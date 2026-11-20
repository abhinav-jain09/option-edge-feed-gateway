package app.feedgateway;

import app.feedgateway.readable.ReferencePrice;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;

/**
 * <b>C-74</b>: the two read-backs the reference price needs, as the gateway performs them.
 *
 * <p><b>No session calendar is involved, deliberately.</b> The SPX index only prints during the
 * cash session, so the newest index print at or before now <i>is</i> the previous cash close —
 * there is nothing to look up and no holiday table to get wrong. That is the same rule the live
 * page already uses; this class only lets the gateway apply it to history instead of waiting for
 * the next print, which overnight is eleven hours away.
 *
 * <p>Both seeks are bounded in time and in records, and both are <b>one-shot at start-up</b>: this
 * is not a consumer, it opens a client, assigns, seeks, drains a bounded window and closes. A
 * failure anywhere returns an empty list, because {@link ReferencePrice} already states an
 * explained absence and a page that says why it has no reference is better than a gateway that
 * will not start.
 */
final class ReadableReferenceHistory implements ReferencePrice.History {

    /**
     * A bound on one drain. The caller asks for a time WINDOW and chooses how wide; this class
     * only reads what it is asked for, so a look-back constant here would be a second opinion
     * about a question the caller already answered.
     */
    /** One session plus the evening before it: enough to rebuild the path, not a week of it. */
    static final long SNAPSHOT_LOOKBACK_MS = 30L * 3_600_000L;

    /** A bound on the drain, so a busy topic cannot turn start-up into a replay. */
    static final int MAX_RECORDS = 20_000;

