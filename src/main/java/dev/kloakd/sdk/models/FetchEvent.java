package dev.kloakd.sdk.models;

import java.util.Map;

/** SSE event yielded by Evadr.fetchStream(). */
public record FetchEvent(
        String type,
        Integer tier,
        String vendor,
        Map<String, Object> metadata
) {}
