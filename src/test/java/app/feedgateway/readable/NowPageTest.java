package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** <b>C-66</b>: the {@code /now} page, assembled from real shapes. */
class NowPageTest {

    private static final long NOW = 1_795_134_303_000L;
    private static final long CLOSE = 1_795_121_934_000L;

    private static final String ES_VWAP = """
            {"session":{"vwapAnalysis":{
              "vwap":{"value":"7187.0082","state":"INCOMPLETE","reason":"DEADLINE_FINALIZED",
                      "incompleteKind":"LATCHED"},
              "stdDev":{"value":"5.0993","state":"INCOMPLETE","incompleteKind":"LATCHED"},
              "bandUpper1":{"value":"7192.1075","state":"INCOMPLETE","incompleteKind":"LATCHED"},
              "bandLower1":{"value":"7181.9089","state":"INCOMPLETE","incompleteKind":"LATCHED"},
              "bandUpper2":{"value":"7197.2068","state":"INCOMPLETE","incompleteKind":"LATCHED"},
              "bandLower2":{"value":"7176.8096","state":"INCOMPLETE","incompleteKind":"LATCHED"}},
             "vwapRth":{"vwap":{"value":null,"state":"WARMING_UP","reason":"WARMING_UP_WINDOW"}}}}""";

    private static ReadablePage.Price price() {
        return new ReadablePage.Price(NOW - 3_000L, 7163.95, "ES_BASIS_DERIVED", "LIVE_ES_BASIS");
    }

    private static ReadablePage.Overnight overnight() {
        return new ReadablePage.Overnight(7176.24, CLOSE,
                List.of(new ReadablePage.Slot(CLOSE, 7177.94, 7181.74, 7162.99, 7179.99,
                        258939L, "ANCHORED")), 7181.74, 7148.74);
    }

    private static ReadablePage.Board board() {
        return BoardLines.from("{\"spot\":7177.56,\"strikes\":["
                + "{\"strikeCents\":717500,\"board\":{\"netGex\":-8.31E9,"
                + "\"holdOrAccelerate\":\"ACCELERATE\",\"excludedReason\":null}},"
                + "{\"strikeCents\":718000,\"board\":{\"netGex\":1.2E9,"
                + "\"holdOrAccelerate\":\"HOLD\",\"excludedReason\":null}},"
                + "{\"strikeCents\":716000,\"board\":{\"netGex\":-8.61E8,"
                + "\"holdOrAccelerate\":\"ACCELERATE\",\"excludedReason\":null}}]}",
                1_795_124_682_036L);
    }

