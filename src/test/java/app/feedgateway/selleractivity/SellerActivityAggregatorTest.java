package app.feedgateway.selleractivity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity with the former client-side option-chain.js seller-activity aggregation
 * (its node:test values in option-chain-seller-activity.test.js), plus adversarial edges: the same
 * inputs must produce the same counts, so moving the logic server-side shifts NOTHING for the UI.
 */
class SellerActivityAggregatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SellerActivityAggregator aggregator = new SellerActivityAggregator(mapper);

    /** {bucketMinutes:5, points:[{0,1/1/0},{5m,2/0/2},{10m,3/2/1},{15m,4/3/1}]} — mirrors the JS test. */
    private JsonNode fiveMinuteActivity() {
        ObjectNode activity = mapper.createObjectNode();
        activity.put("bucketMinutes", 5);
        ArrayNode points = activity.putArray("points");
        point(points, 0L, 1, 1, 0);
        point(points, 5 * 60_000L, 2, 0, 2);
        point(points, 10 * 60_000L, 3, 2, 1);
        point(points, 15 * 60_000L, 4, 3, 1);
        return activity;
    }

    private void point(ArrayNode arr, long t, long sell, long call, long put) {
        ObjectNode p = arr.addObject();
        p.put("timestampMs", t);
        p.put("sellTradeCount", sell);
        p.put("callSellTradeCount", call);
        p.put("putSellTradeCount", put);
    }

    private List<Long> counts(JsonNode activity, int sample, String mode) {
        List<Long> out = new ArrayList<>();
        for (long[] p : aggregator.aggregatePoints(activity, sample, mode)) {
            out.add(p[1]);
        }
        return out;
    }

    @Test
    void combinesExactCountsIntoEveryRequestedSampleSize() {
        JsonNode a = fiveMinuteActivity();
        assertEquals(List.of(1L, 2L, 3L, 4L), counts(a, 5, "combined"));
        assertEquals(List.of(3L, 7L), counts(a, 10, "combined"));
        assertEquals(List.of(6L, 4L), counts(a, 15, "combined"));
        assertEquals(List.of(10L), counts(a, 30, "combined"));
        assertEquals(List.of(10L), counts(a, 60, "combined"));
        assertEquals(List.of(10L), counts(a, 240, "combined"));
        assertEquals(List.of(1L, 5L), counts(a, 10, "call"));
        assertEquals(List.of(2L, 2L), counts(a, 10, "put"));
        // Session = cumulative running total.
        assertEquals(List.of(1L, 3L, 6L, 10L), counts(a, SellerActivityAggregator.SESSION_MINUTES, "combined"));
        assertEquals(List.of(1L, 1L, 3L, 6L), counts(a, SellerActivityAggregator.SESSION_MINUTES, "call"));
    }

    @Test
    void preservesOneMinuteBucketsWithoutInterpolation() {
        ObjectNode activity = mapper.createObjectNode();
        activity.put("bucketMinutes", 1);
        ArrayNode points = activity.putArray("points");
        point(points, 0L, 2, 1, 1);
        point(points, 60_000L, 4, 3, 1);
        assertEquals(List.of(2L, 4L), counts(activity, 1, "combined"));
    }

    @Test
    void leaderFollowsTheLatestVisibleBucketNotTheHistoricalTotal() {
        List<SellerActivityAggregator.StrikeSeries> series = List.of(
                new SellerActivityAggregator.StrikeSeries(7515, List.of(new long[]{1, 20}, new long[]{2, 1})),
                new SellerActivityAggregator.StrikeSeries(7520, List.of(new long[]{1, 2}, new long[]{2, 7})));
        assertEquals(7520.0, SellerActivityAggregator.leader(series, false));
    }

    @Test
    void sessionLeaderCarriesTheLastCumulativeValueForwardInsteadOfScoringItZero() {
        // Session points are cumulative and sparse: 7515 holds a running total of 100 but last printed
        // at t=2, while 7520 printed 2 most recently at t=3. The leader is the strike HOLDING the most,
        // not the one that printed last -- the exact-bucket rule would score 7515 at zero and invert it.
        List<SellerActivityAggregator.StrikeSeries> sparse = List.of(
                new SellerActivityAggregator.StrikeSeries(7515, List.of(new long[]{1, 40}, new long[]{2, 100})),
                new SellerActivityAggregator.StrikeSeries(7520, List.of(new long[]{3, 2})));
        assertEquals(7515.0, SellerActivityAggregator.leader(sparse, true));
        // Bucketed samples keep the opposite rule on the same data: absent from the latest bucket == 0.
        assertEquals(7520.0, SellerActivityAggregator.leader(sparse, false));
        // Unordered points and duplicate timestamps resolve to the later element, as the web does.
        List<SellerActivityAggregator.StrikeSeries> messy = List.of(
                new SellerActivityAggregator.StrikeSeries(7515, List.of(new long[]{2, 9}, new long[]{1, 4}, new long[]{2, 11})),
                new SellerActivityAggregator.StrikeSeries(7520, List.of(new long[]{2, 10})));
        assertEquals(7515.0, SellerActivityAggregator.leader(messy, true));
    }

    @Test
    void sessionEnvelopeNamesTheHoldingLeaderNotTheMostRecentPrinter() {
        // Same shape end to end: a Session (1440) envelope built from a real snapshot must name the
        // strike holding the largest cumulative total even though it did not print at the latest minute.
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("timestampMs", 1_800_000L);
        ArrayNode strikes = snapshot.putArray("strikes");

        ObjectNode holder = strikes.addObject();
        holder.put("strike", 7515);
        ObjectNode holderActivity = holder.putObject("sellerActivity");
        holderActivity.put("bucketMinutes", 1);
        ArrayNode holderPoints = holderActivity.putArray("points");
        point(holderPoints, 0L, 40, 20, 20);
        point(holderPoints, 60_000L, 60, 30, 30);   // cumulative 100 by t=1min, then silent

        ObjectNode recent = strikes.addObject();
        recent.put("strike", 7520);
        ObjectNode recentActivity = recent.putObject("sellerActivity");
        recentActivity.put("bucketMinutes", 1);
        ArrayNode recentPoints = recentActivity.putArray("points");
        // 30 minutes later: a DIFFERENT 30m bucket, so the bucketed and Session answers can differ.
        point(recentPoints, 1_800_000L, 2, 1, 1);   // printed most recently, total of 2

        ObjectNode env = aggregator.aggregate(snapshot.toString(), "SPX", "2026-08-26",
                SellerActivityAggregator.SESSION_MINUTES, "combined");
        assertEquals(7515.0, env.path("leaderStrike").asDouble());

        ObjectNode bucketed = aggregator.aggregate(snapshot.toString(), "SPX", "2026-08-26", 30, "combined");
        assertEquals(7520.0, bucketed.path("leaderStrike").asDouble());
    }

    @Test
    void nullOrEmptySnapshotYieldsAValidEmptyEnvelope() {
        for (String snap : new String[]{null, "", "   ", "not json"}) {
            ObjectNode env = aggregator.aggregate(snap, "SPX", "2026-07-24", 30, "combined");
            assertEquals("SPX", env.path("symbol").asText());
            assertEquals(30, env.path("sampleMinutes").asInt());
            assertEquals(0L, env.path("asOfMs").asLong());
            assertTrue(env.path("leaderStrike").isNull());
            assertEquals(0, env.path("series").size());
        }
    }

    @Test
    void aggregatesAFullSnapshotAcrossStrikesWithLeaderAndAsOf() {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("symbol", "SPX");
        snap.put("expiry", "20260724");
        snap.put("timestampMs", 1_780_000_500_000L);
        ArrayNode strikes = snap.putArray("strikes");

        ObjectNode s1 = strikes.addObject();
        s1.put("strike", 7515.0);
        ObjectNode a1 = s1.putObject("sellerActivity");
        a1.put("bucketMinutes", 1);
        point(a1.putArray("points"), 60_000L, 5, 3, 2);

        ObjectNode s2 = strikes.addObject();
        s2.put("strike", 7520.0);
        ObjectNode a2 = s2.putObject("sellerActivity");
        a2.put("bucketMinutes", 1);
        point(a2.putArray("points"), 60_000L, 9, 4, 5);

        ObjectNode env = aggregator.aggregate(snap.toString(), "SPX", "2026-07-24", 30, "combined");
        assertEquals(1_780_000_500_000L, env.path("asOfMs").asLong());
        assertEquals(2, env.path("series").size());
        // series sorted by strike; 7520 leads (9 > 5) at the single (latest) bucket.
        assertEquals(7515.0, env.path("series").get(0).path("strike").asDouble());
        assertEquals(7520.0, env.path("leaderStrike").asDouble());
        assertEquals(9L, env.path("series").get(1).path("points").get(0).path("count").asLong());
    }

    @Test
    void handlesUnsortedAndDuplicateTimestamps() {
        ObjectNode activity = mapper.createObjectNode();
        activity.put("bucketMinutes", 1);
        ArrayNode points = activity.putArray("points");
        point(points, 10 * 60_000L, 3, 2, 1);   // out of order
        point(points, 0L, 1, 1, 0);
        point(points, 0L, 2, 0, 2);              // duplicate timestamp
        // sample=5: bucket 0 = 1+2 = 3; the 10m point falls in its own 5m bucket = 3.
        assertEquals(List.of(3L, 3L), counts(activity, 5, "combined"));
        // Session cumulates over SORTED points: 1, 1+2=3, 3+3=6.
        assertEquals(List.of(1L, 3L, 6L), counts(activity, SellerActivityAggregator.SESSION_MINUTES, "combined"));
    }

    @Test
    void coercesMissingNumericFieldsToZeroAndFiltersEmptyPoints() {
        ObjectNode activity = mapper.createObjectNode();
        activity.put("bucketMinutes", 1);
        ArrayNode points = activity.putArray("points");
        ObjectNode noCount = points.addObject();  // no sellTradeCount -> 0 -> dropped by the >0 filter
        noCount.put("timestampMs", 0L);
        ObjectNode valid = points.addObject();
        valid.put("timestampMs", 60_000L);
        valid.put("sellTradeCount", 4);
        valid.put("callSellTradeCount", 4);       // putSellTradeCount absent -> 0
        assertEquals(List.of(4L), counts(activity, 1, "combined"));
        assertEquals(List.of(), counts(activity, 1, "put"), "no put counts -> no surviving points");
    }

    @Test
    void leaderTieResolvesToLowerStrikeAndNullWhenNoData() {
        List<SellerActivityAggregator.StrikeSeries> tie = List.of(
                new SellerActivityAggregator.StrikeSeries(7515, List.of(new long[]{5, 7})),
                new SellerActivityAggregator.StrikeSeries(7520, List.of(new long[]{5, 7})));
        assertEquals(7515.0, SellerActivityAggregator.leader(tie, false));
        assertEquals(7515.0, SellerActivityAggregator.leader(tie, true));
        assertNull(SellerActivityAggregator.leader(List.of(), false));
        assertNull(SellerActivityAggregator.leader(List.of(), true));
    }

    @Test
    void enforcesMaxPointsPerStrikeKeepingTheMostRecent() {
        ObjectNode activity = mapper.createObjectNode();
        activity.put("bucketMinutes", 1);
        ArrayNode points = activity.putArray("points");
        int n = SellerActivityAggregator.MAX_POINTS_PER_STRIKE + 250;
        for (int i = 0; i < n; i++) {
            point(points, i * 60_000L, 1, 1, 0);   // distinct 1-minute buckets
        }
        List<long[]> out = aggregator.aggregatePoints(activity, 1, "combined");
        assertEquals(SellerActivityAggregator.MAX_POINTS_PER_STRIKE, out.size());
        assertEquals((long) (n - 1) * 60_000L, out.get(out.size() - 1)[0], "keeps the newest bucket");
    }

    @Test
    void enforcesMaxTotalPointsSoTheSerializedResponseIsBounded() {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("timestampMs", 1L);
        ArrayNode strikes = snap.putArray("strikes");
        int perStrike = 1500; // == MAX_POINTS_PER_STRIKE
        int strikeCount = (SellerActivityAggregator.MAX_TOTAL_POINTS / perStrike) + 5; // exceed the total cap
        for (int i = 0; i < strikeCount; i++) {
            ObjectNode s = strikes.addObject();
            s.put("strike", 1000.0 + i);
            ObjectNode a = s.putObject("sellerActivity");
            a.put("bucketMinutes", 1);
            ArrayNode pts = a.putArray("points");
            for (int t = 0; t < perStrike; t++) {
                point(pts, (long) t * 60_000L, 1, 1, 0);
            }
        }
        ObjectNode env = aggregator.aggregate(snap.toString(), "SPX", "2026-07-24", 1, "combined");
        int total = 0;
        for (JsonNode row : env.path("series")) {
            total += row.path("points").size();
        }
        assertTrue(total <= SellerActivityAggregator.MAX_TOTAL_POINTS,
                "total points must be bounded, was " + total);
        assertTrue(total > SellerActivityAggregator.MAX_TOTAL_POINTS - perStrike,
                "and should fill close to the cap, was " + total);
    }

    @Test
    void rejectsAnOversizedSnapshotBeforeParsingWithAnEmptyEnvelope() {
        // A snapshot larger than MAX_SNAPSHOT_BYTES must be rejected BEFORE readTree (bounded parse cost),
        // returning a valid empty envelope. (Compact strings keep this ~32MB, not 64MB.)
        String oversized = "a".repeat(SellerActivityAggregator.MAX_SNAPSHOT_BYTES + 1);
        ObjectNode env = aggregator.aggregate(oversized, "SPX", "2026-07-24", 30, "combined");
        assertEquals(0, env.path("series").size());
        assertTrue(env.path("leaderStrike").isNull());
        assertEquals(0L, env.path("asOfMs").asLong());
    }

    @Test
    void enforcesMaxStrikesCeiling() {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("timestampMs", 1L);
        ArrayNode strikes = snap.putArray("strikes");
        for (int i = 0; i < SellerActivityAggregator.MAX_STRIKES + 50; i++) {
            ObjectNode s = strikes.addObject();
            s.put("strike", 1000.0 + i);
            ObjectNode a = s.putObject("sellerActivity");
            a.put("bucketMinutes", 1);
            point(a.putArray("points"), 60_000L, 1, 1, 0);
        }
        ObjectNode env = aggregator.aggregate(snap.toString(), "SPX", "2026-07-24", 30, "combined");
        assertEquals(SellerActivityAggregator.MAX_STRIKES, env.path("series").size());
    }
}
