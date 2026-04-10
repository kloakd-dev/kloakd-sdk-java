package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * A discovered API endpoint.
 *
 * @param url full URL of the discovered endpoint
 * @param method HTTP method (GET, POST, etc.)
 * @param apiType type of API (REST, GraphQL, WebSocket, etc.)
 * @param confidence detection confidence score between 0.0 and 1.0
 * @param parameters map of discovered request parameters
 */
public record ApiEndpoint(
        String url,
        String method,
        String apiType,
        double confidence,
        Map<String, Object> parameters
) {}
