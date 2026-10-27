package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Nexus.synthesize(). */
public record NexusSynthesisResult(
        String strategyId,
        String strategyName,
        String strategyType,
        String program,
        double confidence,
        List<Map<String, Object>> steps,
        int synthesisTimeMs
) {}
