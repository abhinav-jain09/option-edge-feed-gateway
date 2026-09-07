# ES Footprint — Gateway bindings (Gate 2, F-R20)

Revision 1 — 2026-09-07. Requirement doc per rule.md (doc → Codex review → code). Nothing here is
IMPLEMENTED until the coordinated-PR protocol says so.

Upstream contract: `options-edge-processing/ES-FOOTPRINT-DESIGN.md` revision 13 (§5 grammar,
F-R14 topics and keys, F-R15 live publication, F-R20). Downstream: the Gate-3 page in
`options-edge` (F-R21/F-R22).

## 0. Purpose

Relay the four footprint topics to browser clients exactly the way the ES CVD topics are relayed
today (ES-CVD-DESIGN.md R31/R46, as built in `FeedGatewayService`), so the Gate-3 page can use the
CVD page's plumbing verbatim: standalone WebSocket events, a keyed bar view with a REST backfill,
and the connect-time hello handshake. The gateway is a VERBATIM relay: it never enriches, re-keys
or interprets a footprint record. Flag off (default), nothing footprint is subscribed, retained,
broadcast or exported.

## 1. AS-IS (source-anchored, `origin/main` 47bd409)

| Fact | Where |
|---|---|
| ES CVD topics are wired ONCE for both the state cache consumer and the state live consumer through `addEsCvdTopics(topicEvents)` (two call sites, one method), gated by `GatewaySettings.esCvdEnabled()` (`GATEWAY_ES_CVD_ENABLED`, default false) | `FeedGatewayService.java:1905`, `:2041`, `:2053-2058`; `GatewaySettings.java:264-276` |
| `es-cvd` is broadcast STANDALONE (never selection-gated, never cached); `es-cvd-bar` is upserted into the keyed view FIRST, then broadcast | `FeedGatewayService.java:2833-2848` (`runLiveConsumerOnce`) |
| Keyed view `cvdBars: TreeMap<tf\|barStartMs, json>` with MONOTONIC session rollover on the record's `sessionDate` (string order = date order); older-session records dropped; foreign shapes dropped from the view but still broadcast | `FeedGatewayService.java:9656-9690` |
| Connect hello `cvd-hello` = `{sessionDate, hwm:{tf: maxBarStartMs}[, levels]}` sent to every new session when CVD or SPX-levels is enabled | `FeedGatewayService.java:908-916`, `:10219-10235` |
| Backfill `GET /api/cvd/bars?tf&toMs&afterMs&limit&sessionDate` → `{sessionDate, [sessionMismatch], bars[], nextCursor}`; page, cursor and session stamp are ONE synchronized snapshot | `GatewayController.java:50-72`; `FeedGatewayService.java:10247-10269` |
| Per-session (auth) routing drops any event not in the standalone allowlist; `es-cvd` / `es-cvd-bar` are allowlisted | `FeedGatewayService.java:11493-11494` |
| Tests: view semantics and the one-wiring-path pin | `src/test/java/app/feedgateway/CvdBarViewTest.java` |

Upstream record facts the gateway relies on (ES-FOOTPRINT-DESIGN.md §5): every record carries
`symbol`, `sessionDate` (ISO `YYYY-MM-DD`, so string order is date order), `timeframe`; a closed-bar
record carries `observations.barStartMs`; an outcome record carries `identity`,
`resolvedAtBarStartMs`; the two live snapshots carry `eventTimeMs` and a `timeframes` object. Bar
and outcome records are compacted-topic upserts (F-R14): at-least-once delivery is invisible under
last-write-per-key.

## 2. Requirements

