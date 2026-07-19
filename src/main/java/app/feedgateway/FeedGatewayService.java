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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
            "snapshot", "pace", "pace-rank", "directional-pressure", "strike-flow", "delta-flow", "strike-intel", "strike-invasion", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "gex-by-strike",
            "strike-sr",
            "gex-magnet",
            "max-pain",
            "liquidity-heatmap",
            "option-price-behavior",
            "dealer-ledger",
            "zero-dte-intelligence",
            "greek-move-auth",
            "opb-v2-by-option", "opb-v2-session",
            "index-price", "vix-price", "hpsf-latest-signal", "hpsf-market-flow", "hpsf-top-candidates",
            "hpsf-audit", "hpsf-exit-intent");
    // Clock-skew allowance for the greek-move-authenticity verdict's PAYLOAD decision time
    // (asOfEventTimeMs, a past market observation): a verdict stamped more than this far ahead of the
    // gateway wall clock is treated as malformed and fails closed, so a bad future timestamp can neither
    // evade the freshness window nor poison the monotonic last-value-wins supersede gate. Generous enough
    // to absorb realistic inter-service NTP skew; tight enough that a poisoned record can never freeze a
    // symbol's track for more than this bound.
    private static final long GREEK_MOVE_AUTH_MAX_FUTURE_SKEW_MS = 60_000L;
    private final SessionRoutingEngine routingEngine;
    private final Map<String, String> snapshots = new ConcurrentHashMap<>();
    private final Map<String, String> paces = new ConcurrentHashMap<>();
    private final Map<String, String> paceRanks = new ConcurrentHashMap<>(); // board-level pace ranking, keyed by boardKey
    private final Map<String, String> directionalPressures = new ConcurrentHashMap<>();
    private final Map<String, String> strikeFlows = new ConcurrentHashMap<>();
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
    private final Map<String, String> currentStates = new ConcurrentHashMap<>();
    private final Map<String, String> gexByStrike = new ConcurrentHashMap<>();
    private final Map<String, String> strikeSr = new ConcurrentHashMap<>();
    private final Map<String, String> gexMagnet = new ConcurrentHashMap<>();
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
    private final Map<String, String> opbV2ByOptions = new ConcurrentHashMap<>();
    private final Map<String, String> opbV2Sessions = new ConcurrentHashMap<>();
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
    private final Map<String, String> pendingDeltaFlows = new LinkedHashMap<>();
    private final Map<String, String> pendingStrikeIntels = new LinkedHashMap<>();
    private final Map<String, String> pendingStrikeInvasions = new LinkedHashMap<>();
    private final Map<String, String> pendingLiquidityHeatmaps = new LinkedHashMap<>();
    private final Map<String, String> pendingMissionPaces = new LinkedHashMap<>();
    private final Map<String, String> pendingMissionControls = new LinkedHashMap<>();
    private final Map<String, String> pendingSpreadSkews = new LinkedHashMap<>();
    private final Map<String, String> pendingIndexPrices = new LinkedHashMap<>();
    private final Map<String, String> pendingVolumeSandwiches = new LinkedHashMap<>();
    private final Map<String, String> pendingMissionSandwiches = new LinkedHashMap<>();
    private final Map<String, String> pendingGexByStrike = new LinkedHashMap<>();
    private final Map<String, String> pendingStrikeSr = new LinkedHashMap<>();
    private final Map<String, String> pendingGexMagnet = new LinkedHashMap<>();
    private final Map<String, String> pendingMaxPain = new LinkedHashMap<>();
    private final Map<String, String> pendingOptionPriceBehaviors = new LinkedHashMap<>();
    private final Map<String, String> pendingOpbV2ByOptions = new LinkedHashMap<>();
    private final Map<String, String> pendingOpbV2Sessions = new LinkedHashMap<>();
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
    private final AtomicLong inactiveDroppedEvents = new AtomicLong();
    private final AtomicLong droppedNonRoutableEvents = new AtomicLong();
    private final AtomicLong staleDroppedEvents = new AtomicLong();
    private final AtomicLong seekToLatestEvents = new AtomicLong();
    private final AtomicLong sourceStaleEvents = new AtomicLong();
    private final AtomicLong lastSourceStaleBroadcastMs = new AtomicLong();
    private final AtomicLong lastLagCheckMs = new AtomicLong();
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
        // In per-session mode the GLOBAL cached replay is replaced by a PER-SESSION filtered replay:
        // each socket gets only the cached state matching its own AppSession selection (no cross-
        // contract leak), then live routed data (FR-11).
        if (perSessionRouting()) {
            replayCachedToSocket(session);
            // short-premium is a GLOBAL advisory (symbol-filtered client-side), not per-session
            // routed — replayCacheMap can't deliver it (no GatewayRecordMapper case), so replay it
            // the same standalone way legacy mode does, so an auth-mode reload restores the overlay.
            replayShortPremiumCached(session);
            replayZeroDteIntelligenceCached(session);
            return;
        }
        if (avroCaughtUp.get()) {
            // max-pain + strike-sr are DATABENTO-only Avro, so they bootstrap purely via the Avro consumer.
            sendCachedState(session, List.of("snapshot", "pace", "pace-rank", "directional-pressure", "max-pain", "strike-sr", "gex-magnet"));
        }
        if (stateCaughtUp.get()) {
            sendCachedState(session, List.of("vix-price", "index-price", "strike-flow", "delta-flow", "strike-intel", "strike-invasion", "liquidity-heatmap", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "option-price-behavior", "opb-v2-by-option", "opb-v2-session"));
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
            replayZeroDteIntelligenceCached(session);
        }
        // gex-by-strike is the one MULTI-SOURCE cache: IBKR/Unusual-Whales gex arrives via the JSON state
        // consumer while DATABENTO gex arrives via the Avro consumer. Its cached replay is only complete once
        // BOTH have caught up, so gate it on both flags (avoids a first-send that omits one source's gex).
        if (avroCaughtUp.get() && stateCaughtUp.get()) {
            sendCachedState(session, List.of("gex-by-strike"));
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
                + "\"deltaFlows\":" + deltaFlows.size() + ","
                + "\"strikeIntels\":" + strikeIntels.size() + ","
                + "\"liquidityHeatmaps\":" + liquidityHeatmaps.size() + ","
                + "\"missionPaces\":" + missionPaces.size() + ","
                + "\"missionControls\":" + missionControls.size() + ","
                + "\"spreadSkews\":" + spreadSkews.size() + ","
                + "\"indexPrices\":" + indexPrices.size() + ","
                + "\"vixPrices\":" + vixPrices.size() + ","
                + "\"currentStates\":" + currentStates.size() + ","
                + "\"gexByStrike\":" + gexByStrike.size() + ","
                + "\"strikeSr\":" + strikeSr.size() + ","
                + "\"gexMagnet\":" + gexMagnet.size() + ","
                + "\"maxPain\":" + maxPain.size() + ","
                + "\"optionPriceBehaviors\":" + optionPriceBehaviors.size() + ","
                + "\"opbV2ByOptions\":" + opbV2ByOptions.size() + ","
                + "\"opbV2Sessions\":" + opbV2Sessions.size() + ","
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
                + "# HELP options_edge_feed_gateway_delta_flows Cached delta-flow count.\n"
                + "# TYPE options_edge_feed_gateway_delta_flows gauge\n"
                + "options_edge_feed_gateway_delta_flows " + deltaFlows.size() + "\n"
                + "# HELP options_edge_feed_gateway_strike_intels Cached strike-intel count.\n"
                + "# TYPE options_edge_feed_gateway_strike_intels gauge\n"
                + "options_edge_feed_gateway_strike_intels " + strikeIntels.size() + "\n"
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
                + "# HELP options_edge_feed_gateway_max_pain Cached per-(symbol,expiry) max-pain count.\n"
                + "# TYPE options_edge_feed_gateway_max_pain gauge\n"
                + "options_edge_feed_gateway_max_pain " + maxPain.size() + "\n"
                + "# HELP options_edge_feed_gateway_option_price_behaviors Cached option price behavior dashboard count.\n"
                + "# TYPE options_edge_feed_gateway_option_price_behaviors gauge\n"
                + "options_edge_feed_gateway_option_price_behaviors " + optionPriceBehaviors.size() + "\n"
                + "# HELP options_edge_feed_gateway_opb_v2_by_options Cached OPB V2 by-option count.\n"
                + "# TYPE options_edge_feed_gateway_opb_v2_by_options gauge\n"
                + "options_edge_feed_gateway_opb_v2_by_options " + opbV2ByOptions.size() + "\n"
                + "# HELP options_edge_feed_gateway_opb_v2_sessions Cached OPB V2 session count.\n"
                + "# TYPE options_edge_feed_gateway_opb_v2_sessions gauge\n"
                + "options_edge_feed_gateway_opb_v2_sessions " + opbV2Sessions.size() + "\n"
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
            List<TopicPartition> partitions = partitionsFor(consumer, Set.of(settings.marketDataSelectionTopic()));
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
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
        runAssignedCacheConsumer("avro", topicEvents, true, avroCaughtUp);
    }

    private void runJsonStateCacheConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrVixPriceTopic(), new TopicBinding("IBKR", "vix-price"));
        topicEvents.put(settings.databentoEsTradesTopic(), new TopicBinding("DATABENTO", "index-price"));
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
        topicEvents.put(settings.databentoStrikeFlowTopic(), new TopicBinding("DATABENTO", "strike-flow"));
        // delta-flow-by-strike is plain JSON (DeltaFlowStrikeSnapshot), per-strike keyed
        // (symbol|date|expiry|strike) — this JSON-state consumer, never the Avro one.
        topicEvents.put(settings.databentoDeltaFlowByStrikeTopic(), new TopicBinding("DATABENTO", "delta-flow"));
        // strike-intelligence-by-strike is plain JSON (StrikeIntelligenceSignal), per-strike keyed
        // (symbol|expiry|strike) — this JSON-state consumer, never the Avro one (mirrors delta-flow).
        topicEvents.put(settings.strikeIntelByStrikeTopic(), new TopicBinding("DATABENTO", "strike-intel"));
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
        topicEvents.put(settings.optionPriceBehaviorDashboardTopic(), new TopicBinding("DATABENTO", "option-price-behavior"));
        topicEvents.put(settings.optionPriceBehaviorV2ByOptionTopic(), new TopicBinding("DATABENTO", "opb-v2-by-option"));
        topicEvents.put(settings.optionPriceBehaviorV2SessionTopic(), new TopicBinding("DATABENTO", "opb-v2-session"));
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
        runLiveConsumer("avro-live", topicEvents, true, avroCaughtUp);
    }

    private void runJsonStateLiveConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrVixPriceTopic(), new TopicBinding("IBKR", "vix-price"));
        topicEvents.put(settings.databentoEsTradesTopic(), new TopicBinding("DATABENTO", "index-price"));
        topicEvents.put(settings.ibkrVolumeSandwichTopic(), new TopicBinding("IBKR", "volume-sandwich"));
        topicEvents.put(settings.databentoVolumeSandwichTopic(), new TopicBinding("DATABENTO", "volume-sandwich"));
        topicEvents.put(settings.databentoMissionSandwichTopic(), new TopicBinding("DATABENTO", "mission-sandwich"));
        topicEvents.put(settings.ibkrUnusualWhalesGexTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        topicEvents.put(settings.ibkrUnusualWhalesGexHistoryTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        // DATABENTO gex + max-pain are Avro — live-consumed by runAvroLiveConsumer, not here. The
        // DATABENTO gex HISTORY topic IS JSON, so it lives here (keep the cache + live JSON consumer
        // topic sets symmetric, exactly as the UW gex/gex-history pair and the databento-gex Avro pair).
        topicEvents.put(settings.databentoGexHistoryTopic(), new TopicBinding("DATABENTO", "gex-by-strike"));
        topicEvents.put(settings.databentoStrikeFlowTopic(), new TopicBinding("DATABENTO", "strike-flow"));
        // delta-flow-by-strike is plain JSON, per-strike keyed — keep the cache + live JSON consumer
        // topic sets symmetric (same rule as gex-history/strike-flow above).
        topicEvents.put(settings.databentoDeltaFlowByStrikeTopic(), new TopicBinding("DATABENTO", "delta-flow"));
        // strike-intelligence-by-strike is plain JSON, per-strike keyed — keep the cache + live JSON
        // consumer topic sets symmetric (same rule as delta-flow above).
        topicEvents.put(settings.strikeIntelByStrikeTopic(), new TopicBinding("DATABENTO", "strike-intel"));
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
        // Keep the cache + live JSON consumer topic sets symmetric (same rule as gex-history).
        topicEvents.put(settings.strikeLiquidityTopic(), new TopicBinding("DATABENTO", "liquidity-heatmap"));
        topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding("DATABENTO", "mission-pace"));
        topicEvents.put(settings.missionControlTopic(), new TopicBinding("DATABENTO", "mission-control"));
        // spread-skew.current is plain JSON (single record keyed "SPX") — keep the cache + live JSON
        // consumer topic sets symmetric (same rule as mission-control above).
        topicEvents.put(settings.spreadSkewTopic(), new TopicBinding("DATABENTO", "spread-skew"));
        // spread-skew.events: discrete FIRE/EXIT/REVERSAL/RESTART transitions, broadcast STANDALONE (never cached).
        topicEvents.put(settings.spreadSkewEventsTopic(), new TopicBinding("DATABENTO", "spread-skew-event"));
        topicEvents.put(settings.optionPriceBehaviorDashboardTopic(), new TopicBinding("DATABENTO", "option-price-behavior"));
        topicEvents.put(settings.optionPriceBehaviorV2ByOptionTopic(), new TopicBinding("DATABENTO", "opb-v2-by-option"));
        topicEvents.put(settings.optionPriceBehaviorV2SessionTopic(), new TopicBinding("DATABENTO", "opb-v2-session"));
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
        topicEvents.put(settings.vixOptionInteligenceTopic(), new TopicBinding("DATABENTO", "zero-dte-intelligence"));
        runLiveConsumer("state-live", topicEvents, false, stateCaughtUp);
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
            List<TopicPartition> partitions = partitionsFor(consumer, hpsfTopics());
            consumer.assign(partitions);
            seekToCacheWindow(consumer, partitions);
            Map<TopicPartition, Long> bootstrapEndOffsets = consumer.endOffsets(partitions);
            boolean live = caughtUp(consumer, bootstrapEndOffsets);
            if (live) {
                markCacheCaughtUp("hpsf", hpsfEvents(), hpsfCaughtUp);
            }
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
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
            List<TopicPartition> partitions = partitionsFor(consumer, hpsfTopics());
            consumer.assign(partitions);
            if (retry) {
                seekToCacheWindow(consumer, partitions);
            } else {
                consumer.seekToEnd(partitions);
            }
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
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
            List<TopicPartition> partitions = partitionsFor(consumer, topicEvents.keySet());
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
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

    private void runAssignedCacheConsumerOnce(String name, Map<String, TopicBinding> topicEvents, boolean avro, AtomicBoolean caughtUpFlag) {
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(avro ? avroConsumerProperties(name) : stringObjectConsumerProperties(name))) {
            List<TopicPartition> partitions = partitionsFor(consumer, topicEvents.keySet());
            consumer.assign(partitions);
            seekToCacheWindow(consumer, partitions, topicEvents);
            Map<TopicPartition, Long> bootstrapEndOffsets = consumer.endOffsets(partitions);
            Map<TopicPartition, Long> catchUpEndOffsets = catchUpEndOffsets(bootstrapEndOffsets, topicEvents);
            List<String> events = topicEvents.values().stream().map(TopicBinding::event).distinct().toList();
            boolean live = caughtUp(consumer, catchUpEndOffsets);
            if (live) {
                markCacheCaughtUp(name, events, caughtUpFlag);
            }
            while (running.get()) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                maybeSeekSelectedSourceToLatest(consumer, partitions, topicEvents);
                for (ConsumerRecord<String, Object> record : records) {
                    TopicBinding binding = topicEvents.get(record.topic());
                    String json = enrichJson(avro ? avroJson(record.value()) : stringJson(record.value()), binding);
                    if (binding == null || json == null || json.isBlank()) {
                        evictStrikeSrTombstone(binding, record);
                        continue;
                    }
                    updateCache(binding, record, json);
                }
                purgeExpiredCache(System.currentTimeMillis());
                if (!live && caughtUp(consumer, catchUpEndOffsets)) {
                    live = true;
                    markCacheCaughtUp(name, events, caughtUpFlag);
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
            List<TopicPartition> partitions = partitionsFor(consumer, topicEvents.keySet());
            consumer.assign(partitions);
            if (retry) {
                seekToCacheWindow(consumer, partitions, topicEvents);
            } else {
                consumer.seekToEnd(partitions);
            }
            while (running.get()) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
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
                    String json = enrichJson(avro ? avroJson(record.value()) : stringJson(record.value()), binding);
                    if (binding == null || json == null || json.isBlank()) {
                        evictStrikeSrTombstone(binding, record);
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
                    String cacheKey = updateCache(binding, record, json);
                    // Dealer-ledger forwards the JOINED envelope (not the raw profile/state record); for
                    // every other event forwardJson is just `json`, so the block below is unchanged.
                    String forwardJson = "dealer-ledger".equals(binding.event())
                            ? (cacheKey == null ? null : dealerLedgers.get(cacheKey))
                            : json;
                    if ("dealer-ledger".equals(binding.event()) && (forwardJson == null || forwardJson.isBlank())) {
                        continue; // join not ready / stale-dropped — nothing to forward for this record
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
                        // Strike-intel fail-closed (freshness): a per-strike signal carries no per-session
                        // selectionEpoch (epoch 0 bypasses passesBarrier), so add an explicit maxStale gate on
                        // the PAYLOAD event time — a backfilled/catching-up producer must never live-route a
                        // stale action onto a strike. Its useful life is the generic cache window (the
                        // delta-flow sibling shares the same 15-min cachePolicy); the cached-replay path
                        // already gates the same way via isCacheFresh("strike-intel:...").
                        boolean strikeIntelStale = "strike-intel".equals(binding.event())
                                && eventCacheTimestamp(binding.event(), record, json)
                                < System.currentTimeMillis() - settings.cacheTtlMs();
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
                        if ((cacheKey != null || !"max-pain".equals(binding.event()))
                                && !missionPaceStale && !missionControlStale && !liquidityHeatmapStale
                                && !strikeIntelStale && !strikeInvasionStale && !spreadSkewStale
                                && !esOpenDirectionDropped) {
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
                    return !"max-pain".equals(binding.event()) && !isEsOpenDirectionEvent(binding.event());
                })
                .toList();
        if (selectedPartitions.isEmpty()) {
            return;
        }
        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(selectedPartitions);
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
     * Topics whose producers may legitimately be absent (graceful-absence contract). Every other
     * topic in a consumer set is mandatory and keeps the strict metadata requirement.
     */
    private boolean isOptionalTopic(String topic) {
        // OPTIONAL: absence must never block/crash-loop the SHARED consumer. The dealer-ledger topics
        // are produced by a separate service that may not be deployed (and, since Kafka is wiped and
        // services restart each day, are simply absent until that service first produces) — treat them
        // as optional so their absence can't starve strike-flow / mission-pace / the other JSON feeds.
        return topic != null
                && (topic.equals(settings.strikeLiquidityTopic())
                    || topic.equals(settings.dealerLedgerProfileTopic())
                    || topic.equals(settings.dealerLedgerStateTopic())
                    || topic.equals(settings.strikeIntelByStrikeTopic())
                    || topic.equals(settings.strikeInvasionTopic())
                    // Both spread-skew topics come from the same brand-new spread-skew-service, which may
                    // not be deployed during a staged rollout — BOTH must be optional (like strike-invasion)
                    // so their absence cannot starve the shared JSON consumer.
                    || topic.equals(settings.spreadSkewTopic())
                    || topic.equals(settings.spreadSkewEventsTopic())
                    || topic.equals(settings.shortPremiumRecommendationTopic())
                    // ES open-direction forecast/outcome producer is a brand-new service that may not be
                    // deployed (and the topics are absent after the daily Kafka wipe until it first
                    // produces) — optional, so their absence can never starve the shared JSON consumer.
                    || topic.equals(settings.esOpenDirectionForecastTopic())
                    || topic.equals(settings.esOpenDirectionOutcomeTopic())
                    // The live STATUS heartbeat comes from the same may-not-be-deployed producer, and is
                    // additionally absent whenever no overnight session is active — optional like its siblings.
                    || topic.equals(settings.esOpenDirectionStatusTopic())
                    // Greek-move-authenticity is a brand-new standalone service that may not be deployed (and
                    // the topic is absent after the daily Kafka wipe until it first produces) — optional, so
                    // its absence can never starve the shared JSON consumer.
                    || topic.equals(settings.greekMoveAuthCurrentTopic())
                    || topic.equals(settings.vixOptionInteligenceTopic()));
    }

    private void markCacheRecovering(AtomicBoolean caughtUpFlag) {
        if (caughtUpFlag.getAndSet(false)) {
            broadcast("status", statusJson());
        }
    }

    private List<TopicPartition> partitionsFor(KafkaConsumer<?, ?> consumer, Set<String> topics) {
        long deadlineMs = System.currentTimeMillis() + settings.metadataTimeoutMs();
        Map<String, List<PartitionInfo>> metadata = new HashMap<>();
        while (running.get() && System.currentTimeMillis() < deadlineMs) {
            metadata.clear();
            boolean complete = true;
            for (String topic : topics) {
                List<PartitionInfo> partitions = consumer.partitionsFor(topic, Duration.ofMillis(1_000));
                if (partitions == null || partitions.isEmpty()) {
                    if (isOptionalTopic(topic)) {
                        // OPTIONAL topic (producer not deployed yet / absent): skip it so its
                        // absence can NEVER block or crash-loop the shared consumer and starve
                        // the mandatory feeds. It is picked up when the consumer restarts after
                        // the producer's topic exists (gateway redeploy/restart).
                        System.err.println("Feed gateway: optional topic absent, consuming without it: " + topic);
                        continue;
                    }
                    complete = false;
                    break;
                }
                metadata.put(topic, partitions);
            }
            if (complete) {
                List<TopicPartition> result = new ArrayList<>();
                for (Map.Entry<String, List<PartitionInfo>> entry : metadata.entrySet()) {
                    for (PartitionInfo partition : entry.getValue()) {
                        result.add(new TopicPartition(entry.getKey(), partition.partition()));
                    }
                }
                result.sort(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition));
                return result;
            }
        }
        throw new IllegalStateException("Timed out waiting for Kafka topic metadata: " + topics);
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
            if (requiresCatchUpForActiveSource(selection.source(), binding.source())) {
                selectedEndOffsets.put(entry.getKey(), entry.getValue());
            }
        }
        return selectedEndOffsets.isEmpty() ? endOffsets : selectedEndOffsets;
    }

    static boolean requiresCatchUpForActiveSource(String activeSource, String bindingSource) {
        return activeSource != null && activeSource.equals(bindingSource);
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
            if (broadcastCachedState(sourceSwitchReplayEvents())) {
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
            List<TopicPartition> partitions = partitionsFor(consumer, Set.copyOf(topics));
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
                    settings.databentoStrikeFlowTopic(),
                    settings.databentoDeltaFlowByStrikeTopic(),
                    settings.strikeIntelByStrikeTopic(),
                    settings.strikeInvasionTopic(),
                    settings.strikeLiquidityTopic(),
                    settings.databentoPaceMissionTopic(),
                    settings.missionControlTopic(),
                    settings.spreadSkewTopic(),
                    settings.optionPriceBehaviorDashboardTopic(),
                    settings.optionPriceBehaviorV2ByOptionTopic(),
                    settings.optionPriceBehaviorV2SessionTopic(),
                    settings.databentoGexTopic(),
                    settings.unifiedSrTopic(),
                    settings.databentoGexMagnetTopic(),
                    settings.databentoMaxPainTopic(),
                    settings.databentoVolumeSandwichTopic(),
                    settings.databentoVolumeSandwichAlertsTopic(),
                    settings.databentoMissionSandwichTopic()
            );
        }
        return List.of();
    }

    static List<String> sourceSwitchReplayEvents() {
        // NB: dealer-ledger is intentionally ABSENT — it is delivered standalone (not via the ui-batch
        // this list feeds). After a source switch it self-heals from the next live dealer-ledger record.
        return List.of("snapshot", "pace", "pace-rank", "directional-pressure", "vix-price", "index-price", "strike-flow", "delta-flow", "strike-intel", "strike-invasion", "liquidity-heatmap", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "option-price-behavior", "opb-v2-by-option", "opb-v2-session", "gex-by-strike", "strike-sr", "gex-magnet", "max-pain");
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
            return "DATABENTO".equals(selection.source()) && passesSelectionBarrier(record, selection);
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
        if ("opb-v2-session".equals(binding.event())) {
            return binding.source().equals(selection.source())
                    && passesSelectionTimeBarrier(cacheTimestamp(record), selection)
                    && matchesOptionPriceBehaviorSelection(json, selection);
        }
        // opb-v2-by-option is a normal per-contract signal (symbol|expiry|strike) — fall through to the
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
            readySelectionKey.set(key);
            // Announce readiness FIRST, then converge every open dashboard onto the new selection's cached
            // strikes. Without this re-push, a tab that missed the live seed batch right after
            // "source-switching" — e.g. the daily post-close expiry roll, when no further live ticks arrive —
            // would stay blank until a manual refresh or the next market open.
            broadcast("source-ready", activeSelectionJson(selection, "source-ready"));
            broadcastCachedState(sourceSwitchReplayEvents());
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
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException ignored) {
            return json;
        }
    }

    private synchronized String updateCache(TopicBinding binding, ConsumerRecord<String, ?> record, String json) {
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
        } else if ("vix-price".equals(event) || "index-price".equals(event)) {
            key = indexPriceCacheKey(json, key);
        } else if ("strike-flow".equals(event)) {
            key = strikeFlowCacheKey(json, key);
        } else if ("delta-flow".equals(event)) {
            key = deltaFlowCacheKey(json, key);
        } else if ("strike-intel".equals(event)) {
            key = strikeIntelCacheKey(json, key);
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
        } else if ("max-pain".equals(event)) {
            key = maxPainCacheKey(json, key);
        } else if ("option-price-behavior".equals(event)) {
            key = optionPriceBehaviorCacheKey(json, key);
        } else if ("dealer-ledger".equals(event)) {
            // Role-qualified (…|PROFILE / …|STATE) so the two source topics keep SEPARATE monotonic
            // event-time gates — otherwise a profile could be false-dropped by a newer state (or vice
            // versa). The join + envelope cache use the role-stripped base key (dealerLedgerBaseKey).
            key = dealerLedgerCacheKey(json, record, key);
        } else if ("opb-v2-by-option".equals(event)) {
            key = opbV2ByOptionCacheKey(json, key);
        } else if ("opb-v2-session".equals(event)) {
            key = opbV2SessionCacheKey(json, key);
        }
        if (!"pace".equals(event) && !"pace-rank".equals(event)) {
            // pace-rank's record key is already the epoch-qualified boardKey (includes source) — don't re-prefix.
            key = binding.source() + "|" + key;
        }
        String versionKey = event + ":" + key;
        long eventTime = eventCacheTimestamp(event, record, json);
        Long previousEventTime = cacheEventTimes.get(versionKey);
        // Terminal max-pain MUST always reach the EXPIRED branch (eviction + return key for the
        // one-time live forward) regardless of timestamp ordering. Without this, an EXPIRED record
        // with an older Kafka timestamp would be silently dropped before the UI sees the transition.
        boolean isTerminalMaxPainShortCircuitBypass = "max-pain".equals(event) && isMaxPainExpired(json);
        if (!isTerminalMaxPainShortCircuitBypass
                && previousEventTime != null && previousEventTime > eventTime) {
            return null;
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
            case "strike-flow" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                strikeFlows.put(key, json);
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
            case "opb-v2-by-option" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                opbV2ByOptions.put(key, json);
                return key;
            }
            case "opb-v2-session" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                opbV2Sessions.put(key, json);
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
                case "opb-v2-by-option" -> opbV2ByOptions.entrySet().stream()
                        .filter(entry -> isCacheFresh("opb-v2-by-option:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("opb-v2-by-option:" + entry.getKey(), selection))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("opb-v2-by-option", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "opb-v2-session" -> opbV2Sessions.entrySet().stream()
                        .filter(entry -> isCacheFresh("opb-v2-session:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("opb-v2-session:" + entry.getKey(), selection, true, false))
                        .filter(entry -> "DATABENTO".equals(selection.source()))
                        .filter(entry -> matchesOptionPriceBehaviorSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("opb-v2-session", entry.getValue()))
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
        return !"snapshot".equals(event) && !"max-pain".equals(event) && !"gex-by-strike".equals(event);
    }

    static boolean enforceCachedReplayOffsetBarrier(String event, String source) {
        // Same rationale as enforceCachedReplayMaxStale: a slow max-pain/GEX latest record can sit below the
        // session's per-partition offset barrier (set when other fast topics advanced past selection), so
        // the offset barrier would wrongly filter the current max-pain/GEX on replay. Exempt like snapshot.
        return !"snapshot".equals(event) && !"max-pain".equals(event) && !"gex-by-strike".equals(event);
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

    private void removeCacheEntry(String versionKey) {
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
        } else if (versionKey.startsWith("zero-dte-intelligence:")) {
            zeroDteIntelligence.remove(versionKey.substring("zero-dte-intelligence:".length()));
        } else if (versionKey.startsWith("strike-flow:")) {
            strikeFlows.remove(versionKey.substring("strike-flow:".length()));
        } else if (versionKey.startsWith("delta-flow:")) {
            deltaFlows.remove(versionKey.substring("delta-flow:".length()));
        } else if (versionKey.startsWith("strike-invasion:")) {
            strikeInvasions.remove(versionKey.substring("strike-invasion:".length()));
        } else if (versionKey.startsWith("strike-intel:")) {
            strikeIntels.remove(versionKey.substring("strike-intel:".length()));
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
        } else if (versionKey.startsWith("strike-sr:")) {
            strikeSr.remove(versionKey.substring("strike-sr:".length()));
        } else if (versionKey.startsWith("gex-magnet:")) {
            gexMagnet.remove(versionKey.substring("gex-magnet:".length()));
        } else if (versionKey.startsWith("hot-strike:")) {
            hotStrikes.remove(versionKey.substring("hot-strike:".length()));
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
        } else if (versionKey.startsWith("opb-v2-by-option:")) {
            opbV2ByOptions.remove(versionKey.substring("opb-v2-by-option:".length()));
        } else if (versionKey.startsWith("opb-v2-session:")) {
            opbV2Sessions.remove(versionKey.substring("opb-v2-session:".length()));
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
     * Cache key for the per-(symbol,expiry) max-pain stream. The producer key is already
     * {@code symbol|expiry}, but we re-derive it from payload identity to be robust against
     * producer-side key drift. Source is prepended by updateCache so the resulting cache key matches
     * the UI contract (DATABENTO|symbol|expiry).
     */
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

    private String opbV2SessionCacheKey(String json, String fallback) {
        return optionPriceBehaviorCacheKey(json, fallback);
    }

    private String opbV2ByOptionCacheKey(String json, String fallback) {
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
        replayCacheMap(session, "delta-flow", deltaFlows);
        replayCacheMap(session, "strike-intel", strikeIntels);
        replayCacheMap(session, "strike-invasion", strikeInvasions);
        // Mission-level state is low-frequency in replay/off-hours dev. Replay the fresh cached value on
        // connect, still routed by source|symbol|expiry so it cannot leak to another selected market.
        replayCacheMap(session, "mission-pace", missionPaces);
        replayCacheMap(session, "mission-control", missionControls);
        replayCacheMap(session, "spread-skew", spreadSkews);
        replayCacheMap(session, "gex-by-strike", gexByStrike);
        replayCacheMap(session, "strike-sr", strikeSr);
        replayCacheMap(session, "gex-magnet", gexMagnet);
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
        replayCacheMap(session, "opb-v2-by-option", opbV2ByOptions);
        replayCacheMap(session, "opb-v2-session", opbV2Sessions);
        // P1: replay each underlying cache with its ORIGINAL event type — VIX (SHARED) as vix-price, ES/index
        // as index-price — so a VIX record is never delivered mislabelled as index-price.
        replayCacheMap(session, "vix-price", vixPrices);
        replayCacheMap(session, "index-price", indexPrices);
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
    }

    private void replayCacheMap(WebSocketSession session, String event, Map<String, String> cache) {
        if (routingEngine == null) {
            return;
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
                    send(session, event, json);
                }
            } catch (JsonProcessingException ignored) {
                // skip malformed cached entry
            }
        }
    }

    private boolean requiresFreshPerSessionReplay(String event) {
        return "strike-sr".equals(event)
                || "gex-magnet".equals(event)
                || "liquidity-heatmap".equals(event)
                || "mission-pace".equals(event)
                || "mission-control".equals(event)
                || "spread-skew".equals(event);
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
                stringTopics.put(settings.databentoStrikeFlowTopic(), "strike-flow");
                stringTopics.put(settings.databentoDeltaFlowByStrikeTopic(), "delta-flow");
                stringTopics.put(settings.strikeIntelByStrikeTopic(), "strike-intel");
                stringTopics.put(settings.strikeInvasionTopic(), "strike-invasion");
                stringTopics.put(settings.strikeLiquidityTopic(), "liquidity-heatmap");
                stringTopics.put(settings.databentoPaceMissionTopic(), "mission-pace");
                stringTopics.put(settings.missionControlTopic(), "mission-control");
                stringTopics.put(settings.spreadSkewTopic(), "spread-skew");
                stringTopics.put(settings.databentoEsTradesTopic(), "index-price");
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
                openReplayPartitions(avro, params, avroTopics, true, parts);
            }
            if (!stringTopics.isEmpty()) {
                str = new KafkaConsumer<>(replayConsumerProps(appSessionId, false));
                handle.stringConsumer = str;
                openReplayPartitions(str, params, stringTopics, false, parts);
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
    private void openReplayPartitions(KafkaConsumer<String, Object> consumer, ReplayParams params,
                                      Map<String, String> topicEvents, boolean avro,
                                      Map<TopicPartition, ReplayPartitionState> parts) {
        List<TopicPartition> partitions = partitionsFor(consumer, topicEvents.keySet());
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
            "greek-move-auth");

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
            String expiry = root.hasNonNull("expiry") ? root.get("expiry").asText("") : "";
            String strike = root.hasNonNull("strike") ? root.get("strike").asText("") : "";
            return event + "|" + symbol + "|" + expiry + "|" + strike;
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
            case "delta-flow" -> pendingDeltaFlows;
            case "strike-intel" -> pendingStrikeIntels;
            case "strike-invasion" -> pendingStrikeInvasions;
            case "liquidity-heatmap" -> pendingLiquidityHeatmaps;
            case "mission-pace" -> pendingMissionPaces;
            case "mission-control" -> pendingMissionControls;
            case "spread-skew" -> pendingSpreadSkews;
            case "vix-price", "index-price" -> pendingIndexPrices;
            case "volume-sandwich" -> pendingVolumeSandwiches;
            case "mission-sandwich" -> pendingMissionSandwiches;
            case "gex-by-strike" -> pendingGexByStrike;
            case "strike-sr" -> pendingStrikeSr;
            case "gex-magnet" -> pendingGexMagnet;
            case "max-pain" -> pendingMaxPain;
            case "option-price-behavior" -> pendingOptionPriceBehaviors;
            case "opb-v2-by-option" -> pendingOpbV2ByOptions;
            case "opb-v2-session" -> pendingOpbV2Sessions;
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
                        new ArrayList<>(pendingStrikeSr.values()),
                        new ArrayList<>(pendingGexMagnet.values()),
                        new ArrayList<>(pendingMaxPain.values()),
                        new ArrayList<>(pendingOptionPriceBehaviors.values()),
                        new ArrayList<>(pendingOpbV2ByOptions.values()),
                        new ArrayList<>(pendingOpbV2Sessions.values()),
                        new ArrayList<>(pendingHpsfLatestSignals.values()),
                        new ArrayList<>(pendingHpsfMarketFlows.values()),
                        new ArrayList<>(pendingHpsfTopCandidates.values()),
                        new ArrayList<>(pendingHpsfAudits.values()),
                        new ArrayList<>(pendingHpsfExitIntents.values())
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
                + pendingDeltaFlows.size()
                + pendingStrikeIntels.size()
                + pendingStrikeInvasions.size()
                + pendingLiquidityHeatmaps.size()
                + pendingMissionPaces.size()
                + pendingMissionControls.size()
                + pendingSpreadSkews.size()
                + pendingIndexPrices.size()
                + pendingVolumeSandwiches.size()
                + pendingMissionSandwiches.size()
                + pendingGexByStrike.size()
                + pendingStrikeSr.size()
                + pendingGexMagnet.size()
                + pendingMaxPain.size()
                + pendingOptionPriceBehaviors.size()
                + pendingOpbV2ByOptions.size()
                + pendingOpbV2Sessions.size()
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
        pendingDeltaFlows.clear();
        pendingStrikeIntels.clear();
        pendingStrikeInvasions.clear();
        pendingLiquidityHeatmaps.clear();
        pendingMissionPaces.clear();
        pendingMissionControls.clear();
        pendingSpreadSkews.clear();
        pendingIndexPrices.clear();
        pendingVolumeSandwiches.clear();
        pendingMissionSandwiches.clear();
        pendingGexByStrike.clear();
        pendingStrikeSr.clear();
        pendingGexMagnet.clear();
        pendingMaxPain.clear();
        pendingOptionPriceBehaviors.clear();
        pendingOpbV2ByOptions.clear();
        pendingOpbV2Sessions.clear();
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
        List<String> deltaFlowJsons = new ArrayList<>();
        List<String> strikeIntelJsons = new ArrayList<>();
        List<String> strikeInvasionJsons = new ArrayList<>();
        List<String> liquidityHeatmapJsons = new ArrayList<>();
        List<String> missionPaceJsons = new ArrayList<>();
        List<String> missionControlJsons = new ArrayList<>();
        List<String> spreadSkewJsons = new ArrayList<>();
        List<String> indexPriceJsons = new ArrayList<>();
        List<String> volumeSandwichJsons = new ArrayList<>();
        List<String> missionSandwichJsons = new ArrayList<>();
        List<String> gexByStrikeJsons = new ArrayList<>();
        List<String> strikeSrJsons = new ArrayList<>();
        List<String> gexMagnetJsons = new ArrayList<>();
        List<String> maxPainJsons = new ArrayList<>();
        List<String> optionPriceBehaviorJsons = new ArrayList<>();
        List<String> opbV2ByOptionJsons = new ArrayList<>();
        List<String> opbV2SessionJsons = new ArrayList<>();
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
                case "delta-flow" -> deltaFlowJsons.add(cachedEvent.json());
                case "strike-intel" -> strikeIntelJsons.add(cachedEvent.json());
                case "strike-invasion" -> strikeInvasionJsons.add(cachedEvent.json());
                case "liquidity-heatmap" -> liquidityHeatmapJsons.add(cachedEvent.json());
                case "mission-pace" -> missionPaceJsons.add(cachedEvent.json());
                case "mission-control" -> missionControlJsons.add(cachedEvent.json());
                case "spread-skew" -> spreadSkewJsons.add(cachedEvent.json());
                case "vix-price", "index-price" -> indexPriceJsons.add(cachedEvent.json());
                case "volume-sandwich" -> volumeSandwichJsons.add(cachedEvent.json());
                case "mission-sandwich" -> missionSandwichJsons.add(cachedEvent.json());
                case "gex-by-strike" -> gexByStrikeJsons.add(cachedEvent.json());
                case "strike-sr" -> strikeSrJsons.add(cachedEvent.json());
                case "gex-magnet" -> gexMagnetJsons.add(cachedEvent.json());
                case "max-pain" -> maxPainJsons.add(cachedEvent.json());
                case "option-price-behavior" -> optionPriceBehaviorJsons.add(cachedEvent.json());
                case "opb-v2-by-option" -> opbV2ByOptionJsons.add(cachedEvent.json());
                case "opb-v2-session" -> opbV2SessionJsons.add(cachedEvent.json());
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
                strikeSrJsons,
                gexMagnetJsons,
                maxPainJsons,
                optionPriceBehaviorJsons,
                opbV2ByOptionJsons,
                opbV2SessionJsons,
                hpsfLatestSignalJsons,
                hpsfMarketFlowJsons,
                hpsfTopCandidatesJsons,
                hpsfAuditJsons,
                hpsfExitIntentJsons
        );
    }

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
            List<String> strikeSrJsons,
            List<String> gexMagnetJsons,
            List<String> maxPainJsons,
            List<String> optionPriceBehaviorJsons,
            List<String> opbV2ByOptionJsons,
            List<String> opbV2SessionJsons,
            List<String> hpsfLatestSignalJsons,
            List<String> hpsfMarketFlowJsons,
            List<String> hpsfTopCandidatesJsons,
            List<String> hpsfAuditJsons,
            List<String> hpsfExitIntentJsons
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
                + "\"deltaFlows\":" + jsonArray(deltaFlowJsons) + ","
                + "\"strikeIntels\":" + jsonArray(strikeIntelJsons) + ","
                + "\"strikeInvasions\":" + jsonArray(strikeInvasionJsons) + ","
                + "\"liquidityHeatmaps\":" + jsonArray(liquidityHeatmapJsons) + ","
                + "\"missionPaces\":" + jsonArray(missionPaceJsons) + ","
                + "\"missionControls\":" + jsonArray(missionControlJsons) + ","
                + "\"spreadSkews\":" + jsonArray(spreadSkewJsons) + ","
                + "\"indexPrices\":" + jsonArray(indexPriceJsons) + ","
                + "\"volumeSandwiches\":" + jsonArray(volumeSandwichJsons) + ","
                + "\"missionSandwiches\":" + jsonArray(missionSandwichJsons) + ","
                + "\"gexByStrike\":" + jsonArray(gexByStrikeJsons) + ","
                + "\"strikeSr\":" + jsonArray(strikeSrJsons) + ","
                + "\"gexMagnets\":" + jsonArray(gexMagnetJsons) + ","
                + "\"maxPains\":" + jsonArray(maxPainJsons) + ","
                + "\"optionPriceBehaviors\":" + jsonArray(optionPriceBehaviorJsons) + ","
                + "\"opbV2ByOptions\":" + jsonArray(opbV2ByOptionJsons) + ","
                + "\"opbV2Sessions\":" + jsonArray(opbV2SessionJsons) + ","
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
                + "\"liquidityHeatmaps\":" + liquidityHeatmaps.size() + ","
                + "\"missionPaces\":" + missionPaces.size() + ","
                + "\"missionControls\":" + missionControls.size() + ","
                + "\"spreadSkews\":" + spreadSkews.size() + ","
                + "\"gexByStrike\":" + gexByStrike.size() + ","
                + "\"maxPain\":" + maxPain.size() + ","
                + "\"optionPriceBehaviors\":" + optionPriceBehaviors.size() + ","
                + "\"opbV2ByOptions\":" + opbV2ByOptions.size() + ","
                + "\"opbV2Sessions\":" + opbV2Sessions.size() + ","
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
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, settings.groupIdBase() + "-" + name);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Integer.toString(settings.maxPollRecords()));
        properties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, Integer.toString(settings.fetchMaxBytes()));
        properties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, Integer.toString(settings.maxPartitionFetchBytes()));
        properties.put(ConsumerConfig.RECEIVE_BUFFER_CONFIG, Integer.toString(settings.receiveBufferBytes()));
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
                    + " sourceStaleEvents=" + sourceStaleEvents.get()
                    + " offsetBarriers=" + offsetBarriers.get().size()
                    + " running=" + running.get()
                    + " nowMs=" + nowMs);

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
