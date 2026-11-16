package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.esmbo.EsMboSchemas;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The ES MBO relay at its boundary, fed the RAW bytes a consumer hands it. */
class EsMboRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GatewaySettings SETTINGS = new GatewaySettings();
    private static final String FLOW = SETTINGS.esMboLevelFlowTopic(), HEALTH = SETTINGS.esMboHealthTopic(), EVENTS = SETTINGS.esMboEventTopic();
    private static final AtomicLong SCOPES = new AtomicLong();
    private static final long NOW = 1_794_780_000_000L;
    private static final long MID = 30_000L;

    private static final class Sink implements java.util.function.BiConsumer<String, String> {
        final List<String[]> frames = new ArrayList<>();

        @Override
        public void accept(String event, String json) {
            frames.add(new String[] {event, json});
        }

        List<JsonNode> of(String event) throws Exception {
            List<JsonNode> out = new ArrayList<>();
            for (String[] f : frames) {
                if (f[0].equals(event)) {
                    out.add(JSON.readTree(f[1]));
                }
            }
            return out;
        }
    }

    private final KafkaAvroSerializer serializer = new KafkaAvroSerializer();
    private final GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer();
    private final AtomicLong decodes = new AtomicLong();
    private final Deserializer<Object> counting = (topic, data) -> {
        decodes.incrementAndGet();
        return deserializer.deserialize(topic, data);
    };

    EsMboRelayTest() {
        String scope = "es-mbo-relay-" + SCOPES.incrementAndGet();
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(scope);
        EsMboSchemas.register(registry);
        serializer.configure(EsMboSchemas.serdeConfig("mock://" + scope), false);
        Map<String, Object> reader = new HashMap<>();
        reader.put("schema.registry.url", "mock://" + scope);
        reader.put("specific.avro.reader", "false");
        reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, FLOW + "," + HEALTH + "," + EVENTS);
        deserializer.configure(reader, false);
    }

    @AfterEach
    void close() {
        serializer.close();
        deserializer.close();
    }

    private byte[] bytes(GenericRecord r) {
        return serializer.serialize("any-topic", r);
    }

    private EsMboRelay relay(Sink sink) {
        EsMboRelay relay = new EsMboRelay(SETTINGS, sink, counting);
        relay.levelFlowCaughtUp(true, 0);   // the level-flow lane is at its end offset unless a test says it is behind
        return relay;
    }

    private static GenericRecord row(long bucketEnd, String side, long tick, long resting, long in, long out, long orders) {
        return row("ESZ6", bucketEnd, side, tick, resting, in, out, orders);
    }

    private static GenericRecord row(String contract, long bucketEnd, String side, long tick, long resting, long in, long out, long orders) {
        GenericRecord r = new GenericData.Record(EsMboSchemas.LEVEL_FLOW.schema());
        r.put("schemaVersion", 1);
        r.put("environment", "Test");
        r.put("contract", contract);
        r.put("bucketEnd", bucketEnd);
        r.put("configFingerprint", "fp");
        r.put("side", side);
        r.put("tick", tick);
        r.put("attributionStatus", "PROVISIONAL");
        r.put("epochStatus", "LIVE");
        r.put("availability", "AVAILABLE");
        for (String f : new String[] {"addCount", "addQty", "reductionQty", "increaseQty", "maxOrderQty"}) {
            r.put(f, 0L);
        }
        r.put("moveInQty", in);
        r.put("moveOutQty", out);
        r.put("restingQty", resting);
        r.put("orderCount", orders);
        return r;
    }

    private static GenericRecord health(long bucketEnd, String epoch, Long bid, Long ask) {
        return health("ESZ6", bucketEnd, epoch, bid, ask);
    }

    private static GenericRecord health(String contract, long bucketEnd, String epoch, Long bid, Long ask) {
        Schema s = EsMboSchemas.HEALTH.schema();
        GenericRecord r = new GenericData.Record(s);
        r.put("schemaVersion", 2);
        r.put("environment", "Test");
        r.put("contract", contract);
        r.put("bucketEnd", bucketEnd);
        r.put("configFingerprint", "fp");
        r.put("epochId", 1L);
        r.put("epochStatus", epoch);
        r.put("degradedReason", null);
        for (String f : new String[] {"admittedTotal", "sequenceGaps", "streamChanges", "bookOrderCount", "bookBidLevels", "bookAskLevels", "lastReceivedAtMs", "bucketsClosed", "bucketsDegraded", "gapBucketsSkipped"}) {
            r.put(f, 0L);
        }
        r.put("refusedByReason", new GenericData.Array<>(s.getField("refusedByReason").schema(), List.of()));
        r.put("bestBidTick", bid);
        r.put("bestAskTick", ask);
        return r;
    }

    private static GenericRecord event(String id, int revision, long bucketEnd, String kind, Long lo, Long hi) {
        Schema s = EsMboSchemas.EVENT.schema();
        GenericRecord r = new GenericData.Record(s);
        r.put("schemaVersion", 1);
        r.put("environment", "Test");
        r.put("contract", "ESZ6");
        r.put("bucketEnd", bucketEnd);
        r.put("configFingerprint", "fp");
        r.put("eventId", id);
        r.put("revision", revision);
        r.put("kind", kind);
        r.put("status", "PROVISIONAL");
        r.put("startMs", bucketEnd - 5000);
        r.put("endMs", null);
        r.put("direction", "ABOVE");
        r.put("loTick", lo);
        r.put("hiTick", hi);
        r.put("availability", "AVAILABLE");
        Schema valueSchema = s.getField("values").schema().getElementType();
        GenericRecord v1 = new GenericData.Record(valueSchema), v2 = new GenericData.Record(valueSchema);
        v1.put("name", "depthQty");
        v1.put("value", 12L);
        v2.put("name", "emptyRun");
        v2.put("value", 3L);
        r.put("values", new GenericData.Array<>(s.getField("values").schema(), List.of(v1, v2)));
        return r;
    }

    private void feedBucket(EsMboRelay relay, long bucketEnd, Long bid, Long ask, GenericRecord... rows) {
        for (GenericRecord r : rows) {
            relay.accept(FLOW, r.get("side") + "|" + r.get("tick"), bytes(r), bucketEnd + 100);
        }
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(bucketEnd, "LIVE", bid, ask)), bucketEnd + 150);
        relay.tick(bucketEnd + 1_500L);   // level-flow is caught up and quiet: the bucket is proven complete
    }

    @Test
    void rowsAndTheHealthRecordFoldIntoOneFrameWithinSixtyTicksOfTheMid() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        long b = NOW;
        feedBucket(relay, b, MID, MID + 1, row(b, "B", MID, 40, 1, 2, 5), row(b, "A", MID + 1, 30, 0, 0, 3),
                row(b, "B", MID - 60, 7, 0, 0, 1),   // 60.5 ticks below the mid 30000.5: out (beyond 60)
                row(b, "B", MID - 59, 8, 0, 0, 1),   // 59.5: in
                row(b, "A", MID + 60, 9, 0, 0, 1),   // 59.5 above: in
                row(b, "A", MID + 62, 9, 0, 0, 1));  // 61.5 above: out
        List<JsonNode> frames = sink.of("es-mbo-frame");
        assertEquals(1, frames.size());
        JsonNode f = frames.get(0);
        assertEquals("ESZ6", f.get("contract").asText());
        assertEquals(b, f.get("bucketEnd").asLong());
        assertEquals("LIVE", f.get("epochStatus").asText());
        assertEquals(MID, f.get("bestBidTick").asLong());
        assertEquals(MID + 1, f.get("bestAskTick").asLong());
        assertFalse(f.get("cached").asBoolean());
        JsonNode levels = f.get("levels");
        assertEquals(4, levels.size());
        assertEquals("[\"B\",30000,40,1,2,5]", levels.get(0).toString());
        assertEquals("[\"A\",30001,30,0,0,3]", levels.get(1).toString());
        assertEquals(List.of(MID - 59, MID + 60), List.of(levels.get(2).get(1).asLong(), levels.get(3).get(1).asLong()));
        assertEquals(0, relay.openBucketsForTest(), "the closed bucket is dropped from the buffer");
    }

    @Test
    void noBestBidOrAskKeepsNoLevelAndSaysWhyByEpochStatus() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.accept(FLOW, "B|30000", bytes(row(NOW, "B", MID, 40, 0, 0, 1)), NOW);
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW, "UNAVAILABLE", null, null)), NOW);
        relay.tick(NOW + 1_500L);
        JsonNode f = sink.of("es-mbo-frame").get(0);
        assertEquals(0, f.get("levels").size());
        assertTrue(f.get("bestBidTick").isNull() && f.get("bestAskTick").isNull());
        assertEquals("UNAVAILABLE", f.get("epochStatus").asText());
        // one side only is also no mid
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW + 1000, "LIVE", MID, null)), NOW + 1000);
        relay.tick(NOW + 3000);
        assertEquals(0, sink.of("es-mbo-frame").get(1).get("levels").size());
    }

    @Test
    void aQuietBucketIsClosedAfterTheHoldAndAHealthAheadOfLevelFlowWaitsForALaterBucket() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.levelFlowCaughtUp(false, NOW - 1);   // behind: only a row of a LATER bucket proves a bucket complete
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW, "LIVE", MID, MID + 1)), NOW);
        assertEquals(0, sink.of("es-mbo-frame").size(), "level-flow has not reached the bucket yet");
        relay.accept(FLOW, "B|30000", bytes(row(NOW, "B", MID, 40, 0, 0, 1)), NOW + 10);
        assertEquals(0, sink.of("es-mbo-frame").size(), "a row of this very bucket does not prove the rest of its rows were read");
        relay.accept(FLOW, "B|30001", bytes(row(NOW + 1000, "B", MID + 1, 41, 0, 0, 1)), NOW + 20);
        assertEquals(1, sink.of("es-mbo-frame").size());
        assertEquals(1, sink.of("es-mbo-frame").get(0).get("levels").size(), "the frame carries the row of its own bucket only");
        relay.levelFlowCaughtUp(true, NOW);   // caught up: a bucket with no later row closes once the lane has been quiet for the hold
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW + 1000, "LIVE", MID, MID + 1)), NOW + 1000);
        assertEquals(1, sink.of("es-mbo-frame").size());
        relay.tick(NOW + 2500);
        assertEquals(2, sink.of("es-mbo-frame").size(), "caught up and quiet: closed when the hold runs out");
    }

    @Test
    void theRowBufferIsBoundedAndALateRowOrReplayedHealthMakesNoSecondFrame() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        for (int i = 0; i < EsMboRelay.MAX_OPEN_BUCKETS + 50; i++) {   // rows of buckets whose health never arrives
            relay.accept(FLOW, "B|30000", bytes(row(NOW + i * 1000L, "B", MID, 1, 0, 0, 1)), NOW);
        }
        assertEquals(EsMboRelay.MAX_OPEN_BUCKETS, relay.openBucketsForTest());
        feedBucket(relay, NOW + 1_000_000L, MID, MID + 1);
        relay.tick(NOW + 1_002_000L);   // no rows for this bucket: closed when the hold runs out
        assertEquals(0, relay.openBucketsForTest(), "a health record drops every buffered bucket at or before it");
        relay.accept(FLOW, "B|30000", bytes(row(NOW + 5000L, "B", MID, 1, 0, 0, 1)), NOW);
        assertEquals(1, relay.lateRowsForTest());
        assertEquals(0, relay.openBucketsForTest());
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW + 1_000_000L, "LIVE", MID, MID + 1)), NOW + 1_000_150L);
        assertEquals(1, sink.of("es-mbo-frame").size(), "the same bucket is never closed twice");
    }

    @Test
    void aStaleFrameIsReplayOnlyAndTheHistoryIsOneCachedMessageOldestFirst() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW - 600_000L, "LIVE", MID, MID + 1)), NOW);   // 10 minutes old: catch-up
        relay.tick(NOW + 2000);
        feedBucket(relay, NOW, MID, MID + 1, row(NOW, "B", MID, 4, 0, 0, 1));
        assertEquals(1, sink.of("es-mbo-frame").size(), "only the fresh frame is broadcast live");
        List<String[]> replay = new ArrayList<>();
        relay.replay((e, j) -> replay.add(new String[] {e, j}));
        assertEquals("es-mbo-status", replay.get(0)[0], "the status goes FIRST");
        assertEquals("es-mbo-history", replay.get(1)[0]);
        JsonNode h = JSON.readTree(replay.get(1)[1]);
        assertTrue(h.get("cached").asBoolean());
        assertEquals(2, h.get("frames").size());
        assertTrue(h.get("frames").get(0).get("bucketEnd").asLong() < h.get("frames").get(1).get("bucketEnd").asLong());
        assertTrue(h.get("frames").get(0).get("cached").asBoolean() && h.get("frames").get(1).get("cached").asBoolean());
    }

    @Test
    void twoConsecutiveReplaysEachCarryOneConsistentDuplicateFreeOrderedHistoryEvenWithALiveFrameBetween() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        for (int i = 0; i < 150; i++) {
            long b = NOW + i * 1000L;
            feedBucket(relay, b, MID, MID + 1, row(b, "B", MID, 10, 1, 1, 1));
        }
        List<String[]> first = new ArrayList<>();
        relay.replay((e, j) -> first.add(new String[] {e, j}));
        feedBucket(relay, NOW + 150_000L, MID, MID + 1, row(NOW + 150_000L, "B", MID, 10, 1, 1, 1));   // a live frame arrives between the two replays
        List<String[]> second = new ArrayList<>();
        relay.replay((e, j) -> second.add(new String[] {e, j}));
        for (List<String[]> replay : List.of(first, second)) {
            assertEquals(List.of("es-mbo-status", "es-mbo-history"), List.of(replay.get(0)[0], replay.get(1)[0]), "status first, then ONE history");
            assertEquals(2, replay.size(), "no second history message");
        }
        List<Long> a = bucketEnds(JSON.readTree(first.get(1)[1]));
        List<Long> b = bucketEnds(JSON.readTree(second.get(1)[1]));
        assertEquals(150, a.size());
        assertEquals(151, b.size());
        for (List<Long> list : List.of(a, b)) {
            for (int i = 1; i < list.size(); i++) {
                assertTrue(list.get(i) > list.get(i - 1), "strictly increasing: ordered and duplicate-free");
            }
        }
        assertEquals(a, b.subList(0, 150), "the second history is the first plus the new frame, nothing missing or moved");
        assertEquals(NOW + 150_000L, b.get(150).longValue());
    }

    private static List<Long> bucketEnds(JsonNode history) {
        List<Long> out = new ArrayList<>();
        for (JsonNode f : history.get("frames")) {
            out.add(f.get("bucketEnd").asLong());
        }
        return out;
    }

    @Test
    void theHistoryDropsTheOldestFramesToFitTheByteBound() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        for (int i = 0; i < 40; i++) {
            long b = NOW + i * 1000L;
            GenericRecord[] rows = new GenericRecord[40];
            for (int k = 0; k < 40; k++) {
                rows[k] = row(b, "B", MID - k, 10, 1, 1, 1);
            }
            feedBucket(relay, b, MID, MID + 1, rows);
        }
        String history = relay.historyJson();
        int limit = SETTINGS.esMboHistoryMaxBytes();
        assertTrue(history.length() < limit);
        // a tiny bound through the real settings path: env-less settings subclass
        EsMboRelay small = new EsMboRelay(SETTINGS, new Sink(), counting, 10_000);
        small.levelFlowCaughtUp(true, 0);
        for (int i = 0; i < 40; i++) {
            long b = NOW + i * 1000L;
            GenericRecord[] rows = new GenericRecord[40];
            for (int k = 0; k < 40; k++) {
                rows[k] = row(b, "B", MID - k, 10, 1, 1, 1);
            }
            feedBucket(small, b, MID, MID + 1, rows);
        }
        String capped = small.historyJson();
        assertTrue(capped.length() <= 10_000, "was " + capped.length());
        JsonNode h = JSON.readTree(capped);
        int n = h.get("frames").size();
        assertTrue(n > 0 && n < 40);
        assertEquals(NOW + 39_000L, h.get("frames").get(n - 1).get("bucketEnd").asLong(), "the NEWEST frames are kept");
    }

    @Test
    void anEventIsForwardedWithItsValuesMapAndNullTicksAndTheRingKeepsTheLatestRevisionPerId() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.accept(EVENTS, "E1", bytes(event("E1", 1, NOW, "VACUUM_ABOVE", null, null)), NOW + 100);
        relay.accept(EVENTS, "E2", bytes(event("E2", 1, NOW, "BOOK_SWEEP_UP", 30010L, 30020L)), NOW + 100);
        relay.accept(EVENTS, "E1", bytes(event("E1", 2, NOW + 1000, "VACUUM_CLEARED", null, null)), NOW + 1100);
        List<JsonNode> out = sink.of("es-mbo-event");
        assertEquals(3, out.size());
        JsonNode e = out.get(0);
        assertEquals("E1", e.get("eventId").asText());
        assertEquals(1, e.get("revision").asInt());
        assertEquals("VACUUM_ABOVE", e.get("kind").asText());
        assertTrue(e.get("loTick").isNull() && e.get("hiTick").isNull() && e.get("endMs").isNull());
        assertEquals("ABOVE", e.get("direction").asText());
        assertEquals(12, e.get("values").get("depthQty").asLong());
        assertEquals(3, e.get("values").get("emptyRun").asLong());
        assertFalse(e.get("cached").asBoolean());
        assertEquals(30010, out.get(1).get("loTick").asLong());
        assertEquals(2, relay.eventsHeldForTest(), "one entry per eventId");
        JsonNode h = JSON.readTree(relay.historyJson());
        assertEquals("E2", h.get("events").get(0).get("eventId").asText());
        assertEquals(2, h.get("events").get(1).get("revision").asInt(), "E1's latest revision, moved to the end");
        assertTrue(h.get("events").get(1).get("cached").asBoolean());
        for (int i = 0; i < EsMboRelay.EVENT_RING + 20; i++) {
            relay.accept(EVENTS, "X" + i, bytes(event("X" + i, 1, NOW, "VACUUM_ABOVE", null, null)), NOW);
        }
        assertEquals(EsMboRelay.EVENT_RING, relay.eventsHeldForTest());
    }

    @Test
    void anOldEventIsRingedAndNotBroadcastLive() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.accept(EVENTS, "E1", bytes(event("E1", 1, NOW - 3_600_000L, "VACUUM_ABOVE", null, null)), NOW);
        assertEquals(0, sink.of("es-mbo-event").size());
        assertEquals(1, relay.eventsHeldForTest());
    }

    @Test
    void aWrongSchemaIdIsRefusedAndCountedBeforeAnyDecode() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.accept(FLOW, "B|30000", bytes(health(NOW, "LIVE", MID, MID + 1)), NOW);   // a health frame on the level-flow topic
        relay.accept(EVENTS, "E1", bytes(row(NOW, "B", MID, 1, 0, 0, 1)), NOW);
        relay.accept(HEALTH, "CME|ESZ6", bytes(event("E1", 1, NOW, "VACUUM_ABOVE", null, null)), NOW);
        assertEquals(1, relay.schemaIdRefusedForTest(EsMboSchemas.LEVEL_FLOW));
        assertEquals(1, relay.schemaIdRefusedForTest(EsMboSchemas.EVENT));
        assertEquals(1, relay.schemaIdRefusedForTest(EsMboSchemas.HEALTH));
        assertEquals(0, decodes.get(), "refused before the registry is asked");
        assertTrue(sink.frames.isEmpty());
        assertTrue(relay.statusJson().contains("\"schemaIdRefused\":3"));
    }

    @Test
    void aRecordWithTheWrongKeyIsADropAndAGarbageBodyIsUndecodable() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.accept(FLOW, "B|1", bytes(row(NOW, "B", MID, 1, 0, 0, 1)), NOW);
        relay.accept(EVENTS, "WRONG", bytes(event("E1", 1, NOW, "VACUUM_ABOVE", null, null)), NOW);
        assertEquals(2, relay.dropsForTest());
        byte[] framed = bytes(row(NOW, "B", MID, 1, 0, 0, 1));
        byte[] corrupt = java.util.Arrays.copyOf(framed, 8);
        relay.accept(FLOW, "B|30000", corrupt, NOW);
        assertTrue(relay.statusJson().contains("\"undecodable\":1"));
    }

    @Test
    void theStatusSaysWhatTheRelayHasSeen() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        JsonNode empty = JSON.readTree(relay.statusJson());
        assertTrue(empty.get("enabled").asBoolean());
        assertTrue(empty.get("lastFrameBucketEnd").isNull() && empty.get("lastEventBucketEnd").isNull() && empty.get("epochStatus").isNull());
        feedBucket(relay, NOW, MID, MID + 1, row(NOW, "B", MID, 1, 0, 0, 1));
        relay.accept(EVENTS, "E1", bytes(event("E1", 1, NOW, "VACUUM_ABOVE", null, null)), NOW);
        JsonNode s = JSON.readTree(relay.statusJson());
        assertEquals(NOW, s.get("lastFrameBucketEnd").asLong());
        assertEquals(NOW, s.get("lastEventBucketEnd").asLong());
        assertEquals("LIVE", s.get("epochStatus").asText());
        assertEquals(1, s.get("framesSeen").asInt());
        assertEquals(1, s.get("eventsSeen").asInt());
        assertEquals(0, s.get("consumerFailures").asInt());
    }

    @Test
    void anOtherContractRowForTheSameBucketNeverClosesOrMixesWithTheCurrentContract() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.levelFlowCaughtUp(false, NOW - 2000);   // behind: only a current-contract row can close a bucket
        relay.accept(HEALTH, "CME|ESZ6", bytes(health("ESZ6", NOW - 1000, "LIVE", MID, MID + 1)), NOW - 1000);   // ESZ6 is the newest health: current
        relay.tick(NOW + 500);
        assertEquals("ESZ6", relay.currentContractForTest());
        relay.accept(HEALTH, "CME|ESZ6", bytes(health("ESZ6", NOW, "LIVE", MID, MID + 1)), NOW);
        relay.accept(FLOW, "B|30010", bytes(row("ESH7", NOW, "B", MID + 10, 22, 0, 0, 1)), NOW + 10);   // the other contract's row for T arrives FIRST
        assertEquals(0, sink.of("es-mbo-frame").size(), "an ESH7 row must not advance ESZ6's watermark and close its bucket");
        assertEquals(1, relay.otherContractForTest());
        relay.accept(FLOW, "B|30000", bytes(row("ESZ6", NOW, "B", MID, 11, 0, 0, 1)), NOW + 20);
        relay.accept(FLOW, "B|30001", bytes(row("ESZ6", NOW + 1000, "B", MID + 1, 12, 0, 0, 1)), NOW + 30);   // a later bucket proves NOW complete
        List<JsonNode> frames = sink.of("es-mbo-frame");
        assertEquals(2, frames.size(), "the held earlier bucket and this one");
        assertEquals(1, frames.get(1).get("levels").size(), "only the current contract's row");
        assertEquals(11, frames.get(1).get("levels").get(0).get(2).asLong());
    }

    @Test
    void aChangeOfTheCurrentContractResetsTheFoldingStateAndSaysSoInTheStatus() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        feedBucket(relay, NOW, MID, MID + 1, row(NOW, "B", MID, 5, 0, 0, 1));
        relay.levelFlowCaughtUp(false, NOW + 1);   // level-flow is behind from here: only rows close buckets
        relay.accept(EVENTS, "VAC|ESZ6|1", bytes(event("VAC|ESZ6|1", 1, NOW, "VACUUM_ABOVE", null, null)), NOW);
        relay.accept(FLOW, "B|30000", bytes(row(NOW + 1000, "B", MID, 6, 0, 0, 1)), NOW + 1000);   // an open ESZ6 bucket
        relay.accept(HEALTH, "CME|ESZ6", bytes(health("ESZ6", NOW + 5000, "LIVE", MID, MID + 1)), NOW + 1100);   // held: level-flow has not reached it
        assertEquals(1, relay.framesHeldForTest());
        assertTrue(JSON.readTree(relay.statusJson()).get("contract").asText().equals("ESZ6"));
        relay.accept(HEALTH, "CME|ESH7", bytes(health("ESH7", NOW + 2000, "LIVE", MID + 100, MID + 101)), NOW + 2000);   // the engine now speaks ESH7
        assertEquals("ESH7", relay.currentContractForTest());
        assertEquals("ESH7", JSON.readTree(relay.statusJson()).get("contract").asText());
        assertEquals(0, relay.openBucketsForTest(), "open buckets reset");
        assertEquals(0, relay.framesHeldForTest(), "the other contract's frames are not served");
        assertEquals(0, relay.eventsHeldForTest(), "nor its events");
        relay.accept(FLOW, "B|30000", bytes(row(NOW + 6000, "B", MID, 6, 0, 0, 1)), NOW + 6000);   // ESZ6 goes on speaking: dropped, counted
        assertEquals(2, relay.otherContractForTest(), "the ringed ESZ6 event removed at the switch, and this row");
        assertEquals(0, relay.openBucketsForTest());
        assertEquals(1, sink.of("es-mbo-frame").size(), "the held ESZ6 health never closes after the reset");
        relay.accept(FLOW, "B|30100", bytes(row("ESH7", NOW + 2000, "B", MID + 100, 9, 0, 0, 1)), NOW + 2100);
        relay.accept(FLOW, "B|30101", bytes(row("ESH7", NOW + 3000, "B", MID + 101, 9, 0, 0, 1)), NOW + 3100);   // a later bucket proves 2000 complete
        List<JsonNode> frames = sink.of("es-mbo-frame");
        assertEquals(2, frames.size(), "ESH7's bucket closes on ESH7's own row");
        assertEquals("ESH7", frames.get(1).get("contract").asText());
        assertEquals(1, frames.get(1).get("levels").size());
    }

    @Test
    void theFirstHealthEverBecomesCurrentAndKeepsTheRowsReadBeforeItWhenTheyAreTheSameContract() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        assertEquals(null, relay.currentContractForTest());
        relay.accept(FLOW, "B|30000", bytes(row(NOW, "B", MID, 40, 0, 0, 1)), NOW);   // level-flow ahead of health at the start of a catch-up
        relay.accept(HEALTH, "CME|ESZ6", bytes(health("ESZ6", NOW, "LIVE", MID, MID + 1)), NOW);
        relay.tick(NOW + 1_500L);
        assertEquals("ESZ6", relay.currentContractForTest());
        assertEquals(1, sink.of("es-mbo-frame").get(0).get("levels").size());
        // rows of a different contract read first are provisional and thrown away when the first health names another
        EsMboRelay other = relay(new Sink());
        other.accept(FLOW, "B|30000", bytes(row("ESH7", NOW, "B", MID, 40, 0, 0, 1)), NOW);
        other.accept(HEALTH, "CME|ESZ6", bytes(health("ESZ6", NOW, "LIVE", MID, MID + 1)), NOW);
        assertEquals("ESZ6", other.currentContractForTest());
        assertEquals(0, other.openBucketsForTest());
    }

    @Test
    void evictingAnEventFromTheRingNeverForgetsItsHighRevision() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.accept(EVENTS, "E1", bytes(event("E1", 2, NOW, "VACUUM_CLEARED", null, null)), NOW);
        for (int i = 0; i < EsMboRelay.EVENT_RING + 5; i++) {
            relay.accept(EVENTS, "X" + i, bytes(event("X" + i, 1, NOW, "VACUUM_ABOVE", null, null)), NOW);
        }
        assertFalse(JSON.readTree(relay.historyJson()).toString().contains("\"E1\""), "E1 left the ring");
        int before = sink.of("es-mbo-event").size();
        relay.accept(EVENTS, "E1", bytes(event("E1", 1, NOW, "VACUUM_ABOVE", null, null)), NOW + 100);   // a stale revision after the eviction
        assertEquals(before, sink.of("es-mbo-event").size());
        assertEquals(1, relay.duplicateEventsForTest());
        assertFalse(JSON.readTree(relay.historyJson()).toString().contains("\"E1\""), "and it did not re-enter the ring");
        relay.accept(EVENTS, "E1", bytes(event("E1", 3, NOW + 200, "VACUUM_RAISED", null, null)), NOW + 200);
        assertEquals(before + 1, sink.of("es-mbo-event").size(), "a newer revision still passes");
    }

    @Test
    void theLedgerIsPrunedByAgeToTheEventsWindowAndAnythingOutsideTheWindowIsNeverAccepted() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        for (int i = 0; i < 50; i++) {
            relay.accept(EVENTS, "E" + i, bytes(event("E" + i, 1, NOW, "VACUUM_ABOVE", null, null)), NOW);
        }
        assertEquals(50, relay.ledgerSizeForTest());
        long later = NOW + EsMboRelay.EVENTS_LOOKBACK_MS + 120_000L;
        relay.accept(EVENTS, "NEW", bytes(event("NEW", 1, later, "VACUUM_ABOVE", null, null)), later);
        assertEquals(1, relay.ledgerSizeForTest(), "entries older than the window are pruned: bounded by age");
        int before = sink.of("es-mbo-event").size();
        relay.accept(EVENTS, "E0", bytes(event("E0", 1, NOW, "VACUUM_ABOVE", null, null)), later + 1);   // pruned id, but its bucket end is outside the window
        assertEquals(before, sink.of("es-mbo-event").size(), "outside the window it is not re-accepted either");
        assertEquals(1, relay.ledgerSizeForTest());
        assertEquals(1, relay.eventsHeldForTest(), "and it is outside the replay ring too");
    }

    @Test
    void aHealthRecordIsNeverClosedByWallTimeWhileLevelFlowIsBehind() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.levelFlowCaughtUp(false, NOW);   // the catch-up backlog: level-flow is behind its end offset
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW, "LIVE", MID, MID + 1)), NOW);
        relay.tick(NOW + 3_000L);   // three times the 1 s hold, the injected clock says (still inside the 5 s live window)
        assertEquals(0, sink.of("es-mbo-frame").size(), "behind: no frame, however long");
        relay.accept(FLOW, "B|30000", bytes(row(NOW, "B", MID, 40, 0, 0, 1)), NOW + 3_001L);   // the backlog reaches the bucket
        relay.accept(FLOW, "B|30001", bytes(row(NOW + 1000, "B", MID + 1, 40, 0, 0, 1)), NOW + 3_002L);   // and passes it
        List<JsonNode> frames = sink.of("es-mbo-frame");
        assertEquals(1, frames.size());
        assertEquals(1, frames.get(0).get("levels").size(), "the frame carries its rows");
        assertEquals(0, relay.lateRowsForTest());
    }

    @Test
    void aCaughtUpQuietLaneClosesAnEmptyBucketOnlyAfterTheHoldSinceTheCatchUp() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.levelFlowCaughtUp(false, NOW);
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW, "LIVE", MID, MID + 1)), NOW);
        relay.levelFlowCaughtUp(true, NOW + 5_000L);   // caught up only now
        relay.tick(NOW + 5_500L);
        assertEquals(0, sink.of("es-mbo-frame").size(), "less than the hold since the catch-up");
        assertEquals(0, relay.framesHeldForTest());
        relay.tick(NOW + 6_100L);
        assertEquals(1, relay.framesHeldForTest(), "closed (replay-only: its bucket is older than the live window)");
    }

    @Test
    void aDelayedOlderOrEqualRevisionIsIgnoredAndCounted() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.accept(EVENTS, "E1", bytes(event("E1", 2, NOW + 1000, "VACUUM_CLEARED", null, null)), NOW + 1100);
        relay.accept(EVENTS, "E1", bytes(event("E1", 1, NOW, "VACUUM_ABOVE", null, null)), NOW + 1200);   // delayed revision 1 after 2
        relay.accept(EVENTS, "E1", bytes(event("E1", 2, NOW + 1000, "VACUUM_CLEARED", null, null)), NOW + 1300);   // duplicate
        assertEquals(1, sink.of("es-mbo-event").size());
        assertEquals(2, relay.duplicateEventsForTest());
        JsonNode h = JSON.readTree(relay.historyJson());
        assertEquals(1, h.get("events").size());
        assertEquals(2, h.get("events").get(0).get("revision").asInt());
        assertEquals("VACUUM_CLEARED", h.get("events").get(0).get("kind").asText());
        relay.accept(EVENTS, "E1", bytes(event("E1", 3, NOW + 2000, "VACUUM_RAISED", null, null)), NOW + 2100);
        assertEquals(2, sink.of("es-mbo-event").size(), "a newer revision still passes");
        assertTrue(relay.statusJson().contains("\"eventsSeen\":2"));
    }

    @Test
    void aMissingLevelFlowTopicHoldsTheHealthAndSaysSoUntilItsRowsArrive() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        assertFalse(EsMboRelay.laneCaughtUp(false, 0, 0), "an absent lane is NOT caught up, even with position == end == 0");
        assertTrue(EsMboRelay.laneCaughtUp(true, 7, 7));
        assertFalse(EsMboRelay.laneCaughtUp(true, 6, 7));
        relay.levelFlowLaneAbsent(true);
        relay.levelFlowCaughtUp(false, NOW);   // what the consumer loop reports for an absent lane
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW, "LIVE", MID, MID + 1)), NOW);
        relay.tick(NOW + 5_000L);
        assertEquals(0, sink.of("es-mbo-frame").size(), "the lane does not exist: nothing closes, not even empty");
        assertEquals(0, relay.framesHeldForTest());
        assertEquals("absent", JSON.readTree(relay.statusJson()).get("levelFlowLane").asText());
        relay.levelFlowLaneAbsent(false);   // the topic appears
        relay.levelFlowCaughtUp(false, NOW + 5_100L);
        relay.accept(FLOW, "B|30000", bytes(row(NOW, "B", MID, 40, 0, 0, 1)), NOW + 5_200L);
        relay.accept(FLOW, "B|30001", bytes(row(NOW + 1000, "B", MID + 1, 40, 0, 0, 1)), NOW + 5_201L);
        assertEquals(1, relay.framesHeldForTest());
        assertEquals("present", JSON.readTree(relay.statusJson()).get("levelFlowLane").asText());
        assertEquals(0, relay.lateRowsForTest());
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW + 1000, "LIVE", MID, MID + 1)), NOW + 5_300L);   // and the history carries the row
        assertTrue(relay.historyJson().contains("[\"B\",30000,40,0,0,1]"));
    }

    @Test
    void rowsOfAnotherContractDiscardedAtTheFirstHealthAreCounted() throws Exception {
        EsMboRelay relay = relay(new Sink());
        relay.accept(FLOW, "B|30000", bytes(row("ESH7", NOW, "B", MID, 40, 0, 0, 1)), NOW);
        relay.accept(FLOW, "B|30001", bytes(row("ESH7", NOW, "B", MID + 1, 40, 0, 0, 1)), NOW);
        relay.accept(HEALTH, "CME|ESZ6", bytes(health("ESZ6", NOW, "LIVE", MID, MID + 1)), NOW);
        assertEquals(2, relay.otherContractForTest(), "two provisional rows thrown away, both counted");
        assertEquals(0, relay.openBucketsForTest());
    }

    @Test
    void aFutureDatedEventIsNeverLedgeredOrRinged() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        long future = NOW + 365L * 86_400_000L;
        for (int i = 0; i < 30; i++) {
            relay.accept(EVENTS, "F" + i, bytes(event("F" + i, 1, future, "VACUUM_ABOVE", null, null)), NOW);
        }
        assertEquals(0, relay.ledgerSizeForTest());
        assertEquals(0, relay.eventsHeldForTest());
        assertEquals(0, sink.of("es-mbo-event").size());
        assertEquals(30, relay.eventsFutureForTest());
        relay.accept(EVENTS, "OK", bytes(event("OK", 1, NOW + 4_000L, "VACUUM_ABOVE", null, null)), NOW);   // inside the 5 s skew
        assertEquals(1, relay.ledgerSizeForTest());
    }

    @Test
    void rowsOfAnEarlierBucketThatNeverGotAHealthAreCountedWhenALaterBucketCloses() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.levelFlowCaughtUp(false, NOW);   // behind: only rows close buckets
        relay.accept(FLOW, "B|30000", bytes(row(NOW + 100, "B", MID, 5, 0, 0, 1)), NOW + 100);   // a row for bucket 100, no health ever
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW + 200, "LIVE", MID, MID + 1)), NOW + 200);   // health for 200, held
        assertEquals(0, relay.rowsDroppedForTest());
        relay.accept(FLOW, "B|30001", bytes(row(NOW + 200, "B", MID + 1, 6, 0, 0, 1)), NOW + 210);   // row for 200: not yet proof (more rows of 200 may follow)
        assertEquals(0, sink.of("es-mbo-frame").size());
        relay.accept(FLOW, "B|30002", bytes(row(NOW + 300, "B", MID + 2, 7, 0, 0, 1)), NOW + 310);   // a row of bucket 300 proves 200 complete
        assertEquals(1, sink.of("es-mbo-frame").size());
        assertEquals(1, sink.of("es-mbo-frame").get(0).get("levels").size(), "bucket 200 carries only its own row");
        assertEquals(1, relay.rowsDroppedForTest(), "bucket 100's row was discarded and is counted");
        assertEquals(1, relay.openBucketsForTest(), "bucket 300 is still open");
    }

    @Test
    void levelFlowIsHeldBackWhileHealthIsBehindAndHalfTheBucketBoundIsOpen() throws Exception {
        EsMboRelay relay = relay(new Sink());
        assertFalse(relay.levelFlowShouldPause(false), "nothing buffered");
        for (int i = 0; i < EsMboRelay.MAX_OPEN_BUCKETS / 2; i++) {
            relay.accept(FLOW, "B|30000", bytes(row(NOW + i * 1000L, "B", MID, 1, 0, 0, 1)), NOW);
        }
        assertTrue(relay.levelFlowShouldPause(false), "half the bound is open and health has not caught up: hold level-flow");
        assertFalse(relay.levelFlowShouldPause(true), "health is at its end: buckets without a health will never close, so do not wait on it");
    }

    @Test
    void aRowOfTheClosingBucketThatArrivesLaterIsNotLostBecauseARowOfItsOwnBucketNeverClosesIt() throws Exception {
        Sink sink = new Sink();
        EsMboRelay relay = relay(sink);
        relay.levelFlowCaughtUp(false, NOW - 1);   // catch-up: level-flow is behind and a batch can end in the middle of a bucket
        relay.accept(HEALTH, "CME|ESZ6", bytes(health(NOW, "LIVE", MID, MID + 1)), NOW);
        relay.accept(FLOW, "B|30000", bytes(row(NOW, "B", MID, 1, 0, 0, 1)), NOW + 1);   // first row of the bucket: the batch ends here
        relay.accept(FLOW, "B|30001", bytes(row(NOW, "B", MID + 1, 2, 0, 0, 1)), NOW + 2);   // the rest of the same bucket, in the next batch
        relay.accept(FLOW, "B|30002", bytes(row(NOW + 1000, "B", MID + 2, 3, 0, 0, 1)), NOW + 3);
        assertEquals(1, sink.of("es-mbo-frame").size());
        assertEquals(2, sink.of("es-mbo-frame").get(0).get("levels").size(), "both rows of the bucket are in its frame");
        assertEquals(0, relay.lateRowsForTest());
    }

    @Test
    void aDisabledRelayConsumesNothing() {
        EsMboRelay relay = new EsMboRelay(new GatewaySettings(), new Sink(), null);
        assertFalse(SETTINGS.esMboRelayEnabled(), "the flag defaults OFF");
        relay.start();
        assertFalse(relay.consuming(), "no thread, no consumer");
        assertEquals(0, relay.framesHeldForTest());
        relay.stop();
    }

    @Test
    void theTopicDefaultsAreTheEnginesTopics() {
        assertEquals("es.mbo.level-flow", SETTINGS.esMboLevelFlowTopic());
        assertEquals("es.mbo.health", SETTINGS.esMboHealthTopic());
        assertEquals("es.mbo.liquidity-events", SETTINGS.esMboEventTopic());
        assertEquals(3502, EsMboSchemas.LEVEL_FLOW.id());
        assertEquals(3509, EsMboSchemas.HEALTH.id());
        assertEquals(3503, EsMboSchemas.EVENT.id());
    }
}
