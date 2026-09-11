package app.feedgateway;

/**
 * A child JVM that says it has started, keeps its stdout open and then stalls, as a child JVM hung in start-up or
 * in construction would. VolPremiumSessionStoreTest runs it to prove its child-JVM helper enforces its deadline:
 * the helper must destroy it at the deadline rather than wait for its output to end.
 */
public final class VolPremiumStallingProbe {

    private VolPremiumStallingProbe() {
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("STALLING");
        System.out.flush();
        Thread.sleep(120_000L);
    }
}
