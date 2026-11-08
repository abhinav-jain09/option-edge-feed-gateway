package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The break-score relay (break-line-service, design {@code liq-S-break-score-rev13-design.md} sections 4.6 and 14.8, requirement BS13-W-007 and BS13-W-008), modelled on
 * {@link LineHealthRelay}: one read-only tail of the state, events and status topics per gateway replica (read_committed, partition 0 of each, no consumer group, nothing
 * committed), fanned out through the existing authenticated sockets as {@link #STATE_EVENT}, {@link #EVENT_EVENT} and {@link #STATUS_EVENT} and folded into the checkpoint that
 * the hello field {@code breakLine} carries: {@code {status, states, version}}.
 * <p>
 * Every break-score line belongs to a user. There are no system lines. So:
 * <ul>
 * <li>A state or event record goes only to the sockets whose authenticated subject maps to the record's {@code ownerTag} (the owner filter of the line-health user lines, same tag
 * function): live frames through the targeted sink, the hello and the resume answer built per socket with every other owner's states removed before serialization. A state or
 * event record with no owner tag reaches no socket (counted as refused). A socket with no subject receives no line.</li>
 * <li>An event of scope {@code WATERMARK_ADVANCE} or {@code REFUSAL} on no line has no owner and is the service's own bookkeeping: it is not relayed (counted).</li>
 * <li>The status record is global: it carries counts and the data-lag, not a line, and it is the page's heartbeat.</li>
 * <li>A state with {@code lineStatus = REMOVED} is forwarded to its owner (the page clears its tombstone on it) and removes the line from the checkpoint.</li>
 * <li>The Confluent frame must carry the topic's FIXED schema id (state 3902, events 3903, status 3904) before any decoding; the bytes are decoded by the registry through
 * {@link GatewayAvroDeserializer} (tolerant: an unknown id or a corrupt body is counted). The record must be schema version 1 and its key must equal its own identity field.</li>
 * <li>At start (and after a consumer failure) a bounded scan reads the compacted state and status topics from the beginning to the end offsets captured at that instant; only
 * then the live tails start. Events are not replayed (the tail starts at the end): the checkpoint is the recovery.</li>
 * <li>No freshness gate: every record is forwarded in order; the page judges staleness from the status heartbeat.</li>
 * </ul>
 * Disabled ({@code BREAK_LINE_RELAY_ENABLED=false}, the default): no thread, no consumer, nothing cached.
 */
@Component
final class BreakLineRelay {
    static final String STATE_EVENT = "break-line-state";
    static final String EVENT_EVENT = "break-line-event";
    static final String STATUS_EVENT = "break-line-status";
    /** The inbound message type of the page asking for a fresh checkpoint (a gap, a reset). */
    static final String RESUME = "break-line-resume";
    /** The checkpoint of a socket that does not fit the hello is sent right after it, in parts, under this frame name. */
    static final String CHECKPOINT_EVENT = "break-line-checkpoint";
    static final String REMOVED = "REMOVED";

    static final int STATE_ID = 3902, EVENT_ID = 3903, STATUS_ID = 3904;
    static final String STATE_NAME = "com.optionsedge.contracts.breakline.BreakLineState";
    static final String EVENT_NAME = "com.optionsedge.contracts.breakline.BreakLineEvent";
    static final String STATUS_NAME = "com.optionsedge.contracts.breakline.BreakLineStatus";
    /** The key of the one status record. */
    static final String STATUS_KEY = "break-line";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The three subjects the relay reads. */
    enum Kind { STATE, EVENT, STATUS }

    private static final class Lane {
        final AtomicLong accepted = new AtomicLong();
        final AtomicLong schemaIdRefused = new AtomicLong();
    }

    /** Delivers a frame to the sockets of one owner only. */
    @FunctionalInterface
    interface TargetedSink {
        void accept(String ownerTag, String event, String json);
    }

    /** A hello field and the parts that follow it, built for one owner (or for none). */
    private record Checkpoint(String field, List<String> parts) { }

