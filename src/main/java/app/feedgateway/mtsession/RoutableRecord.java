package app.feedgateway.mtsession;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A market-data record as seen by the router, decoupled from Kafka/Avro/JSON wire formats so the
 * routing core is pure and unit-testable (OE-DDD-001 §8.7).
 *
 * @param bindingSource          authoritative source from the topic binding the record arrived on
 * @param eventType              event classification (contract vs underlying)
 * @param symbol                 payload symbol (raw; normalized during derivation)
 * @param expiry                 payload expiry (raw; normalized during derivation)
 * @param strike                 strike for contract events, used by the per-user delivery filter
 * @param selectionEpoch         monotonic epoch carried by the record for the per-session barrier
 * @param payloadMarketDataSource optional payload {@code marketDataSource} (cross-checked vs binding)
 * @param payloadSource          optional payload {@code source} (e.g. {@code UNUSUAL_WHALES})
 */
public record RoutableRecord(
        MarketDataSource bindingSource,
        EventType eventType,
        String symbol,
        String expiry,
        OptionalDouble strike,
        long selectionEpoch,
        String payloadMarketDataSource,
        String payloadSource) {

    public RoutableRecord {
        Objects.requireNonNull(bindingSource, "bindingSource");
        Objects.requireNonNull(eventType, "eventType");
        strike = strike == null ? OptionalDouble.empty() : strike;
    }

    /** Convenience for a contract event with a strike. */
    public static RoutableRecord contract(MarketDataSource source, EventType type, String symbol,
                                          String expiry, double strike, long epoch) {
        return new RoutableRecord(source, type, symbol, expiry, OptionalDouble.of(strike), epoch, null, null);
    }

    /** Convenience for an underlying event. */
    public static RoutableRecord underlying(MarketDataSource source, EventType type, long epoch) {
        return new RoutableRecord(source, type, null, null, OptionalDouble.empty(), epoch, null, null);
    }
}
