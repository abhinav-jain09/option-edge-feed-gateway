# ES Footprint gateway design — Codex round 8 (2026-09-07)

Reviewed: revision 8. Verdict: REQUEST_CHANGES (2 MEDIUM findings, dispositioned in revision 9's change log).

Round-7 dispositions:

1. **RESOLVED.** G-R11(17) now requires all four footprint topics in both immutable discovery sets while restricting assignments to admitted partitions.

2. **RESOLVED.** G-R8a now defines an implementable seam: `Predicate<String> topicAdmit` is evaluated inside `PartitionRefresh.apply()` after discovery but before `addedPartitions`, merge, and the internal `consumer.assign(...)`.

   This placement matches `FeedGatewayService.java:3633-3671`. Rejected partitions are excluded before `added` is computed, so they cannot enter:

   - `Refresh.added()`;
   - the cache seek and end-offset/caught-up bookkeeping at `:2312-2334`;
   - the live seek at `:2679-2680`;
   - `Refresh.partitions()` or the consumer assignment.

   The bootstrap requirement is also correctly placed. Filtering the `partitionsFor(...)` results before the assignments at `:2280` and `:2666` means the subsequent cache seek, bootstrap end offsets, catch-up barriers, live seek, and initial `PartitionRefresh` assignment state all contain admitted partitions only.

3. **RESOLVED.** Section 1 now correctly anchors explicit authentication to `LiquidityHistoryController.java:86` and limits the CVD route precedent to typed binding and backfill behavior.

Section 1 line-by-line verification:

- **Lines 82–83:** Supported by `FeedGatewayService.java:1905,2041,2053-2058,2833-2848` and `GatewaySettings.java:264-276`.
- **Line 84:** Supported by `FeedGatewayService.java:9656-9690`.
- **Line 85:** Supported by `FeedGatewayService.java:908-916,10219-10235`.
- **Line 86:** Supported by `GatewayController.java:50-72` and `FeedGatewayService.java:10247-10269`.
- **Line 87:** Corrected and supported by `LiquidityHistoryController.java:86` and `GatewayController.java:50-72`.
- **Line 88:** Supported by `FeedGatewayService.java:11493-11494`.
- **Lines 89–91:** Consistent with the previously verified external page, test, and deployment anchors.
- **Line 92:** Accurate. `PartitionRefresh` snapshots its topics at `:3605-3607`, discovers at `:3633-3639`, assigns internally at `:3665-3671`, and callers seek/book only `Refresh.added()` at `:2310-2334` and `:2677-2680`.
- **Line 93:** Supported by `GatewaySettings.java:1071-1081` and `FeedGatewayService.java:12294-12296`.
- **Lines 95–103:** Consistent with upstream revision 13’s grammar, bounds, keys, and publication contract.

NEW findings:

1. **MEDIUM — `ES-FOOTPRINT-GATEWAY-DESIGN.md:117,120` — contradictory constructor-call-site count.** G-R8a says “the existing six constructor call sites pass `t -> true`; the two STATE consumers pass `footprintGate::admit`,” while G-R11(17) again requires all six existing sites to pass the always-true predicate. There are six total call sites, including the two state consumers at `FeedGatewayService.java:2303,2674`; both statements cannot hold. **Required change:** specify that the other four call sites pass `t -> true`, while the two state call sites pass `footprintGate::admit`; correct G-R11(17) accordingly.

2. **MEDIUM — `ES-FOOTPRINT-GATEWAY-DESIGN.md:143-149` — merged prerequisite is still documented as unmerged and conditional.** The compression prerequisite has merged as options-edge-processing PR #757, but §4 still says “not yet merged,” “in its own CODE gate,” and “conditional on that merge.” **Required change:** record PR #757 as merged and remove the obsolete conditional/until-merge wording, while retaining G-R8a’s runtime validation as the continuing safety guard.

VERDICT: REQUEST_CHANGES
