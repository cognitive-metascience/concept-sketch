package pl.marcinmilkowski.word_sketch.model.sketch;

import org.jspecify.annotations.Nullable;

/** Scored collocate result produced by the BCQL scoring pipeline. */
public record CollocateResult(String sentence, @Nullable String rawXml,
                               int startOffset, int endOffset, String docId,
                               @Nullable String collocateLemma, long frequency, double logDice)
        implements ConcordanceResult {}
