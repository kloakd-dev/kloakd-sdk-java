package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Fetchyr.createWorkflow().
 *
 * @param workflowId unique identifier for the created workflow
 * @param name human-readable name of the workflow
 * @param steps ordered list of workflow step definitions
 * @param url target URL the workflow is configured to run against
 * @param createdAt ISO-8601 timestamp when the workflow was created
 * @param error error message if creation failed, otherwise null
 */
public record WorkflowResult(
        String workflowId,
        String name,
        List<Map<String, Object>> steps,
        String url,
        String createdAt,
        String error
) {}
