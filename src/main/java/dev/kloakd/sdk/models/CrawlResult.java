package dev.kloakd.sdk.models;

import java.util.List;

/**
 * Result of Webgrph.crawl() / waitForCrawl() / startCrawl().
 *
 * <p>From {@code startCrawl} only {@code success}, {@code crawlId}, {@code url},
 * {@code totalPages}, {@code maxDepthReached}, {@code artifactId} and {@code error}
 * are set: {@code POST webgrph/crawl} answers with no status (kobe-scraper
 * modules/webgrph/router.py CrawlResponse). The rest come from the crawl's
 * status ({@code GET webgrph/crawl/{id}}, CrawlStatusResponse).
 *
 * <p>A crawl whose {@code status} is {@code held_quota} ({@code held} true) is
 * paused, not failed: the organisation's quota ran out while it ran. It keeps
 * its id and resumes by itself when quota returns (an upgrade, a top-up or a
 * new billing period); {@code message} is the API's text and {@code error} is
 * null. Call {@code waitForCrawl(crawlId)} again later. After 30 days held it
 * ends {@code failed} with {@code errorCode} {@code held_quota_expired}.
 *
 * @param success true when the crawl completed (startCrawl: when it was accepted)
 * @param crawlId the crawl's ID
 * @param url the root URL: the one given to {@code crawl}/{@code startCrawl};
 *            null from {@code waitForCrawl(crawlId, ...)} (the status has no url)
 * @param status the crawl's status when the wait ended (completed, failed,
 *               cancelled or held_quota), or null from startCrawl
 * @param totalPages pages fetched (grouping folders of the hierarchy are not counted)
 * @param maxDepthReached deepest level of a fetched page
 * @param pages every fetched page (empty from startCrawl or when not completed)
 * @param total page count reported by the pages route
 * @param artifactId the crawl's hierarchy artifact
 * @param error error message, or null (null for a held crawl)
 * @param isPartial whether the crawl stopped before fetching every page it found; null until it ends
 * @param partialReason why it is partial: {@code time_budget}, {@code max_pages}, {@code data_limit}
 *                      (the job's {@code max_data_mb} was reached: "Stopped at your data limit of N MB.
 *                      Continue with a higher limit to fetch more.") or {@code error}; else null
 * @param resumable whether {@code continueCrawl(crawlId)} can go on with the unfetched pages; null until it ends
 * @param pagesDiscovered pages found, or null when not reported
 * @param pagesFetched pages fetched, or null when not reported
 * @param pagesUnfetched pages found and not fetched, or null when not reported
 * @param errorCode the API's code for a failure or an early stop (for example {@code held_quota_expired}), or null
 * @param message the API's text about the crawl's state (for a held crawl, why it is held), or null
 * @param held true when {@code status} is {@code held_quota}
 * @param timeBudgetS the crawl's time budget in seconds ({@code time_budget_s}), or null until the crawl reports it
 * @param elapsedS seconds the crawl has used ({@code elapsed_s}), or null until the crawl reports it
 * @param estRemainingS estimated seconds left ({@code est_remaining_s}), or null until the crawl reports it
 * @param dataUsedMb MB of data the crawl has used so far ({@code data_used_mb}), or null when not reported
 * @param maxDataMb the crawl's data limit in MB ({@code max_data_mb}), or null when it has none
 * @param includeResources the page extras the crawl loads ({@code include_resources}; empty: none,
 *                         the default), or null when not reported
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
        String error,
        Boolean isPartial,
        String partialReason,
        Boolean resumable,
        Integer pagesDiscovered,
        Integer pagesFetched,
        Integer pagesUnfetched,
        String errorCode,
        String message,
        boolean held,
        Integer timeBudgetS,
        Double elapsedS,
        Double estRemainingS,
        Double dataUsedMb,
        Double maxDataMb,
        List<String> includeResources
) {
    /** The components before the data limit (no {@code dataUsedMb}, {@code maxDataMb}, {@code includeResources}). */
    public CrawlResult(boolean success, String crawlId, String url, String status, int totalPages,
            int maxDepthReached, List<PageNode> pages, int total, String artifactId, String error,
            Boolean isPartial, String partialReason, Boolean resumable, Integer pagesDiscovered,
            Integer pagesFetched, Integer pagesUnfetched, String errorCode, String message, boolean held,
            Integer timeBudgetS, Double elapsedS, Double estRemainingS) {
        this(success, crawlId, url, status, totalPages, maxDepthReached, pages, total, artifactId, error,
                isPartial, partialReason, resumable, pagesDiscovered, pagesFetched, pagesUnfetched,
                errorCode, message, held, timeBudgetS, elapsedS, estRemainingS, null, null, null);
    }

    /** The 0.7.0 components only (no time budget fields). */
    public CrawlResult(boolean success, String crawlId, String url, String status, int totalPages,
            int maxDepthReached, List<PageNode> pages, int total, String artifactId, String error,
            Boolean isPartial, String partialReason, Boolean resumable, Integer pagesDiscovered,
            Integer pagesFetched, Integer pagesUnfetched, String errorCode, String message, boolean held) {
        this(success, crawlId, url, status, totalPages, maxDepthReached, pages, total, artifactId, error,
                isPartial, partialReason, resumable, pagesDiscovered, pagesFetched, pagesUnfetched,
                errorCode, message, held, null, null, null, null, null, null);
    }

    /** The 0.6.0 components only. */
    public CrawlResult(boolean success, String crawlId, String url, String status, int totalPages,
            int maxDepthReached, List<PageNode> pages, int total, String artifactId, String error) {
        this(success, crawlId, url, status, totalPages, maxDepthReached, pages, total, artifactId, error,
                null, null, null, null, null, null, null, null, false, null, null, null, null, null, null);
    }
}
