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
    void derivesUnderlyingKey() {
        RoutingTarget t = RoutingKeyDeriver.derive(
                RoutableRecord.underlying(MarketDataSource.IBKR, EventType.VIX_PRICE, 0)).orElseThrow();
        RoutingTarget.Underlying u = assertInstanceOf(RoutingTarget.Underlying.class, t);
        assertEquals(new UnderlyingKey(MarketDataSource.IBKR, "VIX"), u.key());
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
    void payloadSourceMatchingBindingIsAccepted() {
        RoutableRecord r = new RoutableRecord(MarketDataSource.DATABENTO, EventType.SNAPSHOT,
                "SPX", "20260612", OptionalDouble.of(7500), 0, "DATABENTO", null);
        assertTrue(RoutingKeyDeriver.derive(r).isPresent());
    }
}
