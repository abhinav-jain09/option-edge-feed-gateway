package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class DealerLedgerJoinerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode node(String json) throws Exception {
        return mapper.readTree(json);
    }

    private ObjectNode join(String profile, String state, boolean stale) throws Exception {
        return DealerLedgerJoiner.join(mapper,
                profile == null ? null : node(profile),
                state == null ? null : node(state),
                "DATABENTO", 15000L, stale);
    }

    @Test
    void nullBothInputsReturnsNull() {
        assertNull(DealerLedgerJoiner.join(mapper, null, null, "DATABENTO", 15000L, false));
    }

    @Test
    void defendedStatePinsPillToDefendedLevelWithPutAction() throws Exception {
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"asOfEventTimeMs\":100,"
                + "\"dealerNetDelta\":-1.2e8,\"netDealerGamma\":-4.5e7,\"regime\":\"AMPLIFY\","
                + "\"storedUnwindNotional\":8.0e7,\"spentUnwindPct\":0.92,\"nearestFlip\":5290,"
                + "\"pinCandidateStrike\":5300,\"inventoryConfidence\":\"HIGH\",\"greeksQuality\":\"FULL\"}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"asOfEventTimeMs\":120,"
                + "\"state\":\"DEFENDED\",\"stateSinceMs\":90,\"defendedLevel\":5300,\"triggerId\":\"t1\","
                + "\"spotQuality\":\"OK\","
                + "\"actions\":[{\"zone\":\"CALL_SELL_ZONE\",\"state\":\"CAUTION\",\"reasonCode\":\"rc1\"},"
                + "{\"zone\":\"PUT_SELL_ZONE\",\"state\":\"ACTIVE\",\"reasonCode\":\"rc2\"}],"
                + "\"decisionTrace\":[{\"condition\":\"G-DEF\",\"observedValue\":0.95,\"requiredThreshold\":0.90,\"passed\":true,\"reason\":\"cushion held\"}]}";
        ObjectNode env = join(profile, state, false);

        assertEquals("dealer-ledger", env.get("type").asText());
        assertEquals("SPXW", env.get("symbol").asText());
        assertEquals("20260704", env.get("expiry").asText());
        assertEquals("DATABENTO", env.get("marketDataSource").asText());
        assertEquals(120L, env.get("asOfEventTimeMs").asLong()); // max of profile/state event times

        JsonNode strikes = env.get("strikes");
        assertEquals(1, strikes.size());
        JsonNode e = strikes.get(0);
        assertEquals(5300.0, e.get("strike").asDouble());
        assertEquals("DEFENDED", e.get("state").asText());
        assertEquals("PUT", e.get("action").asText());          // PUT_SELL_ZONE is most permissive (ACTIVE)
        assertEquals("ACTIVE", e.get("permission").asText());
        assertEquals("cushion held", e.get("reason").asText()); // last decisionTrace reason
        assertEquals("t1", e.get("triggerId").asText());
        assertEquals(90L, e.get("sinceMs").asLong());
        assertFalse(e.get("stale").asBoolean());

        JsonNode trace = e.get("trace");
        assertEquals(1, trace.size());
        assertEquals("G-DEF", trace.get(0).get("condition").asText());
        assertEquals(0.95, trace.get(0).get("observed").asDouble());   // observedValue -> observed
        assertEquals(0.90, trace.get(0).get("threshold").asDouble());  // requiredThreshold -> threshold
        assertTrue(trace.get(0).get("passed").asBoolean());

        JsonNode book = env.get("book");
        assertEquals(-1.2e8, book.get("netDealerDeltaUsd").asDouble());
        assertEquals(-4.5e7, book.get("netDealerGammaUsd").asDouble());
        assertEquals(0.92, book.get("spentUnwindPct").asDouble());
        assertEquals(5290.0, book.get("flipLevel").asDouble());
        assertEquals(5300.0, book.get("pinCandidate").asDouble());
        assertEquals(5300.0, book.get("defendedLevel").asDouble());
        assertEquals("HIGH", book.get("inventoryConfidence").asText());
        assertEquals("OK", book.get("spotQuality").asText());
        assertEquals(2, book.get("actions").size());
        assertEquals("PUT_SELL_ZONE", book.get("actions").get(1).get("zone").asText());
    }

    @Test
    void callArmedDoesNotUseDefendedLevelAsAnchor() throws Exception {
        // CALL-side ARMED must anchor to pinCandidateStrike (5350), NEVER the put-side defendedLevel (5340),
        // even though defendedLevel is present (defense-candidate lifecycle). This is a wrong-strike guard.
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"pinCandidateStrike\":5350}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"ARMED\",\"defendedLevel\":5340,"
                + "\"actions\":[{\"zone\":\"CALL_SELL_ZONE\",\"state\":\"ACTIVE\"},{\"zone\":\"PUT_SELL_ZONE\",\"state\":\"WAITING\"}]}";
        ObjectNode env = join(profile, state, false);
        JsonNode e = env.get("strikes").get(0);
        assertEquals(5350.0, e.get("strike").asDouble()); // pinCandidateStrike, NOT defendedLevel 5340
        assertEquals("CALL", e.get("action").asText());
        assertEquals("ACTIVE", e.get("permission").asText());
    }

    @Test
    void defendedLevelPresentButDominantActionCALLDoesNotAnchorToDefendedLevel() throws Exception {
        // Explicit: defendedLevel set + CALL dominant ⇒ the entry strike is NOT defendedLevel.
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"pinCandidateStrike\":5400}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"ARMED\",\"defendedLevel\":5310,"
                + "\"actions\":[{\"zone\":\"CALL_SELL_ZONE\",\"state\":\"ACTIVE\"}]}";
        ObjectNode env = join(profile, state, false);
        assertEquals(5400.0, env.get("strikes").get(0).get("strike").asDouble());
        assertNotEquals(5310.0, env.get("strikes").get(0).get("strike").asDouble());
    }

    @Test
    void callArmedWithNoCallAnchorDoesNotRenderWrongStrikePill() throws Exception {
        // CALL-side ARMED with NO pinCandidateStrike must NOT fall back to defendedLevel: no pill at all
        // (refuse to fabricate the wrong strike), book only.
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"netDealerGamma\":-1e7}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"ARMED\",\"defendedLevel\":5310,"
                + "\"actions\":[{\"zone\":\"CALL_SELL_ZONE\",\"state\":\"ACTIVE\"}]}";
        ObjectNode env = join(profile, state, false);
        assertEquals(0, env.get("strikes").size());
        assertEquals(5310.0, env.get("book").get("defendedLevel").asDouble()); // still shown in the book, just not as the pill anchor
    }

    @Test
    void putDefendedUsesDefendedLevel() throws Exception {
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"pinCandidateStrike\":5350}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"DEFENDED\",\"defendedLevel\":5300,"
                + "\"actions\":[{\"zone\":\"PUT_SELL_ZONE\",\"state\":\"ACTIVE\"}]}";
        ObjectNode env = join(profile, state, false);
        JsonNode e = env.get("strikes").get(0);
        assertEquals(5300.0, e.get("strike").asDouble()); // put/defense side anchors to defendedLevel
        assertEquals("PUT", e.get("action").asText());
    }

    @Test
    void anchorFallsBackToPinCandidateWhenNoDefendedLevel() throws Exception {
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"pinCandidateStrike\":5350}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"ARMED\","
                + "\"actions\":[{\"zone\":\"CALL_SELL_ZONE\",\"state\":\"ACTIVE\"}]}";
        ObjectNode env = join(profile, state, false);
        assertEquals(5350.0, env.get("strikes").get(0).get("strike").asDouble());
    }

    @Test
    void actionableStateWithNoAnchorEmitsBookButNoStrikeEntry() throws Exception {
        // Pure call-side ARMED with no defendedLevel and no pinCandidateStrike: refuse to fabricate.
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"netDealerGamma\":-1e7}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"ARMED\","
                + "\"actions\":[{\"zone\":\"CALL_SELL_ZONE\",\"state\":\"ACTIVE\"}]}";
        ObjectNode env = join(profile, state, false);
        assertEquals(0, env.get("strikes").size());
        assertEquals(-1e7, env.get("book").get("netDealerGammaUsd").asDouble()); // book still present
    }

    @Test
    void quietAndInvalidatedStatesEmitNoStrikeEntry() throws Exception {
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"pinCandidateStrike\":5300}";
        for (String st : new String[]{"QUIET", "INVALIDATED", "INSUFFICIENT_DATA"}) {
            String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"" + st + "\",\"defendedLevel\":5300}";
            ObjectNode env = join(profile, state, false);
            assertEquals(0, env.get("strikes").size(), st + " should surface no pill");
        }
    }

    @Test
    void missingBookFieldsAreOmittedNotZeroed() throws Exception {
        // A degraded profile with no numbers must NOT emit fake 0s — the UI shows '—' for absent fields.
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\"}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"ARMING\",\"defendedLevel\":5300}";
        ObjectNode env = join(profile, state, false);
        JsonNode book = env.get("book");
        assertFalse(book.has("netDealerDeltaUsd"));
        assertFalse(book.has("spentUnwindPct"));
        assertFalse(book.has("flipLevel"));
    }

    @Test
    void profileOnlyBeforeAnyStateEmitsBookOnly() throws Exception {
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"netDealerGamma\":-5e7,\"pinCandidateStrike\":5300}";
        ObjectNode env = join(profile, null, false);
        assertEquals("SPXW", env.get("symbol").asText());
        assertEquals(0, env.get("strikes").size()); // no state ⇒ no session ⇒ no pill
        assertEquals(-5e7, env.get("book").get("netDealerGammaUsd").asDouble());
    }

    @Test
    void stateOnlyAfterProfileExpiresKeepsThePillWithAnEmptyBook() throws Exception {
        // Mirrors removeCacheEntry rebuilding from the surviving fresh half: profile aged out (null),
        // state still fresh -> the actionable pill MUST survive; the book is simply empty ('—' in UI).
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"DEFENDED\",\"defendedLevel\":5300,"
                + "\"actions\":[{\"zone\":\"PUT_SELL_ZONE\",\"state\":\"ACTIVE\"}]}";
        ObjectNode env = join(null, state, false);
        assertEquals(1, env.get("strikes").size());
        assertEquals(5300.0, env.get("strikes").get(0).get("strike").asDouble());
        assertEquals("DEFENDED", env.get("strikes").get(0).get("state").asText());
        assertFalse(env.get("book").has("netDealerGammaUsd")); // no profile -> book fields absent, not faked
    }

    @Test
    void staleFlagPropagatesToEnvelopeAndEntry() throws Exception {
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"pinCandidateStrike\":5300}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"state\":\"DEFENDED\",\"defendedLevel\":5300}";
        ObjectNode env = join(profile, state, true);
        assertTrue(env.get("stale").asBoolean());
        assertTrue(env.get("strikes").get(0).get("stale").asBoolean());
    }

    @Test
    void expiryIsNormalized() throws Exception {
        String profile = "{\"symbol\":\"SPXW\",\"expiry\":\"2026-07-04\",\"pinCandidateStrike\":5300}";
        String state = "{\"symbol\":\"SPXW\",\"expiry\":\"2026-07-04\",\"state\":\"DEFENDED\",\"defendedLevel\":5300}";
        ObjectNode env = join(profile, state, false);
        assertEquals("20260704", env.get("expiry").asText());
    }
}
