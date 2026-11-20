package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The gamma block of the readable page, built from the map service's <b>own published record</b>.
 *
 * <p><b>This formats and does not decide</b> (C-63): the net gamma, its sign and the hold-or-
 * accelerate reading are read off the record exactly as the map service published them. Nothing
 * here recomputes a reading, and nothing applies a basis — the record's prices are already SPX.
 *
 * <p><b>Every grid strike gets a line, including the ones with no record.</b> The gamma exposure
 * service leaves gaps on the 5-point grid: ten of them on the last board of 2026-11-19, several
 * within 500 points of spot, with no row marked excluded. Nothing downstream can recover a strike
 * that never arrived, so a missing one is printed as {@code no row} rather than skipped. A skipped
 * strike is an invisible hole in a ladder somebody trades off; a printed {@code no row} is a fact.
 *
 * <p>That is also why the heading carries {@code rows N of M}: the first pre-market boards are thin
 * in the same way, and the owner should see how complete his board is without counting lines.
 */
public final class BoardLines {

    /** The source this board names itself by: the map service's own published record. */
    public static final String SOURCE = "whatif-map";

    /** The owner's grid: 5-point strikes within 100 points of spot, his strike included. */
    public static final double GRID_STEP = 5.0;
    public static final double GRID_RADIUS = 100.0;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BoardLines() {
    }

    /**
     * Build the block, or null where there is no board to build it from.
     *
     * @param mapJson the map service's record as the gateway cached it
     * @param cycleMs the record's own cycle instant, for the heading (C-60 addendum d: a board
     *                without its own time reads as today's)
     */
    public static ReadablePage.Board from(String mapJson, long cycleMs) {
        return from(mapJson, cycleMs, null, 0L);
    }

    /**
     * <b>C-120</b>: the same board, with each strike's published call delta and put delta on its
     * row.
     *
     * @param deltas the published per-strike deltas, or null where the tap is not wired. Never a
     *               delta computed here: C-103 holds and this page carries the producer's figures
     * @param nowMs  the render instant, for a row's age. Zero leaves the age off, which is what a
     *               caller that has no clock should produce rather than a wrong figure
     */
    public static ReadablePage.Board from(String mapJson, long cycleMs,
            StrikeDeltaSource deltas, long nowMs) {
        JsonNode root = parse(mapJson);
        if (root == null) {
            return null;
        }
        JsonNode spotNode = root.path("spot");
        if (!spotNode.isNumber()) {
            return null;
        }
        double spot = spotNode.doubleValue();
        // C-120: the board's OWN expiry, so a delta is only ever taken from the chain this board
        // is. The topic carries today's 0DTE and the next session's together, and the first live
        // render of this change put the NEXT session's delta on a 0DTE board.
        String boardExpiry = text(root, "expiry");
        JsonNode strikes = root.path("strikes");
        if (!strikes.isArray()) {
            return null;
        }
        Map<Long, JsonNode> byCents = new LinkedHashMap<>();
        for (JsonNode s : strikes) {
            JsonNode cents = s.path("strikeCents");
            if (cents.isNumber()) {
                byCents.put(cents.longValue(), s);
            }
        }

        // Walk the grid rather than the record, so a gap is a printed line and not an absence.
        //
        // The grid is CENTRED on spot's nearest 5-point strike and runs a fixed 20 steps either
        // side, so it is always exactly 41 rows. Taking instead every grid strike inside a
        // 100-point window would give 40 or 41 depending on where spot happened to sit between two
        // lines, and then "rows 31 of 41" -- the figure the owner reads to judge how complete his
        // board is -- would silently change denominator from one cycle to the next.
        List<String> lines = new ArrayList<>();
        int present = 0;
        int expected = 0;
        double centre = Math.round(spot / GRID_STEP) * GRID_STEP;
        int steps = (int) Math.round(GRID_RADIUS / GRID_STEP);
        for (int i = -steps; i <= steps; i++) {
            double k = centre + i * GRID_STEP;
            expected++;
            long cents = Math.round(k * 100.0);
            JsonNode s = byCents.get(cents);
            // C-66's compact form: strike|netgamma|read, no padding, no alignment, an empty
            // cell is nothing between two separators, and the reading is a one-letter code the
            // page's legend expands. The spot row is marked with a trailing field rather than a
            // column, so 40 of the 41 rows do not carry an empty one.
            String marker = i == 0 ? "|spot" : "";
            if (s == null) {
                lines.add(strikeOf(k) + "||norow"
                        + deltaCells(deltas, boardExpiry, cents, nowMs) + marker);
                continue;
            }
            present++;
            JsonNode board = s.path("board");
            JsonNode net = board.path("netGex");
            String excluded = text(board, "excludedReason");
            lines.add(strikeOf(k)
                    + "|" + (net.isNumber() ? money(net.doubleValue()) : "")
                    + "|" + code(text(board, "holdOrAccelerate"))
                    + (excluded == null ? "" : "|excluded=" + excluded)
                    + deltaCells(deltas, boardExpiry, cents, nowMs)
                    + deltaBandCells(s.path("deltaBand"))
                    + marker);
        }
        return new ReadablePage.Board(cycleMs, spot, present, expected, SOURCE,
                Extremes.largest(byCents, spot, true), Extremes.largest(byCents, spot, false),
                lines, strikes.size(), holdOf(root),
                byCents.isEmpty() ? -1L : java.util.Collections.min(byCents.keySet()),
                byCents.isEmpty() ? -1L : java.util.Collections.max(byCents.keySet()));
    }

