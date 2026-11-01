package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Phase F: the bytes the REAL producer wrote (break-classifier-service RecordFactory/Engine; 17_break_fixtures/producer-records.jsonl: the raw Avro datum and the contract JSON of each record)
 * pass through the production {@link GatewayAvroDeserializer} against a registry that holds the three producer schemas under their registered ids 2601-2603, Confluent-framed, then through
 * {@link BreakClassificationRelay}. The frame must equal the contract JSON EXACTLY: the same text (field order, integers without fraction or exponent, enum symbols, bare unions, nulls) and the same tree.
 * Sets: development-* is what the producer publishes today (every mark UNDECIDED / NOT_EVALUATED, no source CALIBRATED); calibrated-* holds a real LIKELY_REAL CALIBRATED mark, the reachable-later case.
 */
class BreakClassificationProducerRecordsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> SCOPES = List.of("f-golden", "f-hello", "f-poison");

    @AfterAll
    static void dropScopes() {
        SCOPES.forEach(io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry::dropScope);
    }

    private static Map<String, List<BreakFixtures.Real>> bySet() throws Exception {
        Map<String, List<BreakFixtures.Real>> sets = new LinkedHashMap<>();
        for (BreakFixtures.Real r : BreakFixtures.real()) {
            sets.computeIfAbsent(r.set(), k -> new java.util.ArrayList<>()).add(r);
        }
        return sets;
    }

    private static String sha256(String resource) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BreakFixtures.text(resource).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theSchemaCopiesAreThePinnedProducerFiles() throws Exception {
        assertEquals("119dc2992eaa7c8f506192e45d457d7f1a640a52daf4bf2685920fb70b72f4a6", sha256("/break/BreakMark.avsc"));
        assertEquals("25b368184f2433ce04d365c2b3ed35841e396284bb4f24824ff735ff1e87c6ba", sha256("/break/BreakOutcome.avsc"));
        assertEquals("85a698a5d133e3c88948a8677bfcf793573501f868c3db67bb098bc1cbb0572a", sha256("/break/BreakStatus.avsc"));
    }

    @Test
    void everyRealProducerRecordIsRelayedAsTheExactContractJson() throws Exception {
        Map<String, List<BreakFixtures.Real>> sets = bySet();
        assertEquals(Set.of("development-1m", "development-5m", "development-15m", "calibrated-1m", "calibrated-5m", "calibrated-15m"), sets.keySet());
        try (GatewayAvroDeserializer d = BreakFixtures.deserializer("f-golden")) {
            int relayed = 0;
            for (Map.Entry<String, List<BreakFixtures.Real>> set : sets.entrySet()) {
                var relay = new BreakClassificationRelay();
                for (BreakFixtures.Real r : set.getValue()) {
                    byte[] framed = r.framed();
                    assertEquals(r.schemaId(), java.nio.ByteBuffer.wrap(framed, 1, 4).getInt());
                    Object decoded = d.deserialize(BreakFixtures.TOPIC, framed);
                    assertTrue(decoded instanceof org.apache.avro.generic.GenericRecord, set.getKey() + " " + r.type() + " must decode, not be UNDECODABLE");
                    String[] frame = relay.onLive(BreakClassificationRelay.EVENT, decoded);
                    assertNotNull(frame, set.getKey() + " " + r.type());
                    assertEquals(r.frameType(), frame[0]);
                    assertEquals(JSON.writeValueAsString(r.json()), frame[1], set.getKey() + " " + r.type() + ": the exact text");
                    assertEquals(r.json(), JSON.readTree(frame[1]), set.getKey() + " " + r.type() + ": the exact tree");
                    relayed++;
                }
                assertEquals(0, relay.dropsForTest(), set.getKey());
                assertEquals(set.getValue().size(), relay.acceptedForTest(), set.getKey());
                String metrics = relay.metrics();
                for (String reason : List.of("late", "orphan", "conflict", "evicted")) {
                    assertTrue(metrics.contains("gateway_break_classification_" + reason + "_total 0\n"), set.getKey() + " " + reason);
                }
            }
            assertEquals(49, relayed);
        }
    }

    @Test
    void theExactFormsOfNumbersEnumsAndNullsAreTheContractsOnes() throws Exception {
        try (GatewayAvroDeserializer d = BreakFixtures.deserializer("f-golden")) {
            Map<String, BreakClassificationRelay> relays = new LinkedHashMap<>();
            for (BreakFixtures.Real r : BreakFixtures.real()) {
                String json = relays.computeIfAbsent(r.set(), k -> new BreakClassificationRelay()).onLive(BreakClassificationRelay.EVENT, d.deserialize(BreakFixtures.TOPIC, r.framed()))[1];
                JsonNode node = JSON.readTree(json);
                for (JsonNode n : (Iterable<JsonNode>) () -> numbers(node).iterator()) {
                    assertTrue(n.isIntegralNumber() && n.asText().matches("-?\\d+"), "no fraction and no exponent in " + r.type() + ": " + n);
                }
                assertTrue(node.get("schemaVersion").isInt() && node.get("schemaVersion").asInt() == 1);
                if (r.type().equals("BreakMark")) {
                    assertTrue(node.get("timeframe").isTextual() && List.of("M1", "M5", "M15").contains(node.get("timeframe").asText()));
                    assertTrue(node.get("breakCloseCents").isIntegralNumber() && node.get("stageStartMs").isIntegralNumber());
                    assertTrue(node.has("esBarKey") && node.has("unknownSideBps") && node.has("scorePermille"), "every field present, also the null ones");
                    for (String union : List.of("scorePermille", "unknownSideBps", "esBarKey")) {
                        assertTrue(node.get(union).isNull() || node.get(union).isNumber() || node.get(union).isTextual(), "a bare union: " + union);
                    }
                    for (JsonNode c : node.get("contributions")) {
                        assertEquals(6, c.size());
                        assertTrue(c.get("featureId").isTextual() && c.get("evaluable").isBoolean());
                    }
                    assertTrue(node.get("calibration").isObject(), "nested records stay nested");
                }
            }
        }
    }

    private static java.util.stream.Stream<JsonNode> numbers(JsonNode node) {
        return node.isNumber() ? java.util.stream.Stream.of(node)
                : java.util.stream.StreamSupport.stream(node.spliterator(), false).flatMap(BreakClassificationProducerRecordsTest::numbers);
    }

    @Test
    void theDevelopmentSetsHaveNoCalibratedSourceAndTheCalibratedSetHasARealLikelyRealMark() throws Exception {
        long calibratedDevelopment = 0, likelyReal = 0;
        for (BreakFixtures.Real r : BreakFixtures.real()) {
            if (r.type().equals("BreakMark") && r.json().get("calibration").get("state").asText().equals("CALIBRATED")) {
                if (r.set().startsWith("development-")) {
                    calibratedDevelopment++;
                } else if (r.json().get("state").asText().equals("LIKELY_REAL") && r.json().get("reason").asText().equals("NONE")) {
                    likelyReal++;
                    assertEquals("calibrated-5m", r.set());
                }
            }
        }
        assertEquals(0, calibratedDevelopment);
        assertEquals(1, likelyReal);
    }

    @Test
    void theHelloCarriesTheRealRecordsExactlyAsTheLiveFramesInArrivalOrder() throws Exception {
        for (String tf : List.of("development-1m", "development-5m", "development-15m", "calibrated-5m")) {
            try (GatewayAvroDeserializer d = BreakFixtures.deserializer("f-hello")) {
                var relay = new BreakClassificationRelay();
                String key = tf.substring(tf.indexOf('-') + 1);
                ArrayNode marks = JSON.createArrayNode(), outcomes = JSON.createArrayNode();
                JsonNode status = null;
                Set<String> marked = new LinkedHashSet<>();
                for (BreakFixtures.Real r : bySet().get(tf)) {
                    relay.onLive(BreakClassificationRelay.EVENT, d.deserialize(BreakFixtures.TOPIC, r.framed()));
                    switch (r.type()) {
                        case "BreakMark" -> {
                            marks.add(r.json());
                            marked.add(r.json().get("classificationId").asText());
                        }
                        case "BreakOutcome" -> {
                            assertTrue(marked.contains(r.json().get("classificationId").asText()), "the producer's outcome follows its mark");
                            outcomes.add(r.json());
                        }
                        default -> status = r.json();
                    }
                }
                ObjectNode expected = JSON.createObjectNode();
                for (String slot : List.of("1m", "5m", "15m")) {
                    if (slot.equals(key)) {
                        ObjectNode retained = JSON.createObjectNode();
                        retained.set("marks", marks);
                        retained.set("outcomes", outcomes);
                        expected.set(slot, retained);
                    } else {
                        expected.putNull(slot);
                    }
                }
                expected.set("status", status == null ? JSON.nullNode() : status);
                String part = relay.helloField(Long.MAX_VALUE);
                assertTrue(part.startsWith(",\"breaks\":"));
                JsonNode hello = JSON.readTree("{" + part.substring(1) + "}").get("breaks");
                assertEquals(expected, hello, tf);
                assertEquals(JSON.writeValueAsString(expected), part.substring(",\"breaks\":".length()), tf + ": the exact text; the keys are 1m, 5m, 15m, status in that order");
                assertEquals(Arrays.asList("1m", "5m", "15m", "status"), java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(hello.fieldNames(), 0), false).toList());
            }
        }
    }

    @Test
    void poisonValuesAreCountedAndRetainNothing() throws Exception {
        BreakFixtures.Real mark = BreakFixtures.real().get(0);
        try (GatewayAvroDeserializer d = BreakFixtures.deserializer("f-poison")) {
            var relay = new BreakClassificationRelay();
            byte[] framed = mark.framed();
            Object retainedJson = d.deserialize(BreakFixtures.TOPIC, "{\"type\":\"retained json\"}".getBytes(StandardCharsets.UTF_8));
            Object truncated = d.deserialize(BreakFixtures.TOPIC, Arrays.copyOf(framed, framed.length / 2));
            Object unknownId = d.deserialize(BreakFixtures.TOPIC, BreakFixtures.frame(999_999, mark.datum()));
            Object unknownIdAgain = d.deserialize(BreakFixtures.TOPIC, BreakFixtures.frame(999_999, mark.datum()));   // within the 60 s memory
            Object tooShort = d.deserialize(BreakFixtures.TOPIC, new byte[] {0, 0});
            for (Object poison : new Object[] {retainedJson, truncated, unknownId, unknownIdAgain, tooShort}) {
                assertSame(GatewayAvroDeserializer.UNDECODABLE, poison);
                assertEquals(null, relay.onLive(BreakClassificationRelay.EVENT, poison));
            }
            assertEquals(5, relay.dropsForTest());
            assertEquals(0, relay.acceptedForTest());
            assertEquals(",\"breaks\":{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":null}", relay.helloField(Long.MAX_VALUE));
            // and the next real record is still relayed: poison never stalls the stream
            assertNotNull(relay.onLive(BreakClassificationRelay.EVENT, d.deserialize(BreakFixtures.TOPIC, framed)));
        }
    }
}
