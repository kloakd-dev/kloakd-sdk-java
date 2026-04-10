package dev.kloakd.sdk.models;

import java.util.Map;

/** SSE event yielded by Webgrph.crawlStream(). */
public record CrawlEvent(
        String type,
        String url,
        Integer depth,
        Integer pagesFound,
        Map<String, Object> metadata
) {}
