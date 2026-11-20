package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every fixture here is a record Dev actually published on 2026-11-19/20, not a shape I imagined.
 * The raw ES cents and the SPX-equivalent cents are deliberately DIFFERENT in them, so a test can
 * tell which one the decoder read.
 */
class OvernightDecodersTest {

    /**
     * A real bar, read off {@code es.futures.footprint.bars} at 23:16:05Z. Raw ES close is
     * 718975 (7189.75) and the SPX-equivalent close is 717145 (7171.45) — about 19 points apart,
     * which is the basis.
     */
    private static final String REAL_BAR = """
            {"schemaVersion":6,"symbol":"ES.v.0","timeframe":"1m","widthMs":60000,
             "sessionDate":"2026-11-20","levelTicks":1,"tickCents":25,
             "observations":{"barStartMs":1795130100000,"barStart":"2026-11-19T23:15:00.000Z",
              "barEndMs":1795130160000,"complete":true,"barQuality":"READABLE",
              "openCents":718550,"highCents":719000,"lowCents":718550,"closeCents":718975,
              "spxEqOpenCents":716720,"spxEqHighCents":717170,"spxEqLowCents":716720,
              "spxEqCloseCents":717145,"spxEqBasisState":"ANCHORED",
              "volume":301,"delta":63,"trades":158}}""";

    /** A real price record, read off {@code underlying.spx.price} at 23:35:18Z. */
    private static final String REAL_PRICE = """
            {"schemaVersion":1,"symbol":"SPX","eventTime":"2026-11-19T23:35:18.049Z",
             "price":"7158.450000000000000000","size":null,"source":"ES_BASIS_DERIVED",
             "quality":"LIVE_ES_BASIS","canonicalSpot":"7158.450000000000000000",
             "spotEventTimeNs":1795131318048000000,"epochSeq":5022,
             "spotQuality":"LIVE_ES_BASIS"}""";

    @Test
    @DisplayName("the real bar decodes to its SPX-EQUIVALENT prices, never the raw ES ones")
    void theRealBarDecodesToSpxEquivalent() {
        OvernightGrid.Bar bar = OvernightDecoders.bar(REAL_BAR);
        assertNotNull(bar);
        assertEquals(1_795_130_100_000L, bar.startMs());
        assertEquals(7167.20, bar.open(), 1e-9);
        assertEquals(7171.70, bar.high(), 1e-9);
        assertEquals(7167.20, bar.low(), 1e-9);
        assertEquals(7171.45, bar.close(), 1e-9);
        assertEquals(301L, bar.volume());
        assertEquals("ANCHORED", bar.basisState());
        // The raw ES close is 7189.75. If the decoder had read closeCents the assertion above
        // would have failed by about nineteen points -- the basis -- and the page would have shown
        // the owner ES prices under an SPX heading.
    }

    @Test
    @DisplayName("C-118: a bar with ONLY the raw ES cents is ADMITTED, carrying futures prices")
    void anUntranslatedBarIsAdmittedWithItsFuturesPrices() {
        String untranslated = REAL_BAR
                .replace("\"spxEqOpenCents\":716720,", "")
                .replace("\"spxEqHighCents\":717170,", "")
                .replace("\"spxEqLowCents\":716720,", "")
                .replace("\"spxEqCloseCents\":717145,", "");
        OvernightGrid.Bar bar = OvernightDecoders.bar(untranslated);
        assertNotNull(bar,
                "C-118: this bar EXISTS. Dropping it hid its volume, its delta and its side as "
                        + "well as its price, and nine such bars rendered as nine blank rows on "
                        + "2026-11-20 (C-117)");
        assertFalse(bar.hasSpx(), "and it says it was never translated");
        assertNull(bar.close());
        assertTrue(bar.hasFutures());
        assertEquals(7185.50, bar.futuresOpen(), 1e-9);
        assertEquals(7190.00, bar.futuresHigh(), 1e-9);
        assertEquals(7185.50, bar.futuresLow(), 1e-9);
        assertEquals(7189.75, bar.futuresClose(), 1e-9);
        assertEquals(301L, bar.volume(), "the volume is the bar's own count and never needed a basis");
        assertEquals(63L, bar.delta());
        // The 19-point basis is STILL not computed anywhere: these are the raw ES cents as the bar
        // carried them, and the renderer prints them marked ES. Reading a number the bar holds is
        // the opposite of deriving one (C-103).
    }

    @Test
    @DisplayName("C-118: a HALF-translated bar is still refused — one row, two kinds of price")
    void aHalfTranslatedBarIsRefused() {
        assertNull(OvernightDecoders.bar(REAL_BAR.replace("\"spxEqCloseCents\":717145,", "")),
                "three index prices and one futures price on one row read as four prices of the "
                        + "same kind, which is worse than showing neither");
        assertNull(OvernightDecoders.bar(REAL_BAR.replace("\"spxEqOpenCents\":716720,", "")));
    }

