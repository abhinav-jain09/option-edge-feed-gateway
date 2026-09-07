# ES Footprint gateway design — Codex round 4 (2026-09-07)

Reviewed: revision 4. Verdict: REQUEST_CHANGES (2 findings, dispositioned in revision 5's change log).

Round-3 dispositions:

1. **Round-3 finding 1 — NOT RESOLVED.**  
   G-R7 fixes the response-construction portion, and the HotSpot checks make the retained-layout assumptions enforceable. However, G-R8’s Kafka term remains unproven, so the 180 MiB total and derived inequalities are not established.

2. **Round-3 finding 2 — RESOLVED.**  
   G-R9 adds `consumer` to `drops_total`, precisely defines cache/live ownership and broadcast identities, and assigns one rejection reason through explicit precedence. G-R11 includes the necessary overlapping-condition assertions.

Verification:

- **G-R7 streamed writer:** The stated implementation is feasible with the existing controller/service organization. The permit is acquired before snapshotting and released only after flush/failure; the coordinator lock covers reference selection only and is explicitly excluded from network writing. The arithmetic is sound: `262,144 + 65,536 + 400 < 0.32 MiB`; four requests remain below 1.3 MiB. The retained `String` objects are already charged to the view.
- **HotSpot layout verification:** `HotSpotDiagnosticMXBean.getVMOption(...)` can query `UseCompressedOops`, `UseCompressedClassPointers`, and `CompactStrings` on the Java 21 HotSpot runtime. Requiring all three to be `true` is sufficient for the stated `String`, array, reference, and `TreeMap.Entry` arithmetic.
- **Retained-view arithmetic:** `32,000 × 296 = 9,472,000 B = 9.033 MiB`; plus 144 MiB payload and less than 1 KiB coordinator state gives less than 153.1 MiB.
- **Recomputed total:** `153.1 + 1.3 + 24 = 178.4 MiB`, correctly rounded upward to 180 MiB—but the 24 MiB Kafka operand is not proven.
- **Inequalities:** Given a valid 180 MiB contribution, `180 + 128 = 308 MiB` heap headroom and `180 + 256 = 436 MiB` working-set headroom are correct. The current deployment also correctly fails `limit − Xmx ≥ 512 MiB` because both are 1536 MiB.
- **G-R9:** The live/cache drop attribution and both broadcast identities now form one exact contract. The precedence tests pin busy over bad cursor, bad cursor over session mismatch, and session mismatch over a served page.

New findings:

1. **HIGH — line 83 — Kafka transient bound is still unproven.**  
   `GatewaySettings` and `FeedGatewayService` do enforce 100 records, 4 MiB `fetch.max.bytes`, and 512 KiB `max.partition.fetch.bytes`. But Kafka explicitly states that `max.poll.records` does not limit underlying fetching: fetched records are cached and returned incrementally by later polls. The design provides no basis for “the completed-fetch buffer holds at most one response per broker.” Kafka also permits parallel fetches and oversized first batches. Separately, upstream validates `max.message.bytes` only as a lower bound; its default is not an enforced upper ceiling, so `≤ 1,048,588 B` does not follow. See the official [Kafka consumer configuration](https://kafka.apache.org/40/configuration/consumer-configs/).

   **Required change:** derive a conservative bound for all simultaneously cached/completed fetch data from the actual Kafka-client behavior and enforced broker/topic limits, or introduce an implementation/configuration mechanism that imposes such a bound. Validate an upper bound for each footprint topic’s effective `max.message.bytes`, account for every broker that may lead assigned partitions, then recompute the Kafka term, total, and both inequalities.

2. **MEDIUM — line 82 — the declared flag/auth/binding order is not implementable as written under the AS-IS controller convention.**  
   This application does not have an existing JWT servlet filter for these routes; authenticated REST controllers call `LiquidityHistoryAuth.authenticate(...)` inside the handler. Conversely, Spring converts typed `long`/`int` parameters before entering that handler. Thus the stated order “flag → existing filter → Spring typed binding” cannot occur: malformed typed parameters are rejected before either an in-handler flag check or in-handler authentication.

   **Required change:** define an implementable order matching the existing explicit-auth convention—normally Spring binding → handler flag check → explicit `LiquidityHistoryAuth` check—or accept raw parameters and perform explicit parsing after the chosen flag/auth checks. Correct “existing filter,” and add overlap tests covering flag-off/auth failure with malformed typed parameters.

VERDICT: REQUEST_CHANGES
