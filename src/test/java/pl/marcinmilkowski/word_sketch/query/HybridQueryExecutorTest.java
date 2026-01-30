package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import pl.marcinmilkowski.word_sketch.indexer.hybrid.HybridIndexer;
import pl.marcinmilkowski.word_sketch.indexer.hybrid.SentenceDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HybridQueryExecutor.
 */
class HybridQueryExecutorTest {

    @TempDir
    Path tempDir;

    private Path indexPath;
    private Path statsPath;

    @BeforeEach
    void setUp() throws IOException {
        indexPath = tempDir.resolve("index");
        statsPath = tempDir.resolve("stats.tsv");
        
        // Create a test index with sample sentences
        try (HybridIndexer indexer = new HybridIndexer(indexPath.toString())) {
            // Sentence 1: "The big cat sat on the mat"
            indexer.indexSentence(SentenceDocument.builder()
                .sentenceId(1)
                .text("The big cat sat on the mat.")
                .addToken(0, "The", "the", "DT", 0, 3)
                .addToken(1, "big", "big", "JJ", 4, 7)
                .addToken(2, "cat", "cat", "NN", 8, 11)
                .addToken(3, "sat", "sit", "VBD", 12, 15)
                .addToken(4, "on", "on", "IN", 16, 18)
                .addToken(5, "the", "the", "DT", 19, 22)
                .addToken(6, "mat", "mat", "NN", 23, 26)
                .addToken(7, ".", ".", ".", 26, 27)
                .build());

            // Sentence 2: "The small cat runs quickly"
            indexer.indexSentence(SentenceDocument.builder()
                .sentenceId(2)
                .text("The small cat runs quickly.")
                .addToken(0, "The", "the", "DT", 0, 3)
                .addToken(1, "small", "small", "JJ", 4, 9)
                .addToken(2, "cat", "cat", "NN", 10, 13)
                .addToken(3, "runs", "run", "VBZ", 14, 18)
                .addToken(4, "quickly", "quickly", "RB", 19, 26)
                .addToken(5, ".", ".", ".", 26, 27)
                .build());

            // Sentence 3: "A lazy cat sleeps"
            indexer.indexSentence(SentenceDocument.builder()
                .sentenceId(3)
                .text("A lazy cat sleeps.")
                .addToken(0, "A", "a", "DT", 0, 1)
                .addToken(1, "lazy", "lazy", "JJ", 2, 6)
                .addToken(2, "cat", "cat", "NN", 7, 10)
                .addToken(3, "sleeps", "sleep", "VBZ", 11, 17)
                .addToken(4, ".", ".", ".", 17, 18)
                .build());

            // Sentence 4: "Dogs chase cats"
            indexer.indexSentence(SentenceDocument.builder()
                .sentenceId(4)
                .text("Dogs chase cats.")
                .addToken(0, "Dogs", "dog", "NNS", 0, 4)
                .addToken(1, "chase", "chase", "VBP", 5, 10)
                .addToken(2, "cats", "cat", "NNS", 11, 15)
                .addToken(3, ".", ".", ".", 15, 16)
                .build());

            indexer.commit();
            indexer.writeStatistics(statsPath.toString());
        }
    }

    @Test
    @DisplayName("Find basic collocations for 'cat'")
    void findBasicCollocations() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            List<WordSketchQueryExecutor.WordSketchResult> results = 
                executor.findCollocations("cat", "[tag=\".*\"]", 0, 10);

            assertFalse(results.isEmpty(), "Should find collocations for 'cat'");
            
            // Check that we find adjectives (big, small, lazy)
            List<String> lemmas = results.stream()
                .map(WordSketchQueryExecutor.WordSketchResult::getLemma)
                .toList();
            
