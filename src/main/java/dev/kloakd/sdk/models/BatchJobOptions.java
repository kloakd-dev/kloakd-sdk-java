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
 * @param robotsAcknowledgedHosts hosts whose robots.txt stop you acknowledge for this request
 *                         ({@code robots_acknowledged_hosts}): the {@code host} of a
 *                         {@link dev.kloakd.sdk.errors.RobotsDisallowedException}. Naming a host
 *                         confirms you have the right to access its content despite its robots.txt
 *                         and accept responsibility under the Terms; KLOAKD records it. This request
 *                         and these hosts only (at most 50 bare host names). Null or empty is not sent
 */
public record BatchJobOptions(
        Map<String, Object> extractionConfig,
        String crawlId,
        String entityType,
        List<String> fields,
        List<String> robotsAcknowledgedHosts
) {
    /** The constructor before robots.txt acknowledgements. */
    public BatchJobOptions(Map<String, Object> extractionConfig, String crawlId, String entityType, List<String> fields) {
        this(extractionConfig, crawlId, entityType, fields, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Map<String, Object> extractionConfig;
        private String crawlId;
        private String entityType;
        private List<String> fields;
        private List<String> robotsAcknowledgedHosts;

        private Builder() {}

        public Builder extractionConfig(Map<String, Object> extractionConfig) { this.extractionConfig = extractionConfig; return this; }
        public Builder crawlId(String crawlId) { this.crawlId = crawlId; return this; }
        public Builder entityType(String entityType) { this.entityType = entityType; return this; }
        public Builder fields(List<String> fields) { this.fields = fields; return this; }

        /**
         * Hosts whose robots.txt stop you acknowledge for this request ({@code robots_acknowledged_hosts}):
         * the {@code getHost()} of a {@link dev.kloakd.sdk.errors.RobotsDisallowedException}. Recorded by
         * KLOAKD; applies to this request and these hosts only. Null or empty is not sent.
         */
        public Builder robotsAcknowledgedHosts(List<String> hosts) { this.robotsAcknowledgedHosts = hosts == null ? null : List.copyOf(hosts); return this; }
        /** {@link #robotsAcknowledgedHosts(List)} with these hosts. */
        public Builder robotsAcknowledgedHosts(String... hosts) { return robotsAcknowledgedHosts(List.of(hosts)); }

        public BatchJobOptions build() {
            return new BatchJobOptions(extractionConfig, crawlId, entityType, fields, robotsAcknowledgedHosts);
        }
    }
}
