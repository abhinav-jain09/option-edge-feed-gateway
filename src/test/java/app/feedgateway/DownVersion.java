package app.feedgateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * Rewrites a decoded record of a version-2 schema as the record of the registered version 1 of the same subject: every field the older schema has is copied (recursively) and the fields only
 * version 2 has are dropped. It is how a test makes a REAL, fully populated version-1 record (a producer that has not been upgraded writes exactly this) out of the real version-2 record
 * of the producer, so a version-1 relay test walks real objects, annotations and timeframes instead of empty lists.
 */
final class DownVersion {
    private DownVersion() {
    }

    static GenericRecord to(GenericRecord source, Schema older) {
        GenericRecord out = new GenericData.Record(older);
        for (Schema.Field f : older.getFields()) {
            out.put(f.pos(), convert(source.get(f.name()), f.schema()));
        }
        return out;
    }

    private static Object convert(Object v, Schema target) {
        if (v == null) {
            return null;
        }
        switch (target.getType()) {
            case RECORD:
                return to((GenericRecord) v, target);
            case ENUM:
                return new GenericData.EnumSymbol(target, v.toString());
            case ARRAY: {
                List<Object> list = new ArrayList<>();
                for (Object x : (List<?>) v) {
                    list.add(convert(x, target.getElementType()));
                }
                return list;
            }
            case MAP: {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                    map.put(e.getKey().toString(), convert(e.getValue(), target.getValueType()));
                }
                return map;
            }
            case UNION:
                for (Schema branch : target.getTypes()) {
                    if (branch.getType() != Schema.Type.NULL) {
                        return convert(v, branch);
                    }
                }
                return null;
            case STRING:
                return v.toString();
            default:
                return v;
        }
    }
}
