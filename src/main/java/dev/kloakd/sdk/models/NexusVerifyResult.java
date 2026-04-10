package dev.kloakd.sdk.models;

import java.util.List;

/** Result of Nexus.verify(). */
public record NexusVerifyResult(
        String verificationResultId,
        boolean isSafe,
        double riskScore,
        double safetyScore,
        List<String> violations,
        int durationMs,
        String error
) {}
