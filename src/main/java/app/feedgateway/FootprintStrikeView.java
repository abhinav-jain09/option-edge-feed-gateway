package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * identity AND a revision must be identical AS BYTES — checked for EVERY observed revision of an
 * identity, not only its head (code round-1 #1) — else that identity is REFUSED for the incarnation
 * and counted. A refused identity keeps its place in the ordering as a TOMBSTONE: if it is the newest
 * episode of a strike, {@code latest} shows NO ROW for that strike (the chip renders NO DATA), never an
 * older episode in its stead (code round-1 #3); history omits it and still lists the older ones. A
 * CHECKPOINT carries no episode and only advances the per-timeframe high-water mark the hello reports.
 *
 * <p>Scope is by symbol AND timeframe everywhere (code round-1 #2). Unlike the bars/outcomes
 * coordinator this view never rolls a session away: history crosses sessions ("6800 may behave
 * differently on every visit"), and {@code latest} is scoped to ONE session by the reader.
 *
 * <p><b>Bounded, with the boundary published.</b> Budgets evict the OLDEST identities (by
 * {@code openBarStartMs}); every eviction advances a MONOTONIC boundary, records that open before it are
 * dropped rather than re-admitted (so an evicted identity can never return at a lower revision), and
 * {@link #historyBeginsAtMs} reports that boundary — or, before any eviction, the oldest retained open —
 * so the drill-down never presents a partial list as complete (R20; code round-1 #6). Collision digests
 * live with their identity and leave with it; the refusal ledger is bounded by
 * {@code maxRefusedIdentities}, beyond which the view is UNAVAILABLE for the incarnation rather than
 * silently forgetting a refusal (code round-1 #5). Every read is one snapshot under the lock; the lock
 * is never held while writing to a client.
 */
final class FootprintStrikeView {

    enum Reason { ADMITTED, OVERSIZE, SHAPE, COLLISION, REFUSED, EVICTED, UNAVAILABLE }

    record Admission(Reason reason, boolean checkpoint) {
        static final Admission OVERSIZE = new Admission(Reason.OVERSIZE, false);
        static final Admission SHAPE = new Admission(Reason.SHAPE, false);
        static final Admission COLLISION = new Admission(Reason.COLLISION, false);
        static final Admission REFUSED = new Admission(Reason.REFUSED, false);
        static final Admission EVICTED = new Admission(Reason.EVICTED, false);
        static final Admission UNAVAILABLE = new Admission(Reason.UNAVAILABLE, false);
        static final Admission CHECKPOINT = new Admission(Reason.ADMITTED, true);
        static final Admission EPISODE = new Admission(Reason.ADMITTED, false);
    }

    /** One folded identity: its identity fields, its head revision, the head's bytes and every observed revision's digest. */
    private static final class Head {
        final String identity, symbol, sessionDate, tf; final long strikeCents, openBarStartMs;
        long revision; String json; boolean refused;
        final Map<Long, byte[]> digests = new HashMap<>();
        Head(String identity, String symbol, String sessionDate, String tf, long strikeCents, long openBarStartMs) {
            this.identity = identity; this.symbol = symbol; this.sessionDate = sessionDate; this.tf = tf; this.strikeCents = strikeCents; this.openBarStartMs = openBarStartMs;
        }
    }

    /** One page of folded records: ascending by strike for {@code latest}, newest first for {@code history}. */
    record Page(String sessionDate, List<String> records, String nextCursor, Long historyBeginsAtMs, long refused, boolean unavailable) {}

    static final long EPOCH_MAX_MS = 253_402_300_799_999L;
    static final int LATEST_LIMIT_MAX = 200, HISTORY_LIMIT_MAX = 100;
    private static final Set<String> EPISODE_KINDS = Set.of("OPEN", "UPDATE", "CLOSE");

    private final ObjectMapper mapper;
    private final long maxRecordBytes, maxBytes;
    private final int maxEpisodes, maxRefused;

    private final Object lock = new Object();
    private final Map<String, Head> heads = new HashMap<>();
    /** symbol|tf → strike → (openKey → identity). Refused identities STAY here as tombstones. */
    private final Map<String, TreeMap<Long, TreeMap<String, String>>> index = new TreeMap<>();
    /** Eviction order over LIVE heads: ageKey → identity. */
    private final TreeMap<String, String> byAge = new TreeMap<>();
    private final Set<String> refused = new HashSet<>();
    private final Map<String, Long> hwm = new TreeMap<>();
    private long bytes;
    private String sessionDate;
    private Long boundaryMs;                                    // monotonic: nothing that opened before it is admitted or retained
    private boolean unavailable;
    private final AtomicLong evictions = new AtomicLong(), collisions = new AtomicLong();

    FootprintStrikeView(ObjectMapper mapper, long maxRecordBytes, long maxBytes, int maxEpisodes, int maxRefused) {
        if (maxRecordBytes <= 0 || maxBytes <= 0 || maxEpisodes <= 0 || maxRefused <= 0) throw new IllegalArgumentException("strike view budgets must be positive");
        this.mapper = mapper; this.maxRecordBytes = maxRecordBytes; this.maxBytes = maxBytes; this.maxEpisodes = maxEpisodes; this.maxRefused = maxRefused;
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
        JsonNode dateNode = root.get("sessionDate");
        LocalDate date = dateNode != null && dateNode.isTextual() ? FootprintViews.parseCanonicalDate(dateNode.asText()) : null;
        long seen = epoch(root, "seenMaxBarStartMs");
        if (symbol.isEmpty() || !FootprintViews.TIMEFRAMES.contains(tf) || seen < 0) return Admission.SHAPE;
        if ("CHECKPOINT".equals(kind)) {
            // the session date may be a genuine JSON null (nothing seen yet); an invalid non-null one is a shape drop
            if (dateNode == null || (!dateNode.isNull() && date == null)) return Admission.SHAPE;
            synchronized (lock) { if (unavailable) return Admission.UNAVAILABLE; advance(tf, seen, date); }
            return Admission.CHECKPOINT;
        }
        if (!EPISODE_KINDS.contains(kind)) return Admission.SHAPE;
        long strike = nonNegative(root, "strikeCents"), open = epoch(root, "openBarStartMs"), revision = nonNegative(root, "revision");
        if (date == null || strike < 0 || open < 0 || revision < 0 || !root.path("series").isArray()) return Admission.SHAPE;
        String identity = symbol + "|" + date + "|" + tf + "|" + strike + "|" + open;
        byte[] digest = sha256(json);
        synchronized (lock) {
            if (unavailable) return Admission.UNAVAILABLE;
            advance(tf, seen, date);
            if (boundaryMs != null && open < boundaryMs) return Admission.EVICTED;   // before the published boundary: never re-admitted
            if (refused.contains(identity)) return Admission.REFUSED;
            Head h = heads.get(identity);
            if (h != null) {
                byte[] prior = h.digests.get(revision);
                if (prior != null) {
                    if (Arrays.equals(prior, digest)) return Admission.EPISODE;   // a bar colliding with itself (R6)
                    refuse(h);                                                     // R14: a fault, not a tie to break
                    collisions.incrementAndGet();
                    return unavailable ? Admission.UNAVAILABLE : Admission.COLLISION;
                }
                h.digests.put(revision, digest);
                if (revision < h.revision) return Admission.EPISODE;              // an older revision after a newer one: the fold already holds the greater
                bytes += len - FootprintViews.utf8Length(h.json);
                h.revision = revision; h.json = json;
                enforce();
                return Admission.EPISODE;
            }
            h = new Head(identity, symbol, date.toString(), tf, strike, open);
            h.revision = revision; h.json = json; h.digests.put(revision, digest);
            heads.put(identity, h);
            index.computeIfAbsent(scope(symbol, tf), k -> new TreeMap<>()).computeIfAbsent(strike, k -> new TreeMap<>()).put(openKey(h.sessionDate, open), identity);
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

    /** Refuse an identity: its bytes leave, its tombstone stays in the index, the refusal is remembered — within a bound. */
    private void refuse(Head h) {
        h.refused = true;
        bytes -= FootprintViews.utf8Length(h.json);
        h.json = null; h.digests.clear();
        heads.remove(h.identity);
        byAge.remove(ageKey(h.openBarStartMs, h.identity));
        refused.add(h.identity);
        if (refused.size() > maxRefused) unavailable = true;    // the ledger would have to forget a refusal: fail closed instead
    }

    private void removeLive(Head h) {
        heads.remove(h.identity);
        TreeMap<Long, TreeMap<String, String>> perScope = index.get(scope(h.symbol, h.tf));
        if (perScope != null) {
            TreeMap<String, String> per = perScope.get(h.strikeCents);
            if (per != null) { per.remove(openKey(h.sessionDate, h.openBarStartMs)); if (per.isEmpty()) perScope.remove(h.strikeCents); }
            if (perScope.isEmpty()) index.remove(scope(h.symbol, h.tf));
        }
        byAge.remove(ageKey(h.openBarStartMs, h.identity));
        bytes -= FootprintViews.utf8Length(h.json);
    }

    /** Oldest identities go first, whatever their scope; the boundary advances to the oldest RETAINED open, and never retreats. */
    private void enforce() {
        int evicted = 0;
        while (!byAge.isEmpty() && (heads.size() > maxEpisodes || bytes > maxBytes)) {
            Head oldest = heads.get(byAge.firstEntry().getValue());
            removeLive(oldest);
            evicted++;
            long next = byAge.isEmpty() ? oldest.openBarStartMs + 1 : heads.get(byAge.firstEntry().getValue()).openBarStartMs;
            long candidate = Math.max(next, oldest.openBarStartMs + 1);
            boundaryMs = boundaryMs == null ? candidate : Math.max(boundaryMs, candidate);
            // tombstones older than the boundary are unreachable: drop them from the index and the ledger keeps its count
            pruneTombstonesBefore(boundaryMs);
        }
        if (evicted > 0) evictions.addAndGet(evicted);
    }

    private void pruneTombstonesBefore(long ms) {
        for (var scopeEntry : new ArrayList<>(index.entrySet())) {
            for (var strikeEntry : new ArrayList<>(scopeEntry.getValue().entrySet())) {
                TreeMap<String, String> per = strikeEntry.getValue();
                per.entrySet().removeIf(e -> !heads.containsKey(e.getValue()) && openMsOf(e.getKey()) < ms);
                if (per.isEmpty()) scopeEntry.getValue().remove(strikeEntry.getKey());
            }
            if (scopeEntry.getValue().isEmpty()) index.remove(scopeEntry.getKey());
        }
    }

    // ---- reads (one snapshot each) -----------------------------------------------------------------

    /** The hello field: {@code {"sessionDate":..,"hwm":{tf:seenMax},"historyBeginsAtMs":..,"refused":n,"unavailable":bool}}. */
    String helloField() {
        synchronized (lock) {
            StringBuilder sb = new StringBuilder("{\"sessionDate\":").append(sessionDate == null ? "null" : "\"" + sessionDate + "\"").append(",\"hwm\":{");
            boolean first = true;
            for (Map.Entry<String, Long> e : hwm.entrySet()) { if (!first) sb.append(','); sb.append('"').append(e.getKey()).append("\":").append(e.getValue()); first = false; }
            sb.append("},\"historyBeginsAtMs\":").append(historyBeginsAtLocked() == null ? "null" : historyBeginsAtLocked());
            return sb.append(",\"refused\":").append(refused.size()).append(",\"unavailable\":").append(unavailable).append('}').toString();
        }
    }

    /**
     * {@code latest} (R14): for ONE symbol, ONE timeframe and ONE session, the folded record of the
     * episode with the greatest {@code openBarStartMs} per strike, ascending by strike; {@code afterStrike}
     * is an exclusive cursor. A strike whose newest episode is REFUSED has no row (NO DATA), never an
     * older one; a strike with no episode in that session simply has no row.
     */
    Page latest(String symbol, String tf, String session, long afterStrikeExclusive, int limit) {
        synchronized (lock) {
            List<String> out = new ArrayList<>();
            Long last = null;
            TreeMap<Long, TreeMap<String, String>> perScope = index.get(scope(symbol, tf));
            if (!unavailable && perScope != null && session != null && !session.isEmpty()) {
                String lo = session + "|", hi = session + "|~";
                for (Map.Entry<Long, TreeMap<String, String>> e : perScope.tailMap(afterStrikeExclusive, false).entrySet()) {
                    Map.Entry<String, String> newest = e.getValue().subMap(lo, true, hi, true).lastEntry();   // greatest openBarStartMs in that session
                    last = e.getKey();
                    if (newest == null) continue;
                    Head h = heads.get(newest.getValue());
                    if (h == null) continue;                                                              // a refused tombstone: NO DATA for this strike
                    out.add(h.json);
                    if (out.size() >= limit) break;
                }
            }
            return new Page(session, out, out.size() >= limit && last != null ? Long.toString(last) : null, historyBeginsAtLocked(), refused.size(), unavailable);
        }
    }

    /**
     * {@code history} (R14/R18/R20): every folded episode of one strike in one symbol and timeframe, NEWEST
     * first, ACROSS sessions, refused identities omitted; {@code before} is an exclusive opaque cursor
     * ({@code sessionDate|%019d(openBarStartMs)}).
     */
    Page history(String symbol, String tf, long strikeCents, String beforeExclusive, int limit) {
        synchronized (lock) {
            List<String> out = new ArrayList<>();
            String last = null;
            TreeMap<Long, TreeMap<String, String>> perScope = index.get(scope(symbol, tf));
            TreeMap<String, String> per = perScope == null ? null : perScope.get(strikeCents);
            if (!unavailable && per != null) {
                var view = beforeExclusive == null || beforeExclusive.isEmpty() ? per.descendingMap() : per.headMap(beforeExclusive, false).descendingMap();
                for (Map.Entry<String, String> e : view.entrySet()) {
                    last = e.getKey();
                    Head h = heads.get(e.getValue());
                    if (h == null) continue;                                                              // refused: omitted, and the walk continues
                    out.add(h.json);
                    if (out.size() >= limit) break;
                }
            }
            return new Page(sessionDate, out, out.size() >= limit ? last : null, historyBeginsAtLocked(), refused.size(), unavailable);
        }
    }

    /** The cursor grammar, and the domain: a canonical calendar date and an epoch inside the domain. */
    static boolean validHistoryCursor(String c) {
        if (c == null || !c.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}\\|[0-9]{19}") || FootprintViews.parseCanonicalDate(c.substring(0, 10)) == null) return false;
        try { return Long.parseLong(c.substring(11)) <= EPOCH_MAX_MS; } catch (NumberFormatException e) { return false; }
    }

    private Long historyBeginsAtLocked() {
        if (boundaryMs != null) return boundaryMs;
        if (byAge.isEmpty()) return null;
        return heads.get(byAge.firstEntry().getValue()).openBarStartMs;
    }

    // ---- gauges -------------------------------------------------------------------------------------

    String sessionDate() { synchronized (lock) { return sessionDate; } }
    Long historyBeginsAtMs() { synchronized (lock) { return historyBeginsAtLocked(); } }
    int episodesInView() { synchronized (lock) { return heads.size(); } }
    long bytesInView() { synchronized (lock) { return bytes; } }
    int refusedIdentities() { synchronized (lock) { return refused.size(); } }
    boolean unavailable() { synchronized (lock) { return unavailable; } }
    long evictions() { return evictions.get(); }
    long collisions() { return collisions.get(); }

    // ---- helpers ------------------------------------------------------------------------------------

    static String scope(String symbol, String tf) { return symbol + "|" + tf; }
    static String openKey(String sessionDate, long openBarStartMs) { return sessionDate + "|" + String.format(Locale.ROOT, "%019d", openBarStartMs); }
    private static String ageKey(long openBarStartMs, String identity) { return String.format(Locale.ROOT, "%019d", openBarStartMs) + "|" + identity; }
    private static long openMsOf(String openKey) { return Long.parseLong(openKey.substring(11)); }
    private static String text(JsonNode n, String k) { JsonNode v = n.get(k); return v == null || !v.isTextual() ? "" : v.asText(); }
    /** An integral value inside the epoch-ms domain, or -1. */
    private static long epoch(JsonNode n, String k) {
        long x = nonNegative(n, k);
        return x > EPOCH_MAX_MS ? -1 : x;
    }
    /** An integral, non-negative long (a strike in cents, a revision) — NOT an epoch, so no epoch ceiling. */
    private static long nonNegative(JsonNode n, String k) {
        JsonNode v = n.get(k);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong()) return -1;
        long x = v.longValue();
        return x < 0 ? -1 : x;
    }
    static byte[] sha256(String s) {
        try { return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    /** A JSON string literal carrying {@code json} verbatim, so a reader receives the exact bytes the fold compared (code round-1, page #1). */
    static String quoted(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16).append('"');
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
