package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.util.*;
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
     * Send a message and block until the turn's stream ends ({@code end}).
     * {@link ChatTurn#response()} joins the {@code text} and
     * {@code clarification} contents; the plan is in
     * {@link ChatTurn#interpretation()}, extracted data in {@link ChatTurn#results()}.
     * {@code error} events do not throw: they are in {@link ChatTurn#errors()}
     * and make {@link ChatTurn#ok()} false.
     */
    public ChatTurn chat(String sessionId, String message) {
        var events = new ArrayList<ChatEvent>();
        var warnings = new ArrayList<ChatEvent>();
        var errors = new ArrayList<ChatEvent>();
        var response = new StringBuilder();
        String clarification = null;
        Map<String, Object> interpretation = null;
        Map<String, Object> results = null;
        List<Map<String, Object>> actions = List.of();
        try (var stream = chatStream(sessionId, message)) {
            for (var it = stream.iterator(); it.hasNext(); ) {
                var e = it.next();
                events.add(e);
                if (!e.actions().isEmpty()) actions = e.actions();
                switch (e.type()) {
                    case "text", "clarification" -> {
                        if (e.content() != null && !e.content().isEmpty()) {
                            if (!response.isEmpty()) response.append('\n');
                            response.append(e.content());
                        }
                        if ("clarification".equals(e.type())) clarification = e.content();
                    }
                    case "interpretation" -> interpretation = e.data();
                    case "results" -> results = e.data();
                    case "warning" -> warnings.add(e);
                    case "error" -> errors.add(e);
                    default -> { }
                }
            }
        }
        String intent = interpretation != null && interpretation.get("intent") instanceof String s ? s : null;
        return new ChatTurn(sessionId, intent, response.toString(), clarification, interpretation, results,
                List.copyOf(warnings), List.copyOf(errors), actions, List.copyOf(events));
    }

    /**
     * Stream a conversation turn's events as they arrive; the stream ends at
     * the API's {@code end} event (which is not yielded). Close the stream
     * (try-with-resources) to stop early.
     */
    public Stream<ChatEvent> chatStream(String sessionId, String message) {
        var body = new HashMap<String, Object>();
        body.put("session_id", sessionId);
        body.put("message", message);
        return t.streamEvents("parlyr/chat", body)
                .takeWhile(e -> !"end".equals(e.event()))
                .map(e -> parseChatEvent(e.event(), t.parseBody(e.data())));
    }

    public void deleteSession(String sessionId) {
        t.delete("parlyr/chat/" + seg(sessionId));
    }

    /** A chat event: {@code {type, content, data, actions, timestamp}} (chat_orchestrator.py ChatEvent). */
    static ChatEvent parseChatEvent(String eventName, Map<String, Object> m) {
        String type = str(m, "type") != null ? str(m, "type") : eventName;
        return new ChatEvent(type, str(m, "content"), mapField(m, "data"), mapList(m, "actions"),
                str(m, "timestamp"));
    }

    static ParseResult parseParseResult(Map<String, Object> m) {
        return new ParseResult(str(m, "intent"), dbl(m, "confidence"), integer(m, "tier"),
                str(m, "source"), mapField(m, "entities"), bool(m, "requires_action"),
                str(m, "clarification_needed"), str(m, "reasoning"), str(m, "detected_url"));
    }
}
