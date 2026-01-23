package pl.marcinmilkowski.word_sketch.query;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a window of tokens from a single sentence.
 * Provides efficient access to tokens for CQL verification.
 */
public class TokenWindow {
    private final List<Token> tokens;
    private final int startDocId;
    private final int endDocId;
    private final int sentenceId;

    public TokenWindow(List<Token> tokens, int startDocId, int endDocId, int sentenceId) {
        this.tokens = tokens;
        this.startDocId = startDocId;
        this.endDocId = endDocId;
        this.sentenceId = sentenceId;
    }

    public List<Token> getTokens() { return tokens; }
    public int size() { return tokens.size(); }
    public Token get(int index) { return tokens.get(index); }
    public int getStartDocId() { return startDocId; }
    public int getEndDocId() { return endDocId; }
    public int getSentenceId() { return sentenceId; }

    /**
     * Find tokens within a position range relative to a base position.
     */
    public List<Token> findInRange(int basePosition, int minDist, int maxDist) {
        List<Token> result = new ArrayList<>();
        for (Token token : tokens) {
            int distance = token.getPosition() - basePosition;
            if (distance >= minDist && distance <= maxDist) {
                result.add(token);
            }
        }
        return result;
    }

    /**
     * Build a TokenWindow from Lucene documents.
     */
    public static TokenWindow fromDocuments(List<Document> docs, IndexReader reader,
                                            int startDocId, int sentenceId) throws IOException {
        List<Token> tokens = new ArrayList<>();
        int endDocId = startDocId;

        for (int i = 0; i < docs.size(); i++) {
            int docId = startDocId + i;
            Document doc = docs.get(i);
            endDocId = docId;

            String lemma = doc.get("lemma");
            String word = doc.get("word");
            String tag = doc.get("tag");
            String posGroup = doc.get("pos_group");
            int position = Integer.parseInt(doc.get("position"));
            String sentence = doc.get("sentence");

            tokens.add(new Token(lemma, word, tag, posGroup, position, sentenceId, sentence));
        }

        return new TokenWindow(tokens, startDocId, endDocId, sentenceId);
    }

    @Override
    public String toString() {
        return String.format("TokenWindow(sentenceId=%d, size=%d)", sentenceId, tokens.size());
    }
}
