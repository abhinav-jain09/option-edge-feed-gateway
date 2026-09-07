package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** ES-FOOTPRINT-GATEWAY-DESIGN.md G-R8/G-R8a — layout check and topic validation over a fake describeConfigs. */
class FootprintTopicGateTest {

    private static final List<String> TOPICS = List.of("futures.footprint", "futures.footprint.evidence", "futures.footprint.bars", "futures.footprint.outcomes");
    private static final long CEILING = 1_048_588L;

    /** A scripted reader: a map value, an UnknownTopic marker, or an AdminFailure marker per topic. */
    static final class FakeReader implements FootprintTopicGate.ConfigReader {
        final Map<String, Object> byTopic = new HashMap<>();
        int calls;
        FakeReader valid(String t) { byTopic.put(t, new FootprintTopicGate.TopicConfigView(CEILING, "producer")); return this; }
        FakeReader cfg(String t, long mmb, String ct) { byTopic.put(t, new FootprintTopicGate.TopicConfigView(mmb, ct)); return this; }
        FakeReader unknown(String t) { byTopic.put(t, "unknown"); return this; }
        FakeReader admin(String t) { byTopic.put(t, "admin"); return this; }
        @Override public FootprintTopicGate.TopicConfigView read(String topic) throws FootprintTopicGate.UnknownTopic, FootprintTopicGate.AdminFailure {
            calls++;
            Object o = byTopic.get(topic);
            if (o == null || "unknown".equals(o)) throw new FootprintTopicGate.UnknownTopic(topic);
            if ("admin".equals(o)) throw new FootprintTopicGate.AdminFailure("timeout", null);
            return (FootprintTopicGate.TopicConfigView) o;
        }
    }

    private static FakeReader allValid() { FakeReader r = new FakeReader(); TOPICS.forEach(r::valid); return r; }

    @Test void theCeilingIsInclusiveAndOneByteAboveIsRefused() {
        FakeReader r = allValid().cfg("futures.footprint.bars", CEILING, "producer");
        new FootprintTopicGate(TOPICS, CEILING, r).validateExisting();
        FakeReader over = allValid().cfg("futures.footprint.bars", CEILING + 1, "producer");
        FootprintTopicGate g = new FootprintTopicGate(TOPICS, CEILING, over);
        IllegalStateException e = assertThrows(IllegalStateException.class, g::validateExisting);
        assertTrue(e.getMessage().contains("futures.footprint.bars: ceiling"), e.getMessage());
        assertEquals(1, g.failures("futures.footprint.bars", FootprintTopicGate.Reason.ceiling), "the start-up attempt counts");
        assertFalse(g.validated("futures.footprint.bars"));
        assertTrue(g.validated("futures.footprint"), "the other topics were validated by the same pass");
    }

    @Test void codecsAreRefusedProducerAndUncompressedAccepted() {
        for (String codec : new String[]{"gzip", "lz4", "snappy", "zstd"}) {
            FootprintTopicGate g = new FootprintTopicGate(TOPICS, CEILING, allValid().cfg("futures.footprint.outcomes", 1000, codec));
            assertThrows(IllegalStateException.class, g::validateExisting, codec);
            assertEquals(1, g.failures("futures.footprint.outcomes", FootprintTopicGate.Reason.compression));
        }
        for (String ok : new String[]{"producer", "uncompressed", "Producer "}) {
            new FootprintTopicGate(TOPICS, CEILING, allValid().cfg("futures.footprint.outcomes", 1000, ok)).validateExisting();
        }
    }

    @Test void ceilingTakesPrecedenceOverCompressionForOneAttempt() {
        FootprintTopicGate g = new FootprintTopicGate(TOPICS, CEILING, allValid().cfg("futures.footprint", CEILING + 5, "gzip"));
        assertThrows(IllegalStateException.class, g::validateExisting);
        assertEquals(1, g.failures("futures.footprint", FootprintTopicGate.Reason.ceiling));
        assertEquals(0, g.failures("futures.footprint", FootprintTopicGate.Reason.compression));
    }

