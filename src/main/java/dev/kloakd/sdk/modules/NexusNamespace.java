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

    /** Classify a page and propose a strategy. The API needs the page's HTML. */
    public NexusAnalyzeResult analyze(String url, String html) {
        return analyze(url, html, null);
    }

    public NexusAnalyzeResult analyze(String url, String html, Map<String, Object> constraints) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        body.put("html", html);
        if (constraints != null && !constraints.isEmpty()) body.put("constraints", constraints);
        return parseNexusAnalyzeResult(t.post("nexus/analyze", body));
    }

    /** Generate an extraction program for a page and a goal. */
    public NexusSynthesisResult synthesize(String url, String html, String goal) {
        return parseNexusSynthesisResult(t.post("nexus/synthesize", Map.of("url", url, "html", html, "goal", goal)));
    }

    public NexusVerifyResult verify(String strategyId) {
        return parseNexusVerifyResult(t.post("nexus/verify", Map.of("strategy_id", strategyId)));
    }

    public NexusExecuteResult execute(String strategyId, String url) {
        return execute(strategyId, url, null);
    }

    public NexusExecuteResult execute(String strategyId, String url, Map<String, Object> options) {
        var body = new HashMap<String, Object>();
        body.put("strategy_id", strategyId);
        body.put("url", url);
        if (options != null && !options.isEmpty()) body.put("options", options);
        return parseNexusExecuteResult(t.post("nexus/execute", body));
    }

    public NexusKnowledgeResult knowledge(String executionResultId) {
        return parseNexusKnowledgeResult(t.post("nexus/knowledge",
                Map.of("execution_result_id", executionResultId)));
    }

    // ── Reason ───────────────────────────────────────────────────────────────

    public Map<String, Object> reason(Map<String, Object> context) { return t.post("nexus/reason", context); }

    // ── Recommendations ──────────────────────────────────────────────────────

    public Map<String, Object> recommendAnalyze(Map<String, Object> data) { return t.post("nexus/recommendations/analyze", data); }
    public Map<String, Object> listRecommendationApplications() { return t.get("nexus/recommendations/applications", null); }

    public Map<String, Object> getCacheStatistics() { return t.get("nexus/recommendations/cache/statistics", null); }
    public Map<String, Object> cleanupCache() { return t.post("nexus/recommendations/cache/cleanup", Map.of()); }
    public Map<String, Object> invalidateCache() { return t.post("nexus/recommendations/cache/invalidate", Map.of()); }

    public Map<String, Object> getHooksStatus() { return t.get("nexus/recommendations/hooks/status", null); }
    public Map<String, Object> enableHook(String hookName) { return t.post("nexus/recommendations/hooks/" + hookName + "/enable", Map.of()); }
    public Map<String, Object> disableHook(String hookName) { return t.post("nexus/recommendations/hooks/" + hookName + "/disable", Map.of()); }

    public Map<String, Object> createPreference(Map<String, Object> preference) { return t.post("nexus/recommendations/preferences", preference); }
    public Map<String, Object> getPreferences(String userId) { return t.get("nexus/recommendations/preferences/" + userId, null); }
    public Map<String, Object> updatePreference(String preferenceId, Map<String, Object> data) { return t.put("nexus/recommendations/preferences/" + preferenceId, data); }
    public void deletePreference(String preferenceId) { t.delete("nexus/recommendations/preferences/" + preferenceId); }

    public Map<String, Object> getRecommendationStatistics() { return t.get("nexus/recommendations/statistics", null); }

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
                integer(m, "duration_ms"), str(m, "error_message"));
    }

    static NexusKnowledgeResult parseNexusKnowledgeResult(Map<String, Object> m) {
        return new NexusKnowledgeResult(mapList(m, "learned_concepts"),
                mapList(m, "patterns"), integer(m, "total_patterns"), integer(m, "duration_ms"));
    }
}
