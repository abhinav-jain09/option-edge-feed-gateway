package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The studies relay's switches: OFF by default, its own topics, the frame allowlist, and the hello fields' presence as the flag. */
class StudiesWiringTest {

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    @Test
    void isOffByDefaultAndNamesTheProducersTopicsLocalAndMirrored() {
        GatewaySettings s = new GatewaySettings();
        assertFalse(s.studiesEnabled(), "OFF by default — the flag is the last rollout step");
        assertEquals(List.of("options.indicators.studies.bars", "es.options.indicators.studies.bars"), List.copyOf(s.studiesBarsTopics()));
        assertEquals(List.of("options.indicators.studies.current", "es.options.indicators.studies.current"), List.copyOf(s.studiesCurrentTopics()));
        assertEquals(72L * 3_600_000L, s.studiesRecoveryMs());
    }

    @Test
    void theTopicNamesAreFixedSoNoOverrideCanMisrouteTheMirror() {
        System.setProperty("KAFKA_INDICATORS_STUDIES_BARS_TOPIC", "x.bars");
        System.setProperty("GATEWAY_STUDIES_RECOVERY_HOURS", "5");
        try {
            GatewaySettings s = new GatewaySettings();
            assertEquals(List.of("options.indicators.studies.bars", "es.options.indicators.studies.bars"), List.copyOf(s.studiesBarsTopics()),
                    "the local name is not overridable: the es. half could not follow it");
            assertEquals(5L * 3_600_000L, s.studiesRecoveryMs());
        } finally {
            System.clearProperty("KAFKA_INDICATORS_STUDIES_BARS_TOPIC");
            System.clearProperty("GATEWAY_STUDIES_RECOVERY_HOURS");
        }
    }

