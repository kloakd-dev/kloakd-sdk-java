package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/** Result of Fetchyr.detectForms(). */
public record FormDetectionResult(
        List<Map<String, Object>> forms,
        int totalForms,
        String error
) {}
