package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>C-120</b>: the newest published call delta and put delta per strike, off
 * {@code options.databento.gex.strike}.
 *
 * <p><b>Why the owner asked for it.</b> A poke through a strike is not a fixed number of points:
 * it goes as far as it takes to bring that strike's delta close to one, and the distance widens
 * with volatility and time to expiry. He wants the stab read in delta terms, not in points. Today
 * 2026-11-20 three stabs under 7,100 went 13, 21 and 40 points; the first reversed fully and the
 * other two only came back to the strike.
 *
 * <p><b>Read, never computed</b> (C-103). The Databento GEX producer publishes {@code callDelta}
 * and {@code putDelta} on every strike record, and these are carried to the page unchanged. This
 * class holds no model, no Black-Scholes and no time-to-expiry arithmetic; if the producer stops
 * publishing a side's delta, the page says so rather than deriving one.
 *
 * <p><b>Only the live Databento topic.</b> The gateway binds the same {@code gex-by-strike} event
 * to four topics &mdash; the Databento live one, the Databento history one, and the IBKR live and
 * history ones. The caller gates on the live Databento topic by name, because a history row or
 * another producer's row mixed into the live board would be a wrong delta on a strike the owner is
 * trading, with nothing on the page to say which feed it came from.
 *
 * <p><b>Last-value-wins per strike, by the record's own time.</b> The topic can redeliver after a
 * restart, so a record older than the one held is refused rather than taken as the newest: "last
 * to arrive" is not "newest" on a log that can be re-read.
 *
 * <p><b>Keyed by EXPIRY and strike, and the board asks for its own expiry.</b> The topic carries
 * more than one expiry at a time &mdash; measured on 2026-11-20 at 18:01 it was serving the
 * 0DTE 20261120 chain and the 20261123 chain together, 170 strikes of the latter. A store keyed on
 * the strike alone, keeping "the newest expiry seen", put the NEXT session's delta on today's 0DTE
 * board: the first live render of this change showed exactly that, {@code expiry 20261123} under a
 * board whose own expiry was 20261120, and nothing on the page would have said so. The owner
 * trades the 0DTE. This is the same rule the map service's C-26 holds for its board, and it is
 * here for the same reason.
 *
 * <p><b>The two formats.</b> The gamma record spells the expiry {@code 20261120} and the map
 * record spells it {@code 2026-11-20}, so both are reduced to their digits before they are
 * compared. A lookup that silently missed on the punctuation would print {@code norec} on every
 * row and read as a producer that had stopped publishing deltas.
 */
