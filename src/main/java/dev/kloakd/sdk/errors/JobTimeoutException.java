package dev.kloakd.sdk.errors;

/**
 * A request the API handed off as a job (HTTP 202), or a crawl, was still
 * running when the SDK's poll timeout ran out. The work may still finish:
 * resume with {@link #getJobId()} ({@code client.waitForHandoff(id)},
 * {@code evadr().followJob(id, ...)} or {@code webgrph().getCrawlStatus(id)}).
 * The code is {@code job_timeout} ({@code crawl_timeout} for a crawl).
 */
public class JobTimeoutException extends ApiException {
    private final String jobId;

    public JobTimeoutException(String message, String jobId) {
        this(message, jobId, "job_timeout");
    }

    public JobTimeoutException(String message, String jobId, String code) {
        super(0, message, code);
        this.jobId = jobId;
    }

    /** The job (or crawl) still running; pass it on to resume waiting. */
    public String getJobId() {
        return jobId;
    }
}
