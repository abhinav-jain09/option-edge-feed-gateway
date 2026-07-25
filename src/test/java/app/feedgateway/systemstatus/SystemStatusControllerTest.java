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
}
