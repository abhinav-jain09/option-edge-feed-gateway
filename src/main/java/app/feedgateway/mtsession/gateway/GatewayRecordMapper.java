package app.feedgateway.mtsession.gateway;

import app.feedgateway.mtsession.EventType;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.RoutableRecord;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Maps a gateway forward-path record (its topic-binding source + event name + the already-parsed
 * JSON payload) into a {@link RoutableRecord} for {@link app.feedgateway.mtsession.SessionRoutingEngine}
 * (OE-DDD-001 §8.7). Pure and unit-testable.
 *
 * <p>Returns empty when the event has no contract/underlying routing meaning (e.g. HPSF events),
 * signalling the caller to fall back to legacy broadcast for that event.
 */
public final class GatewayRecordMapper {

    private GatewayRecordMapper() {
    }

    public static Optional<RoutableRecord> toRoutableRecord(String bindingSource, String event, JsonNode root) {
        return toRoutableRecord(bindingSource, event, root, false);
    }

    /**
     * @param preOpenValueOnOrdinaryPlane true ONLY while the pre-open value plane is OFF
     *        ({@code GATEWAY_IBKR_PREOPEN_ENABLED=true} and {@code GATEWAY_IBKR_PREOPEN_GEX_ARBITRATION_ENABLED=false}),
     *        i.e. when the IBKR pre-open gamma value rides the ordinary gex-by-strike delivery. With the plane
     *        ON the arbitration plane owns that tuple and nothing here may change.
     */
    public static Optional<RoutableRecord> toRoutableRecord(
            String bindingSource, String event, JsonNode root, boolean preOpenValueOnOrdinaryPlane) {
        Optional<MarketDataSource> source = MarketDataSource.parse(bindingSource);
        if (source.isEmpty() || root == null) {
            return Optional.empty();
        }
        EventType type = eventTypeFor(event);
        if (type == null) {
            return Optional.empty();
        }
        // The topic binding is the authoritative source (OE-DDD-001 §8.6). The payload may also carry
        // a source — thread both into the RoutableRecord, but if the payload DECLARES a source that
        // contradicts the binding it is misrouted/corrupt data: reject it so it is never delivered to
        // a session of the binding source.
        String payloadMarketDataSource = textOrNull(root, "marketDataSource");
        String payloadSource = textOrNull(root, "source");
        if (preOpenValueOnOrdinaryPlane && isIbkrPreOpenGexValue(event, root)) {
            // The pre-open IBKR gamma value is DESIGNED to be a Databento-board value from a different
            // producer (the board joins it beside the Databento rows, D14): its `source` says IBKR on
            // purpose, while the topic binding and the enriched marketDataSource say DATABENTO. Read as a
            // contradiction it was dropped as non-routable — ~280 records/s on dev 2026-11-09, so a page
            // saw the pre-open status stream and no gamma at all. The exemption is this exact tuple only.
            payloadSource = null;
        }
        if (contradicts(source.get(), payloadMarketDataSource) || contradicts(source.get(), payloadSource)) {
            return Optional.empty();
        }
        long epoch = root.hasNonNull("selectionEpoch") ? root.get("selectionEpoch").asLong(0L) : 0L;
        if (type.isUnderlying()) {
            return Optional.of(new RoutableRecord(source.get(), type, null, null,
                    OptionalDouble.empty(), epoch, payloadMarketDataSource, payloadSource));
        }
        String symbol = root.hasNonNull("symbol") ? root.get("symbol").asText("") : "";
        if (symbol.isBlank() && type == EventType.SPREAD_SKEW) {
            // The spread-skew snapshot names its market `underlying` (no symbol field) — see EventType.
            symbol = root.hasNonNull("underlying") ? root.get("underlying").asText("") : "";
        }
        String expiry = root.hasNonNull("expiry") ? root.get("expiry").asText("") : "";
        if (expiry.isBlank() && (type == EventType.OPTION_PRICE_BEHAVIOR || type == EventType.OPB_SESSION)) {
            expiry = root.hasNonNull("tradingDate") ? root.get("tradingDate").asText("") : "";
        }
        OptionalDouble strike = root.hasNonNull("strike")
                ? OptionalDouble.of(root.get("strike").asDouble())
                : OptionalDouble.empty();
        return Optional.of(new RoutableRecord(source.get(), type, symbol, expiry, strike, epoch,
                payloadMarketDataSource, payloadSource));
    }

