package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Fetchyr.detectForms().
 *
 * @param forms list of detected forms with their field metadata
 * @param totalForms total number of forms detected on the page
 * @param error error message if detection failed, otherwise null
 */
public record FormDetectionResult(
        List<Map<String, Object>> forms,
        int totalForms,
        String error
) {}
