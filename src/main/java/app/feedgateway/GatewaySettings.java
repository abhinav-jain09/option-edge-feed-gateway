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

    public String displayTopic() {
        return value("KAFKA_DISPLAY_TOPIC", "display");
    }

    public String paceTopic() {
        return value("KAFKA_PACE_TOPIC", "options.ibkr.pace");
    }

    public String directionalPressureTopic() {
        return value("KAFKA_DIRECTIONAL_PRESSURE_TOPIC", "options.ibkr.directional-pressure");
    }

    public String volumeSandwichTopic() {
        return value("KAFKA_VOLUME_SANDWICH_CURRENT_TOPIC", "display.volume.sandwich.current");
    }

    public String volumeSandwichAlertsTopic() {
        return value("KAFKA_VOLUME_SANDWICH_ALERTS_TOPIC", "display.volume.sandwich.alerts");
    }

    public String unusualWhalesGexTopic() {
        return value("KAFKA_UNUSUAL_WHALES_GEX_TOPIC", "options.unusualwhales.gex.strike");
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

    public long cacheTtlMs() {
        return intValue("GATEWAY_CACHE_TTL_MS", 900_000, 0);
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
}
