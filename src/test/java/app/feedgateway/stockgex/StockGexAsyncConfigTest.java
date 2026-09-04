package app.feedgateway.stockgex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

/**
 * The pool shape is load-bearing, so it is pinned rather than trusted to a comment.
 *
 * <p>The defect this configuration exists to prevent is not slowness: with Boot's default MVC async
 * executor (core 8, effectively unbounded queue) the ninth concurrent {@code StreamingResponseBody}
 * QUEUES — it produces no status, no headers and no body — and with
 * {@code spring.mvc.async.request-timeout=-1} it queues forever. A queued response is invisible: the
 * browser shows a request that never resolves and no log line is written anywhere. So the two properties
 * that matter are (a) the queue holds nothing, and (b) the pool is big enough for every stream the
 * controller will admit.
 */
class StockGexAsyncConfigTest {

    @Test
    void theAsyncPoolIsHandedOffDirectlyAndNeverQueues() {
        StockGexAsyncConfig config = new StockGexAsyncConfig();
        try {
            ThreadPoolTaskExecutor executor = config.executor();
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            assertEquals(0, pool.getQueue().remainingCapacity(),
                    "a queue with capacity is where a streaming response goes to wait forever");
            assertEquals(StockGexAsyncConfig.MAX_ASYNC_THREADS, pool.getMaximumPoolSize());
            assertTrue(pool.getMaximumPoolSize() > StockGexController.MAX_CONCURRENT_STREAMS,
                    "the controller's own cap must be the thing that refuses a client, not pool rejection — "
                    + "the controller answers 503 SSE_CLIENT_LIMIT, the pool answers a 500");
        } finally {
            config.shutdown();
        }
    }

    @Test
    void aFullPoolRefusesLoudlyRatherThanRunningTheStreamOnTheRequestThread() throws Exception {
        // CallerRunsPolicy would look kinder and be far worse: an hours-long SSE pump executed on the
        // Tomcat request thread takes a connector thread out of service for the whole session.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setDaemon(true);
        executor.initialize();
        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    hold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
        } finally {
            hold.countDown();
            executor.shutdown();
        }
    }

    @Test
    void theConfigurerInstallsThatExecutorAndLeavesTheTimeoutAlone() {
        StockGexAsyncConfig config = new StockGexAsyncConfig();
        try {
            AtomicReference<AsyncTaskExecutor> installed = new AtomicReference<>();
            AtomicReference<Long> timeout = new AtomicReference<>();
            AsyncSupportConfigurer configurer = new AsyncSupportConfigurer() {
                @Override
                public AsyncSupportConfigurer setTaskExecutor(AsyncTaskExecutor taskExecutor) {
                    installed.set(taskExecutor);
                    return this;
                }

                @Override
                public AsyncSupportConfigurer setDefaultTimeout(long timeoutMillis) {
                    timeout.set(timeoutMillis);
                    return this;
                }
            };

            config.configureAsyncSupport(configurer);

            assertNotNull(installed.get());
            assertSame(config.executor(), installed.get());
            // Setting a default timeout here would silently override
            // spring.mvc.async.request-timeout=-1 and tear healthy SSE streams down on a clock.
            assertEquals(null, timeout.get(), "the async deadline stays owned by application.properties");
        } finally {
            config.shutdown();
        }
    }
}
