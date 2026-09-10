# ES Footprint strike interaction — gateway Codex round 2 (gpt-6-astra, 2026-09-10)

Review of PR #179 at 6a94794. Verdict: **REQUEST_CHANGES** (8 findings + the round-1 dispositions). Every finding folded; dispositions:

| # | finding | disposition |
|---|---|---|
| 1 | the revision-digest ledger and the refusal identities are unbounded; the refusal COUNT is not a memory bound | FIXED — the view now charges what it actually holds: each head's payload, its identity string, 40 B per observed revision, and each tombstone's identity, published as `gateway_footprint_strike_view_metadata_bytes` and enforced together with the payload bytes against `_MAX_BYTES`. A symbol over 64 characters is a shape drop, so one record cannot charge arbitrary metadata. When eviction cannot bring the view inside the budget it fails closed, like the refusal ledger. Your 10,000-revision reproduction is now a test: the budget holds at EVERY revision, the identity leaves through the published boundary and the chip reads NO DATA rather than a stale head |
| 2 | equal-time eviction leaves visible heads BELOW the boundary, freezing their updates and hiding their collisions | FIXED — eviction takes the whole equal-opening-time bucket, and the boundary is the oldest RETAINED opening, so every retained head is at or after it and nothing evicted can return at any revision. Pinned with your exact scenario (two strikes at one millisecond, `maxEpisodes=1`), including that a later opening still admits and updates, and that every returned record opens at or after `historyBeginsAtMs` |
| 3 | an incomplete bootstrap can be reported as completed NO DATA | FIXED — the view carries the cache consumer's replay window and its completion (`replay(beginsAtMs, complete)`, called when the consumer that owns the strike topic crosses the end offsets captured at its bootstrap). Until then the hello, every page envelope and `gateway_footprint_strike_loading` say so, and completion fires the control frame. Pinned: a live CHECKPOINT that raises the high-water mark mid-replay cannot make an empty page a completed answer |
| 4 | entering UNAVAILABLE does not invalidate already-connected readers | FIXED — `es-footprint-strike-control` carries the same field the hello carries (no evidence, only the authority) on a collision refusal, on failing closed and on replay completion; allowlisted for per-session routing exactly like the stream. The companion page applies it like a hello, so a READY reader reconciles instead of displaying evidence the fold has withdrawn |
| 5 | quoted records invalidate the proxy's page-size arithmetic | FIXED on the proxy side (options-edge PR #726): both strike routes size pages by the ESCAPED record (`6·bytes + 4`), start-up refuses a ceiling whose escaped form admits no page, and the forwarded limit is 42 rather than 200. `FootprintStrikeView.quotedBoundBytes` states the bound the proxy uses so the two cannot drift |
| 6 | `historyBeginsAtMs` reports inventory, not replay coverage | FIXED — `replayBeginsAtMs` (the window the replay actually covered) is published beside `historyBeginsAtMs` (retained inventory), in the hello, in every page and on the control frame, because neither implies the other |
| 7 | tombstone pruning scans the whole index per eviction, under the lock | FIXED — pruned once per enforcement, and only when tombstones exist |
| 8 | `quoted()` calls `String.format` per control character on the Kafka thread | FIXED — hexadecimal escapes are written directly. Pinned with an exhaustive control-character round trip and the worst-case bound |
| — | the G-R8 resource amendment claims headroom it did not measure (`limit − Xmx` is native margin, not free heap) | ACCEPTED and rewritten: what can be stated is that retained bytes are now bounded by `_MAX_BYTES` because every retained byte is charged to it, and that the two-consumer Kafka allowance rises from ~25 MiB to ~31 MiB with a fifth topic. What is NOT established — the `Xmx − H_peak` and `limit − W_peak` inequalities — is named as needing the measurement G-R8's deployment contingency already requires, with the strike view's own contribution observable as `_view_bytes` + `_view_metadata_bytes` |
| — | the page had no way to scope its live fold to the symbol the routes answer for | FIXED — the hello names the relay's symbol (`GATEWAY_ES_FOOTPRINT_STRIKE_SYMBOL`), so a reader folds exactly what REST returns |
| — | round-1 dispositions | Your table is taken as recorded: findings 2, 3, 4, 7 and 8 CLOSED; 1, 5, 6 and 9 were PARTIAL and are addressed above. The coverage rows you marked STILL OPEN that belong to the executed-integration class (fifth-event consumer seam, sink ordering under oversize, cache adoption) remain open and are named as such rather than claimed |

