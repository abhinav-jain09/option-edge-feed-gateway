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
    void mapsUnifiedSrLevelAsContractEventWithStrikeAndExpiry() throws Exception {
        // The real UNIFIED_SR_LEVEL payload (producer PR #131) carries symbol/marketDataSource/
        // expiry/strike(=bucketStrike)/bucketStrike — proving strike-sr routes + strike-window filters.
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "strike-sr",
                node("{\"messageType\":\"UNIFIED_SR_LEVEL\",\"symbol\":\"SPX\",\"marketDataSource\":\"DATABENTO\","
                        + "\"expiry\":\"20260619\",\"sessionDate\":\"20260619\",\"strike\":6050.0,"
                        + "\"bucketStrike\":6050.0,\"dominantSide\":\"RESISTANCE\"}")).orElseThrow();
        assertEquals(EventType.STRIKE_SR, rec.eventType());
        assertEquals("SPX", rec.symbol());
        assertEquals("20260619", rec.expiry());
        assertEquals(6050.0, rec.strike().getAsDouble());
    }

    @Test
    void mapsUnderlyingEventWithoutSymbolStrike() throws Exception {
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "vix-price",
                node("{\"value\":18.2}")).orElseThrow();
        assertEquals(EventType.VIX_PRICE, rec.eventType());
        assertTrue(rec.strike().isEmpty());
    }

    @Test
    void mapsSpxPriceAsUnderlyingEventDespiteCascadeTierSource() throws Exception {
        // The canonical spot's payload source names the cascade tier (never a market-data source
        // token), so it must not contradict the DATABENTO binding — the record stays routable.
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "spx-price",
                node("{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"SYNTHETIC_OPTION_SPOT\","
                        + "\"quality\":\"SYNTHETIC\"}")).orElseThrow();
        assertEquals(EventType.SPX_PRICE, rec.eventType());
        assertTrue(rec.eventType().isUnderlying());
        assertEquals("SPX", rec.eventType().underlyingSymbol());
        assertTrue(rec.strike().isEmpty());
        assertEquals(EventType.SPX_PRICE, GatewayRecordMapper.eventTypeFor("spx-price"));
    }

    @Test
    void contractEventWithoutStrikeHasEmptyStrike() throws Exception {
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "strike-flow",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\"}")).orElseThrow();
        assertTrue(rec.strike().isEmpty());
    }

    @Test
    void mapsDeltaFlowAsContractEventWithStrike() throws Exception {
        // delta-flow is per-strike JSON (DeltaFlowStrikeSnapshot), keyed symbol|date|expiry|strike —
        // it routes CONTRACT-scoped by source|symbol|expiry AND carries a strike for the per-user filter.
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "delta-flow",
                node("{\"symbol\":\"SPX\",\"tradingDate\":\"2026-06-17\",\"expiry\":\"20260617\","
                        + "\"strike\":6000.0,\"sessionNetDeltaFlow\":12345.6,"
                        + "\"confidenceWeightQuality\":\"DEGRADED\"}")).orElseThrow();
        assertEquals(EventType.DELTA_FLOW, rec.eventType());
        assertTrue(EventType.DELTA_FLOW.isContractScoped());
        assertEquals("SPX", rec.symbol());
        assertEquals("20260617", rec.expiry());
        assertEquals(6000.0, rec.strike().getAsDouble());
    }

    @Test
    void deltaFlowEventTypeMapping() {
        assertEquals(EventType.DELTA_FLOW, GatewayRecordMapper.eventTypeFor("delta-flow"));
    }

    @Test
    void mapsStrikeIntelAsContractEventWithStrike() throws Exception {
        // strike-intel is per-strike JSON (StrikeIntelligenceSignal), keyed symbol|expiry|strike —
        // it routes CONTRACT-scoped by source|symbol|expiry AND carries a strike for the per-user filter.
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "strike-intel",
                node("{\"symbol\":\"SPX\",\"expiry\":\"20260617\","
                        + "\"strike\":6000.0,\"strikeRole\":\"MAGNET\"}")).orElseThrow();
        assertEquals(EventType.STRIKE_INTEL, rec.eventType());
        assertTrue(EventType.STRIKE_INTEL.isContractScoped());
        assertEquals("SPX", rec.symbol());
        assertEquals("20260617", rec.expiry());
        assertEquals(6000.0, rec.strike().getAsDouble());
    }

    @Test
    void strikeIntelEventTypeMapping() {
        assertEquals(EventType.STRIKE_INTEL, GatewayRecordMapper.eventTypeFor("strike-intel"));
    }

    @Test
    void mapsOptionTruthAsStrikeFilteredContractEvent() throws Exception {
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "option-truth",
                node("{\"symbol\":\"SPX\",\"expiry\":\"2026-07-20\",\"strike\":7500.0,"
                        + "\"horizon\":\"STEP\",\"eventTimeMs\":1784520000000}"))
                .orElseThrow();
        assertEquals(EventType.OPTION_TRUTH, rec.eventType());
        assertTrue(EventType.OPTION_TRUTH.isContractScoped());
        assertEquals("SPX", rec.symbol());
        assertEquals("2026-07-20", rec.expiry());
        assertEquals(7500.0, rec.strike().getAsDouble());
    }

    @Test
    void optionTruthEventTypeMapping() {
        assertEquals(EventType.OPTION_TRUTH, GatewayRecordMapper.eventTypeFor("option-truth"));
    }

    @Test
    void strikeInvasionEventTypeMapping() {
        assertEquals(EventType.STRIKE_INVASION, GatewayRecordMapper.eventTypeFor("strike-invasion"));
    }

    @Test
    void mapsStrikeInvasionAsContractEventWithNoExpiry() throws Exception {
        // strike-invasion is per-strike JSON (StrikeInvasionSnapshot), SPX-only with NO expiry — it routes
        // CONTRACT-scoped and carries a strike for the per-user filter; the mapper tolerates a missing expiry.
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "strike-invasion",
                node("{\"symbol\":\"SPX\",\"strike\":6000.0,\"invasionState\":\"INVADED\"}")).orElseThrow();
        assertEquals(EventType.STRIKE_INVASION, rec.eventType());
        assertTrue(EventType.STRIKE_INVASION.isContractScoped());
        assertEquals("SPX", rec.symbol());
        assertEquals("", rec.expiry());
        assertEquals(6000.0, rec.strike().getAsDouble());
    }

    @Test
    void spreadSkewEventTypeMapping() {
        assertEquals(EventType.SPREAD_SKEW, GatewayRecordMapper.eventTypeFor("spread-skew"));
        // The discrete spread-skew-event siblings are broadcast standalone (turn-alert style), never routed.
        assertNull(GatewayRecordMapper.eventTypeFor("spread-skew-event"));
    }

    @Test
    void mapsSpreadSkewAsContractEventUsingUnderlyingAsSymbol() throws Exception {
        // spread-skew is a whole-underlying JSON snapshot (SpreadSkewSnapshot) that names its market
        // `underlying` (no symbol field) with a nullable expiry — the mapper reads underlying as the
        // symbol so it routes CONTRACT-scoped with NO strike filter (like MISSION_CONTROL).
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "spread-skew",
                node("{\"underlying\":\"SPX\",\"expiry\":\"2026-07-11\",\"ts\":1752192000000,"
                        + "\"headline\":{\"state\":\"CALL_SKEW\",\"z\":2.4}}")).orElseThrow();
        assertEquals(EventType.SPREAD_SKEW, rec.eventType());
        assertTrue(EventType.SPREAD_SKEW.isContractScoped());
        assertEquals("SPX", rec.symbol());
        assertEquals("2026-07-11", rec.expiry());
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
        assertEquals(EventType.LIQUIDITY_HEATMAP, GatewayRecordMapper.eventTypeFor("liquidity-heatmap"));
        assertNull(GatewayRecordMapper.eventTypeFor("hpsf-audit"));
        assertNull(GatewayRecordMapper.eventTypeFor(null));
    }

    @Test
    void liquidityHeatmapRoutesContractScopedLikeMaxPain() {
        // One column frame covers the whole (symbol, expiry) chain: contract-scoped, no strike filter.
        assertEquals(true, EventType.LIQUIDITY_HEATMAP.isContractScoped());
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

    @Test
    void dealerLedgerRoutesContractScopedBySymbolExpiry() throws Exception {
        // The joined dealer-ledger envelope routes like a whole-chain event: contract-scoped, no strike.
        assertEquals(EventType.DEALER_LEDGER, GatewayRecordMapper.eventTypeFor("dealer-ledger"));
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "dealer-ledger",
                node("{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"marketDataSource\":\"DATABENTO\"}"))
                .orElseThrow();
        assertEquals(EventType.DEALER_LEDGER, rec.eventType());
        assertEquals("SPXW", rec.symbol());
        assertEquals("20260704", rec.expiry());
        assertTrue(rec.strike().isEmpty());
    }

    @Test
    void gammaMigrationRoutesContractScopedWithNoStrikeFilter() throws Exception {
        // The record describes the WHOLE chain even though it names a hot strike inside it.
        // Routing per-strike would deliver it only to sessions already watching the strike that
        // just heated up — precisely the session that does not need telling.
        RoutableRecord rec = GatewayRecordMapper.toRoutableRecord("DATABENTO", "gamma-migration",
                node("{\"messageType\":\"GAMMA_MIGRATION_SNAPSHOT\",\"symbol\":\"SPX\","
                        + "\"marketDataSource\":\"DATABENTO\",\"expiry\":\"20260731\","
                        + "\"hotStrike\":7450.0}"))
                .orElseThrow(() -> new AssertionError(
                        "gamma-migration must be routable, not fall through to broadcast"));
        assertEquals(EventType.GAMMA_MIGRATION, rec.eventType());
        assertEquals("SPX", rec.symbol());
        assertEquals("20260731", rec.expiry());
        assertTrue(EventType.GAMMA_MIGRATION.isContractScoped());
        assertTrue(rec.strike().isEmpty(),
                "no strike filter: hotStrike names a target, not an audience — routing on it would "
                + "deliver the record only to sessions already watching that strike");
    }
}
