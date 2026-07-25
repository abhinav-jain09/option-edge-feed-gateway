package app.feedgateway.systemstatus;

import app.feedgateway.GatewaySettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
        if (!store.configured()) {
            ledger.put("available", false);
            ledger.put("reason", "NOT_CONFIGURED");
        } else {
            try {
                List<Map<String, Object>> observed = store.topics(env);
                incidents = store.openIncidents(env);
                restarts = store.restarts(env);
                Map<String, Object> lastRun = store.lastRun(env);
                ledger.put("available", true);
                ledger.put("lastRunAt", lastRun.get("startedAt"));
                ledger.put("lastRunOutcome", lastRun.get("outcome"));
                topics = observed;
            } catch (Exception e) {
                ledger.put("available", false);
                ledger.put("reason", "QUERY_FAILED");
                ledger.put("error", e.getClass().getSimpleName());
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
            row.putIfAbsent("restartsStatus", Boolean.TRUE.equals(ledger.get("available"))
                    ? "NO_EVIDENCE" : "UNKNOWN");
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
