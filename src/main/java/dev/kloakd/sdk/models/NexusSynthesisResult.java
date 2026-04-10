package dev.kloakd.sdk.models;

/**
 * Result of Nexus.synthesize().
 *
 * @param strategyId unique identifier for the generated strategy
 * @param strategyName human-readable name for the strategy
 * @param generatedCode generated extraction code for the strategy
 * @param artifactId storage artifact ID for the strategy artifact
 * @param synthesisTimeMs time taken to synthesize the strategy in milliseconds
 * @param error error message if synthesis failed, otherwise null
 */
public record NexusSynthesisResult(
        String strategyId,
        String strategyName,
        String generatedCode,
        String artifactId,
        int synthesisTimeMs,
        String error
) {}
