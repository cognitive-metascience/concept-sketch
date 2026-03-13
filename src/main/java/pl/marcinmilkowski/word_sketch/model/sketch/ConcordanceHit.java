package pl.marcinmilkowski.word_sketch.model.sketch;

/** Plain concordance result carrying only sentence text and match position. */
public record ConcordanceHit(String sentence, int startOffset, int endOffset, String docId)
        implements ConcordanceResult {}
