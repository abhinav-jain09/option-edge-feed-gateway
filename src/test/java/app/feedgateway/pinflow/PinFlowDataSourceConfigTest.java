package app.feedgateway.pinflow;

import app.feedgateway.GatewaySettings;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §6.3 startup / readiness isolation. The datasource is lazy + conditional: with NO {@code POSTGRES_*}
 * settings the context still starts (no datasource bean is created, none is validated at boot), and
 * the {@link PinFlowStore} is wired with a {@code null} datasource so the endpoint answers 503. When a
 * JDBC URL IS present the {@link HikariDataSource} is created — but still with boot-time validation
 * disabled, so an unreachable DB does not fail startup either.
 */
class PinFlowDataSourceConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(GatewaySettings.class)
            .withUserConfiguration(PinFlowDataSourceConfig.class);

    @Test
    void contextStartsWithNoDatabaseConfigured() {
        // Ensure no ambient URL leaks in from the environment.
        String prev = System.getProperty("pinflow.postgres.jdbc-url");
        System.clearProperty("pinflow.postgres.jdbc-url");
        try {
            runner.run(ctx -> {
                assertThat(ctx).hasNotFailed();
                // No datasource bean when no URL is present (§6.3): nothing to validate at boot.
                assertThat(ctx.getBeansOfType(DataSource.class)).isEmpty();
                assertThat(ctx.getBeansOfType(HikariDataSource.class)).isEmpty();
                // The store still exists and reports the DB as unconfigured → endpoint 503.
                PinFlowStore store = ctx.getBean(PinFlowStore.class);
                assertThat(store.dbConfigured()).isFalse();
                // The bulkhead executor is always present.
                assertThat(ctx.getBean(PinFlowExecutor.class)).isNotNull();
            });
        } finally {
            if (prev != null) {
                System.setProperty("pinflow.postgres.jdbc-url", prev);
            }
        }
    }

    @Test
    void datasourceIsBuiltLazilyWhenUrlPresentWithoutValidatingAtBoot() {
        // A deliberately unreachable URL: if the config validated a connection at boot the context
        // would fail. It must NOT — initializationFailTimeout=-1 defers the first connection.
        // GatewaySettings reads env/system-properties (its single source of truth), so drive it that way.
        String prev = System.getProperty("pinflow.postgres.jdbc-url");
        System.setProperty("pinflow.postgres.jdbc-url", "jdbc:postgresql://127.0.0.1:1/does_not_exist");
        try {
            runner.run(ctx -> {
                assertThat(ctx).hasNotFailed();
                PinFlowStore store = ctx.getBean(PinFlowStore.class);
                assertThat(store.dbConfigured()).isTrue();
                assertThat(ctx.getBeansOfType(HikariDataSource.class)).isNotEmpty();
            });
        } finally {
            if (prev == null) {
                System.clearProperty("pinflow.postgres.jdbc-url");
            } else {
                System.setProperty("pinflow.postgres.jdbc-url", prev);
            }
        }
    }
}
