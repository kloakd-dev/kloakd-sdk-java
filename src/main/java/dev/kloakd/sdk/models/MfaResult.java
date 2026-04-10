package dev.kloakd.sdk.models;

/**
 * Result of Fetchyr.submitMfa().
 *
 * @param success whether the MFA challenge was solved
 * @param sessionArtifactId artifact ID for the authenticated session after MFA
 * @param error error message if submission failed, otherwise null
 */
public record MfaResult(
        boolean success,
        String sessionArtifactId,
        String error
) {}
