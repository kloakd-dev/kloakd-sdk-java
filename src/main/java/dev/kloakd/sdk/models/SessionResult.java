package dev.kloakd.sdk.models;

/** Result of Fetchyr.login(). */
public record SessionResult(
        boolean success,
        String sessionId,
        String url,
        String artifactId,
        String screenshotUrl,
        String error
) {}
