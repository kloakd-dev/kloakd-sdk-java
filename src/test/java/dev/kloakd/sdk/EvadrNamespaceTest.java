package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.AuthenticationException;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class EvadrNamespaceTest {

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
    void fetch_parsesResult() throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("success", true);
        body.put("url", "https://example.com");
        body.put("status_code", 200);
        body.put("tier_used", 2);
        body.put("html", "<html/>");
        body.put("vendor_detected", "cloudflare");
        body.put("anti_bot_bypassed", true);
        body.put("artifact_id", "art-001");
        body.put("error", null);
        enqueue(server, 200, body);

        var result = client.evadr().fetch("https://example.com");

        assertTrue(result.success());
        assertEquals(2, result.tierUsed());
        assertTrue(result.antiBotBypassed());
        assertEquals("art-001", result.artifactId());
        assertEquals("cloudflare", result.vendorDetected());
    }

    @Test
    void fetch_sendsCorrectBody() throws Exception {
        enqueue(server, 200, Map.of("success", true));
        client.evadr().fetch("https://example.com", true, true, "sess-001");

        var body = requestBody(server.takeRequest());
        assertEquals("https://example.com", body.get("url"));
        assertEquals(Boolean.TRUE, body.get("force_browser"));
        assertEquals(Boolean.TRUE, body.get("use_proxy"));
        assertEquals("sess-001", body.get("session_artifact_id"));
    }

    @Test
    void fetch_401_throwsAuthenticationException() {
        enqueue(server, 401, Map.of("detail", "bad key"));
        assertThrows(AuthenticationException.class, () ->
                client.evadr().fetch("https://example.com"));
    }

    @Test
    void fetchAsync_startsAJobAndCompletesWithItsResult() throws Exception {
        var routes = routes(server);
        routes.json("POST evadr/fetch/async", 202, Map.of("job_id", "j1", "status", "pending", "message", "queued"));
        routes.json("GET evadr/jobs/j1", 200, Map.of("job_id", "j1", "status", "completed", "result", Map.of(
                "success", true, "url", "https://example.com", "status_code", 200, "tier_used", 1)));

        var result = client.evadr().fetchAsync("https://example.com", dev.kloakd.sdk.models.FetchOptions.builder()
                .webhookUrl("https://hooks.example.com/k").pollInterval(java.time.Duration.ofMillis(1))
                .pollTimeout(java.time.Duration.ofSeconds(5)).build()).get(5, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertEquals(1, result.tierUsed());
        assertEquals("https://hooks.example.com/k", routes.bodies.get("POST evadr/fetch/async").get("webhook_url"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchStream_startsAJobAndStreamsItsEvents() {
        var routes = routes(server);
        routes.json("POST evadr/fetch/async", 202, Map.of("job_id", "j2", "status", "pending", "message", "queued"));
        routes.sse("GET evadr/jobs/j2/events",
                Map.of("type", "STATUS", "job_id", "j2", "status", "running"),
                Map.of("type", "COMPLETE", "job_id", "j2", "tier_used", 2, "artifact_id", "art-9"),
                Map.of("pipeline_id", "j2", "event_type", "pipeline_error", "stage_id", "fetch", "status", "failed",
                        "message", "blocked", "sequence", 4, "metadata", Map.of(),
                        "error", Map.of("error_code", "bot", "user_message", "Blocked by the site")));

        var events = client.evadr().fetchStream("https://example.com", true).toList();

        assertEquals(3, events.size());
        assertEquals("running", events.get(0).status());
        assertEquals(2, events.get(1).tierUsed());
        assertEquals("art-9", events.get(1).artifactId());
        assertNull(events.get(1).pipelineEvent());
        assertEquals("pipeline_error", events.get(2).type());
        assertEquals("Blocked by the site", events.get(2).error());
        assertEquals(4, events.get(2).pipelineEvent().sequence());
        assertEquals("fetch", events.get(2).pipelineEvent().stageId());
        assertEquals(Boolean.TRUE, routes.bodies.get("POST evadr/fetch/async").get("force_browser"));
    }

    @Test
    void analyze_parsesResult() {
        enqueue(server, 200, Map.of(
                "blocked", true, "vendor", "akamai",
                "confidence", 0.92, "recommended_actions", List.of("use_proxy")));

        var result = client.evadr().analyze("https://example.com");

        assertTrue(result.blocked());
        assertEquals("akamai", result.vendor());
        assertEquals(0.92, result.confidence(), 0.001);
        assertEquals(List.of("use_proxy"), result.recommendedActions());
    }

    @Test
    void storeProxy_sendsCorrectBody() throws Exception {
        enqueue(server, 200, Map.of());
        client.evadr().storeProxy("my-proxy", "http://proxy.example.com:8080");

        var body = requestBody(server.takeRequest());
        assertEquals("my-proxy", body.get("name"));
        assertEquals("http://proxy.example.com:8080", body.get("proxy_url"));
    }
}
