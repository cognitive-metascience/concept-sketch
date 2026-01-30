package pl.marcinmilkowski.word_sketch.regression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.marcinmilkowski.word_sketch.query.WordSketchQueryExecutor.WordSketchResult;
import pl.marcinmilkowski.word_sketch.regression.ResultEquivalenceValidator.ValidationLevel;
import pl.marcinmilkowski.word_sketch.regression.ResultEquivalenceValidator.ValidationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the regression test infrastructure.
 * 
 * These tests verify that the ResultEquivalenceValidator correctly
 * detects differences between result sets at various tolerance levels.
 */
class ResultEquivalenceValidatorTest {

    private ResultEquivalenceValidator strictValidator;
    private ResultEquivalenceValidator statisticalValidator;
    private ResultEquivalenceValidator rankingValidator;

    @BeforeEach
    void setUp() {
        strictValidator = new ResultEquivalenceValidator(
            ValidationLevel.STRICT, 0.0, 0.0);
        statisticalValidator = new ResultEquivalenceValidator(
            ValidationLevel.STATISTICAL, 0.05, 0.01);
        rankingValidator = new ResultEquivalenceValidator(
            ValidationLevel.RANKING, 0.0, 0.0);
    }

    @Test
    void testExactMatchPasses() {
        List<WordSketchResult> results = createResults(
            result("big", 100, 8.5),
            result("small", 50, 7.2),
            result("red", 25, 6.1)
        );

        ValidationResult vr = strictValidator.validate(results, results);
        
        assertTrue(vr.passed, "Exact same results should pass strict validation");
        assertTrue(vr.errors.isEmpty(), "No errors expected");
        assertEquals(3, vr.expectedCount);
        assertEquals(3, vr.actualCount);
    }

