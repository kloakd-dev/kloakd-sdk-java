package dev.kloakd.sdk.errors;

/** Thrown for any other 4xx/5xx response not covered by a specific subclass. */
public class ApiException extends KloakdException {
    public ApiException(int statusCode, String message) {
        super(statusCode, message);
    }
}
