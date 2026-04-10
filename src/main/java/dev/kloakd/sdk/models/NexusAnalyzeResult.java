package dev.kloakd.sdk.models;

import java.util.Map;

/** Result of Nexus.analyze(). */
public record NexusAnalyzeResult(
        String perceptionId,
        Map<String, Object> strategy,
        String pageType,
        String complexityLevel,
        String artifactId,
        int durationMs,
        String error
) {}
