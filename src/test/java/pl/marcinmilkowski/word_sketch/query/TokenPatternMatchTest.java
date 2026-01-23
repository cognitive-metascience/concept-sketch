package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenPatternMatchTest {

    private final Token t = new Token("be", "is", "vbz", "verb", 0, 1, "it is fine");

    @Test
    void testMatchesWord() {
        assertTrue(t.matchesWord("is"));
        assertTrue(t.matchesWord("i?"));
        assertTrue(t.matchesWord("*s"));
        assertFalse(t.matchesWord("was"));
    }

    @Test
    void testMatchesLemma() {
        assertTrue(t.matchesLemma("be"));
        assertTrue(t.matchesLemma("b*"));
        assertFalse(t.matchesLemma("[^b].*")); // negative class simulation handled in implementation
    }

    @Test
    void testMatchesTag() {
        assertTrue(t.matchesTag("vb*"));
        assertTrue(t.matchesTag("VBZ"));
        assertFalse(t.matchesTag("nn*"));
    }

    @Test
    void testMatchesPosGroup() {
        assertTrue(t.matchesPosGroup("verb"));
        assertTrue(t.matchesPosGroup("ver?"));
        assertFalse(t.matchesPosGroup("noun"));
    }
}
