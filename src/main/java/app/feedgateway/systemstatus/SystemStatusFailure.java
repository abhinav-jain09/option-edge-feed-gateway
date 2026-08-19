package app.feedgateway.systemstatus;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One ledger section's failure, split into what the BROWSER may see and what only the LOG may see.
 *
 * <p>The prod incident of 2026-08-19 was diagnosed from a page that said nothing but "PSQLException",
 * so the detail matters — but the detail is server text. PostgreSQL error messages carry schema and
 * table names, SQL fragments, {@code Where:} call stacks, hostnames and whatever a server-side
 * function chose to raise, and a JDBC exception can repeat connection properties. So:
 *
 * <ul>
 *   <li>{@link #code()} and {@link #sqlState()} are the ONLY fields that reach the browser. They are a
 *       closed vocabulary and a five-character SQLState — enough to tell a timeout from a bad password
 *       from a dropped column, which is the whole diagnostic value the incident was missing.</li>
 *   <li>{@link #detail()} is for the log. It is bounded, single-line, walks the cause AND
 *       {@link SQLException#getNextException()} chain (where pgjdbc hides the real cause), and has
 *       secrets redacted — a log line is still a place a password must never land.</li>
 * </ul>
 *
 * <p>Pure: constructing one has no side effects. Reporting is the caller's job.
 */
record SystemStatusFailure(String code, String sqlState, String detail, boolean ledgerUnreachable) {

    /** Depth cap: a cause/next chain is walked, never followed forever. */
    private static final int MAX_CHAIN = 8;
    /** Length cap per link and overall — a server can raise an arbitrarily long message. */
    private static final int MAX_LINK_CHARS = 300;
    private static final int MAX_DETAIL_CHARS = 1200;

    /**
     * Anything that looks like a credential, in any of the shapes a JDBC/PG failure can echo:
     * {@code password=...} / {@code sslpassword=...} / {@code token=...} / {@code apikey=...} in a
     * property list or query string, and the {@code user:pass@host} userinfo of a URL.
     */
    private static final String SECRET_KEYS = "password|passwd|pwd|sslpassword|token|secret|apikey|api_key";
    /** Bare form: {@code password=hunter2}, {@code token: abc123}. */
    private static final java.util.regex.Pattern SECRET_ASSIGNMENT = java.util.regex.Pattern.compile(
            "(?i)\\b(" + SECRET_KEYS + ")\\b\\s*[=:]\\s*[^\\s,;&)\\]}\"']+");
    /** Quoted/JSON form: {@code "password":"hunter2"}, {@code token='abc'} — the bare pattern stops at the quote. */
    private static final java.util.regex.Pattern SECRET_ASSIGNMENT_QUOTED = java.util.regex.Pattern.compile(
            "(?i)([\"']?)\\b(" + SECRET_KEYS + ")\\b\\1\\s*[=:]\\s*([\"'])(?:\\\\.|(?!\\3).)*\\3");
    private static final java.util.regex.Pattern URL_USERINFO = java.util.regex.Pattern.compile(
            "(?i)([a-z][a-z0-9+.-]*://)[^/@\\s]*@");
    /**
     * Every control character and Unicode line/paragraph separator — not just {@code \n}.
     * {@code \p{Cntrl}} alone is ASCII-only in Java, so U+0085 NEL survives it and puts a second line
     * in the log; {@code Cc}+{@code Cf} covers control and format characters, and {@code Zl}/{@code Zp}
     * the U+2028/U+2029 separators.
     */
    private static final java.util.regex.Pattern CONTROL_CHARS = java.util.regex.Pattern.compile(
            "[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}\\u0085]+");

    /**
     * Classify a section failure. {@code extraSecrets} are literal values that must never appear in the
     * detail even when they are not in a recognised {@code key=value} shape — the configured ledger
     * password is passed here, because a driver is free to quote the property value on its own.
     */
    static SystemStatusFailure of(Throwable failure, List<String> extraSecrets) {
        if (failure == null) {
            return new SystemStatusFailure("INTERNAL_ERROR", null, "no exception supplied", false);
        }
        List<Throwable> chain = chain(failure);
        SQLException sql = firstSqlException(chain);
        String sqlState = sql == null ? null : sql.getSQLState();
        return new SystemStatusFailure(classify(sql, sqlState), sqlState, detailOf(chain, extraSecrets),
                unreachable(chain, sqlState));
    }

    /**
     * The closed vocabulary the page may render. Every value here is chosen so an operator can act on it
     * WITHOUT the server message: a timeout means the query is too slow, a privilege error means the
     * reader role lost a grant, an undefined column means the view drifted from the code.
     */
    private static String classify(SQLException failure, String sqlState) {
        // Classified from the SQLException found ANYWHERE in the chain, not from the outermost
        // throwable: a RuntimeException wrapping SQLState 42501 is still a privilege problem in
        // Postgres, and reporting it as INTERNAL_ERROR sends the operator to debug the gateway.
        if (failure == null) {
            return "INTERNAL_ERROR";
        }
        if (sqlState == null || sqlState.isBlank()) {
            return "QUERY_FAILED";
        }
        return switch (sqlState) {
            case "57014" -> "STATEMENT_TIMEOUT";
            case "57P01", "57P02", "57P03" -> "SERVER_SHUTDOWN";
            case "53300" -> "TOO_MANY_CONNECTIONS";
            case "53200", "53100" -> "SERVER_RESOURCE_EXHAUSTED";
            case "28P01", "28000" -> "AUTHENTICATION_FAILED";
            case "42501" -> "INSUFFICIENT_PRIVILEGE";
            case "42703", "42P01", "42883" -> "SCHEMA_MISMATCH";
            case "40001", "40P01" -> "SERIALIZATION_FAILURE";
            default -> sqlState.startsWith("08") ? "CONNECTION_FAILURE" : "QUERY_FAILED";
        };
    }

    /**
     * The first SQLException anywhere in the chain, preferring one that actually carries a SQLState —
     * the outer wrapper often has none, and pgjdbc puts the real one on the next-exception.
     */
    private static SQLException firstSqlException(List<Throwable> chain) {
        SQLException withoutState = null;
        for (Throwable t : chain) {
            if (t instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && !state.isBlank()) {
                    return sql;
                }
                if (withoutState == null) {
                    withoutState = sql;
                }
            }
        }
        return withoutState;
    }

    private static String detailOf(List<Throwable> chain, List<String> extraSecrets) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t : chain) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getSimpleName());
            if (t instanceof SQLException sql && sql.getSQLState() != null && !sql.getSQLState().isBlank()) {
                sb.append('[').append(sql.getSQLState()).append(']');
            }
            String message = t.getMessage();
            if (message != null && !message.isBlank()) {
                // REDACT BEFORE TRUNCATING. Capping first can slice a long secret in half and leave
                // the prefix behind, which is still a secret.
                sb.append(": ").append(cap(redact(message, extraSecrets), MAX_LINK_CHARS));
            }
            if (sb.length() >= MAX_DETAIL_CHARS) {
                break;
            }
        }
        return cap(redact(collapse(sb.toString()), extraSecrets), MAX_DETAIL_CHARS);
    }

    /**
     * Cause AND {@link SQLException#getNextException()}, breadth-first, with IDENTITY-based cycle
     * detection: a self-referencing or mutually-referencing chain is legal and must not hang the thread.
     */
    private static List<Throwable> chain(Throwable failure) {
        List<Throwable> out = new ArrayList<>();
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        java.util.ArrayDeque<Throwable> queue = new java.util.ArrayDeque<>();
        queue.add(failure);
        while (!queue.isEmpty() && out.size() < MAX_CHAIN) {
            Throwable t = queue.poll();
            if (t == null || seen.put(t, Boolean.TRUE) != null) {
                continue;
            }
            out.add(t);
            if (t.getCause() != null) {
                queue.add(t.getCause());
            }
            if (t instanceof SQLException sql && sql.getNextException() != null) {
                queue.add(sql.getNextException());
            }
        }
        return out;
    }

    private static String collapse(String s) {
        return CONTROL_CHARS.matcher(s).replaceAll(" ").trim();
    }

    /** Package-private so the reporting path can sanitise anything else it is about to log. */
    static String redact(String s, List<String> extraSecrets) {
        String out = SECRET_ASSIGNMENT_QUOTED.matcher(s).replaceAll("$2=***");
        out = SECRET_ASSIGNMENT.matcher(out).replaceAll("$1=***");
        out = URL_USERINFO.matcher(out).replaceAll("$1***@");
        if (extraSecrets != null) {
            for (String secret : extraSecrets) {
                // EVERY non-blank configured secret, not only long ones. A short password is still a
                // password; over-redacting a common substring is the cheaper mistake.
                if (secret != null && !secret.isBlank() && out.contains(secret)) {
                    out = out.replace(secret, "***");
                }
            }
        }
        return out;
    }

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * Whether this failure proves the LEDGER ITSELF is unreachable, rather than one query being
     * unhappy. A statement timeout or a missing column says nothing about the next section; a pool
     * that could not hand out a connection, or a connection-class SQLState, says every remaining read
     * will fail the same way. Decided HERE, from the exception type, rather than by sniffing the
     * rendered detail string downstream.
     */
    private static boolean unreachable(List<Throwable> chain, String sqlState) {
        if (sqlState != null && (sqlState.startsWith("08") || "53300".equals(sqlState))) {
            return true;
        }
        for (Throwable t : chain) {
            if (t instanceof java.sql.SQLTransientConnectionException
                    || t instanceof java.sql.SQLNonTransientConnectionException) {
                return true;
            }
        }
        return false;
    }

    /** What the browser is allowed to see. Never {@link #detail()}. */
    Map<String, Object> browserView() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("sqlState", sqlState);
        return out;
    }
}
