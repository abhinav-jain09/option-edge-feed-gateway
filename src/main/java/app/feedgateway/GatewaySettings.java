package app.feedgateway;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public final class GatewaySettings {
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "192.168.100.252:9092,192.168.100.252:9094,192.168.100.252:9096";
    private static final String DEFAULT_SCHEMA_REGISTRY_URL = "http://192.168.100.252:8082";
    private static final DateTimeFormatter IB_EXPIRY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime OPTION_EXPIRY_ROLLOVER_TIME = LocalTime.of(16, 15);

    public boolean enabled() {
        return boolValue("KAFKA_ENABLED", true);
    }

    // ---- Multi-tenant session auth (OE-DDD-001 §5; default OFF — flag-gated migration, DDD §12) ----

    public boolean authEnabled() {
        return boolValue("GATEWAY_AUTH_ENABLED", false);
    }

    // NOTE: the WS handshake origin allow-list is a single source of truth — wsAllowedOrigins() below
    // (WS_ALLOWED_ORIGINS), shared by BOTH the oc.bearer and the multi-tenant ticket handshake. The old
    // GATEWAY_WS_ALLOWED_ORIGINS alias was dropped to avoid two competing origin lists.

    public String keycloakIssuer() {
        return value("GATEWAY_KEYCLOAK_ISSUER", "");
    }

    public String keycloakClientId() {
        return value("GATEWAY_KEYCLOAK_CLIENT_ID", "options-edge-web");
    }

    /** Required value within the token's {@code aud} (review finding #8). Default matches the WS audience. */
    public String keycloakAudience() {
        return value("GATEWAY_KEYCLOAK_AUDIENCE", "options-edge-web");
    }

    /** Where the gateway fetches Keycloak signing keys; may differ from the issuer (proxy/split-horizon). */
    public String keycloakJwksUrl() {
        return value("GATEWAY_KEYCLOAK_JWKS_URL", "");
    }

    public String redisUri() {
        return value("GATEWAY_REDIS_URI", "");
    }

    /** Explicit, deliberate dev/test opt-in to the non-durable in-memory ticket store (review finding #7). */
    public boolean allowInMemoryTickets() {
        return boolValue("GATEWAY_ALLOW_INMEMORY_TICKETS", false);
    }

    public int wsTicketTtlSeconds() {
        return intValue("GATEWAY_WS_TICKET_TTL_SECONDS", 10, 1);
    }

    // Per-session routing is no longer a separate flag (P0): it is INTRINSIC to auth. Whenever
    // GATEWAY_AUTH_ENABLED=true the SessionRoutingEngine bean exists and the live data path is routed
    // per-session — they cannot be turned on independently, so an authenticated socket can never receive
    // the global broadcast. (FeedGatewayService.perSessionRouting() == routingEngine != null.)

    /** Kafka client security protocol (PLAINTEXT/SSL/SASL_SSL/SASL_PLAINTEXT). Default PLAINTEXT (dev). */
    public String kafkaSecurityProtocol() {
        return value("GATEWAY_KAFKA_SECURITY_PROTOCOL", "PLAINTEXT").trim().toUpperCase();
    }

    /** True when the configured Kafka protocol encrypts/authenticates (i.e. not PLAINTEXT). */
    public boolean kafkaSecure() {
        String p = kafkaSecurityProtocol();
        return p.equals("SSL") || p.equals("SASL_SSL") || p.equals("SASL_PLAINTEXT");
    }

    /** Explicit dev/test opt-out allowing PLAINTEXT Kafka while auth is on (P0 production invariant). */
    public boolean allowInsecureKafka() {
        return boolValue("GATEWAY_ALLOW_INSECURE_KAFKA", false);
    }

    /** Apply the Kafka client security settings (protocol + optional SASL/SSL passthrough) to a config. */
    public void applyKafkaSecurity(java.util.Properties props) {
        String protocol = kafkaSecurityProtocol();
        if (protocol.equals("PLAINTEXT")) {
            return;
        }
        props.put("security.protocol", protocol);
        putIfPresent(props, "sasl.mechanism", "GATEWAY_KAFKA_SASL_MECHANISM");
        putIfPresent(props, "sasl.jaas.config", "GATEWAY_KAFKA_SASL_JAAS_CONFIG");
        putIfPresent(props, "ssl.truststore.location", "GATEWAY_KAFKA_SSL_TRUSTSTORE_LOCATION");
        putIfPresent(props, "ssl.truststore.password", "GATEWAY_KAFKA_SSL_TRUSTSTORE_PASSWORD");
        putIfPresent(props, "ssl.endpoint.identification.algorithm", "GATEWAY_KAFKA_SSL_ENDPOINT_ID_ALGORITHM");
    }

    private void putIfPresent(java.util.Properties props, String key, String envKey) {
        String v = value(envKey, "");
        if (!v.isBlank()) {
            props.put(key, v);
        }
    }

    public String bootstrapServers() {
        return value("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS);
    }

    public String schemaRegistryUrl() {
        return value("KAFKA_SCHEMA_REGISTRY_URL", DEFAULT_SCHEMA_REGISTRY_URL);
    }

    // --- WebSocket authentication (Keycloak JWT carried in the Sec-WebSocket-Protocol subprotocol) ---

    /** When true, /ws/events requires a valid Keycloak token at the handshake. Off in local dev. */
    public boolean wsAuthEnabled() {
        return boolValue("WS_AUTH_ENABLED", false);
    }

    public String wsAuthIssuer() {
        return value("WS_AUTH_ISSUER_URI", "");
    }

    public String wsAuthAudience() {
        return value("WS_AUTH_AUDIENCE", "options-edge-web");
    }

    /** Comma-separated allowed Origins for the WS handshake ('*' only acceptable when auth is disabled). */
    public String wsAllowedOrigins() {
        return value("WS_ALLOWED_ORIGINS", "*");
    }

    public String groupIdBase() {
        return value("GATEWAY_KAFKA_GROUP_ID", "options-edge-feed-gateway");
    }

    public String marketDataSelectionTopic() {
        return value("KAFKA_MARKET_DATA_SELECTION_TOPIC", "options.marketdata.selection");
    }

    public String initialMarketDataSource() {
        return normalizeSource(value("APP_MARKET_DATA_SOURCE", "DATABENTO"));
    }

    public String initialSymbol() {
        return value("IB_SYMBOL", "SPX").toUpperCase();
    }

    public String initialExpiry() {
        return normalizeMarketExpiry(value("IB_EXPIRY", ""));
    }

    public String ibkrDisplayTopic() {
        return value("KAFKA_IBKR_DISPLAY_TOPIC", value("KAFKA_DISPLAY_TOPIC", "options.ibkr.display"));
    }

    public String databentoDisplayTopic() {
        return value("KAFKA_DATABENTO_DISPLAY_TOPIC", "options.databento.display");
    }

    public String ibkrPaceTopic() {
        return value("KAFKA_IBKR_PACE_TOPIC", value("KAFKA_PACE_TOPIC", "options.ibkr.pace"));
    }

    public String databentoPaceTopic() {
        return value("KAFKA_DATABENTO_PACE_TOPIC", "options.databento.pace");
    }

    public String ibkrDirectionalPressureTopic() {
        return value("KAFKA_IBKR_DIRECTIONAL_PRESSURE_TOPIC",
                value("KAFKA_DIRECTIONAL_PRESSURE_TOPIC", "options.ibkr.directional-pressure"));
    }

    public String ibkrVixPriceTopic() {
        return value("KAFKA_IBKR_VIX_PRICE_TOPIC", "underlying.vix.price");
    }

    public String databentoEsTradesTopic() {
        return value("KAFKA_DATABENTO_ES_TRADES_TOPIC", value("KAFKA_HPSF_ES_TRADES_TOPIC", "underlying.es.trades"));
    }

    public String databentoDirectionalPressureTopic() {
        return value("KAFKA_DATABENTO_DIRECTIONAL_PRESSURE_TOPIC", "options.databento.directional-pressure");
    }

    public String databentoStrikeFlowTopic() {
        return value("KAFKA_DATABENTO_STRIKE_FLOW_TOPIC", "options.databento.strike-flow");
    }

    public String ibkrVolumeSandwichTopic() {
        return value("KAFKA_IBKR_VOLUME_SANDWICH_CURRENT_TOPIC",
                value("KAFKA_VOLUME_SANDWICH_CURRENT_TOPIC", "options.ibkr.volume-sandwich.current"));
    }

    public String databentoVolumeSandwichTopic() {
        return value("KAFKA_DATABENTO_VOLUME_SANDWICH_CURRENT_TOPIC", "options.databento.volume-sandwich.current");
    }

    public String ibkrVolumeSandwichAlertsTopic() {
        return value("KAFKA_IBKR_VOLUME_SANDWICH_ALERTS_TOPIC",
                value("KAFKA_VOLUME_SANDWICH_ALERTS_TOPIC", "options.ibkr.volume-sandwich.alerts"));
    }

    public String databentoVolumeSandwichAlertsTopic() {
        return value("KAFKA_DATABENTO_VOLUME_SANDWICH_ALERTS_TOPIC", "options.databento.volume-sandwich.alerts");
    }

    public String ibkrUnusualWhalesGexTopic() {
        return value("KAFKA_IBKR_UNUSUAL_WHALES_GEX_TOPIC",
                value("KAFKA_UNUSUAL_WHALES_GEX_TOPIC", "options.ibkr.unusualwhales.gex.strike"));
    }

    public String ibkrUnusualWhalesGexHistoryTopic() {
        return value("KAFKA_IBKR_UNUSUAL_WHALES_GEX_HISTORY_TOPIC",
                value("KAFKA_UNUSUAL_WHALES_GEX_HISTORY_TOPIC", "options.ibkr.unusualwhales.gex.strike.history"));
    }

    public String hpsfLatestSignalTopic() {
        return value("KAFKA_HPSF_LATEST_SIGNAL_TOPIC", "options.hpsf.latest-signal");
    }

    public String hpsfMarketFlowTopic() {
        return value("KAFKA_HPSF_MARKET_FLOW_TOPIC", "options.hpsf.market-flow");
    }

    public String hpsfStrikeScoreTopic() {
        return value("KAFKA_HPSF_STRIKE_SCORE_TOPIC", "options.hpsf.strike-score");
    }

    public String hpsfAuditTopic() {
        return value("KAFKA_HPSF_AUDIT_TOPIC", "options.hpsf.audit");
    }

    public String hpsfExitSignalTopic() {
        return value("KAFKA_HPSF_EXIT_SIGNAL_TOPIC", "options.hpsf.exit-signal");
    }

    public int pollMs() {
        return intValue("GATEWAY_KAFKA_POLL_MS", 250, 10);
    }

    public int webSocketBatchMs() {
        return intValue("GATEWAY_WS_BATCH_MS", 125, 100);
    }

    // P0 (slow-client isolation): the Kafka consumer never writes to a socket directly. Each socket has a
    // bounded, coalescing outbound queue drained by a dedicated writer; breaching a bound disconnects only
    // that client. These knobs size the queue, the writer pool, and the per-write deadline.

    /** Max messages buffered per socket before that slow client is disconnected. */
    public int wsMaxQueuedMessages() {
        return intValue("GATEWAY_WS_MAX_QUEUED_MESSAGES", 1_000, 1);
    }

    /** Max bytes buffered per socket before that slow client is disconnected. */
    public long wsMaxQueuedBytes() {
        return longValue("GATEWAY_WS_MAX_QUEUED_BYTES", 16L * 1024 * 1024, 1_024L);
    }

    /** Per-write deadline (container blocking-send timeout): a write that exceeds it drops the client. */
    public long wsWriteDeadlineMs() {
        return longValue("GATEWAY_WS_WRITE_DEADLINE_MS", 5_000L, 100L);
    }

    /** Size of the shared pool of outbound writer threads (one active drain per socket at a time). */
    public int wsWriterThreads() {
        return intValue("GATEWAY_WS_WRITER_THREADS", 8, 1);
    }

    public int metadataTimeoutMs() {
        return intValue("GATEWAY_KAFKA_METADATA_TIMEOUT_MS", 30_000, 1_000);
    }

    public int consumerRetryInitialMs() {
        return intValue("GATEWAY_KAFKA_RETRY_INITIAL_MS", 1_000, 100);
    }

    public int consumerRetryMaxMs() {
        return intValue("GATEWAY_KAFKA_RETRY_MAX_MS", 30_000, 1_000);
    }

    public int maxPollRecords() {
        return intValue("GATEWAY_KAFKA_MAX_POLL_RECORDS", 100, 1);
    }

    public int fetchMaxBytes() {
        return intValue("GATEWAY_KAFKA_FETCH_MAX_BYTES", 4 * 1024 * 1024, 1024);
    }

    public int maxPartitionFetchBytes() {
        return intValue("GATEWAY_KAFKA_MAX_PARTITION_FETCH_BYTES", 512 * 1024, 1024);
    }

    public int receiveBufferBytes() {
        return intValue("GATEWAY_KAFKA_RECEIVE_BUFFER_BYTES", 512 * 1024, 1024);
    }

    public long cacheTtlMs() {
        return intValue("GATEWAY_CACHE_TTL_MS", 900_000, 0);
    }

    public long maxLagRecords() {
        return longValue("MARKETDATA_GATEWAY_MAX_LAG_RECORDS", 5_000L, 0L);
    }

    public long maxStaleMs() {
        return longValue("MARKETDATA_GATEWAY_MAX_STALE_MS", 15_000L, 0L);
    }

    /** Per-session historical replay (Live↔Replay switching) is enabled only when this flag is on. */
    public boolean replayUiEnabled() {
        return boolValue("DATABENTO_REPLAY_UI_ENABLED", false);
    }

    /** Runtime profile (dev/staging/prod). Replay is rejected in prod unless explicitly allowed. */
    public String appProfile() {
        return value("APP_PROFILE", "dev");
    }

    public boolean isProd() {
        return "prod".equalsIgnoreCase(appProfile()) || "production".equalsIgnoreCase(appProfile());
    }

    /**
     * Dev/test profiles may use the in-memory ticket store; every other profile requires a durable,
     * shared {@code GATEWAY_REDIS_URI} so single-use ticket redemption holds across gateway instances.
     */
    public boolean isDevOrTest() {
        String profile = appProfile();
        return "dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile);
    }

    /** Allow replay even in a prod profile (defaults off; safety guard, req. 11). */
    public boolean replayAllowInProd() {
        return boolValue("DATABENTO_REPLAY_ALLOW_PROD", false);
    }

    /** Hard upper bound on a replay window (req. 11: max 30 minutes). */
    public long replayMaxWindowMs() {
        return longValue("GATEWAY_REPLAY_MAX_WINDOW_MS", 30L * 60L * 1000L, 60_000L);
    }

    /** Hard upper bound on records streamed in one replay run (req. 11: bounded). */
    public int replayMaxRecords() {
        return intValue("GATEWAY_REPLAY_MAX_RECORDS", 200_000, 1);
    }

    /**
     * Base URL of the replay orchestrator. The gateway calls its ownership-checked run endpoint to
     * authorize {@code (issuer, subject, runId)} before turning a runId into replay topics (P0 — runId
     * authz). Blank means no orchestrator is configured: runId-backed replays are then denied (fail
     * closed), and {@link app.feedgateway.mtsession.gateway.MtSessionSecurityInvariant} refuses startup
     * when replay is enabled without it.
     */
    public String replayOrchestratorBaseUrl() {
        return value("GATEWAY_REPLAY_ORCHESTRATOR_URL", "");
    }

    /** Timeout for the orchestrator run-ownership authorization call; on timeout the request is denied. */
    public long replayOrchestratorTimeoutMs() {
        return longValue("GATEWAY_REPLAY_ORCHESTRATOR_TIMEOUT_MS", 3_000L, 200L);
    }

    /**
     * Max wall-clock a replay read may go WITHOUT progress (a polled record or a partition reaching its
     * captured target offset) before the run is declared INCOMPLETE rather than complete. Empty polls are
     * ordinary (fetch latency, broker load, jitter), so they are retried until this deadline; only then is
     * the run failed. Reset on every unit of progress, so a long but live read is never timed out.
     */
    public long replayIdleTimeoutMs() {
        return longValue("GATEWAY_REPLAY_IDLE_TIMEOUT_MS", 30_000L, 100L);
    }

    /** Max concurrent per-session replay readers; the next start is rejected rather than unbounded-threaded. */
    public int replayMaxConcurrent() {
        return intValue("GATEWAY_REPLAY_MAX_CONCURRENT", 16, 1);
    }

    /** How long a start/stop/return-to-live call awaits the prior reader's termination before proceeding. */
    public long replayShutdownAwaitMs() {
        return longValue("GATEWAY_REPLAY_SHUTDOWN_AWAIT_MS", 2_000L, 100L);
    }

    // P0 (approval enforcement): an authoritative approval record is consulted before any data access and is
    // re-checked during sessions. Default-deny: with NEITHER an approval URL nor the role opt-in configured,
    // the authority denies everyone and MtSessionSecurityInvariant refuses to start (no silent allow).

    /** Config-Control approval platform base URL; the source of truth for live approval/suspension. */
    public String approvalUrl() {
        return value("GATEWAY_APPROVAL_URL", "");
    }

    /** Optional shared-secret header sent to the approval platform. */
    public String approvalApiKey() {
        return value("GATEWAY_APPROVAL_API_KEY", "");
    }

    /** Timeout for an approval lookup; on timeout the decision is DENY (fail closed). */
    public long approvalTimeoutMs() {
        return longValue("GATEWAY_APPROVAL_TIMEOUT_MS", 3_000L, 200L);
    }

    /**
     * Dev/simple opt-in: treat this admin-granted realm role as the approval record (must NOT be a Keycloak
     * default role). Blank disables the role fallback. Ignored when GATEWAY_APPROVAL_URL is set.
     */
    public String approvalRole() {
        return value("GATEWAY_APPROVAL_ROLE", "");
    }

    public static String value(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        return fallback;
    }

    public static boolean boolValue(String key, boolean fallback) {
        String value = value(key, Boolean.toString(fallback));
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);
    }

    public static int intValue(String key, int fallback, int min) {
        String value = value(key, Integer.toString(fallback));
        try {
            return Math.max(min, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return Math.max(min, fallback);
        }
    }

    public static long longValue(String key, long fallback, long min) {
        String value = value(key, Long.toString(fallback));
        try {
            return Math.max(min, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return Math.max(min, fallback);
        }
    }

    public static String normalizeSource(String source) {
        if ("IB".equalsIgnoreCase(source) || "IBKR".equalsIgnoreCase(source)) {
            return "IBKR";
        }
        if ("DATABENTO".equalsIgnoreCase(source) || "DB".equalsIgnoreCase(source)) {
            return "DATABENTO";
        }
        return source == null ? "" : source.trim().toUpperCase();
    }

    public static String normalizeExpiry(String expiry) {
        return expiry == null ? "" : expiry.trim().replace("-", "");
    }

    static String normalizeMarketExpiry(String expiry) {
        return normalizeMarketExpiry(expiry, ZonedDateTime.now(MARKET_TIME_ZONE));
    }

    static String normalizeMarketExpiry(String expiry, ZonedDateTime marketNow) {
        String normalized = normalizeExpiry(expiry);
        if (normalized.length() != 8) {
            return normalized;
        }
        try {
            LocalDate date = LocalDate.parse(normalized, IB_EXPIRY_FORMAT);
            LocalDate nextExpiry = nextWeekday(date);
            if (nextExpiry.equals(marketNow.toLocalDate())
                    && !marketNow.toLocalTime().isBefore(OPTION_EXPIRY_ROLLOVER_TIME)) {
                nextExpiry = nextWeekday(nextExpiry.plusDays(1));
            }
            return nextExpiry.format(IB_EXPIRY_FORMAT);
        } catch (DateTimeParseException ignored) {
            return normalized;
        }
    }

    private static LocalDate nextWeekday(LocalDate date) {
        LocalDate value = date;
        while (value.getDayOfWeek() == DayOfWeek.SATURDAY || value.getDayOfWeek() == DayOfWeek.SUNDAY) {
            value = value.plusDays(1);
        }
        return value;
    }
}
