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
    private Routes routes;

    private static final Map<String, Object> EXTRACTION = Map.of(
            "success", true, "url", "https://example.com", "method", "l1_css",
            "records", List.of(Map.of("title", "Book A")), "total_records", 1, "pages_scraped", 1,
            "artifact", Map.of("artifact_id", "art-ext-1"));

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

    @Test
    void extract_sendsSchemaSpec() {
        routes.json("POST kolektr/extract", 200, EXTRACTION);

        var result = client.kolektr().extract("https://example.com",
                Map.of("title", "css:h3"), "art-fetch-1", null, null);

        assertEquals(1, result.totalRecords());
        assertEquals("art-ext-1", result.artifactId());
        var body = routes.bodies.get("POST kolektr/extract");
        assertNotNull(body.get("schema_spec"));
        assertFalse(body.containsKey("schema"));
        assertEquals("art-fetch-1", body.get("fetch_artifact_id"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void page_isExtract() {
        routes.json("POST kolektr/extract", 200, EXTRACTION);

        client.kolektr().page("https://example.com");

        assertEquals(List.of("POST kolektr/extract"), routes.seen);
    }

    @Test
    void extractHtml_usesTheLiveRoute() {
        routes.json("POST kolektr/extract/html", 200, EXTRACTION);

        var result = client.kolektr().extractHtml("<html/>", "https://example.com", Map.of("title", "css:h1"));

        assertTrue(result.success());
        assertNotNull(routes.bodies.get("POST kolektr/extract/html").get("schema_spec"));
    }
}
