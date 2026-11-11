package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * A stored login session's state ({@code fetchyr().getSession},
 * {@code resumeSession}; kobe-scraper modules/fetchyr/router.py
 * SessionStatusResponse). No cookie values are returned.
 *
 * @param artifactId       the session's id
 * @param status           {@code active}, {@code paused}, {@code EXPIRED} or {@code INVALIDATED}
 * @param targetDomain     the site
 * @param authMethod       how the session was made
 * @param createdAt        ISO time
 * @param expiresAt        ISO time
 * @param lastUsedAt       ISO time
 * @param pausedAt         ISO time, when paused
 * @param pauseReason      {@code logged_out}, {@code rate_limited} or {@code challenged}
 * @param pauseMessage     the API's text about the pause
 * @param identity         what the session presents to the site
 * @param requestsThisHour requests made with it this hour, or null
 * @param protections      the limits the session runs under
 */
public record SessionStatus(
        String artifactId,
        String status,
        String targetDomain,
        String authMethod,
        String createdAt,
        String expiresAt,
        String lastUsedAt,
        String pausedAt,
        String pauseReason,
        String pauseMessage,
        Map<String, Object> identity,
        Integer requestsThisHour,
        Map<String, Object> protections
) {}
