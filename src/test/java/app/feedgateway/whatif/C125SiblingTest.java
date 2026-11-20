package app.feedgateway.whatif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.readable.StrikeDeltaSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-125: the delta sibling, and the record still byte for byte.</b>
 *
 * <p>The reviewer's first condition on this path: the map record is served unchanged and the
 * deltas are a separately named block beside it, with a test that asserts the record's served
 * bytes are identical with and without the sibling. That is the first test here, and it is the
 * one that matters: the whole reason this is a sibling rather than two extra fields on the strike
 * rows is that {@code WhatIfController} serves a contract whose only owner is its Avro schema.
 */
final class C125SiblingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A record in the shape the map service publishes, with its keys in ITS order. */
    private static final String RECORD =
            "{\"present\":true,\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"spot\":7031.67,"
                    + "\"strikes\":[{\"strikeCents\":700000,\"board\":{\"netGex\":1.0e9}},"
                    + "{\"strikeCents\":700500,\"board\":{\"netGex\":-2.0e9}}],"
                    + "\"fingerprint\":{\"valuationTime\":\"2026-11-20T18:01:00Z\"}}";

    /** One real gex record off the topic, trimmed to what the store reads. */
    private static String gex(double strike, String call, String put, String putReason) {
        return "{\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"strike\":" + strike + ","
                + (call == null ? "" : "\"callDelta\":" + call + ",")
                + (put == null ? "" : "\"putDelta\":" + put + ",")
                + "\"callInvalidReason\":\"\",\"putInvalidReason\":\""
                + (putReason == null ? "" : putReason) + "\","
                + "\"updatedAtMs\":1795188021183}";
    }

    private static StrikeDeltaSource storeWithTwo() {
        StrikeDeltaSource s = new StrikeDeltaSource();
        s.observe(gex(7000.0, "0.6372", "-0.3561", null));
        s.observe(gex(7005.0, "0.6141", "-0.3792", null));
        return s;
    }

    private static String served(StrikeDeltaSource store) throws Exception {
        JsonNode record = MAPPER.readTree(RECORD);
        ObjectNode block = StrikeDeltasSibling.build(MAPPER, store,
                record.path("expiry").asText(null),
                StrikeDeltasSibling.strikeCentsOf(record));
        return StrikeDeltasSibling.attach(MAPPER, RECORD, block);
    }

    @Test
    @DisplayName("C-125 condition 1: the record's bytes are IDENTICAL with and without the sibling")
    void theRecordsBytesAreIdentical() throws Exception {
        String withSibling = served(storeWithTwo());
        String without = served(null);

        assertEquals(RECORD, without,
                "with no store there is no block, and the body IS the record, unchanged");

        // The record's bytes are a PREFIX of the served body, up to its final brace. That is a
        // stronger statement than "the same fields are there": it says nothing was re-serialised,
        // nothing reordered, no number reformatted.
        String recordWithoutClose = RECORD.substring(0, RECORD.length() - 1);
        assertTrue(withSibling.startsWith(recordWithoutClose),
                "the record is served byte for byte and the sibling is spliced after it:\n"
                        + withSibling);
        assertTrue(withSibling.endsWith("}"));

        // And removing the sibling again gives the record back, to the byte.
        int at = withSibling.indexOf(",\"strikeDeltas\":");
        assertTrue(at > 0, withSibling);
        assertEquals(RECORD, withSibling.substring(0, at) + "}",
                "cut the block out and what is left is exactly what the map service published");
    }

    @Test
    @DisplayName("C-125 condition 2: the block says its source, producer, expiry and the figures")
    void theBlockSaysWhatItIs() throws Exception {
        JsonNode body = MAPPER.readTree(served(storeWithTwo()));
        JsonNode block = body.path("strikeDeltas");
        assertFalse(block.isMissingNode(), body.toString());
        assertEquals("options.databento.gex.strike", block.path("source").asText());
        assertEquals("DATABENTO", block.path("producer").asText());
        assertEquals("20261120", block.path("expiry").asText(),
                "the BOARD's expiry, not the newest the topic carries: the topic serves two at "
                        + "once and the owner trades the 0DTE");

        JsonNode one = block.path("strikes").path("700000");
        assertEquals(0.6372, one.path("callDelta").asDouble(), 1e-9);
        assertEquals(-0.3561, one.path("putDelta").asDouble(), 1e-9);
        assertEquals(1_795_188_021_183L, one.path("updatedAtMs").asLong(),
                "the RECORD's own time, so the page can age the figure");
    }

    @Test
    @DisplayName("C-125: a side with no delta carries the producer's REASON, never a zero")
    void anAbsentSideCarriesTheReason() throws Exception {
        StrikeDeltaSource store = new StrikeDeltaSource();
        store.observe(gex(7000.0, "0.6372", null, "PARITY_IV"));
        JsonNode one = MAPPER.readTree(served(store)).path("strikeDeltas")
                .path("strikes").path("700000");
        assertEquals(0.6372, one.path("callDelta").asDouble(), 1e-9);
        assertTrue(one.path("putDelta").isMissingNode(),
                "no key at all rather than a null or a zero: a zero delta is a real reading and "
                        + "absence must never look like one");
        assertEquals("PARITY_IV", one.path("putReason").asText());
        assertTrue(one.path("callReason").isMissingNode(),
                "and no reason beside a figure, which would read as a doubt about a figure the "
                        + "producer published");
    }

    @Test
    @DisplayName("C-125: and the CALL side too, symmetrically — both sides or neither is a trap")
    void theCallSideBehavesTheSame() throws Exception {
        // The put-side test above passed while the call branch was mutated to publish a zero:
        // one side's test does not defend the other, and the two branches are written twice.
        StrikeDeltaSource store = new StrikeDeltaSource();
        store.observe("{\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"strike\":7000.0,"
                + "\"putDelta\":-0.3561,\"callInvalidReason\":\"TICK_SPREAD\","
                + "\"putInvalidReason\":\"\",\"updatedAtMs\":1795188021183}");
        JsonNode one = MAPPER.readTree(served(store)).path("strikeDeltas")
                .path("strikes").path("700000");
        assertEquals(-0.3561, one.path("putDelta").asDouble(), 1e-9);
        assertTrue(one.path("callDelta").isMissingNode(),
                "no key at all for the absent side: a zero delta is a real reading");
        assertEquals("TICK_SPREAD", one.path("callReason").asText());
        assertTrue(one.path("putReason").isMissingNode());
    }

    @Test
    @DisplayName("C-125: a side with BOTH a figure and a reason publishes the figure ALONE")
    void aFigureWithAReasonBesideItPublishesTheFigureOnly() throws Exception {
        // The real shape, off options.databento.gex.strike at 15:20:21.183Z: strike 7465 carried
        // callDelta 0.002211… AND callInvalidReason TICK_SPREAD. The producer published a figure
        // and flagged its own mark quality; printing the reason beside the figure would read as a
        // doubt about a number the producer published, and the page's cell has room for one.
        StrikeDeltaSource store = new StrikeDeltaSource();
        store.observe("{\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"strike\":7000.0,"
                + "\"callDelta\":0.002211244977943782,\"putDelta\":-0.9977803543527187,"
                + "\"callInvalidReason\":\"TICK_SPREAD\",\"putInvalidReason\":\"PARITY_IV\","
                + "\"updatedAtMs\":1795188021183}");
        JsonNode one = MAPPER.readTree(served(store)).path("strikeDeltas")
                .path("strikes").path("700000");
        assertEquals(0.002211244977943782, one.path("callDelta").asDouble(), 0.0);
        assertEquals(-0.9977803543527187, one.path("putDelta").asDouble(), 0.0);
        assertTrue(one.path("callReason").isMissingNode(),
                "the figure is published, so no reason travels beside it: " + one);
        assertTrue(one.path("putReason").isMissingNode(), one.toString());
    }

    @Test
    @DisplayName("C-125: the CONTROLLER attaches it, so the block reaches the route's own body")
    void theControllerAttachesIt() throws Exception {
        // Driven through WhatIfController.whatIf, not through StrikeDeltasSibling: a splice that
        // nothing calls would pass every test above and the page would show two columns of
        // dashes for ever. The mutation pass found exactly that.
        app.feedgateway.readable.ReadableBoardRenderer renderer =
                org.mockito.Mockito.mock(app.feedgateway.readable.ReadableBoardRenderer.class);
        org.mockito.Mockito.when(renderer.strikeDeltas()).thenReturn(storeWithTwo());

        app.feedgateway.FeedGatewayService service =
                org.mockito.Mockito.mock(app.feedgateway.FeedGatewayService.class);
        org.mockito.Mockito.when(service.cachedWhatIfMap(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(RECORD);
        org.mockito.Mockito.when(service.activeSymbolExpiry())
                .thenReturn(new String[]{"SPX", "20261120"});
        org.mockito.Mockito.when(service.readableBoard()).thenReturn(renderer);

        app.feedgateway.liquidityhistory.LiquidityHistoryAuth auth = org.mockito.Mockito.mock(
                app.feedgateway.liquidityhistory.LiquidityHistoryAuth.class);
        org.mockito.Mockito.when(auth.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new app.feedgateway.liquidityhistory.LiquidityHistoryAuth.Result(
                        200, "tester"));

        String body = new WhatIfController(service, auth, MAPPER)
                .whatIf(null, null, "Bearer t").getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"strikeDeltas\""),
                "the route's own body carries the block: " + body);
        assertTrue(body.startsWith(RECORD.substring(0, RECORD.length() - 1)),
                "and the record is still byte for byte inside it: " + body);
        assertEquals(0.6372, MAPPER.readTree(body).path("strikeDeltas").path("strikes")
                .path("700000").path("callDelta").asDouble(), 1e-9);
    }

    @Test
    @DisplayName("C-125: with no store wired the route serves the record unchanged, never a 500")
    void withNoStoreTheRouteIsUnharmed() {
        app.feedgateway.FeedGatewayService service =
                org.mockito.Mockito.mock(app.feedgateway.FeedGatewayService.class);
        org.mockito.Mockito.when(service.cachedWhatIfMap(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(RECORD);
        org.mockito.Mockito.when(service.activeSymbolExpiry())
                .thenReturn(new String[]{"SPX", "20261120"});
        // readableBoard() unstubbed, so it answers null -- the shape a mock in an older test has
        app.feedgateway.liquidityhistory.LiquidityHistoryAuth auth = org.mockito.Mockito.mock(
                app.feedgateway.liquidityhistory.LiquidityHistoryAuth.class);
        org.mockito.Mockito.when(auth.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new app.feedgateway.liquidityhistory.LiquidityHistoryAuth.Result(
                        200, "tester"));
        assertEquals(RECORD, new WhatIfController(service, auth, MAPPER)
                        .whatIf(null, null, "Bearer t").getBody(),
                "the route's job is to serve the board: a sibling that cannot be built is a dash "
                        + "on the page, never a 500 on the screen the owner is reading");
    }

    @Test
    @DisplayName("C-125: the block carries ONLY the strikes the board carries")
    void onlyTheBoardsStrikesTravel() throws Exception {
        StrikeDeltaSource store = storeWithTwo();
        store.observe(gex(9999.0, "0.5", "-0.5", null));     // a strike no row shows
        JsonNode strikes = MAPPER.readTree(served(store)).path("strikeDeltas").path("strikes");
        assertEquals(2, strikes.size(),
                "a block carrying strikes no row shows is bytes on the wire for nobody");
        assertTrue(strikes.has("700000"));
        assertTrue(strikes.has("700500"));
        assertFalse(strikes.has("999900"));
    }

    @Test
    @DisplayName("C-125: nothing to carry means NO block, not an empty one")
    void nothingToCarryMeansNoBlock() throws Exception {
        assertNull(StrikeDeltasSibling.build(MAPPER, new StrikeDeltaSource(), "20261120",
                List.of(700000L)), "an empty store yields no block");
        assertEquals(RECORD, served(new StrikeDeltaSource()));
        // a store holding ANOTHER expiry's chain yields no block for this board
        StrikeDeltaSource other = new StrikeDeltaSource();
        other.observe(gex(7000.0, "0.6372", "-0.3561", null)
                .replace("\"expiry\":\"20261120\"", "\"expiry\":\"20261123\""));
        assertEquals(RECORD, served(other),
                "the next session's deltas are not this board's and must not appear on it");
    }

    @Test
    @DisplayName("C-125: a body this cannot extend without reshaping is returned UNCHANGED")
    void anUnextendableBodyIsUntouched() {
        ObjectNode block = MAPPER.createObjectNode();
        block.put("source", "x");
        assertEquals("not json", StrikeDeltasSibling.attach(MAPPER, "not json", block));
        assertEquals("[1,2]", StrikeDeltasSibling.attach(MAPPER, "[1,2]", block));
        assertEquals("{\"a\":1}  trailing text",
                StrikeDeltasSibling.attach(MAPPER, "{\"a\":1}  trailing text", block));
        // an EMPTY record object takes the block with no leading comma
        assertEquals("{\"source\":\"x\"}".replace("source", "strikeDeltas\":{\"source"),
                StrikeDeltasSibling.attach(MAPPER, "{}", block).replace("}}", "}"));
    }

    @Test
    @DisplayName("C-125 condition 3: the bytes the block adds, measured at 146 strikes")
    void theSizeIsMeasuredAndNotEstimated() throws Exception {
        StrikeDeltaSource store = new StrikeDeltaSource();
        StringBuilder strikes = new StringBuilder();
        for (int i = 0; i < 146; i++) {
            double strike = 6500.0 + i * 5.0;
            store.observe(gex(strike, "0.6372", "-0.3561", null));
            if (i > 0) {
                strikes.append(',');
            }
            strikes.append("{\"strikeCents\":").append(Math.round(strike * 100.0))
                    .append(",\"board\":{\"netGex\":1.0e9}}");
        }
        String record = "{\"expiry\":\"20261120\",\"strikes\":[" + strikes + "]}";
        JsonNode parsed = MAPPER.readTree(record);
        ObjectNode block = StrikeDeltasSibling.build(MAPPER, store, "20261120",
                StrikeDeltasSibling.strikeCentsOf(parsed));
        assertNotNull(block);
        String withSibling = StrikeDeltasSibling.attach(MAPPER, record, block);
        int added = withSibling.length() - record.length();

        // The figure, so the entry in the tab is measured rather than guessed. The web proxy's cap
        // is 1 MiB (WHAT_IF_PROXY_MAX_BYTES) and the record itself is about 180 KB at this width.
        assertTrue(added > 0);
        assertTrue(added < 20_000,
                "146 strikes of two deltas and a timestamp added " + added + " bytes; anything "
                        + "near the proxy's 1 MiB cap would have to be reported, not assumed");
        assertTrue(added * 50 < 1_048_576,
                "and it is small enough that fifty times this block still fits the cap: " + added);
        System.out.println("C-125 sibling size at 146 strikes: " + added + " bytes added to a "
                + record.length() + "-byte record; proxy cap 1048576");
    }
}
