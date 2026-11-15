package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.ApiException;
import dev.kloakd.sdk.errors.ConcurrencyLimitException;
import dev.kloakd.sdk.errors.CostAcknowledgementRequiredException;
import dev.kloakd.sdk.errors.ForbiddenException;
import dev.kloakd.sdk.models.*;
import dev.kloakd.sdk.modules.WebgrphNamespace;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Extras, the data limit, estimate and consent, map first and artifacts (cost-control contract of 15 Nov 2026). */
class CostControlTest {

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

    private static final Map<String, Object> STARTED = Map.of("success", true, "crawl_id", "c1", "url", "https://example.com/");

    private static CrawlOptions everyNewOption() {
        return CrawlOptions.builder().maxPages(10)
                .includeResources(ResourceTypes.IMAGES, ResourceTypes.FONTS)
                .maxDataMb(40.0)
                .estimateId("est-1")
                .costAcknowledged(true)
                .urls(List.of("https://example.com/a", "https://example.com/b"))
                .includePaths(List.of("/products/"))
                .buildAsSet();
    }

    private static Map<String, Object> everyNewField() {
        return Map.of("url", "https://example.com/", "max_pages", 10,
                "include_resources", List.of("images", "fonts"), "max_data_mb", 40.0,
                "estimate_id", "est-1", "cost_acknowledged", true,
                "urls", List.of("https://example.com/a", "https://example.com/b"),
                "include_paths", List.of("/products/"));
    }

    // ── request fields ───────────────────────────────────────────────────────

    @Test
    void startCrawl_sendsEveryCostControlField() {
        routes.json("POST webgrph/crawl", 202, STARTED);

        client.webgrph().startCrawl("https://example.com/", everyNewOption());

        assertEquals(everyNewField(), routes.bodies.get("POST webgrph/crawl"));
    }

    @Test
    void startCrawlExtract_sendsEveryCostControlField() {
        routes.json("POST webgrph/crawl-extract", 202, Map.of("success", true, "job_id", "job-1"));

        client.webgrph().startCrawlExtract("https://example.com/", everyNewOption(), Map.of("entity_type", "product"));

        var expected = new HashMap<>(everyNewField());
        expected.put("extract", Map.of("entity_type", "product"));
        assertEquals(expected, routes.bodies.get("POST webgrph/crawl-extract"));
    }

    @Test
    void oldCallsSendNoNewField() {
        routes.json("POST webgrph/crawl", 202, STARTED);
        routes.json("POST webgrph/crawl-extract", 202, Map.of("success", true, "job_id", "job-1"));

        client.webgrph().startCrawl("https://example.com/", 2, 10, false, null);
        assertEquals(Map.of("url", "https://example.com/", "max_depth", 2, "max_pages", 10),
                routes.bodies.get("POST webgrph/crawl"));
        // the 0.7.0 eight-component constructor still compiles and sends nothing new
        client.webgrph().startCrawl("https://example.com/", new CrawlOptions(0, 0, null, false, null, null, null, null));
        assertEquals(Map.of("url", "https://example.com/"), routes.bodies.get("POST webgrph/crawl"));
        client.webgrph().startCrawlExtract("https://example.com/");
        assertEquals(Map.of("url", "https://example.com/"), routes.bodies.get("POST webgrph/crawl-extract"));
    }

    @Test
    void costAcknowledgedFalseIsNotSentAndAnEmptyIncludeIsSent() {
        routes.json("POST webgrph/crawl", 202, STARTED);

        client.webgrph().startCrawl("https://example.com/",
                CrawlOptions.builder().includeResources(List.of()).costAcknowledged(false).buildAsSet());

        assertEquals(Map.of("url", "https://example.com/", "include_resources", List.of()),
                routes.bodies.get("POST webgrph/crawl"));
    }

    @Test
    void acknowledge_takesTheEstimateId() {
        var est = CrawlEstimate.from(Map.of("mode", "crawl", "estimate_id", "est-9", "consent_required", true));
        var o = CrawlOptions.builder().acknowledge(est).buildAsSet();
        assertEquals("est-9", o.estimateId());
        assertTrue(o.costAcknowledged());
    }

