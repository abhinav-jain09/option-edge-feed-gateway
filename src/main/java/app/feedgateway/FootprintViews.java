package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R4/G-R5/G-R6/G-R7/G-R8 — the ONE session coordinator that owns
 * BOTH keyed footprint views (closed bars, outcome resolutions) under ONE lock.
 *
 * <p>Keys are fixed-width so {@code TreeMap} string order IS the tuple order over the whole epoch
 * domain: bars {@code tf|%019d(barStartMs)}, outcomes {@code tf|%019d(resolvedAtBarStartMs)|identity}
 * (the identity contains {@code |} and is therefore the LAST component). Rollover is MONOTONIC on
 * the record's strict canonical {@code YYYY-MM-DD} session date, parsed BEFORE any mutation: a newer
 * date clears both views, an older date is dropped, an invalid or missing date is a shape drop. Byte
 * and count budgets are enforced at every admission by evicting the oldest key per timeframe,
 * round-robin across timeframes. Every read (hello snapshot, page) is ONE snapshot under the lock; the
 * lock is never held while anything is written to a client.
 */
final class FootprintViews {

    enum Reason { ADMITTED, OVERSIZE, SHAPE, STALE_SESSION }

    /** The outcome of one admission: the reason, whether it rolled the session, how many keys were evicted. */
    record Admission(Reason reason, boolean rolledOver, int evicted) {
        static final Admission OVERSIZE = new Admission(Reason.OVERSIZE, false, 0);
        static final Admission SHAPE = new Admission(Reason.SHAPE, false, 0);
        static final Admission STALE = new Admission(Reason.STALE_SESSION, false, 0);
    }

    /** One ATOMIC bars page: session stamp, mismatch flag, rows and the numeric cursor (last barStartMs). */
    record BarsPage(String sessionDate, boolean sessionMismatch, List<String> records, Long nextCursor) { }

    /** One ATOMIC outcomes page: the cursor is the OPAQUE last view key (G-R7 grammar). */
    record OutcomesPage(String sessionDate, boolean sessionMismatch, List<String> records, String nextCursor) { }

    static final Set<String> TIMEFRAMES = Set.of("30s", "1m", "5m", "15m", "30m", "4h", "session");
    static final long EPOCH_MAX_MS = 253_402_300_799_999L;
    static final int IDENTITY_MAX_BYTES = 128;
    private static final Pattern CANONICAL_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern IDENTITY = Pattern.compile("^[A-Za-z0-9_.:|-]{1,128}$");
    private static final Pattern TIMEFRAME_KEY = Pattern.compile("^(30s|1m|5m|15m|30m|4h|session)$");
    private static final Pattern OUTCOME_CURSOR = Pattern.compile("^(30s|1m|5m|15m|30m|4h|session)\\|\\d{19}\\|[A-Za-z0-9_.:|-]{1,128}$");

    private final ObjectMapper mapper;
    private final long maxRecordBytes;
    private final long barsMaxBytes;
    private final int barsMaxCount;
    private final long outcomesMaxBytes;
    private final int outcomesMaxCount;

    private final Object lock = new Object();
    private LocalDate sessionDate;                                   // null before the first admitted record
    private final TreeMap<String, String> bars = new TreeMap<>();
    private final TreeMap<String, String> outcomes = new TreeMap<>();
    private long barsBytes;
    private long outcomesBytes;
    private int barsEvictCursor;
    private int outcomesEvictCursor;
    private final AtomicLong barsEvictions = new AtomicLong();
    private final AtomicLong outcomesEvictions = new AtomicLong();
    private final AtomicLong rollovers = new AtomicLong();

    FootprintViews(ObjectMapper mapper, long maxRecordBytes, long barsMaxBytes, int barsMaxCount,
                   long outcomesMaxBytes, int outcomesMaxCount) {
        if (maxRecordBytes <= 0 || barsMaxBytes <= 0 || barsMaxCount <= 0 || outcomesMaxBytes <= 0 || outcomesMaxCount <= 0) {
            throw new IllegalArgumentException("footprint view budgets must be positive");
        }
        this.mapper = mapper;
        this.maxRecordBytes = maxRecordBytes;
        this.barsMaxBytes = barsMaxBytes;
        this.barsMaxCount = barsMaxCount;
        this.outcomesMaxBytes = outcomesMaxBytes;
        this.outcomesMaxCount = outcomesMaxCount;
    }

    // ---- admission --------------------------------------------------------------------------------

