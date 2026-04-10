package dev.kloakd.sdk.models;

import java.util.Map;

/** Result of Parlyr.chat() — full conversation turn. */
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
