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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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

    /** Section read succeeded. */
    private static final String OK = "OK";
    /** Section could not be read. NEVER an empty list, NEVER a missing key — see the honesty contract. */
    private static final String UNKNOWN = "UNKNOWN";
    /** One full stack trace per section per this interval; the rest are one-liners (an outage repeats). */
    private static final long STACK_TRACE_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);

    /**
     * Bulkhead. The ledger pool is two connections and this endpoint is served by the SAME thread pool
     * as the live market-data REST surface, so N concurrent status requests must not become N threads
     * queueing on the database. Exactly one reader at a time; everyone else is served the last snapshot.
     */
    private final Semaphore gate = new Semaphore(1);
    private volatile LedgerSnapshot snapshot;
    private final Map<String, Long> lastStackTraceNanos = new java.util.concurrent.ConcurrentHashMap<>();

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

        LedgerSnapshot snap = readLedger(env);
        Map<String, Object> ledger = new LinkedHashMap<>();
        List<Map<String, Object>> topics = snap.topics();
        Map<String, Map<String, Object>> restarts = snap.restarts();
        List<Map<String, Object>> incidents = snap.incidents();
        boolean topicsOk = OK.equals(snap.status("topics"));
        boolean restartsOk = OK.equals(snap.status("restarts"));

        if (!store.configured()) {
            ledger.put("available", false);
            ledger.put("reason", "NOT_CONFIGURED");
            ledger.put("transport", transport());
        } else {
            // `available` keeps its v1 meaning — ALL FOUR reads completed — because a page built against
            // v1 uses it to decide whether anything below can be trusted. Narrowing it here would have
            // made an old page render a partially-read ledger as fully current. The finer-grained truth
            // is published ALONGSIDE it, so a page that understands the statuses can be precise and one
            // that does not stays conservative.
            boolean allOk = snap.allOk();
            ledger.put("available", allOk);
            ledger.put("topicsAvailable", topicsOk);
            if (!allOk) {
                ledger.put("reason", topicsOk ? "PARTIAL" : "QUERY_FAILED");
                // v1 shape (a string) with a SAFE value: a closed-vocabulary code, never server text.
                SystemStatusFailure primary = snap.primaryFailure();
                ledger.put("error", primary == null ? UNKNOWN : primary.code());
            }
            ledger.put("topicsStatus", snap.status("topics"));
            ledger.put("openIncidentsStatus", snap.status("openIncidents"));
            ledger.put("restartsStatus", snap.status("restarts"));
            ledger.put("lastRunStatus", snap.status("lastRun"));
            // Always PRESENT, nullable when unread: a missing key renders as "undefined" in a browser and
            // `new Date(undefined)` is Invalid Date, while an explicit null is a value the page can test.
            ledger.put("lastRunAt", snap.lastRun().get("startedAt"));
            ledger.put("lastRunOutcome", snap.lastRun().get("outcome"));
            ledger.put("transport", transport());
            ledger.put("snapshotAgeMs", snap.ageMs());
            if (!snap.failures().isEmpty()) {
                // Named per section so the page can say WHICH evidence is missing instead of going grey
                // as a whole. Codes only — the driver's message stays in the log (it can carry schema,
                // SQL fragments, hostnames, and in the worst case a credential echoed by a property).
                Map<String, Object> degraded = new LinkedHashMap<>();
                snap.failures().forEach((section, failure) -> degraded.put(section, failure.browserView()));
                ledger.put("degraded", degraded);
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
                // Gated on the TOPIC read specifically: whether the last-run banner could be read
                // says nothing about whether this topic has ever been observed.
                row.put("reason", topicsOk ? "NEVER_OBSERVED" : "LEDGER_UNAVAILABLE");
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

        // Services. The universe is the REGISTERED service list — not whichever evidence happened to
        // arrive. Deriving it from the evidence meant that when lag was unconfigured AND the restart
        // read failed, `services` came back empty: every expected service vanished from the page
        // instead of appearing as UNKNOWN. That is the expected-universe rule the topics list already
        // obeys, and it exists precisely because a missing row reads as "nothing wrong here".
        List<Map<String, Object>> serviceRows = new ArrayList<>();
        List<Map<String, Object>> lagRows = lag.configured() ? lag.services() : List.of();
        Set<String> universe = new LinkedHashSet<>();
        for (ConsumerLagReader.Entry entry : ConsumerLagReader.parseRegistry(settings.systemStatusLagRegistry())) {
            universe.add(entry.service());
        }
        for (Map<String, Object> row : lagRows) {
            universe.add(String.valueOf(row.get("service")));
        }
        universe.addAll(restarts.keySet());

        Map<String, Map<String, Object>> byService = new LinkedHashMap<>();
        for (String service : universe) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("service", service);
            row.put("lagStatus", lag.configured() ? UNKNOWN : "NOT_CONFIGURED");
            row.put("lagRecordsSum", null);
            row.put("lagRecordsMax", null);
            byService.put(service, row);
        }
        for (Map<String, Object> row : lagRows) {
            byService.get(String.valueOf(row.get("service"))).putAll(row);
        }
        if (restartsOk) {
            for (Map.Entry<String, Map<String, Object>> e : restarts.entrySet()) {
                byService.get(e.getKey()).putAll(e.getValue());
            }
        }
        for (Map<String, Object> row : byService.values()) {
            // An unread restart sample is UNKNOWN on EVERY row — a readable ledger elsewhere does not
            // turn an unsampled service into a measured zero.
            row.put("restartsStatus", restartsOk
                    ? String.valueOf(row.getOrDefault("restartsStatus", "NO_EVIDENCE"))
                    : UNKNOWN);
            if (!restartsOk) {
                row.put("restartsThisSession", null);
            }
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

    // ---------------------------------------------------------------- ledger reading

    /**
     * Every ledger section as of ONE read, with a status per section. Immutable and shared between
     * concurrent requests, so nothing in here may be mutated after construction.
     */
    private record LedgerSnapshot(long takenAtNanos,
                                  List<Map<String, Object>> topics,
                                  List<Map<String, Object>> incidents,
                                  Map<String, Map<String, Object>> restarts,
                                  Map<String, Object> lastRun,
                                  Map<String, String> statuses,
                                  Map<String, SystemStatusFailure> failures) {

        static LedgerSnapshot empty(String status, SystemStatusFailure failure) {
            Map<String, String> statuses = new LinkedHashMap<>();
            Map<String, SystemStatusFailure> failures = new LinkedHashMap<>();
            for (String section : List.of("topics", "openIncidents", "restarts", "lastRun")) {
                statuses.put(section, status);
                if (failure != null) {
                    failures.put(section, failure);
                }
            }
            return new LedgerSnapshot(System.nanoTime(), List.of(), List.of(), Map.of(),
                    nullLastRun(), statuses, failures);
        }

        /** Both keys present and null — the page must never see a MISSING last-run field. */
        static Map<String, Object> nullLastRun() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("startedAt", null);
            out.put("outcome", null);
            return out;
        }

        String status(String section) {
            return statuses.getOrDefault(section, UNKNOWN);
        }

        boolean allOk() {
            return failures.isEmpty() && statuses.values().stream().allMatch(OK::equals);
        }

        /** Topics first: it is the section the rest of the page is joined onto. */
        SystemStatusFailure primaryFailure() {
            SystemStatusFailure topicsFailure = failures.get("topics");
            return topicsFailure != null ? topicsFailure : failures.values().stream().findFirst().orElse(null);
        }

        long ageMs() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - takenAtNanos);
        }
    }

    /** A read that takes an explicit per-call budget, so the request deadline can shrink it. */
    private interface SectionReader<T> {
        T read(int timeoutSeconds) throws Exception;
    }

    /**
     * Serve the ledger under a cache + single-flight gate. Three outcomes, all honest:
     * a fresh-enough snapshot is reused (the page refreshes every 2 minutes; re-reading per request
     * bought nothing and cost the pool), a free gate reads a new one, and a busy gate serves the last
     * snapshot rather than queueing another thread on a two-connection pool. With no snapshot at all
     * and a busy gate the answer is UNKNOWN — never an empty ledger dressed as a read one.
     */
    private LedgerSnapshot readLedger(String env) {
        if (!store.configured()) {
            return LedgerSnapshot.empty(UNKNOWN, null);
        }
        LedgerSnapshot cached = snapshot;
        if (cached != null && cached.ageMs() < settings.systemStatusCacheMs()) {
            return cached;
        }
        if (!gate.tryAcquire()) {
            return cached != null ? cached
                    : LedgerSnapshot.empty(UNKNOWN, new SystemStatusFailure("SATURATED", null,
                            "another status read holds the single-flight gate and no snapshot exists yet"));
        }
        try {
            LedgerSnapshot fresh = readAllSections(env);
            snapshot = fresh;
            return fresh;
        } finally {
            gate.release();
        }
    }

    private LedgerSnapshot readAllSections(String env) {
        long deadlineNanos = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(settings.systemStatusRequestBudgetSeconds());
        int fast = settings.systemStatusQueryTimeoutSeconds();
        int slow = settings.systemStatusSlowQueryTimeoutSeconds();
        Map<String, String> statuses = new LinkedHashMap<>();
        Map<String, SystemStatusFailure> failures = new LinkedHashMap<>();

        List<Map<String, Object>> topics = section("topics", fast, deadlineNanos, statuses, failures,
                budget -> store.topics(env, budget), List.of());
        List<Map<String, Object>> incidents = section("openIncidents", fast, deadlineNanos, statuses,
                failures, budget -> store.openIncidents(env, budget), List.of());
        Map<String, Map<String, Object>> restarts = section("restarts", fast, deadlineNanos, statuses,
                failures, budget -> store.restarts(env, budget), Map.of());
        Map<String, Object> lastRun = section("lastRun", slow, deadlineNanos, statuses, failures,
                budget -> store.lastRun(env, budget), LedgerSnapshot.nullLastRun());
        if (!statuses.get("lastRun").equals(OK) || lastRun.isEmpty()) {
            // No rows is not a failure — it is a ledger with no run yet. Either way both keys exist.
            Map<String, Object> normalised = LedgerSnapshot.nullLastRun();
            normalised.putAll(lastRun);
            lastRun = normalised;
        }
        return new LedgerSnapshot(System.nanoTime(), topics, incidents, restarts, lastRun,
                statuses, failures);
    }

    /**
     * Read one section inside its own boundary. The boundary is what keeps a slow or broken section from
     * blanking the ones that answered — including when the section returns something malformed, which
     * would otherwise explode later, outside any catch, and take the broker evidence down with it.
     */
    private <T> T section(String name, int sectionSeconds, long deadlineNanos,
                          Map<String, String> statuses, Map<String, SystemStatusFailure> failures,
                          SectionReader<T> reader, T fallback) {
        int budget = remainingSeconds(deadlineNanos, sectionSeconds);
        if (budget <= 0) {
            statuses.put(name, UNKNOWN);
            failures.put(name, new SystemStatusFailure("DEADLINE_EXCEEDED", null,
                    "request budget spent before this section was read"));
            return fallback;
        }
        try {
            T value = reader.read(budget);
            if (value == null) {
                throw new IllegalStateException("section returned null");
            }
            statuses.put(name, OK);
            return value;
        } catch (SQLException e) {
            record(name, e, failures);
        } catch (RuntimeException e) {
            // A programming fault is NOT a ledger query failure; classifying it as one would send an
            // operator to Postgres to debug the gateway.
            record(name, e, failures);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            record(name, e, failures);
        }
        statuses.put(name, UNKNOWN);
        return fallback;
    }

    /** Seconds left of the request budget, never more than this section's own allowance. */
    private static int remainingSeconds(long deadlineNanos, int sectionSeconds) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return 0;
        }
        long seconds = (remainingNanos + 999_999_999L) / 1_000_000_000L;   // round up: never floor to 0
        return (int) Math.min(sectionSeconds, seconds);
    }

    /**
     * Classify, then report. The classification is pure ({@link SystemStatusFailure}); this is the only
     * place that logs, and it logs the SANITIZED single-line detail — the raw throwable is attached at
     * most once per section per {@link #STACK_TRACE_INTERVAL_NANOS}, because during an outage this runs
     * on every request and four full stack traces per request is how a log stops being readable.
     */
    private void record(String name, Throwable e, Map<String, SystemStatusFailure> failures) {
        SystemStatusFailure failure = SystemStatusFailure.of(e, List.of(settings.systemStatusDbPassword()));
        failures.put(name, failure);
        long now = System.nanoTime();
        Long previous = lastStackTraceNanos.get(name);
        if (previous == null || now - previous > STACK_TRACE_INTERVAL_NANOS) {
            lastStackTraceNanos.put(name, now);
            LOG.warn("system-status ledger section {} failed: {} {}", name, failure.code(), failure.detail(), e);
        } else {
            LOG.warn("system-status ledger section {} failed: {} {}", name, failure.code(), failure.detail());
        }
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
