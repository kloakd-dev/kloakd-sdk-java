package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.modules.EvadrNamespace.*;

/** RPA and Authentication module. Access via {@code client.fetchyr()}. */
public final class FetchyrNamespace {

    private final HttpTransport t;

    public FetchyrNamespace(HttpTransport t) { this.t = t; }

    // ── Session management ────────────────────────────────────────────────────

    public SessionResult login(String url, String usernameSelector, String passwordSelector,
            String username, String password) {
        return login(url, usernameSelector, passwordSelector, username, password, null, null);
    }

    public SessionResult login(String url, String usernameSelector, String passwordSelector,
            String username, String password, String submitSelector, String successUrlContains) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        body.put("username_selector", usernameSelector);
        body.put("password_selector", passwordSelector);
        body.put("username", username);
        body.put("password", password);
        if (submitSelector != null) body.put("submit_selector", submitSelector);
        if (successUrlContains != null) body.put("success_url_contains", successUrlContains);
        return parseSessionResult(t.post("fetchyr/login", body));
    }

    public FetchyrResult fetch(String url, String sessionArtifactId) {
        return fetch(url, sessionArtifactId, null, false);
    }

    public FetchyrResult fetch(String url, String sessionArtifactId,
            String waitForSelector, boolean extractHtml) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        body.put("session_artifact_id", sessionArtifactId);
        if (waitForSelector != null) body.put("wait_for_selector", waitForSelector);
        if (extractHtml) body.put("extract_html", true);
        return parseFetchyrResult(t.post("fetchyr/fetch", body));
    }

    // ── Workflow automation ───────────────────────────────────────────────────

    public WorkflowResult createWorkflow(String name, List<Map<String, Object>> steps, String url) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("steps", steps);
        if (url != null) body.put("url", url);
        return parseWorkflowResult(t.post("fetchyr/workflows", body));
    }

    public ExecutionResult executeWorkflow(String workflowId) {
        return parseExecutionResult(t.post("fetchyr/workflows/" + workflowId + "/execute", Map.of()));
    }

    public ExecutionResult getExecution(String workflowId, String executionId) {
        return parseExecutionResult(t.get(
                "fetchyr/workflows/" + workflowId + "/executions/" + executionId, null));
    }

    // ── Form detection ────────────────────────────────────────────────────────

    public FormDetectionResult detectForms(String url, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return parseFormDetectionResult(t.post("fetchyr/forms/detect", body));
    }

    // ── MFA handling ──────────────────────────────────────────────────────────

    public MfaDetectionResult detectMfa(String url, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return parseMfaDetectionResult(t.post("fetchyr/mfa/detect", body));
    }

    public MfaResult submitMfa(String challengeId, String code) {
        return parseMfaResult(t.post("fetchyr/mfa/submit",
                Map.of("challenge_id", challengeId, "code", code)));
    }

    // ── Credentials ──────────────────────────────────────────────────────────

    public Map<String, Object> storeCredentials(String name, Map<String, Object> credentials) {
        var body = new HashMap<>(credentials);
        body.put("name", name);
        return t.post("fetchyr/account/credentials", body);
    }

    public Map<String, Object> listCredentials() { return t.get("fetchyr/account/credentials", null); }

    public void deleteCredentials(String name) { t.delete("fetchyr/account/credentials/" + name); }

    // ── Session management ───────────────────────────────────────────────────

    public Map<String, Object> listSessions() { return t.get("fetchyr/sessions", null); }

    public void terminateSession(String artifactId) { t.delete("fetchyr/sessions/" + artifactId); }

    // ── Form filling ─────────────────────────────────────────────────────────

    public Map<String, Object> fillForm(String url, Map<String, String> formData, String sessionArtifactId, boolean submit) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        body.put("form_data", formData);
        body.put("submit", submit);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return t.post("fetchyr/form/fill", body);
    }

    // ── MFA queue and statistics ─────────────────────────────────────────────

    public Map<String, Object> listMfaChallenges() { return t.get("fetchyr/mfa-queue", null); }
    public Map<String, Object> getMfaChallenge(String challengeId) { return t.get("fetchyr/mfa/challenges/" + challengeId, null); }
    public Map<String, Object> getMfaStatistics(String domain) { return t.get("fetchyr/mfa/statistics/" + domain, null); }

    // ── Workflow CRUD ────────────────────────────────────────────────────────

    public Map<String, Object> listWorkflows() { return t.get("fetchyr/workflows", null); }
    public Map<String, Object> getWorkflow(String workflowId) { return t.get("fetchyr/workflows/" + workflowId, null); }
    public Map<String, Object> updateWorkflow(String workflowId, Map<String, Object> updates) { return t.patch("fetchyr/workflows/" + workflowId, updates); }
    public void deleteWorkflow(String workflowId) { t.delete("fetchyr/workflows/" + workflowId); }
    public Map<String, Object> getWorkflowStatistics(String workflowId) { return t.get("fetchyr/workflows/" + workflowId + "/statistics", null); }

    // ── Multi-site ───────────────────────────────────────────────────────────

    public Map<String, Object> createMultiSiteWorkflow(List<Map<String, Object>> sites, String name) {
        var body = new HashMap<String, Object>();
        body.put("sites", sites);
        if (name != null) body.put("name", name);
        return t.post("fetchyr/multi-site-workflows", body);
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    public DeduplicationResult checkDuplicates(List<Map<String, Object>> records, String domain) {
        var body = new HashMap<String, Object>();
        body.put("records", records);
        if (domain != null) body.put("domain", domain);
        return parseDeduplicationResult(t.post("fetchyr/deduplication/check", body));
    }

    public Map<String, Object> createDedupSession(Map<String, Object> config) { return t.post("fetchyr/deduplication/sessions", config); }
    public Map<String, Object> listDedupSessions() { return t.get("fetchyr/deduplication/sessions/active", null); }
    public Map<String, Object> getDedupSession(String sessionId) { return t.get("fetchyr/deduplication/sessions/" + sessionId, null); }
    public Map<String, Object> getDedupSessionStatistics(String sessionId) { return t.get("fetchyr/deduplication/sessions/" + sessionId + "/statistics", null); }
    public Map<String, Object> getDedupDomainStatistics(String domain) { return t.get("fetchyr/deduplication/statistics/" + domain, null); }

    // ── parsers ───────────────────────────────────────────────────────────────

    static SessionResult parseSessionResult(Map<String, Object> m) {
        return new SessionResult(bool(m, "success"), str(m, "session_id"), str(m, "url"),
                str(m, "artifact_id"), str(m, "screenshot_url"), str(m, "error"));
    }

    static FetchyrResult parseFetchyrResult(Map<String, Object> m) {
        return new FetchyrResult(bool(m, "success"), str(m, "url"), integer(m, "status_code"),
                str(m, "html"), str(m, "artifact_id"), str(m, "session_artifact_id"), str(m, "error"));
    }

    static WorkflowResult parseWorkflowResult(Map<String, Object> m) {
        return new WorkflowResult(str(m, "workflow_id"), str(m, "name"),
                mapList(m, "steps"), str(m, "url"), str(m, "created_at"), str(m, "error"));
    }

    static ExecutionResult parseExecutionResult(Map<String, Object> m) {
        return new ExecutionResult(str(m, "execution_id"), str(m, "workflow_id"),
                str(m, "status"), str(m, "started_at"), str(m, "completed_at"),
                mapList(m, "records"), str(m, "error"));
    }

    static FormDetectionResult parseFormDetectionResult(Map<String, Object> m) {
        return new FormDetectionResult(mapList(m, "forms"), integer(m, "total_forms"), str(m, "error"));
    }

    static MfaDetectionResult parseMfaDetectionResult(Map<String, Object> m) {
        return new MfaDetectionResult(bool(m, "mfa_detected"), str(m, "challenge_id"),
                str(m, "mfa_type"), str(m, "error"));
    }

    static MfaResult parseMfaResult(Map<String, Object> m) {
        return new MfaResult(bool(m, "success"), str(m, "session_artifact_id"), str(m, "error"));
    }

    static DeduplicationResult parseDeduplicationResult(Map<String, Object> m) {
        return new DeduplicationResult(mapList(m, "unique_records"),
                integer(m, "duplicate_count"), integer(m, "total_input"), str(m, "error"));
    }
}
