import pl.marcinmilkowski.word_sketch.query.*;
import java.util.*;

String indexPath = "d:\\corpus_74m\\index-hybrid";
var executor = new HybridQueryExecutor(indexPath);
executor.setMaxSampleSize(5000);

String[] testWords = {"the", "be", "have", "make", "think", "know", "implement", "establish", "demonstrate"};

System.out.println("\n=== ALGORITHM PERFORMANCE COMPARISON ===\n");
System.out.printf("%-14s %-12s %-12s %-12s %-10s%n", "Word", "PRECOMPUTED", "SAMPLE_SCAN", "Speedup", "Results");
System.out.println("-".repeat(70));

for (var word : testWords) {
    // Precomputed
    executor.setAlgorithm(HybridQueryExecutor.Algorithm.PRECOMPUTED);
    long start1 = System.currentTimeMillis();
    var preRes = executor.findCollocations(word, "1:\"N.*\"", 0, 50);
    long preTime = System.currentTimeMillis() - start1;
    
    // Sample Scan
    executor.setAlgorithm(HybridQueryExecutor.Algorithm.SAMPLE_SCAN);
    long start2 = System.currentTimeMillis();
    var scanRes = executor.findCollocations(word, "1:\"N.*\"", 0, 50);
    long scanTime = System.currentTimeMillis() - start2;
    
    double speedup = (double)scanTime / Math.max(1, preTime);
    System.out.printf("%-14s %9d ms %9d ms %9.1fx %7d/%d%n", 
        word, preTime, scanTime, speedup, preRes.size(), scanRes.size());
}

System.out.println("-".repeat(70));
executor.close();