    /**
     * What one drain actually did.
     *
     * <p>This exists because the old {@code catch (RuntimeException) { return List.of(); }} made
     * a failed read and an empty window indistinguishable, and C-71 then reported "rebuilt from 0
     * snapshot(s)" for both. I could not say WHY the rebuild was empty, because my own catch block
     * had thrown the reason away: the measurement that finally identified it (45,075 price records
     * polled and ZERO converted) had to be taken outside the gateway, with a separate probe, to
     * learn something the gateway already knew and discarded.
     *
     * <p>{@link #polledButConvertedNothing()} is the signature of a converter that cannot read the
     * records on its topic at all — the C-74 defect, where every value on {@code
     * underlying.spx.price} is a typed {@code UnderlyingPriceEvent} and the generic Avro renderer
     * requires a {@code GenericRecord}. A window that is genuinely empty polls nothing; a
     * converter that is wrong polls plenty and converts none.
     */
    record Drain(String topic, long fromMs, long toMs, int polled, int converted,
                 int outsideWindow, int unconvertible, String failure) {

        boolean polledButConvertedNothing() {
            return polled > 0 && converted == 0;
        }

        String line() {
            StringBuilder b = new StringBuilder("readable drain ").append(topic)
                    .append(" window=").append(java.time.Instant.ofEpochMilli(fromMs))
                    .append("..").append(java.time.Instant.ofEpochMilli(toMs))
                    .append(" polled=").append(polled)
                    .append(" converted=").append(converted)
                    .append(" outsideWindow=").append(outsideWindow)
                    .append(" unconvertible=").append(unconvertible);
            if (failure != null) {
                b.append(" failed=").append(failure);
            }
            if (polledButConvertedNothing()) {
                b.append("  <- POLLED RECORDS AND CONVERTED NONE: the converter cannot read this"
                        + " topic's records, which is not an empty window");
            }
            return b.toString();
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Every drain this instance performed, newest last, <b>bounded</b>.
     *
     * <p>It was an unbounded {@code ArrayList} and I wrote it that way this morning. A {@code
     * Drain} holds counters and two strings, so 28 of them are a few kilobytes and it is not what
     * exhausted the heap at 09:56 — but an unbounded collection on a long-running service is a
     * leak waiting for a longer session, and the day's lesson is that the entry point is where
     * these hide. The bound is generous against what the wiring tests read (the last few).
     */
    static final int DRAINS_MAX = 256;

    private final java.util.ArrayDeque<Drain> drains = new java.util.ArrayDeque<>();

    /** Every drain this instance performed, newest last. Read by the wiring tests. */
    List<Drain> drains() {
        return List.copyOf(drains);
    }

    private Drain record(Drain d) {
        drains.addLast(d);
        while (drains.size() > DRAINS_MAX) {
            drains.removeFirst();
        }
        System.out.println(d.line());
        return d;
    }

    private final Supplier<Consumer<String, Object>> consumers;
    private final Function<Object, String> priceToJson;
    private final Function<Object, String> barToJson;
    private final String priceTopic;
    private final String barTopic;

    /**
     * @param consumers  opens a client configured for these topics; closed by this class
     * @param toJson     the gateway's own record-to-JSON conversion, so history decodes exactly
     *                   as the live tap does -- two decoders would be two ways to be wrong
     */
    ReadableReferenceHistory(Supplier<Consumer<String, Object>> consumers,
            Function<Object, String> toJson, String priceTopic, String barTopic) {
        this(consumers, toJson, toJson, priceTopic, barTopic);
    }

    /**
     * <b>One converter per topic.</b> The single shared converter this replaces was the whole of
     * C-74's wrong close: {@code underlying.spx.price} carries a TYPED
     * {@code com.optionsedge.contracts.hpsf.UnderlyingPriceEvent} (the gateway's deserializer
     * returns typed records for exactly that topic, {@code underlying.vix.price} and the spot-vol
     * regime, and generic ones for everything else), while {@code avroJson} requires a
     * {@code GenericRecord} and returned null for every one of them. Measured: 45,075 price
     * records polled, ZERO converted. The bars on the other topic are generic and convert with
     * {@code avroJson}, so a seek that reads two topics needs two boundaries, named here rather
     * than guessed at from the record's runtime class.
     */
    ReadableReferenceHistory(Supplier<Consumer<String, Object>> consumers,
            Function<Object, String> priceToJson, Function<Object, String> barToJson,
            String priceTopic, String barTopic) {
        this.consumers = consumers;
        this.priceToJson = priceToJson;
        this.barToJson = barToJson;
        this.priceTopic = priceTopic;
        this.barTopic = barTopic;
    }

    @Override
    public List<String> pricesBetween(long fromMs, long toMs, int limit) {
        return asJson(priceTopic, fromMs, toMs, limit, priceToJson);
    }

    @Override
    public List<String> barsBetween(long fromMs, long toMs) {
        return barsByTheirOwnMinutes(fromMs, toMs);
    }

    /**
     * Every bar whose OWN minute lies in the window, backfilled ones included.
     *
     * <p>{@code asJson} judges the window on the broker's stamp, which is right for the price
     * topic: a price record's own instant and its publish instant are the same to within seconds,
     * and it carries no minutes of its own. A BAR does carry them, and the two can be hours apart
     * -- 2026-11-20's night bars were written up to five and a half hours after the minutes they
     * describe. Every reader of this list keys on those minutes (the grid's slots, the previous
     * close's cash-session test, the publish-lag measurement), so the window belongs on them.
     */
    private List<String> barsByTheirOwnMinutes(long fromMs, long toMs) {
        List<String> out = new ArrayList<>();
        drainInto(barTopic, fromMs, toMs, MAX_RECORDS, new Sink() {
            @Override
            public boolean take(ConsumerRecord<String, Object> one) {
                String json = barToJson.apply(one.value());
                if (json == null || json.isBlank()) {
                    return false;
                }
                out.add(json);
                return true;
            }

            @Override
            public long ownTimeMs(ConsumerRecord<String, Object> one) {
                Long start = barStartMs(barToJson.apply(one.value()));
                return start == null ? Long.MIN_VALUE : start;
            }
        });
        return out;
    }

    /**
     * <b>Item 6</b>: each bar's own minute with the instant the BROKER holds for its record.
     *
     * <p>The publish lag is the one measurement of feed lateness that survives a restart, because
     * it travels with the record instead of being taken from our own clock as the record arrives.
     */
    List<app.feedgateway.readable.FeedGap.Published> barPublishTimes(long fromMs, long toMs) {
        List<app.feedgateway.readable.FeedGap.Published> out = new ArrayList<>();
        drainInto(barTopic, fromMs, toMs, MAX_RECORDS, new Sink() {
            @Override
            public boolean take(ConsumerRecord<String, Object> one) {
                String json = barToJson.apply(one.value());
                Long start = barStartMs(json);
                Long end = barEndMs(json);
                if (start == null || end == null) {
                    return false;  // a bar that cannot say which minutes it covers is unusable
                }
                out.add(new app.feedgateway.readable.FeedGap.Published(start, end,
                        one.timestamp()));
                return true;
            }

            @Override
            public long ownTimeMs(ConsumerRecord<String, Object> one) {
                Long start = barStartMs(barToJson.apply(one.value()));
                return start == null ? Long.MIN_VALUE : start;
            }
        });
        return out;
    }

    /**
     * The instant the bar CLOSED, from the record itself.
     *
     * <p>Read rather than derived from {@code barStartMs + widthMs}: the record carries it, and a
     * derived end would be a second opinion about a figure the producer already states.
     */
    private static Long barEndMs(String json) {
        return longAt(json, "barEndMs");
    }

    /** How many records of the last drain were in the window only by their OWN time. */
    private volatile int lastLateButInWindow;

    /**
     * Records the last drain took because their own minutes were in the window, although the
     * broker wrote them after it. Zero on a day with no backfill.
     */
    public int lastLateButInWindow() {
        return lastLateButInWindow;
    }

    /** The bar's own minute, from the record the gateway converted. */
    private static Long barStartMs(String json) {
        return longAt(json, "barStartMs");
    }

    private static Long longAt(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode o =
                    MAPPER.readTree(json).path("observations").path(field);
            return o.isNumber() ? o.longValue() : null;
        } catch (Exception malformed) {
            return null;
        }
    }

    private List<String> asJson(String topic, long fromMs, long toMs, int limit,
            Function<Object, String> toJson) {
        List<String> out = new ArrayList<>();
        drainInto(topic, fromMs, toMs, limit, one -> {
            String json = toJson.apply(one.value());
            if (json == null || json.isBlank()) {
                return false;
            }
            out.add(json);
            return true;
        });
        return out;
    }

    /**
     * <b>C-71</b>: the ES indicators snapshots of this session, for the side-path rebuild.
     *
     * <p>The same bounded, by-time seek: the topic holds seven days and reading it from offset
     * zero at every start-up would be a replay of a week for one session's worth of records.
     */
    List<String> sessionSnapshots(long beforeMs, String snapshotTopic) {
        return asJson(snapshotTopic, beforeMs - SNAPSHOT_LOOKBACK_MS, beforeMs, MAX_RECORDS,
                priceToJson);
    }

    /**
     * Every record of a topic in a time window, as JSON.
     *
     * <p>Seeks by TIMESTAMP on each partition rather than reading from the beginning: these topics
     * are retained unlimited, and reading one from offset zero at every start-up would be minutes
     * of replay for a handful of records that matter.
     */
    /**
     * What one in-window record is turned into. Returns whether it yielded something usable, so
     * the drain can report "polled plenty, converted none" without knowing what a reader wants.
     */
    private interface Sink {
        boolean take(ConsumerRecord<String, Object> record);

        /**
         * <b>The record's OWN time</b>, where it carries one, so the window can be judged on the
         * minutes a record describes rather than the instant a broker happened to write it.
         * {@link Long#MIN_VALUE} where the record carries none and the broker's stamp is all
         * there is.
         *
         * <p><b>Why this exists.</b> The drain dropped every record whose broker stamp was past
         * {@code toMs} and counted it {@code outsideWindow}. A BACKFILLED bar is exactly that: the
         * bars for 18:16 to 23:46 on 2026-11-20 were published up to five and a half hours after
         * the minutes they describe, so the night grid asked for the night and was handed the
         * subset that happened to be punctual. The grid is keyed on {@code barStartMs} and the
         * previous close is chosen by the bar's own minute, so the window belongs on that figure
         * in both readers.
         */
        default long ownTimeMs(ConsumerRecord<String, Object> record) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * <b>One seek, for every reader.</b> The JSON reader and the publish-time reader share this
     * because two copies of a by-timestamp seek is two places for the next fix to miss — the same
     * shape of defect as the two differently-filtered indicator paths this change set also closes.
     */
    private Drain drainInto(String topic, long fromMs, long toMs, int limit, Sink sink) {
        if (topic == null || topic.isBlank() || limit <= 0) {
            return record(new Drain(String.valueOf(topic), fromMs, toMs, 0, 0, 0, 0,
                    "no topic bound"));
        }
        int taken = 0;
        int polled = 0;
        int outsideWindow = 0;
        int unconvertible = 0;
        int lateButInWindow = 0;
        try (Consumer<String, Object> consumer = consumers.get()) {
            if (consumer == null) {
                return record(new Drain(topic, fromMs, toMs, 0, 0, 0, 0, "no consumer"));
            }
            List<TopicPartition> parts = new ArrayList<>();
            var infos = consumer.partitionsFor(topic, Duration.ofSeconds(10));
            if (infos == null || infos.isEmpty()) {
                return record(new Drain(topic, fromMs, toMs, 0, 0, 0, 0,
                        "topic has no partitions"));
            }
            for (var info : infos) {
                parts.add(new TopicPartition(topic, info.partition()));
            }
            consumer.assign(parts);

            Map<TopicPartition, Long> wanted = new java.util.HashMap<>();
            for (TopicPartition tp : parts) {
                wanted.put(tp, fromMs);
            }
            Map<TopicPartition, OffsetAndTimestamp> found =
                    consumer.offsetsForTimes(wanted, Duration.ofSeconds(10));
            Map<TopicPartition, Long> ends = consumer.endOffsets(parts, Duration.ofSeconds(10));
            boolean any = false;
            for (TopicPartition tp : parts) {
                OffsetAndTimestamp at = found == null ? null : found.get(tp);
                if (at == null) {
                    // Nothing at or after fromMs on this partition: every record it holds is older
                    // than the window, so there is nothing here to read forward to.
                    consumer.seek(tp, ends.getOrDefault(tp, 0L));
                    continue;
                }
                consumer.seek(tp, at.offset());
                any = true;
            }
            if (!any) {
                return record(new Drain(topic, fromMs, toMs, 0, 0, 0, 0,
                        "no partition holds a record at or after the window start"));
            }

            int empties = 0;
            while (taken < limit && empties < 3) {
                ConsumerRecords<String, Object> batch = consumer.poll(Duration.ofSeconds(2));
                if (batch == null || batch.isEmpty()) {
                    empties++;
                    continue;
                }
                empties = 0;
                for (ConsumerRecord<String, Object> one : batch) {
                    polled++;
                    long own = sink.ownTimeMs(one);
                    boolean inWindow = own == Long.MIN_VALUE
                            ? one.timestamp() <= toMs
                            : own >= fromMs && own <= toMs;
                    if (!inWindow) {
                        outsideWindow++;
                        continue;             // past the window; a later partition may still be in it
                    }
                    if (own != Long.MIN_VALUE && one.timestamp() > toMs) {
                        // A record whose own minutes are in the window and whose broker stamp is
                        // not: a backfilled bar. Counted so "the grid took bars the publish-time
                        // filter would have dropped" is a figure and not a belief.
                        lateButInWindow++;
                    }
                    if (sink.take(one)) {
                        taken++;
                    } else {
                        unconvertible++;
                    }
                    if (taken >= limit) {
                        break;
                    }
                }
                if (caughtUp(consumer, parts, ends)) {
                    break;
                }
            }
        } catch (RuntimeException unreachable) {
            // A broker that cannot be read at start-up is still not a reason to refuse to start --
            // but it IS a reason to say so. The exception's class and message go into the report
            // and onto stdout, because the version that returned an empty list here cost a
            // separate out-of-process probe to diagnose.
            return record(new Drain(topic, fromMs, toMs, polled, taken, outsideWindow,
                    unconvertible, unreachable.getClass().getSimpleName() + ": "
                            + unreachable.getMessage()));
        }
        lastLateButInWindow = lateButInWindow;
        return record(new Drain(topic, fromMs, toMs, polled, taken, outsideWindow,
                unconvertible, null));
    }

    private static boolean caughtUp(Consumer<String, Object> consumer, List<TopicPartition> parts,
            Map<TopicPartition, Long> ends) {
        for (TopicPartition tp : parts) {
            if (consumer.position(tp) < ends.getOrDefault(tp, 0L)) {
                return false;
            }
        }
        return true;
    }
}