    private final GatewaySettings settings;
    private final BiConsumer<String, String> sink;
    private final TargetedSink targetedSink;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Lane stateLane = new Lane(), eventsLane = new Lane(), statusLane = new Lane();
    private final AtomicLong drops = new AtomicLong();
    private final AtomicLong undecodable = new AtomicLong();
    private final AtomicLong userRecordsRefused = new AtomicLong();
    private final AtomicLong internalEventsNotRelayed = new AtomicLong();
    private final AtomicLong consumerFailures = new AtomicLong();
    /** The folded newest state per line, in arrival order; a REMOVED record deletes its key. */
    private final Map<String, String> states = new LinkedHashMap<>();
    private final Map<String, String> stateOwners = new HashMap<>();
    private String latestStatus;
    private final Map<String, Checkpoint> checkpointMemo = new HashMap<>();
    private long version;
    private volatile Deserializer<Object> decoder;
    private volatile KafkaConsumer<String, byte[]> consumer;
    private final Map<TopicPartition, Long> bootstrapEnd = new HashMap<>();
    private Thread thread;
    private boolean live;

    @org.springframework.beans.factory.annotation.Autowired
    BreakLineRelay(GatewaySettings settings, ObjectProvider<FeedGatewayService> gateway) {
        this(settings, (event, json) -> gateway.getObject().streamBreakLine(event, json),
                (ownerTag, event, json) -> gateway.getObject().streamBreakLineTo(ownerTag, event, json), null);
    }

    /** Test seam: the frames go to the sinks instead of the sockets, the bytes are decoded by {@code decoder}. */
    BreakLineRelay(GatewaySettings settings, BiConsumer<String, String> sink, TargetedSink targetedSink, Deserializer<Object> decoder) {
        this.settings = settings;
        this.sink = sink;
        this.targetedSink = targetedSink;
        this.decoder = decoder;
    }

    @PostConstruct
    void start() {
        if (!settings.breakLineRelayEnabled()) {
            return;
        }
        running.set(true);
        thread = new Thread(this::run, "break-line-relay");
        thread.setDaemon(true);
        thread.start();
    }

    boolean consuming() {
        return running.get();
    }

    private void run() {
        while (running.get()) {
            String stateTopic = settings.breakLineStateTopic(), eventsTopic = settings.breakLineEventsTopic(), statusTopic = settings.breakLineStatusTopic();
            Properties p = new Properties();
            p.put("bootstrap.servers", settings.bootstrapServers());
            p.put("client.id", "break-line-relay");
            p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            p.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            p.put("enable.auto.commit", false);
            p.put("isolation.level", "read_committed");
            p.put("max.poll.records", 500);
            p.put("request.timeout.ms", 10000);
            p.put("default.api.timeout.ms", 10000);
            settings.applyKafkaSecurity(p);
            Map<String, Object> decoderConfig = new HashMap<>();
            decoderConfig.put("schema.registry.url", settings.schemaRegistryUrl());
            decoderConfig.put("specific.avro.reader", false);
            decoderConfig.put("http.connect.timeout.ms", 1000);
            decoderConfig.put("http.read.timeout.ms", 1000);
            decoderConfig.put("max.schemas.per.subject", 64);
            decoderConfig.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, settings.underlyingSpxPriceTopic());
            decoderConfig.put(GatewayAvroDeserializer.TOLERANT_TOPICS, stateTopic + "," + eventsTopic + "," + statusTopic);
            try (var c = new KafkaConsumer<String, byte[]>(p); GatewayAvroDeserializer d = new GatewayAvroDeserializer()) {
                d.configure(decoderConfig, false);
                decoder = d;
                consumer = c;
                TopicPartition state = partitionZero(c, stateTopic), events = partitionZero(c, eventsTopic), status = partitionZero(c, statusTopic);
                resetFold();
                bootstrap(c, state, status);
                c.assign(List.of(state, status, events));
                c.seekToEnd(List.of(events));
                c.seek(state, bootstrapEnd.get(state));
                c.seek(status, bootstrapEnd.get(status));
                while (running.get()) {
                    var records = c.poll(Duration.ofMillis(250));
                    for (var r : records.records(state)) {
                        accept(r.topic(), r.key(), r.value());
                    }
                    for (var r : records.records(events)) {
                        accept(r.topic(), r.key(), r.value());
                    }
                    for (var r : records.records(status)) {
                        accept(r.topic(), r.key(), r.value());
                    }
                }
            } catch (WakeupException stopped) {
                // stop() woke the poll
            } catch (RuntimeException unavailable) {
                consumerFailures.incrementAndGet();
                System.out.println("WARN break-line relay consumer failed, retrying in 2s: " + unavailable);
            } finally {
                consumer = null;
                decoder = null;
            }
            if (running.get()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** The bounded scan of the two compacted topics, beginning to the end offsets captured now; the live tails start only after it. */
    void bootstrap(Consumer<String, byte[]> c, TopicPartition state, TopicPartition status) {
        List<TopicPartition> both = List.of(state, status);
        c.assign(both);
        c.seekToBeginning(both);
        Map<TopicPartition, Long> ends = c.endOffsets(both, Duration.ofSeconds(10));
        bootstrapEnd.clear();
        bootstrapEnd.putAll(ends);
        long deadline = System.currentTimeMillis() + 120_000L;
        while (running.get() && (c.position(state) < ends.get(state) || c.position(status) < ends.get(status))) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("break-line bootstrap scan did not finish in 120 s");
            }
            var records = c.poll(Duration.ofMillis(250));
            for (var r : records.records(state)) {
                if (r.offset() < ends.get(state)) {   // a record at or beyond the captured end belongs to the live tail, which starts exactly there
                    accept(r.topic(), r.key(), r.value());
                }
            }
            for (var r : records.records(status)) {
                if (r.offset() < ends.get(status)) {
                    accept(r.topic(), r.key(), r.value());
                }
            }
        }
        goLive();
    }

