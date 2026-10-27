package dev.kloakd.sdk.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kloakd.sdk.errors.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Shared HTTP transport for all KLOAKD namespace methods.
 * Not part of the public API — use via Kloakd client namespaces.
 *
 * <p>Retry policy:
 * <ul>
 *   <li>Retryable: 429, 500, 502, 503, 504</li>
 *   <li>Non-retryable: 400, 401, 402, 403, 404, and 429 {@code gb_exhausted}</li>
 *   <li>Strategy: exponential backoff — base 1s × 2^attempt, cap 60s</li>
 *   <li>429: respects {@code Retry-After} header / {@code retry_after} body field</li>
 *   <li>Default max retries: 3</li>
 * </ul>
 */
public class HttpTransport {

    static final String SDK_VERSION = "0.4.0";
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Default first wait before polling a handed-off request (HTTP 202). */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(3);
    /** Default limit on following a handed-off request. */
    public static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMinutes(15);
    /** Each wait between polls is 1.5 times the last, up to this. */
    public static final Duration MAX_POLL_INTERVAL = Duration.ofSeconds(15);

    private final String apiKey;
    private final String organizationId;
    private final String baseUrl;
    private final int maxRetries;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final Duration pollInterval;
    private final Duration pollTimeout;

    public HttpTransport(
            String apiKey,
            String organizationId,
            String baseUrl,
            Duration timeout,
            int maxRetries,
            HttpClient httpClient) {
        this(apiKey, organizationId, baseUrl, timeout, maxRetries, httpClient, null, null);
    }

    /**
     * @param pollInterval first wait before polling a handed-off request, or null for the default
     * @param pollTimeout  limit on following a handed-off request, or null for the default
     */
    public HttpTransport(
            String apiKey,
            String organizationId,
            String baseUrl,
            Duration timeout,
            int maxRetries,
            HttpClient httpClient,
            Duration pollInterval,
            Duration pollTimeout) {
        this.apiKey = apiKey;
        this.organizationId = organizationId;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.httpClient = httpClient != null ? httpClient :
                HttpClient.newBuilder().connectTimeout(timeout).build();
        this.pollInterval = pollInterval != null ? pollInterval : DEFAULT_POLL_INTERVAL;
        this.pollTimeout = pollTimeout != null ? pollTimeout : DEFAULT_POLL_TIMEOUT;
    }

    public Duration pollInterval() { return pollInterval; }

    public Duration pollTimeout() { return pollTimeout; }

    // ── URL building ──────────────────────────────────────────────────────────

