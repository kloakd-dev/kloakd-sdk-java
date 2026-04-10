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

    @BeforeEach
    void setUp() throws Exception {
        server = startServer();
        client = client(server);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void login_parsesResult() {
        enqueue(server, 200, Map.of(
                "success", true, "session_id", "sess-001",
                "url", "https://example.com/dashboard",
                "artifact_id", "art-sess-001",
                "screenshot_url", "https://cdn.kloakd.dev/screenshot.png"));

        var result = client.fetchyr().login("https://example.com/login",
                "#user", "#pass", "user@example.com", "secret");

        assertTrue(result.success());
        assertEquals("sess-001", result.sessionId());
        assertEquals("art-sess-001", result.artifactId());
    }

    @Test
    void login_withOptions_sendsBody() throws Exception {
        enqueue(server, 200, Map.of("success", true, "session_id", "s"));
        client.fetchyr().login("https://example.com/login", "#u", "#p", "u", "p",
                "#submit", "/dashboard");

        var body = requestBody(server.takeRequest());
        assertEquals("#submit", body.get("submit_selector"));
        assertEquals("/dashboard", body.get("success_url_contains"));
    }

    @Test
    void fetch_parsesResult() {
        enqueue(server, 200, Map.of(
                "success", true, "url", "https://example.com/protected",
                "status_code", 200, "html", "<html/>",
                "session_artifact_id", "art-sess-001"));

        var result = client.fetchyr().fetch("https://example.com/protected", "art-sess-001");

        assertEquals(200, result.statusCode());
        assertEquals("art-sess-001", result.sessionArtifactId());
    }

    @Test
    void createWorkflow_parsesResult() {
        enqueue(server, 200, Map.of(
                "workflow_id", "wf-001", "name", "login_flow",
                "steps", List.of(), "created_at", "2026-04-09T00:00:00Z"));

        var result = client.fetchyr().createWorkflow("login_flow",
                List.of(Map.of("action", "click")), "https://example.com");

        assertEquals("wf-001", result.workflowId());
        assertEquals("login_flow", result.name());
    }

    @Test
    void executeWorkflow_parsesResult() {
        enqueue(server, 200, Map.of(
                "execution_id", "exec-001", "workflow_id", "wf-001",
                "status", "running", "records", List.of()));

        var result = client.fetchyr().executeWorkflow("wf-001");

        assertEquals("running", result.status());
        assertEquals("exec-001", result.executionId());
    }

    @Test
    void getExecution_parsesResult() {
        enqueue(server, 200, Map.of(
                "execution_id", "exec-001", "workflow_id", "wf-001",
                "status", "completed", "records", List.of(Map.of("data", "value"))));

        var result = client.fetchyr().getExecution("wf-001", "exec-001");

        assertEquals("completed", result.status());
        assertEquals(1, result.records().size());
    }

    @Test
    void detectForms_parsesResult() {
        enqueue(server, 200, Map.of(
                "forms", List.of(Map.of("selector", "form#login", "confidence", 0.99)),
                "total_forms", 1));

        var result = client.fetchyr().detectForms("https://example.com/login", null);

        assertEquals(1, result.totalForms());
        assertEquals(1, result.forms().size());
    }

    @Test
    void detectMfa_parsesResult() {
        enqueue(server, 200, Map.of(
                "mfa_detected", true, "challenge_id", "chall-001", "mfa_type", "totp"));

        var result = client.fetchyr().detectMfa("https://example.com/mfa", null);

        assertTrue(result.mfaDetected());
        assertEquals("totp", result.mfaType());
        assertEquals("chall-001", result.challengeId());
    }

    @Test
    void submitMfa_parsesResult() {
        enqueue(server, 200, Map.of("success", true, "session_artifact_id", "art-sess-002"));

        var result = client.fetchyr().submitMfa("chall-001", "123456");

        assertTrue(result.success());
        assertEquals("art-sess-002", result.sessionArtifactId());
    }

    @Test
    void checkDuplicates_parsesResult() {
        enqueue(server, 200, Map.of(
                "unique_records", List.of(Map.of("id", "1")),
                "duplicate_count", 2, "total_input", 3));

        var result = client.fetchyr().checkDuplicates(
                List.of(Map.of("id", "1"), Map.of("id", "2"), Map.of("id", "2")),
                "example.com");

        assertEquals(2, result.duplicateCount());
        assertEquals(3, result.totalInput());
        assertEquals(1, result.uniqueRecords().size());
    }
}
