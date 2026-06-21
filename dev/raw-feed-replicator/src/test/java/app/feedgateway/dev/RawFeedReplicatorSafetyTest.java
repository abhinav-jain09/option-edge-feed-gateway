package app.feedgateway.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;

/**
 * The replicator must be strictly READ-ONLY against the source (production) cluster: it never commits
 * offsets, never joins a consumer group (manual assign), and never produces back to the source. These
 * are the guarantees that make it safe to tail production live data.
 */
class RawFeedReplicatorSafetyTest {

    private static final Path SOURCE =
            Path.of("src/main/java/app/feedgateway/dev/RawFeedReplicator.java");

    @Test
    void sourceConsumerHasAutoCommitDisabled() throws Exception {
        Method m = RawFeedReplicator.class.getDeclaredMethod("consumerProps", String.class);
        m.setAccessible(true);
        Properties props = (Properties) m.invoke(null, "source-broker:9092");
        assertEquals("false", props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG),
                "replicator must never auto-commit offsets to the source");
    }

    @Test
    void neverCommitsOffsetsToSource() throws Exception {
        String src = Files.readString(SOURCE);
        assertFalse(src.contains(".commitSync"), "must not commitSync to the source");
        assertFalse(src.contains(".commitAsync"), "must not commitAsync to the source");
        assertFalse(src.contains(".commitOffsets"), "must not commit offsets to the source");
    }

    @Test
    void usesManualAssignmentNotGroupSubscription() throws Exception {
        String src = Files.readString(SOURCE);
        assertTrue(src.contains(".assign("), "must use manual partition assignment");
        assertFalse(src.contains(".subscribe("), "must not subscribe (would join a consumer group on the source)");
    }

    @Test
    void declaresAutoCommitFalseInConfig() throws Exception {
        String src = Files.readString(SOURCE);
        assertTrue(src.contains("ENABLE_AUTO_COMMIT_CONFIG"), "explicitly configures auto-commit");
        assertTrue(src.contains("\"false\""), "auto-commit is set to false");
    }
}
