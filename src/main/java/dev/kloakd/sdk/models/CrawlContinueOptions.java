package dev.kloakd.sdk.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * New limits for {@code webgrph().continueCrawl} and {@code continueCrawlExtract}
 * ({@code {"max_pages"?, "max_depth"?, "max_data_mb"?}}; contract of 14 Nov 2026,
 * section 3, and the cost-control contract of 15 Nov 2026, section 3).
 * A limit left null is not sent. The API clamps them to the plan and only
 * lets them rise: a lower value is answered with 422 {@code continue_cannot_lower}.
 * The job's {@code urls}, {@code include_paths} and {@code include_resources}
 * are kept by the API.
 *
 * @param maxPages  the new page limit, or null
 * @param maxDepth  the new depth, or null
 * @param maxDataMb the new data limit in MB, or null (a job that stopped at its
 *                  data limit, {@code partial_reason} {@code data_limit}, continues with a higher one)
 * @param robotsAcknowledgedHosts hosts whose robots.txt stop you acknowledge for this continue
 *                  ({@code robots_acknowledged_hosts}; a page the crawl goes on to may be stopped);
 *                  recorded by KLOAKD, this request and these hosts only. Null or empty is not sent
 */
public record CrawlContinueOptions(Integer maxPages, Integer maxDepth, Double maxDataMb,
        List<String> robotsAcknowledgedHosts) {

    /** The 14 Nov components only (no data limit). */
    public CrawlContinueOptions(Integer maxPages, Integer maxDepth) {
        this(maxPages, maxDepth, null, null);
    }

    /** The cost-control components (before robots.txt acknowledgements). */
    public CrawlContinueOptions(Integer maxPages, Integer maxDepth, Double maxDataMb) {
        this(maxPages, maxDepth, maxDataMb, null);
    }

    public static Builder builder() { return new Builder(); }

    /** True when no option is set: the continue is then sent with no body. */
    public boolean isEmpty() {
        return maxPages == null && maxDepth == null && maxDataMb == null
                && (robotsAcknowledgedHosts == null || robotsAcknowledgedHosts.isEmpty());
    }

    /** The request body, or null when no option is set. */
    public Map<String, Object> toBody() {
        if (isEmpty()) return null;
        var body = new HashMap<String, Object>();
        if (maxPages != null) body.put("max_pages", maxPages);
        if (maxDepth != null) body.put("max_depth", maxDepth);
        if (maxDataMb != null) body.put("max_data_mb", maxDataMb);
        if (robotsAcknowledgedHosts != null && !robotsAcknowledgedHosts.isEmpty()) {
            body.put("robots_acknowledged_hosts", robotsAcknowledgedHosts);
        }
        return body;
    }

    public static final class Builder {
        private Integer maxPages;
        private Integer maxDepth;
        private Double maxDataMb;
        private List<String> robotsAcknowledgedHosts;

        private Builder() {}

        public Builder maxPages(int maxPages) { this.maxPages = maxPages; return this; }
        public Builder maxDepth(int maxDepth) { this.maxDepth = maxDepth; return this; }
        public Builder maxDataMb(double maxDataMb) { this.maxDataMb = maxDataMb; return this; }
        /** Hosts whose robots.txt stop you acknowledge for this continue ({@code robots_acknowledged_hosts}). */
        public Builder robotsAcknowledgedHosts(List<String> hosts) { this.robotsAcknowledgedHosts = hosts == null ? null : List.copyOf(hosts); return this; }
        /** {@link #robotsAcknowledgedHosts(List)} with these hosts. */
        public Builder robotsAcknowledgedHosts(String... hosts) { return robotsAcknowledgedHosts(List.of(hosts)); }

        public CrawlContinueOptions build() { return new CrawlContinueOptions(maxPages, maxDepth, maxDataMb, robotsAcknowledgedHosts); }
    }
}
