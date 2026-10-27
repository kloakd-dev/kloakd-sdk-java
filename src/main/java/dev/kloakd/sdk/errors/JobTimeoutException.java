package dev.kloakd.sdk.errors;

/**
 * A request the API handed off as a job (HTTP 202) was still running when
 * the SDK's poll timeout ran out. The job may still finish; the code is
 * {@code job_timeout}.
 */
public class JobTimeoutException extends ApiException {
    private final String jobId;

    public JobTimeoutException(String message, String jobId) {
        super(0, message, "job_timeout");
        this.jobId = jobId;
    }

    /** The job the API handed the request off to. */
    public String getJobId() {
        return jobId;
    }
}
