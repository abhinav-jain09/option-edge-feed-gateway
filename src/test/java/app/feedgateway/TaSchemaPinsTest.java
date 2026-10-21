package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The test schemas (src/test/resources/ta) are byte-identical copies of the contracts registry files (options-edge-contracts 0.31.0,
 * schemas-registry/ta, SHA-256 pinned there against the producers' resources). The gateway needs no contracts class for them: it renders
 * from the writer schema the registry returns. If a producer schema changes, the contracts pin, this copy and the fixtures change together.
 */
class TaSchemaPinsTest {
    private static final Map<String, String> SHA256 = new TreeMap<>();

    static {
        String[] pins = {
            "ClimaxEvent", "494a9e8e45e50da4b64e056a820557b6261797349f32de1f59137facd11bf2a0",
            "ControlCheckpoint", "8de4bb1b10281f18deaaa2ed8fe098046f0a469da5f9ec39db0e6686afd13f39",
            "CycleSet", "9eb889a2bebeb740f4e9a257c9f18575d7cea9393bef4d9f3fb4d0ab4e7d3134",
            "DlqRecord", "1dbd98fbae1ccbe1ad9278c43905e7bbb24d2c94cf3acf6aeca8ef2413867a31",
            "FlowBar", "27d78217d39ab9e71caa7853216bd0dddcc0ee81e962fd092e6f89e4074d2b53",
            "GeometryAnnotationSet", "7df6d75aab13cfa943ef3b7cd10a7fc3da78f7e54c24dcedba4664fec2b023a8",
            "GeometryUnavailable", "15dd96c7d3e97e62e029b67689ba4a53a471002cb32d19c13751fb5e39c91e46",
            "PatternAnnotationSet", "71f68ef6858c5bce3db89217b162d43541a945de3c77f8048333b1969493be28",
            "PatternUnavailable", "4600ad015dfb74d463ba95a9c09bad424fda27d277829ba8c5b1479d2beb3813",
            "PfBreakEvent", "73d408eebd12f35485d67b6018f6ecafe1dcac3aa4c7df764a2b9875b62fb774",
            "PfBreakVolumeCheck", "63eb0a037dc995c77f161a4372f326139245e4fed34addd6523e9fa31b8f1e57",
            "PfChartState", "3cfe2d28efdfb4483e68d1fd6cebf3286fde589d43dda7bd79ae6d0828c0abe7",
            "PfColumn", "b5e1f57d81819c871ed1d5bf4251e222e6404b79d10b6ab487fb10883d94d52b",
            "PfCountEvent", "399448ac4f5326e519fb8d326d4d989d76a0b1c858679f5acf9260b11a2b544b",
            "PfLineEvent", "cb50dbdbe3b0a2104e4c0edf78546030ee8541e7ff539be7c3fe3dfaf747f458",
            "PfMaCrossEvent", "ed47a86569bff7bedcd72224cf142beb80ece4d91137d41928415a3caa99dc4c",
            "PfMoneyFlowDivergenceEvent", "0d95e7580df4c3d10a4c0ee39cec98b0f815b3c6a1800c0c5c70757f9a983821",
            "PfVolumeDivergenceEvent", "2ac15f0a2f5de7586b02067cb7be6a1835b7d1aeb24f9ea0b32a2385b5cfdbf9",
            "RecoveryCheckpoint", "40cd5e2a82be26dc4b7550459a919d6bc9649f903e3d2f9eb894b2db27ac6262",
            "RecoveryPayload", "aec8e8512a33d120d4f64ae1f79c59f1ceb8d34cc38ebc7715f97f0259d295ac",
            "SequenceRecord", "9d2f86434bbc799118c9eb8913a59f1b3f67276006bad2bfd79f20404528a216",
            "SeriesCheckpoint", "9f0606d80e7bcf2847784d31380e9bf986be99e18ca13b9b2a6aa6c7f71309f5",
            "StructureToolsSet", "d7b13694069d53b46ce7aa91c1f6c484f379f2daf777f55417834f6208cf9a6e",
            "TimeframeCheckpoint", "9ad3fc99ac64650fa4dd5b30ffa8ccdb60d49d31030a6e6f73a5466f43fc8a44",
            "TtCheckpointChunk", "5909bd204ed7c79ca6cd33ae132219b040ec41e1e1cf0ddc1864bf604a60df91",
            "TtCheckpointManifest", "e0b7363d4306ba55b9c03d6855b2273204b566b61f3051640f827a30bb1dc8f8",
            "TtFamilyStatus", "229ab30b2b398b08ff7d7a4671ca74d3603ca918e2955a300ec641c7008e49f8",
            "UiProjection", "e77f05135fdbf99b39cfae491c613c956b3605fe6156661d084821423ae448f5",
            "UiUnavailable", "7b4d518b830fc8b2ecb932f4728b733264c2c89d58de1acb1b731b90d79d39ba",
            "VolumeNodeSet", "ff7c43fc12aa67643d14b0750dc7e57ff308ee49cf3f9d2a6fe7bd235d7b80be",
            "WaveSet", "2d28adcf2ca2683383e6d2959651a03830a16da2b696f653062abb1319ca3644"
        };
        for (int i = 0; i < pins.length; i += 2) {
            SHA256.put(pins[i], pins[i + 1]);
        }
    }

    @Test
    void theTestSchemasAreTheContractsRegistryFiles() throws Exception {
        try (var files = Files.list(Path.of("src", "test", "resources", "ta"))) {
            assertEquals(SHA256.keySet(), files.map(p -> p.getFileName().toString().replace(".avsc", "")).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)));
        }
        for (Map.Entry<String, String> e : SHA256.entrySet()) {
            byte[] bytes = Files.readAllBytes(Path.of("src", "test", "resources", "ta", e.getKey() + ".avsc"));
            assertEquals(e.getValue(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), e.getKey());
        }
    }
}
