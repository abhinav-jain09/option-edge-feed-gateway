package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The gamma board as it exists <b>before the cash open</b>, for the owner's 06:30 New York start.
 *
 * <p><b>Why this class exists.</b> The owner reads his ladder the moment the open interest arrives,
 * 06:30 New York, and trades off it in the pre-market. The map service cannot serve him then, and
 * not because it holds itself back: {@code SessionWindow} bounds only the cash close and
 * {@code roundComplete} is satisfied at whatever hour the producer first comes round the board. It
 * consumes one topic, {@code options.databento.gex.strike}, and the pre-open board is published to
 * another — {@code options.databento.gex.strike.preopen}, which {@code databento-gex-history}
 * projects the IBKR pre-open records onto. Measured on 2026-11-19: the pre-open board was complete
 * at <b>06:43:02</b> New York, 41 of the owner's 41 grid rows with no gaps, while the first map
 * record of that day did not arrive until <b>09:15:58</b> — two hours thirty-three minutes later.
 *
 * <p><b>What this is not.</b> This is not the map board and must never read as it. The pre-open
 * record carries strike, net gamma, its sign and spot; it carries <b>no what-if reading</b> — no
 * pivot ladder, no ladder state, no regime — because those are the map service's own work on
 * Databento rows. So the read cell of every row here is <b>empty</b>, and the board names its own
 * source so that a reader can never mistake which of the two he is looking at. Putting
 * {@code gammaSign} into the read column instead would be a claim the input cannot support.
 *
 * <p><b>Nothing is computed here.</b> {@code netGex} is published by the gamma path and passed
 * through; this class never calculates gamma. {@code canonicalSpot} is already SPX — the records
 * carry {@code symbol SPX} and strikes on the SPX scale — so no basis is applied, and none would
 * be correct.
 */
public final class PreopenBoard {

    /** The source this board names itself by, so it cannot be read as the map board. */
    public static final String SOURCE = "IBKR pre-open";

    /** The owner's board is SPX. An ES pre-open record is not his board and is refused. */
    static final String SYMBOL = "SPX";

    /** Used only when a record declares no {@code staleAfterMs} of its own. */
    static final long DEFAULT_STALE_AFTER_MS = 300_000L;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private PreopenBoard() {
    }

    /** One decoded pre-open row, as the producer published it. */
    record Row(String expiry, double strike, Double netGex, String gammaSign, double spot,
               long updatedAtMs, long staleAfterMs) {
    }

    /**
     * Build the pre-open board, or null where these records cannot make one.
     *
     * @param records the newest pre-open record per strike, as JSON exactly as published
     * @param nowMs   the write instant, for the staleness the records themselves declare
     */
    public static ReadablePage.Board from(Collection<String> records, long nowMs) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        List<Row> rows = new ArrayList<>();
        for (String json : records) {
            Row row = decode(json);
            if (row != null) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            return null;
        }

        // C-26 applied to this board too: the EARLIEST expiry present IS the board. A later
        // expiry's rows belong to a different board and are not mixed into this one.
        String expiry = null;
        for (Row r : rows) {
            if (expiry == null || r.expiry().compareTo(expiry) < 0) {
                expiry = r.expiry();
            }
        }

        // Newest record per strike, and the newest record overall for the board's spot and
        // instant: a pre-open strike updates repeatedly and the log is delete-only, so several
        // values for one strike are the ordinary case rather than a fault.
        Map<Long, Row> byCents = new HashMap<>();
        Row newest = null;
        for (Row r : rows) {
            if (!expiry.equals(r.expiry())) {
                continue;
            }
            long cents = Math.round(r.strike() * 100.0);
            Row held = byCents.get(cents);
            if (held == null || r.updatedAtMs() > held.updatedAtMs()) {
                byCents.put(cents, r);
            }
            if (newest == null || r.updatedAtMs() > newest.updatedAtMs()) {
                newest = r;
            }
        }
        if (newest == null) {
            return null;
        }

