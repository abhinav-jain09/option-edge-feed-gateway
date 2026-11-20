package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;

/**
 * The map record's own cycle instant, read from its fingerprint.
 *
 * <p>Separate from {@link BoardLines} because it answers a different question: not what the board
 * says, but <b>when the board was</b>. C-60's addendum (d) requires the heading to carry it, for
 * the reason the owner hit — a board without its own time reads as today's, and yesterday's last
 * board shown without a date is the most misleading thing this page could contain.
 */
final class BoardCycle {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BoardCycle() {
    }

    /** The cycle instant in epoch milliseconds, or null where the record does not carry one. */
    static Long epochMsOf(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode fp = root.path("fingerprint");
            for (String field : new String[] {"valuationTimeMs", "valuationTime", "asOfMs"}) {
                JsonNode v = fp.path(field);
                if (v.isNumber()) {
                    return v.longValue();
                }
                if (v.isTextual()) {
                    try {
                        return Instant.parse(v.textValue()).toEpochMilli();
                    } catch (Exception ignored) {
                        // not an instant; try the next field rather than guessing
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
