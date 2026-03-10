package pl.marcinmilkowski.word_sketch.tagging;

import pl.marcinmilkowski.word_sketch.utils.PosGroup;

/**
 * A single token with its POS tag, lemma, and position in a sentence.
 */
public final class TaggedToken {

    private final String word;
    private final String lemma;
    private final String tag;
    private final int position;

    public TaggedToken(String word, String lemma, String tag, int position) {
        this.word = word;
        this.lemma = lemma;
        this.tag = tag;
        this.position = position;
    }

    public String getWord() { return word; }
    public String getLemma() { return lemma; }
    public String getTag() { return tag; }
    public int getPosition() { return position; }

    public String getPosGroup() {
        if (tag == null) return PosGroup.OTHER.getValue();
        char firstChar = tag.charAt(0);
        switch (firstChar) {
            case 'N': return PosGroup.NOUN.getValue();
            case 'V': return PosGroup.VERB.getValue();
            case 'J': return PosGroup.ADJ.getValue();
            case 'R': return PosGroup.ADV.getValue();
            case 'D': return "det";
            case 'P': return "pron";
            case 'I': return "prep";
            case 'C': return "conj";
            case 'U': return "punct";
            default: return PosGroup.OTHER.getValue();
        }
    }

    @Override
    public String toString() {
        return word + "\t" + tag + "\t" + lemma;
    }
}
