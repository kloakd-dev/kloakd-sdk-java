package dev.kloakd.sdk.models;

import java.util.List;

/** Result of Skanyr.discover(). */
public record DiscoverResult(
        boolean success,
        String discoveryId,
        String url,
        int totalEndpoints,
        List<ApiEndpoint> endpoints,
        boolean hasMore,
        int total,
        String artifactId,
        String error
) {}
