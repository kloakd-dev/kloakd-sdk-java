package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Kolektr.page() and Kolektr.extractHtml(). */
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
