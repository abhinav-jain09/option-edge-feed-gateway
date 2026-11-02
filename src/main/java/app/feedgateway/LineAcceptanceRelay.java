package app.feedgateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.generic.GenericRecord;

/**
 * VWAP-ACCEPTANCE-REJECTION P5 (options-edge-processing docs, design 10.1): the line / zone acceptance-rejection records of line-acceptance-service. Three topics, three binding events, one relay:
 * <ul>
 * <li>{@code options.spx.line-acceptance} ({@link #EVENT}): the record TYPES {@code LineEpisodeCore}, {@code LineEpisodeEvidence}, {@code LineEpisodeOutcome}, {@code LineEpisodeIntegrity},
 * {@code LeanEvent} and {@code SessionCoverage}, told apart by the WRITER schema's full name (never by the key). The four episode parts and the latest lean are MERGED here into one view per
 * episode, {@code {timeframe, sessionDate, episodeId, core, evidence, outcome, integrity, lean}}, and that view is the {@link #EPISODE_FRAME} frame (an update of any part re-sends the view); a
 * coverage record is its own {@link #COVERAGE_FRAME} frame. An episode whose core has not arrived is held and not relayed.</li>
 * <li>{@code options.spx.line-acceptance.monitor} ({@link #MONITOR_EVENT}): the live {@code LineMonitorStatus} of each line, latest per line, never calibrated.</li>
 * <li>{@code options.spx.line-acceptance.status} ({@link #STATUS_EVENT}): the {@code CalibrationStatus} artifact, latest version per cell and hash, forwarded for the page to join by {@code cellRef}.</li>
 * </ul>
 * The gateway forwards FIELDS (the rendering of the writer schema, {@link AvroFrameJson}), never strings that could say "likely"; the page builds every sentence. Boundary checks only: record type,
 * schema version 1, timeframe M1/M5/M15, the required enums not {@code UNKNOWN}, a {@code yyyy-mm-dd} session date, an episode id, the frame size. A record that fails is a counted drop; nothing is
 * repaired. Monotonicity (design 9.3b): {@code publishSeq} strictly increasing per timeframe (a lower one is a counted regression), {@code asOfMs} non-decreasing per (episode, part), a part version
 * only ever rises (an identical re-delivery is idempotent, a different record for the same part and version is a counted conflict). Retention is per timeframe, of the greatest session date seen (an
 * earlier one is {@code late}, a later one clears the timeframe), at most {@link #EPISODES_PER_TF} episodes (the next one evicts the oldest). The hello part carries whole timeframes while they fit,
 * trimming the OLDEST episodes of a timeframe that does not (and says how many in {@code omitted}), {@code null} for a timeframe that holds nothing or does not fit at all.
 */
final class LineAcceptanceRelay extends TaRelay {
    static final String EVENT = "line-acceptance";
    static final String MONITOR_EVENT = "line-acceptance-monitor";
    static final String STATUS_EVENT = "line-acceptance-status";
    static final String EPISODE_FRAME = "line-acceptance";
    static final String COVERAGE_FRAME = "line-acceptance-coverage";
    static final String MONITOR_FRAME = "line-acceptance-monitor";
    static final String STATUS_FRAME = "line-acceptance-status";
    /** The lineAcceptance part of the hello is held under 512 KiB of retained records. */
    static final long HELLO_PART_BYTES = 512L * 1024;
    /** Episodes retained per timeframe. */
    static final int EPISODES_PER_TF = 200;
    static final int MONITORS_PER_TF = 64;
    static final int STATUS_CELLS = 256;
    /** A session of 3 timeframes x up to 200 episodes x (core + 2 evidence + 2 outcome + integrity + lean) plus coverage and monitors, and the interleaved remainder. */
    static final int RECOVERY_TAIL_RECORDS = 4000;

