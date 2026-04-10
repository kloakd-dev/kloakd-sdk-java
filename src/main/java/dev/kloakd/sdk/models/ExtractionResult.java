package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Kolektr.page() and Kolektr.extractHtml().
 *
 * @param success whether the extraction succeeded
 * @param url the target URL that was extracted
 * @param method extraction method used (e.g. css, xpath, ai)
 * @param records list of extracted structured records
 * @param totalRecords number of records in this response
 * @param pagesScraped number of pages scraped to produce these records
 * @param hasMore whether additional records are available
 * @param total total record count across all pages
 * @param artifactId storage artifact ID for the extraction
 * @param jobId async job ID if extraction was queued
 * @param error error message if success is false, otherwise null
 */
public record ExtractionResult(
        boolean success,
        String url,
        String method,
        List<Map<String, Object>> records,
        int totalRecords,
        int pagesScraped,
        boolean hasMore,
        int total,
        String artifactId,
        String jobId,
        String error
) {}
