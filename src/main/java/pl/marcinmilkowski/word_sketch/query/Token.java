package pl.marcinmilkowski.word_sketch.query;

import java.util.regex.Pattern;

/**
 * Represents a single token in a sentence for CQL verification.
 * Contains all fields needed to evaluate token predicates.
 */
public class Token {
    private final String lemma;
    private final String word;
    private final String tag;
    private final String posGroup;
    private final int position;
    private final int sentenceId;
    private final String sentence;

    public Token(String lemma, String word, String tag, String posGroup,
                 int position, int sentenceId, String sentence) {
        this.lemma = lemma != null ? lemma : "";
        this.word = word != null ? word : "";
        this.tag = tag != null ? tag : "";
        this.posGroup = posGroup != null ? posGroup : "";
        this.position = position;
        this.sentenceId = sentenceId;
        this.sentence = sentence != null ? sentence : "";
    }

    public String getLemma() { return lemma; }
    public String getWord() { return word; }
    public String getTag() { return tag; }
    public String getPosGroup() { return posGroup; }
    public int getPosition() { return position; }
    public int getSentenceId() { return sentenceId; }
    public String getSentence() { return sentence; }

    /**
     * Check if this token matches a tag pattern (e.g., "jj.*", "nn").
     */
    public boolean matchesTag(String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        pattern = pattern.toLowerCase();
        return matchesPattern(tag, pattern);
    }

    /**
     * Check if this token matches a lemma pattern.
     */
    public boolean matchesLemma(String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        return matchesPattern(lemma.toLowerCase(), pattern.toLowerCase());
    }

    /**
     * Check if this token matches a word pattern.
     */
    public boolean matchesWord(String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        return matchesPattern(word.toLowerCase(), pattern.toLowerCase());
    }

    /**
     * Check if this token matches a pos_group pattern.
     */
    public boolean matchesPosGroup(String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        return matchesPattern(posGroup.toLowerCase(), pattern.toLowerCase());
    }

    private boolean matchesPattern(String value, String pattern) {
        // Convert wildcard pattern to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .replace("[^", "(?!")
            .replace("[", "(")
            .replace("]", ")");
        return Pattern.matches(regex, value);
    }

    @Override
    public String toString() {
        return String.format("Token(%d: %s/%s)", position, word, tag);
    }
}
