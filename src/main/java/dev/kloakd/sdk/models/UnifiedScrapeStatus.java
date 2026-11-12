package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * The state of a unified scrape job ({@code client.getUnifiedScrape};
 * kobe-scraper api/v1/endpoints/unified_scraping.py JobStatusResponse).
 *
 * @param id             the job's id
 * @param status         the job's status
 * @param level          {@code level1} or {@code level2}
 * @param progress       0 to 100, or null
 * @param progressDetail set while the job is running; else null
 * @param createdAt      ISO time
 * @param updatedAt      ISO time, or null
 * @param completedAt    ISO time, or null
 * @param error          the job's error, or null
 * @param result         the job's stored result document; null until there is one
 * @param escalationPath the levels tried, in order
 */
public record UnifiedScrapeStatus(
        String id,
        String status,
        String level,
        Integer progress,
        UnifiedScrapeProgress progressDetail,
        String createdAt,
        String updatedAt,
        String completedAt,
        String error,
        Map<String, Object> result,
        List<UnifiedEscalation> escalationPath
) {}
