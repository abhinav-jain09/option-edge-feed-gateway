# Chart delivery campaign source binding

Gateway Jenkins build 851 did not publish an image. Its Linux ES4 run passed 1,973 tests and a separate context smoke test. The full 50-mutation footprint campaign reproduced the existing outcomes: 49 killed mutations and one already characterized survivor. The final `footprint-reqstate.sh --check` refused the checked-in campaign because it still bound FeedGatewayService and OutboundChannel to their preceding source hashes.

This change replaces only `ES-FOOTPRINT-CAMPAIGN.json` with the actual captured rerun archived by build 851 at source `5e64c32871b86450b36d0207e9ba9c9acdb9d572`. The artifact SHA-256 is `2500199fba271c7037396f484b0908682bedeeb2583b8d5b9384a475aabf5f3d`. No production code, mutation specification, gate, timeout or outcome is changed.

Artifact: [build 851 campaign](http://localhost:8085/job/option-edge-feed-gateway/851/artifact/.jenkins-tmp/es4-tests-851/footprint-reverify.json). The local controller URL is session-specific; the committed record preserves its own source/patch/baseline/output provenance. It is a reproducible CI record, not a signed attestation or a passing release receipt.

Validation: the existing requirement-state generator accepts all current source bindings, and `bash scripts/footprint-reqstate.sh --check` passes. Regenerating the design table produces no change because claims and outcomes are unchanged. `git diff --check` passes. The next Jenkins run must repeat the full suite/campaign and all release checks; this record refresh grants no deployment bypass.

## Refresh for the liq-AA relay changes (build 872)

Build 872 (gateway source `1696106671acf0663cce54deec23a5bd76941c2e`) passed the full suite and reproduced the 50-mutation campaign (49 killed, the same one characterized survivor), then refused to emit because `FeedGatewayService.java` had moved (PRs 332 and 333, the targets relay). `ES-FOOTPRINT-CAMPAIGN.json` is replaced by the record captured by that run (`.jenkins-tmp/es4-tests-872/footprint-reverify.json`, 395,779 bytes, SHA-256 `d3baeac0394c304db8f56f09c946081f5bd9b5babad38d0152921fd1547ba359`). No code, spec, gate or outcome changes; `bash scripts/footprint-reqstate.sh --check` reports the campaign matches. The next Jenkins run repeats everything.

## Refresh for the ES MBO relay (build 874)

Build 874 (gateway source `2df1beca9d9198fadd5dcb3f0d21eb30feb5284f`, the EsMboRelay change) passed the full suite (1,422 tests in the contracts run, the gateway suite on the Linux ES4 host) and reproduced the 50-mutation campaign (49 killed, the same one characterized survivor; every outcome identical to the checked-in record), then refused to emit because `FeedGatewayService.java` had moved. `ES-FOOTPRINT-CAMPAIGN.json` is replaced by the record captured by that run (`.jenkins-tmp/es4-tests-874/footprint-reverify.json`, 395,779 bytes, SHA-256 `1885305f26ab4ca8a041831bae6ec61113cc18c4df6cc92ddb69d1afa8470cdd`). No code, spec, gate or outcome changes. The next Jenkins run repeats everything.
