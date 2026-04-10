package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Result of Nexus.analyze().
 *
 * @param perceptionId unique identifier for this perception result
 * @param strategy recommended extraction strategy map
 * @param pageType detected page type (e.g. listing, detail, search)
 * @param complexityLevel page complexity rating (low, medium, high)
 * @param artifactId storage artifact ID for the analysis
 * @param durationMs time taken to complete the analysis in milliseconds
 * @param error error message if analysis failed, otherwise null
 */
public record NexusAnalyzeResult(
        String perceptionId,
        Map<String, Object> strategy,
        String pageType,
        String complexityLevel,
        String artifactId,
        int durationMs,
        String error
) {}
