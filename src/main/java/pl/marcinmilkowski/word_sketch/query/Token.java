package pl.marcinmilkowski.word_sketch.query;

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

    @Override
    public String toString() {
        return String.format("Token(%d: %s/%s)", position, word, tag);
    }
}
