package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.*;
import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.modules.*;
import dev.kloakd.sdk.models.*;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * KLOAKD Java SDK client.
 *
 * <pre>{@code
 * Kloakd client = Kloakd.builder()
 *     .apiKey("sk-live-...")
 *     .organizationId("your-org-uuid")
 *     .build();
 * }</pre>
 */
public final class Kloakd {

    static final String DEFAULT_BASE_URL = "https://api.kloakd.dev";
    /** Above the API's 85 s synchronous wait, so a slow request receives its 202 hand-off. */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(100);
    static final int DEFAULT_MAX_RETRIES = 3;

    private final EvadrNamespace evadr;
    private final WebgrphNamespace webgrph;
    private final SkanyrNamespace skanyr;
    private final NexusNamespace nexus;
    private final ParlyrNamespace parlyr;
    private final FetchyrNamespace fetchyr;
    private final KolektrNamespace kolektr;
    private final HttpTransport transport;

    private Kloakd(Builder builder) {
        String baseUrl = builder.baseUrl != null ? builder.baseUrl : DEFAULT_BASE_URL;
        Duration timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        int maxRetries = builder.maxRetries >= 0 ? builder.maxRetries : DEFAULT_MAX_RETRIES;

        var transport = new HttpTransport(
                builder.apiKey,
                builder.organizationId,
                baseUrl,
                timeout,
                maxRetries,
                builder.httpClient,
                builder.pollInterval,
                builder.pollTimeout);

        this.transport = transport;
        this.evadr   = new EvadrNamespace(transport);
        this.webgrph = new WebgrphNamespace(transport);
        this.skanyr  = new SkanyrNamespace(transport);
        this.nexus   = new NexusNamespace(transport);
        this.parlyr  = new ParlyrNamespace(transport);
        this.fetchyr = new FetchyrNamespace(transport);
        this.kolektr = new KolektrNamespace(transport);
    }

    public EvadrNamespace evadr()   { return evadr; }
    public WebgrphNamespace webgrph() { return webgrph; }
    public SkanyrNamespace skanyr() { return skanyr; }
    public NexusNamespace nexus()   { return nexus; }
    public ParlyrNamespace parlyr() { return parlyr; }
    public FetchyrNamespace fetchyr() { return fetchyr; }
    public KolektrNamespace kolektr() { return kolektr; }

    // ── Hand-off ────────────────────────────────────────────────────────────

    /**
     * The state of a request the API handed off as a job (HTTP 202):
     * {@code GET handoff/{job_id}} answers {@code status} ({@code running},
     * {@code completed} with the route's response as {@code result}, or
     * {@code failed} with {@code error}). Results are kept for an hour.
     */
    public Map<String, Object> getHandoff(String jobId) {
        return transport.get("handoff/" + HttpTransport.seg(jobId), null);
    }

    /** {@link #waitForHandoff(String, Duration, Duration)} with the client's poll settings. */
    public Map<String, Object> waitForHandoff(String jobId) {
        return waitForHandoff(jobId, transport.pollInterval(), transport.pollTimeout());
    }

    /**
     * Wait for a handed-off job, for example the {@link dev.kloakd.sdk.errors.JobTimeoutException#getJobId()}
     * of a call that gave up waiting, and return the route's own response body.
     *
     * @throws dev.kloakd.sdk.errors.JobFailedException  the job failed, or expired (404)
     * @throws dev.kloakd.sdk.errors.JobTimeoutException still running after {@code pollTimeout}
     */
    public Map<String, Object> waitForHandoff(String jobId, Duration pollInterval, Duration pollTimeout) {
        return transport.followHandOff(jobId, "handoff/" + HttpTransport.seg(jobId), pollInterval, pollTimeout);
    }

    // ── Batch export ────────────────────────────────────────────────────────

