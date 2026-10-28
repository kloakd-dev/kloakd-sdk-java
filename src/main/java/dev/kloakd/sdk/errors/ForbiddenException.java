package dev.kloakd.sdk.errors;

/**
 * Thrown for an HTTP 403 that is not a plan restriction: the key or user
 * may not do this (for example another organisation's resource). Plan
 * restrictions ({@code not_entitled}, {@code tier_access_denied}) are
 * {@link NotEntitledException}.
 */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message, String code) {
        super(403, message, code);
    }
}
