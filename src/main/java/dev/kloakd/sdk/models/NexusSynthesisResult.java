package dev.kloakd.sdk.models;

/** Result of Nexus.synthesize(). */
public record NexusSynthesisResult(
        String strategyId,
        String strategyName,
        String generatedCode,
        String artifactId,
        int synthesisTimeMs,
        String error
) {}
