package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * One event of Parlyr's chat stream ({@code POST parlyr/chat}). The API sends
 * {@code thinking}, {@code interpretation}, {@code progress}, {@code results},
 * {@code error}, {@code warning}, {@code auth_required}, {@code mfa_required},
 * {@code clarification}, {@code export_ready} and {@code text}, then {@code end},
 * where {@code chatStream} stops ({@code end} itself is not yielded).
 *
 * @param type the event type (the SSE event name)
 * @param content the event's text, or null
 * @param data the event's structured payload (empty if none)
 * @param actions the actions offered with it (e.g. {@code {"id": "start", "label": ...}}), empty if none
 * @param timestamp when the API produced it (ISO 8601), or null
 */
public record ChatEvent(
        String type,
        String content,
        Map<String, Object> data,
        List<Map<String, Object>> actions,
        String timestamp
) {
    /** @deprecated use {@link #type()}. */
    @Deprecated
    public String event() { return type; }
}
