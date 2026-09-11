# ES Footprint strike stream — gateway final Codex review (2026-09-11)

Review of `main` at `079d42c` (the #179 squash `03c65006` plus the #180 campaign re-anchor). Verdict: **REQUEST_CHANGES** —
five fresh findings, plus the earlier-round tables it re-graded. Folded on `fix/footprint-strike-final-review`. Nothing
here is re-reviewed yet: no `VERDICT: APPROVE` exists for this change.

## Disposition

| # | Finding | Disposition | What changed | Pinning tests |
|---|---|---|---|---|
| 1 | **[P1]** Previously admitted evidence can be broadcast after its identity has been withdrawn (admit at S:1013, broadcast at S:1016; the cache consumer's refusal/eviction control could overtake it) | **FIXED** | Every strike mutation — admission from EITHER consumer, refusal, eviction that moves the boundary, failing closed, replay completing/reopening — decides its outcome and queues the frames it causes (the ADMITTED record's `es-footprint-strike` evidence when the live consumer forwards, then `es-footprint-strike-control` when the authority moved) under the ONE view lock; the queue is drained outside that lock, one drainer at a time, into the non-blocking socket channels, in order. `onFootprintLiveRecord` no longer broadcasts strike evidence itself. The per-socket hello is captured and queued the same way (`FootprintStrikeView.hello`). Only admitted evidence is forwarded (kept). | `FootprintStrikeViewTest.aRecordAdmittedBeforeARefusalIsDeliveredBeforeThatRefusalsControl_whicheverThreadDrains`, `…BeforeAnEvictionIsDeliveredBeforeThatEvictionsControl`, `…aHelloIsSequencedWithTheControls_noFrameCarriesAnAuthorityOlderThanOneDeliveredBeforeIt`; `FootprintStrikeDeliveryTest.aRecordAdmittedBeforeARefusalReachesEverySocketBeforeTheRefusalsControl` (two consumer threads, latch-parked live thread, REST read between the control and the live thread's resumption, two real socket channels), `…BeforeAnEvictionReachesEverySocketBeforeTheEvictionsControl`, `…aSocketNeverReceivesAHelloOlderThanAControlItAlreadyHolds` |
| 2 | **[P1]** Late adoption never joins the strike replay lifecycle; progress coupled to shared `live`; published cutoff fabricated after the seek | **FIXED** | Per-attempt `StrikeReplay`: `cacheHydrationSeek` (bootstrap AND adoption) seeks the cache window, captures the end offsets, and for strike partitions (re)opens the replay with the cutoff `seekToCacheWindow` actually used (it now returns its cutoffs; captured once, used for both). Completion is evaluated after each poll's records are applied, on every poll, outside `if (live)`. A retry is a new attempt and reopens a completed replay (authority change → control `loading:true`). | `FootprintStrikeDeliveryTest.anAbsentStrikeTopicAdoptedLaterJoinsTheReplayWithItsSeeksCutoff_andCompletesWithoutSharedReadiness`, `…aRetryReopensTheReplayWithItsOwnCutoffAndCompletesAgain` — both drive the PRODUCTION `runAssignedCacheConsumerOnce` loop (new consumer-factory seam) against a scripted broker: absent→adopted, `replayBeginsAtMs` == the timestamp handed to `offsetsForTimes` (the fake broker sleeps inside it, so a recomputed clock would differ), LOADING observed poll by poll until the last adopted record is applied, completion announced with all three records already folded, shared readiness never reached, retry reopen with its own cutoff, empty partition completes at bootstrap |
| 3 | **[P1]** Retained storage exceeds the configured byte budget (UTF-8 length charged, UTF-16 String retained; "40 bytes packed" / "every retained byte is charged" in the design) | **FIXED** (accounting); heap ceiling **NOT CLAIMED** | Payloads retained as the record's UTF-8 `byte[]` (digested from those same bytes) and charged the array's heap size; every retained string charged its compact-string storage (2 bytes/char once any char > U+00FF); per-node constants re-stated as estimates. Pages write straight from the array (`writeQuoted`, byte-identical to quoting the String). Wire bytes (`view_bytes`) are published apart from retained storage (`view_retained_bytes`, `view_metadata_bytes`). Design text replaced; no heap ceiling claimed — the full-session heap measurement G-R8 names is still owed. | `FootprintStrikeViewTest.retainedStorageIsWhatTheBudgetCharges_evenWhenOneCharacterIsWiderThanLatin1` (the reviewer's case: maxBytes 300,000, ~200 KB record + one `€`, checked against the array actually retained), `…theChargeTracksEveryRetainedArrayThroughGrowthShrinkEvictionAndRefusal` (600 random steps: charge == Σ retained arrays, wire bytes == Σ lengths), `…theRetainedUtf8IsQuotedByteForByteAsTheStringWouldBe` |
| 4 | **[P2]** Tests do not exercise the service-level fixes; strike/control absent from retry/adoption seams and fan-out; no byte-exact producer-shaped record | **FIXED** for the named cases; **PARTIAL** for the older obligations table (below) | Production-path tests listed under #1/#2; strike + control added to the live retry/adoption seam fixtures (and asserted not to touch the replay) and to the legacy fan-out test; auth-mode (per-session routing) fan-out of all five events with rejection suppression; a producer-shaped record (producer key order, nulls, non-Latin-1 + surrogate pair, escaped control chars, unknown nested and top-level fields) byte-exact through live, latest and history. | `FootprintSeamTest.theLiveBootstrapSeekLeavesFootprintPartitionsAtEndOnEveryRetry`, `…aLateAdoptedFootprintPartitionStartsAtEndOnTheLiveConsumer`, `…everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone` (now five events + control); `FootprintStrikeDeliveryTest.inAuthModeTheFifthEventAndItsControlReachEverySocket_andARejectedRecordReachesNone`, `…aProducerShapedRecordIsCarriedByteExactlyThroughLiveLatestAndHistory` |
| 5 | **[P3]** The amendment is not an accurate as-built description (page schema ≠ hello; boundary sentence; late-adoption claim) | **FIXED** | Amendment rewritten: hello/control schema and page schema specified SEPARATELY (pages carry no `symbol`/`hwm`/`unavailable`); boundary sentence now "strictly above evicted openings and at or below retained openings" (also in the class javadoc); lifecycle, ordering, incarnation, tombstones and memory paragraphs match the tested behaviour; deployment coupling with the web change stated. | — (prose) |

### The earlier-round items the review graded PARTIAL / STILL OPEN

| Item | Now |
|---|---|
| R1 #1 superseded collisions / evicted identities returning — delayed evidence could cross a refusal/eviction | FIXED by #1 |
| R1 #5, R2 #1, R3 #1 memory accounting | FIXED by #3 as an accounting bound; no heap ceiling claimed |
| R1 #6, R2 #3, R2 #6, R3 #2, R3 #5 replay coverage / barrier / cutoff | FIXED by #2 |
| R1 #9 fifth-stream conformance tests | PARTIAL — see the obligations table |
| R2 #4, R3 #3, R3 #4 ordering of control vs evidence vs hello | FIXED by #1 |
| R2 #5 companion proxy correction and a gateway→proxy ceiling test | NOT FIXED here — the proxy lives in the web repository; this gateway only publishes `quotedBoundBytes` |
| R2 resource/headroom finding (design claimed every retained byte charged) | Text FIXED; the `Xmx − H_peak` / `limit − W_peak` measurement remains OPEN (needs a full session with the flag on) |
| R3 additional: foreign symbol advances the named hello — committed regression missing | FIXED — `FootprintStrikeViewTest.aForeignSymbolNeverAdvancesTheNamedHello` |

### The "remaining obligations" table (finding 4)

| Obligation | Now |
|---|---|
| Payload growth/shrink accounting, complete index consistency, concurrent strike reads | PARTIAL — growth/shrink/eviction/refusal accounting invariant now pinned (`theChargeTracksEveryRetainedArray…`); concurrent hello reads under mutation pinned (`aHelloIsSequenced…`); a full index-consistency invariant and concurrent REST reads under mutation are NOT pinned |
| Full checkpoint/type/domain matrix, numeric strike boundaries, nonexistent valid cursors | PARTIAL — a nonexistent-but-valid history cursor is now pinned (`FootprintBackfillControllerTest.strikeRoutesClampLimitsAcceptANonexistentValidCursorAndReleaseThePermitOnAWriteFailure`); the full matrix is not |
| Strike HTTP binding/flag/auth overlaps, maximum/negative limits | PARTIAL — maximum and negative limits on both strike routes now pinned (same test); binding/flag/auth overlap matrix for the strike routes not added |
| Strike blocked writer, buffer refusal, mid-write/flush failure and permit release | PARTIAL — a mid-write failure on a strike page releasing its permit is pinned (same test); blocked writer and buffer refusal on the strike routes specifically are NOT |
| Settings override/prefix/clamp/malformed-value matrix | STILL OPEN |
| Complete nonzero strike/control metric sequence and Turkish labels | PARTIAL — nonzero strike evidence/control broadcast and live/cache drop counters are asserted in the delivery tests; the complete sequence and a Turkish-locale label test are not |
| Near-ceiling gateway→proxy response and shared-stream stress/headroom | STILL OPEN — needs the web proxy and a load run |

## Protocol v2 as implemented (gateway half)

- Hello field `cvd-hello.footprintStrike` and the body of every `es-footprint-strike-control` frame (same object):
  `{"authority":n,"incarnation":"<uuid>","symbol":"…","sessionDate":"YYYY-MM-DD"|null,"hwm":{"<tf>":n},"historyBeginsAtMs":n|null,"replayBeginsAtMs":n|null,"loading":bool,"refused":n,"unavailable":bool}`
- `latest` page: `{"sessionDate","historyBeginsAtMs","replayBeginsAtMs","loading","authority","incarnation","refused","tombstones":[{"strikeCents":n,"openBarStartMs":n}],"episodes":["<record>"],"nextCursor":n|null}`
- `history` page: the same without `tombstones`; `nextCursor` a string or null.
- `incarnation`: a random UUID fixed per gateway process (per strike view). `authority` is monotonic within it.
- Ordering: evidence and control frames are sequenced with the mutation that caused them; a hello is sequenced with the controls.
- Additive: old readers ignore the new fields. Deploys together with the options-edge web change for the same protocol.

## Do the new tests bite? (temporary mutants, all restored)

Each defect re-introduced alone into the fixed source, `mvn -B -o clean test` over the five strike-touching Footprint suites:

| Mutant | Went red |
|---|---|
| M1 evidence queued after the view lock (the reviewed race) | both view ordering tests + both socket ordering tests |
| M2 hello captured outside the lock | `aHelloIsSequencedWithTheControls…` (a stress test, so probabilistic by nature: red in 3 of 3 runs) |
| M3 adoption does not join the replay (the reviewed code) | `anAbsentStrikeTopicAdoptedLater…` |
| M4 cutoff recomputed from the clock (the reviewed code) | `anAbsentStrikeTopicAdoptedLater…`, `aRetryReopens…` |
| M5 progress only under shared `live` (the reviewed code) | `anAbsentStrikeTopicAdoptedLater…`, `aRetryReopens…` |
| M6 progress evaluated before the records are applied | `anAbsentStrikeTopicAdoptedLater…` |
| M7 a retry never reopens | `aRetryReopens…`, `aReplayThatRestartsReopensLOADING…` |
| M8 tombstones omitted | `latestNamesEveryStrikeWhoseNewestEpisodeIsRefused…` + both refusal-ordering tests |
| M9 strings charged 1 byte/char whatever their width | `retainedStorageIsWhatTheBudgetCharges…` |
| M10 a wide payload held at UTF-16 width | `retainedStorageIsWhatTheBudgetCharges…`, `theChargeTracksEveryRetainedArray…`, `aProducerShapedRecord…` |
| M11 the fifth event not allowlisted in auth mode | `inAuthModeTheFifthEvent…`, the legacy fan-out test, `deliveryClassesAreWiredAsDesigned` |
| M12 pages omit the incarnation | both strike route tests + the socket refusal test |

## Verification

All runs clean (`clean` every time), Java 21, offline Maven, on the code commit `9ff292a`:

- `mvn -B -o clean test -Dtest='Footprint*' -Dsurefire.failIfNoSpecifiedTests=false` — **97 run, 0 failures, 0 errors**
  (FootprintStrikeViewTest 30, FootprintWiringTest 16, FootprintStrikeDeliveryTest 7, FootprintTopicGateTest 7,
  FootprintSeamTest 9, FootprintViewsTest 14, FootprintBackfillControllerTest 14; before this change: 80), plus the
  context smoke test 1/0.
- `mvn -B -o clean test` (full) — **1177 run, 0 failures, 0 errors**, plus the context smoke test 1/0. The 11
  `FeedGatewayServiceTest` volPremium* failures expected from `origin/main` did NOT reproduce: `FeedGatewayServiceTest`
  is 259/0 on this branch AND on a detached `origin/main` (`079d42c`) checked at 03:02 CEST on 2026-09-11, so they
  appear to be clock/session-dependent. The branch and main behave identically there.

### Mutation campaign (the Jenkinsfile's "Footprint reverification" inputs)

- `G-R9.2` re-anchored in `scripts/footprint-campaign.spec.json`: its `old`/`new` now carry the changed admission line
  `footprintStrikeView.admit(json, "live".equals(consumer));`. The mutation's intent is unchanged: a bar/outcome
  counted only AFTER admission, while the strike and live-snapshot paths keep counting first. All 34 anchors resolve.
- Re-run per `scripts/footprint-mutate.py` against a CLEAN detached worktree at `9ff292a`
  (`git worktree add --detach`, removed afterwards). Baseline GREEN (`mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest`, 133 tests).
  Result: **33 KILLED, 1 SURVIVED** — the same survivor as before (`G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed`,
  recorded as inert). No status changed. Kill sets changed only by addition: `G-R6.2` is now also killed by
  `FootprintStrikeDeliveryTest.aSocketNeverReceivesAHelloOlderThanAControlItAlreadyHolds`, `G-R8a ceiling-strict` by
  `FootprintStrikeDeliveryTest.anAbsentStrikeTopicAdoptedLaterJoinsTheReplayWithItsSeeksCutoff_andCompletesWithoutSharedReadiness`.
- `ES-FOOTPRINT-CAMPAIGN.json` replaced by that record and §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md regenerated from it.
  No campaign mutation targets `FootprintStrikeView`: the strike fixes are pinned by the unit/production-path tests and the
  12 temporary mutants above, not by the recorded campaign.

### Jenkinsfile gates

Run on the committed tree exactly as the Jenkinsfile's `Footprint reverification` stage does (`scripts/footprint-reverify.sh`, then
`scripts/footprint-reqstate.sh --check`). GATE_RESULTS_PENDING

## Review text (verbatim)

**Request changes.** Main still has defects in strike replay lifecycle, live/control ordering, and retained-memory accounting.

Reviewed detached HEAD `079d42c5d74e6ba51ab6ee36ac0588c8b54c3789`, equal to `origin/main`, including squash `03c65006` and campaign re-anchor `079d42c`. No files changed.

Verification: **21/21 current-source view tests passed**, compiled in memory. Additional probes reproduced late-adoption loading failure and memory undercounting, and verified symbol-scoped hello metadata. A separate prebuilt run passed 27 tests; 32 failed during fixture construction because the read-only sandbox prevents creating the seller-activity directory. This was **not a clean Maven build or production verification**.

For compact file:line references:

| Alias | Current-tree file |
|---|---|
| V | [FootprintStrikeView.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java) |
| S | [FeedGatewayService.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java) |
| C | [GatewayController.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewayController.java) |
| G | [GatewaySettings.java](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewaySettings.java) |
| VT | [FootprintStrikeViewTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintStrikeViewTest.java) |
| WT | [FootprintWiringTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintWiringTest.java) |
| ST | [FootprintSeamTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintSeamTest.java) |
| CT | [FootprintBackfillControllerTest.java](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintBackfillControllerTest.java) |
| D | [ES-FOOTPRINT-GATEWAY-DESIGN.md](/private/tmp/oe-gw-fpstrike/ES-FOOTPRINT-GATEWAY-DESIGN.md) |

The earlier findings stand as follows. “Closed” applies to the stated defect; it does not certify missing integration coverage.

| Round 1 finding | Disposition | Current evidence |
|---|---|---|
| 1. Superseded collisions; evicted identities returning | **PARTIAL** | View fixed: V:221,225,234,313; VT:44,221. Delayed admitted evidence can still cross a subsequent refusal/eviction—finding 1 below. |
| 2. Symbol omitted from index | **CLOSED** | Identity, index, removal and reads consistently include symbol: V:214,247,291,393,419; VT:78. |
| 3. Refused newest exposes an older episode | **CLOSED** | Tombstone retained at V:278; latest selects newest before checking its head at V:397–401; VT:68. |
| 4. Unsafe timeframe/date admission and numeric ceiling | **CLOSED** | V:204–213 validates timeframe/date and separates count from epoch domains; V:475–484; VT:120,162. |
| 5. Refusal metadata and memory bound | **PARTIAL** | Count cap and fail-closed behavior exist at V:285–286; accounting at V:275,345. Actual storage remains undercounted—finding 3. |
| 6. Unreliable history boundary | **PARTIAL** | Retention invariant fixed at V:313–324,446; VT:221. Replay coverage remains incorrectly captured and incompletely managed—finding 2. |
| 7. Latest envelope names another session | **CLOSED** | V:406 passes the requested session; C:183 writes it; VT:99–104. |
| 8. Locale-dependent keys/labels and impossible cursors | **CLOSED** | V:441–443,470–471; S:924,938; VT:177. |
| 9. Fifth-stream conformance tests | **PARTIAL** | CT:130,150 and ST:114 add useful cases. Critical strike consumer, ordering, fan-out and failure tests remain absent—finding 4. |

| Round 2 finding | Disposition | Current evidence |
|---|---|---|
| 1. Unbounded revision/refusal metadata | **PARTIAL** | Revision and identity charges enforced at V:234,249,275,345; VT:193. Logical growth is bounded; heap accounting is not conservative. |
| 2. Equal-time eviction freezes surviving heads | **CLOSED** | Entire opening-time bucket removed at V:313–320; boundary established at V:323–324; VT:221. |
| 3. Incomplete bootstrap becomes completed NO DATA | **PARTIAL** | Initial strike barrier now checked at S:4419–4430. Adoption is absent from that barrier and lifecycle. |
| 4. UNAVAILABLE does not invalidate connected readers | **PARTIAL** | Controls emitted at S:1005–1008 and V:256. Ordering against previously admitted evidence remains unsafe. |
| 5. Quoted records break proxy sizing | **PARTIAL within this review scope** | Gateway bound and escaping are correct at V:496–520; VT:362. The companion proxy correction and gateway→proxy ceiling test are not established by this main tree. |
| 6. Inventory confused with replay coverage | **PARTIAL** | Separate fields at V:376–378 and C:184–188; actual seek timestamp is not propagated—S:3018 versus S:4410. |
| 7. Full-index pruning once per evicted head | **CLOSED** | Pruning moved outside the eviction loop at V:328–330; indexed count decremented at V:354; VT:351. |
| 8. Formatter called per control character | **CLOSED** | Direct hexadecimal escaping at V:507; exhaustive round-trip test at VT:362. |

The additional Round 2 resource/headroom finding is **STILL OPEN**: D:309–315 still claims every retained byte is charged while admitting deployment inequalities are unestablished. The additional live-symbol-scoping finding is **CLOSED for hello/control** at V:372 and S:1007; the page-envelope documentation remains inaccurate.

| Round 3 finding | Disposition | Current evidence |
|---|---|---|
| 1. Packed-byte accounting applied to an object graph | **PARTIAL** | Constants increased at V:102,108, but UTF-8 payload charging remains at V:195,249. Executed counterexample below. |
| 2. Wrong replay barrier; replay never reopens | **PARTIAL** | Correct initial partition check at S:4421–4428. Sole start call is S:2785; adoption at S:2810–2838 never updates it. |
| 3. Control/evidence/hello ordering | **PARTIAL** | Generation appears in hello/pages at V:371 and C:190; rejected records suppressed at S:931. Evidence remains unversioned and admission/enqueue remain separate at S:1013–1016. |
| 4. Eviction does not invalidate; self-eviction reports admitted | **PARTIAL end to end** | Local fixes correct at V:241,253,341; VT:302. Delayed pre-eviction evidence can still be emitted. |
| 5. Replay timestamp moves every caught-up poll | **PARTIAL** | V:175 stabilizes it, but S:4410 fabricates the initial timestamp after the actual seek. |
| 6. Configured symbol corrupts shared hello | **CLOSED** | Validation at V:153–157; escaping at V:372; VT:342. |
| 7. Pruned tombstones keep scan guard enabled | **CLOSED** | V:354 decrements the indexed count; VT:351 pins zero after pruning. |
| 8. Control broadcast counter not exported | **CLOSED** | Separate broadcast domain at S:897–898, export at S:1055, increment at S:1008; startup series pinned by WT:270. |
| Additional: foreign symbol advances named hello HWM/session | **CLOSED** | V:268 filters both metadata updates. Current-source probe preserved ES session/HWM after a newer NQ checkpoint. A committed regression test is still missing. |

The fresh adversarial findings, most severe first:

1. **[P1] Previously admitted evidence can be broadcast after its identity has been withdrawn.**

   **Evidence:** [S:1013](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:1013) admits the record, then separately quotes and broadcasts it at S:1016. Between those operations, the cache consumer can refuse the same identity at V:228 and broadcast the newer control at S:1007. Neither the `Admission` record at V:67 nor the evidence payload at S:1016 contains its admission authority.

   The permitted interleaving is:

   ```text
   Live:  admit A → ADMITTED; pause
   Cache: admit conflicting B → refuse identity; broadcast authority 1
   Live:  resume → broadcast A
   REST:  identity absent
   ```

   The current-source probe confirmed the saved admission remains `ADMITTED` while authority is 1 and latest returns zero records. The outgoing-order defect follows from the service code; I did not execute a full socket reproduction.

   **Why it matters:** Rejecting the currently colliding record fixes only one ordering. A reader rebuilding after the control can still accept delayed evidence that REST has withdrawn. Eviction has the same race.

   **Corrected code or test:** Atomically sequence strike mutations and their outbound evidence/control entries, then drain that ordered queue outside the view lock. Alternatively, attach the captured authority to evidence and enforce it downstream. Merely rechecking before broadcast leaves another race. Add a latch-controlled two-consumer/socket test for both refusal and eviction, including REST completion between control and delayed evidence.

2. **[P1] Late adoption never joins the strike replay lifecycle.**

   **Evidence:** [S:2810](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:2810) seeks newly adopted partitions and captures `addedEndOffsets`. S:2838 registers generic bootstrap entries, but neither calls `noteFootprintStrikeReplayStart` nor updates the initial `bootstrapEndOffsets` subsequently passed at S:2988.

   The helper at S:4423 returns immediately when that initial map contains no strike partition. An isolated execution of the current helper bodies reproduced:

   ```text
   Late-adopted strike position = end offset
   loading = true
   replayBeginsAtMs = null
   ```

   Initial bootstrap correctly remained loading before its barrier and completed at the barrier.

   Additionally, progress remains inside `if (live)` at S:2981, coupling strike completion to shared readiness. The advertised seek timestamp is computed at S:4410, after the actual timestamp calculation at S:3018–3026 and intervening broker calls.

   **Why it matters:** The explicitly supported absent-at-startup/adopt-later path can remain LOADING indefinitely despite complete hydration. The published coverage timestamp does not identify the seek actually performed.

   **Corrected code or test:** Maintain explicit strike replay state covering bootstrap and adopted partitions. Capture the cutoff once, use it for both seeking and publication, and evaluate strike barriers after record application independently of shared `live`. Reopen before retry/adoption hydration. Test absent→adopted, source-independent completion, retry, and exact cutoff propagation through the production consumer orchestration.

3. **[P1] Retained storage still exceeds the configured byte budget.**

   **Evidence:** [V:195](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:195) charges UTF-8 length while V:245 retains a Java `String`. Increased metadata constants do not cover a predominantly ASCII string switching to UTF-16 storage because of one non-Latin-1 character.

   Executed against current source with a valid admitted JSON record:

   ```text
   maxBytes                    300,000
   payload + metadata charge   201,157
   retained payload byte[]     400,906
   admission                   ADMITTED
   ```

   The backing array alone exceeds the budget. D:290–292 and D:309–310 still claim every retained byte is charged; they even retain the obsolete “40 bytes … packed” description.

   **Why it matters:** Operators cannot use the reported accounting total as the promised retained-memory ceiling. This shares heap with existing streams. Calling it an accounting bound in V:41 does not establish conservative accounting or repair the contradictory deployment contract.

   **Corrected code or test:** Retain bounded UTF-8 storage, or separately charge conservatively for actual string storage and collection capacities. Keep wire-byte metrics distinct from retained-storage accounting. Add a storage-backed Unicode regression and capacity tests; require the full-session heap/working-set evidence named by D:312–315 before claiming deployment headroom.

4. **[P2] The tests still do not exercise the unreviewed service-level fixes.**

   **Evidence:** [VT:321](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintStrikeViewTest.java:321) manually invokes `replayRestarted`; it cannot detect the missing service call. WT:70 checks source ordering, while ST:179 exercises four event families and omits strike/control. Strike is included in the initial END-seek test at ST:114, but omitted from retry/adoption fixtures at ST:127 and ST:153.

   **Why it matters:** The green 21-test view suite coexists with the concrete replay and ordering defects above. The campaign re-anchor supplies no independent protection for them.

   **Corrected code or test:** Add the production-path tests specified in findings 1–2, plus fifth-event authenticated fan-out and rejection suppression. Carry complete producer-shaped records, unknown values and nulls byte-exactly through live/latest/history.

   Remaining earlier-round coverage obligations are also unresolved:

   | Obligation | Status and evidence |
   |---|---|
   | Payload growth/shrink accounting, complete index consistency, concurrent strike reads | **PARTIAL** — VT:35,193,221 cover narrower cases; no complete invariant/concurrency test. |
   | Full checkpoint/type/domain matrix, numeric strike boundaries, nonexistent valid cursors | **PARTIAL** — VT:90,120,162,177 cover selected cases. |
   | Strike HTTP binding/flag/auth overlaps, maximum/negative limits | **PARTIAL** — CT:74,106,130; ST:249 remains an original-route test. |
   | Strike blocked writer, buffer refusal, mid-write/flush failure and permit release | **STILL OPEN** — production writer at C:166–201; current failure tests do not establish these strike cases. |
   | Settings override/prefix/clamp/malformed-value matrix | **STILL OPEN** — WT:335 verifies defaults; getters at G:342–367 use shared helpers. |
   | Complete nonzero strike/control metric sequence and Turkish labels | **PARTIAL** — startup domain pinned; full sequence absent. |
   | Near-ceiling gateway→proxy response and shared-stream stress/headroom | **STILL OPEN** — quoting unit tests and accounting counters do not establish these. |

5. **[P3] The strike amendment is not an accurate as-built protocol description.**

   **Evidence:** [D:319](/private/tmp/oe-gw-fpstrike/ES-FOOTPRINT-GATEWAY-DESIGN.md:319) says the hello’s fields also ride every page. `Page` at V:89 has no symbol or HWM; C:182–199 emits neither. D:286 says the boundary is strictly above every **retained** head, contradicting the correct implementation and invariant at V:321–324. D:332 claims late adoption reopens replay, which finding 2 disproves.

   **Why it matters:** Consumers and operators cannot implement or audit against this amendment reliably.

   **Corrected code or test:** Specify hello/control and page schemas separately, or implement the promised fields from the same snapshot. Correct the boundary sentence to “strictly above evicted openings and at or below retained openings.” Replace the obsolete memory paragraph and make lifecycle claims match tested behavior.

The campaign re-anchor itself is **CLOSED for anchor/provenance consistency**. Its 34 records match current source hashes; the recorded baseline has no `src`/`pom.xml` difference from the feature squash. The read-only reconciliation generator exited successfully and reproduced the committed table: **33 killed, one explicitly recorded survivor**. The two re-anchored mutations still target the original counter semantics. None targets `FootprintStrikeView`. I did not rerun the mutation campaign, which requires writes; these results certify record consistency, not fresh mutation execution or strike conformance.

The requested bars are:

- **Institutional: FAIL.** Authoritative REST and live delivery can disagree after withdrawal; late adoption does not complete reliably.
- **Military: FAIL.** Recovery/adoption and concurrent authority publication remain unsafe, with the critical production-path regressions untested.
- **NASA: FAIL.** A measured backing-array counterexample disproves conservative memory accounting; full-session resource and shared-stream performance evidence is not established by the reviewed artifacts.

VERDICT: REQUEST_CHANGES
Main still permits withdrawn evidence after control, leaves late-adopted strike replay loading indefinitely, and understates retained storage.
