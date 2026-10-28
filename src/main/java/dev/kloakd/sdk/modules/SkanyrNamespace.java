package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.kloakd.sdk.modules.EvadrNamespace.seg;

/**
 * Site Knowledge module: sites built from crawls, their resources, search,
 * change history, the resource graph and technology findings
 * ({@code modules/skanyr/router.py}). Access via {@code client.skanyr()}.
 *
 * <p>Routes that answer a JSON array return {@code List<Map<String, Object>>}:
 * {@link #listSites}, {@link #listResources}, {@link #getSiteResources},
 * {@link #getSiteApis}, {@link #getFindings}, {@link #getTechnologies}.
 * Optional numbers are not sent when 0 (the API's defaults apply), optional
 * strings when null.
 */
public final class SkanyrNamespace {

    private final HttpTransport t;

    public SkanyrNamespace(HttpTransport t) { this.t = t; }

    // ── Sites ─────────────────────────────────────────────────────────────────

    /** The organisation's sites. */
    public List<Map<String, Object>> listSites() { return t.requestList("GET", "skanyr/sites", null, null); }

    /** Build (or update) a site from a finished Webgrph crawl. */
    public Map<String, Object> siteFromCrawl(String crawlId) {
        return t.post("skanyr/sites/from-crawl/" + seg(crawlId), null);
    }

    /** @deprecated use {@link #siteFromCrawl(String)}. */
    @Deprecated
    public Map<String, Object> createSiteFromCrawl(String crawlId) { return siteFromCrawl(crawlId); }

    public Map<String, Object> getSite(String siteId) { return t.get("skanyr/sites/" + seg(siteId), null); }

    /**
     * Set a site's default sync policy ({@code PATCH skanyr/sites/{id}}):
     * {@code full}, {@code incremental}, {@code conditional} or {@code head_first}.
     */
    public Map<String, Object> updateSite(String siteId, String policy) {
        return t.patch("skanyr/sites/" + seg(siteId), Map.of("policy", policy));
    }

    /** @deprecated use {@link #updateSite(String, String)}. */
    @Deprecated
    public Map<String, Object> setSitePolicy(String siteId, String policy) { return updateSite(siteId, policy); }

    public void deleteSite(String siteId) { t.delete("skanyr/sites/" + seg(siteId)); }

    /** Re-fetch a site's changed resources ({@code urls} and {@code policy} may be null). */
    public Map<String, Object> sync(String siteId, List<String> urls, String policy) {
        var body = new HashMap<String, Object>();
        body.put("site_id", siteId);
        if (urls != null && !urls.isEmpty()) body.put("urls", urls);
        if (policy != null) body.put("policy", policy);
        return t.post("skanyr/sync", body);
    }

    /**
     * Export captured content as a ZIP ({@code POST skanyr/sites/{id}/export}).
     *
     * @param resourceIds the resources to include, or null for all with content
     * @param maxBytes    the size limit (0 for the API's 500 MB)
     * @return the ZIP file's bytes
     */
    public byte[] exportSite(String siteId, List<String> resourceIds, long maxBytes) {
        var body = new HashMap<String, Object>();
        if (resourceIds != null) body.put("resource_ids", resourceIds);
        if (maxBytes > 0) body.put("max_bytes", maxBytes);
        return t.requestBytes("POST", "skanyr/sites/" + seg(siteId) + "/export", body, null);
    }

    public List<Map<String, Object>> getSiteApis(String siteId) { return getSiteApis(siteId, 0, 0); }

    public List<Map<String, Object>> getSiteApis(String siteId, int limit, int offset) {
        return t.requestList("GET", "skanyr/sites/" + seg(siteId) + "/apis", null, q("limit", limit, "offset", offset));
    }

    /** @deprecated use {@link #getSiteApis(String)}, which returns the list. */
    @Deprecated
    public Map<String, Object> siteApis(String siteId) { return t.get("skanyr/sites/" + seg(siteId) + "/apis", null); }

    public List<Map<String, Object>> getSiteResources(String siteId) { return getSiteResources(siteId, null, 0, 0); }

    public List<Map<String, Object>> getSiteResources(String siteId, String resourceType, int limit, int offset) {
        var p = q("limit", limit, "offset", offset);
        put(p, "resource_type", resourceType);
        return t.requestList("GET", "skanyr/sites/" + seg(siteId) + "/resources", null, p);
    }

    /** @deprecated use {@link #getSiteResources(String)}, which returns the list. */
    @Deprecated
    public Map<String, Object> siteResources(String siteId) {
        return t.get("skanyr/sites/" + seg(siteId) + "/resources", null);
    }

    /** Search one site's content ({@code GET skanyr/sites/{id}/search}). */
    public Map<String, Object> searchSite(String siteId, String q, int limit, int offset, String mimeType) {
        var p = q("limit", limit, "offset", offset);
        p.put("q", q);
        put(p, "mime_type", mimeType);
        return t.get("skanyr/sites/" + seg(siteId) + "/search", p);
    }

