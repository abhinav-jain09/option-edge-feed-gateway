package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>A page must not name the wrong reason, and mine did.</b>
 *
 * <p>The heap guard makes the overnight block absent. The cadence page had the truth appended; the
 * on-demand {@code /now} and {@code /overnight} did not, so {@code Pages.overnight} fell into its
 * pre-existing branch and printed <i>"no reference price yet: the previous cash close is taken from
 * the last print of the SPX index itself and none has been seen"</i> &mdash; while a reference
 * price existed and the heap was the reason. On the one failure the reviewer had asked to be
 * watched for, the page would have handed him the wrong cause.
 *
 * <p>The guard's own figures cannot be forced in a unit test without a second JVM, so what is
 * driven here is the composition: the sentence appended is the SAME sentence on all three pages,
 * it carries the measurement, and a page is left untouched when the heap is fine.
 */
final class OvernightAbsenceReasonTest {

    @Test
    @DisplayName("the appended sentence is the heap's, carries the figures, and says ABSENT")
    void theSentenceIsTheHeaps() {
        String reason = HeapGuard.reason();
        assertTrue(reason.contains("ABSENT"), reason);
        assertTrue(reason.contains("low on heap"), reason);
        assertTrue(reason.contains("used") && reason.contains("MiB"),
                "the figures travel with it, or the owner has nothing to act on: " + reason);
    }

    @Test
    @DisplayName("the wrong sentence and the right one are DIFFERENT, which is the whole defect")
    void theTwoReasonsAreNotInterchangeable() {
        String heap = HeapGuard.reason();
        String referenceAbsence = "no reference price yet: the previous cash close is taken "
                + "from the last print of the SPX index itself and none has been seen";
        assertFalse(heap.contains("reference price"),
                "the heap's reason must not mention the reference price, because the reference "
                        + "price is there: " + heap);
        assertFalse(referenceAbsence.contains("heap"),
                "and the reference absence must not mention the heap -- two distinct facts, two "
                        + "distinct sentences, and printing one for the other is a page lying "
                        + "about why");
    }

    @Test
    @DisplayName("the composition: untouched when the heap is fine, appended when it is not")
    void theCompositionIsAGuardedAppend() {
        // Driven on the method itself rather than on a tautology. My first version of this test
        // asserted `page.equals(page)`, which is no test at all -- the same shape I objected to in
        // §12f's wiring test this morning, written by me an hour later.
        ReadableBoardRenderer renderer = rendererForComposition();
        String page = "now|7143.70 SPX|04:04:54NY\novernight|slots 120|filled 118\n";

        assertEquals(page, renderer.withOvernightAbsence(page, false),
                "a guard that edited a healthy page would be worse than the defect it fixes");

        String degraded = renderer.withOvernightAbsence(page, true);
        assertTrue(degraded.startsWith(page),
                "it APPENDS: the page's own content is never rewritten");
        assertTrue(degraded.contains("OVERNIGHT ROWS ABSENT"), degraded);
        assertTrue(degraded.contains("low on heap"), degraded);
        assertFalse(degraded.contains("reference price"),
                "and it does NOT name the reference price, which is the defect: " + degraded);

        assertNull(renderer.withOvernightAbsence(null, true),
                "a page that was never built is not given a reason for a block it does not have");
    }

    /** A renderer with no board and a fixed clock: the composition needs neither. */
    private static ReadableBoardRenderer rendererForComposition() {
        return new ReadableBoardRenderer(() -> null, () -> 1_795_183_200_000L);
    }
}
