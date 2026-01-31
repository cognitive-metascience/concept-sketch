package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pl.marcinmilkowski.word_sketch.query.SemanticFieldExplorer.*;
import pl.marcinmilkowski.word_sketch.query.WordSketchQueryExecutor.WordSketchResult;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SemanticFieldExplorer with graded comparison.
 */
class SemanticFieldExplorerTest {
    
    private MockQueryExecutor mockExecutor;
    private SemanticFieldExplorer explorer;
    
    @BeforeEach
    void setUp() {
        mockExecutor = new MockQueryExecutor();
        explorer = new SemanticFieldExplorer(mockExecutor);
    }
    
    @Test
    @DisplayName("Empty input returns empty result")
    void testEmptyInput() throws IOException {
        ComparisonResult result = explorer.compare(Collections.emptySet(), 3.0, 50);
        
        assertTrue(result.getNouns().isEmpty());
        assertTrue(result.getAllAdjectives().isEmpty());
    }
    
    @Test
    @DisplayName("Null input returns empty result")
    void testNullInput() throws IOException {
        ComparisonResult result = explorer.compare(null, 3.0, 50);
        
        assertTrue(result.getNouns().isEmpty());
    }
    
    @Test
    @DisplayName("Single noun - all adjectives are specific")
    void testSingleNoun() throws IOException {
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("accurate", "JJ", 100, 6.5, 0.1, List.of()),
            new WordSketchResult("consistent", "JJ", 80, 5.8, 0.08, List.of()),
            new WordSketchResult("elegant", "JJ", 60, 5.2, 0.06, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory"), 3.0, 50);
        
        assertEquals(1, result.getNouns().size());
        assertEquals(3, result.getAllAdjectives().size());
        
        // With 1 noun, all adjectives have presentInCount=1 (specific)
        for (AdjectiveProfile adj : result.getAllAdjectives()) {
            assertEquals(1, adj.presentInCount);
            assertEquals(1, adj.totalNouns);
            assertTrue(adj.isFullyShared()); // 1/1 = fully shared
        }
    }
    
    @Test
    @DisplayName("Two nouns - graded comparison shows shared and specific")
    void testTwoNounsGradedComparison() throws IOException {
        // "accurate" shared, "elegant" only theory, "simple" only model
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("accurate", "JJ", 100, 6.5, 0.1, List.of()),
            new WordSketchResult("elegant", "JJ", 60, 5.2, 0.06, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("accurate", "JJ", 90, 5.8, 0.09, List.of()),
            new WordSketchResult("simple", "JJ", 50, 4.8, 0.05, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory", "model"), 3.0, 50);
        
        assertEquals(2, result.getNouns().size());
        assertEquals(3, result.getAllAdjectives().size());
        
        // Check fully shared
        List<AdjectiveProfile> fullyShared = result.getFullyShared();
        assertEquals(1, fullyShared.size());
        assertEquals("accurate", fullyShared.get(0).adjective);
        assertEquals(2, fullyShared.get(0).presentInCount);
        
        // Check specific
        List<AdjectiveProfile> specific = result.getSpecific();
        assertEquals(2, specific.size());
        
        // Check noun scores are graded
        AdjectiveProfile accurate = fullyShared.get(0);
        assertEquals(6.5, accurate.nounScores.get("theory"), 0.01);
        assertEquals(5.8, accurate.nounScores.get("model"), 0.01);
    }
    
    @Test
    @DisplayName("Three nouns - partially shared adjectives")
    void testThreeNounsPartiallyShared() throws IOException {
        // "accurate" shared by all 3
        // "testable" shared by theory and hypothesis (2/3)
        // "simple" only model
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("accurate", "JJ", 100, 6.0, 0.1, List.of()),
            new WordSketchResult("testable", "JJ", 70, 4.0, 0.07, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("accurate", "JJ", 90, 5.5, 0.09, List.of()),
            new WordSketchResult("simple", "JJ", 50, 3.8, 0.05, List.of())
        ));
        
        mockExecutor.addCollocations("hypothesis", List.of(
            new WordSketchResult("accurate", "JJ", 85, 5.0, 0.085, List.of()),
            new WordSketchResult("testable", "JJ", 80, 4.5, 0.08, List.of())
        ));
        
        ComparisonResult result = explorer.compare(
            Set.of("theory", "model", "hypothesis"), 3.0, 50);
        
        assertEquals(3, result.getNouns().size());
        
        // Fully shared (all 3)
        List<AdjectiveProfile> fullyShared = result.getFullyShared();
        assertEquals(1, fullyShared.size());
        assertEquals("accurate", fullyShared.get(0).adjective);
        
        // Partially shared (2/3)
        List<AdjectiveProfile> partiallyShared = result.getPartiallyShared();
        assertEquals(1, partiallyShared.size());
        assertEquals("testable", partiallyShared.get(0).adjective);
        assertEquals(2, partiallyShared.get(0).presentInCount);
        
        // Specific (1/3)
        List<AdjectiveProfile> specific = result.getSpecific();
        assertEquals(1, specific.size());
        assertEquals("simple", specific.get(0).adjective);
    }
    
    @Test
    @DisplayName("Adjective scores include variance")
    void testVarianceCalculation() throws IOException {
        // "accurate" with similar scores across nouns (low variance)
        // "controversial" with very different scores (high variance)
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("accurate", "JJ", 100, 6.0, 0.1, List.of()),
            new WordSketchResult("controversial", "JJ", 70, 7.5, 0.07, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("accurate", "JJ", 90, 5.8, 0.09, List.of()),
            new WordSketchResult("controversial", "JJ", 30, 3.5, 0.03, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory", "model"), 3.0, 50);
        
        AdjectiveProfile accurate = result.getAllAdjectives().stream()
            .filter(a -> a.adjective.equals("accurate"))
            .findFirst().orElseThrow();
        
        AdjectiveProfile controversial = result.getAllAdjectives().stream()
            .filter(a -> a.adjective.equals("controversial"))
            .findFirst().orElseThrow();
        
        // Controversial has higher variance (7.5 vs 3.5 = diff of 4)
        assertTrue(controversial.variance > accurate.variance);
        
        // Controversial should have higher distinctiveness score
        assertTrue(controversial.distinctivenessScore > accurate.distinctivenessScore);
    }
    
    @Test
    @DisplayName("getSpecificTo returns adjectives for specific noun")
    void testGetSpecificTo() throws IOException {
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("elegant", "JJ", 60, 5.2, 0.06, List.of()),
            new WordSketchResult("abstract", "JJ", 50, 4.8, 0.05, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("computational", "JJ", 70, 5.5, 0.07, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory", "model"), 3.0, 50);
        
        List<AdjectiveProfile> theorySpecific = result.getSpecificTo("theory");
        assertEquals(2, theorySpecific.size());
        
        List<AdjectiveProfile> modelSpecific = result.getSpecificTo("model");
        assertEquals(1, modelSpecific.size());
        assertEquals("computational", modelSpecific.get(0).adjective);
    }
    
    @Test
    @DisplayName("getStrongestNoun identifies the noun with highest score")
    void testGetStrongestNoun() throws IOException {
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("rigorous", "JJ", 100, 7.2, 0.1, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("rigorous", "JJ", 50, 4.5, 0.05, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory", "model"), 3.0, 50);
        
        AdjectiveProfile rigorous = result.getAllAdjectives().get(0);
        assertEquals("theory", rigorous.getStrongestNoun());
    }
    
    @Test
    @DisplayName("Edges are created for all noun-adjective pairs")
    void testEdgesCreated() throws IOException {
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("accurate", "JJ", 100, 6.5, 0.1, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("accurate", "JJ", 90, 5.8, 0.09, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory", "model"), 3.0, 50);
        
        List<Edge> edges = result.getEdges();
        assertEquals(2, edges.size());
        
        // Both edges are for "accurate"
        assertTrue(edges.stream().allMatch(e -> e.source.equals("accurate")));
    }
    
    @Test
    @DisplayName("Commonality score ranks shared adjectives higher")
    void testCommonalityScoreRanking() throws IOException {
        // "common" shared by 2 nouns with avg 5.0 -> commonality = 10.0
        // "rare" only 1 noun with 7.0 -> commonality = 7.0
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("common", "JJ", 100, 5.0, 0.1, List.of()),
            new WordSketchResult("rare", "JJ", 50, 7.0, 0.05, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("common", "JJ", 90, 5.0, 0.09, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory", "model"), 3.0, 50);
        
        // Sorted by commonality score, "common" should be first
        assertEquals("common", result.getAllAdjectives().get(0).adjective);
        
        AdjectiveProfile common = result.getAllAdjectives().get(0);
        assertEquals(10.0, common.commonalityScore, 0.1);
    }
    
    @Test
    @DisplayName("Case insensitivity in adjective matching")
    void testCaseInsensitivity() throws IOException {
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("Accurate", "JJ", 100, 6.5, 0.1, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("accurate", "JJ", 90, 5.8, 0.09, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory", "model"), 3.0, 50);
        
        // Should be merged as one adjective
        List<AdjectiveProfile> fullyShared = result.getFullyShared();
        assertEquals(1, fullyShared.size());
        assertEquals(2, fullyShared.get(0).presentInCount);
    }
    
    @Test
    @DisplayName("toString provides readable summary")
    void testToString() throws IOException {
        mockExecutor.addCollocations("theory", List.of(
            new WordSketchResult("accurate", "JJ", 100, 6.5, 0.1, List.of())
        ));
        
        mockExecutor.addCollocations("model", List.of(
            new WordSketchResult("accurate", "JJ", 90, 5.8, 0.09, List.of()),
            new WordSketchResult("simple", "JJ", 50, 4.0, 0.05, List.of())
        ));
        
        ComparisonResult result = explorer.compare(Set.of("theory", "model"), 3.0, 50);
        
        String str = result.toString();
        assertTrue(str.contains("2 nouns"));
        assertTrue(str.contains("2 adjectives"));
    }
    
    // ==================== Mock Executor ====================
    
    /**
     * Mock executor that returns predefined collocations for specific headwords.
     */
    private static class MockQueryExecutor implements QueryExecutor {
        
        private final Map<String, List<WordSketchResult>> collocations = new HashMap<>();
        
        void addCollocations(String headword, List<WordSketchResult> results) {
            collocations.put(headword.toLowerCase(), results);
        }
        
        @Override
        public List<WordSketchResult> findCollocations(
                String headword, String collocatePattern, double minLogDice, int maxResults) {
            List<WordSketchResult> results = collocations.getOrDefault(headword.toLowerCase(), List.of());
            
            return results.stream()
                .filter(r -> r.getLogDice() >= minLogDice)
                .limit(maxResults)
                .toList();
        }
        
        @Override
        public long getTotalFrequency(String lemma) {
            return 1000;
        }
        
        @Override
        public void close() {
            // Nothing to close
        }
    }
}
