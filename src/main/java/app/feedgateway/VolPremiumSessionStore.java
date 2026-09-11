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
import com.optionsedge.contracts.volpremium.EarlyWarningType;
import com.optionsedge.contracts.volpremium.IvRvReading;
import com.optionsedge.contracts.volpremium.IvRvReadingV1;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
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
 * <p><b>Where the records are (Codex gateway r3, the storage decision).</b> The BYTES of every held record live on
 * local disk, in one append-only log per session ({@link VolPremiumSessionLog}). The heap holds only an INDEX: per
 * held position, its identity, where its current version's bytes are, their CRC32C, and its delivery state
 * ({@link Slot}, a fixed-size entry). No hot tail is kept. The live path forwards the consumer's own string
 * without reading it back, and a replay reads bytes this process wrote moments earlier from the page cache. A heap
 * tail would only bring back heap use proportional to content. See {@link #SERIES_DISK_BUDGET_BYTES} for the
 * supported-output envelope the disk holds, derived from the engine's code, and {@link #SERIES_INDEX_BUDGET_BYTES}
 * for the heap the index can take.
 *
 * <p><b>The log is a cache, not a source of truth.</b> A restart rebuilds every session from Kafka exactly as
 * before (the gateway's seek-back and re-admission; see {@link #acceptObservation}, TRANSITIONAL LIMITS). No byte
 * on disk is read back across a restart.
 *
 * <p>Every method is synchronized on this store, which is a LEAF lock: nothing is called while it is held
 * except the pure function a caller hands {@link #nextChanged} and the log's file I/O. Those are an append of at
 * most one record, a read of at most one record or one page chunk, and a rewrite, bounded by the live content.
 * So the lock nests safely inside the gateway's emit lock and its instance monitor.
 */
final class VolPremiumSessionStore {

    /** The two wire events this store feeds; both are delivered as standalone, never-coalesced frames. */
    static final String EVENT_OBSERVATION = "vol-premium-ivrv";
    static final String EVENT_WARNING = "vol-premium-warning";

    /** 09:30–16:00 ET: the regular session (§39 item 1), the one the engine frames over. */
    static final long RTH_SESSION_MS = 6L * 60L * 60_000L + 30L * 60_000L;

    /**
     * The fastest frame cadence this gateway SUPPORTS: Gate-1 §11 {@code FRAME_CADENCE_MS} = 5,000, the cadence
     * §7.1 sizes the stream at. The contracts admit down to {@link IvRvReading#MIN_FRAME_CADENCE_MS} (250 ms),
     * twenty times as many frames, and the retention envelope below is derived at 5,000.
     * So an observation stamped faster is refused ({@link Refusal#CADENCE_UNSUPPORTED}), counted and logged,
     * before anything of it is retained. Decision of the design author, 2026-09-11: the engine refuses to boot
     * with a faster cadence too. No deployment configures one: VOL_PREMIUM_FRAME_CADENCE_MS is set in no
     * overlay of the deploy repo, and both producers default it to 5,000.
     */
    static final long SUPPORTED_MIN_FRAME_CADENCE_MS = 5_000L;

    /** 23,400,000 / 5,000: the observations of one full regular session at the supported cadence. */
    static final int REFERENCE_OBSERVATIONS_PER_SESSION = (int) (RTH_SESSION_MS / SUPPORTED_MIN_FRAME_CADENCE_MS);

    /**
     * Every ordinal of the SUPPORTED window at the supported cadence: the regular session, plus the contract's
     * after-midnight allowance ({@link IvRvReading#MAX_AFTER_MIDNIGHT_MS}, 00:00–04:00 of the next day), which
     * this store keeps and admits: {@code (23,400,000 + 14,400,000) / 5,000 = 7,560}.
     */
    static final int SUPPORTED_ORDINALS_PER_SESSION =
            (int) ((RTH_SESSION_MS + IvRvReading.MAX_AFTER_MIDNIGHT_MS) / SUPPORTED_MIN_FRAME_CADENCE_MS);

    /**
     * Observation points ON TOP of one per supported ordinal: the allowance for measurement-epoch overlap. It is a
     * DECISION (design author, Codex gateway r3), because the contract does not bound how many epochs a session has.
     *
     * <p><b>What bounds epoch breaks in the engine</b> (options-edge-processing vol-premium-service, engine worktree
     * at {@code ad924ca5}). The engine begins a new epoch only when an in-session spot tick finds no grid
     * (SessionEngine.java:1186-1191). That is the session's first tick (setSession resets the grid, 1051-1052), or
     * the first tick after the engine LOST its state. Frames stamped before that tick carry midnight + 1
     * (SessionEngine.java:1855). They are EARLIER ordinals, so they break the line without overlapping it. The engine
     * requires exactly-once processing (VolPremiumStreams.java:215-217), so frames and input offsets commit together,
     * and a restart that keeps its state resumes after what it published: no ordinal is framed twice. Points
     * overlap on one ordinal only when the state is lost AND the input is read again, as in an operator's reprocess
     * of the session. One reprocess re-frames at most the engine's whole framed span, open to close (4,680 ordinals
     * at 5 s: SessionEngine.java:1027). The rollout's v1 producer frames on its own epoch, wherever its stream time
     * goes.
     *
     * <p><b>The allowance: one complete re-publication of the supported window</b>, 7,560 points. Every supported
     * ordinal may be held on two epochs, or any number of overlaps may total one window. That covers one full
     * reprocess by either producer. Beyond it the store fails CLOSED and LOUD ({@link Refusal#SESSION_BUDGET}, below).
     */
    static final int EPOCH_OVERLAP_POINTS = SUPPORTED_ORDINALS_PER_SESSION;

    /** Observation positions one session may hold: {@code 7,560 + 7,560 = 15,120}. */
    static final int MAX_OBSERVATION_POSITIONS = SUPPORTED_ORDINALS_PER_SESSION + EPOCH_OVERLAP_POINTS;

    /**
     * The most warning records the engine's evidence emits per type per evaluated observation: TWO. Derived from
     * the engine's code, IvRvEvidence.java (engine worktree at {@code ad924ca5}):
     * <ul>
     *   <li>{@code evaluate} visits each of the 14 types once per observation (214).</li>
     *   <li>No open episode: at most ONE record, the opening (251), then {@code continue} (254).</li>
     *   <li>An open episode that resolves: ONE resolution (285), plus ONE re-arming opening (300) only when the
     *       outcome is CONFIRMED or EXPIRED and the evidence would open again (293-295), then {@code continue}
     *       (303).</li>
     *   <li>An outcome at the opening instant: nothing (305-306). Otherwise at most ONE state move (308-312).</li>
     *   <li>{@code censorAtClose} emits one SESSION_CENSORED per type with an open episode and removes it
     *       (327-352).</li>
     * </ul>
     * <b>The censors are inside the same bound.</b> Every resolution (285 or 341) removes an episode (287, 348) that
     * exactly one opening put (250 or 298). The evidence is new for every session (SessionEngine.java:1058); a
     * restore brings back only this session's episodes, whose openings were already emitted
     * (SessionEngine.java:245). Each evaluation emits, per type, at most one of {opening, move}. So per type, over a
     * session of E evaluations: records = openings + moves + resolutions &le; (openings + moves) + openings &le;
     * E + E = 2E, censors included. Each evaluation belongs to one framed observation (SessionEngine.java:1902), so
     * E is bounded by the observation positions a session may hold.
     *
     * <p>This bound uses no parameter value. A tighter one depends on the confirmation window and
     * {@code episodeMaxDurationMs}, which are configuration, and this gateway does not rely on them.
     */
    static final int MAX_TRANSITIONS_PER_TYPE_PER_OBSERVATION = 2;

    /** Warning positions one session may hold: {@code 2 × 14 types × 15,120 = 423,360}. */
    static final int MAX_WARNING_POSITIONS =
            MAX_TRANSITIONS_PER_TYPE_PER_OBSERVATION * EarlyWarningType.values().length * MAX_OBSERVATION_POSITIONS;

    /**
     * The widest observation, in UTF-8 bytes, that the engine's serialiser can write for a contract-valid record.
     * Every component is present; every number is at the widest text its Java type prints; enums are at their
     * longest name; the four trends of the one shipped parameter set and all fourteen summaries are there. Every
     * FREE string (symbol, codeVersion, both topics, the chain epoch id, and the symbol inside each episode id) is at
     * its contract bound and made of characters Jackson escapes to six bytes. The contract bounds those strings in
     * length only (EarlyWarning and IvRvReading {@code requireText}). ASCII text of the same values is 9,685 bytes.
     * VolPremiumSessionStoreTest derives both figures from the contract's own components and bounds.
     */
    static final int WIDEST_OBSERVATION_BYTES = 14_015;

    /** The widest warning on the same rule: 1,557 bytes as ASCII, 2,037 with its free strings escaped. */
    static final int WIDEST_WARNING_BYTES = 2_037;

    /**
     * The supported-output envelope of one series' session, in bytes of log:
     * {@code 15,120 × 14,015 + 423,360 × 2,037 = 211,906,800 + 862,384,320 = 1,074,291,120}.
     */
    static final long ENVELOPE_BYTES = (long) MAX_OBSERVATION_POSITIONS * WIDEST_OBSERVATION_BYTES
            + (long) MAX_WARNING_POSITIONS * WIDEST_WARNING_BYTES;

    /**
     * Distinct series held at once. The producer publishes SPX; one more is room for a second
     * underlying or a rename overlap. The symbol is a payload string bounded only in length, so without
     * a cap a producer fault minting symbols would grow this map for ever.
     */
    static final int MAX_SYMBOLS = 2;

    /**
     * The DISK budget of one series' session: the live bytes of its held versions, ENFORCED on every admission
     * ({@link Refusal#SESSION_BUDGET}), together with the two position caps above.
     *
     * <p><b>1,280 MiB = 1,342,177,280 bytes.</b> That covers the whole {@link #ENVELOPE_BYTES} (1,074,291,120) with
     * 267,886,160 bytes (24.9%) of headroom. VolPremiumSessionStoreTest admits exactly that envelope through the
     * production store: 15,120 observations and 423,360 transitions of all fourteen types, every one at the widest
     * width. It then replays all of it, from the log, in order.
     *
     * <p><b>Replacement and compaction.</b> A replacement appends the new version and repoints the index; the old
     * bytes become superseded. The log is REWRITTEN with only the held versions ({@link VolPremiumSessionLog#beginRewrite})
     * when the superseded bytes exceed one {@value #COMPACTION_DEAD_DENOMINATOR}th of the live ones, and before an
     * append that would take the file past the budget. So the file is at most 1.25 × live + one record, and never
     * more than the budget + one record. Disk use follows live content, not how often a position was replaced.
     *
     * <p><b>What the disk must provide</b> ({@link #DISK_REQUIRED_BYTES}): each series' file up to the budget + one
     * record, plus ONE rewrite in progress (rewrites run under this store's lock, so never two at once), of at most
     * the live bytes: {@code 3 × 1,280 MiB + 2 × 64 KiB}.
     *
     * <p><b>Beyond the envelope the store fails CLOSED and LOUD, never by truncation.</b> This covers: a 15,121st
     * observation position; a 423,361st transition; live bytes past the budget, which only records wider than the
     * engine's serialiser writes can reach (insignificant whitespace, gratuitous escapes, up to the 64 KiB wire cap);
     * and a disk failure ({@link Refusal#DISK_FAILURE}). The record is refused, and nothing held is evicted to make
     * room. The session is marked incomplete from that moment on: an ERROR line once per session, the
     * {@code gateway_vol_premium_refused_total} counter, the {@code gateway_vol_premium_sessions_over_budget} or
     * {@code gateway_vol_premium_sessions_disk_failed} gauge, and {@code retention.complete=false} with the refused
     * count on the REST page. So neither an operator nor a machine can mistake the held prefix for the whole
     * session. The alternatives both break §40.1 worse. Evicting the oldest points discards history (§40.1: "the
     * history is never discarded") and leaves a chart that looks complete and is not. Forwarding records live
     * without retaining them gives connected sockets a session that no late joiner and no REST reader can ever
     * reproduce.
     */
    static final long SERIES_DISK_BUDGET_BYTES = 1_280L << 20;

    /** Rewrite the log once its superseded bytes exceed {@code live / 4}. */
    static final int COMPACTION_DEAD_DENOMINATOR = 4;

    /** {@code (MAX_SYMBOLS + 1) × budget + MAX_SYMBOLS × 64 KiB = 4,026,662,912} bytes: what the log directory must hold. */
    static final long DISK_REQUIRED_BYTES = (MAX_SYMBOLS + 1L) * SERIES_DISK_BUDGET_BYTES
            + MAX_SYMBOLS * (long) IvRvReading.MAX_RECORD_BYTES;

    /**
     * Upper bound of the heap one index entry retains, whatever its kind: its {@link Slot} (80 bytes with
     * compressed oops, measured) and its share of the {@link SlotList} that orders it (at most 16 bytes: a
     * reference in a 64-slot chunk that is at least half full, the chunk's own object and header, and the
     * chunk directory). VolPremiumSessionStoreTest holds this against the sizes the JVM reports, for one entry and
     * for the whole envelope's index.
     */
    static final long ENTRY_BYTES = 96L;

    /** One session's own structures (the session, its two lists, its log and file channel, its strings). */
    static final long SERIES_OVERHEAD_BYTES = 16_384L;

    /**
     * The HEAP budget of one series' index at the envelope's caps:
     * {@code 16,384 + (15,120 + 423,360) × 96 = 42,110,464} bytes (40.2 MiB). The caps enforce it; bytes are never
     * on the heap.
     */
    static final long SERIES_INDEX_BUDGET_BYTES = SERIES_OVERHEAD_BYTES
            + ((long) MAX_OBSERVATION_POSITIONS + MAX_WARNING_POSITIONS) * ENTRY_BYTES;

    /**
     * The most record bytes one REST page chunk reads under the lock before it writes them out. The next record's
     * length is taken from the index and checked BEFORE the record is read, so no chunk exceeds this (Codex gateway r4,
     * minor: the r3 check ran after the record was added). A chunk always holds at least one record, and one always
     * fits: {@link IvRvReading#MAX_RECORD_BYTES} is a quarter of this.
     */
    static final int PAGE_CHUNK_BYTES = 256 * 1024;

    /**
     * Everything the store may hold on the heap, checked at boot: every series' index, plus, for each REST response
     * that may stream at once, its chunk (at most {@link #PAGE_CHUNK_BYTES} of record bytes) and one record more, the
     * margin the r3 figure reserved when a chunk could pass its bound by a record (it is kept, not re-derived):
     * {@code 2 × 42,110,464 + 4 × (262,144 + 65,536) = 85,531,648} bytes (81.6 MiB).
     *
     * <p>Held in {@code 1/}{@value #HEAP_FRACTION_DENOMINATOR} of {@code Runtime.maxMemory()}, so the heap must
     * report at least 684,253,184 bytes (652.6 MiB). Production runs {@code -Xms256m -Xmx1536m} (JAVA_TOOL_OPTIONS in
     * every feed-gateway overlay) and names no collector. Measured on JDK 21 at those flags, {@code maxMemory()} is
     * 1,610,612,736 bytes under G1, 1,556,938,752 under Serial (what the JVM picks with one CPU) and 1,431,830,528
     * under Parallel. The store can therefore take at most 5.3% (G1), 5.5% (Serial) or 6.0% (Parallel) of the heap
     * reported. The r2 heap-resident store reserved 160 MiB, for a smaller envelope. VolPremiumSessionStoreTest
     * starts the production store in child JVMs under all three.
     */
    static final long TOTAL_HEAP_BUDGET_BYTES = MAX_SYMBOLS * SERIES_INDEX_BUDGET_BYTES
            + VolPremiumController.MAX_CONCURRENT_RESPONSES * (long) (PAGE_CHUNK_BYTES + IvRvReading.MAX_RECORD_BYTES);

    /** The store may use at most {@code 1/8} of {@code Runtime.maxMemory()}; the gateway has other caches. */
    static final int HEAP_FRACTION_DENOMINATOR = 8;

    /**
     * The directory the logs go under: a DEDICATED volume in production. Unset, the store uses {@code java.io.tmpdir}
     * as SellerActivityDiskStore does, and leaves its siblings there alone.
     */
    static final String LOG_DIR_ENV = "GATEWAY_VOL_PREMIUM_LOG_DIR";

    /** Each process's directory under the root: {@code vol-premium-log-<random>}. */
    static final String LOG_DIR_PREFIX = "vol-premium-log-";

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
        /** An observation stamped faster than {@link #SUPPORTED_MIN_FRAME_CADENCE_MS}: outside the envelope. */
        CADENCE_UNSUPPORTED,
        OVERSIZE, KEY_MISMATCH, FUTURE_EVENT_TIME, SESSION_NOT_CURRENT, OLDER_SESSION,
        FOREIGN_PARTITION, REPLAYED_OFFSET, EVENT_TIME_REGRESSION, SESSION_BUDGET, SYMBOL_CAP,
        /** The session's log failed (directory, open, write, read or rewrite): its session takes nothing more. */
        DISK_FAILURE
    }

    /** Which of the two streams a record belongs to. */
    enum Stream {
        OBSERVATION("ivrv"), WARNING("warning");

        final String label;

        Stream(String label) {
            this.label = label;
        }
    }

    /** The log operations whose failures are counted. */
    enum DiskOp {
        DIRECTORY, OPEN, WRITE, READ, REWRITE, DELETE
    }

    /**
     * The position of one record in the replay order: series, then session, then observations before
     * warnings, then {@code (frameSeq, epochMs)} — the measurement epoch for an observation, the
     * transition's own instant {@code asOfMs} for a warning — then the episode id and the transition.
     * ONE total order, so a replay cursor, a live routing decision and the index are the same
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
     * Why a page is NOT the whole session, in the order the verdict reports them when several hold. The first three
     * last for the rest of the session, so asking again does not help. The fourth passes: a new page reads the session
     * as it then stands.
     */
    enum Incomplete {
        /** A newer session replaced the one the page opened on, or it ended, while the page was being read. */
        SESSION_ENDED,
        /** The session's log failed: a record did not read back intact, or an append or rewrite failed. */
        DISK_FAILURE,
        /** The session refused a record for its envelope ({@link Refusal#SESSION_BUDGET}). */
        SESSION_BUDGET,
        /**
         * A record of the page's snapshot was REPLACED before the page reached it: the version the snapshot holds is
         * no longer indexed, so the page could not deliver it (see {@link #page}). A new page reads the session as
         * it then stands.
         */
        CHANGED_WHILE_READ
    }

    /**
     * Whether a page is the whole session, and what it holds, as the REST page states it. {@code reason} is null
     * exactly when the page is complete. {@code retainedBytes} is the live bytes of the snapshot the page was opened
     * on, which a complete page's records add up to.
     */
    record Retention(Incomplete reason, long refusedForBudget, long refusedForDisk, long retainedBytes,
                     long budgetBytes) {
        boolean complete() {
            return reason == null;
        }
    }

    /**
     * One session materialised as lists (VP-346): verbatim records in replay order, and whether they are ALL of it
     * (the page verdict, {@link Retention}). A test and diagnostic convenience: the REST route streams a {@link Page}
     * instead, so a session's bytes are never all on the heap at once.
     */
    record Snapshot(String sessionDate, List<String> observations, List<String> warnings, Incomplete reason,
                    long refusedForBudget, long refusedForDisk, long retainedBytes, long budgetBytes) {
        Snapshot(String sessionDate, List<String> observations, List<String> warnings) {
            this(sessionDate, observations, warnings, null, 0L, 0L, 0L, SERIES_DISK_BUDGET_BYTES);
        }

        boolean complete() {
            return reason == null;
        }
    }

    /**
     * One series' current session as the REST route writes it: ONE SNAPSHOT of it, the session as it stood when the
     * page was opened ({@link #page}), STREAMED from the log in chunks of at most {@link #PAGE_CHUNK_BYTES} (each
     * chunk read under the store's lock, written out after it is released), verbatim and comma-separated, in replay
     * order, then the retention verdict. The verdict is read after the records. It is complete only if they were
     * every record of the snapshot, each once, and the session is still whole; otherwise it names the reason.
     */
    interface Page {
        /** The session date; null when the series holds no current session. */
        String sessionDate();

        void writeObservations(OutputStream out) throws IOException;

        void writeWarnings(OutputStream out) throws IOException;

        /** The verdict, after the records have been written. */
        Retention retention();

        /** A page over an already-materialised snapshot (a diagnostic, or a test's stub of the gateway). */
        static Page of(Snapshot snapshot) {
            return new Page() {
                @Override
                public String sessionDate() {
                    return snapshot.sessionDate();
                }

                @Override
                public void writeObservations(OutputStream out) throws IOException {
                    writeAll(out, snapshot.observations());
                }

                @Override
                public void writeWarnings(OutputStream out) throws IOException {
                    writeAll(out, snapshot.warnings());
                }

                @Override
                public Retention retention() {
                    return new Retention(snapshot.reason(), snapshot.refusedForBudget(), snapshot.refusedForDisk(),
                            snapshot.retainedBytes(), snapshot.budgetBytes());
                }

                private void writeAll(OutputStream out, List<String> records) throws IOException {
                    for (int i = 0; i < records.size(); i++) {
                        if (i > 0) {
                            out.write(',');
                        }
                        out.write(records.get(i).getBytes(StandardCharsets.UTF_8));
                    }
                }
            };
        }
    }

    /**
     * One held position's index entry: its identity within its session, where its CURRENT version's bytes are in
     * the session's log, and its delivery state. Fixed size, whatever the record's size: seven longs and three
     * ints, 80 bytes with compressed oops (measured by VolPremiumSessionStoreTest). An observation's
     * {@code aux} is its event time (a replacement moves it); a warning's is its episode's opening ordinal, and
     * its {@code kind} is its type and transition, so the episode id is DERIVED on demand
     * ({@code symbol|sessionDate|type|openedFrameSeq}, which the contract requires it to equal), never stored.
     */
    static final class Slot {
        final long frameSeq;
        /** An observation's measurementEpochMs, a warning's asOfMs (which is also its event time). */
        final long epochMs;
        long aux;
        /** {@link #OBSERVATION_KIND}, or a warning's {@code type.ordinal() << 8 | transitionRank}. */
        final int kind;
        /** The Kafka offset of the held version. */
        long offset;
        /** The store's admission sequence when this version was admitted; see {@link #nextChanged} and {@link #page}. */
        long seq;
        /** Greatest offset already broadcast for this position; -1 when none. */
        long broadcastFence;
        long fileOffset;
        int length;
        int crc;

        Slot(long frameSeq, long epochMs, long aux, int kind, long offset, long seq, long broadcastFence,
             long fileOffset, int length, int crc) {
            this.frameSeq = frameSeq;
            this.epochMs = epochMs;
            this.aux = aux;
            this.kind = kind;
            this.offset = offset;
            this.seq = seq;
            this.broadcastFence = broadcastFence;
            this.fileOffset = fileOffset;
            this.length = length;
            this.crc = crc;
        }

        boolean warning() {
            return kind != OBSERVATION_KIND;
        }

        int rank() {
            return warning() ? kind & 0xFF : 0;
        }

        long eventTimeMs() {
            return warning() ? epochMs : aux;
        }
    }

    static final int OBSERVATION_KIND = -1;
    private static final EarlyWarningType[] TYPES = EarlyWarningType.values();

    /**
     * The slots of one stream of one session, in replay order: a list of chunks of at most {@value #CHUNK} slots.
     * A lookup is a binary search over the chunks' last slots, then within one chunk; an insert shifts at most one
     * chunk, and splits it in half when full (an append past the end starts a new chunk instead). So every chunk
     * but the last is at least half full, which is what {@link #ENTRY_BYTES} charges for. Nothing is ever removed:
     * a session's index is dropped whole.
     */
    static final class SlotList implements Iterable<Slot> {
        static final int CHUNK = 64;

        static final class Chunk {
            final Slot[] items = new Slot[CHUNK];
            int size;
        }

        private final ArrayList<Chunk> chunks = new ArrayList<>();
        private int size;

        int size() {
            return size;
        }

        /** The first chunk whose last slot is at or after {@code key}; {@code chunks.size()} when none is. */
        private int chunkFor(Position key, Session s) {
            int lo = 0;
            int hi = chunks.size() - 1;
            int found = chunks.size();
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                Chunk c = chunks.get(mid);
                if (s.compare(c.items[c.size - 1], key) >= 0) {
                    found = mid;
                    hi = mid - 1;
                } else {
                    lo = mid + 1;
                }
            }
            return found;
        }

        /** The first index of the chunk whose slot is at or after {@code key}. */
        private static int indexIn(Chunk c, Position key, Session s) {
            int lo = 0;
            int hi = c.size - 1;
            int found = c.size;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (s.compare(c.items[mid], key) >= 0) {
                    found = mid;
                    hi = mid - 1;
                } else {
                    lo = mid + 1;
                }
            }
            return found;
        }

        Slot find(Position key, Session s) {
            int ci = chunkFor(key, s);
            if (ci == chunks.size()) {
                return null;
            }
            Chunk c = chunks.get(ci);
            int i = indexIn(c, key, s);
            return i < c.size && s.compare(c.items[i], key) == 0 ? c.items[i] : null;
        }

        /** Inserts a slot whose key is not held. */
        void insert(Slot slot, Position key, Session s) {
            size++;
            int ci = chunks.isEmpty() ? 0 : chunkFor(key, s);
            if (ci == chunks.size()) {
                Chunk last = chunks.isEmpty() ? null : chunks.get(ci - 1);
                if (last == null || last.size == CHUNK) {
                    last = new Chunk();
                    chunks.add(last);
                }
                last.items[last.size++] = slot;
                return;
            }
            Chunk c = chunks.get(ci);
            int i = indexIn(c, key, s);
            if (c.size == CHUNK) {
                Chunk upper = new Chunk();
                int half = CHUNK / 2;
                System.arraycopy(c.items, half, upper.items, 0, CHUNK - half);
                Arrays.fill(c.items, half, CHUNK, null);
                upper.size = CHUNK - half;
                c.size = half;
                chunks.add(ci + 1, upper);
                if (i > half) {
                    c = upper;
                    i -= half;
                }
            }
            System.arraycopy(c.items, i, c.items, i + 1, c.size - i);
            c.items[i] = slot;
            c.size++;
        }

        /** The slots strictly after {@code key} (every slot when key is null), in order. */
        Iterator<Slot> after(Position key, Session s) {
            int ci = 0;
            int i = 0;
            if (key != null) {
                ci = chunkFor(key, s);
                if (ci < chunks.size()) {
                    Chunk c = chunks.get(ci);
                    i = indexIn(c, key, s);
                    if (i < c.size && s.compare(c.items[i], key) == 0) {
                        i++;
                    }
                }
            }
            return cursor(ci, i);
        }

        @Override
        public Iterator<Slot> iterator() {
            return cursor(0, 0);
        }

        private Iterator<Slot> cursor(int startChunk, int startIndex) {
            return new Iterator<>() {
                int ci = startChunk;
                int i = startIndex;

                private void settle() {
                    while (ci < chunks.size() && i >= chunks.get(ci).size) {
                        ci++;
                        i = 0;
                    }
                }

                @Override
                public boolean hasNext() {
                    settle();
                    return ci < chunks.size();
                }

                @Override
                public Slot next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return chunks.get(ci).items[i++];
                }
            };
        }

        /**
         * Test seam: every object the list itself retains besides its slots — the directory and each chunk with its
         * array. The directory's backing array is not reachable without opening java.base, so a test adds its bound
         * ({@code 16 + 4 × capacity}, capacity at most 1.5 × the chunks + 10).
         */
        void containerObjects(List<Object> out) {
            out.add(chunks);
            for (Chunk c : chunks) {
                out.add(c);
                out.add(c.items);
            }
        }
    }

    private static final class Session {
        final String seriesKey;
        final String sessionDate;
        final String symbol;
        /** The ONE partition this session's records may come from — the topic is single-partition. */
        final int partition;
        /**
         * Unique within this store, from 1. A REST page is tied to it rather than to the date, so a session closed and
         * then opened again for the same date is never taken for the one the page opened on.
         */
        final long number;
        final SlotList observations = new SlotList();
        final SlotList warnings = new SlotList();
        /** Null when it could not be created: the session is then failed from its first record. */
        VolPremiumSessionLog log;
        /** Bytes of every held version, the figure the disk budget is enforced on. */
        long liveBytes;
        /** Records refused because they would have taken this session past its envelope. */
        long refusedForBudget;
        /** Records refused because the session's log had failed. */
        long refusedForDisk;
        /** Set by the first failure of the session's log; the session takes nothing more. */
        boolean diskFailed;

        Session(String seriesKey, String sessionDate, String symbol, int partition, long number) {
            this.seriesKey = seriesKey;
            this.sessionDate = sessionDate;
            this.symbol = symbol;
            this.partition = partition;
            this.number = number;
        }

        SlotList of(int phase) {
            return phase == Position.OBSERVATIONS ? observations : warnings;
        }

        String episodeIdOf(Slot slot) {
            return slot.warning() ? EarlyWarning.episodeId(symbol, sessionDate, TYPES[slot.kind >>> 8], slot.aux) : "";
        }

        Position positionOf(int phase, Slot slot) {
            return new Position(seriesKey, sessionDate, phase, slot.frameSeq, slot.epochMs, episodeIdOf(slot),
                    slot.rank());
        }

        /**
         * A slot against a key of THIS session and the slot's own stream: exactly {@link Position#compareTo} on the
         * fields that can differ. The episode id is derived only when the ordinal and instant tie.
         */
        int compare(Slot slot, Position key) {
            int c = Long.compare(slot.frameSeq, key.frameSeq());
            if (c != 0) {
                return c;
            }
            c = Long.compare(slot.epochMs, key.epochMs());
            if (c != 0) {
                return c;
            }
            c = episodeIdOf(slot).compareTo(key.episodeId());
            if (c != 0) {
                return c;
            }
            return Integer.compare(slot.rank(), key.transition());
        }

        /** Where a position lies against this session's positions: before (-1), within (0) or after (1). */
        int placeOf(Position p) {
            int c = p.seriesKey().compareTo(seriesKey);
            if (c == 0) {
                c = p.sessionDate().compareTo(sessionDate);
            }
            return Integer.signum(c);
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
     * the version, the identity, the instant, the cadence, and the Kafka key its own version requires. It is built only
     * from a constructed contract record. The key rule is bound to the record TYPE here, so neither version
     * can be keyed by the other's rule.
     */
    private record Observation(int schemaVersion, String symbol, String sessionDate, long eventTimeMs,
                               long frameSeq, long measurementEpochMs, long frameCadenceMs, String key) {
        static Observation of(IvRvReadingV1 r) {
            return new Observation(r.schemaVersion(), r.symbol(), r.sessionDate(), r.eventTimeMs(), r.frameSeq(),
                    r.measurementEpochMs(), r.frameCadenceMs(), v1SessionKey(r.symbol(), r.sessionDate()));
        }

        static Observation of(IvRvReading r) {
            return new Observation(r.schemaVersion(), r.symbol(), r.sessionDate(), r.eventTimeMs(), r.frameSeq(),
                    r.measurementEpochMs(), r.frameCadenceMs(),
                    IvRvReading.observationKey(r.symbol(), r.sessionDate(), r.frameSeq()));
        }
    }

    private final long seriesBudgetBytes;
    private final int maxSymbols;
    private final int maxObservations;
    private final int maxWarnings;
    private final Path root;
    private VolPremiumSessionLog.Io io;
    /** This process's log directory; created with the first session, null until then or while it cannot be. */
    private Path directory;
    private long sessionNumber;
    private final TreeMap<String, Session> sessions = new TreeMap<>();
    private final AtomicLong[][] refusals = new AtomicLong[Stream.values().length][Refusal.values().length];
    private final AtomicLong[] diskErrors = new AtomicLong[DiskOp.values().length];
    private final AtomicLong rewrites = new AtomicLong();
    /** Observations admitted (new positions and replacements), per wire version: which producer is being taken. */
    private final AtomicLong admittedV1 = new AtomicLong();
    private final AtomicLong admittedV2 = new AtomicLong();
    private boolean symbolCapLogged;
    /** Whether the first {@link Refusal#CADENCE_UNSUPPORTED} has been logged; every one is counted. */
    private boolean cadenceRefusalLogged;
    /** Incremented on every admission, new position or replacement; see {@link #nextChanged}. */
    private long admissionSeq;

    /** The production store: the declared envelope, its log under the configured directory, the heap check run. */
    VolPremiumSessionStore() {
        this(Runtime.getRuntime()::maxMemory);
    }

    /**
     * The production store against a stated max heap: the declared envelope, and the boot check
     * ({@link #requireBudgetFitsHeap}) run on it. The no-argument constructor passes this JVM's own
     * {@code Runtime.maxMemory()}; a test passes a heap too small, so deleting the check fails a test.
     */
    VolPremiumSessionStore(LongSupplier maxMemory) {
        this(SERIES_DISK_BUDGET_BYTES, MAX_SYMBOLS, MAX_OBSERVATION_POSITIONS, MAX_WARNING_POSITIONS,
                configuredRoot(System.getenv(LOG_DIR_ENV)), dedicated(System.getenv(LOG_DIR_ENV)),
                VolPremiumSessionLog.FILES);
        long maxMemoryBytes = maxMemory.getAsLong();
        requireBudgetFitsHeap(TOTAL_HEAP_BUDGET_BYTES, maxMemoryBytes);
        System.out.println("INFO vol-premium: session store index " + TOTAL_HEAP_BUDGET_BYTES + " bytes of heap at most ("
                + MAX_SYMBOLS + " series x " + SERIES_INDEX_BUDGET_BYTES + " + REST page chunks), within 1/"
                + HEAP_FRACTION_DENOMINATOR + " of the " + (maxMemoryBytes >> 20) + " MiB heap; record bytes in the log under "
                + root + " (" + LOG_DIR_ENV + (dedicated(System.getenv(LOG_DIR_ENV)) ? "" : " unset: java.io.tmpdir")
                + "), " + (SERIES_DISK_BUDGET_BYTES >> 20) + " MiB per series, " + DISK_REQUIRED_BYTES
                + " bytes of disk at most");
    }

    /** Test seam: the production caps and file system under {@code java.io.tmpdir}, with a stated disk budget. */
    VolPremiumSessionStore(long seriesBudgetBytes, int maxSymbols) {
        this(seriesBudgetBytes, maxSymbols, MAX_OBSERVATION_POSITIONS, MAX_WARNING_POSITIONS,
                configuredRoot(null), false, VolPremiumSessionLog.FILES);
    }

    /**
     * Every setting stated. When {@code dedicatedRoot}, the root belongs to this gateway (a volume of its own), so
     * the directories earlier processes left in it are deleted here: the log is a cache, and they are unreadable
     * to this process anyway. An emptyDir outlives a container restart, which is what this cleans.
     */
    VolPremiumSessionStore(long seriesBudgetBytes, int maxSymbols, int maxObservations, int maxWarnings, Path root,
                           boolean dedicatedRoot, VolPremiumSessionLog.Io io) {
        this.seriesBudgetBytes = seriesBudgetBytes;
        this.maxSymbols = maxSymbols;
        this.maxObservations = maxObservations;
        this.maxWarnings = maxWarnings;
        this.root = root;
        this.io = io;
        for (AtomicLong[] row : refusals) {
            for (int i = 0; i < row.length; i++) {
                row[i] = new AtomicLong();
            }
        }
        for (int i = 0; i < diskErrors.length; i++) {
            diskErrors[i] = new AtomicLong();
        }
        if (dedicatedRoot) {
            try {
                for (Path stale : io.children(root, LOG_DIR_PREFIX)) {
                    io.deleteTree(stale);
                    System.out.println("INFO vol-premium: deleted " + stale + ", a session log directory an earlier "
                            + "process left in " + root);
                }
            } catch (IOException | RuntimeException failed) {
                diskErrors[DiskOp.DELETE.ordinal()].incrementAndGet();
                System.out.println("ERROR vol-premium: could not clear earlier processes' session logs from " + root
                        + " (" + failed + "); counted as gateway_vol_premium_disk_errors_total{op=\"DELETE\"}. They "
                        + "take disk this process's budget does not count");
            }
        }
    }

    static Path configuredRoot(String env) {
        return dedicated(env) ? Path.of(env.trim()) : Path.of(System.getProperty("java.io.tmpdir"));
    }

    static boolean dedicated(String env) {
        return env != null && !env.isBlank();
    }

    /**
     * The boot check: the heap the store can take must fit in
     * {@code 1/}{@value #HEAP_FRACTION_DENOMINATOR} of the heap, or the gateway refuses to start —
     * loudly, at construction, rather than admitting a session it could only index by running the whole
     * process out of memory in the afternoon. Returns the bound it checked against.
     */
    static long requireBudgetFitsHeap(long totalBudgetBytes, long maxMemoryBytes) {
        long bound = maxMemoryBytes / HEAP_FRACTION_DENOMINATOR;
        if (totalBudgetBytes > bound) {
            throw new IllegalStateException("VOL_PREMIUM_BUDGET_EXCEEDS_HEAP: the vol-premium session store's index "
                    + "needs " + totalBudgetBytes + " bytes (" + MAX_SYMBOLS + " series x " + SERIES_INDEX_BUDGET_BYTES
                    + " + REST page chunks) but 1/" + HEAP_FRACTION_DENOMINATOR + " of this JVM's max heap ("
                    + maxMemoryBytes + " bytes) is " + bound + "; raise -Xmx (production runs -Xmx1536m) rather than "
                    + "run the whole-session relay unbounded");
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
     *   <li>Its {@code frameCadenceMs} must be at least {@link #SUPPORTED_MIN_FRAME_CADENCE_MS}, whichever
     *       version it is: the envelope is derived at that cadence ({@link #SERIES_DISK_BUDGET_BYTES}). Faster
     *       is {@link Refusal#CADENCE_UNSUPPORTED}, counted and logged before anything is retained.</li>
     * </ol>
     * Past that point the two versions are ONE series. They share the session, the identity
     * {@code (frameSeq, measurementEpochMs)}, the envelope, the replay order and the exactly-once handoff. The
     * producer's bytes are forwarded verbatim.
     *
     * <p><b>TRANSITIONAL LIMITS</b>, as found on 2026-09-11; they go away with the bridge at step 6.
     * <ul>
     *   <li><b>After a gateway restart, a session is only what the topic still holds.</b> The store's disk log
     *       is a cache of this process ({@link VolPremiumSessionLog}); nothing in it is read back. A restarted
     *       gateway rebuilds it by seeking back
     *       {@code VOL_PREMIUM_SESSION_SEEK_BACK_MS} and re-admitting what it reads. What it can read depends on
     *       the topic's cleanup policy and on each version's KEY, which is all compaction looks at:
     *       <ul>
     *         <li>DELETE topic: every record still within retention is there, so a rebuild recovers both
     *             versions and every measurement epoch: the whole session.</li>
     *         <li>COMPACTED topic: the log cleaner guarantees only the newest record per key. v1 writes ONE key
     *             per session ({@code SYMBOL|sessionDate}), so only the session's newest v1 record is guaranteed.
     *             v2 writes one key per ORDINAL ({@link IvRvReading#observationKey},
     *             {@code SYMBOL|sessionDate|frameSeq}), and that key does not carry measurementEpochMs, so only
     *             the newest v2 record PER ORDINAL is guaranteed: two epochs at one ordinal, which this store
     *             holds live as two points, rebuild as one. Older records last only until the cleaner compacts
     *             the segment holding them; the active segment is never compacted. What a compacted topic loses
     *             was only ever what this gateway instance observed live.</li>
     *       </ul>
     *       Whether the topic IS compacted is decided by the producer, which stamps it at every boot
     *       (processing-common KafkaTopics.ensureServedTopic): compact,delete unless
     *       OPTIONS_EDGE_UNCOMPACTED_SERVED_TOPICS=true. The deploy repo sets that switch for production
     *       (options-edge-config) and for es4, so there the topic is delete, with VOL_PREMIUM_IVRV_RETENTION_MS
     *       (default -1), and a restart re-reads the whole session. Dev keeps compaction (by default segment.ms
     *       is 1 h and min.cleanable.dirty.ratio 0.01). FeedGatewayServiceTest rebuilds both cases.</li>
     *   <li><b>A v1 run and a v2 run are separate points only when their epochs differ, and nothing
     *       guarantees that they do.</b> Each producer sets measurementEpochMs to the event time of the first
     *       record ITS accumulator folds: for v1 the first record of the session a new processor instance sees,
     *       held in memory; for v2 the first in-session spot tick of its grid, persisted. Where the replacing
     *       producer starts reading usually makes its epoch the later one: both use the streams application id
     *       options-edge-vol-premium by default, so the replacement resumes after the committed offset of the
     *       producer it replaces, and with a fresh id VOL_PREMIUM_STREAMS_AUTO_OFFSET_RESET defaults to latest.
     *       But offsets do not order EVENT TIMES. A later-offset spot record can carry the very instant T that
     *       the replaced run's first folded record carried, and the engine stamps its session midnight + 1 ms on
     *       a frame measured before any in-session spot tick has started its grid (SessionEngine.emitIvRv:
     *       {@code st.measurementEpochMs > 0L ? st.measurementEpochMs : midnight + 1L}). So the two runs can
     *       share an epoch with no offset reset at all, and no distinctness is claimed here. When they share it,
     *       a v1 and a v2 reading of one ordinal are the SAME position {@code (frameSeq, measurementEpochMs)}: the
     *       later offset replaces the earlier, under exactly the rules that apply within one version (never with
     *       an earlier event time; a lower or equal offset is a replay), and the session's live bytes change by
     *       the size difference. The earlier producer's reading of that window is then neither held nor replayed.
     *       VolPremiumSessionStoreTest pins this in both directions.</li>
     *   <li><b>Ordering by frameSeq assumes one cadence per session.</b> Both producers read
     *       VOL_PREMIUM_FRAME_CADENCE_MS (default 5,000; nothing faster than
     *       {@link #SUPPORTED_MIN_FRAME_CADENCE_MS} is admitted). Producers publishing at different supported
     *       cadences in one session would number their frames on different lattices, and this store would
     *       interleave the frames out of time order. That is not guarded here, and a cadence change within v2
     *       alone would do the same.</li>
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
        Refusal unwired = wireRefusal(json);
        if (unwired != null) {
            return refuse(Stream.OBSERVATION, unwired);
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
        if (reading.frameCadenceMs() < SUPPORTED_MIN_FRAME_CADENCE_MS) {
            // Before anything is retained: no point, no byte, and no session opened or rolled over. The envelope is
            // derived at the supported cadence, so a faster producer outruns it by construction, not by accident.
            // Counted on every record and logged once, so the refusal is never silent.
            if (!cadenceRefusalLogged) {
                cadenceRefusalLogged = true;
                System.out.println("ERROR vol-premium: refusing observations at frameCadenceMs " + reading.frameCadenceMs()
                        + " (schemaVersion " + reading.schemaVersion() + ", key " + recordKey + "): the supported minimum is "
                        + SUPPORTED_MIN_FRAME_CADENCE_MS + " ms, the cadence the retention envelope is derived at. Counted as "
                        + "gateway_vol_premium_refused_total{stream=\"ivrv\",reason=\"CADENCE_UNSUPPORTED\"}");
            }
            return refuse(Stream.OBSERVATION, Refusal.CADENCE_UNSUPPORTED);
        }
        if (reading.eventTimeMs() > nowMs + MAX_FUTURE_SKEW_MS) {
            return refuse(Stream.OBSERVATION, Refusal.FUTURE_EVENT_TIME);
        }
        Position position = new Position(source + "|" + reading.symbol(), reading.sessionDate(),
                Position.OBSERVATIONS, reading.frameSeq(), reading.measurementEpochMs(), "", 0);
        Admission admission = admit(Stream.OBSERVATION, position, reading.symbol(), utf8(json), reading.eventTimeMs(),
                OBSERVATION_KIND, reading.eventTimeMs(), partition, offset, nowMs);
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
     * states none of its own. Warnings share the session and its envelope with the observations, so
     * either stream can roll it over and neither may bring an older one back.
     */
    synchronized Admission acceptWarning(String source, String recordKey, int partition, long offset,
                                         String json, long nowMs) {
        Refusal unwired = wireRefusal(json);
        if (unwired != null) {
            return refuse(Stream.WARNING, unwired);
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
        int rank = transitionRank(warning.previousState(), warning.state());
        Position position = new Position(source + "|" + warning.symbol(), warning.sessionDate(),
                Position.WARNINGS, warning.frameSeq(), warning.asOfMs(), warning.episodeId(), rank);
        return admit(Stream.WARNING, position, warning.symbol(), utf8(json), warning.asOfMs(),
                warning.type().ordinal() << 8 | rank, warning.openedFrameSeq(), partition, offset, nowMs);
    }

    private Admission admit(Stream stream, Position position, String symbol, byte[] bytes, long eventTimeMs,
                            int kind, long aux, int partition, long offset, long nowMs) {
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
            // here to force a rollover. The replaced session's log is deleted now: the store no longer holds it.
            if (session != null) {
                release(session);
            }
            session = openSession(position.seriesKey(), position.sessionDate(), symbol, partition);
            sessions.put(session.seriesKey, session);
        }
        SlotList held = session.of(position.phase());
        Slot previous = held.find(position, session);
        boolean recreatedTopic = false;
        long fence = -1L;
        if (previous != null) {
            if (offset > previous.offset) {
                // A later write of the SAME position: a replacement, which is the contract's rule for a
                // repeated ordinal on the same epoch. Its event time may equal the stored one (a
                // correction) but not precede it — this producer's stream time never runs backwards, so
                // an earlier one is corrupt, and the browser refuses it too.
                if (eventTimeMs < previous.eventTimeMs()) {
                    return refuse(stream, Refusal.EVENT_TIME_REGRESSION);
                }
                fence = previous.broadcastFence;
            } else if (eventTimeMs > previous.eventTimeMs()) {
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
        if (session.diskFailed) {
            return refuseForDisk(stream, session);
        }
        long live = session.liveBytes - (previous == null ? 0L : previous.length) + bytes.length;
        int cap = position.phase() == Position.OBSERVATIONS ? maxObservations : maxWarnings;
        if (previous == null && held.size() >= cap) {
            return refuseForBudget(stream, session, held.size() + " " + stream.label + " positions, the cap of " + cap);
        }
        if (live > seriesBudgetBytes) {
            // Fail CLOSED and LOUD (see SERIES_DISK_BUDGET_BYTES): nothing held is evicted, this record is
            // refused, and the session is marked incomplete from here on.
            return refuseForBudget(stream, session, session.liveBytes + " live bytes of " + seriesBudgetBytes
                    + ", and this version needs " + bytes.length);
        }
        VolPremiumSessionLog log = session.log;
        // An append that would take the FILE past the budget reclaims the superseded bytes first. The live bytes
        // fit (checked above), so the file then does too, give or take the one record a replacement adds before
        // it supersedes its predecessor.
        if (log.end() + bytes.length > seriesBudgetBytes && log.end() > session.liveBytes && !rewrite(session)) {
            return refuseForDisk(stream, session);
        }
        long at;
        try {
            at = log.append(bytes);
        } catch (IOException | RuntimeException failed) {
            failDisk(session, DiskOp.WRITE, failed);
            return refuseForDisk(stream, session);
        }
        int crc = VolPremiumSessionLog.crc(bytes);
        // Strictly greater than every earlier stamp, on a new position and on a replacement alike: nextChanged and a
        // REST page's snapshot (see page) both rely on it.
        long seq = ++admissionSeq;
        if (previous != null) {
            // REPOINT: the index now names the new version's bytes; the old ones are superseded.
            previous.fileOffset = at;
            previous.length = bytes.length;
            previous.crc = crc;
            previous.offset = offset;
            previous.seq = seq;
            previous.broadcastFence = fence;
            if (!previous.warning()) {
                previous.aux = eventTimeMs;
            }
        } else {
            held.insert(new Slot(position.frameSeq(), position.epochMs(), aux, kind, offset, seq, fence, at,
                    bytes.length, crc), position, session);
        }
        session.liveBytes = live;
        // Superseded bytes past the stated fraction of the live ones, or a file past the budget: rewrite. A failure
        // there leaves the old file, intact, as the log (so this version stays held and readable) and fails the
        // session from its next record.
        long dead = log.end() - session.liveBytes;
        if (dead * COMPACTION_DEAD_DENOMINATOR > session.liveBytes || log.end() > seriesBudgetBytes) {
            rewrite(session);
        }
        return new Admission(new Position(session.seriesKey, session.sessionDate, position.phase(),
                position.frameSeq(), position.epochMs(), position.episodeId(), position.transition()),
                recreatedTopic, null);
    }

    /** A new session's index and log. A log that cannot be created fails the session closed from its first record. */
    private Session openSession(String seriesKey, String sessionDate, String symbol, int partition) {
        Session session = new Session(seriesKey, sessionDate, symbol, partition, ++sessionNumber);
        if (directory == null) {
            try {
                directory = io.createDirectory(root, LOG_DIR_PREFIX);
            } catch (IOException | RuntimeException failed) {
                failDisk(session, DiskOp.DIRECTORY, failed);
                return session;
            }
        }
        try {
            session.log = new VolPremiumSessionLog(io, directory, "s" + session.number);
        } catch (IOException | RuntimeException failed) {
            failDisk(session, DiskOp.OPEN, failed);
        }
        return session;
    }

    /**
     * Rewrites the session's log with only its held versions, observations then warnings in replay order, and
     * repoints every slot to its new offset (the running sum of the lengths before it, since the new file is
     * written sequentially in that order). Returns false, with the session failed, if the rewrite failed: the old
     * file is then untouched and stays the log.
     */
    private boolean rewrite(Session session) {
        VolPremiumSessionLog log = session.log;
        VolPremiumSessionLog.Rewrite next = null;
        try {
            next = log.beginRewrite();
            for (SlotList list : List.of(session.observations, session.warnings)) {
                for (Slot slot : list) {
                    next.append(log.read(slot.fileOffset, slot.length, slot.crc));
                }
            }
            log.commit(next);
        } catch (IOException | RuntimeException failed) {
            if (next != null) {
                log.abandon(next);
            }
            failDisk(session, DiskOp.REWRITE, failed);
            return false;
        }
        long at = 0L;
        for (SlotList list : List.of(session.observations, session.warnings)) {
            for (Slot slot : list) {
                slot.fileOffset = at;
                at += slot.length;
            }
        }
        rewrites.incrementAndGet();
        IOException leftover = log.takeRetireFailure();
        if (leftover != null) {
            diskErrors[DiskOp.DELETE.ordinal()].incrementAndGet();
            System.out.println("ERROR vol-premium: session " + session.seriesKey + " " + session.sessionDate
                    + ": the superseded log generation could not be removed after a rewrite (" + leftover + "); it "
                    + "stays on disk until the process directory is deleted. Counted as "
                    + "gateway_vol_premium_disk_errors_total{op=\"DELETE\"}");
        }
        return true;
    }

    /** Deletes a session's log: the store no longer holds that session. A failure is counted and logged. */
    private void release(Session session) {
        VolPremiumSessionLog log = session.log;
        session.log = null;
        if (log == null) {
            return;
        }
        try {
            log.delete();
        } catch (IOException | RuntimeException failed) {
            diskErrors[DiskOp.DELETE.ordinal()].incrementAndGet();
            System.out.println("ERROR vol-premium: session " + session.seriesKey + " " + session.sessionDate
                    + ": its log " + log.path() + " could not be deleted (" + failed + "); it stays on disk until the "
                    + "process directory is deleted. Counted as gateway_vol_premium_disk_errors_total{op=\"DELETE\"}");
        }
    }

    /**
     * Fails a session's log CLOSED: counted by operation, logged once per session, and from here on the session
     * takes nothing more ({@link Refusal#DISK_FAILURE}) and says it is incomplete. What it already holds is still
     * served while it reads back intact.
     */
    private void failDisk(Session session, DiskOp op, Throwable failure) {
        diskErrors[op.ordinal()].incrementAndGet();
        if (!session.diskFailed) {
            session.diskFailed = true;
            System.out.println("ERROR vol-premium: session " + session.seriesKey + " " + session.sessionDate
                    + ": its disk log FAILED on " + op + " (" + failure + ", " + session.observations.size()
                    + " observations and " + session.warnings.size() + " warnings held); REFUSING further records "
                    + "and never truncating held ones. The session is INCOMPLETE from here on: "
                    + "gateway_vol_premium_disk_errors_total{op=\"" + op + "\"}, "
                    + "gateway_vol_premium_refused_total{reason=\"DISK_FAILURE\"}, gateway_vol_premium_sessions_disk_failed, "
                    + "and retention.complete=false on /api/vol-premium/ivrv");
        }
    }

    private Admission refuseForDisk(Stream stream, Session session) {
        session.refusedForDisk++;
        return refuse(stream, Refusal.DISK_FAILURE);
    }

    private Admission refuseForBudget(Stream stream, Session session, String limit) {
        if (session.refusedForBudget++ == 0L) {
            System.out.println("ERROR vol-premium: session " + session.seriesKey + " " + session.sessionDate
                    + " is at its retention envelope (" + limit + "; " + session.observations.size()
                    + " observations, " + session.warnings.size() + " warnings, " + session.liveBytes + " live bytes); "
                    + "REFUSING further records and never evicting held ones. The session is INCOMPLETE from this "
                    + "record on: gateway_vol_premium_refused_total{stream=\"" + stream.label
                    + "\",reason=\"SESSION_BUDGET\"}, gateway_vol_premium_sessions_over_budget, "
                    + "and retention.complete=false on /api/vol-premium/ivrv");
        }
        return refuse(stream, Refusal.SESSION_BUDGET);
    }

    /**
     * Non-blank, encodable as UTF-8 EXACTLY (so the bytes logged, read back and forwarded are the string the
     * contract validated), and within {@link IvRvReading#MAX_RECORD_BYTES}. Null when it passes.
     */
    private static Refusal wireRefusal(String json) {
        if (json == null || json.isBlank()) {
            return Refusal.MALFORMED;
        }
        if (json.length() > IvRvReading.MAX_RECORD_BYTES) {
            return Refusal.OVERSIZE;   // every char is at least one byte
        }
        byte[] bytes = utf8(json);
        if (bytes == null) {
            // A lone surrogate: String.getBytes would write '?' in its place, and the bytes read back would no
            // longer be the record the contract validated. A consumer's UTF-8 decoder never produces one.
            return Refusal.MALFORMED;
        }
        try {
            IvRvReading.requireWithinWire(bytes);
        } catch (IllegalArgumentException oversize) {
            return Refusal.OVERSIZE;
        }
        return null;
    }

    /** The string's UTF-8 bytes, or null when it holds a lone surrogate and so has none. */
    static byte[] utf8(String json) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(json));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException unencodable) {
            return null;
        }
    }

    /**
     * A held version's bytes as the string they encode; null, with the session failed, when they cannot be
     * read back intact.
     */
    private String read(Session session, Slot slot) {
        byte[] bytes = readBytes(session, slot);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] readBytes(Session session, Slot slot) {
        VolPremiumSessionLog log = session.log;
        if (log == null) {
            return null;
        }
        try {
            return log.read(slot.fileOffset, slot.length, slot.crc);
        } catch (IOException | RuntimeException failed) {
            failDisk(session, DiskOp.READ, failed);
            return null;
        }
    }

    /**
     * Exactly-once live delivery for one position: an offset at or below one already broadcast for that
     * position never is again. Since Codex r2 finding 1 only the gateway's cache consumer ingests the
     * vol-premium streams, so this guards a re-read of the partition (a retried cache attempt re-reading its
     * window), not a race between two readers. The fence lives ON the index entry, so it is dropped with
     * it — at rollover, at the session's end — and can never outlive the record it fences into a floor
     * that a recreated topic's offsets would have to climb back over.
     */
    synchronized boolean claimBroadcast(Position position, long offset) {
        Session session = sessions.get(position.seriesKey());
        if (session == null || !session.sessionDate.equals(position.sessionDate())) {
            return false;
        }
        Slot slot = session.of(position.phase()).find(position, session);
        if (slot == null || offset <= slot.broadcastFence) {
            return false;
        }
        slot.broadcastFence = offset;
        return true;
    }

    /**
     * The first held record strictly after {@code after} in replay order ({@code null}: from the
     * start), skipping any session that is no longer current. Reads the CURRENT version of each position,
     * so a replacement that lands before a replay reaches its position is the version that is replayed.
     * A version that cannot be read back intact fails its session and ends this walk's pass through it: the
     * walk moves on to the next series, never past the unreadable position to a later one of the same session.
     */
    synchronized Item next(Position after, long nowMs) {
        SortedMap<String, Session> from = after == null ? sessions : sessions.tailMap(after.seriesKey());
        for (Session session : from.values()) {
            if (!sessionCurrent(session.sessionDate, nowMs)) {
                continue;
            }
            int place = after == null ? -1 : session.placeOf(after);
            if (place > 0) {
                continue;
            }
            for (int phase = Position.OBSERVATIONS; phase <= Position.WARNINGS; phase++) {
                if (place == 0 && after.phase() > phase) {
                    continue;
                }
                Iterator<Slot> it = session.of(phase).after(place == 0 && after.phase() == phase ? after : null, session);
                if (!it.hasNext()) {
                    continue;
                }
                Slot slot = it.next();
                String json = read(session, slot);
                if (json == null) {
                    break;   // failed closed: this pass takes nothing more of this session
                }
                return new Item(session.positionOf(phase, slot), json);
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
        sessions:
        for (Session session : from.values()) {
            if (session.seriesKey.compareTo(upTo.seriesKey()) > 0) {
                return null;
            }
            if (!sessionCurrent(session.sessionDate, nowMs)) {
                continue;
            }
            int place = after == null ? -1 : session.placeOf(after);
            if (place > 0) {
                continue;
            }
            for (int phase = Position.OBSERVATIONS; phase <= Position.WARNINGS; phase++) {
                if (place == 0 && after.phase() > phase) {
                    continue;
                }
                Iterator<Slot> it = session.of(phase).after(place == 0 && after.phase() == phase ? after : null, session);
                while (it.hasNext()) {
                    Slot slot = it.next();
                    Position p = session.positionOf(phase, slot);
                    if (p.compareTo(upTo) > 0) {
                        return null;
                    }
                    if (slot.seq > sinceSeq.applyAsLong(p)) {
                        String json = read(session, slot);
                        if (json == null) {
                            continue sessions;   // failed closed: nothing more of this session in this pass
                        }
                        return new Item(p, json);
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

    /**
     * One series' current session as the REST route streams it: a SNAPSHOT of it, taken here under the lock (Codex
     * gateway r4, the page-consistency finding). The snapshot is named by three values: the session's
     * {@code number}; the store's admission sequence at this instant ({@code asOfSeq}); and how many positions each
     * stream held. It is exactly the positions held now, each in the version it holds now: the slots whose sequence
     * is at most {@code asOfSeq}.
     *
     * <p><b>Why the records a page writes are that snapshot, and nothing else</b>, whatever is admitted, replaced or
     * rewritten between its chunks:
     * <ol>
     *   <li>Every admission stamps its slot with a new, strictly greater admission sequence ({@link #admit}), whether it
     *       adds a position or replaces one; nothing else changes a slot's sequence. So a slot whose sequence is at most
     *       {@code asOfSeq} when a chunk reads it held that very version at the snapshot. A slot with a greater sequence
     *       was inserted after the snapshot, or replaced after it.</li>
     *   <li>A chunk writes only slots at or below {@code asOfSeq}, and passes over the others without reading them. So
     *       every record written is the snapshot's version of a position the snapshot holds.</li>
     *   <li>Positions never change and are never removed (a session's index is dropped whole), and each chunk resumes
     *       strictly after the last position the one before it visited. So no position is visited twice, and the
     *       records come out in the store's order.</li>
     *   <li>Hence a stream of the page is every record of the snapshot exactly when it wrote as many records as the
     *       snapshot held. A position of the snapshot is missed only when it was REPLACED before the page reached it:
     *       its slot then carries a later sequence, and the version the snapshot holds is no longer indexed. The count
     *       at the end of each stream detects exactly that ({@link Incomplete#CHANGED_WHILE_READ}). The page then writes
     *       nothing more; the other stream, if it is still to come, is not written.</li>
     *   <li>A rewrite (compaction) moves bytes, never versions or sequences, and each chunk resolves every offset
     *       under the lock through the log as it then is. So a rewrite between chunks changes nothing a page writes.
     *       Whatever triggered it is an admission, judged as above.</li>
     *   <li>The page is tied to its session's number. A rollover, the session's end or a close stops it at the next
     *       chunk ({@link Incomplete#SESSION_ENDED}). A record that does not read back intact stops it there, and
     *       nothing past it is written, the other stream included ({@link Incomplete#DISK_FAILURE}).</li>
     * </ol>
     * The verdict is also incomplete if the session failed its log or refused a record for its envelope at any point
     * before the verdict is read, even after the snapshot: a session is incomplete from its first refusal on,
     * whichever reader asks.
     *
     * <p>Cost: the page holds counts and a cursor between chunks, never records or offsets, and the socket path is
     * unchanged. During live traffic a page is complete unless a record it has yet to reach is replaced while it is
     * written. New frames and new transitions are left out of it; the sockets deliver them.
     */
    synchronized Page page(String seriesKey, LongSupplier clock) {
        Session session = sessions.get(seriesKey);
        if (session == null || !sessionCurrent(session.sessionDate, clock.getAsLong())) {
            return new StorePage(seriesKey, null, 0L, 0L, new int[2], 0L, clock);
        }
        return new StorePage(seriesKey, session.sessionDate, session.number, admissionSeq,
                new int[] {session.observations.size(), session.warnings.size()}, session.liveBytes, clock);
    }

    private enum ChunkEnd { MORE, END, SESSION_ENDED, UNREADABLE }

    private record PageChunk(List<byte[]> records, Position last, ChunkEnd end) {
    }

    /** The session a page was opened on, while it is still held and current; null once it is not. */
    private Session sessionOf(StorePage page, long nowMs) {
        Session session = sessions.get(page.seriesKey);
        return session == null || session.number != page.sessionNumber || !sessionCurrent(session.sessionDate, nowMs)
                ? null : session;
    }

    /**
     * A page's next chunk of one stream, after {@code after} (null: from the start), read under the lock: the
     * snapshot's records in order, up to {@link #PAGE_CHUNK_BYTES} of them. Each record's length is checked against
     * that bound before the record is read. Slots admitted after the snapshot are passed over unread. SESSION_ENDED when
     * the session is no longer the one the page opened on; UNREADABLE when a version could not be read back intact
     * (the session is then failed).
     */
    private synchronized PageChunk pageChunk(StorePage page, int phase, Position after, long nowMs) {
        Session session = sessionOf(page, nowMs);
        if (session == null) {
            return new PageChunk(List.of(), after, ChunkEnd.SESSION_ENDED);
        }
        List<byte[]> records = new ArrayList<>();
        long bytes = 0L;
        Slot visited = null;
        ChunkEnd end = ChunkEnd.END;
        Iterator<Slot> it = session.of(phase).after(after, session);
        while (it.hasNext()) {
            Slot slot = it.next();
            if (slot.seq > page.asOfSeq) {
                visited = slot;   // admitted after the snapshot: not part of it
                continue;
            }
            if (!records.isEmpty() && bytes + slot.length > PAGE_CHUNK_BYTES) {
                end = ChunkEnd.MORE;   // not visited: the next chunk begins with this slot
                break;
            }
            byte[] record = readBytes(session, slot);
            if (record == null) {
                end = ChunkEnd.UNREADABLE;
                break;
            }
            records.add(record);
            bytes += record.length;
            visited = slot;
        }
        return new PageChunk(records, visited == null ? after : session.positionOf(phase, visited), end);
    }

    /** The verdict of a page whose records have been written, with its session as it stands now. */
    private synchronized Retention verdict(StorePage page, long nowMs) {
        if (page.sessionDate == null) {
            return new Retention(null, 0L, 0L, 0L, seriesBudgetBytes);
        }
        Session s = sessionOf(page, nowMs);
        if (s == null) {
            return new Retention(Incomplete.SESSION_ENDED, 0L, 0L, 0L, seriesBudgetBytes);   // it ended while it was being read
        }
        Incomplete reason = s.diskFailed ? Incomplete.DISK_FAILURE
                : s.refusedForBudget > 0L ? Incomplete.SESSION_BUDGET
                : page.stopped;
        return new Retention(reason, s.refusedForBudget, s.refusedForDisk, page.snapshotBytes, seriesBudgetBytes);
    }

    /** A page over one snapshot of one session (see {@link #page}), used by the one thread that writes it out. */
    private final class StorePage implements Page {
        private final String seriesKey;
        private final String sessionDate;
        private final long sessionNumber;
        /** The store's admission sequence when the page was opened: the snapshot is the versions at or below it. */
        private final long asOfSeq;
        /** Positions each stream held at the snapshot, by phase. */
        private final int[] held;
        /** Live bytes of the snapshot: what a complete page's records add up to. */
        private final long snapshotBytes;
        private final LongSupplier clock;
        /** Why the page stopped, or found it had missed a record of its snapshot; null while neither happened. */
        private Incomplete stopped;

        StorePage(String seriesKey, String sessionDate, long sessionNumber, long asOfSeq, int[] held,
                  long snapshotBytes, LongSupplier clock) {
            this.seriesKey = seriesKey;
            this.sessionDate = sessionDate;
            this.sessionNumber = sessionNumber;
            this.asOfSeq = asOfSeq;
            this.held = held;
            this.snapshotBytes = snapshotBytes;
            this.clock = clock;
        }

        @Override
        public String sessionDate() {
            return sessionDate;
        }

        @Override
        public void writeObservations(OutputStream out) throws IOException {
            write(out, Position.OBSERVATIONS);
        }

        @Override
        public void writeWarnings(OutputStream out) throws IOException {
            write(out, Position.WARNINGS);
        }

        private void write(OutputStream out, int phase) throws IOException {
            boolean[] first = {true};
            forEach(phase, record -> {
                if (!first[0]) {
                    out.write(',');
                }
                out.write(record);
                first[0] = false;
            });
        }

        /**
         * Every record of one stream of the snapshot, chunk by chunk, in replay order. Stops at the first chunk that
         * could not be read, and at the end of a stream that missed a record of the snapshot; from then on it hands
         * over nothing more of the page, the other stream included.
         */
        private void forEach(int phase, RecordSink sink) throws IOException {
            if (sessionDate == null || stopped != null) {
                return;
            }
            Position after = null;
            int written = 0;
            while (true) {
                PageChunk chunk = pageChunk(this, phase, after, clock.getAsLong());
                for (byte[] record : chunk.records()) {
                    sink.accept(record);
                }
                written += chunk.records().size();
                switch (chunk.end()) {
                    case MORE -> after = chunk.last();
                    case SESSION_ENDED -> {
                        stopped = Incomplete.SESSION_ENDED;
                        return;
                    }
                    case UNREADABLE -> {
                        stopped = Incomplete.DISK_FAILURE;
                        return;
                    }
                    case END -> {
                        if (written != held[phase]) {
                            // A position of the snapshot was passed over: replaced before the page reached it.
                            stopped = Incomplete.CHANGED_WHILE_READ;
                        }
                        return;
                    }
                }
            }
        }

        /**
         * The verdict as it stands after the records were written: complete only if they were every record of the
         * snapshot and the session is still the one opened on, current, and whole.
         */
        @Override
        public Retention retention() {
            return verdict(this, clock.getAsLong());
        }
    }

    /**
     * One series' current session materialised as verbatim records in replay order, read through the same
     * chunks a {@link Page} streams; empty when there is none. Tests and diagnostics: a whole envelope would not fit
     * the heap this way, which is why the REST route streams.
     */
    Snapshot snapshot(String seriesKey, long nowMs) {
        StorePage page = (StorePage) page(seriesKey, () -> nowMs);
        List<String> observations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            page.forEach(Position.OBSERVATIONS, record -> observations.add(new String(record, StandardCharsets.UTF_8)));
            page.forEach(Position.WARNINGS, record -> warnings.add(new String(record, StandardCharsets.UTF_8)));
        } catch (IOException impossible) {
            throw new java.io.UncheckedIOException(impossible);   // the sinks above write to memory
        }
        Retention r = page.retention();
        return new Snapshot(page.sessionDate, List.copyOf(observations), List.copyOf(warnings), r.reason(),
                r.refusedForBudget(), r.refusedForDisk(), r.retainedBytes(), r.budgetBytes());
    }

    /** Where a page hands its records: an output stream, or a list. */
    private interface RecordSink {
        void accept(byte[] record) throws IOException;
    }

    /** Drops every session that is no longer current, deleting its log. Returns how many were dropped. */
    synchronized int purge(long nowMs) {
        int before = sessions.size();
        sessions.values().removeIf(session -> {
            if (sessionCurrent(session.sessionDate, nowMs)) {
                return false;
            }
            release(session);
            return true;
        });
        if (sessions.size() < maxSymbols) {
            symbolCapLogged = false;
        }
        return before - sessions.size();
    }

    /** Deletes every session's log and this process's directory. The gateway's shutdown calls it. */
    synchronized void close() {
        for (Session session : sessions.values()) {
            release(session);
        }
        sessions.clear();
        if (directory != null) {
            try {
                io.deleteTree(directory);
            } catch (IOException | RuntimeException failed) {
                diskErrors[DiskOp.DELETE.ordinal()].incrementAndGet();
                System.out.println("ERROR vol-premium: the session log directory " + directory + " could not be deleted ("
                        + failed + ")");
            }
            directory = null;
        }
    }

    long refusals(Stream stream, Refusal reason) {
        return refusals[stream.ordinal()][reason.ordinal()].get();
    }

    long diskErrors(DiskOp op) {
        return diskErrors[op.ordinal()].get();
    }

    long rewrites() {
        return rewrites.get();
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

    /** Live bytes of every held session (the figure the disk budget is enforced on). */
    synchronized long heldBytes() {
        long n = 0L;
        for (Session s : sessions.values()) {
            n += s.liveBytes;
        }
        return n;
    }

    /** Bytes in every held session's log file: the live versions and the superseded ones not yet rewritten away. */
    synchronized long logFileBytes() {
        long n = 0L;
        for (Session s : sessions.values()) {
            n += s.log == null ? 0L : s.log.end();
        }
        return n;
    }

    /** The heap the index is charged: each session's overhead and {@link #ENTRY_BYTES} per held position. */
    synchronized long indexBytes() {
        long n = 0L;
        for (Session s : sessions.values()) {
            n += SERIES_OVERHEAD_BYTES + ((long) s.observations.size() + s.warnings.size()) * ENTRY_BYTES;
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

    synchronized int sessionsDiskFailed() {
        int n = 0;
        for (Session s : sessions.values()) {
            if (s.diskFailed) {
                n++;
            }
        }
        return n;
    }

    /** Test seam: the log file of a held session, or null. */
    synchronized Path logPathForTest(String seriesKey) {
        Session s = sessions.get(seriesKey);
        return s == null || s.log == null ? null : s.log.path();
    }

    /** Test seam: this process's log directory, or null before the first session. */
    synchronized Path directoryForTest() {
        return directory;
    }

    /** Test seam: replace the file system under the store (a failing one); the next session creates its directory anew. */
    synchronized void ioForTest(VolPremiumSessionLog.Io replacement) {
        this.io = replacement;
        this.directory = null;
    }

    /** Test seam: the index entry of a held position, or null. */
    synchronized Slot slotForTest(Position position) {
        Session s = sessions.get(position.seriesKey());
        return s == null ? null : s.of(position.phase()).find(position, s);
    }

    /** Test seam: every object the index retains — each slot, each chunk and its array, each chunk directory. */
    synchronized List<Object> indexObjectsForTest() {
        List<Object> out = new ArrayList<>();
        for (Session s : sessions.values()) {
            for (SlotList list : List.of(s.observations, s.warnings)) {
                list.containerObjects(out);
                for (Slot slot : list) {
                    out.add(slot);
                }
            }
        }
        return out;
    }

    /** Prometheus text for the store: refusals by stream and reason, disk errors by operation, and what is held. */
    String metricsText() {
        StringBuilder sb = new StringBuilder()
                .append("# HELP gateway_vol_premium_refused_total vol-premium records NOT admitted to the ")
                .append("session cache, by stream and reason. SESSION_BUDGET and SYMBOL_CAP are the declared ")
                .append("envelope refusing, DISK_FAILURE a failed session log; the rest are contract, key, ordering ")
                .append("and session gates.\n")
                .append("# TYPE gateway_vol_premium_refused_total counter\n");
        for (Stream stream : Stream.values()) {
            for (Refusal reason : Refusal.values()) {
                sb.append("gateway_vol_premium_refused_total{stream=\"").append(stream.label)
                        .append("\",reason=\"").append(reason.name()).append("\"} ")
                        .append(refusals(stream, reason)).append('\n');
            }
        }
        sb.append("# HELP gateway_vol_premium_disk_errors_total Failures of the vol-premium session log, by operation. ")
                .append("Any but DELETE fails its session closed. Alert on any increase.\n")
                .append("# TYPE gateway_vol_premium_disk_errors_total counter\n");
        for (DiskOp op : DiskOp.values()) {
            sb.append("gateway_vol_premium_disk_errors_total{op=\"").append(op.name()).append("\"} ")
                    .append(diskErrors(op)).append('\n');
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
                .append("# HELP gateway_vol_premium_cached_bytes Live bytes of every held vol-premium session on disk ")
                .append("(the disk budget is enforced on it).\n")
                .append("# TYPE gateway_vol_premium_cached_bytes gauge\n")
                .append("gateway_vol_premium_cached_bytes ").append(heldBytes()).append('\n')
                .append("# HELP gateway_vol_premium_log_file_bytes Bytes in the session log files, superseded versions ")
                .append("not yet rewritten away included.\n")
                .append("# TYPE gateway_vol_premium_log_file_bytes gauge\n")
                .append("gateway_vol_premium_log_file_bytes ").append(logFileBytes()).append('\n')
                .append("# HELP gateway_vol_premium_index_bytes Heap charged to the session index (an upper bound).\n")
                .append("# TYPE gateway_vol_premium_index_bytes gauge\n")
                .append("gateway_vol_premium_index_bytes ").append(indexBytes()).append('\n')
                .append("# HELP gateway_vol_premium_log_rewrites_total Session log rewrites (compactions).\n")
                .append("# TYPE gateway_vol_premium_log_rewrites_total counter\n")
                .append("gateway_vol_premium_log_rewrites_total ").append(rewrites()).append('\n')
                .append("# HELP gateway_vol_premium_series_budget_bytes Disk budget of one series' session (live bytes).\n")
                .append("# TYPE gateway_vol_premium_series_budget_bytes gauge\n")
                .append("gateway_vol_premium_series_budget_bytes ").append(seriesBudgetBytes).append('\n')
                .append("# HELP gateway_vol_premium_sessions_over_budget Current sessions that refused a record ")
                .append("for the envelope and are therefore INCOMPLETE. Alert on > 0.\n")
                .append("# TYPE gateway_vol_premium_sessions_over_budget gauge\n")
                .append("gateway_vol_premium_sessions_over_budget ").append(sessionsOverBudget()).append('\n')
                .append("# HELP gateway_vol_premium_sessions_disk_failed Current sessions whose log failed and which ")
                .append("are therefore INCOMPLETE. Alert on > 0.\n")
                .append("# TYPE gateway_vol_premium_sessions_disk_failed gauge\n")
                .append("gateway_vol_premium_sessions_disk_failed ").append(sessionsDiskFailed()).append('\n');
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

    private Admission refuse(Stream stream, Refusal reason) {
        refusals[stream.ordinal()][reason.ordinal()].incrementAndGet();
        return new Admission(null, false, reason);
    }
}