    /** Closed-bar record (G-R4): key {@code timeframe|observations.barStartMs}. */
    Admission admitBar(String json) {
        if (json == null) return Admission.SHAPE;
        int bytes = utf8Length(json);
        if (bytes > maxRecordBytes) return Admission.OVERSIZE;
        JsonNode root = parse(json);
        if (root == null) return Admission.SHAPE;
        LocalDate date = parseCanonicalDate(text(root, "sessionDate"));
        String tf = text(root, "timeframe");
        long start = epochField(root.path("observations"), "barStartMs");
        if (date == null || !TIMEFRAME_KEY.matcher(tf).matches() || start < 0) return Admission.SHAPE;
        String key = barKey(tf, start);
        synchronized (lock) {
            Boolean roll = rollover(date);
            if (roll == null) return Admission.STALE;
            String previous = bars.put(key, json);
            barsBytes += bytes - (previous == null ? 0 : utf8Length(previous));
            int evicted = enforce(bars, barsMaxBytes, barsMaxCount, true);
            return new Admission(Reason.ADMITTED, roll, evicted);
        }
    }

    /** Outcome record (G-R5): key {@code timeframe|resolvedAtBarStartMs|identity}. */
    Admission admitOutcome(String json) {
        if (json == null) return Admission.SHAPE;
        int bytes = utf8Length(json);
        if (bytes > maxRecordBytes) return Admission.OVERSIZE;
        JsonNode root = parse(json);
        if (root == null) return Admission.SHAPE;
        LocalDate date = parseCanonicalDate(text(root, "sessionDate"));
        String tf = text(root, "timeframe");
        String identity = text(root, "identity");
        long resolved = epochField(root, "resolvedAtBarStartMs");
        if (date == null || !TIMEFRAME_KEY.matcher(tf).matches() || resolved < 0
                || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {
            return Admission.SHAPE;
        }
        String key = outcomeKey(tf, resolved, identity);
        synchronized (lock) {
            Boolean roll = rollover(date);
            if (roll == null) return Admission.STALE;
            String previous = outcomes.put(key, json);
            outcomesBytes += bytes - (previous == null ? 0 : utf8Length(previous));
            int evicted = enforce(outcomes, outcomesMaxBytes, outcomesMaxCount, false);
            return new Admission(Reason.ADMITTED, roll, evicted);
        }
    }

    /**
     * Under the lock. Returns TRUE when the date rolled the session forward (both views cleared),
     * FALSE when it is the current (or first) session, NULL when it is older (drop).
     */
    private Boolean rollover(LocalDate date) {
        if (sessionDate == null) { sessionDate = date; return false; }
        int cmp = date.compareTo(sessionDate);
        if (cmp < 0) return null;
        if (cmp == 0) return false;
        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;
        sessionDate = date;
        rollovers.incrementAndGet();
        return true;
    }

    /**
     * Evict the OLDEST key of one timeframe at a time, ROTATING across the timeframes present (the
     * cursor persists across admissions), until the view fits both budgets — so a run of single
     * evictions spreads over every timeframe instead of draining the lexically first one. Under the lock.
     */
    private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {
        int evicted = 0;
        while (view.size() > maxCount || (isBars ? barsBytes : outcomesBytes) > maxBytes) {
            List<String> tfs = new ArrayList<>(timeframesIn(view));
            if (tfs.isEmpty()) break;                                 // nothing left to evict
            int cursor = isBars ? barsEvictCursor : outcomesEvictCursor;
            String tf = tfs.get(Math.floorMod(cursor, tfs.size()));
            if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;
            String oldest = view.ceilingKey(tf + "|");
            if (oldest == null || !oldest.startsWith(tf + "|")) continue;
            String removed = view.remove(oldest);
            long len = removed == null ? 0 : utf8Length(removed);
            if (isBars) barsBytes -= len; else outcomesBytes -= len;
            evicted++;
        }
        if (evicted > 0) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);
        return evicted;
    }

    private static Set<String> timeframesIn(TreeMap<String, String> view) {
        Set<String> out = new TreeSet<>();
        for (String tf : TIMEFRAMES) {
            String k = view.ceilingKey(tf + "|");
            if (k != null && k.startsWith(tf + "|")) out.add(tf);
        }
        return out;
    }

    // ---- reads (each ONE snapshot under the lock) --------------------------------------------------

    /** The {@code footprint} field of the hello (G-R6): {@code {"sessionDate":..,"hwm":{..},"outcomeHwm":{..}}}. */
    String helloField() {
        synchronized (lock) {
            StringBuilder sb = new StringBuilder("{\"sessionDate\":");
            sb.append(sessionDate == null ? "null" : "\"" + sessionDate + "\"");
            sb.append(",\"hwm\":"); appendHwm(sb, bars, false);
            sb.append(",\"outcomeHwm\":"); appendHwm(sb, outcomes, true);
            return sb.append('}').toString();
        }
    }

