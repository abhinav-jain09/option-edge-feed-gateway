package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

/**
 * The Phase A studies relay (PHASE-A-INDICATORS-REQUIREMENT.md A-R10.1/A-R10.4; browser contract:
 * ta-books-catalog/03_STUDIES_FRAME_CONTRACT.md). Validates the two Avro record types at the gateway boundary, renders them
 * once to the browser JSON (the Avro field names exactly), keeps a bounded recent history of finalized bars and the latest
 * current state per symbol for the hello, and tells the caller which frames to broadcast. No Kafka types: the service owns the
 * consumer, the recovery staging and the broadcast, this class owns the data and the decisions.
 */
final class StudiesRelay {
    static final String BAR_EVENT = "indicator-studies";
    static final String CURRENT_EVENT = "indicator-studies-current";
    /** Recent finalized bars kept per symbol and timeframe, and carried in the hello. */
    static final int HISTORY_BARS = 8;
    /** The whole studies part of the hello, in UTF-8 bytes of the rendered JSON (producer strings are not assumed ASCII). */
    static final int HELLO_MAX_BYTES = 512 * 1024;
    static final int BAR_MAX_BYTES = 64 * 1024;
    static final int CURRENT_MAX_BYTES = 256 * 1024;
    /**
     * Cheap structural bounds applied BEFORE anything is rendered, so an absurd record costs a size check and not an allocation.
     * Generous multiples of the producer's own limits (56 values, 8 states, 48 events, 22 / 16 divergences, 6 timeframes).
     */
    static final int MAX_VALUES = 128;
    static final int MAX_STATES = 32;
    static final int MAX_EVENTS = 96;
    static final int MAX_DIVERGENCES = 64;
    static final int MAX_TIMEFRAMES = 12;

    private static final String BAR_SCHEMA = "com.optionsedge.contracts.indicators.avro.StudyBar";
    private static final String CURRENT_SCHEMA = "com.optionsedge.contracts.indicators.avro.StudiesCurrent";
    private static final String[] SYMBOLS = {"SPX", "ES"};
    /** Hello field names per symbol: SPX is the chart's index symbol, ES rides the es-prefixed twins. */
    private static final String[] CURRENT_FIELD = {"studies", "esStudies"};
    private static final String[] BARS_FIELD = {"studyBars", "esStudyBars"};
    private static final String[] TF = {"1m", "5m", "15m"};
    private static final String[] TF_ENUM = {"T1M", "T5M", "T15M"};
    private static final long[] TF_MS = {60_000L, 300_000L, 900_000L};
    /** The producer's own bound (StudyWireValidator): a barId is at most 64 characters. */
    private static final int BAR_ID_MAX = 64;

    /** One retained finalized bar: its identity, its clock and the frame data it will be sent as. */
    record Bar(String barId, long startMs, String json, int bytes) { }

    /** The retained state of the stream. Not thread-safe: the relay mutates the live store under its lock. */
    static final class Store {
        @SuppressWarnings("unchecked")
        final ArrayList<Bar>[] bars = new ArrayList[SYMBOLS.length * TF.length];
        /** Per symbol: the latest current state rendered once per timeframe (timeframes reduced to that one key). */
        final String[][] current = new String[SYMBOLS.length][];
        /** UTF-8 sizes of the {@link #current} entries. */
        final int[][] currentBytes = new int[SYMBOLS.length][];
        /** Bumped on every change to the retained state; the hello cache is valid for exactly one version. */
        long version;
        /** Per symbol: the frame data of the latest current state (timeframes reduced to the three forwarded keys). */
        final String[] currentFrame = new String[SYMBOLS.length];
        final long[] currentMs = new long[SYMBOLS.length];
        /** The producer run and revision of the retained current state: the tie-break between two states of the same instant. */
        final String[] currentRun = new String[SYMBOLS.length];
        final long[] currentRevision = new long[SYMBOLS.length];

        Store() {
            for (int i = 0; i < bars.length; i++) {
                bars[i] = new ArrayList<>(HISTORY_BARS + 1);
            }
            java.util.Arrays.fill(currentMs, Long.MIN_VALUE);
        }

        Store copy() {
            Store c = new Store();
            for (int i = 0; i < bars.length; i++) {
                c.bars[i].addAll(bars[i]);
            }
            for (int s = 0; s < current.length; s++) {
                c.current[s] = current[s];
                c.currentBytes[s] = currentBytes[s];
            }
            c.version = version;
            System.arraycopy(currentFrame, 0, c.currentFrame, 0, currentFrame.length);
            System.arraycopy(currentMs, 0, c.currentMs, 0, currentMs.length);
            System.arraycopy(currentRun, 0, c.currentRun, 0, currentRun.length);
            System.arraycopy(currentRevision, 0, c.currentRevision, 0, currentRevision.length);
            return c;
        }

