package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-83</b>: the {@code hold|} line carries the two figures the side was decided from.
 *
 * <p>The reviewer computed the side himself from the page's {@code now|} line plus the published
 * basis and got the opposite letter. He was reading the page correctly; the page was at fault.
 * {@code now|} comes from {@code underlying.spx.price} (`ES_BASIS_DERIVED`), while the side is
 * decided from the indicators record's <b>own</b> price against <b>that same record's</b> own VWAP
 * — one record, one instant, which is C-71's rule and stays. The page was handing a reader two of
 * the three numbers, from two different sources, and leaving out the pair actually used.
 *
 * <p>So both are published, in ES, directly after the side.
 */
class HoldLineFiguresTest {

    private static final long NOW = java.time.Instant.parse("2026-11-20T07:19:00Z").toEpochMilli();

    @Test
    @DisplayName("px and vwap are the figures the comparison used, right after the side")
    void theHoldLineCarriesTheComparisonsOwnFigures() {
        // The measured reading from Dev at 07:18:46Z: price 7162.25 against vwap 7161.2319, so
        // +1.02 above and the letter is A.
        VwapSideTracker t = new VwapSideTracker();
        t.observe(NOW - 60_000L, 7162.25, 7161.2319, null, null, "vwapAnalysis");
        t.observe(NOW, 7162.25, 7161.2319, null, null, "vwapAnalysis");

        String page = Pages.now(NOW, null, null, null, null, t.result(), List.of(), null,
                List.of(), new VwapHeld(), null, null);
        String hold = page.lines().filter(l -> l.startsWith("hold|")).findFirst().orElse("");

        assertTrue(hold.startsWith("hold|side=A|px=7162.25|vwap=7161.23|"),
                "the side, then the two figures it was decided from: " + hold);
        assertTrue(hold.contains("anchor=vwapAnalysis"), hold);
    }

    @Test
    @DisplayName("a reader can check the letter from the page alone, which was the whole point")
    void theLetterIsCheckableFromThePage() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(NOW - 60_000L, 7150.00, 7161.2319, null, null, "vwapAnalysis");
        t.observe(NOW, 7150.00, 7161.2319, null, null, "vwapAnalysis");

        String hold = Pages.now(NOW, null, null, null, null, t.result(), List.of(), null,
                        List.of(), new VwapHeld(), null, null)
                .lines().filter(l -> l.startsWith("hold|")).findFirst().orElse("");

        assertTrue(hold.startsWith("hold|side=B|px=7150.00|vwap=7161.23|"),
                "price 11.23 below the line, and the page shows both numbers: " + hold);
        assertFalse(hold.contains("px=|"), "never an empty figure beside a real side: " + hold);
    }

    @Test
    @DisplayName("the headline vwap| line is unchanged, as C-83 requires")
    void theHeadlineIsUnchanged() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(NOW - 60_000L, 7162.25, 7161.2319, null, null, "vwapAnalysis");
        t.observe(NOW, 7162.25, 7161.2319, null, null, "vwapAnalysis");

        String head = Pages.now(NOW, null, null, null, null, t.result(), List.of(), null,
                        List.of(), new VwapHeld(), null, null)
                .lines().filter(l -> l.startsWith("vwap|")).findFirst().orElse("");

        assertTrue(head.startsWith("vwap|A|held "), "still side and held time only: " + head);
        assertFalse(head.contains("px="), "the figures go on hold|, not here: " + head);
        assertTrue(head.length() <= 64, "C-75's budget: " + head);
    }
}
