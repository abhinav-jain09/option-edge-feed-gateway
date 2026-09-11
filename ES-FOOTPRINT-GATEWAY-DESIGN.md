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
| G-R3 | **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`). `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt). All four events are added to the per-session standalone allowlist (D5). **As-built, the fifth stream (strike re-review round 2):** `es-footprint-strike` forwards ONLY admitted evidence, and only an admission that CHANGED what the fold retains — a new episode or a newer revision — is forwarded: an admission that changed nothing (a redelivery, an older revision) queues nothing. The change is queued by whichever consumer made it: the live consumer always; the cache consumer only once its replay is complete, because while the replay is incomplete every reader is LOADING and the completion's authority change makes each one re-walk. |
| G-R4 | **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock. Admission of any bar/outcome record: parse JSON; `sessionDate` must be present and parse with `LocalDate.parse(s, ISO_LOCAL_DATE)` AND round-trip to the same string (strict canonical `YYYY-MM-DD`); `timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates). Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances; OLDER ⇒ `stale_session` drop; EQUAL (or first) ⇒ upsert (last write per key wins). Because one coordinator rolls both views, a bar from session N+1 arriving before any outcome of N+1 clears N's outcomes with N's bars, and an outcome of N arriving after that is dropped — whichever topic the cache consumer drains first. |
| G-R5 | **Outcome view.** `outcomes: TreeMap<tf\|%019d(resolvedAtBarStartMs)\|identity, json>` in the SAME coordinator. The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain; `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component). Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast. |
| G-R6 | **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held). It rides the SAME frame so the page has one handshake; `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled. Flag off, the field is absent (its ABSENCE tells the page the gateway has no footprint). |
| G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's). `GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` ascending by view key within `tf`, `toMs` inclusive on `resolvedAtBarStartMs`, `after` = an EXCLUSIVE lower bound given as a previous page's `nextCursor` (the last key string). Cursor grammar: `tf|<19 digits>|<identity>` where `tf` equals the query `tf`; an `after` that is non-empty and fails the grammar ⇒ HTTP 400 `{"error":"bad cursor"}`; a well-formed cursor that no longer exists is legal (string lower bound). `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from. Same JWT gate as every `/api` route. **Processing order (exactly one outcome per request, in the AS-IS convention):** (1) Spring typed binding of `toMs`/`limit`/`afterMs` ⇒ 400 on malformed input BEFORE the handler runs (so a malformed request is never counted and never sees the flag or auth); (2) in the handler, flag off ⇒ 404 `{"enabled":false}`; (3) explicit `LiquidityHistoryAuth.authenticate(...)` exactly as `LiquidityHistoryController` does (`:86`) ⇒ its 401/403 on failure; (4) permit: `tryAcquire` on a `Semaphore(GATEWAY_ES_FOOTPRINT_BACKFILL_CONCURRENCY)` (default 4) ⇒ 503 `Retry-After: 1` (`busy`) when none is free; (5) cursor grammar (outcomes route) ⇒ 400 `{"error":"bad cursor"}`; (6) page snapshot under the coordinator lock — the session check (`sessionMismatch`, HTTP 200) and the selection of up to `limit` record REFERENCES into a `List<String>` (no copies) plus `sessionDate` and `nextCursor`; (7) the response is STREAMED: the controller writes to `HttpServletResponse.getOutputStream()` through ONE fixed 64 KiB buffer, the envelope pieces and each record as `record.getBytes(US_ASCII)` one at a time (records are ASCII by F-E8), status 200 set before the first byte; the permit is released in `finally` after the stream is flushed (or the write failed). The lock is held only during (6), never during (7). Transient memory per request is therefore ≤ one record's bytes (≤ 262 144 B) + the 64 KiB buffer + the reference list (≤ 100 × 4 B) < 0.32 MiB (G-R8). **As-built, the strike `latest` page (strike re-review round 2):** `tombstones` names every visited strike whose newest episode in the requested session is refused or EVICTED — an evicted newest episode leaves an eviction marker in the index, so no older episode can take its place; a genuinely newer opening of that strike in that session retires its marker. |
| G-R8 | **Bounds and heap (proven from the limits, never from averages).** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key. **Retained-heap bound** (HotSpot, compressed oops and compressed class pointers — true for any heap < 32 GiB, as here): a JSON value is ASCII (F-E8 alphabet) so it is a Latin-1 compact `String`: 24 B object + 16 B array header + payload, aligned to 8 ⇒ ≤ payload + 48 B; a key `String` ≤ 160 B payload ⇒ ≤ 208 B; a `TreeMap.Entry` = 40 B; per entry overhead ≤ 296 B, so overhead ≤ (12 000 + 20 000) × 296 B = 9.04 MiB; payload ≤ 128 + 16 = 144 MiB; the coordinator's accounting (two `long` counters, one `LocalDate`, per-timeframe eviction cursors) < 1 KiB. Views total ≤ 153.1 MiB. **Transient** per in-flight backfill (G-R7 step 7): < 0.32 MiB, at most 4 concurrent ⇒ < 1.3 MiB; no `StringBuilder`, no response `String`, no encoder buffer beyond the fixed 64 KiB. **Kafka transient**, from the client's fetch mechanics and THREE enforced facts. Mechanics (Kafka consumer `Fetcher`, unchanged since KIP-74): the consumer keeps at most ONE in-flight fetch request per broker node, a fetch request for a node includes only assigned partitions that have NO buffered (completed, unconsumed) data, each partition's share of a response is ≤ `max.partition.fetch.bytes` except that the FIRST batch of a partition is returned whole when it is larger, and `max.poll.records` only paces how many buffered records a `poll()` hands out. Enforced facts: (i) `max.partition.fetch.bytes` = 512 KiB (§1); (ii) `M` = each footprint topic's effective `max.message.bytes` ≤ `GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588, the broker default), validated per G-R8a; (iii) the four topics are UNCOMPRESSED end to end — the upstream producer sets `compression.type=none` explicitly (options-edge-processing amendment: `EsCvdRuntime.producerProps`, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` per G-R8a — so a fetched share IS its decompressed size and no decompression buffer exists. Per consumer, each of the four single-partition footprint topics contributes at most: one in-flight share (≤ max(512 KiB, M) ≤ 1.0 MiB) + one buffered completed share (≤ 1.0 MiB) + the deserialized value `String`s of the records a `poll()` returned from that share (≤ the share's bytes + 48 B per record) ⇒ ≤ 3.0 MiB per topic + per-record overhead, ≤ 12.0 MiB per consumer + per-record overhead. Per-record client overhead for the records of ONE poll — here, and only here, bounded by `max.poll.records` = 100 (§1): key `String` ≤ 208 B, `ConsumerRecord` object ≤ 96 B (header, 7 references, two longs, two ints), empty `RecordHeaders` ≤ 32 B, `ConsumerRecords` map/list share ≤ 64 B ⇒ ≤ 400 B × 100 = 40 KiB. Per consumer ≤ 12.04 MiB; TWO consumers touch footprint topics (state cache, state live) ⇒ ≤ 24.1 MiB, rounded to 25 MiB. Non-footprint partitions in the same fetch responses are not attributable to this feature and are already part of the measured `H_peak`. **Layout assumptions are verified at start-up** when the flag is on: `HotSpotDiagnosticMXBean.getVMOption` for `UseCompressedOops`, `UseCompressedClassPointers` and `CompactStrings` must all be `true`, else the gateway refuses to start naming the flag (the arithmetic above is void otherwise); pinned by a test of the checker over a fake option map. Topic-side facts (ii) and (iii) are verified by G-R8a. **Footprint heap contribution ≤ 153.1 + 1.3 + 25 = 179.4 MiB, rounded to 180 MiB**, independent of record size distribution. **Deployment contingency (§4):** the flag may be enabled only when the deploy PR records, from a full ES session with CVD on and footprint off: (a) heap: `jvm_memory_bytes_used{area="heap"}` peak `H_peak` with `-Xmx − H_peak ≥ 180 MiB + 128 MiB = 308 MiB` (GC headroom); (b) container: `container_memory_working_set_bytes` peak `W_peak` with `limit − W_peak ≥ 180 MiB + 256 MiB = 436 MiB`, AND `limit − Xmx ≥ 512 MiB` (metaspace, code cache, thread stacks, socket/direct buffers, JVM overhead — the current es4 manifest has `limit = Xmx = 1536 MiB`, which violates this and MUST change in the same PR: e.g. `-Xmx1536m` with `limit 2560Mi`, or a measured alternative that satisfies both inequalities). Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits and the tests pin them. **As-built, strike eviction markers (strike re-review round 2):** at most `GATEWAY_ES_FOOTPRINT_STRIKE_MAX_REFUSED_IDENTITIES` eviction markers are kept, each charged to the strike byte budget; when only the byte budget is exceeded, markers are dropped oldest first before any live head is evicted; dropping a marker is an authority change, so a reader discards what it held and re-walks — it reads NO DATA for that strike, never an older episode, because every marker opened below the boundary. |
| G-R8a | **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`. **Start-up** (`start()`, before any consumer launches): validate the topics that EXIST; an existing INVALID topic ⇒ refuse to start naming topic and value (fail-closed); an absent topic (`UnknownTopicOrPartitionException` for that resource) ⇒ deferred; any other Admin failure ⇒ refuse to start. **The seam (an API change to `PartitionRefresh`, the AS-IS contract otherwise kept):** `PartitionRefresh` gains a constructor parameter `Predicate<String> topicAdmit`: of the SIX existing constructor call sites (`:1739` selection, `:2087` hpsf-cache, `:2125` hpsf-live, `:2201` alerts, `:2303` state cache, `:2674` state live), the two STATE consumers pass `footprintGate::admit` and the other FOUR pass `t -> true`. Inside `apply()`, AFTER `partitionsFor` discovers new partitions and BEFORE `mergedAssignment`/`consumer.assign(...)` (`FeedGatewayService.java:3665-3671`), discovered partitions whose topic fails `topicAdmit` are WITHHELD: they are not merged, not assigned, not in `Refresh.added()`, hence never sought and never in the caller's caught-up bookkeeping — identical to a topic that does not exist yet; they are re-discovered (and re-evaluated) at the next refresh interval. The same predicate is applied to the bootstrap `partitionsFor` result before the initial `assign` in both state consumers. `addEsFootprintTopics` adds ALL FOUR topics to both consumers' `topicEvents` unconditionally, so the immutable `Set.copyOf(topics)` always contains them and discovery works whenever a topic appears. **`FootprintTopicGate.admit(topic)`** runs on the calling poll thread: a non-footprint topic ⇒ true; a footprint topic already marked VALID ⇒ true; otherwise it validates NOW (bounded timeout): VALID ⇒ mark and true; unknown topic, timeout or other Admin failure ⇒ false this cycle (counted, logged once per state change, retried at the next refresh — the absorbed-refresh-failure discipline); existing-but-invalid ⇒ false (counted `ceiling`/`compression`, logged once per state change). Per-topic state in a `ConcurrentHashMap` shared by both consumers (each evaluates on its own thread; a duplicate validation is harmless); once VALID a topic stays VALID for the incarnation (a topic config change after admission requires a gateway restart — §4 operational rule). Adoption of a late-admitted topic then follows the existing caller behaviour for `Refresh.added()`: the cache consumer seeks its window (a compacted keyed topic replays its retained keys into the coordinator, as `es-cvd-bar` does), the live consumer seeks END. The two validation series are defined in G-R9. |
| G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. **As-built (strike re-review round 2):** a socket's teardown never runs on the thread that enqueued for it: a channel that overflows is marked closed at once, and its session is closed on a teardown thread of its own, so a close that blocks behind an outstanding write delays neither consumer nor any other socket. |
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

What this table cannot do is decide whether a quoted obligation is the clause a probe exercises.
The generator refuses a quote that is not verbatim in the requirement it sits under, and one that
shares no vocabulary with the clause recorded against it; beyond that, attribution is a judgement
made in review, and the merge gate for this change corrected several of them.

**What backs this table.** The record is a file. Every refusal above checks it against itself or
against the source it names — the clause a mutation claims to have broken must be in this
repository at HEAD, exactly as many times as the record says, and every file it mutated must hash to
what the record says it was — and no amount of that
makes a file tamper-evident: a baseline block invented wholesale is internally consistent and always
will be. What cannot be invented is the run. Re-run every mutation against your own checkout with
`scripts/footprint-reverify.sh`, which compares what each row claims — its requirement, quoted
obligation, file, occurrence and patch, all taken from the committed spec — and then whether the
outcome reproduces, failing on any difference. That, and not the record's internal shape, is what
this table rests on.

The Jenkinsfile runs it on every build, in its own stage, along with
`scripts/footprint-reqstate.sh --check`: reverification proves the record, and only `--check` proves
that this section is the one that record produces. It cannot run in a GitHub check — this build
needs options-edge-contracts installed from source, which no hosted runner has. The Coverage column
says only what the tooling can check: whether a row is in the set of specs
`scripts/footprint-gated-specs` declares, and reverify fails unless the specs it just ran are exactly
those — so the column cannot drift from what the build does. WHEN the gate runs is this paragraph's
job, not the table's: it is every build of this repository.

| id | Conformance | Gate | Coverage | Disposition |
|----|-------------|------|----------|-------------|
| G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | re-run by the gate | Flag and wiring |
| G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | re-run by the gate | Flag and wiring |
| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | re-run by the gate | Delivery and views |
| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | re-run by the gate | Delivery and views |
| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | re-run by the gate | Delivery and views |
| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | re-run by the gate | Hello |
| G-R7 | 4 of 5 probes pinned, over 4 clauses (1 survived) | 2 | re-run by the gate | Backfill routes |
| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | re-run by the gate | Deployment contingency |
| G-R9 | 3 of 3 probes pinned, over 3 clauses | 2 | re-run by the gate | Metrics |
| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | re-run by the gate | Non-interference |
| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | — | Tests |
| G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | re-run by the gate | Deployment contingency |

12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).

Read the state column narrowly. "n of m probes pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION (or propagate a throw the spec declared and the run matched) — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.

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


## As-built amendment — the fifth stream (ES-FOOTPRINT-STRIKE-INTERACTION.md R3/R14, 2026-09-10; code Codex rounds 1–3 folded; final review folded 2026-09-11; final re-review (round 2) folded 2026-09-11)

The strike-interaction log `es.futures.footprint.strike` rides the SAME relay path under the SAME flag
(`GATEWAY_ES_FOOTPRINT_ENABLED`), topic env `KAFKA_ES_FOOTPRINT_STRIKE_TOPIC`, event `es-footprint-strike`:

- **Wiring (G-R2)**: `addEsFootprintTopics` binds FIVE topics; the G-R8a gate validates all five; the live
  consumer seeks the fifth to END like the other four, on bootstrap, retry and adoption alike; the cache
  consumer seeks back `GATEWAY_ES_FOOTPRINT_STRIKE_SEEK_BACK_MS` (default 7 days — history crosses sessions, R18).
- **Fold (R14)**: `FootprintStrikeView` folds by identity to the greatest revision. Two records sharing an
  identity and a revision must be identical bytes — checked for EVERY observed revision of an identity, with a
  32-byte digest per revision kept with the identity — else the identity is REFUSED for the incarnation (drop
  reason `collision`, later revisions `refused`, counted). A refused identity stays in the ordering as a
  TOMBSTONE: if it is a strike's newest episode, `latest` has no row for that strike (NO DATA), never an older
  episode, and names the strike in the page's `tombstones`; history omits it and keeps the older ones. An
  EVICTED newest episode is named the same way, through an eviction marker (below). Scope is
  symbol AND timeframe. CHECKPOINT records carry no episode and only advance the per-timeframe high-water mark (a
  JSON-null session date is accepted; an invalid one is a shape drop, as is any timeframe outside the producer's
  vocabulary — nothing unescaped can reach the hello).
- **Bounds and the boundary (R20/G-R8)**: `GATEWAY_ES_FOOTPRINT_STRIKE_MAX_BYTES` (64 MiB) and `_MAX_EPISODES`
  (50 000) evict the OLDEST identities, and always the WHOLE equal-opening-time bucket, so the MONOTONIC boundary
  that follows is strictly above evicted openings and at or below retained openings: records opening before it
  are dropped (`evicted`) rather than re-admitted, and no survivor can sit behind it with its own updates refused
  (code round-2 #2). `historyBeginsAtMs` reports that boundary (or, before any eviction, the oldest retained
  open) and is retained INVENTORY; `replayBeginsAtMs` reports the cutoff the cache consumer's replay actually
  sought, and the two are published separately because neither implies the other (code round-2 #6). A symbol
  longer than 64 characters is a shape drop. The refusal ledger is bounded by
  `GATEWAY_ES_FOOTPRINT_STRIKE_MAX_REFUSED_IDENTITIES` (10 000), and eviction that cannot bring the view inside
  its budget is the same fail-closed case: the view is UNAVAILABLE for the incarnation — routes answer 503 +
  Retry-After, the hello says `"unavailable":true`, the gauge `gateway_footprint_strike_unavailable` is 1 —
  rather than forgetting evidence.
- **Eviction markers (final re-review #3).** When eviction removes the NEWEST episode of a strike in its session
  (per symbol, timeframe, strike and session), its index entry stays behind, dead, as an EVICTION MARKER: `latest`
  for that session then names the strike in `tombstones` exactly as it names a refused newest episode, so the
  reader can never let an older episode take the slot. The rules, exactly:
  - A marker exists only while it is its session's newest entry for the strike. A genuinely newer opening of the
    strike in that session (it must open at or after the boundary, so it is newer than every marker) retires the
    marker at once; `latest` then answers with the new episode.
  - Everything below a marker in its session is dead already (eviction runs oldest first; an equal opening is the
    same identity) and can never be reached by `latest` again, so it leaves the index when the marker is made — a
    refused tombstone included (the refusal ledger still remembers the identity). A refused newest episode that the
    boundary has passed is KEPT and still named: it is its session's newest entry. Nothing walks the whole index
    (the round-2 #7 per-eviction scan, and the round-3 #7 guard for it, are gone with the prune they served).
  - Bound: at most `GATEWAY_ES_FOOTPRINT_STRIKE_MAX_REFUSED_IDENTITIES` (10 000) markers — the refusal ledger's
    bound, since both record a strike's newest episode that is no longer served — and each is charged to the byte
    budget: 256 B of nodes (its index node, the marker object, its map node and table share, its age-order node)
    plus every string it references (identity, open key, age key, symbol, timeframe). A marker is always charged
    less than the head it replaces, so evicting to make room never grows the charge.
  - Markers are worth less than live heads: when only the BYTE budget is exceeded, markers are dropped, oldest
    first, before any head is evicted; past their own count bound the oldest are dropped too. Only when no head
    and no marker is left does the view fail closed, as before.
  - **When a marker is itself dropped**, the authority advances (once per mutation, together with any eviction the
    same admission caused) and a control frame is queued, so every reader discards what it held and re-walks
    `latest`. That strike then has no row and no tombstone in that session: the reader shows NO DATA — the same
    chip the marker produced — and never an older episode, because every marker opened below the boundary: no
    retained head of that strike and session opens before it, and none can be admitted (it would open before the
    boundary and is dropped `evicted`). History never listed anything below the boundary either. The page's
    `historyBeginsAtMs` (the boundary) says that nothing opening before it is held.
  - `gateway_footprint_strike_eviction_markers` (gauge) and `gateway_footprint_strike_eviction_marker_drops_total`
    (counter) publish the ledger.
- **What the byte budget charges (final review #3).** The budget acts on RETAINED STORAGE, estimated from the
  objects the view keeps — not on the bytes it sends. A head's payload is retained as the record's UTF-8
  `byte[]` and charged that array's heap size (16-byte header, 8-byte alignment). It is never retained as a
  `String`: a String's backing array switches to two bytes per char as soon as ONE char is above U+00FF, and the
  final review's executed counterexample was exactly that — an admitted ~200 KB record held in a 400,906-byte
  array against a 201,157-byte charge under a 300,000-byte budget. Every string the view keeps (identity,
  symbol, session date, timeframe, open key, age key) is charged its storage under compact strings — the object
  plus one byte per char when every char is Latin-1, two otherwise. On top of that: 512 B of node overhead per
  head (the head object, its map node and table share, three index nodes, the age-tree node, the revision
  map), 128 B per observed revision (a boxed key, a node, the 32-byte digest array, a table share), and per
  tombstone 256 B plus its identity and open-key strings. The per-node constants are deliberate over-estimates
  of a compressed-oops HotSpot layout (G-R8's preflight refuses a JVM without compressed oops, compressed class
  pointers or compact strings); they are ESTIMATES, not measurements. An eviction marker is charged as above
  (256 B plus the five strings it references). Three gauges keep what is sent apart from
  what is retained: `gateway_footprint_strike_view_bytes` (the heads' UTF-8 record bytes — what a reader
  receives before quoting, NOT the budget), `gateway_footprint_strike_view_retained_bytes` (the payload arrays
  as held) and `gateway_footprint_strike_view_metadata_bytes` (everything else charged); the budget acts on the
  sum of the last two.
- **Resources — NOT a proof, and not claimed as one.** The accounting above bounds what the view's own charged
  structures may hold; it is not a measured heap ceiling, and no heap-headroom claim is made for this stream.
  Transient allocations — Jackson's parse tree per admission, the quoted copy of a record written to a socket,
  the per-socket outbound queues, the short-lived frame queue below — are outside it. What is NOT established:
  the `Xmx − H_peak` and `limit − W_peak` inequalities for the strike stream, which need the full-session
  measurement G-R8's deployment contingency already requires
  (`max_over_time(jvm_memory_bytes_used{area="heap"}[8h])` with the flag on and the strike view near its
  budget). Until that is recorded, 64 MiB is an accounting budget, not a demonstrated fit. The two-consumer
  Kafka allowance in G-R8 rises from ~25 MiB to ~31 MiB with a fifth topic.
- **Loading is not empty — the replay lifecycle (R14, code round-2 #3, round-3 #2/#5, final review #2).** The
  fold is LOADING until the cache consumer's STRIKE replay completes; until then the hello, every page and every
  control carry `"loading":true` and `gateway_footprint_strike_loading` is 1, so a live CHECKPOINT that raises
  the high-water mark ahead of an unfinished replay can never let a reader render a completed NO DATA. The
  replay belongs to one cache-consumer ATTEMPT and covers every strike partition that attempt seeks by cache
  window — at bootstrap AND at every late adoption (a strike topic absent at start-up and created later). Each
  such seek (re)opens the replay: the cutoff that seek actually used is captured once and published as
  `replayBeginsAtMs` (with several strike partitions, the latest cutoff — the window every one of them covers),
  and the end offsets captured right after the seek become that partition's barrier. Completion is evaluated
  after each poll's records are APPLIED, on every poll, independently of the shared readiness flag, and is
  declared when every partition of the open replay has reached its barrier. A retry is a new attempt: its
  bootstrap reopens a completed replay — an authority change, so connected readers receive a control with
  `"loading":true` — and completes it again. A strike partition the consumer no longer assigns keeps the replay
  open (fail closed); an empty strike partition completes at bootstrap. The live consumer never opens or
  completes the replay.
- **Ordered delivery (final review #1).** Every strike mutation — an admission from EITHER consumer, a refusal,
  an eviction that moves the boundary, a dropped eviction marker, failing closed, the replay completing or
  reopening — decides its outcome AND queues the frames it causes under the one view lock: the admitted record's
  `es-footprint-strike` evidence (see the next bullet for when), then an `es-footprint-strike-control` frame when
  the authority moved. A connecting
  socket's hello is captured and queued the same way. The queue is drained outside the view lock, by one
  drainer at a time, into the non-blocking per-socket channels, in queue order. So a record admitted before a
  refusal or eviction reaches every socket before that change's control frame, never after it; a socket never
  receives a hello whose authority is older than a control it already holds; and a REST page read after a
  control reflects at least that control's state. Only ADMITTED strike evidence is forwarded: a refused,
  evicted, unavailable or shape-dropped record reaches no socket (every other footprint stream keeps G-R3's
  rule, because those pages do not fold against the relay's own decision).
- **Every change to what the fold retains reaches the readers (final re-review #2).** Two kinds of change, two
  signals:
  - ADDITIVE — a new episode, or a newer revision replacing a head (which includes a change of WHICH episode is
    latest for a strike through a newer opening). The admitted record itself is queued as evidence by WHICHEVER
    consumer made the change: the live consumer at any time, the cache consumer once its replay is complete. A
    reader folds evidence by the same rule this view does — greatest revision per identity, newest opening per
    strike — so it converges without discarding anything. This is what closes the reviewed gap: the live consumer
    re-seeks END on a reconnect and skips an update, the continuing cache consumer folds it after completion, and
    every connected reader now receives it in order instead of keeping the superseded revision. An admission that
    changed nothing — a redelivery, the second consumer's copy of a record the first already folded, an older
    revision after a newer one — queues nothing, so a record reaches a socket once, whichever consumer folds it
    first. While the replay is incomplete the cache consumer queues no evidence (a week of replay would otherwise
    flood every socket): every reader is LOADING — the hello, every page and every control say so — and the
    completion is itself an authority change after which each reader discards what it holds and re-walks `latest`,
    which already contains every change folded before it. A CHECKPOINT is forwarded by the live consumer only.
  - INVALIDATING — a refusal, an eviction that moves the boundary, a dropped eviction marker, failing closed, the
    replay completing or reopening. These advance `authority` and queue a control frame after any evidence the
    same mutation queued.
  - Additive changes deliberately do NOT advance the authority. The reader this protocol serves (options-edge
    `strike-board.js`, `applyAuthority`/`backfill`) treats every newer authority as an invalidation: it releases
    its published fold, every chip reads LOADING, and it re-walks `latest` (restarting a walk that meets a newer
    authority, with backoff). Both consumers fold every strike record, so an authority per additive change would
    fire on most live updates and keep the board LOADING through the session.
  - `gateway_footprint_broadcast_total{event="es-footprint-strike"}` counts the evidence frames the fold queued,
    from either consumer (G-R9's "the cache consumer never broadcasts" remains the rule for the other footprint
    events).
- **A slow socket stalls nothing (final re-review #1).** The fold's drainer fans each frame out to every socket's
  channel while it holds the stream's delivery order, so a socket that overflows does so ON the drainer. The
  channel is marked closed at once (every later enqueue is refused, its queue dropped, the slow-client counter
  incremented) and its session close — then the detach callback — runs on a teardown thread of its own: closing
  a WebSocket session can block behind the very write that made the client slow, and it used to block the live
  consumer inside the drainer, the cache consumer waiting for that drainer, its refusal control, and every
  healthy socket. This is `OutboundChannel`'s rule for every stream the gateway serves (overflow, write error
  and the write-deadline watchdog alike): at most one teardown thread per channel, ending when the close returns
  (bounded by the container's blocking-send timeout even when the write never completes); until then the
  channel stays registered and closed, so no sender can fall back to writing to its session directly.
- **Authority and incarnation.** `authority` is monotonic WITHIN a gateway process and bumps on every authority
  change. `incarnation` is a random UUID fixed when the process builds its strike view; it rides beside
  `authority` on the hello, every control frame and every page. A reader that meets a different `incarnation`
  resets its authority comparison (a restarted gateway starts again at 0); within one incarnation it discards
  an older authority that arrives after a newer one (round-3 #3).
- **Hello and control schema (G-R6).** The `cvd-hello` field `footprintStrike` and the body of every
  `es-footprint-strike-control` frame are the SAME object:
  `{"authority":n,"incarnation":"<uuid>","symbol":"<configured symbol>","sessionDate":"YYYY-MM-DD"|null,"hwm":{"<tf>":seenMaxBarStartMs,…},"historyBeginsAtMs":n|null,"replayBeginsAtMs":n|null,"loading":bool,"refused":n,"unavailable":bool}`.
  `symbol` is JSON-escaped and a configured value that could not be a symbol is refused at start-up (round-3
  #6); `sessionDate` and `hwm` advance only for the configured symbol (round-3 #8).
- **Page schema (G-R7)** — a DIFFERENT object: it carries no `symbol`, `hwm` or `unavailable` (an unavailable view
  answers 503 instead of a page).
  `latest`: `{"sessionDate":"<the requested session>","historyBeginsAtMs":n|null,"replayBeginsAtMs":n|null,"loading":bool,"authority":n,"incarnation":"<uuid>","refused":n,"tombstones":[{"strikeCents":n,"openBarStartMs":n},…],"episodes":["<record>",…],"nextCursor":n|null}`.
  `history`: the same fields without `tombstones`; its `sessionDate` is the newest session the view holds for the
  configured symbol, and `nextCursor` is the opaque string cursor or null.
  `tombstones` names each strike the page visited whose NEWEST episode in the requested session is refused or
  evicted (its eviction marker) — the strikes `latest` shows no row for. Cursor semantics are unchanged: a
  tombstone strike counts as visited.
  History pages keep omitting refused identities.
- **Bytes to the reader (R14)**: the strike record rides the live frame and the backfill pages as a JSON STRING
  LITERAL. The live frame quotes the record as consumed; a page writes it straight from the retained UTF-8 array,
  escaping byte by byte (`FootprintStrikeView.writeQuoted`) — byte-identical to quoting the decoded String,
  because every escaped byte is ASCII and every byte of a multi-byte character is ≥ 0x80. A page therefore folds
  exactly the bytes this relay folded, not a re-serialisation.
- **Backfill (G-R7)**: `GET /api/footprint/strike/latest?tf&sessionDate&symbol=&afterStrike&limit≤200` (one folded
  record per strike for ONE symbol, ONE session and ONE timeframe, ascending strike, exclusive strike cursor; the
  envelope's `sessionDate` is the REQUESTED session) and `GET /api/footprint/strike/history?tf&strikeCents&symbol=&before&limit≤100`
  (newest first, across sessions, opaque exclusive cursor `sessionDate|%019d(openBarStartMs)`, validated as a
  canonical date and an in-domain epoch). `symbol` defaults to `GATEWAY_ES_FOOTPRINT_STRIKE_SYMBOL` (`ES.v.0`).
  Same flag → auth → permit → cursor → snapshot → streamed write order; `unavailable` → 503.
- **Locale**: fixed-width keys, cursors and drop labels are formatted under `Locale.ROOT`.
- **Metrics (G-R9)**: the fifth event / third keyed event / two new routes / drop reasons `collision`, `refused`,
  `evicted`, `unavailable` / reject reason `unavailable`; the broadcast domain is the five evidence events plus
  `es-footprint-strike-control`; and
  `gateway_footprint_strike_{episodes_in_view,view_bytes,view_retained_bytes,view_metadata_bytes,loading,evictions_total,collisions_total,refused_identities,unavailable,eviction_markers,eviction_marker_drops_total}`.
- **R21**: the relay adds no field to a record; the page's chip words are the page's business.
- **Deployment coupling (protocol v2).** `incarnation` (hello, control, pages) and `tombstones` (latest pages) are
  additive: a reader that ignores unknown fields keeps working. The options-edge web change that consumes them —
  resetting its authority comparison on a new incarnation, and dropping a held value for a tombstone strike —
  deploys together with this gateway change. The final re-review changes add no field: eviction markers ride the
  existing `tombstones` (the web fold already treats a tombstone as "the newest episode is not served"), a dropped
  marker rides the existing authority/control frame, and cache-consumer evidence is the existing
  `es-footprint-strike` frame.
- **Pinned by** `FootprintStrikeViewTest` (fold, bounds, tombstones, retained-storage accounting incl. the final
  review's Unicode case, byte-exact quoting, latch-controlled refusal/eviction ordering, hello sequencing,
  incarnation; the final re-review's cache-only change after completion, eviction markers with their charge,
  bound, byte-budget priority and drop-as-authority-change), `FootprintStrikeDeliveryTest` (a slow socket whose
  close blocks, through the production writer pool, stalling neither consumer nor any healthy socket; a cache-only
  update after completion reaching every socket once; an evicted newest episode named on the wire; the same
  orderings through real socket channels with REST reads
  interleaved, fifth-event fan-out in authenticated mode with rejection suppression, a producer-shaped record
  byte-exact through live/latest/history, and the replay lifecycle — absent→adopted, exact cutoff, completion
  after application and without shared readiness, retry reopen — driven through the production cache-consumer
  loop against a scripted broker), `FootprintSeamTest` (the live consumer's END seeks include the strike topic on
  retry and adoption and never touch the replay) and `FootprintBackfillControllerTest`. What those tests do not
  establish is listed in ES-FOOTPRINT-STRIKE-GATEWAY-CODEX-FINAL.md.
