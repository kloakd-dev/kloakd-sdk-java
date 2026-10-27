package dev.kloakd.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** Shared test utilities. */
public final class TestHelpers {

    static final String TEST_API_KEY = "sk-test-fixture-key";
    static final String TEST_ORG_ID  = "00000000-0000-0000-0000-000000000001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestHelpers() {}

    /** Build a Kloakd client pointed at the given MockWebServer. */
    public static Kloakd client(MockWebServer server) {
        return Kloakd.builder()
                .apiKey(TEST_API_KEY)
                .organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString())
                .maxRetries(0)
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    /** Enqueue a JSON response with the given status and body. */
    public static void enqueue(MockWebServer server, int status, Object body) {
        try {
            server.enqueue(new MockResponse()
                    .setResponseCode(status)
                    .addHeader("Content-Type", "application/json")
                    .setBody(MAPPER.writeValueAsString(body)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Enqueue an SSE response — data-only lines. */
    public static void enqueueSse(MockWebServer server, Map<String, Object>... payloads) {
        var sb = new StringBuilder();
        for (var p : payloads) {
            try {
                sb.append("data: ").append(MAPPER.writeValueAsString(p)).append("\n\n");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(sb.toString()));
    }

    /** Enqueue an SSE response with event names. */
    public static void enqueueSseWithEvents(MockWebServer server,
            String[] events, Map<String, Object>[] payloads) {
        var sb = new StringBuilder();
        for (int i = 0; i < events.length; i++) {
            try {
                sb.append("event: ").append(events[i]).append("\n");
                sb.append("data: ").append(MAPPER.writeValueAsString(payloads[i])).append("\n\n");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(sb.toString()));
    }

    public static MockWebServer startServer() throws IOException {
        var server = new MockWebServer();
        server.start();
        return server;
    }

    /**
     * A dispatcher answering by "METHOD path-after-org-prefix" (query ignored);
     * each route's responses are served in order, the last one repeating.
     * Unknown routes get 404. {@link #seen} records every request.
     */
    public static final class Routes extends okhttp3.mockwebserver.Dispatcher {
        private final Map<String, java.util.List<MockResponse>> routes = new java.util.concurrent.ConcurrentHashMap<>();
        public final java.util.List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        public final Map<String, Map<String, Object>> bodies = new java.util.concurrent.ConcurrentHashMap<>();

        public Routes json(String route, int status, Object body) {
            try {
                return add(route, new MockResponse().setResponseCode(status)
                        .addHeader("Content-Type", "application/json")
                        .setBody(MAPPER.writeValueAsString(body)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Routes sse(String route, Map<String, Object>... payloads) {
            var sb = new StringBuilder();
            for (var p : payloads) {
                try {
                    sb.append("data: ").append(MAPPER.writeValueAsString(p)).append("\n\n");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return add(route, new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "text/event-stream").setBody(sb.toString()));
        }

        public Routes add(String route, MockResponse response) {
            routes.computeIfAbsent(route, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(response);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
            String path = request.getPath() == null ? "" : request.getPath().split("\\?")[0];
            String key = request.getMethod() + " " + path.replaceFirst("^/api/v1/organizations/" + TEST_ORG_ID + "/", "");
            seen.add(key);
            String raw = request.getBody().readUtf8();
            try {
                bodies.put(key, raw.isEmpty() ? Map.of() : MAPPER.readValue(raw, Map.class));
            } catch (Exception ignored) {
                bodies.put(key, Map.of());
            }
            var list = routes.get(key);
            if (list == null || list.isEmpty()) {
                return new MockResponse().setResponseCode(404).setBody("{\"detail\":\"Not Found\"}");
            }
            return list.size() > 1 ? list.remove(0) : list.get(0);
        }
    }

    /** Point a server at a route dispatcher and return it. */
    public static Routes routes(MockWebServer server) {
        var r = new Routes();
        server.setDispatcher(r);
        return r;
    }

    /** Parse a recorded request body as Map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> requestBody(okhttp3.mockwebserver.RecordedRequest req) {
        try {
            return MAPPER.readValue(req.getBody().readUtf8(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
