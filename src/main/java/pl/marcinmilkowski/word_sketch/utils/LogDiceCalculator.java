package pl.marcinmilkowski.word_sketch.utils;

/**
 * Utility class for computing association scores for collocation analysis.
 *
 * Supported measures:
 * - logDice: Symmetric association measure, range 0-14. Formula: log2(2 * f(AB) / (f(A) + f(B))) + 14
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

}
