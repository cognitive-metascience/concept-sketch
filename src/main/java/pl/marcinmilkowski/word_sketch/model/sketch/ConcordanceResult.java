package pl.marcinmilkowski.word_sketch.model.sketch;

/**
 * Common interface for all concordance result types.
 * Use pattern-matching ({@code instanceof}) or the concrete subtype to access type-specific fields.
 */
public sealed interface ConcordanceResult
        permits ConcordanceHit, CollocateResult {
    String sentence();
    int startOffset();
    int endOffset();
    String docId();
}
