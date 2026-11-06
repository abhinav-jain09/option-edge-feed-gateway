package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.optionsedge.contracts.linehealth.LineHealthSchemas;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayDeque;
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
 * The line-health relay (Line Health requirement LH-O-6, design section 7), modelled on {@link EsLiquidityWallRelay}: one read-only tail of the state,
 * events, voice and status topics per gateway replica (read_committed, partition 0 of each, no consumer group, nothing committed), fanned out through the
 * existing authenticated sockets as the global frames {@link #STATE_EVENT}, {@link #EVENT_EVENT}, {@link #VOICE_EVENT} and {@link #STATUS_EVENT}, and
 * folded into the checkpoint that the hello field {@code lineHealth} carries: {@code {status, states, watermark}}.
 * <p>
 * Boundary per record, in order: the Confluent frame must carry the topic's FIXED schema id (3701 state, 3702 events, 3703 voice, 3705 status;
 * {@link LineHealthSchemas#assertFramedAs}) before any decoding; the bytes are decoded by the registry through {@link GatewayAvroDeserializer} (tolerant:
 * an unknown id or a corrupt body is counted); then the record checks of {@link #render}. Every rendered record omits its null fields (an absent member is
 * a null), so a hello of several hundred lines stays small.
 * <ul>
 * <li><b>Bootstrap and coherence.</b> At start (and after a consumer failure) a bounded scan reads the compacted state and status topics from their
 * beginning to the end offsets captured at that instant and folds every state key (a {@code REMOVED} record deletes its key); only then the live tails start.
 * When a status record of second N is read, the relay captures the then-current end offset E of the state topic and holds the status frame and the hello
 * checkpoint back until its state consumer has reached E; the state records and the status record of one second are written in one transaction, so the
 * states of that second lie below E and a page never sees a status ahead of the states it counts. The watermark is {@code max(systemRecordCount, the
 * largest systemSequence in the folded map)}. No broker offset is ever put in a frame.</li>
 * <li><b>A checkpoint may hold newer states than its status.</b> The state records of every fetched batch are folded before the status records of that batch, so when
 * several seconds are fetched together the checkpoint of status N can include the states of N+1; that is the "state record between two status records" case of
 * the design, and it is correct because the watermark is the larger of the status count and the largest system sequence in the map, and the page continues
 * from the watermark (a state at or below it is a duplicate).</li>
 * <li><b>No replay of events or voice.</b> Events and voice are forwarded as they arrive (the tail starts at the end): a warning that a disconnected page
 * missed is not replayed (requirement LH-W-5); the hello's current state of every line is the recovery.</li>
 * <li><b>Records of a user line</b> (a non-null {@code ownerTag}) go only to the sockets whose authenticated subject maps to that tag (requirement LH-O-6, LH-N-6; the
 * owner filter of milestone M5, no fallback): live frames through the targeted sink, the hello and the resume answer built per socket with the other owners' states
 * removed before serialization. A record that says it is a user line but carries no tag is inconsistent and reaches no socket (counted as refused).</li>
 * <li>No freshness gate: every record is forwarded in order; the page judges staleness from the status heartbeat (LH-D-7).</li>
 * </ul>
 * Disabled ({@code LINE_HEALTH_RELAY_ENABLED=false}, the default): no thread, no consumer, nothing cached.
 */
@Component
final class LineHealthRelay {
    static final String STATE_EVENT = "line-health-state";
    static final String EVENT_EVENT = "line-health-event";
    static final String VOICE_EVENT = "line-health-voice";
    static final String STATUS_EVENT = "line-health-status";
    /** The inbound message type of the page asking for a fresh checkpoint (a gap, a reset). */
    static final String RESUME = "line-health-resume";
    /** The checkpoint of a socket that does not fit the hello is sent right after it, in parts, under this frame name. */
    static final String CHECKPOINT_EVENT = "line-health-checkpoint";
    static final String REMOVED = "REMOVED";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One topic's counters. */
    private static final class Lane {
        final AtomicLong accepted = new AtomicLong();
        final AtomicLong schemaIdRefused = new AtomicLong();
    }

    /** A status record read but not yet released: its frame, its sequence, and the end offset of the state topic it waits for (-1 until captured). */
    private static final class HeldStatus {
        final String json;
        final long count;
        long waitsForStateOffset = -1;

        HeldStatus(String json, long count) {
            this.json = json;
            this.count = count;
        }
    }

    private final GatewaySettings settings;
    private final BiConsumer<String, String> sink;
    /** The targeted form of the sink: {@code (ownerTag, event, json)} reaches only the sockets of that owner. */
    private final TargetedSink targetedSink;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Lane stateLane = new Lane(), eventsLane = new Lane(), voiceLane = new Lane(), statusLane = new Lane();
    private final AtomicLong drops = new AtomicLong();
    private final AtomicLong undecodable = new AtomicLong();
    private final AtomicLong userRecordsRefused = new AtomicLong();
    private final AtomicLong consumerFailures = new AtomicLong();
    /** The folded latest state per line key, in arrival order; a REMOVED record deletes its key. */
    private final Map<String, String> states = new LinkedHashMap<>();
    private final Map<String, Long> stateSequences = new HashMap<>();
    /** The owner tag of each folded user-line state (a system line has no entry). */
    private final Map<String, String> stateOwners = new HashMap<>();
    private Map<String, String> releasedOwners = Map.of();
    private final ArrayDeque<HeldStatus> held = new ArrayDeque<>();
    /** The released checkpoint: the status frame, a copy of the folded states, the sequence of the status. */
    private String releasedStatus;
    private Map<String, String> releasedStates = Map.of();
    private long releasedCount = -1;
    private long releasedWatermark = -1;
    private final Map<String, Checkpoint> checkpointMemo = new HashMap<>();
    private volatile Deserializer<Object> decoder;
    private volatile KafkaConsumer<String, byte[]> consumer;
    private Thread thread;
    private boolean live;

    @org.springframework.beans.factory.annotation.Autowired
    LineHealthRelay(GatewaySettings settings, ObjectProvider<FeedGatewayService> gateway) {
        this(settings, (event, json) -> gateway.getObject().streamLineHealth(event, json),
                (ownerTag, event, json) -> gateway.getObject().streamLineHealthTo(ownerTag, event, json), null);
    }

    /** Delivers a frame to the sockets of one owner only. */
    @FunctionalInterface
    interface TargetedSink {
        void accept(String ownerTag, String event, String json);
    }

    /** A hello field and the parts that follow it, built for one owner (or for none). */
    private record Checkpoint(String field, List<String> parts) { }

    /** Test seam: the frames go to {@code sink} instead of the sockets, the bytes are decoded by {@code decoder}. */
    LineHealthRelay(GatewaySettings settings, BiConsumer<String, String> sink, Deserializer<Object> decoder) {
        this(settings, sink, (ownerTag, event, json) -> { }, decoder);
    }

    LineHealthRelay(GatewaySettings settings, BiConsumer<String, String> sink, TargetedSink targetedSink, Deserializer<Object> decoder) {
        this.settings = settings;
        this.sink = sink;
        this.targetedSink = targetedSink;
        this.decoder = decoder;
    }

    @PostConstruct
    void start() {
        if (!settings.lineHealthRelayEnabled()) {
            return;
        }
        running.set(true);
        thread = new Thread(this::run, "line-health-relay");
        thread.setDaemon(true);
        thread.start();
    }

    /** Test seam: the relay is running (a bootstrap scan loops while it is). */
    void runningForTest() {
        running.set(true);
    }

    Map<TopicPartition, Long> bootstrapEndsForTest() {
        return bootstrapEnd;
    }

    boolean consuming() {
        return running.get();
    }

    private void run() {
        while (running.get()) {
            String stateTopic = settings.lineHealthStateTopic(), eventsTopic = settings.lineHealthEventsTopic();
            String voiceTopic = settings.lineHealthVoiceTopic(), statusTopic = settings.lineHealthStatusTopic();
            Properties p = new Properties();
            p.put("bootstrap.servers", settings.bootstrapServers());
            p.put("client.id", "line-health-relay");
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
            decoderConfig.put(GatewayAvroDeserializer.TOLERANT_TOPICS, stateTopic + "," + eventsTopic + "," + voiceTopic + "," + statusTopic);
            try (var c = new KafkaConsumer<String, byte[]>(p); GatewayAvroDeserializer d = new GatewayAvroDeserializer()) {
                d.configure(decoderConfig, false);
                decoder = d;
                consumer = c;
                TopicPartition state = partitionZero(c, stateTopic), events = partitionZero(c, eventsTopic);
                TopicPartition voice = partitionZero(c, voiceTopic), status = partitionZero(c, statusTopic);
                resetFold();
                bootstrap(c, state, status);
                c.assign(List.of(state, status, events, voice));
                c.seekToEnd(List.of(events, voice));
                // state and status continue from their own positions: assign() keeps none, so seek to the end offsets the bootstrap reached
                c.seek(state, bootstrapEnd.get(state));
                c.seek(status, bootstrapEnd.get(status));
                while (running.get()) {
                    var records = c.poll(Duration.ofMillis(250));
                    for (var r : records.records(state)) {
                        accept(r.topic(), r.key(), r.value());
                    }
                    for (var r : records.records(status)) {
                        accept(r.topic(), r.key(), r.value());
                    }
                    for (var r : records.records(events)) {
                        accept(r.topic(), r.key(), r.value());
                    }
                    for (var r : records.records(voice)) {
                        accept(r.topic(), r.key(), r.value());
                    }
                    if (epochChanged) {
                        throw new IllegalStateException("status epoch changed: the fold is rebuilt from the topics");
                    }
                    if (hasHeldWithoutBound()) {
                        captureBound(c.endOffsets(List.of(state), Duration.ofSeconds(10)).get(state));
                    }
                    release(c.position(state));
                }
            } catch (WakeupException stopped) {
                // stop() woke the poll
            } catch (RuntimeException unavailable) {
                consumerFailures.incrementAndGet();
                System.out.println("WARN line-health relay consumer failed, retrying in 2s: " + unavailable);
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

    private final Map<TopicPartition, Long> bootstrapEnd = new HashMap<>();

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
                throw new IllegalStateException("line-health bootstrap scan did not finish in 120 s");
            }
            var records = c.poll(Duration.ofMillis(250));
            for (var r : records.records(state)) {
                if (r.offset() < ends.get(state)) {   // a record at or beyond the captured end belongs to the live tail, which starts exactly at that end
                    accept(r.topic(), r.key(), r.value());
                }
            }
            for (var r : records.records(status)) {
                if (r.offset() < ends.get(status)) {
                    accept(r.topic(), r.key(), r.value());
                }
            }
        }
        // the whole scan is one checkpoint: the last status read, with the states folded up to the state end captured with it
        captureBound(ends.get(state));
        release(ends.get(state));
        live = true;
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
        stateSequences.clear();
        stateOwners.clear();
        releasedOwners = Map.of();
        held.clear();
        releasedStatus = null;
        releasedStates = Map.of();
        releasedCount = -1;
        releasedWatermark = -1;
        checkpointMemo.clear();
        live = false;
        epochSeen = null;
        epochChanged = false;
    }

    private LineHealthSchemas.Subject subjectOf(String topic) {
        if (topic.equals(settings.lineHealthStateTopic())) {
            return LineHealthSchemas.STATE;
        }
        if (topic.equals(settings.lineHealthEventsTopic())) {
            return LineHealthSchemas.EVENT;
        }
        if (topic.equals(settings.lineHealthVoiceTopic())) {
            return LineHealthSchemas.VOICE;
        }
        if (topic.equals(settings.lineHealthStatusTopic())) {
            return LineHealthSchemas.STATUS;
        }
        return null;
    }

    private Lane lane(LineHealthSchemas.Subject subject) {
        return subject == LineHealthSchemas.STATE ? stateLane : subject == LineHealthSchemas.EVENT ? eventsLane : subject == LineHealthSchemas.VOICE ? voiceLane : statusLane;
    }

    /** One raw record: the frame's schema id, the registry decode, the record checks, the fold and the fan-out. */
    synchronized void accept(String topic, String key, byte[] value) {
        LineHealthSchemas.Subject subject = subjectOf(topic);
        if (subject == null || value == null) {
            drops.incrementAndGet();   // a foreign topic, or a tombstone
            return;
        }
        Lane lane = lane(subject);
        try {
            LineHealthSchemas.assertFramedAs(value, subject);
        } catch (IllegalStateException wrongId) {
            lane.schemaIdRefused.incrementAndGet();
            return;
        }
        Deserializer<Object> d = decoder;
        Object decoded = d == null ? GatewayAvroDeserializer.UNDECODABLE : d.deserialize(topic, value);
        if (decoded == GatewayAvroDeserializer.UNDECODABLE) {
            undecodable.incrementAndGet();
            return;
        }
        ObjectNode node = render(subject, key, decoded);
        if (node == null) {
            drops.incrementAndGet();
            return;
        }
        String owner = null;
        if (subject != LineHealthSchemas.STATUS && isUserOwned(node)) {
            owner = node.hasNonNull("ownerTag") ? node.get("ownerTag").asText() : null;
            if (owner == null || owner.isBlank()) {
                userRecordsRefused.incrementAndGet();   // says it is a user line but names no owner: fail closed, it reaches no socket
                return;
            }
        }
        lane.accepted.incrementAndGet();
        if (subject == LineHealthSchemas.STATE) {
            if (REMOVED.equals(node.path("state").asText())) {
                states.remove(key);
                stateSequences.remove(key);
                stateOwners.remove(key);
            } else {
                states.put(key, node.toString());
                stateSequences.put(key, node.path("systemSequence").asLong(-1));
                if (owner != null) {
                    stateOwners.put(key, owner);
                } else {
                    stateOwners.remove(key);
                }
            }
            if (live) {
                emit(owner, STATE_EVENT, withCached(node, false));
            }
        } else if (subject == LineHealthSchemas.STATUS) {
            noteEpoch(node);
            held.addLast(new HeldStatus(withCached(node, false), node.path("systemRecordCount").asLong()));
        } else if (live) {
            emit(owner, subject == LineHealthSchemas.EVENT ? EVENT_EVENT : VOICE_EVENT, withCached(node, false));
        }
    }

    /** A system record goes to every socket; a user record only to the sockets of its owner. */
    private void emit(String owner, String event, String json) {
        if (owner == null) {
            sink.accept(event, json);
        } else {
            targetedSink.accept(owner, event, json);
        }
    }

    /** The status epoch (when the status carries one): a change is a continuity reset of the sequences, so the fold is discarded and rebuilt from the topics. */
    private void noteEpoch(JsonNode status) {
        if (!status.hasNonNull("statusEpoch")) {
            return;
        }
        long epoch = status.get("statusEpoch").asLong();
        // the bootstrap scan reads every epoch the compacted topic still holds: only a change seen on the live tail is a continuity reset
        if (live && epochSeen != null && epochSeen != epoch) {
            epochChanged = true;
        }
        epochSeen = epoch;
    }

    private Long epochSeen;
    private volatile boolean epochChanged;

    boolean epochChangedForTest() {
        return epochChanged;
    }

    private synchronized boolean hasHeldWithoutBound() {
        for (HeldStatus h : held) {
            if (h.waitsForStateOffset < 0) {
                return true;
            }
        }
        return false;
    }

    /** The state topic's end offset captured now becomes the offset every unbound held status waits for. */
    synchronized void captureBound(long stateEndOffset) {
        for (HeldStatus h : held) {
            if (h.waitsForStateOffset < 0) {
                h.waitsForStateOffset = stateEndOffset;
            }
        }
    }

    /** Releases, in order, every held status whose awaited state offset the state consumer has reached: the status frame is forwarded and the checkpoint cached. */
    synchronized void release(long statePosition) {
        while (!held.isEmpty() && held.peekFirst().waitsForStateOffset >= 0 && held.peekFirst().waitsForStateOffset <= statePosition) {
            HeldStatus h = held.pollFirst();
            long watermark = h.count;
            for (long sequence : stateSequences.values()) {
                watermark = Math.max(watermark, sequence);
            }
            releasedStatus = h.json;
            releasedStates = new LinkedHashMap<>(states);
            releasedOwners = new HashMap<>(stateOwners);
            releasedCount = h.count;
            releasedWatermark = watermark;
            releaseSerial++;
            checkpointMemo.clear();
            if (live) {
                sink.accept(STATUS_EVENT, h.json);
                recordAge(h.json);
            }
        }
    }

    /** The bytes of the hello field around the status and the states: its member names, the watermark and the version. */
    static final long ENVELOPE_BYTES = 128;
    private long releaseSerial;

    /**
     * Builds, once per release and owner, the inline field and the parts of the checkpoint, so that a hello and the parts that follow it come from the same release (they
     * share {@code version}). {@code ownerTag} null means a socket without a subject: it receives system lines only. The states of every other owner are removed before
     * anything is serialized.
     */
    private Checkpoint checkpointFor(String ownerTag) {
        String memoKey = ownerTag == null ? "" : "#" + ownerTag;
        Checkpoint memo = checkpointMemo.get(memoKey);
        if (memo != null) {
            return memo;
        }
        long max = settings.lineHealthHelloMaxBytes();
        String status = withoutCached(releasedStatus);
        List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        long currentBytes = 0, total = 0;
        int inCurrent = 0;
        for (Map.Entry<String, String> entry : releasedStates.entrySet()) {
            String owner = releasedOwners.get(entry.getKey());
            if (owner != null && !owner.equals(ownerTag)) {
                continue;   // another user's line
            }
            String st = entry.getValue();
            int bytes = st.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;   // the state and its separator, in UTF-8 bytes
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
        parts.add(current.toString());
        long statusBytes = status.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        Checkpoint built;
        if (total + statusBytes + ENVELOPE_BYTES <= max) {
            built = new Checkpoint("{\"status\":" + status + ",\"states\":[" + parts.get(0) + "],\"watermark\":" + releasedWatermark + ",\"version\":" + releaseSerial + "}", List.of());
        } else {
            List<String> framed = new java.util.ArrayList<>();
            for (int k = 0; k < parts.size(); k++) {
                framed.add("{\"part\":" + k + ",\"parts\":" + parts.size() + ",\"version\":" + releaseSerial + ",\"watermark\":" + releasedWatermark + ",\"states\":[" + parts.get(k) + "]}");
            }
            built = new Checkpoint("{\"status\":" + status + ",\"states\":null,\"parts\":" + parts.size() + ",\"watermark\":" + releasedWatermark + ",\"version\":" + releaseSerial + "}", framed);
        }
        checkpointMemo.put(memoKey, built);
        return built;
    }

    /**
     * The checkpoint {@code {status, states, watermark, version}} as the hello field value for the socket of {@code ownerTag} (null: a socket without a subject, system lines
     * only). When the states do not fit {@link GatewaySettings#lineHealthHelloMaxBytes()} the field carries {@code states:null} and {@code parts:K}, and the states follow the
     * hello in {@code K} frames {@link #CHECKPOINT_EVENT} that share its {@code version}: no line is ever dropped to make a hello fit. Another owner's lines are never in it.
     */
    synchronized String helloField(String ownerTag) {
        if (releasedStatus == null) {
            return "{\"status\":null,\"states\":[],\"watermark\":null}";
        }
        return checkpointFor(ownerTag).field();
    }

    /** The checkpoint parts that follow a hello whose field is {@code states:null}, for the same owner; empty when the hello carried the states inline. */
    synchronized List<String> checkpointParts(String ownerTag) {
        if (releasedStatus == null) {
            return List.of();
        }
        return checkpointFor(ownerTag).parts();
    }

    private final AtomicLong[] statusAgeBuckets = {new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};

    /** LH-N-2 telemetry: the age of a status record (gateway clock minus its second's end) at the moment it is forwarded, counted in buckets under 1 s, 2 s, 3 s and 3 s or more. */
    private void recordAge(String statusJson) {
        try {
            long asOf = JSON.readTree(statusJson).path("asOfMs").asLong();
            long age = System.currentTimeMillis() - asOf;
            statusAgeBuckets[age < 1000 ? 0 : age < 2000 ? 1 : age < 3000 ? 2 : 3].incrementAndGet();
        } catch (Exception ignored) {
            // telemetry only
        }
    }

    /** Test conveniences: the checkpoint of a socket without a subject (system lines only). Production code always passes the socket's own tag. */
    synchronized String helloField() { return helloField(null); }

    synchronized List<String> checkpointParts() { return checkpointParts(null); }

    String resumeReply() { return resumeReply(null); }

    /** The answer to {@link #RESUME}: the same checkpoint under its field name. */
    String resumeReply(String ownerTag) {
        return "{\"lineHealth\":" + helloField(ownerTag) + "}";
    }

    synchronized long releasedWatermarkForTest() {
        return releasedWatermark;
    }

    synchronized long releasedCountForTest() {
        return releasedCount;
    }

    /**
     * Fail closed: a record is user-owned when it carries an owner tag, says it is a user line ({@code isUser}), or has a kind of the USER_ family, at the top level or in the
     * snapshot of an event. Any of the three keeps it from every socket (a record that says "user" without a tag is inconsistent and is refused as well).
     */
    static boolean isUserOwned(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (node.hasNonNull("ownerTag") || node.path("isUser").asBoolean(false) || node.path("kind").asText("").startsWith("USER_")) {
            return true;
        }
        return isUserOwned(node.get("snapshot"));
    }

    /** The node for a record that passes the boundary, with null members omitted, else null. */
    static ObjectNode render(LineHealthSchemas.Subject subject, String key, Object value) {
        if (!(value instanceof GenericRecord r) || !subject.name().equals(r.getSchema().getFullName())) {
            return null;
        }
        try {
            if (!Integer.valueOf(1).equals(r.get("schemaVersion")) || !(r.get("asOfMs") instanceof Long asOf) || asOf <= 0) {
                return null;
            }
            String idField = subject == LineHealthSchemas.STATE ? "lineId" : subject == LineHealthSchemas.STATUS ? null : "eventId";
            String expectedKey = idField == null ? "SPX" : String.valueOf(r.get(idField));
            if (key == null || !key.equals(expectedKey) || key.isBlank()) {
                return null;
            }
            // the semantic fields each subject needs: none is synthesized from another (a made-up count would defeat the page's gap detection)
            if (subject == LineHealthSchemas.STATUS && (!(r.get("stateSequence") instanceof Long) || !(r.get("systemRecordCount") instanceof Long) || !(r.get("statusEpoch") instanceof Long))) {
                return null;
            }
            if (subject == LineHealthSchemas.STATE && r.get("ownerTag") == null && !(r.get("systemSequence") instanceof Long)) {
                return null;   // a system line always carries its sequence, a removal record included
            }
            if (subject == LineHealthSchemas.VOICE && (r.get("text") == null || r.get("text").toString().isBlank())) {
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
        String p = "gateway_line_health_";
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP ").append(p).append("enabled Whether the line-health relay is enabled.\n# TYPE ").append(p).append("enabled gauge\n").append(p).append("enabled ")
                .append(settings.lineHealthRelayEnabled() ? 1 : 0).append('\n');
        String[] names = {"state", "events", "voice", "status"};
        Lane[] lanes = {stateLane, eventsLane, voiceLane, statusLane};
        for (int i = 0; i < names.length; i++) {
            sb.append("# TYPE ").append(p).append(names[i]).append("_accepted_total counter\n").append(p).append(names[i]).append("_accepted_total ").append(lanes[i].accepted.get()).append('\n');
            sb.append("# TYPE ").append(p).append(names[i]).append("_schema_id_refused_total counter\n").append(p).append(names[i]).append("_schema_id_refused_total ")
                    .append(lanes[i].schemaIdRefused.get()).append('\n');
        }
        String[] ages = {"lt_1s", "lt_2s", "lt_3s", "ge_3s"};
        for (int i = 0; i < ages.length; i++) {
            sb.append("# TYPE ").append(p).append("status_forward_age_").append(ages[i]).append("_total counter\n").append(p).append("status_forward_age_").append(ages[i]).append("_total ")
                    .append(statusAgeBuckets[i].get()).append('\n');
        }
        sb.append("# TYPE ").append(p).append("drops_total counter\n").append(p).append("drops_total ").append(drops.get()).append('\n');
        sb.append("# TYPE ").append(p).append("undecodable_total counter\n").append(p).append("undecodable_total ").append(undecodable.get()).append('\n');
        sb.append("# TYPE ").append(p).append("user_records_refused_total counter\n").append(p).append("user_records_refused_total ").append(userRecordsRefused.get()).append('\n');
        sb.append("# TYPE ").append(p).append("consumer_failures_total counter\n").append(p).append("consumer_failures_total ").append(consumerFailures.get()).append('\n');
        return sb.toString();
    }

    long userRecordsRefusedForTest() {
        return userRecordsRefused.get();
    }

    long dropsForTest() {
        return drops.get();
    }

    long schemaIdRefusedForTest(LineHealthSchemas.Subject subject) {
        return lane(subject).schemaIdRefused.get();
    }

    /** Test seam: the fold is live (frames are forwarded) as after the bootstrap scan. */
    synchronized void goLiveForTest() {
        live = true;
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
