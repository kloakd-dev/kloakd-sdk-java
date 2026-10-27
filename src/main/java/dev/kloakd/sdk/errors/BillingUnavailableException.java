package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 503 {@code billing_unavailable}: usage could not be checked,
 * so the request was not started. Retry after {@link #getRetryAfter()} seconds.
 */
public class BillingUnavailableException extends KloakdException {
    private final int retryAfter;

    public BillingUnavailableException(String message, int retryAfter) {
        super(503, message, "billing_unavailable");
        this.retryAfter = retryAfter;
    }

    public int getRetryAfter() { return retryAfter; }
}
