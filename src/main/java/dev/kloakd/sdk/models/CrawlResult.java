package dev.kloakd.sdk.models;

import java.util.List;

/**
 * Result of Webgrph.crawl().
 *
 * @param success whether the crawl succeeded
 * @param crawlId unique identifier for this crawl job
 * @param url the root URL that was crawled
 * @param totalPages total number of pages discovered
 * @param maxDepthReached deepest depth level reached during crawl
 * @param pages list of discovered page nodes
 * @param hasMore whether additional pages are available beyond this result
 * @param total total page count across all pages
 * @param artifactId storage artifact ID for the crawl graph
 * @param error error message if success is false, otherwise null
 */
public record CrawlResult(
        boolean success,
        String crawlId,
        String url,
        int totalPages,
        int maxDepthReached,
        List<PageNode> pages,
        boolean hasMore,
        int total,
        String artifactId,
        String error
) {}
