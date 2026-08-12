package app.feedgateway.contexttape;

import app.feedgateway.GatewaySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wiring for the context-tape proxy. The client is built unconditionally — constructing it opens no
 * socket and validates nothing, so there is no boot-time cost to quarantine. A gateway whose
 * {@code context-tape.base-url} points at nothing still starts and serves market data; the endpoint
 * then answers 502 with a JSON error, which is exactly the state the page must be able to render
 * ("the service is down") rather than a failed readiness probe for the whole gateway.
 */
@Configuration
public class ContextTapeConfig {

    /**
     * {@code destroyMethod} is not cosmetic: the upstream owns its reader and closer pools plus the
     * HTTP client, and pools or a client that outlive the context are threads and sockets that
     * outlive the application.
     */
    @Bean(destroyMethod = "close")
    public ContextTapeUpstream contextTapeUpstream(GatewaySettings settings) {
        return new ContextTapeUpstream(settings.contextTapeBaseUrl(),
                Duration.ofMillis(settings.contextTapeConnectTimeoutMs()),
                Duration.ofMillis(settings.contextTapeRequestTimeoutMs()));
    }
}
