package app.feedgateway.replay;

/**
 * Maps a live topic name to its per-{@code runId} replay-namespace equivalent, matching the
 * orchestrator's convention: {@code replay.<runId>} is inserted directly after the leading namespace
 * segment.
 *
 * <pre>
 *   options.databento.display     ->  options.replay.&lt;runId&gt;.databento.display
 *   options.databento.strike-flow ->  options.replay.&lt;runId&gt;.databento.strike-flow
 *   underlying.es.trades          ->  underlying.replay.&lt;runId&gt;.es.trades
 * </pre>
 *
 * The result is asserted to sit in the replay namespace, so a session-replay read can never touch a
 * live topic. Pure and unit-testable — no Kafka, no Spring.
 */
public final class ReplayTopicResolver {

    private ReplayTopicResolver() {
    }

    public static String toReplayTopic(String liveTopic, String runId) {
        if (liveTopic == null || liveTopic.isBlank()) {
            throw new IllegalArgumentException("liveTopic is required");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        int firstDot = liveTopic.indexOf('.');
        if (firstDot <= 0) {
            throw new IllegalArgumentException("unrecognised topic (no namespace segment): " + liveTopic);
        }
        String namespace = liveTopic.substring(0, firstDot);   // e.g. "options" / "underlying"
        String remainder = liveTopic.substring(firstDot + 1);  // e.g. "databento.display"
        String replay = namespace + ".replay." + runId.trim() + "." + remainder;
        if (!(replay.startsWith("options.replay.") || replay.startsWith("underlying.replay."))) {
            throw new IllegalArgumentException("resolved topic is not in the replay namespace: " + replay);
        }
        return replay;
    }
}