        /** Merges a bar by its clock: true when it became the newest of its (symbol, timeframe). */
        boolean merge(int slot, Bar bar, AtomicLong duplicates) {
            ArrayList<Bar> list = bars[slot];
            int n = list.size();
            if (n == 0 || bar.startMs() > list.get(n - 1).startMs()) {
                version++;
                list.add(bar);
                if (n == HISTORY_BARS) {
                    list.remove(0);
                }
                return true;
            }
            int at = 0;
            while (list.get(at).startMs() < bar.startMs()) {
                at++;
            }
            if (list.get(at).startMs() == bar.startMs()) {
                duplicates.incrementAndGet();
                return false;
            }
            if (at == 0 && n == HISTORY_BARS) {
                return false;   // older than everything a full window retains
            }
            version++;
            list.add(at, bar);
            if (n == HISTORY_BARS) {
                list.remove(0);
            }
            return false;
        }
    }

    private final ObjectMapper mapper;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong drops = new AtomicLong();
    private final AtomicLong regressions = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private Store live = new Store();
    private String helloCache;
    private long helloCacheVersion;

    StudiesRelay(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The live path: returns the frame data to broadcast for the event, or null (dropped, duplicate, regression, history only). */
    synchronized String onLive(String event, String key, Object value, boolean esTopic) {
        return apply(live, event, key, value, esTopic);
    }

    /** A private copy of the retained state for a recovery read; nothing in it is visible until {@link #promote}. */
    synchronized Store newStaging() {
        return live.copy();
    }

    /** The recovery path: the record is merged into the private store; nothing is broadcast. */
    void onStaged(Store staged, String event, String key, Object value, boolean esTopic) {
        apply(staged, event, key, value, esTopic);
    }

    /**
     * Folds the staged recovery store into the live one and returns the frames recovery owes the connected sockets, as
     * {event, data} pairs in order: every staged bar that becomes the newest of its (symbol, timeframe), then each symbol's current
     * state when it is newer than the live one (or revised at the same instant). Records that arrived live while the recovery was
     * pending are already in the live store and are kept: a merge, never a replacement.
     */
    synchronized List<String[]> promote(Store staged) {
        List<String[]> frames = new ArrayList<>();
        AtomicLong notCounted = new AtomicLong();   // the staged copy re-presents bars the live store already holds
        for (int i = 0; i < staged.bars.length; i++) {
            for (Bar b : staged.bars[i]) {
                if (live.merge(i, b, notCounted)) {
                    frames.add(new String[] {BAR_EVENT, b.json()});
                }
            }
        }
        for (int s = 0; s < SYMBOLS.length; s++) {
            String frame = staged.currentFrame[s];
            if (frame != null && supersedes(live, s, staged.currentMs[s], staged.currentRun[s], staged.currentRevision[s])) {
                live.current[s] = staged.current[s];
                live.currentRun[s] = staged.currentRun[s];
                live.currentRevision[s] = staged.currentRevision[s];
                live.currentBytes[s] = staged.currentBytes[s];
                live.currentFrame[s] = frame;
                live.currentMs[s] = staged.currentMs[s];
                live.version++;
                frames.add(new String[] {CURRENT_EVENT, frame});
            }
        }
        return frames;
    }

    /** A tombstone or an unreadable value: counted, retains nothing, withdraws nothing. */
    void onDrop() {
        drops.incrementAndGet();
    }

    /** The mirrored ES topics carry the ES producer's records and the local topics the SPX producer's: the topic fixes the symbol. */
    static boolean isEsTopic(String topic) {
        return topic != null && topic.startsWith("es.");
    }

    private String apply(Store target, String event, Object key, Object value, boolean esTopic) {
        if (!(value instanceof GenericRecord record)) {
            drops.incrementAndGet();
            return null;
        }
        try {
            return BAR_EVENT.equals(event) ? applyBar(target, key, record, esTopic ? 1 : 0) : applyCurrent(target, key, record, esTopic ? 1 : 0);
        } catch (RuntimeException | JsonProcessingException e) {
            drops.incrementAndGet();   // a malformed record (a missing field, an unparseable instant) is refused, never repaired
            return null;
        }
    }

    private String applyBar(Store target, Object key, GenericRecord r, int topicSymbol) throws JsonProcessingException {
        Schema schema = r.getSchema();
        String symbol = str(r, "symbol");
        int s = indexOf(SYMBOLS, symbol);
        int t = indexOf(TF_ENUM, str(r, "timeframe"));
        String status = str(r, "status");
        String barId = str(r, "barId");
        if (!BAR_SCHEMA.equals(schema.getFullName()) || s != topicSymbol || t < 0 || !"STUDIES_V1".equals(str(r, "methodVersion"))
                || !Integer.valueOf(1).equals(r.get("schemaVersion")) || "EMPTY".equals(status) || barId.isEmpty()
                || !(symbol + "|" + TF[t]).equals(String.valueOf(key)) || !withinBarBounds(r)) {
            drops.incrementAndGet();
            return null;
        }
        if (!withinBytes(r, new int[] {BAR_MAX_BYTES})) {
            drops.incrementAndGet();   // an absurd string or key is refused by a read-only walk, before anything is rendered
            return null;
        }
        String barStart = str(r, "barStart");
        long startMs = Instant.parse(barStart).toEpochMilli();
        long span = Instant.parse(str(r, "barEnd")).toEpochMilli() - startMs;
        // the bar's own clock and identity, as the producer's validator states them: a positive span of at most one timeframe, and a
        // barId of the form symbol|…|tf|barStart (at most 64 characters), so a mislabelled record cannot enter the history
        if (span <= 0 || span > TF_MS[t] || barId.length() > BAR_ID_MAX || !barId.startsWith(symbol + "|")
                || !barId.endsWith("|" + TF[t] + "|" + barStart)) {
            drops.incrementAndGet();
            return null;
        }
        String json = mapper.writeValueAsString(AvroJson.toJsonNode(mapper, r));
        int bytes = utf8Length(json);
        if (bytes > BAR_MAX_BYTES) {
            drops.incrementAndGet();
            return null;
        }
        Bar bar = new Bar(barId, startMs, json, bytes);
        ArrayList<Bar> held = target.bars[s * TF.length + t];
        for (int i = held.size() - 1; i >= 0; i--) {
            if (held.get(i).barId().equals(barId)) {
                duplicates.incrementAndGet();
                return null;
            }
        }
        boolean newest = target.merge(s * TF.length + t, bar, duplicates);
        if (newest) {
            accepted.incrementAndGet();
        }
        return newest ? json : null;
    }

    private String applyCurrent(Store target, Object key, GenericRecord r, int topicSymbol) throws JsonProcessingException {
        String symbol = str(r, "symbol");
        int s = indexOf(SYMBOLS, symbol);
        if (!CURRENT_SCHEMA.equals(r.getSchema().getFullName()) || s != topicSymbol || !symbol.equals(String.valueOf(key))
                || !"STUDIES_V1".equals(str(r, "methodVersion")) || !Integer.valueOf(1).equals(r.get("schemaVersion"))) {
            drops.incrementAndGet();
            return null;
        }
        long publishedMs = Instant.parse(str(r, "publishedAt")).toEpochMilli();
        if (publishedMs < target.currentMs[s]) {
            regressions.incrementAndGet();
            return null;
        }
        if (!withinBytes(r, new int[] {CURRENT_MAX_BYTES})) {
            drops.incrementAndGet();
            return null;
        }
        String runId = str(r, "runId");
        long revision = ((Number) r.get("revision")).longValue();
        Schema.Field timeframes = r.getSchema().getField("timeframes");
        Map<?, ?> byTf = (Map<?, ?>) r.get("timeframes");
        if (byTf.size() > MAX_TIMEFRAMES || !withinCurrentBounds(byTf)) {
            drops.incrementAndGet();
            return null;
        }
        Schema valueSchema = timeframes.schema().getValueType();
        String[] pieces = new String[TF.length];
        for (Map.Entry<?, ?> e : byTf.entrySet()) {
            int t = indexOf(TF, String.valueOf(e.getKey()));
            if (t >= 0) {
                pieces[t] = mapper.writeValueAsString(AvroJson.toJsonNode(mapper, e.getValue(), valueSchema));
            }
        }
        ObjectNode header = mapper.createObjectNode();
        for (Schema.Field f : r.getSchema().getFields()) {
            if (f != timeframes) {
                header.set(f.name(), AvroJson.toJsonNode(mapper, r.get(f.name()), f.schema()));
            }
        }
        String head = mapper.writeValueAsString(header);
        head = head.substring(0, head.length() - 1) + ",\"timeframes\":{";
        int headBytes = utf8Length(head);
        int size = headBytes + 2;
        int[] pieceBytes = new int[TF.length];
        for (int t = 0; t < TF.length; t++) {
            if (pieces[t] == null) {
                drops.incrementAndGet();   // a forwarded timeframe is missing: the record is not the producer's contract
                return null;
            }
            pieceBytes[t] = utf8Length(pieces[t]);
            size += pieceBytes[t] + 8;
        }
        if (size > CURRENT_MAX_BYTES) {
            drops.incrementAndGet();
            return null;
        }
        String[] single = new String[TF.length];
        int[] singleBytes = new int[TF.length];
        StringBuilder all = new StringBuilder(size).append(head);
        for (int t = 0; t < TF.length; t++) {
            all.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":").append(pieces[t]);
            single[t] = head + "\"" + TF[t] + "\":" + pieces[t] + "}}";
            singleBytes[t] = headBytes + pieceBytes[t] + 8;
        }
        String frame = all.append("}}").toString();
        if (publishedMs == target.currentMs[s] && frame.equals(target.currentFrame[s])) {
            accepted.incrementAndGet();
            return frame;   // an identical republish: accepted and forwarded, nothing retained changes (the hello cache stays valid)
        }
        if (!supersedes(target, s, publishedMs, runId, revision)) {
            regressions.incrementAndGet();   // the same instant from the same run needs a higher revision; another run does not displace the held one
            return null;
        }
        target.version++;
        target.currentRun[s] = runId;
        target.currentRevision[s] = revision;
        target.current[s] = single;
        target.currentBytes[s] = singleBytes;
        target.currentFrame[s] = frame;
        target.currentMs[s] = publishedMs;
        accepted.incrementAndGet();
        return frame;
    }

