package dev.kloakd.sdk.models;

import java.util.List;

/** Result of Webgrph.crawl(). */
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
