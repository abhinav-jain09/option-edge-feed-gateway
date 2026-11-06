package app.feedgateway;

import static app.feedgateway.EsLiquidityWallFixtures.CONTRACT;
import static app.feedgateway.EsLiquidityWallFixtures.ENV;
import static app.feedgateway.EsLiquidityWallFixtures.KEY;
import static app.feedgateway.EsLiquidityWallFixtures.NOW;
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

/** The JSON view of the three relayed records: the record's own fields in schema order, nulls kept, integers as integers, nothing invented. */
class EsLiquidityWallJsonTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static List<String> names(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.fieldNames().forEachRemaining(out::add);
        return out;
    }

    @Test
    void dashboardViewMirrorsTheSchemaFieldsInOrder() throws Exception {
        String json = EsLiquidityWallJson.dashboard(KEY, EsLiquidityWallFixtures.dashboard(NOW));
        assertNotNull(json);
        JsonNode v = JSON.readTree(json);
        assertEquals(List.of("schemaVersion", "depthEnvironment", "contract", "bucketEnd", "configFingerprint", "quality", "closePath", "layerBEnabled", "spotTick", "spotAgeMs",
                "levels", "clusters", "walls", "leanThousandths", "above", "below", "sideRead", "confirmedSide", "lastAdmittedFrameReceivedAtMs", "lastAdmittedStreamId",
                "closingFramePresent"), names(v));
        assertEquals(ENV, v.get("depthEnvironment").asText());
        assertEquals(CONTRACT, v.get("contract").asText());
        assertEquals(NOW, v.get("bucketEnd").longValue());
        assertTrue(v.get("bucketEnd").isIntegralNumber());
        assertEquals("LIVE", v.get("quality").asText());
        assertEquals("WATERMARK", v.get("closePath").asText());
        assertEquals(EsLiquidityWallFixtures.FINGERPRINT, v.get("configFingerprint").asText());
        assertEquals(-412, v.get("leanThousandths").intValue());
        assertEquals(2, v.get("levels").size());
        assertEquals(List.of("side", "tick", "lastSize", "smoothedSize", "state"), names(v.get("levels").get(0)));
        assertEquals(30460L, v.get("levels").get(1).get("tick").longValue());
        JsonNode wall = v.get("walls").get(0);
        assertEquals("C1", wall.get("clusterId").asText());
        assertTrue(wall.get("brokenBucketsFrozen").isNull(), "a null union is kept as null, never elided or defaulted");
        assertEquals(3, wall.get("frozenTicks").size());
        assertFalse(json.contains("."), "no floating-point field reaches the wire (every ratio is an integer in thousandths)");
    }

    @Test
    void eventViewCarriesTheWallOrTheFlipAndNullForTheOther() throws Exception {
        JsonNode born = JSON.readTree(EsLiquidityWallJson.event("C1", EsLiquidityWallFixtures.born(NOW, "C1")));
        assertEquals(List.of("schemaVersion", "depthEnvironment", "contract", "bucketEnd", "configFingerprint", "kind", "spotTick", "wall", "retireReason", "breachT0",
                "breachConfirmedAt", "breachTickReached", "flip"), names(born));
        assertEquals("BORN", born.get("kind").asText());
        assertEquals("RESISTANCE", born.get("wall").get("lane").asText());
        assertTrue(born.get("flip").isNull());
        assertTrue(born.get("retireReason").isNull());

        JsonNode retired = JSON.readTree(EsLiquidityWallJson.event("C1", EsLiquidityWallFixtures.retired(NOW, "C1", "BREACHED")));
        assertEquals("BREACHED", retired.get("retireReason").asText());

        JsonNode flip = JSON.readTree(EsLiquidityWallJson.event(KEY + "|FLIP", EsLiquidityWallFixtures.flip(NOW)));
        assertEquals("FLIP", flip.get("kind").asText());
        assertTrue(flip.get("wall").isNull());
        assertEquals("FLIP_TO_DOWNSIDE", flip.get("flip").get("direction").asText());
        assertEquals(640, flip.get("flip").get("fadeShareThousandths").intValue());
        assertEquals(ENV, flip.get("depthEnvironment").asText());
    }

    @Test
    void voiceViewRelaysTheSpokenTextVerbatimAndTheStaleFlagIsAppendedAtDelivery() throws Exception {
        String json = EsLiquidityWallJson.voice(KEY, EsLiquidityWallFixtures.voice(NOW));
        assertNotNull(json);
        JsonNode v = JSON.readTree(json);
        assertEquals(List.of("schemaVersion", "depthEnvironment", "contract", "bucketEnd", "configFingerprint", "voiceId", "text", "direction", "spotTick"), names(v));
        assertEquals("liquidity flip - downside, 7612.25", v.get("text").asText());
        assertEquals(30449L, v.get("spotTick").longValue());
        JsonNode stale = JSON.readTree(EsLiquidityWallJson.withStale(json, true));
        assertTrue(stale.get("stale").booleanValue());
        assertEquals("liquidity flip - downside, 7612.25", stale.get("text").asText());
        assertFalse(JSON.readTree(EsLiquidityWallJson.withStale(json, false)).get("stale").booleanValue());
    }

    @Test
    void theBoundaryRefusesTheWrongKeyRecordTypeAndVersion() {
        GenericRecord dashboard = EsLiquidityWallFixtures.dashboard(NOW);
        assertNull(EsLiquidityWallJson.dashboard("CME|ESH7", dashboard), "the key must be CME|<contract>");
        assertNull(EsLiquidityWallJson.event(KEY, dashboard), "a dashboard record on the events topic is refused by its writer schema name");
        assertNull(EsLiquidityWallJson.voice(KEY, dashboard));
        assertNull(EsLiquidityWallJson.dashboard(KEY, EsLiquidityWallFixtures.dashboard(NOW, "LIVE").set("schemaVersion", 3).build()));
        assertNull(EsLiquidityWallJson.dashboard(KEY, EsLiquidityWallFixtures.dashboard(0L, "LIVE").build()), "a bucket end of 0 is refused");
        assertNull(EsLiquidityWallJson.dashboard(KEY, EsLiquidityWallFixtures.dashboard(NOW, "LIVE").set("depthEnvironment", " ").build()));
        assertNull(EsLiquidityWallJson.dashboard(KEY, null), "a tombstone");
        assertNull(EsLiquidityWallJson.event("C2", EsLiquidityWallFixtures.born(NOW, "C1")), "a wall event's key is its clusterId");
        assertNull(EsLiquidityWallJson.event(KEY, EsLiquidityWallFixtures.flip(NOW)), "a flip's key is CME|<contract>|FLIP");
        assertNull(EsLiquidityWallJson.event(KEY + "|FLIP", EsLiquidityWallFixtures.event(NOW, "FLIP").build()), "a FLIP without its flip record");
        assertNull(EsLiquidityWallJson.event("C1", EsLiquidityWallFixtures.event(NOW, "BORN").build()), "a wall event without its wall");
        assertNull(EsLiquidityWallJson.voice("CME|ESZ6|FLIP", EsLiquidityWallFixtures.voice(NOW)), "the voice key is the dashboard key");
    }
}