    private static void appendHwm(StringBuilder sb, TreeMap<String, String> view, boolean outcomes) {
        Map<String, Long> hwm = new LinkedHashMap<>();
        for (String tf : new String[]{"30s", "1m", "5m", "15m", "30m", "4h", "session"}) {
            String last = view.floorKey(tf + "|~");                   // '~' sorts above every digit and '|'
            if (last == null || !last.startsWith(tf + "|")) continue;
            hwm.put(tf, Long.parseLong(last.substring(tf.length() + 1, tf.length() + 20)));
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Long> e : hwm.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append('}');
    }

    /** Bars page (G-R7): ascending {@code barStartMs}, exclusive {@code afterMs}, inclusive {@code toMs}. */
    BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {
        synchronized (lock) {
            String current = sessionDate == null ? null : sessionDate.toString();
            if (mismatch(expectedSessionDate, current)) return new BarsPage(current, true, List.of(), null);
            List<String> out = new ArrayList<>();
            Long last = null;
            // Exclusive cursor at the domain edges (round-1 #2): a cursor at or past the last epoch
            // value can have nothing after it, and the increment is taken only inside the domain.
            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {
                long from = afterMsExclusive < 0 ? 0L : afterMsExclusive + 1L;
                String lo = barKey(tf, from);
                String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));
                if (lo.compareTo(hi) <= 0) {
                    for (Map.Entry<String, String> e : bars.subMap(lo, true, hi, true).entrySet()) {
                        out.add(e.getValue());
                        last = Long.parseLong(e.getKey().substring(tf.length() + 1));
                        if (out.size() >= limit) break;
                    }
                }
            }
            return new BarsPage(current, false, out, out.size() >= limit ? last : null);
        }
    }

    /**
     * Outcomes page (G-R7): ascending by view key within {@code tf}; {@code afterKey} is an EXCLUSIVE
     * lower bound (a previous page's cursor, already validated by {@link #validOutcomeCursor}); rows
     * whose {@code resolvedAtBarStartMs} exceeds {@code toMs} are excluded.
     */
    OutcomesPage outcomesPage(String tf, long toMsInclusive, String afterKey, int limit, String expectedSessionDate) {
        synchronized (lock) {
            String current = sessionDate == null ? null : sessionDate.toString();
            if (mismatch(expectedSessionDate, current)) return new OutcomesPage(current, true, List.of(), null);
            List<String> out = new ArrayList<>();
            String last = null;
            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0) {
                String prefix = tf + "|";
                Map<String, String> tail = afterKey == null || afterKey.isEmpty()
                        ? outcomes.tailMap(prefix, true) : outcomes.tailMap(afterKey, false);
                for (Map.Entry<String, String> e : tail.entrySet()) {
                    String k = e.getKey();
                    if (!k.startsWith(prefix)) break;
                    long resolved = Long.parseLong(k.substring(prefix.length(), prefix.length() + 19));
                    if (resolved > toMsInclusive) break;
                    out.add(e.getValue());
                    last = k;
                    if (out.size() >= limit) break;
                }
            }
            return new OutcomesPage(current, false, out, out.size() >= limit ? last : null);
        }
    }

    private static boolean mismatch(String expected, String current) {
        return expected != null && !expected.isEmpty() && current != null && !expected.equals(current);
    }

    /** G-R7 cursor grammar: {@code tf|<19 digits>|<identity>} with {@code tf} equal to the query's. */
    static boolean validOutcomeCursor(String tf, String cursor) {
        return cursor != null && OUTCOME_CURSOR.matcher(cursor).matches() && cursor.startsWith(tf + "|");
    }

    // ---- gauges -----------------------------------------------------------------------------------

    String sessionDate() { synchronized (lock) { return sessionDate == null ? null : sessionDate.toString(); } }
    int barsInView() { synchronized (lock) { return bars.size(); } }
    int outcomesInView() { synchronized (lock) { return outcomes.size(); } }
    long barsBytes() { synchronized (lock) { return barsBytes; } }
    long outcomesBytes() { synchronized (lock) { return outcomesBytes; } }
    long barsEvictions() { return barsEvictions.get(); }
    long outcomesEvictions() { return outcomesEvictions.get(); }
    long rollovers() { return rollovers.get(); }

    // ---- helpers ----------------------------------------------------------------------------------

    static String barKey(String tf, long barStartMs) { return tf + "|" + String.format("%019d", barStartMs); }

    static String outcomeKey(String tf, long resolvedAtBarStartMs, String identity) {
        return tf + "|" + String.format("%019d", resolvedAtBarStartMs) + "|" + identity;
    }

    /** Strict canonical {@code YYYY-MM-DD}: shape, calendar validity and round-trip equality; null otherwise. */
    static LocalDate parseCanonicalDate(String s) {
        if (s == null || !CANONICAL_DATE.matcher(s).matches()) return null;
        try {
            LocalDate d = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.toString().equals(s) ? d : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private JsonNode parse(String json) {
        try {
            JsonNode n = mapper.readTree(json);
            return n != null && n.isObject() ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode v = root == null ? null : root.get(field);
        return v == null || !v.isTextual() ? "" : v.asText();
    }

    /** An integral field inside the epoch-ms domain, or -1. */
    private static long epochField(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong()) return -1;
        long x = v.asLong();
        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;
    }

    static int utf8Length(String s) {
        int n = s.length();
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 127) return s.getBytes(StandardCharsets.UTF_8).length;
        return n;
    }
}
