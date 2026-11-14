package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.ApiException;
import dev.kloakd.sdk.models.CrawlContinueOptions;
import dev.kloakd.sdk.models.CrawlOptions;
import dev.kloakd.sdk.models.CrawlTiming;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Crawl estimate, continue with options and the time budget fields (contract of 14 Nov 2026). */
class CrawlEstimateTest {

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

    private static Map<String, Object> estimate(String mode) {
        var m = new HashMap<String, Object>();
        m.put("mode", mode);
        m.put("url", "https://example.com/");
        m.put("max_depth", 2);
        m.put("max_pages", 10);
        m.put("plan_limits", Map.of("max_depth", 5, "max_pages", 500, "plan_tier", "free"));
        m.put("clamped", false);
        var site = new HashMap<String, Object>();
        site.put("reachable", true);
        site.put("sitemap_pages", 1234);
        site.put("homepage_links", 57);
        site.put("probe_error", null);
        m.put("site", site);
        m.put("est_pages", Map.of("low", 10, "high", 10, "source", "sitemap"));
        m.put("will_hit_page_cap", true);
        m.put("time_budget_s", 120);
        m.put("est_duration_s", Map.of("low", 40, "high", 120));
        m.put("est_gb", 0.03);
        m.put("gb_remaining", 4.2);
        m.put("will_exhaust_quota", false);
        m.put("warning", null);
        m.put("explanation", "A crawl maps the site: it finds the pages and how they link, without pulling data from them.");
        return m;
    }

    // ── estimate ─────────────────────────────────────────────────────────────

    @Test
    void estimateCrawl_postsTheCrawlBodyAndReadsEveryField() {
        routes.json("POST webgrph/crawl/estimate", 200, estimate("crawl"));

        var e = client.webgrph().estimateCrawl("https://example.com/", 2, 10);

        assertEquals(List.of("POST webgrph/crawl/estimate"), routes.seen);
        assertEquals(Map.of("url", "https://example.com/", "max_depth", 2, "max_pages", 10),
                routes.bodies.get("POST webgrph/crawl/estimate"));
        assertEquals("crawl", e.mode());
        assertEquals("https://example.com/", e.url());
        assertEquals(2, e.maxDepth());
        assertEquals(10, e.maxPages());
        assertEquals(5, e.planLimits().maxDepth());
        assertEquals(500, e.planLimits().maxPages());
        assertEquals("free", e.planLimits().planTier());
        assertFalse(e.clamped());
        assertTrue(e.site().reachable());
        assertEquals(1234, e.site().sitemapPages());
        assertEquals(57, e.site().homepageLinks());
        assertNull(e.site().probeError());
        assertEquals(10, e.estPages().low());
        assertEquals(10, e.estPages().high());
        assertEquals("sitemap", e.estPages().source());
        assertTrue(e.willHitPageCap());
        assertEquals(120, e.timeBudgetS());
        assertEquals(40.0, e.estDurationS().low());
        assertEquals(120.0, e.estDurationS().high());
        assertEquals(0.03, e.estGb());
        assertEquals(4.2, e.gbRemaining());
        assertFalse(e.willExhaustQuota());
        assertNull(e.warning());
        assertTrue(e.explanation().startsWith("A crawl maps the site"));
    }

    @Test
    void estimateCrawl_withNoLimitsSendsOnlyTheUrlAndReadsNullsAsNull() {
        var body = estimate("crawl");
        body.put("gb_remaining", null);
        body.put("time_budget_s", null);
        var site = new HashMap<String, Object>();
        site.put("reachable", false);
        site.put("sitemap_pages", null);
        site.put("homepage_links", null);
        site.put("probe_error", "timeout");
        body.put("site", site);
        body.put("est_pages", Map.of("low", 1, "high", 10, "source", "limit"));
        routes.json("POST webgrph/crawl/estimate", 200, body);

        var e = client.webgrph().estimateCrawl("https://example.com/");

        assertEquals(Map.of("url", "https://example.com/"), routes.bodies.get("POST webgrph/crawl/estimate"));
        assertNull(e.gbRemaining());
        assertNull(e.timeBudgetS());
        assertFalse(e.site().reachable());
        assertNull(e.site().sitemapPages());
        assertNull(e.site().homepageLinks());
        assertEquals("timeout", e.site().probeError());
        assertEquals("limit", e.estPages().source());
    }

    @Test
    void estimateCrawlExtract_postsTheJobBodyWithExtract() {
        routes.json("POST webgrph/crawl-extract/estimate", 200, estimate("crawl_extract"));

        var e = client.webgrph().estimateCrawlExtract("https://example.com/",
                CrawlOptions.builder().maxDepth(3).maxPages(200).buildAsSet(), Map.of("entity_type", "product"));

        assertEquals(List.of("POST webgrph/crawl-extract/estimate"), routes.seen);
        assertEquals(Map.of("url", "https://example.com/", "max_depth", 3, "max_pages", 200,
                "extract", Map.of("entity_type", "product")), routes.bodies.get("POST webgrph/crawl-extract/estimate"));
        assertEquals("crawl_extract", e.mode());
    }

