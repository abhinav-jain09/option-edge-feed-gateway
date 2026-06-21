package app.feedgateway.mtsession;

/**
 * The derived routing identity of a record — either a {@link Contract} ({@code source|symbol|expiry})
 * or an {@link Underlying} ({@code source|underlying}). A sealed type so the router must handle both
 * shapes exhaustively (OE-DDD-001 §8.6 "two key shapes").
 */
public sealed interface RoutingTarget permits RoutingTarget.Contract, RoutingTarget.Underlying {

    MarketDataSource source();

    record Contract(SubscriptionKey key) implements RoutingTarget {
        public Contract {
            if (key == null) {
                throw new IllegalArgumentException("key");
            }
        }

        @Override
        public MarketDataSource source() {
            return key.source();
        }
    }

    record Underlying(UnderlyingKey key) implements RoutingTarget {
        public Underlying {
            if (key == null) {
                throw new IllegalArgumentException("key");
            }
        }

        @Override
        public MarketDataSource source() {
            return key.source();
        }
    }
}
