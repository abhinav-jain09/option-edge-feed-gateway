package app.feedgateway;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.optionsedge.contracts.volpremium.EarlyWarning;
import com.optionsedge.contracts.volpremium.EarlyWarningState;
import com.optionsedge.contracts.volpremium.IvRvReading;
import com.optionsedge.contracts.volpremium.IvRvReadingV1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

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
 * <p><b>Both wire versions</b> (the rollout bridge: vol-premium runbook, "Rollout sequence", step 2 until
 * step 6). schemaVersion 1 comes from the old realised-only producer, keyed {@code SYMBOL|sessionDate}.
 * schemaVersion 2 comes from the engine, keyed per observation. Each is admitted on its OWN contract and key
 * rule, and both are then held as ONE series. {@link #acceptObservation} states the rules and the
 * transitional limits.
 *
 * <p><b>An observation is identified by {@code (frameSeq, measurementEpochMs)}</b>, which is the
 * contract's own rule (the constructors of IvRvReading and IvRvReadingV1 both state it, and both
 * versions define both fields identically): a later frame with the same ordinal AND
 * the same epoch is a newer reading of the same window and REPLACES it; the same ordinal on a different
 * epoch is a different measurement and stays a separate point, which is where the line breaks.
 *
 * <p><b>A warning transition is identified by {@code (episodeId, frameSeq, asOfMs, previousState,
 * state)}</b> — see {@link #acceptWarning} for why the first three are not enough.
 *
 * <p><b>Order and freshness are judged PER RECORD POSITION.</b> A higher offset may replace a position
 * (never with an earlier event time); a lower or equal one is a replay and is refused — unless it
 * carries a strictly newer event time, which no incarnation of this single-partition topic can produce
 * except a recreated one. Every event-time decision reads the clock the caller passes, never the wall
 * clock directly, so a fixed-date fixture is judged at the time it describes.
 *
 * <p><b>Retention is the session, bounded by the contract's own rule</b>
 * ({@link IvRvReading#requireInstantInSession}): a session is current from its ET midnight until
 * {@link IvRvReading#MAX_AFTER_MIDNIGHT_MS} past the midnight that ends it. Liveness of the NEWEST point
 * is the consumer's call from {@code eventTimeMs} and {@code frameCadenceMs}.
 *
 * <p><b>Memory is an ENFORCED byte budget</b>, not a record count — see {@link #SERIES_BUDGET_BYTES}
 * for the supported envelope and its arithmetic, and {@link #requireBudgetFitsHeap} for the boot
 * check against the heap the JVM was actually given.
 *
 * <p>Every method is synchronized on this store, which is a LEAF lock: nothing is called while it is
 * held except the pure function a caller hands {@link #nextChanged}, so it nests safely inside the
 * gateway's emit lock and its instance monitor.
 */
final class VolPremiumSessionStore {

    /** The two wire events this store feeds; both are delivered as standalone, never-coalesced frames. */
    static final String EVENT_OBSERVATION = "vol-premium-ivrv";
    static final String EVENT_WARNING = "vol-premium-warning";

    /** 09:30–16:00 ET: the regular session the producer's cadence runs over (§39 item 1). */
    static final long RTH_SESSION_MS = 6L * 60L * 60_000L + 30L * 60_000L;

    /** The producer's configured cadence ({@code frameCadenceMs} on every record it publishes). */
    static final long REFERENCE_FRAME_CADENCE_MS = 5_000L;

    /** 23,400,000 / 5,000: the observations of one full regular session at the producer's cadence. */
    static final int REFERENCE_OBSERVATIONS_PER_SESSION = (int) (RTH_SESSION_MS / REFERENCE_FRAME_CADENCE_MS);

    /**
     * Distinct series held at once. The producer publishes SPX; one more is room for a second
     * underlying or a rename overlap. The symbol is a payload string bounded only in length, so without
     * a cap a producer fault minting symbols would grow this map for ever.
     */
    static final int MAX_SYMBOLS = 2;

    /**
     * Retained-heap budget of ONE series' current session — observations and warnings together —
     * ENFORCED on every admission ({@link Refusal#SESSION_BUDGET}).
     *
     * <p><b>The supported envelope, and its arithmetic.</b> Each record is charged an upper bound of
     * the heap it retains ({@link #charge}): the JSON string as the JVM stores it plus a fixed
     * per-record overhead for the entry, its tree node and its position key. The engine's real
     * observations are 5,300–5,686 characters (the committed full-session stream), so the largest is
     * charged {@code 256 + 32 + align8(24 + 5,686) = 6,000} bytes; its warnings are 978–1,052
     * characters, charged at most {@code 568 + 32 + align8(24 + 1,052) = 1,680} bytes.
     * <ul>
     *   <li>Observations of a full 09:30–16:00 session at the producer's 5 s cadence:
     *       {@code 4,680 × 6,000 = 28,080,000} bytes (26.8 MiB).</li>
     *   <li>Budget: {@code 48 MiB = 50,331,648} bytes, leaving {@code 22,251,648} bytes for everything
     *       else the session legitimately carries — every extra measurement epoch (one more point per
     *       restart) and every warning transition, any number of types per ordinal. That is 13,245
     *       transitions at 1,680 bytes, against the 15 the engine emitted in the committed 31 minutes
     *       (≈190 for a whole session at that rate), or 3,708 more observations: a whole session
     *       down to a {@code 23,400,000 / 8,388 = 2,790} ms cadence with no warnings at all.</li>
     *   <li>Total: {@code MAX_SYMBOLS × 48 MiB = 96 MiB}, asserted at boot to fit in
     *       {@code 1/}{@value #HEAP_FRACTION_DENOMINATOR} of {@code Runtime.maxMemory()}. Production
     *       runs {@code -Xms256m -Xmx1536m} (JAVA_TOOL_OPTIONS in every feed-gateway overlay), so the
     *       bound is 192 MiB and the store can hold at most 6.25% of the heap.</li>
     *   <li>A schemaVersion 1 record (the rollout bridge) is charged by the SAME formula. It carries
     *       sixteen scalar fields and no trends or warnings, so its charge is a fraction of a v2 record's for
     *       the same window (VolPremiumSessionStoreTest measures both). Any mix of v1 and v2 points
     *       therefore fits wherever the same number of v2 points does.</li>
     * </ul>
     *
     * <p><b>Beyond the envelope the store fails CLOSED and LOUD, never by truncation.</b> A record that
     * would take its session past the budget is refused; nothing already held is evicted to make room.
     * The session is marked incomplete from that moment on: an ERROR line once per session, the
     * {@code gateway_vol_premium_refused_total{reason="SESSION_BUDGET"}} counter, the
     * {@code gateway_vol_premium_sessions_over_budget} gauge, and {@code retention.complete=false} with
     * the refused count on the REST page, so neither an operator nor a machine can mistake the held
     * prefix for the whole session. The alternatives both break §40.1 worse: evicting the oldest points
     * discards history (§40.1: "the history is never discarded") and leaves a chart that looks complete
     * and is not; forwarding records live without retaining them gives connected sockets a session
     * that no late joiner and no REST reader can ever reproduce. Refusing keeps one session, the same
     * for every reader, with its incompleteness stated. The latest point then stops advancing, which is
     * exactly what the page's own cadence rule reports as stale — the gateway does not present a
     * point it could not also replay.
     */
    static final long SERIES_BUDGET_BYTES = 48L << 20;

    /** Every series together: the figure the boot check holds against the heap. */
    static final long TOTAL_BUDGET_BYTES = MAX_SYMBOLS * SERIES_BUDGET_BYTES;

    /** The store may use at most {@code 1/8} of {@code Runtime.maxMemory()}; the gateway has other caches. */
    static final int HEAP_FRACTION_DENOMINATOR = 8;

    /**
     * Upper bound of the fixed objects one held record adds besides its JSON string: the entry
     * (json, event time, offset, charge, fence, admission sequence), the TreeMap node that holds it,
     * and the position key. 56 + 40 + 48 = 144 bytes with compressed oops and class pointers, 64 × 3
     * = 192 without them; 256 covers either. The series key and session date inside the position are
     * the session's own instances, never a copy per record.
     */
    static final long RECORD_OVERHEAD_BYTES = 256L;

    /**
     * A warning's position additionally holds its episode id, a string of at most
     * {@link EarlyWarning#MAX_EPISODE_ID_CHARS} characters: at most {@code 32 + align8(24 + 2 × 128) = 312}
     * bytes, which is what is charged for every warning whatever its id.
     */
    static final long WARNING_OVERHEAD_BYTES = RECORD_OVERHEAD_BYTES + 312L;

    /** One session's own structures (the session, its two maps, its key strings), charged when it is created. */
    static final long SERIES_OVERHEAD_BYTES = 1_024L;

    /** Upper bounds, for either oop layout, of a String object and of an array header. */
    static final long STRING_OBJECT_BYTES = 32L;
    static final long ARRAY_HEADER_BYTES = 24L;

    /**
     * Whether the JVM stores a Latin-1 string one byte per character. Read once; a JVM that does not
     * say so is charged two bytes per character, which is what it would then spend.
     */
    static final boolean COMPACT_STRINGS = readCompactStrings();

    /**
     * Clock-skew allowance for a record's own event time. A frame stamped further ahead of the gateway
     * than this is a clock fault, not freshness: it would sit on the chart as the newest point and, as
     * the first record of a session, could roll the series over to a date that has not started.
     */
    static final long MAX_FUTURE_SKEW_MS = 60_000L;

    /** Why a record was not admitted; each is counted so a refusal is never silent. */
    enum Refusal {
        MALFORMED,
        /** An observation whose schemaVersion is missing, not a JSON integer, or neither version this gateway knows. */
        SCHEMA_VERSION,
        OVERSIZE, KEY_MISMATCH, FUTURE_EVENT_TIME, SESSION_NOT_CURRENT, OLDER_SESSION,
        FOREIGN_PARTITION, REPLAYED_OFFSET, EVENT_TIME_REGRESSION, SESSION_BUDGET, SYMBOL_CAP
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
     * warnings, then {@code (frameSeq, epochMs)} — the measurement epoch for an observation, the
     * transition's own instant {@code asOfMs} for a warning — then the episode id and the transition.
     * ONE total order, so a replay cursor, a live routing decision and a TreeMap key are the same
     * comparison and cannot disagree.
     *
     * @param transition 0 for an observation; for a warning, {@link #transitionRank}
     */
    record Position(String seriesKey, String sessionDate, int phase, long frameSeq, long epochMs,
                    String episodeId, int transition) implements Comparable<Position> {
        static final int OBSERVATIONS = 0;
        static final int WARNINGS = 1;

        /** An observation's position (or any position whose transition rank is 0). */
        Position(String seriesKey, String sessionDate, int phase, long frameSeq, long epochMs, String episodeId) {
            this(seriesKey, sessionDate, phase, frameSeq, epochMs, episodeId, 0);
        }

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
            c = episodeId.compareTo(o.episodeId);
            if (c != 0) {
                return c;
            }
            return Integer.compare(transition, o.transition);
        }

        String event() {
            return phase == OBSERVATIONS ? EVENT_OBSERVATION : EVENT_WARNING;
        }

        /** The same position over the session's own key strings, so a held key never copies them. */
        private Position over(Session session) {
            return new Position(session.seriesKey, session.sessionDate, phase, frameSeq, epochMs, episodeId,
                    transition);
        }
    }

    /**
     * The transition component of a warning's identity. A resolution — or any continuing transition —
     * of an episode sorts BEFORE an opening with the same episode id at the same instant: that opening
     * is the re-armed NEXT episode, which the contract stamps on the very observation that resolved
     * the previous one (see {@link #acceptWarning}).
     */
    static int transitionRank(EarlyWarningState previousState, EarlyWarningState state) {
        return (previousState == EarlyWarningState.NONE ? 100 : 0) + previousState.ordinal() * 10 + state.ordinal();
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

    /**
     * One session as a machine reads it (VP-346): verbatim records, in replay order, and whether the
     * store still holds ALL of it — false from the first record it refused for the budget.
     */
    record Snapshot(String sessionDate, List<String> observations, List<String> warnings, boolean complete,
                    long refusedForBudget, long retainedBytes, long budgetBytes) {
        Snapshot(String sessionDate, List<String> observations, List<String> warnings) {
            this(sessionDate, observations, warnings, true, 0L, 0L, SERIES_BUDGET_BYTES);
        }
    }

    private static final class Entry {
        final String json;
        final long eventTimeMs;
        final long offset;
        final long charge;
        /** The store's admission sequence when this version was admitted; see {@link #nextChanged}. */
        final long seq;
        /** Greatest offset already broadcast for this position; -1 when none. */
        long broadcastFence;

        Entry(String json, long eventTimeMs, long offset, long charge, long broadcastFence, long seq) {
            this.json = json;
            this.eventTimeMs = eventTimeMs;
            this.offset = offset;
            this.charge = charge;
            this.broadcastFence = broadcastFence;
            this.seq = seq;
        }
    }

    private static final class Session {
        final String seriesKey;
        final String sessionDate;
        /** The ONE partition this session's records may come from — the topic is single-partition. */
        final int partition;
        final TreeMap<Position, Entry> observations = new TreeMap<>();
        final TreeMap<Position, Entry> warnings = new TreeMap<>();
        /** Charged bytes: SERIES_OVERHEAD_BYTES plus every held record's charge. Never above the budget. */
        long bytes = SERIES_OVERHEAD_BYTES;
        /** Records refused because they would have taken this session past its budget. */
        long refusedForBudget;

        Session(String seriesKey, String sessionDate, int partition) {
            this.seriesKey = seriesKey;
            this.sessionDate = sessionDate;
            this.partition = partition;
        }

        TreeMap<Position, Entry> of(Stream stream) {
            return stream == Stream.OBSERVATION ? observations : warnings;
        }
    }

    /**
     * STRICTER than the shared mapper: what is admitted here is forwarded VERBATIM, so the record the
     * contract validated and the bytes a browser reads must be the same values.
     *
     * <ul>
     *   <li>FAIL_ON_MISSING_CREATOR_PROPERTIES and FAIL_ON_NULL_FOR_PRIMITIVES: Jackson fills a missing
     *       component — or an explicit null for a primitive — with the Java default, so a deleted count
     *       would deserialise to a valid-looking zero that the browser refuses outright.</li>
     *   <li>FAIL_ON_TRAILING_TOKENS: a value followed by anything else would make the envelope around
     *       it malformed JSON for every client that received it.</li>
     *   <li>ACCEPT_FLOAT_AS_INT off: by default {@code "frameSeq":6840.5} deserialises to 6840 and
     *       {@code "schemaVersion":2.5} to 2 — the contract then validates numbers the bytes do not
     *       contain, the gateway files the record under ordinal 6840 while the forwarded payload still
     *       says 6840.5, and an unknown wire version passes the version gate. An integer component
     *       must be a JSON integer.</li>
     *   <li>ALLOW_COERCION_OF_SCALARS off, and no number or boolean read as a string: {@code "2"} is not
     *       the number 2, and {@code 7} is not the string "7" — each would be validated as one type and
     *       forwarded as another.</li>
     *   <li>FAIL_ON_NUMBERS_FOR_ENUMS: an enum is its name on the wire, never an ordinal.</li>
     *   <li>STRICT_DUPLICATE_DETECTION: with a key twice, which value is "the" record depends on the
     *       parser — the gateway could validate one and a consumer read the other.</li>
     * </ul>
     * An integer literal in a {@code double} component stays admitted: it is the same number, exactly.
     */
    private static final ObjectMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .withCoercionConfig(LogicalType.Textual, textual -> textual
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();
    /**
     * One strict reader per observation version, both from {@link #STRICT}: each version gets every
     * protection the other has, and each record is validated only by its own contract's constructor.
     */
    private static final ObjectReader V1_OBSERVATION_READER = STRICT.readerFor(IvRvReadingV1.class);
    private static final ObjectReader V2_OBSERVATION_READER = STRICT.readerFor(IvRvReading.class);
    private static final ObjectReader WARNING_READER = STRICT.readerFor(EarlyWarning.class);

    /** No usable schemaVersion: missing, null, or not a JSON integer within int range. Matches no version. */
    static final int NO_SCHEMA_VERSION = Integer.MIN_VALUE;

    /**
     * What the store needs from an observation of EITHER version, once it has passed its own contract:
     * the version, the identity, the instant, and the Kafka key its own version requires. It is built only
     * from a constructed contract record. The key rule is bound to the record TYPE here, so neither version
     * can be keyed by the other's rule.
     */
    private record Observation(int schemaVersion, String symbol, String sessionDate, long eventTimeMs,
                               long frameSeq, long measurementEpochMs, String key) {
        static Observation of(IvRvReadingV1 r) {
            return new Observation(r.schemaVersion(), r.symbol(), r.sessionDate(), r.eventTimeMs(), r.frameSeq(),
                    r.measurementEpochMs(), v1SessionKey(r.symbol(), r.sessionDate()));
        }

        static Observation of(IvRvReading r) {
            return new Observation(r.schemaVersion(), r.symbol(), r.sessionDate(), r.eventTimeMs(), r.frameSeq(),
                    r.measurementEpochMs(), IvRvReading.observationKey(r.symbol(), r.sessionDate(), r.frameSeq()));
        }
    }

    private final long seriesBudgetBytes;
    private final int maxSymbols;
    private final TreeMap<String, Session> sessions = new TreeMap<>();
    private final AtomicLong[][] refusals = new AtomicLong[Stream.values().length][Refusal.values().length];
    /** Observations admitted (new positions and replacements), per wire version: which producer is being taken. */
    private final AtomicLong admittedV1 = new AtomicLong();
    private final AtomicLong admittedV2 = new AtomicLong();
    private boolean symbolCapLogged;
    /** Incremented on every admission, new position or replacement; see {@link #nextChanged}. */
    private long admissionSeq;

    /** The production store: the declared budget, checked against the heap this JVM was given. */
    VolPremiumSessionStore() {
        this(SERIES_BUDGET_BYTES, MAX_SYMBOLS);
        requireBudgetFitsHeap(TOTAL_BUDGET_BYTES, Runtime.getRuntime().maxMemory());
        System.out.println("INFO vol-premium: session store budget " + (SERIES_BUDGET_BYTES >> 20) + " MiB per series x "
                + MAX_SYMBOLS + " series = " + (TOTAL_BUDGET_BYTES >> 20) + " MiB, within 1/" + HEAP_FRACTION_DENOMINATOR
                + " of the " + (Runtime.getRuntime().maxMemory() >> 20) + " MiB heap");
    }

    /** Test seam: the same store with a small budget, so the budget refusal is reachable in a unit test. */
    VolPremiumSessionStore(long seriesBudgetBytes, int maxSymbols) {
        this.seriesBudgetBytes = seriesBudgetBytes;
        this.maxSymbols = maxSymbols;
        for (AtomicLong[] row : refusals) {
            for (int i = 0; i < row.length; i++) {
                row[i] = new AtomicLong();
            }
        }
    }

    /**
     * The boot check: the budget of every series together must fit in
     * {@code 1/}{@value #HEAP_FRACTION_DENOMINATOR} of the heap, or the gateway refuses to start —
     * loudly, at construction, rather than admitting a session it could only hold by running the whole
     * process out of memory in the afternoon. Returns the bound it checked against.
     */
    static long requireBudgetFitsHeap(long totalBudgetBytes, long maxMemoryBytes) {
        long bound = maxMemoryBytes / HEAP_FRACTION_DENOMINATOR;
        if (totalBudgetBytes > bound) {
            throw new IllegalStateException("VOL_PREMIUM_BUDGET_EXCEEDS_HEAP: the vol-premium session store needs "
                    + totalBudgetBytes + " bytes (" + MAX_SYMBOLS + " series x " + SERIES_BUDGET_BYTES
                    + ") but 1/" + HEAP_FRACTION_DENOMINATOR + " of this JVM's max heap (" + maxMemoryBytes
                    + " bytes) is " + bound + "; raise -Xmx (production runs -Xmx1536m) rather than run the "
                    + "whole-session relay unbounded");
        }
        return bound;
    }

    /**
     * Offer one IV/RV observation from the ivrv topic, of EITHER wire version.
     *
     * <p><b>Why two versions</b> (vol-premium runbook, "Rollout sequence", step 2). The live producer today is
     * the old realised-only service, which publishes schemaVersion 1. The engine that replaces it publishes
     * schemaVersion 2 to the SAME topic. From step 2 until the v1 bridge is removed (step 6) this gateway admits
     * both, so the old producer keeps working until the engine replaces it (step 4), and a rollback to the old
     * image stays safe.
     *
     * <p><b>Each version on its own rules, and never on the other's:</b>
     * <ol>
     *   <li>{@code schemaVersion} is read FIRST, by the same strict parser ({@link #wireSchemaVersion}). It must
     *       be a JSON integer naming a version this gateway knows. Missing, null, fractional, quoted or unknown
     *       is {@link Refusal#SCHEMA_VERSION}. Unparseable text, a duplicated key or trailing tokens is
     *       {@link Refusal#MALFORMED}.</li>
     *   <li>The record is then deserialised through THAT version's contract record by the same strict reader
     *       ({@link #STRICT}: no coercion, no duplicate keys; the 64 KiB cap is checked before any parse). v1
     *       goes through {@link IvRvReadingV1}, v2 through {@link IvRvReading}, and each constructor is that
     *       producer's own validation.</li>
     *   <li>The Kafka key must then be EXACTLY that version's key: v1 {@code SYMBOL|sessionDate}
     *       ({@link #v1SessionKey}, the rule gateway main enforced), v2 {@link IvRvReading#observationKey}. The
     *       key is what compaction acts on. A record keyed for one slot but carrying another's body would take
     *       the first slot on the topic while the gateway filed it under the second, and the two histories
     *       would disagree with nothing failing. So a v1 record under a v2-style key, and a v2 record under the
     *       v1 key, are both {@link Refusal#KEY_MISMATCH}.</li>
     * </ol>
     * Past that point the two versions are ONE series. They share the session, the identity
     * {@code (frameSeq, measurementEpochMs)}, the byte budget and its charge, the replay order and the
     * exactly-once handoff. The producer's bytes are forwarded verbatim.
     *
     * <p><b>TRANSITIONAL LIMITS</b>, as found on 2026-09-11; they go away with the bridge at step 6.
     * <ul>
     *   <li><b>After a gateway restart, a v1 session's history is only what the topic still holds.</b> This
     *       store lives on the heap alone. A restarted gateway rebuilds it by seeking back
     *       {@code VOL_PREMIUM_SESSION_SEEK_BACK_MS} and re-admitting what it reads. v1 writes ONE key per
     *       session, so on a COMPACTED topic only the session's newest v1 record is guaranteed to survive.
     *       Older records last only until the log cleaner compacts the segment holding them; the active
     *       segment is never compacted. On such a topic, a v1 producer's whole-session history is only what
     *       this gateway instance observed live. A restart rebuilds the newest v1 point, plus whatever the
     *       cleaner has not yet removed. (v2 keys every observation separately, so compaction keeps all of
     *       it.) Whether the topic IS compacted is decided by the producer, which stamps it at every boot
     *       (processing-common KafkaTopics.ensureServedTopic): compact,delete unless
     *       OPTIONS_EDGE_UNCOMPACTED_SERVED_TOPICS=true. The deploy repo sets that switch for production
     *       (options-edge-config) and for es4, so there the topic is delete, with VOL_PREMIUM_IVRV_RETENTION_MS
     *       (default -1). Every v1 record then stays, and a restart re-reads the whole v1 session. Dev keeps
     *       compaction (by default segment.ms is 1 h and min.cleanable.dirty.ratio 0.01).</li>
     *   <li><b>Two producers form two runs only because their epochs differ.</b> Each producer sets
     *       measurementEpochMs to the event time of the first record ITS accumulator folds. For v1 that is the
     *       first record of the session a new processor instance sees, held in memory. For v2 it is the first
     *       in-session spot tick of its grid, persisted. A switch therefore changes the epoch, and the two runs
     *       stay distinct points. If both accumulators ever began on the same event-time millisecond, a v1 and
     *       a v2 reading of one ordinal would be the SAME position: the later offset would replace the earlier,
     *       under exactly the rules that apply within one version, and would be charged the size difference.
     *       The vol-premium Deployment runs replicas 1 with strategy Recreate, so the two images never publish
     *       at the same time.</li>
     *   <li><b>Ordering by frameSeq assumes one cadence per session.</b> Both producers read
     *       VOL_PREMIUM_FRAME_CADENCE_MS (default 5,000). Producers publishing at different cadences in one
     *       session would number their frames on different lattices, and this store would interleave the
     *       frames out of time order. That is not guarded here, and a cadence change within v2 alone would do
     *       the same.</li>
     *   <li>A v1 record carries no warnings and no trends, so it adds nothing to the warnings stream.</li>
     *   <li>The 64 KiB wire cap belongs to IvRvReading. IvRvReadingV1 declares none, and gateway main applied
     *       none to v1. It is applied to v1 here as well; a genuine v1 record is under 1 KiB.</li>
     *   <li>Both contract records ignore unknown fields (their own annotation). A record labelled v1 that also
     *       carries v2 fields is therefore admitted on v1's rules alone, its extra fields unvalidated, and
     *       forwarded verbatim. Only the cap bounds it.</li>
     *   <li>Gateway main compared the v1 key case-insensitively. Here the comparison is exact, as v2's is,
     *       because Kafka compacts on the key's exact bytes. The v1 producer writes exactly
     *       {@code settings.symbol() + "|" + sessionDate}, with the same symbol in the body, so the exact rule
     *       refuses nothing it publishes.</li>
     * </ul>
     */
    synchronized Admission acceptObservation(String source, String recordKey, int partition, long offset,
                                             String json, long nowMs) {
        if (!withinWire(json)) {
            return refuse(Stream.OBSERVATION, json == null || json.isBlank() ? Refusal.MALFORMED : Refusal.OVERSIZE);
        }
        int schemaVersion;
        try {
            schemaVersion = wireSchemaVersion(json);
        } catch (java.io.IOException | RuntimeException invalid) {
            return refuse(Stream.OBSERVATION, Refusal.MALFORMED);
        }
        Observation reading;
        try {
            // The two case labels are the contracts' own compile-time constants: if they were ever equal,
            // this switch would not compile, rather than silently routing one version through the other.
            reading = switch (schemaVersion) {
                case IvRvReadingV1.CURRENT_SCHEMA_VERSION ->
                        Observation.of(V1_OBSERVATION_READER.<IvRvReadingV1>readValue(json));
                case IvRvReading.CURRENT_SCHEMA_VERSION ->
                        Observation.of(V2_OBSERVATION_READER.<IvRvReading>readValue(json));
                default -> null;
            };
        } catch (java.io.IOException | RuntimeException invalid) {
            return refuse(Stream.OBSERVATION, Refusal.MALFORMED);
        }
        if (reading == null) {
            return refuse(Stream.OBSERVATION, Refusal.SCHEMA_VERSION);
        }
        if (!reading.key().equals(recordKey)) {
            return refuse(Stream.OBSERVATION, Refusal.KEY_MISMATCH);
        }
        if (reading.eventTimeMs() > nowMs + MAX_FUTURE_SKEW_MS) {
            return refuse(Stream.OBSERVATION, Refusal.FUTURE_EVENT_TIME);
        }
        Position position = new Position(source + "|" + reading.symbol(), reading.sessionDate(),
                Position.OBSERVATIONS, reading.frameSeq(), reading.measurementEpochMs(), "", 0);
        Admission admission = admit(Stream.OBSERVATION, position, json, charge(json, false), reading.eventTimeMs(),
                partition, offset, nowMs);
        if (admission.admitted()) {
            (reading.schemaVersion() == IvRvReadingV1.CURRENT_SCHEMA_VERSION ? admittedV1 : admittedV2)
                    .incrementAndGet();
        }
        return admission;
    }

    /**
     * The v1 Kafka key: ONE per session, {@code SYMBOL|sessionDate}. This is what the v1 producer writes
     * (processing main VolPremiumStreams: {@code settings.symbol() + "|" + sessionDate}) and what gateway main
     * required. Compared exactly; see {@link #acceptObservation}.
     */
    static String v1SessionKey(String symbol, String sessionDate) {
        return symbol + "|" + sessionDate;
    }

    /**
     * The record's wire version, read BEFORE any contract is chosen and as strictly as the record itself.
     * The same parser features apply, so a duplicated key (a second schemaVersion included) or trailing tokens
     * throw. Only a JSON INTEGER counts: {@code 1.0}, {@code "1"}, {@code true}, {@code null} or an absent
     * field yield {@link #NO_SCHEMA_VERSION}, never a coerced 1, because a version the bytes do not literally
     * state must not choose the contract that validates them. Throws when the text is not a JSON object.
     */
    static int wireSchemaVersion(String json) throws java.io.IOException {
        JsonNode root = STRICT.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("an observation is a JSON object");
        }
        JsonNode version = root.get("schemaVersion");
        return version != null && version.isInt() ? version.intValue() : NO_SCHEMA_VERSION;
    }

    /** Observations admitted with the given wire version (new positions and replacements); 0 for any other. */
    long admittedObservations(int schemaVersion) {
        return schemaVersion == IvRvReadingV1.CURRENT_SCHEMA_VERSION ? admittedV1.get()
                : schemaVersion == IvRvReading.CURRENT_SCHEMA_VERSION ? admittedV2.get() : 0L;
    }

    /**
     * Offer one early-warning transition from the warnings topic. Validated through
     * {@link EarlyWarning} — whose constructor re-derives the components, strength and state from the
     * raw evidence under the hashed parameter set — and the Kafka key must be EXACTLY its
     * {@code episodeId}, which is what the topic is keyed by (VolPremiumTopics.WARNINGS).
     *
     * <p><b>Identity: {@code (episodeId, frameSeq, asOfMs, previousState, state)}.</b> The episode id
     * names an EPISODE, and one episode emits several transitions, so it is not a transition's id.
     * {@code (episodeId, frameSeq)} is not either: the contract requires a later transition to carry a
     * later INSTANT, not a later ordinal, so an opening at 7141 and its {@code DATA_CENSORED}
     * resolution one second later on a restarted epoch share both. {@code (episodeId, frameSeq, asOfMs)}
     * is still not unique: a resolution and the re-armed opening of the NEXT episode of the same type
     * are stamped on the SAME observation (the contract's own re-arming rule), and when the resolved
     * episode opened at that same ordinal the new one derives the very same id
     * ({@code symbol|sessionDate|type|openedFrameSeq}). What separates them — and a transition that a
     * replacement frame at the same instant re-evaluated differently — is the transition itself. Two
     * records with all five equal are the same transition delivered twice, and the later offset
     * replaces the earlier (a correction of its strength or outcome).
     *
     * <p>The byte bound is the observation's {@link IvRvReading#MAX_RECORD_BYTES}; the warnings contract
     * states none of its own. Warnings share the session and its budget with the observations, so
     * either stream can roll it over and neither may bring an older one back.
     */
    synchronized Admission acceptWarning(String source, String recordKey, int partition, long offset,
                                         String json, long nowMs) {
        if (!withinWire(json)) {
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
                Position.WARNINGS, warning.frameSeq(), warning.asOfMs(), warning.episodeId(),
                transitionRank(warning.previousState(), warning.state()));
        return admit(Stream.WARNING, position, json, charge(json, true), warning.asOfMs(), partition, offset, nowMs);
    }

    private Admission admit(Stream stream, Position position, String json, long charge, long eventTimeMs,
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
            // here to force a rollover. One record (at most MAX_RECORD_BYTES) always fits a fresh
            // session's budget, so a rollover is never followed by a refusal that leaves it empty.
            session = new Session(position.seriesKey(), position.sessionDate(), partition);
            sessions.put(session.seriesKey, session);
        }
        position = position.over(session);
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
        }
        long grown = charge - (previous == null ? 0L : previous.charge);
        if (session.bytes + grown > seriesBudgetBytes) {
            // Fail CLOSED and LOUD (see SERIES_BUDGET_BYTES): nothing held is evicted, this record is
            // refused, and the session is marked incomplete from here on.
            if (session.refusedForBudget++ == 0L) {
                System.out.println("ERROR vol-premium: session " + position.seriesKey() + " " + position.sessionDate()
                        + " is at its retention budget (" + session.bytes + " of " + seriesBudgetBytes
                        + " bytes, " + session.observations.size() + " observations, " + session.warnings.size()
                        + " warnings); REFUSING further records and never evicting held ones. The session is "
                        + "INCOMPLETE from this record on: gateway_vol_premium_refused_total{stream=\""
                        + stream.label + "\",reason=\"SESSION_BUDGET\"}, gateway_vol_premium_sessions_over_budget, "
                        + "and retention.complete=false on /api/vol-premium/ivrv");
            }
            return refuse(stream, Refusal.SESSION_BUDGET);
        }
        held.put(position, new Entry(json, eventTimeMs, offset, charge, fence, ++admissionSeq));
        session.bytes += grown;
        return new Admission(position, recreatedTopic, null);
    }

    /**
     * The retained-heap charge of one record: its JSON as the JVM stores it plus the fixed objects
     * that hold it ({@link #RECORD_OVERHEAD_BYTES}, or {@link #WARNING_OVERHEAD_BYTES} with the episode
     * id). An UPPER bound, which a test checks against the sizes the JVM itself reports.
     */
    static long charge(String json, boolean warning) {
        return (warning ? WARNING_OVERHEAD_BYTES : RECORD_OVERHEAD_BYTES) + retainedStringBytes(json);
    }

    /** A String and its backing array: one byte per char when compact and Latin-1, else two; 8-byte aligned. */
    static long retainedStringBytes(String s) {
        boolean latin1 = COMPACT_STRINGS;
        for (int i = 0; latin1 && i < s.length(); i++) {
            latin1 = s.charAt(i) <= 0xFF;
        }
        long payload = (long) s.length() * (latin1 ? 1L : 2L);
        return STRING_OBJECT_BYTES + ((ARRAY_HEADER_BYTES + payload + 7L) & ~7L);
    }

    private static boolean readCompactStrings() {
        try {
            return "true".equalsIgnoreCase(String.valueOf(FootprintTopicGate.hotSpotVmOptions().apply("CompactStrings")));
        } catch (RuntimeException | LinkageError unreadable) {
            return false;
        }
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

    /**
     * The first held record in {@code (after, upTo]} whose CURRENT version was admitted after
     * {@code sinceSeq.applyAsLong(position)} — i.e. one a reader that was in step with the store up to
     * that admission has not seen. This is how a socket held back while the cache recovered is brought
     * back in step BEHIND its replay cursor without resending what it already has: see the gateway's
     * vol-premium delivery. {@code sinceSeq} must be a pure function; it is called under this lock.
     */
    synchronized Item nextChanged(Position after, Position upTo, ToLongFunction<Position> sinceSeq, long nowMs) {
        if (upTo == null) {
            return null;
        }
        SortedMap<String, Session> from = after == null ? sessions : sessions.tailMap(after.seriesKey());
        for (Map.Entry<String, Session> e : from.entrySet()) {
            if (e.getKey().compareTo(upTo.seriesKey()) > 0) {
                return null;
            }
            Session session = e.getValue();
            if (!sessionCurrent(session.sessionDate, nowMs)) {
                continue;
            }
            for (TreeMap<Position, Entry> held : List.of(session.observations, session.warnings)) {
                Map<Position, Entry> tail = after == null ? held : held.tailMap(after, false);
                for (Map.Entry<Position, Entry> candidate : tail.entrySet()) {
                    Position p = candidate.getKey();
                    if (p.compareTo(upTo) > 0) {
                        return null;
                    }
                    if (candidate.getValue().seq > sinceSeq.applyAsLong(p)) {
                        return new Item(p, candidate.getValue().json);
                    }
                }
            }
        }
        return null;
    }

    /** The sequence number of the latest admission; 0 before the first. */
    synchronized long admissionSeq() {
        return admissionSeq;
    }

    /** One series' current session, verbatim and in replay order; empty when there is none. */
    synchronized Snapshot snapshot(String seriesKey, long nowMs) {
        Session session = sessions.get(seriesKey);
        if (session == null || !sessionCurrent(session.sessionDate, nowMs)) {
            return new Snapshot(null, List.of(), List.of(), true, 0L, 0L, seriesBudgetBytes);
        }
        List<String> observations = new ArrayList<>(session.observations.size());
        for (Entry entry : session.observations.values()) {
            observations.add(entry.json);
        }
        List<String> warnings = new ArrayList<>(session.warnings.size());
        for (Entry entry : session.warnings.values()) {
            warnings.add(entry.json);
        }
        return new Snapshot(session.sessionDate, List.copyOf(observations), List.copyOf(warnings),
                session.refusedForBudget == 0L, session.refusedForBudget, session.bytes, seriesBudgetBytes);
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

    long seriesBudgetBytes() {
        return seriesBudgetBytes;
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

    /** Charged bytes of every held session (the figure the budget is enforced on). */
    synchronized long heldBytes() {
        long n = 0L;
        for (Session s : sessions.values()) {
            n += s.bytes;
        }
        return n;
    }

    synchronized int sessionsOverBudget() {
        int n = 0;
        for (Session s : sessions.values()) {
            if (s.refusedForBudget > 0L) {
                n++;
            }
        }
        return n;
    }

    /**
     * Test seam: the objects one held record retains besides its JSON string's backing array — the
     * entry, the TreeMap node holding it, the position key and the JSON string — so a test can hold the
     * charge against the sizes the JVM reports. Empty when the position is not held.
     */
    synchronized List<Object> retainedObjectsForTest(Position position) {
        Session session = sessions.get(position.seriesKey());
        if (session == null) {
            return List.of();
        }
        for (Map.Entry<Position, Entry> node : (position.phase() == Position.OBSERVATIONS
                ? session.observations : session.warnings).entrySet()) {
            if (node.getKey().equals(position)) {
                return List.of(node.getValue(), node, node.getKey(), node.getValue().json);
            }
        }
        return List.of();
    }

    /** Prometheus text for the store: refusals by stream and reason, and what is resident. */
    String metricsText() {
        StringBuilder sb = new StringBuilder()
                .append("# HELP gateway_vol_premium_refused_total vol-premium records NOT admitted to the ")
                .append("session cache, by stream and reason. SESSION_BUDGET and SYMBOL_CAP are the declared ")
                .append("memory bounds refusing; the rest are contract, key, ordering and session gates.\n")
                .append("# TYPE gateway_vol_premium_refused_total counter\n");
        for (Stream stream : Stream.values()) {
            for (Refusal reason : Refusal.values()) {
                sb.append("gateway_vol_premium_refused_total{stream=\"").append(stream.label)
                        .append("\",reason=\"").append(reason.name()).append("\"} ")
                        .append(refusals(stream, reason)).append('\n');
            }
        }
        sb.append("# HELP gateway_vol_premium_admitted_total vol-premium IV/RV observations admitted to the session ")
                .append("cache (new positions and replacements), by wire schemaVersion. Through the v1-to-v2 rollout ")
                .append("this says which producer the gateway is taking.\n")
                .append("# TYPE gateway_vol_premium_admitted_total counter\n")
                .append("gateway_vol_premium_admitted_total{stream=\"ivrv\",schema=\"")
                .append(IvRvReadingV1.CURRENT_SCHEMA_VERSION).append("\"} ").append(admittedV1.get()).append('\n')
                .append("gateway_vol_premium_admitted_total{stream=\"ivrv\",schema=\"")
                .append(IvRvReading.CURRENT_SCHEMA_VERSION).append("\"} ").append(admittedV2.get()).append('\n');
        sb.append("# HELP gateway_vol_premium_cached_observations IV/RV observations held for the current sessions.\n")
                .append("# TYPE gateway_vol_premium_cached_observations gauge\n")
                .append("gateway_vol_premium_cached_observations ").append(heldObservations()).append('\n')
                .append("# HELP gateway_vol_premium_cached_warnings Early-warning transitions held for the current sessions.\n")
                .append("# TYPE gateway_vol_premium_cached_warnings gauge\n")
                .append("gateway_vol_premium_cached_warnings ").append(heldWarnings()).append('\n')
                .append("# HELP gateway_vol_premium_cached_bytes Retained-heap charge of every held vol-premium ")
                .append("session (an upper bound; the budget is enforced on it).\n")
                .append("# TYPE gateway_vol_premium_cached_bytes gauge\n")
                .append("gateway_vol_premium_cached_bytes ").append(heldBytes()).append('\n')
                .append("# HELP gateway_vol_premium_series_budget_bytes Retention budget of one series' session.\n")
                .append("# TYPE gateway_vol_premium_series_budget_bytes gauge\n")
                .append("gateway_vol_premium_series_budget_bytes ").append(seriesBudgetBytes).append('\n')
                .append("# HELP gateway_vol_premium_sessions_over_budget Current sessions that refused a record ")
                .append("for the budget and are therefore INCOMPLETE. Alert on > 0.\n")
                .append("# TYPE gateway_vol_premium_sessions_over_budget gauge\n")
                .append("gateway_vol_premium_sessions_over_budget ").append(sessionsOverBudget()).append('\n');
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

    /** Non-blank and within {@link IvRvReading#MAX_RECORD_BYTES} as UTF-8. */
    private static boolean withinWire(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            IvRvReading.requireWithinWire(json.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException oversize) {
            return false;
        }
        return true;
    }

    private Admission refuse(Stream stream, Refusal reason) {
        refusals[stream.ordinal()][reason.ordinal()].incrementAndGet();
        return new Admission(null, false, reason);
    }
}
