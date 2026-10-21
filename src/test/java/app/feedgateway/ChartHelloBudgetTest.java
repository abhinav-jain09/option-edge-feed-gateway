package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static void fill(FeedGatewayService s, int studiesBars, int perSet, int flowBars, int flowBarChars) {
        for (String symbol : new String[] {"SPX", "ES"}) {
            for (String tf : new String[] {"T1M", "T5M", "T15M"}) {
                for (int i = 0; i < studiesBars; i++) {
                    StudiesFixtures.onLive(s.studiesForTest(), StudiesRelay.BAR_EVENT, symbol + "|" + tf.substring(1).toLowerCase(), StudiesFixtures.bar(symbol, tf, i));
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
        fill(s, 8, 190_000, 4, 50_000);
        long sumOfCaps = (StudiesRelay.HELLO_MAX_BYTES + 3 * TimeframeSetRelay.HELLO_PART_BYTES + TradeToolsRelay.HELLO_PART_BYTES) / 1024;
        assertEquals(2176, sumOfCaps - 0, "512 + 384 + 384 + 384 + 512 KiB of content before arbitration");
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
        assertNotEquals(0, bytesOf(node, "studies"));
        assertFalse(node.get("studies").get("5m").isNull(), "the studies' current states are never traded for a newer layer");
    }

    private String fillAndHello(boolean tradeTools) {
        var s = service(tradeTools);
        fill(s, 8, 190_000, tradeTools ? 4 : 0, 50_000);
        return s.cvdHelloJson();
    }

    @Test
    void aTypicalDayFillsSixHundredThousandBytesOfSetsAndStillFitsOrTrimsDeterministically() throws Exception {
        var s = service(true);
        fill(s, 8, 40_000, 3, 53_000);   // tools / geometry / waves ~40 KB, a trade-tools bundle ~160 KB
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
    void whenTheFixedFieldsAlreadyFillTheGuardEveryPartKeepsOnlyItsSkeleton() throws Exception {
        var s = service(true);
        fill(s, 8, 1_000, 1, 1_000);
        // a retained structure record of ~1 MiB (the pre-existing fixed field) leaves no budget
        s.retainMarketStructure(1L, "{\"x\":\"" + "y".repeat(FeedGatewayService.HELLO_FRAME_HARD_BYTES) + "\"}");
        System.setProperty("GATEWAY_MARKET_STRUCTURE_ENABLED", "true");
        try {
            var t = new FeedGatewayService(spyAll(), JSON, new HpsfGatewayViewMapper(), null);
            t.retainMarketStructure(1L, "{\"x\":\"" + "y".repeat(FeedGatewayService.HELLO_FRAME_HARD_BYTES) + "\"}");
            fill(t, 8, 1_000, 1, 1_000);
            JsonNode node = JSON.readTree(t.cvdHelloJson());
            for (String field : List.of("geometry", "tools", "waves", "tradeTools")) {
                assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null}", node.get(field).toString(), field);
            }
            assertNull(node.get("studies"), "the fixed fields leave no room even for the studies skeleton: the hello goes without them (studiesHelloBudget)");
            assertTrue(bytes(t.cvdHelloJson()) < FeedGatewayService.HELLO_FRAME_HARD_BYTES + 512,   // the four chart skeletons (~55 bytes each) over the fixed field
                    "only the fixed field and the skeletons remain");
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
