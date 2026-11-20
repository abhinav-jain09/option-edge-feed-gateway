package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * <b>C-71</b>: rebuild the session's path of sides at start-up, so a gateway restart does not lose
 * the day.
 *
 * <p>The tracker can only know what it has observed, so after a restart the session's crosses and
 * holding times were gone and the page would have read as a quiet session rather than a short
 * memory. The owner's rule from 00:33 is that a stall does not lose the day.
 *
 * <p><b>Each snapshot's VWAP is paired with the price of that same snapshot</b>, which is the only
 * comparison that is ever true. Pairing a price from three hours ago against the line as it stands
 * now would put that price on whichever side suits the present, and the crossing count would change
 * every time the line moved. The snapshot carries {@code currentPrice} beside
 * {@code asOfEventTime}, so nothing has to be borrowed from a neighbouring record.
 *
 * <p>Replayed in <b>event-time order</b>, not in the order the records arrive: the topic has one
 * partition but a restart can redeliver, and the tracker credits held time from one observation to
 * the next, so an out-of-order replay would produce negative stretches.
 */
public final class VwapRebuild {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VwapRebuild() {
    }

    /** One decoded moment: the price and the line as they both stood then. */
    record Moment(long atMs, double price, double vwap, Double upper1, Double lower1,
                  String anchor) {
    }

    /**
     * Replay the history into the tracker.
     *
     * @return how many moments were usable; the rest are counted by the caller's own reporting
     */
    public static int replay(VwapSideTracker tracker, Collection<String> snapshots) {
        if (tracker == null || snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        List<Moment> moments = new ArrayList<>();
        for (String json : snapshots) {
            Moment m = decode(json);
            if (m != null) {
                moments.add(m);
            }
        }
        moments.sort(Comparator.comparingLong(Moment::atMs));
        for (Moment m : moments) {
            tracker.observe(m.atMs(), m.price(), m.vwap(), m.upper1(), m.lower1(), m.anchor());
        }
        return moments.size();
    }

    /**
     * One snapshot into a moment, or null where it cannot make one.
     *
     * <p>Refused rather than guessed at: a snapshot with no price of its own moment, or whose
     * holding anchor has no usable VWAP, cannot place price on a side. Counting it as ON the line
     * would be a measurement nobody made.
     */
    static Moment decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode n = MAPPER.readTree(json);
            if (n == null || !n.isObject()) {
                return null;
            }
            JsonNode priceNode = n.path("currentPrice");
            String at = n.path("asOfEventTime").isTextual()
                    ? n.path("asOfEventTime").textValue() : null;
            if (at == null) {
                return null;
            }
            Double price = asDouble(priceNode);
            if (price == null) {
                return null;
            }
            VwapRead read = VwapRead.of(json);
            if (read == null) {
                return null;
            }
            VwapRead.Anchor anchor = read.forHolding();
            if (anchor == null || !anchor.usable()) {
                return null;
            }
            return new Moment(Instant.parse(at).toEpochMilli(), price,
                    anchor.vwap().value(),
                    anchor.upper1() == null ? null : anchor.upper1().value(),
                    anchor.lower1() == null ? null : anchor.lower1().value(),
                    anchor.name());
        } catch (Exception malformed) {
            return null;
        }
    }

    /** The producer writes the price as a decimal STRING; a number is accepted too. */
    private static Double asDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.textValue().trim());
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
        return null;
    }
}
