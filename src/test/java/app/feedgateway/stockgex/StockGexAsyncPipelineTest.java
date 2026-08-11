package app.feedgateway.stockgex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * The async pipeline, end to end over real HTTP, against the REAL Boot MVC configuration.
 *
 * <p>Every other test in this package proves a piece in isolation, and none of them can prove the claim
 * that actually carries the feature: that after Boot's own {@code WebMvcConfigurer}s have run, the MVC
 * async dispatcher is using {@link StockGexAsyncConfig}'s pool and not {@code applicationTaskExecutor}.
 * That claim is an ORDERING fact about autoconfiguration, and a unit test that calls
 * {@code configureAsyncSupport} by hand would stay green if Boot stopped applying our configurer last.
 *
 * <p>What is proved here:
 * <ol>
 *   <li>the effective executor on the {@link RequestMappingHandlerAdapter} is ours;</li>
 *   <li>more than EIGHT streams run concurrently — eight being exactly what Boot's default pool would
 *       cap us at, and the ninth would not fail but hang;</li>
 *   <li>a burst past the cap gets prompt JSON 503s, not queued requests and not 500s from a rejected
 *       task — which is only possible because the refusal is written synchronously;</li>
 *   <li>the other async endpoint on this gateway still answers while all those streams are held open.</li>
 * </ol>
 *
 * <p>The context is a MINIMAL Boot app rather than the gateway's own: the real one starts Kafka
 * consumers from {@code @PostConstruct} (which is why {@code GatewayContextBootIT} is opt-in). What is
 * under test is the MVC async pipeline, and this context contains exactly that plus the two endpoints
 * that use it.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = StockGexAsyncPipelineTest.TestApp.class,
        properties = {
            "spring.mvc.async.request-timeout=-1",
            "server.tomcat.threads.max=200"
        })
class StockGexAsyncPipelineTest {

    /** Above Boot's 8-thread default, below the pool, so both boundaries are exercised. */
    static final int STREAM_CAP = 12;

    @LocalServerPort
    int port;

    @Autowired
    RequestMappingHandlerAdapter handlerAdapter;

    @Autowired
    StockGexAsyncConfig asyncConfig;

    @Autowired
    BlockingUpstream upstream;

    @Autowired
    StockGexController controller;

    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * The Spring context is shared across these tests, and so is the semaphore they exhaust. Starting a
     * test while the previous one's streams are still draining would silently measure the wrong thing —
     * a cap test that never reaches the cap passes for the wrong reason.
     */
    @BeforeEach
    void startFromFullCapacity() throws Exception {
        upstream.reset();
        assertTrue(waitFor(() -> controller.streamSlots.availablePermits() == STREAM_CAP, 20_000L),
                "previous test left " + controller.streamSlots.availablePermits() + "/" + STREAM_CAP
                + " slots; this one would not be testing what it claims");
    }

    @AfterEach
    void releaseEverything() {
        upstream.releaseAll();
    }

    @AfterAll
    static void done() {
    }

    @Test
    void theEFFECTIVEMvcAsyncExecutorIsOursAfterAllOfBootsConfigurersHaveRun() {
        Field field = ReflectionUtils.findField(RequestMappingHandlerAdapter.class, "taskExecutor");
        assertNotNull(field, "Spring changed the field this pins; re-verify the claim, do not delete it");
        ReflectionUtils.makeAccessible(field);
        Object effective = ReflectionUtils.getField(field, handlerAdapter);

        assertTrue(effective instanceof AsyncTaskExecutor, "expected an AsyncTaskExecutor, got " + effective);
        assertSame(asyncConfig.executor(), effective,
                "Boot installs applicationTaskExecutor (core 8, unbounded queue) unless our configurer "
                + "runs after it. If this fails, the ninth concurrent stream silently queues forever.");
    }

    @Test
    void moreThanEightStreamsRunConcurrentlyAndABurstPastTheCapGetsPromptJson503s() throws Exception {
        List<Future<HttpResponse<InputStream>>> opened = new ArrayList<>();
        for (int i = 0; i < STREAM_CAP; i++) {
            opened.add(clients.submit(() -> openStream("SYM" + System.nanoTime())));
        }
        List<HttpResponse<InputStream>> live = new ArrayList<>();
        for (Future<HttpResponse<InputStream>> f : opened) {
            HttpResponse<InputStream> r = f.get(30, TimeUnit.SECONDS);
            assertEquals(200, r.statusCode(), "every stream up to the cap must be admitted");
            live.add(r);
        }
        // Eight is the number that matters: it is exactly where Boot's default pool stops creating
        // threads, and the ninth request there does not fail — it waits, forever, answering nothing.
        assertTrue(live.size() > 8, "only " + live.size() + " concurrent streams ran");
        assertTrue(upstream.opened() >= STREAM_CAP, "each admitted stream must hold one upstream exchange");

        // Now the burst. Every one of these must come back FAST with a readable 503 — not queue, and
        // not fail with a 500 from a rejected async task.
        int burst = 20;
        List<Future<HttpResponse<String>>> refusals = new ArrayList<>();
        long start = System.nanoTime();
        for (int i = 0; i < burst; i++) {
            refusals.add(clients.submit(() -> http.send(
                    HttpRequest.newBuilder(URI.create(url("/api/stock-gex/stream?symbol=OVER")))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer t").GET().build(),
                    HttpResponse.BodyHandlers.ofString())));
        }
        for (Future<HttpResponse<String>> f : refusals) {
            HttpResponse<String> r = f.get(30, TimeUnit.SECONDS);
            assertEquals(503, r.statusCode(),
                    "overflow must be refused, not queued and not 500: got " + r.statusCode() + " " + r.body());
            assertTrue(r.body().contains("SSE_CLIENT_LIMIT"),
                    "the refusal must carry a code the page renders: " + r.body());
            assertEquals("5", r.headers().firstValue("Retry-After").orElse(""));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs < 10_000L, "refusals took " + elapsedMs + "ms — they were not immediate");

        // ...and the OTHER async endpoint on this gateway still works while all of that is held open.
        HttpResponse<String> neighbour = http.send(
                HttpRequest.newBuilder(URI.create(url("/api/seller-activity-like")))
                        .timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, neighbour.statusCode(),
                "a gateway whose streams starve every other async endpoint has traded one outage for two");
        assertEquals("{\"ok\":true}", neighbour.body());

        upstream.releaseAll();
        for (HttpResponse<InputStream> r : live) {
            r.body().close();
        }
    }

