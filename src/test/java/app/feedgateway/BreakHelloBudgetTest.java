package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * The hello with every relay on and the break-classification part last in the priority order (studies, geometry, tools, waves, trade tools, breaks). Sizes are measured with the records the
 * REAL producer wrote (producer-records.jsonl), the largest mark being 1 927 bytes of frame JSON: a day of real records, a busy day, and the type-maximal 64 marks and 64 outcomes of every timeframe.
 */
class BreakHelloBudgetTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String E = BreakClassificationRelay.EVENT;
    private static final long PART = BreakClassificationRelay.HELLO_PART_BYTES;

    @AfterAll
    static void dropScopes() {
        io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry.dropScope("f-budget");
    }

    private static FeedGatewayService service(boolean breaks) {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).studiesEnabled();
        doReturn(true).when(settings).geometryEnabled();
        doReturn(true).when(settings).structureToolsEnabled();
        doReturn(true).when(settings).waveCycleEnabled();
        doReturn(true).when(settings).tradeToolsEnabled();
        doReturn(breaks).when(settings).breakClassificationEnabled();
        return new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null);
    }

    private static BreakClassificationRelay breaks(FeedGatewayService s) {
        return (BreakClassificationRelay) s.taRelaysForTest().stream().filter(r -> r.handles(E)).findFirst().orElseThrow();
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    /** The real records decoded through the production deserializer: the marks of every timeframe (largest first by set), the outcomes and the status. */
    private static List<GenericRecord> decoded(String type) throws Exception {
        try (GatewayAvroDeserializer d = BreakFixtures.deserializer("f-budget")) {
            return BreakFixtures.real().stream().filter(r -> r.type().equals(type)).map(r -> (GenericRecord) d.deserialize(BreakFixtures.TOPIC, r.framed())).toList();
        }
    }

    private static GenericRecord largest(List<GenericRecord> records) {
        return records.stream().max(java.util.Comparator.comparingInt(r -> bytes(AvroFrameJson.render(r, Integer.MAX_VALUE, false)))).orElseThrow();
    }

    private static GenericRecord withId(GenericRecord r, String timeframe, long seed) {
        GenericRecord c = (GenericRecord) GenericData.get().deepCopy(r.getSchema(), r);
        TaFixtures.put(c, "classificationId", BreakFixtures.hex(seed));
        if (timeframe != null) {
            TaFixtures.put(c, "timeframe", timeframe);
        }
        return c;
    }

    /** {@code marks} marks and as many outcomes in every timeframe, each id distinct, built from the largest real mark and the largest real outcome. */
    private static void fillBreaks(BreakClassificationRelay relay, int marks) throws Exception {
        GenericRecord mark = largest(decoded("BreakMark")), outcome = largest(decoded("BreakOutcome"));
        for (String tf : new String[] {"M1", "M5", "M15"}) {
            for (int i = 0; i < marks; i++) {
                long seed = (long) tf.hashCode() * 1000 + i;
                assertNotNull(relay.onLive(E, withId(mark, tf, seed)));
                assertNotNull(relay.onLive(E, withId(outcome, null, seed)));
            }
        }
        relay.onLive(E, decoded("BreakStatus").get(0));
    }

    @Test
    void aDayOfTheRealProducersRecordsFitsTheBreaksPartWithRoomToSpare() throws Exception {
        var relay = new BreakClassificationRelay();
        int realBytes = 0;
        try (GatewayAvroDeserializer d = BreakFixtures.deserializer("f-budget")) {
            for (BreakFixtures.Real r : BreakFixtures.real()) {
                if (r.set().startsWith("development-")) {
                    String[] frame = relay.onLive(E, d.deserialize(BreakFixtures.TOPIC, r.framed()));
                    realBytes += bytes(frame[1]);
                }
            }
        }
        String part = relay.helloField(Long.MAX_VALUE);
        assertTrue(bytes(part) < PART / 10, "the development sets of the three timeframes (" + realBytes + " bytes of frames) take " + bytes(part) + " bytes, under a tenth of 256 KiB");
        assertTrue(bytes(JSON.writeValueAsString(JSON.readTree("{" + part.substring(1) + "}").get("breaks").get("1m").get("marks").get(0))) < 2_048 + 512);
    }

    @Test
    void theTypeMaximalPartTwelveHundredAndEightyFourRecordsKeepsTheFirstTimeframeWholeAndNullsTheRest() throws Exception {
        var relay = new BreakClassificationRelay();
        fillBreaks(relay, BreakClassificationRelay.MARKS_PER_TF);
        String part = relay.helloField(Long.MAX_VALUE);
        JsonNode hello = JSON.readTree("{" + part.substring(1) + "}").get("breaks");
        assertEquals(64, hello.get("1m").get("marks").size());
        assertEquals(64, hello.get("1m").get("outcomes").size());
        assertTrue(hello.get("5m").isNull() && hello.get("15m").isNull(), "3 x 143 KB does not fit 256 KiB: whole timeframes in order, null for the rest");
        assertEquals("LIVE", hello.get("status").get("statusKind").asText());
        assertTrue(bytes(part) <= PART, "the part is " + bytes(part) + " bytes of " + PART);
        System.out.println("BREAK-HELLO type-maximal part (64 marks + 64 outcomes of the largest real records, 1m only): " + bytes(part) + " bytes = " + 100 * bytes(part) / PART + "% of 256 KiB");
    }

    @Test
    void withEveryExistingPartAtItsCapAndTheBreaksPartAtItsCapTheHelloStaysUnderTheGuardAndTheBreaksPartIsRenderedLast() throws Exception {
        var s = service(true);
        ChartHelloBudgetTest.fill(s, 8, 11_000, 190_000, 4, 50_000);
        fillBreaks(breaks(s), BreakClassificationRelay.MARKS_PER_TF);
        assertEquals(2176 + 256, (StudiesRelay.HELLO_MAX_BYTES + 3 * TimeframeSetRelay.HELLO_PART_BYTES + TradeToolsRelay.HELLO_PART_BYTES + BreakClassificationRelay.HELLO_PART_BYTES) / 1024,
                "512 + 384 + 384 + 384 + 512 + 256 KiB of content before arbitration");
        String hello = s.cvdHelloJson();
        assertTrue(bytes(hello) <= FeedGatewayService.HELLO_FRAME_HARD_BYTES, "the hello is " + bytes(hello) + " bytes");
        JsonNode node = JSON.readTree(hello);
        JsonNode without = JSON.readTree(plainHello());
        for (String field : List.of("studies", "studyBars", "esStudies", "esStudyBars", "geometry", "tools", "waves", "tradeTools")) {
            assertEquals(without.get(field), node.get(field), field + " does not depend on the lowest-priority part");
        }
        JsonNode part = node.get("breaks");
        boolean ended = false;
        for (String tf : List.of("1m", "5m", "15m")) {
            assertFalse(ended && !part.get(tf).isNull(), tf + " follows a null");
            ended |= part.get(tf).isNull();
        }
        assertTrue(bytes(part.toString()) <= PART, "the breaks part is held under its own cap");
        System.out.println("BREAK-HELLO worst case, every part at its cap: hello " + bytes(hello) + " bytes (hard guard " + FeedGatewayService.HELLO_FRAME_HARD_BYTES + "), without breaks " + bytes(plainHello()) + ", breaks field " + bytes(part.toString()) + " bytes, 1m " + (part.get("1m").isNull() ? "null" : "present") + ", 5m " + (part.get("5m").isNull() ? "null" : "present"));
    }

    private static String plainHello() {
        var s = service(false);
        ChartHelloBudgetTest.fill(s, 8, 11_000, 190_000, 4, 50_000);
        return s.cvdHelloJson();
    }

    private static String helloWithRealRecords(int studiesChars, int perSet, int flowChars) throws Exception {
        var s = service(true);
        ChartHelloBudgetTest.fill(s, 8, studiesChars, perSet, 3, flowChars);
        try (GatewayAvroDeserializer d = BreakFixtures.deserializer("f-budget")) {
            for (BreakFixtures.Real r : BreakFixtures.real()) {
                if (r.set().startsWith("development-")) {
                    breaks(s).onLive(E, d.deserialize(BreakFixtures.TOPIC, r.framed()));
                }
            }
        }
        String hello = s.cvdHelloJson();
        assertTrue(bytes(hello) <= FeedGatewayService.HELLO_FRAME_HARD_BYTES, "hello " + bytes(hello));
        assertEquals(hello, s.cvdHelloJson(), "deterministic");
        return hello;
    }

    @Test
    void theRealRecordsRideTheHelloWholeOnALightDayAndAreStarvedToTheStatusWhenTheOtherPartsFillTheTarget() throws Exception {
        String light = helloWithRealRecords(100, 1_000, 1_000);
        JsonNode part = JSON.readTree(light).get("breaks");
        for (String tf : List.of("1m", "5m", "15m")) {
            assertFalse(part.get(tf).isNull(), tf);
        }
        assertEquals("LIVE", part.get("status").get("statusKind").asText());
        System.out.println("BREAK-HELLO light day of the other parts + real development records: hello " + bytes(light) + " bytes, breaks field " + bytes(part.toString()) + " bytes, all three timeframes present");
        String heavy = helloWithRealRecords(4_000, 40_000, 53_000);   // the existing typical-day fill of ChartHelloBudgetTest
        JsonNode starved = JSON.readTree(heavy).get("breaks");
        System.out.println("BREAK-HELLO heavy typical day of the other parts + real development records: hello " + bytes(heavy) + " bytes, breaks field " + bytes(starved.toString()) + " bytes");
        assertTrue(starved.get("15m").isNull(), "the lowest priority gives way when the target is used");
    }

    @Test
    void flagOffTheHelloHasNoBreaksFieldAndNoRelayExists() throws Exception {
        var s = service(false);
        assertFalse(JSON.readTree(s.cvdHelloJson()).has("breaks"));
        assertTrue(s.taRelaysForTest().stream().noneMatch(r -> r.handles(E)));
    }
}
