package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Options for {@code client.createBatchJob} (the body of {@code POST
 * batch-jobs}; kobe-scraper kernel/jobs_runs_router.py). Only what is set
 * is sent.
 *
 * @param extractionConfig how each URL is extracted, for example
 *                         {@code Map.of("selectors", Map.of("title", "h1"))}
 * @param crawlId          the crawl the URLs came from: its detected-API records are added
 *                         to the results
 * @param entityType       what each page holds
 * @param fields           the names of the fields to extract
 */
public record BatchJobOptions(
        Map<String, Object> extractionConfig,
        String crawlId,
        String entityType,
        List<String> fields
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Map<String, Object> extractionConfig;
        private String crawlId;
        private String entityType;
        private List<String> fields;

        private Builder() {}

        public Builder extractionConfig(Map<String, Object> extractionConfig) { this.extractionConfig = extractionConfig; return this; }
        public Builder crawlId(String crawlId) { this.crawlId = crawlId; return this; }
        public Builder entityType(String entityType) { this.entityType = entityType; return this; }
        public Builder fields(List<String> fields) { this.fields = fields; return this; }

        public BatchJobOptions build() {
            return new BatchJobOptions(extractionConfig, crawlId, entityType, fields);
        }
    }
}
