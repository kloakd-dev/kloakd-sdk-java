package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 403 {@code not_entitled} (the plan does not include the
 * module) and {@code tier_access_denied} (the tier does not include the
 * feature). Any other 403 is a {@link ForbiddenException}.
 */
public class NotEntitledException extends ForbiddenException {
    private final String module;
    private final String upgradeUrl;
    private final String feature;
    private final String currentTier;
    private final String requiredTier;

    public NotEntitledException(String message, String module, String upgradeUrl) {
        this(message, module, upgradeUrl, "not_entitled", null, null, null);
    }

    public NotEntitledException(String message, String module, String upgradeUrl, String code,
                                String feature, String currentTier, String requiredTier) {
        super(message, code);
        this.module = module;
        this.upgradeUrl = upgradeUrl;
        this.feature = feature;
        this.currentTier = currentTier;
        this.requiredTier = requiredTier;
    }

    /** The module refused ({@code not_entitled}), or null. */
    public String getModule() { return module; }
    public String getUpgradeUrl() { return upgradeUrl; }
    /** The feature refused ({@code tier_access_denied}), or null. */
    public String getFeature() { return feature; }
    /** The organisation's tier ({@code tier_access_denied}), or null. */
    public String getCurrentTier() { return currentTier; }
    /** The tier the feature needs ({@code tier_access_denied}), or null. */
    public String getRequiredTier() { return requiredTier; }
}
