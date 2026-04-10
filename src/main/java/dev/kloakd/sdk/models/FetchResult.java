package dev.kloakd.sdk.models;

/** Result of Evadr.fetch(). */
public record FetchResult(
        boolean success,
        String url,
        int statusCode,
        int tierUsed,
        String html,
        String vendorDetected,
        boolean antiBotBypassed,
        String artifactId,
        String error
) {}
