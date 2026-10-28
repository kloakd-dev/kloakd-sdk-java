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
    private final String tier;

    public QuotaExceededException(String message, String upgradeUrl,
                                  double gbUsed, double gbLimit, double purchasedGbBalance) {
        this(message, upgradeUrl, gbUsed, gbLimit, purchasedGbBalance, null);
    }

    public QuotaExceededException(String message, String upgradeUrl,
                                  double gbUsed, double gbLimit, double purchasedGbBalance, String tier) {
        super(429, message, "gb_exhausted");
        this.upgradeUrl = upgradeUrl;
        this.gbUsed = gbUsed;
        this.gbLimit = gbLimit;
        this.purchasedGbBalance = purchasedGbBalance;
        this.tier = tier;
    }

    public String getUpgradeUrl() { return upgradeUrl; }
    public double getGbUsed() { return gbUsed; }
    public double getGbLimit() { return gbLimit; }
    public double getPurchasedGbBalance() { return purchasedGbBalance; }
    /** The organisation's tier ({@code free}, {@code pro}, ...), or null. */
    public String getTier() { return tier; }
}
