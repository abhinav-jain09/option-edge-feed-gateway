package app.feedgateway.liquidityhistory;

import app.feedgateway.GatewayMarketCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/** Shared fixtures for the liquidity-history tests: ET calendar, mutable clock, frame JSON builder. */
final class LiquidityHistoryTestSupport {

    static final ZoneId ET = ZoneId.of("America/New_York");
    static final ObjectMapper MAPPER = new ObjectMapper();
    /** Tuesday 2026-06-23, an ordinary EDT trading day; Monday 2026-06-22 is the previous session. */
    static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 23);
    static final LocalDate PREVIOUS_TRADING_DAY = LocalDate.of(2026, 6, 22);

    private LiquidityHistoryTestSupport() {
    }

    static GatewayMarketCalendar calendar() {
        return new GatewayMarketCalendar(ET, LocalTime.of(9, 30), LocalTime.of(16, 0), Set.of(), Map.of());
    }

    static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    static long sessionOpenMs(LocalDate date) {
        return et(date, 9, 30).toEpochMilli();
    }

    /** Thread-safe settable clock so tests advance eviction/date-roll time deterministically. */
    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    static LiquidityHistoryStore.Config config(int maxChains, long maxBytes, long maxLagRecords) {
        return new LiquidityHistoryStore.Config("strike-liquidity-heatmap-dashboard", 4,
                maxChains, maxBytes, maxLagRecords, 300_000L, 20);
    }

    /** Store with NO usable consumer factory — for tests driving state via the fold/epoch seams. */
    static LiquidityHistoryStore seamStore(Clock clock, LiquidityHistoryStore.Config config,
                                           java.util.function.Supplier<Set<String>> wsChains) {
        return new LiquidityHistoryStore(config, calendar(),
                () -> {
                    throw new IllegalStateException("this test never opens a consumer");
                },
                (consumer, timestamps) -> Map.of(), wsChains, clock);
    }

    /** Default cell node (all §5 inputs present); override any field via trailing key/value pairs. */
    static ObjectNode cell(double strike, String side, Object... overrides) {
        ObjectNode c = MAPPER.createObjectNode();
        c.put("strike", strike);
        c.put("optionSide", side);
        c.put("eventTimeMs", 0L);
        c.put("bid", 1.0);
        c.put("ask", 1.2);
        c.put("openBidSize", 0L);
        c.put("openAskSize", 0L);
        c.put("lastBidSize", 0L);
        c.put("lastAskSize", 0L);
        c.put("maxBidSize", 0L);
        c.put("maxAskSize", 0L);
        c.put("avgBidSize", 0.0);
        c.put("avgAskSize", 0.0);
        c.put("bidSizeDeltaSum", 0L);
        c.put("askSizeDeltaSum", 0L);
        c.put("bidPullCount", 0);
        c.put("askPullCount", 0);
        c.put("bidRefillCount", 0);
        c.put("askRefillCount", 0);
        c.put("quoteUpdateCount", 0);
        c.put("dominantBidAction", "INSUFFICIENT_DATA");
        c.put("dominantAskAction", "INSUFFICIENT_DATA");
        c.put("bidState", "NEUTRAL");
        c.put("askState", "NEUTRAL");
        c.put("buyContracts", 0L);
        c.put("sellContracts", 0L);
        c.put("buyPremium", 0.0);
        c.put("sellPremium", 0.0);
        c.put("printsAtAsk", 0);
        c.put("printsAtBid", 0);
        c.put("contractsAtAsk", 0L);
        c.put("contractsAtBid", 0L);
        c.put("lockedOrCrossed", false);
        c.put("stale", false);
        c.put("diagnostics", "");
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            String key = (String) overrides[i];
            Object value = overrides[i + 1];
            if (value instanceof Integer v) {
                c.put(key, v);
            } else if (value instanceof Long v) {
                c.put(key, v);
            } else if (value instanceof Double v) {
                c.put(key, v);
            } else if (value instanceof Boolean v) {
                c.put(key, v);
            } else {
                c.put(key, String.valueOf(value));
            }
        }
        return c;
    }

    static String frame(String symbol, String expiry, long bucketStartMs, String freshness,
                        String inputQuality, List<Double> visibleStrikes, ObjectNode... cells) {
        ObjectNode f = MAPPER.createObjectNode();
        f.put("schemaVersion", 1);
        f.put("symbol", symbol);
        f.put("expiry", expiry);
        f.put("bucketStartMs", bucketStartMs);
        f.put("bucketEndMs", bucketStartMs + 1_000L);
        f.put("asOfEventTimeMs", bucketStartMs);
        f.put("bucketMs", 1_000L);
        ArrayNode cellArray = f.putArray("cells");
        for (ObjectNode cell : cells) {
            cellArray.add(cell);
        }
        ArrayNode strikes = f.putArray("visibleStrikes");
        for (Double strike : visibleStrikes) {
            strikes.add(strike);
        }
        f.put("freshness", freshness);
        f.put("inputQuality", inputQuality);
        return f.toString();
    }

    static void await(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for: " + what);
            }
        }
        fail("timed out waiting for: " + what);
    }
}
