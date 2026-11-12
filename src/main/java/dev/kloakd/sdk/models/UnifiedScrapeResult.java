package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * The answer of {@code client.unifiedScrape} (kobe-scraper
 * api/v1/endpoints/unified_scraping.py UnifiedScrapeResponse).
 *
 * @param success        the API's flag
 * @param jobId          the scrape's job: read it with {@code client.getUnifiedScrape(jobId)}
 * @param level          {@code level1}, {@code level2}, or {@code preflight} when a login page
 *                       was found before any scrape
 * @param status         the job's status
 * @param escalationPath the levels tried, in order
 * @param message        the API's text
 * @param metadata       what the route adds, or null
 * @param authRequired   true when the URL is behind a login: nothing was scraped,
 *                       {@code status} is {@code auth_required} and {@code discoveryId} names
 *                       the auth-chain discovery ({@code fetchyr().getAuthChainStatus});
 *                       {@code jobId} then names no stored job
 * @param discoveryId    the auth-chain discovery, or null
 */
public record UnifiedScrapeResult(
        boolean success,
        String jobId,
        String level,
        String status,
        List<UnifiedEscalation> escalationPath,
        String message,
        Map<String, Object> metadata,
        boolean authRequired,
        String discoveryId
) {}
