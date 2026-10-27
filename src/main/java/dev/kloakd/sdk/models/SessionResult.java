package dev.kloakd.sdk.models;

/**
 * Result of Fetchyr.authenticate().
 *
 * @param success whether the login succeeded
 * @param sessionId the session's ID
 * @param artifactId the authenticated session artifact (pass as sessionArtifactId)
 * @param targetDomain the site's domain
 * @param authMethod how the site authenticated
 * @param error error message if success is false, otherwise null
 */
public record SessionResult(
        boolean success,
        String sessionId,
        String artifactId,
        String targetDomain,
        String authMethod,
        String error
) {}
