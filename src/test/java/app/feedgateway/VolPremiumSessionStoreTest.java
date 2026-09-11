package app.feedgateway;

import static app.feedgateway.VolPremiumFixtures.CANONICAL_READING_SHA256;
import static app.feedgateway.VolPremiumFixtures.CANONICAL_WARNING_SHA256;
import static app.feedgateway.VolPremiumFixtures.FIXTURE_NOW_MS;
import static app.feedgateway.VolPremiumFixtures.MAPPER;
import static app.feedgateway.VolPremiumFixtures.READINGS_TSV_SHA256;
import static app.feedgateway.VolPremiumFixtures.Row;
import static app.feedgateway.VolPremiumFixtures.SESSION;
import static app.feedgateway.VolPremiumFixtures.SESSION_LAST_INSTANT_MS;
import static app.feedgateway.VolPremiumFixtures.SESSION_MIDNIGHT_MS;
import static app.feedgateway.VolPremiumFixtures.WARNINGS_TSV_SHA256;
import static app.feedgateway.VolPremiumFixtures.canonicalReading;
import static app.feedgateway.VolPremiumFixtures.canonicalWarning;
import static app.feedgateway.VolPremiumFixtures.longField;
import static app.feedgateway.VolPremiumFixtures.readingAt;
import static app.feedgateway.VolPremiumFixtures.readings;
import static app.feedgateway.VolPremiumFixtures.readingsTsvBytes;
import static app.feedgateway.VolPremiumFixtures.resource;
import static app.feedgateway.VolPremiumFixtures.sha256;
import static app.feedgateway.VolPremiumFixtures.warnings;
import static app.feedgateway.VolPremiumFixtures.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.VolPremiumSessionStore.Admission;
import app.feedgateway.VolPremiumSessionStore.Item;
import app.feedgateway.VolPremiumSessionStore.Position;
import app.feedgateway.VolPremiumSessionStore.Refusal;
import app.feedgateway.VolPremiumSessionStore.Stream;
import com.optionsedge.contracts.volpremium.IvRvReading;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The session store on its own: the declared memory bounds, the one replay order, and the session
 * boundary. The gateway-level behaviour — keys, replacement, epochs, rollover, replay, forwarding —
 * is exercised through the gateway itself in FeedGatewayServiceTest.
 */
class VolPremiumSessionStoreTest {

    private static Admission offer(VolPremiumSessionStore store, Row row, long offset) {
        return store.acceptObservation("DATABENTO", row.key(), 0, offset, row.json(), FIXTURE_NOW_MS);
    }

    private static Admission offerWarning(VolPremiumSessionStore store, Row row, long offset) {
        return store.acceptWarning("DATABENTO", row.key(), 0, offset, row.json(), FIXTURE_NOW_MS);
    }

    /** Everything the store would replay, in order, as the verbatim records. */
    private static List<String> walk(VolPremiumSessionStore store, long nowMs) {
        List<String> out = new ArrayList<>();
        Position at = null;
        for (Item item = store.next(null, nowMs); item != null; item = store.next(at, nowMs)) {
            out.add(item.json());
            at = item.position();
        }
        return out;
    }

    @Test
    void theCommittedFixturesAreTheEngineOutputByteForByte() throws Exception {
        // Pinned by hash, so no later edit to a fixture can make a test pass against a record the
        // engine never produced. The gzip is transport only: the DECOMPRESSED stream is what is pinned.
        assertEquals(CANONICAL_READING_SHA256, sha256(resource("ivrv-reading.canonical.v2.json")));
        assertEquals(CANONICAL_WARNING_SHA256, sha256(resource("early-warning.canonical.v1.json")));
        assertEquals(READINGS_TSV_SHA256, sha256(readingsTsvBytes()));
        assertEquals(WARNINGS_TSV_SHA256, sha256(resource("warnings.tsv")));
        assertEquals(371, readings().size());
        assertEquals(15, warnings().size());
        // The canonical reading IS the stream's observation at its ordinal, and the canonical warning is
        // the stream's first transition: one engine run, not two unrelated specimens.
        assertEquals(canonicalReading().key(), readingAt(7141).key());
        assertEquals(MAPPER.readTree(canonicalReading().json()), MAPPER.readTree(readingAt(7141).json()));
        assertEquals(canonicalWarning().key(), warnings().get(0).key());
        assertEquals(MAPPER.readTree(canonicalWarning().json()), MAPPER.readTree(warnings().get(0).json()));
    }

