package pl.marcinmilkowski.word_sketch.exploration;

/**
 * Options for single-seed semantic field exploration
 * ({@code SemanticFieldExplorer#exploreByPattern}).
 *
 * <p>Extends the shared {@link ExploreOptions} parameters with
 * {@code nounsPerCollocate}, which is only meaningful for the single-seed
 * reverse-lookup phase. Multi-seed exploration and profile comparison use
 * {@link ExploreOptions} directly.</p>
 */
public record SingleSeedExploreOptions(
        /** Maximum collocates to retrieve per seed in the first lookup pass. */
        int topCollocates,
        /**
         * Maximum nouns to expand per collocate in the reverse lookup phase.
         * Specific to single-seed exploration — not used in multi-seed or comparison.
         */
        int nounsPerCollocate,
        /** Minimum logDice score threshold; collocates below this value are discarded. */
        double minLogDice,
        /** Minimum number of shared collocates required for a noun to be included in results. */
        int minShared) {

    /** Project to a shared {@link ExploreOptions}, dropping the single-seed-only field. */
    public ExploreOptions toBaseOptions() {
        return new ExploreOptions(topCollocates, minLogDice, minShared);
    }
}