    private static VwapSideTracker.Result holding() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(NOW - 40 * 60_000L, 7190, 7187, 7192.1075, 7181.9089, "vwapAnalysis");
        t.observe(NOW - 20 * 60_000L, 7180, 7187, 7192.1075, 7181.9089, "vwapAnalysis");
        return t.result();
    }

    private static List<RecentRows.Row> recent() {
        long slot = RecentRows.slotOf(NOW);
        return RecentRows.build(NOW, 3, List.of(
                OvernightGrid.Bar.spx(slot, 7163, 7165, 7162, 7163.95, 300L, "ANCHORED", "30s",
                        180L, 120L, 60L)));
    }

    @Test
    @DisplayName("/now carries every section C-66 lists, in order")
    void everySectionIsPresentInOrder() {
        String page = Pages.now(NOW, price(), overnight(), VwapRead.of(ES_VWAP),
                BasisRead.of("{\"level\":19.26,\"levelKind\":\"ANCHORED\"}"),
                holding(), recent(), board(), List.of(), new VwapHeld(), null, null);
        List<String> lines = page.lines().toList();

        assertTrue(lines.get(0).startsWith("last="), "the first line");

        // C-75 inserts the headline between the first line and the legend: short single-fact
        // lines a summarising reader returns whole, above everything it would refuse to quote.
        int legend = indexOfPrefix(lines, "legend|");
        int headNow = indexOfPrefix(lines, "now|");
        int headAbove = indexOfPrefix(lines, "above|");
        int headStalled = indexOfPrefix(lines, "stalled|");
        assertTrue(headNow == 1, "the headline opens immediately under the first line: " + headNow);
        assertTrue(headAbove > headNow && headAbove < legend,
                "the wall distances are in the headline, above the legend: " + headAbove);
        assertTrue(headStalled > headNow && headStalled < legend,
                "and so is the stalled-input line, which must precede every table: "
                        + headStalled);

        // The detail sections keep C-66's order BELOW the legend. Searched from the legend
        // onwards, because `close|` now appears in the headline too and the first match would
        // otherwise be that one.
        List<String> detail = lines.subList(legend, lines.size());
        int close = indexOfPrefix(detail, "close|");
        int vwap = indexOfPrefix(detail, "vwapes|");
        int hold = indexOfPrefix(detail, "hold|");
        int recent = indexOfPrefix(detail, "recent|");
        int strikes = indexOfPrefix(detail, "strikes|");
        assertTrue(close < vwap && vwap < hold && hold < recent && recent < strikes,
                "close, vwap, hold, recent, strikes — in C-66's order: "
                        + close + "," + vwap + "," + hold + "," + recent + "," + strikes);
        assertTrue(page.length() <= Pages.MAX_CHARS, "within the bound: " + page.length());
    }

    @Test
    @DisplayName("the VWAP is shown in ES as published AND as SPX-equivalent with its basis")
    void bothUnitsWithTheBasisNamed() {
        String page = Pages.now(NOW, price(), overnight(), VwapRead.of(ES_VWAP),
                BasisRead.of("{\"level\":19.26,\"levelKind\":\"ANCHORED\"}"),
                holding(), recent(), board(), List.of(), new VwapHeld(), null, null);
        assertTrue(page.contains("7187.01(LATCHED)"),
                "the ES figure as published, with its state");
        assertTrue(page.contains("7167.75"),
                "and the SPX-equivalent, 7187.0082 minus the 19.26 basis");
        assertTrue(page.contains("basis|state=19.26 ANCHORED"),
                "with the level and state the translation rests on");
    }

    @Test
    @DisplayName("a basis that is not anchored translates NOTHING and says why")
    void anUnanchoredBasisTranslatesNothing() {
        String page = Pages.now(NOW, price(), overnight(), VwapRead.of(ES_VWAP),
                BasisRead.of("{\"level\":19.26,\"levelKind\":\"QUARANTINED\"}"),
                holding(), recent(), board(), List.of(), new VwapHeld(), null, null);
        assertTrue(page.contains("vwapspx|absent|basis 19.26 QUARANTINED"), page);
        assertTrue(page.contains("7187.01(LATCHED)"), "the ES figures remain");
        assertFalse(page.contains("7167.75"), "and nothing is translated under it");
    }

    @Test
    @DisplayName("the holding line names its anchor and when its figures start from")
    void theHoldingLineNamesItsAnchorAndMemory() {
        String page = Pages.now(NOW, price(), overnight(), VwapRead.of(ES_VWAP), null,
                holding(), recent(), board(), List.of(), new VwapHeld(), null, null);
        String line = page.lines().filter(l -> l.startsWith("hold|")).findFirst().orElseThrow();
        assertTrue(line.contains("side=B"), "price is below the line now: " + line);
        assertTrue(line.contains("crosses=1"), line);
        assertTrue(line.contains("anchor=vwapAnalysis"), "which line it used: " + line);
        assertTrue(line.contains("observedfrom="),
                "and from when, because a restart shortens its memory: " + line);
    }

    @Test
    @DisplayName("the five largest strikes are the largest by absolute net gamma")
    void theFiveLargestAreLargest() {
        String page = Pages.now(NOW, price(), overnight(), null, null, null, null, board(), List.of(), new VwapHeld(), null, null);
        List<String> rows = page.lines()
                .filter(l -> l.matches("^7[0-9]{3}\\|.*"))
                .toList();
        assertEquals(5, rows.size(), "five of them");
        assertTrue(rows.get(0).startsWith("7175|-8.31B"), "largest first: " + rows.get(0));
        assertTrue(rows.get(1).startsWith("7180|+1.20B"), rows.get(1));
        assertTrue(rows.get(2).startsWith("7160|-861M"), rows.get(2));
        assertTrue(rows.get(3).contains("norow"),
                "and the ones with no row sort last rather than being dropped");
    }

    @Test
    @DisplayName("every absent section gives one line of reason, and the page still renders")
    void absentSectionsDoNotCostThePage() {
        String page = Pages.now(NOW, price(), null, null, null, null, null, null, List.of(), new VwapHeld(), null, null);
        assertTrue(page.contains("vwap|absent|"), page);
        assertTrue(page.contains("hold|absent|"), page);
        assertTrue(page.contains("recent|absent|"), page);
        assertTrue(page.contains("strikes|absent|"), page);
        assertTrue(page.lines().findFirst().orElseThrow().startsWith("last="),
                "and the price still leads it");
    }

    @Test
    @DisplayName("no label contains a pipe, or the format would eat it")
    void noLabelCarriesASeparator() {
        String page = Pages.now(NOW, price(), overnight(), VwapRead.of(ES_VWAP),
                BasisRead.of("{\"level\":19.26,\"levelKind\":\"ANCHORED\"}"),
                holding(), recent(), board(), List.of(), new VwapHeld(), null, null);
        for (String line : page.lines().toList()) {
            assertFalse(line.contains("||netgamma"),
                    "a pipe inside a label would split into a phantom column: " + line);
            assertFalse(line.contains("  "), "no padding: " + line);
        }
    }

    private static int indexOfPrefix(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
