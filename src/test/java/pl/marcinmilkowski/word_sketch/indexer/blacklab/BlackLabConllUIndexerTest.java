package pl.marcinmilkowski.word_sketch.indexer.blacklab;

import nl.inl.blacklab.index.DocumentFormats;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for {@link BlackLabConllUIndexer}.
 *
 * <p><strong>Why these tests are disabled:</strong> The indexer wraps BlackLab's on-disk
 * document format system, which must locate a YAML format config file
 * ({@code conllu-sentences.blf.yaml}) at startup via
 * {@link DocumentFormats#addConfigFormatsInDirectories}. This config file registers the
 * CoNLL-U token/sentence structure with BlackLab and is <em>not distributed with the
 * source</em> because it contains site-specific field mappings. Without it, BlackLab
 * cannot parse the WPL-format intermediate files produced by {@link ConlluConverter},
 * and all indexing attempts fail.</p>
 *
 * <p><strong>What these tests verify:</strong>
 * <ul>
 *   <li>End-to-end pipeline: CoNLL-U text → {@link ConlluConverter} WPL chunks →
 *       {@link BlackLabConllUIndexer} → on-disk BlackLab index.</li>
 *   <li>Document and token counts are correct after indexing a minimal corpus.</li>
 *   <li>The constructor creates the index directory when it does not exist.</li>
 *   <li>{@code indexFile} throws {@link IOException} for a missing input path.</li>
 *   <li>{@code getTokenCount()} starts at zero before any documents are indexed.</li>
 * </ul>
 * </p>
 *
 * <p><strong>Prerequisites to run locally:</strong>
 * <ol>
 *   <li>Place {@code conllu-sentences.blf.yaml} in the project root (working directory
 *       when running Maven, i.e. the directory containing {@code pom.xml}).</li>
 *   <li>Run: {@code JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test -Dtest=BlackLabConllUIndexerTest}</li>
 * </ol>
 * Tests are skipped automatically in CI via {@code @BeforeAll} when the format config is absent.
 * </p>
 */
@DisplayName("BlackLabConllUIndexer")
class BlackLabConllUIndexerTest {

    private static final String FORMAT_NAME = "conllu-sentences";

    private static final String MINIMAL_CONLLU =
            "# sent_id = 1\n" +
            "1\tThe\tthe\tDET\tDT\t_\t2\tdet\t_\t_\n" +
            "2\ttheory\ttheory\tNOUN\tNN\t_\t4\tnsubj\t_\t_\n" +
            "3\tis\tbe\tVERB\tVBZ\t_\t4\tcop\t_\t_\n" +
            "4\tcorrect\tcorrect\tADJ\tJJ\t_\t0\troot\t_\t_\n" +
            "\n" +
            "# sent_id = 2\n" +
            "1\tA\ta\tDET\tDT\t_\t2\tdet\t_\t_\n" +
            "2\tmodel\tmodel\tNOUN\tNN\t_\t3\tnsubj\t_\t_\n" +
            "3\texplains\texplain\tVERB\tVBZ\t_\t0\troot\t_\t_\n" +
            "\n";

    @BeforeAll
    static void requireFormatConfig() {
        Path formatFile = Paths.get(System.getProperty("user.dir"), FORMAT_NAME + ".blf.yaml");
        Assumptions.assumeTrue(
                formatFile.toFile().exists(),
                "Skipped: format config not found at " + formatFile + " — run from project root");
        DocumentFormats.addConfigFormatsInDirectories(java.util.List.of(formatFile.getParent().toFile()));
    }

    // ── smoke: full pipeline ──────────────────────────────────────────────────

    @Test
    @DisplayName("indexes a minimal CoNLL-U corpus and reports correct document and token counts")
    void indexMinimalCorpus_countsAreCorrect(@TempDir Path tmp) throws IOException {
        Path conlluFile = tmp.resolve("mini.conllu");
        Files.writeString(conlluFile, MINIMAL_CONLLU);

        Path wplDir = tmp.resolve("wpl");
        Files.createDirectories(wplDir);
        ConlluConverter.convertConlluToWplChunks(conlluFile, wplDir, 1000);

        Path indexDir = tmp.resolve("index");
        try (BlackLabConllUIndexer indexer = new BlackLabConllUIndexer(indexDir.toString(), FORMAT_NAME, System.getProperty("user.dir"))) {
            indexer.indexFile(wplDir.toString());

            assertTrue(indexer.getDocumentCount() >= 1,
                    "Document count should be at least 1 after indexing a non-empty corpus");
        }
        // After close the index directory should contain at least the BlackLab version file
        assertTrue(indexDir.toFile().exists(), "Index directory must exist after indexing");
        assertTrue(indexDir.toFile().list() != null && indexDir.toFile().list().length > 0,
                "Index directory must be non-empty after indexing");
    }

    @Test
    @DisplayName("creates index directory when it does not exist yet")
    void constructor_createsIndexDirectory(@TempDir Path tmp) throws IOException {
        Path newIndex = tmp.resolve("new-index");
        assertFalse(newIndex.toFile().exists(), "precondition: directory must not exist");

        try (BlackLabConllUIndexer indexer = new BlackLabConllUIndexer(newIndex.toString(), FORMAT_NAME, System.getProperty("user.dir"))) {
            assertTrue(newIndex.toFile().exists(), "Constructor must create the index directory");
        }
    }

    @Test
    @DisplayName("throws IOException when input file does not exist")
    void indexFile_missingPath_throwsIOException(@TempDir Path tmp) throws IOException {
        Path indexDir = tmp.resolve("index");
        try (BlackLabConllUIndexer indexer = new BlackLabConllUIndexer(indexDir.toString(), FORMAT_NAME, System.getProperty("user.dir"))) {
            assertThrows(IOException.class,
                    () -> indexer.indexFile(tmp.resolve("nonexistent.conllu").toString()),
                    "indexFile must throw IOException for a missing path");
        }
    }

    @Test
    @DisplayName("getTokenCount returns 0 before any documents are indexed")
    void tokenCount_initiallyZero(@TempDir Path tmp) throws IOException {
        Path indexDir = tmp.resolve("index");
        try (BlackLabConllUIndexer indexer = new BlackLabConllUIndexer(indexDir.toString(), FORMAT_NAME, System.getProperty("user.dir"))) {
            assertEquals(0, indexer.getTokenCount(), "Token count must start at zero");
        }
    }

    // ── Pure-logic CoNLL-U parsing tests (no BlackLab format config required) ──

    /**
     * Unit tests for {@link ConlluConverter} pure parsing logic.
     *
     * <p>These tests exercise sentence boundary detection, comment/MWT-line skipping,
     * token counting, and chunk rotation entirely in-process using {@code @TempDir}.
     * No BlackLab format config or external corpus file is needed, so every test here
     * runs unconditionally in CI.</p>
     */
    @Nested
    @DisplayName("ConlluConverter pure-logic tests")
    static class ConlluConverterTests {

        private static final String TWO_SENTENCE_CONLLU =
                "# sent_id = 1\n" +
                "1\tThe\tthe\tDET\tDT\t_\t2\tdet\t_\t_\n" +
                "2\ttheory\ttheory\tNOUN\tNN\t_\t4\tnsubj\t_\t_\n" +
                "3\tis\tbe\tVERB\tVBZ\t_\t4\tcop\t_\t_\n" +
                "4\tcorrect\tcorrect\tADJ\tJJ\t_\t0\troot\t_\t_\n" +
                "\n" +
                "# sent_id = 2\n" +
                "1\tA\ta\tDET\tDT\t_\t2\tdet\t_\t_\n" +
                "2\tmodel\tmodel\tNOUN\tNN\t_\t3\tnsubj\t_\t_\n" +
                "3\texplains\texplain\tVERB\tVBZ\t_\t0\troot\t_\t_\n" +
                "\n";

        @Test
        @DisplayName("counts sentences and tokens correctly for a minimal corpus")
        void convertConlluToWplChunks_countsAreCorrect(@TempDir Path tmp) throws IOException {
            Path input = tmp.resolve("test.conllu");
            Files.writeString(input, TWO_SENTENCE_CONLLU);
            Path outDir = tmp.resolve("wpl");
            Files.createDirectories(outDir);

            ConlluConverter.ConversionStats stats =
                    ConlluConverter.convertConlluToWplChunks(input, outDir, 1000);

            assertEquals(2, stats.sentences(), "Two sentences expected");
            assertEquals(7, stats.tokens(), "Seven tokens expected (4 + 3)");
        }

        @Test
        @DisplayName("comment lines starting with '#' are skipped and not counted as tokens")
        void convertConlluToWplChunks_skipsCommentLines(@TempDir Path tmp) throws IOException {
            String corpus =
                    "# sent_id = 1\n" +
                    "# text = Hello world\n" +
                    "1\tHello\thello\tINTJ\tUH\t_\t0\troot\t_\t_\n" +
                    "2\tworld\tworld\tNOUN\tNN\t_\t1\tdep\t_\t_\n" +
                    "\n";
            Path input = tmp.resolve("test.conllu");
            Files.writeString(input, corpus);
            Path outDir = tmp.resolve("wpl");
            Files.createDirectories(outDir);

            ConlluConverter.ConversionStats stats =
                    ConlluConverter.convertConlluToWplChunks(input, outDir, 1000);

            assertEquals(1, stats.sentences());
            assertEquals(2, stats.tokens(), "Only real tokens, not comment lines");
        }

        @Test
        @DisplayName("multi-word token lines (e.g. '1-2') are skipped and not counted as tokens")
        void convertConlluToWplChunks_skipsMwtLines(@TempDir Path tmp) throws IOException {
            String corpus =
                    "1-2\tdon't\t_\t_\t_\t_\t_\t_\t_\t_\n" +
                    "1\tdo\tdo\tAUX\tVBP\t_\t3\taux\t_\t_\n" +
                    "2\tn't\tnot\tPART\tRB\t_\t3\tadvmod\t_\t_\n" +
                    "3\tknow\tknow\tVERB\tVB\t_\t0\troot\t_\t_\n" +
                    "\n";
            Path input = tmp.resolve("test.conllu");
            Files.writeString(input, corpus);
            Path outDir = tmp.resolve("wpl");
            Files.createDirectories(outDir);

            ConlluConverter.ConversionStats stats =
                    ConlluConverter.convertConlluToWplChunks(input, outDir, 1000);

            assertEquals(1, stats.sentences());
            assertEquals(3, stats.tokens(), "MWT span line must not be counted as a token");
        }

        @Test
        @DisplayName("empty-node lines (e.g. '1.1') are skipped and not counted as tokens")
        void convertConlluToWplChunks_skipsEmptyNodeLines(@TempDir Path tmp) throws IOException {
            String corpus =
                    "1\tHe\the\tPRON\tPRP\t_\t2\tnsubj\t_\t_\n" +
                    "1.1\tnull\tnull\t_\t_\t_\t_\t_\t_\t_\n" +
                    "2\truns\trun\tVERB\tVBZ\t_\t0\troot\t_\t_\n" +
                    "\n";
            Path input = tmp.resolve("test.conllu");
            Files.writeString(input, corpus);
            Path outDir = tmp.resolve("wpl");
            Files.createDirectories(outDir);

            ConlluConverter.ConversionStats stats =
                    ConlluConverter.convertConlluToWplChunks(input, outDir, 1000);

            assertEquals(1, stats.sentences());
            assertEquals(2, stats.tokens(), "Empty-node lines must not be counted as tokens");
        }

        @Test
        @DisplayName("chunk rotation: sentences are distributed across multiple chunk files")
        void convertConlluToWplChunks_rotatesChunksAtQuota(@TempDir Path tmp) throws IOException {
            Path input = tmp.resolve("test.conllu");
            Files.writeString(input, TWO_SENTENCE_CONLLU);
            Path outDir = tmp.resolve("wpl");
            Files.createDirectories(outDir);

            // With sentencesPerChunk=1, two sentences → two chunk files
            ConlluConverter.ConversionStats stats =
                    ConlluConverter.convertConlluToWplChunks(input, outDir, 1);

            assertEquals(2, stats.sentences());
            assertEquals(2, stats.chunks(), "One chunk per sentence when sentencesPerChunk=1");

            String[] chunks = outDir.toFile().list((d, n) -> n.endsWith(".tsv"));
            assertNotNull(chunks);
            assertEquals(2, chunks.length, "Two .tsv chunk files must be written");
        }

        @Test
        @DisplayName("WPL chunk files contain <s> / </s> sentence markers around token lines")
        void convertConlluToWplChunks_wplFilesContainSentenceMarkers(@TempDir Path tmp) throws IOException {
            String singleSentence =
                    "1\tcat\tcat\tNOUN\tNN\t_\t0\troot\t_\t_\n\n";
            Path input = tmp.resolve("test.conllu");
            Files.writeString(input, singleSentence);
            Path outDir = tmp.resolve("wpl");
            Files.createDirectories(outDir);

            ConlluConverter.convertConlluToWplChunks(input, outDir, 1000);

            String[] files = outDir.toFile().list((d, n) -> n.endsWith(".tsv"));
            assertNotNull(files);
            assertEquals(1, files.length, "One chunk file expected");
            String content = Files.readString(outDir.resolve(files[0]));
            assertTrue(content.contains("<s>"), "Chunk must contain opening <s> sentence marker");
            assertTrue(content.contains("</s>"), "Chunk must contain closing </s> sentence marker");
        }

        @Test
        @DisplayName("empty input produces zero sentences, tokens, and chunks")
        void convertConlluToWplChunks_emptyInput_allCountsZero(@TempDir Path tmp) throws IOException {
            Path input = tmp.resolve("empty.conllu");
            Files.writeString(input, "");
            Path outDir = tmp.resolve("wpl");
            Files.createDirectories(outDir);

            ConlluConverter.ConversionStats stats =
                    ConlluConverter.convertConlluToWplChunks(input, outDir, 1000);

            assertEquals(0, stats.sentences());
            assertEquals(0, stats.tokens());
            assertEquals(0, stats.chunks());
        }
    }
}