    /**
     * The hello's studies fields, ready to append after a comma-less prefix: {@code ,"studies":{...},"studyBars":{...},
     * "esStudies":{...},"esStudyBars":{...}}. Cached until the retained state changes; the oldest history bars are dropped
     * (longest list first) when the part would exceed {@link #HELLO_MAX_BYTES}; only when no history is left does the largest current
     * entry degrade to {@code null}.
     */
    synchronized String helloFields() {
        if (helloCache == null || helloCacheVersion != live.version) {
            helloCache = buildHello(live);
            helloCacheVersion = live.version;
        }
        return helloCache;
    }

    /**
     * The hello part, rendered in full and measured: while its UTF-8 size exceeds {@link #HELLO_MAX_BYTES} the oldest bar of the
     * longest history is given up (one at a time, re-rendered and re-measured, so history is dropped only when the fragment REALLY
     * exceeds the budget); only when no history is left does the largest current entry degrade to {@code null}. The common case is a
     * single render. The result is cached until the retained state changes, so the extra renders of an over-budget hello are paid once.
     */
    private static String buildHello(Store st) {
        int[] from = new int[st.bars.length];
        boolean[][] nulled = new boolean[SYMBOLS.length][TF.length];
        String out = render(st, from, nulled);
        while (utf8Length(out) > HELLO_MAX_BYTES) {
            int longest = -1;
            for (int i = 0; i < st.bars.length; i++) {
                if (st.bars[i].size() - from[i] > 0 && (longest < 0 || st.bars[i].size() - from[i] > st.bars[longest].size() - from[longest])) {
                    longest = i;
                }
            }
            if (longest >= 0) {
                from[longest]++;
            } else {
                int ws = -1, wt = -1, most = -1;
                for (int s = 0; s < SYMBOLS.length; s++) {
                    for (int t = 0; st.current[s] != null && t < TF.length; t++) {
                        if (!nulled[s][t] && st.currentBytes[s][t] > most) {
                            most = st.currentBytes[s][t];
                            ws = s;
                            wt = t;
                        }
                    }
                }
                if (ws < 0) {
                    break;
                }
                nulled[ws][wt] = true;
            }
            out = render(st, from, nulled);
        }
        return out;
    }

