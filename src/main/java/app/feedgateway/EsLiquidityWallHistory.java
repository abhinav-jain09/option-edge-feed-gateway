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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.avro.generic.GenericRecord;

/**
 * The 60-second session projection of {@code /api/es-liquidity-history} (ES Book Liquidity Walls Requirements rev 11 section 8 "The 60-second projection,
 * exactly"), folded in memory from the dashboard and event records the relay has seen. Minutes are aligned to the collector clock: minute m covers bucket ends
 * in (m*60000, (m+1)*60000]. Per level and side the minute carries maxSize (the maximum lastSize over its buckets), the last size, the maximum smoothed size,
 * the bucket count, and the minute's state = the highest-precedence state (section 5's order) any of its buckets showed, with INSUFFICIENT_DATA only when
 * every bucket showed it. The minute carries measuredBuckets (LIVE quality) and missingBuckets (the others), quality DEGRADED when any bucket was, else the
 * worst by LIVE &lt; GAP &lt; STALE, the last spot, the lean's last, minimum and maximum (thousandths), the last side read and confirmed side, the walls live at
 * its end (the last frame's) and the events within it. Absent fields are elided and read as "not measured", never as zero.
 * <p>
 * The print-matched quantities (Added, Decrease, Accounted, Unaccounted, Hidden) are NOT on the dashboard record (they are on
 * {@code futures.liquidity-wall.buckets}, which the gateway does not read), so the projection does not carry them: an elided field, by the document's rule.
 * <p>
 * Trade date: the ES session rolls at 18:00 ET, so a bucket end at or after 18:00 ET belongs to the next calendar date (no holiday arithmetic: a projection is
 * keyed by the date it is served under). A record of an older trade date is counted late and dropped; a newer one clears the projection. Bounded:
 * {@link #MAX_MINUTES} minutes (a Globex day is 1,380), {@link #MAX_LEVELS} levels and {@link #MAX_EVENTS} events per minute, the walls of the last frame only.
 */
final class EsLiquidityWallHistory {
    static final ZoneId ET = ZoneId.of("America/New_York");
    static final LocalTime SESSION_ROLL = LocalTime.of(18, 0);
    static final long MINUTE_MS = 60_000L;
    static final int MAX_MINUTES = 1_500;
    static final int MAX_LEVELS = EsLiquidityWallJson.MAX_LEVELS;
    static final int MAX_EVENTS = 256;
    /** Section 5's classification order, highest precedence first. */
    static final List<String> STATE_ORDER = List.of("INSUFFICIENT_DATA", "EATEN", "HIDDEN", "REFILLING", "PULLED", "STABLE_WALL", "LOW_LIQUIDITY", "NEUTRAL");
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

    static final class Level {
        final String side;
        final long tick;
        long maxSize;
        long lastSize;
        long maxSmoothedSize;
        long lastBucketEnd;
        int buckets;
        String state;
        boolean everOutsideInsufficient;

        Level(String side, long tick) {
            this.side = side;
            this.tick = tick;
        }
    }

    record WallEvent(String kind, long bucketEnd, String clusterId, String lane, Long loTick, Long hiTick, String retireReason, String direction) {
    }

    static final class Minute {
        final long minute;
        long firstBucketEnd = Long.MAX_VALUE;
        long lastBucketEnd;
        int buckets;
        int measuredBuckets;
        int qualityRank = -1;
        boolean degraded;
        String depthEnvironment;
        boolean layerBEnabled;
        Long spotTick;
        Integer leanLast;
        Integer leanMin;
        Integer leanMax;
        String sideRead;
        String confirmedSide;
        final Map<String, Level> levels = new LinkedHashMap<>();
        List<GenericRecord> walls = List.of();
        final List<WallEvent> events = new ArrayList<>();

        Minute(long minute) {
            this.minute = minute;
        }

