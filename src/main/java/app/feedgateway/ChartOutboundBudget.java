package app.feedgateway;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Accounts chart-owned wire copies through actual send/discard, including detached in-flight owners. */
final class ChartOutboundBudget implements AutoCloseable {
    static final long GLOBAL_BYTES = 8L * 1024 * 1024;
    static final int OWNER_BYTES = 128 * 1024, OWNER_FRAMES = 2, MAX_OWNERS = 1024;
    private final Object lock = new Object();
    private final Set<Owner> owners = new HashSet<>();
    private final long globalLimit;
    private final int ownerLimit, frameLimit, ownerCountLimit;
    private final Runnable capacityReleased;
    private boolean closed;
    private long reserved;

    /** The wakeup must only schedule deduplicated work; it is never called while the budget lock is held. */
    ChartOutboundBudget(Runnable capacityReleased) {
        this(GLOBAL_BYTES, OWNER_BYTES, OWNER_FRAMES, MAX_OWNERS, capacityReleased);
    }
    ChartOutboundBudget(long globalLimit, int ownerLimit, int frameLimit, int ownerCountLimit, Runnable capacityReleased) {
        if (globalLimit < 1 || ownerLimit < 1 || frameLimit < 1 || ownerCountLimit < 1) throw new IllegalArgumentException("chart budget");
        this.globalLimit=globalLimit;this.ownerLimit=ownerLimit;this.frameLimit=frameLimit;this.ownerCountLimit=ownerCountLimit;
        this.capacityReleased=Objects.requireNonNull(capacityReleased);
    }

    Owner attach() {
        synchronized (lock) {
            if (closed || owners.size() >= ownerCountLimit) return null;
            var owner = new Owner();owners.add(owner);return owner;
        }
    }
    long reservedBytes() { synchronized (lock) { return reserved; } }
    int retainedOwners() { synchronized (lock) { return owners.size(); } }

    final class Owner implements AutoCloseable {
        private boolean active=true;
        private int bytes, frames;
        private Owner() { }
        Lease tryAcquire(int wireBytes) {
            synchronized (lock) {
                if (closed || !active || wireBytes < 1 || wireBytes > ownerLimit || frames >= frameLimit
                        || (long)bytes + wireBytes > ownerLimit || reserved + wireBytes > globalLimit) return null;
                var lease = new Lease(this,wireBytes);
                bytes += wireBytes;frames++;reserved += wireBytes;return lease;
            }
        }
        @Override public void close() {
            boolean freed;
            synchronized (lock) { active=false;freed=frames==0 && owners.remove(this); }
            if(freed)notifyCapacity();
        }
    }

    final class Lease implements AutoCloseable {
        private final Owner owner;
        private final int bytes;
        private final AtomicBoolean released=new AtomicBoolean();
        private Lease(Owner owner,int bytes) { this.owner=owner;this.bytes=bytes; }
        @Override public void close() {
            if(!released.compareAndSet(false,true))return;
            synchronized (lock) {
                owner.bytes -= bytes;owner.frames--;reserved -= bytes;
                if(!owner.active && owner.frames==0)owners.remove(owner);
            }
            notifyCapacity();
        }
    }

    private void notifyCapacity() {
        try { capacityReleased.run(); } catch (RuntimeException | Error ignored) { /* Watchdog retries queued owner work. */ }
    }

    @Override public void close() {
        synchronized (lock) {
            closed=true;
            for(Owner owner:owners)owner.active=false;
            owners.removeIf(owner->owner.frames==0);
        }
    }
}
