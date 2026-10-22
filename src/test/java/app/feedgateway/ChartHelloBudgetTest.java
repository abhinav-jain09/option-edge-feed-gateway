package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/**
 * The hello with every relay on: the five relay parts (studies, geometry, tools, waves, trade tools) share what the fixed fields leave of
 * the 1 MiB guard. Worst case = every part at its own cap (512 + 384 + 384 + 384 + 512 KiB = 2 176 KiB of content): the arbitration trims
 * by priority (studies, geometry, tools, waves, trade tools), each part by its own rule, and the hello stays under the guard.
 */
class ChartHelloBudgetTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static FeedGatewayService service(boolean tradeTools) {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).studiesEnabled();
        doReturn(true).when(settings).geometryEnabled();
        doReturn(true).when(settings).structureToolsEnabled();
        doReturn(true).when(settings).waveCycleEnabled();
        doReturn(tradeTools).when(settings).tradeToolsEnabled();
        return new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null);
    }

    private static TaRelay relay(FeedGatewayService s, String event) {
        return s.taRelaysForTest().stream().filter(r -> r.handles(event)).findFirst().orElseThrow();
    }

    private static GenericRecord pad(GenericRecord r, int chars) {
        return TaFixtures.put(r, "parameterSetHash", "h".repeat(chars));
    }

    /** Fills every part; {@code perSet} is the size of one tools / geometry set, one wave record and one trade-tools flow bar (all in characters). */
    static void fill(FeedGatewayService s, int studiesBars, int studiesBarChars, int perSet, int flowBars, int flowBarChars) {
        for (String symbol : new String[] {"SPX", "ES"}) {
            for (String tf : new String[] {"T1M", "T5M", "T15M"}) {
                for (int i = 0; i < studiesBars; i++) {
                    GenericRecord bar = StudiesFixtures.bar(symbol, tf, i);
                    bar.put("calibration", "p".repeat(studiesBarChars));   // pads the bar so the studies reach their share of the hello
                    StudiesFixtures.onLive(s.studiesForTest(), StudiesRelay.BAR_EVENT, symbol + "|" + tf.substring(1).toLowerCase(), bar);
                }
            }
            StudiesFixtures.onLive(s.studiesForTest(), StudiesRelay.CURRENT_EVENT, symbol, StudiesFixtures.current(symbol, 1));
        }
        String[] tools = {"M1", "M5", "M15"}, geometry = {"TF_1M", "TF_5M", "TF_15M"};
        for (int t = 0; t < 3; t++) {
            relay(s, TimeframeSetRelay.TOOLS_EVENT).onLive(TimeframeSetRelay.TOOLS_EVENT, pad(TaFixtures.tools(tools[t], 1 + t, "OK"), perSet));
            relay(s, TimeframeSetRelay.GEOMETRY_EVENT).onLive(TimeframeSetRelay.GEOMETRY_EVENT, pad(TaFixtures.geometrySet(geometry[t], 1 + t), perSet));
            GenericRecord waves = TaFixtures.waveSet(tools[t], 1, 1, "OK");
            TaFixtures.put(TaFixtures.child(waves, "env"), "parameterSetHash", "h".repeat(perSet));
            relay(s, WaveCycleRelay.WAVES_EVENT).onLive(WaveCycleRelay.WAVES_EVENT, waves);
            relay(s, WaveCycleRelay.CYCLES_EVENT).onLive(WaveCycleRelay.CYCLES_EVENT, TaFixtures.cycleSet(tools[t], 1, 1, "OK"));
            if (flowBars > 0) {
                for (int i = 0; i < flowBars; i++) {
                    relay(s, TradeToolsRelay.ROLES[4]).onLive(TradeToolsRelay.ROLES[4], TaFixtures.put(TaFixtures.flowBar(tools[t], 60_000L * (i + 1), 10 + i),
                            "contractEpoch", "E".repeat(flowBarChars)));
                }
            }
        }
    }

    private static int bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int bytesOf(JsonNode hello, String field) {
        return hello.has(field) ? bytes(hello.get(field).toString()) : 0;
    }

    @Test
    void theWorstCaseEveryPartAtItsCapStaysUnderTheGuardAndLosesTheLowestPriorityFirst() throws Exception {
        var s = service(true);
        // 8 bars x 6 series of ~8 KiB fill the studies cap; 190 KB sets: two of a part's three fit its 384 KiB; 50 KB flow bars x 4 per timeframe
        fill(s, 8, 11_000, 190_000, 4, 50_000);
        long sumOfCaps = (StudiesRelay.HELLO_MAX_BYTES + 3 * TimeframeSetRelay.HELLO_PART_BYTES + TradeToolsRelay.HELLO_PART_BYTES) / 1024;
        assertEquals(2176, sumOfCaps - 0, "512 + 384 + 384 + 384 + 512 KiB of content before arbitration");
        // before arbitration every chart part really holds at least half of its own cap (two of three 190 KB sets; four 50 KB flow bars x 3)
        for (TaRelay relay : s.taRelaysForTest()) {
            long own = StudiesRelay.utf8Length(relay.helloField(Long.MAX_VALUE));
            assertTrue(own > TimeframeSetRelay.HELLO_PART_BYTES / 2 && own <= TradeToolsRelay.HELLO_PART_BYTES, relay.getClass().getSimpleName() + " holds " + own + " bytes of its own cap");
        }
        String hello = s.cvdHelloJson();
        assertTrue(bytes(hello) <= FeedGatewayService.HELLO_FRAME_HARD_BYTES, "the hello is " + bytes(hello) + " bytes");
        JsonNode node = JSON.readTree(hello);
        // the parts before the trade tools are exactly what they are without the trade tools: priority, not proportional cuts
        JsonNode without = JSON.readTree(fillAndHello(false));
        for (String field : List.of("studies", "studyBars", "esStudies", "esStudyBars", "geometry", "tools", "waves")) {
            assertEquals(without.get(field), node.get(field), field + " does not depend on a lower-priority part");
        }
        // every part keeps its lists in order: once a timeframe is null, every later one is null
        for (String field : List.of("geometry", "tools", "waves", "tradeTools")) {
            boolean ended = false;
            for (String tf : List.of("1m", "5m", "15m")) {
                boolean none = node.get(field).get(tf).isNull();
                assertFalse(ended && !none, field + " " + tf + " follows a null");
                ended |= none;
            }
        }
        long studiesBytes = bytesOf(node, "studyBars") + bytesOf(node, "esStudyBars") + bytesOf(node, "studies") + bytesOf(node, "esStudies");
        assertTrue(studiesBytes > StudiesRelay.HELLO_MAX_BYTES * 0.7 && studiesBytes <= StudiesRelay.HELLO_MAX_BYTES, "the studies really fill their share: " + studiesBytes + " bytes");
        assertNotEquals(0, bytesOf(node, "studies"));
        assertFalse(node.get("studies").get("5m").isNull(), "the studies' current states are never traded for a newer layer");
    }

    private String fillAndHello(boolean tradeTools) {
        var s = service(tradeTools);
        fill(s, 8, 11_000, 190_000, tradeTools ? 4 : 0, 50_000);
        return s.cvdHelloJson();
    }

    @Test
    void aTypicalDayFillsSixHundredThousandBytesOfSetsAndStillFitsOrTrimsDeterministically() throws Exception {
        var s = service(true);
        fill(s, 8, 4_000, 40_000, 3, 53_000);   // tools / geometry / waves ~40 KB, a trade-tools bundle ~160 KB
        String hello = s.cvdHelloJson();
        JsonNode node = JSON.readTree(hello);
        long parts = 0;
        for (String field : List.of("studies", "studyBars", "esStudies", "esStudyBars", "geometry", "tools", "waves", "tradeTools")) {
            parts += bytesOf(node, field);
        }
        assertTrue(parts > FeedGatewayService.HELLO_FRAME_HARD_BYTES * 0.6, "a typical day is a large hello: " + parts + " bytes of parts");
        assertTrue(bytes(hello) <= FeedGatewayService.HELLO_FRAME_HARD_BYTES, "hello " + bytes(hello));
        assertEquals(hello, s.cvdHelloJson(), "deterministic: the same retained state renders the same hello");
    }

    @Test
    void whenTheFixedFieldsLeaveNoBudgetEveryChartPartKeepsOnlyItsSkeletonAndTheHelloStaysUnderTheHardGuard() throws Exception {
        // a retained structure record at its own cap (one frame guard less the 4 KiB margin) leaves the hello no budget beyond the margin
        String record = "{\"x\":\"" + "y".repeat(FeedGatewayService.HELLO_FRAME_TARGET_BYTES - 64) + "\"}";
        System.setProperty("GATEWAY_MARKET_STRUCTURE_ENABLED", "true");
        try {
            var t = new FeedGatewayService(spyAll(), JSON, new HpsfGatewayViewMapper(), null);
            assertTrue(t.retainMarketStructure(1L, record), "the fixed field is really retained (the precondition of the whole scenario)");
            assertTrue(t.cvdHelloJson().contains("\"structure\":{\"x\":"), "and it rides the hello");
            fill(t, 8, 100, 1_000, 1, 1_000);
            String hello = t.cvdHelloJson();
            JsonNode node = JSON.readTree(hello);
            for (String field : List.of("geometry", "tools", "waves", "tradeTools")) {
                assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null}", node.get(field).toString(), field);
            }
            assertTrue(node.get("studies").get("5m").isNull(), "the studies degrade to null last and only with no budget left");
            assertTrue(bytes(hello) < FeedGatewayService.HELLO_FRAME_HARD_BYTES, "only the fixed field and the skeletons remain, inside the margin: " + bytes(hello));
        } finally {
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_ENABLED");
        }
    }

    private static GatewaySettings spyAll() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).studiesEnabled();
        doReturn(true).when(settings).geometryEnabled();
        doReturn(true).when(settings).structureToolsEnabled();
        doReturn(true).when(settings).waveCycleEnabled();
        doReturn(true).when(settings).tradeToolsEnabled();
        doReturn(true).when(settings).marketStructureEnabled();
        return settings;
    }
}
