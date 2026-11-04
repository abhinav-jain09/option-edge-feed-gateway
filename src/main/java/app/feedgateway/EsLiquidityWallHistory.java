package app.feedgateway;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.avro.generic.GenericRecord;

/**
 * The 60-second session projection of {@code /api/es-liquidity-history} (ES Book Liquidity Walls Requirements rev 11 section 8 "The 60-second projection,
 * exactly"), folded in memory from three sources with three different reaches, each reported in the body:
 * <ul>
 * <li>{@code futures.liquidity-wall.buckets} (id 3402, delete retention 1 day, one row per bucket x side x level): THE retained source, read from the session
 * open. Per level and side the minute carries maxSize (the maximum lastSize over the minute's rows), the last size, the maximum smoothed size, the row count,
 * the state = the highest-precedence state (section 5's order) any row showed, with INSUFFICIENT_DATA only when every row showed it, and the
 * classification-window quantities (Added, Decrease, Traded, Accounted, Unaccounted, Hidden, pull and refill counts) of the minute's LAST LIVE row, each
 * null when that window has it null, never summed across windows ({@code windowBucketEnd} says which bucket they are as of). The minute's bucket count is
 * the number of distinct bucket ends its rows showed, measuredBuckets those of LIVE quality and missingBuckets the others; the minute's quality is the
 * worst its buckets showed by the order LIVE &lt; GAP &lt; STALE &lt; RESTART &lt; DEGRADED (document D section 4's five qualities: DEGRADED when any bucket
 * was, else RESTART when any was, else the worst of the three), a literal outside the five ranking between STALE and RESTART as {@code OTHER}. A RESTART
 * row folds its sizes and state like a GAP or STALE row (section 5 classifies it INSUFFICIENT_DATA) and its window is never taken.</li>
 * <li>{@code futures.liquidity-wall.events} (id 3404, delete retention under a byte cap): read from the session open; the BORN and RETIRED events are listed
 * in their minute (section 8 lists those two kinds only) and, applied in bucket order, give the walls alive at every minute's end (identity, lane, ticks,
 * birth bucket). A wall whose BORN fell outside the retained bytes is unknown until a dashboard frame lists it.</li>
 * <li>{@code futures.liquidity-wall.dashboard} (id 3403): COMPACTED under the single key {@code CME|<contract>}, so after compaction or a gateway restart it
 * holds only the surviving latest frame and can never reconstruct a session; the relay reads it from the latest record and this projection's
 * dashboard-derived part covers ONLY the relay's uptime ({@code dashboardCoverage} = SINCE_START with the start instant). It contributes the fields no
 * retained source carries: the lean (last, minimum, maximum in thousandths), side read, confirmed side, spot and layer-B flag of a minute, and the score
 * terms and grade of the walls a frame of that minute listed. For a minute before the relay started those fields are elided: not measured, never guessed.</li>
 * </ul>
 * Minutes are aligned to the collector clock: minute m covers bucket ends in (m*60000, (m+1)*60000]. Absent fields are elided and read as "not measured",
 * never as zero. Trade date: the ES session rolls at 18:00 ET, so a bucket end at or after 18:00 ET belongs to the next calendar date (no holiday
 * arithmetic). A record of an older trade date is counted late and dropped; a newer one clears the projection. Bounded: {@link #MAX_MINUTES} minutes (a
 * Globex day is 1,380), {@link #MAX_LEVELS} levels and {@link #MAX_EVENTS} events per minute, the walls of a minute's last dashboard frame only.
 */
