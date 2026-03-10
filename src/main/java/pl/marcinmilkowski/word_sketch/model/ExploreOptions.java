package pl.marcinmilkowski.word_sketch.model;

/**
 * Options for {@code SemanticFieldExplorer#exploreByPattern}, bundling the tuning parameters
 * that were previously spread across multiple method arguments.
 */
public record ExploreOptions(
        int topCollocates,
        int nounsPerCollocate,
        double minLogDice,
        int minShared,
        boolean includeExamples) {}
