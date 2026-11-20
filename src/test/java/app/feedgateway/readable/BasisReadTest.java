package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** <b>C-69</b>: the basis is read and labelled, never recomputed, and only ANCHORED translates. */
class BasisReadTest {

    /** The real record from `spx.basis.state` on 2026-11-19 — plain JSON, not Avro. */
    private static final String REAL = """
            {"level":19.26,"levelKind":"ANCHORED","algorithmVersion":"v2.0","epochId":5,
             "esContract":10252,"valueReferenceMs":1795122000000,
             "nextSessionQuarantinedOpen":true}""";

    @Test
    @DisplayName("the real record reads its level, state and the quarantine warning")
    void theRealRecordReads() {
        BasisRead b = BasisRead.of(REAL);
        assertEquals(19.26, b.level(), 1e-9);
        assertEquals("ANCHORED", b.state());
        assertTrue(b.nextSessionQuarantinedOpen(),
                "the producer's warning is carried, to be printed and not acted on here");
        assertTrue(b.usableForDisplay());
        assertEquals("19.26 ANCHORED", b.cell());
    }

    @Test
    @DisplayName("SPX-equivalent is ES MINUS the level, in that direction")
    void theDirectionIsEsMinusLevel() {
        BasisRead b = BasisRead.of(REAL);
        assertEquals(7167.74, b.spxEquivalentOf(7187.00), 1e-9,
                "the ES VWAP of 7187.00 is 7167.74 in SPX terms at a basis of 19.26");
        assertNull(b.spxEquivalentOf(null), "nothing in, nothing out");
    }

    @Test
    @DisplayName("a basis that is NOT anchored translates nothing at all")
    void onlyAnchoredTranslates() {
        for (String state : new String[] {"QUARANTINED", "PROVISIONAL", "UNKNOWN", "STALE"}) {
            BasisRead b = BasisRead.of(REAL.replace("\"ANCHORED\"", "\"" + state + "\""));
            assertFalse(b.usableForDisplay(), state + " is not anchored");
            assertNull(b.spxEquivalentOf(7187.00),
                    "a caller cannot obtain a translated number under " + state
                            + "; translating under it would put every level a few points out "
                            + "in silence");
            assertEquals("19.26 " + state, b.cell(),
                    "but the level and its state are still printed, so the reader sees why");
        }
    }

    @Test
    @DisplayName("a record with no level is absent, not a zero basis")
    void noLevelIsAbsent() {
        assertNull(BasisRead.of("{\"levelKind\":\"ANCHORED\"}"),
                "a zero basis would translate ES prices unchanged and look plausible");
        for (String bad : new String[] {null, "", "  ", "not json", "[1]",
                "{\"level\":\"abc\"}"}) {
            assertNull(BasisRead.of(bad), "input: " + bad);
        }
    }

    @Test
    @DisplayName("a level given as a decimal string is parsed, not cast")
    void aStringLevelIsParsed() {
        BasisRead b = BasisRead.of("{\"level\":\"19.26\",\"levelKind\":\"ANCHORED\"}");
        assertEquals(19.26, b.level(), 1e-9);
    }
}
