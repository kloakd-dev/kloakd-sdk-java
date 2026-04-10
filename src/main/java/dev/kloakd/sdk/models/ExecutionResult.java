package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Fetchyr.executeWorkflow() / getExecution(). */
public record ExecutionResult(
        String executionId,
        String workflowId,
        String status,
        String startedAt,
        String completedAt,
        List<Map<String, Object>> records,
        String error
) {}
