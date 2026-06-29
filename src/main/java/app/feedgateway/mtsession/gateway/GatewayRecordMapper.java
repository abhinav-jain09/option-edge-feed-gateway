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
        if (contradicts(source.get(), payloadMarketDataSource) || contradicts(source.get(), payloadSource)) {
            return Optional.empty();
        }
        long epoch = root.hasNonNull("selectionEpoch") ? root.get("selectionEpoch").asLong(0L) : 0L;
        if (type.isUnderlying()) {
            return Optional.of(new RoutableRecord(source.get(), type, null, null,
                    OptionalDouble.empty(), epoch, payloadMarketDataSource, payloadSource));
        }
        String symbol = root.hasNonNull("symbol") ? root.get("symbol").asText("") : "";
        String expiry = root.hasNonNull("expiry") ? root.get("expiry").asText("") : "";
        OptionalDouble strike = root.hasNonNull("strike")
                ? OptionalDouble.of(root.get("strike").asDouble())
                : OptionalDouble.empty();
        return Optional.of(new RoutableRecord(source.get(), type, symbol, expiry, strike, epoch,
                payloadMarketDataSource, payloadSource));
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
            case "mission-pace" -> EventType.MISSION_PACE;
            case "mission-control" -> EventType.MISSION_CONTROL;
            case "volume-sandwich" -> EventType.VOLUME_SANDWICH;
            case "gex-by-strike" -> EventType.GEX_BY_STRIKE;
            case "strike-sr" -> EventType.STRIKE_SR;
            case "max-pain" -> EventType.MAX_PAIN;
            case "vix-price" -> EventType.VIX_PRICE;
            case "index-price" -> EventType.INDEX_PRICE;
            default -> null; // hpsf-* and others: caller falls back to broadcast
        };
    }
}
