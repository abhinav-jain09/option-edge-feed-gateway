package app.feedgateway.mtsession;

import java.util.Set;

/**
 * Coarse entitlement checks (OE-DDD-001 §7.2, FR-12), applied both when a selection is made and
 * again at the router (defense in depth). IBKR mode requires {@code ibkr-user}; order staging
 * requires {@code trader}; DATABENTO is available to any approved user.
 */
public final class EntitlementPolicy {

    public static final String IBKR_USER = "ibkr-user";
    public static final String TRADER = "trader";

    private EntitlementPolicy() {
    }

    public static boolean canSelect(MarketDataSource source, Set<String> entitlements) {
        return switch (source) {
            case IBKR -> entitlements != null && entitlements.contains(IBKR_USER);
            case DATABENTO -> true;
            // SHARED underlyings (VIX) are never a primary selection — a session selects IBKR or DATABENTO.
            case SHARED -> false;
        };
    }

    /** Router-side double check: never deliver a source's data to a session not entitled to it. */
    public static boolean canReceive(MarketDataSource source, Set<String> entitlements) {
        // SHARED underlyings (VIX) are delivered to every session regardless of its selected-source entitlement.
        if (source == MarketDataSource.SHARED) {
            return true;
        }
        return canSelect(source, entitlements);
    }

    public static boolean canTrade(Set<String> entitlements) {
        return entitlements != null && entitlements.contains(TRADER);
    }
}
