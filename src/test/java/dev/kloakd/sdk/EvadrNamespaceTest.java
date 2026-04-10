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
    void fetchAsync_returnsCompletableFuture() throws Exception {
        enqueue(server, 200, Map.of(
                "success", true, "url", "https://example.com",
                "status_code", 200, "tier_used", 1, "anti_bot_bypassed", false));

        var future = client.evadr().fetchAsync("https://example.com");
        var result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(result.success());
    }

    @Test
    void fetchStream_yieldsEvents() {
        enqueueSse(server,
                Map.of("type", "tier_attempt", "tier", 1, "metadata", Map.of()),
                Map.of("type", "tier_success", "tier", 1, "vendor", "cloudflare", "metadata", Map.of()),
                Map.of("type", "complete", "metadata", Map.of()));

        var events = client.evadr().fetchStream("https://example.com").toList();

        assertEquals(3, events.size());
        assertEquals("tier_attempt", events.get(0).type());
        assertEquals("cloudflare", events.get(1).vendor());
        assertEquals("complete", events.get(2).type());
    }

    @Test
    void fetchStream_withForceBrowser_sendsBody() throws Exception {
        enqueueSse(server, Map.of("type", "done", "metadata", Map.of()));
        client.evadr().fetchStream("https://example.com", true).toList();

        var body = requestBody(server.takeRequest());
        assertEquals(Boolean.TRUE, body.get("force_browser"));
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
