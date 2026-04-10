package dev.kloakd.sdk.models;

import java.util.List;

/**
 * Result of Skanyr.discover().
 *
 * @param success whether the discovery succeeded
 * @param discoveryId unique identifier for this discovery job
 * @param url the target URL that was analyzed
 * @param totalEndpoints total number of endpoints discovered
 * @param endpoints list of discovered API endpoints
 * @param hasMore whether additional endpoints are available beyond this result
 * @param total total endpoint count across all pages
 * @param artifactId storage artifact ID for the discovery report
 * @param error error message if success is false, otherwise null
 */
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
