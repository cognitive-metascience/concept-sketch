import pl.marcinmilkowski.word_sketch.indexer.hybrid.*;
import java.util.*;

var reader = new CollocationsReader("d:\\corpus_74m\\index-hybrid\\collocations_v2.bin");
System.out.println("Entries: " + reader.getEntryCount());
System.out.println("TopK: " + reader.getTopK());
System.out.println("Window: " + reader.getWindowSize());

String[] words = {"the", "be", "have", "make", "think", "implement", "establish"};
for (var word : words) {
    long start = System.nanoTime();
    var entry = reader.getCollocations(word);
    long elapsed = System.nanoTime() - start;
    if (entry != null) {
        System.out.printf("%12s: %6d collocates, %4d μs, top: %s (logDice=%.2f)%n", 
            word, entry.collocates().size(), elapsed/1000,
            entry.collocates().isEmpty() ? "N/A" : entry.collocates().get(0).lemma(),
            entry.collocates().isEmpty() ? 0 : entry.collocates().get(0).logDice());
    } else {
        System.out.printf("%12s: NOT FOUND%n", word);
    }
}
reader.close();
