package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 429 {@code gb_exhausted}: the plan's GB allocation and this
 * period's top-ups are used up. Not retried: waiting does not clear it.
 */
public class QuotaExceededException extends KloakdException {
    private final String upgradeUrl;
    private final double gbUsed;
    private final double gbLimit;
    private final double purchasedGbBalance;

    public QuotaExceededException(String message, String upgradeUrl,
                                  double gbUsed, double gbLimit, double purchasedGbBalance) {
        super(429, message, "gb_exhausted");
        this.upgradeUrl = upgradeUrl;
        this.gbUsed = gbUsed;
        this.gbLimit = gbLimit;
        this.purchasedGbBalance = purchasedGbBalance;
    }

    public String getUpgradeUrl() { return upgradeUrl; }
    public double getGbUsed() { return gbUsed; }
    public double getGbLimit() { return gbLimit; }
    public double getPurchasedGbBalance() { return purchasedGbBalance; }
}
