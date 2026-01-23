package pl.marcinmilkowski.word_sketch.query;

import java.util.regex.Pattern;

/**
 * Predicate that matches tokens based on their word form.
 */
public class WordPredicate implements TokenPredicate {
    private final String pattern;

    public WordPredicate(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean test(Token token) {
        if (pattern == null || pattern.isEmpty() || pattern.equals("*")) {
            return true;
        }
        return matchesPattern(token.getWord(), pattern);
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
        return "Word(" + pattern + ")";
    }
}
