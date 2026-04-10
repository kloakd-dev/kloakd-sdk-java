package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.*;
import dev.kloakd.sdk.http.HttpTransport;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.net.http.HttpClient;
import java.time.Duration;

import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpTransportTest {

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
    void request_sendsAuthHeaders() throws Exception {
        enqueue(server, 200, Map.of());
        client.evadr().fetch("https://example.com");

        var req = server.takeRequest();
        assertEquals("Bearer " + TEST_API_KEY, req.getHeader("Authorization"));
        assertEquals(TEST_ORG_ID, req.getHeader("X-Kloakd-Organization"));
        assertTrue(req.getHeader("X-Kloakd-SDK").startsWith("java/"));
    }

    @Test
    void request_urlContainsOrgPrefix() throws Exception {
        enqueue(server, 200, Map.of());
        client.evadr().fetch("https://example.com");

        var req = server.takeRequest();
        assertTrue(req.getPath().contains("/api/v1/organizations/" + TEST_ORG_ID + "/"));
    }

    @Test
    void request_401_throwsAuthenticationException() {
        enqueue(server, 401, Map.of("detail", "invalid key"));
        assertThrows(AuthenticationException.class, () ->
                client.evadr().fetch("https://example.com"));
    }

    @Test
    void request_403_throwsNotEntitledException() {
        enqueue(server, 403, Map.of("detail", "no plan", "module", "evadr"));
        var ex = assertThrows(NotEntitledException.class, () ->
                client.evadr().fetch("https://example.com"));
        assertEquals("evadr", ex.getModule());
    }

    @Test
    void request_429_throwsRateLimitException() {
        enqueue(server, 429, Map.of("detail", "slow down", "retry_after", 30, "reset_at", "2026-04-10T00:00:00Z"));
        var ex = assertThrows(RateLimitException.class, () ->
                client.evadr().fetch("https://example.com"));
        assertEquals(30, ex.getRetryAfter());
        assertEquals("2026-04-10T00:00:00Z", ex.getResetAt());
    }

    @Test
    void request_502_throwsUpstreamException() {
        enqueue(server, 502, Map.of("detail", "upstream failed"));
        assertThrows(UpstreamException.class, () ->
                client.evadr().fetch("https://example.com"));
    }

    @Test
    void request_500_throwsApiException() {
        enqueue(server, 500, Map.of("detail", "server error"));
        var ex = assertThrows(ApiException.class, () ->
                client.evadr().fetch("https://example.com"));
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void request_retryExhausted_throwsLastException() throws Exception {
        // 3 failures queued (1 initial + 2 retries)
        enqueue(server, 500, Map.of("detail", "err"));
        enqueue(server, 500, Map.of("detail", "err"));
        enqueue(server, 500, Map.of("detail", "err"));

        // Use a no-sleep transport subclass so the test completes instantly
        int[] callCount = {0};
        var noSleepTransport = new dev.kloakd.sdk.http.HttpTransport(
                TEST_API_KEY, TEST_ORG_ID, server.url("/").toString(),
                Duration.ofSeconds(5), 2, null) {
            @Override protected void sleep(long ms) { /* no-op: skip backoff in tests */ }
        };

        assertThrows(ApiException.class, () ->
                noSleepTransport.post("evadr/fetch",
                        Map.of("url", "https://example.com")));

        assertEquals(3, server.getRequestCount());
    }

    @Test
    void backoffMs_respectsRetryAfterHeader() {
        long ms = HttpTransport.backoffMs(0, "5", null);
        assertEquals(5000L, ms);
    }

    @Test
    void backoffMs_respectsBodyField() {
        long ms = HttpTransport.backoffMs(0, null, Map.of("retry_after", 10.0));
        assertEquals(10000L, ms);
    }

    @Test
    void backoffMs_exponential() {
        long d0 = HttpTransport.backoffMs(0, null, Map.of());
        long d1 = HttpTransport.backoffMs(1, null, Map.of());
        assertTrue(d1 > d0, "expected d1 > d0 but got d0=" + d0 + " d1=" + d1);
    }

    @Test
    void backoffMs_cappedAt60s() {
        long ms = HttpTransport.backoffMs(100, null, Map.of());
        assertEquals(60_000L, ms);
    }

    @Test
    void isRetryable_correctCodes() {
        for (int code : new int[]{429, 500, 502, 503, 504}) {
            assertTrue(HttpTransport.isRetryable(code), code + " should be retryable");
        }
        for (int code : new int[]{200, 400, 401, 403, 404}) {
            assertFalse(HttpTransport.isRetryable(code), code + " should NOT be retryable");
        }
    }

    @Test
    void raiseForStatus_nullOnSuccess() {
        assertNull(HttpTransport.raiseForStatus(200, Map.of()));
        assertNull(HttpTransport.raiseForStatus(201, Map.of()));
    }
}
