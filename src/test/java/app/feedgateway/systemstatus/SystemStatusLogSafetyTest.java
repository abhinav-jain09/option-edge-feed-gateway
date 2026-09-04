package app.feedgateway.systemstatus;

import app.feedgateway.GatewaySettings;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Against the ACTUAL emitted log text, because the previous attempt at this passed a sanitized string
 * to SLF4J and then attached the raw {@link Throwable} as the trailing argument — so logback printed
 * the original unredacted, unbounded, multi-line message and every cause anyway. Asserting on the
 * sanitizer's return value alone would not have caught that.
 */
class SystemStatusLogSafetyTest {

    private static final String PASSWORD = "LiteralPw99";

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SystemStatusController.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        System.clearProperty("OE_WATCH_DB_PASSWORD");
    }

    private String emittedLog() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            sb.append(event.getFormattedMessage()).append('\n');
            if (event.getThrowableProxy() != null) {
                sb.append("THROWABLE_ATTACHED:").append(event.getThrowableProxy().getMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void nothingSecretOrMultilineReachesTheLog() throws Exception {
        System.setProperty("OE_WATCH_DB_PASSWORD", PASSWORD);
        SQLException detail = new SQLException(
                "ERROR: permission denied for table events\r\n  Where: oe_watch.v_topic_state\n"
                        + "  connection jdbc:postgresql://oe_watch_reader:" + PASSWORD
                        + "@192.168.100.252:5432/options_flow password=hunter2 token=\"abc123\"",
                "42501");
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) throws SQLException {
                throw detail;
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env, int t) {
                return List.of();
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env, int t) {
                return Map.of();
            }

            @Override
            public Map<String, Object> lastRun(String env, int t) {
                return new LinkedHashMap<>();
            }
        };
        new SystemStatusController(new GatewaySettings(), store,
                new ConsumerLagReader("", List.of(), 30_000L, 5_000), new ObjectMapper()).systemStatus();

        String log = emittedLog();
        assertTrue(log.contains("INSUFFICIENT_PRIVILEGE"), "the classified code must be logged: " + log);
        assertFalse(log.contains("THROWABLE_ATTACHED"),
                "attaching the raw throwable makes logback print the unredacted original: " + log);
        assertFalse(log.contains(PASSWORD), "the configured ledger password leaked: " + log);
        assertFalse(log.contains("hunter2"), "an assigned password leaked: " + log);
        assertFalse(log.contains("abc123"), "a quoted token leaked: " + log);
        for (String line : log.split("\n")) {
            assertFalse(line.contains("\r"), "a log line must stay one line: " + line);
        }
    }

    /** An outage repeats every refresh; four full traces per request is how a log stops being read. */
    @Test
    void repeatedFailuresDoNotFloodTheLog() throws Exception {
        SystemStatusStore store = new SystemStatusStore(null, 3) {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public List<Map<String, Object>> topics(String env, int t) {
                throw new IllegalStateException("boom");
            }

            @Override
            public List<Map<String, Object>> openIncidents(String env, int t) {
                throw new IllegalStateException("boom");
            }

            @Override
            public Map<String, Map<String, Object>> restarts(String env, int t) {
                throw new IllegalStateException("boom");
            }

            @Override
            public Map<String, Object> lastRun(String env, int t) {
                throw new IllegalStateException("boom");
            }
        };
        System.setProperty("OE_SYSTEM_STATUS_CACHE_MS", "1000");
        try {
            SystemStatusController c = new SystemStatusController(new GatewaySettings(), store,
                    new ConsumerLagReader("", List.of(), 30_000L, 5_000), new ObjectMapper());
            c.systemStatus();
            long origins = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("failure origin"))
                    .count();
            assertTrue(origins <= 1,
                    "at most one stack origin per interval ACROSS all four sections, saw " + origins);
        } finally {
            System.clearProperty("OE_SYSTEM_STATUS_CACHE_MS");
        }
    }
}
