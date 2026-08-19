package app.feedgateway.systemstatus;

import app.feedgateway.GatewaySettings;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     * The whole-request deadline. Per-section timeouts do not bound a request — four sequential reads
     * can each spend their own allowance. With the budget spent, later sections are UNKNOWN with a
     * reason, never a silent empty result.
     */
    @Test
    void aSpentRequestBudgetSkipsLaterSectionsAsUnknown() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S", "1");
        try {
            SystemStatusStore store = new SystemStatusStore(null, 3) {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public List<Map<String, Object>> topics(String env, int t) {
                    try {
                        Thread.sleep(1_200);   // spends the whole 1s request budget
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of();
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env, int t) throws SQLException {
                    throw new AssertionError("must not be read after the budget is spent");
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env, int t) throws SQLException {
                    throw new AssertionError("must not be read after the budget is spent");
                }

                @Override
                public Map<String, Object> lastRun(String env, int t) throws SQLException {
                    throw new AssertionError("must not be read after the budget is spent");
                }
            };
            SystemStatusController c = new SystemStatusController(new GatewaySettings(), store,
                    new ConsumerLagReader("", List.of(), 30_000L, 5_000), new com.fasterxml.jackson.databind.ObjectMapper());
            Map<?, ?> ledger = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(c.systemStatus().getBody(), Map.class)
                    .get("ledger") instanceof Map<?, ?> m ? m : new LinkedHashMap<>();
            assertEquals("OK", ledger.get("topicsStatus"));
            assertEquals("UNKNOWN", ledger.get("lastRunStatus"));
            Map<?, ?> degraded = (Map<?, ?>) ledger.get("degraded");
            Map<?, ?> lastRun = (Map<?, ?>) degraded.get("lastRun");
            assertEquals("DEADLINE_EXCEEDED", lastRun.get("code"));
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S");
        }
    }
}
