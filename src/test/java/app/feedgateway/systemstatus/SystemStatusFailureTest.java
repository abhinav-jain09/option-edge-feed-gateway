package app.feedgateway.systemstatus;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The split between what the browser may see and what only the log may see. Each case here is a way the
 * previous one-line {@code getClass().getSimpleName()} either said nothing useful or would have said
 * too much.
 */
class SystemStatusFailureTest {

    @Test
    void sqlStateBecomesAnActionableCode() {
        assertEquals("STATEMENT_TIMEOUT",
                SystemStatusFailure.of(new SQLException("cancelled", "57014"), List.of()).code());
        assertEquals("AUTHENTICATION_FAILED",
                SystemStatusFailure.of(new SQLException("bad password", "28P01"), List.of()).code());
        assertEquals("SCHEMA_MISMATCH",
                SystemStatusFailure.of(new SQLException("no such column", "42703"), List.of()).code());
        assertEquals("INSUFFICIENT_PRIVILEGE",
                SystemStatusFailure.of(new SQLException("denied", "42501"), List.of()).code());
        assertEquals("CONNECTION_FAILURE",
                SystemStatusFailure.of(new SQLException("refused", "08001"), List.of()).code());
        assertEquals("QUERY_FAILED",
                SystemStatusFailure.of(new SQLException("odd", "XX000"), List.of()).code());
        assertEquals("QUERY_FAILED",
                SystemStatusFailure.of(new SQLException("no state"), List.of()).code());
    }

    /** A gateway bug is not a ledger query failure; calling it one sends the operator to the wrong box. */
    @Test
    void nonSqlFailuresAreInternalErrorsNotQueryFailures() {
        SystemStatusFailure f = SystemStatusFailure.of(new IllegalStateException("boom"), List.of());
        assertEquals("INTERNAL_ERROR", f.code());
        assertNull(f.sqlState());
    }

    /** pgjdbc hides the real cause behind getNextException(); the outer wrapper often has no SQLState. */
    @Test
    void sqlStateAndDetailAreFoundThroughCauseAndNextExceptionChains() {
        SQLException next = new SQLException("ERROR: canceling statement due to user request", "57014");
        SQLException outer = new SQLException("batch failed");
        outer.setNextException(next);
        SystemStatusFailure viaNext = SystemStatusFailure.of(outer, List.of());
        assertEquals("STATEMENT_TIMEOUT", viaNext.code());
        assertEquals("57014", viaNext.sqlState());
        assertTrue(viaNext.detail().contains("canceling statement"));

        SystemStatusFailure viaCause = SystemStatusFailure.of(
                new RuntimeException("wrapped", new SQLException("denied", "42501")), List.of());
        assertEquals("42501", viaCause.sqlState());
    }

