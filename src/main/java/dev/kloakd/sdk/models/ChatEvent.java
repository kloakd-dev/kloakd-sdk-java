package dev.kloakd.sdk.models;

import java.util.Map;

/** SSE event yielded by Parlyr.chatStream(). */
public record ChatEvent(
        String event,
        Map<String, Object> data
) {}
