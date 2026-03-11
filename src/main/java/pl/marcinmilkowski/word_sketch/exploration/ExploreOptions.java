package pl.marcinmilkowski.word_sketch.exploration;

/**
 * Shared exploration options for multi-seed exploration
 * ({@code SemanticFieldExplorer#exploreMultiSeed}) and collocate profile comparison
 * ({@code SemanticFieldExplorer#compareCollocateProfiles}).
 *
 * <p>Single-seed exploration uses {@link SingleSeedExploreOptions}, which adds the
 * {@code nounsPerCollocate} parameter specific to the reverse-lookup phase.</p>
 *
 * <p>Note: the {@code Explore} prefix (vs {@code Exploration} used by {@link
 * pl.marcinmilkowski.word_sketch.model.ExplorationResult}) is historical; the asymmetry is
 * intentional rather than a naming error.</p>
 */
public record ExploreOptions(
        /** Maximum collocates to retrieve per seed in the first lookup pass. */
        int topCollocates,
        /** Minimum logDice score threshold; collocates below this value are discarded. */
        double minLogDice,
        /** Minimum number of shared collocates required for a noun to be included in results. */
        int minShared) {
}