final class EsLiquidityWallHistory {
    static final ZoneId ET = ZoneId.of("America/New_York");
    static final LocalTime SESSION_ROLL = LocalTime.of(18, 0);
    static final long MINUTE_MS = 60_000L;
    static final int MAX_MINUTES = 1_500;
    static final int MAX_LEVELS = EsLiquidityWallJson.MAX_LEVELS;
    static final int MAX_EVENTS = 256;
    /** One-second buckets: a minute holds 60; the bound tolerates a producer's bucket size change without unbounded growth. */
    static final int MAX_BUCKETS_PER_MINUTE = 600;
    /** Section 5's classification order, highest precedence first. */
    static final List<String> STATE_ORDER = List.of("INSUFFICIENT_DATA", "EATEN", "HIDDEN", "REFILLING", "PULLED", "STABLE_WALL", "LOW_LIQUIDITY", "NEUTRAL");
    /** Document D section 4's bucket qualities from best to worst, with {@code OTHER} for a literal outside them; the minute's quality is its worst bucket's. */
    static final List<String> QUALITY_ORDER = List.of("LIVE", "GAP", "STALE", "OTHER", "RESTART", "DEGRADED");

    static int qualityRank(String quality) {
        int i = QUALITY_ORDER.indexOf(quality);
        return i < 0 ? QUALITY_ORDER.indexOf("OTHER") : i;
    }

    /** The event kinds the projection lists (section 8). */
    static final List<String> PROJECTED_EVENT_KINDS = List.of("BORN", "RETIRED");
    static final String SINCE_START = "SINCE_START";
    private static final JsonFactory FACTORY = new JsonFactory();

    /** The trade date of a bucket end: the ET date, the next one at or after 18:00 ET. */
    static LocalDate tradeDate(long epochMs) {
        ZonedDateTime et = Instant.ofEpochMilli(epochMs).atZone(ET);
        return et.toLocalTime().isBefore(SESSION_ROLL) ? et.toLocalDate() : et.toLocalDate().plusDays(1);
    }

    /** The first instant of a trade date's session: 18:00 ET of the previous calendar day. */
    static long sessionStartMs(LocalDate tradeDate) {
        return tradeDate.minusDays(1).atTime(SESSION_ROLL).atZone(ET).toInstant().toEpochMilli();
    }

    /** The minute index of a bucket end: m with bucketEnd in (m*60000, (m+1)*60000]. */
    static long minuteOf(long bucketEnd) {
        return (bucketEnd - 1) / MINUTE_MS;
    }

    /** One level of one minute, from its bucket rows. */
    static final class Level {
        final String side;
        final long tick;
        int rows;
        long maxSize;
        long lastSize;
        long maxSmoothedSize;
        long lastBucketEnd;
        String state;
        boolean everOutsideInsufficient;
        /** The bucket end of the LIVE row the window quantities below are as of; 0 while none. */
        long windowBucketEnd;
        Long added, decrease, traded, accounted, unaccounted, hidden;
        Integer pullCount, refillCount;

        Level(String side, long tick) {
            this.side = side;
            this.tick = tick;
        }
    }

    record WallEvent(String kind, long bucketEnd, String clusterId, String lane, long loTick, long hiTick, String retireReason) {
    }

    static final class Minute {
        final long minute;
        /** Distinct bucket ends the rows showed, each with its quality. */
        final Map<Long, String> bucketQualities = new HashMap<>();
        String depthEnvironment;
        long depthEnvironmentAt;
        // ---- the dashboard-derived part: only for minutes the relay was up ----
        int dashboardFrames;
        long dashboardLastBucketEnd;
        boolean layerBEnabled;
        Long spotTick;
        Integer leanLast;
        Integer leanMin;
        Integer leanMax;
        String sideRead;
        String confirmedSide;
        /** The last dashboard frame's walls by clusterId: the score terms of the walls alive at the minute's end. */
        Map<String, GenericRecord> scoredWalls = Map.of();
        final Map<String, Level> levels = new LinkedHashMap<>();
        final List<WallEvent> events = new ArrayList<>();

        Minute(long minute) {
            this.minute = minute;
        }

        int measured() {
            int n = 0;
            for (String q : bucketQualities.values()) {
                if ("LIVE".equals(q)) {
                    n++;
                }
            }
            return n;
        }

