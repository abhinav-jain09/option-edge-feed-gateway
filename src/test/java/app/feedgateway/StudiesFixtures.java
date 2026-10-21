package app.feedgateway;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * StudyBar / StudiesCurrent records built from the producer's own schemas (src/test/resources/studies, byte-identical copies of
 * the contracts registry files): the fields the gateway judges are set explicitly, every other field takes its first/empty/zero
 * value, and the 56 values / 8 states of a COMPLETE bar are filled so JSON sizes are the real ones.
 */
final class StudiesFixtures {
    static final Schema BAR = load("StudyBar");
    static final Schema CURRENT = load("StudiesCurrent");
    static final long T0 = Instant.parse("2026-10-20T13:30:00Z").toEpochMilli();

    /** The real segment id of the dev SPX series (32 hex): the barId is then 71-72 characters, as the producer really emits it (a 4-character fixture segment hid the 64 bound). */
    static final String SEGMENT = "c5c51360e1554b3b9e7fd8ab6644914d";

    private StudiesFixtures() {
    }

    private static Schema load(String name) {
        try (InputStream in = StudiesFixtures.class.getResourceAsStream("/studies/" + name + ".avsc")) {
            return new Schema.Parser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A COMPLETE bar of {@code symbol} and Avro timeframe symbol ("T5M") whose barStart is T0 + index minutes. */
    static GenericRecord bar(String symbol, String timeframe, int index) {
        return bar(symbol, timeframe, index, "COMPLETE");
    }

    static GenericRecord bar(String symbol, String timeframe, int index, String status) {
        GenericRecord r = minimal(BAR);
        long start = T0 + index * 60_000L;
        r.put("schemaVersion", 1);
        r.put("methodVersion", "STUDIES_V1");
        r.put("parameterSetHash", "a".repeat(64));
        r.put("symbol", symbol);
        r.put("tradeDate", "2026-10-20");
        r.put("timeframe", enumOf(BAR, "timeframe", timeframe));
        r.put("barId", symbol + "|" + SEGMENT + "|2026-10-20|" + timeframe.substring(1).toLowerCase() + "|" + Instant.ofEpochMilli(start).toString());
        r.put("barStart", Instant.ofEpochMilli(start).toString());
        r.put("barEnd", Instant.ofEpochMilli(start + 60_000L).toString());
        r.put("status", enumOf(BAR, "status", status));
        r.put("closesSeen", 100L + index);
        if ("COMPLETE".equals(status)) {
            r.put("values", values(BAR.getField("values").schema().getValueType(), 56, index));
            r.put("states", values(BAR.getField("states").schema().getValueType(), 8, index));
            r.put("events", List.of("RSI_OB_ENTER"));
        }
        r.put("calibration", "UNVALIDATED");
        return r;
    }

    /** The relay call as the service makes it: the topic family follows the record's symbol (ES records arrive on the es. topics). */
    static String onLive(StudiesRelay relay, String event, String key, Object value) {
        return relay.onLive(event, key, value, value instanceof GenericRecord r && "ES".equals(String.valueOf(r.get("symbol"))));
    }

    static void onStaged(StudiesRelay relay, StudiesRelay.Store staged, String event, String key, Object value) {
        relay.onStaged(staged, event, key, value, value instanceof GenericRecord r && "ES".equals(String.valueOf(r.get("symbol"))));
    }

    static String barKey(GenericRecord bar) {
        String tf = String.valueOf(bar.get("timeframe"));
        return bar.get("symbol") + "|" + tf.substring(1).toLowerCase();
    }

    /** A current state of {@code symbol} published at T0 + publishedMinutes with the six producer timeframes. */
    static GenericRecord current(String symbol, int publishedMinutes) {
        GenericRecord r = minimal(CURRENT);
        r.put("schemaVersion", 1);
        r.put("methodVersion", "STUDIES_V1");
        r.put("parameterSetHash", "a".repeat(64));
        r.put("symbol", symbol);
        r.put("tradeDate", "2026-10-20");
        r.put("runId", "run-1");
        r.put("revision", (long) publishedMinutes);
        r.put("publishedAt", Instant.ofEpochMilli(T0 + publishedMinutes * 60_000L).toString());
        r.put("quality", enumOf(CURRENT, "quality", "READY"));
        r.put("calibration", "UNVALIDATED");
        Schema tfSchema = CURRENT.getField("timeframes").schema().getValueType();
        Map<String, Object> byTf = new HashMap<>();
        for (String tf : List.of("30s", "1m", "5m", "15m", "1h", "4h")) {
            GenericRecord t = minimal(tfSchema);
            t.put("closesSeen", 50L);
            t.put("values", values(tfSchema.getField("values").schema().getValueType(), 56, publishedMinutes));
            t.put("states", values(tfSchema.getField("states").schema().getValueType(), 8, publishedMinutes));
            byTf.put(tf, t);
        }
        r.put("timeframes", byTf);
        return r;
    }

    static GenericRecord currentWithout(String symbol, int publishedMinutes, String missingTimeframe) {
        GenericRecord r = current(symbol, publishedMinutes);
        @SuppressWarnings("unchecked")
        Map<String, Object> byTf = (Map<String, Object>) r.get("timeframes");
        byTf.remove(missingTimeframe);
        return r;
    }

    private static Map<String, Object> values(Schema element, int count, int seed) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < count; i++) {
            GenericRecord v = minimal(element);
            if (element.getField("value") != null) {
                v.put("value", "5" + (1000 + i) + "." + (seed % 10) + "123");
                v.put("state", enumOf(element, "state", "READY"));
                v.put("reason", enumOf(element, "reason", "NONE"));
            }
            m.put("series.number." + i, v);
        }
        return m;
    }

    private static Object enumOf(Schema record, String field, String symbol) {
        Schema s = record.getField(field).schema();
        return new GenericData.EnumSymbol(s, symbol);
    }

    /** First symbol, empty collections, null optionals, 0/""/false scalars. */
    static GenericRecord minimal(Schema schema) {
        GenericRecord r = new GenericData.Record(schema);
        for (Schema.Field f : schema.getFields()) {
            r.put(f.pos(), zero(f.schema()));
        }
        return r;
    }

    private static Object zero(Schema s) {
        return switch (s.getType()) {
            case RECORD -> minimal(s);
            case ENUM -> new GenericData.EnumSymbol(s, s.getEnumSymbols().get(0));
            case ARRAY -> new ArrayList<>();
            case MAP -> new HashMap<>();
            case UNION -> s.getTypes().get(0).getType() == Schema.Type.NULL ? null : zero(s.getTypes().get(0));
            case STRING -> "";
            case INT -> 0;
            case LONG -> 0L;
            case BOOLEAN -> false;
            default -> throw new IllegalStateException(s.toString());
        };
    }
}
