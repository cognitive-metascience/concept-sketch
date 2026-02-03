package pl.marcinmilkowski.word_sketch.tools;

import pl.marcinmilkowski.word_sketch.query.HybridQueryExecutor;
import pl.marcinmilkowski.word_sketch.query.HybridQueryExecutor.Algorithm;
import pl.marcinmilkowski.word_sketch.query.WordSketchQueryExecutor.WordSketchResult;

import java.util.List;

/**
 * Benchmark tool for comparing collocation algorithms.
 */
public class CollocationsBenchmark {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: CollocationsBenchmark <indexPath> [words...]");
            System.exit(1);
        }

        String indexPath = args[0];
        String[] testWords = args.length > 1 
            ? java.util.Arrays.copyOfRange(args, 1, args.length)
            : new String[]{"the", "be", "have", "make", "think", "know", "implement", "establish"};

        System.out.println("\n=== COLLOCATION ALGORITHM BENCHMARK ===");
        System.out.println("Index: " + indexPath);
        System.out.println();

        HybridQueryExecutor executor = new HybridQueryExecutor(indexPath);
        executor.setMaxSampleSize(5000);

        // Warmup
        executor.setAlgorithm(Algorithm.PRECOMPUTED);
        executor.findCollocations("test", "1:\"N.*\"", 0, 10);
        executor.setAlgorithm(Algorithm.SAMPLE_SCAN);
        executor.findCollocations("test", "1:\"N.*\"", 0, 10);

        System.out.printf("%-14s %12s %12s %10s %12s%n", 
            "Word", "PRECOMPUTED", "SAMPLE_SCAN", "Speedup", "Results");
        System.out.println("-".repeat(70));

        long totalPre = 0, totalScan = 0;

        for (String word : testWords) {
            // PRECOMPUTED
            executor.setAlgorithm(Algorithm.PRECOMPUTED);
            long start1 = System.currentTimeMillis();
            List<WordSketchResult> preRes = executor.findCollocations(word, "1:\"N.*\"", 0, 50);
            long preTime = System.currentTimeMillis() - start1;
            totalPre += preTime;

            // SAMPLE_SCAN
            executor.setAlgorithm(Algorithm.SAMPLE_SCAN);
            long start2 = System.currentTimeMillis();
            List<WordSketchResult> scanRes = executor.findCollocations(word, "1:\"N.*\"", 0, 50);
            long scanTime = System.currentTimeMillis() - start2;
            totalScan += scanTime;

            double speedup = scanTime / (double) Math.max(1, preTime);
            System.out.printf("%-14s %10d ms %10d ms %9.1fx %6d / %d%n",
                word, preTime, scanTime, speedup, preRes.size(), scanRes.size());
        }

        System.out.println("-".repeat(70));
        double totalSpeedup = totalScan / (double) Math.max(1, totalPre);
        System.out.printf("%-14s %10d ms %10d ms %9.1fx%n", "TOTAL", totalPre, totalScan, totalSpeedup);

        executor.close();
    }
}
