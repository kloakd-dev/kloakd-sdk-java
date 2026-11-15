package dev.kloakd.sdk.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A site's pages grouped by path, before a crawl ({@code POST webgrph/map};
 * cost-control contract of 15 Nov 2026, section 5). The map reads robots.txt,
 * the sitemaps and the start page's links, and fetches no other page. Choose
 * branches and crawl them with {@code CrawlOptions.includePaths}, or single
 * pages with {@code CrawlOptions.urls}.
 *
 * @param url        the start URL
 * @param source     where the URLs came from: {@code sitemap}, {@code links} or {@code both}
 * @param totalUrls  URLs found
 * @param truncated  true when more were found than {@code max_urls}
 * @param dataUsedMb MB the map used, or null when not sent
 * @param branches   the top level of the tree, largest first (at most 50; the rest grouped as "other")
 */
public record SiteMap(String url, String source, int totalUrls, boolean truncated, Double dataUsedMb,
        List<Branch> branches) {

    /**
     * A branch of the site by path segment (up to 3 levels).
     *
     * @param path       the branch's path prefix, for example {@code /products/}
     * @param urlCount   URLs under it
     * @param sampleUrls a few of them
     * @param children   the next level, largest first (empty at the last level)
     */
    public record Branch(String path, int urlCount, List<String> sampleUrls, List<Branch> children) {}

    /** Read a map from the API's answer. */
    public static SiteMap from(Map<String, Object> m) {
        return new SiteMap(m.get("url") instanceof String s ? s : null,
                m.get("source") instanceof String s ? s : null,
                m.get("total_urls") instanceof Number n ? n.intValue() : 0,
                Boolean.TRUE.equals(m.get("truncated")),
                m.get("data_used_mb") instanceof Number n ? n.doubleValue() : null,
                branches(m.get("branches")));
    }

    @SuppressWarnings("unchecked")
    private static List<Branch> branches(Object v) {
        if (!(v instanceof List<?> list)) return List.of();
        var out = new ArrayList<Branch>();
        for (var item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            var b = (Map<String, Object>) raw;
            var samples = new ArrayList<String>();
            if (b.get("sample_urls") instanceof List<?> s) for (var u : s) if (u instanceof String str) samples.add(str);
            out.add(new Branch(b.get("path") instanceof String p ? p : null,
                    b.get("url_count") instanceof Number n ? n.intValue() : 0,
                    Collections.unmodifiableList(samples), branches(b.get("children"))));
        }
        return Collections.unmodifiableList(out);
    }
}
