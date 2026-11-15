package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.optionsedge.contracts.esmbo.EsMboSchemas;
import java.util.List;
import java.util.Set;
import org.apache.avro.generic.GenericRecord;

/**
 * The browser view of the three ES MBO records the gateway relays (es-liquidity-engine-service): {@code EsMboLevelFlow} (folded into frames, never sent on
 * its own), {@code EsMboHealth} (closes a bucket) and {@code EsMboEvent}. The gateway reshapes and forwards; it computes no signal and applies no
 * threshold. Ticks stay RAW ES ticks (the page converts to SPX). A frame / event base is the JSON object WITHOUT its closing brace and without the
 * {@code cached} member, so one stored string serves both the live forward ({@code cached:false}) and the connect replay ({@code cached:true}).
 */
final class EsMboJson {
    /** Levels kept either side of the mid of best bid / best ask, in raw ticks. */
    static final int WINDOW_TICKS = 60;
    /** The records have one version each (health: 1 archival, 2 current). */
    static final Set<Integer> ONLY_VERSION_1 = Set.of(1);
    static final Set<Integer> HEALTH_VERSIONS = Set.of(1, 2);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EsMboJson() {
    }

    /** One level-flow row as the frame carries it. */
    record Row(String side, long tick, long restingQty, long moveInQty, long moveOutQty, long orderCount) {
    }

    /** The record when it is the subject's, with an accepted schemaVersion, a positive bucketEnd and a non-blank contract; else null. */
    static GenericRecord checked(EsMboSchemas.Subject subject, Object value, Set<Integer> versions) {
        if (!(value instanceof GenericRecord r) || !subject.name().equals(r.getSchema().getFullName())) {
            return null;
        }
        try {
            if (!(r.get("schemaVersion") instanceof Integer version) || !versions.contains(version) || !(r.get("bucketEnd") instanceof Long bucketEnd) || bucketEnd <= 0
                    || text(r.get("contract")).isBlank()) {
                return null;
            }
            return r;
        } catch (RuntimeException invalid) {
            return null;   // a field absent from this writer's schema: refused, never repaired
        }
    }

    /** A level-flow row (key {@code <side>|<tick>}), or null when the record is refused. */
    static Row levelFlow(String key, GenericRecord r) {
        try {
            String side = text(r.get("side"));
            if (!(r.get("tick") instanceof Long tick) || side.isBlank() || !key.equals(side + "|" + tick)) {
                return null;
            }
            return new Row(side, tick, num(r.get("restingQty")), num(r.get("moveInQty")), num(r.get("moveOutQty")), num(r.get("orderCount")));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /** True when the health record's key is {@code CME|<contract>}. */
    static boolean healthKey(String key, GenericRecord r) {
        return key != null && key.equals("CME|" + text(r.get("contract")));
    }

    /**
     * The frame of a closed bucket without its closing brace. Only rows within {@link #WINDOW_TICKS} of the mid of the best bid and best ask are kept;
     * with no best bid or no best ask NO level is kept (the epochStatus says why).
     */
    static String frameBase(String contract, long bucketEnd, String epochStatus, Long bestBid, Long bestAsk, List<Row> rows) {
        StringBuilder sb = new StringBuilder(256 + rows.size() * 40);
        sb.append("{\"contract\":").append(quote(contract)).append(",\"bucketEnd\":").append(bucketEnd).append(",\"epochStatus\":").append(quote(epochStatus))
                .append(",\"bestBidTick\":").append(bestBid).append(",\"bestAskTick\":").append(bestAsk).append(",\"levels\":[");
        boolean first = true;
        if (bestBid != null && bestAsk != null) {
            double mid = (bestBid + bestAsk) / 2.0;
            for (Row row : rows) {
                if (Math.abs(row.tick() - mid) > WINDOW_TICKS) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('[').append(quote(row.side())).append(',').append(row.tick()).append(',').append(row.restingQty()).append(',').append(row.moveInQty())
                        .append(',').append(row.moveOutQty()).append(',').append(row.orderCount()).append(']');
            }
        }
        return sb.append(']').toString();
    }

    /** The event view without its closing brace, or null when the record is refused (blank eventId / kind / status, key not the eventId). */
    static String eventBase(String key, GenericRecord r) {
        try {
            String eventId = text(r.get("eventId"));
            if (eventId.isBlank() || !eventId.equals(key) || !(r.get("revision") instanceof Integer revision) || text(r.get("kind")).isBlank()
                    || text(r.get("status")).isBlank() || !(r.get("startMs") instanceof Long start) || !(r.get("values") instanceof List<?> values)) {
                return null;
            }
            ObjectNode n = MAPPER.createObjectNode();
            n.put("contract", text(r.get("contract")));
            n.put("bucketEnd", (Long) r.get("bucketEnd"));
            n.put("eventId", eventId);
            n.put("revision", revision);
            n.put("kind", text(r.get("kind")));
            n.put("status", text(r.get("status")));
            n.put("startMs", start);
            putLong(n, "endMs", r.get("endMs"));
            putText(n, "direction", r.get("direction"));
            putLong(n, "loTick", r.get("loTick"));
            putLong(n, "hiTick", r.get("hiTick"));
            n.put("availability", text(r.get("availability")));
            ObjectNode map = n.putObject("values");
            for (Object v : values) {
                GenericRecord gr = (GenericRecord) v;
                map.put(text(gr.get("name")), (Long) gr.get("value"));
            }
            String json = MAPPER.writeValueAsString(n);
            return json.length() > TaRelay.FRAME_MAX_BYTES ? null : json.substring(0, json.length() - 1);
        } catch (RuntimeException | JsonProcessingException invalid) {
            return null;
        }
    }

    /** {@code base} closed with the gateway's {@code cached} flag. */
    static String close(String base, boolean cached) {
        return base + ",\"cached\":" + cached + "}";
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long num(Object value) {
        return ((Number) value).longValue();
    }

    private static void putLong(ObjectNode n, String name, Object v) {
        if (v == null) {
            n.putNull(name);
        } else {
            n.put(name, ((Number) v).longValue());
        }
    }

    private static void putText(ObjectNode n, String name, Object v) {
        if (v == null) {
            n.putNull(name);
        } else {
            n.put(name, v.toString());
        }
    }

    static String quote(String s) {
        try {
            return MAPPER.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
