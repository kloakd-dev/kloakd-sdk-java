package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.ConcurrencyLimitException;
import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.CrawlOptions;
import dev.kloakd.sdk.models.ExtractOptions;
import dev.kloakd.sdk.models.ExtractSchema;
import dev.kloakd.sdk.modules.KolektrNamespace;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.6.0: Kolektr item_selector (GAP-API-11), crawl pages limit and
 * include_grouping (GAP-API-9/10), work-slot 409 (GAP-BILL-13).
 * Checked against kobe-scraper 1c74de2b.
 */
class Sdk060Test {

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

    private static final Map<String, Object> RECORDS = Map.of("success", true, "url", "https://books.toscrape.com",
            "method", "level1", "records", List.of(Map.of("title", "A Light in the Attic", "price", "£51.77")),
            "total_records", 1, "pages_scraped", 1);

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentSpec(String route) {
        return (Map<String, Object>) routes.bodies.get(route).get("schema_spec");
    }

    // ── item_selector ─────────────────────────────────────────────────────────

    @Test
    void typedSchemaSendsItemSelectorAndFieldOptions() {
        routes.json("POST kolektr/extract", 200, RECORDS);
        var schema = ExtractSchema.builder()
                .itemSelector("article.product_pod")
                .field("title", "css:h3 a", "title")
                .field("price", "p.price_color")
                .listField("tags", "ul.tags li")
                .field("id", null, "data-id")
                .build();
        var r = client.kolektr().extract("https://books.toscrape.com",
                ExtractOptions.builder().schema(schema.toMap()).build());
        assertEquals(1, r.records().size());
        assertEquals(Map.of(
                "item_selector", "article.product_pod",
                "fields", List.of(
                        Map.of("name", "title", "selector", "h3 a", "attribute", "title"),
                        Map.of("name", "price", "selector", "p.price_color"),
                        Map.of("name", "tags", "selector", "ul.tags li", "type", "list"),
                        Map.of("name", "id", "attribute", "data-id"))),
                sentSpec("POST kolektr/extract"));
    }

    @Test
    void optionsItemSelectorAppliesToTheShorthand() {
        routes.json("POST kolektr/extract", 200, RECORDS);
        client.kolektr().extract("https://books.toscrape.com", ExtractOptions.builder()
                .schema(Map.of("title", "css:h3 a")).itemSelector(" article.product_pod ").build());
        assertEquals(Map.of("item_selector", "article.product_pod",
                "fields", List.of(Map.of("name", "title", "selector", "h3 a"))), sentSpec("POST kolektr/extract"));
    }

    @Test
    void shorthandCarriesItemSelector() {
        var shorthand = new LinkedHashMap<String, Object>();
        shorthand.put("item_selector", "article.product_pod");
        shorthand.put("title", "css:h3 a");
        shorthand.put("link", Map.of("selector", "h3 a", "attribute", "href"));
        assertEquals(Map.of("item_selector", "article.product_pod", "fields", List.of(
                        Map.of("name", "title", "selector", "h3 a"),
                        Map.of("name", "link", "selector", "h3 a", "attribute", "href"))),
                KolektrNamespace.schemaSpec(shorthand));
    }

    @Test
    void extractHtmlSendsItemSelector() {
        routes.json("POST kolektr/extract/html", 200, RECORDS);
        client.kolektr().extractHtml("<article class=x><h3>A</h3></article>", "https://e.com",
                Map.of("title", "h3"), "article.x");
        assertEquals(Map.of("item_selector", "article.x", "fields", List.of(Map.of("name", "title", "selector", "h3"))),
                sentSpec("POST kolektr/extract/html"));

        client.kolektr().extractHtml("<p>", "https://e.com",
                ExtractSchema.builder().itemSelector("li").field("t", null).build().toMap());
        assertEquals(Map.of("item_selector", "li", "fields", List.of(Map.of("name", "t"))),
                sentSpec("POST kolektr/extract/html"));
    }

    @Test
    void withoutItemSelectorTheSpecIsUnchanged() {
        routes.json("POST kolektr/extract", 200, RECORDS);
        client.kolektr().extract("https://e.com", ExtractOptions.builder().schema(Map.of("title", "css:h1")).build());
        assertEquals(Map.of("fields", List.of(Map.of("name", "title", "selector", "h1"))), sentSpec("POST kolektr/extract"));
        var full = Map.of("fields", List.of(Map.of("name", "t", "selector", "h1")), "item_selector", "li");
        assertSame(full, KolektrNamespace.schemaSpec(full));
        // 0.5.0's 7-argument constructor still works.
        assertNull(new ExtractOptions(null, null, null, null, null, null, null).itemSelector());
    }

    @Test
    void itemSelectorIsCheckedBeforeSending() {
        assertThrows(IllegalArgumentException.class, () -> ExtractSchema.builder().itemSelector(" ").field("t", "h1").build());
        assertThrows(IllegalArgumentException.class, () -> ExtractSchema.builder().itemSelector("li").build());
        assertThrows(IllegalArgumentException.class, () -> ExtractSchema.builder().field("t", "h1").maxPages(0).build());
        assertThrows(IllegalArgumentException.class, () -> new ExtractSchema.Field("", "h1"));
        assertThrows(IllegalArgumentException.class, () -> KolektrNamespace.schemaSpec(null, "li"));
        assertThrows(IllegalArgumentException.class, () -> KolektrNamespace.schemaSpec(Map.of("t", "h1"), ""));
        assertTrue(routes.seen.isEmpty());
    }

