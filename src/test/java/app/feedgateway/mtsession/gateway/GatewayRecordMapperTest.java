package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.mtsession.EventType;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.RoutableRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GatewayRecordMapperTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static com.fasterxml.jackson.databind.JsonNode node(String json) throws Exception {
        return M.readTree(json);
    }

    @Test
    void mapsContractEventWithStrike() throws Exception {
        Optional<RoutableRecord> r = GatewayRecordMapper.toRoutableRecord("DATABENTO", "pace",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\",\"strike\":7500,\"selectionEpoch\":5}"));
        RoutableRecord rec = r.orElseThrow();
        assertEquals(MarketDataSource.DATABENTO, rec.bindingSource());
        assertEquals(EventType.PACE, rec.eventType());
        assertEquals("SPX", rec.symbol());
        assertEquals("20260617", rec.expiry());
        assertEquals(7500.0, rec.strike().getAsDouble());
        assertEquals(5L, rec.selectionEpoch());
    }

    @Test
    void mapsUnderlyingEventWithoutSymbolStrike() throws Exception {
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "vix-price",
                node("{\"value\":18.2}")).orElseThrow();
        assertEquals(EventType.VIX_PRICE, rec.eventType());
        assertTrue(rec.strike().isEmpty());
    }

    @Test
    void contractEventWithoutStrikeHasEmptyStrike() throws Exception {
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "strike-flow",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\"}")).orElseThrow();
        assertTrue(rec.strike().isEmpty());
    }

    @Test
    void unknownEventReturnsEmpty() throws Exception {
        assertTrue(GatewayRecordMapper.toRoutableRecord("DATABENTO", "hpsf-latest-signal",
                node("{\"symbol\":\"SPX\"}")).isEmpty());
    }

    @Test
    void unknownSourceReturnsEmpty() throws Exception {
        assertTrue(GatewayRecordMapper.toRoutableRecord("MYSTERY", "pace",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\"}")).isEmpty());
    }

    @Test
    void eventTypeMapping() {
        assertEquals(EventType.SNAPSHOT, GatewayRecordMapper.eventTypeFor("snapshot"));
        assertEquals(EventType.MISSION_PACE, GatewayRecordMapper.eventTypeFor("mission-pace"));
        assertEquals(EventType.MISSION_CONTROL, GatewayRecordMapper.eventTypeFor("mission-control"));
        assertEquals(EventType.GEX_BY_STRIKE, GatewayRecordMapper.eventTypeFor("gex-by-strike"));
        assertEquals(EventType.MAX_PAIN, GatewayRecordMapper.eventTypeFor("max-pain"));
        assertNull(GatewayRecordMapper.eventTypeFor("hpsf-audit"));
        assertNull(GatewayRecordMapper.eventTypeFor(null));
    }

    // ---- source-mismatch rejection (req. 5) ----

    @Test
    void databentoBindingWithIbkrPayloadIsRejected() throws Exception {
        // payload marketDataSource contradicts the authoritative binding
        assertTrue(GatewayRecordMapper.toRoutableRecord("DATABENTO", "snapshot",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\",\"strike\":7500,\"marketDataSource\":\"IBKR\"}"))
                .isEmpty());
        // payload source field contradicts the binding
        assertTrue(GatewayRecordMapper.toRoutableRecord("DATABENTO", "strike-flow",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\",\"source\":\"IBKR\"}"))
                .isEmpty());
        // symmetric: IBKR binding + Databento payload
        assertTrue(GatewayRecordMapper.toRoutableRecord("IBKR", "snapshot",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\",\"strike\":7500,\"marketDataSource\":\"DB\"}"))
                .isEmpty());
    }

    @Test
    void matchingSourceIsAcceptedAndPayloadSourcesThreadedThrough() throws Exception {
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "snapshot",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\",\"strike\":7500,"
                        + "\"marketDataSource\":\"DATABENTO\",\"source\":\"DATABENTO\"}")).orElseThrow();
        assertEquals(MarketDataSource.DATABENTO, rec.bindingSource());
        assertEquals("DATABENTO", rec.payloadMarketDataSource());
        assertEquals("DATABENTO", rec.payloadSource());
    }

    @Test
    void avroContractEventWithoutPayloadSourceStaysBindingAuthoritative() throws Exception {
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "snapshot",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\",\"strike\":7500}")).orElseThrow();
        assertEquals(MarketDataSource.DATABENTO, rec.bindingSource());
        assertNull(rec.payloadMarketDataSource());
        assertNull(rec.payloadSource());
    }

    @Test
    void unrecognisedPayloadSourceDoesNotContradict() throws Exception {
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "snapshot",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\",\"strike\":7500,\"marketDataSource\":\"WAT\"}"))
                .orElseThrow();
        assertEquals("WAT", rec.payloadMarketDataSource());
    }
}