    /**
     * <b>R-B-9 / C-116</b>: the record's own {@code boardHold} block, or null where it is live.
     *
     * <p>Read off the record as JSON, like everything else here. The map service decides whether a
     * board is held and says so in the record; this page's only job is not to draw a held board as
     * a fresh one, which is C-40's fault over again and the reason the reviewer asked for this line.
     */
    static ReadablePage.Board.Hold holdOf(JsonNode root) {
        JsonNode hold = root.path("boardHold");
        if (hold.isMissingNode() || hold.isNull() || !hold.isObject()) {
            return null;                    // absent means live, which is the normal case
        }
        JsonNode age = hold.path("ageMs");
        String reason = text(hold, "reason");
        if (!age.isNumber() || reason == null) {
            // A hold block that does not say how old it is or why cannot be printed honestly, and
            // printing the board without it would be the very thing this exists to prevent.
            return new ReadablePage.Board.Hold(-1L, reason == null ? "held, reason absent" : reason,
                    -1);
        }
        JsonNode soFar = hold.path("strikesSoFar");
        return new ReadablePage.Board.Hold(age.longValue(), reason,
                soFar.isNumber() ? soFar.intValue() : -1);
    }

    /**
     * <b>C-120</b>: {@code |cd=0.423|pd=-0.577|age=12s} for one strike.
     *
     * <p><b>A strike with no delta published prints the reason, never an empty cell</b>, as the
     * reviewer asked. Three cases, kept apart because they are three different facts: no record
     * for this strike at all ({@code norec}); a record that carries one side and not the other,
     * where the producer's own {@code callInvalidReason} / {@code putInvalidReason} is printed if
     * it gave one and {@code none} if it did not; and a record carrying both, which is the normal
     * case.
     *
     * <p>The age is the render instant against the record's own {@code updatedAtMs}, and it is
     * left off entirely where either is unknown rather than printed as zero. Three decimals,
     * because the owner is reading for "close to one" and the fourth would only lengthen a line
     * that must stay under 125 characters.
     */
    static String deltaCells(StrikeDeltaSource deltas, String boardExpiry, long strikeCents,
            long nowMs) {
        if (deltas == null) {
            return "";                  // the tap is not wired: no cells at all, not empty ones
        }
        StrikeDeltaSource.Deltas d = deltas.at(boardExpiry, strikeCents);
        if (d == null) {
            return "|cd=norec|pd=norec";
        }
        StringBuilder out = new StringBuilder(32);
        out.append("|cd=").append(deltaCell(d.call(), d.reasonFor(true)));
        out.append("|pd=").append(deltaCell(d.put(), d.reasonFor(false)));
        if (nowMs > 0L && d.updatedAtMs() > 0L) {
            out.append("|age=").append(ageCell(Math.max(0L, nowMs - d.updatedAtMs())));
        }
        return out.toString();
    }

