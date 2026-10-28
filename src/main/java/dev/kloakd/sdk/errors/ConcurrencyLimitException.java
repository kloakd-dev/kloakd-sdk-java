package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 409 {@code concurrent_limit}: the organisation (or test
 * key) already runs as many crawls or jobs as its tier allows. Not retried;
 * wait for a running one to finish.
 */
public class ConcurrencyLimitException extends ApiException {
    private final int concurrentLimit;
    private final Integer activeCrawls;
    private final String tier;

    public ConcurrencyLimitException(String message, int concurrentLimit, Integer activeCrawls, String tier) {
        super(409, message, "concurrent_limit");
        this.concurrentLimit = concurrentLimit;
        this.activeCrawls = activeCrawls;
        this.tier = tier;
    }

    /** The limit that applies ({@code concurrent_limit}). */
    public int getConcurrentLimit() { return concurrentLimit; }

    /** Crawls running now ({@code active_crawls}), or null (test keys do not report it). */
    public Integer getActiveCrawls() { return activeCrawls; }

    /** The tier whose limit applied, or null (test keys). */
    public String getTier() { return tier; }
}
