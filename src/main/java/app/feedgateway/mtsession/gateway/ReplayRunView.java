package app.feedgateway.mtsession.gateway;

/**
 * A projected, ownership-safe view of an orchestrated replay run for the UI run-picker (PR-2 of the runId
 * bridge). Deliberately a SUBSET of the orchestrator's {@code ReplayRunSnapshot}: it carries only what the
 * picker needs to identify a run and OMITS {@code ownerId} and every internal/operational field (topics,
 * processor ids, evidence paths, record counts, etc.). Any field may be {@code null} if the orchestrator
 * omits it; only {@code runId} is guaranteed non-blank by the lister.
 */
public record ReplayRunView(String runId, String state, String replayDate, String startTime, String endTime) {
}
