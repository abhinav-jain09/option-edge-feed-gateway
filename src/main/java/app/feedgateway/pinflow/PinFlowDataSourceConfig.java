package app.feedgateway.pinflow;

import app.feedgateway.GatewaySettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * §6 gateway Postgres isolation. The gateway is a live market-data path, so the DB dependency is
 * fully quarantined:
 *
 * <ul>
 *   <li><b>Lazy + conditional (§6.3):</b> the {@link DataSource} bean is built ONLY when a JDBC URL is
 *       resolvable via {@link GatewaySettings#pinFlowJdbcUrl()} — i.e. {@code pinflow.postgres.jdbc-url}
 *       or the shared {@code POSTGRES_JDBC_URL}. We deliberately do NOT use {@code @ConditionalOnProperty}
 *       (which reads the Spring {@code Environment}) so there is ONE source of truth for the URL — the
 *       same env/system-property resolver every other gateway setting uses. A {@code null}-returning
 *       {@code @Bean} is treated by Spring as an ABSENT bean, so with no URL there is simply no
 *       datasource. We also do NOT add {@code spring-boot-starter-jdbc}, so
 *       {@code DataSourceAutoConfiguration} never runs and never builds/validates a datasource at boot.</li>
 *   <li><b>No boot validation (§6.3):</b> Hikari is created {@code @Lazy} with
 *       {@code initializationFailTimeout=-1}, so it NEVER opens/validates a connection at startup; the
 *       first connection is opened on the first request and a down DB surfaces as a 503, never a failed
 *       startup or unready pod.</li>
 *   <li><b>No health probe (§6.3):</b> there is no actuator DataSource health indicator on the classpath
 *       (the gateway has no actuator dependency), so DB state can never flap readiness/liveness.</li>
 *   <li><b>Pool + timeouts (§6.2):</b> small pool (max 4), 2s connection-acquisition timeout, read-only,
 *       and short socket/connect timeouts baked onto the driver.</li>
 * </ul>
 *
 * When no URL is configured, {@link #pinFlowDataSource} returns {@code null} → {@link #pinFlowStore}
 * receives a {@code null} datasource → the endpoint answers 503 (§5.3) while everything else is unaffected.
 */
@Configuration
public class PinFlowDataSourceConfig {

    /**
     * Read-only pin_* datasource. Returns {@code null} (→ no bean) unless a JDBC URL is configured.
     * {@code @Lazy} + {@code initializationFailTimeout=-1} guarantee no connection is opened at boot.
     */
    @Bean(destroyMethod = "close")
    @Lazy
    public HikariDataSource pinFlowDataSource(GatewaySettings settings) {
        String url = settings.pinFlowJdbcUrl();
        if (url == null || url.isBlank()) {
            return null; // §6.3: no DB configured → no datasource bean; endpoint 503s
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("pin-flow");
        cfg.setJdbcUrl(url);
        String user = settings.pinFlowDbUser();
        if (!user.isBlank()) {
            cfg.setUsername(user);
        }
        String password = settings.pinFlowDbPassword();
        if (!password.isBlank()) {
            cfg.setPassword(password);
        }
        cfg.setReadOnly(true);
        cfg.setMaximumPoolSize(settings.pinFlowPoolMax());
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(settings.pinFlowConnectionTimeoutMs()); // §6.2: 503 on pool exhaustion
        cfg.setInitializationFailTimeout(-1L);                           // §6.3: NEVER validate at boot
        cfg.setAutoCommit(true);
        // §6.2: short socket + connect/login timeouts at the driver level so a wedged network can never
        // hang a bulkhead thread past the deadline. Query-level timeout is set per PreparedStatement.
        Properties dsProps = new Properties();
        dsProps.setProperty("socketTimeout",
                Long.toString(Math.max(1L, settings.pinFlowQueryTimeoutSeconds() + 2L)));
        dsProps.setProperty("connectTimeout", "2");
        dsProps.setProperty("loginTimeout", "2");
        dsProps.setProperty("ApplicationName", "feed-gateway-pin-flow");
        cfg.setDataSourceProperties(dsProps);
        return new HikariDataSource(cfg);
    }

    @Bean(destroyMethod = "close")
    public PinFlowExecutor pinFlowExecutor(GatewaySettings settings) {
        return new PinFlowExecutor(settings.pinFlowExecutorThreads(), settings.pinFlowExecutorQueue());
    }

    @Bean
    public PinFlowStore pinFlowStore(GatewaySettings settings,
                                     ObjectProvider<HikariDataSource> dataSource) {
        return new PinFlowStore(settings, dataSource.getIfAvailable());
    }
}
