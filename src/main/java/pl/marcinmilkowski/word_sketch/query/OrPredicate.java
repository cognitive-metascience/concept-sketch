package pl.marcinmilkowski.word_sketch.query;

import java.util.List;

/**
 * Predicate that matches when ANY of its child predicates match.
 */
public class OrPredicate implements TokenPredicate {
    private final List<TokenPredicate> predicates;

    public OrPredicate(List<TokenPredicate> predicates) {
        this.predicates = predicates;
    }

    @Override
    public boolean test(Token token) {
        for (TokenPredicate pred : predicates) {
            if (pred.test(token)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Or(" + predicates + ")";
    }
}
