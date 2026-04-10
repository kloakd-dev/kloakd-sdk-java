package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Result of Parlyr.chat() — full conversation turn.
 *
 * @param sessionId conversation session identifier
 * @param intent detected intent for the user message
 * @param confidence intent confidence score between 0.0 and 1.0
 * @param tier processing tier used
 * @param response assistant response text
 * @param entities extracted named entities from the message
 * @param requiresAction whether a follow-up action is needed
 * @param clarificationNeeded clarification prompt if more info is required, otherwise null
 */
public record ChatTurn(
        String sessionId,
        String intent,
        double confidence,
        int tier,
        String response,
        Map<String, Object> entities,
        boolean requiresAction,
        String clarificationNeeded
) {}
