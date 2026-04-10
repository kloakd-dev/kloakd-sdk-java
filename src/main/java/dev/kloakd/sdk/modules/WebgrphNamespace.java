package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.util.*;
import java.util.stream.Stream;

import static dev.kloakd.sdk.modules.EvadrNamespace.*;

/** Site Mapping & Discovery module. Access via {@code client.webgrph()}. */
public final class WebgrphNamespace {

    private final HttpTransport t;

    public WebgrphNamespace(HttpTransport t) { this.t = t; }

    public CrawlResult crawl(String url) {
        return crawl(url, 0, 0, false, null, 0, 0);
    }

    public CrawlResult crawl(String url, int maxDepth, int maxPages,
            boolean includeExternalLinks, String sessionArtifactId, int limit, int offset) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (maxDepth > 0) body.put("max_depth", maxDepth);
        if (maxPages > 0) body.put("max_pages", maxPages);
        if (includeExternalLinks) body.put("include_external_links", true);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        if (limit > 0) body.put("limit", limit);
        if (offset > 0) body.put("offset", offset);
        return parseCrawlResult(t.post("webgrph/crawl", body));
    }

    public List<PageNode> crawlAll(String url, int maxDepth, int maxPages) {
        int limit = 100, offset = 0;
        var all = new ArrayList<PageNode>();
        while (true) {
            var result = crawl(url, maxDepth, maxPages, false, null, limit, offset);
            all.addAll(result.pages());
            if (!result.hasMore()) break;
            offset += result.pages().size();
        }
        return Collections.unmodifiableList(all);
    }

    public Stream<CrawlEvent> crawlStream(String url) {
        return crawlStream(url, 0, 0, null);
    }

    public Stream<CrawlEvent> crawlStream(String url, int maxDepth, int maxPages,
            String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (maxDepth > 0) body.put("max_depth", maxDepth);
        if (maxPages > 0) body.put("max_pages", maxPages);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return t.stream("webgrph/crawl/stream", body)
                .map(WebgrphNamespace::parseCrawlEvent);
    }

    public Map<String, Object> getHierarchy(String artifactId) {
        return t.get("webgrph/hierarchy/" + artifactId, null);
    }

    public Map<String, Object> getJob(String jobId) {
        return t.get("webgrph/jobs/" + jobId, null);
    }

    static CrawlResult parseCrawlResult(Map<String, Object> m) {
        var pages = new ArrayList<PageNode>();
        var raw = m.get("pages");
        if (raw instanceof List<?> list) {
            for (var item : list) {
                if (item instanceof Map<?,?> pm) {
                    @SuppressWarnings("unchecked")
                    var pm2 = (Map<String, Object>) pm;
                    pages.add(parsePageNode(pm2));
                }
            }
        }
        return new CrawlResult(
                bool(m, "success"), str(m, "crawl_id"), str(m, "url"),
                integer(m, "total_pages"), integer(m, "max_depth_reached"),
                Collections.unmodifiableList(pages),
                bool(m, "has_more"), integer(m, "total"),
                str(m, "artifact_id"), str(m, "error"));
    }

    static PageNode parsePageNode(Map<String, Object> m) {
        return new PageNode(str(m, "url"), integer(m, "depth"),
                str(m, "title"), optInt(m, "status_code"), strList(m, "children"));
    }

    static CrawlEvent parseCrawlEvent(Map<String, Object> m) {
        return new CrawlEvent(str(m, "type"), str(m, "url"),
                optInt(m, "depth"), optInt(m, "pages_found"), mapField(m, "metadata"));
    }
}
