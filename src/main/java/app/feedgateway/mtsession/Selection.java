package app.feedgateway.mtsession;

import java.util.Objects;

/**
 * What a user is viewing: {@code {source, symbol, expiry, strikeWindow}} (OE-DDD-001 §6.1).
 * Symbol/expiry are normalized on construction so a Selection and a record derive identical keys.
 */
public record Selection(MarketDataSource source, String symbol, String expiry, StrikeWindow strikeWindow) {

    public Selection {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(strikeWindow, "strikeWindow");
        symbol = Normalization.symbol(symbol);
        expiry = Normalization.expiry(expiry);
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (expiry.isEmpty()) {
            throw new IllegalArgumentException("expiry must not be blank");
        }
    }

    /** The contract subscription identity this selection depends on. */
    public SubscriptionKey subscriptionKey() {
        return new SubscriptionKey(source, symbol, expiry);
    }

    /** The underlying instrument for this symbol (identity mapping; specialised in the engine if needed). */
    public String underlying() {
        return symbol;
    }
}
