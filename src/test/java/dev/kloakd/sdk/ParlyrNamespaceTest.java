package dev.kloakd.sdk;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

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

    @SuppressWarnings("unchecked")
    @Test
    void chatStream_yieldsEvents() {
        enqueueSseWithEvents(server,
                new String[]{"intent", "response", "done"},
                new Map[]{
                        Map.of("intent", "scrape_site", "confidence", 0.95,
                                "tier", 2, "entities", Map.of(), "requires_action", true),
                        Map.of("content", "Scraping now."),
                        Map.of()
                });

        var events = client.parlyr().chatStream("sess-001", "scrape example.com").toList();

        assertEquals(3, events.size());
        assertEquals("intent", events.get(0).event());
        assertEquals("response", events.get(1).event());
        assertEquals("done", events.get(2).event());
    }

    @SuppressWarnings("unchecked")
    @Test
    void chat_assemblesTurnFromStream() {
        enqueueSseWithEvents(server,
                new String[]{"intent", "response", "end"},
                new Map[]{
                        Map.of("intent", "scrape_site", "confidence", 0.95,
                                "tier", 2, "entities", Map.of(), "requires_action", true),
                        Map.of("content", "Scraping now."),
                        Map.of()
                });

        var turn = client.parlyr().chat("sess-001", "scrape example.com");

        assertEquals("sess-001", turn.sessionId());
        assertEquals("scrape_site", turn.intent());
        assertEquals("Scraping now.", turn.response());
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
