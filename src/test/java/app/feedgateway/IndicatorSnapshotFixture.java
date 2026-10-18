package app.feedgateway;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.optionsedge.contracts.indicators.IndicatorJson;
import com.optionsedge.contracts.indicators.IndicatorSnapshot;
import com.optionsedge.contracts.indicators.avro.AvroRecordMapper;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;

/**
 * Test-side Avro for the indicator CURRENT topics: a COMPLETE contract snapshot (every required field populated) with
 * the fields a test looks at set by the test, handed over as the GENERIC record the gateway's Avro consumer yields
 * (specific record → binary → GenericDatumReader), exactly as a deserialized value arrives in the consumer loop.
 */
final class IndicatorSnapshotFixture {
    private IndicatorSnapshotFixture() {
    }

    /**
     * The pre-Avro Java record (what the producer holds before it serializes): complete, with the test's values merged
     * in; its maps are keyed the way the producer keys them (timeframes 30s,1m,...; bias those plus SESSION).
     */
    static IndicatorSnapshot record(String json) {
        try {
            // an exact tree: a default tree would turn 6420.50 into 6420.5
            com.fasterxml.jackson.databind.ObjectMapper exact = IndicatorJson.reader().copy()
                    .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true))
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            ObjectNode full = exact.valueToTree(build(IndicatorSnapshot.class));
            ObjectNode wanted = (ObjectNode) exact.readTree(json);
            merge(full, wanted);
            return exact.treeToValue(full, IndicatorSnapshot.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** The generic IndicatorSnapshot record whose JSON image carries the test's values. */
    static GenericRecord generic(String json) {
        return generic(record(json));
    }

    /** The generic record the gateway's Avro consumer yields for a producer's record. */
    static GenericRecord generic(IndicatorSnapshot record) {
        try {
            com.optionsedge.contracts.indicators.avro.IndicatorSnapshot specific = AvroRecordMapper.toAvro(record,
                    com.optionsedge.contracts.indicators.avro.IndicatorSnapshot.class);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BinaryEncoder enc = EncoderFactory.get().binaryEncoder(bos, null);
            new SpecificDatumWriter<>(com.optionsedge.contracts.indicators.avro.IndicatorSnapshot.class).write(specific, enc);
            enc.flush();
            return new GenericDatumReader<GenericRecord>(specific.getSchema(), specific.getSchema(), GenericData.get())
                    .read(null, DecoderFactory.get().binaryDecoder(bos.toByteArray(), null));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** The signals twin of {@link #record(String)}: a complete {@code IndicatorSignals} with the test's values merged in. */
    static com.optionsedge.contracts.indicators.SignalContracts.IndicatorSignals signals(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper exact = IndicatorJson.reader().copy()
                    .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true))
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            ObjectNode full = exact.valueToTree(build(com.optionsedge.contracts.indicators.SignalContracts.IndicatorSignals.class));
            merge(full, (ObjectNode) exact.readTree(json));
            return exact.treeToValue(full, com.optionsedge.contracts.indicators.SignalContracts.IndicatorSignals.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** The generic {@code IndicatorSignals} record the gateway's Avro consumer yields for a producer's record. */
    static GenericRecord generic(com.optionsedge.contracts.indicators.SignalContracts.IndicatorSignals record) {
        try {
            com.optionsedge.contracts.indicators.avro.IndicatorSignals specific = AvroRecordMapper.toAvro(record,
                    com.optionsedge.contracts.indicators.avro.IndicatorSignals.class);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BinaryEncoder enc = EncoderFactory.get().binaryEncoder(bos, null);
            new SpecificDatumWriter<>(com.optionsedge.contracts.indicators.avro.IndicatorSignals.class).write(specific, enc);
            enc.flush();
            return new GenericDatumReader<GenericRecord>(specific.getSchema(), specific.getSchema(), GenericData.get())
                    .read(null, DecoderFactory.get().binaryDecoder(bos.toByteArray(), null));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** The contract's own JSON rendering of a signals record. */
    static String signalsContractJson(GenericRecord record) {
        try {
            return IndicatorJson.writer().writeValueAsString(AvroRecordMapper.fromAvro(record,
                    com.optionsedge.contracts.indicators.SignalContracts.IndicatorSignals.class));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** The contract's own JSON rendering of the record: what every reader of the topic re-emits. */
    static String contractJson(GenericRecord record) {
        try {
            return IndicatorJson.writer().writeValueAsString(AvroRecordMapper.fromAvro(record, IndicatorSnapshot.class));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void merge(ObjectNode into, ObjectNode from) {
        from.fields().forEachRemaining(e -> {
            if (e.getValue().isObject() && into.get(e.getKey()) != null && into.get(e.getKey()).isObject()) {
                merge((ObjectNode) into.get(e.getKey()), (ObjectNode) e.getValue());
            } else {
                into.set(e.getKey(), e.getValue());
            }
        });
    }

    private static int n;

    /** A deterministic, fully populated instance of a contract record. */
    private static Object build(Class<?> record) {
        RecordComponent[] comps = record.getRecordComponents();
        Object[] args = new Object[comps.length];
        Class<?>[] types = new Class<?>[comps.length];
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            args[i] = value(comps[i].getGenericType(), comps[i].getName());
        }
        try {
            var ctor = record.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(record.getName(), e);
        }
    }

    private static Object value(Type t, String name) {
        n++;
        if (t instanceof ParameterizedType p) {
            Class<?> raw = (Class<?>) p.getRawType();
            if (List.class.isAssignableFrom(raw)) {
                return List.of(value(p.getActualTypeArguments()[0], name));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            // the producer's keys, in the producer's order: the timeframe maps carry every timeframe (and bias SESSION)
            String[] keys = name.equals("timeframes") ? new String[] {"30s", "1m", "5m", "15m", "1h", "4h"}
                    : name.equals("bias") ? new String[] {"30s", "1m", "5m", "15m", "1h", "4h", "SESSION"} : new String[] {"k" + n};
            for (String k : keys) {
                m.put(k, value(p.getActualTypeArguments()[1], name));
            }
            return m;
        }
        Class<?> c = (Class<?>) t;
        if (c == String.class) {
            // every string the reader may parse as a time or date is a valid one
            return name.toLowerCase().contains("date") ? "2026-10-14" : name.toLowerCase().contains("time") || name.toLowerCase().contains("at")
                    ? "2026-10-14T13:30:00Z" : name + "-" + n;
        }
        if (c == Long.class || c == long.class) {
            return (long) n;
        }
        if (c == Integer.class || c == int.class) {
            return n;
        }
        if (c == Boolean.class || c == boolean.class) {
            return Boolean.TRUE;
        }
        if (c == BigDecimal.class) {
            return new BigDecimal(n).movePointLeft(2).setScale(3);   // e.g. 0.120: a trailing zero the wire must keep
        }
        if (c == byte[].class) {
            return new byte[] {1, 2};
        }
        if (c == long[].class) {
            return new long[] {n};
        }
        if (c.isEnum()) {
            return c.getEnumConstants()[0];
        }
        if (c.isRecord()) {
            return build(c);
        }
        throw new AssertionError(name + ": " + c);
    }

    @SuppressWarnings("unused")
    private static boolean required(Class<?> record, RecordComponent rc) {
        try {
            Field f = record.getDeclaredField(rc.getName());
            JsonSetter js = f.getAnnotation(JsonSetter.class);
            return rc.getType().isPrimitive() || (js != null && js.nulls() == Nulls.FAIL);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }
}
