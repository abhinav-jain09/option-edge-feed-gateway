package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ES-FOOTPRINT-STRIKE-INTERACTION.md R14 — the gateway's fold of the {@code es.futures.footprint.strike}
 * log, the fifth footprint stream on the same relay path (R3): cache + live + backfill.
 *
 * <p>The fold is DEFINED, per identity {@code (symbol, sessionDate, timeframe, strikeCents,
 * openBarStartMs)}: the record with the greatest {@code revision} wins. Two records sharing an
 * identity AND a revision must be identical AS BYTES; if they are not, that identity is REFUSED for
 * the incarnation and counted — never one of two contradictory records silently picked. A CHECKPOINT
 * carries no episode and only advances the per-timeframe high-water mark the hello reports.
 *
 * <p>Unlike the bars/outcomes coordinator this view never rolls a session away: history deliberately
 * crosses sessions ("6800 may behave differently on every visit"), and {@code latest} is scoped to
 * ONE session by the reader. Budgets are enforced by evicting the OLDEST identities (by
 * {@code openBarStartMs}); {@link #historyBeginsAtMs} is then the explicit boundary the drill-down
 * shows instead of presenting a partial list as complete (R20). Every read is one snapshot under the
 * lock; the lock is never held while writing to a client.
 */
final class FootprintStrikeView {

    enum Reason { ADMITTED, OVERSIZE, SHAPE, COLLISION, REFUSED }

    record Admission(Reason reason, boolean checkpoint) {
        static final Admission OVERSIZE = new Admission(Reason.OVERSIZE, false);
        static final Admission SHAPE = new Admission(Reason.SHAPE, false);
        static final Admission COLLISION = new Admission(Reason.COLLISION, false);
        static final Admission REFUSED = new Admission(Reason.REFUSED, false);
        static final Admission CHECKPOINT = new Admission(Reason.ADMITTED, true);
        static final Admission EPISODE = new Admission(Reason.ADMITTED, false);
    }

    /** One folded identity: its parsed identity fields and the winning record's bytes. */
    private record Head(String identity, String symbol, String sessionDate, String tf, long strikeCents, long openBarStartMs,
                        long revision, String json) {}

    /** One page of folded records: ascending by strike for {@code latest}, newest first for {@code history}. */
    record Page(String sessionDate, List<String> records, String nextCursor, Long historyBeginsAtMs, long refused) {}

    static final long EPOCH_MAX_MS = 253_402_300_799_999L;
    static final int LATEST_LIMIT_MAX = 200, HISTORY_LIMIT_MAX = 100;

    private final ObjectMapper mapper;
    private final long maxRecordBytes, maxBytes;
    private final int maxEpisodes;

    private final Object lock = new Object();
    private final Map<String, Head> heads = new HashMap<>();
    private final Set<String> refused = new HashSet<>();
    /** tf → strike → (openKey → identity), openKey = sessionDate|%019d(openBarStartMs) so string order is time order. */
    private final Map<String, TreeMap<Long, TreeMap<String, String>>> index = new TreeMap<>();
    /** Eviction order: openBarStartMs|identity. */
    private final TreeMap<String, String> byAge = new TreeMap<>();
    private final Map<String, Long> hwm = new TreeMap<>();
    private long bytes;
    private String sessionDate;
    private final AtomicLong evictions = new AtomicLong(), collisions = new AtomicLong();

    FootprintStrikeView(ObjectMapper mapper, long maxRecordBytes, long maxBytes, int maxEpisodes) {
        if (maxRecordBytes <= 0 || maxBytes <= 0 || maxEpisodes <= 0) throw new IllegalArgumentException("strike view budgets must be positive");
        this.mapper = mapper; this.maxRecordBytes = maxRecordBytes; this.maxBytes = maxBytes; this.maxEpisodes = maxEpisodes;
    }

    // ---- admission --------------------------------------------------------------------------------

    Admission admit(String json) {
        if (json == null) return Admission.SHAPE;
        int len = FootprintViews.utf8Length(json);
        if (len > maxRecordBytes) return Admission.OVERSIZE;
        JsonNode root;
        try { root = mapper.readTree(json); } catch (Exception e) { return Admission.SHAPE; }
        if (root == null || !root.isObject()) return Admission.SHAPE;
        String kind = text(root, "kind"), symbol = text(root, "symbol"), tf = text(root, "timeframe");
        LocalDate date = FootprintViews.parseCanonicalDate(text(root, "sessionDate"));
        long seen = epoch(root, "seenMaxBarStartMs");
        if (symbol.isEmpty() || tf.isEmpty() || seen < 0) return Admission.SHAPE;
        if ("CHECKPOINT".equals(kind)) {
            synchronized (lock) { advance(tf, seen, date); }
            return Admission.CHECKPOINT;
        }
        if (!"OPEN".equals(kind) && !"UPDATE".equals(kind) && !"CLOSE".equals(kind)) return Admission.SHAPE;
        long strike = epoch(root, "strikeCents"), open = epoch(root, "openBarStartMs"), revision = epoch(root, "revision");
        if (date == null || strike < 0 || open < 0 || revision < 0 || !root.path("series").isArray()) return Admission.SHAPE;
        String identity = symbol + "|" + date + "|" + tf + "|" + strike + "|" + open;
        synchronized (lock) {
            advance(tf, seen, date);
            if (refused.contains(identity)) return Admission.REFUSED;
            Head cur = heads.get(identity);
            if (cur != null && revision == cur.revision()) {
                if (json.equals(cur.json())) return Admission.EPISODE;                  // a bar colliding with itself (R6)
                // R14: a fault, not a tie to break — the whole identity is refused
                remove(cur);
                refused.add(identity);
                collisions.incrementAndGet();
                return Admission.COLLISION;
            }
            if (cur != null && revision < cur.revision()) return Admission.EPISODE;    // an older revision after a newer one: the fold already holds the greater
            if (cur != null) remove(cur);
            Head h = new Head(identity, symbol, date.toString(), tf, strike, open, revision, json);
            heads.put(identity, h);
            index.computeIfAbsent(tf, k -> new TreeMap<>()).computeIfAbsent(strike, k -> new TreeMap<>()).put(openKey(h.sessionDate(), open), identity);
            byAge.put(ageKey(open, identity), identity);
            bytes += len;
            enforce();
            return Admission.EPISODE;
        }
    }

    private void advance(String tf, long seen, LocalDate date) {
        Long h = hwm.get(tf);
        if (h == null || seen > h) hwm.put(tf, seen);
        if (date != null && (sessionDate == null || date.toString().compareTo(sessionDate) > 0)) sessionDate = date.toString();
    }

    private void remove(Head h) {
        heads.remove(h.identity());
        TreeMap<Long, TreeMap<String, String>> perTf = index.get(h.tf());
        if (perTf != null) {
            TreeMap<String, String> per = perTf.get(h.strikeCents());
            if (per != null) { per.remove(openKey(h.sessionDate(), h.openBarStartMs())); if (per.isEmpty()) perTf.remove(h.strikeCents()); }
            if (perTf.isEmpty()) index.remove(h.tf());
        }
        byAge.remove(ageKey(h.openBarStartMs(), h.identity()));
        bytes -= FootprintViews.utf8Length(h.json());
    }

    /** Oldest identities go first, whatever their timeframe: the boundary is then ONE instant the reader can be told. */
    private void enforce() {
        int evicted = 0;
        while (!byAge.isEmpty() && (heads.size() > maxEpisodes || bytes > maxBytes)) {
            Head oldest = heads.get(byAge.firstEntry().getValue());
            remove(oldest);
            evicted++;
        }
        if (evicted > 0) evictions.addAndGet(evicted);
    }

    // ---- reads (one snapshot each) -----------------------------------------------------------------

    /** The hello field: {@code {"sessionDate":..,"hwm":{tf:seenMax},"historyBeginsAtMs":..,"refused":n}}. */
    String helloField() {
        synchronized (lock) {
            StringBuilder sb = new StringBuilder("{\"sessionDate\":").append(sessionDate == null ? "null" : "\"" + sessionDate + "\"").append(",\"hwm\":{");
            boolean first = true;
            for (Map.Entry<String, Long> e : hwm.entrySet()) { if (!first) sb.append(','); sb.append('"').append(e.getKey()).append("\":").append(e.getValue()); first = false; }
            sb.append("},\"historyBeginsAtMs\":").append(historyBeginsAtLocked() == null ? "null" : historyBeginsAtLocked());
            return sb.append(",\"refused\":").append(refused.size()).append('}').toString();
        }
    }

    /**
     * {@code latest} (R14): for ONE timeframe and ONE session, the folded record of the episode with the
     * greatest {@code openBarStartMs} per strike, ascending by strike; {@code afterStrike} is an exclusive
     * cursor. A strike with no episode in that session simply has no row — NO DATA is the reader's word.
     */
    Page latest(String tf, String session, long afterStrikeExclusive, int limit) {
        synchronized (lock) {
            List<String> out = new ArrayList<>();
            Long last = null;
            TreeMap<Long, TreeMap<String, String>> perTf = index.get(tf);
            if (perTf != null && session != null && !session.isEmpty()) {
                String lo = session + "|", hi = session + "|~";
                for (Map.Entry<Long, TreeMap<String, String>> e : perTf.tailMap(afterStrikeExclusive, false).entrySet()) {
                    Map.Entry<String, String> newest = e.getValue().subMap(lo, true, hi, true).lastEntry();   // greatest openBarStartMs in that session
                    if (newest == null) continue;
                    out.add(heads.get(newest.getValue()).json());
                    last = e.getKey();
                    if (out.size() >= limit) break;
                }
            }
            return new Page(sessionDate, out, out.size() >= limit && last != null ? Long.toString(last) : null, historyBeginsAtLocked(), refused.size());
        }
    }

    /**
     * {@code history} (R14/R18/R20): every folded episode of one strike in one timeframe, NEWEST first,
     * ACROSS sessions; {@code before} is an exclusive opaque cursor ({@code sessionDate|%019d(openBarStartMs)}).
     */
    Page history(String tf, long strikeCents, String beforeExclusive, int limit) {
        synchronized (lock) {
            List<String> out = new ArrayList<>();
            String last = null;
            TreeMap<Long, TreeMap<String, String>> perTf = index.get(tf);
            TreeMap<String, String> per = perTf == null ? null : perTf.get(strikeCents);
            if (per != null) {
                var view = beforeExclusive == null || beforeExclusive.isEmpty() ? per.descendingMap() : per.headMap(beforeExclusive, false).descendingMap();
                for (Map.Entry<String, String> e : view.entrySet()) {
                    out.add(heads.get(e.getValue()).json());
                    last = e.getKey();
                    if (out.size() >= limit) break;
                }
            }
            return new Page(sessionDate, out, out.size() >= limit ? last : null, historyBeginsAtLocked(), refused.size());
        }
    }

    static boolean validHistoryCursor(String c) { return c != null && c.matches("^\\d{4}-\\d{2}-\\d{2}\\|\\d{19}$"); }

    private Long historyBeginsAtLocked() {
        if (byAge.isEmpty()) return null;
        return heads.get(byAge.firstEntry().getValue()).openBarStartMs();
    }

    // ---- gauges -------------------------------------------------------------------------------------

    String sessionDate() { synchronized (lock) { return sessionDate; } }
    Long historyBeginsAtMs() { synchronized (lock) { return historyBeginsAtLocked(); } }
    int episodesInView() { synchronized (lock) { return heads.size(); } }
    long bytesInView() { synchronized (lock) { return bytes; } }
    int refusedIdentities() { synchronized (lock) { return refused.size(); } }
    long evictions() { return evictions.get(); }
    long collisions() { return collisions.get(); }

    // ---- helpers ------------------------------------------------------------------------------------

    static String openKey(String sessionDate, long openBarStartMs) { return sessionDate + "|" + String.format("%019d", openBarStartMs); }
    private static String ageKey(long openBarStartMs, String identity) { return String.format("%019d", openBarStartMs) + "|" + identity; }
    private static String text(JsonNode n, String k) { JsonNode v = n.get(k); return v == null || !v.isTextual() ? "" : v.asText(); }
    private static long epoch(JsonNode n, String k) {
        JsonNode v = n.get(k);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong()) return -1;
        long x = v.asLong();
        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;
    }
    static byte[] ascii(String s) { return s.getBytes(StandardCharsets.US_ASCII); }
}