| id | Requirement |
|----|-------------|
| G-R1 | **Flag.** `GATEWAY_ES_FOOTPRINT_ENABLED` (default `false`; `GatewaySettings.esFootprintEnabled()`). Flag off: no footprint topic is subscribed by any consumer, no view exists, the hello carries no `footprint` field, the backfill routes answer `404`-equivalent `{"enabled":false}` (HTTP 404), and no `gateway_footprint_*` metric except `gateway_footprint_enabled 0` is exported. Flag on with a topic absent on the cluster: the gateway starts (the topics are OPTIONAL like every other DATABENTO JSON topic) and the page reports no data. |
| G-R2 | **Topics and bindings.** Four settings, LITERAL names with the gateway's usual `*_TOPIC` prefixing: `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`). ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`. |
| G-R3 | **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`). `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class). All four events are added to the per-session standalone allowlist. |
| G-R4 | **Bar view.** `footprintBars: TreeMap<tf\|barStartMs, json>` with the CVD view's exact semantics: key from the record's `timeframe` and `observations.barStartMs`; last write per key wins; MONOTONIC rollover on the record's own `sessionDate` (a newer date clears the view and becomes current; an older date is dropped; equal keeps); a record without `timeframe`/`observations.barStartMs`/parseable JSON is dropped from the view but still broadcast. The view is SEPARATE from `cvdBars` and keeps its own `footprintBarsSessionDate` (the CVD's `sessionDate` is `yyyymmdd`; the footprint's is ISO — the two are never compared). |
| G-R5 | **Outcome view.** `footprintOutcomes: TreeMap<tf\|resolvedAtBarStartMs\|identity, json>`, same rollover rule keyed on the record's `sessionDate`, last write per `identity` wins: an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same `resolvedAtBarStartMs` and overwrites in place; a foreign shape (missing `identity`, `timeframe`, `resolvedAtBarStartMs`) is dropped from the view but still broadcast. |
| G-R6 | **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` (`sessionDate` null and empty maps before the first record). It rides the SAME frame so the page has one handshake; `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled. Flag off, the field is absent (its ABSENCE tells the page the gateway has no footprint). |
| G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=200&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics verbatim (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` capped to `[1, 500]` — footprint bars are up to 64 KB each (F-E8), so the cap is 500, not 1000, and the default 200), computed as ONE synchronized snapshot with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the view's current one). `GET /api/footprint/outcomes?tf&toMs&after=&limit=500&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` where the cursor is the OPAQUE last view key (string; `after` exclusive, `toMs` inclusive on `resolvedAtBarStartMs`). Same JWT gate as every `/api` route. |
| G-R8 | **Bounds.** Both views are bounded by construction: one session of records (upstream ≤ 9 122 bars and ≤ 15 339 outcomes on the reference session; ≤ 64 KB per bar and ≤ 2.5 KB per outcome by F-E8) ≈ 160 MB worst case for bars — the gateway heap must absorb it. Because a footprint bar is ~10× a CVD bar, the bar view drops a bar record whose UTF-8 length exceeds `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 = the producer's `maxRecordBytes`) — counted in `gateway_footprint_drops_total{reason="oversize"}`, never broadcast (a record over the producer's own ceiling is corrupt, not large). |
| G-R9 | **Metrics.** `gateway_footprint_enabled`, `gateway_footprint_records_total{event}`, `gateway_footprint_drops_total{event,reason=oversize|shape|stale_session}`, `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_backfill_requests_total{route}`. |
| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (parse failures are drops). |
| G-R11 | **Tests** (JUnit, no broker): the one-wiring-path source pin; view upsert/rollover/stale-drop/foreign-shape for BOTH views; hello field on/off; both backfill pages (ascending, exclusive cursor, inclusive bound, cap, session mismatch, atomic snapshot); oversize drop; allowlist membership for the four events. |

## 3. Decisions

- **D1 No live retention.** `es-footprint`/`es-footprint-evidence` are not retained for connect
  replay: the producer's 5 s heartbeat bounds the wait and the CVD page already lives with the same
  bound. Retention would add the SPX-levels attestation machinery for no page benefit.
- **D2 One hello.** A second `footprint-hello` frame would give the page two handshakes to order;
  one field inside the existing frame keeps the R46 protocol single.
- **D3 Verbatim.** The gateway parses a record only to extract keys; the bytes it broadcasts and
  backfills are the producer's bytes.
- **D4 Opaque outcome cursor.** Outcomes have no monotonic single number per timeframe (many resolve
  in the same bar), so the cursor is the last view key, which is total-ordered by construction.

## 4. Deploy (es4)

`k8s/es4/services/es-feed-gateway.yaml`: `GATEWAY_ES_FOOTPRINT_ENABLED=true` and the four
`KAFKA_ES_FOOTPRINT_*` names (prefixed to `es.futures.footprint*` by the gateway's `TOPIC_PREFIX`),
after the service PR is deployed. Heap: review `-Xmx` against G-R8 before enabling.

## 5. Codex review request (round 1)

Review against the three bars (institutional accuracy, military completeness, NASA preparation):
every AS-IS anchor must be true of `origin/main`; every requirement must be implementable without
touching a CVD path; look for a rollover, cursor or allowlist rule that could disagree with the CVD
page's expectations; verdict `APPROVE` or `REQUEST_CHANGES` with numbered findings.