        /** The worst quality of the minute's buckets by {@link #QUALITY_ORDER}; null while it has none. */
        String quality() {
            int rank = -1;
            for (String q : bucketQualities.values()) {
                rank = Math.max(rank, qualityRank(q));
            }
            return rank < 0 ? null : QUALITY_ORDER.get(rank);
        }

        long firstBucketEnd() {
            long first = Long.MAX_VALUE;
            for (long b : bucketQualities.keySet()) {
                first = Math.min(first, b);
            }
            return first;
        }

        long lastBucketEnd() {
            long last = 0;
            for (long b : bucketQualities.keySet()) {
                last = Math.max(last, b);
            }
            return last;
        }
    }

    private LocalDate tradeDate;
    private String contract;
    private String depthEnvironment;
    private long lastBucketEnd;
    private long framesFolded;
    private long framesLate;
    private long framesRefused;
    private long eventsFolded;
    private long eventsLate;
    private long eventsNotProjected;
    private long bucketRowsFolded;
    private long bucketRowsLate;
    private long bucketRowsNotLive;
    private long dashboardCoverageFromMs;
    private String bucketsSeekOrigin = "NONE";
    private long bucketsCoverageFromMs;
    private String eventsSeekOrigin = "NONE";
    private long eventsCoverageFromMs;
    private final TreeMap<Long, Minute> minutes = new TreeMap<>();

    /** The instant the relay began reading the dashboard topic from its latest record: the start of the dashboard-derived coverage. */
    synchronized void dashboardCoverageFrom(long startMs) {
        dashboardCoverageFromMs = startMs;
    }

    /** Where the relay's buckets read started: {@code SESSION_OPEN} with that instant, or {@code LATEST} with the start time. */
    synchronized void bucketsSeekOrigin(String origin, long fromMs) {
        bucketsSeekOrigin = origin;
        bucketsCoverageFromMs = fromMs;
    }

    /** Where the relay's events read started, as {@link #bucketsSeekOrigin}. */
    synchronized void eventsSeekOrigin(String origin, long fromMs) {
        eventsSeekOrigin = origin;
        eventsCoverageFromMs = fromMs;
    }

    /**
     * Folds a validated bucket row ({@link EsLiquidityWallJson#bucket} accepted it) of any quality into its minute's bucket set and its level's sizes and
     * state; a LIVE row's classification-window quantities become the level's when it is the latest LIVE row of the minute seen so far (a non-LIVE row's
     * window is null by section 5 and is counted, not taken).
     */
    synchronized void foldBucket(GenericRecord r) {
        try {
            long bucketEnd = (Long) r.get("bucketEnd");
            Minute m = minute(bucketEnd, r);
            if (m == null) {
                bucketRowsLate++;
                return;
            }
            String quality = EsLiquidityWallJson.text(r.get("quality"));
            if (m.bucketQualities.size() < MAX_BUCKETS_PER_MINUTE || m.bucketQualities.containsKey(bucketEnd)) {
                m.bucketQualities.put(bucketEnd, quality);
            }
            identity(m, r, bucketEnd);
            Level level = level(m, EsLiquidityWallJson.text(r.get("side")), (Long) r.get("tick"));
            if (level == null) {
                return;   // the minute's level bound
            }
            bucketRowsFolded++;
            long lastSize = (Long) r.get("lastSize");
            level.rows++;
            level.maxSize = Math.max(level.maxSize, lastSize);
            level.maxSmoothedSize = Math.max(level.maxSmoothedSize, (Long) r.get("smoothedSize"));
            if (bucketEnd >= level.lastBucketEnd) {
                level.lastBucketEnd = bucketEnd;
                level.lastSize = lastSize;
            }
            level.state = precedent(level, EsLiquidityWallJson.text(r.get("state")));
            if (!"LIVE".equals(quality)) {
                bucketRowsNotLive++;
                return;
            }
            if (bucketEnd < level.windowBucketEnd) {
                return;   // an earlier LIVE bucket arriving after a later one: the minute's values stay those of its last LIVE bucket
            }
            level.windowBucketEnd = bucketEnd;
            level.added = (Long) r.get("added");
            level.decrease = (Long) r.get("decrease");
            level.traded = (Long) r.get("traded");
            level.accounted = (Long) r.get("accounted");
            level.unaccounted = (Long) r.get("unaccounted");
            level.hidden = (Long) r.get("hidden");
            level.pullCount = (Integer) r.get("pullCount");
            level.refillCount = (Integer) r.get("refillCount");
        } catch (RuntimeException invalid) {
            framesRefused++;
        }
    }