    @Test
    void estimate_sendsOnlyIncludeResourcesOfTheNewFields() {
        routes.json("POST webgrph/crawl/estimate", 200, Map.of("mode", "crawl"));
        routes.json("POST webgrph/crawl-extract/estimate", 200, Map.of("mode", "crawl_extract"));

        client.webgrph().estimateCrawl("https://example.com/", everyNewOption());
        assertEquals(Map.of("url", "https://example.com/", "max_pages", 10, "include_resources", List.of("images", "fonts")),
                routes.bodies.get("POST webgrph/crawl/estimate"));

        client.webgrph().estimateCrawlExtract("https://example.com/", everyNewOption(), null);
        assertEquals(Map.of("url", "https://example.com/", "max_pages", 10, "include_resources", List.of("images", "fonts")),
                routes.bodies.get("POST webgrph/crawl-extract/estimate"));

        client.webgrph().estimateCrawl("https://example.com/");
        assertEquals(Map.of("url", "https://example.com/"), routes.bodies.get("POST webgrph/crawl/estimate"));
    }

    // ── continue ─────────────────────────────────────────────────────────────

    @Test
    void continue_sendsMaxDataMbAndStillNoBodyWithoutOptions() {
        routes.json("POST webgrph/crawl/c1/continue", 202, Map.of("success", true, "crawl_id", "c1", "status", "queued"));
        routes.json("POST webgrph/crawl-extract/job-1/continue", 202, Map.of("success", true, "job_id", "job-1"));

        client.webgrph().continueCrawl("c1", CrawlContinueOptions.builder().maxDataMb(100).build());
        assertEquals(Map.of("max_data_mb", 100.0), routes.bodies.get("POST webgrph/crawl/c1/continue"));

        client.webgrph().continueCrawl("c1");
        assertEquals("", routes.rawBodies.get("POST webgrph/crawl/c1/continue"));
        client.webgrph().continueCrawl("c1", new CrawlContinueOptions(null, null));
        assertEquals("", routes.rawBodies.get("POST webgrph/crawl/c1/continue"));

        client.webgrph().continueCrawlExtract("job-1",
                CrawlContinueOptions.builder().maxPages(50).maxDataMb(80.5).build());
        assertEquals(Map.of("max_pages", 50, "max_data_mb", 80.5),
                routes.bodies.get("POST webgrph/crawl-extract/job-1/continue"));
    }

    // ── estimate response ────────────────────────────────────────────────────

    private static Map<String, Object> costEstimate() {
        var m = new HashMap<String, Object>();
        m.put("mode", "crawl");
        m.put("url", "https://example.com/");
        m.put("max_depth", 2);
        m.put("max_pages", 10);
        m.put("est_gb", 0.012);
        m.put("include_resources", List.of());
        m.put("data", Map.of(
                "source", "site_profile",
                "per_page_mb", Map.of("typical", 1.2, "high", 4.0),
                "total_mb", Map.of("low", 3.0, "typical", 12.0, "high", 40.0),
                "total_mb_all_resources", Map.of("typical", 30.0, "high", 120.0),
                "saving_mb", Map.of("typical", 18.0, "high", 80.0),
                "by_category_mb_per_page", Map.of("document", 0.3, "script", 0.6, "data", 0.1, "images", 1.1,
                        "media", 0, "fonts", 0.1, "stylesheets", 0.08, "tracking", 0.05),
                "saving_by_category_mb", Map.of("images", 11.0, "media", 0.0, "fonts", 1.0,
                        "stylesheets", 0.8, "tracking", 0.5)));
        m.put("share_of_remaining", Map.of("typical", 0.01, "high", 0.04));
        m.put("consent_required", false);
        m.put("risks", List.of("Some sites block automated visitors.", "Second risk."));
        m.put("suggested_max_data_mb", 40.0);
        m.put("estimate_id", "opaque.signed");
        return m;
    }

