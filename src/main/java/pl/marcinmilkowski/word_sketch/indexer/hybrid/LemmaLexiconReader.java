package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Reader for lexicon.bin written by {@link LemmaLexiconWriter}.
 *
 * Optimized for O(1) id -> frequency lookups and cheap access to UTF-8 bytes.
 */
public class LemmaLexiconReader implements Closeable {

    private static final int MAGIC_NUMBER = 0x57534C58; // "WSLX"
    private static final int EXPECTED_VERSION = 1;

    private final MappedByteBuffer buffer;

    private final long totalTokens;
    private final long totalSentences;
    private final int entryCount;

    private final long[] frequencyById;
    private final int[] lemmaOffset;
    private final int[] lemmaLen;
    private final int[] posOffset;
    private final int[] posLen;

    public LemmaLexiconReader(String path) throws IOException {
        if (!Files.exists(Paths.get(path))) {
            throw new FileNotFoundException("Lexicon file not found: " + path);
        }

        try (FileChannel channel = FileChannel.open(Paths.get(path), StandardOpenOption.READ)) {
            this.buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

            int magic = buffer.getInt();
            if (magic != MAGIC_NUMBER) {
                throw new IOException("Invalid lexicon file: bad magic number");
            }
            int version = buffer.getInt();
            if (version != EXPECTED_VERSION) {
                throw new IOException("Unsupported lexicon file version: " + version);
            }

            this.totalTokens = buffer.getLong();
            this.totalSentences = buffer.getLong();
            this.entryCount = buffer.getInt();

            this.frequencyById = new long[entryCount];
            this.lemmaOffset = new int[entryCount];
            this.lemmaLen = new int[entryCount];
            this.posOffset = new int[entryCount];
            this.posLen = new int[entryCount];

            for (int id = 0; id < entryCount; id++) {
                int len = buffer.getShort() & 0xFFFF;
                lemmaLen[id] = len;
                lemmaOffset[id] = buffer.position();
                buffer.position(buffer.position() + len);

                frequencyById[id] = buffer.getLong();

                int pLen = buffer.get() & 0xFF;
                posLen[id] = pLen;
                posOffset[id] = buffer.position();
                buffer.position(buffer.position() + pLen);
            }
        }
    }

    public int size() {
        return entryCount;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public long getTotalSentences() {
        return totalSentences;
    }

    public long getFrequency(int lemmaId) {
        return frequencyById[lemmaId];
    }

    public String getMostFrequentPos(int lemmaId) {
        int offset = posOffset[lemmaId];
        int len = posLen[lemmaId];
        if (len == 0) return "";
        byte[] bytes = new byte[len];
        var dup = buffer.duplicate();
        dup.position(offset);
        dup.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String getLemma(int lemmaId) {
        int offset = lemmaOffset[lemmaId];
        int len = lemmaLen[lemmaId];
        if (len == 0) return "";
        byte[] bytes = new byte[len];
        var dup = buffer.duplicate();
        dup.position(offset);
        dup.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public byte[] getLemmaUtf8(int lemmaId) {
        int offset = lemmaOffset[lemmaId];
        int len = lemmaLen[lemmaId];
        byte[] bytes = new byte[len];
        var dup = buffer.duplicate();
        dup.position(offset);
        dup.get(bytes);
        return bytes;
    }

    @Override
    public void close() {
        // MappedByteBuffer cleaned up by GC
    }
}
