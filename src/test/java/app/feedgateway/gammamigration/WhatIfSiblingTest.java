package app.feedgateway.gammamigration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-72: the additive {@code whatIf} sibling, and the three D-71 conditions on it. */
class WhatIfSiblingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GAMMA = """
            {"symbol":"SPX","expiry":"20261119","regime":"SHORT","hotStrike":745000,
             "strikes":[{"strikeCents":744000,"mass":0.4},{"strikeCents":745000,"mass":0.6}]}""";

    private static String mapRecord(String symbol, String expiry) {
        return """
                {"symbol":"%s","expiry":"%s",
                 "fingerprint":{"valuationTime":1795093000000,"chainVersion":"c-1"},
                 "strikes":[
                   {"strikeCents":744000,
                    "board":{"holdOrAccelerate":"HOLD","strength":0.42},
                    "deltaBand":{"lowerEdge":7398.5,"upperEdge":7481.5},
                    "decayToExpiry":{"magnitude":12.5,"direction":"TOWARD_SPOT"}},
                   {"strikeCents":745000,
                    "board":{"holdOrAccelerate":"ACCELERATE","strength":1.0},
                    "deltaBand":{"lowerEdge":7408.0,"upperEdge":7492.0},
                    "decayToExpiry":{"magnitude":3.25,"direction":"AWAY_FROM_SPOT"}}]}"""
                .formatted(symbol, expiry);
    }

    @Test
    @DisplayName("D-72: the sibling carries cycle time, symbol, expiry and the four values per strike")
    void theSiblingCarriesTheFourValues() throws Exception {
        String merged = WhatIfSibling.merge(MAPPER, GAMMA, mapRecord("SPX", "20261119"),
                "SPX", "20261119");
        JsonNode out = MAPPER.readTree(merged);
        JsonNode whatIf = out.get("whatIf");

        assertEquals(1795093000000L, whatIf.get("cycleTime").asLong(),
                "without the cycle time the page cannot say LIVE or STALE (D-71)");
        assertEquals("SPX", whatIf.get("symbol").asText());
        assertEquals("20261119", whatIf.get("expiry").asText());

        JsonNode row = whatIf.get("strikes").get("744000");
        assertEquals("HOLD", row.get("reading").asText());
        assertEquals(0.42, row.get("strength").asDouble(), 1e-12);
        assertEquals(12.5, row.get("decayToClose").get("magnitude").asDouble(), 1e-12);
        assertEquals("TOWARD_SPOT", row.get("decayToClose").get("direction").asText());
        assertEquals(7398.5, row.get("band").get("lowerEdge").asDouble(), 1e-12);
        assertEquals(7481.5, row.get("band").get("upperEdge").asDouble(), 1e-12);

        assertEquals("ACCELERATE", whatIf.get("strikes").get("745000").get("reading").asText());
    }

    @Test
    @DisplayName("D-72: no existing gamma field is touched")
    void noExistingFieldIsTouched() throws Exception {
        JsonNode before = MAPPER.readTree(GAMMA);
        JsonNode after = MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA,
                mapRecord("SPX", "20261119"), "SPX", "20261119"));

        before.fieldNames().forEachRemaining(f ->
                assertEquals(before.get(f), after.get(f),
                        "field '" + f + "' is the gamma record's and this gateway does not "
                                + "re-author it"));
        // and the only thing added is the sibling
        assertEquals(before.size() + 1, after.size());
    }

    @Test
    @DisplayName("D-71: with no map record the key is ABSENT — not null, not empty, byte-identical")
    void withNoMapRecordTheKeyIsAbsent() throws Exception {
        for (String none : new String[]{null, "", "   "}) {
            String merged = WhatIfSibling.merge(MAPPER, GAMMA, none, "SPX", "20261119");
            assertSame(GAMMA, merged,
                    "the gamma record is served as it stands, not re-serialised");
            assertFalse(MAPPER.readTree(merged).has("whatIf"),
                    "absent, not null and not zero: the board renders exactly as today");
        }
    }

    @Test
    @DisplayName("D-71: a what-if row of ANOTHER chain is not joined — symbol or expiry")
    void anotherChainsRecordIsNotJoined() throws Exception {
        // same strike numbers, different expiry: the join would look entirely plausible
        assertFalse(MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA,
                        mapRecord("SPX", "20261120"), "SPX", "20261119")).has("whatIf"),
                "a different expiry carries the same strike numbers");
        assertFalse(MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA,
                        mapRecord("NDX", "20261119"), "SPX", "20261119")).has("whatIf"));
        // and the matching one still joins, so the test above is not passing for a second reason
        assertTrue(MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA,
                mapRecord("SPX", "20261119"), "SPX", "20261119")).has("whatIf"));
    }

    @Test
    @DisplayName("D-71: the caller's symbol is matched case- and space-insensitively, the expiry exactly")
    void theChainMatchIsOnTheRecordsOwnFields() throws Exception {
        assertTrue(MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA,
                mapRecord("SPX", "20261119"), " spx ", " 20261119 ")).has("whatIf"));
    }

    @Test
    @DisplayName("A record with no cycle time is WITHHELD: an unknowable age must not read as current")
    void aRecordWithNoCycleTimeIsWithheld() throws Exception {
        String noCycle = mapRecord("SPX", "20261119")
                .replace("\"valuationTime\":1795093000000,", "");
        assertFalse(MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA, noCycle,
                "SPX", "20261119")).has("whatIf"));
    }

    @Test
    @DisplayName("C-43: a strike with NO BAND keeps its reading, strength and decay")
    void aStrikeWithNoBandKeepsEverythingElse() throws Exception {
        // The common case, not a rare one: R-DB-3 computes a band only within 100 points of
        // spot, so on a measured record 42 of 53 strikes legitimately had none. Dropping them
        // would blank four fifths of the owner's board.
        String noBand = mapRecord("SPX", "20261119")
                .replace("\"deltaBand\":{\"lowerEdge\":7398.5,\"upperEdge\":7481.5},", "");
        JsonNode row = MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA, noBand,
                "SPX", "20261119")).get("whatIf").get("strikes").get("744000");

        assertTrue(row != null, "the strike stays on the board");
        assertEquals("HOLD", row.get("reading").asText());
        assertEquals(0.42, row.get("strength").asDouble(), 1e-12);
        assertEquals(12.5, row.get("decayToClose").get("magnitude").asDouble(), 1e-12);
        assertFalse(row.has("band"),
                "the band is OMITTED, never zeroed -- a zero width reads like a measurement");
    }

    @Test
    @DisplayName("C-43: half a band is not a band")
    void oneEdgeIsNotABand() throws Exception {
        String halfBand = mapRecord("SPX", "20261119")
                .replace("\"lowerEdge\":7398.5,", "");
        JsonNode row = MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA, halfBand,
                "SPX", "20261119")).get("whatIf").get("strikes").get("744000");
        assertFalse(row.has("band"), "a page given one edge would have to invent the other");
        assertEquals("HOLD", row.get("reading").asText(), "and the rest of the row survives");
    }

    @Test
    @DisplayName("C-43: a row carrying NONE of the four is still dropped")
    void aRowWithNothingToSayIsStillDropped() throws Exception {
        String empty = """
                {"symbol":"SPX","expiry":"20261119",
                 "fingerprint":{"valuationTime":1795093000000},
                 "strikes":[{"strikeCents":744000}]}""";
        assertFalse(MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA, empty, "SPX", "20261119"))
                .has("whatIf"), "no strike has anything to say, so there is no board");
    }

    @Test
    @DisplayName("C-42: the gateway's own clock travels with the board")
    void theServerClockTravelsWithTheBoard() throws Exception {
        long before = System.currentTimeMillis();
        JsonNode whatIf = MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA,
                mapRecord("SPX", "20261119"), "SPX", "20261119")).get("whatIf");
        long after = System.currentTimeMillis();

        long serverTime = whatIf.get("serverTime").asLong();
        assertTrue(serverTime >= before && serverTime <= after,
                "serverTime is read when the response is built: " + serverTime);
        // the two the page subtracts are now readings of ONE clock
        assertTrue(whatIf.has("cycleTime") && whatIf.has("serverTime"),
                "staleness is measured between two readings of the gateway's clock, never "
                        + "against the viewer's machine");
    }

    @Test
    @DisplayName("The record DEV actually published, union envelopes and all, yields the four values")
    void theRealDevRecordYieldsTheFourValues() throws Exception {
        // Captured from options.spx.whatif.map on dev at the service's first publish after the
        // D-74 deploy (build 2354, digest 1536dbdf, cycle 1795097758006). Avro's own JSON
        // encoding wraps a nullable record in its branch's type name -- deltaBand is
        // {"DeltaBand": {...}} and netQuantity is {"double": ...} -- and this gateway's AvroJson
        // does NOT produce those envelopes. Both shapes must read, because guessing wrong here
        // fails SILENTLY: every strike would be dropped as "missing one of the four" and the
        // board would show four empty columns, which is exactly what it shows when the map
        // service has not published at all.
        String real = new String(getClass().getResourceAsStream("/whatif/dev-map-record.json")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String gamma = """
                {"symbol":"SPX","expiry":"20261119","regime":"SHORT"}""";

        JsonNode whatIf = MAPPER.readTree(WhatIfSibling.merge(MAPPER, gamma, real, "SPX",
                "20261119")).get("whatIf");

        assertEquals(1795097758006L, whatIf.get("cycleTime").asLong());
        JsonNode row = whatIf.get("strikes").get("731000");
        assertEquals("ACCELERATE", row.get("reading").asText());
        assertEquals(0.1140031228706515, row.get("strength").asDouble(), 1e-12);
        assertEquals("RISING", row.get("decayToClose").get("direction").asText());
        assertEquals(97.6448224863434, row.get("decayToClose").get("magnitude").asDouble(), 1e-9);

        // the band D-74's widening produced: 7310's 0.05 edge is 125.95 points BELOW the strike,
        // outside the 100-point span that refused for an hour and inside the 200-point one
        assertEquals(7184.045181274414, row.get("band").get("lowerEdge").asDouble(), 1e-9);
        assertEquals(7437.038955688477, row.get("band").get("upperEdge").asDouble(), 1e-9);
        assertTrue(7310.0 - row.get("band").get("lowerEdge").asDouble() > 100.0,
                "this edge is the whole reason D-74 exists");
    }

    @Test
    @DisplayName("A real field is never mistaken for a union envelope")
    void aRealSingleFieldObjectIsNotUnwrapped() throws Exception {
        // one field, lower-case, not a primitive branch name: left alone
        JsonNode plain = MAPPER.readTree("{\"lowerEdge\":1.0}");
        assertSame(plain, WhatIfSibling.unwrap(plain));
        // an envelope IS unwrapped, both forms
        assertEquals(1.0, WhatIfSibling.unwrap(
                MAPPER.readTree("{\"double\":1.0}")).asDouble(), 1e-12);
        assertEquals(2.0, WhatIfSibling.unwrap(
                MAPPER.readTree("{\"DeltaBand\":2.0}")).asDouble(), 1e-12);
        // two fields is a record, never an envelope
        JsonNode two = MAPPER.readTree("{\"double\":1.0,\"other\":2}");
        assertSame(two, WhatIfSibling.unwrap(two));
    }

    @Test
    @DisplayName("An unparseable cache entry serves the gamma record unchanged rather than failing")
    void anUnparseableCacheEntryIsNotAFailure() {
        assertSame(GAMMA, WhatIfSibling.merge(MAPPER, GAMMA, "{not json", "SPX", "20261119"));
        assertSame(GAMMA, WhatIfSibling.merge(MAPPER, GAMMA, "[1,2,3]", "SPX", "20261119"));
        // and a gamma record this gateway cannot parse is still served as it stands
        assertSame("{broken", WhatIfSibling.merge(MAPPER, "{broken",
                mapRecord("SPX", "20261119"), "SPX", "20261119"));
    }
}
