package app.feedgateway.pinflow;

import app.feedgateway.GatewaySettings;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only data access + short-lived result cache for {@code GET /api/pin-flow} (§5.1 / §6.5).
 *
 * <p>The heavy aggregation lives in the pure {@link PinFlowAggregator}; this class owns ONLY the JDBC
 * fetch (parameterized {@link PreparedStatement}, per-query timeout, read-only connection) and the
 * {@code (date,lo,hi)} coalescing cache. The {@link DataSource} is optional: when it is {@code null}
 * (Postgres not configured, §6.3) every query throws {@link DbUnavailableException} → the controller
 * maps it to 503.
 */
public final class PinFlowStore {

    /** DB unavailable / disabled / not configured / timed out / pool-exhausted → 503 (§5.3). */
    public static final class DbUnavailableException extends RuntimeException {
        public DbUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public DbUnavailableException(String message) {
            super(message);
        }
    }

    // Session-to-date cumulative sell notionals + spot, band-filtered, floored to the minute.
    private static final String STRIKE_SQL =
            "SELECT date_trunc('minute', as_of_minute) AS m, strike, "
                    + "call_sell_notional, put_sell_notional, spot, trade_date "
                    + "FROM pin_strike_minute "
                    + "WHERE as_of_minute >= ? AND as_of_minute < ? "
                    + "AND strike >= ? AND strike <= ?";

    // Self-gex over the WIDENED gamma band, band-filtered, floored to the minute.
    private static final String GEX_SQL =
            "SELECT date_trunc('minute', as_of_minute) AS m, strike, net_gex "
                    + "FROM pin_self_gex_minute "
                    + "WHERE as_of_minute >= ? AND as_of_minute < ? "
                    + "AND strike >= ? AND strike <= ?";

    private static final String SPOT_RANGE_SQL =
            "SELECT min(spot), max(spot) FROM pin_strike_minute "
                    + "WHERE as_of_minute >= ? AND as_of_minute < ? AND spot IS NOT NULL";

    private final DataSource dataSource; // null → DB not configured
    private final ZoneId zone;
    private final java.time.LocalTime sessionStart;
    private final java.time.LocalTime sessionEnd;
    private final int queryTimeoutSeconds;
    private final long cacheTtlMs;
    // Atomic per-key coalescing: the first caller for a (date,lo,hi) installs a future and runs the DB
    // work; concurrent callers for the SAME key await that same future → exactly one DB fetch per key.
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    // Derived-band cache: the band only needs re-deriving as often as the response itself, so reuse the
    // same TTL. Without it every request (INCLUDING response-cache hits) paid an extra DB round-trip.
    private final Map<LocalDate, BandEntry> bandCache = new ConcurrentHashMap<>();

    public PinFlowStore(GatewaySettings settings, DataSource dataSource) {
        this.dataSource = dataSource;
        this.zone = settings.pinFlowZone();
        this.sessionStart = settings.pinFlowSessionStart();
        this.sessionEnd = settings.pinFlowSessionEnd();
        this.queryTimeoutSeconds = settings.pinFlowQueryTimeoutSeconds();
        this.cacheTtlMs = settings.pinFlowCacheTtlMs();
    }

    /** Test seam: explicit datasource + params, no Spring settings. */
    PinFlowStore(DataSource dataSource, ZoneId zone, int queryTimeoutSeconds, long cacheTtlMs) {
        this(dataSource, zone, java.time.LocalTime.of(9, 30), java.time.LocalTime.of(16, 1),
                queryTimeoutSeconds, cacheTtlMs);
    }

    /** Test seam with an explicit session window (covers midnight-spanning sessions like ES). */
    PinFlowStore(DataSource dataSource, ZoneId zone, java.time.LocalTime sessionStart,
                 java.time.LocalTime sessionEnd, int queryTimeoutSeconds, long cacheTtlMs) {
        this.dataSource = dataSource;
        this.zone = zone;
        this.sessionStart = sessionStart;
        this.sessionEnd = sessionEnd;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.cacheTtlMs = cacheTtlMs;
    }