    private static final String NS = "com.optionsedge.contracts.lineacc.";
    private static final String[] TF_ENUM = {"M1", "M5", "M15"};
    private static final String UNKNOWN = "UNKNOWN";
    private static final int CORE = 0, EVIDENCE = 1, OUTCOME = 2, INTEGRITY = 3, LEAN = 4, COVERAGE = 5, MONITOR = 6, STATUS = 7;
    private static final String[] RECORD_TYPES = {"EPISODE_CORE", "EPISODE_EVIDENCE", "EPISODE_OUTCOME", "EPISODE_INTEGRITY", "LEAN_EVENT", "SESSION_COVERAGE", "MONITOR_STATUS", null};
    private static final String[] NAMES = {"LineEpisodeCore", "LineEpisodeEvidence", "LineEpisodeOutcome", "LineEpisodeIntegrity", "LeanEvent", "SessionCoverage", "LineMonitorStatus", "CalibrationStatus"};

    /** One retained record: its frame data, its UTF-8 size, its version (part version / lean ordinal / coverage version) and its asOfMs. */
    private record Held(String json, int bytes, long version, long asOf) {
    }

    /** The parts of one episode. */
    private static final class Ep {
        final Held[] part = new Held[5];

        Ep copy() {
            Ep c = new Ep();
            System.arraycopy(part, 0, c.part, 0, part.length);
            return c;
        }

        long bytes() {
            long sum = 0;
            for (Held h : part) {
                sum += h == null ? 0 : h.bytes();
            }
            return sum;
        }

        String view(String tf, String date, String id) {
            StringBuilder sb = new StringBuilder("{\"timeframe\":\"").append(tf).append("\",\"sessionDate\":\"").append(date).append("\",\"episodeId\":");
            sb.append(jsonString(id));
            String[] keys = {"core", "evidence", "outcome", "integrity", "lean"};
            for (int i = 0; i < keys.length; i++) {
                sb.append(",\"").append(keys[i]).append("\":").append(part[i] == null ? "null" : part[i].json());
            }
            return sb.append('}').toString();
        }
    }

    @SuppressWarnings("unchecked")
    private static final class Store {
        final String[] session = new String[TF.length];
        final Map<String, Ep>[] episodes = new Map[TF.length];
        final Held[] coverage = new Held[TF.length];
        final long[] lastSeq = new long[TF.length];
        final Map<String, Held>[] monitors = new Map[TF.length];
        final Map<String, Held> status = new LinkedHashMap<>();
        long version;

        Store() {
            for (int t = 0; t < TF.length; t++) {
                episodes[t] = new LinkedHashMap<>();
                monitors[t] = new LinkedHashMap<>();
                lastSeq[t] = -1L;
            }
        }

        Store copy() {
            Store c = new Store();
            for (int t = 0; t < TF.length; t++) {
                for (Map.Entry<String, Ep> e : episodes[t].entrySet()) {
                    c.episodes[t].put(e.getKey(), e.getValue().copy());
                }
                c.monitors[t].putAll(monitors[t]);
            }
            System.arraycopy(session, 0, c.session, 0, session.length);
            System.arraycopy(coverage, 0, c.coverage, 0, coverage.length);
            c.status.putAll(status);
            c.version = version;
            return c;   // the publishSeq trackers start empty: history is replayed in log order into a copy that is merged, not compared
        }
    }

    private final AtomicLong late = new AtomicLong();
    private final AtomicLong conflict = new AtomicLong();
    private final AtomicLong duplicate = new AtomicLong();
    private final AtomicLong evicted = new AtomicLong();
    private Store live = new Store();
    private Store staged;
    private String helloCache;
    private long helloVersion = -1L;
    private long helloCap = -1L;

    LineAcceptanceRelay() {
        super("line_acceptance");
    }

    @Override
    boolean handles(String bindingEvent) {
        return EVENT.equals(bindingEvent) || MONITOR_EVENT.equals(bindingEvent) || STATUS_EVENT.equals(bindingEvent);
    }

    @Override
    synchronized String[] onLive(String bindingEvent, Object value) {
        return apply(live, bindingEvent, value, false);
    }

    @Override
    synchronized void beginRecovery() {
        staged = live.copy();
    }

    @Override
    synchronized void onStaged(String bindingEvent, Object value) {
        apply(staged, bindingEvent, value, true);
    }

