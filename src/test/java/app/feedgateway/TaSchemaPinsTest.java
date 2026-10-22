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
 * The test schemas (src/test/resources/ta) are byte-identical copies of the contracts registry files (options-edge-contracts 0.36.0 for WaveSet and CycleSet version 2, 0.31.0 otherwise,
 * schemas-registry/ta, SHA-256 pinned there against the producers' resources). The gateway needs no contracts class for them: it renders
 * from the writer schema the registry returns. If a producer schema changes, the contracts pin, this copy and the fixtures change together.
 */
class TaSchemaPinsTest {
    private static final Map<String, String> SHA256 = new TreeMap<>();

    static {
        String[] pins = {
            "ClimaxEvent", "a91a92f21e8fbbdcf4fb364cab752eeccde2edae30484fb9a77075c150e8a564",
            "ControlCheckpoint", "8de4bb1b10281f18deaaa2ed8fe098046f0a469da5f9ec39db0e6686afd13f39",
            "CycleSet", "00fd1f9fb68b9aca1ee0e7622ea5e26184d490d2df5377c84562721e74e9ef7b",
            "DlqRecord", "1dbd98fbae1ccbe1ad9278c43905e7bbb24d2c94cf3acf6aeca8ef2413867a31",
            "FlowBar", "ee5260efdf2f48bcc7bcc4e4f712e2f8f1cf2d086ae404a2f4ae20dbaa83f8f1",
            "GeometryAnnotationSet", "7df6d75aab13cfa943ef3b7cd10a7fc3da78f7e54c24dcedba4664fec2b023a8",
            "GeometryUnavailable", "15dd96c7d3e97e62e029b67689ba4a53a471002cb32d19c13751fb5e39c91e46",
            "PatternAnnotationSet", "71f68ef6858c5bce3db89217b162d43541a945de3c77f8048333b1969493be28",
            "PatternUnavailable", "4600ad015dfb74d463ba95a9c09bad424fda27d277829ba8c5b1479d2beb3813",
            "PfBreakEvent", "6227ed2f980e92486949f6289ae15152b83abecef9574872ae23aa81dc288d00",
            "PfBreakVolumeCheck", "3b142f6a64791eb9932f013a3faea0ae2e0f220e22a1c579f707a273c32180be",
            "PfChartState", "bb92c7d82b8b1cab17652b7967590b65b76340a523928cbbf232d7e0526b043f",
            "PfColumn", "cb73baef44d1d0205568480b76f6bdf7184a2730db5b622c5ceeca3128f66849",
            "PfCountEvent", "0215ad1948a1a915ce6bd329cb3c48ed7e9f5f164bfeb515414da437441ff32d",
            "PfLineEvent", "5a61f1d40739d371b0b6bd5e60da164c57dc761757ca0e93c04ca8f9092c02ee",
            "PfMaCrossEvent", "dd69a0e33da280965b78f43b64961d1e0221885d341f1582f50fcff294d624f7",
            "PfMoneyFlowDivergenceEvent", "3fc2e51ff419d28a2ec247d21ca469b967f338eedb7528fade1fe0d76b56f1e6",
            "PfVolumeDivergenceEvent", "c5aafb63be8f81d688e0af41e4f193c5c694e79b0e0dff926d7a4e4d3e98980b",
            "RecoveryCheckpoint", "40cd5e2a82be26dc4b7550459a919d6bc9649f903e3d2f9eb894b2db27ac6262",
            "RecoveryPayload", "aec8e8512a33d120d4f64ae1f79c59f1ceb8d34cc38ebc7715f97f0259d295ac",
            "SequenceRecord", "9d2f86434bbc799118c9eb8913a59f1b3f67276006bad2bfd79f20404528a216",
            "SeriesCheckpoint", "9f0606d80e7bcf2847784d31380e9bf986be99e18ca13b9b2a6aa6c7f71309f5",
            "StructureToolsSet", "d7b13694069d53b46ce7aa91c1f6c484f379f2daf777f55417834f6208cf9a6e",
            "TimeframeCheckpoint", "9ad3fc99ac64650fa4dd5b30ffa8ccdb60d49d31030a6e6f73a5466f43fc8a44",
            "TtCheckpointChunk", "5909bd204ed7c79ca6cd33ae132219b040ec41e1e1cf0ddc1864bf604a60df91",
            "TtCheckpointManifest", "e0b7363d4306ba55b9c03d6855b2273204b566b61f3051640f827a30bb1dc8f8",
            "TtFamilyStatus", "b6a5aa1259cf8c9bb2bad0e9203dfb3bb2f8ef09c2c560453137f08c8bdd345a",
            "UiProjection", "e77f05135fdbf99b39cfae491c613c956b3605fe6156661d084821423ae448f5",
            "UiUnavailable", "7b4d518b830fc8b2ecb932f4728b733264c2c89d58de1acb1b731b90d79d39ba",
            "VolumeNodeSet", "c5495be1f6972a410e6bc518aa671a8a1e8b9bd6bff1d5dd9ed5488f8d800ed8",
            "WaveSet", "167d6adf36c95d1a58342c36b09fbd0297161fd9ac23acde1d67f12efb0aff33"
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

    /** The registered version 1 of WaveSet and CycleSet (contracts schemas-registry/ta/v1): a producer that has not been upgraded still writes it, and the relay renders it from its own writer schema. */
    @Test
    void theVersion1WaveAndCycleSchemasAreTheFrozenRegistryFiles() throws Exception {
        Map<String, String> v1 = Map.of("CycleSet", "48e70d6ff1aac5336ac083a5ff5607e341ac0e4f2a782520d3fe51badcc3b230",
                "WaveSet", "2d28adcf2ca2683383e6d2959651a03830a16da2b696f653062abb1319ca3644");
        try (var files = Files.list(Path.of("src", "test", "resources", "ta-v1"))) {
            assertEquals(v1.keySet(), files.map(p -> p.getFileName().toString().replace(".avsc", "")).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)));
        }
        for (Map.Entry<String, String> e : v1.entrySet()) {
            byte[] bytes = Files.readAllBytes(Path.of("src", "test", "resources", "ta-v1", e.getKey() + ".avsc"));
            assertEquals(e.getValue(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), e.getKey() + " version 1");
        }
    }
}
