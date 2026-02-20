package pl.marcinmilkowski.word_sketch.query;

import java.util.List;

/**
 * Common result classes for word sketch queries.
 * Extracted from WordSketchQueryExecutor for reuse across different executor implementations.
 */
public class QueryResults {

    private QueryResults() {
        // Utility class - prevent instantiation
    }

    /**
     * Result of a word sketch query containing collocation information.
     */
    public static class WordSketchResult {
        private final String lemma;
        private final String pos;
        private final long frequency;
        private final double logDice;
        private final double relativeFrequency;
        private final List<String> examples;

        public WordSketchResult(String lemma, String pos, long frequency,
                               double logDice, double relativeFrequency,
                               List<String> examples) {
            this.lemma = lemma;
            this.pos = pos;
            this.frequency = frequency;
            this.logDice = logDice;
            this.relativeFrequency = relativeFrequency;
            this.examples = examples;
        }

        public String getLemma() { return lemma; }
        public String getPos() { return pos; }
        public long getFrequency() { return frequency; }
        public double getLogDice() { return logDice; }
        public double getRelativeFrequency() { return relativeFrequency; }
        public List<String> getExamples() { return examples; }
    }

    /**
     * Result of a concordance query containing sentence context.
     */
    public static class ConcordanceResult {
        private final String sentence;
        private final String lemma;
        private final String tag;
        private final String word;
        private final int startOffset;
        private final int endOffset;
        private final String docId;

        public ConcordanceResult(String sentence, String lemma, String tag,
                                String word, int startOffset, int endOffset, String docId) {
            this.sentence = sentence;
            this.lemma = lemma;
            this.tag = tag;
            this.word = word;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.docId = docId;
        }

        /**
         * Simplified constructor without docId for backward compatibility.
         */
        public ConcordanceResult(String sentence, String lemma, String tag,
                                String word, int startOffset, int endOffset) {
            this(sentence, lemma, tag, word, startOffset, endOffset, null);
        }

        /**
         * Constructor for BlackLab-style results (snippet-based).
         */
        public ConcordanceResult(String snippet, int start, int end, String docId) {
            this(snippet, null, null, null, start, end, docId);
        }

        public String getSentence() { return sentence; }
        public String getLemma() { return lemma; }
        public String getTag() { return tag; }
        public String getWord() { return word; }
        public int getStartOffset() { return startOffset; }
        public int getEndOffset() { return endOffset; }
        public String getDocId() { return docId; }
    }
}
