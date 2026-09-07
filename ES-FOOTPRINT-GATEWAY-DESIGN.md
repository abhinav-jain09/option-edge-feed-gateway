# ES Footprint — Gateway bindings (Gate 2, F-R20)

Revision 2 — 2026-09-07 (Codex round 1: REQUEST_CHANGES, 7 findings → all dispositioned below).
Requirement doc per rule.md (doc → Codex review → code). Nothing here is IMPLEMENTED until the
coordinated-PR protocol says so.

Upstream contract: `options-edge-processing/ES-FOOTPRINT-DESIGN.md` revision 13 (§5 grammar,
F-R14 topics and keys, F-R15 live publication, F-E8 bounds, F-R20). Downstream: the Gate-3 page in
`options-edge` (F-R21/F-R22).

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
| Backfill `GET /api/cvd/bars?tf&toMs&afterMs&limit&sessionDate` → `{sessionDate, [sessionMismatch], bars[], nextCursor}`; page, cursor and session stamp are ONE synchronized snapshot | `GatewayController.java:50-72`; `FeedGatewayService.java:10247-10269` |
| Per-session (auth) routing drops any event not in the standalone allowlist; `es-cvd` / `es-cvd-bar` are allowlisted | `FeedGatewayService.java:11493-11494` |
| The external ES CVD page stores the hello object and reads only `sessionDate`/`hwm`, so an added field is ignored by it | `options-edge/src/app/web/assets/es-cvd.js:278-304` |
| Tests: view semantics and the one-wiring-path pin | `src/test/java/app/feedgateway/CvdBarViewTest.java` |
| es4 deployment: `JAVA_TOOL_OPTIONS=-Xms256m -Xmx1536m`, container memory limit `1536Mi` | `options-edge-deploy/k8s/es4/services/es-feed-gateway.yaml:120-121`, `:176-178` |

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
| G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's). `GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` ascending by view key within `tf`, `toMs` inclusive on `resolvedAtBarStartMs`, `after` = an EXCLUSIVE lower bound given as a previous page's `nextCursor` (the last key string). Cursor grammar: `tf|<19 digits>|<identity>` where `tf` equals the query `tf`; an `after` that is non-empty and fails the grammar ⇒ HTTP 400 `{"error":"bad cursor"}`; a well-formed cursor that no longer exists is legal (string lower bound). `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from. Same JWT gate as every `/api` route. |
| G-R8 | **Bounds and heap.** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON lengths) and ≤ 12 000 records; outcomes ≤ 16 MiB and ≤ 20 000 records; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key, so an evicted bar is simply outside the page's backfill (the page is bounded to the newest 150 per timeframe, P-R4 of the page design). Retained-heap bound: JSON strings of ASCII are Latin-1 compact strings (1 B/char), keys ≤ 160 B, `TreeMap` entry overhead ≤ 64 B ⇒ retained ≤ 144 MiB × (1 + 224 B / 17 KiB average) < 150 MiB; worst case with every record at the ceiling: 12 000 × 256 KiB is capped by the byte budget at 128 MiB, so the bound holds by construction. The es4 gateway heap is `-Xmx1536m` inside a `1536Mi` container limit (§1); deployment of the flag is CONTINGENT on a recorded measurement in the deploy PR: `jvm_memory_bytes_used{area="heap"}` after full boot with CVD on must leave ≥ 320 MiB of headroom under `-Xmx`, or `-Xmx` AND the container limit are raised together in the same PR. Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits. |
| G-R9 | **Metrics.** `gateway_footprint_enabled`, `gateway_footprint_records_total{event}`, `gateway_footprint_drops_total{event,reason=oversize\|shape\|stale_session}`, `gateway_footprint_evictions_total{view=bars\|outcomes}`, `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}`, `gateway_footprint_backfill_requests_total{route}`, `gateway_footprint_backfill_rejected_total{route,reason=bad_cursor\|session_mismatch}`. |
| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
| G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract on and off. |

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

## 4. Deploy (es4)

`k8s/es4/services/es-feed-gateway.yaml`: `GATEWAY_ES_FOOTPRINT_ENABLED=true`, the four
`KAFKA_ES_FOOTPRINT_*` names (prefixed to `es.futures.footprint*` by the gateway),
`GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` equal to the service's `FOOTPRINT_MAX_RECORD_BYTES`, after the
service PR is deployed and the G-R8 heap measurement is recorded in the PR.

## 5. Codex review request (round 2)

Re-verify every AS-IS anchor; check each round-1 disposition against the requirement it changed;
verify the key encoding's order claim over the epoch domain and the identity alphabet; verify the
retained-heap arithmetic; verdict `APPROVE` or `REQUEST_CHANGES` with numbered findings.