    /**
     * A batch job's results as a file, streamed
     * ({@code GET batch-jobs/{batch_job_id}/export}; kobe-scraper
     * kernel/batch_export_router.py). Close the export when done. This route
     * sends neither {@code X-Job-Status} nor {@code X-Crawl-*} headers, so
     * the export's {@code jobStatus()} and {@code crawl()} are null.
     *
     * @param format {@code json}, {@code ndjson} or {@code csv}
     * @throws IllegalArgumentException {@code format} is none of the three
     */
    public ExportDownload exportBatchJob(String batchJobId, String format) {
        return ExportDownload.of(transport.download("batch-jobs/" + HttpTransport.seg(batchJobId) + "/export",
                Map.of("format", ExportDownload.checkFormat(format))));
    }

    /**
     * {@link #exportBatchJob(String, String)} written to {@code path}. The
     * file is created once the API has answered 200, so a refused request
     * leaves no file.
     */
    public SavedExport downloadBatchJob(String batchJobId, Path path, String format) {
        return exportBatchJob(batchJobId, format).saveTo(path);
    }

    // ── Batch jobs ──────────────────────────────────────────────────────────

    /** {@link #createBatchJob(List, BatchJobOptions)} with the URLs alone. */
    public BatchJob createBatchJob(List<String> urls) {
        return createBatchJob(urls, null);
    }

    /**
     * Create a batch job: one extraction per URL ({@code POST batch-jobs},
     * 201; kobe-scraper kernel/jobs_runs_router.py). The batch holds one
     * work slot until it ends. Never retried.
     *
     * @param urls    the URLs, at least one
     * @param options how to extract them, or null
     * @throws ForbiddenException        403 {@code test_key_not_allowed}: a test key
     * @throws ConcurrencyLimitException 409: the plan's running batches are in use
     * @throws ApiException              413 {@code batch_too_large}: more URLs than the plan allows;
     *                                   422: no URLs; 503 {@code dispatch_failed}: the batch could
     *                                   not be queued (nothing was started)
     */
    public BatchJob createBatchJob(List<String> urls, BatchJobOptions options) {
        var body = new HashMap<String, Object>();
        body.put("urls", urls);
        if (options != null) {
            if (options.extractionConfig() != null) body.put("extraction_config", options.extractionConfig());
            if (options.crawlId() != null) body.put("crawl_id", options.crawlId());
            if (options.entityType() != null) body.put("entity_type", options.entityType());
            if (options.fields() != null) body.put("fields", options.fields());
        }
        return parseBatchJob(transport.post("batch-jobs", body));
    }

    /**
     * A batch job's state ({@code GET batch-jobs/{batch_job_id}}).
     *
     * @throws ApiException 404: no such batch of yours
     */
    public BatchJob getBatchJob(String batchJobId) {
        return parseBatchJob(transport.get("batch-jobs/" + HttpTransport.seg(batchJobId), null));
    }

    /** {@link #listBatchJobs(int, int)} with the API's defaults (page 1, 20 a page). */
    public BatchJobsPage listBatchJobs() {
        return listBatchJobs(0, 0);
    }

    /**
     * The organisation's batch jobs, newest first ({@code GET batch-jobs}).
     *
     * @param page     from 1; 0 for the API's default
     * @param pageSize 1 to 100; 0 for the API's default (20)
     */
    public BatchJobsPage listBatchJobs(int page, int pageSize) {
        var params = new LinkedHashMap<String, String>();
        if (page > 0) params.put("page", String.valueOf(page));
        if (pageSize > 0) params.put("page_size", String.valueOf(pageSize));
        var m = transport.get("batch-jobs", params.isEmpty() ? null : params);
        var jobs = new ArrayList<BatchJob>();
        for (var job : maps(m.get("batch_jobs"))) jobs.add(parseBatchJob(job));
        return new BatchJobsPage(intOf(m.get("total")), Collections.unmodifiableList(jobs));
    }

    /** {@link #getBatchJobResults(String, int, int)} with every item. */
    public BatchJobResults getBatchJobResults(String batchJobId) {
        return getBatchJobResults(batchJobId, 0, 0);
    }

