package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.*;
import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.modules.*;
import dev.kloakd.sdk.models.*;

import java.net.http.HttpClient;
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

    // ── High-level orchestrator ──────────────────────────────────────────────

    /**
     * High-level orchestrator: discovers, fetches, and optionally extracts
     * structured data from all pages on a site in a single call.
     *
     * <p>The result's {@code success} and {@code error} are the crawl's own.
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
                        crawlResult.error());
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
