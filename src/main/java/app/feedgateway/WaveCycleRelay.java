package app.feedgateway;

import java.util.ArrayList;
import java.util.List;
import org.apache.avro.generic.GenericRecord;

/**
 * ELLIOTT-FIB-CYCLE-TOOLS Elliott counts, Fibonacci price and time, channels and cycle bands (options-edge-processing docs/ta-books/catalog/WAVES-FRAME-CONTRACT.md): the
 * {@code WaveSet} and the {@code CycleSet} of the timeframes 1m, 5m and 15m (H1, D1 and W1 are dropped and counted, so seasonality and
 * the daily cycle tables never reach the page). Each type is retained independently per timeframe by {@code (env.epoch, env.seq)},
 * lexicographically: a smaller pair is a counted regression, an equal pair replaces ({@code asOfMs} is NOT the order: the single
 * {@code PARAMETER_SET_CHANGED} announcement carries 0). The hello carries per timeframe an object with the latest {@code waves} and
 * {@code cycles} (each possibly {@code null}), the whole timeframe or {@code null}, in the order 1m, 5m, 15m while they fit the part budget.
 * <p>
 * Version 2 of both records (ELLIOTT-FIB-CYCLE-TOOLS revision 21: {@code WaveCount.measurement}, {@code WaveSet.calibration}, {@code CycleSet.calibration}, all optional) needs no code here: a
 * frame is the rendering of the WRITER schema of the record, so a version-2 record carries the measured evidence and its fixed statements verbatim and a version-1 record carries none. The
 * gateway judges none of the new vocabularies (the boundary check below is unchanged); the page is the validator. WaveCycleProducerRecordsTest pins this with the bytes the real producer published.
 */
final class WaveCycleRelay extends TaRelay {
    static final String WAVES_EVENT = "wave-cycle-waves";
    static final String CYCLES_EVENT = "wave-cycle-cycles";
    /** The waves part of the hello is held under 384 KiB of retained records. */
    static final long HELLO_PART_BYTES = 384L * 1024;
    /**
     * A pair of records per bar of every timeframe: 15 + 3 + 1 pairs per quarter hour, so 120 records cover about an hour and
     * therefore at least one 15m record and several of the faster timeframes after a restart.
     */
    static final int RECOVERY_TAIL_RECORDS = 120;

    private static final String WAVE_SCHEMA = "com.optionsedge.contracts.wavecycle.WaveSet";
    private static final String CYCLE_SCHEMA = "com.optionsedge.contracts.wavecycle.CycleSet";
    private static final String[] TF_ENUM = {"M1", "M5", "M15"};
    private static final int SLOTS = 2 * TF.length;

    /** Per (timeframe, kind) slot {@code t * 2 + kind}: the rendered record, its size and its ordering pair. */
    private static final class Store {
        final String[] json = new String[SLOTS];
        final int[] bytes = new int[SLOTS];
        final int[] epoch = new int[SLOTS];
        final long[] seq = new long[SLOTS];
        long version;

        Store copy() {
            Store c = new Store();
            System.arraycopy(json, 0, c.json, 0, SLOTS);
            System.arraycopy(bytes, 0, c.bytes, 0, SLOTS);
            System.arraycopy(epoch, 0, c.epoch, 0, SLOTS);
            System.arraycopy(seq, 0, c.seq, 0, SLOTS);
            c.version = version;
            return c;
        }

        /** Whether (epoch, seq) comes strictly after the pair held in {@code slot} (a held slot compared; an empty one is always older). */
        boolean after(int slot, int e, long s) {
            return json[slot] == null || e > epoch[slot] || e == epoch[slot] && s > seq[slot];
        }

        void put(int slot, String frame, int size, int e, long s) {
            json[slot] = frame;
            bytes[slot] = size;
            epoch[slot] = e;
            seq[slot] = s;
            version++;
        }
    }

    private Store live = new Store();
    private Store staged;
    private String helloCache;
    private long helloVersion = -1L;
    private long helloCap = -1L;

    WaveCycleRelay() {
        super("wave_cycle");
    }

