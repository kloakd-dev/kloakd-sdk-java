package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Anti-Bot Intelligence module. Access via {@code client.evadr()}. */
public final class EvadrNamespace {

    private final HttpTransport t;

    public EvadrNamespace(HttpTransport t) { this.t = t; }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    public FetchResult fetch(String url) {
        return fetch(url, false, false, null);
    }

    public FetchResult fetch(String url, boolean forceBrowser, boolean useProxy, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (forceBrowser) body.put("force_browser", true);
        if (useProxy) body.put("use_proxy", true);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return parseFetchResult(t.post("evadr/fetch", body));
    }

    public CompletableFuture<FetchResult> fetchAsync(String url) {
        return fetchAsync(url, false, false, null);
    }

    public CompletableFuture<FetchResult> fetchAsync(
            String url, boolean forceBrowser, boolean useProxy, String webhookUrl) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (forceBrowser) body.put("force_browser", true);
        if (useProxy) body.put("use_proxy", true);
        if (webhookUrl != null) body.put("webhook_url", webhookUrl);
        return t.postAsync("evadr/fetch/async", body, EvadrNamespace::parseFetchResult);
    }

    public Stream<FetchEvent> fetchStream(String url) {
        return fetchStream(url, false);
    }

    public Stream<FetchEvent> fetchStream(String url, boolean forceBrowser) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (forceBrowser) body.put("force_browser", true);
        return t.stream("evadr/fetch/stream", body)
                .map(EvadrNamespace::parseFetchEvent);
    }

    // ── Analyze ───────────────────────────────────────────────────────────────

    public AnalyzeResult analyze(String url) {
        return analyze(url, 0, null, null);
    }

    public AnalyzeResult analyze(String url, int statusCode,
            Map<String, String> headers, String bodySnippet) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (statusCode > 0) body.put("status_code", statusCode);
        if (headers != null && !headers.isEmpty()) body.put("headers", headers);
        if (bodySnippet != null) body.put("body_snippet", bodySnippet);
        return parseAnalyzeResult(t.post("evadr/analyze", body));
    }

    // ── StoreProxy ────────────────────────────────────────────────────────────

    public void storeProxy(String name, String proxyUrl) {
        t.post("evadr/proxies", Map.of("name", name, "proxy_url", proxyUrl));
    }

    // ── parsers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static FetchResult parseFetchResult(Map<String, Object> m) {
        return new FetchResult(
                bool(m, "success"), str(m, "url"), integer(m, "status_code"),
                integer(m, "tier_used"), str(m, "html"), str(m, "vendor_detected"),
                bool(m, "anti_bot_bypassed"), str(m, "artifact_id"), str(m, "error"));
    }

    @SuppressWarnings("unchecked")
    static FetchEvent parseFetchEvent(Map<String, Object> m) {
        return new FetchEvent(str(m, "type"), optInt(m, "tier"),
                str(m, "vendor"), mapField(m, "metadata"));
    }

    static AnalyzeResult parseAnalyzeResult(Map<String, Object> m) {
        return new AnalyzeResult(bool(m, "blocked"), str(m, "vendor"),
                dbl(m, "confidence"), strList(m, "recommended_actions"));
    }

    // ── field helpers (shared across namespace classes via package-private) ───

    static String str(Map<String, Object> m, String key) {
        var v = m.get(key);
        return v instanceof String s ? s : null;
    }

    static boolean bool(Map<String, Object> m, String key) {
        var v = m.get(key);
        return Boolean.TRUE.equals(v);
    }

    static int integer(Map<String, Object> m, String key) {
        var v = m.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    static Integer optInt(Map<String, Object> m, String key) {
        var v = m.get(key);
        return v instanceof Number n ? n.intValue() : null;
    }

    static double dbl(Map<String, Object> m, String key) {
        var v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mapField(Map<String, Object> m, String key) {
        var v = m.get(key);
        return v instanceof Map<?,?> mp ? (Map<String, Object>) mp : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<String> strList(Map<String, Object> m, String key) {
        var v = m.get(key);
        if (!(v instanceof List<?> list)) return List.of();
        var result = new ArrayList<String>();
        for (var item : list) if (item instanceof String s) result.add(s);
        return Collections.unmodifiableList(result);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mapList(Map<String, Object> m, String key) {
        var v = m.get(key);
        if (!(v instanceof List<?> list)) return List.of();
        var result = new ArrayList<Map<String, Object>>();
        for (var item : list) if (item instanceof Map<?,?> mp) result.add((Map<String, Object>) mp);
        return Collections.unmodifiableList(result);
    }
}
