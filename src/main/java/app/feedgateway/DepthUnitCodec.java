package app.feedgateway;

import com.optionsedge.contracts.rithmic.RithmicDepthV2Hashes;
import com.optionsedge.contracts.rithmic.RithmicDepthV2Schemas;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;

/** Strict, fixed-schema source boundary. Destination offsets are deliberately not identities. */
final class DepthUnitCodec {
    private static final long MAX_SAFE = 9007199254740991L;
    record Verified(GenericRecord unit, byte[] datum) {}
    private DepthUnitCodec() {}

    static Verified decode(String key, byte[] wire) {
        if (!RithmicDepthV2Schemas.KEY.equals(key)) throw new IllegalArgumentException("depth instrument key");
        if (wire == null || wire.length < 6 || wire.length > DepthUnitJournal.MAX_UNIT + 5)
            throw new IllegalArgumentException("depth wire length");
        if (wire[0] != 0 || ByteBuffer.wrap(wire, 1, 4).getInt() != RithmicDepthV2Schemas.SCHEMA_ID)
            throw new IllegalArgumentException("depth schema identity");
        DepthDatumBounds.check(wire, RithmicDepthV2Schemas.UNIT);
        try {
            var decoder = DecoderFactory.get().binaryDecoder(wire, 5, wire.length - 5, null);
            var record = new GenericDatumReader<GenericRecord>(RithmicDepthV2Schemas.UNIT).read(null, decoder);
            if (!decoder.isEnd()) throw new IllegalArgumentException("trailing depth datum");
            if (!RithmicDepthV2Hashes.verifySource(record)) throw new IllegalArgumentException("depth source hash");
            if (!Integer.valueOf(2).equals(record.get("schemaVersion")) || !"Rithmic".contentEquals((CharSequence) record.get("provider")) ||
                !"CME".contentEquals((CharSequence) record.get("exchange")) || !"ESZ6".contentEquals((CharSequence) record.get("contract")))
                throw new IllegalArgumentException("depth instrument identity");
            for (String name : new String[]{"system", "producer", "epoch"}) {
                String value = record.get(name).toString();
                if (value.isEmpty() || value.length() > 128 || value.contains("|") || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value))
                    throw new IllegalArgumentException("depth source identity");
            }
            for (String name : new String[]{"K", "observedAtMs", "observedThroughMs", "emittedAtMs", "trustRevision"}) exact(record.get(name));
            return new Verified(record, Arrays.copyOfRange(wire, 5, wire.length));
        } catch (IOException | org.apache.avro.AvroRuntimeException e) {
            throw new IllegalArgumentException("invalid depth datum", e);
        }
    }
    private static void exact(Object value) {
        if (!(value instanceof Long number) || number < 0 || number > MAX_SAFE)
            throw new IllegalArgumentException("depth exact integer");
    }
}
