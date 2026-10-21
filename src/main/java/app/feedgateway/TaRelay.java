package app.feedgateway;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the four technical-analysis chart relays (structure tools, geometry, trade tools, waves and cycles) share with the service: the
 * Kafka glue lives in {@link FeedGatewayService}, the data and the decisions live in the relay. A relay validates a decoded Avro record
 * at the gateway boundary, renders it once to the browser JSON, keeps a bounded retained state for the hello, tells the caller which
 * frame to broadcast, and recovers its state from a seek-back through a private staging copy that becomes visible in one promotion.
 */
abstract class TaRelay {
    /** The frame guard: a rendered record above this is refused (the 1 MiB hello/frame guard minus 4 KiB of envelope). */
    static final int FRAME_MAX_BYTES = 1_048_576 - 4_096;
    /** The timeframes the page knows, in hello order. */
    static final String[] TF = {"1m", "5m", "15m"};

    private final String name;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong drops = new AtomicLong();
    private final AtomicLong regressions = new AtomicLong();

    TaRelay(String name) {
        this.name = name;
    }

    /** Whether {@code bindingEvent} is one of the Kafka binding events this relay reads. */
    abstract boolean handles(String bindingEvent);

    /** The live path: {@code {frame event, frame data}} to broadcast, or null (dropped, regression, history only). */
    abstract String[] onLive(String bindingEvent, Object value);

    /** Starts a recovery read: a private copy of the retained state; nothing in it is visible until {@link #promote}. */
    abstract void beginRecovery();

    /** The recovery path: the record is merged into the private copy; nothing is broadcast. */
    abstract void onStaged(String bindingEvent, Object value);

    /** Folds the private copy into the retained state and returns the frames the recovery owes the connected sockets, in order. */
    abstract List<String[]> promote();

    /**
     * The hello field(s) of this relay, with a leading comma, at most {@code budget} UTF-8 bytes of content (the field skeleton with
     * every entry {@code null} is always sent: the field's presence is the flag).
     */
    abstract String helloField(long budget);

    /** How many records are re-read from the end of each partition of a binding's topic on a cold start, retry or late adoption. */
    abstract int tailRecords(String bindingEvent);

    /** A tombstone or an unreadable value: counted, retains nothing, withdraws nothing. */
    final void onDrop() {
        drops.incrementAndGet();
    }

    final void accepted() {
        accepted.incrementAndGet();
    }

    final void regressed() {
        regressions.incrementAndGet();
    }

    final String metrics() {
        String p = "gateway_" + name + "_";
        return "# HELP " + p + "enabled Whether the " + name + " relay is enabled.\n# TYPE " + p + "enabled gauge\n" + p + "enabled 1\n"
                + "# HELP " + p + "accepted_total Records accepted at the gateway boundary and forwarded.\n# TYPE " + p + "accepted_total counter\n"
                + p + "accepted_total " + accepted.get() + "\n"
                + "# HELP " + p + "drops_total Records refused at the gateway boundary (invalid, oversize, unknown record, tombstone).\n"
                + "# TYPE " + p + "drops_total counter\n" + p + "drops_total " + drops.get() + "\n"
                + "# HELP " + p + "regressions_total Records refused because their ordering key regressed.\n# TYPE " + p + "regressions_total counter\n"
                + p + "regressions_total " + regressions.get() + "\n";
    }

    final long dropsForTest() {
        return drops.get();
    }

    final long regressionsForTest() {
        return regressions.get();
    }

    final long acceptedForTest() {
        return accepted.get();
    }

    static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }

    /** The decoded value as a record, or null: a tombstone, a non-record or an undecodable marker is a counted drop. */
    final org.apache.avro.generic.GenericRecord recordOrDrop(Object value) {
        if (value instanceof org.apache.avro.generic.GenericRecord record) {
            return record;
        }
        drops.incrementAndGet();
        return null;
    }
}
