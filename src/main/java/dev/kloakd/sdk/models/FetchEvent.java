package dev.kloakd.sdk.models;

/**
 * An event of an Evadr fetch job's stream ({@code evadr/jobs/{id}/events}).
 * The API sends pipeline events when the job runs in the API's own process
 * ({@link #pipelineEvent()} is set and {@code type} is its event type), and
 * otherwise polls the job and sends {@code STATUS}, {@code COMPLETE},
 * {@code FAILED} or {@code ERROR}.
 *
 * @param type {@code STATUS}, {@code COMPLETE}, {@code FAILED}, {@code ERROR},
 *             or a pipeline event type ({@code pipeline_step}, ...)
 * @param jobId the job, or null
 * @param status the job's status ({@code STATUS} events) or the pipeline stage status, or null
 * @param tierUsed the tier that fetched the page ({@code COMPLETE}), or null
 * @param artifactId the fetched page's artifact ({@code COMPLETE}), or null
 * @param error the error ({@code FAILED}, {@code ERROR}; a pipeline error's message), or null
 * @param pipelineEvent the full pipeline event, or null for the polled events
 */
public record FetchEvent(
        String type,
        String jobId,
        String status,
        Integer tierUsed,
        String artifactId,
        String error,
        PipelineEvent pipelineEvent
) {}
