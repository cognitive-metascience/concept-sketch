package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds and persists term statistics for fast logDice calculations.
 * 
 * Statistics are collected during indexing and written to a binary file
 * that can be memory-mapped for O(1) lookups.
 * 
 * File format:
 * - Header: magic number (4 bytes), version (4 bytes), entry count (4 bytes)
 * - For each entry:
 *   - lemma length (2 bytes)
 *   - lemma (UTF-8 bytes)
 *   - total frequency (8 bytes)
 *   - document frequency (4 bytes)
 *   - POS count (2 bytes)
 *   - For each POS:
 *     - tag length (1 byte)
 *     - tag (UTF-8 bytes)
 *     - count (8 bytes)
 */
public class StatisticsIndexBuilder implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(StatisticsIndexBuilder.class);

    private static final int MAGIC_NUMBER = 0x57534C53; // "WSLS" - Word Sketch Lucene Stats
    private static final int VERSION = 1;

    private final ConcurrentHashMap<String, LemmaStats> lemmaStats = new ConcurrentHashMap<>();
    private final AtomicLong totalTokens = new AtomicLong(0);
    private final AtomicLong totalSentences = new AtomicLong(0);

    /**
     * Internal class for tracking lemma statistics.
     */
    private static class LemmaStats {
        final AtomicLong totalFrequency = new AtomicLong(0);
        final Set<Integer> documentIds = ConcurrentHashMap.newKeySet();
        final ConcurrentHashMap<String, AtomicLong> posDistribution = new ConcurrentHashMap<>();

        void addOccurrence(String posTag, int documentId) {
            totalFrequency.incrementAndGet();
            documentIds.add(documentId);
            if (posTag != null && !posTag.isEmpty()) {
                posDistribution.computeIfAbsent(posTag.toUpperCase(), k -> new AtomicLong(0))
                    .incrementAndGet();
            }
        }

        TermStatistics toTermStatistics(String lemma) {
            Map<String, Long> posDist = new HashMap<>();
            posDistribution.forEach((tag, count) -> posDist.put(tag, count.get()));
            return new TermStatistics(lemma, totalFrequency.get(), documentIds.size(), posDist);
        }
    }

    /**
     * Adds token occurrences from a sentence to the statistics.
     * 
     * @param sentence The sentence containing tokens
     */
    public void addSentence(SentenceDocument sentence) {
        totalSentences.incrementAndGet();
        int docId = sentence.sentenceId();

        for (SentenceDocument.Token token : sentence.tokens()) {
            totalTokens.incrementAndGet();

            String lemma = token.lemma();
            if (lemma != null && !lemma.isEmpty()) {
                lemma = lemma.toLowerCase();
                String tag = token.tag();
                lemmaStats.computeIfAbsent(lemma, k -> new LemmaStats())
                    .addOccurrence(tag, docId);
            }
        }
    }

    /**
     * Gets statistics for a specific lemma.
     */
    public TermStatistics getStatistics(String lemma) {
        LemmaStats stats = lemmaStats.get(lemma.toLowerCase());
        if (stats == null) {
            return TermStatistics.of(lemma, 0, 0);
        }
        return stats.toTermStatistics(lemma);
    }

    /**
     * Gets the total frequency of a lemma.
     */
    public long getFrequency(String lemma) {
        LemmaStats stats = lemmaStats.get(lemma.toLowerCase());
        return stats != null ? stats.totalFrequency.get() : 0;
    }

    /**
     * Gets the document frequency of a lemma.
     */
    public int getDocumentFrequency(String lemma) {
        LemmaStats stats = lemmaStats.get(lemma.toLowerCase());
        return stats != null ? stats.documentIds.size() : 0;
    }

    /**
     * Gets the total number of tokens processed.
     */
    public long getTotalTokens() {
        return totalTokens.get();
    }

    /**
     * Gets the total number of sentences processed.
     */
    public long getTotalSentences() {
        return totalSentences.get();
    }

    /**
     * Gets the number of unique lemmas.
     */
    public int getUniqueLemmaCount() {
        return lemmaStats.size();
    }

    /**
     * Writes statistics to a binary file.
     * 
     * @param path Path to the output file
     * @throws IOException if writing fails
     */
    public void writeBinaryFile(String path) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filePath.toFile())))) {

            // Header
            dos.writeInt(MAGIC_NUMBER);
            dos.writeInt(VERSION);
            dos.writeLong(totalTokens.get());
            dos.writeLong(totalSentences.get());
            dos.writeInt(lemmaStats.size());

            // Entries sorted by frequency descending for better cache locality
            List<Map.Entry<String, LemmaStats>> sorted = new ArrayList<>(lemmaStats.entrySet());
            sorted.sort((a, b) -> Long.compare(
                b.getValue().totalFrequency.get(),
                a.getValue().totalFrequency.get()));

            for (Map.Entry<String, LemmaStats> entry : sorted) {
                String lemma = entry.getKey();
                LemmaStats stats = entry.getValue();

                // Lemma
                byte[] lemmaBytes = lemma.getBytes("UTF-8");
                dos.writeShort(lemmaBytes.length);
                dos.write(lemmaBytes);

                // Frequencies
                dos.writeLong(stats.totalFrequency.get());
                dos.writeInt(stats.documentIds.size());

                // POS distribution
                Map<String, AtomicLong> posDist = stats.posDistribution;
                dos.writeShort(posDist.size());
                for (Map.Entry<String, AtomicLong> posEntry : posDist.entrySet()) {
                    byte[] tagBytes = posEntry.getKey().getBytes("UTF-8");
                    dos.writeByte(tagBytes.length);
                    dos.write(tagBytes);
                    dos.writeLong(posEntry.getValue().get());
                }
            }
        }

        log.info("Statistics written: {} lemmas, {} tokens, {} sentences",
            lemmaStats.size(), totalTokens.get(), totalSentences.get());
    }

    /**
     * Writes statistics to a human-readable TSV file.
     * 
     * @param path Path to the output file
     * @throws IOException if writing fails
     */
    public void writeTsvFile(String path) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            // Header
            writer.write("# Word Sketch Lucene Statistics\n");
            writer.write(String.format("# Total tokens: %d\n", totalTokens.get()));
            writer.write(String.format("# Total sentences: %d\n", totalSentences.get()));
            writer.write(String.format("# Unique lemmas: %d\n", lemmaStats.size()));
            writer.write("# Format: lemma<TAB>total_freq<TAB>doc_freq<TAB>pos:count,...\n");

            // Entries sorted by frequency descending
            lemmaStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(
                    b.getValue().totalFrequency.get(),
                    a.getValue().totalFrequency.get()))
                .forEach(entry -> {
                    try {
                        String lemma = entry.getKey();
                        LemmaStats stats = entry.getValue();

                        // Build POS distribution string
                        StringBuilder posStr = new StringBuilder();
                        stats.posDistribution.entrySet().stream()
                            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                            .forEach(posEntry -> {
                                if (posStr.length() > 0) posStr.append(",");
                                posStr.append(posEntry.getKey())
                                    .append(":")
                                    .append(posEntry.getValue().get());
                            });

                        writer.write(String.format("%s\t%d\t%d\t%s\n",
                            lemma,
                            stats.totalFrequency.get(),
                            stats.documentIds.size(),
                            posStr.toString()));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }

        log.info("TSV statistics written: {}", path);
    }

    @Override
    public void close() throws IOException {
        // Nothing to close, but implements Closeable for try-with-resources pattern
    }
}
