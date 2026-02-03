package pl.marcinmilkowski.word_sketch.tools;

import pl.marcinmilkowski.word_sketch.indexer.hybrid.CollocationsReader;
import pl.marcinmilkowski.word_sketch.indexer.hybrid.CollocationEntry;
import pl.marcinmilkowski.word_sketch.indexer.hybrid.Collocation;

public class CollocationsDebug {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: CollocationsDebug <collocations.bin> <word1> [word2] ...");
            return;
        }
        
        try (CollocationsReader r = new CollocationsReader(args[0])) {
            System.out.println("=== Collocations Debug ===");
            System.out.println("File: " + args[0]);
            System.out.println("Total entries: " + r.getEntryCount());
            System.out.println("Window size: " + r.getWindowSize());
            System.out.println("Top-K: " + r.getTopK());
            System.out.println();
            
            for (int i = 1; i < args.length; i++) {
                String word = args[i];
                CollocationEntry e = r.getCollocations(word);
                
                System.out.println("--- " + word + " ---");
                if (e == null) {
                    System.out.println("  NOT FOUND in index");
                } else {
                    System.out.println("  Headword freq: " + e.headwordFrequency());
                    System.out.println("  Collocates: " + e.collocates().size());
                    
                    // Count by POS
                    java.util.Map<String, Integer> posCounts = new java.util.HashMap<>();
                    for (Collocation c : e.collocates()) {
                        posCounts.merge(c.pos().isEmpty() ? "(empty)" : c.pos(), 1, Integer::sum);
                    }
                    System.out.println("  POS distribution: " + posCounts);
                    
                    // Show first 15
                    System.out.println("  Top 15 collocates:");
                    int count = 0;
                    for (Collocation c : e.collocates()) {
                        if (count++ >= 15) break;
                        System.out.printf("    %-20s [%-6s] cooc=%-8d freq=%-10d logDice=%.2f%n",
                            c.lemma(), c.pos(), c.cooccurrence(), c.frequency(), c.logDice());
                    }
                }
                System.out.println();
            }
        }
    }
}
