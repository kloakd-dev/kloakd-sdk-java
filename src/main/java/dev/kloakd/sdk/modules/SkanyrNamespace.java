package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Site Knowledge module: synced sites, their resources, extraction and
 * search. Access via {@code client.skanyr()}.
 *
 * <p>In 0.3.0 the discovery methods of 0.2 ({@code discover}, {@code discoverAll},
 * {@code discoverStream}, {@code analyzeBundle}, {@code hierarchy}, {@code expandNode},
 * {@code retry}, {@code health}, {@code getApiMap}, {@code getJob}, {@code getDiscovery*})
 * are removed: the API no longer serves those routes. The session, detected-API
 * and reader-view methods moved to {@code client.webgrph()}.
 */
public final class SkanyrNamespace {

    private final HttpTransport t;

    public SkanyrNamespace(HttpTransport t) { this.t = t; }

    /** The organisation's synced sites, under {@code "items"}. */
    public Map<String, Object> listSites() { return t.get("skanyr/sites", null); }

    /** Register a site from a finished Webgrph crawl. */
    public Map<String, Object> createSiteFromCrawl(String crawlId) {
        return t.post("skanyr/sites/from-crawl/" + crawlId, null);
    }

    public Map<String, Object> getSite(String siteId) { return t.get("skanyr/sites/" + siteId, null); }

    public Map<String, Object> setSitePolicy(String siteId, String policy) {
        return t.patch("skanyr/sites/" + siteId, Map.of("policy", policy));
    }

    public void deleteSite(String siteId) { t.delete("skanyr/sites/" + siteId); }

    public Map<String, Object> siteResources(String siteId) { return t.get("skanyr/sites/" + siteId + "/resources", null); }

    public Map<String, Object> siteApis(String siteId) { return t.get("skanyr/sites/" + siteId + "/apis", null); }

    /** Re-fetch a site's changed resources ({@code urls} and {@code policy} may be null). */
    public Map<String, Object> sync(String siteId, List<String> urls, String policy) {
        var body = new HashMap<String, Object>();
        body.put("site_id", siteId);
        if (urls != null && !urls.isEmpty()) body.put("urls", urls);
        if (policy != null) body.put("policy", policy);
        return t.post("skanyr/sync", body);
    }

    /** Search extracted content ({@code siteId} may be null; limit/offset 0 for the defaults). */
    public Map<String, Object> search(String query, String siteId, int limit, int offset) {
        var body = new HashMap<String, Object>();
        body.put("query", query);
        if (siteId != null) body.put("site_id", siteId);
        if (limit > 0) body.put("limit", limit);
        if (offset > 0) body.put("offset", offset);
        return t.post("skanyr/search", body);
    }

    /** Extract text and structure from stored content. */
    public Map<String, Object> extract(String contentHash, String mimeType, Map<String, Object> metadata) {
        var body = new HashMap<String, Object>();
        body.put("content_hash", contentHash);
        body.put("mime_type", mimeType);
        if (metadata != null && !metadata.isEmpty()) body.put("metadata", metadata);
        return t.post("skanyr/extract", body);
    }
}
