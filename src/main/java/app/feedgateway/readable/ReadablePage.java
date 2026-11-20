package app.feedgateway.readable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * <b>C-61</b>: the readable record as one page of plain text, rendered by the service and served
 * read-only at one unauthenticated address, so the reviewer reads the service's own output instead
 * of a page somebody typed by hand.
 *
 * <p><b>This is a REPLACE and not an append.</b> The map service's own {@code LiveWriter} seam
 * appends one block to a tab and R-LIVE-1 forbids it from ever editing an earlier one, which is
 * what makes that tab evidence. This page is the opposite shape on purpose: it is the newest state,
 * whole, replaced every 30 seconds, and a reader must never have to scroll to find out what is
 * current. Two different jobs; folding this into the append-only writer would have broken R-LIVE-1
 * to do it.
 *
 * <p><b>It lives in the GATEWAY, by C-63.</b> The gateway already holds everything this page needs
 * and the map service holds none of it at night: the board comes from the map service's own
 * published record on {@code options.spx.whatif.map}, the overnight rows from the cached
 * {@code es.futures.footprint.bars}, and the last price from the decoded {@code canonicalSpot} of
 * {@code underlying.spx.price}. So the page needs no new topic and the overnight price does not
 * depend on the map service being up — which is right, because at night the two have nothing to
 * do with each other. <b>The gateway FORMATS and does not decide</b>: no gamma, no basis and no
 * reading is computed here, only laid out.
 *
 * <p><b>It is a pure function.</b> Nothing here reads a topic, a clock or a secret: every input is
 * a parameter, so the page a test renders is byte-for-byte the page the service serves. The path it
 * is served at is not this class's business and never appears in it (C-61: the address lives in a
 * Kubernetes secret, never in the repository and never in a log line).
 *
 * <p><b>The order of the first line is the owner's</b> (C-60's first addendum, item a): the time of
 * the last price, in New York and in Amsterdam, then the price, then the time the page was written.
 * He asked for it in that order because the failure he actually hit was a page whose own write time
 * looked current while the price in it was ninety minutes old. {@code STALE} opens the line when the
 * price is older than {@link #STALE_AFTER_SECONDS}, with since when.
 */
public final class ReadablePage {

    /** C-61: the page is plain text under twenty thousand characters. */
    public static final int MAX_CHARS = 20_000;

    /** C-60 addendum (b): older than this and the first line opens with STALE. */
    public static final long STALE_AFTER_SECONDS = 20 * 60;

    /** The owner trades New York hours from Amsterdam, so every time is given in both. */
    static final ZoneId NY = ZoneId.of("America/New_York");
    private static final ZoneId AMS = ZoneId.of("Europe/Amsterdam");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * The last SPX-equivalent price and where it came from.
     *
     * @param epochMs when the price was observed, NOT when it was read
     * @param spx     the SPX-equivalent price; this service never applies a basis of its own
     * @param source  the producer's own word, e.g. {@code ES_BASIS_DERIVED} overnight
     * @param quality the producer's own word, e.g. {@code LIVE_ES_BASIS}
     */
    public record Price(long epochMs, double spx, String source, String quality) {
        public Price {
            if (epochMs <= 0) {
                throw new IllegalArgumentException(
                        "a price with no time cannot be judged stale, which is the one thing this "
                                + "page must be able to say about it (C-60 addendum b)");
            }
        }
    }

    /**
     * One 15-minute slot of the fixed grid. Every field but the start is nullable <b>as a block</b>:
     * a slot the bars have not delivered is a row with its timestamp and empty price cells, which is
     * C-60's third addendum in one sentence — the grid is a time grid, not a list of what arrived.
     */
    public record Slot(long startMs, Double open, Double high, Double low, Double close,
                       Long volume, String basisState) {

        /**
         * True where a bar reached this slot at all, whether or not its price was translated.
         *
         * <p><b>C-118.</b> This was {@code close != null}, so a slot whose bars the platform never
         * translated printed as blank cells and hid the volume that did arrive &mdash; the same
         * defect as the 30-second row's, one grid up.
         */
        public boolean filled() {
            return volume != null;
        }

        /** True where this slot has SPX-equivalent prices to print (C-118). */
        public boolean hasPrices() {
            return close != null;
        }
    }

    /**
     * The overnight path. The high and low are passed in rather than taken from the slots, because
     * they are computed over the live tick path and the tick low can sit below every bar's low —
     * measured on 2026-11-20, where it did.
     */
    public record Overnight(double previousClose, long previousCloseMs, List<Slot> slots,
                            Double high, Double low) {
        public Overnight {
            slots = List.copyOf(slots);
        }
    }

    /**
     * The gamma block, when a board exists. {@code rowsPresent} of {@code rowsExpected} is the
     * reviewer's own item 3: the owner sees at once how complete the morning board is, because the
     * first pre-market boards are thin in the same way 7,170 was.
     */
    /**
     * The largest net-gamma strike on one side of spot, and how far away it is.
     *
     * <p>"Largest" is by the MAGNITUDE of net gamma, not by strike price: the highest strike above
     * spot is just the edge of the grid, which tells a reader nothing, whereas the biggest wall on
     * each side is what the owner reads the ladder for. The distance is in index points and is
     * computed from the board's own spot, so it is the distance at the board's instant rather than
     * at the moment the page happened to be written.
     */
    public record Extreme(double strike, double netGex, double distancePoints) {
    }

    /**
     * @param published how many strikes the record itself carried, as against the {@code
     *                  rowsPresent} of the owner's 41-row grid. The reviewer asked for it on the
     *                  {@code board|} line because the two answer different questions: 41 of 41
     *                  says his grid is full, and {@code published=368} says how much of the chain
     *                  the producer sent. A full grid out of a chain that suddenly halved is a
     *                  fault the grid's own count cannot show. &minus;1 where it is not known
     * @param hold      the map record's own {@code boardHold} block, or null where the board is
     *                  live (R-B-9, C-116). A held board that the page draws as fresh is exactly
     *                  C-40's fault again, so it is carried here and printed
     * @param chainLowCents  the lowest strike the record carries, in cents, or &minus;1 where it
     *                  is not known. With {@code chainHighCents} this tells a SHORT GRID from a
     *                  MISSING FEED: the reviewer met {@code rows=35/41} with {@code norow} at
     *                  6,970&ndash;6,995 as price fell, and those two readings look identical on
     *                  the row count alone. A chain that ends above the bottom of the owner's grid
     *                  is the gamma service publishing a narrower chain than his window; a chain
     *                  that spans the grid with holes in it is a feed losing strikes
     * @param chainHighCents the highest strike the record carries, in cents, or &minus;1
     */
    public record Board(long cycleMs, double spot, int rowsPresent, int rowsExpected,
                        String source, Extreme largestAbove, Extreme largestBelow,
                        List<String> lines, int published, Hold hold,
                        long chainLowCents, long chainHighCents) {

        /** A board held from an earlier cycle, as the map service published it (R-B-9). */
        public record Hold(long ageMs, String reason, int strikesSoFar) {
        }

        /** The shape before the published count and the hold, for the callers that have neither. */
        public Board(long cycleMs, double spot, int rowsPresent, int rowsExpected, String source,
                     Extreme largestAbove, Extreme largestBelow, List<String> lines) {
            this(cycleMs, spot, rowsPresent, rowsExpected, source, largestAbove, largestBelow,
                    lines, -1, null, -1L, -1L);
        }

        /** True where the record named both ends of the chain it carried. */
        public boolean hasChainRange() {
            return chainLowCents >= 0 && chainHighCents >= 0;
        }

        public Board {
            // The source is required and never blank. Two boards reach this page -- the map
            // service's and the pre-open projection's -- and they carry different things: the
            // pre-open one has no what-if reading at all. A board that did not say which it was
            // would let a reader take a pre-open ladder for the map's.
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("a board that does not name its source cannot "
                        + "be told apart from the other one");
            }
            lines = List.copyOf(lines);
        }
    }

    private ReadablePage() {
    }

    /**
     * Render the page.
     *
     * @param nowMs     the write instant
     * @param last      the last price, or null where there is none at all
     * @param overnight the overnight grid, or null before there is one
     * @param board     the gamma block, or null when no board exists yet — in which case the page
     *                  says so rather than showing yesterday's as today's (C-60 addendum d)
     * @param stalled   the inputs THIS page depends on, so a frozen column is named at the top
     *                  rather than left to be inferred from a timestamp in a table
     */
    public static String render(long nowMs, Price last, Overnight overnight, Board board,
            List<StalledInputs.Input> stalled) {
        StringBuilder out = new StringBuilder(4_096);
        out.append(firstLine(nowMs, last)).append('\n');
        // Before ANY table: which inputs have stopped, and since when. A column that is frozen
        // hours back still prints its last value, correctly stamped and entirely plausible, so
        // the page says so in words at the top rather than leaving it to be inferred from a
        // timestamp further down.
        out.append(StalledInputs.banner(nowMs, stalled));
        if (overnight != null) {
            out.append('\n').append(overnightSection(nowMs, overnight));
        }
        if (board != null) {
            out.append('\n').append(boardSection(board));
        } else {
            out.append("\nGAMMA BOARD\n")
                    .append("  No board yet. The gamma exposure feed is silent overnight and the "
                            + "first complete round arrives pre-market; this page will carry it "
                            + "then. Yesterday's board is deliberately NOT shown here, because a "
                            + "board without its own cycle time reads as today's.\n");
        }
        return cap(out.toString());
    }

    /** C-60 addendum (a) and (b), in the owner's own order. */
    private static String firstLine(long nowMs, Price last) {
        if (last == null) {
            return "NO PRICE — this page has no SPX-equivalent price at all. Written "
                    + hm(nowMs, AMS) + " Amsterdam. Nothing below is a current price.";
        }
        long ageSeconds = Math.max(0, (nowMs - last.epochMs()) / 1000L);
        String head = ageSeconds > STALE_AFTER_SECONDS
                ? "STALE — the last price is " + (ageSeconds / 60) + " minutes old, unchanged since "
                        + hms(last.epochMs(), NY) + " New York. "
                : "";
        return head
                + "Last price " + hms(last.epochMs(), NY) + " New York / "
                + hms(last.epochMs(), AMS) + " Amsterdam: "
                + money(last.spx()) + " SPX-equivalent"
                + " (" + last.source() + ", " + last.quality() + ")."
                + " Written " + hms(nowMs, AMS) + " Amsterdam.";
    }

    private static String overnightSection(long nowMs, Overnight o) {
        StringBuilder s = new StringBuilder(2_048);
        s.append("OVERNIGHT — SPX-equivalent, from the platform's own fields\n");
        s.append("  Previous SPX cash close ").append(money(o.previousClose()))
                .append(" at ").append(hms(o.previousCloseMs(), NY)).append(" New York.");
        if (o.high() != null && o.low() != null) {
            s.append("  High ").append(money(o.high()))
                    .append(", low ").append(money(o.low())).append('.');
        } else {
            s.append("  High and low not measurable yet.");
        }
        s.append('\n');
        int filled = 0;
        for (Slot slot : o.slots()) {
            if (slot.filled()) {
                filled++;
            }
        }
        s.append("  ").append(filled).append(" of ").append(o.slots().size())
                .append(" 15-minute slots carry bars. A blank slot keeps its place and is filled "
                        + "in by backfill when the bars arrive; nothing is interpolated.\n\n");
        s.append("  SLOT(NY)  SLOT(AMS)       OPEN       HIGH        LOW      CLOSE"
                + "     VOLUME  BASIS\n");
        for (Slot slot : o.slots()) {
            s.append(String.format(Locale.ROOT, "  %-9s %-9s", hm(slot.startMs(), NY),
                    hm(slot.startMs(), AMS)));
            if (slot.filled()) {
                s.append(String.format(Locale.ROOT, " %10s %10s %10s %10s %10s  %s",
                        slot.hasPrices() ? money(slot.open()) : "-",
                        slot.hasPrices() ? money(slot.high()) : "-",
                        slot.hasPrices() ? money(slot.low()) : "-",
                        slot.hasPrices() ? money(slot.close()) : "-",
                        slot.volume() == null ? "-" : String.format(Locale.ROOT, "%,d",
                                slot.volume()),
                        slot.basisState() == null ? "-" : slot.basisState()));
            } else {
                // C-60's third addendum: the row exists with its timestamp and empty price cells.
                s.append("          -          -          -          -          -  -");
            }
            s.append('\n');
        }
        return s.toString();
    }

    /** C-60 addendum (d): the board's own cycle time and date, so it is never read as today's. */
    /**
     * One side's largest wall, or a stated absence. An absent side is printed rather than omitted:
     * a missing line reads as a page that forgot, and the owner is making a decision on the
     * distance to these two numbers.
     */
    private static String extremeLine(String label, Extreme e) {
        if (e == null) {
            return label + "none on the board yet\n";
        }
        return label + money(e.strike()) + ", net gamma " + BoardLines.money(e.netGex())
                + ", " + String.format(java.util.Locale.ROOT, "%.2f", e.distancePoints())
                + " points away\n";
    }

    private static String boardSection(Board b) {
        StringBuilder s = new StringBuilder(2_048);
        s.append("GAMMA BOARD — cycle ")
                .append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .format(Instant.ofEpochMilli(b.cycleMs()).atZone(NY)))
                .append(" New York (")
                .append(hms(b.cycleMs(), AMS)).append(" Amsterdam)\n");
        s.append("  Spot ").append(money(b.spot()))
                .append(" — source ").append(b.source())
                .append(" — rows ").append(b.rowsPresent()).append(" of ")
                .append(b.rowsExpected());
        if (b.published() >= 0) {
            s.append(" — ").append(b.published())
                    .append(" strikes published on the record");
        }
        s.append(". A strike the gamma service published no row for is printed as "
                + "\"no row\" and never skipped.\n");
        if (b.hold() != null) {
            // R-B-9: first thing after the cycle time and before a single figure, because every
            // number below it is the previous cycle's. A reader who gets to the rows without
            // meeting this sentence has read a held board as a current one.
            s.append("  THESE FIGURES ARE HELD. They are the last complete board, ")
                    .append(b.hold().ageMs() < 0 ? "age unknown"
                            : StalledInputs.ago(b.hold().ageMs()) + " old")
                    .append(", republished because: ").append(b.hold().reason());
            if (b.hold().strikesSoFar() >= 0) {
                s.append(" The round being assembled has reached ")
                        .append(b.hold().strikesSoFar()).append(" strikes.");
            }
            s.append('\n');
        }
        s.append(extremeLine("  Largest net gamma ABOVE spot: ", b.largestAbove()));
        s.append(extremeLine("  Largest net gamma BELOW spot: ", b.largestBelow()));
        // The rows below are C-66's compact form, so the one-letter readings are spelled out HERE,
        // on the page that carries them. Without this the page shows `A`, `H`, `N` and `norow`
        // with nothing to decode them -- which is what a reader would be left guessing at, and
        // what ReadableBoardRendererTest caught when the rows went compact.
        s.append("  ").append(Pages.LEGEND).append('\n');
        if (PreopenBoard.SOURCE.equals(b.source())) {
            s.append("  ").append(Pages.PREOPEN_LEGEND).append('\n');
        }
        s.append('\n');
        for (String line : b.lines()) {
            s.append("  ").append(line).append('\n');
        }
        return s.toString();
    }

    /**
     * C-61's twenty-thousand-character bound, enforced here rather than hoped for.
     *
     * <p>The page is truncated from the <b>end</b>, so the first line and the overnight summary —
     * the price the owner is actually reading — always survive, and the cut is NAMED. A silent
     * truncation of a board would read as a short board.
     */
    private static String cap(String page) {
        if (page.length() <= MAX_CHARS) {
            return page;
        }
        String notice = "\n[TRUNCATED: this page exceeded " + MAX_CHARS
                + " characters and was cut here. The price and the overnight path above are "
                + "complete; board rows below this point are missing.]\n";
        int keep = MAX_CHARS - notice.length();
        int cut = page.lastIndexOf('\n', keep);
        return page.substring(0, cut > 0 ? cut : keep) + notice;
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String hm(long ms, ZoneId zone) {
        return HM.format(Instant.ofEpochMilli(ms).atZone(zone));
    }

    static String hms(long ms, ZoneId zone) {
        return HMS.format(Instant.ofEpochMilli(ms).atZone(zone));
    }
}