    public String buildUrl(String path, Map<String, String> params) {
        String full = baseUrl + "/api/v1/organizations/" + organizationId + "/"
                + path.replaceAll("^/+", "");
        if (params == null || params.isEmpty()) return full;
        var sb = new StringBuilder(full).append("?");
        params.forEach((k, v) -> sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(v, StandardCharsets.UTF_8))
                .append("&"));
        return sb.substring(0, sb.length() - 1);
    }

    // ── Auth headers ──────────────────────────────────────────────────────────

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Kloakd-Organization", organizationId)
                .header("X-Kloakd-SDK", "java/" + SDK_VERSION)
                .header("Content-Type", "application/json");
    }

    // ── Sync request with retry ───────────────────────────────────────────────

    /**
     * Execute a synchronous HTTP request with retry/backoff.
     *
     * @param method    HTTP method ("GET", "POST", "DELETE")
     * @param path      path relative to org prefix
     * @param body      request body map (JSON-serialised), or null for GET/DELETE
     * @param params    query parameters, or null
     * @return parsed response body as Map
     */
    public Map<String, Object> request(
            String method,
            String path,
            Map<String, Object> body,
            Map<String, String> params) {
        return send(method, path, body, params).body();
    }

    /** A response's status and parsed body. */
    public record Response(int status, Map<String, Object> body) {}

    /**
     * {@link #request} that also returns the HTTP status: a 202 from the
     * synchronous fetch means the work continues as a job.
     */
    public Response send(
            String method,
            String path,
            Map<String, Object> body,
            Map<String, String> params) {

        return sendTo(method, buildUrl(path, params), body);
    }

    /**
     * Resolve a URL the API returned: a full URL as is, an absolute path
     * ({@code /api/v1/...}) against the base URL's origin, anything else
     * relative to the organisation prefix.
     */
    public String resolveUrl(String pathOrUrl) {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl;
        if (pathOrUrl.startsWith("/")) return URI.create(baseUrl + "/").resolve(pathOrUrl).toString();
        return buildUrl(pathOrUrl, null);
    }

    private Response sendTo(String method, String url, Map<String, Object> body) {
        byte[] bodyBytes = serialise(body);
        KloakdException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                var req = baseRequest(url);
                if ("GET".equals(method)) {
                    req.GET();
                } else if ("DELETE".equals(method)) {
                    req.DELETE();
                } else {
                    req.method(method, bodyBytes != null
                            ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                            : HttpRequest.BodyPublishers.noBody());
                }

                HttpResponse<String> resp = httpClient.send(req.build(),
                        HttpResponse.BodyHandlers.ofString());

                Map<String, Object> parsed = parseBody(resp.body());
                String retryAfter = resp.headers().firstValue("Retry-After").orElse(null);
                KloakdException ex = raiseForStatus(resp.statusCode(), parsed, retryAfter);
                if (ex == null) return new Response(resp.statusCode(), parsed);

                if (!isRetryable(ex)) throw ex;

                lastException = ex;
                if (attempt < maxRetries) {
                    sleep(backoffMs(attempt, retryAfter, parsed));
                }
            } catch (KloakdException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KloakdException(0, "Interrupted: " + e.getMessage());
            } catch (IOException e) {
                throw new KloakdException(0, "IO error: " + e.getMessage());
            }
        }
        throw lastException;
    }

    public Map<String, Object> get(String path, Map<String, String> params) {
        return request("GET", path, null, params);
    }

    public Map<String, Object> post(String path, Map<String, Object> body) {
        return request("POST", path, body, null);
    }

    public Map<String, Object> put(String path, Map<String, Object> body) {
        return request("PUT", path, body, null);
    }

    public Map<String, Object> patch(String path, Map<String, Object> body) {
        return request("PATCH", path, body, null);
    }

    public void delete(String path) {
        request("DELETE", path, null, null);
    }

    // ── 202 hand-off ──────────────────────────────────────────────────────────

    /**
     * POST to a route that may hand the request off as a job: the API
     * answers within about 85 s, and a request still running then gets
     * HTTP 202 with {@code job_id} and {@code poll_url}. This follows the
     * hand-off ({@link #followHandOff}) with the client's poll settings and
     * returns the route's own response body either way.
     */
    public Map<String, Object> postFollowing(String path, Map<String, Object> body) {
        return postFollowing(path, body, pollInterval, pollTimeout);
    }

    /** {@link #postFollowing(String, Map)} with this call's poll settings. */
    public Map<String, Object> postFollowing(String path, Map<String, Object> body,
                                             Duration interval, Duration limit) {
        Response resp = send("POST", path, body, null);
        if (resp.status() == 202 && resp.body().get("job_id") instanceof String jobId) {
            String pollUrl = resp.body().get("poll_url") instanceof String p && !p.isBlank()
                    ? p : "handoff/" + jobId;
            return followHandOff(jobId, pollUrl, interval, limit);
        }
        return resp.body();
    }

    /**
     * Poll a handed-off request's {@code pollUrl} until it ends. Waits
     * {@code interval} before the first poll, then 1.5 times longer each
     * time, at most {@link #MAX_POLL_INTERVAL}.
     *
     * @return the job's {@code result}: the body the route would have answered with
     * @throws JobFailedException  the job failed, or the API no longer knows it (404: expired)
     * @throws JobTimeoutException still running after {@code limit}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> followHandOff(String jobId, String pollUrl, Duration interval, Duration limit) {
        String url = resolveUrl(pollUrl);
        long deadline = System.nanoTime() + limit.toNanos();
        long waitMs = Math.max(0, interval.toMillis());
        while (true) {
            try {
                sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KloakdException(0, "Interrupted while following job " + jobId);
            }
            Map<String, Object> job;
            try {
                job = sendTo("GET", url, null).body();
            } catch (ApiException e) {
                if (e.getStatusCode() != 404) throw e;
                throw new JobFailedException("Job " + jobId + " not found or expired: " + e.getMessage(), jobId);
            }
            Object status = job.get("status");
            if ("completed".equals(status)) {
                return job.get("result") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            }
            if ("failed".equals(status)) {
                Object error = job.get("error");
                throw new JobFailedException(error != null ? String.valueOf(error) : "Job " + jobId + " failed", jobId);
            }
            waitMs = Math.min(Math.max(1, waitMs * 3 / 2), MAX_POLL_INTERVAL.toMillis());
            if (System.nanoTime() + waitMs * 1_000_000L > deadline) {
                throw new JobTimeoutException("Job " + jobId + " still running after " + limit, jobId);
            }
        }
    }

    // ── Async request ─────────────────────────────────────────────────────────

    /**
     * Execute an asynchronous POST request, returning a CompletableFuture.
     * No retry — caller can compose retry logic if needed.
     */
    public <T> CompletableFuture<T> postAsync(
            String path,
            Map<String, Object> body,
            Function<Map<String, Object>, T> mapper) {

        String url = buildUrl(path, null);
        byte[] bodyBytes = serialise(body);
        var req = baseRequest(url)
                .method("POST", bodyBytes != null
                        ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                        : HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    Map<String, Object> parsed = parseBody(resp.body());
                    KloakdException ex = raiseForStatus(resp.statusCode(), parsed);
                    if (ex != null) throw ex;
                    return mapper.apply(parsed);
                });
    }

    // ── SSE streaming ─────────────────────────────────────────────────────────

    /**
     * Open a server-sent events stream with POST.
     * Returns a {@link Stream} of parsed data payloads (data-only lines).
     * The underlying HTTP connection is closed when the stream is closed.
     */
    public Stream<Map<String, Object>> stream(String path, Map<String, Object> body) {
        byte[] bodyBytes = serialise(body);
        return sseDataStream(open(baseRequest(buildUrl(path, null))
                .method("POST", bodyBytes != null
                        ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                        : HttpRequest.BodyPublishers.noBody())));
    }

    /**
     * Open a server-sent events stream with GET (job, crawl and pipeline
     * event routes).
     */
    public Stream<Map<String, Object>> getStream(String path, Map<String, String> params) {
        return sseDataStream(open(baseRequest(buildUrl(path, params)).GET()));
    }

    /**
     * Open a server-sent events stream with event-name support.
     * Returns a {@link Stream} of {@code String[2]}: [eventName, dataJson].
     */
    public Stream<String[]> streamWithEvents(String path, Map<String, Object> body) {
        byte[] bodyBytes = serialise(body);
        return sseEventStream(open(baseRequest(buildUrl(path, null))
                .method("POST", bodyBytes != null
                        ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                        : HttpRequest.BodyPublishers.noBody())));
    }

    private BufferedReader open(HttpRequest.Builder builder) {
        var req = builder.header("Accept", "text/event-stream").build();
        try {
            HttpResponse<InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                String errBody;
                try (InputStream in = resp.body()) {
                    errBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw raiseForStatus(resp.statusCode(), parseBody(errBody),
                        resp.headers().firstValue("Retry-After").orElse(null));
            }
            return new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8));
        } catch (KloakdException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KloakdException(0, "Interrupted: " + e.getMessage());
        } catch (IOException e) {
            throw new KloakdException(0, "IO error: " + e.getMessage());
        }
    }

    // ── SSE parse helpers ─────────────────────────────────────────────────────

    private Stream<Map<String, Object>> sseDataStream(BufferedReader reader) {
        Iterator<Map<String, Object>> it = new Iterator<>() {
            Map<String, Object> next = null;
            boolean done = false;

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (next != null) return true;
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (!data.isEmpty()) {
                                next = parseBody(data);
                                return true;
                            }
                        }
                    }
                } catch (IOException ignored) {}
                done = true;
                try { reader.close(); } catch (IOException ignored) {}
                return false;
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) throw new NoSuchElementException();
                var v = next;
                next = null;
                return v;
            }
        };
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);
    }

    private Stream<String[]> sseEventStream(BufferedReader reader) {
        Iterator<String[]> it = new Iterator<>() {
            String[] next = null;
            boolean done = false;
            String currentEvent = "";

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (next != null) return true;
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("event:")) {
                            currentEvent = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (!data.isEmpty()) {
                                next = new String[]{currentEvent, data};
                                currentEvent = "";
                                return true;
                            }
                        }
                    }
                } catch (IOException ignored) {}
                done = true;
                try { reader.close(); } catch (IOException ignored) {}
                return false;
            }

            @Override
            public String[] next() {
                if (!hasNext()) throw new NoSuchElementException();
                var v = next;
                next = null;
                return v;
            }
        };
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    public static KloakdException raiseForStatus(int status, Map<String, Object> body) {
        return raiseForStatus(status, body, null);
    }

    /**
     * Map an HTTP status to the SDK exception, or null for 2xx. The gateway's
     * errors carry {@code {"detail": {"error": <code>, "message": <text>, ...}}}.
     */
    public static KloakdException raiseForStatus(int status, Map<String, Object> body, String retryAfterHeader) {
        if (status >= 200 && status < 300) return null;
        Map<String, Object> detail = detail(body);
        String code = str(detail, "error");
        String msg = extractMessage(body, status);
        int headerRetry = -1;
        if (retryAfterHeader != null) {
            try { headerRetry = (int) Double.parseDouble(retryAfterHeader.trim()); } catch (NumberFormatException ignored) {}
        }
        int retry60 = headerRetry > 0 ? headerRetry : intVal(detail, "retry_after", intVal(body, "retry_after", 60));
        int retry30 = headerRetry > 0 ? headerRetry : intVal(detail, "retry_after", intVal(body, "retry_after", 30));
        String upgradeUrl = str(detail, "upgrade_url") != null ? str(detail, "upgrade_url") : str(body, "upgrade_url");

        if (status == 401) return new AuthenticationException(msg);
        if (status == 402) return new PaymentRequiredException(msg, upgradeUrl);
        if (status == 403) return new NotEntitledException(msg,
                str(detail, "module") != null ? str(detail, "module") : str(body, "module"), upgradeUrl);
        if (status == 429 && "gb_exhausted".equals(code)) return new QuotaExceededException(msg, upgradeUrl,
                dbl(detail, "residential_gb_used"), dbl(detail, "residential_gb_limit"),
                dbl(detail, "purchased_gb_balance"));
        if (status == 429) return new RateLimitException(msg, retry60,
                str(detail, "reset_at") != null ? str(detail, "reset_at") : str(body, "reset_at"), code);
        if (status == 503 && "billing_unavailable".equals(code)) return new BillingUnavailableException(msg, retry30);
        if (status == 502) return new UpstreamException(msg);
        return new ApiException(status, msg, code);
    }

    public static boolean isRetryable(int status) {
        return RETRYABLE.contains(status);
    }

    /** As {@link #isRetryable(int)}, except an exhausted quota and a failed payment are not retried. */
    public static boolean isRetryable(KloakdException ex) {
        if (ex instanceof QuotaExceededException || ex instanceof PaymentRequiredException) return false;
        return RETRYABLE.contains(ex.getStatusCode());
    }

    // ── Backoff ───────────────────────────────────────────────────────────────

    public static long backoffMs(int attempt, String retryAfterHeader, Map<String, Object> body) {
        if (retryAfterHeader != null && !retryAfterHeader.isBlank()) {
            try {
                return (long) (Double.parseDouble(retryAfterHeader.trim()) * 1000);
            } catch (NumberFormatException ignored) {}
        }
        for (Map<String, Object> m : body == null ? List.<Map<String, Object>>of() : List.of(body, detail(body))) {
            if (m.get("retry_after") instanceof Number n && n.doubleValue() > 0) {
                return (long) (n.doubleValue() * 1000);
            }
        }
        return Math.min(1000L * (1L << attempt), 60_000L);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static Map<String, Object> detail(Map<String, Object> body) {
        if (body != null && body.get("detail") instanceof Map<?, ?> d) return (Map<String, Object>) d;
        return Map.of();
    }

    static double dbl(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.doubleValue() : 0;
    }

    private static String extractMessage(Map<String, Object> body, int status) {
        Map<String, Object> d = detail(body);
        if (d.get("message") != null) return String.valueOf(d.get("message"));
        if (body.containsKey("detail")) return String.valueOf(body.get("detail"));
        if (body.containsKey("message")) return String.valueOf(body.get("message"));
        return "HTTP " + status;
    }

    static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    static int intVal(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    /** Parse a JSON body; a top-level array is returned as {@code {"items": [...]}}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseBody(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object value = MAPPER.readValue(json, Object.class);
            if (value instanceof Map<?, ?> m) return (Map<String, Object>) m;
            if (value instanceof List<?> l) return Map.of("items", l);
            return Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private byte[] serialise(Map<String, Object> body) {
        if (body == null) return null;
        try {
            return MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new KloakdException(0, "Failed to serialise request body: " + e.getMessage());
        }
    }

    /** Protected so test subclasses can override to skip backoff sleeps. */
    protected void sleep(long ms) throws InterruptedException {
        if (ms > 0) Thread.sleep(ms);
    }
}
