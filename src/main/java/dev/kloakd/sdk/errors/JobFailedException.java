package dev.kloakd.sdk.errors;

/**
 * A request the API handed off as a job (HTTP 202) ended in failure, or its
 * job was not found when polled (results are kept for an hour). The code is
 * {@code job_failed}.
 */
public class JobFailedException extends ApiException {
    private final String jobId;

    public JobFailedException(String message, String jobId) {
        super(200, message, "job_failed");
        this.jobId = jobId;
    }

    /** The job the API handed the request off to. */
    public String getJobId() {
        return jobId;
    }
}
