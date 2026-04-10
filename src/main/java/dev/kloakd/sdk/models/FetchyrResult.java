package dev.kloakd.sdk.models;

/**
 * Result of Fetchyr.fetch().
 *
 * @param success whether the fetch succeeded
 * @param url the target URL that was fetched
 * @param statusCode HTTP status code returned by the target
 * @param html raw HTML body of the response
 * @param artifactId storage artifact ID for the captured page
 * @param sessionArtifactId artifact ID for the authenticated session used
 * @param error error message if success is false, otherwise null
 */
public record FetchyrResult(
        boolean success,
        String url,
        int statusCode,
        String html,
        String artifactId,
        String sessionArtifactId,
        String error
) {}
