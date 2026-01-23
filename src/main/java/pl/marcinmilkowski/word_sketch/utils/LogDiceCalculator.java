package pl.marcinmilkowski.word_sketch.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for computing logDice association scores.
 *
 * logDice is a symmetric association measure that ranges from 0 to ~14.
 * A score of 14 indicates perfect association (the collocate only occurs with the headword).
 *
 * Formula: logDice = log2(2 * f(AB) / (f(A) + f(B))) + 14
 *
 * Where:
 * - f(AB) = frequency of collocate occurring with headword
 * - f(A) = total frequency of headword
 * - f(B) = total frequency of collocate
 */
public class LogDiceCalculator {

    /**
     * Compute the logDice score for a collocation.
     *
     * @param collocateFreq Frequency of collocate with headword (f(AB))
     * @param headwordFreq Total frequency of headword (f(A))
     * @param collocateTotal Total frequency of collocate (f(B))
     * @return logDice score (0 to ~14)
     */
    public static double compute(double collocateFreq, double headwordFreq, double collocateTotal) {
        if (headwordFreq <= 0 || collocateTotal <= 0) {
            return 0.0;
        }

        double numerator = 2.0 * collocateFreq;
        double denominator = headwordFreq + collocateTotal;

        if (denominator <= 0) {
            return 0.0;
        }

        double dice = numerator / denominator;

        // Handle edge case where dice is 0 or negative
        if (dice <= 0) {
            return 0.0;
        }

        double logDice = Math.log(dice) / Math.log(2) + 14.0;

        return Math.max(0.0, logDice);
    }

    /**
     * Compute logDice score from frequency counts.
     */
    public static double compute(long collocateFreq, long headwordFreq, long collocateTotal) {
        return compute((double) collocateFreq, (double) headwordFreq, (double) collocateTotal);
    }

    /**
     * Compute relative frequency (collocate frequency / headword frequency).
     */
    public static double relativeFrequency(long collocateFreq, long headwordFreq) {
        if (headwordFreq <= 0) {
            return 0.0;
        }
        return (double) collocateFreq / (double) headwordFreq;
    }

    /**
     * Result class for collocation analysis.
     */
    public static class CollocationResult {
        private final String lemma;
        private final String pos;
        private final long frequency;
        private final long headwordFrequency;
        private final long collocateTotal;
        private final double logDice;
        private final double relativeFrequency;

        public CollocationResult(String lemma, String pos, long frequency,
                                 long headwordFrequency, long collocateTotal) {
            this.lemma = lemma;
            this.pos = pos;
            this.frequency = frequency;
            this.headwordFrequency = headwordFrequency;
            this.collocateTotal = collocateTotal;
            this.logDice = compute(frequency, headwordFrequency, collocateTotal);
            this.relativeFrequency = relativeFrequency(frequency, headwordFrequency);
        }

        public String getLemma() { return lemma; }
        public String getPos() { return pos; }
        public long getFrequency() { return frequency; }
        public long getHeadwordFrequency() { return headwordFrequency; }
        public long getCollocateTotal() { return collocateTotal; }
        public double getLogDice() { return logDice; }
        public double getRelativeFrequency() { return relativeFrequency; }
    }

    /**
     * Aggregated frequency counts for collocation analysis.
     */
    public static class FrequencyAggregator {
        private final Map<String, Long> lemmaFrequencies = new HashMap<>();
        private final Map<String, Long> lemmaPosFrequencies = new HashMap<>();
        private long totalHeadwordFreq = 0;

        public void addCollocate(String lemma, String pos) {
            lemmaFrequencies.merge(lemma, 1L, Long::sum);
            String key = lemma + "|" + pos;
            lemmaPosFrequencies.merge(key, 1L, Long::sum);
        }

        public void setHeadwordFrequency(long freq) {
            this.totalHeadwordFreq = freq;
        }

        public long getHeadwordFrequency() { return totalHeadwordFreq; }

        public long getCollocateTotal(String lemma) {
            return lemmaFrequencies.getOrDefault(lemma, 0L);
        }

        public long getCollocateTotal(String lemma, String pos) {
            String key = lemma + "|" + pos;
            return lemmaPosFrequencies.getOrDefault(key, 0L);
        }

        public Map<String, Long> getLemmaFrequencies() {
            return new HashMap<>(lemmaFrequencies);
        }

        public CollocationResult getResult(String lemma, String pos, long collocateFreq) {
            return new CollocationResult(
                lemma,
                pos,
                collocateFreq,
                totalHeadwordFreq,
                getCollocateTotal(lemma)
            );
        }
    }
}