    @Test
    void theProductionCapsAreSizedFromTheContractNotFromTodaysCadence() {
        // One observation per ordinal of a full 09:30-16:00 session at the contract's SMALLEST cadence.
        // Sized from the producer's current 5 s cadence (4,680) the cap would refuse the afternoon of a
        // real session the day the producer legitimately ran faster.
        assertEquals(23_400_000L, VolPremiumSessionStore.RTH_SESSION_MS);
        assertEquals(Math.ceilDiv(23_400_000L, IvRvReading.MIN_FRAME_CADENCE_MS),
                (long) VolPremiumSessionStore.MAX_OBSERVATIONS_PER_SESSION);
        assertEquals(93_600, VolPremiumSessionStore.MAX_OBSERVATIONS_PER_SESSION);
        assertEquals(VolPremiumSessionStore.MAX_OBSERVATIONS_PER_SESSION,
                VolPremiumSessionStore.MAX_WARNINGS_PER_SESSION);
        assertEquals(8, VolPremiumSessionStore.MAX_SYMBOLS);
    }

    @Test
    void aFullSessionIsRefusedLoudlyAndNothingHeldIsEvictedToMakeRoom() {
        VolPremiumSessionStore store = new VolPremiumSessionStore(10, 10, 8);
        List<Row> rows = readings();
        for (int i = 0; i < 10; i++) {
            assertTrue(offer(store, rows.get(i), i).admitted(), "row " + i + " fits under the cap");
        }

        Admission refused = offer(store, rows.get(10), 10);
        assertEquals(Refusal.SESSION_CAP, refused.refusal(), "the eleventh observation is refused");
        assertEquals(1L, store.refusals(Stream.OBSERVATION, Refusal.SESSION_CAP));
        assertTrue(store.metricsText().contains(
                        "gateway_vol_premium_refused_total{stream=\"ivrv\",reason=\"SESSION_CAP\"} 1\n"),
                "and the refusal is visible outside the JVM; got:\n" + store.metricsText());
        // NEVER silently truncated: what is held is exactly the first ten, unchanged. A store that made
        // room by dropping its oldest point would pass a count check and draw a chart missing 09:30.
        assertEquals(rows.subList(0, 10).stream().map(Row::json).toList(), walk(store, FIXTURE_NOW_MS));
        assertEquals(10, store.heldObservations());

        // A REPLACEMENT of a held position is not growth: it still lands at the cap.
        Row fifth = rows.get(5);
        String replacement = with(fifth.json(), "eventTimeMs", longField(fifth.json(), "eventTimeMs") + 1_000L);
        assertTrue(store.acceptObservation("DATABENTO", fifth.key(), 0, 11, replacement, FIXTURE_NOW_MS)
                .admitted(), "a same-ordinal, same-epoch replacement must still be admitted at the cap");
        assertEquals(10, store.heldObservations());
    }

    @Test
    void theWarningCapRefusesTheSameWay() {
        VolPremiumSessionStore store = new VolPremiumSessionStore(10, 3, 8);
        List<Row> rows = warnings();
        for (int i = 0; i < 3; i++) {
            assertTrue(offerWarning(store, rows.get(i), i).admitted());
        }
        assertEquals(Refusal.SESSION_CAP, offerWarning(store, rows.get(3), 3).refusal());
        assertEquals(1L, store.refusals(Stream.WARNING, Refusal.SESSION_CAP));
        assertEquals(3, store.heldWarnings());
    }

