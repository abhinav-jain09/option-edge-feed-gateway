package app.feedgateway;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Exchange-session calendar for the US options market (OPRA/NYSE regular hours). Pure and side-effect free:
 * {@link #isRegularTradingHours(Instant)} is a function of the supplied instant plus the configured zone,
 * regular open/close, holidays, and early-close (half-day) overrides — so it is fully unit-testable with
 * fixed instants and needs no wall clock.
 *
 * <p>It drives market-aware cache freshness in {@link FeedGatewayService}: the option-chain cache uses a
 * short freshness TTL during regular trading hours and is NEVER evicted off-hours (so the published strike
 * structure stays visible overnight / on weekends / on holidays, when quotes legitimately do not tick).
 *
 * <p>Holidays and early closes are operator-supplied (annual update from the OPRA/NYSE schedule). When the
 * holiday set is empty the calendar degrades to weekday + regular-hours only — still correct on weekends and
 * outside RTH, but it would treat a market holiday as a normal session; callers should configure holidays
 * for full correctness (see {@link GatewaySettings#marketCalendar()}, which warns when none are set).
 */
public final class GatewayMarketCalendar {

    private final ZoneId zone;
    private final LocalTime regularOpen;
    private final LocalTime regularClose;
    private final Set<LocalDate> holidays;
    private final Map<LocalDate, LocalTime> earlyCloses;

    public GatewayMarketCalendar(ZoneId zone, LocalTime regularOpen, LocalTime regularClose,
                                 Set<LocalDate> holidays, Map<LocalDate, LocalTime> earlyCloses) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.regularOpen = Objects.requireNonNull(regularOpen, "regularOpen");
        this.regularClose = Objects.requireNonNull(regularClose, "regularClose");
        this.holidays = Set.copyOf(Objects.requireNonNull(holidays, "holidays"));
        this.earlyCloses = Map.copyOf(Objects.requireNonNull(earlyCloses, "earlyCloses"));
        if (!regularOpen.isBefore(regularClose)) {
            throw new IllegalArgumentException("regularOpen must be before regularClose");
        }
    }

    /** True only on a trading day (weekday, not a holiday) and within [open, close) in the market zone. */
    public boolean isRegularTradingHours(Instant instant) {
        ZonedDateTime t = Objects.requireNonNull(instant, "instant").atZone(zone);
        if (!isTradingDay(t.toLocalDate())) {
            return false;
        }
        LocalTime close = earlyCloses.getOrDefault(t.toLocalDate(), regularClose);
        LocalTime now = t.toLocalTime();
        // [open, close): at exactly the close instant the session is over.
        return !now.isBefore(regularOpen) && now.isBefore(close);
    }

    /** A weekday that is not a configured market holiday. */
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }

    public int holidayCount() {
        return holidays.size();
    }
}