74 Footprint tests green.

## Review text (verbatim)

**Request changes.** Round 2 fixes several important defects, but retained episodes can still become permanently stale, memory remains unbounded, and the new wire encoding invalidates the proxy’s page-size calculation.

Reviewed HEAD `6a94794`, the full `origin/main..HEAD` diff, the round-2 delta, both designs, producer serialization, and the companion web consumers.

Verification: 48 focused gateway tests executed against prebuilt classes: **16 passed; 32 failed during fixture construction** because the read-only sandbox prevented `SellerActivityDiskStore` from creating its directory. Separately, an isolated JShell harness compiled the current view/coordinator/test bodies and passed all **11 view tests**, then reproduced the defects below. Both companion JavaScript suites passed **50/50**. This is not a clean Maven build.

**Part A — round-1 dispositions**

References below use current HEAD line numbers:

| Reference | File |
|---|---|
| V | [FootprintStrikeView.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java) |
| S | [FeedGatewayService.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java) |
| C | [GatewayController.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewayController.java) |
| G | [GatewaySettings.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewaySettings.java) |
| VT | [FootprintStrikeViewTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintStrikeViewTest.java) |
| CT | [FootprintBackfillControllerTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintBackfillControllerTest.java) |
| WT | [FootprintWiringTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintWiringTest.java) |
| ST | [FootprintSeamTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintSeamTest.java) |

“Pins” identifies the committed test and its scope; it does not imply that a fixture-blocked test passed during this review.

| Round-1 finding | Disposition | Evidence and test pin |
|---|---|---|
| 1. Superseded-revision collisions; evicted identities returning | **PARTIAL** | V:134–142 checks every retained digest; V:130 rejects old openings. VT:43, `aCollisionRefusesTheIdentity…`, exercises both arrival orders; VT:130 exercises re-admission below the cutoff. **Equal-time eviction leaves retained identities behind that cutoff, bypassing their collision checks.** B2 below. |
| 2. Symbol omitted from index | **CLOSED** | V:125,151,178,238,264 scopes identity, insertion, removal and reads consistently. VT:77, `symbolsHaveIndependentIndexesAndRemoval`, pins independent histories and refusal isolation. |
| 3. Refused newest episode exposes an older value | **CLOSED** for the view selection defect | V:166–173 preserves the index tombstone; V:242–246 selects the newest identity before resolving its value. VT:67, `aRefusedNewestEpisodeNeverFallsBackToAnOlderValue`, pins suppression, historical retention and a genuinely newer replacement. Connected-reader notification remains a separate issue in B4. |
| 4. Unsafe hello timeframe; CHECKPOINT date; inappropriate numeric ceiling | **CLOSED** for the reported defects | V:115–124 validates timeframe/date and separates epoch from nonnegative-long domains. VT:119/159 cover nullable/invalid dates and large revisions. The quoted-timeframe fixture at VT:165 is itself invalid JSON, so it does not independently pin vocabulary enforcement; my valid-JSON quoted-timeframe probe did reject it without changing hello. |
| 5. Unbounded refusal metadata and incomplete heap proof | **PARTIAL** | V:172–173 bounds refusal **count** and enters unavailable; VT:147 and CT:155 pin that transition and HTTP handling. Digest count and identity bytes remain unbudgeted. B1. |
| 6. Unreliable history boundary | **PARTIAL** | V:195–199 makes the eviction boundary monotonic; VT:130 pins that improvement. Equal-time retention violates the boundary, and replay coverage is still absent. B2/B6. |
| 7. Latest envelope names another session | **CLOSED** | V:251 returns the requested session. VT:89, particularly 99–103, pins a retained previous session and an empty future session. |
| 8. Locale-dependent keys/labels; impossible cursors | **CLOSED** for implementation | V:281–283,305–306 and S:916/925 use semantic cursor validation and `Locale.ROOT`. VT:89/174 pin invalid cursors and Arabic locale. My probes accepted epoch 0, 9, 10 and the maximum. A committed Turkish metric-label probe is still missing. |
| 9. Inadequate fifth-stream conformance tests | **PARTIAL** | CT:136 adds busy precedence; CT:155 adds unavailable handling; ST:114 now includes the fifth partition; VT:188 and CT:74 cover string envelopes. Most previously missing integration/failure probes remain absent, as itemized below. |

