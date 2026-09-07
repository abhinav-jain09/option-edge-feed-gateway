# ES Footprint gateway design — Codex round 7 (2026-09-07)

Reviewed: revision 7. Verdict: REQUEST_CHANGES (3 findings, dispositioned in revision 8's change log).

Round-6 dispositions:

1. **RESOLVED.** The upstream change set exists at `/private/tmp/oe-footprint-compression` on branch `feat/es-footprint-uncompressed`. `EsCvdRuntime.producerProps` explicitly sets `compression.type=none` at `EsCvdRuntime.java:1364`; `EsCvdRuntimeTest.java:102-109` pins it; and `ES-FOOTPRINT-DESIGN.md` records amendment 12. Section 4 accurately makes G-R8 conditional on merging that change set.

2. **NOT RESOLVED.** G-R8a is not implementable with the AS-IS `PartitionRefresh` contract as written. `PartitionRefresh.apply()` discovers, merges, and calls `consumer.assign(merged)` internally at `FeedGatewayService.java:3665-3671`. The callers receive the result only afterward at `:2310-2313` and `:2677-2680`. Therefore no gate can sit “after `apply`, before the caller assigns”; the caller does not assign. Keeping all four topics in the immutable `Set.copyOf(topics)` is correct, but the design must explicitly require either:

   - changing `apply()` to return unassigned discoveries for gate/filter/assign at both callers, or
   - injecting the gate inside `apply()` before its internal assignment.

   In either case, `Refresh.added()`, seeking, and caught-up bookkeeping must contain only admitted partitions.

3. **NOT RESOLVED.** Section 1 line 79 still says the unauthenticated CVD route follows the explicit-auth convention and retains the empty ``GatewayController.java:`` anchor. The valid authentication precedent is `LiquidityHistoryController.java:86`; `GatewayController.java:51` supports only typed binding/backfill behavior.

Section-1 anchor verification:

- The CVD wiring, delivery, view, hello, backfill, allowlist, consumer-setting, and test anchors are substantively supported.
- Line 79 remains incorrect as described above.
- Line 84 incorrectly says the caller assigns after `PartitionRefresh.apply()`. It also labels constructor lines `:2303` and `:2674` as call sites; the actual `apply()` calls are `:2310` and `:2677`.

NEW findings:

1. **HIGH — `ES-FOOTPRINT-GATEWAY-DESIGN.md:112` — acceptance test contradicts G-R8a.** G-R11(17) still requires `addEsFootprintTopics` to add only validated topics and says a later-valid topic appears in the “next topic set.” G-R8a correctly requires all four topics to be present from construction because `PartitionRefresh` snapshots an immutable topic set. Required change: rewrite the test to assert all four topics are always in both immutable discovery sets, while only validated partitions enter assignments.

2. **HIGH — `ES-FOOTPRINT-GATEWAY-DESIGN.md:84,109`; `FeedGatewayService.java:3665-3671` — the documented refresh/assignment seam does not exist.** Required change: specify the concrete contract/API change or an in-`apply()` validation callback, and update both state-consumer flows and lifecycle tests accordingly.

3. **MEDIUM — `ES-FOOTPRINT-GATEWAY-DESIGN.md:79` — stale authentication statement and empty anchor remain despite the revision-7 disposition.** Required change: cite `LiquidityHistoryController.java:86` for explicit authentication and describe the CVD route solely as the typed-binding/backfill precedent.

VERDICT: REQUEST_CHANGES
