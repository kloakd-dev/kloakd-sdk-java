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
                builder.httpClient);

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
        int maxDepth = opts.maxDepth() > 0 ? opts.maxDepth() : 3;
        int maxPages = opts.maxPages() > 0 ? opts.maxPages() : 100;

        var crawlResult = webgrph.crawl(url, maxDepth, maxPages,
                opts.includeExternalLinks(), opts.sessionArtifactId());

        var pages = new ArrayList<CrawlPage>();
        int fetched = 0;
        int failed = 0;

        for (var node : crawlResult.pages()) {
            try {
                var fetchResult = evadr.fetch(node.url(),
                        false, false, opts.sessionArtifactId());

                Map<String, Object> structuredData = null;
                if (opts.extractSchema() != null && !opts.extractSchema().isEmpty()
                        && fetchResult.success()) {
                    try {
                        @SuppressWarnings("unchecked")
                        var schema = (Map<String, Object>) (Map<?, ?>) opts.extractSchema();
                        var extraction = kolektr.extract(node.url(), schema,
                                fetchResult.artifactId(), null, null);
                        if (!extraction.records().isEmpty()) {
                            structuredData = extraction.records().get(0);
                        }
                    } catch (Exception ignored) {
                        // Extraction failure is non-fatal
                    }
                }

                var page = new CrawlPage(
                        fetchResult.success(),
                        fetchResult.url(),
                        fetchResult.statusCode(),
                        fetchResult.tierUsed(),
                        fetchResult.html(),
                        structuredData,
                        fetchResult.artifactId(),
                        fetchResult.error()
                );
                pages.add(page);
                if (page.ok()) fetched++; else failed++;
            } catch (Exception e) {
                pages.add(new CrawlPage(false, node.url(), 0, 0, null, null, null, e.getMessage()));
                failed++;
            }
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
        int maxDepth = opts.maxDepth() > 0 ? opts.maxDepth() : 3;
        int maxPages = opts.maxPages() > 0 ? opts.maxPages() : 100;

        var crawlResult = webgrph.crawl(url, maxDepth, maxPages,
                opts.includeExternalLinks(), opts.sessionArtifactId());

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

            try {
                var fetchResult = evadr.fetch(node.url(),
                        false, false, opts.sessionArtifactId());

                Map<String, Object> structuredData = null;
                if (opts.extractSchema() != null && !opts.extractSchema().isEmpty()
                        && fetchResult.success()) {
                    try {
                        @SuppressWarnings("unchecked")
                        var schema = (Map<String, Object>) (Map<?, ?>) opts.extractSchema();
                        var extraction = kolektr.extract(node.url(), schema,
                                fetchResult.artifactId(), null, null);
                        if (!extraction.records().isEmpty()) {
                            structuredData = extraction.records().get(0);
                        }
                    } catch (Exception ignored) {
                        // Extraction failure is non-fatal
                    }
                }

                var page = new CrawlPage(
                        fetchResult.success(),
                        fetchResult.url(),
                        fetchResult.statusCode(),
                        fetchResult.tierUsed(),
                        fetchResult.html(),
                        structuredData,
                        fetchResult.artifactId(),
                        fetchResult.error()
                );
                pages.add(page);

                if (page.ok()) {
                    fetched++;
                    events.add(new CrawlProgressEvent("page_fetched", node.url(), pageNum, total,
                            true, null, Map.of("tier_used", fetchResult.tierUsed())));
                } else {
                    failed++;
                    events.add(new CrawlProgressEvent("page_failed", node.url(), pageNum, total,
                            false, fetchResult.error(), Map.of()));
                }
            } catch (Exception e) {
                pages.add(new CrawlPage(false, node.url(), 0, 0, null, null, null, e.getMessage()));
                failed++;
                events.add(new CrawlProgressEvent("page_failed", node.url(), pageNum, total,
                        false, e.getMessage(), Map.of()));
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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String apiKey;
        private String organizationId;
        private String baseUrl;
        private Duration timeout;
        private int maxRetries = -1;
        private HttpClient httpClient;

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
