package app.feedgateway;

import com.optionsedge.contracts.hpsf.UnderlyingPriceAvro;
import com.optionsedge.contracts.spotvolregime.SpotVolRegimeAvro;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * The value deserializer of every Avro consumer the gateway builds. Generic Avro records for every topic except
 * {@code underlying.spx.price} (live name and its {@code underlying.replay.<runId>.} name), which is read ONLY by
 * {@link UnderlyingPriceAvro.EventDeserializer} and reaches the gateway as a typed {@code UnderlyingPriceEvent}, and
 * {@code options.spx.spot-vol-regime.current}, which is read ONLY by {@link SpotVolRegimeAvro.SnapshotDeserializer}
 * and reaches the gateway as a typed {@code SpotVolRegimeSnapshot}.
 */
public final class GatewayAvroDeserializer implements Deserializer<Object> {
    /** Consumer property naming the live spx price topic. */
    public static final String SPX_PRICE_TOPIC = "gateway.spx.price.topic";
    /** Consumer property naming the spot-vol-regime CURRENT topic. */
    public static final String SPOT_VOL_REGIME_TOPIC = "gateway.spot.vol.regime.topic";

    private final KafkaAvroDeserializer generic = new KafkaAvroDeserializer();
    private final UnderlyingPriceAvro.EventDeserializer spx = new UnderlyingPriceAvro.EventDeserializer();
    private final SpotVolRegimeAvro.SnapshotDeserializer regime = new SpotVolRegimeAvro.SnapshotDeserializer();
    private String regimeTopic;
    private String liveTopic;
    private String replayPrefix;
    private String replaySuffix;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        generic.configure(configs, isKey);
        spx.configure(UnderlyingPriceAvro.deserializerConfig((String) configs.get("schema.registry.url")), isKey);
        regime.configure(SpotVolRegimeAvro.deserializerConfig((String) configs.get("schema.registry.url")), isKey);
        regimeTopic = (String) configs.get(SPOT_VOL_REGIME_TOPIC);
        liveTopic = (String) configs.get(SPX_PRICE_TOPIC);
        int dot = liveTopic.indexOf('.');
        replayPrefix = liveTopic.substring(0, dot) + ".replay.";
        replaySuffix = liveTopic.substring(dot);
    }

    @Override
    public Object deserialize(String topic, byte[] data) {
        if (regimeTopic != null && regimeTopic.equals(topic)) {
            return regime.deserialize(topic, data);
        }
        boolean spxTopic = liveTopic.equals(topic) || topic.startsWith(replayPrefix) && topic.endsWith(replaySuffix);
        return spxTopic ? spx.deserialize(topic, data) : generic.deserialize(topic, data);
    }

    @Override
    public void close() {
        generic.close();
        spx.close();
        regime.close();
    }
}
