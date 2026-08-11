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
    /** Every response body this test opened, closed in @AfterEach rather than left to the GC. */
    private final List<HttpResponse<InputStream>> openedBodies =
            java.util.Collections.synchronizedList(new ArrayList<>());
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
        // JUnit builds a fresh test INSTANCE per method, so this cached pool is per method too, and
        // its workers are non-daemon: without this they linger for a minute each, and the suite ends
        // up holding threads and sockets while later timing assertions run.
        clients.shutdownNow();
        for (HttpResponse<InputStream> open : openedBodies) {
            try {
                open.body().close();
            } catch (Exception ignored) {
                // best effort; the point is not to leave sockets to the GC
            }
        }
        openedBodies.clear();
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
    void theRefusalIsStillSynchronousWhenTheAsyncEXECUTORItselfIsFull() throws Exception {
        // The claim is not "refusals are fast when there is spare capacity" — it is that a refusal
        // does not NEED the async executor at all. Proving that requires the executor to be full, so
        // this exhausts the stream cap AND then fills the remaining pool before asking for one more.
        List<HttpResponse<InputStream>> live = new ArrayList<>();
        for (int i = 0; i < STREAM_CAP; i++) {
            live.add(openStream("CAP" + i));
        }
        List<Future<HttpResponse<InputStream>>> occupiers = new ArrayList<>();
        int fill = StockGexAsyncConfig.MAX_ASYNC_THREADS + 8;
        for (int i = 0; i < fill; i++) {
            occupiers.add(clients.submit(() -> http.send(
                    HttpRequest.newBuilder(URI.create(url("/test/occupy")))
                            .timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream())));
        }
        try {
            int occupied = 0;
            for (Future<HttpResponse<InputStream>> f : occupiers) {
                try {
                    if (f.get(30, TimeUnit.SECONDS).statusCode() == 200) {
                        occupied++;
                    }
                } catch (Exception rejected) {
                    // A rejected async task is the designed behaviour of the direct-handoff pool and
                    // is exactly the pressure this test wants.
                }
            }
            // The named condition, asserted rather than assumed: the EFFECTIVE executor is at its
            // maximum with no spare thread. Without this the test could pass with 52 threads free,
            // in which case an accidentally-asynchronous refusal would also have been prompt and the
            // test would have proved nothing.
            java.util.concurrent.ThreadPoolExecutor pool =
                    asyncConfig.executor().getThreadPoolExecutor();
            assertTrue(waitFor(() -> pool.getActiveCount() >= pool.getMaximumPoolSize(), 20_000L),
                    "the async executor never reached its maximum: active=" + pool.getActiveCount()
                    + " of " + pool.getMaximumPoolSize() + " (occupied=" + occupied + ")");
            assertEquals(0, controller.streamSlots.availablePermits(), "the cap must be exhausted");

            long start = System.nanoTime();
            HttpResponse<String> refused = http.send(
                    HttpRequest.newBuilder(URI.create(url("/api/stock-gex/stream?symbol=UNDERLOAD")))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer t").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertEquals(503, refused.statusCode(),
                    "a refusal must not become a 500 from a rejected async task: " + refused.body());
            assertTrue(refused.body().contains("SSE_CLIENT_LIMIT"),
                    "and it must still be the readable one: " + refused.body());
            assertTrue(elapsedMs < 10_000L, "the refusal took " + elapsedMs + "ms — it was not immediate");
        } finally {
            upstream.releaseAll();
            for (HttpResponse<InputStream> r : live) {
                r.body().close();
            }
        }
    }

    @Test
    void anADMITTEDStreamRejectedByTheExecutorStillGivesBackItsPermitAndCloses() throws Exception {
        // The complement: the cap says yes, the POOL says no. The streaming body is then never
        // invoked, so its finally never runs — and only the real servlet async interceptor can
        // return the permit and close the upstream exchange. If it does not, the gateway loses a
        // slot and an upstream SSE listener on every rejection.
        List<Future<HttpResponse<InputStream>>> occupiers = new ArrayList<>();
        int fill = StockGexAsyncConfig.MAX_ASYNC_THREADS + 24;
        for (int i = 0; i < fill; i++) {
            occupiers.add(clients.submit(() -> http.send(
                    HttpRequest.newBuilder(URI.create(url("/test/occupy")))
                            .timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream())));
        }
        try {
            for (Future<HttpResponse<InputStream>> f : occupiers) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception rejected) {
                    // expected for the overflow
                }
            }
            java.util.concurrent.ThreadPoolExecutor pool =
                    asyncConfig.executor().getThreadPoolExecutor();
            assertTrue(waitFor(() -> pool.getActiveCount() >= pool.getMaximumPoolSize(), 20_000L),
                    "the async executor never reached its maximum, so no stream could be rejected");
            int before = controller.streamSlots.availablePermits();
            int upstreamOpensBefore = upstream.opened();

            // With the pool at its maximum, the cap admits these and the EXECUTOR refuses them, so
            // the streaming body is never invoked and only the servlet async interceptor can clean up.
            int rejected = 0;
            for (int i = 0; i < 8; i++) {
                try {
                    HttpResponse<String> r = http.send(
                            HttpRequest.newBuilder(URI.create(url("/api/stock-gex/stream?symbol=REJ" + i)))
                                    .timeout(Duration.ofSeconds(15))
                                    .header("Authorization", "Bearer t").GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() == 500) {
                        rejected++;
                    }
                } catch (IOException streamCutOff) {
                    rejected++;   // a rejected async dispatch can also surface as a broken response
                }
            }
            assertTrue(rejected > 0,
                    "no request actually entered the executor-rejection path, so this test proved nothing");
            assertTrue(upstream.opened() > upstreamOpensBefore,
                    "a rejected request must have opened an upstream exchange — that is the thing "
                    + "that has to be closed again");
            upstream.releaseAll();

            // Whatever happened to each request, every permit must come back and every upstream
            // exchange must be closed. A rejection may be loud; it may not be lossy.
            assertTrue(waitFor(() -> controller.streamSlots.availablePermits() >= before, 20_000L),
                    "permits leaked under executor rejection: " + controller.streamSlots.availablePermits()
                    + " < " + before);
            assertTrue(waitFor(() -> upstream.closed() >= upstream.opened(), 20_000L),
                    "upstream exchanges leaked: opened=" + upstream.opened()
                    + " closed=" + upstream.closed());
        } finally {
            upstream.releaseAll();
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
        HttpResponse<InputStream> opened = http.send(
                HttpRequest.newBuilder(URI.create(url("/api/stock-gex/stream?symbol=" + symbol)))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer t")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        openedBodies.add(opened);
        return opened;
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
        private final AtomicInteger closed = new AtomicInteger();

        int opened() {
            return opened.get();
        }

        int closed() {
            return closed.get();
        }

        /** A fresh hold for the next test; the context (and this bean) is shared between them. */
        void reset() {
            release.countDown();
            release = new CountDownLatch(1);
            opened.set(0);
            closed.set(0);
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

                @Override
                public void close() {
                    closed.incrementAndGet();
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

        /** Fills the MVC async executor, so the synchronous-refusal claim is tested where it matters. */
        @Bean
        ExecutorFillingEndpoint executorFillingEndpoint(BlockingUpstream blocking) {
            return new ExecutorFillingEndpoint(blocking);
        }
    }

    /**
     * A streaming endpoint whose only purpose is to OCCUPY async threads. Holding 12 streams in a
     * 64-thread pool leaves 52 free, so an accidentally-asynchronous refusal would still be answered
     * promptly and the test would pass for the wrong reason.
     */
    @RestController
    static class ExecutorFillingEndpoint {
        private final BlockingUpstream blocking;

        ExecutorFillingEndpoint(BlockingUpstream blocking) {
            this.blocking = blocking;
        }

        @GetMapping("/test/occupy")
        ResponseEntity<StreamingResponseBody> occupy() {
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(out -> {
                out.write(": held\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                try (InputStream held = blocking.open()) {
                    byte[] buffer = new byte[64];
                    while (held.read(buffer) != -1) {
                        // drains the first frame, then blocks on the shared latch
                    }
                }
            });
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
