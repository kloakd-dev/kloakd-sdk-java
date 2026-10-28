package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * A pipeline event from an SSE stream ({@code webgrph/crawl/{id}/events},
 * {@code evadr/jobs/{id}/events}). The API's
 * {@code core/pipeline/models.py} PipelineEvent.
 *
 * @param eventType {@code pipeline_step}, {@code pipeline_error} or {@code pipeline_complete}
 * @param stageId the stage this event reports on, or null
 * @param status the stage status ({@code pending}, {@code in_progress}, ...), or null
 * @param message human-readable text, or null
 * @param progressPct progress 0–100, or null
 * @param metadata extra data; on a crawl's {@code pipeline_complete} it may hold {@code discovery_response}
 * @param error the structured error ({@code error_code}, {@code user_message}, ...), or null
 * @param sequence the event's sequence number (the stream's resume cursor), or null
 * @param pipelineId the crawl or job id, or null
 * @param phase the pipeline phase, or null
 * @param subStatus finer progress text, or null
 * @param timestamp when it was emitted (ISO 8601), or null
 */
public record PipelineEvent(
        String eventType,
        String stageId,
        String status,
        String message,
        Double progressPct,
        Map<String, Object> metadata,
        Map<String, Object> error,
        Integer sequence,
        String pipelineId,
        String phase,
        String subStatus,
        String timestamp
) {}