    /** The minute's level entry, created when absent while the bound allows; null when the minute is full. */
    private static Level level(Minute m, String side, long tick) {
        String key = side + "|" + tick;
        Level level = m.levels.get(key);
        if (level == null && m.levels.size() < MAX_LEVELS) {
            level = new Level(side, tick);
            m.levels.put(key, level);
        }
        return level;
    }

    /** The session identity any record carries: the contract and depth environment of the latest bucket end seen, per minute and overall. */
    private void identity(Minute m, GenericRecord r, long bucketEnd) {
        if (bucketEnd >= m.depthEnvironmentAt) {
            m.depthEnvironmentAt = bucketEnd;
            m.depthEnvironment = EsLiquidityWallJson.text(r.get("depthEnvironment"));
        }
        if (bucketEnd >= lastBucketEnd) {
            lastBucketEnd = bucketEnd;
            depthEnvironment = EsLiquidityWallJson.text(r.get("depthEnvironment"));
            contract = EsLiquidityWallJson.text(r.get("contract"));
        }
    }

    /** Folds a validated dashboard record ({@link EsLiquidityWallJson#dashboard} accepted it): the dashboard-only fields of its minute and the walls' scores. */
    synchronized void foldDashboard(GenericRecord r) {
        try {
            long bucketEnd = (Long) r.get("bucketEnd");
            Minute m = minute(bucketEnd, r);
            if (m == null) {
                framesLate++;
                return;
            }
            boolean latest = bucketEnd >= m.dashboardLastBucketEnd;
            m.dashboardFrames++;
            identity(m, r, bucketEnd);
            Integer lean = (Integer) r.get("leanThousandths");
            if (lean != null) {
                m.leanMin = m.leanMin == null ? lean : Math.min(m.leanMin, lean);
                m.leanMax = m.leanMax == null ? lean : Math.max(m.leanMax, lean);
            }
            if (latest) {
                m.dashboardLastBucketEnd = bucketEnd;
                m.layerBEnabled = Boolean.TRUE.equals(r.get("layerBEnabled"));
                m.spotTick = (Long) r.get("spotTick");
                m.leanLast = lean;
                m.sideRead = r.get("sideRead") == null ? null : r.get("sideRead").toString();
                m.confirmedSide = r.get("confirmedSide") == null ? null : r.get("confirmedSide").toString();
                Map<String, GenericRecord> walls = new LinkedHashMap<>();
                for (Object w : (List<?>) r.get("walls")) {
                    GenericRecord wall = (GenericRecord) w;
                    walls.put(EsLiquidityWallJson.text(wall.get("clusterId")), wall);
                }
                m.scoredWalls = walls;
            }
            framesFolded++;
        } catch (RuntimeException invalid) {
            framesRefused++;
        }
    }

