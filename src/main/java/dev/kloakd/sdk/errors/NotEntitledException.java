package dev.kloakd.sdk.errors;

/** Thrown for HTTP 403 — tenant not on required plan. */
public class NotEntitledException extends KloakdException {
    private final String module;
    private final String upgradeUrl;

    public NotEntitledException(String message, String module, String upgradeUrl) {
        super(403, message);
        this.module = module;
        this.upgradeUrl = upgradeUrl;
    }

    public String getModule() { return module; }
    public String getUpgradeUrl() { return upgradeUrl; }
}
