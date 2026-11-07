package app.feedgateway;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Bounded owner-specific outbound wakeups, independent of socket writers and domain locks. */
final class OutboundIdleDispatcher implements AutoCloseable {
    static final int MAX_OWNERS = 4, MAX_CALLBACKS = 32;
    static final long SLICE_NANOS = 2_000_000;
    private final Executor executor;
    private final Object lock = new Object();
    private final LinkedHashMap<Object, Registration> owners = new LinkedHashMap<>();
    private boolean closed;
    private Work scheduled;

    private final class Work implements Runnable {
        final Thread submitter = Thread.currentThread();
        volatile boolean handingOff = true;
        boolean started;
        @Override public void run() { OutboundIdleDispatcher.this.run(this); }
    }

    OutboundIdleDispatcher(Executor executor) { this.executor = Objects.requireNonNull(executor); }

    final class Registration implements AutoCloseable {
        private final Object owner;
        private final Runnable callback, failed;
        private boolean active = true, ready;
        private Registration(Object owner, Runnable callback, Runnable failed) {
            this.owner = owner; this.callback = callback; this.failed = failed;
        }
        void wake() {
            synchronized (lock) { if (closed || !active || owners.get(owner) != this) return; ready = true; }
            retry();
        }
        @Override public void close() {
            synchronized (lock) { active = false; ready = false; owners.remove(owner, this); }
        }
    }

    /** Replacement returns a distinct handle: closing an old handle cannot remove its successor. */
    Registration register(Object owner, Runnable callback, Runnable failed) {
        Objects.requireNonNull(owner); Objects.requireNonNull(callback); Objects.requireNonNull(failed);
        synchronized (lock) {
            if (closed || !owners.containsKey(owner) && owners.size() == MAX_OWNERS) return null;
            Registration entry = new Registration(owner, callback, failed), old = owners.put(owner, entry);
            if (old != null) { old.active = false; old.ready = false; }
            return entry;
        }
    }

    void signalIdle() {
        synchronized (lock) { if (closed || owners.isEmpty()) return; for (Registration entry : owners.values()) entry.ready = true; }
        retry();
    }

    /** A rejected handoff remains ready; the existing bounded watchdog can retry it. */
    void retry() {
        Work work;
        synchronized (lock) {
            if (closed || scheduled != null || owners.isEmpty()) return;
            boolean ready=false;for(Registration entry:owners.values())if(entry.ready){ready=true;break;}
            if(!ready)return;
            work = new Work(); scheduled = work;
        }
        try { executor.execute(work); }
        catch (RuntimeException | Error rejected) {
            synchronized (lock) { if (scheduled == work && !work.started) scheduled = null; }
        } finally { work.handingOff = false; }
    }

    private void run(Work work) {
        synchronized (lock) { if (closed || scheduled != work) return; work.started = true; }
        long started = System.nanoTime(); int calls = 0;
        try {
            while (calls < MAX_CALLBACKS && (calls == 0 || System.nanoTime() - started < SLICE_NANOS)) {
                Registration next = null;
                synchronized (lock) {
                    if (closed) return;
                    for (Registration entry : owners.values()) if (entry.ready) { next = entry; break; }
                    if (next == null) return;
                    next.ready = false;
                    owners.remove(next.owner); owners.put(next.owner, next);
                }
                try { next.callback.run(); }
                catch (RuntimeException | Error failure) {
                    next.close();
                    try { next.failed.run(); } catch (RuntimeException | Error ignored) { /* Isolate this owner. */ }
                }
                calls++;
            }
        } finally {
            synchronized (lock) { if (scheduled == work) scheduled = null; }
            // Production uses an asynchronous bounded executor. An injected inline executor cannot
            // recursively exhaust the stack; its remaining ready work stays for watchdog retry.
            if (!(work.handingOff && work.submitter == Thread.currentThread())) retry();
        }
    }

    @Override public void close() {
        synchronized (lock) {
            closed = true;
            for (Registration entry : owners.values()) { entry.active = false; entry.ready = false; }
            owners.clear();
        }
    }
}
