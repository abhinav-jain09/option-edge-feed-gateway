# ES Footprint gateway design — Codex round 5 (2026-09-07)

Reviewed: revision 5. Verdict: REQUEST_CHANGES (3 findings, dispositioned in revision 6's change log).

Round-4 dispositions:

1. **Round-4 finding 1 — NOT RESOLVED.**  
   G-R8 now correctly states the fetch mechanics: one in-flight fetch per node, partitions with buffered data excluded from subsequent fetches, oversized first batches returned whole, and `max.poll.records` pacing delivery rather than fetching. It also adds the required enforced upper check of each topic’s effective `max.message.bytes`.

   However, the resulting 25 MiB bound is still not proven. Kafka fetch and `max.message.bytes` limits apply to serialized/compressed record batches. G-R8 assumes the deserialized `String` payload is no larger than the fetched share, which does not hold when compression is enabled. It also counts only `String` payload plus 48 bytes per record, omitting keys, `ConsumerRecord` objects, collections, headers, decompression storage, and related client bookkeeping. Incidentally, the stated 4.8 KiB allowance does rely on the 100-record poll limit, despite saying the bound never uses it.

2. **Round-4 finding 2 — RESOLVED.**  
   G-R7 line 90 now gives the implementable order: Spring typed binding → handler flag check → explicit `LiquidityHistoryAuth.authenticate(...)` → permit → cursor → snapshot → streaming. The derived counting rules are consistent:

   - Binding failures execute no handler code and increment no footprint counter.
   - Flag-off requests return 404 and expose no footprint request series.
   - Flag-on authentication failures increment `backfill_requests_total`, but no footprint rejection reason.
   - Permit failure precedes cursor/session evaluation and counts only `busy`.
   - Bad cursor precedes session comparison and counts only `bad_cursor`.
   - Session mismatch counts only `session_mismatch`.
   - A served page increments no rejection counter.

   G-R11 line 94 covers the important overlap cases.

New findings:

1. **HIGH — line 91 — Kafka heap arithmetic treats compressed fetch bytes as a bound on deserialized data.**  
   A partition share bounded by `max.partition.fetch.bytes` or an oversized compressed batch bounded by `max.message.bytes` can expand substantially during decompression. The claimed “deserialized Strings … ≤ the share’s bytes” therefore does not follow. Additional per-record/client allocations are also absent.

   **Required change:** either enforce uncompressed production for all four topics and verify the effective producer/topic behavior, or derive and enforce a conservative decompressed-record bound. Include record keys, `ConsumerRecord`/collection/header overhead and decompression buffers, then recompute the Kafka term, total, and deployment inequalities.

2. **HIGH — lines 84 and 91 — the startup ceiling check conflicts with optional absent topics.**  
   G-R1 requires startup to succeed when a footprint topic is absent, while G-R8 says startup reads all four topic configurations and refuses startup above the ceiling. An absent topic cannot be validated, and if it appears later with an excessive ceiling the startup-only check never protects the bound.

   **Required change:** specify that unknown-topic results are allowed without weakening other Admin failures, and require validation before an absent topic is first assigned/consumed—including topics created after startup. Consumption must remain disabled for that topic until validation succeeds.

3. **MEDIUM — line 63 — the AS-IS source anchor incorrectly says the CVD bars route performs explicit authentication.**  
   `GatewayController.cvdBars` has typed Spring parameters but does not call `LiquidityHistoryAuth.authenticate(...)`. Explicit handler authentication is demonstrated by controllers such as `LiquidityHistoryController`, not by the CVD route itself.

   **Required change:** replace the CVD authentication claim with an accurate authenticated-controller anchor, while retaining the CVD route only as the typed-binding/backfill-semantics precedent.

VERDICT: REQUEST_CHANGES
