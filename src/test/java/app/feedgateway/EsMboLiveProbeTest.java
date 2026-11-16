package app.feedgateway;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

/**
 * Drives the REAL relay (real id check, real registry decode, real record boundary) over REAL records from the dev broker. SKIPPED unless
 * {@code ES_MBO_LIVE_PROBE} names the broker (for example {@code 100.117.21.102:19092}) and {@code ES_MBO_LIVE_REGISTRY} the registry; it is never part of a
 * default run. {@code ES_MBO_LIVE_ROWS} (default 4000) is how many trailing level-flow records are read; health and events are read for the same time span.
 */
class EsMboLiveProbeTest {
    @Test
    void realRecordsGoThroughTheRealReader() throws Exception {
        String broker = System.getenv("ES_MBO_LIVE_PROBE"), registry = System.getenv("ES_MBO_LIVE_REGISTRY");
        assumeTrue(broker != null && registry != null, "no live broker/registry named");
        int rows = Integer.parseInt(System.getenv().getOrDefault("ES_MBO_LIVE_ROWS", "4000"));
        GatewaySettings settings = new GatewaySettings();
        List<String[]> sunk = new ArrayList<>();
        GatewayAvroDeserializer d = new GatewayAvroDeserializer();
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("schema.registry.url", registry);
        cfg.put("specific.avro.reader", false);
        cfg.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        cfg.put(GatewayAvroDeserializer.TOLERANT_TOPICS, settings.esMboLevelFlowTopic() + "," + settings.esMboHealthTopic() + "," + settings.esMboEventTopic());
        d.configure(cfg, false);
        EsMboRelay relay = new EsMboRelay(settings, (e, j) -> sunk.add(new String[] {e, j}), d);
        Properties p = new Properties();
        p.put("bootstrap.servers", broker);
        p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        p.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        p.put("enable.auto.commit", false);
        p.put("isolation.level", "read_committed");
        try (var c = new KafkaConsumer<String, byte[]>(p)) {
            TopicPartition flow = new TopicPartition(settings.esMboLevelFlowTopic(), 0), health = new TopicPartition(settings.esMboHealthTopic(), 0),
                    events = new TopicPartition(settings.esMboEventTopic(), 0);
            c.assign(List.of(flow, health, events));
            long end = c.endOffsets(List.of(flow)).get(flow);
            c.seek(flow, Math.max(0, end - rows));
            c.seek(health, c.endOffsets(List.of(health)).get(health));
            c.seek(events, c.beginningOffsets(List.of(events)).get(events));
            // the span of the level-flow tail in record time, then health for the same span, events from 24 h before it
            c.assign(List.of(flow));
            c.seek(flow, Math.max(0, end - rows));
            ConsumerRecord<String, byte[]> first = c.poll(Duration.ofSeconds(10)).iterator().next();
            long from = first.timestamp();
            long flowEnd = end;
            List<ConsumerRecord<String, byte[]>> all = new ArrayList<>();
            c.seek(flow, Math.max(0, end - rows));
            long lastTs = from;
            while (all.size() < rows) {
                var batch = c.poll(Duration.ofSeconds(5));
                if (batch.isEmpty()) {
                    break;
                }
                for (var r : batch) {
                    if (r.offset() < flowEnd) {
                        all.add(r);
                        lastTs = r.timestamp();
                    }
                }
            }
            List<ConsumerRecord<String, byte[]>> healthRecs = readRange(c, health, from - 2000, lastTs + 5000);
            List<ConsumerRecord<String, byte[]>> eventRecs = readRange(c, events, from - 24 * 3_600_000L, lastTs + 5000);
            long now = lastTs + 1000;
            // arrival order: records merged by Kafka record time, as one consumer would deliver them
            List<ConsumerRecord<String, byte[]>> merged = new ArrayList<>(all);
            merged.addAll(healthRecs);
            merged.addAll(eventRecs);
            merged.sort(java.util.Comparator.comparingLong(ConsumerRecord::timestamp));
            for (var r : merged) {
                relay.accept(r.topic(), r.key(), r.value(), r.timestamp());
            }
            relay.levelFlowCaughtUp(true, lastTs);   // the tail was read to its end
            relay.tick(lastTs + 5000);
            System.out.println("LIVE-PROBE read level-flow=" + all.size() + " health=" + healthRecs.size() + " events=" + eventRecs.size());
            System.out.println("LIVE-PROBE frames built=" + relay.framesHeldForTest() + " events held=" + relay.eventsHeldForTest() + " drops=" + relay.dropsForTest()
                    + " lateRows=" + relay.lateRowsForTest() + " status=" + relay.statusJson());
            System.out.println("LIVE-PROBE metrics\n" + relay.metrics());
            long t0 = System.nanoTime();
            String history = relay.historyJson();
            System.out.println("LIVE-PROBE historyJson build ms=" + (System.nanoTime() - t0) / 1_000_000.0 + " frames held=" + relay.framesHeldForTest());
            System.out.println("LIVE-PROBE history bytes=" + history.length());
            com.fasterxml.jackson.databind.JsonNode h = new com.fasterxml.jackson.databind.ObjectMapper().readTree(history);
            int nonEmpty = 0;
            com.fasterxml.jackson.databind.JsonNode sample = null;
            for (var f : h.get("frames")) {
                if (f.get("levels").size() > 0) {
                    nonEmpty++;
                    if (sample == null) {
                        sample = f;
                    }
                }
            }
            System.out.println("LIVE-PROBE frames with levels=" + nonEmpty + " of " + h.get("frames").size());
            System.out.println("LIVE-PROBE sample frame " + (sample == null ? "none" : sample.toString()));
            if (h.get("events").size() > 0) {
                System.out.println("LIVE-PROBE sample event " + h.get("events").get(h.get("events").size() - 1));
            }
        }
    }

    private static List<ConsumerRecord<String, byte[]>> readRange(KafkaConsumer<String, byte[]> c, TopicPartition tp, long fromTs, long toTs) {
        c.assign(List.of(tp));
        OffsetAndTimestamp at = c.offsetsForTimes(Map.of(tp, fromTs)).get(tp);
        List<ConsumerRecord<String, byte[]>> out = new ArrayList<>();
        if (at == null) {
            return out;
        }
        c.seek(tp, at.offset());
        long end = c.endOffsets(List.of(tp)).get(tp);
        while (c.position(tp) < end) {
            var batch = c.poll(Duration.ofSeconds(5));
            if (batch.isEmpty()) {
                break;
            }
            for (var r : batch) {
                if (r.timestamp() > toTs) {
                    return out;
                }
                out.add(r);
            }
        }
        return out;
    }
}
