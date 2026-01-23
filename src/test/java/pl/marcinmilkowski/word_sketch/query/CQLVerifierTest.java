package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.Test;
import pl.marcinmilkowski.word_sketch.grammar.CQLParser;
import pl.marcinmilkowski.word_sketch.grammar.CQLPattern;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CQLVerifier - tests labeled positions, agreement rules,
 * and complex pattern matching.
 */
class CQLVerifierTest {

    private final CQLParser parser = new CQLParser();

    /**
     * Create a test token window with predefined tokens.
     */
    private TokenWindow createTestWindow() {
        // Simulate: "the big problem is very serious"
        Token t1 = new Token("the", "the", "dt", "det", 0, 1, "the big problem is very serious");
        Token t2 = new Token("big", "big", "jj", "adj", 1, 1, "the big problem is very serious");
        Token t3 = new Token("problem", "problem", "nn", "noun", 2, 1, "the big problem is very serious");
        Token t4 = new Token("be", "is", "vb", "verb", 3, 1, "the big problem is very serious");
        Token t5 = new Token("very", "very", "rb", "adv", 4, 1, "the big problem is very serious");
        Token t6 = new Token("serious", "serious", "jj", "adj", 5, 1, "the big problem is very serious");

        List<Token> tokens = List.of(t1, t2, t3, t4, t5, t6);
        return new TokenWindow(tokens, 0, 5, 1);
    }

    @Test
    void testSimpleConstraintMatching() {
        CQLPattern pattern = parser.parse("[tag=jj]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // Headword is at position 2 (problem), looking for adjectives
        // Should find "big" at position 1
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should find adjective near problem");
    }

    @Test
    void testVerbPattern() {
        // Pattern: verb
        CQLPattern pattern = parser.parse("[tag=vb]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // "is" is at position 3 (verb)
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should find verb near problem");
    }

    @Test
    void testNounPattern() {
        // Pattern: noun
        CQLPattern pattern = parser.parse("[tag=nn]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // "problem" is the headword at position 2, but we're looking for OTHER nouns
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should find noun near problem");
    }

    @Test
    void testAdverbPattern() {
        // Pattern: adverb
        CQLPattern pattern = parser.parse("[tag=rb]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // "very" is at position 4 (adverb)
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should find adverb near problem");
    }

    @Test
    void testNegatedConstraint() {
        // Pattern: not a noun
        CQLPattern pattern = parser.parse("[tag!='nn']");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // "big" is JJ, should match
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should match non-noun token");
    }

    @Test
    void testWildcardPattern() {
        // Pattern: all adjectives (jj.*)
        CQLPattern pattern = parser.parse("[tag=jj.*]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should match adjective wildcard pattern");
    }

    @Test
    void testLabeledPositionParsing() {
        // Test that labeled positions are parsed correctly
        CQLPattern pattern = parser.parse("1:\"JJ.*\" 2:\"NN\"");
        List<CQLPattern.PatternElement> elements = pattern.getElements();

        assertEquals(2, elements.size());
        assertEquals(1, elements.get(0).getPosition());
        assertEquals("JJ.*", elements.get(0).getTarget());
        assertEquals(2, elements.get(1).getPosition());
        assertEquals("NN", elements.get(1).getTarget());
    }

    @Test
    void testPosGroupMatching() {
        // Pattern: adjective POS group
        CQLPattern pattern = parser.parse("[pos_group=adj]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // "big" has pos_group=adj
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should match by POS group");
    }

    @Test
    void testWordConstraint() {
        // Pattern: specific word
        CQLPattern pattern = parser.parse("[word=big]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should match by word");
    }

    @Test
    void testTokenWindowFindInRange() {
        TokenWindow window = createTestWindow();

        // Find tokens 1-3 positions from headword at 2
        List<Token> range = window.findInRange(2, 1, 3);

        assertEquals(3, range.size());
        // Position 3: is (vb), Position 4: very (rb), Position 5: serious (jj)
        assertTrue(range.stream().anyMatch(t -> t.getWord().equals("is")));
        assertTrue(range.stream().anyMatch(t -> t.getWord().equals("very")));
        assertTrue(range.stream().anyMatch(t -> t.getWord().equals("serious")));
    }

    @Test
    void testDeterminerPattern() {
        // Pattern: determiner
        CQLPattern pattern = parser.parse("[tag=dt]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // "the" is at position 0 (determiner)
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should find determiner near problem");
    }

    @Test
    void testHasLabeledPositions() {
        // Test that labeled positions are detected
        CQLPattern pattern1 = parser.parse("1:\"VB.*\"");
        CQLPattern pattern2 = parser.parse("[tag=jj]");

        // Both should work without error
        assertEquals(1, pattern1.getElements().size());
        assertEquals(1, pattern2.getElements().size());
    }

    @Test
    void testMultiElementPattern() {
        // Pattern: adjective and then verb
        CQLPattern pattern = parser.parse("[tag=jj] [tag=vb]");
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // Should find both "big" (jj) at position 1 and "is" (vb) at position 3
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        assertTrue(result.isMatched(), "Should match multi-element pattern");
    }

    @Test
    void testNonMatch() {
        // Pattern: something that doesn't exist near problem
        CQLPattern pattern = parser.parse("[tag=cd]");  // Cardinal number
        TokenWindow window = createTestWindow();
        CQLVerifier verifier = new CQLVerifier();

        // No CD tags in our test window
        CQLVerifier.VerificationResult result = verifier.verifyForCollocate(pattern, window, 2);

        // This should return false since there's no cardinal number
        assertFalse(result.isMatched());
    }
}
