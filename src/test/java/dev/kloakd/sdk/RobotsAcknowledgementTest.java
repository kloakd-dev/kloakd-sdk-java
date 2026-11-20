package dev.kloakd.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kloakd.sdk.errors.*;
import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The robots.txt acknowledgement (kobe-scraper core/compliance/robots.py,
 * GAP-DOC-35): {@code robots_acknowledged_hosts} on every route that takes it,
 * the 409 {@code ROBOTS_DISALLOWED} stop and the 503 {@code ROBOTS_CHECK_UNAVAILABLE}.
 */
class RobotsAcknowledgementTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> HOSTS = List.of("www.reddit.com");
    private static final String ACK = "robots_acknowledged_hosts";

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

    private Kloakd retrying() {
        return Kloakd.builder().apiKey(TEST_API_KEY).organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString()).maxRetries(2).timeout(Duration.ofSeconds(5)).build();
    }

    /** The body of a stop, as core/compliance/robots.py stop_detail writes it. */
    private static Map<String, Object> stop() {
        var d = new HashMap<String, Object>();
        d.put("error", "ROBOTS_DISALLOWED");
        d.put("code", "ROBOTS_DISALLOWED");
        d.put("reason", "disallowed");
        d.put("host", "www.reddit.com");
        d.put("url", "https://www.reddit.com/r/Python/");
        d.put("rule", "Disallow: /");
        d.put("message", "www.reddit.com does not allow this in its robots.txt");
        d.put("acknowledge_field", ACK);
        d.put("terms_version", "draft-2026-11-19");
        return Map.of("detail", d);
    }

    private static MockResponse unavailable(String reason) throws Exception {
        var d = new HashMap<String, Object>();
        if (reason != null) d.put("reason", reason);
        d.put("error", "ROBOTS_CHECK_UNAVAILABLE");
        d.put("code", "ROBOTS_CHECK_UNAVAILABLE");
        d.put("host", "www.gov.uk");
        d.put("message", "robots.txt could not be read");
        d.put("retry_after_s", 30);
        return new MockResponse().setResponseCode(503).addHeader("Content-Type", "application/json")
                .addHeader("Retry-After", "0.01").setBody(MAPPER.writeValueAsString(Map.of("detail", d)));
    }

    private static final Map<String, Object> FETCHED = Map.of("success", true, "url", "https://www.reddit.com/r/Python/",
            "status_code", 200, "html", "<html></html>");

    // ── request field ────────────────────────────────────────────────────────

    @Test
    void fetch_sendsAcknowledgedHosts() {
        routes.json("POST evadr/fetch", 200, FETCHED);
        client.evadr().fetch("https://www.reddit.com/r/Python/",
                FetchOptions.builder().robotsAcknowledgedHosts("www.reddit.com").build());
        assertEquals(HOSTS, routes.bodies.get("POST evadr/fetch").get(ACK));
    }

    @Test
    void fetch_withoutHosts_sendsNoField() {
        routes.json("POST evadr/fetch", 200, FETCHED);
        client.evadr().fetch("https://example.com/");
        client.evadr().fetch("https://example.com/", FetchOptions.builder().robotsAcknowledgedHosts(List.of()).build());
        assertFalse(routes.rawBodies.get("POST evadr/fetch").contains(ACK));
    }

    @Test
    void startFetchJob_sendsAcknowledgedHosts() {
        routes.json("POST evadr/fetch/async", 202, Map.of("job_id", "j1"));
        client.evadr().startFetchJob("https://www.reddit.com/", FetchOptions.builder().robotsAcknowledgedHosts(HOSTS).build());
        assertEquals(HOSTS, routes.bodies.get("POST evadr/fetch/async").get(ACK));
    }

    @Test
    void scanAndProfile_sendAcknowledgedHosts() {
        routes.json("POST evadr/scan", 200, Map.of("url", "x"));
        routes.json("POST evadr/profile", 200, Map.of("url", "x"));
        client.evadr().scan("https://www.reddit.com/", HOSTS);
        client.evadr().profile("https://www.reddit.com/", HOSTS);
        assertEquals(Map.of("url", "https://www.reddit.com/", ACK, HOSTS), routes.bodies.get("POST evadr/scan"));
        assertEquals(Map.of("url", "https://www.reddit.com/", ACK, HOSTS), routes.bodies.get("POST evadr/profile"));
        client.evadr().scan("https://example.com/");
        assertEquals(Map.of("url", "https://example.com/"), routes.bodies.get("POST evadr/scan"));
    }

    @Test
    void extract_sendsAcknowledgedHosts() {
        routes.json("POST kolektr/extract", 200, Map.of("success", true, "records", List.of()));
        client.kolektr().extract("https://www.reddit.com/r/Python/",
                ExtractOptions.builder().robotsAcknowledgedHosts(HOSTS).build());
        assertEquals(HOSTS, routes.bodies.get("POST kolektr/extract").get(ACK));
    }

    @Test
    void extractAllApiData_sendsEachHostAsAQueryParameter() throws Exception {
        routes.json("POST kolektr/api-data/https%3A%2F%2Fapi.e.com%2Fv1/extract-all", 200, Map.of("job_id", "j1"));
        client.kolektr().extractAllApiData("https://api.e.com/v1", "h1", 5, List.of("api.e.com", "cdn.e.com"));
        var req = server.takeRequest();
        String query = req.getRequestUrl().query();
        assertEquals("endpoint_hash=h1&max_records=5&robots_acknowledged_hosts=api.e.com&robots_acknowledged_hosts=cdn.e.com",
                query);
        assertEquals(List.of("api.e.com", "cdn.e.com"), req.getRequestUrl().queryParameterValues(ACK));
    }

    @Test
    void extractAllApiData_withoutHosts_isUnchanged() throws Exception {
        routes.json("POST kolektr/api-data/https%3A%2F%2Fapi.e.com%2Fv1/extract-all", 200, Map.of("job_id", "j1"));
        client.kolektr().extractAllApiData("https://api.e.com/v1", "h1", null);
        assertEquals("endpoint_hash=h1", server.takeRequest().getRequestUrl().query());
    }

    @Test
    void crawlRoutes_sendAcknowledgedHosts() {
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c1"));
        routes.json("POST webgrph/crawl/estimate", 200, Map.of());
        routes.json("POST webgrph/crawl-extract", 202, Map.of("success", true, "job_id", "j1"));
        routes.json("POST webgrph/crawl-extract/estimate", 200, Map.of());
        var opts = CrawlOptions.builder().robotsAcknowledgedHosts(HOSTS).buildAsSet();

        client.webgrph().startCrawl("https://www.reddit.com/", opts);
        client.webgrph().estimateCrawl("https://www.reddit.com/", opts);
        client.webgrph().startCrawlExtract("https://www.reddit.com/", opts, null);
        client.webgrph().estimateCrawlExtract("https://www.reddit.com/", opts, null);

        for (String route : List.of("POST webgrph/crawl", "POST webgrph/crawl/estimate", "POST webgrph/crawl-extract",
                "POST webgrph/crawl-extract/estimate")) {
            assertEquals(Map.of("url", "https://www.reddit.com/", ACK, HOSTS), routes.bodies.get(route), route);
        }
    }

    @Test
    void continueRoutes_sendABodyWithTheHostsAlone() {
        routes.json("POST webgrph/crawl/c1/continue", 202, Map.of("success", true, "crawl_id", "c1", "status", "running"));
        routes.json("POST webgrph/crawl-extract/j1/continue", 202, Map.of("success", true, "job_id", "j1"));
        var opts = CrawlContinueOptions.builder().robotsAcknowledgedHosts("www.reddit.com").build();
        assertFalse(opts.isEmpty());

        client.webgrph().continueCrawl("c1", opts);
        client.webgrph().continueCrawlExtract("j1", opts);

        assertEquals(Map.of(ACK, HOSTS), routes.bodies.get("POST webgrph/crawl/c1/continue"));
        assertEquals(Map.of(ACK, HOSTS), routes.bodies.get("POST webgrph/crawl-extract/j1/continue"));
        // No option: still no body.
        client.webgrph().continueCrawl("c1", CrawlContinueOptions.builder().robotsAcknowledgedHosts(List.of()).build());
        assertEquals("", routes.rawBodies.get("POST webgrph/crawl/c1/continue"));
    }

    @Test
    void mapReaderViewSyncChatUnifiedBatch_sendAcknowledgedHosts() {
        routes.json("POST webgrph/map", 200, Map.of("url", "x"));
        routes.json("POST webgrph/reader-view", 200, Map.of());
        routes.json("POST skanyr/sync", 200, Map.of());
        routes.sse("POST parlyr/chat", Map.of("type", "text", "content", "hi"), Map.of("type", "end"));
        routes.json("POST scraping/unified", 200, Map.of("success", true));
        routes.json("POST batch-jobs", 201, Map.of("id", "b1", "status", "pending"));

        client.webgrph().mapSite("https://www.reddit.com/", 10, HOSTS);
        client.webgrph().readerView("https://www.reddit.com/", null, null, HOSTS);
        client.skanyr().sync("site-1", null, null, HOSTS);
        client.parlyr().chat("s1", "scrape reddit", HOSTS);
        client.unifiedScrape("https://www.reddit.com/", UnifiedScrapeOptions.builder().robotsAcknowledgedHosts(HOSTS).build());
        client.createBatchJob(List.of("https://www.reddit.com/"), BatchJobOptions.builder().robotsAcknowledgedHosts(HOSTS).build());

        for (String route : List.of("POST webgrph/map", "POST webgrph/reader-view", "POST skanyr/sync",
                "POST parlyr/chat", "POST scraping/unified", "POST batch-jobs")) {
            assertEquals(HOSTS, routes.bodies.get(route).get(ACK), route);
        }
    }

    @Test
    void olderConstructorsSendNoField() {
        var fetch = new FetchOptions(false, false, null, null, null, null, null);
        var crawl = new CrawlOptions(0, 0, null, false, null, null, null, null);
        var cont = new CrawlContinueOptions(5, null, null);
        assertNull(fetch.robotsAcknowledgedHosts());
        assertNull(crawl.robotsAcknowledgedHosts());
        assertEquals(Map.of("max_pages", 5), cont.toBody());
    }

    // ── 409 ROBOTS_DISALLOWED ────────────────────────────────────────────────

    @Test
    void stop409_isRobotsDisallowedAndNotRetried() {
        routes.json("POST evadr/fetch", 409, stop());
        var retrying = retrying();

        var ex = assertThrows(RobotsDisallowedException.class, () -> retrying.evadr().fetch("https://www.reddit.com/r/Python/"));
        assertEquals(409, ex.getStatusCode());
        assertEquals("ROBOTS_DISALLOWED", ex.getCode());
        assertEquals("disallowed", ex.getReason());
        assertEquals("www.reddit.com", ex.getHost());
        assertEquals("Disallow: /", ex.getRule());
        assertEquals("https://www.reddit.com/r/Python/", ex.getUrl());
        assertNull(ex.getRedirectedFrom());
        assertEquals("draft-2026-11-19", ex.getTermsVersion());
        assertEquals(ACK, ex.getAcknowledgeField());
        assertFalse(ex.isUnreadable());
        assertEquals("www.reddit.com does not allow this in its robots.txt", ex.getMessage());
        assertInstanceOf(ApiException.class, ex);
        assertEquals(1, routes.seen.size(), "a robots.txt stop must not be retried");
    }

    @Test
    void stop409_thenAcknowledged_goesPast() {
        routes.json("POST evadr/fetch", 409, stop());
        routes.json("POST evadr/fetch", 200, FETCHED);

        var ex = assertThrows(RobotsDisallowedException.class, () -> client.evadr().fetch("https://www.reddit.com/r/Python/"));
        assertFalse(routes.rawBodies.get("POST evadr/fetch").contains(ACK), "the SDK never acknowledges on its own");
        var result = client.evadr().fetch("https://www.reddit.com/r/Python/",
                FetchOptions.builder().robotsAcknowledgedHosts(ex.getHost()).build());
        assertTrue(result.success());
        assertEquals(HOSTS, routes.bodies.get("POST evadr/fetch").get(ACK));
    }

    @Test
    void stop409_unreadableAndRedirected() {
        var d = new HashMap<String, Object>(stop());
        @SuppressWarnings("unchecked")
        var detail = new HashMap<String, Object>((Map<String, Object>) d.get("detail"));
        detail.put("reason", "unreadable");
        detail.put("rule", null);
        detail.put("redirected_from", "https://reddit.com/r/Python/");
        routes.json("POST webgrph/crawl", 409, Map.of("detail", detail));

        var ex = assertThrows(RobotsDisallowedException.class, () -> client.webgrph().startCrawl("https://reddit.com/r/Python/",
                CrawlOptions.builder().buildAsSet()));
        assertTrue(ex.isUnreadable());
        assertNull(ex.getRule());
        assertEquals("https://reddit.com/r/Python/", ex.getRedirectedFrom());
    }

    @Test
    void stop409_fromDetail_readsAChatErrorEvent() {
        @SuppressWarnings("unchecked")
        var detail = (Map<String, Object>) stop().get("detail");
        var ex = RobotsDisallowedException.fromDetail(detail, null);
        assertNotNull(ex);
        assertEquals("www.reddit.com", ex.getHost());
        assertNull(RobotsDisallowedException.fromDetail(Map.of("error", "concurrent_limit"), null));
        assertNull(RobotsDisallowedException.fromDetail(null, null));
    }

    @Test
    void concurrentLimit409_isUnchanged() {
        routes.json("POST evadr/fetch", 409, Map.of("detail", Map.of("error", "concurrent_limit",
                "message", "all slots in use", "concurrent_limit", 5)));
        var ex = assertThrows(ConcurrencyLimitException.class, () -> client.evadr().fetch("https://example.com"));
        assertEquals(5, ex.getConcurrentLimit());
    }

    @Test
    void other409_staysApiException() {
        routes.json("POST webgrph/crawl/c1/continue", 409, Map.of("detail", Map.of("error", "not_continuable",
                "message", "no")));
        var ex = assertThrows(ApiException.class, () -> client.webgrph().continueCrawl("c1"));
        assertFalse(ex instanceof RobotsDisallowedException);
        assertEquals("not_continuable", ex.getCode());
    }

    // ── 503 ROBOTS_CHECK_UNAVAILABLE ─────────────────────────────────────────

    @Test
    void unavailable503_isRetriedOnPost() throws Exception {
        routes.add("POST evadr/fetch", unavailable(null));
        routes.json("POST evadr/fetch", 200, FETCHED);

        var result = retrying().evadr().fetch("https://www.gov.uk/");
        assertTrue(result.success());
        assertEquals(2, routes.seen.size());
    }

    @Test
    void unavailable503_readsReasonHostAndRetryAfter() throws Exception {
        routes.add("POST evadr/fetch", unavailable(null));
        var ex = assertThrows(RobotsCheckUnavailableException.class, () -> client.evadr().fetch("https://www.gov.uk/"));
        assertFalse(ex instanceof RobotsExitsRefusedException);
        assertEquals(503, ex.getStatusCode());
        assertEquals("ROBOTS_CHECK_UNAVAILABLE", ex.getCode());
        assertNull(ex.getReason());
        assertEquals("www.gov.uk", ex.getHost());
        assertEquals(30, ex.getRetryAfter(), "Retry-After under a second: the body's retry_after_s");
        assertTrue(HttpTransport.isRetryable("POST", ex));
    }

    @Test
    void exitsRefused503_isDistinctAndNotRetried() throws Exception {
        routes.add("POST evadr/fetch", unavailable("exits_refused"));
        var ex = assertThrows(RobotsExitsRefusedException.class, () -> retrying().evadr().fetch("https://www.gov.uk/"));
        assertEquals("exits_refused", ex.getReason());
        assertEquals("www.gov.uk", ex.getHost());
        assertInstanceOf(RobotsCheckUnavailableException.class, ex);
        assertEquals(1, routes.seen.size(), "every exit refused: not retried");
        assertFalse(HttpTransport.isRetryable("GET", ex));
    }

    @Test
    void retryAfterHeaderIsRead() {
        var ex = HttpTransport.raiseForStatus(503, Map.of("detail", Map.of("error", "ROBOTS_CHECK_UNAVAILABLE",
                "message", "m", "retry_after_s", 30)), "12");
        assertEquals(12, ((RobotsCheckUnavailableException) ex).getRetryAfter());
    }
}
