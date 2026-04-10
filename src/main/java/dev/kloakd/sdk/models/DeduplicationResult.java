package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Fetchyr.checkDuplicates().
 *
 * @param uniqueRecords list of records with duplicates removed
 * @param duplicateCount number of duplicate records detected
 * @param totalInput total number of input records submitted
 * @param error error message if the operation failed, otherwise null
 */
public record DeduplicationResult(
        List<Map<String, Object>> uniqueRecords,
        int duplicateCount,
        int totalInput,
        String error
) {}
