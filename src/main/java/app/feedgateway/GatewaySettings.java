package app.feedgateway;

import org.springframework.stereotype.Component;

@Component
public final class GatewaySettings {
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "192.168.100.252:9092,192.168.100.252:9094,192.168.100.252:9096";
    private static final String DEFAULT_SCHEMA_REGISTRY_URL = "http://192.168.100.252:8082";

    public boolean enabled() {
        return boolValue("KAFKA_ENABLED", true);
    }

    public String bootstrapServers() {
        return value("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS);
    }

    public String schemaRegistryUrl() {
        return value("KAFKA_SCHEMA_REGISTRY_URL", DEFAULT_SCHEMA_REGISTRY_URL);
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
        return normalizeExpiry(value("IB_EXPIRY", ""));
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
}
