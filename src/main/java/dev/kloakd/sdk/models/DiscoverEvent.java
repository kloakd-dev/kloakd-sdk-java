package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * SSE event yielded by Skanyr.discoverStream().
 *
 * @param type event type (e.g. endpoint_found, done, error)
 * @param endpointUrl URL of the discovered API endpoint
 * @param apiType detected API type (REST, GraphQL, WebSocket, etc.)
 * @param metadata additional discovery metadata
 */
public record DiscoverEvent(
        String type,
        String endpointUrl,
        String apiType,
        Map<String, Object> metadata
) {}