    @Test
    void estimate_readsEveryNewField() {
        routes.json("POST webgrph/crawl/estimate", 200, costEstimate());

        var e = client.webgrph().estimateCrawl("https://example.com/");

        assertEquals(List.of(), e.includeResources());
        var d = e.data();
        assertEquals("site_profile", d.source());
        assertEquals(new CrawlEstimate.DataRange(null, 1.2, 4.0), d.perPageMb());
        assertEquals(new CrawlEstimate.DataRange(3.0, 12.0, 40.0), d.totalMb());
        assertEquals(new CrawlEstimate.DataRange(null, 30.0, 120.0), d.totalMbAllResources());
        assertEquals(new CrawlEstimate.DataRange(null, 18.0, 80.0), d.savingMb());
        assertEquals(8, d.byCategoryMbPerPage().size());
        assertEquals(1.1, d.byCategoryMbPerPage().get("images"));
        assertEquals(0.0, d.byCategoryMbPerPage().get("media"));
        assertEquals(11.0, d.savingByCategoryMb().get(ResourceTypes.IMAGES));
        assertEquals(5, d.savingByCategoryMb().size());
        assertEquals(new CrawlEstimate.DataRange(null, 0.01, 0.04), e.shareOfRemaining());
        assertFalse(e.consentRequired());
        assertEquals(List.of("Some sites block automated visitors.", "Second risk."), e.risks());
        assertEquals(40.0, e.suggestedMaxDataMb());
        assertEquals("opaque.signed", e.estimateId());
        assertEquals(0.012, e.estGb());
    }

    @Test
    void estimate_fromAnOlderApiHasNullNewFields() {
        routes.json("POST webgrph/crawl/estimate", 200, Map.of("mode", "crawl", "url", "https://example.com/"));

        var e = client.webgrph().estimateCrawl("https://example.com/");

        assertNull(e.includeResources());
        assertNull(e.data());
        assertNull(e.shareOfRemaining());
        assertFalse(e.consentRequired());
        assertEquals(List.of(), e.risks());
        assertNull(e.suggestedMaxDataMb());
        assertNull(e.estimateId());
        // the 14 Nov constructor still works
        var old = new CrawlEstimate("crawl", "u", 1, 1, null, false, null, null, false, null, null, 0, null,
                false, null, null);
        assertNull(old.data());
    }

    // ── status ───────────────────────────────────────────────────────────────

    @Test
    void waitForCrawl_readsTheDataFieldsAndTheDataLimitReason() {
        routes.json("GET webgrph/crawl/c1", 200, Map.of("crawl_id", "c1", "status", "failed",
                "is_partial", true, "partial_reason", "data_limit",
                "message", "Stopped at your data limit of 40 MB. Continue with a higher limit to fetch more.",
                "data_used_mb", 40.2, "max_data_mb", 40, "include_resources", List.of("images")));

        var r = client.webgrph().waitForCrawl("c1", Duration.ofMillis(1), Duration.ofSeconds(5));

        assertEquals(40.2, r.dataUsedMb());
        assertEquals(40.0, r.maxDataMb());
        assertEquals(List.of("images"), r.includeResources());
        assertEquals(WebgrphNamespace.DATA_LIMIT, r.partialReason());
    }

    @Test
    void waitForCrawl_dataFieldsAreNullWhenNotSent() {
        routes.json("GET webgrph/crawl/c1", 200, Map.of("crawl_id", "c1", "status", "failed"));

        var r = client.webgrph().waitForCrawl("c1", Duration.ofMillis(1), Duration.ofSeconds(5));

        assertNull(r.dataUsedMb());
        assertNull(r.maxDataMb());
        assertNull(r.includeResources());
    }

    @Test
    void crawlDataUse_readsACrawlExtractStatus() {
        routes.json("GET webgrph/crawl-extract/job-1", 200, Map.of("job_id", "job-1", "status", "crawling",
                "data_used_mb", 3, "max_data_mb", 50.5, "include_resources", List.of()));

        var use = CrawlDataUse.from(client.webgrph().getCrawlExtract("job-1"));

        assertEquals(3.0, use.dataUsedMb());
        assertEquals(50.5, use.maxDataMb());
        assertEquals(List.of(), use.includeResources());
        var none = CrawlDataUse.from(Map.of("status", "queued"));
        assertNull(none.dataUsedMb());
        assertNull(none.maxDataMb());
        assertNull(none.includeResources());
    }

