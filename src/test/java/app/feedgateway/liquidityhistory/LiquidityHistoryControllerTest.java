package app.feedgateway.liquidityhistory;

import app.feedgateway.GatewaySettings;
import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.InMemoryTicketStore;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.approval.ApprovalAuthority;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.TokenVerifier;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import app.feedgateway.mtsession.gateway.WsTicketService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.MAPPER;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.PREVIOUS_TRADING_DAY;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.TRADING_DAY;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.cell;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.config;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.et;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.frame;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.seamStore;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.sessionOpenMs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §3 endpoint: precedence ladder, sentinels, truncation budget, headers, auth, rate limit (AC11/AC12). */
class LiquidityHistoryControllerTest {

    private static final String SYMBOL = "SPX";
    private static final String EXPIRY = "2026-06-23";
    private static final long M0 = sessionOpenMs(TRADING_DAY);
    private static final long DEFAULT_BUDGET = 24L * 1024 * 1024;

    private static LiquidityHistoryStore emptySeamStore(Clock clock) {
        return seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
    }

    private static LiquidityHistoryStore publishedStore(Clock clock, String... frames) {
        LiquidityHistoryStore store = emptySeamStore(clock);
        store.beginEpochState();
        for (String f : frames) {
            store.foldForTest(f, 0);
        }
        store.completeEpochState();
        return store;
    }

    private static LiquidityHistoryController controller(LiquidityHistoryStore store, Clock clock) {
        return controller(store, clock, DEFAULT_BUDGET, 1_000);
    }

    private static LiquidityHistoryController controller(LiquidityHistoryStore store, Clock clock,
                                                         long maxResponseBytes, int ratePerMin) {
        // Open-auth mode (both auth flags off in the test env) except in the dedicated auth tests.
        LiquidityHistoryAuth auth = new LiquidityHistoryAuth(new GatewaySettings(), null);
        return new LiquidityHistoryController(store, auth, MAPPER, clock, maxResponseBytes, ratePerMin);
    }

    private static Clock midSession() {
        return Clock.fixed(et(TRADING_DAY, 10, 5), java.time.ZoneOffset.UTC);
    }

