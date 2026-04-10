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
 *   <li>Non-retryable: 400, 401, 403, 404</li>
 *   <li>Strategy: exponential backoff — base 1s × 2^attempt, cap 60s</li>
 *   <li>429: respects {@code Retry-After} header / {@code retry_after} body field</li>
 *   <li>Default max retries: 3</li>
 * </ul>
 */
public class HttpTransport {

    static final String SDK_VERSION = "0.1.0";
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String organizationId;
    private final String baseUrl;
    private final int maxRetries;
    private final Duration timeout;
    private final HttpClient httpClient;

    public HttpTransport(
            String apiKey,
            String organizationId,
            String baseUrl,
            Duration timeout,
            int maxRetries,
            HttpClient httpClient) {
        this.apiKey = apiKey;
        this.organizationId = organizationId;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.httpClient = httpClient != null ? httpClient :
                HttpClient.newBuilder().connectTimeout(timeout).build();
    }

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

        String url = buildUrl(path, params);
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
                KloakdException ex = raiseForStatus(resp.statusCode(), parsed);
                if (ex == null) return parsed;

                if (!RETRYABLE.contains(resp.statusCode())) throw ex;

                lastException = ex;
                if (attempt < maxRetries) {
                    sleep(backoffMs(attempt, resp.headers().firstValue("Retry-After").orElse(null), parsed));
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

    public void delete(String path) {
        request("DELETE", path, null, null);
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
     * Open a server-sent events stream.
     * Returns a {@link Stream} of parsed data payloads (data-only lines).
     * The underlying HTTP connection is closed when the stream is closed.
     */
    public Stream<Map<String, Object>> stream(String path, Map<String, Object> body) {
        String url = buildUrl(path, null);
        byte[] bodyBytes = serialise(body);
        var req = baseRequest(url)
                .header("Accept", "text/event-stream")
                .method("POST", bodyBytes != null
                        ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                        : HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            KloakdException ex = raiseForStatus(resp.statusCode(), Map.of());
            if (ex != null) {
                resp.body().close();
                throw ex;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8));
            return sseDataStream(reader);
        } catch (KloakdException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KloakdException(0, "Interrupted: " + e.getMessage());
        } catch (IOException e) {
            throw new KloakdException(0, "IO error: " + e.getMessage());
        }
    }

    /**
     * Open a server-sent events stream with event-name support.
     * Returns a {@link Stream} of {@code String[2]}: [eventName, dataJson].
     */
    public Stream<String[]> streamWithEvents(String path, Map<String, Object> body) {
        String url = buildUrl(path, null);
        byte[] bodyBytes = serialise(body);
        var req = baseRequest(url)
                .header("Accept", "text/event-stream")
                .method("POST", bodyBytes != null
                        ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                        : HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            KloakdException ex = raiseForStatus(resp.statusCode(), Map.of());
            if (ex != null) {
                resp.body().close();
                throw ex;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8));
            return sseEventStream(reader);
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
        if (status >= 200 && status < 300) return null;
        String msg = extractMessage(body, status);
        return switch (status) {
            case 401 -> new AuthenticationException(msg);
            case 403 -> new NotEntitledException(msg,
                    str(body, "module"), str(body, "upgrade_url"));
            case 429 -> new RateLimitException(msg,
                    intVal(body, "retry_after", 60), str(body, "reset_at"));
            case 502 -> new UpstreamException(msg);
            default -> new ApiException(status, msg);
        };
    }

    public static boolean isRetryable(int status) {
        return RETRYABLE.contains(status);
    }

    // ── Backoff ───────────────────────────────────────────────────────────────

    public static long backoffMs(int attempt, String retryAfterHeader, Map<String, Object> body) {
        if (retryAfterHeader != null && !retryAfterHeader.isBlank()) {
            try {
                return (long) (Double.parseDouble(retryAfterHeader.trim()) * 1000);
            } catch (NumberFormatException ignored) {}
        }
        if (body != null && body.containsKey("retry_after")) {
            try {
                double secs = ((Number) body.get("retry_after")).doubleValue();
                if (secs > 0) return (long) (secs * 1000);
            } catch (Exception ignored) {}
        }
        return Math.min(1000L * (1L << attempt), 60_000L);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String extractMessage(Map<String, Object> body, int status) {
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseBody(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
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
