# ES Footprint gateway design — Codex round 6 (2026-09-07)

Reviewed: revision 6. Verdict: REQUEST_CHANGES (3 unresolved, dispositioned in revision 7's change log).

Round-5 dispositions:

1. **Finding 1 — NOT RESOLVED.**  
   G-R8’s arithmetic is reasonable only if fact (iii), uncompressed end-to-end delivery, is enforced. It is not: [`EsCvdRuntime.java:1349`](/private/tmp/oe-footprint-wire/es-cvd-service/src/main/java/com/optionsedge/processing/escvd/EsCvdRuntime.java:1349) configures serializers, acknowledgements, and idempotence but does not set `ProducerConfig.COMPRESSION_TYPE_CONFIG` to `none`. Therefore the claimed 25 MiB Kafka bound and 180 MiB total are not presently established.

   Conditional on uncompressed records, the revised per-record allowance is conservative for the stated empty-header producer contract: value `String` allocation is counted separately; key `String` ≤208 B, `ConsumerRecord` ≤96 B, empty `RecordHeaders` ≤32 B, and collection allocation ≤64 B per record gives ≤400 B × 100 = 40 KiB. The 100-record limit is now used explicitly and only for delivered-record overhead. But this cannot repair the missing fact (iii).

2. **Finding 2 — NOT RESOLVED.**  
   G-R8a correctly permits unknown topics and prevents unvalidated topics from initial assignment, but its described re-adoption path does not exist for the proposed setup. The gateway’s current state consumers manually `assign(...)` and use `PartitionRefresh`; that mechanism discovers topics created after startup only from the topic set captured when the refresh object is constructed. `PartitionRefresh` makes an immutable copy with `Set.copyOf(topics)` at [`FeedGatewayService.java:3605`](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:3605). Because G-R8a says `addEsFootprintTopics` initially adds only validated topics, an absent/unvalidated topic is excluded from that immutable set and cannot later be discovered by the existing refresh path. No consumer restart, command queue, or other re-adoption mechanism is specified.

3. **Finding 3 — NOT RESOLVED.**  
   The section-1 anchor remains incorrect and incomplete at [`ES-FOOTPRINT-GATEWAY-DESIGN.md:71`](/private/tmp/oe-gw-footprint/ES-FOOTPRINT-GATEWAY-DESIGN.md:71). It still says the unauthenticated CVD route “follows” the explicit-auth convention and gives the empty anchor ``GatewayController.java:``. A valid precedent is `LiquidityHistoryController`, whose explicit call is at [`LiquidityHistoryController.java:86`](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryController.java:86). The CVD route remains valid only for typed binding and backfill semantics.

New findings:

- **HIGH — [`ES-FOOTPRINT-GATEWAY-DESIGN.md:126`](/private/tmp/oe-gw-footprint/ES-FOOTPRINT-GATEWAY-DESIGN.md:126) — upstream prerequisite is stated as present when it is absent.** The text says `EsCvdRuntime.producerProps` sets `compression.type=none`, but the supplied revision-13 implementation does not. Required change: amend and test the upstream producer before relying on fact (iii), or rewrite the design and bound for the actual producer behavior.

- **HIGH — [`ES-FOOTPRINT-GATEWAY-DESIGN.md:100`](/private/tmp/oe-gw-footprint/ES-FOOTPRINT-GATEWAY-DESIGN.md:100) — late validation has no implementable consumer handoff.** Required change: specify a concrete Kafka-consumer-thread-safe mechanism, such as restarting both state consumers with rebuilt validated topic maps or queuing reconfiguration onto each owning poll thread. Define cache/live seek behavior and cache readiness during adoption, and pin an end-to-end lifecycle test: absent at startup → validated later → assigned by both consumers.

VERDICT: REQUEST_CHANGES