    // ── map first ────────────────────────────────────────────────────────────

    @Test
    void mapSite_postsTheBodyAndReadsTheTree() {
        routes.json("POST webgrph/map", 200, Map.of(
                "url", "https://example.com/", "source", "both", "total_urls", 1234, "truncated", true,
                "data_used_mb", 0.8,
                "branches", List.of(
                        Map.of("path", "/products/", "url_count", 800, "sample_urls", List.of("https://example.com/products/1"),
                                "children", List.of(Map.of("path", "/products/shoes/", "url_count", 300,
                                        "sample_urls", List.of(), "children", List.of()))),
                        Map.of("path", "other", "url_count", 34, "sample_urls", List.of(), "children", List.of()))));

        var map = client.webgrph().mapSite("https://example.com/", 5000);

        assertEquals(Map.of("url", "https://example.com/", "max_urls", 5000), routes.bodies.get("POST webgrph/map"));
        assertEquals("https://example.com/", map.url());
        assertEquals("both", map.source());
        assertEquals(1234, map.totalUrls());
        assertTrue(map.truncated());
        assertEquals(0.8, map.dataUsedMb());
        assertEquals(2, map.branches().size());
        var products = map.branches().get(0);
        assertEquals("/products/", products.path());
        assertEquals(800, products.urlCount());
        assertEquals(List.of("https://example.com/products/1"), products.sampleUrls());
        assertEquals("/products/shoes/", products.children().get(0).path());
        assertEquals(300, products.children().get(0).urlCount());
        assertEquals(List.of(), products.children().get(0).children());

        client.webgrph().mapSite("https://example.com/");
        assertEquals(Map.of("url", "https://example.com/"), routes.bodies.get("POST webgrph/map"));
    }

    // ── artifacts ────────────────────────────────────────────────────────────

    private static final String ART = "3f2b8c1e-9d4a-4e7b-8a61-2c5d0f9e7b13";

    @Test
    void getArtifact_readsTheMetadata() {
        var body = new HashMap<String, Object>();
        body.put("id", ART);
        body.put("type", "FETCHED_CONTENT");
        body.put("url", "https://example.com/");
        body.put("created_at", "2026-11-15T01:00:00Z");
        body.put("size_bytes", 5_000_000_000L);
        body.put("content_type", "text/html");
        body.put("final_url", "https://www.example.com/");
        body.put("status_code", 200);
        body.put("verified", true);
        body.put("content_url", "/api/v1/organizations/org/artifacts/" + ART + "/content");
        routes.json("GET artifacts/" + ART, 200, body);

        var a = client.getArtifact(ART);

        assertEquals(List.of("GET artifacts/" + ART), routes.seen);
        assertEquals(new Artifact(ART, Artifact.FETCHED_CONTENT, "https://example.com/", "2026-11-15T01:00:00Z",
                5_000_000_000L, "text/html", "https://www.example.com/", 200, true,
                "/api/v1/organizations/org/artifacts/" + ART + "/content"), a);
    }

    @Test
    void getArtifact_degradedWithNullableFieldsNull() {
        var body = new HashMap<String, Object>();
        body.put("id", ART);
        body.put("type", "DEGRADED_FETCHED_CONTENT");
        body.put("url", "https://example.com/");
        body.put("created_at", "2026-11-15T01:00:00Z");
        body.put("size_bytes", 12);
        body.put("content_type", "text/html");
        body.put("final_url", null);
        body.put("status_code", null);
        body.put("verified", false);
        body.put("content_url", "/c");
        routes.json("GET artifacts/" + ART, 200, body);

        var a = client.getArtifact(ART);

        assertEquals(Artifact.DEGRADED_FETCHED_CONTENT, a.type());
        assertNull(a.finalUrl());
        assertNull(a.statusCode());
        assertFalse(a.verified());
        assertEquals(12L, a.sizeBytes());
    }

