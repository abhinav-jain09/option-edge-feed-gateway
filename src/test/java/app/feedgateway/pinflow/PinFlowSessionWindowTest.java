package app.feedgateway.pinflow;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pin-flow session window used to be hardcoded to the SPX cash session (09:30 → 16:01 ET), so on a
 * near-24h market the Flow Explorer could never see the evening/overnight session. On es4 (ES trades
 * 18:00 ET → 17:00 ET the next day) a request at 18:55 ET resolved to the window [today 09:30, today
 * 16:01) — a range in which ES was closed and had ZERO rows — and the endpoint returned an empty payload.
 *
 * <p>The window is now configurable and rolls the end to date+1 when it spans midnight.
 */
class PinFlowSessionWindowTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate DATE = LocalDate.of(2026, 7, 19); // a Sunday — ES reopens 18:00 ET

    /** Captures the [start,end) timestamps the store binds into the query. */
    private static final class CapturingDataSource implements DataSource {
        final List<Timestamp> bound = new ArrayList<>();

        @Override public Connection getConnection() {
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "prepareStatement" -> preparedStatement();
                        case "close", "setAutoCommit", "setReadOnly" -> null;
                        case "isClosed" -> false;
                        default -> defaultFor(method.getReturnType());
                    });
        }

        private PreparedStatement preparedStatement() {
            return (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        if ("setTimestamp".equals(method.getName())) {
                            bound.add((Timestamp) args[1]);
                            return null;
                        }
                        if ("executeQuery".equals(method.getName())) {
                            return emptyResultSet();
                        }
                        return defaultFor(method.getReturnType());
                    });
        }

        private static ResultSet emptyResultSet() {
            return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> "next".equals(method.getName()) ? false
                            : defaultFor(method.getReturnType()));
        }

        private static Object defaultFor(Class<?> t) {
            if (!t.isPrimitive()) return null;
            if (t == boolean.class) return false;
            if (t == int.class) return 0;
            if (t == long.class) return 0L;
            return null;
        }

        @Override public Connection getConnection(String u, String p) { return getConnection(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @Test
    void sameDaySessionKeepsTheSpxCashWindow() throws SQLException {
        CapturingDataSource ds = new CapturingDataSource();
        PinFlowStore store = new PinFlowStore(ds, ET, LocalTime.of(9, 30), LocalTime.of(16, 1), 10, 0L);

        store.query(DATE, 7490, 7590, 1_000L);

        assertFalse(store.sessionSpansMidnight(), "09:30 → 16:01 is a same-day session");
        assertEquals(ZonedDateTime.of(DATE, LocalTime.of(9, 30), ET).toInstant(), bound(ds, 0).toInstant());
        assertEquals(ZonedDateTime.of(DATE, LocalTime.of(16, 1), ET).toInstant(), bound(ds, 1).toInstant());
    }

    @Test
    void midnightSpanningSessionRollsTheEndToTheNextDay() throws SQLException {
        // ES on es4: 18:00 ET → 17:00 ET the NEXT day.
        CapturingDataSource ds = new CapturingDataSource();
        PinFlowStore store = new PinFlowStore(ds, ET, LocalTime.of(18, 0), LocalTime.of(17, 0), 10, 0L);

        store.query(DATE, 7000, 7800, 1_000L);

        assertTrue(store.sessionSpansMidnight(), "18:00 → 17:00 crosses midnight");
        assertEquals(ZonedDateTime.of(DATE, LocalTime.of(18, 0), ET).toInstant(), bound(ds, 0).toInstant(),
                "start = the session's own evening open");
        assertEquals(ZonedDateTime.of(DATE.plusDays(1), LocalTime.of(17, 0), ET).toInstant(), bound(ds, 1).toInstant(),
                "end must roll to the NEXT day, else the range is inverted and returns nothing");
    }

    private static Timestamp bound(CapturingDataSource ds, int i) {
        assertTrue(ds.bound.size() > i, "expected the store to bind a [start,end) range");
        return ds.bound.get(i);
    }
}
