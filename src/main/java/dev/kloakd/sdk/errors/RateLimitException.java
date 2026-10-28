package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 429 {@code rate_limited}. A tier rate limit carries
 * {@link #getRetryAfter()} and {@link #getResetAt()} and is retried by the
 * SDK. The playground (test-key) quota answers {@code retry_after} 0 with no
 * reset: {@link #isQuotaExhausted()} is true, it carries the quota counts,
 * and it is not retried.
 */
public class RateLimitException extends KloakdException {
    private final int retryAfter;
    private final String resetAt;
    private final boolean quotaExhausted;
    private final Integer quotaUsed;
    private final Integer quotaLimit;
    private final String upgradeUrl;

    public RateLimitException(String message, int retryAfter, String resetAt) {
        this(message, retryAfter, resetAt, null);
    }

    public RateLimitException(String message, int retryAfter, String resetAt, String code) {
        this(message, retryAfter, resetAt, code, false, null, null, null);
    }

    public RateLimitException(String message, int retryAfter, String resetAt, String code,
                              boolean quotaExhausted, Integer quotaUsed, Integer quotaLimit, String upgradeUrl) {
        super(429, message, code);
        this.retryAfter = retryAfter;
        this.resetAt = resetAt;
        this.quotaExhausted = quotaExhausted;
        this.quotaUsed = quotaUsed;
        this.quotaLimit = quotaLimit;
        this.upgradeUrl = upgradeUrl;
    }

    /** Seconds to wait before retrying (0 for an exhausted playground quota). */
    public int getRetryAfter() { return retryAfter; }
    /** ISO timestamp when the rate limit resets, or null. */
    public String getResetAt() { return resetAt; }
    /** True for the playground quota: waiting does not clear it. */
    public boolean isQuotaExhausted() { return quotaExhausted; }
    /** Playground requests used ({@code quota_used}), or null. */
    public Integer getQuotaUsed() { return quotaUsed; }
    /** Playground request quota ({@code quota_limit}), or null. */
    public Integer getQuotaLimit() { return quotaLimit; }
    /** Where to upgrade, or null. */
    public String getUpgradeUrl() { return upgradeUrl; }
}
