package dev.kloakd.sdk.models;

/**
 * Answer of {@code webgrph().continueCrawl}
 * ({@code POST webgrph/crawl/{crawl_id}/continue}; kobe-scraper
 * modules/webgrph/router.py continue_crawl).
 *
 * @param success true when the continuation was queued
 * @param crawlId the crawl's ID (unchanged)
 * @param status  {@code queued}
 */
public record CrawlContinueResult(boolean success, String crawlId, String status) {}
