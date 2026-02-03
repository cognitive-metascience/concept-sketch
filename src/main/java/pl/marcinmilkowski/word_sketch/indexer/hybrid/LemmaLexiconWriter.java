package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes lexicon.bin mapping lemma IDs to lemma strings and frequencies.
 *
 * File format (v1):
 * - magic (int): "WSLX" (0x57534C58)
 * - version (int): 1
 * - totalTokens (long)
 * - totalSentences (long)
 * - entryCount (int)
 * - for id=0..entryCount-1:
 *   - lemmaLen (unsigned short)
 *   - lemma (UTF-8 bytes)
 *   - totalFreq (long)
 *   - mostFrequentPosLen (unsigned byte)
 *   - mostFrequentPos (UTF-8 bytes)
 */
public final class LemmaLexiconWriter {

    private static final int MAGIC_NUMBER = 0x57534C58; // "WSLX"
    private static final int VERSION = 1;

    private LemmaLexiconWriter() {
    }

    public static void writeBinaryFile(
        String path,
        LemmaIdAssigner assigner,
        HybridIndexer.StatisticsCollector statsCollector
    ) throws IOException {
        Path out = Paths.get(path);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }

        int size = assigner.size();
        String[] lemmaById = new String[size];
        for (var entry : assigner.entries()) {
            int id = entry.getValue();
            if (id < 0 || id >= size) {
                throw new IOException("Invalid lemmaId " + id + " for lemma: " + entry.getKey());
            }
            lemmaById[id] = entry.getKey();
        }
        for (int i = 0; i < size; i++) {
            if (lemmaById[i] == null) {
                throw new IOException("Missing lemma for id=" + i + " (lexicon incomplete)");
            }
        }

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out.toFile())))) {
            dos.writeInt(MAGIC_NUMBER);
            dos.writeInt(VERSION);
            dos.writeLong(statsCollector.getTotalTokens());
            dos.writeLong(statsCollector.getTotalSentences());
            dos.writeInt(size);

            for (int id = 0; id < size; id++) {
                String lemma = lemmaById[id];
                byte[] lemmaBytes = lemma.getBytes(StandardCharsets.UTF_8);
                if (lemmaBytes.length > 0xFFFF) {
                    throw new IOException("Lemma too long for lexicon: " + lemma);
                }

                dos.writeShort(lemmaBytes.length);
                dos.write(lemmaBytes);

                long freq = statsCollector.getLemmaFrequency(lemma);
                dos.writeLong(freq);

                String pos = statsCollector.getMostFrequentPos(lemma);
                byte[] posBytes = pos != null ? pos.getBytes(StandardCharsets.UTF_8) : new byte[0];
                if (posBytes.length > 0xFF) {
                    throw new IOException("POS string too long: " + pos);
                }
                dos.writeByte(posBytes.length);
                dos.write(posBytes);
            }
        }
    }
}
