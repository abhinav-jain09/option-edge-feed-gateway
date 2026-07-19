package app.feedgateway.pinflow;

import app.feedgateway.GatewaySettings;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.InMemoryTicketStore;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.approval.ApprovalAuthority;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.TokenVerifier;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import app.feedgateway.mtsession.gateway.WsTicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5.3 status codes + §4 auth for {@code GET /api/pin-flow}: 400 bad band / impossible date, 401/403
 * auth (shared verifier), 503 when the DB is not configured. The bulkhead runs synchronously enough
 * for the test to poll the {@link DeferredResult} for its result.
 */
class PinFlowControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final Clock CLOCK = Clock.fixed(
            java.time.Instant.parse("2026-06-23T14:00:00Z"), ZoneOffset.UTC);

    private PinFlowExecutor bulkhead;

    @AfterEach
    void tearDown() {
        if (bulkhead != null) {
            bulkhead.close();
        }
    }

    /** A valid bearer that the enforcing (multi-tenant) validation controller accepts. */
    private static final String GOOD_TOKEN = "Bearer good.token";

    /** DB-not-configured store (null datasource) → dbConfigured() == false. */
    private PinFlowStore noDbStore() {
        return new PinFlowStore(null, ET, 10, 8_000L);
    }

    /**
     * Enforcing controller used by the param-validation tests: multi-tenant auth (ticket service present,
     * {@code enforcing()==true}) with a valid+approved token, so requests carrying {@link #GOOD_TOKEN}
     * pass auth and exercise the 400/503 validation paths. Pin-flow ALWAYS fails closed, so tests must
     * present a real token even for the validation cases.
     */
    private PinFlowController validationController(PinFlowStore store) {
        VerifiedPrincipal principal = new VerifiedPrincipal("uv", "val", Set.of("user"),
                "options-edge-web", java.time.Instant.parse("2030-01-01T00:00:00Z"));
        WsTicketService svc = multiTenantService("good.token", principal,
                q -> ApprovalAuthority.ApprovalDecision.approved(0L));
        return multiTenantController(svc, store);
    }

    @SuppressWarnings("unchecked")
    private static int status(DeferredResult<ResponseEntity<byte[]>> deferred) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000L;
        while (!deferred.hasResult() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5L);
        }
        Object result = deferred.getResult();
        return ((ResponseEntity<byte[]>) result).getStatusCode().value();
    }

    @Test
    void dbNotConfiguredAnswers503() {
        withSymbolAndExpiry(() -> {
            PinFlowController controller = validationController(noDbStore());
            try {
                DeferredResult<ResponseEntity<byte[]>> deferred =
                        controller.pinFlow(null, null, null, GOOD_TOKEN);
                assertEquals(503, status(deferred), "no datasource → 503, not 500");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void bandOutOfRangeAnswers400() {
        withSymbolAndExpiry(() -> {
            PinFlowController controller = validationController(noDbStore());
            try {
                // hi below the floor / lo under 100 / band too wide → all 400 BEFORE any DB touch.
                assertEquals(400, status(controller.pinFlow(null, "50", "60", GOOD_TOKEN)), "lo < 100");
                assertEquals(400, status(controller.pinFlow(null, "1", "100001", GOOD_TOKEN)), "hi > 100000");
                assertEquals(400, status(controller.pinFlow(null, "7000", "9000", GOOD_TOKEN)),
                        "band width > 1000");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void impossibleDateAnswers400() {
        withSymbolAndExpiry(() -> {
            PinFlowController controller = validationController(noDbStore());
            try {
                assertEquals(400, status(controller.pinFlow("2026-02-30", null, null, GOOD_TOKEN)),
                        "strict parse rejects a real-looking but impossible date");
                assertEquals(400, status(controller.pinFlow("2026-13-01", null, null, GOOD_TOKEN)));
                assertEquals(400, status(controller.pinFlow("junk", null, null, GOOD_TOKEN)));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void badLoHiAnswers400() {
        withSymbolAndExpiry(() -> {
            PinFlowController controller = validationController(noDbStore());
            try {
                assertEquals(400, status(controller.pinFlow(null, "abc", "7590", GOOD_TOKEN)),
                        "non-integer lo");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ---- P1: pin-flow FAILS CLOSED. With WS auth globally disabled the shared verifier would return
    // an authenticated "anonymous" principal; pin-flow must still reject with 401 (never serve DB data
    // unauthenticated), regardless of the presence of any bearer token. ----

    @Test
    void authDisabledFailsClosedWith401() throws InterruptedException {
        bulkhead = new PinFlowExecutor(2, 4);
        // Both auth flags off (no ticket service, WS_AUTH_ENABLED default false) → enforcing()==false.
        LiquidityHistoryAuth disabled = new LiquidityHistoryAuth(new GatewaySettings(), null);
        assertEquals(false, disabled.enforcing(), "sanity: this auth is NOT enforcing");
        // A DB-CONFIGURED store proves the 401 comes from the fail-closed gate, not from a missing DB.
        PinFlowStore dbStore = new PinFlowStore(new NeverCalledDataSource(), ET, 10, 8_000L);
        PinFlowController controller =
                new PinFlowController(dbStore, bulkhead, disabled, MAPPER, CLOCK, 7490, 7590, 5_000L, 1_000);

        // No token → 401 (fail closed).
        assertEquals(401, status(controller.pinFlow(null, null, null, null)),
                "auth disabled → 401 even with no token");
        // Even WITH a bearer token → still 401 (the endpoint is never open in dev).
        assertEquals(401, status(controller.pinFlow(null, null, null, "Bearer anything")),
                "auth disabled → 401 even with a bearer token");
    }

    // ---- Derived band is OURS, not the caller's: an extreme derivation must be CLAMPED, never 400. ----

    /**
     * DataSource whose spot-range query reports an absurd spread (100..99000) so the derived band is far
     * wider than MAX_BAND_WIDTH; every other query returns no rows. Captures the lo/hi the store is
     * finally asked for, which is the clamped band.
     */
    private static final class ExtremeSpotDataSource implements javax.sql.DataSource {
        final java.util.List<Integer> ints = new java.util.ArrayList<>();

        @Override public java.sql.Connection getConnection() {
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.Connection.class.getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            return statement(String.valueOf(args[0]).contains("min(spot)"));
                        }
                        if ("isClosed".equals(method.getName())) {
                            return false;
                        }
                        return null;
                    });
        }

        private java.sql.PreparedStatement statement(boolean spotRange) {
            return (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{java.sql.PreparedStatement.class},
                    (proxy, method, args) -> {
                        if ("setInt".equals(method.getName())) {
                            ints.add((Integer) args[1]); // captures the band the store queries with
                            return null;
                        }
                        if ("executeQuery".equals(method.getName())) {
                            return rows(spotRange);
                        }
                        return null;
                    });
        }

        private java.sql.ResultSet rows(boolean spotRange) {
            java.util.concurrent.atomic.AtomicBoolean served = new java.util.concurrent.atomic.AtomicBoolean();
            return (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.ResultSet.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        // the spot-range query yields exactly one row; the data queries yield none
                        case "next" -> spotRange && served.compareAndSet(false, true);
                        case "getBigDecimal" -> ((Integer) args[0]) == 1
                                ? java.math.BigDecimal.valueOf(100)
                                : java.math.BigDecimal.valueOf(99_000);
                        default -> null;
                    });
        }

        @Override public java.sql.Connection getConnection(String u, String p) { return getConnection(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @Test
    void extremeDerivedBandIsClampedNotRejected() throws InterruptedException {
        ExtremeSpotDataSource ds = new ExtremeSpotDataSource();
        PinFlowStore store = new PinFlowStore(ds, ET, 10, 8_000L);
        VerifiedPrincipal principal = new VerifiedPrincipal("uv", "val", Set.of("user"),
                "options-edge-web", java.time.Instant.parse("2030-01-01T00:00:00Z"));
        WsTicketService svc = multiTenantService("good.token", principal,
                q -> ApprovalAuthority.ApprovalDecision.approved(0L));
        bulkhead = new PinFlowExecutor(2, 4);
        // bandMargin = 150 → derivation ON (10-arg ctor).
        PinFlowController controller = new PinFlowController(store, bulkhead,
                new LiquidityHistoryAuth(new GatewaySettings(), svc), MAPPER, CLOCK,
                7490, 7590, 5_000L, 1_000, 150);

        // No lo/hi → band derived (absurdly wide), then clamped — and still served, not 400'd.
        withSymbolAndExpiry(() -> {
            try {
                assertEquals(200, status(controller.pinFlow(null, null, null, GOOD_TOKEN)),
                        "a SERVER-derived band must never surface as a 400 'band out of range'");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        assertTrue(ds.ints.size() >= 2, "expected the store to bind a lo/hi band");
        int lo = ds.ints.get(0);
        int hi = ds.ints.get(1);
        assertTrue(hi - lo <= 1_000, "derived band must be clamped to MAX_BAND_WIDTH, was " + lo + ".." + hi);
        assertTrue(lo >= 100, "clamped lo must respect MIN_LO, was " + lo);
    }

    // ---- §4 auth (401/403): same shared verifier as /api/liquidity-history ----

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

    private PinFlowController multiTenantController(WsTicketService svc, PinFlowStore store) {
        bulkhead = new PinFlowExecutor(2, 4);
        LiquidityHistoryAuth auth = new LiquidityHistoryAuth(new GatewaySettings(), svc);
        return new PinFlowController(store, bulkhead, auth, MAPPER, CLOCK, 7490, 7590, 5_000L, 1_000);
    }

    private static void withSymbolAndExpiry(Runnable body) {
        String prevSym = System.getProperty("IB_SYMBOL");
        String prevExp = System.getProperty("IB_EXPIRY");
        System.setProperty("IB_SYMBOL", "SPX");
        System.setProperty("IB_EXPIRY", "20260623");
        try {
            body.run();
        } finally {
            restore("IB_SYMBOL", prevSym);
            restore("IB_EXPIRY", prevExp);
        }
    }

    private static void restore(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }

    @Test
    void missingAndInvalidTokensAnswer401() {
        withSymbolAndExpiry(() -> {
            VerifiedPrincipal principal = new VerifiedPrincipal("u1", "alice", Set.of("user"),
                    "options-edge-web", java.time.Instant.parse("2030-01-01T00:00:00Z"));
            WsTicketService svc = multiTenantService("good.token", principal,
                    q -> ApprovalAuthority.ApprovalDecision.approved(0L));
            PinFlowController controller = multiTenantController(svc, noDbStore());
            try {
                assertEquals(401, status(controller.pinFlow(null, null, null, null)),
                        "missing Authorization header → 401");
                assertEquals(401, status(controller.pinFlow(null, null, null, "Bearer wrong.token")),
                        "invalid token → 401");
                assertEquals(401, status(controller.pinFlow(null, null, null, "Basic abc")),
                        "non-Bearer scheme → 401");
                // valid + approved token: passes auth, then 503 because THIS store has no DB configured.
                assertEquals(503, status(controller.pinFlow(null, null, null, "Bearer good.token")),
                        "valid token passes auth; 503 only because DB is unconfigured");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** A DataSource that fails the test if any connection is ever requested (proves no DB touch). */
    private static final class NeverCalledDataSource implements javax.sql.DataSource {
        @Override
        public java.sql.Connection getConnection() {
            throw new AssertionError("DB must not be touched when auth fails closed");
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) {
            throw new AssertionError("DB must not be touched when auth fails closed");
        }

        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return null; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @Test
    void validButUnapprovedTokenAnswers403() {
        withSymbolAndExpiry(() -> {
            VerifiedPrincipal principal = new VerifiedPrincipal("u2", "bob", Set.of("user"),
                    "options-edge-web", java.time.Instant.parse("2030-01-01T00:00:00Z"));
            WsTicketService svc = multiTenantService("good.token", principal,
                    q -> ApprovalAuthority.ApprovalDecision.DENY);
            PinFlowController controller = multiTenantController(svc, noDbStore());
            try {
                assertEquals(403, status(controller.pinFlow(null, null, null, "Bearer good.token")),
                        "valid token, no approval → 403");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
