package dev.kloakd.sdk;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class FetchyrNamespaceTest {

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
    void authenticate_sendsCredentialNameAndTarget() {
        routes.json("POST fetchyr/authenticate", 200, Map.of(
                "success", true, "session_id", "s1", "artifact_id", "art-1",
                "target_domain", "example.com", "auth_method", "form"));

        var result = client.fetchyr().authenticate("my-login", "https://example.com/login");

        assertTrue(result.success());
        assertEquals("art-1", result.artifactId());
        assertEquals("form", result.authMethod());
        assertEquals(Map.of("credential_name", "my-login", "target_url", "https://example.com/login"),
                routes.bodies.get("POST fetchyr/authenticate"));
    }

    @Test
    void createWorkflow_sendsNoUrl_andReadsId() {
        routes.json("POST fetchyr/workflows", 201, Map.of(
                "id", "wf-1", "name", "Login", "workflow_type", "form", "steps", List.of(),
                "is_active", true, "created_at", "2026-10-27T00:00:00Z"));

        var result = client.fetchyr().createWorkflow("Login", List.of(Map.of("action", "click")), null, "form");

        assertEquals("wf-1", result.workflowId());
        assertTrue(result.isActive());
        assertFalse(routes.bodies.get("POST fetchyr/workflows").containsKey("url"));
        assertEquals("form", routes.bodies.get("POST fetchyr/workflows").get("workflow_type"));
    }

    @Test
    void executeWorkflow_sendsTargetUrl() {
        routes.json("POST fetchyr/workflows/wf-1/execute", 202, Map.of(
                "execution_id", "ex-1", "workflow_id", "wf-1", "status", "running",
                "started_at", "2026-10-27T00:00:00Z", "result", Map.of("k", "v")));

        var result = client.fetchyr().executeWorkflow("wf-1", "https://example.com");

        assertEquals("ex-1", result.executionId());
        assertEquals("v", result.result().get("k"));
        assertEquals("https://example.com", routes.bodies.get("POST fetchyr/workflows/wf-1/execute").get("target_url"));
    }

    @Test
    void detectForms_usesTheLiveRouteAndFields() {
        routes.json("POST fetchyr/form/detect", 200, Map.of(
                "url", "https://example.com", "forms_found", 2, "login_form_detected", true,
                "fields", List.of(Map.of("name", "user"))));

        var result = client.fetchyr().detectForms("https://example.com", null);

        assertEquals(2, result.formsFound());
        assertTrue(result.loginFormDetected());
        assertEquals(1, result.fields().size());
    }

    @Test
    void resolveMfa_postsTheResolution() {
        routes.json("POST fetchyr/mfa-queue/ch-1/resolve", 200, Map.of("id", "ch-1", "status", "resolved"));

        var result = client.fetchyr().resolveMfa("ch-1", "123456");

        assertEquals("resolved", result.get("status"));
        assertEquals("123456", routes.bodies.get("POST fetchyr/mfa-queue/ch-1/resolve").get("resolution"));
    }

    @Test
    void checkDuplicate_sendsUrl() {
        routes.json("POST fetchyr/deduplication/check", 200, Map.of(
                "is_duplicate", true, "original_url", "https://example.com/a", "similarity_score", 0.98));

        var result = client.fetchyr().checkDuplicate("https://example.com/b", null, "d1");

        assertTrue(result.isDuplicate());
        assertEquals(0.98, result.similarityScore());
        assertEquals("d1", routes.bodies.get("POST fetchyr/deduplication/check").get("session_id"));
    }

    @Test
    void listMfaChallenges_keepsAnArrayBody() {
        routes.json("GET fetchyr/mfa-queue", 200, List.of(Map.of("id", "ch-1")));

        var result = client.fetchyr().listMfaChallenges();

        assertEquals(1, ((List<?>) result.get("items")).size());
    }
}
