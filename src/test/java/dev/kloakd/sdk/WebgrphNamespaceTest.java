package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.ApiException;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class WebgrphNamespaceTest {

    private MockWebServer server;
    private Kloakd client;
    private Routes routes;

    @BeforeEach
    void setUp() throws Exception {
        server = startServer();
        routes = routes(server);
        client = client(server);
        routes.json("POST webgrph/crawl", 202, Map.of("success", true, "crawl_id", "c1",
                "url", "https://example.com", "total_pages", 0, "max_depth_reached", 0));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void status(String... states) {
        for (String s : states) {
            routes.json("GET webgrph/crawl/c1", 200, Map.of("crawl_id", "c1", "status", s,
                    "organization_id", TEST_ORG_ID, "total_pages", 3, "max_depth_reached", 1, "artifact_id", "art-c1"));
        }
    }

    private static Map<String, Object> node(String url) {
        return Map.of("id", url, "url", url, "depth", 1, "category", "page",
                "children_urls", List.of(), "metadata", Map.of("title", "T " + url));
    }

    @Test
    void crawl_waitsAndCollectsEveryPage() {
        status("queued", "crawling", "completed");
        routes.json("GET webgrph/crawl/c1/pages", 200, Map.of("crawl_id", "c1",
                "nodes", List.of(node("a"), node("b")), "next_cursor", "2", "total", 3));
        routes.json("GET webgrph/crawl/c1/pages", 200, Map.of("crawl_id", "c1",
                "nodes", List.of(node("c")), "total", 3));

        var result = client.webgrph().crawl("https://example.com", 2, 50, false, null,
                Duration.ofMillis(1), Duration.ofSeconds(5));

        assertTrue(result.success());
        assertEquals(3, result.pages().size());
        assertEquals("T a", result.pages().get(0).title());
        assertEquals("art-c1", result.artifactId());
        assertEquals(50, ((Number) routes.bodies.get("POST webgrph/crawl").get("max_pages")).intValue());
    }

    @Test
    void crawl_failedReturnsNoPages() {
        status("failed");

        var result = client.webgrph().crawl("https://example.com", 0, 0, false, null,
                Duration.ofMillis(1), Duration.ofSeconds(5));

        assertFalse(result.success());
        assertEquals("failed", result.status());
        assertTrue(result.pages().isEmpty());
    }

    @Test
    void crawl_timesOut() {
        status("crawling");

        var ex = assertThrows(ApiException.class, () -> client.webgrph().crawl("https://example.com",
                0, 0, false, null, Duration.ofMillis(1), Duration.ofMillis(20)));
        assertEquals("crawl_timeout", ex.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void crawlStream_startsTheCrawlAndStreamsItsEvents() {
        routes.sse("GET webgrph/crawl/c1/events",
                Map.of("pipeline_id", "c1", "event_type", "pipeline_step", "stage_id", "crawl", "status", "in_progress",
                        "message", "Crawling", "progress_pct", 40.0, "sequence", 1, "metadata", Map.of(),
                        "sub_status", "Discovered 15/500 pages (depth 1/3)"),
                Map.of("event_type", "pipeline_complete", "sequence", 2, "crawl_id", "c1", "metadata", Map.of()));

        var events = client.webgrph().crawlStream("https://example.com").toList();

        assertEquals(2, events.size());
        assertEquals("pipeline_step", events.get(0).eventType());
        assertEquals(40.0, events.get(0).progressPct());
        assertEquals("Discovered 15/500 pages (depth 1/3)", events.get(0).subStatus());
        assertEquals("pipeline_complete", events.get(1).eventType());
        assertEquals("c1", events.get(1).pipelineId());
        assertEquals(2, events.get(1).sequence());
    }

    @Test
    void sessions_movedFromSkanyr() {
        routes.json("GET webgrph/sessions", 200, List.of(Map.of("id", "s1")));
        routes.json("PATCH webgrph/sessions/s1/job", 200, Map.of("id", "s1", "last_job_id", "j1"));

        assertEquals(1, ((List<?>) client.webgrph().listSessions(true).get("items")).size());
        assertEquals("j1", client.webgrph().updateSessionJob("s1", "j1").get("last_job_id"));
    }
}
