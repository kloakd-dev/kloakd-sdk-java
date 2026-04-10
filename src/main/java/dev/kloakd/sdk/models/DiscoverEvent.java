package dev.kloakd.sdk.models;

import java.util.Map;

/** SSE event yielded by Skanyr.discoverStream(). */
public record DiscoverEvent(
        String type,
        String endpointUrl,
        String apiType,
        Map<String, Object> metadata
) {}
