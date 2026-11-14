package dev.kloakd.sdk.models;

import java.util.HashMap;
import java.util.Map;

/**
 * New limits for {@code webgrph().continueCrawl} and {@code continueCrawlExtract}
 * ({@code {"max_pages"?, "max_depth"?}}; contract of 14 Nov 2026, section 3).
 * A limit left null is not sent. The API clamps them to the plan and only
 * lets them rise: a lower value is answered with 422 {@code continue_cannot_lower}.
 *
 * @param maxPages the new page limit, or null
 * @param maxDepth the new depth, or null
 */
public record CrawlContinueOptions(Integer maxPages, Integer maxDepth) {

    public static Builder builder() { return new Builder(); }

    /** True when no limit is set: the continue is then sent with no body. */
    public boolean isEmpty() { return maxPages == null && maxDepth == null; }

    /** The request body, or null when no limit is set. */
    public Map<String, Object> toBody() {
        if (isEmpty()) return null;
        var body = new HashMap<String, Object>();
        if (maxPages != null) body.put("max_pages", maxPages);
        if (maxDepth != null) body.put("max_depth", maxDepth);
        return body;
    }

    public static final class Builder {
        private Integer maxPages;
        private Integer maxDepth;

        private Builder() {}

        public Builder maxPages(int maxPages) { this.maxPages = maxPages; return this; }
        public Builder maxDepth(int maxDepth) { this.maxDepth = maxDepth; return this; }

        public CrawlContinueOptions build() { return new CrawlContinueOptions(maxPages, maxDepth); }
    }
}
