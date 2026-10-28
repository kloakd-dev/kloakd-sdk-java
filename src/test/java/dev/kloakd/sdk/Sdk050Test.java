package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.*;
import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.CrawlOptions;
import dev.kloakd.sdk.models.CrawlProgressEvent;
import dev.kloakd.sdk.models.ExtractOptions;
import dev.kloakd.sdk.modules.FetchyrNamespace;
import dev.kloakd.sdk.modules.KolektrNamespace;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.*;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/** The 0.5.0 fixes (sdk-0.5.0 spec items 1–18 and the Java extras). */
class Sdk050Test {

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

    /** A transport that records its waits instead of sleeping. */
    private static final class Recording extends HttpTransport {
        final List<Long> waits = new ArrayList<>();
        Recording(MockWebServer server, int retries) {
            super(TEST_API_KEY, TEST_ORG_ID, server.url("/").toString(), Duration.ofSeconds(5), retries, null);
        }
        @Override protected void sleep(long ms) { waits.add(ms); }
    }

    private static Map<String, Object> detail(String code, String message, Map<String, Object> extra) {
        var d = new HashMap<String, Object>(extra);
        d.put("error", code);
        d.put("message", message);
        return Map.of("detail", d);
    }

    // ── 1, 17: timeout and User-Agent ────────────────────────────────────────

    @Test
    void defaultTimeoutIsAboveTheSynchronousWait() {
        assertEquals(Duration.ofSeconds(100), Kloakd.DEFAULT_TIMEOUT);
    }

    @Test
    void sendsTheUserAgent() throws Exception {
        routes.json("GET evadr/vendors", 200, Map.of());
        client.evadr().listVendors();
        var req = server.takeRequest();
        assertEquals("kloakd-sdk-java/0.5.0", req.getHeader("User-Agent"));
        assertEquals("java/0.5.0", req.getHeader("X-Kloakd-SDK"));
    }

    @Test
    void theVersionMatchesThePom() throws Exception {
        assertTrue(Files.readString(Path.of("pom.xml")).contains("<version>" + "0.5.0" + "</version>"));
        assertEquals("kloakd-sdk-java/0.5.0", HttpTransport.USER_AGENT);
    }

    // ── 8: retries ───────────────────────────────────────────────────────────

    @Test
    void aPostIsNotRetriedOn5xx() {
        var t = new Recording(server, 3);
        routes.json("POST evadr/fetch", 503, Map.of("detail", "unavailable"));
        assertThrows(ApiException.class, () -> t.post("evadr/fetch", Map.of("url", "u")));
        assertEquals(1, routes.seen.size());
    }

    @Test
    void aGetIsRetriedOn5xx() {
        var t = new Recording(server, 3);
        routes.json("GET evadr/vendors", 502, Map.of("detail", "bad gateway"));
        routes.json("GET evadr/vendors", 200, Map.of("vendors", List.of()));
        assertEquals(List.of(), t.get("evadr/vendors", null).get("vendors"));
        assertEquals(2, routes.seen.size());
        assertEquals(List.of(1000L), t.waits);
    }