    public boolean dbConfigured() {
        return dataSource != null;
    }

    public ZoneId zone() {
        return zone;
    }

    /** Session window start (local time in {@link #zone()}). */
    public java.time.LocalTime sessionStart() {
        return sessionStart;
    }

    /** True when the session crosses midnight (end <= start), e.g. ES 18:00 → 17:00. */
    public boolean sessionSpansMidnight() {
        return !sessionEnd.isAfter(sessionStart);
    }

    private record CacheEntry(long builtAtMs, java.util.concurrent.CompletableFuture<PinFlowResponse> future) {
    }

    private record BandEntry(long builtAtMs, int lo, int hi) {
    }

    /**
     * Serve {@code [date, lo, hi]} — from the short-lived cache when fresh, else a fresh read+aggregate.
     * {@code nowMs} is the current clock (injected so tests are deterministic).
     *
     * <p>Coalescing is atomic: {@code computeIfAbsent} guarantees exactly one caller installs the future
     * for a key; that caller runs the DB work inline (on its own bulkhead thread — no extra threads are
     * spawned) and completes the future. Concurrent callers for the same key see the in-flight future and
     * block on it, so N simultaneous misses trigger ONE fetch. On failure the entry is removed so errors
     * are never cached; successful entries expire after the TTL.
     */
    public PinFlowResponse query(LocalDate date, int lo, int hi, long nowMs) {
        if (dataSource == null) {
            throw new DbUnavailableException("pin-flow datasource not configured");
        }
        String key = date + "|" + lo + "|" + hi;

        // Drop a stale (expired) success so the next miss re-fetches; in-flight entries are never touched.
        CacheEntry existing = cache.get(key);
        if (existing != null && existing.future().isDone() && nowMs - existing.builtAtMs() >= cacheTtlMs) {
            cache.remove(key, existing);
        }

        boolean[] iComputed = {false};
        CacheEntry entry = cache.computeIfAbsent(key, k -> {
            iComputed[0] = true;
            return new CacheEntry(nowMs, new java.util.concurrent.CompletableFuture<>());
        });

        if (iComputed[0]) {
            // We are the sole owner for this key: run the DB work on THIS (bulkhead) thread.
            try {
                PinFlowResponse fresh = readAndAggregate(date, lo, hi);
                entry.future().complete(fresh);
                if (cache.size() > 256) {
                    cache.entrySet().removeIf(e -> e.getValue().future().isDone()
                            && nowMs - e.getValue().builtAtMs() >= cacheTtlMs);
                }
                return fresh;
            } catch (RuntimeException failure) {
                // Never cache an error: remove our entry (only if still ours) and fail the waiters.
                cache.remove(key, entry);
                entry.future().completeExceptionally(failure);
                throw failure;
            }
        }

        // Another caller owns the fetch (or it is already complete): await the shared result.
        return awaitShared(entry.future());
    }

