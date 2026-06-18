package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReplayTopicResolverTest {

    @Test
    void insertsReplayRunIdAfterTheNamespaceSegment() {
        assertEquals("options.replay.r-1.databento.display",
                ReplayTopicResolver.toReplayTopic("options.databento.display", "r-1"));
        assertEquals("options.replay.r-1.databento.strike-flow",
                ReplayTopicResolver.toReplayTopic("options.databento.strike-flow", "r-1"));
        assertEquals("underlying.replay.r-1.es.trades",
                ReplayTopicResolver.toReplayTopic("underlying.es.trades", "r-1"));
    }

    @Test
    void resultIsAlwaysInTheReplayNamespace() {
        String resolved = ReplayTopicResolver.toReplayTopic("options.databento.pace", "abc");
        org.junit.jupiter.api.Assertions.assertTrue(resolved.startsWith("options.replay."));
    }

    @Test
    void rejectsBlankOrNamespacelessInput() {
        assertThrows(IllegalArgumentException.class, () -> ReplayTopicResolver.toReplayTopic("", "r-1"));
        assertThrows(IllegalArgumentException.class, () -> ReplayTopicResolver.toReplayTopic("options.databento.display", ""));
        assertThrows(IllegalArgumentException.class, () -> ReplayTopicResolver.toReplayTopic("nodot", "r-1"));
    }
}
