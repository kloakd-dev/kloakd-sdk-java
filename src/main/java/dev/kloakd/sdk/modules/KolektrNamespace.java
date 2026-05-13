package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.ExtractionResult;

import java.util.*;

import static dev.kloakd.sdk.modules.EvadrNamespace.*;

/** Data Extraction module. Access via {@code client.kolektr()}. */
public final class KolektrNamespace {

    private final HttpTransport t;

    public KolektrNamespace(HttpTransport t) { this.t = t; }

    public ExtractionResult page(String url) {
        return page(url, null, null, null, null, 0, 0);
    }

    public ExtractionResult page(String url, Map<String, Object> schema,
            String fetchArtifactId, String sessionArtifactId,
            String apiMapArtifactId, int limit, int offset) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (schema != null && !schema.isEmpty()) body.put("schema", schema);
        if (fetchArtifactId != null) body.put("fetch_artifact_id", fetchArtifactId);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        if (apiMapArtifactId != null) body.put("api_map_artifact_id", apiMapArtifactId);
        if (limit > 0) body.put("limit", limit);
        if (offset > 0) body.put("offset", offset);
        return parseExtractionResult(t.post("kolektr/page", body));
    }

    public List<Map<String, Object>> pageAll(String url, Map<String, Object> schema) {
        int limit = 100, offset = 0;
        var all = new ArrayList<Map<String, Object>>();
        while (true) {
            var result = page(url, schema, null, null, null, limit, offset);
            all.addAll(result.records());
            if (!result.hasMore()) break;
            offset += result.records().size();
        }
        return Collections.unmodifiableList(all);
    }

    public ExtractionResult extractHtml(String html, String url) {
        return extractHtml(html, url, null);
    }

    public ExtractionResult extractHtml(String html, String url, Map<String, Object> schema) {
        var body = new HashMap<String, Object>();
        body.put("html", html);
        body.put("url", url);
        if (schema != null && !schema.isEmpty()) body.put("schema", schema);
        return parseExtractionResult(t.post("kolektr/extract-html", body));
    }

    // ── API data ──────────────────────────────────────────────────────────────

    public Map<String, Object> getApiData(String apiEndpoint) { return t.get("kolektr/api-data/" + apiEndpoint, null); }
    public Map<String, Object> getApiDataPaginated(String apiEndpoint, int offset, int limit) {
        return t.get("kolektr/api-data/" + apiEndpoint + "/paginated", Map.of("offset", String.valueOf(offset), "limit", String.valueOf(limit)));
    }
    public Map<String, Object> extractAllApiData(String apiEndpoint) { return t.post("kolektr/api-data/" + apiEndpoint + "/extract-all", Map.of()); }

    // ── Content ──────────────────────────────────────────────────────────────

    public Map<String, Object> listContent() { return t.get("kolektr/content", null); }
    public Map<String, Object> getContent(String itemId) { return t.get("kolektr/content/" + itemId, null); }
    public void deleteContent(String itemId) { t.delete("kolektr/content/" + itemId); }

    // ── Jobs ─────────────────────────────────────────────────────────────────

    public Map<String, Object> listJobs() { return t.get("kolektr/jobs", null); }
    public Map<String, Object> createJob(Map<String, Object> config) { return t.post("kolektr/jobs", config); }
    public Map<String, Object> getJob(String jobId) { return t.get("kolektr/jobs/" + jobId, null); }
    public Map<String, Object> getJobStatus(String jobId) { return t.get("kolektr/extraction-jobs/" + jobId + "/status", null); }
    public Map<String, Object> getJobProgress(String jobId) { return t.get("kolektr/jobs/" + jobId + "/progress", null); }
    public Map<String, Object> getJobProgressEvents(String jobId) { return t.get("kolektr/jobs/" + jobId + "/progress/events", null); }
    public Map<String, Object> getJobProgressLatest(String jobId) { return t.get("kolektr/jobs/" + jobId + "/progress/latest", null); }
    public Map<String, Object> getJobProgressSummary(String jobId) { return t.get("kolektr/jobs/" + jobId + "/progress/summary", null); }

    // ── Pipeline ─────────────────────────────────────────────────────────────

    public Map<String, Object> getPipelineEvents(String pipelineId) { return t.get("kolektr/pipeline/" + pipelineId + "/events", null); }
    public Map<String, Object> getPipelineStream(String pipelineId) { return t.get("kolektr/pipeline/" + pipelineId + "/stream", null); }

    // ── Progress phases ──────────────────────────────────────────────────────

    public Map<String, Object> listProgressPhases() { return t.get("kolektr/progress/phases", null); }
    public Map<String, Object> getProgressPhase(String phaseName) { return t.get("kolektr/progress/phases/" + phaseName, null); }
    public Map<String, Object> getProgressPhaseSteps(String phaseName) { return t.get("kolektr/progress/phases/" + phaseName + "/steps", null); }
    public Map<String, Object> getProgressSummary() { return t.get("kolektr/progress/summary", null); }

    // ── Scraper config ───────────────────────────────────────────────────────

    public Map<String, Object> listScrapers() { return t.get("kolektr/scrapers", null); }
    public Map<String, Object> createScraper(Map<String, Object> config) { return t.post("kolektr/scrapers", config); }
    public Map<String, Object> getScraper(String scraperId) { return t.get("kolektr/scrapers/" + scraperId, null); }
    public Map<String, Object> updateScraper(String scraperId, Map<String, Object> updates) { return t.patch("kolektr/scrapers/" + scraperId, updates); }
    public void deleteScraper(String scraperId) { t.delete("kolektr/scrapers/" + scraperId); }

    // ── parsers ──────────────────────────────────────────────────────────────

    static ExtractionResult parseExtractionResult(Map<String, Object> m) {
        return new ExtractionResult(
                bool(m, "success"), str(m, "url"), str(m, "method"),
                mapList(m, "records"), integer(m, "total_records"),
                integer(m, "pages_scraped"), bool(m, "has_more"), integer(m, "total"),
                str(m, "artifact_id"), str(m, "job_id"), str(m, "error"));
    }
}