    @Test
    void estimate_errorsAreApiExceptions() {
        routes.json("POST webgrph/crawl-extract/estimate", 403, Map.of("detail", "module_not_entitled"));

        var ex = assertThrows(ApiException.class, () -> client.webgrph().estimateCrawlExtract("https://example.com/"));
        assertEquals(403, ex.getStatusCode());
    }

    // ── continue ─────────────────────────────────────────────────────────────

    @Test
    void continueCrawl_withoutOptionsSendsNoBody() {
        routes.json("POST webgrph/crawl/c1/continue", 202, Map.of("success", true, "crawl_id", "c1", "status", "queued"));

        client.webgrph().continueCrawl("c1");
        assertEquals("", routes.rawBodies.get("POST webgrph/crawl/c1/continue"));
        client.webgrph().continueCrawl("c1", null);
        assertEquals("", routes.rawBodies.get("POST webgrph/crawl/c1/continue"));
        client.webgrph().continueCrawl("c1", CrawlContinueOptions.builder().build());
        assertEquals("", routes.rawBodies.get("POST webgrph/crawl/c1/continue"));
    }

    @Test
    void continueCrawl_withOptionsSendsOnlyTheSetLimits() {
        routes.json("POST webgrph/crawl/c1/continue", 202, Map.of("success", true, "crawl_id", "c1", "status", "queued"));

        var r = client.webgrph().continueCrawl("c1", CrawlContinueOptions.builder().maxPages(500).build());
        assertEquals(Map.of("max_pages", 500), routes.bodies.get("POST webgrph/crawl/c1/continue"));
        assertEquals("queued", r.status());

        client.webgrph().continueCrawl("c1", CrawlContinueOptions.builder().maxPages(500).maxDepth(4).build());
        assertEquals(Map.of("max_pages", 500, "max_depth", 4), routes.bodies.get("POST webgrph/crawl/c1/continue"));
    }

    @Test
    void continueCrawl_aLowerLimitIs422() {
        routes.json("POST webgrph/crawl/c1/continue", 422, Map.of("error", "continue_cannot_lower"));

        var ex = assertThrows(ApiException.class, () -> client.webgrph().continueCrawl("c1",
                CrawlContinueOptions.builder().maxPages(5).build()));
        assertEquals(422, ex.getStatusCode());
    }

    @Test
    void continueCrawlExtract_withAndWithoutOptions() {
        routes.json("POST webgrph/crawl-extract/job-1/continue", 202,
                Map.of("success", true, "job_id", "job-1", "status", "queued"));

        var r = client.webgrph().continueCrawlExtract("job-1");
        assertEquals("", routes.rawBodies.get("POST webgrph/crawl-extract/job-1/continue"));
        assertEquals("queued", r.get("status"));

        client.webgrph().continueCrawlExtract("job-1", CrawlContinueOptions.builder().maxDepth(3).build());
        assertEquals(Map.of("max_depth", 3), routes.bodies.get("POST webgrph/crawl-extract/job-1/continue"));
    }

    // ── status time budget ───────────────────────────────────────────────────

    @Test
    void waitForCrawl_readsTheTimeBudgetFields() {
        routes.json("GET webgrph/crawl/c1", 200, Map.of("crawl_id", "c1", "status", "failed",
                "total_pages", 0, "max_depth_reached", 0,
                "time_budget_s", 120, "elapsed_s", 30.5, "est_remaining_s", 89.5));

        var r = client.webgrph().waitForCrawl("c1", Duration.ofMillis(1), Duration.ofSeconds(5));

        assertEquals(120, r.timeBudgetS());
        assertEquals(30.5, r.elapsedS());
        assertEquals(89.5, r.estRemainingS());
    }

    @Test
    void waitForCrawl_theTimeBudgetFieldsAreNullUntilReported() {
        routes.json("GET webgrph/crawl/c1", 200, Map.of("crawl_id", "c1", "status", "failed"));

        var r = client.webgrph().waitForCrawl("c1", Duration.ofMillis(1), Duration.ofSeconds(5));

        assertNull(r.timeBudgetS());
        assertNull(r.elapsedS());
        assertNull(r.estRemainingS());
    }

    @Test
    void crawlTiming_readsACrawlExtractStatus() {
        routes.json("GET webgrph/crawl-extract/job-1", 200, Map.of("job_id", "job-1", "status", "crawling",
                "time_budget_s", 600, "elapsed_s", 12, "est_remaining_s", 588.0));

        var timing = CrawlTiming.from(client.webgrph().getCrawlExtract("job-1"));

        assertEquals(600, timing.timeBudgetS());
        assertEquals(12.0, timing.elapsedS());
        assertEquals(588.0, timing.estRemainingS());
        var none = CrawlTiming.from(Map.of("status", "queued"));
        assertNull(none.timeBudgetS());
        assertNull(none.elapsedS());
        assertNull(none.estRemainingS());
    }
}
