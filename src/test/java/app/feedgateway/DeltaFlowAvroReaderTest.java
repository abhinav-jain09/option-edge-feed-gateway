package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.deltaflow.DeltaFlowAvro;
import com.optionsedge.contracts.deltaflow.DeltaFlowOutputMeta;
import com.optionsedge.contracts.deltaflow.DeltaFlowOutputQuality;
import com.optionsedge.contracts.deltaflow.DeltaFlowReconciliationStatus;
import com.optionsedge.contracts.deltaflow.DeltaFlowStrikeSnapshot;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serde;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** delta-flow-by-strike is Avro on the wire; the browser-bound JSON must stay byte-identical to the JSON era. */
class DeltaFlowAvroReaderTest {
    private static final String SCOPE = "gateway-delta-flow-reader";
    private static final String URL = "mock://" + SCOPE;
    private static final String TOPIC = new GatewaySettings().databentoDeltaFlowByStrikeTopic();
    private static final String SOURCE = "src/main/java/app/feedgateway/FeedGatewayService.java";

    /** Production delta-flow-by-strike, partition 30 offset 32944, 2026-10-09, verbatim (the JSON-era producer's text). */
    private static final String PROD_STRIKE_JSON = "{\"symbol\":\"SPX\",\"tradingDate\":\"20261009\",\"expiry\":\"20261009\",\"strike\":7815.0,"
            + "\"asOfEventTimeMs\":1791575996910,\"sessionNetDeltaFlow\":-208548.55176232077,\"rolling10sNetDeltaFlow\":-1514.2331245326254,"
            + "\"rolling30sNetDeltaFlow\":-901.7198649526501,\"rolling1mNetDeltaFlow\":-36251.202780574364,\"rolling5mNetDeltaFlow\":-30440.94477990198,"
            + "\"rolling15mNetDeltaFlow\":-184791.5451060668,\"cumulativeIntradayNetDeltaFlow\":196037.11737087136,\"buyerContracts\":59326,"
            + "\"sellerContracts\":71481,\"highConfidenceTradeCount\":11010,\"mediumConfidenceTradeCount\":2546,\"lowConfidenceTradeCount\":0,"
            + "\"noConfidenceTradeCount\":0,\"strikeNetCallDeltaFlow\":-195637.37458406342,\"strikeNetPutDeltaFlow\":-12911.177178262267,"
            + "\"strikeCallBuyDeltaFlow\":1351026.4233802168,\"strikeCallSellDeltaFlow\":-1546663.7979642802,"
            + "\"strikePutBuyDeltaFlow\":-1148420.3703221607,\"strikePutSellDeltaFlow\":1135509.1931438984,"
            + "\"highConfidenceNetDeltaFlow\":-222774.85632548796,\"confidenceWeightedNetDeltaFlow\":0.0,\"confidenceWeightQuality\":\"UNKNOWN\","
            + "\"outputMeta\":{\"quality\":\"DEGRADED\",\"reconciliationStatus\":\"RECONCILED\",\"sourceCompleteness\":0.8333333333333334,"
            + "\"calibrationDatasetId\":\"none\",\"modelVersion\":\"delta-flow-inst-1\",\"thresholdSetId\":\"default\"},"
            + "\"sessionObservedFromMs\":1791552675061}";

    private Serde<GenericRecord> producer;
    private GatewayAvroDeserializer consumer;
    private FeedGatewayService service;

    @BeforeEach
    void setUp() throws Exception {
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(SCOPE);
        registry.register(DeltaFlowAvro.STRIKE_SNAPSHOT_SUBJECT, new AvroSchema(DeltaFlowAvro.STRIKE_SNAPSHOT));
        producer = DeltaFlowAvro.serde(URL, DeltaFlowAvro.STRIKE_SNAPSHOT, registry);
        consumer = new GatewayAvroDeserializer();
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("schema.registry.url", URL);
        cfg.put("specific.avro.reader", "false");
        cfg.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        cfg.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, new GatewaySettings().underlyingSpxPriceTopic());
        consumer.configure(cfg, false);
        service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static DeltaFlowStrikeSnapshot prodStrike() {
        return new DeltaFlowStrikeSnapshot("SPX", "20261009", "20261009", 7815.0, 1791575996910L, -208548.55176232077,
                -1514.2331245326254, -901.7198649526501, -36251.202780574364, -30440.94477990198, -184791.5451060668,
                196037.11737087136, 59326L, 71481L, 11010L, 2546L, 0L, 0L, -195637.37458406342, -12911.177178262267,
                1351026.4233802168, -1546663.7979642802, -1148420.3703221607, 1135509.1931438984, -222774.85632548796, 0.0,
                DeltaFlowOutputQuality.UNKNOWN, new DeltaFlowOutputMeta(DeltaFlowOutputQuality.DEGRADED,
                        DeltaFlowReconciliationStatus.RECONCILED, 0.8333333333333334, "none", "delta-flow-inst-1", "default"));
    }