Every row of the original fold/read coverage table:

| Clause | Disposition | Evidence / pin / remaining gap |
|---|---|---|
| Greatest revision; older after newer | **PARTIAL** | V:134–145; VT:34 `theFoldTakesTheGreatestRevision…`. Head-only accounting is asserted, but ascending growth/shrink accounting lacks a committed test. |
| Identical bytes; collisions; later refusal | **PARTIAL** | V:130–142; VT:43/67. Superseded revisions and newest refusal are covered; retained equal-time survivors bypass this logic. |
| CHECKPOINT HWM, no episode, nonregression | **PARTIAL** | V:116–120,159–162; VT:119/159. Nullable and invalid dates covered; the recovery-key/type matrix remains unprobed. |
| Latest opening/session/timeframe/order/cursor | **PARTIAL** | V:234–251; VT:77/89. Symbol and envelope corrections pinned; numeric strike digit-boundary tests remain absent. |
| History ordering and exclusive cursor | **PARTIAL** | V:260–283; VT:89; CT:108. Semantic rejection added; nonexistent valid cursor and HTTP URL round trip remain absent. |
| Both eviction budgets | **PARTIAL** | V:189–201; VT:130 now identifies the byte-budget survivor. Equal-time handling fails; mixed-timeframe/out-of-order and revision growth/shrink remain unpinned. |
| Index/heads/byAge consistency | **STILL OPEN** | V:166–212. VT:77 checks one symbol-removal case; no complete invariant checker across all transitions. B2 demonstrates a broken boundary invariant. |
| Atomic snapshots; no lock during writes | **PARTIAL** | V:218/234/260; C:125/152/186. Lock separation is sound by inspection. No concurrent strike read/admission or blocked-strike-writer test. |
| Hello/latest/history agreement | **PARTIAL** | V:218–276; VT:67/89/119/130/147. Individual cases improved; boundary correctness and readiness agreement remain broken. |
| LOADING versus completed NO DATA | **STILL OPEN** | V:159–162; S:2888–2893,11341. No completion barrier is represented in strike responses. No cache-behind-live/partial-bootstrap integration pin. B3. |
| Shape and oversize | **PARTIAL** | V:104–124; VT:159. More cases covered, but no complete type/domain matrix or exact UTF-8 ceiling/one-byte-excess test. |
| Fixed-width epoch ordering | **PARTIAL** | V:281–306; VT:174 pins locale. Review probes covered endpoint cursor acceptance; committed numeric-ordering tests across the full domain remain absent. |

Every row of the original service coverage table:

