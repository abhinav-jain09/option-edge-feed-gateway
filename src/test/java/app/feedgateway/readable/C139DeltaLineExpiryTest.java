package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-139: the delta lines name their expiry, and never stand a strike with no record in front of
 * one that has a figure.</b>
 *
 * <p>Both were seen on the live page rather than reasoned about. At 15:07 New York, with spot at
 * 6946.28, {@code /now} carried {@code delta|below|6945|cd=norec|pd=norec}: 6945 is the nearest
 * strike below price and the gamma topic carries no record for it, so the owner's headline pair
 * held one figure and one blank where a readable strike sat five points further away. And the lines
 * said nowhere which expiry they were — the topic carries today's 0DTE and the next session's
 * together, which was proven today to be a real hazard rather than a hypothetical one.
 */
final class C139DeltaLineExpiryTest {

    private static final String REAL = "{\"schemaVersion\":10,"
            + "\"messageType\":\"GEX_STRIKE_SNAPSHOT\",\"source\":\"DATABENTO\","
            + "\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"strike\":7275.0,"
            + "\"callDelta\":0.05545327388772792,\"putDelta\":-0.9702893751050274,"
            + "\"callInvalidReason\":\"\",\"putInvalidReason\":\"\","
            + "\"updatedAtMs\":1795188021183,\"kafkaPublishedAtMs\":1795188021183}";

    private static String withStrike(double strike, double call, double put) {
        return REAL.replace("\"strike\":7275.0", "\"strike\":" + strike)
                .replace("0.05545327388772792", String.valueOf(call))
                .replace("-0.9702893751050274", String.valueOf(put));
    }

    /** Five strikes five points apart, so a gap in the records can be stepped over. */
    private static String mapJson() {
        return "{\"spot\":7275.0,\"expiry\":\"20261120\",\"strikes\":["
                + "{\"strikeCents\":726500,\"board\":{\"netGex\":1.0e9,"
                + "\"holdOrAccelerate\":\"HOLD\"}},"
                + "{\"strikeCents\":727000,\"board\":{\"netGex\":1.0e9,"
                + "\"holdOrAccelerate\":\"HOLD\"}},"
                + "{\"strikeCents\":727500,\"board\":{\"netGex\":-2.0e9,"
                + "\"holdOrAccelerate\":\"ACCELERATE\"}},"
                + "{\"strikeCents\":728000,\"board\":{\"netGex\":3.0e8,"
                + "\"holdOrAccelerate\":\"NEUTRAL\"}},"
                + "{\"strikeCents\":728500,\"board\":{\"netGex\":3.0e8,"
                + "\"holdOrAccelerate\":\"NEUTRAL\"}}]}";
    }

    private static final long NOW = 1_795_188_021_183L;

    private static ReadablePage.Price priceAt(double spx) {
        return new ReadablePage.Price(NOW, spx, "NATIVE_SPX_INDEX", "LIVE_INDEX");
    }

    @Test
    @DisplayName("the nearest strike below with a FIGURE, stepping over the one with no record")
    void theNearestStrikeBelowWithAFigure() {
        // the live shape: price just above a strike the topic does not carry, with a record on the
        // strike beyond it. Before C-139 the line read `cd=norec|pd=norec` and the owner had
        // nothing to read on the side price was falling towards.
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(withStrike(7265.0, 0.9100, -0.0899));     // a real figure, 10 points below
        source.observe(REAL);                                    // 7275, above price
        ReadablePage.Board board = BoardLines.from(mapJson(), NOW, source, NOW);

        String lines = Pages.deltaLines(priceAt(7271.00), board, null);

        assertTrue(lines.contains("delta|below|7265|cd=0.910|pd=-0.090"),
                "the nearest strike below that HAS a figure: " + lines);
        assertFalse(lines.contains("delta|below|7270|"),
                "7270 is nearer and carries no record, so it is not the headline: " + lines);
        assertTrue(lines.contains("delta|above|7275|cd=0.055|pd=-0.970"),
                "and the side with a record on the nearest strike is unchanged: " + lines);
    }

    @Test
    @DisplayName("with NO figure anywhere on a side, the nearest REASON is printed, not nothing")
    void withNoFigureAnywhereTheNearestReasonIsPrinted() {
        // "no figure anywhere below" and "this one strike has no record" are different facts, and
        // an empty line would be a third thing that is neither.
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);                                    // 7275 only
        ReadablePage.Board board = BoardLines.from(mapJson(), NOW, source, NOW);

        String lines = Pages.deltaLines(priceAt(7274.00), board, null);

        assertTrue(lines.contains("delta|below|7270|cd=norec|pd=norec"),
                "the nearest strike below, with its reason, because no strike below has a figure: "
                        + lines);
        assertFalse(lines.contains("delta|below|absent"),
                "a strike that exists and has no record is not an absence of strikes: " + lines);
    }

    @Test
    @DisplayName("one side may carry a figure and the other a reason, and both are printed")
    void oneSideAFigureTheOtherAReason() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(withStrike(7280.0, 0.0221, -0.9778));
        ReadablePage.Board board = BoardLines.from(mapJson(), NOW, source, NOW);

        String lines = Pages.deltaLines(priceAt(7276.00), board, null);

        assertTrue(lines.contains("delta|above|7280|cd=0.022|pd=-0.978"), lines);
        assertTrue(lines.contains("delta|below|7275|cd=norec|pd=norec"), lines);
    }

    @Test
    @DisplayName("the expiry is printed on both lines when it is known, and absent when it is not")
    void theExpiryIsPrintedWhenKnown() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);
        source.observe(withStrike(7270.0, 0.1234, -0.8766));
        ReadablePage.Board board = BoardLines.from(mapJson(), NOW, source, NOW);

        String named = Pages.deltaLines(priceAt(7272.40), board, "20261120");
        assertEquals("delta|exp=20261120|above|7275|cd=0.055|pd=-0.970\n"
                        + "delta|exp=20261120|below|7270|cd=0.123|pd=-0.877\n", named,
                "the expiry sits before the side, so the line still reads left to right: " + named);

        String unnamed = Pages.deltaLines(priceAt(7272.40), board, null);
        assertFalse(unnamed.contains("exp="),
                "a caller that does not know the expiry prints no empty cell for it: " + unnamed);
        assertEquals(Pages.deltaLines(priceAt(7272.40), board), unnamed,
                "and the two-argument form is exactly the no-expiry form");
    }

    @Test
    @DisplayName("the expiry comes from the board RECORD's own field, not from anywhere else")
    void theExpiryComesFromTheRecord() {
        assertEquals("20261120", BoardLines.expiryOf(mapJson()),
                "the same field the delta lookup keys on, read through the same helper");
        assertEquals(null, BoardLines.expiryOf("{\"spot\":7275.0}"),
                "a record with no expiry yields none rather than a guess");
        assertEquals(null, BoardLines.expiryOf(null));
        assertEquals(null, BoardLines.expiryOf("not json at all"));
    }

    @Test
    @DisplayName("the expiry reaches the /now page the owner reads")
    void theExpiryReachesTheNowPage() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);
        source.observe(withStrike(7270.0, 0.1234, -0.8766));
        ReadablePage.Board board = BoardLines.from(mapJson(), NOW, source, NOW);

        String page = Pages.now(NOW, priceAt(7272.40), null, null, null, null, null, board,
                null, null, null, null, null, "20261120");

        assertTrue(page.contains("delta|exp=20261120|above|7275|"),
                "the page carries the expiry on the line: " + page);
        assertTrue(page.contains("delta|exp=20261120|below|7270|"), page);
    }
}
