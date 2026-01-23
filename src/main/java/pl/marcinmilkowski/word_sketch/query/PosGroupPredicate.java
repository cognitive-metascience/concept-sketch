package pl.marcinmilkowski.word_sketch.query;

import java.util.regex.Pattern;

/**
 * Predicate that matches tokens based on their POS group.
 */
public class PosGroupPredicate implements TokenPredicate {
    private final String pattern;

    public PosGroupPredicate(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean test(Token token) {
        if (pattern == null || pattern.isEmpty() || pattern.equals("*")) {
            return true;
        }
        return matchesPattern(token.getPosGroup(), pattern);
    }

    private boolean matchesPattern(String value, String pattern) {
        // Convert wildcard pattern to regex
        String regex = wildcardToRegex(pattern.toLowerCase());
        return Pattern.matches(regex, value.toLowerCase());
    }

    private String wildcardToRegex(String pattern) {
        return pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
    }

    @Override
    public String toString() {
        return "PosGroup(" + pattern + ")";
    }
}
