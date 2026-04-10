package dev.kloakd.sdk;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class WebgrphNamespaceTest {

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

    Map<String, Object> crawlResponse() {
        return Map.of(
                "success", true, "crawl_id", "c-001", "url", "https://example.com",
                "total_pages", 5, "max_depth_reached", 2,
                "pages", List.of(Map.of("url", "https://example.com", "depth", 0,
                        "title", "Home", "status_code", 200, "children", List.of())),
                "has_more", false, "total", 5, "artifact_id", "art-c-001");
    }

    @Test
    void crawl_parsesResult() {
        enqueue(server, 200, crawlResponse());
        var result = client.webgrph().crawl("https://example.com");

        assertTrue(result.success());
        assertEquals("c-001", result.crawlId());
        assertEquals(5, result.totalPages());
        assertEquals(1, result.pages().size());
        assertEquals("Home", result.pages().get(0).title());
        assertEquals("art-c-001", result.artifactId());
    }

    @Test
    void crawl_sendsOptions() throws Exception {
        enqueue(server, 200, crawlResponse());
        client.webgrph().crawl("https://example.com", 3, 100, false, "sess-001", 0, 0);

        var body = requestBody(server.takeRequest());
        assertEquals(3, ((Number) body.get("max_depth")).intValue());
        assertEquals(100, ((Number) body.get("max_pages")).intValue());
        assertEquals("sess-001", body.get("session_artifact_id"));
    }

    @Test
    void crawlAll_singlePage() {
        enqueue(server, 200, crawlResponse());
        var pages = client.webgrph().crawlAll("https://example.com", 0, 0);
        assertEquals(1, pages.size());
    }

    @Test
    void crawlAll_paginates() {
        enqueue(server, 200, Map.of(
                "success", true, "crawl_id", "c-001", "url", "https://example.com",
                "total_pages", 2, "max_depth_reached", 1,
                "pages", List.of(Map.of("url", "https://example.com/p1", "depth", 0, "children", List.of())),
                "has_more", true, "total", 2));
        enqueue(server, 200, Map.of(
                "success", true, "crawl_id", "c-001", "url", "https://example.com",
                "total_pages", 2, "max_depth_reached", 1,
                "pages", List.of(Map.of("url", "https://example.com/p2", "depth", 1, "children", List.of())),
                "has_more", false, "total", 2));

        var pages = client.webgrph().crawlAll("https://example.com", 0, 0);
        assertEquals(2, pages.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void crawlStream_yieldsEvents() {
        enqueueSse(server,
                Map.of("type", "page_discovered", "url", "https://example.com/a",
                        "depth", 1, "metadata", Map.of()),
                Map.of("type", "complete", "pages_found", 5, "metadata", Map.of()));

        var events = client.webgrph().crawlStream("https://example.com").toList();

        assertEquals(2, events.size());
        assertEquals("page_discovered", events.get(0).type());
        assertEquals("https://example.com/a", events.get(0).url());
        assertEquals(5, events.get(1).pagesFound());
    }

    @Test
    void getHierarchy_urlContainsId() throws Exception {
        enqueue(server, 200, Map.of("artifact_id", "art-c-001"));
        client.webgrph().getHierarchy("art-c-001");

        assertTrue(server.takeRequest().getPath().contains("art-c-001"));
    }

    @Test
    void getJob_urlContainsId() throws Exception {
        enqueue(server, 200, Map.of("status", "running"));
        client.webgrph().getJob("job-001");

        assertTrue(server.takeRequest().getPath().contains("job-001"));
    }
}
