package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The chart relays' switches: every flag OFF by default and independent, its own topics, the frame allowlist, the hello fields' presence as the flag. */
class TaRelaysWiringTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FLAGS = List.of("GATEWAY_STRUCTURE_TOOLS_ENABLED", "GATEWAY_GEOMETRY_ENABLED", "GATEWAY_TRADE_TOOLS_ENABLED",
            "GATEWAY_WAVE_CYCLE_ENABLED");
    private static final List<String> FIELDS = List.of("tools", "geometry", "tradeTools", "waves");

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), JSON, new HpsfGatewayViewMapper(), null);
    }

    @Test
    void everyFlagIsOffByDefaultAndNamesTheProducersTopics() {
        GatewaySettings s = new GatewaySettings();
        assertFalse(s.structureToolsEnabled());
        assertFalse(s.geometryEnabled());
        assertFalse(s.tradeToolsEnabled());
        assertFalse(s.waveCycleEnabled());
        assertEquals("options.spx.market-structure.tools", s.structureToolsTopic());
        assertEquals("options.spx.market-structure.geometry", s.geometryTopic());
        assertEquals(List.of("futures.pf.chart", "futures.pf.columns", "futures.pf.events", "futures.volume-nodes", "futures.flow.bars", "futures.flow.events"),
                s.tradeToolsTopics());
        assertEquals(TradeToolsRelay.ROLES.length, s.tradeToolsTopics().size());
        assertEquals("options.spx.wave-cycle.waves", s.waveCycleWavesTopic());
        assertEquals("options.spx.wave-cycle.cycles", s.waveCycleCyclesTopic());
    }

    @Test
    void theTopicNamesAreOverridable() {
        System.setProperty("KAFKA_TT_PF_EVENTS_TOPIC", "x.events");
        System.setProperty("KAFKA_WAVE_CYCLE_WAVES_TOPIC", "x.waves");
        try {
            GatewaySettings s = new GatewaySettings();
            assertEquals("x.events", s.tradeToolsTopics().get(2));
            assertEquals("x.waves", s.waveCycleWavesTopic());
        } finally {
            System.clearProperty("KAFKA_TT_PF_EVENTS_TOPIC");
            System.clearProperty("KAFKA_WAVE_CYCLE_WAVES_TOPIC");
        }
    }

    @Test
    void everyFrameTypeIsAGlobalBroadcastEventAndNothingElseIsAddedByAccident() {
        Set<String> frames = new HashSet<>(List.of(TimeframeSetRelay.TOOLS_EVENT, TimeframeSetRelay.GEOMETRY_EVENT, WaveCycleRelay.WAVES_EVENT, WaveCycleRelay.CYCLES_EVENT));
        frames.addAll(List.of(TradeToolsRelay.FRAME));
        assertEquals(16, frames.size());
        for (String frame : frames) {
            assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(frame), frame);
        }
        long chartFrames = FeedGatewayService.GLOBAL_BROADCAST_EVENTS.stream()
                .filter(e -> e.startsWith("trade-tools-") || e.startsWith("wave-cycle-") || e.equals("market-structure-tools") || e.equals("market-structure-geometry")).count();
        assertEquals(16, chartFrames);
    }

    @Test
    void flagsOffTheHelloHasNoFieldOfTheseStreamsAndNoHelloIsSent() {
        var off = service();
        assertFalse(off.sendsCvdHello());
        for (String field : FIELDS) {
            assertFalse(off.cvdHelloJson().contains("\"" + field + "\""), field);
        }
        assertFalse(off.metrics().contains("gateway_trade_tools_"));
        assertFalse(off.metrics().contains("gateway_wave_cycle_"));
    }

    @Test
    void eachFlagAloneAddsExactlyItsHelloFieldWithNullsBeforeAnyRecord() throws Exception {
        for (int i = 0; i < FLAGS.size(); i++) {
            System.setProperty(FLAGS.get(i), "true");
            try {
                var on = service();
                assertTrue(on.sendsCvdHello(), FLAGS.get(i));
                JsonNode hello = JSON.readTree(on.cvdHelloJson());
                for (int j = 0; j < FIELDS.size(); j++) {
                    assertEquals(i == j, hello.has(FIELDS.get(j)), FLAGS.get(i) + " / " + FIELDS.get(j) + ": independent layers");
                }
                assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null}", hello.get(FIELDS.get(i)).toString(), "null = none retained; the field's presence = flag on");
            } finally {
                System.clearProperty(FLAGS.get(i));
            }
        }
    }

    @Test
    void theTopicsAreBoundOnTheAvroLiveConsumerOnlyBehindTheirFlags() throws Exception {
        String src = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int avro = src.indexOf("private void runAvroLiveConsumer()");
        int avroEnd = src.indexOf("\n    }\n", avro);
        int json = src.indexOf("private void runJsonStateLiveConsumer()");
        int jsonEnd = src.indexOf("\n    }\n", json);
        for (String binding : List.of("settings.structureToolsEnabled()", "settings.geometryEnabled()", "settings.tradeToolsEnabled()", "settings.waveCycleEnabled()",
                "settings.marketStructureEnabled()", "settings.marketStructurePatternsEnabled()")) {
            int at = src.indexOf("if (" + binding + ") {", avro);
            assertTrue(at > avro && at < avroEnd, binding + " guards a binding of the AVRO live consumer");
            assertFalse(src.substring(json, jsonEnd).contains(binding), binding + " is not bound on the JSON state consumer: the converted topics are Avro only");
        }
        assertTrue(src.indexOf("properties.put(GatewayAvroDeserializer.TOLERANT_TOPICS", 0) > 0, "the converted and chart topics never fail the poll");
    }
}
