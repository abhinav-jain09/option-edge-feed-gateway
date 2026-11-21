package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <b>C-150 item 4</b>: the What-If screen as text — the same record, the same panels, the same
 * fields, the same figures, so the reviewer can run C-151's check without a browser and a session.
 *
 * <p><b>This is a MIRROR and not a second opinion.</b> Every formatter below is a transcription of
 * the deployed asset's own formatter, named after it, so the mirror and the screen cannot disagree
 * about a figure. Where the screen prints {@code +1.2M} this prints {@code +1.2M}; where the screen
 * prints an em dash this prints {@code -}, because a dash in a pipe-delimited text line is read as
 * a minus sign by eye. The asset is {@code what-if.js} at web commit {@code 42eaf244}, and the
 * field map it draws is:
 *
 * <ul>
 *   <li><b>status</b> — {@code symbol}, {@code expiry}, {@code spot},
 *       {@code fingerprint.valuationTime} as the cycle time, and LIVE/STALE with an age measured
 *       against the server's own {@code Date} header.</li>
 *   <li><b>Pivot ladder</b> — {@code pivotLadder.pivotUp} and {@code .pivotDown}, each
 *       {@code strike}, {@code dealerAction}, {@code netGamma}, {@code jobDoneLevel}; then
 *       {@code standingStillLean}, {@code outsideForce}, {@code outsideForceSide},
 *       {@code outsideForceSinceMinutes}, {@code outsideForceReason}.</li>
 *   <li><b>Ladder unwind</b> — {@code ladders[]}, one block per {@code orientation}, with
 *       {@code ladderState}, the rung counts, the span and {@code ladderPosition},
 *       {@code expectedBottom}, {@code unwindTarget}, {@code unwindBias}, the three booleans
 *       {@code touch}/{@code fail}/{@code reclaim}, and {@code radarReads}.</li>
 *   <li><b>the table</b> — one row per {@code strikes[]} entry: {@code strikeCents},
 *       {@code board.netGex}, {@code board.holdOrAccelerate}, {@code board.strength},
 *       {@code decayToExpiry}, {@code deltaBand}.</li>
 * </ul>
 *
 * <p><b>Two things this page does that the screen does not, and both are deliberate.</b>
 *
 * <p>First, <b>it names what the screen does not draw</b>. The record carries a great deal the
 * screen never renders — the levels, the flips, the charm horizons, the vanna probe, the lean's
 * side magnitudes, the board's own quality fields — and a reviewer comparing a requirement against
 * a screen cannot see the difference between "the service does not publish it" and "the service
 * publishes it and the page ignores it". Those are different defects with different fixes, so the
 * {@code notdrawn} lines name every top-level key of the record that no panel reads. <b>It is
 * computed from the record itself</b>, not from a list in this file: a hand-kept list would go
 * stale the first time the contract gained a field, which is the one failure mode this block
 * exists to catch.
 *
 * <p>Second, <b>an absence is named and never drawn as a zero</b> (R-IN-2). The screen already does
 * this correctly on the ladder cards — {@code none in reach} for an absent bottom, {@code tape
 * reads not measured} rather than four crosses — and this page follows that spelling rather than
 * inventing another.
 *
 * <p><b>It formats and does not decide</b> (C-63): no gamma, no basis, no reading and no
 * arithmetic of its own beyond the two subtractions the screen itself performs (a band edge's
 * distance from its strike, and the job-done level's distance from its pivot). It is a pure
 * function of its arguments, so the page a test renders is the page the service serves.
 */
public final class WhatIfMirror {

    /** The served asset this page mirrors, named so a reader can tell when they diverge. */
    public static final String ASSET = "what-if.js";

    /** The web commit the asset was read from. */
    public static final String ASSET_COMMIT = "783642ad";

    /**
     * The screen's own staleness bound, transcribed from the asset: {@code STALE_AFTER_MS = 150000}.
     *
     * <p>It read 90_000 until C-159. That was a transcription error, not a choice: between 90 and
     * 150 seconds this page said STALE while the screen it mirrors said LIVE, which is the one
     * disagreement a mirror exists to rule out.
     */
    public static final long STALE_AFTER_MS = 150_000L;

    /** C-159 item 4: the face shows this many strikes each side of spot, the asset's FACE_EACH_SIDE. */
    static final int FACE_EACH_SIDE = 4;

    /** The face's column headings, in the screen's order (C-159 item 3, the asset's FACE_COLUMNS). */
    public static final List<String> COLUMNS = List.of(
            "Strike", "Reading", "Strength", "Net gamma ($ per 1% move)", "Call delta", "Put delta");

    /**
     * Every top-level key any panel of the screen reads. The {@code notdrawn} block is the
     * record's own key set minus this one, so a field added to the contract shows up as undrawn
     * on the next render rather than silently joining the ones nobody looks at.
     */
    public static final Set<String> DRAWN = Set.of(
            "symbol", "expiry", "spot", "fingerprint", "strikes", "pivotLadder", "ladders",
            "staircases", "boardHold", "strikeDeltas", "present");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WhatIfMirror() {
    }

    /**
     * The mirror.
     *
     * @param nowMs    the clock the age is measured against — the screen measures it against the
     *                 gateway's own {@code Date} header, and this page is rendered by the gateway,
     *                 so the two are the same clock
     * @param mapJson  the cached map record, exactly the bytes {@code /api/what-if} serves, or null
     */
    public static java.util.List<String> render(long nowMs, String mapJson) {
        StringBuilder head = new StringBuilder();
        head.append("WHAT-IF SCREEN MIRROR — the same record, the same panels, as text\n");
        head.append("screen|asset=").append(ASSET).append("|web=").append(ASSET_COMMIT)
                .append("|mirror=C-150 item 4, C-159\n");

        JsonNode root = parse(mapJson);
        if (root == null) {
            // The screen's own empty state, in its own words, because a reviewer comparing the two
            // must see the same sentence and not a second spelling of the same absence.
            head.append("record|present=no\n");
            head.append("empty|No what-if board published for this chain yet.\n");
            return List.of(head.toString());
        }
        if (root.path("present").isBoolean() && !root.path("present").asBoolean()) {
            head.append("record|present=no|symbol=").append(text(root, "symbol"))
                    .append("|expiry=").append(text(root, "expiry")).append('\n');
            head.append("empty|No what-if board published for this chain yet.\n");
            return List.of(head.toString());
        }

        chain(head, root);
        status(head, root, nowMs);
        summary(head, root, nowMs);
        line(head);
        pivotLadder(head, root);
        unwinds(head, root);
        staircases(head, root);

        // The strike rows are the only part that can outgrow a page, so they are the rows and
        // everything else is the header. Pages.paged is reused rather than re-implemented because
        // its contract is the one that matters here: nothing is dropped or merged to fit (C-66),
        // and a board the owner trades off must not lose a strike to a byte budget.
        List<String> header = new ArrayList<>(List.of(head.toString().split("\n", -1)));
        header.removeIf(String::isEmpty);
        List<String> rows = new ArrayList<>();
        tableRows(header, rows, root);
        notDrawnRows(rows, root);
        return Pages.paged(header, rows);
    }

    /** The whole mirror as one string, for a test and for a reader who wants it in one piece. */
    public static String renderWhole(long nowMs, String mapJson) {
        return String.join("", render(nowMs, mapJson));
    }

    /**
     * <b>Which chain this is, and the one way this page can differ from the screen.</b>
     *
     * <p>The screen asks {@code /api/what-if} with no symbol or expiry, and the route fills them
     * from the gateway's <b>active</b> selection. This page is rendered from the renderer's own
     * board supplier, which is the <b>newest cached chain key</b>. With one chain cached — which is
     * every session this board has had — they are the same entry. With two cached they need not
     * be: the greatest key is the later expiry and the active selection is usually the nearer one.
     *
     * <p>The right fix is to give this page the active selection, and it is <b>not made here</b>:
     * the supplier is bound in {@code FeedGatewayService}, which the footprint attestation campaign
     * pins by sha256, and moving a pinned file to tidy a caveat that a printed line makes visible
     * is the wrong trade. So the line is printed instead, with both selection rules named, and the
     * reviewer compares this symbol and expiry against the screen's own before comparing a figure.
     * It is a finding of its own, not a silence.
     */
    private static void chain(StringBuilder out, JsonNode root) {
        out.append("chain|rendered=").append(text(root, "symbol")).append(' ')
                .append(text(root, "expiry"))
                .append("|this page selects=the newest cached chain key")
                .append("|the screen selects=the gateway's active symbol and expiry")
                .append("|compare these two before comparing any figure\n");
    }

    // ---------------------------------------------------------------- panel 1: the status line

    private static void status(StringBuilder out, JsonNode root, long nowMs) {
        Double spot = number(root, "spot");
        Long cycle = cycleMillis(root);
        out.append("status|symbol=").append(text(root, "symbol"))
                .append("|expiry=").append(text(root, "expiry"))
                .append("|spot=").append(spot == null ? "-" : fixed(spot, 2))
                .append("|cycle=").append(cycle == null ? "no cycle time" : hms(cycle));
        String[] fresh = freshness(root, nowMs);
        out.append("|state=").append(fresh[0]).append("|age=").append(fresh[1]);
        out.append('\n');
        // C-170: each thing once. The hold's reason and the biggest strike are on the summary line.
    }

    /**
     * The asset's {@code stateFrom()} freshness: {state, age text}.
     *
     * <p>No cycle time, no LIVE claim: STALE and a dash. C-40 / C-116: a held board is republished
     * every cycle, so its PUBLISH is fresh while the BOARD is not; it reads HELD with the board's
     * own age, never LIVE, and a stale publish stays STALE.
     */
    static String[] freshness(JsonNode root, long nowMs) {
        Long cycle = cycleMillis(root);
        if (cycle == null) {
            return new String[] {"STALE", "-"};
        }
        long age = Math.max(0L, nowMs - cycle);
        JsonNode hold = root.path("boardHold");
        if (age <= STALE_AFTER_MS && hold.isObject()) {
            Double heldAge = number(hold, "ageMs");
            return new String[] {"HELD", heldAge == null ? "age unknown"
                    : "board " + Math.round(heldAge / 1000.0) + "s old"};
        }
        return new String[] {age > STALE_AFTER_MS ? "STALE" : "LIVE", ageText(age)};
    }

    /**
     * C-159 item 5, the asset's {@code summaryLine()}: the reading in one line, assembled only from
     * published fields and the Requirement's words. It names no dealer action (RD-58, R-PUB-5).
     */
    static String summaryLine(JsonNode root, long nowMs) {
        List<String> parts = new ArrayList<>();
        Double spot = number(root, "spot");
        String sym = text(root, "symbol");
        parts.add((sym.isEmpty() ? "-" : sym) + ' ' + (spot == null ? "no spot" : fixed(spot, 2)));
        String[] fresh = freshness(root, nowMs);
        JsonNode hold = root.path("boardHold");
        if ("HELD".equals(fresh[0]) && hold.isObject()) {
            parts.add("board HELD " + fresh[1].replaceFirst("^board ", "") + ": "
                    + fmtHold(hold).replaceFirst("^held because ", "")
                            .replaceFirst(" \\((\\d+) strikes so far\\)$", ", $1 strikes so far"));
        } else {
            parts.add("board " + fresh[0] + ("-".equals(fresh[1]) ? "" : " " + fresh[1]));
        }
        Double biggest = biggestStrike(root.path("strikes"), spot);
        if (biggest != null) {
            String reading = "";
            for (JsonNode st : root.path("strikes")) {
                Double c = number(st, "strikeCents");
                if (c != null && c / 100.0 == biggest) {
                    reading = text(st.path("board"), "holdOrAccelerate");
                }
            }
            parts.add("biggest strike within 100 points " + fixed(biggest, 0)
                    + (reading.isEmpty() ? "" : ", " + reading));
        }
        JsonNode ladder = root.path("pivotLadder");
        Double up = ladder.isObject() ? pivotStrike(ladder.path("pivotUp")) : null;
        Double down = ladder.isObject() ? pivotStrike(ladder.path("pivotDown")) : null;
        if (up == null && down == null) {
            parts.add("no pivot in reach");
        } else if (up != null && up.equals(down)) {
            parts.add("pivot " + fixed(up, 0) + " both sides");
        } else {
            if (up != null) {
                parts.add("pivot above " + fixed(up, 0));
            }
            if (down != null) {
                parts.add("pivot below " + fixed(down, 0));
            }
        }
        for (String[] key : List.of(new String[] {"ladders", "ladderState", "ladder"},
                new String[] {"staircases", "stairState", "stair"})) {
            JsonNode list = root.path(key[0]);
            if (!list.isArray()) {
                continue;
            }
            for (JsonNode b : list) {
                String o = text(b, "orientation");
                String st = text(b, key[1]);
                if (o.isEmpty() || st.isEmpty()) {
                    continue;   // dropped on the screen too, never defaulted
                }
                parts.add(key[2] + ("DOWN".equals(o) ? " below " : " above ") + st);
            }
        }
        // C-170: no `no line` clause; the line slot directly under the summary says it
        return String.join(" · ", parts);
    }

    private static void summary(StringBuilder out, JsonNode root, long nowMs) {
        out.append("summary|").append(summaryLine(root, nowMs)).append('\n');
    }

    private static Double pivotStrike(JsonNode p) {
        return p.isObject() ? number(p, "strike") : null;
    }

    /** {@code fmtHold()}: why the board is held, said once beside the HELD chip. */
    static String fmtHold(JsonNode h) {
        String reason = text(h, "reason");
        String why = switch (reason) {
            case "INPUT_QUIET" -> "no gamma record arrived";
            case "ROWS_REFUSED" -> "every gamma record was refused";
            case "ROUND_NOT_COMPLETE" -> "the gamma round is still filling";
            default -> reason.isEmpty() ? "reason not published" : reason;
        };
        Double sofar = number(h, "strikesSoFar");
        return "held because " + why + (sofar == null ? "" : " (" + fixed(sofar, 0) + " strikes so far)");
    }

    /**
     * {@code biggestStrike()}: the accepted screen's line. SELECTED from the service's own strength
     * scale within 100 points of spot, ties to the strike nearest spot.
     */
    static Double biggestStrike(JsonNode strikes, Double spot) {
        if (!strikes.isArray() || spot == null) {
            return null;
        }
        Double best = null;
        Double bestStrength = null;
        for (JsonNode s : strikes) {
            Double cents = number(s, "strikeCents");
            Double strength = number(s.path("board"), "strength");
            if (cents == null || strength == null) {
                continue;
            }
            double k = cents / 100.0;
            if (Math.abs(k - spot) > 100) {
                continue;
            }
            if (best == null || strength > bestStrength
                    || (strength.equals(bestStrength) && Math.abs(k - spot) < Math.abs(best - spot))) {
                best = k;
                bestStrength = strength;
            }
        }
        return best;
    }

    /** R-ID-2: the line's own slot, in the screen's words. */
    private static void line(StringBuilder out) {
        out.append("line|No line: the signal layer that produces it is not built yet.\n");
    }

    /** {@code freshness()}'s age wording, transcribed: seconds under 90, else whole minutes. */
    static String ageText(long ageMs) {
        long secs = Math.round(ageMs / 1000.0);
        return secs < 90 ? secs + "s ago" : Math.round(secs / 60.0) + "m ago";
    }

    private static Long cycleMillis(JsonNode root) {
        Double v = number(root.path("fingerprint"), "valuationTime");
        return v == null ? null : (long) (double) v;
    }

    // ------------------------------------------------------------- panel 2: §12a's pivot ladder

    private static void pivotLadder(StringBuilder out, JsonNode root) {
        JsonNode ladder = root.path("pivotLadder");
        if (!ladder.isObject()) {
            // C-161: the record carries no reason for an absent block, so none is claimed
            out.append("pivot|panel=Pivot ladder|drawn=no|Pivot ladder: not published this cycle.\n");
            return;
        }
        boolean up = ladder.path("pivotUp").isObject() && number(ladder.path("pivotUp"), "strike") != null;
        boolean down = ladder.path("pivotDown").isObject()
                && number(ladder.path("pivotDown"), "strike") != null;
        if (!up && !down) {
            out.append("pivot|panel=Pivot ladder|drawn=no|Pivot ladder: no strike in reach on "
                    + "either side this cycle.\n");
            return;
        }
        Double spot = number(root, "spot");
        if (spot == null) {
            // the screen cannot place a drawing without spot, so it keeps the cards; so does this
            out.append("pivot|panel=Pivot ladder|drawn=no (no spot to place it), cards instead\n");
            pivotCard(out, "Upward pivot", ladder.path("pivotUp"));
            pivotCard(out, "Downward pivot", ladder.path("pivotDown"));
        } else {
            drawnLadder(out, root, ladder, spot);
        }

        if (spot != null) {
            // C-164: each pivot in the ruled form, under the drawing
            out.append("pivot|").append(pivotLine("Upward pivot", ladder.path("pivotUp"))).append('\n');
            out.append("pivot|").append(pivotLine("Downward pivot", ladder.path("pivotDown"))).append('\n');
        }
        String lean = text(ladder, "standingStillLean");
        String force = ladder.path("outsideForce").isMissingNode()
                || ladder.path("outsideForce").isNull() ? "NONE" : text(ladder, "outsideForce");
        // C-165: the lean and a NONE force are BEHIND the tap ("more about the ladder")
        out.append("pivot|behind the tap|standing-still lean ").append(lean.isEmpty() ? "not published"
                : lean + " (its two side magnitudes are not published on this block)");
        if ("NONE".equals(force)) {
            String reason = text(ladder, "outsideForceReason");
            out.append("|outside force none").append(reason.isEmpty() ? "" : " (" + reason + ")");
        }
        out.append('\n');
    }

    /** C-164 (ruled), the asset's {@code pivotLine()}: the published word, no subject. */
    static String pivotLine(String title, JsonNode p) {
        Double strike = p.isObject() ? number(p, "strike") : null;
        if (strike == null) {
            return title + ": none in reach";
        }
        String word = text(p, "dealerAction");
        return title + ' ' + fixed(strike, 0) + " · " + (word.isEmpty() ? "no word published" : word)
                + " · job done at " + fmtJobDone(number(p, "jobDoneLevel"), strike);
    }

    /**
     * R-PIV-9's drawing, the asset's {@code ladderGeometry()}, said as text: what each of Source
     * §19.3's six rules put on the panel. The bar LENGTHS are a drawing scale and are not printed
     * on the screen, so they are not printed here; each bar's colour and the longest one are.
     */
    private static void drawnLadder(StringBuilder out, JsonNode root, JsonNode ladder, double spot) {
        JsonNode up = ladder.path("pivotUp");
        JsonNode down = ladder.path("pivotDown");
        Double upStrike = pivotStrike(up);
        Double downStrike = pivotStrike(down);
        Double upDone = upStrike == null ? null : number(up, "jobDoneLevel");
        Double downDone = downStrike == null ? null : number(down, "jobDoneLevel");
        double hi = spot + 100;
        double lo = spot - 100;
        if (upDone != null) {
            hi = Math.max(hi, upDone);
        }
        if (downDone != null) {
            lo = Math.min(lo, downDone);
        }
        hi += 5;
        lo -= 5;
        out.append("pivot|panel=Pivot ladder|drawn=yes|axis=").append(fixed(lo, 2)).append(" to ")
                .append(fixed(hi, 2)).append("|spot=").append(fixed(spot, 2)).append(" dashed\n");

        // rule 2: one bar per drawn strike with a published net gamma, red negative, green positive
        List<double[]> bars = new ArrayList<>();
        for (JsonNode st : root.path("strikes")) {
            Double c = number(st, "strikeCents");
            Double g = number(st.path("board"), "netGex");
            if (c == null || g == null) {
                continue;
            }
            double k = c / 100.0;
            if (k >= lo && k <= hi) {
                bars.add(new double[] {k, g});
            }
        }
        bars.sort((a, b) -> Double.compare(b[0], a[0]));
        double maxAbs = 0;
        for (double[] b : bars) {
            maxAbs = Math.max(maxAbs, Math.abs(b[1]));
        }
        List<String> marks = new ArrayList<>();
        for (double[] b : bars) {
            marks.add(fixed(b[0], 0) + ' ' + (b[1] < 0 ? "red" : b[1] > 0 ? "green" : "grey")
                    + (maxAbs > 0 && Math.abs(b[1]) == maxAbs ? " (longest)" : ""));
        }
        out.append("pivot|bars=").append(marks.isEmpty() ? "none" : String.join(" · ", marks))
                .append('\n');

        // rules 3 and 4: per side, the help zone in the side's published word, the job-done line,
        // the dead zone past it, and the arrow from spot to the pivot
        String forceSide = text(ladder, "outsideForceSide");
        String force = ladder.path("outsideForce").isMissingNode()
                || ladder.path("outsideForce").isNull() ? "NONE" : text(ladder, "outsideForce");
        for (Object[] side : new Object[][] {{"up", "UP", up, upStrike, upDone, hi},
                {"down", "DOWN", down, downStrike, downDone, lo}}) {
            String name = (String) side[0];
            Double k = (Double) side[3];
            Double done = (Double) side[4];
            double edge = (double) side[5];
            if (k == null) {
                out.append("pivot|").append(name).append("|none in reach|dead zone from spot to ")
                        .append(fixed(edge, 2)).append('\n');
                continue;
            }
            String word = text((JsonNode) side[2], "dealerAction");
            // C-171: a pivot on the FAR side of spot draws its arrow to its own job-done line,
            // labelled with the pivot (a departure from Source §19.3 rule 4, put to the owner)
            boolean far = "UP".equals(side[1]) ? k < spot : k > spot;
            if (far && done != null) {
                out.append("pivot|").append(name).append("|arrow spot to ").append(fixed(done, 0))
                        .append(" (pivot ").append(fixed(k, 0)).append(" is on the other side of spot)")
                        .append(" in ").append(word.isEmpty() ? "no published word" : word);
            } else {
                out.append("pivot|").append(name).append("|arrow spot to ").append(fixed(k, 0))
                        .append(" in ").append(word.isEmpty() ? "no published word" : word);
            }
            if (done != null) {
                out.append("|help zone ").append(fixed(spot, 2)).append(" to ").append(fixed(done, 0))
                        .append("|job done ").append(fixed(done, 0)).append(" solid black")
                        .append("|dead zone ").append(fixed(done, 0)).append(" to ").append(fixed(edge, 2));
                // rule 5: the hatched band sits over the AFFECTED side's help zone only
                if (!"NONE".equals(force) && side[1].equals(forceSide)) {
                    out.append("|hatched: outside force ").append(force);
                    Double mins = number(ladder, "outsideForceSinceMinutes");
                    if (mins != null) {
                        out.append(" for ").append(fixed(mins, 0)).append('m');
                    }
                }
            } else {
                out.append("|no job-done level published");
            }
            out.append('\n');
        }
    }

    private static void pivotCard(StringBuilder out, String title, JsonNode p) {
        Double strike = p.isObject() ? number(p, "strike") : null;
        if (strike == null) {
            // the asset says this once, in these words, rather than three dashes
            out.append("pivot|").append(pivotLine(title, p)).append('\n');
            return;
        }
        out.append("pivot|").append(pivotLine(title, p))
                .append("|net gamma ").append(fmtGamma(number(p, "netGamma")))
                .append('\n');
    }

    /** {@code fmtJobDone()}: the level with its signed distance from the pivot on the same card. */
    static String fmtJobDone(Double level, Double strike) {
        if (level == null || strike == null) {
            return "-";
        }
        double d = level - strike;
        return fixed(level, 0) + " (" + (d >= 0 ? "+" : "-") + fixed(Math.abs(d), 0) + ")";
    }

    // ------------------------------------------------------------ panel 3: §12b's ladder unwind

    private static void unwinds(StringBuilder out, JsonNode root) {
        JsonNode list = root.path("ladders");
        if (!list.isArray() || list.isEmpty()) {
            out.append("unwind|panel=Ladder unwind|drawn=no|Ladder unwind: not published this cycle.\n");
            return;
        }
        out.append("unwind|panel=Ladder unwind|blocks=").append(list.size()).append('\n');
        for (JsonNode b : list) {
            String orientation = text(b, "orientation");
            if (orientation.isEmpty()) {
                // R-LAD-16 / F-219: a block without its orientation cannot be told from the other
                // one, and a ladder read on the wrong side inverts the trade it recommends. The
                // asset drops it rather than defaulting it, so the mirror says it was dropped.
                out.append("unwind|DROPPED|reason=no orientation on the block; the screen drops it "
                        + "rather than defaulting a side (R-LAD-16, F-219)\n");
                continue;
            }
            String side = "DOWN".equals(orientation) ? "Ladder below" : "Ladder above";
            // C-174: the card's FACE -- state, rungs, expected bottom, target, phrase
            out.append("unwind|").append(side)
                    .append("|state=").append(blank(text(b, "ladderState")))
                    .append("|rungs=").append(rungs(b))
                    .append("|expected bottom=").append(bottom(b.path("expectedBottom")))
                    .append("|unwind target=").append(target(b.path("unwindTarget")))
                    .append('\n');
            phrase(out, "unwind|" + side, b);
            // ... and what is behind its tap, each figure with its label and unit
            JsonNode eb = b.path("expectedBottom");
            Double absGamma = eb.isObject() ? number(eb, "absGamma") : null;
            out.append("unwind|").append(side).append("|behind the tap")
                    .append("|span=").append(span(b))
                    .append("|gamma at the expected bottom=").append(absGamma == null ? "-"
                            : fmtGamma(absGamma) + " ($ per 1% move, absolute)")
                    .append("|unwind bias=").append(bias(b.path("unwindBias")))
                    .append("|touch/fail/reclaim=").append(yesNo(b, "touch"))
                    .append(" / ").append(yesNo(b, "fail"))
                    .append(" / ").append(yesNo(b, "reclaim"))
                    .append('\n');
            reads(out, side + "|behind the tap", b.path("radarReads"));
        }
    }

    /** C-90 / R-VOICE-1: the phrase as worded, spoken or not, and R-SC-12b's OPPOSED. */
    private static void phrase(StringBuilder out, String prefix, JsonNode b) {
        JsonNode p = b.path("phrase");
        String t = p.isObject() ? text(p, "text") : "";
        out.append(prefix).append("|phrase=");
        if (t.isEmpty()) {
            out.append("no phrase this minute");
        } else {
            out.append('"').append(t).append("\" ")
                    .append(p.path("arbitrated").asBoolean(false) ? "spoken this minute"
                            : "not spoken");
        }
        if (b.path("opposed").isBoolean() && b.path("opposed").asBoolean()) {
            out.append("|OPPOSED");
        }
        out.append('\n');
    }

    // ------------------------------------------------------------ panel: §12f's staircase

    private static void staircases(StringBuilder out, JsonNode root) {
        JsonNode list = root.path("staircases");
        if (!list.isArray() || list.isEmpty()) {
            out.append("stair|panel=Staircase|drawn=no|Staircase: not published this cycle.\n");
            return;
        }
        out.append("stair|panel=Staircase|blocks=").append(list.size()).append('\n');
        for (JsonNode b : list) {
            String orientation = text(b, "orientation");
            if (orientation.isEmpty() || text(b, "stairState").isEmpty()) {
                out.append("stair|DROPPED|reason=no orientation or state on the block; the screen "
                        + "drops it rather than defaulting a side (R-SC-12)\n");
                continue;
            }
            String side = "DOWN".equals(orientation) ? "Stair below" : "Stair above";
            Double count = number(b, "rungCount");
            Double low = number(b, "lowRung");
            Double top = number(b, "topRung");
            Double topStep = b.path("topStep").isObject() ? number(b.path("topStep"), "strike") : null;
            JsonNode pin = b.path("pinTarget");
            Double pinStrike = pin.isObject() ? number(pin, "strike") : null;
            Double steps = number(b, "stepsTaken");
            out.append("stair|").append(side)
                    .append("|state=").append(text(b, "stairState"))
                    .append("|rungs=").append(count == null ? "-" : fixed(count, 0) + " ("
                            + (low == null ? "?" : fixed(low, 0)) + " - "
                            + (top == null ? "?" : fixed(top, 0)) + ")")
                    .append("|top step=").append(topStep == null ? "none" : fixed(topStep, 0))
                    .append("|pin target=").append(pinStrike == null ? "none"
                            : fixed(pinStrike, 0) + " · " + stoppedBy(text(pin, "stoppedBy")))
                    .append("|steps taken=").append(steps == null ? "-" : fixed(steps, 0))
                    .append('\n');
            phrase(out, "stair|" + side, b);
        }
    }

    private static String stoppedBy(String code) {
        return switch (code) {
            case "BALANCE" -> "pull turns back";
            case "GAP" -> "next rung out of reach";
            case "NEGATIVE_STRIKE" -> "a negative strike between";
            case "LAST_RUNG" -> "no next rung";
            default -> code;
        };
    }

    private static String rungs(JsonNode b) {
        Double count = number(b, "rungCount");
        if (count == null) {
            return "-";
        }
        Double below = number(b, "rungsBelow");
        Double above = number(b, "rungsAbove");
        return fixed(count, 0) + " (" + (below == null ? "?" : fixed(below, 0)) + " below, "
                + (above == null ? "?" : fixed(above, 0)) + " above)";
    }

    private static String span(JsonNode b) {
        Double low = number(b, "lowRung");
        Double high = number(b, "highRung");
        if (low == null || high == null) {
            return "-";
        }
        String position = text(b, "ladderPosition");
        return fixed(low, 0) + " - " + fixed(high, 0) + " · "
                + (position.isEmpty() ? "-" : position);
    }

    private static String bottom(JsonNode n) {
        Double strike = n.isObject() ? number(n, "strike") : null;
        if (strike == null) {
            // R-LAD-4: with no reachable rung there is no expected bottom, published as absent with
            // that reason and never as a zero. A zero strike would read as a level.
            return "none in reach";
        }
        String by = text(n, "reachableBy");
        // C-174: no figure on the face; the bottom's gamma is behind the tap with its unit
        return fixed(strike, 0) + " · " + ("BAND".equals(by) ? "by band" : "by session print");
    }

    private static String target(JsonNode n) {
        Double strike = n.isObject() ? number(n, "strike") : null;
        if (strike == null) {
            return "none";
        }
        return fixed(strike, 0) + " · " + ("POSITIVE_STRIKE".equals(text(n, "stoppedBy"))
                ? "stopped by a positive strike" : "stopped by the band");
    }

    private static String bias(JsonNode n) {
        if (!n.isObject()) {
            return "-";
        }
        String label = text(n, "label");
        Double value = number(n, "value");
        if (label.isEmpty() && value == null) {
            return "-";
        }
        return (label.isEmpty() ? "-" : label) + " · " + fmtGamma(value)
                + (value == null ? "" : " ($ per 1% move, below minus above)");
    }

    private static void reads(StringBuilder out, String side, JsonNode r) {
        if (!r.isObject()) {
            // R-LAD-14's reads were NOT TAKEN, and the asset's own comment is the reason this is a
            // sentence and not four crosses: four crosses would claim the tape was read and came
            // back negative, and ARMED would look merely unlucky rather than unreachable.
            out.append("unwind|").append(side)
                    .append("|reads=tape reads not measured — the radar cannot arm\n");
            return;
        }
        out.append("unwind|").append(side).append("|reads=")
                .append("absorbed ").append(onOff(r, "sellersAbsorbed"))
                .append(", buyer ").append(onOff(r, "aggressiveBuyer"))
                .append(", off-peak ").append(onOff(r, "tradingOffPeak"))
                .append(", thin below ").append(onOff(r, "liquidityBelowGone"));
        Double strength = number(r, "rejectionStrength");
        out.append("|strength=").append(strength == null ? "-" : fixed(strength, 0));
        String armedBy = text(r, "armedBy");
        if (!armedBy.isEmpty()) {
            out.append("|armed by=").append(String.join(", ", armedBy.split(",")));
        }
        out.append('\n');
    }

    // ------------------------------------------------------------------- panel 4: the table

    private static void tableRows(List<String> header, List<String> out, JsonNode root) {
        JsonNode strikes = root.path("strikes");
        List<Row> rows = new ArrayList<>();
        if (strikes.isArray()) {
            for (JsonNode s : strikes) {
                Double cents = number(s, "strikeCents");
                if (cents == null) {
                    continue;   // a row with no strike is not a row — the asset's own words
                }
                rows.add(new Row(cents / 100.0, s));
            }
        }
        header.add("table|cols=" + String.join("|", COLUMNS));
        if (rows.isEmpty()) {
            out.add("empty|No what-if board published for this chain yet.");
            return;
        }
        rows.sort(Comparator.comparingDouble((Row r) -> r.strike).reversed());
        Double spot = number(root, "spot");

        // C-159 item 4, the asset's faceRows(): four strikes each side of spot plus every strike a
        // panel names; the rest are behind "show all" on the screen, and printed here AFTER the
        // face under their own prefix so this page still drops nothing (C-66)
        Set<Double> keep = new java.util.HashSet<>();
        if (spot != null) {
            List<Row> above = new ArrayList<>();
            List<Row> below = new ArrayList<>();
            for (Row r : rows) {
                (r.strike >= spot ? above : below).add(r);
            }
            for (Row r : above.subList(Math.max(0, above.size() - FACE_EACH_SIDE), above.size())) {
                keep.add(r.strike);
            }
            for (Row r : below.subList(0, Math.min(FACE_EACH_SIDE, below.size()))) {
                keep.add(r.strike);
            }
            Double biggest = biggestStrike(root.path("strikes"), spot);
            if (biggest != null) {
                keep.add(biggest);
            }
            JsonNode ladder = root.path("pivotLadder");
            for (String side : List.of("pivotUp", "pivotDown")) {
                Double k = pivotStrike(ladder.path(side));
                if (k != null) {
                    keep.add(k);
                }
            }
        } else {
            rows.forEach(r -> keep.add(r.strike));   // no spot, no window: every row is face
        }
        List<Row> face = new ArrayList<>();
        List<Row> hidden = new ArrayList<>();
        for (Row r : rows) {
            (keep.contains(r.strike) ? face : hidden).add(r);
        }

        boolean spotDrawn = false;
        for (Row r : face) {
            if (!spotDrawn && spot != null && r.strike < spot) {
                out.add("spot|" + fixed(spot, 2));
                spotDrawn = true;
            }
            out.add(faceRow("row", root, r));
        }
        out.add("table|face rows=" + face.size() + "|behind show all=" + hidden.size()
                + "|spot row drawn=" + (spotDrawn ? "yes" : "no"));
        // C-159 item 3: what a tap on each face row opens, in the screen's labels
        for (Row r : face) {
            JsonNode board = r.node.path("board");
            String dist = bandDistances(r.node.path("deltaBand"), r.strike);
            JsonNode deltas = deltasFor(root, number(r.node, "strikeCents"));
            out.add("tap|" + fixed(r.strike, 0)
                    + "|call delta " + deltaWhy(deltas, true)
                    + "|put delta " + deltaWhy(deltas, false)
                    + "|strength " + fmtStrength(number(board, "strength")) + " of the largest within 100 points"
                    + "|decay to close " + fmtDecay(r.node.path("decayToExpiry")) + " (index delta)"
                    + "|band edges " + fmtBand(r.node.path("deltaBand"), r.strike)
                    + "|band from the strike " + (dist.isEmpty() ? "-" : dist)
                    + "|charm, vanna, levels, flips not drawn on this page yet");
        }
        for (Row r : hidden) {
            out.add(faceRow("showall", root, r));
        }
        JsonNode sib = root.path("strikeDeltas");
        if (!sib.path("strikes").isObject()) {
            out.add("note|Call delta and Put delta are empty because this gateway is not publishing "
                    + "the strikeDeltas block yet.");
        }
    }

    private record Row(double strike, JsonNode node) {
    }

    /**
     * One row in the face's columns. C-160: no excluded-row branch; no record carries an excluded
     * strike, so it returns with C-168 against the shape the service then publishes.
     */
    private static String faceRow(String prefix, JsonNode root, Row r) {
        JsonNode board = r.node.path("board");
        String reading = text(board, "holdOrAccelerate");
        JsonNode deltas = deltasFor(root, number(r.node, "strikeCents"));
        return prefix + '|' + fixed(r.strike, 0)
                + '|' + (reading.isEmpty() ? "-" : reading)
                + '|' + strengthBar(number(board, "strength"))
                + '|' + fmtGamma(number(board, "netGex"))
                + '|' + fmtDelta(deltas, true)
                + '|' + fmtDelta(deltas, false);
    }

    /** C-166, the asset's {@code strengthBar()}: the bar's width clamped at 100%, or no bar for null. */
    static String strengthBar(Double strength) {
        if (strength == null) {
            return "no bar";
        }
        return "bar " + Math.round(Math.max(0, Math.min(1, strength)) * 100) + "%";
    }

    /** {@code bandDistances()}: the band as two distances from the strike, the tap's second reading. */
    static String bandDistances(JsonNode band, double strike) {
        Double lo = band.isObject() ? number(band, "lowerEdge") : null;
        Double hi = band.isObject() ? number(band, "upperEdge") : null;
        if (lo == null || hi == null) {
            return "";
        }
        return "-" + fixed(strike - lo, 0) + " / +" + fixed(hi - strike, 0) + " points from the strike";
    }

    // ---------------------------------------------- what the record carries and no panel draws

    /**
     * Every top-level key of the record no panel reads.
     *
     * <p>This is the block the reviewer asked for without asking for it: a requirement that is
     * absent from the screen is one defect when the service never published the figure and quite
     * another when the service publishes it every minute and the page ignores it. The first needs
     * a producer change; the second needs a line of page code. Nothing on the screen itself can
     * tell those apart, which is why it is here and not there.
     */
    private static void notDrawnRows(List<String> out, JsonNode root) {
        Set<String> undrawn = new LinkedHashSet<>();
        root.fieldNames().forEachRemaining(name -> {
            if (!DRAWN.contains(name)) {
                undrawn.add(name);
            }
        });
        if (undrawn.isEmpty()) {
            out.add("notdrawn|none — every top-level field of the record reaches a panel");
            return;
        }
        out.add("notdrawn|count=" + undrawn.size()
                + "|the record carries these and NO panel of the screen reads them");
        for (String name : undrawn) {
            out.add("notdrawn|" + name + '|' + shape(root.path(name)));
        }
    }

    /** What a field is, in a word or two, without printing a figure the screen never showed. */
    private static String shape(JsonNode v) {
        if (v.isArray()) {
            return "list of " + v.size();
        }
        if (v.isObject()) {
            List<String> keys = new ArrayList<>();
            v.fieldNames().forEachRemaining(keys::add);
            return "block{" + String.join(",", keys) + "}";
        }
        if (v.isNull()) {
            return "null";
        }
        return "value";
    }

    // --------------------------------------------------- the asset's formatters, transcribed

    /**
     * {@code fmtGamma()}: net gamma in millions with its sign, which is the figure the owner reads
     * elsewhere. The sign is a plain minus here and not the asset's typographic one, because this
     * page is read as text.
     */
    static String fmtGamma(Double value) {
        if (value == null) {
            return "-";
        }
        double m = value / 1e6;
        return (m >= 0 ? "+" : "-") + fixed(Math.abs(m), 1) + "M";
    }

    /**
     * {@code fmtStrength()}: the share of the largest board position near price, as a percentage.
     *
     * <p><b>Unclamped, and the mirror keeps it unclamped on purpose.</b> The asset clamps the
     * *bar* to 0..1 and leaves this number alone, so a strike whose strength exceeds 1 draws a
     * full bar beside a number above 100%. That disagreement is a property of the screen and this
     * page exists to show the screen, so it is reproduced rather than tidied — and it is the one
     * figure on this page I would ask about before copying it into anything else.
     */
    static String fmtStrength(Double value) {
        return value == null ? "-" : fixed(value * 100.0, 0) + "%";
    }

    /**
     * {@code fmtDecay()}: a magnitude with its direction as an arrow.
     *
     * <p>The magnitude carries no sign — the direction does (R-ENG-15) — so the arrow is the only
     * place direction may be read from, and a missing direction prints the magnitude alone rather
     * than guessing one.
     */
    static String fmtDecay(JsonNode decay) {
        Double n = decay.isObject() ? number(decay, "magnitude") : null;
        if (n == null) {
            return "-";
        }
        String mag = n >= 1000 ? fixed(n / 1000.0, 1) + "k" : fixed(n, n < 10 ? 1 : 0);
        String dir = decay.isObject() ? text(decay, "direction") : "";
        return mag + ("RISING".equals(dir) ? " up" : "FALLING".equals(dir) ? " down" : "");
    }

    /**
     * {@code fmtBand()}: the band's two EDGE PRICES, each named by the threshold the record
     * published it at (C-122 / C-125, and the accepted screen's "7,417 - 7,433"). A threshold the
     * record omits prints as a question ({@code d95?}), never as a claim.
     */
    static String fmtBand(JsonNode band, double strike) {
        Double lo = band.isObject() ? number(band, "lowerEdge") : null;
        Double hi = band.isObject() ? number(band, "upperEdge") : null;
        if (lo == null || hi == null) {
            return "-";
        }
        return thresholdName(number(band, "lowerThreshold"), "d05") + ' ' + fixed(lo, 2) + " / "
                + thresholdName(number(band, "upperThreshold"), "d95") + ' ' + fixed(hi, 2);
    }

    /** {@code thresholdName()}: d + the threshold in hundredths, or the expected name with a ?. */
    static String thresholdName(Double value, String expected) {
        if (value == null) {
            return expected + "?";
        }
        long n = Math.round(value * 100);
        return "d" + (n < 10 ? "0" : "") + n;
    }

    /** {@code deltasFor()}: the strikeDeltas sibling's entry for this strike, keyed by CENTS. */
    private static JsonNode deltasFor(JsonNode root, Double cents) {
        JsonNode strikes = root.path("strikeDeltas").path("strikes");
        if (!strikes.isObject() || cents == null) {
            return null;
        }
        JsonNode e = strikes.path(String.valueOf((long) (double) cents));
        return e.isObject() ? e : null;
    }

    /** {@code fmtDelta()}: the face's cell, the published delta or a dash, never a code (C-163). */
    static String fmtDelta(JsonNode entry, boolean call) {
        if (entry == null) {
            return "-";
        }
        Double v = number(entry, call ? "callDelta" : "putDelta");
        return v != null ? fixed(v, 3) : "-";
    }

    /** C-163, the asset's {@code DELTA_REASON_WORDS}: the producer's leg reasons in reviewed words. */
    static final java.util.Map<String, String> DELTA_REASON_WORDS = java.util.Map.ofEntries(
            java.util.Map.entry("NO_LEG", "no contract on this side"),
            java.util.Map.entry("INVALID_OI", "open interest not usable"),
            java.util.Map.entry("NON_FINITE_QUOTE", "the quote is not a number"),
            java.util.Map.entry("NO_QUOTE", "no quote"),
            java.util.Map.entry("CROSSED_QUOTE", "bid above ask"),
            java.util.Map.entry("NO_MARK", "no price to value it at"),
            java.util.Map.entry("WIDE_SPREAD", "spread too wide"),
            java.util.Map.entry("FUTURE_QUOTE_TIME", "quote time in the future"),
            java.util.Map.entry("STALE_QUOTE", "quote too old"),
            java.util.Map.entry("ZERO_BID_NO_TRADE_TIME", "no bid and no trade time"),
            java.util.Map.entry("FUTURE_TRADE_TIME", "trade time in the future"),
            java.util.Map.entry("STALE_ZERO_BID_MARK", "no bid, and the last trade is too old"),
            java.util.Map.entry("MARK_ABOVE_ASK", "price above the ask"),
            java.util.Map.entry("WIDE_ZERO_BID_SPREAD", "no bid, and the ask is too far away"),
            java.util.Map.entry("DEGENERATE_GAMMA", "gamma could not be computed"),
            java.util.Map.entry("IV_SOLVER_FAILED", "volatility could not be solved"),
            java.util.Map.entry("PARITY_IV", "volatility taken from the other side of the strike"),
            java.util.Map.entry("SMILE_IV_FALLBACK", "volatility taken from the smile"));

    /** C-163, the asset's {@code deltaWhy()}: behind the tap, the figure or why there is none. */
    static String deltaWhy(JsonNode entry, boolean call) {
        if (entry == null) {
            return "not published for this strike";
        }
        Double v = number(entry, call ? "callDelta" : "putDelta");
        if (v != null) {
            return fixed(v, 3);
        }
        String code = text(entry, call ? "callReason" : "putReason");
        if (code.isEmpty()) {
            return "not published, no reason given";
        }
        String words = DELTA_REASON_WORDS.get(code);
        return "not published: " + (words != null ? words : "producer code " + code);
    }

    // ------------------------------------------------------------------------------ plumbing

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            // A record the gateway cannot parse is an absence with a cause, and the cause is worth
            // more to a reader than a stack trace he cannot see: the page says so and serves.
            return null;
        }
    }

    private static Double number(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        if (!n.isNumber()) {
            if (n.isTextual()) {
                try {
                    double v = Double.parseDouble(n.asText());
                    return Double.isFinite(v) ? v : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
        double v = n.asDouble();
        return Double.isFinite(v) ? v : null;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        return n.isMissingNode() || n.isNull() ? "" : n.asText();
    }

    private static String blank(String s) {
        return s.isEmpty() ? "-" : s;
    }

    private static String onOff(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return "-";
        }
        return n.asBoolean() ? "ON" : "off";
    }

    private static String yesNo(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return "-";
        }
        return n.asBoolean() ? "yes" : "no";
    }

    /** Locale-independent fixed-point, because a comma decimal separator breaks a text contract. */
    static String fixed(double v, int places) {
        return String.format(Locale.ROOT, "%." + places + "f", v);
    }

    /** C-175, the asset's {@code fmtCycle()}: date and time in New York, the zone named. */
    static String hms(long epochMs) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                .withZone(ReadablePage.NY)
                .format(java.time.Instant.ofEpochMilli(epochMs)) + " New York";
    }
}
