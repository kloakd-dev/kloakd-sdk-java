package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Parlyr.chat(): one turn, assembled from its event stream.
 *
 * @param sessionId conversation session identifier
 * @param intent the last {@code interpretation} event's {@code data.intent}, or
 *               null: the API's interpretation data has no intent key today
 *               (its plan is in {@code interpretation})
 * @param response the {@code content} of the turn's {@code text} and
 *                 {@code clarification} events, joined with newlines ("" if none)
 * @param clarification the last {@code clarification} event's content, or null
 * @param interpretation the last {@code interpretation} event's {@code data}
 *                       (the plan: {@code url}, {@code entity}, {@code fields}, ...), or null
 * @param results the last {@code results} event's {@code data}, or null
 * @param warnings the turn's {@code warning} events
 * @param errors the turn's {@code error} events (the turn is then not {@link #ok()})
 * @param actions the actions offered by the last event that had any (empty if none)
 * @param events every event of the turn, in order ({@code end} excluded)
 */
public record ChatTurn(
        String sessionId,
        String intent,
        String response,
        String clarification,
        Map<String, Object> interpretation,
        Map<String, Object> results,
        List<ChatEvent> warnings,
        List<ChatEvent> errors,
        List<Map<String, Object>> actions,
        List<ChatEvent> events
) {
    /** True when the turn had no {@code error} event. */
    public boolean ok() { return errors.isEmpty(); }
}