    @Test
    void aDeleteIsRetriedOnANetworkError() {
        var t = new Recording(server, 2);
        routes.add("DELETE evadr/proxies/p1", new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        routes.json("DELETE evadr/proxies/p1", 200, Map.of());
        t.delete("evadr/proxies/p1");
        assertEquals(2, routes.seen.size());
    }

    @Test
    void aPostIsNotRetriedOnANetworkError() {
        var t = new Recording(server, 2);
        routes.add("POST evadr/fetch", new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        var ex = assertThrows(KloakdException.class, () -> t.post("evadr/fetch", Map.of("url", "u")));
        assertTrue(ex.getMessage().startsWith("IO error"), ex.getMessage());
        assertEquals(1, routes.seen.size());
    }

    @Test
    void rateLimitedIsRetriedOnAnyMethodAfterRetryAfter() {
        var t = new Recording(server, 3);
        routes.add("POST evadr/fetch", new MockResponse().setResponseCode(429).addHeader("Retry-After", "2")
                .setBody("{\"detail\":{\"error\":\"rate_limited\",\"message\":\"slow\",\"retry_after\":2,\"reset_at\":\"\"}}"));
        routes.json("POST evadr/fetch", 200, Map.of("success", true));
        assertEquals(true, t.post("evadr/fetch", Map.of("url", "u")).get("success"));
        assertEquals(List.of(2000L), t.waits);
    }

    @Test
    void rateLimitedWaitsUntilResetAtWhenNoRetryAfter() {
        var t = new Recording(server, 1);
        String reset = OffsetDateTime.now().plusSeconds(5).toString();
        routes.json("POST evadr/fetch", 429, detail("rate_limited", "slow", Map.of("reset_at", reset)));
        routes.json("POST evadr/fetch", 200, Map.of("success", true));
        t.post("evadr/fetch", Map.of("url", "u"));
        assertEquals(1, t.waits.size());
        assertTrue(t.waits.get(0) > 3000 && t.waits.get(0) <= 5000, t.waits.toString());
    }

    @Test
    void aRateLimitWaitOverAMinuteIsThrownNotSlept() {
        var t = new Recording(server, 3);
        routes.add("GET evadr/vendors", new MockResponse().setResponseCode(429).addHeader("Retry-After", "3600")
                .setBody("{\"detail\":{\"error\":\"rate_limited\",\"message\":\"slow\",\"retry_after\":3600}}"));
        var ex = assertThrows(RateLimitException.class, () -> t.get("evadr/vendors", null));
        assertEquals(3600, ex.getRetryAfter());
        assertEquals(List.of(), t.waits);
    }

    @Test
    void thePlaygroundQuotaIsNotRetried() {
        var t = new Recording(server, 3);
        routes.add("POST evadr/fetch", new MockResponse().setResponseCode(429).addHeader("Retry-After", "0")
                .setBody("{\"detail\":{\"error\":\"rate_limited\",\"message\":\"Rate limit exceeded. Retry after 0s.\","
                        + "\"retry_after\":0,\"reset_at\":\"\",\"quota_used\":25,\"quota_limit\":25,"
                        + "\"upgrade_url\":\"https://app.kloakd.dev/pricing\"}}"));
        var ex = assertThrows(RateLimitException.class, () -> t.post("evadr/fetch", Map.of("url", "u")));
        assertTrue(ex.isQuotaExhausted());
        assertEquals(25, ex.getQuotaUsed());
        assertEquals(25, ex.getQuotaLimit());
        assertEquals("https://app.kloakd.dev/pricing", ex.getUpgradeUrl());
        assertEquals(0, ex.getRetryAfter());
        assertNull(ex.getResetAt());
        assertEquals(1, routes.seen.size());
    }

    @Test
    void billingUnavailableIsRetriedOnAPost() {
        var t = new Recording(server, 3);
        routes.add("POST evadr/fetch", new MockResponse().setResponseCode(503).addHeader("Retry-After", "30")
                .setBody("{\"detail\":{\"error\":\"billing_unavailable\",\"message\":\"Usage could not be checked\"}}"));
        routes.json("POST evadr/fetch", 200, Map.of("success", true));
        t.post("evadr/fetch", Map.of("url", "u"));
        assertEquals(List.of(30_000L), t.waits);
    }

    @Test
    void gbExhaustedCarriesTheTier() {
        routes.json("POST evadr/fetch", 429, detail("gb_exhausted", "used up",
                Map.of("tier", "pro", "residential_gb_used", 10, "residential_gb_limit", 10, "purchased_gb_balance", 0)));
        var ex = assertThrows(QuotaExceededException.class, () -> client.evadr().fetch("https://example.com"));
        assertEquals("pro", ex.getTier());
    }

    @Test
    void retryPolicyByMethod() {
        var server500 = new ApiException(500, "x");
        assertTrue(HttpTransport.isRetryable("GET", server500));
        assertTrue(HttpTransport.isRetryable("DELETE", server500));
        assertFalse(HttpTransport.isRetryable("POST", server500));
        assertFalse(HttpTransport.isRetryable("PATCH", server500));
        assertTrue(HttpTransport.isRetryable("POST", new RateLimitException("x", 1, null, "rate_limited")));
        assertFalse(HttpTransport.isRetryable("POST", new RateLimitException("x", 0, null, "rate_limited", true, 1, 1, null)));
        assertTrue(HttpTransport.isRetryable("POST", new BillingUnavailableException("x", 30)));
        assertFalse(HttpTransport.isRetryable("GET", new ConcurrencyLimitException("x", 5, 5, "pro")));
    }

    // ── 11: errors ───────────────────────────────────────────────────────────

    @Test
    void concurrentLimitIsTyped() {
        routes.json("POST kolektr/extract", 409, detail("concurrent_limit", "Concurrent crawl limit reached (10 for pro tier).",
                Map.of("tier", "pro", "concurrent_limit", 10, "active_crawls", 10)));
        var ex = assertThrows(ConcurrencyLimitException.class, () -> client.kolektr().extract("https://example.com"));
        assertEquals(10, ex.getConcurrentLimit());
        assertEquals(10, ex.getActiveCrawls());
        assertEquals("pro", ex.getTier());
        assertEquals("concurrent_limit", ex.getCode());
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void tierAccessDeniedReadsTheFeature() {
        routes.json("GET evadr/vendors", 403, detail("tier_access_denied", "Upgrade for this",
                Map.of("current_tier", "free", "required_tier", "pro", "feature", "api_access",
                        "upgrade_url", "https://app.kloakd.dev/pricing")));
        var ex = assertThrows(NotEntitledException.class, () -> client.evadr().listVendors());
        assertEquals("api_access", ex.getFeature());
        assertEquals("free", ex.getCurrentTier());
        assertEquals("pro", ex.getRequiredTier());
        assertEquals("tier_access_denied", ex.getCode());
        assertInstanceOf(ForbiddenException.class, ex);
    }

    @Test
    void anOther403IsForbiddenNotNotEntitled() {
        routes.json("GET skanyr/sites/s1", 403, Map.of("detail", "Not your site"));
        var ex = assertThrows(ForbiddenException.class, () -> client.skanyr().getSite("s1"));
        assertFalse(ex instanceof NotEntitledException);
        assertEquals("Not your site", ex.getMessage());
    }

    @Test
    void aNonJsonErrorBodyIsTheMessage() {
        routes.add("GET evadr/vendors", new MockResponse().setResponseCode(504)
                .addHeader("Content-Type", "text/html").setBody("<html>Gateway Timeout</html>"));
        var ex = assertThrows(ApiException.class, () -> client.evadr().listVendors());
        assertEquals("<html>Gateway Timeout</html>", ex.getMessage());
    }

    @Test
    void a422KeepsTheValidationMessages() {
        routes.json("POST evadr/fetch", 422, Map.of("detail", List.of(
                Map.of("loc", List.of("body", "url"), "msg", "Field required", "type", "missing"),
                Map.of("loc", List.of("body", "use_proxy"), "msg", "Input should be a valid boolean"))));
        var ex = assertThrows(ApiException.class, () -> client.evadr().fetch("https://example.com"));
        assertEquals(422, ex.getStatusCode());
        assertEquals("body.url: Field required; body.use_proxy: Input should be a valid boolean", ex.getMessage());
    }

    // ── 12: hand-off ─────────────────────────────────────────────────────────

    @Test
    void getAndWaitForAHandoff() {
        routes.json("GET handoff/job-7", 200, Map.of("status", "running"));
        routes.json("GET handoff/job-7", 200, Map.of("status", "completed", "result", Map.of("records", List.of())));

        assertEquals("running", client.getHandoff("job-7").get("status"));
        var result = client.waitForHandoff("job-7", Duration.ofMillis(1), Duration.ofSeconds(5));
        assertEquals(Map.of("records", List.of()), result);
    }

    @Test
    void aHandoffTimeoutCarriesTheJobIdToResume() {
        routes.json("GET handoff/job-8", 200, Map.of("status", "running"));
        var ex = assertThrows(JobTimeoutException.class,
                () -> client.waitForHandoff("job-8", Duration.ofMillis(1), Duration.ofMillis(10)));
        assertEquals("job-8", ex.getJobId());
    }

    @Test
    void aCrawlTimeoutCarriesTheCrawlId() {
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c9"));
        routes.json("GET webgrph/crawl/c9", 200, Map.of("status", "crawling"));
        var ex = assertThrows(JobTimeoutException.class, () -> client.webgrph().crawl("https://example.com",
                CrawlOptions.builder().buildAsSet(), Duration.ofMillis(1), Duration.ofMillis(10)));
        assertEquals("c9", ex.getJobId());
        assertEquals("crawl_timeout", ex.getCode());
    }

    @Test
    void waitForCrawlResumes() {
        routes.json("GET webgrph/crawl/c9", 200, Map.of("status", "completed", "url", "https://example.com",
                "total_pages", 0));
        routes.json("GET webgrph/crawl/c9/pages", 200, Map.of("nodes", List.of(), "total", 0));
        var r = client.webgrph().waitForCrawl("c9", Duration.ofMillis(1), Duration.ofSeconds(5));
        assertTrue(r.success());
        assertEquals("https://example.com", r.url());
    }

    // ── 3: extraction schema ─────────────────────────────────────────────────

    @Test
    void theShorthandSchemaBecomesFields() {
        var spec = KolektrNamespace.schemaSpec(new LinkedHashMap<>(Map.of("title", "css:h1")));
        assertEquals(Map.of("fields", List.of(Map.of("name", "title", "selector", "h1"))), spec);
        assertEquals(Map.of("fields", List.of(Map.of("name", "p", "selector", "span.css:x"))),
                KolektrNamespace.schemaSpec(Map.of("p", "span.css:x")), "only a leading css: is stripped");
        assertEquals(Map.of("fields", List.of(Map.of("name", "img", "selector", "img", "attribute", "src"))),
                KolektrNamespace.schemaSpec(Map.of("img", Map.of("selector", "img", "attribute", "src"))));
        Map<String, Object> full = Map.of("fields", List.of(Map.of("name", "t", "selector", "h1")), "pagination", true);
        assertSame(full, KolektrNamespace.schemaSpec(full));
        assertNull(KolektrNamespace.schemaSpec(Map.of()));
        assertNull(KolektrNamespace.schemaSpec(null));
    }

    @Test
    void extractSendsLevelOptionsAndTheConvertedSchema() {
        routes.json("POST kolektr/extract", 200, Map.of("success", true, "url", "u", "method", "css",
                "auth_required", true, "discovery_id", "d1"));
        Map<String, String> schema = Map.of("price", "p.price");
        var r = client.kolektr().extract("https://example.com", ExtractOptions.builder().schema(schema)
                .level("level2").options(Map.of("wait_for", "body")).pipelineId("pl1").build());

        var body = routes.bodies.get("POST kolektr/extract");
        assertEquals("level2", body.get("level"));
        assertEquals(Map.of("wait_for", "body"), body.get("options"));
        assertEquals("pl1", body.get("pipeline_id"));
        assertEquals(Map.of("fields", List.of(Map.of("name", "price", "selector", "p.price"))), body.get("schema_spec"));
        assertTrue(r.authRequired());
        assertEquals("d1", r.discoveryId());
    }

    @Test
    void extractHtmlConvertsTheSchema() {
        routes.json("POST kolektr/extract/html", 200, Map.of("success", true));
        client.kolektr().extractHtml("<h1>x</h1>", "https://example.com", Map.of("t", "css:h1"));
        assertEquals(Map.of("fields", List.of(Map.of("name", "t", "selector", "h1"))),
                routes.bodies.get("POST kolektr/extract/html").get("schema_spec"));
    }

    // ── 9: result fields ─────────────────────────────────────────────────────

    @Test
    void fetchReadsAuthRequiredAndANullHtml() {
        var page = new HashMap<String, Object>(Map.of("success", true, "url", "https://example.com/login",
                "status_code", 200, "tier_used", 1, "auth_required", true, "discovery_id", "d2"));
        page.put("html", null);
        routes.json("POST evadr/fetch", 200, page);
        var r = client.evadr().fetch("https://example.com/login");
        assertTrue(r.authRequired());
        assertEquals("d2", r.discoveryId());
        assertNull(r.html());
    }

    // ── 4, 5, 6, 13, 14: parameters ──────────────────────────────────────────

    @Test
    void nexusReasonSendsQueryTypeAndParameters() {
        routes.json("POST nexus/reason", 200, Map.of("success", true, "results", List.of("a")));
        client.nexus().reason("classify", List.of("https://example.com"), "per-1");
        assertEquals(Map.of("query_type", "classify", "parameters", List.of("https://example.com"),
                "perception_id", "per-1"), routes.bodies.get("POST nexus/reason"));
        client.nexus().reason("classify");
        assertEquals(Map.of("query_type", "classify", "parameters", List.of()), routes.bodies.get("POST nexus/reason"));
    }

    @Test
    void multiSiteWorkflowSendsDependenciesDescriptionAndGlobalConfig() {
        routes.json("POST fetchyr/multi-site-workflows", 201, Map.of("id", "m1"));
        var sites = List.<Map<String, Object>>of(Map.of("site_id", "a", "name", "A", "url", "https://a"),
                Map.of("site_id", "b", "name", "B", "url", "https://b"));
        client.fetchyr().createMultiSiteWorkflow("m", sites, "two sites",
                List.of(FetchyrNamespace.siteDependency("b", "a", "data")), Map.of("timeout", 60));
        var body = routes.bodies.get("POST fetchyr/multi-site-workflows");
        assertEquals("two sites", body.get("description"));
        assertEquals(List.of(Map.of("dependent_site", "b", "dependency_site", "a", "dependency_type", "data")),
                body.get("dependencies"));
        assertEquals(Map.of("timeout", 60), body.get("global_config"));
    }

    @Test
    void authChainResumeRoutes() {
        routes.json("POST fetchyr/auth-chain/d1/resume-credentials", 200, Map.of("status", "resumed"));
        routes.json("POST fetchyr/auth-chain/d1/resume-mfa", 200, Map.of("status", "resumed"));
        client.fetchyr().resumeAuthChainCredentials("d1", "user", "fixture-pass", true);
        client.fetchyr().resumeAuthChainMfa("d1", "123456");
        assertEquals(Map.of("username", "user", "password", "fixture-pass", "remember", true),
                routes.bodies.get("POST fetchyr/auth-chain/d1/resume-credentials"));
        assertEquals(Map.of("mfa_code", "123456"), routes.bodies.get("POST fetchyr/auth-chain/d1/resume-mfa"));
    }

    private String queryOf(Runnable call) throws Exception {
        call.run();
        String path = server.takeRequest().getPath();
        return path.contains("?") ? path.substring(path.indexOf('?') + 1) : "";
    }

    @Test
    void pagingParameters() throws Exception {
        for (String r : List.of("GET kolektr/jobs", "GET kolektr/scrapers", "GET kolektr/content", "GET fetchyr/workflows")) {
            routes.json(r, 200, Map.of());
        }
        assertEquals("page=2&limit=50&status_filter=completed",
                queryOf(() -> client.kolektr().listJobs(2, 50, "completed")));
        assertEquals("page=1&page_size=10&is_template=true",
                queryOf(() -> client.kolektr().listScrapers(1, 10, true)));
        assertEquals("page=3&page_size=5&search=shoes",
                queryOf(() -> client.kolektr().listContent(3, 5, "shoes")));
        assertEquals("page=2&page_size=25", queryOf(() -> client.fetchyr().listWorkflows(2, 25)));
        assertEquals("", queryOf(() -> client.kolektr().listJobs()));
    }

    @Test
    void apiDataEncodesTheEndpointAndSendsItsParameters() throws Exception {
        String endpoint = "https://api.shop.com/v1/items?page=2";
        String enc = "https%3A%2F%2Fapi.shop.com%2Fv1%2Fitems%3Fpage%3D2";
        routes.json("GET kolektr/api-data/" + enc, 200, Map.of());
        routes.json("GET kolektr/api-data/" + enc + "/paginated", 200, Map.of());
        routes.json("POST kolektr/api-data/" + enc + "/extract-all", 200, Map.of("job_id", "x"));

        assertEquals("endpoint_hash=h1", queryOf(() -> client.kolektr().getApiData(endpoint, "h1")));
        assertEquals("endpoint_hash=h1&offset=10&limit=20",
                queryOf(() -> client.kolektr().getApiDataPaginated(endpoint, 10, 20, "h1")));
        assertEquals("endpoint_hash=h1&max_records=500",
                queryOf(() -> client.kolektr().extractAllApiData(endpoint, "h1", 500)));
        assertEquals(List.of("GET kolektr/api-data/" + enc, "GET kolektr/api-data/" + enc + "/paginated",
                "POST kolektr/api-data/" + enc + "/extract-all"), routes.seen);
    }

    @Test
    void pathSegmentsAreEncoded() {
        assertEquals("a%2Fb%20c%3Fd%23e", HttpTransport.seg("a/b c?d#e"));
        routes.json("GET kolektr/jobs/a%2Fb", 200, Map.of("id", "a/b"));
        assertEquals("a/b", client.kolektr().getJob("a/b").get("id"));
        assertThrows(IllegalArgumentException.class, () -> HttpTransport.seg(null));
    }

    @Test
    void listSessionsAlwaysSendsActiveOnly() throws Exception {
        routes.json("GET webgrph/sessions", 200, List.of());
        assertEquals("active_only=false", queryOf(() -> client.webgrph().listSessions(false)));
        assertEquals("active_only=true", queryOf(() -> client.webgrph().listSessions(true)));
    }

    @Test
    void webgrphCrawlSendsScopeBudgetAndCaptureMode() {
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c1"));
        client.webgrph().startCrawl("https://example.com", CrawlOptions.builder()
                .scope(Map.of("same_host", true)).budget(Map.of("max_resources", 50)).captureMode("content")
                .buildAsSet());
        assertEquals(Map.of("url", "https://example.com", "scope", Map.of("same_host", true),
                "budget", Map.of("max_resources", 50), "capture_mode", "content"),
                routes.bodies.get("POST webgrph/crawl"));
    }

    @Test
    void readerViewSendsTheSession() {
        routes.json("POST webgrph/reader-view", 200, Map.of("url", "u"));
        client.webgrph().readerView("https://example.com", null, "sess-1");
        assertEquals(Map.of("url", "https://example.com", "session_artifact_id", "sess-1"),
                routes.bodies.get("POST webgrph/reader-view"));
    }

    // ── Skanyr parity ────────────────────────────────────────────────────────

    @Test
    void skanyrListsParseAsListsAndExportIsBytes() throws Exception {
        routes.json("GET skanyr/sites/s1/resources", 200, List.of(Map.of("id", "r1"), Map.of("id", "r2")));
        routes.json("GET skanyr/sites/s1/technologies", 200, List.of(Map.of("name", "React")));
        routes.add("POST skanyr/sites/s1/export", new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/zip").setBody(new okio.Buffer().write(new byte[]{80, 75, 3, 4})));
        routes.add("GET skanyr/extractions/h1/text", new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "text/plain").setBody("plain text"));

        assertEquals("r2", client.skanyr().getSiteResources("s1", "page", 10, 0).get(1).get("id"));
        assertEquals("React", client.skanyr().getTechnologies("s1").get(0).get("name"));
        assertArrayEquals(new byte[]{80, 75, 3, 4}, client.skanyr().exportSite("s1", List.of("r1"), 0));
        assertEquals(Map.of("resource_ids", List.of("r1")), routes.bodies.get("POST skanyr/sites/s1/export"));
        assertEquals("plain text", client.skanyr().getExtractionText("h1"));
    }

    @Test
    void skanyrSearchSendsTheMimeType() {
        routes.json("POST skanyr/search", 200, Map.of("results", List.of()));
        client.skanyr().search("pricing", null, 0, 0, "application/pdf");
        assertEquals(Map.of("query", "pricing", "mime_type", "application/pdf"), routes.bodies.get("POST skanyr/search"));
    }

    // ── 7: the crawl helper ──────────────────────────────────────────────────

    private void failedCrawl() {
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c1"));
        routes.json("GET webgrph/crawl/c1", 200, Map.of("status", "failed", "error", "seed unreachable"));
    }

    @Test
    void crawlCopiesTheCrawlsSuccessAndError() {
        failedCrawl();
        var r = client.crawl("https://example.com", CrawlOptions.builder().build());
        assertFalse(r.success());
        assertEquals("seed unreachable", r.error());
        assertEquals(0, r.pages().size());
    }

    private void completedCrawlOfOnePage() {
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c1"));
        routes.json("GET webgrph/crawl/c1", 200, Map.of("status", "completed", "total_pages", 2));
        routes.json("GET webgrph/crawl/c1/pages", 200, Map.of("total", 2, "nodes", List.of(
                Map.of("id", "a", "url", "https://example.com/a"), Map.of("id", "b", "url", "https://example.com/b"))));
    }

    @Test
    void crawlDoesNotSwallowBillingErrors() {
        completedCrawlOfOnePage();
        routes.json("POST evadr/fetch", 402, detail("payment_required", "pay", Map.of()));
        assertThrows(PaymentRequiredException.class, () -> client.crawl("https://example.com", CrawlOptions.builder().build()));
        assertEquals(1, routes.seen.stream().filter("POST evadr/fetch"::equals).count(), "stops at the first page");
    }

    @Test
    void crawlDoesNotSwallowAnExtractionAuthError() {
        completedCrawlOfOnePage();
        routes.json("POST evadr/fetch", 200, Map.of("success", true, "url", "https://example.com/a"));
        routes.json("POST kolektr/extract", 401, Map.of("detail", "bad key"));
        assertThrows(AuthenticationException.class, () -> client.crawl("https://example.com",
                CrawlOptions.builder().extractSchema(Map.of("t", "h1")).build()));
    }

    @Test
    void crawlStreamIsLazy() {
        completedCrawlOfOnePage();
        routes.json("POST evadr/fetch", 200, Map.of("success", true, "url", "https://example.com/a", "tier_used", 1));
        try (var stream = client.crawlStream("https://example.com", CrawlOptions.builder().build())) {
            var it = stream.iterator();
            assertEquals(List.of(), routes.seen, "nothing runs before the first event is read");
            assertEquals("discovery_complete", it.next().type());
            assertFalse(routes.seen.contains("POST evadr/fetch"));
            assertEquals("page_fetching", it.next().type());
            assertEquals(1, routes.seen.stream().filter("POST evadr/fetch"::equals).count());
            assertEquals("page_fetched", it.next().type());
            assertEquals(1, routes.seen.stream().filter("POST evadr/fetch"::equals).count(), "page b not fetched yet");
            var rest = new ArrayList<CrawlProgressEvent>();
            it.forEachRemaining(rest::add);
            assertEquals("crawl_complete", rest.get(rest.size() - 1).type());
        }
    }

    // ── SSE parsing ──────────────────────────────────────────────────────────

    @Test
    void sseJoinsDataLinesAndSkipsComments() {
        routes.json("POST evadr/fetch/async", 202, Map.of("job_id", "j1"));
        routes.add("GET evadr/jobs/j1/events", new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(": keepalive\n\nid: 1\nevent: pipeline_step\ndata: {\"event_type\":\"pipeline_step\",\n"
                        + "data: \"sequence\":1}\n\ndata: {\"type\":\"COMPLETE\",\"tier_used\":3}"));
        var events = client.evadr().fetchStream("https://example.com").toList();
        assertEquals(2, events.size());
        assertEquals(1, events.get(0).pipelineEvent().sequence());
        assertEquals(3, events.get(1).tierUsed(), "a last event without a blank line still counts");
    }

    @Test
    void anSseReadErrorIsRaised() {
        routes.json("POST evadr/fetch/async", 202, Map.of("job_id", "j1"));
        routes.add("GET evadr/jobs/j1/events", new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"STATUS\"}\n\n" + "data: {\"type\":\"STATUS\"}\n\n".repeat(2000))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        var ex = assertThrows(KloakdException.class, () -> client.evadr().fetchStream("https://example.com").toList());
        assertTrue(ex.getMessage().startsWith("Event stream read failed"), ex.getMessage());
    }

    @Test
    void aStreamThatFailsToOpenIsTyped() {
        routes.json("POST evadr/fetch/async", 202, Map.of("job_id", "j1"));
        routes.json("GET evadr/jobs/j1/events", 404, Map.of("detail", "Job j1 not found"));
        var ex = assertThrows(ApiException.class, () -> client.evadr().fetchStream("https://example.com"));
        assertEquals(404, ex.getStatusCode());
        assertEquals("Job j1 not found", ex.getMessage());
    }

    // ── examples stay out of the jar but compile ─────────────────────────────

    @Test
    void theExampleIsNotInTheMainSourcesAndCompiles() throws Exception {
        assertFalse(Files.exists(Path.of("src/main/java/dev/kloakd/examples")));
        var out = Files.createTempDirectory("kloakd-example");
        var err = new ByteArrayOutputStream();
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, err, "-d", out.toString(),
                "-cp", System.getProperty("java.class.path"), "examples/Quickstart.java");
        assertEquals(0, rc, err.toString());
    }
}
