package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Fetchyr.detectForms(). */
public record FormDetectionResult(
        String url,
        int formsFound,
        boolean loginFormDetected,
        List<Map<String, Object>> fields
) {}
