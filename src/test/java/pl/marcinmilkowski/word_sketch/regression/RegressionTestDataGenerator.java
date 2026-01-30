package pl.marcinmilkowski.word_sketch.regression;

import pl.marcinmilkowski.word_sketch.query.QueryExecutor;
import pl.marcinmilkowski.word_sketch.query.WordSketchQueryExecutor;
import pl.marcinmilkowski.word_sketch.query.WordSketchQueryExecutor.WordSketchResult;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates baseline regression test data from the legacy query executor.
 * 
 * This tool captures the exact results from the current (legacy) implementation
 * so they can be compared against the hybrid implementation to ensure no regressions.
 * 
 * Uses a simple tab-separated format for portability (no external JSON libraries required).
 * 
 * Usage:
 * <pre>
 *   RegressionTestDataGenerator generator = new RegressionTestDataGenerator(indexPath);
 *   generator.generateBaseline(headwords, patterns, outputDir);
 * </pre>
 */
public class RegressionTestDataGenerator implements Closeable {

    private final QueryExecutor executor;

    public RegressionTestDataGenerator(String indexPath) throws IOException {
        this.executor = new WordSketchQueryExecutor(indexPath);
    }

    public RegressionTestDataGenerator(QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Generate baseline data for a list of headwords and patterns.
     * 
     * @param testCases Map of headword -> list of CQL patterns to test
     * @param outputDir Directory to write baseline files
     * @param minLogDice Minimum logDice threshold
     * @param maxResults Maximum results per query
     * @throws IOException if generation fails
     */
    public void generateBaseline(Map<String, List<String>> testCases, 
                                  Path outputDir,
                                  double minLogDice,
                                  int maxResults) throws IOException {
        
        Files.createDirectories(outputDir);
        
        List<QueryBaseline> allBaselines = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : testCases.entrySet()) {
            String headword = entry.getKey();
            List<String> patterns = entry.getValue();
            
            for (String pattern : patterns) {
                System.out.printf("Generating baseline: headword='%s', pattern='%s'%n", 
                                  headword, pattern);
                
                long startTime = System.currentTimeMillis();
                List<WordSketchResult> results = executor.findCollocations(
                    headword, pattern, minLogDice, maxResults);
                long duration = System.currentTimeMillis() - startTime;
                
                QueryBaseline baseline = new QueryBaseline();
                baseline.headword = headword;
                baseline.pattern = pattern;
                baseline.minLogDice = minLogDice;
                baseline.maxResults = maxResults;
                baseline.queryTimeMs = duration;
                baseline.totalResults = results.size();
                baseline.results = new ArrayList<>();
                
                for (WordSketchResult result : results) {
                    CollocationBaseline cb = new CollocationBaseline();
                    cb.collocate = result.getLemma();
                    cb.frequency = result.getFrequency();
                    cb.logDice = result.getLogDice();
                    cb.exampleCount = result.getExamples() != null ? 
                                      result.getExamples().size() : 0;
                    baseline.results.add(cb);
                }
                
                allBaselines.add(baseline);
                
                System.out.printf("  -> %d results in %dms%n", 
                                  results.size(), duration);
            }
        }
        
        // Write baseline file in TSV format
        Path baselineFile = outputDir.resolve("baseline.tsv");
        writeBaselineTsv(baselineFile, allBaselines);
        System.out.printf("Wrote baseline to: %s%n", baselineFile);
        
        // Write metadata
        Path metadataFile = outputDir.resolve("metadata.txt");
        writeMetadata(metadataFile, testCases, allBaselines);
    }

