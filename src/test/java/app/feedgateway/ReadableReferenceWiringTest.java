package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.readable.ReferencePrice;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C-74 through the service: the start-up read-back must actually reach the page.
 *
 * <p>Driven rather than asserted about. The resolver has its own tests; what this one proves is the
 * thing that fails silently — that {@code seedReadableReference} is wired to the renderer the
 * controller serves from, so a recovered close reaches the bytes a reader receives. A source pin
 * saying the call exists would have passed on a method nothing called.
 */
class ReadableReferenceWiringTest {

    /** 2026-11-19 16:00 New York, the cash close, and a start five hours later. */
    private static final long CLOSE = 1_795_122_000_000L;

    private static String priceJson(long atMs, double spx, String source, String quality) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"price\":\"" + spx + "\","
                + "\"canonicalSpot\":\"" + spx + "\",\"source\":\"" + source + "\","
                + "\"quality\":\"" + quality + "\",\"spotEventTimeNs\":" + (atMs * 1_000_000L) + "}";
    }

    private static ReferencePrice.History history(List<String> prices, List<String> bars) {
        return new ReferencePrice.History() {
            @Override
            public List<String> pricesBetween(long fromMs, long toMs, int limit) {
                return prices;
            }

            @Override
            public List<String> barsBetween(long fromMs, long toMs) {
                return bars;
            }
        };
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
    }

    @Test
    @DisplayName("a close recovered at start-up reaches the bytes the controller serves")
    void theRecoveredCloseReachesTheServedPage() throws Exception {
        FeedGatewayService service = service();
        List<String> prices = new ArrayList<>();
        prices.add(priceJson(CLOSE - 1_000L, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        prices.add(priceJson(CLOSE + 3_600_000L, 7160.10, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));

        service.seedReadableReference(history(prices, List.of()));
        service.renderReadableBoard();
        String page = service.readableBoardPage();

        assertNotNull(page);
        assertTrue(page.contains("7176.24"),
                "the recovered close must be on the page the controller serves: " + page);
        assertTrue(page.contains("REFERENCE — previous cash close from the SPX index's own last "
                + "print"), "named by source, because an index print and a futures bar's "
                + "SPX-equivalent are different facts: " + page);
        assertFalse(page.contains("NO REFERENCE PRICE YET"),
                "which is the whole point of C-74");
    }

    @Test
    @DisplayName("with nothing recoverable the page states the absence and the gateway still runs")
    void nothingRecoverableIsStatedNotFatal() throws Exception {
        FeedGatewayService service = service();
        service.seedReadableReference(history(List.of(), List.of()));
        service.renderReadableBoard();
        String page = service.readableBoardPage();

        assertTrue(page.contains("NO REFERENCE PRICE"), page);
        assertTrue(page.contains("not recoverable"),
                "with the reason, so an operator is not left guessing: " + page);
    }

    @Test
    @DisplayName("the seed is called from start-up, not merely defined")
    void theSeedIsCalledFromStartUp() throws Exception {
        // The one thing reflection can establish cheaply and exactly: start() references the
        // no-argument seed. The behavioural half is the test above; this half guards against the
        // method existing and never being invoked, which is how the live block went unreached
        // through four changes in the map service.
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int start = src.indexOf("public void start() {");
        assertTrue(start > 0, "start() must exist");
        int end = src.indexOf("\n    }", start);
        String body = src.substring(start, end);
        assertTrue(body.contains("seedReadableReference();"),
                "start() must call the read-back; a seed nothing calls recovers nothing");

        // And the field it writes through is the one the controller reads from.
        Field f = FeedGatewayService.class.getDeclaredField("readableBoard");
        f.setAccessible(true);
        assertNotNull(f.get(service()), "the renderer the seed writes to must exist");
    }
}
