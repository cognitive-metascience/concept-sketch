package pl.marcinmilkowski.word_sketch.regression;

import pl.marcinmilkowski.word_sketch.query.WordSketchQueryExecutor.WordSketchResult;

import java.util.*;

/**
 * Validates equivalence between results from different QueryExecutor implementations.
 * 
 * This validator ensures that the hybrid index produces the same (or acceptably similar)
 * results as the legacy token-per-document index.
 * 
 * Validation levels:
 * - STRICT: Exact match required (same collocates, same order, same frequencies)
 * - STATISTICAL: Allow minor differences due to sampling (±5% on frequencies)
 * - RANKING: Allow frequency differences but require same top-N ranking
 */
public class ResultEquivalenceValidator {

    public enum ValidationLevel {
        /** Exact match required */
        STRICT,
        /** Allow ±5% frequency variance */
        STATISTICAL,
        /** Only check ranking order, not exact frequencies */
        RANKING
    }

    private final ValidationLevel level;
    private final double frequencyTolerance;
    private final double logDiceTolerance;
    
    /**
     * Create a validator with default statistical tolerance.
     */
    public ResultEquivalenceValidator() {
        this(ValidationLevel.STATISTICAL, 0.05, 0.01);
    }

    /**
     * Create a validator with specific tolerances.
     * 
     * @param level Validation strictness level
     * @param frequencyTolerance Relative tolerance for frequency (e.g., 0.05 = 5%)
     * @param logDiceTolerance Absolute tolerance for logDice (e.g., 0.01)
     */
    public ResultEquivalenceValidator(ValidationLevel level, 
                                       double frequencyTolerance,
                                       double logDiceTolerance) {
        this.level = level;
        this.frequencyTolerance = frequencyTolerance;
        this.logDiceTolerance = logDiceTolerance;
    }

    /**
     * Validate that two result sets are equivalent.
     * 
     * @param expected Results from legacy/baseline executor
     * @param actual Results from new/hybrid executor
     * @return Validation result with details
     */
    public ValidationResult validate(List<WordSketchResult> expected,
                                      List<WordSketchResult> actual) {
        
        ValidationResult result = new ValidationResult();
        result.expectedCount = expected.size();
        result.actualCount = actual.size();
        
        // Check count
        if (expected.size() != actual.size()) {
            result.addError("Result count mismatch: expected %d, got %d",
                           expected.size(), actual.size());
        }
        
        // Build maps for comparison - using lemma as the collocate identifier
        Map<String, WordSketchResult> expectedByLemma = new LinkedHashMap<>();
        Map<String, WordSketchResult> actualByLemma = new LinkedHashMap<>();
        
        for (WordSketchResult r : expected) {
            expectedByLemma.put(r.getLemma(), r);
        }
        for (WordSketchResult r : actual) {
            actualByLemma.put(r.getLemma(), r);
        }
        
        // Check for missing collocates
        Set<String> missingInActual = new HashSet<>(expectedByLemma.keySet());
        missingInActual.removeAll(actualByLemma.keySet());
        
        Set<String> extraInActual = new HashSet<>(actualByLemma.keySet());
        extraInActual.removeAll(expectedByLemma.keySet());
        
        if (!missingInActual.isEmpty()) {
            result.addError("Missing collocates in actual: %s", missingInActual);
            result.missingCollocates = new ArrayList<>(missingInActual);
        }
        
        if (!extraInActual.isEmpty()) {
            result.addWarning("Extra collocates in actual: %s", extraInActual);
            result.extraCollocates = new ArrayList<>(extraInActual);
        }
        
        // Check shared collocates
        Set<String> shared = new HashSet<>(expectedByLemma.keySet());
        shared.retainAll(actualByLemma.keySet());
        
        for (String lemma : shared) {
            WordSketchResult exp = expectedByLemma.get(lemma);
            WordSketchResult act = actualByLemma.get(lemma);
            
            // Check frequency
            if (!frequencyMatches(exp.getFrequency(), act.getFrequency())) {
                result.addError("Frequency mismatch for '%s': expected %d, got %d (tolerance: %.1f%%)",
                               lemma, exp.getFrequency(), act.getFrequency(),
                               frequencyTolerance * 100);
            }
            
            // Check logDice
            if (!logDiceMatches(exp.getLogDice(), act.getLogDice())) {
                result.addError("LogDice mismatch for '%s': expected %.4f, got %.4f (tolerance: %.4f)",
                               lemma, exp.getLogDice(), act.getLogDice(),
                               logDiceTolerance);
            }
        }
        
        // Check ranking (if not strict mode)
        if (level == ValidationLevel.RANKING || level == ValidationLevel.STATISTICAL) {
            validateRanking(expected, actual, result);
        }
        
        // Determine pass/fail
        result.passed = determinePassFail(result);
        
        return result;
    }

    /**
     * Validate ranking order of top results.
     */
    private void validateRanking(List<WordSketchResult> expected,
                                  List<WordSketchResult> actual,
                                  ValidationResult result) {
        
        int topN = Math.min(10, Math.min(expected.size(), actual.size()));
        
        List<String> expectedTop = new ArrayList<>();
        List<String> actualTop = new ArrayList<>();
        
        for (int i = 0; i < topN; i++) {
            if (i < expected.size()) expectedTop.add(expected.get(i).getLemma());
            if (i < actual.size()) actualTop.add(actual.get(i).getLemma());
        }
        
        // Calculate overlap in top-N
        Set<String> expectedSet = new HashSet<>(expectedTop);
        Set<String> actualSet = new HashSet<>(actualTop);
        
        int overlap = 0;
        for (String s : expectedSet) {
            if (actualSet.contains(s)) overlap++;
        }
        
        result.topNOverlap = (double) overlap / topN;
        
        if (result.topNOverlap < 0.8) {  // Less than 80% overlap in top 10
            result.addWarning("Top-%d overlap is only %.0f%%", topN, result.topNOverlap * 100);
        }
        
        // Check if #1 result matches
        if (!expectedTop.isEmpty() && !actualTop.isEmpty()) {
            if (!expectedTop.get(0).equals(actualTop.get(0))) {
                result.addWarning("Top result differs: expected '%s', got '%s'",
                                 expectedTop.get(0), actualTop.get(0));
            }
        }
    }

