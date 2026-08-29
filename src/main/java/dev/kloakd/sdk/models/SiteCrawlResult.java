package dev.kloakd.sdk.models;

import java.util.List;

/**
 * Result of the high-level {@code client.crawl()} orchestrator.
 *
 * @param success whether the crawl completed
 * @param url the seed URL
 * @param totalPagesDiscovered pages found during BFS discovery
 * @param pagesFetched pages successfully fetched
 * @param pagesFailed pages that failed (included with success=false)
 * @param pages all pages with HTML and optional structured data
 * @param crawlArtifactId artifact ID for the site hierarchy
 * @param error error message if the crawl failed entirely, otherwise null
 */
public record SiteCrawlResult(
        boolean success,
        String url,
        int totalPagesDiscovered,
        int pagesFetched,
        int pagesFailed,
        List<CrawlPage> pages,
        String crawlArtifactId,
        String error
) {}
