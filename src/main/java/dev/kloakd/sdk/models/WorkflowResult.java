package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Fetchyr.createWorkflow(). */
public record WorkflowResult(
        String workflowId,
        String name,
        List<Map<String, Object>> steps,
        String url,
        String createdAt,
        String error
) {}
