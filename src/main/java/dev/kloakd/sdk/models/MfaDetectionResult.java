package dev.kloakd.sdk.models;

/** Result of Fetchyr.detectMfa(). */
public record MfaDetectionResult(
        boolean mfaDetected,
        String challengeId,
        String mfaType,
        String error
) {}
