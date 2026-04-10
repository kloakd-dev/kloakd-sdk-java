package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Fetchyr.checkDuplicates(). */
public record DeduplicationResult(
        List<Map<String, Object>> uniqueRecords,
        int duplicateCount,
        int totalInput,
        String error
) {}
