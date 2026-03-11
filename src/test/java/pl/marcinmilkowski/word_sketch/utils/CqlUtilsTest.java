package pl.marcinmilkowski.word_sketch.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CqlUtilsTest {

    // ── escapeForRegex ────────────────────────────────────────────────────────

    @Test
    void escapeForRegex_null_returnsEmpty() {
        assertEquals("", CqlUtils.escapeForRegex(null));
    }

    @Test
    void escapeForRegex_emptyString_returnsEmpty() {
        assertEquals("", CqlUtils.escapeForRegex(""));
    }

    @Test
    void escapeForRegex_plainWord_unchanged() {
        assertEquals("hello", CqlUtils.escapeForRegex("hello"));
    }

    @Test
    void escapeForRegex_backslash_isDoubled() {
        assertEquals("a\\\\b", CqlUtils.escapeForRegex("a\\b"));
    }

    @Test
    void escapeForRegex_doubleQuote_isEscaped() {
        assertEquals("say \\\"hi\\\"", CqlUtils.escapeForRegex("say \"hi\""));
    }

    @Test
    void escapeForRegex_backslashAndQuote_bothEscaped() {
        assertEquals("\\\\\\\"", CqlUtils.escapeForRegex("\\\""));
    }

    // ── extractConstraintAttribute ────────────────────────────────────────────

    @Test
    void extractConstraintAttribute_null_returnsNull() {
        assertNull(CqlUtils.extractConstraintAttribute(null, "xpos"));
    }

    @Test
    void extractConstraintAttribute_presentAttribute_returnsFullFragment() {
        String constraint = "[xpos=\"NN.*\" & lemma=\"dog\"]";
        String result = CqlUtils.extractConstraintAttribute(constraint, "xpos");
        assertEquals("xpos=\"NN.*\"", result);
    }

    @Test
    void extractConstraintAttribute_lemmaAttribute_returnsCorrectFragment() {
        String constraint = "[lemma=\"cat\"]";
        String result = CqlUtils.extractConstraintAttribute(constraint, "lemma");
        assertEquals("lemma=\"cat\"", result);
    }

    @Test
    void extractConstraintAttribute_missingAttribute_returnsNull() {
        String constraint = "[lemma=\"cat\"]";
        assertNull(CqlUtils.extractConstraintAttribute(constraint, "xpos"));
    }

    @Test
    void extractConstraintAttribute_tagAttribute_returnsFullFragment() {
        String constraint = "[tag=\"JJ.*\"]";
        String result = CqlUtils.extractConstraintAttribute(constraint, "tag");
        assertEquals("tag=\"JJ.*\"", result);
    }

    @Test
    void extractConstraintAttribute_emptyValue_returnsFragment() {
        String constraint = "[xpos=\"\"]";
        String result = CqlUtils.extractConstraintAttribute(constraint, "xpos");
        assertEquals("xpos=\"\"", result);
    }
}
