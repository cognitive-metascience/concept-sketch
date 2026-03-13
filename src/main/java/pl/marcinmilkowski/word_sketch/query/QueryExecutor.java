package pl.marcinmilkowski.word_sketch.query;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import pl.marcinmilkowski.word_sketch.model.sketch.*;
import pl.marcinmilkowski.word_sketch.query.spi.CollocateQueryPort;
import pl.marcinmilkowski.word_sketch.query.spi.SketchQueryPort;

/**
 * Unified query interface for corpus lookups.
 *
 * <p>This interface extends {@link CollocateQueryPort} (BCQL concordance queries) and adds
 * surface-pattern, collocate-scoring, and dependency-query methods used by the word-sketch layer.
 * Handlers that only need BCQL retrieval should declare {@link CollocateQueryPort} instead;
 * the single production implementation ({@link BlackLabQueryExecutor}) implements both.</p>
 *
 *
 */
public interface QueryExecutor extends CollocateQueryPort, SketchQueryPort, Closeable {

    /**
     * Find collocations for a lemma using a CQL pattern.
     * Groups hits by collocate identity and ranks results by logDice.
     *
     * <p>The {@code cqlPattern} must start with {@code [} and is treated as a collocate
     * constraint appended after the head lemma token, e.g. {@code "[xpos=\"JJ.*\"]"}.
     * Any other format throws {@link IllegalArgumentException}.
     *
     * @param lemma       The headword filter: its corpus frequency is looked up separately to
     *                    compute logDice scores and is <em>not</em> embedded in {@code cqlPattern}.
     *                    Must not be null or empty.
     * @param cqlPattern  CQL pattern defining the collocate constraints (see above)
     * @param minLogDice  Minimum logDice score threshold (0 for no minimum)
     * @param maxResults  Maximum number of results to return
     * @return List of collocation results, sorted by logDice descending
     * @throws IOException if index access fails
     * @throws IllegalArgumentException if {@code lemma} is null or empty, or {@code cqlPattern} is not in a recognized format
     */
    @NonNull List<WordSketchResult> executeCollocations(@NonNull String lemma, @NonNull String cqlPattern,
                                             double minLogDice, int maxResults) throws IOException;

    /**
     * Execute a general CQL query and return raw concordance results.
     * Does not compute logDice or extract collocates — use {@link CollocateQueryPort#executeBcqlQuery} for that.
     *
     * @implNote Uses the CQL parser ({@code ContextualQueryLanguageParser}); no headword scoring.
     *           Returns token windows matching the pattern without ranked collocation data.
     *
     * @param cqlPattern  CQL pattern string
     * @param maxResults  Maximum number of concordance results
     * @return Concordance results in hit order
     * @throws IOException if index access fails
     */
    @NonNull List<ConcordanceResult> executeCqlQuery(@NonNull String cqlPattern, int maxResults) throws IOException;

    /**
     * Get the total frequency of a lemma in the corpus.
     *
     * @param lemma  The lemma to look up
     * @return Total occurrence count, or 0 if not found
     * @throws IOException if index access fails
     */
    long getTotalFrequency(@NonNull String lemma) throws IOException;

    /**
     * Execute a dependency-pattern query without a head POS constraint.
     *
     * @param lemma              The head lemma to search for
     * @param deprel             The dependency relation label (e.g., "nsubj", "obj")
     * @param minLogDice         Minimum logDice score threshold (0 for no minimum)
     * @param maxResults         Maximum number of results to return
     * @param headPosConstraint  Optional POS regex for the head token (e.g., "VB.*"); pass null
     *                           to skip the constraint and match any POS
     * @return Collocate results ranked by logDice descending
     * @throws IOException if index access fails
     */
    @NonNull List<WordSketchResult> executeDependencyPattern(
            @NonNull String lemma, @NonNull String deprel,
            double minLogDice, int maxResults,
            @Nullable String headPosConstraint) throws IOException;

    /**
     * Get the type of this executor for logging/debugging.
     *
     * @return Executor type name (e.g., "blacklab")
     */
    default String getExecutorType() {
        return this.getClass().getSimpleName();
    }

}
