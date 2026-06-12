package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final Set<WebSocketSession> clients = new CopyOnWriteArraySet<>();
    private final Map<String, String> snapshots = new ConcurrentHashMap<>();
    private final Map<String, String> paces = new ConcurrentHashMap<>();
    private final Map<String, String> directionalPressures = new ConcurrentHashMap<>();
    private final Map<String, String> currentStates = new ConcurrentHashMap<>();
    private final Map<String, String> gexByStrike = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheEventTimes = new ConcurrentHashMap<>();
    private final Map<String, RecordPosition> cachePositions = new ConcurrentHashMap<>();
    private final Map<String, Long> sourceLastForwardedAt = new ConcurrentHashMap<>();
    private final Object batchLock = new Object();
    private final Map<String, String> pendingSnapshots = new LinkedHashMap<>();
    private final Map<String, String> pendingPaces = new LinkedHashMap<>();
    private final Map<String, String> pendingDirectionalPressures = new LinkedHashMap<>();
    private final Map<String, String> pendingVolumeSandwiches = new LinkedHashMap<>();
    private final Map<String, String> pendingGexByStrike = new LinkedHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean avroCaughtUp = new AtomicBoolean(false);
    private final AtomicBoolean stateCaughtUp = new AtomicBoolean(false);
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

    private record ActiveSelection(String source, String symbol, String expiry, long selectionEpoch, long selectedAtMs) {
        private static ActiveSelection fromSettings(GatewaySettings settings) {
            return new ActiveSelection(
                    GatewaySettings.normalizeSource(settings.initialMarketDataSource()),
                    settings.initialSymbol(),
                    GatewaySettings.normalizeExpiry(settings.initialExpiry()),
                    0L,
                    0L
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

    public FeedGatewayService(GatewaySettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
        this.activeSelection = new AtomicReference<>(ActiveSelection.fromSettings(settings));
    }

    @PostConstruct
    public void start() {
        if (!settings.enabled() || !running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newFixedThreadPool(6, runnable -> {
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
            sendCachedState(session, List.of("volume-sandwich", "gex-by-strike"));
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
                + "\"clients\":" + clients.size() + ","
                + "\"snapshots\":" + snapshots.size() + ","
                + "\"paces\":" + paces.size() + ","
                + "\"directionalPressures\":" + directionalPressures.size() + ","
                + "\"currentStates\":" + currentStates.size() + ","
                + "\"gexByStrike\":" + gexByStrike.size() + ","
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
                + "# HELP options_edge_feed_gateway_current_states Cached current-state count.\n"
                + "# TYPE options_edge_feed_gateway_current_states gauge\n"
                + "options_edge_feed_gateway_current_states " + currentStates.size() + "\n"
                + "# HELP options_edge_feed_gateway_gex_by_strike Cached Unusual Whales GEX strike count.\n"
                + "# TYPE options_edge_feed_gateway_gex_by_strike gauge\n"
                + "options_edge_feed_gateway_gex_by_strike " + gexByStrike.size() + "\n"
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
        Long lastForwardedAtMs = sourceLastForwardedAt.get(selection.source());
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
            List<String> events = topicEvents.values().stream().map(TopicBinding::event).distinct().toList();
            boolean live = caughtUp(consumer, bootstrapEndOffsets);
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
                if (!live && caughtUp(consumer, bootstrapEndOffsets)) {
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

    private void markCacheCaughtUp(String name, List<String> events, AtomicBoolean caughtUpFlag) {
        if (caughtUpFlag.compareAndSet(false, true)) {
            broadcast("status", statusJson());
            broadcastCachedState(events);
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
        broadcastCachedState(List.of("snapshot", "pace", "directional-pressure", "volume-sandwich", "gex-by-strike"));
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
                    settings.databentoVolumeSandwichTopic(),
                    settings.databentoVolumeSandwichAlertsTopic()
            );
        }
        return List.of();
    }

    private boolean shouldForward(TopicBinding binding, String json, ConsumerRecord<?, ?> record) {
        if (binding == null) {
            return false;
        }
        ActiveSelection selection = activeSelection.get();
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
        sourceLastForwardedAt.put(selection.source(), System.currentTimeMillis());
        if (!"snapshot".equals(binding.event())) {
            return;
        }
        String key = selectionKey(selection);
        if (readySelectionKey.compareAndSet("", key) || readySelectionKey.compareAndSet(null, key)) {
            broadcast("source-ready", activeSelectionJson(selection, "source-ready"));
        }
    }

    private void reportSourceStale(ActiveSelection selection, String reason) {
        staleDroppedEvents.incrementAndGet();
        sourceStaleEvents.incrementAndGet();
        long nowMs = System.currentTimeMillis();
        long previousMs = lastSourceStaleBroadcastMs.get();
        if (nowMs - previousMs >= 5_000L && lastSourceStaleBroadcastMs.compareAndSet(previousMs, nowMs)) {
            broadcast("source-stale", activeSelectionJson(selection, "source-stale:" + reason));
        }
    }

    private String selectionKey(ActiveSelection selection) {
        return selection.source() + "|" + selection.symbol() + "|" + selection.expiry() + "|" + selection.selectionEpoch();
    }

    private boolean matchesActiveSelection(String json, ActiveSelection selection) {
        if (json == null || json.isBlank() || selection == null) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(json);
            String source = GatewaySettings.normalizeSource(text(root, "marketDataSource"));
            if (source.isBlank() && "UNUSUAL_WHALES".equalsIgnoreCase(text(root, "source"))) {
                source = "IBKR";
            }
            if (!source.isBlank() && !selection.source().equals(source)) {
                return false;
            }
            long recordEpoch = longField(root, "selectionEpoch", 0L);
            if (recordEpoch > 0L && selection.selectionEpoch() > 0L && recordEpoch < selection.selectionEpoch()) {
                return false;
            }
            return selection.symbol().equalsIgnoreCase(text(root, "symbol"))
                    && selection.expiry().equals(normalizeExpiry(text(root, "expiry")));
        } catch (JsonProcessingException ignored) {
            return false;
        }
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

    private void broadcastCachedState(List<String> events) {
        List<CachedEvent> cachedEvents = cachedEvents(events, System.currentTimeMillis());
        if (cachedEvents.isEmpty()) {
            return;
        }
        String envelope = uiBatchEnvelopeJson(cachedEvents);
        for (WebSocketSession client : clients) {
            sendEnvelope(client, envelope);
        }
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
                        .filter(entry -> passesSelectionBarrier("snapshot:" + entry.getKey(), selection))
                        .filter(entry -> matchesActiveSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("snapshot", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "pace" -> paces.entrySet().stream()
                        .filter(entry -> isCacheFresh("pace:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("pace:" + entry.getKey(), selection))
                        .filter(entry -> matchesActiveSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("pace", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "directional-pressure" -> directionalPressures.entrySet().stream()
                        .filter(entry -> isCacheFresh("directional-pressure:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("directional-pressure:" + entry.getKey(), selection))
                        .filter(entry -> matchesActiveSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("directional-pressure", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "volume-sandwich" -> currentStates.entrySet().stream()
                        .filter(entry -> "volume-sandwich".equals(eventFromCacheKey(entry.getKey())))
                        .filter(entry -> isCacheFresh(entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier(entry.getKey(), selection))
                        .filter(entry -> matchesActiveSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("volume-sandwich", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "gex-by-strike" -> gexByStrike.entrySet().stream()
                        .filter(entry -> isCacheFresh("gex-by-strike:" + entry.getKey(), nowMs))
                        .filter(entry -> passesSelectionBarrier("gex-by-strike:" + entry.getKey(), selection))
                        .filter(entry -> "IBKR".equals(selection.source()))
                        .filter(entry -> matchesActiveSelection(entry.getValue(), selection))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("gex-by-strike", entry.getValue()))
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
        Long eventTimeMs = cacheEventTimes.get(versionKey);
        if (eventTimeMs == null || !passesSelectionTimeBarrier(eventTimeMs, selection)) {
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
        if (selection != null && selection.selectedAtMs() > 0L && eventTimeMs < selection.selectedAtMs()) {
            return false;
        }
        long maxStaleMs = settings.maxStaleMs();
        return maxStaleMs <= 0L || eventTimeMs >= System.currentTimeMillis() - maxStaleMs;
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
        } else if (versionKey.startsWith("volume-sandwich:")) {
            currentStates.remove(versionKey);
        } else if (versionKey.startsWith("gex-by-strike:")) {
            gexByStrike.remove(versionKey.substring("gex-by-strike:".length()));
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
            case "volume-sandwich" -> pendingVolumeSandwiches;
            case "gex-by-strike" -> pendingGexByStrike;
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
                        new ArrayList<>(pendingVolumeSandwiches.values()),
                        new ArrayList<>(pendingGexByStrike.values())
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
                + pendingVolumeSandwiches.size()
                + pendingGexByStrike.size();
    }

    private void clearPendingLocked() {
        pendingSnapshots.clear();
        pendingPaces.clear();
        pendingDirectionalPressures.clear();
        pendingVolumeSandwiches.clear();
        pendingGexByStrike.clear();
    }

    private String envelopeJson(String event, String json) {
        String payload = json == null || json.isBlank() ? "{}" : json;
        return "{\"type\":\"" + escapeJson(event) + "\",\"data\":" + payload + "}";
    }

    private String uiBatchEnvelopeJson(List<CachedEvent> cachedEvents) {
        List<String> snapshotJsons = new ArrayList<>();
        List<String> paceJsons = new ArrayList<>();
        List<String> directionalPressureJsons = new ArrayList<>();
        List<String> volumeSandwichJsons = new ArrayList<>();
        List<String> gexByStrikeJsons = new ArrayList<>();
        for (CachedEvent cachedEvent : cachedEvents) {
            switch (cachedEvent.event()) {
                case "snapshot" -> snapshotJsons.add(cachedEvent.json());
                case "pace" -> paceJsons.add(cachedEvent.json());
                case "directional-pressure" -> directionalPressureJsons.add(cachedEvent.json());
                case "volume-sandwich" -> volumeSandwichJsons.add(cachedEvent.json());
                case "gex-by-strike" -> gexByStrikeJsons.add(cachedEvent.json());
                default -> {
                    // This batch protocol only carries latest-state feed events.
                }
            }
        }
        return uiBatchEnvelopeJson(snapshotJsons, paceJsons, directionalPressureJsons, volumeSandwichJsons, gexByStrikeJsons);
    }

    private String uiBatchEnvelopeJson(
            List<String> snapshotJsons,
            List<String> paceJsons,
            List<String> directionalPressureJsons,
            List<String> volumeSandwichJsons,
            List<String> gexByStrikeJsons
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
                + "\"volumeSandwiches\":" + jsonArray(volumeSandwichJsons) + ","
                + "\"gexByStrike\":" + jsonArray(gexByStrikeJsons)
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
                + "\"lastSelectedForwardAgeSeconds\":" + lastSelectedForwardAgeSeconds(selection) + ","
                + "\"snapshots\":" + snapshots.size() + ","
                + "\"paces\":" + paces.size() + ","
                + "\"directionalPressures\":" + directionalPressures.size() + ","
                + "\"gexByStrike\":" + gexByStrike.size() + ","
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