    /**
     * A batch's items with what each extracted
     * ({@code GET batch-jobs/{batch_job_id}/results}). To download the rows
     * use {@link #exportBatchJob}.
     *
     * @param limit  1 to 1000 items from {@code offset}, and the counts are then of the
     *               whole batch; 0 for every item
     * @param offset items to skip; 0 for none
     */
    public BatchJobResults getBatchJobResults(String batchJobId, int limit, int offset) {
        var params = new LinkedHashMap<String, String>();
        if (limit > 0) params.put("limit", String.valueOf(limit));
        if (offset > 0) params.put("offset", String.valueOf(offset));
        var m = transport.get("batch-jobs/" + HttpTransport.seg(batchJobId) + "/results",
                params.isEmpty() ? null : params);
        var items = new ArrayList<BatchJobItem>();
        for (var item : maps(m.get("results"))) {
            items.add(new BatchJobItem(text(item.get("job_id")), text(item.get("url")), text(item.get("status")),
                    item.get("extracted_data"), mapOrNull(item.get("api_data"))));
        }
        String id = text(m.get("batch_job_id"));
        return new BatchJobResults(id != null ? id : batchJobId, Collections.unmodifiableList(items),
                intOf(m.get("total_jobs")), intOf(m.get("completed_jobs")), intOf(m.get("failed_jobs")));
    }

    /**
     * Pause a running batch ({@code POST batch-jobs/{batch_job_id}/pause};
     * kobe-scraper application/jobs/batch_control.py). Items already running
     * finish; no other item starts until {@link #resumeBatchJob}. Pausing a
     * paused batch changes nothing.
     *
     * @throws ApiException 409 with code {@code invalid_batch_transition}: the batch is in
     *                      any other state; 404: no such batch of yours
     */
    public BatchJob pauseBatchJob(String batchJobId) {
        return batchControl(batchJobId, "/pause");
    }

    /**
     * Resume a paused batch ({@code POST batch-jobs/{batch_job_id}/resume}):
     * the items held while it was paused are queued again, each once.
     * Resuming a running batch changes nothing. Gated like creation (quota,
     * payment, the plan's running batches). Never retried.
     *
     * @throws ApiException              409 with code {@code invalid_batch_transition}: the batch
     *                                   is in any other state; 503 with code {@code dispatch_failed}:
     *                                   the items could not be queued and the batch is paused
     *                                   again, so call it again; 404: no such batch of yours
     * @throws ConcurrencyLimitException 409: the plan's running batches are in use
     */
    public BatchJob resumeBatchJob(String batchJobId) {
        return batchControl(batchJobId, "/resume");
    }

    /**
     * Cancel a batch and every item of it that has not ended
     * ({@code POST batch-jobs/{batch_job_id}/cancel}). Completed and failed
     * items keep their results. Cancelling a cancelled batch changes nothing.
     *
     * @throws ApiException 409 with code {@code invalid_batch_transition}: the batch has
     *                      completed or failed; 404: no such batch of yours
     */
    public BatchJob cancelBatchJob(String batchJobId) {
        return batchControl(batchJobId, "/cancel");
    }

    private BatchJob batchControl(String batchJobId, String action) {
        return parseBatchJob(transport.post("batch-jobs/" + HttpTransport.seg(batchJobId) + action, null));
    }

    // ── Unified scrape ──────────────────────────────────────────────────────

    /** {@link #unifiedScrape(String, UnifiedScrapeOptions)} with the API's defaults. */
    public UnifiedScrapeResult unifiedScrape(String url) {
        return unifiedScrape(url, null);
    }

