package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FeedGatewayService {
    private final Instant startedAt = Instant.now();
    private final GatewaySettings settings;
    private final ObjectMapper mapper;
    private final HpsfGatewayViewMapper hpsfViewMapper;
    private final Set<WebSocketSession> clients = new CopyOnWriteArraySet<>();
    private final Map<String, String> snapshots = new ConcurrentHashMap<>();
    private final Map<String, String> paces = new ConcurrentHashMap<>();
    private final Map<String, String> directionalPressures = new ConcurrentHashMap<>();
    private final Map<String, String> indexPrices = new ConcurrentHashMap<>();
    private final Map<String, String> currentStates = new ConcurrentHashMap<>();
    private final Map<String, String> gexByStrike = new ConcurrentHashMap<>();
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
    private final Map<String, String> pendingDirectionalPressures = new LinkedHashMap<>();
    private final Map<String, String> pendingIndexPrices = new LinkedHashMap<>();
    private final Map<String, String> pendingVolumeSandwiches = new LinkedHashMap<>();
    private final Map<String, String> pendingGexByStrike = new LinkedHashMap<>();
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
    private final AtomicLong staleDroppedEvents = new AtomicLong();
    private final AtomicLong seekToLatestEvents = new AtomicLong();
    private final AtomicLong sourceStaleEvents = new AtomicLong();
    private final AtomicLong lastSourceStaleBroadcastMs = new AtomicLong();
    private final AtomicLong lastLagCheckMs = new AtomicLong();
    private final AtomicReference<String> readySelectionKey = new AtomicReference<>("");
    private ExecutorService executor;
    private ScheduledExecutorService batchExecutor;

    private record CachedEvent(String event, String json) {
    }

    private record TopicBinding(String source, String event) {
    }

    private record RecordPosition(TopicPartition partition, long offset) {
    }

    private record HpsfCacheUpdate(String event, String key, String json) {
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

    public FeedGatewayService(GatewaySettings settings, ObjectMapper mapper, HpsfGatewayViewMapper hpsfViewMapper) {
        this.settings = settings;
        this.mapper = mapper;
        this.hpsfViewMapper = hpsfViewMapper;
        this.activeSelection = new AtomicReference<>(ActiveSelection.fromSettings(settings));
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
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        ExecutorService currentExecutor = executor;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
            try {
                currentExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ScheduledExecutorService currentBatchExecutor = batchExecutor;
        if (currentBatchExecutor != null) {
            currentBatchExecutor.shutdownNow();
            try {
                currentBatchExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void addClient(WebSocketSession session) {
        long nowMs = System.currentTimeMillis();
        purgeExpiredCache(nowMs);
        clients.add(session);
        send(session, "status", statusJson());
        if (avroCaughtUp.get()) {
            sendCachedState(session, List.of("snapshot", "pace", "directional-pressure"));
        }
        if (stateCaughtUp.get()) {
            sendCachedState(session, List.of("vix-price", "index-price", "volume-sandwich", "gex-by-strike"));
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
        clients.remove(session);
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException | IllegalStateException ignored) {
            // Removing the session from the fanout set is sufficient.
        }
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
                + "\"clients\":" + clients.size() + ","
                + "\"snapshots\":" + snapshots.size() + ","
                + "\"paces\":" + paces.size() + ","
                + "\"directionalPressures\":" + directionalPressures.size() + ","
                + "\"indexPrices\":" + indexPrices.size() + ","
                + "\"currentStates\":" + currentStates.size() + ","
                + "\"gexByStrike\":" + gexByStrike.size() + ","
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
                + "# HELP options_edge_feed_gateway_snapshots Cached option snapshot count.\n"
                + "# TYPE options_edge_feed_gateway_snapshots gauge\n"
                + "options_edge_feed_gateway_snapshots " + snapshots.size() + "\n"
                + "# HELP options_edge_feed_gateway_paces Cached pace count.\n"
                + "# TYPE options_edge_feed_gateway_paces gauge\n"
                + "options_edge_feed_gateway_paces " + paces.size() + "\n"
                + "# HELP options_edge_feed_gateway_directional_pressures Cached directional-pressure count.\n"
                + "# TYPE options_edge_feed_gateway_directional_pressures gauge\n"
                + "options_edge_feed_gateway_directional_pressures " + directionalPressures.size() + "\n"
                + "# HELP options_edge_feed_gateway_index_prices Cached index price count.\n"
                + "# TYPE options_edge_feed_gateway_index_prices gauge\n"
                + "options_edge_feed_gateway_index_prices " + indexPrices.size() + "\n"
                + "# HELP options_edge_feed_gateway_current_states Cached current-state count.\n"
                + "# TYPE options_edge_feed_gateway_current_states gauge\n"
                + "options_edge_feed_gateway_current_states " + currentStates.size() + "\n"
                + "# HELP options_edge_feed_gateway_gex_by_strike Cached Unusual Whales GEX strike count.\n"
                + "# TYPE options_edge_feed_gateway_gex_by_strike gauge\n"
                + "options_edge_feed_gateway_gex_by_strike " + gexByStrike.size() + "\n"
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
                + "options_edge_feed_gateway_uptime_seconds " + uptimeSeconds + "\n";
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
        topicEvents.put(settings.ibkrDirectionalPressureTopic(), new TopicBinding("IBKR", "directional-pressure"));
        topicEvents.put(settings.databentoDisplayTopic(), new TopicBinding("DATABENTO", "snapshot"));
        topicEvents.put(settings.databentoPaceTopic(), new TopicBinding("DATABENTO", "pace"));
        topicEvents.put(settings.databentoDirectionalPressureTopic(), new TopicBinding("DATABENTO", "directional-pressure"));
        runAssignedCacheConsumer("avro", topicEvents, true, avroCaughtUp);
    }

    private void runJsonStateCacheConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrVixPriceTopic(), new TopicBinding("IBKR", "vix-price"));
        topicEvents.put(settings.databentoEsTradesTopic(), new TopicBinding("DATABENTO", "index-price"));
        topicEvents.put(settings.ibkrVolumeSandwichTopic(), new TopicBinding("IBKR", "volume-sandwich"));
        topicEvents.put(settings.databentoVolumeSandwichTopic(), new TopicBinding("DATABENTO", "volume-sandwich"));
        topicEvents.put(settings.ibkrUnusualWhalesGexTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        topicEvents.put(settings.ibkrUnusualWhalesGexHistoryTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        runAssignedCacheConsumer("state", topicEvents, false, stateCaughtUp);
    }

    private void runAvroLiveConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrDisplayTopic(), new TopicBinding("IBKR", "snapshot"));
        topicEvents.put(settings.ibkrPaceTopic(), new TopicBinding("IBKR", "pace"));
        topicEvents.put(settings.ibkrDirectionalPressureTopic(), new TopicBinding("IBKR", "directional-pressure"));
        topicEvents.put(settings.databentoDisplayTopic(), new TopicBinding("DATABENTO", "snapshot"));
        topicEvents.put(settings.databentoPaceTopic(), new TopicBinding("DATABENTO", "pace"));
        topicEvents.put(settings.databentoDirectionalPressureTopic(), new TopicBinding("DATABENTO", "directional-pressure"));
        runLiveConsumer("avro-live", topicEvents, true, avroCaughtUp);
    }

    private void runJsonStateLiveConsumer() {
        Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.ibkrVixPriceTopic(), new TopicBinding("IBKR", "vix-price"));
        topicEvents.put(settings.databentoEsTradesTopic(), new TopicBinding("DATABENTO", "index-price"));
        topicEvents.put(settings.ibkrVolumeSandwichTopic(), new TopicBinding("IBKR", "volume-sandwich"));
        topicEvents.put(settings.databentoVolumeSandwichTopic(), new TopicBinding("DATABENTO", "volume-sandwich"));
        topicEvents.put(settings.ibkrUnusualWhalesGexTopic(), new TopicBinding("IBKR", "gex-by-strike"));
        topicEvents.put(settings.ibkrUnusualWhalesGexHistoryTopic(), new TopicBinding("IBKR", "gex-by-strike"));
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
                for (ConsumerRecord<String, String> record : records) {
                    HpsfCacheUpdate update = updateHpsfCache(record);
                    if (update != null && hpsfCaughtUp.get()) {
                        enqueuePending(update.event(), update.key(), update.json());
                        forwardedEvents.incrementAndGet();
                    } else if (update != null) {
                        inactiveDroppedEvents.incrementAndGet();
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
                    if (json != null && !json.isBlank() && shouldForward(binding, json, record)) {
                        broadcast(binding.event(), json);
                        forwardedEvents.incrementAndGet();
                        recordSelectedForward(binding, json);
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
            seekToCacheWindow(consumer, partitions);
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
        long ttlMs = settings.cacheTtlMs();
        if (ttlMs <= 0) {
            consumer.seekToEnd(partitions);
            return;
        }
        long startTimeMs = System.currentTimeMillis() - ttlMs;
        Map<TopicPartition, Long> timestamps = new HashMap<>();
        for (TopicPartition partition : partitions) {
            timestamps.put(partition, startTimeMs);
        }
        Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(timestamps);
        List<TopicPartition> seekToEnd = new ArrayList<>();
        for (TopicPartition partition : partitions) {
            OffsetAndTimestamp offset = offsets.get(partition);
            if (offset == null) {
                seekToEnd.add(partition);
            } else {
                consumer.seek(partition, offset.offset());
            }
        }
        if (!seekToEnd.isEmpty()) {
            consumer.seekToEnd(seekToEnd);
        }
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
                seekToCacheWindow(consumer, partitions);
            } else {
                consumer.seekToEnd(partitions);
            }
            while (running.get()) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                for (ConsumerRecord<String, Object> record : records) {
                    TopicBinding binding = topicEvents.get(record.topic());
                    String json = enrichJson(avro ? avroJson(record.value()) : stringJson(record.value()), binding);
                    if (binding == null || json == null || json.isBlank()) {
                        continue;
                    }
                    String cacheKey = updateCache(binding, record, json);
                    if (cacheKey != null && cacheCaughtUpFlag.get() && shouldForward(binding, json, record)) {
                        enqueuePending(binding.event(), cacheKey, json);
                        forwardedEvents.incrementAndGet();
                        recordSelectedForward(binding, json);
                    } else if (cacheKey != null) {
                        inactiveDroppedEvents.incrementAndGet();
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
                    return binding != null && selection.source().equals(binding.source());
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
            broadcast("status", statusJson());
            if (broadcastCachedState(events)) {
                markSelectionReady(activeSelection.get());
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
        ActiveSelection previous = activeSelection.get();
        if (!next.newerThan(previous)) {
            return;
        }
        offsetBarriers.set(captureOffsetBarriers(next));
        readySelectionKey.set("");
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
        System.out.println("Feed gateway selected market data source " + next.source()
                + " " + next.symbol() + " " + next.expiry()
                + " epoch=" + next.selectionEpoch());
    }

    private Map<TopicPartition, Long> captureOffsetBarriers(ActiveSelection selection) {
        List<String> topics = outputTopicsForSource(selection.source());
        if (topics.isEmpty()) {
            return Map.of();
        }
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(stringConsumerProperties("barrier"))) {
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
                    settings.databentoDirectionalPressureTopic(),
                    settings.ibkrVixPriceTopic(),
                    settings.databentoEsTradesTopic(),
                    settings.databentoVolumeSandwichTopic(),
                    settings.databentoVolumeSandwichAlertsTopic()
            );
        }
        return List.of();
    }

    static List<String> sourceSwitchReplayEvents() {
        return List.of("snapshot", "pace", "directional-pressure", "vix-price", "index-price", "volume-sandwich", "gex-by-strike");
    }

    private boolean shouldForward(TopicBinding binding, String json, ConsumerRecord<?, ?> record) {
        if (binding == null) {
            return false;
        }
        ActiveSelection selection = activeSelection.get();
        if ("vix-price".equals(binding.event())) {
            return passesSelectionBarrier(record, selection);
        }
        if ("index-price".equals(binding.event())) {
            return "DATABENTO".equals(selection.source()) && passesSelectionBarrier(record, selection);
        }
        if (!binding.source().equals(selection.source())) {
            return false;
        }
        if ("gex-by-strike".equals(binding.event()) && !"IBKR".equals(selection.source())) {
            return false;
        }
        if (!passesSelectionBarrier(record, selection)) {
            reportSourceStale(selection, "switch-barrier");
            return false;
        }
        return matchesActiveSelection(json, selection);
    }

    private void recordSelectedForward(TopicBinding binding, String json) {
        ActiveSelection selection = activeSelection.get();
        sourceLastForwardedAt.put(selectionKey(selection), System.currentTimeMillis());
        if (!"snapshot".equals(binding.event())) {
            return;
        }
        markSelectionReady(selection);
    }

    private void markSelectionReady(ActiveSelection selection) {
        String key = selectionKey(selection);
        sourceLastForwardedAt.put(key, System.currentTimeMillis());
        if (readySelectionKey.compareAndSet("", key) || readySelectionKey.compareAndSet(null, key)) {
            broadcast("source-ready", activeSelectionJson(selection, "source-ready"));
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

    private boolean matchesActiveSelection(String json, ActiveSelection selection) {
        return matchesSelection(json, selection, true);
    }

    private boolean matchesCachedSelection(String json, ActiveSelection selection) {
        return matchesSelection(json, selection, true);
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
        if ("directional-pressure".equals(event)) {
            key = directionalPressureCacheKey(json, key);
        } else if ("vix-price".equals(event) || "index-price".equals(event)) {
            key = indexPriceCacheKey(json, key);
        }
        key = binding.source() + "|" + key;
        String versionKey = event + ":" + key;
        long eventTime = cacheTimestamp(record);
        Long previousEventTime = cacheEventTimes.get(versionKey);
        if (previousEventTime != null && previousEventTime > eventTime) {
            return null;
        }
        if ("gex-by-strike".equals(event)
                && previousEventTime != null
                && previousEventTime == eventTime
                && hasGexHistory(gexByStrike.get(key))
                && !hasGexHistory(json)) {
            return null;
        }
        if (isExpired(eventTime, System.currentTimeMillis())) {
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
            case "directional-pressure" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                directionalPressures.put(key, json);
                return key;
            }
            case "vix-price", "index-price" -> {
                cacheEventTimes.put(versionKey, eventTime);
                cachePositions.put(versionKey, recordPosition(record));
                indexPrices.put(key, json);
                return key;
            }
            case "volume-sandwich" -> {
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
            HpsfCacheUpdate update = new HpsfCacheUpdate("hpsf-latest-signal", groupKey, json);
            HpsfCacheUpdate candidates = topCandidatesUpdate(groupKey);
            if (candidates != null) {
                cacheEventTimes.put(candidates.event() + ":" + candidates.key(), cacheTimestamp(record));
                cachePositions.put(candidates.event() + ":" + candidates.key(), recordPosition(record));
                putHpsfView(candidates);
                if (hpsfCaughtUp.get()) {
                    enqueuePending(candidates.event(), candidates.key(), candidates.json());
                }
            }
            return update;
        }
        if (settings.hpsfMarketFlowTopic().equals(topic)) {
            MarketFlowSnapshot snapshot = read(rawJson, MarketFlowSnapshot.class);
            String key = fallbackKey(record);
            return new HpsfCacheUpdate("hpsf-market-flow", key, write(hpsfViewMapper.marketFlowView(snapshot)));
        }
        if (settings.hpsfStrikeScoreTopic().equals(topic)) {
            StrikeScoreSnapshot score = read(rawJson, StrikeScoreSnapshot.class);
            String groupKey = hpsfGroupKey(score.tradeDate(), score.expiry(), fallbackKey(record));
            hpsfStrikeScores.put(groupKey + "|" + fallbackKey(record), score);
            return topCandidatesUpdate(groupKey);
        }
        if (settings.hpsfAuditTopic().equals(topic)) {
            HpsfAuditEvent audit = read(rawJson, HpsfAuditEvent.class);
            String key = hpsfGroupKey(audit.tradeDate(), audit.expiry(), fallbackKey(record));
            return new HpsfCacheUpdate("hpsf-audit", key, write(hpsfViewMapper.auditView(audit)));
        }
        if (settings.hpsfExitSignalTopic().equals(topic)) {
            HpsfExitIntentEvent event = read(rawJson, HpsfExitIntentEvent.class);
            String key = hpsfGroupKey(event.tradeDate(), event.expiry(), fallbackKey(record));
            return new HpsfCacheUpdate("hpsf-exit-intent", key, write(hpsfViewMapper.exitIntentView(event)));
        }
        return null;
    }

    private HpsfCacheUpdate topCandidatesUpdate(String groupKey) {
        String latestEvaluationId = hpsfLatestEvaluationIds.get(groupKey);
        List<StrikeScoreSnapshot> scores = hpsfStrikeScores.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(groupKey + "|"))
                .map(Map.Entry::getValue)
                .filter(score -> latestEvaluationId == null || latestEvaluationId.equals(score.evaluationId()))
                .toList();
        if (scores.isEmpty()) {
            return null;
        }
        return new HpsfCacheUpdate("hpsf-top-candidates", groupKey, write(hpsfViewMapper.topCandidatesView(scores)));
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
            if (isExpired(entry.getValue(), nowMs)) {
                expiredKeys.add(entry.getKey());
            }
        }
        expiredKeys.forEach(this::removeCacheEntry);
    }

    private boolean isCacheFresh(String versionKey, long nowMs) {
        Long eventTime = cacheEventTimes.get(versionKey);
        return eventTime != null && !isExpired(eventTime, nowMs);
    }

    private boolean broadcastCachedState(List<String> events) {
        List<CachedEvent> cachedEvents = cachedEvents(events, System.currentTimeMillis());
        if (cachedEvents.isEmpty()) {
            return false;
        }
        String envelope = uiBatchEnvelopeJson(cachedEvents);
        for (WebSocketSession client : clients) {
            sendEnvelope(client, envelope);
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
                                enforceCachedReplayMaxStale("snapshot", selection == null ? "" : selection.source())
                        ))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
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
                case "directional-pressure" -> directionalPressures.entrySet().stream()
                        .filter(entry -> isCacheFresh("directional-pressure:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("directional-pressure:" + entry.getKey(), selection))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("directional-pressure", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "vix-price" -> indexPrices.entrySet().stream()
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
                case "volume-sandwich" -> currentStates.entrySet().stream()
                        .filter(entry -> "volume-sandwich".equals(eventFromCacheKey(entry.getKey())))
                        .filter(entry -> isCacheFresh(entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier(entry.getKey(), selection))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("volume-sandwich", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "gex-by-strike" -> gexByStrike.entrySet().stream()
                        .filter(entry -> isCacheFresh("gex-by-strike:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("gex-by-strike:" + entry.getKey(), selection))
                        .filter(entry -> "IBKR".equals(selection.source()))
                        .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("gex-by-strike", entry.getValue()))
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

    private boolean passesSelectionBarrier(String versionKey, ActiveSelection selection) {
        return passesSelectionBarrier(versionKey, selection, true);
    }

    private boolean passesSelectionBarrier(String versionKey, ActiveSelection selection, boolean enforceMaxStale) {
        Long eventTimeMs = cacheEventTimes.get(versionKey);
        if (eventTimeMs == null || !passesSelectionTimeBarrier(eventTimeMs, selection, enforceMaxStale)) {
            return false;
        }
        RecordPosition position = cachePositions.get(versionKey);
        return position == null || passesOffsetBarrier(position.partition(), position.offset());
    }

    private boolean passesSelectionBarrier(ConsumerRecord<?, ?> record, ActiveSelection selection) {
        if (!passesSelectionTimeBarrier(cacheTimestamp(record), selection)) {
            return false;
        }
        return passesOffsetBarrier(new TopicPartition(record.topic(), record.partition()), record.offset());
    }

    private boolean passesSelectionTimeBarrier(long eventTimeMs, ActiveSelection selection) {
        return passesSelectionTimeBarrier(eventTimeMs, selection, true);
    }

    private boolean passesSelectionTimeBarrier(long eventTimeMs, ActiveSelection selection, boolean enforceMaxStale) {
        if (selection != null && selection.selectedAtMs() > 0L && eventTimeMs < selection.selectedAtMs()) {
            return false;
        }
        if (!enforceMaxStale) {
            return true;
        }
        long maxStaleMs = settings.maxStaleMs();
        return maxStaleMs <= 0L || eventTimeMs >= System.currentTimeMillis() - maxStaleMs;
    }

    static boolean enforceCachedReplayMaxStale(String event, String source) {
        return !("snapshot".equals(event) && "DATABENTO".equals(GatewaySettings.normalizeSource(source)));
    }

    private boolean passesOffsetBarrier(TopicPartition partition, long offset) {
        Long barrier = offsetBarriers.get().get(partition);
        return barrier == null || offset >= barrier;
    }

    private long cacheTimestamp(ConsumerRecord<?, ?> record) {
        long eventTime = record.timestamp();
        return eventTime >= 0 ? eventTime : System.currentTimeMillis();
    }

    private RecordPosition recordPosition(ConsumerRecord<?, ?> record) {
        return new RecordPosition(new TopicPartition(record.topic(), record.partition()), record.offset());
    }

    private void removeCacheEntry(String versionKey) {
        cacheEventTimes.remove(versionKey);
        cachePositions.remove(versionKey);
        if (versionKey.startsWith("snapshot:")) {
            snapshots.remove(versionKey.substring("snapshot:".length()));
        } else if (versionKey.startsWith("pace:")) {
            paces.remove(versionKey.substring("pace:".length()));
        } else if (versionKey.startsWith("directional-pressure:")) {
            directionalPressures.remove(versionKey.substring("directional-pressure:".length()));
        } else if (versionKey.startsWith("vix-price:")) {
            indexPrices.remove(versionKey.substring("vix-price:".length()));
        } else if (versionKey.startsWith("index-price:")) {
            indexPrices.remove(versionKey.substring("index-price:".length()));
        } else if (versionKey.startsWith("volume-sandwich:")) {
            currentStates.remove(versionKey);
        } else if (versionKey.startsWith("gex-by-strike:")) {
            gexByStrike.remove(versionKey.substring("gex-by-strike:".length()));
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
        for (WebSocketSession client : clients) {
            send(client, event, json);
        }
    }

    private void send(WebSocketSession session, String event, String json) {
        sendEnvelope(session, envelopeJson(event, json));
    }

    private void sendEnvelope(WebSocketSession session, String envelope) {
        if (!session.isOpen()) {
            removeClient(session);
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(envelope));
            }
            if (GatewaySettings.boolValue("GATEWAY_WS_LOG_MESSAGES", false)) {
                System.out.println("goes to UI ->>> " + envelope);
            }
        } catch (IOException | IllegalStateException ignored) {
            removeClient(session);
        }
    }

    private void enqueuePending(String event, String key, String json) {
        if (clients.isEmpty()) {
            return;
        }
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
            case "directional-pressure" -> pendingDirectionalPressures;
            case "vix-price", "index-price" -> pendingIndexPrices;
            case "volume-sandwich" -> pendingVolumeSandwiches;
            case "gex-by-strike" -> pendingGexByStrike;
            case "hpsf-latest-signal" -> pendingHpsfLatestSignals;
            case "hpsf-market-flow" -> pendingHpsfMarketFlows;
            case "hpsf-top-candidates" -> pendingHpsfTopCandidates;
            case "hpsf-audit" -> pendingHpsfAudits;
            case "hpsf-exit-intent" -> pendingHpsfExitIntents;
            default -> null;
        };
    }

    private void flushPendingBatch() {
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
                        new ArrayList<>(pendingDirectionalPressures.values()),
                        new ArrayList<>(pendingIndexPrices.values()),
                        new ArrayList<>(pendingVolumeSandwiches.values()),
                        new ArrayList<>(pendingGexByStrike.values()),
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
                + pendingDirectionalPressures.size()
                + pendingIndexPrices.size()
                + pendingVolumeSandwiches.size()
                + pendingGexByStrike.size()
                + pendingHpsfLatestSignals.size()
                + pendingHpsfMarketFlows.size()
                + pendingHpsfTopCandidates.size()
                + pendingHpsfAudits.size()
                + pendingHpsfExitIntents.size();
    }

    private void clearPendingLocked() {
        pendingSnapshots.clear();
        pendingPaces.clear();
        pendingDirectionalPressures.clear();
        pendingIndexPrices.clear();
        pendingVolumeSandwiches.clear();
        pendingGexByStrike.clear();
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
        List<String> directionalPressureJsons = new ArrayList<>();
        List<String> indexPriceJsons = new ArrayList<>();
        List<String> volumeSandwichJsons = new ArrayList<>();
        List<String> gexByStrikeJsons = new ArrayList<>();
        List<String> hpsfLatestSignalJsons = new ArrayList<>();
        List<String> hpsfMarketFlowJsons = new ArrayList<>();
        List<String> hpsfTopCandidatesJsons = new ArrayList<>();
        List<String> hpsfAuditJsons = new ArrayList<>();
        List<String> hpsfExitIntentJsons = new ArrayList<>();
        for (CachedEvent cachedEvent : cachedEvents) {
            switch (cachedEvent.event()) {
                case "snapshot" -> snapshotJsons.add(cachedEvent.json());
                case "pace" -> paceJsons.add(cachedEvent.json());
                case "directional-pressure" -> directionalPressureJsons.add(cachedEvent.json());
                case "vix-price", "index-price" -> indexPriceJsons.add(cachedEvent.json());
                case "volume-sandwich" -> volumeSandwichJsons.add(cachedEvent.json());
                case "gex-by-strike" -> gexByStrikeJsons.add(cachedEvent.json());
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
                directionalPressureJsons,
                indexPriceJsons,
                volumeSandwichJsons,
                gexByStrikeJsons,
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
            List<String> directionalPressureJsons,
            List<String> indexPriceJsons,
            List<String> volumeSandwichJsons,
            List<String> gexByStrikeJsons,
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
                + "\"directionalPressures\":" + jsonArray(directionalPressureJsons) + ","
                + "\"indexPrices\":" + jsonArray(indexPriceJsons) + ","
                + "\"volumeSandwiches\":" + jsonArray(volumeSandwichJsons) + ","
                + "\"gexByStrike\":" + jsonArray(gexByStrikeJsons) + ","
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
                + "\"directionalPressures\":" + directionalPressures.size() + ","
                + "\"gexByStrike\":" + gexByStrike.size() + ","
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
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, settings.groupIdBase() + "-" + name);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Integer.toString(settings.maxPollRecords()));
        properties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, Integer.toString(settings.fetchMaxBytes()));
        properties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, Integer.toString(settings.maxPartitionFetchBytes()));
        properties.put(ConsumerConfig.RECEIVE_BUFFER_CONFIG, Integer.toString(settings.receiveBufferBytes()));
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
}
