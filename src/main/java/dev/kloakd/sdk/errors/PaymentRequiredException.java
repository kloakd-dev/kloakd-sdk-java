package dev.kloakd.sdk.errors;

/**
 * Thrown for HTTP 402 {@code payment_required}: the organisation's paid plan
 * has a failed payment; new billable work is refused until it is paid.
 */
public class PaymentRequiredException extends KloakdException {
    private final String upgradeUrl;

    public PaymentRequiredException(String message, String upgradeUrl) {
        super(402, message, "payment_required");
        this.upgradeUrl = upgradeUrl;
    }

    /** Where to update the payment method, or null. */
    public String getUpgradeUrl() { return upgradeUrl; }
}