    private static String render(Store st, int[] from, boolean[][] nulled) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        for (int s = 0; s < SYMBOLS.length; s++) {
            sb.append(",\"").append(CURRENT_FIELD[s]).append("\":{");
            for (int t = 0; t < TF.length; t++) {
                sb.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":")
                        .append(st.current[s] == null || nulled[s][t] ? "null" : st.current[s][t]);
            }
            sb.append("},\"").append(BARS_FIELD[s]).append("\":{");
            for (int t = 0; t < TF.length; t++) {
                int slot = s * TF.length + t;
                sb.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":[");
                for (int i = from[slot]; i < st.bars[slot].size(); i++) {
                    sb.append(i == from[slot] ? "" : ",").append(st.bars[slot].get(i).json());
                }
                sb.append(']');
            }
            sb.append('}');
        }
        return sb.toString();
    }

    String metrics() {
        return "# HELP gateway_studies_accepted_total Study bars that became the newest of their series (history-only bars are not counted) and current states accepted.\n"
                + "# TYPE gateway_studies_accepted_total counter\n"
                + "gateway_studies_accepted_total " + accepted.get() + "\n"
                + "# HELP gateway_studies_drops_total Study records refused at the gateway boundary (invalid, oversize, tombstone).\n"
                + "# TYPE gateway_studies_drops_total counter\n"
                + "gateway_studies_drops_total " + drops.get() + "\n"
                + "# HELP gateway_studies_regressions_total Study current states refused as out of order (an older publishedAt, or the same instant without a higher revision from the same run).\n"
                + "# TYPE gateway_studies_regressions_total counter\n"
                + "gateway_studies_regressions_total " + regressions.get() + "\n"
                + "# HELP gateway_studies_duplicates_total Study bars already held (replay re-emits identical keys and values).\n"
                + "# TYPE gateway_studies_duplicates_total counter\n"
                + "gateway_studies_duplicates_total " + duplicates.get() + "\n";
    }

    long dropsForTest() {
        return drops.get();
    }

    long regressionsForTest() {
        return regressions.get();
    }

    long duplicatesForTest() {
        return duplicates.get();
    }

    /**
     * Whether a state of {@code publishedMs} / {@code run} / {@code revision} replaces the one {@code held} retains for symbol
     * {@code s}: a later instant always; the same instant only from the same run with a higher revision (a delayed older revision
     * of the same instant, or another run's state of that instant, never rolls the retained state back or sideways).
     */
    private static boolean supersedes(Store held, int s, long publishedMs, String run, long revision) {
        return held.currentFrame[s] == null || publishedMs > held.currentMs[s]
                || (publishedMs == held.currentMs[s] && run.equals(held.currentRun[s]) && revision > held.currentRevision[s]);
    }

    /**
     * A read-only walk of a decoded record that spends {@code budget[0]} on every string (its UTF-8 length), map key, number and
     * element and returns false once it is exhausted: an absurdly long string or key is refused here, before any JSON is rendered or
     * retained. It bounds the RENDERING and retention cost; the decoded record itself already exists (its size is bounded by the Kafka
     * message limit, not by this walk). A lower bound of the rendered size, so it never refuses a record the rendered-size check accepts.
     */
    static boolean withinBytes(Object v, int[] budget) {
        if (v == null) {
            return true;
        }
        if (v instanceof CharSequence cs) {
            budget[0] -= utf8Length(cs.toString());
        } else if (v instanceof GenericRecord rec) {
            for (Schema.Field f : rec.getSchema().getFields()) {
                if (!withinBytes(rec.get(f.pos()), budget)) {
                    return false;
                }
            }
            budget[0] -= 2;
        } else if (v instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!withinBytes(e.getKey(), budget) || !withinBytes(e.getValue(), budget)) {
                    return false;
                }
            }
        } else if (v instanceof java.util.Collection<?> c) {
            for (Object x : c) {
                if (!withinBytes(x, budget)) {
                    return false;
                }
            }
        } else {
            budget[0] -= 1;   // a number, a boolean or an enum symbol
        }
        return budget[0] >= 0;
    }

    /** Bar bounds before rendering: the value/state maps and the two lists are the only unbounded parts of a StudyBar. */
    private static boolean withinBarBounds(GenericRecord r) {
        return size(r.get("values")) <= MAX_VALUES && size(r.get("states")) <= MAX_STATES && size(r.get("events")) <= MAX_EVENTS
                && size(r.get("divergences")) <= MAX_DIVERGENCES;
    }

    /** The same for every timeframe entry of a current state. */
    private static boolean withinCurrentBounds(Map<?, ?> byTf) {
        for (Object v : byTf.values()) {
            if (!(v instanceof GenericRecord t) || size(t.get("values")) > MAX_VALUES || size(t.get("states")) > MAX_STATES
                    || size(t.get("lastEvents")) > MAX_EVENTS || size(t.get("divergences")) > MAX_DIVERGENCES) {
                return false;
            }
        }
        return true;
    }

    private static int size(Object collectionOrMap) {
        return collectionOrMap instanceof Map<?, ?> m ? m.size() : collectionOrMap instanceof java.util.Collection<?> c ? c.size() : 0;
    }

    /** The UTF-8 length of {@code s} without allocating the bytes. */
    static int utf8Length(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                n++;
            } else if (c < 0x800) {
                n += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                n += 4;
                i++;
            } else {
                n += 3;
            }
        }
        return n;
    }

    private static String str(GenericRecord r, String field) {
        return String.valueOf(r.get(field));
    }

    private static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }
}
