package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-101 item 2: two bases on the line, and the legend saying which figure used which.</b>
 *
 * <p><b>Why two.</b> The level on {@code spx.basis.state} is what this page translates with. The
 * difference between a bar's own {@code closeCents} and its {@code spxEqCloseCents} is what the
 * PRODUCER translated that bar with. On 2026-11-20 those were 19.26 and about 18.30 &mdash; so a
 * reader comparing a page-computed SPX figure against the bars' own SPX column saw a point of
 * disagreement with nothing on the page to explain it.
 *
 * <p><b>Neither figure is derived here</b> (C-103). The published level is read off the record with
 * the instant it refers to; the bars' figure is the producer's own subtraction read back off a row
 * that carries both prices.
 */
final class C101TwoBasisLineTest {

    /** 2026-11-19 16:00:00 New York, which is the instant the real record's level refers to. */
    private static final long SIXTEEN_NY =
            java.time.Instant.parse("2026-11-19T21:00:00Z").toEpochMilli();

    /** The real record off {@code spx.basis.state}, trimmed to the fields this reads. */
    private static final String REAL_BASIS = "{\"schemaVersion\":1,\"level\":19.26000000000022,"
            + "\"valueReferenceMs\":" + SIXTEEN_NY + ",\"levelKind\":\"ANCHORED\","
            + "\"nextSessionQuarantinedOpen\":true,"
            + "\"trustedAnchor\":{\"level\":19.26000000000022,\"referenceMs\":" + SIXTEEN_NY + "}}";

    private static RecentRows.Row row(long atMs, Double spx, Double futures) {
        return new RecentRows.Row(atMs, spx, spx, spx, 120L, 70L, 50L, 20L, "B", "ANCHORED",
                true, futures, futures, futures);
    }

    @Test
    @DisplayName("C-101: the published level carries the instant it refers to")
    void thePublishedLevelCarriesItsOwnInstant() {
        BasisRead basis = BasisRead.of(REAL_BASIS);
        assertEquals(SIXTEEN_NY, basis.asOfMs());
        assertEquals("19.26 ANCHORED@16:00", basis.cellWithTime(),
                "an anchored basis is a level taken at a moment and held, so 19.26 is the right "
                        + "answer at 16:00 and a guess by the next afternoon");
    }

    @Test
    @DisplayName("C-101: with valueReferenceMs absent the anchor's own instant is used")
    void theAnchorsInstantIsTheFallback() {
        String noValueRef = REAL_BASIS.replace("\"valueReferenceMs\":" + SIXTEEN_NY + ",", "");
        assertEquals(SIXTEEN_NY, BasisRead.of(noValueRef).asOfMs(),
                "a record that states the instant in one place and not the other is the shape "
                        + "this fallback exists for");
    }

    @Test
    @DisplayName("C-101: the bars' basis is the PRODUCER's own subtraction, read back")
    void theBarsBasisIsReadBackNotDerived() {
        // 7168.25 futures against 7149.95 SPX-equivalent: the producer applied 18.30
        Double bars = BasisRead.fromRows(List.of(
                row(1_000L, 7140.00, 7158.10),
                row(2_000L, 7149.95, 7168.25)));
        assertEquals(18.30, bars, 1e-9,
                "the NEWEST row carrying both prices, and the subtraction is the producer's own");
        assertNull(BasisRead.fromRows(List.of(row(1_000L, 7140.00, null))),
                "a row with one price cannot say what basis was applied to it");
        assertNull(BasisRead.fromRows(List.of(row(1_000L, null, 7158.10))));
    }

    @Test
    @DisplayName("C-101: the line carries both, the drift, and the quarantine warning")
    void theLineCarriesBothAndTheDrift() {
        String line = Pages.basisLine(BasisRead.of(REAL_BASIS),
                List.of(row(2_000L, 7149.95, 7168.25)));
        assertEquals("basis|state=19.26 ANCHORED@16:00|bars=18.30|drift=0.96"
                + "|nextopen=QUARANTINED\n", line,
                "the drift is printed because a reader should not have to subtract two numbers "
                        + "on the same line to get the one that matters");
    }

    @Test
    @DisplayName("C-101: the line stands even where the published basis is absent or unusable")
    void theLineStandsWithoutAUsablePublishedBasis() {
        String noBasis = Pages.basisLine(null, List.of(row(2_000L, 7149.95, 7168.25)));
        assertEquals("basis|state=absent|bars=18.30\n", noBasis,
                "THE DEFECT: this line used to be printed only inside the anchored branch, so "
                        + "both readings vanished in exactly the cases where the published one "
                        + "had stopped being usable and the bars' one was all there was");

        String quarantined = Pages.basisLine(
                BasisRead.of(REAL_BASIS.replace("ANCHORED", "QUARANTINED")),
                List.of(row(2_000L, 7149.95, 7168.25)));
        assertTrue(quarantined.startsWith("basis|state=19.26 QUARANTINED@16:00|bars=18.30"),
                quarantined);

        assertEquals("basis|state=19.26 ANCHORED@16:00|bars=absent|nextopen=QUARANTINED\n",
                Pages.basisLine(BasisRead.of(REAL_BASIS), List.of()),
                "and with no row carrying both prices, the bars' cell says absent rather than "
                        + "leaving the reader to think the two agree");
    }

    @Test
    @DisplayName("C-101: the PAGE prints basis| even when the published basis is not anchored")
    void thePageItselfPrintsTheLineOutsideTheAnchoredBranch() {
        // Driven through Pages.now rather than Pages.basisLine, because the defect was the line's
        // PLACE on the page and not its contents: a test that calls basisLine directly passes
        // happily with the call sitting back inside the anchored branch, which is exactly what
        // the mutation pass caught here.
        List<RecentRows.Row> recent = List.of(row(2_000L, 7149.95, 7168.25));
        BasisRead quarantined = BasisRead.of(REAL_BASIS.replace("ANCHORED", "QUARANTINED"));

        // vwap null too: the basis line must not depend on the indicators service having
        // published a vwap block either, which is the same defect one level further out.
        String page = Pages.now(2_000L, null, null, null, quarantined, null,
                recent, null, null, null, null, null);
        String basisLine = page.lines().filter(l -> l.startsWith("basis|")).findFirst()
                .orElse("(no basis line on the page)");
        assertTrue(basisLine.startsWith("basis|state=19.26 QUARANTINED@16:00|bars=18.30"),
                "the two readings a reader most needs to compare must be on the page in exactly "
                        + "the case where the published one has stopped being usable: "
                        + basisLine);
        assertTrue(page.contains("vwap|absent|"),
                "with no vwap record the vwap line says so, and the basis line is there anyway: "
                        + page);
    }

    @Test
    @DisplayName("C-101: the legend says which basis each page-computed figure used")
    void theLegendNamesWhichBasisEachFigureUsed() {
        String legend = Pages.LEGEND;
        assertTrue(legend.contains("using basis|state (the level on spx.basis.state)"), legend);
        assertTrue(legend.contains("the bars' own px column was translated by the producer "
                + "using basis|bars"), legend);
    }
}
