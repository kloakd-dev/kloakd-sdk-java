package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Progress of a running unified scrape (kobe-scraper
 * api/v1/endpoints/unified_scraping.py ProgressDetail).
 *
 * @param percentage     0 to 100
 * @param currentStep    what the job is doing
 * @param itemsFound     or null
 * @param itemsTarget    or null
 * @param partialResults the first few results, or null
 */
public record UnifiedScrapeProgress(
        int percentage,
        String currentStep,
        Integer itemsFound,
        Integer itemsTarget,
        List<Map<String, Object>> partialResults
) {}