        double spot = newest.spot();
        List<String> lines = new ArrayList<>();
        int present = 0;
        int expected = 0;
        double centre = Math.round(spot / BoardLines.GRID_STEP) * BoardLines.GRID_STEP;
        int steps = (int) Math.round(BoardLines.GRID_RADIUS / BoardLines.GRID_STEP);
        for (int i = -steps; i <= steps; i++) {
            double k = centre + i * BoardLines.GRID_STEP;
            expected++;
            String marker = i == 0 ? "|spot" : "";
            Row r = byCents.get(Math.round(k * 100.0));
            if (r == null) {
                lines.add(BoardLines.strikeOf(k) + "||norow" + marker);
                continue;
            }
            present++;
            // The read cell is deliberately empty: this board has no what-if reading to put in it.
            lines.add(BoardLines.strikeOf(k)
                    + "|" + (r.netGex() == null ? "" : BoardLines.money(r.netGex()))
                    + "|"
                    + staleMark(r, nowMs)
                    + signMismatch(r)
                    + marker);
        }
        return new ReadablePage.Board(newest.updatedAtMs(), spot, present, expected,
                sourceFor(newest, nowMs),
                Extremes.largestOfRows(byCents, spot, true),
                Extremes.largestOfRows(byCents, spot, false),
                lines);
    }

    /**
     * The producer disagreeing with itself is a fact the reader needs, not something to smooth
     * over: {@code gammaSign} is checked against the sign of {@code netGex} and a disagreement is
     * marked on the row. Agreement adds nothing, because the printed figure already carries a sign.
     */
    private static String signMismatch(Row r) {
        if (r.netGex() == null || r.gammaSign() == null) {
            return "";
        }
        boolean saysPositive = "POSITIVE".equals(r.gammaSign());
        boolean saysNegative = "NEGATIVE".equals(r.gammaSign());
        if (!saysPositive && !saysNegative) {
            return "";
        }
        if (r.netGex() > 0 && saysNegative || r.netGex() < 0 && saysPositive) {
            return "|signmismatch=" + r.gammaSign();
        }
        return "";
    }

    /**
     * <b>C-73</b>: a board from an earlier session names that session, so it can never be read as
     * today's.
     *
     * <p>It is NOT withheld. Withholding would make the board disappear at midnight New York while
     * the overnight window was still running, and a board that vanishes tells the reader nothing
     * at all — whereas a board that says which session it belongs to tells him exactly what he is
     * looking at. The comparison is between the newest record's own New York date and the date
     * now, both measured, neither taken from a calendar.
     */
    private static String sourceFor(Row newest, long nowMs) {
        String recordDate = DAY.format(Instant.ofEpochMilli(newest.updatedAtMs()).atZone(NY));
        String today = DAY.format(Instant.ofEpochMilli(nowMs).atZone(NY));
        if (!recordDate.equals(today)) {
            return SOURCE + " (PREVIOUS SESSION " + recordDate + ")";
        }
        return SOURCE;
    }

    /**
     * <b>C-73</b>: a row past its OWN declared {@code staleAfterMs} is printed with a stale mark
     * and its age, and is never dropped.
     *
     * <p>Dropping it would turn a stale board into an empty one, and an empty board reads as "no
     * gamma at that strike" rather than "nobody has told me for a while". The bound is the
     * producer's own figure on that very record, not one chosen here, so a producer that changes
     * its mind about freshness is followed rather than argued with.
     */
    private static String staleMark(Row r, long nowMs) {
        long age = nowMs - r.updatedAtMs();
        if (age > r.staleAfterMs()) {
            return "|stale" + Math.max(1, age / 60_000L) + "m";
        }
        return "";
    }

    static Row decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode n = MAPPER.readTree(json);
            if (n == null || !n.isObject()) {
                return null;
            }
            if (!SYMBOL.equals(text(n, "symbol"))) {
                return null;          // not the owner's board
            }
            String expiry = text(n, "expiry");
            JsonNode strike = n.path("strike");
            JsonNode spot = n.path("canonicalSpot");
            String updatedAt = text(n, "updatedAt");
            if (expiry == null || !strike.isNumber() || !spot.isNumber() || updatedAt == null) {
                return null;
            }
            long updatedAtMs = Instant.parse(updatedAt).toEpochMilli();
            JsonNode net = n.path("netGex");
            JsonNode stale = n.path("staleAfterMs");
            return new Row(expiry, strike.doubleValue(),
                    net.isNumber() ? net.doubleValue() : null,
                    text(n, "gammaSign"), spot.doubleValue(), updatedAtMs,
                    stale.isNumber() && stale.longValue() > 0
                            ? stale.longValue() : DEFAULT_STALE_AFTER_MS);
        } catch (Exception malformed) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isTextual()) {
            return null;
        }
        String s = v.textValue();
        return s == null || s.isEmpty() ? null : s;
    }
}
