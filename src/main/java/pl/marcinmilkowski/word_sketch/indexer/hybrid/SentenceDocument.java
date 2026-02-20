package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.util.List;
import java.util.Collections;

/**
 * Represents a sentence document for the hybrid index.
 * In the hybrid index, each document is a sentence containing multiple tokens.
 */
public record SentenceDocument(
    int sentenceId,
    String text,
    List<Token> tokens
) {
    /**
     * Represents a single token within a sentence.
     */
    public record Token(
        int position,
        String word,
        String lemma,
        String tag,
        int startOffset,
        int endOffset,
        String deprel
    ) {
        /**
         * Gets the POS group (broad category) from the tag.
         * Maps UD tags to simplified categories.
         */
        public String getPosGroup() {
            if (tag == null || tag.isEmpty()) {
                return "other";
            }
            String upperTag = tag.toUpperCase();
            if (upperTag.startsWith("NOUN") || upperTag.startsWith("PROPN")) {
                return "noun";
            } else if (upperTag.startsWith("VERB") || upperTag.startsWith("AUX")) {
                return "verb";
            } else if (upperTag.startsWith("ADJ")) {
                return "adj";
            } else if (upperTag.startsWith("ADV")) {
                return "adv";
            } else if (upperTag.startsWith("ADP")) {
                return "adp";
            } else if (upperTag.startsWith("DET")) {
                return "det";
            } else if (upperTag.startsWith("PRON")) {
                return "pron";
            } else if (upperTag.startsWith("CONJ") || upperTag.startsWith("CCONJ") || upperTag.startsWith("SCONJ")) {
                return "conj";
            } else if (upperTag.startsWith("NUM")) {
                return "num";
            } else if (upperTag.startsWith("PART")) {
                return "part";
            } else if (upperTag.startsWith("INTJ")) {
                return "intj";
            } else if (upperTag.startsWith("PUNCT")) {
                return "punct";
            } else if (upperTag.startsWith("SYM")) {
                return "sym";
            } else if (upperTag.startsWith("X")) {
                return "other";
            }
            return "other";
        }
    }

    /**
     * Creates an immutable SentenceDocument.
     */
    public SentenceDocument {
        tokens = tokens == null ? Collections.emptyList() : List.copyOf(tokens);
    }

    /**
     * Gets the number of tokens in this sentence.
     */
    public int tokenCount() {
        return tokens.size();
    }

    /**
     * Gets a token at the specified position.
     * @param position 0-based position
     * @return the token, or null if position is out of bounds
     */
    public Token getToken(int position) {
        if (position < 0 || position >= tokens.size()) {
            return null;
        }
        return tokens.get(position);
    }

    /**
     * Builder for creating SentenceDocument instances.
     */
    public static class Builder {
        private int sentenceId;
        private String text;
        private final java.util.ArrayList<Token> tokens = new java.util.ArrayList<>();

        public Builder sentenceId(int sentenceId) {
            this.sentenceId = sentenceId;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder addToken(int position, String word, String lemma, String tag,
                               int startOffset, int endOffset) {
            tokens.add(new Token(position, word, lemma, tag, startOffset, endOffset, null));
            return this;
        }

        public Builder addToken(int position, String word, String lemma, String tag,
                               int startOffset, int endOffset, String deprel) {
            tokens.add(new Token(position, word, lemma, tag, startOffset, endOffset, deprel));
            return this;
        }

        public Builder addToken(Token token) {
            tokens.add(token);
            return this;
        }

        public SentenceDocument build() {
            return new SentenceDocument(sentenceId, text, tokens);
        }
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
}
