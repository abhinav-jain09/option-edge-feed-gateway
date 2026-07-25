package app.feedgateway.systemstatus;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Owns the System Status page's read-only Hikari pool.
 *
 * <p>Deliberately NOT a {@code HikariDataSource} bean: the gateway already has one (pin-flow) that is
 * injected as an unqualified {@code ObjectProvider<HikariDataSource>}, so publishing a second bean of
 * that type makes the injection ambiguous and the whole gateway fails to start. Wrapping keeps this
 * datasource invisible to every other consumer.
 */
public final class SystemStatusDataSource implements AutoCloseable {

    private final HikariDataSource delegate;

    public SystemStatusDataSource(HikariDataSource delegate) {
        this.delegate = delegate;
    }

    HikariDataSource delegate() {
        return delegate;
    }

    public boolean configured() {
        return delegate != null;
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
    }
}
