package app.feedgateway.systemstatus;

import app.feedgateway.GatewaySettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/system-status} — the System Status page's single source (design §3.5/§3.6.6).
 *
 * <p>Composes two independent sources so one failing never blanks the other:
 * <ol>
 *   <li>the watcher's verification ledger ({@code oe_watch} views) for topic freshness, open incidents
 *       and per-service restarts-this-session;</li>
 *   <li>live consumer lag from the broker via AdminClient, over an allowlisted service registry.</li>
 * </ol>
 *
 * <p><b>Every absence is explicit.</b> The topics array is the LEFT JOIN of the deployed watcher registry
 * onto the ledger, so a registered topic with no evidence renders {@code evidence=NO_EVIDENCE} with a
 * reason — never a health-coloured guess. Lag rows carry a discriminated {@code lagStatus}; restart rows
 * carry {@code restartsStatus} (OK / PARTIAL / NO_PODS / UNKNOWN) with a nullable number. The endpoint
 * always answers 200 with a status envelope: a broken dependency is information for the page, not an error
 * that hides the rest of the picture.
 */
@RestController
public class SystemStatusController {

    private static final Logger LOG = LoggerFactory.getLogger(SystemStatusController.class);

    private final GatewaySettings settings;
    private final SystemStatusStore store;
    private final ConsumerLagReader lag;
    private final ObjectMapper mapper;

    public SystemStatusController(GatewaySettings settings, SystemStatusStore store,
                                  ConsumerLagReader lag, ObjectMapper mapper) {
        this.settings = settings;
        this.store = store;
        this.lag = lag;
        this.mapper = mapper;
    }

