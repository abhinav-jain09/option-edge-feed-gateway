package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ES-FOOTPRINT-GATEWAY-DESIGN.md G-R4/G-R5/G-R6/G-R7/G-R8 — the coordinator, without a broker. */
class FootprintViewsTest {

    private static FootprintViews views() { return new FootprintViews(new ObjectMapper(), 262_144, 128L << 20, 12_000, 16L << 20, 20_000); }

    static String bar(String session, String tf, long start) { return bar(session, tf, start, "x"); }
    static String bar(String session, String tf, long start, String tag) {
        return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"" + tf + "\",\"sessionDate\":\"" + session
                + "\",\"observations\":{\"barStartMs\":" + start + "},\"tag\":\"" + tag + "\"}";
    }
    static String outcome(String session, String tf, long resolved, String identity) { return outcome(session, tf, resolved, identity, "x"); }
    static String outcome(String session, String tf, long resolved, String identity, String tag) {
        return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"" + tf + "\",\"sessionDate\":\"" + session
                + "\",\"identity\":\"" + identity + "\",\"resolvedAtBarStartMs\":" + resolved + ",\"outcome\":\"HELD\",\"tag\":\"" + tag + "\"}";
    }

    // ---- G-R4 admission / shape ------------------------------------------------------------------

    @Test void shapeDropsNeverMutate() {
        FootprintViews v = views();
        for (String bad : new String[]{"not json", "{\"unrelated\":true}",
                bar("z", "1m", 1), bar("2026-8-14", "1m", 1), bar("20260814", "1m", 1), bar("2026-02-30", "1m", 1),
                bar("2026-08-14", "2m", 1), "{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{}}",
                "{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":\"1\"}}",
                "{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":-1}}",
                "{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":253402300800000}}",
                "{\"timeframe\":\"1m\",\"observations\":{\"barStartMs\":1}}"}) {
            assertEquals(FootprintViews.Reason.SHAPE, v.admitBar(bad).reason(), bad);
        }
        assertNull(v.sessionDate(), "a shape drop never anchors the session");
        assertEquals(0, v.barsInView());
        assertEquals(FootprintViews.Reason.SHAPE, v.admitOutcome(outcome("2026-08-14", "1m", 5, "bad identity!")).reason());
        assertEquals(FootprintViews.Reason.SHAPE, v.admitOutcome(outcome("2026-08-14", "1m", 5, "a".repeat(129))).reason());
        assertEquals(FootprintViews.Reason.SHAPE, v.admitOutcome("{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"identity\":\"x\"}").reason());
        assertEquals(0, v.outcomesInView());
    }

    @Test void oversizeIsDroppedEntirelyAndNeverRollsTheSession() {
        FootprintViews v = new FootprintViews(new ObjectMapper(), 200, 1 << 20, 100, 1 << 20, 100);
        String big = bar("2026-08-14", "1m", 1, "y".repeat(300));
        assertEquals(FootprintViews.Reason.OVERSIZE, v.admitBar(big).reason());
        assertEquals(FootprintViews.Reason.OVERSIZE, v.admitOutcome(outcome("2026-08-14", "1m", 1, "ES|1m|1|BUYERS|LEVEL|1|1", "y".repeat(300))).reason());
        assertNull(v.sessionDate());
        String exact = bar("2026-08-14", "1m", 1, "y".repeat(200 - bar("2026-08-14", "1m", 1, "").length()));
        assertEquals(200, FootprintViews.utf8Length(exact));
        assertEquals(FootprintViews.Reason.ADMITTED, v.admitBar(exact).reason(), "at the ceiling is legal");
    }

    @Test void lastWritePerKeyWinsAndKeysAreFixedWidth() {
        FootprintViews v = views();
        assertEquals(FootprintViews.Reason.ADMITTED, v.admitBar(bar("2026-08-14", "30s", 1000, "first")).reason());
        assertEquals(FootprintViews.Reason.ADMITTED, v.admitBar(bar("2026-08-14", "30s", 1000, "second")).reason());
        assertEquals(1, v.barsInView());
        assertTrue(v.barsPage("30s", Long.MAX_VALUE, -1, 10, "").records().get(0).contains("\"tag\":\"second\""));
        assertEquals("30s|0000000000000001000", FootprintViews.barKey("30s", 1000));
        assertEquals("1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000",
                FootprintViews.outcomeKey("1m", 5, "ES.v.0|1m|1|BUYERS|LEVEL|650000|650000"));
    }

    // ---- G-R4 rollover: one coordinator, both views, strict dates ------------------------------

    @Test void aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts() {
        FootprintViews v = views();
        v.admitBar(bar("2026-08-14", "1m", 1)); v.admitOutcome(outcome("2026-08-14", "1m", 1, "a"));
        FootprintViews.Admission roll = v.admitBar(bar("2026-08-17", "5m", 9));
        assertTrue(roll.rolledOver()); assertEquals("2026-08-17", v.sessionDate());
        assertEquals(1, v.barsInView()); assertEquals(0, v.outcomesInView(), "the outcomes view rolled with the bars view");
        assertEquals(1, v.rollovers());
        assertEquals(FootprintViews.Reason.STALE_SESSION, v.admitOutcome(outcome("2026-08-14", "1m", 2, "b")).reason(), "late record from a dead session");
        assertEquals(FootprintViews.Reason.STALE_SESSION, v.admitBar(bar("2026-08-14", "1m", 2)).reason());
        assertFalse(v.admitOutcome(outcome("2026-08-17", "1m", 3, "c")).rolledOver());
        assertEquals(1, v.outcomesInView());
    }

    @Test void outOfOrderBootstrapAcrossTopicsInBothOrders() {
        // bar of N+1 before outcomes of N+1, then an outcome of N: dropped; and the mirror order
        FootprintViews v = views();
        v.admitOutcome(outcome("2026-08-14", "1m", 1, "n")); v.admitBar(bar("2026-08-14", "1m", 1));
        v.admitBar(bar("2026-08-15", "1m", 2));                      // N+1 arrives on the bars topic first
        assertEquals(FootprintViews.Reason.STALE_SESSION, v.admitOutcome(outcome("2026-08-14", "1m", 1, "late")).reason());
        assertEquals(FootprintViews.Reason.ADMITTED, v.admitOutcome(outcome("2026-08-15", "1m", 2, "n1")).reason());
        FootprintViews w = views();
        w.admitBar(bar("2026-08-14", "1m", 1));
        w.admitOutcome(outcome("2026-08-15", "1m", 2, "n1"));         // N+1 arrives on the outcomes topic first
        assertEquals("2026-08-15", w.sessionDate()); assertEquals(0, w.barsInView());
        assertEquals(FootprintViews.Reason.STALE_SESSION, w.admitBar(bar("2026-08-14", "1m", 3)).reason());
    }

    @Test void yearAndMonthBoundariesOrderCorrectly() {
        FootprintViews v = views();
        v.admitBar(bar("2026-12-31", "1m", 1));
        assertTrue(v.admitBar(bar("2027-01-01", "1m", 2)).rolledOver());
        assertEquals(FootprintViews.Reason.STALE_SESSION, v.admitBar(bar("2026-12-31", "1m", 3)).reason());
        assertNull(FootprintViews.parseCanonicalDate("2026-02-30"));
        assertNull(FootprintViews.parseCanonicalDate("2026-2-3"));
        assertNotNull(FootprintViews.parseCanonicalDate("2024-02-29"));
        assertNull(FootprintViews.parseCanonicalDate("2023-02-29"));
    }

    // ---- G-R6 hello --------------------------------------------------------------------------------

    @Test void helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks() {
        FootprintViews v = views();
        assertEquals("{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}", v.helloField());
        v.admitBar(bar("2026-08-14", "30s", 1000)); v.admitBar(bar("2026-08-14", "30s", 2000)); v.admitBar(bar("2026-08-14", "1m", 5));
        v.admitOutcome(outcome("2026-08-14", "5m", 77, "ES.v.0|5m|1|SELLERS|STACK|1|2")); v.admitOutcome(outcome("2026-08-14", "5m", 70, "z"));
        assertEquals("{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}", v.helloField());
    }

    // ---- G-R7 pages ------------------------------------------------------------------------------

    @Test void barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic() {
        FootprintViews v = views();
        for (long s = 1000; s <= 5000; s += 1000) v.admitBar(bar("2026-08-14", "30s", s));
        v.admitBar(bar("2026-08-14", "1m", 1000));
        FootprintViews.BarsPage first = v.barsPage("30s", 5000, -1, 2, "");
        assertEquals(2, first.records().size()); assertTrue(first.records().get(0).contains("\"barStartMs\":1000"));
        assertEquals(2000L, first.nextCursor());
        FootprintViews.BarsPage second = v.barsPage("30s", 5000, first.nextCursor(), 10, "");
        assertEquals(3, second.records().size()); assertTrue(second.records().get(0).contains("\"barStartMs\":3000"));
        assertNull(second.nextCursor());
        assertEquals(0, v.barsPage("30s", 999, -1, 10, "").records().size());
        assertEquals(0, v.barsPage("15m", Long.MAX_VALUE, -1, 10, "").records().size());
        FootprintViews.BarsPage mismatch = v.barsPage("30s", Long.MAX_VALUE, -1, 10, "2026-08-13");
        assertTrue(mismatch.sessionMismatch()); assertEquals("2026-08-14", mismatch.sessionDate()); assertEquals(0, mismatch.records().size());
        assertFalse(v.barsPage("30s", Long.MAX_VALUE, -1, 10, "2026-08-14").sessionMismatch());
    }

    @Test void outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor() {
        FootprintViews v = views();
        v.admitOutcome(outcome("2026-08-14", "1m", 10, "ES.v.0|1m|1|SELLERS|LEVEL|2|2"));
        v.admitOutcome(outcome("2026-08-14", "1m", 10, "ES.v.0|1m|1|BUYERS|LEVEL|1|1"));   // same instant, identity order
        v.admitOutcome(outcome("2026-08-14", "1m", 9, "b"));
        v.admitOutcome(outcome("2026-08-14", "1m", 100, "a"));                              // more digits, numerically later
        v.admitOutcome(outcome("2026-08-14", "5m", 1, "other-tf"));
        FootprintViews.OutcomesPage p1 = v.outcomesPage("1m", Long.MAX_VALUE, "", 2, "");
        assertEquals(2, p1.records().size());
        assertTrue(p1.records().get(0).contains("\"identity\":\"b\""));
        assertTrue(p1.records().get(1).contains("BUYERS"), "identity order inside one timestamp");
        assertEquals("1m|0000000000000000010|ES.v.0|1m|1|BUYERS|LEVEL|1|1", p1.nextCursor());
        assertTrue(FootprintViews.validOutcomeCursor("1m", p1.nextCursor()));
        FootprintViews.OutcomesPage p2 = v.outcomesPage("1m", Long.MAX_VALUE, p1.nextCursor(), 10, "");
        assertEquals(2, p2.records().size()); assertTrue(p2.records().get(0).contains("SELLERS")); assertTrue(p2.records().get(1).contains("\"identity\":\"a\""));
        assertNull(p2.nextCursor());
        assertEquals(3, v.outcomesPage("1m", 10, "", 10, "").records().size(), "toMs is inclusive on resolvedAtBarStartMs");
        assertEquals(0, v.outcomesPage("1m", Long.MAX_VALUE, "1m|9999999999999999999|zz", 10, "").records().size(), "a nonexistent cursor is a legal lower bound");
        assertFalse(FootprintViews.validOutcomeCursor("1m", "5m|0000000000000000010|x"), "wrong timeframe");
        assertFalse(FootprintViews.validOutcomeCursor("1m", "1m|10|x"), "not fixed width");
        assertFalse(FootprintViews.validOutcomeCursor("1m", "1m|0000000000000000010|bad id"), "identity alphabet");
        assertFalse(FootprintViews.validOutcomeCursor("1m", null));
    }

    // ---- G-R8 budgets ----------------------------------------------------------------------------

    @Test void countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin() {
        FootprintViews v = new FootprintViews(new ObjectMapper(), 262_144, 128L << 20, 4, 16L << 20, 20_000);
        v.admitBar(bar("2026-08-14", "30s", 1)); v.admitBar(bar("2026-08-14", "30s", 2)); v.admitBar(bar("2026-08-14", "30s", 3));
        v.admitBar(bar("2026-08-14", "1m", 1));
        FootprintViews.Admission a = v.admitBar(bar("2026-08-14", "1m", 2));       // 5 > 4: the round starts at the lexically first timeframe ("1m") and stops as soon as it fits
        assertEquals(1, a.evicted()); assertEquals(4, v.barsInView()); assertEquals(1, v.barsEvictions());
        assertEquals(List.of(1L, 2L, 3L), v.barsPage("30s", Long.MAX_VALUE, -1, 10, "").records().stream().map(r -> Long.parseLong(r.replaceAll(".*barStartMs\":(\\d+).*", "$1"))).toList());
        assertEquals(List.of(2L), v.barsPage("1m", Long.MAX_VALUE, -1, 10, "").records().stream().map(r -> Long.parseLong(r.replaceAll(".*barStartMs\":(\\d+).*", "$1"))).toList(), "1m lost its oldest");
        v.admitBar(bar("2026-08-14", "1m", 3)); v.admitBar(bar("2026-08-14", "1m", 4));   // 6 > 4: two rounds, each timeframe loses one per round
        assertEquals(4, v.barsInView()); assertEquals(3, v.barsEvictions());
        assertEquals(2, v.barsPage("30s", Long.MAX_VALUE, -1, 10, "").records().size(), "no timeframe is starved: 30s kept two");
        assertEquals(2, v.barsPage("1m", Long.MAX_VALUE, -1, 10, "").records().size());
    }

    @Test void byteBudgetEvictsUntilItFitsAndBytesAreExact() {
        int one = FootprintViews.utf8Length(bar("2026-08-14", "1m", 1));
        FootprintViews v = new FootprintViews(new ObjectMapper(), 262_144, one * 2L + 1, 100, 16L << 20, 100);
        v.admitBar(bar("2026-08-14", "1m", 1)); v.admitBar(bar("2026-08-14", "1m", 2));
        assertEquals(2L * one, v.barsBytes());
        assertEquals(1, v.admitBar(bar("2026-08-14", "1m", 3)).evicted());
        assertEquals(2, v.barsInView()); assertEquals(2L * one, v.barsBytes());
        v.admitBar(bar("2026-08-14", "1m", 3, "xx"));                                // overwrite: bytes track the replacement
        assertEquals(2L * one + 1, v.barsBytes());
        FootprintViews o = new FootprintViews(new ObjectMapper(), 262_144, 1 << 20, 100, 1 << 20, 1);
        o.admitOutcome(outcome("2026-08-14", "1m", 1, "a")); assertEquals(1, o.admitOutcome(outcome("2026-08-14", "1m", 2, "b")).evicted());
        assertEquals(1, o.outcomesInView()); assertEquals(1, o.outcomesEvictions());
    }
}
