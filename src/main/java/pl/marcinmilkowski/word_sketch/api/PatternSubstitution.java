package pl.marcinmilkowski.word_sketch.api;

/**
 * @deprecated Use {@link pl.marcinmilkowski.word_sketch.utils.PatternSubstitution} instead.
 */
@Deprecated
public class PatternSubstitution {
    private PatternSubstitution() {}

    public static String substituteCollocate(String pattern, String collocate, int collocatePosition) {
        return pl.marcinmilkowski.word_sketch.utils.PatternSubstitution.substituteCollocate(pattern, collocate, collocatePosition);
    }

    public static String extractXposFromConstraint(String constraint) {
        return pl.marcinmilkowski.word_sketch.utils.PatternSubstitution.extractXposFromConstraint(constraint);
    }

    public static String escapeForRegex(String s) {
        return pl.marcinmilkowski.word_sketch.utils.CqlUtils.escapeForRegex(s);
    }
}
