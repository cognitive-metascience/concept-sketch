package pl.marcinmilkowski.word_sketch.query;

import java.util.List;

/**
 * Predicate that matches when ALL of its child predicates match.
 */
public class AndPredicate implements TokenPredicate {
    private final List<TokenPredicate> predicates;

    public AndPredicate(List<TokenPredicate> predicates) {
        this.predicates = predicates;
    }

    @Override
    public boolean test(Token token) {
        for (TokenPredicate pred : predicates) {
            if (!pred.test(token)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "And(" + predicates + ")";
    }
}
