package pl.marcinmilkowski.word_sketch.query;

import pl.marcinmilkowski.word_sketch.grammar.CQLPattern;

import java.util.*;

/**
 * Exact CQL pattern verifier with support for:
 * - Labeled positions (captures)
 * - Agreement rules
 * - Distance ranges (including negative)
 * - Repetition {m,n}
 * - AND/OR/NOT constraints at same position
 */
public class CQLVerifier {

    /**
     * Result of verifying a pattern against a token window.
     */
    public static class VerificationResult {
        private final boolean matched;
        private final Map<Integer, Token> captures;
        private final List<TokenWindow> matchedWindows;

        public VerificationResult(boolean matched, Map<Integer, Token> captures) {
            this.matched = matched;
            this.captures = captures;
            this.matchedWindows = Collections.emptyList();
        }

        public VerificationResult(boolean matched, Map<Integer, Token> captures, List<TokenWindow> windows) {
            this.matched = matched;
            this.captures = captures;
            this.matchedWindows = windows;
        }

        public boolean isMatched() { return matched; }
        public Map<Integer, Token> getCaptures() { return captures; }
        public Token getCapturedToken(int label) { return captures.get(label); }
        public List<TokenWindow> getMatchedWindows() { return matchedWindows; }
    }

    /**
     * A step in the matching process.
     */
    private static class MatchStep {
        final int minDist;
        final int maxDist;
        final int minRep;
        final int maxRep;
        final List<TokenPredicate> predicates;
        final Integer label; // null if unlabeled

        MatchStep(int minDist, int maxDist, int minRep, int maxRep,
                  List<TokenPredicate> predicates, Integer label) {
            this.minDist = minDist;
            this.maxDist = maxDist;
            this.minRep = minRep;
            this.maxRep = maxRep;
            this.predicates = predicates;
            this.label = label;
        }
    }

    /**
     * Verify a CQL pattern against a token window.
     *
     * @param pattern The parsed CQL pattern
     * @param window  The token window to search
     * @param headwordPosition The position of the headword (for relative matching)
     * @return Verification result with captures if matched
     */
    public VerificationResult verify(CQLPattern pattern, TokenWindow window, int headwordPosition) {
        List<CQLPattern.PatternElement> elements = pattern.getElements();
        if (elements.isEmpty()) {
            return new VerificationResult(false, Collections.emptyMap());
        }

        // Build match steps from pattern elements
        List<MatchStep> steps = buildSteps(elements);

        // Find all possible match sequences using backtracking
        List<Map<Integer, Token>> allCaptures = matchSequence(window, headwordPosition, steps, 0, new HashMap<>(), 0);

        if (allCaptures.isEmpty()) {
            return new VerificationResult(false, Collections.emptyMap());
        }

        // Return first successful match (could return all for alternatives)
        return new VerificationResult(true, allCaptures.get(0));
    }

    /**
     * Verify a CQL pattern against a token window, looking for collocates.
     * The headword is at position 0 (conceptually).
     */
    public VerificationResult verifyForCollocate(CQLPattern pattern, TokenWindow window, int headwordPosition) {
        List<CQLPattern.PatternElement> elements = pattern.getElements();
        if (elements.isEmpty()) {
            return new VerificationResult(false, Collections.emptyMap());
        }

        // Build match steps
        List<MatchStep> steps = buildSteps(elements);

        // Find matches starting from headword position
        List<Map<Integer, Token>> allCaptures = matchSequence(window, headwordPosition, steps, 0, new HashMap<>(), 0);

        if (allCaptures.isEmpty()) {
            return new VerificationResult(false, Collections.emptyMap());
        }

        return new VerificationResult(true, allCaptures.get(0));
    }

    private List<MatchStep> buildSteps(List<CQLPattern.PatternElement> elements) {
        List<MatchStep> steps = new ArrayList<>();

        for (CQLPattern.PatternElement elem : elements) {
            List<TokenPredicate> predicates = new ArrayList<>();

            // Add constraint predicates
            if (elem.getConstraint() != null) {
                predicates.add(constraintToPredicate(elem.getConstraint()));
            }

            // Add target as predicate if present (e.g., "VB.*" or "jj.*")
            String target = elem.getTarget();
            if (target != null && !target.isEmpty()) {
                // Determine field based on pattern
                if (target.contains(".*") || target.contains("?") || target.matches("^[a-zA-Z]+$")) {
                    // Looks like a tag pattern
                    predicates.add(new TagPredicate(target.toLowerCase().replace(".*", "*")));
                } else {
                    // Could be lemma pattern
                    predicates.add(new LemmaPredicate(target));
                }
            }

            Integer label = elem.getPosition() > 0 ? elem.getPosition() : null;

            int minDist = elem.getMinDistance();
            int maxDist = elem.getMaxDistance();
            int minRep = elem.getMinRepetition();
            int maxRep = elem.getMaxRepetition();

            steps.add(new MatchStep(minDist, maxDist, minRep, maxRep, predicates, label));
        }

        return steps;
    }

