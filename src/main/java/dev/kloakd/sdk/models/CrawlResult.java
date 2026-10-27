package dev.kloakd.sdk.models;

import java.util.List;

/**
 * Result of Webgrph.crawl() / startCrawl().
 *
 * @param success true when the crawl completed (startCrawl: when it was accepted)
 * @param crawlId the crawl's ID
 * @param url the root URL
 * @param status the crawl's final status (completed, failed, cancelled), or null from startCrawl
 * @param totalPages pages discovered
 * @param maxDepthReached deepest level reached
 * @param pages every discovered page (empty from startCrawl or when not completed)
 * @param total page count reported by the pages route
 * @param artifactId the crawl's hierarchy artifact
 * @param error error message, or null
 */
public record CrawlResult(
        boolean success,
        String crawlId,
        String url,
        String status,
        int totalPages,
        int maxDepthReached,
        List<PageNode> pages,
        int total,
        String artifactId,
        String error
) {}
