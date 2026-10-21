package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The trade-tools relay: dispatch on the record name, routing by chart, horizon and width, bounded retention, the bundle hello, recovery. */
class TradeToolsRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CHART = "trade-tools-pf-chart", COLUMN = "trade-tools-pf-column", EVENTS = "trade-tools-pf-events",
            NODES = "trade-tools-volume-nodes", FLOW = "trade-tools-flow-bar", CLIMAX = "trade-tools-climax";
    private static final List<String> ARRAYS = List.of("pfCharts", "pfColumns", "pfBreaks", "pfLines", "pfCounts", "pfMaCrosses", "pfVolDivs",
            "pfMfDivs", "pfVolChecks", "flowBars", "climax", "nodes");

    private static JsonNode bundles(TradeToolsRelay relay, long budget) throws Exception {
        return JSON.readTree("{" + relay.helloField(budget).substring(1) + "}").get("tradeTools");
    }

    private static List<String> names(Iterator<String> it) {
        List<String> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }

    @Test
    void aChartThatHasNoColumnYetIsRelayedWithItsMinusOneIndex() throws Exception {
        // the producer's first state of a chart (Wire: currentColumnIndex = nextIndex - 1): integration check finding F1
        var relay = new TradeToolsRelay();
        org.apache.avro.generic.GenericRecord chart = TaFixtures.chart("c5", "M5", 10);
        chart.put("currentColumnIndex", -1L);
        chart.put("columnsRetained", 0);
        String[] frame = relay.onLive(CHART, chart);
        assertNotNull(frame, "the gateway never judges the index: the page does");
        assertEquals(-1, JSON.readTree(frame[1]).get("currentColumnIndex").asLong());
        assertEquals(0, relay.dropsForTest());
        assertEquals(1, bundles(relay, Long.MAX_VALUE).get("5m").get("pfCharts").size());
    }

    @Test
    void theTwelveRecordsEachMakeTheirOwnFrameTypeAndAreRoutedToTheirTimeframe() {
        var relay = new TradeToolsRelay();
        assertEquals(CHART, relay.onLive(CHART, TaFixtures.chart("c5", "M5", 10))[0]);
        assertEquals("trade-tools-pf-column", relay.onLive(COLUMN, TaFixtures.column("c5", 1, 11))[0]);
        String[] kinds = {"PfBreakEvent", "PfLineEvent", "PfCountEvent", "PfMaCrossEvent", "PfVolumeDivergenceEvent", "PfMoneyFlowDivergenceEvent", "PfBreakVolumeCheck"};
        String[] frames = {"trade-tools-pf-break", "trade-tools-pf-line", "trade-tools-pf-count", "trade-tools-pf-macross", "trade-tools-pf-voldiv",
            "trade-tools-pf-mfdiv", "trade-tools-pf-volcheck"};
        for (int i = 0; i < kinds.length; i++) {
            assertEquals(frames[i], relay.onLive(EVENTS, TaFixtures.pfEvent(kinds[i], "c5", "e" + i, 100 + i, 12))[0], "dispatch on the writer schema's record name: " + kinds[i]);
        }
        assertEquals(NODES, relay.onLive(NODES, TaFixtures.nodes("RTH", 5, 13))[0]);
        assertEquals("trade-tools-flow-bar", relay.onLive(FLOW, TaFixtures.flowBar("M15", 900_000, 14))[0]);
        assertEquals(CLIMAX, relay.onLive(CLIMAX, TaFixtures.climax("k1", "M1", 60_000, 15))[0]);
        assertEquals(12, TradeToolsRelay.FRAME.length);
        assertEquals(0, relay.dropsForTest());
        assertEquals(12, relay.acceptedForTest());
    }

    @Test
    void theBoundaryDropsAndCountsAndTheGatewayNeverGuessesAHorizon() {
        var relay = new TradeToolsRelay();
        assertNull(relay.onLive(CHART, TaFixtures.chart("h1", "H1", 1)), "H1 has no page timeframe");
        assertNull(relay.onLive(CHART, TaFixtures.chart("h4", "H4", 1)));
        assertNull(relay.onLive(FLOW, TaFixtures.flowBar("H1", 1, 1)));
        assertNull(relay.onLive(CLIMAX, TaFixtures.climax("k", "H4", 1, 1)));
        assertNull(relay.onLive(COLUMN, TaFixtures.column("never-seen", 1, 1)), "no retained chart yet: parked (bounded), not guessed from the text of the chart id and not broadcast");
        assertNull(relay.onLive(EVENTS, TaFixtures.pfEvent("PfBreakEvent", "never-seen", "e", 1, 1)));
        assertNull(relay.onLive(CHART, TaFixtures.put(TaFixtures.chart("c", "M1", 1), "validation", "VALIDATED")), "only UNVALIDATED is relayed");
        assertNull(relay.onLive(CHART, TaFixtures.put(TaFixtures.chart("c", "M1", 1), "schemaVersion", 2)));
        assertNull(relay.onLive(CHART, TaFixtures.put(TaFixtures.chart("c", "M1", 1), "symbol", "")));
        assertNull(relay.onLive(COLUMN, TaFixtures.chart("c", "M1", 1)), "a chart record on the columns topic");
        assertNull(relay.onLive(EVENTS, TaFixtures.column("c", 1, 1)), "a column on the events topic");
        assertNull(relay.onLive(CHART, TaFixtures.column("c", 1, 1)));
        assertNull(relay.onLive(CHART, TaFixtures.put(TaFixtures.chart("big", "M1", 1), "contractEpoch", "e".repeat(TradeToolsRelay.RECORD_MAX_BYTES))), "over 64 KiB");
        assertNull(relay.onLive(CHART, GatewayAvroDeserializer.UNDECODABLE));
        assertNull(relay.onLive(CHART, TaFixtures.waveSet("M1", 1, 1, "OK")), "a record of another family");
        assertEquals(13, relay.dropsForTest(), "the two parked records are not drops");
        assertEquals(0, relay.acceptedForTest());
    }

    @Test
    void perKeyOrderingIsByAsOfMsAnEqualOneReplacesAnOlderOneIsARegression() {
        var relay = new TradeToolsRelay();
        relay.onLive(CHART, TaFixtures.chart("c", "M1", 100));
        assertNotNull(relay.onLive(CHART, TaFixtures.chart("c", "M1", 100)));
        assertNull(relay.onLive(CHART, TaFixtures.chart("c", "M1", 99)));
        relay.onLive(COLUMN, TaFixtures.column("c", 7, 105));
        assertNull(relay.onLive(COLUMN, TaFixtures.column("c", 7, 104)));
        assertNotNull(relay.onLive(COLUMN, TaFixtures.column("c", 8, 104)), "another column index is another key");
        assertEquals(2, relay.regressionsForTest());
    }

    @Test
    void aStaleRecordOfAnotherHorizonNeverMovesAChartAndANewerOneReplacesItWhole() throws Exception {
        var relay = new TradeToolsRelay();
        relay.onLive(CHART, TaFixtures.chart("c", "M5", 100));
        relay.onLive(COLUMN, TaFixtures.column("c", 1, 101));
        relay.onLive(EVENTS, TaFixtures.pfEvent("PfBreakEvent", "c", "b1", 1000, 102));
        assertNull(relay.onLive(CHART, TaFixtures.chart("c", "M15", 99)), "older: a regression, nothing moves");
        assertEquals(1, bundles(relay, 1 << 20).get("5m").get("pfColumns").size());
        assertEquals(1, bundles(relay, 1 << 20).get("5m").get("pfBreaks").size());
        assertNotNull(relay.onLive(CHART, TaFixtures.chart("c", "M15", 102)));
        JsonNode hello = bundles(relay, 1 << 20);
        assertTrue(hello.get("5m").isNull(), "the chart left the 5m bundle with its columns and its events");
        assertEquals(0, hello.get("15m").get("pfBreaks").size(), "an event never outlives its chart's horizon");
        assertEquals(1, hello.get("15m").get("pfCharts").size());
        assertEquals(0, hello.get("15m").get("pfColumns").size());
    }

    @Test
    void anEvictedChartAndAnEpochChangeTakeTheirEventsWithThem() throws Exception {
        var relay = new TradeToolsRelay();
        relay.onLive(CHART, TaFixtures.chart("old", "M1", 1));
        relay.onLive(EVENTS, TaFixtures.pfEvent("PfBreakEvent", "old", "b", 1000, 2));
        for (int i = 2; i <= 5; i++) {
            relay.onLive(CHART, TaFixtures.chart("c" + i, "M1", i));
        }
        assertEquals(0, bundles(relay, 1 << 20).get("1m").get("pfBreaks").size(), "the evicted chart's event left with it");
        relay.onLive(EVENTS, TaFixtures.pfEvent("PfBreakEvent", "c5", "e", 2000, 10));
        assertEquals(1, bundles(relay, 1 << 20).get("1m").get("pfBreaks").size());
        relay.onLive(CHART, TaFixtures.put(TaFixtures.chart("c5", "M1", 20), "contractEpoch", "ESH7"));
        assertEquals(0, bundles(relay, 1 << 20).get("1m").get("pfBreaks").size(), "a new contract epoch starts the chart's events afresh");
    }

    @Test
    void theHelloBundleHasExactlyTheTwelveKeysAndTheRetentionDepthsOfTheContract() throws Exception {
        var relay = new TradeToolsRelay();
        assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null}", bundles(relay, 1 << 20).toString(), "nothing retained: null");
        relay.onLive(CHART, TaFixtures.chart("c1", "M1", 1));
        for (int i = 0; i < 30; i++) {
            relay.onLive(COLUMN, TaFixtures.column("c1", i, 10 + i));
        }
        for (int i = 0; i < 14; i++) {
            relay.onLive(EVENTS, TaFixtures.pfEvent("PfBreakEvent", "c1", "b" + i, 1000 + i, 50 + i));
        }
        for (int i = 0; i < 40; i++) {
            relay.onLive(FLOW, TaFixtures.flowBar("M1", 60_000L * i, 100 + i));
        }
        for (int i = 0; i < 11; i++) {
            relay.onLive(CLIMAX, TaFixtures.climax("k" + i, "M1", 60_000L * i, 200 + i));
        }
        relay.onLive(NODES, TaFixtures.nodes("RTH", 20, 300));
        relay.onLive(NODES, TaFixtures.nodes("GLOBEX", 5, 301));
        relay.onLive(NODES, TaFixtures.nodes("RTH", 5, 302));
        JsonNode hello = bundles(relay, 1 << 20);
        JsonNode m1 = hello.get("1m");
        assertEquals(ARRAYS, names(m1.fieldNames()), "exactly the twelve keys, in contract order");
        assertEquals(1, m1.get("pfCharts").size());
        assertEquals(24, m1.get("pfColumns").size(), "the latest 24 columns of the chart");
        assertEquals(6, m1.get("pfColumns").get(0).get("columnIndex").asInt(), "ascending columnIndex, contiguous, latest 24 of 30");
        assertEquals(29, m1.get("pfColumns").get(23).get("columnIndex").asInt());
        assertEquals(10, m1.get("pfBreaks").size(), "the latest 10 events by eventTimeMs");
        assertEquals(1004, m1.get("pfBreaks").get(0).get("eventTimeMs").asInt());
        assertEquals(0, m1.get("pfLines").size(), "a family without records is an empty array, never a missing key");
        assertEquals(32, m1.get("flowBars").size());
        assertEquals(8L * 60_000L, m1.get("flowBars").get(0).get("barStartMs").asLong());
        assertEquals(8, m1.get("climax").size());
        assertEquals(List.of("GLOBEX", "RTH", "RTH"), java.util.stream.StreamSupport.stream(m1.get("nodes").spliterator(), false).map(n -> n.get("scope").asText()).toList());
        assertEquals(List.of(5, 5, 20), java.util.stream.StreamSupport.stream(m1.get("nodes").spliterator(), false).map(n -> n.get("windowSessions").asInt()).toList(),
                "GLOBEX before RTH, then windowSessions ascending");
        assertEquals(3, names(hello.fieldNames()).size());
        assertEquals(ARRAYS, names(hello.get("15m").fieldNames()), "the nodes alone make a bundle for every timeframe");
        assertEquals(0, hello.get("15m").get("pfCharts").size());
        assertEquals(3, hello.get("15m").get("nodes").size());
    }

    @Test
    void aColumnOrFlowBarOfAnOldContractEpochIsNeverInTheBundle() throws Exception {
        var relay = new TradeToolsRelay();
        relay.onLive(CHART, TaFixtures.chart("c", "M5", 10));
        relay.onLive(COLUMN, TaFixtures.column("c", 1, 11));
        relay.onLive(CHART, TaFixtures.put(TaFixtures.chart("c", "M5", 20), "contractEpoch", "ESH7"));
        relay.onLive(COLUMN, TaFixtures.put(TaFixtures.column("c", 1, 21), "contractEpoch", "ESH7"));
        JsonNode m5 = bundles(relay, 1 << 20).get("5m");
        assertEquals(1, m5.get("pfColumns").size(), "the new epoch started the chart's columns afresh");
        assertEquals("ESH7", m5.get("pfColumns").get(0).get("contractEpoch").asText());
        relay.onLive(FLOW, TaFixtures.flowBar("M5", 300_000, 30));
        relay.onLive(FLOW, TaFixtures.put(TaFixtures.flowBar("M5", 600_000, 31), "contractEpoch", "ESH7"));
        JsonNode flow = bundles(relay, 1 << 20).get("5m").get("flowBars");
        assertEquals(1, flow.size(), "bars of the epoch of the newest bar only");
        assertEquals(600_000, flow.get(0).get("barStartMs").asInt());
    }

    @Test
    void anEventOfAnotherContractEpochThanItsRetainedChartIsRefused() throws Exception {
        var relay = new TradeToolsRelay();
        relay.onLive(CHART, TaFixtures.put(TaFixtures.chart("c", "M1", 10), "contractEpoch", "ESH7"));
        assertNull(relay.onLive(EVENTS, TaFixtures.pfEvent("PfBreakEvent", "c", "old", 1000, 11)), "the default fixture epoch is not ESH7: a stale epoch is refused");
        assertNotNull(relay.onLive(EVENTS, TaFixtures.put(TaFixtures.pfEvent("PfBreakEvent", "c", "now", 2000, 12), "contractEpoch", "ESH7")));
        assertEquals(1, bundles(relay, 1 << 20).get("1m").get("pfBreaks").size());
    }

    @Test
    void columnsLeaveByTheirLowestIndexEvenWhenAnOldOneWasRepublished() throws Exception {
        var relay = new TradeToolsRelay();
        relay.onLive(CHART, TaFixtures.chart("c", "M1", 1));
        for (int i = 0; i < TradeToolsRelay.COLUMNS_PER_CHART; i++) {
            relay.onLive(COLUMN, TaFixtures.column("c", 100 + i, 10 + i));
        }
        relay.onLive(COLUMN, TaFixtures.column("c", 100, 500));   // the oldest column republished: newest by asOfMs, still the lowest index
        relay.onLive(COLUMN, TaFixtures.column("c", 100 + TradeToolsRelay.COLUMNS_PER_CHART, 501));
        JsonNode cols = bundles(relay, 1 << 20).get("1m").get("pfColumns");
        assertEquals(TradeToolsRelay.COLUMNS_PER_CHART, cols.size());
        assertEquals(101, cols.get(0).get("columnIndex").asInt(), "index 100 left, 101 stays: the retained history is contiguous");
        assertEquals(100 + TradeToolsRelay.COLUMNS_PER_CHART, cols.get(cols.size() - 1).get("columnIndex").asInt());
    }

    @Test
    void aLateColumnOfTheOldEpochIsRefusedWhileTheNewEpochsColumnIsNot() throws Exception {
        var relay = new TradeToolsRelay();
        relay.onLive(CHART, TaFixtures.put(TaFixtures.chart("c", "M5", 100), "contractEpoch", "ESH7"));
        // published BEFORE the retained chart state (asOf 50 < 100) under the old epoch: late, refused
        assertNull(relay.onLive(COLUMN, TaFixtures.put(TaFixtures.column("c", 1, 50), "contractEpoch", "ESZ6")));
        // published at or after it under the new epoch: the new epoch's column, ahead of (or equal to) its chart state: kept
        assertNotNull(relay.onLive(COLUMN, TaFixtures.put(TaFixtures.column("c", 2, 120), "contractEpoch", "ESM7")));
    }

    @Test
    void equalClocksAreResolvedByKeySoTheSameInputKeepsTheSameCharts() throws Exception {
        for (String[] order : new String[][] {{"a", "b", "c", "d", "e"}, {"e", "d", "c", "b", "a"}, {"c", "e", "a", "d", "b"}}) {
            var relay = new TradeToolsRelay();
            for (String id : order) {
                relay.onLive(CHART, TaFixtures.chart(id, "M1", 7));
            }
            JsonNode charts = bundles(relay, 1 << 20).get("1m").get("pfCharts");
            assertEquals(List.of("b", "c", "d", "e"), java.util.stream.StreamSupport.stream(charts.spliterator(), false).map(n -> n.get("chartId").asText()).sorted().toList(), String.join("", order));
        }
    }

    @Test
    void aColumnAndAnEventThatPrecedeTheirChartAreParkedAndReleasedRightAfterItsFrame() {
        var relay = new TradeToolsRelay();
        assertNull(relay.onLive(COLUMN, TaFixtures.column("late", 1, 11)));
        assertNull(relay.onLive(EVENTS, TaFixtures.pfEvent("PfBreakEvent", "late", "e", 12, 12)));
        assertEquals(0, relay.dropsForTest());
        String[] chart = relay.onLive(CHART, TaFixtures.chart("late", "M5", 10));
        assertEquals("trade-tools-pf-chart", chart[0]);
        var released = relay.released();
        assertEquals(2, released.size(), "both parked records follow the chart frame");
        assertEquals(List.of("trade-tools-pf-column", "trade-tools-pf-break"), released.stream().map(f -> f[0]).toList());
        assertEquals(0, relay.released().size(), "released once");
        assertEquals(0, relay.dropsForTest());
    }

    @Test
    void theParkedBufferIsBoundedTheOldestLeavesCountedAndOnlyTheSurvivorsAreReleased() {
        var relay = new TradeToolsRelay();
        int extra = 3;
        for (int i = 0; i < TradeToolsRelay.LIVE_ORPHANS + extra; i++) {
            assertNull(relay.onLive(COLUMN, TaFixtures.column("c-" + i, 1, 10 + i)), "parked, never broadcast");
        }
        assertEquals(extra, relay.dropsForTest(), "three records past the bound were counted as drops");
        // the first `extra` chart ids were the oldest: their records left; their charts release nothing
        for (int i = 0; i < extra; i++) {
            assertNotNull(relay.onLive(CHART, TaFixtures.chart("c-" + i, "M5", 5)));
            assertEquals(0, relay.released().size(), "c-" + i + " was evicted from the buffer, there is nothing to release");
        }
        // every survivor is released exactly once, right after its OWN chart, and never twice
        for (int i = extra; i < TradeToolsRelay.LIVE_ORPHANS + extra; i++) {
            assertNotNull(relay.onLive(CHART, TaFixtures.chart("c-" + i, "M5", 1000 + i)), "a newer chart is accepted (the timeframe keeps its newest four)");
            var released = relay.released();
            assertEquals(1, released.size(), "c-" + i);
            assertEquals("trade-tools-pf-column", released.get(0)[0]);
            assertTrue(released.get(0)[1].contains("\"c-" + i + "\""), "it is c-" + i + "'s own column");
            assertEquals(0, relay.released().size(), "c-" + i + " is released once");
        }
        assertEquals(extra, relay.dropsForTest(), "no further drops");
    }

    @Test
    void aDelayedRecordThatWouldBeEvictedAtOnceIsRefusedNotForwarded() throws Exception {
        var relay = new TradeToolsRelay();
        for (int i = 0; i < TradeToolsRelay.FLOW_BARS; i++) {
            relay.onLive(FLOW, TaFixtures.flowBar("M1", 60_000L * (100 + i), 10 + i));
        }
        // a new bar that is newer to the gateway (asOfMs) but OLDER than every retained bar: it would be evicted on arrival
        assertNull(relay.onLive(FLOW, TaFixtures.flowBar("M1", 60_000L, 500)), "not retained, so not broadcast");
        assertEquals(TradeToolsRelay.FLOW_BARS, bundles(relay, 1 << 20).get("1m").get("flowBars").size());
        assertEquals(6_000_000L, bundles(relay, 1 << 20).get("1m").get("flowBars").get(0).get("barStartMs").asLong(), "the oldest retained bar is untouched");
        // a delayed chart older than all four retained ones does not evict a newer chart
        for (int i = 1; i <= 4; i++) {
            relay.onLive(CHART, TaFixtures.chart("c" + i, "M1", 10 + i));
        }
        assertNull(relay.onLive(CHART, TaFixtures.chart("late", "M1", 1)));
        JsonNode charts = bundles(relay, 1 << 20).get("1m").get("pfCharts");
        assertEquals(List.of("c1", "c2", "c3", "c4"), java.util.stream.StreamSupport.stream(charts.spliterator(), false).map(n -> n.get("chartId").asText()).toList());
    }

    @Test
    void aFifthChartEvictsTheOldestWithItsColumnsAndAFifthNodeSetEvictsTheOldest() throws Exception {
        var relay = new TradeToolsRelay();
        for (int i = 1; i <= 4; i++) {
            relay.onLive(CHART, TaFixtures.chart("c" + i, "M1", i));
            relay.onLive(COLUMN, TaFixtures.column("c" + i, 1, 10 + i));
        }
        relay.onLive(CHART, TaFixtures.chart("c5", "M1", 5));
        JsonNode m1 = bundles(relay, 1 << 20).get("1m");
        assertEquals(4, m1.get("pfCharts").size());
        assertEquals(List.of("c2", "c3", "c4", "c5"), java.util.stream.StreamSupport.stream(m1.get("pfCharts").spliterator(), false).map(n -> n.get("chartId").asText()).toList());
        assertEquals(3, m1.get("pfColumns").size(), "c1's column left with its chart");
        assertNull(relay.onLive(COLUMN, TaFixtures.column("c1", 2, 99)), "…and c1 is not a known chart any more");
        for (int i = 0; i < 5; i++) {
            relay.onLive(NODES, TaFixtures.nodes(i < 3 ? "RTH" : "GLOBEX", 5 + i, 100 + i));
        }
        assertEquals(4, bundles(relay, 1 << 20).get("1m").get("nodes").size());
    }

    @Test
    void theHelloSendsWholeBundlesInOrderWhileTheyFit() throws Exception {
        var relay = new TradeToolsRelay();
        for (String h : new String[] {"M1", "M5", "M15"}) {
            relay.onLive(CHART, TaFixtures.chart("c" + h, h, 1));
            for (int i = 0; i < 3; i++) {
                relay.onLive(FLOW, TaFixtures.put(TaFixtures.flowBar(h, 60_000L * (i + 1), 10 + i), "contractEpoch", "E" + "x".repeat(10_000)));
            }
        }
        JsonNode all = bundles(relay, 1 << 20);
        assertFalse(all.get("15m").isNull());
        long one = StudiesRelay.utf8Length(relay.helloField(1 << 20)) / 3;   // bytes, not characters
        JsonNode two = bundles(relay, one * 2 + 2000);
        assertFalse(two.get("1m").isNull());
        assertTrue(two.get("15m").isNull(), "the budget ended before the third bundle: null, never a thinned bundle");
        assertTrue(bundles(relay, 10).get("1m").isNull());
    }

    @Test
    void aRecoveryHoldsRecordsThatPrecedeTheirChartAndPromotesChartsFirst() {
        var relay = new TradeToolsRelay();
        relay.onLive(CHART, TaFixtures.chart("live", "M1", 1));
        String before = relay.helloField(1 << 20);
        relay.beginRecovery();
        relay.onStaged(COLUMN, TaFixtures.column("c", 1, 11));                  // read before its chart
        relay.onStaged(EVENTS, TaFixtures.pfEvent("PfLineEvent", "c", "l1", 5, 12));
        relay.onStaged(COLUMN, TaFixtures.column("orphan", 1, 11));             // its chart is never read
        relay.onStaged(CHART, TaFixtures.chart("c", "M15", 10));
        relay.onStaged(FLOW, TaFixtures.flowBar("M1", 60_000, 13));
        relay.onStaged(NODES, TaFixtures.nodes("RTH", 5, 14));
        assertEquals(before, relay.helloField(1 << 20), "private until promoted");
        List<String[]> frames = relay.promote();
        assertEquals(List.of(CHART, COLUMN, "trade-tools-pf-line", NODES, FLOW), frames.stream().map(f -> f[0]).toList(),
                "the staged chart, then what was waiting for it, then nodes and flow; the orphan and the live chart (unchanged) send nothing");
        assertEquals(1, relay.dropsForTest(), "the orphan whose chart never came");
        assertTrue(relay.promote().isEmpty());
        assertEquals(List.of(512, TradeToolsRelay.COLUMNS_TAIL, 1024, 32, 1024, 256), java.util.Arrays.stream(TradeToolsRelay.ROLES).map(relay::tailRecords).toList());
    }

    /** The producer's log of one chart: columns 1..{@code closed} each published once closed, after many republished copies of each while it was open. */
    private static List<org.apache.avro.generic.GenericRecord> republishedLog(String chart, int columns, int copies, long[] clock) {
        List<org.apache.avro.generic.GenericRecord> log = new ArrayList<>();
        for (int c = 1; c <= columns; c++) {
            for (int k = 0; k < copies; k++) {
                log.add(TaFixtures.column(chart, c, ++clock[0]));
            }
        }
        return log;
    }

    @Test
    void aRecoveryOverATailDominatedByRepublishedOpenColumnsRetainsTheSameColumnsAsTheLiveRelayDid() throws Exception {
        long[] clock = {100};
        var chart1 = TaFixtures.chart("busy", "M1", 1);
        var chart2 = TaFixtures.chart("quiet", "M5", 2);
        List<org.apache.avro.generic.GenericRecord> log = new ArrayList<>(republishedLog("quiet", 30, 3, clock));   // quiet chart: older, few copies
        log.addAll(republishedLog("busy", 30, 400, clock));                                                        // busy chart: 12 000 records, 24 columns do not fit any 2 048 tail
        var before = new TradeToolsRelay();
        before.onLive(CHART, chart1);
        before.onLive(CHART, chart2);
        log.forEach(r -> before.onLive(COLUMN, r));
        String expected = before.helloField(1 << 22);
        JsonNode m1 = bundles(before, 1 << 22).get("1m").get("pfColumns");
        assertEquals(24, m1.size());
        assertEquals(7, m1.get(0).get("columnIndex").asLong());
        assertEquals(30, m1.get(23).get("columnIndex").asLong());
        // restart: the charts are read AFTER thousands of their columns, and only the last COLUMNS_TAIL records of the log are read
        assertTrue(log.size() < TradeToolsRelay.COLUMNS_TAIL);
        var after = new TradeToolsRelay();
        after.beginRecovery();
        log.forEach(r -> after.onStaged(COLUMN, r));
        after.onStaged(CHART, chart1);
        after.onStaged(CHART, chart2);
        after.promote();
        assertEquals(expected, after.helloField(1 << 22), "the retained P&F columns after a restart equal those before it");
        assertEquals(0, after.dropsForTest(), "republished copies of one key are one parked record, not a drop per copy");
    }

    @Test
    void aTailThatReachesOnlyRepublishedCopiesOfTheNewestColumnsKeepsOnlyThoseAndNeverGuessesTheRest() throws Exception {
        long[] clock = {100};
        List<org.apache.avro.generic.GenericRecord> log = republishedLog("busy", 30, 400, clock);
        List<org.apache.avro.generic.GenericRecord> shortTail = log.subList(log.size() - 2048, log.size());   // the former 2 048 record tail: 5.12 columns
        var relay = new TradeToolsRelay();
        relay.beginRecovery();
        relay.onStaged(CHART, TaFixtures.chart("busy", "M1", 1));
        shortTail.forEach(r -> relay.onStaged(COLUMN, r));
        relay.promote();
        JsonNode cols = bundles(relay, 1 << 22).get("1m").get("pfColumns");
        assertEquals(6, cols.size(), "the defect: a tail that is all copies of the newest few columns shows only those");
        assertEquals(25, cols.get(0).get("columnIndex").asLong());
    }

    @Test
    void theParkedBufferOfARecoveryCountsDistinctKeysNotCopiesAndABoundedOverflowIsCounted() {
        var relay = new TradeToolsRelay();
        relay.beginRecovery();
        for (int copy = 0; copy < 3 * TradeToolsRelay.ORPHANS; copy++) {
            relay.onStaged(COLUMN, TaFixtures.column("c", copy % 5, 10 + copy));   // 5 keys, 6 144 copies, chart never read
        }
        relay.onStaged(COLUMN, TaFixtures.column("c", 2, 3));                        // an older copy than the parked one: a regression
        assertEquals(1, relay.regressionsForTest());
        assertEquals(0, relay.dropsForTest(), "5 distinct keys fit the bound");
        for (int i = 0; i < TradeToolsRelay.ORPHANS; i++) {
            relay.onStaged(COLUMN, TaFixtures.column("d", i, 5));                    // 2 048 more distinct keys: the first 2 043 fit, five do not
        }
        assertEquals(5, relay.dropsForTest(), "distinct keys past the bound are counted");
        relay.promote();
        assertEquals(5 + TradeToolsRelay.ORPHANS, relay.dropsForTest(), "the five overflowed keys plus the parked ones whose chart never came");
    }
}