    /** A self-referencing chain is legal and must not hang the request thread. */
    @Test
    void cyclicChainsTerminate() {
        SQLException a = new SQLException("a", "57014");
        SQLException b = new SQLException("b", "57014");
        a.setNextException(b);
        b.setNextException(a);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                () -> assertEquals("STATEMENT_TIMEOUT", SystemStatusFailure.of(a, List.of()).code()));
    }

    /** \r, tabs and Unicode separators are line breaks too — a log line must stay ONE line. */
    @Test
    void detailIsSingleLineAndBounded() {
        String nasty = "first\r\nsecond\tthird\u2028fourth\u0085fifth\u0000sixth" + "x".repeat(5_000);
        String detail = SystemStatusFailure.of(new SQLException(nasty, "57014"), List.of()).detail();
        for (String breaker : List.of("\n", "\r", "\t", "\u2028", "\u0085", "\u0000")) {
            assertFalse(detail.contains(breaker),
                    "detail must be a single line, found " + breaker.codePointAt(0) + " in: " + detail);
        }
        assertTrue(detail.length() <= 1_300, "detail must be bounded, was " + detail.length());
        assertTrue(detail.contains("first second"), "content survives the flattening: " + detail);
    }

    /** A log line is still a place a credential must never land. */
    @Test
    void secretsAreRedactedFromTheDetail() {
        SystemStatusFailure f = SystemStatusFailure.of(new SQLException(
                "FATAL: auth failed for password=hunter2, token: abc123 "
                        + "url jdbc:postgresql://oe_watch_reader:LiteralPw99@192.168.100.252:5432/options_flow",
                "28P01"),
                List.of("LiteralPw99"));
        String detail = f.detail();
        assertFalse(detail.contains("hunter2"), detail);
        assertFalse(detail.contains("abc123"), detail);
        assertFalse(detail.contains("LiteralPw99"), detail);
        assertTrue(detail.contains("***"), detail);
    }

    /** The browser view is exactly two safe fields — adding the detail here is the leak this prevents. */
    @Test
    void browserViewCarriesOnlyCodeAndSqlState() {
        var view = SystemStatusFailure.of(new SQLException("schema oe_watch table events", "57014"),
                List.of()).browserView();
        assertEquals(List.of("code", "sqlState"), List.copyOf(view.keySet()));
        assertEquals("STATEMENT_TIMEOUT", view.get("code"));
        assertEquals("57014", view.get("sqlState"));
    }

    @Test
    void aNullFailureStillClassifies() {
        assertEquals("INTERNAL_ERROR", SystemStatusFailure.of(null, List.of()).code());
    }

    /** A RuntimeException wrapping SQLState 42501 is still a PRIVILEGE problem in Postgres. */
    @Test
    void aWrappedSqlFailureIsClassifiedFromTheSqlExceptionNotTheWrapper() {
        SystemStatusFailure f = SystemStatusFailure.of(
                new RuntimeException("pool wrapper", new SQLException("denied", "42501")), List.of());
        assertEquals("INSUFFICIENT_PRIVILEGE", f.code(),
                "classifying the wrapper as INTERNAL_ERROR sends the operator to debug the gateway");
        assertEquals("42501", f.sqlState());
    }

    /** A SQLException with a nonsense state is still a query failure, not an internal error. */
    @Test
    void anUnknownSqlStateIsStillAQueryFailure() {
        assertEquals("QUERY_FAILED",
                SystemStatusFailure.of(new SQLException("odd", "ZZZZZ"), List.of()).code());
        assertEquals("QUERY_FAILED",
                SystemStatusFailure.of(new SQLException("blank state", ""), List.of()).code());
    }

    /** Quoted and JSON assignment forms — the bare pattern stops at the opening quote. */
    @Test
    void quotedAndJsonSecretFormsAreRedacted() {
        String detail = SystemStatusFailure.of(new SQLException(
                "{\"password\":\"hunter2\", \"token\": \"abc123\"} sslpassword='pk-9' ok", "28P01"),
                List.of()).detail();
        assertFalse(detail.contains("hunter2"), detail);
        assertFalse(detail.contains("abc123"), detail);
        assertFalse(detail.contains("pk-9"), detail);
    }

    /** A short configured password is still a password; it must not be exempt from redaction. */
    @Test
    void shortConfiguredSecretsAreRedactedToo() {
        String detail = SystemStatusFailure.of(new SQLException("value was Ab1 here", "28P01"),
                List.of("Ab1")).detail();
        assertFalse(detail.contains("Ab1"), detail);
    }

    /**
     * Redaction happens BEFORE truncation. Capping first slices a long secret in half and leaves the
     * prefix behind — which is still a secret, and still enough to be dangerous.
     */
    @Test
    void aLongSecretIsRedactedBeforeTheMessageIsTruncated() {
        String secret = "S3cretValue" + "9".repeat(400);
        String detail = SystemStatusFailure.of(
                new SQLException("prefix " + secret + " suffix", "28P01"), List.of(secret)).detail();
        assertFalse(detail.contains("S3cretValue"),
                "not even the surviving PREFIX of the secret may remain: " + detail);
    }
}