    private static TopicPartition partitionZero(Consumer<String, byte[]> c, String topic) {
        var partitions = c.partitionsFor(topic, Duration.ofSeconds(10));
        if (partitions == null || partitions.size() != 1) {
            throw new IllegalStateException(topic + " must have exactly one partition, found " + (partitions == null ? "none" : partitions.size()));
        }
        return new TopicPartition(topic, 0);
    }

    private synchronized void resetFold() {
        states.clear();
        stateOwners.clear();
        latestStatus = null;
        checkpointMemo.clear();
        live = false;
    }

    synchronized void goLive() {
        live = true;
        version++;
        checkpointMemo.clear();
    }

    private Kind kindOf(String topic) {
        if (topic.equals(settings.breakLineStateTopic())) {
            return Kind.STATE;
        }
        if (topic.equals(settings.breakLineEventsTopic())) {
            return Kind.EVENT;
        }
        if (topic.equals(settings.breakLineStatusTopic())) {
            return Kind.STATUS;
        }
        return null;
    }

    private Lane lane(Kind kind) {
        return kind == Kind.STATE ? stateLane : kind == Kind.EVENT ? eventsLane : statusLane;
    }

    static int expectedId(Kind kind) {
        return kind == Kind.STATE ? STATE_ID : kind == Kind.EVENT ? EVENT_ID : STATUS_ID;
    }

    static String expectedName(Kind kind) {
        return kind == Kind.STATE ? STATE_NAME : kind == Kind.EVENT ? EVENT_NAME : STATUS_NAME;
    }

    /** The schema id of a Confluent frame, or -1 when the bytes are not framed. */
    static int frameId(byte[] value) {
        if (value == null || value.length < 5 || value[0] != 0) {
            return -1;
        }
        return ByteBuffer.wrap(value, 1, 4).getInt();
    }

    /** One raw record: the frame's schema id, the registry decode, the record checks, the fold and the fan-out. */
    synchronized void accept(String topic, String key, byte[] value) {
        Kind kind = kindOf(topic);
        if (kind == null || value == null) {
            drops.incrementAndGet();   // a foreign topic, or a tombstone
            return;
        }
        Lane lane = lane(kind);
        if (frameId(value) != expectedId(kind)) {
            lane.schemaIdRefused.incrementAndGet();
            return;
        }
        Deserializer<Object> d = decoder;
        Object decoded = d == null ? GatewayAvroDeserializer.UNDECODABLE : d.deserialize(topic, value);
        if (decoded == GatewayAvroDeserializer.UNDECODABLE) {
            undecodable.incrementAndGet();
            return;
        }
        ObjectNode node = render(kind, key, decoded);
        if (node == null) {
            drops.incrementAndGet();
            return;
        }
        if (kind == Kind.STATUS) {
            lane.accepted.incrementAndGet();
            latestStatus = withCached(node, false);
            version++;
            checkpointMemo.clear();
            if (live) {
                sink.accept(STATUS_EVENT, latestStatus);
            }
            return;
        }
        String owner = node.hasNonNull("ownerTag") ? node.get("ownerTag").asText() : null;
        if (owner == null || owner.isBlank()) {
            if (kind == Kind.EVENT && !node.hasNonNull("lineId")) {
                internalEventsNotRelayed.incrementAndGet();   // the service's own bookkeeping (a watermark advance, a refusal of an input record)
            } else {
                userRecordsRefused.incrementAndGet();   // a line record with no owner: fail closed, it reaches no socket
            }
            return;
        }
        lane.accepted.incrementAndGet();
        if (kind == Kind.STATE) {
            if (REMOVED.equals(node.path("lineStatus").asText())) {
                states.remove(key);
                stateOwners.remove(key);
            } else {
                states.put(key, node.toString());
                stateOwners.put(key, owner);
            }
            version++;
            checkpointMemo.clear();
        }
        if (live) {
            targetedSink.accept(owner, kind == Kind.STATE ? STATE_EVENT : EVENT_EVENT, withCached(node, false));
        }
    }