    /**
     * Followers (coalesced callers that did NOT win {@code computeIfAbsent}) await the OWNER's shared
     * future. This wait MUST be interruptible and deadline-bounded: {@link java.util.concurrent.CompletableFuture#join()}
     * is neither, so a follower whose bulkhead task is cancelled (deferred timeout / client disconnect →
     * {@code Future.cancel(true)} → thread interrupt) would stay pinned to a bulkhead thread until the
     * shared DB query finishes, defeating the deadline/cancellation guarantees.
     *
     * <p>So we use {@code future.get(timeout)} instead:
     * <ul>
     *   <li><b>Interrupted</b> → restore the interrupt flag and throw {@link DbUnavailableException} (→ 503):
     *       the follower releases its bulkhead thread immediately. We do NOT cancel the shared owner future —
     *       only the OWNER thread cancels its own JDBC (via {@link InterruptWatcher}); other waiters or the
     *       owner may still complete it.</li>
     *   <li><b>Timeout</b> (bound exceeded) → throw {@link DbUnavailableException} (→ 503), release the thread.</li>
     *   <li><b>ExecutionException</b> → unwrap and surface as before (DB errors as {@link DbUnavailableException}).
     *       The owner already removed the failed entry from the cache, so the error is never cached.</li>
     * </ul>
     *
     * <p>The wait is bounded by the query timeout: that is the upper bound on how long the OWNER's DB work
     * can legitimately take, so a follower never blocks longer than a fresh query would.
     */
    private PinFlowResponse awaitShared(java.util.concurrent.CompletableFuture<PinFlowResponse> future) {
        // Bound the follower's wait by the query timeout — the max the owner's DB work can take. Guard
        // against a non-positive/zero configured timeout by falling back to a small sane bound.
        long boundMs = queryTimeoutSeconds > 0 ? queryTimeoutSeconds * 1000L : 1000L;
        try {
            return future.get(boundMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            // Our bulkhead task was cancelled (deadline / disconnect). Release this thread NOW; do NOT
            // cancel the shared owner future — only the owner cancels its own JDBC.
            Thread.currentThread().interrupt();
            throw new DbUnavailableException("pin-flow request cancelled", interrupted);
        } catch (java.util.concurrent.TimeoutException timedOut) {
            throw new DbUnavailableException("pin-flow request cancelled", timedOut);
        } catch (java.util.concurrent.ExecutionException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof DbUnavailableException dbDown) {
                throw dbDown;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new DbUnavailableException("pin-flow query failed", cause);
        }
    }

    /** [start,end) instants for the session on {@code date}, honouring a midnight-spanning window. */
    private ZonedDateTime[] sessionRange(LocalDate date) {
        ZonedDateTime start = date.atTime(sessionStart).atZone(zone);
        ZonedDateTime end = sessionEnd.isAfter(sessionStart)
                ? date.atTime(sessionEnd).atZone(zone)
                : date.plusDays(1).atTime(sessionEnd).atZone(zone);
        return new ZonedDateTime[]{start, end};
    }

    /**
     * Strike band derived from the session's ACTUAL spot travel: {@code [min(spot)-margin,
     * max(spot)+margin]} snapped to the 5-point grid. Returns {@code null} when the DB is absent or the
     * session has no spot yet, so the caller falls back to the configured defaults.
     *
     * <p>Why: the configured default band is a FIXED range (historically 7490..7590). It does not follow
     * the market, so once spot drifts out of it the Flow Explorer silently shows a sliver of the chain —
     * on 2026-07-17 prod SPX ran 7435..7497 against a 7490..7590 band, hiding nearly the whole session,
     * and on ES it hid every put below spot. Deriving the band from spot removes that whole class of bug.
     */
    public int[] resolveBandFromSpot(LocalDate date, int marginPts, long nowMs) {
        if (dataSource == null) {
            return null;
        }
        // Serve from the band cache when fresh — this is what keeps response-cache HITS from paying an
        // extra DB round-trip just to re-derive a band that has not meaningfully moved.
        BandEntry cached = bandCache.get(date);
        if (cached != null && (nowMs - cached.builtAtMs()) < cacheTtlMs) {
            return new int[]{cached.lo(), cached.hi()};
        }
        ZonedDateTime[] range = sessionRange(date);
        Connection conn;
        try {
            conn = dataSource.getConnection();
        } catch (SQLException e) {
            return null;
        }
        // Same §6.1 cancellation contract as the main read: if the worker is interrupted (deadline /
        // client disconnect) the watcher cancels this statement and closes the connection promptly
        // instead of holding a pooled connection for the full statement timeout.
        java.util.concurrent.atomic.AtomicReference<PreparedStatement> active =
                new java.util.concurrent.atomic.AtomicReference<>();
        InterruptWatcher watcher = new InterruptWatcher(Thread.currentThread(), active, conn);
        watcher.start();
        try (PreparedStatement ps = conn.prepareStatement(SPOT_RANGE_SQL)) {
            active.set(ps);
            ps.setQueryTimeout(queryTimeoutSeconds);
            ps.setTimestamp(1, Timestamp.from(range[0].toInstant()));
            ps.setTimestamp(2, Timestamp.from(range[1].toInstant()));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                java.math.BigDecimal min = rs.getBigDecimal(1);
                java.math.BigDecimal max = rs.getBigDecimal(2);
                if (min == null || max == null) {
                    return null;
                }
                int lo = (int) (Math.floor((min.doubleValue() - marginPts) / 5.0) * 5);
                int hi = (int) (Math.ceil((max.doubleValue() + marginPts) / 5.0) * 5);
                lo = Math.max(1, lo);
                hi = Math.max(lo, hi);
                bandCache.put(date, new BandEntry(nowMs, lo, hi));
                return new int[]{lo, hi};
            }
        } catch (SQLException | RuntimeException ignored) {
            return null; // never fail the request on band derivation — fall back to the defaults
        } finally {
            active.set(null);
            watcher.stop();
            try {
                conn.close();
            } catch (SQLException ignored) {
                // best-effort; the watcher may have closed it already on cancel.
            }
        }
    }