| Clause | Disposition | Evidence / pin / remaining gap |
|---|---|---|
| Same flag; fifth topic bound unconditionally | **CLOSED** | S:815–828,2476–2483; WT:41/108 source and constructed-map pins. |
| Same gate for five topics | **PARTIAL** | S:849–851,929–980; WT:271 startup cells include strike. No strike-specific rejection→later-admission/counter test through both flows. |
| Both consumers share admission | **PARTIAL** | S:910,2888,3323; WT:70 source pin. No executed fifth-event consumer-loop seam. |
| Admit before broadcast; oversize reaches no socket | **PARTIAL** | S:986–990; WT:70 source pin. ST:179 still exercises four event families; no strike sink-ordering/oversize delivery test. |
| Seven-day cache policy | **PARTIAL** | S:6633–6636; WT:128/139 policy assertion. Actual fifth-topic timestamp seeking and adoption unpinned. |
| Live END on bootstrap/retry/adoption | **PARTIAL** | S:978–982; ST:114 now includes strike. ST:127/153 retry/adoption cases still omit it. |
| Hello field; flag-off bytes | **PARTIAL** | S:825,11341; WT:93/222. Hello behavior covered; explicit flag-off strike-view-null assertion absent. |
| Authenticated per-session allowlist | **PARTIAL** | S:12613; WT:87 source pin. ST:179/219 still omit strike from executed fan-out assertions. |
| Every startup metric cell; flag-off exception | **CLOSED** | S:1014–1059; WT:93/270 exact-series tests updated. |
| Nonzero strike metrics and overlap identities | **PARTIAL** | S:910–918,1044–1055; CT:155 pins unavailable counters/gauge. Full collision/refused/shape/oversize/cache/live/broadcast/eviction/locale sequence absent. |
| Existing-stream non-interference | **PARTIAL** | S:910–927 separates admission; original events retain their framing. Shared memory/CPU exposure and failure notification remain unresolved. B1/B4/B7/B8. |

Every row of the original route/settings coverage table:

| Clause | Disposition | Evidence / pin / remaining gap |
|---|---|---|
| Binding→flag→auth→permit→validation→snapshot→write | **PARTIAL** | C:112–158; CT:136 pins busy precedence. WT:326 and ST:249 still exercise original routes, not strike binding/overlaps. |
| Flag/auth precedence | **PARTIAL** | C:199–218; CT:74/108 cover both flag-off routes and history auth failure. Latest auth failure and overlapping flag/auth/cursor cases absent. |
| Limit clamps | **PARTIAL** | C:126/153. CT:74/108 exercise small limits; no over-200/100 dataset or zero/negative-limit pin. |
| Permit release | **PARTIAL** | C:130–131,157–158; CT:108/136/155 assert several paths. Strike mid-write/flush failure and buffer refusal remain untested. |
| Verified 64 KiB streaming buffer | **PARTIAL** | C:167–194. CT:74 verifies decoded record text; strike ignored-buffer, oversized-buffer and blocked-writer tests absent. Encoding adds substantial temporaries. |
| Envelope | **PARTIAL** | C:180–193; CT:74; VT:89. Session/string shape fixed; encoded page-cap compatibility fails. B5. |
| Requested-session validation | **PARTIAL** | C:124; CT:74 rejects compact date. Route tests for impossible calendar date, unpadded and empty values absent. |
| Setting names/defaults | **CLOSED** | G:342–367; WT:313 `settingsDefaults` covers the new defaults. |
| Bounds/prefix/overrides | **PARTIAL** | G:343–367,1880–1910 use existing helpers. No strike-specific override, prefix/no-double-prefix, clamp or malformed/overflow tests. |

R21 remains sound by inspection: producer records are retained as original text and bypass enrichment at S:9556–9560. However, VT:21 still uses a fabricated `tag` series rather than the producer’s complete series grammar. There is no gateway end-to-end test carrying producer-shaped unknown enums and nulls byte-exactly through live, latest and history.

**Part B — fresh findings, most severe first**

1. **[P1] The new digest ledger is unbounded, and the refusal count does not establish a safe memory bound.**

   **Evidence:** [V:66](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:66) creates a revision map per head. [V:141](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:141) inserts every new revision, including older revisions that immediately return. Enforcement at V:191 considers only head count and current JSON bytes.

   Executed: 10,000 revisions of one identity, with `maxEpisodes=1` and `maxRefused=1`, produced:

   ```text
   digests=10000
   bytes=460
   heads=1
   unavailable=false
   ```

   Separately, V:115 accepts any nonempty symbol up to the record ceiling. Refusal retains the composite identity at V:172. Ten thousand approximately 260 KiB identities alone approach **2.5 GiB**, before index keys and collection overhead.

   **Why it matters:** A single long-lived or faulty identity can exhaust the shared gateway heap without violating any configured budget. Bounding refusals by count does not prevent large identities exhausting the heap before that count is reached.

   **Corrected code or test:** Introduce enforced digest and metadata byte/count budgets. Check capacity before inserting, including the older-revision branch; on exhaustion, enter the documented unavailable state without forgetting collision evidence. Bound symbol/identity size or charge their actual retained storage. Add `revisionEvidenceCannotOutgrowItsBudget` and `largeRefusedIdentitiesRespectMetadataBudget`, asserting counters, availability and actual ledger sizes.

