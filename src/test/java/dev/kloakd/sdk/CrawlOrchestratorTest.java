package dev.kloakd.sdk;

import dev.kloakd.sdk.models.CrawlOptions;
import dev.kloakd.sdk.models.CrawlProgressEvent;
import dev.kloakd.sdk.models.SiteCrawlResult;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/** {@code client.crawl} / {@code crawlStream}: Webgrph discovers, Evadr fetches, Kolektr extracts. */
class CrawlOrchestratorTest {

    private MockWebServer server;
    private Kloakd client;
    private Routes routes;

    @BeforeEach
    void setUp() throws Exception {
        server = startServer();
        routes = routes(server);
        client = client(server);
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c1", "url", "https://example.com"));
        routes.json("GET webgrph/crawl/c1", 200, Map.of("crawl_id", "c1", "status", "completed",
                "total_pages", 3, "max_depth_reached", 1, "artifact_id", "art-crawl"));
        routes.json("GET webgrph/crawl/c1/pages", 200, Map.of("crawl_id", "c1", "total", 3,
                "nodes", List.of(node("https://example.com/a"), node("https://example.com/b"), node("https://example.com/c"))));
        // a: fetched; b: fetched but not successful; c: the fetch errors (500).
        routes.json("POST evadr/fetch", 200, Map.of("success", true, "url", "https://example.com/a",
                "status_code", 200, "tier_used", 2, "html", "<h1>A</h1>", "artifact", Map.of("artifact_id", "art-a")));
        routes.json("POST evadr/fetch", 200, Map.of("success", false, "url", "https://example.com/b",
                "status_code", 403, "tier_used", 4, "error", "blocked"));
        routes.json("POST evadr/fetch", 500, Map.of("detail", "fetch crashed"));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private static Map<String, Object> node(String url) {
        return Map.of("id", url, "url", url, "depth", 1, "children_urls", List.of(), "metadata", Map.of());
    }

    @Test
    void crawl_fetchesEveryPageAndExtractsTheSuccessfulOnes() {
        routes.json("POST kolektr/extract", 200, Map.of("success", true, "records", List.of(Map.of("title", "A"))));

        SiteCrawlResult result = client.crawl("https://example.com", CrawlOptions.builder()
                .maxDepth(2).maxPages(10).extractSchema(Map.of("title", "css:h1")).build());

        assertTrue(result.success());
        assertEquals(3, result.totalPagesDiscovered());
        assertEquals(1, result.pagesFetched());
        assertEquals(2, result.pagesFailed());
        assertEquals("art-crawl", result.crawlArtifactId());
        var a = result.pages().get(0);
        assertTrue(a.ok());
        assertEquals(Map.of("title", "A"), a.structuredData());
        assertEquals("art-a", a.artifactId());
        assertEquals("blocked", result.pages().get(1).error());
        assertFalse(result.pages().get(2).ok());
        assertNotNull(result.pages().get(2).error());

        assertEquals(Map.of("url", "https://example.com", "max_depth", 2, "max_pages", 10),
                routes.bodies.get("POST webgrph/crawl"));
        var extract = routes.bodies.get("POST kolektr/extract");
        assertEquals("art-a", extract.get("fetch_artifact_id"));
        assertEquals(Map.of("title", "css:h1"), extract.get("schema_spec"));
        assertEquals(1, routes.seen.stream().filter("POST kolektr/extract"::equals).count(),
                "only the successful page is extracted");
    }

    @Test
    void crawl_aFailedExtractionStillKeepsThePage() {
        routes.json("POST kolektr/extract", 502, Map.of("detail", "extract failed"));

        var result = client.crawl("https://example.com",
                CrawlOptions.builder().extractSchema(Map.of("title", "css:h1")).build());

        assertTrue(result.pages().get(0).ok());
        assertNull(result.pages().get(0).structuredData());
        assertEquals(1, result.pagesFetched());
    }

    @Test
    void crawl_withoutASchemaDoesNotExtract() {
        var result = client.crawl("https://example.com", CrawlOptions.builder().build());

        assertEquals(3, result.pages().size());
        assertFalse(routes.seen.contains("POST kolektr/extract"));
        assertEquals(Map.of("url", "https://example.com", "max_depth", 3, "max_pages", 100),
                routes.bodies.get("POST webgrph/crawl"));
    }

    @Test
    void crawlStream_reportsDiscoveryEachPageAndTheResult() {
        var events = client.crawlStream("https://example.com", CrawlOptions.builder().build()).toList();

        assertEquals(List.of("discovery_complete",
                        "page_fetching", "page_fetched",
                        "page_fetching", "page_failed",
                        "page_fetching", "page_failed",
                        "crawl_complete"),
                events.stream().map(CrawlProgressEvent::type).toList());
        assertEquals(3, events.get(0).metadata().get("pages_found"));
        assertEquals(2, events.get(2).metadata().get("tier_used"));
        assertEquals("blocked", events.get(4).error());
        assertEquals(3, events.get(6).page());
        var result = (SiteCrawlResult) events.get(7).metadata().get("result");
        assertEquals(1, result.pagesFetched());
        assertEquals(2, result.pagesFailed());
    }

    @Test
    void crawlOptions_builderDefaults() {
        var opts = CrawlOptions.builder().build();
        assertEquals(3, opts.maxDepth());
        assertEquals(100, opts.maxPages());
        assertEquals("json", opts.outputFormat());
        assertNull(opts.extractSchema());

        var set = CrawlOptions.builder().maxDepth(1).maxPages(5).outputFormat("markdown")
                .includeExternalLinks(true).sessionArtifactId("sess").build();
        assertEquals("markdown", set.outputFormat());
        assertTrue(set.includeExternalLinks());
        assertEquals("sess", set.sessionArtifactId());
    }
}
