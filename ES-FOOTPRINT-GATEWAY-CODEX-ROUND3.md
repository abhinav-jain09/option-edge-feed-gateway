# ES Footprint gateway design — Codex round 3 (2026-09-07)

Reviewed: revision 3. Verdict: REQUEST_CHANGES (2 findings, dispositioned in revision 4's change log).

1. Round-2 finding 1 — **NOT RESOLVED**

G-R8 replaces the average-size assumption with byte/count budgets and gets the retained-view subtotal essentially right, but the total heap proof is still unsound because its transient-response and Kafka-batch bounds are not conservative. See new finding 1.

2. Round-2 finding 2 — **RESOLVED**

G-R8 and §4 now require both heap headroom and container/native headroom. The current deployment has `-Xmx1536m` and a `1536Mi` container limit, so it fails `limit − Xmx ≥ 512 MiB`, exactly as revision 3 states. Enabling requires a deployment change and recorded measurements.

3. Round-2 finding 3 — **NOT RESOLVED**

G-R9 supplies label domains and most increment/overlap rules, but the drop series cannot unambiguously represent the cache/live semantics it defines, and simultaneous backfill rejection conditions have no precedence. Therefore G-R11 still cannot pin one exact contract for all executions. See new finding 2.

G-R8 arithmetic verification:

- Compact `String`, assuming HotSpot compact strings, compressed ordinary object pointers, compressed class pointers, and 8-byte alignment:

  - `String`: 12-byte header + 4-byte `byte[]` reference + 4-byte hash + coder/hash-state bytes and padding = 24 B.
  - Backing `byte[]`: 16-byte aligned array header plus one byte per ASCII character, then 8-byte alignment.
  - Combined maximum is `payload + 48 B`. This bound is conservative.
  - However, heap below 32 GiB does not itself guarantee that compressed pointers or compact strings are enabled; JVM options can disable them. The proof must make these deployment/runtime invariants explicit.

- Key sizes:

  - Bar key: at most `7 + 1 + 19 = 27` Latin-1 bytes.
  - Outcome key: at most `7 + 1 + 19 + 1 + 128 = 156` bytes.
  - Therefore the stated `≤160 B` payload and `≤208 B` complete key-string allocation are valid.

- `TreeMap.Entry` with compressed references:

  - Header 12 B.
  - Five references (`key`, `value`, `left`, `right`, `parent`) = 20 B.
  - `boolean color` plus alignment = 8 B.
  - Total = 40 B. Correct.

- Per-entry non-JSON overhead:

  - Key string: ≤208 B.
  - Entry: 40 B.
  - JSON value string/array overhead: ≤48 B.
  - Total: `208 + 40 + 48 = 296 B`. Correct.

- Count-bound overhead:

  - `12,000 + 20,000 = 32,000` entries.
  - `32,000 × 296 = 9,472,000 B`.
  - `9,472,000 / 1,048,576 = 9.033 MiB`, correctly rounded to 9.04 MiB.

- Retained views:

  - JSON payload: `128 + 16 = 144 MiB`.
  - Entry/string overhead: approximately 9.033 MiB.
  - Coordinator: less than 0.001 MiB as described.
  - Total: approximately 153.034 MiB, so `≤153.1 MiB` is valid under the stated layout assumptions.

- Nominal response content:

  - `100 × 262,144 + 4,096 = 26,218,496 B`.
  - This is about `25.006 MiB`, so the stated 25.0 MiB content size is reasonable.

- Transient bound:

  - The conclusion `25.0 MiB builder + 25.0 MiB String = 50.1 MiB` is not established.
  - A `StringBuilder` grown by repeated appends can have capacity substantially greater than its final length.
  - Returning a `String` can additionally require an HTTP UTF-8 output byte array or encoder buffers while the builder/result and selected record references remain live.
  - The requirement neither mandates exact pre-sizing nor specifies a streaming/byte-oriented implementation.
  - Thus `4 × 50.1 = 200.4 MiB` and the resulting 360 MiB total are not proven.

- Kafka transient:

  - `max.partition.fetch.bytes × 4 topics` is not a valid poll-batch bound. The gateway actually configures `GATEWAY_KAFKA_FETCH_MAX_BYTES`, `GATEWAY_KAFKA_MAX_PARTITION_FETCH_BYTES`, and `GATEWAY_KAFKA_MAX_POLL_RECORDS`; topic count is not partition count, and Kafka may return an oversized first record batch.
  - The document also cites a 1 MiB client default, while this gateway’s default `max.partition.fetch.bytes` is 512 KiB.
  - Therefore the added 4 MiB term is not established.

- Concurrency:

  - G-R7’s semaphore limits served backfills to four and rejects excess requests with 503. This is a valid concurrency bound provided the permit is acquired before page snapshotting/response construction and released only after construction finishes. The text should state that lifecycle explicitly.

- Contingency inequalities:

  - Heap: `Xmx − H_peak ≥ 360 + 128 = 488 MiB`.
  - Container working set: `limit − W_peak ≥ 360 + 256 = 616 MiB`.
  - Native reservation: `limit − Xmx ≥ 512 MiB`.
  - Current deployment: `1536 − 1536 = 0 MiB`, so it fails the third inequality.
  - Example deployment: `limit=2560Mi`, `Xmx=1536Mi` gives `1024 MiB ≥ 512 MiB`; the first two inequalities still depend on the recorded `H_peak` and `W_peak`.
  - The inequalities themselves are coherent, but their 360 MiB input must be corrected before they constitute a proof.

G-R9 overlap verification against G-R3/G-R4/G-R7:

- Oversize keyed live record: `records_total` + `drops_total{oversize}`; no view admission and no broadcast. Consistent with G-R3.
- Shape/stale keyed live record: `records_total` + the applicable drop + `broadcast_total`. Consistent with G-R3/G-R4.
- Admitted keyed live record: `records_total` + `broadcast_total`; possibly eviction or rollover depending on admission effects.
- Cache-consumer keyed record: `records_total`; admission/drop/eviction/rollover applies, but never broadcast. The missing `consumer` dimension on `drops_total` makes this overlap insufficiently precise.
- Rollover clearing is excluded from eviction and increments rollover once per date advance. Consistent with G-R4.
- Every enabled backfill call increments requests; bad cursor, mismatch, or busy increments one rejection. Individually consistent with G-R7, but precedence is undefined when multiple conditions apply.

New findings:

1. **HIGH — line 75 — transient heap and Kafka-poll bounds are unproven.**  
   The proof assumes a `StringBuilder` has exactly its final capacity, omits HTTP encoding/output allocations, and incorrectly bounds Kafka data by one `max.partition.fetch.bytes` allowance per topic. Consequently the claimed 200.4 MiB backfill bound and 360 MiB total do not follow.

   **Required change:** mandate and bound a concrete response implementation—such as pre-sized byte-oriented construction or streaming—and include all simultaneously live builder/result/encoding buffers. Derive the Kafka bound from enforced `fetch.max.bytes`, `max.partition.fetch.bytes`, `max.poll.records`, partition cardinality, and Kafka’s oversized-first-batch behavior. Recompute the total and both measurement inequalities. Also make the JVM layout assumptions enforceable or runtime-verified.

2. **MEDIUM — lines 76 and 78 — metrics overlap remains ambiguous across consumers and competing route rejections.**  
   `records_total` distinguishes `cache` and `live`, while `drops_total` does not. Yet G-R9 refers to live-path oversize drops when defining broadcasts, without specifying whether the shared drop counter increments during cache admission, live admission, or both. Separately, a request can be both busy and malformed or session-mismatched, but no validation/acquisition precedence determines its HTTP result and single rejection reason.

   **Required change:** either add `consumer` to the drop series or explicitly define which consumer paths increment it and how live-only drop counts are obtained. Define route processing precedence—for example flag/auth → semaphore → cursor validation → session check, or another chosen order—and add overlapping-condition tests with exact counter and HTTP expectations.

VERDICT: REQUEST_CHANGES
