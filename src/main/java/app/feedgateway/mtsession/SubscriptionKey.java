package app.feedgateway.mtsession;

import java.util.Objects;

/**
 * Identity of an upstream contract subscription: {@code source|symbol|expiry}
 * (OE-DDD-001 §8.6 Subscription Key). Strike-window independent — differing strike windows on the
 * same key share one upstream subscription. Components are stored already normalized.
 */
public record SubscriptionKey(MarketDataSource source, String symbol, String expiry) {

    public SubscriptionKey {
        Objects.requireNonNull(source, "source");
        symbol = Normalization.symbol(symbol);
        expiry = Normalization.expiry(expiry);
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (expiry.isEmpty()) {
            throw new IllegalArgumentException("expiry must not be blank");
        }
    }

    @Override
    public String toString() {
        return source + "|" + symbol + "|" + expiry;
    }
}
