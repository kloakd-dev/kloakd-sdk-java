package dev.kloakd.sdk;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class SkanyrNamespaceTest {

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

    Map<String, Object> discoverResponse() {
        return Map.of(
                "success", true, "discovery_id", "d-001",
                "url", "https://api.example.com", "total_endpoints", 2,
                "endpoints", List.of(
                        Map.of("url", "https://api.example.com/users",
                                "method", "GET", "api_type", "rest",
                                "confidence", 0.98, "parameters", Map.of())),
                "has_more", false, "total", 2, "artifact_id", "art-d-001");
    }

    @Test
    void discover_parsesResult() {
        enqueue(server, 200, discoverResponse());
        var result = client.skanyr().discover("https://api.example.com");

        assertTrue(result.success());
        assertEquals("d-001", result.discoveryId());
        assertEquals(2, result.totalEndpoints());
        assertEquals(1, result.endpoints().size());
        assertEquals("rest", result.endpoints().get(0).apiType());
    }

    @Test
    void discover_sendsSiteHierarchyArtifact() throws Exception {
        enqueue(server, 200, discoverResponse());
        client.skanyr().discover("https://api.example.com", "hier-001", 0, null, 0, 0);

        assertEquals("hier-001", requestBody(server.takeRequest()).get("site_hierarchy_artifact_id"));
    }

    @Test
    void discoverAll_singlePage() {
        enqueue(server, 200, discoverResponse());
        var endpoints = client.skanyr().discoverAll("https://api.example.com");
        assertEquals(1, endpoints.size());
    }

    @Test
    void discoverStream_yieldsEvents() {
        enqueueSse(server,
                Map.of("type", "endpoint_found", "endpoint_url", "https://api.example.com/v1",
                        "api_type", "rest", "metadata", Map.of()),
                Map.of("type", "complete", "metadata", Map.of()));

        var events = client.skanyr().discoverStream("https://api.example.com").toList();

        assertEquals(2, events.size());
        assertEquals("endpoint_found", events.get(0).type());
        assertEquals("https://api.example.com/v1", events.get(0).endpointUrl());
    }

    @Test
    void discoverStream_sendsSiteHierarchyArtifact() throws Exception {
        enqueueSse(server, Map.of("type", "complete", "metadata", Map.of()));
        client.skanyr().discoverStream("https://api.example.com", "hier-001", 0).toList();

        assertEquals("hier-001", requestBody(server.takeRequest()).get("site_hierarchy_artifact_id"));
    }

    @Test
    void getApiMap_urlContainsId() throws Exception {
        enqueue(server, 200, Map.of("artifact_id", "art-d-001"));
        client.skanyr().getApiMap("art-d-001");
        assertTrue(server.takeRequest().getPath().contains("art-d-001"));
    }

    @Test
    void getJob_urlContainsId() throws Exception {
        enqueue(server, 200, Map.of("status", "completed"));
        client.skanyr().getJob("job-d-001");
        assertTrue(server.takeRequest().getPath().contains("job-d-001"));
    }
}
