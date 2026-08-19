package app.feedgateway.systemstatus;

import app.feedgateway.GatewaySettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            public List<Map<String, Object>> topics(String env, int timeoutSeconds) throws SQLException {
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
            public List<Map<String, Object>> topics(String env, int timeoutSeconds) {
                return topics;
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env, int timeoutSeconds) {
                return List.of();
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env, int timeoutSeconds) {
                return restarts;
            }

            @Override
            public Map<String, Object> lastRun(String env, int timeoutSeconds) {
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
                public List<Map<String, Object>> topics(String env, int timeoutSeconds) {
                    return List.of(observed);
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env, int timeoutSeconds) {
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env, int timeoutSeconds) {
                    return Map.of("databento-gex-service",
                            new LinkedHashMap<>(Map.of("restartsStatus", "OK",
                                    "restartsThisSession", 0)));
                }

                @Override
                public Map<String, Object> lastRun(String env, int timeoutSeconds) throws SQLException {
                    throw new SQLException("ERROR: canceling statement due to user request", "57014");
                }
            };
            JsonNode out = call(store, noLag());

            JsonNode ledger = out.path("ledger");
            // v1 `available` still means ALL FOUR reads completed, so a page built against v1 stays
            // conservative. The finer truth rides alongside it.
            assertFalse(ledger.path("available").asBoolean(),
                    "one section failed, so the v1 all-or-nothing flag must not claim availability");
            assertTrue(ledger.path("topicsAvailable").asBoolean(),
                    "topic evidence was read successfully and must be advertised as such");
            assertEquals("PARTIAL", ledger.path("reason").asText());
            assertEquals("OK", ledger.path("topicsStatus").asText());
            assertEquals("OK", ledger.path("restartsStatus").asText());
            assertEquals("UNKNOWN", ledger.path("lastRunStatus").asText());
            assertTrue(ledger.has("lastRunAt"), "the key must exist so the page tests a value, not undefined");
            assertTrue(ledger.path("lastRunAt").isNull(), "an unread last run is null, never invented");
            assertTrue(ledger.path("lastRunOutcome").isNull(),
                    "an unread last run must never carry a stale or invented outcome");

            JsonNode degraded = ledger.path("degraded");
            assertTrue(degraded.has("lastRun"), "the failing section must be named");
            assertEquals(1, degraded.size(), "only the section that actually failed is degraded");
            assertEquals("STATEMENT_TIMEOUT", degraded.path("lastRun").path("code").asText());

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
     * The browser gets a CODE, never the driver's sentence. "PSQLException" alone could not tell a
     * statement timeout from a bad password from a dropped column — that was the diagnostic hole — but
     * a PostgreSQL message carries schema names, SQL fragments, {@code Where:} context and whatever a
     * server function chose to raise, and a JDBC failure can echo connection properties. So the page
     * gets a closed vocabulary and the SQLState; the sentence goes to the log.
     */
    @Test
    void browserSeesASafeCodeAndSqlStateNeverTheDriverSentence() throws Exception {
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int timeoutSeconds) throws SQLException {
                throw new SQLException("ERROR: canceling statement due to user request\n  Where: "
                        + "oe_watch.v_topic_state, password=hunter2 jdbc:postgresql://oe:s3cret@host/db",
                        "57014");
            }
        };
        JsonNode ledger = call(store, noLag()).path("ledger");
        assertEquals("QUERY_FAILED", ledger.path("reason").asText());
        assertEquals("STATEMENT_TIMEOUT", ledger.path("error").asText(),
                "the v1 error field keeps its string shape but carries a SAFE classified code");
        JsonNode topicsFailure = ledger.path("degraded").path("topics");
        assertEquals("STATEMENT_TIMEOUT", topicsFailure.path("code").asText());
        assertEquals("57014", topicsFailure.path("sqlState").asText());
        String whole = call(store, noLag()).toString();
        assertFalse(whole.contains("canceling statement"),
                "the driver's sentence must not reach the browser: " + whole);
        assertFalse(whole.contains("hunter2") || whole.contains("s3cret"),
                "no credential may reach the browser: " + whole);
        assertFalse(whole.contains("Where:"), "no server context may reach the browser: " + whole);
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
                public List<Map<String, Object>> topics(String env, int timeoutSeconds) {
                    return List.of();
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env, int timeoutSeconds) {
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env, int timeoutSeconds) throws SQLException {
                    throw new SQLException("ERROR: canceling statement due to user request", "57014");
                }

                @Override
                public Map<String, Object> lastRun(String env, int timeoutSeconds) {
                    return new LinkedHashMap<>(Map.of("startedAt", "2026-08-19T17:00:00Z",
                            "outcome", "OK"));
                }
            };
            JsonNode out = call(store, noLag());
            assertTrue(out.path("ledger").path("topicsAvailable").asBoolean());
            assertFalse(out.path("ledger").path("available").asBoolean());
            assertEquals("UNKNOWN", out.path("ledger").path("restartsStatus").asText());
            assertTrue(out.path("ledger").path("degraded").has("restarts"));
            // The universe comes from the REGISTRY, so the row exists even though neither evidence
            // source produced it. Asserted by exact size + name: a zero-iteration loop over an empty
            // services array is how this test passed vacuously before.
            assertEquals(1, out.path("services").size(),
                    "the registered service must still appear: " + out.path("services"));
            JsonNode row = out.path("services").get(0);
            assertEquals("databento-gex-service", row.path("service").asText());
            assertEquals("UNKNOWN", row.path("restartsStatus").asText(),
                    "an unread restart sample must not read as a measured 0");
            assertTrue(row.path("restartsThisSession").isNull());
            assertEquals("NOT_CONFIGURED", row.path("lagStatus").asText());
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_TOPICS");
            System.clearProperty("OE_SYSTEM_STATUS_LAG_REGISTRY");
        }
    }

    /** A store whose sections each behave as the test asks; unspecified sections succeed emptily. */
    private static SystemStatusStore storeWhere(SectionBehaviour topics, SectionBehaviour incidents,
                                                SectionBehaviour restarts, SectionBehaviour lastRun) {
        return new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            @SuppressWarnings("unchecked")
            public List<Map<String, Object>> topics(String env, int t) throws SQLException {
                return (List<Map<String, Object>>) topics.apply(List.of());
            }

            @Override
            @SuppressWarnings("unchecked")
            public List<Map<String, Object>> openIncidents(String env, int t) throws SQLException {
                return (List<Map<String, Object>>) incidents.apply(List.of());
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Map<String, Object>> restarts(String env, int t) throws SQLException {
                return (Map<String, Map<String, Object>>) restarts.apply(Map.of());
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> lastRun(String env, int t) throws SQLException {
                return (Map<String, Object>) lastRun.apply(new LinkedHashMap<String, Object>());
            }
        };
    }

    private interface SectionBehaviour {
        Object apply(Object emptyValue) throws SQLException;
    }

    private static final SectionBehaviour SUCCEEDS = empty -> empty;
    private static final SectionBehaviour TIMES_OUT = empty -> {
        throw new SQLException("ERROR: canceling statement due to user request", "57014");
    };

    /**
     * An empty incident list is what a HEALTHY system looks like, so "could not read the incidents" must
     * never arrive as one. Without an explicit status the page renders "no open incidents" over an
     * unread section — absence as health, the exact failure mode this endpoint exists to prevent.
     */
    @Test
    void unreadableIncidentsAreNotAnEmptyIncidentList() throws Exception {
        JsonNode out = call(storeWhere(SUCCEEDS, TIMES_OUT, SUCCEEDS, SUCCEEDS), noLag());
        JsonNode ledger = out.path("ledger");
        assertEquals("UNKNOWN", ledger.path("openIncidentsStatus").asText(),
                "an unread incident section must say so; the empty array alone cannot be trusted");
        assertEquals("OK", ledger.path("topicsStatus").asText());
        assertTrue(ledger.path("degraded").has("openIncidents"));
        assertEquals(0, out.path("openIncidents").size());
    }

    /** The mirror case of the prod incident: topics unreadable while the restart sample came back. */
    @Test
    void topicsFailureLeavesRestartEvidenceIntact() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_TOPICS", "options.databento.gex.magnet");
        try {
            SystemStatusStore store = storeWhere(TIMES_OUT, SUCCEEDS,
                    empty -> Map.of("databento-gex-service",
                            new LinkedHashMap<>(Map.of("restartsStatus", "OK", "restartsThisSession", 2))),
                    SUCCEEDS);
            JsonNode out = call(store, noLag());
            assertFalse(out.path("ledger").path("topicsAvailable").asBoolean());
            assertEquals("QUERY_FAILED", out.path("ledger").path("reason").asText());
            assertEquals("LEDGER_UNAVAILABLE", out.path("topics").get(0).path("reason").asText());
            JsonNode row = out.path("services").get(0);
            assertEquals("OK", row.path("restartsStatus").asText(),
                    "a restart sample that WAS read must survive the topic read failing");
            assertEquals(2, row.path("restartsThisSession").asInt());
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_TOPICS");
        }
    }

    /**
     * A ledger with no run yet is not a failure. Both keys must still be PRESENT and null — a missing
     * key renders as "undefined" in a browser and Date.parse(undefined) is Invalid Date, whereas an
     * explicit null is a value the page can test.
     */
    @Test
    void noLastRunRowIsNullNotMissingAndNotAFailure() throws Exception {
        JsonNode ledger = call(storeWhere(SUCCEEDS, SUCCEEDS, SUCCEEDS, SUCCEEDS), noLag()).path("ledger");
        assertEquals("OK", ledger.path("lastRunStatus").asText(), "no rows is not a query failure");
        assertTrue(ledger.path("available").asBoolean(), "all four reads completed");
        assertTrue(ledger.has("lastRunAt") && ledger.path("lastRunAt").isNull());
        assertTrue(ledger.has("lastRunOutcome") && ledger.path("lastRunOutcome").isNull());
        assertFalse(ledger.has("degraded"));
    }

    /** A run that started but has not finished carries a null outcome, not an invented one. */
    @Test
    void inFlightRunReportsANullOutcome() throws Exception {
        SystemStatusStore store = storeWhere(SUCCEEDS, SUCCEEDS, SUCCEEDS, empty -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("startedAt", "2026-08-19T17:00:00Z");
            row.put("outcome", null);
            return row;
        });
        JsonNode ledger = call(store, noLag()).path("ledger");
        assertEquals("OK", ledger.path("lastRunStatus").asText());
        assertEquals("2026-08-19T17:00:00Z", ledger.path("lastRunAt").asText());
        assertTrue(ledger.path("lastRunOutcome").isNull());
    }

    /** A store that hands back null is a gateway bug, not a query failure — and must not escape. */
    @Test
    void aNullSectionResultIsAnInternalErrorNotAQueryFailure() throws Exception {
        JsonNode ledger = call(storeWhere(empty -> null, SUCCEEDS, SUCCEEDS, SUCCEEDS), noLag())
                .path("ledger");
        assertEquals("UNKNOWN", ledger.path("topicsStatus").asText());
        assertEquals("INTERNAL_ERROR", ledger.path("degraded").path("topics").path("code").asText(),
                "a programming fault must not be reported as a Postgres query failure");
    }

    /**
     * The bulkhead COALESCES. The ledger pool is two connections and this endpoint shares the gateway's
     * request threads, so N concurrent status requests must not become N reads. Exactly one read runs
     * and the second request receives THAT result — not synthetic data while a real answer is seconds
     * away, and not its own trip to Postgres.
     */
    @Test
    void concurrentRequestsShareOneLedgerReadInsteadOfStackingUp() throws Exception {
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("topic", "options.databento.raw");
        observed.put("state", "HEALTHY");
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) {
                reads.incrementAndGet();
                inside.countDown();
                try {
                    release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of(observed);
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env, int t) {
                return List.of();
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env, int t) {
                return Map.of();
            }

            @Override
            public Map<String, Object> lastRun(String env, int t) {
                return new LinkedHashMap<>();
            }
        };
        System.setProperty("OE_SYSTEM_STATUS_TOPICS", "options.databento.raw");
        try {
            SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
            java.util.concurrent.atomic.AtomicReference<String> firstBody =
                    new java.util.concurrent.atomic.AtomicReference<>();
            Thread slow = new Thread(() -> {
                try {
                    firstBody.set(c.systemStatus().getBody());
                } catch (Exception ignored) {
                    // the assertions below are on the READ COUNT and the shared payload
                }
            });
            slow.start();
            assertTrue(inside.await(5, java.util.concurrent.TimeUnit.SECONDS), "first read never started");
            release.countDown();

            JsonNode second = mapper.readTree(c.systemStatus().getBody());
            slow.join(5_000);

            assertEquals(1, reads.get(), "a second request must not start a second ledger read");
            assertEquals("OK", second.path("ledger").path("topicsStatus").asText(),
                    "the waiting request receives the REAL shared result, not synthetic data");
            assertEquals("HEALTHY", second.path("topics").get(0).path("state").asText());
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_TOPICS");
        }
    }

    /** When the in-flight read cannot finish inside the budget and nothing was ever cached: UNKNOWN. */
    @Test
    void aWaiterThatCannotWaitGetsUnknownNotAFabricatedEmptyRead() throws Exception {
        // The in-flight read gets a long budget; the WAITER's budget is then shortened, so the waiter
        // provably cannot receive the shared result and must fall back. Sharing one budget would make
        // this race non-deterministic — and would pass for the wrong reason, since a waiter that DOES
        // get the shared result is the better outcome and is covered by the test above.
        System.setProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S", "30");
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try {
            SystemStatusStore store = new SystemStatusStore(null, 3) {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public List<Map<String, Object>> topics(String env, int t) {
                    inside.countDown();
                    try {
                        release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of();
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env, int t) {
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env, int t) {
                    return Map.of();
                }

                @Override
                public Map<String, Object> lastRun(String env, int t) {
                    return new LinkedHashMap<>();
                }
            };
            SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
            Thread slow = new Thread(() -> {
                try {
                    c.systemStatus();
                } catch (Exception ignored) {
                    // this thread's payload is not what is asserted
                }
            });
            slow.start();
            assertTrue(inside.await(5, java.util.concurrent.TimeUnit.SECONDS), "first read never started");
            System.setProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S", "1");

            long startedAt = System.nanoTime();
            JsonNode out = mapper.readTree(c.systemStatus().getBody());
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            assertEquals("UNKNOWN", out.path("ledger").path("topicsStatus").asText());
            assertEquals("SATURATED", out.path("ledger").path("degraded").path("topics").path("code").asText(),
                    "with nothing cached and no shared result in time, the honest answer is UNKNOWN");
            // The cold-start wait is clamped to the request budget. Unclamped it was a fixed 2s, so a
            // 1s budget bought a 2s wait — the follower outlasting the very budget it was given.
            assertTrue(elapsedMs < 1_800,
                    "a 1s budget must not buy a 2s cold-start wait; took " + elapsedMs + "ms");
            release.countDown();
            slow.join(5_000);
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S");
        }
    }

    /** A fresh snapshot is reused: the page refreshes every 2 minutes, the pool has two connections. */
    @Test
    void aFreshSnapshotIsServedWithoutRereadingTheLedger() throws Exception {
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) {
                reads.incrementAndGet();
                return List.of();
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env, int t) {
                return List.of();
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env, int t) {
                return Map.of();
            }

            @Override
            public Map<String, Object> lastRun(String env, int t) {
                return new LinkedHashMap<>();
            }
        };
        SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
        c.systemStatus();
        JsonNode second = mapper.readTree(c.systemStatus().getBody());
        assertEquals(1, reads.get(), "the second request inside the cache window must not re-read");
        assertTrue(second.path("ledger").path("snapshotAgeMs").asLong() >= 0,
                "the page is told how old the evidence is, so a cached read is never passed off as live");
    }

    /**
     * The envelope has ONE shape on every path. With no ledger configured the sections were previously
     * omitted entirely, so a page gating "no open incidents" on openIncidentsStatus saw `undefined`,
     * fell through, and rendered the healthy view over a ledger it had never read.
     */
    @Test
    void anUnconfiguredLedgerStillNamesEverySection() throws Exception {
        JsonNode ledger = call(new SystemStatusStore(null, 3), noLag()).path("ledger");
        assertEquals("NOT_CONFIGURED", ledger.path("reason").asText());
        assertFalse(ledger.path("available").asBoolean());
        assertFalse(ledger.path("topicsAvailable").asBoolean());
        for (String section : List.of("topicsStatus", "openIncidentsStatus", "restartsStatus",
                "lastRunStatus")) {
            assertEquals("UNKNOWN", ledger.path(section).asText(), section + " must be named");
        }
        assertTrue(ledger.has("lastRunAt") && ledger.path("lastRunAt").isNull());
        assertTrue(ledger.has("lastRunOutcome") && ledger.path("lastRunOutcome").isNull());
    }

    /**
     * A snapshot past the max-stale line stops being evidence. Proved on ONE controller: prime a real
     * successful read, let it age past the limit, then force the aged snapshot to be SERVED by holding
     * the refresh gate from another thread. Accepting "STALE or UNKNOWN" would have passed on the
     * trivially-produced UNKNOWN and proved nothing about staleness at all.
     */
    @Test
    void anAgedSnapshotIsServedAsStaleAndNeverAsEvidence() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_TOPICS", "options.databento.raw");
        System.setProperty("OE_SYSTEM_STATUS_CACHE_MS", "1000");
        System.setProperty("OE_SYSTEM_STATUS_MAX_STALE_MS", "1000");
        java.util.concurrent.CountDownLatch secondReadStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try {
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("topic", "options.databento.raw");
            observed.put("state", "HEALTHY");
            java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
            SystemStatusStore store = new SystemStatusStore(null, 3) {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public List<Map<String, Object>> topics(String env, int t) {
                    if (reads.incrementAndGet() > 1) {
                        secondReadStarted.countDown();
                        try {
                            release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return List.of(observed);
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env, int t) {
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env, int t) {
                    return Map.of();
                }

                @Override
                public Map<String, Object> lastRun(String env, int t) {
                    return new LinkedHashMap<>();
                }
            };
            SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);

            JsonNode fresh = mapper.readTree(c.systemStatus().getBody());
            assertEquals("OK", fresh.path("ledger").path("topicsStatus").asText());
            assertEquals("OK", fresh.path("topics").get(0).path("evidence").asText());

            Thread.sleep(1_100);                       // now past both the reuse window and max-stale
            Thread holder = new Thread(() -> {             // takes the refresh gate and blocks in it
                try {
                    c.systemStatus();
                } catch (Exception ignored) {
                    // this thread's payload is not what is asserted
                }
            });
            holder.start();
            assertTrue(secondReadStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));

            JsonNode aged = mapper.readTree(c.systemStatus().getBody());
            JsonNode ledger = aged.path("ledger");
            assertEquals("SNAPSHOT_STALE", ledger.path("reason").asText(),
                    "the aged snapshot must be served AS stale, not silently reused: " + ledger);
            assertEquals("STALE", ledger.path("topicsStatus").asText());
            assertFalse(ledger.path("available").asBoolean());
            assertFalse(ledger.path("topicsAvailable").asBoolean());
            assertTrue(ledger.path("snapshotAgeMs").asLong() > ledger.path("maxStaleMs").asLong(),
                    "the published age must itself be past the limit it was judged against");
            // The consequence that matters: the retained row is visible but is NOT evidence.
            JsonNode topic = aged.path("topics").get(0);
            assertEquals("STALE", topic.path("evidence").asText(),
                    "a retained row must never still claim evidence=OK: " + topic);
            assertEquals("HEALTHY", topic.path("state").asText(), "what it WAS is still shown");
            assertEquals("SNAPSHOT_STALE", topic.path("reason").asText());

            release.countDown();
            holder.join(5_000);
        } finally {
            release.countDown();
            System.clearProperty("OE_SYSTEM_STATUS_TOPICS");
            System.clearProperty("OE_SYSTEM_STATUS_CACHE_MS");
            System.clearProperty("OE_SYSTEM_STATUS_MAX_STALE_MS");
        }
    }

    /**
     * Reuse is bounded by BOTH windows. Honouring only the cache window let a short max-stale be
     * defeated by a long cache one: the endpoint would keep serving evidence already past its own
     * staleness limit, without ever attempting a refresh.
     */
    @Test
    void aShortMaxStaleIsNotDefeatedByALongCacheWindow() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_CACHE_MS", "300000");
        System.setProperty("OE_SYSTEM_STATUS_MAX_STALE_MS", "1000");
        try {
            java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
            SystemStatusStore store = new SystemStatusStore(null, 3) {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public List<Map<String, Object>> topics(String env, int t) {
                    reads.incrementAndGet();
                    return List.of();
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env, int t) {
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env, int t) {
                    return Map.of();
                }

                @Override
                public Map<String, Object> lastRun(String env, int t) {
                    return new LinkedHashMap<>();
                }
            };
            SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
            c.systemStatus();
            Thread.sleep(1_100);
            c.systemStatus();
            assertEquals(2, reads.get(),
                    "past max-stale the endpoint must attempt a refresh even inside the cache window");
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_CACHE_MS");
            System.clearProperty("OE_SYSTEM_STATUS_MAX_STALE_MS");
        }
    }

    /**
     * The cache is keyed by environment. Reusing a snapshot across an env change would relabel one
     * environment's evidence as another's — the page's entire purpose is that its rows are true of the
     * env named in the header.
     */
    @Test
    void aSnapshotIsNeverReusedAcrossAnEnvironmentChange() throws Exception {
        java.util.List<String> envsRead = java.util.Collections.synchronizedList(new ArrayList<>());
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) {
                envsRead.add(env);
                return List.of();
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env, int t) {
                return List.of();
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env, int t) {
                return Map.of();
            }

            @Override
            public Map<String, Object> lastRun(String env, int t) {
                return new LinkedHashMap<>();
            }
        };
        SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
        System.setProperty("OE_ENV", "prod");
        try {
            assertEquals("prod", mapper.readTree(c.systemStatus().getBody()).path("env").asText());
            System.setProperty("OE_ENV", "es4");
            JsonNode second = mapper.readTree(c.systemStatus().getBody());
            assertEquals("es4", second.path("env").asText());
            assertEquals(List.of("prod", "es4"), envsRead,
                    "the es4 request must read es4's ledger, not relabel prod's snapshot");
        } finally {
            System.clearProperty("OE_ENV");
        }
    }

    /**
     * A contended request must not block on a flight it can never use. Waiting on another
     * environment's refresh pins a shared REST request thread for the whole budget to learn nothing.
     */
    @Test
    void aRequestNeverWaitsOnAnotherEnvironmentsInFlightRead() throws Exception {
        System.setProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S", "30");
        System.setProperty("OE_ENV", "prod");
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try {
            SystemStatusStore store = new SystemStatusStore(null, 3) {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public List<Map<String, Object>> topics(String env, int t) {
                    inside.countDown();
                    try {
                        release.await(20, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of();
                }

                @Override
                public List<Map<String, Object>> openIncidents(String env, int t) {
                    return List.of();
                }

                @Override
                public Map<String, Map<String, Object>> restarts(String env, int t) {
                    return Map.of();
                }

                @Override
                public Map<String, Object> lastRun(String env, int t) {
                    return new LinkedHashMap<>();
                }
            };
            SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
            Thread prodReader = new Thread(() -> {
                try {
                    c.systemStatus();
                } catch (Exception ignored) {
                    // not the subject of this assertion
                }
            });
            prodReader.start();
            assertTrue(inside.await(5, java.util.concurrent.TimeUnit.SECONDS), "prod read never started");

            System.setProperty("OE_ENV", "es4");
            long startedAt = System.nanoTime();
            JsonNode es4 = mapper.readTree(c.systemStatus().getBody());
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

            assertTrue(elapsedMs < 2_000,
                    "es4 waited " + elapsedMs + "ms on a prod flight it could never use");
            assertEquals("es4", es4.path("env").asText());
            assertEquals("UNKNOWN", es4.path("ledger").path("topicsStatus").asText());
            release.countDown();
            prodReader.join(5_000);
        } finally {
            release.countDown();
            System.clearProperty("OE_SYSTEM_STATUS_REQUEST_BUDGET_S");
            System.clearProperty("OE_ENV");
        }
    }

    /** The executor is shut down with the Spring context, not left running over a closed datasource. */
    @Test
    void destroyStopsTheSectionExecutor() throws Exception {
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) {
                inside.countDown();
                try {
                    Thread.sleep(20_000);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
                return List.of();
            }
        };
        SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
        Thread reader = new Thread(() -> {
            try {
                c.systemStatus();
            } catch (Exception ignored) {
                // not the subject of this assertion
            }
        });
        reader.start();
        assertTrue(inside.await(5, java.util.concurrent.TimeUnit.SECONDS));
        c.destroy();
        for (int i = 0; i < 100 && !interrupted.get(); i++) {
            Thread.sleep(20);
        }
        assertTrue(interrupted.get(), "context shutdown must stop in-flight section work");
        reader.join(5_000);
    }

    /**
     * A JDBC read blocked on a socket does not answer an interrupt — it ends when its own socket
     * timeout fires. destroy() must not RETURN while such a task is still running, or the datasource
     * is closed underneath it. Modelled with a task that deliberately swallows the first interrupt.
     */
    @Test
    void destroyWaitsForAnInterruptDeafSection() throws Exception {
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean stillRunning =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) {
                inside.countDown();
                long until = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(600);
                while (System.nanoTime() < until) {
                    try {
                        Thread.sleep(50);   // swallowed: the first interrupt does NOT end this work
                    } catch (InterruptedException ignored) {
                        // deliberately deaf, exactly like a socket read
                    }
                }
                stillRunning.set(false);
                return List.of();
            }
        };
        SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
        Thread reader = new Thread(() -> {
            try {
                c.systemStatus();
            } catch (Exception ignored) {
                // not the subject of this assertion
            }
        });
        reader.start();
        assertTrue(inside.await(5, java.util.concurrent.TimeUnit.SECONDS));

        c.destroy();
        assertFalse(stillRunning.get(),
                "destroy() returned while a section was still running over the datasource it is about "
                        + "to close");
        reader.join(5_000);
    }

    /** Once shutdown begins, no NEW read may start against a datasource being torn down. */
    @Test
    void noNewLedgerReadStartsWhileClosing() throws Exception {
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) {
                reads.incrementAndGet();
                return List.of();
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env, int t) {
                return List.of();
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env, int t) {
                return Map.of();
            }

            @Override
            public Map<String, Object> lastRun(String env, int t) {
                return new LinkedHashMap<>();
            }
        };
        SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
        c.destroy();
        JsonNode out = mapper.readTree(c.systemStatus().getBody());
        assertEquals(0, reads.get(), "a read started during shutdown races the datasource teardown");
        assertEquals("UNKNOWN", out.path("ledger").path("topicsStatus").asText());
        assertEquals("SHUTTING_DOWN",
                out.path("ledger").path("degraded").path("topics").path("code").asText());
    }

    /**
     * The INTERLEAVING, not just the after-the-fact case: a request already inside the endpoint, past
     * its admission check, must not go on submitting further sections once shutdown begins. Testing
     * only a request issued after destroy() returned cannot reach this window at all.
     */
    @Test
    void aRequestAlreadyInFlightStopsSubmittingSectionsOnceShutdownBegins() throws Exception {
        java.util.concurrent.CountDownLatch firstSectionRunning = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch shutdownStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean laterSectionEnteredTheStore =
                new java.util.concurrent.atomic.AtomicBoolean();
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) {
                firstSectionRunning.countDown();
                try {
                    shutdownStarted.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of();
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env, int t) {
                laterSectionEnteredTheStore.set(true);
                return List.of();
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env, int t) {
                laterSectionEnteredTheStore.set(true);
                return Map.of();
            }

            @Override
            public Map<String, Object> lastRun(String env, int t) {
                laterSectionEnteredTheStore.set(true);
                return new LinkedHashMap<>();
            }
        };
        SystemStatusController c = new SystemStatusController(new GatewaySettings(), store, noLag(), mapper);
        java.util.concurrent.atomic.AtomicReference<String> body =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread request = new Thread(() -> {
            try {
                body.set(c.systemStatus().getBody());
            } catch (Exception ignored) {
                // the assertion is on what the store was asked to do, not on this payload
            }
        });
        request.start();
        assertTrue(firstSectionRunning.await(5, java.util.concurrent.TimeUnit.SECONDS));

        Thread closer = new Thread(() -> {
            try {
                c.destroy();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        closer.start();
        Thread.sleep(100);          // let destroy() take the lock and set closing
        shutdownStarted.countDown();  // now release the section that is mid-flight

        request.join(15_000);
        closer.join(15_000);
        assertFalse(laterSectionEnteredTheStore.get(),
                "a section submitted after shutdown began must not reach the datasource being closed");
        assertNotNull(body.get(), "the request must still answer, degraded, rather than hang or throw");
        JsonNode ledger = mapper.readTree(body.get()).path("ledger");
        assertEquals("SHUTTING_DOWN", ledger.path("degraded").path("lastRun").path("code").asText(),
                "and it must SAY why the later sections are missing: " + ledger);
    }
}
