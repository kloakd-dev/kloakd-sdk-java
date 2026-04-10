package dev.kloakd.sdk;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusNamespaceTest {

    private MockWebServer server;
    private Kloakd client;

    @BeforeEach
    void setUp() throws Exception {
        server = startServer();
        client = client(server);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void analyze_parsesResult() {
        enqueue(server, 200, Map.of(
                "perception_id", "perc-001", "strategy", Map.of("type", "css"),
                "page_type", "listing", "complexity_level", "medium",
                "artifact_id", "art-n-001", "duration_ms", 120));

        var result = client.nexus().analyze("https://example.com");

        assertEquals("perc-001", result.perceptionId());
        assertEquals("listing", result.pageType());
        assertEquals("medium", result.complexityLevel());
        assertEquals(120, result.durationMs());
    }

    @Test
    void analyze_withOptions_sendsBody() throws Exception {
        enqueue(server, 200, Map.of(
                "perception_id", "perc-002", "strategy", Map.of(),
                "page_type", "detail", "complexity_level", "low", "duration_ms", 80));
        client.nexus().analyze("https://example.com", "<html/>", Map.of("max_selectors", 10));

        var body = requestBody(server.takeRequest());
        assertEquals("<html/>", body.get("html"));
        assertNotNull(body.get("constraints"));
    }

    @Test
    void synthesize_parsesResult() {
        enqueue(server, 200, Map.of(
                "strategy_id", "strat-001", "strategy_name", "css_extractor",
                "generated_code", "return doc.q('h1')", "synthesis_time_ms", 250));

        var result = client.nexus().synthesize("perc-001");

        assertEquals("strat-001", result.strategyId());
        assertEquals(250, result.synthesisTimeMs());
    }

    @Test
    void verify_parsesResult() {
        enqueue(server, 200, Map.of(
                "verification_result_id", "v-001", "is_safe", true,
                "risk_score", 0.1, "safety_score", 0.95,
                "violations", List.of(), "duration_ms", 45));

        var result = client.nexus().verify("strat-001");

        assertTrue(result.isSafe());
        assertEquals(0.1, result.riskScore(), 0.001);
        assertEquals(0, result.violations().size());
    }

    @Test
    void execute_parsesResult() {
        enqueue(server, 200, Map.of(
                "execution_result_id", "exec-001", "success", true,
                "records", List.of(Map.of("title", "Book A")),
                "duration_ms", 800));

        var result = client.nexus().execute("strat-001", "https://example.com");

        assertEquals("exec-001", result.executionResultId());
        assertEquals(1, result.records().size());
        assertEquals("Book A", result.records().get(0).get("title"));
    }

    @Test
    void knowledge_parsesResult() {
        enqueue(server, 200, Map.of(
                "learned_concepts", List.of(Map.of("name", "product_listing")),
                "learned_patterns", List.of(Map.of("selector", "h3 a")),
                "duration_ms", 60));

        var result = client.nexus().knowledge("exec-001");

        assertEquals(1, result.learnedConcepts().size());
        assertEquals(1, result.learnedPatterns().size());
    }
}