    /**
     * Scrape a URL and let the API choose how ({@code POST scraping/unified};
     * kobe-scraper api/v1/endpoints/unified_scraping.py): level 1 (direct
     * extraction) or level 2 (the Strategy Engine), with escalation when the
     * first fails. Needs a live API key entitled to Kolektr and Nexus. A
     * request the API hands off as a job (HTTP 202) is followed to its
     * result, as every other long request is. The answer names the job; read
     * its state and data with {@link #getUnifiedScrape}. When the URL is
     * behind a login the answer has {@code authRequired} true and nothing
     * was scraped.
     *
     * <p>Check {@code status()}: when the scrape fails the API still answers
     * with {@code success} true and {@code status} {@code "failed"}, without
     * the error text, and that answer's {@code jobId} names no stored job, so
     * {@link #getUnifiedScrape} on it throws the API's 404.
     *
     * @param url     the page
     * @param options prompt, mode, schema and the rest, or null; only what is set is sent
     * @throws ForbiddenException  403 {@code test_key_not_allowed} or {@code module_not_entitled}
     * @throws JobFailedException  a handed-off request failed or expired
     * @throws JobTimeoutException a handed-off request was still running at the poll timeout
     */
    public UnifiedScrapeResult unifiedScrape(String url, UnifiedScrapeOptions options) {
        var body = new HashMap<String, Object>();
        body.put("url", url);
        Duration interval = transport.pollInterval();
        Duration limit = transport.pollTimeout();
        if (options != null) {
            if (options.prompt() != null) body.put("prompt", options.prompt());
            if (options.mode() != null) body.put("mode", options.mode());
            if (options.schema() != null) body.put("schema", options.schema());
            if (options.options() != null) body.put("options", options.options());
            if (options.context() != null) body.put("context", options.context());
            if (options.constraints() != null) body.put("constraints", options.constraints());
            if (options.sessionArtifactId() != null) body.put("session_artifact_id", options.sessionArtifactId());
            if (options.pollInterval() != null) interval = options.pollInterval();
            if (options.pollTimeout() != null) limit = options.pollTimeout();
        }
        var m = transport.postFollowing("scraping/unified", body, interval, limit);
        return new UnifiedScrapeResult(!Boolean.FALSE.equals(m.get("success")), text(m.get("job_id")),
                text(m.get("level")), text(m.get("status")), escalations(m.get("escalation_path")),
                text(m.get("message")), mapOrNull(m.get("metadata")),
                Boolean.TRUE.equals(m.get("auth_required")), text(m.get("discovery_id")));
    }

    /**
     * The state of a unified scrape job and, once it has one, its result
     * ({@code GET scraping/unified/{job_id}}).
     *
     * @throws ApiException 404: no such job of yours
     */
    public UnifiedScrapeStatus getUnifiedScrape(String jobId) {
        var m = transport.get("scraping/unified/" + HttpTransport.seg(jobId), null);
        var detail = mapOrNull(m.get("progress_detail"));
        UnifiedScrapeProgress progress = detail == null ? null : new UnifiedScrapeProgress(
                intOf(detail.get("percentage")), text(detail.get("current_step")),
                optInt(detail.get("items_found")), optInt(detail.get("items_target")),
                detail.get("partial_results") instanceof List<?> ? maps(detail.get("partial_results")) : null);
        String id = text(m.get("id"));
        return new UnifiedScrapeStatus(id != null ? id : jobId, text(m.get("status")), text(m.get("level")),
                optInt(m.get("progress")), progress, text(m.get("created_at")), text(m.get("updated_at")),
                text(m.get("completed_at")), text(m.get("error")), mapOrNull(m.get("result")),
                escalations(m.get("escalation_path")));
    }

    private static BatchJob parseBatchJob(Map<String, Object> m) {
        return new BatchJob(text(m.get("id")), text(m.get("name")), text(m.get("status")),
                intOf(m.get("total_items")), intOf(m.get("completed_items")), text(m.get("created_at")),
                text(m.get("message")));
    }

    private static List<UnifiedEscalation> escalations(Object value) {
        var out = new ArrayList<UnifiedEscalation>();
        for (var step : maps(value)) {
            out.add(new UnifiedEscalation(text(step.get("from")), text(step.get("to")), text(step.get("reason")),
                    text(step.get("timestamp"))));
        }
        return Collections.unmodifiableList(out);
    }