    /** The bytes of the hello field around the status and the states: its member names and the version. */
    static final long ENVELOPE_BYTES = 128;

    private Checkpoint checkpointFor(String ownerTag) {
        String memoKey = ownerTag == null ? "" : "#" + ownerTag;
        Checkpoint memo = checkpointMemo.get(memoKey);
        if (memo != null) {
            return memo;
        }
        long max = settings.breakLineHelloMaxBytes();
        String status = latestStatus == null ? "null" : withoutCached(latestStatus);
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        long currentBytes = 0, total = 0;
        int inCurrent = 0;
        if (ownerTag != null) {   // a socket with no subject receives no line
            for (Map.Entry<String, String> entry : states.entrySet()) {
                if (!ownerTag.equals(stateOwners.get(entry.getKey()))) {
                    continue;   // another user's line
                }
                String st = entry.getValue();
                int bytes = st.getBytes(StandardCharsets.UTF_8).length + 1;
                if (inCurrent > 0 && currentBytes + bytes > max) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                    currentBytes = 0;
                    inCurrent = 0;
                }
                current.append(inCurrent == 0 ? "" : ",").append(st);
                currentBytes += bytes;
                inCurrent++;
                total += bytes;
            }
        }
        parts.add(current.toString());
        long statusBytes = status.getBytes(StandardCharsets.UTF_8).length;
        Checkpoint built;
        if (total + statusBytes + ENVELOPE_BYTES <= max) {
            built = new Checkpoint("{\"status\":" + status + ",\"states\":[" + parts.get(0) + "],\"version\":" + version + "}", List.of());
        } else {
            List<String> framed = new ArrayList<>();
            for (int k = 0; k < parts.size(); k++) {
                framed.add("{\"part\":" + k + ",\"parts\":" + parts.size() + ",\"version\":" + version + ",\"states\":[" + parts.get(k) + "]}");
            }
            built = new Checkpoint("{\"status\":" + status + ",\"states\":null,\"parts\":" + parts.size() + ",\"version\":" + version + "}", framed);
        }
        checkpointMemo.put(memoKey, built);
        return built;
    }

    /**
     * The checkpoint {@code {status, states, version}} as the hello field value for the socket of {@code ownerTag} (null: a socket without a subject, no lines). When the states do
     * not fit {@link GatewaySettings#breakLineHelloMaxBytes()} the field carries {@code states:null} and {@code parts:K} and the states follow the hello in {@code K} frames
     * {@link #CHECKPOINT_EVENT} that share its {@code version}: no line is ever dropped to make a hello fit. Another owner's lines are never in it.
     */
    synchronized String helloField(String ownerTag) {
        return checkpointFor(ownerTag).field();
    }

    /** The checkpoint parts that follow a hello whose field is {@code states:null}, for the same owner; empty when the hello carried the states inline. */
    synchronized List<String> checkpointParts(String ownerTag) {
        return checkpointFor(ownerTag).parts();
    }

    /** The answer to {@link #RESUME}: the same checkpoint under its field name. */
    String resumeReply(String ownerTag) {
        return "{\"breakLine\":" + helloField(ownerTag) + "}";
    }

    /** The node for a record that passes the boundary, with null members omitted, else null. */
    static ObjectNode render(Kind kind, String key, Object value) {
        if (!(value instanceof GenericRecord r) || !expectedName(kind).equals(r.getSchema().getFullName())) {
            return null;
        }
        try {
            if (!Integer.valueOf(1).equals(r.get("schemaVersion"))) {
                return null;
            }
            String expectedKey = kind == Kind.STATUS ? STATUS_KEY : String.valueOf(r.get(kind == Kind.STATE ? "lineId" : "eventId"));
            if (key == null || key.isBlank() || !key.equals(expectedKey)) {
                return null;
            }
            if (kind == Kind.STATE && (r.get("lineStatus") == null || !(r.get("eventSequence") instanceof Long))) {
                return null;   // a state record always says whether the line is active or removed and carries its sequence
            }
            if (kind == Kind.STATUS && !(r.get("statusEpochMillis") instanceof Long)) {
                return null;
            }
            String json = AvroFrameJson.render(r, TaRelay.FRAME_MAX_BYTES, false);
            if (json == null) {
                return null;
            }
            JsonNode tree = JSON.readTree(json);
            if (!(tree instanceof ObjectNode object)) {
                return null;
            }
            stripNulls(object);
            return object;
        } catch (Exception invalid) {
            return null;   // a field absent from this writer's schema, or unrenderable: refused, never repaired
        }
    }

    private static void stripNulls(JsonNode node) {
        if (node instanceof ObjectNode o) {
            for (Iterator<Map.Entry<String, JsonNode>> it = o.fields(); it.hasNext();) {
                var e = it.next();
                if (e.getValue().isNull()) {
                    it.remove();
                } else {
                    stripNulls(e.getValue());
                }
            }
        } else if (node instanceof ArrayNode a) {
            for (JsonNode child : a) {
                stripNulls(child);
            }
        }
    }

    static String withCached(ObjectNode node, boolean cached) {
        ObjectNode copy = node.deepCopy();
        copy.put("cached", cached);
        return copy.toString();
    }

    private static String withoutCached(String json) {
        try {
            ObjectNode o = (ObjectNode) JSON.readTree(json);
            o.remove("cached");
            return o.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    String metrics() {
        String p = "gateway_break_line_";
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP ").append(p).append("enabled Whether the break-line relay is enabled.\n# TYPE ").append(p).append("enabled gauge\n").append(p).append("enabled ")
                .append(settings.breakLineRelayEnabled() ? 1 : 0).append('\n');
        String[] names = {"state", "events", "status"};
        Lane[] lanes = {stateLane, eventsLane, statusLane};
        for (int i = 0; i < names.length; i++) {
            sb.append("# TYPE ").append(p).append(names[i]).append("_accepted_total counter\n").append(p).append(names[i]).append("_accepted_total ").append(lanes[i].accepted.get()).append('\n');
            sb.append("# TYPE ").append(p).append(names[i]).append("_schema_id_refused_total counter\n").append(p).append(names[i]).append("_schema_id_refused_total ")
                    .append(lanes[i].schemaIdRefused.get()).append('\n');
        }
        sb.append("# TYPE ").append(p).append("drops_total counter\n").append(p).append("drops_total ").append(drops.get()).append('\n');
        sb.append("# TYPE ").append(p).append("undecodable_total counter\n").append(p).append("undecodable_total ").append(undecodable.get()).append('\n');
        sb.append("# TYPE ").append(p).append("user_records_refused_total counter\n").append(p).append("user_records_refused_total ").append(userRecordsRefused.get()).append('\n');
        sb.append("# TYPE ").append(p).append("internal_events_not_relayed_total counter\n").append(p).append("internal_events_not_relayed_total ").append(internalEventsNotRelayed.get()).append('\n');
        sb.append("# TYPE ").append(p).append("consumer_failures_total counter\n").append(p).append("consumer_failures_total ").append(consumerFailures.get()).append('\n');
        return sb.toString();
    }

    long userRecordsRefusedForTest() {
        return userRecordsRefused.get();
    }

    long internalEventsNotRelayedForTest() {
        return internalEventsNotRelayed.get();
    }

    long dropsForTest() {
        return drops.get();
    }

    long schemaIdRefusedForTest(Kind kind) {
        return lane(kind).schemaIdRefused.get();
    }

    synchronized int foldedLinesForTest() {
        return states.size();
    }

    @PreDestroy
    void stop() {
        running.set(false);
        var c = consumer;
        if (c != null) {
            c.wakeup();
        }
        if (thread != null) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