    /**
     * Staged is a copy of live plus the history below the captured end offsets, replayed in log order with the part-version rules, so each part of each episode in it is the greatest version
     * seen anywhere. The promotion merges it into live part by part (the greater version wins, an equal one keeps live) and returns the frames live gained, in episode order.
     */
    @Override
    synchronized List<String[]> promote() {
        List<String[]> frames = new ArrayList<>();
        for (int t = 0; staged != null && t < TF.length; t++) {
            String s = staged.session[t];
            int order = s == null ? -1 : live.session[t] == null ? 1 : s.compareTo(live.session[t]);
            if (order > 0) {
                live.episodes[t].clear();
                live.coverage[t] = null;
                live.monitors[t].clear();
                live.session[t] = s;
            }
            if (order >= 0) {
                for (Map.Entry<String, Ep> e : staged.episodes[t].entrySet()) {
                    Ep mine = live.episodes[t].get(e.getKey());
                    Ep merged = mine == null ? e.getValue().copy() : mine.copy();
                    boolean changed = mine == null;
                    for (int p = 0; mine != null && p < 5; p++) {
                        Held a = mine.part[p], b = e.getValue().part[p];
                        if (b != null && (a == null || b.version() > a.version())) {
                            merged.part[p] = b;
                            changed = true;
                        }
                    }
                    if (changed) {
                        live.episodes[t].remove(e.getKey());
                        live.episodes[t].put(e.getKey(), merged);
                        if (merged.part[CORE] != null) {
                            frames.add(new String[] {EPISODE_FRAME, merged.view(TF[t], live.session[t], e.getKey())});
                        }
                    }
                }
                while (live.episodes[t].size() > EPISODES_PER_TF) {
                    live.episodes[t].remove(live.episodes[t].keySet().iterator().next());
                    evicted.incrementAndGet();
                }
                Held sc = staged.coverage[t];
                if (sc != null && (live.coverage[t] == null || sc.version() > live.coverage[t].version())) {
                    live.coverage[t] = sc;
                    frames.add(new String[] {COVERAGE_FRAME, sc.json()});
                }
                for (Map.Entry<String, Held> e : staged.monitors[t].entrySet()) {
                    Held a = live.monitors[t].get(e.getKey());
                    if ((a == null || e.getValue().asOf() > a.asOf()) && live.monitors[t].size() < MONITORS_PER_TF + (a == null ? 0 : 1)) {
                        live.monitors[t].put(e.getKey(), e.getValue());
                        frames.add(new String[] {MONITOR_FRAME, e.getValue().json()});
                    }
                }
                live.lastSeq[t] = Math.max(order > 0 ? -1L : live.lastSeq[t], staged.lastSeq[t]);   // the monotonicity guard survives the recovery
            }
        }
        if (staged != null) {
            for (Map.Entry<String, Held> e : staged.status.entrySet()) {
                Held a = live.status.get(e.getKey());
                if (a == null || e.getValue().version() > a.version()) {
                    live.status.remove(e.getKey());
                    live.status.put(e.getKey(), e.getValue());
                    frames.add(new String[] {STATUS_FRAME, e.getValue().json()});
                }
            }
            while (live.status.size() > STATUS_CELLS) {
                live.status.remove(live.status.keySet().iterator().next());
            }
        }
        if (!frames.isEmpty()) {
            live.version++;
        }
        staged = null;
        return frames;
    }

