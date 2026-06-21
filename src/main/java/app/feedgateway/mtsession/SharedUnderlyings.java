package app.feedgateway.mtsession;

import java.util.Locale;
import java.util.Set;

/**
 * The set of underlying symbols that are SHARED across tenant modes (OE-DDD-001 §8.6). A shared underlying
 * (today: VIX) is routed under {@link MarketDataSource#SHARED} so it reaches every session regardless of the
 * session's selected source, fixing the inconsistency where VIX was bound to one source and other-source
 * sessions never received it.
 */
public final class SharedUnderlyings {

    public static final String VIX = "VIX";

    private static final Set<String> SHARED = Set.of(VIX);

    private SharedUnderlyings() {
    }

    public static boolean isShared(String underlying) {
        return underlying != null && SHARED.contains(underlying.trim().toUpperCase(Locale.ROOT));
    }

    /** The routing source for an underlying: {@link MarketDataSource#SHARED} for shared symbols, else {@code source}. */
    public static MarketDataSource routingSource(String underlying, MarketDataSource source) {
        return isShared(underlying) ? MarketDataSource.SHARED : source;
    }
}