    /** Folds a validated BORN or RETIRED event into the minute of its bucket end; any other kind is counted and not projected. */
    synchronized void foldEvent(GenericRecord r) {
        try {
            String kind = EsLiquidityWallJson.text(r.get("kind"));
            if (!PROJECTED_EVENT_KINDS.contains(kind)) {
                eventsNotProjected++;
                return;
            }
            long bucketEnd = (Long) r.get("bucketEnd");
            Minute m = minute(bucketEnd, r);
            if (m == null) {
                eventsLate++;
                return;
            }
            identity(m, r, bucketEnd);
            if (m.events.size() >= MAX_EVENTS) {
                return;
            }
            GenericRecord wall = (GenericRecord) r.get("wall");   // present on every wall event ({@link EsLiquidityWallJson#event} refused it otherwise)
            m.events.add(new WallEvent(kind, bucketEnd, EsLiquidityWallJson.text(wall.get("clusterId")), EsLiquidityWallJson.text(wall.get("lane")),
                    (Long) wall.get("loTick"), (Long) wall.get("hiTick"), r.get("retireReason") == null ? null : r.get("retireReason").toString()));
            eventsFolded++;
        } catch (RuntimeException invalid) {
            framesRefused++;
        }
    }

    /** The minute of a bucket end under the current trade date; null when the record is of an older trade date. A newer trade date clears the projection. */
    private Minute minute(long bucketEnd, GenericRecord r) {
        LocalDate date = tradeDate(bucketEnd);
        if (tradeDate == null || date.isAfter(tradeDate)) {
            tradeDate = date;
            minutes.clear();
            lastBucketEnd = 0;
            // every session identity goes with the minutes: a bucket row or an event may be the new session's first record (the topics are polled
            // independently), and the body must not report the new trade date with the previous session's contract or depth environment
            contract = null;
            depthEnvironment = null;
        } else if (date.isBefore(tradeDate)) {
            return null;
        }
        long index = minuteOf(bucketEnd);
        Minute m = minutes.get(index);
        if (m == null) {
            if (minutes.size() >= MAX_MINUTES) {
                if (index < minutes.firstKey()) {
                    return null;   // older than everything a full projection holds
                }
                minutes.pollFirstEntry();
            }
            m = new Minute(index);
            minutes.put(index, m);
        }
        return m;
    }

    /** The level's state after one more row: the highest-precedence non-INSUFFICIENT state seen, INSUFFICIENT_DATA only while every row showed it. */
    private static String precedent(Level level, String state) {
        boolean insufficient = "INSUFFICIENT_DATA".equals(state);
        if (insufficient) {
            return level.everOutsideInsufficient ? level.state : "INSUFFICIENT_DATA";
        }
        if (!level.everOutsideInsufficient) {
            level.everOutsideInsufficient = true;
            return state;
        }
        return rank(state) < rank(level.state) ? state : level.state;
    }

    private static int rank(String state) {
        int i = STATE_ORDER.indexOf(state);
        return i < 0 ? STATE_ORDER.size() : i;
    }

    synchronized int minuteCount() {
        return minutes.size();
    }

    synchronized long framesFolded() {
        return framesFolded;
    }

    synchronized LocalDate tradeDate() {
        return tradeDate;
    }

    /** A wall alive at a minute's end, from the events ledger. */
    private record LiveWall(String clusterId, String lane, long loTick, long hiTick, long birthBucketEnd) {
    }

