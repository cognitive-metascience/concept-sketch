package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PredicatesTest {

    private Token token(String word, String lemma, String tag, String posGroup) {
        return new Token(lemma, word, tag, posGroup, 0, 0, word);
    }

    @Test
    void testWordPredicate() {
        WordPredicate p = new WordPredicate("house");
        assertTrue(p.test(token("house", "house", "nn", "noun")));
        assertFalse(p.test(token("home", "home", "nn", "noun")));
    }

    @Test
    void testLemmaPredicate() {
        LemmaPredicate p = new LemmaPredicate("be");
        assertTrue(p.test(token("is", "be", "vb", "verb")));
        assertFalse(p.test(token("was", "do", "vb", "verb")));
    }

    @Test
    void testTagPredicateWithWildcards() {
        // exact via wildcard engine
        TagPredicate exact = new TagPredicate("nn");
        assertTrue(exact.test(token("cat", "cat", "nn", "noun")));
        assertFalse(exact.test(token("cats", "cat", "nns", "noun")));

        // wildcard pattern
        TagPredicate wildcard = new TagPredicate("n*");
        assertTrue(wildcard.test(token("cats", "cat", "nns", "noun")));
        assertFalse(wildcard.test(token("run", "run", "vb", "verb")));
    }

    @Test
    void testPosGroupPredicate() {
        PosGroupPredicate p = new PosGroupPredicate("adj");
        assertTrue(p.test(token("big", "big", "jj", "adj")));
        assertFalse(p.test(token("cat", "cat", "nn", "noun")));
    }

    @Test
    void testAndOrNotPredicates() {
        WordPredicate w = new WordPredicate("cat");
        TagPredicate t = new TagPredicate("n*");

        AndPredicate and = new AndPredicate(List.of(w, t));
        OrPredicate or = new OrPredicate(List.of(w, new TagPredicate("vb")));

        Token tok1 = token("cat", "cat", "nn", "noun");
        Token tok2 = token("run", "run", "vb", "verb");

        assertTrue(and.test(tok1));
        assertFalse(and.test(tok2));

        assertTrue(or.test(tok1));
        assertTrue(or.test(tok2));

        NotPredicate notNoun = new NotPredicate(new TagPredicate("n*"));
        assertFalse(notNoun.test(tok1));
        assertTrue(notNoun.test(tok2));
    }
}
