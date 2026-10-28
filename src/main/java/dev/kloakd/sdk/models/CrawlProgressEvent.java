package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Progress event yielded by {@code client.crawlStream()}, as it happens.
 *
 * @param type event type: {@code discovery_complete}, {@code page_fetching},
 *             {@code page_fetched}, {@code page_failed}, {@code crawl_complete}
 * @param url the URL associated with this event, or null
 * @param page page number (1-indexed) for page events, or null
 * @param total total pages discovered, or null
 * @param success whether the page was fetched successfully (for page events), or null
 * @param error error message for failed pages, or null
 * @param metadata additional event-specific data ({@code pages_found},
 *                 {@code tier_used}, and on {@code crawl_complete} the
 *                 {@link SiteCrawlResult} under {@code result})
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
