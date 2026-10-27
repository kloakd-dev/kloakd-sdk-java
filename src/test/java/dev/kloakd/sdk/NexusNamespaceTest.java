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
    private Routes routes;

    @BeforeEach
    void setUp() throws Exception {
        server = startServer();
        routes = routes(server);
        client = client(server);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void analyze_sendsHtml() {
        routes.json("POST nexus/analyze", 200, Map.of(
                "url", "https://example.com", "page_type", "listing", "confidence", 0.9,
                "strategy", Map.of("type", "css"), "perception_id", "p1",
                "artifact", Map.of("artifact_id", "art-n")));

        var result = client.nexus().analyze("https://example.com", "<html/>");

        assertEquals("listing", result.pageType());
        assertEquals(0.9, result.confidence());
        assertEquals("art-n", result.artifactId());
        assertEquals("<html/>", routes.bodies.get("POST nexus/analyze").get("html"));
    }

    @Test
    void synthesize_sendsUrlHtmlGoal() {
        routes.json("POST nexus/synthesize", 200, Map.of(
                "url", "https://example.com", "goal", "prices", "program", "extract(...)",
                "strategy_id", "st-1", "synthesis_time_ms", 40));

        var result = client.nexus().synthesize("https://example.com", "<html/>", "prices");

        assertEquals("st-1", result.strategyId());
        assertEquals("extract(...)", result.program());
        assertEquals(Map.of("url", "https://example.com", "html", "<html/>", "goal", "prices"),
                routes.bodies.get("POST nexus/synthesize"));
    }

    @Test
    void verifyExecuteKnowledge_readTheApiFields() {
        routes.json("POST nexus/verify", 200, Map.of("strategy_id", "st-1", "verified", true,
                "safety_score", 0.95, "violations", List.of()));
        routes.json("POST nexus/execute", 200, Map.of("url", "https://example.com", "strategy_id", "st-1",
                "success", true, "records", List.of(Map.of("a", 1)), "duration_ms", 12));
        routes.json("POST nexus/knowledge", 200, Map.of("execution_result_id", "ex-1",
                "learned_concepts", List.of(), "patterns", List.of(Map.of()), "total_patterns", 1));

        assertTrue(client.nexus().verify("st-1").verified());
        var exec = client.nexus().execute("st-1", "https://example.com");
        assertEquals(1, exec.records().size());
        assertEquals(12, exec.durationMs());
        assertEquals(1, client.nexus().knowledge("ex-1").totalPatterns());
    }
}
