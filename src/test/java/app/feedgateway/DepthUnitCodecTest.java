package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.rithmic.RithmicDepthV2Schemas;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DepthUnitCodecTest {
    private byte[] wire() throws Exception {
        try (var input = getClass().getResourceAsStream("/depth-v2-source-fixture.json")) {
            var data = new ObjectMapper().readTree(input);
            byte[] datum = Base64.getDecoder().decode(data.get("sourceDatumBase64").asText());
            return ByteBuffer.allocate(datum.length + 5).put((byte)0).putInt(RithmicDepthV2Schemas.SCHEMA_ID).put(datum).array();
        }
    }
    @Test void independentPythonAvroFixtureVerifiesWithoutRegistryOrOffsetIdentity() throws Exception {
        byte[] wire = wire();var verified = DepthUnitCodec.decode("CME|ESZ6", wire);
        assertEquals("ESZ6", verified.unit().get("contract").toString());
        assertArrayEquals(Arrays.copyOfRange(wire,5,wire.length),verified.datum());
        wire[5]=0;assertEquals(2,verified.unit().get("schemaVersion"));
    }
    @Test void wrongKeySchemaMagicTrailingDataAndTamperedHashFailBeforeAdmission() throws Exception {
        byte[] wire = wire();
        assertThrows(IllegalArgumentException.class,()->DepthUnitCodec.decode("CME|MESZ6",wire));
        byte[] magic=wire.clone();magic[0]=1;
        assertThrows(IllegalArgumentException.class,()->DepthUnitCodec.decode("CME|ESZ6",magic));
        byte[] schema=wire.clone();schema[4]++;
        assertThrows(IllegalArgumentException.class,()->DepthUnitCodec.decode("CME|ESZ6",schema));
        byte[] trailing=Arrays.copyOf(wire,wire.length+1);
        assertThrows(IllegalArgumentException.class,()->DepthUnitCodec.decode("CME|ESZ6",trailing));
        byte[] changed=wire.clone();changed[changed.length-1]^=1;
        assertThrows(IllegalArgumentException.class,()->DepthUnitCodec.decode("CME|ESZ6",changed));
        assertThrows(IllegalArgumentException.class,()->DepthUnitCodec.decode("CME|ESZ6",Arrays.copyOf(wire,wire.length-1)));
    }
}
