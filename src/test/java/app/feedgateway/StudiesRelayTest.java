package app.feedgateway;

import static app.feedgateway.StudiesFixtures.bar;
import static app.feedgateway.StudiesFixtures.barKey;
import static app.feedgateway.StudiesFixtures.current;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/** The relay's decisions, record by record: boundary, history, dedupe, ordering, staging and the hello. */
class StudiesRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StudiesRelay relay = new StudiesRelay(JSON);

    private String live(GenericRecord bar) {
        return relay.onLive(StudiesRelay.BAR_EVENT, barKey(bar), bar);
    }

    private JsonNode hello() throws Exception {
        return JSON.readTree("{\"hello\":1" + relay.helloFields() + "}");
    }

    private static void assertAcceptedAs(String json, String symbol, String enumTf) throws Exception {
        JsonNode n = JSON.readTree(json);
        assertEquals(symbol, n.get("symbol").asText());
        assertEquals(enumTf, n.get("timeframe").asText());
    }

    @Test
    void aBarIsRenderedWithTheExactAvroFieldNamesEnumSymbolsAndExactDecimalStrings() throws Exception {
        String json = live(bar("SPX", "T5M", 0));
        JsonNode n = JSON.readTree(json);
        assertEquals(List.of("schemaVersion", "methodVersion", "parameterSetHash", "symbol", "tradeDate", "timeframe", "barId", "barStart",
                "barEnd", "status", "resetCount", "closesSeen", "values", "states", "events", "divergences", "calibration"),
                new ArrayList<>(iterator(n.fieldNames())));
        assertEquals("T5M", n.get("timeframe").asText());
        assertEquals("COMPLETE", n.get("status").asText());
        assertTrue(n.get("schemaVersion").isInt());
        assertTrue(n.get("closesSeen").isIntegralNumber());
        JsonNode v = n.get("values").get("series.number.0");
        assertTrue(v.get("value").isTextual(), "a decimal stays an exact JSON string");
        assertEquals("READY", v.get("state").asText());
        assertEquals("RSI_OB_ENTER", n.get("events").get(0).asText());
        assertEquals(56, n.get("values").size());
        assertEquals(8, n.get("states").size());
    }

    private static <T> List<T> iterator(java.util.Iterator<T> it) {
        List<T> l = new ArrayList<>();
        it.forEachRemaining(l::add);
        return l;
    }

    @Test
    void onlyOneMinuteFiveMinuteAndFifteenMinuteBarsOfSpxAndEsAreForwarded() throws Exception {
        for (String tf : List.of("T1M", "T5M", "T15M")) {
            assertNotNull(live(bar("SPX", tf, 0)), tf);
            assertNotNull(live(bar("ES", tf, 0)), tf);
        }
        assertEquals(0L, relay.dropsForTest());
        for (String tf : List.of("T30S", "T1H", "T4H")) {
            assertNull(relay.onLive(StudiesRelay.BAR_EVENT, "SPX|" + tf.substring(1).toLowerCase(), bar("SPX", tf, 1)), tf);
        }
        assertEquals(3L, relay.dropsForTest());
        assertNull(live(bar("NDX", "T5M", 1)), "a symbol outside SPX/ES");
        assertEquals(4L, relay.dropsForTest());
    }

    @Test
    void theBoundaryRefusesEveryContractViolationAndRetainsNothing() throws Exception {
        GenericRecord ok = bar("SPX", "T5M", 0);
        List<Object[]> bad = new ArrayList<>();
        GenericRecord r;
        r = bar("SPX", "T5M", 0, "EMPTY");
        bad.add(new Object[] {"SPX|5m", r, "status EMPTY"});
        r = bar("SPX", "T5M", 0); r.put("methodVersion", "STUDIES_V2");
        bad.add(new Object[] {"SPX|5m", r, "methodVersion"});
        r = bar("SPX", "T5M", 0); r.put("schemaVersion", 2);
        bad.add(new Object[] {"SPX|5m", r, "schemaVersion"});
        r = bar("SPX", "T5M", 0); r.put("barId", "");
        bad.add(new Object[] {"SPX|5m", r, "empty barId"});
        r = bar("SPX", "T5M", 0); r.put("barStart", "not-an-instant");
        bad.add(new Object[] {"SPX|5m", r, "unparseable barStart"});
        bad.add(new Object[] {"SPX|1m", ok, "key timeframe differs from the record"});
        bad.add(new Object[] {"ES|5m", ok, "key symbol differs from the record"});
        bad.add(new Object[] {null, ok, "null key"});
        bad.add(new Object[] {"SPX|5m", current("SPX", 0), "a StudiesCurrent on the bars event"});
        bad.add(new Object[] {"SPX|5m", "not a record", "a non-record value"});
        r = bar("SPX", "T5M", 0); r.put("calibration", "x".repeat(StudiesRelay.BAR_MAX_CHARS));
        bad.add(new Object[] {"SPX|5m", r, "oversize"});
        int n = 0;
        for (Object[] b : bad) {
            assertNull(relay.onLive(StudiesRelay.BAR_EVENT, (String) b[0], b[1]), (String) b[2]);
            assertEquals(++n, relay.dropsForTest(), (String) b[2]);
        }
        assertEquals("[]", hello().get("studyBars").get("5m").toString(), "nothing was retained");
        assertNotNull(live(ok), "the valid bar still passes");
    }

    @Test
    void aPartialOrMissingBarIsForwardedAsAChainBreakMarker() throws Exception {
        assertNotNull(live(bar("SPX", "T1M", 0, "PARTIAL")));
        assertNotNull(live(bar("SPX", "T1M", 1, "MISSING")));
        assertEquals("MISSING", hello().get("studyBars").get("1m").get(1).get("status").asText());
    }

    @Test
    void aBarIsBroadcastOnlyWhenNewestAndAReplayedBarIsAcceptedOnce() throws Exception {
        assertNotNull(live(bar("SPX", "T5M", 5)));
        assertNull(live(bar("SPX", "T5M", 5)), "the producer re-emits identical keys and values on replay");
        assertEquals(1L, relay.duplicatesForTest());
        assertNull(live(bar("SPX", "T5M", 3)), "an older unseen bar is history only");
        assertNotNull(live(bar("SPX", "T5M", 6)));
        JsonNode bars = hello().get("studyBars").get("5m");
        assertEquals(3, bars.size());
        assertEquals(List.of(3L, 5L, 6L), List.of(minute(bars.get(0)), minute(bars.get(1)), minute(bars.get(2))), "oldest first, strictly increasing");
    }

    private static long minute(JsonNode bar) {
        return (java.time.Instant.parse(bar.get("barStart").asText()).toEpochMilli() - StudiesFixtures.T0) / 60_000L;
    }

    @Test
    void theHistoryKeepsTheNewestEightPerSymbolAndTimeframe() throws Exception {
        for (int i = 0; i < 25; i++) {
            live(bar("ES", "T15M", i));
        }
        JsonNode bars = hello().get("esStudyBars").get("15m");
        assertEquals(StudiesRelay.HISTORY_BARS, bars.size());
        assertEquals(17L, minute(bars.get(0)));
        assertEquals(24L, minute(bars.get(7)));
        assertNull(live(bar("ES", "T15M", 2)), "older than a full window: refused quietly");
        assertEquals(StudiesRelay.HISTORY_BARS, hello().get("esStudyBars").get("15m").size());
        assertEquals(0, hello().get("studyBars").get("15m").size(), "the other symbol is separate");
    }

    @Test
    void aCurrentStateKeepsOnlyTheThreeForwardedTimeframesAndHelloEntriesTheirOwn() throws Exception {
        String frame = relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1));
        JsonNode n = JSON.readTree(frame);
        assertEquals(List.of("schemaVersion", "methodVersion", "parameterSetHash", "symbol", "tradeDate", "runId", "revision",
                "publishedAt", "asOfEventTime", "quality", "restored", "calibration", "timeframes"), iterator(n.fieldNames()));
        assertEquals(List.of("1m", "5m", "15m"), iterator(n.get("timeframes").fieldNames()));
        assertEquals("READY", n.get("quality").asText());
        assertEquals(56, n.get("timeframes").get("5m").get("values").size());
        JsonNode studies = hello().get("studies");
        for (String tf : List.of("1m", "5m", "15m")) {
            assertEquals(List.of(tf), iterator(studies.get(tf).get("timeframes").fieldNames()), "hello entry carries just its timeframe");
            assertEquals("SPX", studies.get(tf).get("symbol").asText());
        }
        assertTrue(hello().get("esStudies").get("1m").isNull(), "ES has nothing yet");
    }

    @Test
    void aCurrentStateIsRefusedWhenOlderMalformedOrIncompleteAndAnEqualOneIsAccepted() throws Exception {
        assertNotNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 5)));
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 4)));
        assertEquals(1L, relay.regressionsForTest());
        assertNotNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 5)), "an equal publishedAt is an idempotent republish");
        assertNotNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "ES", current("ES", 1)), "each symbol has its own baseline");
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "ES", StudiesFixtures.currentWithout("ES", 2, "15m")), "a forwarded timeframe is missing");
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("ES", 9)), "key differs from the symbol");
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "NDX", current("NDX", 9)));
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", bar("SPX", "T5M", 0)), "a StudyBar on the current event");
        GenericRecord oversize = current("SPX", 9);
        oversize.put("runId", "x".repeat(StudiesRelay.CURRENT_MAX_CHARS));
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", oversize));
        assertEquals(5L, relay.dropsForTest());
        assertEquals("5", hello().get("studies").get("1m").get("revision").asText(), "the retained state is the last accepted one");
    }

    @Test
    void aTombstoneIsCountedAndWithdrawsNothing() throws Exception {
        live(bar("SPX", "T1M", 0));
        relay.onDrop();
        assertEquals(1L, relay.dropsForTest());
        assertEquals(1, hello().get("studyBars").get("1m").size());
    }

    @Test
    void anEmptyRelayHelloHasAllFourFieldsWithNullsAndEmptyLists() throws Exception {
        JsonNode h = hello();
        for (String f : List.of("studies", "esStudies")) {
            assertEquals(List.of("1m", "5m", "15m"), iterator(h.get(f).fieldNames()));
            h.get(f).forEach(v -> assertTrue(v.isNull()));
        }
        for (String f : List.of("studyBars", "esStudyBars")) {
            assertEquals(List.of("1m", "5m", "15m"), iterator(h.get(f).fieldNames()));
            h.get(f).forEach(v -> assertEquals(0, v.size()));
        }
    }

    @Test
    void theHelloIsCachedUntilTheRetainedStateChanges() {
        String first = relay.helloFields();
        assertTrue(first == relay.helloFields());
        live(bar("SPX", "T1M", 0));
        assertFalse(first.equals(relay.helloFields()));
    }

    @Test
    void theRealisticFullHelloFitsTheBudgetWithAllFortyEightBarsAndSixCurrentEntries() throws Exception {
        for (String symbol : List.of("SPX", "ES")) {
            relay.onLive(StudiesRelay.CURRENT_EVENT, symbol, current(symbol, 1));
            for (String tf : List.of("T1M", "T5M", "T15M")) {
                for (int i = 0; i < StudiesRelay.HISTORY_BARS; i++) {
                    relay.onLive(StudiesRelay.BAR_EVENT, symbol + "|" + tf.substring(1).toLowerCase(), bar(symbol, tf, i));
                }
            }
        }
        String fields = relay.helloFields();
        assertTrue(fields.length() < StudiesRelay.HELLO_MAX_CHARS, "full hello is " + fields.length() + " chars");
        JsonNode h = hello();
        for (String f : List.of("studyBars", "esStudyBars")) {
            for (String tf : List.of("1m", "5m", "15m")) {
                assertEquals(StudiesRelay.HISTORY_BARS, h.get(f).get(tf).size(), f + tf + ": nothing had to be dropped at the realistic size");
            }
        }
        System.out.println("STUDIES_HELLO_FULL_CHARS=" + fields.length() + " BAR_JSON_CHARS=" + live(bar("SPX", "T1M", 99)).length());
    }

    @Test
    void anOverBudgetHelloDropsTheOldestHistoryFirstAndNeverTheCurrentEntries() throws Exception {
        relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1));
        for (String tf : List.of("T1M", "T5M")) {
            for (int i = 0; i < StudiesRelay.HISTORY_BARS; i++) {
                GenericRecord big = bar("SPX", tf, i);
                big.put("calibration", "x".repeat(StudiesRelay.BAR_MAX_CHARS - 12_000));   // under the per-bar bound, far over the hello budget in sum
                assertNotNull(relay.onLive(StudiesRelay.BAR_EVENT, barKey(big), big));
            }
        }
        String fields = relay.helloFields();
        assertTrue(fields.length() <= StudiesRelay.HELLO_MAX_CHARS, "length " + fields.length());
        JsonNode h = hello();
        int kept1m = h.get("studyBars").get("1m").size(), kept5m = h.get("studyBars").get("5m").size();
        assertTrue(kept1m + kept5m < 2 * StudiesRelay.HISTORY_BARS && kept1m > 0 && kept5m > 0);
        assertTrue(Math.abs(kept1m - kept5m) <= 1, "the longest list loses its oldest bar first: " + kept1m + "/" + kept5m);
        for (String tf : List.of("1m", "5m")) {
            JsonNode bars = h.get("studyBars").get(tf);
            assertEquals(StudiesRelay.HISTORY_BARS - 1L, minute(bars.get(bars.size() - 1)), "the newest bar survives in " + tf);
        }
        assertTrue(h.get("studies").get("5m").isObject(), "current entries are never dropped");
    }

    @Test
    void stagedRecordsStayPrivateUntilPromotedAndPromotionReturnsOnlyWhatIsNew() throws Exception {
        live(bar("SPX", "T5M", 1));
        relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1));
        String before = relay.helloFields();
        StudiesRelay.Store staged = relay.newStaging();
        for (int i = 0; i < 4; i++) {
            GenericRecord b = bar("SPX", "T5M", i);
            relay.onStaged(staged, StudiesRelay.BAR_EVENT, barKey(b), b);
        }
        relay.onStaged(staged, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 3));
        relay.onStaged(staged, StudiesRelay.CURRENT_EVENT, "ES", current("ES", 3));
        assertEquals(before, relay.helloFields(), "nothing staged is visible");
        List<String[]> frames = relay.promote(staged);
        List<String> events = frames.stream().map(f -> f[0]).toList();
        assertEquals(List.of(StudiesRelay.BAR_EVENT, StudiesRelay.BAR_EVENT, StudiesRelay.CURRENT_EVENT, StudiesRelay.CURRENT_EVENT), events,
                "bars 2 and 3 are newer than the live tail (1); the older ones are history; one current per symbol");
        assertAcceptedAs(frames.get(0)[1], "SPX", "T5M");
        assertEquals(4, hello().get("studyBars").get("5m").size());
        assertEquals("3", hello().get("studies").get("5m").get("revision").asText());
        assertEquals("3", hello().get("esStudies").get("5m").get("revision").asText());
    }

    @Test
    void aStagedCurrentOlderThanTheLiveOneIsNotBroadcastAndNeverReplacesIt() throws Exception {
        relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 9));
        StudiesRelay.Store staged = relay.newStaging();
        relay.onStaged(staged, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 2));
        assertTrue(relay.promote(staged).isEmpty());
        assertEquals("9", hello().get("studies").get("1m").get("revision").asText());
        assertEquals(1L, relay.regressionsForTest());
    }

    @Test
    void metricsNameEveryCounter() {
        String m = relay.metrics();
        for (String c : List.of("accepted_total", "drops_total", "regressions_total", "duplicates_total")) {
            assertTrue(m.contains("gateway_studies_" + c + " "), c);
        }
    }
}