        String quality() {
            return degraded ? "DEGRADED" : qualityRank == 0 ? "LIVE" : qualityRank == 1 ? "GAP" : qualityRank == 2 ? "STALE" : qualityRank < 0 ? null : "OTHER";
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
    private String seekOrigin = "NONE";
    private long coverageFromMs;
    private final TreeMap<Long, Minute> minutes = new TreeMap<>();

    /** Where the relay's dashboard read started: {@code SESSION_OPEN} with that instant, or {@code LATEST} with the start time. */
    synchronized void seekOrigin(String origin, long fromMs) {
        seekOrigin = origin;
        coverageFromMs = fromMs;
    }

    /** Folds a validated dashboard record ({@link EsLiquidityWallJson#dashboard} accepted it). */
    synchronized void foldDashboard(GenericRecord r) {
        try {
            long bucketEnd = (Long) r.get("bucketEnd");
            Minute m = minute(bucketEnd, r);
            if (m == null) {
                framesLate++;
                return;
            }
            boolean latest = bucketEnd >= m.lastBucketEnd;
            m.buckets++;
            m.firstBucketEnd = Math.min(m.firstBucketEnd, bucketEnd);
            String quality = EsLiquidityWallJson.text(r.get("quality"));
            if ("LIVE".equals(quality)) {
                m.measuredBuckets++;
            }
            if ("DEGRADED".equals(quality)) {
                m.degraded = true;
            } else {
                m.qualityRank = Math.max(m.qualityRank, "LIVE".equals(quality) ? 0 : "GAP".equals(quality) ? 1 : "STALE".equals(quality) ? 2 : 3);
            }
            Integer lean = (Integer) r.get("leanThousandths");
            if (lean != null) {
                m.leanMin = m.leanMin == null ? lean : Math.min(m.leanMin, lean);
                m.leanMax = m.leanMax == null ? lean : Math.max(m.leanMax, lean);
            }
            if (latest) {
                m.lastBucketEnd = bucketEnd;
                m.depthEnvironment = EsLiquidityWallJson.text(r.get("depthEnvironment"));
                m.layerBEnabled = Boolean.TRUE.equals(r.get("layerBEnabled"));
                m.spotTick = (Long) r.get("spotTick");
                m.leanLast = lean;
                m.sideRead = r.get("sideRead") == null ? null : r.get("sideRead").toString();
                m.confirmedSide = r.get("confirmedSide") == null ? null : r.get("confirmedSide").toString();
                List<GenericRecord> walls = new ArrayList<>();
                for (Object w : (List<?>) r.get("walls")) {
                    walls.add((GenericRecord) w);
                }
                m.walls = walls;
                lastBucketEnd = Math.max(lastBucketEnd, bucketEnd);
                depthEnvironment = m.depthEnvironment;
                contract = EsLiquidityWallJson.text(r.get("contract"));
            }
            for (Object o : (List<?>) r.get("levels")) {
                GenericRecord lr = (GenericRecord) o;
                String side = EsLiquidityWallJson.text(lr.get("side"));
                long tick = (Long) lr.get("tick");
                String key = side + "|" + tick;
                Level level = m.levels.get(key);
                if (level == null) {
                    if (m.levels.size() >= MAX_LEVELS) {
                        continue;
                    }
                    level = new Level(side, tick);
                    m.levels.put(key, level);
                }
                long lastSize = (Long) lr.get("lastSize");
                level.buckets++;
                level.maxSize = Math.max(level.maxSize, lastSize);
                level.maxSmoothedSize = Math.max(level.maxSmoothedSize, (Long) lr.get("smoothedSize"));
                if (bucketEnd >= level.lastBucketEnd) {
                    level.lastBucketEnd = bucketEnd;
                    level.lastSize = lastSize;
                }
                level.state = precedent(level, EsLiquidityWallJson.text(lr.get("state")));
            }
            framesFolded++;
        } catch (RuntimeException invalid) {
            framesRefused++;
        }
    }

    /** Folds a validated event record into the minute of its bucket end. */
    synchronized void foldEvent(GenericRecord r) {
        try {
            long bucketEnd = (Long) r.get("bucketEnd");
            Minute m = minute(bucketEnd, r);
            if (m == null) {
                eventsLate++;
                return;
            }
            if (m.events.size() >= MAX_EVENTS) {
                return;
            }
            String kind = EsLiquidityWallJson.text(r.get("kind"));
            GenericRecord wall = r.get("wall") instanceof GenericRecord w ? w : null;
            GenericRecord flip = r.get("flip") instanceof GenericRecord f ? f : null;
            m.events.add(new WallEvent(kind, bucketEnd,
                    wall == null ? null : EsLiquidityWallJson.text(wall.get("clusterId")),
                    wall == null ? null : EsLiquidityWallJson.text(wall.get("lane")),
                    wall == null ? null : (Long) wall.get("loTick"),
                    wall == null ? null : (Long) wall.get("hiTick"),
                    r.get("retireReason") == null ? null : r.get("retireReason").toString(),
                    flip == null ? null : EsLiquidityWallJson.text(flip.get("direction"))));
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

    /** The minute's state after one more bucket: the highest-precedence non-INSUFFICIENT state seen, INSUFFICIENT_DATA only while every bucket showed it. */
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
            g.writeStringField("coverage", seekOrigin);
            g.writeNumberField("coverageFromMs", coverageFromMs);
            if (lastBucketEnd > 0) {
                g.writeNumberField("lastBucketEnd", lastBucketEnd);
            }
            g.writeNumberField("framesFolded", framesFolded);
            g.writeNumberField("framesLate", framesLate);
            g.writeNumberField("framesRefused", framesRefused);
            g.writeNumberField("eventsFolded", eventsFolded);
            g.writeNumberField("eventsLate", eventsLate);
            g.writeArrayFieldStart("minutes");
            for (Minute m : minutes.values()) {
                g.writeStartObject();
                g.writeNumberField("minute", m.minute);
                g.writeNumberField("startMs", m.minute * MINUTE_MS);
                g.writeNumberField("endMs", (m.minute + 1) * MINUTE_MS);
                g.writeNumberField("buckets", m.buckets);
                g.writeNumberField("measuredBuckets", m.measuredBuckets);
                g.writeNumberField("missingBuckets", m.buckets - m.measuredBuckets);
                if (m.buckets > 0) {
                    g.writeNumberField("firstBucketEnd", m.firstBucketEnd);
                    g.writeNumberField("lastBucketEnd", m.lastBucketEnd);
                    g.writeStringField("quality", m.quality());
                    g.writeStringField("depthEnvironment", m.depthEnvironment);
                    g.writeBooleanField("layerBEnabled", m.layerBEnabled);
                }
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
                g.writeArrayFieldStart("levels");
                for (Level level : m.levels.values()) {
                    g.writeStartObject();
                    g.writeStringField("side", level.side);
                    g.writeNumberField("tick", level.tick);
                    g.writeNumberField("maxSize", level.maxSize);
                    g.writeNumberField("lastSize", level.lastSize);
                    g.writeNumberField("maxSmoothedSize", level.maxSmoothedSize);
                    g.writeStringField("state", level.state);
                    g.writeNumberField("buckets", level.buckets);
                    g.writeEndObject();
                }
                g.writeEndArray();
                g.writeArrayFieldStart("walls");
                for (GenericRecord w : m.walls) {
                    g.writeStartObject();
                    g.writeStringField("clusterId", EsLiquidityWallJson.text(w.get("clusterId")));
                    g.writeStringField("lane", EsLiquidityWallJson.text(w.get("lane")));
                    g.writeNumberField("loTick", (Long) w.get("loTick"));
                    g.writeNumberField("hiTick", (Long) w.get("hiTick"));
                    g.writeNumberField("birthBucketEnd", (Long) w.get("birthBucketEnd"));
                    g.writeNumberField("heldBuckets", (Integer) w.get("heldBuckets"));
                    g.writeNumberField("scorePublished", (Integer) w.get("scorePublished"));
                    g.writeBooleanField("scoreSettled", Boolean.TRUE.equals(w.get("scoreSettled")));
                    g.writeStringField("grade", EsLiquidityWallJson.text(w.get("grade")));
                    g.writeBooleanField("defendedState", Boolean.TRUE.equals(w.get("defendedState")));
                    g.writeBooleanField("brokenState", Boolean.TRUE.equals(w.get("brokenState")));
                    g.writeEndObject();
                }
                g.writeEndArray();
                g.writeArrayFieldStart("events");
                for (WallEvent e : m.events) {
                    g.writeStartObject();
                    g.writeStringField("kind", e.kind());
                    g.writeNumberField("bucketEnd", e.bucketEnd());
                    if (e.clusterId() != null) {
                        g.writeStringField("clusterId", e.clusterId());
                        g.writeStringField("lane", e.lane());
                        g.writeNumberField("loTick", e.loTick());
                        g.writeNumberField("hiTick", e.hiTick());
                    }
                    if (e.retireReason() != null) {
                        g.writeStringField("retireReason", e.retireReason());
                    }
                    if (e.direction() != null) {
                        g.writeStringField("direction", e.direction());
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
}
