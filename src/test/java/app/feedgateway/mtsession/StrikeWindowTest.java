package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StrikeWindowTest {

    @Test
    void containsIsInclusive() {
        StrikeWindow w = StrikeWindow.of(7000, 8000);
        assertTrue(w.contains(7000));
        assertTrue(w.contains(8000));
        assertTrue(w.contains(7500));
        assertFalse(w.contains(6999.99));
        assertFalse(w.contains(8000.01));
    }

    @Test
    void allContainsEverything() {
        assertTrue(StrikeWindow.ALL.contains(-1e9));
        assertTrue(StrikeWindow.ALL.contains(1e9));
        assertTrue(StrikeWindow.ALL.contains(0));
    }

    @Test
    void unionWidens() {
        StrikeWindow u = StrikeWindow.of(7000, 7500).union(StrikeWindow.of(7400, 8000));
        assertEquals(7000, u.lo());
        assertEquals(8000, u.hi());
    }

    @Test
    void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> StrikeWindow.of(8000, 7000));
        assertThrows(IllegalArgumentException.class, () -> new StrikeWindow(Double.NaN, 1));
    }
}
