package app.feedgateway;

/**
 * A child JVM's entry point for VolPremiumSessionStoreTest: build the PRODUCTION session store (its no-argument
 * constructor, the one the gateway calls) on the heap this JVM was given. Exit 0 when it starts; exit 3, with the
 * refusal on stdout, when its boot check refuses the heap.
 */
public final class VolPremiumHeapProbe {

    private VolPremiumHeapProbe() {
    }

    public static void main(String[] args) {
        try {
            new VolPremiumSessionStore();
        } catch (IllegalStateException refused) {
            System.out.println(refused.getMessage());
            System.exit(3);
        }
        System.out.println("PROBE started");
    }
}
