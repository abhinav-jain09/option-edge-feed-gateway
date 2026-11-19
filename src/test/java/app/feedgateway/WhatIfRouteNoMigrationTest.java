package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.gammamigration.GammaMigrationController;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import app.feedgateway.whatif.WhatIfController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * The proof that the merge guard cannot bite again: a <b>real</b> {@link FeedGatewayService},
 * built through the constructor Spring builds it with, holding a What-If map record and
 * <b>no gamma-migration record at all</b> — and {@code /api/what-if} answering with every strike.
 *
 * <p><b>Why this test exists beside the controller's own unit tests.</b> Those construct the
 * controller over a mocked {@code FeedGatewayService}. A mock answers {@code cachedWhatIfMap}
 * because the test told it to, which is exactly the thing that was never in doubt: the defect was
 * never in that accessor. It was that the board's only caller sat behind
 * {@code GammaMigrationController}'s early return, so a chain with no active migration produced
 * {@code {"present": false}} and the four columns rendered empty. No mocked test can show that the
 * guard is gone, because a mock has no guard. This one drives the real keyer, the real cache and
 * the real reader, and asserts both halves in the same service instance:
 *
 * <ol>
 *   <li>{@code /api/gamma-migration} says {@code present: false} — the state that used to hide the
 *       board is genuinely present here, not simulated;</li>
 *   <li>{@code /api/what-if} nevertheless returns the record, with all strikes.</li>
 * </ol>
 *
 * <p>Both controllers are the production classes. Only the bearer check is mocked, because this
 * test is about the data path and {@code LiquidityHistoryAuth} has its own suite.
 */
class WhatIfRouteNoMigrationTest {

    /**
     * A map record in the shape the map service publishes, with the two cases C-43 distinguishes:
     * a strike inside the band radius that carries its band, and one outside it that carries no
     * band key at all.
     */
    private static final String MAP_RECORD =
            "{\"service\":\"A\",\"symbol\":\"SPX\",\"expiry\":\"20261119\",\"spot\":7177.56,"
            + "\"minutesToExpiry\":9.98,\"cycleTime\":1795121402069,"
            + "\"strikes\":["
            + "{\"strikeCents\":717500,\"board\":{\"netGex\":-2.6e8,"
            + "\"holdOrAccelerate\":\"ACCELERATE\",\"strength\":0.46},"
            + "\"deltaBand\":{\"lowerEdge\":7051.2,\"upperEdge\":7304.9}},"
            + "{\"strikeCents\":720000,\"board\":{\"netGex\":1.4e8,"
            + "\"holdOrAccelerate\":\"HOLD\",\"strength\":0.21},"
            + "\"deltaBand\":{\"lowerEdge\":7074.0,\"upperEdge\":7326.0}},"
            + "{\"strikeCents\":760000,\"board\":{\"netGex\":1.0e6,"
            + "\"holdOrAccelerate\":\"NEUTRAL\",\"strength\":0.01}}"
            + "]}";

    /** The gateway as Spring builds it: the @Autowired constructor, nothing stubbed. */
    private static FeedGatewayService realGateway() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
    }

    private static LiquidityHistoryAuth allowingAuth() {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "tester"));
        return auth;
    }

    @Test
    @DisplayName("a real gateway with a map record and NO migration record serves all strikes")
    void theBoardIsServedFromARealGatewayHoldingNoMigrationRecord() {
        FeedGatewayService gateway = realGateway();
        ObjectMapper mapper = new ObjectMapper();

        // The record goes in through the REAL updateCache path, keyed off its own symbol and
        // expiry rather than its Kafka key (which is the cycle instant, as the producer sets it),
        // and then source-prefixed by updateCache exactly as every other chain cache is. A fixed
        // past timestamp will NOT do: updateCache's freshness gate evicts a stale record before
        // the switch is reached, which is how this test first failed.
        String cacheKey = gateway.cacheWhatIfMapForTest(MAP_RECORD, System.currentTimeMillis());
        assertEquals("DATABENTO|SPX|20261119", cacheKey,
                "the keyer reads the chain off the record, not off the Kafka key, and updateCache "
                        + "then prefixes the source — which is the key cachedByChain rebuilds");
        assertNotNull(gateway.cachedWhatIfMap("SPX", "20261119"),
                "the reader must resolve the same key updateCache wrote");

        // The state that used to hide the board, present for real in this same service.
        assertNull(gateway.cachedGammaMigration("SPX", "20261119"),
                "no gamma-migration record has been fed to this gateway");
        ResponseEntity<String> migration = new GammaMigrationController(gateway, allowingAuth(),
                mapper).gammaMigration("SPX", "20261119", "Bearer t");
        assertEquals(200, migration.getStatusCode().value());
        assertTrue(migration.getBody().contains("\"present\":false"),
                "this is the exact response that returned before merge was ever reached");

        // And the board is served anyway.
        ResponseEntity<String> whatIf = new WhatIfController(gateway, allowingAuth(), mapper)
                .whatIf("SPX", "20261119", "Bearer t");
        assertEquals(200, whatIf.getStatusCode().value());
        assertEquals(MAP_RECORD, whatIf.getBody(), "served verbatim off the map record");
        assertFalse(whatIf.getBody().contains("\"present\""),
                "a real record is the record, not an envelope around it");

        // All strikes travel (C-43), the far one included, and its absent band stays absent.
        for (String strike : new String[]{"717500", "720000", "760000"}) {
            assertTrue(whatIf.getBody().contains(strike), "strike " + strike + " must travel");
        }
        int bands = whatIf.getBody().split("deltaBand", -1).length - 1;
        assertEquals(2, bands,
                "two bands for three strikes: the strike outside the radius carries no band key");
    }

    @Test
    @DisplayName("a real gateway with nothing cached answers 200 present:false, never 404")
    void anAbsentBoardIsTwoHundredAndNotFourOhFour() {
        FeedGatewayService gateway = realGateway();
        ResponseEntity<String> whatIf = new WhatIfController(gateway, allowingAuth(),
                new ObjectMapper()).whatIf("SPX", "20261119", "Bearer t");

        assertEquals(200, whatIf.getStatusCode().value(),
                "a cold start is a normal state, and a page must tell it from a routing mistake");
        assertTrue(whatIf.getBody().contains("\"present\":false"));
        assertTrue(whatIf.getBody().contains("\"symbol\":\"SPX\""));
    }

    @Test
    @DisplayName("a map record that names no chain is dropped, not keyed off the cycle instant")
    void aRecordWithNoChainIsDropped() {
        FeedGatewayService gateway = realGateway();
        assertNull(gateway.cacheWhatIfMapForTest("{\"service\":\"A\",\"spot\":7177.56}",
                System.currentTimeMillis()), "no symbol/expiry is no cache key");
        assertNull(gateway.cachedWhatIfMap("SPX", "20261119"));
    }
}
