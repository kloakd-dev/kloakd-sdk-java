package dev.kloakd.sdk.errors;

/** Thrown for HTTP 429 — quota exceeded. */
public class RateLimitException extends KloakdException {
    private final int retryAfter;
    private final String resetAt;

    public RateLimitException(String message, int retryAfter, String resetAt) {
        super(429, message);
        this.retryAfter = retryAfter;
        this.resetAt = resetAt;
    }

    /** Seconds to wait before retrying. */
    public int getRetryAfter() { return retryAfter; }
    /** ISO timestamp when the rate limit resets, or null. */
    public String getResetAt() { return resetAt; }
}
