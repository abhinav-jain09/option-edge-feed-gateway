package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** liq-V design section 10, gateway part: the scoreboard projection holds the service's fields, computes nothing and reports what is missing. */
class ScorecardTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOPIC = "futures.level-absorption.scoreboard";
    private static final LiquidityHistoryAuth OPEN = new LiquidityHistoryAuth(new GatewaySettings(), null);
    private static final Schema SCHEMA = schema();

    private static Schema schema() {
        try (InputStream in = ScorecardTest.class.getResourceAsStream("/scorecard/LevelAbsorptionScoreboard.avsc")) {
            return new Schema.Parser().parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static GenericRecord row(String date, String digest, String family, String cls, int horizon, long decided, long positive, Double rate) {
        GenericRecord r = new GenericData.Record(SCHEMA);
        r.put("schemaVersion", 1);
        r.put("outcomeRuleVersion", "liq-U-v1");
        r.put("service", "level-absorption-service");
        r.put("environment", "dev");
        r.put("tradeDate", date);
        r.put("family", family);
        r.put("classKey", cls);
        r.put("stratum", "RTH");
        r.put("horizonSeconds", horizon);
        r.put("signals", decided + 2);
        r.put("decided", decided);
        r.put("positive", positive);
        r.put("positiveName", "HELD");
        r.put("rate", rate);
        r.put("claimed", decided);
        r.put("hits", positive);
        r.put("excluded", new HashMap<>(Map.of("TAPE_GAP", 2L)));
        r.put("smallN", decided < 30);
        r.put("identityDigest", digest);
        r.put("identityFields", new HashMap<>(Map.of("contractTableDigest", "abc")));
        r.put("lastDecidedAtMs", 1L);
        r.put("label", "DESCRIPTIVE_UNVALIDATED");
        return r;
    }

    private static byte[] frame(int id) {
        return ByteBuffer.allocate(10).put((byte) 0).putInt(id).putInt(7).put((byte) 0).array();
    }

    /** The decoder seam returns the record registered under the payload's last int. */
    private static ScorecardReader reader(Map<Integer, Object> byPayload, String... topics) {
        if (topics.length == 0) {
            System.clearProperty("KAFKA_SCORECARD_SCOREBOARD_TOPICS");
        } else {
            System.setProperty("KAFKA_SCORECARD_SCOREBOARD_TOPICS", String.join(",", topics));
        }
        GatewaySettings s = new GatewaySettings();
        Deserializer<Object> d = (t, b) -> byPayload.getOrDefault(ByteBuffer.wrap(b, 5, 4).getInt(), GatewayAvroDeserializer.UNDECODABLE);
        return new ScorecardReader(s, d);
    }

    private static JsonNode body(ScorecardReader r, String from) throws Exception {
        ScorecardController c = new ScorecardController(r, OPEN);
        ResponseEntity<String> res = c.serve(from, null, 1L);
        assertEquals(200, res.getStatusCode().value());
        assertEquals("no-store", res.getHeaders().getCacheControl());
        return JSON.readTree(res.getBody());
    }

    @Test
    void aReaderWithNoTopicAnswersAnEmptyListAndSaysSo() throws Exception {
        ScorecardReader r = reader(Map.of());
        assertTrue(r.off());
        JsonNode v = body(r, null);
        assertEquals("DESCRIPTIVE_UNVALIDATED", v.get("label").asText());
        assertEquals(0, v.get("rows").size());
        assertEquals("NOT_CONFIGURED", v.get("sources").get(0).get("status").asText());
        assertTrue(v.get("message").asText().contains("not configured"));
    }

    @Test
    void aMissingTopicIsReportedNeverShownAsData() throws Exception {
        ScorecardReader r = reader(Map.of(), TOPIC);
        assertEquals("CONNECTING", body(r, null).get("sources").get(0).get("status").asText(), "before the first look at the broker the topic is not called missing");
        r.board().setState(TOPIC, "TOPIC_MISSING");
        JsonNode v = body(r, null);
        assertEquals("TOPIC_MISSING", v.get("sources").get(0).get("status").asText());
        assertEquals(TOPIC, v.get("sources").get(0).get("topic").asText());
        assertTrue(v.get("message").asText().contains("does not exist yet"));
        assertEquals(0, v.get("rows").size());
    }

    @Test
    void theTopicExistingButEmptyIsNoData_andLoadingIsNamed() throws Exception {
        ScorecardReader r = reader(Map.of(), TOPIC);
        r.board().setState(TOPIC, "LOADING");
        assertEquals("LOADING", body(r, null).get("sources").get(0).get("status").asText());
        r.board().setState(TOPIC, "LIVE");
        JsonNode v = body(r, null);
        assertEquals("NO_DATA", v.get("sources").get(0).get("status").asText());
        assertTrue(v.get("message").asText().contains("holds no scoreboard row"));
    }

    @Test
    void theNewestRecordPerKeyStandsAndATombstoneRemovesIt() throws Exception {
        Map<Integer, Object> m = new HashMap<>();
        m.put(1, row("2026-11-09", "d1", "LA_VERDICT", "VERDICT=REJECTION", 900, 3, 2, 2.0 / 3));
        m.put(2, row("2026-11-09", "d1", "LA_VERDICT", "VERDICT=REJECTION", 900, 4, 3, 0.75));
        ScorecardReader r = reader(m, TOPIC);
        r.board().setState(TOPIC, "LIVE");
        r.accept(TOPIC, "k1", payload(1));
        r.accept(TOPIC, "k1", payload(2));
        JsonNode v = body(r, null);
        assertEquals(1, v.get("rows").size());
        assertEquals(4, v.get("rows").get(0).get("decided").asLong());
        assertEquals("OK", v.get("sources").get(0).get("status").asText());
        r.accept(TOPIC, "k1", null);
        assertEquals(0, body(r, null).get("rows").size());
    }

    private static byte[] payload(int n) {
        return ByteBuffer.allocate(9).put((byte) 0).putInt(ScorecardReader.SCOREBOARD_ID).putInt(n).array();
    }

    @Test
    void theGatewayComputesNothing_everyFieldIsTheServicesOwn() throws Exception {
        // a row whose rate does not equal positive / decided is shown exactly as published: nothing is recomputed or summed
        Map<Integer, Object> m = new HashMap<>();
        m.put(1, row("2026-11-09", "d1", "LA_VERDICT", "VERDICT=HOLD", 300, 10, 3, 0.9));
        m.put(2, row("2026-11-09", "d1", "LA_VERDICT", "VERDICT=BREAK", 300, 5, 1, null));
        ScorecardReader r = reader(m, TOPIC);
        r.board().setState(TOPIC, "LIVE");
        r.accept(TOPIC, "a", payload(1));
        r.accept(TOPIC, "b", payload(2));
        JsonNode v = body(r, null);
        assertEquals(2, v.get("rows").size());
        JsonNode first = v.get("rows").get(1);   // VERDICT=HOLD sorts after VERDICT=BREAK
        assertEquals("VERDICT=HOLD", first.get("classKey").asText());
        assertEquals(0.9, first.get("rate").asDouble());
        assertTrue(v.get("rows").get(0).get("rate").isNull());
        assertEquals(2L, first.get("excluded").get("TAPE_GAP").asLong());
        assertEquals("abc", first.get("identityFields").get("contractTableDigest").asText());
        assertEquals(22, first.size());
        assertFalse(v.has("totals"));
    }

    @Test
    void anUndecodableRecordAndAForeignIdAreCountedAndNeverShown() throws Exception {
        ScorecardReader r = reader(Map.of(), TOPIC);
        r.board().setState(TOPIC, "LIVE");
        r.accept(TOPIC, "a", payload(99));          // right id, decoder refuses
        r.accept(TOPIC, "b", frame(2813));          // another subject's id
        r.accept(TOPIC, "c", "{\"json\":1}".getBytes());   // retained JSON
        JsonNode v = body(r, null);
        assertEquals("UNDECODABLE", v.get("sources").get(0).get("status").asText());
        assertEquals(3, v.get("sources").get(0).get("undecodable").asInt());
        assertEquals(0, v.get("rows").size());
        assertTrue(v.get("message").asText().contains("3 record(s)"));
    }

    @Test
    void aRecordWithoutAReadableTradeDateIsNotHeld() throws Exception {
        Map<Integer, Object> m = new HashMap<>();
        m.put(1, row("not-a-date", "d", "LA_LEAN", "LEAN=QUIET", 300, 1, 1, 1.0));
        ScorecardReader r = reader(m, TOPIC);
        r.board().setState(TOPIC, "LIVE");
        r.accept(TOPIC, "a", payload(1));
        assertEquals(0, r.board().rowCount());
        assertEquals(1, body(r, null).get("sources").get(0).get("undecodable").asInt());
    }

    @Test
    void onlyTheNewest120TradeDatesAreHeld() throws Exception {
        Map<Integer, Object> m = new HashMap<>();
        java.time.LocalDate d0 = java.time.LocalDate.of(2026, 1, 1);
        ScorecardReader r = reader(m, TOPIC);
        r.board().setState(TOPIC, "LIVE");
        for (int i = 0; i < 130; i++) {
            m.put(i, row(d0.plusDays(i).toString(), "d", "LA_LEAN", "LEAN=QUIET", 300, 1, 1, 1.0));
            r.accept(TOPIC, "k" + i, payload(i));
        }
        assertEquals(120, r.board().rowCount());
        JsonNode v = body(r, null);
        assertEquals(d0.plusDays(129).toString(), v.get("rows").get(0).get("tradeDate").asText(), "newest first");
        assertEquals(d0.plusDays(10).toString(), v.get("rows").get(119).get("tradeDate").asText(), "the ten oldest dates left");
        assertEquals(10, r.board().evicted());
    }

    @Test
    void fromTradeDateFiltersInclusiveAndAnInvalidOneIs400() throws Exception {
        Map<Integer, Object> m = new HashMap<>();
        m.put(1, row("2026-11-06", "d", "LA_LEAN", "LEAN=QUIET", 300, 1, 1, 1.0));
        m.put(2, row("2026-11-09", "d", "LA_LEAN", "LEAN=QUIET", 300, 1, 1, 1.0));
        ScorecardReader r = reader(m, TOPIC);
        r.board().setState(TOPIC, "LIVE");
        r.accept(TOPIC, "a", payload(1));
        r.accept(TOPIC, "b", payload(2));
        assertEquals(2, body(r, null).get("rows").size());
        assertEquals(1, body(r, "2026-11-09").get("rows").size());
        assertEquals(0, body(r, "2026-11-10").get("rows").size());
        assertEquals(400, new ScorecardController(r, OPEN).serve("junk", null, 1L).getStatusCode().value());
    }

    @Test
    void rowsAreOrderedNewestDateThenDigestFamilyClassStratumHorizon() throws Exception {
        Map<Integer, Object> m = new HashMap<>();
        m.put(1, row("2026-11-09", "b", "LA_LEAN", "LEAN=A", 900, 1, 1, 1.0));
        m.put(2, row("2026-11-09", "a", "LA_VERDICT", "VERDICT=B", 300, 1, 1, 1.0));
        m.put(3, row("2026-11-09", "a", "LA_LEAN", "LEAN=A", 900, 1, 1, 1.0));
        m.put(4, row("2026-11-09", "a", "LA_LEAN", "LEAN=A", 300, 1, 1, 1.0));
        m.put(5, row("2026-11-10", "z", "LA_LEAN", "LEAN=A", 300, 1, 1, 1.0));
        ScorecardReader r = reader(m, TOPIC);
        r.board().setState(TOPIC, "LIVE");
        for (int i = 1; i <= 5; i++) {
            r.accept(TOPIC, "k" + i, payload(i));
        }
        JsonNode rows = body(r, null).get("rows");
        String[] want = {"2026-11-10|z|LA_LEAN|300", "2026-11-09|a|LA_LEAN|300", "2026-11-09|a|LA_LEAN|900", "2026-11-09|a|LA_VERDICT|300", "2026-11-09|b|LA_LEAN|900"};
        for (int i = 0; i < 5; i++) {
            JsonNode x = rows.get(i);
            assertEquals(want[i], x.get("tradeDate").asText() + "|" + x.get("identityDigest").asText() + "|" + x.get("family").asText() + "|" + x.get("horizonSeconds").asInt());
        }
    }

    @org.junit.jupiter.api.AfterEach
    void clear() {
        System.clearProperty("KAFKA_SCORECARD_SCOREBOARD_TOPICS");
    }

    @Test
    void theSettingParsesAList_andTheDefaultIsOff() {
        assertTrue(new GatewaySettings().scorecardScoreboardTopics().isEmpty());
        System.setProperty("KAFKA_SCORECARD_SCOREBOARD_TOPICS", " a.b , c.d,a.b,, ");
        assertEquals(java.util.List.of("a.b", "c.d"), new GatewaySettings().scorecardScoreboardTopics());
        assertTrue(new GatewaySettings().scorecardTopicRetryMs() >= 5_000L);
    }

    @Test
    void eachFamilyTopicAcceptsOnlyItsOwnFixedSchemaId() throws Exception {
        String wall = "futures.liquidity-wall.scoreboard", lh = "futures.line-health.scoreboard";
        Map<Integer, Object> m = new HashMap<>();
        m.put(1, row("2026-11-09", "w", "WALL_DEFENDED", "WALL=DEFENDED", 300, 1, 1, 1.0));
        m.put(2, row("2026-11-09", "h", "LH_TOUCH", "GAUGE=HIGH", 300, 1, 1, 1.0));
        ScorecardReader r = reader(m, TOPIC, wall, lh);
        for (String t : new String[] {TOPIC, wall, lh}) {
            r.board().setState(t, "LIVE");
        }
        r.accept(wall, "a", ByteBuffer.allocate(9).put((byte) 0).putInt(3410).putInt(1).array());
        r.accept(lh, "b", ByteBuffer.allocate(9).put((byte) 0).putInt(3712).putInt(2).array());
        r.accept(wall, "c", ByteBuffer.allocate(9).put((byte) 0).putInt(2814).putInt(1).array());   // the Level Absorption id on the wall topic
        JsonNode v = body(r, null);
        assertEquals(2, v.get("rows").size());
        assertEquals(1, v.get("sources").get(1).get("undecodable").asInt());
        assertEquals("UNDECODABLE", v.get("sources").get(1).get("status").asText());
    }

    @Test
    void loadingAndUndecodableAreBothStated() throws Exception {
        ScorecardReader r = reader(Map.of(), TOPIC);
        r.board().setState(TOPIC, "LOADING");
        r.accept(TOPIC, "a", payload(99));
        JsonNode v = body(r, null);
        assertEquals("LOADING_UNDECODABLE", v.get("sources").get(0).get("status").asText());
        assertTrue(v.get("message").asText().contains("still reading") && v.get("message").asText().contains("could not be decoded"));
    }

    @Test
    void aLostTopicEpochClearsItsRows() throws Exception {
        Map<Integer, Object> m = new HashMap<>();
        m.put(1, row("2026-11-09", "d", "LA_LEAN", "LEAN=QUIET", 300, 1, 1, 1.0));
        ScorecardReader r = reader(m, TOPIC);
        r.board().setState(TOPIC, "LIVE");
        r.accept(TOPIC, "a", payload(1));
        r.accept(TOPIC, "b", payload(99));
        assertEquals(1, r.board().rowCount());
        r.board().reset(TOPIC);
        r.board().setState(TOPIC, "TOPIC_MISSING");
        JsonNode v = body(r, null);
        assertEquals(0, v.get("rows").size());
        assertEquals(0, v.get("sources").get(0).get("undecodable").asInt());
        assertEquals(0, r.board().rowCount());
    }

    @Test
    void theRowBoundRefusesNewKeysAndSaysSoButStillUpdatesHeldKeys() throws Exception {
        ScorecardBoard b = new ScorecardBoard(2);
        b.configure(java.util.List.of(TOPIC));
        b.setState(TOPIC, "LIVE");
        b.put(TOPIC, "a", row("2026-11-09", "d", "LA_LEAN", "LEAN=A", 300, 1, 1, 1.0));
        b.put(TOPIC, "b", row("2026-11-09", "d", "LA_LEAN", "LEAN=B", 300, 1, 1, 1.0));
        b.put(TOPIC, "c", row("2026-11-09", "d", "LA_LEAN", "LEAN=C", 300, 1, 1, 1.0));
        b.put(TOPIC, "a", row("2026-11-09", "d", "LA_LEAN", "LEAN=A", 300, 9, 9, 1.0));
        assertEquals(2, b.rowCount());
        JsonNode v = JSON.readTree(b.render(null, false));
        assertEquals("CAP_REACHED", v.get("sources").get(0).get("status").asText());
        assertEquals(1, v.get("sources").get(0).get("capRefused").asInt());
        assertEquals(9, v.get("rows").get(0).get("decided").asLong());
    }

    @Test
    void recoveringPartitionsAndNullKeysAreStated() throws Exception {
        Map<Integer, Object> m = new HashMap<>();
        m.put(1, row("2026-11-09", "d", "LA_LEAN", "LEAN=QUIET", 300, 1, 1, 1.0));
        ScorecardReader r = reader(m, TOPIC);
        r.board().setState(TOPIC, "LIVE");
        r.accept(TOPIC, "a", payload(1));
        r.accept(TOPIC, null, payload(1));   // a scoreboard row always has a key
        assertEquals("UNDECODABLE", body(r, null).get("sources").get(0).get("status").asText());
        r.board().setAllStates("RECOVERING");
        JsonNode v = body(r, null);
        assertEquals("RECOVERING_UNDECODABLE", v.get("sources").get(0).get("status").asText());
        assertTrue(v.get("message").asText().contains("may be stale"));
        r.board().resetAll("CONNECTING");
        assertEquals(0, r.board().rowCount());
        r.board().reset(TOPIC);
        r.board().setState(TOPIC, "PARTITIONS_INVALID");
        v = body(r, null);
        assertEquals("PARTITIONS_INVALID", v.get("sources").get(0).get("status").asText());
        assertTrue(v.get("message").asText().contains("exactly one partition"));
    }

    @Test
    void aNewerTradeDateDisplacesTheOldestAtTheRowBound() throws Exception {
        ScorecardBoard b = new ScorecardBoard(2);
        b.configure(java.util.List.of(TOPIC));
        b.setState(TOPIC, "LIVE");
        b.put(TOPIC, "a", row("2026-11-06", "d", "LA_LEAN", "LEAN=A", 300, 1, 1, 1.0));
        b.put(TOPIC, "b", row("2026-11-09", "d", "LA_LEAN", "LEAN=B", 300, 1, 1, 1.0));
        b.put(TOPIC, "c", row("2026-11-10", "d", "LA_LEAN", "LEAN=C", 300, 1, 1, 1.0));   // full: 11-06 leaves, 11-10 is kept
        b.put(TOPIC, "d", row("2026-11-01", "d", "LA_LEAN", "LEAN=D", 300, 1, 1, 1.0));   // older than everything held: refused
        JsonNode v = JSON.readTree(b.render(null, false));
        assertEquals(2, v.get("rows").size());
        assertEquals("2026-11-10", v.get("rows").get(0).get("tradeDate").asText());
        assertEquals("CAP_REACHED", v.get("sources").get(0).get("status").asText());
    }

    @Test
    void theLimiterAdmitsNoNewPrincipalPastItsHardBound() {
        ScorecardController c = new ScorecardController(reader(Map.of()), OPEN);
        for (int i = 0; i < 4096; i++) {
            assertEquals(0L, c.tryAcquire("p" + i, 1L));
        }
        assertEquals(60L, c.tryAcquire("one-too-many", 2L));
        assertEquals(0L, c.tryAcquire("p5", 2L), "a known principal keeps its budget");
        assertEquals(0L, c.tryAcquire("one-too-many", 61_000L), "expired windows leave, so the newcomer is admitted a minute later");
    }

    @Test
    void aRealWallScoreboardRowOfContracts096IsHeldWithItsMapAndEveryFieldByName() throws Exception {
        org.apache.avro.Schema ws = com.optionsedge.contracts.liquiditywall.LiquidityWallSchemas.SCOREBOARD.schema();
        GenericRecord w = new org.apache.avro.generic.GenericRecordBuilder(ws).set("schemaVersion", 1).set("outcomeRuleVersion", "liq-AA-v1").set("service", "es-liquidity-wall-service")
                .set("depthEnvironment", "Rithmic").set("contract", "ESZ6").set("tradeDate", "2026-11-16").set("classKey", "SIDE=ABOVE/DIST=D25_50/SIZE=X4_6")
                .set("side", "ABOVE").set("distanceBand", "D25_50").set("sizeBand", "X4_6").set("horizonSeconds", 1800).set("signals", 3L).set("decided", 2L).set("reached", 1L)
                .set("reachedRateThousandths", 500).set("consumed", 1L).set("pulled", 0L).set("heldSize", 1L).set("fateUnknown", 0L).set("consumedShareThousandths", 1000)
                .set("stopPastCount", 1L).set("stopPastMeanMilliTicks", 4000L).set("stopPastMaxTicks", 4L).set("secondsToReachMeanMillis", 90_000L)
                .set("excluded", new HashMap<>(Map.of("SESSION_ENDED", 1L))).set("smallN", true).set("identityDigest", "d").set("configFingerprint", "f").set("bucketEnd", 5L)
                .set("label", "DESCRIPTIVE_UNVALIDATED").build();
        String wall = "futures.liquidity-wall.scoreboard";
        ScorecardReader r = reader(Map.of(1, w), wall);
        r.board().setState(wall, "LIVE");
        r.accept(wall, "2026-11-16|d|SIDE=ABOVE/DIST=D25_50/SIZE=X4_6|M1800", ByteBuffer.allocate(9).put((byte) 0).putInt(3410).putInt(1).array());
        JsonNode v = body(r, null);
        assertEquals(1, v.get("rows").size());
        JsonNode row = v.get("rows").get(0);
        assertEquals(500, row.get("reachedRateThousandths").asInt());
        assertEquals(1, row.get("excluded").get("SESSION_ENDED").asInt());
        assertTrue(row.get("reachedRateThousandths").isNumber() && !row.has("stratum"));
        for (org.apache.avro.Schema.Field f : ws.getFields()) {
            assertTrue(row.has(f.name()), "the board copies " + f.name());   // including family and the role-flip counters of 0.96.0
        }
    }
}
