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
            case ENUM -> symbol(s, s.getEnumSymbols().get(0).equals("UNKNOWN") ? s.getEnumSymbols().get(1) : s.getEnumSymbols().get(0));
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

    // ── Phase B / C ────────────────────────────────────────────────────────────────────────────

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

    // ── Phase D ────────────────────────────────────────────────────────────────────────────────

    private static GenericRecord header(String record, long asOfMs) {
        GenericRecord r = minimal(schema(record));
        put(r, "schemaVersion", 1);
        put(r, "symbol", "ES");
        put(r, "contractEpoch", "ESZ6");
        put(r, "asOfMs", asOfMs);
        put(r, "validation", "UNVALIDATED");
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

    // ── Phase E ────────────────────────────────────────────────────────────────────────────────

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
