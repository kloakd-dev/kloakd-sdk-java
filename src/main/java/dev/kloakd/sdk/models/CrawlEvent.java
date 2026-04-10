package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * SSE event yielded by Webgrph.crawlStream().
 *
 * @param type event type (e.g. page_found, done, error)
 * @param url URL of the page discovered in this event
 * @param depth crawl depth at which this page was found
 * @param pagesFound running total of pages found so far
 * @param metadata additional event metadata
 */
public record CrawlEvent(
        String type,
        String url,
        Integer depth,
        Integer pagesFound,
        Map<String, Object> metadata
) {}
