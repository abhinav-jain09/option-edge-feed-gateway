package app.feedgateway.systemstatus;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the watcher's verification ledger (PIPELINE-STALL-REJECT-ALERTING-DESIGN.md §3.4–3.6).
 *
 * <p>Reads ONLY the {@code oe_watch.v_*} views with the {@code oe_watch_reader} role — never the base
 * table, never an application schema. Every method degrades to "unavailable" rather than throwing into
 * the request path: the gateway is a live market-data process and this page must not be able to hurt it.
 *
 * <p><b>Honesty contract.</b> Absence is never rendered as health:
 * <ul>
 *   <li>ledger unreachable → {@code available=false} and the page says so with the last known time;</li>
 *   <li>a registered topic with no ledger evidence → {@code evidence=NO_EVIDENCE} (never a green state);</li>
 *   <li>a service whose restart sample failed → {@code status=UNKNOWN}, {@code restarts=null} — the
 *       watcher publishes those rows itself, so a stale OK number cannot survive here.</li>
 * </ul>
 */
public class SystemStatusStore {

    private final HikariDataSource dataSource;
    private final int queryTimeoutSeconds;

    public SystemStatusStore(HikariDataSource dataSource, int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = Math.max(1, queryTimeoutSeconds);
    }

    public boolean configured() {
        return dataSource != null;
    }

    /**
     * One row per topic the watcher observed, newest observation per (env, subject).
     *
     * <p>{@code thresholdS} and {@code guard} come from migration 003 and are what make the page's
     * per-topic view judgeable rather than merely descriptive: an age means nothing without its OWN
     * threshold (120s and 1200s topics are not comparable on raw age), and {@code guard} is what lets
     * a stalled ROOT be shown as the root instead of colouring in everything downstream of it.
     * Every one of these is nullable and is passed through as null — never defaulted — because a
     * missing threshold must render as "unknown", not as a threshold of zero.
     */
    public List<Map<String, Object>> topics(String env) throws SQLException {
        String sql = "SELECT topic, state, age_s, last_observation, as_of,"
                + " threshold_s, guard, guard_lease_left_s, consec_stale, consec_ok,"
                + " phase, would_have_fired, shadow"
                + " FROM oe_watch.v_topic_state WHERE env = ? ORDER BY topic";
        List<Map<String, Object>> out = new ArrayList<>();
        query(sql, env, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("topic", rs.getString("topic"));
            row.put("state", rs.getString("state"));
            row.put("ageS", nullableLong(rs, "age_s"));
            row.put("lastObservation", rs.getString("last_observation"));
            row.put("asOf", ts(rs, "as_of"));
            row.put("thresholdS", nullableLong(rs, "threshold_s"));
            row.put("guard", rs.getString("guard"));
            row.put("guardLeaseLeftS", nullableLong(rs, "guard_lease_left_s"));
            row.put("consecStale", nullableLong(rs, "consec_stale"));
            row.put("consecOk", nullableLong(rs, "consec_ok"));
            row.put("phase", rs.getString("phase"));
            row.put("wouldHaveFired", nullableBoolean(rs, "would_have_fired"));
            row.put("shadow", nullableBoolean(rs, "shadow"));
            out.add(row);
        });
        return out;
    }

    /** SQL NULL stays null instead of collapsing to 0 — a zero here would read as a measurement. */
    private static Long nullableLong(java.sql.ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    /** SQL NULL stays null instead of collapsing to false — "unknown" must never read as "did not fire". */
    private static Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws SQLException {
        boolean v = rs.getBoolean(column);
        return rs.wasNull() ? null : v;
    }

    /** Open incidents (per the transition_seq-ordered projection), most recent first. */
    public List<Map<String, Object>> openIncidents(String env) throws SQLException {
        String sql = "SELECT topic, current_transition, transition_seq, as_of"
                + " FROM oe_watch.v_incidents WHERE env = ? AND open_now ORDER BY as_of DESC";
        List<Map<String, Object>> out = new ArrayList<>();
        query(sql, env, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("topic", rs.getString("topic"));
            row.put("transition", rs.getString("current_transition"));
            row.put("transitionSeq", rs.getLong("transition_seq"));
            row.put("asOf", ts(rs, "as_of"));
            out.add(row);
        });
        return out;
    }

    /**
     * §3.6 per-service restart counts for the current session/expiry. One row per service EVERY cycle,
     * so 0 / NO_PODS / PARTIAL / UNKNOWN are all representable and a missing cell is impossible.
     */
    public Map<String, Map<String, Object>> restarts(String env) throws SQLException {
        String sql = "SELECT service, session_key, restarts_this_session, status,"
                + " unattributed_prior, last_restart_at, as_of"
                + " FROM oe_watch.v_restarts_latest WHERE env = ? ORDER BY service";
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        query(sql, env, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            String service = rs.getString("service");
            row.put("sessionKey", rs.getString("session_key"));
            Object restarts = rs.getObject("restarts_this_session");
            row.put("restartsThisSession", restarts == null ? null : rs.getInt("restarts_this_session"));
            row.put("restartsStatus", rs.getString("status"));
            Object prior = rs.getObject("unattributed_prior");
            row.put("restartsUnattributedPrior", prior == null ? null : rs.getInt("unattributed_prior"));
            row.put("restartsLastAt", ts(rs, "last_restart_at"));
            row.put("restartsAsOf", ts(rs, "as_of"));
            out.put(service, row);
        });
        return out;
    }

    /** Last watcher run per env: proves the evidence is current, and surfaces BLIND outcomes. */
    public Map<String, Object> lastRun(String env) throws SQLException {
        String sql = "SELECT started_at, finished_at, outcome FROM oe_watch.v_runs"
                + " WHERE env = ? AND watcher = 'freshness' AND started_at IS NOT NULL"
                + " ORDER BY started_at DESC LIMIT 1";
        Map<String, Object> out = new LinkedHashMap<>();
        query(sql, env, rs -> {
            out.put("startedAt", ts(rs, "started_at"));
            out.put("finishedAt", ts(rs, "finished_at"));
            out.put("outcome", rs.getString("outcome"));
        });
        return out;
    }

    // ---------------------------------------------------------------- plumbing
    private interface RowHandler {
        void accept(ResultSet rs) throws SQLException;
    }

    private void query(String sql, String env, RowHandler handler) throws SQLException {
        if (dataSource == null) {
            throw new SQLException("oe_watch datasource not configured");
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(queryTimeoutSeconds);
            ps.setString(1, env);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    handler.accept(rs);
                }
            }
        }
    }

    private static String ts(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant().toString();
    }
}