    @Test
    @DisplayName("C-118: a bar with NO price of either kind is refused — there is nothing to show")
    void aBarWithNoPriceAtAllIsRefused() {
        String priceless = REAL_BAR
                .replace("\"spxEqOpenCents\":716720,", "")
                .replace("\"spxEqHighCents\":717170,", "")
                .replace("\"spxEqLowCents\":716720,", "")
                .replace("\"spxEqCloseCents\":717145,", "")
                .replace("\"openCents\":718550,", "")
                .replace("\"highCents\":719000,", "")
                .replace("\"lowCents\":718550,", "")
                .replace("\"closeCents\":718975,", "");
        assertNull(OvernightDecoders.bar(priceless));
    }


    @Test
    @DisplayName("an INCOMPLETE bar is refused: a forming minute's high and low are not the minute's")
    void anIncompleteBarIsRefused() {
        assertNull(OvernightDecoders.bar(REAL_BAR.replace("\"complete\":true", "\"complete\":false")));
        assertNotNull(OvernightDecoders.bar(REAL_BAR), "and the complete one still decodes");
    }

    @Test
    @DisplayName("only one-minute bars are read, or a 5m bar double-counts its own minutes")
    void onlyOneMinuteBarsAreRead() {
        assertNull(OvernightDecoders.bar(REAL_BAR.replace("\"timeframe\":\"1m\"", "\"timeframe\":\"5m\"")));
        assertNull(OvernightDecoders.bar(REAL_BAR.replace("\"timeframe\":\"1m\"", "\"timeframe\":\"session\"")));
    }

    @Test
    @DisplayName("the real price decodes, with its own event time and the producer's own words")
    void theRealPriceDecodes() {
        ReadablePage.Price p = OvernightDecoders.price(REAL_PRICE, 999L);
        assertNotNull(p);
        assertEquals(7158.45, p.spx(), 1e-9, "canonicalSpot, parsed from its decimal text");
        assertEquals(1_795_131_318_048L, p.epochMs(),
                "the record's OWN event time, not the broker stamp of 999");
        assertEquals("ES_BASIS_DERIVED", p.source());
        assertEquals("LIVE_ES_BASIS", p.quality());
    }

    @Test
    @DisplayName("with no event time the broker stamp stands in, never a wall clock")
    void theBrokerStampIsTheFallback() {
        String noEventTime = REAL_PRICE.replace("\"spotEventTimeNs\":1795131318048000000,", "");
        ReadablePage.Price p = OvernightDecoders.price(noEventTime, 1_795_131_400_000L);
        assertNotNull(p);
        assertEquals(1_795_131_400_000L, p.epochMs());
    }

    @Test
    @DisplayName("a price with no time at all is ABSENT, because it could not be judged stale")
    void aPriceWithNoTimeIsAbsent() {
        String noEventTime = REAL_PRICE.replace("\"spotEventTimeNs\":1795131318048000000,", "");
        assertNull(OvernightDecoders.price(noEventTime, 0L),
                "a price the reader cannot date is worse than no price");
    }

    @Test
    @DisplayName("a price with no spot at all is absent, never a zero")
    void aPriceWithNoSpotIsAbsent() {
        String noSpot = REAL_PRICE
                .replace("\"canonicalSpot\":\"7158.450000000000000000\",", "")
                .replace("\"price\":\"7158.450000000000000000\",", "");
        assertNull(OvernightDecoders.price(noSpot, 1_795_131_400_000L));
        assertNull(OvernightDecoders.price(
                REAL_PRICE.replace("\"7158.450000000000000000\"", "\"0\""), 1L),
                "and a zero spot is not a spot");
    }

    @Test
    @DisplayName("garbage of every shape is absent rather than fatal")
    void garbageIsAbsent() {
        for (String bad : new String[] {null, "", "   ", "not json", "[1,2,3]", "\"a string\"",
                "{", "{\"timeframe\":\"1m\"}"}) {
            assertNull(OvernightDecoders.bar(bad), "bar: " + bad);
            assertNull(OvernightDecoders.price(bad, 1L), "price: " + bad);
        }
    }

    @Test
    @DisplayName("a basis that is not ANCHORED is carried through, not dropped")
    void anUnanchoredBasisIsCarried() {
        OvernightGrid.Bar bar = OvernightDecoders.bar(
                REAL_BAR.replace("\"ANCHORED\"", "\"QUARANTINED\""));
        assertNotNull(bar, "the bar is kept");
        assertEquals("QUARANTINED", bar.basisState(), "and its state travels with it");
    }
}
