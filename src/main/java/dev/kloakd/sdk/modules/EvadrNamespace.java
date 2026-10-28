package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Anti-Bot Intelligence module. Access via {@code client.evadr()}. */
public final class EvadrNamespace {

    private final HttpTransport t;

    public EvadrNamespace(HttpTransport t) { this.t = t; }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    /** Default first wait before polling a handed-off fetch. */
    public static final Duration DEFAULT_POLL_INTERVAL = HttpTransport.DEFAULT_POLL_INTERVAL;
    /** Default limit on following a handed-off fetch. */
    public static final Duration DEFAULT_POLL_TIMEOUT = HttpTransport.DEFAULT_POLL_TIMEOUT;

    public FetchResult fetch(String url) {
        return fetch(url, FetchOptions.builder().build());
    }

    /** {@link #fetch(String, FetchOptions)} with these options. */
    public FetchResult fetch(String url, boolean forceBrowser, boolean useProxy, String sessionArtifactId) {
        return fetch(url, FetchOptions.builder().forceBrowser(forceBrowser).useProxy(useProxy)
                .sessionArtifactId(sessionArtifactId).build());
    }

    /** {@link #fetch(String, FetchOptions)} with these options. */
    public FetchResult fetch(String url, boolean forceBrowser, boolean useProxy, String sessionArtifactId,
                             Duration pollInterval, Duration pollTimeout) {
        return fetch(url, FetchOptions.builder().forceBrowser(forceBrowser).useProxy(useProxy)
                .sessionArtifactId(sessionArtifactId).pollInterval(pollInterval).pollTimeout(pollTimeout).build());
    }

    /**
     * Fetch a URL. The API answers within about 85 s; a slower site is handed
     * off as a job (HTTP 202), which this follows by polling the 202's
     * {@code poll_url} ({@code evadr/jobs/{id}}), first after {@code pollInterval}
     * and then with growing waits, up to {@code pollTimeout}, and returns the
     * job's result. ({@code webhookUrl} is not used: it applies to async jobs.)
     *
     * @throws dev.kloakd.sdk.errors.JobFailedException  the job failed or expired
     * @throws dev.kloakd.sdk.errors.JobTimeoutException still running after {@code pollTimeout}
     */
    public FetchResult fetch(String url, FetchOptions opts) {
        var body = fetchBody(url, opts);
        return parseFetchResult(t.postFollowing("evadr/fetch", body, interval(opts), limit(opts)));
    }

    private Duration interval(FetchOptions o) { return o.pollInterval() != null ? o.pollInterval() : t.pollInterval(); }

    private Duration limit(FetchOptions o) { return o.pollTimeout() != null ? o.pollTimeout() : t.pollTimeout(); }

