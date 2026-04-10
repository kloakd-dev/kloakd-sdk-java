package dev.kloakd.sdk;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class KolektrNamespaceTest {

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

    Map<String, Object> extractionResponse() {
        return Map.of(
                "success", true, "url", "https://example.com", "method", "l1_css",
                "records", List.of(Map.of("title", "Book A", "price", "$10")),
                "total_records", 1, "pages_scraped", 1,
                "has_more", false, "total", 1, "artifact_id", "art-e-001");
    }

    @Test
    void page_parsesResult() {
        enqueue(server, 200, extractionResponse());
        var result = client.kolektr().page("https://example.com");

        assertTrue(result.success());
        assertEquals("l1_css", result.method());
        assertEquals(1, result.totalRecords());
        assertEquals("art-e-001", result.artifactId());
        assertEquals("Book A", result.records().get(0).get("title"));
    }

    @Test
    void page_withSchema_sendsBody() throws Exception {
        enqueue(server, 200, extractionResponse());
        client.kolektr().page("https://example.com",
                Map.of("title", "css:h3 a"), "art-fetch-001", null, null, 0, 0);

        var body = requestBody(server.takeRequest());
        assertNotNull(body.get("schema"));
        assertEquals("art-fetch-001", body.get("fetch_artifact_id"));
    }

    @Test
    void pageAll_singlePage() {
        enqueue(server, 200, extractionResponse());
        var records = client.kolektr().pageAll("https://example.com", null);
        assertEquals(1, records.size());
        assertEquals("Book A", records.get(0).get("title"));
    }

    @Test
    void pageAll_paginates() {
        enqueue(server, 200, Map.of(
                "success", true, "url", "https://example.com", "method", "l1_css",
                "records", List.of(Map.of("id", "1")),
                "total_records", 2, "pages_scraped", 1,
                "has_more", true, "total", 2));
        enqueue(server, 200, Map.of(
                "success", true, "url", "https://example.com", "method", "l1_css",
                "records", List.of(Map.of("id", "2")),
                "total_records", 2, "pages_scraped", 1,
                "has_more", false, "total", 2));

        var records = client.kolektr().pageAll("https://example.com", null);

        assertEquals(2, records.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void extractHtml_sendsBody() throws Exception {
        enqueue(server, 200, Map.of(
                "success", true, "url", "https://example.com", "method", "l1_css",
                "records", List.of(Map.of("price", "$12")),
                "total_records", 1, "pages_scraped", 0,
                "has_more", false, "total", 1));

        var result = client.kolektr().extractHtml(
                "<html><p class='price'>$12</p></html>", "https://example.com",
                Map.of("price", "css:p.price"));

        assertEquals(1, result.totalRecords());

        var body = requestBody(server.takeRequest());
        assertNotNull(body.get("html"));
        assertEquals("https://example.com", body.get("url"));
        assertNotNull(body.get("schema"));
    }
}
