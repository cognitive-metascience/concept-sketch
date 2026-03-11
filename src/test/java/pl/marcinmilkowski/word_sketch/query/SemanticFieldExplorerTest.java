package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.marcinmilkowski.word_sketch.exploration.ExploreOptions;
import pl.marcinmilkowski.word_sketch.exploration.SemanticFieldExplorer;
import pl.marcinmilkowski.word_sketch.model.AdjectiveProfile;
import pl.marcinmilkowski.word_sketch.model.ComparisonResult;
import pl.marcinmilkowski.word_sketch.model.CoreCollocate;
import pl.marcinmilkowski.word_sketch.model.DiscoveredNoun;
import pl.marcinmilkowski.word_sketch.model.ExplorationResult;
import pl.marcinmilkowski.word_sketch.model.QueryResults;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pl.marcinmilkowski.word_sketch.config.GrammarConfig;
import pl.marcinmilkowski.word_sketch.config.GrammarConfigLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SemanticFieldExplorer using a stub QueryExecutor.
 * No real index is required.
 */
@DisplayName("SemanticFieldExplorer")
class SemanticFieldExplorerTest {

    // ── Stub QueryExecutor ────────────────────────────────────────────────────

    /**
     * Minimal stub that returns pre-defined adjective lists per noun.
     * The compare() method calls executor.findCollocations(noun, ADJECTIVE_PATTERN, ...).
     */
    private static class StubExecutor implements QueryExecutor {

        private final Map<String, List<QueryResults.WordSketchResult>> collocations;

        StubExecutor(Map<String, List<QueryResults.WordSketchResult>> collocations) {
            this.collocations = collocations;
        }

        @Override
        public List<QueryResults.WordSketchResult> findCollocations(
                String lemma, String cqlPattern, double minLogDice, int maxResults) {
            return collocations.getOrDefault(lemma.toLowerCase(), Collections.emptyList());
        }

        @Override
        public List<QueryResults.ConcordanceResult> executeCqlQuery(String cqlPattern, int maxResults) {
            return Collections.emptyList();
        }

        @Override
        public long getTotalFrequency(String lemma) {
            return 0;
        }

        @Override
        public List<QueryResults.CollocateResult> executeBcqlQuery(String bcqlPattern, int maxResults) {
            return Collections.emptyList();
        }

        @Override
        public List<QueryResults.WordSketchResult> executeSurfacePattern(
                String lemma, String bcqlPattern,
                double minLogDice, int maxResults) {
            return collocations.getOrDefault(lemma.toLowerCase(), Collections.emptyList());
        }

        @Override
        public void close() {}

        @Override
        public List<QueryResults.WordSketchResult> executeDependencyPattern(
                String lemma, String deprel, double minLogDice, int maxResults) {
            return Collections.emptyList();
        }

        @Override
        public List<QueryResults.WordSketchResult> executeDependencyPatternWithPos(
                String lemma, String deprel,
                double minLogDice, int maxResults, String headPosConstraint) {
            return Collections.emptyList();
        }
    }

    /** Convenience factory for WordSketchResult. */
    private static QueryResults.WordSketchResult wsr(String lemma, double logDice) {
        return new QueryResults.WordSketchResult(lemma, "JJ", 10, logDice, 0.0, Collections.emptyList());
    }

    // ── compare() – intersection logic ───────────────────────────────────────

    @Test
    @DisplayName("compare: adjectives shared by all seeds are fully-shared")
    void compare_sharedAdjectives() throws IOException {
        StubExecutor executor = new StubExecutor(Map.of(
            "theory",     List.of(wsr("empirical", 8.0), wsr("new", 7.0), wsr("old", 6.0)),
            "model",      List.of(wsr("empirical", 7.5), wsr("new", 6.5), wsr("simple", 5.0)),
            "hypothesis", List.of(wsr("empirical", 7.0), wsr("new", 6.0), wsr("bold", 5.5))
        ));

        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);
        ComparisonResult result =
            explorer.compareCollocateProfiles(Set.of("theory", "model", "hypothesis"), 0.0, 50);

