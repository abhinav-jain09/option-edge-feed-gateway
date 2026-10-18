package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import java.math.BigDecimal;
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
        n.putRawValue("price", new RawValue(pythonRepr(e.price())));
        n.put("size", e.size());
        n.put("source", e.source());
        n.put("quality", e.quality());
        n.putRawValue("canonicalSpot", new RawValue(pythonRepr(e.canonicalSpot())));
        n.put("spotEventTimeNs", e.spotEventTimeNs());
        n.put("feedEpochId", e.feedEpochId());
        n.put("epochSeq", e.epochSeq());
        n.put("spotQuality", e.spotQuality());
        return n.toString();
    }

    /**
     * CPython's {@code repr(float)}, which is what the JSON-era feed wrote: the shortest round-trip digits, plain decimal
     * (integral values keep {@code .0}) while the decimal exponent is in [-4, 16), otherwise scientific with a signed
     * exponent of at least two digits ({@code 1e-05}, {@code 1.5e+16}). Jackson/{@code Double.toString} switch at 1e7 and 1e-3.
     */
    static String pythonRepr(double v) {
        if (v == 0.0) return Double.doubleToRawLongBits(v) < 0 ? "-0.0" : "0.0";
        BigDecimal b = new BigDecimal(Double.toString(Math.abs(v))).stripTrailingZeros();
        String digits = b.unscaledValue().toString();
        int exp = digits.length() - 1 - b.scale();
        StringBuilder out = new StringBuilder(24);
        if (v < 0) out.append('-');
        if (exp >= -4 && exp < 16) {
            if (exp >= 0) {
                if (digits.length() <= exp + 1) out.append(digits).append("0".repeat(exp + 1 - digits.length())).append(".0");
                else out.append(digits, 0, exp + 1).append('.').append(digits, exp + 1, digits.length());
            } else {
                out.append("0.").append("0".repeat(-exp - 1)).append(digits);
            }
        } else {
            out.append(digits.charAt(0));
            if (digits.length() > 1) out.append('.').append(digits, 1, digits.length());
            out.append('e').append(exp < 0 ? '-' : '+');
            int abs = Math.abs(exp);
            if (abs < 10) out.append('0');
            out.append(abs);
        }
        return out.toString();
    }
}
