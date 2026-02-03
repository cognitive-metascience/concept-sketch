package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Streaming writer for collocations.bin compatible with {@link CollocationsReader}.
 *
 * Writes entries incrementally and maintains a temp offset table file that is appended on finalize.
 */
public final class CollocationsBinWriter implements AutoCloseable {

    private static final int MAGIC_NUMBER = 0x434F4C4C; // "COLL"
    private static final int VERSION = 1;
    private static final long HEADER_SIZE = 64;

    private final int windowSize;
    private final int topK;
    private final long totalTokens;

    private final Path outFile;
    private final Path offsetsTmpFile;

    private final RandomAccessFile outRaf;
    private final FileChannel outChannel;
    private final RandomAccessFile offsetsRaf;

    private int entryCount;

    public CollocationsBinWriter(String outputPath, int windowSize, int topK, long totalTokens) throws IOException {
        this.windowSize = windowSize;
        this.topK = topK;
        this.totalTokens = totalTokens;
        this.outFile = Paths.get(outputPath);
        this.offsetsTmpFile = Paths.get(outputPath + ".offsets.tmp");

        // Fresh build only (resume will be added later)
        Files.deleteIfExists(outFile);
        Files.deleteIfExists(offsetsTmpFile);

        this.outRaf = new RandomAccessFile(outFile.toFile(), "rw");
        this.outChannel = outRaf.getChannel();
        this.offsetsRaf = new RandomAccessFile(offsetsTmpFile.toFile(), "rw");

        outRaf.setLength(0);
        outChannel.position(0);
        writeHeader(0, 0L, 0L);
        outChannel.position(HEADER_SIZE);

        offsetsRaf.setLength(0);
        offsetsRaf.seek(0);
        offsetsRaf.writeInt(0); // placeholder count

        this.entryCount = 0;
    }

    public long position() throws IOException {
        return outChannel.position();
    }

    public void writeEntry(int headId, long headFreq, CollocationsBuilderV2.Candidate[] collocates, int collocateCount, LemmaLexiconReader lexicon) throws IOException {
        long offset = outChannel.position();

        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);

        // Headword bytes
        byte[] headwordBytes = lexicon.getLemmaUtf8(headId);
        if (headwordBytes.length > 0xFFFF) {
            throw new IOException("Headword too long: id=" + headId);
        }
        buffer.putShort((short) headwordBytes.length);
        buffer.put(headwordBytes);

        buffer.putLong(headFreq);

        if (collocateCount > 0xFFFF) {
            throw new IOException("Too many collocates for headId=" + headId + ": " + collocateCount);
        }
        buffer.putShort((short) collocateCount);

        for (int i = 0; i < collocateCount; i++) {
            var c = collocates[i];
            byte[] lemmaBytes = lexicon.getLemmaUtf8(c.collId);
            byte[] posBytes = lexicon.getMostFrequentPos(c.collId).getBytes(StandardCharsets.UTF_8);

            if (lemmaBytes.length > 255) {
                // Keep consistent with existing format: lemma length stored as unsigned byte
                // (Real corpora should not have lemmas this long, but guard just in case.)
                throw new IOException("Collocate lemma too long (>255 bytes): id=" + c.collId);
            }
            if (posBytes.length > 255) {
                throw new IOException("POS too long (>255 bytes): collId=" + c.collId);
            }

            buffer.put((byte) lemmaBytes.length);
            buffer.put(lemmaBytes);

            buffer.put((byte) posBytes.length);
            buffer.put(posBytes);

            buffer.putLong(c.cooc);
            buffer.putLong(c.collFreq);
            buffer.putFloat(c.logDice);
        }

        buffer.flip();
        outChannel.write(buffer);

        // Offset record
        byte[] headUtf8 = headwordBytes; // already utf-8
        offsetsRaf.writeShort(headUtf8.length);
        offsetsRaf.write(headUtf8);
        offsetsRaf.writeLong(offset);

        entryCount++;
    }

    public void finalizeFile() throws IOException {
        // finalize offsets tmp count
        long pos = offsetsRaf.getFilePointer();
        offsetsRaf.seek(0);
        offsetsRaf.writeInt(entryCount);
        offsetsRaf.seek(pos);
        outChannel.force(false);

        long dataEnd = outChannel.position();
        long offsetTableStart = dataEnd;

        try (FileChannel offsetsChannel = FileChannel.open(offsetsTmpFile, StandardOpenOption.READ)) {
            long offsetTableSize = offsetsChannel.size();
            long transferred = 0;
            while (transferred < offsetTableSize) {
                transferred += offsetsChannel.transferTo(transferred, offsetTableSize - transferred, outChannel);
            }
            long fileEnd = outChannel.position();

            outChannel.position(0);
            writeHeader(entryCount, offsetTableStart, offsetTableSize);
            outChannel.position(fileEnd);
        }
    }

    private void writeHeader(int entryCount, long offsetTableOffset, long offsetTableSize) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        buffer.putInt(MAGIC_NUMBER);
        buffer.putInt(VERSION);
        buffer.putInt(entryCount);
        buffer.putInt(windowSize);
        buffer.putInt(topK);
        buffer.putLong(totalTokens);
        buffer.putLong(offsetTableOffset);
        buffer.putLong(offsetTableSize);
        buffer.flip();
        outChannel.write(buffer);
    }

    public int getEntryCount() {
        return entryCount;
    }

    @Override
    public void close() throws IOException {
        try {
            offsetsRaf.close();
        } finally {
            try {
                outChannel.close();
            } finally {
                outRaf.close();
            }
        }
    }
}
