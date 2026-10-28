package dev.kloakd.sdk;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The pass-through Kolektr and Fetchyr methods: each sends one request, to
 * the route and with the method named here, and returns the API's body.
 * ({@code LiveRoutesTest} checks the source's paths exist; this checks the
 * calls actually made.)
 */
class RouteMappingTest {

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

    private static Arguments call(String route, Consumer<Kloakd> call) {
        return Arguments.of(route, call);
    }

    static Stream<Arguments> calls() {
        return Stream.of(
                call("GET kolektr/api-data/products", c -> c.kolektr().getApiData("products")),
                call("GET kolektr/api-data/products/paginated", c -> c.kolektr().getApiDataPaginated("products", 0, 50)),
                call("POST kolektr/api-data/products/extract-all", c -> c.kolektr().extractAllApiData("products")),
                call("GET kolektr/content", c -> c.kolektr().listContent()),
                call("GET kolektr/content/i1", c -> c.kolektr().getContent("i1")),
                call("DELETE kolektr/content/i1", c -> c.kolektr().deleteContent("i1")),
                call("GET kolektr/jobs", c -> c.kolektr().listJobs()),
                call("GET kolektr/jobs/j1", c -> c.kolektr().getJob("j1")),
                call("GET kolektr/extraction-jobs/j1/status", c -> c.kolektr().getJobStatus("j1")),
                call("GET kolektr/scrapers", c -> c.kolektr().listScrapers()),
                call("POST kolektr/scrapers", c -> c.kolektr().createScraper(Map.of("name", "s"))),
                call("GET kolektr/scrapers/s1", c -> c.kolektr().getScraper("s1")),
                call("PATCH kolektr/scrapers/s1", c -> c.kolektr().updateScraper("s1", Map.of("name", "t"))),
                call("DELETE kolektr/scrapers/s1", c -> c.kolektr().deleteScraper("s1")),
                call("POST fetchyr/auth-chain/discover", c -> c.fetchyr().discoverAuthChain("https://example.com")),
                call("GET fetchyr/auth-chain/d1/status", c -> c.fetchyr().getAuthChainStatus("d1")),
                call("GET fetchyr/workflows/w1/executions/e1", c -> c.fetchyr().getExecution("w1", "e1")),
                call("POST fetchyr/workflows/w1/executions/e1/resume", c -> c.fetchyr().resumeExecution("w1", "e1", null)),
                call("POST fetchyr/account/credentials", c -> c.fetchyr().storeCredentials("site", Map.of("username", "u"))),
                call("GET fetchyr/account/credentials", c -> c.fetchyr().listCredentials()),
                call("DELETE fetchyr/account/credentials/site", c -> c.fetchyr().deleteCredentials("site")),
                call("GET fetchyr/sessions", c -> c.fetchyr().listSessions()),
                call("DELETE fetchyr/sessions/a1", c -> c.fetchyr().terminateSession("a1")),
                call("GET fetchyr/mfa/challenges/ch1", c -> c.fetchyr().getMfaChallenge("ch1")),
                call("GET fetchyr/mfa/statistics/example.com", c -> c.fetchyr().getMfaStatistics("example.com")),
                call("GET fetchyr/workflows", c -> c.fetchyr().listWorkflows()),
                call("GET fetchyr/workflows/w1", c -> c.fetchyr().getWorkflow("w1")),
                call("PATCH fetchyr/workflows/w1", c -> c.fetchyr().updateWorkflow("w1", Map.of("name", "n"))),
                call("DELETE fetchyr/workflows/w1", c -> c.fetchyr().deleteWorkflow("w1")),
                call("GET fetchyr/workflows/w1/statistics", c -> c.fetchyr().getWorkflowStatistics("w1")),
                call("POST fetchyr/multi-site-workflows", c -> c.fetchyr().createMultiSiteWorkflow("m", List.of())),
                call("POST fetchyr/deduplication/sessions", c -> c.fetchyr().createDedupSession(Map.of())),
                call("GET fetchyr/deduplication/sessions/active", c -> c.fetchyr().listDedupSessions()),
                call("GET fetchyr/deduplication/sessions/s1", c -> c.fetchyr().getDedupSession("s1")),
                call("GET fetchyr/deduplication/sessions/s1/statistics", c -> c.fetchyr().getDedupSessionStatistics("s1")),
                call("GET fetchyr/deduplication/statistics/example.com", c -> c.fetchyr().getDedupDomainStatistics("example.com")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calls")
    void eachCallSendsOneRequestToItsRoute(String route, Consumer<Kloakd> call) {
        routes.json(route, 200, Map.of("ok", true));

        call.accept(client);

        assertEquals(List.of(route), routes.seen);
    }

    @Test
    void paginatedApiDataSendsOffsetAndLimit() throws Exception {
        routes.json("GET kolektr/api-data/products/paginated", 200, Map.of("items", List.of()));

        client.kolektr().getApiDataPaginated("products", 100, 50);

        var path = server.takeRequest().getPath();
        assertTrue(path.contains("offset=100") && path.contains("limit=50"), path);
    }

    @Test
    void storeCredentialsSendsTheNameWithTheCredentials() {
        routes.json("POST fetchyr/account/credentials", 201, Map.of("name", "site"));

        client.fetchyr().storeCredentials("site", Map.of("username", "u", "password", "fixture-pass"));

        assertEquals(Map.of("name", "site", "username", "u", "password", "fixture-pass"),
                routes.bodies.get("POST fetchyr/account/credentials"));
    }

    @Test
    void detectMfaAndFillFormSendTheSession() {
        routes.json("POST fetchyr/mfa/detect", 200, Map.of("mfa_detected", true, "challenge_id", "ch1"));
        routes.json("POST fetchyr/form/fill", 200, Map.of("success", true));

        assertEquals("ch1", client.fetchyr().detectMfa("https://example.com", "sess").challengeId());
        client.fetchyr().fillForm("https://example.com", Map.of("q", "x"), "sess");

        assertEquals("sess", routes.bodies.get("POST fetchyr/mfa/detect").get("session_artifact_id"));
        assertEquals(Map.of("url", "https://example.com", "form_data", Map.of("q", "x"), "session_artifact_id", "sess"),
                routes.bodies.get("POST fetchyr/form/fill"));
    }
}
