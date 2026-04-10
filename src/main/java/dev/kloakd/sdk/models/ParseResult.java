package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Result of Parlyr.parse().
 *
 * @param intent detected intent classification
 * @param confidence intent confidence score between 0.0 and 1.0
 * @param tier processing tier used
 * @param source input source type (e.g. text, url, audio)
 * @param entities extracted named entities
 * @param requiresAction whether a follow-up action is needed
 * @param clarificationNeeded clarification prompt if more info is required, otherwise null
 * @param reasoning explanation of the intent classification
 * @param detectedUrl URL detected in the input, if any
 */
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
