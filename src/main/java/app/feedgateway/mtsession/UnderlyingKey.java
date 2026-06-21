package app.feedgateway.mtsession;

import java.util.Locale;
import java.util.Objects;

/**
 * Routing identity for underlying-scoped events: {@code source|underlying}
 * (OE-DDD-001 §8.6). Underlying events (VIX, ES/index, SPX price) are not contract-scoped.
 */
public record UnderlyingKey(MarketDataSource source, String underlying) {

    public UnderlyingKey {
        Objects.requireNonNull(source, "source");
        underlying = underlying == null ? "" : underlying.trim().toUpperCase(Locale.ROOT);
        if (underlying.isEmpty()) {
            throw new IllegalArgumentException("underlying must not be blank");
        }
    }

    @Override
    public String toString() {
        return source + "|" + underlying;
    }
}
