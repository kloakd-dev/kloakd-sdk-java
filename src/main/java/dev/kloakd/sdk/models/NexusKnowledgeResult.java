package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Nexus.knowledge(). */
public record NexusKnowledgeResult(
        List<Map<String, Object>> learnedConcepts,
        List<Map<String, Object>> learnedPatterns,
        int durationMs,
        String error
) {}
