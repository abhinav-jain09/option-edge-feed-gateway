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

    private BasisRead(double level, String state, boolean nextSessionQuarantinedOpen) {
        this.level = level;
        this.state = state;
        this.nextSessionQuarantinedOpen = nextSessionQuarantinedOpen;
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
            return new BasisRead(level, state,
                    root.path("nextSessionQuarantinedOpen").asBoolean(false));
        } catch (Exception e) {
            return null;
        }
    }
}
