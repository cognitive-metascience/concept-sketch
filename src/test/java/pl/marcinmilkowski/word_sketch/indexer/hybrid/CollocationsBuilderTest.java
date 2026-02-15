package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import pl.marcinmilkowski.word_sketch.config.GrammarConfigLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CollocationsBuilder.
 * 
 * Tests binary format, hash index, and builder logic.
 */
class CollocationsBuilderTest {

    @TempDir
    Path tempDir;

    private String testIndexPath;
    private String testOutputPath;

    @BeforeEach
    void setUp() throws IOException {
        testIndexPath = "target/index-quarter";  // Use quarter index for faster tests
        testOutputPath = tempDir.resolve("collocations.bin").toString();
    }

    @Test
    @DisplayName("Builder should create valid binary file")
    void testBuildCreatesValidFile() throws IOException, InterruptedException {
        // Skip if test index doesn't exist
        if (!Files.exists(Path.of(testIndexPath))) {
            System.out.println("Skipping test: quarter index not found at " + testIndexPath);
            return;
        }

        CollocationsBuilder builder = new CollocationsBuilder(testIndexPath, testIndexPath + "/stats.bin");
        builder.setTopK(50);  // Smaller for faster test
        builder.setMinFrequency(100);  // Only frequent lemmas
        builder.setThreads(2);

        builder.build(testOutputPath);

        // Verify file exists and has content
        assertTrue(Files.exists(Path.of(testOutputPath)));
        assertTrue(Files.size(Path.of(testOutputPath)) > 100);
    }

    @Test
    @DisplayName("Binary roundtrip should preserve data")
    void testBinaryRoundtrip() throws IOException, InterruptedException {
        if (!Files.exists(Path.of(testIndexPath))) {
            System.out.println("Skipping test: quarter index not found");
            return;
        }

        // Build with small dataset
        CollocationsBuilder builder = new CollocationsBuilder(testIndexPath, testIndexPath + "/stats.bin");
        builder.setTopK(20);
        builder.setMinFrequency(500);  // Very frequent only
        builder.setThreads(1);

        builder.build(testOutputPath);

        // Read back
        CollocationsReader reader = new CollocationsReader(testOutputPath);

        // Verify metadata
        assertTrue(reader.getEntryCount() > 0);
        assertEquals(5, reader.getWindowSize());  // Default window
        assertEquals(20, reader.getTopK());

        // Verify we can lookup some common words
        String[] commonWords = {"the", "be", "of", "and", "to"};
        int found = 0;
        for (String word : commonWords) {
            if (reader.hasLemma(word)) {
                CollocationEntry entry = reader.getCollocations(word);
                assertNotNull(entry);
                assertEquals(word, entry.headword());
                assertTrue(entry.headwordFrequency() > 0);
                assertTrue(entry.collocates().size() <= 20);
                found++;
            }
        }
        assertTrue(found > 0, "Should find at least one common word");

        reader.close();
    }

    @Test
    @DisplayName("Hash index should provide O(1) lookup")
    void testHashIndexLookup() throws IOException, InterruptedException {
        if (!Files.exists(Path.of(testIndexPath))) {
            System.out.println("Skipping test: quarter index not found");
            return;
        }

        CollocationsBuilder builder = new CollocationsBuilder(testIndexPath, testIndexPath + "/stats.bin");
        builder.setTopK(30);
        builder.setMinFrequency(200);
        builder.build(testOutputPath);

        CollocationsReader reader = new CollocationsReader(testOutputPath);

        // Multiple lookups should be fast
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            reader.getCollocations("the");
        }
        long duration = (System.nanoTime() - start) / 1_000_000;

        // 100 lookups should take < 10ms
        assertTrue(duration < 10, "100 lookups took " + duration + "ms (expected < 10ms)");

        reader.close();
    }

    @Test
    @DisplayName("Builder should handle missing index gracefully")
    void testMissingIndex() {
        String badPath = tempDir.resolve("nonexistent").toString();
        assertThrows(Exception.class, () -> {
            new CollocationsBuilder(badPath, badPath + "/stats.bin");
        });
    }

    @Test
    @DisplayName("Configuration setters should work")
    void testConfiguration() throws IOException, InterruptedException {
        if (!Files.exists(Path.of(testIndexPath))) {
            return;
        }

        CollocationsBuilder builder = new CollocationsBuilder(testIndexPath, testIndexPath + "/stats.bin");
        
        builder.setWindowSize(7);
        builder.setTopK(100);
        builder.setMinFrequency(50);
        builder.setMinCooccurrence(3);
        builder.setThreads(4);
        builder.setBatchSize(500);

        // Build should succeed
        builder.build(testOutputPath);

        // Verify metadata
        CollocationsReader reader = new CollocationsReader(testOutputPath);
        assertEquals(7, reader.getWindowSize());
        assertEquals(100, reader.getTopK());
        reader.close();
    }

    @Test
    @DisplayName("Empty lemma should return no collocations")
    void testEmptyLemma() throws IOException, InterruptedException {
        if (!Files.exists(Path.of(testIndexPath))) {
            return;
        }

        CollocationsBuilder builder = new CollocationsBuilder(testIndexPath, testIndexPath + "/stats.bin");
        builder.setMinFrequency(100);
        builder.build(testOutputPath);

        CollocationsReader reader = new CollocationsReader(testOutputPath);
        assertNull(reader.getCollocations(""));
        assertNull(reader.getCollocations(null));
        assertFalse(reader.hasLemma("zzz_nonexistent_word_zzz"));
        reader.close();
    }

    @Test
    @DisplayName("Build supports resume: second run with resume=true does not duplicate entries")
    void testResumeBehavior() throws IOException, InterruptedException {
        if (!Files.exists(Path.of(testIndexPath))) {
            return;
        }

        CollocationsBuilder builder = new CollocationsBuilder(testIndexPath, testIndexPath + "/stats.bin");
        builder.setTopK(20);
        builder.setMinFrequency(500);
        builder.setThreads(1);

        // First build
        builder.build(testOutputPath);
        CollocationsReader reader = new CollocationsReader(testOutputPath);
        int firstCount = reader.getEntryCount();
        reader.close();

        // Second build with resume enabled should not add entries
        builder.setResume(true);
        builder.build(testOutputPath);

        CollocationsReader reader2 = new CollocationsReader(testOutputPath);
        int secondCount = reader2.getEntryCount();
        reader2.close();

        assertEquals(firstCount, secondCount, "Resume build should preserve entry count");
    }

    @Test
    @DisplayName("Relation collocations build creates output file")
    void testBuildRelationCollocationsCreatesFile() throws IOException, InterruptedException {
        if (!Files.exists(Path.of(testIndexPath))) {
            return;
        }

        CollocationsBuilder builder = new CollocationsBuilder(testIndexPath, testIndexPath + "/stats.bin");
        String outRelPath = tempDir.resolve("relation_collocations.bin").toString();
        var grammar = GrammarConfigLoader.createDefaultEnglish();

        builder.setMinFrequency(100);
        builder.setThreads(1);
        builder.buildRelationCollocations(outRelPath, grammar);

        assertTrue(Files.exists(Path.of(outRelPath)));
        assertTrue(Files.size(Path.of(outRelPath)) > 100);
    }}
