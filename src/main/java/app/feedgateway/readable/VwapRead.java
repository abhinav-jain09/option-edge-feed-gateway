package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <b>C-69</b>: the platform's own VWAP and its bands, read from
 * {@code es.options.indicators.snapshot.current}.
 *
 * <p><b>Every figure carries a state, and the state travels with it.</b> This is the whole reason
 * this is a record and not six doubles. Measured on Dev on 2026-11-20: the ES session VWAP read
 * {@code 7187.0082} with {@code state=INCOMPLETE}, {@code reason=DEADLINE_FINALIZED},
 * {@code incompleteKind=LATCHED} — a latched figure, not a live one — while on the SPX
 * instrument every VWAP field read {@code state=ABSENT}, {@code reason=SOURCE_HAS_NO_VOLUME},
 * because an index has no volume and a volume-weighted average needs some. A page that printed the
 * number alone would have shown a latched or absent VWAP as a live one, which is the single most
 * misleading thing it could do to someone deciding where price sits.
 *
 * <p><b>These are ES prices.</b> C-69 settles what to do with that: the side, the crosses and the
 * holding times are computed <b>ES against ES</b>, like with like, so no basis arithmetic is
 * involved in anything that decides something; the SPX-equivalent is a display cell only, carried
 * beside the platform's own published basis level and its state.
 *
 * <p><b>The value is a decimal string</b> ({@code "7187.0082"}), so it is parsed and not cast.
 */
public final class VwapRead {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One figure with the producer's own verdict on it.
     *
     * @param value  the number, or null where the producer published none
     * @param state  {@code COMPLETE}, {@code INCOMPLETE}, {@code ABSENT}, {@code WARMING_UP}, ...
     * @param reason why, in the producer's words, e.g. {@code SOURCE_HAS_NO_VOLUME}
     * @param kind   {@code incompleteKind}, e.g. {@code LATCHED}, or null
     */
    public record Figure(Double value, String state, String reason, String kind) {

        /** True only where the producer calls it complete: anything else is qualified. */
        public boolean complete() {
            return "COMPLETE".equals(state);
        }

        /**
         * The compact cell: the number, then its state code where the state is not complete.
         *
         * <p>A figure is never printed bare unless the producer called it complete, and an absent
         * figure prints its reason rather than an empty cell, because "no VWAP" and "a VWAP the
         * service could not finish" are different facts.
         */
        public String cell() {
            if (value == null) {
                return state == null ? "" : shortCode();
            }
            return complete()
                    ? String.format(java.util.Locale.ROOT, "%.2f", value)
                    : String.format(java.util.Locale.ROOT, "%.2f", value) + "(" + shortCode() + ")";
        }

        /** The state as a short code for the page, with the reason where it adds something. */
        private String shortCode() {
            String s = state == null ? "?" : state;
            if ("INCOMPLETE".equals(s) && kind != null) {
                return kind;                       // LATCHED says more than INCOMPLETE
            }
            if ("ABSENT".equals(s) && reason != null) {
                return reason;                     // SOURCE_HAS_NO_VOLUME is the useful word
            }
            return s;
        }
    }

    /** One anchored VWAP with its two band pairs. */
    public record Anchor(String name, Figure vwap, Figure stdDev,
                         Figure upper1, Figure lower1, Figure upper2, Figure lower2) {

        /** True where this anchor can place price at all. */
        public boolean usable() {
            return vwap != null && vwap.value() != null;
        }
    }

    private final Anchor session;
    private final Anchor rth;

    private VwapRead(Anchor session, Anchor rth) {
        this.session = session;
        this.rth = rth;
    }

    /** {@code session.vwapAnalysis} — anchored at the session. */
    public Anchor session() {
        return session;
    }

    /** {@code session.vwapRth} — anchored at the regular-hours open. */
    public Anchor rth() {
        return rth;
    }

    /**
     * Which anchor the holding line is computed against (C-69): {@code vwapRth} once it has left
     * {@code WARMING_UP}, {@code vwapAnalysis} at all other times. The caller prints its name, so a
     * reader is never left guessing which line the holding times belong to.
     */
    public Anchor forHolding() {
        if (rth != null && rth.usable() && !"WARMING_UP".equals(rth.vwap().state())) {
            return rth;
        }
        return session;
    }

    /** Read the snapshot, or null where it carries no VWAP block at all. */
    public static VwapRead of(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode s = root.path("session");
            if (s.isMissingNode() || s.isNull()) {
                return null;
            }
            Anchor session = anchor("vwapAnalysis", s.path("vwapAnalysis"));
            Anchor rth = anchor("vwapRth", s.path("vwapRth"));
            return session == null && rth == null ? null : new VwapRead(session, rth);
        } catch (Exception e) {
            return null;
        }
    }

    private static Anchor anchor(String name, JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return new Anchor(name,
                figure(node.path("vwap")),
                figure(node.path("stdDev")),
                figure(node.path("bandUpper1")),
                figure(node.path("bandLower1")),
                figure(node.path("bandUpper2")),
                figure(node.path("bandLower2")));
    }

    private static Figure figure(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        Double value = null;
        JsonNode v = node.path("value");
        if (v.isNumber()) {
            value = v.doubleValue();
        } else if (v.isTextual()) {
            try {
                value = Double.parseDouble(v.textValue().trim());
            } catch (NumberFormatException ignored) {
                value = null;                      // a value we cannot parse is not a value
            }
        }
        return new Figure(value, text(node, "state"), text(node, "reason"),
                text(node, "incompleteKind"));
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