2. **[P1] Equal-time eviction leaves visible heads below the boundary and permanently disables their updates and collision checks.**

   **Evidence:** [V:195–197](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:195) advances the boundary to at least `oldest.openBarStartMs + 1`, but the loop stops when budgets fit. Other heads opened at the same millisecond remain.

   Executed with `maxEpisodes=1`:

   ```text
   admit strike 680000, open=100, revision=0
   admit strike 680500, open=100, revision=0
   boundary=101; retained strike 680500 still has open=100

   revision=1 update   → EVICTED
   revision=0 conflict → EVICTED
   collisions=0; stale revision=0 remains visible
   ```

   The rejection at [V:130](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:130) occurs before retained-head lookup.

   **Why it matters:** Several strikes opening on the same bar is ordinary producer behavior. Budget pressure can freeze apparently valid chips while suppressing subsequent CLOSE records and collision faults.

   **Corrected code or test:** Make the scalar cutoff consistent with retention: evict the entire equal-opening-time bucket before advancing beyond it, or use an identity-aware cutoff that permits retained survivors to update. Pin:

   ```java
   assertTrue(page.records().stream()
       .allMatch(json -> openOf(json) >= page.historyBeginsAtMs()));
   ```

   Exercise count pressure, byte pressure, updates and conflicts for equal-time identities across strikes/symbols/timeframes. Define the sentinel behavior when eviction occurs at `EPOCH_MAX_MS`.

3. **[P1] Strike backfill can report an incomplete bootstrap as completed NO DATA.**

   **Evidence:** [V:159–162](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:159) records an observed maximum, not contiguous cache progress. Both consumers advance it. Strike pages have no loading/completion field and return 200 unless unavailable at C:127/154.

   The cache loop captures bootstrap end offsets at [S:2733](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:2733), but that barrier is not connected to the strike view. Hello is sent on connection at S:1237. The companion client marks its fold READY when the currently available page walk ends at [strike-board.js:3280](/private/tmp/oe-web-fpstrike/src/app/web/assets/strike-board.js:3280).

   **Why it matters:** A live CHECKPOINT can establish a high HWM while older cache records remain unread. An empty or partial REST result then becomes NO DATA. Later cache-only admissions do not notify the reader. A null-session hello before hydration is likewise indistinguishable from a completed empty log.

   **Corrected code or test:** Expose a captured strike replay barrier and completion state; keep responses/readers loading until the cache consumer crosses it. Publish completion to connected clients. Add a consumer-seam test that pauses cache replay, admits a newer live CHECKPOINT, requests hello/latest, and proves READY/NO DATA is impossible until replay completes—including a CHECKPOINT-only log.

4. **[P1] Entering UNAVAILABLE does not invalidate already-connected readers.**

   **Evidence:** [V:173](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:173) changes only local state. [S:918](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:918) still authorizes broadcast for `UNAVAILABLE`, and S:990 sends the ordinary producer record. The unavailable flag reaches new hellos and REST requests, but no transition notification is emitted.

   The companion client sets its unavailable flag only from hello at [strike-board.js:3296](/private/tmp/oe-web-fpstrike/src/app/web/assets/strike-board.js:3296). A previously READY reader can continue displaying values. Cache-only collisions have the same notification gap: the server refuses an identity without delivering the conflicting observation or an invalidation.

   **Why it matters:** Readers disagree about whether the authoritative fold is usable. A metric and a future HTTP 503 do not revoke a value already displayed.

   **Corrected code or test:** Send an ordered control notification or refreshed strike hello whenever authoritative availability/refusal state requires reconciliation. Keep producer records unchanged. Add an already-connected READY-client test that exhausts the ledger through the cache path and verifies immediate LOADING, then verifies that bars, outcomes and both snapshot streams continue normally.

