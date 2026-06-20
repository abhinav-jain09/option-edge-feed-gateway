package app.feedgateway.mtsession;

import java.util.Optional;

/**
 * Derives the {@link RoutingTarget} of a record (OE-DDD-001 §8.7 {@code deriveKey}).
 *
 * <p>Rules, mirroring the gateway's existing {@code matchesSelectionNode}:
 * <ul>
 *   <li>source is authoritative from the topic binding;</li>
 *   <li>if the payload carries a {@code marketDataSource} that conflicts with the binding, the
 *       record is rejected (empty) — a defensive guard against mislabeled records;</li>
 *   <li>a blank payload source of {@code UNUSUAL_WHALES} is treated as {@code IBKR};</li>
 *   <li>underlying events derive an {@link UnderlyingKey}; contract events derive a
 *       {@link SubscriptionKey} from normalized symbol/expiry, rejecting blanks.</li>
 * </ul>
 * Returning {@link Optional#empty()} means "do not route this record".
 */
public final class RoutingKeyDeriver {

    private RoutingKeyDeriver() {
    }

    public static Optional<RoutingTarget> derive(RoutableRecord record) {
        MarketDataSource source = record.bindingSource();

        // Shared underlyings (VIX) route under SHARED regardless of the binding/payload source, so EVERY
        // session — DATABENTO or IBKR — receives them. The source-mismatch guard below does not apply here
        // (VIX is intentionally source-agnostic).
        if (record.eventType().isUnderlying()) {
            String underlying = record.eventType().underlyingSymbol();
            if (SharedUnderlyings.isShared(underlying)) {
                return Optional.of(new RoutingTarget.Underlying(
                        new UnderlyingKey(MarketDataSource.SHARED, underlying)));
            }
        }

        Optional<MarketDataSource> payloadSource = MarketDataSource.parse(record.payloadMarketDataSource());
        if (payloadSource.isEmpty()
                && record.payloadSource() != null
                && "UNUSUAL_WHALES".equalsIgnoreCase(record.payloadSource().trim())) {
            payloadSource = Optional.of(MarketDataSource.IBKR);
        }
        if (payloadSource.isPresent() && payloadSource.get() != source) {
            return Optional.empty(); // source mismatch — reject
        }

        if (record.eventType().isUnderlying()) {
            return Optional.of(new RoutingTarget.Underlying(
                    new UnderlyingKey(source, record.eventType().underlyingSymbol())));
        }

        String symbol = Normalization.symbol(record.symbol());
        String expiry = Normalization.expiry(record.expiry());
        if (symbol.isEmpty() || expiry.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RoutingTarget.Contract(new SubscriptionKey(source, symbol, expiry)));
    }
}
