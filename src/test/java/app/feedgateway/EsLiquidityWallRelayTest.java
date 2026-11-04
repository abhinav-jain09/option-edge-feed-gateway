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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/**
 * The relay at its boundary: the three records decoded through their REGISTERED schema ids by {@link GatewayAvroDeserializer} (the gateway's own
 * deserializer against an in-memory registry holding the fixed ids 3403/3404/3407), the freshness gates, the connect replay, and a disabled relay.
 */
class EsLiquidityWallRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GatewaySettings SETTINGS = new GatewaySettings();
    private static final String DASHBOARD = SETTINGS.esLiquidityWallDashboardTopic();
    private static final String EVENTS = SETTINGS.esLiquidityWallEventsTopic();
    private static final String VOICE = SETTINGS.esLiquidityWallVoiceTopic();

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
    }

    /** A Confluent frame of the record under its fixed id, as es-liquidity-wall-service writes it, decoded as the gateway's consumer decodes it. */
    private static final class Wire implements AutoCloseable {
        final KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        final GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer();

        Wire(String scope) {
            SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(scope);
            LiquidityWallSchemas.register(registry);
            serializer.configure(LiquidityWallSchemas.serdeConfig("mock://" + scope), false);
            Map<String, Object> reader = new HashMap<>();
            reader.put("schema.registry.url", "mock://" + scope);
            reader.put("specific.avro.reader", "false");
            reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
            reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, DASHBOARD + "," + EVENTS + "," + VOICE);
            deserializer.configure(reader, false);
        }

        Object roundTrip(String topic, GenericRecord record, LiquidityWallSchemas.Subject subject) {
            byte[] bytes = serializer.serialize(topic, record);
            assertEquals(subject.id(), LiquidityWallSchemas.assertFramedAs(bytes, subject), "the frame carries the fixed id");
            return deserializer.deserialize(topic, bytes);
        }

        @Override
        public void close() {
            serializer.close();
            deserializer.close();
        }
    }

    @Test
    void eachRecordIsDecodedThroughItsRegisteredIdAndRelayedUnderItsEvent() throws Exception {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = new EsLiquidityWallRelay(SETTINGS, sink);
        try (Wire wire = new Wire("es-liquidity-wall-relay")) {
            relay.accept(DASHBOARD, KEY, wire.roundTrip(DASHBOARD, EsLiquidityWallFixtures.dashboard(NOW), LiquidityWallSchemas.DASHBOARD), NOW + 500);
            relay.accept(EVENTS, "C1", wire.roundTrip(EVENTS, EsLiquidityWallFixtures.born(NOW, "C1"), LiquidityWallSchemas.EVENT), NOW + 500);
            relay.accept(EVENTS, KEY + "|FLIP", wire.roundTrip(EVENTS, EsLiquidityWallFixtures.flip(NOW), LiquidityWallSchemas.EVENT), NOW + 500);
            relay.accept(VOICE, KEY, wire.roundTrip(VOICE, EsLiquidityWallFixtures.voice(NOW), LiquidityWallSchemas.VOICE), NOW + 500);
        }
        assertEquals(List.of("es-liquidity-heatmap", "es-liquidity-wall", "es-liquidity-wall", "es-liquidity-voice"), sink.events());
        JsonNode heatmap = JSON.readTree(sink.frames.get(0)[1]);
        assertEquals("Rithmic Test", heatmap.get("depthEnvironment").asText());
        assertEquals(NOW, heatmap.get("bucketEnd").longValue());
        assertEquals("FLIP", JSON.readTree(sink.frames.get(2)[1]).get("kind").asText());
        JsonNode voice = JSON.readTree(sink.frames.get(3)[1]);
        assertEquals("liquidity flip - downside, 7612.25", voice.get("text").asText());
        assertFalse(voice.get("stale").booleanValue());
        assertEquals(0, relay.dropsForTest());
        assertEquals(1, relay.history().framesFolded());
        for (String event : List.of("es-liquidity-heatmap", "es-liquidity-wall", "es-liquidity-voice")) {
            assertTrue(FeedGatewayService.isGlobalBroadcastEvent(event), event + " must survive per-session routing");
        }
    }

    @Test
    void aStaleDashboardFrameIsFoldedButNeverShownAsLiveAndTheReplayIsGatedTheSameWay() {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = new EsLiquidityWallRelay(SETTINGS, sink);
        long ttl = SETTINGS.esLiquidityWallFreshMs();
        relay.accept(DASHBOARD, KEY, EsLiquidityWallFixtures.dashboard(NOW - ttl - 1), NOW);
        assertTrue(sink.frames.isEmpty(), "older than the bound: folded, not broadcast");
        assertEquals(1, relay.staleFramesForTest());
        assertEquals(1, relay.history().framesFolded());
        relay.accept(DASHBOARD, KEY, EsLiquidityWallFixtures.dashboard(NOW + EsLiquidityWallRelay.FUTURE_SKEW_MS + 1), NOW);
        assertTrue(sink.frames.isEmpty(), "ahead of the clock beyond the skew allowance: not live either");
        relay.accept(DASHBOARD, KEY, EsLiquidityWallFixtures.dashboard(NOW - ttl), NOW);
        assertEquals(List.of("es-liquidity-heatmap"), sink.events());

        Sink replay = new Sink();
        relay.replay(replay, NOW);
        assertEquals(List.of("es-liquidity-heatmap"), replay.events(), "fresh at replay time");
        Sink later = new Sink();
        relay.replay(later, NOW + ttl + 1);
        assertTrue(later.frames.isEmpty(), "the cached frame crossed the bound: absent, never stale-as-live");
    }

    @Test
    void theVoiceIsDeliveredMarkedStaleBeyondItsBoundAndTheReplayCarriesTheRingInLogOrder() throws Exception {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = new EsLiquidityWallRelay(SETTINGS, sink);
        long bound = SETTINGS.esLiquidityWallVoiceFreshMs();
        relay.accept(VOICE, KEY, EsLiquidityWallFixtures.voice(NOW - bound - 1), NOW);
        assertTrue(JSON.readTree(sink.frames.get(0)[1]).get("stale").booleanValue(), "delivered, marked stale");
        relay.accept(VOICE, KEY, EsLiquidityWallFixtures.voice(NOW), NOW);
        assertFalse(JSON.readTree(sink.frames.get(1)[1]).get("stale").booleanValue());
        relay.accept(EVENTS, "C1", EsLiquidityWallFixtures.born(NOW - 2000, "C1"), NOW);
        relay.accept(EVENTS, "C1", EsLiquidityWallFixtures.retired(NOW - 1000, "C1", "AGED"), NOW);

        Sink replay = new Sink();
        relay.replay(replay, NOW + 1000);
        assertEquals(List.of("es-liquidity-wall", "es-liquidity-wall", "es-liquidity-voice"), replay.events());
        assertEquals("BORN", JSON.readTree(replay.frames.get(0)[1]).get("kind").asText());
        assertEquals("RETIRED", JSON.readTree(replay.frames.get(1)[1]).get("kind").asText());
        assertFalse(JSON.readTree(replay.frames.get(2)[1]).get("stale").booleanValue());
        Sink old = new Sink();
        relay.replay(old, NOW + bound + 1);
        assertTrue(JSON.readTree(old.frames.get(2)[1]).get("stale").booleanValue(), "the last voice record is replayed marked by its age now");
    }

    @Test
    void theEventRingIsBoundedAndRefusedOrUndecodableRecordsAreCountedNotForwarded() {
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = new EsLiquidityWallRelay(SETTINGS, sink);
        int ring = SETTINGS.esLiquidityWallEventRing();
        for (int i = 0; i <= ring; i++) {
            relay.accept(EVENTS, "C" + i, EsLiquidityWallFixtures.born(NOW + i, "C" + i), NOW + i);
        }
        Sink replay = new Sink();
        relay.replay(replay, NOW);
        assertEquals(ring, replay.frames.size());
        assertTrue(replay.frames.get(0)[1].contains("\"clusterId\":\"C1\""), "the oldest left the ring");
        relay.accept(EVENTS, "C1", GatewayAvroDeserializer.UNDECODABLE, NOW);
        relay.accept(DASHBOARD, KEY, EsLiquidityWallFixtures.born(NOW, "C1"), NOW);
        relay.accept("some.other.topic", KEY, EsLiquidityWallFixtures.dashboard(NOW), NOW);
        assertEquals(2, relay.dropsForTest());
        assertEquals(ring + 1, sink.frames.size(), "nothing refused was forwarded");
        assertTrue(relay.metrics().contains("gateway_es_liquidity_wall_undecodable_total 1\n"));
        assertTrue(relay.metrics().contains("gateway_es_liquidity_wall_drops_total 2\n"));
    }

    @Test
    void aDisabledRelayStartsNoConsumerAndCachesNothing() {
        assertFalse(SETTINGS.esLiquidityWallRelayEnabled(), "ES_LIQUIDITY_WALL_RELAY_ENABLED defaults to false");
        Sink sink = new Sink();
        EsLiquidityWallRelay relay = new EsLiquidityWallRelay(SETTINGS, sink);
        relay.start();
        assertFalse(relay.consuming());
        relay.replay(sink, NOW);
        assertTrue(sink.frames.isEmpty());
        assertTrue(relay.metrics().contains("gateway_es_liquidity_wall_enabled 0\n"));
        relay.stop();
    }
}