public final class StrikeDeltaSource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * A ceiling on strikes held, to bound memory. Measured on 2026-11-20 the resolved 0DTE chain
     * carried 201 strikes and the next session's 167, so this never binds in practice; a record
     * refused at it is counted, because a strike that silently vanished would print as one with no
     * delta published and read as a producer fault.
     */
    static final int MAX_STRIKES = 8_192;

    /** One strike's published deltas, as the producer published them. */
    public record Deltas(long strikeCents, String expiry, Double call, Double put,
                         String callInvalidReason, String putInvalidReason, long updatedAtMs) {

        /** The producer's own reason this side has no usable figure, or null. */
        public String reasonFor(boolean callSide) {
            String reason = callSide ? callInvalidReason : putInvalidReason;
            return reason == null || reason.isBlank() ? null : reason;
        }
    }

    /**
     * How many expiries are kept. Two, because the topic carries today's 0DTE and the next
     * session's, and an unbounded map would refuse TODAY's strikes at the ceiling after a week.
     */
    static final int MAX_EXPIRIES = 2;

    private final Map<String, Deltas> byStrike = new ConcurrentHashMap<>();
    private final AtomicLong observed = new AtomicLong();
    private final AtomicLong unreadable = new AtomicLong();
    private final AtomicLong refusedAtCap = new AtomicLong();
    private final AtomicLong refusedOlder = new AtomicLong();
    private volatile String newestExpirySeen;

    /** Take one record off the live Databento strike topic. Nothing here throws. */
    public void observe(String json) {
        Deltas read = decode(json);
        if (read == null) {
            unreadable.incrementAndGet();
            return;
        }
        observed.incrementAndGet();
        String expiry = digitsOf(read.expiry());
        if (expiry != null) {
            String newest = newestExpirySeen;
            if (newest == null || expiry.compareTo(newest) > 0) {
                newestExpirySeen = expiry;
            }
        }
        String key = key(expiry, read.strikeCents());
        if (!byStrike.containsKey(key) && byStrike.size() >= MAX_STRIKES) {
            refusedAtCap.incrementAndGet();
            return;
        }
        byStrike.merge(key, read, (held, fresh) -> {
            if (fresh.updatedAtMs() < held.updatedAtMs()) {
                refusedOlder.incrementAndGet();
                return held;        // a redelivered older record is not the newest
            }
            return fresh;
        });
        pruneOldExpiries();
    }

    /**
     * Keep only the newest {@link #MAX_EXPIRIES} expiries.
     *
     * <p>Every expiry is kept while it is one of those, because the BOARD decides which one it
     * wants and this store does not: dropping an expiry because a later one appeared is what put
     * the next session's delta on today's 0DTE board in the first live render of this change.
     */
    private void pruneOldExpiries() {
        java.util.TreeSet<String> expiries = new java.util.TreeSet<>();
        for (String k : byStrike.keySet()) {
            int bar = k.indexOf('|');
            expiries.add(bar < 0 ? "" : k.substring(0, bar));
        }
        while (expiries.size() > MAX_EXPIRIES) {
            String oldest = expiries.pollFirst();
            byStrike.keySet().removeIf(k -> k.startsWith(oldest + "|"));
            pruned.incrementAndGet();
        }
    }

    private final AtomicLong pruned = new AtomicLong();

    /** How many expiries have been dropped for being older than the two newest. */
    public long prunedExpiries() {
        return pruned.get();
    }

    /**
     * This strike's published deltas FOR THIS EXPIRY, or null where none has been seen.
     *
     * @param expiry the board's own expiry in any spelling: {@code 20261120} and
     *               {@code 2026-11-20} are the same expiry, and the two producers spell it
     *               differently
     */
    public Deltas at(String expiry, long strikeCents) {
        return byStrike.get(key(digitsOf(expiry), strikeCents));
    }

    private static String key(String expiryDigits, long strikeCents) {
        return (expiryDigits == null ? "" : expiryDigits) + "|" + strikeCents;
    }

    /**
     * The expiry reduced to its digits, so {@code 2026-11-20} and {@code 20261120} compare equal.
     *
     * <p>The gamma producer spells it without punctuation and the map service's record spells it
     * with; a lookup that missed on that would print {@code norec} on every row and read as a
     * producer that had stopped publishing deltas.
     */
    static String digitsOf(String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return null;
        }
        StringBuilder out = new StringBuilder(8);
        for (int i = 0; i < expiry.length(); i++) {
            char c = expiry.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
            }
        }
        return out.length() == 0 ? null : out.toString();
    }

    /**
     * How many strikes are held for one expiry, in either spelling.
     *
     * <p>Published because "294 strikes held" says nothing about whether the BOARD's chain is one
     * of them, and that is the question the first live render of this change got wrong.
     */
    public int strikesForExpiry(String expiry) {
        String digits = digitsOf(expiry);
        if (digits == null) {
            return 0;
        }
        String prefix = digits + "|";
        int n = 0;
        for (String k : byStrike.keySet()) {
            if (k.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    /** How many strikes are held, for a readiness probe and the metrics page. */
    public int strikesHeld() {
        return byStrike.size();
    }

    public long observed() {
        return observed.get();
    }

    public long unreadable() {
        return unreadable.get();
    }

    public long refusedAtCap() {
        return refusedAtCap.get();
    }

    public long refusedOlder() {
        return refusedOlder.get();
    }

    /** The newest 0DTE expiry whose records have been seen, or null before the first one. */
    public String newestExpirySeen() {
        return newestExpirySeen;
    }

    /**
     * Read one record, or null where it is not one this can use.
     *
     * <p>A record with no strike cannot be placed and a record with neither delta carries nothing
     * this page wants, so both are refused and counted. A record with ONE delta is kept: the page
     * prints the side it has and says why the other is missing, which is more than a blank.
     */
    static Deltas decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            Long cents = strikeCentsOf(root);
            if (cents == null) {
                return null;
            }
            Double call = doubleOrNull(root, "callDelta");
            Double put = doubleOrNull(root, "putDelta");
            if (call == null && put == null) {
                return null;
            }
            long at = root.path("updatedAtMs").asLong(0L);
            if (at <= 0L) {
                at = root.path("kafkaPublishedAtMs").asLong(0L);
            }
            return new Deltas(cents, textOrNull(root, "expiry"), call, put,
                    textOrNull(root, "callInvalidReason"), textOrNull(root, "putInvalidReason"),
                    at);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The strike in cents.
     *
     * <p>The record carries {@code strike} as a double in points and {@code strikeCents} as a
     * long; the cents field is preferred where it is there, because rounding a double is this
     * estate's own arithmetic on a figure the producer already states exactly.
     */
    private static Long strikeCentsOf(JsonNode root) {
        JsonNode cents = root.path("strikeCents");
        if (cents.isNumber()) {
            return cents.longValue();
        }
        JsonNode strike = root.path("strike");
        if (strike.isNumber()) {
            return Math.round(strike.doubleValue() * 100.0);
        }
        return null;
    }

    private static Double doubleOrNull(JsonNode root, String field) {
        JsonNode v = root.path(field);
        if (v.isNumber()) {
            return v.doubleValue();
        }
        if (v.isTextual()) {
            try {
                return Double.parseDouble(v.textValue().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode v = root.path(field);
        if (!v.isTextual()) {
            return null;
        }
        String s = v.textValue();
        return s == null || s.isEmpty() ? null : s;
    }
}