    @Override
    int tailRecords(String bindingEvent) {
        return EVENT.equals(bindingEvent) ? RECOVERY_TAIL_RECORDS : 800;
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

    /**
     * The status cells first (the page needs them to word every verdict; never more than an eighth of the part), then whole timeframes while they fit. Every byte is measured on the text that is
     * actually rendered. A timeframe that does not fit loses its OLDEST episodes (and says how many in {@code omitted}); one that cannot fit even with none is {@code null}, and so are the later ones.
     * A timeframe's session date is the greater of its episode and monitor dates; records of an older date are not rendered under it.
     */
    private static String renderHello(Store st, long cap) {
        long used = 0;
        List<String> cells = new ArrayList<>();
        long skeleton = StudiesRelay.utf8Length(",\"lineAcceptance\":{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":[]}");
        for (Held h : st.status.values()) {
            long add = h.bytes() + (cells.isEmpty() ? 0 : 1);   // a comma only between cells
            if (used + add > cap / 8 || skeleton + used + add > cap) {
                break;
            }
            used += add;
            cells.add(h.json());
        }
        used += StudiesRelay.utf8Length(",\"lineAcceptance\":{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":[]}");   // the empty field, exactly; a part replaces its "null"
        StringBuilder sb = new StringBuilder(",\"lineAcceptance\":{");
        boolean fits = true;
        for (int t = 0; t < TF.length; t++) {
            sb.append('"').append(TF[t]).append("\":");
            String d = st.session[t];
            boolean eps = d != null, mons = d != null;
            boolean held = d != null && (eps && (!st.episodes[t].isEmpty() || st.coverage[t] != null) || mons && !st.monitors[t].isEmpty());
            String part = null;
            if (fits && held) {
                List<String> keys = new ArrayList<>();
                if (eps) {
                    for (Map.Entry<String, Ep> e : st.episodes[t].entrySet()) {
                        if (e.getValue().part[CORE] != null) {
                            keys.add(e.getKey());
                        }
                    }
                }
                int lo = 0, hi = keys.size();   // keep the newest `keep` episodes: the largest keep whose rendering fits
                while (lo < hi) {
                    int mid = (lo + hi + 1) >>> 1;
                    if (used + StudiesRelay.utf8Length(tfPart(st, t, d, eps, mons, keys, keys.size() - mid)) - 4 <= cap) {
                        lo = mid;
                    } else {
                        hi = mid - 1;
                    }
                }
                String candidate = tfPart(st, t, d, eps, mons, keys, keys.size() - lo);
                if (used + StudiesRelay.utf8Length(candidate) - 4 <= cap) {
                    part = candidate;
                }
            }
            if (part == null) {
                fits &= !held;   // the first retained timeframe that does not fit ends the list: the rest are null
                sb.append("null,");
            } else {
                used += StudiesRelay.utf8Length(part) - 4;
                sb.append(part).append(',');
            }
        }
        return sb.append("\"status\":[").append(String.join(",", cells)).append("]}").toString();
    }

    /** One timeframe's object, with the newest episodes after dropping the first {@code drop} of {@code keys}. */
    private static String tfPart(Store st, int t, String date, boolean eps, boolean mons, List<String> keys, int drop) {
        StringBuilder sb = new StringBuilder("{\"sessionDate\":\"").append(date).append("\",\"episodes\":[");
        for (int i = drop; i < keys.size(); i++) {
            sb.append(i == drop ? "" : ",").append(st.episodes[t].get(keys.get(i)).view(TF[t], date, keys.get(i)));
        }
        sb.append("],\"omitted\":").append(drop).append(",\"coverage\":").append(eps && st.coverage[t] != null ? st.coverage[t].json() : "null").append(",\"monitors\":[");
        boolean first = true;
        for (Held m : mons ? st.monitors[t].values() : List.<Held>of()) {
            sb.append(first ? "" : ",").append(m.json());
            first = false;
        }
        return sb.append("]}").toString();
    }

    private String[] apply(Store target, String bindingEvent, Object value, boolean staging) {
        GenericRecord r = recordOrDrop(value);
        if (r == null) {
            return null;
        }
        try {
            String schema = r.getSchema().getFullName();
            int kind = -1;
            for (int i = 0; i < NAMES.length; i++) {
                if (schema.equals(NS + NAMES[i])) {
                    kind = i;
                }
            }
            boolean topicOk = kind >= 0 && (kind == STATUS ? STATUS_EVENT.equals(bindingEvent) : kind == MONITOR ? MONITOR_EVENT.equals(bindingEvent) : EVENT.equals(bindingEvent));
            if (!topicOk || !Integer.valueOf(1).equals(r.get("schemaVersion")) || RECORD_TYPES[kind] != null && !RECORD_TYPES[kind].equals(String.valueOf(r.get("recordType")))) {
                onDrop();
                return null;
            }
            String frame = AvroFrameJson.render(r, FRAME_MAX_BYTES, false);
            int size = frame == null ? Integer.MAX_VALUE : StudiesRelay.utf8Length(frame);
            if (size > FRAME_MAX_BYTES) {
                onDrop();
                return null;
            }
            if (kind == STATUS) {
                return status(target, r, new Held(frame, size, ((Number) r.get("statusVersion")).longValue(), ((Number) r.get("effectiveAtMs")).longValue()), staging);
            }
            int t = indexOf(TF_ENUM, String.valueOf(r.get("timeframe")));
            String date = String.valueOf(r.get("sessionDate"));
            if (t < 0 || !isDate(date) || !known(r.get("sessionSegment"))) {
                onDrop();
                return null;
            }
            long publishSeq = ((Number) r.get("publishSeq")).longValue(), asOf = ((Number) r.get("asOfMs")).longValue();
            if (kind == MONITOR) {
                return monitor(target, r, t, date, publishSeq, new Held(frame, size, 0L, asOf), staging);
            }
            return episodeRecord(target, r, kind, t, date, publishSeq, asOf, frame, size, staging);
        } catch (RuntimeException e) {
            onDrop();   // a missing or mistyped field is refused, never repaired
            return null;
        }
    }

    private String[] episodeRecord(Store st, GenericRecord r, int kind, int t, String date, long publishSeq, long asOf, String frame, int size, boolean staging) {
        // the session rule first: an earlier session is late and changes nothing, a later one clears the timeframe
        int order = st.session[t] == null ? 1 : date.compareTo(st.session[t]);
        if (order < 0) {
            late.incrementAndGet();
            return null;
        }
        if (kind == COVERAGE) {
            long cv = ((Number) r.get("coverageVersion")).longValue();
            Held old = order > 0 ? null : st.coverage[t];
            if (old != null && cv <= old.version()) {
                return same(old, frame, cv);
            }
            if (!freshSeq(st, t, publishSeq, order, staging)) {
                return null;
            }
            enter(st, t, date, order);
            seen(st, t, publishSeq, staging);
            st.coverage[t] = new Held(frame, size, cv, asOf);
            st.version++;
            accepted();
            return new String[] {COVERAGE_FRAME, frame};
        }
        String id = String.valueOf(r.get("episodeId"));
        if (id.isEmpty() || id.length() > 256) {
            onDrop();
            return null;
        }
        long version;
        if (kind == CORE) {
            GenericRecord line = (GenericRecord) r.get("line");
            if (!known(r.get("s0Verdict")) || !known(line.get("kind")) || !known(line.get("lineClass")) || !known(r.get("approachDirection")) || !known(r.get("vwapSource"))) {
                onDrop();
                return null;
            }
            version = 0L;
        } else if (kind == EVIDENCE) {
            version = ((Number) r.get("evidenceVersion")).longValue();
        } else if (kind == OUTCOME) {
            if (!known(r.get("s1Outcome"))) {
                onDrop();
                return null;
            }
            version = ((Number) r.get("s1Version")).longValue();
        } else if (kind == INTEGRITY) {
            version = ((Number) r.get("integrityVersion")).longValue();
        } else {
            if (!known(r.get("lean"))) {
                onDrop();
                return null;
            }
            version = ((Number) r.get("leanBarOrdinal")).longValue();
        }
        Ep ep = order > 0 ? null : st.episodes[t].get(id);
        Held old = ep == null ? null : ep.part[kind];
        if (old != null) {
            if (version == old.version()) {
                if (!old.json().equals(frame)) {
                    if (!staging) {
                        conflict.incrementAndGet();
                    }
                    return null;
                }
                return same(old, frame, version);
            }
            if (version < old.version()) {
                if (!staging) {
                    regressed();
                }
                return null;
            }
            if (asOf < old.asOf()) {
                if (!staging) {
                    regressed();
                }
                return null;
            }
        }
        if (!freshSeq(st, t, publishSeq, order, staging)) {
            return null;
        }
        enter(st, t, date, order);
        Map<String, Ep> eps = st.episodes[t];
        if (ep == null) {
            if (eps.size() >= EPISODES_PER_TF) {
                eps.remove(eps.keySet().iterator().next());
                evicted.incrementAndGet();
            }
            ep = new Ep();
            eps.put(id, ep);
        }
        seen(st, t, publishSeq, staging);
        ep.part[kind] = new Held(frame, size, version, asOf);
        st.version++;
        accepted();
        return ep.part[CORE] == null ? null : new String[] {EPISODE_FRAME, ep.view(TF[t], date, id)};
    }

    /** An idempotent re-delivery of the held record: not an error, not a frame. */
    private String[] same(Held old, String frame, long version) {
        if (old.json().equals(frame)) {
            duplicate.incrementAndGet();
            return null;
        }
        conflict.incrementAndGet();
        return null;
    }

    /** {@code publishSeq} strictly increasing per timeframe in the live path (an equal one is a re-delivery, judged by the part rules); a staged replay is in log order and is merged, not compared. */
    private boolean freshSeq(Store st, int t, long publishSeq, int order, boolean staging) {
        if (staging) {
            return true;
        }
        if (order <= 0 && publishSeq <= st.lastSeq[t]) {
            regressed();
            return false;
        }
        return true;
    }

    private static void seen(Store st, int t, long publishSeq, boolean staging) {
        st.lastSeq[t] = Math.max(st.lastSeq[t], publishSeq);   // a staged replay records it too: promote() folds it into the live watermark
    }

    private void enter(Store st, int t, String date, int order) {
        if (order > 0) {
            st.episodes[t].clear();
            st.coverage[t] = null;
            st.monitors[t].clear();
            st.lastSeq[t] = -1L;
            st.session[t] = date;
        }
    }

    private String[] monitor(Store st, GenericRecord r, int t, String date, long publishSeq, Held held, boolean staging) {
        if (!known(r.get("monitorState"))) {
            onDrop();
            return null;
        }
        String lineId = String.valueOf(r.get("lineId"));
        int order = st.session[t] == null ? 1 : date.compareTo(st.session[t]);
        if (order < 0) {
            late.incrementAndGet();
            return null;
        }
        Held same = order == 0 ? st.monitors[t].get(lineId) : null;
        if (same != null && same.json().equals(held.json())) {
            duplicate.incrementAndGet();
            return null;
        }
        if (!freshSeq(st, t, publishSeq, order, staging)) {
            return null;
        }
        enter(st, t, date, order);
        Held old = st.monitors[t].get(lineId);
        if (old != null && held.asOf() < old.asOf()) {
            if (!staging) {
                regressed();
            }
            return null;
        }
        if (old == null && st.monitors[t].size() >= MONITORS_PER_TF) {
            st.monitors[t].remove(st.monitors[t].keySet().iterator().next());
            evicted.incrementAndGet();
        }
        st.monitors[t].remove(lineId);
        st.monitors[t].put(lineId, held);
        seen(st, t, publishSeq, staging);
        st.version++;
        accepted();
        return new String[] {MONITOR_FRAME, held.json()};
    }

    private String[] status(Store st, GenericRecord r, Held held, boolean staging) {
        if (!known(r.get("state"))) {
            onDrop();
            return null;
        }
        String key = r.get("cellId") + "|" + r.get("parameterSetHash");
        Held old = st.status.get(key);
        if (old != null && held.version() <= old.version()) {
            return held.version() == old.version() ? same(old, held.json(), held.version()) : staleStatus(staging);
        }
        if (old == null && st.status.size() >= STATUS_CELLS) {
            st.status.remove(st.status.keySet().iterator().next());
            evicted.incrementAndGet();
        }
        st.status.remove(key);
        st.status.put(key, held);
        st.version++;
        accepted();
        return new String[] {STATUS_FRAME, held.json()};
    }

    private String[] staleStatus(boolean staging) {
        if (!staging) {
            regressed();
        }
        return null;
    }

    @Override
    String metrics() {
        return super.metrics() + counter("late_total", "Records refused because their sessionDate is earlier than the retained one of their timeframe.", late)
                + counter("conflict_total", "Records refused because a different record is already retained for the same episode, part and version.", conflict)
                + counter("duplicate_total", "Identical re-deliveries of a retained record (idempotent, not forwarded).", duplicate)
                + counter("evicted_total", "Retained episodes, monitors or status cells evicted by the next one over the cap.", evicted);
    }

    private static String counter(String name, String help, AtomicLong value) {
        String metric = "gateway_line_acceptance_" + name;
        return "# HELP " + metric + " " + help + "\n# TYPE " + metric + " counter\n" + metric + " " + value.get() + "\n";
    }

    private static boolean known(Object symbol) {
        return !UNKNOWN.equals(String.valueOf(symbol));
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

    /** A JSON string literal of an episode id (ids are ASCII by contract; anything else is escaped, never trusted). */
    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7e) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Test seam. */
    synchronized int episodesForTest(String tf) {
        return live.episodes[indexOf(TF, tf)].size();
    }
}
