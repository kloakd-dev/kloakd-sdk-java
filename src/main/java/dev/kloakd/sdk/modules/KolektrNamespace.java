package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.ExtractOptions;
import dev.kloakd.sdk.models.ExtractionResult;

import java.util.*;

import static dev.kloakd.sdk.modules.EvadrNamespace.*;

/** Data Extraction module. Access via {@code client.kolektr()}. */
public final class KolektrNamespace {

    private final HttpTransport t;

    public KolektrNamespace(HttpTransport t) { this.t = t; }

    /** Extract structured data from a URL with the full Kolektr pipeline. */
    public ExtractionResult extract(String url) {
        return extract(url, ExtractOptions.builder().build());
    }

    /**
     * {@link #extract(String, ExtractOptions)} with these options.
     *
     * @param schemaSpec the fields to extract, or null: the shorthand
     *                   {@code {"title": "css:h1"}} or a full {@code {"fields": [...]}} spec
     */
    public ExtractionResult extract(String url, Map<String, ?> schemaSpec,
            String fetchArtifactId, String sessionArtifactId, String apiMapArtifactId) {
        return extract(url, ExtractOptions.builder().schema(schemaSpec).fetchArtifactId(fetchArtifactId)
                .sessionArtifactId(sessionArtifactId).apiMapArtifactId(apiMapArtifactId).build());
    }

