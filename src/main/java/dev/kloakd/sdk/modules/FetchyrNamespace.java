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
     * was removed: the API no longer takes selectors or a password per call. The API answers within about
     * 85 s; a request still running then is handed off as a job (HTTP 202),
     * which this follows to its result with the client's poll settings
     * ({@link dev.kloakd.sdk.Kloakd.Builder#pollInterval}); a failed or expired
     * job throws {@link dev.kloakd.sdk.errors.JobFailedException}, one still
     * running at the poll timeout {@link dev.kloakd.sdk.errors.JobTimeoutException}.
     */
    public SessionResult authenticate(String credentialName, String targetUrl) {
        return parseSessionResult(t.postFollowing("fetchyr/authenticate",
                Map.of("credential_name", credentialName, "target_url", targetUrl)));
    }

    /** Start discovering how a site authenticates. */
    public Map<String, Object> discoverAuthChain(String targetUrl) {
        return t.post("fetchyr/auth-chain/discover", Map.of("target_url", targetUrl));
    }

    /**
     * Give a paused auth-chain discovery the credentials it asked for
     * ({@code POST fetchyr/auth-chain/{id}/resume-credentials}). With
     * {@code remember} the API stores them encrypted for later runs.
     */
    public Map<String, Object> resumeAuthChainCredentials(String discoveryId, String username, String password,
                                                          boolean remember) {
        var body = new HashMap<String, Object>();
        body.put("username", username);
        body.put("password", password);
        body.put("remember", remember);
        return t.post("fetchyr/auth-chain/" + seg(discoveryId) + "/resume-credentials", body);
    }

    /** Give a paused auth-chain discovery its MFA code ({@code POST fetchyr/auth-chain/{id}/resume-mfa}). */
    public Map<String, Object> resumeAuthChainMfa(String discoveryId, String mfaCode) {
        return t.post("fetchyr/auth-chain/" + seg(discoveryId) + "/resume-mfa", Map.of("mfa_code", mfaCode));
    }

    /** An auth-chain discovery's status. */
    public Map<String, Object> getAuthChainStatus(String discoveryId) {
        return t.get("fetchyr/auth-chain/" + seg(discoveryId) + "/status", null);
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
        return parseExecutionResult(t.post("fetchyr/workflows/" + seg(workflowId) + "/execute", body));
    }

    public ExecutionResult getExecution(String workflowId, String executionId) {
        return parseExecutionResult(t.get(
                "fetchyr/workflows/" + seg(workflowId) + "/executions/" + seg(executionId), null));
    }

    /** Resume a paused execution (after MFA): {@code mfa_code} and/or {@code resolution}. */
    public ExecutionResult resumeExecution(String workflowId, String executionId, Map<String, Object> resume) {
        return parseExecutionResult(t.post(
                "fetchyr/workflows/" + seg(workflowId) + "/executions/" + seg(executionId) + "/resume",
                resume != null ? resume : Map.of()));
    }

    // ── Form detection ────────────────────────────────────────────────────────

    /** Detect the forms on a page ({@code POST fetchyr/form/detect}). The API answers within about
     * 85 s; a request still running then is handed off as a job (HTTP 202),
     * which this follows to its result with the client's poll settings
     * ({@link dev.kloakd.sdk.Kloakd.Builder#pollInterval}); a failed or expired
     * job throws {@link dev.kloakd.sdk.errors.JobFailedException}, one still
     * running at the poll timeout {@link dev.kloakd.sdk.errors.JobTimeoutException}. */
    public FormDetectionResult detectForms(String url, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return parseFormDetectionResult(t.postFollowing("fetchyr/form/detect", body));
    }

    // ── MFA handling ──────────────────────────────────────────────────────────

    /** Detect an MFA challenge on a page ({@code POST fetchyr/mfa/detect}). The API answers within about
     * 85 s; a request still running then is handed off as a job (HTTP 202),
     * which this follows to its result with the client's poll settings
     * ({@link dev.kloakd.sdk.Kloakd.Builder#pollInterval}); a failed or expired
     * job throws {@link dev.kloakd.sdk.errors.JobFailedException}, one still
     * running at the poll timeout {@link dev.kloakd.sdk.errors.JobTimeoutException}. */
    public MfaDetectionResult detectMfa(String url, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return parseMfaDetectionResult(t.postFollowing("fetchyr/mfa/detect", body));
    }

    /**
     * Resolve a queued MFA challenge (the code, or "approved"). Replaces 0.2's
     * {@code submitMfa}, whose route was removed.
     */
    public Map<String, Object> resolveMfa(String challengeId, String resolution) {
        return t.post("fetchyr/mfa-queue/" + seg(challengeId) + "/resolve", Map.of("resolution", resolution));
    }

    // ── Credentials ──────────────────────────────────────────────────────────

    public Map<String, Object> storeCredentials(String name, Map<String, Object> credentials) {
        var body = new HashMap<>(credentials);
        body.put("name", name);
        return t.post("fetchyr/account/credentials", body);
    }

    public Map<String, Object> listCredentials() { return t.get("fetchyr/account/credentials", null); }

    public void deleteCredentials(String name) { t.delete("fetchyr/account/credentials/" + seg(name)); }

    // ── Session management ───────────────────────────────────────────────────

    public Map<String, Object> listSessions() { return t.get("fetchyr/sessions", null); }

    public void terminateSession(String artifactId) { t.delete("fetchyr/sessions/" + seg(artifactId)); }

    // ── Form filling ─────────────────────────────────────────────────────────

    /** Fill and submit a form ({@code POST fetchyr/form/fill}). The API answers within about
     * 85 s; a request still running then is handed off as a job (HTTP 202),
     * which this follows to its result with the client's poll settings
     * ({@link dev.kloakd.sdk.Kloakd.Builder#pollInterval}); a failed or expired
     * job throws {@link dev.kloakd.sdk.errors.JobFailedException}, one still
     * running at the poll timeout {@link dev.kloakd.sdk.errors.JobTimeoutException}. */
    public Map<String, Object> fillForm(String url, Map<String, String> formData, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        body.put("form_data", formData);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return t.postFollowing("fetchyr/form/fill", body);
    }

    // ── MFA queue and statistics ─────────────────────────────────────────────

    public Map<String, Object> listMfaChallenges() { return t.get("fetchyr/mfa-queue", null); }
    public Map<String, Object> getMfaChallenge(String challengeId) { return t.get("fetchyr/mfa/challenges/" + seg(challengeId), null); }
    public Map<String, Object> getMfaStatistics(String domain) { return t.get("fetchyr/mfa/statistics/" + seg(domain), null); }

    // ── Workflow CRUD ────────────────────────────────────────────────────────

    public Map<String, Object> listWorkflows() { return listWorkflows(0, 0); }

    /** RPA workflows (page/pageSize 0 for the defaults, 1 and 20). */
    public Map<String, Object> listWorkflows(int page, int pageSize) {
        return t.get("fetchyr/workflows", KolektrNamespace.paging("page", page, "page_size", pageSize));
    }
    public Map<String, Object> getWorkflow(String workflowId) { return t.get("fetchyr/workflows/" + seg(workflowId), null); }
    public Map<String, Object> updateWorkflow(String workflowId, Map<String, Object> updates) { return t.patch("fetchyr/workflows/" + seg(workflowId), updates); }
    public void deleteWorkflow(String workflowId) { t.delete("fetchyr/workflows/" + seg(workflowId)); }
    public Map<String, Object> getWorkflowStatistics(String workflowId) { return t.get("fetchyr/workflows/" + seg(workflowId) + "/statistics", null); }

    // ── Multi-site ───────────────────────────────────────────────────────────

    public Map<String, Object> createMultiSiteWorkflow(String name, List<Map<String, Object>> sites) {
        return createMultiSiteWorkflow(name, sites, null, null, null);
    }

    /**
     * Create a multi-site workflow ({@code POST fetchyr/multi-site-workflows}).
     *
     * @param sites        each {@code {site_id, name, url, authentication_config?, workflow_steps?, ...}}
     * @param description  or null
     * @param dependencies each {@code {dependent_site, dependency_site, dependency_type?}}
     *                     ({@code sequential}, {@code data} or {@code conditional}), or null
     * @param globalConfig workflow-wide configuration, or null
     */
    public Map<String, Object> createMultiSiteWorkflow(String name, List<Map<String, Object>> sites,
                                                       String description, List<Map<String, Object>> dependencies,
                                                       Map<String, Object> globalConfig) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("sites", sites);
        if (description != null) body.put("description", description);
        if (dependencies != null && !dependencies.isEmpty()) body.put("dependencies", dependencies);
        if (globalConfig != null) body.put("global_config", globalConfig);
        return t.post("fetchyr/multi-site-workflows", body);
    }

    /** One multi-site dependency: {@code dependent} runs after {@code dependency}. */
    public static Map<String, Object> siteDependency(String dependent, String dependency, String dependencyType) {
        var d = new HashMap<String, Object>();
        d.put("dependent_site", dependent);
        d.put("dependency_site", dependency);
        if (dependencyType != null) d.put("dependency_type", dependencyType);
        return d;
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
    public Map<String, Object> getDedupSession(String sessionId) { return t.get("fetchyr/deduplication/sessions/" + seg(sessionId), null); }
    public Map<String, Object> getDedupSessionStatistics(String sessionId) { return t.get("fetchyr/deduplication/sessions/" + seg(sessionId) + "/statistics", null); }
    public Map<String, Object> getDedupDomainStatistics(String domain) { return t.get("fetchyr/deduplication/statistics/" + seg(domain), null); }

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
