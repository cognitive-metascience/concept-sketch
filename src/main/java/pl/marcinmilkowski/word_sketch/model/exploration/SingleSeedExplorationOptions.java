package pl.marcinmilkowski.word_sketch.model.exploration;

/**
 * Exploration options for single-seed exploration, extending the common options
 * ({@link ExplorationOptions}) with {@code reverseExpansionLimit}, which controls the
 * number of noun candidates expanded per collocate in the reverse-lookup pass and is
 * specific to the single-seed code path.
 *
 * <p>Multi-seed and comparison methods accept plain {@link ExplorationOptions}; using a
 * distinct type here makes the single-seed call site self-documenting and prevents the
 * {@code reverseExpansionLimit} value from silently being ignored when passed to those
 * methods.</p>
 *
 * @param base                 common exploration tuning parameters shared across all modes
 * @param reverseExpansionLimit maximum noun candidates to expand per collocate in the
 *                              reverse-lookup pass; tunes breadth of discovered-noun set
 */
public record SingleSeedExplorationOptions(
        ExplorationOptions base,
        int reverseExpansionLimit) {

    /** Delegates to {@link ExplorationOptions#topCollocates()}. */
    public int topCollocates()  { return base.topCollocates(); }
    /** Delegates to {@link ExplorationOptions#minLogDice()}. */
    public double minLogDice()  { return base.minLogDice(); }
    /** Delegates to {@link ExplorationOptions#minShared()}. */
    public int minShared()      { return base.minShared(); }
}