    /** The producer's own tuple, literally: a {@code gex-by-strike} record stamped {@code source=IBKR} AND
     *  {@code timeframe=IBKR_PREOPEN} (the same fail-closed pair the web client's {@code isPreOpenGexPayload}
     *  requires, byte-exact: the wire values are upper-case literals and a mis-cased copy is corrupt data,
     *  not the producer's tuple). Anything less — a bare IBKR source, a different timeframe, another event — keeps the
     *  contradiction rule. */
    static boolean isIbkrPreOpenGexValue(String event, JsonNode root) {
        return "gex-by-strike".equals(event)
                && "IBKR".equals(textOrNull(root, "source"))
                && "IBKR_PREOPEN".equals(textOrNull(root, "timeframe"));
    }

    private static String textOrNull(JsonNode root, String field) {
        return root.hasNonNull(field) ? root.get(field).asText(null) : null;
    }

    /**
     * True if {@code payloadSourceText} names a known market-data source that differs from the
     * authoritative {@code binding}. Absent or unrecognised payload sources do not contradict — the
     * binding remains authoritative (Avro contract events legitimately carry no source field).
     */
    private static boolean contradicts(MarketDataSource binding, String payloadSourceText) {
        if (payloadSourceText == null || payloadSourceText.isBlank()) {
            return false;
        }
        Optional<MarketDataSource> payload = MarketDataSource.parse(payloadSourceText);
        return payload.isPresent() && payload.get() != binding;
    }

    /** Maps the gateway's event-name strings to the routing {@link EventType}, or null if unroutable. */
    public static EventType eventTypeFor(String event) {
        if (event == null) {
            return null;
        }
        return switch (event) {
            case "snapshot" -> EventType.SNAPSHOT;
            case "pace" -> EventType.PACE;
            case "pace-rank" -> EventType.PACE_RANK;
            case "directional-pressure" -> EventType.DIRECTIONAL_PRESSURE;
            case "strike-flow" -> EventType.STRIKE_FLOW;
            case "delta-flow" -> EventType.DELTA_FLOW;
            case "strike-intel" -> EventType.STRIKE_INTEL;
            case "option-truth" -> EventType.OPTION_TRUTH;
            case "strike-invasion" -> EventType.STRIKE_INVASION;
            case "mission-pace" -> EventType.MISSION_PACE;
            case "mission-control" -> EventType.MISSION_CONTROL;
            case "spread-skew" -> EventType.SPREAD_SKEW;
            case "volume-sandwich" -> EventType.VOLUME_SANDWICH;
            case "mission-sandwich" -> EventType.MISSION_SANDWICH;
            case "gex-by-strike" -> EventType.GEX_BY_STRIKE;
            case "gex-oi-status" -> EventType.GEX_OI_STATUS;
            case "strike-sr" -> EventType.STRIKE_SR;
            case "gamma-migration" -> EventType.GAMMA_MIGRATION;
            case "corridor-gauge" -> EventType.CORRIDOR_GAUGE;
            case "max-pain" -> EventType.MAX_PAIN;
            case "es-gex" -> EventType.ES_GEX;
            case "es-strike-intel" -> EventType.ES_STRIKE_INTEL;
            case "liquidity-heatmap" -> EventType.LIQUIDITY_HEATMAP;
            case "option-price-behavior" -> EventType.OPTION_PRICE_BEHAVIOR;
            case "dealer-ledger" -> EventType.DEALER_LEDGER;
            case "opb-by-option" -> EventType.OPB_BY_OPTION;
            case "opb-session" -> EventType.OPB_SESSION;
            case "vix-price" -> EventType.VIX_PRICE;
            case "index-price" -> EventType.INDEX_PRICE;
            case "spx-price" -> EventType.SPX_PRICE;
            default -> null; // hpsf-* and others: caller falls back to broadcast
        };
    }
}
