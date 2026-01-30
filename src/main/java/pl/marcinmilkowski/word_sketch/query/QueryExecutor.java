package pl.marcinmilkowski.word_sketch.query;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Interface for word sketch query executors.
 * Allows different implementations (legacy, hybrid) to be used interchangeably.
 * This is the core abstraction that enables the hybrid index migration.
 */
public interface QueryExecutor extends Closeable {

    /**
     * Find collocations for a headword using a CQL pattern.
     *
     * @param headword    The headword/lemma to find collocations for
     * @param cqlPattern  CQL pattern defining the collocate constraints
     * @param minLogDice  Minimum logDice score (0 for no minimum)
     * @param maxResults  Maximum number of results to return
     * @return List of collocation results, sorted by logDice descending
     * @throws IOException if index access fails
     */
    List<WordSketchQueryExecutor.WordSketchResult> findCollocations(String headword, String cqlPattern,
                                             double minLogDice, int maxResults) throws IOException;

    /**
     * Get the total frequency of a lemma in the corpus.
     *
     * @param lemma The lemma to look up
     * @return Total frequency count
     * @throws IOException if index access fails
     */
    long getTotalFrequency(String lemma) throws IOException;

    /**
     * Get the type of this executor for logging/debugging.
     *
     * @return Executor type name (e.g., "legacy", "hybrid")
     */
    default String getExecutorType() {
        return this.getClass().getSimpleName();
    }

    /**
     * Check if the executor is ready to process queries.
     *
     * @return true if ready, false otherwise
     */
    default boolean isReady() {
        return true;
    }
}
