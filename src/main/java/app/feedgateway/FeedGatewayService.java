package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class FeedGatewayService {
    private final GatewaySettings settings;
    private final ObjectMapper mapper;
    private final Set<WebSocketSession> clients = new CopyOnWriteArraySet<>();
    private final Map<String, String> snapshots = new ConcurrentHashMap<>();
    private final Map<String, String> gammaHistories = new ConcurrentHashMap<>();
    private final Map<String, String> paces = new ConcurrentHashMap<>();
    private final Map<String, String> directionalPressures = new ConcurrentHashMap<>();
    private final Map<String, String> currentStates = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheEventTimes = new ConcurrentHashMap<>();
    private final Object batchLock = new Object();
    private final Map<String, String> pendingSnapshots = new LinkedHashMap<>();
    private final Map<String, String> pendingGammaHistories = new LinkedHashMap<>();
    private final Map<String, String> pendingPaces = new LinkedHashMap<>();
    private final Map<String, String> pendingDirectionalPressures = new LinkedHashMap<>();
    private final Map<String, String> pendingVolumeSandwiches = new LinkedHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean avroCaughtUp = new AtomicBoolean(false);
    private final AtomicBoolean stateCaughtUp = new AtomicBoolean(false);
    private final AtomicLong coalescedUpdates = new AtomicLong();
    private final AtomicLong batchesSent = new AtomicLong();
    private final AtomicLong consumerRestarts = new AtomicLong();
    private ExecutorService executor;
    private ScheduledExecutorService batchExecutor;

    private record CachedEvent(String event, String json) {
    }

    @FunctionalInterface
    private interface ConsumerAttempt {
        void run(boolean retry) throws RuntimeException;
    }

    public FeedGatewayService(GatewaySettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    @PostConstruct
    public void start() {
        if (!settings.enabled() || !running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newFixedThreadPool(5, runnable -> {
            Thread thread = new Thread(runnable, "options-edge-feed-gateway");
            thread.setDaemon(true);
            return thread;
        });
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
            sendCachedState(session, List.of("snapshot", "gamma-history", "pace", "directional-pressure"));
        }
        if (stateCaughtUp.get()) {
            sendCachedState(session, List.of("volume-sandwich"));
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
        return "{"
                + "\"running\":" + running.get() + ","
                + "\"avroCaughtUp\":" + avroCaughtUp.get() + ","
                + "\"stateCaughtUp\":" + stateCaughtUp.get() + ","
                + "\"clients\":" + clients.size() + ","
                + "\"snapshots\":" + snapshots.size() + ","
                + "\"gammaHistories\":" + gammaHistories.size() + ","
                + "\"paces\":" + paces.size() + ","
                + "\"directionalPressures\":" + directionalPressures.size() + ","
                + "\"currentStates\":" + currentStates.size() + ","
                + "\"pendingEvents\":" + pendingEventCount() + ","
                + "\"webSocketBatchMs\":" + settings.webSocketBatchMs() + ","
                + "\"coalescedUpdates\":" + coalescedUpdates.get() + ","
                + "\"batchesSent\":" + batchesSent.get() + ","
                + "\"consumerRestarts\":" + consumerRestarts.get() + ","
                + "\"cacheTtlMs\":" + settings.cacheTtlMs()
                + "}";
    }

    private void runAvroCacheConsumer() {
        Map<String, String> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.displayTopic(), "snapshot");
        topicEvents.put(settings.gammaHistoryTopic(), "gamma-history");
        topicEvents.put(settings.paceTopic(), "pace");
        topicEvents.put(settings.directionalPressureTopic(), "directional-pressure");
        runAssignedCacheConsumer("avro", topicEvents, true, avroCaughtUp);
    }

    private void runJsonStateCacheConsumer() {
        Map<String, String> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.volumeSandwichTopic(), "volume-sandwich");
        runAssignedCacheConsumer("state", topicEvents, false, stateCaughtUp);
    }

    private void runAvroLiveConsumer() {
        Map<String, String> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.displayTopic(), "snapshot");
        topicEvents.put(settings.gammaHistoryTopic(), "gamma-history");
        topicEvents.put(settings.paceTopic(), "pace");
        topicEvents.put(settings.directionalPressureTopic(), "directional-pressure");
        runLiveConsumer("avro-live", topicEvents, true, avroCaughtUp);
    }

    private void runJsonStateLiveConsumer() {
        Map<String, String> topicEvents = new LinkedHashMap<>();
        topicEvents.put(settings.volumeSandwichTopic(), "volume-sandwich");
        runLiveConsumer("state-live", topicEvents, false, stateCaughtUp);
    }

    private void runAlertConsumer() {
        Map<String, String> topicEvents = Map.of(settings.volumeSandwichAlertsTopic(), "volume-sandwich-alert");
        runRetryingConsumer("alerts", retry -> runAlertConsumerOnce(topicEvents), null);
    }

    private void runAlertConsumerOnce(Map<String, String> topicEvents) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(stringConsumerProperties("alerts"))) {
            List<TopicPartition> partitions = partitionsFor(consumer, topicEvents.keySet());
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && !record.value().isBlank()) {
                        broadcast(topicEvents.get(record.topic()), record.value());
                    }
                }
            }
        }
    }

    private void runAssignedCacheConsumer(String name, Map<String, String> topicEvents, boolean avro, AtomicBoolean caughtUpFlag) {
        runRetryingConsumer(
                name,
                retry -> runAssignedCacheConsumerOnce(name, topicEvents, avro, caughtUpFlag),
                () -> markCacheRecovering(caughtUpFlag)
        );
    }

    private void runAssignedCacheConsumerOnce(String name, Map<String, String> topicEvents, boolean avro, AtomicBoolean caughtUpFlag) {
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(avro ? avroConsumerProperties(name) : stringObjectConsumerProperties(name))) {
            List<TopicPartition> partitions = partitionsFor(consumer, topicEvents.keySet());
            consumer.assign(partitions);
            seekToCacheWindow(consumer, partitions);
            Map<TopicPartition, Long> bootstrapEndOffsets = consumer.endOffsets(partitions);
            List<String> events = new ArrayList<>(topicEvents.values());
            boolean live = caughtUp(consumer, bootstrapEndOffsets);
            if (live) {
                markCacheCaughtUp(name, events, caughtUpFlag);
            }
            while (running.get()) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                for (ConsumerRecord<String, Object> record : records) {
                    String event = topicEvents.get(record.topic());
                    String json = avro ? avroJson(record.value()) : stringJson(record.value());
                    if (event == null || json == null || json.isBlank()) {
                        continue;
                    }
                    updateCache(event, record, json);
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

    private void runLiveConsumer(String name, Map<String, String> topicEvents, boolean avro, AtomicBoolean cacheCaughtUpFlag) {
        runRetryingConsumer(
                name,
                retry -> runLiveConsumerOnce(name, topicEvents, avro, cacheCaughtUpFlag, retry),
                null
        );
    }

    private void runLiveConsumerOnce(
            String name,
            Map<String, String> topicEvents,
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
                    String event = topicEvents.get(record.topic());
                    String json = avro ? avroJson(record.value()) : stringJson(record.value());
                    if (event == null || json == null || json.isBlank()) {
                        continue;
                    }
                    String cacheKey = updateCache(event, record, json);
                    if (cacheKey != null && cacheCaughtUpFlag.get()) {
                        enqueuePending(event, cacheKey, json);
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

    private synchronized String updateCache(String event, ConsumerRecord<String, ?> record, String json) {
        String key = record.key() == null || record.key().isBlank()
                ? record.topic() + ":" + record.partition()
                : record.key();
        if ("directional-pressure".equals(event)) {
            key = directionalPressureCacheKey(json, key);
        }
        String versionKey = event + ":" + key;
        long eventTime = cacheTimestamp(record);
        Long previousEventTime = cacheEventTimes.get(versionKey);
        if (previousEventTime != null && previousEventTime > eventTime) {
            return null;
        }
        if (isExpired(eventTime, System.currentTimeMillis())) {
            removeCacheEntry(versionKey);
            return null;
        }
        switch (event) {
            case "snapshot" -> {
                cacheEventTimes.put(versionKey, eventTime);
                snapshots.put(key, json);
                return key;
            }
            case "gamma-history" -> {
                cacheEventTimes.put(versionKey, eventTime);
                gammaHistories.put(key, json);
                return key;
            }
            case "pace" -> {
                cacheEventTimes.put(versionKey, eventTime);
                paces.put(key, json);
                return key;
            }
            case "directional-pressure" -> {
                cacheEventTimes.put(versionKey, eventTime);
                directionalPressures.put(key, json);
                return key;
            }
            case "volume-sandwich" -> {
                cacheEventTimes.put(versionKey, eventTime);
                currentStates.put(versionKey, json);
                return versionKey;
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
        List<CachedEvent> cachedEvents = new ArrayList<>();
        for (String event : events) {
            switch (event) {
                case "snapshot" -> snapshots.entrySet().stream()
                        .filter(entry -> isCacheFresh("snapshot:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("snapshot", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "gamma-history" -> gammaHistories.entrySet().stream()
                        .filter(entry -> isCacheFresh("gamma-history:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("gamma-history", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "pace" -> paces.entrySet().stream()
                        .filter(entry -> isCacheFresh("pace:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("pace", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "directional-pressure" -> directionalPressures.entrySet().stream()
                        .filter(entry -> isCacheFresh("directional-pressure:" + entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("directional-pressure", entry.getValue()))
                        .forEach(cachedEvents::add);
                case "volume-sandwich" -> currentStates.entrySet().stream()
                        .filter(entry -> "volume-sandwich".equals(eventFromCacheKey(entry.getKey())))
                        .filter(entry -> isCacheFresh(entry.getKey(), nowMs))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new CachedEvent("volume-sandwich", entry.getValue()))
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

    private long cacheTimestamp(ConsumerRecord<String, ?> record) {
        long eventTime = record.timestamp();
        return eventTime >= 0 ? eventTime : System.currentTimeMillis();
    }

    private void removeCacheEntry(String versionKey) {
        cacheEventTimes.remove(versionKey);
        if (versionKey.startsWith("snapshot:")) {
            snapshots.remove(versionKey.substring("snapshot:".length()));
        } else if (versionKey.startsWith("gamma-history:")) {
            gammaHistories.remove(versionKey.substring("gamma-history:".length()));
        } else if (versionKey.startsWith("pace:")) {
            paces.remove(versionKey.substring("pace:".length()));
        } else if (versionKey.startsWith("directional-pressure:")) {
            directionalPressures.remove(versionKey.substring("directional-pressure:".length()));
        } else if (versionKey.startsWith("volume-sandwich:")) {
            currentStates.remove(versionKey);
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

    private static String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
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
            case "gamma-history" -> pendingGammaHistories;
            case "pace" -> pendingPaces;
            case "directional-pressure" -> pendingDirectionalPressures;
            case "volume-sandwich" -> pendingVolumeSandwiches;
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
                        new ArrayList<>(pendingGammaHistories.values()),
                        new ArrayList<>(pendingPaces.values()),
                        new ArrayList<>(pendingDirectionalPressures.values()),
                        new ArrayList<>(pendingVolumeSandwiches.values())
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
                + pendingGammaHistories.size()
                + pendingPaces.size()
                + pendingDirectionalPressures.size()
                + pendingVolumeSandwiches.size();
    }

    private void clearPendingLocked() {
        pendingSnapshots.clear();
        pendingGammaHistories.clear();
        pendingPaces.clear();
        pendingDirectionalPressures.clear();
        pendingVolumeSandwiches.clear();
    }

    private String envelopeJson(String event, String json) {
        String payload = json == null || json.isBlank() ? "{}" : json;
        return "{\"type\":\"" + escapeJson(event) + "\",\"data\":" + payload + "}";
    }

    private String uiBatchEnvelopeJson(List<CachedEvent> cachedEvents) {
        List<String> snapshotJsons = new ArrayList<>();
        List<String> gammaHistoryJsons = new ArrayList<>();
        List<String> paceJsons = new ArrayList<>();
        List<String> directionalPressureJsons = new ArrayList<>();
        List<String> volumeSandwichJsons = new ArrayList<>();
        for (CachedEvent cachedEvent : cachedEvents) {
            switch (cachedEvent.event()) {
                case "snapshot" -> snapshotJsons.add(cachedEvent.json());
                case "gamma-history" -> gammaHistoryJsons.add(cachedEvent.json());
                case "pace" -> paceJsons.add(cachedEvent.json());
                case "directional-pressure" -> directionalPressureJsons.add(cachedEvent.json());
                case "volume-sandwich" -> volumeSandwichJsons.add(cachedEvent.json());
                default -> {
                    // This batch protocol only carries latest-state feed events.
                }
            }
        }
        return uiBatchEnvelopeJson(snapshotJsons, gammaHistoryJsons, paceJsons, directionalPressureJsons, volumeSandwichJsons);
    }

    private String uiBatchEnvelopeJson(
            List<String> snapshotJsons,
            List<String> gammaHistoryJsons,
            List<String> paceJsons,
            List<String> directionalPressureJsons,
            List<String> volumeSandwichJsons
    ) {
        return "{"
                + "\"type\":\"ui-batch\","
                + "\"data\":{"
                + "\"sentAtMs\":" + System.currentTimeMillis() + ","
                + "\"cadenceMs\":" + settings.webSocketBatchMs() + ","
                + "\"snapshots\":" + jsonArray(snapshotJsons) + ","
                + "\"gammaHistories\":" + jsonArray(gammaHistoryJsons) + ","
                + "\"paces\":" + jsonArray(paceJsons) + ","
                + "\"directionalPressures\":" + jsonArray(directionalPressureJsons) + ","
                + "\"volumeSandwiches\":" + jsonArray(volumeSandwichJsons)
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
        return "{"
                + "\"status\":\"connected\","
                + "\"time\":\"" + escapeJson(Instant.now().toString()) + "\","
                + "\"avroCaughtUp\":" + avroCaughtUp.get() + ","
                + "\"stateCaughtUp\":" + stateCaughtUp.get() + ","
                + "\"snapshots\":" + snapshots.size() + ","
                + "\"gammaHistories\":" + gammaHistories.size() + ","
                + "\"paces\":" + paces.size() + ","
                + "\"directionalPressures\":" + directionalPressures.size() + ","
                + "\"cacheTtlMs\":" + settings.cacheTtlMs()
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
