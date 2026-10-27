package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Nexus.execute(). */
public record NexusExecuteResult(
        String strategyId,
        boolean success,
        List<Map<String, Object>> records,
        String artifactId,
        int durationMs,
        String error
) {}
