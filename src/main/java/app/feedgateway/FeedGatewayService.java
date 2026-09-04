package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import app.feedgateway.mtsession.RoutableRecord;
import app.feedgateway.mtsession.AppSession;
import app.feedgateway.mtsession.EventType;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.gateway.ReplayChronology;
import app.feedgateway.mtsession.gateway.ReplayParams;
import app.feedgateway.mtsession.gateway.ReplayTopicResolver;
import app.feedgateway.mtsession.gateway.ReplayRunner;
import app.feedgateway.mtsession.gateway.DealerLedgerJoiner;
import app.feedgateway.mtsession.gateway.GatewayRecordMapper;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import java.util.Optional;
import java.util.OptionalDouble;
import org.springframework.lang.Nullable;
import com.optionsedge.contracts.hpsf.HpsfAuditEvent;
import com.optionsedge.contracts.hpsf.HpsfExitIntentEvent;
import com.optionsedge.contracts.hpsf.HpsfSignal;
import com.optionsedge.contracts.hpsf.HpsfTopics;
import com.optionsedge.contracts.hpsf.MarketFlowSnapshot;
import com.optionsedge.contracts.hpsf.StrikeScoreSnapshot;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FeedGatewayService implements ReplayRunner {
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5L;

    /**
     * Every RECORD-surfacing consumer reads {@code read_committed} so a record from an ABORTED pre-open GEX
     * transaction is never surfaced (PREOPEN-GEX-GATE1-REQUIREMENT.md §6). HARD-CODED, not env-tunable: a single
     * wrong {@code read_uncommitted} value would silently defeat the safety property on the live/cache/replay
     * consumers. On the non-transactional topics this is identical to read_uncommitted.
     */
    static final String RECORD_CONSUMER_ISOLATION = "read_committed";

    /**
     * The source-switch barrier ({@link #captureOffsetBarriers}) reads {@code endOffsets()} as a PHYSICAL high
     * watermark. Under {@code read_committed} that call returns the last-STABLE offset instead, so an open
     * transaction on an output topic (the databento gex topic IS one) would capture a too-low barrier and
     * mis-classify records across a source switch. This consumer NEVER polls records, so {@code read_uncommitted}
     * is safe here and is REQUIRED for a correct physical-offset barrier.
     */
    static final String BARRIER_CONSUMER_ISOLATION = "read_uncommitted";
    private final Instant startedAt = Instant.now();
    private final GatewaySettings settings;
    private final GatewayMarketCalendar marketCalendar;
    // The ET trading date the AUTO expiry last rolled to. Seeded from the initial selection; advanced by
    // maybeAutoRollExpiry on each new trading day. A manual (control-topic) selection does NOT change it,
    // so the manual pick holds for the day and the auto-roll resumes the next trading day. Only meaningful
    // when settings.autoExpiry().
    private volatile String autoRolledExpiry;
    private final ObjectMapper mapper;
    private final HpsfGatewayViewMapper hpsfViewMapper;
    private final Set<WebSocketSession> clients = new CopyOnWriteArraySet<>();
    private final Map<String, WebSocketSession> clientsById = new ConcurrentHashMap<>();
    // P0 (slow-client isolation): per-socket bounded async outbound queues. The Kafka thread enqueues here
    // and returns; dedicated writers do the network I/O, so one slow client never stalls polling.
    private final Map<String, OutboundChannel> outbound = new ConcurrentHashMap<>();
    private volatile ExecutorService outboundWriters;
    private volatile java.util.concurrent.Executor outboundWriterOverride; // test seam (caller-runs)
    private final AtomicLong wsEnqueued = new AtomicLong();
    private final AtomicLong wsCoalesced = new AtomicLong();
    private final AtomicLong wsSent = new AtomicLong();
    private final AtomicLong wsSlowDisconnects = new AtomicLong();
    private final AtomicLong wsWriteErrors = new AtomicLong();
    private final AtomicLong wsDroppedOnClose = new AtomicLong();
    private final OutboundChannel.Metrics outboundMetrics = new OutboundChannel.Metrics() {
        @Override public void enqueued(int bytes) { wsEnqueued.incrementAndGet(); }
        @Override public void coalesced() { wsCoalesced.incrementAndGet(); }
        @Override public void sent(int bytes) { wsSent.incrementAndGet(); }
        @Override public void disconnectedSlow() { wsSlowDisconnects.incrementAndGet(); }
        @Override public void writeError() { wsWriteErrors.incrementAndGet(); }
        @Override public void droppedOnClose(int messages) { wsDroppedOnClose.addAndGet(messages); }
    };
    private static final Set<String> COALESCABLE_EVENTS = Set.of(
            "snapshot", "pace", "pace-rank", "directional-pressure", "strike-flow", "seller-activity", "spot-band", "delta-flow", "strike-intel", "option-truth", "strike-invasion", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "gex-by-strike",
            "gex-oi-status",
            "strike-sr",
            "gex-magnet",
            "gamma-migration",
            "es-gex",
            "es-strike-intel",
            // A replaceable 1 Hz current-state frame: behind a slow client only the newest verdict
            // matters, and letting frames queue contributes to avoidable disconnects.
            "delta-flow-accel",
            "gex-strike-lifecycle",
            "max-pain",
            "liquidity-heatmap",
            "option-price-behavior",
            "dealer-ledger",
            "corridor-gauge",
            "zero-dte-intelligence",
            "greek-move-auth",
            "spot-vol-regime",
            "vol-premium-ivrv",
            "indicators",
            "tapeZones",
            "opb-by-option", "opb-session",
            "index-price", "vix-price", "spx-price", "hpsf-latest-signal", "hpsf-market-flow", "hpsf-top-candidates",
            "hpsf-audit", "hpsf-exit-intent");
    // Clock-skew allowance for the greek-move-authenticity verdict's PAYLOAD decision time
    // (asOfEventTimeMs, a past market observation): a verdict stamped more than this far ahead of the
    // gateway wall clock is treated as malformed and fails closed, so a bad future timestamp can neither
    // evade the freshness window nor poison the monotonic last-value-wins supersede gate. Generous enough
    // to absorb realistic inter-service NTP skew; tight enough that a poisoned record can never freeze a
    // symbol's track for more than this bound.
    private static final long GREEK_MOVE_AUTH_MAX_FUTURE_SKEW_MS = 60_000L;
    // Same clock-skew fail-closed bound for the spot-vol-regime snapshot's asOfEventTimeMs (a past
    // stream-time observation): a future-dated record must neither evade the SHORT freshness window
    // nor poison the monotonic supersede gate.
    private static final long SPOT_VOL_REGIME_MAX_FUTURE_SKEW_MS = 60_000L;
    /** Only shape the gateway knows how to police; anything else is refused, never guessed at. */
    private static final int STRIKE_BAND_SCHEMA_VERSION = 1;
    /**
     * Hard ceiling on the latched strike list. A US RTH session that traversed 512 strikes at the SPX
     * 5-point grid would have moved 2,560 points — impossible. The bound exists so a producer bug can
     * never push an unbounded array through the cache and out to every connected browser.
     */
    private static final int STRIKE_BAND_MAX_MARKS = 512;
    /**
     * FROZEN vocabulary: only the two SUSPECT regimes mark strikes. CONFIRMED_UP/CONFIRMED_DOWN/
     * STABLE_UP/NEUTRAL/UNKNOWN never do — a band claiming otherwise is a producer defect, not a new
     * feature to pass through.
     */
    private static final Set<String> STRIKE_BAND_REGIMES = Set.of("DIVERGENT_UP", "COMPLACENT_DOWN");
    private final SessionRoutingEngine routingEngine;
    private final Map<String, String> snapshots = new ConcurrentHashMap<>();
    private final Map<String, String> paces = new ConcurrentHashMap<>();
    private final Map<String, String> paceRanks = new ConcurrentHashMap<>(); // board-level pace ranking, keyed by boardKey
    private final Map<String, String> directionalPressures = new ConcurrentHashMap<>();
    private final Map<String, String> strikeFlows = new ConcurrentHashMap<>();
    // Per-strike premium binned by where spot stood. Modelled on strikeFlows (a plain in-memory
    // map, not seller-activity's disk store) because a band matrix is a short sparse list, not a
    // session history — but it follows seller-activity's OTHER rule: never broadcast one record at
    // a time. ~200 strikes republished on change would fill the chain's outbound queue; it rides
    // the ui-batch instead, exactly as strikeFlows does. That holds on EVERY route: the live
    // consumer skips it (see the spot-band continue) and replayCachedToSocket does not replay it.
    private final Map<String, String> spotBands = new ConcurrentHashMap<>();
    // socketId -> the readiness key whose completed band board this socket has already been sent by a
    // SWITCH-time replay. Readiness and the connect bracket both race to serve the same board; rather
    // than reason about which iterator ordering wins, both go through it and the loser is a no-op.
    // Connect and return-to-live replays are NOT deduplicated here — a reset owes a fresh board. That is
    // also why a STALE claim cannot cost a socket its board: if an id were ever reused, the new
    // connection still gets the full board from the connect replay, which never consults this ledger.
    // Bounded by the live socket set on every switch (see replaySpotBandBatchAfterSourceSwitch).
    private final Map<String, String> spotBandSwitchDelivered = new ConcurrentHashMap<>();
    private final SellerActivityDiskStore sellerActivityStore = new SellerActivityDiskStore();
    // Per-strike delta-flow snapshots, keyed by source|symbol|expiry|strike (last-value-wins per
    // strike). JSON on the wire (DeltaFlowStrikeSnapshot) — lives on the JSON-state consumer.
    private final Map<String, String> deltaFlows = new ConcurrentHashMap<>();
    // Last StrikeIntelligenceDashboard per symbol ("strike-cluster" event): replayed on connect so a
    // refreshed page repaints the recent-signals trail instantly instead of waiting for the next
    // dashboard interval (§9b, user 2026-07-17).
    private final Map<String, String> strikeClusters = new ConcurrentHashMap<>();
    // Per-strike strike-intelligence signals, keyed by source|symbol|expiry|strike (last-value-wins per
    // strike). JSON on the wire (StrikeIntelligenceSignal) — lives on the JSON-state consumer.
    private final Map<String, String> strikeIntels = new ConcurrentHashMap<>();
    // Option Truth pair readings, keyed source|symbol|expiry|strike|horizon so STEP and
    // SESSION_ANCHOR never overwrite each other. JSON on the state consumer.
    private final Map<String, String> optionTruths = new ConcurrentHashMap<>();
    // Hot Strike of the Day envelope per symbol ("hot-strike" event): last-value-wins,
    // replayed on connect so a fresh page gets the day's gold mark immediately (§4.4).
    // The newest-record / SPX_NATIVE-preference logic is CLIENT-side by design.
    private final Map<String, String> hotStrikes = new ConcurrentHashMap<>();
    // Per-strike, per-direction strike-invasion signals, keyed by source|symbol|strike|direction
    // (SPX-only — NO expiry; last-value-wins per strike+direction). One strike can legitimately carry
    // BOTH a live UP record (SHORT_CALL_CANDIDATE domain) and a DOWN record (SHORT_PUT_CANDIDATE
    // domain) at once, so the key must separate them or one verdict silently overwrites the other.
    // JSON on the wire (StrikeInvasionSnapshot) — lives on the JSON-state consumer (mirrors
    // strike-intel, minus the expiry segment).
    private final Map<String, String> strikeInvasions = new ConcurrentHashMap<>();
    // Latest liquidity-heatmap column frame per symbol|expiry (last-value-wins; short TTL —
    // see GatewaySettings.liquidityHeatmapTtlMs()). History accumulates client-side.
    private final Map<String, String> liquidityHeatmaps = new ConcurrentHashMap<>();
    private final Map<String, String> missionPaces = new ConcurrentHashMap<>();
    private final Map<String, String> missionControls = new ConcurrentHashMap<>();
    // Whole-underlying spread-skew snapshot, keyed by source|underlying (SINGLE value, last snapshot
    // wins). JSON on the wire (SpreadSkewSnapshot) — lives on the JSON-state consumer (the
    // mission-control sibling; its discrete spread-skew-event siblings are never cached).
    private final Map<String, String> spreadSkews = new ConcurrentHashMap<>();
    private final Map<String, String> indexPrices = new ConcurrentHashMap<>();
    // P1 (VIX/underlying consistency): VIX is cached SEPARATELY from ES/index so each cache entry keeps its
    // ORIGINAL event type (vix-price vs index-price) on replay, instead of being flattened to index-price.
    // This map is also the "last known VIX" — replayed when present, omitted when absent (VIX is optional).
    private final Map<String, String> vixPrices = new ConcurrentHashMap<>();
    // Canonical SPX spot (underlying.spx.price, UnderlyingPriceEvent JSON) cached SEPARATELY from
    // ES/index so it keeps its ORIGINAL event type (spx-price) on replay — the vix-price idiom. This is
    // the SSOT spot the UI displays for the SPX chain; its payload source is the cascade tier
    // (NATIVE_SPX_INDEX / ES_BASIS_DERIVED / SYNTHETIC_OPTION_SPOT), NEVER "DATABENTO", which is why it
    // must not ride the index-price event (isTrustedIndexPrice would fail-closed drop it).
    private final Map<String, String> spxPrices = new ConcurrentHashMap<>();
    private final Map<String, String> currentStates = new ConcurrentHashMap<>();
    private final Map<String, String> gexByStrike = new ConcurrentHashMap<>();
    // Per-strike OI-arrival status (OI_MISSING/OI_OK) from the gex watchdog, keyed symbol|expiry|strike
    // (last-value-wins like gex-by-strike): the UI's explicit badge when fail-closed GEX publishes nothing.
    private final Map<String, String> gexOiStatus = new ConcurrentHashMap<>();
    // Pre-open IBKR GEX status/control rows (rev13 Phase 3), keyed by the record key: strike
    // rows ("SPX|<D>|<strike>") AND "__"-prefixed controls (path/manifest/heartbeat/ownership)
    // — the UI needs BOTH to drive per-strike chips + window state. Dark unless enabled.
    private final Map<String, String> ibkrPreOpenStatus = new ConcurrentHashMap<>();
    // ---- Pre-open IBKR GEX value plane (rev13 Phase 3 slice 2: R-ARB + the R-STOP frozen-projection
    // cache). Sessioned value records arriving on the SHARED live topic (USER D14) are arbitrated off
    // the Databento pipeline into this plane; Databento records are NEVER touched (D11). Keyed by the
    // payload-derived strike identity (symbol|expiry|strike) so the frozen-projection takeover can
    // join an incoming Databento record to its frozen strike regardless of key formatting. All
    // mutations run under ibkrPreOpenGexLock; the maps are concurrent only so read-mostly fast paths
    // (emptiness probes, replay snapshots) need no lock.
    private final Map<String, IbkrPreOpenGexCandidate> ibkrPreOpenGexCandidates = new ConcurrentHashMap<>();
    private final Map<String, IbkrPreOpenGexCandidate> ibkrPreOpenFrozenProjections = new ConcurrentHashMap<>();
    // Values provably committed BEFORE their fence whose revision-equal number-bearing status has
    // not (yet) been OBSERVED — the two streams ride independent consumers, so a restarted gateway
    // can read the value first (round-1 finding 1). Re-evaluated when a status arrives and at every
    // sweep; never presented while pending; dies unpaired at fence+10min (frozen-blank forever).
    private final Map<String, IbkrPreOpenGexCandidate> ibkrPreOpenPendingProjections = new ConcurrentHashMap<>();
    // Strikes terminally evicted by a Databento takeover (identity -> that window's fence): a
    // late-observed pre-fence value (compacted redelivery) or a late-completing pending pair must
    // never resurrect a taken-over strike — evictions are terminal (R-ARB: frozen values die
    // forever). Released with the window's bookkeeping at fence+10min. Access under the lock.
    private final Map<String, Long> ibkrPreOpenTakenOverStrikes = new ConcurrentHashMap<>();
    // Newest broker CreateTime of a DATABENTO record observed per strike identity on the shared
    // live topic. Restart race (round-3 finding 2): the live consumer starts at END and can
    // observe a post-09:30 Databento record BEFORE the rewinding cache consumer reconstructs the
    // strike's frozen/pending state — with no state to act on, the takeover would be lost and
    // the later reconstruction would resurrect the strike. Every projection-creating path
    // therefore consults this memory (isIbkrPreOpenStrikeTakenOverLocked) and converts a prior
    // post-boundary observation into the terminal tombstone at reconstruction time. An entry is
    // only decision-relevant while a window whose boundary its commit time crosses can still
    // reconstruct (≤5 min); the sweep retires entries at commit + 10 min (2x margin). Written by
    // the intercept path (concurrent map), read under the plane lock.
    /**
     * Per-strike memory of the newest Databento record OBSERVED for it, carrying BOTH axes of the
     * newly-ingested predicate (round-4 finding 3): the broker commit time AND the partition
     * position it was seen at. Commit time alone is not the predicate — a record committed after
     * the 09:30 boundary but sitting at/below the snapshotted high-watermark is a prior record and
     * must NOT take a strike over, so recording only its commit time would let a later pending
     * promotion or pre-fence reconstruction be killed by something the immediate path correctly
     * refused.
     */
    private record IbkrPreOpenDatabentoObservation(long commitMs, int partition, long offset) {
    }

    private final Map<String, IbkrPreOpenDatabentoObservation> ibkrPreOpenDatabentoMaxCommitMs =
            new ConcurrentHashMap<>();
    private final Object ibkrPreOpenGexLock = new Object();
    // Highest broker offset OBSERVED per partition of the shared live gex topic (both consumers feed
    // it). Snapshotted per fence at the 09:30 takeover boundary: that snapshot IS the gateway's
    // "09:30 high-watermark" — a record at/below it is a compacted/bootstrap/cached prior record and
    // can never evict a frozen projection (R-STOP/O5).
    private final Map<Integer, Long> ibkrPreOpenSharedGexMaxSeenOffsets = new ConcurrentHashMap<>();
    // fenceMs (= the session's validUntilMs, 09:25 ET) -> per-partition watermark snapshot taken at
    // fence + 5 min. Entries are removed when the fence's window is destroyed (fence + 10 min).
    private final Map<Long, Map<Integer, Long>> ibkrPreOpenTakeoverWatermarks = new ConcurrentHashMap<>();
    // Newest output generation observed per session DATE (keyed like the __generation and
    // __revocation controls: "<D>"), advanced by BOTH observed value records AND
    // __generation|<D>|<gen> controls on the status stream — R-WIRE.2's "max observed output
    // generation per session", round-2 finding 3. Access under ibkrPreOpenGexLock.
    // Bounded LRU: sessions die daily, so anything beyond a few entries is leftover bookkeeping.
    private final Map<String, Long> ibkrPreOpenMaxGeneration =
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > 64;
                }
            };
    // Revoked (sessionDate|generation) pairs from __revocation controls (access under
    // ibkrPreOpenGexLock). Bounded: a revocation only matters inside its own session window.
    private final java.util.LinkedHashSet<String> ibkrPreOpenRevokedGenerations = new java.util.LinkedHashSet<>();
    // Fail-closed drops of sessioned records on the shared topic (unknown tuple, PREOPEN conflict,
    // missing identity/validity) — never presented, never conditioning Databento (R-WIRE.1 scoping).
    private final AtomicLong ibkrPreOpenGexDroppedSessioned = new AtomicLong();
    // Valid-tuple records rejected by arbitration (regressed offset, superseded/revoked generation,
    // post-fence observation, window over) — visibility for the old-generation-straggler drills.
    private final AtomicLong ibkrPreOpenGexRejected = new AtomicLong();

    /** Sweep failures. Scraped: a stuck sweeper leaves projections on screen past 09:35. */
    private final AtomicLong ibkrPreOpenSweepFailures = new AtomicLong();

    /**
     * When this process started. A window's 09:30 boundary was OBSERVED LIVE only if this process
     * was already running when it passed — and that distinction decides which takeover predicate
     * applies (round-5 finding 1). Observed live: both axes, because the high-watermark snapshot is
     * a real record of where the gateway stood on each partition at the boundary. NOT observed live
     * (the gateway restarted after 09:30): the pinned rule is commit-time-only recovery, and a
     * watermark must NEVER be synthesized retrospectively — a snapshot taken now would contain
     * offsets read after the boundary, which for a second strike can include a record already
     * observed post-boundary and let the first strike resurrect.
     */
    private long ibkrPreOpenProcessStartMs = System.currentTimeMillis();

    /**
     * Every fence this process has SEEN named by a value or a path control, whether or not a
     * projection was ever built for it. The boundary snapshot is keyed off this rather than off the
     * projection planes, so a process that crosses 09:30 while reconstruction lags still fences at
     * the right instant instead of synthesizing one later.
     */
    private final Set<Long> ibkrPreOpenObservedFences = ConcurrentHashMap.newKeySet();
    private final Map<String, String> strikeSr = new ConcurrentHashMap<>();
    private final Map<String, String> gexMagnet = new ConcurrentHashMap<>();
    private final Map<String, String> gammaMigration = new ConcurrentHashMap<>();
    /** Corridor-gauge live state per symbol|expiry (JSON, last-value-wins). Standalone event. */
    private final Map<String, String> corridorGauges = new ConcurrentHashMap<>();
    /**
     * Peak rotation windows and the raw move log, per chain. Same shape as gammaMigration.
     *
     * <p>REST-ONLY, deliberately: it is consumed by {@code GET /api/gamma-rotation} and nothing
     * else. It is therefore consumed into this cache and evicted with it, but is NOT plumbed
     * through the websocket paths — no EventType, no pending map, no per-session replay, no batch
     * field. Half-wiring it there is worse than not wiring it: entries would be collected into a
     * snapshot that has no field to carry them and silently dropped, and every one of those sites
     * would then have to be kept in step for a delivery path with no subscriber. If a websocket
     * consumer ever wants this, wire it in ONE change across all of those sites (Codex).
     */
    private final Map<String, String> gammaRotation = new ConcurrentHashMap<>();
    /** Leader-fragility panel per chain (GMS-R14). REST-only, same contract as gammaRotation. */
    private final Map<String, String> gammaFragility = new ConcurrentHashMap<>();
    // ES-on-SPX aligned whole-book per symbol|expiry (JSON, roll-forward: latest emitEventTimeMs wins).
    private final Map<String, String> esGex = new ConcurrentHashMap<>();
    // ES strike-intelligence projected onto SPX strikes, keyed by NATIVE ES identity (symbol|expiry|esStrike,
    // the Kafka key) so colliding SPX strikes stay distinct; withdrawn by tombstone (evictEsStrikeIntelTombstone).
    private final Map<String, String> esStrikeIntel = new ConcurrentHashMap<>();
    // Per-strike gamma lifecycle (emerging/sticky/fading), keyed symbol|expiry|strike (last-value-wins,
    // like gex-by-strike). rev-17: the producer emits ONLY active strikes plus a one-shot NEUTRAL "clear"
    // when a strike goes inactive; that NEUTRAL overwrites the cache entry, so a reconnect replays NEUTRAL
    // (no badge) for a departed strike. Cache is model-agnostic — no gateway-side frame bookkeeping.
    private final Map<String, String> gexStrikeLifecycle = new ConcurrentHashMap<>();
    private final Map<String, String> maxPain = new ConcurrentHashMap<>();
    // Agent A short-premium recommendations, cached per trade_id (last-value-wins), replayed on connect.
    private final Map<String, String> shortPremiumRecommendations = new ConcurrentHashMap<>();
    // ES 09:15 open-direction forecast (ONE per tradeDate) + per-horizon outcomes (tradeDate|horizon —
    // H1/H2/H3 kept side-by-side), cached last-value-wins with the long esOpenDirectionTtlMs window and
    // replayed on connect, so a client that joins at 11:00 still receives the 09:15 forecast plus every
    // outcome resolved so far. JSON pass-through, GLOBAL advisory like short-premium (never selection-
    // gated, never subject to the market-data staleness gates) — lives on the JSON-state consumer.
    private final Map<String, String> esOpenDirectionForecasts = new ConcurrentHashMap<>();
    private final Map<String, String> esOpenDirectionOutcomes = new ConcurrentHashMap<>();
    // ES open-direction LIVE STATUS (60s heartbeat while a session is active): ONE current value per
    // source|tradeDate (last heartbeat wins), so a late-joining client gets the CURRENT status on
    // connect. Unlike the forecast/outcome siblings it lives on the SHORT esOpenDirectionStatusTtlMs
    // window (default 5 min) — a stale status is evicted/suppressed rather than replayed (the UI strip
    // simply vanishes). Same standalone/global/JSON pass-through delivery class as the siblings.
    private final Map<String, String> esOpenDirectionStatuses = new ConcurrentHashMap<>();
    // Greek-move-authenticity CURRENT verdict (the standalone service's MoveAuthenticityVerdict): ONE
    // current value per symbol (last-value-wins), so a late-joining client gets the CURRENT verdict on
    // connect. Like the es-open-direction live STATUS it lives on the SHORT greekMoveAuthTtlMs window
    // (default 5 min) — a stale verdict (dead producer, overnight leftover) is evicted/suppressed rather
    // than replayed (the UI move-authenticity track simply vanishes), never rendered as live. Same
    // standalone/global/JSON pass-through delivery class as the open-direction siblings; NOT in the
    // ui-batch. JSON pass-through; keyed by symbol (updateCache source-prefixes it to source|symbol,
    // exactly like es-open-direction-status keys by source|tradeDate and zero-dte by source|symbol|session).
    private final Map<String, String> greekMoveAuthCurrent = new ConcurrentHashMap<>();
    // Spot-vol regime CURRENT snapshot (the standalone service's SpotVolRegimeSnapshot): ONE current
    // value per symbol (last-value-wins) on the SHORT spotVolRegimeTtlMs window, so a stale regime
    // (dead producer, overnight leftover) is evicted/suppressed rather than replayed — the UI regime
    // pill simply vanishes. Same standalone/global/JSON pass-through delivery class as the
    // greek-move-auth sibling above; NOT in the ui-batch. Keyed by symbol.
    private final Map<String, String> spotVolRegime = new ConcurrentHashMap<>();
    // Vol-premium IV-vs-realised reading: ONE current value per SYMBOL|sessionDate
    // (last-value-wins) on the SHORT volPremiumIvrvTtlMs window, so a stale reading (dead producer,
    // overnight leftover) is evicted rather than replayed as live — the chart simply has no current
    // point. Same standalone/global/JSON pass-through class as the spot-vol-regime sibling above;
    // NOT in the ui-batch.
    private final Map<String, String> volPremiumIvrv = new ConcurrentHashMap<>();
    // Indicator CURRENT snapshots: ONE per canonical symbol (ES|SPX), per-symbol
    // cache + (runId, revision) supersession (rev 14 §6.9/§8): a new runId is
    // accepted in arrival(=offset) order on the single-partition compacted topic and
    // retires the prior; within a run, revisions must strictly increase; retired-run
    // returns are rejected.
    private final Map<String, String> indicatorsCurrent = new ConcurrentHashMap<>();
    private final Map<String, String> indicatorsRunId = new ConcurrentHashMap<>();
    private final Map<String, Long> indicatorsRevision = new ConcurrentHashMap<>();
    /** r1 finding 7 / r2 finding 4: retirement memory is bounded at a cap no real
     * deployment approaches (one retirement per producer restart; 4096 ≈ years of
     * restarts within one gateway uptime), so eviction can never let a genuinely
     * retired run re-enter in practice — while heap stays bounded (~400 KiB max). */
    private static final int INDICATORS_RETIRED_RUNS_CAP = 4096;
    private final java.util.Set<String> indicatorsRetiredRuns =
            java.util.Collections.synchronizedSet(
                    new java.util.LinkedHashSet<>());
    /**
     * r1 finding 3: serializes the live (cache-update → broadcast-enqueue) pair
     * against the connect/caught-up replay's (cache-read → session-send) pair, so a
     * replay-captured older revision can never be enqueued AFTER a newer live frame
     * (coalescing keeps the LAST enqueue).
     */
    private final Object indicatorsEmitLock = new Object();
    /**
     * Same role as {@link #indicatorsEmitLock} and for the same two races. The cache and live
     * consumers read the SAME single partition, and the contract permits an equal-event-time
     * correction at a later offset — so without one lock spanning (cache update -> broadcast) and
     * (cache read -> replay enqueue), a superseded offset can win the broadcast, or a replay can
     * enqueue an older frame over a newer one already queued under the same coalescing key.
     */
    private final Object volPremiumIvrvEmitLock = new Object();
    private final Map<String, java.util.concurrent.atomic.AtomicLong> volPremiumIvrvBroadcastOffset
            = new ConcurrentHashMap<>();
    /**
     * Times a vol-premium record arrived behind the stored offset while carrying a strictly newer
     * event time — the signature of a recreated topic, which no incarnation of this one can
     * otherwise produce. Counted because it is a recovery, and a recovery nobody can see is
     * indistinguishable from the outage it fixed.
     */
    static final java.util.concurrent.atomic.AtomicLong VOL_PREMIUM_TOPIC_RESETS =
            new java.util.concurrent.atomic.AtomicLong();
    /** r2 finding 1: exactly-one in-order live delivery per offset across BOTH
     * ingesting consumers — whichever reaches an offset first broadcasts it. */
    private final Map<String, java.util.concurrent.atomic.AtomicLong>
            indicatorsBroadcastOffset = new ConcurrentHashMap<>();

    /** Exactly-one, in-order live delivery per offset — the single-partition contract. */
    boolean shouldBroadcastVolPremiumIvrv(String cacheKey, long offset) {
        var gate = volPremiumIvrvBroadcastOffset.computeIfAbsent(cacheKey,
                k -> new java.util.concurrent.atomic.AtomicLong(-1L));
        while (true) {
            long current = gate.get();
            if (offset <= current) {
                return false;
            }
            if (gate.compareAndSet(current, offset)) {
                return true;
            }
        }
    }

    boolean shouldBroadcastIndicators(String cacheKey, long offset) {
        var gate = indicatorsBroadcastOffset.computeIfAbsent(cacheKey,
                k -> new java.util.concurrent.atomic.AtomicLong(-1L));
        while (true) {
            long current = gate.get();
            if (offset <= current) {
                return false;
            }
            if (gate.compareAndSet(current, offset)) {
                return true;
            }
        }
    }
    // Tape-zones CURRENT board (TAPE-ZONES-REQUIREMENT §6.2, UI design §3): the standalone
    // service's whole-session snapshot on a compacted 1-partition topic keyed ES|sessionDate.
    // The producer value is the SSOT and rides byte-untouched — the gateway performs NO
    // computation or reshaping; every field the card needs (cells, merged zones, aggregates,
    // quality banner, terminalFlushed) is already on it. Same standalone/global/JSON
    // pass-through delivery class as spot-vol-regime/indicators; NOT in the ui-batch.
    // Keyed by source|sessionDate; the parallel map holds (offset, kafkaRecordTimeMs) so the
    // emitted wire form can carry the board's own age without mutating the payload.
    private final Map<String, String> tapeZonesBoards = new ConcurrentHashMap<>();
    private final Map<String, long[]> tapeZonesPositions = new ConcurrentHashMap<>();
    /** Exactly-one, in-order live delivery per offset — single-partition contract (§6.2). */
    private final java.util.concurrent.atomic.AtomicLong tapeZonesBroadcastOffset =
            new java.util.concurrent.atomic.AtomicLong(-1L);
    /**
     * Serializes the live (cache-update → broadcast-enqueue) pair against the replay's
     * (cache-read → session-send) pair, so a replay-captured older board can never be enqueued
     * AFTER a newer live frame (coalescing keeps the LAST enqueue). Same seam as
     * {@code indicatorsEmitLock}.
     */
    private final Object tapeZonesEmitLock = new Object();

    boolean shouldBroadcastTapeZones(long offset) {
        while (true) {
            long current = tapeZonesBroadcastOffset.get();
            if (offset <= current) {
                return false;
            }
            if (tapeZonesBroadcastOffset.compareAndSet(current, offset)) {
                return true;
            }
        }
    }
    // SPX close-direction (design CLOSE-DIRECTION-GATE1 §8/CD-R30): ONE topic, two cache classes.
    // VERDICTS (key V|sessionDate) are once-a-session frozen decisions on the LONG
    // closeDirectionTtlMs window; INTERIMS (key I|sessionDate) are 1/min monitoring reads whose
    // REPLAY is additionally bounded by closeDirectionInterimFreshMs (stale interim = absent).
    // Precedence: once a session's VERDICT is cached, later interims for that session are ignored
    // (verdictId dedupe upstream keeps re-published verdicts idempotent). Standalone/global/JSON
    // pass-through delivery class, same as the open-direction siblings; never in the ui-batch.
    private final Map<String, String> closeDirectionVerdicts = new ConcurrentHashMap<>();
    private final Map<String, String> closeDirectionInterims = new ConcurrentHashMap<>();
    // Current SPX 0DTE binary direction, keyed by source|symbol|sessionDate. This is a short-lived
    // standalone UI control signal: stale/malformed data must disappear instead of leaving a chain
    // tinted green/red after the underlying evidence is no longer current.
    private final Map<String, String> zeroDteIntelligence = new ConcurrentHashMap<>();
    private final Map<String, String> optionPriceBehaviors = new ConcurrentHashMap<>();
    // Dealer-ledger: the two source topics are cached RAW per (source|symbol|expiry), and the JOINED
    // envelope the UI consumes is cached in dealerLedgers (last-value-wins). See DealerLedgerJoiner.
    private final Map<String, String> dealerLedgerProfiles = new ConcurrentHashMap<>();
    private final Map<String, String> dealerLedgerStates = new ConcurrentHashMap<>();
    private final Map<String, String> dealerLedgers = new ConcurrentHashMap<>();
    private final Map<String, String> opbByOptions = new ConcurrentHashMap<>();
    private final Map<String, String> opbSessions = new ConcurrentHashMap<>();
    private final Map<String, String> hpsfLatestSignals = new ConcurrentHashMap<>();
    private final Map<String, String> hpsfMarketFlows = new ConcurrentHashMap<>();
    private final Map<String, String> hpsfTopCandidates = new ConcurrentHashMap<>();
    private final Map<String, String> hpsfAudits = new ConcurrentHashMap<>();
    private final Map<String, String> hpsfExitIntents = new ConcurrentHashMap<>();
    private final Map<String, StrikeScoreSnapshot> hpsfStrikeScores = new ConcurrentHashMap<>();
    private final Map<String, String> hpsfLatestEvaluationIds = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheEventTimes = new ConcurrentHashMap<>();
    private final Map<String, RecordPosition> cachePositions = new ConcurrentHashMap<>();
    private final Map<String, Long> sourceLastForwardedAt = new ConcurrentHashMap<>();
    private final Object batchLock = new Object();
    private final Map<String, String> pendingSnapshots = new LinkedHashMap<>();
    private final Map<String, String> pendingPaces = new LinkedHashMap<>();
    private final Map<String, String> pendingPaceRanks = new LinkedHashMap<>();
    private final Map<String, String> pendingDirectionalPressures = new LinkedHashMap<>();
    private final Map<String, String> pendingStrikeFlows = new LinkedHashMap<>();
    private final Map<String, String> pendingSpotBands = new LinkedHashMap<>();
    private final Map<String, String> pendingSellerActivities = new LinkedHashMap<>();
    private final Map<String, String> pendingDeltaFlows = new LinkedHashMap<>();
    private final Map<String, String> pendingStrikeIntels = new LinkedHashMap<>();
    private final Map<String, String> pendingStrikeInvasions = new LinkedHashMap<>();
    private final Map<String, String> pendingLiquidityHeatmaps = new LinkedHashMap<>();
    private final Map<String, String> pendingMissionPaces = new LinkedHashMap<>();
    private final Map<String, String> pendingMissionControls = new LinkedHashMap<>();
    private final Map<String, String> pendingSpreadSkews = new LinkedHashMap<>();
    private final Map<String, String> pendingIndexPrices = new LinkedHashMap<>();
    // Canonical SPX spot pending queue — separate from pendingIndexPrices so the legacy batch carries
    // it under its own `spxPrices` envelope field (event identity preserved end to end).
    private final Map<String, String> pendingSpxPrices = new LinkedHashMap<>();
    private final Map<String, String> pendingVolumeSandwiches = new LinkedHashMap<>();
    private final Map<String, String> pendingMissionSandwiches = new LinkedHashMap<>();
    private final Map<String, String> pendingGexByStrike = new LinkedHashMap<>();
    private final Map<String, String> pendingGexOiStatus = new LinkedHashMap<>();
    private final Map<String, String> pendingStrikeSr = new LinkedHashMap<>();
    private final Map<String, String> pendingGexMagnet = new LinkedHashMap<>();
    private final Map<String, String> pendingGammaMigration = new LinkedHashMap<>();
    private final Map<String, String> pendingEsGex = new LinkedHashMap<>();
    private final Map<String, String> pendingEsStrikeIntel = new LinkedHashMap<>();
    private final Map<String, String> pendingGexStrikeLifecycle = new LinkedHashMap<>();
    private final Map<String, String> pendingMaxPain = new LinkedHashMap<>();
    private final Map<String, String> pendingOptionPriceBehaviors = new LinkedHashMap<>();
    private final Map<String, String> pendingOpbByOptions = new LinkedHashMap<>();
    private final Map<String, String> pendingOpbSessions = new LinkedHashMap<>();
    private final Map<String, String> pendingHpsfLatestSignals = new LinkedHashMap<>();
    private final Map<String, String> pendingHpsfMarketFlows = new LinkedHashMap<>();
    private final Map<String, String> pendingHpsfTopCandidates = new LinkedHashMap<>();
    private final Map<String, String> pendingHpsfAudits = new LinkedHashMap<>();
    private final Map<String, String> pendingHpsfExitIntents = new LinkedHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean avroCaughtUp = new AtomicBoolean(false);
    private final AtomicBoolean stateCaughtUp = new AtomicBoolean(false);
    private final AtomicBoolean hpsfCaughtUp = new AtomicBoolean(false);
    private final AtomicReference<ActiveSelection> activeSelection;
    private final AtomicReference<Map<TopicPartition, Long>> offsetBarriers = new AtomicReference<>(Map.of());
    private final AtomicLong coalescedUpdates = new AtomicLong();
    private final AtomicLong batchesSent = new AtomicLong();
    private final AtomicLong consumerRestarts = new AtomicLong();
    private final AtomicLong forwardedEvents = new AtomicLong();
    /** drop-nowcast values dropped at the parse gate (review finding #4). */
    private final AtomicLong dropNowcastMalformed = new AtomicLong();
    /** U16: latest ACCEPTED es-cvd-spx-levels record (verbatim), replayed on connect; null = none. */
    private final java.util.concurrent.atomic.AtomicReference<String> cvdSpxLevelsLatest =
            new java.util.concurrent.atomic.AtomicReference<>();
    /** U16: es-cvd-spx-levels records rejected at the boundary (invalid, oversize, tombstone). */
    private final AtomicLong cvdSpxLevelsDrops = new AtomicLong();
    /** U16: records refused because their provenance REGRESSED against what is retained (CL-R7 V1). */
    private final AtomicLong cvdSpxLevelsRegressions = new AtomicLong();
    /** U16 retention baseline: the provenance of the retained record; guarded by the retain lock. */
    private CvdSpxLevelsProvenance cvdSpxLevelsProvenance;
    /** U16: the end offset hydration read to, handed to the live consumer once; -1 = none. */
    private final AtomicLong cvdSpxLevelsHandoffOffset = new AtomicLong(-1L);
    /** U16: the next offset to read on this partition, so a consumer RETRY resumes instead of
     *  replaying history (a historical tombstone or reset must never be re-applied as new). */
    private final AtomicLong cvdSpxLevelsNextOffset = new AtomicLong(-1L);
    private final AtomicLong inactiveDroppedEvents = new AtomicLong();
    private final AtomicLong droppedNonRoutableEvents = new AtomicLong();
    private final AtomicLong staleDroppedEvents = new AtomicLong();
    private final AtomicLong seekToLatestEvents = new AtomicLong();
    private final AtomicLong sourceStaleEvents = new AtomicLong();
    private final AtomicLong lastSourceStaleBroadcastMs = new AtomicLong();
    private final AtomicLong lastLagCheckMs = new AtomicLong();
    /** Topics currently absent from metadata — shared across consumer threads, so log-once per name|topic. */
    private final Set<String> absentTopics = ConcurrentHashMap.newKeySet();
    /**
     * Partitions discovered mid-run that are still replaying their cache rebuild, published at SERVICE
     * scope because two independent threads need them: the owning cache consumer (to keep them out of the
     * lag guard until they reach their barrier) and {@link #applySelection} on the selection thread (to
     * refuse to announce a source READY while its data is still incomplete).
     */
    private final Map<TopicPartition, BootstrapState> bootstrappingPartitions = new ConcurrentHashMap<>();
    /** Live refresh state per consumer, for the diagnostics snapshot. The original incident was SILENT. */
    private final Map<String, PartitionRefresh> partitionRefreshes = new ConcurrentHashMap<>();
    /** Current assignment size per consumer, so "assigned != discovered" is externally checkable. */
    private final Map<String, Integer> assignedPartitionCounts = new ConcurrentHashMap<>();

    /**
     * @param source   the market-data source this partition feeds — the axis {@link #applySelection} asks about.
     * @param barrier  the end offset at discovery; the partition is complete once its position reaches it.
     * @param sinceMs  when the rebuild started, so a partition stuck replaying is externally visible.
     * @param owner    the consumer attempt that created the entry. Entries SURVIVE the attempt's death,
     *                 failing closed for their source, and are superseded by the replacement attempt's
     *                 bootstrap — see {@link #supersedeBootstrapEntries}.
     */
    private record BootstrapState(String source, long barrier, long sinceMs, String owner) {
    }
    private final AtomicReference<String> readySelectionKey = new AtomicReference<>("");

    // ---- Rollover-diagnostics instrumentation (additive; no behavior changes) ----
    // These counters + fields exist ONLY to answer "which flag flipped wrong?" the next time the gateway
    // silently wedges across a midnight-ET rollover (see 2026-07-01 9.5h outage). They are additive:
    // nothing here alters the forward decision or the rollover logic. Removing this block would restore
    // the pre-instrumentation runtime exactly.
    private final AtomicLong liveRecordsPolled = new AtomicLong();          // total records seen by any live consumer
    // Codex round-4 P2: only records whose binding.source() matches the CURRENT activeSelection.source()
    // (plus HPSF, which is not source-gated) are eligible for forward. `consumersAdvancing` in the stall
    // gate is derived from THIS counter's per-interval delta, not liveRecordsPolled, so noisy traffic
    // from a non-selected source can never mask a real wedge in the selected source's pipeline.
    private final AtomicLong liveRecordsEligibleForActiveSelection = new AtomicLong();
    private final AtomicLong droppedByStaleness = new AtomicLong();         // records dropped by selection-barrier / stale gate
    private final AtomicLong droppedByCacheGate = new AtomicLong();         // records dropped because cacheCaughtUpFlag was false
    private final AtomicLong droppedByOtherReasons = new AtomicLong();      // caught-up + non-forwardable (source/symbol/expiry mismatch, etc.)
    private final AtomicLong strikeBandsRejected = new AtomicLong();        // spot-vol-regime strikeBand blocks refused by sanitizeStrikeBand
    private final AtomicLong tapeZonesRejected = new AtomicLong();          // tape-zones boards refused by the fail-closed identity contract
    private final AtomicBoolean strikeBandRejectionLogged = new AtomicBoolean(false); // log the first rejection only; the rest are counted
    private final AtomicLong rolloverCount = new AtomicLong();              // number of session-boundary rollovers observed
    private final AtomicLong forwardStalledAlerts = new AtomicLong();       // number of GATEWAY_FORWARD_STALLED_DURING_MARKET_HOURS emissions
    private final AtomicLong lastRolloverAtMs = new AtomicLong();           // wall-clock ms of the most recent rollover
    private final AtomicReference<String> lastRolloverFrom = new AtomicReference<>("");
    private final AtomicReference<String> lastRolloverTo = new AtomicReference<>("");
    private final AtomicLong lastForwardedSnapshot = new AtomicLong();      // forwardedEvents at the last 60s dump
    private final AtomicLong lastDumpLiveRecordsPolledSnapshot = new AtomicLong(); // liveRecordsPolled at the last 60s dump
    private final AtomicLong lastDumpLiveRecordsEligibleSnapshot = new AtomicLong(); // liveRecordsEligibleForActiveSelection at the last 60s dump
    private volatile int consecutiveZeroForwardCycles = 0;                  // read/written only by the diagnostics thread
    private volatile ScheduledExecutorService diagnosticsExecutor;
    private volatile boolean diagnosticsEnabled = true;
    // ---- end rollover-diagnostics instrumentation ----

    private ExecutorService executor;
    private ScheduledExecutorService batchExecutor;

    private record CachedEvent(String event, String json) {
    }

    private record TopicBinding(String source, String event) {
    }

    private record RecordPosition(TopicPartition partition, long offset) {
    }

    // expiry is the (symbol,expiry)-chain this HPSF view belongs to, used as the per-session routing
    // key (null for market-flow, which is whole-underlying). The legacy batch path ignores it.
    private record HpsfCacheUpdate(String event, String key, String json, String expiry) {
    }

    private record ActiveSelection(String source, String symbol, String expiry, long selectionEpoch, long selectedAtMs) {
        private static ActiveSelection fromSettings(GatewaySettings settings) {
            long nowMs = System.currentTimeMillis();
            return new ActiveSelection(
                    GatewaySettings.normalizeSource(settings.initialMarketDataSource()),
                    settings.initialSymbol(),
                    GatewaySettings.normalizeExpiry(settings.initialExpiry()),
                    0L,
                    nowMs
            );
        }

        private boolean newerThan(ActiveSelection other) {
            if (other == null) {
                return true;
            }
            if (selectionEpoch > 0L || other.selectionEpoch > 0L) {
                return selectionEpoch > other.selectionEpoch;
            }
            return selectedAtMs > other.selectedAtMs;
        }
    }

    @FunctionalInterface
    private interface ConsumerAttempt {
        void run(boolean retry) throws RuntimeException;
    }

    public FeedGatewayService(GatewaySettings settings, ObjectMapper mapper, HpsfGatewayViewMapper hpsfViewMapper,
                              @Nullable SessionRoutingEngine routingEngine) {
        this.settings = settings;
        this.mapper = mapper;
        this.hpsfViewMapper = hpsfViewMapper;
        this.routingEngine = routingEngine;
        this.activeSelection = new AtomicReference<>(ActiveSelection.fromSettings(settings));
        this.marketCalendar = settings.marketCalendar();
        this.autoRolledExpiry = this.activeSelection.get().expiry();
    }

    /**
     * True when the live data path is routed per-session instead of broadcast.
     *
     * <p>Isolation is COUPLED to auth (review finding #2 / C-2): whenever the routing engine is wired —
     * which is exactly when {@code GATEWAY_AUTH_ENABLED=true} (MtSessionAuthConfig only creates the
     * SessionRoutingEngine bean then) — per-session routing is forced ON. Otherwise an authenticated
     * socket would receive the global broadcast of EVERY user's data. The legacy
     * {@code GATEWAY_ROUTING_PER_SESSION} flag can only be used to force routing on; it can no longer be
     * used to leave an auth-enabled gateway in broadcast mode.
     */
    private boolean perSessionRouting() {
        // Intrinsic to auth (P0): the routing engine exists exactly when GATEWAY_AUTH_ENABLED=true, so an
        // authenticated gateway ALWAYS routes per-session. There is no separate routing flag to leave off.
        return routingEngine != null;
    }

    @PostConstruct
    public void start() {
        if (!settings.enabled() || !running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "options-edge-feed-gateway");
            thread.setDaemon(true);
            return thread;
        });
        // U16 (CL-R8/G19): HYDRATE the retained levels record BEFORE anything else starts — this
        // is a barrier, not a background task. Submitting it to the executor would return from
        // start() immediately, so a client connecting in the next second still got levels:null,
        // and worse, a live tombstone could clear retention only for the late hydration to
        // resurrect the withdrawn record. Running it here means the live consumer begins with the
        // baseline already in place, and every ordering after that is the live one's.
        if (settings.esCvdSpxLevelsEnabled()) {
            hydrateCvdSpxLevels();
        }
        executor.submit(this::runSelectionConsumer);
        executor.submit(this::runAvroLiveConsumer);
        executor.submit(this::runJsonStateLiveConsumer);
        executor.submit(this::runAvroCacheConsumer);
        executor.submit(this::runJsonStateCacheConsumer);
        executor.submit(this::runAlertConsumer);
        executor.submit(this::runHpsfCacheConsumer);
        executor.submit(this::runHpsfLiveConsumer);
        batchExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "options-edge-feed-gateway-batcher");
            thread.setDaemon(true);
            return thread;
        });
        batchExecutor.scheduleAtFixedRate(
                this::flushPendingBatch,
                settings.webSocketBatchMs(),
                settings.webSocketBatchMs(),
                TimeUnit.MILLISECONDS
        );
        // P0 (write deadline): force-close any socket whose send has been stuck past the deadline, freeing
        // its writer-pool thread so a few stuck/adversarial clients can never starve the healthy ones.
        batchExecutor.scheduleAtFixedRate(
                this::enforceOutboundWriteDeadlines,
                settings.wsWriteDeadlineMs(),
                Math.max(100L, settings.wsWriteDeadlineMs() / 2),
                TimeUnit.MILLISECONDS
        );
        // AUTO-expiry daily roll (no-op unless IB_EXPIRY is empty/AUTO). 60s cadence catches the overnight
        // ET trading-date change well before the open; the date never changes mid-session.
        batchExecutor.scheduleAtFixedRate(this::maybeAutoRollExpiry, 60L, 60L, TimeUnit.SECONDS);
        if (settings.ibkrPreOpenEnabled()) {
            // rev13 slice 2: the pre-open window transitions (fence capture / takeover snapshot /
            // 09:35 destruction) are consumer-LOCAL — they must fire on wall clock even when both
            // Kafka consumers are stalled or the brokers are down, or connected clients would keep
            // frozen values past 09:35 (R-SLO's ≤10 s sweeper bound). 5s cadence; no-op when the
            // plane is empty; exceptions cannot wedge the batch cadence (isolated runnable).
            batchExecutor.scheduleAtFixedRate(() -> {
                try {
                    sweepIbkrPreOpenGexWindows(System.currentTimeMillis());
                } catch (RuntimeException e) {
                    // A sweep failure must not kill the scheduled task chain — but it must not be
                    // silent either: repeated failures leave projections on screen past 09:35 with
                    // no other symptom, so they get a counter (scraped) and a log line.
                    ibkrPreOpenSweepFailures.incrementAndGet();
                    System.out.println("Feed gateway ibkr-preopen sweep FAILED "
                            + "(projections may outlive 09:35): "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }, 5L, 5L, TimeUnit.SECONDS);
        }
        // Rollover-diagnostics 60s dump (additive; separate executor so a diag exception can never wedge
        // the batch/deadline/autoroll cadence). See dumpDiagnosticState() for the semantics.
        diagnosticsExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "options-edge-feed-gateway-diagnostics");
            thread.setDaemon(true);
            return thread;
        });
        diagnosticsExecutor.scheduleAtFixedRate(this::dumpDiagnosticState, 60L, 60L, TimeUnit.SECONDS);
    }

    private void enforceOutboundWriteDeadlines() {
        long now = System.currentTimeMillis();
        long deadline = settings.wsWriteDeadlineMs();
        for (OutboundChannel channel : outbound.values()) {
            try {
                channel.enforceWriteDeadline(now, deadline);
            } catch (RuntimeException ignored) {
                // a single channel must not break the sweep
            }
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        // Let Kafka polling loops observe running=false and leave their try-with-resources blocks.
        // Interrupting first makes KafkaConsumer.close() inherit the interrupted flag and emit one
        // "Failed to close fetcher" ERROR per consumer during every normal Kubernetes rollout.
        shutdownExecutorGracefully(executor);
        shutdownExecutorGracefully(batchExecutor);
        shutdownExecutorGracefully(diagnosticsExecutor);
        // Wake and stop any in-flight replay readers so shutdown is not held up by a blocking poll.
        for (ReplayHandle handle : replayHandles.values()) {
            handle.active.set(false);
            handle.wakeConsumers();
        }
        shutdownExecutorGracefully(replayExecutor);
        for (OutboundChannel channel : outbound.values()) {
            channel.shutdown();
        }
        outbound.clear();
        shutdownExecutorGracefully(outboundWriters);
        sellerActivityStore.close();
    }

    static void shutdownExecutorGracefully(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return;
            }
            executor.shutdownNow();
            executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void addClient(WebSocketSession session) {
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        OutboundChannel channel = new OutboundChannel(session, outboundWriterExecutor(),
                settings.wsMaxQueuedMessages(), settings.wsMaxQueuedBytes(), outboundMetrics, this::onSlowDisconnect);
        outbound.put(session.getId(), channel);
        clients.add(session);
        clientsById.put(session.getId(), session);
        send(session, "status", statusJson());
        if (settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled()) {
            // R46 hello: the per-timeframe high-water marks of the bar view, so the page can bound
            // its REST backfill to exactly what this gateway holds and buffer WS bars past it.
            // U16 (CL-R8/G19): the latest ACCEPTED levels record rides INSIDE this same hello, so
            // "hello carried no levels record" (levels: null) is distinguishable from "replay still
            // pending" — the page needs that distinction to choose `no_data` over staying blank.
            send(session, "cvd-hello", cvdHelloJson());
        }
        // In per-session mode the GLOBAL cached replay is replaced by a PER-SESSION filtered replay:
        // each socket gets only the cached state matching its own AppSession selection (no cross-
        // contract leak), then live routed data (FR-11).
        if (perSessionRouting()) {
            // A socket that connects BETWEEN a source switch and its readiness can be served a cache that
            // is still filling, and the readiness replay iterates `clients` weakly — if its iterator was
            // snapshotted before this session was registered, no later attempt repairs it, because
            // readiness is one-shot per selection key. Bracket the replay with the key: if readiness
            // committed while we were replaying, take the completed band board ourselves.
            String keyBeforeReplay = readySelectionKey.get();
            replayCachedToSocket(session);
            if (!java.util.Objects.equals(keyBeforeReplay, readySelectionKey.get())) {
                replaySpotBandBatchOncePerReadiness(session);
            }
            // short-premium is a GLOBAL advisory (symbol-filtered client-side), not per-session
            // routed — replayCacheMap can't deliver it (no GatewayRecordMapper case), so replay it
            // the same standalone way legacy mode does, so an auth-mode reload restores the overlay.
            replayShortPremiumCached(session);
            // corridor-gauge standalone replay: a reload mid-session restores the strip instantly
            replayCorridorGaugeCached(session);
            replayZeroDteIntelligenceCached(session);
            // Indicators are the same GLOBAL advisory class (symbol-filtered
            // client-side) — the production auth mode owes the connect replay too
            // (r1 finding 5).
            replayIndicatorsCached(session);
            // Tape-zones board is the same GLOBAL advisory class — auth mode owes it too.
            replayTapeZonesCached(session);
            return;
        }
        if (avroCaughtUp.get()) {
            // max-pain + strike-sr are DATABENTO-only Avro, so they bootstrap purely via the Avro consumer.
            sendCachedState(session, List.of("snapshot", "pace", "pace-rank", "directional-pressure", "max-pain", "strike-sr", "gex-magnet", "gamma-migration", "gex-strike-lifecycle"));
        }
        if (stateCaughtUp.get()) {
            sendCachedState(session, List.of("vix-price", "index-price", "spx-price", "strike-flow", "spot-band", "delta-flow", "strike-intel", "strike-invasion", "liquidity-heatmap", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "option-price-behavior", "opb-by-option", "opb-session", "es-gex", "es-strike-intel"));
            replayOptionTruthCachedLegacy(session);
            // dealer-ledger is delivered STANDALONE (its own message.type), never inside the ui-batch,
            // so it replays via its own path rather than sendCachedState's batch envelope.
            replayDealerLedgerCached(session);
            // hot-strike is likewise STANDALONE and GLOBAL: replay the fresh cached
            // envelope(s) so a legacy-mode reload restores the gold mark (§4.4).
            replayHotStrikeCached(session);
            // short-premium recommendations are likewise STANDALONE; replay the day's cached recommendations
            // so a page reload mid-session (or a client that connects after Agent A acted) still shows the
            // active overlay rather than waiting for the next live broadcast.
            replayShortPremiumCached(session);
            // ES open-direction forecast + outcomes are likewise STANDALONE global advisories; replay the
            // cached 09:15 forecast and all horizon outcomes resolved so far, so a client that connects at
            // 11:00 (or reloads) still gets the once-a-day forecast instead of waiting until tomorrow.
            replayEsOpenDirectionCached(session);
            // Greek-move-authenticity CURRENT verdict is likewise a STANDALONE global advisory; replay the
            // fresh cached verdict per symbol so a page reload mid-session restores the move-authenticity
            // track rather than waiting for the next live verdict.
            replayGreekMoveAuthCached(session);
            replaySpotVolRegimeCached(session);
            replayVolPremiumIvrvCached(session);
            replayIndicatorsCached(session);
            replayTapeZonesCached(session);
            replayIbkrPreOpenCached(session);
            // Close-direction: replay the session's frozen verdict (or the current interim) so a page
            // reload in the final hour restores the card instead of waiting for the next minute tick.
            replayCloseDirectionCached(session);
            replayZeroDteIntelligenceCached(session);
        }
        // gex-by-strike is the one MULTI-SOURCE cache: IBKR/Unusual-Whales gex arrives via the JSON state
        // consumer while DATABENTO gex arrives via the Avro consumer. Its cached replay is only complete once
        // BOTH have caught up, so gate it on both flags (avoids a first-send that omits one source's gex).
        if (avroCaughtUp.get() && stateCaughtUp.get()) {
            sendCachedState(session, List.of("gex-by-strike"));
        }
        // Pre-open IBKR GEX value plane (rev13 R-ARB slice 2): standalone wrapped replay, gated on
        // BOTH consumers — the AVRO consumer ingests the shared live gex topic, but the revocation/
        // generation CONTROLS (and the pairing statuses) ride the JSON state consumer's status
        // topic (R-WIRE.5 status/control-before-serving). Gating on avro alone could expose a value
        // whose retained revocation has not been consumed yet (round-1 finding 2); the caught-up
        // re-push in markCacheCaughtUp covers this client the moment the LAST barrier clears.
        if (settings.ibkrPreOpenEnabled() && ibkrPreOpenGexServingUp()) {
            replayIbkrPreOpenGexCached(session);
        }
        // gex-oi-status rides the JSON state consumer only; replay once it has caught up so a reconnect
        // during an OI_MISSING morning restores the badge (last-value-wins per strike).
        if (stateCaughtUp.get()) {
            sendCachedState(session, List.of("gex-oi-status"));
        }
        if (hpsfCaughtUp.get()) {
            sendCachedState(session, List.of(
                    "hpsf-latest-signal",
                    "hpsf-market-flow",
                    "hpsf-top-candidates",
                    "hpsf-audit",
                    "hpsf-exit-intent"
            ));
        }
    }

    public void removeClient(WebSocketSession session) {
        String id = session.getId();
        OutboundChannel channel = outbound.remove(id);
        spotBandSwitchDelivered.remove(id);
        WebSocketSession stored = clientsById.remove(id);
        if (stored != null) {
            clients.remove(stored);
        }
        if (channel != null) {
            channel.shutdown(); // quiet teardown — normal disconnect, not a slow-client eviction
        }
        closeQuietly(stored != null ? stored : session);
        // The handler detaches this socket from the routing engine BEFORE calling removeClient, so if its
        // AppSession is now left with no sockets, end any in-flight replay (a reader must never keep
        // consuming Kafka for a session nobody is listening to).
        cancelReplayIfNoSockets(appSessionIdOf(session));
    }

    /**
     * P0 (logout completeness): tear down a user's entire server-side session. Cancels any in-flight
     * replay, removes the AppSession from the routing engine (so no further live/replay data can be routed
     * to it), and force-closes EVERY socket attached to it. Returns the number of sockets closed. Safe to
     * call when nothing is attached (returns 0).
     */
    public int logout(String appSessionId) {
        if (routingEngine == null || appSessionId == null || appSessionId.isBlank()) {
            return 0;
        }
        Set<String> sockets;
        synchronized (replayControlLock) {
            cancelActiveReplay(appSessionId); // stop any in-flight replay reader for this session
            // Remove the session+sockets WHILE STILL HOLDING the lock so a concurrent startReplay cannot
            // install a fresh reader in the gap between cancel and teardown (startReplay needs this same
            // lock). Without this, a /replay/start racing logout could strand an orphaned Kafka-consuming
            // reader for a session that is about to be torn down. (Lock order replayControlLock -> engine
            // write lock matches startReplay/sweepExpiredSessions; the cancelled reader exits without
            // taking replayControlLock, so awaiting it here cannot deadlock.)
            sockets = routingEngine.teardownAppSession(appSessionId);
        }
        for (String socketId : sockets) {
            OutboundChannel channel = outbound.remove(socketId);
            WebSocketSession stored = clientsById.remove(socketId);
            if (stored != null) {
                clients.remove(stored);
            }
            if (channel != null) {
                channel.shutdown();
            }
            closeQuietly(stored != null ? stored : (channel != null ? channel.session() : null));
        }
        return sockets.size();
    }

    /**
     * Close a set of sockets the engine has ALREADY detached from routing (used by the expiry / revocation
     * sweeps). Idempotent per socket; safe if a socket was concurrently closed.
     */
    public void closeSockets(java.util.Collection<String> socketIds) {
        if (socketIds == null) {
            return;
        }
        for (String socketId : socketIds) {
            OutboundChannel channel = outbound.remove(socketId);
            WebSocketSession stored = clientsById.remove(socketId);
            if (stored != null) {
                clients.remove(stored);
            }
            if (channel != null) {
                channel.shutdown();
            }
            closeQuietly(stored != null ? stored : (channel != null ? channel.session() : null));
        }
    }

    /**
     * P0 (FR-18, evidenced scheduler): tear down every idle- or max-session-expired AppSession atomically
     * and force-close its sockets. Returns the number of sessions expired. Driven by {@code SessionExpiryReaper}.
     */
    public int sweepExpiredSessions() {
        if (routingEngine == null) {
            return 0;
        }
        SessionRoutingEngine.SweepResult result = routingEngine.sweepExpired();
        // The sweep has already removed these AppSessions from the engine. Finalize any in-flight replay
        // for each one EXPLICITLY here rather than relying on the eventual socket-close callbacks
        // (removeClient/onSlowDisconnect) — those may be delayed or suppressed, which would otherwise let a
        // replay reader keep consuming Kafka past the session's expiry. cancelActiveReplay is keyed by the
        // appSessionId and works regardless of the session already being gone from the engine.
        for (String appSessionId : result.expiredAppSessionIds()) {
            synchronized (replayControlLock) {
                cancelActiveReplay(appSessionId);
            }
        }
        closeSockets(result.closedSocketIds());
        return result.expiredAppSessionIds().size();
    }

    /** Detach a client the channel just disconnected for being too slow (the socket is already closed). */
    private void onSlowDisconnect(OutboundChannel channel) {
        outbound.remove(channel.socketId(), channel);
        WebSocketSession stored = clientsById.remove(channel.socketId());
        if (stored != null) {
            clients.remove(stored);
        }
        // A slow client is force-closed here, ahead of the container's afterConnectionClosed. Detach it
        // from routing now (idempotent — the later afterConnectionClosed detach is then a no-op) so the
        // no-sockets check is accurate, then cancel replay if this was its AppSession's last socket.
        if (routingEngine != null) {
            routingEngine.detachSocket(channel.socketId());
        }
        cancelReplayIfNoSockets(appSessionIdOf(channel.session()));
    }

    /** The AppSession id bound to a socket at handshake (per-session mode), or null if absent/blank. */
    private static String appSessionIdOf(WebSocketSession session) {
        if (session == null) {
            return null;
        }
        Object attr = session.getAttributes().get(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID);
        return attr instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * P0 (replay resource safety): when a socket goes away and its AppSession is left with NO attached
     * sockets, cancel any in-flight per-session replay and leave replay mode. Otherwise a historical-replay
     * reader would keep consuming Kafka for a session nobody is listening to until its window expires. Must
     * be called AFTER the socket has been detached from the routing engine so the socketsForAppSession check
     * reflects the removal. Idempotent; a no-op when nothing is replaying.
     */
    private void cancelReplayIfNoSockets(String appSessionId) {
        if (routingEngine == null || appSessionId == null || appSessionId.isBlank()) {
            return;
        }
        if (!routingEngine.socketsForAppSession(appSessionId).isEmpty()) {
            return; // another socket for this AppSession is still attached — keep replaying
        }
        synchronized (replayControlLock) {
            // Re-check under the lock: a socket may have (re)attached, or a new replay started, between the
            // unlocked check above and acquiring the lock. Only cancel if still nobody is listening.
            if (!routingEngine.socketsForAppSession(appSessionId).isEmpty()) {
                return;
            }
            if (!replayHandles.containsKey(appSessionId) && !routingEngine.isReplaying(appSessionId)) {
                return; // nothing in flight to cancel
            }
            cancelActiveReplay(appSessionId);
            // The AppSession may already be gone — a logout or expiry sweep can race ahead of this socket's
            // close and remove it WITHOUT holding replayControlLock. setReplayModeIfPresent does the
            // lookup-and-clear atomically under the engine write lock, so it can never throw on an
            // absent session (unlike setReplayMode); cancelActiveReplay above already stopped the reader.
            routingEngine.setReplayModeIfPresent(appSessionId, false);
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (IOException | RuntimeException ignored) {
            // best effort
        }
    }

    /** Deployment-injected Keycloak config for the bundled sign-in pages (issuer + client id; no secrets). */
    public String authConfigJson() {
        return "{\"issuer\":\"" + escapeJson(settings.keycloakIssuer())
                + "\",\"clientId\":\"" + escapeJson(settings.keycloakClientId()) + "\"}";
    }

    private int totalOutboundQueued() {
        int sum = 0;
        for (OutboundChannel channel : outbound.values()) {
            sum += channel.queueDepth();
        }
        return sum;
    }

    /** Visible for tests: run outbound writes inline (caller-runs) so delivery is synchronous + deterministic. */
    void runOutboundWritesInline() {
        outboundWriterOverride = Runnable::run;
    }

    private java.util.concurrent.Executor outboundWriterExecutor() {
        java.util.concurrent.Executor override = outboundWriterOverride;
        return override != null ? override : outboundWriters();
    }

    private ExecutorService outboundWriters() {
        ExecutorService e = outboundWriters;
        if (e == null) {
            synchronized (this) {
                e = outboundWriters;
                if (e == null) {
                    e = Executors.newFixedThreadPool(settings.wsWriterThreads(), r -> {
                        Thread t = new Thread(r, "options-edge-ws-writer");
                        t.setDaemon(true);
                        return t;
                    });
                    outboundWriters = e;
                }
            }
        }
        return e;
    }

    // 4001 is in the application-private close-code range (4000-4999); the browser client reads it,
    // refreshes the token, and reconnects.
    private static final CloseStatus TOKEN_EXPIRED_STATUS = new CloseStatus(4001, "token expired");

    /**
     * Closes any authenticated socket whose access token has expired. Auth is enforced only at the
     * handshake, so without this sweep a long-lived socket would keep streaming live quotes after its
     * token died. Sockets opened while WS auth was disabled carry no expiry attribute and are left
     * untouched. Returns the number of sockets closed (for logging/tests).
     */
    public int closeExpiredAuthSessions(long nowMs) {
        int closed = 0;
        for (WebSocketSession session : clients) {
            Object expiry = session.getAttributes().get(WsJwtHandshakeInterceptor.AUTH_EXPIRES_AT_ATTR);
            if (!(expiry instanceof Long expiresAtMs) || nowMs < expiresAtMs) {
                continue;
            }
            clients.remove(session);
            clientsById.remove(session.getId());
            OutboundChannel channel = outbound.remove(session.getId());
            if (channel != null) {
                channel.shutdown();
            }
            try {
                if (session.isOpen()) {
                    session.close(TOKEN_EXPIRED_STATUS);
                }
            } catch (IOException | IllegalStateException ignored) {
                // Removing the session from the fanout set is sufficient.
            }
            // Mirror removeClient/onSlowDisconnect: detach this socket from routing so socketsForAppSession
            // is accurate, then cancel replay if it was its AppSession's last socket. Without this, an
            // expired-token close would orphan an in-flight replay reader — and, because the socket stays
            // "attached" in the engine, a later cancelReplayIfNoSockets would wrongly see a non-empty socket
            // set and decline. This is the same delayed/suppressed-callback hazard sweepExpiredSessions was
            // hardened against; the token-expiry reaper needs the same treatment.
            if (routingEngine != null) {
                routingEngine.detachSocket(session.getId());
                cancelReplayIfNoSockets(appSessionIdOf(session));
            }
            closed++;
        }
        return closed;
    }

    /**
     * Chains ({@code symbol|yyyy-MM-dd}, the liquidity chainKey format) with an active WS
     * subscriber — the liquidity-history store's eviction-preference hook (spec §2: chains with no
     * active WS subscriber evict first).
     *
     * <p>Per-session routing (multi-tenant) mode: the DISTINCT chains across ALL live AppSession
     * selections in the routing engine — each user may watch a different chain and every one of
     * them must be preferred over unwatched chains (Codex Gate-2 finding 3 / AC15). AppSessions in
     * the reconnect grace window (sockets momentarily detached) are deliberately still counted:
     * evicting the chain a user is about to re-attach to would defeat the preference.
     *
     * <p>Legacy broadcast mode: exactly one active selection, so the set is that selection's chain
     * whenever any client is connected.
     */
    public java.util.Set<String> liquidityHistoryWsChains() {
        if (routingEngine != null) {
            java.util.Set<String> chains = new java.util.HashSet<>();
            for (app.feedgateway.mtsession.AppSession session : routingEngine.activeAppSessions()) {
                String chain = liquidityChainKey(session.selection().symbol(), session.selection().expiry());
                if (chain != null) {
                    chains.add(chain);
                }
            }
            return java.util.Set.copyOf(chains);
        }
        if (clients.isEmpty()) {
            return java.util.Set.of();
        }
        ActiveSelection selection = activeSelection.get();
        String chain = liquidityChainKey(selection.symbol(), selection.expiry());
        return chain == null ? java.util.Set.of() : java.util.Set.of(chain);
    }

    /** {@code symbol|yyyy-MM-dd} from a normalized yyyyMMdd expiry; null when either part is malformed. */
    private static String liquidityChainKey(String symbol, String expiry) {
        if (symbol == null || symbol.isBlank() || expiry == null || expiry.length() != 8) {
            return null;
        }
        return symbol + "|" + expiry.substring(0, 4) + "-" + expiry.substring(4, 6) + "-" + expiry.substring(6, 8);
    }

    public String healthJson() {
        purgeExpiredCache(System.currentTimeMillis());
        ActiveSelection selection = activeSelection.get();
        return "{"
                + "\"running\":" + running.get() + ","
                + "\"marketDataSource\":\"" + escapeJson(selection.source()) + "\","
                + "\"symbol\":\"" + escapeJson(selection.symbol()) + "\","
                + "\"expiry\":\"" + escapeJson(selection.expiry()) + "\","
                + "\"selectionEpoch\":" + selection.selectionEpoch() + ","
                + "\"avroCaughtUp\":" + avroCaughtUp.get() + ","
                + "\"stateCaughtUp\":" + stateCaughtUp.get() + ","
                + "\"hpsfCaughtUp\":" + hpsfCaughtUp.get() + ","
                + "\"instanceId\":\"" + escapeJson(settings.instanceId()) + "\","
                + "\"clients\":" + clients.size() + ","
                + "\"outboundQueued\":" + totalOutboundQueued() + ","
                + "\"outboundCoalesced\":" + wsCoalesced.get() + ","
                + "\"outboundSlowDisconnects\":" + wsSlowDisconnects.get() + ","
                + "\"outboundWriteErrors\":" + wsWriteErrors.get() + ","
                + "\"outboundDroppedOnClose\":" + wsDroppedOnClose.get() + ","
                + "\"snapshots\":" + snapshots.size() + ","
                + "\"paces\":" + paces.size() + ","
                + "\"paceRanks\":" + paceRanks.size() + ","
                + "\"directionalPressures\":" + directionalPressures.size() + ","
                + "\"strikeFlows\":" + strikeFlows.size() + ","
                + "\"spotBands\":" + spotBands.size() + ","
                + "\"deltaFlows\":" + deltaFlows.size() + ","
                + "\"strikeIntels\":" + strikeIntels.size() + ","
                + "\"optionTruths\":" + optionTruths.size() + ","
                + "\"liquidityHeatmaps\":" + liquidityHeatmaps.size() + ","
                + "\"missionPaces\":" + missionPaces.size() + ","
                + "\"missionControls\":" + missionControls.size() + ","
                + "\"spreadSkews\":" + spreadSkews.size() + ","
                + "\"indexPrices\":" + indexPrices.size() + ","
                + "\"vixPrices\":" + vixPrices.size() + ","
                + "\"spxPrices\":" + spxPrices.size() + ","
                + "\"currentStates\":" + currentStates.size() + ","
                + "\"gexByStrike\":" + gexByStrike.size() + ","
                // Pre-open IBKR plane counters appear ONLY with the feature flag ON: O7
                // (feature-off identity) pins every OFF-state observable — this /health payload
                // included — equivalent to a build without the feature (round-2 finding 5).
                + (settings.ibkrPreOpenEnabled()
                        ? "\"ibkrPreOpenStatus\":" + ibkrPreOpenStatus.size() + ","
                          + "\"ibkrPreOpenGexCandidates\":" + ibkrPreOpenGexCandidates.size() + ","
                          + "\"ibkrPreOpenFrozenProjections\":" + ibkrPreOpenFrozenProjections.size() + ","
                          + "\"ibkrPreOpenPendingProjections\":" + ibkrPreOpenPendingProjections.size() + ","
                          + "\"ibkrPreOpenGexDroppedSessioned\":" + ibkrPreOpenGexDroppedSessioned.get() + ","
                          + "\"ibkrPreOpenGexRejected\":" + ibkrPreOpenGexRejected.get() + ","
                        : "")
                + "\"strikeSr\":" + strikeSr.size() + ","
                + "\"gexMagnet\":" + gexMagnet.size() + ","
                // ES-on-SPX aligned cache. On environments where the feature is OFF (e.g. the ES
                // env: GATEWAY_ES_GEX_ENABLED unset -> no consumer), a bare 0 here reads like a
                // data fault. Report "disabled" + an explicit flag instead of a misleading count.
                + "\"esGexEnabled\":" + settings.esGexEnabled() + ","
                + "\"esGex\":" + (settings.esGexEnabled() ? String.valueOf(esGex.size()) : "\"disabled\"") + ","
                + "\"esStrikeIntelEnabled\":" + settings.esStrikeIntelEnabled() + ","
                + "\"esStrikeIntel\":" + (settings.esStrikeIntelEnabled() ? String.valueOf(esStrikeIntel.size()) : "\"disabled\"") + ","
                + "\"gexStrikeLifecycle\":" + gexStrikeLifecycle.size() + ","
                + "\"maxPain\":" + maxPain.size() + ","
                + "\"optionPriceBehaviors\":" + optionPriceBehaviors.size() + ","
                + "\"opbByOptions\":" + opbByOptions.size() + ","
                + "\"opbSessions\":" + opbSessions.size() + ","
                + "\"hpsfLatestSignals\":" + hpsfLatestSignals.size() + ","
                + "\"hpsfMarketFlows\":" + hpsfMarketFlows.size() + ","
                + "\"hpsfTopCandidates\":" + hpsfTopCandidates.size() + ","
                + "\"hpsfAudits\":" + hpsfAudits.size() + ","
                + "\"hpsfExitIntents\":" + hpsfExitIntents.size() + ","
                + "\"pendingEvents\":" + pendingEventCount() + ","
                + "\"webSocketBatchMs\":" + settings.webSocketBatchMs() + ","
                + "\"coalescedUpdates\":" + coalescedUpdates.get() + ","
                + "\"batchesSent\":" + batchesSent.get() + ","
                + "\"forwardedEvents\":" + forwardedEvents.get() + ","
                + "\"dropNowcastMalformed\":" + dropNowcastMalformed.get() + ","
                + "\"inactiveDroppedEvents\":" + inactiveDroppedEvents.get() + ","
                + "\"droppedNonRoutableEvents\":" + droppedNonRoutableEvents.get() + ","
                + "\"staleDroppedEvents\":" + staleDroppedEvents.get() + ","
                + "\"seekToLatestEvents\":" + seekToLatestEvents.get() + ","
                + "\"sourceStaleEvents\":" + sourceStaleEvents.get() + ","
                + "\"lastSelectedForwardAgeSeconds\":" + lastSelectedForwardAgeSeconds(selection) + ","
                + "\"offsetBarriers\":" + offsetBarriers.get().size() + ","
                + "\"maxLagRecords\":" + settings.maxLagRecords() + ","
                + "\"maxStaleMs\":" + settings.maxStaleMs() + ","
                + "\"consumerRestarts\":" + consumerRestarts.get() + ","
                + "\"cacheTtlMs\":" + settings.cacheTtlMs()
                + "}";
    }

    public String metrics() {
        purgeExpiredCache(System.currentTimeMillis());
        long uptimeSeconds = Math.max(0, Duration.between(startedAt, Instant.now()).toSeconds());
        ActiveSelection selection = activeSelection.get();
        return ""
                + "# HELP options_edge_feed_gateway_running Whether the feed gateway is running.\n"
                + "# TYPE options_edge_feed_gateway_running gauge\n"
                + "options_edge_feed_gateway_running " + boolMetric(running.get()) + "\n"
                + "# HELP options_edge_feed_gateway_avro_caught_up Whether Avro cache consumers have caught up.\n"
                + "# TYPE options_edge_feed_gateway_avro_caught_up gauge\n"
                + "options_edge_feed_gateway_avro_caught_up " + boolMetric(avroCaughtUp.get()) + "\n"
                + "# HELP options_edge_feed_gateway_state_caught_up Whether JSON state cache consumers have caught up.\n"
                + "# TYPE options_edge_feed_gateway_state_caught_up gauge\n"
                + "options_edge_feed_gateway_state_caught_up " + boolMetric(stateCaughtUp.get()) + "\n"
                + "# HELP options_edge_feed_gateway_hpsf_caught_up Whether HPSF view cache consumers have caught up.\n"
                + "# TYPE options_edge_feed_gateway_hpsf_caught_up gauge\n"
                + "options_edge_feed_gateway_hpsf_caught_up " + boolMetric(hpsfCaughtUp.get()) + "\n"
                + "# HELP options_edge_feed_gateway_clients Connected WebSocket client count.\n"
                + "# TYPE options_edge_feed_gateway_clients gauge\n"
                + "options_edge_feed_gateway_clients " + clients.size() + "\n"
                + "# HELP options_edge_gateway_ws_queued Outbound messages currently buffered across all sockets.\n"
                + "# TYPE options_edge_gateway_ws_queued gauge\n"
                + "options_edge_gateway_ws_queued " + totalOutboundQueued() + "\n"
                + "# HELP options_edge_gateway_ws_coalesced_total Replaceable snapshots collapsed by coalescing.\n"
                + "# TYPE options_edge_gateway_ws_coalesced_total counter\n"
                + "options_edge_gateway_ws_coalesced_total " + wsCoalesced.get() + "\n"
                + "# HELP options_edge_gateway_ws_slow_disconnects_total Clients disconnected for exceeding outbound limits.\n"
                + "# TYPE options_edge_gateway_ws_slow_disconnects_total counter\n"
                + "options_edge_gateway_ws_slow_disconnects_total " + wsSlowDisconnects.get() + "\n"
                + "# HELP options_edge_gateway_ws_write_errors_total Outbound write failures/timeouts.\n"
                + "# TYPE options_edge_gateway_ws_write_errors_total counter\n"
                + "options_edge_gateway_ws_write_errors_total " + wsWriteErrors.get() + "\n"
                + "# HELP options_edge_gateway_ws_dropped_on_close_total Queued messages discarded when a slow client was dropped.\n"
                + "# TYPE options_edge_gateway_ws_dropped_on_close_total counter\n"
                + "options_edge_gateway_ws_dropped_on_close_total " + wsDroppedOnClose.get() + "\n"
                + "# HELP gateway_cvd_spx_levels_enabled Whether the U16 CVD SPX levels stream is enabled (the paging-alert gate).\n"
                + "# TYPE gateway_cvd_spx_levels_enabled gauge\n"
                + "gateway_cvd_spx_levels_enabled " + boolMetric(settings.esCvdSpxLevelsEnabled()) + "\n"
                + "# HELP gateway_cvd_spx_levels_drops_total es-cvd-spx-levels records dropped at the gateway boundary (invalid, oversize, tombstone).\n"
                + "# TYPE gateway_cvd_spx_levels_drops_total counter\n"
                + "gateway_cvd_spx_levels_drops_total " + cvdSpxLevelsDrops.get() + "\n"
                + "# HELP gateway_cvd_spx_levels_position_regressions_total es-cvd-spx-levels records refused because their fold provenance regressed.\n"
                + "# TYPE gateway_cvd_spx_levels_position_regressions_total counter\n"
                + "gateway_cvd_spx_levels_position_regressions_total " + cvdSpxLevelsRegressions.get() + "\n"
                + "# HELP gateway_vol_premium_topic_resets_total vol-premium-ivrv records admitted as a "
                + "recreated topic: behind the cached offset AND strictly newer, which no incarnation "
                + "of that topic can otherwise produce. Each one is a recovery from a reset that "
                + "would otherwise have frozen the card for the rest of the session; a rising count "
                + "with no operator action behind it means the detector is firing on something "
                + "else.\n"
                + "# TYPE gateway_vol_premium_topic_resets_total counter\n"
                + "gateway_vol_premium_topic_resets_total " + VOL_PREMIUM_TOPIC_RESETS.get() + "\n"
                + "# HELP options_edge_feed_gateway_snapshots Cached option snapshot count.\n"
                + "# TYPE options_edge_feed_gateway_snapshots gauge\n"
                + "options_edge_feed_gateway_snapshots " + snapshots.size() + "\n"
                + "# HELP options_edge_feed_gateway_paces Cached pace count.\n"
                + "# TYPE options_edge_feed_gateway_paces gauge\n"
                + "options_edge_feed_gateway_paces " + paces.size() + "\n"
                + "# HELP options_edge_feed_gateway_pace_ranks Cached pace-rank board count.\n"
                + "# TYPE options_edge_feed_gateway_pace_ranks gauge\n"
                + "options_edge_feed_gateway_pace_ranks " + paceRanks.size() + "\n"
                + "# HELP options_edge_feed_gateway_directional_pressures Cached directional-pressure count.\n"
                + "# TYPE options_edge_feed_gateway_directional_pressures gauge\n"
                + "options_edge_feed_gateway_directional_pressures " + directionalPressures.size() + "\n"
                + "# HELP options_edge_feed_gateway_strike_flows Cached strike-flow count.\n"
                + "# TYPE options_edge_feed_gateway_strike_flows gauge\n"
                + "options_edge_feed_gateway_strike_flows " + strikeFlows.size() + "\n"
                + "# HELP options_edge_feed_gateway_spot_bands Cached per-strike spot-band matrices.\n"
                + "# TYPE options_edge_feed_gateway_spot_bands gauge\n"
                + "options_edge_feed_gateway_spot_bands " + spotBands.size() + "\n"
                + "# HELP options_edge_feed_gateway_delta_flows Cached delta-flow count.\n"
                + "# TYPE options_edge_feed_gateway_delta_flows gauge\n"
                + "options_edge_feed_gateway_delta_flows " + deltaFlows.size() + "\n"
                + "# HELP options_edge_feed_gateway_strike_intels Cached strike-intel count.\n"
                + "# TYPE options_edge_feed_gateway_strike_intels gauge\n"
                + "options_edge_feed_gateway_strike_intels " + strikeIntels.size() + "\n"
                + "# HELP options_edge_feed_gateway_option_truths Cached per-strike Option Truth readings.\n"
                + "# TYPE options_edge_feed_gateway_option_truths gauge\n"
                + "options_edge_feed_gateway_option_truths " + optionTruths.size() + "\n"
                + "# HELP options_edge_feed_gateway_liquidity_heatmaps Cached liquidity-heatmap frame count.\n"
                + "# TYPE options_edge_feed_gateway_liquidity_heatmaps gauge\n"
                + "options_edge_feed_gateway_liquidity_heatmaps " + liquidityHeatmaps.size() + "\n"
                + "# HELP options_edge_feed_gateway_mission_paces Cached mission-pace count.\n"
                + "# TYPE options_edge_feed_gateway_mission_paces gauge\n"
                + "options_edge_feed_gateway_mission_paces " + missionPaces.size() + "\n"
                + "# HELP options_edge_feed_gateway_mission_controls Cached mission-control count.\n"
                + "# TYPE options_edge_feed_gateway_mission_controls gauge\n"
                + "options_edge_feed_gateway_mission_controls " + missionControls.size() + "\n"
                + "# HELP options_edge_feed_gateway_spread_skews Cached spread-skew snapshot count.\n"
                + "# TYPE options_edge_feed_gateway_spread_skews gauge\n"
                + "options_edge_feed_gateway_spread_skews " + spreadSkews.size() + "\n"
                + "# HELP options_edge_feed_gateway_index_prices Cached index price count.\n"
                + "# TYPE options_edge_feed_gateway_index_prices gauge\n"
                + "options_edge_feed_gateway_index_prices " + indexPrices.size() + "\n"
                + "# HELP options_edge_feed_gateway_vix_prices Cached shared VIX (last-known) entry count.\n"
                + "# TYPE options_edge_feed_gateway_vix_prices gauge\n"
                + "options_edge_feed_gateway_vix_prices " + vixPrices.size() + "\n"
                + "# HELP options_edge_feed_gateway_spx_prices Cached canonical SPX spot entry count.\n"
                + "# TYPE options_edge_feed_gateway_spx_prices gauge\n"
                + "options_edge_feed_gateway_spx_prices " + spxPrices.size() + "\n"
                + "# HELP options_edge_feed_gateway_current_states Cached current-state count.\n"
                + "# TYPE options_edge_feed_gateway_current_states gauge\n"
                + "options_edge_feed_gateway_current_states " + currentStates.size() + "\n"
                + "# HELP options_edge_feed_gateway_gex_by_strike Cached Unusual Whales GEX strike count.\n"
                + "# TYPE options_edge_feed_gateway_gex_by_strike gauge\n"
                + "options_edge_feed_gateway_gex_by_strike " + gexByStrike.size() + "\n"
                + "# HELP options_edge_feed_gateway_strike_sr Cached unified support/resistance level count.\n"
                + "# TYPE options_edge_feed_gateway_strike_sr gauge\n"
                + "options_edge_feed_gateway_strike_sr " + strikeSr.size() + "\n"
                + "# HELP options_edge_feed_gateway_gex_magnet Cached per-(symbol,expiry) gex-magnet count.\n"
                + "# TYPE options_edge_feed_gateway_gex_magnet gauge\n"
                + "options_edge_feed_gateway_gex_magnet " + gexMagnet.size() + "\n"
                + "# HELP options_edge_feed_gateway_corridor_gauges Cached corridor-gauge chain count.\n"
                + "# TYPE options_edge_feed_gateway_corridor_gauges gauge\n"
                + "options_edge_feed_gateway_corridor_gauges " + corridorGauges.size() + "\n"
                + (settings.esGexEnabled()
                        ? "# HELP options_edge_feed_gateway_es_gex Cached per-(symbol,expiry) ES-on-SPX aligned book count.\n"
                          + "# TYPE options_edge_feed_gateway_es_gex gauge\n"
                          + "options_edge_feed_gateway_es_gex " + esGex.size() + "\n"
                        : "")
                + (settings.esStrikeIntelEnabled()
                        ? "# HELP options_edge_feed_gateway_es_strike_intel Cached per-ES-strike projected strike-intel count.\n"
                          + "# TYPE options_edge_feed_gateway_es_strike_intel gauge\n"
                          + "options_edge_feed_gateway_es_strike_intel " + esStrikeIntel.size() + "\n"
                        : "")
                + "# HELP options_edge_feed_gateway_gex_strike_lifecycle Cached per-strike gamma-lifecycle count.\n"
                + "# TYPE options_edge_feed_gateway_gex_strike_lifecycle gauge\n"
                + "options_edge_feed_gateway_gex_strike_lifecycle " + gexStrikeLifecycle.size() + "\n"
                // Flag-gated like the statusJson counters (O7 feature-off identity, round-2
                // finding 5): with the feature OFF the scrape is byte-equivalent to a build
                // without it — the es-gex/es-strike-intel precedent above.
                + (settings.ibkrPreOpenEnabled()
                        ? "# HELP options_edge_feed_gateway_ibkr_preopen_gex_candidates Pre-open IBKR GEX live candidates (rev13 slice 2).\n"
                          + "# TYPE options_edge_feed_gateway_ibkr_preopen_gex_candidates gauge\n"
                          + "options_edge_feed_gateway_ibkr_preopen_gex_candidates " + ibkrPreOpenGexCandidates.size() + "\n"
                          + "# HELP options_edge_feed_gateway_ibkr_preopen_gex_frozen_projections Frozen 09:25 projections awaiting takeover/eviction.\n"
                          + "# TYPE options_edge_feed_gateway_ibkr_preopen_gex_frozen_projections gauge\n"
                          + "options_edge_feed_gateway_ibkr_preopen_gex_frozen_projections " + ibkrPreOpenFrozenProjections.size() + "\n"
                          + "# HELP options_edge_feed_gateway_ibkr_preopen_gex_pending_projections Pre-fence-committed values awaiting their pairing status (reconstruction).\n"
                          + "# TYPE options_edge_feed_gateway_ibkr_preopen_gex_pending_projections gauge\n"
                          + "options_edge_feed_gateway_ibkr_preopen_gex_pending_projections " + ibkrPreOpenPendingProjections.size() + "\n"
                          + "# HELP options_edge_feed_gateway_ibkr_preopen_gex_dropped_sessioned Fail-closed drops of sessioned records on the shared gex topic.\n"
                          + "# TYPE options_edge_feed_gateway_ibkr_preopen_gex_dropped_sessioned counter\n"
                          + "options_edge_feed_gateway_ibkr_preopen_gex_dropped_sessioned " + ibkrPreOpenGexDroppedSessioned.get() + "\n"
                          + "# HELP options_edge_feed_gateway_ibkr_preopen_gex_rejected Valid-tuple records rejected by arbitration (stale/superseded/revoked/post-fence).\n"
                          + "# TYPE options_edge_feed_gateway_ibkr_preopen_gex_rejected counter\n"
                          + "options_edge_feed_gateway_ibkr_preopen_gex_rejected " + ibkrPreOpenGexRejected.get() + "\n"
                          + "# HELP options_edge_feed_gateway_ibkr_preopen_sweep_failures Window sweeper failures — nonzero means projections may outlive 09:35.\n"
                          + "# TYPE options_edge_feed_gateway_ibkr_preopen_sweep_failures counter\n"
                          + "options_edge_feed_gateway_ibkr_preopen_sweep_failures " + ibkrPreOpenSweepFailures.get() + "\n"
                        : "")
                + "# HELP options_edge_feed_gateway_max_pain Cached per-(symbol,expiry) max-pain count.\n"
                + "# TYPE options_edge_feed_gateway_max_pain gauge\n"
                + "options_edge_feed_gateway_max_pain " + maxPain.size() + "\n"
                + "# HELP options_edge_feed_gateway_option_price_behaviors Cached option price behavior dashboard count.\n"
                + "# TYPE options_edge_feed_gateway_option_price_behaviors gauge\n"
                + "options_edge_feed_gateway_option_price_behaviors " + optionPriceBehaviors.size() + "\n"
                + "# HELP options_edge_feed_gateway_opb_by_options Cached OPB by-option count.\n"
                + "# TYPE options_edge_feed_gateway_opb_by_options gauge\n"
                + "options_edge_feed_gateway_opb_by_options " + opbByOptions.size() + "\n"
                + "# HELP options_edge_feed_gateway_opb_sessions Cached OPB session count.\n"
                + "# TYPE options_edge_feed_gateway_opb_sessions gauge\n"
                + "options_edge_feed_gateway_opb_sessions " + opbSessions.size() + "\n"
                + "# HELP options_edge_feed_gateway_hpsf_latest_signals Cached HPSF latest-signal view count.\n"
                + "# TYPE options_edge_feed_gateway_hpsf_latest_signals gauge\n"
                + "options_edge_feed_gateway_hpsf_latest_signals " + hpsfLatestSignals.size() + "\n"
                + "# HELP options_edge_feed_gateway_hpsf_market_flows Cached HPSF market-flow view count.\n"
                + "# TYPE options_edge_feed_gateway_hpsf_market_flows gauge\n"
                + "options_edge_feed_gateway_hpsf_market_flows " + hpsfMarketFlows.size() + "\n"
                + "# HELP options_edge_feed_gateway_hpsf_top_candidates Cached HPSF top-candidates view count.\n"
                + "# TYPE options_edge_feed_gateway_hpsf_top_candidates gauge\n"
                + "options_edge_feed_gateway_hpsf_top_candidates " + hpsfTopCandidates.size() + "\n"
                + "# HELP options_edge_feed_gateway_hpsf_audits Cached HPSF audit view count.\n"
                + "# TYPE options_edge_feed_gateway_hpsf_audits gauge\n"
                + "options_edge_feed_gateway_hpsf_audits " + hpsfAudits.size() + "\n"
                + "# HELP options_edge_feed_gateway_hpsf_exit_intents Cached HPSF exit-intent view count.\n"
                + "# TYPE options_edge_feed_gateway_hpsf_exit_intents gauge\n"
                + "options_edge_feed_gateway_hpsf_exit_intents " + hpsfExitIntents.size() + "\n"
                + "# HELP options_edge_feed_gateway_pending_events Pending WebSocket events waiting for the next batch.\n"
                + "# TYPE options_edge_feed_gateway_pending_events gauge\n"
                + "options_edge_feed_gateway_pending_events " + pendingEventCount() + "\n"
                + "# HELP options_edge_feed_gateway_coalesced_updates_total Total coalesced gateway updates.\n"
                + "# TYPE options_edge_feed_gateway_coalesced_updates_total counter\n"
                + "options_edge_feed_gateway_coalesced_updates_total " + coalescedUpdates.get() + "\n"
                + "# HELP options_edge_feed_gateway_batches_sent_total Total WebSocket batches sent.\n"
                + "# TYPE options_edge_feed_gateway_batches_sent_total counter\n"
                + "options_edge_feed_gateway_batches_sent_total " + batchesSent.get() + "\n"
                + "# HELP options_edge_marketdata_selected_source Selected market-data source.\n"
                + "# TYPE options_edge_marketdata_selected_source gauge\n"
                + "options_edge_marketdata_selected_source{source=\"IBKR\"} " + boolMetric("IBKR".equals(selection.source())) + "\n"
                + "options_edge_marketdata_selected_source{source=\"DATABENTO\"} " + boolMetric("DATABENTO".equals(selection.source())) + "\n"
                + "# HELP options_edge_marketdata_selection_epoch Active market-data selection epoch.\n"
                + "# TYPE options_edge_marketdata_selection_epoch gauge\n"
                + "options_edge_marketdata_selection_epoch " + selection.selectionEpoch() + "\n"
                + "# HELP options_edge_gateway_forwarded_total Selected-source records forwarded to browsers.\n"
                + "# TYPE options_edge_gateway_forwarded_total counter\n"
                + "options_edge_gateway_forwarded_total " + forwardedEvents.get() + "\n"
                + "# HELP options_edge_gateway_drop_nowcast_malformed_total Malformed drop-nowcast records dropped before broadcast.\n"
                + "# TYPE options_edge_gateway_drop_nowcast_malformed_total counter\n"
                + "options_edge_gateway_drop_nowcast_malformed_total " + dropNowcastMalformed.get() + "\n"
                + "# HELP options_edge_gateway_inactive_dropped_total Inactive-source records consumed but not forwarded.\n"
                + "# TYPE options_edge_gateway_inactive_dropped_total counter\n"
                + "options_edge_gateway_inactive_dropped_total " + inactiveDroppedEvents.get() + "\n"
                + "# HELP options_edge_gateway_dropped_non_routable_total Malformed/unroutable market-data events dropped (per-session mode, not broadcast).\n"
                + "# TYPE options_edge_gateway_dropped_non_routable_total counter\n"
                + "options_edge_gateway_dropped_non_routable_total " + droppedNonRoutableEvents.get() + "\n"
                + "# HELP options_edge_gateway_stale_dropped_total Selected-source records dropped behind the active switch barrier.\n"
                + "# TYPE options_edge_gateway_stale_dropped_total counter\n"
                + "options_edge_gateway_stale_dropped_total " + staleDroppedEvents.get() + "\n"
                + "# HELP options_edge_gateway_seek_to_latest_total Selected-source backlog seek-to-latest operations.\n"
                + "# TYPE options_edge_gateway_seek_to_latest_total counter\n"
                + "options_edge_gateway_seek_to_latest_total " + seekToLatestEvents.get() + "\n"
                + "# HELP options_edge_gateway_source_stale_total Times the selected source was reported stale.\n"
                + "# TYPE options_edge_gateway_source_stale_total counter\n"
                + "options_edge_gateway_source_stale_total " + sourceStaleEvents.get() + "\n"
                + "# HELP options_edge_gateway_last_forward_age_seconds Age of the last forwarded selected-source record.\n"
                + "# TYPE options_edge_gateway_last_forward_age_seconds gauge\n"
                + "options_edge_gateway_last_forward_age_seconds " + lastSelectedForwardAgeSeconds(selection) + "\n"
                + "# HELP options_edge_gateway_max_lag_records Configured selected-source max lag before seeking latest.\n"
                + "# TYPE options_edge_gateway_max_lag_records gauge\n"
                + "options_edge_gateway_max_lag_records " + settings.maxLagRecords() + "\n"
                + "# HELP options_edge_gateway_max_stale_ms Configured selected-source max stale age in milliseconds.\n"
                + "# TYPE options_edge_gateway_max_stale_ms gauge\n"
                + "options_edge_gateway_max_stale_ms " + settings.maxStaleMs() + "\n"
                + "# HELP options_edge_gateway_offset_barrier Selected-source next-offset switch barrier by topic partition.\n"
                + "# TYPE options_edge_gateway_offset_barrier gauge\n"
                + offsetBarrierMetrics()
                + "# HELP options_edge_feed_gateway_consumer_restarts_total Total Kafka consumer restart attempts.\n"
                + "# TYPE options_edge_feed_gateway_consumer_restarts_total counter\n"
                + "options_edge_feed_gateway_consumer_restarts_total " + consumerRestarts.get() + "\n"
                + "# HELP options_edge_feed_gateway_cache_ttl_ms Replay cache TTL in milliseconds.\n"
                + "# TYPE options_edge_feed_gateway_cache_ttl_ms gauge\n"
                + "options_edge_feed_gateway_cache_ttl_ms " + settings.cacheTtlMs() + "\n"
                + "# HELP options_edge_feed_gateway_uptime_seconds Seconds since the feed gateway service object was created.\n"
                + "# TYPE options_edge_feed_gateway_uptime_seconds gauge\n"
                + "options_edge_feed_gateway_uptime_seconds " + uptimeSeconds + "\n"
                // ---- Rollover-diagnostics counters/gauges (additive; see dumpDiagnosticState). ----
                + "# HELP options_edge_feed_gateway_forward_stalled_flag_avro_caught_up Whether the Avro live-consumer cache-caught-up gate is TRUE.\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_flag_avro_caught_up gauge\n"
                + "options_edge_feed_gateway_forward_stalled_flag_avro_caught_up " + boolMetric(avroCaughtUp.get()) + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_flag_state_caught_up Whether the JSON-state live-consumer cache-caught-up gate is TRUE.\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_flag_state_caught_up gauge\n"
                + "options_edge_feed_gateway_forward_stalled_flag_state_caught_up " + boolMetric(stateCaughtUp.get()) + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_flag_hpsf_caught_up Whether the HPSF live-consumer cache-caught-up gate is TRUE.\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_flag_hpsf_caught_up gauge\n"
                + "options_edge_feed_gateway_forward_stalled_flag_hpsf_caught_up " + boolMetric(hpsfCaughtUp.get()) + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_flag_active_selection_present Whether the activeSelection reference is non-null.\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_flag_active_selection_present gauge\n"
                + "options_edge_feed_gateway_forward_stalled_flag_active_selection_present " + boolMetric(selection != null) + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_flag_ready_selection_key_set Whether readySelectionKey has been transitioned for the CURRENT active selection (matches by key, so a stale key from the PREVIOUS selection reads 0).\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_flag_ready_selection_key_set gauge\n"
                + "options_edge_feed_gateway_forward_stalled_flag_ready_selection_key_set " + boolMetric(readySelectionKeyMatchesActive(selection)) + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_dropped_by_staleness_total Records dropped by the selection/staleness barrier (bucketed slice of inactiveDroppedEvents).\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_dropped_by_staleness_total counter\n"
                + "options_edge_feed_gateway_forward_stalled_dropped_by_staleness_total " + droppedByStaleness.get() + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_dropped_by_cache_gate_total Records dropped because cacheCaughtUpFlag was FALSE.\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_dropped_by_cache_gate_total counter\n"
                + "options_edge_feed_gateway_forward_stalled_dropped_by_cache_gate_total " + droppedByCacheGate.get() + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_dropped_by_other_reasons_total Records dropped by source/symbol/expiry mismatch or other non-staleness reasons.\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_dropped_by_other_reasons_total counter\n"
                + "options_edge_feed_gateway_forward_stalled_dropped_by_other_reasons_total " + droppedByOtherReasons.get() + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_live_records_polled_total Total records observed by the live consumers (poll advance signal).\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_live_records_polled_total counter\n"
                + "options_edge_feed_gateway_forward_stalled_live_records_polled_total " + liveRecordsPolled.get() + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_live_records_eligible_total Records whose source matches the current active selection (or HPSF); the actual `consumers advancing` signal used by the stall alert.\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_live_records_eligible_total counter\n"
                + "options_edge_feed_gateway_forward_stalled_live_records_eligible_total " + liveRecordsEligibleForActiveSelection.get() + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_rollover_count_total Number of session-boundary rollovers observed (applySelection transitions).\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_rollover_count_total counter\n"
                + "options_edge_feed_gateway_forward_stalled_rollover_count_total " + rolloverCount.get() + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_last_rollover_ms Wall-clock ms of the most recent rollover (0 if none observed).\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_last_rollover_ms gauge\n"
                + "options_edge_feed_gateway_forward_stalled_last_rollover_ms " + lastRolloverAtMs.get() + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_active_sessions Current active-session count (routing engine when per-session, else connected clients).\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_active_sessions gauge\n"
                + "options_edge_feed_gateway_forward_stalled_active_sessions " + activeSessionsCount() + "\n"
                + "# HELP options_edge_feed_gateway_forward_stalled_alerts_total Number of GATEWAY_FORWARD_STALLED_DURING_MARKET_HOURS alerts emitted.\n"
                + "# TYPE options_edge_feed_gateway_forward_stalled_alerts_total counter\n"
                + "options_edge_feed_gateway_forward_stalled_alerts_total " + forwardStalledAlerts.get() + "\n";
    }

    private static int boolMetric(boolean value) {
        return value ? 1 : 0;
    }

    private long lastSelectedForwardAgeSeconds(ActiveSelection selection) {
        Long lastForwardedAtMs = sourceLastForwardedAt.get(selectionKey(selection));
        if (lastForwardedAtMs == null || lastForwardedAtMs <= 0L) {
            return -1L;
        }
        return Math.max(0L, (System.currentTimeMillis() - lastForwardedAtMs) / 1_000L);
    }

    private String offsetBarrierMetrics() {
        StringBuilder builder = new StringBuilder();
        offsetBarriers.get().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition)))
                .forEach(entry -> builder.append("options_edge_gateway_offset_barrier{topic=\"")
                        .append(escapeJson(entry.getKey().topic()))
                        .append("\",partition=\"")
                        .append(entry.getKey().partition())
                        .append("\"} ")
                        .append(entry.getValue())
                        .append('\n'));
        return builder.toString();
    }

    private void runSelectionConsumer() {
        runRetryingConsumer("selection", retry -> runSelectionConsumerOnce(), null);
    }

    private void runSelectionConsumerOnce() {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(stringConsumerProperties("selection"))) {
            Set<String> topics = Set.of(settings.marketDataSelectionTopic());
            List<TopicPartition> partitions = partitionsFor("selection", consumer, topics);
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            PartitionRefresh partitionRefresh = new PartitionRefresh("selection", topics);
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                // Selection is a replayed-from-beginning state topic: a new partition must be read in FULL,
                // matching bootstrap, or a selection published to it would never be applied.
                Refresh refresh = partitionRefresh.apply(consumer, partitions);
                partitions = refresh.partitions();
                if (!refresh.added().isEmpty()) {
                    consumer.seekToBeginning(refresh.added());
                }
                for (ConsumerRecord<String, String> record : records) {
                    ActiveSelection selection = selectionFromJson(record.value(), record.timestamp());
                    if (selection != null) {
                        applySelection(selection);
                    }
                }
            }
        }
    }

    private void runAvroCacheConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrDisplayTopic(), new TopicBinding("IBKR", "snapshot"));
        topicEvents.put(settings.ibkrPaceTopic(), new TopicBinding("IBKR", "pace"));
        topicEvents.put(settings.ibkrPaceRankTopic(), new TopicBinding("IBKR", "pace-rank"));
        topicEvents.put(settings.ibkrDirectionalPressureTopic(), new TopicBinding("IBKR", "directional-pressure"));
        topicEvents.put(settings.databentoDisplayTopic(), new TopicBinding("DATABENTO", "snapshot"));
        topicEvents.put(settings.databentoPaceTopic(), new TopicBinding("DATABENTO", "pace"));
        topicEvents.put(settings.databentoPaceRankTopic(), new TopicBinding("DATABENTO", "pace-rank"));
        topicEvents.put(settings.databentoDirectionalPressureTopic(), new TopicBinding("DATABENTO", "directional-pressure"));
        // DATABENTO gex + max-pain are Avro on the wire (Confluent schema-registry framed, like
        // display/pace), so they MUST be consumed via the Avro deserializer — reading them as JSON yields a
        // garbled value that is cached under a fallback key but silently dropped on delivery. (IBKR/Unusual-
        // Whales gex + gex-history stay on the JSON consumer below; those topics are genuinely JSON.)
        topicEvents.put(settings.databentoGexTopic(), new TopicBinding("DATABENTO", "gex-by-strike"));
        topicEvents.put(settings.databentoMaxPainTopic(), new TopicBinding("DATABENTO", "max-pain"));
        topicEvents.put(settings.unifiedSrTopic(), new TopicBinding("DATABENTO", "strike-sr"));
        topicEvents.put(settings.databentoGexMagnetTopic(), new TopicBinding("DATABENTO", "gex-magnet"));
        topicEvents.put(settings.gammaMigrationTopic(), new TopicBinding("DATABENTO", "gamma-migration"));
        topicEvents.put(settings.gammaRotationTopic(), new TopicBinding("DATABENTO", "gamma-rotation"));
        topicEvents.put(settings.gammaFragilityTopic(), new TopicBinding("DATABENTO", "gamma-fragility"));
        topicEvents.put(settings.databentoGexStrikeLifecycleTopic(), new TopicBinding("DATABENTO", "gex-strike-lifecycle"));
        runAssignedCacheConsumer("avro", topicEvents, true, avroCaughtUp);
    }

    private void runJsonStateCacheConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrVixPriceTopic(), new TopicBinding("IBKR", "vix-price"));
        topicEvents.put(settings.databentoEsTradesTopic(), new TopicBinding("DATABENTO", "index-price"));
        // Canonical SPX spot — dedicated event, NOT index-price: its payload source names the cascade
        // tier (never "DATABENTO"), so the index-price provenance gate would drop every record.
        topicEvents.put(settings.underlyingSpxPriceTopic(), new TopicBinding("DATABENTO", "spx-price"));
        topicEvents.put(settings.ibkrVolumeSandwichTopic(), new TopicBinding("IBKR", "volume-sandwich"));
        topicEvents.put(settings.databentoVolumeSandwichTopic(), new TopicBinding("DATABENTO", "volume-sandwich"));
        topicEvents.put(settings.databentoMissionSandwichTopic(), new TopicBinding("DATABENTO", "mission-sandwich"));
        topicEvents.put(settings.ibkrUnusualWhalesGexTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        topicEvents.put(settings.ibkrUnusualWhalesGexHistoryTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        // NOTE: DATABENTO gex + max-pain are Avro-encoded and consumed by runAvroCacheConsumer (above), NOT
        // here. Only genuinely-JSON topics belong on this string consumer.
        // The DATABENTO gex HISTORY topic, however, IS JSON (emitted by databento-gex-history-service),
        // so it belongs here. It carries the same symbol|expiry|strike identity as the Avro gex rows, so
        // gexCacheKey() merges its `history` map onto the existing DATABENTO|... gex-by-strike row (mirrors
        // the UW gex/gex-history pairing above; the history record is a superset and wins via the
        // history-preservation gate in cacheRecord()).
        topicEvents.put(settings.databentoGexHistoryTopic(), new TopicBinding("DATABENTO", "gex-by-strike"));
        // Per-strike OI-arrival status (JSON, gex watchdog): OI_MISSING/OI_OK badge rows for the UI.
        topicEvents.put(settings.databentoGexOiStatusTopic(), new TopicBinding("DATABENTO", "gex-oi-status"));
        if (settings.ibkrPreOpenEnabled()) {
            // Pre-open IBKR GEX status/control stream (rev13 Phase 3) — JSON on the wire.
            topicEvents.put(settings.ibkrPreOpenStatusTopic(), new TopicBinding("IBKR", "ibkr-preopen-status"));
        }
        topicEvents.put(settings.databentoStrikeFlowTopic(), new TopicBinding("DATABENTO", "strike-flow"));
        topicEvents.put(settings.databentoSellerActivityTopic(), new TopicBinding("DATABENTO", "seller-activity"));
        topicEvents.put(settings.databentoSpotBandTopic(), new TopicBinding("DATABENTO", "spot-band"));
        if (settings.esGexEnabled()) {
            topicEvents.put(settings.esGexSpxAlignedTopic(), new TopicBinding("DATABENTO", "es-gex"));
        }
        if (settings.esStrikeIntelEnabled()) {
            topicEvents.put(settings.esStrikeIntelSpxAlignedTopic(), new TopicBinding("DATABENTO", "es-strike-intel"));
        }
        // delta-flow-by-strike is plain JSON (DeltaFlowStrikeSnapshot), per-strike keyed
        // (symbol|date|expiry|strike) — this JSON-state consumer, never the Avro one.
        topicEvents.put(settings.databentoDeltaFlowByStrikeTopic(), new TopicBinding("DATABENTO", "delta-flow"));
        // Server-rated Δ-flow acceleration verdicts: one JSON frame per second from the web tier,
        // chain-global for the active symbol — standalone broadcast, same delivery class as es-cvd.
        topicEvents.put(settings.deltaFlowAccelTopic(), new TopicBinding("DATABENTO", "delta-flow-accel"));
        // strike-intelligence-by-strike is plain JSON (StrikeIntelligenceSignal), per-strike keyed
        // (symbol|expiry|strike) — this JSON-state consumer, never the Avro one (mirrors delta-flow).
        topicEvents.put(settings.strikeIntelByStrikeTopic(), new TopicBinding("DATABENTO", "strike-intel"));
        topicEvents.put(settings.optionTruthByStrikeTopic(), new TopicBinding("DATABENTO", "option-truth"));
        // strike-intelligence-turn-alert: discrete START/STOP turn events, broadcast STANDALONE (never cached).
        topicEvents.put(settings.strikeIntelTurnAlertTopic(), new TopicBinding("DATABENTO", "turn-alert"));
        // strike-intelligence-dashboard: per-symbol JSON carrying level-based cluster walls, broadcast as
        // "strike-cluster" STANDALONE (never cached; re-emitted each dashboard interval).
        topicEvents.put(settings.strikeIntelDashboardTopic(), new TopicBinding("DATABENTO", "strike-cluster"));
        // signal-follower.hot-strike: per-symbol JSON envelope (as-of hot_strike_day
        // snapshots), broadcast as "hot-strike", cached per symbol + replayed on connect
        // (the strike-cluster idiom; §4.4 gold mark).
        topicEvents.put(settings.hotStrikeTopic(), new TopicBinding("DATABENTO", "hot-strike"));
        // strike-invasion is plain JSON (StrikeInvasionSnapshot), per-strike+direction keyed
        // (symbol|strike|direction, SPX-only — NO expiry) — this JSON-state consumer, never the Avro one
        // (mirrors strike-intel).
        topicEvents.put(settings.strikeInvasionTopic(), new TopicBinding("DATABENTO", "strike-invasion"));
        // Both dealer-ledger topics bind to ONE event; updateCache tells profile from state by topic name
        // and joins them into the single `dealer-ledger` envelope (DealerLedgerJoiner).
        topicEvents.put(settings.dealerLedgerProfileTopic(), new TopicBinding("DATABENTO", "dealer-ledger"));
        topicEvents.put(settings.dealerLedgerStateTopic(), new TopicBinding("DATABENTO", "dealer-ledger"));
        // corridor-gauge live state: JSON per symbol|expiry, standalone delivery like dealer-ledger
        topicEvents.put(settings.corridorGaugeTopic(), new TopicBinding("DATABENTO", "corridor-gauge"));
        // Liquidity-heatmap frames are JSON (StrikeLiquidityHeatmapFrame) — this string consumer,
        // never the Avro one (the Avro-read-as-JSON bug class).
        topicEvents.put(settings.strikeLiquidityTopic(), new TopicBinding("DATABENTO", "liquidity-heatmap"));
        topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding("DATABENTO", "mission-pace"));
        topicEvents.put(settings.missionControlTopic(), new TopicBinding("DATABENTO", "mission-control"));
        // spread-skew.current is plain JSON (SpreadSkewSnapshot, a SINGLE record keyed "SPX") — this
        // JSON-state consumer, never the Avro one (mirrors mission-control: single-value latest-state).
        topicEvents.put(settings.spreadSkewTopic(), new TopicBinding("DATABENTO", "spread-skew"));
        // spread-skew.events: discrete FIRE/EXIT/REVERSAL/RESTART transitions, broadcast STANDALONE (never cached).
        topicEvents.put(settings.spreadSkewEventsTopic(), new TopicBinding("DATABENTO", "spread-skew-event"));
        // drop-classifier SHADOW nowcast: discrete k=1 verdicts + refinements, broadcast STANDALONE
        // (never cached) — the spread-skew-event sibling. Advisory-only (SHADOW).
        topicEvents.put(settings.dropNowcastTopic(), new TopicBinding("DATABENTO", "drop-nowcast"));
        topicEvents.put(settings.optionPriceBehaviorDashboardTopic(), new TopicBinding("DATABENTO", "option-price-behavior"));
        topicEvents.put(settings.optionPriceBehaviorByOptionTopic(), new TopicBinding("DATABENTO", "opb-by-option"));
        topicEvents.put(settings.optionPriceBehaviorSessionTopic(), new TopicBinding("DATABENTO", "opb-session"));
        // ES 09:15 open-direction forecast + per-horizon outcomes: plain JSON (key = tradeDate),
        // standalone global advisories (never in the ui-batch), OPTIONAL topics — this JSON-state
        // consumer, never the Avro one. Their long TTL (esOpenDirectionTtlMs, default 12h) drives a
        // 12h seek-back here so a restart mid-session re-bootstraps the morning forecast.
        topicEvents.put(settings.esOpenDirectionForecastTopic(), new TopicBinding("DATABENTO", "es-open-direction-forecast"));
        topicEvents.put(settings.esOpenDirectionOutcomeTopic(), new TopicBinding("DATABENTO", "es-open-direction-outcome"));
        // ES open-direction live STATUS (60s heartbeat, JSON, key = tradeDate): standalone global
        // advisory like its siblings, OPTIONAL topic — but on the SHORT esOpenDirectionStatusTtlMs
        // window (default 5 min), which also bounds its seek-back here to the last few minutes.
        topicEvents.put(settings.esOpenDirectionStatusTopic(), new TopicBinding("DATABENTO", "es-open-direction-status"));
        // Greek-move-authenticity CURRENT verdict (JSON, key = symbol): standalone global advisory like the
        // open-direction siblings, OPTIONAL topic — on the SHORT greekMoveAuthTtlMs window (default 5 min),
        // which also bounds its seek-back here to the last few minutes.
        topicEvents.put(settings.greekMoveAuthCurrentTopic(), new TopicBinding("DATABENTO", "greek-move-auth"));
        // Spot-vol-regime CURRENT rides the same optional/standalone JSON class as greek-move-auth.
        topicEvents.put(settings.spotVolRegimeTopic(), new TopicBinding("DATABENTO", "spot-vol-regime"));
        // Vol-premium IV/RV rides the same optional/standalone JSON class as spot-vol-regime.
        topicEvents.put(settings.volPremiumIvrvTopic(), new TopicBinding("DATABENTO", "vol-premium-ivrv"));
        // r1 finding 1: dev/prod consume BOTH the locally-computed SPX topic AND
        // the es4-mirrored ES topic (§7.3); on es4 the set collapses to one.
        for (String indicatorTopic : settings.indicatorsSnapshotTopics()) {
            topicEvents.put(indicatorTopic, new TopicBinding("DATABENTO", "indicators"));
        }
        // Tape-zones CURRENT board (plain JSON, key ES|sessionDate): standalone global advisory on
        // the SHORT tapeZonesTtlMs window, OPTIONAL topic — absent on dev/prod until the MM1 mirror
        // is installed and on es4 until the service first produces (§6.2).
        topicEvents.put(settings.tapeZonesBoardTopic(), new TopicBinding("DATABENTO", "tapeZones"));
        // SPX close-direction interims + frozen verdict (JSON, key = symbol|expiry): standalone global
        // advisory, OPTIONAL topic — LONG closeDirectionTtlMs window (verdict class) bounds the seek-back;
        // interim replay freshness is separately bounded (closeDirectionInterimFreshMs).
        topicEvents.put(settings.closeDirectionSignalTopic(), new TopicBinding("DATABENTO", "close-direction"));
        if (settings.esAggressorFlowEnabled()) {
            topicEvents.put(settings.esAggressorFlowTopic(), new TopicBinding("DATABENTO", "es-aggressor-flow"));
        }
        addEsCvdTopics(topicEvents);
        // Binary SPX direction / unusual-flow state: JSON, standalone, optional during staged rollout.
        topicEvents.put(settings.vixOptionInteligenceTopic(), new TopicBinding("DATABENTO", "zero-dte-intelligence"));
        runAssignedCacheConsumer("state", topicEvents, false, stateCaughtUp);
    }

    private void runAvroLiveConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrDisplayTopic(), new TopicBinding("IBKR", "snapshot"));
        topicEvents.put(settings.ibkrPaceTopic(), new TopicBinding("IBKR", "pace"));
        topicEvents.put(settings.ibkrPaceRankTopic(), new TopicBinding("IBKR", "pace-rank"));
        topicEvents.put(settings.ibkrDirectionalPressureTopic(), new TopicBinding("IBKR", "directional-pressure"));
        topicEvents.put(settings.databentoDisplayTopic(), new TopicBinding("DATABENTO", "snapshot"));
        topicEvents.put(settings.databentoPaceTopic(), new TopicBinding("DATABENTO", "pace"));
        topicEvents.put(settings.databentoPaceRankTopic(), new TopicBinding("DATABENTO", "pace-rank"));
        topicEvents.put(settings.databentoDirectionalPressureTopic(), new TopicBinding("DATABENTO", "directional-pressure"));
        // DATABENTO gex + max-pain are Avro on the wire — live-consume them via the Avro deserializer too
        // (mirrors runAvroCacheConsumer; keep the cache + live consumer topic sets symmetric).
        topicEvents.put(settings.databentoGexTopic(), new TopicBinding("DATABENTO", "gex-by-strike"));
        topicEvents.put(settings.databentoMaxPainTopic(), new TopicBinding("DATABENTO", "max-pain"));
        topicEvents.put(settings.unifiedSrTopic(), new TopicBinding("DATABENTO", "strike-sr"));
        topicEvents.put(settings.databentoGexMagnetTopic(), new TopicBinding("DATABENTO", "gex-magnet"));
        topicEvents.put(settings.gammaMigrationTopic(), new TopicBinding("DATABENTO", "gamma-migration"));
        topicEvents.put(settings.gammaRotationTopic(), new TopicBinding("DATABENTO", "gamma-rotation"));
        topicEvents.put(settings.gammaFragilityTopic(), new TopicBinding("DATABENTO", "gamma-fragility"));
        topicEvents.put(settings.databentoGexStrikeLifecycleTopic(), new TopicBinding("DATABENTO", "gex-strike-lifecycle"));
        runLiveConsumer("avro-live", topicEvents, true, avroCaughtUp);
    }

    private void runJsonStateLiveConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrVixPriceTopic(), new TopicBinding("IBKR", "vix-price"));
        if (settings.ibkrPreOpenEnabled()) {
            // Pre-open IBKR GEX status/control stream (rev13 Phase 3) — keep the cache + live
            // JSON consumer topic sets symmetric so live statuses actually flow post-bootstrap.
            topicEvents.put(settings.ibkrPreOpenStatusTopic(), new TopicBinding("IBKR", "ibkr-preopen-status"));
        }
        topicEvents.put(settings.databentoEsTradesTopic(), new TopicBinding("DATABENTO", "index-price"));
        // Canonical SPX spot — dedicated event, NOT index-price: its payload source names the cascade
        // tier (never "DATABENTO"), so the index-price provenance gate would drop every record.
        topicEvents.put(settings.underlyingSpxPriceTopic(), new TopicBinding("DATABENTO", "spx-price"));
        topicEvents.put(settings.ibkrVolumeSandwichTopic(), new TopicBinding("IBKR", "volume-sandwich"));
        topicEvents.put(settings.databentoVolumeSandwichTopic(), new TopicBinding("DATABENTO", "volume-sandwich"));
        topicEvents.put(settings.databentoMissionSandwichTopic(), new TopicBinding("DATABENTO", "mission-sandwich"));
        topicEvents.put(settings.ibkrUnusualWhalesGexTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        topicEvents.put(settings.ibkrUnusualWhalesGexHistoryTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        // DATABENTO gex + max-pain are Avro — live-consumed by runAvroLiveConsumer, not here. The
        // DATABENTO gex HISTORY topic IS JSON, so it lives here (keep the cache + live JSON consumer
        // topic sets symmetric, exactly as the UW gex/gex-history pair and the databento-gex Avro pair).
        topicEvents.put(settings.databentoGexHistoryTopic(), new TopicBinding("DATABENTO", "gex-by-strike"));
        // Keep cache + live symmetric: per-strike OI-arrival status (JSON, gex watchdog).
        topicEvents.put(settings.databentoGexOiStatusTopic(), new TopicBinding("DATABENTO", "gex-oi-status"));
        topicEvents.put(settings.databentoStrikeFlowTopic(), new TopicBinding("DATABENTO", "strike-flow"));
        topicEvents.put(settings.databentoSellerActivityTopic(), new TopicBinding("DATABENTO", "seller-activity"));
        topicEvents.put(settings.databentoSpotBandTopic(), new TopicBinding("DATABENTO", "spot-band"));
        if (settings.esGexEnabled()) {
            topicEvents.put(settings.esGexSpxAlignedTopic(), new TopicBinding("DATABENTO", "es-gex"));
        }
        if (settings.esStrikeIntelEnabled()) {
            topicEvents.put(settings.esStrikeIntelSpxAlignedTopic(), new TopicBinding("DATABENTO", "es-strike-intel"));
        }
        // delta-flow-by-strike is plain JSON, per-strike keyed — keep the cache + live JSON consumer
        // topic sets symmetric (same rule as gex-history/strike-flow above).
        topicEvents.put(settings.databentoDeltaFlowByStrikeTopic(), new TopicBinding("DATABENTO", "delta-flow"));
        // Δ-flow acceleration — cache/live symmetry, same rule as delta-flow above.
        topicEvents.put(settings.deltaFlowAccelTopic(), new TopicBinding("DATABENTO", "delta-flow-accel"));
        // strike-intelligence-by-strike is plain JSON, per-strike keyed — keep the cache + live JSON
        // consumer topic sets symmetric (same rule as delta-flow above).
        topicEvents.put(settings.strikeIntelByStrikeTopic(), new TopicBinding("DATABENTO", "strike-intel"));
        topicEvents.put(settings.optionTruthByStrikeTopic(), new TopicBinding("DATABENTO", "option-truth"));
        // strike-intelligence-turn-alert: discrete START/STOP turn events, broadcast STANDALONE (never cached).
        topicEvents.put(settings.strikeIntelTurnAlertTopic(), new TopicBinding("DATABENTO", "turn-alert"));
        // strike-intelligence-dashboard: per-symbol JSON carrying level-based cluster walls, broadcast as
        // "strike-cluster" STANDALONE (never cached; re-emitted each dashboard interval).
        topicEvents.put(settings.strikeIntelDashboardTopic(), new TopicBinding("DATABENTO", "strike-cluster"));
        topicEvents.put(settings.hotStrikeTopic(), new TopicBinding("DATABENTO", "hot-strike"));
        // strike-invasion is plain JSON, per-strike+direction keyed (symbol|strike|direction, no expiry)
        // — keep the cache + live JSON consumer topic sets symmetric (same rule as strike-intel above).
        topicEvents.put(settings.strikeInvasionTopic(), new TopicBinding("DATABENTO", "strike-invasion"));
        // Both dealer-ledger topics bind to ONE event; updateCache tells profile from state by topic name
        // and joins them into the single `dealer-ledger` envelope (DealerLedgerJoiner).
        topicEvents.put(settings.dealerLedgerProfileTopic(), new TopicBinding("DATABENTO", "dealer-ledger"));
        topicEvents.put(settings.dealerLedgerStateTopic(), new TopicBinding("DATABENTO", "dealer-ledger"));
        // corridor-gauge live state: JSON per symbol|expiry, standalone delivery like dealer-ledger
        topicEvents.put(settings.corridorGaugeTopic(), new TopicBinding("DATABENTO", "corridor-gauge"));
        // Keep the cache + live JSON consumer topic sets symmetric (same rule as gex-history).
        topicEvents.put(settings.strikeLiquidityTopic(), new TopicBinding("DATABENTO", "liquidity-heatmap"));
        topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding("DATABENTO", "mission-pace"));
        topicEvents.put(settings.missionControlTopic(), new TopicBinding("DATABENTO", "mission-control"));
        // spread-skew.current is plain JSON (single record keyed "SPX") — keep the cache + live JSON
        // consumer topic sets symmetric (same rule as mission-control above).
        topicEvents.put(settings.spreadSkewTopic(), new TopicBinding("DATABENTO", "spread-skew"));
        // spread-skew.events: discrete FIRE/EXIT/REVERSAL/RESTART transitions, broadcast STANDALONE (never cached).
        topicEvents.put(settings.spreadSkewEventsTopic(), new TopicBinding("DATABENTO", "spread-skew-event"));
        // drop-classifier SHADOW nowcast: discrete k=1 verdicts + refinements, broadcast STANDALONE
        // (never cached) — the spread-skew-event sibling. Advisory-only (SHADOW).
        topicEvents.put(settings.dropNowcastTopic(), new TopicBinding("DATABENTO", "drop-nowcast"));
        topicEvents.put(settings.optionPriceBehaviorDashboardTopic(), new TopicBinding("DATABENTO", "option-price-behavior"));
        topicEvents.put(settings.optionPriceBehaviorByOptionTopic(), new TopicBinding("DATABENTO", "opb-by-option"));
        topicEvents.put(settings.optionPriceBehaviorSessionTopic(), new TopicBinding("DATABENTO", "opb-session"));
        // Keep the cache + live JSON consumer topic sets symmetric: ES open-direction forecast +
        // outcomes (JSON, standalone/optional — same rule as the cache consumer above).
        topicEvents.put(settings.esOpenDirectionForecastTopic(), new TopicBinding("DATABENTO", "es-open-direction-forecast"));
        topicEvents.put(settings.esOpenDirectionOutcomeTopic(), new TopicBinding("DATABENTO", "es-open-direction-outcome"));
        // Keep the cache + live JSON consumer topic sets symmetric: the ES open-direction live STATUS
        // heartbeat (JSON, standalone/optional — same rule as the forecast/outcome siblings above).
        topicEvents.put(settings.esOpenDirectionStatusTopic(), new TopicBinding("DATABENTO", "es-open-direction-status"));
        // Keep the cache + live JSON consumer topic sets symmetric: the greek-move-authenticity CURRENT
        // verdict (JSON, key = symbol, standalone/optional — same rule as the open-direction siblings above).
        topicEvents.put(settings.greekMoveAuthCurrentTopic(), new TopicBinding("DATABENTO", "greek-move-auth"));
        // Spot-vol-regime CURRENT rides the same optional/standalone JSON class as greek-move-auth.
        topicEvents.put(settings.spotVolRegimeTopic(), new TopicBinding("DATABENTO", "spot-vol-regime"));
        // Vol-premium IV/RV rides the same optional/standalone JSON class as spot-vol-regime.
        topicEvents.put(settings.volPremiumIvrvTopic(), new TopicBinding("DATABENTO", "vol-premium-ivrv"));
        // r1 finding 1: dev/prod consume BOTH the locally-computed SPX topic AND
        // the es4-mirrored ES topic (§7.3); on es4 the set collapses to one.
        for (String indicatorTopic : settings.indicatorsSnapshotTopics()) {
            topicEvents.put(indicatorTopic, new TopicBinding("DATABENTO", "indicators"));
        }
        // Tape-zones CURRENT board (plain JSON, key ES|sessionDate): standalone global advisory on
        // the SHORT tapeZonesTtlMs window, OPTIONAL topic — absent on dev/prod until the MM1 mirror
        // is installed and on es4 until the service first produces (§6.2).
        topicEvents.put(settings.tapeZonesBoardTopic(), new TopicBinding("DATABENTO", "tapeZones"));
        // Keep the cache + live JSON consumer topic sets symmetric: the SPX close-direction signal
        // (JSON, standalone/optional — same rule as the open-direction siblings above).
        topicEvents.put(settings.closeDirectionSignalTopic(), new TopicBinding("DATABENTO", "close-direction"));
        if (settings.esAggressorFlowEnabled()) {
            topicEvents.put(settings.esAggressorFlowTopic(), new TopicBinding("DATABENTO", "es-aggressor-flow"));
        }
        addEsCvdTopics(topicEvents);
        if (settings.esCvdSpxLevelsEnabled()) {
            // U16: SPX-translated CVD structure levels (compacted single-partition heartbeat,
            // >=1 record per ALIGN_HEARTBEAT while the aligner runs) — LIVE consumer only. The
            // cache consumer is deliberately NOT subscribed: updateCache has no case for it, and
            // this event keeps its own latest-record retention for the connect replay.
            topicEvents.put(settings.esCvdSpxLevelsTopic(), new TopicBinding("DATABENTO", "es-cvd-spx-levels"));
        }
        topicEvents.put(settings.vixOptionInteligenceTopic(), new TopicBinding("DATABENTO", "zero-dte-intelligence"));
        runLiveConsumer("state-live", topicEvents, false, stateCaughtUp);
    }

    private void addEsCvdTopics(Map<String, TopicBinding> topicEvents) {
        if (!settings.esCvdEnabled()) return;
        // One wiring path is shared by bootstrap and live consumers so their topic sets cannot drift.
        topicEvents.put(settings.esCvdTopic(), new TopicBinding("DATABENTO", "es-cvd"));
        topicEvents.put(settings.esCvdBarsTopic(), new TopicBinding("DATABENTO", "es-cvd-bar"));
    }

    private void runAlertConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrVolumeSandwichAlertsTopic(), new TopicBinding("IBKR", "volume-sandwich-alert"));
        topicEvents.put(settings.databentoVolumeSandwichAlertsTopic(), new TopicBinding("DATABENTO", "volume-sandwich-alert"));
        runRetryingConsumer("alerts", retry -> runAlertConsumerOnce(topicEvents), null);
    }

    private void runHpsfCacheConsumer() {
        runRetryingConsumer(
                "hpsf-cache",
                retry -> runHpsfCacheConsumerOnce(),
                () -> markCacheRecovering(hpsfCaughtUp)
        );
    }

    private void runHpsfLiveConsumer() {
        runRetryingConsumer("hpsf-live", retry -> runHpsfLiveConsumerOnce(retry), null);
    }

    private void runHpsfCacheConsumerOnce() {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(stringConsumerProperties("hpsf-cache"))) {
            List<TopicPartition> partitions = partitionsFor("hpsf-cache", consumer, hpsfTopics());
            consumer.assign(partitions);
            seekToCacheWindow(consumer, partitions);
            Map<TopicPartition, Long> bootstrapEndOffsets =
                    new LinkedHashMap<>(consumer.endOffsets(partitions));
            boolean live = caughtUp(consumer, bootstrapEndOffsets);
            PartitionRefresh partitionRefresh = new PartitionRefresh("hpsf-cache", hpsfTopics());
            if (live) {
                markCacheCaughtUp("hpsf", hpsfEvents(), hpsfCaughtUp);
            }
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                // CACHE consumer: a new partition rebuilds its full window, exactly like bootstrap, and the
                // cache is NOT complete again until it has. Readiness must drop back to RECOVERING or
                // clients trust a cache that is missing every strike on the new partitions.
                Refresh refresh = partitionRefresh.apply(consumer, partitions);
                partitions = refresh.partitions();
                if (!refresh.added().isEmpty()) {
                    seekToCacheWindow(consumer, refresh.added());
                    bootstrapEndOffsets.putAll(consumer.endOffsets(refresh.added()));
                    live = false;
                    markCacheRecovering(hpsfCaughtUp);
                }
                for (ConsumerRecord<String, String> record : records) {
                    updateHpsfCache(record);
                }
                purgeExpiredCache(System.currentTimeMillis());
                if (!live && caughtUp(consumer, bootstrapEndOffsets)) {
                    live = true;
                    markCacheCaughtUp("hpsf", hpsfEvents(), hpsfCaughtUp);
                }
            }
        }
    }

    private void runHpsfLiveConsumerOnce(boolean retry) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(stringConsumerProperties("hpsf-live"))) {
            List<TopicPartition> partitions = partitionsFor("hpsf-live", consumer, hpsfTopics());
            consumer.assign(partitions);
            if (retry) {
                seekToCacheWindow(consumer, partitions);
            } else {
                consumer.seekToEnd(partitions);
            }
            PartitionRefresh partitionRefresh = new PartitionRefresh("hpsf-live", hpsfTopics());
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                // LIVE path over CACHED topics: seek new partitions to END, matching this consumer's own
                // bootstrap. hpsf-cache rebuilds their window; replaying it here could broadcast a record
                // the cache has already superseded.
                Refresh refresh = partitionRefresh.apply(consumer, partitions);
                partitions = refresh.partitions();
                if (!refresh.added().isEmpty()) {
                    consumer.seekToEnd(refresh.added());
                }
                // Rollover-diagnostics (Codex round-2 P2b): count HPSF polls toward the "consumers advancing"
                // signal so an HPSF-only advancing pipeline still lifts polledDelta > 0. Without this, an
                // HPSF-only feed would leave consumersAdvancing=false and permanently suppress the
                // GATEWAY_FORWARD_STALLED_DURING_MARKET_HOURS alert.
                // Codex round-4 P2: HPSF is NOT source-gated (it forwards unconditionally when caught up),
                // so every HPSF-poll record is eligible for the active selection's forward path.
                if (!records.isEmpty()) {
                    liveRecordsPolled.addAndGet(records.count());
                    liveRecordsEligibleForActiveSelection.addAndGet(records.count());
                }
                for (ConsumerRecord<String, String> record : records) {
                    HpsfCacheUpdate update = updateHpsfCache(record);
                    if (update != null && hpsfCaughtUp.get()) {
                        // P0 (HPSF bypass): in tenant mode route per-session by the record's chain key
                        // so HPSF signals/audit reach only entitled sessions; the all-client batch path
                        // is unreachable. Legacy single-tenant mode keeps the coalesced batch.
                        if (perSessionRouting()) {
                            routeHpsfPerSession(update);
                        } else {
                            enqueuePending(update.event(), update.key(), update.json());
                        }
                        forwardedEvents.incrementAndGet();
                    } else if (update != null) {
                        inactiveDroppedEvents.incrementAndGet();
                        // Rollover-diagnostics fine-grained bucketing (Codex round-2 P2b): mirror the
                        // generic live-loop bucketing so HPSF drops also show up in the per-bucket telemetry.
                        // The only reason a non-null update drops here is the HPSF cache-gate being FALSE.
                        droppedByCacheGate.incrementAndGet();
                    } else {
                        // update == null: parse failure or non-matching binding — not a staleness/gate drop,
                        // so bucket as "other reasons". Note: inactiveDroppedEvents intentionally does NOT
                        // include this branch (legacy behavior); only the diagnostic bucket does.
                        droppedByOtherReasons.incrementAndGet();
                    }
                }
                purgeExpiredCache(System.currentTimeMillis());
            }
        }
    }

    private Set<String> hpsfTopics() {
        return Set.of(
                settings.hpsfLatestSignalTopic(),
                settings.hpsfMarketFlowTopic(),
                settings.hpsfStrikeScoreTopic(),
                settings.hpsfAuditTopic(),
                settings.hpsfExitSignalTopic()
        );
    }

    private List<String> hpsfEvents() {
        return List.of(
                "hpsf-latest-signal",
                "hpsf-market-flow",
                "hpsf-top-candidates",
                "hpsf-audit",
                "hpsf-exit-intent"
        );
    }

    private void runAlertConsumerOnce(Map<String, TopicBinding> topicEvents) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(stringConsumerProperties("alerts"))) {
            List<TopicPartition> partitions = partitionsFor("alerts", consumer, topicEvents.keySet());
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            PartitionRefresh partitionRefresh = new PartitionRefresh("alerts", topicEvents.keySet());
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                // Alerts are DISCRETE events with no cache consumer behind them, so nothing else would ever
                // recover the gap: seek new partitions back to exactly when we last knew they did not exist.
                Refresh refresh = partitionRefresh.apply(consumer, partitions);
                partitions = refresh.partitions();
                if (!refresh.added().isEmpty()) {
                    // Alerts are DISCRETE events with no cache consumer behind them, so nothing else would
                    // ever recover the gap. A partition on a topic that merely GREW was created empty, so
                    // BEGINNING is the exact missed set; a partition on a topic that just APPEARED can hold
                    // a full retention, so it follows bootstrap semantics (END).
                    if (!refresh.addedOnGrownTopics().isEmpty()) {
                        consumer.seekToBeginning(refresh.addedOnGrownTopics());
                    }
                    if (!refresh.addedOnNewTopics().isEmpty()) {
                        consumer.seekToEnd(refresh.addedOnNewTopics());
                    }
                }
                for (ConsumerRecord<String, String> record : records) {
                    TopicBinding binding = topicEvents.get(record.topic());
                    String json = binding == null ? null : enrichJson(record.value(), binding);
                    ActiveSelection decided = activeSelection.get();
                    if (json != null && !json.isBlank() && (perSessionRouting() || shouldForward(binding, json, record, decided))) {
                        routeOrBroadcast(binding.source(), binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        recordSelectedForward(binding, json, decided);
                    } else {
                        inactiveDroppedEvents.incrementAndGet();
                    }
                }
            }
        }
    }

    private void runAssignedCacheConsumer(String name, Map<String, TopicBinding> topicEvents, boolean avro, AtomicBoolean caughtUpFlag) {
        runRetryingConsumer(
                name,
                retry -> runAssignedCacheConsumerOnce(name, topicEvents, avro, caughtUpFlag),
                () -> markCacheRecovering(caughtUpFlag)
        );
    }

    /** Register a replay-in-progress exemption per partition: owned by this attempt, retired at its barrier. */
    private void registerBootstrapEntries(String owner, Map<TopicPartition, Long> endOffsets,
                                          Map<String, TopicBinding> topicEvents) {
        long nowMs = System.currentTimeMillis();
        endOffsets.forEach((partition, endOffset) -> {
            TopicBinding binding = topicEvents.get(partition.topic());
            bootstrappingPartitions.put(partition, new BootstrapState(
                    binding == null ? "" : binding.source(), endOffset, nowMs, owner));
        });
    }

    /**
     * A dead attempt's registry entries are deliberately NOT released when it dies. They keep FAILING
     * CLOSED: while they exist, markSelectionReady refuses their source, which is correct — the attempt
     * died mid-rebuild, so that source's cache IS incomplete. Releasing them on exit opened a window
     * (dead attempt → replacement's re-registration) in which another consumer's per-poll convergence
     * could announce the incomplete source READY, permanently, since readiness is one-shot per key.
     * Instead the REPLACEMENT attempt supersedes them here: {@code put} overwrites each partition's entry
     * with a fresh barrier, and the prune drops any leftover owned key no longer in the bootstrap set. On
     * process shutdown the entries die with the process.
     */
    private void supersedeBootstrapEntries(String owner, Map<TopicPartition, Long> freshEndOffsets,
                                           Map<String, TopicBinding> topicEvents) {
        registerBootstrapEntries(owner, freshEndOffsets, topicEvents);
        bootstrappingPartitions.entrySet().removeIf(entry ->
                owner.equals(entry.getValue().owner()) && !freshEndOffsets.containsKey(entry.getKey()));
    }

    private void runAssignedCacheConsumerOnce(String name, Map<String, TopicBinding> topicEvents, boolean avro, AtomicBoolean caughtUpFlag) {
        // Entries in the service-scoped bootstrap registry are owned by ONE consumer attempt. When this
        // attempt exits -- crash, Kafka error, shutdown -- its entries MUST go with it: the replacement
        // attempt re-bootstraps every partition through the full catch-up gate, so the incremental entries
        // are obsolete, and a dead attempt's entry could never be retired (retirement needs this consumer's
        // position()), which would withhold readiness for that source forever.
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(avro ? avroConsumerProperties(name) : stringObjectConsumerProperties(name))) {
            List<TopicPartition> partitions = partitionsFor(name, consumer, topicEvents.keySet());
            consumer.assign(partitions);
            seekToCacheWindow(consumer, partitions, topicEvents);
            // Bootstrap gets the BOOTSTRAP budget: a broker that answers in 10s is slow, not broken, and
            // must bootstrap rather than crash-loop. The 2s refresh budget applies only inside the poll
            // loop, where blocking is the cost and a failed call is a free retry.
            Map<TopicPartition, Long> bootstrapEndOffsets =
                    boundedEndOffsets(consumer, partitions, settings.metadataTimeoutMs());
            // Capture the key BEFORE deriving barriers from the selection. Captured after, a selection that
            // rolled in between would leave OLD-source barriers labelled with the NEW key -- a mismatch that
            // never fires, so the recompute below would never run. Captured before, the worst case is one
            // redundant recompute, which is idempotent.
            String barriersSelectionKey = selectionKey(activeSelection.get());
            // The INITIAL cache-window replay is exactly as much "intended backlog" as a mid-run discovery:
            // without these exemptions, a consumer RESTART with a window larger than maxLagRecords lets the
            // lag guard seekToEnd the whole rebuild away, after which the untouched catch-up barriers are
            // trivially met and the gateway reports caught-up over an incomplete cache. Entries are owned
            // by this attempt, retire per partition at barrier, SURVIVE the attempt's death (failing
            // closed), and are superseded here by the next attempt.
            supersedeBootstrapEntries(name, bootstrapEndOffsets, topicEvents);
            Map<TopicPartition, Long> catchUpEndOffsets =
                    new LinkedHashMap<>(catchUpEndOffsets(bootstrapEndOffsets, topicEvents));
            List<String> events = topicEvents.values().stream().map(TopicBinding::event).distinct().toList();
            boolean live = caughtUp(consumer, catchUpEndOffsets);
            PartitionRefresh partitionRefresh = new PartitionRefresh(name, topicEvents.keySet());
            if (live) {
                markCacheCaughtUp(name, events, caughtUpFlag);
            }
            while (running.get()) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                // CACHE consumer: a new partition rebuilds its full per-event window, exactly like bootstrap.
                Refresh refresh = partitionRefresh.apply(consumer, partitions);
                partitions = refresh.partitions();
                if (!refresh.added().isEmpty()) {
                    seekToCacheWindow(consumer, refresh.added(), topicEvents);
                    Map<TopicPartition, Long> addedEndOffsets = boundedEndOffsets(consumer, refresh.added());
                    // TWO barriers, deliberately not the same set.
                    // Readiness is per-SELECTED-SOURCE, so it uses the source-filtered barriers: a partition
                    // on the non-selected source must not hold the cache in RECOVERING forever (there would
                    // be nothing to clear it, since catchUpEndOffsets drops it).
                    // NOT catchUpEndOffsets(): its "no selected partitions -> return them all" fallback is
                    // right for the BOOTSTRAP set (a consumer with nothing on the selected source must
                    // still gate on something) and wrong for an incremental subset, where an empty result
                    // genuinely means "none of these belong to the selected source". Through the fallback,
                    // adding only non-selected partitions would push their barriers into the readiness map
                    // and hold the cache in RECOVERING on a source nobody is watching.
                    Map<TopicPartition, Long> addedSelected =
                            selectedSourceBarriers(addedEndOffsets, topicEvents);
                    catchUpEndOffsets.putAll(addedSelected);
                    if (!addedSelected.isEmpty()) {
                        // The cache is NOT complete again until these have replayed. Without this the flag
                        // stays CAUGHT UP and clients trust a cache missing every strike on the new
                        // partitions — the exact user-visible symptom of the incident being fixed. Gated on
                        // addedSelected so growth on a non-selected source cannot flap readiness for nothing.
                        live = false;
                        markCacheRecovering(caughtUpFlag);
                    }
                    // Lag-skip protection covers EVERY added partition, source-filtered or not: the
                    // selection can change while a partition is still replaying, and it would then be
                    // measured as lag. The response to lag is seekToEnd across all selected partitions,
                    // which would erase this rebuild (and the healthy partitions' positions) and re-hide
                    // the strikes. Tracked against raw end offsets so a barrier always exists to clear it.
                    registerBootstrapEntries(name, addedEndOffsets, topicEvents);
                }
                maybeSeekSelectedSourceToLatest(consumer, partitions, topicEvents,
                        bootstrappingPartitions.keySet());
                for (ConsumerRecord<String, Object> record : records) {
                    TopicBinding binding = topicEvents.get(record.topic());
                    // The pre-open status payload is a PRODUCER-authored contract (revision-equal
                    // pairing fields, control JSON): it reaches the browser byte-untouched —
                    // never enriched/reserialized. The tape-zones board is the same class: it is
                    // the SSOT for the card, so enrichJson's marketDataSource/source/sessionDate
                    // stamping must never overwrite the service's own sessionDate (UI design §3).
                    String json;
                    if (binding != null && isRawPassThroughEvent(binding.event())) {
                        json = stringJson(record.value());
                    } else {
                        String rawJson = avro ? avroJson(record.value()) : stringJson(record.value());
                        // rev13 R-ARB (slice 2): the shared live gex topic carries TWO planes (USER
                        // D14). Arbitrate BEFORE enrichJson — a sessioned pre-open record must never
                        // be stamped with the binding's DATABENTO provenance nor enter the Databento
                        // cache; a Databento record falls through byte-untouched. The cache consumer
                        // never live-broadcasts (liveBroadcast=false), exactly like slice 1.
                        if (binding != null && settings.ibkrPreOpenEnabled()
                                && "gex-by-strike".equals(binding.event())
                                && "DATABENTO".equals(binding.source())
                                // R-WIRE.1 is scoped to the SHARED LIVE topic ONLY. The same
                                // (DATABENTO, gex-by-strike) binding also carries the separate JSON
                                // history topic, and arbitrating there would drop session-claiming
                                // records out of a pre-existing Databento pipeline this feature must
                                // leave untouched.
                                && settings.databentoGexTopic().equals(record.topic())
                                && interceptSharedGexRecord(record, rawJson, false,
                                        System.currentTimeMillis())) {
                            continue;
                        }
                        json = enrichJson(rawJson, binding);
                    }
                    if (binding == null || json == null || json.isBlank()) {
                        evictStrikeSrTombstone(binding, record);
                        evictEsStrikeIntelTombstone(binding, record);
                        continue;
                    }
                    // This topic is a production market-data boundary. A retired dev helper once
                    // published synthetic prices into it at 4 Hz and overwrote the naturally sparser
                    // Databento trade in the last-value cache. Enforce provenance before either cache
                    // or routing so live, reconnect, replay, and per-session paths behave identically.
                    if (!isTrustedIndexPrice(binding, json) || !isValidSpxPrice(binding, json)) {
                        inactiveDroppedEvents.incrementAndGet();
                        droppedByOtherReasons.incrementAndGet();
                        continue;
                    }
                    if (binding != null && "vol-premium-ivrv".equals(binding.event())) {
                        // Same reason as indicators below: THIS consumer also ingests the IV/RV
                        // topic, so whichever consumer wins updateCache for an offset must be the
                        // one that broadcasts it. Without this block the cache consumer takes the
                        // offset, the live consumer's duplicate is then correctly rejected by the
                        // offset gate, and nobody broadcasts — clients starve while the cache is
                        // perfectly up to date.
                        synchronized (volPremiumIvrvEmitLock) {
                            String ivrvKey = updateCache(binding, record, json);
                            if (ivrvKey != null && caughtUpFlag.get()
                                    && shouldBroadcastVolPremiumIvrv(ivrvKey, record.offset())) {
                                broadcast(binding.event(), json);
                                forwardedEvents.incrementAndGet();
                            }
                        }
                        continue;
                    }
                    if (binding != null && "indicators".equals(binding.event())) {
                        // r2 findings 1+2: the cache consumer consumes this offset —
                        // if it wins the CAS gate it must also BROADCAST, or the
                        // live consumer's duplicate is suppressed and clients starve.
                        // The emit lock covers mutation+enqueue as one unit.
                        synchronized (indicatorsEmitLock) {
                            String indicatorKey = updateCache(binding, record, json);
                            if (indicatorKey != null && caughtUpFlag.get()
                                    && shouldBroadcastIndicators(indicatorKey, record.offset())) {
                                broadcast(binding.event(), json);
                                forwardedEvents.incrementAndGet();
                            }
                        }
                        continue;
                    }
                    if (binding != null && "tapeZones".equals(binding.event())) {
                        // Same reason as indicators above: this consumer also ingests the board
                        // topic, so whichever consumer wins updateCache for an offset must be the
                        // one that broadcasts it — otherwise the live consumer's duplicate is
                        // correctly rejected by the offset gate and clients starve.
                        tapeZonesBroadcast(binding, record, json, caughtUpFlag);
                        continue;
                    }
                    updateCache(binding, record, json);
                }
                purgeExpiredCache(System.currentTimeMillis());
                if (settings.ibkrPreOpenEnabled()) {
                    // Consumer-local window transitions (fence capture / takeover snapshot / 09:35
                    // destruction) must fire even on quiet polls with no records.
                    sweepIbkrPreOpenGexWindows(System.currentTimeMillis());
                }

                // ORDER IS LOAD-BEARING: everything below runs AFTER the polled records are applied to the
                // cache. poll() advances position() past records it merely RETURNED, so on the final
                // bootstrap batch the barrier reads as reached while those records are still in `records`,
                // unapplied. Retiring the exemption or announcing readiness before updateCache would
                // broadcast the one-shot cached replay WITHOUT the final batch -- permanently, since
                // markSelectionReady is one-shot per selection key. (The lag guard above is the opposite
                // case: it must see the PRE-retirement exemption set, so it stays before processing.)
                clearReachedBootstrapBarriers(bootstrappingPartitions, partitions, consumer::position);

                // A source switch invalidates this consumer's catch-up barriers: they were filtered to the
                // source selected when they were computed, and nothing else recomputes them. Recompute
                // against the NEW selection and let the existing catch-up machinery converge.
                String currentSelectionKey = selectionKey(activeSelection.get());
                if (!currentSelectionKey.equals(barriersSelectionKey)) {
                    barriersSelectionKey = currentSelectionKey;
                    catchUpEndOffsets.clear();
                    catchUpEndOffsets.putAll(
                            catchUpEndOffsets(boundedEndOffsets(consumer, partitions), topicEvents));
                    live = caughtUp(consumer, catchUpEndOffsets);
                    if (!live) {
                        markCacheRecovering(caughtUpFlag);
                    }
                }
                if (!live && caughtUp(consumer, catchUpEndOffsets)) {
                    live = true;
                }
                if (live) {
                    // Converge every poll, not on a one-shot flag transition: readiness withheld by the
                    // fail-closed check in markSelectionReady (bootstrap still replaying) must be retried
                    // once the last barrier retires, and markCacheCaughtUp's false->true CAS may never fire
                    // again. Both calls are idempotent and markSelectionReady re-validates under readyLock,
                    // so this is safe and cheap.
                    markCacheCaughtUp(name, events, caughtUpFlag);
                    ActiveSelection liveSelection = activeSelection.get();
                    if (liveSelection != null
                            && !selectionKey(liveSelection).equals(readySelectionKey.get())) {
                        markSelectionReady(liveSelection);
                    }
                }
            }
        }
    }

    private void seekToCacheWindow(KafkaConsumer<?, ?> consumer, List<TopicPartition> partitions) {
        seekToCacheWindow(consumer, partitions, null);
    }

    /**
     * Seek each assigned partition to the start of its event's cache window so the latest cached state is
     * bootstrapped on (re)connect. When {@code topicEvents} is supplied, the window is PER-EVENT: max-pain
     * partitions seek to {@code now - maxPainTtlMs} (12h) while fast-ticking topics keep
     * {@code now - cacheTtlMs} (15 min). Without this, a >15-min-old (but valid) max-pain record sits
     * BEHIND the generic seek position and is never read — the root cause of "max-pain missing on screen".
     *
     * <p>A per-event TTL {@code <= 0} (or a partition whose timestamp has no offset) seeks that partition
     * to END — unchanged from the original single-window behaviour. We deliberately do NOT seek max-pain to
     * the beginning: under unknown (delete-retention) topic config that could create avoidable bootstrap
     * backlog; the timestamp-bounded window reads only what the longer TTL admits.
     */
    private void seekToCacheWindow(KafkaConsumer<?, ?> consumer, List<TopicPartition> partitions,
                                   Map<String, TopicBinding> topicEvents) {
        long nowMs = System.currentTimeMillis();
        Map<TopicPartition, Long> timestamps = new HashMap<>();
        List<TopicPartition> seekToEnd = new ArrayList<>();
        for (TopicPartition partition : partitions) {
            long ttlMs = windowTtlMsFor(partition, topicEvents, nowMs);
            if (ttlMs <= 0) {
                seekToEnd.add(partition);
            } else {
                timestamps.put(partition, nowMs - ttlMs);
            }
        }
        seekToTimestampsOrEnd(consumer, timestamps, seekToEnd);
    }

    /**
     * Seek each partition in {@code timestamps} to the first offset at/after its cutoff. A partition with no
     * such offset (empty, or every record older than the cutoff) falls back to END, joining {@code seekToEnd}.
     *
     * <p>{@code seekToEnd} is mutated — callers pass a fresh mutable list.
     */
    private void seekToTimestampsOrEnd(KafkaConsumer<?, ?> consumer,
                                       Map<TopicPartition, Long> timestamps,
                                       List<TopicPartition> seekToEnd) {
        if (!timestamps.isEmpty()) {
            Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(timestamps);
            for (Map.Entry<TopicPartition, Long> entry : timestamps.entrySet()) {
                OffsetAndTimestamp offset = offsets.get(entry.getKey());
                if (offset == null) {
                    seekToEnd.add(entry.getKey());
                } else {
                    consumer.seek(entry.getKey(), offset.offset());
                }
            }
        }
        if (!seekToEnd.isEmpty()) {
            consumer.seekToEnd(seekToEnd);
        }
    }

    /**
     * Seek partitions a LIVE consumer just discovered. Two independent axes decide the answer, and both
     * matter — getting either wrong silently loses or duplicates user-visible events.
     *
     * <p><b>Axis 1 — can anything else recover this event?</b> CACHED last-value events (snapshot, pace,
     * gex, max-pain, …) are independently rebuilt by the cache consumer, so the live consumer seeks them to
     * END: replaying here could hand {@code routeOrBroadcast} a record the cache has already superseded,
     * and the per-session routing path does not gate generic events on cache acceptance, so a stale
     * snapshot would regress the UI. {@link #isLiveOnlyRebuiltEvent} events have NO such rebuild path and
     * must be recovered here or lost outright.
     *
     * <p><b>Axis 2 — why is the partition new?</b> Only for the live-only events, and only when the topic
     * was ALREADY assigned and merely grew, is the partition guaranteed EMPTY at creation — so
     * {@code seekToBeginning} recovers exactly the missed records and nothing older, with no clock
     * assumption. A partition belonging to a topic that just APPEARED (an optional producer finally
     * deploying) can hold a full retention of history; beginning-seeking it would replay hours into every
     * connected WebSocket, so it follows this consumer's bootstrap semantics instead: END.
     */
    private void seekAddedLivePartitions(KafkaConsumer<?, ?> consumer, Refresh refresh,
                                         Map<String, TopicBinding> topicEvents) {
        Set<TopicPartition> onGrownTopics = Set.copyOf(refresh.addedOnGrownTopics());
        List<TopicPartition> recoverFromBeginning = new ArrayList<>();
        List<TopicPartition> recoverDisplayWindow = new ArrayList<>();
        List<TopicPartition> toEnd = new ArrayList<>();
        for (TopicPartition partition : refresh.added()) {
            TopicBinding binding = topicEvents.get(partition.topic());
            boolean liveOnly = binding != null && isLiveOnlyRebuiltEvent(binding.event());
            if (liveOnly && onGrownTopics.contains(partition)) {
                recoverFromBeginning.add(partition);
            } else if (binding != null && "drop-nowcast".equals(binding.event())) {
                // A drop-nowcast topic that just APPEARED (the optional producer creating it
                // mid-session) may already hold verdicts; END would lose the very first one and
                // nothing rebuilds it. Beginning would replay full retention (7 d). Bounded
                // recovery instead: seek by timestamp over the client's 10-minute display
                // window (review finding #3).
                recoverDisplayWindow.add(partition);
            } else {
                toEnd.add(partition);
            }
        }
        if (!toEnd.isEmpty()) {
            consumer.seekToEnd(toEnd);
        }
        if (!recoverFromBeginning.isEmpty()) {
            consumer.seekToBeginning(recoverFromBeginning);
        }
        if (!recoverDisplayWindow.isEmpty()) {
            long fromMs = System.currentTimeMillis() - 10 * 60_000L;
            Map<TopicPartition, Long> query = new HashMap<>();
            for (TopicPartition p : recoverDisplayWindow) {
                query.put(p, fromMs);
            }
            try {
                Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndTimestamp> offsets =
                        consumer.offsetsForTimes(query);
                for (TopicPartition p : recoverDisplayWindow) {
                    org.apache.kafka.clients.consumer.OffsetAndTimestamp ot =
                            offsets == null ? null : offsets.get(p);
                    if (ot != null) {
                        consumer.seek(p, ot.offset());
                    } else {
                        consumer.seekToEnd(List.of(p));
                    }
                }
            } catch (Exception e) {
                // bounded-recovery best effort: END is the safe fallback
                consumer.seekToEnd(recoverDisplayWindow);
            }
        }
    }

    /**
     * Events whose ONLY writer is the live consumer, so the cache consumer cannot recover them and seeking
     * a newly discovered partition to END loses them. The test is "does the cache path rebuild it?", NOT
     * "is it cached?" — {@code strike-cluster} IS cached, but only by the live branch; {@code updateCache}
     * has no case for it, so the cache consumer reads those records and drops them.
     *
     * <ul>
     *   <li>{@code turn-alert}, {@code spread-skew-event}, {@code drop-nowcast} — discrete one-shot
     *       transitions, never cached ({@code drop-nowcast} additionally gets a bounded 10-minute
     *       timestamp-seek when its OPTIONAL topic first appears — see seekAddedLivePartitions).</li>
     *   <li>{@code strike-cluster} — dashboard + recent-signals trail, cached into {@code strikeClusters}
     *       by the live branch only and replayed to connecting clients from there.</li>
     * </ul>
     *
     * <p>Deliberately excluded: {@code hot-strike} (updateCache calls cacheHotStrike, so the cache consumer
     * does rebuild it) and {@code es-aggressor-flow} (a compacted snapshot re-emitted every second — END
     * loses only records something newer immediately supersedes).
     *
     * <p>Keep in sync with the standalone-broadcast branches in {@link #runLiveConsumerOnce}.
     */
    private static boolean isLiveOnlyRebuiltEvent(String event) {
        return "turn-alert".equals(event)
                || "spread-skew-event".equals(event)
                || "drop-nowcast".equals(event)
                || "strike-cluster".equals(event);
    }

    /** Per-partition Kafka cache-rebuild seek-back: the bound event's bounded seek window, else the generic one. */
    private long windowTtlMsFor(TopicPartition partition, Map<String, TopicBinding> topicEvents, long nowMs) {
        if (topicEvents != null) {
            TopicBinding binding = topicEvents.get(partition.topic());
            if (binding != null) {
                return cachePolicyFor(binding.event(), nowMs).seekBackMs();
            }
        }
        return settings.cacheTtlMs();
    }

    private void runLiveConsumer(String name, Map<String, TopicBinding> topicEvents, boolean avro, AtomicBoolean cacheCaughtUpFlag) {
        runRetryingConsumer(
                name,
                retry -> runLiveConsumerOnce(name, topicEvents, avro, cacheCaughtUpFlag, retry),
                null
        );
    }

    private void runLiveConsumerOnce(
            String name,
            Map<String, TopicBinding> topicEvents,
            boolean avro,
            AtomicBoolean cacheCaughtUpFlag,
            boolean retry
    ) {
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(avro ? avroConsumerProperties(name) : stringObjectConsumerProperties(name))) {
            List<TopicPartition> partitions = partitionsFor(name, consumer, topicEvents.keySet());
            consumer.assign(partitions);
            if (retry) {
                seekToCacheWindow(consumer, partitions, topicEvents);
                resumeCvdSpxLevels(consumer, partitions);          // U16: never replay history here
            } else {
                consumer.seekToEnd(partitions);
                seekCvdSpxLevelsToHandoff(consumer, partitions);   // U16: continuous consumption
            }
            PartitionRefresh partitionRefresh = new PartitionRefresh(name, topicEvents.keySet());
            while (running.get()) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                Refresh refresh = partitionRefresh.apply(consumer, partitions);
                partitions = refresh.partitions();
                if (!refresh.added().isEmpty()) {
                    seekAddedLivePartitions(consumer, refresh, topicEvents);
                }
                // Rollover-diagnostics: record that a live consumer is advancing. Additive; the counter
                // is only read by dumpDiagnosticState() to distinguish "consumers polling" from "forward gate stuck".
                if (!records.isEmpty()) {
                    liveRecordsPolled.addAndGet(records.count());
                }
                // Codex round-4 P2: bucket polled records by "eligible for the active selection" — a
                // record's binding.source() matching the current active selection is the only shape that
                // COULD forward if the pipeline were healthy. Symbol/expiry mismatch is a real wedge and
                // must NOT count as advancing (peer producing the wrong contract is exactly what we want
                // the stall alert to catch).
                ActiveSelection selectionForEligibility = activeSelection.get();
                if (selectionForEligibility != null && !records.isEmpty()) {
                    long eligible = 0L;
                    for (ConsumerRecord<String, Object> r : records) {
                        TopicBinding b = topicEvents.get(r.topic());
                        if (b != null && b.source().equals(selectionForEligibility.source())) {
                            eligible++;
                        }
                    }
                    if (eligible > 0L) {
                        liveRecordsEligibleForActiveSelection.addAndGet(eligible);
                    }
                }
                for (ConsumerRecord<String, Object> record : records) {
                    TopicBinding binding = topicEvents.get(record.topic());
                    // The pre-open status payload is a PRODUCER-authored contract (revision-equal
                    // pairing fields, control JSON): it reaches the browser byte-untouched —
                    // never enriched/reserialized. The tape-zones board is the same class: it is
                    // the SSOT for the card, so enrichJson's marketDataSource/source/sessionDate
                    // stamping must never overwrite the service's own sessionDate (UI design §3).
                    String json;
                    if (binding != null && isRawPassThroughEvent(binding.event())) {
                        json = stringJson(record.value());
                    } else {
                        String rawJson = avro ? avroJson(record.value()) : stringJson(record.value());
                        // rev13 R-ARB (slice 2): arbitrate the shared live gex topic BEFORE
                        // enrichJson (see the cache-consumer twin). This live consumer is the
                        // broadcasting side: liveBroadcast rides the caught-up flag, and the
                        // per-partition offset CAS gate keeps delivery exactly-once across both
                        // ingesting consumers.
                        if (binding != null && settings.ibkrPreOpenEnabled()
                                && "gex-by-strike".equals(binding.event())
                                && "DATABENTO".equals(binding.source())
                                // Shared LIVE topic only — see the cache path for why.
                                && settings.databentoGexTopic().equals(record.topic())
                                && interceptSharedGexRecord(record, rawJson, cacheCaughtUpFlag.get(),
                                        System.currentTimeMillis())) {
                            continue;
                        }
                        json = enrichJson(rawJson, binding);
                    }
                    if (binding == null || json == null || json.isBlank()) {
                        evictStrikeSrTombstone(binding, record);
                        evictEsStrikeIntelTombstone(binding, record);
                        evictCvdSpxLevelsTombstone(binding == null ? null : binding.event(), record);
                        noteCvdSpxLevelsProgress(binding, record);
                        continue;
                    }
                    if (!isTrustedIndexPrice(binding, json) || !isValidSpxPrice(binding, json)) {
                        inactiveDroppedEvents.incrementAndGet();
                        droppedByOtherReasons.incrementAndGet();
                        continue;
                    }
                    if ("turn-alert".equals(binding.event())) {
                        // Discrete StrikeTurnAlert START/STOP (own message.type), symbol-filtered client-side
                        // and keyed to today. Broadcast STANDALONE to every client — never selection-gated (a
                        // turn must reach the client regardless of active market) and never cached (the producer
                        // re-asserts START, so a late-joining client catches an active alert within its TTL).
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        continue;
                    }
                    if ("strike-cluster".equals(binding.event())) {
                        // Per-symbol StrikeIntelligenceDashboard carrying cluster walls + the recent-signals
                        // trail (own message.type), symbol-filtered client-side. Broadcast STANDALONE to every
                        // client — never selection-gated — and the LAST payload per symbol is cached so a
                        // freshly-connected page repaints the trail immediately instead of waiting for the
                        // next dashboard interval (§9b).
                        String clusterSymbol = record.key() == null ? "" : String.valueOf(record.key());
                        strikeClusters.put(clusterSymbol, json);
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        continue;
                    }
                    if ("hot-strike".equals(binding.event())) {
                        // Hot Strike of the Day envelope, symbol-keyed. §4.4 demands the payload
                        // VERBATIM — cache and broadcast the RAW record value, never the
                        // enrichJson reserialization. Broadcast STANDALONE to every client
                        // (never selection-gated); the client keeps the newest row (SPX_NATIVE
                        // preferred over ES_MAPPED at equal trading date).
                        String hotRaw = avro ? avroJson(record.value()) : stringJson(record.value());
                        if (hotRaw == null || hotRaw.isBlank()) {
                            continue;
                        }
                        String hotSymbol = record.key() == null ? "" : String.valueOf(record.key());
                        if (cacheHotStrike(hotSymbol, hotRaw,
                                record.timestamp() > 0 ? record.timestamp()
                                        : System.currentTimeMillis())) {
                            broadcast(binding.event(), hotRaw);
                            forwardedEvents.incrementAndGet();
                        }
                        continue;
                    }
                    if ("drop-nowcast".equals(binding.event())) {
                        // Drop-classifier verdict/refinement: one-shot advisory, broadcast STANDALONE
                        // to every client (never selection-gated, never cached — the client keys by
                        // drop_id and expires its own banner). SHADOW: display-only.
                        // A malformed value must never reach the envelope: enrichJson() would pass
                        // unparseable text through verbatim and poison every legacy client's frame,
                        // so parse-gate here — drop and count instead (review finding #4).
                        if (!isBroadcastableDropNowcast(mapper, json)) {
                            dropNowcastMalformed.incrementAndGet();
                            continue;
                        }
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        continue;
                    }
                    if ("spread-skew-event".equals(binding.event())) {
                        // Discrete spread-skew transition (FIRE/EXIT/REVERSAL/RESTART; own message.type),
                        // symbol-filtered client-side. Broadcast STANDALONE to every client — never
                        // selection-gated and never cached (a transition is a one-shot alert; the 5s
                        // spread-skew snapshot carries the current state for late joiners) — the
                        // turn-alert sibling.
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        continue;
                    }
                    if ("es-cvd-spx-levels".equals(binding.event())) {
                        // U16 (CL-R7): producer-authored transactional attestation, delivered
                        // VERBATIM (raw pass-through — never enriched). Boundary checks only; the
                        // aligner already validated the full schema. Anything rejected here is
                        // counted and dropped — never broadcast, never retained.
                        CvdSpxLevelsAccepted accepted = validateCvdSpxLevels(
                                record.key() == null ? null : String.valueOf(record.key()), json);
                        if (accepted == null) {
                            cvdSpxLevelsDrops.incrementAndGet();
                        } else if (retainCvdSpxLevels(accepted)) {
                            broadcast(binding.event(), json);
                            forwardedEvents.incrementAndGet();
                        }
                        noteCvdSpxLevelsProgress(binding, record);
                        continue;
                    }
                    if ("delta-flow-accel".equals(binding.event())) {
                        // Same standalone-delivery reasoning as es-cvd below: one frame per second,
                        // no option-expiry identity, never selection-gated.
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        continue;
                    }
                    if ("es-cvd".equals(binding.event())) {
                        // Same standalone-delivery reasoning as es-aggressor-flow below: one compacted
                        // ES.v.0 snapshot per second, no option-expiry identity, never selection-gated.
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        continue;
                    }
                    if ("es-cvd-bar".equals(binding.event())) {
                        // Keyed view first (the REST backfill's source of truth), then the live
                        // broadcast — clients apply the same last-per-key upsert rule, so at-least-once
                        // delivery is invisible in every materialized state (ES-CVD-DESIGN.md R31).
                        upsertCvdBar(json);
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        continue;
                    }
                    if ("es-aggressor-flow".equals(binding.event())) {
                        // One compacted ES.v.0 snapshot is published every second. Deliver it directly in
                        // both legacy and per-session routing modes: it has no option expiry identity and
                        // therefore must not fall through the generic cache-key switch (which correctly
                        // returns null for unknown per-market cache shapes).
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        continue;
                    }
                    if ("vol-premium-ivrv".equals(binding.event())) {
                        // Same shape as indicators below, and needed for the same two races: the
                        // whole cache→CAS→enqueue decision is ONE unit under the emit lock, and the
                        // offset CAS makes whichever consumer wins the broadcaster. Both consumers
                        // read the same single partition, and the contract allows an equal-
                        // event-time correction at a later offset — so an event-time gate alone
                        // would let a superseded offset broadcast over its own correction.
                        //
                        // Freshness stays fail-closed: a stale reading yields a null key from
                        // updateCache and is therefore never live-broadcast.
                        synchronized (volPremiumIvrvEmitLock) {
                            String ivrvKey = updateCache(binding, record, json);
                            if (ivrvKey != null && cacheCaughtUpFlag.get()
                                    && shouldBroadcastVolPremiumIvrv(ivrvKey, record.offset())) {
                                broadcast(binding.event(), json);
                                forwardedEvents.incrementAndGet();
                            }
                        }
                        continue;
                    }
                    if ("indicators".equals(binding.event())) {
                        // r2 findings 1+2: the whole supersede→cache→CAS→enqueue
                        // decision is ONE unit under the emit lock, and the offset
                        // CAS gate makes whichever consumer wins the broadcaster.
                        synchronized (indicatorsEmitLock) {
                            String indicatorsKey = updateCache(binding, record, json);
                            if (indicatorsKey != null && cacheCaughtUpFlag.get()
                                    && shouldBroadcastIndicators(indicatorsKey,
                                            record.offset())) {
                                broadcast(binding.event(), json);
                                forwardedEvents.incrementAndGet();
                            }
                        }
                        continue;
                    }
                    String cacheKey = updateCache(binding, record, json);
                    if (skipsLiveSpotBandForward(binding.event(), perSessionRouting())) {
                        // ONLY in per-session mode. There the live path routes one socket message per
                        // record, and the client has no "spot-band" message handler, so the browser would
                        // discard ~200 of them per republish; that mode is served by the batch replays in
                        // replayCachedToSocket / markSelectionReady instead.
                        //
                        // In LEGACY mode this record must NOT be skipped: the branch it falls through to
                        // ends in enqueuePending, and that IS the ui-batch the page reads. Skipping both
                        // left pendingSpotBands permanently empty, so the batch shipped "spotBands":[]
                        // every time while the cache held a full board — the exact symptom on dev.
                        continue;
                    }
                    if ("seller-activity".equals(binding.event())) {
                        // REST-only: every record contains one strike's full session history. Replaying or
                        // forwarding hundreds of these through the option-chain WebSocket recreates the
                        // oversized chain payload and fills its outbound queue before initial rows arrive.
                        // updateCache above remains the ingestion path for /api/seller-activity.
                        continue;
                    }
                    // Dealer-ledger forwards the JOINED envelope (not the raw profile/state record); for
                    // every other event forwardJson is just `json`, so the block below is unchanged.
                    String forwardJson = "dealer-ledger".equals(binding.event())
                            ? (cacheKey == null ? null : dealerLedgers.get(cacheKey))
                            : json;
                    if ("dealer-ledger".equals(binding.event()) && (forwardJson == null || forwardJson.isBlank())) {
                        continue; // join not ready / stale-dropped — nothing to forward for this record
                    }
                    if ("ibkr-preopen-status".equals(binding.event())) {
                        // Pre-open window state (rev13 R-STATE): a STANDALONE global stream like
                        // close-direction/spot-vol-regime — its own websocket event, never a
                        // ui-batch row, never selection-routed (payloads carry sessionId; clients
                        // gate on it), never coalesced (controls must not drop). BOTH consumers
                        // ingest this topic: the offset CAS gate delivers each offset EXACTLY
                        // once, in order, from whichever consumer reaches it first — updateCache's
                        // duplicate rejection (cacheKey == null for the second consumer) never
                        // suppresses a live delivery. The wrap carries (recordKey, offset) so the
                        // client renders last-writer-wins even when a replay interleaves.
                        if (cacheCaughtUpFlag.get() && shouldBroadcastIbkrPreOpen(record.offset())) {
                            broadcast(binding.event(),
                                    wrapIbkrPreOpenStatus(String.valueOf(record.key()),
                                            record.offset(), record.timestamp(), json));
                            forwardedEvents.incrementAndGet();
                        }
                        continue;
                    }
                    if ("tapeZones".equals(binding.event())) {
                        // Tape-zones board: a STANDALONE global advisory — its own websocket event,
                        // never a ui-batch row, never selection-routed (the board is ES-global; the
                        // SPX view is a unit toggle on the SAME record). The board rides VERBATIM
                        // inside the wrapper; only the gateway's own clock stamps are added (§3).
                        //
                        // DELIVERY IS GATED ON updateCache's RETURN, not merely on the offset CAS.
                        // updateCache is where the fail-closed identity contract, the offset
                        // ordering gate and the TTL all live, and it answers null for every record
                        // that fails one of them. Broadcasting past a null would put a malformed,
                        // duplicated, rewound or expired board on every authenticated socket while
                        // the cache correctly refused it — the card and the cache would disagree
                        // about the same offset. Nothing may reach a client that the cache rejected.
                        //
                        // Both consumers ingest this topic, so the (updateCache → CAS → enqueue)
                        // decision is ONE unit under the emit lock: whichever consumer wins
                        // updateCache for an offset is the one that broadcasts it, and the loser's
                        // null correctly suppresses only its own duplicate.
                        tapeZonesBroadcast(binding, record, json, cacheCaughtUpFlag);
                        continue;
                    }
                    if ("zero-dte-intelligence".equals(binding.event())) {
                        // Chain-level control signal: always its own websocket event, never a ui-batch row
                        // and never selection-routed. Every client receives it and filters symbol/session;
                        // updateCache's payload-time TTL ensures replay/backfill cannot look live.
                        if (cacheKey != null && cacheCaughtUpFlag.get()) {
                            broadcast(binding.event(), forwardJson);
                            forwardedEvents.incrementAndGet();
                        }
                        continue;
                    }
                    if ("close-direction".equals(binding.event())) {
                        // Close-direction: GLOBAL advisory track, its own websocket event, never a
                        // ui-batch row and never selection-routed. cacheKey == null covers malformed
                        // payloads AND interims after the session's verdict (dead by precedence) —
                        // neither is ever live-broadcast (design CD-R30).
                        if (cacheKey != null && cacheCaughtUpFlag.get()) {
                            broadcast(binding.event(), forwardJson);
                            forwardedEvents.incrementAndGet();
                        }
                        continue;
                    }
                    if ("greek-move-auth".equals(binding.event())) {
                        // Move-authenticity CURRENT verdict: a GLOBAL advisory track, its own websocket event,
                        // never a ui-batch row and never selection-routed. Every client receives it and filters
                        // by symbol client-side. Freshness fail-closed: updateCache's SHORT greekMoveAuthTtlMs
                        // window returns null (cacheKey == null) for a stale/backfilled verdict, so a stale
                        // authenticity read is never live-broadcast — the UI track just stays hidden.
                        if (cacheKey != null && cacheCaughtUpFlag.get()) {
                            broadcast(binding.event(), forwardJson);
                            forwardedEvents.incrementAndGet();
                        }
                        continue;
                    }
                    if ("spot-vol-regime".equals(binding.event())) {
                        // Spot-vol regime CURRENT snapshot: same GLOBAL advisory delivery class as
                        // greek-move-auth above — own websocket event, never a ui-batch row, never
                        // selection-routed. Freshness fail-closed via the SHORT spotVolRegimeTtlMs window
                        // (stale snapshot => cacheKey == null => never live-broadcast).
                        if (cacheKey != null && cacheCaughtUpFlag.get()) {
                            broadcast(binding.event(), forwardJson);
                            forwardedEvents.incrementAndGet();
                        }
                        continue;
                    }
                    // Selection captured ONCE for this record's forward+readiness decision (legacy mode).
                    ActiveSelection decided = null;
                    // Per-session mode: route directly via the engine using the authoritative
                    // topic-binding source (bypasses the global single-source/selection/barrier gate;
                    // supports IBKR + Databento users simultaneously). shouldForward gates legacy mode.
                    if (perSessionRouting()) {
                        // Fail-closed for max-pain (Codex Gate-2): updateCache returns null when a
                        // NON-terminal max-pain is stale (older than its 12h window) or SUPERSEDED (an
                        // out-of-order/older Kafka timestamp than the cached value). Routing such a record
                        // at the live edge would overwrite a client's view with an out-of-date level.
                        // Terminal EXPIRED returns a non-null key, so it still forwards once. Other events
                        // keep their existing unconditional per-session routing (this change is scoped to
                        // max-pain per the approved requirement).
                        //
                        // Mission-pace fail-closed (freshness): mission-pace records carry no per-session
                        // selectionEpoch (epoch 0 bypasses passesBarrier), so add an explicit maxStale
                        // freshness gate — a STALE mission-pace frame must never reach a socket. A fresh
                        // frame is routed by source|symbol|expiry, so it only reaches sockets that selected
                        // this market. Full pre-selection epoch-gating additionally requires the producer
                        // to stamp selectionEpoch (multi-tenant follow-up; per-session mode is off in dev).
                        boolean missionPaceStale = "mission-pace".equals(binding.event())
                                && !recordWithinMaxStale(record);
                        // Mission-control fail-closed (freshness): mission-control is the analog low-frequency
                        // per-MARKET signal (symbol|expiry, epoch 0 bypasses passesBarrier), so it gets the
                        // same explicit maxStale gate — a STALE mission-control frame must never reach a socket.
                        boolean missionControlStale = "mission-control".equals(binding.event())
                                && !recordWithinMaxStale(record);
                        // Liquidity-heatmap fail-closed (freshness): frames carry no per-session
                        // selectionEpoch (epoch 0 bypasses passesBarrier) and their useful life is
                        // the SHORT liquidity TTL, not the generic maxStale window — a stale column
                        // must never be live-routed as current liquidity.
                        boolean liquidityHeatmapStale = "liquidity-heatmap".equals(binding.event())
                                && eventCacheTimestamp(binding.event(), record, json)
                                < System.currentTimeMillis() - settings.liquidityHeatmapTtlMs();
                        // Strike-intel fail-closed (like es-open-direction / gamma-lifecycle): a null cacheKey
                        // means updateCache classified the record as stale (PAYLOAD event time older than the
                        // long strikeIntelTtlMs window) or SUPERSEDED (out-of-order/older than the cached value)
                        // — never live-route it. Gating live-route AND replay off the SAME updateCache verdict
                        // (12h strikeIntelTtlMs) keeps them consistent: a published 0DTE level persists for its
                        // whole session (not 15-min TTL-evicted when the strike goes quiet) and is removed only
                        // at expiry (per-strike replay is expiry-filtered; DashboardAssembler.expiryOpen purges
                        // at 16:00 ET), while a >12h backfill/superseded record is dropped from both paths.
                        boolean strikeIntelDropped = "strike-intel".equals(binding.event())
                                && cacheKey == null;
                        boolean optionTruthStale = "option-truth".equals(binding.event())
                                && eventCacheTimestamp(binding.event(), record, json)
                                < System.currentTimeMillis() - settings.optionTruthTtlMs();
                        // Strike-invasion fail-closed (freshness): same rationale as strike-intel — a
                        // per-strike signal carries no per-session selectionEpoch, so gate on the generic
                        // cache window so a backfilled/catching-up producer never live-routes a stale action.
                        boolean strikeInvasionStale = "strike-invasion".equals(binding.event())
                                && eventCacheTimestamp(binding.event(), record, json)
                                < System.currentTimeMillis() - settings.cacheTtlMs();
                        // Spread-skew fail-closed (freshness): the whole-underlying skew snapshot is the
                        // same low-frequency per-MARKET class as mission-control (epoch 0 bypasses
                        // passesBarrier), so it gets the same explicit maxStale gate — but on the PAYLOAD
                        // ts (spreadSkewTimestamp): a producer catching up on a backlog must never
                        // live-route a stale skew state onto a socket.
                        boolean spreadSkewStale = "spread-skew".equals(binding.event())
                                && !eventTimeWithinMaxStale(eventCacheTimestamp(binding.event(), record, json));
                        // ES open-direction fail-closed (freshness): like max-pain, a null cacheKey means
                        // updateCache classified the record as stale (older than esOpenDirectionTtlMs) or
                        // superseded (out-of-order) — never live-route it. A cached record needs no extra
                        // per-record staleness gate: its useful life IS the long TTL window, enforced
                        // uniformly by cachePolicyFor at ingest/purge/replay.
                        boolean esOpenDirectionDropped = isEsOpenDirectionEvent(binding.event())
                                && cacheKey == null;
                        // Gamma-lifecycle fail-closed (like max-pain / es-open-direction): a null cacheKey means
                        // updateCache classified the record as expired (PAYLOAD eventTimeMs older than the 12h
                        // window) or SUPERSEDED (out-of-order/older payload than the cached value). Live-routing
                        // it would paint a stale/old badge onto a strike — so drop it. A fresh in-order record
                        // returns a non-null key and still routes by source|symbol|expiry.
                        boolean strikeLifecycleDropped = "gex-strike-lifecycle".equals(binding.event())
                                && cacheKey == null;
                        // ES strike-intel fail-closed (like es-open-direction / gamma-lifecycle): a null cacheKey
                        // means updateCache classified this record as SUPERSEDED (an out-of-order/older producer
                        // time than the cached value lost roll-forward) or a tombstone. Live-routing it would
                        // paint a stale ES overlay onto a strike — so drop it.
                        boolean esStrikeIntelDropped = "es-strike-intel".equals(binding.event())
                                && cacheKey == null;
                        // gex-oi-status fail-closed (Codex round-5): a null cacheKey means updateCache
                        // REJECTED the record (status not exactly OI_MISSING/OI_OK). Live-routing what the
                        // cache refused would make the live and replay paths disagree — drop it.
                        boolean gexOiStatusDropped = "gex-oi-status".equals(binding.event())
                                && cacheKey == null;
                        if ((cacheKey != null || !"max-pain".equals(binding.event()))
                                && !missionPaceStale && !missionControlStale && !liquidityHeatmapStale
                                && !strikeIntelDropped && !optionTruthStale && !strikeInvasionStale && !spreadSkewStale
                                && !esOpenDirectionDropped && !strikeLifecycleDropped && !esStrikeIntelDropped
                                && !gexOiStatusDropped) {
                            routeOrBroadcast(binding.source(), binding.event(), forwardJson);
                            forwardedEvents.incrementAndGet();
                        }
                    } else if (isEsOpenDirectionEvent(binding.event())) {
                        // ES 09:15 open-direction forecast/outcome: a GLOBAL advisory panel feed, not
                        // per-market data — broadcast STANDALONE (its own message.type) like short-premium,
                        // deliberately NOT gated by the per-market active selection (which is null pre-open
                        // and would suppress the once-a-day 09:15 forecast) and NOT subject to the 15s
                        // market-data staleness gates (a forecast published at 09:15 must still be served
                        // at 11:00 — freshness is the long esOpenDirectionTtlMs window via updateCache).
                        // The live STATUS heartbeat shares this branch with its OWN short window: a status
                        // older than esOpenDirectionStatusTtlMs (5 min) makes updateCache return null
                        // (cacheKey == null), so a stale heartbeat is never live-broadcast here either.
                        if (cacheKey != null && cacheCaughtUpFlag.get()) {
                            broadcast(binding.event(), forwardJson);
                            forwardedEvents.incrementAndGet();
                        }
                    } else if ("short-premium-recommendation".equals(binding.event())) {
                        // Advisory chain-level overlay: broadcast STANDALONE (its own message.type) to every
                        // connected dashboard as soon as a fresh recommendation is cached. Unlike market-data it
                        // is deliberately NOT gated by the per-market active selection — that selection is null
                        // pre-open and until a client selects a market, which would suppress the overlay exactly
                        // when Agent A acts late in the session. The UI filters by symbol client-side, and a
                        // recommendation is low-frequency + keyed to today's expiry, so a global broadcast is safe.
                        if (cacheKey != null && cacheCaughtUpFlag.get()) {
                            broadcast(binding.event(), forwardJson);
                            forwardedEvents.incrementAndGet();
                        }
                    } else if (cacheKey != null && cacheCaughtUpFlag.get()
                            && shouldForward(binding, forwardJson, record, (decided = activeSelection.get()))) {
                        if ("dealer-ledger".equals(binding.event())) {
                            // Delivered STANDALONE (its own message.type), never inside a ui-batch — the UI
                            // has a dedicated dealer-ledger handler and reads no dealerLedgers batch array.
                            broadcast(binding.event(), forwardJson);
                        } else if ("corridor-gauge".equals(binding.event())) {
                            // Likewise STANDALONE: the strike-board strip has its own handler; keeping it
                            // out of ui-batch leaves the established batch schema untouched (shadow mode).
                            broadcast(binding.event(), forwardJson);
                        } else if ("option-truth".equals(binding.event())) {
                            // Compact per-strike signal has its own event in legacy mode. Keeping it out of
                            // ui-batch avoids changing the established batch schema and lets the outbound
                            // channel coalesce latest-by-strike updates directly.
                            broadcast(binding.event(), forwardJson);
                        } else if ("es-strike-intel".equals(binding.event())) {
                            // Enqueue ATOMICALLY with the tombstone: the helper holds `this` (then batchLock)
                            // exactly as evictEsStrikeIntelTombstone does, so a withdrawal that lands between
                            // updateCache above and this enqueue cannot leave a post-withdrawal ghost in the
                            // next batch (it either evicts before we check, or after we put — then it removes).
                            enqueueEsStrikeIntelPendingIfLive(cacheKey, forwardJson);
                        } else {
                            enqueuePending(binding.event(), cacheKey, forwardJson);
                        }
                        forwardedEvents.incrementAndGet();
                        recordSelectedForward(binding, forwardJson, decided);
                    } else if (cacheKey != null) {
                        inactiveDroppedEvents.incrementAndGet();
                        // Rollover-diagnostics fine-grained bucketing (additive; existing counters still
                        // increment). Splits inactiveDroppedEvents into WHY it dropped so a stall shows
                        // up as e.g. "droppedByCacheGate climbing" vs "droppedByOtherReasons climbing".
                        recordDropBucket(binding, forwardJson,
                                cacheCaughtUpFlag.get(),
                                decided != null ? decided : activeSelection.get());
                        // Cache-arrival convergence: a snapshot for the ACTIVE selection was cached but not
                        // live-forwarded (e.g. it arrived already older than maxStaleMs on a closed market
                        // right after the daily roll). Still mark the selection ready so markSelectionReady
                        // fires its one-shot cached re-push and open dashboards repopulate. matchesActiveSelection
                        // guards against off-selection data; markSelectionReady re-validates atomically.
                        ActiveSelection current = activeSelection.get();
                        if (cacheCaughtUpFlag.get() && "snapshot".equals(binding.event())
                                && current != null && matchesActiveSelection(json, current)) {
                            markSelectionReady(current);
                        }
                    }
                }
                purgeExpiredCache(System.currentTimeMillis());
                if (settings.ibkrPreOpenEnabled()) {
                    // Consumer-local window transitions fire from the live loop too — either
                    // consumer alone is enough to hit the fence/takeover/eviction boundaries.
                    sweepIbkrPreOpenGexWindows(System.currentTimeMillis());
                }
            }
        }
    }

    private void runRetryingConsumer(String name, ConsumerAttempt attempt, Runnable afterFailure) {
        long retryDelayMs = settings.consumerRetryInitialMs();
        long maxRetryDelayMs = Math.max(retryDelayMs, settings.consumerRetryMaxMs());
        boolean retry = false;
        while (running.get()) {
            boolean failed = false;
            try {
                attempt.run(retry);
                if (!running.get()) {
                    return;
                }
                failed = true;
                System.err.println("Feed gateway " + name + " consumer exited unexpectedly; restarting.");
            } catch (WakeupException e) {
                if (!running.get()) {
                    return;
                }
                failed = true;
                System.err.println("Feed gateway " + name + " consumer wakeup while running; restarting.");
            } catch (RuntimeException e) {
                if (!running.get()) {
                    return;
                }
                failed = true;
                System.err.println("Feed gateway " + name + " consumer error: " + e.getMessage());
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                System.err.println("Feed gateway " + name + " consumer exhausted heap; exiting for clean pod restart.");
                e.printStackTrace();
                System.exit(137);
            }
            if (!failed) {
                return;
            }
            if (afterFailure != null) {
                afterFailure.run();
            }
            consumerRestarts.incrementAndGet();
            if (!sleepBeforeConsumerRetry(name, retryDelayMs)) {
                return;
            }
            retry = true;
            retryDelayMs = Math.min(maxRetryDelayMs, Math.max(retryDelayMs + 1, retryDelayMs * 2));
        }
    }

    private boolean sleepBeforeConsumerRetry(String name, long retryDelayMs) {
        System.err.println("Feed gateway " + name + " consumer restarting in " + retryDelayMs + " ms.");
        try {
            Thread.sleep(retryDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void maybeSeekSelectedSourceToLatest(
            KafkaConsumer<?, ?> consumer,
            List<TopicPartition> partitions,
            Map<String, TopicBinding> topicEvents
    ) {
        maybeSeekSelectedSourceToLatest(consumer, partitions, topicEvents, Set.of());
    }

    /**
     * @param bootstrapping partitions deliberately replaying a cache window after being discovered mid-run.
     *                      Their backlog is intended, not a stale-source symptom, and must not be measured
     *                      as lag — the response to lag is {@code seekToEnd} across every selected
     *                      partition, which would erase both their rebuild and the healthy partitions'
     *                      positions.
     */
    private void maybeSeekSelectedSourceToLatest(
            KafkaConsumer<?, ?> consumer,
            List<TopicPartition> partitions,
            Map<String, TopicBinding> topicEvents,
            Set<TopicPartition> bootstrapping
    ) {
        long maxLagRecords = settings.maxLagRecords();
        if (maxLagRecords <= 0L) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long previousCheckMs = lastLagCheckMs.get();
        if (nowMs - previousCheckMs < 5_000L || !lastLagCheckMs.compareAndSet(previousCheckMs, nowMs)) {
            return;
        }
        ActiveSelection selection = activeSelection.get();
        List<TopicPartition> selectedPartitions = partitions.stream()
                .filter(partition -> {
                    TopicBinding binding = topicEvents.get(partition.topic());
                    if (binding == null || !selection.source().equals(binding.source())) {
                        return false;
                    }
                    if (bootstrapping.contains(partition)) {
                        return false; // intended replay backlog — see the bootstrapping param
                    }
                    // Never lag-skip max-pain: it is a slow last-value-wins signal whose latest record can
                    // sit far behind the live edge by design, so seeking it to END on a backlog would drop
                    // the current max-pain entirely (the very bug this change fixes). Its own 12h window
                    // already bounds how far back it bootstraps.
                    // Never lag-skip the once-a-day ES open-direction forecast/outcomes either: the same
                    // slow last-value-wins class as max-pain — seeking their partitions to END on a
                    // backlog would drop the day's still-unread forecast entirely; their own 12h window
                    // (esOpenDirectionTtlMs) already bounds how far back they bootstrap. The 60s status
                    // heartbeat rides along (trivial volume; each record it reads through is dropped by
                    // its own SHORT 5-min window when stale, so no stale status ever routes).
                    // Never lag-skip the pre-open IBKR status/control stream either: R-WIRE.2 is
                    // a NON-DROP control path — a seekToEnd could permanently skip a revocation,
                    // generation close or path transition. Volume is tiny (one window's statuses).
                    return !"max-pain".equals(binding.event()) && !isEsOpenDirectionEvent(binding.event())
                            && !"ibkr-preopen-status".equals(binding.event());
                })
                .toList();
        if (selectedPartitions.isEmpty()) {
            return;
        }
        Map<TopicPartition, Long> endOffsets;
        try {
            // Bounded: the no-timeout overload blocks for default.api.timeout.ms (60s) ON THE POLL THREAD.
            endOffsets = boundedEndOffsets(consumer, selectedPartitions);
        } catch (org.apache.kafka.common.errors.TimeoutException e) {
            // A lag CHECK that cannot complete is a skipped check, nothing more. Acting on it — seeking,
            // reporting stale, or letting the exception unwind and rebuild the consumer — would convert a
            // broker metadata hiccup into real data movement. The guard re-arms in 5s.
            return;
        }
        long maxLag = 0L;
        for (TopicPartition partition : selectedPartitions) {
            long endOffset = endOffsets.getOrDefault(partition, 0L);
            long position = consumer.position(partition);
            maxLag = Math.max(maxLag, Math.max(0L, endOffset - position));
        }
        if (maxLag <= maxLagRecords) {
            return;
        }
        consumer.seekToEnd(selectedPartitions);
        seekToLatestEvents.incrementAndGet();
        reportSourceStale(selection, "lag-" + maxLag);
        System.err.println("Feed gateway selected source " + selection.source()
                + " lag=" + maxLag
                + " exceeded maxLagRecords=" + maxLagRecords
                + "; sought selected partitions to latest.");
    }

    /**
     * Parse gate for the drop-classifier verdict stream. A malformed value must never reach the
     * envelope: enrichJson() passes unparseable text through verbatim, which would poison every
     * legacy client's frame ("Bad Data" on the whole feed). Only a JSON object that is a NOWCAST
     * with a non-empty drop_id may broadcast; everything else is dropped (and counted by the
     * caller via dropNowcastMalformed).
     */
    static boolean isBroadcastableDropNowcast(com.fasterxml.jackson.databind.ObjectMapper mapper,
            String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode parsed = mapper.readTree(json);
            return parsed != null && parsed.isObject()
                    && "NOWCAST".equals(parsed.path("message_type").asText())
                    && !parsed.path("drop_id").asText("").isEmpty();
        } catch (Exception malformed) {
            return false;
        }
    }

    private void markCacheRecovering(AtomicBoolean caughtUpFlag) {
        if (caughtUpFlag.getAndSet(false)) {
            broadcast("status", statusJson());
        }
    }

    private List<TopicPartition> partitionsFor(String name, KafkaConsumer<?, ?> consumer, Set<String> topics) {
        return partitionsFor(name, consumer, topics, settings.metadataTimeoutMs());
    }

    /** Graceful-absence overload — see the full overload for the contract. */
    private List<TopicPartition> partitionsFor(String name, KafkaConsumer<?, ?> consumer, Set<String> topics,
                                               long budgetMs) {
        return partitionsFor(name, consumer, topics, budgetMs, Set.of(), false);
    }

    /** Refresh overload (graceful absence, with the current assignment as known topics). */
    private List<TopicPartition> partitionsFor(String name, KafkaConsumer<?, ?> consumer, Set<String> topics,
                                               long budgetMs, Set<String> alreadyKnownTopics) {
        return partitionsFor(name, consumer, topics, budgetMs, alreadyKnownTopics, false);
    }

    /**
     * Resolve the topics' partitions with ONE whole-cluster metadata request per pass
     * ({@link KafkaConsumer#listTopics(Duration)}). Replaces the per-topic probe loop AND the
     * optional-topic whitelist (deleted 2026-08-31): the whitelist itself was the recurring outage —
     * dev wipes every topic nightly, so any newly bound topic someone forgot to whitelist made this
     * method throw for the WHOLE consumer set, restarting it every 30s and blacking out every feed it
     * carried (.pace.rank, gex.oi-status, spot-band-flow mornings). The per-topic probes were also the
     * cost center: an UNKNOWN topic blocked ~1s per probe, so absent topics serially burned the budget;
     * listTopics answers for every topic in one round trip, so absence costs nothing.
     *
     * <p>The contract, per pass:
     * <ul>
     *   <li><b>An absent topic is skipped, not fatal.</b> Its absence is transition-logged
     *       ({@code RGW_ALERT GATEWAY_TOPIC_ABSENT}, greppable) and the topic is re-checked on every
     *       {@link PartitionRefresh} pass — the producer coming up later is picked up without a gateway
     *       restart, and one missing topic can only dark its OWN feature, never the shared consumer.</li>
     *   <li><b>Resolving NOTHING is a failure, never a success.</b> An empty resolution would make the
     *       caller {@code assign([])} — whose next {@code poll()} throws an IllegalStateException that no
     *       longer names the topics — and would let {@code caughtUp({})} certify an EMPTY cache as ready.
     *       The pass retries within the budget and then throws, naming the unresolved set.</li>
     *   <li><b>An ALREADY-ASSIGNED topic vanishing from metadata fails the pass.</b> A broker that stops
     *       answering for topics this consumer is actively assigned to is a metadata outage, not a
     *       producer that has not started: the throw keeps {@link PartitionRefresh}'s absorb site and its
     *       refreshFailing / GATEWAY_PARTITION_METADATA_STALE alarms honest.</li>
     *   <li><b>{@code strictAbsence} restores all-or-nothing</b> for callers whose correctness needs the
     *       FULL set: replay (a missing run topic must surface as a failure, never a silent
     *       REPLAY_COMPLETE with 0 records) and the source-switch offset barrier (a partial barrier map
     *       would wave pre-switch records through on the skipped topic's partitions).</li>
     * </ul>
     */
    private List<TopicPartition> partitionsFor(String name, KafkaConsumer<?, ?> consumer, Set<String> topics,
                                               long budgetMs, Set<String> alreadyKnownTopics,
                                               boolean strictAbsence) {
        long deadlineMs = System.currentTimeMillis() + budgetMs;
        Set<String> unresolved = Set.copyOf(topics);
        while (running.get()) {
            long remainingMs = deadlineMs - System.currentTimeMillis();
            if (remainingMs <= 0L) {
                break;
            }
            Map<String, List<PartitionInfo>> cluster;
            try {
                cluster = consumer.listTopics(Duration.ofMillis(Math.min(5_000L, remainingMs)));
            } catch (org.apache.kafka.common.errors.TimeoutException clusterMetadataTimedOut) {
                continue; // one slice answered nothing; retry within the budget, throw (named) after it
            }
            Map<String, List<PartitionInfo>> resolved = new HashMap<>();
            List<String> absent = new ArrayList<>();
            for (String topic : new java.util.TreeSet<>(topics)) { // stable order for readable logs
                List<PartitionInfo> partitions = cluster.get(topic);
                if (partitions == null || partitions.isEmpty()) {
                    absent.add(topic);
                } else {
                    resolved.put(topic, partitions);
                }
            }
            unresolved = Set.copyOf(absent);
            boolean vanishedKnown = absent.stream().anyMatch(alreadyKnownTopics::contains);
            boolean nothingResolved = resolved.isEmpty() && !topics.isEmpty();
            if ((strictAbsence && !absent.isEmpty()) || vanishedKnown || nothingResolved) {
                // Not a publishable observation (see contract). Brief pause so a fast broker answer of
                // "topic not there yet" does not spin this into a hot loop for the whole budget.
                sleepQuietly(Math.min(200L, Math.max(1L, deadlineMs - System.currentTimeMillis())));
                continue;
            }
            for (String topic : absent) {
                if (absentTopics.add(name + "|" + topic)) {
                    System.err.println("RGW_ALERT GATEWAY_TOPIC_ABSENT " + name
                            + ": topic absent, consuming without it (rechecked every partition refresh): "
                            + topic);
                }
            }
            List<TopicPartition> result = new ArrayList<>();
            for (Map.Entry<String, List<PartitionInfo>> entry : resolved.entrySet()) {
                if (absentTopics.remove(name + "|" + entry.getKey())) {
                    System.out.println("Feed gateway " + name + ": topic present again: " + entry.getKey());
                }
                for (PartitionInfo partition : entry.getValue()) {
                    result.add(new TopicPartition(entry.getKey(), partition.partition()));
                }
            }
            result.sort(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition));
            return result;
        }
        throw new TopicMetadataTimeoutException("Timed out waiting for Kafka topic metadata: " + unresolved);
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0L) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@link #partitionsFor} exceeded its metadata deadline. A distinct type so the in-loop refresh can
     * absorb exactly this transient condition and nothing else — every other Kafka runtime failure
     * (invalid topic, config, auth, wakeup) must still reach {@code runRetryingConsumer}. Extends
     * IllegalStateException so bootstrap callers and existing handlers behave exactly as before.
     */
    static final class TopicMetadataTimeoutException extends IllegalStateException {
        TopicMetadataTimeoutException(String message) {
            super(message);
        }
    }

    static List<TopicPartition> addedPartitions(
            List<TopicPartition> assigned,
            List<TopicPartition> discovered
    ) {
        Set<TopicPartition> existing = Set.copyOf(assigned);
        return discovered.stream().filter(partition -> !existing.contains(partition)).toList();
    }

    /**
     * The assignment a refresh may install: everything currently assigned PLUS what was discovered — never
     * the discovered list on its own.
     *
     * <p>{@link #partitionsFor} deliberately SKIPS a topic that is momentarily absent from
     * metadata (see {@link #isOptionalTopic}). At bootstrap that is a documented, logged, self-healing
     * choice. Inside the poll loop it is not: assigning the discovered list verbatim would silently
     * unassign that topic's partitions for the entire life of the consumer, which is exactly the
     * silent-data-loss shape this refresh mechanism exists to prevent. Taking the union means a transient
     * metadata gap can only ever DELAY growth, never drop a live feed.
     */
    /**
     * Retire the lag-skip exemption for every partition that has replayed up to its bootstrap barrier, and
     * drop its barrier with it so neither collection grows for the life of the consumer.
     *
     * <p>A missing barrier also retires the partition: the exemption suppresses a real stale-source guard,
     * so it must fail CLOSED. An entry that could never be cleared would disable lag protection for that
     * partition permanently and silently.
     */
    private void clearReachedBootstrapBarriers(
            Map<TopicPartition, BootstrapState> bootstrapping,
            List<TopicPartition> ownedByThisConsumer,
            java.util.function.ToLongFunction<TopicPartition> position
    ) {
        if (bootstrapping.isEmpty()) {
            return;
        }
        // Only this consumer's own partitions: position() throws on anything it does not assign, and the
        // registry is shared across consumers.
        Set<TopicPartition> owned = Set.copyOf(ownedByThisConsumer);
        bootstrapping.keySet().removeIf(partition -> {
            if (!owned.contains(partition)) {
                return false;
            }
            BootstrapState state = bootstrapping.get(partition);
            return state == null || position.applyAsLong(partition) >= state.barrier();
        });
    }

    /**
     * True while any partition feeding {@code source} is still replaying a mid-run rebuild — i.e. the cache
     * for that source is knowably incomplete right now.
     */
    private List<TopicPartition> bootstrappingPartitionsForSource(String source) {
        return bootstrappingPartitions.entrySet().stream()
                .filter(e -> source != null && source.equalsIgnoreCase(e.getValue().source()))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition))
                .toList();
    }

    private boolean hasIncompleteBootstrapForSource(String source) {
        if (source == null || source.isBlank() || bootstrappingPartitions.isEmpty()) {
            return false;
        }
        return bootstrappingPartitions.values().stream()
                .anyMatch(state -> source.equalsIgnoreCase(state.source()));
    }

    static List<TopicPartition> mergedAssignment(
            List<TopicPartition> assigned,
            List<TopicPartition> added
    ) {
        List<TopicPartition> merged = new ArrayList<>(assigned);
        merged.addAll(added);
        merged.sort(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition));
        return List.copyOf(merged);
    }

    /**
     * Periodic "did a topic grow?" check for a manually assigned consumer.
     *
     * <p>Manual assignment is intentional — every gateway replica needs a COMPLETE cache, so there is no
     * consumer group and therefore no rebalance to notice a producer expanding a topic. On 2026-07-27 ES
     * seller-activity went 4→32 partitions while the gateway stayed pinned to 0-3, hiding most strikes.
     *
     * <p>Two invariants, both of which cost an outage to learn:
     * <ul>
     *   <li>NEVER shrink — see {@link #mergedAssignment}.</li>
     *   <li>NEVER throw. {@link #partitionsFor} fails loudly on a mandatory-topic metadata timeout, which
     *       is right at bootstrap and self-inflicted damage inside the poll loop: the exception would
     *       unwind through {@code runRetryingConsumer}, tear the consumer down and re-bootstrap the whole
     *       cache window (and flip cache-caught-up to RECOVERING) over a transient broker hiccup. A failed
     *       refresh is a no-op; the next interval retries.</li>
     * </ul>
     */
    private final class PartitionRefresh {
        private final String name;
        private final Set<String> topics;
        private long nextRefreshMs;
        /** When the topology was last observed successfully — diagnostics only, never a seek input. */
        private volatile long lastObservedMs;
        private volatile boolean refreshFailing;
        private volatile int lastDiscoveredCount;

        PartitionRefresh(String name, Set<String> topics) {
            this.name = name;
            this.topics = Set.copyOf(topics);
            long nowMs = System.currentTimeMillis();
            // The caller has just completed its bootstrap partitionsFor(), so the topology is known good now.
            this.lastObservedMs = nowMs;
            this.nextRefreshMs = nowMs + settings.partitionMetadataRefreshMs();
            // Self-register for diagnostics. Deliberately here rather than at each of the six call sites:
            // a consumer loop that is observable only if someone remembered to wire it up is exactly how a
            // partial assignment stays invisible. Consumer names are unique, and a restarted consumer
            // replaces its own entry.
            partitionRefreshes.put(name, this);
        }

        /**
         * Discover and ASSIGN newly created partitions. Seeking and bookkeeping for {@link Refresh#added()}
         * are the caller's job — deliberately, because they differ per consumer (cache window vs END vs
         * BEGINNING) and because a failure there must NOT be swallowed: it unwinds to
         * {@code runRetryingConsumer}, which rebuilds the consumer from a clean bootstrap. The alternative
         * — catching around the assign — would leave the consumer polling a partition the caller does not
         * know about, from {@code auto.offset.reset=latest}, silently skipping its window.
         */
        Refresh apply(KafkaConsumer<?, ?> consumer, List<TopicPartition> assigned) {
            long nowMs = System.currentTimeMillis();
            assignedPartitionCounts.put(name, assigned.size());
            if (nowMs < nextRefreshMs) {
                return Refresh.unchanged(assigned);
            }
            List<TopicPartition> discovered;
            try {
                Set<String> assignedTopics = assigned.stream()
                        .map(TopicPartition::topic)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                discovered = partitionsFor(name, consumer, topics,
                        settings.partitionRefreshMetadataTimeoutMs(), assignedTopics);
            } catch (TopicMetadataTimeoutException e) {
                // The ONLY condition we absorb, and now a dedicated type rather than "any RuntimeException":
                // partitionsFor() exceeded its metadata deadline. At bootstrap that failure is correct;
                // inside the poll loop it would tear the consumer down and re-bootstrap the whole cache
                // window over a broker hiccup. Retry next interval. Everything else -- wakeup, interrupt,
                // auth, invalid topic, config, any other Kafka client failure -- propagates untouched to
                // runRetryingConsumer, which is the component that knows how to rebuild.
                if (!refreshFailing) {
                    refreshFailing = true; // log the TRANSITION only — this can fire every interval
                    System.err.println("Feed gateway " + name + " partition metadata refresh failing,"
                            + " keeping " + assigned.size() + " assigned partition(s), retrying every "
                            + settings.partitionMetadataRefreshMs() + "ms: " + e);
                }
                nextRefreshMs = System.currentTimeMillis() + settings.partitionMetadataRefreshMs();
                return Refresh.unchanged(assigned);
            }
            if (refreshFailing) {
                refreshFailing = false;
                System.out.println("Feed gateway " + name + " partition metadata refresh recovered");
            }
            lastObservedMs = System.currentTimeMillis();
            lastDiscoveredCount = discovered.size();
            // Bound from the END of the attempt: a slow metadata call must not tight-loop.
            nextRefreshMs = lastObservedMs + settings.partitionMetadataRefreshMs();

            List<TopicPartition> added = addedPartitions(assigned, discovered);
            if (added.isEmpty()) {
                return Refresh.unchanged(assigned);
            }
            List<TopicPartition> merged = mergedAssignment(assigned, added);
            consumer.assign(merged);
            Refresh refresh = Refresh.grown(merged, added, assigned);
            System.out.println("Feed gateway " + name + " discovered " + added.size()
                    + " new Kafka partition(s): " + added + " (now assigned " + merged.size()
                    + ", onGrownTopics=" + refresh.addedOnGrownTopics().size()
                    + ", onNewTopics=" + refresh.addedOnNewTopics().size() + ")");
            return refresh;
        }

        long lastObservedMs() {
            return lastObservedMs;
        }

        boolean refreshFailing() {
            return refreshFailing;
        }

        int lastDiscoveredCount() {
            return lastDiscoveredCount;
        }
    }

    /**
     * The outcome of one refresh. {@code added} is split by WHY the partition is new, because the two cases
     * have opposite safe recovery boundaries:
     *
     * <ul>
     *   <li>{@code addedOnGrownTopics} — the topic was ALREADY assigned and its partition count grew. Kafka
     *       creates such a partition EMPTY, so its log holds exactly the records written since creation:
     *       seeking it to BEGINNING recovers precisely what was missed, bounded by the discovery delay, with
     *       no dependency on any clock.</li>
     *   <li>{@code addedOnNewTopics} — the whole topic just appeared (an OPTIONAL topic whose producer was
     *       not yet deployed; see {@link #isOptionalTopic}). Its partitions can hold a FULL retention of
     *       history, so beginning-seeking them would replay hours into the broadcast path. These must be
     *       treated exactly as bootstrap would treat them.</li>
     * </ul>
     *
     * <p>This split replaced a timestamp-based recovery window that fed the gateway's wall clock into
     * {@code offsetsForTimes}. That was unsound: {@code offsetsForTimes} matches on RECORD timestamps, which
     * on a {@code CreateTime} topic are the PRODUCER's clock. A producer running behind the gateway could
     * write a discrete event whose timestamp preceded the cutoff, and the seek would skip it — silently and
     * permanently losing a {@code turn-alert} or {@code spread-skew-event}. Offsets carry no such
     * assumption.
     *
     * @param partitions the assignment now in effect — the caller MUST adopt it before seeking, so a seek
     *                   failure cannot leave the caller tracking a stale list.
     */
    private record Refresh(
            List<TopicPartition> partitions,
            List<TopicPartition> added,
            List<TopicPartition> addedOnGrownTopics,
            List<TopicPartition> addedOnNewTopics
    ) {
        static Refresh unchanged(List<TopicPartition> assigned) {
            return new Refresh(assigned, List.of(), List.of(), List.of());
        }

        static Refresh grown(List<TopicPartition> merged, List<TopicPartition> added,
                             List<TopicPartition> previouslyAssigned) {
            Set<String> knownTopics = previouslyAssigned.stream()
                    .map(TopicPartition::topic)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            List<TopicPartition> onGrown = added.stream().filter(p -> knownTopics.contains(p.topic())).toList();
            List<TopicPartition> onNew = added.stream().filter(p -> !knownTopics.contains(p.topic())).toList();
            return new Refresh(merged, added, onGrown, onNew);
        }
    }

    /**
     * {@code endOffsets} with an explicit bound. The no-timeout overload blocks for
     * {@code default.api.timeout.ms} — 60s — and the refresh/selection-switch paths call this ON THE POLL
     * THREAD, where a broker hiccup would freeze consumption for a minute and pressure the whole pipeline.
     * Callers treat a thrown TimeoutException like any other refresh failure: unwind or retry next poll.
     */
    private Map<TopicPartition, Long> boundedEndOffsets(KafkaConsumer<?, ?> consumer,
                                                        Collection<TopicPartition> partitions) {
        return boundedEndOffsets(consumer, partitions, settings.partitionRefreshMetadataTimeoutMs());
    }

    private Map<TopicPartition, Long> boundedEndOffsets(KafkaConsumer<?, ?> consumer,
                                                        Collection<TopicPartition> partitions,
                                                        long budgetMs) {
        return consumer.endOffsets(partitions, Duration.ofMillis(budgetMs));
    }

    private boolean caughtUp(KafkaConsumer<?, ?> consumer, Map<TopicPartition, Long> endOffsets) {
        for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
            if (consumer.position(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private Map<TopicPartition, Long> catchUpEndOffsets(
            Map<TopicPartition, Long> endOffsets,
            Map<String, TopicBinding> topicEvents
    ) {
        ActiveSelection selection = activeSelection.get();
        Map<TopicPartition, Long> selectedEndOffsets = new LinkedHashMap<>();
        for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
            TopicBinding binding = topicEvents.get(entry.getKey().topic());
            if (binding == null) {
                continue;
            }
            if ("ibkr-preopen-status".equals(binding.event())
                    || isIbkrPreOpenSharedGexTopic(entry.getKey().topic())
                    || "indicators".equals(binding.event())
                    || "tapeZones".equals(binding.event())
                    || requiresCatchUpForActiveSource(selection.source(), binding.source())) {
                // The pre-open status/control stream is SOURCE-INDEPENDENT window state:
                // stateCaughtUp must include its partition regardless of the active market-data
                // source, or replay could expose a pre-revocation snapshot (round-2 finding 4).
                // With the pre-open feature enabled the SHARED live gex topic (slice 2) is window
                // state too — its sessioned records must be caught up before replay claims
                // completeness, whatever source the user selected.
                // Indicators (r1 finding 4) are likewise source-independent GLOBAL truth —
                // their partitions gate the barrier no matter which source is active. The
                // tape-zones board joins them: it is ES-global truth, not per-source market data.
                selectedEndOffsets.put(entry.getKey(), entry.getValue());
            }
        }
        return selectedEndOffsets.isEmpty() ? endOffsets : selectedEndOffsets;
    }

    /**
     * The same active-source filter as {@link #catchUpEndOffsets} WITHOUT its empty-result fallback, for
     * partitions added mid-run. An empty result here means "none of the added partitions are on the
     * selected source", which must stay empty — falling back to "all of them" would gate cache readiness
     * on a source nobody is watching.
     */
    private Map<TopicPartition, Long> selectedSourceBarriers(
            Map<TopicPartition, Long> endOffsets,
            Map<String, TopicBinding> topicEvents
    ) {
        ActiveSelection selection = activeSelection.get();
        Map<TopicPartition, Long> selected = new LinkedHashMap<>();
        for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
            TopicBinding preOpenBinding = topicEvents.get(entry.getKey().topic());
            if (preOpenBinding != null && ("ibkr-preopen-status".equals(preOpenBinding.event())
                    || isIbkrPreOpenSharedGexTopic(entry.getKey().topic())
                    || "indicators".equals(preOpenBinding.event())
                    || "tapeZones".equals(preOpenBinding.event()))) {
                // Source-independent streams (pre-open control + shared live gex + indicators +
                // tape-zones board) always gate mid-run barriers too (r1 finding 4).
                selected.put(entry.getKey(), entry.getValue());
                continue;
            }
            TopicBinding binding = topicEvents.get(entry.getKey().topic());
            if (binding != null && requiresCatchUpForActiveSource(selection.source(), binding.source())) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return selected;
    }

    static boolean requiresCatchUpForActiveSource(String activeSource, String bindingSource) {
        return activeSource != null && activeSource.equals(bindingSource);
    }

    /** True when the pre-open feature is on and {@code topic} is the SHARED live gex topic (USER
     *  D14) — then its partitions are source-independent window state for the catch-up barriers. */
    private boolean isIbkrPreOpenSharedGexTopic(String topic) {
        return settings.ibkrPreOpenEnabled() && topic.equals(settings.databentoGexTopic());
    }

    private void markCacheCaughtUp(String name, List<String> events, AtomicBoolean caughtUpFlag) {
        if (caughtUpFlag.compareAndSet(false, true)) {
            // Run the whole catch-up replay under readyLock so the active selection is STABLE across the
            // capture, the cached-batch build (cachedEvents/uiBatchEnvelopeJson re-read activeSelection),
            // and the readiness commit. Without the lock a concurrent applySelection could swap the active
            // selection mid-replay, broadcasting a cached batch for a different selection than intended.
            // Lock order is readyLock -> this (cachedEvents is synchronized), consistent with applySelection;
            // markSelectionReady's readyLock is reentrant here.
            synchronized (readyLock) {
                ActiveSelection selection = activeSelection.get();
                broadcast("status", statusJson());
                if (broadcastCachedState(events)) {
                    markSelectionReady(selection);
                }
            }
            // ES open-direction forecast/outcomes are STANDALONE (never inside the ui-batch that
            // broadcastCachedState assembles), so re-push them explicitly to the already-connected
            // clients once this consumer's cache is caught up — a dashboard left open across a
            // gateway restart must get the morning forecast back without a page reload.
            // (The status heartbeat rides along: replayEsOpenDirectionCached only re-pushes it when
            // still inside its own SHORT 5-min isCacheFresh window — never a stale overnight status.)
            if (events.contains("es-open-direction-forecast") || events.contains("es-open-direction-outcome")
                    || events.contains("es-open-direction-status")) {
                for (WebSocketSession client : clients) {
                    replayEsOpenDirectionCached(client);
                }
            }
            // Greek-move-authenticity CURRENT verdict is STANDALONE (never in the ui-batch) too: re-push it
            // explicitly to already-connected clients once this consumer's cache is caught up, so a
            // dashboard left open across a gateway restart gets the current verdict back without a reload.
            // (replayGreekMoveAuthCached only re-pushes verdicts still inside their SHORT 5-min isCacheFresh
            // window — never a stale overnight verdict.)
            if (events.contains("greek-move-auth")) {
                for (WebSocketSession client : clients) {
                    replayGreekMoveAuthCached(client);
                }
            }
            // Spot-vol-regime CURRENT is STANDALONE (never in the ui-batch) too: re-push it explicitly
            // once this consumer's cache is caught up (fresh-window-gated inside the replay helper).
            if (events.contains("spot-vol-regime")) {
                for (WebSocketSession client : clients) {
                    replaySpotVolRegimeCached(client);
                }
            }
            // Vol-premium IV/RV is its OWN standalone event with its OWN topic. Gating its
            // catch-up on spot-vol-regime's presence meant a consumer carrying only the IV/RV
            // topic never re-pushed it, and one carrying only spot-vol-regime re-pushed IV/RV it
            // had not consumed.
            if (events.contains("vol-premium-ivrv")) {
                for (WebSocketSession client : clients) {
                    replayVolPremiumIvrvCached(client);
                }
            }
            // Indicator CURRENT is STANDALONE too: explicit re-push once caught up.
            if (events.contains("indicators")) {
                for (WebSocketSession client : clients) {
                    replayIndicatorsCached(client);
                }
            }
            // Tape-zones board is STANDALONE too: explicit re-push once caught up.
            if (events.contains("tapeZones")) {
                for (WebSocketSession client : clients) {
                    replayTapeZonesCached(client);
                }
            }
            if (events.contains("ibkr-preopen-status")) {
                for (WebSocketSession client : clients) {
                    replayIbkrPreOpenCached(client);
                }
            }
            // Pre-open IBKR GEX value plane (slice 2): re-push once BOTH barriers are up — the avro
            // consumer ingests the values, the JSON state consumer the revocation/generation
            // controls and pairing statuses (round-1 finding 2: serving before the control stream
            // is caught up could expose a revoked value). Both consumers' event lists contain
            // "gex-by-strike", so whichever catches up LAST triggers this re-push exactly once.
            // Replays are idempotent client-side (phase + per-key offset last-writer-wins).
            if (settings.ibkrPreOpenEnabled() && events.contains("gex-by-strike")
                    && ibkrPreOpenGexServingUp()) {
                for (WebSocketSession client : clients) {
                    replayIbkrPreOpenGexCached(client);
                }
            }
            // Close-direction is STANDALONE (never in the ui-batch) too: re-push the session's frozen
            // verdict / current interim to already-connected clients once this consumer's cache is
            // caught up, so a dashboard left open across a gateway restart gets the card back.
            if (events.contains("close-direction")) {
                for (WebSocketSession client : clients) {
                    replayCloseDirectionCached(client);
                }
            }
            System.out.println("Feed gateway " + name + " cache caught up; replayed cached state to clients.");
        }
    }

    private ActiveSelection selectionFromJson(String json, long recordTimestampMs) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(json);
            String command = text(root, "command");
            String type = text(root, "type");
            if (!"select-market-data-source".equalsIgnoreCase(command)
                    && !"select-market-data-source".equalsIgnoreCase(type)) {
                return null;
            }
            String source = GatewaySettings.normalizeSource(text(root, "source"));
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            if (source.isBlank() || symbol.isBlank() || expiry.isBlank()) {
                return null;
            }
            long selectedAtMs = parseInstantMs(text(root, "requestedAt"), recordTimestampMs);
            long epoch = longField(root, "selectionEpoch", selectedAtMs);
            return new ActiveSelection(source, symbol, expiry, epoch, selectedAtMs);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private void applySelection(ActiveSelection next) {
        // The whole roll lifecycle — active swap, reset/source-switching broadcasts, switch-time cached
        // replay, and readiness — runs under readyLock so it is atomic against any concurrent
        // markSelectionReady from a consumer thread. A consumer can therefore never observe a half-rolled
        // state (new activeSelection but old readiness, or vice versa). markSelectionReady itself locks
        // readyLock (reentrant on this thread). Lock order is always readyLock -> {batchLock, this}, never
        // the inverse, so there is no deadlock. Rolls are rare, so the wider critical section is cheap.
        synchronized (readyLock) {
            ActiveSelection previous = activeSelection.get();
            if (!next.newerThan(previous)) {
                return;
            }
            // A new epoch for the same source/symbol/expiry is a control-plane reassertion, not a market
            // switch. Web-pod restarts used to generate exactly this record and the global reset below
            // blanked every authenticated dashboard even though all data services were still healthy.
            // Keep the established selection (including its readiness/barriers) and accept future records:
            // the forward gate rejects only epochs OLDER than the active epoch, so a newer producer epoch
            // continues through without forcing clients to throw away cumulative volume and pace.
            if (sameMarketSelection(previous, next)) {
                System.out.println("RGW_SELECTION_REASSERT event=selection_reassert_ignored"
                        + " activeSelection=" + describeSelection(previous)
                        + " ignoredSelection=" + describeSelection(next));
                return;
            }
            // Rollover-diagnostics WARN — moment-of-truth log emitted BEFORE the swap so a grep-friendly
            // before/after record exists in Loki for the 2026-07-01-style silent-wedge incidents. Additive;
            // does not gate the roll. (Wrapped so a diag failure never breaks the roll.)
            try {
                emitRolloverWarn(previous, next);
            } catch (RuntimeException ignored) {
                // instrumentation must never fail a real rollover
            }
            offsetBarriers.set(captureOffsetBarriers(next));
            // NOTE: readySelectionKey is intentionally NOT reset here. markSelectionReady detects a NEW
            // selection by key change, so resetting to "" is unnecessary and previously opened a race:
            // a consumer could mark the OLD selection ready in the window between the reset and the
            // activeSelection swap, burning the new selection's one-shot before it became active.
            activeSelection.set(next);
            synchronized (batchLock) {
                clearPendingLocked();
            }
            String resetJson = activeSelectionJson(next);
            broadcast("reset", resetJson);
            broadcast("source-switching", activeSelectionJson(next, "source-switching"));
            broadcast("status", statusJson());
            boolean replayed = broadcastCachedState(sourceSwitchReplayEvents());
            // NB: the band batch is deliberately NOT replayed here. When this selection is already
            // serviceable, markSelectionReady(next) runs immediately below and replays it — doing it
            // here too sent every socket the same batch twice for one switch. Readiness is also the
            // correct moment: before it, the new selection's cache is knowably incomplete, so a replay
            // here would push exactly the partial board the readiness gate exists to withhold.
            // WITHHOLD readiness while the newly selected source has partitions still replaying a mid-run
            // rebuild. Announcing source-ready here would certify a cache that is knowably missing every
            // strike on those partitions — the original incident's symptom, reached by a different route.
            // The owning cache consumer recomputes its barriers for this selection on its next poll and
            // calls markSelectionReady once they are met, so readiness is deferred, never dropped.
            if (hasIncompleteBootstrapForSource(next.source())) {
                System.out.println("RGW_SELECTION_READY_DEFERRED event=selection_ready_deferred"
                        + " selection=" + describeSelection(next)
                        + " bootstrappingPartitions=" + bootstrappingPartitionsForSource(next.source()));
            } else if (replayed) {
                markSelectionReady(next);
            }
        }
        System.out.println("Feed gateway selected market data source " + next.source()
                + " " + next.symbol() + " " + next.expiry()
                + " epoch=" + next.selectionEpoch());
    }

    private static boolean sameMarketSelection(ActiveSelection left, ActiveSelection right) {
        return left != null && right != null
                && left.source().equalsIgnoreCase(right.source())
                && left.symbol().equalsIgnoreCase(right.symbol())
                && left.expiry().equals(right.expiry());
    }

    /**
     * AUTO-expiry daily roll: when the ET trading date advances, switch the active selection to the new
     * date so the gateway's strict expiry filter stays locked to the date the Databento feed publishes
     * (the feed self-rolls the same way). Reuses {@link #applySelection} — the SAME path as a control-topic
     * selection — so the chain reset/replay/readiness behave identically. {@code autoRolledExpiry} guards it
     * to fire once per new trading day, and a manual selection holds for the day (auto resumes next day).
     * No-op when pinned to an explicit IB_EXPIRY. Scheduled every 60s; the date only changes overnight, so
     * this is never a mid-session swap. A fresh ms epoch makes the rolled selection supersede prior ones.
     */
    private void maybeAutoRollExpiry() {
        if (!settings.autoExpiry()) {
            return;
        }
        String target;
        try {
            target = marketCalendar.currentTradingDate(Instant.now(), settings.expiryRollAfter())
                    .format(DateTimeFormatter.BASIC_ISO_DATE);
        } catch (RuntimeException e) {
            return; // a calendar hiccup must never kill the scheduled task
        }
        ActiveSelection current = activeSelection.get();
        // A control-topic selection for the current/future session holds for the day. But a stale
        // selection pinning an EXPIRED expiry (before the session target — e.g. a retained UI
        // selection adopted on boot, or one republished after the roll) must NOT strand the chain on
        // the dead contract, so override it. yyyyMMdd compares chronologically as a string.
        String currentExpiry = current.expiry();
        boolean stale = currentExpiry != null && currentExpiry.length() == 8
                && currentExpiry.chars().allMatch(Character::isDigit)
                && currentExpiry.compareTo(target) < 0;
        if (target.equals(autoRolledExpiry) && !stale) {
            return; // same trading day, or a current/future selection holds for today
        }
        autoRolledExpiry = target;
        if (target.equals(currentExpiry)) {
            return; // already serving the new date
        }
        long now = System.currentTimeMillis();
        System.out.println("Feed gateway auto-rolling expiry " + currentExpiry + " -> " + target
                + (stale ? " (overriding stale/expired selection)" : " (new ET trading day)"));
        applySelection(new ActiveSelection(current.source(), current.symbol(), target, now, now));
    }

    /**
     * The canonical current-session expiry for the SPX-only 0DTE strike-invasion signal, which carries
     * no expiry of its own (its {@code StrikeInvasionSnapshot} contract is keyed symbol|strike). Derived
     * from the SAME market-calendar trading date the Databento feed self-rolls to (see
     * {@link #maybeAutoRollExpiry}) — the exact chain the strike-invasion producer emits for — so it is
     * INDEPENDENT of any pinned {@code IB_EXPIRY} or per-session manual selection. Falls back to
     * {@code autoRolledExpiry} only if the calendar is momentarily unavailable.
     */
    private String currentInvasionExpiry() {
        try {
            return marketCalendar.currentTradingDate(Instant.now()).format(DateTimeFormatter.BASIC_ISO_DATE);
        } catch (RuntimeException e) {
            return autoRolledExpiry; // calendar hiccup — the daily-rolled date is the best available proxy
        }
    }

    private Map<TopicPartition, Long> captureOffsetBarriers(ActiveSelection selection) {
        List<String> topics = outputTopicsForSource(selection.source());
        if (topics.isEmpty()) {
            return Map.of();
        }
        Properties barrierProps = stringConsumerProperties("barrier");
        // endOffsets() must return the PHYSICAL high watermark here, so force read_uncommitted (read_committed
        // would return the last-stable offset and capture a too-low barrier while a pre-open txn is open).
        barrierProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, BARRIER_CONSUMER_ISOLATION);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(barrierProps)) {
            List<TopicPartition> partitions = partitionsFor("barrier", consumer, Set.copyOf(topics),
                    settings.metadataTimeoutMs(), Set.of(), true);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            System.out.println("Feed gateway captured " + endOffsets.size()
                    + " switch offset barriers for " + selection.source()
                    + " " + selection.symbol() + " " + selection.expiry()
                    + " epoch=" + selection.selectionEpoch());
            return Map.copyOf(endOffsets);
        } catch (RuntimeException e) {
            System.err.println("Feed gateway could not capture switch offset barriers; falling back to time/epoch barrier: "
                    + e.getMessage());
            return Map.of();
        }
    }

    private List<String> outputTopicsForSource(String source) {
        if ("IBKR".equals(source)) {
            return List.of(
                    settings.ibkrDisplayTopic(),
                    settings.ibkrPaceTopic(),
                    settings.ibkrPaceRankTopic(),
                    settings.ibkrDirectionalPressureTopic(),
                    settings.ibkrVixPriceTopic(),
                    settings.ibkrVolumeSandwichTopic(),
                    settings.ibkrVolumeSandwichAlertsTopic(),
                    settings.ibkrUnusualWhalesGexTopic(),
                    settings.ibkrUnusualWhalesGexHistoryTopic()
            );
        }
        if ("DATABENTO".equals(source)) {
            return List.of(
                    settings.databentoDisplayTopic(),
                    settings.databentoPaceTopic(),
                    settings.databentoPaceRankTopic(),
                    settings.databentoDirectionalPressureTopic(),
                    settings.ibkrVixPriceTopic(),
                    settings.databentoEsTradesTopic(),
                    settings.underlyingSpxPriceTopic(),
                    settings.databentoStrikeFlowTopic(),
                    settings.databentoSellerActivityTopic(),
                    settings.databentoDeltaFlowByStrikeTopic(),
                    settings.strikeIntelByStrikeTopic(),
                    settings.optionTruthByStrikeTopic(),
                    settings.strikeInvasionTopic(),
                    settings.corridorGaugeTopic(),
                    settings.strikeLiquidityTopic(),
                    settings.databentoPaceMissionTopic(),
                    settings.missionControlTopic(),
                    settings.spreadSkewTopic(),
                    settings.optionPriceBehaviorDashboardTopic(),
                    settings.optionPriceBehaviorByOptionTopic(),
                    settings.optionPriceBehaviorSessionTopic(),
                    settings.databentoGexTopic(),
                    settings.unifiedSrTopic(),
                    settings.databentoGexMagnetTopic(),
                    settings.gammaMigrationTopic(),
                    settings.gammaRotationTopic(),
                    settings.gammaFragilityTopic(),
                    settings.databentoMaxPainTopic(),
                    settings.databentoVolumeSandwichTopic(),
                    settings.databentoVolumeSandwichAlertsTopic(),
                    settings.databentoMissionSandwichTopic()
            );
        }
        return List.of();
    }

    /**
     * Whether a LIVE spot-band record must skip the individual forward.
     *
     * <p>True only under per-session routing, where the live path sends one socket message per record and
     * no client handler exists for it. Under legacy routing it must be FALSE, so the record falls through
     * to enqueuePending — the ui-batch, which is the only route the page reads.
     */
    static boolean skipsLiveSpotBandForward(String event, boolean perSessionRouting) {
        return "spot-band".equals(event) && perSessionRouting;
    }

    static List<String> sourceSwitchReplayEvents() {
        // NB: dealer-ledger is intentionally ABSENT — it is delivered standalone (not via the ui-batch
        // this list feeds). After a source switch it self-heals from the next live dealer-ledger record.
        return List.of("snapshot", "pace", "pace-rank", "directional-pressure", "vix-price", "index-price", "spx-price", "strike-flow", "spot-band", "seller-activity", "delta-flow", "strike-intel", "strike-invasion", "liquidity-heatmap", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "option-price-behavior", "opb-by-option", "opb-session", "gex-by-strike", "gex-oi-status", "strike-sr", "gex-magnet", "gamma-migration", "es-gex", "es-strike-intel", "max-pain", "gex-strike-lifecycle");
    }

    /**
     * An index price is trusted only when the payload explicitly proves Databento provenance.
     * Missing, malformed, synthetic, or differently sourced payloads fail closed. Other event
     * types are intentionally unaffected.
     */
    private boolean isTrustedIndexPrice(TopicBinding binding, String json) {
        if (binding == null || !"index-price".equals(binding.event())) {
            return true;
        }
        if (!"DATABENTO".equals(binding.source()) || json == null || json.isBlank()) {
            return false;
        }
        try {
            return "DATABENTO".equalsIgnoreCase(text(mapper.readTree(json), "source").trim());
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    /**
     * The exhaustive set of provenance tokens the feed may stamp on {@code underlying.spx.price}
     * (config.py allowed_sources: the cascade tiers plus the static/legacy providers). Anything else —
     * including a MISSING source, which {@link #enrichJson} would have rewritten to the binding source
     * "DATABENTO" before validation — fails closed: "DATABENTO" is deliberately NOT in this set, so a
     * record that never declared its cascade provenance can never launder itself into the spot SSOT.
     */
    private static final Set<String> SPX_SPOT_ALLOWED_SOURCES = Set.of(
            "NATIVE_SPX_INDEX", "ES_BASIS_DERIVED", "SYNTHETIC_OPTION_SPOT",
            "IBKR_INDEX", "STATIC_DEV", "CASCADED");

    /**
     * A canonical SPX spot record is valid only when it names the SPX symbol, carries a positive finite
     * price, and declares one of the feed's legal cascade/static provenance tiers
     * ({@link #SPX_SPOT_ALLOWED_SOURCES}). Malformed, foreign-symbol, unpriced, or unproven payloads
     * fail closed — the topic is the spot SSOT boundary, so garbage must never reach the cache or a
     * client. NOTE: this deliberately replaces (not reuses) the index-price source=="DATABENTO" gate —
     * the canonical spot's honest provenance is its cascade tier, never a market-data source token.
     * Applied identically at cache-consume, live-consume, live-forward, and historical-replay emission.
     * Other event types are intentionally unaffected.
     */
    private boolean isValidSpxPrice(TopicBinding binding, String json) {
        if (binding == null || !"spx-price".equals(binding.event())) {
            return true;
        }
        return isValidSpxPriceJson(json);
    }

    /** Event-scoped core of {@link #isValidSpxPrice}, reused by the replay path (which has no binding). */
    private boolean isValidSpxPriceJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!"SPX".equalsIgnoreCase(text(root, "symbol"))) {
                return false;
            }
            // Tolerate case drift on an otherwise-legal token (the producer uppercases), but an unknown
            // or missing token fails closed.
            if (!SPX_SPOT_ALLOWED_SOURCES.contains(text(root, "source").toUpperCase(Locale.ROOT))) {
                return false;
            }
            JsonNode price = root.get("price");
            return price != null && price.isNumber() && Double.isFinite(price.asDouble())
                    && price.asDouble() > 0.0;
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    private boolean shouldForward(TopicBinding binding, String json, ConsumerRecord<?, ?> record) {
        return shouldForward(binding, json, record, activeSelection.get());
    }

    // Overload taking the selection captured ONCE at the record's decision point, so the forward gate,
    // the forward bookkeeping, and the readiness decision all reason about the SAME selection snapshot
    // (no mid-record activeSelection re-read race across a roll boundary).
    private boolean shouldForward(TopicBinding binding, String json, ConsumerRecord<?, ?> record, ActiveSelection selection) {
        if (binding == null || selection == null) {
            return false;
        }
        if ("vix-price".equals(binding.event())) {
            return passesSelectionBarrier(record, selection);
        }
        if ("index-price".equals(binding.event())) {
            return "DATABENTO".equals(selection.source())
                    && isTrustedIndexPrice(binding, json)
                    && passesSelectionBarrier(record, selection);
        }
        if ("spx-price".equals(binding.event())) {
            // The canonical SPX spot only has meaning for Databento sessions (the multi-tenant engine);
            // validity replaces the index-price provenance gate (see isValidSpxPrice).
            return "DATABENTO".equals(selection.source())
                    && isValidSpxPrice(binding, json)
                    && passesSelectionBarrier(record, selection);
        }
        if ("mission-pace".equals(binding.event())) {
            // Mission-pace is a low-frequency, per-MARKET signal (symbol|expiry), not per-strike. The
            // source-switch OFFSET barrier exists to suppress pre-switch high-frequency snapshot/gex/
            // display records; applied to mission-pace it perpetually classifies fresh frames as
            // pre-switch and drops them (inactiveDropped/sourceStale), so the page never receives
            // data. Forward when the source matches, the frame is fresh (time barrier), and it
            // matches the active market — matchesActiveSelection still enforces the symbol/expiry/
            // source identity, so there is no cross-market or cross-source leak.
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesActiveSelection(json, selection);
        }
        if ("mission-control".equals(binding.event())) {
            // Mission-control is a low-frequency, per-MARKET signal (symbol|expiry), not per-strike. The
            // source-switch OFFSET barrier exists to suppress pre-switch high-frequency snapshot/gex/
            // display records; applied to mission-control it perpetually classifies fresh frames as
            // pre-switch and drops them (inactiveDropped/sourceStale), so the page never receives
            // data. Forward when the source matches, the frame is fresh (time barrier), and it
            // matches the active market — matchesActiveSelection still enforces the symbol/expiry/
            // source identity, so there is no cross-market or cross-source leak.
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesActiveSelection(json, selection);
        }
        if ("spread-skew".equals(binding.event())) {
            // Spread-skew is a low-frequency, whole-UNDERLYING signal (a single snapshot per underlying),
            // the same class as mission-control: the source-switch OFFSET barrier would perpetually
            // classify its fresh frames as pre-switch and drop them, so bypass only the offset barrier.
            // The time barrier runs on the PAYLOAD ts (spreadSkewTimestamp via eventCacheTimestamp) — a
            // producer catching up on a backlog must never forward a stale skew state — and
            // matchesSpreadSkewSelection enforces the underlying/expiry/source identity (the payload
            // names its market `underlying`, not `symbol`, and its expiry is nullable).
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(eventCacheTimestamp(binding.event(), record, json), selection)
                    && matchesSpreadSkewSelection(json, selection);
        }
        if ("strike-sr".equals(binding.event())) {
            // Strike-S/R is a low-frequency derived selected-market signal. It may be (re)started after the
            // gateway has captured source-switch offset barriers, so applying that offset barrier can suppress
            // valid fresh levels indefinitely. Keep the same source/symbol/expiry and max-stale gates used by
            // mission-* while bypassing only the offset barrier.
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesActiveSelection(json, selection);
        }
        if ("corridor-gauge".equals(binding.event())) {
            // Same class as gamma-migration: low-frequency derived per-chain state; the offset
            // barrier would suppress fresh values indefinitely after a source switch.
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesActiveSelection(json, selection);
        }
        if ("gamma-migration".equals(binding.event())) {
            // Same class as gex-magnet: a low-frequency derived per-chain signal that may be
            // (re)started after the source-switch offset barrier is captured, so applying that
            // barrier would suppress valid fresh values indefinitely. Keep the source/symbol/
            // expiry and max-stale gates; bypass only the offset barrier.
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesActiveSelection(json, selection);
        }
        if ("gex-magnet".equals(binding.event())) {
            // GEX-magnet is a low-frequency derived selected-market signal (per chain), same class as
            // strike-sr. It may be (re)started after source-switch offset barriers are captured, so applying
            // that offset barrier can suppress valid fresh values indefinitely. Keep the same
            // source/symbol/expiry and max-stale gates while bypassing only the offset barrier.
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesActiveSelection(json, selection);
        }
        if ("mission-sandwich".equals(binding.event())) {
            // Mission-sandwich is a low-frequency, per-MARKET signal (symbol|expiry), not per-strike —
            // the same class as mission-pace/mission-control. The source-switch OFFSET barrier exists to
            // suppress pre-switch high-frequency snapshot/gex/display records; applied to mission-sandwich
            // it perpetually classifies its fresh frames as pre-switch and drops them (inactiveDropped/
            // sourceStale), so the page never receives the sandwich. Forward when the source matches, the
            // frame is fresh (time barrier), and it matches the active market — the binding.source() ==
            // selection.source() check isolates the source, and matchesActiveSelection enforces the
            // symbol/expiry identity, so there is no cross-market or cross-source leak.
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesActiveSelection(json, selection);
        }
        if ("option-price-behavior".equals(binding.event())) {
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesOptionPriceBehaviorSelection(json, selection);
        }
        if ("opb-session".equals(binding.event())) {
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesOptionPriceBehaviorSelection(json, selection);
        }
        if ("gex-strike-lifecycle".equals(binding.event())) {
            // Per-strike HIGH-frequency signal (like gex-by-strike), so KEEP the source-switch OFFSET barrier
            // (pre-switch records must still be suppressed) — but run the freshness/selection TIME barrier on
            // the PAYLOAD eventTimeMs (via eventCacheTimestamp), not Kafka arrival: a backfilling producer whose
            // records ARRIVE fresh but carry an OLD eventTimeMs must never live-forward a stale badge, matching
            // the cache/replay path. A missing/unparseable eventTimeMs yields a negative ts → time barrier fails
            // closed. matchesActiveSelection enforces the symbol/expiry/source identity (no cross-market leak).
            if (!binding.source().equals(selection.source())) {
                return false;
            }
            long lifecycleTs = eventCacheTimestamp(binding.event(), record, json);
            if (!passesSelectionTimeBarrier(lifecycleTs, selection)
                    || !passesOffsetBarrier(new TopicPartition(record.topic(), record.partition()), record.offset())) {
                reportSourceStale(selection, "switch-barrier");
                return false;
            }
            return matchesActiveSelection(json, selection);
        }
        // opb-by-option is a normal per-contract signal (symbol|expiry|strike) — fall through to the
        // default contract routing (passesSelectionBarrier + matchesActiveSelection) below.
        if (!binding.source().equals(selection.source())) {
            return false;
        }
        // Terminal max-pain: bypass the selection-barrier so the EXPIRED transition is forwarded ONCE
        // even when the producer's Kafka timestamp predates the current source-switch barrier. The
        // selection-match check below still applies, so a terminal for a different (symbol, expiry)
        // does NOT leak to the active selection.
        boolean isTerminalMaxPain = "max-pain".equals(binding.event()) && isMaxPainExpired(json);
        if (!isTerminalMaxPain && !passesSelectionBarrier(record, selection)) {
            reportSourceStale(selection, "switch-barrier");
            return false;
        }
        return matchesActiveSelection(json, selection);
    }

    // `decided` is the selection captured at the forward decision point and already proven to match the
    // payload by shouldForward(...). Stamping sourceLastForwardedAt for THAT selection (not a fresh re-read)
    // means a roll landing here can never record a phantom "recent forward" for the new selection and
    // wrongly suppress its source-stale.
    private void recordSelectedForward(TopicBinding binding, String json, ActiveSelection decided) {
        if (decided == null) {
            return;
        }
        sourceLastForwardedAt.put(selectionKey(decided), System.currentTimeMillis());
        if (!"snapshot".equals(binding.event())) {
            return;
        }
        markSelectionReady(decided);
    }

    private final Object readyLock = new Object();

    private void markSelectionReady(ActiveSelection selection) {
        if (selection == null) {
            return;
        }
        String key = selectionKey(selection);
        // Atomic readiness commit. Under readyLock we (1) re-validate against the LIVE active selection so a
        // roll since the caller's decision can never announce or converge a superseded selection, and (2)
        // enforce the one-shot per selection key. The source-ready broadcast + cached convergence re-push
        // happen inside the lock so no concurrent roll can interleave a stale announcement. We deliberately
        // do NOT touch sourceLastForwardedAt here — the cache-arrival caller did not forward, and claiming a
        // recent forward would wrongly suppress source-stale; real-forward bookkeeping lives in
        // recordSelectedForward.
        synchronized (readyLock) {
            if (!key.equals(selectionKey(activeSelection.get()))) {
                return;
            }
            if (key.equals(readySelectionKey.get())) {
                return;
            }
            // FAIL CLOSED at the single choke point. applySelection performs the same check, but it is not
            // the only caller: either cache consumer reaching caught-up also lands here, and consumer A can
            // be complete while consumer B still has partitions of this source replaying. Announcing
            // source-ready then would certify a cache that is knowably incomplete -- the original incident's
            // symptom via a different route. Convergence is retried from the poll loop, so this defers
            // readiness, never drops it.
            if (hasIncompleteBootstrapForSource(selection.source())) {
                return;
            }
            readySelectionKey.set(key);
            // Announce readiness FIRST, then converge every open dashboard onto the new selection's cached
            // strikes. Without this re-push, a tab that missed the live seed batch right after
            // "source-switching" — e.g. the daily post-close expiry roll, when no further live ticks arrive —
            // would stay blank until a manual refresh or the next market open.
            broadcast("source-ready", activeSelectionJson(selection, "source-ready"));
            broadcastCachedState(sourceSwitchReplayEvents());
            replaySpotBandBatchAfterSourceSwitch();
        }
    }

    private void reportSourceStale(ActiveSelection selection, String reason) {
        staleDroppedEvents.incrementAndGet();
        sourceStaleEvents.incrementAndGet();
        long nowMs = System.currentTimeMillis();
        if (hasRecentSelectedForward(selection, nowMs)) {
            return;
        }
        long previousMs = lastSourceStaleBroadcastMs.get();
        if (nowMs - previousMs >= 5_000L && lastSourceStaleBroadcastMs.compareAndSet(previousMs, nowMs)) {
            broadcast("source-stale", activeSelectionJson(selection, "source-stale:" + reason));
        }
    }

    private boolean hasRecentSelectedForward(ActiveSelection selection, long nowMs) {
        Long lastForwardedAtMs = sourceLastForwardedAt.get(selectionKey(selection));
        if (lastForwardedAtMs == null || lastForwardedAtMs <= 0L) {
            return false;
        }
        long maxStaleMs = settings.maxStaleMs();
        return maxStaleMs <= 0L || nowMs - lastForwardedAtMs <= maxStaleMs;
    }

    private String selectionKey(ActiveSelection selection) {
        return selection.source() + "|" + selection.symbol() + "|" + selection.expiry() + "|" + selection.selectionEpoch();
    }

    /**
     * Rollover-diagnostics (Codex round-2 P2a): true only when {@code readySelectionKey} matches the CURRENT
     * active selection's key. {@link #applySelection} intentionally does NOT reset {@code readySelectionKey}
     * on rollover, so a bare non-empty check would keep the gauge at 1 using the PREVIOUS selection's key —
     * hiding the exact "new selection has not yet emitted source-ready" wedge the gauge is meant to expose.
     */
    private boolean readySelectionKeyMatchesActive(ActiveSelection selection) {
        if (selection == null) {
            return false;
        }
        String ready = readySelectionKey.get();
        if (ready == null || ready.isEmpty()) {
            return false;
        }
        return ready.equals(selectionKey(selection));
    }

    private boolean matchesActiveSelection(String json, ActiveSelection selection) {
        return matchesSelection(json, selection, true);
    }

    /**
     * Codex round-4 P3: bucket a dropped record into the right diagnostic counter.
     *
     * <p>Semantics:
     * <ul>
     *   <li>{@code cacheCaughtUp == false} → {@code droppedByCacheGate}</li>
     *   <li>caught up AND source matches AND symbol/expiry matches → {@code droppedByStaleness}
     *       (fresh-selection record dropped by the staleness / selection-barrier gate)</li>
     *   <li>otherwise → {@code droppedByOtherReasons} (wrong source; or source match but wrong
     *       symbol/expiry — the peer is producing the wrong contract, which is a real wedge shape
     *       and must NOT be lumped in with normal staleness noise).</li>
     * </ul>
     */
    void recordDropBucket(TopicBinding binding, String json, boolean cacheCaughtUp, ActiveSelection selection) {
        if (!cacheCaughtUp) {
            droppedByCacheGate.incrementAndGet();
            return;
        }
        if (selection != null
                && binding != null
                && binding.source().equals(selection.source())
                && matchesActiveSelection(json, selection)) {
            droppedByStaleness.incrementAndGet();
        } else {
            droppedByOtherReasons.incrementAndGet();
        }
    }

    /**
     * Evict a unified S/R level on a compacted-topic tombstone (null value). The consumer skips null
     * json before {@link #updateCache}, so retraction is handled here: drop the cache entry so new
     * sessions / cached replay no longer see the dropped level. Live sessions converge within the
     * cache TTL (the level is no longer re-emitted). Only acts for the {@code strike-sr} event.
     */
    private void evictStrikeSrTombstone(TopicBinding binding, ConsumerRecord<String, Object> record) {
        if (binding == null || !"strike-sr".equals(binding.event()) || record.value() != null) {
            return;
        }
        if (record.key() == null || record.key().isBlank()) {
            return;
        }
        String key = binding.source() + "|" + record.key();
        if (strikeSr.remove(key) != null) {
            removeCacheEntry("strike-sr:" + key);
        }
    }

    /**
     * Withdraw a projected ES strike-intel signal on the align service's tombstone (null value). The cache key
     * is {@code source|<Kafka key>} — the SAME key the live upsert stored (es-strike-intel adds no cache-key
     * derivation, so the map key is exactly {@code source|record.key()}), so the removal targets the right
     * entry.
     *
     * <p><b>Synchronized on the same monitor as {@link #updateCache}</b> so a tombstone can never interleave
     * with an upsert of the same key, even across the cache + live consumers that both bind this topic.
     * Roll-forward-aware: the tombstone's Kafka producer time is written as a WATERMARK into
     * {@code cacheEventTimes}, so a racing OLDER upsert (the very record being withdrawn, arriving late from
     * the other consumer) is rejected by updateCache's {@code previousEventTime > eventTime} guard and cannot
     * resurrect the signal; a genuinely NEWER re-projection (later time) still re-adds it. An out-of-order
     * OLD tombstone is itself ignored so it cannot evict a newer live upsert. It also clears any queued
     * {@code pendingEsStrikeIntel} upsert so the next UI batch can't emit a ghost after withdrawal.
     */
    private synchronized void evictEsStrikeIntelTombstone(TopicBinding binding, ConsumerRecord<String, Object> record) {
        if (binding == null || !"es-strike-intel".equals(binding.event()) || record.value() != null) {
            return;
        }
        if (record.key() == null || record.key().isBlank()) {
            return;
        }
        String key = binding.source() + "|" + record.key();
        String versionKey = "es-strike-intel:" + key;
        long tombstoneTime = cacheTimestamp(record); // Kafka producer time — same clock the upserts order by
        Long previous = cacheEventTimes.get(versionKey);
        if (previous != null && previous > tombstoneTime) {
            return; // out-of-order stale tombstone — never evict a newer upsert
        }
        esStrikeIntel.remove(key);
        // Watermark = tombstoneTime + 1 so the tombstone wins an EQUAL-millisecond tie: the withdrawn upsert
        // (same or older producer time) is rejected by updateCache's `previous > eventTime` guard, while a
        // genuinely later re-projection (time > tombstoneTime) still re-adds. The tombstone is always the
        // higher Kafka offset than the upsert it withdraws, so breaking the tie in its favour is correct.
        cacheEventTimes.put(versionKey, tombstoneTime + 1);
        cachePositions.remove(versionKey);
        synchronized (batchLock) {
            pendingEsStrikeIntel.remove(key); // cancel a queued upsert so no post-withdrawal ghost broadcasts
        }
    }

    /**
     * Legacy-batch enqueue for es-strike-intel that is ATOMIC with {@link #evictEsStrikeIntelTombstone}. Both
     * take {@code this} then {@code batchLock} (same order — no inversion), so a tombstone that fires between
     * this record's {@link #updateCache} and its enqueue cannot leave a post-withdrawal ghost: the enqueue
     * only fires while the entry is still cached, and if the tombstone wins it removes both the cache entry
     * and any pending row.
     */
    private synchronized void enqueueEsStrikeIntelPendingIfLive(String cacheKey, String json) {
        if (cacheKey == null || !esStrikeIntel.containsKey(cacheKey)) {
            return; // withdrawn before we could enqueue — never resurrect it into the batch
        }
        enqueuePending("es-strike-intel", cacheKey, json);
    }

    private boolean matchesCachedSelection(String json, ActiveSelection selection) {
        return matchesSelection(json, selection, true);
    }

    private boolean matchesCachedSelection(String json, ActiveSelection selection, boolean enforceSelectionEpoch) {
        return matchesSelection(json, selection, enforceSelectionEpoch);
    }

    private boolean matchesSelection(String json, ActiveSelection selection, boolean enforceSelectionEpoch) {
        if (json == null || json.isBlank() || selection == null) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(json);
            return matchesSelectionNode(root, selection.source(), selection.symbol(), selection.expiry(),
                    selection.selectionEpoch(), enforceSelectionEpoch);
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    static boolean matchesSelectionNode(
            JsonNode root,
            String selectedSource,
            String selectedSymbol,
            String selectedExpiry,
            long selectionEpoch,
            boolean enforceSelectionEpoch
    ) {
        String source = GatewaySettings.normalizeSource(text(root, "marketDataSource"));
        if (source.isBlank() && "UNUSUAL_WHALES".equalsIgnoreCase(text(root, "source"))) {
            source = "IBKR";
        }
        if (!source.isBlank() && !selectedSource.equals(source)) {
            return false;
        }
        long recordEpoch = longField(root, "selectionEpoch", 0L);
        if (enforceSelectionEpoch
                && recordEpoch > 0L
                && selectionEpoch > 0L
                && recordEpoch < selectionEpoch) {
            return false;
        }
        return selectedSymbol.equalsIgnoreCase(text(root, "symbol"))
                && selectedExpiry.equals(normalizeExpiry(text(root, "expiry")));
    }

    private String enrichJson(String json, TopicBinding binding) {
        if (json == null || json.isBlank() || binding == null) {
            return json;
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!(root instanceof ObjectNode object)) {
                return json;
            }
            object.put("marketDataSource", binding.source());
            if (!object.hasNonNull("source")) {
                object.put("source", binding.source());
            }
            ActiveSelection selection = activeSelection.get();
            if (!object.hasNonNull("sessionDate")) {
                object.put("sessionDate", selection.expiry());
            }
            // strike-invasion is SPX-only 0DTE and its StrikeInvasionSnapshot contract carries NO expiry
            // (record identity is symbol|strike). But contract routing — RoutingKeyDeriver.derive,
            // matchesSelectionNode, and the cached-replay selection barrier — all match on symbol|expiry
            // and REJECT a blank-expiry contract record. Stamp the canonical current 0DTE expiry so
            // strike-invasion routes and replays like its strike-intel sibling instead of being dropped.
            //
            // Source the expiry from the market-calendar trading date (currentInvasionExpiry) — exactly the
            // 0DTE chain the strike-invasion producer emits for and the date the Databento feed self-rolls
            // to — NOT the per-session/global activeSelection or a pinned IB_EXPIRY. A session viewing
            // today's 0DTE receives it; a session that manually selected a non-0DTE chain correctly does
            // not (a 0DTE-only signal must not attach to a later expiry). The cache key stays
            // symbol|strike|direction (updateCache's strikeInvasionCacheKey ignores expiry), so
            // last-value-wins per strike+direction holds.
            // NOTE: the replay path re-stamps this with the replay window's expiry (see emitReplayRecord),
            // since a historical record belongs to that session's chain, not today's.
            if ("strike-invasion".equals(binding.event()) && text(object, "expiry").isBlank()) {
                String expiry = currentInvasionExpiry();
                if (expiry == null || expiry.isBlank()) {
                    expiry = selection.expiry(); // last-resort fallback so a routable record is never dropped
                }
                if (!expiry.isBlank()) {
                    object.put("expiry", expiry);
                }
            }
            if ("spot-vol-regime".equals(binding.event())) {
                sanitizeStrikeBand(object);
            }
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException ignored) {
            return json;
        }
    }

    /**
     * Fail-closed sanitation of the {@code strikeBand} block carried on a spot-vol-regime snapshot.
     *
     * <p>The band is the LATCHED strike marking (USER 2026-08-02): when the regime becomes
     * DIVERGENT_UP or COMPLACENT_DOWN the spot at that moment sets an ANCHOR strike, and every strike
     * from the anchor through the strikes the spot subsequently traverses carries the regime colour on
     * the option chain. The producer owns the history and publishes the RESOLVED per-strike latch
     * state; the gateway's job is only to guarantee that whatever reaches a browser is well formed and
     * belongs to the session the snapshot was computed in.
     *
     * <p>Runs INSIDE {@link #enrichJson}'s existing parse (no second {@code readTree}) and mutates in
     * place, so the sanitised band is what gets cached, live-broadcast AND late-join replayed — one
     * enforcement point for all three paths, not three.
     *
     * <p>Every rejection REMOVES the whole band and leaves the rest of the snapshot untouched: a
     * malformed band must never take the regime pill down with it, and a half-valid band must never
     * paint a partial marking that the user would read as "the spot stopped here". A removed band is
     * indistinguishable from no band at all, which the UI renders as no coloured strikes.
     *
     * <p>The session rule is measured against the snapshot's own {@code asOfEventTimeMs}, NOT the
     * gateway wall clock — the same stream-time discipline the producer uses. Live, that is "now", so a
     * latch carried across a session boundary is stripped; under replay, that is the historical instant,
     * so a replayed session keeps its own band instead of being blanked by today's date.
     *
     * <p>THE LATCH IS SESSION-SCOPED (Codex requirements consult 2026-08-02). "Latched" means the
     * colour survives the end of the suspect MOVE, not that it becomes a permanent annotation: a
     * trader who sees a coloured 7410 at 09:31 on Tuesday reads it as today's traversal, so carrying
     * Monday's range forward would be silently false. That is why the band must both belong to the ET
     * session its snapshot was computed in AND have been computed inside that session's regular
     * trading hours (early closes included, via {@link GatewayMarketCalendar}).
     *
     * @return the rejection reason, or {@code null} when the band was left intact (or was absent).
     */
    private String sanitizeStrikeBand(ObjectNode snapshot) {
        JsonNode bandNode = snapshot.get("strikeBand");
        if (bandNode == null || bandNode.isNull()) {
            return null; // no band is a valid state — the common one before the first suspect regime
        }
        if (!(bandNode instanceof ObjectNode band)) {
            return rejectStrikeBand(snapshot, "NOT_AN_OBJECT");
        }
        if (band.path("schemaVersion").asInt(-1) != STRIKE_BAND_SCHEMA_VERSION) {
            // Unknown shape: refuse rather than guess at its meaning. The band evolves independently of
            // the enclosing snapshot's schemaVersion, so a band-only change never blinds the pill.
            return rejectStrikeBand(snapshot, "UNSUPPORTED_SCHEMA");
        }
        long asOfEventTimeMs = longField(snapshot, "asOfEventTimeMs", -1L);
        if (asOfEventTimeMs <= 0L) {
            return rejectStrikeBand(snapshot, "NO_EVENT_TIME"); // the session rule cannot be applied
        }
        JsonNode marks = band.get("marks");
        if (marks == null || !marks.isArray() || marks.isEmpty()) {
            return rejectStrikeBand(snapshot, "NO_MARKS");
        }
        if (marks.size() > STRIKE_BAND_MAX_MARKS) {
            // Deliberately NOT truncated: a clipped band would look like a complete one and understate
            // how far the spot actually travelled. Refusing it is the honest failure.
            return rejectStrikeBand(snapshot, "TOO_MANY_MARKS");
        }
        Set<Double> seen = new HashSet<>();
        for (JsonNode mark : marks) {
            if (mark == null || !mark.isObject()) {
                return rejectStrikeBand(snapshot, "MALFORMED_MARK");
            }
            JsonNode strike = mark.get("strike");
            if (strike == null || !strike.isNumber() || !Double.isFinite(strike.asDouble())) {
                return rejectStrikeBand(snapshot, "MALFORMED_MARK");
            }
            if (!seen.add(strike.asDouble())) {
                // marks is the COMPLETE resolved state, one entry per strike. A duplicate means the
                // producer failed to resolve overlapping episodes; "last element wins" would silently
                // invent a resolution the producer never made.
                return rejectStrikeBand(snapshot, "DUPLICATE_STRIKE");
            }
            if (!STRIKE_BAND_REGIMES.contains(text(mark, "regime"))) {
                return rejectStrikeBand(snapshot, "UNSUPPORTED_REGIME");
            }
            long markedAt = longField(mark, "markedAtEventTimeMs", -1L);
            if (markedAt <= 0L || markedAt > asOfEventTimeMs) {
                // A mark cannot have been made after the frame that reports it.
                return rejectStrikeBand(snapshot, "MARK_TIME_OUT_OF_RANGE");
            }
        }
        Instant asOf = Instant.ofEpochMilli(asOfEventTimeMs);
        if (!marketCalendar.isRegularTradingHours(asOf)) {
            return rejectStrikeBand(snapshot, "OUTSIDE_RTH");
        }
        String session = marketCalendar.currentTradingDate(asOf).format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (!session.equals(text(band, "sessionDate"))) {
            return rejectStrikeBand(snapshot, "WRONG_SESSION");
        }
        return null;
    }

    /** Drop the band, count it, and name the reason once (never per client). */
    private String rejectStrikeBand(ObjectNode snapshot, String reason) {
        snapshot.remove("strikeBand");
        strikeBandsRejected.incrementAndGet();
        if (strikeBandRejectionLogged.compareAndSet(false, true)) {
            System.err.println("WARN: spot-vol-regime strikeBand rejected (" + reason
                    + ") — the regime pill still forwards; only the strike marking is suppressed."
                    + " Further rejections are counted, not logged.");
        }
        return reason;
    }

    /**
     * Send-time half of the session rule: the ingest checks above prove the band BELONGED to a live RTH
     * session when it was computed, but a snapshot minted at 15:59 stays inside the 5-minute
     * spot-vol-regime cache TTL until 16:04. Without this, a browser opened at 16:01 would late-join
     * into a coloured chain after the session it describes has closed. Only the band is stripped — the
     * late joiner still gets the regime snapshot itself for the rest of its TTL.
     */
    private String suppressStrikeBandAfterClose(String json, long nowMs) {
        if (json == null || json.isBlank() || isRegularTradingHours(nowMs) || !json.contains("strikeBand")) {
            return json;
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!(root instanceof ObjectNode object) || !object.has("strikeBand")) {
                return json;
            }
            object.remove("strikeBand");
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException ignored) {
            return json; // unparseable payloads are already refused downstream by the freshness gate
        }
    }

    private synchronized String updateCache(TopicBinding binding, ConsumerRecord<String, ?> record, String json) {
        // Set by the offset gate when a record shows the recreated-topic shape, and ACTED ON only
        // where the record is genuinely admitted — see both sites. Declared here because those two
        // places are in different scopes and the fact has to travel between them.
        boolean volPremiumRecreatedTopic = false;
        String event = binding.event();
        String key = record.key() == null || record.key().isBlank()
                ? record.topic() + ":" + record.partition()
                : record.key();
        if ("hot-strike".equals(event)) {
            // Restart bootstrap path (cache consumer): same §4.4 VERBATIM contract as the
            // live branch — store the RAW record value, keyed by symbol, last-value-wins.
            Object rawValue = record.value();
            String hotRaw = rawValue == null ? null : String.valueOf(rawValue);
            if (hotRaw != null && !hotRaw.isBlank()) {
                cacheHotStrike(key, hotRaw,
                        record.timestamp() > 0 ? record.timestamp()
                                : System.currentTimeMillis());
            }
            return key;
        }
        if ("pace".equals(event)) {
            key = paceCacheKey(json, key);
        } else if ("directional-pressure".equals(event)) {
            key = directionalPressureCacheKey(json, key);
        } else if ("vix-price".equals(event) || "index-price".equals(event) || "spx-price".equals(event)) {
            key = indexPriceCacheKey(json, key);
        } else if ("strike-flow".equals(event)) {
            key = strikeFlowCacheKey(json, key);
        } else if ("spot-band".equals(event)) {
            // PER-STRIKE, so the per-strike keyer — the same one seller-activity uses. strikeFlowCacheKey
            // is symbol|expiry because a strike-flow record is CHAIN-WIDE (one record carrying strikes[]);
            // a band record is one strike. Keying these board-level collapsed all ~200 into a single
            // entry and the cache held exactly 1, which the spot_bands metric showed immediately.
            key = deltaFlowCacheKey(json, key);
        } else if ("seller-activity".equals(event)) {
            key = deltaFlowCacheKey(json, key);
        } else if ("delta-flow".equals(event)) {
            key = deltaFlowCacheKey(json, key);
        } else if ("strike-intel".equals(event)) {
            key = strikeIntelCacheKey(json, key);
        } else if ("option-truth".equals(event)) {
            key = optionTruthCacheKey(json, key);
        } else if ("strike-invasion".equals(event)) {
            key = strikeInvasionCacheKey(json, key);
        } else if ("es-open-direction-forecast".equals(event)) {
            key = esOpenDirectionForecastCacheKey(json, key);
        } else if ("es-open-direction-outcome".equals(event)) {
            key = esOpenDirectionOutcomeCacheKey(json, key);
        } else if ("es-open-direction-status".equals(event)) {
            key = esOpenDirectionStatusCacheKey(json, key);
        } else if ("greek-move-auth".equals(event)) {
            key = greekMoveAuthCacheKey(json, key);
        } else if ("spot-vol-regime".equals(event)) {
            key = spotVolRegimeCacheKey(json, key);
        } else if ("vol-premium-ivrv".equals(event)) {
            key = volPremiumIvrvCacheKey(json, key);
        } else if ("indicators".equals(event)) {
            key = indicatorsCacheKey(json, key);
        } else if ("tapeZones".equals(event)) {
            key = tapeZonesCacheKey(json, key);
        } else if ("close-direction".equals(event)) {
            key = closeDirectionCacheKey(json, key);
        } else if ("zero-dte-intelligence".equals(event)) {
            key = zeroDteIntelligenceCacheKey(json, key);
        } else if ("liquidity-heatmap".equals(event)) {
            // Payload-derived symbol|expiry key (mirrors strike-flow): last-value-wins per chain
            // must not depend on the Kafka record key shape.
            key = strikeFlowCacheKey(json, key);
        } else if ("mission-pace".equals(event)) {
            key = missionPaceCacheKey(json, key);
        } else if ("mission-control".equals(event)) {
            key = missionControlCacheKey(json, key);
        } else if ("spread-skew".equals(event)) {
            key = spreadSkewCacheKey(json, key);
        } else if ("gex-by-strike".equals(event)) {
            key = gexCacheKey(json, key);
        } else if ("gex-oi-status".equals(event)) {
            // Same symbol|expiry|strike identity as gex rows -> same per-strike key derivation.
            key = gexCacheKey(json, key);
        } else if ("es-gex".equals(event)) {
            key = esGexCacheKey(json, key);
        } else if ("gex-strike-lifecycle".equals(event)) {
            key = strikeLifecycleCacheKey(json, key);
        } else if ("max-pain".equals(event)) {
            key = maxPainCacheKey(json, key);
        } else if ("option-price-behavior".equals(event)) {
            key = optionPriceBehaviorCacheKey(json, key);
        } else if ("dealer-ledger".equals(event)) {
            // Role-qualified (…|PROFILE / …|STATE) so the two source topics keep SEPARATE monotonic
            // event-time gates — otherwise a profile could be false-dropped by a newer state (or vice
            // versa). The join + envelope cache use the role-stripped base key (dealerLedgerBaseKey).
            key = dealerLedgerCacheKey(json, record, key);
        } else if ("opb-by-option".equals(event)) {
            key = opbByOptionCacheKey(json, key);
        } else if ("opb-session".equals(event)) {
            key = opbSessionCacheKey(json, key);
        }
        // A per-event cache-key deriver may FAIL CLOSED by returning null (currently gex-strike-lifecycle:
        // a badge without its own symbol|expiry|strike identity is unusable). Stop here — never build a
        // "event:null" version key, never cache, never replay, never forward. The caller treats a null
        // return as "dropped" (see strikeLifecycleDropped in the live-forward gate).
        if (key == null) {
            return null;
        }
        if (!"pace".equals(event) && !"pace-rank".equals(event)) {
            // pace-rank's record key is already the epoch-qualified boardKey (includes source) — don't re-prefix.
            key = binding.source() + "|" + key;
        }
        String versionKey = event + ":" + key;
        if ("ibkr-preopen-status".equals(event) || "indicators".equals(event)
                || "tapeZones".equals(event) || "vol-premium-ivrv".equals(event)) {
            // OFFSET-ordered last-value-wins (rev13 R-WIRE.2/.5; indicators rev 14
            // §6.9 r1 finding 2): these topics are single-partition per symbol and
            // strictly ordered by offset — Kafka timestamps may tie or regress
            // across legitimate later offsets (and the cache + live consumers read
            // the same partition). Accept ONLY a strictly higher offset on the same
            // partition; a DIFFERENT partition for the same key is fail-closed
            // (reject, never reorder) — each indicator symbol lives on exactly one
            // topic/partition (§7.3).
            //
            // vol-premium-ivrv belongs to exactly this class, and needs it for a reason the event
            // time cannot cover: the contract permits an equal-event-time CORRECTION at a later
            // offset (Kafka is last-write-wins), and the generic event-time gate accepts equal
            // timestamps. Without the offset gate the cache consumer could take offset N+1 and the
            // live consumer then take offset N, whose equal timestamp passes — broadcasting the
            // value the correction had already superseded. The topic is single-partition by
            // construction: the producer creates it with one partition and refuses to boot on any
            // other count.
            RecordPosition incoming = recordPosition(record);
            RecordPosition previousPosition = cachePositions.get(versionKey);
            // A TOPIC RECREATION IS NOT A REGRESSION, and the gate cannot tell them apart from the
            // offset alone.
            //
            // A deleted-and-remade topic — an operator remaking it, the daily reset — starts again
            // at offset zero while a perfectly fresh cache entry still holds the old incarnation's
            // position. Every frame of the SAME session would then be refused until the offset
            // climbed back past it, which for a mid-session recreation is the rest of the day. The
            // cache freezes, live delivery stops, and nothing anywhere reports a fault.
            //
            // The EVENT TIME is what distinguishes the two. This producer stamps each reading with
            // its own stream time, which never runs backwards, so a record that is BOTH behind the
            // stored offset and strictly ahead of the stored event time cannot be a replay of
            // something already seen — no incarnation of this topic can produce it. A recreation
            // can, and does, on its very first record.
            //
            // So that shape RESETS the entry rather than being dropped: it is counted, and the
            // fence goes with it, because a stale fence would suppress the same frames the
            // position gate just stopped suppressing.
            if (previousPosition != null && "vol-premium-ivrv".equals(event)) {
                Long storedEventTime = cacheEventTimes.get(versionKey);
                long incomingEventTime = eventCacheTimestamp(event, record, json);
                // AT OR BELOW the stored offset, not strictly below. If the old incarnation had
                // reached only offset 0 — a topic recreated moments after its first record, or one
                // whose only frame was the session's first — the new incarnation's first record is
                // offset 0 as well, and a strict comparison would drop the very frame this
                // recovery exists to admit. Equal offset with a strictly newer event time is just
                // as impossible within one incarnation as a lower one: an offset identifies a
                // record, and this producer's event times never run backwards.
                volPremiumRecreatedTopic = incoming.offset() <= previousPosition.offset()
                        && previousPosition.partition().equals(incoming.partition())
                        && storedEventTime != null && incomingEventTime > storedEventTime;
                // NOTHING IS RECORDED HERE. Recognising the shape is not the recovery — the record
                // still has the staleness gate ahead of it, and a recreated topic whose first
                // record arrives past its TTL is refused. Counting and clearing the fence at this
                // point would report a recovery that did not happen and would drop a fence for a
                // record that never got cached or broadcast. Both happen where the record is
                // actually admitted, below.
            }
            if (!volPremiumRecreatedTopic && previousPosition != null
                    && (!previousPosition.partition().equals(incoming.partition())
                        || incoming.offset() <= previousPosition.offset())) {
                return null;
            }
        }
        long eventTime = eventCacheTimestamp(event, record, json);
        Long previousEventTime = cacheEventTimes.get(versionKey);
        // Terminal max-pain MUST always reach the EXPIRED branch (eviction + return key for the
        // one-time live forward) regardless of timestamp ordering. Without this, an EXPIRED record
        // with an older Kafka timestamp would be silently dropped before the UI sees the transition.
        boolean isTerminalMaxPainShortCircuitBypass = "max-pain".equals(event) && isMaxPainExpired(json);
        if (!isTerminalMaxPainShortCircuitBypass
                && !"ibkr-preopen-status".equals(event)
                && !"indicators".equals(event)
                && !"tapeZones".equals(event)
                // vol-premium-ivrv is DELIBERATELY still timestamp-gated, unlike the three above.
                //
                // Its event time cannot legitimately regress: the producer stamps each reading
                // with its own Kafka Streams stream time, which never runs backwards, so a later
                // record always carries an equal or greater event time. Equal is the correction
                // case and passes this gate; strictly EARLIER is a corrupt or foreign record, and
                // dropping it here is the same rule the browser applies to its own series — which
                // is the point. Excluding it here instead would leave the gateway accepting a
                // regression that every browser then discards, so a live client and a late joiner
                // would diverge with nothing failing anywhere. The offset gate above still does
                // the work the timestamp gate cannot: it decides which CONSUMER broadcasts, and
                // orders the equal-time corrections that this gate lets through.
                && previousEventTime != null && previousEventTime > eventTime) {
            return null;   // (offset-ordered streams above are never timestamp-gated —
                           // a publishedAt wall-clock regression must not outrank a
                           // higher offset + higher revision, r1 finding 2)
        }
        if ("gex-by-strike".equals(event)
                && previousEventTime != null
                && previousEventTime == eventTime
                && hasGexHistory(gexByStrike.get(key))
                && !hasGexHistory(json)) {
            return null;
        }
        // Terminal max-pain bypasses the generic stale-eviction so the EXPIRED transition still forwards
        // ONCE even when the Kafka record timestamp is older than the freshness window. The downstream
        // case "max-pain" branch handles eviction + cache prune itself; we just must not short-circuit
        // to null here on the staleness check (which would swallow the terminal entirely).
        boolean isTerminalMaxPain = "max-pain".equals(event) && isMaxPainExpired(json);
        if (!isTerminalMaxPain && isExpired(event, eventTime, System.currentTimeMillis())) {
            removeCacheEntry(versionKey);
            return null;
        }
        switch (event) {
            case "snapshot" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                snapshots.put(key, json);
                return key;
            }
            case "pace" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                paces.put(key, json);
                return key;
            }
            case "pace-rank" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                paceRanks.put(key, json); // one compact board record per boardKey (latest-wins)
                return key;
            }
            case "directional-pressure" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                directionalPressures.put(key, json);
                return key;
            }
            case "vix-price" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                vixPrices.put(key, json); // SHARED last-known VIX, kept distinct from ES/index
                return key;
            }
            case "index-price" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                indexPrices.put(key, json);
                return key;
            }
            case "spx-price" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                spxPrices.put(key, json); // canonical SPX spot, kept distinct from ES/index (vix idiom)
                return key;
            }
            case "strike-flow" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                strikeFlows.put(key, json);
                return key;
            }
            case "spot-band" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                spotBands.put(key, json);
                return key;
            }
            case "seller-activity" -> {
                sellerActivityStore.put(key, json, eventTime);
                return key;
            }
            case "delta-flow" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                deltaFlows.put(key, json);
                return key;
            }
            case "strike-intel" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                strikeIntels.put(key, json);
                return key;
            }
            case "option-truth" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                optionTruths.put(key, json);
                return key;
            }
            case "strike-invasion" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                strikeInvasions.put(key, json);
                return key;
            }
            case "es-open-direction-forecast" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                esOpenDirectionForecasts.put(key, json);
                return key;
            }
            case "es-open-direction-outcome" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                esOpenDirectionOutcomes.put(key, json);
                return key;
            }
            case "es-open-direction-status" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                esOpenDirectionStatuses.put(key, json); // ONE current status per source|tradeDate — last heartbeat wins
                return key;
            }
            case "greek-move-auth" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                greekMoveAuthCurrent.put(key, json); // ONE current verdict per symbol — last-value-wins
                return key;
            }
            case "vol-premium-ivrv" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                volPremiumIvrv.put(key, json); // ONE current reading per source|SYMBOL|sessionDate
                if (volPremiumRecreatedTopic) {
                    // HERE, where the record is genuinely admitted, so the counter means what its
                    // HELP text says — a recovery that happened — and the fence is dropped only
                    // for a reading that will actually be cached and offered for broadcast.
                    VOL_PREMIUM_TOPIC_RESETS.incrementAndGet();
                    volPremiumIvrvBroadcastOffset.remove(key);
                }
                return key;
            }
            case "spot-vol-regime" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                spotVolRegime.put(key, json); // ONE current regime per symbol — last heartbeat wins
                return key;
            }
            case "indicators" -> {
                // (runId, revision) supersession (rev 14 §6.9): accept a NEW runId in
                // arrival order and retire the prior; require strictly increasing
                // revision within the active run; reject retired-run returns.
                if (!indicatorsSupersedes(key, json)) {
                    return null; // regression/retired-run — never cached or broadcast
                }
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                indicatorsCurrent.put(key, json);
                return key;
            }
            case "close-direction" -> {
                // key here is already source-prefixed: SOURCE|V|sessionDate (verdict) or
                // SOURCE|I|sessionDate (interim) — the helper returned V|/I| + sessionDate and
                // updateCache prepended the binding source. Malformed payloads never reach this
                // case (helper returns null → dropped, design CD-R30). Verdict-over-interim
                // precedence: once the session's verdict is cached, later interims are dead.
                int marker = key.indexOf("|I|");
                if (marker >= 0) {
                    // CD-R30 short-class INGESTION freshness for interims — BOTH clocks:
                    // the Kafka record timestamp (delayed/backfilled delivery) AND the
                    // payload asOfMs (fresh redelivery of an old evaluation). Either stale
                    // ⇒ never cached, never live-broadcast; the long 12h policy governs
                    // only the VERDICT + seek-back. Returning null suppresses both paths.
                    long nowIngestMs = System.currentTimeMillis();
                    if (nowIngestMs - eventTime > settings.closeDirectionInterimFreshMs()) {
                        return null;
                    }
                    try {
                        long asOfMs = mapper.readTree(json).path("asOfMs").asLong(0);
                        if (asOfMs <= 0 || nowIngestMs - asOfMs
                                > settings.closeDirectionInterimFreshMs()) {
                            return null;
                        }
                    } catch (JsonProcessingException e) {
                        return null;
                    }
                    String verdictKey = key.substring(0, marker) + "|V|"
                            + key.substring(marker + 3);
                    if (closeDirectionVerdicts.containsKey(verdictKey)) {
                        return null;   // session already decided — interim ignored
                    }
                    cacheEventTimes.put(versionKey, eventTime);
                    cachePositions.put(versionKey, recordPosition(record));
                    closeDirectionInterims.put(key, json);   // last interim wins
                    return key;
                }
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                closeDirectionVerdicts.put(key, json);
                return key;
            }
            case "zero-dte-intelligence" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                zeroDteIntelligence.put(key, json);
                return key;
            }
            case "mission-pace" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                missionPaces.put(key, json);
                return key;
            }
            case "mission-control" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                missionControls.put(key, json);
                return key;
            }
            case "spread-skew" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                spreadSkews.put(key, json); // SINGLE value per source|underlying — last snapshot wins
                return key;
            }
            case "volume-sandwich" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                currentStates.put(versionKey, json);
                return versionKey;
            }
            case "mission-sandwich" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                currentStates.put(versionKey, json);
                return versionKey;
            }
            case "gex-by-strike" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                gexByStrike.put(key, json);
                return key;
            }
            case "gex-oi-status" -> {
                // Fail-closed semantics guard (Codex round-4 P1): only the two known status values may
                // enter the replay cache. A malformed/schema-drifted record must not displace a valid
                // cached OI_MISSING warning — a reconnecting client would replay only the malformed value
                // and silently lose the badge.
                if (!isKnownOiStatus(json)) {
                    return null;
                }
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                gexOiStatus.put(key, json);
                return key;
            }
            case "ibkr-preopen-status" -> {
                // Last-value-wins per Kafka record key: strike rows AND "__" control keys both
                // cache. The record KEY carries the identity (rev13 wire contract: values omit
                // it), so the cached/broadcast payload is the WRAPPED form
                // {"recordKey":…,"status":…} — a browser can tell "SPX|D|6300" from "__path|D".
                // The RAW Kafka key is wrapped, NEVER the source-prefixed cache key ("IBKR|…"),
                // and the RAW producer value rides byte-untouched (enrichJson is bypassed for
                // this event at ingest).
                String rawKey = String.valueOf(record.key());
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                ibkrPreOpenStatus.put(key,
                        wrapIbkrPreOpenStatus(rawKey, record.offset(), record.timestamp(), json));
                if (rawKey.startsWith("__revocation|")) {
                    // R-ROLL: a revocation control kills the named output generation on the value
                    // plane synchronously (gateway evict ≤30 s) — candidates AND frozen projections.
                    // Runs on the winning (offset-ordered) delivery only; application is idempotent.
                    applyIbkrPreOpenRevocation(rawKey);
                } else if (rawKey.startsWith("__generation|")) {
                    // R-WIRE.2: the __generation control is the AUTHORITATIVE "max observed output
                    // generation" signal (round-2 finding 3) — it must reject delayed
                    // lower-generation values even before any value of the new generation arrives.
                    applyIbkrPreOpenGenerationControl(rawKey, record.timestamp());
                }
                // Reconstruction is order-INDEPENDENT (round-1 finding 1): a status arriving after
                // its value completes the pending pair right here, not at some later poll.
                reevaluateIbkrPreOpenPendingProjections(System.currentTimeMillis());
                return key;
            }
            case "tapeZones" -> {
                // Last-value-wins per sessionDate on the compacted 1-partition board topic. The
                // RAW producer value is cached (enrichJson bypassed at ingest); the wrapper is
                // built at EMIT so replay carries the true age rather than a frozen stamp.
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                tapeZonesBoards.put(key, json);
                tapeZonesPositions.put(key, new long[]{record.offset(), eventTime});
                return key;
            }
            case "liquidity-heatmap" -> {
                // Latest column frame per symbol|expiry, last-value-wins; short TTL keeps a dead
                // producer from replaying minutes-old liquidity as live on connect.
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                liquidityHeatmaps.put(key, json);
                return key;
            }
            case "strike-sr" -> {
                // Per-bucket upsert. Compacted-topic tombstones (null value) never reach here (the
                // consumer skips null json); they are evicted by evictStrikeSrTombstone() instead.
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                strikeSr.put(key, json);
                return key;
            }
            case "gex-magnet" -> {
                // Per-chain (symbol|expiry) last-value-wins upsert. No tombstones.
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                gexMagnet.put(key, json);
                return key;
            }
            case "corridor-gauge" -> {
                // Per-chain last-value-wins upsert; the producer never tombstones. The cache key
                // is SOURCE|symbol|expiry (UI-review r2 #2): replayCacheMap derives the routing
                // source from the prefix before the first '|', and the record key alone would
                // hand it "SPX" as a source.
                key = binding.source() + "|" + key;
                versionKey = event + ":" + key;
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                corridorGauges.put(key, json);
                return key;
            }
            case "gamma-migration" -> {
                // Per-chain (symbol|expiry) last-value-wins upsert, same shape as gex-magnet.
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                gammaMigration.put(key, json);
                return key;
            }
            case "gamma-fragility" -> {
                // Same contract as gamma-rotation: one record per chain is the whole panel.
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                gammaFragility.put(key, json);
                return key;
            }
            case "gamma-rotation" -> {
                // Same contract as gamma-migration: one compacted record per chain is the whole
                // current answer, so last-value-wins with no tombstones.
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                gammaRotation.put(key, json);
                return key;
            }
            case "es-gex" -> {
                // ES-on-SPX aligned WHOLE-BOOK per symbol|expiry (roll-forward: latest emitEventTimeMs wins;
                // the align service is the single writer and stamps a monotonically-advancing emitEventTimeMs).
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                esGex.put(key, json);
                return key;
            }
            case "es-strike-intel" -> {
                // ES strike-intel projected onto SPX, per NATIVE ES identity (source|symbol|expiry|esStrike).
                // Last-value-wins upsert; withdrawal arrives as a tombstone (evictEsStrikeIntelTombstone), so
                // there is no eviction branch here. Roll-forward orders by Kafka producer time (eventTime).
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                esStrikeIntel.put(key, json);
                return key;
            }
            case "gex-strike-lifecycle" -> {
                // Per-strike (symbol|expiry|strike) last-value-wins upsert (mirrors gex-by-strike). rev-17:
                // the producer emits only ACTIVE strikes + a one-shot NEUTRAL clear per departure; the UI
                // applies each per-strike record directly (NEUTRAL removes the badge), so the gateway needs no
                // frameId bookkeeping or tombstones — the last value per strike is the whole truth.
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                gexStrikeLifecycle.put(key, json);
                return key;
            }
            case "max-pain" -> {
                // EXPIRED terminal records evict the cache entry instead of caching them — a stale
                // terminal must NEVER be replayed to a freshly-connected client. The live forward of
                // this single EXPIRED record to currently-connected matching clients still happens
                // via the normal forward path; only the cache is pruned.
                if (isMaxPainExpired(json)) {
                    removeCacheEntry(versionKey);
                    maxPain.remove(key);
                    return key;       // still forward this terminal once to live clients
                }
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                maxPain.put(key, json);
                return key;
            }
            case "option-price-behavior" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                optionPriceBehaviors.put(key, json);
                return key;
            }
            case "dealer-ledger" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                // key is source|symbol|expiry|ROLE; cache the raw record by role, then (re)join the two
                // topics into the single envelope the UI consumes, keyed by the role-stripped base key.
                String baseKey = dealerLedgerBaseKey(key);
                if (record.topic().equals(settings.dealerLedgerStateTopic())) {
                    dealerLedgerStates.put(baseKey, json);
                } else {
                    dealerLedgerProfiles.put(baseKey, json);
                }
                String envelope = joinDealerLedger(baseKey, binding.source());
                if (envelope == null) {
                    return null;
                }
                dealerLedgers.put(baseKey, envelope);
                // The forward/replay payload is the JOINED envelope, resolved by base key downstream.
                return baseKey;
            }
            case "opb-by-option" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                opbByOptions.put(key, json);
                return key;
            }
            case "opb-session" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                opbSessions.put(key, json);
                return key;
            }
            default -> {
                return null;
            }
        }
    }

    private synchronized HpsfCacheUpdate updateHpsfCache(ConsumerRecord<String, String> record) {
        String rawJson = record.value();
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            HpsfCacheUpdate update = hpsfCacheUpdate(record, rawJson);
            if (update == null || update.json() == null || update.json().isBlank()) {
                return null;
            }
            String versionKey = update.event() + ":" + update.key();
            long eventTime = cacheTimestamp(record);
            Long previousEventTime = cacheEventTimes.get(versionKey);
            if (previousEventTime != null && previousEventTime > eventTime) {
                return null;
            }
            if (isExpired(eventTime, System.currentTimeMillis())) {
                removeCacheEntry(versionKey);
                return null;
            }
            cacheEventTimes.put(versionKey, eventTime);
            cachePositions.put(versionKey, recordPosition(record));
            putHpsfView(update);
            return update;
        } catch (RuntimeException e) {
            System.err.println("Feed gateway could not map HPSF topic " + record.topic()
                    + " offset=" + record.offset()
                    + ": " + e.getMessage());
            return null;
        }
    }

    private HpsfCacheUpdate hpsfCacheUpdate(ConsumerRecord<String, String> record, String rawJson) {
        String topic = record.topic();
        if (settings.hpsfLatestSignalTopic().equals(topic)) {
            HpsfSignal signal = read(rawJson, HpsfSignal.class);
            String groupKey = hpsfGroupKey(signal.tradeDate(), signal.expiry(), fallbackKey(record));
            hpsfLatestEvaluationIds.put(groupKey, signal.evaluationId());
            String json = write(hpsfViewMapper.latestSignalView(HpsfTopics.HPSF_LATEST_SIGNAL, signal));
            HpsfCacheUpdate update = new HpsfCacheUpdate("hpsf-latest-signal", groupKey, json, signal.expiry());
            HpsfCacheUpdate candidates = topCandidatesUpdate(groupKey, signal.expiry());
            if (candidates != null) {
                cacheEventTimes.put(candidates.event() + ":" + candidates.key(), cacheTimestamp(record));
                cachePositions.put(candidates.event() + ":" + candidates.key(), recordPosition(record));
                putHpsfView(candidates);
                if (hpsfCaughtUp.get()) {
                    // Same P0 gate as the live consumer: route per-session in tenant mode, batch in legacy.
                    if (perSessionRouting()) {
                        routeHpsfPerSession(candidates);
                    } else {
                        enqueuePending(candidates.event(), candidates.key(), candidates.json());
                    }
                }
            }
            return update;
        }
        if (settings.hpsfMarketFlowTopic().equals(topic)) {
            MarketFlowSnapshot snapshot = read(rawJson, MarketFlowSnapshot.class);
            String key = fallbackKey(record);
            return new HpsfCacheUpdate("hpsf-market-flow", key, write(hpsfViewMapper.marketFlowView(snapshot)), null);
        }
        if (settings.hpsfStrikeScoreTopic().equals(topic)) {
            StrikeScoreSnapshot score = read(rawJson, StrikeScoreSnapshot.class);
            String groupKey = hpsfGroupKey(score.tradeDate(), score.expiry(), fallbackKey(record));
            hpsfStrikeScores.put(groupKey + "|" + fallbackKey(record), score);
            return topCandidatesUpdate(groupKey, score.expiry());
        }
        if (settings.hpsfAuditTopic().equals(topic)) {
            HpsfAuditEvent audit = read(rawJson, HpsfAuditEvent.class);
            String key = hpsfGroupKey(audit.tradeDate(), audit.expiry(), fallbackKey(record));
            return new HpsfCacheUpdate("hpsf-audit", key, write(hpsfViewMapper.auditView(audit)), audit.expiry());
        }
        if (settings.hpsfExitSignalTopic().equals(topic)) {
            HpsfExitIntentEvent event = read(rawJson, HpsfExitIntentEvent.class);
            String key = hpsfGroupKey(event.tradeDate(), event.expiry(), fallbackKey(record));
            return new HpsfCacheUpdate("hpsf-exit-intent", key, write(hpsfViewMapper.exitIntentView(event)), event.expiry());
        }
        return null;
    }

    private HpsfCacheUpdate topCandidatesUpdate(String groupKey, String expiry) {
        String latestEvaluationId = hpsfLatestEvaluationIds.get(groupKey);
        List<StrikeScoreSnapshot> scores = hpsfStrikeScores.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(groupKey + "|"))
                .map(Map.Entry::getValue)
                .filter(score -> latestEvaluationId == null || latestEvaluationId.equals(score.evaluationId()))
                .toList();
        if (scores.isEmpty()) {
            return null;
        }
        return new HpsfCacheUpdate("hpsf-top-candidates", groupKey, write(hpsfViewMapper.topCandidatesView(scores)), expiry);
    }

    private void putHpsfView(HpsfCacheUpdate update) {
        switch (update.event()) {
            case "hpsf-latest-signal" -> hpsfLatestSignals.put(update.key(), update.json());
            case "hpsf-market-flow" -> hpsfMarketFlows.put(update.key(), update.json());
            case "hpsf-top-candidates" -> hpsfTopCandidates.put(update.key(), update.json());
            case "hpsf-audit" -> hpsfAudits.put(update.key(), update.json());
            case "hpsf-exit-intent" -> hpsfExitIntents.put(update.key(), update.json());
            default -> {
                // Unknown HPSF events are ignored because they have no UI contract.
            }
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " JSON", e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize HPSF view", e);
        }
    }

    private String hpsfGroupKey(String tradeDate, String expiry, String fallback) {
        if (tradeDate != null && !tradeDate.isBlank() && expiry != null && !expiry.isBlank()) {
            return tradeDate + "|" + expiry;
        }
        return fallback;
    }

    private String fallbackKey(ConsumerRecord<?, ?> record) {
        String key = record.key() == null ? "" : String.valueOf(record.key()).trim();
        return key.isBlank() ? record.topic() + ":" + record.partition() : key;
    }

    private synchronized void purgeExpiredCache(long nowMs) {
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, Long> entry : cacheEventTimes.entrySet()) {
            // Event-aware: the versionKey is "<event>:<key>", so max-pain entries are purged on the long
            // max-pain TTL while everything else stays on the generic 15-min window. Without this, the
            // periodic purge would evict a perfectly valid (but >15-min-old) max-pain on the next poll.
            if (isExpired(eventFromCacheKey(entry.getKey()), entry.getValue(), nowMs)) {
                expiredKeys.add(entry.getKey());
            }
        }
        expiredKeys.forEach(this::removeCacheEntry);
    }

    private boolean isCacheFresh(String versionKey, long nowMs) {
        Long eventTime = cacheEventTimes.get(versionKey);
        // Event-aware so the cached-state snapshot sent to a newly-connected client keeps a slow but valid
        // max-pain (12h window) while fast events still drop at the 15-min generic window.
        return eventTime != null && !isExpired(eventFromCacheKey(versionKey), eventTime, nowMs);
    }

    private boolean broadcastCachedState(List<String> events) {
        List<CachedEvent> cachedEvents = cachedEvents(events, System.currentTimeMillis());
        if (cachedEvents.isEmpty()) {
            return false;
        }
        // Per-session mode: cached market-data state is replayed per-socket on connect
        // (replayCachedToSocket, FR-11); never fan a global ui-batch of snapshots/paces to all.
        if (perSessionRouting()) {
            droppedNonRoutableEvents.addAndGet(cachedEvents.size());
        } else {
            String envelope = uiBatchEnvelopeJson(cachedEvents);
            for (WebSocketSession client : clients) {
                sendEnvelope(client, envelope);
            }
        }
        return cachedEvents.stream().anyMatch(cachedEvent -> "snapshot".equals(cachedEvent.event()));
    }

    private void sendCachedState(WebSocketSession session, List<String> events) {
        List<CachedEvent> cachedEvents = cachedEvents(events, System.currentTimeMillis());
        if (!cachedEvents.isEmpty()) {
            sendEnvelope(session, uiBatchEnvelopeJson(cachedEvents));
        }
    }

    private synchronized List<CachedEvent> cachedEvents(List<String> events, long nowMs) {
        purgeExpiredCache(nowMs);
        ActiveSelection selection = activeSelection.get();
        List<CachedEvent> cachedEvents = new ArrayList<>();
        for (String event : events) {
            switch (event) {
                case "snapshot" -> snapshots.entrySet().stream()
                        .filter(entry -> isCacheFresh("snapshot:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier(
                                "snapshot:" + entry.getKey(),
                                selection,
                                enforceCachedReplayMaxStale("snapshot", selection == null ? "" : selection.source()),
                                enforceCachedReplayOffsetBarrier("snapshot", selection == null ? "" : selection.source())
                        ))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection, false))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("snapshot", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "pace" -> paces.entrySet().stream()
                        .filter(entry -> isCacheFresh("pace:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("pace:" + entry.getKey(), selection))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("pace", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "pace-rank" -> paceRanks.entrySet().stream()
                        .filter(entry -> isCacheFresh("pace-rank:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("pace-rank:" + entry.getKey(), selection))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("pace-rank", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "directional-pressure" -> directionalPressures.entrySet().stream()
                        .filter(entry -> isCacheFresh("directional-pressure:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("directional-pressure:" + entry.getKey(), selection))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("directional-pressure", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "vix-price" -> vixPrices.entrySet().stream()
                        // VIX is SHARED + optional: replay the last-known value to every session (any source);
                        // an empty map simply omits VIX.
                        .filter(entry -> isCacheFresh("vix-price:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("vix-price", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "index-price" -> indexPrices.entrySet().stream()
                        .filter(entry -> isCacheFresh("index-price:" + entry.getKey(), nowMs))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("index-price", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "spx-price" -> spxPrices.entrySet().stream()
                        // Canonical SPX spot: replayed with its ORIGINAL event type (never flattened to
                        // index-price), Databento sessions only — mirrors index-price above.
                        .filter(entry -> isCacheFresh("spx-price:" + entry.getKey(), nowMs))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("spx-price", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "strike-flow" -> strikeFlows.entrySet().stream()
                        .filter(entry -> isCacheFresh("strike-flow:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("strike-flow:" + entry.getKey(), selection))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("strike-flow", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "delta-flow" -> deltaFlows.entrySet().stream()
                        .filter(entry -> isCacheFresh("delta-flow:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("delta-flow:" + entry.getKey(), selection))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("delta-flow", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "strike-intel" -> strikeIntels.entrySet().stream()
                        .filter(entry -> isCacheFresh("strike-intel:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("strike-intel:" + entry.getKey(), selection))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("strike-intel", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "strike-invasion" -> strikeInvasions.entrySet().stream()
                        .filter(entry -> isCacheFresh("strike-invasion:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("strike-invasion:" + entry.getKey(), selection))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("strike-invasion", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "liquidity-heatmap" -> liquidityHeatmaps.entrySet().stream()
                        // DATABENTO-only per-second column frames; isCacheFresh is event-aware with the
                        // SHORT liquidity TTL, so a stale frame is simply absent on connect (UI fills
                        // forward) rather than replayed as live liquidity.
                        .filter(entry -> isCacheFresh("liquidity-heatmap:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("liquidity-heatmap:" + entry.getKey(), selection))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("liquidity-heatmap", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "mission-pace" -> missionPaces.entrySet().stream()
                        .filter(entry -> isCacheFresh("mission-pace:" + entry.getKey(), nowMs))
                        // Per-MARKET signal: keep the TIME/selected-at barrier (enforceMaxStale=true)
                        // so a stale or pre-selection frame is never replayed, but DROP the per-strike
                        // source-switch OFFSET barrier (enforceOffset=false) which otherwise blocks the
                        // low-frequency mission-pace frame so the page never bootstraps on connect.
                        // matchesCachedSelection below still enforces symbol/expiry/source identity.
                        .filter(entry -> passesSelectionBarrier("mission-pace:" + entry.getKey(), selection, true, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("mission-pace", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "mission-control" -> missionControls.entrySet().stream()
                        .filter(entry -> isCacheFresh("mission-control:" + entry.getKey(), nowMs))
                        // Per-MARKET signal: keep the TIME/selected-at barrier (enforceMaxStale=true)
                        // so a stale or pre-selection frame is never replayed, but DROP the per-strike
                        // source-switch OFFSET barrier (enforceOffset=false) which otherwise blocks the
                        // low-frequency mission-control frame so the page never bootstraps on connect.
                        // matchesCachedSelection below still enforces symbol/expiry/source identity.
                        .filter(entry -> passesSelectionBarrier("mission-control:" + entry.getKey(), selection, true, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("mission-control", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "spread-skew" -> spreadSkews.entrySet().stream()
                        .filter(entry -> isCacheFresh("spread-skew:" + entry.getKey(), nowMs))
                        // Per-MARKET signal: keep the TIME/selected-at barrier (enforceMaxStale=true)
                        // so a stale or pre-selection frame is never replayed, but DROP the per-strike
                        // source-switch OFFSET barrier (enforceOffset=false) which otherwise blocks the
                        // low-frequency spread-skew frame so the page never bootstraps on connect.
                        // matchesSpreadSkewSelection below still enforces underlying/expiry/source identity.
                        .filter(entry -> passesSelectionBarrier("spread-skew:" + entry.getKey(), selection, true, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesSpreadSkewSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("spread-skew", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "volume-sandwich" -> currentStates.entrySet().stream()
                        .filter(entry -> "volume-sandwich".equals(eventFromCacheKey(entry.getKey())))
                        .filter(entry -> isCacheFresh(entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier(entry.getKey(), selection))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("volume-sandwich", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "mission-sandwich" -> currentStates.entrySet().stream()
                        .filter(entry -> "mission-sandwich".equals(eventFromCacheKey(entry.getKey())))
                        .filter(entry -> isCacheFresh(entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier(entry.getKey(), selection))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("mission-sandwich", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "gex-by-strike" -> gexByStrike.entrySet().stream()
                        .filter(entry -> isCacheFresh("gex-by-strike:" + entry.getKey(), nowMs))
                        // Slow daily-OI signal like max-pain: relax the time-freshness + offset barriers on
                        // cached replay (enforceCachedReplay* return false for gex-by-strike) so a valid-but-slow
                        // GEX still replays on connect instead of being re-dropped by the 15s selection barrier.
                        .filter(entry -> passesSelectionBarrier(
                                "gex-by-strike:" + entry.getKey(),
                                selection,
                                enforceCachedReplayMaxStale("gex-by-strike", selection == null ? "" : selection.source()),
                                enforceCachedReplayOffsetBarrier("gex-by-strike", selection == null ? "" : selection.source())
                        ))
                        // Source-aware (not hard IBKR-only): the gexByStrike cache now holds BOTH IBKR
                        // (Unusual-Whales, JSON) AND DATABENTO (Avro) entries, source-prefixed in the key.
                        // matchesCachedSelection enforces (source,symbol,expiry) isolation, so an IBKR
                        // selection gets IBKR gex and a DATABENTO selection gets DATABENTO gex — the prior
                        // hard `"IBKR".equals(source)` filter wrongly suppressed DATABENTO gex (which used to
                        // be garbled-on-the-JSON-consumer and never delivered anyway; it now works via Avro).
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("gex-by-strike", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "gex-oi-status" -> gexOiStatus.entrySet().stream()
                        // Slow watchdog signal (a few records per day at most): replay like gex-by-strike —
                        // relaxed time/offset barriers, source/symbol/expiry isolation still enforced.
                        .filter(entry -> isCacheFresh("gex-oi-status:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier(
                                "gex-oi-status:" + entry.getKey(),
                                selection,
                                enforceCachedReplayMaxStale("gex-oi-status", selection == null ? "" : selection.source()),
                                enforceCachedReplayOffsetBarrier("gex-oi-status", selection == null ? "" : selection.source())
                        ))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("gex-oi-status", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "strike-sr" -> strikeSr.entrySet().stream()
                        // DATABENTO-only Avro per-bucket S/R map. These are compacted current-state levels:
                        // unchanged active levels are not re-emitted every maxStaleMs, and retractions arrive as
                        // tombstones. Replay them while the cache entry is alive, but still enforce
                        // source/symbol/expiry isolation below.
                        .filter(entry -> isCacheFresh("strike-sr:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("strike-sr:" + entry.getKey(), selection, false, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("strike-sr", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "gamma-migration" -> gammaMigration.entrySet().stream()
                        .filter(entry -> isCacheFresh("gamma-migration:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("gamma-migration:" + entry.getKey(),
                                selection, false, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("gamma-migration", entry.getValue()))
                        .forEach(cachedEvents::add);

                case "gex-magnet" -> gexMagnet.entrySet().stream()
                        // DATABENTO-only Avro per-chain magnet value (last-value-wins). Replay while the
                        // cache entry is alive, enforcing source/symbol/expiry isolation below.
                        .filter(entry -> isCacheFresh("gex-magnet:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("gex-magnet:" + entry.getKey(), selection, false, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("gex-magnet", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "es-gex" -> esGex.entrySet().stream()
                        // DATABENTO-only JSON whole-book (roll-forward). Replay while the cache entry is alive.
                        .filter(entry -> isCacheFresh("es-gex:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("es-gex:" + entry.getKey(), selection, false, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("es-gex", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "es-strike-intel" -> esStrikeIntel.entrySet().stream()
                        // DATABENTO-rendered ES-origin overlay (per ES strike). Replay while the entry is alive;
                        // a withdrawn signal was already evicted, so it never replays.
                        .filter(entry -> isCacheFresh("es-strike-intel:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("es-strike-intel:" + entry.getKey(), selection, false, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("es-strike-intel", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "gex-strike-lifecycle" -> gexStrikeLifecycle.entrySet().stream()
                        // DATABENTO-only Avro per-strike lifecycle labels (last-value-wins, like gex-by-strike).
                        .filter(entry -> isCacheFresh("gex-strike-lifecycle:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("gex-strike-lifecycle:" + entry.getKey(), selection, false, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("gex-strike-lifecycle", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "max-pain" -> maxPain.entrySet().stream()
                        // DATABENTO-only stream: IBKR-selected sessions never receive max pain.
                        // isCacheFresh is event-aware (12h max-pain window); the selection barrier relaxes
                        // the time-freshness/offset checks for max-pain (enforceCachedReplay* below) so a
                        // valid but slow last-value-wins record still replays — while matchesCachedSelection
                        // + the DATABENTO source filter keep per-session (symbol,expiry,source) isolation.
                        .filter(entry -> isCacheFresh("max-pain:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier(
                                "max-pain:" + entry.getKey(),
                                selection,
                                enforceCachedReplayMaxStale("max-pain", selection == null ? "" : selection.source()),
                                enforceCachedReplayOffsetBarrier("max-pain", selection == null ? "" : selection.source())))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("max-pain", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "option-price-behavior" -> optionPriceBehaviors.entrySet().stream()
                        .filter(entry -> isCacheFresh("option-price-behavior:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("option-price-behavior:" + entry.getKey(), selection, true, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesOptionPriceBehaviorSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("option-price-behavior", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "opb-by-option" -> opbByOptions.entrySet().stream()
                        .filter(entry -> isCacheFresh("opb-by-option:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("opb-by-option:" + entry.getKey(), selection))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("opb-by-option", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "opb-session" -> opbSessions.entrySet().stream()
                        .filter(entry -> isCacheFresh("opb-session:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("opb-session:" + entry.getKey(), selection, true, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesOptionPriceBehaviorSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("opb-session", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "hpsf-latest-signal" -> hpsfLatestSignals.entrySet().stream()
                        .filter(entry -> isCacheFresh("hpsf-latest-signal:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("hpsf-latest-signal", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "hpsf-market-flow" -> hpsfMarketFlows.entrySet().stream()
                        .filter(entry -> isCacheFresh("hpsf-market-flow:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("hpsf-market-flow", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "hpsf-top-candidates" -> hpsfTopCandidates.entrySet().stream()
                        .filter(entry -> isCacheFresh("hpsf-top-candidates:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("hpsf-top-candidates", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "hpsf-audit" -> hpsfAudits.entrySet().stream()
                        .filter(entry -> isCacheFresh("hpsf-audit:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("hpsf-audit", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "hpsf-exit-intent" -> hpsfExitIntents.entrySet().stream()
                        .filter(entry -> isCacheFresh("hpsf-exit-intent:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("hpsf-exit-intent", entry.getValue()))
                        .forEach(cachedEvents::add);
                default -> {
                    // Unknown events are ignored because there is no replay cache for them.
                }
            }
        }
        return cachedEvents;
    }

    private boolean isExpired(long eventTime, long nowMs) {
        long ttlMs = settings.cacheTtlMs();
        return ttlMs <= 0 || eventTime < nowMs - ttlMs;
    }

    /**
     * The structural option-chain cache events that follow market-aware freshness (10-min RTH, never
     * off-hours). Scoped to exactly the "current-state" caches that are replayed IN FULL on connect — i.e.
     * those EXEMPT from the cached-replay staleness/offset barrier (see {@link #enforceCachedReplayMaxStale}:
     * {@code snapshot} + {@code max-pain}). max-pain keeps its own 12h window; the fast order-flow signals
     * (pace/directional-pressure/strike-flow/gex-by-strike) are deliberately stale-gated by the 15s selection
     * barrier — a 12h-old flow value is misleading — so making them never-evict would retain-but-never-deliver
     * them. Keeping the never-evict policy aligned with what is actually delivered off-hours is the contract.
     */
    private static final Set<String> MARKET_AWARE_CHAIN_EVENTS = Set.of("snapshot");

    /**
     * The freshness/seek decision for a cache event. {@code neverEvict} disables staleness eviction entirely
     * (off-hours, so the published strike structure persists) while {@code seekBackMs} keeps the Kafka
     * cache-rebuild window BOUNDED regardless — the two concerns are deliberately decoupled.
     */
    private record CachePolicy(long ttlMs, boolean neverEvict, long seekBackMs) {
        static CachePolicy expiring(long ttlMs) {
            return new CachePolicy(ttlMs, false, ttlMs);
        }

        static CachePolicy noEviction(long seekBackMs) {
            return new CachePolicy(0L, true, seekBackMs);
        }
    }

    /**
     * Effective cache policy for an event type — the ONE seam all freshness flows through (seek window,
     * ingest eviction, periodic purge, cached-state send-filter):
     * <ul>
     *   <li>{@code max-pain}: its own long window ({@link GatewaySettings#maxPainTtlMs()}, 12h) — a slow
     *       daily last-value-wins signal, unchanged.</li>
     *   <li>the structural chain ({@code snapshot}, see {@link #MARKET_AWARE_CHAIN_EVENTS}): MARKET-AWARE.
     *       During regular trading hours a short freshness TTL ({@link GatewaySettings#optionChainRthCacheTtlMs()},
     *       10m); OFF-hours never evicted (so the published strikes stay visible overnight/weekends/holidays),
     *       with a bounded off-hours seek-back ({@link GatewaySettings#optionChainOffHoursSeekBackMs()}, 24h).</li>
     *   <li>everything else (pace/directional-pressure/strike-flow/gex-by-strike/index-price/vix/HPSF/...):
     *       the generic {@link GatewaySettings#cacheTtlMs()} (fast signals also keep their 15s selection barrier).</li>
     * </ul>
     */
    // Test seam: when non-null, forces the market-hours decision (true=RTH, false=off-hours) so the cache
    // POLICY can be tested deterministically without the wall clock. The calendar date/holiday math itself
    // is covered separately by GatewayMarketCalendarTest. Mirrors the other override*ForTest seams.
    private volatile Boolean regularTradingHoursOverrideForTest = null;

    void overrideRegularTradingHoursForTest(Boolean regularTradingHours) {
        this.regularTradingHoursOverrideForTest = regularTradingHours;
    }

    private boolean isRegularTradingHours(long nowMs) {
        Boolean override = regularTradingHoursOverrideForTest;
        return override != null ? override : marketCalendar.isRegularTradingHours(Instant.ofEpochMilli(nowMs));
    }

    private CachePolicy cachePolicyFor(String event, long nowMs) {
        if ("option-truth".equals(event)) {
            return CachePolicy.expiring(settings.optionTruthTtlMs());
        }
        if ("zero-dte-intelligence".equals(event)) {
            // This state controls a whole-chain red/green background. Keep its lifetime deliberately
            // short and shared across ingest, purge, reconnect replay, and restart seek-back.
            return CachePolicy.expiring(settings.zeroDteIntelligenceTtlMs());
        }
        if ("max-pain".equals(event)) {
            return CachePolicy.expiring(settings.maxPainTtlMs());
        }
        if ("hot-strike".equals(event)) {
            // The day's hot-strike row stays valid for the whole session (recompute is
            // hourly): 12h window + matching seek-back so a restarted gateway
            // re-bootstraps the CURRENT row instead of waiting for the next compute.
            return CachePolicy.expiring(settings.hotStrikeTtlMs());
        }
        if ("short-premium-recommendation".equals(event)) {
            // A recommendation is emitted once at entry and stays valid for the whole 0DTE session:
            // use a long last-value-wins window (default 12h, like max-pain), NOT the generic 15-min TTL,
            // so the overlay persists and replays on reconnect instead of vanishing minutes after entry.
            return CachePolicy.expiring(settings.shortPremiumRecommendationTtlMs());
        }
        if ("es-open-direction-status".equals(event)) {
            // 60s live heartbeat: SHORT window (default 5 min, the liquidity-heatmap/dealer-ledger
            // freshness class) — NEVER the siblings' 12h window below (this check MUST stay before the
            // isEsOpenDirectionEvent fall-through). A stale status (dead producer, overnight leftover)
            // must read as absent, not replay as current. The ONE seam: drives ingest eviction
            // (updateCache -> isExpired), periodic purge, the isCacheFresh replay gate, and the bounded
            // ~5-min seek-back (windowTtlMsFor -> seekBackMs) after a gateway restart.
            return CachePolicy.expiring(settings.esOpenDirectionStatusTtlMs());
        }
        if ("greek-move-auth".equals(event)) {
            // Move-authenticity CURRENT verdict: SHORT window (default 5 min, the es-open-direction
            // STATUS / liquidity-heatmap / dealer-ledger freshness class) — a verdict is only meaningful
            // while CURRENT. A stale verdict (dead producer, overnight leftover) must read as absent, not
            // replay as live. The ONE seam: drives ingest eviction (updateCache -> isExpired), periodic
            // purge, the isCacheFresh replay gate, and the bounded ~5-min seek-back (windowTtlMsFor ->
            // seekBackMs) after a gateway restart.
            return CachePolicy.expiring(settings.greekMoveAuthTtlMs());
        }
        if ("spot-vol-regime".equals(event)) {
            // Spot-vol regime CURRENT snapshot: SHORT window (default 5 min, the greek-move-auth /
            // es-open-direction STATUS freshness class) — a regime is only meaningful while CURRENT.
            // A stale regime (dead producer, overnight leftover) must read as absent, not replay as
            // live. Same ONE-seam consequences as the sibling above.
            return CachePolicy.expiring(settings.spotVolRegimeTtlMs());
        }
        if ("vol-premium-ivrv".equals(event)) {
            // Vol-premium IV/RV reading: same SHORT freshness class — an implied-vs-realised point
            // is only meaningful while CURRENT, and a dead producer's last reading must read as
            // absent rather than replay as live.
            return CachePolicy.expiring(settings.volPremiumIvrvTtlMs());
        }
        if ("indicators".equals(event)) {
            // Indicator CURRENT snapshot: same SHORT freshness class — a dead
            // producer's snapshot must read as ABSENT on late-join, never as live.
            return CachePolicy.expiring(settings.indicatorsTtlMs());
        }
        if ("close-direction".equals(event)) {
            // Long last-value-wins window (default 12h, the max-pain/es-open-direction class): the
            // frozen T-11m VERDICT stays decision-relevant until the close and must survive a gateway
            // restart (matching seek-back). Interim REPLAY freshness is additionally bounded by
            // closeDirectionInterimFreshMs inside replayCloseDirectionCached — the long window here
            // governs eviction and seek-back only.
            return CachePolicy.expiring(settings.closeDirectionTtlMs());
        }
        if (isEsOpenDirectionEvent(event)) {
            // The 09:15 forecast (and each horizon outcome) stays valid for the whole session: a long
            // last-value-wins window (default 12h, like max-pain) — NEVER the generic 15-min TTL — so a
            // client connecting at 11:00 still receives the morning forecast, and the matching 12h
            // seek-back (windowTtlMsFor -> seekBackMs) re-bootstraps it after a gateway restart.
            return CachePolicy.expiring(settings.esOpenDirectionTtlMs());
        }
        if ("gex-by-strike".equals(event)) {
            // GEX is a slow once-daily-OI signal like max-pain: a strike re-emits only when it trades, so its
            // latest record is routinely older than the generic 15-min TTL. Use a long last-value-wins window
            // (default 12h) so a valid-but-slow GEX is not evicted and still replays on connect.
            return CachePolicy.expiring(settings.gexByStrikeTtlMs());
        }
        if ("gex-oi-status".equals(event)) {
            // OI-arrival badge state: at most a handful of records per day, must survive reconnects all
            // session — same long last-value-wins window as gex-by-strike/max-pain (default 12h).
            return CachePolicy.expiring(settings.gexOiStatusTtlMs());
        }
        if ("ibkr-preopen-status".equals(event)) {
            // Pre-open window state: one session's horizon (default 4h) — survives an intra-window
            // reconnect, can never replay yesterday's window as live (rev13 R-STATE).
            return CachePolicy.expiring(settings.ibkrPreOpenStatusTtlMs());
        }
        if ("tapeZones".equals(event)) {
            // Tape-zones board: the SHORT freshness class (spot-vol-regime/indicators). A board
            // from a dead service or a stale mirror must read as ABSENT on late-join, never live.
            // Sub-TTL aging is the card's own business — §5's 10 s STALE overlay reads the emitted
            // ageMs; eviction here is deliberately much looser because the board publishes ON
            // CHANGE and a quiet minute is a normal, renderable state.
            return CachePolicy.expiring(settings.tapeZonesTtlMs());
        }
        if ("es-gex".equals(event)) {
            // ES-on-SPX aligned book: the align service re-emits ~5s, but a quiet chain may pause; a long
            // last-value-wins window (default 12h, like gex-by-strike) so a mid-session reconnect gets the
            // latest book. Freshness/roll-forward is governed by the payload emitEventTimeMs (below).
            return CachePolicy.expiring(settings.esGexTtlMs());
        }
        if ("es-strike-intel".equals(event)) {
            // Projected ES strike-intel: a quiet-but-valid signal must not evict (withdrawal is explicit via
            // tombstone), so use the same long last-value-wins window as es-gex. Roll-forward/freshness track
            // the Kafka producer time (default cacheTimestamp), which advances on every re-emit.
            return CachePolicy.expiring(settings.esStrikeIntelTtlMs());
        }
        if ("strike-intel".equals(event)) {
            // NATIVE per-strike strike-intel: a published 0DTE level must persist for the whole session and
            // drop ONLY at expiry (per-strike replay is expiry-filtered; DashboardAssembler.expiryOpen purges
            // at 16:00 ET) — a strike that goes quiet must NOT be evicted by the generic 15-min cacheTtlMs.
            // Same "quiet-but-valid must not evict" contract as the projected es-strike-intel sibling.
            return CachePolicy.expiring(settings.strikeIntelTtlMs());
        }
        if ("gex-strike-lifecycle".equals(event)) {
            // rev-17: an active strike re-emits its label every frame, so a long last-value-wins window (default
            // 12h, like gex-by-strike) lets a mid-session reconnect replay the latest badge for still-active
            // strikes; a departed strike's one-shot NEUTRAL is the last cached value and replays as "no badge".
            return CachePolicy.expiring(settings.gexStrikeLifecycleTtlMs());
        }
        if ("liquidity-heatmap".equals(event)) {
            // Per-second column frames: SHORT window (default 5s), never the generic 15-min TTL —
            // a minutes-old frame must read as stale/absent, not live liquidity.
            return CachePolicy.expiring(settings.liquidityHeatmapTtlMs());
        }
        if ("dealer-ledger".equals(event)) {
            // Live PERMISSION heartbeat (state emits every flow evaluation): a minutes-old record must
            // read as STALE, never active. SHORT window (default 15s), never the generic 15-min TTL —
            // otherwise a stalled/dead producer's last ARMED/DEFENDED would render as an active permission.
            // Drives join freshness (joinDealerLedger), purge eviction, and cached-replay uniformly.
            return CachePolicy.expiring(settings.dealerLedgerTtlMs());
        }
        if ("gamma-fragility".equals(event) || "gamma-rotation".equals(event)) {
            // NEVER the generic 15-minute TTL. This topic publishes only when the peak MOVES, so a
            // quiet stretch is the normal state of a healthy chain — and eviction after 15 quiet
            // minutes threw away the whole session's move log, after which the endpoint answered
            // present:false and the card said "the peak has not moved yet today". That is the
            // precise false claim the card was built to avoid (Codex).
            //
            // The record is a SESSION-LONG accumulation, not a heartbeat: its age says nothing
            // about whether it is still true, and the producer replaces it at the ET rollover.
            // Seek-back matches, so a restart rebuilds a log whose last move was hours ago.
            return CachePolicy.noEviction(settings.optionChainOffHoursSeekBackMs());
        }
        if (MARKET_AWARE_CHAIN_EVENTS.contains(event)) {
            if (isRegularTradingHours(nowMs)) {
                return CachePolicy.expiring(settings.optionChainRthCacheTtlMs());
            }
            return CachePolicy.noEviction(settings.optionChainOffHoursSeekBackMs());
        }
        return CachePolicy.expiring(settings.cacheTtlMs());
    }

    /** Event-aware staleness: market-aware for the option-chain cache, generic otherwise. */
    private boolean isExpired(String event, long eventTime, long nowMs) {
        CachePolicy policy = cachePolicyFor(event, nowMs);
        if (policy.neverEvict()) {
            return false;
        }
        return policy.ttlMs() <= 0 || eventTime < nowMs - policy.ttlMs();
    }

    private boolean passesSelectionBarrier(String versionKey, ActiveSelection selection) {
        return passesSelectionBarrier(versionKey, selection, true, true);
    }

    private boolean passesSelectionBarrier(String versionKey, ActiveSelection selection, boolean enforceMaxStale) {
        return passesSelectionBarrier(versionKey, selection, enforceMaxStale, true);
    }

    private boolean passesSelectionBarrier(
            String versionKey,
            ActiveSelection selection,
            boolean enforceMaxStale,
            boolean enforceOffset
    ) {
        Long eventTimeMs = cacheEventTimes.get(versionKey);
        if (eventTimeMs == null || !passesSelectionTimeBarrier(eventTimeMs, selection, enforceMaxStale)) {
            return false;
        }
        RecordPosition position = cachePositions.get(versionKey);
        return !enforceOffset || position == null || passesOffsetBarrier(position.partition(), position.offset());
    }

    private boolean passesSelectionBarrier(ConsumerRecord<?, ?> record, ActiveSelection selection) {
        if (!passesSelectionTimeBarrier(cacheTimestamp(record), selection)) {
            return false;
        }
        return passesOffsetBarrier(new TopicPartition(record.topic(), record.partition()), record.offset());
    }

    /** Selection-independent freshness: true when the record's event time is within maxStaleMs of now. */
    private boolean recordWithinMaxStale(ConsumerRecord<?, ?> record) {
        long maxStaleMs = settings.maxStaleMs();
        return maxStaleMs <= 0L || cacheTimestamp(record) >= System.currentTimeMillis() - maxStaleMs;
    }

    /** As {@link #recordWithinMaxStale}, but on a PAYLOAD event time (e.g. spread-skew's {@code ts}). */
    private boolean eventTimeWithinMaxStale(long eventTimeMs) {
        long maxStaleMs = settings.maxStaleMs();
        return maxStaleMs <= 0L || eventTimeMs >= System.currentTimeMillis() - maxStaleMs;
    }

    private boolean passesSelectionTimeBarrier(long eventTimeMs, ActiveSelection selection) {
        return passesSelectionTimeBarrier(eventTimeMs, selection, true);
    }

    private boolean passesSelectionTimeBarrier(long eventTimeMs, ActiveSelection selection, boolean enforceMaxStale) {
        if (!enforceMaxStale) {
            return true;
        }
        if (selection != null && selection.selectedAtMs() > 0L && eventTimeMs < selection.selectedAtMs()) {
            return false;
        }
        long maxStaleMs = settings.maxStaleMs();
        return maxStaleMs <= 0L || eventTimeMs >= System.currentTimeMillis() - maxStaleMs;
    }

    boolean passesSelectionTimeBarrierForTest(long eventTimeMs, long selectedAtMs, boolean enforceMaxStale) {
        return passesSelectionTimeBarrier(eventTimeMs, new ActiveSelection("IBKR", "SPX", "20260616", 1L, selectedAtMs), enforceMaxStale);
    }

    static boolean enforceCachedReplayMaxStale(String event, String source) {
        // snapshot, max-pain AND gex-by-strike are "current-state" caches replayed in full on connect. Max-pain
        // and GEX are slow daily-OI signals whose latest record is routinely older than maxStaleMs (15s) and
        // older than the client's selectedAtMs — enforcing the max-stale/selected-time barrier here would
        // re-drop them even after the TTL seam admits them. Selection isolation is still enforced by
        // matchesCachedSelection + the DATABENTO source filter; this only relaxes the time-freshness barrier.
        return !"snapshot".equals(event) && !"max-pain".equals(event) && !"gex-by-strike".equals(event)
                && !"gex-oi-status".equals(event);
    }

    static boolean enforceCachedReplayOffsetBarrier(String event, String source) {
        // Same rationale as enforceCachedReplayMaxStale: a slow max-pain/GEX latest record can sit below the
        // session's per-partition offset barrier (set when other fast topics advanced past selection), so
        // the offset barrier would wrongly filter the current max-pain/GEX on replay. Exempt like snapshot.
        return !"snapshot".equals(event) && !"max-pain".equals(event) && !"gex-by-strike".equals(event)
                && !"gex-oi-status".equals(event);
    }

    private boolean passesOffsetBarrier(TopicPartition partition, long offset) {
        Long barrier = offsetBarriers.get().get(partition);
        return barrier == null || offset >= barrier;
    }

    private long cacheTimestamp(ConsumerRecord<?, ?> record) {
        long eventTime = record.timestamp();
        return eventTime >= 0 ? eventTime : System.currentTimeMillis();
    }

    private long eventCacheTimestamp(String event, ConsumerRecord<?, ?> record, String json) {
        if ("zero-dte-intelligence".equals(event)) {
            // Never use fresh Kafka arrival time for a replayed direction decision. A historical record
            // arriving now must expire from its decision time, otherwise an old unusual burst can tint
            // the live chain dark red/green.
            return zeroDteIntelligenceTimestamp(json);
        }
        if ("greek-move-auth".equals(event)) {
            // Freshness MUST track the PAYLOAD event time (asOfEventTimeMs), not the Kafka ARRIVAL time
            // (mirrors dealer-ledger/zero-dte). A producer catching up / backfilling appends records now
            // (fresh arrival) whose asOfEventTimeMs is old — using arrival time would let a stale verdict
            // pass the SHORT greekMoveAuthTtlMs window and render the authenticity track as live.
            return greekMoveAuthTimestamp(json);
        }
        if ("vol-premium-ivrv".equals(event)) {
            // Freshness tracks the PAYLOAD's own event time, never the Kafka ARRIVAL time, so a
            // producer catching up on a backlog cannot render a stale reading as live.
            return volPremiumIvrvTimestamp(json);
        }
        if ("spot-vol-regime".equals(event)) {
            // Same rule as greek-move-auth: freshness tracks the PAYLOAD stream-time (asOfEventTimeMs),
            // never the Kafka ARRIVAL time, so a producer catching up on a backlog cannot render a
            // stale regime as live.
            return spotVolRegimeTimestamp(json);
        }
        if ("indicators".equals(event)) {
            return indicatorsTimestamp(json);
        }
        if ("liquidity-heatmap".equals(event)) {
            long payloadTime = liquidityHeatmapTimestamp(json);
            if (payloadTime >= 0) {
                return payloadTime;
            }
        }
        if ("dealer-ledger".equals(event)) {
            // Freshness MUST track the PAYLOAD event time (asOfEventTimeMs), not the Kafka ARRIVAL time.
            // A producer catching up on a backlog appends records now (fresh arrival) whose asOfEventTimeMs
            // is old — using arrival time would let a stale permission pass the 15s TTL and render active.
            long payloadTime = dealerLedgerEventTimestamp(json);
            if (payloadTime >= 0) {
                return payloadTime;
            }
        }
        if ("delta-flow".equals(event)) {
            // Freshness MUST track the PAYLOAD event time (asOfEventTimeMs), not the Kafka ARRIVAL time
            // (mirrors dealer-ledger above). A producer catching up / backfilling appends records now
            // (fresh arrival) whose asOfEventTimeMs is old — using arrival time would let a stale
            // delta-flow signal pass the cache-fresh TTL + replay barriers and render as live.
            long payloadTime = deltaFlowTimestamp(json);
            if (payloadTime >= 0) {
                return payloadTime;
            }
        }
        if ("strike-intel".equals(event)) {
            // Freshness MUST track the PAYLOAD event time (asOfEventTimeMs), not the Kafka ARRIVAL time
            // (mirrors delta-flow above). A producer catching up / backfilling appends records now
            // (fresh arrival) whose asOfEventTimeMs is old — using arrival time would let a stale
            // strike-intel signal pass the cache-fresh TTL + replay barriers and render as live.
            long payloadTime = strikeIntelTimestamp(json);
            if (payloadTime >= 0) {
                return payloadTime;
            }
        }
        if ("option-truth".equals(event)) {
            long payloadTime = optionTruthTimestamp(json);
            if (payloadTime >= 0) {
                return payloadTime;
            }
        }
        if ("strike-invasion".equals(event)) {
            // Freshness MUST track the PAYLOAD event time (asOfEventTimeMs), not the Kafka ARRIVAL time
            // (mirrors strike-intel above). A producer catching up / backfilling appends records now
            // (fresh arrival) whose asOfEventTimeMs is old — using arrival time would let a stale
            // strike-invasion action pass the cache-fresh TTL + replay barriers and render as live. This
            // is what the strikeInvasionStale live gate and isCacheFresh replay gate both rely on.
            long payloadTime = strikeInvasionTimestamp(json);
            if (payloadTime >= 0) {
                return payloadTime;
            }
        }
        if ("spread-skew".equals(event)) {
            // Freshness MUST track the PAYLOAD event time (ts), not the Kafka ARRIVAL time (mirrors
            // strike-invasion above). A producer catching up / backfilling appends records now (fresh
            // arrival) whose ts is old — using arrival time would let a stale skew state pass the
            // cache-fresh TTL + replay barriers and render as live. This is what the spreadSkewStale
            // live gate and the shouldForward/isCacheFresh spread-skew paths all rely on. Unlike the
            // siblings above there is deliberately NO Kafka-arrival fallback: a missing/unparseable ts
            // returns -1 (fail closed), so a malformed snapshot arriving fresh can never be cached or
            // forwarded as current.
            return spreadSkewTimestamp(json);
        }
        if ("es-gex".equals(event)) {
            // Roll-forward + freshness track the align service's monotonic emitEventTimeMs, NOT Kafka arrival
            // (a re-emitted/replayed book must order by emit time). Missing ⇒ -1 (fail closed).
            return esGexTimestamp(json);
        }
        if ("gex-strike-lifecycle".equals(event)) {
            // Track the PAYLOAD event time (eventTimeMs), not Kafka arrival — a backfilling producer appends
            // fresh-arrival records whose eventTimeMs is old; arrival time would let a stale badge render live.
            // Return it UNCONDITIONALLY (no Kafka fallback): a missing/unparseable eventTimeMs yields -1, which
            // reads as ancient so updateCache rejects the record (cacheKey==null) and every live-forward gate
            // fails closed — a malformed lifecycle record is never cached, replayed, or rendered.
            return strikeLifecycleTimestamp(json);
        }
        return cacheTimestamp(record);
    }

    /** Event time (asOfEventTimeMs) of a raw dealer-ledger profile/state record; -1 if absent/unparseable. */
    private long dealerLedgerEventTimestamp(String json) {
        try {
            return longField(mapper.readTree(json), "asOfEventTimeMs", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    /** Decision time for the chain-level 0DTE direction state; -1 means malformed and fails closed. */
    private long zeroDteIntelligenceTimestamp(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            long epochMs = longField(root, "asOfEventTimeMs", -1L);
            if (epochMs >= 0) {
                return epochMs;
            }
            for (String field : List.of("asOfEventTime", "asOf", "producedAt")) {
                String value = text(root, field);
                if (!value.isBlank()) {
                    long parsed = parseInstantMs(value, -1L);
                    if (parsed >= 0) {
                        return parsed;
                    }
                }
            }
        } catch (JsonProcessingException ignored) {
            // Invalid JSON is not a current decision.
        }
        return -1L;
    }

    /**
     * Decision time (asOfEventTimeMs) of a greek-move-authenticity verdict; -1 means malformed/absent/
     * implausibly-future and fails closed (such a verdict can never be cached or replayed as current).
     *
     * <p><b>Clock-skew freeze-safety.</b> asOfEventTimeMs is a PAST observation (the market event the
     * verdict is computed from), so it must never be materially ahead of the gateway wall clock. A
     * future-dated verdict would (a) evade the SHORT-window expiry gate and (b) POISON the monotonic
     * last-value-wins supersede check in {@code updateCache} — its future event time would reject every
     * subsequently-arriving CORRECT verdict as "older" until wall time catches up, freezing the symbol's
     * track. Records beyond {@link #GREEK_MOVE_AUTH_MAX_FUTURE_SKEW_MS} ahead of now are rejected here
     * (returned as -1) so they are dropped at ingest and can never enter {@code cacheEventTimes}.
     */
    private long greekMoveAuthTimestamp(String json) {
        try {
            long eventTimeMs = longField(mapper.readTree(json), "asOfEventTimeMs", -1L);
            if (eventTimeMs > System.currentTimeMillis() + GREEK_MOVE_AUTH_MAX_FUTURE_SKEW_MS) {
                return -1L; // implausibly future — fail closed, never cache/replay/poison the supersede gate
            }
            return eventTimeMs;
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    /**
     * Stream-time (asOfEventTimeMs) of a spot-vol-regime snapshot; -1 means malformed/absent/
     * implausibly-future and fails closed — same freeze-safety rationale as
     * {@link #greekMoveAuthTimestamp}.
     */
    /**
     * Stream-time (eventTimeMs) of a vol-premium reading; -1 means malformed/absent/implausibly
     * future and fails closed — same freeze-safety rationale as {@link #spotVolRegimeTimestamp}.
     */
    private long volPremiumIvrvTimestamp(String json) {
        try {
            long eventTimeMs = longField(mapper.readTree(json), "eventTimeMs", -1L);
            if (eventTimeMs > System.currentTimeMillis() + SPOT_VOL_REGIME_MAX_FUTURE_SKEW_MS) {
                return -1L; // implausibly future — fail closed, never cache or replay
            }
            return eventTimeMs;
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    private long spotVolRegimeTimestamp(String json) {
        try {
            long eventTimeMs = longField(mapper.readTree(json), "asOfEventTimeMs", -1L);
            if (eventTimeMs > System.currentTimeMillis() + SPOT_VOL_REGIME_MAX_FUTURE_SKEW_MS) {
                return -1L; // implausibly future — fail closed, never cache/replay/poison the supersede gate
            }
            return eventTimeMs;
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    /** Event time (asOfEventTimeMs) of a raw per-strike delta-flow record; -1 if absent/unparseable. */
    private long deltaFlowTimestamp(String json) {
        try {
            return longField(mapper.readTree(json), "asOfEventTimeMs", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    /**
     * Event time of a raw per-strike strike-intel record; -1 if absent/unparseable. The
     * {@code StrikeIntelligenceSignal} contract names this field {@code eventTimeMs} (decision-relevant
     * event time; {@code publishedAtMs} is wall-clock and NOT used for freshness). A legacy
     * {@code asOfEventTimeMs} fallback is kept only for defensive parity with the delta-flow sibling.
     */
    private long strikeIntelTimestamp(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            long eventTime = longField(node, "eventTimeMs", -1L);
            return eventTime >= 0 ? eventTime : longField(node, "asOfEventTimeMs", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    /** Event time of an Option Truth pair reading; missing time fails closed. */
    private long optionTruthTimestamp(String json) {
        try {
            return longField(mapper.readTree(json), "eventTimeMs", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    /**
     * Event time of a raw per-strike strike-invasion record; -1 if absent/unparseable. The
     * {@code StrikeInvasionSnapshot} contract names this field {@code asOfEventTimeMs} (decision-relevant
     * event time). An {@code eventTimeMs} fallback is kept for defensive parity with the strike-intel
     * sibling.
     */
    private long strikeInvasionTimestamp(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            long eventTime = longField(node, "asOfEventTimeMs", -1L);
            return eventTime >= 0 ? eventTime : longField(node, "eventTimeMs", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    /**
     * Event time of a spread-skew snapshot record; -1 if absent/unparseable. The spread-skew contract
     * names this field {@code ts} (epoch ms of the evaluated bar — the decision-relevant event time;
     * there is no asOfEventTimeMs sibling).
     */
    private long spreadSkewTimestamp(String json) {
        try {
            return longField(mapper.readTree(json), "ts", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    private long liquidityHeatmapTimestamp(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            long asOf = longField(root, "asOfEventTimeMs", -1L);
            if (asOf >= 0) {
                return asOf;
            }
            long bucketEnd = longField(root, "bucketEndMs", -1L);
            if (bucketEnd >= 0) {
                return bucketEnd;
            }
            return longField(root, "bucketStartMs", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    private RecordPosition recordPosition(ConsumerRecord<?, ?> record) {
        return new RecordPosition(new TopicPartition(record.topic(), record.partition()), record.offset());
    }

    /**
     * SYNCHRONIZED, and on the instance monitor rather than on any event's emit lock.
     *
     * <p>updateCache holds this same monitor, so a cache entry cannot be half-removed while a
     * record is being installed, and the fence below is removed with the rest of the entry as one
     * unit relative to ingest.
     *
     * <p>It must NOT take an event emit lock. Both ingest paths acquire the emit lock and then
     * call updateCache, which takes this monitor; a removal taking them in the other order is a
     * lock-order inversion, and the deadlock it buys costs far more than the window it closes.
     */
    private synchronized void removeCacheEntry(String versionKey) {
        cacheEventTimes.remove(versionKey);
        cachePositions.remove(versionKey);
        if (versionKey.startsWith("snapshot:")) {
            snapshots.remove(versionKey.substring("snapshot:".length()));
        } else if (versionKey.startsWith("pace-rank:")) {
            paceRanks.remove(versionKey.substring("pace-rank:".length()));
        } else if (versionKey.startsWith("pace:")) {
            paces.remove(versionKey.substring("pace:".length()));
        } else if (versionKey.startsWith("directional-pressure:")) {
            directionalPressures.remove(versionKey.substring("directional-pressure:".length()));
        } else if (versionKey.startsWith("vix-price:")) {
            vixPrices.remove(versionKey.substring("vix-price:".length()));
        } else if (versionKey.startsWith("index-price:")) {
            indexPrices.remove(versionKey.substring("index-price:".length()));
        } else if (versionKey.startsWith("spx-price:")) {
            spxPrices.remove(versionKey.substring("spx-price:".length()));
        } else if (versionKey.startsWith("short-premium-recommendation:")) {
            shortPremiumRecommendations.remove(versionKey.substring("short-premium-recommendation:".length()));
        } else if (versionKey.startsWith("es-open-direction-forecast:")) {
            esOpenDirectionForecasts.remove(versionKey.substring("es-open-direction-forecast:".length()));
        } else if (versionKey.startsWith("es-open-direction-outcome:")) {
            esOpenDirectionOutcomes.remove(versionKey.substring("es-open-direction-outcome:".length()));
        } else if (versionKey.startsWith("es-open-direction-status:")) {
            esOpenDirectionStatuses.remove(versionKey.substring("es-open-direction-status:".length()));
        } else if (versionKey.startsWith("greek-move-auth:")) {
            greekMoveAuthCurrent.remove(versionKey.substring("greek-move-auth:".length()));
        } else if (versionKey.startsWith("spot-vol-regime:")) {
            spotVolRegime.remove(versionKey.substring("spot-vol-regime:".length()));
        } else if (versionKey.startsWith("vol-premium-ivrv:")) {
            String ivrvKey = versionKey.substring("vol-premium-ivrv:".length());
            volPremiumIvrv.remove(ivrvKey);
            // THE BROADCAST FENCE GOES WITH THE ENTRY IT FENCES.
            //
            // The fence remembers the greatest offset already broadcast for a key, which is what
            // lets two consumers of one topic agree on who delivers a record. Kept after the cache
            // entry is gone, it stops being a fence and becomes a floor: if the topic is recreated
            // — deleted and remade by an operator, or wiped by the daily reset — offsets start at
            // zero again, and every fresh frame of the SAME session would be silently refused
            // until the offset climbed back past the old incarnation's high-water mark. The cache
            // itself recovers, so late joiners would be served correctly while every already-open
            // page froze: the worst shape, because nothing is failing.
            //
            // WHAT THIS IS AND IS NOT ATOMIC WITH, stated exactly, because the first version of
            // this comment claimed more than the code gives.
            //
            // It is atomic with INGEST: updateCache holds the same instance monitor this method
            // now holds, so the fence is removed with the timestamp, the position and the reading
            // as one unit — a record can never find half an entry.
            //
            // It is NOT atomic with a consumer's BROADCAST DECISION, which runs under the emit
            // lock and not under this monitor, and deliberately so: taking the emit lock here
            // would invert the order both ingest paths use and deadlock the gateway. The window
            // that leaves is benign in the only direction that matters. A purge landing between a
            // consumer's cache write and its shouldBroadcast call can only RESET the fence, so
            // that record broadcasts — never the reverse — and only for an entry already old
            // enough to expire, which a freshly written one is not.
            volPremiumIvrvBroadcastOffset.remove(ivrvKey);
        } else if (versionKey.startsWith("indicators:")) {
            indicatorsCurrent.remove(versionKey.substring("indicators:".length()));
        } else if (versionKey.startsWith("close-direction:")) {
            String cdKey = versionKey.substring("close-direction:".length());
            if (cdKey.contains("|V|")) {
                closeDirectionVerdicts.remove(cdKey);
            } else {
                closeDirectionInterims.remove(cdKey);
            }
        } else if (versionKey.startsWith("zero-dte-intelligence:")) {
            zeroDteIntelligence.remove(versionKey.substring("zero-dte-intelligence:".length()));
        } else if (versionKey.startsWith("strike-flow:")) {
            strikeFlows.remove(versionKey.substring("strike-flow:".length()));
        } else if (versionKey.startsWith("spot-band:")) {
            spotBands.remove(versionKey.substring("spot-band:".length()));
        } else if (versionKey.startsWith("seller-activity:")) {
            sellerActivityStore.remove(versionKey.substring("seller-activity:".length()));
        } else if (versionKey.startsWith("delta-flow:")) {
            deltaFlows.remove(versionKey.substring("delta-flow:".length()));
        } else if (versionKey.startsWith("strike-invasion:")) {
            strikeInvasions.remove(versionKey.substring("strike-invasion:".length()));
        } else if (versionKey.startsWith("strike-intel:")) {
            strikeIntels.remove(versionKey.substring("strike-intel:".length()));
        } else if (versionKey.startsWith("option-truth:")) {
            optionTruths.remove(versionKey.substring("option-truth:".length()));
        } else if (versionKey.startsWith("liquidity-heatmap:")) {
            liquidityHeatmaps.remove(versionKey.substring("liquidity-heatmap:".length()));
        } else if (versionKey.startsWith("mission-pace:")) {
            missionPaces.remove(versionKey.substring("mission-pace:".length()));
        } else if (versionKey.startsWith("mission-control:")) {
            missionControls.remove(versionKey.substring("mission-control:".length()));
        } else if (versionKey.startsWith("spread-skew:")) {
            spreadSkews.remove(versionKey.substring("spread-skew:".length()));
        } else if (versionKey.startsWith("volume-sandwich:")) {
            currentStates.remove(versionKey);
        } else if (versionKey.startsWith("mission-sandwich:")) {
            currentStates.remove(versionKey);
        } else if (versionKey.startsWith("gex-by-strike:")) {
            gexByStrike.remove(versionKey.substring("gex-by-strike:".length()));
        } else if (versionKey.startsWith("gex-oi-status:")) {
            gexOiStatus.remove(versionKey.substring("gex-oi-status:".length()));
        } else if (versionKey.startsWith("ibkr-preopen-status:")) {
            ibkrPreOpenStatus.remove(versionKey.substring("ibkr-preopen-status:".length()));
        } else if (versionKey.startsWith("tapeZones:")) {
            String tapeZonesKey = versionKey.substring("tapeZones:".length());
            tapeZonesBoards.remove(tapeZonesKey);
            tapeZonesPositions.remove(tapeZonesKey);
        } else if (versionKey.startsWith("strike-sr:")) {
            strikeSr.remove(versionKey.substring("strike-sr:".length()));
        } else if (versionKey.startsWith("gex-strike-lifecycle:")) {
            gexStrikeLifecycle.remove(versionKey.substring("gex-strike-lifecycle:".length()));
        } else if (versionKey.startsWith("gex-magnet:")) {
            gexMagnet.remove(versionKey.substring("gex-magnet:".length()));
        } else if (versionKey.startsWith("corridor-gauge:")) {
            corridorGauges.remove(versionKey.substring("corridor-gauge:".length()));
        } else if (versionKey.startsWith("gamma-migration:")) {
            gammaMigration.remove(versionKey.substring("gamma-migration:".length()));
        } else if (versionKey.startsWith("gamma-rotation:")) {
            gammaRotation.remove(versionKey.substring("gamma-rotation:".length()));
        } else if (versionKey.startsWith("gamma-fragility:")) {
            gammaFragility.remove(versionKey.substring("gamma-fragility:".length()));
        } else if (versionKey.startsWith("hot-strike:")) {
            hotStrikes.remove(versionKey.substring("hot-strike:".length()));
        } else if (versionKey.startsWith("es-gex:")) {
            esGex.remove(versionKey.substring("es-gex:".length()));
        } else if (versionKey.startsWith("es-strike-intel:")) {
            esStrikeIntel.remove(versionKey.substring("es-strike-intel:".length()));
        } else if (versionKey.startsWith("max-pain:")) {
            maxPain.remove(versionKey.substring("max-pain:".length()));
        } else if (versionKey.startsWith("option-price-behavior:")) {
            optionPriceBehaviors.remove(versionKey.substring("option-price-behavior:".length()));
        } else if (versionKey.startsWith("dealer-ledger:")) {
            // versionKey = dealer-ledger:SOURCE|symbol|expiry|ROLE. Evict the expired role's RAW record,
            // then REBUILD the envelope from whatever half is still fresh (one fresh half is publishable —
            // a fresh state keeps the pill even after the profile ages out). Only drop the envelope when
            // the rebuild yields nothing (both halves stale/absent). cacheEventTimes for this role was
            // already removed at the top of this method, so joinDealerLedger sees it as not-fresh.
            String roleKey = versionKey.substring("dealer-ledger:".length());
            String baseKey = dealerLedgerBaseKey(roleKey);
            if (roleKey.endsWith("|STATE")) {
                dealerLedgerStates.remove(baseKey);
            } else if (roleKey.endsWith("|PROFILE")) {
                dealerLedgerProfiles.remove(baseKey);
            }
            int sep = baseKey.indexOf('|');
            String source = sep < 0 ? "" : baseKey.substring(0, sep);
            String rebuilt = joinDealerLedger(baseKey, source);
            if (rebuilt == null) {
                dealerLedgers.remove(baseKey);
            } else {
                dealerLedgers.put(baseKey, rebuilt);
            }
        } else if (versionKey.startsWith("opb-by-option:")) {
            opbByOptions.remove(versionKey.substring("opb-by-option:".length()));
        } else if (versionKey.startsWith("opb-session:")) {
            opbSessions.remove(versionKey.substring("opb-session:".length()));
        } else if (versionKey.startsWith("hpsf-latest-signal:")) {
            hpsfLatestSignals.remove(versionKey.substring("hpsf-latest-signal:".length()));
        } else if (versionKey.startsWith("hpsf-market-flow:")) {
            hpsfMarketFlows.remove(versionKey.substring("hpsf-market-flow:".length()));
        } else if (versionKey.startsWith("hpsf-top-candidates:")) {
            hpsfTopCandidates.remove(versionKey.substring("hpsf-top-candidates:".length()));
        } else if (versionKey.startsWith("hpsf-audit:")) {
            hpsfAudits.remove(versionKey.substring("hpsf-audit:".length()));
        } else if (versionKey.startsWith("hpsf-exit-intent:")) {
            hpsfExitIntents.remove(versionKey.substring("hpsf-exit-intent:".length()));
        }
    }

    private String directionalPressureCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            if (!symbol.isBlank() && !expiry.isBlank()) {
                return symbol + "|" + expiry;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    private String paceCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String source = GatewaySettings.normalizeSource(text(root, "marketDataSource"));
            if (source.isBlank()) {
                source = GatewaySettings.normalizeSource(text(root, "source"));
            }
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            double strike = doubleField(root, "strike", Double.NaN);
            if (!source.isBlank() && !symbol.isBlank() && !expiry.isBlank() && Double.isFinite(strike)) {
                return source + "|" + symbol + "|" + expiry + "|" + formatStrike(strike);
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    private String strikeFlowCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            if (!symbol.isBlank() && !expiry.isBlank()) {
                return symbol + "|" + expiry;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * The latest gamma-migration record for one chain, or null when none has arrived.
     *
     * <p>Keyed exactly as {@code updateCache} wrote it — the Kafka record key, which the producer
     * sets to {@code source|symbol|expiry}. Reconstructing the key here rather than scanning the
     * map keeps the read O(1) and, more importantly, makes a producer key change fail loudly as a
     * miss instead of quietly matching the wrong chain.
     *
     * <p>Freshness is the caller's business: the raw record carries {@code eventTimeMs}, and a
     * page that wants to grey out a stalled reading needs the timestamp, not a null.
     */
    /** The chain the app is currently on, as {@code symbol|expiry}, for callers that omit it. */
    public String[] activeSymbolExpiry() {
        ActiveSelection sel = activeSelection.get();
        return new String[]{sel.symbol(), sel.expiry()};
    }

    public String cachedGammaMigration(String symbol, String expiry) {
        return cachedByChain(gammaMigration, symbol, expiry);
    }

    /** The peak-rotation windows and raw move log for one chain, or null when none is cached. */
    public String cachedGammaRotation(String symbol, String expiry) {
        return cachedByChain(gammaRotation, symbol, expiry);
    }

    /** The leader-fragility panel for one chain, or null when none is cached. */
    public String cachedGammaFragility(String symbol, String expiry) {
        return cachedByChain(gammaFragility, symbol, expiry);
    }

    /**
     * ONE key derivation for both. They are written by the same producer under the same key, so
     * two hand-rolled copies could only ever drift apart — and a mismatch here is invisible: the
     * endpoint answers present:false for a chain that is publishing once a second.
     */
    private String cachedByChain(Map<String, String> cache, String symbol, String expiry) {
        if (symbol == null || expiry == null) {
            return null;
        }
        String key = "DATABENTO|" + symbol.trim().toUpperCase(Locale.ROOT) + "|" + normalizeExpiry(expiry);
        return cache.get(key);
    }

    /**
     * Build the chain-shaped seller-activity input consumed by the REST aggregator from the independently
     * cached per-strike records. Seller history deliberately no longer travels inside strike-flow snapshots:
     * doing so made the chain record grow throughout the session until Kafka rejected it.
     */
    public String cachedSellerActivitySnapshot(String symbol, String expiry) {
        if (symbol == null || expiry == null) {
            return null;
        }
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        String normalizedExpiry = normalizeExpiry(expiry);
        long now = System.currentTimeMillis();
        ObjectNode root = mapper.createObjectNode();
        root.put("symbol", normalizedSymbol);
        root.put("expiry", normalizedExpiry);
        var strikes = root.putArray("strikes");
        long asOfMs = 0L;
        int includedBytes = 0;

        long oldest = now - settings.cacheTtlMs();
        List<SellerActivityDiskStore.Stored> records = sellerActivityStore.readChain(
                "DATABENTO", normalizedSymbol, normalizedExpiry, oldest, 1024, 8 * 1024 * 1024);
        for (SellerActivityDiskStore.Stored entry : records) {
            String json = entry.json();
            if (json == null || includedBytes + json.length() > 32 * 1024 * 1024 || strikes.size() >= 1024) {
                break;
            }
            try {
                JsonNode activity = mapper.readTree(json);
                double strike = doubleField(activity, "strike", Double.NaN);
                if (!Double.isFinite(strike)
                        || !normalizedSymbol.equals(text(activity, "symbol").toUpperCase(Locale.ROOT))
                        || !normalizedExpiry.equals(normalizeExpiry(text(activity, "expiry")))) {
                    continue;
                }
                ObjectNode strikeNode = strikes.addObject();
                strikeNode.put("strike", strike);
                ObjectNode sellerActivity = strikeNode.putObject("sellerActivity");
                sellerActivity.set("bucketMinutes", activity.path("bucketMinutes"));
                sellerActivity.set("points", activity.path("points"));
                asOfMs = Math.max(asOfMs, activity.path("timestampMs").asLong(0L));
                includedBytes += json.length();
            } catch (JsonProcessingException ignored) {
                // Ignore one malformed compacted record; other strikes remain independently usable.
            }
        }
        if (strikes.isEmpty()) {
            return null;
        }
        root.put("timestampMs", asOfMs);
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException impossible) {
            return null;
        }
    }

    /**
     * Per-strike delta-flow cache key: {@code symbol|expiry|strike} (mirrors {@link #gexCacheKey},
     * since delta-flow is per-strike not chain-level). Derived from payload identity so the cache key
     * matches the UI contract (source is prepended by updateCache → source|symbol|expiry|strike).
     */
    private String deltaFlowCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            double strike = doubleField(root, "strike", Double.NaN);
            if (!symbol.isBlank() && !expiry.isBlank() && Double.isFinite(strike)) {
                return symbol + "|" + expiry + "|" + formatStrike(strike);
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * Per-strike strike-intel cache key: {@code symbol|expiry|strike} (mirrors {@link #deltaFlowCacheKey},
     * since strike-intel is per-strike not chain-level). Derived from payload identity so the cache key
     * matches the UI contract (source is prepended by updateCache → source|symbol|expiry|strike).
     */
    private String strikeIntelCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            double strike = doubleField(root, "strike", Double.NaN);
            if (!symbol.isBlank() && !expiry.isBlank() && Double.isFinite(strike)) {
                return symbol + "|" + expiry + "|" + formatStrike(strike);
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /** Per-strike, per-horizon Option Truth key: symbol|expiry|strike|horizon. */
    private String optionTruthCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            double strike = doubleField(root, "strike", Double.NaN);
            String horizon = text(root, "horizon").toUpperCase();
            if (!symbol.isBlank() && !expiry.isBlank() && Double.isFinite(strike) && !horizon.isBlank()) {
                return symbol + "|" + expiry + "|" + formatStrike(strike) + "|" + horizon;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * Per-strike, per-direction strike-invasion cache key: {@code symbol|strike|direction} (mirrors
     * {@link #strikeIntelCacheKey} but WITHOUT the expiry segment — strike-invasion is SPX-only and
     * carries no expiry — and WITH the contract-v2 {@code direction} segment). One strike can
     * legitimately carry BOTH a live UP record (SHORT_CALL_CANDIDATE domain) and a DOWN record
     * (SHORT_PUT_CANDIDATE domain) at the same time; keying by symbol|strike alone would let the second
     * record OVERWRITE the first and silence an actionable verdict in the UI. Direction is read from the
     * payload (the Kafka record key shape must not be trusted) via {@link #strikeInvasionDirection} and
     * defaults to {@code UP} when absent/blank, so a pre-v2 (upside-only) record REPLACES the strike's
     * UP entry rather than duplicating it. Derived from payload identity so the cache key matches the
     * UI contract (source is prepended by updateCache → source|symbol|strike|direction). Falls back to
     * the Kafka record key if symbol/strike are absent.
     */
    private String strikeInvasionCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            double strike = doubleField(root, "strike", Double.NaN);
            if (!symbol.isBlank() && Double.isFinite(strike)) {
                return symbol + "|" + formatStrike(strike) + "|" + strikeInvasionDirection(root);
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * Direction segment of the strike-invasion cache key: the payload's contract-v2 {@code direction}
     * field ({@code "UP"}/{@code "DOWN"}), normalized to uppercase for key stability; defaults to
     * {@code UP} when absent/blank because pre-v2 records were upside-only. The gateway never interprets
     * direction beyond the key — the record passes through as raw JSON, so direction reaches the browser
     * untouched.
     */
    private static String strikeInvasionDirection(JsonNode root) {
        String direction = text(root, "direction").toUpperCase();
        return direction.isBlank() ? "UP" : direction;
    }

    private String missionPaceCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            if (!symbol.isBlank() && !expiry.isBlank()) {
                return symbol + "|" + expiry;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    private String missionControlCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            if (!symbol.isBlank() && !expiry.isBlank()) {
                return symbol + "|" + expiry;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * Single-value spread-skew cache key: the {@code underlying} alone (the snapshot is a whole-
     * underlying signal — SPX-only, ONE record, last snapshot wins across expiries). Derived from
     * payload identity so the slot never splits on producer key drift; source is prepended by
     * updateCache (source|underlying). Falls back to the Kafka record key ({@code "SPX"}) if absent.
     */
    private String spreadSkewCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String underlying = text(root, "underlying").toUpperCase();
            if (!underlying.isBlank()) {
                return underlying;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /** True when a gex-oi-status payload carries exactly OI_MISSING or OI_OK (package-visible for tests). */
    boolean isKnownOiStatus(String json) {
        try {
            String status = text(mapper.readTree(json), "status").toUpperCase();
            return "OI_MISSING".equals(status) || "OI_OK".equals(status);
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    private String gexCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            double strike = doubleField(root, "strike", Double.NaN);
            // Derive a deterministic per-strike key from payload identity instead of
            // trusting the producer's Kafka key. Source is prepended by updateCache, so
            // the cache key matches the UI contract (source|symbol|expiry|strike).
            if (!symbol.isBlank() && !expiry.isBlank() && Double.isFinite(strike)) {
                return symbol + "|" + expiry + "|" + formatStrike(strike);
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * Per-strike gamma-lifecycle cache key: {@code symbol|expiry|strike} (mirrors {@link #gexCacheKey};
     * lifecycle is per-strike like gex-by-strike). Source is prepended by updateCache → the cache key
     * matches the UI contract (source|symbol|expiry|strike).
     */
    private String strikeLifecycleCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            double strike = doubleField(root, "strike", Double.NaN);
            if (!symbol.isBlank() && !expiry.isBlank() && Double.isFinite(strike)) {
                return symbol + "|" + expiry + "|" + formatStrike(strike);
            }
        } catch (JsonProcessingException ignored) {
            // fall through to the fail-closed return below
        }
        // FAIL CLOSED (Codex review): a lifecycle badge is meaningless without its own symbol|expiry|strike
        // identity. Falling back to the Kafka key would let a malformed record occupy a cache slot and
        // replay a badge that cannot be matched to any strike — so reject it: updateCache returns null,
        // the record is never cached, never replayed, and strikeLifecycleDropped blocks the live forward.
        return null;
    }

    /** Per-strike gamma-lifecycle payload event time (eventTimeMs); -1 when absent/unparseable. */
    private long strikeLifecycleTimestamp(String json) {
        try {
            return longField(mapper.readTree(json), "eventTimeMs", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    /**
     * Cache key for the per-(symbol,expiry) max-pain stream. The producer key is already
     * {@code symbol|expiry}, but we re-derive it from payload identity to be robust against
     * producer-side key drift. Source is prepended by updateCache so the resulting cache key matches
     * the UI contract (DATABENTO|symbol|expiry).
     */
    /** ES-on-SPX aligned book cache key: {@code symbol|expiry} (the TARGET SPX chain the align service stamps). */
    private String esGexCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            if (!symbol.isBlank() && !expiry.isBlank()) {
                return symbol + "|" + expiry;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to the Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /** Aligned book payload event time (emitEventTimeMs); -1 when absent/unparseable (fail-closed). */
    private long esGexTimestamp(String json) {
        try {
            return longField(mapper.readTree(json), "emitEventTimeMs", -1L);
        } catch (JsonProcessingException ignored) {
            return -1L;
        }
    }

    private String maxPainCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            if (!symbol.isBlank() && !expiry.isBlank()) {
                return symbol + "|" + expiry;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * Cache key for a dealer-ledger source record: {@code symbol|expiry|ROLE} where ROLE is PROFILE or
     * STATE (from the topic). The ROLE suffix keeps the two topics on SEPARATE monotonic event-time
     * gates in updateCache; the join uses the role-stripped {@link #dealerLedgerBaseKey base key}.
     */
    private String dealerLedgerCacheKey(String json, ConsumerRecord<?, ?> record, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            if (!symbol.isBlank() && !expiry.isBlank()) {
                String role = record.topic().equals(settings.dealerLedgerStateTopic()) ? "STATE" : "PROFILE";
                return symbol + "|" + expiry + "|" + role;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /** Strip the trailing {@code |PROFILE}/{@code |STATE} role so both topics share one join/envelope key. */
    private static String dealerLedgerBaseKey(String roleQualifiedKey) {
        int last = roleQualifiedKey.lastIndexOf('|');
        return last < 0 ? roleQualifiedKey : roleQualifiedKey.substring(0, last);
    }

    /**
     * (Re)join the latest cached dealer-ledger profile + state for one chain into the single envelope
     * the UI consumes. Freshness is computed PER ROLE (not from the one triggering record): a STALE
     * state half is dropped so the pill is never emitted from stale state, and a stale profile half is
     * dropped from the book — otherwise a fresh profile arriving next to an old state would publish a
     * stale action pill as fresh. Returns null when neither half is fresh. Never throws.
     */
    private String joinDealerLedger(String baseKey, String source) {
        try {
            long now = System.currentTimeMillis();
            boolean stateFresh = isCacheFresh("dealer-ledger:" + baseKey + "|STATE", now);
            boolean profileFresh = isCacheFresh("dealer-ledger:" + baseKey + "|PROFILE", now);
            String stateJson = stateFresh ? dealerLedgerStates.get(baseKey) : null;
            String profileJson = profileFresh ? dealerLedgerProfiles.get(baseKey) : null;
            if (stateJson == null && profileJson == null) {
                return null; // both halves stale/absent — nothing fresh to publish
            }
            JsonNode profile = profileJson == null ? null : mapper.readTree(profileJson);
            JsonNode state = stateJson == null ? null : mapper.readTree(stateJson);
            // The pill is state-driven; mark the envelope stale whenever the state half isn't fresh
            // (state absent ⇒ book-only, so stale reflects the book/profile half instead).
            boolean stale = stateJson == null ? !profileFresh : false;
            ObjectNode envelope = DealerLedgerJoiner.join(mapper, profile, state, source, settings.maxStaleMs(), stale);
            return envelope == null ? null : mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    /**
     * Legacy-mode (single active selection) cached replay of the dealer-ledger signal on connect.
     * Sent STANDALONE (its own message.type), never batched — mirrors the standalone live delivery.
     * dealerLedgers holds only fresh, non-evicted envelopes, so no extra freshness gate is needed here.
     */
    /**
     * §4.4 cache write, MONOTONIC and ATOMIC: the live and bootstrap consumers run
     * concurrently, so the value map and its timestamp are updated together under
     * one lock and an OLDER record can never displace a newer one — replay after a
     * consumer race stays current.
     * @return true when the incoming record won (caller broadcasts only then).
     */
    private synchronized boolean cacheHotStrike(String symbol, String raw, long timestampMs) {
        Long existing = cacheEventTimes.get("hot-strike:" + symbol);
        if (existing != null && existing > timestampMs) {
            return false;   // stale: a newer record is already cached
        }
        hotStrikes.put(symbol, raw);
        cacheEventTimes.put("hot-strike:" + symbol, timestampMs);
        return true;
    }

    /**
     * §4.4: replay the fresh cached hot-strike envelope(s) — client symbol-filters —
     * gated by the 12h session window so a long-running gateway never resends
     * yesterday's row. Used by BOTH legacy and per-session connect paths.
     */
    private void replayHotStrikeCached(WebSocketSession session) {
        // SNAPSHOT under the same lock as cacheHotStrike so the value/timestamp pair
        // is read atomically (a racing writer can never yield a fresh timestamp with
        // a stale value, or vice versa); sends happen OUTSIDE the lock.
        List<String> freshEnvelopes = new ArrayList<>();
        long hotNowMs = System.currentTimeMillis();
        synchronized (this) {
            for (Map.Entry<String, String> hotEntry : hotStrikes.entrySet()) {
                if (hotEntry.getValue() != null && !hotEntry.getValue().isBlank()
                        && isCacheFresh("hot-strike:" + hotEntry.getKey(), hotNowMs)) {
                    freshEnvelopes.add(hotEntry.getValue());
                }
            }
        }
        for (String envelope : freshEnvelopes) {
            send(session, "hot-strike", envelope);
        }
    }

    private void replayDealerLedgerCached(WebSocketSession session) {
        // Purge first so a stale-half envelope (one role crossed TTL since the last poll purge) is evicted
        // before replay rather than sent to the connecting client.
        purgeExpiredCache(System.currentTimeMillis());
        ActiveSelection selection = activeSelection.get();
        for (Map.Entry<String, String> entry : dealerLedgers.entrySet()) {
            String json = entry.getValue();
            if (json != null && !json.isBlank() && matchesCachedSelection(json, selection)) {
                send(session, "dealer-ledger", json);
            }
        }
    }

    /** Legacy-mode cached replay of the standalone corridor-gauge state on connect. Fresh
     *  entries only; the strip otherwise waits at most one 60s heartbeat for a live frame. */
    private void replayCorridorGaugeCached(WebSocketSession session) {
        // Purge first (dealer-ledger precedent), then per-entry freshness AND active-selection
        // isolation (UI-review r2 #2): a legacy client must not receive another chain's frame.
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        ActiveSelection selection = activeSelection.get();
        for (Map.Entry<String, String> entry : corridorGauges.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("corridor-gauge:" + entry.getKey(), nowMs)) {
                continue;
            }
            // the cache key ENCODES the source (SOURCE|symbol|expiry); the payload itself is
            // source-less, so the key is the only authoritative source check here (r3 #1)
            if (selection == null
                    || !entry.getKey().startsWith(selection.source() + "|")) {
                continue;
            }
            if (!matchesCachedSelection(json, selection)) {
                continue;
            }
            send(session, "corridor-gauge", json);
        }
    }

    private void replayShortPremiumCached(WebSocketSession session) {
        // Purge first so a recommendation that crossed its (12h) TTL since the last poll purge is evicted
        // before replay rather than sent to the connecting client. Unlike dealer-ledger this is intentionally
        // NOT filtered by the active market selection: a recommendation is an advisory overlay filtered by
        // symbol client-side, and replaying all fresh recommendations lets a reload restore the overlay even
        // before the client has (re)selected a market.
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        for (Map.Entry<String, String> entry : shortPremiumRecommendations.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("short-premium-recommendation:" + entry.getKey(), nowMs)) {
                continue;
            }
            send(session, "short-premium-recommendation", json);
        }
    }

    private void replayZeroDteIntelligenceCached(WebSocketSession session) {
        // Global standalone state, filtered by symbol/session in the browser. Purge + per-entry
        // freshness checks are intentional defence in depth: reconnect must never restore an expired
        // unusual-movement tint while the periodic purge is between cycles.
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        for (Map.Entry<String, String> entry : zeroDteIntelligence.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("zero-dte-intelligence:" + entry.getKey(), nowMs)) {
                continue;
            }
            send(session, "zero-dte-intelligence", json);
        }
    }

    private void replayCloseDirectionCached(WebSocketSession session) {
        // Late-join delivery for the close-direction advisory: replay the session's frozen VERDICT
        // (long closeDirectionTtlMs window — a client connecting at 15:55 must still see the 15:49
        // verdict) and, when no verdict exists yet, the latest interim ONLY while it is current
        // (asOfMs within closeDirectionInterimFreshMs — a stale interim reads as absent, never live).
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        for (Map.Entry<String, String> entry : closeDirectionVerdicts.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("close-direction:" + entry.getKey(), nowMs)) {
                continue;
            }
            send(session, "close-direction", json);
        }
        for (Map.Entry<String, String> entry : closeDirectionInterims.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            int marker = entry.getKey().indexOf("|I|");
            if (marker >= 0 && closeDirectionVerdicts.containsKey(
                    entry.getKey().substring(0, marker) + "|V|"
                            + entry.getKey().substring(marker + 3))) {
                continue;   // verdict wins — the interim is history
            }
            if (!isCacheFresh("close-direction:" + entry.getKey(), nowMs)) {
                continue;
            }
            try {
                long asOfMs = mapper.readTree(json).path("asOfMs").asLong(0);
                if (asOfMs <= 0 || nowMs - asOfMs > settings.closeDirectionInterimFreshMs()) {
                    continue;   // stale interim = absent (design CD-R30 short class)
                }
            } catch (JsonProcessingException e) {
                continue;
            }
            send(session, "close-direction", json);
        }
    }

    private void replayGreekMoveAuthCached(WebSocketSession session) {
        // Late-join delivery for the greek-move-authenticity CURRENT verdict: replay the cached verdict
        // per symbol (SPX/ES live under distinct symbol keys, so both current verdicts survive
        // side-by-side). Like the open-direction siblings this is intentionally NOT filtered by the active
        // market selection — it is a GLOBAL advisory the UI renders in its own move-authenticity track,
        // symbol-filtered client-side. The purge-first + isCacheFresh gates run on the SHORT
        // greekMoveAuthTtlMs window (5 min via cachePolicyFor): a late joiner gets the CURRENT verdict
        // only; anything older is simply absent and the UI track stays hidden.
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        for (Map.Entry<String, String> entry : greekMoveAuthCurrent.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("greek-move-auth:" + entry.getKey(), nowMs)) {
                continue;
            }
            send(session, "greek-move-auth", json);
        }
    }

    /**
     * Late-join delivery for the vol-premium IV/RV reading: same GLOBAL advisory class as
     * {@link #replaySpotVolRegimeCached} — deliberately NOT filtered by the active market
     * selection, and symbol-filtered client-side. Purge-first plus the SHORT window means a late
     * joiner gets the CURRENT reading only; anything older is simply absent and the chart shows no
     * current point rather than a stale one.
     */
    private void replayVolPremiumIvrvCached(WebSocketSession session) {
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        // The (cache-read -> send-enqueue) pair is atomic under the emit lock, so a live update
        // either lands BEFORE (and replay reads the newer value) or AFTER (and its enqueue
        // supersedes ours via coalescing). The stale ordering — live N+1 enqueued, then replayed
        // N enqueued over it under the same coalescing key, leaving the socket on the older value
        // until some later frame happens to arrive — cannot happen.
        synchronized (volPremiumIvrvEmitLock) {
            for (Map.Entry<String, String> entry : volPremiumIvrv.entrySet()) {
                String json = entry.getValue();
                if (json == null || json.isBlank()) {
                    continue;
                }
                if (!isCacheFresh("vol-premium-ivrv:" + entry.getKey(), nowMs)) {
                    continue;
                }
                send(session, "vol-premium-ivrv", json);
            }
        }
    }

    private void replaySpotVolRegimeCached(WebSocketSession session) {
        // Late-join delivery for the spot-vol-regime CURRENT snapshot: same GLOBAL advisory class as
        // replayGreekMoveAuthCached above — intentionally NOT filtered by the active market selection,
        // symbol-filtered client-side. Purge-first + isCacheFresh run on the SHORT spotVolRegimeTtlMs
        // window: a late joiner gets the CURRENT regime only; anything older is simply absent and the
        // UI regime pill stays hidden.
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        for (Map.Entry<String, String> entry : spotVolRegime.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("spot-vol-regime:" + entry.getKey(), nowMs)) {
                continue;
            }
            // Session-scoped latch: the strike marking describes ONE RTH session, and the 5-minute TTL
            // outlives the close by 4 minutes. Strip the band (never the snapshot) once the session is
            // over so a browser opened at 16:01 does not late-join into a coloured chain.
            send(session, "spot-vol-regime", suppressStrikeBandAfterClose(json, nowMs));
        }
    }

    /**
     * Late-join delivery for indicator CURRENT snapshots (rev 14 §8): PER-SYMBOL —
     * both ES and SPX replay independently; GLOBAL advisory class, symbol-filtered
     * client-side; SHORT-TTL fresh-gated so a dead producer reads as absent.
     */
    private void replayIndicatorsCached(WebSocketSession session) {
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        // r1 finding 3: the (cache-read → send-enqueue) pair is atomic under the
        // emit lock — a live update either lands before (replay reads the newer
        // value) or after (its enqueue supersedes ours via coalescing). The stale
        // ordering "live N+1 enqueued, then replayed N enqueued" cannot happen.
        synchronized (indicatorsEmitLock) {
            for (Map.Entry<String, String> entry : indicatorsCurrent.entrySet()) {
                String json = entry.getValue();
                if (json == null || json.isBlank()) {
                    continue;
                }
                if (!isCacheFresh("indicators:" + entry.getKey(), nowMs)) {
                    continue;
                }
                send(session, "indicators", json);
            }
        }
    }

    /**
     * Canonical symbol (ES|SPX) keys the indicator cache — STRICT (r1 finding 6):
     * the payload symbol must be exactly ES or SPX AND equal the Kafka record key
     * (a keyed-SPX record claiming ES may never overwrite ES state); the frame must
     * carry schemaVersion 1, a bounded runId, an INTEGRAL non-negative revision and
     * a parseable publishedAt. Anything else is dropped (null), never cached or
     * forwarded.
     */
    private String indicatorsCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol");
            if (!"ES".equals(symbol) && !"SPX".equals(symbol)) {
                return null;
            }
            if (fallback != null && !symbol.equals(fallback)) {
                return null; // Kafka key / payload identity mismatch — poisoning guard
            }
            JsonNode sv = root.get("schemaVersion");
            if (sv == null || !sv.isIntegralNumber() || !sv.canConvertToInt()
                    || sv.asInt() != 1) {
                return null;
            }
            JsonNode runNode = root.get("runId");
            if (runNode == null || !runNode.isTextual()) {
                return null; // r3 finding 2: a numeric/boolean runId is type-invalid
            }
            String runId = runNode.asText();
            if (runId.isBlank() || runId.length() > 64) {
                return null;
            }
            JsonNode rev = root.get("revision");
            if (rev == null || !rev.isIntegralNumber() || !rev.canConvertToLong()
                    || rev.asLong() < 0) {
                return null;
            }
            String publishedAt = text(root, "publishedAt");
            java.time.Instant.parse(publishedAt);
            return symbol;
        } catch (Exception malformed) {
            return null;
        }
    }

    /** Payload publish time (publishedAt ISO) drives freshness; -1 = malformed. */
    private long indicatorsTimestamp(String json) {
        try {
            String publishedAt = text(mapper.readTree(json), "publishedAt");
            if (publishedAt.isBlank()) {
                return -1L;
            }
            long ms = java.time.Instant.parse(publishedAt).toEpochMilli();
            if (ms > System.currentTimeMillis() + SPOT_VOL_REGIME_MAX_FUTURE_SKEW_MS) {
                return -1L; // implausibly future — fail closed
            }
            return ms;
        } catch (Exception ignored) {
            return -1L;
        }
    }

    /** Rev 14 §6.9 cross-run ordering; returns false on any regression. */
    private boolean indicatorsSupersedes(String symbol, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode runNode = root.get("runId");
            if (runNode == null || !runNode.isTextual()) {
                return false; // r3 finding 2: strict schema — runId is a string
            }
            String runId = runNode.asText();
            JsonNode revNode = root.get("revision");
            // r1 finding 6: revision must be an INTEGRAL JSON number — a textual
            // number is a schema violation, never coerced.
            if (runId.isBlank() || runId.length() > 64 || revNode == null
                    || !revNode.isIntegralNumber() || !revNode.canConvertToLong()
                    || revNode.asLong() < 0) {
                return false;
            }
            long revision = revNode.asLong();
            synchronized (indicatorsRevision) {
                String currentRun = indicatorsRunId.get(symbol);
                if (runId.equals(currentRun)) {
                    Long last = indicatorsRevision.get(symbol);
                    if (last != null && revision <= last) {
                        return false; // stale revision within the active run
                    }
                } else {
                    if (indicatorsRetiredRuns.contains(symbol + "|" + runId)) {
                        return false; // a retired run may never return
                    }
                    if (currentRun != null) {
                        // r3 finding 1: the never-return rule is ABSOLUTE — eviction
                        // is forbidden. At the (operationally unreachable) cap the
                        // gateway FAILS CLOSED: no further run transitions are
                        // accepted, the current run keeps serving, and memory stays
                        // bounded. A retired run can never re-enter.
                        synchronized (indicatorsRetiredRuns) {
                            if (indicatorsRetiredRuns.size() >= INDICATORS_RETIRED_RUNS_CAP) {
                                return false;
                            }
                            indicatorsRetiredRuns.add(symbol + "|" + currentRun);
                        }
                    }
                    indicatorsRunId.put(symbol, runId);
                }
                indicatorsRevision.put(symbol, revision);
            }
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /** The wrapped wire form for the pre-open status stream: the Kafka record key IS the
     *  identity (strike row "SPX|D|6300" vs control "__path|D") and the OFFSET is the ordering
     *  token — replay and live deliveries can interleave at the socket, so the client renders
     *  last-writer-wins per (recordKey, offset) and a stale replay can never override a newer
     *  live delta. {@code timestampMs} is the status record's broker CreateTime — the R-STOP
     *  pairing proof needs BOTH halves of a frozen pair committed before the fence (round-1
     *  finding 4), and the cache stores only this wrap. The producer payload rides byte-untouched. */
    static String wrapIbkrPreOpenStatus(String recordKey, long offset, long timestampMs, String json) {
        StringBuilder sb = new StringBuilder("{\"recordKey\":\"");
        appendJsonEscaped(sb, recordKey);
        return sb.append("\",\"offset\":").append(offset)
                .append(",\"timestampMs\":").append(timestampMs)
                .append(",\"status\":").append(json).append('}').toString();
    }

    /** Full JSON string escaping (quotes/backslash/newline/CR/tab + every U+0000-001F as \\uXXXX)
     *  for the pre-open wrap keys — shared by the status wrap and the gex value/eviction wraps. */
    private static void appendJsonEscaped(StringBuilder sb, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }

    /** Exactly-one, in-order live delivery per offset across the TWO ingesting consumers
     *  (cache + live): whichever consumer reaches an offset FIRST broadcasts it; the loser's
     *  duplicate — and any lower offset — is suppressed. Single-partition contract. */
    private final java.util.concurrent.atomic.AtomicLong ibkrPreOpenBroadcastOffset =
            new java.util.concurrent.atomic.AtomicLong(-1L);

    boolean shouldBroadcastIbkrPreOpen(long offset) {
        while (true) {
            long current = ibkrPreOpenBroadcastOffset.get();
            if (offset <= current) {
                return false;
            }
            if (ibkrPreOpenBroadcastOffset.compareAndSet(current, offset)) {
                return true;
            }
        }
    }

    /** Re-push the fresh pre-open window state to one client (standalone advisory class). */
    private void replayIbkrPreOpenCached(WebSocketSession session) {
        long nowMs = System.currentTimeMillis();
        for (Map.Entry<String, String> entry : ibkrPreOpenStatus.entrySet()) {
            String wrapped = entry.getValue();
            if (wrapped == null || wrapped.isBlank()) {
                continue;
            }
            if (!isCacheFresh("ibkr-preopen-status:" + entry.getKey(), nowMs)) {
                continue;
            }
            send(session, "ibkr-preopen-status", wrapped);
        }
    }

    // ==================================================================================
    // Pre-open IBKR GEX value plane — rev13 Phase 3 slice 2 (R-ARB arbitration + the R-STOP
    // frozen-projection cache). The shared live topic (USER D14) carries BOTH planes; the
    // gateway separates them by the validated exact provenance tuple and never relabels
    // provenance: a sessioned payload rides byte-untouched inside the wrap, and a Databento
    // record is never filtered, conditioned or re-styled (D11).
    // ==================================================================================

    /** One sessioned value record on the shared topic — a live candidate before its fence
     *  (validUntilMs, 09:25 ET of its session) or a frozen projection after it. */
    private record IbkrPreOpenGexCandidate(
            String recordKey, String identity, int partition, long offset,
            String sessionId, long outputGeneration, long baselineEpoch, long recordRevision,
            long validUntilMs, long recordTimestampMs, String json) {
    }

    /** R-WIRE.1 enforcement scoping for records on the shared live gex topic. */
    enum SharedGexClass { DATABENTO, IBKR_PREOPEN, PREOPEN, UNKNOWN_SESSIONED }

    /** The freeze window: fence (09:25) -> Databento takeover boundary (09:30). */
    private static final long IBKR_PREOPEN_FREEZE_WINDOW_MS = 5 * 60_000L;
    /** Full projection lifetime: fence (09:25) -> unconditional eviction (09:35, O5/D12). */
    private static final long IBKR_PREOPEN_EVICT_AFTER_MS = 10 * 60_000L;
    /** Fail-closed sanity bound on validUntilMs: a fence more than a day ahead is a producer
     *  defect, and admitting it would pin a candidate in memory with no reachable window. */
    private static final long IBKR_PREOPEN_MAX_FUTURE_VALIDITY_MS = 24 * 3_600_000L;

    /**
     * Classify a record on the shared live gex topic by its EXACT provenance tuple (rev13
     * R-WIRE.1). The special arbitration/validity/generation rules apply ONLY to the two
     * validated tuples; a record that CLAIMS a session in any way (non-blank sessionId, or a
     * sessioned source/timeframe value in any casing) without forming a valid tuple fails
     * closed as UNKNOWN_SESSIONED — dropped, never presented, never conditioning Databento.
     * Everything else is a Databento record and BYPASSES this feature entirely.
     */
    static SharedGexClass classifySharedGexRecord(JsonNode root) {
        String source = text(root, "source");
        String timeframe = text(root, "timeframe");
        String sessionId = text(root, "sessionId");
        boolean claimsSession = !sessionId.isBlank()
                || "IBKR".equalsIgnoreCase(source) || "PREOPEN".equalsIgnoreCase(source)
                || "IBKR_PREOPEN".equalsIgnoreCase(timeframe) || "PREOPEN".equalsIgnoreCase(timeframe);
        if (!claimsSession) {
            return SharedGexClass.DATABENTO;
        }
        // Valid tuples are matched CASE-SENSITIVELY **and WHITESPACE-EXACTLY**: the wire contract
        // pins the exact values, and a mis-cased or padded tuple must fail closed rather than be
        // "helpfully" normalized into validity. text() trims, so " IBKR " would otherwise qualify;
        // the raw node values are re-read here to make the exactness real.
        String rawSource = root.path("source").isTextual() ? root.path("source").asText() : source;
        String rawTimeframe = root.path("timeframe").isTextual()
                ? root.path("timeframe").asText() : timeframe;
        if (!rawSource.equals(source) || !rawTimeframe.equals(timeframe)) {
            return SharedGexClass.UNKNOWN_SESSIONED; // padded/asymmetric tuple -> fail closed
        }
        if ("IBKR".equals(source) && "IBKR_PREOPEN".equals(timeframe) && !sessionId.isBlank()) {
            return SharedGexClass.IBKR_PREOPEN;
        }
        if ("PREOPEN".equals(source) && "PREOPEN".equals(timeframe)) {
            return SharedGexClass.PREOPEN;
        }
        return SharedGexClass.UNKNOWN_SESSIONED;
    }

    /**
     * The ONE classification chokepoint for a shared-topic reader that only needs the
     * Databento-plane / sessioned split (round-3 finding 3: cache, live AND historical-replay
     * readers must share {@link #classifySharedGexRecord}'s R-WIRE.1 tuple rules). Returns
     * {@code true} when the record is NOT an ordinary Databento record: an exact IBKR tuple
     * belongs to the pre-open plane only, and PREOPEN / UNKNOWN_SESSIONED tuples fail closed —
     * none of them may ever enter the ordinary gex-by-strike plane. Malformed / non-object JSON
     * cannot claim a session and stays on the Databento plane, exactly like the live reader.
     */
    boolean isSessionedSharedGexJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(rawJson);
            return root instanceof ObjectNode
                    && classifySharedGexRecord(root) != SharedGexClass.DATABENTO;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Arbitration entry point, called by BOTH shared-topic consumers BEFORE {@link #enrichJson}
     * (a sessioned record must never be stamped with the topic binding's DATABENTO provenance).
     *
     * @return {@code true} when the record was fully handled on the pre-open plane (or dropped
     *         fail-closed) — the caller skips it; {@code false} for a genuine Databento record,
     *         which falls through to the EXISTING pipeline byte-untouched.
     */
    private boolean interceptSharedGexRecord(
            ConsumerRecord<String, Object> record, String rawJson, boolean liveBroadcast, long nowMs) {
        boolean liveTopic = record.topic().equals(settings.databentoGexTopic());
        if (rawJson == null || rawJson.isBlank()) {
            if (liveTopic) {
                trackIbkrPreOpenObservedOffset(record);
            }
            return false; // existing pipeline handles empty values (tombstone/eviction paths)
        }
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            if (liveTopic) {
                trackIbkrPreOpenObservedOffset(record);
            }
            return false; // not JSON — cannot claim a session; existing pipeline behavior unchanged
        }
        if (!(root instanceof ObjectNode)) {
            if (liveTopic) {
                trackIbkrPreOpenObservedOffset(record);
            }
            return false;
        }
        SharedGexClass cls = classifySharedGexRecord(root);
        if (cls == SharedGexClass.DATABENTO) {
            if (liveTopic) {
                // Apply the per-strike takeover eviction, THEN track the observed high-watermark —
                // the only things this feature ever does with a Databento record (the record
                // itself is never touched). Order is load-bearing: if this record is the first
                // one past the 09:30 boundary AND the sweep snapshots the watermark lazily right
                // now, merging the record's own offset first would put it AT the watermark and
                // self-suppress the takeover it should trigger.
                // PUBLISH THE OBSERVATION FIRST (round-4 finding 2). On a fresh restart the
                // live consumer can observe a post-boundary record while the rewinding cache
                // consumer is reconstructing the same strike; publishing after the check left a
                // gap in which reconstruction saw no observation and rebuilt a projection the
                // eviction below would never revisit. Recording first means the reconstruction
                // either sees the observation and tombstones, or completes before it and is
                // caught by the eviction — never neither.
                if (record.timestamp() > 0L) {
                    String observed = gexIdentityFromNode(root,
                            record.key() == null ? "" : String.valueOf(record.key()));
                    IbkrPreOpenDatabentoObservation incoming = new IbkrPreOpenDatabentoObservation(
                            record.timestamp(), record.partition(), record.offset());
                    ibkrPreOpenDatabentoMaxCommitMs.merge(observed, incoming,
                            (prev, next) -> next.commitMs() >= prev.commitMs() ? next : prev);
                }
                maybeEvictIbkrProjectionOnTakeover(record, root, nowMs);
                trackIbkrPreOpenObservedOffset(record);
            }
            return false;
        }
        if (liveTopic) {
            // EVERY observed record advances the partition's high-watermark, whatever its class
            // (round-1 finding 5): the 09:30 snapshot must be the gateway's real observed POSITION
            // on the partition, or a delayed sibling-consumer delivery whose offset sits between
            // the last Databento record and the true position would read as "newly ingested".
            // For sessioned/malformed records there is no takeover interplay, so order is free.
            trackIbkrPreOpenObservedOffset(record);
        }
        if (!liveTopic || cls == SharedGexClass.PREOPEN || cls == SharedGexClass.UNKNOWN_SESSIONED) {
            // Fail closed, never presented (R-WIRE.1). PREOPEN is a VALID tuple, but with this
            // feature enabled the environment's trade-date owner is IBKR_PREOPEN (R-PREOPEN's
            // conflict domain admits exactly one owner and R-FLAGS forbids dual publish), so a
            // PreOpen record here is a conflicting non-owner: presenting it as a live candidate
            // could be a wrong number. A sessioned record on the JSON HISTORY topic is likewise
            // dropped — the pre-open plane admits records only from the live Avro topic.
            ibkrPreOpenGexDroppedSessioned.incrementAndGet();
            return true;
        }
        String wrapped = ingestIbkrPreOpenGexValue(record, root, rawJson, nowMs);
        if (wrapped != null && liveBroadcast && ibkrPreOpenGexServingUp()
                && shouldBroadcastIbkrPreOpenGex(record.partition(), record.offset())) {
            // Live delivery mirrors slice 1: the live consumer broadcasts, the per-partition
            // offset CAS gate keeps delivery exactly-once and in-order per partition even when
            // the sibling cache consumer won the admission race (the wrap of the CURRENT state
            // is returned for an exact duplicate, so the delivery is never suppressed). The
            // dual-barrier check sits BEFORE the CAS (round-2 finding 1): a value must never
            // reach a client until the state consumer has reconstructed the revocation/
            // generation controls, and a suppressed offset must not consume the CAS gate.
            broadcast("ibkr-preopen-gex", wrapped);
            forwardedEvents.incrementAndGet();
        }
        return true;
    }

    /**
     * Admit one valid IBKR_PREOPEN value record into the plane.
     *
     * <p>BEFORE its fence the record is a LIVE candidate (last-value-wins per strike, strictly
     * increasing broker offset on the strike's own partition). AT/AFTER the fence a record can
     * only serve frozen-projection RECONSTRUCTION (R-STOP recovery): it must have been COMMITTED
     * before the fence (broker CreateTime), the window must still be alive (before fence+10min),
     * and it must be a revision-EQUAL number-bearing value+status pair — the projection converges
     * to the latest pre-fence pair regardless of observation order, which is exactly the state a
     * live-running gateway captured at the fence. A record first COMMITTED at/after the fence can
     * never enter the projection, and after 09:35 nothing ever reappears.
     *
     * @return the wrap representing the plane's CURRENT state for this strike when the record is
     *         (or exactly duplicates) that state; {@code null} when rejected.
     */
    private String ingestIbkrPreOpenGexValue(
            ConsumerRecord<String, Object> record, JsonNode root, String rawJson, long nowMs) {
        String recordKey = record.key() == null ? "" : String.valueOf(record.key());
        String sessionId = text(root, "sessionId");
        long validUntilMs = longField(root, "validUntilMs", 0L);
        long outputGeneration = longField(root, "outputGeneration", 0L);
        long baselineEpoch = longField(root, "baselineEpoch", 0L);
        long recordRevision = longField(root, "recordRevision", 0L);
        if (recordKey.isBlank() || validUntilMs <= 0L
                || validUntilMs > nowMs + IBKR_PREOPEN_MAX_FUTURE_VALIDITY_MS) {
            // No identity or no provable validity bound -> fail closed (never presented).
            ibkrPreOpenGexDroppedSessioned.incrementAndGet();
            return null;
        }
        // FAIL CLOSED ON THE CONTRACT, not merely on the tuple (round-5 finding 3). A record can
        // carry the exact source/timeframe pair and still be malformed: the session id has a pinned
        // shape (IBKR_PREOPEN:<yyyy-MM-dd>), the ordering fields are 1-based so a zero/absent
        // generation, epoch or revision is an UNVERSIONED record that R-WIRE.5 cannot order, and the
        // payload's own identity must agree with the Kafka key it arrived under — otherwise one
        // strike's record could be admitted under another's partitioned key.
        String rawSessionId = root.path("sessionId").isTextual() ? root.path("sessionId").asText() : sessionId;
        if (!rawSessionId.equals(sessionId)                       // text() trims — a padded id is NOT exact
                || !isIbkrPreOpenSessionId(sessionId)             // shape AND a real calendar date
                || outputGeneration <= 0L || baselineEpoch <= 0L || recordRevision <= 0L) {
            ibkrPreOpenGexDroppedSessioned.incrementAndGet();
            return null;
        }
        // TIMESTAMP PROVENANCE (round-6 finding 1). The shared gex topic is CreateTime — the broker
        // default, verified on the running cluster — so record.timestamp() is the PRODUCER's clock,
        // not a broker commit stamp. R-STOP's "committed before the fence" is therefore proved here
        // against a first-party clock: every IBKR_PREOPEN record on this topic is written by our own
        // databento-gex-service, and the window/validUntil guards independently bound how far a
        // record can be trusted. That is the honest scope of the proof, and it is NOT sound for a
        // third-party producer — pinning message.timestamp.type=LogAppendTime on this topic would
        // make it a broker fact, but that timestamp is shared with the existing Databento RTH
        // pipeline (gamma-flow event times), so it is a separate, deliberate change.
        //
        // Rejecting non-LOG_APPEND_TIME outright was tried and reverted: on a CreateTime topic it
        // disables the entire window rather than hardening it.
        ibkrPreOpenObservedFences.add(validUntilMs);
        String identity = gexIdentityFromNode(root, recordKey);
        if (!identity.equals(gexIdentityFromNode(root, ""))
                || !identityMatchesRecordKey(identity, recordKey)) {
            // The canonical payload identity disagrees with the key the record was partitioned
            // under: the key->partition contract every offset comparison relies on is broken.
            ibkrPreOpenGexDroppedSessioned.incrementAndGet();
            return null;
        }
        IbkrPreOpenGexCandidate incoming = new IbkrPreOpenGexCandidate(
                recordKey, identity, record.partition(), record.offset(), sessionId,
                outputGeneration, baselineEpoch, recordRevision, validUntilMs,
                record.timestamp(), rawJson);
        String sessionDate = ibkrPreOpenSessionDate(sessionId);
        synchronized (ibkrPreOpenGexLock) {
            if (ibkrPreOpenRevokedGenerations.contains(sessionDate + "|" + outputGeneration)) {
                ibkrPreOpenGexRejected.incrementAndGet();
                return null;
            }
            Long maxGen = ibkrPreOpenMaxGeneration.get(sessionDate);
            if (maxGen != null && outputGeneration < maxGen) {
                ibkrPreOpenGexRejected.incrementAndGet(); // old-generation straggler (R-WIRE.5/.6)
                return null;
            }
            if (maxGen == null || outputGeneration > maxGen) {
                // SUPERSESSION NEEDS PROOF (round-4 finding 1). The generation still advances —
                // the producer HAS moved on, so later lower-generation records are stragglers —
                // but EVICTING the lower generation's proven state is destructive and may only be
                // driven by a record that proves ITSELF: its own broker commit time before its
                // fence, the same proof the reconstruction branch below demands. Consumer loops
                // process a whole BATCH before sweeping, so an unproven gen-4 committed at the
                // fence can reach here while gen-3 is still the valid paired live candidate;
                // evicting on it destroyed gen-3 and then refused to capture gen-4, losing the
                // frozen projection outright. Admission itself is unchanged: a live candidate is
                // governed by the gateway clock, and only the FENCE CAPTURE needs the commit proof.
                boolean provesItself = incoming.recordTimestampMs() > 0L
                        && incoming.recordTimestampMs() < validUntilMs;
                if (maxGen != null && !provesItself) {
                    // An unproven higher generation must not ADVANCE the max either (round-5
                    // finding 2). Advancing it turns every later pre-fence reconstruction record of
                    // the current generation into a "straggler" and makes the unconditional
                    // max-generation check delete pending pairs that were about to complete —
                    // destroying, by a different route, exactly the state gating the eviction saved.
                    // The producer's authoritative "the generation moved" signal is the
                    // __generation CONTROL, not an unproven value record. Scoped to an actual
                    // ADVANCE (maxGen != null): the first generation observed strands nothing, and
                    // live admission stays gateway-clock governed — only the capture needs proof.
                    ibkrPreOpenGexRejected.incrementAndGet();
                    return null;
                }
                ibkrPreOpenMaxGeneration.put(sessionDate, outputGeneration);
                if (maxGen != null) {
                    // A NEW output generation supersedes every lower one (R-WIRE.2/.6): evict the
                    // lower-generation LIVE candidates and pending pairs, and TELL connected
                    // clients — an eviction is a state transition (the non-drop control class),
                    // broadcast exactly once because the eviction happens once. The FROZEN cache
                    // is deliberately untouched (round-2 finding 4): R-STOP makes it
                    // NON-CANDIDATE state that generation supersession never reaches — a proven
                    // frozen pair is replaced only when the newer generation PROVES its own
                    // pre-fence pair (below / pending promotion), revoked by __revocation, taken
                    // over by Databento, or destroyed at 09:35.
                    for (String wrapped : evictIbkrPreOpenSupersededLocked(
                            sessionDate, outputGeneration)) {
                        broadcastIbkrPreOpenGex(wrapped);
                    }
                }
            }
            if (nowMs < validUntilMs) {
                IbkrPreOpenGexCandidate previous = ibkrPreOpenGexCandidates.get(identity);
                if (previous != null) {
                    if (previous.partition() == incoming.partition()
                            && previous.offset() == incoming.offset()) {
                        // The sibling consumer's exact duplicate of the CURRENT state: not an
                        // admission, but the live delivery must not be suppressed (slice-1
                        // round-4 lesson) — return the current wrap for the CAS-gated broadcast.
                        return wrapIbkrPreOpenGex(previous.recordKey(), previous.offset(), "LIVE",
                                previous.json());
                    }
                    if (previous.partition() != incoming.partition()
                            || incoming.offset() <= previous.offset()) {
                        // Regressed offset, or the same strike on a DIFFERENT partition (the
                        // key->partition contract is broken): fail closed, never reorder.
                        ibkrPreOpenGexRejected.incrementAndGet();
                        return null;
                    }
                }
                ibkrPreOpenGexCandidates.put(identity, incoming);
                return wrapIbkrPreOpenGex(recordKey, incoming.offset(), "LIVE", rawJson);
            }
            // At/after the fence: frozen-projection reconstruction only.
            if (nowMs >= validUntilMs + IBKR_PREOPEN_EVICT_AFTER_MS) {
                ibkrPreOpenGexRejected.incrementAndGet(); // window over — evicted values never reappear
                return null;
            }
            if (incoming.recordTimestampMs() <= 0L || incoming.recordTimestampMs() >= validUntilMs) {
                ibkrPreOpenGexRejected.incrementAndGet(); // committed at/after the fence — never enters
                return null;
            }
            if (isIbkrPreOpenStrikeTakenOverLocked(identity, validUntilMs)) {
                // The strike is terminally Databento-owned — its projection was evicted by a
                // takeover, or a post-boundary Databento record was observed before this value
                // (fresh-restart race, round-3 finding 2). A late-observed pre-fence value
                // (compacted redelivery / rewinding cache consumer) can never resurrect it.
                ibkrPreOpenGexRejected.incrementAndGet();
                return null;
            }
            if (!isIbkrPreOpenPairedNumberBearing(incoming)) {
                // NOT a terminal reject: the revision-equal status may simply not have been
                // OBSERVED yet (independent consumers — a restarted gateway can read the value
                // first, round-1 finding 1). Hold the value pending; the status's arrival (or the
                // next sweep) completes the pair. Unpaired at 09:35 -> frozen-blank forever.
                stashIbkrPreOpenPendingLocked(incoming);
                return null;
            }
            IbkrPreOpenGexCandidate previous = ibkrPreOpenFrozenProjections.get(identity);
            if (previous != null) {
                if (previous.partition() == incoming.partition()
                        && previous.offset() == incoming.offset()) {
                    return wrapIbkrPreOpenGex(previous.recordKey(), previous.offset(), "FROZEN",
                            previous.json());
                }
                if (previous.partition() != incoming.partition()
                        || incoming.offset() <= previous.offset()) {
                    ibkrPreOpenGexRejected.incrementAndGet();
                    return null;
                }
            }
            ibkrPreOpenFrozenProjections.put(identity, incoming);
            return wrapIbkrPreOpenGex(recordKey, incoming.offset(), "FROZEN", rawJson);
        }
    }

    /**
     * Window transitions, driven from both consumer poll loops and every replay path (consumer-
     * local: they fire even when no record arrives). Idempotent and cheap when the plane is empty.
     *
     * <ol>
     *   <li><b>Fence capture (09:25):</b> an ATOMIC transition per fence — every candidate whose
     *       fence has passed is either captured into the frozen-projection cache (revision-EQUAL
     *       number-bearing value+status pair) or dropped (frozen-blank), and the live candidate is
     *       invalidated. Captured strikes are broadcast as FROZEN so connected clients flip their
     *       per-strike SWITCHING alerts without waiting for a reconnect.</li>
     *   <li><b>Takeover boundary (09:30):</b> the per-partition observed high-watermark is
     *       snapshotted once per fence; only a Databento record BEYOND it (and committed at/after
     *       the boundary) counts as newly-ingested for the per-strike takeover.</li>
     *   <li><b>Destruction (09:35):</b> every remaining projection of the fence is evicted
     *       (broadcast EVICTED/WINDOW_END) and the fence's bookkeeping is released — frozen
     *       values die forever (no post-window resurrection, R-ARB).</li>
     * </ol>
     */
    void sweepIbkrPreOpenGexWindows(long nowMs) {
        if (ibkrPreOpenGexCandidates.isEmpty() && ibkrPreOpenFrozenProjections.isEmpty()
                && ibkrPreOpenTakeoverWatermarks.isEmpty()
                && ibkrPreOpenPendingProjections.isEmpty()
                && ibkrPreOpenTakenOverStrikes.isEmpty()
                && ibkrPreOpenDatabentoMaxCommitMs.isEmpty()) {
            return;
        }
        List<String> broadcasts = new ArrayList<>();
        synchronized (ibkrPreOpenGexLock) {
            // 1. Fence capture — atomic per fence: capture-then-invalidate under the one lock.
            for (Iterator<Map.Entry<String, IbkrPreOpenGexCandidate>> it =
                    ibkrPreOpenGexCandidates.entrySet().iterator(); it.hasNext(); ) {
                IbkrPreOpenGexCandidate candidate = it.next().getValue();
                if (nowMs < candidate.validUntilMs()) {
                    continue;
                }
                it.remove(); // the live candidate is invalidated at the fence, captured or not
                if (nowMs >= candidate.validUntilMs() + IBKR_PREOPEN_EVICT_AFTER_MS) {
                    continue; // whole window already over (gateway slept through it) — dead
                }
                if (candidate.recordTimestampMs() <= 0L
                        || candidate.recordTimestampMs() >= candidate.validUntilMs()) {
                    // R-STOP demands the frozen pair be COMMITTED before the fence. A candidate
                    // admitted live (gateway clock pre-fence) whose broker CreateTime is at/after
                    // the fence — or absent — cannot prove that (round-1 finding 4): frozen-blank.
                    ibkrPreOpenGexRejected.incrementAndGet();
                    continue;
                }
                if (isIbkrPreOpenStrikeTakenOverLocked(
                        candidate.identity(), candidate.validUntilMs())) {
                    // Terminally Databento-owned (takeover tombstone, or the restart-race
                    // observation memory — round-3 finding 2): a late fence capture must not
                    // freeze a strike past its first newly-ingested Databento record.
                    ibkrPreOpenGexRejected.incrementAndGet();
                    continue;
                }
                if (isIbkrPreOpenPairedNumberBearing(candidate)) {
                    IbkrPreOpenGexCandidate previous =
                            ibkrPreOpenFrozenProjections.get(candidate.identity());
                    if (previous == null || (previous.partition() == candidate.partition()
                            && candidate.offset() > previous.offset())) {
                        ibkrPreOpenFrozenProjections.put(candidate.identity(), candidate);
                        broadcasts.add(wrapIbkrPreOpenGex(
                                candidate.recordKey(), candidate.offset(), "FROZEN", candidate.json()));
                    }
                } else {
                    // Numberless at 09:25⁻ can mean "the pairing status was not CONSUMED yet"
                    // (independent consumers, round-1 finding 1) — hold pending; if the status
                    // never proves a pre-fence pair, the strike stays frozen-blank truthfully.
                    stashIbkrPreOpenPendingLocked(candidate);
                }
            }
            // 1b. Pending-pair promotion: a status observed since the last sweep may have
            // completed a pending pair (order-independent reconstruction, round-1 finding 1).
            broadcasts.addAll(promoteIbkrPreOpenPendingLocked(nowMs));
            // 2. Takeover watermark snapshot, once per fence at fence + 5 min. PENDING pairs
            // count too (round-2 finding 2): a window whose statuses lag the boundary has only
            // pending entries, and its takeover judgment needs the same 09:30 position snapshot.
            for (Map<String, IbkrPreOpenGexCandidate> plane
                    : List.of(ibkrPreOpenFrozenProjections, ibkrPreOpenPendingProjections)) {
                for (IbkrPreOpenGexCandidate projection : plane.values()) {
                    long fence = projection.validUntilMs();
                    if (nowMs >= fence + IBKR_PREOPEN_FREEZE_WINDOW_MS
                            && ibkrPreOpenBoundaryObservedLive(fence)
                            && !ibkrPreOpenTakeoverWatermarks.containsKey(fence)) {
                        // Only for a boundary this process actually crossed. After a restart the
                        // snapshot would be synthetic — offsets read AFTER the boundary — and the
                        // pinned rule is commit-time-only recovery instead.
                        ibkrPreOpenTakeoverWatermarks.put(fence,
                                Map.copyOf(ibkrPreOpenSharedGexMaxSeenOffsets));
                    }
                }
            }
            // 3. Destruction at fence + 10 min: evict every remaining projection, forever.
            for (Iterator<Map.Entry<String, IbkrPreOpenGexCandidate>> it =
                    ibkrPreOpenFrozenProjections.entrySet().iterator(); it.hasNext(); ) {
                IbkrPreOpenGexCandidate projection = it.next().getValue();
                if (nowMs >= projection.validUntilMs() + IBKR_PREOPEN_EVICT_AFTER_MS) {
                    it.remove();
                    broadcasts.add(wrapIbkrPreOpenGexEviction(projection.recordKey(), "WINDOW_END"));
                }
            }
            // Bookkeeping release: watermarks and takeover tombstones for windows that are over
            // (nothing can enter a dead window, so terminality no longer needs the pin), oldest
            // revocations.
            ibkrPreOpenTakeoverWatermarks.keySet().removeIf(
                    fence -> nowMs >= fence + IBKR_PREOPEN_EVICT_AFTER_MS);
            ibkrPreOpenTakenOverStrikes.values().removeIf(
                    fence -> nowMs >= fence + IBKR_PREOPEN_EVICT_AFTER_MS);
            // Restart-race observation memory (round-3 finding 2): an entry with commit time T
            // only matters for a window whose boundary (fence+5min) T crosses AND that can still
            // reconstruct (before fence+10min) — algebraically dead once nowMs >= T + 5 min.
            // Retire at T + 10 min (2x margin for clock skew); bounds the map to the strikes
            // active in the last few minutes.
            ibkrPreOpenDatabentoMaxCommitMs.values().removeIf(
                    seen -> nowMs >= seen.commitMs() + IBKR_PREOPEN_EVICT_AFTER_MS);
            while (ibkrPreOpenRevokedGenerations.size() > 128) {
                Iterator<String> eldest = ibkrPreOpenRevokedGenerations.iterator();
                eldest.next();
                eldest.remove();
            }
        }
        for (String wrapped : broadcasts) {
            broadcastIbkrPreOpenGex(wrapped);
        }
    }

    /** Advance the shared live gex topic's per-partition observed high-watermark. Fed by EVERY
     *  record either consumer observes on the topic — Databento, sessioned, malformed alike —
     *  so the 09:30 snapshot is the gateway's real observed position (round-1 finding 5). */
    private void trackIbkrPreOpenObservedOffset(ConsumerRecord<String, Object> record) {
        ibkrPreOpenSharedGexMaxSeenOffsets.merge(record.partition(), record.offset(), Math::max);
    }

    /**
     * True when the strike is terminally Databento-owned for the window whose fence is
     * {@code fenceMs}: either its takeover tombstone already exists, or a Databento record
     * committed at/after the window's 09:30 boundary was ALREADY OBSERVED before the strike's
     * state was (re)constructed — the fresh-restart race, round-3 finding 2 — in which case the
     * tombstone is recorded now. Every projection-creating path (post-fence ingest, fence
     * capture, pending stash, pending promotion) must consult this ONE guard so no path can
     * resurrect a strike past its first newly-ingested Databento record. MUST be called under
     * {@link #ibkrPreOpenGexLock}.
     */
    /**
     * True when a control committed at {@code controlCommitMs} can prove it precedes the live state
     * it would supersede — i.e. it was committed before the fence of every candidate/pending entry
     * it could reach. A control that arrives after those fences says nothing about them.
     * MUST be called under {@link #ibkrPreOpenGexLock}.
     */
    private boolean ibkrPreOpenControlPrecedesLiveState(long controlCommitMs) {
        for (Map<String, IbkrPreOpenGexCandidate> plane
                : List.of(ibkrPreOpenGexCandidates, ibkrPreOpenPendingProjections)) {
            for (IbkrPreOpenGexCandidate candidate : plane.values()) {
                if (controlCommitMs >= candidate.validUntilMs()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Snapshot the partition high-watermark for any window whose 09:30 boundary this process has
     * crossed and which has no snapshot yet — even when no projection exists for it. Reads the
     * boundaries from the window state the path plane maintains, so a session with no live
     * projections is still fenced at the right instant.
     */
    private void captureIbkrPreOpenBoundaryWatermarkLocked(long nowMs) {
        synchronized (ibkrPreOpenGexLock) {
            for (Long fence : new java.util.ArrayList<>(ibkrPreOpenObservedFences)) {
                if (nowMs >= fence + IBKR_PREOPEN_FREEZE_WINDOW_MS
                        && ibkrPreOpenBoundaryObservedLive(fence)
                        && !ibkrPreOpenTakeoverWatermarks.containsKey(fence)) {
                    ibkrPreOpenTakeoverWatermarks.put(fence,
                            Map.copyOf(ibkrPreOpenSharedGexMaxSeenOffsets));
                }
            }
        }
    }

    /** True when this process was running when the window's 09:30 boundary passed. */
    private boolean ibkrPreOpenBoundaryObservedLive(long fenceMs) {
        return fenceMs + IBKR_PREOPEN_FREEZE_WINDOW_MS > ibkrPreOpenProcessStartMs;
    }

    private boolean isIbkrPreOpenStrikeTakenOverLocked(String identity, long fenceMs) {
        if (ibkrPreOpenTakenOverStrikes.containsKey(identity)) {
            return true;
        }
        IbkrPreOpenDatabentoObservation seen = ibkrPreOpenDatabentoMaxCommitMs.get(identity);
        if (seen == null || seen.commitMs() < fenceMs + IBKR_PREOPEN_FREEZE_WINDOW_MS) {
            return false;
        }
        if (ibkrPreOpenBoundaryObservedLive(fenceMs)) {
            // Observed live => BOTH axes, exactly as the immediate path applies them. A
            // post-boundary commit at/below the window's snapshotted high-watermark is a prior
            // record, not a takeover. Until that snapshot exists the second axis cannot be
            // evaluated at all, so this must NOT declare a takeover — killing a candidate or a
            // pending pair on a half-evaluated predicate is exactly the round-5 finding.
            Map<Integer, Long> watermark = ibkrPreOpenTakeoverWatermarks.get(fenceMs);
            if (watermark == null) {
                return false;
            }
            Long mark = watermark.get(seen.partition());
            if (mark != null && seen.offset() <= mark) {
                return false;
            }
        }
        // NOT observed live (restart after 09:30) => commit-time only, per the pinned recovery
        // rule. No watermark exists for this window and none may be invented.
        ibkrPreOpenTakenOverStrikes.merge(identity, fenceMs, Math::max);
        return true;
    }

    /**
     * Per-strike takeover (O5/D12): the FIRST newly-ingested Databento record for a frozen strike
     * after the 09:30 boundary evicts that strike's projection — its per-strike alert flips to
     * LIVE client-side. "Newly-ingested" is fail-closed on BOTH axes: the record must be COMMITTED
     * at/after the boundary (broker CreateTime — a compacted/bootstrap/cached prior record always
     * predates it) AND, when the boundary watermark exists, sit BEYOND the gateway's snapshotted
     * 09:30 high-watermark for its partition. This predicate controls ONLY the projection/alert —
     * the Databento record itself is untouched and flows through the existing pipeline.
     */
    /**
     * True when some live projection's window has passed its 09:30 boundary and has NOT yet had its
     * high-watermark snapshotted — the only condition under which the takeover path needs to force
     * a sweep. Cheap: two small plane scans and a map lookup, no lock, no allocation.
     */
    private boolean ibkrPreOpenBoundarySnapshotMissing(long nowMs) {
        for (Map<String, IbkrPreOpenGexCandidate> plane
                : List.of(ibkrPreOpenFrozenProjections, ibkrPreOpenPendingProjections)) {
            for (IbkrPreOpenGexCandidate projection : plane.values()) {
                long fence = projection.validUntilMs();
                if (nowMs >= fence + IBKR_PREOPEN_FREEZE_WINDOW_MS
                        && ibkrPreOpenBoundaryObservedLive(fence)
                        && !ibkrPreOpenTakeoverWatermarks.containsKey(fence)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void maybeEvictIbkrProjectionOnTakeover(
            ConsumerRecord<String, Object> record, JsonNode root, long nowMs) {
        if (ibkrPreOpenFrozenProjections.isEmpty() && ibkrPreOpenPendingProjections.isEmpty()
                && ibkrPreOpenTakeoverWatermarks.isEmpty()) {
            // No projections YET does not mean no boundary (round-6 finding 2). A process that
            // started before 09:30 while cache reconstruction lagged has genuinely OBSERVED the
            // boundary, and returning early here left it un-snapshotted — reconstruction then saw a
            // live-observed boundary with no watermark and admitted the projection, and the NEXT
            // sweep snapshotted offsets that already included this takeover record, classifying it
            // at/below its own watermark so the projection survived to 09:35. Record the position
            // now, before anything can reconstruct against it.
            captureIbkrPreOpenBoundaryWatermarkLocked(nowMs);
            return;
        }
        // ONCE PER BOUNDARY, not once per record (round-5 finding 5). The sweep exists here only to
        // install a window's 09:30 watermark snapshot before this record is judged "newly-ingested";
        // it traverses every plane and the whole observation map under one lock, and the watermark
        // lives until 09:35, so calling it per shared-topic record kept that cost on the hot path
        // for the entire window. The scheduled 5 s sweeper does everything else — this needs the
        // sweep ONLY while some live window has crossed its boundary without a snapshot yet.
        if (ibkrPreOpenBoundarySnapshotMissing(nowMs)) {
            sweepIbkrPreOpenGexWindows(nowMs);
        }
        String fallbackKey = record.key() == null ? "" : String.valueOf(record.key());
        String identity = gexIdentityFromNode(root, fallbackKey);
        String evictionWrap = null;
        synchronized (ibkrPreOpenGexLock) {
            // The candidate fences: the strike's own held state (frozen OR pending — round-2
            // finding 2: a pending pair is takeover-eligible state too) names its fence, and the
            // snapshotted watermark fences cover a strike holding NO state yet, whose takeover
            // must still be recorded TERMINALLY so a late-completing pending pair can never
            // present IBKR after the strike's first newly-ingested Databento record.
            java.util.TreeSet<Long> fences =
                    new java.util.TreeSet<>(ibkrPreOpenTakeoverWatermarks.keySet());
            IbkrPreOpenGexCandidate frozen = ibkrPreOpenFrozenProjections.get(identity);
            if (frozen != null) {
                fences.add(frozen.validUntilMs());
            }
            IbkrPreOpenGexCandidate pendingPair = ibkrPreOpenPendingProjections.get(identity);
            if (pendingPair != null) {
                fences.add(pendingPair.validUntilMs());
            }
            for (long fence : fences) {
                long boundary = fence + IBKR_PREOPEN_FREEZE_WINDOW_MS;
                if (nowMs < boundary || nowMs >= fence + IBKR_PREOPEN_EVICT_AFTER_MS) {
                    continue; // before this window's 09:30, or the window is already dead
                }
                if (record.timestamp() <= 0L || record.timestamp() < boundary) {
                    continue; // committed before 09:30 — compaction/bootstrap replay, never a takeover
                }
                Map<Integer, Long> watermark = ibkrPreOpenTakeoverWatermarks.get(fence);
                if (watermark != null) {
                    Long mark = watermark.get(record.partition());
                    if (mark != null && record.offset() <= mark) {
                        continue; // at/below the 09:30 high-watermark — a prior record, never a takeover
                    }
                }
                // Newly-ingested for this window: TERMINAL per-strike takeover. Pin the identity
                // (late compacted redeliveries and late-completing pending pairs can never
                // resurrect it), kill the pending pair, and evict the presented projection.
                ibkrPreOpenTakenOverStrikes.merge(identity, fence, Math::max);
                if (pendingPair != null && pendingPair.validUntilMs() == fence) {
                    ibkrPreOpenPendingProjections.remove(identity); // never presented — silent
                }
                if (frozen != null && frozen.validUntilMs() == fence
                        && ibkrPreOpenFrozenProjections.remove(identity, frozen)) {
                    evictionWrap = wrapIbkrPreOpenGexEviction(frozen.recordKey(), "TAKEOVER");
                }
            }
        }
        if (evictionWrap != null) {
            broadcastIbkrPreOpenGex(evictionWrap);
        }
    }

    /**
     * Apply a {@code __revocation|<D>|<gen>} control (R-ROLL: explicit early supersession of a
     * named output generation; gateway evict ≤30 s — this runs synchronously at control ingest).
     * Evicts matching live candidates AND frozen projections (a rollback in the freeze gap must
     * kill the frozen values too) and pins the (sessionDate, generation) so late stragglers of
     * the revoked generation can never re-enter. Idempotent — both consumers may deliver it.
     */
    private void applyIbkrPreOpenRevocation(String controlKey) {
        String[] parts = controlKey.split("\\|");
        if (parts.length < 3) {
            return; // malformed control — no named generation to act on
        }
        String sessionDate = parts[1];
        long generation;
        try {
            generation = Long.parseLong(parts[2].trim());
        } catch (NumberFormatException e) {
            return;
        }
        List<String> broadcasts;
        synchronized (ibkrPreOpenGexLock) {
            if (!ibkrPreOpenRevokedGenerations.add(sessionDate + "|" + generation)) {
                return; // already applied
            }
            broadcasts = evictIbkrPreOpenGexLocked(candidate ->
                    sessionDate.equals(ibkrPreOpenSessionDate(candidate.sessionId()))
                            && candidate.outputGeneration() == generation, "REVOKED");
        }
        for (String wrapped : broadcasts) {
            broadcastIbkrPreOpenGex(wrapped);
        }
    }

    /**
     * Apply a {@code __generation|<D>|<gen>} control (R-WIRE.2: OUTPUT generations only —
     * "consumers treat the max observed output generation per session as current, all lower
     * superseded"). The control is AUTHORITATIVE on its own (round-2 finding 3): after the state
     * consumer observes it, a delayed lower-generation value must be rejected even if no value
     * of the new generation has been observed yet. Advances the session date's max generation
     * and evicts the superseded LIVE candidates / pending pairs; the FROZEN cache is
     * NON-CANDIDATE state that supersession never touches (round-2 finding 4). Idempotent —
     * both consumers may deliver it.
     */
    private void applyIbkrPreOpenGenerationControl(String controlKey, long controlCommitMs) {
        String[] parts = controlKey.split("\\|");
        if (parts.length < 3) {
            return; // malformed control — no named generation to act on
        }
        String sessionDate = parts[1];
        long generation;
        try {
            generation = Long.parseLong(parts[2].trim());
        } catch (NumberFormatException e) {
            return;
        }
        List<String> broadcasts;
        synchronized (ibkrPreOpenGexLock) {
            Long maxGen = ibkrPreOpenMaxGeneration.get(sessionDate);
            if (maxGen != null && generation <= maxGen) {
                return; // already at/behind the observed max — nothing new to supersede
            }
            // ADVANCING the max is destructive too, not just the eviction (round-6 finding 3): the
            // pending re-evaluation and every later straggler check read this global maximum, so an
            // unproven control erases or blocks the latest valid pre-fence pair by that route
            // instead. A control that cannot prove it precedes the state it supersedes changes
            // nothing at all.
            if (controlCommitMs > 0L && !ibkrPreOpenControlPrecedesLiveState(controlCommitMs)) {
                return;
            }
            ibkrPreOpenMaxGeneration.put(sessionDate, generation);
            // The control is authoritative for ORDERING, but eviction is destructive and needs the
            // same proof a value record needs (round-5 finding 2): a control COMMITTED at/after the
            // fence of the state it would destroy cannot prove that state is superseded, and a
            // delayed one must not wipe candidates and pending pairs the sweep has not judged yet.
            broadcasts = evictIbkrPreOpenSupersededLocked(sessionDate, generation,
                    controlCommitMs);
        }
        for (String wrapped : broadcasts) {
            broadcastIbkrPreOpenGex(wrapped);
        }
    }

    /** Evict matching entries from BOTH plane maps (an explicit {@code __revocation} kills live
     *  candidates AND frozen projections — R-ROLL); returns the eviction wraps (caller broadcasts
     *  outside the lock where possible). Matching PENDING pairs die too — silently, they were
     *  never presented. MUST be called under {@link #ibkrPreOpenGexLock}. */
    private List<String> evictIbkrPreOpenGexLocked(
            java.util.function.Predicate<IbkrPreOpenGexCandidate> matches, String reason) {
        List<String> wraps = evictIbkrPreOpenMatchingLocked(
                List.of(ibkrPreOpenGexCandidates, ibkrPreOpenFrozenProjections), matches, reason);
        ibkrPreOpenPendingProjections.values().removeIf(matches::test);
        return wraps;
    }

    /**
     * Generation supersession (R-WIRE.2/.6 "all lower superseded"): evict the session date's
     * lower-generation LIVE candidates and purge its pending pairs — NEVER the frozen cache
     * (round-2 finding 4): R-STOP pins the frozen projection as NON-CANDIDATE state that
     * closed-generation rejection does not touch; an unproven newer generation must not destroy
     * a proven pre-fence pair. MUST be called under {@link #ibkrPreOpenGexLock}.
     */
    private List<String> evictIbkrPreOpenSupersededLocked(String sessionDate, long newGeneration) {
        return evictIbkrPreOpenSupersededLocked(sessionDate, newGeneration, Long.MIN_VALUE);
    }

    /**
     * As above, but skipping any entry whose fence the superseding signal cannot prove it precedes.
     * {@code supersedingCommitMs} is the broker commit time of the record or control driving the
     * supersession; {@code Long.MIN_VALUE} means "already proven by the caller". An entry whose
     * fence is at/before that commit time is NOT superseded by it — the signal arrived too late to
     * say anything about state that was already frozen or is still awaiting its pairing status.
     */
    private List<String> evictIbkrPreOpenSupersededLocked(String sessionDate, long newGeneration,
            long supersedingCommitMs) {
        java.util.function.Predicate<IbkrPreOpenGexCandidate> matches = candidate ->
                sessionDate.equals(ibkrPreOpenSessionDate(candidate.sessionId()))
                        && candidate.outputGeneration() < newGeneration
                        && (supersedingCommitMs == Long.MIN_VALUE
                                || (supersedingCommitMs > 0L
                                        && supersedingCommitMs < candidate.validUntilMs()));
        List<String> wraps = evictIbkrPreOpenMatchingLocked(
                List.of(ibkrPreOpenGexCandidates), matches, "SUPERSEDED");
        ibkrPreOpenPendingProjections.values().removeIf(matches::test);
        return wraps;
    }

    /** Shared eviction walk over the given presented-plane maps. MUST be called under
     *  {@link #ibkrPreOpenGexLock}. */
    private List<String> evictIbkrPreOpenMatchingLocked(
            List<Map<String, IbkrPreOpenGexCandidate>> maps,
            java.util.function.Predicate<IbkrPreOpenGexCandidate> matches, String reason) {
        List<String> wraps = new ArrayList<>();
        for (Map<String, IbkrPreOpenGexCandidate> map : maps) {
            for (Iterator<Map.Entry<String, IbkrPreOpenGexCandidate>> it =
                    map.entrySet().iterator(); it.hasNext(); ) {
                IbkrPreOpenGexCandidate candidate = it.next().getValue();
                if (matches.test(candidate)) {
                    it.remove();
                    wraps.add(wrapIbkrPreOpenGexEviction(candidate.recordKey(), reason));
                }
            }
        }
        return wraps;
    }

    /**
     * Hold a value that is provably committed BEFORE its fence but whose revision-equal
     * number-bearing status has not been observed yet (round-1 finding 1). Same monotonic
     * per-identity ordering as the presented planes; never presented while pending. MUST be
     * called under {@link #ibkrPreOpenGexLock}.
     */
    private void stashIbkrPreOpenPendingLocked(IbkrPreOpenGexCandidate incoming) {
        if (isIbkrPreOpenStrikeTakenOverLocked(incoming.identity(), incoming.validUntilMs())) {
            // Takeover is TERMINAL per strike (round-2 finding 2; restart-race memory, round-3
            // finding 2): a late sweep processing a candidate whose fence passed while the
            // gateway was stalled must not stash a pending pair for a strike Databento owns.
            ibkrPreOpenGexRejected.incrementAndGet();
            return;
        }
        IbkrPreOpenGexCandidate frozen = ibkrPreOpenFrozenProjections.get(incoming.identity());
        if (frozen != null && (frozen.partition() != incoming.partition()
                || incoming.offset() <= frozen.offset())) {
            ibkrPreOpenGexRejected.incrementAndGet(); // can never beat the established projection
            return;
        }
        IbkrPreOpenGexCandidate previous = ibkrPreOpenPendingProjections.get(incoming.identity());
        if (previous != null && (previous.partition() != incoming.partition()
                || incoming.offset() <= previous.offset())) {
            if (previous.partition() != incoming.partition()
                    || incoming.offset() < previous.offset()) {
                ibkrPreOpenGexRejected.incrementAndGet(); // regressed/cross-partition — fail closed
            } // the sibling consumer's exact duplicate is not a rejection, just a no-op
            return;
        }
        ibkrPreOpenPendingProjections.put(incoming.identity(), incoming);
    }

    /**
     * Re-evaluate every pending pair: promote the ones whose status now proves a pre-fence
     * revision-equal number-bearing pair, drop the dead ones (window over, generation revoked or
     * superseded). Returns the FROZEN wraps to broadcast (caller does so OUTSIDE the lock).
     * MUST be called under {@link #ibkrPreOpenGexLock}.
     */
    private List<String> promoteIbkrPreOpenPendingLocked(long nowMs) {
        if (ibkrPreOpenPendingProjections.isEmpty()) {
            return List.of();
        }
        List<String> wraps = new ArrayList<>();
        for (Iterator<Map.Entry<String, IbkrPreOpenGexCandidate>> it =
                ibkrPreOpenPendingProjections.entrySet().iterator(); it.hasNext(); ) {
            IbkrPreOpenGexCandidate pending = it.next().getValue();
            if (nowMs >= pending.validUntilMs() + IBKR_PREOPEN_EVICT_AFTER_MS) {
                it.remove(); // never paired inside its window — frozen-blank forever
                continue;
            }
            if (ibkrPreOpenRevokedGenerations.contains(
                    ibkrPreOpenSessionDate(pending.sessionId()) + "|" + pending.outputGeneration())) {
                it.remove(); // generation revoked while pending
                continue;
            }
            Long maxGen = ibkrPreOpenMaxGeneration.get(
                    ibkrPreOpenSessionDate(pending.sessionId()));
            if (maxGen != null && pending.outputGeneration() < maxGen) {
                it.remove(); // generation superseded while pending
                continue;
            }
            if (isIbkrPreOpenStrikeTakenOverLocked(pending.identity(), pending.validUntilMs())) {
                // Terminal-takeover guard (round-2 finding 2; round-3 finding 2): a taken-over
                // strike — whether tombstoned live or discovered from the restart-race
                // observation memory — must never promote a pending pair.
                it.remove();
                ibkrPreOpenGexRejected.incrementAndGet();
                continue;
            }
            if (!isIbkrPreOpenPairedNumberBearing(pending)) {
                continue; // still unproven — keep waiting inside the window
            }
            it.remove();
            IbkrPreOpenGexCandidate previous = ibkrPreOpenFrozenProjections.get(pending.identity());
            if (previous != null && (previous.partition() != pending.partition()
                    || pending.offset() <= previous.offset())) {
                ibkrPreOpenGexRejected.incrementAndGet(); // a newer projection landed meanwhile
                continue;
            }
            ibkrPreOpenFrozenProjections.put(pending.identity(), pending);
            wraps.add(wrapIbkrPreOpenGex(
                    pending.recordKey(), pending.offset(), "FROZEN", pending.json()));
        }
        return wraps;
    }

    /** Lock-taking wrapper around {@link #promoteIbkrPreOpenPendingLocked}: promotion driven by a
     *  status-record arrival (the status ingest path holds no plane lock); broadcasts outside. */
    private void reevaluateIbkrPreOpenPendingProjections(long nowMs) {
        if (ibkrPreOpenPendingProjections.isEmpty()) {
            return;
        }
        List<String> promoted;
        synchronized (ibkrPreOpenGexLock) {
            promoted = promoteIbkrPreOpenPendingLocked(nowMs);
        }
        for (String wrapped : promoted) {
            broadcastIbkrPreOpenGex(wrapped);
        }
    }

    /**
     * R-WIRE.5 pairing, evaluated for the FROZEN projection only (the live plane is a pass-through
     * — the browser joins values to the separately-delivered status stream and self-corrects on
     * arrival order): the value is number-bearing iff the strike's LATEST cached status carries the
     * SAME sessionId + outputGeneration + baselineEpoch, an EQUAL recordRevision, and a
     * number-bearing state (FRESH or STALE — both render numbers per §0; GATED/ABSENT never do).
     * Status rows live in the slice-1 cache under the "IBKR|" + raw-record-key cache key (the
     * status topic's strike keys are normalized to the value key by contract).
     *
     * <p>R-STOP pins the frozen pair as committed BEFORE 09:25 — BOTH halves. The value half is
     * proven by the caller (recordTimestampMs &lt; fence); the status half is proven here from the
     * wrap's {@code timestampMs} (the status record's broker CreateTime, round-1 finding 4). A
     * status without a provable pre-fence commit time can never prove a frozen pair.
     */
    private boolean isIbkrPreOpenPairedNumberBearing(IbkrPreOpenGexCandidate candidate) {
        String wrappedStatus = ibkrPreOpenStatus.get("IBKR|" + candidate.recordKey());
        if (wrappedStatus == null || wrappedStatus.isBlank()) {
            return false;
        }
        try {
            JsonNode wrapper = mapper.readTree(wrappedStatus);
            long statusTimestampMs = longField(wrapper, "timestampMs", 0L);
            if (statusTimestampMs <= 0L || statusTimestampMs >= candidate.validUntilMs()) {
                return false; // status committed at/after the fence (or unprovable) — no pair
            }
            JsonNode status = wrapper.get("status");
            if (status == null || !status.isObject()) {
                return false;
            }
            String state = text(status, "state");
            boolean numberBearing = "FRESH".equals(state) || "STALE".equals(state);
            return numberBearing
                    && candidate.sessionId().equals(text(status, "sessionId"))
                    && longField(status, "outputGeneration", Long.MIN_VALUE) == candidate.outputGeneration()
                    && longField(status, "baselineEpoch", Long.MIN_VALUE) == candidate.baselineEpoch()
                    && longField(status, "recordRevision", Long.MIN_VALUE) == candidate.recordRevision();
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /** {@code IBKR_PREOPEN:<D>} -> {@code <D>} (the {@code __revocation|<D>|<gen>} key's date). */
    /** The pinned session-id shape: IBKR_PREOPEN:&lt;yyyy-MM-dd&gt;. Anything else fails closed. */
    private static final java.util.regex.Pattern IBKR_PREOPEN_SESSION_ID =
            java.util.regex.Pattern.compile("IBKR_PREOPEN:\\d{4}-\\d{2}-\\d{2}");

    /**
     * The pinned session id: IBKR_PREOPEN:&lt;yyyy-MM-dd&gt; where the date is a REAL calendar date.
     * The regex alone admits 2026-99-99, which is not a session and must not key a plane.
     */
    private static boolean isIbkrPreOpenSessionId(String sessionId) {
        if (!IBKR_PREOPEN_SESSION_ID.matcher(sessionId).matches()) {
            return false;
        }
        try {
            java.time.LocalDate.parse(sessionId.substring("IBKR_PREOPEN:".length()));
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    /**
     * True when the canonical payload identity (symbol|expiry|strike) names the same strike as the
     * Kafka key it arrived under. Keys are "SPX|20260804|6300"; the identity carries the same three
     * components, so a mismatch means the record was partitioned under someone else's key and every
     * per-strike offset comparison downstream would be comparing across partitions.
     */
    private static boolean identityMatchesRecordKey(String identity, String recordKey) {
        String[] left = identity.split("\\|", -1);
        String[] right = recordKey.split("\\|", -1);
        // EXACTLY three components on both sides: a key carrying extras is not the canonical key,
        // and accepting it would let an unrelated record ride a valid strike's partitioning.
        if (left.length != 3 || right.length != 3) {
            return false;
        }
        // Case-SENSITIVE: the wire contract pins the symbol's casing like every other tuple field.
        if (!left[0].equals(right[0]) || !left[1].equals(right[1])) {
            return false;
        }
        try {
            return Double.compare(Double.parseDouble(left[2]), Double.parseDouble(right[2])) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String ibkrPreOpenSessionDate(String sessionId) {
        int separator = sessionId.indexOf(':');
        return separator < 0 ? sessionId : sessionId.substring(separator + 1);
    }

    /** Payload-derived strike identity, symbol|expiry|strike (the gexCacheKey derivation, from an
     *  already-parsed node) — the join key between a frozen projection and its Databento takeover. */
    private String gexIdentityFromNode(JsonNode root, String fallback) {
        String symbol = text(root, "symbol").toUpperCase();
        String expiry = normalizeExpiry(text(root, "expiry"));
        double strike = doubleField(root, "strike", Double.NaN);
        if (!symbol.isBlank() && !expiry.isBlank() && Double.isFinite(strike)) {
            return symbol + "|" + expiry + "|" + formatStrike(strike);
        }
        return fallback;
    }

    /** The wrapped wire form for the pre-open gex value plane (the status-wrap sibling): the raw
     *  Kafka record key + broker offset + phase, the producer payload byte-untouched. Client
     *  contract: EVICTED > FROZEN > LIVE regardless of offset; within a phase, higher offset wins
     *  per recordKey (replay and live deliveries can interleave at the socket). */
    static String wrapIbkrPreOpenGex(String recordKey, long offset, String phase, String json) {
        StringBuilder sb = new StringBuilder("{\"recordKey\":\"");
        appendJsonEscaped(sb, recordKey);
        return sb.append("\",\"offset\":").append(offset)
                .append(",\"phase\":\"").append(phase)
                .append("\",\"gex\":").append(json).append('}').toString();
    }

    /** Terminal per-strike eviction (TAKEOVER at the strike's first newly-ingested Databento
     *  record; WINDOW_END at 09:35; REVOKED/SUPERSEDED for generation death). */
    static String wrapIbkrPreOpenGexEviction(String recordKey, String reason) {
        StringBuilder sb = new StringBuilder("{\"recordKey\":\"");
        appendJsonEscaped(sb, recordKey);
        return sb.append("\",\"phase\":\"EVICTED\",\"reason\":\"").append(reason)
                .append("\"}").toString();
    }

    /** Per-partition exactly-once in-order live-delivery gate for the value plane (the slice-1
     *  status gate, generalized to the shared topic's multiple partitions). */
    private final Map<Integer, java.util.concurrent.atomic.AtomicLong> ibkrPreOpenGexBroadcastOffsets =
            new ConcurrentHashMap<>();

    boolean shouldBroadcastIbkrPreOpenGex(int partition, long offset) {
        java.util.concurrent.atomic.AtomicLong gate = ibkrPreOpenGexBroadcastOffsets
                .computeIfAbsent(partition, p -> new java.util.concurrent.atomic.AtomicLong(-1L));
        while (true) {
            long current = gate.get();
            if (offset <= current) {
                return false;
            }
            if (gate.compareAndSet(current, offset)) {
                return true;
            }
        }
    }

    /** Latched TRUE the first time both consumer barriers are up together — see
     *  {@link #ibkrPreOpenGexServingUp()}. */
    private final AtomicBoolean ibkrPreOpenEverServed = new AtomicBoolean(false);

    /**
     * R-WIRE.5 status/control-before-serving barrier for the value plane: values ride the AVRO
     * consumer, but the revocation/generation controls and pairing statuses ride the JSON state
     * consumer — a value must never reach a client before BOTH are reconstructed (round-2
     * finding 1: gating replay alone is not enough, the live broadcast path serves clients too).
     *
     * <p>This is an INITIAL-BOOTSTRAP gate only, latched open the first time both barriers are
     * up together. The caught-up flags are NOT monotonic — {@link #markCacheRecovering} flips
     * one back to false when a caught-up consumer stalls/rewinds — but a post-serving recovery
     * never wipes the plane's in-memory state (revocations, generations, tombstones, projections
     * all persist), so continued serving stays sound; suppressing during recovery would instead
     * LOSE terminal evictions (takeover / revocation / 09:35 destruction) for clients that
     * already hold the value — a client could keep a frozen projection forever (round-3
     * finding 1). Before the first joint catch-up nothing was ever served, so there is nothing
     * a suppressed message could correct.
     */
    private boolean ibkrPreOpenGexServingUp() {
        if (ibkrPreOpenEverServed.get()) {
            return true;
        }
        if (avroCaughtUp.get() && stateCaughtUp.get()) {
            ibkrPreOpenEverServed.set(true);
            return true;
        }
        return false;
    }

    /**
     * The ONLY way a value-plane message (LIVE/FROZEN wrap or EVICTED transition) reaches
     * connected clients. Suppressed until {@link #ibkrPreOpenGexServingUp()} latches: before the
     * first joint catch-up NO client has ever received a value from this process (replay is
     * gated on the same barrier) and a suppressed eviction has nothing to correct; the
     * markCacheCaughtUp re-push replays the full plane the moment the LAST barrier clears.
     * After the latch, every message — terminal evictions above all — always flows.
     */
    private void broadcastIbkrPreOpenGex(String wrapped) {
        if (ibkrPreOpenGexServingUp()) {
            broadcast("ibkr-preopen-gex", wrapped);
        }
    }

    /** Re-push the pre-open value plane to one client: live candidates (phase LIVE) plus frozen
     *  projections (phase FROZEN), window transitions applied first (consumer-local — a client
     *  connecting at 09:26 gets projections even if no record has arrived since the fence). */
    private void replayIbkrPreOpenGexCached(WebSocketSession session) {
        long nowMs = System.currentTimeMillis();
        sweepIbkrPreOpenGexWindows(nowMs);
        List<String> wraps = new ArrayList<>();
        synchronized (ibkrPreOpenGexLock) {
            for (IbkrPreOpenGexCandidate candidate : ibkrPreOpenGexCandidates.values()) {
                if (nowMs < candidate.validUntilMs()) {
                    wraps.add(wrapIbkrPreOpenGex(
                            candidate.recordKey(), candidate.offset(), "LIVE", candidate.json()));
                }
            }
            for (IbkrPreOpenGexCandidate projection : ibkrPreOpenFrozenProjections.values()) {
                wraps.add(wrapIbkrPreOpenGex(
                        projection.recordKey(), projection.offset(), "FROZEN", projection.json()));
            }
        }
        for (String wrapped : wraps) {
            send(session, "ibkr-preopen-gex", wrapped);
        }
    }

    /**
     * Events whose Kafka value is a PRODUCER-authored contract that must reach the browser
     * byte-untouched: {@link #enrichJson} is bypassed at ingest so the gateway can never stamp
     * {@code marketDataSource}/{@code source}/{@code sessionDate} over the producer's own fields.
     */
    private static boolean isRawPassThroughEvent(String event) {
        return "ibkr-preopen-status".equals(event) || "tapeZones".equals(event)
                || "es-cvd-spx-levels".equals(event);
    }

    /**
     * The wire form for the tape-zones board: the producer record rides VERBATIM under
     * {@code board} and the gateway adds ONLY its own clock stamps around it (UI design §3 —
     * "no gateway-side computation ... the board record already carries everything").
     *
     * <p>{@code boardTimeMs} is the KAFKA RECORD timestamp, i.e. when the service published this
     * board — deliberately NOT the payload's {@code quality.watermark}. The watermark is STREAM
     * time (max eventTime of released trades) and legitimately stops advancing on a quiet tape or
     * is null before the first release, so using it for liveness would paint a healthy board STALE.
     * The record timestamp answers the only question §5's 10 s overlay asks: is the producer alive.
     *
     * <p>{@code serverTime} + {@code ageMs} are stamped at EMIT, from ONE clock (the gateway's), so
     * the card never differences a producer clock against a browser clock. {@code offset} is the
     * ordering token: live and replay deliveries can interleave at the socket, so the client
     * renders last-writer-wins by offset exactly as the pre-open sibling does.
     */
    static String wrapTapeZonesBoard(long offset, long boardTimeMs, long serverTimeMs, String json) {
        long ageMs = boardTimeMs <= 0 ? -1L : Math.max(0L, serverTimeMs - boardTimeMs);
        return "{\"offset\":" + offset
                + ",\"boardTimeMs\":" + boardTimeMs
                + ",\"serverTime\":" + serverTimeMs
                + ",\"ageMs\":" + ageMs
                + ",\"board\":" + json + "}";
    }

    /**
     * The ONE live-delivery seam for the tape-zones board, shared by both ingesting consumers.
     *
     * <p>Order is load-bearing and the whole point of the fix: {@link #updateCache} runs FIRST and
     * its return value is the gate. It answers null for a record that fails the fail-closed
     * identity contract, that repeats or rewinds an offset on the single-partition topic, or whose
     * event time is already outside the TTL — and in every one of those cases nothing is broadcast.
     * A client can therefore never be shown a board the cache refused to keep.
     *
     * <p>The offset CAS behind it makes delivery exactly-once ACROSS the two consumers, and the
     * emit lock makes (cache-mutate → enqueue) atomic against the replay's (cache-read → send), so
     * a replayed older board can never be enqueued after a newer live one.
     */
    private void tapeZonesBroadcast(TopicBinding binding, ConsumerRecord<String, ?> record, String json,
                                    AtomicBoolean caughtUpFlag) {
        synchronized (tapeZonesEmitLock) {
            String cacheKey = updateCache(binding, record, json);
            if (cacheKey == null) {
                return;   // refused by identity / ordering / TTL — never forwarded
            }
            if (!caughtUpFlag.get() || !shouldBroadcastTapeZones(record.offset())) {
                return;
            }
            // Re-read the position the cache just committed rather than re-deriving it from the
            // record: the replay path reads the SAME pair, so the two surfaces cannot disagree
            // about a board's age.
            long[] position = tapeZonesPositions.get(cacheKey);
            if (position == null) {
                return;
            }
            broadcast(binding.event(),
                    wrapTapeZonesBoard(position[0], position[1], System.currentTimeMillis(), json));
            forwardedEvents.incrementAndGet();
        }
    }

    /**
     * Late-join delivery for the tape-zones board: STANDALONE global advisory class, fresh-gated on
     * the SHORT tapeZonesTtlMs window so a dead service (or an un-mirrored dev/prod broker) reads as
     * absent rather than replaying yesterday's session as live. The age stamps are recomputed HERE,
     * at emit, so a client connecting ten minutes after the last change sees the true age.
     */
    private void replayTapeZonesCached(WebSocketSession session) {
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        // The (cache-read → send-enqueue) pair is atomic against the live seam: a live update
        // either lands before (we read the newer value) or after (its enqueue supersedes ours via
        // coalescing). The stale ordering "live N+1 enqueued, then replayed N enqueued" cannot happen.
        synchronized (tapeZonesEmitLock) {
            for (Map.Entry<String, String> entry : tapeZonesBoards.entrySet()) {
                String board = entry.getValue();
                if (board == null || board.isBlank()) {
                    continue;
                }
                if (!isCacheFresh("tapeZones:" + entry.getKey(), nowMs)) {
                    continue;
                }
                long[] position = tapeZonesPositions.get(entry.getKey());
                if (position == null) {
                    continue; // no ordering/age token — never emit an unaged board
                }
                send(session, "tapeZones", wrapTapeZonesBoard(position[0], position[1], nowMs, board));
            }
        }
    }

    /**
     * Cache key for the tape-zones board — the payload's own {@code sessionDate} (§6.2 keys the
     * topic {@code ES|sessionDate}). STRICT, fail-closed: the frame must carry schemaVersion 1 and
     * a non-blank sessionDate, and when the Kafka key names a session it must MATCH the payload's
     * (a record keyed for one session may never overwrite another's board). Anything else returns
     * null — never cached, never forwarded.
     */
    /**
     * The literal record-key prefix the tape-zones service writes. Verified against the producer,
     * not the design doc: {@code TapeZonesRuntime} publishes
     * {@code new ProducerRecord<>(boardTopic, "ES|" + session, board)}. The tape is ES-only by
     * construction (TAPE-ZONES-REQUIREMENT §11 explicitly rules out an SPX/SPY tape variant), so
     * this is a CONSTANT, not a symbol the gateway should be flexible about.
     */
    private static final String TAPE_ZONES_KEY_PREFIX = "ES|";

    /**
     * Cache key for the tape-zones board — FAIL-CLOSED on the full identity contract.
     *
     * <p>The board is a single-key compacted topic that every authenticated client renders as
     * "the session". There is therefore no such thing as a partially-trusted board: a record whose
     * identity cannot be proven must not enter the cache, because everything downstream (live
     * broadcast, connect replay, the caught-up re-push) reads the cache and would fan it out.
     *
     * <p>ALL of the following must hold, or the record is rejected and counted:
     * <ul>
     *   <li>the payload carries {@code schemaVersion} exactly 1 — an unknown shape is refused,
     *       never guessed at;</li>
     *   <li>the Kafka key is present and starts with the exact {@code ES|} prefix. A null/blank
     *       key, a bare {@code 2026-08-07}, or {@code NDX|2026-08-07} are all rejected: the second
     *       cannot be attributed to a producer at all and the third is a DIFFERENT instrument's
     *       record that would otherwise silently overwrite the ES board;</li>
     *   <li>the keyed session is a strictly valid ISO {@code yyyy-MM-dd} calendar date (so
     *       {@code 2026-02-30} or {@code 2026-8-7} are refused, not coerced);</li>
     *   <li>the payload's own {@code sessionDate} is likewise a valid calendar date AND equal to
     *       the keyed one — a record keyed for one session may never overwrite another's board.</li>
     * </ul>
     */
    private String tapeZonesCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode schemaVersion = root.get("schemaVersion");
            if (schemaVersion == null || !schemaVersion.isIntegralNumber()
                    || !schemaVersion.canConvertToInt() || schemaVersion.asInt() != 1) {
                return rejectTapeZones();
            }
            // The Kafka key is REQUIRED, not a best-effort cross-check. Without it the record has
            // no verifiable producer identity, and "trust the payload's own claim about itself" is
            // exactly the hole a poisoned record walks through.
            if (fallback == null || !fallback.startsWith(TAPE_ZONES_KEY_PREFIX)) {
                return rejectTapeZones();
            }
            String keyedSession = fallback.substring(TAPE_ZONES_KEY_PREFIX.length());
            if (!isIsoCalendarDate(keyedSession)) {
                return rejectTapeZones();
            }
            String sessionDate = text(root, "sessionDate");
            if (!isIsoCalendarDate(sessionDate) || !keyedSession.equals(sessionDate)) {
                return rejectTapeZones();
            }
            return sessionDate;
        } catch (Exception malformed) {
            return rejectTapeZones();
        }
    }

    /** Count the refusal and return null (the "dropped" contract updateCache's callers expect). */
    private String rejectTapeZones() {
        tapeZonesRejected.incrementAndGet();
        return null;
    }

    /**
     * Strict ISO calendar date: the shape {@code yyyy-MM-dd} AND a date that really exists.
     * {@code LocalDate.parse} alone would accept neither {@code 2026-8-7} (wrong shape) nor
     * {@code 2026-02-30} (not a real day), which is precisely the point — both are rejected.
     */
    static boolean isIsoCalendarDate(String text) {
        if (text == null || !text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        try {
            java.time.LocalDate.parse(text);
            return true;
        } catch (java.time.format.DateTimeParseException notADate) {
            return false;
        }
    }

    /**
     * True for the three ES open-direction advisory events (once-a-day forecast + per-horizon outcome +
     * the 60s live status heartbeat). They share the standalone/global/never-selection-gated delivery
     * class; freshness deliberately does NOT: cachePolicyFor gives the status its own SHORT window
     * (esOpenDirectionStatusTtlMs, 5 min) BEFORE falling through to the siblings' 12h window.
     */
    private static boolean isEsOpenDirectionEvent(String event) {
        return "es-open-direction-forecast".equals(event)
                || "es-open-direction-outcome".equals(event)
                || "es-open-direction-status".equals(event);
    }

    private void replayEsOpenDirectionCached(WebSocketSession session) {
        // Late-join delivery for the once-a-day ES 09:15 open-direction advisory: replay the cached
        // forecast plus EVERY horizon outcome resolved so far (H1/H2/H3 live under distinct
        // tradeDate|horizon keys, so all three latest outcomes survive side-by-side). Like
        // short-premium this is intentionally NOT filtered by the active market selection — it is a
        // global advisory the UI renders in its own panel — and the purge-first + isCacheFresh gates
        // keep anything older than esOpenDirectionTtlMs (default 12h) from replaying as current.
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        for (Map.Entry<String, String> entry : esOpenDirectionForecasts.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("es-open-direction-forecast:" + entry.getKey(), nowMs)) {
                continue;
            }
            send(session, "es-open-direction-forecast", json);
        }
        for (Map.Entry<String, String> entry : esOpenDirectionOutcomes.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("es-open-direction-outcome:" + entry.getKey(), nowMs)) {
                continue;
            }
            send(session, "es-open-direction-outcome", json);
        }
        // Live status: same standalone replay, but its isCacheFresh gate runs on the SHORT
        // esOpenDirectionStatusTtlMs window (5 min via cachePolicyFor) — a late joiner gets the
        // CURRENT heartbeat only; anything older is simply absent and the UI strip stays hidden.
        for (Map.Entry<String, String> entry : esOpenDirectionStatuses.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            if (!isCacheFresh("es-open-direction-status:" + entry.getKey(), nowMs)) {
                continue;
            }
            send(session, "es-open-direction-status", json);
        }
    }

    /**
     * Cache key for the per-(symbol,tradingDate) Option Price Behavior dashboard stream. The payload uses
     * tradingDate instead of option expiry because it describes the whole intraday behavior surface.
     */
    private String optionPriceBehaviorCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String tradingDate = normalizeExpiry(text(root, "tradingDate"));
            if (tradingDate.isBlank()) {
                tradingDate = normalizeExpiry(text(root, "sessionDate"));
            }
            if (!symbol.isBlank() && !tradingDate.isBlank()) {
                return symbol + "|" + tradingDate;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    private String opbSessionCacheKey(String json, String fallback) {
        return optionPriceBehaviorCacheKey(json, fallback);
    }

    private String opbByOptionCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String expiry = normalizeExpiry(text(root, "expiry"));
            // Normalize strike the same way as gex/pace (formatStrike) so 5500 and 5500.0 collapse to a
            // single cache slot instead of splitting into two and leaking a stale residual on replay.
            double strike = doubleField(root, "strike", Double.NaN);
            // Per-contract event: call and put share a strike, so the side MUST be part of the key or
            // the two contracts overwrite each other in the cache. Prefer the fully-qualified optionKey;
            // fall back to optionType when absent.
            String side = text(root, "optionType").toUpperCase();
            if (side.isBlank()) {
                side = text(root, "optionKey").toUpperCase();
            }
            if (!symbol.isBlank() && !expiry.isBlank() && Double.isFinite(strike) && !side.isBlank()) {
                return symbol + "|" + expiry + "|" + formatStrike(strike) + "|" + side;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    private boolean matchesOptionPriceBehaviorSelection(String json, ActiveSelection selection) {
        if (selection == null || json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(json);
            String source = GatewaySettings.normalizeSource(text(root, "marketDataSource"));
            if (source.isBlank()) {
                source = GatewaySettings.normalizeSource(text(root, "source"));
            }
            if (!source.isBlank() && !selection.source().equals(source)) {
                return false;
            }
            String symbol = text(root, "symbol").toUpperCase();
            String tradingDate = normalizeExpiry(text(root, "tradingDate"));
            if (tradingDate.isBlank()) {
                tradingDate = normalizeExpiry(text(root, "sessionDate"));
            }
            return selection.symbol().equalsIgnoreCase(symbol)
                    && (selection.expiry().isBlank() || tradingDate.isBlank() || selection.expiry().equals(tradingDate));
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    /**
     * Selection match for the whole-underlying spread-skew snapshot: the payload names its market
     * {@code underlying} (there is NO {@code symbol} field) with a NULLABLE {@code expiry}
     * ("YYYY-MM-DD"; null while the producer cannot resolve the 0DTE chain). Match the selection
     * symbol against the underlying, and treat an ABSENT expiry as covering the active session
     * (mirrors matchesOptionPriceBehaviorSelection's blank-date leniency) — a PRESENT expiry must
     * still match, so a frame for a different chain never leaks to the active selection.
     */
    private boolean matchesSpreadSkewSelection(String json, ActiveSelection selection) {
        if (selection == null || json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(json);
            String source = GatewaySettings.normalizeSource(text(root, "marketDataSource"));
            if (source.isBlank()) {
                source = GatewaySettings.normalizeSource(text(root, "source"));
            }
            if (!source.isBlank() && !selection.source().equals(source)) {
                return false;
            }
            String underlying = text(root, "underlying");
            String expiry = normalizeExpiry(text(root, "expiry"));
            return selection.symbol().equalsIgnoreCase(underlying)
                    && (selection.expiry().isBlank() || expiry.isBlank() || selection.expiry().equals(expiry));
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    /**
     * Whether a max-pain payload carries the terminal EXPIRED status. Terminal records evict the cache
     * so a freshly-connected client never receives a stale terminal; the gateway still forwards a
     * single EXPIRED to currently-connected matching clients so the UI can transition cleanly.
     * Defensive: a malformed payload returns {@code false} (treat as non-terminal — harmless).
     */
    private boolean isMaxPainExpired(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(json);
            String status = text(root, "status");
            // The databento-maxpain service emits a terminal (settled) record as status "TERMINAL"
            // (the v2 schema's successor to the v1 "EXPIRED"). Accept BOTH so a settled max-pain is
            // evicted from cache + forwarded once, and never replayed stale to a fresh client.
            return "TERMINAL".equals(status) || "EXPIRED".equals(status);
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    /** Cache key for the ES open-direction forecast: the tradeDate (ONE forecast per trading day). */
    private String esOpenDirectionForecastCacheKey(String json, String fallback) {
        try {
            String tradeDate = text(mapper.readTree(json), "tradeDate");
            if (!tradeDate.isBlank()) {
                return tradeDate;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to the Kafka key (also the tradeDate) if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * Cache key for an ES open-direction outcome: tradeDate|horizon — the horizon segment keeps the
     * day's H1/H2/H3 outcomes as SEPARATE last-value-wins entries, so a late-joining client replays
     * all outcomes resolved so far, not only the most recent one.
     */
    private String esOpenDirectionOutcomeCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String tradeDate = text(root, "tradeDate");
            String horizon = text(root, "horizon");
            if (!tradeDate.isBlank() && !horizon.isBlank()) {
                return tradeDate + "|" + horizon;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to the Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * Cache key for the ES open-direction live status: the tradeDate (ONE current status per trading
     * day, last 60s heartbeat wins — the forecast sibling's key shape).
     */
    private String esOpenDirectionStatusCacheKey(String json, String fallback) {
        try {
            String tradeDate = text(mapper.readTree(json), "tradeDate");
            if (!tradeDate.isBlank()) {
                return tradeDate;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to the Kafka key (also the tradeDate) if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    /**
     * One current move-authenticity verdict per symbol (e.g. "SPX", "ES"); last-value-wins. The symbol is
     * the distinguishing key component; updateCache then source-prefixes it to {@code source|symbol}
     * (the universal gateway convention — es-open-direction-status is keyed by source|tradeDate, zero-dte
     * by source|symbol|session), so the stored key, version key, eviction key, and replay key are one and
     * the same {@code DATABENTO|SPX} form everywhere.
     */
    /**
     * Cache key for close-direction: {@code V|sessionDate} (VERDICT) / {@code I|sessionDate}
     * (MONITORING). Returns null for malformed payloads (missing/unknown phase, blank
     * sessionDate or direction) — updateCache drops null-keyed records and they are never
     * broadcast (design CD-R30 malformed-drop).
     */
    private String closeDirectionCacheKey(String json, String fallback) {
        try {
            JsonNode node = mapper.readTree(json);
            String phase = text(node, "phase");
            String sessionDate = text(node, "sessionDate");
            String direction = text(node, "direction");
            if (sessionDate.isBlank() || direction.isBlank()) {
                return null;
            }
            if ("VERDICT".equals(phase)) {
                return "V|" + sessionDate;
            }
            if ("MONITORING".equals(phase)) {
                return "I|" + sessionDate;
            }
            return null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String greekMoveAuthCacheKey(String json, String fallback) {
        try {
            String symbol = text(mapper.readTree(json), "symbol").toUpperCase();
            if (!symbol.isBlank()) {
                return symbol;
            }
        } catch (JsonProcessingException ignored) {
            // Malformed payloads expire immediately via greekMoveAuthTimestamp; fallback is only used to
            // give updateCache a stable eviction key (the Kafka record key is also the symbol).
        }
        return fallback;
    }

    /**
     * Key a vol-premium reading by SYMBOL|sessionDate, matching the producer's own record key.
     * updateCache then source-prefixes it, so the stored key is source|SYMBOL|sessionDate — the
     * sessionDate component is what stops two sessions sharing one cache slot.
     */
    /**
     * The vol-premium reader, which is STRICTER than the shared mapper on one specific point.
     *
     * <p>Jackson fills a missing record component with the Java default — 0 for an int, null for a
     * reference — so a payload with maxContiguousGapSlots simply deleted deserialises to a
     * perfectly valid reading of zero, and the constructor has nothing to object to. The browser
     * refuses it, because undefined is not a count. That is precisely the divergence this
     * boundary exists to prevent: the live client keeps what it has while a late joiner is served
     * only the broken record and shows nothing.
     *
     * <p>Two features, because the same hole has two doors. FAIL_ON_MISSING_CREATOR_PROPERTIES
     * closes the omitted field. FAIL_ON_NULL_FOR_PRIMITIVES closes the adjacent one: an EXPLICIT
     * null for a primitive component is also converted to the Java default, so
     * {@code "maxContiguousGapSlots": null} arrives as a valid-looking zero by a different route.
     *
     * <p>Neither touches the four legitimately nullable fields — atmIvPct, impliedAsOfMs,
     * realisedVolPct and the spread are boxed, are null on purpose, and are validated against each
     * other by the record itself.
     */
    private static final com.fasterxml.jackson.databind.ObjectReader VOL_PREMIUM_READER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature
                            .FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature
                            .FAIL_ON_NULL_FOR_PRIMITIVES)
                    .readerFor(com.optionsedge.contracts.volpremium.IvRvReading.class);

    private String volPremiumIvrvCacheKey(String json, String fallback) {
        com.optionsedge.contracts.volpremium.IvRvReading reading;
        try {
            // The WHOLE contract, not the two fields the key is built from.
            //
            // This method is the single gate for both paths — updateCache stores under the key it
            // returns, and a null key also suppresses the broadcast — so whatever it admits is
            // what a late joiner is served. Checking only symbol and sessionDate admitted a
            // payload with a valid identity and a broken body: a bad schemaVersion, an ordinal
            // that disagreed with its own timestamp, a coverage outside [0,1], a measurement epoch
            // from another day. Such a record has a valid event time, so it passes freshness, and
            // its identity is the SAME cache slot as the good reading at a higher offset — it
            // evicts a still-fresh value and is broadcast in its place. Live browsers reject it
            // and keep what they have; a late joiner receives only the broken value and shows
            // nothing, with no way to tell that a good reading existed.
            //
            // Deserialising through the contract record makes this boundary exactly as strong as
            // the browser's and as the producer's, because it IS the producer's: every invariant
            // is enforced by the record's own constructor rather than by a copy of it kept here
            // and left to drift.
            //
            // THE COST IS DELIBERATE AND FAIL-CLOSED: this pins the gateway to the contract
            // version it was built against, so a producer that bumps schemaVersion blanks the card
            // until the gateway is redeployed with the new contract. That is the safe direction —
            // the browser pins the same version and would reject the payload anyway — and it makes
            // a contract bump a deployment-ordering fact rather than a silent divergence.
            reading = VOL_PREMIUM_READER.readValue(json);
        } catch (JsonProcessingException | RuntimeException invalid) {
            // NULL, not the record key. Falling back was justified as "malformed payloads expire
            // immediately anyway", and that is only true of unparseable JSON or a bad event time.
            // Because the Kafka key for this topic already IS "SPX|sessionDate", the fallback
            // handed a malformed record the SAME cache slot as the good value.
            return null;
        }
        // Locale.ROOT, not the JVM default: a cache KEY must not depend on the host's locale.
        String key = reading.symbol().toUpperCase(Locale.ROOT) + "|" + reading.sessionDate();
        // And the record KEY must agree with the payload it carries. Kafka's key is what compaction
        // and last-write-wins act on, so a record keyed for one session carrying another session's
        // body would take the first session's slot and hold it — the payload decides what is
        // displayed, the key decides what it displaces, and only agreement makes those the same
        // thing.
        if (fallback != null && !fallback.isBlank()
                && !fallback.toUpperCase(Locale.ROOT).equals(key)) {
            return null;
        }
        return key;
    }

    private String spotVolRegimeCacheKey(String json, String fallback) {
        try {
            String symbol = text(mapper.readTree(json), "symbol").toUpperCase();
            if (!symbol.isBlank()) {
                return symbol;
            }
        } catch (JsonProcessingException ignored) {
            // Malformed payloads expire immediately via spotVolRegimeTimestamp; fallback only gives
            // updateCache a stable eviction key (the Kafka record key is also the symbol).
        }
        return fallback;
    }

    /** One current decision per symbol/session; horizon variants are carried inside the payload. */
    private String zeroDteIntelligenceCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            String sessionDate = normalizeExpiry(text(root, "sessionDate"));
            if (!symbol.isBlank() && !sessionDate.isBlank()) {
                return symbol + "|" + sessionDate;
            }
        } catch (JsonProcessingException ignored) {
            // Malformed payloads expire immediately via zeroDteIntelligenceTimestamp; fallback is only
            // used to give updateCache a stable eviction key.
        }
        return fallback;
    }

    /** Cache key for a short-premium recommendation: the trade_id (one cache entry per trade). */
    private String shortPremiumRecommendationCacheKey(String json, String fallback) {
        try {
            String tradeId = text(mapper.readTree(json), "trade_id");
            if (!tradeId.isBlank()) {
                return tradeId;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to the Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    String indexPriceCacheKey(String json, String fallback) {
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = text(root, "symbol").toUpperCase();
            if (!symbol.isBlank()) {
                return symbol;
            }
            String instrumentId = text(root, "instrumentId");
            if (!instrumentId.isBlank()) {
                return "instrument:" + instrumentId;
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to Kafka key if the payload is unexpectedly not JSON.
        }
        return fallback;
    }

    private boolean hasGexHistory(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode history = mapper.readTree(json).path("history");
            return history.isObject() && history.fieldNames().hasNext();
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    /** Keyed view of the current session's CVD bar-close records — tf|barStartMs -> record JSON. */
    private final java.util.TreeMap<String, String> cvdBars = new java.util.TreeMap<>();
    private volatile String cvdBarsSessionDate;

    /**
     * At-least-once keyed upsert (ES-CVD-DESIGN.md R31): last record per tf|barStartMs wins, so a
     * restart's re-emission overwrites rather than duplicates. Session rollover clears the view —
     * the backfill contract is CURRENT-session bars only. A foreign-shaped record is dropped from
     * the view but still broadcast; clients apply the same keyed-upsert rule downstream.
     */
    void upsertCvdBar(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String tf = text(root, "timeframe");
            JsonNode bar = root.path("bar");
            if (tf.isEmpty() || !bar.hasNonNull("barStartMs")) return;
            String key = tf + "|" + bar.get("barStartMs").asLong();
            String sd = text(root, "sessionDate");
            synchronized (cvdBars) {
                if (!sd.isEmpty()) {
                    String current = cvdBarsSessionDate;
                    // MONOTONIC rollover (merge-gate finding 4): sessionDate is yyyymmdd, so string
                    // order IS date order. A NEWER date rolls the view forward; an OLDER date is a
                    // late/replayed record from a dead session and must never resurrect it.
                    if (current == null || sd.compareTo(current) > 0) {
                        cvdBars.clear();
                        cvdBarsSessionDate = sd;
                    } else if (sd.compareTo(current) < 0) {
                        return;                                // stale-session record: dropped
                    }
                }
                cvdBars.put(key, json);
            }
        } catch (JsonProcessingException ignored) {
        }
    }

    /** U16 boundary cap: one levels record may never exceed the design's 64 KiB record bound. */
    static final int CVD_SPX_LEVELS_MAX_BYTES = 65536;

    /**
     * U16 (CL-R7/CL-R8): the provenance a levels record carries through the hop —
     * {@code (sessionDate, foldPositionMs, foldPrints)} of the SOURCE fold that caused it, plus the
     * two combination-matrix flags. Ordering is CALENDAR-AWARE and TOTAL: sessionDate first
     * (the aligner emits it zero-padded, so lexical order IS date order), then foldPositionMs, then
     * foldPrints. A CROSSED pair — one component higher, the other lower within the same session —
     * cannot come from any single monotone fold, so it is inconsistent provenance and rejected like
     * a regression rather than ordered.
     */
    record CvdSpxLevelsProvenance(String sessionDate, long foldPositionMs, long foldPrints) {
        boolean crossed(CvdSpxLevelsProvenance other) {
            if (!sessionDate.equals(other.sessionDate)) return false;
            return (foldPositionMs > other.foldPositionMs && foldPrints < other.foldPrints)
                    || (foldPositionMs < other.foldPositionMs && foldPrints > other.foldPrints);
        }

        /** −1 / 0 / +1 against another provenance in the SAME total order the aligner uses. */
        int compareTo(CvdSpxLevelsProvenance other) {
            int c = sessionDate.compareTo(other.sessionDate);
            if (c != 0) return c < 0 ? -1 : 1;
            if (foldPositionMs != other.foldPositionMs) return foldPositionMs < other.foldPositionMs ? -1 : 1;
            if (foldPrints != other.foldPrints) return foldPrints < other.foldPrints ? -1 : 1;
            return 0;
        }
    }

    /**
     * Parsed acceptance verdict for one levels record: the bytes to forward, the SOURCE provenance
     * it carries (null when none has ever existed), and the two matrix flags.
     */
    record CvdSpxLevelsAccepted(String json, CvdSpxLevelsProvenance provenance,
                                boolean provenanceRetained, boolean baselineReset) { }

    /**
     * U16 (CL-R7/CL-R8) schema validation for ONE levels record. Refuses: oversized (pre-parse),
     * unparseable, non-object, schema major != 1 (minors ignored), state outside {OK, UNAVAILABLE},
     * and every combination outside the design's provenance MATRIX:
     *
     * <ul>
     *   <li>tombstone-derived absence — {null provenance, baselineReset true, retained false}</li>
     *   <li>pre-first-source startup absence — {null, false, false} (never resets)</li>
     *   <li>provenance-unrecoverable malformed — {non-null, false, retained true}</li>
     *   <li>causally-validated source-derived — {non-null, false, false}</li>
     * </ul>
     *
     * Anything else is MALFORMED and dropped — never forwarded, and never treated as a baseline
     * erase: an invalid reset combination must not be able to wipe the gateway's retention.
     */
    CvdSpxLevelsAccepted validateCvdSpxLevels(String json) {
        return validateCvdSpxLevels(CVD_SPX_LEVELS_KEY, json);
    }

    /** The topic's contractual key; the payload's symbol must agree with it (CL-R7). */
    static final String CVD_SPX_LEVELS_KEY = "ES.v.0";

    /**
     * U16 (§2 / CL-R7 / CL-R8): the FULL gateway schema gate for one aligned levels record. The
     * record reaches the browser byte-for-byte, so anything this misses is something a page has to
     * survive — the aligner validating its own output is not a substitute for the boundary between
     * a producer and every client.
     *
     * <p>Refused: a foreign Kafka key or a payload {@code symbol} disagreeing with it; oversize
     * (pre-parse); unparseable or non-object; schema major != 1; a state outside {OK, UNAVAILABLE};
     * any field the state forbids; a missing or unknown UNAVAILABLE reason; every provenance
     * combination outside the matrix; and, on OK records, the whole structure contract — required
     * envelope fields, array bounds, per-level shape with the side-sign rule and duplicate-price
     * rejection, flip and balance shape, basis fields with a known state, and the cross-field
     * bounds (level touches and the flip crossing lie at or before the record's own flow time).
     * Every integral field must be JS-exact and non-truncating; a {@code BigIntegerNode} is
     * integral and {@code asLong()} would wrap it.
     */
    CvdSpxLevelsAccepted validateCvdSpxLevels(String key, String json) {
        if (json == null || !CVD_SPX_LEVELS_KEY.equals(key)) return null;
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > CVD_SPX_LEVELS_MAX_BYTES) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode n = mapper.readTree(json);
            if (n == null || !n.isObject()) return null;
            if (!n.path("schemaVersion").asText("").matches("1\\.\\d+\\.\\d+")) return null;
            if (!CVD_SPX_LEVELS_KEY.equals(n.path("symbol").asText(""))) return null;
            Long alignedAtMs = levelsInt(n, "alignedAtMs");
            if (alignedAtMs == null || alignedAtMs <= 0) return null;
            String state = n.path("state").asText("");

            if ("OK".equals(state)) {
                for (String unavailableOnly : CVD_SPX_LEVELS_UNAVAILABLE_ONLY_FIELDS) {
                    if (n.has(unavailableOnly)) return null;      // present at all, whatever its value
                }
                if (!n.path("sessionComplete").isBoolean()) return null;
                // TEXTUAL by contract: asText() coerces the integer 20260817 into "20260817", so a
                // strict-type violation would pass a check that looks like it enforces the type.
                if (!n.path("sessionDate").isTextual()) return null;
                String sessionDate = n.path("sessionDate").asText("");
                if (!sessionDate.matches("\\d{8}") || !isCalendarDate(sessionDate)) return null;
                Long flow = levelsInt(n, "flowEventTimeMs");
                Long sourcePublished = levelsInt(n, "sourcePublishedAtMs");
                Long basisCents = levelsInt(n, "basisCents");
                Long basisMeasured = levelsInt(n, "basisMeasuredAtMs");
                Long pos = levelsInt(n, "foldPositionMs");
                Long prints = levelsInt(n, "foldPrints");
                if (flow == null || flow < 0 || sourcePublished == null || sourcePublished <= 0
                        || basisCents == null || basisMeasured == null || basisMeasured <= 0
                        || pos == null || pos < 0 || prints == null || prints < 0) {
                    return null;
                }
                if (!CVD_SPX_LEVELS_BASIS_STATES.contains(n.path("basisState").asText(""))) return null;
                if (!validLevelsArray(n.get("buyLevels"), true, flow)
                        || !validLevelsArray(n.get("sellLevels"), false, flow)) {
                    return null;
                }
                com.fasterxml.jackson.databind.JsonNode flip = n.get("flip");
                if (flip == null) return null;                            // present, possibly null
                if (!flip.isNull()) {
                    if (!flip.isObject()) return null;
                    Long fp = levelsInt(flip, "priceCents");
                    Long at = levelsInt(flip, "atMs");
                    Long cross = levelsInt(flip, "cvdAtCross");
                    String dir = flip.path("direction").asText("");
                    if (fp == null || fp <= 0 || fp > CVD_SPX_LEVELS_MAX_PRICE_CENTS
                            || at == null || at <= 0 || at > flow || cross == null
                            || (!"UP".equals(dir) && !"DOWN".equals(dir))) {
                        return null;
                    }
                }
                com.fasterxml.jackson.databind.JsonNode balance = n.get("balancePriceCents");
                if (balance == null) return null;                         // present, possibly null
                if (!balance.isNull()) {
                    Long b = levelsInt(n, "balancePriceCents");
                    if (b == null || b <= 0 || b > CVD_SPX_LEVELS_MAX_PRICE_CENTS) return null;
                }
                return new CvdSpxLevelsAccepted(json,
                        new CvdSpxLevelsProvenance(sessionDate, pos, prints), false, false);
            }
            if (!"UNAVAILABLE".equals(state)) return null;

            if (!CVD_SPX_LEVELS_REASONS.contains(n.path("reason").asText(""))) return null;
            for (String okOnly : CVD_SPX_LEVELS_OK_ONLY_FIELDS) {
                if (n.has(okOnly)) return null;                           // OK-only field on UNAVAILABLE
            }
            if (!n.path("provenanceRetained").isBoolean() || !n.path("baselineReset").isBoolean()) return null;
            if (!n.has("sourceProvenance")) return null;
            boolean retained = n.path("provenanceRetained").asBoolean();
            boolean reset = n.path("baselineReset").asBoolean();
            com.fasterxml.jackson.databind.JsonNode sp = n.get("sourceProvenance");
            CvdSpxLevelsProvenance p = null;
            if (!sp.isNull()) {
                p = provenanceOfUnavailable(sp);
                if (p == null) return null;
            }
            boolean hasProvenance = p != null;
            boolean matrixOk =
                    (!hasProvenance && reset && !retained)       // tombstone-derived absence
                    || (!hasProvenance && !reset && !retained)   // pre-first-source startup absence
                    || (hasProvenance && !reset && retained)     // provenance-unrecoverable malformed
                    || (hasProvenance && !reset && !retained);   // causally validated
            if (!matrixOk) return null;
            return new CvdSpxLevelsAccepted(json, p, retained, reset);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * U16 startup hydration: read the compacted levels partition's latest COMMITTED record and
     * apply it through the ordinary validate+retain path, so a restarted gateway serves the
     * governing record in its very first hello instead of a null. Bounded and best-effort by
     * design — a failure leaves the replay empty, which the page renders as no_data (fail-closed),
     * and the next live record hydrates it anyway.
     */
    /** Bounded: this runs on the startup path, so it must never hold the service down. */
    static final long CVD_SPX_LEVELS_HYDRATE_DEADLINE_MS = 15_000L;

    void hydrateCvdSpxLevels() {
        String topic = settings.esCvdSpxLevelsTopic();
        Properties props = stringObjectConsumerProperties("cvd-spx-levels-hydrate");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
        // ONE absolute deadline, taken BEFORE the first Kafka call and spent down by every blocking
        // operation including the close. Per-call 10s timeouts do not bound a startup step: three
        // metadata calls plus a position() plus a bounded close can each pay their own timeout, and
        // "15 seconds" would quietly become a minute on a sick broker.
        final long deadlineNanos = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(CVD_SPX_LEVELS_HYDRATE_DEADLINE_MS);
        // The CONSTRUCTOR is inside the guard: a config, security or client-initialization failure
        // here would otherwise escape a best-effort startup step and abort @PostConstruct — the
        // opposite of the fail-closed levels:null this promises.
        KafkaConsumer<String, Object> consumer = null;
        try {
            consumer = new KafkaConsumer<>(props);
            var infos = consumer.partitionsFor(topic, hydrateRemaining(deadlineNanos));
            if (infos == null || infos.isEmpty()) return;               // topic not created yet
            if (infos.size() != 1) {
                System.err.println("cvd-spx-levels hydration skipped: " + topic + " has "
                        + infos.size() + " partitions, expected 1");
                return;
            }
            TopicPartition tp = new TopicPartition(topic, infos.get(0).partition());
            consumer.assign(List.of(tp));
            long begin = consumer.beginningOffsets(List.of(tp), hydrateRemaining(deadlineNanos)).get(tp);
            long end = consumer.endOffsets(List.of(tp), hydrateRemaining(deadlineNanos)).get(tp);
            // The end offset is the HANDOFF POINT: the live consumer starts this partition here
            // instead of at its own later seekToEnd, so a record committed between the two can no
            // longer fall into a gap that leaves the hydrated record replaying indefinitely.
            cvdSpxLevelsHandoffOffset.set(end);
            if (end <= begin) return;                                   // empty log
            consumer.seek(tp, begin);
            // A read_committed scan can legitimately return NOTHING between begin and end — the
            // range may hold only aborted batches or transaction control records. That is not a
            // withdrawal, so it must not be counted as one; the two states are tracked apart.
            boolean sawRecord = false;
            String latestValue = null;
            while (consumer.position(tp, hydrateRemaining(deadlineNanos)) < end) {
                if (System.nanoTime() >= deadlineNanos) {
                    System.err.println("cvd-spx-levels hydration timed out; the first hello may carry no levels");
                    return;
                }
                for (ConsumerRecord<String, Object> rec : consumer.poll(hydrateRemaining(deadlineNanos))) {
                    if (rec.offset() >= end) continue;
                    // PER KEY, because compaction is per key: a later foreign-key record can sit
                    // after the governing ES.v.0 one, and taking "the last record in the
                    // partition" would drop the real state, leave the replay empty, and hand the
                    // live consumer an offset past it — invisible until the next heartbeat, or
                    // forever with the aligner stopped. Live ingestion already refuses a foreign
                    // key without displacing retained state; hydration now matches it, counting
                    // the foreign record exactly as the live path would.
                    if (!CVD_SPX_LEVELS_KEY.equals(rec.key())) {
                        cvdSpxLevelsDrops.incrementAndGet();
                        continue;
                    }
                    sawRecord = true;
                    latestValue = rec.value() == null ? null : String.valueOf(rec.value());
                }
            }
            if (!sawRecord) return;                                     // nothing committed to hydrate from
            if (latestValue == null) {
                // A retained TOMBSTONE is a withdrawal, and CL-R8 counts every one of them. Being
                // silent here made the counter depend on whether the gateway happened to restart
                // after the wipe — the same operator action, two different histories.
                cvdSpxLevelsDrops.incrementAndGet();
                return;
            }
            CvdSpxLevelsAccepted accepted = validateCvdSpxLevels(CVD_SPX_LEVELS_KEY, latestValue);
            if (accepted == null) {
                cvdSpxLevelsDrops.incrementAndGet();                    // malformed retained: stay empty
                return;
            }
            retainCvdSpxLevels(accepted);
        } catch (RuntimeException e) {
            System.err.println("cvd-spx-levels hydration failed; the first hello may carry no levels: " + e);
        } finally {
            if (consumer != null) {
                try {
                    consumer.close(hydrateRemaining(deadlineNanos));    // the close is inside the budget too
                } catch (RuntimeException ignored) { }
            }
        }
    }

    /**
     * U16 handoff: hydration read to end offset E and closed its consumer; the live consumer would
     * otherwise seekToEnd to a LATER E', silently skipping everything committed in between — and
     * if the aligner then stopped, the gateway would replay the older hydrated record forever.
     * Starting this one partition at E instead makes the two reads continuous. Consumed once, so a
     * reconnect after a live tombstone cannot rewind to a pre-tombstone position.
     */
    /**
     * Where a levels cursor may actually be honoured. Returns the offset to seek to, or
     * {@link #CVD_SPX_LEVELS_SEEK_END} when the cursor cannot be trusted: no cursor at all, or one
     * outside the partition's CURRENT range. The log moves underneath a process-local cursor —
     * compaction and retention advance the start, a recreation or truncation can leave the whole
     * log behind it — and this partition shares the state-live consumer, so an out-of-range seek
     * would take every other JSON state stream down with it (repeated OffsetOutOfRange below the
     * beginning, a stranded position above the end).
     *
     * <p>Pure on purpose: the decision is the part worth testing, and it is testable without a
     * broker.
     */
    static long resolveCvdSpxLevelsSeek(long cursor, long beginningOffset, long endOffset) {
        if (cursor < 0) return CVD_SPX_LEVELS_SEEK_END;
        if (cursor < beginningOffset || cursor > endOffset) return CVD_SPX_LEVELS_SEEK_END;
        return cursor;
    }

    /** Sentinel: "do not trust the cursor, start at the end". */
    static final long CVD_SPX_LEVELS_SEEK_END = -1L;

    /** Apply the decision, reading the live range; any failure takes the safe end-seek. */
    private void seekCvdSpxLevelsWithin(KafkaConsumer<String, Object> consumer, TopicPartition owned,
                                        long cursor, AtomicLong cursorHolder) {
        List<TopicPartition> one = List.of(owned);
        long target;
        try {
            long beginning = consumer.beginningOffsets(one, Duration.ofSeconds(10)).get(owned);
            long end = consumer.endOffsets(one, Duration.ofSeconds(10)).get(owned);
            target = resolveCvdSpxLevelsSeek(cursor, beginning, end);
        } catch (RuntimeException e) {
            target = CVD_SPX_LEVELS_SEEK_END;                  // range unknown: the safe fallback
        }
        if (target == CVD_SPX_LEVELS_SEEK_END) {
            if (cursorHolder != null) cursorHolder.set(-1L);    // stale: never retried forever
            consumer.seekToEnd(one);
            return;
        }
        consumer.seek(owned, target);
    }

    private void seekCvdSpxLevelsToHandoff(KafkaConsumer<String, Object> consumer,
                                           List<TopicPartition> partitions) {
        // OWNERSHIP FIRST, consumption second. Both live consumers run this helper, and consuming
        // the offset before finding the partition let whichever ran first (avro-live, which never
        // holds this topic) destroy the handoff — leaving state-live at its own later seekToEnd
        // and reopening the very gap this exists to close. getAndSet then runs only in the
        // consumer that actually owns the partition, so exactly one seek can happen.
        String topic = settings.esCvdSpxLevelsTopic();
        TopicPartition owned = null;
        for (TopicPartition tp : partitions) {
            if (tp.topic().equals(topic)) {
                owned = tp;
                break;
            }
        }
        if (owned == null) return;
        long handoff = cvdSpxLevelsHandoffOffset.getAndSet(-1L);
        if (handoff < 0) return;
        // Same lifecycle exposure as the retry cursor: between hydration and this seek, retention,
        // compaction, truncation or a recreation can make the handoff offset invalid.
        seekCvdSpxLevelsWithin(consumer, owned, handoff, null);
    }

    /** Remember where this partition got to, so a retry resumes rather than replays. */
    private void noteCvdSpxLevelsProgress(TopicBinding binding, ConsumerRecord<String, ?> record) {
        if (binding != null && "es-cvd-spx-levels".equals(binding.event())) {
            cvdSpxLevelsNextOffset.set(record.offset() + 1);
        }
    }

    /**
     * U16 retry continuity. The generic retry path seeks every partition back into its cache
     * window — up to 15 minutes — which on a COMPACTED state topic means re-reading history that
     * has not been cleaned yet. A historical tombstone would then erase the baseline again, a
     * historical {@code baselineReset} would be re-applied as a fresh operator wipe, and older
     * post-reset records would be accepted and broadcast on the way back to now, with a concurrent
     * hello seeing levels:null in the middle of it. None of that is new information: this consumer
     * already processed those records. So the levels partition resumes from where it left off, and
     * when nothing is known it starts at the END rather than in the past — a missed record is a
     * bounded staleness the page already handles, while a replayed erase is a false one.
     */
    private void resumeCvdSpxLevels(KafkaConsumer<String, Object> consumer, List<TopicPartition> partitions) {
        String topic = settings.esCvdSpxLevelsTopic();
        TopicPartition owned = null;
        for (TopicPartition tp : partitions) {
            if (tp.topic().equals(topic)) {
                owned = tp;
                break;
            }
        }
        if (owned == null) return;
        seekCvdSpxLevelsWithin(consumer, owned, cvdSpxLevelsNextOffset.get(), cvdSpxLevelsNextOffset);
    }

    /** What is left of the hydration budget; never negative, so a blown budget stops immediately. */
    private static Duration hydrateRemaining(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        return remaining <= 0 ? Duration.ZERO : Duration.ofNanos(remaining);
    }

    /**
     * The basis states an OK record may carry. SOURCE OF TRUTH: the aligner's
     * {@code CvdSpxLevelsAligner.OK_BASIS_STATES} in options-edge-processing, which is itself
     * parity-tested against {@code BasisSnapshot.isValid}. This repo cannot import that constant,
     * so the set is pinned by a test here that names the same authority — a drift on either side
     * fails a test rather than silently accepting (or rejecting) a state at the boundary.
     */
    static final Set<String> CVD_SPX_LEVELS_BASIS_STATES =
            Set.of("ANCHORED", "MEASURED", "PROJECTED");

    /** The aligner's reason precedence chain — an unknown reason is a schema violation. */
    private static final Set<String> CVD_SPX_LEVELS_REASONS = Set.of(
            "source_absent", "source_malformed", "source_wrong_session", "source_future_dated",
            "source_overflow", "source_not_observable", "source_stale", "basis_unusable",
            "translation_error");

    /**
     * The state-field matrix, both directions. A record carrying a field its state does not define
     * is malformed WHATEVER the field's value: the record reaches the browser verbatim, so an
     * UNAVAILABLE frame with a sessionDate and fold provenance in it reads as structure the
     * aligner never attested.
     */
    private static final List<String> CVD_SPX_LEVELS_OK_ONLY_FIELDS = List.of(
            "buyLevels", "sellLevels", "flip", "balancePriceCents", "basisCents", "basisState",
            "basisMeasuredAtMs", "sourcePublishedAtMs", "flowEventTimeMs", "sessionComplete",
            "sessionDate", "foldPositionMs", "foldPrints");

    private static final List<String> CVD_SPX_LEVELS_UNAVAILABLE_ONLY_FIELDS = List.of(
            "reason", "sourceProvenance", "provenanceRetained", "baselineReset");

    /**
     * One integral field, read WITHOUT truncation and bounded to JS-exact. A BigIntegerNode is
     * integral and {@code asLong()} silently wraps it, which would let an oversized value pass a
     * positive check and then corrupt ordering — so convertibility is required, then the bound.
     */
    private static Long levelsInt(com.fasterxml.jackson.databind.JsonNode parent, String field) {
        com.fasterxml.jackson.databind.JsonNode v = parent.get(field);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong()) return null;
        long x = v.asLong();
        return (x > CVD_SPX_LEVELS_MAX_SAFE || x < -CVD_SPX_LEVELS_MAX_SAFE) ? null : x;
    }

    /** JavaScript's exact-integer bound: the browser is the consumer of every one of these. */
    static final long CVD_SPX_LEVELS_MAX_SAFE = (1L << 53) - 1;

    /** §2: every translated SPX price is in (0, 10_000_000] cents — balance included. */
    static final long CVD_SPX_LEVELS_MAX_PRICE_CENTS = 10_000_000L;

    /** A real calendar date, not merely eight digits — 99999999 must not order as a session. */
    private static boolean isCalendarDate(String yyyymmdd) {
        try {
            java.time.LocalDate.parse(yyyymmdd, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    /** One side's levels: bounded array, per-level shape, side-sign rule, no duplicate prices. */
    private static boolean validLevelsArray(com.fasterxml.jackson.databind.JsonNode arr,
                                            boolean buy, long flowEventTimeMs) {
        if (arr == null || !arr.isArray() || arr.size() > 10) return false;
        Set<Long> prices = new java.util.HashSet<>();
        for (com.fasterxml.jackson.databind.JsonNode l : arr) {
            if (!l.isObject()) return false;
            Long price = levelsInt(l, "priceCents");
            Long delta = levelsInt(l, "deltaSum");
            Long count = levelsInt(l, "tradeCount");
            Long touch = levelsInt(l, "lastTouchMs");
            if (price == null || price <= 0 || price > CVD_SPX_LEVELS_MAX_PRICE_CENTS
                    || !prices.add(price)) return false;
            if (delta == null || (buy ? delta <= 0 : delta >= 0)) return false;   // side sign
            if (count == null || count <= 0 || touch == null || touch <= 0) return false;
            if (touch > flowEventTimeMs) return false;      // bounded by the record's own flow time
        }
        return true;
    }

    /** OK records carry the source's provenance inline (sessionDate + foldPositionMs + foldPrints). */
    /** UNAVAILABLE records carry it under sourceProvenance when any validated record has existed. */
    private static CvdSpxLevelsProvenance provenanceOfUnavailable(com.fasterxml.jackson.databind.JsonNode sp) {
        if (!sp.isObject() || !sp.path("sessionDate").isTextual()) return null;   // textual by contract
        String sd = sp.path("sessionDate").asText("");
        Long pos = levelsInt(sp, "foldPositionMs");
        Long prints = levelsInt(sp, "foldPrints");
        if (!sd.matches("\\d{8}") || !isCalendarDate(sd)
                || pos == null || pos < 0 || prints == null || prints < 0) {
            return null;
        }
        return new CvdSpxLevelsProvenance(sd, pos, prints);
    }

    /**
     * U16 retention (CL-R7 V1, hop-to-hop freshness): accept the record iff it does not REGRESS
     * against what is retained, applying the SAME calendar-aware total order the aligner applies.
     * ONLY the exact tombstone combination resets the baseline; a provenance-less startup-absence
     * record is accepted only when nothing is retained (never a regression to "nothing known").
     * Rejections increment gateway_cvd_spx_levels_position_regressions_total and forward nothing,
     * so a lagging incarnation can never displace a newer attestation on the browser.
     */
    synchronized boolean retainCvdSpxLevels(CvdSpxLevelsAccepted accepted) {
        if (accepted.baselineReset()) {
            cvdSpxLevelsProvenance = null;                       // operator wipe flows through
            cvdSpxLevelsLatest.set(accepted.json());
            return true;
        }
        CvdSpxLevelsProvenance incoming = accepted.provenance();
        if (incoming == null) {
            if (cvdSpxLevelsProvenance != null) {
                cvdSpxLevelsRegressions.incrementAndGet();
                return false;
            }
            cvdSpxLevelsLatest.set(accepted.json());
            return true;
        }
        CvdSpxLevelsProvenance held = cvdSpxLevelsProvenance;
        if (held != null && (incoming.crossed(held) || incoming.compareTo(held) < 0)) {
            cvdSpxLevelsRegressions.incrementAndGet();
            return false;
        }
        cvdSpxLevelsProvenance = incoming;
        cvdSpxLevelsLatest.set(accepted.json());
        return true;
    }

    /**
     * U16 withdrawal handling: a TOMBSTONE on the levels topic (reset tooling) clears the connect
     * replay AND the retention baseline — a withdrawn record must never be served to a late joiner,
     * and the next record must not be judged against an erased history. Counted so the operation is
     * visible. Non-tombstone unparseable values just count.
     */
    synchronized void evictCvdSpxLevelsTombstone(String event, org.apache.kafka.clients.consumer.ConsumerRecord<String, ?> record) {
        if (!"es-cvd-spx-levels".equals(event)) {
            return;
        }
        // A withdrawal is as governing as a value, so it passes the SAME key gate: a foreign-key
        // tombstone is malformed input, counted and ignored, never an erase of the baseline.
        if (record.value() == null && CVD_SPX_LEVELS_KEY.equals(record.key())) {
            cvdSpxLevelsLatest.set(null);
            cvdSpxLevelsProvenance = null;
        }
        cvdSpxLevelsDrops.incrementAndGet();
    }

    java.util.concurrent.atomic.AtomicReference<String> cvdSpxLevelsLatestForTest() {
        return cvdSpxLevelsLatest;
    }

    long cvdSpxLevelsDropsForTest() {
        return cvdSpxLevelsDrops.get();
    }

    long cvdSpxLevelsRegressionsForTest() {
        return cvdSpxLevelsRegressions.get();
    }

    /** R46 hello payload: {"sessionDate":...,"hwm":{"30s":<lastBarStartMs>,...}}. */
    String cvdHelloJson() {
        StringBuilder sb = new StringBuilder("{\"sessionDate\":");
        String sd = cvdBarsSessionDate;
        sb.append(sd == null ? "null" : "\"" + sd + "\"").append(",\"hwm\":{");
        boolean first = true;
        for (java.util.Map.Entry<String, Long> e : cvdBarsHighWaterMarks().entrySet()) {
            if (!first) sb.append(',');
            sb.append('\"').append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append('}');
        if (settings.esCvdSpxLevelsEnabled()) {
            // Verbatim record or an explicit null — the FIELD's presence is the completion signal.
            String levels = cvdSpxLevelsLatest.get();
            sb.append(",\"levels\":").append(levels == null ? "null" : levels);
        }
        return sb.append('}').toString();
    }

    /** One ATOMIC backfill page: session check, rows, cursor and session stamp under one lock. */
    public record CvdBarsPage(String sessionDate, boolean sessionMismatch,
                              java.util.List<String> bars, Long nextCursor) { }

    /**
     * R46 (merge-gate finding 3): the mismatch check, the rows, the response session and the
     * cursor are ONE synchronized snapshot — a rollover can happen before or after this call,
     * never inside it, so a page can never mix sessions or mislabel itself.
     */
    public CvdBarsPage cvdBarsPage(String timeframe, long toMsInclusive, long afterMsExclusive,
                                   int limit, String expectedSessionDate) {
        synchronized (cvdBars) {
            String current = cvdBarsSessionDate;
            if (expectedSessionDate != null && !expectedSessionDate.isEmpty()
                    && current != null && !expectedSessionDate.equals(current)) {
                return new CvdBarsPage(current, true, java.util.List.of(), null);
            }
            java.util.List<String> out = new java.util.ArrayList<>();
            long lastStart = -1;
            for (java.util.Map.Entry<String, String> e : cvdBars.entrySet()) {
                int sep = e.getKey().lastIndexOf('|');
                if (!e.getKey().substring(0, sep).equals(timeframe)) continue;
                long start = Long.parseLong(e.getKey().substring(sep + 1));
                if (start <= afterMsExclusive || start > toMsInclusive) continue;
                out.add(e.getValue());
                lastStart = start;
                if (out.size() >= limit) break;
            }
            return new CvdBarsPage(current, false, out, out.size() >= limit ? lastStart : null);
        }
    }

    public String cvdBarsSessionDate() { return cvdBarsSessionDate; }

    /** Per-timeframe high-water marks of the keyed CVD bar view, for the R46 handshake. */
    public java.util.Map<String, Long> cvdBarsHighWaterMarks() {
        java.util.Map<String, Long> hwm = new java.util.LinkedHashMap<>();
        synchronized (cvdBars) {
            for (String key : cvdBars.keySet()) {
                int sep = key.lastIndexOf('|');
                hwm.merge(key.substring(0, sep), Long.parseLong(key.substring(sep + 1)), Math::max);
            }
        }
        return hwm;
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static long longField(JsonNode root, String field, long fallback) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.canConvertToLong()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText("").trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double doubleField(JsonNode root, String field, double fallback) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(value.asText("").trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String formatStrike(double strike) {
        if (strike == Math.rint(strike)) {
            return Long.toString((long) strike);
        }
        return Double.toString(strike);
    }

    private static long parseInstantMs(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback > 0L ? fallback : System.currentTimeMillis();
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (RuntimeException ignored) {
            return fallback > 0L ? fallback : System.currentTimeMillis();
        }
    }

    private static String normalizeExpiry(String expiry) {
        return expiry == null ? "" : expiry.trim().replace("-", "");
    }

    private String eventFromCacheKey(String key) {
        int separator = key.indexOf(':');
        return separator <= 0 ? key : key.substring(0, separator);
    }

    private String avroJson(Object value) {
        if (!(value instanceof GenericRecord record)) {
            return null;
        }
        try {
            return mapper.writeValueAsString(AvroJson.toJsonNode(mapper, record));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize Avro record as JSON", e);
        }
    }

    private String stringJson(Object value) {
        return value == null ? null : value.toString();
    }

    private void broadcast(String event, String json) {
        // Per-session routing: only explicitly allowlisted global events may fan out to every
        // socket. Any other event reaching here (e.g. a market-data event via a fallback path) is
        // dropped so it can never leak to another user. Legacy mode broadcasts everything.
        if (perSessionRouting() && !isGlobalBroadcastEvent(event)) {
            droppedNonRoutableEvents.incrementAndGet();
            return;
        }
        for (WebSocketSession client : clients) {
            send(client, event, json);
        }
    }

    /** Per-session filtered replay of cached state to a newly-connected socket (FR-11). */
    private void replayCachedToSocket(WebSocketSession session) {
        // Purge FIRST so no replay path (connect OR return-to-live via resumeLive) can serve a cache
        // entry that crossed its TTL since the last consumer purge. Critical for the joined dealer-ledger
        // envelope: an expired role evicts the envelope (removeCacheEntry), so a stale pill can't replay.
        purgeExpiredCache(System.currentTimeMillis());
        replayCacheMap(session, "snapshot", snapshots);
        replayCacheMap(session, "pace", paces);
        replayCacheMap(session, "pace-rank", paceRanks);
        replayCacheMap(session, "directional-pressure", directionalPressures);
        replayCacheMap(session, "strike-flow", strikeFlows);
        // Bands go out as ONE batch, not through replayCacheMap: that sends a socket message per cache
        // entry and the web client has no handler for a "spot-band" message, so ~200 per-strike messages
        // were simply discarded by the browser. It belongs HERE rather than in addClient because this is
        // the common per-socket replay path, which has exactly two triggers: connect (addClient) and
        // return-to-live (resumeLive -> replayLiveCacheToAppSession). A SOURCE SWITCH does NOT come
        // through here — that is handled separately in applySelection/markSelectionReady, because in
        // per-session mode broadcastCachedState drops the switch batch outright.
        replaySpotBandBatchToSocket(session);
        replayCacheMap(session, "delta-flow", deltaFlows);
        replayCacheMap(session, "strike-intel", strikeIntels);
        replayCacheMap(session, "option-truth", optionTruths);
        replayCacheMap(session, "strike-invasion", strikeInvasions);
        // Mission-level state is low-frequency in replay/off-hours dev. Replay the fresh cached value on
        // connect, still routed by source|symbol|expiry so it cannot leak to another selected market.
        replayCacheMap(session, "mission-pace", missionPaces);
        replayCacheMap(session, "mission-control", missionControls);
        replayCacheMap(session, "spread-skew", spreadSkews);
        replayCacheMap(session, "gex-by-strike", gexByStrike);
        replayCacheMap(session, "gex-oi-status", gexOiStatus);
        replayCacheMap(session, "strike-sr", strikeSr);
        replayCacheMap(session, "gex-magnet", gexMagnet);
        replayCacheMap(session, "gamma-migration", gammaMigration);
        replayCacheMap(session, "es-gex", esGex);
        replayCacheMap(session, "es-strike-intel", esStrikeIntel);
        replayCacheMap(session, "gex-strike-lifecycle", gexStrikeLifecycle);
        // strike-cluster (dashboard + recent-signals trail): broadcast is unconditional live, so the
        // replay is unconditional too — the client symbol-filters (fail-closed). Without this a
        // refreshed page shows an EMPTY trail until the next dashboard interval (§9b).
        for (String clusterJson : strikeClusters.values()) {
            if (clusterJson != null && !clusterJson.isBlank()) {
                send(session, "strike-cluster", clusterJson);
            }
        }
        // hot-strike: same standalone replay as legacy mode (§4.4).
        replayHotStrikeCached(session);
        // liquidity-heatmap replays WITH the freshness gate below (5s TTL): only a live-fresh
        // column frame bootstraps a new socket; anything older is simply absent and the UI
        // fills forward from the next live frame.
        replayCacheMap(session, "liquidity-heatmap", liquidityHeatmaps);
        replayCacheMap(session, "max-pain", maxPain);
        replayCacheMap(session, "option-price-behavior", optionPriceBehaviors);
        // The joined dealer-ledger envelope, standalone per session. dealerLedgers holds only envelopes
        // built from fresh halves (joinDealerLedger) and evicted on role expiry (removeCacheEntry).
        replayCacheMap(session, "dealer-ledger", dealerLedgers);
        // corridor-gauge per-session replay goes through the GENERIC path on purpose
        // (UI-review r2 #2): replayCacheMap applies BOTH the per-entry freshness gate (the
        // event is registered in requiresFreshPerSessionReplay) and the routing engine's
        // shouldDeliverToSocket — the bespoke helper would bypass selection isolation here.
        replayCacheMap(session, "corridor-gauge", corridorGauges);
        replayCacheMap(session, "opb-by-option", opbByOptions);
        replayCacheMap(session, "opb-session", opbSessions);
        // P1: replay each underlying cache with its ORIGINAL event type — VIX (SHARED) as vix-price, ES/index
        // as index-price — so a VIX record is never delivered mislabelled as index-price.
        replayCacheMap(session, "vix-price", vixPrices);
        replayCacheMap(session, "index-price", indexPrices);
        // Canonical SPX spot, replayed with its own event type (never flattened to index-price). Its
        // cache key is source-prefixed ("DATABENTO|SPX", see updateCache), so the generic source
        // resolution above recovers the binding source.
        replayCacheMap(session, "spx-price", spxPrices);
        // Agent A recommendations: standalone per session, one envelope per cached trade_id.
        replayCacheMap(session, "short-premium-recommendation", shortPremiumRecommendations);
        // ES open-direction forecast + outcomes are STANDALONE global advisories: replayCacheMap
        // cannot deliver them (GatewayRecordMapper has no route), so replay them directly. Placing
        // this here (not only in addClient) also restores the panel after return-to-live from a
        // historical replay (replayLiveCacheToAppSession -> replayCachedToSocket).
        replayEsOpenDirectionCached(session);
        // Greek-move-authenticity CURRENT verdict is the same STANDALONE global-advisory class:
        // replayCacheMap cannot deliver it (GatewayRecordMapper has no route), so replay it directly here
        // too, which restores the move-authenticity track in per-session (auth) mode and after
        // return-to-live from a historical replay (replayLiveCacheToAppSession -> replayCachedToSocket).
        replayGreekMoveAuthCached(session);
        replaySpotVolRegimeCached(session);
        // Same STANDALONE global-advisory class, and the same reason it must be here as well as in
        // addClient: this is the path that serves per-session (auth) connections and return-to-live
        // from a historical replay. Without it the card was permanently blank in authenticated
        // prod, which is the only mode prod runs in.
        replayVolPremiumIvrvCached(session);
        if (stateCaughtUp.get()) {
            // R-WIRE.5 high-watermark-before-serving: a mid-bootstrap auth connection must not
            // see strike/path rows that a not-yet-consumed revocation or generation supersedes.
            // The caught-up re-push covers this client the moment the barrier clears.
            replayIbkrPreOpenCached(session);
        }
        if (settings.ibkrPreOpenEnabled() && ibkrPreOpenGexServingUp()) {
            // Value-plane sibling of the block above, gated on BOTH consumers (round-2
            // finding 1): values ride the avro consumer, but the revocation/generation
            // controls and pairing statuses ride the JSON state consumer — serving on avro
            // alone could expose a value whose retained revocation has not been consumed
            // yet. The markCacheCaughtUp re-push covers this client the moment the LAST
            // barrier clears (both consumers' event lists contain "gex-by-strike").
            replayIbkrPreOpenGexCached(session);
        }
    }

    private void replayCacheMap(WebSocketSession session, String event, Map<String, String> cache) {
        for (String json : deliverableCacheEntries(session, event, cache)) {
            send(session, event, json);
        }
    }

    /**
     * The entries of {@code cache} this socket is allowed to see, in cache order. Shared by the
     * per-entry replay above and the batch replay below so the freshness and per-session routing
     * filters can never drift apart between the two delivery shapes.
     */
    private List<String> deliverableCacheEntries(WebSocketSession session, String event, Map<String, String> cache) {
        List<String> deliverable = new ArrayList<>();
        if (routingEngine == null) {
            return deliverable;
        }
        String socketId = session.getId();
        long nowMs = System.currentTimeMillis();
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            String json = entry.getValue();
            if (json == null || json.isBlank()) {
                continue;
            }
            // Freshness gate for short-lived derived state: never replay a bucket/frame that crossed its
            // TTL between purge ticks on per-session bootstrap / return-to-live.
            if (requiresFreshPerSessionReplay(event) && !isCacheFresh(event + ":" + entry.getKey(), nowMs)) {
                continue;
            }
            try {
                JsonNode root = mapper.readTree(json);
                // Cache keys are "SOURCE|..." (see updateCache); use that as the authoritative source
                // since Avro contract payloads carry no marketDataSource field.
                String key = entry.getKey();
                int bar = key == null ? -1 : key.indexOf('|');
                String source = bar > 0 ? key.substring(0, bar)
                        : (root.hasNonNull("marketDataSource") ? root.get("marketDataSource").asText("") : "");
                Optional<RoutableRecord> rec = GatewayRecordMapper.toRoutableRecord(source, event, root);
                if (rec.isPresent() && routingEngine.shouldDeliverToSocket(rec.get(), socketId)) {
                    deliverable.add(json);
                }
            } catch (JsonProcessingException ignored) {
                // skip malformed cached entry
            }
        }
        return deliverable;
    }

    /**
     * Bands as ONE ui-batch, never one socket message per strike.
     *
     * <p>This exists because the per-session route has no other way to deliver them: enqueuePending and
     * broadcastCachedState both drop when perSessionRouting() is true, so the coalesced batch and the
     * source-switch batch are dead on an authenticated gateway. The earlier version replayed bands
     * through replayCacheMap, which does deliver — as ~200 individual "spot-band" messages that the web
     * client has no handler for, so an authenticated browser received nothing at all. Same per-socket
     * routing filter as the per-entry path; one envelope out.
     */
    /**
     * Bands after a source switch, for per-session sockets only.
     *
     * <p>broadcastCachedState DROPS when perSessionRouting() is true, so the switch batch never leaves
     * the gateway in authenticated mode. Every other cached surface still recovers, because live routed
     * records refill it at the consume sites — bands cannot, since the live consumer skips them by
     * design (one message per strike would flood the chain socket). Suppressing the live path is what
     * makes this replay owed. Per-socket filtered by the same routing check, one batch each.
     *
     * <p>Called from markSelectionReady ONLY — never also from applySelection, which would double-send
     * on the common path where a switch is serviceable at once and marks itself ready inline.
     */
    private void replaySpotBandBatchAfterSourceSwitch() {
        if (!perSessionRouting()) {
            return;
        }
        // Self-healing prune. removeClient clears a socket's claim, but onSlowDisconnect, closeSockets
        // (logout / session expiry) and closeExpiredAuthSessions drop sockets by other routes whose close
        // callback can be delayed or suppressed, so the ledger must not depend on any of them running.
        // Retaining only live sockets bounds it by the connected set on every switch.
        spotBandSwitchDelivered.keySet().retainAll(clientsById.keySet());
        for (WebSocketSession client : clients) {
            replaySpotBandBatchOncePerReadiness(client);
        }
    }

    /**
     * The switch-time band board, at most once per socket per readiness key. Both the readiness replay
     * and the connect bracket call this; whichever arrives first claims the key and the other no-ops, so
     * exactly-once holds under EITHER iterator ordering instead of depending on the interleaving.
     */
    private void replaySpotBandBatchOncePerReadiness(WebSocketSession session) {
        String key = readySelectionKey.get();
        String previous = spotBandSwitchDelivered.put(session.getId(), key);
        if (java.util.Objects.equals(previous, key)) {
            return;
        }
        replaySpotBandBatchToSocket(session);
    }

    private void replaySpotBandBatchToSocket(WebSocketSession session) {
        List<String> deliverable = deliverableCacheEntries(session, "spot-band", spotBands);
        if (deliverable.isEmpty()) {
            return;
        }
        List<CachedEvent> asBatch = new ArrayList<>(deliverable.size());
        for (String json : deliverable) {
            asBatch.add(new CachedEvent("spot-band", json));
        }
        sendEnvelope(session, uiBatchEnvelopeJson(asBatch));
    }

    private boolean requiresFreshPerSessionReplay(String event) {
        return "option-truth".equals(event)
                || "corridor-gauge".equals(event)
                || "strike-sr".equals(event)
                || "gex-magnet".equals(event)
                || "gamma-migration".equals(event)
                || "es-gex".equals(event)
                || "es-strike-intel".equals(event)
                || "gex-strike-lifecycle".equals(event)
                || "liquidity-heatmap".equals(event)
                || "mission-pace".equals(event)
                || "mission-control".equals(event)
                || "spread-skew".equals(event);
    }

    /** Legacy single-selection bootstrap for the standalone Option Truth event. */
    private void replayOptionTruthCachedLegacy(WebSocketSession session) {
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        ActiveSelection selection = activeSelection.get();
        optionTruths.entrySet().stream()
                .filter(entry -> isCacheFresh("option-truth:" + entry.getKey(), nowMs))
                .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> send(session, "option-truth", entry.getValue()));
    }

    // =====================================================================
    // Per-session historical replay (ReplayRunner, reqs 7/8). Reads historical
    // records READ-ONLY (assign + seek, auto-commit off) and streams them ONLY to
    // the requesting session's sockets. Nothing is ever published to live topics.
    // While a session replays, SessionRoutingEngine.route() skips it, so other
    // users keep receiving live data uninterrupted (req. 7).
    // =====================================================================

    // P0 (cancellation/generation barrier). Each control action (start/stop/return-to-live) is serialized
    // per service and assigns a monotonic GENERATION that "owns" the session's replay output. A reader may
    // emit data or a terminal status ONLY while its generation is still the owner; the instant a newer
    // generation is installed (or the run is canceled) the old reader goes silent — so a stale poll batch,
    // or a late terminal status, can never reach the UI after the user moved on.
    private final Object replayControlLock = new Object();
    private final Map<String, ReplayHandle> replayHandles = new ConcurrentHashMap<>();
    private final Map<String, Long> replayOwnerGeneration = new ConcurrentHashMap<>();
    private final AtomicLong replayGenerationSeq = new AtomicLong();
    private volatile ExecutorService replayExecutor;

    /** Handle to one running reader: its generation, cooperative cancel flag, Future, and live consumers. */
    static final class ReplayHandle {
        final long generation;
        final AtomicBoolean active = new AtomicBoolean(true);
        volatile Future<?> future;
        volatile KafkaConsumer<?, ?> avroConsumer;
        volatile KafkaConsumer<?, ?> stringConsumer;

        ReplayHandle(long generation) {
            this.generation = generation;
        }

        /** Break a blocking poll/offsetsForTimes immediately (WakeupException), without interrupting I/O. */
        void wakeConsumers() {
            KafkaConsumer<?, ?> a = avroConsumer;
            KafkaConsumer<?, ?> s = stringConsumer;
            if (a != null) {
                a.wakeup();
            }
            if (s != null) {
                s.wakeup();
            }
        }
    }

    private ExecutorService replayExecutor() {
        ExecutorService e = replayExecutor;
        if (e == null) {
            synchronized (replayControlLock) {
                e = replayExecutor;
                if (e == null) {
                    // Bounded: at most replayMaxConcurrent readers; further starts are REJECTED (never an
                    // unbounded fan-out of daemon threads).
                    e = new ThreadPoolExecutor(0, settings.replayMaxConcurrent(), 60L, TimeUnit.SECONDS,
                            new SynchronousQueue<>(),
                            r -> {
                                Thread t = new Thread(r, "options-edge-replay");
                                t.setDaemon(true);
                                return t;
                            },
                            new ThreadPoolExecutor.AbortPolicy());
                    replayExecutor = e;
                }
            }
        }
        return e;
    }

    /** Visible for tests: a handle pre-registered as the current owner of the session's replay output. */
    ReplayHandle registerOwnerHandleForTest(String appSessionId) {
        long gen = replayGenerationSeq.incrementAndGet();
        replayOwnerGeneration.put(appSessionId, gen);
        return new ReplayHandle(gen);
    }

    /** True while {@code generation} is still the owner entitled to emit for {@code appSessionId}. */
    private boolean isReplayOwner(String appSessionId, long generation) {
        Long owner = replayOwnerGeneration.get(appSessionId);
        return owner != null && owner == generation;
    }

    /**
     * Cancel any in-flight reader for the session and BARRIER on its termination: install a fresh owner
     * generation (so the old reader is no longer entitled to emit anything), wake its consumers, interrupt
     * its Future, and await the thread leaving before returning. After this the caller may safely install
     * the next state. Must be called holding {@link #replayControlLock}.
     */
    private void cancelActiveReplay(String appSessionId) {
        // Invalidate the current owner FIRST so even a reader mid-emit stops being entitled to send.
        replayOwnerGeneration.put(appSessionId, replayGenerationSeq.incrementAndGet());
        ReplayHandle prev = replayHandles.remove(appSessionId);
        if (prev == null) {
            return;
        }
        prev.active.set(false);
        prev.wakeConsumers();
        Future<?> f = prev.future;
        if (f != null) {
            f.cancel(true);
            try {
                f.get(settings.replayShutdownAwaitMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                // Reader did not exit in time; the generation barrier already prevents any further emits,
                // so it is harmless — log and move on rather than blocking the control call forever.
                System.err.println("Feed gateway replay reader for " + appSessionId + " did not stop within "
                        + settings.replayShutdownAwaitMs() + "ms; proceeding (output already barriered).");
            } catch (CancellationException | ExecutionException ignored) {
                // canceled / threw on the way out — fine, it is no longer the owner
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public ReplayRunner.Mode startReplay(ReplayParams params) {
        if (routingEngine == null) {
            throw new IllegalStateException("per-session routing is not enabled");
        }
        String appSessionId = params.sessionId();
        synchronized (replayControlLock) {
            // Resource safety (symmetric to cancelReplayIfNoSockets): never install a Kafka-consuming reader
            // for a session with NO attached sockets. A /replay/start that raced just behind the last
            // socket's disconnect would otherwise strand a reader polling Kafka for nobody until the window
            // or idle-expiry ends. Checked under the SAME lock the disconnect path takes, so the two
            // orderings are mutually exclusive: either the close ran first (we see empty sockets and refuse)
            // or we ran first (the close path's re-check finds our handle and cancels it). Maps to HTTP 409.
            if (routingEngine.socketsForAppSession(appSessionId).isEmpty()) {
                throw new IllegalStateException("no active socket for session " + appSessionId);
            }
            cancelActiveReplay(appSessionId); // cancel + wake + await any prior run for this session
            // Enter replay mode BEFORE installing any handle/generation. If the session vanished between the
            // socket-presence check above and here (a max-expiry sweep removes engine sessions WITHOUT
            // holding replayControlLock), this throws with nothing yet to roll back — rethrown as a clean
            // 409. Doing it first also guarantees we never install a handle or submit a reader for an absent
            // session (which would otherwise be an orphaned Kafka reader).
            try {
                routingEngine.setReplayMode(appSessionId, true);
            } catch (IllegalStateException sessionGone) {
                throw new IllegalStateException("no active session to replay; it was just torn down");
            }
            long generation = replayGenerationSeq.incrementAndGet();
            ReplayHandle handle = new ReplayHandle(generation);
            replayOwnerGeneration.put(appSessionId, generation);
            replayHandles.put(appSessionId, handle);
            // Clear the live rows and flip the UI badge (reqs 9).
            sendToAppSession(appSessionId, "reset", "{\"reason\":\"replay-start\"}");
            sendToAppSession(appSessionId, "replay-status", replayStatusJson("REPLAY_RUNNING", params, 0L));
            try {
                handle.future = replayExecutor().submit(() -> runReplay(appSessionId, params, handle));
            } catch (RejectedExecutionException tooMany) {
                replayHandles.remove(appSessionId, handle);
                replayOwnerGeneration.put(appSessionId, replayGenerationSeq.incrementAndGet());
                routingEngine.setReplayModeIfPresent(appSessionId, false); // rollback; no-op if torn down
                throw new IllegalStateException("replay capacity reached; please retry shortly");
            }
            return ReplayRunner.Mode.REPLAY_RUNNING;
        }
    }

    @Override
    public ReplayRunner.Mode stopReplay(String appSessionId) {
        synchronized (replayControlLock) {
            boolean wasRunning = replayHandles.containsKey(appSessionId);
            cancelActiveReplay(appSessionId);
            if (wasRunning) {
                // Authoritative terminal status from the control thread (the reader is now barriered silent).
                sendToAppSession(appSessionId, "replay-status",
                        replayTerminalJson(ReplayOutcome.CANCELED, appSessionId));
            }
            return ReplayRunner.Mode.REPLAY_COMPLETE; // stays in replay mode until the user returns to live
        }
    }

    @Override
    public ReplayRunner.Mode resumeLive(String appSessionId) {
        synchronized (replayControlLock) {
            cancelActiveReplay(appSessionId);
            if (routingEngine != null) {
                // IfPresent: a max-expiry sweep / logout may have removed the session concurrently (it does
                // not hold replayControlLock); returning to live for an absent session is a no-op, not an error.
                routingEngine.setReplayModeIfPresent(appSessionId, false);
            }
            // Clear replay rows and re-seed the latest live cache, then live routing resumes (req. 10).
            sendToAppSession(appSessionId, "reset", "{\"reason\":\"return-to-live\"}");
            replayLiveCacheToAppSession(appSessionId);
            sendToAppSession(appSessionId, "replay-status", "{\"mode\":\"LIVE\"}");
            return ReplayRunner.Mode.LIVE;
        }
    }

    private void runReplay(String appSessionId, ReplayParams params, ReplayHandle handle) {
        MarketDataSource source = routingEngine.appSession(appSessionId)
                .map(app -> app.selection().source()).orElse(null);
        long delivered = 0L;
        ReplayOutcome outcome = ReplayOutcome.COMPLETED;
        String error = null;
        if (source != null) {
            Map<String, String> avroTopics = new LinkedHashMap<>();
            Map<String, String> stringTopics = new LinkedHashMap<>();
            if (source == MarketDataSource.DATABENTO) {
                avroTopics.put(settings.databentoDisplayTopic(), "snapshot");
                avroTopics.put(settings.databentoPaceTopic(), "pace");
                avroTopics.put(settings.databentoPaceRankTopic(), "pace-rank");
                avroTopics.put(settings.databentoDirectionalPressureTopic(), "directional-pressure");
                // DATABENTO gex + max-pain are Avro on the wire — replay them via the Avro reader too, so a
                // historical replay reproduces them exactly like the live Avro path (keep classification
                // consistent across cache / live / replay).
                avroTopics.put(settings.databentoGexTopic(), "gex-by-strike");
                avroTopics.put(settings.databentoMaxPainTopic(), "max-pain");
                avroTopics.put(settings.unifiedSrTopic(), "strike-sr");
                avroTopics.put(settings.databentoGexMagnetTopic(), "gex-magnet");
                avroTopics.put(settings.gammaMigrationTopic(), "gamma-migration");
                avroTopics.put(settings.gammaRotationTopic(), "gamma-rotation");
                avroTopics.put(settings.gammaFragilityTopic(), "gamma-fragility");
                avroTopics.put(settings.databentoGexStrikeLifecycleTopic(), "gex-strike-lifecycle");
                stringTopics.put(settings.databentoStrikeFlowTopic(), "strike-flow");
                stringTopics.put(settings.databentoDeltaFlowByStrikeTopic(), "delta-flow");
                stringTopics.put(settings.strikeIntelByStrikeTopic(), "strike-intel");
                stringTopics.put(settings.strikeInvasionTopic(), "strike-invasion");
                stringTopics.put(settings.corridorGaugeTopic(), "corridor-gauge");
                stringTopics.put(settings.strikeLiquidityTopic(), "liquidity-heatmap");
                stringTopics.put(settings.databentoPaceMissionTopic(), "mission-pace");
                stringTopics.put(settings.missionControlTopic(), "mission-control");
                stringTopics.put(settings.spreadSkewTopic(), "spread-skew");
                stringTopics.put(settings.databentoEsTradesTopic(), "index-price");
                stringTopics.put(settings.underlyingSpxPriceTopic(), "spx-price");
                if (settings.esGexEnabled()) {
                    stringTopics.put(settings.esGexSpxAlignedTopic(), "es-gex");
                }
                if (settings.esStrikeIntelEnabled()) {
                    stringTopics.put(settings.esStrikeIntelSpxAlignedTopic(), "es-strike-intel");
                }
            } else {
                avroTopics.put(settings.ibkrDisplayTopic(), "snapshot");
                avroTopics.put(settings.ibkrPaceTopic(), "pace");
                avroTopics.put(settings.ibkrPaceRankTopic(), "pace-rank");
                avroTopics.put(settings.ibkrDirectionalPressureTopic(), "directional-pressure");
                stringTopics.put(settings.ibkrUnusualWhalesGexTopic(), "gex-by-strike");
                stringTopics.put(settings.ibkrVixPriceTopic(), "vix-price");
            }
            if (params.hasRun()) {
                // Run-scoped replay reads options.replay.<runId>.* topics. The strike-liquidity
                // dashboard is NOT part of the per-run replay contract (design defers heatmap
                // replay; the replicator produces no replay.<runId> liquidity topic), and its
                // dotless name has no namespace segment for ReplayTopicResolver — drop it BEFORE
                // resolution so an orchestrated Databento run can never die on topic resolution.
                stringTopics.remove(settings.strikeLiquidityTopic());
                // delta-flow-by-strike is likewise NOT in the per-run replay contract and its dotless
                // name has no namespace segment for ReplayTopicResolver — drop it before resolution too.
                stringTopics.remove(settings.databentoDeltaFlowByStrikeTopic());
                // strike-intelligence-by-strike is likewise NOT in the per-run replay contract and its
                // dotless name has no namespace segment for ReplayTopicResolver — drop it too.
                stringTopics.remove(settings.strikeIntelByStrikeTopic());
                // strike-invasion is likewise NOT part of the per-run replay contract (the replicator
                // produces no replay.<runId> strike-invasion topic) — drop it too, so an orchestrated
                // Databento run never dies waiting on a topic the run does not provide. Windowed
                // (non-run) historical replay still consumes the live topic above.
                stringTopics.remove(settings.strikeInvasionTopic());
                // Read the orchestrated run's LOCAL replay topics instead of the live topics.
                avroTopics = toReplayTopics(avroTopics, params.runId());
                stringTopics = toReplayTopics(stringTopics, params.runId());
            }
            try {
                // P0 (replay chronology): a SINGLE deterministic k-way merge across ALL topics (avro +
                // string) by canonical event time — never topic-by-topic, which would surface "all
                // snapshots then all strike-flow" and let the record cap drain entirely in one phase.
                ReplayResult result = replayMerged(appSessionId, params, source, avroTopics, stringTopics, handle);
                delivered = result.delivered();
                outcome = result.outcome();
            } catch (WakeupException canceled) {
                outcome = ReplayOutcome.CANCELED; // a control call woke the consumer to stop this run
            } catch (RuntimeException e) {
                outcome = ReplayOutcome.FAILED;
                error = String.valueOf(e.getMessage());
            }
        }
        // Free our handle only if it is still ours (a newer generation may have replaced it already).
        replayHandles.remove(appSessionId, handle);
        // P0 (cancellation/generation barrier): emit a terminal status ONLY if this reader is still the
        // owner. A canceled/superseded reader stays silent — the control call that replaced it sends the
        // authoritative next status — so a stale terminal can never overwrite the live/new-replay state.
        // Exactly ONE terminal status is sent, with a distinct state (completed/timed-out/failed).
        if (outcome == ReplayOutcome.CANCELED || !isReplayOwner(appSessionId, handle.generation)) {
            return;
        }
        synchronized (replayControlLock) {
            if (!isReplayOwner(appSessionId, handle.generation)) {
                return; // a control call slipped in between the check and the lock — defer to it
            }
            // This reader finished on its own and is still the owner; retire the generation and report.
            replayOwnerGeneration.put(appSessionId, replayGenerationSeq.incrementAndGet());
            sendToAppSession(appSessionId, "replay-status",
                    replayTerminalJson(outcome, params, delivered, error));
        }
    }

    /** Map a terminal outcome to its distinct UI status (completed/canceled/timed-out/failed). */
    private static String terminalMode(ReplayOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> "REPLAY_COMPLETE";
            case CANCELED -> "REPLAY_CANCELED";
            case TIMED_OUT -> "REPLAY_TIMED_OUT";
            case FAILED -> "REPLAY_FAILED";
        };
    }

    /** Terminal status used by the control thread for an explicit cancel (no window/record context). */
    private String replayTerminalJson(ReplayOutcome outcome, String appSessionId) {
        return "{\"mode\":\"" + terminalMode(outcome) + "\",\"complete\":"
                + (outcome == ReplayOutcome.COMPLETED) + "}";
    }

    /** Terminal status used by the reader: carries the window, delivered count, and any error. */
    private String replayTerminalJson(ReplayOutcome outcome, ReplayParams params, long delivered, String error) {
        StringBuilder b = new StringBuilder()
                .append("{\"mode\":\"").append(terminalMode(outcome))
                .append("\",\"complete\":").append(outcome == ReplayOutcome.COMPLETED)
                .append(",\"symbol\":\"").append(escapeJson(params.symbol()))
                .append("\",\"expiry\":\"").append(escapeJson(params.expiry()))
                .append("\",\"startUtcMs\":").append(params.startUtcMs())
                .append(",\"endUtcMs\":").append(params.endUtcMs())
                .append(",\"records\":").append(delivered)
                .append(",\"maxRecords\":").append(params.maxRecords());
        if (outcome == ReplayOutcome.TIMED_OUT && error == null) {
            error = "replay timed out before all captured data was read";
        }
        if (error != null) {
            b.append(",\"error\":\"").append(escapeJson(error)).append("\"");
        }
        return b.append("}").toString();
    }

    /**
     * Deterministic chronological replay (P0 — replay chronology). Reads the session's avro AND string
     * replay topics together and merges every partition by canonical event time
     * ({@code eventTimestamp → topic → partition → offset}; see {@link ReplayChronology}), so the outbound
     * stream reproduces the real market sequence and the record cap bounds the merged timeline instead of
     * being exhausted by whichever topic is read first. Each outbound event keeps its source timestamp,
     * topic, partition and offset.
     */
    /**
     * Distinct terminal states of a replay read (P0): {@code COMPLETED} — every active partition reached
     * its captured target offset (or the record cap was hit); {@code CANCELED} — superseded by a newer
     * generation or an explicit stop/return-to-live; {@code TIMED_OUT} — a poll deadline expired short of
     * target; {@code FAILED} — a read error. Only COMPLETED is "success".
     */
    enum ReplayOutcome { COMPLETED, CANCELED, TIMED_OUT, FAILED }

    record ReplayResult(long delivered, ReplayOutcome outcome) {
    }

    private ReplayResult replayMerged(String appSessionId, ReplayParams params, MarketDataSource source,
                                      Map<String, String> avroTopics, Map<String, String> stringTopics,
                                      ReplayHandle handle) {
        if (avroTopics.isEmpty() && stringTopics.isEmpty()) {
            return new ReplayResult(0L, ReplayOutcome.COMPLETED);
        }
        KafkaConsumer<String, Object> avro = null;
        KafkaConsumer<String, Object> str = null;
        try {
            Map<TopicPartition, ReplayPartitionState> parts = new LinkedHashMap<>();
            if (!avroTopics.isEmpty()) {
                avro = new KafkaConsumer<>(replayConsumerProps(appSessionId, true));
                handle.avroConsumer = avro; // publish for wakeup() before any blocking call
                openReplayPartitions(appSessionId, avro, params, avroTopics, true, parts);
            }
            if (!stringTopics.isEmpty()) {
                str = new KafkaConsumer<>(replayConsumerProps(appSessionId, false));
                handle.stringConsumer = str;
                openReplayPartitions(appSessionId, str, params, stringTopics, false, parts);
            }
            if (parts.isEmpty()) {
                return new ReplayResult(0L, ReplayOutcome.COMPLETED);
            }
            return mergeReplayPartitions(appSessionId, params, source, avro, str, parts, handle);
        } catch (WakeupException woken) {
            // A control call (stop/return-to-live/new replay) woke the consumer — cancel cleanly.
            return new ReplayResult(0L, ReplayOutcome.CANCELED);
        } finally {
            // Detach BEFORE close so a concurrent wakeup() can never touch a closed consumer.
            handle.avroConsumer = null;
            handle.stringConsumer = null;
            if (avro != null) {
                avro.close();
            }
            if (str != null) {
                str.close();
            }
        }
    }

    /** Per-partition cursor for the replay merge: a buffer of in-order records plus its window end. */
    static final class ReplayPartitionState {
        final boolean avro;
        final String event;
        final long endTarget;                          // exclusive: read offsets < endTarget
        final ArrayDeque<MergeRecord> buffer = new ArrayDeque<>();
        boolean drained;                               // consumer position reached endTarget — nothing more to poll

        ReplayPartitionState(boolean avro, String event, long endTarget) {
            this.avro = avro;
            this.event = event;
            this.endTarget = endTarget;
        }
    }

    /** One buffered record awaiting chronological emission. */
    record MergeRecord(ReplayChronology.Cursor cursor, String event, Object value, boolean avro) {
    }

    private Properties replayConsumerProps(String appSessionId, boolean avro) {
        Properties props = avro
                ? avroConsumerProperties("replay-" + appSessionId)
                : stringObjectConsumerProperties("replay-" + appSessionId);
        // Unique group per run so the windowed read is independent and commits nothing (read-only).
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                settings.groupIdBase() + "-replay-" + appSessionId + "-" + (avro ? "avro" : "str"));
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return props;
    }

    /** Assign + seek this consumer's partitions for the window (or full runId topics) and register state. */
    private void openReplayPartitions(String appSessionId, KafkaConsumer<String, Object> consumer,
                                      ReplayParams params,
                                      Map<String, String> topicEvents, boolean avro,
                                      Map<TopicPartition, ReplayPartitionState> parts) {
        // Session-scoped name: concurrent replays would otherwise share one optional-topic log state and
        // suppress each other's absent/present transitions.
        List<TopicPartition> partitions =
                partitionsFor("replay-" + appSessionId + (avro ? "-avro" : "-str"),
                        consumer, topicEvents.keySet(), settings.metadataTimeoutMs(), Set.of(), true);
        consumer.assign(partitions);
        Map<TopicPartition, Long> logEnd = consumer.endOffsets(partitions);
        if (params.hasRun()) {
            // Orchestrated run: the *.replay.<runId>.* topics already contain exactly the windowed data,
            // so read each partition in full (beginning..log-end) — no timestamp slicing.
            Map<TopicPartition, Long> begin = consumer.beginningOffsets(partitions);
            for (TopicPartition tp : partitions) {
                long start = begin.getOrDefault(tp, 0L);
                long target = logEnd.getOrDefault(tp, start);
                if (target <= start) {
                    continue; // empty partition
                }
                consumer.seek(tp, start);
                parts.put(tp, new ReplayPartitionState(avro, topicEvents.get(tp.topic()), target));
            }
        } else {
            Map<TopicPartition, Long> startQuery = new HashMap<>();
            Map<TopicPartition, Long> endQuery = new HashMap<>();
            for (TopicPartition tp : partitions) {
                startQuery.put(tp, params.startUtcMs());
                endQuery.put(tp, params.endUtcMs());
            }
            Map<TopicPartition, OffsetAndTimestamp> startOffsets = consumer.offsetsForTimes(startQuery);
            Map<TopicPartition, OffsetAndTimestamp> endOffsets = consumer.offsetsForTimes(endQuery);
            for (TopicPartition tp : partitions) {
                OffsetAndTimestamp start = startOffsets.get(tp);
                if (start == null) {
                    continue; // no records at/after the window start
                }
                OffsetAndTimestamp end = endOffsets.get(tp);
                long target = end != null ? end.offset() : logEnd.getOrDefault(tp, start.offset());
                if (target <= start.offset()) {
                    continue; // empty window on this partition
                }
                consumer.seek(tp, start.offset());
                parts.put(tp, new ReplayPartitionState(avro, topicEvents.get(tp.topic()), target));
            }
        }
    }

    /**
     * The chronological merge core. Holds a small per-partition buffer for both consumers and always emits
     * the globally-earliest available record ({@link ReplayChronology#ORDER}). A not-yet-drained partition
     * with an empty buffer BLOCKS emission until it is polled, so no later-but-first-read record can jump
     * ahead of an earlier one on another topic/partition. The cap bounds DELIVERED (matched) records.
     *
     * <p>P0 (empty poll ≠ end of replay): completion is decided ONLY by every active partition's consumer
     * position reaching its captured target offset. An empty poll proves nothing (fetch latency, broker
     * load, jitter), so empty polls are retried until a bounded no-progress deadline; if that deadline
     * expires while any partition is still short of target the run is reported INCOMPLETE (a failure), never
     * a silent REPLAY_COMPLETE. Progress (a buffered record or a partition reaching target) resets the
     * deadline, so an ongoing-but-slow read is never timed out.
     */
    ReplayResult mergeReplayPartitions(String appSessionId, ReplayParams params, MarketDataSource source,
                                       KafkaConsumer<String, Object> avro, KafkaConsumer<String, Object> str,
                                       Map<TopicPartition, ReplayPartitionState> parts, ReplayHandle handle) {
        long delivered = 0L;
        int maxRecords = params.maxRecords();
        long idleTimeoutMs = settings.replayIdleTimeoutMs();
        long lastProgressMs = System.currentTimeMillis();
        boolean drainedToEnd = false;
        // P0 (generation barrier): stop the moment this reader is no longer the session's owner — a stop,
        // return-to-live, or newer replay has taken over, so nothing more may be emitted.
        while (handle.active.get() && running.get() && isReplayOwner(appSessionId, handle.generation)
                && delivered < maxRecords) {
            if (hasUnbufferedActivePartition(parts)) {
                // Only blocking partitions (empty buffer, not yet at target) need fetching — pause the rest
                // so buffers stay bounded and an empty poll reflects ONLY the partitions we are waiting on.
                int satisfiedBefore = countSatisfied(parts);
                tunePauses(avro, true, parts);
                tunePauses(str, false, parts);
                pollIntoBuffers(avro, parts);
                pollIntoBuffers(str, parts);
                refreshDrained(avro, true, parts);
                refreshDrained(str, false, parts);
                if (hasUnbufferedActivePartition(parts)) {
                    if (countSatisfied(parts) > satisfiedBefore) {
                        lastProgressMs = System.currentTimeMillis(); // a blocking partition advanced
                        continue;
                    }
                    if (System.currentTimeMillis() - lastProgressMs > idleTimeoutMs) {
                        // A partition has not reached its captured target offset within the deadline.
                        return new ReplayResult(delivered, ReplayOutcome.TIMED_OUT);
                    }
                    continue; // ordinary empty poll — keep retrying until target or deadline
                }
                lastProgressMs = System.currentTimeMillis();
            }
            MergeRecord next = pollGlobalMinimum(parts);
            if (next == null) {
                drainedToEnd = true; // every active partition reached target AND its buffer is drained
                break;
            }
            if (emitReplayRecord(appSessionId, params, source, next, handle)) {
                delivered++;
            }
            lastProgressMs = System.currentTimeMillis();
        }
        // COMPLETED only when the read genuinely finished (all partitions at target) or hit the record cap.
        // Any other loop exit — ownership lost, cancel flag cleared, gateway shutdown — is a cancellation.
        if (drainedToEnd || delivered >= maxRecords) {
            return new ReplayResult(delivered, ReplayOutcome.COMPLETED);
        }
        return new ReplayResult(delivered, ReplayOutcome.CANCELED);
    }

    private static boolean hasUnbufferedActivePartition(Map<TopicPartition, ReplayPartitionState> parts) {
        for (ReplayPartitionState st : parts.values()) {
            if (!st.drained && st.buffer.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A partition is "satisfied" for the merge when it has reached target (drained) or has a buffered head. */
    private static int countSatisfied(Map<TopicPartition, ReplayPartitionState> parts) {
        int n = 0;
        for (ReplayPartitionState st : parts.values()) {
            if (st.drained || !st.buffer.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Fetch only from partitions we are still waiting on; pause those already satisfied (bounds buffers). */
    private void tunePauses(KafkaConsumer<String, Object> consumer, boolean avro,
                            Map<TopicPartition, ReplayPartitionState> parts) {
        if (consumer == null) {
            return;
        }
        List<TopicPartition> pause = new ArrayList<>();
        List<TopicPartition> resume = new ArrayList<>();
        for (Map.Entry<TopicPartition, ReplayPartitionState> e : parts.entrySet()) {
            ReplayPartitionState st = e.getValue();
            if (st.avro != avro) {
                continue;
            }
            if (!st.drained && st.buffer.isEmpty()) {
                resume.add(e.getKey());
            } else {
                pause.add(e.getKey());
            }
        }
        if (!pause.isEmpty()) {
            consumer.pause(pause);
        }
        if (!resume.isEmpty()) {
            consumer.resume(resume);
        }
    }

    /** Poll one consumer and append every in-window record to its partition buffer. */
    private boolean pollIntoBuffers(KafkaConsumer<String, Object> consumer,
                                    Map<TopicPartition, ReplayPartitionState> parts) {
        if (consumer == null) {
            return false;
        }
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
        boolean appended = false;
        for (ConsumerRecord<String, Object> rec : records) {
            TopicPartition tp = new TopicPartition(rec.topic(), rec.partition());
            ReplayPartitionState st = parts.get(tp);
            if (st == null || rec.offset() >= st.endTarget) {
                continue; // beyond the window end — ignore (position still advances past it)
            }
            ReplayChronology.Cursor cursor =
                    new ReplayChronology.Cursor(rec.timestamp(), rec.topic(), rec.partition(), rec.offset());
            st.buffer.addLast(new MergeRecord(cursor, st.event, rec.value(), st.avro));
            appended = true;
        }
        return appended;
    }

    /** Mark this consumer's partitions whose position reached the window end (nothing more will be polled). */
    private void refreshDrained(KafkaConsumer<String, Object> consumer, boolean avro,
                                Map<TopicPartition, ReplayPartitionState> parts) {
        if (consumer == null) {
            return;
        }
        for (Map.Entry<TopicPartition, ReplayPartitionState> e : parts.entrySet()) {
            ReplayPartitionState st = e.getValue();
            if (st.drained || st.avro != avro) {
                continue;
            }
            if (consumer.position(e.getKey()) >= st.endTarget) {
                st.drained = true;
            }
        }
    }

    /** Pop the globally-earliest buffered record across all partitions ({@link ReplayChronology#ORDER}). */
    private MergeRecord pollGlobalMinimum(Map<TopicPartition, ReplayPartitionState> parts) {
        ReplayPartitionState minState = null;
        MergeRecord min = null;
        for (ReplayPartitionState st : parts.values()) {
            MergeRecord head = st.buffer.peek();
            if (head == null) {
                continue;
            }
            if (min == null || ReplayChronology.ORDER.compare(head.cursor(), min.cursor()) < 0) {
                min = head;
                minState = st;
            }
        }
        if (minState != null) {
            minState.buffer.pollFirst();
        }
        return min;
    }

    /** Convert, stamp provenance, filter to the session, and stream one merged record. */
    private boolean emitReplayRecord(String appSessionId, ReplayParams params, MarketDataSource source,
                                     MergeRecord r, ReplayHandle handle) {
        // A single malformed/poison record must NOT abort the windowed replay (review finding #12).
        try {
            String raw = r.avro() ? avroJson(r.value()) : stringJson(r.value());
            // R-WIRE.1 on the REPLAY reader too (round-3 finding 3): the shared live topic
            // carries sessioned (IBKR/PREOPEN) records with the feature enabled, and replay must
            // apply the SAME classification the cache/live readers do. Disposition per class:
            // exact IBKR tuples belong to the pre-open plane only — a wall-clock-governed
            // 09:25–09:35 construct that a windowed historical replay cannot reproduce, so they
            // are excluded from the ordinary replay plane; conflicting PREOPEN and
            // UNKNOWN_SESSIONED tuples fail closed exactly like the live reader. All three drop
            // here (counted), so no sessioned record is ever relabelled with Databento
            // provenance by the generic enrichment below. Flag OFF -> byte-identical behavior (O7).
            if (settings.ibkrPreOpenEnabled() && source == MarketDataSource.DATABENTO
                    && "gex-by-strike".equals(r.event()) && isSessionedSharedGexJson(raw)) {
                ibkrPreOpenGexDroppedSessioned.incrementAndGet();
                return false;
            }
            String json = enrichJson(raw, new TopicBinding(source.name(), r.event()));
            if (json == null || json.isBlank()) {
                return false;
            }
            // strike-invasion carries no expiry; enrichJson stamped the LIVE 0DTE date, but a REPLAYED
            // record belongs to the replay window's chain (params.expiry()), not today's. Re-stamp it so
            // the expiry-matched replayMatches filter accepts it — otherwise the historical, live-dated
            // record would never match the replay selection and would be silently dropped.
            if ("strike-invasion".equals(r.event())) {
                json = stampExpiry(json, params.expiry());
            }
            json = withReplayProvenance(json, r.cursor());
            if (replayMatches(params, r.event(), json)) {
                // P0 (generation barrier): re-verify ownership IMMEDIATELY before the send. A record already
                // returned by an earlier poll must never reach the socket after the user started a different
                // replay or returned to live — the barrier closes that exact window.
                if (!handle.active.get() || !isReplayOwner(appSessionId, handle.generation)) {
                    return false;
                }
                sendToAppSession(appSessionId, r.event(), json);
                return true;
            }
            return false;
        } catch (RuntimeException poison) {
            return false; // skip the poison record; the merge continues with the next
        }
    }

    /**
     * Overwrite the {@code expiry} of a JSON payload (used to re-stamp the expiry-less strike-invasion
     * record with the replay window's chain). No-op on blank expiry or non-object/unparseable JSON.
     */
    private String stampExpiry(String json, String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return json;
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (root instanceof ObjectNode object) {
                object.put("expiry", expiry);
                return mapper.writeValueAsString(object);
            }
        } catch (JsonProcessingException ignored) {
            // fall through with the original json
        }
        return json;
    }

    /** Retain the record's canonical event time, topic, partition and offset on the outbound event. */
    private String withReplayProvenance(String json, ReplayChronology.Cursor cursor) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root instanceof ObjectNode object) {
                object.put("replaySourceTimestamp", cursor.eventTimestamp());
                object.put("replaySourceTopic", cursor.topic());
                object.put("replaySourcePartition", cursor.partition());
                object.put("replaySourceOffset", cursor.offset());
                return mapper.writeValueAsString(object);
            }
        } catch (JsonProcessingException ignored) {
            // fall through with the unannotated json
        }
        return json;
    }

    /** Map each live topic in {@code topicEvents} to its per-runId replay-namespace equivalent. */
    private static Map<String, String> toReplayTopics(Map<String, String> topicEvents, String runId) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : topicEvents.entrySet()) {
            resolved.put(ReplayTopicResolver.toReplayTopic(e.getKey(), runId), e.getValue());
        }
        return resolved;
    }

    /** A replay record matches the session: same contract (symbol+expiry) and inside the strike window. */
    private boolean replayMatches(ReplayParams params, String event, String json) {
        if ("spx-price".equals(event)) {
            // Underlying-scoped like index/vix (no strike/expiry match), but the SSOT boundary holds on
            // replay too: a malformed/foreign/unproven archived record must never reach a session
            // (Codex round-1 P0 — cache, live, forward, and replay behave identically).
            return isValidSpxPriceJson(json);
        }
        if ("index-price".equals(event) || "vix-price".equals(event)) {
            return true; // underlying/vix carry no strike and a different symbol — always relevant
        }
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = root.hasNonNull("symbol") ? root.get("symbol").asText("") : "";
            String expiry = root.hasNonNull("expiry") ? GatewaySettings.normalizeExpiry(root.get("expiry").asText("")) : "";
            if (!params.symbol().equalsIgnoreCase(symbol) || !params.expiry().equals(expiry)) {
                return false;
            }
            if (routingEngine != null && root.hasNonNull("strike") && root.get("strike").isNumber()) {
                AppSession app = routingEngine.appSession(params.sessionId()).orElse(null);
                if (app != null && !app.selection().strikeWindow().contains(root.get("strike").asDouble())) {
                    return false; // per-user strike-window filter (same as live)
                }
            }
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private void sendToAppSession(String appSessionId, String event, String json) {
        if (routingEngine == null) {
            return;
        }
        for (String socketId : routingEngine.socketsForAppSession(appSessionId)) {
            WebSocketSession s = clientsById.get(socketId);
            if (s != null) {
                send(s, event, json);
            }
        }
    }

    private void replayLiveCacheToAppSession(String appSessionId) {
        if (routingEngine == null) {
            return;
        }
        for (String socketId : routingEngine.socketsForAppSession(appSessionId)) {
            WebSocketSession s = clientsById.get(socketId);
            if (s != null) {
                replayCachedToSocket(s);
            }
        }
    }

    private String replayStatusJson(String mode, ReplayParams params, long delivered) {
        return "{\"mode\":\"" + mode + "\",\"symbol\":\"" + escapeJson(params.symbol())
                + "\",\"expiry\":\"" + escapeJson(params.expiry())
                + "\",\"startUtcMs\":" + params.startUtcMs() + ",\"endUtcMs\":" + params.endUtcMs()
                + ",\"records\":" + delivered + ",\"maxRecords\":" + params.maxRecords() + "}";
    }

    /**
     * Events that may legitimately fan out to every connected socket while per-session routing is
     * active. These are connection/selection lifecycle signals — identical for all users and
     * carrying no per-user market data. Everything else (snapshot/pace/strike-flow/etc., and any
     * malformed or unroutable payload) is DROPPED rather than broadcast in per-session mode, so a
     * per-user market-data event can never reach another user's socket.
     */
    static final Set<String> GLOBAL_BROADCAST_EVENTS = Set.of(
            "status", "reset", "source-switching", "source-ready", "source-stale",
            // Pre-open IBKR GEX window state (rev13 R-STATE): a GLOBAL control/status stream —
            // identical for all users, sessionId-gated client-side. Allowlisted so the
            // standalone broadcast reaches sockets in per-session (auth) mode too
            // (GatewayRecordMapper deliberately has no route for it).
            "ibkr-preopen-status",
            // Pre-open IBKR GEX value plane (rev13 R-ARB slice 2): the same GLOBAL wrapped-stream
            // class as its status sibling — sessionId-gated client-side, never selection-routed
            // (GatewayRecordMapper deliberately has no route for it).
            "ibkr-preopen-gex",
            // Drop-classifier SHADOW nowcast: a GLOBAL advisory (identical for every user,
            // display-only). Allowlisted so the standalone broadcast reaches per-session
            // (auth) sockets too — without this, authenticated prod silently drops it.
            "drop-nowcast",
            // Vol-premium IV-vs-realised reading is a GLOBAL advisory (identical for every user,
            // display-only, symbol-filtered client-side). GatewayRecordMapper deliberately has no
            // route for it, so without this entry broadcast() drops it whenever per-session routing
            // is on — i.e. the card is permanently blank in authenticated prod while every test
            // that exercises the unauthenticated path still passes.
            "vol-premium-ivrv",
            // Agent A short-premium recommendation is a GLOBAL advisory overlay (the UI filters by
            // symbol client-side). Allowlisting it here lets routeOrBroadcast/broadcast fan it out
            // in per-session (auth) mode too, not only legacy mode — otherwise it is silently
            // dropped once GATEWAY_AUTH_ENABLED=true (GatewayRecordMapper has no route for it).
            "short-premium-recommendation",
            // Discrete spread-skew transitions (FIRE/EXIT/REVERSAL/RESTART) are likewise a GLOBAL
            // one-shot alert overlay, symbol-filtered client-side (the turn-alert sibling). Allowlist
            // them so the standalone broadcast still reaches sockets in per-session (auth) mode —
            // GatewayRecordMapper deliberately has no route for spread-skew-event.
            "spread-skew-event",
            // Hot Strike of the Day is a GLOBAL advisory overlay (symbol-filtered
            // client-side, §4.4). Allowlist it so the standalone broadcast reaches
            // sockets in per-session (auth) mode too — GatewayRecordMapper
            // deliberately has no route for hot-strike.
            "hot-strike",
            // ES 09:15 open-direction forecast + outcomes are the same class of GLOBAL advisory
            // overlay (one per day, symbol-independent) — allowlist them so routeOrBroadcast/broadcast
            // fan them out in per-session (auth) mode too, not only legacy mode. The 60s live STATUS
            // heartbeat shares the delivery class (staleness is enforced upstream: updateCache's SHORT
            // 5-min window returns null for a stale record, which suppresses the broadcast entirely).
            "es-open-direction-forecast", "es-open-direction-outcome", "es-open-direction-status",
            // Greek-move-authenticity CURRENT verdict is the same class of GLOBAL advisory overlay
            // (per-symbol, symbol-filtered client-side) — allowlist it so routeOrBroadcast/broadcast fan it
            // out in per-session (auth) mode too, not only legacy mode. Staleness is enforced upstream:
            // updateCache's SHORT 5-min greekMoveAuthTtlMs window returns null for a stale verdict, which
            // suppresses the broadcast entirely.
            "greek-move-auth",
            // Spot-vol-regime CURRENT snapshot: same GLOBAL advisory class (per-symbol,
            // symbol-filtered client-side); staleness enforced upstream by the SHORT
            // spotVolRegimeTtlMs window in updateCache.
            "spot-vol-regime",
            "indicators",
            // The tape-zones board is ES-global session truth rendered in its own card — the same
            // GLOBAL advisory class. Allowlist it so the standalone broadcast reaches sockets in
            // per-session (auth) mode too; GatewayRecordMapper deliberately has no route for it.
            // Staleness is enforced upstream (SHORT tapeZonesTtlMs window) and downstream (the
            // card's own 10 s overlay off the emitted ageMs).
            "tapeZones",
            // SPX close-direction interims + frozen verdict are the same class of GLOBAL advisory
            // overlay (one session at a time, rendered in its own summary card) — allowlist so
            // routeOrBroadcast/broadcast fan them out in per-session (auth) mode too. Malformed and
            // post-verdict-interim records are suppressed upstream (updateCache returns null).
            "close-direction",
            // Continuous ES futures buyer/seller pressure is an ES-global advisory card. The payload is
            // symbol-filtered by the browser and deliberately has no option-chain expiry identity.
            "es-aggressor-flow",
            // DEFECT FIX (found during U16): the ES CVD stream is the same ES-global advisory class,
            // but was never allowlisted — in per-session (auth) mode broadcast() dropped every
            // es-cvd/es-cvd-bar frame as non-routable.
            "es-cvd",
            "es-cvd-bar",
            // Server-rated Δ-flow acceleration: chain-global advisory; a non-allowlisted event is
            // dropped as non-routable in per-session (auth) mode.
            "delta-flow-accel",
            // U16: SPX-translated CVD structure levels — ES-global advisory overlay on the tape page,
            // fail-closed client-side (ES-CVD-SPX-LEVELS-DESIGN.md CL-R7).
            "es-cvd-spx-levels");

    static boolean isGlobalBroadcastEvent(String event) {
        return GLOBAL_BROADCAST_EVENTS.contains(event);
    }

    /**
     * Per-session routing cutover (OE-DDD-001 §8, finding #3): when enabled, deliver an event only
     * to the sockets whose AppSession selection matches it (via {@link SessionRoutingEngine}).
     *
     * <p>In per-session mode there is NO broadcast fallback for market data: if the payload is
     * malformed, or maps to no routable key (a known market-data event we couldn't key), it is
     * dropped — never broadcast — unless it is an explicitly {@linkplain #GLOBAL_BROADCAST_EVENTS
     * allowlisted global event}. In legacy mode the behaviour is unchanged (broadcast).
     */
    private void routeOrBroadcast(String bindingSource, String event, String json) {
        if (perSessionRouting()) {
            SessionRoutingEngine engine = routingEngine;
            try {
                JsonNode root = mapper.readTree(json);
                // Source is authoritative from the TOPIC BINDING (OE-DDD-001 §8.6): Avro contract
                // events (display/pace) carry no marketDataSource field, so the payload alone cannot
                // identify the source. Fall back to the payload only if the binding source is absent.
                String source = (bindingSource != null && !bindingSource.isBlank())
                        ? bindingSource
                        : (root.hasNonNull("marketDataSource") ? root.get("marketDataSource").asText("") : "");
                Optional<RoutableRecord> rec = GatewayRecordMapper.toRoutableRecord(source, event, root);
                if (rec.isPresent()) {
                    for (String socketId : engine.route(rec.get())) {
                        WebSocketSession s = clientsById.get(socketId);
                        if (s != null) {
                            send(s, event, json);
                        }
                    }
                    return;
                }
                // Unroutable while per-session: drop unless it is an allowlisted global event.
                if (isGlobalBroadcastEvent(event)) {
                    broadcast(event, json);
                } else {
                    droppedNonRoutableEvents.incrementAndGet();
                }
            } catch (JsonProcessingException malformed) {
                // Malformed payload while per-session: drop, never broadcast.
                droppedNonRoutableEvents.incrementAndGet();
            }
            return;
        }
        broadcast(event, json);
    }

    private void send(WebSocketSession session, String event, String json) {
        enqueueOutbound(session, envelopeJson(event, json), coalesceKeyFor(event, json));
    }

    private void sendEnvelope(WebSocketSession session, String envelope) {
        // Control / batch / cached-state envelopes are never coalesced — every one must be delivered.
        enqueueOutbound(session, envelope, null);
    }

    /**
     * Hand an envelope to the socket's bounded async channel and return immediately (P0 — the Kafka thread
     * NEVER blocks on browser I/O). A non-null {@code coalesceKey} collapses replaceable snapshots to
     * latest-wins. Untracked sessions (never added via {@link #addClient}) fall back to a guarded direct
     * send — that path is never on the Kafka hot loop.
     */
    private void enqueueOutbound(WebSocketSession session, String envelope, String coalesceKey) {
        if (session == null || envelope == null) {
            return;
        }
        OutboundChannel channel = outbound.get(session.getId());
        if (channel != null) {
            channel.enqueue(envelope, coalesceKey);
            return;
        }
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(envelope));
            }
        } catch (IOException | RuntimeException ignored) {
            closeQuietly(session);
        }
    }

    /**
     * Coalescing key for replaceable market-data snapshots: {@code event|symbol|expiry|strike}. Returns
     * null (never coalesce) for non-replaceable events, and for REPLAY records — those are distinct
     * historical events (stamped with {@code replaySourceOffset}), so every one must be delivered in order.
     */
    private String coalesceKeyFor(String event, String json) {
        if (event == null || !COALESCABLE_EVENTS.contains(event)) {
            return null;
        }
        if (json == null || json.contains("\"replaySourceOffset\"")) {
            return null; // replay record (or empty) — do not coalesce
        }
        try {
            JsonNode root = mapper.readTree(json);
            String symbol = root.hasNonNull("symbol") ? root.get("symbol").asText("") : "";
            if ("indicators".equals(event)) {
                // r3 finding 3: the frozen key is literally `indicators|symbol` —
                // additive fields must never split the coalescing identity.
                return event + "|" + symbol;
            }
            if ("tapeZones".equals(event)) {
                // ONE board per session (§6.2). The wrapper's own fields (offset/serverTime/ageMs)
                // must NEVER enter the key — they change on every emit, so keying on them would
                // defeat coalescing entirely and let a slow socket queue a session's worth of
                // whole-board snapshots. Frozen key: the event name alone.
                return event;
            }
            String expiry = root.hasNonNull("expiry") ? root.get("expiry").asText("") : "";
            String strike = root.hasNonNull("strike") ? root.get("strike").asText("") : "";
            String horizon = "option-truth".equals(event) && root.hasNonNull("horizon")
                    ? root.get("horizon").asText("") : "";
            return event + "|" + symbol + "|" + expiry + "|" + strike + "|" + horizon;
        } catch (JsonProcessingException unparseable) {
            return null; // when in doubt, deliver it
        }
    }

    /**
     * P0 (HPSF bypass fix): route a single HPSF view to exactly the sessions entitled to its chain,
     * through the SAME {@link SessionRoutingEngine} + entitlement checks as live contract events.
     * Contract-scoped HPSF events (signal/candidates/audit/exit-intent) route by {@code source|symbol|
     * expiry} with no strike filter (whole-chain decisions); market-flow routes by the underlying.
     * Unroutable records (unknown source/event or missing expiry) are dropped — NEVER broadcast.
     */
    private void routeHpsfPerSession(HpsfCacheUpdate update) {
        SessionRoutingEngine engine = routingEngine;
        if (engine == null) {
            return;
        }
        EventType type = hpsfEventType(update.event());
        Optional<MarketDataSource> source = MarketDataSource.parse(settings.initialMarketDataSource());
        if (type == null || source.isEmpty()) {
            droppedNonRoutableEvents.incrementAndGet();
            return;
        }
        RoutableRecord record;
        if (type.isUnderlying()) {
            record = RoutableRecord.underlying(source.get(), type, 0L);
        } else {
            if (update.expiry() == null || update.expiry().isBlank()) {
                droppedNonRoutableEvents.incrementAndGet();
                return;
            }
            record = new RoutableRecord(source.get(), type, settings.initialSymbol(), update.expiry(),
                    OptionalDouble.empty(), 0L, null, null);
        }
        for (String socketId : engine.route(record)) {
            WebSocketSession s = clientsById.get(socketId);
            if (s != null) {
                send(s, update.event(), update.json());
            }
        }
    }

    private static EventType hpsfEventType(String event) {
        return switch (event) {
            case "hpsf-latest-signal" -> EventType.HPSF_LATEST_SIGNAL;
            case "hpsf-top-candidates" -> EventType.HPSF_TOP_CANDIDATES;
            case "hpsf-audit" -> EventType.HPSF_AUDIT;
            case "hpsf-exit-intent" -> EventType.HPSF_EXIT_INTENT;
            case "hpsf-market-flow" -> EventType.HPSF_MARKET_FLOW;
            default -> null;
        };
    }

    private void enqueuePending(String event, String key, String json) {
        // Defense in depth (P0): the coalesced all-client batch is the LEGACY single-tenant path only.
        // In tenant-routing mode every consume site routes per-session, so this must be unreachable —
        // drop rather than risk a cross-tenant broadcast if any future call site forgets the gate.
        if (perSessionRouting()) {
            droppedNonRoutableEvents.incrementAndGet();
            return;
        }
        if (clients.isEmpty()) {
            return;
        }
        // Per-session routing is handled at the consume sites (with the authoritative binding source);
        // enqueuePending is the legacy batched path only.
        synchronized (batchLock) {
            Map<String, String> pending = pendingMap(event);
            if (pending == null) {
                return;
            }
            if (pending.put(key, json) != null) {
                coalescedUpdates.incrementAndGet();
            }
        }
    }

    private Map<String, String> pendingMap(String event) {
        return switch (event) {
            case "snapshot" -> pendingSnapshots;
            case "pace" -> pendingPaces;
            case "pace-rank" -> pendingPaceRanks;
            case "directional-pressure" -> pendingDirectionalPressures;
            case "strike-flow" -> pendingStrikeFlows;
            case "spot-band" -> pendingSpotBands;
            // Seller activity is REST-only and must never enter the option-chain WebSocket batch.
            case "delta-flow" -> pendingDeltaFlows;
            case "strike-intel" -> pendingStrikeIntels;
            case "strike-invasion" -> pendingStrikeInvasions;
            case "liquidity-heatmap" -> pendingLiquidityHeatmaps;
            case "mission-pace" -> pendingMissionPaces;
            case "mission-control" -> pendingMissionControls;
            case "spread-skew" -> pendingSpreadSkews;
            case "vix-price", "index-price" -> pendingIndexPrices;
            // Dedicated pending queue: the canonical SPX spot must keep its event identity through the
            // legacy batch too (its own additive `spxPrices` envelope field), never flattened into the
            // shared indexPrices collection (Codex round-1 P0).
            case "spx-price" -> pendingSpxPrices;
            case "volume-sandwich" -> pendingVolumeSandwiches;
            case "mission-sandwich" -> pendingMissionSandwiches;
            case "gex-by-strike" -> pendingGexByStrike;
            case "gex-oi-status" -> pendingGexOiStatus;
            case "strike-sr" -> pendingStrikeSr;
            case "gex-magnet" -> pendingGexMagnet;
            case "gamma-migration" -> pendingGammaMigration;
            case "es-gex" -> pendingEsGex;
            case "es-strike-intel" -> pendingEsStrikeIntel;
            case "gex-strike-lifecycle" -> pendingGexStrikeLifecycle;
            case "max-pain" -> pendingMaxPain;
            case "option-price-behavior" -> pendingOptionPriceBehaviors;
            case "opb-by-option" -> pendingOpbByOptions;
            case "opb-session" -> pendingOpbSessions;
            case "hpsf-latest-signal" -> pendingHpsfLatestSignals;
            case "hpsf-market-flow" -> pendingHpsfMarketFlows;
            case "hpsf-top-candidates" -> pendingHpsfTopCandidates;
            case "hpsf-audit" -> pendingHpsfAudits;
            case "hpsf-exit-intent" -> pendingHpsfExitIntents;
            default -> null;
        };
    }

    private void flushPendingBatch() {
        // P0 (HPSF bypass): the all-client batch broadcasts one envelope to EVERY socket with no routing.
        // In tenant-routing mode that is a cross-tenant leak, so the path is unreachable here: drain any
        // residue and return without sending. All events route per-session at their consume sites instead.
        if (perSessionRouting()) {
            synchronized (batchLock) {
                clearPendingLocked();
            }
            return;
        }
        try {
            String envelope;
            synchronized (batchLock) {
                if (pendingEventCountLocked() == 0) {
                    return;
                }
                if (clients.isEmpty()) {
                    clearPendingLocked();
                    return;
                }
                envelope = uiBatchEnvelopeJson(
                        new ArrayList<>(pendingSnapshots.values()),
                        new ArrayList<>(pendingPaces.values()),
                        new ArrayList<>(pendingPaceRanks.values()),
                        new ArrayList<>(pendingDirectionalPressures.values()),
                        new ArrayList<>(pendingStrikeFlows.values()),
                        new ArrayList<>(pendingSpotBands.values()),
                        new ArrayList<>(pendingSellerActivities.values()),
                        new ArrayList<>(pendingDeltaFlows.values()),
                        new ArrayList<>(pendingStrikeIntels.values()),
                        new ArrayList<>(pendingStrikeInvasions.values()),
                        new ArrayList<>(pendingLiquidityHeatmaps.values()),
                        new ArrayList<>(pendingMissionPaces.values()),
                        new ArrayList<>(pendingMissionControls.values()),
                        new ArrayList<>(pendingSpreadSkews.values()),
                        new ArrayList<>(pendingIndexPrices.values()),
                        new ArrayList<>(pendingVolumeSandwiches.values()),
                        new ArrayList<>(pendingMissionSandwiches.values()),
                        new ArrayList<>(pendingGexByStrike.values()),
                        new ArrayList<>(pendingGexOiStatus.values()),
                        new ArrayList<>(pendingStrikeSr.values()),
                        new ArrayList<>(pendingGexMagnet.values()),
                        new ArrayList<>(pendingGexStrikeLifecycle.values()),
                        new ArrayList<>(pendingMaxPain.values()),
                        new ArrayList<>(pendingOptionPriceBehaviors.values()),
                        new ArrayList<>(pendingOpbByOptions.values()),
                        new ArrayList<>(pendingOpbSessions.values()),
                        new ArrayList<>(pendingHpsfLatestSignals.values()),
                        new ArrayList<>(pendingHpsfMarketFlows.values()),
                        new ArrayList<>(pendingHpsfTopCandidates.values()),
                        new ArrayList<>(pendingHpsfAudits.values()),
                        new ArrayList<>(pendingHpsfExitIntents.values()),
                        new ArrayList<>(pendingEsGex.values()),
                        new ArrayList<>(pendingEsStrikeIntel.values()),
                        // Appended LAST (not next to indexPrices) so the positional test reflection
                        // helpers stay stable; the JSON field order below is independent of this order.
                        new ArrayList<>(pendingSpxPrices.values()),
                        new ArrayList<>(pendingGammaMigration.values())
                );
                clearPendingLocked();
            }
            for (WebSocketSession client : clients) {
                sendEnvelope(client, envelope);
            }
            batchesSent.incrementAndGet();
        } catch (RuntimeException e) {
            if (running.get()) {
                System.err.println("Feed gateway batch flush error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private int pendingEventCount() {
        synchronized (batchLock) {
            return pendingEventCountLocked();
        }
    }

    private int pendingEventCountLocked() {
        return pendingSnapshots.size()
                + pendingPaces.size()
                + pendingPaceRanks.size()
                + pendingDirectionalPressures.size()
                + pendingStrikeFlows.size()
                + pendingSpotBands.size()
                + pendingSellerActivities.size()
                + pendingDeltaFlows.size()
                + pendingStrikeIntels.size()
                + pendingStrikeInvasions.size()
                + pendingLiquidityHeatmaps.size()
                + pendingMissionPaces.size()
                + pendingMissionControls.size()
                + pendingSpreadSkews.size()
                + pendingIndexPrices.size()
                + pendingSpxPrices.size()
                + pendingVolumeSandwiches.size()
                + pendingMissionSandwiches.size()
                + pendingGexByStrike.size()
                + pendingGexOiStatus.size()
                + pendingStrikeSr.size()
                + pendingGexMagnet.size()
                + pendingGammaMigration.size()
                + pendingEsGex.size()
                + pendingEsStrikeIntel.size()
                + pendingGexStrikeLifecycle.size()
                + pendingMaxPain.size()
                + pendingOptionPriceBehaviors.size()
                + pendingOpbByOptions.size()
                + pendingOpbSessions.size()
                + pendingHpsfLatestSignals.size()
                + pendingHpsfMarketFlows.size()
                + pendingHpsfTopCandidates.size()
                + pendingHpsfAudits.size()
                + pendingHpsfExitIntents.size();
    }

    private void clearPendingLocked() {
        pendingSnapshots.clear();
        pendingPaces.clear();
        pendingPaceRanks.clear();
        pendingDirectionalPressures.clear();
        pendingStrikeFlows.clear();
        pendingSpotBands.clear();
        pendingSellerActivities.clear();
        pendingDeltaFlows.clear();
        pendingStrikeIntels.clear();
        pendingStrikeInvasions.clear();
        pendingLiquidityHeatmaps.clear();
        pendingMissionPaces.clear();
        pendingMissionControls.clear();
        pendingSpreadSkews.clear();
        pendingIndexPrices.clear();
        pendingSpxPrices.clear();
        pendingVolumeSandwiches.clear();
        pendingMissionSandwiches.clear();
        pendingGexByStrike.clear();
        pendingGexOiStatus.clear();
        pendingStrikeSr.clear();
        pendingGexMagnet.clear();
        pendingGammaMigration.clear();
        pendingEsGex.clear();
        pendingEsStrikeIntel.clear();
        pendingGexStrikeLifecycle.clear();
        pendingMaxPain.clear();
        pendingOptionPriceBehaviors.clear();
        pendingOpbByOptions.clear();
        pendingOpbSessions.clear();
        pendingHpsfLatestSignals.clear();
        pendingHpsfMarketFlows.clear();
        pendingHpsfTopCandidates.clear();
        pendingHpsfAudits.clear();
        pendingHpsfExitIntents.clear();
    }

    private String envelopeJson(String event, String json) {
        String payload = json == null || json.isBlank() ? "{}" : json;
        return "{\"type\":\"" + escapeJson(event) + "\",\"data\":" + payload + "}";
    }

    private String uiBatchEnvelopeJson(List<CachedEvent> cachedEvents) {
        List<String> snapshotJsons = new ArrayList<>();
        List<String> paceJsons = new ArrayList<>();
        List<String> paceRankJsons = new ArrayList<>();
        List<String> directionalPressureJsons = new ArrayList<>();
        List<String> strikeFlowJsons = new ArrayList<>();
        List<String> spotBandJsons = new ArrayList<>();
        List<String> sellerActivityJsons = new ArrayList<>();
        List<String> deltaFlowJsons = new ArrayList<>();
        List<String> strikeIntelJsons = new ArrayList<>();
        List<String> strikeInvasionJsons = new ArrayList<>();
        List<String> liquidityHeatmapJsons = new ArrayList<>();
        List<String> missionPaceJsons = new ArrayList<>();
        List<String> missionControlJsons = new ArrayList<>();
        List<String> spreadSkewJsons = new ArrayList<>();
        List<String> indexPriceJsons = new ArrayList<>();
        List<String> spxPriceJsons = new ArrayList<>();
        List<String> volumeSandwichJsons = new ArrayList<>();
        List<String> missionSandwichJsons = new ArrayList<>();
        List<String> gexByStrikeJsons = new ArrayList<>();
        List<String> gexOiStatusJsons = new ArrayList<>();
        List<String> strikeSrJsons = new ArrayList<>();
        List<String> gexMagnetJsons = new ArrayList<>();
        List<String> gammaMigrationJsons = new ArrayList<>();
        List<String> esGexJsons = new ArrayList<>();
        List<String> esStrikeIntelJsons = new ArrayList<>();
        List<String> gexStrikeLifecycleJsons = new ArrayList<>();
        List<String> maxPainJsons = new ArrayList<>();
        List<String> optionPriceBehaviorJsons = new ArrayList<>();
        List<String> opbByOptionJsons = new ArrayList<>();
        List<String> opbSessionJsons = new ArrayList<>();
        List<String> hpsfLatestSignalJsons = new ArrayList<>();
        List<String> hpsfMarketFlowJsons = new ArrayList<>();
        List<String> hpsfTopCandidatesJsons = new ArrayList<>();
        List<String> hpsfAuditJsons = new ArrayList<>();
        List<String> hpsfExitIntentJsons = new ArrayList<>();
        for (CachedEvent cachedEvent : cachedEvents) {
            switch (cachedEvent.event()) {
                case "snapshot" -> snapshotJsons.add(cachedEvent.json());
                case "pace" -> paceJsons.add(cachedEvent.json());
                case "pace-rank" -> paceRankJsons.add(cachedEvent.json());
                case "directional-pressure" -> directionalPressureJsons.add(cachedEvent.json());
                case "strike-flow" -> strikeFlowJsons.add(cachedEvent.json());
                case "spot-band" -> spotBandJsons.add(cachedEvent.json());
                case "seller-activity" -> {
                    // REST-only. Keep the additive sellerActivities field empty for older clients.
                }
                case "delta-flow" -> deltaFlowJsons.add(cachedEvent.json());
                case "strike-intel" -> strikeIntelJsons.add(cachedEvent.json());
                case "strike-invasion" -> strikeInvasionJsons.add(cachedEvent.json());
                case "liquidity-heatmap" -> liquidityHeatmapJsons.add(cachedEvent.json());
                case "mission-pace" -> missionPaceJsons.add(cachedEvent.json());
                case "mission-control" -> missionControlJsons.add(cachedEvent.json());
                case "spread-skew" -> spreadSkewJsons.add(cachedEvent.json());
                case "vix-price", "index-price" -> indexPriceJsons.add(cachedEvent.json());
                // Dedicated envelope field — the canonical SPX spot's event identity survives the batch
                // (never flattened into indexPrices; Codex round-1 P0).
                case "spx-price" -> spxPriceJsons.add(cachedEvent.json());
                case "volume-sandwich" -> volumeSandwichJsons.add(cachedEvent.json());
                case "mission-sandwich" -> missionSandwichJsons.add(cachedEvent.json());
                case "gex-by-strike" -> gexByStrikeJsons.add(cachedEvent.json());
                case "gex-oi-status" -> gexOiStatusJsons.add(cachedEvent.json());
                case "strike-sr" -> strikeSrJsons.add(cachedEvent.json());
                case "gex-magnet" -> gexMagnetJsons.add(cachedEvent.json());
                case "gamma-migration" -> gammaMigrationJsons.add(cachedEvent.json());
                case "es-gex" -> esGexJsons.add(cachedEvent.json());
                case "es-strike-intel" -> esStrikeIntelJsons.add(cachedEvent.json());
                case "gex-strike-lifecycle" -> gexStrikeLifecycleJsons.add(cachedEvent.json());
                case "max-pain" -> maxPainJsons.add(cachedEvent.json());
                case "option-price-behavior" -> optionPriceBehaviorJsons.add(cachedEvent.json());
                case "opb-by-option" -> opbByOptionJsons.add(cachedEvent.json());
                case "opb-session" -> opbSessionJsons.add(cachedEvent.json());
                case "hpsf-latest-signal" -> hpsfLatestSignalJsons.add(cachedEvent.json());
                case "hpsf-market-flow" -> hpsfMarketFlowJsons.add(cachedEvent.json());
                case "hpsf-top-candidates" -> hpsfTopCandidatesJsons.add(cachedEvent.json());
                case "hpsf-audit" -> hpsfAuditJsons.add(cachedEvent.json());
                case "hpsf-exit-intent" -> hpsfExitIntentJsons.add(cachedEvent.json());
                default -> {
                    // This batch protocol only carries latest-state feed events.
                }
            }
        }
        return uiBatchEnvelopeJson(
                snapshotJsons,
                paceJsons,
                paceRankJsons,
                directionalPressureJsons,
                strikeFlowJsons,
                spotBandJsons,
                sellerActivityJsons,
                deltaFlowJsons,
                strikeIntelJsons,
                strikeInvasionJsons,
                liquidityHeatmapJsons,
                missionPaceJsons,
                missionControlJsons,
                spreadSkewJsons,
                indexPriceJsons,
                volumeSandwichJsons,
                missionSandwichJsons,
                gexByStrikeJsons,
                gexOiStatusJsons,
                strikeSrJsons,
                gexMagnetJsons,
                gexStrikeLifecycleJsons,
                maxPainJsons,
                optionPriceBehaviorJsons,
                opbByOptionJsons,
                opbSessionJsons,
                hpsfLatestSignalJsons,
                hpsfMarketFlowJsons,
                hpsfTopCandidatesJsons,
                hpsfAuditJsons,
                hpsfExitIntentJsons,
                esGexJsons,
                esStrikeIntelJsons,
                spxPriceJsons,
                gammaMigrationJsons
        );
    }

    // Backward-compatible test/reflection seam for the established batch contract. New callers use the
    // overload below with sellerActivityJsons; old callers receive an empty additive array.
    private String uiBatchEnvelopeJson(
            List<String> snapshotJsons,
            List<String> paceJsons,
            List<String> paceRankJsons,
            List<String> directionalPressureJsons,
            List<String> strikeFlowJsons,
            List<String> deltaFlowJsons,
            List<String> strikeIntelJsons,
            List<String> strikeInvasionJsons,
            List<String> liquidityHeatmapJsons,
            List<String> missionPaceJsons,
            List<String> missionControlJsons,
            List<String> spreadSkewJsons,
            List<String> indexPriceJsons,
            List<String> volumeSandwichJsons,
            List<String> missionSandwichJsons,
            List<String> gexByStrikeJsons,
            List<String> gexOiStatusJsons,
            List<String> strikeSrJsons,
            List<String> gexMagnetJsons,
            List<String> gexStrikeLifecycleJsons,
            List<String> maxPainJsons,
            List<String> optionPriceBehaviorJsons,
            List<String> opbByOptionJsons,
            List<String> opbSessionJsons,
            List<String> hpsfLatestSignalJsons,
            List<String> hpsfMarketFlowJsons,
            List<String> hpsfTopCandidatesJsons,
            List<String> hpsfAuditJsons,
            List<String> hpsfExitIntentJsons,
            List<String> esGexJsons,
            List<String> esStrikeIntelJsons,
            // Appended LAST so the positional test reflection helpers stay stable; the JSON field sits
            // next to indexPrices below regardless.
            List<String> spxPriceJsons
    ) {
        return uiBatchEnvelopeJson(
                snapshotJsons, paceJsons, paceRankJsons, directionalPressureJsons, strikeFlowJsons,
                // this older overload carries neither seller-activity nor spot-band
                List.of(), List.of(), deltaFlowJsons, strikeIntelJsons, strikeInvasionJsons, liquidityHeatmapJsons,
                missionPaceJsons, missionControlJsons, spreadSkewJsons, indexPriceJsons,
                volumeSandwichJsons, missionSandwichJsons, gexByStrikeJsons, gexOiStatusJsons, strikeSrJsons,
                gexMagnetJsons, gexStrikeLifecycleJsons, maxPainJsons, optionPriceBehaviorJsons,
                opbByOptionJsons, opbSessionJsons, hpsfLatestSignalJsons, hpsfMarketFlowJsons,
                hpsfTopCandidatesJsons, hpsfAuditJsons, hpsfExitIntentJsons, esGexJsons,
                esStrikeIntelJsons, spxPriceJsons, List.of());
    }

    private String uiBatchEnvelopeJson(
            List<String> snapshotJsons,
            List<String> paceJsons,
            List<String> paceRankJsons,
            List<String> directionalPressureJsons,
            List<String> strikeFlowJsons,
            List<String> spotBandJsons,
            List<String> sellerActivityJsons,
            List<String> deltaFlowJsons,
            List<String> strikeIntelJsons,
            List<String> strikeInvasionJsons,
            List<String> liquidityHeatmapJsons,
            List<String> missionPaceJsons,
            List<String> missionControlJsons,
            List<String> spreadSkewJsons,
            List<String> indexPriceJsons,
            List<String> volumeSandwichJsons,
            List<String> missionSandwichJsons,
            List<String> gexByStrikeJsons,
            List<String> gexOiStatusJsons,
            List<String> strikeSrJsons,
            List<String> gexMagnetJsons,
            List<String> gexStrikeLifecycleJsons,
            List<String> maxPainJsons,
            List<String> optionPriceBehaviorJsons,
            List<String> opbByOptionJsons,
            List<String> opbSessionJsons,
            List<String> hpsfLatestSignalJsons,
            List<String> hpsfMarketFlowJsons,
            List<String> hpsfTopCandidatesJsons,
            List<String> hpsfAuditJsons,
            List<String> hpsfExitIntentJsons,
            List<String> esGexJsons,
            List<String> esStrikeIntelJsons,
            List<String> spxPriceJsons,
            // Appended LAST for the same reason spxPriceJsons was: the positional reflection
            // helpers in the tests index this signature, so new fields go on the end.
            List<String> gammaMigrationJsons
    ) {
        ActiveSelection selection = activeSelection.get();
        return "{"
                + "\"type\":\"ui-batch\","
                + "\"data\":{"
                + "\"sentAtMs\":" + System.currentTimeMillis() + ","
                + "\"cadenceMs\":" + settings.webSocketBatchMs() + ","
                + "\"marketDataSource\":\"" + escapeJson(selection.source()) + "\","
                + "\"symbol\":\"" + escapeJson(selection.symbol()) + "\","
                + "\"expiry\":\"" + escapeJson(selection.expiry()) + "\","
                + "\"selectionEpoch\":" + selection.selectionEpoch() + ","
                + "\"snapshots\":" + jsonArray(snapshotJsons) + ","
                + "\"paces\":" + jsonArray(paceJsons) + ","
                + "\"paceRanks\":" + jsonArray(paceRankJsons) + ","
                + "\"directionalPressures\":" + jsonArray(directionalPressureJsons) + ","
                + "\"strikeFlows\":" + jsonArray(strikeFlowJsons) + ","
                + "\"spotBands\":" + jsonArray(spotBandJsons) + ","
                + "\"sellerActivities\":" + jsonArray(sellerActivityJsons) + ","
                + "\"deltaFlows\":" + jsonArray(deltaFlowJsons) + ","
                + "\"strikeIntels\":" + jsonArray(strikeIntelJsons) + ","
                + "\"strikeInvasions\":" + jsonArray(strikeInvasionJsons) + ","
                + "\"liquidityHeatmaps\":" + jsonArray(liquidityHeatmapJsons) + ","
                + "\"missionPaces\":" + jsonArray(missionPaceJsons) + ","
                + "\"missionControls\":" + jsonArray(missionControlJsons) + ","
                + "\"spreadSkews\":" + jsonArray(spreadSkewJsons) + ","
                + "\"indexPrices\":" + jsonArray(indexPriceJsons) + ","
                // Additive field: the canonical SPX spot keeps its own event identity through the batch
                // (older UIs ignore the unknown key; never flattened into indexPrices).
                + "\"spxPrices\":" + jsonArray(spxPriceJsons) + ","
                + "\"volumeSandwiches\":" + jsonArray(volumeSandwichJsons) + ","
                + "\"missionSandwiches\":" + jsonArray(missionSandwichJsons) + ","
                + "\"gexByStrike\":" + jsonArray(gexByStrikeJsons) + ","
                // Additive field: per-strike OI-arrival status (OI_MISSING/OI_OK badge rows). Older UIs
                // ignore the unknown key.
                + "\"gexOiStatus\":" + jsonArray(gexOiStatusJsons) + ","
                + "\"strikeSr\":" + jsonArray(strikeSrJsons) + ","
                + "\"gexMagnets\":" + jsonArray(gexMagnetJsons) + ","
                // Additive: where the magnet says which strike gamma sits ON, this says where it
                // is GOING. Older UIs ignore the unknown key.
                + "\"gammaMigrations\":" + jsonArray(gammaMigrationJsons) + ","
                + "\"esGex\":" + jsonArray(esGexJsons) + ","
                // Gated so the disabled wire is unchanged (the web reads batchItems('esStrikeIntels') which
                // tolerates an absent key). Status keeps the EXPLICIT esStrikeIntel(Enabled) fields on purpose
                // — a missing/"0" count misled a live prod triage (2026-07-19), same as esGex.
                + (settings.esStrikeIntelEnabled()
                        ? "\"esStrikeIntels\":" + jsonArray(esStrikeIntelJsons) + "," : "")
                + "\"gexStrikeLifecycle\":" + jsonArray(gexStrikeLifecycleJsons) + ","
                + "\"maxPains\":" + jsonArray(maxPainJsons) + ","
                + "\"optionPriceBehaviors\":" + jsonArray(optionPriceBehaviorJsons) + ","
                + "\"opbByOptions\":" + jsonArray(opbByOptionJsons) + ","
                + "\"opbSessions\":" + jsonArray(opbSessionJsons) + ","
                + "\"hpsfLatestSignals\":" + jsonArray(hpsfLatestSignalJsons) + ","
                + "\"hpsfMarketFlows\":" + jsonArray(hpsfMarketFlowJsons) + ","
                + "\"hpsfTopCandidates\":" + jsonArray(hpsfTopCandidatesJsons) + ","
                + "\"hpsfAudits\":" + jsonArray(hpsfAuditJsons) + ","
                + "\"hpsfExitIntents\":" + jsonArray(hpsfExitIntentJsons)
                + "}"
                + "}";
    }

    private String jsonArray(List<String> jsons) {
        if (jsons == null || jsons.isEmpty()) {
            return "[]";
        }
        return "[" + String.join(",", jsons) + "]";
    }

    private String statusJson() {
        ActiveSelection selection = activeSelection.get();
        return "{"
                + "\"status\":\"connected\","
                + "\"time\":\"" + escapeJson(Instant.now().toString()) + "\","
                + "\"marketDataSource\":\"" + escapeJson(selection.source()) + "\","
                + "\"symbol\":\"" + escapeJson(selection.symbol()) + "\","
                + "\"expiry\":\"" + escapeJson(selection.expiry()) + "\","
                + "\"selectionEpoch\":" + selection.selectionEpoch() + ","
                + "\"avroCaughtUp\":" + avroCaughtUp.get() + ","
                + "\"stateCaughtUp\":" + stateCaughtUp.get() + ","
                + "\"hpsfCaughtUp\":" + hpsfCaughtUp.get() + ","
                + "\"lastSelectedForwardAgeSeconds\":" + lastSelectedForwardAgeSeconds(selection) + ","
                + "\"snapshots\":" + snapshots.size() + ","
                + "\"paces\":" + paces.size() + ","
                + "\"paceRanks\":" + paceRanks.size() + ","
                + "\"directionalPressures\":" + directionalPressures.size() + ","
                + "\"strikeFlows\":" + strikeFlows.size() + ","
                + "\"deltaFlows\":" + deltaFlows.size() + ","
                + "\"strikeIntels\":" + strikeIntels.size() + ","
                + "\"optionTruths\":" + optionTruths.size() + ","
                + "\"liquidityHeatmaps\":" + liquidityHeatmaps.size() + ","
                + "\"missionPaces\":" + missionPaces.size() + ","
                + "\"missionControls\":" + missionControls.size() + ","
                + "\"spreadSkews\":" + spreadSkews.size() + ","
                + "\"gexByStrike\":" + gexByStrike.size() + ","
                + "\"maxPain\":" + maxPain.size() + ","
                + "\"optionPriceBehaviors\":" + optionPriceBehaviors.size() + ","
                + "\"opbByOptions\":" + opbByOptions.size() + ","
                + "\"opbSessions\":" + opbSessions.size() + ","
                + "\"hpsfLatestSignals\":" + hpsfLatestSignals.size() + ","
                + "\"hpsfMarketFlows\":" + hpsfMarketFlows.size() + ","
                + "\"hpsfTopCandidates\":" + hpsfTopCandidates.size() + ","
                + "\"hpsfAudits\":" + hpsfAudits.size() + ","
                + "\"hpsfExitIntents\":" + hpsfExitIntents.size() + ","
                + "\"cacheTtlMs\":" + settings.cacheTtlMs()
                + "}";
    }

    private String activeSelectionJson(ActiveSelection selection) {
        return activeSelectionJson(selection, "selected");
    }

    private String activeSelectionJson(ActiveSelection selection, String status) {
        return "{"
                + "\"status\":\"" + escapeJson(status) + "\","
                + "\"time\":\"" + escapeJson(Instant.now().toString()) + "\","
                + "\"marketDataSource\":\"" + escapeJson(selection.source()) + "\","
                + "\"symbol\":\"" + escapeJson(selection.symbol()) + "\","
                + "\"expiry\":\"" + escapeJson(selection.expiry()) + "\","
                + "\"selectionEpoch\":" + selection.selectionEpoch() + ","
                + "\"lastSelectedForwardAgeSeconds\":" + lastSelectedForwardAgeSeconds(selection)
                + "}";
    }

    private Properties avroConsumerProperties(String name) {
        Properties properties = baseConsumerProperties(name);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        properties.put("schema.registry.url", settings.schemaRegistryUrl());
        properties.put("specific.avro.reader", "false");
        // Every Avro topic this gateway consumes (display/pace/directional-pressure/gex/max-pain) registers
        // its schema under a RECORD-NAME subject (e.g. app.options.maxpain.MaxPainSnapshot), NOT the default
        // <topic>-value. The GenericRecord consume-by-id path resolves the WRITER schema by the message's
        // embedded schema id (verified: GET /schemas/ids/{id} returns the schema even for a non-existent
        // subject), so this is belt-and-suspenders rather than strictly required — but setting it makes the
        // deserializer's derived subject match how these schemas are actually registered, which is the
        // correct, fail-closed default for the record-name convention used across these topics.
        properties.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        return properties;
    }

    private Properties stringObjectConsumerProperties(String name) {
        Properties properties = baseConsumerProperties(name);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }

    private Properties stringConsumerProperties(String name) {
        Properties properties = baseConsumerProperties(name);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }

    private Properties baseConsumerProperties(String name) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, settings.groupIdBase() + "-" + name);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // read_committed so an ABORTED pre-open GEX transaction is never surfaced (safe/identical on the
        // non-transactional topics too). The endOffsets-only barrier consumer overrides this — see
        // captureOffsetBarriers + BARRIER_CONSUMER_ISOLATION.
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, RECORD_CONSUMER_ISOLATION);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // A READER NEVER CREATES A TOPIC. Subscribing to an absent topic makes the consumer ask the
        // broker for its metadata, and with auto-creation enabled the broker MAKES it — at cluster
        // defaults, which here is 32 partitions and no compaction.
        //
        // That is not a tidiness point. options.spx.vol-premium.ivrv is a single ordered series
        // whose consumers read a per-session frame ordinal to decide whether two readings were
        // observed back to back, and its producer refuses to start on any partition count but one.
        // On 2026-08-28 this gateway auto-created that topic at 32 partitions within two seconds of
        // it being deleted, and the producer then crash-looped: a service correctly refusing to
        // publish an unorderable series, blocked by a reader that had no business creating anything.
        // Every clean-slate would have reproduced it.
        //
        // The owner of a topic is whoever declares it — the deploy repo's topics.env, or the
        // producer's own ensureTopics. This process is neither, for any topic it reads.
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, settings.groupIdBase() + "-" + name);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Integer.toString(settings.maxPollRecords()));
        properties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, Integer.toString(settings.fetchMaxBytes()));
        properties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, Integer.toString(settings.maxPartitionFetchBytes()));
        properties.put(ConsumerConfig.RECEIVE_BUFFER_CONFIG, Integer.toString(settings.receiveBufferBytes()));
        // Partition-growth detection depends on this. KafkaConsumer.partitionsFor() only FORCES a metadata
        // fetch for a topic the client does not know yet; for a known topic it answers from the client's
        // metadata cache, which the client refreshes on its own schedule — metadata.max.age.ms, Kafka
        // default 5 MINUTES. Without this line the periodic refresh in PartitionRefresh re-reads a stale
        // cache and GATEWAY_PARTITION_METADATA_REFRESH_MS bounds nothing: a 4→32 expansion would still take
        // up to 5 minutes to be seen. Pin the client's refresh to ours so the knob means what it says.
        properties.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG,
                Long.toString(settings.partitionMetadataRefreshMs()));
        settings.applyKafkaSecurity(properties); // TLS/SASL when configured (required under auth — P0)
        return properties;
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    // ============================================================================================
    // Rollover-diagnostics instrumentation. All methods below are additive and never gate real
    // behavior. Purpose: after the 2026-07-01 9.5-hour silent-wedge incident where a midnight-ET
    // rollover left activeSelection/cacheCaughtUp/shouldForward in a state that dropped every
    // record without an error, we want to know within minutes which flag flipped wrong. Every log
    // line is structured (key=value) so `grep RGW_` in Loki/Discord surfaces the timeline.
    // ============================================================================================

    /**
     * Structured periodic state dump. Emits ONE INFO line every 60s with all rollover-adjacent flags
     * and the forwarded-events delta since the last dump. Also drives the two-cycle
     * "GATEWAY_FORWARD_STALLED_DURING_MARKET_HOURS" ERROR when the gateway is provably not forwarding
     * during trading hours even though consumers are advancing.
     *
     * <p>Called by {@link #diagnosticsExecutor} every 60s (and directly from tests).
     */
    /**
     * Make partition-assignment health EXTERNALLY OBSERVABLE. The 4→32 incident was silent precisely
     * because a partial assignment looks identical to a healthy one from outside: the pod is Ready, the
     * consumer polls, records flow — they are simply the wrong subset. Every line below exists so that
     * failure mode is greppable and alertable rather than something a human notices on a chart.
     *
     * <p>Two conditions are escalated to RGW_ALERT because they are actionable and cannot be benign:
     * <ul>
     *   <li>{@code assigned != discovered} — the consumer is knowably not reading a partition that exists.</li>
     *   <li>cache reporting CAUGHT UP while the SELECTED source still has a partition replaying — the exact
     *       "we certified an incomplete cache" shape this whole programme exists to prevent.</li>
     * </ul>
     */
    private void emitPartitionRefreshDiagnostics(long nowMs, ActiveSelection selection) {
        String selectedSource = selection == null ? "" : selection.source();
        List<TopicPartition> selectedBootstrapping = bootstrappingPartitionsForSource(selectedSource);
        long oldestBootstrapAgeMs = bootstrappingPartitions.values().stream()
                .mapToLong(state -> Math.max(0L, nowMs - state.sinceMs()))
                .max()
                .orElse(0L);
        partitionRefreshes.forEach((consumerName, refresh) -> {
            int assigned = assignedPartitionCounts.getOrDefault(consumerName, -1);
            int discovered = refresh.lastDiscoveredCount();
            long staleMs = Math.max(0L, nowMs - refresh.lastObservedMs());
            System.out.println("RGW_PARTITIONS event=partition_refresh_state"
                    + " consumer=" + quote(consumerName)
                    + " assignedPartitions=" + assigned
                    + " discoveredPartitions=" + discovered
                    + " refreshFailing=" + refresh.refreshFailing()
                    + " msSinceLastTopologyObservation=" + staleMs
                    + " bootstrappingPartitions=" + bootstrappingPartitions.size()
                    + " oldestBootstrapAgeMs=" + oldestBootstrapAgeMs);
            // Alert ONLY when discovery sees MORE than is assigned: partitions exist that this consumer is
            // knowably not reading. The opposite direction (assigned > discovered) is healthy and expected:
            // the union assignment deliberately RETAINS an absent topic's partitions while that topic is
            // momentarily absent from a discovery pass, so equality is not an invariant.
            if (discovered > 0 && assigned >= 0 && discovered > assigned) {
                System.err.println("RGW_ALERT event=GATEWAY_PARTITION_ASSIGNMENT_INCOMPLETE"
                        + " consumer=" + quote(consumerName)
                        + " assignedPartitions=" + assigned
                        + " discoveredPartitions=" + discovered);
            }
            if (staleMs > 2L * settings.partitionMetadataRefreshMs()) {
                System.err.println("RGW_ALERT event=GATEWAY_PARTITION_METADATA_STALE"
                        + " consumer=" + quote(consumerName)
                        + " msSinceLastTopologyObservation=" + staleMs
                        + " refreshIntervalMs=" + settings.partitionMetadataRefreshMs());
            }
        });
        boolean cacheClaimsReady = stateCaughtUp.get() && avroCaughtUp.get();
        if (cacheClaimsReady && !selectedBootstrapping.isEmpty()) {
            System.err.println("RGW_ALERT event=GATEWAY_CACHE_READY_WITH_INCOMPLETE_BOOTSTRAP"
                    + " selectedSource=" + quote(selectedSource)
                    + " bootstrappingPartitions=" + selectedBootstrapping);
        }
    }

    void dumpDiagnosticState() {
        if (!diagnosticsEnabled) {
            return;
        }
        try {
            long nowMs = System.currentTimeMillis();
            ActiveSelection selection = activeSelection.get();
            long forwardedNow = forwardedEvents.get();
            long delta = Math.max(0L, forwardedNow - lastForwardedSnapshot.getAndSet(forwardedNow));
            long lastRoll = lastRolloverAtMs.get();
            String hoursSinceRoll = lastRoll <= 0L
                    ? "never"
                    : String.format(java.util.Locale.ROOT, "%.2f", (nowMs - lastRoll) / 3_600_000.0);
            int activeSessions = activeSessionsCount();
            long polled = liveRecordsPolled.get();
            // Per-interval delta — cumulative `polled` would stay > 0 forever after the first record,
            // making `consumersAdvancing` a false positive during legitimately quiet cycles. Use the
            // delta since the previous dump instead. (Codex P2 fix.)
            long polledDelta = Math.max(0L, polled - lastDumpLiveRecordsPolledSnapshot.getAndSet(polled));
            // Codex round-4 P2: `consumersAdvancing` must reflect records ELIGIBLE for the active
            // selection (source matches, or HPSF which is not source-gated). Otherwise noisy traffic
            // from a non-selected source (e.g. IB traffic while DATABENTO is selected) keeps
            // polledDelta > 0 and hides a real wedge in the selected pipeline.
            long eligible = liveRecordsEligibleForActiveSelection.get();
            long eligibleDelta = Math.max(0L, eligible - lastDumpLiveRecordsEligibleSnapshot.getAndSet(eligible));

            System.out.println("RGW_STATE_DUMP event=state_dump"
                    + " activeSelection=" + describeSelection(selection)
                    + " avroCaughtUp=" + avroCaughtUp.get()
                    + " stateCaughtUp=" + stateCaughtUp.get()
                    + " hpsfCaughtUp=" + hpsfCaughtUp.get()
                    + " readySelectionKey=" + quote(readySelectionKey.get())
                    + " autoRolledExpiry=" + quote(autoRolledExpiry)
                    + " lastRolloverAtMs=" + lastRoll
                    + " hoursSinceLastRollover=" + hoursSinceRoll
                    + " rolloverCount=" + rolloverCount.get()
                    + " activeSessions=" + activeSessions
                    + " connectedClients=" + clients.size()
                    + " forwardedEventsSinceLastLog=" + delta
                    + " forwardedEventsTotal=" + forwardedNow
                    + " liveRecordsPolled=" + polled
                    + " liveRecordsEligibleForActiveSelection=" + eligible
                    + " droppedByStaleness=" + droppedByStaleness.get()
                    + " droppedByCacheGate=" + droppedByCacheGate.get()
                    + " droppedByOtherReasons=" + droppedByOtherReasons.get()
                    + " inactiveDroppedEvents=" + inactiveDroppedEvents.get()
                    + " staleDroppedEvents=" + staleDroppedEvents.get()
                    + " strikeBandsRejected=" + strikeBandsRejected.get()
                    + " tapeZonesRejected=" + tapeZonesRejected.get()
                    + " sourceStaleEvents=" + sourceStaleEvents.get()
                    + " offsetBarriers=" + offsetBarriers.get().size()
                    + " running=" + running.get()
                    + " nowMs=" + nowMs);

            emitPartitionRefreshDiagnostics(nowMs, selection);

            boolean marketHours = isRegularTradingHours(nowMs);
            boolean consumersAdvancing = eligibleDelta > 0L;
            // Gate on ATTACHED sockets, not registered AppSessions: an AppSession in the grace
            // window has no user waiting for data, so a zero-forward cycle there is not a stall.
            // (Codex round-3 P2.)
            long attachedSockets = attachedSocketCount();
            if (marketHours && delta == 0L && attachedSockets > 0L && consumersAdvancing) {
                consecutiveZeroForwardCycles++;
            } else {
                consecutiveZeroForwardCycles = 0;
            }
            if (consecutiveZeroForwardCycles >= 2) {
                forwardStalledAlerts.incrementAndGet();
                System.err.println("RGW_ALERT event=GATEWAY_FORWARD_STALLED_DURING_MARKET_HOURS"
                        + " consecutiveZeroForwardCycles=" + consecutiveZeroForwardCycles
                        + " activeSelection=" + describeSelection(selection)
                        + " avroCaughtUp=" + avroCaughtUp.get()
                        + " stateCaughtUp=" + stateCaughtUp.get()
                        + " hpsfCaughtUp=" + hpsfCaughtUp.get()
                        + " readySelectionKey=" + quote(readySelectionKey.get())
                        + " autoRolledExpiry=" + quote(autoRolledExpiry)
                        + " hoursSinceLastRollover=" + hoursSinceRoll
                        + " activeSessions=" + activeSessions
                        + " attachedSockets=" + attachedSockets
                        + " liveRecordsPolled=" + polled
                        + " liveRecordsEligibleForActiveSelection=" + eligible
                        + " forwardedEventsTotal=" + forwardedNow
                        + " droppedByStaleness=" + droppedByStaleness.get()
                        + " droppedByCacheGate=" + droppedByCacheGate.get()
                        + " droppedByOtherReasons=" + droppedByOtherReasons.get()
                        + " nowMs=" + nowMs);
            }
        } catch (RuntimeException e) {
            // Instrumentation must never wedge its own thread; log and move on.
            System.err.println("RGW_DIAG_ERROR event=diagnostics_dump_failed message=" + quote(e.getMessage()));
        }
    }

    /**
     * Rollover WARN. Emits ONE structured WARN line at every session-boundary transition (the daily
     * AUTO expiry roll or any other {@link #applySelection} that supersedes the previous selection),
     * capturing before/after flag state. Called from inside the {@link #applySelection} readyLock, so
     * the flag snapshot is consistent with the swap. Additive: never gates the roll.
     */
    private void emitRolloverWarn(ActiveSelection previous, ActiveSelection next) {
        long nowMs = System.currentTimeMillis();
        rolloverCount.incrementAndGet();
        lastRolloverAtMs.set(nowMs);
        lastRolloverFrom.set(describeSelection(previous));
        lastRolloverTo.set(describeSelection(next));
        System.err.println("RGW_ROLLOVER event=rollover_transition"
                + " fromSelection=" + describeSelection(previous)
                + " toSelection=" + describeSelection(next)
                + " avroCaughtUp=" + avroCaughtUp.get()
                + " stateCaughtUp=" + stateCaughtUp.get()
                + " hpsfCaughtUp=" + hpsfCaughtUp.get()
                + " readySelectionKey=" + quote(readySelectionKey.get())
                + " autoRolledExpiry=" + quote(autoRolledExpiry)
                + " activeSessions=" + activeSessionsCount()
                + " connectedClients=" + clients.size()
                + " forwardedEventsTotal=" + forwardedEvents.get()
                + " liveRecordsPolled=" + liveRecordsPolled.get()
                + " rolloverCount=" + rolloverCount.get()
                + " nowMs=" + nowMs);
    }

    private int activeSessionsCount() {
        // In per-session/routing mode use the routing engine's session count; else fall back to the
        // legacy connected-client count. Either way a positive value means "someone is waiting for
        // data" and a zero-forward cycle is suspicious.
        try {
            if (routingEngine != null) {
                java.lang.reflect.Method m = routingEngine.getClass().getMethod("activeAppSessions");
                Object result = m.invoke(routingEngine);
                if (result instanceof Number number) {
                    return number.intValue();
                }
                if (result instanceof java.util.Collection<?> coll) {
                    return coll.size();
                }
                if (result instanceof java.util.Map<?, ?> map) {
                    return map.size();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall through to client-count fallback.
        }
        return clients.size();
    }

    /**
     * Count of currently-attached WebSockets (per {@code SessionRoutingEngine#attachedSocketCount}),
     * excluding AppSessions that persist in the grace window after their socket detached. Used to
     * gate the GATEWAY_FORWARD_STALLED_DURING_MARKET_HOURS alert so we don't cry wolf when there's
     * no real user waiting for data. Falls back to {@code clients.size()} if the routing engine is
     * absent or the method is not present on the linked class version.
     */
    private long attachedSocketCount() {
        try {
            if (routingEngine != null) {
                java.lang.reflect.Method m = routingEngine.getClass().getMethod("attachedSocketCount");
                Object result = m.invoke(routingEngine);
                if (result instanceof Number number) {
                    return number.longValue();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall through to client-count fallback.
        }
        return clients.size();
    }

    private static String describeSelection(ActiveSelection selection) {
        if (selection == null) {
            return "null";
        }
        return "\"" + escapeJson(String.valueOf(selection.source()))
                + "|" + escapeJson(String.valueOf(selection.symbol()))
                + "|" + escapeJson(String.valueOf(selection.expiry()))
                + "|epoch=" + selection.selectionEpoch() + "\"";
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "null" : escapeJson(value)) + "\"";
    }

    // Package-private test seams for the diagnostics unit tests.
    void setDiagnosticsEnabledForTest(boolean enabled) {
        this.diagnosticsEnabled = enabled;
    }

    long forwardStalledAlertsForTest() {
        return forwardStalledAlerts.get();
    }

    long rolloverCountForTest() {
        return rolloverCount.get();
    }

    void applySelectionForTest(String source, String symbol, String expiry, long epoch) {
        long now = System.currentTimeMillis();
        applySelection(new ActiveSelection(source, symbol, expiry, epoch, now));
    }

    /**
     * Test seam for the lag-skip guard: binds every supplied partition's topic to {@code source}/{@code
     * event} and reports whether the lag response actually fired. Exists because {@code TopicBinding} is
     * private and the guard is only reachable through it.
     */
    boolean lagSkipFiredForTest(KafkaConsumer<?, ?> consumer, List<TopicPartition> partitions,
                                String source, String event, Set<TopicPartition> bootstrapping) {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        for (TopicPartition partition : partitions) {
            topicEvents.put(partition.topic(), new TopicBinding(source, event));
        }
        long before = seekToLatestEvents.get();
        maybeSeekSelectedSourceToLatest(consumer, partitions, topicEvents, bootstrapping);
        return seekToLatestEvents.get() > before;
    }

    /** Test seam: register a partition as mid-run bootstrapping, exactly as a refresh would. */
    void registerBootstrapForTest(TopicPartition partition, String source, long barrier, long sinceMs) {
        bootstrappingPartitions.put(partition, new BootstrapState(source, barrier, sinceMs, "test"));
    }

    void supersedeBootstrapEntriesForTest(String owner, Map<TopicPartition, Long> freshEndOffsets,
                                          String topic, String source) {
        supersedeBootstrapEntries(owner, freshEndOffsets,
                Map.of(topic, new TopicBinding(source, "snapshot")));
    }

    /** Test seam: run one retirement pass and report which partitions remain exempt. */
    Set<TopicPartition> retireReachedBootstrapForTest(
            List<TopicPartition> owned,
            java.util.function.ToLongFunction<TopicPartition> position) {
        clearReachedBootstrapBarriers(bootstrappingPartitions, owned, position);
        return Set.copyOf(bootstrappingPartitions.keySet());
    }

    boolean hasIncompleteBootstrapForSourceForTest(String source) {
        return hasIncompleteBootstrapForSource(source);
    }

    long activeSelectionEpochForTest() {
        ActiveSelection selection = activeSelection.get();
        return selection == null ? -1L : selection.selectionEpoch();
    }

    void bumpLiveRecordsPolledForTest(long by) {
        liveRecordsPolled.addAndGet(by);
    }

    // Codex round-4 P2 test seam: bump the eligible counter used by the stall gate.
    void bumpLiveRecordsEligibleForActiveSelectionForTest(long by) {
        liveRecordsEligibleForActiveSelection.addAndGet(by);
    }

    long droppedByStalenessForTest() {
        return droppedByStaleness.get();
    }

    long droppedByOtherReasonsForTest() {
        return droppedByOtherReasons.get();
    }

    // Codex round-4 P3 test seam: exercise the drop-bucket helper without needing to spin up a live
    // Kafka consumer. Test provides the ingredients (source/event/json + cache flag + active selection)
    // and asserts on {droppedByStaleness, droppedByOtherReasons, droppedByCacheGate}.
    void recordDropBucketForTest(String bindingSource, String bindingEvent, String json,
                                 boolean cacheCaughtUp,
                                 String selectionSource, String selectionSymbol, String selectionExpiry,
                                 long selectionEpoch) {
        TopicBinding binding = new TopicBinding(bindingSource, bindingEvent);
        ActiveSelection selection = selectionSource == null
                ? null
                : new ActiveSelection(selectionSource, selectionSymbol, selectionExpiry, selectionEpoch,
                        System.currentTimeMillis());
        recordDropBucket(binding, json, cacheCaughtUp, selection);
    }

    void bumpForwardedEventsForTest(long by) {
        forwardedEvents.addAndGet(by);
    }

    /**
     * Test seam: drive a strike-flow record through the REAL {@link #updateCache} path (which
     * source-prefixes the cache key), so a test can prove {@link #cachedSellerActivitySnapshot} resolves the
     * SAME key updateCache writes — the integration gap that a mocked accessor conceals. Returns the
     * cache key updateCache produced.
     */
    String cacheStrikeFlowForTest(String bindingSource, String recordKey, String json, long recordTimestampMs) {
        TopicBinding binding = new TopicBinding(bindingSource, "strike-flow");
        // strike-flow freshness tracks the Kafka record timestamp (eventCacheTimestamp default), so a test
        // must set it explicitly to exercise the isCacheFresh gate.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "test-strike-flow", 0, 0L, recordTimestampMs,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, recordKey, json,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
        return updateCache(binding, record, json);
    }

    String cacheSellerActivityForTest(String bindingSource, String recordKey, String json, long recordTimestampMs) {
        TopicBinding binding = new TopicBinding(bindingSource, "seller-activity");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "test-seller-activity", 0, 0L, recordTimestampMs,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, recordKey, json,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
        return updateCache(binding, record, json);
    }

    void invokeRolloverWarnForTest(String fromSource, String toSource) {
        long now = System.currentTimeMillis();
        emitRolloverWarn(
                new ActiveSelection(fromSource, "SPX", "20260701", 1L, now - 1000L),
                new ActiveSelection(toSource, "SPX", "20260702", 2L, now));
    }

    // Codex round-2 P2a test seam: drive the applySelection-without-reset path so a test can verify
    // the readySelectionKey gauge flips OFF on rollover even though the field itself is intentionally
    // NOT cleared. Returns 1 iff readySelectionKey matches the current activeSelection's key.
    int readySelectionKeyGaugeForTest() {
        return boolMetric(readySelectionKeyMatchesActive(activeSelection.get()));
    }

    void seedReadySelectionForTest(String source, String symbol, String expiry, long epoch) {
        long now = System.currentTimeMillis();
        ActiveSelection sel = new ActiveSelection(source, symbol, expiry, epoch, now);
        activeSelection.set(sel);
        readySelectionKey.set(selectionKey(sel));
    }

    // Codex round-2 P2a: swap the active selection WITHOUT clearing readySelectionKey (mirrors the
    // real applySelection contract). Used to verify the readySelectionKey gauge flips to 0 when the
    // stored key belongs to the PREVIOUS selection.
    void swapActiveSelectionForTest(String source, String symbol, String expiry, long epoch) {
        long now = System.currentTimeMillis();
        activeSelection.set(new ActiveSelection(source, symbol, expiry, epoch, now));
    }
}
