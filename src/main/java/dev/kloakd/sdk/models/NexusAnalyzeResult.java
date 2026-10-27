package dev.kloakd.sdk.models;

import java.util.Map;

/** Result of Nexus.analyze(). */
public record NexusAnalyzeResult(
        String url,
        String perceptionId,
        String reasoningId,
        Map<String, Object> strategy,
        String pageType,
        double confidence,
        String artifactId
) {}
