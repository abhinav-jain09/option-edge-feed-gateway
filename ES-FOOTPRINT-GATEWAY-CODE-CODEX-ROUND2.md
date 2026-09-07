# ES Footprint gateway relay — CODE gate, Codex round 2 (2026-09-07)

Reviewed commit: the round-1 remediation. Verdict: REQUEST_CHANGES (round-1 #1/#2/#3 RESOLVED; #4 and #5 not, both remediated in the next commit).

## Round-1 findings

1. **RESOLVED — live consumer seeks footprint partitions to END.** Retry handling first applies the shared cache-window seek and then overrides footprint partitions with `seekToEnd`; late-adopted footprint partitions receive the same treatment. Evidence: [FeedGatewayService.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:855), [FeedGatewayService.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:2883), [FeedGatewayService.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:2895). The cache-consumer replay path remains unchanged.

2. **RESOLVED — bars cursor is exclusive at the epoch boundary.** `afterMs >= EPOCH_MAX_MS` produces no rows, and `+1` occurs only after proving the cursor is below the maximum, eliminating both overflow and epoch-max replay. Evidence: [FootprintViews.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FootprintViews.java:224).

3. **RESOLVED — startup preflight is failure-atomic.** Layout and topic validation now run before the `running` CAS and before executor allocation. An exception therefore leaves `running=false` and no executor allocated. Evidence: [FeedGatewayService.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:795), [FeedGatewayService.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:956), [FeedGatewayService.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:963).

4. **NOT RESOLVED — G-R11 is still not fully behavioral.** The predicate itself is now executed against a mocked consumer and correctly checks `Refresh.added()`, merged assignment, and rejecting-then-accepting admission. However, the required live retry/adoption test still reads Java source and searches for call text instead of executing `runLiveConsumerOnce` or an equivalent production orchestration seam: [FootprintSeamTest.java](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintSeamTest.java:108). Thus a refactor or control-flow change could leave the calls textually present while preventing their execution. The “both consumer flows” test also invokes the same synthetic helper twice under different names rather than exercising the two production consumer paths: [FootprintSeamTest.java](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintSeamTest.java:87). This does not meet the Round-1 remediation’s behavioral retry/seek requirement or the stated military-completeness bar.

5. **NOT RESOLVED — the fixed 64 KiB/`<0.32 MiB` memory proof remains unestablished.** Removing `BufferedOutputStream` eliminates one explicit page-side buffer, but `HttpServletResponse.setBufferSize(64 KiB)` requests a servlet buffer; it does not prove that the container allocates exactly 64 KiB or that no other output buffering exists. The exception handler explicitly proceeds with an uncontrolled existing buffer, contradicting the “ONLY buffer” assertion: [GatewayController.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/GatewayController.java:139), [GatewayController.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/GatewayController.java:148). No `getBufferSize()` verification or container-level invariant closes the bound. Consequently the design’s exact transient-memory arithmetic remains stronger than the implementation proves.

## New findings

None beyond the unresolved Round-1 findings above.

The supplied test evidence—`mvn -o -q test`: 1,070 tests, 0 failures—is accepted, but passing tests do not close the two coverage/proof gaps identified above.

VERDICT: REQUEST_CHANGES
