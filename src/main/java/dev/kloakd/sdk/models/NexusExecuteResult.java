package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Nexus.execute().
 *
 * @param executionResultId unique identifier for this execution result
 * @param success whether the execution succeeded
 * @param records list of extracted records produced by the strategy
 * @param artifactId storage artifact ID for the execution output
 * @param durationMs time taken to complete the execution in milliseconds
 * @param error error message if execution failed, otherwise null
 */
public record NexusExecuteResult(
        String executionResultId,
        boolean success,
        List<Map<String, Object>> records,
        String artifactId,
        int durationMs,
        String error
) {}
