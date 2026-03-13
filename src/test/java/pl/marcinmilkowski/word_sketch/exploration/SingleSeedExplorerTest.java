package pl.marcinmilkowski.word_sketch.exploration;

import org.junit.jupiter.api.Test;
import pl.marcinmilkowski.word_sketch.model.sketch.*;
import pl.marcinmilkowski.word_sketch.model.exploration.ExplorationOptions;
import pl.marcinmilkowski.word_sketch.model.exploration.ExplorationResult;
import pl.marcinmilkowski.word_sketch.model.exploration.SingleSeedExplorationOptions;
import pl.marcinmilkowski.word_sketch.query.QueryExecutor;
import pl.marcinmilkowski.word_sketch.query.StubQueryExecutor;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SingleSeedExplorer}.
 *
 * <p>Uses a stub {@link QueryExecutor} so no real index is needed.</p>
 */
class SingleSeedExplorerTest {

    private static final String NOUN_CQL = "[xpos=\"NN.*\"]";
    private static final String BCQL     = "[lemma=\"theory\"] [xpos=\"JJ.*\"]";
    private static final String SIMPLE   = "[xpos=\"JJ.*\"]";

    private static WordSketchResult wsr(String lemma, double logDice) {
        return new WordSketchResult(lemma, "JJ", 10, logDice, 0.0, List.of());
    }

    private static SingleSeedExplorationOptions opts(int top, double minLogDice, int minShared) {
        return new SingleSeedExplorationOptions(
                new ExplorationOptions(top, minLogDice, minShared), top);
    }

    // ── Normal path ──────────────────────────────────────────────────────────

    /**
     * Normal path: seed has collocates; reverse lookup returns nouns; nouns meeting
     * minShared threshold appear in discoveredNouns, sorted by combined relevance score.
     */
    @Test
    void explore_normalPath_discoversNounsAndCoreCollocates() throws IOException {
        // phase-1: theory collocates with "important" (9.0) and "novel" (7.0)
        // phase-2: reverse lookup — "important" → [model, hypothesis], "novel" → [model]
        QueryExecutor executor = new StubQueryExecutor() {
            @Override
            public List<WordSketchResult> executeSurfaceCollocations(
                    String pattern, double minLogDice, int max) {
                return List.of(wsr("important", 9.0), wsr("novel", 7.0));
            }

            @Override
            public List<WordSketchResult> executeCollocations(
                    String lemma, String cqlPattern, double minLogDice, int max) {
                return switch (lemma) {
                    case "important" -> List.of(wsr("model", 8.5), wsr("hypothesis", 6.0));
                    case "novel"     -> List.of(wsr("model", 5.0));
                    default          -> List.of();
                };
            }
        };

        SingleSeedExplorer explorer = new SingleSeedExplorer(executor, NOUN_CQL);
        ExplorationResult result = explorer.explore("theory", "adj_predicate", BCQL, SIMPLE, opts(10, 0.0, 1));

        assertFalse(result.isEmpty(), "Result should not be empty");
        assertEquals("theory", result.seed());

        // Seed collocates must be populated
        assertTrue(result.seedCollocates().containsKey("important"), "seedCollocates should contain 'important'");
        assertTrue(result.seedCollocates().containsKey("novel"),     "seedCollocates should contain 'novel'");
        assertEquals(9.0, result.seedCollocates().get("important"), 0.001);

        // Discovered nouns: "model" shares 2 collocates, "hypothesis" shares 1
        List<String> nouns = result.discoveredNouns().stream()
                .map(pl.marcinmilkowski.word_sketch.model.exploration.DiscoveredNoun::noun).toList();
        assertTrue(nouns.contains("model"),      "model should be discovered");
        assertTrue(nouns.contains("hypothesis"), "hypothesis should be discovered");

        // "model" has higher combined score → should appear before "hypothesis"
        int modelIdx      = nouns.indexOf("model");
        int hypothesisIdx = nouns.indexOf("hypothesis");
        assertTrue(modelIdx < hypothesisIdx, "model (2 shared) should rank before hypothesis (1 shared)");
    }

    // ── Empty collocates edge case ────────────────────────────────────────────

    /**
     * Edge case: QueryExecutor returns empty for both surface-pattern and collocation calls.
     * Explorer must return an empty ExplorationResult without throwing.
     */
    @Test
    void explore_emptyCollocates_returnsEmptyResult() throws IOException {
        SingleSeedExplorer explorer = new SingleSeedExplorer(new StubQueryExecutor(), NOUN_CQL);
        ExplorationResult result = explorer.explore("rare", "adj_predicate", BCQL, SIMPLE, opts(10, 0.0, 1));

        assertTrue(result.isEmpty(), "Result should be empty when executor returns no collocates");
        assertEquals("rare", result.seed());
        assertTrue(result.seedCollocates().isEmpty());
        assertTrue(result.discoveredNouns().isEmpty());
        assertTrue(result.coreCollocates().isEmpty());
    }

    // ── Min-score filtering ───────────────────────────────────────────────────

    /**
     * Min-score filtering: collocates below minLogDice must not appear in the result.
     * The executor returns only results above the threshold when minLogDice is applied.
     */
    @Test
    void explore_minLogDiceFiltering_excludesBelowThreshold() throws IOException {
        QueryExecutor executor = new StubQueryExecutor() {
            @Override
            public List<WordSketchResult> executeSurfaceCollocations(
                    String pattern, double minLogDice, int max) {
                // Simulate executor honouring minLogDice: only return items >= threshold
                return List.of(wsr("important", 8.0), wsr("obscure", 2.0)).stream()
                        .filter(r -> r.logDice() >= minLogDice)
                        .toList();
            }

            @Override
            public List<WordSketchResult> executeCollocations(
                    String lemma, String cqlPattern, double minLogDice, int max) {
                if ("important".equals(lemma)) {
                    return List.of(wsr("model", 7.0));
                }
                return List.of();
            }
        };

        SingleSeedExplorer explorer = new SingleSeedExplorer(executor, NOUN_CQL);
        // minLogDice = 5.0: "obscure" (2.0) should be filtered out by the executor
        ExplorationResult result = explorer.explore("theory", "adj_predicate", BCQL, SIMPLE, opts(10, 5.0, 1));

        assertFalse(result.isEmpty());
        assertTrue(result.seedCollocates().containsKey("important"),  "important (8.0) should pass filter");
        assertFalse(result.seedCollocates().containsKey("obscure"),   "obscure (2.0) should be excluded");
    }
}
