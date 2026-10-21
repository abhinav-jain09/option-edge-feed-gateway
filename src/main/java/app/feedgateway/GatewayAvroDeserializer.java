package app.feedgateway;

import com.optionsedge.contracts.hpsf.UnderlyingPriceAvro;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * The value deserializer of every Avro consumer the gateway builds. Generic Avro records for every topic except
 * {@code underlying.spx.price} (live name and its {@code underlying.replay.<runId>.} name), which is read ONLY by
 * {@link UnderlyingPriceAvro.EventDeserializer} and reaches the gateway as a typed {@code UnderlyingPriceEvent}.
 * <p>
 * A TOLERANT topic (the technical-analysis relays' and the converted market-structure topics, whose relays count what they refuse) never
 * fails a poll: a value that is not Confluent-framed (first byte not 0, e.g. retained JSON), whose datum does not decode, or whose
 * schema id the registry does not know reaches the relay as {@link #UNDECODABLE} to be counted and dropped, so one poison record cannot
 * stall the consumer that carries every other topic. An unknown schema id is remembered for {@link #UNKNOWN_ID_BACKOFF_MS}, so the
 * registry is asked once per backoff and not once per record; a registry that is unreachable still fails the poll (nothing is lost).
 */
public final class GatewayAvroDeserializer implements Deserializer<Object> {
    /** Consumer property naming the live spx price topic. */
    public static final String SPX_PRICE_TOPIC = "gateway.spx.price.topic";
    /** Consumer property: the comma-separated tolerant topics. */
    public static final String TOLERANT_TOPICS = "gateway.tolerant.topics";
    /** The marker a tolerant topic yields for a value it cannot decode. */
    public static final Object UNDECODABLE = new Object();
    static final long UNKNOWN_ID_BACKOFF_MS = 60_000L;
    private static final int UNKNOWN_IDS_MAX = 64;

    private final KafkaAvroDeserializer generic = new KafkaAvroDeserializer();
    private final UnderlyingPriceAvro.EventDeserializer spx = new UnderlyingPriceAvro.EventDeserializer();
    private String liveTopic;
    private String replayPrefix;
    private String replaySuffix;
    private Set<String> tolerant = Set.of();
    /** Schema id -> the time until which it is treated as unknown; touched by the consumer thread only. */
    private final Map<Integer, Long> unknownIds = new java.util.LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Long> eldest) {
            return size() > UNKNOWN_IDS_MAX;   // bounded: the oldest unknown id leaves, the others keep their backoff
        }
    };

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        generic.configure(configs, isKey);
        spx.configure(UnderlyingPriceAvro.deserializerConfig((String) configs.get("schema.registry.url")), isKey);
        liveTopic = (String) configs.get(SPX_PRICE_TOPIC);
        int dot = liveTopic.indexOf('.');
        replayPrefix = liveTopic.substring(0, dot) + ".replay.";
        replaySuffix = liveTopic.substring(dot);
        Object topics = configs.get(TOLERANT_TOPICS);
        tolerant = topics == null || topics.toString().isEmpty() ? Set.of() : new HashSet<>(java.util.List.of(topics.toString().split(",")));
    }

    @Override
    public Object deserialize(String topic, byte[] data) {
        boolean spxTopic = liveTopic.equals(topic) || topic.startsWith(replayPrefix) && topic.endsWith(replaySuffix);
        if (spxTopic) {
            return spx.deserialize(topic, data);
        }
        return data != null && tolerant.contains(topic) ? deserializeTolerant(topic, data) : generic.deserialize(topic, data);
    }

    private Object deserializeTolerant(String topic, byte[] data) {
        if (data.length < 5 || data[0] != 0) {
            return UNDECODABLE;
        }
        int id = ByteBuffer.wrap(data, 1, 4).getInt();
        Long until = unknownIds.get(id);
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            return UNDECODABLE;
        }
        try {
            return generic.deserialize(topic, data);
        } catch (SerializationException e) {
            RestClientException registry = null;
            for (Throwable c = e.getCause(); c != null && registry == null; c = c.getCause() == c ? null : c.getCause()) {
                if (c instanceof RestClientException r) {
                    registry = r;   // however deeply the client wrapped it
                } else if (c instanceof java.io.IOException && !(c instanceof java.io.EOFException)) {
                    throw e;   // connect/read/DNS/TLS/HTTP failures reach the registry as an IOException: infrastructure, never a poison record (a corrupt Avro body is an EOF or a runtime Avro error): fail the poll and retry
                }
            }
            if (registry != null) {
                if (registry.getStatus() != 404) {
                    throw e;   // the registry is failing, not the record: fail the poll and retry rather than lose data
                }
                unknownIds.put(id, now + UNKNOWN_ID_BACKOFF_MS);
            }
            return UNDECODABLE;
        }
    }

    @Override
    public void close() {
        generic.close();
        spx.close();
    }
}
