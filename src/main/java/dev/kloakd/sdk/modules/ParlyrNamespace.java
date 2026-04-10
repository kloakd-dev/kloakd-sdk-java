package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static dev.kloakd.sdk.modules.EvadrNamespace.*;

/** Conversational NLP module. Access via {@code client.parlyr()}. */
public final class ParlyrNamespace {

    private final HttpTransport t;

    public ParlyrNamespace(HttpTransport t) { this.t = t; }

    public ParseResult parse(String message) {
        return parse(message, null);
    }

    public ParseResult parse(String message, String sessionId) {
        var body = new HashMap<String, Object>();
        body.put("message", message);
        if (sessionId != null) body.put("session_id", sessionId);
        return parseParseResult(t.post("parlyr/parse", body));
    }

    /**
     * Send a message and block until the full response is assembled from the SSE stream.
     */
    public ChatTurn chat(String sessionId, String message) {
        var body = new java.util.HashMap<String, Object>();
        body.put("session_id", sessionId);
        body.put("message", message);
        var turn = new ChatTurnBuilder(sessionId);
        try (var stream = t.streamWithEvents("parlyr/chat", body)) {
            stream.forEach(pair -> {
                String event = pair[0];
                Map<String, Object> data = t.parseBody(pair[1]);
                switch (event) {
                    case "intent" -> {
                        turn.intent      = str(data, "intent");
                        turn.confidence  = dbl(data, "confidence");
                        turn.tier        = integer(data, "tier");
                        turn.entities    = mapField(data, "entities");
                        turn.requiresAction = bool(data, "requires_action");
                    }
                    case "response" -> turn.response = str(data, "content");
                    case "clarification" -> turn.clarificationNeeded = str(data, "message");
                }
            });
        }
        return turn.build();
    }

    /**
     * Stream a conversation turn as SSE events.
     * Caller is responsible for closing the stream (use try-with-resources or {@code .close()}).
     */
    public Stream<ChatEvent> chatStream(String sessionId, String message) {
        var body = new java.util.HashMap<String, Object>();
        body.put("session_id", sessionId);
        body.put("message", message);
        return t.streamWithEvents("parlyr/chat", body)
                .map(pair -> new ChatEvent(pair[0], t.parseBody(pair[1])));
    }

    public void deleteSession(String sessionId) {
        t.delete("parlyr/chat/" + sessionId);
    }

    static ParseResult parseParseResult(Map<String, Object> m) {
        return new ParseResult(str(m, "intent"), dbl(m, "confidence"), integer(m, "tier"),
                str(m, "source"), mapField(m, "entities"), bool(m, "requires_action"),
                str(m, "clarification_needed"), str(m, "reasoning"), str(m, "detected_url"));
    }

    private static class ChatTurnBuilder {
        final String sessionId;
        String intent = "";
        double confidence;
        int tier;
        String response = "";
        Map<String, Object> entities = Map.of();
        boolean requiresAction;
        String clarificationNeeded;

        ChatTurnBuilder(String sessionId) { this.sessionId = sessionId; }

        ChatTurn build() {
            return new ChatTurn(sessionId, intent, confidence, tier, response,
                    entities, requiresAction, clarificationNeeded);
        }
    }
}