    private static HashMap<String, Object> fetchBody(String url, FetchOptions o) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (o.forceBrowser()) body.put("force_browser", true);
        if (o.useProxy()) body.put("use_proxy", true);
        if (o.sessionArtifactId() != null) body.put("session_artifact_id", o.sessionArtifactId());
        return body;
    }

    /**
     * Poll a fetch job ({@code evadr/jobs/{id}}) until it completes (its result)
     * or fails ({@link dev.kloakd.sdk.errors.JobFailedException}); past
     * {@code pollTimeout}, {@link dev.kloakd.sdk.errors.JobTimeoutException}.
     */
    public FetchResult followJob(String jobId, Duration pollInterval, Duration pollTimeout) {
        return parseFetchResult(t.followHandOff(jobId, "evadr/jobs/" + seg(jobId), pollInterval, pollTimeout));
    }

    /** Start an asynchronous fetch ({@code POST evadr/fetch/async}) and return its job ID. */
    public String startFetchJob(String url, FetchOptions opts) {
        var body = fetchBody(url, opts);
        if (opts.webhookUrl() != null) body.put("webhook_url", opts.webhookUrl());
        return str(t.post("evadr/fetch/async", body), "job_id");
    }

    /** {@link #startFetchJob(String, FetchOptions)} with these options. */
    public String startFetchJob(String url, boolean forceBrowser, boolean useProxy,
                                String sessionArtifactId, String webhookUrl) {
        return startFetchJob(url, FetchOptions.builder().forceBrowser(forceBrowser).useProxy(useProxy)
                .sessionArtifactId(sessionArtifactId).webhookUrl(webhookUrl).build());
    }

    public CompletableFuture<FetchResult> fetchAsync(String url) {
        return fetchAsync(url, FetchOptions.builder().build());
    }

    /**
     * Start an asynchronous fetch job and complete with its result (polled on
     * a background thread). Use {@link #startFetchJob} for the job ID alone.
     */
    public CompletableFuture<FetchResult> fetchAsync(String url, FetchOptions opts) {
        return CompletableFuture.supplyAsync(() -> followJob(startFetchJob(url, opts), interval(opts), limit(opts)));
    }

    public Stream<FetchEvent> fetchStream(String url) {
        return fetchStream(url, FetchOptions.builder().build());
    }

    /** {@link #fetchStream(String, FetchOptions)} with {@code forceBrowser}. */
    public Stream<FetchEvent> fetchStream(String url, boolean forceBrowser) {
        return fetchStream(url, FetchOptions.builder().forceBrowser(forceBrowser).build());
    }

    /**
     * Start an asynchronous fetch and stream its events as they arrive
     * ({@code GET evadr/jobs/{id}/events}); there is no POST stream route.
     * Close the stream to stop.
     */
    public Stream<FetchEvent> fetchStream(String url, FetchOptions opts) {
        String jobId = startFetchJob(url, opts);
        return jobEvents(jobId).map(EvadrNamespace::parseFetchEvent);
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

    // ── Scan and profile ──────────────────────────────────────────────────────

    /** Fetch a URL once (plain HTTP) and report its anti-bot infrastructure. */
    public Map<String, Object> scan(String url) {
        return t.post("evadr/scan", Map.of("url", url));
    }

    /** Analyse a URL and return its canonical site profile. */
    public Map<String, Object> profile(String url) {
        return t.post("evadr/profile", Map.of("url", url));
    }

    // ── Job polling ──────────────────────────────────────────────────────────

    public Map<String, Object> getJob(String jobId) {
        return t.get("evadr/jobs/" + seg(jobId), null);
    }

    /** Stream an async fetch job's events (SSE); close the stream to stop. */
    public Stream<Map<String, Object>> jobEvents(String jobId) {
        return t.getStream("evadr/jobs/" + seg(jobId) + "/events", null);
    }

    // ── Vendors ──────────────────────────────────────────────────────────────

    public Map<String, Object> listVendors() { return t.get("evadr/vendors", null); }

    // ── Proxy management ─────────────────────────────────────────────────────

    public Map<String, Object> listProxies() { return t.get("evadr/proxies", null); }

    public void storeProxy(String name, String proxyUrl) {
        t.post("evadr/proxies", Map.of("name", name, "proxy_url", proxyUrl));
    }

    public void deleteProxy(String name) { t.delete("evadr/proxies/" + seg(name)); }

    // ── parsers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static FetchResult parseFetchResult(Map<String, Object> m) {
        return new FetchResult(
                bool(m, "success"), str(m, "url"), integer(m, "status_code"),
                integer(m, "tier_used"), str(m, "html"), str(m, "vendor_detected"),
                bool(m, "anti_bot_bypassed"), nestedArtifactId(m), str(m, "error"),
                bool(m, "auth_required"), str(m, "discovery_id"));
    }

    @SuppressWarnings("unchecked")
    static FetchEvent parseFetchEvent(Map<String, Object> m) {
        if (m.get("event_type") instanceof String eventType) {
            var p = parsePipelineEvent(m);
            String error = p.error() == null ? null
                    : str(p.error(), "user_message") != null ? str(p.error(), "user_message")
                    : str(p.error(), "technical_message");
            return new FetchEvent(eventType, p.pipelineId(), p.status(), optInt(p.metadata(), "tier_used"),
                    str(p.metadata(), "artifact_id"), error, p);
        }
        return new FetchEvent(str(m, "type"), str(m, "job_id"), str(m, "status"), optInt(m, "tier_used"),
                str(m, "artifact_id"), str(m, "error"), null);
    }

    /** A pipeline event payload ({@code core/pipeline/models.py} PipelineEvent.to_dict). */
    @SuppressWarnings("unchecked")
    public static PipelineEvent parsePipelineEvent(Map<String, Object> m) {
        Object err = m.get("error");
        Object pct = m.get("progress_pct");
        return new PipelineEvent(str(m, "event_type"), str(m, "stage_id"), str(m, "status"), str(m, "message"),
                pct instanceof Number n ? n.doubleValue() : null, mapField(m, "metadata"),
                err instanceof Map<?, ?> e ? (Map<String, Object>) e : null, optInt(m, "sequence"),
                str(m, "pipeline_id") != null ? str(m, "pipeline_id") : str(m, "crawl_id"),
                str(m, "phase"), str(m, "sub_status"), str(m, "timestamp"));
    }

    static AnalyzeResult parseAnalyzeResult(Map<String, Object> m) {
        return new AnalyzeResult(bool(m, "blocked"), str(m, "vendor"),
                dbl(m, "confidence"), strList(m, "recommended_actions"));
    }

    // ── field helpers (shared across namespace classes via package-private) ───

    /** Percent-encode a path segment ({@link HttpTransport#seg}). */
    static String seg(String value) { return HttpTransport.seg(value); }

    static String nestedArtifactId(Map<String, Object> m) {
        var artifact = m.get("artifact");
        if (artifact instanceof Map<?,?> mp) {
            var id = mp.get("artifact_id");
            if (id instanceof String s) return s;
        }
        return str(m, "artifact_id");
    }

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
