package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 409 {@code concurrent_limit}: every one of the
 * organisation's work slots is in use. Each metered call holds one slot
 * while it runs, and running or queued crawls and batches hold one each;
 * the plan sets how many run at once (Free 2, Starter 5, Pro 10, Pro+ 20,
 * Developer 50, Enterprise 100). Playground test keys have their own limit.
 * Not retried: wait for a running call, crawl or batch to finish.
 */
public class ConcurrencyLimitException extends ApiException {
    private final int concurrentLimit;
    private final Integer activeCrawls;
    private final Integer activeRequests;
    private final String tier;

    /** The 0.5.0 constructor (no {@code active_requests}). */
    public ConcurrencyLimitException(String message, int concurrentLimit, Integer activeCrawls, String tier) {
        this(message, concurrentLimit, activeCrawls, null, tier);
    }

    public ConcurrencyLimitException(String message, int concurrentLimit, Integer activeCrawls,
            Integer activeRequests, String tier) {
        super(409, message, "concurrent_limit");
        this.concurrentLimit = concurrentLimit;
        this.activeCrawls = activeCrawls;
        this.activeRequests = activeRequests;
        this.tier = tier;
    }

    /** The number of slots the plan has ({@code concurrent_limit}). */
    public int getConcurrentLimit() { return concurrentLimit; }

    /** Crawls and batches running or queued ({@code active_crawls}), or null (test keys do not report it). */
    public Integer getActiveCrawls() { return activeCrawls; }

    /** Other calls in flight holding a slot ({@code active_requests}), or null (test keys, or an API before 2 November 2026). */
    public Integer getActiveRequests() { return activeRequests; }

    /** The tier whose limit applied, or null (test keys). */
    public String getTier() { return tier; }
}
