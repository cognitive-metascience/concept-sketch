package pl.marcinmilkowski.word_sketch.query;

/**
 * Functional interface for token matching predicates.
 * Used by CQLVerifier to check if a token matches a constraint.
 */
@FunctionalInterface
public interface TokenPredicate {
    boolean test(Token token);

    /**
     * Negate this predicate.
     */
    default TokenPredicate negate() {
        return token -> !this.test(token);
    }
}
