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

    @Test
    void sitesSyncSearch_useTheLiveRoutes() {
        routes.json("GET skanyr/sites", 200, List.of(Map.of("site_id", "s1")));
        routes.json("POST skanyr/sync", 200, Map.of("site_id", "s1", "sync_id", "y1", "policy", "changed"));
        routes.json("POST skanyr/search", 200, Map.of("results", List.of()));
        routes.json("PATCH skanyr/sites/s1", 200, Map.of("site_id", "s1", "policy", "full"));
        routes.json("DELETE skanyr/sites/s1", 200, Map.of("site_id", "s1", "deleted", true));

        assertEquals(1, ((List<?>) client.skanyr().listSites().get("items")).size());
        assertEquals("y1", client.skanyr().sync("s1", null, "changed").get("sync_id"));
        client.skanyr().search("pricing", "s1", 5, 0);
        client.skanyr().setSitePolicy("s1", "full");
        client.skanyr().deleteSite("s1");

        assertEquals("s1", routes.bodies.get("POST skanyr/search").get("site_id"));
        assertEquals("changed", routes.bodies.get("POST skanyr/sync").get("policy"));
        assertEquals(5, routes.seen.size());
    }
}
