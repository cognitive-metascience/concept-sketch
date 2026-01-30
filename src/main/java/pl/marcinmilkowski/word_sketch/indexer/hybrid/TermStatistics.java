package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.util.Map;
import java.util.HashMap;

/**
 * Immutable record for term statistics.
 * Used for logDice calculations and corpus analysis.
 */
public record TermStatistics(
    String lemma,
    long totalFrequency,
    int documentFrequency,
    Map<String, Long> posDistribution
) {
    /**
     * Creates TermStatistics with an immutable POS distribution map.
     */
    public TermStatistics {
        posDistribution = posDistribution != null 
            ? Map.copyOf(posDistribution) 
            : Map.of();
    }

    /**
     * Creates TermStatistics without POS distribution.
     */
    public static TermStatistics of(String lemma, long totalFrequency, int documentFrequency) {
        return new TermStatistics(lemma, totalFrequency, documentFrequency, Map.of());
    }

    /**
     * Creates TermStatistics with a single POS tag.
     */
    public static TermStatistics of(String lemma, long totalFrequency, int documentFrequency, 
                                   String posTag, long posCount) {
        return new TermStatistics(lemma, totalFrequency, documentFrequency, 
            Map.of(posTag, posCount));
    }

    /**
     * Gets the frequency for a specific POS tag.
     */
    public long getFrequencyForPos(String posTag) {
        return posDistribution.getOrDefault(posTag.toUpperCase(), 0L);
    }

    /**
     * Gets the most common POS tag for this lemma.
     */
    public String getMostCommonPos() {
        return posDistribution.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Builder for TermStatistics.
     */
    public static class Builder {
        private String lemma;
        private long totalFrequency = 0;
        private int documentFrequency = 0;
        private final Map<String, Long> posDistribution = new HashMap<>();

        public Builder lemma(String lemma) {
            this.lemma = lemma;
            return this;
        }

        public Builder totalFrequency(long frequency) {
            this.totalFrequency = frequency;
            return this;
        }

        public Builder documentFrequency(int frequency) {
            this.documentFrequency = frequency;
            return this;
        }

        public Builder addPos(String posTag, long count) {
            posDistribution.merge(posTag.toUpperCase(), count, Long::sum);
            return this;
        }

        public Builder incrementFrequency() {
            this.totalFrequency++;
            return this;
        }

        public Builder incrementDocumentFrequency() {
            this.documentFrequency++;
            return this;
        }

        public TermStatistics build() {
            return new TermStatistics(lemma, totalFrequency, documentFrequency, posDistribution);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
