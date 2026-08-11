package app.feedgateway.selleractivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Regression coverage for the OTHER async endpoint on this gateway, which the stock-gex work changed the
 * environment of without changing a line of its code.
 *
 * <p>{@code /api/seller-activity} holds one of four aggregation permits until its response has finished
 * being WRITTEN, so that concurrent parse+serialise+write stays bounded under slow-client backpressure.
 * Until now the container's 30-second async deadline was a second, invisible bound on how long that
 * permit could be held. {@code spring.mvc.async.request-timeout=-1} — which the stock-gex SSE proxy
 * requires, and which is necessarily global — removes it.
 *
 * <p>So the permit release has to be correct on its own, for every way a write can end. Four stuck
 * clients that never gave their permits back would wedge this endpoint permanently, and nothing would
 * appear in any log: callers would simply start receiving 503s forever.
 */
class SellerActivityStreamReleaseTest {

    private static SellerActivityController controller(int slots) {
        FeedGatewayService service = mock(FeedGatewayService.class);
        when(service.cachedSellerActivitySnapshot(anyString(), anyString()))
                .thenReturn("{\"symbol\":\"SPX\",\"expiry\":\"2026-08-14\",\"buckets\":[]}");
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "tester"));
        return new SellerActivityController(service, auth, new ObjectMapper(), slots);
    }

    @Test
    void aCompletedWriteReturnsItsAggregationSlot() throws Exception {
        SellerActivityController controller = controller(1);

        ResponseEntity<StreamingResponseBody> res =
                controller.sellerActivity("SPX", "2026-08-14", null, null, "Bearer t");

        assertEquals(200, res.getStatusCode().value());
        assertEquals(0, controller.aggregationSlots.availablePermits(),
                "the permit is held until the response has actually been written");
        res.getBody().writeTo(new ByteArrayOutputStream());
        assertEquals(1, controller.aggregationSlots.availablePermits());
    }

    @Test
    void aClientThatGoesAwayMidWriteStillReturnsItsAggregationSlot() throws Exception {
        // THE case that used to be covered by the container's async deadline. With that deadline gone,
        // a permit leaked here is a permit this endpoint never gets back — four of them and it is dead.
        SellerActivityController controller = controller(1);
        ResponseEntity<StreamingResponseBody> res =
                controller.sellerActivity("SPX", "2026-08-14", null, null, "Bearer t");

        OutputStream brokenPipe = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        assertThrows(IOException.class, () -> res.getBody().writeTo(brokenPipe));
        assertEquals(1, controller.aggregationSlots.availablePermits(),
                "a disconnected client costs one response, never a permanent slot");
    }

    @Test
    void anUnexpectedRuntimeFaultMidWriteAlsoReturnsTheSlot() throws Exception {
        SellerActivityController controller = controller(1);
        ResponseEntity<StreamingResponseBody> res =
                controller.sellerActivity("SPX", "2026-08-14", null, null, "Bearer t");

        OutputStream exploding = new OutputStream() {
            @Override
            public void write(int b) {
                throw new IllegalStateException("container went away");
            }

            @Override
            public void write(byte[] b, int off, int len) {
                throw new IllegalStateException("container went away");
            }
        };

        assertThrows(RuntimeException.class, () -> res.getBody().writeTo(exploding));
        assertEquals(1, controller.aggregationSlots.availablePermits());
    }

    /**
     * The socket-level backstop is the only thing that can end a write to a client that has stopped
     * reading but not disconnected, now that no async clock will.
     *
     * <p>Asserted on the EFFECTIVE, BOUND value rather than on the text of a properties file: a
     * commented-out line, a misspelled key, or a property that Boot no longer binds would all leave
     * a source-text assertion green while the backstop was gone. {@code connection-timeout} is an
     * I/O-operation budget on the connector (how long one blocking read or write may take), which is
     * exactly the shape needed — not a general idle-connection deadline.
     */
    @Test
    void theSocketTimeoutThatMakesAStuckWriteFailIsActuallyBOUND() {
        org.springframework.boot.autoconfigure.web.ServerProperties bound =
                new org.springframework.boot.autoconfigure.web.ServerProperties();
        org.springframework.boot.context.properties.bind.Binder binder =
                new org.springframework.boot.context.properties.bind.Binder(
                        org.springframework.boot.context.properties.source.ConfigurationPropertySources.from(
                                new org.springframework.core.env.MapPropertySource("under-test",
                                        loadApplicationProperties())));
        binder.bind("server", org.springframework.boot.context.properties.bind.Bindable.ofInstance(bound));

        java.time.Duration connectionTimeout = bound.getTomcat().getConnectionTimeout();
        assertNotNull(connectionTimeout,
                "without a connector timeout, a client that stops reading holds an async thread and an "
                        + "aggregation permit for as long as it likes");
        assertTrue(connectionTimeout.toMillis() > 0 && connectionTimeout.toMillis() <= 60_000L,
                "the backstop must be a real, bounded budget; got " + connectionTimeout);
    }

    /** And the setting that made that backstop load-bearing, also asserted as a bound value. */
    @Test
    void theAsyncDeadlineIsActuallyRemoved() {
        org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties mvc =
                new org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties();
        new org.springframework.boot.context.properties.bind.Binder(
                org.springframework.boot.context.properties.source.ConfigurationPropertySources.from(
                        new org.springframework.core.env.MapPropertySource("under-test",
                                loadApplicationProperties())))
                .bind("spring.mvc", org.springframework.boot.context.properties.bind.Bindable.ofInstance(mvc));

        java.time.Duration requestTimeout = mvc.getAsync().getRequestTimeout();
        assertNotNull(requestTimeout, "the property must be present at all");
        assertTrue(requestTimeout.isNegative(),
                "a non-negative async deadline tears down healthy SSE streams on a clock; got "
                        + requestTimeout);
    }

    private static java.util.Map<String, Object> loadApplicationProperties() {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.InputStream in =
                     Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        properties.forEach((k, v) -> map.put(String.valueOf(k), v));
        return map;
    }
}
