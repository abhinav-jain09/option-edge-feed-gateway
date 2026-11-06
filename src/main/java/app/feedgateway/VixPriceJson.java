package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import com.optionsedge.contracts.hpsf.VixUnderlyingPriceEvent;

/**
 * The browser JSON of {@code underlying.vix.price}. The topic is Avro only (decoded by VixUnderlyingPriceAvro.EventDeserializer); the browser
 * contract is unchanged, so a decoded record is re-emitted with the field names, order and number formatting of the JSON era: prices as
 * CPython {@code repr(float)} text, the four parity fields ABSENT on an IBKR_INDEX print (the JSON era never wrote them there) and the eight
 * ATM evidence fields present as {@code null} when the feed holds no complete set.
 */
final class VixPriceJson {
    private VixPriceJson() {}

    /** The JSON text of a decoded event; {@code null} for a tombstone, a loud failure for anything that is not the typed event. */
    static String render(ObjectMapper mapper, Object value) {
        if (value == null) return null;
        if (!(value instanceof VixUnderlyingPriceEvent e))
            throw new IllegalStateException("vix-price: value is not a VixUnderlyingPriceEvent (" + value.getClass().getName() + ")");
        ObjectNode n = mapper.createObjectNode();
        n.put("schemaVersion", e.schemaVersion());
        n.put("symbol", e.symbol());
        number(n, "price", e.price());
        n.put("eventTime", e.eventTime());
        n.put("sourceEventTimeNs", e.sourceEventTimeNs());
        n.put("expiry", e.expiry());
        n.put("source", e.source());
        n.put("quality", e.quality());
        n.put("derivation", e.derivation());
        if (e.pairsUsed() != null) n.put("pairsUsed", e.pairsUsed());
        if (e.dispersion() != null) number(n, "dispersion", e.dispersion());
        if (e.oldestLegAgeMs() != null) n.put("oldestLegAgeMs", e.oldestLegAgeMs());
        if (e.parityDiscountErrorEstVolPts() != null) number(n, "parityDiscountErrorEstVolPts", e.parityDiscountErrorEstVolPts());
        number(n, "atmStrike", e.atmStrike());
        number(n, "atmCallBid", e.atmCallBid());
        number(n, "atmCallAsk", e.atmCallAsk());
        number(n, "atmPutBid", e.atmPutBid());
        number(n, "atmPutAsk", e.atmPutAsk());
        number(n, "atmCallSpread", e.atmCallSpread());
        number(n, "atmPutSpread", e.atmPutSpread());
        number(n, "maxLegSpread", e.maxLegSpread());
        n.put("feedEpochId", e.feedEpochId());
        n.put("epochSeq", e.epochSeq());
        return n.toString();
    }

    private static void number(ObjectNode n, String field, Double v) {
        if (v == null) n.putNull(field);
        else n.putRawValue(field, new RawValue(SpxPriceJson.pythonRepr(v)));
    }
}
