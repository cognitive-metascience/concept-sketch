package pl.marcinmilkowski.word_sketch.query;

import java.util.regex.Pattern;

/**
 * Predicate that matches tokens based on their POS tag.
 */
public class TagPredicate implements TokenPredicate {
    private final String pattern;

    public TagPredicate(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean test(Token token) {
        if (pattern == null || pattern.isEmpty() || pattern.equals("*")) {
            return true;
        }
        return matchesPattern(token.getTag(), pattern);
    }

    private boolean matchesPattern(String value, String pattern) {
        // Convert wildcard pattern to regex
        String regex = wildcardToRegex(pattern);
        return Pattern.matches(regex, value.toLowerCase());
    }

    private String wildcardToRegex(String pattern) {
        // Convert wildcard pattern to regex
        // * matches any string, ? matches any single char
        // Need to escape regex special chars first
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if (c == '?') {
                sb.append(".");
            } else if (".^$|()[]{}\\".indexOf(c) >= 0) {
                sb.append('\\').append(c);  // Escape regex special chars
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Tag(" + pattern + ")";
    }
}
