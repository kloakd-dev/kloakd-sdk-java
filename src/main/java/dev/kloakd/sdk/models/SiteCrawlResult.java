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
 * @param crawlId the crawl's ID (to wait for it again, or to continue it), or null
 * @param status the crawl's status when discovery ended (completed, failed, cancelled or held_quota)
 * @param message the API's text about the crawl's state (for a held crawl, why it is held), or null
 * @param held true when the crawl is held because the organisation's quota ran out: no page
 *             was fetched; it resumes by itself when quota returns ({@code webgrph().waitForCrawl(crawlId)})
 */
public record SiteCrawlResult(
        boolean success,
        String url,
        int totalPagesDiscovered,
        int pagesFetched,
        int pagesFailed,
        List<CrawlPage> pages,
        String crawlArtifactId,
        String error,
        String crawlId,
        String status,
        String message,
        boolean held
) {
    /** The 0.6.0 components only. */
    public SiteCrawlResult(boolean success, String url, int totalPagesDiscovered, int pagesFetched,
            int pagesFailed, List<CrawlPage> pages, String crawlArtifactId, String error) {
        this(success, url, totalPagesDiscovered, pagesFetched, pagesFailed, pages, crawlArtifactId, error,
                null, null, null, false);
    }
}
