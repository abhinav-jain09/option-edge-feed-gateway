package app.feedgateway;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.generic.GenericRecord;

/**
 * BREAKOUT-TRAP-VS-REAL (Phase F) break classification (ta-books-catalog 17_BREAK_CLASSIFICATION_FRAME_CONTRACT.md): the {@code BreakMark}, {@code BreakOutcome} and {@code BreakStatus}
 * of break-classifier-service share ONE topic and are told apart by the WRITER schema's full name, never by the key. The gateway checks the boundary only (record type, schema version 1,
 * the required enums not {@code UNKNOWN}, a mark's timeframe in M1/M5/M15, 64 lower-case hex ids and hashes, a mark's {@code yyyy-mm-dd} session date, size under the frame guard); a record that
 * fails is a counted drop and nothing is repaired. The page is the full validator.
 * <p>
 * Marks are retained per timeframe keyed by {@code classificationId} (a re-delivery replaces and keeps its place), only of the greatest {@code sessionDate} seen for that timeframe (an earlier one
 * is {@code late}, a later one clears the timeframe) and at most {@link #MARKS_PER_TF}: the 65th evicts the first arrival with its outcome. An outcome is relayed only for a retained mark (else
 * {@code orphan}), one per id (an identical re-delivery is idempotent, a different one is a {@code conflict}). The status is the latest by arrival. The hello part holds whole timeframes in the
 * order 1m, 5m, 15m while they fit, {@code null} for the rest; the status is accounted first.
 */
final class BreakClassificationRelay extends TaRelay {
    /** The binding event of the one topic. */
    static final String EVENT = "break-classification";
    static final String MARK_FRAME = "break-classification-mark";
    static final String OUTCOME_FRAME = "break-classification-outcome";
    static final String STATUS_FRAME = "break-classification-status";
    /** The breaks part of the hello is held under 256 KiB of retained records. */
    static final long HELLO_PART_BYTES = 256L * 1024;
    /** Marks (and so outcomes) retained per timeframe. */
    static final int MARKS_PER_TF = 64;
    /** Three timeframes x (64 marks + 64 outcomes) + the status = 385 records, and the interleaved remainder. */
    static final int RECOVERY_TAIL_RECORDS = 600;

    private static final String MARK = "com.optionsedge.contracts.breakclass.BreakMark";
    private static final String OUTCOME = "com.optionsedge.contracts.breakclass.BreakOutcome";
    private static final String STATUS = "com.optionsedge.contracts.breakclass.BreakStatus";
    private static final String[] TF_ENUM = {"M1", "M5", "M15"};
    private static final String UNKNOWN = "UNKNOWN";

    /** One retained record: the frame data and its UTF-8 size. */
    private record Held(String json, int bytes) {
    }

    @SuppressWarnings("unchecked")
    private static final class Store {
        final Map<String, Held>[] marks = new Map[TF.length];
        final Map<String, Held>[] outcomes = new Map[TF.length];
        final String[] session = new String[TF.length];
        Held status;
        long version;

        Store() {
            for (int t = 0; t < TF.length; t++) {
                marks[t] = new LinkedHashMap<>();
                outcomes[t] = new LinkedHashMap<>();
            }
        }

        Store copy() {
            Store c = new Store();
            for (int t = 0; t < TF.length; t++) {
                c.marks[t].putAll(marks[t]);
                c.outcomes[t].putAll(outcomes[t]);
            }
            System.arraycopy(session, 0, c.session, 0, session.length);
            c.status = status;
            c.version = version;
            return c;
        }
    }

    private final AtomicLong late = new AtomicLong();
    private final AtomicLong orphan = new AtomicLong();
    private final AtomicLong conflict = new AtomicLong();
    private final AtomicLong evicted = new AtomicLong();
    private Store live = new Store();
    private Store staged;
    /** The live status when the recovery began: a live status that arrived since is later in log order than anything staged. */
    private Held statusAtBegin;
    /** Live outcomes parked in the staging because their mark is only staged: counted accepted when the promotion adopts them, orphans when it does not. */
    private final Set<String> parked = new HashSet<>();
    private String helloCache;
    private long helloVersion = -1L;
    private long helloCap = -1L;

    BreakClassificationRelay() {
        super("break_classification");
    }

    @Override
    boolean handles(String bindingEvent) {
        return EVENT.equals(bindingEvent);
    }

    @Override
    synchronized String[] onLive(String bindingEvent, Object value) {
        return apply(live, value);
    }

    @Override
    synchronized void beginRecovery() {
        staged = live.copy();
        statusAtBegin = live.status;
        parked.clear();
    }

    @Override
    synchronized void onStaged(String bindingEvent, Object value) {
        apply(staged, value);
    }

