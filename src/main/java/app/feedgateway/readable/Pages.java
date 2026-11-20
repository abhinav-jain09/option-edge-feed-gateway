package app.feedgateway.readable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>C-66</b>: the readable record as several compact plain-text pages under one secret prefix.
 *
 * <p><b>Compact, because the reader is a machine.</b> The owner's words were "put it in a compact
 * form", and the reviewer reads these through a plain web read with no script. So: one header line
 * per table, one row per line, fields separated by a single {@code |} with no spaces, no padding, no
 * alignment, no blank lines between rows. Values that are the same for every row of a table are
 * stated <b>once</b> on the line above it rather than repeated down a column. Times are
 * {@code HH:MM:SS}, prices carry two decimals, volumes are plain integers with no thousands
 * separator, and an empty cell is nothing at all between two separators.
 *
 * <p><b>No prose except three things</b>: the first line, the legend, and a one-line reason where a
 * section is absent. Readings travel as one-letter codes, which the legend expands.
 *
 * <p><b>Every page starts with the same first line</b>, so whichever page the reviewer opens tells
 * him immediately how current the data behind it is. {@code STALE} opens that line when the price is
 * older than {@link ReadablePage#STALE_AFTER_SECONDS}.
 *
 * <p><b>Nothing is ever dropped to fit.</b> Where a page would exceed its bound it is paged, not
 * truncated: {@code /footprint/2}, {@code /recent-full/2} and so on. That is C-66 in one line, and
 * it is the opposite of the earlier single page, which cut from the bottom.
 */
public final class Pages {

    /** C-66: each page stays under this. */
    public static final int MAX_CHARS = 20_000;

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final ZoneId AMS = ZoneId.of("Europe/Amsterdam");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * <b>C-99: which SPX figures on these pages this page worked out, rather than read.</b>
     *
     * <p>The owner's order is that the SPX price comes from the SPX price topic and is never derived
     * by us. Almost everything already obeys it: {@code now|} and {@code last=…px} are the topic's
     * own {@code canonicalSpot} untouched, and the grid and the 30-second rows read the producer's
     * published {@code spxEq*Cents} with its own {@code spxEqBasisState}.
     *
     * <p><b>Two figures are published in SPX terms by nothing, and those two this page
     * translates.</b> They are named here rather than described, so a reader can tell at a glance
     * which numbers are ours:
     *
     * <ul>
     *   <li>the {@code vwapspx|} line and the SPX cells of the other VWAP lines — the indicators
     *       service publishes its VWAP in ES only (C-69);</li>
     *   <li>the SPX column of {@code /footprint}'s levels — the gamma-zone profile publishes
     *       {@code priceCents} in ES.</li>
     * </ul>
     *
     * <p>Both use the platform's <b>own published basis</b> from {@code spx.basis.state}, and only
     * where it is {@code ANCHORED}; neither computes a basis. So what is ours is the subtraction, not
     * the figure it is made from — and the legend says so in the owner's own terms rather than
     * leaving him to ask which column he is reading.
     */
    static final String PAGE_COMPUTED =
            "spx-by-this-page=vwapspx and the footprint SPX column are translated here from the "
            + "published ES figure using the published basis; every other SPX price is the topic's";

    /**
     * The reading codes, expanded by every page's legend.
     *
     * <p><b>C-118 made {@code empty} mean one thing, and the legend now says which.</b> It read
     * "no data for that slot" while a blank row could also be a bar the platform had not
     * translated &mdash; nine of them on 2026-11-20, each with a real volume and a real delta. A
     * blank row is now only a slot with no bar at all, so the legend's old words are true for the
     * first time; and the two states that used to hide behind it have their own entries, because a
     * reader meeting {@code ES7168.25} or {@code noSPX} for the first time should not have to
     * guess whether the page is showing him a different instrument.
     */
    static final String LEGEND =
            "legend|A=accelerate|H=hold|N=neutral|norow=gamma service published no row|"
            + "empty=no bar arrived for that slot (and nothing else)|"
            + "ES0000.00=the bar's OWN futures price, printed because the platform published no "
            + "SPX-equivalent for it; never translated here|"
            + "noSPX=that bar carries no SPX-equivalent price, so its prices are futures|"
            + "px=SPX-equivalent|basis A=anchored, ?=none on that bar|"
            + PAGE_COMPUTED;

    /**
     * <b>C-73</b>: the one line that explains the empty columns on a pre-open board. Those columns
     * are not missing data — the map service is the only thing that can fill them, and it has no
     * board until the cash path comes round. Saying when they begin is the difference between a
     * reader thinking the feed is broken and knowing he is early.
     */
    static final String PREOPEN_LEGEND =
            "preopen|reading, strength, decay, band and ladders are empty until the map service "
            + "publishes, expected about 09:15 New York";

    private Pages() {
    }

    /**
     * The line every page opens with (C-66), in the owner's order: the price's own time in both
     * zones, then the price, then when this page was written.
     */
    public static String firstLine(long nowMs, ReadablePage.Price last) {
        if (last == null) {
            return "NO PRICE|written=" + hms(nowMs, AMS) + "AMS|nothing below is a current price";
        }
        long ageSeconds = Math.max(0, (nowMs - last.epochMs()) / 1000L);
        String head = ageSeconds > ReadablePage.STALE_AFTER_SECONDS
                ? "STALE(" + (ageSeconds / 60) + "m)|"
                : "";
        return head
                + "last=" + hms(last.epochMs(), NY) + "NY/" + hms(last.epochMs(), AMS) + "AMS"
                + "|px=" + money(last.spx())
                + "|src=" + last.source()
                + "|written=" + hms(nowMs, AMS) + "AMS";
    }

    /**
     * {@code /board}: every 5-point strike within 100 points of spot, one row per line.
     *
     * <p>The cycle time and spot are stated once above the table rather than repeated on 41 rows,
     * which is the addendum's rule and saves about a thousand characters.
     */
    /**
     * The largest net-gamma wall on one side, compact: {@code largest|above|7455|+604M|36.54pts}.
     * An absent side still prints a row, because the owner is reading the distance to these two.
     */
    static String extreme(String side, ReadablePage.Extreme e) {
        if (e == null) {
            return "largest|" + side + "|none\n";
        }
        return "largest|" + side + "|" + BoardLines.strikeOf(e.strike())
                + "|" + BoardLines.money(e.netGex())
                + "|" + String.format(java.util.Locale.ROOT, "%.2f", e.distancePoints()) + "pts\n";
    }

    public static String board(long nowMs, ReadablePage.Price last, ReadablePage.Board board) {
        StringBuilder s = new StringBuilder(4_096);
        s.append(firstLine(nowMs, last)).append('\n').append(LEGEND).append('\n');
        if (board == null) {
            s.append("board|absent|no complete gamma round yet; the gamma feed is silent "
                    + "overnight and yesterday's board is not shown because a board without its "
                    + "own cycle time reads as today's\n");
            return cap(s.toString());
        }
        s.append("board|cycle=").append(hms(board.cycleMs(), NY)).append("NY|date=")
                .append(DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        .format(Instant.ofEpochMilli(board.cycleMs()).atZone(NY)))
                .append("|spot=").append(money(board.spot()))
                .append("|src=").append(board.source())
                .append("|rows=").append(board.rowsPresent()).append('/')
                .append(board.rowsExpected())
                .append(board.published() < 0 ? "" : "|published=" + board.published())
                .append(chainCell(board))
                .append(holdCell(board.hold()))
                .append('\n');
        if (PreopenBoard.SOURCE.equals(board.source())) {
            s.append(PREOPEN_LEGEND).append('\n');
        }
        s.append(extreme("above", board.largestAbove()));
        s.append(extreme("below", board.largestBelow()));
        s.append("strike|netgamma|read\n");
        for (String line : board.lines()) {
            s.append(line).append('\n');
        }
        return cap(s.toString());
    }


    /**
     * {@code /now} — the page the reviewer opens first (C-66).
     *
     * <p>Top to bottom: the first line; the legend; the close and the night's range; the VWAP line
     * with both anchors, both band pairs and the basis it was translated under; the holding line of
     * C-65 (3); the band attempts; the Recent table of 30-second rows; and the five largest strikes
     * when a board exists.
     *
     * <p><b>Nothing on this page is computed from another line of it.</b> The holding figures come
     * from the observed side path, the Recent rows from the bars, the strikes from the map record.
     * A page whose lines were derived from each other would agree with itself while being wrong.
     */
    /**
     * <b>C-75</b>: the top of {@code /now} as single labelled lines, each quotable whole.
     *
     * <p>The stalled inputs appear here rather than as the prose banner used on the long page:
     * one statement of a fact, not two. They stay above every table, which is the requirement
     * they came from.
     */
    static String headline(long nowMs, ReadablePage.Price last, ReadablePage.Overnight overnight,
            VwapRead vwap, VwapSideTracker.Result holding, List<RecentRows.Row> recent,
            ReadablePage.Board board, List<StalledInputs.Input> stalledInputs,
            String feedGap, String closeAbsenceReason) {
        StringBuilder s = new StringBuilder(512);

        s.append("now|").append(last == null ? "no price" : money(last.spx()) + " SPX")
                .append('|').append(last == null ? "" : hms(last.epochMs(), NY) + "NY")
                .append('\n');

        if (overnight == null) {
            // C-98 item 5: an unresolvable close is an absence WITH ITS REASON, never a price.
            // `close|absent` on its own told the owner the figure was missing and nothing about
            // why -- and the reason is the whole of what he needs, because "no silence has been
            // seen in the index feed yet" and "the heap would not let the block be built" call for
            // completely different reactions from him.
            s.append("close|absent|")
                    .append(closeAbsenceReason == null || closeAbsenceReason.isBlank()
                            ? "no reason given" : closeAbsenceReason)
                    .append('\n');
        } else {
            s.append("close|").append(money(overnight.previousClose())).append('|')
                    .append(last == null ? "" : String.format(Locale.ROOT, "%+.2f",
                            last.spx() - overnight.previousClose()))
                    .append('\n');
        }

        if (holding == null) {
            s.append("vwap|absent").append(gapSuffix(feedGap)).append('\n');
        } else {
            s.append("vwap|").append(holding.side()).append("|held ")
                    .append(mins(nowMs - holding.sideSinceMs()))
                    .append(gapSuffix(feedGap)).append('\n');
        }

        s.append(wall("above", board == null ? null : board.largestAbove()));
        s.append(wall("below", board == null ? null : board.largestBelow()));

        // The three newest 30-second rows, newest first, one line each.
        //
        // C-118: the price cell follows the same rule as the table's -- the SPX-equivalent price
        // where the platform translated the bar, otherwise the bar's own futures price marked ES,
        // and the side is printed either way. These three lines are what the owner reads first, so
        // a bar hidden here is hidden where it matters most.
        int n = 0;
        if (recent != null) {
            for (RecentRows.Row r : recent) {
                if (n >= 3) {
                    break;
                }
                s.append('r').append(n + 1).append('|').append(hms(r.startMs(), NY)).append("NY")
                        .append('|').append(headlinePriceCell(r))
                        .append('|').append(r.dominant() == null ? "" : r.dominant())
                        .append('\n');
                n++;
            }
        }
        while (n < 3) {
            s.append('r').append(++n).append("|absent\n");
        }
        s.append(gridStateLine(recent));

        boolean anyStalled = false;
        if (stalledInputs != null) {
            for (StalledInputs.Input in : stalledInputs) {
                if (in == null || in.name() == null) {
                    continue;
                }
                if (in.newestMs() <= 0L) {
                    s.append("stalled|").append(in.name()).append("|never received\n");
                    anyStalled = true;
                } else if (nowMs - in.newestMs() > StalledInputs.STALLED_AFTER_MS) {
                    s.append("stalled|").append(in.name()).append('|')
                            .append(StalledInputs.ago(nowMs - in.newestMs()))
                            .append("|since ").append(hms(in.newestMs(), NY)).append("NY\n");
                    anyStalled = true;
                }
            }
        }
        if (!anyStalled) {
            // Said explicitly, because the ABSENCE of a stalled line is otherwise indistinguishable
            // from a page that forgot to check.
            s.append("stalled|none\n");
        }
        return s.toString();
    }

    /**
     * The headline row's price cell (C-118): the index price, else the bar's own futures price
     * marked, else empty for a slot with no bar.
     */
    private static String headlinePriceCell(RecentRows.Row r) {
        if (r.price() != null) {
            return money(r.price());
        }
        if (r.barPresent() && r.futuresPrice() != null) {
            return RecentRows.FUTURES_MARK + money(r.futuresPrice());
        }
        return "";
    }

    /**
     * <b>C-118</b>: what the grid above actually shows, counted off the RENDERED rows.
     *
     * <p>Three states, named apart, because collapsing any two of them is how C-117 happened:
     * <ul>
     *   <li>{@code missing} &mdash; the slot has no bar at all. Nothing arrived for it, and the
     *       page's legend "no data for that slot" is about these and only these.</li>
     *   <li>{@code noSPX} &mdash; a bar is here, with its volume and its side, and the platform
     *       never translated its price, so the row shows futures prices marked {@code ES}. This is
     *       the state that used to render identically to {@code missing}.</li>
     *   <li>{@code complete} &mdash; a bar is here and carries its index price.</li>
     * </ul>
     *
     * <p>It is counted from the rows the renderer is about to print rather than from the bars held
     * upstream, so the line cannot say one thing while the grid shows another.
     */
    static String gridStateLine(List<RecentRows.Row> recent) {
        if (recent == null || recent.isEmpty()) {
            return "grid|rows=0\n";
        }
        int missing = 0;
        int noSpx = 0;
        int complete = 0;
        for (RecentRows.Row r : recent) {
            if (!r.barPresent()) {
                missing++;
            } else if (r.price() == null) {
                noSpx++;
            } else {
                complete++;
            }
        }
        return "grid|rows=" + recent.size() + "|missing=" + missing
                + "|noSPX=" + noSpx + "|complete=" + complete + "\n";
    }

    /**
     * The ends of the chain the record carried: {@code |chain=6995-7395}.
     *
     * <p><b>Why the row count is not enough.</b> {@code rows=35/41} with {@code norow} on six
     * lines reads the same whether the gamma service published a chain that stops above the bottom
     * of the owner's window, or published across it and lost six strikes. The first is a short
     * grid and follows price; the second is a feed fault. The chain's own ends tell them apart in
     * one glance, which is why the reviewer asked for them here rather than in a count.
     */
    static String chainCell(ReadablePage.Board board) {
        if (!board.hasChainRange()) {
            return "";
        }
        return "|chain=" + BoardLines.strikeOf(board.chainLowCents() / 100.0)
                + "-" + BoardLines.strikeOf(board.chainHighCents() / 100.0);
    }

    /**
     * <b>R-B-9</b>: {@code |held|age=2m10s|reason=…} where the board is held, nothing where it is
     * live.
     *
     * <p>On the same line as the cycle time, deliberately. A reader who takes in
     * {@code cycle=10:38:58NY} and stops has read a fresh-looking board; the hold has to be in the
     * same glance or the page is drawing a held board as a current one, which is C-40's fault
     * again. The age is the map service's own figure and is not derived from the cycle time here.
     */
    static String holdCell(ReadablePage.Board.Hold hold) {
        if (hold == null) {
            return "";
        }
        StringBuilder s = new StringBuilder(48);
        s.append("|held");
        s.append("|age=").append(hold.ageMs() < 0 ? "unknown" : StalledInputs.ago(hold.ageMs()));
        if (hold.strikesSoFar() >= 0) {
            s.append("|sofar=").append(hold.strikesSoFar());
        }
        s.append("|reason=").append(hold.reason());
        return s.toString();
    }

    /** One wall as a short line: {@code above|7455|36.54pts}. */
    private static String wall(String side, ReadablePage.Extreme e) {
        if (e == null) {
            return side + "|absent\n";
        }
        return side + "|" + BoardLines.strikeOf(e.strike())
                + "|" + String.format(Locale.ROOT, "%.2f", e.distancePoints()) + "pts\n";
    }

    public static String now(long nowMs, ReadablePage.Price last, ReadablePage.Overnight overnight,
            VwapRead vwap, BasisRead basis, VwapSideTracker.Result holding,
            List<RecentRows.Row> recent, ReadablePage.Board board,
            List<StalledInputs.Input> stalled, VwapHeld held, String feedGap, String closeAbsenceReason) {
        StringBuilder s = new StringBuilder(8_192);
        s.append(firstLine(nowMs, last)).append('\n');
        // C-75: the facts the reviewer is asked for, as SHORT single-fact labelled lines, above
        // everything else. His reader answers through a summarising step: it returns short labelled
        // lines reliably and will not reproduce a 60-row table, so it would hand the owner a
        // description of a table instead of the table. Each line here is one fact, short enough to
        // be quoted whole. The full tables stay below, unchanged.
        s.append(headline(nowMs, last, overnight, vwap, holding, recent, board, stalled,
                feedGap, closeAbsenceReason));
        s.append(LEGEND).append("|A/B/==above/below/on VWAP|U1/L1=one-sigma bands\n");

        // the close and the range, once
        if (overnight == null) {
            // C-98 item 5 again, on the detailed line: before this, an absent reference printed
            // NOTHING here, so the owner's page simply had no close row and he had to notice its
            // absence rather than be told. An absence he can read beats a line that is not there.
            s.append("close|absent|")
                    .append(closeAbsenceReason == null || closeAbsenceReason.isBlank()
                            ? "no reason given" : closeAbsenceReason)
                    .append('\n');
        } else {
            s.append("close|prev=").append(money(overnight.previousClose()))
                    .append("|diff=").append(last == null ? ""
                            : String.format(Locale.ROOT, "%+.2f",
                                    last.spx() - overnight.previousClose()))
                    .append("|high=").append(overnight.high() == null ? "" : money(overnight.high()))
                    .append("|low=").append(overnight.low() == null ? "" : money(overnight.low()))
                    .append('\n');
        }

        // the VWAP line: ES as published, SPX-equivalent only where the basis says it may
        if (vwap == null && (held == null || held.held() == 0)) {
            // No record at all and nothing ever held: name the topic that is silent. Two
            // per-line "not published" lines would have said less than this one does.
            s.append("vwap|absent|es.options.indicators.snapshot.current carries no vwap block\n");
        } else if (vwap == null) {
            // No readable record NOW, but figures were held earlier: keep them, with the time
            // they were read -- "no vwap block at all" is a state of the record, not a reason to
            // drop what the line last knew.
            s.append(vwapLine("vwapes", null, null, held));
            s.append(vwapLine("vwaprth", null, null, held));
        } else {
            s.append(vwapLine("vwapes", vwap.session(), null, held));
            s.append(vwapLine("vwaprth", vwap.rth(), null, held));
            if (basis == null) {
                s.append("vwapspx|absent|no basis published on spx.basis.state\n");
            } else if (!basis.usableForDisplay()) {
                s.append("vwapspx|absent|basis ").append(basis.cell())
                        .append("|not anchored, so nothing is translated\n");
            } else {
                s.append(vwapLine("vwapspx", vwap.session(), basis, held))
                        .append("basis|").append(basis.cell())
                        .append(basis.nextSessionQuarantinedOpen() ? "|nextopen=QUARANTINED" : "")
                        .append('\n');
            }
        }

        // the holding line
        if (holding == null) {
            s.append("hold|absent|no side observed yet\n");
        } else {
            // C-83: px and vwap directly after the side, both in ES as the comparison uses them,
            // so a reader can check the letter from the page instead of reconstructing it from
            // now| plus the basis -- which is a different source and gives a different answer.
            s.append("hold|side=").append(holding.side())
                    .append("|px=").append(holding.esPrice() == null
                            ? "" : money(holding.esPrice()))
                    .append("|vwap=").append(holding.esVwap() == null
                            ? "" : money(holding.esVwap()))
                    .append("|since=").append(hms(holding.sideSinceMs(), NY)).append("NY")
                    .append("|lastcross=").append(holding.lastCrossMs() == null ? ""
                            : hms(holding.lastCrossMs(), NY) + "NY")
                    .append("|crosses=").append(holding.crosses())
                    .append("|maxabove=").append(mins(holding.longestAboveMs()))
                    .append("|maxbelow=").append(mins(holding.longestBelowMs()))
                    .append("|anchor=").append(holding.anchorName())
                    .append("|observedfrom=").append(hms(holding.observedFromMs(), NY)).append("NY")
                    .append(holding.anchorChanges() > 0
                            ? "|anchorchanges=" + holding.anchorChanges() : "")
                    .append('\n');
            if (!holding.attempts().isEmpty()) {
                s.append("attempt|band|at|heldmin|open\n");
                int n = 0;
                for (VwapSideTracker.Attempt a : holding.attempts()) {
                    if (n++ >= 10) {
                        break;                       // C-66 prints ten
                    }
                    s.append("attempt|").append(a.band()).append('|')
                            .append(hms(a.atMs(), NY)).append('|').append(mins(a.heldMs()))
                            .append('|').append(a.open() ? "y" : "").append('\n');
                }
            }
        }

        // the Recent table
        if (recent == null || recent.isEmpty()) {
            s.append("recent|absent|no 30-second bars held for this window\n");
        } else {
            s.append("recent|rows=").append(recent.size()).append("|slot=30s|newest first\n");
            s.append(RecentRows.header()).append('\n');
            for (RecentRows.Row r : recent) {
                s.append(RecentRows.line(r, NY)).append('\n');
            }
        }

        // the five largest strikes
        if (board == null) {
            s.append("strikes|absent|no board yet\n");
        } else {
            s.append("strikes|cycle=").append(hms(board.cycleMs(), NY)).append("NY|spot=")
                    .append(money(board.spot())).append("|rows=").append(board.rowsPresent())
                    .append('/').append(board.rowsExpected()).append("|five largest by absolute netgamma\n");
            s.append("strike|netgamma|read\n");
            int n = 0;
            for (String line : largestFirst(board.lines())) {
                if (n++ >= 5) {
                    break;
                }
                s.append(line).append('\n');
            }
        }
        return cap(s.toString());
    }

    /** One VWAP line, in ES as published or translated for display where the basis permits. */
    /**
     * One VWAP line. <b>It never loses the last known figures</b> (see {@link VwapHeld}).
     *
     * <p>Three shapes, and the middle one is the requirement: a line whose source carries a figure
     * prints it; a line whose source has stopped carrying one prints the <b>last known</b> figures
     * with their own states, the time they were read, and the <b>new</b> state beside them, so the
     * reader can see both what was last true and that it is no longer being refreshed; a line that
     * has never had a figure says so and names the state that is standing in its way. The absence
     * is always reported — the held figures are never passed off as current.
     */
    private static String vwapLine(String label, VwapRead.Anchor a, BasisRead basis,
            VwapHeld held) {
        if (a != null && a.vwap() != null && a.vwap().value() != null) {
            return label + "|" + figures(a, basis) + "\n";
        }
        VwapHeld.Snapshot last = held == null ? null : held.of(label);
        if (last != null) {
            return label + "|" + figures(last.anchor(), basis)
                    + "|held=" + hms(last.atMs(), NY) + "NY"
                    + "|now=" + stateOf(a)
                    + "\n";
        }
        return label + "|absent|" + stateOf(a) + "\n";
    }

    /** The five cells and the deviation, in the order the legend names them. */
    private static String figures(VwapRead.Anchor a, BasisRead basis) {
        return cellOf(a.vwap(), basis)
                + "|" + cellOf(a.lower2(), basis)
                + "|" + cellOf(a.lower1(), basis)
                + "|" + cellOf(a.upper1(), basis)
                + "|" + cellOf(a.upper2(), basis)
                + "|sd=" + cellOf(a.stdDev(), null);
    }

    /**
     * What the source says instead of a figure, in its own words.
     *
     * <p>The record's {@code state} and {@code reason} are printed as published rather than
     * translated: {@code ABSENT/SOURCE_HAS_NO_VOLUME} is the cash index having no traded volume
     * and {@code WARMING_UP/WARMING_UP_WINDOW} is the morning, and a single word for both would
     * have hidden which one the owner was looking at.
     */
    private static String stateOf(VwapRead.Anchor a) {
        if (a == null || a.vwap() == null) {
            return "not published";
        }
        VwapRead.Figure f = a.vwap();
        String state = f.state() == null || f.state().isBlank() ? "no state" : f.state();
        return f.reason() == null || f.reason().isBlank() ? state : state + "/" + f.reason();
    }

    private static String cellOf(VwapRead.Figure f, BasisRead basis) {
        if (f == null) {
            return "";
        }
        if (basis == null) {
            return f.cell();
        }
        Double spx = basis.spxEquivalentOf(f.value());
        if (spx == null) {
            return f.cell();
        }
        return f.complete() ? money(spx) : money(spx) + "(" + f.cell().replaceAll("^[0-9.]+", "")
                .replaceAll("[()]", "") + ")";
    }

    /**
     * The feed-gap label, as a suffix for the {@code vwap|} headline line.
     *
     * <p>On the VWAP line specifically, because that is the figure the gap damages: the volume of
     * the missing window is not in it, and a reader who cannot see that would take a VWAP built on
     * half a session for the session's VWAP. Nothing is added on a normal day — an empty suffix,
     * not the word "none", because this line is read at a glance and a label that is always there
     * stops being read.
     */
    private static String gapSuffix(String feedGap) {
        return feedGap == null || feedGap.isBlank() ? "" : "|" + feedGap;
    }

    /** The rows with the largest absolute net gamma first; rows with none sort last. */
    private static List<String> largestFirst(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        out.sort((x, y) -> Double.compare(magnitude(y), magnitude(x)));
        return out;
    }

    private static double magnitude(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 2 || parts[1].isEmpty()) {
            return -1;                               // "norow" and empty cells sort last
        }
        String v = parts[1];
        double mult = v.endsWith("B") ? 1e9 : v.endsWith("M") ? 1e6 : 1.0;
        try {
            return Math.abs(Double.parseDouble(v.replaceAll("[BM]$", ""))) * mult;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Minutes, rounded, because a holding time to the second is noise on this page. */
    static String mins(long ms) {
        return String.valueOf(Math.round(ms / 60000.0));
    }

    /**
     * {@code /overnight}: the fixed 15-minute grid from the previous cash close, with its blanks.
     */
    public static String overnight(long nowMs, ReadablePage.Price last,
            ReadablePage.Overnight overnight) {
        StringBuilder s = new StringBuilder(4_096);
        s.append(firstLine(nowMs, last)).append('\n').append(LEGEND).append('\n');
        if (overnight == null) {
            s.append("overnight|absent|no reference price yet: the previous cash close is taken "
                    + "from the last print of the SPX index itself and none has been seen\n");
            return cap(s.toString());
        }
        int filled = 0;
        for (ReadablePage.Slot slot : overnight.slots()) {
            if (slot.filled()) {
                filled++;
            }
        }
        s.append("overnight|prevclose=").append(money(overnight.previousClose()))
                .append("|at=").append(hms(overnight.previousCloseMs(), NY)).append("NY")
                .append("|high=").append(overnight.high() == null ? "" : money(overnight.high()))
                .append("|low=").append(overnight.low() == null ? "" : money(overnight.low()))
                .append("|slots=").append(filled).append('/').append(overnight.slots().size())
                .append('\n');
        s.append("ny|ams|o|h|l|c|vol|basis\n");
        for (ReadablePage.Slot slot : overnight.slots()) {
            s.append(hms(slot.startMs(), NY)).append('|').append(hms(slot.startMs(), AMS))
                    .append('|');
            if (slot.filled()) {
                // C-118: a slot the platform never translated still shows its volume and says
                // noSPX, rather than printing as a slot that never had a bar.
                s.append(slot.hasPrices() ? money(slot.open()) : "").append('|')
                        .append(slot.hasPrices() ? money(slot.high()) : "").append('|')
                        .append(slot.hasPrices() ? money(slot.low()) : "").append('|')
                        .append(slot.hasPrices() ? money(slot.close()) : "")
                        .append('|').append(slot.volume() == null ? "" : slot.volume())
                        .append('|').append(slot.basisState() == null ? "" : slot.basisState());
            } else {
                s.append("|||||");          // an empty cell is nothing between two separators
            }
            s.append('\n');
        }
        return cap(s.toString());
    }


    /**
     * {@code /footprint} — every published level in the window, one line each, paged (C-66).
     *
     * <p><b>Nothing is dropped or merged to fit.</b> Where the levels exceed a page they continue on
     * the next one; the owner's rule is that no level is summarised by us, and C-66's answer to a
     * page that will not fit is to split it rather than thin it.
     */
    public static List<String> footprint(long nowMs, ReadablePage.Price last, Double spxSpot,
            List<FootprintByPrice.Level> levels, BasisRead basis) {
        List<String> header = new ArrayList<>();
        header.add(firstLine(nowMs, last));
        header.add(LEGEND + "|px=SPX-equivalent|espx=as published in ES|unk=unknown side");
        // A primitive double could not say "no spot", so a night with no board printed
        // spot=0.00 -- a price, and one the index has never traded at. The absence is reported.
        header.add("footprint|spot=" + (spxSpot == null ? "absent" : money(spxSpot))
                + "|levels=" + (levels == null ? 0 : levels.size())
                + "|basis=" + (basis == null ? "absent" : basis.cell()));
        header.add(FootprintByPrice.header());
        if (levels == null || levels.isEmpty()) {
            List<String> only = new ArrayList<>(header);
            only.add("absent|no level published for this window");
            return List.of(String.join("\n", only) + "\n");
        }
        List<String> rows = new ArrayList<>(levels.size());
        for (FootprintByPrice.Level l : levels) {
            rows.add(FootprintByPrice.line(l));
        }
        return paged(header, rows);
    }

    /**
     * {@code /recent-full} — the 30-second rows for the whole span since the previous cash close,
     * newest first, paged (C-66).
     *
     * <p>It exists so that <b>no fine row is lost when it leaves the last thirty minutes</b> of
     * {@code /now}. The night is about 540 slots, which is more than one page, so this is paged by
     * construction rather than only when it happens to overflow.
     */
    public static List<String> recentFull(long nowMs, ReadablePage.Price last,
            List<RecentRows.Row> rows) {
        List<String> header = new ArrayList<>();
        header.add(firstLine(nowMs, last));
        header.add(LEGEND + "|newest first|slot=30s");
        header.add("recentfull|rows=" + (rows == null ? 0 : rows.size()) + "|slot=30s");
        header.add(RecentRows.header());
        if (rows == null || rows.isEmpty()) {
            List<String> only = new ArrayList<>(header);
            only.add("absent|no 30-second bar held for this span");
            return List.of(String.join("\n", only) + "\n");
        }
        List<String> lines = new ArrayList<>(rows.size());
        for (RecentRows.Row r : rows) {
            lines.add(RecentRows.line(r, NY));
        }
        return paged(header, lines);
    }

    /**
     * {@code /index}: one line per page, with its write time and its size.
     *
     * <p>It exists so the reviewer can see at a glance which pages are current without opening
     * them, and so a page that stopped being written is visible rather than merely stale.
     */
    public static String index(long nowMs, ReadablePage.Price last, List<PageStat> pages) {
        StringBuilder s = new StringBuilder(1_024);
        s.append(firstLine(nowMs, last)).append('\n');
        s.append("page|written|chars\n");
        for (PageStat p : pages) {
            s.append(p.path()).append('|')
                    .append(p.writtenAtMs() <= 0 ? "" : hms(p.writtenAtMs(), AMS) + "AMS")
                    .append('|').append(p.chars()).append('\n');
        }
        return cap(s.toString());
    }

    /** One line of {@code /index}. */
    public record PageStat(String path, long writtenAtMs, int chars) {
    }

    /**
     * Split rows over pages so that nothing is ever dropped (C-66).
     *
     * @param header  the lines that open every page of the set (first line, legend, the once-only
     *                line above the table, the column header)
     * @param rows    every row, in the order they are to be read
     * @return one string per page, at least one
     */
    public static List<String> paged(List<String> header, List<String> rows) {
        List<String> pages = new ArrayList<>();
        StringBuilder head = new StringBuilder();
        for (String line : header) {
            head.append(line).append('\n');
        }
        String prefix = head.toString();
        StringBuilder current = new StringBuilder(prefix);
        int onPage = 0;
        for (String row : rows) {
            // +1 for the newline, and leave room for the continuation notice
            if (onPage > 0 && current.length() + row.length() + 1 + 80 > MAX_CHARS) {
                current.append("more|continued on the next page\n");
                pages.add(current.toString());
                current = new StringBuilder(prefix);
                onPage = 0;
            }
            current.append(row).append('\n');
            onPage++;
        }
        pages.add(current.toString());
        return pages;
    }

    /**
     * The bound, enforced. A page built by {@link #paged} never reaches this; the single pages
     * above can, and the cut is named rather than silent.
     */
    static String cap(String page) {
        if (page.length() <= MAX_CHARS) {
            return page;
        }
        String notice = "truncated|this page exceeded " + MAX_CHARS + " characters\n";
        int keep = MAX_CHARS - notice.length();
        int cut = page.lastIndexOf('\n', keep);
        return page.substring(0, cut > 0 ? cut + 1 : keep) + notice;
    }

    static String money(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static String hms(long ms, ZoneId zone) {
        return HMS.format(Instant.ofEpochMilli(ms).atZone(zone));
    }
}
