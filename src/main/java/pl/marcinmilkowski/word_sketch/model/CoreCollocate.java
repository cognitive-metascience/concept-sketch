package pl.marcinmilkowski.word_sketch.model;

/**
 * A collocate that defines the semantic class (shared by multiple discovered nouns).
 */
public record CoreCollocate(
        String collocate,
        int sharedByCount,
        int totalNouns,
        double seedLogDice,
        double avgLogDice) {

    /** Coverage ratio: how many of the discovered nouns share this collocate */
    public double getCoverage() {
        return totalNouns > 0 ? (double) sharedByCount / totalNouns : 0.0;
    }

    @Override
    public String toString() {
        return String.format("%s (in %d/%d nouns, avgLogDice=%.1f)",
            collocate, sharedByCount, totalNouns, avgLogDice);
    }
}
