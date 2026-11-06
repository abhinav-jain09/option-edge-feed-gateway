package app.feedgateway;

import com.optionsedge.contracts.liquiditywall.LiquidityWallSchemas;
import java.util.List;
import java.util.Set;
import org.apache.avro.generic.GenericRecord;

/**
 * The browser view of the three ES liquidity-wall records the gateway relays (ES Book Liquidity Walls Requirements rev 11 section 8 "Gateway and page";
 * es-liquidity-wall-service): {@code LiquidityWallDashboard} (event {@code es-liquidity-heatmap}), {@code LiquidityWallEvent} ({@code es-liquidity-wall}) and
 * {@code LiquidityWallVoice} ({@code es-liquidity-voice}), and the boundary of the {@code LiquidityWallBucket} record the projection folds. The view is the record's own fields in schema order ({@link AvroFrameJson}: int and long as JSON
 * integers, a null union as {@code null}, no float, no enum), so {@code depthEnvironment}, {@code bucketEnd}, {@code quality}, {@code closePath} and
 * {@code configFingerprint} reach the page exactly as written and a later FULL-compatible field needs no code change here. Nothing is added or repaired; the
 * only gateway-owned fields are appended at delivery: {@code cached} on every frame ({@link #withCached}: a connect replay, or a live forward) and the voice
 * record's {@code stale} ({@link #withStale}). The schema ID of the frame is checked by the relay BEFORE decoding ({@link EsLiquidityWallRelay#accept}).
 * <p>
 * Boundary checks, each a counted drop in the relay: the WRITER schema is the subject's record (a record of another family under the same topic is refused by
 * name, not by key), {@code schemaVersion} 1 (the dashboard: 1 or 2, ids 3403 and 3408), the key is the one section 8 states ({@code CME|<contract>} for the dashboard and the voice,
 * {@code CME|<contract>|FLIP} for a flip event, the wall's {@code clusterId} for a wall event), a positive {@code bucketEnd}, a non-blank
 * {@code depthEnvironment} and {@code contract}, a dashboard of at most {@link #MAX_LEVELS} levels, a voice record with a non-blank {@code text} and
 * {@code voiceId}, and a rendering of at most {@link TaRelay#FRAME_MAX_BYTES} bytes.
 */
final class EsLiquidityWallJson {
    /** A band of a few hundred ticks per side; a dashboard listing more levels than this is refused. */
    static final int MAX_LEVELS = 4096;
    static final String FLIP = "FLIP";
    /** The dashboard has two versions (ids 3403 and 3408, contracts 0.84.0): a version 1 record is still on the compacted topic until the wall service writes its next one. */
    static final Set<Integer> DASHBOARD_VERSIONS = Set.of(1, 2);
    /** The event, voice and bucket records have one version. */
    static final Set<Integer> ONLY_VERSION_1 = Set.of(1);

    private EsLiquidityWallJson() {
    }

    /** The dashboard view, or null when the record is refused. */
    static String dashboard(String key, Object value) {
        GenericRecord r = record(LiquidityWallSchemas.DASHBOARD, value, DASHBOARD_VERSIONS);
        if (r == null || !dashboardKey(r).equals(key)) {
            return null;
        }
        Object levels = r.get("levels");
        if (!(levels instanceof List<?> rows) || rows.size() > MAX_LEVELS) {
            return null;
        }
        return render(r);
    }

    /** The event view, or null when the record is refused. */
    static String event(String key, Object value) {
        GenericRecord r = record(LiquidityWallSchemas.EVENT, value, ONLY_VERSION_1);
        if (r == null) {
            return null;
        }
        String kind = text(r.get("kind"));
        if (FLIP.equals(kind)) {
            if (!(dashboardKey(r) + "|" + FLIP).equals(key) || !(r.get("flip") instanceof GenericRecord)) {
                return null;
            }
        } else {
            if (!(r.get("wall") instanceof GenericRecord wall) || key == null || !key.equals(text(wall.get("clusterId"))) || key.isBlank()) {
                return null;
            }
        }
        return render(r);
    }

    /** The voice view WITHOUT the stale flag (the relay appends it at delivery), or null when the record is refused. */
    static String voice(String key, Object value) {
        GenericRecord r = record(LiquidityWallSchemas.VOICE, value, ONLY_VERSION_1);
        if (r == null || !dashboardKey(r).equals(key) || text(r.get("text")).isBlank() || text(r.get("voiceId")).isBlank()) {
            return null;
        }
        return render(r);
    }

    /**
     * A bucket record ({@code LiquidityWallBucket}, key {@code <side>|<tick>}) when it passes the boundary, else null. Never rendered for a socket: it feeds
     * the projection's classification-window quantities only.
     */
    static GenericRecord bucket(String key, Object value) {
        GenericRecord r = record(LiquidityWallSchemas.BUCKET, value, ONLY_VERSION_1);
        if (r == null || key == null || !key.equals(text(r.get("side")) + "|" + text(r.get("tick")))) {
            return null;
        }
        return r;
    }

    /** The voice view with the gateway's {@code stale} flag (section 7b: delivered marked stale beyond the freshness bound). */
    static String withStale(String voiceJson, boolean stale) {
        return flagged(voiceJson, "stale", stale);
    }

    /**
     * A delivered frame with the gateway's {@code cached} flag: {@code true} on every record a connect replay sends, {@code false} on a live forward, so the page
     * can tell a replayed record from one that arrived live (it never speaks a replayed voice record) without a timer.
     */
    static String withCached(String json, boolean cached) {
        return flagged(json, "cached", cached);
    }

    /** {@code json} (an object) with one more boolean member appended last. */
    static String flagged(String json, String name, boolean value) {
        return json.substring(0, json.length() - 1) + ",\"" + name + "\":" + value + "}";
    }

    /** The section 8 dashboard key of a record: {@code CME|<contract>}. */
    static String dashboardKey(GenericRecord r) {
        return "CME|" + text(r.get("contract"));
    }

    /** The record when it is the subject's, with a {@code schemaVersion} in {@code versions}, with a positive bucket end and a non-blank environment and contract; else null. */
    private static GenericRecord record(LiquidityWallSchemas.Subject subject, Object value, Set<Integer> versions) {
        if (!(value instanceof GenericRecord r) || !subject.name().equals(r.getSchema().getFullName())) {
            return null;
        }
        try {
            if (!(r.get("schemaVersion") instanceof Integer version) || !versions.contains(version) || !(r.get("bucketEnd") instanceof Long bucketEnd) || bucketEnd <= 0
                    || text(r.get("depthEnvironment")).isBlank() || text(r.get("contract")).isBlank() || r.get("configFingerprint") == null) {
                return null;
            }
            return r;
        } catch (RuntimeException invalid) {
            return null;   // a field absent from this writer's schema: refused, never repaired
        }
    }

    private static String render(GenericRecord r) {
        String json = AvroFrameJson.render(r, TaRelay.FRAME_MAX_BYTES, false);
        return json != null && StudiesRelay.utf8Length(json) <= TaRelay.FRAME_MAX_BYTES ? json : null;
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
