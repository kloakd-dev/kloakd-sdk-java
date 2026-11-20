package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 503 {@code ROBOTS_CHECK_UNAVAILABLE} with reason
 * {@code exits_refused}: every one of KLOAKD's exits (its proxy providers)
 * refused to open a connection to the site, so its robots.txt could not be
 * read and nothing was fetched. It is not the site's answer and there is
 * nothing to acknowledge: {@code robotsAcknowledgedHosts} does not help.
 * Not retried by the SDK; the refusal is the providers', and asking again
 * at once is unlikely to change it.
 */
public class RobotsExitsRefusedException extends RobotsCheckUnavailableException {
    /** The reason the API sends. */
    public static final String REASON = "exits_refused";

    /**
     * @param message    the API's sentence
     * @param host       the site's host, or null
     * @param retryAfter the {@code Retry-After} the API gave, in seconds
     */
    public RobotsExitsRefusedException(String message, String host, int retryAfter) {
        super(message, REASON, host, retryAfter);
    }
}
