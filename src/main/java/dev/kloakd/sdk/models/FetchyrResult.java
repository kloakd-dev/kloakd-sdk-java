package dev.kloakd.sdk.models;

/** Result of Fetchyr.fetch(). */
public record FetchyrResult(
        boolean success,
        String url,
        int statusCode,
        String html,
        String artifactId,
        String sessionArtifactId,
        String error
) {}
