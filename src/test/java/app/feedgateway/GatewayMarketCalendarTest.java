package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GatewayMarketCalendarTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");

    private static GatewayMarketCalendar calendar(Set<LocalDate> holidays, Map<LocalDate, LocalTime> earlyCloses) {
        return new GatewayMarketCalendar(ET, LocalTime.of(9, 30), LocalTime.of(16, 0), holidays, earlyCloses);
    }

    private static java.time.Instant et(int y, int m, int d, int hh, int mm) {
        return ZonedDateTime.of(y, m, d, hh, mm, 0, 0, ET).toInstant();
    }

    @Test
    void regularWeekdaySessionIsRth() {
        GatewayMarketCalendar c = calendar(Set.of(), Map.of());
        // 2026-06-23 is a Tuesday.
        assertFalse(c.isRegularTradingHours(et(2026, 6, 23, 9, 29)), "before 09:30 ET is off-hours");
        assertTrue(c.isRegularTradingHours(et(2026, 6, 23, 9, 30)), "09:30 ET open");
        assertTrue(c.isRegularTradingHours(et(2026, 6, 23, 12, 0)), "midday is RTH");
        assertTrue(c.isRegularTradingHours(et(2026, 6, 23, 15, 59)), "15:59 ET still RTH");
        assertFalse(c.isRegularTradingHours(et(2026, 6, 23, 16, 0)), "16:00 ET close is off-hours");
        assertFalse(c.isRegularTradingHours(et(2026, 6, 23, 20, 0)), "evening is off-hours");
    }

    @Test
    void weekendsAreOffHours() {
        GatewayMarketCalendar c = calendar(Set.of(), Map.of());
        assertFalse(c.isRegularTradingHours(et(2026, 6, 20, 12, 0)), "Saturday is off-hours");
        assertFalse(c.isRegularTradingHours(et(2026, 6, 21, 12, 0)), "Sunday is off-hours");
    }

    @Test
    void configuredHolidayIsOffHoursAllDay() {
        // 2026-07-03 (observed Independence Day) as a holiday — even midday is off-hours.
        GatewayMarketCalendar c = calendar(Set.of(LocalDate.of(2026, 7, 3)), Map.of());
        assertFalse(c.isRegularTradingHours(et(2026, 7, 3, 12, 0)), "holiday midday is off-hours");
        assertFalse(c.isTradingDay(LocalDate.of(2026, 7, 3)));
        assertTrue(c.isTradingDay(LocalDate.of(2026, 7, 2)), "the prior weekday is a trading day");
    }

    @Test
    void earlyCloseShortensTheSession() {
        // Half-day: close at 13:00 instead of 16:00.
        GatewayMarketCalendar c = calendar(Set.of(), Map.of(LocalDate.of(2026, 11, 27), LocalTime.of(13, 0)));
        assertTrue(c.isRegularTradingHours(et(2026, 11, 27, 12, 59)), "before the early close is RTH");
        assertFalse(c.isRegularTradingHours(et(2026, 11, 27, 13, 0)), "at the early close is off-hours");
        assertFalse(c.isRegularTradingHours(et(2026, 11, 27, 15, 0)), "after early close is off-hours");
    }
}
