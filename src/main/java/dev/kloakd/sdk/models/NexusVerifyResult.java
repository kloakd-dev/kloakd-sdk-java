package dev.kloakd.sdk.models;

import java.util.List;

/** Result of Nexus.verify(). */
public record NexusVerifyResult(
        String strategyId,
        boolean verified,
        double safetyScore,
        List<String> violations
) {}
