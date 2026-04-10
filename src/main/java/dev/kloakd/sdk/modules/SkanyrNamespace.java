package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.util.*;
import java.util.stream.Stream;

import static dev.kloakd.sdk.modules.EvadrNamespace.*;

/** API Discovery module. Access via {@code client.skanyr()}. */
public final class SkanyrNamespace {

    private final HttpTransport t;

    public SkanyrNamespace(HttpTransport t) { this.t = t; }

    public DiscoverResult discover(String url) {
        return discover(url, null, 0, null, 0, 0);
    }

    public DiscoverResult discover(String url, String siteHierarchyArtifactId,
            int maxRequests, String sessionArtifactId, int limit, int offset) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (siteHierarchyArtifactId != null) body.put("site_hierarchy_artifact_id", siteHierarchyArtifactId);
        if (maxRequests > 0) body.put("max_requests", maxRequests);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        if (limit > 0) body.put("limit", limit);
        if (offset > 0) body.put("offset", offset);
        return parseDiscoverResult(t.post("skanyr/discover", body));
    }

    public List<ApiEndpoint> discoverAll(String url) {
        int limit = 100, offset = 0;
        var all = new ArrayList<ApiEndpoint>();
        while (true) {
            var result = discover(url, null, 0, null, limit, offset);
            all.addAll(result.endpoints());
            if (!result.hasMore()) break;
            offset += result.endpoints().size();
        }
        return Collections.unmodifiableList(all);
    }

    public Stream<DiscoverEvent> discoverStream(String url) {
        return discoverStream(url, null, 0);
    }

    public Stream<DiscoverEvent> discoverStream(String url, String siteHierarchyArtifactId, int maxRequests) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (siteHierarchyArtifactId != null) body.put("site_hierarchy_artifact_id", siteHierarchyArtifactId);
        if (maxRequests > 0) body.put("max_requests", maxRequests);
        return t.stream("skanyr/discover/stream", body)
                .map(SkanyrNamespace::parseDiscoverEvent);
    }

    public Map<String, Object> getApiMap(String artifactId) {
        return t.get("skanyr/api-map/" + artifactId, null);
    }

    public Map<String, Object> getJob(String jobId) {
        return t.get("skanyr/jobs/" + jobId, null);
    }

    static DiscoverResult parseDiscoverResult(Map<String, Object> m) {
        var endpoints = new ArrayList<ApiEndpoint>();
        var raw = m.get("endpoints");
        if (raw instanceof List<?> list) {
            for (var item : list) {
                if (item instanceof Map<?,?> em) {
                    @SuppressWarnings("unchecked")
                    var em2 = (Map<String, Object>) em;
                    endpoints.add(parseApiEndpoint(em2));
                }
            }
        }
        return new DiscoverResult(
                bool(m, "success"), str(m, "discovery_id"), str(m, "url"),
                integer(m, "total_endpoints"), Collections.unmodifiableList(endpoints),
                bool(m, "has_more"), integer(m, "total"),
                str(m, "artifact_id"), str(m, "error"));
    }

    static ApiEndpoint parseApiEndpoint(Map<String, Object> m) {
        return new ApiEndpoint(str(m, "url"), str(m, "method"), str(m, "api_type"),
                dbl(m, "confidence"), mapField(m, "parameters"));
    }

    static DiscoverEvent parseDiscoverEvent(Map<String, Object> m) {
        return new DiscoverEvent(str(m, "type"), str(m, "endpoint_url"),
                str(m, "api_type"), mapField(m, "metadata"));
    }
}