    @GetMapping(value = "/api/system-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> systemStatus() throws Exception {
        String env = settings.systemStatusEnv();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contractVersion", 1);
        out.put("env", env);
        out.put("generatedAt", Instant.now().toString());

        Map<String, Object> ledger = new LinkedHashMap<>();
        List<Map<String, Object>> topics = new ArrayList<>();
        Map<String, Map<String, Object>> restarts = Map.of();
        List<Map<String, Object>> incidents = List.of();
        boolean topicsOk = false;
        boolean restartsOk = false;
        if (!store.configured()) {
            ledger.put("available", false);
            ledger.put("reason", "NOT_CONFIGURED");
            ledger.put("transport", transport());
        } else {
            // Each ledger section is read INDEPENDENTLY. One slow view (a statement timeout on the
            // last-run lookup, say) must not blank the topic evidence that was already read
            // successfully — that is the same "one failing never blanks the other" rule this endpoint
            // already applies between the ledger and the broker, applied inside the ledger too.
            Map<String, Object> failures = new LinkedHashMap<>();
            Map<String, Object> lastRun = Map.of();
            try {
                topics = store.topics(env);
                topicsOk = true;
            } catch (Exception e) {
                failures.put("topics", describe("topics", e));
            }
            try {
                incidents = store.openIncidents(env);
            } catch (Exception e) {
                failures.put("openIncidents", describe("openIncidents", e));
            }
            try {
                restarts = store.restarts(env);
                restartsOk = true;
            } catch (Exception e) {
                failures.put("restarts", describe("restarts", e));
            }
            try {
                lastRun = store.lastRun(env);
            } catch (Exception e) {
                failures.put("lastRun", describe("lastRun", e));
            }
            // available tracks the TOPIC evidence specifically: that is what the topic rows below are
            // joined onto. A failed last-run lookup degrades the freshness banner, not the whole page.
            ledger.put("available", topicsOk);
            if (!topicsOk) {
                ledger.put("reason", "QUERY_FAILED");
                ledger.put("error", failures.get("topics"));
            }
            if (!failures.containsKey("lastRun")) {
                ledger.put("lastRunAt", lastRun.get("startedAt"));
                ledger.put("lastRunOutcome", lastRun.get("outcome"));
            }
            ledger.put("transport", transport());
            if (!failures.isEmpty()) {
                // Named per section so the page can say WHICH evidence is missing instead of going grey
                // as a whole, and so the operator sees the driver's own message rather than a class name.
                ledger.put("degraded", failures);
            }
        }
        out.put("ledger", ledger);

        // Expected-universe rule: one row per REGISTERED topic; ledger evidence is joined on.
        List<String> registered = registeredTopics();
        Map<String, Map<String, Object>> byTopic = new LinkedHashMap<>();
        for (Map<String, Object> row : topics) {
            byTopic.put(String.valueOf(row.get("topic")), row);
        }
        List<Map<String, Object>> topicRows = new ArrayList<>();
        if (registered.isEmpty()) {
            // Deriving the universe from observed rows would hide a never-observed (or newly
            // registered) topic — the exact false-healthy case the expected-universe rule exists to
            // prevent. A missing registry is a MISCONFIGURATION and says so.
            Map<String, Object> misconfig = new LinkedHashMap<>(ledger);
            misconfig.put("topicsRegistry", "MISCONFIGURED");
            misconfig.put("topicsRegistryReason",
                    "OE_SYSTEM_STATUS_TOPICS is not set: the expected topic universe is unknown");
            out.put("ledger", misconfig);
        }
        for (String topic : registered) {
            Map<String, Object> evidence = byTopic.get(topic);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("topic", topic);
            if (evidence == null) {
                row.put("evidence", "NO_EVIDENCE");
                row.put("state", null);
                row.put("ageS", null);
                row.put("reason", Boolean.TRUE.equals(ledger.get("available"))
                        ? "NEVER_OBSERVED" : "LEDGER_UNAVAILABLE");
            } else {
                row.put("evidence", "OK");
                row.put("state", evidence.get("state"));
                row.put("ageS", evidence.get("ageS"));
                row.put("lastObservation", evidence.get("lastObservation"));
                row.put("asOf", evidence.get("asOf"));
                // The judging context, passed through verbatim including nulls. The page needs the
                // threshold to say "268s of 300s" instead of a bare "268s", and the guard to show a
                // stalled root AS the root rather than reddening everything behind it.
                for (String k : new String[]{"thresholdS", "guard", "guardLeaseLeftS",
                        "consecStale", "consecOk", "phase", "wouldHaveFired", "shadow"}) {
                    row.put(k, evidence.get(k));
                }
            }
            topicRows.add(row);
        }
        out.put("topics", topicRows);
        out.put("topicsRegistrySize", registered.size());
        out.put("openIncidents", incidents);

        // Services: lag (live) + restarts-this-session (ledger), merged by service name.
        List<Map<String, Object>> serviceRows = new ArrayList<>();
        List<Map<String, Object>> lagRows = lag.configured() ? lag.services() : List.of();
        Map<String, Map<String, Object>> byService = new LinkedHashMap<>();
        for (Map<String, Object> row : lagRows) {
            byService.put(String.valueOf(row.get("service")), new LinkedHashMap<>(row));
        }
        for (Map.Entry<String, Map<String, Object>> e : restarts.entrySet()) {
            byService.computeIfAbsent(e.getKey(), k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("service", k);
                row.put("lagStatus", lag.configured() ? "UNKNOWN" : "NOT_CONFIGURED");
                row.put("lagRecordsSum", null);
                row.put("lagRecordsMax", null);
                return row;
            }).putAll(e.getValue());
        }
        for (Map<String, Object> row : byService.values()) {
            row.putIfAbsent("restartsStatus", restartsOk ? "NO_EVIDENCE" : "UNKNOWN");
            row.putIfAbsent("restartsThisSession", null);
            serviceRows.add(row);
        }
        serviceRows.sort((a, b) -> String.valueOf(a.get("service"))
                .compareTo(String.valueOf(b.get("service"))));
        out.put("services", serviceRows);
        if (lag.error() != null) {
            out.put("lagError", lag.error());
        }
        return ResponseEntity.ok(mapper.writeValueAsString(out));
    }

    /**
     * The driver's own message, not just the exception class: "PSQLException" alone cannot distinguish a
     * statement timeout from a bad password from a dropped column, and this endpoint is the only place
     * the failure is ever seen. Logged at WARN too, because a page nobody is looking at is not evidence.
     */
    private static String describe(String section, Exception e) {
        String sqlState = (e instanceof SQLException sql) ? sql.getSQLState() : null;
        String message = e.getMessage() == null ? "" : e.getMessage().replace('\n', ' ').trim();
        StringBuilder sb = new StringBuilder(e.getClass().getSimpleName());
        if (sqlState != null && !sqlState.isBlank()) {
            sb.append(" [SQLState ").append(sqlState).append(']');
        }
        if (!message.isEmpty()) {
            sb.append(": ").append(message);
        }
        String described = sb.toString();
        LOG.warn("system-status ledger section {} failed: {}", section, described, e);
        return described;
    }

    /**
     * How the ledger link is protected. Surfaced so an accepted plaintext link is VISIBLE on the page
     * rather than a silent posture nobody revisits.
     */
    private String transport() {
        String url = settings.systemStatusJdbcUrl();
        if (url == null || url.isBlank()) {
            return "NONE";
        }
        if (url.contains("//localhost") || url.contains("//127.0.0.1") || url.contains("//[::1]")) {
            return "LOOPBACK_TUNNEL";
        }
        return settings.systemStatusAllowPlaintext() ? "PLAINTEXT_ACCEPTED" : "TLS_VERIFY_FULL";
    }

    /**
     * The deployed watcher topic registry, mirrored into gateway config so a registered topic can never
     * silently vanish from the page. Format: comma-separated topic names.
     */
    private List<String> registeredTopics() {
        String raw = GatewaySettings.value("OE_SYSTEM_STATUS_TOPICS", "");
        List<String> out = new ArrayList<>();
        for (String t : raw.split(",")) {
            if (!t.isBlank()) {
                out.add(t.trim());
            }
        }
        return out;
    }
}