    public List<Map<String, Object>> getFindings(String siteId) { return getFindings(siteId, null, 0, 0); }

    public List<Map<String, Object>> getFindings(String siteId, String findingKind, int limit, int offset) {
        var p = q("limit", limit, "offset", offset);
        put(p, "finding_kind", findingKind);
        return t.requestList("GET", "skanyr/sites/" + seg(siteId) + "/findings", null, p);
    }

    public List<Map<String, Object>> getTechnologies(String siteId) {
        return t.requestList("GET", "skanyr/sites/" + seg(siteId) + "/technologies", null, null);
    }

    /** Evidence for one technology ({@code subject}, e.g. "React"); {@code detector} may be null. */
    public Map<String, Object> getTechnologyEvidence(String siteId, String subject, String detector,
                                                     int limit, int offset) {
        var p = q("limit", limit, "offset", offset);
        p.put("subject", subject);
        put(p, "detector", detector);
        return t.get("skanyr/sites/" + seg(siteId) + "/technology-evidence", p);
    }

    // ── History ───────────────────────────────────────────────────────────────

    public Map<String, Object> listSnapshots(String siteId, int limit) {
        return t.get("skanyr/sites/" + seg(siteId) + "/snapshots", q("limit", limit, null, 0));
    }

    public Map<String, Object> getTimeline(String siteId, int limit) {
        return t.get("skanyr/sites/" + seg(siteId) + "/timeline", q("limit", limit, null, 0));
    }

    public Map<String, Object> getTimelineSnapshot(String siteId, String snapshotId) {
        return t.get("skanyr/sites/" + seg(siteId) + "/timeline/" + seg(snapshotId), null);
    }

    /** What changed between two snapshots (both null: the last two). */
    public Map<String, Object> diff(String siteId, String snapshotA, String snapshotB) {
        var p = new LinkedHashMap<String, String>();
        put(p, "snapshot_a", snapshotA);
        put(p, "snapshot_b", snapshotB);
        return t.get("skanyr/sites/" + seg(siteId) + "/diff", p);
    }

    // ── Graph ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getGraph(String siteId, String resourceType, int maxDepth, String edgeKind, int limit) {
        var p = q("max_depth", maxDepth, "limit", limit);
        put(p, "resource_type", resourceType);
        put(p, "edge_kind", edgeKind);
        return t.get("skanyr/sites/" + seg(siteId) + "/graph", p);
    }

    public Map<String, Object> getGraphStats(String siteId) {
        return t.get("skanyr/sites/" + seg(siteId) + "/graph/stats", null);
    }

    public Map<String, Object> getSubgraph(String siteId, String resourceId, int hops) {
        return t.get("skanyr/sites/" + seg(siteId) + "/graph/subgraph/" + seg(resourceId), q("hops", hops, null, 0));
    }

    // ── Resources, search, extraction ─────────────────────────────────────────

    /** Resources across sites ({@code siteId}, {@code resourceType} may be null). */
    public List<Map<String, Object>> listResources(String siteId, String resourceType, int limit, int offset) {
        var p = q("limit", limit, "offset", offset);
        put(p, "site_id", siteId);
        put(p, "resource_type", resourceType);
        return t.requestList("GET", "skanyr/resources", null, p);
    }

    public Map<String, Object> getResource(String resourceId) {
        return t.get("skanyr/resources/" + seg(resourceId), null);
    }

    /** Search extracted content ({@code siteId} may be null; limit/offset 0 for the defaults). */
    public Map<String, Object> search(String query, String siteId, int limit, int offset) {
        return search(query, siteId, limit, offset, null);
    }

    /** {@link #search(String, String, int, int)} filtered by MIME type ({@code mimeType} may be null). */
    public Map<String, Object> search(String query, String siteId, int limit, int offset, String mimeType) {
        var body = new HashMap<String, Object>();
        body.put("query", query);
        if (siteId != null) body.put("site_id", siteId);
        if (limit > 0) body.put("limit", limit);
        if (offset > 0) body.put("offset", offset);
        if (mimeType != null) body.put("mime_type", mimeType);
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

    /** The plain text of an extraction ({@code text_object_hash} of {@link #extract}'s answer). */
    public String getExtractionText(String contentHash) {
        return t.requestText("GET", "skanyr/extractions/" + seg(contentHash) + "/text", null, null);
    }

    /** The structure (headings, tables, links) of an extraction ({@code structure_object_hash}). */
    public Map<String, Object> getExtractionStructure(String contentHash) {
        return t.get("skanyr/extractions/" + seg(contentHash) + "/structure", null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Map<String, String> q(String k1, int v1, String k2, int v2) {
        var p = new LinkedHashMap<String, String>();
        if (k1 != null && v1 > 0) p.put(k1, String.valueOf(v1));
        if (k2 != null && v2 > 0) p.put(k2, String.valueOf(v2));
        return p;
    }

    private static void put(Map<String, String> p, String key, String value) {
        if (value != null) p.put(key, value);
    }
}
