package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.ApiException;
import dev.kloakd.sdk.models.CrawlOptions;
import dev.kloakd.sdk.models.CrawlResult;
import dev.kloakd.sdk.models.ExportCrawl;
import dev.kloakd.sdk.models.ExportDownload;
import dev.kloakd.sdk.models.ExtractionResult;
import dev.kloakd.sdk.models.LoginSessionOptions;
import dev.kloakd.sdk.models.PageNode;
import dev.kloakd.sdk.models.SavedExport;
import dev.kloakd.sdk.models.SiteCrawlResult;
import dev.kloakd.sdk.modules.WebgrphNamespace;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checked against kobe-scraper main 6bd19e9b: held crawls, partial crawls
 * and continue, login sessions, the export's X-Crawl-* headers, and the
 * fields the API does and does not return. Every answer here is a mock's,
 * shaped as the backend's response models.
 */
class Sdk070Test {

    private static final String HELD_TEXT = "Held: the organisation's quota is used up. The crawl resumes when quota returns.";
    private static final Duration FAST = Duration.ofMillis(1);
    private static final Duration SHORT = Duration.ofSeconds(5);

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

    /** POST webgrph/crawl as CrawlResponse answers it: no status. */
    private void crawlStarts() {
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c1",
                "url", "https://example.com", "total_pages", 0, "max_depth_reached", 0));
    }

    /** GET webgrph/crawl/c1 as CrawlStatusResponse answers it: no url. */
    private static Map<String, Object> crawlStatus(String status, Map<String, Object> more) {
        var m = new HashMap<String, Object>();
        m.put("crawl_id", "c1");
        m.put("status", status);
        m.put("organization_id", TEST_ORG_ID);
        m.put("total_pages", 0);
        m.put("max_depth_reached", 0);
        m.put("artifact_id", null);
        m.put("error", null);
        m.putAll(more);
        return m;
    }

    private static Map<String, Object> held() {
        return crawlStatus("held_quota", Map.of("message", HELD_TEXT));
    }

    // ── start ────────────────────────────────────────────────────────────────

    @Test
    void startCrawl_hasNoStatusAndSendsNoLimitsUnlessSet() {
        crawlStarts();

        var started = client.webgrph().startCrawl("https://example.com", CrawlOptions.builder().buildAsSet());

        assertTrue(started.success());
        assertEquals("c1", started.crawlId());
        assertEquals("https://example.com", started.url());
        assertNull(started.status(), "POST webgrph/crawl answers with no status");
        assertFalse(started.held());
        assertNull(started.isPartial());
        assertEquals(Map.of("url", "https://example.com"), routes.bodies.get("POST webgrph/crawl"),
                "max_depth and max_pages are sent only when set");
    }

    @Test
    void startCrawl_aStatusInTheAnswerIsNotRead() {
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c1",
                "url", "https://example.com", "status", "invented"));
        assertNull(client.webgrph().startCrawl("https://example.com", 2, 10, false, null).status());
        var body = routes.bodies.get("POST webgrph/crawl");
        assertEquals(2, ((Number) body.get("max_depth")).intValue());
        assertEquals(10, ((Number) body.get("max_pages")).intValue());
    }

    // ── held crawls ──────────────────────────────────────────────────────────

    @Test
    void crawl_aHeldCrawlIsReturnedNotPolledToTheTimeout() {
        crawlStarts();
        routes.json("GET webgrph/crawl/c1", 200, crawlStatus("crawling", Map.of()));
        routes.json("GET webgrph/crawl/c1", 200, held());

        // A timeout far beyond the test's: only stopping on held_quota returns in time.
        CrawlResult r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> client.webgrph()
                .crawl("https://example.com", CrawlOptions.builder().buildAsSet(), FAST, Duration.ofMinutes(10)));

        assertTrue(r.held());
        assertEquals("held_quota", r.status());
        assertEquals(WebgrphNamespace.HELD_QUOTA, r.status());
        assertFalse(r.success());
        assertEquals(HELD_TEXT, r.message());
        assertNull(r.error(), "a held crawl has not failed");
        assertTrue(r.pages().isEmpty());
        assertEquals("c1", r.crawlId());
        assertEquals("https://example.com", r.url(), "the url is the caller's, not the status body's");
        assertFalse(routes.seen.contains("GET webgrph/crawl/c1/pages"), "no pages are read for a held crawl");
    }

    @Test
    void crawl_aHeldCrawlsErrorTextIsNotAnError() {
        crawlStarts();
        var body = held();
        body.put("error", "should not be read as an error");
        routes.json("GET webgrph/crawl/c1", 200, body);

        var r = client.webgrph().crawl("https://example.com", CrawlOptions.builder().buildAsSet(), FAST, SHORT);

        assertTrue(r.held());
        assertNull(r.error());
    }

    @Test
    void waitForCrawl_byIdReturnsAHeldCrawlAndLaterItsPages() {
        routes.json("GET webgrph/crawl/c1", 200, held());

        var first = client.webgrph().waitForCrawl("c1", FAST, SHORT);
        assertTrue(first.held());
        assertNull(first.url(), "the status body has no url");

        // Quota returned: the same id goes on and completes.
        var routes2 = routes(server);
        routes2.json("GET webgrph/crawl/c1", 200, crawlStatus("completed", Map.of("total_pages", 1,
                "pages_discovered", 1, "pages_fetched", 1, "pages_unfetched", 0, "is_partial", false,
                "resumable", false)));
        routes2.json("GET webgrph/crawl/c1/pages", 200, Map.of("crawl_id", "c1", "total", 1, "nodes", List.of(
                Map.of("id", "n1", "url", "https://example.com/", "depth", 0, "category", "page",
                        "children_urls", List.of(), "metadata", Map.of("title", "Home", "fetch_status", "fetched")))));

        var second = client.webgrph().waitForCrawl("c1", FAST, SHORT, false);
        assertTrue(second.success());
        assertFalse(second.held());
        assertEquals(1, second.pages().size());
        assertEquals("fetched", second.pages().get(0).fetchStatus());
        assertNull(second.pages().get(0).fetchError());
        assertEquals(Boolean.FALSE, second.isPartial());
    }

    @Test
    void waitForCrawl_withTheDefaultsTakesOnlyTheId() {
        routes.json("GET webgrph/crawl/c1", 200, held());
        var r = client.webgrph().waitForCrawl("c1");
        assertTrue(r.held());
        assertEquals(List.of("GET webgrph/crawl/c1"), routes.seen);
    }

    @Test
    void crawlAll_isEmptyForAHeldCrawl() {
        crawlStarts();
        routes.json("GET webgrph/crawl/c1", 200, held());
        List<PageNode> pages = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> client.webgrph().crawlAll("https://example.com", 0, 0));
        assertTrue(pages.isEmpty());
    }

    @Test
    void clientCrawl_reportsAHeldCrawlAndFetchesNothing() {
        crawlStarts();
        routes.json("GET webgrph/crawl/c1", 200, held());

        SiteCrawlResult r = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> client.crawl("https://example.com", CrawlOptions.builder().maxPages(5).build()));

        assertTrue(r.held());
        assertEquals("held_quota", r.status());
        assertEquals("c1", r.crawlId());
        assertEquals(HELD_TEXT, r.message());
        assertFalse(r.success());
        assertNull(r.error());
        assertEquals(0, r.pagesFetched());
        assertFalse(routes.seen.contains("POST evadr/fetch"));
    }

    // ── partial crawls and continue ──────────────────────────────────────────

    @Test
    void crawl_readsTheOutcomeFields() {
        crawlStarts();
        var done = crawlStatus("completed", Map.of("total_pages", 40,
                "max_depth_reached", 2, "artifact_id", "art-1", "message", "Stopped at the time budget",
                "pages_discovered", 100, "pages_fetched", 40, "pages_unfetched", 60, "is_partial", true,
                "partial_reason", "time_budget", "resumable", true));
        done.put("error_code", "time_budget");
        routes.json("GET webgrph/crawl/c1", 200, done);
        routes.json("GET webgrph/crawl/c1/pages", 200, Map.of("crawl_id", "c1", "total", 0, "nodes", List.of()));

        var r = client.webgrph().crawl("https://example.com", CrawlOptions.builder().buildAsSet(), FAST, SHORT);

        assertTrue(r.success());
        assertFalse(r.held());
        assertEquals(Boolean.TRUE, r.isPartial());
        assertEquals("time_budget", r.partialReason());
        assertEquals(Boolean.TRUE, r.resumable());
        assertEquals(100, r.pagesDiscovered());
        assertEquals(40, r.pagesFetched());
        assertEquals(60, r.pagesUnfetched());
        assertEquals("time_budget", r.errorCode());
        assertEquals("Stopped at the time budget", r.message());
        assertEquals(40, r.totalPages());
        assertEquals("art-1", r.artifactId());
    }

    @Test
    void crawl_theOutcomeFieldsAreNullWhenNotReported() {
        crawlStarts();
        routes.json("GET webgrph/crawl/c1", 200, crawlStatus("failed", Map.of("error", "boom",
                "error_code", "held_quota_expired")));

        var r = client.webgrph().crawl("https://example.com", CrawlOptions.builder().buildAsSet(), FAST, SHORT);

        assertFalse(r.success());
        assertEquals("boom", r.error());
        assertEquals("held_quota_expired", r.errorCode());
        assertNull(r.isPartial());
        assertNull(r.resumable());
        assertNull(r.pagesDiscovered());
        assertNull(r.pagesFetched());
        assertNull(r.pagesUnfetched());
        assertNull(r.partialReason());
        assertNull(r.message());
    }

    @Test
    void crawlResult_the060ConstructorStillWorks() {
        var r = new CrawlResult(true, "c1", "u", "completed", 1, 0, List.of(), 1, "a", null);
        assertFalse(r.held());
        assertNull(r.isPartial());
        assertNull(r.message());
        var site = new SiteCrawlResult(true, "u", 1, 1, 0, List.of(), "a", null);
        assertFalse(site.held());
        assertNull(site.crawlId());
        var extraction = new ExtractionResult(true, "u", "css", List.of(), 0, 1, null, null, null, false, null);
        assertNull(extraction.level());
        assertNull(extraction.loginOptions());
        var saved = new SavedExport(Path.of("x"), 1, "text/csv", "x.csv", "completed");
        assertNull(saved.crawl());
    }

    @Test
    void continueCrawl_postsWithNoBodyAndReadsTheAnswer() {
        routes.json("POST webgrph/crawl/c1/continue", 202,
                Map.of("success", true, "crawl_id", "c1", "status", "queued"));

        var r = client.webgrph().continueCrawl("c1");

        assertTrue(r.success());
        assertEquals("c1", r.crawlId());
        assertEquals("queued", r.status());
        assertEquals(List.of("POST webgrph/crawl/c1/continue"), routes.seen);
        assertEquals(Map.of(), routes.bodies.get("POST webgrph/crawl/c1/continue"));
    }

    @Test
    void continueCrawl_a409IsAnApiException() {
        routes.json("POST webgrph/crawl/c1/continue", 409, Map.of("detail",
                "Crawl cannot be continued: it is not a crawl that stopped at its time budget"));

        var ex = assertThrows(ApiException.class, () -> client.webgrph().continueCrawl("c1"));

        assertEquals(409, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("cannot be continued"));
    }

    @Test
    void continueCrawl_theIdIsPercentEncoded() {
        routes.json("POST webgrph/crawl/a%2Fb/continue", 202, Map.of("success", true, "crawl_id", "a/b", "status", "queued"));
        assertEquals("a/b", client.webgrph().continueCrawl("a/b").crawlId());
    }

    // ── page nodes ───────────────────────────────────────────────────────────

    @Test
    void pageNode_readsFetchStatusAndFetchErrorFromItsMetadata() {
        var unfetched = new PageNode("n", "u", null, 1, "page", null, List.of(),
                Map.of("fetch_status", "unfetched", "fetch_error", "timeout"));
        assertEquals("unfetched", unfetched.fetchStatus());
        assertEquals("timeout", unfetched.fetchError());
        var bare = new PageNode("n", "u", null, 1, "page", null, List.of(), Map.of());
        assertNull(bare.fetchStatus());
        assertNull(bare.fetchError());
        var none = new PageNode("n", "u", null, 1, "page", null, List.of(), null);
        assertNull(none.fetchStatus());
        assertNull(none.fetchError());
    }

    // ── crawl-extract ────────────────────────────────────────────────────────

    private static Map<String, Object> job(String status, Map<String, Object> crawl) {
        var m = new HashMap<String, Object>();
        m.put("job_id", "job-1");
        m.put("status", status);
        m.put("crawl", crawl);
        return m;
    }

    @Test
    void waitForCrawlExtract_returnsWhenTheJobsCrawlIsHeld() {
        routes.json("GET webgrph/crawl-extract/job-1", 200, job("crawling", null));
        routes.json("GET webgrph/crawl-extract/job-1", 200, job("crawling", Map.of("status", "crawling")));
        routes.json("GET webgrph/crawl-extract/job-1", 200, job("crawling", Map.of("status", "held_quota")));

        Map<String, Object> status = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> client.webgrph().waitForCrawlExtract("job-1", FAST, Duration.ofMinutes(10)));

        assertEquals(Boolean.TRUE, status.get("held"));
        assertEquals("crawling", status.get("status"), "the job itself is not held");
        assertTrue(WebgrphNamespace.isCrawlExtractHeld(status));
        assertEquals(3, routes.seen.size());
    }

    @Test
    void waitForCrawlExtract_anEndedJobIsNotHeldAndKeepsItsCrawlObject() {
        routes.json("GET webgrph/crawl-extract/job-1", 200, job("completed", Map.of("status", "completed",
                "is_partial", true, "partial_reason", "max_pages", "pages_discovered", 9, "pages_fetched", 5,
                "pages_unfetched", 4, "cancelled", false, "root_unfetched", false)));

        var status = client.webgrph().waitForCrawlExtract("job-1", FAST, SHORT);

        assertEquals(Boolean.FALSE, status.get("held"));
        @SuppressWarnings("unchecked")
        var crawl = (Map<String, Object>) status.get("crawl");
        assertEquals(true, crawl.get("is_partial"));
        assertEquals("max_pages", crawl.get("partial_reason"));
        assertEquals(4, ((Number) crawl.get("pages_unfetched")).intValue());
    }

    @Test
    void crawlExtract_aHeldJobIsReturnedNotThrown() {
        routes.json("POST webgrph/crawl-extract", 202, Map.of("success", true, "job_id", "job-1"));
        routes.json("GET webgrph/crawl-extract/job-1", 200, job("crawling", Map.of("status", "held_quota")));

        var status = client.webgrph().crawlExtract("https://example.com", null, null, FAST, Duration.ofMinutes(10));

        assertEquals(Boolean.TRUE, status.get("held"));
        assertEquals("job-1", status.get("job_id"));
    }

    @Test
    void isCrawlExtractHeld_readsOnlyTheCrawlObject() {
        assertFalse(WebgrphNamespace.isCrawlExtractHeld(null));
        assertFalse(WebgrphNamespace.isCrawlExtractHeld(Map.of("status", "held_quota")));
        assertFalse(WebgrphNamespace.isCrawlExtractHeld(job("crawling", null)));
        assertTrue(WebgrphNamespace.isCrawlExtractHeld(job("crawling", Map.of("status", "held_quota"))));
    }

    // ── export headers ───────────────────────────────────────────────────────

    private static MockResponse export() {
        return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-ndjson")
                .addHeader("Content-Disposition", "attachment; filename=\"crawl-extract-job-1.ndjson\"")
                .addHeader("X-Job-Status", "completed").setBody("{}\n");
    }

    @Test
    void exportCrawlExtract_readsTheCrawlHeaders() {
        routes.add("GET webgrph/crawl-extract/job-1/export", export()
                .addHeader("X-Crawl-Status", "completed").addHeader("X-Crawl-Partial", "true")
                .addHeader("X-Crawl-Partial-Reason", "time_budget").addHeader("X-Crawl-Pages-Discovered", "100")
                .addHeader("X-Crawl-Pages-Fetched", "40").addHeader("X-Crawl-Pages-Unfetched", "60")
                .addHeader("X-Crawl-Cancelled", "false").addHeader("X-Crawl-Root-Unfetched", "false"));

        try (ExportDownload e = client.webgrph().exportCrawlExtract("job-1", "ndjson")) {
            assertEquals("completed", e.jobStatus());
            assertEquals(new ExportCrawl("completed", true, "time_budget", 100, 40, 60, false, false), e.crawl());
        }
    }

    @Test
    void exportCrawlExtract_aHeaderNotSentIsNull() {
        routes.add("GET webgrph/crawl-extract/job-1/export", export()
                .addHeader("X-Crawl-Status", "held_quota").addHeader("X-Crawl-Cancelled", "false")
                .addHeader("X-Crawl-Pages-Fetched", "not-a-number"));

        try (ExportDownload e = client.webgrph().exportCrawlExtract("job-1", "ndjson")) {
            ExportCrawl crawl = e.crawl();
            assertEquals("held_quota", crawl.status());
            assertEquals(Boolean.FALSE, crawl.cancelled());
            assertNull(crawl.isPartial());
            assertNull(crawl.partialReason());
            assertNull(crawl.pagesDiscovered());
            assertNull(crawl.pagesFetched());
            assertNull(crawl.pagesUnfetched());
            assertNull(crawl.rootUnfetched());
        }
    }

    @Test
    void exportCrawlExtract_noCrawlHeaderMeansNoCrawlObject() {
        routes.add("GET webgrph/crawl-extract/job-1/export", export());
        try (ExportDownload e = client.webgrph().exportCrawlExtract("job-1", "ndjson")) {
            assertEquals("completed", e.jobStatus());
            assertNull(e.crawl());
        }
    }

    @Test
    void downloadCrawlExtract_keepsTheCrawlHeaders(@TempDir Path dir) throws Exception {
        routes.add("GET webgrph/crawl-extract/job-1/export", export()
                .addHeader("X-Crawl-Status", "cancelled").addHeader("X-Crawl-Cancelled", "true"));

        SavedExport saved = client.webgrph().downloadCrawlExtract("job-1", dir.resolve("rows.ndjson"), "ndjson");

        assertEquals("{}\n", Files.readString(dir.resolve("rows.ndjson")));
        assertEquals("cancelled", saved.crawl().status());
        assertEquals(Boolean.TRUE, saved.crawl().cancelled());
    }

    @Test
    void batchExport_hasNeitherJobStatusNorCrawl(@TempDir Path dir) {
        // kernel/batch_export_router.py sends Content-Disposition and Cache-Control only.
        routes.add("GET batch-jobs/b-1/export", new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/x-ndjson")
                .addHeader("Content-Disposition", "attachment; filename=\"batch-b-1.ndjson\"")
                .addHeader("Cache-Control", "no-store").setBody("{}\n"));

        try (ExportDownload e = client.exportBatchJob("b-1", "ndjson")) {
            assertNull(e.jobStatus());
            assertNull(e.crawl());
        }
        var saved = client.downloadBatchJob("b-1", dir.resolve("b.ndjson"), "ndjson");
        assertNull(saved.jobStatus());
        assertNull(saved.crawl());
    }

    // ── login sessions ───────────────────────────────────────────────────────

    @Test
    void startLoginSession_interactiveSendsOnlyTheUrl() {
        routes.json("POST fetchyr/login-sessions", 202, Map.of("method", "interactive",
                "status", "waiting_for_login", "target_url", "https://shop.example/account",
                "discovery_id", "disc-1", "next", Map.of("status", "/fetchyr/auth-chain/disc-1"),
                "cookies_dropped", List.of(), "protections", Map.of("max_requests_per_hour", 60),
                "message", "Answer the login"));

        var s = client.fetchyr().startLoginSession("https://shop.example/account");

        assertEquals(Map.of("target_url", "https://shop.example/account"),
                routes.bodies.get("POST fetchyr/login-sessions"));
        assertEquals(List.of("POST fetchyr/login-sessions"), routes.seen, "the 202 is the answer, not a hand-off");
        assertEquals("interactive", s.method());
        assertEquals("waiting_for_login", s.status());
        assertEquals("https://shop.example/account", s.targetUrl());
        assertEquals("disc-1", s.discoveryId());
        assertNull(s.sessionArtifactId());
        assertEquals(Map.of("status", "/fetchyr/auth-chain/disc-1"), s.next());
        assertNull(s.cookiesKept());
        assertEquals(List.of(), s.cookiesDropped());
        assertEquals(60, ((Number) s.protections().get("max_requests_per_hour")).intValue());
        assertEquals("Answer the login", s.message());
    }

    @Test
    void startLoginSession_cookiesSendsWhatIsSet() {
        routes.json("POST fetchyr/login-sessions", 201, Map.of("method", "cookies", "status", "ready",
                "target_url", "https://shop.example/account", "session_artifact_id", "art-9",
                "next", Map.of(), "cookies_kept", 1, "cookies_dropped", List.of("_ga"),
                "protections", Map.of(), "message", "Ready"));
        List<Map<String, Object>> cookies = List.of(
                Map.of("name", "sid", "value", "fixture-cookie", "domain", ".shop.example", "httpOnly", true),
                Map.of("name", "_ga", "value", "fixture-other", "domain", ".elsewhere.example"));

        var s = client.fetchyr().startLoginSession("https://shop.example/account", LoginSessionOptions.builder()
                .method("cookies").cookies(cookies).userAgent("Mozilla/5.0 (fixture)").country("KE").build());

        var body = routes.bodies.get("POST fetchyr/login-sessions");
        assertEquals(Map.of("target_url", "https://shop.example/account", "method", "cookies",
                "cookies", cookies, "user_agent", "Mozilla/5.0 (fixture)", "country", "KE"), body);
        assertEquals("ready", s.status());
        assertEquals("art-9", s.sessionArtifactId());
        assertNull(s.discoveryId());
        assertEquals(1, s.cookiesKept());
        assertEquals(List.of("_ga"), s.cookiesDropped());
    }

    @Test
    void startLoginSession_emptyOptionsSendOnlyTheUrl() {
        routes.json("POST fetchyr/login-sessions", 202, Map.of("method", "interactive",
                "status", "waiting_for_login", "target_url", "https://shop.example", "message", "m"));
        var s = client.fetchyr().startLoginSession("https://shop.example", LoginSessionOptions.builder().build());
        assertEquals(Map.of("target_url", "https://shop.example"), routes.bodies.get("POST fetchyr/login-sessions"));
        assertEquals(Map.of(), s.next());
        assertEquals(Map.of(), s.protections());
    }

    @Test
    void startLoginSession_a422IsAnApiException() {
        routes.json("POST fetchyr/login-sessions", 422,
                Map.of("detail", "method=cookies needs cookies and user_agent"));
        var ex = assertThrows(ApiException.class, () -> client.fetchyr().startLoginSession("https://shop.example",
                LoginSessionOptions.builder().method("cookies").build()));
        assertEquals(422, ex.getStatusCode());
        assertEquals(Map.of("target_url", "https://shop.example", "method", "cookies"),
                routes.bodies.get("POST fetchyr/login-sessions"));
    }

    private static Map<String, Object> session(String status, String pauseReason) {
        var m = new HashMap<String, Object>();
        m.put("artifact_id", "art-9");
        m.put("status", status);
        m.put("target_domain", "shop.example");
        m.put("auth_method", "cookies");
        m.put("created_at", "2026-11-12T08:00:00+00:00");
        m.put("expires_at", "2026-11-19T08:00:00+00:00");
        m.put("last_used_at", "2026-11-12T09:00:00+00:00");
        m.put("paused_at", pauseReason == null ? null : "2026-11-12T09:30:00+00:00");
        m.put("pause_reason", pauseReason);
        m.put("pause_message", pauseReason == null ? null : "The site slowed this session down");
        m.put("identity", Map.of("user_agent", "Mozilla/5.0 (fixture)", "country", "KE"));
        m.put("requests_this_hour", 12);
        m.put("protections", Map.of("max_requests_per_hour", 60));
        return m;
    }

    @Test
    void getSession_readsEveryField() {
        routes.json("GET fetchyr/sessions/art-9", 200, session("paused", "rate_limited"));

        var s = client.fetchyr().getSession("art-9");

        assertEquals("art-9", s.artifactId());
        assertEquals("paused", s.status());
        assertEquals("shop.example", s.targetDomain());
        assertEquals("cookies", s.authMethod());
        assertEquals("2026-11-12T08:00:00+00:00", s.createdAt());
        assertEquals("2026-11-19T08:00:00+00:00", s.expiresAt());
        assertEquals("2026-11-12T09:00:00+00:00", s.lastUsedAt());
        assertEquals("2026-11-12T09:30:00+00:00", s.pausedAt());
        assertEquals("rate_limited", s.pauseReason());
        assertEquals("The site slowed this session down", s.pauseMessage());
        assertEquals("KE", s.identity().get("country"));
        assertEquals(12, s.requestsThisHour());
        assertEquals(60, ((Number) s.protections().get("max_requests_per_hour")).intValue());
        assertEquals(List.of("GET fetchyr/sessions/art-9"), routes.seen);
    }

    @Test
    void getSession_anUnknownSessionIs404() {
        var ex = assertThrows(ApiException.class, () -> client.fetchyr().getSession("nope"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void resumeSession_postsAndReadsTheSession() {
        routes.json("POST fetchyr/sessions/art-9/resume", 200, session("active", null));

        var s = client.fetchyr().resumeSession("art-9");

        assertEquals("active", s.status());
        assertNull(s.pauseReason());
        assertNull(s.pausedAt());
        assertEquals(List.of("POST fetchyr/sessions/art-9/resume"), routes.seen);
        assertEquals(Map.of(), routes.bodies.get("POST fetchyr/sessions/art-9/resume"));
    }

    @Test
    void resumeSession_a409IsAnApiException() {
        routes.json("POST fetchyr/sessions/art-9/resume", 409, Map.of("detail", "The session is not paused"));
        var ex = assertThrows(ApiException.class, () -> client.fetchyr().resumeSession("art-9"));
        assertEquals(409, ex.getStatusCode());
        assertEquals("The session is not paused", ex.getMessage());
    }

    // ── nexus and kolektr ────────────────────────────────────────────────────

    @Test
    void reason_sendsTimeoutSecsOnlyWhenSet() {
        routes.json("POST nexus/reason", 200, Map.of("success", true, "results", List.of(), "confidence", 0.5));

        client.nexus().reason("classify", List.of("a"), "p-1", 12.5);
        assertEquals(Map.of("query_type", "classify", "parameters", List.of("a"), "perception_id", "p-1",
                "timeout_secs", 12.5), routes.bodies.get("POST nexus/reason"));

        client.nexus().reason("classify", List.of("a"), null, null);
        assertEquals(Map.of("query_type", "classify", "parameters", List.of("a")),
                routes.bodies.get("POST nexus/reason"));

        client.nexus().reason("classify", List.of("a"), "p-1");
        assertFalse(routes.bodies.get("POST nexus/reason").containsKey("timeout_secs"));
    }

    @Test
    void extract_readsLevelLevelReasonAndLoginOptions() {
        routes.json("POST kolektr/extract", 200, Map.of("success", true, "url", "https://example.com",
                "method", "css", "records", List.of(), "total_records", 0, "pages_scraped", 1,
                "level", "level2", "level_reason", "auto: the page needs a browser"));
        var r = client.kolektr().extract("https://example.com");
        assertEquals("level2", r.level());
        assertEquals("auto: the page needs a browser", r.levelReason());
        assertNull(r.loginOptions());
    }

    @Test
    void extract_aLoginPageCarriesItsLoginOptions() {
        routes.json("POST kolektr/extract", 200, Map.of("success", false, "url", "https://example.com/login",
                "method", "none", "records", List.of(), "total_records", 0, "pages_scraped", 0,
                "auth_required", true, "discovery_id", "disc-1",
                "login_options", Map.of("reason", "login_page", "discovery_id", "disc-1")));
        var r = client.kolektr().extract("https://example.com/login");
        assertTrue(r.authRequired());
        assertEquals("disc-1", r.discoveryId());
        assertEquals("login_page", r.loginOptions().get("reason"));
    }
}
