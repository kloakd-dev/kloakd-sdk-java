package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Kolektr.extract() and Kolektr.extractHtml().
 *
 * @param success whether the extraction succeeded
 * @param url the target URL that was extracted
 * @param method extraction method used (e.g. css, xpath, ai)
 * @param records list of extracted structured records
 * @param totalRecords number of records in this response
 * @param pagesScraped number of pages scraped to produce these records
 * @param artifactId storage artifact ID for the extraction, or null
 * @param jobId async job ID if extraction was queued, or null
 * @param error error message if success is false, otherwise null
 * @param authRequired true when the target is a login page; the API then
 *                     started an auth-chain discovery ({@code discoveryId})
 * @param discoveryId the auth-chain discovery, or null
 */
public record ExtractionResult(
        boolean success,
        String url,
        String method,
        List<Map<String, Object>> records,
        int totalRecords,
        int pagesScraped,
        String artifactId,
        String jobId,
        String error,
        boolean authRequired,
        String discoveryId
) {}
