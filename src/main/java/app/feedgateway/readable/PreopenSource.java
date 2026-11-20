package app.feedgateway.readable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The newest pre-open gamma record per strike, as the gateway taps them off
 * {@code options.databento.gex.strike.preopen}.
 *
 * <p>That topic is <b>plain delete, never compacted</b>, by the deploy repository's own note: the
 * context tape seeks back and replays it, and compaction would drop a strike's earlier pre-open
 * buckets. So several records for one strike are the ordinary case here, and this store keeps the
 * newest of them rather than the last one to arrive — a delete-only log can redeliver out of order
 * after a restart, and "last seen" would then be an older number than the one it replaced.
 *
 * <p><b>Why yesterday's board is pruned by the data and not by the clock.</b> A pre-open record
 * carries the 0DTE expiry of its own session, so the newest record's own New York date is what
 * "today" means here. Keys for an earlier expiry are dropped once a newer session's records
 * arrive. Pruning on the wall clock instead would make the board depend on when the gateway
 * happened to be restarted.
 */
public final class PreopenSource {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * A ceiling on distinct strikes held, purely to bound memory. Measured against the real feed
     * it never binds: the 2026-11-19 pre-open board carried 61 distinct strikes for its expiry.
     * A record refused at the ceiling is counted and never dropped silently, because a missing
     * strike would print as "no row" and read as a gap in the feed rather than a limit of ours.
     */
    static final int MAX_STRIKES = 8_192;

    private final Map<String, Held> held = new ConcurrentHashMap<>();
    private final AtomicLong observed = new AtomicLong();
    private final AtomicLong unreadable = new AtomicLong();
    private final AtomicLong refusedAtCap = new AtomicLong();
    private final AtomicLong pruned = new AtomicLong();
    private volatile String newestExpirySeen;

    private record Held(String json, long updatedAtMs) {
    }

    /** Take one record off the topic. Anything unreadable is counted, never thrown. */
    public void observe(String json) {
        PreopenBoard.Row row = PreopenBoard.decode(json);
        if (row == null) {
            unreadable.incrementAndGet();
            return;
        }
        observed.incrementAndGet();
        String key = row.expiry() + "|" + Math.round(row.strike() * 100.0);
        Held existing = held.get(key);
        if (existing != null && existing.updatedAtMs() >= row.updatedAtMs()) {
            return;                                   // an older or equal redelivery
        }
        if (existing == null && held.size() >= MAX_STRIKES) {
            refusedAtCap.incrementAndGet();
            return;
        }
        held.put(key, new Held(json, row.updatedAtMs()));
        pruneExpiriesBefore(sessionDateOf(row.updatedAtMs()));
    }

    /**
     * Drop every key whose expiry is before the session date given. Called on each put, which is
     * cheap because the map holds hundreds of entries rather than thousands.
     */
    private void pruneExpiriesBefore(String sessionDate) {
        if (newestExpirySeen != null && newestExpirySeen.compareTo(sessionDate) >= 0) {
            return;
        }
        newestExpirySeen = sessionDate;
        List<String> stale = new ArrayList<>();
        for (String key : held.keySet()) {
            if (key.substring(0, key.indexOf('|')).compareTo(sessionDate) < 0) {
                stale.add(key);
            }
        }
        for (String key : stale) {
            held.remove(key);
            pruned.incrementAndGet();
        }
    }

    private static String sessionDateOf(long updatedAtMs) {
        return EXPIRY.format(Instant.ofEpochMilli(updatedAtMs).atZone(NY));
    }

    /** The records the board is built from, exactly as published. */
    public Collection<String> records() {
        List<String> out = new ArrayList<>(held.size());
        for (Held h : held.values()) {
            out.add(h.json());
        }
        return out;
    }

    /** The pre-open board, or null while there is nothing to build one from. */
    public ReadablePage.Board board(long nowMs) {
        return PreopenBoard.from(records(), nowMs);
    }

    public int strikesHeld() {
        return held.size();
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

    public long prunedForOlderExpiry() {
        return pruned.get();
    }
}
