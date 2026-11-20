package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-120: the delta of the strike, on the page.</b>
 *
 * <p><b>The owner's reason, in his words:</b> "what we learn from it is we need to add a delta
 * price on the page so that you know about the delta and take a decision how it functioning." A
 * poke through a strike is not a fixed 5 or 10 points &mdash; it goes as far as it takes to bring
 * that strike's delta close to one, and the distance widens with volatility and time to expiry. On
 * 2026-11-20 three stabs under 7,100 went 13, 21 and 40 points: the 13-point one reversed fully
 * and ran 110 points up, and the other two only came back to the strike.
 *
 * <p><b>Read, never computed</b> (C-103). The Databento GEX producer publishes {@code callDelta}
 * and {@code putDelta} on every strike record of the same-day chain; they reach the page unchanged.
 * Nothing here holds a model, and where the producer publishes no delta for a side the page prints
 * the producer's own reason instead of a figure of ours.
 */
final class C120StrikeDeltaTest {

    /**
     * A real record off {@code options.databento.gex.strike}, read at 15:20:21.183Z on
     * 2026-11-20, trimmed to the fields this reads and no others.
     *
     * <p>Note what it does NOT carry: a {@code strikeCents} field. The strike arrives as
     * {@code strike} in points, so the rounding to cents is here and matches the grid's own.
     */
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

    @Test
    @DisplayName("C-120: the real record's two deltas are read unchanged, to the last digit")
    void therealRecordsDeltasAreReadUnchanged() {
        StrikeDeltaSource.Deltas d = StrikeDeltaSource.decode(REAL);
        assertNotNull(d);
        assertEquals(727_500L, d.strikeCents(), "7275.0 points, rounded to cents here");
        assertEquals("20261120", d.expiry());
        assertEquals(0.05545327388772792, d.call(), 0.0,
                "EXACTLY the producer's figure: nothing here recomputes a delta (C-103)");
        assertEquals(-0.9702893751050274, d.put(), 0.0);
        assertEquals(1_795_188_021_183L, d.updatedAtMs());
        assertNull(d.reasonFor(true), "an empty invalid reason is no reason");
        assertNull(d.reasonFor(false));
    }

