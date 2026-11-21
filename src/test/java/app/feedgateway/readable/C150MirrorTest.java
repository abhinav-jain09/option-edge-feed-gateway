package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-150 item 4</b>: the readable mirror of the What-If screen.
 *
 * <p><b>What these tests are for.</b> The mirror's whole value is that it cannot disagree with the
 * screen, so the tests that matter are the ones that would fail if a formatter drifted from the
 * deployed asset's own — not the ones that prove the page renders. Each figure below is the figure
 * {@code what-if.js} at web commit {@code 42eaf244} produces for the same input, read out of the
 * asset and asserted here, so a change to either side of the pair breaks a test rather than
 * quietly producing two readings of one board.
 *
 * <p><b>And the absences are the point.</b> R-IN-2 says a field the board does not carry is
 * counted as missing, never as zero. Half of what follows is an absent field asserting that it is
 * named and not drawn as a figure.
 */
class C150MirrorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 2026-11-20 16:00:08 ET, the cycle the map service valued its last board at. */
    private static final long CYCLE_MS = 1_795_208_408_000L;

    // ------------------------------------------------------------------ the asset's formatters

    @Test
    @DisplayName("net gamma is millions to one place with its sign, as the asset prints it")
    void gammaMatchesTheAsset() {
        assertEquals("+1.2M", WhatIfMirror.fmtGamma(1_234_567.0));
        assertEquals("-1.2M", WhatIfMirror.fmtGamma(-1_234_567.0));
        assertEquals("+0.0M", WhatIfMirror.fmtGamma(0.0), "zero is a measurement and prints as one");
        assertEquals("-", WhatIfMirror.fmtGamma(null), "absent is named, never zero (R-IN-2)");
    }

    @Test
    @DisplayName("strength is a percentage and is UNCLAMPED, which is the screen's own behaviour")
    void strengthIsUnclamped() {
        assertEquals("34%", WhatIfMirror.fmtStrength(0.3449));
        assertEquals("100%", WhatIfMirror.fmtStrength(1.0));
        // The asset clamps only the bar's width; fmtStrength multiplies and rounds. A strike
        // outside the 100-point window therefore reads above 100% beside a full bar. The mirror
        // reproduces the disagreement rather than tidying it: a mirror that corrected the screen
        // would make the screen's own defect invisible to the one reader looking for it.
        assertEquals("140%", WhatIfMirror.fmtStrength(1.4));
        assertEquals("-", WhatIfMirror.fmtStrength(null));
    }

    @Test
    @DisplayName("decay prints a magnitude and a direction word, never a signed product")
    void decayKeepsMagnitudeAndDirectionApart() {
        assertEquals("64.2k up", decay("{\"magnitude\":64200,\"direction\":\"RISING\"}"));
        assertEquals("64.2k down", decay("{\"magnitude\":64200,\"direction\":\"FALLING\"}"));
        // R-ENG-15: the magnitude carries no sign and the direction does. A missing direction
        // prints the magnitude alone rather than guessing one.
        assertEquals("64.2k", decay("{\"magnitude\":64200}"));
        // The asset's own threshold: one decimal place under ten, none from ten to a thousand.
        // Twelve is not under ten, which is what this pair pins — the first version of this test
        // asserted "12.0" and the mirror was right.
        assertEquals("8.5 up", decay("{\"magnitude\":8.5,\"direction\":\"RISING\"}"));
        assertEquals("12 up", decay("{\"magnitude\":12.0,\"direction\":\"RISING\"}"));
        assertEquals("999 up", decay("{\"magnitude\":999.4,\"direction\":\"RISING\"}"));
        assertEquals("1.0k up", decay("{\"magnitude\":1000,\"direction\":\"RISING\"}"));
        assertEquals("-", decay("{}"), "an absent magnitude is not a zero decay");
    }

    @Test
    @DisplayName("the band is its two EDGE PRICES named by the published thresholds (accepted screen, C-122)")
    void bandIsTwoDistances() {
        // re-aimed: the screen this mirrors (web 06a3f53b) prints the edge prices; an omitted
        // threshold is a question, never a claim
        assertEquals("d05? 6916.00 / d95? 7084.00", band("{\"lowerEdge\":6916,\"upperEdge\":7084}", 7000));
        assertEquals("d05 6950.00 / d95 7120.00", band("{\"lowerEdge\":6950,\"upperEdge\":7120,"
                + "\"lowerThreshold\":0.05,\"upperThreshold\":0.95}", 7000));
        assertEquals("d10 6950.00 / d90 7120.00", band("{\"lowerEdge\":6950,\"upperEdge\":7120,"
                + "\"lowerThreshold\":0.1,\"upperThreshold\":0.9}", 7000), "a 0.90 band is never named d95");
        assertEquals("-", band("{\"lowerEdge\":6950}", 7000), "one edge is not a band");
        assertEquals("-", band("{}", 7000));
    }

    @Test
    @DisplayName("the job-done level carries its signed distance from the pivot")
    void jobDoneCarriesItsDistance() {
        assertEquals("7010 (+10)", WhatIfMirror.fmtJobDone(7010.0, 7000.0));
        assertEquals("6990 (-10)", WhatIfMirror.fmtJobDone(6990.0, 7000.0));
        assertEquals("-", WhatIfMirror.fmtJobDone(null, 7000.0));
    }

    @Test
    @DisplayName("the age wording switches from seconds to minutes at ninety seconds")
    void ageWordingMatchesTheAsset() {
        assertEquals("30s ago", WhatIfMirror.ageText(30_000L));
        assertEquals("89s ago", WhatIfMirror.ageText(89_400L));
        assertEquals("2m ago", WhatIfMirror.ageText(90_000L));
        assertEquals("92m ago", WhatIfMirror.ageText(92L * 60_000L));
    }

    @Test
    @DisplayName("every figure is locale-independent, because a comma decimal breaks the contract")
    void figuresDoNotFollowTheHostLocale() {
        java.util.Locale saved = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertEquals("+1.2M", WhatIfMirror.fmtGamma(1_234_567.0));
            assertEquals("6963.70", WhatIfMirror.fixed(6963.7, 2));
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }

    @Test
    @DisplayName("the page names its chain and BOTH selection rules, because they can differ")
    void thePageNamesItsChainAndTheSelectionRules() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, fullRecord());
        // The screen reads the gateway's ACTIVE chain; this page reads the NEWEST cached one. With
        // one chain cached they are the same entry, with two they need not be, and a reviewer who
        // did not know that could read a figure off the wrong expiry and call the screen wrong.
        assertTrue(page.contains("chain|rendered=SPX 20261120"
                + "|this page selects=the newest cached chain key"
                + "|the screen selects=the gateway's active symbol and expiry"
                + "|compare these two before comparing any figure"), page);
        // and it comes BEFORE the status line, so it is read before any figure is
        assertTrue(page.indexOf("chain|rendered=") < page.indexOf("status|symbol="), page);
    }

    // ------------------------------------------------------------------------ the whole page

    @Test
    @DisplayName("a full record renders the screen's four panels in the screen's order")
    void fullRecordRendersEveryPanel() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS + 30_000L, fullRecord());

        assertTrue(page.contains("status|symbol=SPX|expiry=20261120|spot=6963.70"), page);
        assertTrue(page.contains("|state=LIVE|age=30s ago"), page);

        // re-aimed for C-159 / R-PIV-9: the screen DRAWS the ladder now, so the mirror says what
        // each drawing rule put on the panel instead of printing the old cards
        assertTrue(page.contains("pivot|panel=Pivot ladder|drawn=yes|axis=6858.70 to 7068.70"
                + "|spot=6963.70 dashed"), page);
        assertTrue(page.contains("pivot|up|arrow spot to 7000 in SELL|help zone 6963.70 to 7010"
                + "|job done 7010 solid black|dead zone 7010 to 7068.70"), page);
        assertTrue(page.contains("pivot|Upward pivot 7000 · SELL · job done at 7010 (+10)"), page);
        // C-165: the lean and a NONE force are behind the tap
        assertTrue(page.contains("pivot|behind the tap|standing-still lean SELL (its two side magnitudes "
                + "are not published on this block)|outside force none (the session has not produced two "
                + "completed 10-minute ranges yet)"), page);
        assertFalse(page.contains("dealer must"), "C-159: the drawn panel replaces the cards\n" + page);

        assertTrue(page.contains("unwind|panel=Ladder unwind|blocks=1"), page);
        assertTrue(page.contains("unwind|Ladder below|state=ARMED|rungs=3 (2 below, 1 above)"), page);
        assertTrue(page.contains("|expected bottom=6950 · +8.0M · by band"), page);
        assertTrue(page.contains("|unwind target=7025 · stopped by the band"), page);
        assertTrue(page.contains("|unwind bias=BUY · +4.0M"), page);
        assertTrue(page.contains("|touch/fail/reclaim=yes / no / no"), page);
        assertTrue(page.contains("reads=absorbed ON, buyer off, off-peak ON, thin below off"
                + "|strength=2|armed by=sellers_absorbed, trading_off_peak"), page);

        // re-aimed for C-159 item 3: the face carries six columns; decay and band are behind the tap
        assertTrue(page.contains("table|cols=Strike|Reading|Strength|Net gamma ($ per 1% move)"
                + "|Call delta|Put delta"), page);
        assertTrue(page.contains("row|7000|HOLD|bar 34%|+1.2M|-|-"), page);
        assertTrue(page.contains("tap|7000|call delta not published for this strike"
                + "|put delta not published for this strike|strength 34% of the largest within 100 points"
                + "|decay to close 64.2k up (index delta)|band edges d05? 6916.00 / d95? 7084.00"
                + "|band from the strike -84 / +84 points from the strike"
                + "|charm, vanna, levels, flips not drawn on this page yet"), page);
    }

    @Test
    @DisplayName("the highest strike is on top and the spot line sits between the strikes it falls between")
    void rowsAreOrderedWithTheSpotLineBetweenThem() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, fullRecord());
        int high = page.indexOf("row|7000|");
        int spot = page.indexOf("spot|6963.70");
        int low = page.indexOf("row|6950|");
        assertTrue(high >= 0 && spot >= 0 && low >= 0, page);
        assertTrue(high < spot, "the strike above price comes first\n" + page);
        assertTrue(spot < low, "the spot line precedes the strikes below it\n" + page);
        assertTrue(page.contains("table|face rows=2|behind show all=0|spot row drawn=yes"), page);
    }

    // ---------------------------------------------------------------------------- absences

    @Test
    @DisplayName("no record at all gives the screen's own empty sentence, not a blank page")
    void absentRecordSaysTheScreensOwnSentence() {
        for (String input : new String[]{null, "", "   ", "{not json", "[]"}) {
            String page = WhatIfMirror.renderWhole(CYCLE_MS, input);
            assertTrue(page.contains("record|present=no"), "for [" + input + "]\n" + page);
            assertTrue(page.contains("empty|No what-if board published for this chain yet."),
                    "for [" + input + "]\n" + page);
        }
    }

    @Test
    @DisplayName("a present=false envelope is an absence with its chain named, never a 404 shape")
    void presentFalseNamesTheChain() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS,
                "{\"present\":false,\"symbol\":\"SPX\",\"expiry\":\"20261123\"}");
        assertTrue(page.contains("record|present=no|symbol=SPX|expiry=20261123"), page);
    }

    @Test
    @DisplayName("an absent panel says it was not drawn and why, rather than drawing an empty one")
    void absentPanelsNameThemselves() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS,
                "{\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"spot\":6963.7,"
                        + "\"fingerprint\":{\"valuationTime\":" + CYCLE_MS + "},"
                        + "\"strikes\":[{\"strikeCents\":700000,\"board\":{\"netGex\":1.2e6}}]}");
        // re-aimed: the screen now SAYS an absent panel in words rather than drawing nothing (R-IN-2)
        // re-aimed for C-161: the record carries no reason for an absent block, so none is claimed
        assertTrue(page.contains("pivot|panel=Pivot ladder|drawn=no|Pivot ladder: not published this cycle."), page);
        assertTrue(page.contains("unwind|panel=Ladder unwind|drawn=no|Ladder unwind: not published this cycle."), page);
        assertTrue(page.contains("stair|panel=Staircase|drawn=no|Staircase: not published this cycle."), page);
        // and the row still renders, with each absent cell named
        assertTrue(page.contains("row|7000|-|no bar|+1.2M|-|-"), page);
    }

    @Test
    @DisplayName("a pivot block with neither pivot in reach draws no panel, as the screen does")
    void neitherPivotInReachDrawsNothing() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS,
                "{\"symbol\":\"SPX\",\"pivotLadder\":{\"pivotUp\":null,\"pivotDown\":null},"
                        + "\"strikes\":[]}");
        assertTrue(page.contains("pivot|panel=Pivot ladder|drawn=no|Pivot ladder: no strike in reach"), page);
    }

    @Test
    @DisplayName("one pivot in reach draws the panel and says 'none in reach' for the other")
    void onePivotDrawsTheOtherAsAbsent() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS,
                "{\"symbol\":\"SPX\",\"pivotLadder\":{\"pivotUp\":{\"strike\":7000,"
                        + "\"dealerAction\":\"SELL\"},\"pivotDown\":null},\"strikes\":[]}");
        // no spot on this record, so the cards stand in for the drawing, in C-164's ruled form
        assertTrue(page.contains("pivot|Upward pivot 7000 · SELL · job done at -"), page);
        assertTrue(page.contains("pivot|Downward pivot: none in reach"), page);
        assertFalse(page.contains("Downward pivot|strike=0"), "a zero strike reads as a level\n" + page);
    }

    @Test
    @DisplayName("an unmeasured tape read is a sentence, never four crosses")
    void unmeasuredReadsAreASentence() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS,
                "{\"symbol\":\"SPX\",\"ladders\":[{\"orientation\":\"DOWN\","
                        + "\"ladderState\":\"RADAR\"}],\"strikes\":[]}");
        assertTrue(page.contains("reads=tape reads not measured — the radar cannot arm"), page);
        assertTrue(page.contains("|expected bottom=none in reach"), page);
        assertTrue(page.contains("|unwind target=none"), page);
    }

    @Test
    @DisplayName("a ladder block with no orientation is dropped and SAID to be dropped")
    void anOrientationlessBlockIsDroppedOutLoud() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS,
                "{\"symbol\":\"SPX\",\"ladders\":[{\"ladderState\":\"ARMED\"}],\"strikes\":[]}");
        // R-LAD-16 / F-219: a ladder read on the wrong side inverts the trade it recommends, so
        // the asset drops it. A silent drop would leave the reviewer looking for a panel that was
        // never going to appear.
        assertTrue(page.contains("unwind|DROPPED|reason=no orientation on the block"), page);
        assertFalse(page.contains("Ladder above|state=ARMED"), page);
        assertFalse(page.contains("Ladder below|state=ARMED"), page);
    }

    @Test
    @DisplayName("no cycle time means no LIVE claim, which is the asset's own rule")
    void noCycleTimeMakesNoLiveClaim() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS,
                "{\"symbol\":\"SPX\",\"spot\":6963.7,\"strikes\":[]}");
        assertTrue(page.contains("|cycle=no cycle time|state=STALE|age=-"), page);
    }

    @Test
    @DisplayName("a strike with no strikeCents is not a row")
    void aRowWithNoStrikeIsNotARow() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS,
                "{\"symbol\":\"SPX\",\"strikes\":[{\"board\":{\"netGex\":1.0e6}},"
                        + "{\"strikeCents\":700000,\"board\":{\"netGex\":2.0e6}}]}");
        assertTrue(page.contains("table|face rows=1|"), page);
        assertTrue(page.contains("row|7000|-|no bar|+2.0M"), page);
    }

    // ------------------------------------------- the block that names what the screen ignores

    @Test
    @DisplayName("every record field no panel reads is listed, and the list comes from the record")
    void undrawnFieldsAreNamedFromTheRecordItself() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, fullRecord());
        assertTrue(page.contains("notdrawn|count="), page);
        // These are on the record and no panel of the screen reads one of them. The list is
        // derived from the record's own key set, so a field added to the contract appears here on
        // the next render instead of joining the ones nobody looks at.
        for (String field : new String[]{"levels", "flips", "charmTotal", "vannaDrift", "lean",
                                         "coverageConfidence", "oiQuality", "moveState"}) {
            assertTrue(page.contains("notdrawn|" + field + "|"),
                    "expected " + field + " to be named as undrawn\n" + page);
        }
        // and a field the screen DOES draw is never listed as undrawn
        for (String drawn : new String[]{"strikes", "pivotLadder", "ladders", "spot", "symbol",
                                         "expiry", "fingerprint"}) {
            assertFalse(page.contains("notdrawn|" + drawn + "|"),
                    drawn + " is drawn and must not be listed as undrawn\n" + page);
        }
    }

    @Test
    @DisplayName("an undrawn field's shape is named without printing a figure the screen never showed")
    void undrawnFieldsShowShapeNotFigures() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, fullRecord());
        assertTrue(page.contains("notdrawn|levels|list of 121"), page);
        assertTrue(page.contains("notdrawn|charmTotal|block{h15,h30,h60}"), page);
        assertTrue(page.contains("notdrawn|moveState|null"), page);
        assertTrue(page.contains("notdrawn|coverageConfidence|value"), page);
        // the figure itself is NOT on the page: this block says what exists, not what it says, and
        // a reviewer must not be able to mistake it for something the owner can see
        assertFalse(page.contains("notdrawn|coverageConfidence|value|0.82"), page);
    }

    @Test
    @DisplayName("the page is text, within the readable page's budget, and ends in a newline")
    void thePageIsPlainTextAndBounded() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, fullRecord());
        assertTrue(page.endsWith("\n"), "every line is a line");
        assertTrue(page.length() < ReadablePage.MAX_CHARS,
                "one part must fit the page budget: " + page.length());
        assertFalse(page.contains("<"), "no markup: the reviewer's web read runs no script");
    }

    @Test
    @DisplayName("whatif is a page the renderer answers to, and /index lists it")
    void theRendererAnswersToTheName() {
        assertTrue(ReadableBoardRenderer.PAGES.contains("whatif"),
                "the page must be reachable by name: " + ReadableBoardRenderer.PAGES);
        assertEquals(ReadableBoardRenderer.PAGES.size() - 1,
                ReadableBoardRenderer.PAGES.indexOf("index"),
                "index lists the others and so stays last");
    }

    // ------------------------------------------------------------------------------ fixtures

    /** Shaped after the live record: the fields the screen draws, and the ones it ignores. */
    private static String fullRecord() {
        StringBuilder levels = new StringBuilder("[");
        for (int i = 0; i < 121; i++) {
            levels.append(i == 0 ? "" : ",").append("{\"price\":").append(6900 + i).append('}');
        }
        levels.append(']');
        return "{"
                + "\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"spot\":6963.7,"
                + "\"fingerprint\":{\"valuationTime\":" + CYCLE_MS + "},"
                + "\"strikes\":["
                + "  {\"strikeCents\":700000,\"board\":{\"netGex\":1234567,"
                + "     \"holdOrAccelerate\":\"HOLD\",\"strength\":0.3449},"
                + "   \"decayToExpiry\":{\"magnitude\":64200,\"direction\":\"RISING\"},"
                + "   \"deltaBand\":{\"lowerEdge\":6916,\"upperEdge\":7084}},"
                + "  {\"strikeCents\":695000,\"board\":{\"netGex\":-2000000,"
                + "     \"holdOrAccelerate\":\"ACCELERATE\",\"strength\":1.0},"
                + "   \"decayToExpiry\":{\"magnitude\":1200,\"direction\":\"FALLING\"},"
                + "   \"deltaBand\":{\"lowerEdge\":6880,\"upperEdge\":7020}}"
                + "],"
                + "\"pivotLadder\":{"
                + "  \"pivotUp\":{\"strike\":7000,\"netGamma\":1234567,\"jobDoneLevel\":7010,"
                + "    \"dealerAction\":\"SELL\"},"
                + "  \"pivotDown\":{\"strike\":6950,\"netGamma\":-2000000,\"jobDoneLevel\":6940,"
                + "    \"dealerAction\":\"BUY\"},"
                + "  \"standingStillLean\":\"SELL\",\"outsideForce\":\"NONE\","
                + "  \"outsideForceReason\":\"the session has not produced two completed "
                + "10-minute ranges yet\"},"
                + "\"ladders\":[{\"orientation\":\"DOWN\",\"ladderState\":\"ARMED\","
                + "  \"rungCount\":3,\"rungsBelow\":2,\"rungsAbove\":1,"
                + "  \"lowRung\":6900,\"highRung\":6990,\"ladderPosition\":\"BELOW\","
                + "  \"expectedBottom\":{\"strike\":6950,\"absGamma\":8000000,"
                + "    \"reachableBy\":\"BAND\"},"
                + "  \"unwindTarget\":{\"strike\":7025,\"stoppedBy\":\"BAND_LIMIT\"},"
                + "  \"unwindBias\":{\"label\":\"BUY\",\"value\":4000000},"
                + "  \"touch\":true,\"fail\":false,\"reclaim\":false,"
                + "  \"radarReads\":{\"sellersAbsorbed\":true,\"aggressiveBuyer\":false,"
                + "    \"tradingOffPeak\":true,\"liquidityBelowGone\":false,"
                + "    \"rejectionStrength\":2,"
                + "    \"armedBy\":\"sellers_absorbed,trading_off_peak\"}}],"
                // on the record, drawn by no panel of the screen
                + "\"levels\":" + levels + ","
                + "\"flips\":[{\"strike\":6975,\"direction\":\"UP\"}],"
                + "\"charmTotal\":{\"h15\":1.0,\"h30\":2.0,\"h60\":3.0},"
                + "\"vannaDrift\":-120.0,"
                + "\"lean\":[{\"distancePoints\":10,\"upPush\":1.0,\"downPush\":2.0}],"
                + "\"bounceTracker\":[],"
                + "\"coverageConfidence\":0.82,"
                + "\"oiQuality\":\"LIVE\","
                + "\"moveState\":null"
                + "}";
    }

    private static String decay(String json) {
        try {
            return WhatIfMirror.fmtDecay(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String band(String json, double strike) {
        try {
            return WhatIfMirror.fmtBand(MAPPER.readTree(json), strike);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- C-150: the panels the page gained (web 06a3f53b), mirrored --------------------------

    private static final String C150 = "{\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"spot\":6963.7,"
            + "\"fingerprint\":{\"valuationTime\":" + CYCLE_MS + "},"
            + "\"boardHold\":{\"heldFromCycleMs\":1,\"ageMs\":542000,\"reason\":\"INPUT_QUIET\",\"strikesSoFar\":6},"
            + "\"strikes\":[{\"strikeCents\":700000,\"board\":{\"netGex\":-2.39e9,\"holdOrAccelerate\":\"ACCELERATE\",\"strength\":0.84}},"
            + "{\"strikeCents\":690000,\"board\":{\"netGex\":-2.85e9,\"holdOrAccelerate\":\"ACCELERATE\",\"strength\":1.0}},"
            + "{\"strikeCents\":697000,\"board\":{\"netGex\":0,\"excluded\":true,\"excludedReason\":\"BAND_NOT_BRACKETED\"}},"
            + "{\"strikeCents\":720000,\"board\":{\"netGex\":9e9,\"strength\":1.4}}],"
            + "\"staircases\":[{\"orientation\":\"UP\",\"stairState\":\"WATCH\",\"topRung\":7050,\"lowRung\":6950,"
            + "\"rungCount\":2,\"topStep\":{\"strike\":7050},\"pinTarget\":{\"strike\":7000,\"stoppedBy\":\"BALANCE\"},"
            + "\"stepsTaken\":0,\"phrase\":{\"text\":\"Stair above. First step 7050.\",\"arbitrated\":false},\"opposed\":true}],"
            + "\"strikeDeltas\":{\"strikes\":{\"700000\":{\"callDelta\":0.4528,\"putReason\":\"PARITY_IV\"}}}}";

    @Test
    @DisplayName("C-40/C-116: a held board reads HELD with the board's age and why, never LIVE")
    void heldBoardIsNeverLive() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS + 5000, C150);
        assertTrue(page.contains("|state=HELD|age=board 542s old"), page);
        assertTrue(page.contains("status|held because no gamma record arrived (6 strikes so far)"), page);
        assertTrue(!page.contains("state=LIVE"), page);
    }

    @Test
    @DisplayName("the accepted screen's biggest strike, within 100 points, from the service's own scale")
    void biggestStrikeWithinOneHundredPoints() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, C150);
        // 7200 carries 1.4 but is 236 points out; 6900 is the top of the scale inside the window
        assertTrue(page.contains("status|Biggest strike within 100 points: 6900"), page);
    }

    @Test
    @DisplayName("R-ID-2: the line's slot says the signal layer is not built, never NO TRADE")
    void theLineSlotSaysItIsNotBuilt() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, C150);
        assertTrue(page.contains("line|No line: the signal layer that produces it is not built yet."), page);
        assertTrue(!page.contains("NO TRADE"), page);
    }

    @Test
    @DisplayName("the staircase panel, the phrase as worded, and OPPOSED")
    void staircaseAndPhrase() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, C150);
        assertTrue(page.contains("stair|Stair above|state=WATCH|rungs=2 (6950 - 7050)|top step=7050"
                + "|pin target=7000 · pull turns back|steps taken=0"), page);
        assertTrue(page.contains("stair|Stair above|phrase=\"Stair above. First step 7050.\" "
                + "not spoken|OPPOSED"), page);
        assertFalse(page.contains("another pattern"), "C-162: the record names no pattern\n" + page);
    }

    @Test
    @DisplayName("C-160 CONTRACT SHAPE ONLY (no live record carries excluded:true); C-163 a missing delta is a dash, its reason in words behind the tap")
    void exclusionsAndDeltaReasons() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, C150);
        // C-159: an excluded row carries its reason and NO figure
        assertTrue(page.contains("row|6970|excluded: BAND_NOT_BRACKETED"), page);
        assertFalse(page.contains("+0.0M"), page);
        assertTrue(page.contains("|0.453|-"), page);
        assertTrue(page.contains("|put delta not published: volatility taken from the other side of the strike|"), page);
        assertFalse(page.contains("|PARITY_IV"), "C-163: no producer code on the face\n" + page);
        assertTrue(!page.contains("note|Call delta and Put delta are empty"), page);
    }

    // ---- C-159: the face, the drawn ladder and the summary line (web ee25ec26), mirrored -----

    @Test
    @DisplayName("the stale bound is the screen's 150 s, so the two never disagree between 90 and 150 s")
    void staleBoundMatchesTheScreen() {
        String rec = "{\"symbol\":\"SPX\",\"spot\":6963.7,\"fingerprint\":{\"valuationTime\":"
                + CYCLE_MS + "},\"strikes\":[]}";
        assertEquals(150_000L, WhatIfMirror.STALE_AFTER_MS);
        assertTrue(WhatIfMirror.renderWhole(CYCLE_MS + 120_000L, rec).contains("|state=LIVE|age=2m ago"),
                "the screen says LIVE at 120 s, and so must this page");
        assertTrue(WhatIfMirror.renderWhole(CYCLE_MS + 151_000L, rec).contains("|state=STALE|"));
    }

    @Test
    @DisplayName("C-159 item 5: the summary line, published words only, no dealer action")
    void summaryLineMatchesTheScreen() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS + 5000, C159);
        assertTrue(page.contains("summary|SPX 6963.70 · board HELD 62s old: the gamma round is still "
                + "filling, 43 strikes so far · biggest strike within 100 points 6900, ACCELERATE · "
                + "pivot 7000 both sides · ladder below WATCH · stair above WATCH · no line: signal "
                + "layer not built"), page);
        String summary = page.lines().filter(l -> l.startsWith("summary|")).findFirst().orElseThrow();
        assertFalse(summary.matches(".*(BUY|SELL|dealer).*"), summary);
    }

    @Test
    @DisplayName("R-PIV-9: the drawn ladder as text, rule by rule, with the force on its own side")
    void drawnLadderSaysEachRule() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, C159);
        assertTrue(page.contains("pivot|bars=7075 green · 7050 red · 7000 red · 6975 red · 6950 green · 6900 red (longest) · 6875 green"),
                "the excluded 6970 is not a bar\n" + page);
        assertTrue(page.contains("pivot|up|arrow spot to 7000 in BUY|help zone 6963.70 to 7082"
                + "|job done 7082 solid black|dead zone 7082 to 7087.00"), page);
        assertTrue(page.contains("pivot|down|arrow spot to 7000 in SELL|help zone 6963.70 to 6919"
                + "|job done 6919 solid black|dead zone 6919 to 6858.70|hatched: outside force TREND for 4m"),
                "the force sits over the DOWN help zone only\n" + page);
        // the UP line is exactly this, with NO hatch: the force is DOWN
        assertTrue(page.lines().anyMatch(l -> l.equals("pivot|up|arrow spot to 7000 in BUY"
                + "|help zone 6963.70 to 7082|job done 7082 solid black|dead zone 7082 to 7087.00")), page);
    }

    @Test
    @DisplayName("C-159 item 4: four strikes each side of spot plus the named ones; the rest after, as show all")
    void faceWindowAndShowAll() {
        String page = WhatIfMirror.renderWhole(CYCLE_MS, C159);
        // 12 strikes: seven at or above spot (6970 is above 6963.70), five below; four nearest each side
        assertTrue(page.contains("table|face rows=8|behind show all=4|spot row drawn=yes"), page);
        for (String k : new String[] {"7000", "6975", "6970", "6950", "6900", "6875", "6850"}) {
            assertTrue(page.contains("row|" + k + "|"), "face row " + k + "\n" + page);
        }
        assertTrue(page.contains("showall|7150|"), page);
        assertFalse(page.contains("row|7150|"), page);
    }

    private static final String C159 = "{\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"spot\":6963.7,"
            + "\"fingerprint\":{\"valuationTime\":" + CYCLE_MS + "},"
            + "\"boardHold\":{\"ageMs\":62000,\"reason\":\"ROUND_NOT_COMPLETE\",\"strikesSoFar\":43},"
            + "\"strikes\":["
            + strike(715000, 1e8, "HOLD", 0.03) + ',' + strike(710000, 1e8, "HOLD", 0.03) + ','
            + strike(707500, 1e8, "HOLD", 0.03) + ',' + strike(705000, -3.1e8, "ACCELERATE", 0.13) + ','
            + strike(700000, -2.39e9, "ACCELERATE", 0.84) + ',' + strike(697500, -4.937e8, "ACCELERATE", 0.18) + ','
            + "{\"strikeCents\":697000,\"board\":{\"netGex\":0,\"excluded\":true,\"excludedReason\":\"BAND_NOT_BRACKETED\"}},"
            + strike(695000, 1.2e8, "HOLD", 0.05) + ',' + strike(690000, -2.85e9, "ACCELERATE", 1.0) + ','
            + strike(687500, 1e8, "HOLD", 0.03) + ',' + strike(685000, 1e8, "HOLD", 0.03) + ','
            + strike(682500, 1e8, "HOLD", 0.03) + "],"
            + "\"pivotLadder\":{\"pivotUp\":{\"strike\":7000,\"jobDoneLevel\":7082,\"dealerAction\":\"BUY\"},"
            + "\"pivotDown\":{\"strike\":7000,\"jobDoneLevel\":6919,\"dealerAction\":\"SELL\"},"
            + "\"standingStillLean\":\"SELL\",\"outsideForce\":\"TREND\",\"outsideForceSide\":\"DOWN\","
            + "\"outsideForceSinceMinutes\":4},"
            + "\"ladders\":[{\"orientation\":\"DOWN\",\"ladderState\":\"WATCH\"}],"
            + "\"staircases\":[{\"orientation\":\"UP\",\"stairState\":\"WATCH\"}]}";

    private static String strike(int cents, double gex, String reading, double strength) {
        return "{\"strikeCents\":" + cents + ",\"board\":{\"netGex\":" + gex + ",\"holdOrAccelerate\":\""
                + reading + "\",\"strength\":" + strength + "}}";
    }

    @Test
    @DisplayName("C-164: no 'dealer must' anywhere on the mirror, drawn or carded")
    void noDealerMustAnywhere() {
        for (String rec : new String[] {fullRecord(), C159, "{\"symbol\":\"SPX\",\"pivotLadder\":{\"pivotUp\":"
                + "{\"strike\":7000,\"dealerAction\":\"SELL\"}},\"strikes\":[]}"}) {
            assertFalse(WhatIfMirror.renderWhole(CYCLE_MS, rec).contains("dealer must"), rec);
        }
    }

    @Test
    @DisplayName("C-166: the bar is clamped at 100% and a null strength draws no bar")
    void strengthBarClampsAndNullDrawsNone() {
        assertEquals("bar 84%", WhatIfMirror.strengthBar(0.84));
        assertEquals("bar 100%", WhatIfMirror.strengthBar(1.4));
        assertEquals("no bar", WhatIfMirror.strengthBar(null));
    }
}
