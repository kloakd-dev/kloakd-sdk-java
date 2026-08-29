package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Options for the high-level {@code client.crawl()} orchestrator.
 *
 * @param maxDepth maximum BFS depth (default 3)
 * @param maxPages maximum pages to crawl (default 100)
 * @param extractSchema CSS selector schema for structured extraction, or null
 * @param outputFormat output format: "json" or "markdown" (default "json")
 * @param includeExternalLinks whether to follow off-domain links
 * @param sessionArtifactId reuse an authenticated session artifact (from Fetchyr), or null
 */
public record CrawlOptions(
        int maxDepth,
        int maxPages,
        Map<String, String> extractSchema,
        String outputFormat,
        boolean includeExternalLinks,
        String sessionArtifactId
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxDepth;
        private int maxPages;
        private Map<String, String> extractSchema;
        private String outputFormat;
        private boolean includeExternalLinks;
        private String sessionArtifactId;

        private Builder() {}

        public Builder maxDepth(int maxDepth) { this.maxDepth = maxDepth; return this; }
        public Builder maxPages(int maxPages) { this.maxPages = maxPages; return this; }
        public Builder extractSchema(Map<String, String> extractSchema) { this.extractSchema = extractSchema; return this; }
        public Builder outputFormat(String outputFormat) { this.outputFormat = outputFormat; return this; }
        public Builder includeExternalLinks(boolean includeExternalLinks) { this.includeExternalLinks = includeExternalLinks; return this; }
        public Builder sessionArtifactId(String sessionArtifactId) { this.sessionArtifactId = sessionArtifactId; return this; }

        public CrawlOptions build() {
            return new CrawlOptions(
                    maxDepth > 0 ? maxDepth : 3,
                    maxPages > 0 ? maxPages : 100,
                    extractSchema,
                    outputFormat != null ? outputFormat : "json",
                    includeExternalLinks,
                    sessionArtifactId
            );
        }
    }
}
