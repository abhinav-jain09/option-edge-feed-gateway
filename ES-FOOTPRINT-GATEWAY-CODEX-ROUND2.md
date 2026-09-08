# ES Footprint gateway design — Codex round 2 (2026-09-07)

Reviewed: revision 2. Verdict: REQUEST_CHANGES (3 findings, dispositioned in revision 3's change log).

Gate-2 DESIGN review, round 2, against `origin/main` `47bd409` plus the two documentation commits.

All AS-IS anchors were reverified against the worktree, the external ES CVD page, upstream revision 13, and the es4 deployment manifest. The stated source behavior is accurate. The external page ignores additional hello fields, and D5 is consistent with the source’s explicit classification and allowlisting of `es-cvd`/`es-cvd-bar` as ES-global broadcasts.

Round-1 dispositions:

1. **RESOLVED** — G-R4/G-R6 define one session coordinator, one lock, atomic hello snapshots, cross-view rollover, and both bootstrap orders.

2. **NOT RESOLVED** — G-R8 introduces enforceable byte/count budgets and deployment gating, but its retained-heap proof and deployment contingency are unsound. See new findings 1–2.

3. **RESOLVED** — G-R4 requires a present canonical ISO date, parsed before mutation, with appropriate invalid-date and boundary tests.

4. **RESOLVED** — The fixed-width ordering claim is correct. Epoch values are nonnegative and at most 15 decimal digits, so `%019d` preserves numeric order throughout `[0, 253402300799999]`. The full key therefore orders by timeframe, numeric timestamp, then identity. Since identity is last, legal embedded `|` characters do not affect tuple ordering or make the prefix ambiguous, provided cursor parsing consumes only the first two separators as specified by the grammar/tests.

5. **NOT RESOLVED** — G-R11 now covers the previously missing behaviors, but the “exact metrics contract” remains impossible to derive because G-R9 does not define counting semantics or complete label domains. See new finding 3.

6. **RESOLVED** — D5 explicitly records the authorization decision and G-R11 tests authenticated multi-session fan-out and continued dropping of non-allowlisted events. This matches `FeedGatewayService.java:11487-11494` and `:11517-11548`.

7. **RESOLVED** — Section 1 now correctly distinguishes singular `timeframe` on bars/outcomes from the fixed-key `timeframes` object on live records.

New findings:

1. **HIGH — `ES-FOOTPRINT-GATEWAY-DESIGN.md:67` — retained-heap bound is not proven.**  
   The `17 KiB average` is neither a configured invariant nor an enforceable lower bound. At the permitted maximum counts, the document’s own simplified arithmetic is:

   `144 MiB + 32,000 × 224 B = 150.84 MiB`

   already exceeding the claimed `<150 MiB`. It also omits `String` objects, backing-array headers/alignment, and map/key ownership details. The worst-record-size example proves only that JSON payload bytes remain under the byte budgets; it does not prove total retained heap.

   **Required change:** derive a conservative bound from both byte and count limits, including value/key `String` and backing-array overhead, `TreeMap.Entry` overhead, alignment, and coordinator/accounting structures. Remove the unenforced average-size assumption or make it an enforced budget dimension.

2. **HIGH — `ES-FOOTPRINT-GATEWAY-DESIGN.md:67,93-96`; deployment manifest `es-feed-gateway.yaml:120-121,176-178` — deployment contingency checks heap only while `-Xmx` equals the container limit.**  
   The verified deployment sets both to 1536 MiB. This reserves no explicit container margin for metaspace, code cache, thread stacks, direct/network buffers, native libraries, or JVM overhead. Raising `-Xmx` and the container limit “together” does not necessarily create that margin. A pre-enable heap-headroom measurement therefore does not establish container safety.

   **Required change:** add a container-memory/RSS contingency with explicit native headroom, or require the container limit to exceed `-Xmx` by a justified margin. The deploy evidence must cover both projected post-enable heap usage and total container working-set risk.

3. **MEDIUM — `ES-FOOTPRINT-GATEWAY-DESIGN.md:68-70` — metrics counting semantics remain unspecified.**  
   It is unclear whether `records_total` counts consumed, accepted, or broadcast records; whether oversize records also increment it; when backfill request/rejection counters increment; and what the exhaustive `event` and `route` label values are. Consequently, G-R11’s “exact metrics contract” has no normative expected values.

   **Required change:** define every metric’s increment/gauge semantics and exhaustive label values, including overlap rules for received, rejected, broadcast, eviction, and session-mismatch cases; then pin those values in tests.

VERDICT: REQUEST_CHANGES
