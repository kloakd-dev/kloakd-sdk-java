package dev.kloakd.sdk.models;

import java.util.List;

/**
 * Result of Evadr.analyze().
 *
 * @param blocked whether the target page is behind an anti-bot system
 * @param vendor name of the detected vendor (e.g. Cloudflare, Akamai)
 * @param confidence detection confidence score between 0.0 and 1.0
 * @param recommendedActions list of suggested bypass actions
 */
public record AnalyzeResult(
        boolean blocked,
        String vendor,
        double confidence,
        List<String> recommendedActions
) {}
