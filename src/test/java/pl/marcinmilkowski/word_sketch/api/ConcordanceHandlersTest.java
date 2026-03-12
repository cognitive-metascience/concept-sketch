package pl.marcinmilkowski.word_sketch.api;

import org.junit.jupiter.api.Test;
import pl.marcinmilkowski.word_sketch.config.GrammarConfigHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validation tests for {@link ConcordanceHandlers} — missing required query parameters.
 */
class ConcordanceHandlersTest {

    @Test
    void handleConcordanceExamples_missingWord1_returns400() throws Exception {
        ConcordanceHandlers handlers = new ConcordanceHandlers(null, GrammarConfigHelper.requireTestConfig());
        TestExchangeFactory.MockExchange ex = new TestExchangeFactory.MockExchange(
                "http://localhost/api/concordance?collocate=house");
        HttpApiUtils.wrapWithErrorHandling(handlers::handleConcordanceExamples, "test").handle(ex);
        assertEquals(400, ex.statusCode);
    }

    @Test
    void handleConcordanceExamples_missingWord2_returns400() throws Exception {
        ConcordanceHandlers handlers = new ConcordanceHandlers(null, GrammarConfigHelper.requireTestConfig());
        TestExchangeFactory.MockExchange ex = new TestExchangeFactory.MockExchange(
                "http://localhost/api/concordance?seed=big");
        HttpApiUtils.wrapWithErrorHandling(handlers::handleConcordanceExamples, "test").handle(ex);
        assertEquals(400, ex.statusCode);
    }

    @Test
    void handleConcordanceExamples_missingBothWords_returns400() throws Exception {
        ConcordanceHandlers handlers = new ConcordanceHandlers(null, GrammarConfigHelper.requireTestConfig());
        TestExchangeFactory.MockExchange ex = new TestExchangeFactory.MockExchange(
                "http://localhost/api/concordance");
        HttpApiUtils.wrapWithErrorHandling(handlers::handleConcordanceExamples, "test").handle(ex);
        assertEquals(400, ex.statusCode);
    }
}