    private static JsonNode body(ResponseEntity<byte[]> response) {
        assertEquals("gzip", response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(response.getBody()))) {
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String oneCellFrame(long bucketStartMs, long buyContracts) {
        return frame(SYMBOL, EXPIRY, bucketStartMs, "LIVE", "FULL", List.of(6000.0),
                cell(6000.0, "CALL", "buyContracts", buyContracts, "lastBidSize", 11L));
    }

    // 200 happy path: envelope shape, headers (incl. watermark presence), gzip.
    @Test
    void servesEnvelopeWithWatermarkAndSessionHeaders() {
        Clock clock = midSession();
        LiquidityHistoryStore store = publishedStore(clock,
                oneCellFrame(M0, 2L), oneCellFrame(M0 + 1_000L, 3L));
        ResponseEntity<byte[]> response =
                controller(store, clock).liquidityHistory(SYMBOL, EXPIRY, null, null);

        assertEquals(200, response.getStatusCode().value());
        HttpHeaders headers = response.getHeaders();
        assertEquals(Long.toString(M0 + 1_000L), headers.getFirst("X-History-Watermark"));
        assertEquals(Long.toString(M0), headers.getFirst("X-History-Session-Open"));
        assertEquals(Long.toString(et(TRADING_DAY, 16, 0).toEpochMilli()),
                headers.getFirst("X-History-Session-Close"));
        assertEquals("false", headers.getFirst("X-History-Truncated"));
        assertNull(headers.getFirst("X-History-Epoch-Rebuilding"), "steady state has no rebuilding header");

        JsonNode envelope = body(response);
        assertEquals(1, envelope.path("historySchemaVersion").asInt());
        assertEquals(SYMBOL, envelope.path("symbol").asText());
        assertEquals(EXPIRY, envelope.path("expiry").asText());
        assertEquals(TRADING_DAY.toString(), envelope.path("tradeDate").asText(),
                "default tradeDate = TODAY in ET, from the request clock");
        assertEquals(M0, envelope.path("sessionOpenMs").asLong());
        assertEquals(M0 + 1_000L, envelope.path("watermarkBucketStartMs").asLong());
        assertFalse(envelope.path("truncated").asBoolean());
        assertEquals(1, envelope.path("buckets").size());
        JsonNode cell = envelope.path("buckets").get(0).path("cells").get(0);
        assertEquals(5L, cell.path("buyContracts").asLong());
        assertEquals(11L, cell.path("lastBidSize").asLong());
        assertEquals("BUY", cell.path("tradeDotSide").asText());
        // Only §5 HISTORY CELL PROJECTION fields serialize, and neutral defaults are ELIDED (part
        // of the historySchemaVersion=1 contract; absent folds as 0/NONE/NEUTRAL client-side).
        assertFalse(cell.has("lastAskSize"), "0 elided");
        assertFalse(cell.has("sellContracts"), "0 elided");
        assertFalse(cell.has("bidState"), "NEUTRAL elided");
        assertEquals(Set.of("strike", "optionSide", "lastBidSize", "buyContracts", "tradeDotSide"),
                toFieldSet(cell), "no non-projection field may leak");
    }

    // AC12 ladder step 1: topology alarm 503s ALL chains (even before session resolution).
    @Test
    void topologyAlarmAnswers503ForAllChains() {
        Clock clock = midSession();
        LiquidityHistoryStore store = publishedStore(clock, oneCellFrame(M0, 1L));
        store.setTopologyAlarmForTest(true);
        ResponseEntity<byte[]> response =
                controller(store, clock).liquidityHistory(SYMBOL, EXPIRY, null, null);
        assertEquals(503, response.getStatusCode().value());
        assertEquals("60", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    // AC12 ladder step 2 + Retry-After clamp: no published aggregate -> 503 with Retry-After in [5,60].
    @Test
    void noPublishedAggregateAnswers503WithClampedRetryAfter() {
        Clock clock = midSession();
        LiquidityHistoryStore store = emptySeamStore(clock);
        ResponseEntity<byte[]> response =
                controller(store, clock).liquidityHistory(SYMBOL, EXPIRY, null, null);
        assertEquals(503, response.getStatusCode().value());
        assertEquals("5", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), "clamp floor");

        store.setLagRecordsForTest(10_000_000L);
        assertEquals("60", controller(store, clock).liquidityHistory(SYMBOL, EXPIRY, null, null)
                .getHeaders().getFirst(HttpHeaders.RETRY_AFTER), "clamp ceiling");
    }

    // AC12/AC16 ladder step 3: steady-state lag breach -> 503.
    @Test
    void lagBreachAnswers503() {
        Clock clock = midSession();
        LiquidityHistoryStore store = publishedStore(clock, oneCellFrame(M0, 1L));
        store.setLagRecordsForTest(301L);
        ResponseEntity<byte[]> response =
                controller(store, clock).liquidityHistory(SYMBOL, EXPIRY, null, null);
        assertEquals(503, response.getStatusCode().value());
        assertEquals("5", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    // AC12 ladder step 4: frozen published store during an epoch rebuild -> 200 + rebuilding header.
    @Test
    void servesFrozenStoreWithRebuildingHeaderDuringEpochRebuild() {
        Clock clock = midSession();
        LiquidityHistoryStore store = publishedStore(clock, oneCellFrame(M0, 1L));
        store.beginEpochState(); // rebuild running
        ResponseEntity<byte[]> response =
                controller(store, clock).liquidityHistory(SYMBOL, EXPIRY, null, null);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("true", response.getHeaders().getFirst("X-History-Epoch-Rebuilding"));
        assertEquals(Long.toString(M0), response.getHeaders().getFirst("X-History-Watermark"),
                "frozen watermark is honest");
    }

    // AC12/AC5: no-session zero sentinels — weekend/holiday and pre-open, never adjacent-session data.
    @Test
    void weekendAndPreOpenServeZeroSentinels() {
        // Explicit Saturday tradeDate, mid-week clock:
        Clock clock = midSession();
        LiquidityHistoryStore store = publishedStore(clock, oneCellFrame(M0, 1L));
        ResponseEntity<byte[]> saturday =
                controller(store, clock).liquidityHistory(SYMBOL, EXPIRY, "2026-06-20", null);
        assertEquals(200, saturday.getStatusCode().value());
        JsonNode envelope = body(saturday);
        assertEquals(0L, envelope.path("sessionOpenMs").asLong());
        assertEquals(0L, envelope.path("sessionCloseMs").asLong());
        assertEquals(0L, envelope.path("watermarkBucketStartMs").asLong());
        assertFalse(envelope.path("truncated").asBoolean());
        assertEquals(0, envelope.path("buckets").size());
        assertEquals("2026-06-20", envelope.path("tradeDate").asText(), "the RESOLVED date, not a neighbor");
        assertEquals("0", saturday.getHeaders().getFirst("X-History-Watermark"));
        assertEquals("0", saturday.getHeaders().getFirst("X-History-Session-Open"));

        // Pre-open on a trading day, on a store with NO published aggregate: still the 200 sentinel
        // (a chain that cannot have data yet must never 503 into the client's retry budget).
        Clock preOpen = Clock.fixed(et(TRADING_DAY, 8, 0), java.time.ZoneOffset.UTC);
        LiquidityHistoryStore coldStore = emptySeamStore(preOpen);
        ResponseEntity<byte[]> preOpenResponse =
                controller(coldStore, preOpen).liquidityHistory(SYMBOL, EXPIRY, null, null);
        assertEquals(200, preOpenResponse.getStatusCode().value());
        assertEquals(0L, body(preOpenResponse).path("sessionOpenMs").asLong());

        // Default request on a weekend resolves to the weekend date itself (§4: TODAY and ONLY today).
        Clock saturdayNoon = Clock.fixed(et(LocalDate.of(2026, 6, 20), 12, 0), java.time.ZoneOffset.UTC);
        ResponseEntity<byte[]> weekendDefault =
                controller(publishedStore(saturdayNoon, oneCellFrame(M0, 1L)), saturdayNoon)
                        .liquidityHistory(SYMBOL, EXPIRY, null, null);
        assertEquals("2026-06-20", body(weekendDefault).path("tradeDate").asText(),
                "default path never walks back to the prior session");
    }

    // Explicit tradeDate for a retained-but-never-folded previous session: 200 empty + truncated.
    @Test
    void absentPreviousTradingDayServesEmptyTruncated() {
        Clock clock = midSession();
        LiquidityHistoryStore store = publishedStore(clock, oneCellFrame(M0, 1L));
        ResponseEntity<byte[]> response = controller(store, clock)
                .liquidityHistory(SYMBOL, EXPIRY, PREVIOUS_TRADING_DAY.toString(), null);
        assertEquals(200, response.getStatusCode().value());
        JsonNode envelope = body(response);
        assertEquals(0, envelope.path("buckets").size());
        assertTrue(envelope.path("truncated").asBoolean(), "cannot claim empty-and-complete");
        assertEquals(sessionOpenMs(PREVIOUS_TRADING_DAY), envelope.path("sessionOpenMs").asLong(),
                "a real past session keeps its real bounds");
    }

    // AC12: response-size budget drops OLDEST buckets first + truncated=true, never 500.
    @Test
    void responseBudgetTruncatesOldestBucketsFirst() {
        Clock clock = midSession();
        LiquidityHistoryStore store = publishedStore(clock,
                frame(SYMBOL, EXPIRY, M0, "LIVE", "FULL", List.of()),
                frame(SYMBOL, EXPIRY, M0 + 60_000L, "LIVE", "FULL", List.of()));
        // Budget = 512 reserve + ~200 for buckets: exactly one (empty-cells) bucket fits.
        ResponseEntity<byte[]> response = controller(store, clock, 512L + 200L, 1_000)
                .liquidityHistory(SYMBOL, EXPIRY, null, null);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("true", response.getHeaders().getFirst("X-History-Truncated"));
        JsonNode envelope = body(response);
        assertTrue(envelope.path("truncated").asBoolean());
        assertEquals(1, envelope.path("buckets").size());
        assertEquals(M0 + 60_000L, envelope.path("buckets").get(0).path("bucketStartMs").asLong(),
                "the NEWEST bucket survives; the oldest is dropped");
    }

    @Test
    void invalidParametersAnswer400() {
        Clock clock = midSession();
        LiquidityHistoryController controller = controller(publishedStore(clock), clock);
        assertEquals(400, controller.liquidityHistory(null, EXPIRY, null, null).getStatusCode().value());
        assertEquals(400, controller.liquidityHistory(SYMBOL, null, null, null).getStatusCode().value());
        assertEquals(400, controller.liquidityHistory(SYMBOL, "20260623", null, null).getStatusCode().value(),
                "expiry must be canonical ISO, not compact");
        assertEquals(400, controller.liquidityHistory(SYMBOL, "2026-13-45", null, null).getStatusCode().value(),
                "shape alone is not enough — the date must be real");
        assertEquals(400, controller.liquidityHistory(SYMBOL, EXPIRY, "junk", null).getStatusCode().value());
    }

    // AC11: rate limit — 7th request within the minute for one principal -> 429 with Retry-After.
    @Test
    void rateLimitAnswers429PerPrincipal() {
        Clock clock = Clock.fixed(et(LocalDate.of(2026, 6, 20), 12, 0), java.time.ZoneOffset.UTC);
        LiquidityHistoryController controller = controller(publishedStore(clock), clock, DEFAULT_BUDGET, 6);
        for (int i = 0; i < 6; i++) {
            assertEquals(200, controller.liquidityHistory(SYMBOL, EXPIRY, null, null)
                    .getStatusCode().value(), "request " + (i + 1) + " within the limit");
        }
        ResponseEntity<byte[]> seventh = controller.liquidityHistory(SYMBOL, EXPIRY, null, null);
        assertEquals(429, seventh.getStatusCode().value());
        assertTrue(Integer.parseInt(seventh.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)) >= 1);
    }

    // ---- AC11: auth 401 / 403 (same verification as the WS handshake) ----

    private static WsTicketService multiTenantService(String goodToken, VerifiedPrincipal principal,
                                                      ApprovalAuthority approval) {
        TokenVerifier verifier = token -> {
            if (goodToken.equals(token)) {
                return principal;
            }
            throw new JwtVerificationException("bad token");
        };
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(1, 4, 100), new SubscriptionManager());
        AtomicInteger seq = new AtomicInteger();
        InMemoryTicketStore tickets = new InMemoryTicketStore(Clock.systemUTC(),
                () -> "tk-" + seq.incrementAndGet());
        return new WsTicketService(verifier, tickets, engine, approval,
                "https://kc.test/realms/optionsedge", Duration.ofSeconds(10));
    }

    private static void withSymbolAndExpiry(Runnable body) {
        String prevSym = System.getProperty("IB_SYMBOL");
        String prevExp = System.getProperty("IB_EXPIRY");
        System.setProperty("IB_SYMBOL", "SPX");
        System.setProperty("IB_EXPIRY", "20260623");
        try {
            body.run();
        } finally {
            if (prevSym == null) {
                System.clearProperty("IB_SYMBOL");
            } else {
                System.setProperty("IB_SYMBOL", prevSym);
            }
            if (prevExp == null) {
                System.clearProperty("IB_EXPIRY");
            } else {
                System.setProperty("IB_EXPIRY", prevExp);
            }
        }
    }

    private LiquidityHistoryController multiTenantController(WsTicketService svc, Clock clock,
                                                             LiquidityHistoryStore store) {
        LiquidityHistoryAuth auth = new LiquidityHistoryAuth(new GatewaySettings(), svc, null);
        return new LiquidityHistoryController(store, auth, MAPPER, clock, DEFAULT_BUDGET, 1_000);
    }

    @Test
    void missingInvalidAndExpiredTokensAnswer401() {
        withSymbolAndExpiry(() -> {
            Clock clock = midSession();
            LiquidityHistoryStore store = publishedStore(clock, oneCellFrame(M0, 1L));
            VerifiedPrincipal principal = new VerifiedPrincipal("u1", "alice", Set.of("user"),
                    "options-edge-web", java.time.Instant.parse("2030-01-01T00:00:00Z"));
            WsTicketService svc = multiTenantService("good.token", principal,
                    q -> ApprovalAuthority.ApprovalDecision.approved(0L));
            LiquidityHistoryController controller = multiTenantController(svc, clock, store);

            assertEquals(401, controller.liquidityHistory(SYMBOL, EXPIRY, null, null)
                    .getStatusCode().value(), "missing Authorization header");
            assertEquals(401, controller.liquidityHistory(SYMBOL, EXPIRY, null, "Bearer wrong.token")
                    .getStatusCode().value(), "invalid/expired token");
            assertEquals(401, controller.liquidityHistory(SYMBOL, EXPIRY, null, "Basic abc")
                    .getStatusCode().value(), "non-Bearer scheme");
            assertEquals(200, controller.liquidityHistory(SYMBOL, EXPIRY, null, "Bearer good.token")
                    .getStatusCode().value(), "valid + approved token serves");
        });
    }

    @Test
    void validButUnapprovedTokenAnswers403() {
        withSymbolAndExpiry(() -> {
            Clock clock = midSession();
            LiquidityHistoryStore store = publishedStore(clock, oneCellFrame(M0, 1L));
            VerifiedPrincipal principal = new VerifiedPrincipal("u2", "bob", Set.of("user"),
                    "options-edge-web", java.time.Instant.parse("2030-01-01T00:00:00Z"));
            WsTicketService svc = multiTenantService("good.token", principal,
                    q -> ApprovalAuthority.ApprovalDecision.DENY);
            LiquidityHistoryController controller = multiTenantController(svc, clock, store);
            assertEquals(403, controller.liquidityHistory(SYMBOL, EXPIRY, null, "Bearer good.token")
                    .getStatusCode().value(), "valid token but no approval record -> 403");
        });
    }

    @Test
    void legacyModeValidatesWithTheWsDecoder() {
        String prev = System.getProperty("WS_AUTH_ENABLED");
        System.setProperty("WS_AUTH_ENABLED", "true");
        try {
            Clock clock = midSession();
            LiquidityHistoryStore store = publishedStore(clock, oneCellFrame(M0, 1L));
            JwtDecoder rejecting = token -> {
                throw new JwtException("invalid");
            };
            LiquidityHistoryController rejected = new LiquidityHistoryController(store,
                    new LiquidityHistoryAuth(new GatewaySettings(), null, rejecting),
                    MAPPER, clock, DEFAULT_BUDGET, 1_000);
            assertEquals(401, rejected.liquidityHistory(SYMBOL, EXPIRY, null, "Bearer any")
                    .getStatusCode().value());

            JwtDecoder accepting = token -> Jwt.withTokenValue(token)
                    .header("alg", "none").subject("user-1").build();
            LiquidityHistoryController accepted = new LiquidityHistoryController(store,
                    new LiquidityHistoryAuth(new GatewaySettings(), null, accepting),
                    MAPPER, clock, DEFAULT_BUDGET, 1_000);
            // Legacy WS enforces nothing beyond validity, so REST enforces none either: straight 200.
            assertEquals(200, accepted.liquidityHistory(SYMBOL, EXPIRY, null, "Bearer any")
                    .getStatusCode().value());
        } finally {
            if (prev == null) {
                System.clearProperty("WS_AUTH_ENABLED");
            } else {
                System.setProperty("WS_AUTH_ENABLED", prev);
            }
        }
    }

    // AC1-adjacent: a full 390-bucket x 320-cell golden session fits the raw budget and serves fast.
    // Realistic sparsity (the §3 ~90B/cell budget math): ~10% of cells active, the rest quiet
    // resting book — quiet cells elide their neutral-default fields.
    @Test
    void fullGoldenSessionMeetsSizeBudgetAndServeSlo() {
        Clock clock = Clock.fixed(et(TRADING_DAY, 16, 30), java.time.ZoneOffset.UTC);
        LiquidityHistoryStore store = emptySeamStore(clock);
        store.beginEpochState();
        com.fasterxml.jackson.databind.node.ObjectNode[] cells =
                new com.fasterxml.jackson.databind.node.ObjectNode[320];
        java.util.List<Double> strikes = new java.util.ArrayList<>();
        for (int i = 0; i < 320; i++) {
            double strike = 5000.0 + (i / 2) * 5;
            if (i % 10 == 0) { // active cell: trades + wall + interpretation state
                cells[i] = cell(strike, i % 2 == 0 ? "CALL" : "PUT",
                        "lastBidSize", (long) (i + 1), "lastAskSize", (long) (i + 2),
                        "maxBidSize", (long) (i + 10), "buyContracts", (long) (i + 1), "sellContracts", 1L,
                        "buyPremium", i * 10.0, "bidState", "STABLE_WALL");
            } else { // quiet resting book: sizes only
                cells[i] = cell(strike, i % 2 == 0 ? "CALL" : "PUT",
                        "lastBidSize", 5L, "lastAskSize", 7L, "maxBidSize", 9L);
            }
            if (i % 2 == 0) {
                strikes.add(strike);
            }
        }
        for (int minute = 0; minute < 390; minute++) {
            store.foldForTest(frame(SYMBOL, EXPIRY, M0 + minute * 60_000L, "LIVE", "FULL", strikes, cells), 0);
        }
        store.completeEpochState();
        LiquidityHistoryController controller = controller(store, clock);

        controller.liquidityHistory(SYMBOL, EXPIRY, null, null); // warmup
        long start = System.nanoTime();
        ResponseEntity<byte[]> response = controller.liquidityHistory(SYMBOL, EXPIRY, null, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(200, response.getStatusCode().value());
        JsonNode envelope = body(response);
        assertEquals(390, envelope.path("buckets").size());
        assertFalse(envelope.path("truncated").asBoolean(), "raw projection must fit the 24MB budget");
        assertTrue(elapsedMs <= 1_500L, "p99 serve SLO 1500ms breached: " + elapsedMs + "ms");
    }

    private static LiquidityHistoryStore publishedStore(Clock clock) {
        LiquidityHistoryStore store = emptySeamStore(clock);
        store.beginEpochState();
        store.completeEpochState();
        return store;
    }

    private static Set<String> toFieldSet(JsonNode node) {
        Set<String> fields = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
