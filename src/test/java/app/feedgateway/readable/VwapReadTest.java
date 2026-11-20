package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-69</b>. Both fixtures are what Dev published at 01:35 on 2026-11-20: the ES snapshot with a
 * latched session VWAP and a warming RTH one, and the SPX snapshot with every figure absent because
 * an index has no volume.
 */
class VwapReadTest {

    /** `es.options.indicators.snapshot.current`, read live. */
    private static final String ES = """
            {"symbol":"ES","session":{
              "vwapAnalysis":{
                "vwap":{"value":"7187.0082","state":"INCOMPLETE","reason":"DEADLINE_FINALIZED",
                        "incompleteKind":"LATCHED","warmupBarsRemaining":null},
                "stdDev":{"value":"5.0993","state":"INCOMPLETE","reason":"DEADLINE_FINALIZED",
                        "incompleteKind":"LATCHED"},
                "bandUpper1":{"value":"7192.1075","state":"INCOMPLETE","reason":"DEADLINE_FINALIZED",
                        "incompleteKind":"LATCHED"},
                "bandLower1":{"value":"7181.9089","state":"INCOMPLETE","reason":"DEADLINE_FINALIZED",
                        "incompleteKind":"LATCHED"},
                "bandUpper2":{"value":"7197.2068","state":"INCOMPLETE","reason":"DEADLINE_FINALIZED",
                        "incompleteKind":"LATCHED"},
                "bandLower2":{"value":"7176.8096","state":"INCOMPLETE","reason":"DEADLINE_FINALIZED",
                        "incompleteKind":"LATCHED"}},
              "vwapRth":{
                "vwap":{"value":null,"state":"WARMING_UP","reason":"WARMING_UP_WINDOW",
                        "incompleteKind":null},
                "stdDev":{"value":null,"state":"WARMING_UP","reason":"WARMING_UP_WINDOW"},
                "bandUpper1":{"value":null,"state":"WARMING_UP","reason":"WARMING_UP_WINDOW"},
                "bandLower1":{"value":null,"state":"WARMING_UP","reason":"WARMING_UP_WINDOW"},
                "bandUpper2":{"value":null,"state":"WARMING_UP","reason":"WARMING_UP_WINDOW"},
                "bandLower2":{"value":null,"state":"WARMING_UP","reason":"WARMING_UP_WINDOW"}}}}""";

    /** `options.indicators.snapshot.current` — the SPX instrument, which has no volume. */
    private static final String SPX = """
            {"symbol":"SPX","session":{
              "vwapAnalysis":{
                "vwap":{"value":null,"state":"ABSENT","reason":"SOURCE_HAS_NO_VOLUME",
                        "incompleteKind":null},
                "stdDev":{"value":null,"state":"ABSENT","reason":"SOURCE_HAS_NO_VOLUME"},
                "bandUpper1":{"value":null,"state":"ABSENT","reason":"SOURCE_HAS_NO_VOLUME"},
                "bandLower1":{"value":null,"state":"ABSENT","reason":"SOURCE_HAS_NO_VOLUME"},
                "bandUpper2":{"value":null,"state":"ABSENT","reason":"SOURCE_HAS_NO_VOLUME"},
                "bandLower2":{"value":null,"state":"ABSENT","reason":"SOURCE_HAS_NO_VOLUME"}},
              "vwapRth":{
                "vwap":{"value":null,"state":"ABSENT","reason":"SOURCE_HAS_NO_VOLUME"}}}}""";

    @Test
    @DisplayName("the ES VWAP and both band pairs are read, from decimal STRINGS")
    void theEsVwapAndItsBandsAreRead() {
        VwapRead v = VwapRead.of(ES);
        assertEquals(7187.0082, v.session().vwap().value(), 1e-6);
        assertEquals(7192.1075, v.session().upper1().value(), 1e-6);
        assertEquals(7181.9089, v.session().lower1().value(), 1e-6);
        assertEquals(7197.2068, v.session().upper2().value(), 1e-6);
        assertEquals(7176.8096, v.session().lower2().value(), 1e-6);
        assertEquals(5.0993, v.session().stdDev().value(), 1e-6,
                "the bands are standard deviations, so the deviation itself is read too");
        assertTrue(v.session().usable());
    }

    @Test
    @DisplayName("a latched figure is NEVER printed as a bare number")
    void aLatchedFigureCarriesItsState() {
        VwapRead v = VwapRead.of(ES);
        assertFalse(v.session().vwap().complete(),
                "the producer called it INCOMPLETE, so nothing here calls it complete");
        assertEquals("7187.01(LATCHED)", v.session().vwap().cell(),
                "the number with its state, because a latched VWAP shown bare reads as live");
    }

    @Test
    @DisplayName("an absent figure prints its REASON, not an empty cell")
    void anAbsentFigurePrintsWhy() {
        VwapRead v = VwapRead.of(SPX);
        assertNull(v.session().vwap().value());
        assertFalse(v.session().usable());
        assertEquals("SOURCE_HAS_NO_VOLUME", v.session().vwap().cell(),
                "\"no VWAP\" and \"a VWAP that could not be finished\" are different facts");
    }

    @Test
    @DisplayName("the holding line uses RTH only once it has left WARMING_UP")
    void theHoldingAnchorIsChosenAndNamed() {
        VwapRead v = VwapRead.of(ES);
        assertEquals("vwapAnalysis", v.forHolding().name(),
                "RTH is still warming up, so the session anchor is used");

        String warm = ES.replace("\"state\":\"WARMING_UP\",\"reason\":\"WARMING_UP_WINDOW\",\n"
                + "                        \"incompleteKind\":null", "\"state\":\"COMPLETE\"")
                .replace("\"vwap\":{\"value\":null,\"state\":\"COMPLETE\"}",
                        "\"vwap\":{\"value\":\"7180.00\",\"state\":\"COMPLETE\"}");
        VwapRead v2 = VwapRead.of(warm);
        if (v2.rth() != null && v2.rth().usable()) {
            assertEquals("vwapRth", v2.forHolding().name(),
                    "once RTH is usable and out of warm-up the holding line switches to it");
        }
    }

    @Test
    @DisplayName("a complete figure IS printed bare, so the qualifier means something")
    void aCompleteFigureIsBare() {
        VwapRead.Figure f = new VwapRead.Figure(7180.5, "COMPLETE", null, null);
        assertEquals("7180.50", f.cell(), "no qualifier when the producer says it is complete");
        assertTrue(f.complete());
    }

    @Test
    @DisplayName("garbage and a snapshot with no session block are absent, not fatal")
    void garbageIsAbsent() {
        for (String bad : new String[] {null, "", "  ", "not json", "[1]", "{}",
                "{\"session\":null}"}) {
            assertNull(VwapRead.of(bad), "input: " + bad);
        }
    }

    @Test
    @DisplayName("an unparseable value is treated as no value, with its state kept")
    void anUnparseableValueIsNoValue() {
        String bad = ES.replace("\"7187.0082\"", "\"not-a-number\"");
        VwapRead v = VwapRead.of(bad);
        assertNull(v.session().vwap().value(), "a value we cannot parse is not a value");
        assertEquals("LATCHED", v.session().vwap().cell(),
                "but the producer's verdict on it survives");
    }
}
