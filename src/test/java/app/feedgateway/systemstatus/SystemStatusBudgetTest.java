package app.feedgateway.systemstatus;

import app.feedgateway.GatewaySettings;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The budgets themselves. 2026-08-19 turned a 3s statement timeout into a blank page, and the fix is not
 * "make the number bigger" — it is that the numbers are bounded on both sides, that only the section
 * that is actually slow gets the wide allowance, and that the socket backstop can never fire first.
 */
class SystemStatusBudgetTest {

    @Test
    void fastSectionsKeepATightBudgetAndOnlyTheSlowSectionGetsTheWideOne() {
        GatewaySettings settings = new GatewaySettings();
        assertEquals(3, settings.systemStatusQueryTimeoutSeconds());
        assertEquals(15, settings.systemStatusSlowQueryTimeoutSeconds());
        assertTrue(settings.systemStatusSlowQueryTimeoutSeconds()
                > settings.systemStatusQueryTimeoutSeconds());
    }

    /** {@code intValue} only enforces a floor, so before the clamp a huge value silently won. */
    @Test
    void timeoutsAreClampedAtBothEnds() {
        System.setProperty("OE_SYSTEM_STATUS_SLOW_QUERY_TIMEOUT_S", Integer.toString(Integer.MAX_VALUE));
        System.setProperty("OE_SYSTEM_STATUS_QUERY_TIMEOUT_S", "0");
        try {
            GatewaySettings settings = new GatewaySettings();
            assertEquals(GatewaySettings.SYSTEM_STATUS_MAX_TIMEOUT_S,
                    settings.systemStatusSlowQueryTimeoutSeconds(),
                    "an unbounded wait must not be configurable into a live market-data process");
            assertEquals(1, settings.systemStatusQueryTimeoutSeconds(), "the floor still applies");
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_SLOW_QUERY_TIMEOUT_S");
            System.clearProperty("OE_SYSTEM_STATUS_QUERY_TIMEOUT_S");
        }
    }

    /**
     * socketTimeout is the backstop for a socket that never answers. Computed in long arithmetic and
     * clamped: at {@code Integer.MAX_VALUE} the old {@code timeout + 2} wrapped NEGATIVE, which pgjdbc
     * would have read as an immediate failure — the backstop firing before the thing it backs up.
     */
    @Test
    void socketTimeoutNeverWrapsAndAlwaysExceedsTheWidestStatementBudget() {
        System.setProperty("OE_SYSTEM_STATUS_SLOW_QUERY_TIMEOUT_S", Integer.toString(Integer.MAX_VALUE));
        System.setProperty("OE_WATCH_JDBC_URL", "jdbc:postgresql://192.168.100.252:5432/options_flow");
        System.setProperty("OE_WATCH_ALLOW_PLAINTEXT", "true");
        try {
            GatewaySettings settings = new GatewaySettings();
            SystemStatusDataSource ds = new SystemStatusConfig().systemStatusDataSource(settings);
            try {
                String socketTimeout = ds.delegate().getDataSourceProperties().getProperty("socketTimeout");
                long value = Long.parseLong(socketTimeout);
                assertTrue(value > 0, "socketTimeout must never be negative, was " + value);
                assertTrue(value > settings.systemStatusSlowQueryTimeoutSeconds() - 1,
                        "the backstop must not fire before the statement timeout it backs up");
                assertEquals(GatewaySettings.SYSTEM_STATUS_MAX_TIMEOUT_S + 2, value);
            } finally {
                ds.close();
            }
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_SLOW_QUERY_TIMEOUT_S");
            System.clearProperty("OE_WATCH_JDBC_URL");
            System.clearProperty("OE_WATCH_ALLOW_PLAINTEXT");
        }
    }

    /**
     * The whole-request deadline is HARD. Handing a number to {@code setQueryTimeout} does not bound a
     * request — connection acquisition, TLS and socket reads all happen outside it — so the section
     * runs off the request thread and is CANCELLED when its share of the budget is spent. A section
     * that overruns is UNKNOWN/DEADLINE_EXCEEDED, and the ones behind it are never even started.
     */
    @Test
    void anOverrunningSectionIsCancelledAndTheOnesBehindItAreNeverStarted() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S", "1");
        try {
            java.util.concurrent.atomic.AtomicBoolean laterSectionRead =
                    new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicBoolean topicsInterrupted =
                    new java.util.concurrent.atomic.AtomicBoolean();
            SystemStatusStore store = new SystemStatusStore(null, 3) {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public List<Map<String, Object>> topics(String env, int t) {
                    try {
                        Thread.sleep(30_000);   // far past the 1s budget
                    } catch (InterruptedException e) {
                        topicsInterrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                    return List.of();
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env, int t) {
                    laterSectionRead.set(true);
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env, int t) {
                    laterSectionRead.set(true);
                    return Map.of();
                }

                @Override
                public Map<String, Object> lastRun(String env, int t) {
                    laterSectionRead.set(true);
                    return new LinkedHashMap<>();
                }
            };
            SystemStatusController c = new SystemStatusController(new GatewaySettings(), store,
                    new ConsumerLagReader("", List.of(), 30_000L, 5_000),
                    new com.fasterxml.jackson.databind.ObjectMapper());

            long startedAt = System.nanoTime();
            Map<?, ?> body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(c.systemStatus().getBody(), Map.class);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

            // Bounded NEAR the configured second. "< 10s" would have passed with the budget doing
            // nothing at all beyond stopping the 30s sleep, which is not the claim being made.
            assertTrue(elapsedMs < 3_000,
                    "a 1s budget must end the request in about a second; took " + elapsedMs + "ms");
            Map<?, ?> ledger = (Map<?, ?>) body.get("ledger");
            assertEquals("UNKNOWN", ledger.get("topicsStatus"));
            assertEquals("UNKNOWN", ledger.get("lastRunStatus"));
            Map<?, ?> degraded = (Map<?, ?>) ledger.get("degraded");
            assertEquals("DEADLINE_EXCEEDED", ((Map<?, ?>) degraded.get("topics")).get("code"));
            assertEquals("DEADLINE_EXCEEDED", ((Map<?, ?>) degraded.get("lastRun")).get("code"));
            assertFalse(laterSectionRead.get(),
                    "with the budget spent, later sections must not be started at all");

            // The abandoned task is cancelled, not merely forgotten: otherwise it keeps a pooled
            // connection until its own statement timeout and the pool has two.
            for (int i = 0; i < 100 && !topicsInterrupted.get(); i++) {
                Thread.sleep(20);
            }
            assertTrue(topicsInterrupted.get(), "the overrunning section must be interrupted");
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S");
        }
    }
}
