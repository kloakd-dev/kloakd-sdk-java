package dev.kloakd.sdk.errors;

/**
 * Base exception for all KLOAKD SDK errors.
 * Every exception carries a statusCode, a message and, when the API sent
 * one, its error word ({@code detail.error}, e.g. {@code gb_exhausted}).
 */
public class KloakdException extends RuntimeException {
    private final int statusCode;
    private final String code;

    public KloakdException(int statusCode, String message) {
        this(statusCode, message, null);
    }

    public KloakdException(int statusCode, String message, String code) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** The API's error word, or null. */
    public String getCode() {
        return code;
    }
}
