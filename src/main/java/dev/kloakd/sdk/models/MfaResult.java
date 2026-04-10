package dev.kloakd.sdk.models;

/** Result of Fetchyr.submitMfa(). */
public record MfaResult(
        boolean success,
        String sessionArtifactId,
        String error
) {}
