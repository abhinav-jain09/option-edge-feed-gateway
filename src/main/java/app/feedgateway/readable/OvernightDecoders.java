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
 * own, because the one arithmetic this estate must not hold two copies of is ES to SPX.
 *
 * <p><b>C-118: the raw {@code openCents} family is now read too, and that is not that arithmetic.</b>
 * It used to be deliberately untouched, and the consequence was that a bar the platform had not
 * translated was dropped whole &mdash; its volume, its two side counts and its delta with it. Those
 * cents are carried through as the FUTURES prices they are, printed marked as futures and never as
 * an index price. Reading a number the bar already holds is not computing a basis; it is the
 * opposite of computing one.
 *
 * <p><b>The price is the platform's own overnight SPX-equivalent.</b> {@code canonicalSpot} is a
 * decimal rendered as text, so it is parsed and not cast. Overnight its {@code source} reads
 * {@code ES_BASIS_DERIVED} and its {@code quality} {@code LIVE_ES_BASIS}; both are carried onto the
 * page verbatim so a reader sees which chain the figure came from rather than trusting the number.
 *
 * <p><b>A field that cannot be read makes the record ABSENT, not zero.</b> Every method returns
 * null and the caller counts it. But a bar with no {@code spxEqCloseCents} is no longer absent: it
 * is a bar the platform never translated, and under C-118 it is admitted without SPX prices rather
 * than dropped. Translating it here would still be that second copy of the arithmetic, and is
 * still not done.
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
     * <p><b>C-118: a bar with no SPX-equivalent price is ADMITTED, not dropped.</b> This method
     * used to return null when any of the four {@code spxEq*Cents} was missing, so such a bar never
     * reached the renderer at all and its row came out blank &mdash; the nine rows of 2026-11-20
     * (C-117), each one a real bar with real volume. Now the four are read together: all four
     * present is a translated bar, none present is a bar carrying only its own futures prices, and
     * a PARTIAL set is still refused, because three index prices and one futures price on one row
     * read as four prices of the same kind. A bar with neither price set is refused too: there is
     * nothing for a row to show.
     *
     * <p><b>Only the two finest timeframes are read, and each keeps its own.</b> The platform
     * publishes the same stretch of time at {@code 30s}, {@code 1m}, {@code 5m}, {@code 15m},
     * {@code 30m}, {@code 4h} and {@code session} — measured on Dev, 241 of the last 400 records
     * were {@code 30s}. The owner's rows are 30-second rows (C-64), so {@code 30s} is read and so is
     * {@code 1m} for the night's coarser grid; everything longer is refused, because a 5-minute bar
     * laid beside the minutes it contains would double every volume.
     */
    public static OvernightGrid.Bar bar(String json) {
        JsonNode root = parse(json);
        if (root == null) {
            return null;
        }
        String timeframe = text(root, "timeframe");
        if (!"30s".equals(timeframe) && !"1m".equals(timeframe)) {
            // The platform publishes 30s, 1m, 5m, 15m, 30m, 4h and session. Only the two finest are
            // read: the owner's rows are 30-second rows, the night's grid is built from minutes,
            // and a 5-minute bar would double-count the minutes it is made of.
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
        if (start == null) {
            return null;            // a bar with no start cannot be placed in any slot
        }
        Long open = longOrNull(o, "spxEqOpenCents");
        Long high = longOrNull(o, "spxEqHighCents");
        Long low = longOrNull(o, "spxEqLowCents");
        Long close = longOrNull(o, "spxEqCloseCents");
        int spx = (open == null ? 0 : 1) + (high == null ? 0 : 1)
                + (low == null ? 0 : 1) + (close == null ? 0 : 1);
        if (spx != 0 && spx != 4) {
            return null;            // half-translated: see this method's own javadoc
        }
        Long futOpen = longOrNull(o, "openCents");
        Long futHigh = longOrNull(o, "highCents");
        Long futLow = longOrNull(o, "lowCents");
        Long futClose = longOrNull(o, "closeCents");
        int fut = (futOpen == null ? 0 : 1) + (futHigh == null ? 0 : 1)
                + (futLow == null ? 0 : 1) + (futClose == null ? 0 : 1);
        if (spx == 0 && fut != 4) {
            // Nothing to print: no translation, and not a complete set of the bar's own prices
            // either. A PARTIAL futures set is not a reason to refuse a bar that IS translated --
            // real records carry openCents and closeCents without highCents and lowCents, and
            // refusing those would have thrown away four of this page's own fixtures and every
            // bar shaped like them.
            return null;
        }
        Long volume = longOrNull(o, "volume");
        boolean futures = fut == 4;
        return new OvernightGrid.Bar(start,
                spx == 4 ? open / 100.0 : null,
                spx == 4 ? high / 100.0 : null,
                spx == 4 ? low / 100.0 : null,
                spx == 4 ? close / 100.0 : null,
                volume == null ? 0L : volume, text(o, "spxEqBasisState"), timeframe,
                longOrNull(o, "buyerContracts"), longOrNull(o, "sellerContracts"),
                longOrNull(o, "delta"),
                futures ? futOpen / 100.0 : null,
                futures ? futHigh / 100.0 : null,
                futures ? futLow / 100.0 : null,
                futures ? futClose / 100.0 : null);
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
