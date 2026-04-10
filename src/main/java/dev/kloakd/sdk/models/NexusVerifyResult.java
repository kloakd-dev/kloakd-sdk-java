package dev.kloakd.sdk.models;

import java.util.List;

/**
 * Result of Nexus.verify().
 *
 * @param verificationResultId unique identifier for this verification result
 * @param isSafe whether the strategy passed all safety checks
 * @param riskScore risk score between 0.0 (no risk) and 1.0 (maximum risk)
 * @param safetyScore safety score between 0.0 and 1.0
 * @param violations list of safety rule violations detected
 * @param durationMs time taken to complete verification in milliseconds
 * @param error error message if verification failed, otherwise null
 */
public record NexusVerifyResult(
        String verificationResultId,
        boolean isSafe,
        double riskScore,
        double safetyScore,
        List<String> violations,
        int durationMs,
        String error
) {}
