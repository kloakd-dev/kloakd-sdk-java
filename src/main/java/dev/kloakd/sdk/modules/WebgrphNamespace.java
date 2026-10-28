package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.errors.ApiException;
import dev.kloakd.sdk.errors.KloakdException;
import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static dev.kloakd.sdk.modules.EvadrNamespace.*;

/** Site Mapping and Discovery module. Access via {@code client.webgrph()}. */
public final class WebgrphNamespace {

    /** Default interval between crawl status polls. */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(3);
    /** Default limit on waiting for a crawl. */
    public static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMinutes(60);

    private final HttpTransport t;

    public WebgrphNamespace(HttpTransport t) { this.t = t; }

    /**
     * Start a site crawl; it answers at once (HTTP 202) with its crawl ID.
     * Follow it with {@link #getCrawlStatus}, {@link #crawlEvents} or use {@link #crawl}.
     */
    public CrawlResult startCrawl(String url, int maxDepth, int maxPages,
            boolean includeExternalLinks, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (maxDepth > 0) body.put("max_depth", maxDepth);
        if (maxPages > 0) body.put("max_pages", maxPages);
        if (includeExternalLinks) body.put("include_external_links", true);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        var m = t.post("webgrph/crawl", body);
        return new CrawlResult(bool(m, "success"), str(m, "crawl_id"), str(m, "url"), str(m, "status"),
                integer(m, "total_pages"), integer(m, "max_depth_reached"), List.of(), 0,
                nestedArtifactId(m), str(m, "error"));
    }

    public CrawlResult crawl(String url) {
        return crawl(url, 0, 0, false, null);
    }

    public CrawlResult crawl(String url, int maxDepth, int maxPages,
            boolean includeExternalLinks, String sessionArtifactId) {
        return crawl(url, maxDepth, maxPages, includeExternalLinks, sessionArtifactId,
                DEFAULT_POLL_INTERVAL, DEFAULT_POLL_TIMEOUT);
    }

    /**
     * Start a crawl, wait for it to finish (crawls run asynchronously), and
     * return every discovered page, read by cursor from
     * {@code webgrph/crawl/{id}/pages}.
     */
    public CrawlResult crawl(String url, int maxDepth, int maxPages, boolean includeExternalLinks,
            String sessionArtifactId, Duration pollInterval, Duration pollTimeout) {
        var started = startCrawl(url, maxDepth, maxPages, includeExternalLinks, sessionArtifactId);
        if (started.crawlId() == null) return started;
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        Map<String, Object> status;
        while (true) {
            status = getCrawlStatus(started.crawlId());
            String state = str(status, "status");
            if ("completed".equals(state) || "failed".equals(state) || "cancelled".equals(state)) break;
            if (System.nanoTime() > deadline) {
                throw new ApiException(0, "crawl " + started.crawlId() + " still " + state
                        + " after " + pollTimeout, "crawl_timeout");
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KloakdException(0, "Interrupted while waiting for crawl " + started.crawlId());
            }
        }
        String state = str(status, "status");
        var pages = new ArrayList<PageNode>();
        int total = 0;
        if ("completed".equals(state)) {
            String cursor = null;
            do {
                var page = getCrawlPages(started.crawlId(), cursor, 500);
                for (var node : mapList(page, "nodes")) pages.add(parsePageNode(node));
                total = integer(page, "total");
                cursor = str(page, "next_cursor");
            } while (cursor != null && !cursor.isEmpty());
        }
        return new CrawlResult("completed".equals(state), started.crawlId(), url, state,
                integer(status, "total_pages"), integer(status, "max_depth_reached"),
                Collections.unmodifiableList(pages), total, str(status, "artifact_id"), str(status, "error"));
    }

    /** {@link #crawl}, returning only the pages. */
    public List<PageNode> crawlAll(String url, int maxDepth, int maxPages) {
        return crawl(url, maxDepth, maxPages, false, null).pages();
    }

    public Stream<CrawlEvent> crawlStream(String url) {
        return crawlStream(url, 0, 0, null);
    }

    /** Start a crawl and stream its events ({@code GET webgrph/crawl/{id}/events}). */
    public Stream<CrawlEvent> crawlStream(String url, int maxDepth, int maxPages,
            String sessionArtifactId) {
        var started = startCrawl(url, maxDepth, maxPages, false, sessionArtifactId);
        return crawlEvents(started.crawlId()).map(WebgrphNamespace::parseCrawlEvent);
    }

    // ── Crawl follow-up ───────────────────────────────────────────────────────

    public Map<String, Object> getCrawlStatus(String crawlId) { return t.get("webgrph/crawl/" + crawlId, null); }

    /** Stream a crawl's events (SSE); close the stream to stop. */
    public Stream<Map<String, Object>> crawlEvents(String crawlId) {
        return t.getStream("webgrph/crawl/" + crawlId + "/events", null);
    }

    /** One page of discovered pages ({@code cursor} null for the first). */
    public Map<String, Object> getCrawlPages(String crawlId, String cursor, int limit) {
        var params = new HashMap<String, String>();
        if (cursor != null) params.put("cursor", cursor);
        if (limit > 0) params.put("limit", String.valueOf(limit));
        return t.get("webgrph/crawl/" + crawlId + "/pages", params);
    }

    public Map<String, Object> cancelCrawl(String crawlId) { return t.post("webgrph/crawl/" + crawlId + "/cancel", null); }

    // ── Detected APIs, reader view, sessions (moved from Skanyr in 0.3.0) ─────

    public Map<String, Object> detectedApis(String pageUrl) {
        return t.get("webgrph/detected-apis", Map.of("page_url", pageUrl));
    }

    public Map<String, Object> readerView(String url, String crawlId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (crawlId != null) body.put("crawl_id", crawlId);
        return t.post("webgrph/reader-view", body);
    }

    /** Discovery sessions, under {@code "items"}. */
    public Map<String, Object> listSessions(boolean activeOnly) {
        return t.get("webgrph/sessions", activeOnly ? Map.of("active_only", "true") : null);
    }

    public Map<String, Object> saveSession(Map<String, Object> session) { return t.post("webgrph/sessions", session); }
    public Map<String, Object> getSession(String sessionId) { return t.get("webgrph/sessions/" + sessionId, null); }
    public void deleteSession(String sessionId) { t.delete("webgrph/sessions/" + sessionId); }
    public Map<String, Object> endSession(String sessionId) { return t.post("webgrph/sessions/" + sessionId + "/end", null); }

    public Map<String, Object> updateSessionJob(String sessionId, String jobId) {
        return t.request("PATCH", "webgrph/sessions/" + sessionId + "/job", null, Map.of("job_id", jobId));
    }

    static PageNode parsePageNode(Map<String, Object> m) {
        var meta = mapField(m, "metadata");
        return new PageNode(str(m, "id"), str(m, "url"), str(m, "parent_url"), integer(m, "depth"),
                str(m, "category"), str(meta, "title"), strList(m, "children_urls"), meta);
    }

    static CrawlEvent parseCrawlEvent(Map<String, Object> m) {
        return new CrawlEvent(str(m, "type"), str(m, "url"),
                optInt(m, "depth"), optInt(m, "pages_found"), mapField(m, "metadata"));
    }
}
