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

    private final OvernightSource source = new OvernightSource();
    private final Supplier<String> boardJson;
    private final LongSupplier clock;

    private volatile String page = "NOT YET RENDERED — the gateway has not written this page yet. "
            + "It is written every 30 seconds; if this line persists, the renderer is not running.\n";
    private volatile long renderedAtMs;
    private long nightStartedAt;

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
                    source.bars(), null, null);
        }
        String json = boardJson.get();
        ReadablePage.Board board = json == null ? null : BoardLines.from(json, boardCycleMs(json, now));
        String rendered = ReadablePage.render(now, source.lastPrice(), overnight, board);
        if (reference == null) {
            rendered = rendered + "\nNO REFERENCE PRICE YET — the previous cash close is taken from "
                    + "the last print of the SPX index itself, and this gateway has not seen one "
                    + "since it started. The overnight rows appear once it has.\n";
        }
        page = rendered;
        renderedAtMs = now;
        return rendered;
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

    /** How many bars the night is holding, for a health read. */
    public int barCount() {
        return source.barCount();
    }

    /** The store, for the end-to-end test to assert against. */
    OvernightSource source() {
        return source;
    }
}
