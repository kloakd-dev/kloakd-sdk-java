package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 409 {@code cost_acknowledgement_required}: the job's estimate
 * requires consent ({@code CrawlEstimate.consentRequired()}) and the request
 * did not carry a valid {@code estimate_id} with {@code cost_acknowledged: true}
 * (cost-control contract of 15 Nov 2026, section 4). Estimate again and start
 * the job with {@code CrawlOptions.builder().acknowledge(estimate)}. The API
 * refuses a dashboard session this way; API keys are not refused.
 */
public class CostAcknowledgementRequiredException extends ApiException {
    /** The error code the API sends. */
    public static final String CODE = "cost_acknowledgement_required";

    /** @param message the API's message */
    public CostAcknowledgementRequiredException(String message) {
        super(409, message, CODE);
    }
}