    @Test
    void testMissingCollocateFails() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.5),
            result("small", 50, 7.2),
            result("red", 25, 6.1)
        );
        
        List<WordSketchResult> actual = createResults(
            result("big", 100, 8.5),
            result("small", 50, 7.2)
            // "red" is missing
        );

        ValidationResult vr = strictValidator.validate(expected, actual);
        
        assertFalse(vr.passed, "Missing collocate should fail strict validation");
        assertNotNull(vr.missingCollocates);
        assertTrue(vr.missingCollocates.contains("red"));
    }

    @Test
    void testFrequencyMismatchFailsStrict() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.5)
        );
        
        List<WordSketchResult> actual = createResults(
            result("big", 95, 8.5)  // 5% less frequency
        );

        ValidationResult vr = strictValidator.validate(expected, actual);
        
        assertFalse(vr.passed, "Frequency mismatch should fail strict validation");
        assertFalse(vr.errors.isEmpty());
    }

    @Test
    void testFrequencyWithinTolerancePassesStatistical() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.5)
        );
        
        List<WordSketchResult> actual = createResults(
            result("big", 95, 8.5)  // 5% less frequency - within tolerance
        );

        ValidationResult vr = statisticalValidator.validate(expected, actual);
        
        assertTrue(vr.passed, "5% frequency difference should pass statistical validation");
    }

    @Test
    void testFrequencyBeyondToleranceFailsStatistical() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.5)
        );
        
        List<WordSketchResult> actual = createResults(
            result("big", 80, 8.5)  // 20% less frequency - beyond tolerance
        );

        ValidationResult vr = statisticalValidator.validate(expected, actual);
        
        // This should fail due to frequency mismatch, but the overall pass/fail
        // depends on collocate overlap and ranking
        assertFalse(vr.errors.isEmpty(), "20% frequency difference should generate error");
    }

    @Test
    void testLogDiceMismatchDetected() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.50)
        );
        
        List<WordSketchResult> actual = createResults(
            result("big", 100, 8.55)  // 0.05 logDice difference
        );

        // With 0.01 tolerance
        ValidationResult vr = statisticalValidator.validate(expected, actual);
        
        assertFalse(vr.errors.isEmpty(), "LogDice difference beyond tolerance should generate error");
    }

    @Test
    void testRankingModeIgnoresFrequencyDifferences() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.5),
            result("small", 50, 7.2)
        );
        
        List<WordSketchResult> actual = createResults(
            result("big", 200, 9.0),   // Very different frequency/logDice
            result("small", 10, 5.0)   // Very different frequency/logDice
        );

        ValidationResult vr = rankingValidator.validate(expected, actual);
        
        // Should pass because same lemmas in same order
        assertTrue(vr.passed, "Ranking mode should pass with same order");
        assertEquals(1.0, vr.topNOverlap, 0.01);
    }

    @Test
    void testRankingOrderMismatch() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.5),
            result("small", 50, 7.2),
            result("red", 30, 6.5)
        );
        
        List<WordSketchResult> actual = createResults(
            result("small", 100, 8.5),   // Different order
            result("red", 50, 7.2),
            result("big", 30, 6.5)
        );

        ValidationResult vr = statisticalValidator.validate(expected, actual);
        
        // All collocates present, so should have 100% overlap
        assertEquals(1.0, vr.topNOverlap, 0.01);
        // But there should be a warning about different top result
        assertFalse(vr.warnings.isEmpty(), "Should warn about different top result");
    }

    @Test
    void testEmptyResultsPass() {
        List<WordSketchResult> empty = new ArrayList<>();
        
        ValidationResult vr = strictValidator.validate(empty, empty);
        
        assertTrue(vr.passed, "Empty result sets should match");
        assertEquals(0, vr.expectedCount);
        assertEquals(0, vr.actualCount);
    }

    @Test
    void testExtraCollocatesGenerateWarning() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.5)
        );
        
        List<WordSketchResult> actual = createResults(
            result("big", 100, 8.5),
            result("small", 50, 7.2)  // Extra
        );

        ValidationResult vr = statisticalValidator.validate(expected, actual);
        
        assertNotNull(vr.extraCollocates);
        assertTrue(vr.extraCollocates.contains("small"));
        // Extra collocates are warnings, not errors
        assertFalse(vr.warnings.isEmpty());
    }

    @Test
    void testTopNOverlapCalculation() {
        // Create 15 results each
        List<WordSketchResult> expected = new ArrayList<>();
        List<WordSketchResult> actual = new ArrayList<>();
        
        for (int i = 0; i < 15; i++) {
            expected.add(result("word" + i, 100 - i, 8.0 - i * 0.1));
        }
        
        // First 8 same, next 7 different
        for (int i = 0; i < 8; i++) {
            actual.add(result("word" + i, 100 - i, 8.0 - i * 0.1));
        }
        for (int i = 0; i < 7; i++) {
            actual.add(result("other" + i, 50 - i, 7.0 - i * 0.1));
        }

        ValidationResult vr = statisticalValidator.validate(expected, actual);
        
        // Top 10 overlap should be 8/10 = 80%
        assertEquals(0.8, vr.topNOverlap, 0.01);
    }

    @Test
    void testValidationResultToString() {
        List<WordSketchResult> expected = createResults(
            result("big", 100, 8.5),
            result("small", 50, 7.2)
        );
        
        List<WordSketchResult> actual = createResults(
            result("big", 80, 8.5)  // Different frequency, missing "small"
        );

        ValidationResult vr = strictValidator.validate(expected, actual);
        String str = vr.toString();
        
        assertFalse(vr.passed);
        assertTrue(str.contains("FAILED"));
        assertTrue(str.contains("expected=2"));
        assertTrue(str.contains("actual=1"));
    }

    // Helper methods

    private WordSketchResult result(String lemma, long frequency, double logDice) {
        return new WordSketchResult(lemma, null, frequency, logDice, 0.0, null);
    }

    private List<WordSketchResult> createResults(WordSketchResult... results) {
        return new ArrayList<>(Arrays.asList(results));
    }
}