        List<AdjectiveProfile> fullyShared = result.getFullyShared();
        List<String> sharedNames = fullyShared.stream()
            .map(p -> p.adjective()).toList();

        assertTrue(sharedNames.contains("empirical"),
            "empirical should be fully shared; got: " + sharedNames);
        assertTrue(sharedNames.contains("new"),
            "new should be fully shared; got: " + sharedNames);
    }

    @Test
    @DisplayName("compare: adjectives specific to one seed are detected as specific")
    void compare_specificAdjectives() throws IOException {
        StubExecutor executor = new StubExecutor(Map.of(
            "theory",  List.of(wsr("abstract", 8.0)),
            "model",   List.of(wsr("mathematical", 7.0))
        ));

        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);
        ComparisonResult result =
            explorer.compareCollocateProfiles(Set.of("theory", "model"), 0.0, 50);

        List<AdjectiveProfile> specific = result.getSpecific();
        List<String> specificNames = specific.stream().map(p -> p.adjective()).toList();

        assertTrue(specificNames.contains("abstract"),
            "abstract should be specific to theory; got: " + specificNames);
        assertTrue(specificNames.contains("mathematical"),
            "mathematical should be specific to model; got: " + specificNames);
    }

    @Test
    @DisplayName("compare: empty seed set throws IllegalArgumentException")
    void compare_emptySeedSet() {
        StubExecutor executor = new StubExecutor(Map.of());
        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);

        assertThrows(IllegalArgumentException.class,
            () -> explorer.compareCollocateProfiles(Collections.emptySet(), 0.0, 50),
            "Empty seed set should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("compare: single seed throws IllegalArgumentException (requires >= 2 seeds)")
    void compare_singleSeed() {
        StubExecutor executor = new StubExecutor(Map.of(
            "theory", List.of(wsr("empirical", 8.0), wsr("scientific", 7.5))
        ));
        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);

        assertThrows(IllegalArgumentException.class,
            () -> explorer.compareCollocateProfiles(Set.of("theory"), 0.0, 50),
            "Single seed should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("compare: seed with no collocates produces empty profile for that noun")
    void compare_seedWithNoCollocates() throws IOException {
        StubExecutor executor = new StubExecutor(Map.of(
            "theory", List.of(wsr("empirical", 8.0)),
            "model",  Collections.emptyList()   // no collocates
        ));

        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);
        ComparisonResult result =
            explorer.compareCollocateProfiles(Set.of("theory", "model"), 0.0, 50);

        // empirical is specific to theory (model has no adjectives)
        List<String> specificNames = result.getSpecific().stream()
            .map(p -> p.adjective()).toList();
        assertTrue(specificNames.contains("empirical"),
            "empirical should be specific when model has no adjectives; got: " + specificNames);
    }

    @Test
    @DisplayName("compare: null seed set throws IllegalArgumentException")
    void compare_nullSeedSet() {
        StubExecutor executor = new StubExecutor(Map.of());
        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);

        assertThrows(IllegalArgumentException.class,
            () -> explorer.compareCollocateProfiles(null, 0.0, 50),
            "Null seed set should throw IllegalArgumentException");
    }

    // ── ComparisonResult edge cases ───────────────────────────────────────────

    @Test
    @DisplayName("compare: partially shared adjectives are identified correctly")
    void compare_partiallySharedAdjectives() throws IOException {
        // "theoretical" appears in theory + model but not hypothesis
        StubExecutor executor = new StubExecutor(Map.of(
            "theory",     List.of(wsr("theoretical", 8.0), wsr("empirical", 7.0)),
            "model",      List.of(wsr("theoretical", 7.5), wsr("simple", 6.0)),
            "hypothesis", List.of(wsr("empirical", 7.0), wsr("bold", 5.0))
        ));

        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);
        ComparisonResult result =
            explorer.compareCollocateProfiles(Set.of("theory", "model", "hypothesis"), 0.0, 50);

        List<String> partialNames = result.getPartiallyShared().stream()
            .map(p -> p.adjective()).toList();

        assertTrue(partialNames.contains("theoretical"),
            "theoretical (in 2/3 nouns) should be partially shared; got: " + partialNames);
    }

    // ── exploreByPattern ──────────────────────────────────────────────────────

    @Test
    @DisplayName("exploreByPattern: returns non-null result for known seed")
    void exploreByPattern_returnsNonNullResult() throws IOException {
        StubExecutor executor = new StubExecutor(Map.of(
            "theory", List.of(wsr("empirical", 8.0), wsr("scientific", 7.0))
        ));

        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);
        ExploreOptions opts = new ExploreOptions(10, 5, 0.0, 1);

        ExplorationResult result = explorer.exploreByPattern(
            "theory", "test-relation",
            "[lemma=\"theory\"] [xpos=\"JJ.*\"]",
            "[xpos=\"JJ.*\"]",
            opts);

        assertNotNull(result, "Result should not be null");
        assertEquals("theory", result.getSeed(), "Result seed should match input");
    }

    @Test
    @DisplayName("exploreByPattern: seed collocates map contains expected entries")
    void exploreByPattern_seedCollocatesContainExpected() throws IOException {
        StubExecutor executor = new StubExecutor(Map.of(
            "theory", List.of(wsr("empirical", 8.0), wsr("scientific", 7.0))
        ));

        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);
        ExploreOptions opts = new ExploreOptions(10, 5, 0.0, 1);

        ExplorationResult result = explorer.exploreByPattern(
            "theory", "test-relation",
            "[lemma=\"theory\"] [xpos=\"JJ.*\"]",
            "[xpos=\"JJ.*\"]",
            opts);

        assertNotNull(result.getSeedCollocates(), "Seed collocates map should not be null");
        assertTrue(result.getSeedCollocates().containsKey("empirical"),
            "Seed collocates should contain 'empirical'");
    }

    // ── exploreMultiSeed ──────────────────────────────────────────────────────

    @Test
    @DisplayName("exploreMultiSeed: returns non-null result for two seeds")
    void exploreMultiSeed_returnsNonNullResult() throws IOException {
        StubExecutor executor = new StubExecutor(Map.of(
            "theory",  List.of(wsr("empirical", 8.0), wsr("scientific", 7.0)),
            "model",   List.of(wsr("empirical", 7.5), wsr("theoretical", 6.0))
        ));

        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);

        ExplorationResult result = explorer.exploreMultiSeed(
            Set.of("theory", "model"),
            new pl.marcinmilkowski.word_sketch.config.RelationConfig(
                "test", "test", "test", "[xpos=\"NN.*\"] [xpos=\"JJ.*\"]",
                1, 2, false, 0,
                java.util.Optional.of(pl.marcinmilkowski.word_sketch.model.RelationType.SURFACE),
                true, pl.marcinmilkowski.word_sketch.model.PosGroup.ADJ),
            0.0, 10, 1);

        assertNotNull(result, "Result should not be null");
    }

    @Test
    @DisplayName("exploreMultiSeed: shared collocates appear in seed collocates")
    void exploreMultiSeed_sharedCollocateInSeedMap() throws IOException {
        StubExecutor executor = new StubExecutor(Map.of(
            "theory",  List.of(wsr("empirical", 8.0), wsr("scientific", 7.0)),
            "model",   List.of(wsr("empirical", 7.5), wsr("formal", 6.0))
        ));

        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor);

        ExplorationResult result = explorer.exploreMultiSeed(
            Set.of("theory", "model"),
            new pl.marcinmilkowski.word_sketch.config.RelationConfig(
                "test", "test", "test", "[xpos=\"NN.*\"] [xpos=\"JJ.*\"]",
                1, 2, false, 0,
                java.util.Optional.of(pl.marcinmilkowski.word_sketch.model.RelationType.SURFACE),
                true, pl.marcinmilkowski.word_sketch.model.PosGroup.ADJ),
            0.0, 10, 2);

        assertNotNull(result.getSeedCollocates(), "Seed collocates should not be null");
        assertTrue(result.getSeedCollocates().containsKey("empirical"),
            "Shared collocate 'empirical' should appear in seed collocates map");
    }

    // ── deriveNounCqlConstraint (via constructor + recording executor) ─────────

    /**
     * Records every (lemma, cqlPattern) pair passed to findCollocations so we can
     * assert which noun CQL constraint the explorer derived from GrammarConfig.
     */
    private static class RecordingExecutor extends StubExecutor {

        final List<String> capturedCqlPatterns = new ArrayList<>();

        RecordingExecutor(Map<String, List<QueryResults.WordSketchResult>> collocations) {
            super(collocations);
        }

        @Override
        public List<QueryResults.WordSketchResult> findCollocations(
                String lemma, String cqlPattern, double minLogDice, int maxResults) {
            capturedCqlPatterns.add(cqlPattern);
            return super.findCollocations(lemma, cqlPattern, minLogDice, maxResults);
        }
    }

    @Test
    @DisplayName("deriveNounCqlConstraint: falls back to [xpos=\"NN.*\"] when grammarConfig is null")
    void deriveNounCqlConstraint_nullConfig_usesFallback() throws IOException {
        RecordingExecutor executor = new RecordingExecutor(Map.of(
            "theory", List.of(wsr("empirical", 8.0))
        ));
        // null GrammarConfig → fallback noun constraint
        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor, null);
        ExploreOptions opts = new ExploreOptions(10, 1, 0.0, 5);

        explorer.exploreByPattern(
            "theory", "test-relation",
            "[lemma=\"theory\"] [xpos=\"JJ.*\"]",
            "[xpos=\"JJ.*\"]",
            opts);

        // The noun-constraint call is the second findCollocations call (first is for seed collocates)
        assertTrue(executor.capturedCqlPatterns.stream()
            .anyMatch(p -> p.contains("NN")),
            "Fallback noun constraint should contain 'NN'; got: " + executor.capturedCqlPatterns);
    }

    @Test
    @DisplayName("deriveNounCqlConstraint: uses noun pattern from config when available")
    void deriveNounCqlConstraint_withNounRelation_usesConfigPattern() throws IOException {
        // Build a GrammarConfig with a NOUN-collocate relation via the loader
        String json = """
            {
              "version": "1.0-test",
              "relations": [
                {
                  "id": "subj",
                  "name": "Subject",
                  "pattern": "1:[xpos=\\"VB.*\\"] 2:[xpos=\\"NN.*\\"]",
                  "head_position": 1,
                  "collocate_position": 2,
                  "relation_type": "SURFACE"
                }
              ]
            }
            """;
        GrammarConfig config = GrammarConfigLoader.fromReader(new StringReader(json));

        RecordingExecutor executor = new RecordingExecutor(Map.of(
            "theory", List.of(wsr("important", 8.0))
        ));
        SemanticFieldExplorer explorer = new SemanticFieldExplorer(executor, config);
        ExploreOptions opts = new ExploreOptions(10, 1, 0.0, 5);

        explorer.exploreByPattern(
            "theory", "test-relation",
            "[lemma=\"theory\"] [xpos=\"JJ.*\"]",
            "[xpos=\"JJ.*\"]",
            opts);

        // The noun lookup uses collocateReversePattern() from the first NOUN relation: [xpos="NN.*"]
        assertTrue(executor.capturedCqlPatterns.stream()
            .anyMatch(p -> p.contains("NN")),
            "Config-derived noun constraint should contain 'NN'; got: " + executor.capturedCqlPatterns);
    }
}