    /**
     * Extract structured data from a URL ({@code POST kolektr/extract}). The
     * schema is sent as {@code schema_spec}: a shorthand {@code {field: "css:selector"}}
     * becomes {@code {"fields": [{"name", "selector"}]}} ({@link #schemaSpec}).
     * The API answers within about 85 s; a request still running then is
     * handed off as a job (HTTP 202), which this follows to its result with
     * the client's poll settings ({@link dev.kloakd.sdk.Kloakd.Builder#pollInterval});
     * a failed or expired job throws {@link dev.kloakd.sdk.errors.JobFailedException},
     * one still running at the poll timeout {@link dev.kloakd.sdk.errors.JobTimeoutException}.
     */
    public ExtractionResult extract(String url, ExtractOptions o) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        var spec = schemaSpec(o.schema());
        if (spec != null) body.put("schema_spec", spec);
        if (o.level() != null) body.put("level", o.level());
        if (o.options() != null && !o.options().isEmpty()) body.put("options", o.options());
        if (o.fetchArtifactId() != null) body.put("fetch_artifact_id", o.fetchArtifactId());
        if (o.sessionArtifactId() != null) body.put("session_artifact_id", o.sessionArtifactId());
        if (o.apiMapArtifactId() != null) body.put("api_map_artifact_id", o.apiMapArtifactId());
        if (o.pipelineId() != null) body.put("pipeline_id", o.pipelineId());
        return parseExtractionResult(t.postFollowing("kolektr/extract", body));
    }

    /** @deprecated use {@link #extract(String)}. */
    @Deprecated
    public ExtractionResult page(String url) {
        return extract(url);
    }

    public ExtractionResult extractHtml(String html, String url) {
        return extractHtml(html, url, null);
    }

    /** Extract from HTML you already have ({@code POST kolektr/extract/html}); the schema as for {@link #extract}. */
    public ExtractionResult extractHtml(String html, String url, Map<String, ?> schemaSpec) {
        var body = new HashMap<String, Object>();
        body.put("html", html);
        body.put("url", url);
        var spec = schemaSpec(schemaSpec);
        if (spec != null) body.put("schema_spec", spec);
        return parseExtractionResult(t.post("kolektr/extract/html", body));
    }

    /**
     * The API's {@code schema_spec} (modules/kolektr/router.py SchemaSpec):
     * a map with a {@code fields} list is sent as given; a shorthand
     * {@code {name: "css:selector"}} (the {@code css:} prefix optional)
     * becomes {@code {"fields": [{"name": name, "selector": selector}]}}; a
     * shorthand value that is itself a map ({@code {"selector", "attribute", "type"}})
     * becomes that field with its name. Null or empty gives null.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> schemaSpec(Map<String, ?> schema) {
        if (schema == null || schema.isEmpty()) return null;
        if (schema.get("fields") instanceof List<?>) return (Map<String, Object>) schema;
        var fields = new ArrayList<Map<String, Object>>();
        for (var e : schema.entrySet()) {
            var field = new LinkedHashMap<String, Object>();
            field.put("name", e.getKey());
            if (e.getValue() instanceof Map<?, ?> m) {
                for (var f : m.entrySet()) field.put(String.valueOf(f.getKey()), f.getValue());
                field.put("name", e.getKey());
            } else if (e.getValue() != null) {
                String sel = String.valueOf(e.getValue());
                field.put("selector", sel.startsWith("css:") ? sel.substring(4) : sel);
            }
            fields.add(field);
        }
        return Map.of("fields", fields);
    }

    // ── API data ──────────────────────────────────────────────────────────────
    // {api_endpoint:path} is the API endpoint's URL; it is sent percent-encoded
    // as one segment, so its own '/', '?' and '#' survive.

    public Map<String, Object> getApiData(String apiEndpoint) { return getApiData(apiEndpoint, null); }

    /** Cached records of a detected API endpoint ({@code endpointHash} may be null). */
    public Map<String, Object> getApiData(String apiEndpoint, String endpointHash) {
        return t.get("kolektr/api-data/" + seg(apiEndpoint), params("endpoint_hash", endpointHash));
    }

    public Map<String, Object> getApiDataPaginated(String apiEndpoint, int offset, int limit) {
        return getApiDataPaginated(apiEndpoint, offset, limit, null);
    }

    public Map<String, Object> getApiDataPaginated(String apiEndpoint, int offset, int limit, String endpointHash) {
        var p = params("endpoint_hash", endpointHash);
        p.put("offset", String.valueOf(offset));
        p.put("limit", String.valueOf(limit));
        return t.get("kolektr/api-data/" + seg(apiEndpoint) + "/paginated", p);
    }

    public Map<String, Object> extractAllApiData(String apiEndpoint) { return extractAllApiData(apiEndpoint, null, null); }

    /** Start a job extracting every record of an API ({@code endpointHash}, {@code maxRecords} may be null). */
    public Map<String, Object> extractAllApiData(String apiEndpoint, String endpointHash, Integer maxRecords) {
        var p = params("endpoint_hash", endpointHash);
        if (maxRecords != null) p.put("max_records", String.valueOf(maxRecords));
        return t.request("POST", "kolektr/api-data/" + seg(apiEndpoint) + "/extract-all", Map.of(), p);
    }

    // ── Content ──────────────────────────────────────────────────────────────

    public Map<String, Object> listContent() { return listContent(0, 0, null); }

    /** Stored content items (page/pageSize 0 for the defaults, 1 and 20; {@code search} matches URL or title). */
    public Map<String, Object> listContent(int page, int pageSize, String search) {
        var p = paging("page", page, "page_size", pageSize);
        if (search != null) p.put("search", search);
        return t.get("kolektr/content", p);
    }

    public Map<String, Object> getContent(String itemId) { return t.get("kolektr/content/" + seg(itemId), null); }
    public void deleteContent(String itemId) { t.delete("kolektr/content/" + seg(itemId)); }

    // ── Jobs ─────────────────────────────────────────────────────────────────

    public Map<String, Object> listJobs() { return listJobs(0, 0, null); }

    /** Extraction jobs (page/limit 0 for the defaults, 1 and 20; {@code statusFilter} may be null). */
    public Map<String, Object> listJobs(int page, int limit, String statusFilter) {
        var p = paging("page", page, "limit", limit);
        if (statusFilter != null) p.put("status_filter", statusFilter);
        return t.get("kolektr/jobs", p);
    }

    public Map<String, Object> getJob(String jobId) { return t.get("kolektr/jobs/" + seg(jobId), null); }
    public Map<String, Object> getJobStatus(String jobId) { return t.get("kolektr/extraction-jobs/" + seg(jobId) + "/status", null); }

    // ── Scraper config ───────────────────────────────────────────────────────

    public Map<String, Object> listScrapers() { return listScrapers(0, 0, null); }

    /** Scraper configs (page/pageSize 0 for the defaults, 1 and 20; {@code isTemplate} null for all). */
    public Map<String, Object> listScrapers(int page, int pageSize, Boolean isTemplate) {
        var p = paging("page", page, "page_size", pageSize);
        if (isTemplate != null) p.put("is_template", String.valueOf(isTemplate));
        return t.get("kolektr/scrapers", p);
    }

    public Map<String, Object> createScraper(Map<String, Object> config) { return t.post("kolektr/scrapers", config); }
    public Map<String, Object> getScraper(String scraperId) { return t.get("kolektr/scrapers/" + seg(scraperId), null); }
    public Map<String, Object> updateScraper(String scraperId, Map<String, Object> updates) { return t.patch("kolektr/scrapers/" + seg(scraperId), updates); }
    public void deleteScraper(String scraperId) { t.delete("kolektr/scrapers/" + seg(scraperId)); }

    // ── helpers ──────────────────────────────────────────────────────────────

    static Map<String, String> params(String key, String value) {
        var p = new LinkedHashMap<String, String>();
        if (value != null) p.put(key, value);
        return p;
    }

    static Map<String, String> paging(String pageKey, int page, String sizeKey, int size) {
        var p = new LinkedHashMap<String, String>();
        if (page > 0) p.put(pageKey, String.valueOf(page));
        if (size > 0) p.put(sizeKey, String.valueOf(size));
        return p;
    }

    // ── parsers ──────────────────────────────────────────────────────────────

    static ExtractionResult parseExtractionResult(Map<String, Object> m) {
        return new ExtractionResult(
                bool(m, "success"), str(m, "url"), str(m, "method"),
                mapList(m, "records"), integer(m, "total_records"),
                integer(m, "pages_scraped"),
                nestedArtifactId(m), str(m, "job_id"), str(m, "error"),
                bool(m, "auth_required"), str(m, "discovery_id"));
    }
}
