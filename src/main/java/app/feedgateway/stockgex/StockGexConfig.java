package app.feedgateway.stockgex;

import app.feedgateway.GatewaySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wiring for the stock-gex proxy. The client is built unconditionally — unlike the Postgres-backed
 * features, constructing it opens no socket and validates nothing, so there is no boot-time cost to
 * quarantine. A gateway whose {@code stock-gex.base-url} points at nothing still starts and serves market
 * data; the endpoints then answer 502 with a JSON error, which is exactly the state the page must be able
 * to render ("the service is down") rather than a failed readiness probe for the whole gateway.
 */
@Configuration
public class StockGexConfig {

    /**
     * {@code destroyMethod} is not cosmetic: the client owns a watchdog scheduler that bounds every
     * body read and every idle stream, and a scheduler that outlives the context is a thread that
     * outlives the application.
     */
    @Bean(destroyMethod = "close")
    public StockGexUpstream stockGexUpstream(GatewaySettings settings) {
        return new StockGexUpstream(settings.stockGexBaseUrl(),
                Duration.ofMillis(settings.stockGexConnectTimeoutMs()),
                Duration.ofMillis(settings.stockGexBoardTimeoutMs()));
    }
}
