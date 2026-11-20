package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>The readable pages read ONE instrument.</b>
 *
 * <p>The gateway binds BOTH indicator CURRENT topics, and on dev they publish at the SAME instants
 * every five seconds. Measured 2026-11-20, 00:07:37 to 00:08:02 New York, six publishes each:
 *
 * <pre>
 *   es.options.indicators.snapshot.current   ES   vwap 7175.3366  INCOMPLETE  DEADLINE_FINALIZED
 *   options.indicators.snapshot.current      SPX  vwap null       ABSENT      SOURCE_HAS_NO_VOLUME
 * </pre>
 *
 * <p>The live tap kept whichever arrived last, so the owner's VWAP cells read
 * {@code 7184.26(LATCHED)} at 05:54 Amsterdam and {@code SOURCE_HAS_NO_VOLUME} at 06:01 — not a
 * reset anywhere, a coin toss on every poll. The side tracker was unaffected only because the SPX
 * records do not decode into moments, so the headline kept measuring against ES while the cells
 * beside it showed the cash index. Two paths, two different filters, and the disagreement was
 * invisible.
 *
 * <p>The fixtures below are those two records, with their real fields and values, in the shape the
 * gateway's own {@code indicatorJson} converter produces.
 */
class ReadableInstrumentTest {

    /** 2026-11-20 00:04:09.870 New York, the instant of the ES record that was measured. */
    private static final String ES_AT = "2026-11-20T05:04:09.870Z";
    private static final String SPX_AT = "2026-11-20T05:04:09.870Z";
    private static final long NOW = java.time.Instant.parse("2026-11-20T05:05:00Z").toEpochMilli();

    /** The ES record as measured: a LATCHED session VWAP, RTH still warming. */
    private static String es(String at, String price, String vwap) {
        return "{\"schemaVersion\":1,\"symbol\":\"ES\",\"sourceSymbol\":\"ES.v.0\","
                + "\"tradeDate\":\"2026-11-20\",\"marketState\":\"OPEN\","
                + "\"currentPrice\":\"" + price + "\",\"asOfEventTime\":\"" + at + "\","
                + "\"session\":{\"vwapAnalysis\":{"
                + "\"vwap\":{\"value\":\"" + vwap + "\",\"state\":\"INCOMPLETE\","
                + "\"reason\":\"DEADLINE_FINALIZED\",\"incompleteKind\":\"LATCHED\"},"
                + "\"stdDev\":{\"value\":\"5.0993\",\"state\":\"INCOMPLETE\"},"
                + "\"bandUpper1\":{\"value\":\"7182.56\",\"state\":\"INCOMPLETE\"},"
                + "\"bandLower1\":{\"value\":\"7172.36\",\"state\":\"INCOMPLETE\"}},"
                + "\"vwapRth\":{\"vwap\":{\"value\":null,\"state\":\"WARMING_UP\","
                + "\"reason\":\"WARMING_UP_WINDOW\"}}}}";
    }

    /** The SPX record as measured: every figure absent, because the index has no traded volume. */
    private static String spx(String at) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"marketState\":\"OPEN\","
                + "\"currentPrice\":\"6980.12\",\"asOfEventTime\":\"" + at + "\","
                + "\"session\":{\"vwapAnalysis\":{"
                + "\"vwap\":{\"value\":null,\"state\":\"ABSENT\","
                + "\"reason\":\"SOURCE_HAS_NO_VOLUME\"},"
                + "\"stdDev\":{\"value\":null,\"state\":\"ABSENT\","
                + "\"reason\":\"SOURCE_HAS_NO_VOLUME\"}},"
                + "\"vwapRth\":{\"vwap\":{\"value\":null,\"state\":\"ABSENT\","
                + "\"reason\":\"SOURCE_HAS_NO_VOLUME\"}}}}";
    }

    private static ReadableBoardRenderer renderer() {
        return new ReadableBoardRenderer(() -> null, () -> NOW);
    }

    @Test
    @DisplayName("the SPX snapshot arriving LAST does not take the cells off ES")
    void theSpxSnapshotDoesNotTakeTheCells() {
        ReadableBoardRenderer r = renderer();
        r.onIndicatorsJson(es(ES_AT, "7158.75", "7177.4637"));
        r.onIndicatorsJson(spx(SPX_AT));            // the same instant, the other topic

        String page = r.namedPage("now");
        assertTrue(page.contains("7177.46"),
                "the ES figure is still what the cells show:\n" + page);
        assertFalse(page.contains("SOURCE_HAS_NO_VOLUME"),
                "the cash index's absence reason must not reach a VWAP cell:\n" + page);
        assertEquals(1, r.otherInstrumentSnapshots(),
                "and the refusal is counted, so a filter that stopped running would show");
    }

    @Test
    @DisplayName("order does not matter: ES last is the same page as ES first")
    void orderDoesNotMatter() {
        ReadableBoardRenderer esFirst = renderer();
        esFirst.onIndicatorsJson(es(ES_AT, "7158.75", "7177.4637"));
        esFirst.onIndicatorsJson(spx(SPX_AT));

        ReadableBoardRenderer spxFirst = renderer();
        spxFirst.onIndicatorsJson(spx(SPX_AT));
        spxFirst.onIndicatorsJson(es(ES_AT, "7158.75", "7177.4637"));

        assertEquals(esFirst.namedPage("now"), spxFirst.namedPage("now"),
                "the page cannot depend on which topic answered the poll last");
    }

    @Test
    @DisplayName("an SPX snapshot alone leaves the VWAP line honestly absent, not wrong")
    void spxAloneIsAbsentNotWrong() {
        ReadableBoardRenderer r = renderer();
        r.onIndicatorsJson(spx(SPX_AT));

        String page = r.namedPage("now");
        assertTrue(page.contains("vwap|absent|es.options.indicators.snapshot.current"),
                "the ES topic has given us nothing, and the page names it rather than printing "
                        + "two bare 'not published' lines:\n" + page);
        assertFalse(page.contains("6980.12"),
                "and the cash index's own price is not quoted as the ES line's:\n" + page);
    }

    @Test
    @DisplayName("the side path refuses the SPX snapshot by name, not by accident")
    void theSidePathRefusesSpxByName() {
        assertNull(VwapRebuild.decode(spx(SPX_AT)),
                "an SPX snapshot makes no moment");
        assertFalse(VwapRebuild.isForReadablePages(spx(SPX_AT)),
                "and it is refused on its symbol, so a usable SPX VWAP would still be refused");
        assertTrue(VwapRebuild.isForReadablePages(es(ES_AT, "7158.75", "7177.4637")));
        assertNotNull(VwapRebuild.decode(es(ES_AT, "7158.75", "7177.4637")));
    }

    @Test
    @DisplayName("a snapshot with no symbol is refused rather than assumed to be ES")
    void aSnapshotWithNoSymbolIsRefused() {
        String noSymbol = es(ES_AT, "7158.75", "7177.4637")
                .replace("\"symbol\":\"ES\",", "");
        assertFalse(VwapRebuild.isForReadablePages(noSymbol));
        assertNull(VwapRebuild.decode(noSymbol));
    }

    @Test
    @DisplayName("the instant is readable even when the figures are not")
    void theInstantIsReadableWithoutUsableFigures() {
        assertEquals(java.time.Instant.parse(SPX_AT).toEpochMilli(),
                VwapRebuild.instantOf(spx(SPX_AT)).longValue(),
                "the held-figure line needs the instant of the record that carries the absence");
    }
}
