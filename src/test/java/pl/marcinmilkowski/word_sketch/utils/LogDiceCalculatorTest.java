package pl.marcinmilkowski.word_sketch.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LogDiceCalculator.
 */
class LogDiceCalculatorTest {

    @Test
    void testPerfectAssociation() {
        // Perfect association: collocate only occurs with headword
        double logDice = LogDiceCalculator.compute(100, 100, 100);
        // log2(2 * 100 / (100 + 100)) + 14 = log2(1) + 14 = 0 + 14 = 14
        assertEquals(14.0, logDice, 0.001);
    }

    @Test
    void testModerateAssociation() {
        // Moderate association
        double logDice = LogDiceCalculator.compute(500, 1000, 1500);
        // log2(2 * 500 / (1000 + 1500)) + 14 = log2(0.4) + 14
        assertTrue(logDice > 12 && logDice < 14);
    }

    @Test
    void testWeakAssociation() {
        // Weak association: collocate occurs rarely with headword
        double logDice = LogDiceCalculator.compute(10, 1000, 50000);
        // Should be low but positive
        assertTrue(logDice > 0 && logDice < 10);
    }

    @Test
    void testZeroCollocateFrequency() {
        double logDice = LogDiceCalculator.compute(0, 1000, 1000);
        assertEquals(0.0, logDice, 0.001);
    }

    @Test
    void testZeroHeadwordFrequency() {
        double logDice = LogDiceCalculator.compute(100, 0, 100);
        assertEquals(0.0, logDice, 0.001);
    }

    @Test
    void testZeroCollocateTotal() {
        double logDice = LogDiceCalculator.compute(100, 100, 0);
        assertEquals(0.0, logDice, 0.001);
    }

    @Test
    void testNegativeFrequency() {
        // Should handle edge cases gracefully
        double logDice = LogDiceCalculator.compute(-10, 100, 100);
        assertEquals(0.0, logDice, 0.001);
    }

    @Test
    void testRelativeFrequency() {
        double relFreq = LogDiceCalculator.relativeFrequency(100, 1000);
        assertEquals(0.1, relFreq, 0.001);
    }

    @Test
    void testRelativeFrequencyZeroHeadword() {
        double relFreq = LogDiceCalculator.relativeFrequency(100, 0);
        assertEquals(0.0, relFreq, 0.001);
    }

    @Test
    void testLongParameters() {
        // Test with long parameters
        long collocateFreq = 5000L;
        long headwordFreq = 10000L;
        long collocateTotal = 15000L;

        double logDice = LogDiceCalculator.compute(collocateFreq, headwordFreq, collocateTotal);
        assertTrue(logDice > 0);
    }

    @Test
    void testFrequencyAggregator() {
        LogDiceCalculator.FrequencyAggregator aggregator = new LogDiceCalculator.FrequencyAggregator();

        aggregator.setHeadwordFrequency(1000);
        aggregator.addCollocate("big", "adj");
        aggregator.addCollocate("big", "adj");
        aggregator.addCollocate("big", "adj");
        aggregator.addCollocate("small", "adj");

        assertEquals(1000, aggregator.getHeadwordFrequency());
        assertEquals(3, aggregator.getCollocateTotal("big"));
        assertEquals(1, aggregator.getCollocateTotal("small"));

        LogDiceCalculator.CollocationResult result = aggregator.getResult("big", "adj", 3);
        assertEquals("big", result.getLemma());
        assertEquals("adj", result.getPos());
        assertEquals(3, result.getFrequency());
        assertEquals(1000, result.getHeadwordFrequency());
    }
}