    @Test void anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears() {
        FakeReader r = allValid().unknown("futures.footprint.evidence");
        FootprintTopicGate g = new FootprintTopicGate(TOPICS, CEILING, r);
        g.validateExisting();                                                  // no throw
        assertFalse(g.validated("futures.footprint.evidence"));
        assertEquals(1, g.failures("futures.footprint.evidence", FootprintTopicGate.Reason.unknown));
        assertFalse(g.admit("futures.footprint.evidence"), "not consumed while absent");
        assertEquals(2, g.failures("futures.footprint.evidence", FootprintTopicGate.Reason.unknown), "one unknown per attempt");
        assertTrue(g.admit("futures.footprint.bars"), "a validated topic is admitted without another read");
        int before = r.calls;
        g.admit("futures.footprint.bars");
        assertEquals(before, r.calls, "VALID is cached for the incarnation");
        r.valid("futures.footprint.evidence");                                  // the producer created it
        assertTrue(g.admit("futures.footprint.evidence"));
        assertTrue(g.validated("futures.footprint.evidence"));
        assertEquals(2, g.failures("futures.footprint.evidence", FootprintTopicGate.Reason.unknown), "success counts no failure");
        assertTrue(g.admit("some.other.topic"), "non-footprint topics always pass");
    }

    @Test void otherAdminFailuresRefuseStartUpButAreRetriedAtTheGate() {
        FootprintTopicGate g = new FootprintTopicGate(TOPICS, CEILING, allValid().admin("futures.footprint.bars"));
        IllegalStateException e = assertThrows(IllegalStateException.class, g::validateExisting);
        assertTrue(e.getMessage().contains("futures.footprint.bars: admin"));
        assertEquals(1, g.failures("futures.footprint.bars", FootprintTopicGate.Reason.admin));
        assertFalse(g.admit("futures.footprint.bars"));
        assertEquals(2, g.failures("futures.footprint.bars", FootprintTopicGate.Reason.admin));
    }

    @Test void aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne() {
        FakeReader r = allValid().unknown("futures.footprint.outcomes");
        FootprintTopicGate g = new FootprintTopicGate(TOPICS, CEILING, r);
        for (int i = 0; i < 3; i++) assertFalse(g.admit("futures.footprint.outcomes"));
        r.valid("futures.footprint.outcomes");
        assertTrue(g.admit("futures.footprint.outcomes"));
        assertEquals(3, g.failures("futures.footprint.outcomes", FootprintTopicGate.Reason.unknown));
        assertTrue(g.validated("futures.footprint.outcomes"));
    }

    @Test void theLayoutCheckRefusesEachOfTheThreeFlagsBeingFalseOrUnreadable() {
        Map<String, String> ok = new HashMap<>(Map.of("UseCompressedOops", "true", "UseCompressedClassPointers", "true", "CompactStrings", "true"));
        assertEquals(Optional.empty(), FootprintTopicGate.layoutViolation(ok::get));
        for (String flag : FootprintTopicGate.LAYOUT_FLAGS) {
            Map<String, String> m = new HashMap<>(ok); m.put(flag, "false");
            assertEquals(Optional.of(flag + "=false"), FootprintTopicGate.layoutViolation(m::get));
            Map<String, String> missing = new HashMap<>(ok); missing.remove(flag);
            assertEquals(Optional.of(flag + "=null"), FootprintTopicGate.layoutViolation(missing::get));
        }
        assertTrue(FootprintTopicGate.layoutViolation(f -> { throw new IllegalStateException("no bean"); }).orElse("").startsWith("UseCompressedOops (unreadable"));
        assertEquals(Optional.empty(), FootprintTopicGate.layoutViolation(FootprintTopicGate.hotSpotVmOptions()),
                "the JVM running this suite satisfies the G-R8 layout assumptions");
    }
}
