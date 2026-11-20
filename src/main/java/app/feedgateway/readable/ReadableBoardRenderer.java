package app.feedgateway.readable;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * <b>C-61 and C-63</b>: holds the overnight inputs, renders the readable page on a fixed cadence,
 * and hands out the bytes that were last rendered.
 *
 * <p><b>Rendered on a schedule, not per request.</b> C-61 says the service writes the page every 30
 * seconds, and the first line carries the time it was written. A render per request would make that
 * time meaningless — it would always equal the read time, and a reader could no longer tell a page
 * the service is still writing from one it stopped writing an hour ago. The cadence also bounds the
 * work: one render per interval however many readers there are.
 *
 * <p><b>The page is never empty and never an error.</b> Before the first render it says so; with no
 * price it says so; with no board it says so. A reader at a secret address cannot tell a 500 from a
 * wrong address, so this never answers with one.
 *
 * <p><b>It decides nothing</b> (C-63). The board's gamma, sign and reading come from the map
 * service's published record; the overnight prices come from the platform's own
 * {@code spxEq} fields; the reference price is the last index print. Nothing here computes a
 * reading, and nothing applies a basis.
 */
public final class ReadableBoardRenderer {

    /** C-61's cadence. */
    public static final long RENDER_INTERVAL_MS = 30_000L;

    /**
     * The timeframe the night's coarse grid is built from. Minutes, not 30-second bars: the grid's
     * slots are 15 minutes wide, so minutes are half the records for the same answer. C-64's Recent
     * table uses {@code 30s} instead, and the two never share a slot.
     */
    public static final String NIGHT_TIMEFRAME = "1m";

    /** The timeframe C-64's 30-second rows are built from — the owner's own resolution. */
    public static final String RECENT_TIMEFRAME = "30s";

    private final OvernightSource source = new OvernightSource();
    private final PreopenSource preopen = new PreopenSource();
    private final Supplier<String> boardJson;
    private final LongSupplier clock;

    private volatile String page = "NOT YET RENDERED — the gateway has not written this page yet. "
            + "It is written every 30 seconds; if this line persists, the renderer is not running.\n";
    private volatile long renderedAtMs;
    private long nightStartedAt;
    /** C-74: where the reference price came from, in words, for the page. */
    private volatile String referenceLabel;

    /** Whether a pre-open board has ever been served, which is what makes a switch a switch. */
    private boolean servedPreopen;
    /** When the map record first replaced it. Recorded once, so the line does not drift. */
    private long switchedAtMs;