    /**
     * A delta row's age, to the SECOND under a minute.
     *
     * <p>{@link StalledInputs#ago} is not reused here: it rounds to whole minutes, which is right
     * for "this input has stalled" and wrong for this cell. The owner reads these deltas while a
     * stab is in progress, and 12 seconds and 50 seconds are the difference between a figure he
     * can act on and one he cannot &mdash; both of which {@code ago} prints as {@code 0m}. Above a
     * minute the figure is already too old to trade off, so minutes are enough there.
     */
    static String ageCell(long ms) {
        long seconds = ms / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "m";
        }
        return String.format(Locale.ROOT, "%dh%02dm", minutes / 60L, minutes % 60L);
    }

    /** One delta cell: the figure as published, or the producer's reason it has none. */
    private static String deltaCell(Double delta, String reason) {
        if (delta != null) {
            return String.format(Locale.ROOT, "%.3f", delta);
        }
        return reason == null ? "none" : reason;
    }

    /**
     * <b>C-122</b>: the two edges of the strike's delta band, as the map record publishes them:
     * {@code |d95=7042.50|d05=6975.00}.
     *
     * <p><b>These are PRICES, not deltas</b>, and that is the point of them. The owner's reading of
     * a stab is how far price has to travel to bring the strike's delta close to one, and that
     * distance widens with volatility and time to expiry. {@code upperEdge} is the price at which
     * this strike's delta reaches the band's upper threshold and {@code lowerEdge} the price at
     * which it reaches the lower one, both computed by the map service from the strike's own IV and
     * its time to expiry (R-PUB-1 row d). So the cell is the distance he measures in points, from
     * the one service that holds the model for it.
     *
     * <p><b>The names come from the record's own thresholds and are not assumed.</b> The record
     * publishes {@code lowerThreshold} and {@code upperThreshold} precisely so a reader can
     * re-derive the edges, so a cell called {@code d95} where the record said 0.90 would be a
     * figure wearing the wrong name. Where they are the document's 0.05 and 0.95 the cells read
     * {@code d05} and {@code d95}; where they are anything else the names carry the published
     * numbers.
     */
    static String deltaBandCells(JsonNode band) {
        if (band == null || band.isMissingNode() || band.isNull() || !band.isObject()) {
            return "";              // no band published for this strike: no cells, not empty ones
        }
        JsonNode upper = band.path("upperEdge");
        JsonNode lower = band.path("lowerEdge");
        if (!upper.isNumber() && !lower.isNumber()) {
            return "";
        }
        String upperName = thresholdName(band.path("upperThreshold"), "d95");
        String lowerName = thresholdName(band.path("lowerThreshold"), "d05");
        return "|" + upperName + "=" + (upper.isNumber() ? edgeOf(upper.doubleValue()) : "")
                + "|" + lowerName + "=" + (lower.isNumber() ? edgeOf(lower.doubleValue()) : "");
    }

    /**
     * An edge price to the cent.
     *
     * <p>Deliberately NOT {@link #strikeOf}: that formatter rounds to one decimal, which is exact
     * for a strike on the owner's 5-point grid and silently drops a cent from an edge the map
     * service computed from the strike's IV. These two cells are prices he measures a distance
     * with, so they keep the precision the record published.
     */
    private static String edgeOf(double price) {
        return String.format(Locale.ROOT, "%.2f", price);
    }

    /** {@code d95} where the threshold is 0.95, else the published threshold's own digits. */
    private static String thresholdName(JsonNode threshold, String expected) {
        if (!threshold.isNumber()) {
            return expected + "?";          // the edge is published and the threshold is not
        }
        long hundredths = Math.round(threshold.doubleValue() * 100.0);
        return "d" + String.format(Locale.ROOT, "%02d", hundredths);
    }

    /**
     * Net gamma in the units a reader of the owner's board uses: billions and millions, signed.
     * The record's own figure is passed through unscaled anywhere it is not abbreviated.
     */
    static String money(double v) {
        double abs = Math.abs(v);
        if (abs >= 1e9) {
            return String.format(Locale.ROOT, "%+.2fB", v / 1e9);
        }
        if (abs >= 1e6) {
            return String.format(Locale.ROOT, "%+.0fM", v / 1e6);
        }
        return String.format(Locale.ROOT, "%+.0f", v);
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

    /** A strike with no decimals where it has none, so a row costs as little as possible. */
    static String strikeOf(double k) {
        return k == Math.rint(k) ? String.valueOf((long) k) : String.format(Locale.ROOT, "%.1f", k);
    }

    /**
     * The reading as the one-letter code C-66's legend expands. An unrecognised word is passed
     * through whole rather than mapped to a letter that would be a guess.
     */
    static String code(String reading) {
        if (reading == null) {
            return "";
        }
        switch (reading) {
            case "ACCELERATE":
                return "A";
            case "HOLD":
                return "H";
            case "NEUTRAL":
                return "N";
            default:
                return reading;
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
}
