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
        return StudiesFixtures.onLive(relay, StudiesRelay.BAR_EVENT, barKey(bar), bar);
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
            assertNull(StudiesFixtures.onLive(relay, StudiesRelay.BAR_EVENT, "SPX|" + tf.substring(1).toLowerCase(), bar("SPX", tf, 1)), tf);
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
        r = bar("SPX", "T5M", 0); r.put("calibration", "x".repeat(StudiesRelay.BAR_MAX_BYTES));
        bad.add(new Object[] {"SPX|5m", r, "oversize"});
        int n = 0;
        for (Object[] b : bad) {
            assertNull(StudiesFixtures.onLive(relay, StudiesRelay.BAR_EVENT, (String) b[0], b[1]), (String) b[2]);
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
        String frame = StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1));
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
        assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 5)));
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 4)));
        assertEquals(1L, relay.regressionsForTest());
        assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 5)), "an equal publishedAt is an idempotent republish");
        assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "ES", current("ES", 1)), "each symbol has its own baseline");
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "ES", StudiesFixtures.currentWithout("ES", 2, "15m")), "a forwarded timeframe is missing");
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("ES", 9)), "key differs from the symbol");
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "NDX", current("NDX", 9)));
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", bar("SPX", "T5M", 0)), "a StudyBar on the current event");
        GenericRecord oversize = current("SPX", 9);
        oversize.put("runId", "x".repeat(StudiesRelay.CURRENT_MAX_BYTES));
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", oversize));
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
            StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, symbol, current(symbol, 1));
            for (String tf : List.of("T1M", "T5M", "T15M")) {
                for (int i = 0; i < StudiesRelay.HISTORY_BARS; i++) {
                    StudiesFixtures.onLive(relay, StudiesRelay.BAR_EVENT, symbol + "|" + tf.substring(1).toLowerCase(), bar(symbol, tf, i));
                }
            }
        }
        String fields = relay.helloFields();
        assertTrue(StudiesRelay.utf8Length(fields) < StudiesRelay.HELLO_MAX_BYTES, "full hello is " + StudiesRelay.utf8Length(fields) + " bytes");
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
        StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1));
        for (String tf : List.of("T1M", "T5M")) {
            for (int i = 0; i < StudiesRelay.HISTORY_BARS; i++) {
                GenericRecord big = bar("SPX", tf, i);
                big.put("calibration", "x".repeat(StudiesRelay.BAR_MAX_BYTES - 12_000));   // under the per-bar bound, far over the hello budget in sum
                assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.BAR_EVENT, barKey(big), big));
            }
        }
        String fields = relay.helloFields();
        assertTrue(StudiesRelay.utf8Length(fields) <= StudiesRelay.HELLO_MAX_BYTES, "bytes " + StudiesRelay.utf8Length(fields));
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
        StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1));
        String before = relay.helloFields();
        StudiesRelay.Store staged = relay.newStaging();
        for (int i = 0; i < 4; i++) {
            GenericRecord b = bar("SPX", "T5M", i);
            StudiesFixtures.onStaged(relay, staged, StudiesRelay.BAR_EVENT, barKey(b), b);
        }
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 3));
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.CURRENT_EVENT, "ES", current("ES", 3));
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
        StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 9));
        StudiesRelay.Store staged = relay.newStaging();
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 2));
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

    @Test
    void theHelloBudgetIsMeasuredInUtf8BytesNotCharacters() throws Exception {
        // 22k two-byte characters = 44 KB per bar: under the 64 KiB per-bar bound, 24 bars = ~1.1 MB, far over the hello budget
        for (String tf : List.of("T1M", "T5M", "T15M")) {
            for (int i = 0; i < StudiesRelay.HISTORY_BARS; i++) {
                GenericRecord big = bar("SPX", tf, i);
                big.put("calibration", "\u00e9".repeat(22_000));
                assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.BAR_EVENT, barKey(big), big));
            }
        }
        String fields = relay.helloFields();
        assertEquals(fields.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, StudiesRelay.utf8Length(fields));
        assertTrue(StudiesRelay.utf8Length(fields) <= StudiesRelay.HELLO_MAX_BYTES, "bytes " + StudiesRelay.utf8Length(fields));
        GenericRecord tooBigInBytes = bar("SPX", "T5M", 50);
        tooBigInBytes.put("calibration", "\u00e9".repeat(StudiesRelay.BAR_MAX_BYTES / 2));   // 32 KiB characters, over the bound only in bytes
        assertNull(live(tooBigInBytes));
    }

    @Test
    void anOverBudgetSetOfCurrentEntriesDegradesToNullLargestFirstNeverAnOversizedHello() throws Exception {
        for (String symbol : List.of("SPX", "ES")) {
            GenericRecord c = current(symbol, 1);
            c.put("runId", "r".repeat(100_000));   // the header repeats in every one of the three entries of each symbol
            assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, symbol, c));
        }
        String fields = relay.helloFields();
        assertTrue(StudiesRelay.utf8Length(fields) <= StudiesRelay.HELLO_MAX_BYTES, "bytes " + StudiesRelay.utf8Length(fields));
        JsonNode h = hello();
        int nulls = 0;
        int objects = 0;
        for (String f : List.of("studies", "esStudies")) {
            for (String tf : List.of("1m", "5m", "15m")) {
                if (h.get(f).get(tf).isNull()) {
                    nulls++;
                } else {
                    objects++;
                }
            }
        }
        assertTrue(nulls > 0 && objects > 0, "some entries degrade to null, not all: " + nulls + "/" + objects);
    }

    @Test
    void aRecordWithAbsurdCollectionsIsRefusedBeforeItIsRendered() throws Exception {
        GenericRecord b = bar("SPX", "T5M", 0);
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        for (int i = 0; i <= StudiesRelay.MAX_VALUES; i++) {
            values.put("v" + i, StudiesFixtures.minimal(StudiesFixtures.BAR.getField("values").schema().getValueType()));
        }
        b.put("values", values);
        assertNull(live(b));
        assertEquals(1L, relay.dropsForTest());
    }

    @Test
    void theHelloIsNotRebuiltByRecordsThatChangeNothing() throws Exception {
        live(bar("SPX", "T5M", 1));
        String first = relay.helloFields();
        assertNull(live(bar("SPX", "T5M", 1)), "a duplicate");
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.BAR_EVENT, "NDX|5m", bar("NDX", "T5M", 2)), "refused");
        assertTrue(first == relay.helloFields(), "same cached string: no rebuild for a duplicate or a refused record");
        live(bar("SPX", "T5M", 2));
        assertFalse(first == relay.helloFields(), "a new bar changes the hello");
    }

    @Test
    void theEmittedHelloNeverExceedsTheBudgetEvenWhenTheContentAloneIsJustUnderIt() throws Exception {
        // 24 equal bars whose summed JSON (plus one comma each) is 100 bytes under the budget: the framing (field names, keys,
        // braces) pushes the rendered fragment over it unless the budget accounts for the framing too
        int target = (StudiesRelay.HELLO_MAX_BYTES - 100) / 24 - 1;
        String probe = live(bar("SPX", "T1M", 0));
        int pad = target - StudiesRelay.utf8Length(probe) + "UNVALIDATED".length();   // the fixture bar carries calibration UNVALIDATED
        assertTrue(pad > 0 && target < StudiesRelay.BAR_MAX_BYTES);
        int symbolIndex = 0;
        for (String symbol : List.of("SPX", "ES")) {
            for (String tf : List.of("T1M", "T5M", "T15M")) {
                for (int i = 0; i < 4 && symbolIndex < 24; i++, symbolIndex++) {
                    GenericRecord b = bar(symbol, tf, 100 + i);
                    b.put("calibration", "p".repeat(pad));
                    StudiesFixtures.onLive(relay, StudiesRelay.BAR_EVENT, barKey(b), b);
                }
            }
        }
        String fields = relay.helloFields();
        assertTrue(StudiesRelay.utf8Length(fields) <= StudiesRelay.HELLO_MAX_BYTES, "bytes " + StudiesRelay.utf8Length(fields));
        JsonNode h = hello();
        int kept = 0;
        for (String f : List.of("studyBars", "esStudyBars")) {
            for (String tf : List.of("1m", "5m", "15m")) {
                kept += h.get(f).get(tf).size();
            }
        }
        assertTrue(kept < 24, "the framing pushed the content over the budget, so the oldest bar went: " + kept);
    }

    @Test
    void recordsAppliedLiveWhileARecoveryIsPendingSurviveItsPromotion() throws Exception {
        live(bar("SPX", "T5M", 1));
        StudiesRelay.Store staged = relay.newStaging();
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.BAR_EVENT, "SPX|5m", bar("SPX", "T5M", 2));      // history read during recovery
        assertNotNull(live(bar("SPX", "T5M", 9)));                                              // a caught-up partition keeps delivering live
        List<String[]> frames = relay.promote(staged);
        assertTrue(frames.isEmpty(), "the staged bar is older than the live tail: history only, no frame");
        JsonNode bars = hello().get("studyBars").get("5m");
        assertEquals(List.of(1L, 2L, 9L), List.of(minute(bars.get(0)), minute(bars.get(1)), minute(bars.get(2))),
                "the live bar was not lost to the promotion, the recovered one joined the history");
    }

    @Test
    void aSameInstantRevisionIsBroadcastByThePromotionButAnIdenticalRepublishIsNot() throws Exception {
        StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 5));
        StudiesRelay.Store staged = relay.newStaging();
        assertTrue(relay.promote(staged).isEmpty(), "identical to what is live: nothing owed");
        GenericRecord revised = current("SPX", 5);
        revised.put("revision", 6L);   // same run, same instant, higher revision
        revised.put("calibration", "UNVALIDATED");
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.CURRENT_EVENT, "SPX", revised);
        List<String[]> frames = relay.promote(staged);
        assertEquals(1, frames.size());
        assertEquals(StudiesRelay.CURRENT_EVENT, frames.get(0)[0]);
        assertEquals(6L, JSON.readTree(frames.get(0)[1]).get("revision").asLong());
        assertEquals(6L, hello().get("studies").get("1m").get("revision").asLong(), "the hello and the sockets agree");
    }

    @Test
    void anIdenticalCurrentRepublishDoesNotRebuildTheHello() throws Exception {
        StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 5));
        String first = relay.helloFields();
        assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 5)), "accepted and forwarded");
        assertTrue(first == relay.helloFields(), "nothing retained changed: the cached hello is reused");
    }

    @Test
    void onlyARecordBelowItsOwnPartitionsCapturedEndIsStaged() {
        java.util.Map<org.apache.kafka.common.TopicPartition, Long> end = java.util.Map.of(new org.apache.kafka.common.TopicPartition("t", 3), 10L);
        assertTrue(FeedGatewayService.studiesRecordIsRecovery(end, "t", 3, 9));
        assertFalse(FeedGatewayService.studiesRecordIsRecovery(end, "t", 3, 10), "at the end offset: live");
        assertFalse(FeedGatewayService.studiesRecordIsRecovery(end, "t", 4, 0), "another partition, already caught up: live");
        assertFalse(FeedGatewayService.studiesRecordIsRecovery(java.util.Map.of(), "t", 3, 0), "no recovery pending: live");
    }

    @Test
    void aBarIdOfTheProducersRealLengthIsAcceptedAndOneOverNinetySixIsNot() throws Exception {
        GenericRecord real = bar("SPX", "T1M", 0);
        assertEquals(StudiesFixtures.SEGMENT, real.get("barId").toString().split("\\|")[1]);
        assertTrue(real.get("barId").toString().length() > 64 && real.get("barId").toString().length() <= 96, real.get("barId").toString());
        assertNotNull(live(real), "the real 71-72 character barId is a valid bar, not a drop");
        GenericRecord ninetySix = bar("SPX", "T1M", 2);
        ninetySix.put("barId", ninetySix.get("barId").toString().replace("|" + StudiesFixtures.SEGMENT + "|", "|" + "X".repeat(57) + "|"));
        assertEquals(96, ninetySix.get("barId").toString().length());
        assertNotNull(live(ninetySix), "exactly 96 characters is accepted: the ceiling is 96, not lower");
        GenericRecord ninetySeven = bar("SPX", "T1M", 1);
        ninetySeven.put("barId", ninetySeven.get("barId").toString().replace("|" + StudiesFixtures.SEGMENT + "|", "|" + "X".repeat(58) + "|"));
        assertEquals(97, ninetySeven.get("barId").toString().length());
        assertNull(live(ninetySeven), "one character over the 96 bound is refused");
        assertEquals(1L, relay.dropsForTest());
    }

    @Test
    void aBarWithAnImpossibleClockOrAMislabelledIdIsRefused() throws Exception {
        GenericRecord endBeforeStart = bar("SPX", "T5M", 0);
        endBeforeStart.put("barEnd", java.time.Instant.ofEpochMilli(StudiesFixtures.T0 - 1).toString());
        GenericRecord tooLong = bar("SPX", "T1M", 1);
        tooLong.put("barEnd", java.time.Instant.ofEpochMilli(StudiesFixtures.T0 + 60_000L + 120_000L).toString());   // 3 minutes on a 1m bar
        GenericRecord wrongTimeframeInId = bar("SPX", "T5M", 2);
        wrongTimeframeInId.put("barId", ((String) wrongTimeframeInId.get("barId").toString()).replace("|5m|", "|15m|"));
        GenericRecord wrongSymbolInId = bar("SPX", "T5M", 3);
        wrongSymbolInId.put("barId", wrongSymbolInId.get("barId").toString().replaceFirst("^SPX", "ES"));
        GenericRecord idNotEndingInBarStart = bar("SPX", "T5M", 4);
        idNotEndingInBarStart.put("barId", idNotEndingInBarStart.get("barId").toString().replace("T13:", "T14:"));
        GenericRecord idTooLong = bar("SPX", "T5M", 5);
        idTooLong.put("barId", idTooLong.get("barId").toString().replace("|" + StudiesFixtures.SEGMENT + "|", "|" + "X".repeat(70) + "|"));
        for (GenericRecord bad : List.of(endBeforeStart, tooLong, wrongTimeframeInId, wrongSymbolInId, idNotEndingInBarStart, idTooLong)) {
            assertNull(live(bad), bad.get("barId") + " / " + bad.get("barEnd"));
        }
        assertEquals(6L, relay.dropsForTest());
        assertEquals(0, hello().get("studyBars").get("5m").size() + hello().get("studyBars").get("1m").size());
        assertNotNull(live(bar("SPX", "T5M", 6)), "a well-formed bar is still accepted");
    }

    @Test
    void aSameInstantStateNeedsAHigherRevisionFromTheSameRunToReplaceTheHeldOne() throws Exception {
        GenericRecord first = current("SPX", 5);
        first.put("revision", 10L);
        assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", first));
        GenericRecord delayedOlder = current("SPX", 5);
        delayedOlder.put("revision", 9L);
        delayedOlder.put("quality", StudiesFixtures.CURRENT.getField("quality").schema().getEnumSymbols().get(0));   // differs from the held content
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", delayedOlder), "an older revision of the same instant is a regression");
        GenericRecord otherRun = current("SPX", 5);
        otherRun.put("runId", "run-2");
        otherRun.put("revision", 99L);
        otherRun.put("calibration", "UNVALIDATED");
        otherRun.put("restored", true);
        assertNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", otherRun), "another run does not displace the held state of that instant");
        assertEquals(2L, relay.regressionsForTest());
        assertEquals("10", hello().get("studies").get("1m").get("revision").asText());
        GenericRecord newer = current("SPX", 5);
        newer.put("revision", 11L);
        newer.put("restored", true);
        assertNotNull(StudiesFixtures.onLive(relay, StudiesRelay.CURRENT_EVENT, "SPX", newer));
        assertEquals("11", hello().get("studies").get("1m").get("revision").asText());
    }

    @Test
    void anAbsurdStringOrMapKeyIsRefusedBeforeAnythingIsRendered() throws Exception {
        GenericRecord hugeString = bar("SPX", "T5M", 0);
        hugeString.put("calibration", "x".repeat(StudiesRelay.BAR_MAX_BYTES + 1));
        GenericRecord hugeKey = bar("SPX", "T5M", 1);
        @SuppressWarnings("unchecked") java.util.Map<String, Object> values = (java.util.Map<String, Object>) hugeKey.get("values");
        values.put("k".repeat(StudiesRelay.BAR_MAX_BYTES + 1), StudiesFixtures.minimal(StudiesFixtures.BAR.getField("values").schema().getValueType()));
        assertNull(live(hugeString));
        assertNull(live(hugeKey));
        assertEquals(2L, relay.dropsForTest());
        assertTrue(StudiesRelay.withinBytes(bar("SPX", "T5M", 2), new int[] {StudiesRelay.BAR_MAX_BYTES}), "a normal bar is within its bound");
    }

    @Test
    void theTopicFixesTheSymbolAMisroutedRecordIsRefused() throws Exception {
        GenericRecord es = bar("ES", "T5M", 0);
        assertNull(relay.onLive(StudiesRelay.BAR_EVENT, "ES|5m", es, false), "an ES record on a local (SPX) topic");
        GenericRecord spx = bar("SPX", "T5M", 0);
        assertNull(relay.onLive(StudiesRelay.BAR_EVENT, "SPX|5m", spx, true), "an SPX record on an es. mirror topic");
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "ES", current("ES", 1), false));
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1), true));
        assertEquals(4L, relay.dropsForTest());
        assertNotNull(relay.onLive(StudiesRelay.BAR_EVENT, "ES|5m", es, true));
        assertNotNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1), false));
        assertTrue(StudiesRelay.isEsTopic("es.options.indicators.studies.bars"));
        assertFalse(StudiesRelay.isEsTopic("options.indicators.studies.bars"));
    }

    @Test
    void thePreRenderWalkChargesUtf8BytesNotCharacters() throws Exception {
        GenericRecord b = bar("SPX", "T5M", 0);
        b.put("calibration", "\u00e9".repeat(StudiesRelay.BAR_MAX_BYTES / 2 - 3_000));   // fewer characters than the bound, more bytes than it once added up
        assertTrue(StudiesRelay.withinBytes(b, new int[] {StudiesRelay.BAR_MAX_BYTES}));
        b.put("calibration", "\u00e9".repeat(StudiesRelay.BAR_MAX_BYTES / 2 + 1_000));   // over in bytes, under in characters
        assertFalse(StudiesRelay.withinBytes(b, new int[] {StudiesRelay.BAR_MAX_BYTES}));
    }

    private StudiesRelay relayWith24BarsPadded(int pad) throws Exception {
        StudiesRelay r = new StudiesRelay(JSON);
        int n = 0;
        for (String symbol : List.of("SPX", "ES")) {
            for (String tf : List.of("T1M", "T5M", "T15M")) {
                for (int i = 0; i < 4; i++, n++) {
                    GenericRecord b = bar(symbol, tf, 100 + i);
                    b.put("calibration", "p".repeat(pad));
                    StudiesFixtures.onLive(r, StudiesRelay.BAR_EVENT, barKey(b), b);
                }
            }
        }
        assertEquals(24, n);
        return r;
    }

    @Test
    void historyIsGivenUpOnlyWhenTheRenderedFragmentReallyExceedsTheBudget() throws Exception {
        int pad0 = 12_000;
        int a0 = StudiesRelay.utf8Length(relayWith24BarsPadded(pad0).helloFields());
        int pad1 = pad0 + (StudiesRelay.HELLO_MAX_BYTES - 40 - a0) / 24;   // lands within 24 + 40 bytes UNDER the budget
        StudiesRelay fits = relayWith24BarsPadded(pad1);
        int actual = StudiesRelay.utf8Length(fits.helloFields());
        assertTrue(actual <= StudiesRelay.HELLO_MAX_BYTES && actual > StudiesRelay.HELLO_MAX_BYTES - 100, "bytes " + actual);
        int kept = 0;
        JsonNode h = JSON.readTree("{\"hello\":1" + fits.helloFields() + "}");
        for (String f : List.of("studyBars", "esStudyBars")) {
            for (String tf : List.of("1m", "5m", "15m")) {
                kept += h.get(f).get(tf).size();
            }
        }
        assertEquals(24, kept, "everything that really fits is kept: no early eviction from conservative accounting");
        StudiesRelay over = relayWith24BarsPadded(pad1 + 10);   // 24 * 10 bytes more: now it really exceeds
        assertTrue(StudiesRelay.utf8Length(over.helloFields()) <= StudiesRelay.HELLO_MAX_BYTES);
        JsonNode h2 = JSON.readTree("{\"hello\":1" + over.helloFields() + "}");
        int kept2 = 0;
        for (String f : List.of("studyBars", "esStudyBars")) {
            for (String tf : List.of("1m", "5m", "15m")) {
                kept2 += h2.get(f).get(tf).size();
            }
        }
        assertEquals(23, kept2, "exactly one bar went");
    }

    @Test
    void recoveryRendersOnlyWhatPromotionRetainsAndCountsOnlyWhatItSends() throws Exception {
        StudiesRelay.Store staged = relay.newStaging();
        for (int i = 0; i < 300; i++) {   // a long history read during recovery, ascending
            GenericRecord b = bar("SPX", "T1M", i);
            StudiesFixtures.onStaged(relay, staged, StudiesRelay.BAR_EVENT, barKey(b), b);
        }
        assertEquals(0L, relay.rendersForTest(), "nothing is rendered while staging");
        assertEquals(0L, relay.acceptedForTest(), "staged bars are not 'accepted' yet");
        List<String[]> frames = relay.promote(staged);
        assertEquals(StudiesRelay.HISTORY_BARS, relay.rendersForTest(), "only the eight retained bars were rendered");
        assertEquals((long) StudiesRelay.HISTORY_BARS, relay.acceptedForTest());
        assertEquals(StudiesRelay.HISTORY_BARS, frames.size());
        assertEquals(StudiesRelay.HISTORY_BARS, hello().get("studyBars").get("1m").size());
        assertEquals(299L, minute(hello().get("studyBars").get("1m").get(StudiesRelay.HISTORY_BARS - 1)));
    }

    @Test
    void theBudgetTheServiceCanSpareBindsTheFragmentAndTheSkeletonIsTheFloor() throws Exception {
        for (String tf : List.of("T1M", "T5M", "T15M")) {
            for (int i = 0; i < StudiesRelay.HISTORY_BARS; i++) {
                live(bar("SPX", tf, i));
            }
        }
        relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1), false);
        int full = StudiesRelay.utf8Length(relay.helloFields());
        int spare = full / 2;
        String half = relay.helloFields(spare);
        assertTrue(StudiesRelay.utf8Length(half) <= spare, "bytes " + StudiesRelay.utf8Length(half) + " of " + spare);
        assertTrue(half.length() < relay.helloFields().length(), "a smaller budget gives up history");
        assertTrue(relay.helloFields() == relay.helloFields(), "the full budget's rendering is cached again");
        assertEquals(relay.helloFields(0), relay.helloFields(-5), "a negative budget is the floor too");
        String floor = relay.helloFields(0);
        JsonNode h = JSON.readTree("{\"hello\":1" + floor + "}");
        for (String f : List.of("studies", "esStudies")) {
            h.get(f).forEach(v -> assertTrue(v.isNull()));
        }
        for (String f : List.of("studyBars", "esStudyBars")) {
            h.get(f).forEach(v -> assertEquals(0, v.size()));
        }
    }

    @Test
    void stagedBarsAreSealedIntoIndependentCopiesAtTheEndOfThePollAndOnlyTheRetainedOnes() throws Exception {
        StudiesRelay.Store staged = relay.newStaging();
        GenericRecord first = bar("SPX", "T5M", 4);
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.BAR_EVENT, barKey(first), first);
        relay.seal(staged);   // the end of the poll that delivered it
        first.put("closesSeen", 987654321L);   // the deserializer reuses or mutates its object afterwards
        for (int i = 10; i < 310; i++) {   // one big poll: 300 more records, 8 retained
            GenericRecord b = bar("SPX", "T1M", i);
            StudiesFixtures.onStaged(relay, staged, StudiesRelay.BAR_EVENT, barKey(b), b);
        }
        assertEquals(1L, relay.copiesForTest(), "nothing copied while a poll is being processed");
        relay.seal(staged);
        assertEquals(1L + StudiesRelay.HISTORY_BARS, relay.copiesForTest(), "one poll of 300 records costs the 8 retained copies, not 300");
        relay.promote(staged);
        assertEquals(104L, hello().get("studyBars").get("5m").get(0).get("closesSeen").asLong(), "the sealed bar kept the values it was validated with");
    }

    @Test
    void theApprovedWriterSchemasAreThePinnedFingerprintsOfTheContractsSchemasAndASameNamedOtherShapeIsRefused() throws Exception {
        // parsing-form fingerprints of the test copies; that those copies are byte-identical to the contracts registry files is pinned by
        // the SHA-256 test in the contracts repository (StudySubjectsTest), not here
        assertEquals(StudiesRelay.BAR_FINGERPRINT, org.apache.avro.SchemaNormalization.parsingFingerprint64(StudiesFixtures.BAR), "StudyBar.avsc changed: re-pin");
        assertEquals(StudiesRelay.CURRENT_FINGERPRINT, org.apache.avro.SchemaNormalization.parsingFingerprint64(StudiesFixtures.CURRENT), "StudiesCurrent.avsc changed: re-pin");
        // a record of the same full name with one extra field
        org.apache.avro.Schema base = StudiesFixtures.BAR;
        java.util.List<org.apache.avro.Schema.Field> fields = new java.util.ArrayList<>();
        for (org.apache.avro.Schema.Field f : base.getFields()) {
            fields.add(new org.apache.avro.Schema.Field(f.name(), f.schema(), f.doc(), (Object) null));
        }
        fields.add(new org.apache.avro.Schema.Field("extra", org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING), null, (Object) null));
        org.apache.avro.Schema altered = org.apache.avro.Schema.createRecord(base.getName(), null, base.getNamespace(), false, fields);
        GenericRecord r = new org.apache.avro.generic.GenericData.Record(altered);
        GenericRecord ok = bar("SPX", "T5M", 0);
        for (org.apache.avro.Schema.Field f : base.getFields()) {
            r.put(f.name(), ok.get(f.name()));
        }
        r.put("extra", "leak");
        assertNull(live(r), "same name, other shape");
        assertEquals(1L, relay.dropsForTest());
        assertNotNull(live(ok));
    }

    @Test
    void theArithmeticSizeIsTheRenderedSizeAndAnOverBudgetHelloIsRenderedOnce() throws Exception {
        for (String symbol : List.of("SPX", "ES")) {
            for (String tf : List.of("T1M", "T5M", "T15M")) {
                for (int i = 0; i < 5; i++) {
                    live(bar(symbol, tf, i));
                }
            }
            relay.onLive(StudiesRelay.CURRENT_EVENT, symbol, current(symbol, 1), "ES".equals(symbol));
        }
        // every budget from the skeleton up to the full size: the rendered fragment never exceeds it, and the full one is the arithmetic size
        int full = StudiesRelay.utf8Length(relay.helloFields());
        for (int budget = 0; budget <= full + 10; budget += Math.max(1, full / 37)) {
            String fields = relay.helloFields(budget);
            assertTrue(StudiesRelay.utf8Length(fields) <= Math.max(budget, StudiesRelay.utf8Length(relay.helloFields(0))), "budget " + budget);
        }
        long before = relay.rendersForTest();
        relay.helloFields(full / 3);
        relay.helloFields(full / 3);
        assertEquals(before, relay.rendersForTest(), "hello rendering is not bar rendering: bars are rendered once, at ingestion");
    }

    @Test
    void aCurrentStateMustCarryExactlyTheProducersSixTimeframes() throws Exception {
        GenericRecord extra = current("SPX", 1);
        @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) extra.get("timeframes");
        m.put("2h", m.get("1h"));
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", extra, false), "an extra key is producer/schema drift");
        assertNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", StudiesFixtures.currentWithout("SPX", 2, "30s"), false), "a missing unforwarded key too");
        assertEquals(2L, relay.dropsForTest());
        assertNotNull(relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 3), false));
    }

    @Test
    void oneRecordObjectBackingSeveralStagedBarsIsDiscardedNotSealedAsTheLastMessage() throws Exception {
        StudiesRelay.Store staged = relay.newStaging();
        GenericRecord reused = bar("SPX", "T5M", 1);
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.BAR_EVENT, barKey(reused), reused);
        // a (forbidden) reusing deserializer: the same object, overwritten with the next message
        GenericRecord next = bar("SPX", "T5M", 2);
        for (org.apache.avro.Schema.Field f : StudiesFixtures.BAR.getFields()) {
            reused.put(f.name(), next.get(f.name()));
        }
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.BAR_EVENT, barKey(reused), reused);
        GenericRecord honest = bar("SPX", "T5M", 3);
        StudiesFixtures.onStaged(relay, staged, StudiesRelay.BAR_EVENT, barKey(honest), honest);
        relay.seal(staged);
        assertEquals(2L, relay.aliasedForTest(), "both bars that shared the object are discarded");
        relay.promote(staged);
        JsonNode bars = hello().get("studyBars").get("5m");
        assertEquals(1, bars.size());
        assertEquals(3L, minute(bars.get(0)), "only the honest bar survives");
    }

    @Test
    void differentPrefixBudgetsShareOneCachedStringWhenTheWholeStateFitsAndTightOnesAreCachedPerBudget() throws Exception {
        for (String tf : List.of("T1M", "T5M", "T15M")) {
            for (int i = 0; i < 4; i++) {
                live(bar("SPX", tf, i));
            }
        }
        relay.onLive(StudiesRelay.CURRENT_EVENT, "SPX", current("SPX", 1), false);
        String a = relay.helloFields(900_000);
        String b = relay.helloFields(700_000);
        String c = relay.helloFields(StudiesRelay.HELLO_MAX_BYTES);
        assertTrue(a == b && b == c, "every caller with room gets the same cached string, whatever its prefix");
        int full = StudiesRelay.utf8Length(a);
        String tight1 = relay.helloFields(full / 2);
        String tight2 = relay.helloFields(full / 3);
        assertTrue(tight1 == relay.helloFields(full / 2) && tight2 == relay.helloFields(full / 3), "tight budgets are cached per budget");
        live(bar("SPX", "T5M", 9));
        assertFalse(a == relay.helloFields(900_000), "a state change rebuilds");
    }
}
