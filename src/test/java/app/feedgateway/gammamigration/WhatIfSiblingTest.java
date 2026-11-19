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
    @DisplayName("A strike missing one of the four is LEFT OUT; every other strike still publishes")
    void aStrikeMissingOneOfTheFourIsLeftOut() throws Exception {
        String holed = mapRecord("SPX", "20261119")
                .replace("\"deltaBand\":{\"lowerEdge\":7398.5,\"upperEdge\":7481.5},", "");
        JsonNode whatIf = MAPPER.readTree(WhatIfSibling.merge(MAPPER, GAMMA, holed,
                "SPX", "20261119")).get("whatIf");

        assertFalse(whatIf.get("strikes").has("744000"),
                "a strike that cannot be described is named by its absence, not by a zero that "
                        + "reads like a measurement");
        assertTrue(whatIf.get("strikes").has("745000"),
                "and the rest of the board still publishes");
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
