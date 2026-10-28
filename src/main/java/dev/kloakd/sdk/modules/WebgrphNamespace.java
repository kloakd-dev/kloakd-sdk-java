package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.errors.JobTimeoutException;
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
     * A limit of 0 is not sent: the API applies the tier's default.
     */
    public CrawlResult startCrawl(String url, int maxDepth, int maxPages,
            boolean includeExternalLinks, String sessionArtifactId) {
        return startCrawl(url, CrawlOptions.builder().maxDepth(maxDepth).maxPages(maxPages)
                .includeExternalLinks(includeExternalLinks).sessionArtifactId(sessionArtifactId).buildAsSet());
    }

    /**
     * Start a site crawl ({@code POST webgrph/crawl}) with these options:
     * limits of 0 are not sent; {@code scope}, {@code budget} and
     * {@code captureMode} are sent when set. ({@code extractSchema} is not used here.)
     */
    public CrawlResult startCrawl(String url, CrawlOptions o) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (o.maxDepth() > 0) body.put("max_depth", o.maxDepth());
        if (o.maxPages() > 0) body.put("max_pages", o.maxPages());
        if (o.includeExternalLinks()) body.put("include_external_links", true);
        if (o.sessionArtifactId() != null) body.put("session_artifact_id", o.sessionArtifactId());
        if (o.scope() != null) body.put("scope", o.scope());
        if (o.budget() != null) body.put("budget", o.budget());
        if (o.captureMode() != null) body.put("capture_mode", o.captureMode());
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

    public CrawlResult crawl(String url, int maxDepth, int maxPages, boolean includeExternalLinks,
            String sessionArtifactId, Duration pollInterval, Duration pollTimeout) {
        return crawl(url, CrawlOptions.builder().maxDepth(maxDepth).maxPages(maxPages)
                .includeExternalLinks(includeExternalLinks).sessionArtifactId(sessionArtifactId).buildAsSet(),
                pollInterval, pollTimeout);
    }

    public CrawlResult crawl(String url, CrawlOptions options) {
        return crawl(url, options, DEFAULT_POLL_INTERVAL, DEFAULT_POLL_TIMEOUT);
    }

    /**
     * Start a crawl, wait for it to finish (crawls run asynchronously), and
     * return every discovered page, read by cursor from
     * {@code webgrph/crawl/{id}/pages}.
     *
     * @throws JobTimeoutException still running after {@code pollTimeout} (code
     *         {@code crawl_timeout}; {@link JobTimeoutException#getJobId()} is the crawl id)
     */
    public CrawlResult crawl(String url, CrawlOptions options, Duration pollInterval, Duration pollTimeout) {
        var started = startCrawl(url, options);
        if (started.crawlId() == null) return started;
        return waitForCrawl(started.crawlId(), url, pollInterval, pollTimeout);
    }

    /** Wait for a started crawl (by id) and return its pages; resumes after a {@link JobTimeoutException}. */
    public CrawlResult waitForCrawl(String crawlId, Duration pollInterval, Duration pollTimeout) {
        return waitForCrawl(crawlId, null, pollInterval, pollTimeout);
    }

    private CrawlResult waitForCrawl(String crawlId, String url, Duration pollInterval, Duration pollTimeout) {
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        Map<String, Object> status;
        while (true) {
            status = getCrawlStatus(crawlId);
            String state = str(status, "status");
            if ("completed".equals(state) || "failed".equals(state) || "cancelled".equals(state)) break;
            if (System.nanoTime() > deadline) {
                throw new JobTimeoutException("crawl " + crawlId + " still " + state
                        + " after " + pollTimeout, crawlId, "crawl_timeout");
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KloakdException(0, "Interrupted while waiting for crawl " + crawlId);
            }
        }
        String state = str(status, "status");
        var pages = new ArrayList<PageNode>();
        int total = 0;
        if ("completed".equals(state)) {
            String cursor = null;
            do {
                var page = getCrawlPages(crawlId, cursor, 500);
                for (var node : mapList(page, "nodes")) pages.add(parsePageNode(node));
                total = integer(page, "total");
                cursor = str(page, "next_cursor");
            } while (cursor != null && !cursor.isEmpty());
        }
        return new CrawlResult("completed".equals(state), crawlId, url != null ? url : str(status, "url"), state,
                integer(status, "total_pages"), integer(status, "max_depth_reached"),
                Collections.unmodifiableList(pages), total, str(status, "artifact_id"), str(status, "error"));
    }

    /** {@link #crawl}, returning only the pages. */
    public List<PageNode> crawlAll(String url, int maxDepth, int maxPages) {
        return crawl(url, maxDepth, maxPages, false, null).pages();
    }

    public Stream<PipelineEvent> crawlStream(String url) {
        return crawlStream(url, 0, 0, null);
    }

    public Stream<PipelineEvent> crawlStream(String url, int maxDepth, int maxPages, String sessionArtifactId) {
        return crawlStream(url, CrawlOptions.builder().maxDepth(maxDepth).maxPages(maxPages)
                .sessionArtifactId(sessionArtifactId).buildAsSet());
    }

    /**
     * Start a crawl and stream its pipeline events as they arrive
     * ({@code GET webgrph/crawl/{id}/events}): {@code pipeline_step} events,
     * then {@code pipeline_complete} (or {@code pipeline_error}).
     */
    public Stream<PipelineEvent> crawlStream(String url, CrawlOptions options) {
        var started = startCrawl(url, options);
        return crawlEvents(started.crawlId()).map(EvadrNamespace::parsePipelineEvent);
    }

    // ── Crawl follow-up ───────────────────────────────────────────────────────

    public Map<String, Object> getCrawlStatus(String crawlId) { return t.get("webgrph/crawl/" + seg(crawlId), null); }

    /** Stream a crawl's events (SSE); close the stream to stop. */
    public Stream<Map<String, Object>> crawlEvents(String crawlId) {
        return t.getStream("webgrph/crawl/" + seg(crawlId) + "/events", null);
    }

    /** One page of discovered pages ({@code cursor} null for the first). */
    public Map<String, Object> getCrawlPages(String crawlId, String cursor, int limit) {
        var params = new HashMap<String, String>();
        if (cursor != null) params.put("cursor", cursor);
        if (limit > 0) params.put("limit", String.valueOf(limit));
        return t.get("webgrph/crawl/" + seg(crawlId) + "/pages", params);
    }

    public Map<String, Object> cancelCrawl(String crawlId) { return t.post("webgrph/crawl/" + seg(crawlId) + "/cancel", null); }

    // ── Detected APIs, reader view, sessions (moved from Skanyr in 0.3.0) ─────

    public Map<String, Object> detectedApis(String pageUrl) {
        return t.get("webgrph/detected-apis", Map.of("page_url", pageUrl));
    }

    public Map<String, Object> readerView(String url, String crawlId) {
        return readerView(url, crawlId, null);
    }

    /** Reader view of a page ({@code crawlId}, {@code sessionArtifactId} may be null). */
    public Map<String, Object> readerView(String url, String crawlId, String sessionArtifactId) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (crawlId != null) body.put("crawl_id", crawlId);
        if (sessionArtifactId != null) body.put("session_artifact_id", sessionArtifactId);
        return t.post("webgrph/reader-view", body);
    }

    /**
     * Discovery sessions, under {@code "items"}. {@code active_only} is always
     * sent: the API's default is true, so false must be explicit.
     */
    public Map<String, Object> listSessions(boolean activeOnly) {
        return t.get("webgrph/sessions", Map.of("active_only", String.valueOf(activeOnly)));
    }

    public Map<String, Object> saveSession(Map<String, Object> session) { return t.post("webgrph/sessions", session); }
    public Map<String, Object> getSession(String sessionId) { return t.get("webgrph/sessions/" + seg(sessionId), null); }
    public void deleteSession(String sessionId) { t.delete("webgrph/sessions/" + seg(sessionId)); }
    public Map<String, Object> endSession(String sessionId) { return t.post("webgrph/sessions/" + seg(sessionId) + "/end", null); }

    public Map<String, Object> updateSessionJob(String sessionId, String jobId) {
        return t.request("PATCH", "webgrph/sessions/" + seg(sessionId) + "/job", null, Map.of("job_id", jobId));
    }

    static PageNode parsePageNode(Map<String, Object> m) {
        var meta = mapField(m, "metadata");
        return new PageNode(str(m, "id"), str(m, "url"), str(m, "parent_url"), integer(m, "depth"),
                str(m, "category"), str(meta, "title"), strList(m, "children_urls"), meta);
    }
}
