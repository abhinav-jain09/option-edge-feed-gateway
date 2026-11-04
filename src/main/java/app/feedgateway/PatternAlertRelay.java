package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.apache.avro.generic.GenericRecord;

/**
 * Server-side pattern break alerts (options-edge-processing market-structure-service/docs/PATTERN-ALERTS-SERVER-SIDE-DESIGN.md section 6; requirement ids AL-14 to AL-16, AL-24).
 * market-structure-service decides, words, records and publishes every event; this relay validates a decoded {@code PatternAlertEvent} at the gateway boundary, keeps a bounded
 * retained state per PROFILE (regular and estimate: each profile numbers its events from 1, and both can hold the same session date), and tells the page what to show. It never
 * derives or edits an event. The profile is named by the BINDING EVENT a record arrived on ({@code market-structure-alerts}, {@code market-structure-alerts-estimate}), never by the record.
 * <p>
 * What is decided where. A live frame is one broadcast, so its {@code display} (the event is {@code RAISED}) and {@code speak} (and the gateway clock is not past the producer's
 * {@code speakUntilMs}) are functions of the event alone. The decisions that differ per connection are made where the connection is known: the hello (composed per socket, its snapshot
 * of each profile remembered under the socket's id), the snapshot a socket is owed when its profile becomes ready, and the reply to its {@code alerts-resume} message. A page therefore
 * renders by a cursor rule alone and decides nothing.
 * <p>
 * Bounds: a record whose rendered form exceeds {@link #RECORD_MAX_BYTES} is a counted drop; a profile retains the newest {@code retained} events of its session (a larger one evicts the oldest)
 * and keeps the FULL record of at most the newest {@link #FULL_RAISED_MAX} {@code RAISED} events, so a display reply of full records is at most 48 x 4 KiB; the hello part is held under
 * {@link #HELLO_PART_BYTES}; a reply is built against {@code min(256 KiB, wsMaxQueuedBytes / 2)}, and the relay refuses to be enabled below {@link #MIN_WS_QUEUED_BYTES}.
 */
final class PatternAlertRelay extends TaRelay {
    static final String REGULAR_EVENT = "market-structure-alerts";
    static final String ESTIMATE_EVENT = "market-structure-alerts-estimate";
    static final String[] PROFILE = {"regular", "estimate"};
    static final String[] EVENT = {REGULAR_EVENT, ESTIMATE_EVENT};
    /** The alerts part of the hello is held under 96 KiB of content (AL-16). */
    static final long HELLO_PART_BYTES = 96L * 1024;
    /** A rendered alert record above this is a counted drop (a real event is about 1.5 KB). */
    static final int RECORD_MAX_BYTES = 4_096;
    /** Full records are kept for at most this many of the newest RAISED events of a session; older ones are retained in compact form only. */
    static final int FULL_RAISED_MAX = 48;
    /** A cold start re-reads about three sessions of watermarks and events from the end of the single partition; only the newest session is kept. */
    static final int RECOVERY_TAIL_RECORDS = 8_192;
    /** Below this per-socket queue the 48 x 4 KiB reply cannot be honoured within half the queue: the relay refuses to be enabled. */
    static final long MIN_WS_QUEUED_BYTES = 512L * 1024;
    static final long REPLY_MAX_BYTES = 256L * 1024;
    /** A resume message above this is answered {@code resume-error}. */
    static final int REQUEST_MAX_BYTES = 512;
    /** Overflow retries per connection (after the initial resume of a profile). */
    static final int OVERFLOW_RETRIES_MAX = 8;

