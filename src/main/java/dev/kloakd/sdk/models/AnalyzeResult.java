package dev.kloakd.sdk.models;

import java.util.List;

/** Result of Evadr.analyze(). */
public record AnalyzeResult(
        boolean blocked,
        String vendor,
        double confidence,
        List<String> recommendedActions
) {}
