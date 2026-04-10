package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Fetchyr.executeWorkflow() / getExecution().
 *
 * @param executionId unique identifier for this workflow execution
 * @param workflowId identifier of the workflow that was executed
 * @param status current execution status (pending, running, completed, failed)
 * @param startedAt ISO-8601 timestamp when execution started
 * @param completedAt ISO-8601 timestamp when execution completed, or null if still running
 * @param records extracted records produced by the workflow
 * @param error error message if execution failed, otherwise null
 */
public record ExecutionResult(
        String executionId,
        String workflowId,
        String status,
        String startedAt,
        String completedAt,
        List<Map<String, Object>> records,
        String error
) {}
