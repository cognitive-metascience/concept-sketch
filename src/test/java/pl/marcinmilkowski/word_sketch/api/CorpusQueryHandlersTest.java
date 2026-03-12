package pl.marcinmilkowski.word_sketch.api;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CorpusQueryHandlers} request validation:
 * body size limit, invalid JSON, missing/blank query, and pattern complexity.
 */
class CorpusQueryHandlersTest {

    private static TestExchangeFactory.MockPostBodyExchange postExchange(String body) {
        return new TestExchangeFactory.MockPostBodyExchange("http://localhost/api/bcql", body == null ? "" : body);
    }

    private static CorpusQueryHandlers handlers() {
        return new CorpusQueryHandlers(null);
    }

    @Test
    void handleCorpusQuery_bodyTooLarge_returns413() throws Exception {
        String oversizeBody = "x".repeat(65537);
        TestExchangeFactory.MockPostBodyExchange ex = postExchange(oversizeBody);
        HttpApiUtils.wrapWithErrorHandling(handlers()::handleCorpusQuery, "test").handle(ex);
        assertEquals(413, ex.statusCode);
    }

    @Test
    void handleCorpusQuery_invalidJson_returns400() throws Exception {
        TestExchangeFactory.MockPostBodyExchange ex = postExchange("not-json");
        HttpApiUtils.wrapWithErrorHandling(handlers()::handleCorpusQuery, "test").handle(ex);
        assertEquals(400, ex.statusCode);
        JSONObject body = JSON.parseObject(ex.getResponseBodyAsString());
        assertNotNull(body.getString("error"), "Error field must be present");
    }

    @Test
    void handleCorpusQuery_missingQueryField_returns400() throws Exception {
        TestExchangeFactory.MockPostBodyExchange ex = postExchange("{\"top\": 10}");
        HttpApiUtils.wrapWithErrorHandling(handlers()::handleCorpusQuery, "test").handle(ex);
        assertEquals(400, ex.statusCode);
        JSONObject body = JSON.parseObject(ex.getResponseBodyAsString());
        assertTrue(body.getString("error").contains("query"), "Error should mention 'query'");
    }

    @Test
    void handleCorpusQuery_blankQueryField_returns400() throws Exception {
        TestExchangeFactory.MockPostBodyExchange ex = postExchange("{\"query\": \"   \"}");
        HttpApiUtils.wrapWithErrorHandling(handlers()::handleCorpusQuery, "test").handle(ex);
        assertEquals(400, ex.statusCode);
    }

    @Test
    void handleCorpusQuery_bracketDepthExceeded_returns400() throws Exception {
        // Use a simple bracket pattern with no inner quotes so JSON stays valid
        String deepPattern = "[x]".repeat(21);
        TestExchangeFactory.MockPostBodyExchange ex = postExchange("{\"query\": \"" + deepPattern + "\"}");
        HttpApiUtils.wrapWithErrorHandling(handlers()::handleCorpusQuery, "test").handle(ex);
        assertEquals(400, ex.statusCode);
        JSONObject body = JSON.parseObject(ex.getResponseBodyAsString());
        assertTrue(body.getString("error").contains("complex"), "Error should mention pattern complexity");
    }
}
