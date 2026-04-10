package dev.kloakd.sdk.models;

import java.util.Map;

/** A discovered API endpoint. */
public record ApiEndpoint(
        String url,
        String method,
        String apiType,
        double confidence,
        Map<String, Object> parameters
) {}
