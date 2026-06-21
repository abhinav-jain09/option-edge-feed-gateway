package app.feedgateway.mtsession.gateway;

import java.util.List;

/**
 * Lists the CALLER's orchestrated replay runs for the UI run-picker (PR-2 of the runId bridge). The
 * orchestrator filters by ownership ({@code issuer|subject}); the gateway only forwards the caller's bearer
 * and projects the result to {@link ReplayRunView}. Implementations MUST be FAIL-CLOSED: any error,
 * timeout, or non-2xx yields an EMPTY list — never a raw orchestrator error and never another user's runs.
 */
public interface ReplayRunLister {
    List<ReplayRunView> listRuns(String bearerToken);
}
