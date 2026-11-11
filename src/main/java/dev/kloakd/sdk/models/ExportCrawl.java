package dev.kloakd.sdk.models;

/**
 * What a crawl-extract export's {@code X-Crawl-*} response headers say about
 * the job's crawl (kobe-scraper modules/webgrph/crawl_extract_router.py
 * {@code _crawl_headers}). A component is null when its header was not sent.
 *
 * @param status          {@code X-Crawl-Status}: the crawl's status ({@code completed},
 *                        {@code failed}, {@code cancelled}, {@code held_quota}, ...)
 * @param isPartial       {@code X-Crawl-Partial}: the crawl stopped before fetching every page it found
 * @param partialReason   {@code X-Crawl-Partial-Reason}
 * @param pagesDiscovered {@code X-Crawl-Pages-Discovered}
 * @param pagesFetched    {@code X-Crawl-Pages-Fetched}
 * @param pagesUnfetched  {@code X-Crawl-Pages-Unfetched}
 * @param cancelled       {@code X-Crawl-Cancelled}
 * @param rootUnfetched   {@code X-Crawl-Root-Unfetched}: the start page itself was not fetched
 */
public record ExportCrawl(
        String status,
        Boolean isPartial,
        String partialReason,
        Integer pagesDiscovered,
        Integer pagesFetched,
        Integer pagesUnfetched,
        Boolean cancelled,
        Boolean rootUnfetched
) {}
