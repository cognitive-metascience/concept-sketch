package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Converts TSV statistics to binary format for faster loading.
 */
public class StatisticsConverter {

    private static final int MAGIC_NUMBER = 0x57534C53; // "WSLS"
    private static final int VERSION = 1;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: StatisticsConverter <input.tsv> <output.bin>");
            System.exit(1);
        }

        String tsvPath = args[0];
        String binPath = args[1];

        System.out.println("Converting " + tsvPath + " to " + binPath);
        convert(tsvPath, binPath);
        System.out.println("Conversion complete!");
    }

    public static void convert(String tsvPath, String binPath) throws IOException {
        // Parse TSV file
        Map<String, LemmaData> lemmas = new HashMap<>();
        long totalTokens = 0;
        long totalSentences = 0;

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(tsvPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    // Parse metadata
                    if (line.contains("Total tokens:")) {
                        totalTokens = Long.parseLong(line.replaceAll("[^0-9]", ""));
                    } else if (line.contains("Total sentences:")) {
                        totalSentences = Long.parseLong(line.replaceAll("[^0-9]", ""));
                    }
                    continue;
                }

                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    String lemma = parts[0];
                    long totalFreq = Long.parseLong(parts[1]);
                    int docFreq = Integer.parseInt(parts[2]);

                    Map<String, Long> posDist = new HashMap<>();
                    if (parts.length >= 4 && !parts[3].isEmpty()) {
                        for (String posEntry : parts[3].split(",")) {
                            String[] posData = posEntry.split(":");
                            if (posData.length == 2) {
                                posDist.put(posData[0], Long.parseLong(posData[1]));
                            }
                        }
                    }

                    lemmas.put(lemma, new LemmaData(totalFreq, docFreq, posDist));
                }
            }
        }

        System.out.println("Loaded " + lemmas.size() + " lemmas, " + totalTokens + " tokens");

        // Write binary file
        Path outputPath = Paths.get(binPath);
        Files.createDirectories(outputPath.getParent());

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputPath.toFile())))) {

            // Header
            dos.writeInt(MAGIC_NUMBER);
            dos.writeInt(VERSION);
            dos.writeLong(totalTokens);
            dos.writeLong(totalSentences);
            dos.writeInt(lemmas.size());

            // Entries sorted by frequency descending
            lemmas.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().totalFreq, a.getValue().totalFreq))
                .forEach(entry -> {
                    try {
                        String lemma = entry.getKey();
                        LemmaData data = entry.getValue();

                        // Lemma
                        byte[] lemmaBytes = lemma.getBytes("UTF-8");
                        dos.writeShort(lemmaBytes.length);
                        dos.write(lemmaBytes);

                        // Frequencies
                        dos.writeLong(data.totalFreq);
                        dos.writeInt(data.docFreq);

                        // POS distribution
                        dos.writeShort(data.posDist.size());
                        for (Map.Entry<String, Long> posEntry : data.posDist.entrySet()) {
                            byte[] tagBytes = posEntry.getKey().getBytes("UTF-8");
                            dos.writeByte(tagBytes.length);
                            dos.write(tagBytes);
                            dos.writeLong(posEntry.getValue());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        long binSize = Files.size(outputPath) / (1024 * 1024);
        System.out.println("Binary file written: " + binSize + " MB");
    }

    private static class LemmaData {
        final long totalFreq;
        final int docFreq;
        final Map<String, Long> posDist;

        LemmaData(long totalFreq, int docFreq, Map<String, Long> posDist) {
            this.totalFreq = totalFreq;
            this.docFreq = docFreq;
            this.posDist = posDist;
        }
    }
}