5. **[P1] Quoted records invalidate the existing proxy cap calculation.**

   **Evidence:** [C:190](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewayController.java:190) now writes quoted strings. The companion proxy still divides its cap by **unquoted** `recordBytes` at [OptionMonitorWebServer.java:2287](/private/tmp/oe-web-fpstrike/src/app/web/OptionMonitorWebServer.java:2287), and forwards up to 200 latest records at line 2360.

   Executed with valid, admitted-size JSON containing escaped backslashes:

   ```text
   original record:       262,052 bytes
   quoted record:         523,712 bytes
   200 quoted records: 104,742,400 bytes, before envelope
   proxy cap:           67,108,864 bytes
   ```

   Those original records total less than the view’s 64 MiB payload budget.

   **Why it matters:** A legal latest page exceeds the proxy’s hard cap, causing failure and repeated LOADING. The existing consumer understands string payloads, but its transport budget does not.

   **Corrected code or test:** Derive the forwarded limit from the maximum **encoded** record size, including separators/envelope, or paginate by encoded bytes with a correct continuation cursor. Add an actual gateway→proxy round-trip test using near-ceiling, escape-heavy valid records. The default history limit of 100 avoids this particular 200-row example; its calculation still needs the same encoding contract.

6. **[P2] `historyBeginsAtMs` still reports inventory rather than established replay coverage.**

   **Evidence:** Before eviction, [V:286–289](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:286) returns the oldest retained opening or null. The cache seeks by Kafka timestamp at [S:3000](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:3000); neither that cutoff nor replay coverage is passed into the view.

   **Why it matters:** A recent UPDATE may refer to an episode opened before the seven-day replay window. Its opening does not prove that other episodes since that opening were replayed. An empty retained view also does not reveal a truncated replay window. Monotonic eviction fixes neither case.

   **Corrected code or test:** Track replay coverage separately from oldest retained opening and eviction cutoff. Publish a precisely defined completeness boundary. Test a recent update for an older episode, omitted episodes outside the seek window, an empty replay, and late topic adoption.

7. **[P2] Tombstone pruning scans the entire index once per evicted head while holding the shared view lock.**

   **Evidence:** [V:199](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:199) invokes pruning inside the eviction loop. [V:205–208](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:205) copies scope/strike entry lists and traverses every indexed episode—even with zero tombstones.

   An isolated source probe with 10,000 heads measured approximately **7 ms** for one eviction. Growing one retained record caused **582 evictions and approximately 202 ms** in one admission. These are local diagnostic measurements, not deployment latency guarantees.

   **Why it matters:** The work is proportional to retained index size multiplied by eviction count. It blocks strike snapshots and the calling Kafka consumer; both consumers also contend for this lock.

   **Corrected code or test:** Maintain tombstones in their own age-ordered structure and remove only those crossing the final cutoff. At minimum, prune once after enforcement and skip it when no indexed tombstones exist. Pin traversal/work counts under a large revision-growth eviction rather than relying on a flaky wall-clock threshold.

8. **[P2] `quoted()` invokes a general formatter per control character on the Kafka thread.**

   **Evidence:** [V:337](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:337) calls `String.format` for each otherwise-unhandled control character. Shape drops still reach this function through S:918/990.

   In a ten-iteration diagnostic probe, approximately 256 KiB ordinary/escape-heavy inputs averaged **0.53 ms**; 256 KiB of NUL characters averaged **55 ms**, producing a **1,572,866-byte** string literal.

   **Why it matters:** A malformed record that fails parsing immediately can still trigger hundreds of thousands of formatter operations before the shared live consumer advances.

   **Corrected code or test:** Emit hexadecimal escapes directly:

   ```java
   sb.append('\\').append('u').append('0').append('0')
       .append(HEX[c >>> 4]).append(HEX[c & 15]);
   ```

   Add exhaustive control-character round trips and a 256 KiB allocation/throughput benchmark. Account for the encoded output size in outbound resource calculations.

