package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.hpsf.UnderlyingPriceAvro;
import com.optionsedge.contracts.hpsf.UnderlyingPriceEvent;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpxPriceJsonTest {
    private static final String SCOPE = "gw-spx-price";
    private static final String URL = "mock://" + SCOPE;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewaySettings settings = new GatewaySettings();
    private final String topic = settings.underlyingSpxPriceTopic();
    private FeedGatewayService service;
    private UnderlyingPriceAvro.EventSerializer writer;
    private SchemaRegistryClient registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = MockSchemaRegistry.getClientForScope(SCOPE);
        registry.register(UnderlyingPriceAvro.SUBJECT, new AvroSchema(UnderlyingPriceAvro.SCHEMA));
        writer = new UnderlyingPriceAvro.EventSerializer(registry);
        writer.configure(UnderlyingPriceAvro.serializerConfig(URL), false);
        service = new FeedGatewayService(settings, mapper, new HpsfGatewayViewMapper(), null);
    }

    @AfterEach
    void tearDown() {
        MockSchemaRegistry.dropScope(SCOPE);
    }

    /** The value deserializer exactly as the service configures it, built the way KafkaConsumer builds it. */
    private Deserializer<?> consumerValueDeserializer(Properties props) throws Exception {
        props.put("schema.registry.url", URL);
        Deserializer<?> d = (Deserializer<?>) Class.forName((String) props.get("value.deserializer")).getDeclaredConstructor().newInstance();
        d.configure(new HashMap<>((java.util.Map) props), false);
        return d;
    }

    private Deserializer<?> liveOrCache() throws Exception {
        var m = FeedGatewayService.class.getDeclaredMethod("avroConsumerProperties", String.class);
        m.setAccessible(true);
        return consumerValueDeserializer((Properties) m.invoke(service, "t"));
    }

    private Deserializer<?> replay(String runId) throws Exception {
        var params = new app.feedgateway.mtsession.gateway.ReplayParams("app:x", "SPX", "20261019", 1000L, 2000L, 10, runId);
        return consumerValueDeserializer(service.replayConsumerProps("app:x", true, params, java.util.Map.of()));
    }

    /** (double bits, CPython repr(float)) pairs printed by a real CPython 3 interpreter. */
    private static final Object[][] GOLDEN = {
        {0x40bca70000000000L, "7335.0"}, {0x40b77c8000000000L, "6012.5"}, {0x40bd4bdf97edc7efL, "7499.873412"},
        {0x3fd3333333333334L, "0.30000000000000004"}, {0x3ee4f8b588e368f1L, "1e-05"}, {0x3f1a36e2eb1c432dL, "0.0001"},
        {0x3f1a36371ea531a8L, "9.999e-05"}, {0x4132d68700000000L, "1234567.0"}, {0x416312cfe0000000L, "9999999.0"},
        {0x416312d000000000L, "10000000.0"}, {0x41678c29dccccccdL, "12345678.9"}, {0x430c6bf526340000L, "1000000000000000.0"},
        {0x4341c37937e07fffL, "9999999999999998.0"}, {0x4341c37937e08000L, "1e+16"}, {0x434aa535d3d0c000L, "1.5e+16"},
        {0x40fe240c9fcb689fL, "123456.789012345"}, {0x40bb580000000001L, "7000.000000000001"},
        {0x40bb57ffffffffffL, "6999.999999999999"}, {0x4341c37937e08001L, "1.0000000000000002e+16"},
        {0x40b388028f5c28f6L, "5000.01"}, {0x40b387ffffffffffL, "4999.999999999999"}, {0x40b98f35c28f5c29L, "6543.21"},
        {0x3f50624dd2f1a9fcL, "0.001"}, {0x3f202c9dedbc309dL, "0.0001234"}, {0x42dc12218377de66L, "123456789012345.6"},
        // adversarial values inside decimal(38,18): |x| < 1e20, at most 18 fractional digits (CPython-printed)
        {0x43e158e460913d00L, "1e+19"},
        {0x43e158e460913d00L, "1e+19"},
        {0x43e0000000000000L, "9.223372036854776e+18"},
        {0x3c570ef54646d497L, "5e-18"},
        {0x3c670ef54646d497L, "1e-17"},
        {0x437b69b4ba630f35L, "1.2345678901234568e+17"},
        {0x4011666666666666L, "4.35"},
        {0x3fd3333333333333L, "0.3"},
        {0x4005666666666666L, "2.675"},
        {0x3ff0000000000001L, "1.0000000000000002"},
        {0x3fd3333333333334L, "0.30000000000000004"},
        {0xc3e158e460913d00L, "-1e+19"},
        {0xbee4f8b588e368f1L, "-1e-05"},
        {0xbfd3333333333334L, "-0.30000000000000004"},
        {0x43a757aa16a03500L, "8.41e+17"},
        {0x43a757aa16a03501L, "8.410000000000001e+17"},
        {0x43a757aa16a034ffL, "8.409999999999999e+17"},
        {0x43abc16d674ec800L, "1e+18"},
        {0x4415af1d78b58c03L, "9.9999999999999e+19"},
        {0x43c679a067967f28L, "3.2390038155667907e+18"},
        {0x40953692db3e40c5L, "1357.6434144713432"},
        {0x43d695c5011fad06L, "6.509693795925498e+18"},
        {0x40845f6b8916f673L, "651.9275075715981"},
        {0x43d29800232fa0d1L, "5.359284161062585e+18"},
        {0x40a9b666da99389bL, "3291.200886524353"},
        {0x43a01fc3c929f427L, "5.809312488222934e+17"},
        {0x40b1d6ec0e2c647eL, "4566.922091269049"},
        {0x4394de21ad97a616L, "3.7591908876140685e+17"},
        {0x40ae7d9f99ac95b1L, "3902.811719315789"},
        {0x43a36a22acfe209cL, "6.994843803226148e+17"},
        {0x408983581fc9dbd4L, "816.4180293817722"},
        {0x43cd75ff770a8900L, "4.245767372235997e+18"},
        {0x40bd11ab56ee1320L, "7441.669295196218"},
        {0x43b131712cc1126aL, "1.2388958095353062e+18"},
        {0x409f649b17dd5e04L, "2009.1514582241662"},
        {0x43d5c50eb0cc0498L, "6.274704790833488e+18"},
        {0x40c0a8b0b559f253L, "8529.380534404108"},
        {0x43d40614574d1285L, "5.771452383226369e+18"},
        {0x40abe43fefa39eb4L, "3570.124875176547"},
    };

    private static UnderlyingPriceEvent event(double px, Long size) {
        return new UnderlyingPriceEvent(1, "SPX", "2026-10-16T14:30:00.123Z", px, size, "NATIVE_SPX_INDEX",
                "LIVE_INDEX", px, 1_760_625_000_123_000_000L, "epoch-1", 42L, "LIVE_INDEX");
    }

    /** The JSON era's text: field order of the record, compact, doubles as Jackson/Python repr print them. */
    private static String oldJson(String px, Long size) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"eventTime\":\"2026-10-16T14:30:00.123Z\",\"price\":" + px
                + ",\"size\":" + size + ",\"source\":\"NATIVE_SPX_INDEX\",\"quality\":\"LIVE_INDEX\",\"canonicalSpot\":" + px
                + ",\"spotEventTimeNs\":1760625000123000000,\"feedEpochId\":\"epoch-1\",\"epochSeq\":42,\"spotQuality\":\"LIVE_INDEX\"}";
    }

    @Test
    void wireBytesThroughTheConfiguredConsumerRenderByteIdenticalJsonForAGridOfPrices() throws Exception {
        Deserializer<?> live = liveOrCache();
        Deserializer<?> replay = replay("run-1");
        String replayTopic = app.feedgateway.mtsession.gateway.ReplayTopicResolver.toReplayTopic(topic, "run-1");
        for (Object[] g : GOLDEN) {
            double px = Double.longBitsToDouble((Long) g[0]);
            String repr = (String) g[1];
            for (Long size : new Long[] {null, 5L}) {
                byte[] wire = writer.serialize(topic, event(px, size));
                String old = oldJson(repr, size);
                Object typed = live.deserialize(topic, wire);
                assertEquals(event(px, size), typed, "the typed event reaches the code, price " + px);
                assertEquals(old, SpxPriceJson.render(mapper, typed), "price " + px);
                assertEquals(typed, replay.deserialize(replayTopic, wire), "run-scoped replay binding");
            }
        }
    }

    @Test
    void doubleToStringIsTheShortestRoundTripOnlyFromJdk19() {
        // JDK-4511638: Double.toString yields CPython's shortest digits from JDK 19; pythonRepr relies on it.
        org.junit.jupiter.api.Assertions.assertTrue(Runtime.version().feature() >= 19);
    }

    @Test
    void jsonForeignSchemaAndNonConfluentBytesAreRejectedOnLiveCacheAndReplayBindings() throws Exception {
        Schema other = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Other\",\"namespace\":\"x\",\"fields\":[{\"name\":\"a\",\"type\":\"long\"}]}");
        registry.register("x.Other", new AvroSchema(other));
        GenericData.Record foreign = new GenericData.Record(other);
        foreign.put("a", 1L);
        KafkaAvroSerializer foreignWriter = new KafkaAvroSerializer(registry);
        foreignWriter.configure(java.util.Map.of("schema.registry.url", URL, "auto.register.schemas", false,
                "use.latest.version", true, "value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.RecordNameStrategy"), false);
        byte[] foreignFrame = foreignWriter.serialize(topic, foreign);
        byte[] json = oldJson("6012.5", null).getBytes(StandardCharsets.UTF_8);
        byte[] garbage = {1, 2, 3, 4, 5, 6, 7};
        String replayTopic = app.feedgateway.mtsession.gateway.ReplayTopicResolver.toReplayTopic(topic, "run-1");
        for (Deserializer<?> d : new Deserializer<?>[] {liveOrCache(), replay("run-1")}) {
            for (String t : new String[] {topic, replayTopic}) {
                assertThrows(SerializationException.class, () -> d.deserialize(t, json));
                assertThrows(SerializationException.class, () -> d.deserialize(t, foreignFrame));
                assertThrows(SerializationException.class, () -> d.deserialize(t, garbage));
            }
        }
    }

    @Test
    void otherAvroTopicsStillDecodeGenericRecords() throws Exception {
        Schema other = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Other\",\"namespace\":\"x\",\"fields\":[{\"name\":\"a\",\"type\":\"long\"}]}");
        GenericData.Record rec = new GenericData.Record(other);
        rec.put("a", 7L);
        KafkaAvroSerializer w = new KafkaAvroSerializer(registry);
        w.configure(java.util.Map.of("schema.registry.url", URL, "value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.RecordNameStrategy"), false);
        Object back = liveOrCache().deserialize(settings.databentoGexTopic(), w.serialize(settings.databentoGexTopic(), rec));
        assertEquals(rec, back);
    }

    @Test
    void tombstoneIsNullAndATypedValueIsRequired() {
        assertNull(SpxPriceJson.render(mapper, null));
        assertThrows(IllegalStateException.class, () -> SpxPriceJson.render(mapper, oldJson("6012.5", null)));
    }
}
