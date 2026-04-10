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
