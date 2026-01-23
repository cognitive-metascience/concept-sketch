package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenAndWindowTest {

    private List<Token> sampleTokens() {
        String sent = "the big problem is very serious";
        return List.of(
                new Token("the", "the", "dt", "det", 0, 1, sent),
                new Token("big", "big", "jj", "adj", 1, 1, sent),
                new Token("problem", "problem", "nn", "noun", 2, 1, sent),
                new Token("be", "is", "vb", "verb", 3, 1, sent),
                new Token("very", "very", "rb", "adv", 4, 1, sent),
                new Token("serious", "serious", "jj", "adj", 5, 1, sent)
        );
    }

    @Test
    void testTokenBasics() {
        Token t = new Token("run", "run", "vb", "verb", 0, 1, "run fast");
        assertEquals("run", t.getLemma());
        assertEquals("run", t.getWord());
        assertEquals("vb", t.getTag());
        assertEquals("verb", t.getPosGroup());
        assertEquals(0, t.getPosition());
        assertEquals("run fast", t.getSentence());
    }

    @Test
    void testTokenWindowFindInRangeForward() {
        TokenWindow w = new TokenWindow(sampleTokens(), 0, 5, 1);
        List<Token> around = w.findInRange(2, 1, 3);
        assertEquals(3, around.size());
        assertTrue(around.stream().anyMatch(t -> t.getWord().equals("is")));
        assertTrue(around.stream().anyMatch(t -> t.getWord().equals("very")));
        assertTrue(around.stream().anyMatch(t -> t.getWord().equals("serious")));
    }

    @Test
    void testTokenWindowFindInRangeBackward() {
        TokenWindow w = new TokenWindow(sampleTokens(), 0, 5, 1);
        List<Token> around = w.findInRange(2, -2, 0);
        assertEquals(3, around.size());
        assertTrue(around.stream().anyMatch(t -> t.getWord().equals("the")));
        assertTrue(around.stream().anyMatch(t -> t.getWord().equals("big")));
        assertTrue(around.stream().anyMatch(t -> t.getWord().equals("problem")));
    }

    @Test
    void testTokenWindowBounds() {
        TokenWindow w = new TokenWindow(sampleTokens(), 1, 4, 1);
        // request wider than window, should clamp
        List<Token> around = w.findInRange(2, -5, 5);
        assertTrue(around.size() <= 6);
        assertTrue(around.stream().anyMatch(t -> t.getWord().equals("big")));
        assertTrue(around.stream().anyMatch(t -> t.getWord().equals("very")));
    }
}
