package dev.kloakd.sdk.models;

import java.util.Map;

/** Result of Fetchyr.executeWorkflow() / getExecution() / resumeExecution(). */
public record ExecutionResult(
        String executionId,
        String workflowId,
        String status,
        String startedAt,
        String completedAt,
        Map<String, Object> result,
        String error
) {}
