package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlackLabQueryExecutor.
 *
 * <p>BlackLabQueryExecutor wraps a live BlackLab index; all its public methods
 * require an on-disk index to operate.  To enable these tests, provide a valid
 * BlackLab index directory via the {@code CONCEPT_SKETCH_TEST_INDEX} environment
 * variable or the {@code conceptSketch.testIndex} system property, and remove
 * the {@code @Disabled} annotation from the class or individual tests.
 *
 * <p>The tests below are written and disabled so the intended behaviour is
 * documented and can be activated once a test index is available.
 */
@Disabled("Requires live BlackLab index — set CONCEPT_SKETCH_TEST_INDEX env var to enable")
class BlackLabQueryExecutorTest {

    private static final String INDEX_PATH = System.getenv("CONCEPT_SKETCH_TEST_INDEX") != null
            ? System.getenv("CONCEPT_SKETCH_TEST_INDEX")
            : System.getProperty("conceptSketch.testIndex", "D:\\corpora_philsci\\bi");

    @Test
    @Disabled("Requires live BlackLab index")
    void findCollocations_missingLemma_throwsIllegalArgumentException() throws Exception {
        try (BlackLabQueryExecutor executor = new BlackLabQueryExecutor(INDEX_PATH)) {
            // A cqlPattern that is neither a placeholder (%s) nor starts with '[' is invalid.
            assertThrows(IllegalArgumentException.class, () ->
                executor.findCollocations("house", "INVALID_PATTERN_FORMAT", 0.0, 10));
        }
    }

    @Test
    @Disabled("Requires live BlackLab index")
    void findCollocations_validLemmaAndPattern_returnsNonNullList() throws Exception {
        try (BlackLabQueryExecutor executor = new BlackLabQueryExecutor(INDEX_PATH)) {
            var results = executor.findCollocations("house", "[xpos=\"JJ.*\"]", 0.0, 10);
            assertNotNull(results, "Result list must not be null");
        }
    }

    @Test
    @Disabled("Requires live BlackLab index")
    void getTotalFrequency_knownLemma_returnsPositiveCount() throws Exception {
        try (BlackLabQueryExecutor executor = new BlackLabQueryExecutor(INDEX_PATH)) {
            long freq = executor.getTotalFrequency("theory");
            assertTrue(freq > 0, "Frequency of a common lemma should be positive");
        }
    }
}

