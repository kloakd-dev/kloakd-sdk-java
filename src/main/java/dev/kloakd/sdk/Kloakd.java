package dev.kloakd.sdk;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.modules.*;
import dev.kloakd.sdk.models.*;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

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
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    static final int DEFAULT_MAX_RETRIES = 3;

    private final EvadrNamespace evadr;
    private final WebgrphNamespace webgrph;
    private final SkanyrNamespace skanyr;
    private final NexusNamespace nexus;
    private final ParlyrNamespace parlyr;
    private final FetchyrNamespace fetchyr;
    private final KolektrNamespace kolektr;

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

    // ── High-level orchestrator ──────────────────────────────────────────────

    /**
     * High-level orchestrator: discovers, fetches, and optionally extracts
     * structured data from all pages on a site in a single call.
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
        var crawlResult = discover(url, opts);
        var pages = new ArrayList<CrawlPage>();
        int fetched = 0;
        int failed = 0;
        for (var node : crawlResult.pages()) {
            var page = fetchPage(node.url(), opts);
            pages.add(page);
            if (page.ok()) fetched++; else failed++;
        }
        return new SiteCrawlResult(
                true, url, crawlResult.totalPages(),
                fetched, failed, Collections.unmodifiableList(pages),
                crawlResult.artifactId(), null
        );
    }

    /**
     * Streaming version of {@link #crawl(String, CrawlOptions)}. Returns a
     * stream of {@link CrawlProgressEvent} values as pages are processed.
     */
    public Stream<CrawlProgressEvent> crawlStream(String url, CrawlOptions opts) {
        var crawlResult = discover(url, opts);
        int total = crawlResult.totalPages();
        var events = new ArrayList<CrawlProgressEvent>();
        events.add(new CrawlProgressEvent("discovery_complete", url, null, total, null, null,
                Map.of("pages_found", total)));

        int fetched = 0;
        int failed = 0;
        var pages = new ArrayList<CrawlPage>();
        for (int i = 0; i < crawlResult.pages().size(); i++) {
            var node = crawlResult.pages().get(i);
            int pageNum = i + 1;
            events.add(new CrawlProgressEvent("page_fetching", node.url(), pageNum, total, null, null, Map.of()));
            var page = fetchPage(node.url(), opts);
            pages.add(page);
            if (page.ok()) {
                fetched++;
                events.add(new CrawlProgressEvent("page_fetched", node.url(), pageNum, total,
                        true, null, Map.of("tier_used", page.tierUsed())));
            } else {
                failed++;
                events.add(new CrawlProgressEvent("page_failed", node.url(), pageNum, total,
                        false, page.error(), Map.of()));
            }
        }

        var result = new SiteCrawlResult(
                true, url, total, fetched, failed,
                Collections.unmodifiableList(pages), crawlResult.artifactId(), null
        );
        events.add(new CrawlProgressEvent("crawl_complete", url, null, total, true, null,
                Map.of("result", result)));
        return events.stream();
    }

    private CrawlResult discover(String url, CrawlOptions opts) {
        int maxDepth = opts.maxDepth() > 0 ? opts.maxDepth() : 3;
        int maxPages = opts.maxPages() > 0 ? opts.maxPages() : 100;
        return webgrph.crawl(url, maxDepth, maxPages, opts.includeExternalLinks(), opts.sessionArtifactId());
    }

    /** Fetch one crawled page and, if a schema is given, extract it; failures become a failed page. */
    private CrawlPage fetchPage(String pageUrl, CrawlOptions opts) {
        FetchResult fetchResult;
        try {
            fetchResult = evadr.fetch(pageUrl, false, false, opts.sessionArtifactId());
        } catch (Exception e) {
            return new CrawlPage(false, pageUrl, 0, 0, null, null, null, e.getMessage());
        }
        Map<String, Object> structuredData = null;
        if (opts.extractSchema() != null && !opts.extractSchema().isEmpty() && fetchResult.success()) {
            try {
                @SuppressWarnings("unchecked")
                var schema = (Map<String, Object>) (Map<?, ?>) opts.extractSchema();
                var extraction = kolektr.extract(pageUrl, schema, fetchResult.artifactId(), null, null);
                if (!extraction.records().isEmpty()) {
                    structuredData = extraction.records().get(0);
                }
            } catch (Exception ignored) {
                // Extraction failure is non-fatal
            }
        }
        return new CrawlPage(fetchResult.success(), fetchResult.url(), fetchResult.statusCode(),
                fetchResult.tierUsed(), fetchResult.html(), structuredData,
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