    @Test
    void getArtifact_encodesTheId() {
        routes.json("GET artifacts/a%2F1", 200, Map.of("id", "a/1"));
        assertEquals("a/1", client.getArtifact("a/1").id());
        assertEquals(List.of("GET artifacts/a%2F1"), routes.seen);
    }

    @Test
    void downloadArtifact_returnsTheBytes() {
        var html = "<html><body>héllo</body></html>";
        routes.add("GET artifacts/" + ART + "/content", new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .addHeader("Content-Disposition", "attachment; filename=\"example.com-3f2b8c1e.html\"")
                .setBody(html));

        var bytes = client.downloadArtifact(ART);

        assertArrayEquals(html.getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void artifact_notFoundIsA404() {
        routes.json("GET artifacts/" + ART, 404, Map.of("detail", "Artifact not found"));
        routes.json("GET artifacts/" + ART + "/content", 404, Map.of("detail", "Artifact not found"));

        var ex = assertThrows(ApiException.class, () -> client.getArtifact(ART));
        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Artifact not found"), ex.getMessage());
        var ex2 = assertThrows(ApiException.class, () -> client.downloadArtifact(ART));
        assertEquals(404, ex2.getStatusCode());
    }

    @Test
    void artifact_wrongOrgIsA403() {
        routes.json("GET artifacts/" + ART, 403, Map.of("detail", "Forbidden"));
        routes.json("GET artifacts/" + ART + "/content", 403, Map.of("detail", "Forbidden"));

        assertEquals(403, assertThrows(ForbiddenException.class, () -> client.getArtifact(ART)).getStatusCode());
        assertThrows(ForbiddenException.class, () -> client.downloadArtifact(ART));
    }

    @Test
    void artifact_anIdThatIsNotAUuidIsA422() {
        var invalid = Map.of("detail", List.of(Map.of("loc", List.of("path", "artifact_id"),
                "msg", "Input should be a valid UUID", "type", "uuid_parsing")));
        routes.json("GET artifacts/not-a-uuid", 422, invalid);
        routes.json("GET artifacts/not-a-uuid/content", 422, invalid);

        var ex = assertThrows(ApiException.class, () -> client.getArtifact("not-a-uuid"));
        assertEquals(422, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("valid UUID"), ex.getMessage());
        assertEquals(422, assertThrows(ApiException.class, () -> client.downloadArtifact("not-a-uuid")).getStatusCode());
        assertEquals(1, routes.seen.stream().filter(k -> k.equals("GET artifacts/not-a-uuid")).count(),
                "a 422 is not retried");
    }

    // ── 409 cost_acknowledgement_required ────────────────────────────────────

    @Test
    void error409_costAcknowledgementRequired() {
        routes.json("POST webgrph/crawl", 409, Map.of("detail", Map.of("error", "cost_acknowledgement_required",
                "message", "Accept the estimate's cost first.")));

        var ex = assertThrows(CostAcknowledgementRequiredException.class,
                () -> client.webgrph().startCrawl("https://example.com/", CrawlOptions.builder().buildAsSet()));

        assertEquals(409, ex.getStatusCode());
        assertEquals("cost_acknowledgement_required", ex.getCode());
        assertEquals(1, routes.seen.size(), "a 409 is not retried");
    }

    @Test
    void error409_costAcknowledgementRequiredAtTheTopLevel() {
        routes.json("POST webgrph/crawl-extract", 409, Map.of("error", "cost_acknowledgement_required"));

        var ex = assertThrows(CostAcknowledgementRequiredException.class,
                () -> client.webgrph().startCrawlExtract("https://example.com/"));
        assertInstanceOf(ApiException.class, ex);
    }

    @Test
    void error409_otherCodesAreUnchanged() {
        routes.json("POST webgrph/crawl", 409, Map.of("detail", Map.of("error", "concurrent_limit", "concurrent_limit", 2)));
        assertThrows(ConcurrencyLimitException.class,
                () -> client.webgrph().startCrawl("https://example.com/", CrawlOptions.builder().buildAsSet()));
    }
}
