package dev.kloakd.sdk.modules;

import dev.kloakd.sdk.errors.JobFailedException;
import dev.kloakd.sdk.errors.JobTimeoutException;
import dev.kloakd.sdk.errors.KloakdException;
import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.models.*;

import java.nio.file.Path;
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
    /** The status of a crawl paused because the organisation's quota ran out. */
    public static final String HELD_QUOTA = "held_quota";

    private final HttpTransport t;

    public WebgrphNamespace(HttpTransport t) { this.t = t; }

    /**
     * Start a site crawl; it answers at once (HTTP 202) with its crawl ID.
     * Follow it with {@link #waitForCrawl(String)}, {@link #getCrawlStatus},
     * {@link #crawlEvents} or use {@link #crawl}. A limit of 0 is not sent:
     * the API applies the plan's default. The result's {@code status} is
     * null: the answer has none.
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
        var m = t.post("webgrph/crawl", crawlBody(url, o));
        // CrawlResponse has no status (kobe-scraper modules/webgrph/router.py)
        return new CrawlResult(bool(m, "success"), str(m, "crawl_id"), str(m, "url"), null,
                integer(m, "total_pages"), integer(m, "max_depth_reached"), List.of(), 0,
                nestedArtifactId(m), str(m, "error"));
    }

    private static HashMap<String, Object> crawlBody(String url, CrawlOptions o) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        if (o.maxDepth() > 0) body.put("max_depth", o.maxDepth());
        if (o.maxPages() > 0) body.put("max_pages", o.maxPages());
        if (o.includeExternalLinks()) body.put("include_external_links", true);
        if (o.sessionArtifactId() != null) body.put("session_artifact_id", o.sessionArtifactId());
        if (o.scope() != null) body.put("scope", o.scope());
        if (o.budget() != null) body.put("budget", o.budget());
        if (o.captureMode() != null) body.put("capture_mode", o.captureMode());
        return body;
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
     * return every fetched page, read by cursor from
     * {@code webgrph/crawl/{id}/pages}.
     *
     * <p>The wait also ends when the crawl is held ({@code status}
     * {@code held_quota}): the result then has {@link CrawlResult#held()}
     * true, {@code success} false, no pages, {@code error} null and the
     * API's text in {@code message}. A held crawl is paused, not failed: the
     * organisation's quota ran out. It resumes by itself on the same id when
     * quota returns (an upgrade, a top-up or a new billing period), so call
     * {@link #waitForCrawl(String)} with its id again later. After 30 days
     * held it ends {@code failed} with error code {@code held_quota_expired}.
     *
     * <p>A crawl that stopped early has {@link CrawlResult#isPartial()} true
     * and a {@code partialReason}; when {@code resumable} is true,
     * {@link #continueCrawl} goes on with the pages it did not fetch.
     *
     * @throws JobTimeoutException still running after {@code pollTimeout} (code
     *         {@code crawl_timeout}; {@link JobTimeoutException#getJobId()} is the crawl id)
     */
    public CrawlResult crawl(String url, CrawlOptions options, Duration pollInterval, Duration pollTimeout) {
        var started = startCrawl(url, options);
        if (started.crawlId() == null) return started;
        return waitForCrawl(started.crawlId(), url, pollInterval, pollTimeout, false);
    }

    /** {@link #waitForCrawl(String, Duration, Duration)} with the default interval (3 s) and limit (60 min). */
    public CrawlResult waitForCrawl(String crawlId) {
        return waitForCrawl(crawlId, null, DEFAULT_POLL_INTERVAL, DEFAULT_POLL_TIMEOUT, false);
    }

    /**
     * Wait for a started crawl (by id) and return its pages. Use it after a
     * {@link JobTimeoutException}, after a held crawl (see
     * {@link #crawl(String, CrawlOptions, Duration, Duration)}) and after
     * {@link #continueCrawl}. The result's {@code url} is null: the crawl's
     * status does not hold it.
     */
    public CrawlResult waitForCrawl(String crawlId, Duration pollInterval, Duration pollTimeout) {
        return waitForCrawl(crawlId, null, pollInterval, pollTimeout, false);
    }

    /**
     * {@link #waitForCrawl(String, Duration, Duration)}; with {@code includeGrouping}
     * the pages also hold the hierarchy's grouping folders
     * ({@link #getCrawlPages(String, String, int, boolean)}).
     */
    public CrawlResult waitForCrawl(String crawlId, Duration pollInterval, Duration pollTimeout, boolean includeGrouping) {
        return waitForCrawl(crawlId, null, pollInterval, pollTimeout, includeGrouping);
    }

    private CrawlResult waitForCrawl(String crawlId, String url, Duration pollInterval, Duration pollTimeout,
            boolean includeGrouping) {
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        Map<String, Object> status;
        while (true) {
            status = getCrawlStatus(crawlId);
            String state = str(status, "status");
            if ("completed".equals(state) || "failed".equals(state) || "cancelled".equals(state)
                    || HELD_QUOTA.equals(state)) break;
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
                var page = getCrawlPages(crawlId, cursor, 500, includeGrouping);
                for (var node : mapList(page, "nodes")) pages.add(parsePageNode(node));
                total = integer(page, "total");
                cursor = str(page, "next_cursor");
            } while (cursor != null && !cursor.isEmpty());
        }
        boolean held = HELD_QUOTA.equals(state);
        // CrawlStatusResponse has no url; a held crawl's message is not an error
        return new CrawlResult("completed".equals(state), crawlId, url, state,
                integer(status, "total_pages"), integer(status, "max_depth_reached"),
                Collections.unmodifiableList(pages), total, str(status, "artifact_id"),
                held ? null : str(status, "error"),
                optBool(status, "is_partial"), str(status, "partial_reason"), optBool(status, "resumable"),
                optInt(status, "pages_discovered"), optInt(status, "pages_fetched"),
                optInt(status, "pages_unfetched"), str(status, "error_code"), str(status, "message"), held);
    }

    /**
     * Go on with a crawl that ended before fetching every page it found
     * ({@code POST webgrph/crawl/{crawl_id}/continue}, no body; HTTP 202).
     * Only for an ended crawl whose {@link CrawlResult#resumable()} is true;
     * the crawl keeps its id, so wait for it again with
     * {@link #waitForCrawl(String)}. The quota checks of a new crawl apply.
     *
     * @throws dev.kloakd.sdk.errors.ApiException 409: the crawl cannot be continued (it is not
     *         resumable, has not ended, or its id is unknown) or is already continuing
     */
    public CrawlContinueResult continueCrawl(String crawlId) {
        var m = t.post("webgrph/crawl/" + seg(crawlId) + "/continue", null);
        return new CrawlContinueResult(bool(m, "success"), str(m, "crawl_id"), str(m, "status"));
    }

    /**
     * {@link #crawl}, returning only the pages. The list is empty when the
     * crawl did not complete, including when it is held for quota; use
     * {@link #crawl} to see which.
     */
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

    /** Most pages one {@link #getCrawlPages} call returns ({@code limit}). */
    public static final int MAX_PAGES_LIMIT = 1000;

    /**
     * One page of a crawl's fetched pages ({@code GET webgrph/crawl/{id}/pages}),
     * as {@code {crawl_id, nodes, next_cursor, total}}.
     *
     * @param cursor null for the first page, else the previous page's {@code next_cursor}
     *               (anything but a non-negative integer is answered with 422)
     * @param limit nodes per page, 1–{@value #MAX_PAGES_LIMIT}, or 0 for the API default (100)
     * @throws IllegalArgumentException {@code limit} is negative or above {@value #MAX_PAGES_LIMIT}
     */
    public Map<String, Object> getCrawlPages(String crawlId, String cursor, int limit) {
        return getCrawlPages(crawlId, cursor, limit, false);
    }

    /**
     * {@link #getCrawlPages(String, String, int)}, optionally with the
     * hierarchy's grouping folders. By default only fetched pages are
     * listed, each under the page the crawl found it on, and {@code total}
     * matches the crawl's {@code total_pages}. With {@code includeGrouping}
     * the displayed tree is returned as stored: grouping folders (never
     * fetched; {@code metadata.is_grouping} true, see {@link PageNode#isGrouping()})
     * with grouped pages under them.
     */
    public Map<String, Object> getCrawlPages(String crawlId, String cursor, int limit, boolean includeGrouping) {
        if (limit < 0 || limit > MAX_PAGES_LIMIT) {
            throw new IllegalArgumentException("limit must be 1-" + MAX_PAGES_LIMIT + " (or 0 for the default), was " + limit);
        }
        var params = new HashMap<String, String>();
        if (cursor != null) params.put("cursor", cursor);
        if (limit > 0) params.put("limit", String.valueOf(limit));
        if (includeGrouping) params.put("include_grouping", "true");
        return t.get("webgrph/crawl/" + seg(crawlId) + "/pages", params);
    }

    public Map<String, Object> cancelCrawl(String crawlId) { return t.post("webgrph/crawl/" + seg(crawlId) + "/cancel", null); }

    // ── Crawl, extract and download in one job ───────────────────────────────

    /** Most rows or pages one {@link #getCrawlExtractResults} or {@link #getCrawlExtractPages} call returns. */
    public static final int CRAWL_EXTRACT_MAX_LIMIT = 1000;

    /** {@link #startCrawlExtract(String, CrawlOptions, Map)} with the plan's limits; the API chooses what to extract. */
    public Map<String, Object> startCrawlExtract(String url) {
        return startCrawlExtract(url, null, null);
    }

    /**
     * Start a job that crawls a site, extracts every page and keeps the rows
     * for download ({@code POST webgrph/crawl-extract}; kobe-scraper
     * modules/webgrph/crawl_extract_router.py). It answers at once (HTTP 202)
     * with {@code {success, job_id, crawl_id, url, status, limits, links}}.
     * The plan must include Kolektr, and a test key is refused (403).
     *
     * @param options the crawl's limits, scope, budget, capture mode and session, as for
     *                {@link #startCrawl(String, CrawlOptions)} (limits of 0 are not sent;
     *                {@code extractSchema} is not used here), or null
     * @param extract what to extract, in the API's own fields: {@code entity_type},
     *                {@code fields} (a list of names) or {@code schema}
     *                ({@code item_selector} and {@code fields} of
     *                {@code {name, selector, attribute, type}}); null lets the API choose
     */
    public Map<String, Object> startCrawlExtract(String url, CrawlOptions options, Map<String, Object> extract) {
        var body = crawlBody(url, options != null ? options : CrawlOptions.builder().buildAsSet());
        if (extract != null) body.put("extract", extract);
        return t.post("webgrph/crawl-extract", body);
    }

    /**
     * A crawl-extract job's state ({@code GET webgrph/crawl-extract/{job_id}}):
     * {@code status} ({@code queued}, {@code crawling}, {@code extracting},
     * {@code completed}, {@code failed} or {@code cancelled}), {@code progress}
     * ({@code pages_crawled, pages_total, pages_extracted, pages_failed, items}),
     * {@code error}, {@code links}, {@code pages_by_method}, {@code shapes} and
     * {@code crawl}: the job's crawl ({@code status, is_partial, partial_reason,
     * pages_discovered, pages_fetched, pages_unfetched, cancelled, root_unfetched,
     * error, error_code}), or null before there is one. The job itself is never
     * {@code held_quota}; its crawl can be ({@link #isCrawlExtractHeld}).
     */
    public Map<String, Object> getCrawlExtract(String jobId) {
        return t.get("webgrph/crawl-extract/" + seg(jobId), null);
    }

    /** {@link #waitForCrawlExtract(String, Duration, Duration)} with the default interval (3 s) and limit (60 min). */
    public Map<String, Object> waitForCrawlExtract(String jobId) {
        return waitForCrawlExtract(jobId, DEFAULT_POLL_INTERVAL, DEFAULT_POLL_TIMEOUT);
    }

    /**
     * Whether a crawl-extract status ({@link #getCrawlExtract}) says the
     * job's crawl is held for quota ({@code crawl.status} is {@code held_quota}).
     */
    public static boolean isCrawlExtractHeld(Map<String, Object> status) {
        return status != null && HELD_QUOTA.equals(str(mapField(status, "crawl"), "status"));
    }

    /**
     * Poll a crawl-extract job until it ends and return its last state,
     * whether {@code completed}, {@code failed} or {@code cancelled}. The
     * returned map is the API's status with one key added by the SDK,
     * {@code held} (a Boolean).
     *
     * <p>The wait also ends, with {@code held} true, when the job's crawl is
     * held ({@code crawl.status} is {@code held_quota}): the organisation's
     * quota ran out, and the job waits for its crawl. Nothing has failed.
     * The crawl resumes by itself when quota returns (an upgrade, a top-up
     * or a new billing period) and the job goes on under the same id, so
     * call this again later. A crawl held for 30 days ends failed
     * ({@code held_quota_expired}).
     *
     * @throws JobTimeoutException still running after {@code pollTimeout};
     *         {@link JobTimeoutException#getJobId()} is the job id
     */
    public Map<String, Object> waitForCrawlExtract(String jobId, Duration pollInterval, Duration pollTimeout) {
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        while (true) {
            var status = getCrawlExtract(jobId);
            String state = str(status, "status");
            boolean ended = "completed".equals(state) || "failed".equals(state) || "cancelled".equals(state);
            boolean held = !ended && isCrawlExtractHeld(status);
            if (ended || held) {
                var result = new LinkedHashMap<String, Object>(status);
                result.put("held", held);
                return result;
            }
            if (System.nanoTime() > deadline) {
                throw new JobTimeoutException("crawl-extract job " + jobId + " still " + state
                        + " after " + pollTimeout, jobId);
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KloakdException(0, "Interrupted while waiting for crawl-extract job " + jobId);
            }
        }
    }

    public Map<String, Object> crawlExtract(String url, CrawlOptions options, Map<String, Object> extract) {
        return crawlExtract(url, options, extract, DEFAULT_POLL_INTERVAL, DEFAULT_POLL_TIMEOUT);
    }

    /**
     * {@link #startCrawlExtract(String, CrawlOptions, Map)}, then
     * {@link #waitForCrawlExtract(String, Duration, Duration)}. A cancelled
     * job is returned, not thrown, and so is one whose crawl is held for
     * quota ({@code held} true; wait for its {@code job_id} again later).
     *
     * @throws JobFailedException  the job failed (its {@code error} is the message)
     * @throws JobTimeoutException still running after {@code pollTimeout}
     */
    public Map<String, Object> crawlExtract(String url, CrawlOptions options, Map<String, Object> extract,
            Duration pollInterval, Duration pollTimeout) {
        String jobId = str(startCrawlExtract(url, options, extract), "job_id");
        if (jobId == null) throw new KloakdException(0, "the API started a crawl-extract job without a job_id");
        var status = waitForCrawlExtract(jobId, pollInterval, pollTimeout);
        if ("failed".equals(str(status, "status"))) {
            String error = str(status, "error");
            throw new JobFailedException(error != null ? error : "Crawl-extract job " + jobId + " failed", jobId);
        }
        return status;
    }

    private static HashMap<String, String> rowFilters(String shape, String pageType, String kind) {
        var params = new HashMap<String, String>();
        if (shape != null) params.put("shape", shape);
        if (pageType != null) params.put("page_type", pageType);
        if (kind != null) params.put("kind", kind);
        return params;
    }

    private static void putLimit(Map<String, String> params, int limit) {
        if (limit < 0 || limit > CRAWL_EXTRACT_MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be 1-" + CRAWL_EXTRACT_MAX_LIMIT
                    + " (or 0 for the default), was " + limit);
        }
        if (limit > 0) params.put("limit", String.valueOf(limit));
    }

    public Map<String, Object> getCrawlExtractResults(String jobId, String cursor, int limit) {
        return getCrawlExtractResults(jobId, cursor, limit, null, null, null);
    }

    /**
     * One page of a job's extracted rows
     * ({@code GET webgrph/crawl-extract/{job_id}/results}), as
     * {@code {job_id, status, total, count, rows, next_cursor}}; a row is
     * {@code {page_url, page_type, row_kind, item_index, data, shape}}.
     * Rows can be read while the job runs.
     *
     * @param cursor   null for the first page, else the previous page's {@code next_cursor}
     *                 (null on the last page)
     * @param limit    rows per page, 1–{@value #CRAWL_EXTRACT_MAX_LIMIT}, or 0 for the API default (100)
     * @param shape    only rows of this shape (see the job's {@code shapes}), or null
     * @param pageType only rows from pages of this type, or null
     * @param kind     {@code item} or {@code page}, or null
     * @throws IllegalArgumentException {@code limit} is negative or above {@value #CRAWL_EXTRACT_MAX_LIMIT}
     */
    public Map<String, Object> getCrawlExtractResults(String jobId, String cursor, int limit,
            String shape, String pageType, String kind) {
        var params = rowFilters(shape, pageType, kind);
        putLimit(params, limit);
        if (cursor != null) params.put("cursor", cursor);
        return t.get("webgrph/crawl-extract/" + seg(jobId) + "/results", params);
    }

    public List<Map<String, Object>> crawlExtractAllResults(String jobId) {
        return crawlExtractAllResults(jobId, null, null, null);
    }

    /** Every row of a job, read by cursor {@value #CRAWL_EXTRACT_MAX_LIMIT} at a time (for a large job prefer {@link #downloadCrawlExtract}). */
    public List<Map<String, Object>> crawlExtractAllResults(String jobId, String shape, String pageType, String kind) {
        var rows = new ArrayList<Map<String, Object>>();
        String cursor = null;
        do {
            var page = getCrawlExtractResults(jobId, cursor, CRAWL_EXTRACT_MAX_LIMIT, shape, pageType, kind);
            rows.addAll(mapList(page, "rows"));
            cursor = str(page, "next_cursor");
        } while (cursor != null && !cursor.isEmpty());
        return Collections.unmodifiableList(rows);
    }

    /**
     * Some of a job's crawled pages and what each one's extraction came to
     * ({@code GET webgrph/crawl-extract/{job_id}/pages}), as
     * {@code {job_id, status, count, offset, pages}}; a page is
     * {@code {page_seq, page_url, page_type, title, status, method, item_count, detail, extracted_at}}.
     *
     * @param limit  pages returned, 1–{@value #CRAWL_EXTRACT_MAX_LIMIT}, or 0 for the API default
     * @param offset pages to skip (0 for the first)
     * @param status {@code extracted} or {@code failed}, or null for both
     * @throws IllegalArgumentException {@code limit} or {@code offset} is out of range
     */
    public Map<String, Object> getCrawlExtractPages(String jobId, int limit, int offset, String status) {
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative, was " + offset);
        var params = new HashMap<String, String>();
        putLimit(params, limit);
        if (offset > 0) params.put("offset", String.valueOf(offset));
        if (status != null) params.put("status", status);
        return t.get("webgrph/crawl-extract/" + seg(jobId) + "/pages", params);
    }

    public ExportDownload exportCrawlExtract(String jobId, String format) {
        return exportCrawlExtract(jobId, format, null, null, null);
    }

    /**
     * A job's rows as a file, streamed
     * ({@code GET webgrph/crawl-extract/{job_id}/export}). Close the export
     * when done. For a job that has not completed the file holds the rows
     * stored so far ({@link ExportDownload#jobStatus()}), and
     * {@link ExportDownload#crawl()} says whether the crawl behind the rows
     * is partial, held or cancelled (the {@code X-Crawl-*} headers). The CSV
     * starts with a UTF-8 byte order mark.
     *
     * @param format {@code json}, {@code ndjson} or {@code csv}
     * @param shape    only rows of this shape, or null
     * @param pageType only rows from pages of this type, or null
     * @param kind     {@code item} or {@code page}, or null
     * @throws IllegalArgumentException {@code format} is none of the three
     */
    public ExportDownload exportCrawlExtract(String jobId, String format, String shape, String pageType, String kind) {
        var params = rowFilters(shape, pageType, kind);
        params.put("format", ExportDownload.checkFormat(format));
        return ExportDownload.of(t.download("webgrph/crawl-extract/" + seg(jobId) + "/export", params));
    }

    public SavedExport downloadCrawlExtract(String jobId, Path path, String format) {
        return downloadCrawlExtract(jobId, path, format, null, null, null);
    }

    /**
     * {@link #exportCrawlExtract(String, String, String, String, String)}
     * written to {@code path}. The file is created once the API has answered
     * 200, so a refused request leaves no file.
     */
    public SavedExport downloadCrawlExtract(String jobId, Path path, String format,
            String shape, String pageType, String kind) {
        return exportCrawlExtract(jobId, format, shape, pageType, kind).saveTo(path);
    }

    /**
     * Cancel a job ({@code POST webgrph/crawl-extract/{job_id}/cancel}):
     * {@code {success, job_id, crawl_id, status}}, with {@code status}
     * {@code cancelled} (it had not started) or {@code cancelling}. A job
     * that has ended is answered with 409 ({@link dev.kloakd.sdk.errors.ApiException}).
     */
    public Map<String, Object> cancelCrawlExtract(String jobId) {
        return t.post("webgrph/crawl-extract/" + seg(jobId) + "/cancel", null);
    }

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
