package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreopenSourceTest {

    private static final double SPOT = 7418.4638671875;

    private static String record(String expiry, double strike, double netGex, String updatedAt) {
        return "{\"symbol\":\"SPX\",\"expiry\":\"" + expiry + "\",\"strike\":" + strike
                + ",\"netGex\":" + netGex + ",\"gammaSign\":\"POSITIVE\",\"canonicalSpot\":" + SPOT
                + ",\"updatedAt\":\"" + updatedAt + "\",\"staleAfterMs\":300000}";
    }

    @Test
    @DisplayName("the newest value for a strike wins, whatever order the log delivers it in")
    void newestWinsRegardlessOfArrivalOrder() {
        PreopenSource s = new PreopenSource();
        s.observe(record("20261119", 7420.0, 1.0E8, "2026-11-19T11:43:02.906Z"));
        s.observe(record("20261119", 7420.0, 5.0E8, "2026-11-19T11:48:02.906Z"));
        s.observe(record("20261119", 7420.0, 2.0E8, "2026-11-19T11:45:02.906Z"));

        assertEquals(1, s.strikesHeld(), "one strike, three values");
        assertEquals(3, s.observed());
        ReadablePage.Board b = s.board(1_795_088_882_906L);
        String spotRow = b.lines().stream().filter(l -> l.endsWith("|spot")).findFirst().orElseThrow();
        assertTrue(spotRow.contains("+500M"),
                "a delete-only log can redeliver out of order after a restart, so the newest "
                        + "instant must win rather than the last to arrive: " + spotRow);
    }

    @Test
    @DisplayName("an unreadable record is counted and never thrown")
    void unreadableRecordsAreCounted() {
        PreopenSource s = new PreopenSource();
        s.observe(null);
        s.observe("");
        s.observe("not json");
        s.observe("{\"symbol\":\"ES\",\"expiry\":\"20261119\",\"strike\":7420.0,"
                + "\"canonicalSpot\":1.0,\"updatedAt\":\"2026-11-19T11:43:02.906Z\"}");
        assertEquals(0, s.strikesHeld());
        assertEquals(0, s.observed());
        assertEquals(4, s.unreadable(), "every refusal is counted, including the ES record");
        assertNull(s.board(1_795_088_882_906L));
    }

    @Test
    @DisplayName("yesterday's board is pruned when today's records arrive, by the data not the clock")
    void anOlderExpiryIsPruned() {
        PreopenSource s = new PreopenSource();
        s.observe(record("20261118", 7400.0, 1.0E8, "2026-11-18T11:43:02.906Z"));
        s.observe(record("20261118", 7405.0, 1.0E8, "2026-11-18T11:43:02.906Z"));
        assertEquals(2, s.strikesHeld());

        s.observe(record("20261119", 7420.0, 3.0E8, "2026-11-19T11:43:02.906Z"));
        assertEquals(1, s.strikesHeld(), "the previous session's strikes are gone");
        assertEquals(2, s.prunedForOlderExpiry());

        ReadablePage.Board b = s.board(1_795_088_582_906L + 1_000L);
        assertNotNull(b);
        assertEquals(1, b.rowsPresent(), "only today's strike remains on the board");
    }

    @Test
    @DisplayName("the ceiling refuses and counts rather than evicting a strike off the board")
    void theCeilingIsCountedNotSilent() {
        PreopenSource s = new PreopenSource();
        for (int i = 0; i < PreopenSource.MAX_STRIKES; i++) {
            s.observe(record("20261119", 1000.0 + i * 5.0, 1.0E8, "2026-11-19T11:43:02.906Z"));
        }
        assertEquals(PreopenSource.MAX_STRIKES, s.strikesHeld());
        assertEquals(0, s.refusedAtCap());

        s.observe(record("20261119", 99_000.0, 1.0E8, "2026-11-19T11:43:02.906Z"));
        assertEquals(PreopenSource.MAX_STRIKES, s.strikesHeld(), "nothing already held is evicted");
        assertEquals(1, s.refusedAtCap(), "the refusal is visible rather than silent");

        // A new value for a strike already held is still taken at the ceiling.
        s.observe(record("20261119", 1000.0, 9.0E8, "2026-11-19T11:50:02.906Z"));
        assertEquals(1, s.refusedAtCap(), "an update is not a new key and is not refused");
    }
}