    @Test
    void aClosedClientReturnsItsSlotSoTheGatewayRecovers() throws Exception {
        List<HttpResponse<InputStream>> live = new ArrayList<>();
        for (int i = 0; i < STREAM_CAP; i++) {
            live.add(openStream("SYM" + i));
        }
        HttpResponse<String> refused = http.send(
                HttpRequest.newBuilder(URI.create(url("/api/stock-gex/stream?symbol=OVER")))
                        .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer t").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503, refused.statusCode());

        upstream.releaseAll();
        for (HttpResponse<InputStream> r : live) {
            r.body().close();
        }
        // The permit comes back through the pump's finally or the async-completion binding, whichever
        // wins. Either way the gateway must become usable again without a restart.
        assertTrue(waitFor(() -> {
            try {
                return openStreamStatus("RECOVERED") == 200;
            } catch (Exception e) {
                return false;
            }
        }, 20_000L), "the gateway never recovered its stream slots");
    }

    // ------------------------------------------------------------------ helpers

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<InputStream> openStream(String symbol) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url("/api/stock-gex/stream?symbol=" + symbol)))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer t")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    private int openStreamStatus(String symbol) throws Exception {
        HttpResponse<InputStream> r = openStream(symbol);
        r.body().close();
        return r.statusCode();
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100L);
        }
        return false;
    }

    // ------------------------------------------------------------------ the context under test

    /**
     * An upstream that answers every subscribe with a stream that emits one frame and then holds open
     * until the test lets go — which is what a healthy live board does for hours.
     */
    static final class BlockingUpstream {
        private volatile CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger opened = new AtomicInteger();

        int opened() {
            return opened.get();
        }

        /** A fresh hold for the next test; the context (and this bean) is shared between them. */
        void reset() {
            release.countDown();
            release = new CountDownLatch(1);
            opened.set(0);
        }

        void releaseAll() {
            release.countDown();
        }

        InputStream open() {
            opened.incrementAndGet();
            CountDownLatch hold = release;
            byte[] first = "id: 1\ndata: {\"state\":\"live\"}\n\n".getBytes(StandardCharsets.UTF_8);
            return new InputStream() {
                private int index = 0;

                @Override
                public int read() throws IOException {
                    if (index < first.length) {
                        return first[index++] & 0xFF;
                    }
                    try {
                        hold.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return -1;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int c = read();
                    if (c == -1) {
                        return -1;
                    }
                    b[off] = (byte) c;
                    int n = 1;
                    while (n < len && index < first.length) {
                        b[off + n++] = first[index++];
                    }
                    return n;
                }
            };
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        BlockingUpstream blockingUpstream() {
            return new BlockingUpstream();
        }

        @Bean
        StockGexAsyncConfig stockGexAsyncConfig() {
            return new StockGexAsyncConfig();
        }

        @Bean
        @SuppressWarnings({"unchecked", "rawtypes"})
        StockGexUpstream stockGexUpstream(BlockingUpstream blocking) throws Exception {
            java.net.http.HttpClient client = mock(java.net.http.HttpClient.class);
            when(client.send(any(HttpRequest.class), any())).thenAnswer(invocation -> {
                HttpResponse resp = mock(HttpResponse.class);
                when(resp.statusCode()).thenReturn(200);
                when(resp.headers()).thenReturn(java.net.http.HttpHeaders.of(
                        Map.of("content-type", List.of("text/event-stream")), (k, v) -> true));
                when(resp.body()).thenReturn(blocking.open());
                return resp;
            });
            return new StockGexUpstream("http://stock-gex-service:8021", Duration.ofSeconds(5), client);
        }

        @Bean
        LiquidityHistoryAuth liquidityHistoryAuth() {
            LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
            when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "tester"));
            return auth;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        StockGexController stockGexController(StockGexUpstream upstream, LiquidityHistoryAuth auth,
                                              ObjectMapper mapper) {
            return new StockGexController(upstream, auth, mapper, STREAM_CAP,
                    StockGexController.SERVLET_ASYNC_CLEANUP);
        }

        /**
         * Stands in for {@code /api/seller-activity}: the gateway's OTHER {@code StreamingResponseBody}
         * endpoint, and the one that silently stops answering if the streams take the whole pool.
         */
        @Bean
        NeighbourAsyncEndpoint neighbourAsyncEndpoint() {
            return new NeighbourAsyncEndpoint();
        }
    }

    @RestController
    static class NeighbourAsyncEndpoint {
        @GetMapping("/api/seller-activity-like")
        ResponseEntity<StreamingResponseBody> answer() {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(out -> out.write(body));
        }
    }
}
