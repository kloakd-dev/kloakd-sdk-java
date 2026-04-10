package dev.kloakd.sdk.models;

import java.util.Map;

/** Result of Parlyr.parse(). */
public record ParseResult(
        String intent,
        double confidence,
        int tier,
        String source,
        Map<String, Object> entities,
        boolean requiresAction,
        String clarificationNeeded,
        String reasoning,
        String detectedUrl
) {}
