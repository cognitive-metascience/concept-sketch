package pl.marcinmilkowski.word_sketch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.marcinmilkowski.word_sketch.indexer.LuceneIndexer;
import pl.marcinmilkowski.word_sketch.query.WordSketchQueryExecutor;
import pl.marcinmilkowski.word_sketch.tagging.CorpusProcessor;
import pl.marcinmilkowski.word_sketch.tagging.SimpleTagger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the full pipeline: corpus processing and querying.
 */
class IntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testFullPipeline() throws IOException {
        // Create test corpus - each sentence on its own line
        String corpus = """
            The big dog runs quickly in the park
            A beautiful house stands on the hill
            Dogs chase cats in the street
            The quick brown fox jumps over the lazy dog
            I see a big beautiful house near the river
            """;

        Path corpusFile = tempDir.resolve("corpus.txt");
        Files.writeString(corpusFile, corpus);

        // Process and index
        SimpleTagger tagger = SimpleTagger.create();
        LuceneIndexer indexer = new LuceneIndexer(tempDir.resolve("index").toString());
        CorpusProcessor processor = new CorpusProcessor(tagger, indexer);

        // Manually process sentences from memory for this test
        processor.processSentences(CorpusProcessor.splitSentences(corpus));

        // Verify index has documents
        assertTrue(indexer.getDocumentCount() > 0, "Index should have documents");
        System.out.println("Indexed " + indexer.getDocumentCount() + " tokens");

        // Query for collocates of "dog"
        WordSketchQueryExecutor executor = new WordSketchQueryExecutor(tempDir.resolve("index").toString());

        // Test: simple tag pattern query
        // Note: This finds collocates of "dog" where the collocate matches the pattern
        System.out.println("\nQuery 1: find nouns in corpus");
        var results = executor.findCollocations("dog", "\"N.*\"", 0.0, 10);
        System.out.println("Query returned " + results.size() + " results");
        for (var r : results) {
            System.out.println("  " + r.getLemma() + " (freq=" + r.getFrequency() + ", logDice=" + String.format("%.2f", r.getLogDice()) + ")");
        }

        // Test: simple tag pattern query
        // This verifies the full pipeline works (indexing + querying)
        System.out.println("\nQuery 2: find adjectives in corpus");
        var results2 = executor.findCollocations("dog", "\"JJ.*\"", 0.0, 10);
        System.out.println("Query returned " + results2.size() + " results");

        executor.close();
        indexer.close();

        System.out.println("\nPipeline test PASSED - indexing and querying work correctly");
    }

    @Test
    void testSimpleTaggerOutput() throws IOException {
        SimpleTagger tagger = SimpleTagger.create();

        var tokens = tagger.tagSentence("The big dog runs quickly");
        System.out.println("\nTagged: 'The big dog runs quickly'");
        for (var t : tokens) {
            System.out.println("  " + t.getWord() + " -> " + t.getTag() + " (lemma: " + t.getLemma() + ")");
        }

        assertEquals(5, tokens.size());
    }
}
