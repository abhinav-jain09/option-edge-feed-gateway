package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertThrows;

import app.feedgateway.mtsession.CapacityException;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The operator's GATEWAY_MAX_* values reach the engine that enforces them; unset keeps the default of 4. */
class MtSessionAuthConfigLimitsTest {

    private static final Selection SPX =
            new Selection(MarketDataSource.DATABENTO, "SPX", "20260617", StrikeWindow.ALL);

    private static SessionRoutingEngine registered(SessionRoutingEngine e) {
        e.registerAppSession("app:u1", "u1", SPX, Set.of());
        return e;
    }

    @Test
    void aConfiguredSocketLimitIsTheOneEnforced() {
        SessionRoutingEngine e = registered(new MtSessionAuthConfig().sessionRoutingEngine(null, 6, null));
        for (int i = 1; i <= 6; i++) {
            e.attachSocket("app:u1", "s" + i);
        }
        assertThrows(CapacityException.class, () -> e.attachSocket("app:u1", "s7"));
    }

    @Test
    void unsetKeepsTheDefaultOfFour() {
        SessionRoutingEngine e = registered(new MtSessionAuthConfig().sessionRoutingEngine(null, null, null));
        for (int i = 1; i <= 4; i++) {
            e.attachSocket("app:u1", "s" + i);
        }
        assertThrows(CapacityException.class, () -> e.attachSocket("app:u1", "s5"));
    }
}
