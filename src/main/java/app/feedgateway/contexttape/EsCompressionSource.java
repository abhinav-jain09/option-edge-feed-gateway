package app.feedgateway.contexttape;

/** Read-only source for the ES-primary challenger projection. */
public interface EsCompressionSource {
    ContextTapeUpstream.SessionResponse esCompression();

    default long acceptedCount() { return 0L; }
    default long rejectedCount() { return 0L; }
}
