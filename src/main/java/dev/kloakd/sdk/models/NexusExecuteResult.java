package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Nexus.execute().
 *
 * @param strategyId        the strategy that ran
 * @param success           whether the execution succeeded
 * @param records           the extracted records
 * @param artifactId        the STRUCTURED_DATA artifact, or null
 * @param durationMs        execution time
 * @param error             the API's {@code error_message}, or null
 * @param executionResultId the id of this execution's stored result, to pass to
 *                          {@code nexus().knowledge(...)}; null if the API did not return one
 */
public record NexusExecuteResult(
        String strategyId,
        boolean success,
        List<Map<String, Object>> records,
        String artifactId,
        int durationMs,
        String error,
        String executionResultId
) {}
