package dev.kloakd.sdk.errors;

/** Thrown for HTTP 502 — upstream site fetch failed. */
public class UpstreamException extends KloakdException {
    public UpstreamException(String message) {
        super(502, message);
    }
}
