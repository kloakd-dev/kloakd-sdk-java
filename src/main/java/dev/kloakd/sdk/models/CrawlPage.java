package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * A single page result from the high-level {@code client.crawl()} orchestrator.
 *
 * @param success whether the page was fetched successfully
 * @param url the URL that was fetched
 * @param statusCode HTTP status code returned by the target
 * @param tierUsed bypass tier that was applied (1–4)
 * @param html raw HTML body of the response, or null
 * @param structuredData extracted structured data if a schema was provided, or null
 * @param artifactId storage artifact ID for the captured response
 * @param error error message if success is false, otherwise null
 */
public record CrawlPage(
        boolean success,
        String url,
        int statusCode,
        int tierUsed,
        String html,
        Map<String, Object> structuredData,
        String artifactId,
        String error
) {
    public boolean ok() {
        return success && error == null;
    }
}
