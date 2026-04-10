package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * SSE event yielded by Parlyr.chatStream().
 *
 * @param event event type identifier (e.g. token, done, error)
 * @param data event payload containing turn data
 */
public record ChatEvent(
        String event,
        Map<String, Object> data
) {}
