package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 503 {@code ROBOTS_CHECK_UNAVAILABLE}: KLOAKD could not read
 * the site's robots.txt because of a failure of its own, so nothing was
 * requested from the page. This is not the site's answer and there is
 * nothing to acknowledge. Retried by the SDK on any method after
 * {@link #getRetryAfter()} seconds, as {@code billing_unavailable} is; see
 * {@link RobotsExitsRefusedException} for the case that is not retried.
 */
public class RobotsCheckUnavailableException extends ApiException {
    /** The error code the API sends. */
    public static final String CODE = "ROBOTS_CHECK_UNAVAILABLE";

    private final String reason;
    private final String host;
    private final int retryAfter;

    /**
     * @param message    the API's sentence
     * @param reason     why, when it is not KLOAKD's capacity (for example {@code exits_refused}), or null
     * @param host       the site's host, or null
     * @param retryAfter seconds to wait ({@code Retry-After}, else {@code retry_after_s}, else 30)
     */
    public RobotsCheckUnavailableException(String message, String reason, String host, int retryAfter) {
        super(503, message, CODE);
        this.reason = reason;
        this.host = host;
        this.retryAfter = retryAfter;
    }

    /** Why the check could not be made, or null for KLOAKD's own capacity. */
    public String getReason() { return reason; }

    /** The site's host, or null. */
    public String getHost() { return host; }

    /** Seconds to wait before asking again. */
    public int getRetryAfter() { return retryAfter; }
}
