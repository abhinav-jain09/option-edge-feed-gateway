package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C-75: the top of {@code /now} as short labelled lines a summarising reader returns whole.
 *
 * <p>The constraint is unusual and worth stating: the reviewer's reader answers through a
 * summarising step. It returns short labelled lines reliably and will not reproduce a sixty-row
 * table — it would hand the owner a <i>description</i> of the table instead. So every line here is
 * one fact, and the test asserts the SHAPE as well as the content: one fact per line, short.
 */
class HeadlineTest {

    private static final long NOW = 1_795_140_327_000L;

    private static ReadablePage.Price price(long atMs, double spx) {
        return new ReadablePage.Price(atMs, spx, "ES_BASIS_DERIVED", "LIVE_ES_BASIS");
    }

    private static ReadablePage.Board board() {
        return new ReadablePage.Board(NOW, 7418.46, 41, 41, BoardLines.SOURCE,
                new ReadablePage.Extreme(7455.0, 6.041e8, 36.54),
                new ReadablePage.Extreme(7410.0, 3.288e8, 8.46),
                List.of("7420|+267M||spot"));
    }

    private static RecentRows.Row row(long atMs, double spx, String dominant) {
        return new RecentRows.Row(atMs, spx, spx + 0.5, spx - 0.5, 120L, 70L, 50L, 20L,
                dominant, "ANCHORED");
    }

    @Test
    @DisplayName("every fact the reviewer is asked for is one short labelled line")
    void everyFactIsOneShortLine() {
        String head = Pages.headline(NOW, price(NOW - 5_000L, 7155.45),
                new ReadablePage.Overnight(7176.24, NOW - 8L * 3_600_000L, List.of(),
                        7181.74, 7148.74),
                null, null,
                List.of(row(NOW - 30_000L, 7155.45, "B"), row(NOW - 60_000L, 7156.10, "S"),
                        row(NOW - 90_000L, 7155.90, "-")),
                board(),
                List.of(new StalledInputs.Input("footprint bars",
                        NOW - 12L * 3_600_000L, "The slots")), null, null);

        List<String> lines = head.lines().toList();
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("now|7155.45 SPX|")), head);
        assertTrue(lines.stream().anyMatch(l -> l.equals("close|7176.24|-20.79")), head);
        assertTrue(lines.stream().anyMatch(l -> l.equals("above|7455|36.54pts")), head);
        assertTrue(lines.stream().anyMatch(l -> l.equals("below|7410|8.46pts")), head);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("r1|")), head);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("r3|")), head);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("stalled|footprint bars|12h")), head);

        for (String l : lines) {
            assertTrue(l.length() <= 64,
                    "a line a summarising reader must return whole has to stay short: \"" + l
                            + "\" is " + l.length() + " characters");
        }
    }

    @Test
    @DisplayName("nothing stalled says so, because a missing line reads as a page that forgot")
    void nothingStalledIsStatedExplicitly() {
        String head = Pages.headline(NOW, price(NOW - 5_000L, 7155.45), null, null, null,
                List.of(), board(),
                List.of(new StalledInputs.Input("footprint bars", NOW - 30_000L, "The slots")), null, null);
        assertTrue(head.contains("stalled|none"),
                "the absence of a stalled line cannot be told apart from an unchecked page: "
                        + head);
    }

    @Test
    @DisplayName("every absent fact still gets its line, so the reader can see what is missing")
    void absencesStillGetALine() {
        String head = Pages.headline(NOW, null, null, null, null, null, null, null, null, null);
        assertTrue(head.contains("now|no price"), head);
        assertTrue(head.contains("close|absent"), head);
        assertTrue(head.contains("vwap|absent"), head);
        assertTrue(head.contains("above|absent") && head.contains("below|absent"), head);
        assertEquals(3, head.lines().filter(l -> l.matches("^r\\d\\|absent$")).count(),
                "all three row slots are named even when there are none");
    }

    @Test
    @DisplayName("the headline sits at the top of /now, above the legend and every table")
    void theHeadlineIsAtTheTop() {
        String page = Pages.now(NOW, price(NOW - 5_000L, 7155.45), null, null, null, null,
                List.of(), board(), List.of(), new VwapHeld(), null, null);
        int nowLine = page.indexOf("\nnow|");
        int legend = page.indexOf("legend|");
        assertTrue(nowLine > 0, "the headline is on the page: " + page);
        assertTrue(nowLine < legend,
                "and above the legend and therefore above every table below it");
    }
}