**Additional adversarial conclusions**

The structural transitions are mostly coherent: insertion installs a head, index entry and age entry; replacement preserves identity/index ordering; refusal removes head/age/digests while retaining index position and ledger membership; pruning leaves incarnation-long refusal membership intact. The central failures are the equal-time boundary invariant, unbudgeted digest/identity storage, and repeated full-index pruning. UNAVAILABLE stops subsequent valid admissions from mutating state and makes view reads empty/unavailable; it does not release retained storage or notify existing readers.

`quoted()` correctly escapes every U+0000–U+001F control character. Backspace and form feed use valid `\u0008`/`\u000c` escapes. Quotes and backslashes are handled. **U+2028/U+2029 are legal literal JSON characters**; their lack of escaping is not a defect in this JSON/WebSocket use. Valid surrogate pairs survive UTF-8 transport.

An isolated literal unpaired surrogate does **not** survive `quoted(...).getBytes(UTF_8)` unchanged. An original record containing the textual escape `\uD800` does survive, because its backslash is escaped. Thus the helper is not a total lossless serializer for arbitrary Java UTF-16 strings, although the unpaired-literal case is not a valid UTF-8 producer record. Add explicit tests or escape surrogate code units if the helper promises arbitrary-string round trips.

The four existing event payloads remain objects; only strike becomes a string literal. The production footprint frame consumers found in the companion source are `es-footprint.js` and `strike-board.js`: both support string decoding, the former dispatches only its four named events, and the latter retains strike text before parsing. Gateway envelope construction inserts the supplied literal directly at S:13033–13035. **No double-encoding or existing-four-event dispatch regression was found.** The unresolved compatibility defect is the proxy cap.

Symbol defaulting is correct for the supplied deployment: G:362 defaults to `ES.v.0`, matching `ES_CVD_SYMBOL` in the producer deployment. C:125/152 applies it only to an empty symbol; explicit symbols retain their case. The companion proxies currently omit a symbol parameter, so they depend entirely on the gateway default. Configuration override and coordinated nondefault-symbol tests remain missing.

The G-R8 amendment is **not a valid resource proof**. Its claimed es4 1024 MiB headroom is `container limit − Xmx`, which is a native-memory margin—not free Java heap. The existing deployment comments explicitly distinguish that quantity from `Xmx − H_peak`.

Even reusing the old assumptions, five topics increase the two-consumer Kafka allowance from roughly 25 to **31 MiB**. The incomplete subtotal becomes approximately `153.1 + 64 + 1.3 + 31 = 249.4 MiB`, before strike indexes, digests, refusal identities and new encoding temporaries. Additional corrections are required:

- Strike strings are not admitted under an enforced ASCII-only contract; UTF-8 byte accounting does not prove a one-byte-per-character retained-string bound.
- `quoted()` creates a builder, result string and encoded byte array.
- Slow response snapshots can retain replaced/evicted record generations outside the current view-byte counter.
- New outbound frame sizes require explicit queue accounting.

Until metadata is bounded, no finite supported heap contribution can be substituted into the required `Xmx − H_peak` and `limit − W_peak` inequalities.

The requested bars are therefore:

| Bar | Assessment |
|---|---|
| **Institutional** | **FAIL:** equal-time survivors can freeze; incomplete replay can appear complete; connected readers can disagree with authoritative availability. |
| **Military** | **FAIL:** digest growth and large refusal identities defeat resource containment; failure-state notification and critical integration probes remain incomplete. |
| **NASA** | **FAIL:** eviction performs repeated full-index scans, quoting has avoidable hot-thread allocation cost, and neither encoded page bounds nor revised heap arithmetic is established. |

VERDICT: REQUEST_CHANGES
Retained-state correctness, bounded memory, replay readiness, failure notification and encoded transport limits remain unresolved.
