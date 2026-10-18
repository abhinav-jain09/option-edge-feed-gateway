package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.optionsedge.contracts.hpsf.UnderlyingPriceEvent;

/**
 * The browser JSON of {@code underlying.spx.price}. The topic is Avro only (decoded by UnderlyingPriceAvro.EventDeserializer); the browser contract is unchanged, so a
 * decoded record is re-emitted with the field names, order and number formatting of the JSON era
 * ({@code price}/{@code canonicalSpot} as {@code 7335.0}/{@code 6012.5}, {@code size} as {@code null}).
 */
final class SpxPriceJson {
    private SpxPriceJson() {}

    /** The JSON text of a decoded event; {@code null} for a tombstone, a loud failure for anything that is not the typed event. */
    static String render(ObjectMapper mapper, Object value) {
        if (value == null) return null;
        if (!(value instanceof UnderlyingPriceEvent e))
            throw new IllegalStateException("spx-price: value is not an UnderlyingPriceEvent (" + value.getClass().getName() + ")");
        ObjectNode n = mapper.createObjectNode();
        n.put("schemaVersion", e.schemaVersion());
        n.put("symbol", e.symbol());
        n.put("eventTime", e.eventTime());
        n.put("price", e.price());
        n.put("size", e.size());
        n.put("source", e.source());
        n.put("quality", e.quality());
        n.put("canonicalSpot", e.canonicalSpot());
        n.put("spotEventTimeNs", e.spotEventTimeNs());
        n.put("feedEpochId", e.feedEpochId());
        n.put("epochSeq", e.epochSeq());
        n.put("spotQuality", e.spotQuality());
        return n.toString();
    }
}
