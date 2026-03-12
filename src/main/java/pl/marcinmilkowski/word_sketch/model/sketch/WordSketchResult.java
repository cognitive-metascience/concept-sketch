package pl.marcinmilkowski.word_sketch.model.sketch;

import java.util.List;

/**
 * Result of a word sketch query containing collocation information.
 */
public record WordSketchResult(String lemma, String pos, long frequency,
                               double logDice, double relativeFrequency,
                               List<String> examples) {
    /** Sentinel for missing POS information returned by the tagger. */
    public static final String UNKNOWN_POS = "unknown";
}
