package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * A running crawl's time against its budget, read from a status answer:
 * {@code GET webgrph/crawl/{id}} or {@code GET webgrph/crawl-extract/{job_id}}
 * ({@code time_budget_s}, {@code elapsed_s}, {@code est_remaining_s}; contract
 * of 14 Nov 2026, section 2). Each is null until the crawl reports it.
 *
 * @param timeBudgetS   the crawl's time budget in seconds, or null
 * @param elapsedS      seconds used so far, or null
 * @param estRemainingS estimated seconds left, or null
 */
public record CrawlTiming(Integer timeBudgetS, Double elapsedS, Double estRemainingS) {

    /** Read the three fields from a status map (for example {@code webgrph().getCrawlExtract(jobId)}). */
    public static CrawlTiming from(Map<String, Object> status) {
        if (status == null) return new CrawlTiming(null, null, null);
        return new CrawlTiming(
                status.get("time_budget_s") instanceof Number n ? n.intValue() : null,
                status.get("elapsed_s") instanceof Number n ? n.doubleValue() : null,
                status.get("est_remaining_s") instanceof Number n ? n.doubleValue() : null);
    }
}
