package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>The guard that turns 2026-11-20's 500 into an absence.</b>
 *
 * <p>The figures cannot be forced in a unit test without a second JVM, so what is asserted here is
 * what can be: the threshold is a declared figure and not a literal in a branch, the reason the
 * page prints carries the measurement, and a healthy JVM is not degraded. The behaviour on a full
 * heap is asserted in {@code ReadableHeapAbsenceTest}, which drives the renderer with the guard's
 * own decision supplied.
 */
final class HeapGuardTest {

    @Test
    @DisplayName("a healthy test JVM is NOT degraded: the guard must not cost a page it need not")
    void aHealthyJvmIsNotDegraded() {
        assertFalse(HeapGuard.low(),
                "the suite runs with plenty of heap, and a guard that fired here would blank the "
                        + "overnight rows on every healthy gateway too: " + HeapGuard.describe());
    }

    @Test
    @DisplayName("the threshold is a DECLARED figure, not a literal inside a branch")
    void theThresholdIsDeclared() {
        assertTrue(HeapGuard.FREE_SHARE_MIN > 0.0 && HeapGuard.FREE_SHARE_MIN < 1.0,
                "a share of the heap, so it follows -Xmx rather than pinning a byte count that "
                        + "would mean something different on every deployment");
    }

    @Test
    @DisplayName("the reason the PAGE prints carries the measurement, so a reader need not guess")
    void theReasonCarriesTheFigures() {
        String reason = HeapGuard.reason();
        assertTrue(reason.contains("ABSENT"), reason);
        assertTrue(reason.contains("used") && reason.contains("MiB"),
                "the figures are in the sentence: a page that says \"low on heap\" and no numbers "
                        + "tells the owner nothing he can act on -- " + reason);
        assertTrue(reason.contains("skipped rather than allowed to fail the page"),
                "and it says WHY the rows are missing, which is the rule being obeyed: " + reason);
    }

    @Test
    @DisplayName("the instrument is one greppable line, with the bar counts beside the heap")
    void theInstrumentIsOneLine() {
        String line = HeapGuard.line(1_234, 56);
        assertTrue(line.startsWith("readable heap "), line);
        assertTrue(line.contains("free="), line);
        assertTrue(line.contains("bars night=1234 recent=56"),
                "the held counts travel with the heap figure, because \"the heap grew\" and "
                        + "\"the night's bars grew\" are different findings: " + line);
        assertFalse(line.contains("LOW"), "and a healthy line is not marked LOW: " + line);
    }
}
