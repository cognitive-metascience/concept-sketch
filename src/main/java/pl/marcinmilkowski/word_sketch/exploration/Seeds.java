package pl.marcinmilkowski.word_sketch.exploration;

import java.util.Collection;

/**
 * Shared validation helpers for seed-word collections.
 */
public final class Seeds {

    private Seeds() {}

    /**
     * Throws {@link IllegalArgumentException} if {@code seeds} has fewer than 2 elements.
     *
     * @param seeds   the seed collection to validate
     * @param context short description of the caller (used in the error message), e.g. "Multi-seed exploration"
     * @throws IllegalArgumentException if {@code seeds.size() < 2}
     */
    public static void requireAtLeastTwo(Collection<String> seeds, String context) {
        if (seeds.size() < 2) {
            throw new IllegalArgumentException(
                context + " requires at least 2 seeds; received " + seeds.size());
        }
    }
}
