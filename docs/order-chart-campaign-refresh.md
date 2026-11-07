# Chart delivery campaign source binding

Gateway Jenkins build 851 did not publish an image. Its Linux ES4 run passed 1,973 tests and a separate context smoke test. The full 50-mutation footprint campaign reproduced the existing outcomes: 49 killed mutations and one already characterized survivor. The final `footprint-reqstate.sh --check` refused the checked-in campaign because it still bound FeedGatewayService and OutboundChannel to their preceding source hashes.

This change replaces only `ES-FOOTPRINT-CAMPAIGN.json` with the actual captured rerun archived by build 851 at source `5e64c32871b86450b36d0207e9ba9c9acdb9d572`. The artifact SHA-256 is `2500199fba271c7037396f484b0908682bedeeb2583b8d5b9384a475aabf5f3d`. No production code, mutation specification, gate, timeout or outcome is changed.

Artifact: [build 851 campaign](http://localhost:8085/job/option-edge-feed-gateway/851/artifact/.jenkins-tmp/es4-tests-851/footprint-reverify.json). The local controller URL is session-specific; the committed record preserves its own source/patch/baseline/output provenance. It is a reproducible CI record, not a signed attestation or a passing release receipt.

Validation: the existing requirement-state generator accepts all current source bindings, and `bash scripts/footprint-reqstate.sh --check` passes. Regenerating the design table produces no change because claims and outcomes are unchanged. `git diff --check` passes. The next Jenkins run must repeat the full suite/campaign and all release checks; this record refresh grants no deployment bypass.
