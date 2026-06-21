package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserSessionPolicyTest {

    @Test
    void validPolicy() {
        UserSessionPolicy p = new UserSessionPolicy(15, 480, false);
        assertEquals(15, p.idleTimeoutMinutes());
        assertFalse(p.isUnlimited());
    }

    @Test
    void idleFloorEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new UserSessionPolicy(9, 480, false));
        // boundary: 10 is allowed
        assertEquals(10, new UserSessionPolicy(10, 480, false).idleTimeoutMinutes());
    }

    @Test
    void unlimitedRequiresFlag() {
        assertThrows(IllegalArgumentException.class, () -> new UserSessionPolicy(30, 0, false));
        UserSessionPolicy unlimited = new UserSessionPolicy(30, 0, true);
        assertTrue(unlimited.isUnlimited());
    }

    @Test
    void negativeMaxRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UserSessionPolicy(30, -1, true));
    }

    @Test
    void systemDefault() {
        UserSessionPolicy d = UserSessionPolicy.systemDefault();
        assertEquals(30, d.idleTimeoutMinutes());
        assertEquals(600, d.maxSessionMinutes());
        assertFalse(d.unlimitedAllowed());
    }
}
