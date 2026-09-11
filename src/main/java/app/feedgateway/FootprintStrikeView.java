package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ES-FOOTPRINT-STRIKE-INTERACTION.md R14 — the gateway's fold of the {@code es.futures.footprint.strike}
 * log, the fifth footprint stream on the same relay path (R3): cache + live + backfill.
 *
 * <p>The fold is DEFINED, per identity {@code (symbol, sessionDate, timeframe, strikeCents,
 * openBarStartMs)}: the record with the greatest {@code revision} wins. Two records sharing an
 * identity AND a revision must be identical AS BYTES — checked for EVERY observed revision of an
 * identity, not only its head (code round-1 #1) — else that identity is REFUSED for the incarnation
 * and counted. A refused identity keeps its place in the ordering as a TOMBSTONE: if it is the newest
 * episode of a strike, {@code latest} shows NO ROW for that strike and names it in the page's
 * {@code tombstones} (the chip renders NO DATA), never an older episode in its stead (code round-1 #3);
 * history omits it and still lists the older ones. A CHECKPOINT carries no episode and only advances
 * the per-timeframe high-water mark the hello reports.
 *
 * <p>Scope is by symbol AND timeframe everywhere (code round-1 #2). Unlike the bars/outcomes
 * coordinator this view never rolls a session away: history crosses sessions ("6800 may behave
 * differently on every visit"), and {@code latest} is scoped to ONE session by the reader.
 *
 * <p><b>Retained storage is what is charged (final review #3).</b> A head's payload is retained as the
 * record's UTF-8 {@code byte[]} — never as a {@link String}, whose backing array doubles to UTF-16 the
 * moment one character is above U+00FF — and the budget is charged the ARRAY that is actually held
 * ({@link #arrayBytes}), each retained identity/key string by its actual storage ({@link #stringBytes}),
 * {@link #REVISION_BYTES} per observed revision and a per-entry node overhead. That is an ACCOUNTING
 * bound on what the view retains, not a measured heap ceiling; the design amendment names the
 * measurement that would settle the latter. The record bytes a reader receives are published apart
 * from it ({@link #bytesInView}). Budgets evict the OLDEST identities by {@code openBarStartMs}, and
 * always the WHOLE equal-opening-time bucket, so a monotonic boundary can be published that is strictly
 * above every evicted opening and at or below every retained one: records opening before it are dropped
 * rather than re-admitted, so an evicted identity can never return at a lower revision and no retained
 * head can sit behind the boundary with its updates silently refused (code round-2 #2). When eviction
 * cannot bring the view inside its budget the view goes UNAVAILABLE rather than forgetting evidence —
 * the same rule the refusal ledger already had.
 *
 * <p><b>Loading is distinguishable from empty.</b> Until the cache consumer's strike replay has crossed
 * the end offsets captured when it was (re)opened ({@link #replayRestarted}, {@link #replayCompleted}),
 * the hello and every page say {@code "loading":true}, so a reader can never turn an unfinished replay
 * into a completed NO DATA (code round-2 #3). {@code replayBeginsAtMs} is the cutoff the replay's seek
 * actually used, kept apart from {@code historyBeginsAtMs}, which is retained inventory (code round-2 #6).
 *
 * <p><b>Delivery is ordered with the mutation that caused it (final review #1).</b> Every mutation — an
 * admission from either consumer, a refusal, an eviction, the replay completing or reopening — decides
 * its outcome AND queues the frames it causes (the admitted record's {@code es-footprint-strike}
 * evidence when the live consumer forwards, then the {@code es-footprint-strike-control} authority frame
 * when the authority moved) under the ONE view lock; a per-socket hello's authority is captured and
 * queued the same way ({@link #hello}). The queue is then drained to the sink in that order, OUTSIDE the
 * view lock, by one drainer at a time. A record admitted before a refusal therefore always reaches a
 * socket before that refusal's control frame, never after it, and a socket can never receive a hello
 * older than a control it already holds. Every read is one snapshot under the lock; the lock is never
 * held while a frame is delivered.
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

    /** What a sequenced frame is. */
    enum FrameKind {
        /** An ADMITTED record the live consumer forwards; {@code body} is the record text. */
        EVIDENCE,
        /** The authority changed; {@code body} is the authority field, the same shape the hello carries. */
        CONTROL,
        /** One socket's hello; {@code target} is that socket and {@code body} the authority field captured for it. */
        HELLO
    }

    /** One outbound frame, queued under the view lock by the mutation (or hello) that caused it, delivered in queue order. */
    record Frame(FrameKind kind, Object target, String body) {}

    /** A strike whose newest episode in the requested session is refused: {@code latest} has no row for it, and says so. */
    record Tombstone(long strikeCents, long openBarStartMs) {}

    /** One folded identity: its identity fields and keys, its head revision, the head's UTF-8 bytes and every observed revision's digest. */
    private static final class Head {
        final String identity, symbol, sessionDate, tf, openKey, ageKey; final long strikeCents, openBarStartMs;
        long revision; byte[] payload;
        final Map<Long, byte[]> digests = new HashMap<>();
        Head(String identity, String symbol, String sessionDate, String tf, long strikeCents, long openBarStartMs) {
            this.identity = identity; this.symbol = symbol; this.sessionDate = sessionDate; this.tf = tf; this.strikeCents = strikeCents; this.openBarStartMs = openBarStartMs;
            this.openKey = openKey(sessionDate, openBarStartMs); this.ageKey = ageKey(openBarStartMs, identity);
        }
    }

    /**
     * One page of folded records: ascending by strike for {@code latest}, newest first for {@code history}.
     * {@code payloads} are the retained UTF-8 arrays themselves (never mutated once retained, so they are
     * safe to write after the snapshot); {@code tombstones} is non-null for {@code latest} only.
     */
    record Page(String sessionDate, List<byte[]> payloads, String nextCursor, Long historyBeginsAtMs, long refused,
                boolean unavailable, boolean loading, Long replayBeginsAtMs, long authority, String incarnation,
                List<Tombstone> tombstones) {
        /** The records as text (a convenience for readers of this API; the routes write {@link #payloads} directly). */
        List<String> records() {
            List<String> out = new ArrayList<>(payloads.size());
            for (byte[] p : payloads) out.add(new String(p, StandardCharsets.UTF_8));
            return out;
        }
    }

    static final long EPOCH_MAX_MS = 253_402_300_799_999L;
    static final int LATEST_LIMIT_MAX = 200, HISTORY_LIMIT_MAX = 100;
    /** A symbol longer than this is not a symbol: refusing it keeps one record from charging arbitrary metadata (round-2 #1). */
    static final int MAX_SYMBOL_CHARS = 64;
    /**
     * What one retained revision is CHARGED. The ledger is a {@code HashMap<Long, byte[]>}, so an entry
     * is a boxed key (16), a node (32), an array header plus the digest (16 + 32) and its share of the
     * bucket table (~16) — charged at 128, a deliberate over-estimate of that object graph (gateway
     * round-3 #1).
     */
    static final int REVISION_BYTES = 128;
    /**
     * Per-entry NODE overhead, strings and payload excluded (those are charged by their actual storage):
     * the head object, its heads-map node and table share, the three index nodes (scope tree, strike tree,
     * open-key tree), the age-tree node, the revision map's own object and initial table. A tombstone
     * keeps its index node and its refusal-set node and table share. Both are deliberate over-estimates.
     */
    static final int HEAD_OVERHEAD = 512, TOMBSTONE_OVERHEAD = 256;
    /** Object header of a String instance (header + hash + coder + value reference), 8-aligned, with compressed oops. */
    static final int STRING_OBJECT_BYTES = 24;
    /** Array header (mark word, compressed class pointer, length). */
    static final int ARRAY_HEADER_BYTES = 16;
    private static final Set<String> EPISODE_KINDS = Set.of("OPEN", "UPDATE", "CLOSE");

    private final ObjectMapper mapper;
    private final long maxRecordBytes, maxBytes;
    private final int maxEpisodes, maxRefused;
    /**
     * Fixed for this view's life — one per gateway process. It rides the hello, the control frame and
     * every page beside {@code authority}, so a reader that meets a different one knows the authority
     * sequence restarted (a new process starts again at 0) and resets its comparison instead of
     * discarding the new process's authority as older.
     */
    private final String incarnation = UUID.randomUUID().toString();
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
    private long bytes;                                         // record bytes of the heads (what a reader receives, before quoting)
    private long retained;                                      // storage of the retained payload arrays (what the budget charges)
    private long meta;                                          // strings, nodes, revision ledgers, tombstones
    private int tombstones;
    private String sessionDate;
    private Long boundaryMs;                                    // monotonic: nothing that opened before it is admitted or retained
    private Long replayBeginsAtMs;
    private boolean replayComplete;
    /**
     * Monotonic within the incarnation. Every authority change bumps it, and it rides the hello, the
     * control frame and every page, so a reader can discard an OLDER authority that arrives after a
     * newer one (gateway round-3 #3).
     */
    private long authority;
    private boolean unavailable;
    /** Frames queued by mutations and hellos, in the order the mutations happened. Guarded by {@link #lock}. */
    private final ArrayDeque<Frame> outbox = new ArrayDeque<>();
    /** One drainer at a time, so the queue's order is the order frames reach the sink. Never taken while holding {@link #lock}. */
    private final Object drainLock = new Object();
    private volatile Consumer<Frame> sink = f -> {};
    /**
     * Test seam: runs after a mutation has queued its frames and released the view lock, BEFORE this
     * thread drains — exactly where a preempted consumer thread would pause. Null in production.
     */
    volatile Runnable afterDecisionForTest;
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

    /** Where the sequenced frames go, in order. The service turns them into socket frames. */
    void onFrame(Consumer<Frame> sink) { this.sink = sink == null ? f -> {} : sink; }

    /**
     * A (re)opened replay of the strike partitions: readers are LOADING until {@link #replayCompleted}.
     * {@code beginsAtMs} is the cutoff the seek ACTUALLY used (null if none was sought), passed in by the
     * caller that sought, never recomputed from the clock (round-3 #5, final review #2). Reopening a
     * completed replay is an authority change.
     */
    void replayRestarted(Long beginsAtMs) {
        synchronized (lock) {
            replayBeginsAtMs = beginsAtMs;
            if (replayComplete) { replayComplete = false; authority++; outbox.add(control()); }
        }
        drain();
    }

    /** Every partition of the open replay has crossed its end offset, after its records were applied. Idempotent. */
    void replayCompleted() {
        synchronized (lock) {
            if (!replayComplete) { replayComplete = true; authority++; outbox.add(control()); }
        }
        drain();
    }

    /**
     * Queue {@code target}'s hello with the authority as of NOW, in sequence with every control frame:
     * a control queued before it carries an authority at most the hello's, and one queued after it is
     * newer. Delivered before this returns (unless the sink throws).
     */
    void hello(Object target) {
        synchronized (lock) { outbox.add(new Frame(FrameKind.HELLO, target, helloFieldLocked())); }
        drain();
    }

    // ---- admission --------------------------------------------------------------------------------

    /** Admit without forwarding (the cache consumer, and every caller that is not the live broadcast path). */
    Admission admit(String json) { return admit(json, false); }

    /**
     * Admit one record. When {@code forward} is set (the live consumer) and the record is ADMITTED, its
     * evidence frame is queued under the same lock as the decision; a control frame follows whenever the
     * decision moved the authority. Both are delivered, in that order, before this returns.
     */
    Admission admit(String json, boolean forward) {
        if (json == null) return Admission.SHAPE;
        if (FootprintViews.utf8Length(json) > maxRecordBytes) return Admission.OVERSIZE;
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
            synchronized (lock) {
                if (unavailable) return Admission.UNAVAILABLE;
                advance(symbol, tf, seen, date);
                if (forward) outbox.add(new Frame(FrameKind.EVIDENCE, null, json));
            }
            deliver();
            return Admission.CHECKPOINT;
        }
        if (!EPISODE_KINDS.contains(kind)) return Admission.SHAPE;
        long strike = nonNegative(root, "strikeCents"), open = epoch(root, "openBarStartMs"), revision = nonNegative(root, "revision");
        if (date == null || strike < 0 || open < 0 || revision < 0 || !root.path("series").isArray()) return Admission.SHAPE;
        String identity = symbol + "|" + date + "|" + tf + "|" + strike + "|" + open;
        // RETAINED as UTF-8, and digested from the very bytes retained (final review #3)
        byte[] utf8 = json.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > maxRecordBytes) return Admission.OVERSIZE;
        byte[] digest = sha256(utf8);
        Admission outcome;
        synchronized (lock) {
            long before = authority;
            outcome = foldLocked(identity, symbol, date, tf, strike, open, revision, seen, utf8, digest);
            // the admitted record FIRST, then what the same mutation did to the authority
            if (forward && outcome.reason() == Reason.ADMITTED) outbox.add(new Frame(FrameKind.EVIDENCE, null, json));
            if (authority != before) outbox.add(control());
        }
        deliver();
        return outcome;
    }

    private Admission foldLocked(String identity, String symbol, LocalDate date, String tf, long strike, long open,
                                 long revision, long seen, byte[] utf8, byte[] digest) {
        if (unavailable) return Admission.UNAVAILABLE;
        advance(symbol, tf, seen, date);
        if (boundaryMs != null && open < boundaryMs) return Admission.EVICTED;   // before the published boundary: never re-admitted
        if (refused.contains(identity)) return Admission.REFUSED;
        Head h = heads.get(identity);
        if (h != null) {
            byte[] prior = h.digests.get(revision);
            if (prior != null) {
                if (Arrays.equals(prior, digest)) return Admission.EPISODE;   // a bar colliding with itself (R6)
                refuse(h);                                                     // R14: a fault, not a tie to break
                collisions.incrementAndGet();
                authority++;                                                   // the readers hold a value this fold has just withdrawn
                return unavailable ? Admission.UNAVAILABLE : Admission.COLLISION;
            }
            h.digests.put(revision, digest); meta += REVISION_BYTES;
            if (revision >= h.revision) {
                bytes += utf8.length - h.payload.length;
                retained += arrayBytes(utf8.length) - arrayBytes(h.payload.length);
                h.revision = revision; h.payload = utf8;
            }
            // an older revision after a newer one keeps its digest (R14) and nothing else
            enforce();
            return heads.containsKey(identity) ? Admission.EPISODE : Admission.EVICTED;
        }
        h = new Head(identity, symbol, date.toString(), tf, strike, open);
        h.revision = revision; h.payload = utf8; h.digests.put(revision, digest);
        heads.put(identity, h);
        index.computeIfAbsent(scope(symbol, tf), k -> new TreeMap<>()).computeIfAbsent(strike, k -> new TreeMap<>()).put(h.openKey, identity);
        byAge.put(h.ageKey, identity);
        bytes += utf8.length; retained += arrayBytes(utf8.length); meta += headMeta(h);
        enforce();
        // enforcement can evict the very identity just inserted: say so rather than reporting it
        // admitted while the fold no longer holds it (round-3 #4)
        return heads.containsKey(identity) ? Admission.EPISODE : Admission.EVICTED;
    }

    private Frame control() { return new Frame(FrameKind.CONTROL, null, helloFieldLocked()); }

    private void deliver() {
        Runnable pause = afterDecisionForTest;
        if (pause != null) pause.run();
        drain();
    }

    /**
     * Hand every queued frame to the sink, in queue order, one drainer at a time and never under the view
     * lock. A caller whose frame another drainer is already delivering waits for it, so when a mutation
     * returns its own frames have been handed on. A sink that throws leaves the frames behind it queued for
     * the next drain, in order.
     */
    void drain() {
        synchronized (drainLock) {
            for (Frame f = nextFrame(); f != null; f = nextFrame()) sink.accept(f);
        }
    }

    private Frame nextFrame() { synchronized (lock) { return outbox.poll(); } }

    /**
     * The high-water mark and the session the HELLO reports belong to the symbol the routes answer for
     * (round-3 #8): a foreign symbol's CHECKPOINT could otherwise advance a hello labelled with the
     * configured one, and the reader would install that session over its own — bypassing the very
     * symbol filter the live path enforces. A record of another symbol still folds into the index and
     * is still served by an explicit `symbol=` request; it just does not speak for this hello.
     */
    private void advance(String symbol, String tf, long seen, LocalDate date) {
        if (!scopeSymbol.isEmpty() && !scopeSymbol.equals(symbol)) return;
        Long h = hwm.get(tf);
        if (h == null || seen > h) hwm.put(tf, seen);
        if (date != null && (sessionDate == null || date.toString().compareTo(sessionDate) > 0)) sessionDate = date.toString();
    }

    /** What one live head's non-payload storage is charged: its node overhead, every string it retains, and its revision ledger. */
    private static long headMeta(Head h) {
        return HEAD_OVERHEAD + stringBytes(h.identity) + stringBytes(h.symbol) + stringBytes(h.sessionDate) + stringBytes(h.tf)
                + stringBytes(h.openKey) + stringBytes(h.ageKey) + (long) REVISION_BYTES * h.digests.size();
    }

    /** What a tombstone keeps: its index node and key, and its identity in the refusal set. */
    private static long tombstoneMeta(Head h) { return TOMBSTONE_OVERHEAD + stringBytes(h.identity) + stringBytes(h.openKey); }

    /** Refuse an identity: its bytes leave, its tombstone stays in the index, the refusal is remembered — within a bound. */
    private void refuse(Head h) {
        bytes -= h.payload.length;
        retained -= arrayBytes(h.payload.length);
        meta -= headMeta(h);
        h.payload = null; h.digests.clear();
        heads.remove(h.identity);
        byAge.remove(h.ageKey);
        if (refused.add(h.identity)) { meta += tombstoneMeta(h); tombstones++; }
        if (refused.size() > maxRefused) unavailable = true;    // the ledger would have to forget a refusal: fail closed instead
    }

    private void removeLive(Head h) {
        heads.remove(h.identity);
        TreeMap<Long, TreeMap<String, String>> perScope = index.get(scope(h.symbol, h.tf));
        if (perScope != null) {
            TreeMap<String, String> per = perScope.get(h.strikeCents);
            if (per != null) { per.remove(h.openKey); if (per.isEmpty()) perScope.remove(h.strikeCents); }
            if (perScope.isEmpty()) index.remove(scope(h.symbol, h.tf));
        }
        byAge.remove(h.ageKey);
        bytes -= h.payload.length;
        retained -= arrayBytes(h.payload.length);
        meta -= headMeta(h);
    }

    /**
     * Oldest identities go first, whatever their scope, and always the WHOLE equal-opening-time bucket, so
     * the boundary that follows is strictly above every evicted opening and at or below every retained one
     * (round-2 #2). An eviction that moves the boundary, or failing closed, bumps the authority.
     */
    private void enforce() {
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
            return;
        }
        // an eviction MOVES the boundary, which changes what every reader may hold: that is an authority
        // change too, not merely bookkeeping (round-3 #4)
        if (!java.util.Objects.equals(boundaryBefore, boundaryMs)) authority++;
    }

    private boolean overBudget() { return heads.size() > maxEpisodes || retained + meta > maxBytes; }

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
     * The hello field, and the body of every control frame:
     * {@code {"authority":n,"incarnation":"..","symbol":..,"sessionDate":..,"hwm":{tf:seenMax},"historyBeginsAtMs":..,"replayBeginsAtMs":..,"loading":bool,"refused":n,"unavailable":bool}}.
     */
    String helloField() { synchronized (lock) { return helloFieldLocked(); } }

    private String helloFieldLocked() {
        StringBuilder sb = new StringBuilder("{\"authority\":").append(authority)
                .append(",\"incarnation\":\"").append(incarnation).append('"')
                .append(",\"symbol\":").append(quoted(scopeSymbol)).append(",\"sessionDate\":")
                .append(sessionDate == null ? "null" : "\"" + sessionDate + "\"").append(",\"hwm\":{");
        boolean first = true;
        for (Map.Entry<String, Long> e : hwm.entrySet()) { if (!first) sb.append(','); sb.append('"').append(e.getKey()).append("\":").append(e.getValue()); first = false; }
        Long history = historyBeginsAtLocked();
        sb.append("},\"historyBeginsAtMs\":").append(history == null ? "null" : history);
        sb.append(",\"replayBeginsAtMs\":").append(replayBeginsAtMs == null ? "null" : replayBeginsAtMs);
        sb.append(",\"loading\":").append(!replayComplete);
        return sb.append(",\"refused\":").append(refused.size()).append(",\"unavailable\":").append(unavailable).append('}').toString();
    }

    /**
     * {@code latest} (R14): for ONE symbol, ONE timeframe and ONE session, the folded record of the
     * episode with the greatest {@code openBarStartMs} per strike, ascending by strike; {@code afterStrike}
     * is an exclusive cursor. A strike whose newest episode is REFUSED has no row (NO DATA), never an
     * older one, and is named in {@code tombstones}; it counts as visited for the cursor. A strike with no
     * episode in that session simply has no row.
     */
    Page latest(String symbol, String tf, String session, long afterStrikeExclusive, int limit) {
        synchronized (lock) {
            List<byte[]> out = new ArrayList<>();
            List<Tombstone> dead = new ArrayList<>();
            Long last = null;
            TreeMap<Long, TreeMap<String, String>> perScope = index.get(scope(symbol, tf));
            if (!unavailable && perScope != null && session != null && !session.isEmpty()) {
                String lo = session + "|", hi = session + "|~";
                for (Map.Entry<Long, TreeMap<String, String>> e : perScope.tailMap(afterStrikeExclusive, false).entrySet()) {
                    Map.Entry<String, String> newest = e.getValue().subMap(lo, true, hi, true).lastEntry();   // greatest openBarStartMs in that session
                    last = e.getKey();
                    if (newest == null) continue;
                    Head h = heads.get(newest.getValue());
                    if (h == null) { dead.add(new Tombstone(e.getKey(), openMsOf(newest.getKey()))); continue; }   // refused: NO DATA, and said
                    out.add(h.payload);
                    if (out.size() >= limit) break;
                }
            }
            return page(session, out, out.size() >= limit && last != null ? Long.toString(last) : null, List.copyOf(dead));
        }
    }

    /**
     * {@code history} (R14/R18/R20): every folded episode of one strike in one symbol and timeframe, NEWEST
     * first, ACROSS sessions, refused identities omitted; {@code before} is an exclusive opaque cursor
     * ({@code sessionDate|%019d(openBarStartMs)}).
     */
    Page history(String symbol, String tf, long strikeCents, String beforeExclusive, int limit) {
        synchronized (lock) {
            List<byte[]> out = new ArrayList<>();
            String last = null;
            TreeMap<Long, TreeMap<String, String>> perScope = index.get(scope(symbol, tf));
            TreeMap<String, String> per = perScope == null ? null : perScope.get(strikeCents);
            if (!unavailable && per != null) {
                var view = beforeExclusive == null || beforeExclusive.isEmpty() ? per.descendingMap() : per.headMap(beforeExclusive, false).descendingMap();
                for (Map.Entry<String, String> e : view.entrySet()) {
                    last = e.getKey();
                    Head h = heads.get(e.getValue());
                    if (h == null) continue;                                                              // refused: omitted, and the walk continues
                    out.add(h.payload);
                    if (out.size() >= limit) break;
                }
            }
            return page(sessionDate, out, out.size() >= limit ? last : null, null);
        }
    }

    /** Every page states the same authority and incarnation the hello does. */
    private Page page(String session, List<byte[]> records, String cursor, List<Tombstone> tombstoneList) {
        return new Page(session, List.copyOf(records), cursor, historyBeginsAtLocked(), refused.size(), unavailable, !replayComplete,
                replayBeginsAtMs, authority, incarnation, tombstoneList);
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

    String incarnation() { return incarnation; }
    String sessionDate() { synchronized (lock) { return sessionDate; } }
    Long historyBeginsAtMs() { synchronized (lock) { return historyBeginsAtLocked(); } }
    Long replayBeginsAtMs() { synchronized (lock) { return replayBeginsAtMs; } }
    int episodesInView() { synchronized (lock) { return heads.size(); } }
    /** The record bytes the retained heads hold — what a reader receives before quoting; NOT the budget. */
    long bytesInView() { synchronized (lock) { return bytes; } }
    /** The storage of the retained payload arrays, as charged to the budget. */
    long retainedBytesInView() { synchronized (lock) { return retained; } }
    long metadataBytesInView() { synchronized (lock) { return meta; } }
    /** What the byte budget acts on: retained payload storage plus metadata. */
    long chargedBytesInView() { synchronized (lock) { return retained + meta; } }
    int refusedIdentities() { synchronized (lock) { return refused.size(); } }
    boolean unavailable() { synchronized (lock) { return unavailable; } }
    boolean loading() { synchronized (lock) { return !replayComplete; } }
    long authority() { synchronized (lock) { return authority; } }
    int indexedTombstones() { synchronized (lock) { return tombstones; } }
    int queuedFrames() { synchronized (lock) { return outbox.size(); } }
    long evictions() { return evictions.get(); }
    long collisions() { return collisions.get(); }

    /** Test seam: the length of every payload array the view actually retains, so storage can be checked against its charge. */
    List<Integer> retainedPayloadLengthsForTest() {
        synchronized (lock) {
            List<Integer> out = new ArrayList<>();
            for (Head h : heads.values()) out.add(h.payload.length);
            return out;
        }
    }

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

    /** The heap an array of {@code length} bytes occupies: its header, rounded up to the 8-byte object alignment. */
    static long arrayBytes(long length) { return (ARRAY_HEADER_BYTES + length + 7) & ~7L; }

    /**
     * The heap a String occupies under COMPACT strings (which the preflight requires, G-R8): its object
     * plus its backing array — one byte per char while every char is Latin-1, TWO per char as soon as one
     * is not. Charging the UTF-8 length instead is what let a mostly-ASCII string with one wide character
     * hold twice what it was charged (final review #3).
     */
    static long stringBytes(String s) {
        int n = s.length();
        boolean latin1 = true;
        for (int i = 0; i < n && latin1; i++) latin1 = s.charAt(i) <= 0xFF;
        return STRING_OBJECT_BYTES + arrayBytes(latin1 ? n : 2L * n);
    }

    static byte[] sha256(String s) { return sha256(s.getBytes(StandardCharsets.UTF_8)); }
    static byte[] sha256(byte[] b) {
        try { return MessageDigest.getInstance("SHA-256").digest(b); }
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
     * {@link #quoted} applied to a record held as UTF-8, streamed straight to {@code out}: the bytes written
     * are exactly {@code quoted(new String(utf8, UTF_8)).getBytes(UTF_8)}. Every byte the escaping touches is
     * ASCII, and every byte of a multi-byte UTF-8 sequence is ≥ 0x80, so escaping byte by byte can never
     * split or alter a character — and the page never decodes the record back into a String to write it.
     */
    static void writeQuoted(OutputStream out, byte[] utf8) throws IOException {
        byte[] buf = new byte[8192];
        int n = 0;
        buf[n++] = '"';
        for (byte b : utf8) {
            if (n > buf.length - 7) { out.write(buf, 0, n); n = 0; }
            int c = b & 0xff;
            switch (c) {
                case '"' -> { buf[n++] = '\\'; buf[n++] = '"'; }
                case '\\' -> { buf[n++] = '\\'; buf[n++] = '\\'; }
                case '\n' -> { buf[n++] = '\\'; buf[n++] = 'n'; }
                case '\r' -> { buf[n++] = '\\'; buf[n++] = 'r'; }
                case '\t' -> { buf[n++] = '\\'; buf[n++] = 't'; }
                default -> {
                    if (c < 0x20) {
                        buf[n++] = '\\'; buf[n++] = 'u'; buf[n++] = '0'; buf[n++] = '0';
                        buf[n++] = (byte) HEX[(c >>> 4) & 0xf]; buf[n++] = (byte) HEX[c & 0xf];
                    } else {
                        buf[n++] = b;
                    }
                }
            }
        }
        buf[n++] = '"';
        out.write(buf, 0, n);
    }

    /** {@link #writeQuoted} into a fresh array. */
    static byte[] quotedUtf8(byte[] utf8) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(utf8.length + 16);
        try { writeQuoted(out, utf8); } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return out.toByteArray();
    }

    /**
     * The worst case {@link #quoted} can produce for a record of {@code recordBytes} UTF-8 bytes, plus its
     * separator: every byte an ASCII control character becomes six (\\u00xx), and the two quotes ride along.
     * The proxy in front of this gateway sizes its pages with this, not with the raw record size (round-2 #5).
     */
    static long quotedBoundBytes(long recordBytes) { return 6L * recordBytes + 3L; }
}
