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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
public class SystemStatusController implements org.springframework.beans.factory.DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(SystemStatusController.class);

    /** Section read succeeded. */
    private static final String OK = "OK";
    /** Section could not be read. NEVER an empty list, NEVER a missing key — see the honesty contract. */
    private static final String UNKNOWN = "UNKNOWN";
    /** Section was read, but so long ago the value is no longer evidence of anything. */
    private static final String STALE = "STALE";
    /** Sections, in read order. Named once so the envelope is identical on every path. */
    private static final List<String> SECTIONS = List.of("topics", "openIncidents", "restarts", "lastRun");
    /** Frames kept from a stack: enough to locate the call site, bounded so a log stays a log. */
    private static final int MAX_STACK_FRAMES = 6;
    /**
     * How long a COLD-START request waits for the one in-flight read. Deliberately far below the
     * database request budget: a request thread waiting is a request thread not serving market data,
     * and once any snapshot exists the wait is skipped entirely.
     */
    private static final long COLD_START_WAIT_MS = 2_000L;
    /** One full stack trace per section per this interval; the rest are one-liners (an outage repeats). */
    private static final long STACK_TRACE_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);

    /**
     * Bulkhead. The ledger pool is two connections and this endpoint is served by the SAME thread pool
     * as the live market-data REST surface, so N concurrent status requests must not become N threads
     * queueing on the database. Exactly one reader at a time; everyone else is served the last snapshot.
     */
    /** The in-flight refresh AND the env it is for: a flight for another env can never help us. */
    private record Flight(String env, CompletableFuture<LedgerSnapshot> result) { }

    private final AtomicReference<Flight> inFlight = new AtomicReference<>();
    private volatile LedgerSnapshot snapshot;
    private final AtomicReference<Long> lastStackNanos = new AtomicReference<>();

    /**
     * Sections run here, NOT on the request thread, so the request-wide deadline is a real bound and
     * not merely the number handed to {@code setQueryTimeout}: connection acquisition, TLS and socket
     * reads all happen outside that statement timeout. An abandoned task keeps its pooled connection
     * only until its own statement timeout fires, so the leak is bounded. Daemon threads: this pool
     * must never hold the JVM open.
     */
    private final ExecutorService sections = new ThreadPoolExecutor(0, 4, 30L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> {
                Thread t = new Thread(r, "system-status-section");
                t.setDaemon(true);
                return t;
            });

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
        // Sampled ONCE. Deciding staleness from one reading of the clock and publishing the age from
        // another lets a threshold crossing between them emit OK next to an age past the limit.
        long snapshotAgeMs = snap.ageMs();
        boolean stale = snapshotAgeMs > settings.systemStatusMaxStaleMs();
        Map<String, Object> ledger = new LinkedHashMap<>();
        List<Map<String, Object>> topics = snap.topics();
        Map<String, Map<String, Object>> restarts = snap.restarts();
        List<Map<String, Object>> incidents = snap.incidents();
        // Past the max-stale line a cached snapshot stops being evidence. Its statuses go STALE and
        // nothing downstream may present it as health — a stuck refresh leaving yesterday's green
        // states on screen is the same lie as a blank page, only more convincing.
        boolean topicsOk = !stale && OK.equals(snap.status("topics"));
        boolean restartsOk = !stale && OK.equals(snap.status("restarts"));

        // ONE envelope shape on every path, configured or not. When the ledger is unconfigured the
        // sections are still named and still say UNKNOWN: a page that gates "no open incidents" on
        // openIncidentsStatus must not fall through to `undefined` and render the healthy view.
        ledger.put("available", !stale && store.configured() && snap.allOk());
        ledger.put("topicsAvailable", topicsOk);
        if (!store.configured()) {
            ledger.put("reason", "NOT_CONFIGURED");
        } else if (stale) {
            ledger.put("reason", "SNAPSHOT_STALE");
            ledger.put("error", "SNAPSHOT_STALE");
        } else if (!snap.allOk()) {
            // `available` keeps its v1 meaning — ALL FOUR reads completed — because a page built
            // against v1 uses it to decide whether anything below can be trusted. Narrowing it would
            // have made an old page render a partially-read ledger as fully current.
            ledger.put("reason", topicsOk ? "PARTIAL" : "QUERY_FAILED");
            SystemStatusFailure primary = snap.primaryFailure();
            // v1 shape (a string) with a SAFE value: a closed-vocabulary code, never server text.
            ledger.put("error", primary == null ? UNKNOWN : primary.code());
        }
        for (String name : SECTIONS) {
            String status = !store.configured() ? UNKNOWN
                    : stale && OK.equals(snap.status(name)) ? STALE
                    : snap.status(name);
            ledger.put(name + "Status", status);
        }
        // Always PRESENT, nullable when unread: a missing key renders as "undefined" in a browser and
        // Date.parse(undefined) is Invalid Date, while an explicit null is a value the page can test.
        ledger.put("lastRunAt", snap.lastRun().get("startedAt"));
        ledger.put("lastRunOutcome", snap.lastRun().get("outcome"));
        ledger.put("transport", transport());
        ledger.put("snapshotAgeMs", snapshotAgeMs);
        ledger.put("maxStaleMs", settings.systemStatusMaxStaleMs());
        if (!snap.failures().isEmpty() || stale) {
            // Named per section so the page can say WHICH evidence is missing instead of going grey
            // as a whole. Codes only — the driver's message stays in the log (it can carry schema,
            // SQL fragments, hostnames, and in the worst case a credential echoed by a property).
            Map<String, Object> degraded = new LinkedHashMap<>();
            snap.failures().forEach((section, failure) -> degraded.put(section, failure.browserView()));
            if (stale) {
                Map<String, Object> staleView = new LinkedHashMap<>();
                staleView.put("code", "SNAPSHOT_STALE");
                staleView.put("sqlState", null);
                for (String name : SECTIONS) {
                    degraded.putIfAbsent(name, staleView);
                }
            }
            ledger.put("degraded", degraded);
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
            } else if (stale) {
                // Retained so the operator can see what it WAS, but never as evidence: a green chip
                // beside an "EVIDENCE STALE" banner is the page contradicting itself, and the green
                // is what a person actually reads.
                row.put("evidence", STALE);
                row.put("state", evidence.get("state"));
                row.put("ageS", evidence.get("ageS"));
                row.put("asOf", evidence.get("asOf"));
                row.put("reason", "SNAPSHOT_STALE");
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
    private record LedgerSnapshot(String env,
                                  long takenAtNanos,
                                  List<Map<String, Object>> topics,
                                  List<Map<String, Object>> incidents,
                                  Map<String, Map<String, Object>> restarts,
                                  Map<String, Object> lastRun,
                                  Map<String, String> statuses,
                                  Map<String, SystemStatusFailure> failures) {

        static LedgerSnapshot empty(String env, String status, SystemStatusFailure failure) {
            Map<String, String> statuses = new LinkedHashMap<>();
            Map<String, SystemStatusFailure> failures = new LinkedHashMap<>();
            for (String section : SECTIONS) {
                statuses.put(section, status);
                if (failure != null) {
                    failures.put(section, failure);
                }
            }
            return new LedgerSnapshot(env, System.nanoTime(), List.of(), List.of(), Map.of(),
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
     * Serve the ledger under a cache + COALESCING single flight. A refresh is computed at most once at
     * a time; concurrent callers WAIT for that same result rather than each opening their own read
     * against a two-connection pool, and rather than being fobbed off with synthetic data while a
     * perfectly good answer is seconds away. Only a caller that cannot wait within the request budget
     * falls back — to the previous snapshot if there is one, and otherwise to UNKNOWN/SATURATED, which
     * is the honest answer for "nobody has read this yet".
     *
     * <p>The cache is keyed by {@code env}: a snapshot taken for one environment must never be
     * relabelled as another's evidence.
     */
    private LedgerSnapshot readLedger(String env) {
        if (!store.configured()) {
            return LedgerSnapshot.empty(env, UNKNOWN, null);
        }
        LedgerSnapshot cached = usable(snapshot, env);
        // Reuse is bounded by BOTH windows. Honouring only the cache window meant that configuring a
        // max-stale shorter than the cache window suppressed the refresh for the whole cache window
        // while serving evidence already past its own staleness limit.
        long reuseMs = Math.min(settings.systemStatusCacheMs(), settings.systemStatusMaxStaleMs());
        if (cached != null && cached.ageMs() < reuseMs) {
            return cached;
        }
        CompletableFuture<LedgerSnapshot> mine = new CompletableFuture<>();
        Flight flight = new Flight(env, mine);
        if (inFlight.compareAndSet(null, flight)) {
            try {
                LedgerSnapshot fresh = readAllSections(env);
                snapshot = fresh;
                mine.complete(fresh);
                return fresh;
            } catch (RuntimeException | Error e) {
                mine.completeExceptionally(e);
                throw e;
            } finally {
                inFlight.compareAndSet(flight, null);
            }
        }
        // Contended. An older snapshot for THIS env is a better answer than a blocked request thread:
        // it is real evidence, it carries its own age, and past the max-stale line the envelope
        // downgrades it to STALE anyway. Only a genuine cold start waits, and then briefly — this
        // endpoint shares the gateway's request threads with the live market-data surface.
        if (cached != null) {
            return cached;
        }
        Flight running = inFlight.get();
        if (running == null) {
            LedgerSnapshot published = usable(snapshot, env);
            return published != null ? published : LedgerSnapshot.empty(env, UNKNOWN, saturated());
        }
        if (!running.env().equals(env)) {
            // A flight for another environment can never become our evidence; waiting on it would pin
            // this thread for the whole budget to learn nothing.
            return LedgerSnapshot.empty(env, UNKNOWN, saturated());
        }
        try {
            LedgerSnapshot shared = running.result().get(COLD_START_WAIT_MS, TimeUnit.MILLISECONDS);
            LedgerSnapshot forThisEnv = usable(shared, env);
            if (forThisEnv != null) {
                return forThisEnv;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
            // The in-flight read is not going to help this request in time.
        }
        return LedgerSnapshot.empty(env, UNKNOWN, saturated());
    }

    /** A snapshot is only usable as this request's evidence if it was taken for the SAME env. */
    private static LedgerSnapshot usable(LedgerSnapshot candidate, String env) {
        return candidate != null && candidate.env().equals(env) ? candidate : null;
    }

    private static SystemStatusFailure saturated() {
        return new SystemStatusFailure("SATURATED", null,
                "a ledger read is already in flight and no usable snapshot exists yet");
    }

    private LedgerSnapshot readAllSections(String env) {
        // Stamped BEFORE the reads, not after: the age we publish must be the age of the OLDEST
        // section in the snapshot, so a slow last-run read cannot make the topic states look fresher
        // than they are.
        long startedAtNanos = System.nanoTime();
        long deadlineNanos = startedAtNanos
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
        return new LedgerSnapshot(env, startedAtNanos, topics, incidents, restarts, lastRun,
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
            return failed(name, statuses, failures, new SystemStatusFailure("DEADLINE_EXCEEDED", null,
                    "request budget spent before this section was read"), fallback);
        }
        Future<T> task;
        try {
            task = sections.submit(() -> reader.read(budget));
        } catch (RejectedExecutionException e) {
            return failed(name, statuses, failures, saturated(), fallback);
        }
        try {
            T value = task.get(budget, TimeUnit.SECONDS);
            if (value == null) {
                throw new IllegalStateException("section returned null");
            }
            statuses.put(name, OK);
            return value;
        } catch (TimeoutException e) {
            // The HARD bound. setQueryTimeout does not cover connection acquisition, TLS or socket
            // reads, so without this the "budget" was only ever advisory.
            task.cancel(true);
            return failed(name, statuses, failures, new SystemStatusFailure("DEADLINE_EXCEEDED", null,
                    "section exceeded its " + budget + "s share of the request budget"), fallback);
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            return failed(name, statuses, failures,
                    SystemStatusFailure.of(e, secrets()), fallback);
        } catch (java.util.concurrent.ExecutionException e) {
            // The cause is the real failure: SQLException stays QUERY_FAILED, anything else is a
            // gateway bug and must not be reported as a Postgres problem.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            reportStack(cause);
            return failed(name, statuses, failures, SystemStatusFailure.of(cause, secrets()), fallback);
        } catch (RuntimeException e) {
            reportStack(e);
            return failed(name, statuses, failures, SystemStatusFailure.of(e, secrets()), fallback);
        }
    }

    private <T> T failed(String name, Map<String, String> statuses,
                         Map<String, SystemStatusFailure> failures, SystemStatusFailure failure,
                         T fallback) {
        statuses.put(name, UNKNOWN);
        failures.put(name, failure);
        report(name, failure);
        return fallback;
    }

    private List<String> secrets() {
        return List.of(settings.systemStatusDbPassword());
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
     * Report a classified failure. The raw {@link Throwable} is NEVER handed to the logger: SLF4J would
     * print its original message and every cause verbatim, which is exactly the unredacted, unbounded,
     * multi-line text the sanitizer exists to prevent — password, URL userinfo and server detail
     * included. What goes out is the sanitized bounded detail plus, at most once per
     * {@link #STACK_TRACE_INTERVAL_NANOS} ACROSS ALL SECTIONS, a short frame list: enough to locate the
     * call site, and never four full traces per request during an outage.
     */
    private void report(String name, SystemStatusFailure failure) {
        LOG.warn("system-status ledger section {} failed: {} {}", name, failure.code(), failure.detail());
    }

    /** Frame list only — class, method and line. No messages, so nothing to redact can ride along. */
    private void reportStack(Throwable e) {
        long now = System.nanoTime();
        Long previous = lastStackNanos.get();
        if (previous != null && now - previous <= STACK_TRACE_INTERVAL_NANOS) {
            return;
        }
        if (!lastStackNanos.compareAndSet(previous, now)) {
            return;
        }
        StringBuilder frames = new StringBuilder();
        StackTraceElement[] stack = e.getStackTrace();
        for (int i = 0; i < Math.min(MAX_STACK_FRAMES, stack.length); i++) {
            frames.append(i == 0 ? "" : " <- ").append(stack[i].getClassName())
                    .append('.').append(stack[i].getMethodName())
                    .append(':').append(stack[i].getLineNumber());
        }
        LOG.warn("system-status ledger failure origin: {}",
                SystemStatusFailure.redact(frames.toString(), secrets()));
    }

    /**
     * Daemon threads keep the JVM from being held open; they do not stop work during a Spring context
     * shutdown or reload, which is exactly when the datasource underneath them is being closed.
     */
    @Override
    public void destroy() throws InterruptedException {
        sections.shutdownNow();
        sections.awaitTermination(2, TimeUnit.SECONDS);
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