    private void writeBaselineTsv(Path file, List<QueryBaseline> baselines) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file))) {
            // Header
            writer.println("# Regression Test Baseline Data");
            writer.println("# Format: headword<TAB>pattern<TAB>collocate<TAB>frequency<TAB>logDice<TAB>exampleCount");
            writer.println("#");
            
            for (QueryBaseline baseline : baselines) {
                for (CollocationBaseline cb : baseline.results) {
                    writer.printf("%s\t%s\t%s\t%d\t%.6f\t%d%n",
                                  baseline.headword,
                                  escapePattern(baseline.pattern),
                                  cb.collocate,
                                  cb.frequency,
                                  cb.logDice,
                                  cb.exampleCount);
                }
            }
        }
    }

    private void writeMetadata(Path file, Map<String, List<String>> testCases, 
                               List<QueryBaseline> baselines) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file))) {
            writer.printf("Generated: %s%n", new Date());
            writer.printf("Executor: %s%n", executor.getExecutorType());
            writer.printf("Total Headwords: %d%n", testCases.size());
            writer.printf("Total Queries: %d%n", baselines.size());
            writer.printf("Total Results: %d%n", 
                         baselines.stream().mapToInt(b -> b.totalResults).sum());
        }
    }

    /**
     * Read baseline data from TSV file.
     */
    public static Map<String, List<QueryBaseline>> readBaseline(Path file) throws IOException {
        Map<String, List<QueryBaseline>> result = new LinkedHashMap<>();
        Map<String, QueryBaseline> currentBaselines = new LinkedHashMap<>();
        
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split("\t");
                if (parts.length < 6) continue;
                
                String headword = parts[0];
                String pattern = unescapePattern(parts[1]);
                String collocate = parts[2];
                long frequency = Long.parseLong(parts[3]);
                double logDice = Double.parseDouble(parts[4]);
                int exampleCount = Integer.parseInt(parts[5]);
                
                // Create unique key for headword+pattern
                String key = headword + "||" + pattern;
                
                QueryBaseline baseline = currentBaselines.computeIfAbsent(key, k -> {
                    QueryBaseline qb = new QueryBaseline();
                    qb.headword = headword;
                    qb.pattern = pattern;
                    qb.results = new ArrayList<>();
                    return qb;
                });
                
                CollocationBaseline cb = new CollocationBaseline();
                cb.collocate = collocate;
                cb.frequency = frequency;
                cb.logDice = logDice;
                cb.exampleCount = exampleCount;
                baseline.results.add(cb);
            }
        }
        
        // Group by headword
        for (QueryBaseline qb : currentBaselines.values()) {
            qb.totalResults = qb.results.size();
            result.computeIfAbsent(qb.headword, k -> new ArrayList<>()).add(qb);
        }
        
        return result;
    }

    private static String escapePattern(String pattern) {
        // Escape tabs and newlines for TSV format
        return pattern.replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescapePattern(String pattern) {
        return pattern.replace("\\t", "\t").replace("\\n", "\n");
    }

    /**
     * Generate baseline for common test headwords.
     */
    public void generateStandardBaseline(Path outputDir) throws IOException {
        Map<String, List<String>> testCases = getStandardTestCases();
        generateBaseline(testCases, outputDir, 0.0, 100);
    }

    /**
     * Get standard test cases covering various patterns and word types.
     * Uses CQL syntax: [field="pattern"] ~ {min,max}
     * The ~ {min,max} specifies distance window from headword.
     */
    public static Map<String, List<String>> getStandardTestCases() {
        Map<String, List<String>> testCases = new LinkedHashMap<>();
        
        // High-frequency nouns - find adjectives modifying them
        testCases.put("time", List.of(
            "[tag=\"JJ.*\"] ~ {-3,0}",    // adjective before noun (within 3 words)
            "[tag=\"NN.*\"] ~ {0,3}",     // noun after (compound nouns)
            "[tag=\"VB.*\"] ~ {-5,0}"     // verb before noun
        ));
        
        testCases.put("people", List.of(
            "[tag=\"JJ.*\"] ~ {-3,0}",    // adjective + people
            "[tag=\"VB.*\"] ~ {0,5}"      // people + verb
        ));
        
        testCases.put("way", List.of(
            "[tag=\"JJ.*\"] ~ {-3,0}",    // adjective + way
            "[tag=\"DT\"] ~ {-2,0}"       // determiner + way
        ));
        
        // Medium-frequency nouns
        testCases.put("house", List.of(
            "[tag=\"JJ.*\"] ~ {-3,0}",    // adjective + house
            "[tag=\"NN.*\"] ~ {0,3}"      // house + noun
        ));
        
        testCases.put("city", List.of(
            "[tag=\"JJ.*\"] ~ {-3,0}"     // adjective + city
        ));
        
        // Verbs - find particles and objects
        testCases.put("go", List.of(
            "[tag=\"TO\"] ~ {0,3}",       // go to...
            "[tag=\"RP\"] ~ {0,2}"        // phrasal verb particle
        ));
        
        testCases.put("make", List.of(
            "[tag=\"NN.*\"] ~ {0,5}",     // make + noun (object)
            "[tag=\"JJ.*\"] ~ {0,3}"      // make + adjective
        ));
        
        testCases.put("take", List.of(
            "[tag=\"NN.*\"] ~ {0,5}"      // take + noun
        ));
        
        // Adjectives - find nouns they modify
        testCases.put("good", List.of(
            "[tag=\"NN.*\"] ~ {0,3}"      // good + noun
        ));
        
        testCases.put("new", List.of(
            "[tag=\"NN.*\"] ~ {0,3}"      // new + noun
        ));
        
        testCases.put("old", List.of(
            "[tag=\"NN.*\"] ~ {0,3}"      // old + noun
        ));
        
        // Lower frequency for edge cases
        testCases.put("cat", List.of(
            "[tag=\"JJ.*\"] ~ {-3,0}",    // adjective + cat
            "[tag=\"VB.*\"] ~ {0,5}"      // cat + verb
        ));
        
        testCases.put("dog", List.of(
            "[tag=\"JJ.*\"] ~ {-3,0}"     // adjective + dog
        ));
        
        return testCases;
    }

    @Override
    public void close() throws IOException {
        executor.close();
    }

    // Data classes
    
    public static class QueryBaseline {
        public String headword;
        public String pattern;
        public double minLogDice;
        public int maxResults;
        public long queryTimeMs;
        public int totalResults;
        public List<CollocationBaseline> results;
    }
    
    public static class CollocationBaseline {
        public String collocate;
        public long frequency;
        public double logDice;
        public int exampleCount;
    }

    /**
     * Command-line entry point for generating baselines.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: RegressionTestDataGenerator <indexPath> <outputDir>");
            System.exit(1);
        }
        
        String indexPath = args[0];
        Path outputDir = Path.of(args[1]);
        
        try (RegressionTestDataGenerator generator = 
                 new RegressionTestDataGenerator(indexPath)) {
            generator.generateStandardBaseline(outputDir);
        }
    }
}