    @Test
    void typedSchemaCarriesPaginationAndGoesIntoCrawlOptions() {
        var schema = ExtractSchema.builder().field("t", "h1").pagination(true).maxPages(3).outputFormat("json").build();
        assertEquals(Map.of("fields", List.of(Map.of("name", "t", "selector", "h1")), "pagination", true,
                "max_pages", 3, "output_format", "json"), schema.toMap());
        assertEquals(schema.toMap(), CrawlOptions.builder().extractSchema(schema.toMap()).build().extractSchema());
        // 0.5.0 calls with a literal null still compile (no ambiguous overloads).
        assertNull(CrawlOptions.builder().extractSchema(null).build().extractSchema());
        assertNull(ExtractOptions.builder().schema(null).build().schema());
    }

    // ── crawl pages ───────────────────────────────────────────────────────────

    @Test
    void crawlPagesSendsLimitAndIncludeGrouping() throws Exception {
        routes.json("GET webgrph/crawl/c1/pages", 200, Map.of("crawl_id", "c1", "nodes", List.of(), "total", 0));
        client.webgrph().getCrawlPages("c1", "100", 1000, true);
        RecordedRequest req = server.takeRequest();
        assertEquals("1000", req.getRequestUrl().queryParameter("limit"));
        assertEquals("100", req.getRequestUrl().queryParameter("cursor"));
        assertEquals("true", req.getRequestUrl().queryParameter("include_grouping"));

        client.webgrph().getCrawlPages("c1", null, 0);
        req = server.takeRequest();
        assertNull(req.getRequestUrl().queryParameter("limit"));
        assertNull(req.getRequestUrl().queryParameter("include_grouping"));
    }

    @Test
    void crawlPagesLimitIsChecked() {
        assertThrows(IllegalArgumentException.class, () -> client.webgrph().getCrawlPages("c1", null, 1001));
        assertThrows(IllegalArgumentException.class, () -> client.webgrph().getCrawlPages("c1", null, -1, true));
        assertTrue(routes.seen.isEmpty());
    }

    @Test
    void waitForCrawlCanIncludeGroupingFolders() throws Exception {
        routes.json("GET webgrph/crawl/c1", 200, Map.of("crawl_id", "c1", "status", "completed",
                "total_pages", 1, "max_depth_reached", 0));
        routes.json("GET webgrph/crawl/c1/pages", 200, Map.of("crawl_id", "c1", "total", 2, "nodes", List.of(
                Map.of("id", "r", "url", "https://e.com", "depth", 0, "category", "page",
                        "metadata", Map.of("is_grouping", false)),
                Map.of("id", "g", "url", "https://e.com#path-catalogue", "parent_url", "https://e.com",
                        "depth", 1, "category", "group", "metadata", Map.of("is_grouping", true)))));
        var r = client.webgrph().waitForCrawl("c1", Duration.ofMillis(1), Duration.ofSeconds(5), true);
        assertEquals(1, r.totalPages());
        assertEquals(2, r.total());
        assertFalse(r.pages().get(0).isGrouping());
        assertTrue(r.pages().get(1).isGrouping());
        server.takeRequest();
        assertEquals("true", server.takeRequest().getRequestUrl().queryParameter("include_grouping"));
    }

    // ── work slots (409) ──────────────────────────────────────────────────────

    /** A client that retries (the shared test client does not). */
    private Kloakd retrying() {
        return Kloakd.builder().apiKey(TEST_API_KEY).organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString()).maxRetries(3).timeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void workSlot409ExposesActiveRequestsAndIsNotRetried() {
        routes.json("POST kolektr/extract", 409, Map.of("detail", Map.of(
                "error", "concurrent_limit",
                "message", "Your free plan runs 2 jobs at once and all 2 are in use",
                "tier", "free", "concurrent_limit", 2, "active_crawls", 1, "active_requests", 1)));
        var ex = assertThrows(ConcurrencyLimitException.class, () -> retrying().kolektr().extract("https://e.com"));
        assertEquals(2, ex.getConcurrentLimit());
        assertEquals(1, ex.getActiveCrawls());
        assertEquals(1, ex.getActiveRequests());
        assertEquals("free", ex.getTier());
        assertEquals(1, routes.seen.size());
        assertFalse(HttpTransport.isRetryable("GET", ex));
        assertFalse(HttpTransport.isRetryable(409));
    }

    @Test
    void a409FromAGetIsNotRetriedEither() {
        routes.json("GET webgrph/crawl/c1/pages", 409, Map.of("detail", Map.of(
                "error", "concurrent_limit", "message", "busy", "tier", "pro", "concurrent_limit", 10,
                "active_crawls", 0, "active_requests", 10)));
        var ex = assertThrows(ConcurrencyLimitException.class, () -> retrying().webgrph().getCrawlPages("c1", null, 0));
        assertEquals(10, ex.getActiveRequests());
        assertEquals(1, routes.seen.size());
    }

    @Test
    void testKey409HasNoActiveRequests() {
        routes.json("POST kolektr/extract", 409, Map.of("detail", Map.of(
                "error", "concurrent_limit", "message", "Concurrent job limit reached (10).",
                "test_key_id", "tk", "concurrent_limit", 10)));
        var ex = assertThrows(ConcurrencyLimitException.class, () -> client.kolektr().extract("https://e.com"));
        assertNull(ex.getActiveRequests());
        assertNull(ex.getActiveCrawls());
        assertNull(ex.getTier());
        assertNull(new ConcurrencyLimitException("x", 5, 5, "pro").getActiveRequests());
    }
}
