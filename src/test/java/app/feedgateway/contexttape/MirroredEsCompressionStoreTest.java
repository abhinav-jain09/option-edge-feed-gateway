package app.feedgateway.contexttape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.GatewaySettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class MirroredEsCompressionStoreTest {
    @Test void validMirroredEnvelopeIsServedVerbatim() {
        GatewaySettings settings = settings(true, 180_000L);
        MirroredEsCompressionStore store = new MirroredEsCompressionStore(settings, new ObjectMapper());
        long now = System.currentTimeMillis();
        String body = envelope(now, MirroredEsCompressionStore.ARTIFACT_SHA, true);

        store.accept(new ConsumerRecord<>("es.context-tape.es-compression.current", 0, 1L,
                "SPX|ES_ANALYSIS", body), now);

        ContextTapeUpstream.SessionResponse response = store.esCompression();
        assertEquals(200, response.status());
        assertEquals(body, new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1L, store.acceptedCount());
        store.close();
    }

    @Test void wrongArtifactIsRejectedAndStaleMirrorFailsClosed() {
        GatewaySettings settings = settings(true, 60_000L);
        MirroredEsCompressionStore store = new MirroredEsCompressionStore(settings, new ObjectMapper());
        long now = System.currentTimeMillis();
        store.accept(new ConsumerRecord<>("es.context-tape.es-compression.current", 0, 1L,
                "SPX|ES_ANALYSIS", envelope(now, "wrong", true)), now);
        assertEquals(503, store.esCompression().status());
        assertEquals(1L, store.rejectedCount());

        store.accept(new ConsumerRecord<>("es.context-tape.es-compression.current", 0, 2L,
                "SPX|ES_ANALYSIS",
                envelope(now - 120_000L, MirroredEsCompressionStore.ARTIFACT_SHA, true)), now);
        assertEquals(503, store.esCompression().status());
        store.close();
    }

    @Test void aDifferentKeyCannotReplaceTheProjection() {
        GatewaySettings settings = settings(true, 180_000L);
        MirroredEsCompressionStore store = new MirroredEsCompressionStore(settings, new ObjectMapper());
        long now = System.currentTimeMillis();
        store.accept(new ConsumerRecord<>("es.context-tape.es-compression.current", 0, 1L,
                "OTHER", envelope(now, MirroredEsCompressionStore.ARTIFACT_SHA, true)), now);
        assertEquals(503, store.esCompression().status());
        assertEquals(0L, store.acceptedCount());
        assertEquals(1L, store.rejectedCount());
        store.close();
    }

    @Test void aTombstoneClearsThePreviouslyAcceptedProjection() {
        GatewaySettings settings = settings(true, 180_000L);
        MirroredEsCompressionStore store = new MirroredEsCompressionStore(settings, new ObjectMapper());
        long now = System.currentTimeMillis();
        store.accept(new ConsumerRecord<>("es.context-tape.es-compression.current", 0, 1L,
                "SPX|ES_ANALYSIS", envelope(now, MirroredEsCompressionStore.ARTIFACT_SHA, true)), now);
        assertEquals(200, store.esCompression().status());

        store.accept(new ConsumerRecord<>("es.context-tape.es-compression.current", 0, 2L,
                "SPX|ES_ANALYSIS", null), now + 1);

        assertEquals(503, store.esCompression().status());
        assertEquals(1L, store.rejectedCount());
        store.close();
    }

    private static GatewaySettings settings(boolean mirrorEnabled, long maxAgeMs) {
        GatewaySettings settings = mock(GatewaySettings.class);
        when(settings.enabled()).thenReturn(false); // direct acceptance tests do not start Kafka
        when(settings.zeroDteEsChallengerMirrorEnabled()).thenReturn(mirrorEnabled);
        when(settings.zeroDteEsChallengerMirrorTopic()).thenReturn(
                "es.context-tape.es-compression.current");
        when(settings.zeroDteEsChallengerMirrorMaxAgeMs()).thenReturn(maxAgeMs);
        return settings;
    }

    private static String envelope(long generatedAtMs, String sha, boolean ready) {
        return "{\"schemaVersion\":\"zdce.es-challenger-view.1\","
                + "\"service\":\"zero-dte-es-challenger\","
                + "\"modelVersion\":\"zdce-es-challenger-v1\","
                + "\"authority\":\"SHADOW_NOT_FOR_TRADING\",\"source\":\"ES_ANALYSIS\","
                + "\"capability\":\"PRICE_VOLUME_ONLY\",\"artifactSha256\":\"" + sha + "\","
                + "\"generatedAtMs\":" + generatedAtMs + ",\"ready\":" + ready
                + ",\"points\":[]}";
    }
}
