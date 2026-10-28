package dev.kloakd.sdk.http;

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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Shared HTTP transport for all KLOAKD namespace methods.
 * Not part of the public API — use via Kloakd client namespaces.
 *
 * <p>Retry policy (at most {@code maxRetries} retries, default 3):
 * <ul>
 *   <li>500, 502, 503, 504 and network errors: retried only for GET, HEAD and
 *       DELETE. A POST, PUT or PATCH may already have done its work, so it is
 *       never retried on these.</li>
 *   <li>429 {@code rate_limited} (or a 429 without a code): retried on any
 *       method, after {@code Retry-After}, else {@code retry_after}, else
 *       {@code reset_at}; a wait over 60 s is not slept, the error is thrown.
 *       The playground quota (429 with {@code retry_after} 0 and no
 *       {@code reset_at}) is not retried: waiting does not clear it.</li>
 *   <li>503 {@code billing_unavailable}: retried on any method (the request
 *       was not started), after {@code Retry-After}.</li>
 *   <li>Never retried: 400, 401, 402, 403, 404, 409, 422 and 429 {@code gb_exhausted}.</li>
 *   <li>Backoff without a server hint: 1 s × 2^attempt, at most 60 s.</li>
 * </ul>
 */
public class HttpTransport {

    static final String SDK_VERSION = "0.5.0";
    /** The {@code User-Agent} every request sends. */
    public static final String USER_AGENT = "kloakd-sdk-java/" + SDK_VERSION;
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
    private static final Set<Integer> SERVER_ERRORS = Set.of(500, 502, 503, 504);
    private static final Set<String> IDEMPOTENT = Set.of("GET", "HEAD", "DELETE");
    /** A server-requested wait longer than this is not slept; the error is thrown. */
    static final long MAX_RETRY_WAIT_MS = 60_000L;
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

