package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Progress event yielded by {@code client.crawlStream()}.
 *
 * @param type event type (discovery_started, discovery_complete, page_fetching, page_fetched, page_failed, crawl_complete)
 * @param url the URL associated with this event, or null
 * @param page page number (1-indexed) for page events, or null
 * @param total total pages discovered, or null
 * @param success whether the page was fetched successfully (for page events), or null
 * @param error error message for failed pages, or null
 * @param metadata additional event-specific data
 */
public record CrawlProgressEvent(
        String type,
        String url,
        Integer page,
        Integer total,
        Boolean success,
        String error,
        Map<String, Object> metadata
) {}
