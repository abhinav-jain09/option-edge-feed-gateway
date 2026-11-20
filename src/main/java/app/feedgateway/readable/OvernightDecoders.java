package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the two overnight inputs off the records the gateway already caches, and <b>nothing else
 * from them</b>.
 *
 * <p><b>They are JSON here, not Avro.</b> The gateway admits footprint bars and SPX prices to its
 * caches as JSON strings, so this reads JSON. That is why the decoder lives beside the renderer
 * rather than being shared with the map service: the same fields, a different wire shape, and
 * pretending otherwise would mean one of the two decoding a shape it never sees.
 *
 * <p><b>The bars already carry the translation.</b> Each bar's {@code observations} holds
 * {@code spxEqOpenCents}, {@code spxEqHighCents}, {@code spxEqLowCents}, {@code spxEqCloseCents}
 * and {@code spxEqBasisState} beside the raw ES cents. This reads those and applies no basis of its
 * own; the raw {@code openCents} family is deliberately never touched, because the one arithmetic
 * this estate must not hold two copies of is ES to SPX.
 *
 * <p><b>The price is the platform's own overnight SPX-equivalent.</b> {@code canonicalSpot} is a
 * decimal rendered as text, so it is parsed and not cast. Overnight its {@code source} reads
 * {@code ES_BASIS_DERIVED} and its {@code quality} {@code LIVE_ES_BASIS}; both are carried onto the
 * page verbatim so a reader sees which chain the figure came from rather than trusting the number.
 *
 * <p><b>A field that cannot be read makes the record ABSENT, not zero.</b> Every method returns
 * null and the caller counts it. A bar with no {@code spxEqCloseCents} was never translated, and
 * translating it here would be that second copy of the arithmetic.
 */
public final class OvernightDecoders {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OvernightDecoders() {
    }

    /**
     * One footprint bar, or null where the record is not one this page can use.
     *
     * <p><b>An INCOMPLETE bar is refused.</b> {@code complete} false means the minute is still
     * forming, and a forming minute's high and low are not the minute's. Admitting it would show a
     * close that moves backwards when the finished bar replaces it.
     *
     * <p><b>Only one-minute bars are read.</b> The gateway's view holds several timeframes keyed
     * together; taking a 5-minute bar as if it were a minute would double-count its volume into a
     * slot that already has the minutes it is made of.
     */
    public static OvernightGrid.Bar bar(String json) {
        JsonNode root = parse(json);
        if (root == null) {
            return null;
        }
        if (!"1m".equals(text(root, "timeframe"))) {
            return null;
        }
        JsonNode o = root.path("observations");
        if (o.isMissingNode() || o.isNull()) {
            return null;
        }
        if (o.path("complete").isBoolean() && !o.path("complete").asBoolean()) {
            return null;
        }
        Long start = longOrNull(o, "barStartMs");
        Long open = longOrNull(o, "spxEqOpenCents");
        Long high = longOrNull(o, "spxEqHighCents");
        Long low = longOrNull(o, "spxEqLowCents");
        Long close = longOrNull(o, "spxEqCloseCents");
        if (start == null || open == null || high == null || low == null || close == null) {
            return null;
        }
        Long volume = longOrNull(o, "volume");
        return new OvernightGrid.Bar(start, open / 100.0, high / 100.0, low / 100.0, close / 100.0,
                volume == null ? 0L : volume, text(o, "spxEqBasisState"));
    }

    /**
     * The last price, or null where this record carries no usable spot.
     *
     * @param recordTimestampMs the broker's stamp, used only where the record carries no event time
     *                          of its own. Never this process's wall clock: a replay stamped with
     *                          the replay's time cannot be compared with the live run at the same
     *                          instants.
     */
    public static ReadablePage.Price price(String json, long recordTimestampMs) {
        JsonNode root = parse(json);
        if (root == null) {
            return null;
        }
        String spot = text(root, "canonicalSpot");
        if (spot == null) {
            spot = text(root, "price");
        }
        if (spot == null) {
            return null;
        }
        double spx;
        try {
            spx = Double.parseDouble(spot);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!(spx > 0)) {
            return null;
        }
        long at = recordTimestampMs;
        Long ns = longOrNull(root, "spotEventTimeNs");
        if (ns != null && ns > 0) {
            at = ns / 1_000_000L;
        }
        if (at <= 0) {
            // A price with no time at all cannot be judged stale, which is the one thing the page
            // must be able to say about it. Absent beats a price the reader cannot date.
            return null;
        }
        String source = text(root, "source");
        String quality = text(root, "quality");
        return new ReadablePage.Price(at, spx,
                source == null ? "UNKNOWN" : source,
                quality == null ? "UNKNOWN" : quality);
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            return root == null || !root.isObject() ? null : root;
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.isTextual() ? v.textValue() : v.asText();
        return s == null || s.isEmpty() ? null : s;
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.longValue();
        }
        if (v.isTextual()) {
            try {
                return Long.parseLong(v.textValue().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
