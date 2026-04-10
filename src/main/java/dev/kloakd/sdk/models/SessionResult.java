package dev.kloakd.sdk.models;

/**
 * Result of Fetchyr.login().
 *
 * @param success whether the login succeeded
 * @param sessionId unique identifier for the authenticated session
 * @param url the URL of the page after login
 * @param artifactId storage artifact ID for the session state
 * @param screenshotUrl URL of a post-login screenshot, if captured
 * @param error error message if login failed, otherwise null
 */
public record SessionResult(
        boolean success,
        String sessionId,
        String url,
        String artifactId,
        String screenshotUrl,
        String error
) {}