    private PinFlowResponse readAndAggregate(LocalDate date, int lo, int hi) {
        // §5.1 rule 2: session window as an instant range. The window is CONFIGURABLE (defaults to the
        // SPX cash session 09:30 → 16:01 ET). When end <= start the session SPANS MIDNIGHT (e.g. ES:
        // 18:00 ET → 17:00 ET next day), so the end rolls to date+1 — otherwise the range would be
        // empty/inverted and the Explorer would show nothing for ES's overnight session.
        ZonedDateTime[] range = sessionRange(date);
        Timestamp startTs = Timestamp.from(range[0].toInstant());
        Timestamp endTs = Timestamp.from(range[1].toInstant());
        // §5.2: widened gamma band for lead, bounded.
        int gexLo = Math.max(100, lo - 100);
        int gexHi = Math.min(100_000, hi + 100);

        // Fail fast if the deadline already cancelled us before we grabbed a connection.
        if (Thread.currentThread().isInterrupted()) {
            throw new DbUnavailableException("pin-flow request cancelled");
        }

        Connection conn;
        try {
            conn = dataSource.getConnection();
        } catch (SQLException e) {
            throw new DbUnavailableException("pin-flow query failed (" + e.getSQLState() + ")", e);
        }
        // §6.1 deadline cancellation: while the worker thread runs, a watcher cancels the in-flight
        // statement and closes the connection the instant the thread is interrupted (deferred timeout /
        // client disconnect → future.cancel(true)) — so a timed-out request frees its pooled connection
        // promptly instead of holding it for the full statement timeout.
        java.util.concurrent.atomic.AtomicReference<PreparedStatement> active =
                new java.util.concurrent.atomic.AtomicReference<>();
        InterruptWatcher watcher = new InterruptWatcher(Thread.currentThread(), active, conn);
        watcher.start();
        try {
            try {
                conn.setReadOnly(true);
            } catch (SQLException ignored) {
                // read-only hint is best-effort; the DB GRANT is the real boundary (§6.4).
            }
            List<PinFlowAggregator.StrikeMinuteRow> strikeRows =
                    readStrikeRows(conn, startTs, endTs, lo, hi, active);
            List<PinFlowAggregator.GexMinuteRow> gexRows =
                    readGexRows(conn, startTs, endTs, gexLo, gexHi, active);
            return PinFlowAggregator.aggregate(strikeRows, gexRows, zone);
        } catch (SQLException e) {
            // A cancelled statement surfaces here as a SQLException — map it to the cancel signal.
            if (Thread.currentThread().isInterrupted() || watcher.fired()) {
                throw new DbUnavailableException("pin-flow request cancelled", e);
            }
            // No JDBC metadata / creds in the message (§5.2 no-leak) — just the SQLState class.
            throw new DbUnavailableException("pin-flow query failed (" + e.getSQLState() + ")", e);
        } finally {
            watcher.stop();
            try {
                conn.close();
            } catch (SQLException ignored) {
                // best-effort close; watcher may have already closed it on cancel.
            }
        }
    }