    private boolean frequencyMatches(long expected, long actual) {
        if (level == ValidationLevel.STRICT) {
            return expected == actual;
        }
        
        if (level == ValidationLevel.RANKING) {
            return true;  // Don't check frequency in ranking mode
        }
        
        // Statistical mode: allow tolerance
        if (expected == 0 && actual == 0) return true;
        if (expected == 0) return actual <= 2;  // Allow small absolute difference
        
        double diff = Math.abs(expected - actual) / (double) expected;
        return diff <= frequencyTolerance;
    }

    private boolean logDiceMatches(double expected, double actual) {
        if (level == ValidationLevel.RANKING) {
            return true;  // Don't check logDice in ranking mode
        }
        
        return Math.abs(expected - actual) <= logDiceTolerance;
    }

    private boolean determinePassFail(ValidationResult result) {
        switch (level) {
            case STRICT:
                return result.errors.isEmpty();
                
            case STATISTICAL:
                // Allow some missing collocates but require 95%+ overlap
                int totalExpected = result.expectedCount;
                int missing = result.missingCollocates != null ? 
                              result.missingCollocates.size() : 0;
                double collocateOverlap = totalExpected > 0 ? 
                    (totalExpected - missing) / (double) totalExpected : 1.0;
                
                return collocateOverlap >= 0.95 && result.topNOverlap >= 0.8;
                
            case RANKING:
                return result.topNOverlap >= 0.7;
                
            default:
                return false;
        }
    }

    /**
     * Result of validation.
     */
    public static class ValidationResult {
        public boolean passed;
        public int expectedCount;
        public int actualCount;
        public double topNOverlap = 1.0;
        public List<String> missingCollocates;
        public List<String> extraCollocates;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();

        public void addError(String format, Object... args) {
            errors.add(String.format(format, args));
        }

        public void addWarning(String format, Object... args) {
            warnings.add(String.format(format, args));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(passed ? "PASSED" : "FAILED");
            sb.append(String.format(" (expected=%d, actual=%d, topNOverlap=%.0f%%)",
                                    expectedCount, actualCount, topNOverlap * 100));
            
            if (!errors.isEmpty()) {
                sb.append("\nErrors:");
                for (String e : errors) {
                    sb.append("\n  - ").append(e);
                }
            }
            
            if (!warnings.isEmpty()) {
                sb.append("\nWarnings:");
                for (String w : warnings) {
                    sb.append("\n  - ").append(w);
                }
            }
            
            return sb.toString();
        }
    }

    /**
     * Compare two sets of baselines from different executors.
     */
    public Map<String, Map<String, ValidationResult>> validateBaselines(
            Map<String, List<RegressionTestDataGenerator.QueryBaseline>> expected,
            Map<String, List<RegressionTestDataGenerator.QueryBaseline>> actual) {
        
        Map<String, Map<String, ValidationResult>> results = new LinkedHashMap<>();
        
        for (String headword : expected.keySet()) {
            Map<String, ValidationResult> headwordResults = new LinkedHashMap<>();
            
            List<RegressionTestDataGenerator.QueryBaseline> expList = 
                expected.getOrDefault(headword, Collections.emptyList());
            List<RegressionTestDataGenerator.QueryBaseline> actList = 
                actual.getOrDefault(headword, Collections.emptyList());
            
            // Match by pattern
            Map<String, RegressionTestDataGenerator.QueryBaseline> actByPattern = 
                new HashMap<>();
            for (RegressionTestDataGenerator.QueryBaseline b : actList) {
                actByPattern.put(b.pattern, b);
            }
            
            for (RegressionTestDataGenerator.QueryBaseline expBaseline : expList) {
                RegressionTestDataGenerator.QueryBaseline actBaseline = 
                    actByPattern.get(expBaseline.pattern);
                
                if (actBaseline == null) {
                    ValidationResult vr = new ValidationResult();
                    vr.passed = false;
                    vr.addError("Pattern not found in actual results: %s", 
                               expBaseline.pattern);
                    headwordResults.put(expBaseline.pattern, vr);
                } else {
                    // Convert baselines to result lists for comparison
                    List<WordSketchResult> expResults = 
                        baselineToResults(expBaseline.results);
                    List<WordSketchResult> actResults = 
                        baselineToResults(actBaseline.results);
                    
                    ValidationResult vr = validate(expResults, actResults);
                    headwordResults.put(expBaseline.pattern, vr);
                }
            }
            
            results.put(headword, headwordResults);
        }
        
        return results;
    }

    private List<WordSketchResult> baselineToResults(
            List<RegressionTestDataGenerator.CollocationBaseline> baselines) {
        List<WordSketchResult> results = new ArrayList<>();
        for (RegressionTestDataGenerator.CollocationBaseline b : baselines) {
            // Create a minimal WordSketchResult for comparison
            // Using lemma field for the collocate, pos=null, relativeFrequency=0
            results.add(new WordSketchResult(
                b.collocate, null, b.frequency, b.logDice, 0.0, null));
        }
        return results;
    }
}
