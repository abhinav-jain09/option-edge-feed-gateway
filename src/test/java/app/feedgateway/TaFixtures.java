package app.feedgateway;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * Records of the technical-analysis family built from the producers' own schemas (src/test/resources/ta, byte-identical copies of the
 * contracts registry files, pinned by TaSchemaPinsTest): every field the gateway judges is set explicitly, every other field takes the
 * first non-UNKNOWN enum symbol, an empty collection, null for an optional, or 1 / "x" / false.
 */
final class TaFixtures {
    private TaFixtures() {
    }

    static Schema schema(String name) {
        try (InputStream in = TaFixtures.class.getResourceAsStream("/ta/" + name + ".avsc")) {
            if (in == null) {
                throw new IllegalStateException("test resource /ta/" + name + ".avsc is missing");
            }
            return new Schema.Parser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static GenericRecord minimal(Schema schema) {
        GenericRecord r = new GenericData.Record(schema);
        for (Schema.Field f : schema.getFields()) {
            r.put(f.pos(), value(f.schema()));
        }
        return r;
    }

    private static Object value(Schema s) {
        return switch (s.getType()) {
            case RECORD -> minimal(s);
            case ENUM -> symbol(s, s.getEnumSymbols().get(0).equals("UNKNOWN") && s.getEnumSymbols().size() > 1 ? s.getEnumSymbols().get(1) : s.getEnumSymbols().get(0));
            case ARRAY -> new GenericData.Array<>(0, s);
            case MAP -> Map.of();
            case UNION -> s.getTypes().get(0).getType() == Schema.Type.NULL ? null : value(s.getTypes().get(0));
            case STRING -> "x";
            case INT -> 1;
            case LONG -> 1L;
            case BOOLEAN -> false;
            case BYTES -> ByteBuffer.wrap(new byte[] {1});
            default -> throw new IllegalStateException(s.toString());
        };
    }

    static GenericData.EnumSymbol symbol(Schema enumSchema, String symbol) {
        return new GenericData.EnumSymbol(enumSchema, symbol);
    }

    /** Sets {@code field} of {@code r}; a String for an enum field (also inside a nullable union) becomes the enum symbol. */
    static GenericRecord put(GenericRecord r, String field, Object v) {
        Schema s = r.getSchema().getField(field).schema();
        if (s.getType() == Schema.Type.UNION) {
            s = s.getTypes().get(s.getTypes().get(0).getType() == Schema.Type.NULL ? 1 : 0);
        }
        r.put(field, s.getType() == Schema.Type.ENUM && v instanceof String name ? symbol(s, name) : v);
        return r;
    }

    static GenericRecord child(GenericRecord r, String field) {
        return (GenericRecord) r.get(field);
    }

    static String frame(GenericRecord r) {
        return AvroFrameJson.render(r, Integer.MAX_VALUE, false);
    }

    // ── W0 ─────────────────────────────────────────────────────────────────────────────────────

    static GenericRecord uiProjection(long asOfMs) {
        GenericRecord r = minimal(schema("UiProjection"));
        put(r, "schemaVersion", 1);
        put(r, "service", "market-structure");
        put(r, "asOfMs", asOfMs);
        put(r, "state", "OK");
        return r;
    }

    static GenericRecord patternSet(String timeframe, long asOfMs) {
        GenericRecord r = minimal(schema("PatternAnnotationSet"));
        put(r, "schemaVersion", 1);
        put(r, "service", "market-structure");
        put(r, "state", "OK");
        put(r, "asOfMs", asOfMs);
        put(r, "timeframe", timeframe);
        put(r, "scheduleId", "2026-10-16.r0000000000");
        return r;
    }

    // ── CHART-STRUCTURE-TOOLS / CLASSICAL-CHART-PATTERNS ────────────────────────────────────────────────────────────────────────────

    static GenericRecord tools(String timeframe, long asOfMs, String state) {
        GenericRecord r = minimal(schema("StructureToolsSet"));
        put(r, "schemaVersion", 1);
        put(r, "service", "market-structure");
        put(r, "state", state);
        put(r, "timeframe", timeframe);
        put(r, "asOfMs", asOfMs);
        put(r, "scheduleId", "2026-10-20.r0000000000");
        return r;
    }

    static GenericRecord geometrySet(String timeframe, long asOfMs) {
        GenericRecord r = minimal(schema("GeometryAnnotationSet"));
        put(r, "schemaVersion", 1);
        put(r, "service", "market-structure");
        put(r, "state", "OK");
        put(r, "timeframe", timeframe);
        put(r, "asOfMs", asOfMs);
        put(r, "scheduleId", "2026-10-20.r0000000000");
        return r;
    }

    static GenericRecord geometryUnavailable(String timeframe, long asOfMs) {
        GenericRecord r = minimal(schema("GeometryUnavailable"));
        put(r, "schemaVersion", 1);
        put(r, "service", "market-structure");
        put(r, "state", "UNAVAILABLE");
        put(r, "timeframe", timeframe);
        put(r, "asOfMs", asOfMs);
        return r;
    }

    // ── TICK-TRADE-TOOLS ────────────────────────────────────────────────────────────────────────────────

    private static GenericRecord header(String record, long asOfMs) {
        GenericRecord r = minimal(schema(record));
        put(r, "schemaVersion", 1);
        put(r, "symbol", "ES");
        put(r, "contractEpoch", "ESZ6");
        put(r, "asOfMs", asOfMs);
        put(r, "calibration", "CALIBRATED");
        put(r, "validation", "MEASURED");
        put(r, "evidence", evidence(r, "NONE"));
        return r;
    }

    /** The producer's {@code Evidence} record of a header-bearing record's schema, filled like a real row (revision 21); {@code edge} is the MeasuredEdge symbol. */
    static GenericRecord evidence(GenericRecord header, String edge) {
        Schema union = header.getSchema().getField("evidence").schema();
        GenericRecord e = minimal(union.getTypes().get(1));
        put(e, "validationResultId", "TT-CAL-1#C8:rv2500-rg2000");
        put(e, "claim", "climax candidate, direction of price 30 minutes later");
        put(e, "measuredEdge", edge);
        put(e, "calibrationFrom", "2025-08-08");
        put(e, "calibrationTo", "2026-04-30");
        put(e, "validationFrom", "2026-05-01");
        put(e, "validationTo", "2026-08-07");
        put(e, "datasetSha256", "3ef872c451a70837e6c7e90b9036fd5748d57305ca56173a922e76e3f5125e0c");
        put(e, "statement", "climax: no measured edge beyond baseline (n=119, adjusted p=0.94, history 2025-08-08..2026-08-07)");
        if (edge.equals("NONE")) {
            put(e, "n", 119L);
            put(e, "effectMilli", 8820L);
            put(e, "effectUnit", "ticks");
            put(e, "pAdjPpm", 942529);
            put(e, "horizonMinutes", 30);
        }
        return e;
    }

    /** The record of a configuration other than the calibrated one: SEED_UNCALIBRATED, UNVALIDATED, no evidence (what the producer writes then). */
    static GenericRecord uncalibrated(GenericRecord r) {
        put(r, "calibration", "SEED_UNCALIBRATED");
        put(r, "validation", "UNVALIDATED");
        put(r, "evidence", null);
        return r;
    }

    static GenericRecord chart(String chartId, String horizon, long asOfMs) {
        GenericRecord r = header("PfChartState", asOfMs);
        put(r, "chartId", chartId);
        put(r, "horizon", horizon);
        return r;
    }

    static GenericRecord column(String chartId, long columnIndex, long asOfMs) {
        GenericRecord r = header("PfColumn", asOfMs);
        put(r, "chartId", chartId);
        put(r, "columnIndex", columnIndex);
        return r;
    }

    /** One of the seven P&F event records ({@code PfBreakEvent}, {@code PfLineEvent}, ...). */
    static GenericRecord pfEvent(String record, String chartId, String eventKey, long eventTimeMs, long asOfMs) {
        GenericRecord r = header(record, asOfMs);
        put(r, "chartId", chartId);
        put(r, "eventKey", eventKey);
        put(r, "eventTimeMs", eventTimeMs);
        return r;
    }

    static GenericRecord nodes(String scope, int windowSessions, long asOfMs) {
        GenericRecord r = header("VolumeNodeSet", asOfMs);
        put(r, "scope", scope);
        put(r, "windowSessions", windowSessions);
        return r;
    }

    static GenericRecord flowBar(String width, long barStartMs, long asOfMs) {
        GenericRecord r = header("FlowBar", asOfMs);
        put(r, "width", width);
        put(r, "barStartMs", barStartMs);
        return r;
    }

    static GenericRecord climax(String climaxId, String width, long candidateBarStartMs, long asOfMs) {
        GenericRecord r = header("ClimaxEvent", asOfMs);
        put(r, "climaxId", climaxId);
        put(r, "width", width);
        put(r, "candidateBarStartMs", candidateBarStartMs);
        return r;
    }

    // ── ELLIOTT-FIB-CYCLE-TOOLS ────────────────────────────────────────────────────────────────────────────────

    static GenericRecord waveSet(String timeframe, int epoch, long seq, String state) {
        return envelope(schema("WaveSet"), timeframe, epoch, seq, state);
    }

    static GenericRecord cycleSet(String timeframe, int epoch, long seq, String state) {
        return envelope(schema("CycleSet"), timeframe, epoch, seq, state);
    }

    private static GenericRecord envelope(Schema schema, String timeframe, int epoch, long seq, String state) {
        GenericRecord r = minimal(schema);
        GenericRecord env = child(r, "env");
        put(env, "schemaVersion", 1);
        put(env, "timeframe", timeframe);
        put(env, "epoch", epoch);
        put(env, "seq", seq);
        put(env, "state", state);
        put(env, "asOfMs", 1_790_000_000_000L + seq);
        return r;
    }
}
