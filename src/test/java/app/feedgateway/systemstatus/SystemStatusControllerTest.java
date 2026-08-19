package app.feedgateway.systemstatus;

import app.feedgateway.GatewaySettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The System Status endpoint's honesty contract: absence must never render as health. These are the
 * cases that would otherwise show a reassuring 0 on the page while something is actually broken.
 */
class SystemStatusControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static SystemStatusStore storeThatFails() {
        return new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;   // configured, but every query fails
            }

            @Override
            public List<Map<String, Object>> topics(String env) throws SQLException {
                throw new SQLException("connection refused");
            }
        };
    }

    private static SystemStatusStore storeWith(List<Map<String, Object>> topics,
                                               Map<String, Map<String, Object>> restarts) {
        return new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env) {
                return topics;
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env) {
                return List.of();
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env) {
                return restarts;
            }

            @Override
            public Map<String, Object> lastRun(String env) {
                return new LinkedHashMap<>(Map.of("startedAt", "2026-07-27T13:40:00Z",
                        "outcome", "OK"));
            }
        };
    }

    private static ConsumerLagReader noLag() {
        return new ConsumerLagReader("", List.of(), 30_000L, 5_000);
    }

    private JsonNode call(SystemStatusStore store, ConsumerLagReader lag) throws Exception {
        SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, lag, mapper);
        return mapper.readTree(c.systemStatus().getBody());
    }

    @Test
    void ledgerNotConfiguredIsReportedNotSilentlyEmpty() throws Exception {
        JsonNode out = call(new SystemStatusStore(null, 3), noLag());
        assertFalse(out.path("ledger").path("available").asBoolean());
        assertEquals("NOT_CONFIGURED", out.path("ledger").path("reason").asText());
    }

    @Test
    void ledgerQueryFailureIsReportedAndTopicsSayLedgerUnavailable() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_TOPICS", "options.databento.gex.magnet");
        try {
            JsonNode out = call(storeThatFails(), noLag());
            assertFalse(out.path("ledger").path("available").asBoolean());
            assertEquals("QUERY_FAILED", out.path("ledger").path("reason").asText());
            JsonNode topic = out.path("topics").get(0);
            assertEquals("NO_EVIDENCE", topic.path("evidence").asText());
            assertEquals("LEDGER_UNAVAILABLE", topic.path("reason").asText());
            assertTrue(topic.path("state").isNull(), "state must be null, never a guessed state");
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_TOPICS");
        }
    }

    @Test
    void registeredTopicWithNoEvidenceStillAppears() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_TOPICS",
                "options.databento.gex.magnet,options.databento.gex.strike");
        try {
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("topic", "options.databento.gex.magnet");
            observed.put("state", "HEALTHY");
            observed.put("ageS", 3L);
            observed.put("lastObservation", "ADVANCED");
            observed.put("asOf", "2026-07-27T13:40:00Z");
            JsonNode out = call(storeWith(List.of(observed), Map.of()), noLag());
            assertEquals(2, out.path("topics").size(), "a registered topic must never vanish");
            assertEquals("OK", out.path("topics").get(0).path("evidence").asText());
            assertEquals("NO_EVIDENCE", out.path("topics").get(1).path("evidence").asText());
            assertEquals("NEVER_OBSERVED", out.path("topics").get(1).path("reason").asText());
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_TOPICS");
        }
    }

    @Test
    void restartStatusesSurviveToTheContract() throws Exception {
        Map<String, Map<String, Object>> restarts = new LinkedHashMap<>();
        restarts.put("databento-gex-service", new LinkedHashMap<>(Map.of(
                "sessionKey", "2026-07-27", "restartsThisSession", 2,
                "restartsStatus", "OK", "restartsUnattributedPrior", 0)));
        Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("sessionKey", "2026-07-27");
        unknown.put("restartsThisSession", null);       // sample failed
        unknown.put("restartsStatus", "UNKNOWN");
        restarts.put("delta-flow-service", unknown);
        Map<String, Object> noPods = new LinkedHashMap<>();
        noPods.put("sessionKey", "2026-07-27");
        noPods.put("restartsThisSession", 0);
        noPods.put("restartsStatus", "NO_PODS");
        restarts.put("databento-feed", noPods);

        JsonNode out = call(storeWith(List.of(), restarts), noLag());
        Map<String, JsonNode> byService = new LinkedHashMap<>();
        out.path("services").forEach(n -> byService.put(n.path("service").asText(), n));
        assertEquals(3, byService.size());
        assertEquals(2, byService.get("databento-gex-service").path("restartsThisSession").asInt());
        assertEquals("UNKNOWN", byService.get("delta-flow-service").path("restartsStatus").asText());
        assertTrue(byService.get("delta-flow-service").path("restartsThisSession").isNull(),
                "an UNKNOWN sample must carry null, so the page cannot draw 0");
        assertEquals("NO_PODS", byService.get("databento-feed").path("restartsStatus").asText());
        // lag is not configured here: it must be labelled, not silently 0
        assertEquals("NOT_CONFIGURED", byService.get("databento-feed").path("lagStatus").asText());
    }

    @Test
    void missingTopicRegistryIsReportedAsMisconfiguredNotInferred() throws Exception {
        // Deriving the universe from observed rows would hide a never-observed topic — the exact
        // false-healthy case the expected-universe rule prevents.
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("topic", "options.databento.gex.magnet");
        observed.put("state", "HEALTHY");
        observed.put("ageS", 2L);
        JsonNode out = call(storeWith(List.of(observed), Map.of()), noLag());
        assertEquals(0, out.path("topics").size(),
                "with no registry the page must not invent a universe from evidence");
        assertEquals("MISCONFIGURED", out.path("ledger").path("topicsRegistry").asText());
        assertEquals(0, out.path("topicsRegistrySize").asInt());
    }

    @Test
    void ledgerTransportIsReportedSoAnAcceptedPlaintextLinkStaysVisible() throws Exception {
        // Default: TLS is required for a non-loopback ledger URL.
        System.setProperty("OE_WATCH_JDBC_URL", "jdbc:postgresql://192.168.100.252:5432/options_flow");
        try {
            JsonNode out = call(storeWith(List.of(), Map.of()), noLag());
            assertEquals("TLS_VERIFY_FULL", out.path("ledger").path("transport").asText());
            // Explicit opt-out must be REPORTED, not silent.
            System.setProperty("OE_WATCH_ALLOW_PLAINTEXT", "true");
            out = call(storeWith(List.of(), Map.of()), noLag());
            assertEquals("PLAINTEXT_ACCEPTED", out.path("ledger").path("transport").asText());
            // A loopback URL is the ssh-tunnel endpoint: already encrypted.
            System.clearProperty("OE_WATCH_ALLOW_PLAINTEXT");
            System.setProperty("OE_WATCH_JDBC_URL", "jdbc:postgresql://localhost:15432/options_flow");
            out = call(storeWith(List.of(), Map.of()), noLag());
            assertEquals("LOOPBACK_TUNNEL", out.path("ledger").path("transport").asText());
        } finally {
            System.clearProperty("OE_WATCH_JDBC_URL");
            System.clearProperty("OE_WATCH_ALLOW_PLAINTEXT");
        }
    }

    @Test
    void lagRegistryParsingIgnoresMalformedEntries() {
        List<ConsumerLagReader.Entry> parsed = ConsumerLagReader.parseRegistry(
                "svc-a:group-a:t1,t2; broken-entry ; svc-b:group-b:t3");
        assertEquals(2, parsed.size());
        assertEquals("svc-a", parsed.get(0).service());
        assertEquals(List.of("t1", "t2"), parsed.get(0).topics());
        assertEquals("group-b", parsed.get(1).group());
        assertTrue(ConsumerLagReader.parseRegistry(null).isEmpty());
    }

    @Test
    void unconfiguredLagReaderReportsNotConfiguredRatherThanZero() throws Exception {
        ConsumerLagReader lag = noLag();
        assertFalse(lag.configured());
        JsonNode out = call(storeWith(List.of(), Map.of("svc",
                new LinkedHashMap<>(Map.of("restartsStatus", "OK", "restartsThisSession", 0)))), lag);
        assertEquals("NOT_CONFIGURED", out.path("services").get(0).path("lagStatus").asText());
        assertNull(out.path("services").get(0).path("lagRecordsSum").numberValue());
    }

    /**
     * The prod failure of 2026-08-19: the topic read answered fine, the LAST-RUN read hit its statement
     * timeout, and the whole page went grey — every topic NO_EVIDENCE, every service UNKNOWN — because a
     * single catch wrapped all four reads and the topic rows were only published after the last one
     * returned. Evidence that WAS read must survive a later section failing.
     */
    @Test
    void lastRunFailureDegradesOnlyTheBannerAndKeepsTopicEvidence() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_TOPICS", "options.databento.gex.magnet");
        try {
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("topic", "options.databento.gex.magnet");
            observed.put("state", "HEALTHY");
            observed.put("ageS", 4L);
            SystemStatusStore store = new SystemStatusStore(null, 3) {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public List<Map<String, Object>> topics(String env) {
                    return List.of(observed);
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env) {
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env) {
                    return Map.of("databento-gex-service",
                            new LinkedHashMap<>(Map.of("restartsStatus", "OK",
                                    "restartsThisSession", 0)));
                }

                @Override
                public Map<String, Object> lastRun(String env) throws SQLException {
                    throw new SQLException("ERROR: canceling statement due to user request", "57014");
                }
            };
            JsonNode out = call(store, noLag());

            JsonNode ledger = out.path("ledger");
            assertTrue(ledger.path("available").asBoolean(),
                    "topic evidence was read successfully — the ledger is not unavailable");
            assertTrue(ledger.path("reason").isMissingNode(), "there is no whole-ledger failure to report");
            assertTrue(ledger.path("lastRunOutcome").isMissingNode(),
                    "an unread last run must be ABSENT, never a stale or invented outcome");

            JsonNode degraded = ledger.path("degraded");
            assertTrue(degraded.has("lastRun"), "the failing section must be named");
            assertEquals(1, degraded.size(), "only the section that actually failed is degraded");

            JsonNode topic = out.path("topics").get(0);
            assertEquals("OK", topic.path("evidence").asText(),
                    "evidence already read must not be discarded by a later section failing");
            assertEquals("HEALTHY", topic.path("state").asText());
            assertEquals("OK", out.path("services").get(0).path("restartsStatus").asText());
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_TOPICS");
        }
    }

    /**
     * "PSQLException" alone cannot tell a statement timeout from a bad password from a dropped column,
     * and this envelope is the only place the failure is ever seen — the endpoint swallows it otherwise.
     */
    @Test
    void ledgerFailureCarriesTheDriverMessageAndSqlStateNotJustTheClassName() throws Exception {
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env) throws SQLException {
                throw new SQLException("ERROR: canceling statement due to user request", "57014");
            }
        };
        String error = call(store, noLag()).path("ledger").path("error").asText();
        assertTrue(error.contains("SQLException"), "the class still identifies the failure family");
        assertTrue(error.contains("57014"), "the SQLState is what names it a TIMEOUT: " + error);
        assertTrue(error.contains("canceling statement"), "the driver's own message must survive: " + error);
    }

    /**
     * A restart sample that could not be read is UNKNOWN even when the topic read succeeded — the two
     * are separate evidence and a readable ledger does not make an unread restart count a NO_EVIDENCE 0.
     */
    @Test
    void unreadableRestartsAreUnknownEvenWhenTopicsRead() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_TOPICS", "options.databento.gex.magnet");
        System.setProperty("OE_SYSTEM_STATUS_LAG_REGISTRY",
                "databento-gex-service:databento-gex-service:options.databento.raw");
        try {
            SystemStatusStore store = new SystemStatusStore(null, 3) {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public List<Map<String, Object>> topics(String env) {
                    return List.of();
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env) {
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env) throws SQLException {
                    throw new SQLException("ERROR: canceling statement due to user request", "57014");
                }

                @Override
                public Map<String, Object> lastRun(String env) {
                    return new LinkedHashMap<>(Map.of("startedAt", "2026-08-19T17:00:00Z",
                            "outcome", "OK"));
                }
            };
            JsonNode out = call(store, noLag());
            assertTrue(out.path("ledger").path("available").asBoolean());
            assertTrue(out.path("ledger").path("degraded").has("restarts"));
            for (JsonNode row : out.path("services")) {
                assertEquals("UNKNOWN", row.path("restartsStatus").asText(),
                        "an unread restart sample must not read as a measured 0");
                assertTrue(row.path("restartsThisSession").isNull());
            }
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_TOPICS");
            System.clearProperty("OE_SYSTEM_STATUS_LAG_REGISTRY");
        }
    }
}