    private static final String EVENT_SCHEMA = "com.optionsedge.contracts.marketstructure.alerts.PatternAlertEvent";
    private static final String WATERMARK_SCHEMA = "com.optionsedge.contracts.marketstructure.alerts.PatternAlertWatermark";
    private static final String SERVICE = "market-structure";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String RAISED = "RAISED";
    private static final String SELF_CHECK = "SUPPRESSED_SELF_CHECK";
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final String[] TF_ENUM = {"TF_1M", "TF_5M", "TF_15M"};
    /** The closed vocabulary of an event's kind: a later additive symbol is not relayed until this relay knows it. */
    private static final String[] KINDS = {"BREAK", "RETURN", "FAILED"};
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One retained event: the compact form always, the full record while it is among the newest {@link #FULL_RAISED_MAX} RAISED. */
    private static final class Held {
        final long seq;
        final boolean raised;
        final long availableMs;
        final long speakUntilMs;
        final String tf;
        final String compact;
        final int compactBytes;
        String full;   // the record as the frame renderer wrote it (no display/speak/tf), null once evicted from the newest 48
        int fullBytes;

        Held(long seq, boolean raised, long availableMs, long speakUntilMs, String tf, String compact, String full) {
            this.seq = seq;
            this.raised = raised;
            this.availableMs = availableMs;
            this.speakUntilMs = speakUntilMs;
            this.tf = tf;
            this.compact = compact;
            this.compactBytes = StudiesRelay.utf8Length(compact);
            this.full = full;
            this.fullBytes = full == null ? 0 : StudiesRelay.utf8Length(full);
        }
    }

    /** The state of one profile: its current session date and the retained events of that session in {@code eventSeq} order. */
    private static final class Prof {
        String sessionDate;
        long high;
        final List<Held> events = new ArrayList<>();
        long evictedThrough;          // the highest eventSeq evicted by the retention bound (any disposition)
        long evictedRaisedSeq;        // the highest RAISED eventSeq evicted, with its availableMs
        long evictedRaisedAvailableMs;

        void clear(String date) {
            sessionDate = date;
            high = 0;
            events.clear();
            evictedThrough = 0;
            evictedRaisedSeq = 0;
            evictedRaisedAvailableMs = 0;
        }

        long low() {
            return events.isEmpty() ? 0 : events.get(0).seq;
        }
    }

    private static final class Store {
        final Prof[] profiles = {new Prof(), new Prof()};
    }

    /** What a hello showed one socket: the date and high-water of each profile (a profile that was not ready shows none). */
    private record HelloSnapshot(String[] date, long[] high, boolean[] ready) {
    }

    /** The per-socket decisions: the hello snapshot the socket resumes against and the resume allowance. */
    private static final class Sock {
        final String[] date = new String[2];
        final long[] high = new long[2];
        final boolean[] ready = new boolean[2];
        final int[] initialCredit = new int[2];
        final boolean[] initialDone = new boolean[2];
        int overflowUsed;
        final List<String[]> owed = new ArrayList<>();   // snapshots a hello bound too late owes this socket
    }

    private static final ThreadLocal<HelloSnapshot> HELLO = new ThreadLocal<>();

    private final long windowMs;
    private final int retained;
    private final long replyBudget;
    private final LongSupplier clock;
    private Store live = new Store();
    private Store staged;
    private final boolean[] ready = new boolean[2];
    private final Map<String, Sock> sockets = new HashMap<>();
    private final AtomicLong framesSent = new AtomicLong();
    private final AtomicLong helloEvents = new AtomicLong();
    private final AtomicLong displayedMarked = new AtomicLong();
    private final AtomicLong cursorResumes = new AtomicLong();
    private final AtomicLong resumeErrors = new AtomicLong();
    private final AtomicLong watermarksIgnored = new AtomicLong();
    private final AtomicLong selfCheckNotForwarded = new AtomicLong();
    private final AtomicLong snapshotsSent = new AtomicLong();

    /** Test seam: the clock a relay built by the service gets; null in production (the system clock). */
    static volatile LongSupplier clockForTest;

    PatternAlertRelay(GatewaySettings settings) {
        this(settings.patternAlertsWindowMs(), settings.patternAlertsRetained(), settings.wsMaxQueuedBytes(), clockForTest != null ? clockForTest : System::currentTimeMillis);
    }

    PatternAlertRelay(long windowMs, int retained, long wsMaxQueuedBytes, LongSupplier clock) {
        super("pattern_alerts");
        this.windowMs = windowMs;
        this.retained = retained;
        this.replyBudget = Math.min(REPLY_MAX_BYTES, wsMaxQueuedBytes / 2);
        this.clock = clock;
    }

    @Override
    boolean handles(String bindingEvent) {
        return profileOf(bindingEvent) >= 0;
    }

    static int profileOf(String bindingEvent) {
        return REGULAR_EVENT.equals(bindingEvent) ? 0 : ESTIMATE_EVENT.equals(bindingEvent) ? 1 : -1;
    }

    static int profileIndex(String profile) {
        return PROFILE[0].equals(profile) ? 0 : PROFILE[1].equals(profile) ? 1 : -1;
    }

    // ── live and recovery ────────────────────────────────────────────────────────────────────────────

    @Override
    synchronized String[] onLive(String bindingEvent, Object value) {
        int p = profileOf(bindingEvent);
        if (p < 0) {
            onDrop();
            return null;
        }
        Held h = ingest(live.profiles[p], value);
        if (h == null) {
            return null;
        }
        long now = clock.getAsLong();
        boolean display = h.raised;
        boolean speak = display && now <= h.speakUntilMs;
        String body = display ? "\"event\":" + fullWith(h, display, speak) : "\"compact\":" + h.compact;
        if (display) {
            displayedMarked.incrementAndGet();
        }
        framesSent.incrementAndGet();
        return new String[] {EVENT[p], "{\"type\":\"event\",\"profile\":\"" + PROFILE[p] + "\",\"display\":" + display + ",\"speak\":" + speak + "," + body + "}"};
    }

    @Override
    synchronized void beginRecovery() {
        staged = new Store();   // history only: it is merged into live at promotion, never a copy of it (a copy would refuse the older history as a regression)
    }

    @Override
    synchronized void onStaged(String bindingEvent, Object value) {
        int p = profileOf(bindingEvent);
        if (p < 0 || staged == null) {
            onDrop();
            return;
        }
        ingest(staged.profiles[p], value);
    }

    /** Merges the history below the captured end offsets into live (older in log order than every live record), makes every profile ready, broadcasts nothing. */
    @Override
    synchronized List<String[]> promote() {
        for (int p = 0; staged != null && p < PROFILE.length; p++) {
            merge(live.profiles[p], staged.profiles[p]);
        }
        staged = null;
        ready[0] = true;
        ready[1] = true;
        return List.of();
    }

    @Override
    int tailRecords(String bindingEvent) {
        return RECOVERY_TAIL_RECORDS;
    }

    /** The record as a retained event, or null (dropped, regression, self-check, a watermark). Validation is the gateway boundary only; nothing is repaired. */
    private Held ingest(Prof prof, Object value) {
        GenericRecord r = recordOrDrop(value);
        if (r == null) {
            return null;
        }
        try {
            String schema = r.getSchema().getFullName();
            if (WATERMARK_SCHEMA.equals(schema)) {
                watermarksIgnored.incrementAndGet();   // the audit read uses them; the relay does not
                return null;
            }
            if (!EVENT_SCHEMA.equals(schema) || !Integer.valueOf(1).equals(r.get("schemaVersion")) || !SERVICE.equals(String.valueOf(r.get("service")))) {
                onDrop();
                return null;
            }
            String date = String.valueOf(r.get("sessionDate"));
            long seq = (Long) r.get("eventSeq");
            String kind = String.valueOf(r.get("kind"));
            String disposition = String.valueOf(r.get("disposition"));
            String timeframe = String.valueOf(r.get("timeframe"));
            int tfSlot = indexOf(TF_ENUM, timeframe);
            if (!DATE.matcher(date).matches() || seq <= 0 || indexOf(KINDS, kind) < 0 || UNKNOWN.equals(disposition) || tfSlot < 0) {
                onDrop();
                return null;
            }
            String json = AvroFrameJson.render(r, RECORD_MAX_BYTES, false);
            if (json == null || StudiesRelay.utf8Length(json) > RECORD_MAX_BYTES) {
                onDrop();
                return null;
            }
            if (prof.sessionDate != null && date.compareTo(prof.sessionDate) < 0) {
                regressed();   // an earlier session than the one followed
                return null;
            }
            if (prof.sessionDate == null || date.compareTo(prof.sessionDate) > 0) {
                prof.clear(date);   // a new session date starts a new sequence space
            }
            if (seq <= prof.high) {
                regressed();   // the rule that makes a staged-recovery or duplicate record produce nothing
                return null;
            }
            prof.high = seq;   // every valid event advances the high-water, retained or not
            accepted();
            if (SELF_CHECK.equals(disposition)) {
                selfCheckNotForwarded.incrementAndGet();   // AL-16: counted, neither retained nor forwarded; it stays in the topic and the audit
                return null;
            }
            boolean raised = RAISED.equals(disposition);
            String tf = TF[tfSlot];
            Held h = new Held(seq, raised, (Long) r.get("availableMs"), (Long) r.get("speakUntilMs"), tf, compact(r, seq, kind, disposition, tf), raised ? json : null);
            prof.events.add(h);
            trim(prof);
            return h;
        } catch (RuntimeException e) {
            onDrop();   // a missing or mistyped field is refused, never repaired
            return null;
        }
    }

    private static String compact(GenericRecord r, long seq, String kind, String disposition, String tf) {
        return "{\"annotationId\":" + quote(r.get("annotationId")) + ",\"kind\":" + quote(kind) + ",\"stance\":" + quote(r.get("stance")) + ",\"eventSeq\":" + seq
                + ",\"disposition\":" + quote(disposition) + ",\"tf\":" + quote(tf) + ",\"patternId\":" + quote(r.get("patternId")) + ",\"patternFamily\":" + quote(r.get("patternFamily"))
                + ",\"breakOrdinal\":" + r.get("breakOrdinal") + ",\"direction\":" + quote(r.get("direction")) + ",\"display\":false,\"speak\":false}";
    }

    private static String quote(Object s) {
        try {
            return JSON.writeValueAsString(String.valueOf(s));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Evicts the oldest events beyond the retention bound and drops the full record of every RAISED event but the newest {@link #FULL_RAISED_MAX}. */
    private void trim(Prof prof) {
        while (prof.events.size() > retained) {
            Held gone = prof.events.remove(0);
            prof.evictedThrough = Math.max(prof.evictedThrough, gone.seq);
            if (gone.raised && gone.seq > prof.evictedRaisedSeq) {
                prof.evictedRaisedSeq = gone.seq;
                prof.evictedRaisedAvailableMs = gone.availableMs;
            }
        }
        int fulls = 0;
        for (int i = prof.events.size() - 1; i >= 0; i--) {
            Held h = prof.events.get(i);
            if (h.full != null && ++fulls > FULL_RAISED_MAX) {
                h.full = null;
                h.fullBytes = 0;
            }
        }
    }

    private void merge(Prof liveProf, Prof history) {
        if (history.sessionDate == null || liveProf.sessionDate != null && history.sessionDate.compareTo(liveProf.sessionDate) < 0) {
            return;
        }
        if (liveProf.sessionDate == null || history.sessionDate.compareTo(liveProf.sessionDate) > 0) {
            liveProf.clear(history.sessionDate);
        }
        java.util.Set<Long> have = new java.util.HashSet<>();
        for (Held h : liveProf.events) {
            have.add(h.seq);
        }
        for (Held h : history.events) {
            if (have.add(h.seq)) {
                liveProf.events.add(h);
            }
        }
        liveProf.events.sort(java.util.Comparator.comparingLong(h -> h.seq));
        liveProf.high = Math.max(liveProf.high, history.high);
        liveProf.evictedThrough = Math.max(liveProf.evictedThrough, history.evictedThrough);
        if (history.evictedRaisedSeq > liveProf.evictedRaisedSeq) {
            liveProf.evictedRaisedSeq = history.evictedRaisedSeq;
            liveProf.evictedRaisedAvailableMs = history.evictedRaisedAvailableMs;
        }
        trim(liveProf);
    }

    // ── rendering ───────────────────────────────────────────────────────────────────────────────────

    /** The full record with the page's three additions: its timeframe in the page's spelling and the display and speak decisions. */
    private static String fullWith(Held h, boolean display, boolean speak) {
        return h.full.substring(0, h.full.length() - 1) + ",\"tf\":\"" + h.tf + "\",\"display\":" + display + ",\"speak\":" + speak + "}";
    }

    private static final int PROFILE_SKELETON_BYTES = 160;

    /** One profile's hello object (also the body of a snapshot): the events whole while they fit {@code cap} bytes, the oldest compact ones giving way first. */
    private String profileJson(int p, long cap, long now) {
        if (!ready[p]) {
            // the staged recovery has not been promoted: this profile's state is not the topic's yet, so the hello shows no events and no high-water and the page does not resume
            return "{\"sessionDate\":null,\"ready\":false,\"eventSeqLow\":0,\"eventSeqHigh\":0,\"truncated\":false,\"events\":[]}";
        }
        Prof prof = live.profiles[p];
        List<Held> source = prof.events;
        List<String> entries = new ArrayList<>(source.size());
        List<Boolean> isFull = new ArrayList<>(source.size());
        long total = 0;
        for (Held h : source) {
            boolean full = h.raised && h.full != null && now <= h.availableMs + windowMs;
            String e = full ? fullWith(h, false, false) : h.compact;
            entries.add(e);
            isFull.add(full);
            total += StudiesRelay.utf8Length(e) + 1;
        }
        boolean trimmed = false;
        long limit = Math.max(0, cap - PROFILE_SKELETON_BYTES);
        while (total > limit && !entries.isEmpty()) {
            int drop = isFull.indexOf(Boolean.FALSE);   // the oldest compact event first
            if (drop < 0) {
                drop = 0;
            }
            total -= StudiesRelay.utf8Length(entries.get(drop)) + 1;
            entries.remove(drop);
            isFull.remove(drop);
            trimmed = true;
        }
        helloEvents.addAndGet(entries.size());
        StringBuilder sb = new StringBuilder("{\"sessionDate\":").append(prof.sessionDate == null ? "null" : "\"" + prof.sessionDate + "\"")
                .append(",\"ready\":").append(ready[p]).append(",\"eventSeqLow\":").append(prof.low()).append(",\"eventSeqHigh\":").append(prof.high)
                .append(",\"truncated\":").append(trimmed || prof.evictedThrough > 0).append(",\"events\":[");
        for (int i = 0; i < entries.size(); i++) {
            sb.append(i == 0 ? "" : ",").append(entries.get(i));
        }
        return sb.append("]}").toString();
    }

    private long sizeOf(int p, long now) {
        long total = PROFILE_SKELETON_BYTES;
        if (!ready[p]) {
            return total;
        }
        for (Held h : live.profiles[p].events) {
            total += (h.raised && h.full != null && now <= h.availableMs + windowMs ? h.fullBytes + 48 : h.compactBytes) + 1;
        }
        return total;
    }

    @Override
    synchronized String helloField(long budget) {
        long now = clock.getAsLong();
        long cap = Math.min(budget, HELLO_PART_BYTES);
        // each profile gets half; what one profile does not need goes to the other
        long need0 = sizeOf(0, now);
        long need1 = sizeOf(1, now);
        long cap0 = cap / 2;
        long cap1 = cap / 2;
        if (need0 + need1 <= cap) {
            cap0 = cap;
            cap1 = cap;
        } else if (need0 <= cap / 2) {
            cap1 = cap - need0;
        } else if (need1 <= cap / 2) {
            cap0 = cap - need1;
        }
        String regular = profileJson(0, cap0, now);
        String estimate = profileJson(1, cap1, now);
        String[] date = {ready[0] ? live.profiles[0].sessionDate : null, ready[1] ? live.profiles[1].sessionDate : null};
        long[] high = {ready[0] ? live.profiles[0].high : 0, ready[1] ? live.profiles[1].high : 0};
        HELLO.set(new HelloSnapshot(date, high, new boolean[] {ready[0], ready[1]}));
        return ",\"alerts\":{\"windowMs\":" + windowMs + ",\"profiles\":{\"regular\":" + regular + ",\"estimate\":" + estimate + "}}";
    }

    // ── per-socket decisions ────────────────────────────────────────────────────────────────────────

    /** Called by the service before it composes a hello on this thread, so no stale snapshot is bound to another socket. */
    static void clearHelloSnapshot() {
        HELLO.remove();
    }

    /**
     * Binds the hello this thread just composed to {@code socketId}: its resume allowance and the date and high-water it will resume against. A profile that was not ready in that
     * hello but is by now (the promotion ran between the composition and this call) owes the socket its snapshot, collected by {@link #takeOwed} once the hello is sent.
     */
    synchronized void bindHello(String socketId) {
        HelloSnapshot h = HELLO.get();
        HELLO.remove();
        if (h == null) {
            return;
        }
        Sock s = new Sock();
        for (int p = 0; p < PROFILE.length; p++) {
            s.date[p] = h.date()[p];
            s.high[p] = h.high()[p];
            s.ready[p] = h.ready()[p];
            s.initialCredit[p] = h.ready()[p] ? 1 : 0;
        }
        sockets.put(socketId, s);
        for (int p = 0; p < PROFILE.length; p++) {
            if (!s.ready[p] && ready[p]) {
                s.owed.add(snapshotFor(s, p));
            }
        }
    }

    /** The snapshots {@link #bindHello} found owed to {@code socketId}, once. */
    synchronized List<String[]> takeOwed(String socketId) {
        Sock s = sockets.get(socketId);
        if (s == null || s.owed.isEmpty()) {
            return List.of();
        }
        List<String[]> out = new ArrayList<>(s.owed);
        s.owed.clear();
        return out;
    }

    synchronized void forget(String socketId) {
        sockets.remove(socketId);
    }

    /**
     * After a promotion: every socket whose hello showed a profile as not ready is sent that profile's snapshot, one socket at a time and in the same step that sets that socket's
     * high-water to the snapshot's, so the stored high-water and the snapshot the page resumes against always match.
     */
    synchronized void afterPromote(BiConsumer<String, String[]> sendToSocket) {
        for (Map.Entry<String, Sock> e : sockets.entrySet()) {
            for (int p = 0; p < PROFILE.length; p++) {
                if (!e.getValue().ready[p] && ready[p]) {
                    sendToSocket.accept(e.getKey(), snapshotFor(e.getValue(), p));
                }
            }
        }
    }

    private String[] snapshotFor(Sock s, int p) {
        Prof prof = live.profiles[p];
        long now = clock.getAsLong();
        s.date[p] = prof.sessionDate;
        s.high[p] = prof.high;
        s.ready[p] = true;
        s.initialCredit[p]++;
        s.initialDone[p] = false;
        snapshotsSent.incrementAndGet();
        framesSent.incrementAndGet();
        String body = profileJson(p, HELLO_PART_BYTES / 2, now);
        return new String[] {EVENT[p], "{\"type\":\"snapshot\",\"profile\":\"" + PROFILE[p] + "\"," + body.substring(1)};
    }

    /**
     * The reply to one {@code alerts-resume} message: ALWAYS one frame, a {@code display} frame or a {@code resume-error}. The request is
     * {@code {"type":"alerts-resume","reason":"initial|overflow","profile":"regular|estimate","sessionDate":"YYYY-MM-DD","cursor":N}}, at most {@link #REQUEST_MAX_BYTES} bytes.
     */
    synchronized String[] resume(String socketId, String payload) {
        int p = 0;
        try {
            if (payload == null || StudiesRelay.utf8Length(payload) > REQUEST_MAX_BYTES) {
                return error(p, "size");
            }
            JsonNode req = JSON.readTree(payload);
            if (req == null || !req.isObject()) {
                return error(p, "request");
            }
            p = profileIndex(req.path("profile").asText(""));
            if (p < 0) {
                return error(0, "profile");
            }
            String reason = req.path("reason").asText("");
            boolean overflow = "overflow".equals(reason);
            if (!overflow && !"initial".equals(reason)) {
                return error(p, "reason");
            }
            Sock s = sockets.get(socketId);
            if (s == null) {
                return error(p, "hello");
            }
            JsonNode c = req.path("cursor");
            if (!c.isIntegralNumber() || !c.canConvertToLong() || c.asLong() < 0) {
                return error(p, "cursor");
            }
            long cursor = c.asLong();
            String date = req.path("sessionDate").asText("");
            Prof prof = live.profiles[p];
            if (!s.ready[p]) {
                return error(p, "not-ready");
            }
            if (!DATE.matcher(date).matches() || !date.equals(s.date[p]) || !date.equals(prof.sessionDate)) {
                return error(p, "session");
            }
            if (overflow) {
                if (!s.initialDone[p] || s.overflowUsed >= OVERFLOW_RETRIES_MAX) {
                    return error(p, "rate");
                }
                s.overflowUsed++;
                s.high[p] = prof.high;   // a retry takes a fresh snapshot: it covers exactly the live frames the page dropped
            } else {
                if (s.initialCredit[p] <= 0) {
                    return error(p, "rate");
                }
                s.initialCredit[p]--;
                s.initialDone[p] = true;
            }
            cursorResumes.incrementAndGet();
            return display(p, s, cursor);
        } catch (java.io.IOException | RuntimeException e) {
            return error(p, "request");
        }
    }

    private String[] error(int p, String why) {
        resumeErrors.incrementAndGet();
        framesSent.incrementAndGet();
        return new String[] {EVENT[p], "{\"type\":\"resume-error\",\"error\":\"" + why + "\"}"};
    }

    /** Every retained RAISED event above the cursor, up to the socket's hello high-water and inside the window, as full records marked for display; a bound that cuts something says so. */
    private String[] display(int p, Sock s, long cursor) {
        Prof prof = live.profiles[p];
        long now = clock.getAsLong();
        long high = s.high[p];
        boolean reset = cursor > high;   // the sequence space restarted (for instance a topic reset on the same date): answer as for cursor 0
        long above = reset ? 0 : cursor;
        List<Held> pick = new ArrayList<>();
        boolean truncated = prof.evictedRaisedSeq > above && prof.evictedRaisedSeq <= high && now <= prof.evictedRaisedAvailableMs + windowMs;
        for (Held h : prof.events) {
            if (!h.raised || h.seq <= above || h.seq > high || now > h.availableMs + windowMs) {
                continue;
            }
            if (h.full == null) {
                truncated = true;   // older than the newest 48 RAISED: only its compact form is held
            } else {
                pick.add(h);
            }
        }
        String head = "{\"type\":\"display\",\"profile\":\"" + PROFILE[p] + "\",\"sessionDate\":\"" + prof.sessionDate + "\",\"reset\":" + reset + ",\"eventSeqHigh\":" + high;
        long room = replyBudget - 192 - head.length();   // envelope, eventSeqLow, truncated and the closing
        long used = 0;
        int first = pick.size();
        for (int i = pick.size() - 1; i >= 0; i--) {   // the newest are kept when the budget cuts
            long size = pick.get(i).fullBytes + 48 + 1;
            if (used + size > room) {
                truncated = true;
                break;
            }
            used += size;
            first = i;
        }
        StringBuilder events = new StringBuilder("[");
        long low = 0;
        for (int i = first; i < pick.size(); i++) {
            Held h = pick.get(i);
            events.append(i == first ? "" : ",").append(fullWith(h, true, now <= h.speakUntilMs));
            low = low == 0 ? h.seq : low;
        }
        events.append(']');
        displayedMarked.addAndGet(pick.size() - first);
        framesSent.incrementAndGet();
        return new String[] {EVENT[p], head + ",\"eventSeqLow\":" + low + ",\"truncated\":" + truncated + ",\"events\":" + events + "}"};
    }

    // ── metrics and test seams ──────────────────────────────────────────────────────────────────────

    @Override
    String metrics() {
        String p = "gateway_pattern_alerts_";
        return super.metrics()
                + counter(p + "frames_sent_total", "Frames the alert relay sent (live frames, snapshots, display replies and resume errors).", framesSent.get())
                + counter(p + "hello_events", "Events rendered into hellos and snapshots so far.", helloEvents.get())
                + counter(p + "displayed_marked_total", "Events sent marked for display (live RAISED frames and the events of display replies).", displayedMarked.get())
                + counter(p + "cursor_resumes_total", "Resume messages answered with a display frame.", cursorResumes.get())
                + counter(p + "resume_errors_total", "Resume messages answered with a resume-error.", resumeErrors.get())
                + counter(p + "watermarks_ignored_total", "Watermark records the relay ignored (the audit read uses them).", watermarksIgnored.get())
                + counter(p + "self_check_not_forwarded_total", "SUPPRESSED_SELF_CHECK events counted and neither retained nor forwarded.", selfCheckNotForwarded.get())
                + counter(p + "snapshots_sent_total", "Per-socket snapshots sent when a profile became ready.", snapshotsSent.get());
    }

    private static String counter(String name, String help, long value) {
        return "# HELP " + name + " " + help + "\n# TYPE " + name + " counter\n" + name + " " + value + "\n";
    }

    synchronized int socketsForTest() {
        return sockets.size();
    }

    synchronized boolean readyForTest(int profile) {
        return ready[profile];
    }

    synchronized long highForTest(int profile) {
        return live.profiles[profile].high;
    }

    synchronized int retainedForTest(int profile) {
        return live.profiles[profile].events.size();
    }

    synchronized int fullsForTest(int profile) {
        int n = 0;
        for (Held h : live.profiles[profile].events) {
            n += h.full != null ? 1 : 0;
        }
        return n;
    }
}
