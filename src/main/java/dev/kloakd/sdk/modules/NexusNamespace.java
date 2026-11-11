package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.util.HashMap;
import java.util.List;
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

    /**
     * Check a synthesised strategy ({@code POST nexus/verify}). The result
     * holds the whole answer: {@code status} ({@code verified} or
     * {@code unsafe}), the scores and each safety check.
     */
    public NexusVerifyResult verify(String strategyId) {
        return parseNexusVerifyResult(t.post("nexus/verify", Map.of("strategy_id", strategyId)));
    }

    // ── Reason ───────────────────────────────────────────────────────────────

    /**
     * Logical reasoning via the Strategy Engine ({@code POST nexus/reason}):
     * {@code {"query_type", "parameters"}} (modules/nexus/router.py ReasonRequest).
     * The answer holds {@code success}, {@code results}, {@code confidence}, {@code reasoning_id}.
     */
    public Map<String, Object> reason(String queryType, List<String> parameters) {
        return reason(queryType, parameters, null);
    }

    public Map<String, Object> reason(String queryType) { return reason(queryType, List.of(), null); }

    /** {@link #reason(String, List)} about a prior analysis ({@code perceptionId} may be null). */
    public Map<String, Object> reason(String queryType, List<String> parameters, String perceptionId) {
        return reason(queryType, parameters, perceptionId, null);
    }

    /**
     * {@link #reason(String, List, String)} with a time limit for the query
     * in seconds ({@code timeout_secs}; null is not sent and the API's own
     * limit applies).
     */
    public Map<String, Object> reason(String queryType, List<String> parameters, String perceptionId,
            Double timeoutSecs) {
        var body = new HashMap<String, Object>();
        body.put("query_type", queryType);
        body.put("parameters", parameters != null ? parameters : List.of());
        if (perceptionId != null) body.put("perception_id", perceptionId);
        if (timeoutSecs != null) body.put("timeout_secs", timeoutSecs);
        return t.post("nexus/reason", body);
    }

    /** @deprecated the API takes {@code query_type} and a list of string {@code parameters}: use {@link #reason(String, List)}. */
    @Deprecated
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
                dbl(m, "safety_score"), strList(m, "violations"), str(m, "status"), dbl(m, "risk_score"),
                mapList(m, "safety_checks"), str(m, "verification_result_id"), integer(m, "duration_ms"));
    }
}
