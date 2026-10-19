package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.avro.CanonicalAvroKafka;
import com.optionsedge.contracts.avro.CanonicalJsonAvro;
import com.optionsedge.contracts.avro.CanonicalModelDeserializer;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;

class CvdAvroRollingGatewayTest {
    @Test void liveCacheAndHistoryBuildersReadRealProducerModelsOnTheirExistingTopicNames() throws Exception {
        String scope="cvd-gateway-rolling";
        GatewaySettings settings=new GatewaySettings();
        var service=new FeedGatewayService(settings,new ObjectMapper(),new HpsfGatewayViewMapper(),null);
        try(var input=getClass().getResourceAsStream("/cvd/producer-23866b17.json")) {
            assertNotNull(input);JsonNode fixtures=new ObjectMapper().readTree(input);
            for(String builder:List.of("stringObjectConsumerProperties")) {
                var method=FeedGatewayService.class.getDeclaredMethod(builder,String.class);method.setAccessible(true);
                var props=(Properties)method.invoke(service,"state-test");
                assertEquals("false",props.get(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG));
                assertEquals(Integer.toString(Math.min(settings.maxPollRecords(),8)),props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
                assertEquals(settings.schemaRegistryUrl(),props.get("schema.registry.url"));
                CanonicalAvroKafka.producer(props,"mock://"+scope);
                var config=new HashMap<String,Object>();props.forEach((k,v)->config.put((String)k,v));
                try(var writer=new KafkaAvroSerializer();var reader=new CanonicalModelDeserializer()) {
                    writer.configure(config,false);reader.configure(config,false);
                    for(JsonNode fixture:fixtures) {
                        String name=fixture.path("schema").asText();
                        String topic=switch(name) {
                            case "CvdBarEnvelope" -> settings.esCvdBarsTopic();
                            case "CvdSnapshot" -> settings.esCvdTopic();
                            case "FootprintBarEnvelope" -> settings.esFootprintBarsTopic();
                            case "FootprintOutcome" -> settings.esFootprintOutcomesTopic();
                            case "FootprintSnapshot" -> settings.esFootprintTopic();
                            case "FootprintEvidenceSnapshot" -> settings.esFootprintEvidenceTopic();
                            default -> throw new AssertionError(name);
                        };
                        Schema schema=service.cvdAvroBindings().get(topic);assertNotNull(schema);
                        MockSchemaRegistry.getClientForScope(scope).register(topic+"-value",schema);
                        JsonNode model=fixture.get("value");String legacy=model.toString();
                        assertEquals(legacy,reader.deserialize(topic,legacy.getBytes(StandardCharsets.UTF_8)));
                        byte[] wire=writer.serialize(topic,CanonicalJsonAvro.record(schema,model));assertEquals(0,wire[0]);
                        JsonNode decoded=new ObjectMapper().readTree(reader.deserialize(topic,wire));
                        assertEquals(fieldNames(model),fieldNames(decoded));
                        same(model,decoded,false);
                        assertEquals(legacy,reader.deserialize(topic,legacy.getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        } finally {MockSchemaRegistry.dropScope(scope);}
    }

    @Test void relatedBindingsUseTheExactReleasedContractAndUnrelatedConsumersKeepTheirPollSetting() throws Exception {
        var settings=new GatewaySettings();var service=new FeedGatewayService(settings,new ObjectMapper(),new HpsfGatewayViewMapper(),null);
        var method=FeedGatewayService.class.getDeclaredMethod("stringConsumerProperties",String.class);method.setAccessible(true);
        var legacy=(Properties)method.invoke(service,"hpsf-live");
        assertEquals(Integer.toString(settings.maxPollRecords()),legacy.getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
        assertEquals(org.apache.kafka.common.serialization.StringDeserializer.class.getName(),legacy.getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        String scope="cvd-related-gateway";
        var readerMethod=FeedGatewayService.class.getDeclaredMethod("stringObjectConsumerProperties",String.class);readerMethod.setAccessible(true);
        var props=(Properties)readerMethod.invoke(service,"state");props.put("schema.registry.url","mock://"+scope);props.put("auto.register.schemas",false);
        var config=new HashMap<String,Object>();props.forEach((k,v)->config.put((String)k,v));
        var bindings=Map.of(settings.esFootprintStrikeTopic(),com.optionsedge.contracts.cvd.CvdRelatedSchemas.FOOTPRINT_STRIKE,
                settings.esCvdSpxLevelsTopic(),com.optionsedge.contracts.cvd.CvdRelatedSchemas.ALIGNED_LEVELS,
                settings.esOpenDirectionForecastTopic(),com.optionsedge.contracts.cvd.CvdRelatedSchemas.OPEN_DIRECTION_FORECAST,
                settings.esOpenDirectionOutcomeTopic(),com.optionsedge.contracts.cvd.CvdRelatedSchemas.OPEN_DIRECTION_OUTCOME,
                settings.esOpenDirectionStatusTopic(),com.optionsedge.contracts.cvd.CvdRelatedSchemas.OPEN_DIRECTION_STATUS);
        try(var writer=new KafkaAvroSerializer();var reader=new CanonicalModelDeserializer()) {
            writer.configure(config,false);reader.configure(config,false);
            for(var binding:bindings.entrySet()) {
                assertEquals(binding.getValue(),service.cvdAvroBindings().get(binding.getKey()));
                MockSchemaRegistry.getClientForScope(scope).register(binding.getKey()+"-value",binding.getValue());
                JsonNode model=populated(binding.getValue());String json=model.toString();
                assertEquals(json,reader.deserialize(binding.getKey(),json.getBytes(StandardCharsets.UTF_8)));
                byte[] wire=writer.serialize(binding.getKey(),CanonicalJsonAvro.record(binding.getValue(),model));
                same(model,new ObjectMapper().readTree(reader.deserialize(binding.getKey(),wire)),true);
            }
            assertNull(reader.deserialize(settings.esCvdSpxLevelsTopic(),null));
            assertThrows(Exception.class,()->reader.deserialize(settings.esCvdBarsTopic(),null));
        } finally {MockSchemaRegistry.dropScope(scope);}
    }

    @Test void derivedReadersAcceptRetainedJsonAndCanonicalAvroWithoutSchemaFallback() throws Exception {
        var settings = new GatewaySettings();
        var service = new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        var method = FeedGatewayService.class.getDeclaredMethod("stringObjectConsumerProperties", String.class);
        method.setAccessible(true);
        String scope = "cvd-derived-reader-closure";
        var props = (Properties) method.invoke(service, "state");
        props.put("schema.registry.url", "mock://" + scope);
        props.put("auto.register.schemas", false);
        var config = new HashMap<String, Object>();
        props.forEach((k, v) -> config.put((String) k, v));
        var models = Map.of(settings.esGexSpxAlignedTopic(), """
                {"recordType":"book","source":"ES_ON_SPX","symbol":"SPX","expiry":"20261019",
                 "settlementType":"PM","basisState":"FRESH","basis":25.25,"basisMeasuredAtMs":1000,
                 "emitEventTimeMs":2000,"dataEventTimeMs":1000,"upstreamHeartbeatEventMs":1000,
                 "upstreamHealth":"HEALTHY","buckets":[{"spxStrike":7800,"esNetGexSum":-123.5,
                 "contributorCount":1,"truncated":false,"contributors":[{"esStrike":7825,"netGex":-123.5}]}]}
                """, settings.shortPremiumRecommendationTopic(), """
                {"type":"short-premium-recommendation","trade_id":"paper-1","emitted_at":"2026-10-19T14:00:00Z",
                 "symbol":"SPX","expiry":"20261019","side":"bull_put","short_strike":7800.25,
                 "long_strike":7795.25,"wing":5.0,"target_credit":2.55,"short_delta":-0.3,
                 "confidence":0.8,"conviction_basis":null,"rationale":"fixture","mode":"PAPER"}
                """);
        var mapper = new ObjectMapper();
        try (var writer = new KafkaAvroSerializer(); var reader = new CanonicalModelDeserializer()) {
            writer.configure(config, false);
            reader.configure(config, false);
            for (var item : models.entrySet()) {
                String topic = item.getKey();
                Schema schema = service.cvdAvroBindings().get(topic);
                assertNotNull(schema);
                MockSchemaRegistry.getClientForScope(scope).register(topic + "-value", schema);
                JsonNode model = mapper.readTree(item.getValue());
                String legacy = model.toString();
                assertEquals(legacy, reader.deserialize(topic, legacy.getBytes(StandardCharsets.UTF_8)));
                byte[] frame = writer.serialize(topic, CanonicalJsonAvro.record(schema, model));
                assertEquals(0, frame[0]);
                String decoded = reader.deserialize(topic, frame);
                assertEquals(legacy, decoded); // Exact API order and numeric spelling, including 5.0.
                assertEquals(legacy, reader.deserialize(topic, legacy.getBytes(StandardCharsets.UTF_8)));
                String other = models.keySet().stream().filter(key -> !key.equals(topic)).findFirst().orElseThrow();
                assertThrows(org.apache.kafka.common.errors.SerializationException.class, () -> reader.deserialize(other, frame));
                assertThrows(org.apache.kafka.common.errors.SerializationException.class, () -> reader.deserialize(topic, new byte[] {0, 0, 0}));
                assertThrows(org.apache.kafka.common.errors.SerializationException.class, () -> reader.deserialize(topic, null));
            }
        } finally {
            MockSchemaRegistry.dropScope(scope);
        }
    }

    @Test void runScopedAlignedBookReplayUsesTheSameCanonicalBinding() throws Exception {
        var settings = new GatewaySettings();
        var service = new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        String topic = app.feedgateway.mtsession.gateway.ReplayTopicResolver.toReplayTopic(settings.esGexSpxAlignedTopic(), "fixture-run");
        var params = new app.feedgateway.mtsession.gateway.ReplayParams("app:fixture", "SPX", "20261019", 1000L, 2000L, 10, "fixture-run");
        var props = service.replayConsumerProps("app:fixture", false, params, Map.of(topic, "es-gex"));
        assertEquals(Map.of(topic, com.optionsedge.contracts.cvd.CvdRelatedSchemas.ALIGNED_GEX_BOOK_EXTENDED),
                props.get("optionsedge.canonical.avro.bindings"));
        assertEquals(Set.of(topic), props.get("optionsedge.canonical.avro.retained-json-topics"));
        assertEquals("false", props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertEquals("false", props.get(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG));
        assertEquals(Integer.toString(Math.min(settings.maxPollRecords(), 8)), props.getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
        assertEquals(settings.groupIdBase() + "-replay-app:fixture-str", props.getProperty(ConsumerConfig.GROUP_ID_CONFIG));
        String scope = "aligned-book-run-replay";
        props.put("schema.registry.url", "mock://" + scope); props.put("auto.register.schemas", false);
        var config = new HashMap<String, Object>(); props.forEach((k, v) -> config.put((String) k, v));
        Schema schema = com.optionsedge.contracts.cvd.CvdRelatedSchemas.ALIGNED_GEX_BOOK_EXTENDED;
        String json = "{\"recordType\":\"book\",\"source\":\"ES_ON_SPX\",\"symbol\":\"SPX\",\"expiry\":\"20261019\","
                + "\"settlementType\":\"PM\",\"basisState\":\"INIT\",\"basis\":null,\"basisMeasuredAtMs\":null,"
                + "\"emitEventTimeMs\":1000,\"dataEventTimeMs\":null,\"upstreamHeartbeatEventMs\":null,\"upstreamHealth\":\"UNAVAILABLE\",\"buckets\":[]}";
        try (var writer = new KafkaAvroSerializer(); var reader = new CanonicalModelDeserializer()) {
            MockSchemaRegistry.getClientForScope(scope).register(topic + "-value", schema);
            writer.configure(config, false); reader.configure(config, false);
            byte[] frame = writer.serialize(topic, CanonicalJsonAvro.record(schema, new ObjectMapper().readTree(json)));
            assertEquals(json, reader.deserialize(topic, json.getBytes(StandardCharsets.UTF_8)));
            assertEquals(json, reader.deserialize(topic, frame));
            assertThrows(org.apache.kafka.common.errors.SerializationException.class,
                    () -> reader.deserialize(topic, new byte[] {'[', ']'}));
            assertThrows(org.apache.kafka.common.errors.SerializationException.class,
                    () -> reader.deserialize(topic, new byte[] {0, 0, 0}));
        } finally { MockSchemaRegistry.dropScope(scope); }
    }

    @Test void eavCacheDecoderKeepsExactEngineModelsAndRefusesWrongFramedSchemas() throws Exception {
        var settings=new GatewaySettings();var mapper=new ObjectMapper();
        var service=new FeedGatewayService(settings,mapper,new HpsfGatewayViewMapper(),null);
        var method=FeedGatewayService.class.getDeclaredMethod("stringObjectConsumerProperties",String.class);method.setAccessible(true);
        var properties=(Properties)method.invoke(service,"state");
        String topic=settings.earlyAuctionVerdictTopic(),scope="eav-gateway-reader";
        var schema=com.optionsedge.contracts.cvd.CvdRelatedSchemas.EARLY_AUCTION_LEDGER;
        assertEquals(schema,service.cvdAvroBindings().get(topic));
        assertEquals(CanonicalModelDeserializer.class,properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals("read_committed",properties.get(ConsumerConfig.ISOLATION_LEVEL_CONFIG));
        assertEquals("false",properties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        properties.put("schema.registry.url","mock://"+scope);properties.put("auto.register.schemas",false);
        var config=new HashMap<String,Object>();properties.forEach((key,value)->config.put((String)key,value));
        var configured=(Class<?>)properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        var registry=MockSchemaRegistry.getClientForScope(scope);registry.register(topic+"-value",schema);
        try(var reader=configured.asSubclass(org.apache.kafka.common.serialization.Deserializer.class).getDeclaredConstructor().newInstance();
            var writer=new com.optionsedge.contracts.avro.CanonicalAvroSerializer(registry);
            var input=getClass().getResourceAsStream("/cvd/eav-engine-emissions.json")) {
            reader.configure(config,false);writer.configure(config,false);
            var models=mapper.readTree(input).path("models");assertEquals(6,models.size());long offset=0;
            for(var model:models) {
                String expected=model.toString();
                byte[] avro=writer.serialize(topic,CanonicalJsonAvro.record(schema,model));
                assertEquals(0,avro[0]);
                for(byte[] wire:List.of(expected.getBytes(StandardCharsets.UTF_8),avro,expected.getBytes(StandardCharsets.UTF_8))) {
                    String decoded=(String)reader.deserialize(topic,wire);assertEquals(expected,decoded);
                    assertEquals(model.path("tradeDate").asText(),service.validateEarlyAuctionVerdict(decoded));
                    service.onEarlyAuctionVerdictRecord(decoded,offset++);
                }
                assertTrue(service.earlyAuctionVerdictPage(null,-1,100).events().stream().anyMatch(event->event.json().equals(expected)));
                assertThrows(org.apache.kafka.common.errors.SerializationException.class,
                        ()->reader.deserialize(settings.esCvdBarsTopic(),avro));
            }
            assertThrows(org.apache.kafka.common.errors.SerializationException.class,()->reader.deserialize(topic,null));
            assertThrows(org.apache.kafka.common.errors.SerializationException.class,()->reader.deserialize(topic,new byte[]{0,0,0}));
        } finally {MockSchemaRegistry.dropScope(scope);}
    }

    private static List<String> fieldNames(JsonNode node) {
        var names=new ArrayList<String>();node.fieldNames().forEachRemaining(names::add);return names;
    }

    private static JsonNode populated(Schema schema) {
        var json=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
        String payload=schema.getProp("optionsedge.api-model-field");if(payload!=null)return populated(schema.getField(payload).schema());
        String scalar=schema.getProp("optionsedge.api-scalar");
        if(scalar!=null)return switch(scalar) {
            case "statistic","decimal-number" -> json.numberNode(new java.math.BigDecimal("7800.25"));
            case "decimal-text","decimal-lexeme" -> json.textNode("7800.25");
            default -> throw new AssertionError("Unsupported API scalar "+scalar);
        };
        if("true".equals(schema.getProp("optionsedge.api-ordered-map")))
            return json.objectNode().set("key",populated(schema.getElementType().getField("value").schema()));
        if("v1".equals(schema.getProp("optionsedge.verbatim-evidence")))return json.objectNode().put("evidence",-0.0);
        if(schema.getType()==Schema.Type.UNION)return populated(schema.getTypes().stream().filter(t->t.getType()!=Schema.Type.NULL).findFirst().orElseThrow());
        if(schema.getLogicalType() instanceof org.apache.avro.LogicalTypes.Decimal)return json.numberNode(new java.math.BigDecimal("7800.25"));
        return switch(schema.getType()) {
            case RECORD -> {var n=json.objectNode();for(Schema.Field f:schema.getFields())if(!f.name().equals(schema.getProp("optionsedge.api-presence-field")))n.set(f.name(),populated(f.schema()));yield n;}
            case ARRAY -> json.arrayNode().add(populated(schema.getElementType()));
            case MAP -> json.objectNode().set("key",populated(schema.getValueType()));
            case LONG -> json.numberNode(9007199254740993L);
            case INT -> json.numberNode(1);
            case STRING -> json.textNode("sample");
            case BOOLEAN -> json.booleanNode(true);
            case DOUBLE -> json.numberNode(0.5);
            default -> throw new AssertionError(schema);
        };
    }

    private static void same(JsonNode expected,JsonNode actual,boolean ordered) {
        if(expected.isNumber()){assertTrue(actual.isNumber());assertEquals(0,expected.decimalValue().compareTo(actual.decimalValue()));return;}
        if(expected.isObject()){if(ordered)assertEquals(fieldNames(expected),fieldNames(actual));assertEquals(expected.size(),actual.size());expected.fields().forEachRemaining(e->{assertTrue(actual.has(e.getKey()));same(e.getValue(),actual.get(e.getKey()),ordered);});}
        else if(expected.isArray()){assertEquals(expected.size(),actual.size());for(int i=0;i<expected.size();i++)same(expected.get(i),actual.get(i),ordered);}
        else assertEquals(expected,actual);
    }
}
