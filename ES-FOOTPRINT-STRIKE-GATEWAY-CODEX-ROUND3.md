# ES Footprint strike interaction — gateway Codex round 3 (gpt-6-astra, 2026-09-10)

Review of PR #179 at 6a94794 + the round-2 delta. Verdict: **REQUEST_CHANGES** (8 findings). Every finding folded; dispositions:

| # | finding | disposition |
|---|---|---|
| 1 | the byte budget charges packed bytes for what is really an object graph | FIXED as far as an accounting bound can go, and no further claimed: 128 B per revision (a boxed key, a node, an array header and the digest), 512 B per head, 256 B per tombstone — deliberate over-estimates — and the design paragraph now says this is an ACCOUNTING bound on what the view may retain, not a measured heap bound. A layout-backed measurement is named as what would settle the latter |
| 2 | replay completion can be declared without replaying the strike partition, and never reopens | FIXED — completion is now derived from the STRIKE partitions this consumer owns crossing the end offsets captured at ITS bootstrap, not from the shared catch-up barriers (which exclude other sources' partitions), and `replayRestarted` reopens it for a new consumer attempt or a late adoption. A consumer that carries no strike partition says nothing |
| 3 | control/evidence/hello ordering is unsafe; refused evidence can return | FIXED at both ends. The relay now forwards ONLY what its fold ADMITTED for the strike stream (every other footprint stream keeps G-R3's rule), so a refused, evicted or shape-dropped record can no longer reach a reader at all; and every authority change bumps a MONOTONIC `authority` generation carried by the hello, every page envelope and the control frame, so a reader can discard an older authority that arrives after a newer one — the stale-hello race |
| 4 | eviction changes authoritative results without invalidating readers, and admission can report ADMITTED for an identity it just evicted | FIXED — an eviction that moves the boundary is an authority change and notifies; admission reports EVICTED when the identity did not survive enforcement |
| 5 | `replayBeginsAtMs` moves on every caught-up poll | FIXED — it is recorded once per replay and names the window that was actually sought; only `replayRestarted` moves it |
| 6 | the configured symbol can corrupt the shared hello | FIXED — written through `quoted()`, and a configured value that could not be a symbol (too long, or carrying a control character) is refused at configuration time |
| 7 | pruned tombstones leave the scan guard permanently enabled | FIXED — pruning decrements the indexed count, so the guard means "there are some" |
| 8 | the control broadcast counter is incremented but never exported | FIXED — the broadcast metric has its own event domain including the control frame, and the zero series is pinned |
| — | (from the ladder review) the hello names one symbol but publishes a session and HWM advanced by every symbol | FIXED — `advance` ignores records of another symbol for the hello's session and high-water mark; those records still fold into the index and are still served by an explicit `symbol=` request |

**No round 4 was possible.** The Codex account reached its usage limit immediately afterwards ("try again at Sep 15th, 2026 12:21 PM"), so these dispositions are unreviewed and nothing carries a `VERDICT: APPROVE`. Nothing is merged.

80 Footprint tests green.

## Review text (verbatim)

Request changes. The local eviction fix is sound, and the quoting bound is conservative. The replay barrier, authority ordering, and retained-memory accounting still have correctness defects.

Reviewed HEAD `1459300`, `origin/main..HEAD`, `6a94794..HEAD`, both review records, the gateway amendment, and interaction-design rev 7. I also inspected and exercised the companion reader.

Verification:

- Focused run against prebuilt classes: **21 passed; 32 failed during fixture construction** because the read-only sandbox prevented creating the seller-activity disk directory.
- Current view/coordinator/test sources compiled in memory: **16/16 view tests passed**.
- Companion reader suite: **22/22 passed**.
- Additional probes reproduced refused-record resurrection, ineffective completion control, mutable replay coverage, and malformed hello JSON.
- Randomized view probes found no retained-head/boundary violations. This was **not a clean Maven build**.

These aliases identify the files used below. Test references identify committed test methods; fixture-blocked tests are not claimed as passing.

| Alias | File |
|---|---|
| V | [FootprintStrikeView.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java) |
| S | [FeedGatewayService.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java) |
| C | [GatewayController.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewayController.java) |
| G | [GatewaySettings.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewaySettings.java) |
| VT | [FootprintStrikeViewTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintStrikeViewTest.java) |
| WT | [FootprintWiringTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintWiringTest.java) |
| CT | [FootprintBackfillControllerTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintBackfillControllerTest.java) |
| ST | [FootprintSeamTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintSeamTest.java) |

For Part A, the eight round-2 findings have these dispositions:

| Round-2 finding | Disposition | Evidence and test pin |
|---|---|---|
| 1. Unbounded revision/refusal metadata | **PARTIAL** | V:192,207,223,233,288 now charge logical metadata and enforce it with payload bytes. VT:193 pins 10,000 revisions, the logical budget, and symbols of 64/65 characters. But V:80 stores boxed keys, map nodes and separate arrays, not packed 40-byte entries. Actual retained storage remains undercounted. Finding 1 below. |
| 2. Equal-time eviction strands retained heads below the boundary | **CLOSED**, for the two view invariants | V:257–271 removes the whole bucket; V:180 rejects every subsequent record below the cutoff. VT:221 pins the reported two-strike case, re-admission rejection and a subsequent updating survivor. Reader invalidation remains a separate defect, finding 4. |
| 3. Incomplete bootstrap becomes completed NO DATA | **PARTIAL** | V:140,315,374 and C:189 publish loading. VT:243 pins a manually completed view. S:4391 nevertheless completes it from source-filtered or absent-partition barriers, and nothing reopens it for adoption/recovery. Finding 2. |
| 4. UNAVAILABLE does not invalidate connected readers | **PARTIAL** | V:190,212 and S:997 send controls; V:147 sends completion. VT:265 pins callback invocation. There is no executed gateway control-ordering test, and the actual ordering permits resurrection or overwrites completion. Finding 3. |
| 5. Quoted records break proxy sizing | **CLOSED**, for the arithmetic | V:431–455 implements the bound. The companion proxy uses escaped sizing on both routes and rejects ceilings admitting no page. VT:280 pins escaping; companion `FootprintProxiesTest`:155–172,238–255 pins sizing and forwarding. A near-ceiling gateway→proxy integration test remains absent. |
| 6. Inventory is confused with replay coverage | **PARTIAL** | Separate fields now exist at V:313–315 and C:184–189. VT:243 pins propagation of a supplied timestamp. S:4391 supplies a continually recomputed timestamp, not the actual seek boundary. Finding 5. |
| 7. Full-index pruning per evicted head | **PARTIAL** | V:275–277 moves pruning outside the eviction loop. However, `tombstones` increments at V:233 and never decreases when V:290 removes indexed tombstones. Full scans continue after the last indexed tombstone disappears. No committed traversal-count pin. Finding 7. |
| 8. Formatter per control character | **CLOSED** | V:442 writes hexadecimal escapes directly. VT:280 exhaustively checks control characters, round trip and worst-case expansion. |

The eviction proof is straightforward:

- With a cutoff `b`, admission requires `open >= b`.
- Evicting bucket `t` removes **all live heads** opening at `t`. The next minimum, if present, is strictly greater than `t`.
- The replacement cutoff is that minimum, or `t + 1` when empty. It never decreases.
- Updates cannot change identity opening times; refusal only removes heads.

Consequently, every retained head opens at or after the cutoff, and every evicted opening remains permanently inadmissible at every revision. My mixed-scope randomized probe found zero violations. At `EPOCH_MAX_MS`, the empty-view sentinel becomes `253402300800000`, without overflowing `long`; that out-of-domain sentinel still needs an explicit reader contract/test.

One wording correction remains: the boundary is a monotonic eviction cutoff, **not necessarily the current oldest retained opening after a later refusal**. V:42 and V:252 also incorrectly say it is “strictly above every retained head”; the implementation establishes the opposite inequality.

For quoting, each input UTF-8 byte contributes at most six output bytes; quotes contribute two, and a separator one. Thus `6N + 3` covers a quoted record plus separator, and the proxy’s `6N + 4` is conservative. Non-ASCII UTF-8 does not invalidate this inequality. The helper’s unchecked arbitrary-`long` multiplication is not a production overflow path: actual string byte-array lengths are `int` bounded. The page bound assumes the configured proxy ceiling covers the gateway ceiling.

The fold/read coverage table is:

| Clause | Disposition | Evidence / committed pin / remaining gap |
|---|---|---|
| Greatest revision; older after newer | **PARTIAL** | V:184–199; VT:35 and VT:193. Greatest-revision selection and revision-driven budget pressure are pinned. Explicit payload growth/shrink accounting and actual allocation accounting are not. |
| Identical bytes; collisions; later refusal | **CLOSED** for the view | V:180–190; VT:44,68,221 cover duplicates, superseded collisions, both arrival orders, tombstones and the former boundary bypass. Transport correctness is finding 3. |
| CHECKPOINT HWM, no episode, nonregression | **PARTIAL** | V:164–168,216–219; VT:120,162. Nullable/invalid dates and nonregression are pinned; the complete recovery-field/type matrix remains absent. |
| Latest opening/session/timeframe/order/cursor | **PARTIAL** | V:326–343; VT:78,90. Symbol/session/timeframe isolation and ordinary pagination are pinned; numeric strike digit-boundary/domain cases remain absent. |
| History ordering and exclusive cursor | **PARTIAL** | V:352–380; VT:90, CT:106. Nonexistent-but-valid cursor and actual gateway HTTP cursor round trip remain unpinned. |
| Both eviction budgets | **PARTIAL** | V:255–288; VT:131,193,221. Logical budgets and count-triggered equal-time eviction are pinned. Equal-time byte pressure across scopes and a real retained-memory bound are not. |
| Index/heads/byAge consistency | **PARTIAL** | V:226–299; VT:78,221. The broken live-head boundary invariant is fixed. No committed complete invariant checker; indexed-tombstone bookkeeping remains wrong. |
| Atomic snapshots; no lock during writes | **PARTIAL** | V:307,326,352; C:125–128,152–155. Individual snapshots release the lock before writing. No concurrent strike reader/writer or blocked-strike-writer test; mutation-to-emission ordering is not atomic. |
| Hello/latest/history agreement | **PARTIAL** | V:307–374; VT:90,120,243,265; CT:74. Field plumbing is pinned, but replay authority and live-reader agreement fail. |
| LOADING versus completed NO DATA | **STILL OPEN** | S:2771–2777,4311–4340,4391; VT:243 only calls `replay()` directly. No owning-partition bootstrap/adoption integration pin. |
| Shape and oversize | **PARTIAL** | V:152–172; VT:162,193. Symbol length is pinned. Exact UTF-8 ceiling/one-byte-excess and complete type/domain matrices remain absent; configured-symbol hello escaping regressed. |
| Fixed-width epoch ordering | **PARTIAL** | V:378–380,405–407; VT:177. Locale is pinned. Full-domain ordering and terminal-boundary semantics remain uncommitted. |

The service coverage table is:

| Clause | Disposition | Evidence / committed pin / remaining gap |
|---|---|---|
| Same flag; fifth topic bound unconditionally | **CLOSED** | S:815–832,2500–2507; WT:41,109 verify shared wiring and the constructed map. |
| Same gate for five topics | **PARTIAL** | S:855–857,940–943; WT:253,270 and ST:58. Fifth-topic rejection→later-admission through both consumer flows remains unexecuted. |
| Both consumers share admission | **PARTIAL** | S:914,1004,2912–2917; WT:70 source pin. No executed fifth-event consumer-loop seam. |
| Admit before broadcast; oversize reaches no socket | **PARTIAL** | S:923,1004–1008; WT:70 source pin. ST:179 still exercises four families. No strike sink-ordering/oversize delivery pin; authority ordering fails. |
| Seven-day cache policy | **PARTIAL** | S:2999–3012,6663–6665; WT:129 policy assertion. Actual fifth-topic seek/adoption and correct coverage publication remain unpinned. |
| Live END on bootstrap/retry/adoption | **PARTIAL** | Fifth membership at S:2507; ST:114 includes strike. ST:127 and ST:153 still omit it from retry/adoption fixtures. |
| Hello field; flag-off bytes | **PARTIAL** | S:826–832,11365–11371; WT:93,222. Normal hello/flag-off behavior is pinned; explicit flag-off strike-view-null and unusual configured-symbol tests are absent. |
| Authenticated per-session allowlist | **PARTIAL** | S:12643–12646; WT:87 checks evidence registration. ST:179 omits strike, and no executed authenticated control fan-out test exists. |
| Every startup metric cell; flag-off exception | **PARTIAL** | Existing five-event cells are pinned by WT:270 and flag-off by WT:93. The new control counter at S:1000 is never exported by S:1045. Finding 8. |
| Nonzero strike metrics and overlap identities | **PARTIAL** | S:919–923,1062–1080; CT:150 pins unavailable handling and counters. Full collision/refused/shape/oversize/cache/live/broadcast/eviction/locale sequence remains absent. |
| Existing-stream non-interference | **PARTIAL** | S:914–932 preserves existing branches. Shared heap exposure, consumer-thread scanning and malformed shared hello remain defects. No executed strike stress/non-interference pin. |

The route/settings coverage table is:

| Clause | Disposition | Evidence / committed pin / remaining gap |
|---|---|---|
| Binding→flag→auth→permit→validation→snapshot→write | **PARTIAL** | C:112–158; CT:130 pins busy precedence. WT:331 and ST:249 still exercise original routes, not strike binding and overlapping failures. |
| Flag/auth precedence | **PARTIAL** | C:204–224; CT:74,106 cover both flag-off routes and history authentication failure. Latest authentication and overlapping flag/auth/cursor cases remain absent. |
| Limit clamps | **PARTIAL** | C:126,153. CT:74,106 exercise small pages; no over-200/100 dataset or zero/negative-limit pin. |
| Permit release | **PARTIAL** | C:129–130,156–157; CT:106,130,150 pin several paths. Strike mid-write/flush failure and buffer refusal remain untested. |
| Verified 64 KiB streaming buffer | **PARTIAL** | C:168–200; CT:74 verifies decoded record text. Ignored/oversized buffer and blocked-writer tests still target the original writer. |
| Envelope | **PARTIAL** | C:182–198; CT:74; VT:90,243. String encoding and fields are pinned; replay-boundary truth is not. Escaped proxy sizing is fixed. |
| Requested-session validation | **PARTIAL** | C:124; CT:74 rejects a compact date. Impossible calendar date, unpadded and empty route parameters remain unpinned. |
| Setting names/defaults | **CLOSED** | G:342–367; WT:318 pins topic, budgets, refusal count, symbol and seek-back defaults. |
| Bounds/prefix/overrides | **PARTIAL** | G:1880–1934 uses existing helpers. No strike-specific override/prefix/clamp/malformed/overflow suite. Symbol configuration lacks validation and safe serialization. |

For completeness, the round-1 dispositions carried in round 2 now stand as follows:

| Round-1 item | Disposition | Evidence / pin |
|---|---|---|
| 1. Superseded collisions; evicted identities returning | **CLOSED at the view**, **PARTIAL end to end** | V:180–199,255–271; VT:44,221. Live forwarding can still reintroduce rejected evidence to readers. |
| 2. Symbol omitted from index | **CLOSED** | V:173,205,239,330,356; VT:78. |
| 3. Refused newest exposes older value | **CLOSED at the view** | V:233,334–338; VT:68. |
| 4. Timeframe/date/numeric-domain defects | **CLOSED for those defects** | V:163–172,410–419; VT:120,162. The new configured-symbol defect is separate. |
| 5. Refusal metadata and heap proof | **PARTIAL** | V:223,233–234,288; VT:150,193. Actual storage accounting remains wrong. |
| 6. History boundary | **PARTIAL** | Eviction invariants fixed at V:255–271; VT:221. Replay coverage remains incorrect at S:4391. |
| 7. Latest envelope session | **CLOSED** | V:343; VT:90. |
| 8. Locale/cursor defects | **CLOSED for implementation; PARTIAL coverage** | V:378–380,405–406; S:921,930 use `Locale.ROOT`; VT:177. Turkish metric-label pin remains absent. |
| 9. Fifth-stream conformance | **PARTIAL** | The missing integration and failure probes are itemized above. |

R21 remains sound for valid producer JSON by inspection: original record text is retained and quoted without adding evidence fields. However, VT:22 still supplies a fabricated `tag` series. There is no committed gateway test carrying complete producer-shaped unknown enums and nulls byte-exactly through **live, latest and history**.

For Part B, these are the findings, ordered by severity.

1. **[P1] The byte budget still does not bound actual retained memory.**

   **Evidence:** [V:80](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:80) uses `HashMap<Long, byte[]>`. [V:192](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:192) charges only 40 bytes per entry. Those entries additionally retain boxed `Long` objects, hash nodes, array headers and bucket-array capacity.

   [V:223](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:223) also does not account for the complete strings and structures created at V:202–206: symbol/date strings, scope/open/age keys, nested trees, nodes and backing tables. Tombstone charging at V:233 likewise does not cover its retained index structure. UTF-8 payload length is not always Java `String` storage size; one non-Latin-1 character can force a predominantly ASCII string into UTF-16 storage.

   Executed probe: **4,000 retained revisions**, `metadataBytes=160190`, `payloadBytes=459`, budget `200000`. The reported charge passes, but the ledger is an object graph, not 160,000 packed bytes. VT:193 checks the same accounting formula and therefore cannot establish the claimed memory bound.

   **Why it matters:** The former unbounded-growth defect has become bounded logical growth, but the advertised 64 MiB retained-memory limit and deployment accounting remain false.

   **Corrected code or test:** Use genuinely packed storage and charge allocated capacity, or implement a conservative bound covering every retained allocation under the enforced JVM layout. Include collection high-water capacities, Unicode strings and snapshot-held payloads in the appropriate resource budgets. Add an allocation/layout-backed `retainedStorageNeverExceedsConfiguredBudget` test; counters alone are insufficient.

2. **[P1] Replay completion can be declared without replaying the strike partition, and never reopens.**

   **Evidence:** [S:2771](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:2771) derives completion from `catchUpEndOffsets`. Its source-independent exceptions at [S:4322](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:4322) omit strike.

   An isolated execution of the current filtering method with an IBKR selection, strike end offset 100, and IBKR end offset 1 returned:

   ```text
   filtered bootstrap barriers={ib-0=1}
   ```

   Strike can still be at position zero when [S:4391](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:4391) declares completion. `events` comes from configured bindings, so an absent/withheld strike partition can also be declared complete. Later adoption/recovery does not reset the view; [V:145](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:145) only ever sets completion to true.

   **Why it matters:** An unfinished replay can still yield `loading:false`, an empty page and completed **NO DATA**. Subsequent cache hydration is silent, so the incorrect reader state can persist.

   **Corrected code or test:** Track a strike-specific replay generation and barriers for the actually admitted strike partition, independent of active source. Publish loading before bootstrap/adoption/recovery and complete only that generation’s barrier. Pin IBKR selection, absent-then-adopted strike, and consumer retry; none may become complete before strike’s own barrier is crossed.

3. **[P1] Control/evidence/hello ordering is unsafe; refused evidence can return and completion can become ineffective.**

   **Evidence:** A collision invokes its callback before `admit()` returns at [V:212](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:212). The service then forwards that same colliding record because [S:923](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:923) suppresses only oversize records.

   The companion reader starts a fresh fold on control at [strike-board.js:3347](/private/tmp/oe-web-fpstrike/src/app/web/assets/strike-board.js:3347), merges arriving evidence into it, and publishes it when REST completes. Executed sequence:

   ```text
   reader holds revision 2
   control(refused=1) starts fresh backfill
   colliding revision 0 arrives
   authoritative REST page returns no episode
   reader publishes DATA, revision 0
   ```

   Subsequent `REFUSED` revisions are also forwarded without another control.

   There is a second ordering hole at [S:1261](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:1261): an initial hello can capture `loading:true`, then completion control can enqueue before that stale hello. The reader applies the stale hello last. My probe remained **LOADING**, with retries queued but no new REST request: the loading retry path cannot refresh authority.

   **Why it matters:** A count-only authority notification is insufficient with this forwarding policy and ordering. Both false DATA and indefinite LOADING are reproducible.

   **Corrected code or test:** Serialize strike mutation, authority capture and enqueue ordering across both consumers and initial hello, or attach a monotonic authority generation that readers enforce. Suppress rejected evidence consistently, including delayed pre-refusal admissions, or transmit sufficient identity/revision authority to reject it. Pin both sequences through actual socket dispatch with controlled REST completion.

   The ordinary outbound queue does **not** silently coalesce this control: it is non-coalescable, and overflow/write failures close the socket ([OutboundChannel.java:118](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/OutboundChannel.java:118), :135, :174). Reconnect can recover that transport loss. The demonstrated loss is semantic: newer authority is overwritten or defeated by later evidence.

4. **[P1] Eviction changes authoritative results without invalidating connected readers.**

   **Evidence:** [V:255](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:255) returns a notification only for entering unavailable. Ordinary eviction moves the boundary and removes heads without notifying. Admission can even evict the record just inserted while returning `EPISODE` at V:209.

   Future records rejected as `EVICTED` still pass [S:923](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:923). The companion reader does not enforce the gateway boundary during folding. My probe applied an episode below a published boundary after an empty authoritative page and obtained **DATA**.

   **Why it matters:** The view satisfies its invariants while the visible reader violates them. Connected readers can retain or resurrect evidence that a reconnecting reader cannot obtain.

   **Corrected code or test:** Include retention-boundary changes in authority notifications, make admission report whether its identity survived enforcement, and enforce that authority during live folding. Add a test with two connected readers, cache-triggered eviction, and a later revision below the boundary; both must agree with REST.

5. **[P2] `replayBeginsAtMs` moves on every caught-up poll instead of identifying the replay window.**

   **Evidence:** The actual seek cutoff is calculated at [S:3001–3009](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:3001). [S:4391](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:4391) independently computes `now - seekBack`, and S:2971 calls it every caught-up poll. [V:143](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:143) overwrites the field unconditionally.

   **Why it matters:** The field advances during ordinary uptime without any new seek. It also changes without a control notification, so connected readers and new REST requests can report different coverage.

   **Corrected code or test:** Capture the actual seek boundary with its replay generation and keep it stable for that generation; distinguish requested coverage from a newer effective log start where necessary. Pin repeated caught-up polls and a long-running replay: the published timestamp must remain the captured boundary until a real replay transition occurs.

6. **[P2] The newly published configured symbol can corrupt the shared hello.**

   **Evidence:** [G:361](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewaySettings.java:361) accepts arbitrary configuration text. [V:309](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:309) appends it inside quotes without escaping.

   Executed: `scopeSymbol("ES\"X")` makes `helloField()` fail Jackson parsing. That fragment is inserted into the shared `cvd-hello` at S:11371.

   **Why it matters:** A malformed configuration value breaks the whole shared handshake, including existing streams. The record-symbol length check does not protect this separate configuration path.

   **Corrected code or test:** Serialize with `quoted(scopeSymbol)` or a JSON writer, and validate the configured scope against supported symbol constraints. Add a real settings→hello test for quotes, backslashes, control characters and excessive length.

7. **[P2] Pruned tombstones leave the scan guard permanently enabled.**

   **Evidence:** [V:233](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:233) increments `tombstones`; [V:290–299](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:290) removes indexed tombstones without decrementing it. The guard at V:277 therefore means “a refusal has ever happened.”

   My probe removed the only indexed tombstone through boundary advancement while retaining its refusal identity. Subsequent evictions still meet the full-scan guard.

   **Why it matters:** The multiplicative per-head scan is fixed, but one historic collision can leave every future eviction scanning the complete index under the view lock and on a Kafka consumer thread.

   **Corrected code or test:** Track indexed tombstones separately from incarnation refusal identities, decrement on pruning, or maintain an age-ordered tombstone index. Pin traversal counts after the last indexed tombstone is removed; do not use a timing-only assertion.

8. **[P3] The control broadcast counter is incremented but never exported.**

   **Evidence:** [S:1000](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:1000) increments `broadcast_total{event="es-footprint-strike-control"}`. [S:1045](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:1045) exports only the five evidence events. WT:270 consequently pins a set that excludes the new counter.

   **Why it matters:** Operators cannot observe whether the new authority-notification path has fired.

   **Corrected code or test:** Give broadcast metrics their own event domain including control, without adding control to Kafka topic/record domains. Pin the zero series and increments for refusal, unavailable and completion.

The requested bars are:

- **Institutional: FAIL.** Replay can falsely complete, and connected readers can display refused or evicted evidence despite authoritative REST omission.
- **Military: FAIL.** Authority ordering, adoption/recovery readiness and configured-symbol serialization remain unsafe; critical fifth-stream failure tests are missing.
- **NASA: FAIL.** The claimed retained-memory bound does not match the allocations, pruning still performs unnecessary locked scans, and full-session heap/container measurements remain unestablished. The deployment amendment now acknowledges the measurement gap correctly.

VERDICT: REQUEST_CHANGES
Replay authority, reader invalidation and retained-memory accounting remain incorrect despite the local eviction and quoting fixes.
