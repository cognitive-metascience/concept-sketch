package pl.marcinmilkowski.word_sketch.query;

import pl.marcinmilkowski.word_sketch.model.QueryResults;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Shared stub implementation of {@link QueryExecutor} for use in tests.
 *
 * <p>All methods return empty collections or zero by default. Individual tests can
 * override specific methods using anonymous subclasses to provide custom behaviour.</p>
 *
 * <p>Example usage in a test:
 * <pre>{@code
 * QueryExecutor stub = new StubQueryExecutor() {
 *     @Override
 *     public List<QueryResults.CollocateResult> executeBcqlQuery(String p, int m) {
 *         return List.of(new QueryResults.CollocateResult("a sentence", null, 0, 5, "d1", "word", 1, 7.0));
 *     }
 * };
 * }</pre>
 * </p>
 */
public class StubQueryExecutor implements QueryExecutor {

    @Override
    public List<QueryResults.WordSketchResult> executeCollocations(
            String lemma, String cqlPattern, double minLogDice, int maxResults) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<QueryResults.ConcordanceResult> executeCqlQuery(
            String cqlPattern, int maxResults) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<QueryResults.CollocateResult> executeBcqlQuery(
            String bcqlPattern, int maxResults) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public long getTotalFrequency(String lemma) throws IOException {
        return 0L;
    }

    @Override
    public List<QueryResults.WordSketchResult> executeSurfacePattern(
            String bcqlPattern, double minLogDice, int maxResults) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<QueryResults.WordSketchResult> executeDependencyPattern(
            String lemma, String deprel, double minLogDice, int maxResults,
            String headPosConstraint) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void close() {}
}
