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

    /**
     * The writer schemas this relay forwards, pinned by their Avro parsing-form fingerprints (contracts 0.30.0 registry files
     * StudyBar.avsc / StudiesCurrent.avsc; pinned in StudiesRelayTest against the byte-identical test copies). A record written under a
     * schema of the same name but another shape is refused: the browser contract is exact, and the page rejects an altered field set.
     */
    static final long BAR_FINGERPRINT = 2213339975127789770L;
    static final long CURRENT_FINGERPRINT = -2671074147627402471L;
    private final java.util.IdentityHashMap<Schema, Boolean> approvedSchemas = new java.util.IdentityHashMap<>();

    private boolean approvedSchema(Schema schema, long expected) {
        synchronized (approvedSchemas) {
            Boolean known = approvedSchemas.get(schema);
            if (known == null) {
                if (approvedSchemas.size() > 32) {
                    approvedSchemas.clear();   // a bounded identity cache: a deserializer hands out one Schema object per writer id
                }
                known = org.apache.avro.SchemaNormalization.parsingFingerprint64(schema) == expected;
                approvedSchemas.put(schema, known);
            }
            return known;
        }
    }

    private static final String BAR_SCHEMA = "com.optionsedge.contracts.indicators.avro.StudyBar";
    private static final String CURRENT_SCHEMA = "com.optionsedge.contracts.indicators.avro.StudiesCurrent";
    private static final String[] SYMBOLS = {"SPX", "ES"};
    /** Hello field names per symbol: SPX is the chart's index symbol, ES rides the es-prefixed twins. */
    private static final String[] CURRENT_FIELD = {"studies", "esStudies"};
    private static final String[] BARS_FIELD = {"studyBars", "esStudyBars"};
    private static final String[] TF = {"1m", "5m", "15m"};
    /** The producer's whole timeframe key set of a StudiesCurrent (its validator requires exactly these six); three are forwarded. */
    private static final java.util.Set<String> PRODUCER_TIMEFRAMES = java.util.Set.of("30s", "1m", "5m", "15m", "1h", "4h");
    private static final String[] TF_ENUM = {"T1M", "T5M", "T15M"};
    private static final long[] TF_MS = {60_000L, 300_000L, 900_000L};
    /** The producer's own bound (StudyWireValidator): a barId is at most 64 characters. */
    private static final int BAR_ID_MAX = 64;

    /** One retained finalized bar: its identity, its clock and the frame data it will be sent as. */
    static final class Bar {
        private final String barId;
        private final long startMs;
        /** The decoded record while the bar is only staged (not yet rendered); released once rendered. */
        private GenericRecord pending;
        /** Whether {@code pending} is this relay's own copy (taken at the end of the poll that delivered it). */
        private boolean sealed;
        private String json;
        private int bytes;

        /** A rendered bar (the live path). */
        Bar(String barId, long startMs, String json, int bytes) {
            this.barId = barId;
            this.startMs = startMs;
            this.json = json;
            this.bytes = bytes;
        }

        /** A staged bar: validated and ordered, rendered only if recovery retains it. */
        Bar(String barId, long startMs, GenericRecord pending) {
            this.barId = barId;
            this.startMs = startMs;
            this.pending = pending;
        }

        String barId() {
            return barId;
        }

        long startMs() {
            return startMs;
        }

        String json() {
            return json;
        }

        int bytes() {
            return bytes;
        }

        boolean rendered() {
            return json != null;
        }
    }

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
    private final AtomicLong renders = new AtomicLong();
    private final AtomicLong copies = new AtomicLong();
    private final AtomicLong helloSuppressed = new AtomicLong();
    private final AtomicLong drops = new AtomicLong();
    private final AtomicLong regressions = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private Store live = new Store();
    private String helloCache;
    private long helloCacheVersion;
    private int helloCacheBudget;

    StudiesRelay(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The live path: returns the frame data to broadcast for the event, or null (dropped, duplicate, regression, history only). */
    synchronized String onLive(String event, String key, Object value, boolean esTopic) {
        return apply(live, event, key, value, esTopic, false);
    }

    /** A private copy of the retained state for a recovery read; nothing in it is visible until {@link #promote}. */
    synchronized Store newStaging() {
        return live.copy();
    }

    /** The recovery path: the record is merged into the private store; nothing is broadcast. */
    void onStaged(Store staged, String event, String key, Object value, boolean esTopic) {
        apply(staged, event, key, value, esTopic, true);
    }

    /**
     * Folds the staged recovery store into the live one and returns the frames recovery owes the connected sockets, as
     * {event, data} pairs in order: every staged bar that becomes the newest of its (symbol, timeframe), then each symbol's current
     * state when it is newer than the live one (or revised at the same instant). Records that arrived live while the recovery was
     * pending are already in the live store and are kept: a merge, never a replacement.
     */
    synchronized List<String[]> promote(Store staged) {
        seal(staged);   // the poll that completed the recovery may have delivered bars not yet sealed
        List<String[]> frames = new ArrayList<>();
        AtomicLong notCounted = new AtomicLong();   // the staged copy re-presents bars the live store already holds
        for (int i = 0; i < staged.bars.length; i++) {
            for (Bar b : staged.bars[i]) {
                if (!render(b)) {
                    continue;   // an unrenderable staged bar is never retained or sent
                }
                if (live.merge(i, b, notCounted)) {
                    accepted.incrementAndGet();
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

    /** The size of the mandatory studies fields with nothing retained (the part of a hello that is never shed). */
    static int skeletonBytes() {
        return SKELETON_BYTES;
    }

    /** The hello went out without the studies fields because the other fields left no room for even the skeleton. */
    void onHelloSuppressed() {
        helloSuppressed.incrementAndGet();
    }

    /** A tombstone or an unreadable value: counted, retains nothing, withdraws nothing. */
    void onDrop() {
        drops.incrementAndGet();
    }

    /** The mirrored ES topics carry the ES producer's records and the local topics the SPX producer's: the topic fixes the symbol. */
    static boolean isEsTopic(String topic) {
        return topic != null && topic.startsWith("es.");
    }

    private String apply(Store target, String event, Object key, Object value, boolean esTopic, boolean staging) {
        if (!(value instanceof GenericRecord record)) {
            drops.incrementAndGet();
            return null;
        }
        try {
            return BAR_EVENT.equals(event) ? applyBar(target, key, record, esTopic ? 1 : 0, staging) : applyCurrent(target, key, record, esTopic ? 1 : 0);
        } catch (RuntimeException | JsonProcessingException e) {
            drops.incrementAndGet();   // a malformed record (a missing field, an unparseable instant) is refused, never repaired
            return null;
        }
    }

    private String applyBar(Store target, Object key, GenericRecord r, int topicSymbol, boolean staging) throws JsonProcessingException {
        Schema schema = r.getSchema();
        String symbol = str(r, "symbol");
        int s = indexOf(SYMBOLS, symbol);
        int t = indexOf(TF_ENUM, str(r, "timeframe"));
        String status = str(r, "status");
        String barId = str(r, "barId");
        if (!BAR_SCHEMA.equals(schema.getFullName()) || !approvedSchema(schema, BAR_FINGERPRINT) || s != topicSymbol || t < 0 || !"STUDIES_V1".equals(str(r, "methodVersion"))
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
        ArrayList<Bar> held = target.bars[s * TF.length + t];
        for (int i = held.size() - 1; i >= 0; i--) {
            if (held.get(i).barId().equals(barId)) {
                duplicates.incrementAndGet();
                return null;
            }
        }
        if (staging) {
            // Recovery reads days of history to keep eight bars per series: a staged bar is validated and ordered here and rendered to
            // JSON only if it is still retained when recovery is promoted, so the work is bounded by what is kept, not by what was read.
            // the deserializer's object is valid for the poll that delivered it and not ours to hold across polls: seal(staged) takes an
            // independent copy at the end of each poll, and only of the bars still retained (at most eight per series), never of those a
            // newer bar of the same poll already evicted
            target.merge(s * TF.length + t, new Bar(barId, startMs, r), duplicates);
            return null;
        }
        String json = mapper.writeValueAsString(AvroJson.toJsonNode(mapper, r));
        renders.incrementAndGet();
        int bytes = utf8Length(json);
        if (bytes > BAR_MAX_BYTES) {
            drops.incrementAndGet();
            return null;
        }
        boolean newest = target.merge(s * TF.length + t, new Bar(barId, startMs, json, bytes), duplicates);
        if (newest) {
            accepted.incrementAndGet();
        }
        return newest ? json : null;
    }

    /**
     * End of a poll during recovery: replaces the deserializer's objects of the bars the staged store still retains by independent
     * copies. At most eight bars per series are retained, so at most 48 copies per poll however many records were read.
     */
    synchronized void seal(Store staged) {
        for (ArrayList<Bar> list : staged.bars) {
            for (Bar b : list) {
                if (!b.rendered() && !b.sealed) {
                    b.pending = org.apache.avro.generic.GenericData.get().deepCopy(b.pending.getSchema(), b.pending);
                    b.sealed = true;
                    copies.incrementAndGet();
                }
            }
        }
    }

    /** Renders a staged bar at promotion; false (counted as a drop) when it cannot be rendered or exceeds the per-bar bound. */
    private boolean render(Bar b) {
        if (b.rendered()) {
            return true;
        }
        try {
            String json = mapper.writeValueAsString(AvroJson.toJsonNode(mapper, b.pending));
            renders.incrementAndGet();
            int bytes = utf8Length(json);
            if (bytes > BAR_MAX_BYTES) {
                drops.incrementAndGet();
                return false;
            }
            b.json = json;
            b.bytes = bytes;
            b.pending = null;
            return true;
        } catch (RuntimeException | JsonProcessingException e) {
            drops.incrementAndGet();
            return false;
        }
    }

    private String applyCurrent(Store target, Object key, GenericRecord r, int topicSymbol) throws JsonProcessingException {
        String symbol = str(r, "symbol");
        int s = indexOf(SYMBOLS, symbol);
        if (!CURRENT_SCHEMA.equals(r.getSchema().getFullName()) || !approvedSchema(r.getSchema(), CURRENT_FINGERPRINT) || s != topicSymbol || !symbol.equals(String.valueOf(key))
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
        if (byTf.size() > MAX_TIMEFRAMES || !withinCurrentBounds(byTf) || !PRODUCER_TIMEFRAMES.equals(keySet(byTf))) {
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
        if (size > CURRENT_MAX_BYTES) {   // a cheap upper bound first: an absurd record is refused before the frames are assembled
            drops.incrementAndGet();
            return null;
        }
        String[] single = new String[TF.length];
        int[] singleBytes = new int[TF.length];
        StringBuilder all = new StringBuilder(size).append(head);
        for (int t = 0; t < TF.length; t++) {
            all.append(t == 0 ? "" : ",").append('"').append(TF[t]).append("\":").append(pieces[t]);
            single[t] = head + "\"" + TF[t] + "\":" + pieces[t] + "}}";
            singleBytes[t] = utf8Length(single[t]);   // measured, not estimated: it picks the largest entry to degrade
        }
        String frame = all.append("}}").toString();
        if (utf8Length(frame) > CURRENT_MAX_BYTES) {   // the exact bound is the assembled frame
            drops.incrementAndGet();
            return null;
        }
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
        return helloFields(HELLO_MAX_BYTES);
    }

    /**
     * The same with the byte budget the caller can spare: the service passes what is left of the whole hello's cap after every other
     * field, so the transmitted hello (not only this fragment) stays within it. A smaller budget gives up history sooner.
     */
    synchronized String helloFields(int budgetBytes) {
        int budget = Math.max(0, Math.min(HELLO_MAX_BYTES, budgetBytes));
        if (helloCache == null || helloCacheVersion != live.version || helloCacheBudget != budget) {
            helloCache = buildHello(live, budget);
            helloCacheVersion = live.version;
            helloCacheBudget = budget;
        }
        return helloCache;
    }

    /**
     * The UTF-8 size of the hello part with nothing retained (every current entry {@code null}, every history {@code []}): the
     * field names, timeframe keys, braces, colons and commas. Everything retained adds to it exactly: a current entry replaces a
     * 4-byte {@code null} (adds its bytes - 4) and a history of k >= 1 bars replaces a 2-byte {@code []} (adds the bars' bytes plus k - 1
     * commas), so the size of any retained set is known by arithmetic, without rendering. The arithmetic is exact for valid Unicode and
     * an upper bound otherwise ({@link #utf8Length} counts an unpaired surrogate as 3 bytes, the encoder's replacement is 1), so a
     * rendered fragment is never larger than the figure the budget was checked against.
     */
    private static final int SKELETON_BYTES = utf8Length(render(new Store(), new int[SYMBOLS.length * TF.length], new boolean[SYMBOLS.length][TF.length]));

    private static long sizeOf(Store st, int[] from, boolean[][] nulled) {
        long total = SKELETON_BYTES;
        for (int s = 0; s < SYMBOLS.length; s++) {
            for (int t = 0; t < TF.length && st.current[s] != null; t++) {
                if (!nulled[s][t]) {
                    total += st.currentBytes[s][t] - 4;
                }
            }
        }
        for (int i = 0; i < st.bars.length; i++) {
            int k = st.bars[i].size() - from[i];
            if (k > 0) {
                total += k - 1;
                for (int j = from[i]; j < st.bars[i].size(); j++) {
                    total += st.bars[i].get(j).bytes();
                }
            }
        }
        return total;
    }

    /**
     * The hello part for a byte budget. The retained set is chosen by exact arithmetic ({@link #sizeOf}): while its size exceeds the
     * budget the oldest bar of the longest history is given up (then, with no history left, the largest current entry degrades to
     * {@code null}); the fragment is rendered ONCE, for the chosen set. Cached until the retained state changes, so an over-budget
     * hello costs an O(entries) selection and one render per state change, never a render per eviction.
     */
    private static String buildHello(Store st, int budget) {
        int[] from = new int[st.bars.length];
        boolean[][] nulled = new boolean[SYMBOLS.length][TF.length];
        while (sizeOf(st, from, nulled) > budget) {
            int longest = -1;
            for (int i = 0; i < st.bars.length; i++) {
                if (st.bars[i].size() - from[i] > 0 && (longest < 0 || st.bars[i].size() - from[i] > st.bars[longest].size() - from[longest])) {
                    longest = i;
                }
            }
            if (longest >= 0) {
                from[longest]++;
                continue;
            }
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
                // nothing left to give up: the bare skeleton (about 300 bytes) is the least this hello can carry; the flag being on, the
                // fields must be present. The service's cap leaves a 4 KiB margin below the 1 MiB guard, which absorbs the skeleton.
                return render(new Store(), new int[st.bars.length], new boolean[SYMBOLS.length][TF.length]);
            }
            nulled[ws][wt] = true;
        }
        return render(st, from, nulled);
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
                + "# HELP gateway_studies_hello_suppressed_total Hellos sent without the studies fields because the other fields left no room for even their skeleton.\n"
                + "# TYPE gateway_studies_hello_suppressed_total counter\n"
                + "gateway_studies_hello_suppressed_total " + helloSuppressed.get() + "\n"
                + "# HELP gateway_studies_duplicates_total Study bars already held (replay re-emits identical keys and values).\n"
                + "# TYPE gateway_studies_duplicates_total counter\n"
                + "gateway_studies_duplicates_total " + duplicates.get() + "\n";
    }

    long copiesForTest() {
        return copies.get();
    }

    long rendersForTest() {
        return renders.get();
    }

    long acceptedForTest() {
        return accepted.get();
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

    private static java.util.Set<String> keySet(Map<?, ?> m) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Object k : m.keySet()) {
            keys.add(String.valueOf(k));
        }
        return keys;
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
