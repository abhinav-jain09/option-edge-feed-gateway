package app.feedgateway;

import com.optionsedge.contracts.greekmoveauth.GreekMoveAuthTopics;
import com.optionsedge.contracts.spotvolregime.SpotVolRegimeTopics;
import com.optionsedge.contracts.strikeintelligence.StrikeIntelligenceTopics;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public final class GatewaySettings {
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "192.168.100.252:9092";
    private static final String DEFAULT_SCHEMA_REGISTRY_URL = "http://192.168.100.252:8082";
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");

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
        // MARKET_DATA_EXPIRY == "AUTO" -> resolve the current ET trading date from the market calendar,
        // mirroring the Databento feed's AUTO mode. The OLD static 16:15 rollover was removed because the
        // feed did NOT roll, so advancing the gateway alone emptied the chain. The feed now self-rolls
        // (options-edge-databento-feed AUTO), so the gateway rolls too — by the SAME calendar, to the SAME
        // date — via FeedGatewayService.maybeAutoRollExpiry. An explicit yyyyMMdd still pins (verbatim),
        // for replay or a manual override. IB_EXPIRY is the deprecated fallback (retired IB feed) — read
        // only when MARKET_DATA_EXPIRY is unset, so old config keeps working during the transition.
        String configured = marketDataExpiry();
        if (isAutoExpiry(configured)) {
            return marketCalendar().currentTradingDate(Instant.now(), expiryRollAfter())
                    .format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        return normalizeExpiry(configured);
    }

    /** True ONLY when MARKET_DATA_EXPIRY (or its IB_EXPIRY fallback) is the explicit string "AUTO":
     *  the gateway resolves AND daily-rolls the expiry from the calendar. Blank fails closed. */
    public boolean autoExpiry() {
        return isAutoExpiry(marketDataExpiry());
    }

    /**
     * The configured chain expiry: source-neutral {@code MARKET_DATA_EXPIRY} is primary, and the legacy
     * {@code IB_EXPIRY} (a leftover from the retired Interactive-Brokers feed; the system is Databento-only
     * now) is the deprecated backward-compat fallback. {@link #value} treats blank as unset, so IB_EXPIRY
     * is consulted only when MARKET_DATA_EXPIRY is absent/blank — old config keeps resolving unchanged.
     */
    private static String marketDataExpiry() {
        return value("MARKET_DATA_EXPIRY", value("IB_EXPIRY", ""));
    }

    /**
     * ET wall-clock time ("HH:MM") after which the AUTO expiry rolls to the NEXT trading day. Empty ->
     * legacy midnight-ET roll (SPX/OPRA, whose 09:30-16:00 session never crosses midnight). Set to the
     * ES 0DTE expiry time ("16:00") so the gateway follows the feed's session-aware roll — otherwise the
     * gateway keeps activeSelection on the just-expired date and drops the feed's next-expiry records
     * until midnight ET. Invalid/blank -> null.
     */
    public LocalTime expiryRollAfter() {
        String raw = value("IB_EXPIRY_ROLL_AFTER", "");
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        // Strict HH:MM (mirrors the feed's parse): exactly two numeric, colon-separated parts. LocalTime.of
        // validates the 0-23 / 0-59 ranges (throws -> null). Rejects "16", "16:00:00", signed/garbage.
        String[] parts = raw.trim().split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isAutoExpiry(String configured) {
        // ONLY an explicit "AUTO" enables calendar resolution. A blank MARKET_DATA_EXPIRY (and blank
        // IB_EXPIRY fallback) deliberately stays blank so a truly-unconfigured gateway still fails closed
        // on the bearer handshake (WsJwtHandshakeInterceptor.defaultSelection throws on a blank expiry).
        // The deploy always writes AUTO or a concrete yyyyMMdd, never blank, so AUTO mode is still active
        // in every deployed env.
        return configured != null && configured.trim().equalsIgnoreCase("AUTO");
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

    public String ibkrPaceRankTopic() {
        return value("KAFKA_IBKR_PACE_RANK_TOPIC", ibkrPaceTopic() + ".rank");
    }

    public String databentoPaceRankTopic() {
        return value("KAFKA_DATABENTO_PACE_RANK_TOPIC", databentoPaceTopic() + ".rank");
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

    /**
     * Canonical SPX spot stream ({@code UnderlyingPriceEvent} JSON, keyed by symbol). This is the SAME
     * topic HPSF and strike-flow source their spot from, forwarded to the UI as the dedicated
     * {@code spx-price} event, so the displayed SPX spot is the canonical value rather than a spot
     * embedded in an arbitrarily-ordered display-snapshot batch.
     */
    public String underlyingSpxPriceTopic() {
        return value("KAFKA_UNDERLYING_SPX_PRICE_TOPIC", "underlying.spx.price");
    }

    /** Latest continuous ES futures aggressor-flow snapshot (JSON, keyed by ES.v.0). */
    public String esAggressorFlowTopic() {
        return value("KAFKA_ES_AGGRESSOR_FLOW_TOPIC", "futures.aggressor-flow");
    }

    /** Opt-in because the ES-only topic does not exist on the SPX production cluster. */
    public boolean esAggressorFlowEnabled() {
        return boolValue("GATEWAY_ES_AGGRESSOR_FLOW_ENABLED", false);
    }

    public String databentoDirectionalPressureTopic() {
        return value("KAFKA_DATABENTO_DIRECTIONAL_PRESSURE_TOPIC", "options.databento.directional-pressure");
    }

    /**
     * Current binary SPX direction from vix-option-inteligence-service (JSON, key = symbol|sessionDate).
     * Forwarded as standalone websocket event {@code zero-dte-intelligence}; optional until the new
     * producer is deployed.
     */
    public String vixOptionInteligenceTopic() {
        return value(
                "KAFKA_VIX_OPTION_INTELIGENCE_TOPIC",
                "options.spx.vix-option-inteligence-service.current");
    }

    /** Short freshness window for the live chain tint; a dead producer must return the UI to neutral. */
    public long zeroDteIntelligenceTtlMs() {
        return longValue("GATEWAY_ZERO_DTE_INTELLIGENCE_TTL_MS", 15_000L, 0L);
    }

    public String databentoStrikeFlowTopic() {
        return value("KAFKA_DATABENTO_STRIKE_FLOW_TOPIC", "options.databento.strike-flow");
    }

    /** Per-strike, compacted Seller Activity histories split from the chain-wide strike-flow snapshot. */
    public String databentoSellerActivityTopic() {
        return value("KAFKA_DATABENTO_SELLER_ACTIVITY_TOPIC", "options.databento.seller-activity");
    }

    /**
     * Per-strike delta-flow topic (JSON {@code DeltaFlowStrikeSnapshot}, one record per
     * {@code symbol|date|expiry|strike}) from delta-flow-service. Broadcast as event
     * {@code "delta-flow"}. NB: unlike the {@code options.databento.*} topics, the delta-flow
     * topics are UNPREFIXED — the default is the bare name {@code delta-flow-by-strike}.
     */
    public String databentoDeltaFlowByStrikeTopic() {
        return value("KAFKA_DATABENTO_DELTA_FLOW_BY_STRIKE_TOPIC", "delta-flow-by-strike");
    }

    /**
     * Per-strike strike-intelligence topic (JSON {@code StrikeIntelligenceSignal}, one record per
     * {@code symbol|expiry|strike}) from strike-intelligence-service. Broadcast as event
     * {@code "strike-intel"}. Like the delta-flow topic, this is UNPREFIXED — the default is the
     * bare name {@code strike-intelligence-by-strike}.
     */
    public String strikeIntelByStrikeTopic() {
        return value("STRIKE_INTEL_BY_STRIKE_TOPIC", StrikeIntelligenceTopics.STRIKE_INTELLIGENCE_BY_STRIKE);
    }

    /**
     * Per-strike Option Truth pair readings (JSON, one STEP and one SESSION_ANCHOR record per strike).
     * The UI currently renders only STEP, but the gateway preserves both horizons by key.
     */
    public String optionTruthByStrikeTopic() {
        return value("KAFKA_OPTION_TRUTH_BY_STRIKE_TOPIC",
                "options.spx.option-truth-engine-service.by-strike");
    }

    /** A truth reading is live evidence, not a session-long level. */
    public long optionTruthTtlMs() {
        return longValue("GATEWAY_OPTION_TRUTH_TTL_MS", 180_000L, 1_000L);
    }

    /**
     * Discrete {@code StrikeTurnAlert} START/STOP turn events from strike-intelligence-service, keyed by
     * {@code symbol|tradingDate}. Broadcast as event {@code "turn-alert"} (own message.type), symbol-filtered
     * client-side. Unprefixed; default is the bare {@code strike-intelligence-turn-alert}.
     */
    public String strikeIntelTurnAlertTopic() {
        return value("STRIKE_INTEL_TURN_ALERT_TOPIC", StrikeIntelligenceTopics.STRIKE_INTELLIGENCE_TURN_ALERT);
    }

    /**
     * Per-symbol {@code StrikeIntelligenceDashboard} from strike-intelligence-service (JSON), which carries
     * the level-based {@code clusters} (adjacent-strike walls). Broadcast as event {@code "strike-cluster"}
     * (own message.type), symbol-filtered client-side. Unprefixed; default is the bare
     * {@code strike-intelligence-dashboard}.
     */
    public String strikeIntelDashboardTopic() {
        return value("STRIKE_INTEL_DASHBOARD_TOPIC", StrikeIntelligenceTopics.STRIKE_INTELLIGENCE_DASHBOARD);
    }

    /**
     * Hot Strike of the Day topic from signal-follower-service (JSON envelope
     * {@code {schemaVersion, row}}, keyed by symbol; as-of snapshots of
     * {@code hot_strike_day} rows — the table is the source of truth). Broadcast as
     * event {@code hot-strike}, cached per symbol and replayed on connect so a fresh
     * page gets the day's gold mark immediately (design §4.4). Unprefixed; default is
     * the bare {@code signal-follower.hot-strike}.
     */
    public String hotStrikeTopic() {
        return value("GATEWAY_HOT_STRIKE_TOPIC", "signal-follower.hot-strike");
    }

    /**
     * Hot-strike cache/seek window (default 12h — the max-pain/es-open-direction
     * session class): the day's row stays valid all session and the matching
     * seek-back re-bootstraps it after a gateway restart. Never the generic 15-min
     * TTL — an hourly recompute cadence would leave restarts empty-handed.
     */
    public long hotStrikeTtlMs() {
        return longValue("GATEWAY_HOT_STRIKE_TTL_MS", 43_200_000L, 60_000L);
    }

    /**
     * Per-strike strike-invasion topic (JSON {@code StrikeInvasionSnapshot}, one record per
     * {@code symbol|strike} — SPX-only, so there is NO expiry). Broadcast as event
     * {@code "strike-invasion"}. Mirrors the strike-intel topic getter.
     */
    public String strikeInvasionTopic() {
        return value("KAFKA_STRIKE_INVASION_TOPIC", "options.spx.strike-invasion.current");
    }

    /** dealer-ledger-service chain-level book (U1-U9), one record per (symbol, expiry). */
    public String dealerLedgerProfileTopic() {
        return value("KAFKA_DEALER_LEDGER_PROFILE_TOPIC", "dealer-ledger-profile");
    }

    /** dealer-ledger-service session state, one record per (symbol, expiry). Joined with the profile. */
    public String dealerLedgerStateTopic() {
        return value("KAFKA_DEALER_LEDGER_STATE_TOPIC", "dealer-ledger-state");
    }

    /**
     * Strike-liquidity heatmap dashboard frames (JSON {@code StrikeLiquidityHeatmapFrame}, one
     * per-second column per symbol|expiry) from strike-liquidity-heatmap-service. Broadcast as
     * event {@code "liquidity-heatmap"}. Optional topic: when the producer is down or absent the
     * ui-batch simply carries no frames and the option-chain page renders exactly as before.
     */
    public String strikeLiquidityTopic() {
        return value("KAFKA_STRIKE_LIQUIDITY_TOPIC", "strike-liquidity-heatmap-dashboard");
    }

    /**
     * Freshness TTL for liquidity-heatmap frames — deliberately SHORT (default 5s ~= 2x bucket
     * width + grace), NOT the generic 15-min {@link #cacheTtlMs()}: a minutes-old "latest column"
     * must render as stale/absent, never as live liquidity (the GEX generic-TTL lesson).
     */
    public long liquidityHeatmapTtlMs() {
        return longValue("GATEWAY_LIQUIDITY_HEATMAP_TTL_MS", 5_000L, 0L);
    }

    // ---- Strike-liquidity heatmap SESSION-HISTORY backfill (HEATMAP-SESSION-HISTORY-BACKFILL v8) ----

    /** Master switch for the /api/liquidity-history endpoint + its dedicated group-less Kafka consumer. */
    public boolean heatmapHistoryEnabled() {
        return boolValue("HEATMAP_HISTORY_ENABLED", true);
    }

    /** Expected partition count for the heatmap dashboard topic topology guard. */
    public int heatmapHistoryExpectedPartitions() {
        return intValue("HEATMAP_HISTORY_EXPECTED_PARTITIONS", 4, 1);
    }

    /** Max distinct chains (symbol|expiry) held in the session-history store (spec §2 memory bounds). */
    public int heatmapHistoryMaxChains() {
        return intValue("HEATMAP_HISTORY_MAX_CHAINS", 4, 1);
    }

    /** Max approximate bytes across all session aggregates before cap-breach eviction (spec §2). */
    public long heatmapHistoryMaxBytes() {
        return longValue("HEATMAP_HISTORY_MAX_BYTES", 200L * 1024 * 1024, 1L);
    }

    /**
     * Steady-state lag guard (spec §2): while the history consumer's total record lag exceeds this,
     * the endpoint answers 503 — a lagging aggregate is never silently served as complete.
     * Default 300 records ~= 5 minutes of 1s frames.
     */
    public long heatmapHistoryMaxLagRecords() {
        return longValue("HEATMAP_HISTORY_MAX_LAG_RECORDS", 300L, 0L);
    }

    /** Hard raw (pre-gzip) response budget; breach drops OLDEST buckets + truncated=true, never 500 (spec §3). */
    public long heatmapHistoryMaxResponseBytes() {
        return longValue("HEATMAP_HISTORY_MAX_RESPONSE_BYTES", 24L * 1024 * 1024, 1024L);
    }

    /** Per-principal request rate limit for /api/liquidity-history (spec §3: 6/min → 429). */
    public int heatmapHistoryRateLimitPerMin() {
        return intValue("HEATMAP_HISTORY_RATE_LIMIT_PER_MIN", 6, 1);
    }

    /** Catch-up abort budget: a rebuild epoch not caught up within this is REBUILD_FAILED (spec §2). */
    public long heatmapHistoryCatchupAbortMs() {
        return longValue("HEATMAP_HISTORY_CATCHUP_ABORT_MS", 300_000L, 1_000L);
    }

    // ---- Flow Explorer / GET /api/pin-flow (FLOW-EXPLORER-OPTION-CHAIN §5/§6) ----
    // Read-only Postgres. Reuses the SHARED POSTGRES_* env the other Postgres services already carry
    // (so deploy wiring is trivial), with pinflow.postgres.* overrides that win when present.

    /** JDBC URL for the read-only pin_* datasource; blank/absent → endpoint 503, DB never built (§6.3). */
    public String pinFlowJdbcUrl() {
        return firstNonBlank(value("pinflow.postgres.jdbc-url", ""), value("POSTGRES_JDBC_URL", ""));
    }

    // ── System Status page (design §3.5/§3.6): read-only oe_watch ledger + lag registry ──
    /** JDBC URL for the read-only oe_watch ledger; blank → the page reports LEDGER UNAVAILABLE. */
    public String systemStatusJdbcUrl() {
        return firstNonBlank(value("systemstatus.postgres.jdbc-url", ""),
                             value("OE_WATCH_JDBC_URL", ""));
    }

    public String systemStatusDbUser() {
        return firstNonBlank(value("systemstatus.postgres.user", ""),
                             value("OE_WATCH_DB_USER", "oe_watch_reader"));
    }

    public String systemStatusDbPassword() {
        return firstNonBlank(value("systemstatus.postgres.password", ""),
                             value("OE_WATCH_DB_PASSWORD", ""));
    }

    /** Which env's ledger rows this gateway serves (rows carry env; a gateway shows only its own). */
    public String systemStatusEnv() {
        return firstNonBlank(value("systemstatus.env", ""), value("OE_ENV", "dev"));
    }

    /** {@code service:group:topic1,topic2;…} allowlist — lag is only computed for registered services. */
    public String systemStatusLagRegistry() {
        return firstNonBlank(value("systemstatus.lag-registry", ""),
                             value("OE_SYSTEM_STATUS_LAG_REGISTRY", ""));
    }

    /**
     * Explicit operator opt-out from TLS on the ledger link. Default FALSE (fail-closed): a
     * non-loopback ledger URL is forced to {@code sslmode=verify-full}. Set true ONLY where the
     * Postgres genuinely has no TLS (both dev and the .252 prod server are plaintext today, and every
     * other service already talks to them unencrypted) — the endpoint then reports
     * {@code ledger.transport = PLAINTEXT_ACCEPTED} and the page shows it, so the accepted risk stays
     * visible instead of being forgotten.
     */
    public boolean systemStatusAllowPlaintext() {
        return "true".equalsIgnoreCase(firstNonBlank(
                value("systemstatus.allow-plaintext", ""),
                value("OE_WATCH_ALLOW_PLAINTEXT", "false")));
    }

    public int systemStatusCacheMs() {
        return intValue("OE_SYSTEM_STATUS_CACHE_MS", 30_000, 1_000);
    }

    public int systemStatusQueryTimeoutSeconds() {
        return intValue("OE_SYSTEM_STATUS_QUERY_TIMEOUT_S", 3, 1);
    }

    public int systemStatusAdminTimeoutMs() {
        return intValue("OE_SYSTEM_STATUS_ADMIN_TIMEOUT_MS", 5_000, 1_000);
    }

    public String pinFlowDbUser() {
        return firstNonBlank(value("pinflow.postgres.user", ""), value("POSTGRES_USER", ""));
    }

    public String pinFlowDbPassword() {
        return firstNonBlank(value("pinflow.postgres.password", ""), value("POSTGRES_PASSWORD", ""));
    }

    /**
     * Pin-flow session window start/end (local time in {@link #pinFlowZone()}). Defaults preserve the
     * SPX cash session (09:30 → 16:01 ET). When END <= START the session SPANS MIDNIGHT — e.g. ES on
     * es4 trades 18:00 ET → 17:00 ET the next day; the store then reads [date START, date+1 END).
     * Without this the window was hardcoded to the SPX cash session and the Flow Explorer could never
     * see ES's evening/overnight session (its main trading hours) — it returned an empty payload.
     */
    public java.time.LocalTime pinFlowSessionStart() {
        return parseSessionTime("PIN_FLOW_SESSION_START", java.time.LocalTime.of(9, 30));
    }

    public java.time.LocalTime pinFlowSessionEnd() {
        return parseSessionTime("PIN_FLOW_SESSION_END", java.time.LocalTime.of(16, 1));
    }

    private java.time.LocalTime parseSessionTime(String key, java.time.LocalTime fallback) {
        String raw = value(key, "");
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return java.time.LocalTime.parse(raw.trim());
        } catch (RuntimeException bad) {
            return fallback; // malformed config must never break the endpoint
        }
    }

    /**
     * Points of head-room added either side of the session's spot travel when the strike band is derived
     * from spot (i.e. the caller sent no lo/hi). 0 disables derivation and keeps the fixed default band.
     */
    public int pinFlowBandMargin() {
        return intValue("PIN_FLOW_BAND_MARGIN", 150, 0);
    }

    /** Session/label timezone (§5.1 rule 1). */
    public ZoneId pinFlowZone() {
        try {
            return ZoneId.of(value("PIN_FLOW_TZ", "America/New_York"));
        } catch (RuntimeException bad) {
            return MARKET_TIME_ZONE;
        }
    }

    /** Default strike band lower bound when the request omits lo (§5). */
    public int pinFlowStrikeLo() {
        return intValue("PIN_FLOW_STRIKE_LO", 7490, 1);
    }

    /** Default strike band upper bound when the request omits hi (§5). */
    public int pinFlowStrikeHi() {
        return intValue("PIN_FLOW_STRIKE_HI", 7590, 1);
    }

    /** Bulkhead pool size (§6.1: 2–4 threads). */
    public int pinFlowExecutorThreads() {
        return intValue("PIN_FLOW_EXECUTOR_THREADS", 3, 1);
    }

    /** Bulkhead bounded queue depth (§6.1: tiny queue, CallerRejects → fast 503). */
    public int pinFlowExecutorQueue() {
        return intValue("PIN_FLOW_EXECUTOR_QUEUE", 8, 1);
    }

    /** Per-request deadline for the whole DB round-trip on the bulkhead (§6.1). */
    public long pinFlowRequestDeadlineMs() {
        return longValue("PIN_FLOW_REQUEST_DEADLINE_MS", 10_000L, 500L);
    }

    /** HikariCP connection-acquisition timeout → 503 on pool exhaustion (§6.2). */
    public long pinFlowConnectionTimeoutMs() {
        return longValue("PIN_FLOW_CONNECTION_TIMEOUT_MS", 2_000L, 250L);
    }

    /** JDBC statement/query timeout in seconds (§6.2). */
    public int pinFlowQueryTimeoutSeconds() {
        return intValue("PIN_FLOW_QUERY_TIMEOUT_SECONDS", 10, 1);
    }

    /** Hikari max pool size (§6.2: small, ~4). */
    public int pinFlowPoolMax() {
        return intValue("PIN_FLOW_POOL_MAX", 4, 1);
    }

    /** Short result-cache TTL for (date,lo,hi) coalescing (§6.5: 5–10s). */
    public long pinFlowCacheTtlMs() {
        return longValue("PIN_FLOW_CACHE_TTL_MS", 8_000L, 0L);
    }

    /** Per-principal request rate limit for /api/pin-flow (§6.5 → 503 when exceeded). */
    public int pinFlowRateLimitPerMin() {
        return intValue("PIN_FLOW_RATE_LIMIT_PER_MIN", 30, 1);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    public String databentoPaceMissionTopic() {
        return value("KAFKA_DATABENTO_PACE_MISSION_TOPIC", "options.databento.pace.mission");
    }

    public String missionControlTopic() {
        return value("KAFKA_SPX_MISSION_CONTROL_TOPIC", "options.spx.mission-control.current");
    }

    /**
     * Whole-underlying spread-skew snapshot (JSON {@code SpreadSkewSnapshot}, a SINGLE record keyed
     * {@code "SPX"}, re-emitted every ~5s) from spread-skew-service. Broadcast as event
     * {@code "spread-skew"} — a single-value latest-state cache, the mission-control sibling. NB:
     * the payload names its market {@code underlying} (no {@code symbol} field) with a NULLABLE
     * {@code expiry}, and carries its event time in {@code ts} (epoch ms).
     */
    public String spreadSkewTopic() {
        return value("KAFKA_SPREAD_SKEW_TOPIC", "options.spx.spread-skew.current");
    }

    /**
     * Discrete spread-skew transition events (FIRE/EXIT/REVERSAL/RESTART) from spread-skew-service —
     * the snapshot shape plus eventId/transitionType. Broadcast as event {@code "spread-skew-event"}
     * (own message.type), symbol-filtered client-side — STANDALONE and never cached, the
     * strike-intelligence turn-alert sibling.
     */
    public String spreadSkewEventsTopic() {
        return value("KAFKA_SPREAD_SKEW_EVENTS_TOPIC", "options.spx.spread-skew.events");
    }

    public String ibkrVolumeSandwichTopic() {
        return value("KAFKA_IBKR_VOLUME_SANDWICH_CURRENT_TOPIC",
                value("KAFKA_VOLUME_SANDWICH_CURRENT_TOPIC", "options.ibkr.volume-sandwich.current"));
    }

    public String databentoVolumeSandwichTopic() {
        return value("KAFKA_DATABENTO_VOLUME_SANDWICH_CURRENT_TOPIC", "options.databento.volume-sandwich.current");
    }

    public String databentoMissionSandwichTopic() {
        return value("KAFKA_DATABENTO_MISSION_SANDWICH_TOPIC", "options.databento.sandwich.mission");
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
     * Per-strike GEX OI-arrival status topic (JSON, from the gex service's OI-arrival watchdog). Carries
     * {@code status=OI_MISSING|OI_OK} per strike when TODAY's OI baseline has not arrived by the pre-open
     * deadline (fail-closed GEX = no records on {@link #databentoGexTopic()}, so this is the UI's only
     * explicit signal). Broadcast as event "gex-oi-status".
     */
    public String databentoGexOiStatusTopic() {
        return value("KAFKA_DATABENTO_GEX_OI_STATUS_TOPIC", "options.databento.gex.oi-status");
    }

    /** Unified support/resistance map (Avro, per SPX-equivalent bucket). Broadcast as event "strike-sr". */
    public String unifiedSrTopic() {
        return value("KAFKA_UNIFIED_SR_TOPIC", "options.spx.strike-sr.current");
    }

    /**
     * Gamma migration (Avro, per-chain last-value-wins). Broadcast as event "gamma-migration".
     *
     * <p>Where the magnet says which strike gamma sits ON, this says where it is GOING: peak
     * dwell and drift, the mass balance either side of spot, the strike heating up, and any wall
     * that has crossed zero.
     */
    public String gammaMigrationTopic() {
        return value("KAFKA_GAMMA_MIGRATION_TOPIC", "options.spx.gamma-migration.current");
    }

    /** GEX magnet strike (Avro, per-chain last-value-wins). Broadcast as event "gex-magnet". */
    public String databentoGexMagnetTopic() {
        return value("KAFKA_DATABENTO_GEX_MAGNET_TOPIC", "options.databento.gex.magnet");
    }

    public String databentoGexStrikeLifecycleTopic() {
        return value("KAFKA_DATABENTO_GEX_STRIKE_LIFECYCLE_TOPIC", "options.databento.gex.strike-lifecycle");
    }

    /**
     * ES-on-SPX aligned GEX (JSON whole-book per symbol|expiry, compacted, roll-forward). Broadcast as event
     * "es-gex". Default OFF — the whole ES-GEX-on-SPX feature ships dark until {@code GATEWAY_ES_GEX_ENABLED}.
     */
    public String esGexSpxAlignedTopic() {
        return value("KAFKA_ES_GEX_SPX_ALIGNED_TOPIC", "options.es-gex-spx-aligned");
    }

    public boolean esGexEnabled() {
        return "true".equalsIgnoreCase(value("GATEWAY_ES_GEX_ENABLED", "false"));
    }

    /**
     * Pre-open IBKR GEX status stream (OVERNIGHT-IBKR-GEX-GATE1-REQUIREMENT.md rev13 Phase 3):
     * per-strike FRESH/STALE/GATED/ABSENT statuses + path/manifest/heartbeat controls (JSON,
     * keyed strike rows + {@code __}-prefixed control keys) from the gex-service's IBKR
     * pre-open path. Broadcast as event "ibkr-preopen-status". Default OFF — the whole
     * feature ships dark until {@code GATEWAY_IBKR_PREOPEN_ENABLED}.
     */
    public boolean ibkrPreOpenEnabled() {
        return "true".equalsIgnoreCase(value("GATEWAY_IBKR_PREOPEN_ENABLED", "false"));
    }

    public String ibkrPreOpenStatusTopic() {
        return value("KAFKA_IBKR_GEX_STATUS_TOPIC", "options.ibkr.gex.status");
    }

    /** Status rows age out with the window: one full session covers restarts, never a stale next-day replay. */
    public long ibkrPreOpenStatusTtlMs() {
        return longValue("GATEWAY_IBKR_PREOPEN_STATUS_TTL_MS", 4 * 3_600_000L, 0L);
    }

    /** Long last-value-wins window for the aligned book (like gex-by-strike); the align service re-emits ~5s. */
    public long esGexTtlMs() {
        return longValue("GATEWAY_ES_GEX_TTL_MS", maxPainTtlMs(), 0L);
    }

    /**
     * ES strike-intelligence projected onto SPX strikes (JSON, one record per NATIVE ES identity
     * symbol|expiry|esStrike, compacted, roll-forward with withdrawal tombstones). Broadcast as event
     * "es-strike-intel" — a separate ES-origin overlay layer on the SPX board, distinct from the native
     * SPX "strike-intel". Default OFF: ships dark until {@code GATEWAY_ES_STRIKE_INTEL_ENABLED}.
     */
    public String esStrikeIntelSpxAlignedTopic() {
        return value("KAFKA_ES_STRIKE_INTEL_SPX_ALIGNED_TOPIC", "options.es-strike-intel-spx-aligned");
    }

    public boolean esStrikeIntelEnabled() {
        return "true".equalsIgnoreCase(value("GATEWAY_ES_STRIKE_INTEL_ENABLED", "false"));
    }

    /** Long last-value-wins window (like es-gex/gex-by-strike): a quiet-but-valid ES signal must not evict;
     *  withdrawal is explicit via the align service's tombstones, not TTL. */
    public long esStrikeIntelTtlMs() {
        return longValue("GATEWAY_ES_STRIKE_INTEL_TTL_MS", maxPainTtlMs(), 0L);
    }

    /** NATIVE per-strike strike-intel (the es4/SPX board's own strike-intelligence levels). A published
     *  level must stay visible for its whole 0DTE session and be removed ONLY at expiry (the per-strike
     *  replay is expiry-filtered and DashboardAssembler.expiryOpen drops it at 16:00 ET) — NOT evicted by
     *  the generic {@link #cacheTtlMs()} (default 15 min, sized for fast-ticking quote data) when a strike
     *  goes quiet. Same "quiet-but-valid must not evict" contract as {@link #esStrikeIntelTtlMs()}; default
     *  a long last-value-wins window (12h, like max-pain). */
    public long strikeIntelTtlMs() {
        return longValue("GATEWAY_STRIKE_INTEL_TTL_MS", maxPainTtlMs(), 0L);
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

    /** Option Price Behavior dashboard output topic (JSON, per symbol/trading-date). */
    public String optionPriceBehaviorDashboardTopic() {
        return value("OPTION_PRICE_BEHAVIOR_DASHBOARD_TOPIC",
                value("KAFKA_OPTION_PRICE_BEHAVIOR_DASHBOARD_TOPIC", "option-price-behavior-dashboard"));
    }

    /** Surface-residual per-contract topic (JSON, per symbol|expiry|strike). */
    public String optionPriceBehaviorByOptionTopic() {
        return value("KAFKA_OPTION_PRICE_BEHAVIOR_BY_OPTION_TOPIC", "option-price-behavior-by-option");
    }

    /** Surface-residual session aggregate topic (JSON, per symbol|trading-date). */
    public String optionPriceBehaviorSessionTopic() {
        return value("KAFKA_OPTION_PRICE_BEHAVIOR_SESSION_TOPIC", "option-price-behavior-session");
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

    /**
     * Manual assignment is intentional because every gateway replica needs a complete cache. Kafka does
     * not rebalance manually assigned consumers when a producer expands a topic, so refresh metadata
     * periodically and add new partitions without requiring a gateway restart.
     */
    public long partitionMetadataRefreshMs() {
        return longValue("GATEWAY_PARTITION_METADATA_REFRESH_MS", 30_000L, 1_000L);
    }

    /**
     * TOTAL metadata budget for ONE in-poll-loop partition refresh. Deliberately far below
     * {@link #metadataTimeoutMs} (the BOOTSTRAP budget): a refresh runs on the poll thread, so its cost is
     * stalled consumption, and a refresh that fails is a harmless no-op retried on the next interval.
     * Spending the full 30s bootstrap budget there would be self-inflicted lag.
     */
    public long partitionRefreshMetadataTimeoutMs() {
        return longValue("GATEWAY_PARTITION_REFRESH_METADATA_TIMEOUT_MS", 2_000L, 250L);
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
     * Freshness TTL for the DATABENTO per-strike GEX cache ({@code gex-by-strike}). GEX is derived from
     * once-daily Open Interest (published ~06:30 ET), so a given strike re-emits only when it trades — its
     * latest record is routinely older than the generic 15s selection barrier and older than the client's
     * selectedAtMs. Treated exactly like {@link #maxPainTtlMs() max-pain}: a long last-value-wins window so a
     * valid-but-slow GEX still replays on connect (default 12h). Selection isolation is still enforced by
     * matchesCachedSelection + the source filter; this only relaxes the time-freshness barrier.
     * {@code <= 0} preserves the generic "do not cache stale state" semantics (NOT infinite).
     */
    public long gexByStrikeTtlMs() {
        return longValue("GATEWAY_GEX_BY_STRIKE_TTL_MS", maxPainTtlMs(), 0L);
    }

    /** Per-strike GEX OI-status TTL — like gex-by-strike, a long last-value-wins window (default 12h): the
     *  badge is a slow, at-most-a-few-times-a-day signal and must survive reconnects all session. */
    public long gexOiStatusTtlMs() {
        return longValue("GATEWAY_GEX_OI_STATUS_TTL_MS", maxPainTtlMs(), 0L);
    }

    /** Per-strike gamma-lifecycle TTL — like gex-by-strike, a long last-value-wins window (default 12h). */
    public long gexStrikeLifecycleTtlMs() {
        return longValue("GATEWAY_GEX_STRIKE_LIFECYCLE_TTL_MS", maxPainTtlMs(), 0L);
    }

    /** Agent A short-premium recommendation output topic (JSON, key = trade_id). */
    public String shortPremiumRecommendationTopic() {
        return value("KAFKA_SHORT_PREMIUM_RECOMMENDATION_TOPIC", "options.short-premium.recommendation");
    }

    /**
     * Freshness TTL for the Agent A short-premium recommendation cache. A recommendation is emitted
     * ONCE when the paper trade is taken and stays valid for the life of that (0DTE) position — the
     * whole trading day. Like {@link #maxPainTtlMs() max-pain}, use a long last-value-wins window
     * (default 12h) so the overlay persists and replays on reconnect, rather than being evicted by the
     * generic 15-min TTL a few minutes after entry. {@code <= 0} preserves the generic semantics.
     */
    public long shortPremiumRecommendationTtlMs() {
        return longValue("GATEWAY_SHORT_PREMIUM_RECOMMENDATION_TTL_MS", maxPainTtlMs(), 0L);
    }

    /** ES 09:15 open-direction forecast topic (JSON, key = tradeDate; ONE forecast per day at 09:15 ET). */
    public String esOpenDirectionForecastTopic() {
        return value("KAFKA_ES_OPEN_DIRECTION_TOPIC", "es.open-direction.forecast");
    }

    /** ES open-direction per-horizon outcome topic (JSON, key = tradeDate; H1 10:30, H2 12:30, H3 16:00). */
    public String esOpenDirectionOutcomeTopic() {
        return value("KAFKA_ES_OPEN_DIRECTION_OUTCOME_TOPIC", "es.open-direction.outcome");
    }

    /**
     * Freshness TTL for the ES open-direction forecast + outcome caches. The forecast is emitted ONCE
     * at 09:15 ET and stays decision-relevant for the whole session (outcomes resolve at 10:30/12:30/
     * 16:00) — like {@link #maxPainTtlMs() max-pain}, use a long last-value-wins window (default 12h)
     * so a client that connects at 11:00 still receives the 09:15 forecast on replay, and the 12h
     * cache seek-back re-bootstraps it after a gateway restart. It must NEVER sit behind the generic
     * 15-min TTL or any market-data staleness gate. {@code <= 0} preserves the generic semantics.
     */
    public long esOpenDirectionTtlMs() {
        return longValue("GATEWAY_ES_OPEN_DIRECTION_TTL_MS", maxPainTtlMs(), 0L);
    }

    /** ES open-direction LIVE STATUS topic (JSON, key = tradeDate; one snapshot every 60s while a session is active). */
    public String esOpenDirectionStatusTopic() {
        return value("KAFKA_ES_OPEN_DIRECTION_STATUS_TOPIC", "es.open-direction.status");
    }

    /**
     * Freshness TTL for the ES open-direction live STATUS cache. Unlike its forecast/outcome siblings
     * (a once-a-day advisory on the long 12h window), the status is a 60s heartbeat whose only value is
     * being CURRENT — a status older than a few minutes (dead producer, overnight leftover) is misleading
     * and must read as absent (the UI strip simply vanishes), never replay as live. SHORT window, default
     * 5 min — the liquidity-heatmap/dealer-ledger freshness class, NEVER the 12h es-open-direction window.
     * {@code <= 0} preserves the generic "do not cache stale state" semantics (NOT infinite).
     */
    public long esOpenDirectionStatusTtlMs() {
        return longValue("GATEWAY_ES_OPEN_DIRECTION_STATUS_TTL_MS", 300_000L, 0L);
    }

    /**
     * SPX close-direction signal topic (JSON, key = symbol|expiry; interim signals 1/min in the
     * final 65 minutes + ONE frozen UP/DOWN/CHOPPY verdict in the T-11m power minute — design
     * CLOSE-DIRECTION-GATE1 §6). Resolved through the {@code _TOPIC} prefix helper, so on es4
     * ({@code TOPIC_PREFIX=es.}) this becomes {@code es.close.direction.signal} with no code change.
     */
    public String closeDirectionSignalTopic() {
        return value("KAFKA_CLOSE_DIRECTION_SIGNAL_TOPIC", "close.direction.signal");
    }

    /**
     * Freshness TTL for the close-direction cache. The VERDICT is emitted once (T-11m) and stays
     * decision-relevant until the close — the max-pain/es-open-direction long last-value-wins class
     * (default 12h), which also bounds the seek-back so a restarted gateway re-bootstraps the
     * session's signals. Interim signals share the window for eviction; their REPLAY freshness is
     * additionally gated by {@link #closeDirectionInterimFreshMs()} so a stale interim never replays
     * as live (design CD-R30's short-class requirement, enforced at the replay seam).
     */
    public long closeDirectionTtlMs() {
        return longValue("GATEWAY_CLOSE_DIRECTION_TTL_MS", maxPainTtlMs(), 0L);
    }

    /**
     * Replay freshness bound for close-direction INTERIM signals (default 5 min — the
     * es-open-direction-status heartbeat class): an interim older than this is simply absent on
     * late-join/replay; the frozen VERDICT is exempt (long window above).
     */
    public long closeDirectionInterimFreshMs() {
        return longValue("GATEWAY_CLOSE_DIRECTION_INTERIM_FRESH_MS", 300_000L, 0L);
    }

    /**
     * Greek-move-authenticity CURRENT verdict topic (JSON, key = symbol; the compacted last-value-wins
     * {@code MoveAuthenticityVerdict} — see {@link GreekMoveAuthTopics#GREEK_MOVE_AUTH_CURRENT}). One
     * record per symbol ("SPX"/"ES"); the standalone service re-publishes as the greeks move. Resolved
     * through the platform topic-prefix helper at deploy time, so on es4 ({@code TOPIC_PREFIX=es.}) this
     * default becomes {@code es.options.spx.greek-move-auth.current} without string-editing the constant.
     */
    public String greekMoveAuthCurrentTopic() {
        return value("KAFKA_GREEK_MOVE_AUTH_CURRENT_TOPIC", GreekMoveAuthTopics.GREEK_MOVE_AUTH_CURRENT);
    }

    /**
     * Freshness TTL for the greek-move-authenticity CURRENT verdict cache. Like the es-open-direction live
     * STATUS heartbeat (never its 12h forecast/outcome window), a verdict is only meaningful while CURRENT:
     * an authenticity read older than a few minutes (dead producer, overnight leftover, gateway catching up
     * on a backlog) is misleading and must read as absent (the UI move-authenticity track simply vanishes),
     * never replay as live. SHORT window, default 5 min — the liquidity-heatmap/dealer-ledger/status
     * freshness class. {@code <= 0} preserves the generic "do not cache stale state" semantics (NOT infinite).
     */
    public long greekMoveAuthTtlMs() {
        return longValue("GATEWAY_GREEK_MOVE_AUTH_TTL_MS", 300_000L, 0L);
    }

    /**
     * Compacted CURRENT topic of the standalone spot-vol-regime service (JSON
     * {@code SpotVolRegimeSnapshot} — see {@link SpotVolRegimeTopics#SPOT_VOL_REGIME_CURRENT}). One
     * record per symbol ("SPX"); the service heartbeats it every frame. Resolved through the platform
     * topic-prefix helper at deploy time, matching the greek-move-auth sibling above.
     */
    public String spotVolRegimeTopic() {
        return value("KAFKA_SPOT_VOL_REGIME_CURRENT_TOPIC", SpotVolRegimeTopics.SPOT_VOL_REGIME_CURRENT);
    }

    /**
     * Freshness TTL for the spot-vol-regime CURRENT cache. Same SHORT freshness class and rationale
     * as {@link #greekMoveAuthTtlMs()}: a regime read minutes old (dead producer, overnight leftover)
     * is misleading and must read as absent — the UI regime pill simply vanishes — never replay as
     * live. Default 5 min.
     */
    public long spotVolRegimeTtlMs() {
        return longValue("GATEWAY_SPOT_VOL_REGIME_TTL_MS", 300_000L, 0L);
    }

    /**
     * Compacted CURRENT topic of the standalone indicator-service (JSON
     * {@code IndicatorSnapshot}, rev 14 §7.1). One record per canonical symbol
     * (ES|SPX) every 5 s during the active session plus event-triggered publishes.
     * Resolved through the platform topic-prefix helper at deploy time.
     */
    public String indicatorsSnapshotTopic() {
        return value("KAFKA_INDICATORS_SNAPSHOT_TOPIC",
                com.optionsedge.contracts.indicators.IndicatorTopics.INDICATORS_SNAPSHOT_CURRENT);
    }

    /**
     * BOTH indicator CURRENT topics this environment must consume (rev 14 §7.3):
     * the prefix-resolved local topic (dev/prod = SPX computed locally; es4 = the
     * es.-prefixed native ES stream) PLUS the es4-mirrored ES topic
     * {@code es.options.indicators.snapshot.current} on dev/prod. On es4 the two
     * coincide and the set collapses to one — no duplicate subscription.
     */
    public java.util.Set<String> indicatorsSnapshotTopics() {
        java.util.LinkedHashSet<String> topics = new java.util.LinkedHashSet<>();
        topics.add(indicatorsSnapshotTopic());
        topics.add("es." + com.optionsedge.contracts.indicators.IndicatorTopics
                .INDICATORS_SNAPSHOT_CURRENT);
        return topics;
    }

    /**
     * Freshness TTL for the indicators CURRENT cache — same SHORT class as
     * {@link #spotVolRegimeTtlMs()}: a minutes-old snapshot (dead producer) must
     * read as absent on late-join, never replay as live. The page's own
     * transport-staleness banner handles sub-TTL aging. Default 5 min.
     */
    public long indicatorsTtlMs() {
        return longValue("GATEWAY_INDICATORS_TTL_MS", 300_000L, 0L);
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

    /**
     * SHORT live-signal TTL for dealer-ledger role records. The dealer-ledger {@code state} topic is a
     * heartbeat (the service emits a routine frame on every flow evaluation), so an ARMED/DEFENDED
     * permission is only valid while the producer keeps refreshing it. Freshness MUST use this window,
     * never the generic 15-min {@link #cacheTtlMs()} — otherwise a stalled/dead producer's last
     * permission would render as active for minutes. Defaults to {@link #maxStaleMs()} (15s).
     */
    public long dealerLedgerTtlMs() {
        return longValue("GATEWAY_DEALER_LEDGER_TTL_MS", maxStaleMs(), 0L);
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
        String resolved = fallback;
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            resolved = env.trim();
        } else {
            String property = System.getProperty(key);
            if (property != null && !property.isBlank()) {
                resolved = property.trim();
            }
        }
        // Anti-divergence: topic env keys (…_TOPIC) get TOPIC_PREFIX applied so ONE binary serves
        // SPX (no prefix) and es4 (TOPIC_PREFIX=es.) with env-only difference — matching the
        // processing-common convention. The guard makes SPX a strict no-op (empty prefix) and never
        // double-prefixes an already-prefixed default (e.g. the es.open-direction.* topics).
        if (key.endsWith("_TOPIC")) {
            return applyTopicPrefix(resolved);
        }
        return resolved;
    }

    /** Prepend TOPIC_PREFIX to a topic name; no-op when the prefix is empty or already applied. */
    static String applyTopicPrefix(String topic) {
        String prefix = System.getenv("TOPIC_PREFIX");
        if (prefix == null || prefix.isBlank()) {
            prefix = System.getProperty("TOPIC_PREFIX", "");
        }
        prefix = prefix == null ? "" : prefix.trim();
        if (prefix.isEmpty() || topic == null || topic.isBlank() || topic.startsWith(prefix)) {
            return topic;
        }
        return prefix + topic;
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
}
