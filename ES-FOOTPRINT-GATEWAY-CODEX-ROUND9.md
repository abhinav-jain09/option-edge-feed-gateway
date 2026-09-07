# ES Footprint gateway design — Codex round 9 (2026-09-07)

Reviewed: revision 9. Verdict: REQUEST_CHANGES (1 MEDIUM finding, dispositioned in revision 10's change log).

Round-8 dispositions verified:

- Source contains exactly six `new PartitionRefresh(...)` sites: lines 1739, 2087, 2125, 2201, 2303, and 2674. G-R8a and G-R11(17) consistently assign `t -> true` to the four non-state sites and `footprintGate::admit` to the two state sites.
- options-edge-processing PR #757 is present on `origin/main`; its source explicitly sets `compression.type=none`, with a pinning test. Section 4 correctly records it as merged and retains runtime validation.

NEW findings:

1. **MEDIUM — doc lines 124–127 — validation metrics fall outside the document’s purportedly exhaustive metrics contract.** G-R8a introduces `gateway_footprint_topic_validated{topic}` and `gateway_footprint_topic_validation_failures_total{topic,reason}`, but G-R9’s exhaustive label domains and series list omit both. G-R11(12) consequently verifies only “every G-R9 series,” while G-R11(17) does not require assertions for these metrics. **Required change:** add both series, their complete `topic` and `reason` label domains, initialization/increment semantics, and flag-off behavior to G-R9; extend G-R11 to pin their initial and failure/success values.

VERDICT: REQUEST_CHANGES
