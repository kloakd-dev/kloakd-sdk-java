package dev.kloakd.sdk.models;

/**
 * Result of Evadr.fetch().
 *
 * @param success whether the fetch succeeded
 * @param url the target URL that was fetched
 * @param statusCode HTTP status code returned by the target
 * @param tierUsed bypass tier that was applied (1–4)
 * @param html raw HTML body of the response
 * @param vendorDetected name of the detected anti-bot vendor, or null
 * @param antiBotBypassed whether the anti-bot system was bypassed
 * @param artifactId storage artifact ID for the captured response
 * @param error error message if success is false, otherwise null
 */
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
