package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Builds a lexicon.bin from an existing stats.bin or stats.tsv.
 *
 * This is a migration tool for indices built before lemma_ids was added.
 * After running this, CollocationsBuilderV2 can use the lexicon for frequency lookups,
 * but will fall back to decoding the 'tokens' field for lemma extraction.
 */
public class LexiconFromStatsMigrator {

    private static final Logger log = LoggerFactory.getLogger(LexiconFromStatsMigrator.class);

    private static final int MAGIC_NUMBER = 0x57534C58; // "WSLX"
    private static final int VERSION = 1;

    public static void migrate(String statsPath, String lexiconPath) throws IOException {
        Path stats = Paths.get(statsPath);
        if (!Files.exists(stats)) {
            throw new FileNotFoundException("Stats file not found: " + statsPath);
        }

        // Load stats to get lemmas and frequencies
        StatisticsReader reader = new StatisticsReader(statsPath);

        List<TermStatistics> allStats = reader.getAllStatisticsByFrequency();
        log.info("Loaded {} lemmas from stats", allStats.size());

        // Assign IDs: 0 = empty, 1..N = lemmas sorted by descending frequency
        int entryCount = allStats.size() + 1;

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(lexiconPath)))) {

            dos.writeInt(MAGIC_NUMBER);
            dos.writeInt(VERSION);
            dos.writeLong(reader.getTotalTokens());
            dos.writeLong(reader.getTotalSentences());
            dos.writeInt(entryCount);

            // Entry 0: empty lemma
            dos.writeShort(0);
            dos.writeLong(0);
            dos.writeByte(0); // no POS

            // Entries 1..N
            for (TermStatistics ts : allStats) {
                String lemma = ts.lemma();
                byte[] lemmaBytes = lemma.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(lemmaBytes.length);
                dos.write(lemmaBytes);
                dos.writeLong(ts.totalFrequency());

                // Most frequent POS
                String pos = getMostFrequentPos(ts);
                byte[] posBytes = pos.getBytes(StandardCharsets.UTF_8);
                dos.writeByte(posBytes.length);
                if (posBytes.length > 0) {
                    dos.write(posBytes);
                }
            }
        }

        log.info("Wrote lexicon.bin with {} entries to {}", entryCount, lexiconPath);
        reader.close();
    }

    private static String getMostFrequentPos(TermStatistics ts) {
        if (ts.posDistribution() == null || ts.posDistribution().isEmpty()) {
            return "";
        }
        return ts.posDistribution().entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: LexiconFromStatsMigrator <stats.bin|stats.tsv> <lexicon.bin>");
            System.exit(1);
        }

        migrate(args[0], args[1]);
    }
}
