package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Fetchyr.createWorkflow(). */
public record WorkflowResult(
        String workflowId,
        String name,
        String description,
        String workflowType,
        List<Map<String, Object>> steps,
        boolean isActive,
        String createdAt
) {}
