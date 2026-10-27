package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.util.HashMap;
import java.util.Map;

import static dev.kloakd.sdk.modules.EvadrNamespace.*;

/** Strategy Engine module (5-layer cognitive pipeline). Access via {@code client.nexus()}. */
public final class NexusNamespace {

    private final HttpTransport t;

    public NexusNamespace(HttpTransport t) { this.t = t; }

    /**
     * Classify a page and propose a strategy. The API needs the page's HTML. The API answers within about
     * 85 s; a request still running then is handed off as a job (HTTP 202),
     * which this follows to its result with the client's poll settings
     * ({@link dev.kloakd.sdk.Kloakd.Builder#pollInterval}); a failed or expired
     * job throws {@link dev.kloakd.sdk.errors.JobFailedException}, one still
     * running at the poll timeout {@link dev.kloakd.sdk.errors.JobTimeoutException}.
     */
    public NexusAnalyzeResult analyze(String url, String html) {
        return analyze(url, html, null);
    }

    public NexusAnalyzeResult analyze(String url, String html, Map<String, Object> constraints) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        body.put("html", html);
        if (constraints != null && !constraints.isEmpty()) body.put("constraints", constraints);
        return parseNexusAnalyzeResult(t.postFollowing("nexus/analyze", body));
    }

    /** Generate an extraction program for a page and a goal. The API answers within about
     * 85 s; a request still running then is handed off as a job (HTTP 202),
     * which this follows to its result with the client's poll settings
     * ({@link dev.kloakd.sdk.Kloakd.Builder#pollInterval}); a failed or expired
     * job throws {@link dev.kloakd.sdk.errors.JobFailedException}, one still
     * running at the poll timeout {@link dev.kloakd.sdk.errors.JobTimeoutException}. */
    public NexusSynthesisResult synthesize(String url, String html, String goal) {
        return parseNexusSynthesisResult(t.postFollowing("nexus/synthesize", Map.of("url", url, "html", html, "goal", goal)));
    }

    public NexusVerifyResult verify(String strategyId) {
        return parseNexusVerifyResult(t.post("nexus/verify", Map.of("strategy_id", strategyId)));
    }

    public NexusExecuteResult execute(String strategyId, String url) {
        return execute(strategyId, url, null);
    }

    /**
     * Run a verified strategy on a URL and return the extracted records. The
     * result's {@link NexusExecuteResult#executionResultId()} is the id to pass
     * to {@link #knowledge(String)}. The API answers within about
     * 85 s; a request still running then is handed off as a job (HTTP 202),
     * which this follows to its result with the client's poll settings
     * ({@link dev.kloakd.sdk.Kloakd.Builder#pollInterval}); a failed or expired
     * job throws {@link dev.kloakd.sdk.errors.JobFailedException}, one still
     * running at the poll timeout {@link dev.kloakd.sdk.errors.JobTimeoutException}.
     */
    public NexusExecuteResult execute(String strategyId, String url, Map<String, Object> options) {
        var body = new HashMap<String, Object>();
        body.put("strategy_id", strategyId);
        body.put("url", url);
        if (options != null && !options.isEmpty()) body.put("options", options);
        return parseNexusExecuteResult(t.postFollowing("nexus/execute", body));
    }

    /**
     * Learn concepts and patterns from a prior execution.
     *
     * @param executionResultId {@link NexusExecuteResult#executionResultId()} from
     *                          {@link #execute(String, String, Map)}'s result
     */
    public NexusKnowledgeResult knowledge(String executionResultId) {
        return parseNexusKnowledgeResult(t.post("nexus/knowledge",
                Map.of("execution_result_id", executionResultId)));
    }

    // ── Reason ───────────────────────────────────────────────────────────────

    public Map<String, Object> reason(Map<String, Object> context) { return t.post("nexus/reason", context); }

    // ── parsers ──────────────────────────────────────────────────────────────

    static NexusAnalyzeResult parseNexusAnalyzeResult(Map<String, Object> m) {
        return new NexusAnalyzeResult(str(m, "url"), str(m, "perception_id"), str(m, "reasoning_id"),
                mapField(m, "strategy"), str(m, "page_type"), dbl(m, "confidence"), nestedArtifactId(m));
    }

    static NexusSynthesisResult parseNexusSynthesisResult(Map<String, Object> m) {
        return new NexusSynthesisResult(str(m, "strategy_id"), str(m, "strategy_name"),
                str(m, "strategy_type"), str(m, "program"), dbl(m, "confidence"),
                mapList(m, "steps"), integer(m, "synthesis_time_ms"));
    }

    static NexusVerifyResult parseNexusVerifyResult(Map<String, Object> m) {
        return new NexusVerifyResult(str(m, "strategy_id"), bool(m, "verified"),
                dbl(m, "safety_score"), strList(m, "violations"));
    }

    static NexusExecuteResult parseNexusExecuteResult(Map<String, Object> m) {
        return new NexusExecuteResult(str(m, "strategy_id"), bool(m, "success"),
                mapList(m, "records"), nestedArtifactId(m),
                integer(m, "duration_ms"), str(m, "error_message"), str(m, "execution_result_id"));
    }

    static NexusKnowledgeResult parseNexusKnowledgeResult(Map<String, Object> m) {
        return new NexusKnowledgeResult(mapList(m, "learned_concepts"),
                mapList(m, "patterns"), integer(m, "total_patterns"), integer(m, "duration_ms"));
    }
}
