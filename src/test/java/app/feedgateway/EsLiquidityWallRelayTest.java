package app.feedgateway;

import static app.feedgateway.EsLiquidityWallFixtures.KEY;
import static app.feedgateway.EsLiquidityWallFixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.liquiditywall.LiquidityWallSchemas;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The relay at its boundary, fed the RAW bytes a consumer hands it: the frame's schema id checked against the topic's fixed id (3403/3404/3407/3402) before
 * any decoding, the registry decode through the gateway's own {@link GatewayAvroDeserializer} against an in-memory registry holding the fixed ids, the
 * freshness gates, the connect replay with its {@code cached} marks, the event replay window, and a disabled relay.
 */
class EsLiquidityWallRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GatewaySettings SETTINGS = new GatewaySettings();
    private static final String DASHBOARD = SETTINGS.esLiquidityWallDashboardTopic();
    private static final String EVENTS = SETTINGS.esLiquidityWallEventsTopic();
    private static final String VOICE = SETTINGS.esLiquidityWallVoiceTopic();
    private static final String BUCKETS = SETTINGS.esLiquidityWallBucketsTopic();
    private static final AtomicLong SCOPES = new AtomicLong();

    private static final class Sink implements java.util.function.BiConsumer<String, String> {
        final List<String[]> frames = new ArrayList<>();

        @Override
        public void accept(String event, String json) {
            frames.add(new String[] {event, json});
        }

        List<String> events() {
            List<String> out = new ArrayList<>();
            for (String[] f : frames) {
                out.add(f[0]);
            }
            return out;
        }

        JsonNode json(int i) throws Exception {
            return JSON.readTree(frames.get(i)[1]);
        }
    }

    /** A Confluent frame of the record under its fixed id, as es-liquidity-wall-service writes it; the decoder is the one the relay's consumer uses. */
    private static final class Wire implements AutoCloseable {
        final KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        final GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer();
        /** Decodes counted through this seam: proves nothing is decoded when the id check refuses. */
        final AtomicLong decodes = new AtomicLong();
        final Deserializer<Object> counting = new Deserializer<>() {
            @Override
            public Object deserialize(String topic, byte[] data) {
                decodes.incrementAndGet();
                return deserializer.deserialize(topic, data);
            }
        };

        Wire() {
            String scope = "es-liquidity-wall-relay-" + SCOPES.incrementAndGet();
            SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(scope);
            LiquidityWallSchemas.register(registry);
            serializer.configure(LiquidityWallSchemas.serdeConfig("mock://" + scope), false);
            Map<String, Object> reader = new HashMap<>();
            reader.put("schema.registry.url", "mock://" + scope);
            reader.put("specific.avro.reader", "false");
            reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
            reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, DASHBOARD + "," + EVENTS + "," + VOICE + "," + BUCKETS);
            deserializer.configure(reader, false);
        }

        byte[] bytes(GenericRecord record) {
            return serializer.serialize("any-topic", record);   // RecordNameStrategy: the subject is the record's name, never the topic
        }

        EsLiquidityWallRelay relay(Sink sink) {
            return new EsLiquidityWallRelay(SETTINGS, sink, counting);
        }

        @Override
        public void close() {
            serializer.close();
            deserializer.close();
        }
    }

    private final Wire wire = new Wire();

    @AfterEach
    void close() {
        wire.close();
    }

    @Test
    void eachRecordIsCheckedForItsFixedIdDecodedThroughTheRegistryAndRelayedUnderItsEventMarkedLive() throws Exception {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = wire.relay(sink);
        byte[] dashboard = wire.bytes(EsLiquidityWallFixtures.dashboard(NOW));
        assertEquals(3403, LiquidityWallSchemas.assertFramedAs(dashboard, LiquidityWallSchemas.DASHBOARD));
        relay.accept(DASHBOARD, KEY, dashboard, NOW + 500);
        relay.accept(EVENTS, "C1", wire.bytes(EsLiquidityWallFixtures.born(NOW, "C1")), NOW + 500);
        relay.accept(EVENTS, KEY + "|FLIP", wire.bytes(EsLiquidityWallFixtures.flip(NOW)), NOW + 500);
        relay.accept(VOICE, KEY, wire.bytes(EsLiquidityWallFixtures.voice(NOW)), NOW + 500);
        relay.accept(BUCKETS, "BID|30440", wire.bytes(EsLiquidityWallFixtures.bucket(NOW, "LIVE", "BID", 30440L, 150L, "NEUTRAL").build()), NOW + 500);
        assertEquals(List.of("es-liquidity-heatmap", "es-liquidity-wall", "es-liquidity-wall", "es-liquidity-voice"), sink.events(), "a bucket row is never a frame");
        assertEquals(5, wire.decodes.get());
        JsonNode heatmap = sink.json(0);
        assertEquals("Rithmic Test", heatmap.get("depthEnvironment").asText());
        assertEquals(NOW, heatmap.get("bucketEnd").longValue());
        assertFalse(heatmap.get("cached").booleanValue(), "a live forward is cached:false");
        assertEquals("FLIP", sink.json(2).get("kind").asText());
        assertFalse(sink.json(1).get("cached").booleanValue());
        JsonNode voice = sink.json(3);
        assertEquals("liquidity flip - downside, 7612.25", voice.get("text").asText());
        assertFalse(voice.get("stale").booleanValue());
        assertFalse(voice.get("cached").booleanValue());
        assertEquals(0, relay.dropsForTest());
        assertEquals(1, relay.history().framesFolded());
        assertTrue(relay.history().render(NOW + 500).contains("\"windowBucketEnd\":" + NOW + ",\"added\":40"), "the bucket row reached the projection");
        for (String event : List.of("es-liquidity-heatmap", "es-liquidity-wall", "es-liquidity-voice")) {
            assertTrue(FeedGatewayService.isGlobalBroadcastEvent(event), event + " must survive per-session routing");
        }
    }

    @Test
    void aFrameUnderAnotherTopicsIdOrNoFrameAtAllIsRefusedBeforeAnyDecodeAndCountedPerTopic() throws Exception {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = wire.relay(sink);
        byte[] voiceBytes = wire.bytes(EsLiquidityWallFixtures.voice(NOW));   // id 3407
        relay.accept(DASHBOARD, KEY, voiceBytes, NOW);                        // the dashboard topic requires 3403
        relay.accept(EVENTS, KEY + "|FLIP", wire.bytes(EsLiquidityWallFixtures.dashboard(NOW)), NOW);   // 3403 on a 3404 topic
        relay.accept(BUCKETS, "BID|30440", wire.bytes(EsLiquidityWallFixtures.born(NOW, "C1")), NOW);   // 3404 on a 3402 topic
        relay.accept(VOICE, KEY, "{\"text\":\"retained json\"}".getBytes(StandardCharsets.UTF_8), NOW);   // not a Confluent frame
        relay.accept(VOICE, KEY, new byte[] {0, 0, 0}, NOW);                                               // shorter than the header
        assertTrue(sink.frames.isEmpty());
        assertEquals(0, wire.decodes.get(), "refused BEFORE the registry is asked");
        assertEquals(1, relay.schemaIdRefusedForTest(LiquidityWallSchemas.DASHBOARD));
        assertEquals(1, relay.schemaIdRefusedForTest(LiquidityWallSchemas.EVENT));
        assertEquals(1, relay.schemaIdRefusedForTest(LiquidityWallSchemas.BUCKET));
        assertEquals(2, relay.schemaIdRefusedForTest(LiquidityWallSchemas.VOICE));
        assertEquals(0, relay.dropsForTest());
        assertTrue(relay.metrics().contains("gateway_es_liquidity_wall_voice_schema_id_refused_total 2\n"));
        // the right id on the right topic still passes afterwards: the refusal never poisoned the lane
        relay.accept(VOICE, KEY, voiceBytes, NOW);
        assertEquals(List.of("es-liquidity-voice"), sink.events());
        assertEquals(1, wire.decodes.get());
    }

    @Test
    void aStaleDashboardFrameIsFoldedButNeverShownAsLiveAndTheReplayIsGatedTheSameWay() throws Exception {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = wire.relay(sink);
        long ttl = SETTINGS.esLiquidityWallFreshMs();
        relay.accept(DASHBOARD, KEY, wire.bytes(EsLiquidityWallFixtures.dashboard(NOW - ttl - 1)), NOW);
        assertTrue(sink.frames.isEmpty(), "older than the bound: folded, not broadcast");
        assertEquals(1, relay.staleFramesForTest());
        assertEquals(1, relay.history().framesFolded());
        relay.accept(DASHBOARD, KEY, wire.bytes(EsLiquidityWallFixtures.dashboard(NOW + EsLiquidityWallRelay.FUTURE_SKEW_MS + 1)), NOW);
        assertTrue(sink.frames.isEmpty(), "ahead of the clock beyond the skew allowance: not live either");
        relay.accept(DASHBOARD, KEY, wire.bytes(EsLiquidityWallFixtures.dashboard(NOW - ttl)), NOW);
        assertEquals(List.of("es-liquidity-heatmap"), sink.events());

        Sink replay = new Sink();
        relay.replay(replay, NOW);
        assertEquals(List.of("es-liquidity-heatmap"), replay.events(), "fresh at replay time");
        assertTrue(replay.json(0).get("cached").booleanValue(), "a replayed frame is cached:true");
        Sink later = new Sink();
        relay.replay(later, NOW + ttl + 1);
        assertTrue(later.frames.isEmpty(), "the cached frame crossed the bound: absent, never stale-as-live");
    }

    @Test
    void theVoiceIsDeliveredMarkedStaleBeyondItsBoundAndTheReplayCarriesOnlyRecentEventsInLogOrder() throws Exception {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = wire.relay(sink);
        long bound = SETTINGS.esLiquidityWallVoiceFreshMs();
        long window = SETTINGS.esLiquidityWallEventReplayMs();
        relay.accept(VOICE, KEY, wire.bytes(EsLiquidityWallFixtures.voice(NOW - bound - 1)), NOW);
        assertTrue(sink.json(0).get("stale").booleanValue(), "delivered, marked stale");
        assertFalse(sink.json(0).get("cached").booleanValue());
        relay.accept(VOICE, KEY, wire.bytes(EsLiquidityWallFixtures.voice(NOW)), NOW);
        assertFalse(sink.json(1).get("stale").booleanValue());
        relay.accept(EVENTS, "C0", wire.bytes(EsLiquidityWallFixtures.born(NOW - window - 1, "C0")), NOW);   // the session-open catch-up: folded, ringed, not live
        relay.accept(EVENTS, "C1", wire.bytes(EsLiquidityWallFixtures.born(NOW - 2000, "C1")), NOW);
        relay.accept(EVENTS, "C1", wire.bytes(EsLiquidityWallFixtures.retired(NOW - 1000, "C1", "AGED")), NOW);
        assertEquals(4, sink.frames.size(), "an event outside the replay window is never broadcast as if it had just happened");
        assertEquals(1, relay.staleEventsForTest());
        assertEquals(1, relay.history().render(NOW).indexOf("\"clusterId\":\"C0\"") > 0 ? 1 : 0, "but it is folded into the projection");
        assertTrue(relay.metrics().contains("gateway_es_liquidity_wall_stale_events_total 1\n"));

        Sink replay = new Sink();
        relay.replay(replay, NOW);
        assertEquals(List.of("es-liquidity-wall", "es-liquidity-wall", "es-liquidity-voice"), replay.events(), "C0 is outside the replay window");
        assertEquals("BORN", replay.json(0).get("kind").asText());
        assertEquals("C1", replay.json(0).get("wall").get("clusterId").asText());
        assertEquals("RETIRED", replay.json(1).get("kind").asText());
        for (int i = 0; i < 3; i++) {
            assertTrue(replay.json(i).get("cached").booleanValue(), "every replayed frame is cached:true");
        }
        assertFalse(replay.json(2).get("stale").booleanValue());
        Sink old = new Sink();
        relay.replay(old, NOW + bound + 1);
        assertTrue(old.json(2).get("stale").booleanValue(), "the last voice record is replayed marked by its age now");
        Sink muchLater = new Sink();
        relay.replay(muchLater, NOW + window + 1001);
        assertEquals(List.of("es-liquidity-voice"), muchLater.events(), "the ring's events aged out of the replay window; the voice stays, marked stale");
    }

    @Test
    void theEventRingIsBoundedAndRefusedOrUndecodableRecordsAreCountedNotForwarded() throws Exception {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = wire.relay(sink);
        int ring = SETTINGS.esLiquidityWallEventRing();
        for (int i = 0; i <= ring; i++) {
            relay.accept(EVENTS, "C" + i, wire.bytes(EsLiquidityWallFixtures.born(NOW + i, "C" + i)), NOW + i);
        }
        Sink replay = new Sink();
        relay.replay(replay, NOW + ring);
        assertEquals(ring, replay.frames.size());
        assertEquals("C1", replay.json(0).get("wall").get("clusterId").asText(), "the oldest left the ring");
        relay.accept(EVENTS, "C1", null, NOW);                                                                      // a tombstone
        relay.accept(DASHBOARD, KEY, wire.bytes(EsLiquidityWallFixtures.dashboard(NOW, "LIVE").set("schemaVersion", 2).build()), NOW);   // right id, refused record
        relay.accept("some.other.topic", KEY, wire.bytes(EsLiquidityWallFixtures.dashboard(NOW)), NOW);
        byte[] unknownId = wire.bytes(EsLiquidityWallFixtures.voice(NOW));
        unknownId[4] = (byte) 0x99;   // 3407 -> a foreign id: refused by the id check, not by the registry
        relay.accept(VOICE, KEY, unknownId, NOW);
        assertEquals(3, relay.dropsForTest());
        assertEquals(1, relay.schemaIdRefusedForTest(LiquidityWallSchemas.VOICE));
        assertEquals(ring + 1, sink.frames.size(), "nothing refused was forwarded");
        assertTrue(relay.metrics().contains("gateway_es_liquidity_wall_drops_total 3\n"));
        assertTrue(relay.metrics().contains("gateway_es_liquidity_wall_events_accepted_total " + (ring + 1) + "\n"));
    }

    @Test
    void aDisabledRelayStartsNoConsumerAndCachesNothing() {
        assertFalse(SETTINGS.esLiquidityWallRelayEnabled(), "ES_LIQUIDITY_WALL_RELAY_ENABLED defaults to false");
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = new EsLiquidityWallRelay(SETTINGS, sink, null);
        relay.start();
        assertFalse(relay.consuming());
        relay.replay(sink, NOW);
        assertTrue(sink.frames.isEmpty());
        assertTrue(relay.metrics().contains("gateway_es_liquidity_wall_enabled 0\n"));
        relay.stop();
    }
}
