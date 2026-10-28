package dev.kloakd.sdk;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import dev.kloakd.sdk.models.ChatEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

class ParlyrNamespaceTest {

    private MockWebServer server;
    private Kloakd client;

    @BeforeEach
    void setUp() throws Exception {
        server = startServer();
        client = client(server);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parse_parsesResult() {
        enqueue(server, 200, Map.of(
                "intent", "scrape_site", "confidence", 0.97, "tier", 1,
                "source", "fast_match", "entities", Map.of("url", "https://example.com"),
                "requires_action", true, "detected_url", "https://example.com"));

        var result = client.parlyr().parse("scrape example.com");

        assertEquals("scrape_site", result.intent());
        assertEquals(0.97, result.confidence(), 0.001);
        assertEquals("https://example.com", result.detectedUrl());
        assertTrue(result.requiresAction());
    }

    @Test
    void parse_withSessionId_sendsBody() throws Exception {
        enqueue(server, 200, Map.of(
                "intent", "help", "confidence", 0.9, "tier", 1,
                "source", "fast_match", "entities", Map.of(), "requires_action", false));
        client.parlyr().parse("help me", "sess-001");

        assertEquals("sess-001", requestBody(server.takeRequest()).get("session_id"));
    }

    /** A chat event as chat_orchestrator.py ChatEvent.to_json writes it. */
    private static Map<String, Object> ev(String type, String content, Map<String, Object> data, List<Map<String, Object>> actions) {
        var m = new HashMap<String, Object>();
        m.put("type", type);
        m.put("content", content);
        m.put("data", data);
        m.put("actions", actions);
        m.put("timestamp", "2026-10-28T10:00:00");
        return m;
    }

    @SuppressWarnings("unchecked")
    private void enqueueTurn(Object[][] events) {
        var names = new String[events.length + 1];
        var payloads = new Map[events.length + 1];
        for (int i = 0; i < events.length; i++) {
            names[i] = (String) events[i][0];
            payloads[i] = (Map<String, Object>) events[i][1];
        }
        names[events.length] = "end";
        payloads[events.length] = Map.of();
        enqueueSseWithEvents(server, names, payloads);
    }

    @Test
    void chatStream_yieldsTheRealEventFields() {
        var plan = Map.<String, Object>of("url", "https://example.com", "entity", "products");
        enqueueTurn(new Object[][]{
                {"thinking", ev("thinking", "Reading your request", null, null)},
                {"interpretation", ev("interpretation", "I'll extract products from https://example.com.", plan,
                        List.of(Map.of("id", "start", "label", "Start", "primary", true), Map.of("id", "cancel", "label", "Cancel")))},
        });

        var events = client.parlyr().chatStream("sess-001", "scrape example.com").toList();

        assertEquals(List.of("thinking", "interpretation"), events.stream().map(ChatEvent::type).toList(),
                "the stream stops at end");
        var interp = events.get(1);
        assertEquals("I'll extract products from https://example.com.", interp.content());
        assertEquals("products", interp.data().get("entity"));
        assertEquals("start", interp.actions().get(0).get("id"));
        assertEquals("2026-10-28T10:00:00", interp.timestamp());
        assertEquals(Map.of(), events.get(0).data(), "a null data is empty");
    }

    @Test
    void chat_buildsTheResponseFromTextAndClarification() {
        var plan = Map.<String, Object>of("url", "https://example.com", "entity", "products", "fields", List.of("price"));
        var results = Map.<String, Object>of("records", List.of(Map.of("price", "9")), "total", 1);
        enqueueTurn(new Object[][]{
                {"thinking", ev("thinking", "Thinking", null, null)},
                {"interpretation", ev("interpretation", "I'll extract products.", plan,
                        List.of(Map.of("id", "start", "label", "Start")))},
                {"results", ev("results", "Found 1", results, List.of(Map.of("id", "export_csv", "label", "CSV")))},
                {"text", ev("text", "Here are your products.", null, null)},
                {"clarification", ev("clarification", "Want more pages?", null, null)},
        });

        var turn = client.parlyr().chat("sess-001", "scrape example.com");

        assertEquals("sess-001", turn.sessionId());
        assertEquals("Here are your products.\nWant more pages?", turn.response());
        assertEquals("Want more pages?", turn.clarification());
        assertEquals(plan, turn.interpretation());
        assertEquals(results, turn.results());
        assertEquals("export_csv", turn.actions().get(0).get("id"));
        assertEquals(5, turn.events().size());
        assertTrue(turn.ok());
        assertNull(turn.intent(), "the API's interpretation data has no intent key");
    }

    @Test
    void chat_anErrorEventIsCollectedNotThrown() {
        enqueueTurn(new Object[][]{
                {"thinking", ev("thinking", "Thinking", null, null)},
                {"warning", ev("warning", "This may take long", null, null)},
                {"interpretation", ev("interpretation", "plan", Map.of("intent", "scrape"), null)},
                {"error", ev("error", "The site could not be reached.", Map.of("code", "fetch_failed"),
                        List.of(Map.of("id", "retry", "label", "Retry")))},
        });

        var turn = client.parlyr().chat("sess-001", "scrape x");
        assertFalse(turn.ok());
        assertEquals("The site could not be reached.", turn.errors().get(0).content());
        assertEquals("fetch_failed", turn.errors().get(0).data().get("code"));
        assertEquals("retry", turn.actions().get(0).get("id"));
        assertEquals("This may take long", turn.warnings().get(0).content());
        assertEquals("scrape", turn.intent(), "an intent key, if the API adds one, is read");
    }

    @Test
    void chat_withoutTextHasAnEmptyResponse() {
        enqueueTurn(new Object[][]{{"thinking", ev("thinking", "Thinking", null, null)}});

        var turn = client.parlyr().chat("sess-001", "hello");

        assertEquals("", turn.response());
        assertNull(turn.interpretation());
        assertNull(turn.results());
        assertEquals(List.of(), turn.actions());
    }

    @Test
    void deleteSession_sendsDelete() throws Exception {
        enqueue(server, 200, Map.of());
        client.parlyr().deleteSession("sess-001");

        var req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertTrue(req.getPath().contains("sess-001"));
    }
}
