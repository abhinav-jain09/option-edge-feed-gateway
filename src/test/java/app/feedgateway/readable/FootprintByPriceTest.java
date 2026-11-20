package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-66's /footprint</b>. The fixture is the real record from
 * {@code options.spx.gamma-zone.profile} on 2026-11-20 — plain JSON, and note the column order it
 * publishes: {@code priceCents, sellerInit, buyerInit, unknown}. <b>Seller before buyer.</b>
 */
class FootprintByPriceTest {

    private static final String REAL = """
            {"schema":1,"symbol":"ES.v.0","timeframe":"session","sessionDate":"2026-11-20",
             "gridCents":25,"levelCount":4,"asOfMs":1795134000000,
             "columns":["priceCents","sellerInit","buyerInit","unknown"],
             "levels":[[716025,5,0,0],[716050,10,1,0],[716075,21,15,0],[716100,35,22,2]]}""";

    private static final BasisRead ANCHORED =
            BasisRead.of("{\"level\":19.26,\"levelKind\":\"ANCHORED\"}");

    @Test
    @DisplayName("buyers and sellers follow the NAMES, not the positions")
    void theColumnsAreReadByName() {
        // In the real record sellerInit comes FIRST. Reading the second number as "buyers" because
        // buyers are named first in every sentence about a footprint would swap every level on the
        // page and nothing would look wrong.
        List<FootprintByPrice.Level> ls = FootprintByPrice.levels(REAL, ANCHORED);
        assertEquals(4, ls.size());
        FootprintByPrice.Level first = ls.get(0);
        assertEquals(5L, first.sellers(), "the 5 is sellerInit, which is the FIRST volume column");
        assertEquals(0L, first.buyers(), "and buyers is 0, the second");
        FootprintByPrice.Level third = ls.get(2);
        assertEquals(21L, third.sellers());
        assertEquals(15L, third.buyers());
    }

    @Test
    @DisplayName("a record that lists its columns in a DIFFERENT order still reads correctly")
    void theOrderMayChangeAndTheReadFollows() {
        String swapped = REAL
                .replace("[\"priceCents\",\"sellerInit\",\"buyerInit\",\"unknown\"]",
                         "[\"priceCents\",\"buyerInit\",\"sellerInit\",\"unknown\"]");
        List<FootprintByPrice.Level> ls = FootprintByPrice.levels(swapped, ANCHORED);
        assertEquals(5L, ls.get(0).buyers(),
                "with buyerInit declared second, the 5 is now BUYERS — the mapping is the "
                        + "record's, not ours");
        assertEquals(0L, ls.get(0).sellers());
    }

    @Test
    @DisplayName("a record whose columns we do not recognise is REFUSED, not read positionally")
    void anUnrecognisedRecordIsRefused() {
        String renamed = REAL.replace("\"buyerInit\"", "\"buyInitiated\"");
        assertTrue(FootprintByPrice.levels(renamed, ANCHORED).isEmpty(),
                "the one thing worse than no footprint is one with buyers and sellers the wrong "
                        + "way round");
        assertTrue(FootprintByPrice.levels("{\"levels\":[[1,2,3]]}", ANCHORED).isEmpty(),
                "no columns block at all");
    }

    @Test
    @DisplayName("the prices are ES cents and the SPX column is the platform's own basis applied")
    void pricesAreEsAndTranslatedForDisplay() {
        FootprintByPrice.Level l = FootprintByPrice.levels(REAL, ANCHORED).get(0);
        assertEquals(7160.25, l.esPrice(), 1e-9, "716025 is ES 7160.25");
        assertEquals(7140.99, l.spxPrice(), 1e-9, "minus the 19.26 basis");
        assertTrue(FootprintByPrice.line(l).startsWith("7140.99|7160.25|"),
                "SPX leads, ES beside it: " + FootprintByPrice.line(l));
    }

    @Test
    @DisplayName("without a usable basis the ES price stands and the SPX cell is empty")
    void noBasisMeansNoTranslationAndNoDroppedLevel() {
        BasisRead quarantined =
                BasisRead.of("{\"level\":19.26,\"levelKind\":\"QUARANTINED\"}");
        List<FootprintByPrice.Level> ls = FootprintByPrice.levels(REAL, quarantined);
        assertEquals(4, ls.size(), "every level is still there");
        assertNull(ls.get(0).spxPrice(), "but nothing is translated under a quarantined basis");
        assertTrue(FootprintByPrice.line(ls.get(0)).startsWith("|7160.25|"),
                "so the SPX cell is empty and the ES price stands: "
                        + FootprintByPrice.line(ls.get(0)));
    }

