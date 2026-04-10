package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Nexus.knowledge().
 *
 * @param learnedConcepts list of domain concepts learned from the target
 * @param learnedPatterns list of structural patterns learned from the target
 * @param durationMs time taken to complete knowledge extraction in milliseconds
 * @param error error message if extraction failed, otherwise null
 */
public record NexusKnowledgeResult(
        List<Map<String, Object>> learnedConcepts,
        List<Map<String, Object>> learnedPatterns,
        int durationMs,
        String error
) {}
