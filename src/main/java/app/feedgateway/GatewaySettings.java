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

    public String databentoGexTopic() {
        return value("KAFKA_DATABENTO_GEX_TOPIC", "options.databento.gex.strike");
    }

    /**
     * Databento per-strike GEX history topic. JSON on the wire (the databento-gex-history Kafka
     * Streams service emits enriched JSON: the gex fields + a {@code history} window map), unlike
     * the Avro {@link #databentoGexTopic()}. Merged onto the same DATABENTO gex-by-strike rows.
     */
    public String databentoGexHistoryTopic() {
        return value("KAFKA_DATABENTO_GEX_HISTORY_TOPIC", "options.databento.gex.strike.history");
    }

    /** Databento per-(symbol,expiry) max-pain output topic. Independent of GEX; consumed only by the max-pain stream. */
    public String databentoMaxPainTopic() {
        return value("KAFKA_DATABENTO_MAXPAIN_TOPIC", "options.databento.maxpain");
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

    /**
     * Stable id of THIS gateway replica (P1 — multi-replica ticket binding). Tickets are stamped with it,
     * and the handshake rejects a ticket minted by a different replica — so the WS upgrade MUST be sticky-
     * routed to the replica that minted the ticket (which is also the replica consuming/routing that
     * session's Kafka data). Defaults to GATEWAY_INSTANCE_ID, else the hostname (the pod name in k8s),
     * else "local". The separator {@code ~} is reserved for the ticket-id prefix and stripped here.
     */
    public String instanceId() {
        String configured = value("GATEWAY_INSTANCE_ID", "");
        if (!configured.isBlank()) {
            return sanitizeInstanceId(configured);
        }
        try {
            String host = java.net.InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return sanitizeInstanceId(host);
            }
        } catch (RuntimeException | java.net.UnknownHostException ignored) {
            // fall through to the stable default
        }
        return "local";
    }

    private static String sanitizeInstanceId(String value) {
        return value.trim().replace("~", "-");
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

    /**
     * Cache TTL for the per-(symbol,expiry) max-pain stream. Max-pain is derived from DAILY OPRA open
     * interest (a slow, last-value-wins signal that legitimately goes hours without a new record), so it
     * must NOT use the generic {@link #cacheTtlMs()} (default 15 min, sized for fast-ticking quote data) —
     * otherwise the latest valid max-pain is seeked-past on (re)connect, evicted on ingest, periodically
     * purged, and filtered out of the cached-state snapshot the moment it ages past 15 minutes (which for
     * daily data is almost always). Default 12h covers a full regular trading session plus a pre/post
     * buffer while bounding the "show yesterday's level at next open" risk a 24h/infinite window would
     * carry. {@code <= 0} preserves the generic "do not cache stale state" semantics (NOT infinite).
     */
    public long maxPainTtlMs() {
        return longValue("GATEWAY_MAXPAIN_TTL_MS", 43_200_000L, 0L);
    }

    /**
     * Freshness TTL for the structural option-chain cache (the {@code snapshot} strike ladder — see
     * {@code FeedGatewayService.MARKET_AWARE_CHAIN_EVENTS}) DURING regular trading hours — default 10 min.
     * Off-hours the chain is never evicted (see {@link FeedGatewayService} cache policy + {@link #marketCalendar()}),
     * so the published strikes stay visible overnight/weekends/holidays when quotes do not tick. The fast
     * order-flow signals (pace/directional-pressure/strike-flow/gex-by-strike) are NOT covered here — they keep
     * the generic {@link #cacheTtlMs()} plus their own 15s selection barrier.
     */
    public long optionChainRthCacheTtlMs() {
        return longValue("GATEWAY_OPTION_CHAIN_RTH_CACHE_TTL_MS", 600_000L, 0L);
    }

    /**
     * Bounded Kafka seek-back used to rebuild the option-chain cache on (re)connect WHEN off-hours (eviction
     * is disabled then, but the seek must stay bounded so a reconnect never reads the whole topic). Default
     * 24h. During RTH the seek-back equals {@link #optionChainRthCacheTtlMs()}.
     */
    public long optionChainOffHoursSeekBackMs() {
        return longValue("GATEWAY_OPTION_CHAIN_OFF_HOURS_SEEK_BACK_MS", 86_400_000L, 0L);
    }

    /**
     * The US options-market session calendar that drives market-aware cache freshness. Regular hours default
     * to 09:30–16:00 America/New_York; {@code GATEWAY_MARKET_HOLIDAYS} (CSV of {@code yyyy-MM-dd}) and
     * {@code GATEWAY_MARKET_EARLY_CLOSES} (CSV of {@code yyyy-MM-dd=HH:mm}) are operator-supplied. Warns when
     * no holidays are configured (the calendar still works for weekends/RTH, but treats holidays as sessions).
     */
    public GatewayMarketCalendar marketCalendar() {
        LocalTime open = parseLocalTime(value("GATEWAY_MARKET_OPEN", "09:30"), LocalTime.of(9, 30));
        LocalTime close = parseLocalTime(value("GATEWAY_MARKET_CLOSE", "16:00"), LocalTime.of(16, 0));
        java.util.Set<LocalDate> holidays = new java.util.LinkedHashSet<>();
        for (String token : value("GATEWAY_MARKET_HOLIDAYS", "").split(",")) {
            String d = token.trim();
            if (d.isEmpty()) {
                continue;
            }
            try {
                holidays.add(LocalDate.parse(d));
            } catch (DateTimeParseException e) {
                System.out.println("WARN: ignoring unparseable GATEWAY_MARKET_HOLIDAYS entry '" + d + "'");
            }
        }
        java.util.Map<LocalDate, LocalTime> earlyCloses = new java.util.LinkedHashMap<>();
        for (String token : value("GATEWAY_MARKET_EARLY_CLOSES", "").split(",")) {
            String entry = token.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                System.out.println("WARN: ignoring malformed GATEWAY_MARKET_EARLY_CLOSES entry '" + entry + "'");
                continue;
            }
            try {
                earlyCloses.put(LocalDate.parse(entry.substring(0, eq).trim()),
                        parseLocalTime(entry.substring(eq + 1).trim(), close));
            } catch (DateTimeParseException e) {
                System.out.println("WARN: ignoring malformed GATEWAY_MARKET_EARLY_CLOSES entry '" + entry + "'");
            }
        }
        if (holidays.isEmpty()) {
            System.out.println("WARN: GATEWAY_MARKET_HOLIDAYS is empty — market-aware cache freshness will "
                    + "treat market holidays as regular sessions. Configure the OPRA/NYSE holiday list.");
        }
        return new GatewayMarketCalendar(MARKET_TIME_ZONE, open, close, holidays, earlyCloses);
    }

    private static LocalTime parseLocalTime(String raw, LocalTime fallback) {
        try {
            return LocalTime.parse(raw.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
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
