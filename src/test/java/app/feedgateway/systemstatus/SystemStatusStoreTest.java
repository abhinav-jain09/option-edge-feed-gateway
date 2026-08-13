package app.feedgateway.systemstatus;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemStatusStoreTest {

    /**
     * Honesty contract: SQL NULL booleans must reach the JSON as null, never as false.
     * {@code getBoolean()} returns false for NULL, so a topic whose watcher never evaluated
     * would_have_fired/shadow would silently render as an affirmative "did not fire / not shadow".
     * Drives the real row mapper through topics(), not the helper in isolation.
     */
    @Test
    void topicsPassSqlNullBooleansThroughAsNullNotFalse() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getString("topic")).thenReturn("underlying.spx.price");
        // NULL columns: the JDBC contract is getBoolean()=false / getLong()=0 with wasNull()=true.
        when(rs.getBoolean(anyString())).thenReturn(false);
        when(rs.getLong(anyString())).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);

        List<Map<String, Object>> rows = new SystemStatusStore(dataSource(rs), 1).topics("prod");

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertNull(row.get("wouldHaveFired"), "NULL would_have_fired must render unknown, not false");
        assertNull(row.get("shadow"), "NULL shadow must render unknown, not false");
        assertNull(row.get("ageS"), "NULL age must stay null (regression guard for the long path)");
    }

    /** A real false must still come through as false — the null fix must not eat genuine values. */
    @Test
    void topicsKeepGenuineBooleanValues() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getString("topic")).thenReturn("underlying.spx.price");
        when(rs.getBoolean("would_have_fired")).thenReturn(true);
        when(rs.getBoolean("shadow")).thenReturn(false);
        when(rs.wasNull()).thenReturn(false);

        Map<String, Object> row = new SystemStatusStore(dataSource(rs), 1).topics("prod").get(0);

        assertEquals(Boolean.TRUE, row.get("wouldHaveFired"));
        assertEquals(Boolean.FALSE, row.get("shadow"));
    }

    private static HikariDataSource dataSource(ResultSet rs) throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(rs);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }
}