    private byte[] prodBytes() {
        return producer.serializer().serialize(TOPIC, DeltaFlowAvro.toRecord(prodStrike(), 1791552675061L));
    }

    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        try {
            return m.invoke(service, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    private String browserJson(byte[] bytes) throws Exception {
        return (String) call("kafkaJson", new Class<?>[] {String.class, boolean.class, Object.class}, "delta-flow", true,
                consumer.deserialize(TOPIC, bytes));
    }

    @Test
    void theProductionRecordReachesTheBrowserAsTheByteIdenticalJsonText() throws Exception {
        assertEquals(PROD_STRIKE_JSON, browserJson(prodBytes()));
    }

    @Test
    void cacheKeyAndTimestampMatchTheJsonTextPath() throws Exception {
        String json = browserJson(prodBytes());
        Class<?>[] keyTypes = {String.class, String.class};
        assertEquals("SPX|20261009|7815", call("deltaFlowCacheKey", keyTypes, json, "fallback"));
        assertEquals(call("deltaFlowCacheKey", keyTypes, PROD_STRIKE_JSON, "fallback"), call("deltaFlowCacheKey", keyTypes, json, "fallback"));
        assertEquals(1791575996910L, call("deltaFlowTimestamp", new Class<?>[] {String.class}, json));
    }

    @Test
    void jsonBytesAreRejectedLoudly() {
        byte[] json = PROD_STRIKE_JSON.getBytes(StandardCharsets.UTF_8);
        assertThrows(SerializationException.class, () -> consumer.deserialize(TOPIC, json));
    }

    @Test
    void aTombstoneRendersNoJsonAndAStringOrForeignRecordFailsLoud() throws Exception {
        assertNull(browserJson(null));
        Class<?>[] types = {String.class, boolean.class, Object.class};
        assertThrows(IllegalStateException.class, () -> call("kafkaJson", types, "delta-flow", true, PROD_STRIKE_JSON));
        GenericRecord event = DeltaFlowAvro.toRecord(new DeltaFlowOutputMeta(DeltaFlowOutputQuality.FULL,
                DeltaFlowReconciliationStatus.RECONCILED, 1.0, "none", "v", "t"));
        assertThrows(IllegalStateException.class, () -> call("kafkaJson", types, "delta-flow", true, event));
    }

    @Test
    void deltaFlowIsBoundOnTheAvroConsumersOnlyAndAccelStaysJson() throws Exception {
        String source = Files.readString(Path.of(SOURCE));
        String binding = "topicEvents.put(settings.databentoDeltaFlowByStrikeTopic(), new TopicBinding(\"DATABENTO\", \"delta-flow\"));";
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(binding), method + " must bind delta-flow (Avro)");
        }
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            String body = methodBody(source, method);
            assertFalse(body.contains(binding), method + " must NOT bind delta-flow-by-strike (it is Avro)");
            assertTrue(body.contains("settings.deltaFlowAccelTopic()"), method + " keeps the JSON delta-flow-accel topic");
        }
        assertTrue(source.contains("avroTopics.put(settings.databentoDeltaFlowByStrikeTopic(), \"delta-flow\");"));
        assertFalse(source.contains("stringTopics.put(settings.databentoDeltaFlowByStrikeTopic(), \"delta-flow\");"));
        assertTrue(source.contains("avroTopics.remove(settings.databentoDeltaFlowByStrikeTopic());"));
        assertFalse(source.contains("stringTopics.remove(settings.databentoDeltaFlowByStrikeTopic());"));
        assertTrue(source.contains("\"gex-strike-lifecycle\", \"delta-flow\"));"), "Avro bootstrap list carries delta-flow");
    }

    private static String methodBody(String source, String methodName) {
        int start = source.indexOf("private void " + methodName + "()");
        if (start < 0) throw new IllegalArgumentException("method not found: " + methodName);
        int next = source.indexOf("\n    private ", start + 1);
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }
}