    /** The projection as JSON (section 8 "Gateway and page"); elided fields are not measured. */
    synchronized String render(long nowMs) {
        StringWriter out = new StringWriter(8192);
        try (JsonGenerator g = FACTORY.createGenerator(out)) {
            g.writeStartObject();
            g.writeStringField("tradeDate", tradeDate == null ? null : tradeDate.toString());
            g.writeStringField("contract", contract);
            g.writeStringField("depthEnvironment", depthEnvironment);
            g.writeNumberField("minuteMs", MINUTE_MS);
            g.writeNumberField("servedAtMs", nowMs);
            // per-source coverage: the dashboard is compacted and covers the relay's uptime only; buckets and events are the retained sources
            g.writeStringField("dashboardCoverage", SINCE_START);
            g.writeNumberField("dashboardCoverageFromMs", dashboardCoverageFromMs);
            g.writeStringField("bucketsCoverage", bucketsSeekOrigin);
            g.writeNumberField("bucketsCoverageFromMs", bucketsCoverageFromMs);
            g.writeStringField("eventsCoverage", eventsSeekOrigin);
            g.writeNumberField("eventsCoverageFromMs", eventsCoverageFromMs);
            if (lastBucketEnd > 0) {
                g.writeNumberField("lastBucketEnd", lastBucketEnd);
            }
            g.writeNumberField("framesFolded", framesFolded);
            g.writeNumberField("framesLate", framesLate);
            g.writeNumberField("framesRefused", framesRefused);
            g.writeNumberField("eventsFolded", eventsFolded);
            g.writeNumberField("eventsLate", eventsLate);
            g.writeNumberField("eventsNotProjected", eventsNotProjected);
            g.writeNumberField("bucketRowsFolded", bucketRowsFolded);
            g.writeNumberField("bucketRowsLate", bucketRowsLate);
            g.writeNumberField("bucketRowsNotLive", bucketRowsNotLive);
            g.writeArrayFieldStart("minutes");
            Map<String, LiveWall> ledger = new LinkedHashMap<>();   // the walls alive after each minute's BORN and RETIRED, in bucket order
            for (Minute m : minutes.values()) {
                List<WallEvent> events = new ArrayList<>(m.events);
                events.sort((a, b) -> Long.compare(a.bucketEnd(), b.bucketEnd()));
                for (WallEvent e : events) {
                    if ("BORN".equals(e.kind())) {
                        ledger.put(e.clusterId(), new LiveWall(e.clusterId(), e.lane(), e.loTick(), e.hiTick(), e.bucketEnd()));
                    } else {
                        ledger.remove(e.clusterId());
                    }
                }
                g.writeStartObject();
                g.writeNumberField("minute", m.minute);
                g.writeNumberField("startMs", m.minute * MINUTE_MS);
                g.writeNumberField("endMs", (m.minute + 1) * MINUTE_MS);
                int buckets = m.bucketQualities.size(), measured = m.measured();
                g.writeNumberField("buckets", buckets);
                g.writeNumberField("measuredBuckets", measured);
                g.writeNumberField("missingBuckets", buckets - measured);
                if (buckets > 0) {
                    g.writeNumberField("firstBucketEnd", m.firstBucketEnd());
                    g.writeNumberField("lastBucketEnd", m.lastBucketEnd());
                    g.writeStringField("quality", m.quality());
                }
                if (m.depthEnvironment != null) {
                    g.writeStringField("depthEnvironment", m.depthEnvironment);
                }
                g.writeNumberField("dashboardFrames", m.dashboardFrames);
                if (m.dashboardFrames > 0) {   // the dashboard-only fields: present only for a minute the relay was up
                    g.writeNumberField("dashboardLastBucketEnd", m.dashboardLastBucketEnd);
                    g.writeBooleanField("layerBEnabled", m.layerBEnabled);
                    if (m.spotTick != null) {
                        g.writeNumberField("spotTick", m.spotTick);
                    }
                    if (m.leanMin != null) {
                        g.writeObjectFieldStart("leanThousandths");
                        if (m.leanLast != null) {
                            g.writeNumberField("last", m.leanLast);
                        }
                        g.writeNumberField("min", m.leanMin);
                        g.writeNumberField("max", m.leanMax);
                        g.writeEndObject();
                    }
                    if (m.sideRead != null) {
                        g.writeStringField("sideRead", m.sideRead);
                    }
                    if (m.confirmedSide != null) {
                        g.writeStringField("confirmedSide", m.confirmedSide);
                    }
                }
                g.writeArrayFieldStart("levels");
                for (Level level : m.levels.values()) {
                    g.writeStartObject();
                    g.writeStringField("side", level.side);
                    g.writeNumberField("tick", level.tick);
                    g.writeNumberField("buckets", level.rows);
                    g.writeNumberField("maxSize", level.maxSize);
                    g.writeNumberField("lastSize", level.lastSize);
                    g.writeNumberField("maxSmoothedSize", level.maxSmoothedSize);
                    g.writeStringField("state", level.state);
                    if (level.windowBucketEnd > 0) {   // the classification window ending at the minute's last LIVE bucket; a null quantity is elided
                        g.writeNumberField("windowBucketEnd", level.windowBucketEnd);
                        number(g, "added", level.added);
                        number(g, "decrease", level.decrease);
                        number(g, "traded", level.traded);
                        number(g, "accounted", level.accounted);
                        number(g, "unaccounted", level.unaccounted);
                        number(g, "hidden", level.hidden);
                        number(g, "pullCount", level.pullCount);
                        number(g, "refillCount", level.refillCount);
                    }
                    g.writeEndObject();
                }
                g.writeEndArray();
                // the walls alive at the minute's end: the events ledger, each scored when the minute's last dashboard frame listed it; a wall that frame
                // listed but the ledger does not know (its BORN outside the retained bytes) follows, from the frame alone
                g.writeArrayFieldStart("walls");
                for (LiveWall w : ledger.values()) {
                    GenericRecord scored = m.scoredWalls.get(w.clusterId());
                    wall(g, w.clusterId(), w.lane(), w.loTick(), w.hiTick(), w.birthBucketEnd(), scored);
                }
                for (Map.Entry<String, GenericRecord> e : m.scoredWalls.entrySet()) {
                    if (!ledger.containsKey(e.getKey())) {
                        GenericRecord w = e.getValue();
                        wall(g, e.getKey(), EsLiquidityWallJson.text(w.get("lane")), (Long) w.get("loTick"), (Long) w.get("hiTick"), (Long) w.get("birthBucketEnd"), w);
                    }
                }
                g.writeEndArray();
                g.writeArrayFieldStart("events");
                for (WallEvent e : m.events) {
                    g.writeStartObject();
                    g.writeStringField("kind", e.kind());
                    g.writeNumberField("bucketEnd", e.bucketEnd());
                    g.writeStringField("clusterId", e.clusterId());
                    g.writeStringField("lane", e.lane());
                    g.writeNumberField("loTick", e.loTick());
                    g.writeNumberField("hiTick", e.hiTick());
                    if (e.retireReason() != null) {
                        g.writeStringField("retireReason", e.retireReason());
                    }
                    g.writeEndObject();
                }
                g.writeEndArray();
                g.writeEndObject();
            }
            g.writeEndArray();
            g.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    /** One wall entry: identity from the ledger (or the frame), the score terms only from a dashboard frame of the minute, else elided. */
    private static void wall(JsonGenerator g, String clusterId, String lane, long loTick, long hiTick, long birthBucketEnd, GenericRecord scored) throws IOException {
        g.writeStartObject();
        g.writeStringField("clusterId", clusterId);
        g.writeStringField("lane", lane);
        g.writeNumberField("loTick", loTick);
        g.writeNumberField("hiTick", hiTick);
        g.writeNumberField("birthBucketEnd", birthBucketEnd);
        if (scored != null) {
            g.writeNumberField("heldBuckets", (Integer) scored.get("heldBuckets"));
            g.writeNumberField("scorePublished", (Integer) scored.get("scorePublished"));
            g.writeBooleanField("scoreSettled", Boolean.TRUE.equals(scored.get("scoreSettled")));
            g.writeStringField("grade", EsLiquidityWallJson.text(scored.get("grade")));
            g.writeBooleanField("defendedState", Boolean.TRUE.equals(scored.get("defendedState")));
            g.writeBooleanField("brokenState", Boolean.TRUE.equals(scored.get("brokenState")));
        }
        g.writeEndObject();
    }

    private static void number(JsonGenerator g, String name, Number value) throws IOException {
        if (value instanceof Long l) {
            g.writeNumberField(name, l);
        } else if (value instanceof Integer i) {
            g.writeNumberField(name, i);
        }
    }
}