    @Test
    @DisplayName("C-120: a record with no strike, or with neither delta, is refused and counted")
    void unusableRecordsAreRefused() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL.replace("\"strike\":7275.0,", ""));
        source.observe(REAL.replace("\"callDelta\":0.05545327388772792,", "")
                .replace("\"putDelta\":-0.9702893751050274,", ""));
        source.observe("not json at all");
        assertEquals(0, source.strikesHeld());
        assertEquals(3, source.unreadable());
        assertEquals(0, source.observed());
    }

    @Test
    @DisplayName("C-120: one side published and the other not keeps the record and says why")
    void oneSidedRecordsAreKeptWithTheProducersReason() {
        StrikeDeltaSource.Deltas d = StrikeDeltaSource.decode(
                REAL.replace("\"putDelta\":-0.9702893751050274,", "")
                        .replace("\"putInvalidReason\":\"\"", "\"putInvalidReason\":\"PARITY_IV\""));
        assertNotNull(d, "the call side is published, so this record is not useless");
        assertNull(d.put());
        assertEquals("PARITY_IV", d.reasonFor(false),
                "the producer's own word for why, which is more than an empty cell");
    }

    @Test
    @DisplayName("C-120: a redelivered OLDER record does not displace the newest")
    void anOlderRedeliveryIsRefused() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);
        source.observe(REAL.replace("\"updatedAtMs\":1795188021183", "\"updatedAtMs\":1795188000000")
                .replace("0.05545327388772792", "0.11111"));
        assertEquals(0.05545327388772792, source.at("20261120", 727_500L).call(), 0.0,
                "a delete-only log can redeliver out of order after a restart, so 'last to "
                        + "arrive' is not 'newest'");
        assertEquals(1, source.refusedOlder());
    }

    @Test
    @DisplayName("C-120: two expiries are KEPT APART, and a board gets its own chain's delta")
    void twoExpiriesAreKeptApart() {
        // Measured on 2026-11-20 at 18:01 the topic was serving the 0DTE 20261120 chain and the
        // 20261123 chain together, 170 strikes of the latter. The first live render of this change
        // keyed on the strike alone and kept "the newest expiry seen", so it put the NEXT
        // session's delta on a 0DTE board and nothing on the page said so. The owner trades the
        // 0DTE. This is the map service's own C-26 rule, here for the same reason.
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);                                   // 20261120, strike 7275
        source.observe(REAL.replace("\"expiry\":\"20261120\"", "\"expiry\":\"20261123\"")
                .replace("0.05545327388772792", "0.4999"));     // 20261123, SAME strike
        assertEquals(2, source.strikesHeld(), "one strike, two expiries, two entries");
        assertEquals(0.05545327388772792, source.at("20261120", 727_500L).call(), 0.0,
                "a 0DTE board asks for the 0DTE chain's delta");
        assertEquals(0.4999, source.at("20261123", 727_500L).call(), 0.0);
        assertEquals("20261123", source.newestExpirySeen());
    }

    @Test
    @DisplayName("C-120: the two spellings of one expiry are the same expiry")
    void theTwoSpellingsAreOneExpiry() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);
        assertNotNull(source.at("2026-11-20", 727_500L),
                "the gamma record spells it 20261120 and the map record spells it 2026-11-20; a "
                        + "lookup that missed on the punctuation would print norec on every row "
                        + "and read as a producer that had stopped publishing deltas");
        assertEquals(source.at("20261120", 727_500L), source.at("2026-11-20", 727_500L));
    }

    @Test
    @DisplayName("C-120: only the two newest expiries are kept, so today's strikes never hit the cap")
    void onlyTheTwoNewestExpiriesAreKept() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        for (String expiry : new String[] {"20261118", "20261119", "20261120"}) {
            source.observe(REAL.replace("\"expiry\":\"20261120\"", "\"expiry\":\"" + expiry + "\""));
        }
        assertEquals(2, source.strikesHeld());
        assertEquals(1, source.prunedExpiries());
        assertNull(source.at("20261118", 727_500L),
                "an unbounded map would refuse TODAY's strikes at the ceiling after a week");
        assertNotNull(source.at("20261119", 727_500L));
        assertNotNull(source.at("20261120", 727_500L));
    }

    /** A map record with three strikes around 7275, as the gateway caches it. */
    private static String mapJson() {
        return "{\"spot\":7275.0,\"expiry\":\"2026-11-20\",\"strikes\":["
                + "{\"strikeCents\":727000,\"board\":{\"netGex\":1.0e9,"
                + "\"holdOrAccelerate\":\"HOLD\"}},"
                + "{\"strikeCents\":727500,\"board\":{\"netGex\":-2.0e9,"
                + "\"holdOrAccelerate\":\"ACCELERATE\"}},"
                + "{\"strikeCents\":728000,\"board\":{\"netGex\":3.0e8,"
                + "\"holdOrAccelerate\":\"NEUTRAL\"}}]}";
    }

    private static String rowFor(ReadablePage.Board board, String strike) {
        for (String row : board.lines()) {
            if (row.startsWith(strike + "|")) {
                return row;
            }
        }
        throw new AssertionError("no row for " + strike + " in " + board.lines());
    }

    @Test
    @DisplayName("C-120: each board row carries cd, pd and the row's age")
    void theBoardRowsCarryTheTwoDeltasAndTheAge() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);
        long now = 1_795_188_021_183L + 12_000L;
        ReadablePage.Board board =
                BoardLines.from(mapJson(), 1_795_188_021_183L, source, now);
        assertNotNull(board);

        String onIt = rowFor(board, "7275");
        assertTrue(onIt.contains("|cd=0.055"), onIt);
        assertTrue(onIt.contains("|pd=-0.970"), onIt);
        assertTrue(onIt.contains("|age=12s"),
                "the age is the render instant against the record's OWN updatedAtMs, to the "
                        + "SECOND: StalledInputs.ago rounds to whole minutes and prints this as "
                        + "0m, which is the difference between a figure the owner can act on "
                        + "during a stab and one he cannot: " + onIt);
        assertEquals("0s", BoardLines.ageCell(400L));
        assertEquals("59s", BoardLines.ageCell(59_999L));
        assertEquals("1m", BoardLines.ageCell(60_000L));
        assertEquals("1h01m", BoardLines.ageCell(3_660_000L));

        String noRecord = rowFor(board, "7270");
        assertTrue(noRecord.contains("|cd=norec|pd=norec"),
                "a strike with no delta published prints the reason, never an empty cell: "
                        + noRecord);
        assertTrue(!noRecord.contains("|age="),
                "and no age, because there is no record to be the age OF: " + noRecord);

        // and the producer's own reason reaches the CELL, not only the decoder
        StrikeDeltaSource oneSided = new StrikeDeltaSource();
        oneSided.observe(REAL.replace("\"putDelta\":-0.9702893751050274,", "")
                .replace("\"putInvalidReason\":\"\"", "\"putInvalidReason\":\"PARITY_IV\""));
        String reasonRow = rowFor(
                BoardLines.from(mapJson(), 1_795_188_021_183L, oneSided, now), "7275");
        assertTrue(reasonRow.contains("|pd=PARITY_IV"),
                "the reviewer asked for the reason and not an empty cell, and the producer's own "
                        + "word for it is the only one this page may use: " + reasonRow);
        assertTrue(reasonRow.contains("|cd=0.055"),
                "while the side that IS published still shows its figure: " + reasonRow);

        for (String row : board.lines()) {
            assertTrue(row.length() < 125,
                    "C-66 keeps a board row under 125 characters: " + row.length()
                            + " in " + row);
        }
    }

    @Test
    @DisplayName("C-120: with the tap unwired the rows carry no delta cells at all, not empty ones")
    void withNoSourceThereAreNoCells() {
        ReadablePage.Board board = BoardLines.from(mapJson(), 1L);
        assertTrue(!rowFor(board, "7275").contains("cd="),
                "an unwired tap is a different fact from a strike with no delta, and the page "
                        + "must not print an empty cell for either");
    }

    @Test
    @DisplayName("C-120: /now carries the strike above and the strike below price, with deltas")
    void theNowPageBracketsPriceWithDeltas() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);                                       // 7275
        source.observe(withStrike(7280.0, 0.0221, -0.9778));
        source.observe(withStrike(7270.0, 0.1234, -0.8766));
        long now = 1_795_188_021_183L + 5_000L;
        ReadablePage.Board board = BoardLines.from(mapJson(), now, source, now);

        // price sits between 7270 and 7275
        ReadablePage.Price last = new ReadablePage.Price(now, 7272.40, "NATIVE_SPX_INDEX",
                "LIVE_INDEX");
        String lines = Pages.deltaLines(last, board);
        assertEquals("delta|above|7275|cd=0.055|pd=-0.970\n"
                        + "delta|below|7270|cd=0.123|pd=-0.877\n", lines,
                "the pair that brackets price, so a stab can be read in delta terms rather than "
                        + "in points. Got: " + lines);
    }

    @Test
    @DisplayName("C-120: the NEAREST on each side, not the first row the walk meets")
    void theNearestOnEachSideAndNotTheFirstRowMet() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);                                       // 7275
        source.observe(withStrike(7280.0, 0.0221, -0.9778));
        source.observe(withStrike(7270.0, 0.1234, -0.8766));
        long now = 1_795_188_021_183L;
        ReadablePage.Board board = BoardLines.from(mapJson(), now, source, now);

        // price above TWO rows: the walk meets 7270 first and 7275 is the nearest below
        String lines = Pages.deltaLines(
                new ReadablePage.Price(now, 7279.00, "NATIVE_SPX_INDEX", "LIVE_INDEX"), board);
        assertTrue(lines.contains("delta|below|7275|"),
                "the nearest strike below price, not the first one the row walk meets: " + lines);
        assertTrue(!lines.contains("delta|below|7270|"), lines);

        // and the same on the other side, with the rows handed over OUT of grid order, so the
        // line does not depend on how the board happened to be built -- C-107 taught this estate
        // exactly that lesson about runs, and it holds for a nearest too.
        ReadablePage.Board shuffled = new ReadablePage.Board(now, 7279.0, 3, 3,
                BoardLines.SOURCE, null, null,
                java.util.List.of(rowFor(board, "7280"), rowFor(board, "7270"),
                        rowFor(board, "7275")));
        String fromShuffled = Pages.deltaLines(
                new ReadablePage.Price(now, 7272.00, "NATIVE_SPX_INDEX", "LIVE_INDEX"), shuffled);
        assertTrue(fromShuffled.contains("delta|above|7275|"),
                "7280 comes first in this list and 7275 is the nearest above: " + fromShuffled);
        assertTrue(fromShuffled.contains("delta|below|7270|"), fromShuffled);
    }

    @Test
    @DisplayName("C-120: the /now lines come off the RENDERED rows, so they cannot disagree")
    void theLinesComeOffTheRowsThemselves() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);
        long now = 1_795_188_021_183L + 90_000L;
        ReadablePage.Board board = BoardLines.from(mapJson(), now, source, now);
        String lines = Pages.deltaLines(
                new ReadablePage.Price(now, 7274.0, "NATIVE_SPX_INDEX", "LIVE_INDEX"), board);
        assertTrue(lines.contains("delta|above|7275|cd=0.055|pd=-0.970\n"), lines);
        assertTrue(!lines.contains("age="),
                "the age belongs on each row; repeating it at the top would be two figures of "
                        + "the same thing one line apart: " + lines);
        assertTrue(lines.contains("delta|below|7270|cd=norec|pd=norec"),
                "and a bracketing strike with no delta says so on this line too: " + lines);
    }

    @Test
    @DisplayName("C-120: the two lines are ON the /now page the owner reads")
    void theLinesAreOnTheNowPage() {
        StrikeDeltaSource source = new StrikeDeltaSource();
        source.observe(REAL);
        source.observe(withStrike(7270.0, 0.1234, -0.8766));
        long now = 1_795_188_021_183L;
        ReadablePage.Board board = BoardLines.from(mapJson(), now, source, now);
        ReadablePage.Price last =
                new ReadablePage.Price(now, 7272.40, "NATIVE_SPX_INDEX", "LIVE_INDEX");

        // Driven through Pages.now, not Pages.deltaLines: the defect a direct call cannot catch
        // is the lines never being appended to the page at all, which is exactly what the
        // mutation pass found here.
        String page = Pages.now(now, last, null, null, null, null, null, board, null, null,
                null, null);
        assertTrue(page.contains("\ndelta|above|7275|cd=0.055|pd=-0.970\n"),
                "the owner reads this page: a figure that is correct and absent is no figure. "
                        + page);
        assertTrue(page.contains("\ndelta|below|7270|cd=0.123|pd=-0.877\n"), page);
        for (String line : page.split("\n")) {
            if (line.startsWith("legend|") || line.startsWith("preopen|")) {
                continue;       // the legend is prose by design and is not a data row
            }
            assertTrue(line.length() < 125,
                    "C-66 keeps a data line under 125 characters: " + line.length() + " in "
                            + line);
        }
    }

    @Test
    @DisplayName("C-120: with no price or no board the delta lines say WHICH is missing")
    void theLinesNameWhatIsMissing() {
        assertEquals("delta|absent|no price, so there is no strike above or below it\n",
                Pages.deltaLines(null, BoardLines.from(mapJson(), 1L)));
        assertEquals("delta|absent|no board, so no strike carries a published delta\n",
                Pages.deltaLines(new ReadablePage.Price(1L, 7272.0, "S", "Q"), null));
    }
}