    private TokenPredicate constraintToPredicate(CQLPattern.Constraint constraint) {
        if (constraint.isOr() && constraint.getOrConstraints() != null) {
            List<TokenPredicate> orPreds = new ArrayList<>();
            for (CQLPattern.Constraint orConst : constraint.getOrConstraints()) {
                orPreds.add(constraintToPredicate(orConst));
            }
            return new OrPredicate(orPreds);
        }

        if (constraint.isAnd() && constraint.getAndConstraints() != null) {
            List<TokenPredicate> andPreds = new ArrayList<>();
            for (CQLPattern.Constraint andConst : constraint.getAndConstraints()) {
                andPreds.add(constraintToPredicate(andConst));
            }
            return new AndPredicate(andPreds);
        }

        String field = constraint.getField();
        String pattern = constraint.getPattern();
        boolean negated = constraint.isNegated();

        TokenPredicate pred;
        switch (field.toLowerCase()) {
            case "tag":
                pred = new TagPredicate(pattern.toLowerCase().replace(".*", "*"));
                break;
            case "lemma":
                pred = new LemmaPredicate(pattern);
                break;
            case "word":
                pred = new WordPredicate(pattern);
                break;
            case "pos_group":
                pred = new PosGroupPredicate(pattern);
                break;
            default:
                pred = new TagPredicate(pattern.toLowerCase().replace(".*", "*"));
        }

        return negated ? new NotPredicate(pred) : pred;
    }

    /**
     * Recursively match a sequence of steps.
     */
    private List<Map<Integer, Token>> matchSequence(TokenWindow window, int headwordPosition,
                                                     List<MatchStep> steps, int stepIndex,
                                                     Map<Integer, Token> captures, int lastPosition) {
        if (stepIndex >= steps.size()) {
            // All steps matched
            return List.of(new HashMap<>(captures));
        }

        MatchStep step = steps.get(stepIndex);
        List<Map<Integer, Token>> results = new ArrayList<>();

        // Determine search range - use default if not specified
        int effectiveMinDist = step.minDist;
        int effectiveMaxDist = step.maxDist;

        // If no distance specified (default 0 or Integer.MAX_VALUE), use default range for collocates
        if (effectiveMaxDist == 0 || effectiveMaxDist == Integer.MAX_VALUE) {
            effectiveMinDist = -10;
            effectiveMaxDist = 10;
        }

        // Try repetitions
        for (int rep = step.minRep; rep <= step.maxRep; rep++) {
            // Find matching tokens in range
            List<Token> candidates = window.findInRange(headwordPosition, effectiveMinDist, effectiveMaxDist);

            for (Token candidate : candidates) {
                // Check if all predicates match
                if (!matchesAllPredicates(candidate, step.predicates)) {
                    continue;
                }

                // Check position ordering for inOrder matches
                if (candidate.getPosition() < lastPosition) {
                    continue;
                }

                // Save capture if labeled
                Map<Integer, Token> newCaptures = new HashMap<>(captures);
                if (step.label != null) {
                    newCaptures.put(step.label, candidate);
                }

                // Recurse for remaining steps
                List<Map<Integer, Token>> remaining = matchSequence(
                    window, headwordPosition, steps, stepIndex + 1,
                    newCaptures, candidate.getPosition()
                );

                results.addAll(remaining);
            }
        }

        return results;
    }

    private boolean matchesAllPredicates(Token token, List<TokenPredicate> predicates) {
        for (TokenPredicate pred : predicates) {
            if (!pred.test(token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check agreement rules after successful match.
     */
    public boolean checkAgreementRules(List<CQLPattern.AgreementRule> rules,
                                        Map<Integer, Token> captures) {
        for (CQLPattern.AgreementRule rule : rules) {
            Token first = captures.get(rule.getFirstPosition());
            Token second = captures.get(rule.getSecondPosition());

            if (first == null || second == null) {
                return false; // Missing captures for agreement check
            }

            String firstValue = getAgreementField(first, rule.getFirstField());
            String secondValue = getAgreementField(second, rule.getSecondField());

            boolean equal = firstValue.equalsIgnoreCase(secondValue);
            boolean shouldEqual = rule.getOperator().equals("=");

            if (shouldEqual && !equal) {
                return false;
            }
            if (!shouldEqual && equal) {
                return false;
            }
        }
        return true;
    }

    private String getAgreementField(Token token, String field) {
        switch (field.toLowerCase()) {
            case "tag": return token.getTag();
            case "lemma": return token.getLemma();
            case "word": return token.getWord();
            case "pos_group": return token.getPosGroup();
            default: return token.getTag();
        }
    }
}
