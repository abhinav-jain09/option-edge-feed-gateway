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
    /** The whole studies part of the hello, in characters (ASCII-only vocabulary, so bytes). */
    static final int HELLO_MAX_CHARS = 512 * 1024;
    static final int BAR_MAX_CHARS = 64 * 1024;
    static final int CURRENT_MAX_CHARS = 256 * 1024;

    private static final String BAR_SCHEMA = "com.optionsedge.contracts.indicators.avro.StudyBar";
    private static final String CURRENT_SCHEMA = "com.optionsedge.contracts.indicators.avro.StudiesCurrent";
    private static final String[] SYMBOLS = {"SPX", "ES"};
    /** Hello field names per symbol: SPX is the chart's index symbol, ES rides the es-prefixed twins. */
    private static final String[] CURRENT_FIELD = {"studies", "esStudies"};
    private static final String[] BARS_FIELD = {"studyBars", "esStudyBars"};
    private static final String[] TF = {"1m", "5m", "15m"};
    private static final String[] TF_ENUM = {"T1M", "T5M", "T15M"};

    /** One retained finalized bar: its identity, its clock and the frame data it will be sent as. */
    record Bar(String barId, long startMs, String json) { }

    /** The retained state of the stream. Not thread-safe: the relay mutates the live store under its lock. */
    static final class Store {
        @SuppressWarnings("unchecked")
        final ArrayList<Bar>[] bars = new ArrayList[SYMBOLS.length * TF.length];
        /** Per symbol: the latest current state rendered once per timeframe (timeframes reduced to that one key). */
        final String[][] current = new String[SYMBOLS.length][];
        /** Per symbol: the frame data of the latest current state (timeframes reduced to the three forwarded keys). */
        final String[] currentFrame = new String[SYMBOLS.length];
        final long[] currentMs = new long[SYMBOLS.length];

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
            }
            System.arraycopy(currentFrame, 0, c.currentFrame, 0, currentFrame.length);
            System.arraycopy(currentMs, 0, c.currentMs, 0, currentMs.length);
            return c;
        }

        /** Merges a bar by its clock: true when it became the newest of its (symbol, timeframe). */
        boolean merge(int slot, Bar bar, AtomicLong duplicates) {
            ArrayList<Bar> list = bars[slot];
            int n = list.size();
            if (n == 0 || bar.startMs() > list.get(n - 1).startMs()) {
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

    StudiesRelay(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The live path: returns the frame data to broadcast for the event, or null (dropped, duplicate, regression, history only). */
    synchronized String onLive(String event, String key, Object value) {
        helloCache = null;
        return apply(live, event, key, value);
    }

    /** A private copy of the retained state for a recovery read; nothing in it is visible until {@link #promote}. */
    synchronized Store newStaging() {
        return live.copy();
    }

    /** The recovery path: the record is merged into the private store; nothing is broadcast. */
    void onStaged(Store staged, String event, String key, Object value) {
        apply(staged, event, key, value);
    }

    /**
     * Makes the staged store the live one and returns the frames recovery owes the connected sockets, as
     * {event, data} pairs in order: every bar newer than the live tail of its (symbol, timeframe), then each symbol's current
     * state when it is newer than the live one.
     */
    synchronized List<String[]> promote(Store staged) {
        List<String[]> frames = new ArrayList<>();
        for (int i = 0; i < staged.bars.length; i++) {
            ArrayList<Bar> was = live.bars[i];
            long tail = was.isEmpty() ? Long.MIN_VALUE : was.get(was.size() - 1).startMs();
            for (Bar b : staged.bars[i]) {
                if (b.startMs() > tail) {
                    frames.add(new String[] {BAR_EVENT, b.json()});
                }
            }
        }
        for (int s = 0; s < SYMBOLS.length; s++) {
            if (staged.currentFrame[s] != null && staged.currentMs[s] > live.currentMs[s]) {
                frames.add(new String[] {CURRENT_EVENT, staged.currentFrame[s]});
            }
        }
        live = staged;
        helloCache = null;
        return frames;
    }

    /** A tombstone or an unreadable value: counted, retains nothing, withdraws nothing. */
    void onDrop() {
        drops.incrementAndGet();
    }

    private String apply(Store target, String event, Object key, Object value) {
        if (!(value instanceof GenericRecord record)) {
            drops.incrementAndGet();
            return null;
        }
        try {
            return BAR_EVENT.equals(event) ? applyBar(target, key, record) : applyCurrent(target, key, record);
        } catch (RuntimeException | JsonProcessingException e) {
            drops.incrementAndGet();   // a malformed record (a missing field, an unparseable instant) is refused, never repaired
            return null;
        }
    }

    private String applyBar(Store target, Object key, GenericRecord r) throws JsonProcessingException {
        Schema schema = r.getSchema();
        String symbol = str(r, "symbol");
        int s = indexOf(SYMBOLS, symbol);
        int t = indexOf(TF_ENUM, str(r, "timeframe"));
        String status = str(r, "status");
        String barId = str(r, "barId");
        if (!BAR_SCHEMA.equals(schema.getFullName()) || s < 0 || t < 0 || !"STUDIES_V1".equals(str(r, "methodVersion"))
                || !Integer.valueOf(1).equals(r.get("schemaVersion")) || "EMPTY".equals(status) || barId.isEmpty()
                || !(symbol + "|" + TF[t]).equals(String.valueOf(key))) {
            drops.incrementAndGet();
            return null;
        }
        long startMs = Instant.parse(str(r, "barStart")).toEpochMilli();
        String json = mapper.writeValueAsString(AvroJson.toJsonNode(mapper, r));
        if (json.length() > BAR_MAX_CHARS) {
            drops.incrementAndGet();
            return null;
        }
        Bar bar = new Bar(barId, startMs, json);
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

    private String applyCurrent(Store target, Object key, GenericRecord r) throws JsonProcessingException {
        String symbol = str(r, "symbol");
        int s = indexOf(SYMBOLS, symbol);
        if (!CURRENT_SCHEMA.equals(r.getSchema().getFullName()) || s < 0 || !symbol.equals(String.valueOf(key))
                || !"STUDIES_V1".equals(str(r, "methodVersion")) || !Integer.valueOf(1).equals(r.get("schemaVersion"))) {
            drops.incrementAndGet();
            return null;
        }
        long publishedMs = Instant.parse(str(r, "publishedAt")).toEpochMilli();
        if (publishedMs < target.currentMs[s]) {
            regressions.incrementAndGet();
            return null;
        }
        Schema.Field timeframes = r.getSchema().getField("timeframes");
        Map<?, ?> byTf = (Map<?, ?>) r.get("timeframes");
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
        int size = head.length() + 2;
        for (String p : pieces) {
            if (p == null) {
                drops.incrementAndGet();   // a forwarded timeframe is missing: the record is not the producer's contract
                return null;
            }
            size += p.length() + 8;
        }
        if (size > CURRENT_MAX_CHARS) {
            drops.incrementAndGet();
            return null;
        }
        String[] single = new String[TF.length];
        StringBuilder all = new StringBuilder(size).append(head);
        for (int t = 0; t < TF.length; t++) {
            all.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":").append(pieces[t]);
            single[t] = head + "\"" + TF[t] + "\":" + pieces[t] + "}}";
        }
        String frame = all.append("}}").toString();
        target.current[s] = single;
        target.currentFrame[s] = frame;
        target.currentMs[s] = publishedMs;
        accepted.incrementAndGet();
        return frame;
    }

    /**
     * The hello's studies fields, ready to append after a comma-less prefix: {@code ,"studies":{...},"studyBars":{...},
     * "esStudies":{...},"esStudyBars":{...}}. Cached until the retained state changes; the oldest history bars are dropped
     * (longest list first) when the part would exceed {@link #HELLO_MAX_CHARS}, the current entries never.
     */
    synchronized String helloFields() {
        if (helloCache == null) {
            helloCache = buildHello(live);
        }
        return helloCache;
    }

    private static String buildHello(Store st) {
        int[] from = new int[st.bars.length];
        long total = 0;
        for (String[] c : st.current) {
            if (c != null) {
                for (String p : c) {
                    total += p.length();
                }
            }
        }
        for (ArrayList<Bar> list : st.bars) {
            for (Bar b : list) {
                total += b.json().length() + 1;
            }
        }
        while (total > HELLO_MAX_CHARS) {
            int longest = -1;
            for (int i = 0; i < st.bars.length; i++) {
                if (st.bars[i].size() - from[i] > 0 && (longest < 0 || st.bars[i].size() - from[i] > st.bars[longest].size() - from[longest])) {
                    longest = i;
                }
            }
            if (longest < 0) {
                break;
            }
            total -= st.bars[longest].get(from[longest]++).json().length() + 1;
        }
        StringBuilder sb = new StringBuilder((int) Math.min(total + 256, Integer.MAX_VALUE));
        for (int s = 0; s < SYMBOLS.length; s++) {
            sb.append(",\"").append(CURRENT_FIELD[s]).append("\":{");
            for (int t = 0; t < TF.length; t++) {
                sb.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":")
                        .append(st.current[s] == null ? "null" : st.current[s][t]);
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
        return "# HELP gateway_studies_accepted_total Study bars (as the newest of their series) and current states accepted.\n"
                + "# TYPE gateway_studies_accepted_total counter\n"
                + "gateway_studies_accepted_total " + accepted.get() + "\n"
                + "# HELP gateway_studies_drops_total Study records refused at the gateway boundary (invalid, oversize, tombstone).\n"
                + "# TYPE gateway_studies_drops_total counter\n"
                + "gateway_studies_drops_total " + drops.get() + "\n"
                + "# HELP gateway_studies_regressions_total Study current states refused because publishedAt regressed.\n"
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