            assertTrue(lemmas.contains("big") || lemmas.contains("small") || lemmas.contains("lazy"),
                "Should find adjective collocates");
        }
    }

    @Test
    @DisplayName("Find adjective collocations with tag constraint")
    void findAdjectiveCollocations() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            List<WordSketchQueryExecutor.WordSketchResult> results = 
                executor.findCollocations("cat", "[tag=\"JJ\"]", 0, 10);

            assertFalse(results.isEmpty(), "Should find adjective collocations");
            
            // All results should be adjectives
            for (var result : results) {
                assertTrue(result.getPos().startsWith("JJ"),
                    "Expected adjective, got: " + result.getPos());
            }
        }
    }

    @Test
    @DisplayName("Find verb collocations")
    void findVerbCollocations() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            List<WordSketchQueryExecutor.WordSketchResult> results = 
                executor.findCollocations("cat", "[tag=\"VB.*\"]", 0, 10);

            // Should find sit, run, sleep, chase
            List<String> lemmas = results.stream()
                .map(WordSketchQueryExecutor.WordSketchResult::getLemma)
                .toList();
            
            assertTrue(lemmas.contains("sit") || lemmas.contains("run") || 
                       lemmas.contains("sleep") || lemmas.contains("chase"),
                "Should find verb collocates");
        }
    }

    @Test
    @DisplayName("getTotalFrequency returns correct counts")
    void getTotalFrequency() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            // "cat" appears in 4 sentences (3x as "cat", 1x as "cats")
            long catFreq = executor.getTotalFrequency("cat");
            assertEquals(4, catFreq, "cat should appear 4 times");
            
            // "the" appears 3 times (2 in sentence 1, 1 in sentence 2)
            long theFreq = executor.getTotalFrequency("the");
            assertEquals(3, theFreq, "the should appear 3 times");
            
            // "dog" appears 1 time
            long dogFreq = executor.getTotalFrequency("dog");
            assertEquals(1, dogFreq, "dog should appear 1 time");
        }
    }

    @Test
    @DisplayName("Unknown headword returns empty list")
    void unknownHeadword() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            List<WordSketchQueryExecutor.WordSketchResult> results = 
                executor.findCollocations("nonexistent", "[tag=\".*\"]", 0, 10);

            assertTrue(results.isEmpty(), "Unknown headword should return empty list");
        }
    }

    @Test
    @DisplayName("Null headword returns empty list")
    void nullHeadword() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            List<WordSketchQueryExecutor.WordSketchResult> results = 
                executor.findCollocations(null, "[tag=\".*\"]", 0, 10);

            assertTrue(results.isEmpty(), "Null headword should return empty list");
        }
    }

    @Test
    @DisplayName("LogDice scores are reasonable")
    void logDiceScores() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            List<WordSketchQueryExecutor.WordSketchResult> results = 
                executor.findCollocations("cat", "[tag=\"JJ\"]", 0, 10);

            for (var result : results) {
                assertTrue(result.getLogDice() >= 0 && result.getLogDice() <= 14,
                    "LogDice should be in range 0-14, got: " + result.getLogDice());
            }
        }
    }

    @Test
    @DisplayName("Results are sorted by logDice descending")
    void resultsSortedByLogDice() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            List<WordSketchQueryExecutor.WordSketchResult> results = 
                executor.findCollocations("cat", "[tag=\".*\"]", 0, 10);

            for (int i = 1; i < results.size(); i++) {
                assertTrue(results.get(i - 1).getLogDice() >= results.get(i).getLogDice(),
                    "Results should be sorted by logDice descending");
            }
        }
    }

    @Test
    @DisplayName("getExecutorType returns 'hybrid'")
    void getExecutorType() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            assertEquals("hybrid", executor.getExecutorType());
        }
    }

    @Test
    @DisplayName("Examples are included in results")
    void examplesIncluded() throws IOException {
        try (HybridQueryExecutor executor = new HybridQueryExecutor(
                indexPath.toString(), statsPath.toString())) {
            
            List<WordSketchQueryExecutor.WordSketchResult> results = 
                executor.findCollocations("cat", "[tag=\"JJ\"]", 0, 10);

            // At least one result should have examples
            boolean hasExamples = results.stream()
                .anyMatch(r -> !r.getExamples().isEmpty());
            
            assertTrue(hasExamples, "At least one result should have examples");
        }
    }
}
