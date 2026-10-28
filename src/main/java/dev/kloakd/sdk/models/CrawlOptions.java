package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Options for the high-level {@code client.crawl()} orchestrator and for
 * {@code webgrph().startCrawl/crawl(url, options)}.
 *
 * @param maxDepth maximum BFS depth (orchestrator default 3; 0 lets the API use the tier's default)
 * @param maxPages maximum pages to crawl (orchestrator default 100; 0 lets the API use the tier's default)
 * @param extractSchema the extraction schema for each fetched page, or null:
 *                      the shorthand {@code {"title": "css:h1"}} (prefix optional) or a full
 *                      {@code {"fields": [{"name", "selector", "attribute"?, "type"?}], ...}}
 * @param includeExternalLinks whether to follow off-domain links
 * @param sessionArtifactId reuse an authenticated session artifact (from Fetchyr), or null
 * @param scope a CrawlScope ({@code same_host}, {@code allowed_domains}, {@code exclude_patterns}, ...), or null
 * @param budget a CrawlBudget ({@code max_resources}, {@code max_total_bytes}, ...), or null
 * @param captureMode {@code structure}, {@code content} or {@code visual}, or null
 */
public record CrawlOptions(
        int maxDepth,
        int maxPages,
        Map<String, ?> extractSchema,
        boolean includeExternalLinks,
        String sessionArtifactId,
        Map<String, Object> scope,
        Map<String, Object> budget,
        String captureMode
) {
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

        private Builder() {}

        public Builder maxDepth(int maxDepth) { this.maxDepth = maxDepth; return this; }
        public Builder maxPages(int maxPages) { this.maxPages = maxPages; return this; }
        public Builder extractSchema(Map<String, ?> extractSchema) { this.extractSchema = extractSchema; return this; }
        public Builder includeExternalLinks(boolean includeExternalLinks) { this.includeExternalLinks = includeExternalLinks; return this; }
        public Builder sessionArtifactId(String sessionArtifactId) { this.sessionArtifactId = sessionArtifactId; return this; }
        public Builder scope(Map<String, Object> scope) { this.scope = scope; return this; }
        public Builder budget(Map<String, Object> budget) { this.budget = budget; return this; }
        public Builder captureMode(String captureMode) { this.captureMode = captureMode; return this; }

        /** Options with the orchestrator's defaults (depth 3, 100 pages) for unset limits. */
        public CrawlOptions build() {
            return new CrawlOptions(maxDepth > 0 ? maxDepth : 3, maxPages > 0 ? maxPages : 100,
                    extractSchema, includeExternalLinks, sessionArtifactId, scope, budget, captureMode);
        }

        /** Options as set: unset limits stay 0 and are not sent, so the API applies the tier's defaults. */
        public CrawlOptions buildAsSet() {
            return new CrawlOptions(maxDepth, maxPages, extractSchema, includeExternalLinks,
                    sessionArtifactId, scope, budget, captureMode);
        }
    }
}
