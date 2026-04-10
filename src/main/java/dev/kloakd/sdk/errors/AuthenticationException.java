package dev.kloakd.sdk.errors;

/** Thrown for HTTP 401 — invalid or expired API key. */
public class AuthenticationException extends KloakdException {
    public AuthenticationException(String message) {
        super(401, message);
    }
}