    /**
     * Percent-encode one path segment (as JavaScript's {@code encodeURIComponent}
     * does): {@code /}, {@code ?}, {@code #}, spaces and the rest become
     * {@code %XX}. The API decodes the path before matching its routes, so an
     * encoded value (an id, or a whole URL for {@code kolektr/api-data/{api_endpoint:path}})
     * arrives as given.
     */
    public static String seg(String value) {
        if (value == null) throw new IllegalArgumentException("a path parameter is null");
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public String buildUrl(String path, Map<String, String> params) {
        String full = baseUrl + "/api/v1/organizations/" + seg(organizationId) + "/"
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
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json");
    }

    // ── Sync request with retry ───────────────────────────────────────────────

    /**
     * Execute a synchronous HTTP request with retry/backoff.
     *
     * @param method    HTTP method ("GET", "POST", "PUT", "PATCH", "DELETE")
     * @param path      path relative to org prefix (segments already {@link #seg encoded})
     * @param body      request body map (JSON-serialised), or null for GET/DELETE
     * @param params    query parameters, or null
     * @return parsed response body as Map (a top-level array under {@code "items"})
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
        var resp = exchange(method, buildUrl(path, params), body);
        return new Response(resp.statusCode(), parseBody(text(resp.body())));
    }

    /** A request whose response is not JSON (a ZIP, a file): its raw bytes. */
    public byte[] requestBytes(String method, String path, Map<String, Object> body, Map<String, String> params) {
        return exchange(method, buildUrl(path, params), body).body();
    }

    /** A request whose response is plain text. */
    public String requestText(String method, String path, Map<String, Object> body, Map<String, String> params) {
        return text(requestBytes(method, path, body, params));
    }

    /** A request whose response is a JSON array. */
    public List<Map<String, Object>> requestList(String method, String path, Map<String, Object> body,
                                                 Map<String, String> params) {
        return asMapList(request(method, path, body, params).get("items"));
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

    private HttpResponse<byte[]> exchange(String method, String url, Map<String, Object> body) {
        byte[] bodyBytes = serialise(body);
        for (int attempt = 0; ; attempt++) {
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

            HttpResponse<byte[]> resp;
            try {
                resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KloakdException(0, "Interrupted: " + e.getMessage());
            } catch (IOException e) {
                var ex = new KloakdException(0, "IO error: " + e.getMessage());
                if (!IDEMPOTENT.contains(method) || attempt >= maxRetries) throw ex;
                pause(backoffMs(attempt, null, null));
                continue;
            }

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return resp;

            String raw = text(resp.body());
            Map<String, Object> parsed = parseBody(raw);
            String retryAfter = resp.headers().firstValue("Retry-After").orElse(null);
            KloakdException ex = raiseForStatus(resp.statusCode(), parsed, retryAfter, raw);
            long wait = retryDelayMs(method, ex, attempt, retryAfter, parsed);
            if (wait < 0 || attempt >= maxRetries) throw ex;
            pause(wait);
        }
    }

    private void pause(long ms) {
        try {
            sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KloakdException(0, "Interrupted: " + e.getMessage());
        }
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
                    ? p : "handoff/" + seg(jobId);
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
     * @throws JobTimeoutException still running after {@code limit}; its
     *                             {@link JobTimeoutException#getJobId()} resumes it
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
                job = parseBody(text(exchange("GET", url, null).body()));
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
                    KloakdException ex = raiseForStatus(resp.statusCode(), parsed,
                            resp.headers().firstValue("Retry-After").orElse(null), resp.body());
                    if (ex != null) throw ex;
                    return mapper.apply(parsed);
                });
    }

    // ── SSE streaming ─────────────────────────────────────────────────────────

    /** One server-sent event: its {@code event:} name ("" if none), joined {@code data:} lines and {@code id:}. */
    public record SseEvent(String event, String data, String id) {}

    /**
     * Open a server-sent events stream with POST.
     * Returns a {@link Stream} of parsed data payloads.
     * Close the stream to close the connection.
     */
    public Stream<Map<String, Object>> stream(String path, Map<String, Object> body) {
        return streamEvents(path, body).map(e -> parseBody(e.data()));
    }

    /**
     * Open a server-sent events stream with GET (job, crawl and pipeline
     * event routes).
     */
    public Stream<Map<String, Object>> getStream(String path, Map<String, String> params) {
        return sseStream(open(baseRequest(buildUrl(path, params)).GET())).map(e -> parseBody(e.data()));
    }

    /** POST a server-sent events stream and return its events as they arrive. */
    public Stream<SseEvent> streamEvents(String path, Map<String, Object> body) {
        byte[] bodyBytes = serialise(body);
        return sseStream(open(baseRequest(buildUrl(path, null))
                .method("POST", bodyBytes != null
                        ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                        : HttpRequest.BodyPublishers.noBody())));
    }

    /**
     * Open a server-sent events stream with event-name support.
     * Returns a {@link Stream} of {@code String[2]}: [eventName, dataJson].
     */
    public Stream<String[]> streamWithEvents(String path, Map<String, Object> body) {
        return streamEvents(path, body).map(e -> new String[]{e.event(), e.data()});
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
                        resp.headers().firstValue("Retry-After").orElse(null), errBody);
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

    // ── SSE parse ─────────────────────────────────────────────────────────────

    /**
     * Parse an SSE body lazily: each event is yielded when its blank line
     * arrives. Comment lines ({@code : keepalive}) are skipped; several
     * {@code data:} lines are joined with newlines. A read error ends the
     * stream with a {@link KloakdException}; it is not dropped.
     */
    static Stream<SseEvent> sseStream(BufferedReader reader) {
        Iterator<SseEvent> it = new Iterator<>() {
            SseEvent next = null;
            boolean done = false;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                if (done) return false;
                String event = "";
                String id = null;
                StringBuilder data = null;
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (data != null) {
                                next = new SseEvent(event, data.toString(), id);
                                return true;
                            }
                            event = "";
                            continue;
                        }
                        if (line.startsWith(":")) continue;
                        int colon = line.indexOf(':');
                        String field = colon < 0 ? line : line.substring(0, colon);
                        String value = colon < 0 ? "" : line.substring(colon + 1);
                        if (value.startsWith(" ")) value = value.substring(1);
                        switch (field) {
                            case "event" -> event = value;
                            case "data" -> {
                                if (data == null) data = new StringBuilder(value);
                                else data.append('\n').append(value);
                            }
                            case "id" -> id = value;
                            default -> { }
                        }
                    }
                } catch (IOException e) {
                    finish();
                    throw new KloakdException(0, "Event stream read failed: " + e.getMessage());
                }
                finish();
                if (data != null) {
                    next = new SseEvent(event, data.toString(), id);
                    return true;
                }
                return false;
            }

            private void finish() {
                done = true;
                try { reader.close(); } catch (IOException ignored) {}
            }

            @Override
            public SseEvent next() {
                if (!hasNext()) throw new NoSuchElementException();
                var v = next;
                next = null;
                return v;
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false)
                .onClose(() -> {
                    try { reader.close(); } catch (IOException ignored) {}
                });
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    public static KloakdException raiseForStatus(int status, Map<String, Object> body) {
        return raiseForStatus(status, body, null, null);
    }

    public static KloakdException raiseForStatus(int status, Map<String, Object> body, String retryAfterHeader) {
        return raiseForStatus(status, body, retryAfterHeader, null);
    }

    /**
     * Map an HTTP status to the SDK exception, or null for 2xx. The gateway's
     * errors carry {@code {"detail": {"error": <code>, "message": <text>, ...}}};
     * FastAPI's validation errors {@code {"detail": [{"loc", "msg"}, ...]}}; a
     * body that is not JSON is the message itself.
     *
     * @param rawBody the response text, or null
     */
    public static KloakdException raiseForStatus(int status, Map<String, Object> body, String retryAfterHeader,
                                                 String rawBody) {
        if (status >= 200 && status < 300) return null;
        if (body == null) body = Map.of();
        Map<String, Object> detail = detail(body);
        String code = str(detail, "error");
        String msg = extractMessage(body, status, rawBody);
        int headerRetry = -1;
        if (retryAfterHeader != null) {
            try { headerRetry = (int) Double.parseDouble(retryAfterHeader.trim()); } catch (NumberFormatException ignored) {}
        }
        String upgradeUrl = first(detail, body, "upgrade_url");

        if (status == 401) return new AuthenticationException(msg);
        if (status == 402) return new PaymentRequiredException(msg, upgradeUrl);
        if (status == 403) {
            if ("not_entitled".equals(code) || "tier_access_denied".equals(code)) {
                return new NotEntitledException(msg, first(detail, body, "module"), upgradeUrl, code,
                        str(detail, "feature"), str(detail, "current_tier"), str(detail, "required_tier"));
            }
            return new ForbiddenException(msg, code);
        }
        if (status == 409 && "concurrent_limit".equals(code)) {
            return new ConcurrencyLimitException(msg, intVal(detail, "concurrent_limit", 0),
                    optInt(detail, "active_crawls"), str(detail, "tier"));
        }
        if (status == 429 && "gb_exhausted".equals(code)) return new QuotaExceededException(msg, upgradeUrl,
                dbl(detail, "residential_gb_used"), dbl(detail, "residential_gb_limit"),
                dbl(detail, "purchased_gb_balance"), str(detail, "tier"));
        if (status == 429) {
            Object bodyRetry = detail.containsKey("retry_after") ? detail.get("retry_after") : body.get("retry_after");
            String resetAt = first(detail, body, "reset_at");
            boolean quotaExhausted = bodyRetry instanceof Number n && n.doubleValue() == 0
                    && (resetAt == null || resetAt.isBlank());
            int retry = headerRetry > 0 ? headerRetry
                    : bodyRetry instanceof Number n ? n.intValue() : quotaExhausted ? 0 : 60;
            return new RateLimitException(msg, retry, resetAt != null && !resetAt.isBlank() ? resetAt : null,
                    code, quotaExhausted, optInt(detail, "quota_used"), optInt(detail, "quota_limit"), upgradeUrl);
        }
        if (status == 503 && "billing_unavailable".equals(code)) {
            return new BillingUnavailableException(msg,
                    headerRetry > 0 ? headerRetry : intVal(detail, "retry_after", 30));
        }
        if (status == 502) return new UpstreamException(msg);
        return new ApiException(status, msg, code);
    }

    /** Whether this status is ever retried (on some method). */
    public static boolean isRetryable(int status) {
        return RETRYABLE.contains(status);
    }

    /** As {@link #isRetryable(String, KloakdException)} for a GET. */
    public static boolean isRetryable(KloakdException ex) {
        return isRetryable("GET", ex);
    }

    /** Whether a request with this method that failed with {@code ex} is retried. */
    public static boolean isRetryable(String method, KloakdException ex) {
        if (ex instanceof RateLimitException rl) return !rl.isQuotaExhausted() && retryableRateLimitCode(rl.getCode());
        if (ex instanceof BillingUnavailableException) return true;
        if (ex instanceof QuotaExceededException || ex instanceof PaymentRequiredException) return false;
        return SERVER_ERRORS.contains(ex.getStatusCode()) && IDEMPOTENT.contains(method);
    }

    private static boolean retryableRateLimitCode(String code) {
        return code == null || "rate_limited".equals(code);
    }

    /** How long to wait before retrying, or -1 for no retry. */
    long retryDelayMs(String method, KloakdException ex, int attempt, String retryAfterHeader,
                      Map<String, Object> body) {
        if (!isRetryable(method, ex)) return -1;
        long wait = backoffMs(attempt, retryAfterHeader, body);
        if (ex instanceof RateLimitException rl && !hasRetryHint(retryAfterHeader, body) && rl.getResetAt() != null) {
            try {
                wait = Math.max(0, Duration.between(OffsetDateTime.now(), OffsetDateTime.parse(rl.getResetAt()))
                        .toMillis());
            } catch (DateTimeParseException ignored) {}
        }
        return wait > MAX_RETRY_WAIT_MS ? -1 : wait;
    }

    private static boolean hasRetryHint(String retryAfterHeader, Map<String, Object> body) {
        if (retryAfterHeader != null) {
            try {
                if (Double.parseDouble(retryAfterHeader.trim()) > 0) return true;
            } catch (NumberFormatException ignored) {}
        }
        if (body == null) return false;
        for (Map<String, Object> m : List.of(body, detail(body))) {
            if (m.get("retry_after") instanceof Number n && n.doubleValue() > 0) return true;
        }
        return false;
    }

    // ── Backoff ───────────────────────────────────────────────────────────────

    public static long backoffMs(int attempt, String retryAfterHeader, Map<String, Object> body) {
        if (retryAfterHeader != null && !retryAfterHeader.isBlank()) {
            try {
                double s = Double.parseDouble(retryAfterHeader.trim());
                if (s > 0) return (long) (s * 1000);
            } catch (NumberFormatException ignored) {}
        }
        for (Map<String, Object> m : body == null ? List.<Map<String, Object>>of() : List.of(body, detail(body))) {
            if (m.get("retry_after") instanceof Number n && n.doubleValue() > 0) {
                return (long) (n.doubleValue() * 1000);
            }
        }
        return Math.min(1000L * (1L << Math.min(attempt, 30)), 60_000L);
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

    private static String first(Map<String, Object> detail, Map<String, Object> body, String key) {
        String v = str(detail, key);
        return v != null ? v : str(body, key);
    }

    static String extractMessage(Map<String, Object> body, int status, String rawBody) {
        Map<String, Object> d = detail(body);
        if (d.get("message") != null) return String.valueOf(d.get("message"));
        if (body.get("detail") instanceof List<?> items && !items.isEmpty()) {
            var parts = new ArrayList<String>();
            for (Object item : items) {
                if (item instanceof Map<?, ?> m && m.get("msg") != null) {
                    String loc = m.get("loc") instanceof List<?> l
                            ? String.join(".", l.stream().map(String::valueOf).toList()) : null;
                    parts.add(loc != null && !loc.isEmpty() ? loc + ": " + m.get("msg") : String.valueOf(m.get("msg")));
                } else {
                    parts.add(String.valueOf(item));
                }
            }
            return String.join("; ", parts);
        }
        if (body.get("detail") instanceof String s) return s;
        if (body.containsKey("detail") && !(body.get("detail") instanceof Map<?, ?>)) return String.valueOf(body.get("detail"));
        if (body.get("message") != null) return String.valueOf(body.get("message"));
        if (body.isEmpty() && rawBody != null && !rawBody.isBlank()) {
            String t = rawBody.strip();
            return t.length() > 500 ? t.substring(0, 500) + "…" : t;
        }
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

    static Integer optInt(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.intValue() : null;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> asMapList(Object v) {
        if (!(v instanceof List<?> list)) return List.of();
        var out = new ArrayList<Map<String, Object>>();
        for (Object item : list) if (item instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        return Collections.unmodifiableList(out);
    }

    private static String text(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    /** Parse a JSON body; a top-level array is returned as {@code {"items": [...]}}; anything else as empty. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseBody(String json) {
        return parseJson(json);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseJson(String json) {
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
