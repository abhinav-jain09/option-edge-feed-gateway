package app.feedgateway.pinflow;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * §6.1 bulkhead: a dedicated bounded {@link ThreadPoolExecutor} on which ALL pin-flow JDBC runs.
 * JDBC is never invoked from Kafka/Streams/WebSocket threads. At saturation (all threads busy AND
 * the tiny bounded queue full) submission is REJECTED with {@link RejectedExecutionException} →
 * the controller maps it to a fast 503 (no unbounded queueing, the live market-data path is untouched).
 */
public final class PinFlowExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;

    public PinFlowExecutor(int threads, int queueDepth) {
        int size = Math.max(1, threads);
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "pin-flow-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        this.executor = new ThreadPoolExecutor(
                size, size, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueDepth)),
                factory,
                new ThreadPoolExecutor.AbortPolicy()); // CallerRejects → RejectedExecutionException → 503
        this.executor.allowCoreThreadTimeOut(true);
    }

    public ThreadPoolExecutor executor() {
        return executor;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
