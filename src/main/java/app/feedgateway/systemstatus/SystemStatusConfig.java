package app.feedgateway.systemstatus;

import app.feedgateway.GatewaySettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Wiring for the System Status page's read side. Follows the same quarantine rules the pin-flow
 * datasource established (§6.3): built ONLY when a URL is configured, {@code @Lazy} with
 * {@code initializationFailTimeout=-1} so no connection is opened or validated at boot, read-only, tiny
 * pool, short driver timeouts. The gateway must start and serve market data with no ledger at all — the
 * page then reports LEDGER UNAVAILABLE instead of the gateway failing readiness.
 */
@Configuration
public class SystemStatusConfig {

    /**
     * Wrapped, never a bare {@code HikariDataSource} bean: the gateway's pin-flow store injects
     * {@code ObjectProvider<HikariDataSource>} WITHOUT a qualifier, so a second bean of that type makes
     * that injection ambiguous and the gateway fails to start (found by actually booting it).
     */
    @Bean(destroyMethod = "close")
    public SystemStatusDataSource systemStatusDataSource(GatewaySettings settings) {
        String url = settings.systemStatusJdbcUrl();
        if (url == null || url.isBlank()) {
            return new SystemStatusDataSource(null);   // no ledger → endpoint reports unavailable
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("oe-watch-status");
        cfg.setJdbcUrl(url);
        // Fail-closed transport (design §3.5): TLS is ENFORCED here, not trusted from the URL. A URL
        // without sslmode would otherwise connect in plaintext and carry ledger credentials over the
        // LAN. Loopback is the one exception — that is the ssh-tunnel endpoint, already encrypted, and
        // requiring server certs on a tunnel would make the tunnel impossible to use.
        // Fail-closed by DEFAULT: TLS is enforced unless the URL is loopback (an ssh-tunnel endpoint,
        // already encrypted) or the operator has explicitly accepted plaintext for a server that has
        // none. Enforcing silently would just disable the feature — the Postgres here answers
        // "server does not support SSL" — so the choice is explicit and reported to the page.
        boolean loopback = url.contains("//localhost") || url.contains("//127.0.0.1")
                || url.contains("//[::1]");
        boolean plaintextAccepted = settings.systemStatusAllowPlaintext();
        String user = settings.systemStatusDbUser();
        if (!user.isBlank()) {
            cfg.setUsername(user);
        }
        String password = settings.systemStatusDbPassword();
        if (!password.isBlank()) {
            cfg.setPassword(password);
        }
        cfg.setReadOnly(true);
        cfg.setMaximumPoolSize(2);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(2_000L);
        cfg.setInitializationFailTimeout(-1L);   // never validate at boot
        cfg.setAutoCommit(true);
        Properties dsProps = new Properties();
        // Backstop for a socket that never answers, sized off the WIDEST per-statement budget (the
        // last-run section's) so it can never fire before the statement timeout it is backing up.
        // Long arithmetic + clamp: an operator-supplied maximum plus a constant must not wrap negative.
        long socketTimeout = Math.min(
                (long) GatewaySettings.SYSTEM_STATUS_MAX_TIMEOUT_S + 2L,
                Math.max((long) settings.systemStatusQueryTimeoutSeconds(),
                         (long) settings.systemStatusSlowQueryTimeoutSeconds()) + 2L);
        dsProps.setProperty("socketTimeout", Long.toString(socketTimeout));
        dsProps.setProperty("connectTimeout", "2");
        dsProps.setProperty("loginTimeout", "2");
        dsProps.setProperty("ApplicationName", "feed-gateway-system-status");
        if (!loopback && !plaintextAccepted) {
            dsProps.setProperty("ssl", "true");
            dsProps.setProperty("sslmode", "verify-full");
        }
        cfg.setDataSourceProperties(dsProps);
        return new SystemStatusDataSource(new HikariDataSource(cfg));
    }

    @Bean
    public SystemStatusStore systemStatusStore(GatewaySettings settings,
                                               SystemStatusDataSource dataSource) {
        return new SystemStatusStore(dataSource.delegate(),
                settings.systemStatusQueryTimeoutSeconds());
    }

    @Bean(destroyMethod = "close")
    public ConsumerLagReader consumerLagReader(GatewaySettings settings) {
        return new ConsumerLagReader(settings.bootstrapServers(),
                ConsumerLagReader.parseRegistry(settings.systemStatusLagRegistry()),
                settings.systemStatusCacheMs(),
                settings.systemStatusAdminTimeoutMs());
    }
}
