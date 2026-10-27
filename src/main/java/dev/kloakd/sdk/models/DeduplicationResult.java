package dev.kloakd.sdk.models;

/** Result of Fetchyr.checkDuplicate(). */
public record DeduplicationResult(
        boolean isDuplicate,
        String originalUrl,
        double similarityScore,
        String matchType
) {}
