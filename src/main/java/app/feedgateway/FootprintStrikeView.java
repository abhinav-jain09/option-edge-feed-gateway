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
 * <p><b>Every retained thing is charged, conservatively, and the boundary is published.</b> The view
 * charges each head's payload, its identity strings, {@link #REVISION_BYTES} per observed revision and
 * {@link #TOMBSTONE_OVERHEAD} per tombstone — deliberate over-estimates of an object graph, since what
 * the JVM holds is a map of boxed keys and arrays, not packed bytes (round-2 #1, round-3 #1). The
 * figure is therefore an ACCOUNTING bound on what the view may retain, not a measured heap bound; the
 * deployment record says which measurement settles the latter. Budgets evict the OLDEST
 * identities by {@code openBarStartMs}, and always the WHOLE equal-opening-time bucket, so a
 * monotonic boundary can be published that is strictly above every retained head: records opening
 * before it are dropped rather than re-admitted, so an evicted identity can never return at a lower
 * revision and no retained head can sit behind the boundary with its updates silently refused
 * (code round-2 #2). When eviction cannot bring the view inside its budget the view goes UNAVAILABLE
 * rather than forgetting evidence — the same rule the refusal ledger already had.
 *
 * <p><b>Loading is distinguishable from empty.</b> {@link #replay(long, boolean)} carries the cache
 * consumer's replay window and whether it has crossed the end offsets captured at its bootstrap;
 * until it has, the hello and every page say {@code "loading":true} so a reader can never turn an
 * unfinished replay into a completed NO DATA (code round-2 #3). {@code replayBeginsAtMs} is the
 * window the replay actually covered, kept apart from {@code historyBeginsAtMs}, which is retained
 * inventory (code round-2 #6).
 *
 * <p><b>Authority changes reach connected readers.</b> A collision refusal, entering UNAVAILABLE, or
 * completing the replay fires {@link #onAuthorityChange}, which the service turns into an ordered
 * control frame so an already-READY page reconciles instead of displaying evidence the fold has
 * withdrawn (code round-2 #4). Every read is one snapshot under the lock; the lock is never held
 * while writing to a client, and never while notifying.
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
    record Page(String sessionDate, List<String> records, String nextCursor, Long historyBeginsAtMs, long refused,
                boolean unavailable, boolean loading, Long replayBeginsAtMs, long authority) {}

    static final long EPOCH_MAX_MS = 253_402_300_799_999L;
    static final int LATEST_LIMIT_MAX = 200, HISTORY_LIMIT_MAX = 100;
    /** A symbol longer than this is not a symbol: refusing it keeps one record from charging arbitrary metadata (round-2 #1). */
    static final int MAX_SYMBOL_CHARS = 64;
    /**
     * What one retained revision is CHARGED. The ledger is a {@code HashMap<Long, byte[]>}, so an entry
     * is a boxed key (16), a node (32), an array header plus the digest (16 + 32) and its share of the
     * bucket table (~16) — charged at 128 rather than the 40 packed bytes, because what the JVM holds is
     * an object graph and the budget has to be conservative against THAT (gateway round-3 #1).
     */
    static final int REVISION_BYTES = 128;
    /**
     * Per-entry structural overhead: the head object and its strings, the three index nodes (scope tree,
     * strike tree, open-key tree), the age-key string and node, and their table shares. A tombstone keeps
     * its index node and its ledger identity. Both are deliberate over-estimates.
     */
    static final int HEAD_OVERHEAD = 512, TOMBSTONE_OVERHEAD = 256;
    private static final Set<String> EPISODE_KINDS = Set.of("OPEN", "UPDATE", "CLOSE");

    private final ObjectMapper mapper;
    private final long maxRecordBytes, maxBytes;
    private final int maxEpisodes, maxRefused;
    /** The symbol the ROUTES scope to. Published in the hello so a live reader scopes exactly as REST does. */
    private volatile String scopeSymbol = "";

    private final Object lock = new Object();
    private final Map<String, Head> heads = new HashMap<>();
    /** symbol|tf → strike → (openKey → identity). Refused identities STAY here as tombstones. */
    private final Map<String, TreeMap<Long, TreeMap<String, String>>> index = new TreeMap<>();
    /** Eviction order over LIVE heads: ageKey → identity. */
    private final TreeMap<String, String> byAge = new TreeMap<>();
    private final Set<String> refused = new HashSet<>();
    private final Map<String, Long> hwm = new TreeMap<>();
    private long bytes;                                         // head payloads only (what a reader would receive)
    private long meta;                                          // identities, revision ledgers, tombstones
    private int tombstones;
    private String sessionDate;
    private Long boundaryMs;                                    // monotonic: nothing that opened before it is admitted or retained
    private Long replayBeginsAtMs;
    private boolean replayComplete;
    /**
     * Monotonic. Every authority change bumps it, and it rides the hello, the control frame and every
     * page, so a reader can discard an OLDER authority that arrives after a newer one — the ordering
     * hole where a stale `loading:true` hello enqueued before a completion control left readers loading
     * for ever (gateway round-3 #3).
     */
    private long authority;
    private boolean unavailable;
    private volatile Runnable onAuthorityChange = () -> {};
    private final AtomicLong evictions = new AtomicLong(), collisions = new AtomicLong();

    FootprintStrikeView(ObjectMapper mapper, long maxRecordBytes, long maxBytes, int maxEpisodes, int maxRefused) {
        if (maxRecordBytes <= 0 || maxBytes <= 0 || maxEpisodes <= 0 || maxRefused <= 0) throw new IllegalArgumentException("strike view budgets must be positive");
        this.mapper = mapper; this.maxRecordBytes = maxRecordBytes; this.maxBytes = maxBytes; this.maxEpisodes = maxEpisodes; this.maxRefused = maxRefused;
    }

    /**
     * The symbol every route defaults to; a reader that folds live records must use the same one. It is
     * CONFIGURATION, so it is escaped where it is written (round-3 #6: a value carrying a quote broke the
     * whole shared cvd-hello, not only this field) and refused outright when it cannot be a symbol.
     */
    void scopeSymbol(String symbol) {
        String s = symbol == null ? "" : symbol;
        if (s.length() > MAX_SYMBOL_CHARS) throw new IllegalArgumentException("GATEWAY_ES_FOOTPRINT_STRIKE_SYMBOL is longer than " + MAX_SYMBOL_CHARS + " characters");
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 0x20) throw new IllegalArgumentException("GATEWAY_ES_FOOTPRINT_STRIKE_SYMBOL contains a control character");
        this.scopeSymbol = s;
    }

    /** What the service does when the authority the readers hold has changed (refusal, unavailable, replay complete). */
    void onAuthorityChange(Runnable listener) { this.onAuthorityChange = listener == null ? () -> {} : listener; }

    /**
     * The cache consumer's replay for the STRIKE partitions: the window it actually sought back to, and
     * whether those partitions have crossed the end offsets captured at that consumer's bootstrap.
     *
     * <p>{@code beginsAtMs} is recorded ONCE per replay — a later call with the same generation cannot
     * move it, because the field names the window that was seeked, not the current clock (round-3 #5).
     * {@link #replayRestarted} reopens it when a new consumer attempt or a late adoption means the
     * strike partitions must be replayed again (round-3 #2).
     */
    void replay(long beginsAtMs, boolean complete) {
        boolean notify = false;
        synchronized (lock) {
            if (replayBeginsAtMs == null) replayBeginsAtMs = beginsAtMs;
            if (complete && !replayComplete) { replayComplete = true; authority++; notify = true; }
        }
        if (notify) onAuthorityChange.run();
    }

    /** A new replay of the strike partitions has begun: readers are loading again until it completes. */
    void replayRestarted(long beginsAtMs) {
        boolean notify = false;
        synchronized (lock) {
            replayBeginsAtMs = beginsAtMs;
            if (replayComplete) { replayComplete = false; authority++; notify = true; }
        }
        if (notify) onAuthorityChange.run();
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
        if (symbol.isEmpty() || symbol.length() > MAX_SYMBOL_CHARS || !FootprintViews.TIMEFRAMES.contains(tf) || seen < 0) return Admission.SHAPE;
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
        Admission outcome;
        boolean notify = false;
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
                    authority++;
                    outcome = unavailable ? Admission.UNAVAILABLE : Admission.COLLISION;
                    notify = true;                                                 // the readers hold a value this fold has just withdrawn
                } else {
                    h.digests.put(revision, digest); meta += REVISION_BYTES;
                    if (revision >= h.revision) {
                        bytes += len - FootprintViews.utf8Length(h.json);
                        h.revision = revision; h.json = json;
                    }
                    // an older revision after a newer one keeps its digest (R14) and nothing else
                    notify = enforce();
                    outcome = heads.containsKey(identity) ? Admission.EPISODE : Admission.EVICTED;
                }
            } else {
                h = new Head(identity, symbol, date.toString(), tf, strike, open);
                h.revision = revision; h.json = json; h.digests.put(revision, digest);
                heads.put(identity, h);
                index.computeIfAbsent(scope(symbol, tf), k -> new TreeMap<>()).computeIfAbsent(strike, k -> new TreeMap<>()).put(openKey(h.sessionDate, open), identity);
                byAge.put(ageKey(open, identity), identity);
                bytes += len; meta += headMeta(h);
                notify = enforce();
                // enforcement can evict the very identity just inserted: say so rather than reporting it
                // admitted while the fold no longer holds it (round-3 #4)
                outcome = heads.containsKey(identity) ? Admission.EPISODE : Admission.EVICTED;
            }
        }
        if (notify) onAuthorityChange.run();
        return outcome;
    }

    private void advance(String tf, long seen, LocalDate date) {
        Long h = hwm.get(tf);
        if (h == null || seen > h) hwm.put(tf, seen);
        if (date != null && (sessionDate == null || date.toString().compareTo(sessionDate) > 0)) sessionDate = date.toString();
    }

    /** What one head's metadata costs: its identity string (twice — head and index key) and its revision ledger. */
    private static long headMeta(Head h) { return HEAD_OVERHEAD + 2L * h.identity.length() + (long) REVISION_BYTES * h.digests.size(); }

    /** Refuse an identity: its bytes leave, its tombstone stays in the index, the refusal is remembered — within a bound. */
    private void refuse(Head h) {
        h.refused = true;
        bytes -= FootprintViews.utf8Length(h.json);
        meta -= headMeta(h);
        h.json = null; h.digests.clear();
        heads.remove(h.identity);
        byAge.remove(ageKey(h.openBarStartMs, h.identity));
        if (refused.add(h.identity)) { meta += TOMBSTONE_OVERHEAD + 2L * h.identity.length(); tombstones++; }
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
        meta -= headMeta(h);
    }

    /**
     * Oldest identities go first, whatever their scope, and always the WHOLE equal-opening-time bucket, so
     * the boundary that follows is strictly above every retained head (round-2 #2). Returns true when the
     * view has just failed closed and connected readers must be told.
     */
    private boolean enforce() {
        int evicted = 0;
        Long boundaryBefore = boundaryMs;
        while (!byAge.isEmpty() && overBudget()) {
            Head oldest = heads.get(byAge.firstEntry().getValue());
            long bucket = oldest.openBarStartMs;
            removeLive(oldest); evicted++;
            // every other head that opened in the same millisecond leaves with it: a boundary at
            // bucket + 1 would otherwise sit ABOVE a retained head, freezing its updates as EVICTED
            while (!byAge.isEmpty()) {
                Head next = heads.get(byAge.firstEntry().getValue());
                if (next.openBarStartMs != bucket) break;
                removeLive(next); evicted++;
            }
            // the boundary is the OLDEST RETAINED opening (never less than one past the bucket just
            // evicted), so it is both strictly above everything evicted and at or below everything kept
            long candidate = byAge.isEmpty() ? bucket + 1 : Math.max(bucket + 1, heads.get(byAge.firstEntry().getValue()).openBarStartMs);
            boundaryMs = boundaryMs == null ? candidate : Math.max(boundaryMs, candidate);
        }
        // tombstones older than the boundary are unreachable: drop them ONCE, and only when there are any
        // (round-2 #7 — pruning per eviction walked the whole index under the lock on every eviction)
        if (evicted > 0) {
            evictions.addAndGet(evicted);
            if (tombstones > 0 && boundaryMs != null) pruneTombstonesBefore(boundaryMs);
        }
        if (overBudget() && !unavailable) {
            // nothing left to evict and still over: the alternative is silently holding more than the
            // deployment allowed, which is the failure this budget exists to prevent
            unavailable = true;
            authority++;
            return true;
        }
        // an eviction MOVES the boundary, which changes what every reader may hold: that is an authority
        // change too, not merely bookkeeping (round-3 #4)
        if (!java.util.Objects.equals(boundaryBefore, boundaryMs)) { authority++; return true; }
        return false;
    }

    private boolean overBudget() { return heads.size() > maxEpisodes || bytes + meta > maxBytes; }

    /** Drops unreachable tombstones and DECREMENTS the indexed count, so the guard means "there are some". */
    private void pruneTombstonesBefore(long ms) {
        for (var scopeEntry : new ArrayList<>(index.entrySet())) {
            for (var strikeEntry : new ArrayList<>(scopeEntry.getValue().entrySet())) {
                TreeMap<String, String> per = strikeEntry.getValue();
                per.entrySet().removeIf(e -> {
                    boolean drop = !heads.containsKey(e.getValue()) && openMsOf(e.getKey()) < ms;
                    if (drop) tombstones--;                       // round-3 #7: it was only ever incremented
                    return drop;
                });
                if (per.isEmpty()) scopeEntry.getValue().remove(strikeEntry.getKey());
            }
            if (scopeEntry.getValue().isEmpty()) index.remove(scopeEntry.getKey());
        }
    }

    // ---- reads (one snapshot each) -----------------------------------------------------------------

    /**
     * The hello field:
     * {@code {"symbol":..,"sessionDate":..,"hwm":{tf:seenMax},"historyBeginsAtMs":..,"replayBeginsAtMs":..,"loading":bool,"refused":n,"unavailable":bool}}.
     */
    String helloField() {
        synchronized (lock) {
            StringBuilder sb = new StringBuilder("{\"authority\":").append(authority)
                    .append(",\"symbol\":").append(quoted(scopeSymbol)).append(",\"sessionDate\":")
                    .append(sessionDate == null ? "null" : "\"" + sessionDate + "\"").append(",\"hwm\":{");
            boolean first = true;
            for (Map.Entry<String, Long> e : hwm.entrySet()) { if (!first) sb.append(','); sb.append('"').append(e.getKey()).append("\":").append(e.getValue()); first = false; }
            sb.append("},\"historyBeginsAtMs\":").append(historyBeginsAtLocked() == null ? "null" : historyBeginsAtLocked());
            sb.append(",\"replayBeginsAtMs\":").append(replayBeginsAtMs == null ? "null" : replayBeginsAtMs);
            sb.append(",\"loading\":").append(!replayComplete);
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
            return page(session, out, out.size() >= limit && last != null ? Long.toString(last) : null);
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
            return page(sessionDate, out, out.size() >= limit ? last : null);
        }
    }

    /** Every page states the same authority the hello does, including its GENERATION. */
    private Page page(String session, List<String> records, String cursor) {
        return new Page(session, records, cursor, historyBeginsAtLocked(), refused.size(), unavailable, !replayComplete, replayBeginsAtMs, authority);
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
    long metadataBytesInView() { synchronized (lock) { return meta; } }
    int refusedIdentities() { synchronized (lock) { return refused.size(); } }
    boolean unavailable() { synchronized (lock) { return unavailable; } }
    boolean loading() { synchronized (lock) { return !replayComplete; } }
    long authority() { synchronized (lock) { return authority; } }
    int indexedTombstones() { synchronized (lock) { return tombstones; } }
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
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    /**
     * A JSON string literal carrying {@code json} verbatim, so a reader receives the exact bytes the fold
     * compared (code round-1, page #1). Escapes are written directly — a general formatter per control
     * character cost 55 ms on a 256 KiB pathological record, on the shared Kafka thread (round-2 #8).
     */
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
                default -> {
                    if (c < 0x20) sb.append('\\').append('u').append('0').append('0').append(HEX[(c >>> 4) & 0xf]).append(HEX[c & 0xf]);
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * The worst case {@link #quoted} can produce for a record of {@code recordBytes} UTF-8 bytes, plus its
     * separator: every byte an ASCII control character becomes six (\\u00xx), and the two quotes ride along.
     * The proxy in front of this gateway sizes its pages with this, not with the raw record size (round-2 #5).
     */
    static long quotedBoundBytes(long recordBytes) { return 6L * recordBytes + 3L; }
}
