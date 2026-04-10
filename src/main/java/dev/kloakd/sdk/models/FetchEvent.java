package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * SSE event yielded by Evadr.fetchStream().
 *
 * @param type event type (e.g. tier_attempt, done, error)
 * @param tier bypass tier being attempted
 * @param vendor anti-bot vendor detected, or null
 * @param metadata additional event metadata
 */
public record FetchEvent(
        String type,
        Integer tier,
        String vendor,
        Map<String, Object> metadata
) {}
