package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Assigns stable, compact integer IDs to lemmas during indexing.
 *
 * IDs are stable within a single index build and persisted to lexicon.bin.
 */
public class LemmaIdAssigner {

    private final AtomicInteger nextId = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> lemmaToId = new ConcurrentHashMap<>();

    /**
     * Returns the ID for the lemma, assigning a new one if necessary.
     * The lemma is expected to be lowercased by the caller.
     */
    public int getOrAssignId(String lemmaLower) {
        String key = lemmaLower != null ? lemmaLower : "";
        Integer existing = lemmaToId.get(key);
        if (existing != null) {
            return existing;
        }
        return lemmaToId.computeIfAbsent(key, ignored -> nextId.getAndIncrement());
    }

    public int size() {
        return nextId.get();
    }

    public Iterable<Map.Entry<String, Integer>> entries() {
        return lemmaToId.entrySet();
    }
}
