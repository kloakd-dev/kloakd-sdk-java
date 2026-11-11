package dev.kloakd.sdk.models;

import java.util.List;
import java.util.Map;

/**
 * Result of Nexus.verify() (modules/nexus/router.py VerifyResponse).
 *
 * @param strategyId           the strategy checked
 * @param verified             whether it passed
 * @param safetyScore          0 to 1
 * @param violations           what it broke
 * @param status               {@code verified} or {@code unsafe}, or null
 * @param riskScore            0 to 1
 * @param safetyChecks         each check: {@code {property, passed, details}}
 * @param verificationResultId the stored result's id, or null
 * @param durationMs           verification time
 */
public record NexusVerifyResult(
        String strategyId,
        boolean verified,
        double safetyScore,
        List<String> violations,
        String status,
        double riskScore,
        List<Map<String, Object>> safetyChecks,
        String verificationResultId,
        int durationMs
) {
    /** The 0.6.0 components only. */
    public NexusVerifyResult(String strategyId, boolean verified, double safetyScore, List<String> violations) {
        this(strategyId, verified, safetyScore, violations, null, 0.0, List.of(), null, 0);
    }
}
