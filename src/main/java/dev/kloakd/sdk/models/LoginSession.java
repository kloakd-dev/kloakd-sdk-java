package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Answer of {@code fetchyr().startLoginSession} ({@code POST fetchyr/login-sessions};
 * kobe-scraper modules/fetchyr/router.py LoginSessionResponse).
 *
 * @param method            {@code interactive} or {@code cookies}
 * @param status            {@code waiting_for_login} (interactive) or {@code ready}
 * @param targetUrl         the page the session is for
 * @param discoveryId       interactive: the auth-chain discovery to follow
 *                          ({@code getAuthChainStatus}, {@code resumeAuthChainCredentials},
 *                          {@code resumeAuthChainMfa}); else null
 * @param sessionArtifactId the session, once there is one (cookies: at once); pass it as
 *                          {@code sessionArtifactId} to fetch, crawl and extract
 * @param next              the calls to make next, by name (the API's own paths)
 * @param cookiesKept       cookies: how many of the cookies were kept; else null
 * @param cookiesDropped    cookies: the names of the cookies not kept
 * @param protections       the limits the session runs under
 * @param message           the API's text
 */
public record LoginSession(
        String method,
        String status,
        String targetUrl,
        String discoveryId,
        String sessionArtifactId,
        Map<String, String> next,
        Integer cookiesKept,
        List<String> cookiesDropped,
        Map<String, Object> protections,
        String message
) {}
