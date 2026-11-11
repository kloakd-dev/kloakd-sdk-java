package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Options for {@code fetchyr().startLoginSession} (kobe-scraper
 * modules/fetchyr/router.py LoginSessionRequest). Only what is set is sent.
 *
 * @param method    {@code interactive} (the API's default: it opens the site's login in a
 *                  browser and asks for the credentials through the auth-chain calls) or
 *                  {@code cookies} (a session you already have in your own browser); null is not sent
 * @param cookies   for {@code cookies}: your browser's cookies for the site, each a map of
 *                  {@code name}, {@code value} and optionally {@code domain}, {@code path},
 *                  {@code expires} or {@code expirationDate} (epoch seconds), {@code secure},
 *                  {@code httpOnly}, {@code sameSite}, as a browser exports them
 * @param userAgent for {@code cookies}: that browser's {@code navigator.userAgent} (required by the API)
 * @param country   two-letter country the session's exit is in, or null
 */
public record LoginSessionOptions(
        String method,
        List<Map<String, Object>> cookies,
        String userAgent,
        String country
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String method;
        private List<Map<String, Object>> cookies;
        private String userAgent;
        private String country;

        private Builder() {}

        public Builder method(String method) { this.method = method; return this; }
        public Builder cookies(List<Map<String, Object>> cookies) { this.cookies = cookies; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder country(String country) { this.country = country; return this; }

        public LoginSessionOptions build() {
            return new LoginSessionOptions(method, cookies, userAgent, country);
        }
    }
}
