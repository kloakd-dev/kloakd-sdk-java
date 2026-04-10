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

    static ExtractionResult parseExtractionResult(Map<String, Object> m) {
        return new ExtractionResult(
                bool(m, "success"), str(m, "url"), str(m, "method"),
                mapList(m, "records"), integer(m, "total_records"),
                integer(m, "pages_scraped"), bool(m, "has_more"), integer(m, "total"),
                str(m, "artifact_id"), str(m, "job_id"), str(m, "error"));
    }
}
