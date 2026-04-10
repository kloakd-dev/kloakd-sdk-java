package dev.kloakd.sdk.errors;

/**
 * Base exception for all KLOAKD SDK errors.
 * Every exception carries a statusCode and message.
 */
public class KloakdException extends RuntimeException {
    private final int statusCode;

    public KloakdException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