    @Test
    @DisplayName("the window is judged in SPX terms, and without a basis no level is excluded")
    void theWindowNeedsTheBasisToBeJudged() {
        List<FootprintByPrice.Level> anchored = FootprintByPrice.levels(REAL, ANCHORED);
        // The grid is 25 CENTS, so these four levels span only 0.75 points: 7140.99, 7141.24,
        // 7141.49, 7141.74. A one-point radius therefore holds all four, and a tenth of a point
        // holds exactly one. My first assertion here said a 1-point radius held one level, which
        // was my arithmetic rather than the code's.
        assertEquals(4, FootprintByPrice.near(anchored, 7141.0, 1.0, ANCHORED).size());
        assertEquals(1, FootprintByPrice.near(anchored, 7141.0, 0.1, ANCHORED).size());

        BasisRead bad = BasisRead.of("{\"level\":19.26,\"levelKind\":\"QUARANTINED\"}");
        List<FootprintByPrice.Level> untranslated = FootprintByPrice.levels(REAL, bad);
        assertEquals(4, FootprintByPrice.near(untranslated, 7141.0, 0.1, bad).size(),
                "comparing an ES price against an SPX spot would shift the window by the basis, "
                        + "so every level is kept and paged instead");
    }

    @Test
    @DisplayName("unknown-side volume is counted in the total but printed only when it exists")
    void unknownIsCountedAndShownOnlyWhenPresent() {
        List<FootprintByPrice.Level> ls = FootprintByPrice.levels(REAL, ANCHORED);
        assertEquals(0L, ls.get(0).unknown());
        assertEquals(5L, ls.get(0).total(), "5 sellers, no buyers, no unknown");
        assertTrue(FootprintByPrice.line(ls.get(0)).endsWith("|0|5||5"),
                "buy then sell, so 0 buyers and 5 sellers, and an empty unknown cell. (I wrote "
                        + "this assertion the other way round first -- the very swap this class "
                        + "exists to prevent: " + FootprintByPrice.line(ls.get(0)) + ")");
        FootprintByPrice.Level withUnknown = ls.get(3);
        assertEquals(2L, withUnknown.unknown());
        assertEquals(59L, withUnknown.total(), "22 buyers + 35 sellers + 2 unknown");
        assertTrue(FootprintByPrice.line(withUnknown).endsWith("|22|35|2|59"),
                FootprintByPrice.line(withUnknown));
    }

    @Test
    @DisplayName("every level pages rather than being thinned, however many there are")
    void nothingIsMergedToFit() {
        StringBuilder levels = new StringBuilder();
        for (int i = 0; i < 3_000; i++) {
            if (i > 0) {
                levels.append(',');
            }
            levels.append("[").append(700000 + i * 25).append(",5,7,0]");
        }
        String big = REAL.replaceAll("\"levels\":\\[.*\\]\\}",
                "\"levels\":[" + levels + "]}");
        List<FootprintByPrice.Level> ls = FootprintByPrice.levels(big, ANCHORED);
        assertEquals(3_000, ls.size(), "all of them read");

        List<String> rows = ls.stream().map(FootprintByPrice::line).toList();
        List<String> pages = Pages.paged(
                List.of("last=...", Pages.LEGEND, "footprint|spot=7141.00",
                        FootprintByPrice.header()), rows);
        assertTrue(pages.size() > 1, "paged: " + pages.size());
        long printed = pages.stream()
                .flatMap(String::lines)
                .filter(l -> l.matches("^[0-9.]*\\|[0-9.]+\\|7\\|5\\|\\|12$"))
                .count();
        assertEquals(3_000, printed, "every level present exactly once across the pages");
    }

    @Test
    @DisplayName("garbage is an empty list, never a partial read")
    void garbageIsEmpty() {
        for (String bad : new String[] {null, "", "  ", "not json", "[1,2]", "{}"}) {
            assertTrue(FootprintByPrice.levels(bad, ANCHORED).isEmpty(), "input: " + bad);
        }
    }
}
