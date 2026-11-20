package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Options for the high-level {@code client.crawl()} orchestrator and for
 * {@code webgrph().startCrawl/crawl(url, options)}.
 *
 * @param maxDepth maximum BFS depth (orchestrator default 3; 0 lets the API use the tier's default)
 * @param maxPages maximum pages to crawl (orchestrator default 100; 0 lets the API use the tier's default)
 * @param extractSchema the extraction schema for each fetched page, or null:
 *                      the shorthand {@code {"title": "css:h1"}} (prefix optional) or a full
 *                      {@code {"fields": [{"name", "selector", "attribute"?, "type"?}], "item_selector"?, ...}}
 *                      ({@link ExtractSchema#toMap()} gives one)
 * @param includeExternalLinks whether to follow off-domain links
 * @param sessionArtifactId reuse an authenticated session artifact (from Fetchyr), or null
 * @param scope a CrawlScope ({@code same_host}, {@code allowed_domains}, {@code exclude_patterns}, ...), or null
 * @param budget a CrawlBudget ({@code max_resources}, {@code max_total_bytes}, ...), or null
 * @param captureMode {@code structure}, {@code content} or {@code visual}, or null
 * @param includeResources the page extras to load ({@code include_resources}; {@link ResourceTypes}):
 *                         {@code images}, {@code media}, {@code fonts}, {@code stylesheets},
 *                         {@code tracking}. Page extras are off by default on every browser route;
 *                         null (unset) means none and is not sent. With {@code captureMode}
 *                         {@code visual}, images and stylesheets are included automatically
 * @param maxDataMb stop the job when it has used this many MB of data ({@code max_data_mb}); it then
 *                  ends partial with {@code partial_reason} {@code data_limit}. Null: no per-job limit
 * @param estimateId the {@code estimateId} of the estimate the user saw ({@code estimate_id}), or null
 * @param costAcknowledged the user accepted that estimate's cost ({@code cost_acknowledged}); sent only when true
 * @param urls fetch exactly these pages (same registrable domain as the URL; capped at max pages;
 *             links are not followed), for example chosen from {@code webgrph().mapSite}; null is not sent
 * @param includePaths follow only links under these path prefixes (the start page is always fetched),
 *                     for example branches of {@code webgrph().mapSite}; null is not sent
 * @param robotsAcknowledgedHosts hosts whose robots.txt stop you acknowledge for this request
 *                         ({@code robots_acknowledged_hosts}): the {@code host} of a
 *                         {@link dev.kloakd.sdk.errors.RobotsDisallowedException}. Naming a host
 *                         confirms you have the right to access its content despite its robots.txt
 *                         and accept responsibility under the Terms; KLOAKD records it. This request
 *                         and these hosts only (at most 50 bare host names). Null or empty is not sent
 */
public record CrawlOptions(
        int maxDepth,
        int maxPages,
        Map<String, ?> extractSchema,
        boolean includeExternalLinks,
        String sessionArtifactId,
        Map<String, Object> scope,
        Map<String, Object> budget,
        String captureMode,
        List<String> includeResources,
        Double maxDataMb,
        String estimateId,
        boolean costAcknowledged,
        List<String> urls,
        List<String> includePaths,
        List<String> robotsAcknowledgedHosts
) {
    /** The cost-control constructor (before robots.txt acknowledgements). */
    public CrawlOptions(int maxDepth, int maxPages, Map<String, ?> extractSchema, boolean includeExternalLinks,
            String sessionArtifactId, Map<String, Object> scope, Map<String, Object> budget, String captureMode,
            List<String> includeResources, Double maxDataMb, String estimateId, boolean costAcknowledged,
            List<String> urls, List<String> includePaths) {
        this(maxDepth, maxPages, extractSchema, includeExternalLinks, sessionArtifactId, scope, budget, captureMode,
                includeResources, maxDataMb, estimateId, costAcknowledged, urls, includePaths, null);
    }

    /** The 0.7.0 components only (none of the cost-control fields). */
    public CrawlOptions(int maxDepth, int maxPages, Map<String, ?> extractSchema, boolean includeExternalLinks,
            String sessionArtifactId, Map<String, Object> scope, Map<String, Object> budget, String captureMode) {
        this(maxDepth, maxPages, extractSchema, includeExternalLinks, sessionArtifactId, scope, budget, captureMode,
                null, null, null, false, null, null, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxDepth;
        private int maxPages;
        private Map<String, ?> extractSchema;
        private boolean includeExternalLinks;
        private String sessionArtifactId;
        private Map<String, Object> scope;
        private Map<String, Object> budget;
        private String captureMode;
        private List<String> includeResources;
        private Double maxDataMb;
        private String estimateId;
        private boolean costAcknowledged;
        private List<String> urls;
        private List<String> includePaths;
        private List<String> robotsAcknowledgedHosts;

        private Builder() {}

        public Builder maxDepth(int maxDepth) { this.maxDepth = maxDepth; return this; }
        public Builder maxPages(int maxPages) { this.maxPages = maxPages; return this; }
        public Builder extractSchema(Map<String, ?> extractSchema) { this.extractSchema = extractSchema; return this; }
        public Builder includeExternalLinks(boolean includeExternalLinks) { this.includeExternalLinks = includeExternalLinks; return this; }
        public Builder sessionArtifactId(String sessionArtifactId) { this.sessionArtifactId = sessionArtifactId; return this; }
        public Builder scope(Map<String, Object> scope) { this.scope = scope; return this; }
        public Builder budget(Map<String, Object> budget) { this.budget = budget; return this; }
        public Builder captureMode(String captureMode) { this.captureMode = captureMode; return this; }
        /** The page extras to load ({@link ResourceTypes}); an empty list sends {@code []} (none, the default). */
        public Builder includeResources(List<String> includeResources) { this.includeResources = includeResources == null ? null : List.copyOf(includeResources); return this; }
        /** The page extras to load ({@link ResourceTypes}). */
        public Builder includeResources(String... includeResources) { return includeResources(List.of(includeResources)); }
        public Builder maxDataMb(double maxDataMb) { this.maxDataMb = maxDataMb; return this; }
        public Builder estimateId(String estimateId) { this.estimateId = estimateId; return this; }
        public Builder costAcknowledged(boolean costAcknowledged) { this.costAcknowledged = costAcknowledged; return this; }
        /** {@link #estimateId(String)} and {@link #costAcknowledged(boolean) costAcknowledged(true)}. */
        public Builder acknowledge(CrawlEstimate estimate) {
            this.estimateId = estimate.estimateId();
            this.costAcknowledged = true;
            return this;
        }
        public Builder urls(List<String> urls) { this.urls = urls == null ? null : List.copyOf(urls); return this; }
        public Builder includePaths(List<String> includePaths) { this.includePaths = includePaths == null ? null : List.copyOf(includePaths); return this; }

        /** Options with the orchestrator's defaults (depth 3, 100 pages) for unset limits. */
        /**
         * Hosts whose robots.txt stop you acknowledge for this request ({@code robots_acknowledged_hosts}):
         * the {@code getHost()} of a {@link dev.kloakd.sdk.errors.RobotsDisallowedException}. Recorded by
         * KLOAKD; applies to this request and these hosts only. Null or empty is not sent.
         */
        public Builder robotsAcknowledgedHosts(List<String> hosts) { this.robotsAcknowledgedHosts = hosts == null ? null : List.copyOf(hosts); return this; }
        /** {@link #robotsAcknowledgedHosts(List)} with these hosts. */
        public Builder robotsAcknowledgedHosts(String... hosts) { return robotsAcknowledgedHosts(List.of(hosts)); }

        public CrawlOptions build() {
            return new CrawlOptions(maxDepth > 0 ? maxDepth : 3, maxPages > 0 ? maxPages : 100,
                    extractSchema, includeExternalLinks, sessionArtifactId, scope, budget, captureMode,
                    includeResources, maxDataMb, estimateId, costAcknowledged, urls, includePaths,
                    robotsAcknowledgedHosts);
        }

        /** Options as set: unset limits stay 0 and are not sent, so the API applies the tier's defaults. */
        public CrawlOptions buildAsSet() {
            return new CrawlOptions(maxDepth, maxPages, extractSchema, includeExternalLinks,
                    sessionArtifactId, scope, budget, captureMode,
                    includeResources, maxDataMb, estimateId, costAcknowledged, urls, includePaths,
                    robotsAcknowledgedHosts);
        }
    }
}
