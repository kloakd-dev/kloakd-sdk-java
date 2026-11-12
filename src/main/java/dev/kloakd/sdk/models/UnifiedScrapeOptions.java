package dev.kloakd.sdk.models;

import java.time.Duration;
import java.util.Map;

/**
 * Options for {@code client.unifiedScrape} (kobe-scraper
 * api/v1/endpoints/unified_scraping.py UnifiedScrapeRequest). Only what is
 * set is sent.
 *
 * @param prompt            what to extract, in words
 * @param mode              {@code auto} (the API's default), {@code level1} or {@code level2}
 * @param schema            field name to its description; made from {@code prompt} when omitted
 * @param options           scraping options
 * @param context           context for the Strategy Engine
 * @param constraints       constraints for the Strategy Engine
 * @param sessionArtifactId a login session's id
 * @param pollInterval      first wait before polling a handed-off request, or null for the client's
 * @param pollTimeout       limit on following a handed-off request, or null for the client's
 */
public record UnifiedScrapeOptions(
        String prompt,
        String mode,
        Map<String, String> schema,
        Map<String, Object> options,
        Map<String, Object> context,
        Map<String, Object> constraints,
        String sessionArtifactId,
        Duration pollInterval,
        Duration pollTimeout
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String prompt;
        private String mode;
        private Map<String, String> schema;
        private Map<String, Object> options;
        private Map<String, Object> context;
        private Map<String, Object> constraints;
        private String sessionArtifactId;
        private Duration pollInterval;
        private Duration pollTimeout;

        private Builder() {}

        public Builder prompt(String prompt) { this.prompt = prompt; return this; }
        public Builder mode(String mode) { this.mode = mode; return this; }
        public Builder schema(Map<String, String> schema) { this.schema = schema; return this; }
        public Builder options(Map<String, Object> options) { this.options = options; return this; }
        public Builder context(Map<String, Object> context) { this.context = context; return this; }
        public Builder constraints(Map<String, Object> constraints) { this.constraints = constraints; return this; }
        public Builder sessionArtifactId(String sessionArtifactId) { this.sessionArtifactId = sessionArtifactId; return this; }
        public Builder pollInterval(Duration pollInterval) { this.pollInterval = pollInterval; return this; }
        public Builder pollTimeout(Duration pollTimeout) { this.pollTimeout = pollTimeout; return this; }

        public UnifiedScrapeOptions build() {
            return new UnifiedScrapeOptions(prompt, mode, schema, options, context, constraints,
                    sessionArtifactId, pollInterval, pollTimeout);
        }
    }
}
