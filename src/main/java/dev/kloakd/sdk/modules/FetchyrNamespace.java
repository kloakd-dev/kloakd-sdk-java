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

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Log in to a site with credentials stored under {@code credentialName}
     * ({@link #storeCredentials}). Replaces 0.2's {@code login}, whose route
     * was removed: the API no longer takes selectors or a password per call.
     */
    public SessionResult authenticate(String credentialName, String targetUrl) {
        return parseSessionResult(t.post("fetchyr/authenticate",
                Map.of("credential_name", credentialName, "target_url", targetUrl)));
    }

    /** Start discovering how a site authenticates. */
    public Map<String, Object> discoverAuthChain(String targetUrl) {
        return t.post("fetchyr/auth-chain/discover", Map.of("target_url", targetUrl));
    }

    /** An auth-chain discovery's status. */
    public Map<String, Object> getAuthChainStatus(String discoveryId) {
        return t.get("fetchyr/auth-chain/" + discoveryId + "/status", null);
    }

    // ── Workflow automation ───────────────────────────────────────────────────

    public WorkflowResult createWorkflow(String name, List<Map<String, Object>> steps) {
        return createWorkflow(name, steps, null, null);
    }

    public WorkflowResult createWorkflow(String name, List<Map<String, Object>> steps,
                                         String description, String workflowType) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("steps", steps);
        if (description != null) body.put("description", description);
        if (workflowType != null) body.put("workflow_type", workflowType);
        return parseWorkflowResult(t.post("fetchyr/workflows", body));
    }

    public ExecutionResult executeWorkflow(String workflowId, String targetUrl) {
        return executeWorkflow(workflowId, targetUrl, null, null);
    }

    public ExecutionResult executeWorkflow(String workflowId, String targetUrl,
                                           String sessionArtifactId, Map<String, Object> inputData) {
        var body = new HashMap<String, Object>();
        body.put("target_url", targetUrl);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        if (inputData != null && !inputData.isEmpty()) body.put("input_data", inputData);
        return parseExecutionResult(t.post("fetchyr/workflows/" + workflowId + "/execute", body));
    }

    public ExecutionResult getExecution(String workflowId, String executionId) {
        return parseExecutionResult(t.get(
                "fetchyr/workflows/" + workflowId + "/executions/" + executionId, null));
    }

    /** Resume a paused execution (after MFA): {@code mfa_code} and/or {@code resolution}. */
    public ExecutionResult resumeExecution(String workflowId, String executionId, Map<String, Object> resume) {
        return parseExecutionResult(t.post(
                "fetchyr/workflows/" + workflowId + "/executions/" + executionId + "/resume",
                resume != null ? resume : Map.of()));
    }

    // ── Form detection ────────────────────────────────────────────────────────

    public FormDetectionResult detectForms(String url, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return parseFormDetectionResult(t.post("fetchyr/form/detect", body));
    }

    // ── MFA handling ──────────────────────────────────────────────────────────

    public MfaDetectionResult detectMfa(String url, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return parseMfaDetectionResult(t.post("fetchyr/mfa/detect", body));
    }

    /**
     * Resolve a queued MFA challenge (the code, or "approved"). Replaces 0.2's
     * {@code submitMfa}, whose route was removed.
     */
    public Map<String, Object> resolveMfa(String challengeId, String resolution) {
        return t.post("fetchyr/mfa-queue/" + challengeId + "/resolve", Map.of("resolution", resolution));
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

    public Map<String, Object> fillForm(String url, Map<String, String> formData, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        body.put("form_data", formData);
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

    public Map<String, Object> createMultiSiteWorkflow(String name, List<Map<String, Object>> sites) {
        return t.post("fetchyr/multi-site-workflows", Map.of("name", name, "sites", sites));
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    /** Whether a URL (or its content hash) was already seen, within a session if given. */
    public DeduplicationResult checkDuplicate(String url, String contentHash, String sessionId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (contentHash != null) body.put("content_hash", contentHash);
        if (sessionId != null) body.put("session_id", sessionId);
        return parseDeduplicationResult(t.post("fetchyr/deduplication/check", body));
    }

    public Map<String, Object> createDedupSession(Map<String, Object> config) { return t.post("fetchyr/deduplication/sessions", config); }
    public Map<String, Object> listDedupSessions() { return t.get("fetchyr/deduplication/sessions/active", null); }
    public Map<String, Object> getDedupSession(String sessionId) { return t.get("fetchyr/deduplication/sessions/" + sessionId, null); }
    public Map<String, Object> getDedupSessionStatistics(String sessionId) { return t.get("fetchyr/deduplication/sessions/" + sessionId + "/statistics", null); }
    public Map<String, Object> getDedupDomainStatistics(String domain) { return t.get("fetchyr/deduplication/statistics/" + domain, null); }

    // ── parsers ───────────────────────────────────────────────────────────────

    static SessionResult parseSessionResult(Map<String, Object> m) {
        return new SessionResult(bool(m, "success"), str(m, "session_id"), str(m, "artifact_id"),
                str(m, "target_domain"), str(m, "auth_method"), str(m, "error"));
    }

    static WorkflowResult parseWorkflowResult(Map<String, Object> m) {
        return new WorkflowResult(str(m, "id"), str(m, "name"), str(m, "description"),
                str(m, "workflow_type"), mapList(m, "steps"), bool(m, "is_active"), str(m, "created_at"));
    }

    @SuppressWarnings("unchecked")
    static ExecutionResult parseExecutionResult(Map<String, Object> m) {
        Object result = m.get("result");
        return new ExecutionResult(str(m, "execution_id"), str(m, "workflow_id"),
                str(m, "status"), str(m, "started_at"), str(m, "completed_at"),
                result instanceof Map<?, ?> r ? (Map<String, Object>) r : Map.of(), str(m, "error"));
    }

    static FormDetectionResult parseFormDetectionResult(Map<String, Object> m) {
        return new FormDetectionResult(str(m, "url"), integer(m, "forms_found"),
                bool(m, "login_form_detected"), mapList(m, "fields"));
    }

    static MfaDetectionResult parseMfaDetectionResult(Map<String, Object> m) {
        return new MfaDetectionResult(bool(m, "mfa_detected"), str(m, "challenge_id"),
                str(m, "mfa_type"), str(m, "message"));
    }

    static DeduplicationResult parseDeduplicationResult(Map<String, Object> m) {
        return new DeduplicationResult(bool(m, "is_duplicate"), str(m, "original_url"),
                dbl(m, "similarity_score"), str(m, "match_type"));
    }
}
