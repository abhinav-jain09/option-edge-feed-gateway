package app.feedgateway;

import java.util.ArrayList;
import java.util.List;
import org.apache.avro.generic.GenericRecord;

/**
 * The latest record per timeframe (1m, 5m, 15m) of one stream: the Phase B structure tools and the Phase C geometry
 * (ta-books-catalog/05_GEOMETRY_FRAME_CONTRACT.md). The gateway checks only the boundary (record type, schema version, service, state,
 * timeframe, size); the page is the full validator. Per timeframe the set with the greatest {@code asOfMs} is retained, a smaller one
 * is a counted regression and an equal one replaces. The hello carries the retained sets whole, in the order 1m, 5m, 15m, while they
 * fit the part budget and {@code null} for the rest: a set is never truncated or thinned.
 */
final class TimeframeSetRelay extends TaRelay {
    static final String TOOLS_EVENT = "market-structure-tools";
    static final String GEOMETRY_EVENT = "market-structure-geometry";
    /** Each hello part (tools, geometry) is held under 384 KiB of retained sets. */
    static final long HELLO_PART_BYTES = 384L * 1024;
    /** Three sets are published per 1m fold; the last three folds re-read on a cold start cover every timeframe. */
    static final int RECOVERY_TAIL_RECORDS = 12;

    private static final String SERVICE = "market-structure";
    private static final String TOOLS_SCHEMA = "com.optionsedge.contracts.marketstructure.tools.StructureToolsSet";
    private static final String GEOMETRY_SET_SCHEMA = "com.optionsedge.contracts.marketstructure.geometry.GeometryAnnotationSet";
    private static final String GEOMETRY_UNAVAILABLE_SCHEMA = "com.optionsedge.contracts.marketstructure.geometry.GeometryUnavailable";
    /** The producer's timeframe enum symbols of the three forwarded timeframes (H1 and H4 are dropped). */
    private static final String[] TOOLS_TF = {"M1", "M5", "M15"};
    private static final String[] GEOMETRY_TF = {"TF_1M", "TF_5M", "TF_15M"};

    /** The retained state: per timeframe the rendered set, its clock and its size; {@code version} changes with every change. */
    private static final class Store {
        final String[] json = new String[TF.length];
        final long[] asOf = {-1L, -1L, -1L};
        final int[] bytes = new int[TF.length];
        long version;

        Store copy() {
            Store c = new Store();
            System.arraycopy(json, 0, c.json, 0, json.length);
            System.arraycopy(asOf, 0, c.asOf, 0, asOf.length);
            System.arraycopy(bytes, 0, c.bytes, 0, bytes.length);
            c.version = version;
            return c;
        }

        void put(int slot, String frame, int size, long clock) {
            json[slot] = frame;
            bytes[slot] = size;
            asOf[slot] = clock;
            version++;
        }
    }

    private final String event;
    private final String field;
    private final boolean geometry;
    private Store live = new Store();
    private Store staged;
    private String helloCache;
    private long helloVersion = -1L;
    private long helloCap = -1L;

    private TimeframeSetRelay(String name, String event, String field, boolean geometry) {
        super(name);
        this.event = event;
        this.field = field;
        this.geometry = geometry;
    }

    static TimeframeSetRelay structureTools() {
        return new TimeframeSetRelay("structure_tools", TOOLS_EVENT, "tools", false);
    }

    static TimeframeSetRelay geometry() {
        return new TimeframeSetRelay("geometry", GEOMETRY_EVENT, "geometry", true);
    }

    @Override
    boolean handles(String bindingEvent) {
        return event.equals(bindingEvent);
    }

    @Override
    synchronized String[] onLive(String bindingEvent, Object value) {
        String frame = apply(live, value);
        return frame == null ? null : new String[] {event, frame};
    }

    @Override
    synchronized void beginRecovery() {
        staged = live.copy();
    }

    @Override
    synchronized void onStaged(String bindingEvent, Object value) {
        apply(staged, value);
    }

    @Override
    synchronized List<String[]> promote() {
        List<String[]> frames = new ArrayList<>(TF.length);
        for (int t = 0; staged != null && t < TF.length; t++) {
            if (staged.json[t] != null && staged.asOf[t] > live.asOf[t]) {
                live.put(t, staged.json[t], staged.bytes[t], staged.asOf[t]);
                frames.add(new String[] {event, staged.json[t]});
            }
        }
        staged = null;
        return frames;
    }

    @Override
    int tailRecords(String bindingEvent) {
        return RECOVERY_TAIL_RECORDS;
    }

    @Override
    synchronized String helloField(long budget) {
        long cap = Math.min(budget, HELLO_PART_BYTES);
        if (helloCache == null || helloVersion != live.version || helloCap != cap) {
            helloCache = renderHello(live, cap);
            helloVersion = live.version;
            helloCap = cap;
        }
        return helloCache;
    }

    private String renderHello(Store st, long cap) {
        StringBuilder sb = new StringBuilder(",\"").append(field).append("\":{");
        long used = 0;
        boolean fits = true;
        for (int t = 0; t < TF.length; t++) {
            sb.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":");
            if (fits && st.json[t] != null && used + st.bytes[t] + 1 <= cap) {
                used += st.bytes[t] + 1;
                sb.append(st.json[t]);
            } else {
                fits &= st.json[t] == null;   // the first retained set that does not fit ends the list: the rest are null
                sb.append("null");
            }
        }
        return sb.append('}').toString();
    }

    private String apply(Store target, Object value) {
        GenericRecord r = recordOrDrop(value);
        if (r == null) {
            return null;
        }
        try {
            int slot = geometry ? geometrySlot(r) : toolsSlot(r);
            long clock = (Long) r.get("asOfMs");
            if (slot < 0 || clock < 0) {
                onDrop();
                return null;
            }
            if (clock < target.asOf[slot]) {
                regressed();
                return null;
            }
            String frame = AvroFrameJson.render(r, FRAME_MAX_BYTES, false);
            int size = frame == null ? Integer.MAX_VALUE : StudiesRelay.utf8Length(frame);
            if (size > FRAME_MAX_BYTES) {
                onDrop();
                return null;
            }
            target.put(slot, frame, size, clock);
            accepted();
            return frame;
        } catch (RuntimeException e) {
            onDrop();   // a missing or mistyped field is refused, never repaired
            return null;
        }
    }

    private static int toolsSlot(GenericRecord r) {
        return TOOLS_SCHEMA.equals(r.getSchema().getFullName()) && Integer.valueOf(1).equals(r.get("schemaVersion"))
                && SERVICE.equals(String.valueOf(r.get("service"))) && isState(r.get("state"))
                ? indexOf(TOOLS_TF, String.valueOf(r.get("timeframe"))) : -1;
    }

    /** A set (state OK) or the short unavailable record (state UNAVAILABLE) of one of the three timeframes. */
    private static int geometrySlot(GenericRecord r) {
        String schema = r.getSchema().getFullName();
        String state = String.valueOf(r.get("state"));
        boolean set = GEOMETRY_SET_SCHEMA.equals(schema) && "OK".equals(state);
        boolean unavailable = GEOMETRY_UNAVAILABLE_SCHEMA.equals(schema) && "UNAVAILABLE".equals(state);
        return (set || unavailable) && Integer.valueOf(1).equals(r.get("schemaVersion")) && SERVICE.equals(String.valueOf(r.get("service")))
                ? indexOf(GEOMETRY_TF, String.valueOf(r.get("timeframe"))) : -1;
    }

    private static boolean isState(Object state) {
        String s = String.valueOf(state);
        return "OK".equals(s) || "UNAVAILABLE".equals(s);
    }
}
