package app.feedgateway;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.optionsedge.contracts.volpremium.EarlyWarning;
import com.optionsedge.contracts.volpremium.IvRvReading;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The gateway's copy of the CURRENT vol-premium session, per symbol: every IV-vs-realised
 * observation of that session (Gate-1 §39.7, VP-338, VP-348) and every early-warning transition of
 * it (§40.12, §40.13, VP-366), held in the order a chart draws them.
 *
 * <p><b>Why a session and not a current value.</b> The gateway used to keep ONE reading per
 * {@code SYMBOL|sessionDate}, which was all a card needed. The chart needs the whole intraday line,
 * and the producer now keys every observation {@code SYMBOL|sessionDate|frameSeq} precisely so that a
 * compacted topic keeps every one of them. A cache that kept only the newest would hand a late joiner
 * one point and call it a session.
 *
 * <p><b>Identity is {@code (frameSeq, measurementEpochMs)}</b>, which is the contract's own rule
 * (IvRvReading's constructor states it): a later frame with the same ordinal AND the same epoch is a
 * newer reading of the same window and REPLACES it; the same ordinal on a different epoch is a
 * different measurement and stays a separate point, which is where the line breaks. Keyed by the
 * ordinal alone, a restart inside one cadence window would overwrite a point measured on another
 * basis and draw a straight line across the restart — the exact misreading the epoch exists to
 * prevent.
 *
 * <p><b>Order and freshness are judged PER OBSERVATION</b>, because that is now the unit a record
 * describes. The offset gate that used to guard the one current reading guards each position: a
 * higher offset may replace it (never with an earlier event time), a lower or equal one is a replay
 * and is refused — unless it carries a strictly newer event time, which no incarnation of this
 * single-partition topic can produce except a recreated one. Every event-time decision reads the
 * clock the caller passes, never the wall clock directly, so a fixed-date fixture is judged at the
 * time it describes.
 *
 * <p><b>Retention is the session, bounded by the contract's own rule</b>
 * ({@link IvRvReading#requireInstantInSession}): a session is current from its ET midnight until
 * {@link IvRvReading#MAX_AFTER_MIDNIGHT_MS} past the midnight that ends it. The old five-minute TTL
 * cannot apply per observation — it would keep five minutes of a six-and-a-half-hour line — and
 * applied to the series it would blank the whole chart five minutes after the close. Liveness of the
 * NEWEST point is the consumer's call from {@code eventTimeMs} and {@code frameCadenceMs}, which the
 * contract documents as exactly what those fields are for.
 *
 * <p><b>Memory is bounded explicitly and refused loudly.</b> See {@link #MAX_OBSERVATIONS_PER_SESSION},
 * {@link #MAX_WARNINGS_PER_SESSION} and {@link #MAX_SYMBOLS}: past a cap a record is REFUSED, counted
 * under its reason and logged once per session. Nothing already held is ever evicted to make room,
 * because a silently truncated session is a chart that looks complete and is not.
 *
 * <p>Every method is synchronized on this store, which is a LEAF lock: nothing is called while it is
 * held, so it nests safely inside the gateway's emit lock and its instance monitor.
 */
final class VolPremiumSessionStore {

    /** The two wire events this store feeds; both are delivered as standalone, never-coalesced frames. */
    static final String EVENT_OBSERVATION = "vol-premium-ivrv";
    static final String EVENT_WARNING = "vol-premium-warning";

    /** 09:30–16:00 ET: the regular session the producer's cadence runs over (§39 item 1). */
    static final long RTH_SESSION_MS = 6L * 60L * 60_000L + 30L * 60_000L;

    /**
     * Observations held per session: one per ordinal of a FULL regular session at the SMALLEST cadence
     * the contract admits ({@link IvRvReading#MIN_FRAME_CADENCE_MS}), i.e. 23,400,000 / 250 = 93,600.
     *
     * <p>Sized from the contract rather than from the cadence the producer runs today (5 s, 4,680 per
     * session) because the cadence is a producer setting the gateway does not see in advance, and a cap
     * a legitimate faster producer could hit would refuse the afternoon of a real session.
     *
     * <p>What that bounds, stated as bytes: each record is refused above the contract's
     * {@link IvRvReading#MAX_RECORD_BYTES} (64 KiB), so the hard ceiling is cap × 64 KiB per symbol. The
     * engine's real records are about 5.7 KB, which is ~27 MB for a 5 s session and ~530 MB for a full
     * session at the 250 ms floor. The resident size is exported as {@code gateway_vol_premium_cached_bytes}
     * so the real figure is visible rather than inferred.
     */
    static final int MAX_OBSERVATIONS_PER_SESSION = (int) ((RTH_SESSION_MS
            + IvRvReading.MIN_FRAME_CADENCE_MS - 1L) / IvRvReading.MIN_FRAME_CADENCE_MS);

    /**
     * Warning transitions held per session: the same number as observations. §40.9 emits a record only
     * on a MATERIAL transition, so a session whose transitions outnumber its observation ordinals is
     * spam or a producer fault, not evidence, and is refused past this point rather than held.
     */
    static final int MAX_WARNINGS_PER_SESSION = MAX_OBSERVATIONS_PER_SESSION;

    /**
     * Distinct series held at once. The producer publishes SPX; the symbol is a payload string bounded
     * only in length, so without a cap a producer fault minting symbols would grow this map for ever.
     */
    static final int MAX_SYMBOLS = 8;

    /**
     * Clock-skew allowance for a record's own event time. A frame stamped further ahead of the gateway
     * than this is a clock fault, not freshness: it would sit on the chart as the newest point and, as
     * the first record of a session, could roll the series over to a date that has not started.
     */
    static final long MAX_FUTURE_SKEW_MS = 60_000L;

    /** Why a record was not admitted; each is counted so a refusal is never silent. */
    enum Refusal {
        MALFORMED, OVERSIZE, KEY_MISMATCH, FUTURE_EVENT_TIME, SESSION_NOT_CURRENT, OLDER_SESSION,
        FOREIGN_PARTITION, REPLAYED_OFFSET, EVENT_TIME_REGRESSION, SESSION_CAP, SYMBOL_CAP
    }

    /** Which of the two streams a record belongs to. */
    enum Stream {
        OBSERVATION("ivrv"), WARNING("warning");

        final String label;

        Stream(String label) {
            this.label = label;
        }
    }

    /**
     * The position of one record in the replay order: series, then session, then observations before
     * warnings, then {@code (frameSeq, measurementEpochMs)} for an observation or
     * {@code (frameSeq, episodeId)} for a warning. ONE total order, so a replay cursor, a live
     * suppression decision and a TreeMap key are the same comparison and cannot disagree.
     */
    record Position(String seriesKey, String sessionDate, int phase, long frameSeq, long epochMs,
                    String episodeId) implements Comparable<Position> {
        static final int OBSERVATIONS = 0;
        static final int WARNINGS = 1;

        @Override
        public int compareTo(Position o) {
            int c = seriesKey.compareTo(o.seriesKey);
            if (c != 0) {
                return c;
            }
            // ISO yyyy-MM-dd, which the contract enforces as a grammar, sorts chronologically.
            c = sessionDate.compareTo(o.sessionDate);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(phase, o.phase);
            if (c != 0) {
                return c;
            }
            c = Long.compare(frameSeq, o.frameSeq);
            if (c != 0) {
                return c;
            }
            c = Long.compare(epochMs, o.epochMs);
            if (c != 0) {
                return c;
            }
            return episodeId.compareTo(o.episodeId);
        }

        String event() {
            return phase == OBSERVATIONS ? EVENT_OBSERVATION : EVENT_WARNING;
        }
    }

    /** The outcome of offering one record: a position when admitted, a reason when not. */
    record Admission(Position position, boolean recreatedTopic, Refusal refusal) {
        boolean admitted() {
            return refusal == null;
        }
    }

    /** The next record a replay should deliver. {@code json} is the producer's bytes, verbatim. */
    record Item(Position position, String json) {
    }

    /** One session as a machine reads it (VP-346): verbatim records, in replay order. */
    record Snapshot(String sessionDate, List<String> observations, List<String> warnings) {
    }

    private static final class Entry {
        final String json;
        final long eventTimeMs;
        final long offset;
        final int bytes;
        /** Greatest offset already broadcast for this position; -1 when none. */
        long broadcastFence;

        Entry(String json, long eventTimeMs, long offset, int bytes, long broadcastFence) {
            this.json = json;
            this.eventTimeMs = eventTimeMs;
            this.offset = offset;
            this.bytes = bytes;
            this.broadcastFence = broadcastFence;
        }
    }

    private static final class Session {
        final String sessionDate;
        /** The ONE partition this session's records may come from — the topic is single-partition. */
        final int partition;
        final TreeMap<Position, Entry> observations = new TreeMap<>();
        final TreeMap<Position, Entry> warnings = new TreeMap<>();
        long bytes;
        boolean observationCapLogged;
        boolean warningCapLogged;

        Session(String sessionDate, int partition) {
            this.sessionDate = sessionDate;
            this.partition = partition;
        }

        TreeMap<Position, Entry> of(Stream stream) {
            return stream == Stream.OBSERVATION ? observations : warnings;
        }
    }

    /**
     * STRICTER than the shared mapper, for the reasons the gateway already paid for once.
     *
     * <p>Jackson fills a missing record component with the Java default and turns an explicit null for
     * a primitive into the same default, so a payload with a count deleted — or nulled — deserialises to
     * a valid-looking zero that the browser refuses outright. FAIL_ON_MISSING_CREATOR_PROPERTIES and
     * FAIL_ON_NULL_FOR_PRIMITIVES close those two doors; the legitimately nullable boxed fields are
     * untouched and are validated against each other by the record itself.
     *
     * <p>FAIL_ON_TRAILING_TOKENS is new with the session relay, and is here because the record is
     * forwarded VERBATIM inside an envelope and a REST array: a value that parsed as one object followed
     * by anything else would make the envelope around it malformed JSON for every client that received
     * it, while the gateway believed it had delivered a valid reading.
     */
    private static final ObjectMapper STRICT = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final ObjectReader OBSERVATION_READER = STRICT.readerFor(IvRvReading.class);
    private static final ObjectReader WARNING_READER = STRICT.readerFor(EarlyWarning.class);

    private final int maxObservations;
    private final int maxWarnings;
    private final int maxSymbols;
    private final TreeMap<String, Session> sessions = new TreeMap<>();
    private final AtomicLong[][] refusals = new AtomicLong[Stream.values().length][Refusal.values().length];
    private boolean symbolCapLogged;

    VolPremiumSessionStore() {
        this(MAX_OBSERVATIONS_PER_SESSION, MAX_WARNINGS_PER_SESSION, MAX_SYMBOLS);
    }

    /** Test seam: the same store with small caps, so a cap refusal is reachable in a unit test. */
    VolPremiumSessionStore(int maxObservations, int maxWarnings, int maxSymbols) {
        this.maxObservations = maxObservations;
        this.maxWarnings = maxWarnings;
        this.maxSymbols = maxSymbols;
        for (AtomicLong[] row : refusals) {
            for (int i = 0; i < row.length; i++) {
                row[i] = new AtomicLong();
            }
        }
    }

    /**
     * Offer one IV/RV observation from the ivrv topic.
     *
     * <p>The WHOLE contract is enforced — the record is deserialised through {@link IvRvReading}, whose
     * constructor is the producer's own validation — and then the Kafka key must be EXACTLY
     * {@link IvRvReading#observationKey} of the parsed value. The key is what compaction acts on: a
     * record keyed for one observation carrying another's body would take the first one's slot on the
     * topic while the gateway filed it under the second, and the two histories would disagree with
     * nothing failing.
     *
     * <p>schemaVersion 1 is refused, by the contract's constructor: the producer publishes only v2 now,
     * and a v1 record is keyed {@code SYMBOL|sessionDate}, which can never equal an observation key.
     * Accepting it would put one reading under a key that stands for a whole session.
     */
    synchronized Admission acceptObservation(String source, String recordKey, int partition, long offset,
                                             String json, long nowMs) {
        Integer bytes = wireBytes(json);
        if (bytes == null) {
            return refuse(Stream.OBSERVATION, json == null || json.isBlank() ? Refusal.MALFORMED : Refusal.OVERSIZE);
        }
        IvRvReading reading;
        try {
            reading = OBSERVATION_READER.readValue(json);
        } catch (java.io.IOException | RuntimeException invalid) {
            return refuse(Stream.OBSERVATION, Refusal.MALFORMED);
        }
        if (!IvRvReading.observationKey(reading.symbol(), reading.sessionDate(), reading.frameSeq())
                .equals(recordKey)) {
            return refuse(Stream.OBSERVATION, Refusal.KEY_MISMATCH);
        }
        if (reading.eventTimeMs() > nowMs + MAX_FUTURE_SKEW_MS) {
            return refuse(Stream.OBSERVATION, Refusal.FUTURE_EVENT_TIME);
        }
        Position position = new Position(source + "|" + reading.symbol(), reading.sessionDate(),
                Position.OBSERVATIONS, reading.frameSeq(), reading.measurementEpochMs(), "");
        return admit(Stream.OBSERVATION, position, json, bytes, reading.eventTimeMs(), partition, offset, nowMs);
    }

    /**
     * Offer one early-warning transition from the warnings topic. Validated through
     * {@link EarlyWarning} — whose constructor re-derives the components, strength and state from the
     * raw evidence under the hashed parameter set — and the Kafka key must be EXACTLY its
     * {@code episodeId}, which is what the topic is keyed by (VolPremiumTopics.WARNINGS).
     *
     * <p>A transition is identified by {@code (frameSeq, episodeId)}: one episode is evaluated once per
     * observation, so two records with that pair are the same transition delivered twice and the later
     * offset replaces the earlier. Warnings share the session with the observations, so either stream
     * can roll it over and neither may bring an older one back.
     *
     * <p>The byte bound is the observation's {@link IvRvReading#MAX_RECORD_BYTES}. The warnings contract
     * states no wire bound of its own; this one is the gateway's, applied so the warning cap bounds
     * bytes as well as records.
     */
    synchronized Admission acceptWarning(String source, String recordKey, int partition, long offset,
                                         String json, long nowMs) {
        Integer bytes = wireBytes(json);
        if (bytes == null) {
            return refuse(Stream.WARNING, json == null || json.isBlank() ? Refusal.MALFORMED : Refusal.OVERSIZE);
        }
        EarlyWarning warning;
        try {
            warning = WARNING_READER.readValue(json);
        } catch (java.io.IOException | RuntimeException invalid) {
            return refuse(Stream.WARNING, Refusal.MALFORMED);
        }
        if (!warning.episodeId().equals(recordKey)) {
            return refuse(Stream.WARNING, Refusal.KEY_MISMATCH);
        }
        if (warning.asOfMs() > nowMs + MAX_FUTURE_SKEW_MS) {
            return refuse(Stream.WARNING, Refusal.FUTURE_EVENT_TIME);
        }
        Position position = new Position(source + "|" + warning.symbol(), warning.sessionDate(),
                Position.WARNINGS, warning.frameSeq(), 0L, warning.episodeId());
        return admit(Stream.WARNING, position, json, bytes, warning.asOfMs(), partition, offset, nowMs);
    }

    private Admission admit(Stream stream, Position position, String json, int bytes, long eventTimeMs,
                            int partition, long offset, long nowMs) {
        // A record whose session has ended — or, within the skew allowance, not begun — is not part of
        // any session this store holds. Refused rather than cached, so a backlog read back across
        // midnight cannot resurrect yesterday's chart.
        if (!sessionCurrent(position.sessionDate(), nowMs)) {
            return refuse(stream, Refusal.SESSION_NOT_CURRENT);
        }
        Session session = sessions.get(position.seriesKey());
        if (session != null) {
            int cmp = position.sessionDate().compareTo(session.sessionDate);
            if (cmp < 0) {
                // A newer session has begun; an older one never comes back.
                return refuse(stream, Refusal.OLDER_SESSION);
            }
            if (cmp == 0 && session.partition != partition) {
                // Fail closed, never reorder: the topic is single-partition by construction, and records
                // of one session interleaved from two partitions have no order to replay them in.
                return refuse(stream, Refusal.FOREIGN_PARTITION);
            }
        }
        if (session == null || position.sessionDate().compareTo(session.sessionDate) > 0) {
            if (session == null && sessions.size() >= maxSymbols) {
                if (!symbolCapLogged) {
                    symbolCapLogged = true;
                    System.out.println("WARN vol-premium: refusing series " + position.seriesKey() + " — "
                            + maxSymbols + " series are already held (MAX_SYMBOLS); counted as "
                            + "gateway_vol_premium_refused_total{reason=\"SYMBOL_CAP\"}");
                }
                return refuse(stream, Refusal.SYMBOL_CAP);
            }
            // A NEWER session replaces the whole series, observations and warnings together: a chart is
            // one session, and yesterday's transitions over today's line would be evidence about a
            // different day. The contract already refuses a session dated after its own event time, and
            // the skew bound above refuses an event time from the future, so a poison date cannot get
            // here to force a rollover.
            session = new Session(position.sessionDate(), partition);
            sessions.put(position.seriesKey(), session);
        }
        TreeMap<Position, Entry> held = session.of(stream);
        Entry previous = held.get(position);
        boolean recreatedTopic = false;
        long fence = -1L;
        if (previous != null) {
            if (offset > previous.offset) {
                // A later write of the SAME position: a replacement, which is the contract's rule for a
                // repeated ordinal on the same epoch. Its event time may equal the stored one (a
                // correction) but not precede it — this producer's stream time never runs backwards, so
                // an earlier one is corrupt, and the browser refuses it too.
                if (eventTimeMs < previous.eventTimeMs) {
                    return refuse(stream, Refusal.EVENT_TIME_REGRESSION);
                }
                fence = previous.broadcastFence;
            } else if (eventTimeMs > previous.eventTimeMs) {
                // AT OR BELOW the stored offset yet strictly NEWER: no incarnation of a single-partition
                // topic whose producer never runs its clock backwards can produce that, but a deleted
                // and recreated topic does, on its first records. Admitted as the recovery it is, with
                // the fence reset — a fence kept from the old incarnation would suppress exactly the
                // frames this admits. Equal offsets count too: an old incarnation that reached only
                // offset 0 meets a new one whose first record is offset 0 as well.
                recreatedTopic = true;
            } else {
                return refuse(stream, Refusal.REPLAYED_OFFSET);
            }
        } else if (held.size() >= (stream == Stream.OBSERVATION ? maxObservations : maxWarnings)) {
            boolean first = stream == Stream.OBSERVATION ? !session.observationCapLogged : !session.warningCapLogged;
            if (first) {
                if (stream == Stream.OBSERVATION) {
                    session.observationCapLogged = true;
                } else {
                    session.warningCapLogged = true;
                }
                System.out.println("WARN vol-premium: session " + position.seriesKey() + " "
                        + position.sessionDate() + " already holds " + held.size() + " " + stream.label
                        + " records, the declared cap; REFUSING further records rather than dropping "
                        + "held ones (counted as gateway_vol_premium_refused_total{stream=\""
                        + stream.label + "\",reason=\"SESSION_CAP\"})");
            }
            return refuse(stream, Refusal.SESSION_CAP);
        }
        held.put(position, new Entry(json, eventTimeMs, offset, bytes, fence));
        session.bytes += bytes - (previous == null ? 0 : previous.bytes);
        return new Admission(position, recreatedTopic, null);
    }

    /**
     * Exactly-once, in-order live delivery for one position across the two consumers that read the
     * same partition: whichever admits an offset first broadcasts it, and an offset at or below one
     * already broadcast for that position never is. The fence lives ON the entry, so it is dropped with
     * it — at rollover, at the session's end — and can never outlive the record it fences into a floor
     * that a recreated topic's offsets would have to climb back over.
     */
    synchronized boolean claimBroadcast(Position position, long offset) {
        Entry entry = entry(position);
        if (entry == null || offset <= entry.broadcastFence) {
            return false;
        }
        entry.broadcastFence = offset;
        return true;
    }

    /**
     * The first held record strictly after {@code after} in replay order ({@code null}: from the
     * start), skipping any session that is no longer current. Reads the CURRENT value of each position,
     * so a replacement that lands before a replay reaches its position is the version that is replayed.
     */
    synchronized Item next(Position after, long nowMs) {
        SortedMap<String, Session> from = after == null ? sessions : sessions.tailMap(after.seriesKey());
        for (Map.Entry<String, Session> e : from.entrySet()) {
            Session session = e.getValue();
            if (!sessionCurrent(session.sessionDate, nowMs)) {
                continue;
            }
            Map.Entry<Position, Entry> found = after == null
                    ? session.observations.firstEntry() : session.observations.higherEntry(after);
            if (found == null) {
                found = after == null ? session.warnings.firstEntry() : session.warnings.higherEntry(after);
            }
            if (found != null) {
                return new Item(found.getKey(), found.getValue().json);
            }
        }
        return null;
    }

    /** One series' current session, verbatim and in replay order; empty when there is none. */
    synchronized Snapshot snapshot(String seriesKey, long nowMs) {
        Session session = sessions.get(seriesKey);
        if (session == null || !sessionCurrent(session.sessionDate, nowMs)) {
            return new Snapshot(null, List.of(), List.of());
        }
        List<String> observations = new ArrayList<>(session.observations.size());
        for (Entry entry : session.observations.values()) {
            observations.add(entry.json);
        }
        List<String> warnings = new ArrayList<>(session.warnings.size());
        for (Entry entry : session.warnings.values()) {
            warnings.add(entry.json);
        }
        return new Snapshot(session.sessionDate, List.copyOf(observations), List.copyOf(warnings));
    }

    /** Drops every session that is no longer current. Returns how many were dropped. */
    synchronized int purge(long nowMs) {
        int before = sessions.size();
        sessions.values().removeIf(session -> !sessionCurrent(session.sessionDate, nowMs));
        if (sessions.size() < maxSymbols) {
            symbolCapLogged = false;
        }
        return before - sessions.size();
    }

    long refusals(Stream stream, Refusal reason) {
        return refusals[stream.ordinal()][reason.ordinal()].get();
    }

    synchronized int heldObservations() {
        int n = 0;
        for (Session s : sessions.values()) {
            n += s.observations.size();
        }
        return n;
    }

    synchronized int heldWarnings() {
        int n = 0;
        for (Session s : sessions.values()) {
            n += s.warnings.size();
        }
        return n;
    }

    synchronized long heldBytes() {
        long n = 0L;
        for (Session s : sessions.values()) {
            n += s.bytes;
        }
        return n;
    }

    /** Prometheus text for the store: refusals by stream and reason, and what is resident. */
    String metricsText() {
        StringBuilder sb = new StringBuilder()
                .append("# HELP gateway_vol_premium_refused_total vol-premium records NOT admitted to the ")
                .append("session cache, by stream and reason. SESSION_CAP and SYMBOL_CAP are the declared ")
                .append("memory bounds refusing; the rest are contract, key, ordering and session gates.\n")
                .append("# TYPE gateway_vol_premium_refused_total counter\n");
        for (Stream stream : Stream.values()) {
            for (Refusal reason : Refusal.values()) {
                sb.append("gateway_vol_premium_refused_total{stream=\"").append(stream.label)
                        .append("\",reason=\"").append(reason.name()).append("\"} ")
                        .append(refusals(stream, reason)).append('\n');
            }
        }
        sb.append("# HELP gateway_vol_premium_cached_observations IV/RV observations held for the current sessions.\n")
                .append("# TYPE gateway_vol_premium_cached_observations gauge\n")
                .append("gateway_vol_premium_cached_observations ").append(heldObservations()).append('\n')
                .append("# HELP gateway_vol_premium_cached_warnings Early-warning transitions held for the current sessions.\n")
                .append("# TYPE gateway_vol_premium_cached_warnings gauge\n")
                .append("gateway_vol_premium_cached_warnings ").append(heldWarnings()).append('\n')
                .append("# HELP gateway_vol_premium_cached_bytes Serialised bytes of every held vol-premium record.\n")
                .append("# TYPE gateway_vol_premium_cached_bytes gauge\n")
                .append("gateway_vol_premium_cached_bytes ").append(heldBytes()).append('\n');
        return sb.toString();
    }

    /**
     * Whether {@code nowMs} still belongs to {@code sessionDate}, by the contract's ONE rule tying an
     * instant to a session — the same rule every record's own event time already met on the way in.
     */
    static boolean sessionCurrent(String sessionDate, long nowMs) {
        try {
            IvRvReading.requireInstantInSession("gateway clock", sessionDate, nowMs);
            return true;
        } catch (IllegalArgumentException notThisSession) {
            return false;
        }
    }

    private Entry entry(Position position) {
        Session session = sessions.get(position.seriesKey());
        if (session == null || !session.sessionDate.equals(position.sessionDate())) {
            return null;
        }
        return (position.phase() == Position.OBSERVATIONS ? session.observations : session.warnings).get(position);
    }

    /** UTF-8 length when non-blank and within {@link IvRvReading#MAX_RECORD_BYTES}; otherwise null. */
    private static Integer wireBytes(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try {
            IvRvReading.requireWithinWire(bytes);
        } catch (IllegalArgumentException oversize) {
            return null;
        }
        return bytes.length;
    }

    private Admission refuse(Stream stream, Refusal reason) {
        refusals[stream.ordinal()][reason.ordinal()].incrementAndGet();
        return new Admission(null, false, reason);
    }
}
