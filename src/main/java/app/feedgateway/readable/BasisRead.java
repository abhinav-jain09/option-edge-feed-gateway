package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <b>C-69</b>: the platform's own ES-to-SPX basis, read from {@code spx.basis.state}.
 *
 * <p><b>Read and labelled, never recomputed.</b> This service does not derive a basis and does not
 * hold the rule for one. It reads the level the platform publishes, prints it beside the figure it
 * translated, and names the state it was published under — so a reader can always see which basis
 * a translated number rests on. C-69 is explicit that this is the only place the basis enters, and
 * only for display: the side, the crosses and the holding times are computed ES against ES.
 *
 * <p><b>C-101 item 2: TWO bases on the line.</b> The published level is one thing; the level the
 * bars were actually translated with is another, and they drift apart. The page prints
 * {@code basis|state=19.26 ANCHORED@16:00|bars=18.30} so a reader can see both and see the gap,
 * and the legend says which of them each page-computed figure used. The bars' figure is a
 * subtraction of two numbers one bar already carries ({@code closeCents} and
 * {@code spxEqCloseCents}), so it is the producer's own basis read back, not a second basis of
 * ours.
 *
 * <p><b>The topic is PLAIN JSON, not Avro.</b> A Confluent-framed reader returns nothing from it.
 * That has caught this estate before, so it is stated here rather than discovered again.
 *
 * <p><b>A basis that is not {@code ANCHORED} does not translate anything.</b> {@link
 * #usableForDisplay()} is false for any other state, and the page then leaves its SPX-equivalent
 * cells empty with the state as the reason while the ES figures stay. Translating under a
 * quarantined basis would put every level a few points out in silence, which is worse than leaving
 * the cell blank.
 */
public final class BasisRead {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The state the platform itself calls settled. */
    public static final String ANCHORED = "ANCHORED";

    private final double level;
    private final String state;
    private final boolean nextSessionQuarantinedOpen;
    private final long asOfMs;

    private BasisRead(double level, String state, boolean nextSessionQuarantinedOpen,
                      long asOfMs) {
        this.level = level;
        this.state = state;
        this.nextSessionQuarantinedOpen = nextSessionQuarantinedOpen;
        this.asOfMs = asOfMs;
    }

    /**
     * <b>C-101 item 2</b>: the instant this level refers to, from the record's own
     * {@code valueReferenceMs}, or 0 where it carries none.
     *
     * <p>An anchored basis is a level taken at a moment and held, so a level with no time beside it
     * cannot be judged: 19.26 is the right answer at 16:00 and a guess by the next afternoon. The
     * reviewer asked for the time on the line for exactly that reason.
     */
    public long asOfMs() {
        return asOfMs;
    }

    /** The level, in SPX points. <b>SPX-equivalent = ES minus this.</b> */
    public double level() {
        return level;
    }

    /** The producer's own word for it, e.g. {@code ANCHORED}. */
    public String state() {
        return state;
    }

    /** The producer's warning that the next open's basis is suspect. Printed, never acted on here. */
    public boolean nextSessionQuarantinedOpen() {
        return nextSessionQuarantinedOpen;
    }

    /** True only where the state is {@code ANCHORED}: anything else translates nothing. */
    public boolean usableForDisplay() {
        return ANCHORED.equals(state);
    }

    /**
     * Translate an ES price to its SPX equivalent for display, or null where the basis is not
     * usable — so a caller cannot get a translated number without the basis having said it may.
     */
    public Double spxEquivalentOf(Double esPrice) {
        if (esPrice == null || !usableForDisplay()) {
            return null;
        }
        return esPrice - level;
    }

    /** The compact cell for the page: the level and its state, e.g. {@code 19.26 ANCHORED}. */
    public String cell() {
        return String.format(java.util.Locale.ROOT, "%.2f", level) + " "
                + (state == null ? "?" : state);
    }

    /**
     * <b>C-101 item 2</b>: the cell with the instant the level refers to, e.g.
     * {@code 19.26 ANCHORED@16:00}.
     */
    public String cellWithTime() {
        if (asOfMs <= 0L) {
            return cell() + "@?";
        }
        return cell() + "@"
                + ReadablePage.hms(asOfMs, ReadablePage.NY).substring(0, 5);
    }

    /**
     * <b>C-101 item 2</b>: the basis the BARS imply, from the newest bar carrying both prices.
     *
     * <p><b>This is a subtraction of two figures one bar already holds</b>, not a basis of our own:
     * {@code closeCents} and {@code spxEqCloseCents} are both the producer's, and their difference
     * is the basis the producer itself used for that bar. C-103 forbids deriving the basis; it does
     * not forbid reading back the one the platform applied, and the whole point of printing it is
     * that a reader can see when the published level and the level actually in the bars have drifted
     * apart. On 2026-11-20 they were 19.26 and about 18.30.
     *
     * <p>Taken from the ROWS the page is about to show rather than from the bars held upstream, so
     * the figure on the line is the basis in the data on the screen and cannot disagree with it.
     *
     * @return the bars' own basis in SPX points, or null where no shown row carries both prices
     */
    public static Double fromRows(java.util.List<RecentRows.Row> rows) {
        if (rows == null) {
            return null;
        }
        RecentRows.Row newest = null;
        for (RecentRows.Row row : rows) {
            if (row == null || row.price() == null || row.futuresPrice() == null) {
                continue;       // only a row carrying BOTH can say what basis was applied to it
            }
            if (newest == null || row.startMs() > newest.startMs()) {
                newest = row;
            }
        }
        return newest == null ? null : newest.futuresPrice() - newest.price();
    }

    /** Read the record, or null where it carries no usable level. */
    public static BasisRead of(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode lvl = root.path("level");
            Double level = null;
            if (lvl.isNumber()) {
                level = lvl.doubleValue();
            } else if (lvl.isTextual()) {
                try {
                    level = Double.parseDouble(lvl.textValue().trim());
                } catch (NumberFormatException ignored) {
                    level = null;
                }
            }
            if (level == null) {
                return null;
            }
            JsonNode kind = root.path("levelKind");
            String state = kind.isTextual() ? kind.textValue() : null;
            if (state == null) {
                JsonNode s = root.path("state");
                state = s.isTextual() ? s.textValue() : null;
            }
            long asOf = root.path("valueReferenceMs").asLong(0L);
            if (asOf <= 0L) {
                // The anchor carries the same instant, and a record that states it in one place
                // and not the other is the shape this fallback exists for.
                asOf = root.path("trustedAnchor").path("referenceMs").asLong(0L);
            }
            return new BasisRead(level, state,
                    root.path("nextSessionQuarantinedOpen").asBoolean(false), asOf);
        } catch (Exception e) {
            return null;
        }
    }
}
