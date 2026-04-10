package dev.kloakd.sdk.models;

/**
 * Result of Fetchyr.detectMfa().
 *
 * @param mfaDetected whether an MFA challenge was detected
 * @param challengeId unique identifier for the detected challenge
 * @param mfaType type of MFA challenge (e.g. totp, sms, email)
 * @param error error message if detection failed, otherwise null
 */
public record MfaDetectionResult(
        boolean mfaDetected,
        String challengeId,
        String mfaType,
        String error
) {}