    @Test
    void bothFramesAreGlobalBroadcastEventsAndTheEventPredicateKnowsExactlyThem() {
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains("indicator-studies"));
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains("indicator-studies-current"));
        assertTrue(FeedGatewayService.isStudiesEvent("indicator-studies"));
        assertTrue(FeedGatewayService.isStudiesEvent("indicator-studies-current"));
        assertFalse(FeedGatewayService.isStudiesEvent("indicator-signals"));
        assertFalse(FeedGatewayService.isStudiesEvent(null));
    }

    @Test
    void flagOffTheHelloHasNoStudyFieldAndEnoughOtherwiseToStaySilent() {
        var off = service();
        assertFalse(off.sendsCvdHello(), "no stream enabled: no hello at all");
        assertFalse(off.cvdHelloJson().contains("tudies"));
        assertFalse(off.metrics().contains("gateway_studies"), "flag off: not one study metric line");
    }

    @Test
    void flagOnTheHelloCarriesAllFourFieldsWithNullsBeforeAnyRecord() throws Exception {
        System.setProperty("GATEWAY_STUDIES_ENABLED", "true");
        try {
            var on = service();
            assertTrue(on.sendsCvdHello(), "the studies join the hello");
            JsonNodeHolder h = new JsonNodeHolder(on.cvdHelloJson());
            for (String f : List.of("studies", "esStudies")) {
                assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null}", h.field(f), f);
            }
            for (String f : List.of("studyBars", "esStudyBars")) {
                assertEquals("{\"1m\":[],\"5m\":[],\"15m\":[]}", h.field(f), f);
            }
        } finally {
            System.clearProperty("GATEWAY_STUDIES_ENABLED");
        }
    }

    @Test
    void theStudyTopicsAreBoundOnTheLiveConsumerOnlyAndTheFlagGuardsThem() throws Exception {
        String src = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        assertEquals(1, count(src, "topicEvents.put(barsTopic, new TopicBinding(\"DATABENTO\", StudiesRelay.BAR_EVENT))"), "one binding site: the live consumer");
        assertEquals(1, count(src, "topicEvents.put(currentTopic, new TopicBinding(\"DATABENTO\", StudiesRelay.CURRENT_EVENT))"));
        int bind = src.indexOf("topicEvents.put(barsTopic");
        assertTrue(src.lastIndexOf("if (settings.studiesEnabled()) {", bind) > src.lastIndexOf("}", bind - 200), "the binding sits inside the flag guard");
        assertTrue(src.indexOf("runLiveConsumer(\"avro-live\"", bind) > bind, "…of the avro-live consumer");
        assertFalse(src.contains("StudiesRelay.COALESCE"), "bars are never coalesced: every finalized bar must reach the page");
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    /** Minimal access to a field of the hello's top-level object, as the exact JSON text. */
    private static final class JsonNodeHolder {
        private final com.fasterxml.jackson.databind.JsonNode root;

        JsonNodeHolder(String json) throws Exception {
            this.root = new ObjectMapper().readTree(json);
        }

        String field(String name) {
            return root.get(name).toString();
        }
    }

    @Test
    void theRecoveryWindowIsClampedAgainstAnAbsurdConfiguration() {
        System.setProperty("GATEWAY_STUDIES_RECOVERY_HOURS", String.valueOf(Long.MAX_VALUE));
        try {
            assertEquals(720L * 3_600_000L, new GatewaySettings().studiesRecoveryMs(), "no overflow, at most 30 days");
        } finally {
            System.clearProperty("GATEWAY_STUDIES_RECOVERY_HOURS");
        }
    }

    @Test
    void theStudiesAreAppendedLastWithWhatTheRestOfTheHelloLeavesUnderTheFrameGuard() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int hello = src.indexOf("String cvdHelloJson(String footprintStrikeField)");
        int footprint = src.indexOf("sb.append(\",\\\"footprintStrike\\\":\")", hello);
        int studies = src.indexOf("sb.append(studies.helloFields(budget))", hello);
        assertTrue(hello > 0 && footprint > hello && studies > footprint, "appended after every other field");
        assertTrue(src.contains("studiesHelloBudget(StudiesRelay.utf8Length(sb.toString()))"));
        assertEquals(1_048_576 - 4_096, FeedGatewayService.HELLO_FRAME_TARGET_BYTES);
    }

    @Test
    void theHardGuardHoldsAtTheExactBoundary() {
        int skeleton = StudiesRelay.skeletonBytes();
        int hard = FeedGatewayService.HELLO_FRAME_HARD_BYTES;
        assertTrue(skeleton < (hard - FeedGatewayService.HELLO_FRAME_TARGET_BYTES) / 8, "the margin absorbs the skeleton many times over");
        int lastAllowed = hard - 1 - skeleton;
        assertEquals(0, FeedGatewayService.studiesHelloBudget(lastAllowed), "past the target the budget is the floor: the skeleton alone");
        assertEquals(-1, FeedGatewayService.studiesHelloBudget(lastAllowed + 1), "one byte more and even the skeleton would breach the guard: leave the studies out");
        assertEquals(-1, FeedGatewayService.studiesHelloBudget(hard - 100));
        assertEquals(-1, FeedGatewayService.studiesHelloBudget(Integer.MAX_VALUE), "no int overflow bypasses the guard");
        assertEquals(-1, FeedGatewayService.studiesHelloBudget(Integer.MAX_VALUE - 10));
        assertTrue(FeedGatewayService.studiesHelloBudget(1000) > 500_000, "an ordinary hello leaves the studies their full share");
        StudiesRelay relay = new StudiesRelay(new com.fasterxml.jackson.databind.ObjectMapper());
        assertTrue(lastAllowed + 1 + StudiesRelay.utf8Length(relay.helloFields(FeedGatewayService.studiesHelloBudget(lastAllowed))) <= hard,
                "the whole frame at the last allowed prefix is within the hard guard");
    }

    @Test
    void everyStudiesCallOnTheSharedConsumerPathSitsBehindTheFlag() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        for (String call : List.of("seekStudies(consumer, partitions, topicEvents);", "seekStudies(consumer, refresh.added(), topicEvents);",
                "studiesRecovery.begin(studiesEndOffsets(consumer, partitions, topicEvents));",
                "studiesRecovery.begin(studiesEndOffsets(consumer, refresh.added(), topicEvents));", "finishStudiesRecovery(consumer, studiesRecovery);")) {
            int at = src.indexOf(call);
            assertTrue(at > 0, call);
            assertEquals(src.indexOf(call, at + 1), -1, "one site: " + call);
            assertTrue(src.substring(Math.max(0, at - 90), at).contains("if (settings.studiesEnabled()) {"), "guarded: " + call);
        }
    }
}