    private static String text(Object value) {
        return value instanceof String s ? s : null;
    }

    private static int intOf(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static Integer optInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOrNull(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        var out = new ArrayList<Map<String, Object>>();
        for (var item : list) if (item instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
        return Collections.unmodifiableList(out);
    }

    // ── High-level orchestrator ──────────────────────────────────────────────

    /**
     * High-level orchestrator: discovers, fetches, and optionally extracts
     * structured data from all pages on a site in a single call.
     *
     * <p>The result's {@code success} and {@code error} are the crawl's own.
     * A crawl held because the organisation's quota ran out fetches nothing:
     * the result has {@code held} true, {@code status} {@code held_quota} and
     * the API's {@code message}; wait for it later with
     * {@code webgrph().waitForCrawl(result.crawlId())}.
     * A page that cannot be fetched or extracted becomes a failed page, but
     * billing and authentication errors ({@link PaymentRequiredException},
     * {@link QuotaExceededException}, {@link BillingUnavailableException},
     * {@link NotEntitledException}, {@link AuthenticationException},
     * {@link RateLimitException}) are thrown: every later page would fail the same way.
     *
     * <pre>{@code
     * SiteCrawlResult result = client.crawl("https://example.com",
     *     CrawlOptions.builder()
     *         .maxPages(50)
     *         .extractSchema(Map.of("title", "css:h1"))
     *         .build());
     * }</pre>
     */
    public SiteCrawlResult crawl(String url, CrawlOptions opts) {
        SiteCrawlResult result = null;
        try (var events = crawlStream(url, opts)) {
            for (var it = events.iterator(); it.hasNext(); ) {
                var e = it.next();
                if ("crawl_complete".equals(e.type())) result = (SiteCrawlResult) e.metadata().get("result");
            }
        }
        return result;
    }

    /**
     * Streaming version of {@link #crawl(String, CrawlOptions)}: the events
     * are produced as the work happens (the stream is lazy): discovery runs
     * when the first event is read, then each page is fetched as its events
     * are read. Events: {@code discovery_complete}, then per page
     * {@code page_fetching} and {@code page_fetched} or {@code page_failed},
     * then {@code crawl_complete} with the {@link SiteCrawlResult} under
     * {@code metadata.result}. Errors are handled as by {@link #crawl}.
     */
    public Stream<CrawlProgressEvent> crawlStream(String url, CrawlOptions opts) {
        var it = new Iterator<CrawlProgressEvent>() {
            CrawlResult crawlResult;
            final ArrayDeque<CrawlProgressEvent> pending = new ArrayDeque<>();
            final List<CrawlPage> pages = new ArrayList<>();
            int index = 0;
            int fetched = 0;
            int failed = 0;
            boolean finished = false;

            @Override
            public boolean hasNext() {
                if (!pending.isEmpty()) return true;
                if (finished) return false;
                if (crawlResult == null) {
                    crawlResult = discover(url, opts);
                    pending.add(new CrawlProgressEvent("discovery_complete", url, null, crawlResult.totalPages(),
                            crawlResult.success(), crawlResult.error(), Map.of("pages_found", crawlResult.totalPages())));
                    return true;
                }
                int total = crawlResult.totalPages();
                if (index < crawlResult.pages().size()) {
                    var node = crawlResult.pages().get(index++);
                    int pageNum = index;
                    pending.add(new CrawlProgressEvent("page_fetching", node.url(), pageNum, total, null, null, Map.of()));
                    var page = fetchPage(node.url(), opts);
                    pages.add(page);
                    if (page.ok()) {
                        fetched++;
                        pending.add(new CrawlProgressEvent("page_fetched", node.url(), pageNum, total,
                                true, null, Map.of("tier_used", page.tierUsed())));
                    } else {
                        failed++;
                        pending.add(new CrawlProgressEvent("page_failed", node.url(), pageNum, total,
                                false, page.error(), Map.of()));
                    }
                    return true;
                }
                finished = true;
                var result = new SiteCrawlResult(crawlResult.success(), url, total, fetched, failed,
                        Collections.unmodifiableList(new ArrayList<>(pages)), crawlResult.artifactId(),
                        crawlResult.error(), crawlResult.crawlId(), crawlResult.status(),
                        crawlResult.message(), crawlResult.held());
                pending.add(new CrawlProgressEvent("crawl_complete", url, null, total, crawlResult.success(),
                        crawlResult.error(), Map.of("result", result)));
                return true;
            }

            @Override
            public CrawlProgressEvent next() {
                if (!hasNext()) throw new NoSuchElementException();
                return pending.poll();
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);
    }

    private CrawlResult discover(String url, CrawlOptions opts) {
        var o = opts != null ? opts : CrawlOptions.builder().build();
        return webgrph.crawl(url, o);
    }

    /** Errors that would fail every later page too: thrown, not recorded per page. */
    static boolean isFatal(Exception e) {
        return e instanceof PaymentRequiredException || e instanceof QuotaExceededException
                || e instanceof BillingUnavailableException || e instanceof NotEntitledException
                || e instanceof AuthenticationException || e instanceof RateLimitException;
    }

    /** Fetch one crawled page and, if a schema is given, extract it; other failures become a failed page. */
    private CrawlPage fetchPage(String pageUrl, CrawlOptions opts) {
        FetchResult fetchResult;
        try {
            fetchResult = evadr.fetch(pageUrl, FetchOptions.builder()
                    .sessionArtifactId(opts != null ? opts.sessionArtifactId() : null).build());
        } catch (RuntimeException e) {
            if (isFatal(e)) throw e;
            return new CrawlPage(false, pageUrl, 0, 0, null, null, null, e.getMessage());
        }
        Map<String, Object> structuredData = null;
        if (opts != null && opts.extractSchema() != null && !opts.extractSchema().isEmpty() && fetchResult.success()) {
            try {
                var extraction = kolektr.extract(pageUrl, ExtractOptions.builder().schema(opts.extractSchema())
                        .fetchArtifactId(fetchResult.artifactId()).build());
                if (!extraction.records().isEmpty()) {
                    structuredData = extraction.records().get(0);
                }
            } catch (RuntimeException e) {
                if (isFatal(e)) throw e;
                // Any other extraction failure keeps the fetched page without data.
            }
        }
        return new CrawlPage(fetchResult.success(), fetchResult.url() != null ? fetchResult.url() : pageUrl,
                fetchResult.statusCode(), fetchResult.tierUsed(), fetchResult.html(), structuredData,
                fetchResult.artifactId(), fetchResult.error());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String apiKey;
        private String organizationId;
        private String baseUrl;
        private Duration timeout;
        private int maxRetries = -1;
        private HttpClient httpClient;
        private Duration pollInterval;
        private Duration pollTimeout;

        private Builder() {}

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Per-request timeout (and connect timeout). Default: 100 s, above the API's 85 s synchronous wait. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** 0 = one attempt, no retries. Default: 3. */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * First wait before polling a request the API handed off as a job
         * (HTTP 202); each later wait is 1.5 times longer, at most 15 s.
         * Default: 3 s.
         */
        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /** Limit on following a handed-off request (then JobTimeoutException). Default: 15 min. */
        public Builder pollTimeout(Duration pollTimeout) {
            this.pollTimeout = pollTimeout;
            return this;
        }

        /** Inject a custom HttpClient (useful for testing). */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Kloakd build() {
            if (apiKey == null || apiKey.isBlank())
                throw new IllegalArgumentException("apiKey is required");
            if (organizationId == null || organizationId.isBlank())
                throw new IllegalArgumentException("organizationId is required");
            return new Kloakd(this);
        }
    }
}
