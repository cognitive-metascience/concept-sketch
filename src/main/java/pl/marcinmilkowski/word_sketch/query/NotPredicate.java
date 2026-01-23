package pl.marcinmilkowski.word_sketch.query;

/**
 * Predicate that negates its child predicate.
 */
public class NotPredicate implements TokenPredicate {
    private final TokenPredicate predicate;

    public NotPredicate(TokenPredicate predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean test(Token token) {
        return !predicate.test(token);
    }

    @Override
    public String toString() {
        return "Not(" + predicate + ")";
    }
}
