package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/** The waves-and-cycles relay: the (epoch, seq) order per record type, the dropped timeframes, the hello object per timeframe, recovery. */
class WaveCycleRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WAVES = WaveCycleRelay.WAVES_EVENT, CYCLES = WaveCycleRelay.CYCLES_EVENT;

    private static JsonNode hello(WaveCycleRelay relay, long budget) throws Exception {
        return JSON.readTree("{" + relay.helloField(budget).substring(1) + "}").get("waves");
    }

    @Test
    void eachTypeIsOrderedByEpochThenSeqIndependentlyAndAnEqualPairReplaces() {
        var relay = new WaveCycleRelay();
        assertEquals(WAVES, relay.onLive(WAVES, TaFixtures.waveSet("M5", 1, 10, "OK"))[0]);
        assertEquals(CYCLES, relay.onLive(CYCLES, TaFixtures.cycleSet("M5", 1, 3, "OK"))[0], "the two types' seq are independent");
        assertNull(relay.onLive(WAVES, TaFixtures.waveSet("M5", 1, 9, "OK")), "a smaller seq in the same epoch regresses");
        assertEquals(1, relay.regressionsForTest());
        assertEquals(WAVES, relay.onLive(WAVES, TaFixtures.waveSet("M5", 1, 10, "OK"))[0], "an equal pair replaces and is forwarded");
        assertEquals(WAVES, relay.onLive(WAVES, TaFixtures.waveSet("M5", 2, 1, "OK"))[0], "a new epoch restarts seq: (2, 1) is after (1, 10)");
        assertNull(relay.onLive(WAVES, TaFixtures.waveSet("M5", 1, 99, "OK")), "…and the old epoch never comes back");
        assertEquals(2, relay.regressionsForTest());
    }

    @Test
    void asOfMsIsNotTheOrderTheParameterSetAnnouncementCarriesZero() {
        var relay = new WaveCycleRelay();
        relay.onLive(WAVES, TaFixtures.waveSet("M1", 1, 5, "OK"));
        GenericRecord announce = TaFixtures.waveSet("M1", 1, 6, "UNAVAILABLE");
        TaFixtures.put(TaFixtures.child(announce, "env"), "unavailableReason", "PARAMETER_SET_CHANGED");
        TaFixtures.put(TaFixtures.child(announce, "env"), "asOfMs", 0L);
        String[] frame = relay.onLive(WAVES, announce);
        assertEquals(WAVES, frame[0], "asOfMs 0 with a higher seq is accepted");
        assertTrue(frame[1].contains("\"unavailableReason\":\"PARAMETER_SET_CHANGED\""));
    }

    @Test
    void theOtherTimeframesTheOtherServiceAndAWrongRecordAreDroppedAndCounted() {
        var relay = new WaveCycleRelay();
        for (String tf : new String[] {"H1", "D1", "W1"}) {
            assertNull(relay.onLive(WAVES, TaFixtures.waveSet(tf, 1, 1, "OK")), tf);
        }
        GenericRecord otherSchemaVersion = TaFixtures.waveSet("M1", 1, 1, "OK");
        TaFixtures.child(otherSchemaVersion, "env").put("schemaVersion", 2);
        assertNull(relay.onLive(WAVES, otherSchemaVersion));
        assertNull(relay.onLive(WAVES, TaFixtures.cycleSet("M1", 1, 1, "OK")), "a CycleSet on the waves binding");
        assertNull(relay.onLive(CYCLES, TaFixtures.waveSet("M1", 1, 1, "OK")), "a WaveSet on the cycles binding");
        assertNull(relay.onLive(WAVES, null));
        assertNull(relay.onLive(WAVES, "{}"));
        assertEquals(8, relay.dropsForTest());
        assertEquals(0, relay.acceptedForTest());
    }

    @Test
    void theHelloIsAnObjectPerTimeframeWithBothRecordsOrNullAndNeverCarriesADroppedTimeframe() throws Exception {
        var relay = new WaveCycleRelay();
        assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null}", hello(relay, 1 << 20).toString());
        relay.onLive(WAVES, TaFixtures.waveSet("M5", 1, 1, "OK"));
        relay.onLive(CYCLES, TaFixtures.cycleSet("M15", 1, 1, "OK"));
        relay.onLive(WAVES, TaFixtures.waveSet("H1", 1, 1, "OK"));
        JsonNode hello = hello(relay, 1 << 20);
        assertTrue(hello.get("1m").isNull());
        assertEquals(List.of("waves", "cycles"), List.copyOf(iterate(hello.get("5m").fieldNames())));
        assertFalse(hello.get("5m").get("waves").isNull());
        assertTrue(hello.get("5m").get("cycles").isNull());
        assertTrue(hello.get("15m").get("waves").isNull());
        assertEquals("OK", hello.get("15m").get("cycles").get("env").get("state").asText());
        assertEquals(1, hello.get("5m").get("waves").get("env").get("epoch").asInt(), "the nested env stays nested, unrenamed");
    }

    private static List<String> iterate(java.util.Iterator<String> it) {
        List<String> out = new java.util.ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }

    @Test
    void aTimeframeIsIncludedWholeOrNotAtAll() throws Exception {
        var relay = new WaveCycleRelay();
        GenericRecord big = TaFixtures.waveSet("M1", 1, 1, "OK");
        TaFixtures.put(TaFixtures.child(big, "env"), "parameterSetHash", "h".repeat(250_000));
        GenericRecord big5 = TaFixtures.waveSet("M5", 1, 1, "OK");
        TaFixtures.put(TaFixtures.child(big5, "env"), "parameterSetHash", "h".repeat(250_000));
        relay.onLive(WAVES, big);
        relay.onLive(CYCLES, TaFixtures.cycleSet("M1", 1, 1, "OK"));
        relay.onLive(WAVES, big5);
        relay.onLive(WAVES, TaFixtures.waveSet("M15", 1, 1, "OK"));
        JsonNode hello = hello(relay, 1 << 20);
        assertFalse(hello.get("1m").isNull());
        assertTrue(hello.get("5m").isNull(), "500 KB of waves does not fit 384 KiB");
        assertTrue(hello.get("15m").isNull(), "nothing is sent past a timeframe that did not fit");
        assertTrue(hello(relay, 100_000).get("1m").isNull(), "a smaller budget nulls the whole timeframe, waves and cycles together");
    }

    @Test
    void recoveryIsPrivateAndPromotesOnlyPairsAfterTheLiveOnes() {
        var relay = new WaveCycleRelay();
        relay.onLive(WAVES, TaFixtures.waveSet("M1", 1, 10, "OK"));
        String before = relay.helloField(1 << 20);
        relay.beginRecovery();
        relay.onStaged(WAVES, TaFixtures.waveSet("M1", 1, 5, "OK"));
        relay.onStaged(CYCLES, TaFixtures.cycleSet("M1", 1, 7, "OK"));
        relay.onStaged(WAVES, TaFixtures.waveSet("M15", 1, 2, "OK"));
        assertEquals(before, relay.helloField(1 << 20));
        List<String[]> frames = relay.promote();
        assertEquals(List.of(CYCLES, WAVES), frames.stream().map(f -> f[0]).toList(), "1m cycles then 15m waves; the held 1m waves (10) beat the staged 5");
        assertEquals(1, relay.regressionsForTest(), "the staged 1m waves 5 is behind the copied 10");
        assertEquals(120, relay.tailRecords(WAVES));
    }
}
