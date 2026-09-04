package app.feedgateway.stockgex;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The MVC async thread pool, made explicit because the Boot default is the wrong shape for a long-lived
 * streaming response and fails in the worst possible way.
 *
 * <p><b>What the default does.</b> Spring Boot wires the shared {@code applicationTaskExecutor} as the
 * MVC async executor ({@code WebMvcAutoConfiguration.configureAsyncSupport}), and its defaults are
 * {@code core-size=8}, {@code queue-capacity=Integer.MAX_VALUE}, {@code max-size=Integer.MAX_VALUE}. A
 * {@link ThreadPoolExecutor} only grows past its core size when the QUEUE is full, and that queue is
 * effectively unbounded — so the pool never exceeds EIGHT threads. Every {@code StreamingResponseBody} on
 * this gateway occupies one of those threads for the whole life of its response, and a stock-gex SSE
 * session lasts hours. The ninth concurrent async response therefore does not fail: it sits in the queue,
 * having produced no status, no headers and no body, and {@code spring.mvc.async.request-timeout=-1}
 * (which the SSE proxy needs, so a healthy stream is not torn down on a 30-second timer) removes the only
 * thing that would ever have ended that wait. Eight open board tabs would silently wedge every async
 * endpoint on this gateway, including {@code /api/seller-activity}.
 *
 * <p><b>What this does instead.</b> A dedicated pool with a {@link SynchronousQueue} — {@code
 * queueCapacity=0} — so a task is either handed straight to a thread or refused; it can never sit in a
 * queue that nothing drains. The pool grows to {@link #MAX_ASYNC_THREADS}, comfortably above the
 * {@link StockGexController#MAX_CONCURRENT_STREAMS} cap plus the handful of slots
 * {@code /api/seller-activity} bounds itself to, so in normal operation the admission decision is made by
 * the controller's own cap (which answers a status the page renders) and never by pool rejection.
 * {@link ThreadPoolExecutor.AbortPolicy} is deliberate for the residual case: a rejection surfaces as an
 * error response, which is loud, whereas {@code CallerRunsPolicy} would run an hours-long stream on the
 * Tomcat request thread and take a connector thread out of service instead.
 *
 * <p>This is a {@link WebMvcConfigurer} holding its OWN executor rather than a {@code @Bean} or a set of
 * {@code spring.task.execution.*} properties, and both alternatives were rejected for a reason. The
 * properties reshape the SHARED executor (also used by {@code @Async} anywhere it is later introduced),
 * while the constraint solved here is specific to MVC async dispatch. Publishing it as an {@code Executor}
 * bean would be worse still: {@code applicationTaskExecutor} is {@code @ConditionalOnMissingBean(Executor)},
 * so a second executor bean would silently SUPPRESS it and change what every other part of the application
 * gets injected. Owning the instance privately, with an explicit {@link PreDestroy}, keeps the blast radius
 * at exactly the thing being fixed.
 */
@Configuration
public class StockGexAsyncConfig implements WebMvcConfigurer {

    /**
     * Ceiling on concurrent async (streaming) responses across the whole gateway. Sized as
     * {@code MAX_CONCURRENT_STREAMS (24) + seller-activity's own cap (4) + headroom}: each thread is
     * blocked on I/O, not burning CPU, so the cost is stack reservation rather than scheduling.
     */
    static final int MAX_ASYNC_THREADS = 64;

    /** Threads kept warm. Idle streams cost nothing; this only avoids churn at the start of a session. */
    private static final int CORE_ASYNC_THREADS = 8;

    /** How long an idle non-core thread lingers before it is reaped. */
    private static final int KEEP_ALIVE_SECONDS = 60;

    private final ThreadPoolTaskExecutor executor = asyncExecutor();

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
        // The DEFAULT TIMEOUT is deliberately left as configured in application.properties
        // (spring.mvc.async.request-timeout=-1). A deadline here would tear down healthy SSE streams;
        // liveness is proven by the upstream heartbeat failing to write, not by a clock.
    }

    /** Package-visible for the test that pins the pool's shape. */
    ThreadPoolTaskExecutor executor() {
        return executor;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private static ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mvc-async-");
        executor.setCorePoolSize(CORE_ASYNC_THREADS);
        executor.setMaxPoolSize(MAX_ASYNC_THREADS);
        // 0 => SynchronousQueue: direct hand-off, so the pool grows on demand and a task is NEVER
        // parked in a queue behind a response that will not finish for hours.
        executor.setQueueCapacity(0);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setAllowCoreThreadTimeOut(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Daemon threads: a stream blocked on a socket read must never be the reason this JVM refuses
        // to exit. The @PreDestroy above is the orderly path; this is the backstop when there is none.
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
