# ES Footprint gateway relay — CODE gate, Codex round 3 (2026-09-07)

Reviewed commit: 0ca132c. Verdict: APPROVE.

## Round-2 findings

4. **RESOLVED — production orchestration seams are behaviorally tested.** Both state consumers call `bootstrapAssign`; the live path calls `liveBootstrapSeek` before polling and `liveAdoptionSeek` after partition adoption. Tests execute these production methods against mocked consumers, verifying filtered assignments, retry ordering, first-attempt END seeking, and late-adoption END seeking. Evidence: [FeedGatewayService.java:864](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:864), [FeedGatewayService.java:2528](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:2528), [FeedGatewayService.java:2920](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:2920), [FootprintSeamTest.java:84](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintSeamTest.java:84), [FootprintSeamTest.java:126](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintSeamTest.java:126), [FootprintSeamTest.java:152](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintSeamTest.java:152).

5. **RESOLVED — response-buffer ceiling is enforced.** `writePage` requests 64 KiB, verifies the effective value using `getBufferSize()`, and returns HTTP 503 with `Retry-After` when the reported buffer exceeds the ceiling. The bounded and oversized cases are tested. Evidence: [GatewayController.java:148](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/GatewayController.java:148), [FootprintBackfillControllerTest.java:129](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintBackfillControllerTest.java:129).

## Compatibility and visibility

The CVD/SPX-levels relocation preserves behavior:

- Retry remains cache-window seek → SPX-level resume → footprint-only END override.
- Initial connection remains all-partitions END → SPX handoff seek.
- The seam executes before the first poll.
- The footprint override filters by footprint topic, so it does not alter CVD/SPX positions.

Evidence: [FeedGatewayService.java:875](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:875), [FeedGatewayService.java:2921](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:2921), [CvdSpxLevelsWiringTest.java:460](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java:460), [CvdSpxLevelsWiringTest.java:579](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java:579).

The `TopicBinding` and `Refresh` widening is safe: both remain package-private nested records, expose immutable record fields, and do not expand the public API. `Refresh` production construction still uses invariant-preserving factories. Evidence: [FeedGatewayService.java:711](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:711), [FeedGatewayService.java:3984](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:3984).

## New findings

None.

The supplied `mvn -o -q test` evidence—1,072 tests, 0 failures—is accepted. My local rerun was prevented by the managed filesystem denying Maven writes under `target/`; no files were modified.

VERDICT: APPROVE
