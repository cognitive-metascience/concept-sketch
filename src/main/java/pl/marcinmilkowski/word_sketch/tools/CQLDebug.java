package pl.marcinmilkowski.word_sketch.tools;

import pl.marcinmilkowski.word_sketch.grammar.CQLParser;
import pl.marcinmilkowski.word_sketch.grammar.CQLPattern;

import java.util.List;

public class CQLDebug {
    public static void main(String[] args) {
        String[] patterns = {
            "[tag=jj.*]~{0,3}",
            "[tag=jj.*]",
            "[tag=\"jj.*\"]",
            "[tag=nn.*]~{1,2} [tag=nn.*]",
            "[tag=dt]~{0,1}"
        };
        
        for (String patternStr : patterns) {
            System.out.println("\n=== Pattern: " + patternStr + " ===");
            try {
                CQLPattern pattern = new CQLParser().parse(patternStr);
                System.out.println("Elements: " + pattern.getElements().size());
                
                for (int i = 0; i < pattern.getElements().size(); i++) {
                    CQLPattern.PatternElement elem = pattern.getElements().get(i);
                    System.out.println("  Element " + i + ":");
                    System.out.println("    Constraint: " + (elem.getConstraint() != null));
                    if (elem.getConstraint() != null) {
                        CQLPattern.Constraint c = elem.getConstraint();
                        System.out.println("    Field: " + c.getField());
                        System.out.println("    Pattern: " + c.getPattern());
                        System.out.println("    Negated: " + c.isNegated());
                        System.out.println("    Or: " + c.isOr());
                        System.out.println("    And: " + c.isAnd());
                    }
                }
                
                // Test matching
                System.out.println("  Matches:");
                testMatch(pattern, "test", "JJ");
                testMatch(pattern, "test", "JJS");
                testMatch(pattern, "test", "NN");
                testMatch(pattern, "test", "NNS");
                testMatch(pattern, "test", "DT");
            } catch (Exception e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
        }
    }
    
    private static void testMatch(CQLPattern pattern, String lemma, String tag) {
        // Get first element's constraint
        if (pattern.getElements().isEmpty()) return;
        CQLPattern.Constraint c = pattern.getElements().get(0).getConstraint();
        if (c == null) return;
        
        boolean matches = matchesField(tag.toLowerCase(), c.getField(), c.getPattern());
        System.out.println("    " + tag + " -> " + matches);
    }
    
    private static boolean matchesField(String value, String field, String pattern) {
        if (value == null) value = "";
        pattern = pattern.replace("\"", "").toLowerCase().trim();
        value = value.toLowerCase();

        System.out.println("      (value='" + value + "' pattern='" + pattern + "')");
        
        // Standard regex matching
        try {
            return value.matches(pattern);
        } catch (java.util.regex.PatternSyntaxException e) {
            return value.equals(pattern);
        }
    }
}