    @Override
    boolean handles(String bindingEvent) {
        return WAVES_EVENT.equals(bindingEvent) || CYCLES_EVENT.equals(bindingEvent);
    }

    @Override
    synchronized String[] onLive(String bindingEvent, Object value) {
        return apply(live, bindingEvent, value);
    }

    @Override
    synchronized void beginRecovery() {
        staged = live.copy();
    }

    @Override
    synchronized void onStaged(String bindingEvent, Object value) {
        apply(staged, bindingEvent, value);
    }

    @Override
    synchronized List<String[]> promote() {
        // Only a record AFTER live promotes (an equal one never does): staged is a copy of live plus the history below the captured end offsets,
        // so on an equal ordering key the live record is the later one in log order and stays.
        List<String[]> frames = new ArrayList<>(SLOTS);
        for (int slot = 0; staged != null && slot < SLOTS; slot++) {
            if (staged.json[slot] != null && live.after(slot, staged.epoch[slot], staged.seq[slot])) {
                live.put(slot, staged.json[slot], staged.bytes[slot], staged.epoch[slot], staged.seq[slot]);
                frames.add(new String[] {slot % 2 == 0 ? WAVES_EVENT : CYCLES_EVENT, staged.json[slot]});
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

    private static String renderHello(Store st, long cap) {
        StringBuilder sb = new StringBuilder(",\"waves\":{");
        long used = 0;
        boolean fits = true;
        for (int t = 0; t < TF.length; t++) {
            sb.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":");
            int w = t * 2, c = w + 1;
            long size = (st.json[w] == null ? 0 : st.bytes[w] + 1) + (st.json[c] == null ? 0 : st.bytes[c] + 1);
            boolean held = st.json[w] != null || st.json[c] != null;
            if (fits && held && used + size <= cap) {
                used += size;
                sb.append("{\"waves\":").append(st.json[w] == null ? "null" : st.json[w]).append(",\"cycles\":")
                        .append(st.json[c] == null ? "null" : st.json[c]).append('}');
            } else {
                fits &= !held;   // the first retained timeframe that does not fit ends the list: the rest are null
                sb.append("null");
            }
        }
        return sb.append('}').toString();
    }

    private String[] apply(Store target, String bindingEvent, Object value) {
        GenericRecord r = recordOrDrop(value);
        if (r == null) {
            return null;
        }
        try {
            boolean waves = WAVES_EVENT.equals(bindingEvent);
            GenericRecord env = (GenericRecord) r.get("env");
            int t = (waves ? WAVE_SCHEMA : CYCLE_SCHEMA).equals(r.getSchema().getFullName()) && isBoundary(env)
                    ? indexOf(TF_ENUM, String.valueOf(env.get("timeframe"))) : -1;
            if (t < 0) {
                onDrop();
                return null;
            }
            int slot = t * 2 + (waves ? 0 : 1);
            int epoch = (Integer) env.get("epoch");
            long seq = (Long) env.get("seq");
            if (!target.after(slot, epoch, seq) && (epoch != target.epoch[slot] || seq != target.seq[slot])) {
                regressed();
                return null;
            }
            String frame = AvroFrameJson.render(r, FRAME_MAX_BYTES, false);
            int size = frame == null ? Integer.MAX_VALUE : StudiesRelay.utf8Length(frame);
            if (size > FRAME_MAX_BYTES) {
                onDrop();
                return null;
            }
            target.put(slot, frame, size, epoch, seq);
            accepted();
            return new String[] {waves ? WAVES_EVENT : CYCLES_EVENT, frame};
        } catch (RuntimeException e) {
            onDrop();   // a missing or mistyped field is refused, never repaired
            return null;
        }
    }

    /** The envelope the gateway checks: schema 1, service WAVE_CYCLE, symbol SPX, a state of OK or UNAVAILABLE. */
    private static boolean isBoundary(GenericRecord env) {
        String state = String.valueOf(env.get("state"));
        return Integer.valueOf(1).equals(env.get("schemaVersion")) && "WAVE_CYCLE".equals(String.valueOf(env.get("service")))
                && "SPX".equals(String.valueOf(env.get("symbol"))) && ("OK".equals(state) || "UNAVAILABLE".equals(state));
    }
}
