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
 * @param metadata the node's metadata ({@code title}, {@code is_navigation},
 *                 {@code is_api_node}, {@code is_grouping}, ...)
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
) {
    /**
     * Whether this node is a grouping folder of the displayed hierarchy
     * ({@code metadata.is_grouping}), not a fetched page. The pages route
     * returns these only with {@code include_grouping=true}.
     */
    public boolean isGrouping() {
        return metadata != null && Boolean.TRUE.equals(metadata.get("is_grouping"));
    }
}
