package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventTypeTest {

    @Test
    void contractScopedClassification() {
        for (EventType t : new EventType[]{EventType.SNAPSHOT, EventType.PACE, EventType.DIRECTIONAL_PRESSURE,
                EventType.STRIKE_FLOW, EventType.MISSION_PACE, EventType.MISSION_CONTROL, EventType.VOLUME_SANDWICH, EventType.MISSION_SANDWICH, EventType.GEX_BY_STRIKE, EventType.MAX_PAIN}) {
            assertTrue(t.isContractScoped(), t + " should be contract-scoped");
            assertFalse(t.isUnderlying(), t + " should not be underlying");
        }
    }

    @Test
    void dealerLedgerIsContractScoped() {
        // The gateway joins profile+state into one per-(symbol,expiry) envelope — routes like MAX_PAIN.
        assertTrue(EventType.DEALER_LEDGER.isContractScoped());
        assertFalse(EventType.DEALER_LEDGER.isUnderlying());
    }

    @Test
    void underlyingClassificationAndMapping() {
        assertTrue(EventType.VIX_PRICE.isUnderlying());
        assertEquals("VIX", EventType.VIX_PRICE.underlyingSymbol());
        assertEquals("SPX", EventType.INDEX_PRICE.underlyingSymbol());
        assertEquals("SPX", EventType.SPX_PRICE.underlyingSymbol());
    }

    @Test
    void underlyingSymbolRejectsContractEvents() {
        assertThrows(IllegalStateException.class, EventType.SNAPSHOT::underlyingSymbol);
    }
}
