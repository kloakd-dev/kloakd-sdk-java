package dev.kloakd.sdk.errors;

import java.util.Map;

/**
 * Thrown for HTTP 409 {@code ROBOTS_DISALLOWED}: the site's robots.txt stops
 * the request, and nothing was requested from the page (kobe-scraper
 * {@code core/compliance/robots.py} {@code stop_detail}). The reason is
 * {@code disallowed} (a rule forbids the page) or {@code unreadable} (the
 * file could not be read: a 5xx, a timeout or a dropped connection).
 *
 * <p>To go past it, call again with the stopped host named in
 * {@code robotsAcknowledgedHosts} (for example
 * {@code FetchOptions.builder().robotsAcknowledgedHosts(e.getHost())}). By
 * naming a host you confirm you have the right to access its content
 * despite its robots.txt and accept responsibility for doing so under the
 * Terms ({@link #getTermsVersion()}); KLOAKD records each acknowledgement.
 * It counts for that request and those hosts only. The SDK never
 * acknowledges on its own and does not retry this error.
 */
public class RobotsDisallowedException extends ApiException {
    /** The error code the API sends. */
    public static final String CODE = "ROBOTS_DISALLOWED";
    /** {@link #getReason()} when a robots.txt rule forbids the page. */
    public static final String REASON_DISALLOWED = "disallowed";
    /** {@link #getReason()} when robots.txt could not be read. */
    public static final String REASON_UNREADABLE = "unreadable";
    /** The request field that acknowledges a stop. */
    public static final String ACKNOWLEDGE_FIELD = "robots_acknowledged_hosts";

    private final String reason;
    private final String host;
    private final String rule;
    private final String url;
    private final String redirectedFrom;
    private final String termsVersion;
    private final String acknowledgeField;

    /**
     * @param message        the API's sentence
     * @param reason         {@code disallowed} or {@code unreadable}
     * @param host           the host to name in {@code robotsAcknowledgedHosts}
     * @param rule           the robots.txt rule that applied, or null
     * @param url            the page that was stopped, or null
     * @param redirectedFrom the URL asked for when the stop is where it redirected, or null
     * @param termsVersion   the terms an acknowledgement is recorded under, or null
     * @param acknowledgeField the request field that acknowledges ({@code robots_acknowledged_hosts})
     */
    public RobotsDisallowedException(String message, String reason, String host, String rule, String url,
            String redirectedFrom, String termsVersion, String acknowledgeField) {
        super(409, message, CODE);
        this.reason = reason;
        this.host = host;
        this.rule = rule;
        this.url = url;
        this.redirectedFrom = redirectedFrom;
        this.termsVersion = termsVersion;
        this.acknowledgeField = acknowledgeField != null ? acknowledgeField : ACKNOWLEDGE_FIELD;
    }

    /**
     * The stop in an API answer's {@code detail} (or a chat {@code error}
     * event's {@code data}), or null when it is not a robots.txt stop.
     *
     * @param detail the stop's fields ({@code code} or {@code error} {@code ROBOTS_DISALLOWED})
     * @param message the message to carry, or null for the detail's own
     */
    public static RobotsDisallowedException fromDetail(Map<String, ?> detail, String message) {
        if (detail == null) return null;
        if (!CODE.equals(text(detail, "error")) && !CODE.equals(text(detail, "code"))) return null;
        String msg = message != null ? message : text(detail, "message");
        return new RobotsDisallowedException(msg != null ? msg : CODE, text(detail, "reason"), text(detail, "host"),
                text(detail, "rule"), text(detail, "url"), text(detail, "redirected_from"),
                text(detail, "terms_version"), text(detail, "acknowledge_field"));
    }

    private static String text(Map<String, ?> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    /** {@code disallowed} or {@code unreadable}. */
    public String getReason() { return reason; }

    /** The host to name in {@code robotsAcknowledgedHosts} to go past this stop. */
    public String getHost() { return host; }

    /** The robots.txt rule that applied (for example {@code Disallow: /}), or null. */
    public String getRule() { return rule; }

    /** The page that was stopped, or null. */
    public String getUrl() { return url; }

    /** The URL asked for, when the stop is at the page it redirected to; otherwise null. */
    public String getRedirectedFrom() { return redirectedFrom; }

    /** The version of the Terms an acknowledgement is recorded under, or null. */
    public String getTermsVersion() { return termsVersion; }

    /** The request field that acknowledges a stop: {@code robots_acknowledged_hosts}. */
    public String getAcknowledgeField() { return acknowledgeField; }

    /** True when robots.txt could not be read (rather than a rule forbidding the page). */
    public boolean isUnreadable() { return REASON_UNREADABLE.equals(reason); }
}