    /**
     * @param boardJson the map service's own cached record for the chain being shown, or null
     * @param clock     the wall clock, injected so a test renders a deterministic page
     */
    public ReadableBoardRenderer(Supplier<String> boardJson, LongSupplier clock) {
        this.boardJson = boardJson == null ? () -> null : boardJson;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** One footprint bar, as the gateway received it. Unreadable records are counted, not fatal. */
    public void onBarJson(String json) {
        OvernightGrid.Bar bar = OvernightDecoders.bar(json);
        if (bar != null) {
            source.onBar(bar);
        }
    }

    /**
     * One pre-open gamma record, as the gateway received it off
     * {@code options.databento.gex.strike.preopen}.
     *
     * <p>This is what lets the owner read his ladder at 06:30 New York. The map service consumes
     * only {@code options.databento.gex.strike} and so has nothing to publish until the cash path
     * comes round; measured on 2026-11-19 its first record was 09:15:58, while the pre-open board
     * was already complete at 06:43:02.
     */
    /**
     * <b>C-74</b>: take the previous cash close from history at start-up.
     *
     * <p>Called once by the gateway after it has read back the price topic and the cash-session
     * bars. Without it, a gateway that restarts overnight has never seen an index print, so the
     * owner's overnight rows stay empty until the cash open — precisely when he does not need
     * them. A live index print still wins over a recovered one; this only fills a gap.
     */
    public void seedReference(ReferencePrice.Resolved resolved) {
        if (resolved == null) {
            return;
        }
        referenceLabel = resolved.label();
        if (resolved.close() != null) {
            source.seedPreviousClose(resolved.close());
            nightStartedAt = resolved.close().epochMs();
            source.nightStartsAt(nightStartedAt);
        }
    }

    /** Where the reference price came from, or null before anything has been resolved. */
    public String referenceLabel() {
        return referenceLabel;
    }

    public void onPreopenJson(String json) {
        preopen.observe(json);
    }

    /** The pre-open store, for the status page and the tests. */
    public PreopenSource preopenSource() {
        return preopen;
    }

    /** One SPX price record, as the gateway received it. */
    public void onPriceJson(String json, long recordTimestampMs) {
        ReadablePage.Price price = OvernightDecoders.price(json, recordTimestampMs);
        if (price != null) {
            source.onPrice(price);
            ReadablePage.Price reference = source.previousCashClose();
            if (reference != null && reference.epochMs() != nightStartedAt) {
                // The night starts at the last index print. When that moves -- a new cash close --
                // the store drops the previous night so a long-running gateway cannot accumulate.
                nightStartedAt = reference.epochMs();
                source.nightStartsAt(nightStartedAt);
            }
        }
    }

    /**
     * Render once. Called on the cadence; safe to call more often.
     *
     * @return the page just rendered
     */
    public String render() {
        long now = clock.getAsLong();
        ReadablePage.Price reference = source.previousCashClose();
        ReadablePage.Overnight overnight = null;
        if (reference != null) {
            overnight = OvernightGrid.build(now, reference.epochMs(), reference.spx(),
                    source.bars(NIGHT_TIMEFRAME), null, null);
        }
        String json = boardJson.get();
        ReadablePage.Board board = json == null ? null : BoardLines.from(json, boardCycleMs(json, now));
        if (board == null) {
            // The map record is always preferred where there is one: it is the fuller board and it
            // carries the what-if readings. The pre-open board stands in only while there is no map
            // record at all, and it names itself on the page, so the handover is visible rather
            // than silent. It is never merged with the map board -- two sources in one ladder
            // would leave the owner unable to say which rows he was reading.
            board = preopen.board(now);
            if (board != null) {
                servedPreopen = true;
            }
        } else if (servedPreopen) {
            // C-73: the switch is ANNOUNCED, with the time it happened. The owner will have been
            // reading the pre-open ladder for two and a half hours; a silent swap to a different
            // board with different columns -- and a different definition of the same figures --
            // is exactly the kind of change he should not have to notice for himself. The instant
            // is recorded ONCE, so the line keeps saying when it happened rather than "just now".
            if (switchedAtMs == 0L) {
                switchedAtMs = now;
            }
            board = new ReadablePage.Board(board.cycleMs(), board.spot(), board.rowsPresent(),
                    board.rowsExpected(),
                    board.source() + " (switched from " + PreopenBoard.SOURCE + " at "
                            + ReadablePage.hms(switchedAtMs, ReadablePage.NY) + " New York)",
                    board.largestAbove(), board.largestBelow(), board.lines());
        }
        String rendered = ReadablePage.render(now, source.lastPrice(), overnight, board,
                stalledInputs());
        if (reference == null) {
            rendered = rendered + "\nNO REFERENCE PRICE YET — the previous cash close is taken from "
                    + "the last print of the SPX index itself, and this gateway has not seen one "
                    + "since it started. The overnight rows appear once it has.\n"
                    + (referenceLabel == null ? "" : "  " + referenceLabel + "\n");
        } else if (referenceLabel != null) {
            // C-74: an index print and a futures bar's SPX-equivalent are different facts, so the
            // page names which one this close is rather than leaving the reader to assume.
            rendered = rendered + "\nREFERENCE — " + referenceLabel + "\n";
        }
        page = rendered;
        renderedAtMs = now;
        return rendered;
    }

    /**
     * The inputs THIS page depends on, with the newest instant the gateway has actually seen for
     * each. Only the footprint bars are listed here: this page's tables are the overnight grid and
     * the gamma board, and the board's own freshness is already on its heading as a cycle time.
     * The VWAP and holding inputs belong to {@code /now}, which lists them itself — a page that
     * warned about an input it does not show would be noise.
     */
    private java.util.List<StalledInputs.Input> stalledInputs() {
        return java.util.List.of(new StalledInputs.Input(
                "footprint bars", source.newestBarStartMs(NIGHT_TIMEFRAME),
                "The overnight slots and their closes, volumes and buyer/seller counts"));
    }

    /**
     * The board's own cycle instant, from the record's fingerprint where it carries one.
     *
     * <p>Falls back to the render time rather than guessing, and the page's heading then shows that
     * — which is visibly wrong rather than quietly wrong, and a board whose own time cannot be
     * read is a board somebody should look at.
     */
    private static long boardCycleMs(String json, long fallback) {
        Long at = BoardCycle.epochMsOf(json);
        return at == null ? fallback : at;
    }

    /** The bytes to serve: whatever was last rendered. Never null, never empty. */
    public String page() {
        return page;
    }

    /** When the served page was rendered, so a watchdog can see the renderer has stopped. */
    public long renderedAtMs() {
        return renderedAtMs;
    }

    /** How many bars the night is holding, across every timeframe, for a health read. */
    public int barCount() {
        return source.barCount();
    }

    /** The store, for the end-to-end test to assert against. */
    OvernightSource source() {
        return source;
    }
}
