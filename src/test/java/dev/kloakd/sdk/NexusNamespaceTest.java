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
    void verify_readsTheWholeResponse() {
        routes.json("POST nexus/verify", 200, Map.of("strategy_id", "st-1", "verified", false,
                "safety_score", 0.4, "violations", List.of("unbounded loop"), "status", "unsafe",
                "risk_score", 0.6, "verification_result_id", "vr-1", "duration_ms", 31,
                "safety_checks", List.of(Map.of("property", "termination", "passed", false,
                        "details", "loop has no bound"))));

        var result = client.nexus().verify("st-1");

        assertFalse(result.verified());
        assertEquals("unsafe", result.status());
        assertEquals(0.4, result.safetyScore());
        assertEquals(0.6, result.riskScore());
        assertEquals(List.of("unbounded loop"), result.violations());
        assertEquals("vr-1", result.verificationResultId());
        assertEquals(31, result.durationMs());
        assertEquals("termination", result.safetyChecks().get(0).get("property"));
        assertEquals(false, result.safetyChecks().get(0).get("passed"));
        assertEquals(Map.of("strategy_id", "st-1"), routes.bodies.get("POST nexus/verify"));
    }

    @Test
    void verify_aResponseWithOnlyThe060FieldsStillReads() {
        routes.json("POST nexus/verify", 200, Map.of("strategy_id", "st-1", "verified", true,
                "safety_score", 0.95, "violations", List.of()));

        var result = client.nexus().verify("st-1");

        assertTrue(result.verified());
        assertNull(result.status());
        assertEquals(List.of(), result.safetyChecks());
        assertEquals(new dev.kloakd.sdk.models.NexusVerifyResult("st-1", true, 0.95, List.of()), result);
    }

    @Test
    void executeAndKnowledgeAreRemoved() {
        // POST nexus/execute left the API; nexus/knowledge is not in the public reference.
        var methods = java.util.Arrays.stream(dev.kloakd.sdk.modules.NexusNamespace.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList();
        assertFalse(methods.contains("execute"));
        assertFalse(methods.contains("knowledge"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.kloakd.sdk.models.NexusExecuteResult"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.kloakd.sdk.models.NexusKnowledgeResult"));
    }
}
