# ES Footprint — Gateway bindings (Gate 2, F-R20)

Revision 10 — 2026-09-07 (Codex rounds 1–9: 7 + 3 + 2 + 2 + 3 + 3 + 3 + 2 + 1 findings → all dispositioned below).
Requirement doc per rule.md (doc → Codex review → code).

**As-built amendments (from the CODE build, recorded per the Implemented-Code Documentation Accuracy Rule):**
1. G-R8a, live consumer: footprint partitions always seek END on the live consumer — on a retry (after
   the shared cache-window seek) and on every late adoption. The cache consumer alone replays the
   session into the coordinator; without this the live retry would re-broadcast a day of compacted
   bars (CODE round-1 #1).
2. G-R7 bars cursor: `afterMs ≥ EPOCH_MAX_MS` returns an empty page, and the `+1` is taken only inside
   the epoch domain, so the cursor is exclusive at the boundary (CODE round-1 #2).
3. G-R8/G-R8a preflight runs BEFORE any lifecycle state moves in `start()` (no `running` flag, no
   executor), so a refusal leaves the process clean (CODE round-1 #3).
4. G-R7 streaming: the page is written straight into the servlet response stream; there is no
   page-side buffer, and the container's response buffer is REQUESTED at 64 KiB and then VERIFIED
   with `getBufferSize()` — a container reporting more refuses the page with 503 instead of streaming
   behind an unbounded buffer, so "≤ one record + 64 KiB per request" is enforced, not assumed (CODE
   rounds 1–2 #5).
5. The consumer orchestration the acceptance tests execute is the production code: `bootstrapAssign`
   (the filtered resolve-and-assign both state consumers call), `liveBootstrapSeek` (the live
   consumer's retry/first-attempt seek including the footprint END override) and `liveAdoptionSeek`
   (per-event adoption seek plus the footprint END override). G-R11's seam tests drive these against
   a mocked consumer rather than reading source text (CODE round-2 #4).

**Gate-2 CODE: APPROVED** — Codex round 3, 2026-09-07 (`ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND3.md`;
rounds 1–2 produced 5 + 2 findings, every one remediated). G-R1–G-R11 are IMPLEMENTED in
`FootprintViews`, `FootprintTopicGate`, `FeedGatewayService` and `GatewayController`, behind
`GATEWAY_ES_FOOTPRINT_ENABLED` (default off).

**Gate-2 DESIGN: APPROVED** — Codex round 10, 2026-09-07 (`ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md`; rounds
1–9 produced 7+3+2+2+3+3+3+2+1 findings, every one dispositioned in the change logs below). The code
change set that implements G-R1–G-R11 carries its own CODE gate (`ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND*.md`).
Nothing is IMPLEMENTED until that gate and the coordinated-PR protocol say so.

Upstream contract: `options-edge-processing/ES-FOOTPRINT-DESIGN.md` revision 13 (§5 grammar,
F-R14 topics and keys, F-R15 live publication, F-E8 bounds, F-R20). Downstream: the Gate-3 page in
`options-edge` (F-R21/F-R22).

### Revision-10 change log (Codex round 9 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | G-R8a's two validation series missing from G-R9's exhaustive contract and G-R11's pins | G-R9 now lists both series with full label domains, initialization, increment semantics and flag-off behaviour; G-R11 (12) and (17) pin their initial, failure and success values |

### Revision-9 change log (Codex round 8 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Constructor call-site count contradictory (six total, two of them the state consumers) | G-R8a and G-R11 (17): of the SIX existing `PartitionRefresh` constructor sites, the two state consumers pass `footprintGate::admit`, the other FOUR pass `t -> true` |
| 2 | §4 still called the compression prerequisite unmerged/conditional | §4 records options-edge-processing PR #757 as MERGED; the conditional wording is gone; G-R8a's runtime check stays as the continuing guard |

### Revision-8 change log (Codex round 7 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | G-R11 (17) contradicted G-R8a (validated-only topic sets) | Rewritten: all four topics are ALWAYS in both immutable discovery sets; only admitted partitions enter assignments |
| 2 | The gate seam "after `apply`, before the caller assigns" does not exist — `apply()` assigns internally | G-R8a now specifies the concrete API change: `PartitionRefresh` takes a topic-admission predicate evaluated INSIDE `apply()` before its merge/assign, so `Refresh.added()`, the seek and the caught-up bookkeeping see admitted partitions only; both state consumers pass the footprint gate |
| 3 | §1 auth anchor still wrong/empty | Fixed by content (the previous edit's pattern had not matched): `LiquidityHistoryController.java:86`; the CVD route is typed-binding/backfill precedent only |

### Revision-7 change log (Codex round 6 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Fact (iii) stated as present upstream while `producerProps` did not set it | The upstream change was made as its own change set (options-edge-processing `feat/es-footprint-uncompressed`: `compression.type=none` explicit, test-pinned, design amendment 12), reviewed and merged as PR #757 (see the revision-9 log) |
| 2 | Late validation had no consumer handoff (`PartitionRefresh` topic set is immutable) | G-R8a rewritten around the AS-IS mechanism: all four topics stay in the consumers' topic sets so `PartitionRefresh` discovers them; the VALIDATION runs on the consumer's own poll thread as a gate between discovery and `assign` — no cross-thread handoff, no restart; start-up refusal covers only topics that exist |
| 3 | §1 auth anchor still wrong/empty | Anchor is `LiquidityHistoryController.java:86`; the CVD route is cited for typed binding and backfill semantics only |

### Revision-6 change log (Codex round 5 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Kafka term assumed fetched bytes bound deserialized data (compression); per-record client overhead omitted | G-R8: the four topics are REQUIRED uncompressed end to end — the upstream producer pins `compression.type=none` (processing-repo amendment, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` — so fetched bytes ARE the decompressed bytes; per-record key/`ConsumerRecord`/collection overhead added; the 100-records-per-poll figure is used ONLY for that overhead and is now stated so |
| 2 | Start-up ceiling check conflicts with optional absent topics | G-R8a: a topic is consumed only after its validation (`max.message.bytes` ceiling, `compression.type`); absent at start-up ⇒ not assigned, revalidated every 60 s, adopted on success via the consumers' existing topic-adoption path; existing-but-invalid ⇒ refuse start; Admin failures other than unknown-topic remain fatal |
| 3 | AS-IS anchor wrongly credited the CVD route with explicit auth | §1 anchor corrected: explicit auth is demonstrated by the `LiquidityHistoryAuth.authenticate` caller; the CVD route is the typed-binding/backfill precedent only |

### Revision-5 change log (Codex round 4 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Kafka transient bound unproven (`max.poll.records` does not bound fetching; `max.message.bytes` only lower-bounded upstream) | G-R8 derives the bound from the client's fetch mechanics (one in-flight fetch per node, no re-fetch of a partition with buffered data) and from an ENFORCED upper ceiling on each footprint topic's effective `max.message.bytes`, read at start-up via `describeConfigs` (refuse to start above it); total and inequalities recomputed |
| 2 | Route order cited a non-existent JWT filter; Spring binding precedes the handler | G-R7 order rewritten to the AS-IS convention: Spring typed binding → in-handler flag check → explicit `LiquidityHistoryAuth.authenticate` → permit → cursor → snapshot → write; overlap tests added |

### Revision-4 change log (Codex round 3 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Transient (StringBuilder/encoding) and Kafka-poll bounds unproven; JVM layout assumptions unenforced | G-R7 mandates a streamed, byte-oriented response with a fixed buffer and a permit lifecycle; G-R8 derives the Kafka bound from the gateway's ENFORCED consumer settings and the oversized-first-batch rule, verifies the three HotSpot layout flags at start-up (fail-closed), and recomputes the total and both inequalities |
| 2 | `drops_total` lacks the consumer dimension; route rejection precedence undefined | G-R9 adds `consumer` to `drops_total` and defines `broadcast_total` from live-path counts only; G-R7 fixes the route processing order so exactly one rejection reason applies; G-R11 tests overlapping conditions |

### Revision-3 change log (Codex round 2 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Retained-heap bound unproven (average-based) | G-R8 rewritten with a per-entry overhead bound from the count limits and the payload bound from the byte limits — no average anywhere; transient backfill memory bounded by a concurrency limit |
| 2 | Contingency ignores native memory (`-Xmx` = container limit today) | G-R8/§4: the container limit must exceed `-Xmx` by a justified native margin AND a measured working-set peak must leave the projected footprint headroom; both recorded in the deploy PR |
| 3 | Metrics semantics/labels undefined | G-R9 defines every series' increment rule, overlap rule and exhaustive label domain; G-R11 (12) pins expected values |

### Revision-2 change log (Codex round 1 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Two views, one hello `sessionDate`; cross-topic bootstrap order undefined | ONE session coordinator owns BOTH views under ONE lock (G-R4); the hello is an atomic snapshot under that lock (G-R6); cross-topic ordering tests (G-R11) |
| 2 | G-R8 arithmetic wrong; outcomes unbounded; heap unknown | G-R8 rewritten: byte AND count budgets per view with deterministic eviction, oversize rejection for BOTH record classes, retained-heap arithmetic against the es4 heap (`-Xmx1536m`, container limit 1536Mi), deployment contingent on measured headroom |
| 3 | Rollover accepts any non-empty string | `sessionDate` must be a strict canonical `YYYY-MM-DD` parsed with `LocalDate` (ISO_LOCAL_DATE, no lenient forms) BEFORE any mutation; invalid/missing = shape drop (G-R4) |
| 4 | Outcome cursor not total over epoch-ms; invalid cursors undefined | Fixed-width key `tf\|%019d\|identity` so string order = `(tf, resolvedAtBarStartMs numeric, identity)`; cursor grammar, validation (HTTP 400) and tie/boundary tests (G-R5, G-R7, G-R11) |
| 5 | Acceptance suite incomplete | G-R11 enumerates a brokerless test per clause of G-R1–G-R10, including per-session fan-out |
| 6 | Allowlisting = global fan-out; authorization consequence unrecorded | D5 records the decision: footprint records are ES-global market data identical for every authenticated socket, the same class as `es-cvd`; there is no per-user or per-selection content; fan-out/isolation tests (G-R11) |
| 7 | "Every record carries `timeframe`" is false for live records | §1 corrected: bar and outcome records carry `timeframe`; live records carry the fixed-key `timeframes` object |

## 0. Purpose

Relay the four footprint topics to browser clients exactly the way the ES CVD topics are relayed
today (ES-CVD-DESIGN.md R31/R46, as built in `FeedGatewayService`), so the Gate-3 page can use the
CVD page's plumbing verbatim: standalone WebSocket events, keyed views with a REST backfill, and the
connect-time hello handshake. The gateway is a VERBATIM relay: it never enriches, re-keys or
interprets a footprint record. Flag off (default), nothing footprint is subscribed, retained,
broadcast or exported.

## 1. AS-IS (source-anchored, `origin/main` 47bd409)

| Fact | Where |
|---|---|
| ES CVD topics are wired ONCE for both the state cache consumer and the state live consumer through `addEsCvdTopics(topicEvents)` (two call sites, one method), gated by `GatewaySettings.esCvdEnabled()` (`GATEWAY_ES_CVD_ENABLED`, default false) | `FeedGatewayService.java:1905`, `:2041`, `:2053-2058`; `GatewaySettings.java:264-276` |
| `es-cvd` is broadcast STANDALONE (never selection-gated, never cached); `es-cvd-bar` is upserted into the keyed view FIRST, then broadcast | `FeedGatewayService.java:2833-2848` (`runLiveConsumerOnce`) |
| Keyed view `cvdBars: TreeMap<tf\|barStartMs, json>` with MONOTONIC session rollover on the record's `sessionDate` (string order = date order for `yyyymmdd`); older-session records dropped; foreign shapes dropped from the view but still broadcast | `FeedGatewayService.java:9656-9690` |
| Connect hello `cvd-hello` = `{sessionDate, hwm:{tf: maxBarStartMs}[, levels]}` sent to every new session when CVD or SPX-levels is enabled | `FeedGatewayService.java:908-916`, `:10219-10235` |
| Backfill `GET /api/cvd/bars?tf&toMs&afterMs&limit&sessionDate` → `{sessionDate, [sessionMismatch], bars[], nextCursor}`; page, cursor and session stamp are ONE synchronized snapshot; typed `long`/`int` parameters are bound by Spring BEFORE the handler runs (malformed ⇒ 400 with no footprint code executed) | `GatewayController.java:50-72`; `FeedGatewayService.java:10247-10269` |
| REST authentication is EXPLICIT and in-handler where a route requires it: such routes call `LiquidityHistoryAuth.authenticate(...)` themselves (there is no JWT servlet filter for `/api`) — the precedent is `LiquidityHistoryController`; the CVD bars route makes NO such call and is cited in this document ONLY as the typed-binding and backfill-semantics precedent | `liquidityhistory/LiquidityHistoryController.java:86`; `GatewayController.java:50-72` |
| Per-session (auth) routing drops any event not in the standalone allowlist; `es-cvd` / `es-cvd-bar` are allowlisted | `FeedGatewayService.java:11493-11494` |
| The external ES CVD page stores the hello object and reads only `sessionDate`/`hwm`, so an added field is ignored by it | `options-edge/src/app/web/assets/es-cvd.js:278-304` |
| Tests: view semantics and the one-wiring-path pin | `src/test/java/app/feedgateway/CvdBarViewTest.java` |
| es4 deployment: `JAVA_TOOL_OPTIONS=-Xms256m -Xmx1536m`, container memory limit `1536Mi` | `options-edge-deploy/k8s/es4/services/es-feed-gateway.yaml:120-121`, `:176-178` |
| Manually assigned consumers discover topology growth through `PartitionRefresh.apply(consumer, assigned)`, called on the CONSUMER'S OWN poll thread every `partitionMetadataRefreshMs`: it re-reads partitions for the consumer's (immutable, `Set.copyOf`) topic set, merges the newly discovered partitions and calls `consumer.assign(merged)` ITSELF, then returns a `Refresh` whose `added()` the CALLER seeks and books (never shrinks; a metadata timeout is absorbed and retried); an optional topic absent at bootstrap is discovered by exactly this path when it appears | `FeedGatewayService.java:3596-3680` (`PartitionRefresh`; the internal assign at `:3665-3671`), `apply()` call sites `:2310` (state cache) and `:2677` (state live) |
| Consumer settings the gateway ENFORCES on every consumer: `max.poll.records` = `GATEWAY_KAFKA_MAX_POLL_RECORDS` (default 100), `fetch.max.bytes` = `GATEWAY_KAFKA_FETCH_MAX_BYTES` (default 4 MiB), `max.partition.fetch.bytes` = `GATEWAY_KAFKA_MAX_PARTITION_FETCH_BYTES` (default 512 KiB) | `GatewaySettings.java:1072`, `:1077`, `:1081`; `FeedGatewayService.java:12294-12296` |

Upstream record facts the gateway relies on (ES-FOOTPRINT-DESIGN.md §5): every record carries
`schemaVersion` (6), `symbol` and `sessionDate` (ISO `YYYY-MM-DD`); a closed-bar record carries
`timeframe` and `observations.barStartMs`; an outcome record carries `timeframe`, `identity` and
`resolvedAtBarStartMs`; the two LIVE records carry no singular `timeframe` but the fixed-key
`timeframes` object and `eventTimeMs`. Every string is drawn from the ASCII alphabet
`[A-Za-z0-9_.:|-]` (F-E8), identities are ≤ 128 bytes, and every record is ≤ the producer's
`maxRecordBytes` (default 262 144) — the evidence live record ≤ `maxEvidenceBytes` (same default).
Bar and outcome records are compacted-topic upserts (F-R14): at-least-once delivery is invisible
under last-write-per-key. Epoch-ms values lie in `[0, 253402300799999]` (§5 timestamp domain).

## 2. Requirements

| id | Requirement |
|----|-------------|
| G-R1 | **Flag.** `GATEWAY_ES_FOOTPRINT_ENABLED` (default `false`; `GatewaySettings.esFootprintEnabled()`). Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`. Flag on with a topic absent on the cluster: the gateway starts (the topics are OPTIONAL like every other DATABENTO JSON topic) and the page reports no data. |
| G-R2 | **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`). ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`. |
| G-R3 | **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`). `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt). All four events are added to the per-session standalone allowlist (D5). |
| G-R4 | **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock. Admission of any bar/outcome record: parse JSON; `sessionDate` must be present and parse with `LocalDate.parse(s, ISO_LOCAL_DATE)` AND round-trip to the same string (strict canonical `YYYY-MM-DD`); `timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates). Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances; OLDER ⇒ `stale_session` drop; EQUAL (or first) ⇒ upsert (last write per key wins). Because one coordinator rolls both views, a bar from session N+1 arriving before any outcome of N+1 clears N's outcomes with N's bars, and an outcome of N arriving after that is dropped — whichever topic the cache consumer drains first. |
| G-R5 | **Outcome view.** `outcomes: TreeMap<tf\|%019d(resolvedAtBarStartMs)\|identity, json>` in the SAME coordinator. The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain; `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component). Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast. |
| G-R6 | **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held). It rides the SAME frame so the page has one handshake; `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled. Flag off, the field is absent (its ABSENCE tells the page the gateway has no footprint). |
| G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's). `GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` ascending by view key within `tf`, `toMs` inclusive on `resolvedAtBarStartMs`, `after` = an EXCLUSIVE lower bound given as a previous page's `nextCursor` (the last key string). Cursor grammar: `tf|<19 digits>|<identity>` where `tf` equals the query `tf`; an `after` that is non-empty and fails the grammar ⇒ HTTP 400 `{"error":"bad cursor"}`; a well-formed cursor that no longer exists is legal (string lower bound). `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from. Same JWT gate as every `/api` route. **Processing order (exactly one outcome per request, in the AS-IS convention):** (1) Spring typed binding of `toMs`/`limit`/`afterMs` ⇒ 400 on malformed input BEFORE the handler runs (so a malformed request is never counted and never sees the flag or auth); (2) in the handler, flag off ⇒ 404 `{"enabled":false}`; (3) explicit `LiquidityHistoryAuth.authenticate(...)` exactly as `LiquidityHistoryController` does (`:86`) ⇒ its 401/403 on failure; (4) permit: `tryAcquire` on a `Semaphore(GATEWAY_ES_FOOTPRINT_BACKFILL_CONCURRENCY)` (default 4) ⇒ 503 `Retry-After: 1` (`busy`) when none is free; (5) cursor grammar (outcomes route) ⇒ 400 `{"error":"bad cursor"}`; (6) page snapshot under the coordinator lock — the session check (`sessionMismatch`, HTTP 200) and the selection of up to `limit` record REFERENCES into a `List<String>` (no copies) plus `sessionDate` and `nextCursor`; (7) the response is STREAMED: the controller writes to `HttpServletResponse.getOutputStream()` through ONE fixed 64 KiB buffer, the envelope pieces and each record as `record.getBytes(US_ASCII)` one at a time (records are ASCII by F-E8), status 200 set before the first byte; the permit is released in `finally` after the stream is flushed (or the write failed). The lock is held only during (6), never during (7). Transient memory per request is therefore ≤ one record's bytes (≤ 262 144 B) + the 64 KiB buffer + the reference list (≤ 100 × 4 B) < 0.32 MiB (G-R8). |
| G-R8 | **Bounds and heap (proven from the limits, never from averages).** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key. **Retained-heap bound** (HotSpot, compressed oops and compressed class pointers — true for any heap < 32 GiB, as here): a JSON value is ASCII (F-E8 alphabet) so it is a Latin-1 compact `String`: 24 B object + 16 B array header + payload, aligned to 8 ⇒ ≤ payload + 48 B; a key `String` ≤ 160 B payload ⇒ ≤ 208 B; a `TreeMap.Entry` = 40 B; per entry overhead ≤ 296 B, so overhead ≤ (12 000 + 20 000) × 296 B = 9.04 MiB; payload ≤ 128 + 16 = 144 MiB; the coordinator's accounting (two `long` counters, one `LocalDate`, per-timeframe eviction cursors) < 1 KiB. Views total ≤ 153.1 MiB. **Transient** per in-flight backfill (G-R7 step 7): < 0.32 MiB, at most 4 concurrent ⇒ < 1.3 MiB; no `StringBuilder`, no response `String`, no encoder buffer beyond the fixed 64 KiB. **Kafka transient**, from the client's fetch mechanics and THREE enforced facts. Mechanics (Kafka consumer `Fetcher`, unchanged since KIP-74): the consumer keeps at most ONE in-flight fetch request per broker node, a fetch request for a node includes only assigned partitions that have NO buffered (completed, unconsumed) data, each partition's share of a response is ≤ `max.partition.fetch.bytes` except that the FIRST batch of a partition is returned whole when it is larger, and `max.poll.records` only paces how many buffered records a `poll()` hands out. Enforced facts: (i) `max.partition.fetch.bytes` = 512 KiB (§1); (ii) `M` = each footprint topic's effective `max.message.bytes` ≤ `GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588, the broker default), validated per G-R8a; (iii) the four topics are UNCOMPRESSED end to end — the upstream producer sets `compression.type=none` explicitly (options-edge-processing amendment: `EsCvdRuntime.producerProps`, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` per G-R8a — so a fetched share IS its decompressed size and no decompression buffer exists. Per consumer, each of the four single-partition footprint topics contributes at most: one in-flight share (≤ max(512 KiB, M) ≤ 1.0 MiB) + one buffered completed share (≤ 1.0 MiB) + the deserialized value `String`s of the records a `poll()` returned from that share (≤ the share's bytes + 48 B per record) ⇒ ≤ 3.0 MiB per topic + per-record overhead, ≤ 12.0 MiB per consumer + per-record overhead. Per-record client overhead for the records of ONE poll — here, and only here, bounded by `max.poll.records` = 100 (§1): key `String` ≤ 208 B, `ConsumerRecord` object ≤ 96 B (header, 7 references, two longs, two ints), empty `RecordHeaders` ≤ 32 B, `ConsumerRecords` map/list share ≤ 64 B ⇒ ≤ 400 B × 100 = 40 KiB. Per consumer ≤ 12.04 MiB; TWO consumers touch footprint topics (state cache, state live) ⇒ ≤ 24.1 MiB, rounded to 25 MiB. Non-footprint partitions in the same fetch responses are not attributable to this feature and are already part of the measured `H_peak`. **Layout assumptions are verified at start-up** when the flag is on: `HotSpotDiagnosticMXBean.getVMOption` for `UseCompressedOops`, `UseCompressedClassPointers` and `CompactStrings` must all be `true`, else the gateway refuses to start naming the flag (the arithmetic above is void otherwise); pinned by a test of the checker over a fake option map. Topic-side facts (ii) and (iii) are verified by G-R8a. **Footprint heap contribution ≤ 153.1 + 1.3 + 25 = 179.4 MiB, rounded to 180 MiB**, independent of record size distribution. **Deployment contingency (§4):** the flag may be enabled only when the deploy PR records, from a full ES session with CVD on and footprint off: (a) heap: `jvm_memory_bytes_used{area="heap"}` peak `H_peak` with `-Xmx − H_peak ≥ 180 MiB + 128 MiB = 308 MiB` (GC headroom); (b) container: `container_memory_working_set_bytes` peak `W_peak` with `limit − W_peak ≥ 180 MiB + 256 MiB = 436 MiB`, AND `limit − Xmx ≥ 512 MiB` (metaspace, code cache, thread stacks, socket/direct buffers, JVM overhead — the current es4 manifest has `limit = Xmx = 1536 MiB`, which violates this and MUST change in the same PR: e.g. `-Xmx1536m` with `limit 2560Mi`, or a measured alternative that satisfies both inequalities). Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits and the tests pin them. |
| G-R8a | **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`. **Start-up** (`start()`, before any consumer launches): validate the topics that EXIST; an existing INVALID topic ⇒ refuse to start naming topic and value (fail-closed); an absent topic (`UnknownTopicOrPartitionException` for that resource) ⇒ deferred; any other Admin failure ⇒ refuse to start. **The seam (an API change to `PartitionRefresh`, the AS-IS contract otherwise kept):** `PartitionRefresh` gains a constructor parameter `Predicate<String> topicAdmit`: of the SIX existing constructor call sites (`:1739` selection, `:2087` hpsf-cache, `:2125` hpsf-live, `:2201` alerts, `:2303` state cache, `:2674` state live), the two STATE consumers pass `footprintGate::admit` and the other FOUR pass `t -> true`. Inside `apply()`, AFTER `partitionsFor` discovers new partitions and BEFORE `mergedAssignment`/`consumer.assign(...)` (`FeedGatewayService.java:3665-3671`), discovered partitions whose topic fails `topicAdmit` are WITHHELD: they are not merged, not assigned, not in `Refresh.added()`, hence never sought and never in the caller's caught-up bookkeeping — identical to a topic that does not exist yet; they are re-discovered (and re-evaluated) at the next refresh interval. The same predicate is applied to the bootstrap `partitionsFor` result before the initial `assign` in both state consumers. `addEsFootprintTopics` adds ALL FOUR topics to both consumers' `topicEvents` unconditionally, so the immutable `Set.copyOf(topics)` always contains them and discovery works whenever a topic appears. **`FootprintTopicGate.admit(topic)`** runs on the calling poll thread: a non-footprint topic ⇒ true; a footprint topic already marked VALID ⇒ true; otherwise it validates NOW (bounded timeout): VALID ⇒ mark and true; unknown topic, timeout or other Admin failure ⇒ false this cycle (counted, logged once per state change, retried at the next refresh — the absorbed-refresh-failure discipline); existing-but-invalid ⇒ false (counted `ceiling`/`compression`, logged once per state change). Per-topic state in a `ConcurrentHashMap` shared by both consumers (each evaluates on its own thread; a duplicate validation is harmless); once VALID a topic stays VALID for the incarnation (a topic config change after admission requires a gateway restart — §4 operational rule). Adoption of a late-admitted topic then follows the existing caller behaviour for `Refresh.added()`: the cache consumer seeks its window (a compacted keyed topic replays its retained keys into the coordinator, as `es-cvd-bar` does), the live consumer seeks END. The two validation series are defined in G-R9. |
| G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
| G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |

<!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->

## 2a. Conformance — what a test actually holds

Every requirement above, and what this repository's mutation campaign established about it. The
campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
the spec declared in advance — never an incidental error under the right test's name; and the table
below refuses to render a "pinned" cell for any record that does not carry its evidence. That
evidence — the patch, the file, line and enclosing declaration, the command, the exit code, the
verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
record with `scripts/footprint-reqstate.sh --check`.

| id | Conformance | Gate | Disposition |
|----|-------------|------|-------------|
| G-R1 | 1 of 1 clauses probed here are pinned | 2 | Flag and wiring |
| G-R2 | 2 of 2 clauses probed here are pinned | 2 | Flag and wiring |
| G-R3 | 3 of 3 clauses probed here are pinned | 2 | Delivery and views |
| G-R4 | 4 of 4 clauses probed here are pinned | 2 | Delivery and views |
| G-R5 | 4 of 4 clauses probed here are pinned | 2 | Delivery and views |
| G-R6 | 3 of 3 clauses probed here are pinned | 2 | Hello |
| G-R7 | 5 of 6 clauses probed here are pinned (1 survived) | 2 | Backfill routes |
| G-R8 | 5 of 5 clauses probed here are pinned | 2 | Deployment contingency |
| G-R9 | 1 of 1 clauses probed here are pinned | 2 | Metrics |
| G-R10 | 3 of 3 clauses probed here are pinned | 2 | Non-interference |
| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
| G-R8a | 2 of 2 clauses probed here are pinned | 2 | Deployment contingency |

12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).

Read the state column narrowly. "n of m clauses probed here are pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.

<!-- END footprint-reqstate -->

## 3. Decisions

- **D1 No live retention.** `es-footprint`/`es-footprint-evidence` are not retained for connect
  replay: the producer's 5 s heartbeat bounds the wait and the CVD page already lives with the same
  bound. Retention would add the SPX-levels attestation machinery for no page benefit.
- **D2 One hello.** A second `footprint-hello` frame would give the page two handshakes to order;
  one field inside the existing frame keeps the R46 protocol single.
- **D3 Verbatim.** The gateway parses a record only to admit it and extract keys; the bytes it
  broadcasts and backfills are the producer's bytes.
- **D4 One coordinator, fixed-width keys.** Both views roll on one date under one lock; keys encode
  the numeric component zero-padded so `TreeMap` string order is the tuple order without a custom
  comparator (which a test could not distinguish from a wrong one as easily).
- **D5 Session-global data (authorization).** A footprint record is ES-futures market microstructure
  computed from the public tape; it carries no user, account, selection or tenant content and is
  identical for every authenticated socket, exactly like `es-cvd`/`es-cvd-bar`. Allowlisting it for
  global fan-out in per-session (auth) mode is therefore an authorization DECISION recorded here: the
  only gate is authentication (the JWT on the socket and on `/api`), never selection. No routable
  identity exists or is needed.

## 4. Deploy (es4) and the upstream prerequisite

**Upstream prerequisite (options-edge-processing) — MERGED:** PR #757 (`feat/es-footprint-uncompressed`,
Codex APPROVE) sets `compression.type=none` explicitly in `EsCvdRuntime.producerProps`, pins it by
test and records ES-FOOTPRINT-DESIGN.md amendment 12; fact (iii) of G-R8 therefore holds by
contract on the producer side, and G-R8a's `compression.type` check on each topic remains the
continuing runtime guard against a topic-level codec being configured later.
Operational rule: a footprint topic's config change after admission requires a gateway
restart (G-R8a caches VALID per incarnation).


`k8s/es4/services/es-feed-gateway.yaml`: `GATEWAY_ES_FOOTPRINT_ENABLED=true`, the four
`KAFKA_ES_FOOTPRINT_*` names (prefixed to `es.futures.footprint*` by the gateway),
`GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` equal to the service's `FOOTPRINT_MAX_RECORD_BYTES`, after the
service PR is deployed. The SAME deploy PR must (1) record `H_peak` and `W_peak` from a full session
(Prometheus queries quoted in the PR), (2) prove both G-R8 inequalities, and (3) set the container
limit to at least `Xmx + 512 MiB` — the current `1536Mi = -Xmx1536m` fails that inequality on its
own, so the PR changes the limit (or `-Xmx`) whatever the measurements say.

## 5. Codex review request (round 10)

Check the round-9 disposition (the two validation series in G-R9's contract and G-R11's pins);
verdict `APPROVE` or `REQUEST_CHANGES` with numbered findings.