    /**
     * §6.1 deadline cancellation helper: watches the worker thread and, the instant it is interrupted,
     * calls {@link Statement#cancel()} on the active statement and closes the connection so the pooled
     * resource is freed immediately (not after the full statement timeout).
     */
    private static final class InterruptWatcher {
        private final Thread worker;
        private final java.util.concurrent.atomic.AtomicReference<PreparedStatement> active;
        private final Connection conn;
        private final Thread thread;
        private volatile boolean running = true;
        private volatile boolean fired = false;

        InterruptWatcher(Thread worker,
                         java.util.concurrent.atomic.AtomicReference<PreparedStatement> active,
                         Connection conn) {
            this.worker = worker;
            this.active = active;
            this.conn = conn;
            this.thread = new Thread(this::run, "pin-flow-cancel-watch");
            this.thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        boolean fired() {
            return fired;
        }

        private void run() {
            while (running) {
                if (worker.isInterrupted()) {
                    fired = true;
                    PreparedStatement ps = active.get();
                    if (ps != null) {
                        try {
                            ps.cancel();
                        } catch (SQLException ignored) {
                            // best-effort; connection close below still frees the resource.
                        }
                    }
                    try {
                        conn.close();
                    } catch (SQLException ignored) {
                        // best-effort.
                    }
                    return;
                }
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        void stop() {
            running = false;
            thread.interrupt();
        }
    }

    private List<PinFlowAggregator.StrikeMinuteRow> readStrikeRows(Connection conn, Timestamp start,
                                                                   Timestamp end, int lo, int hi,
                                                                   java.util.concurrent.atomic.AtomicReference<PreparedStatement> active)
            throws SQLException {
        List<PinFlowAggregator.StrikeMinuteRow> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(STRIKE_SQL)) {
            active.set(ps);
            ps.setQueryTimeout(queryTimeoutSeconds);
            ps.setTimestamp(1, start);
            ps.setTimestamp(2, end);
            ps.setInt(3, lo);
            ps.setInt(4, hi);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // §5.1 rule 8: only integer strikes on the 5-pt grid; fractional strikes ignored.
                    BigDecimal strikeDec = rs.getBigDecimal("strike");
                    Integer strike = integerStrike(strikeDec);
                    if (strike == null) {
                        continue;
                    }
                    rows.add(new PinFlowAggregator.StrikeMinuteRow(
                            rs.getTimestamp("m").getTime(),
                            strike,
                            rs.getBigDecimal("call_sell_notional"),
                            rs.getBigDecimal("put_sell_notional"),
                            rs.getBigDecimal("spot"),
                            rs.getString("trade_date")));
                }
            }
        } finally {
            active.set(null);
        }
        return rows;
    }

    private List<PinFlowAggregator.GexMinuteRow> readGexRows(Connection conn, Timestamp start,
                                                             Timestamp end, int lo, int hi,
                                                             java.util.concurrent.atomic.AtomicReference<PreparedStatement> active)
            throws SQLException {
        List<PinFlowAggregator.GexMinuteRow> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(GEX_SQL)) {
            active.set(ps);
            ps.setQueryTimeout(queryTimeoutSeconds);
            ps.setTimestamp(1, start);
            ps.setTimestamp(2, end);
            ps.setInt(3, lo);
            ps.setInt(4, hi);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer strike = integerStrike(rs.getBigDecimal("strike"));
                    if (strike == null) {
                        continue;
                    }
                    rows.add(new PinFlowAggregator.GexMinuteRow(
                            rs.getTimestamp("m").getTime(),
                            strike,
                            rs.getBigDecimal("net_gex")));
                }
            }
        } finally {
            active.set(null);
        }
        return rows;
    }

    /** §5.1 rule 8: keep only strikes that are whole integers on the 5-point grid; else null (ignored). */
    static Integer integerStrike(BigDecimal strike) {
        if (strike == null) {
            return null;
        }
        BigDecimal stripped = strike.stripTrailingZeros();
        if (stripped.scale() > 0) {
            return null; // fractional strike
        }
        int value;
        try {
            value = stripped.intValueExact();
        } catch (ArithmeticException overflow) {
            return null;
        }
        if (value % 5 != 0) {
            return null; // off the 5-point grid
        }
        return value;
    }
}
