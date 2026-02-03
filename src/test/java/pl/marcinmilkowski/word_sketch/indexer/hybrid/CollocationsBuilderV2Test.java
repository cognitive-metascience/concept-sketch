package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CollocationsBuilderV2Test {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("V2 builder produces collocations.bin on tiny corpus")
    void buildsTinyCorpus() throws Exception {
        Path indexPath = tempDir.resolve("index");
        Path statsPath = indexPath.resolve("stats.tsv");
        Path outPath = tempDir.resolve("collocations.bin");

        try (HybridIndexer indexer = new HybridIndexer(indexPath.toString())) {
            indexer.indexSentence(SentenceDocument.builder()
                .sentenceId(1)
                .text("a b a")
                .addToken(0, "a", "a", "X", 0, 1)
                .addToken(1, "b", "b", "X", 2, 3)
                .addToken(2, "a", "a", "X", 4, 5)
                .build());
            indexer.commit();
            indexer.writeStatistics(statsPath.toString());
        }

        CollocationsBuilderV2 b = new CollocationsBuilderV2(indexPath.toString());
        b.setWindowSize(1);
        b.setTopK(10);
        b.setMinHeadwordFrequency(1);
        b.setMinCooccurrence(1);
        b.setNumShards(2);
        b.setSpillThresholdPerShard(10); // force frequent spills on tiny map
        b.build(outPath.toString());

        try (CollocationsReader r = new CollocationsReader(outPath.toString())) {
            CollocationEntry a = r.getCollocations("a");
            assertNotNull(a);
            assertEquals(2, a.headwordFrequency());
            assertFalse(a.collocates().isEmpty());

            Collocation top = a.collocates().get(0);
            assertEquals("b", top.lemma());
            assertEquals(2, top.cooccurrence());
            assertEquals(1, top.frequency());
            assertTrue(top.logDice() > 0);
        }
    }
}