    /**
     * Staged is a copy of live plus the history below the captured end offsets, and that history is OLDER in log order than every live record: history only fills the room live leaves (it never
     * evicts a live mark), a greater session in the history replaces a smaller live one, a smaller one is ignored, and a live status or outcome is never replaced. Returns what live gained.
     */
    @Override
    synchronized List<String[]> promote() {
        List<String[]> frames = new ArrayList<>();
        for (int t = 0; staged != null && t < TF.length; t++) {
            String s = staged.session[t];
            int order = s == null ? -1 : live.session[t] == null ? 1 : s.compareTo(live.session[t]);
            if (order < 0) {
                continue;
            }
            if (order > 0) {
                live.marks[t].clear();
                live.outcomes[t].clear();
                live.session[t] = s;
            }
            Map<String, Held> marks = live.marks[t];
            List<Map.Entry<String, Held>> extra = new ArrayList<>();
            for (Map.Entry<String, Held> e : staged.marks[t].entrySet()) {
                if (!marks.containsKey(e.getKey())) {
                    extra.add(e);
                }
            }
            int skip = Math.max(0, extra.size() - (MARKS_PER_TF - marks.size()));
            if (skip < extra.size()) {
                Map<String, Held> merged = new LinkedHashMap<>();
                for (Map.Entry<String, Held> e : extra.subList(skip, extra.size())) {
                    merged.put(e.getKey(), e.getValue());
                    frames.add(new String[] {MARK_FRAME, e.getValue().json()});
                }
                merged.putAll(marks);
                live.marks[t] = merged;
            }
            for (Map.Entry<String, Held> e : staged.outcomes[t].entrySet()) {
                if (live.marks[t].containsKey(e.getKey()) && live.outcomes[t].putIfAbsent(e.getKey(), e.getValue()) == null) {
                    frames.add(new String[] {OUTCOME_FRAME, e.getValue().json()});
                    if (parked.remove(e.getKey())) {
                        accepted();
                    }
                }
            }
        }
        if (staged != null && staged.status != statusAtBegin && live.status == statusAtBegin) {
            live.status = staged.status;
            frames.add(new String[] {STATUS_FRAME, staged.status.json()});
        }
        if (!frames.isEmpty()) {
            live.version++;
        }
        orphan.addAndGet(parked.size());   // a parked outcome whose mark the promotion did not adopt (no room, or a newer live session): its mark is not retained
        parked.clear();
        staged = null;
        statusAtBegin = null;
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

    /** The status first (a status that alone exceeds the cap is {@code null}: no real record approaches it), then whole timeframes while they fit. */
    private static String renderHello(Store st, long cap) {
        Held status = st.status != null && st.status.bytes() + 1 <= cap ? st.status : null;
        long used = status == null ? 0 : status.bytes() + 1;
        boolean fits = true;
        StringBuilder sb = new StringBuilder(",\"breaks\":{");
        for (int t = 0; t < TF.length; t++) {
            sb.append('"').append(TF[t]).append("\":");
            boolean held = st.session[t] != null;
            long size = held ? bytes(st.marks[t]) + bytes(st.outcomes[t]) : 0;
            if (fits && held && used + size <= cap) {
                used += size;
                sb.append("{\"marks\":");
                join(sb, st.marks[t]).append(",\"outcomes\":");
                join(sb, st.outcomes[t]).append("},");
            } else {
                fits &= !held;   // the first retained timeframe that does not fit ends the list: the rest are null
                sb.append("null,");
            }
        }
        return sb.append("\"status\":").append(status == null ? "null" : status.json()).append('}').toString();
    }

    private static long bytes(Map<String, Held> records) {
        long sum = 0;
        for (Held h : records.values()) {
            sum += h.bytes() + 1;
        }
        return sum;
    }

    private static StringBuilder join(StringBuilder sb, Map<String, Held> records) {
        sb.append('[');
        boolean first = true;
        for (Held h : records.values()) {
            sb.append(first ? "" : ",").append(h.json());
            first = false;
        }
        return sb.append(']');
    }

    private String[] apply(Store target, Object value) {
        GenericRecord r = recordOrDrop(value);
        if (r == null) {
            return null;
        }
        try {
            String schema = r.getSchema().getFullName();
            int kind = MARK.equals(schema) ? 0 : OUTCOME.equals(schema) ? 1 : STATUS.equals(schema) ? 2 : -1;
            String frame = kind >= 0 && Integer.valueOf(1).equals(r.get("schemaVersion")) && hex(r.get("parameterSetHash")) ? AvroFrameJson.render(r, FRAME_MAX_BYTES, false) : null;
            int size = frame == null ? Integer.MAX_VALUE : StudiesRelay.utf8Length(frame);
            if (size > FRAME_MAX_BYTES) {
                onDrop();
                return null;
            }
            Held held = new Held(frame, size);
            return switch (kind) {
                case 0 -> mark(target, r, held);
                case 1 -> outcome(target, r, held);
                default -> status(target, r, held);
            };
        } catch (RuntimeException e) {
            onDrop();   // a missing or mistyped field is refused, never repaired
            return null;
        }
    }

    private String[] mark(Store st, GenericRecord r, Held held) {
        int t = indexOf(TF_ENUM, String.valueOf(r.get("timeframe")));
        String id = String.valueOf(r.get("classificationId")), date = String.valueOf(r.get("sessionDate"));
        if (t < 0 || !hex(id) || !hex(r.get("vendoredKitHash")) || !isDate(date) || !known(r.get("stage")) || !known(r.get("direction")) || !known(r.get("state"))
                || !known(r.get("reason")) || !known(r.get("todBucket")) || !known(((GenericRecord) r.get("calibration")).get("state")) || !featuresKnown(r)) {
            onDrop();
            return null;
        }
        int order = st.session[t] == null ? 1 : date.compareTo(st.session[t]);
        if (order < 0) {
            late.incrementAndGet();
            return null;
        }
        if (order > 0) {
            st.marks[t].clear();
            st.outcomes[t].clear();
            st.session[t] = date;
        }
        Map<String, Held> marks = st.marks[t];
        if (marks.size() >= MARKS_PER_TF && !marks.containsKey(id)) {
            String first = marks.keySet().iterator().next();
            marks.remove(first);
            st.outcomes[t].remove(first);
            evicted.incrementAndGet();
        }
        marks.put(id, held);
        st.version++;
        accepted();
        return new String[] {MARK_FRAME, held.json()};
    }

    private String[] outcome(Store st, GenericRecord r, Held held) {
        String id = String.valueOf(r.get("classificationId"));
        if (!hex(id) || !known(r.get("label"))) {
            onDrop();
            return null;
        }
        for (int t = 0; t < TF.length; t++) {
            if (st.marks[t].containsKey(id)) {
                return keep(st, t, id, held, true);
            }
        }
        for (int t = 0; st == live && staged != null && t < TF.length; t++) {
            if (staged.marks[t].containsKey(id)) {
                return keep(staged, t, id, held, false);   // its mark is still being staged below the captured end: the outcome waits in the staging for the promotion
            }
        }
        orphan.incrementAndGet();
        return null;
    }

    private String[] keep(Store st, int t, String id, Held held, boolean relay) {
        Held old = st.outcomes[t].putIfAbsent(id, held);
        if (old != null && !old.json().equals(held.json())) {
            conflict.incrementAndGet();
            return null;
        }
        st.version++;
        if (relay) {
            accepted();
            return new String[] {OUTCOME_FRAME, held.json()};
        }
        parked.add(id);
        return null;
    }

    private String[] status(Store st, GenericRecord r, Held held) {
        if (!known(r.get("statusKind"))) {
            onDrop();
            return null;
        }
        st.status = held;
        st.version++;
        accepted();
        return new String[] {STATUS_FRAME, held.json()};
    }

    @Override
    String metrics() {
        return super.metrics() + counter("late_total", "Marks refused because their sessionDate is earlier than the retained one of their timeframe.", late)
                + counter("orphan_total", "Outcomes refused because their mark is not retained.", orphan)
                + counter("conflict_total", "Outcomes refused because a different outcome is already retained for the same classificationId.", conflict)
                + counter("evicted_total", "Retained marks (with their outcome) evicted by the 65th mark of a timeframe.", evicted);
    }

    private static String counter(String name, String help, AtomicLong value) {
        String metric = "gateway_break_classification_" + name;
        return "# HELP " + metric + " " + help + "\n# TYPE " + metric + " counter\n" + metric + " " + value.get() + "\n";
    }

    private static boolean known(Object symbol) {
        return !UNKNOWN.equals(String.valueOf(symbol));
    }

    private static boolean featuresKnown(GenericRecord r) {
        for (Object c : (List<?>) r.get("contributions")) {
            if (!known(((GenericRecord) c).get("featureId"))) {
                return false;
            }
        }
        return true;
    }

    /** 64 lower-case hexadecimal characters. */
    private static boolean hex(Object value) {
        if (!(value instanceof CharSequence s) || s.length() != 64) {
            return false;
        }
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    /** The shape {@code yyyy-mm-dd}. */
    private static boolean isDate(String s) {
        if (s.length() != 10) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            char c = s.charAt(i);
            if (i == 4 || i == 7 ? c != '-' : c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
