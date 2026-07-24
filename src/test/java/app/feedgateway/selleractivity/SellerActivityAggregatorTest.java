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
 * Golden-master parity with the former client-side option-chain.js seller-activity aggregation
 * (option-chain-seller-activity.test.js). The exact same inputs must produce the exact same counts,
 * so moving the logic server-side shifts NOTHING for the UI.
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
        assertEquals(7520.0, SellerActivityAggregator.leader(series));
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
}
