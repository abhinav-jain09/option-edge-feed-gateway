package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class RoutingKeyDeriverTest {

    @Test
    void derivesContractKeyWithNormalization() {
        RoutableRecord r = new RoutableRecord(MarketDataSource.DATABENTO, EventType.PACE,
                " spx ", "2026-06-12", OptionalDouble.of(7500), 0, null, null);
        RoutingTarget t = RoutingKeyDeriver.derive(r).orElseThrow();
        RoutingTarget.Contract c = assertInstanceOf(RoutingTarget.Contract.class, t);
        assertEquals(new SubscriptionKey(MarketDataSource.DATABENTO, "SPX", "20260612"), c.key());
    }

    @Test
    void vixIsSharedRegardlessOfBindingSource() {
        // P1: VIX is a SHARED underlying — it routes under SHARED|VIX no matter which source's topic it
        // arrived on, so DATABENTO and IBKR sessions (both indexed under SHARED|VIX) all receive it.
        for (MarketDataSource binding : new MarketDataSource[]{MarketDataSource.IBKR, MarketDataSource.DATABENTO}) {
            RoutingTarget t = RoutingKeyDeriver.derive(
                    RoutableRecord.underlying(binding, EventType.VIX_PRICE, 0)).orElseThrow();
            RoutingTarget.Underlying u = assertInstanceOf(RoutingTarget.Underlying.class, t);
            assertEquals(new UnderlyingKey(MarketDataSource.SHARED, "VIX"), u.key());
        }
    }

    @Test
    void nonSharedUnderlyingKeepsItsSource() {
        RoutingTarget t = RoutingKeyDeriver.derive(
                RoutableRecord.underlying(MarketDataSource.DATABENTO, EventType.INDEX_PRICE, 0)).orElseThrow();
        RoutingTarget.Underlying u = assertInstanceOf(RoutingTarget.Underlying.class, t);
        assertEquals(new UnderlyingKey(MarketDataSource.DATABENTO, "SPX"), u.key());
    }

    @Test
    void rejectsSourceMismatchInPayload() {
        RoutableRecord r = new RoutableRecord(MarketDataSource.DATABENTO, EventType.SNAPSHOT,
                "SPX", "20260612", OptionalDouble.of(7500), 0, "IBKR", null);
        assertTrue(RoutingKeyDeriver.derive(r).isEmpty());
    }

    @Test
    void unusualWhalesPayloadTreatedAsIbkr() {
        RoutableRecord r = new RoutableRecord(MarketDataSource.IBKR, EventType.GEX_BY_STRIKE,
                "SPX", "20260612", OptionalDouble.of(7500), 0, null, "UNUSUAL_WHALES");
        RoutingTarget t = RoutingKeyDeriver.derive(r).orElseThrow();
        assertEquals(MarketDataSource.IBKR, t.source());
    }

    @Test
    void rejectsBlankSymbolOrExpiryForContractEvent() {
        RoutableRecord noSym = new RoutableRecord(MarketDataSource.DATABENTO, EventType.PACE,
                "  ", "20260612", OptionalDouble.empty(), 0, null, null);
        RoutableRecord noExp = new RoutableRecord(MarketDataSource.DATABENTO, EventType.PACE,
                "SPX", "", OptionalDouble.empty(), 0, null, null);
        assertEquals(Optional.empty(), RoutingKeyDeriver.derive(noSym));
        assertEquals(Optional.empty(), RoutingKeyDeriver.derive(noExp));
    }

    @Test
    void spreadSkewWithBlankExpiryRoutesBySourceAndUnderlying() {
        // A nullable-expiry spread-skew snapshot (EXPIRY_MISSING / degraded heartbeat) must not be
        // rejected as a blank contract key: it targets every session viewing the underlying.
        RoutableRecord r = new RoutableRecord(MarketDataSource.DATABENTO, EventType.SPREAD_SKEW,
                "SPX", "", OptionalDouble.empty(), 0, null, null);
        RoutingTarget t = RoutingKeyDeriver.derive(r).orElseThrow();
        RoutingTarget.Underlying u = assertInstanceOf(RoutingTarget.Underlying.class, t);
        assertEquals(new UnderlyingKey(MarketDataSource.DATABENTO, "SPX"), u.key());
    }

    @Test
    void spreadSkewWithPresentExpiryStaysContractScoped() {
        // A frame pinned to a chain keeps the normal contract scope, so it never leaks to sessions
        // on a different expiry.
        RoutableRecord r = new RoutableRecord(MarketDataSource.DATABENTO, EventType.SPREAD_SKEW,
                "SPX", "2026-07-11", OptionalDouble.empty(), 0, null, null);
        RoutingTarget t = RoutingKeyDeriver.derive(r).orElseThrow();
        RoutingTarget.Contract c = assertInstanceOf(RoutingTarget.Contract.class, t);
        assertEquals(new SubscriptionKey(MarketDataSource.DATABENTO, "SPX", "20260711"), c.key());
    }

    @Test
    void payloadSourceMatchingBindingIsAccepted() {
        RoutableRecord r = new RoutableRecord(MarketDataSource.DATABENTO, EventType.SNAPSHOT,
                "SPX", "20260612", OptionalDouble.of(7500), 0, "DATABENTO", null);
        assertTrue(RoutingKeyDeriver.derive(r).isPresent());
    }
}
