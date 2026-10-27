package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * A page in a crawl's hierarchy (an entry of {@code webgrph/crawl/{id}/pages}).
 *
 * @param id node ID
 * @param url the page's URL
 * @param parentUrl the linking page's URL, or null for the root
 * @param depth depth from the root
 * @param category the page's category
 * @param title page title, or null
 * @param children URLs this page links to
 * @param metadata the node's metadata
 */
public record PageNode(
        String id,
        String url,
        String parentUrl,
        int depth,
        String category,
        String title,
        List<String> children,
        Map<String, Object> metadata
) {}
