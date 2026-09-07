package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Where {@link GatewaySettings#marketCalendar()} sources its holiday list from.
 *
 * <p>Regression cover for the 2026-09-07 (Labor Day) es4 outage: the config map defined only
 * {@code HPSF_MARKET_HOLIDAYS}, the gateway read only {@code GATEWAY_MARKET_HOLIDAYS}, so its calendar was
 * empty, the AUTO expiry resolved to the holiday, and every record the feed published for the real next
 * expiry was dropped as inactive.
 */
class GatewayCalendarConfigTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final String HOLIDAYS_2026 =
            "2026-01-01,2026-01-19,2026-02-16,2026-04-03,2026-05-25,2026-06-19,2026-07-03,2026-09-07,"
                    + "2026-11-26,2026-12-25";

    private final Map<String, String> saved = new LinkedHashMap<>();

    /**
     * {@link GatewaySettings#value} reads {@code System.getenv} BEFORE the system property, so a real
     * environment variable cannot be cleared or overridden from a test. Anyone who has sourced the
     * deployment env (both es4-base-env and options-edge-config export {@code HPSF_MARKET_*}) before
     * running the suite would otherwise get a confusing red here rather than a skip.
     */
    private void set(String key, String value) {
        Assumptions.assumeTrue(System.getenv(key) == null,
                () -> key + " is set in the real environment; this test can only control system properties");
        saved.putIfAbsent(key, System.getProperty(key));
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @AfterEach
    void restore() {
        saved.forEach((k, v) -> {
            if (v == null) {
                System.clearProperty(k);
            } else {
                System.setProperty(k, v);
            }
        });
        saved.clear();
    }

    private static Instant et(int y, int m, int d, int hh, int mm) {
        return ZonedDateTime.of(y, m, d, hh, mm, 0, 0, ET).toInstant();
    }

    @Test
    void gatewayKeySuppliesTheCalendar() {
        set("GATEWAY_MARKET_HOLIDAYS", HOLIDAYS_2026);
        set("HPSF_MARKET_HOLIDAYS", null);
        assertFalse(new GatewaySettings().marketCalendar().isTradingDay(LocalDate.of(2026, 9, 7)));
    }

    @Test
    void hpsfKeySuppliesTheCalendarWhenTheGatewayKeyIsAbsent() {
        // The es4 deployment shape as it stood on 2026-09-07: shared HPSF key set, gateway key never added.
        set("GATEWAY_MARKET_HOLIDAYS", null);
        set("HPSF_MARKET_HOLIDAYS", HOLIDAYS_2026);
        GatewayMarketCalendar calendar = new GatewaySettings().marketCalendar();
        assertEquals(10, calendar.holidayCount(), "the shared holiday list is picked up verbatim");
        assertFalse(calendar.isTradingDay(LocalDate.of(2026, 9, 7)), "Labor Day is not a trading day");
        assertTrue(calendar.isTradingDay(LocalDate.of(2026, 9, 8)), "the day after is");
    }

    @Test
    void theGatewayKeyWinsWhenBothAreSet() {
        set("GATEWAY_MARKET_HOLIDAYS", "2026-09-08");
        set("HPSF_MARKET_HOLIDAYS", HOLIDAYS_2026);
        GatewayMarketCalendar calendar = new GatewaySettings().marketCalendar();
        assertEquals(1, calendar.holidayCount(), "the gateway-specific override replaces, never merges");
        assertFalse(calendar.isTradingDay(LocalDate.of(2026, 9, 8)));
        assertTrue(calendar.isTradingDay(LocalDate.of(2026, 9, 7)));
    }

    @Test
    void earlyClosesFallBackToTheSharedKeyToo() {
        set("GATEWAY_MARKET_EARLY_CLOSES", null);
        // The shared config map writes seconds (HH:mm:ss); the gateway's own key writes HH:mm. Both parse.
        set("HPSF_MARKET_EARLY_CLOSES", "2026-11-27=13:00:00,2026-12-24=13:00:00");
        set("HPSF_MARKET_HOLIDAYS", HOLIDAYS_2026);
        GatewayMarketCalendar calendar = new GatewaySettings().marketCalendar();
        assertTrue(calendar.isRegularTradingHours(et(2026, 11, 27, 12, 59)), "before the early close is RTH");
        assertFalse(calendar.isRegularTradingHours(et(2026, 11, 27, 13, 0)), "13:00 ET half-day close");
        assertEquals(LocalTime.of(13, 0),
                LocalTime.ofInstant(calendar.sessionClose(LocalDate.of(2026, 11, 27)), ET));
    }

    /**
     * The outage itself: with the ES roll-after in force on the evening of Labor Day, the AUTO expiry must
     * resolve to 2026-09-08 (the date the feed publishes), not to the holiday the gateway was locked to.
     */
    @Test
    void autoExpirySkipsTheHolidayWhenOnlyTheSharedKeyIsSet() {
        set("GATEWAY_MARKET_HOLIDAYS", null);
        set("HPSF_MARKET_HOLIDAYS", HOLIDAYS_2026);
        GatewayMarketCalendar calendar = new GatewaySettings().marketCalendar();
        // 2026-09-07 is a Monday (Labor Day); 2026-09-04 the preceding Friday.
        assertEquals(LocalDate.of(2026, 9, 8),
                calendar.currentTradingDate(et(2026, 9, 7, 18, 0), LocalTime.of(16, 0)),
                "ES roll-after: past 16:00 ET the next TRADING day, skipping the holiday");
        assertEquals(LocalDate.of(2026, 9, 4),
                calendar.currentTradingDate(et(2026, 9, 7, 10, 0)),
                "legacy midnight roll: a holiday walks BACK to the previous trading day, never onto itself");
    }

    /**
     * A typo'd gateway override yields zero usable holidays. It must NOT count as "configured" and shadow
     * the shared list — that would silently reproduce the outage with the override sitting right there.
     */
    @Test
    void anUnparseableGatewayListFallsBackToTheSharedOne() {
        set("GATEWAY_MARKET_HOLIDAYS", "2026/09/07,2026-09-07T00:00");
        set("HPSF_MARKET_HOLIDAYS", HOLIDAYS_2026);
        GatewayMarketCalendar calendar = new GatewaySettings().marketCalendar();
        assertEquals(10, calendar.holidayCount(), "no entry parsed, so the shared list still supplies it");
        assertFalse(calendar.isTradingDay(LocalDate.of(2026, 9, 7)));
    }

    /** A malformed TIME must drop the entry, never quietly restore the half-day to a full session. */
    @Test
    void aMalformedEarlyCloseTimeIsDroppedNotSilentlyWidened() {
        set("GATEWAY_MARKET_HOLIDAYS", HOLIDAYS_2026);
        set("GATEWAY_MARKET_EARLY_CLOSES", "2026-11-27=1:00 PM,2026-12-24=13:00");
        GatewayMarketCalendar calendar = new GatewaySettings().marketCalendar();
        assertEquals(LocalTime.of(16, 0),
                LocalTime.ofInstant(calendar.sessionClose(LocalDate.of(2026, 11, 27)), ET),
                "unparseable time: the entry is skipped, so the regular close stands");
        assertEquals(LocalTime.of(13, 0),
                LocalTime.ofInstant(calendar.sessionClose(LocalDate.of(2026, 12, 24)), ET),
                "the well-formed sibling entry is unaffected");
    }

    @Test
    void noHolidaysAtAllStillDegradesToWeekdayOnly() {
        set("GATEWAY_MARKET_HOLIDAYS", null);
        set("HPSF_MARKET_HOLIDAYS", null);
        GatewayMarketCalendar calendar = new GatewaySettings().marketCalendar();
        assertEquals(0, calendar.holidayCount());
        assertTrue(calendar.isTradingDay(LocalDate.of(2026, 9, 7)), "unconfigured: the holiday looks like a session");
        assertFalse(calendar.isTradingDay(LocalDate.of(2026, 9, 5)), "Saturday is still not a session");
    }
}