    @Test
    void aSeriesBeyondTheSymbolCapIsRefusedAndNeverDisplacesAHeldOne() {
        VolPremiumSessionStore store = new VolPremiumSessionStore(10, 10, 1);
        Row spx = readings().get(0);
        assertTrue(offer(store, spx, 0).admitted());

        String ndx = with(spx.json(), "symbol", "NDX");
        Admission refused = store.acceptObservation("DATABENTO", "NDX|" + SESSION + "|6840", 0, 1, ndx,
                FIXTURE_NOW_MS);
        assertEquals(Refusal.SYMBOL_CAP, refused.refusal());
        assertEquals(List.of(spx.json()), walk(store, FIXTURE_NOW_MS), "the held series is untouched");
    }

    @Test
    void aSecondPartitionForTheSameSessionIsRefusedNeverInterleaved() {
        // Fail closed, never reorder: the topic is single-partition by construction, and one session's
        // records arriving from two partitions have no order a replay could deliver them in.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        assertTrue(offer(store, readings().get(0), 0).admitted());
        Row second = readings().get(1);
        Admission refused = store.acceptObservation("DATABENTO", second.key(), 1, 0, second.json(), FIXTURE_NOW_MS);
        assertEquals(Refusal.FOREIGN_PARTITION, refused.refusal());
        assertEquals(1, store.heldObservations());
    }

    @Test
    void theReplayOrderIsEachSeriesObservationsByOrdinalThenItsWarnings() {
        // Offered in the WORST order — warnings first, observations newest first — so the order that
        // comes out can only be the store's own.
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        long offset = 0;
        for (Row w : warnings()) {
            assertTrue(offerWarning(store, w, offset++).admitted(), w.key());
        }
        List<Row> reversed = new ArrayList<>(readings());
        java.util.Collections.reverse(reversed);
        for (Row r : reversed) {
            assertTrue(offer(store, r, offset++).admitted(), r.key());
        }

        List<String> expected = new ArrayList<>(readings().stream().map(Row::json).toList());
        List<Row> byTransition = new ArrayList<>(warnings());
        byTransition.sort(Comparator.<Row>comparingLong(w -> longField(w.json(), "frameSeq"))
                .thenComparing(Row::key));
        expected.addAll(byTransition.stream().map(Row::json).toList());
        assertEquals(expected, walk(store, FIXTURE_NOW_MS));
    }

    @Test
    void aSessionIsCurrentFromItsMidnightUntilTheContractsAfterHoursAllowance() {
        // The contract's own rule (IvRvReading.requireInstantInSession), so the gateway holds a session
        // exactly as long as a record could still legitimately be filed under it.
        assertFalse(VolPremiumSessionStore.sessionCurrent(SESSION, SESSION_MIDNIGHT_MS - 1L));
        assertTrue(VolPremiumSessionStore.sessionCurrent(SESSION, SESSION_MIDNIGHT_MS));
        assertTrue(VolPremiumSessionStore.sessionCurrent(SESSION, SESSION_LAST_INSTANT_MS));
        assertFalse(VolPremiumSessionStore.sessionCurrent(SESSION, SESSION_LAST_INSTANT_MS + 1L));
    }

    @Test
    void anEndedSessionIsNeitherReplayedNorServedAndIsPurged() {
        VolPremiumSessionStore store = new VolPremiumSessionStore();
        assertTrue(offer(store, readings().get(0), 0).admitted());

        assertEquals(0, store.purge(SESSION_LAST_INSTANT_MS), "still current at its last instant");
        assertEquals(1, walk(store, SESSION_LAST_INSTANT_MS).size());

        // Ended but not yet purged: the readers apply the same rule themselves, so a purge that has not
        // run yet can never let a stale session through.
        assertTrue(walk(store, SESSION_LAST_INSTANT_MS + 1L).isEmpty());
        assertNull(store.snapshot("DATABENTO|SPX", SESSION_LAST_INSTANT_MS + 1L).sessionDate());

        assertEquals(1, store.purge(SESSION_LAST_INSTANT_MS + 1L));
        assertEquals(0, store.heldObservations());
        assertEquals(0L, store.heldBytes());
    }
}
