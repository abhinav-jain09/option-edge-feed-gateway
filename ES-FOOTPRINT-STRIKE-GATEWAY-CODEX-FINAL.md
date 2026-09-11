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
`scripts/footprint-reqstate.sh --check`). On `6bc3186` (the commit holding this record), both GREEN:
`footprint-reverify.sh` — baseline green, "the record reproduces: 34 mutations, same claim and same outcome for every
one" (33 KILLED, 1 SURVIVED); `footprint-reqstate.sh --check` — "§2a matches the campaign record". The same two
commands were re-run on the branch head that adds this paragraph (documentation only); the PR states that result.

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

## Re-review (round 2) — Codex re-review of `c18c618` (2026-09-11)

Verdict: **REQUEST_CHANGES**, three findings (verbatim below). Folded on `fix/footprint-strike-gw-r2` and pushed to
`fix/footprint-strike-final-review` (PR #182) as a fast-forward of `c18c618`. Not re-reviewed yet: no `VERDICT: APPROVE`
exists for this change.

### Disposition

| # | Finding | Disposition | What changed | Pinning tests |
|---|---|---|---|---|
| 1 | **[P1]** A slow socket can stall both strike consumers and healthy sockets (the drainer holds `drainLock` through fan-out; `OutboundChannel` closes an overflowing session synchronously, and that close can block behind the socket's outstanding write) | **FIXED** | `OutboundChannel.close` now only marks the channel closed, drops its queue and counts; the session close and then `onClose` run on a teardown executor, never on the thread that detected the breach. The default (`TEARDOWN_ON_OWN_THREAD`) runs each teardown on its own daemon thread `options-edge-ws-closer`: at most one per channel (the close is exactly-once by CAS), ending when the session close returns; an executor that rejects falls back to the same, never to inline. Order inside the teardown: session close, then `onClose` — so until the close returns the channel stays registered and CLOSED, every enqueue to it is refused at once, and no sender can find "no channel" and fall back to `enqueueOutbound`'s direct `session.sendMessage`. This is the rule for every stream the gateway serves and for all three breach paths (overflow on an enqueuer, write error on a writer, deadline on the watchdog — which could previously block the shared batch executor the same way). Kept for every user: refused enqueue, dropped queue, `disconnectedSlow`/`writeError`/`droppedOnClose` metrics (still synchronous), `onClose` exactly once, the socket always closed. Changed: the session close and `onClose` are no longer complete when `enqueue`/`enforceWriteDeadline` returns (`OutboundChannelTest.writeDeadlineForceClosesAStuckSend` now verifies the close with a timeout). No thread or permit leak: nothing is acquired per close except its one thread, which exits when the close returns — bounded by the container's blocking-send timeout even when the write it waits behind never completes; no writer-pool thread is used for teardown. | `FootprintStrikeDeliveryTest.aSlowSocketWhoseCloseBlocksStallsNeitherConsumerNorAnyHealthySocket` (the reviewer's scenario on the production path with the REAL writer pool: a socket whose strike send blocks and whose `close()` blocks on a latch; 80 live admissions overflow it on the drainer; the cache consumer's collision runs while that close is still blocked; asserts both consumer threads finish, the close is still blocked and ran on `options-edge-ws-closer`, both healthy sockets receive all 80 records then the refusal's control in order, the view's queue is empty, and after release `close()` ran exactly once), `OutboundChannelTest.anOverflowNeverWaitsForABlockedSessionClose_andTheTeardownStillHappensExactlyOnce` |
| 2 | **[P1]** Replacing retained evidence does not advance authority or notify readers (a cache-only replacement queues neither evidence nor control; rev 0 → rev 1 left authority 1 → 1 with zero frames) | **FIXED — readers are notified, in order; the notification is the evidence, not an authority bump (a deliberate departure from the suggested fix, reasoned below)** | Every change to what the fold retains now reaches the readers. ADDITIVE changes — a new episode or a newer revision replacing a head, which covers a change of which episode is latest through a newer opening — are queued, under the view lock with the mutation, as the admitted record's `es-footprint-strike` evidence by WHICHEVER consumer made the change: the live consumer at any time, the cache consumer once its replay is complete (the reviewed gap: the live consumer re-seeks END on a reconnect, the continuing cache consumer folds the skipped update after completion). An admission that changed nothing (a redelivery, the second consumer's copy, an older revision) queues nothing, so a record reaches a socket once. While the replay is incomplete the cache consumer queues no evidence: every reader is LOADING and the completion is an authority change after which each re-walks `latest`. INVALIDATING changes (refusal, boundary-moving eviction, dropped eviction marker, failing closed, replay completion/reopen) advance the authority and queue a control after that evidence, as before. | `FootprintStrikeViewTest.aChangeTheCacheConsumerFoldsAfterTheReplayCompletedReachesTheReaders_inOrder` (the reviewer's rev 0 → rev 1 from the cache consumer after completion: REST serves rev 1 AND an evidence frame with those exact bytes follows the completion control; the live copy, a redelivery and an older revision queue nothing; a newer opening from the cache consumer is forwarded; a cache-consumer collision still advances the authority after them; a reopened replay suppresses cache evidence until it completes again), `FootprintStrikeDeliveryTest.anUpdateOnlyTheCacheConsumerFoldedAfterTheReplayCompletedReachesEverySocket` (the production scenario through the service: both sockets get control then rev 1 byte-exact; REST agrees; the live copy adds no frame; `broadcast_total` strike 1, control 1) |
| 3 | **[P2]** Evicted newest episodes never produce the required tombstones (maxEpisodes=1: 680000/100 then 681000/200 → tombstones `[]`) | **FIXED** | Evicting a strike's NEWEST episode in its session keeps its index entry, dead, as an EVICTION MARKER, so `latest` names it in `tombstones` exactly like a refused newest episode. A marker exists only while it is its session's newest entry: a genuinely newer opening retires it; entries below it are dead and unreachable and leave when it is made (a refused tombstone included; the refusal ledger keeps the identity). A refused newest episode the boundary has passed is now KEPT and named (it is still its session's newest entry) — the whole-index prune that dropped it, and its round-3 #7 guard, are gone; nothing walks the index. Bound: `GATEWAY_ES_FOOTPRINT_STRIKE_MAX_REFUSED_IDENTITIES` markers (the refusal ledger's bound), each charged to the byte budget (256 B of nodes plus the five strings it references — always less than the head it replaces). Under the byte budget alone markers are dropped oldest first before any live head; past their count bound the oldest go. **A dropped marker** advances the authority (once per mutation) and queues a control: readers discard what they held and re-walk; that strike then has no row and no tombstone — NO DATA, the same chip the marker gave — and never an older episode, because every marker opened below the boundary, so no retained head of that strike and session opens before it and none can be admitted. New gauges `gateway_footprint_strike_eviction_markers`, `gateway_footprint_strike_eviction_marker_drops_total`. Design §G-R7/G-R8 rows and the amendment's "Eviction markers" bullet state the exact semantics. | `FootprintStrikeViewTest.anEvictedNewestEpisodeIsNamedInTombstones_theReviewersCase` (the reviewer's case, plus the marker's exact charge, EVICTED on return, retirement by a newer opening, per-session/per-timeframe scope), `…theMarkerLedgerIsBounded_forgettingAMarkerIsAnAuthorityChange_andNoOlderEpisodeTakesItsPlace` (count bound, oldest dropped, no older episode admitted or served; byte-budget priority with the live head kept, the exact charge leaving, the boundary unmoved and the authority advanced by the drop alone), `…aRefusedNewestEpisodeBehindTheBoundaryIsStillNamed_andAnUnreachableOneLeavesWithItsSessionsNewestHead` (replaces `prunedTombstonesStopCountingAsIndexed`), `FootprintStrikeDeliveryTest.latestNamesAnEvictedNewestEpisodeOnTheWire` (the reviewer's case through the REST controller: `tombstones:[{"strikeCents":680000,"openBarStartMs":100}]`), `FootprintWiringTest` exact metrics contract (the two new series) |

### Why finding 2 is not fixed with an authority per replacement

The review suggested "advance authority and queue control when evidence is replaced". The reader this protocol
serves — options-edge `strike-board.js` on `fix/footprint-strike-final-review` (`b9842f63`), the web half of PR #729 —
treats EVERY newer authority as an invalidation: `applyControl` → `applyAuthority` → `backfill()` releases the published
fold (`m.fold = freshFold(…)`, "released: nothing displays it while this walk runs"), puts every chip in LOADING and
re-walks `latest` (`strike-board.js:3481-3494`, `:3565-3606`); a page that reveals a newer authority restarts the walk
with backoff (`:3518-3528`). In steady state BOTH consumers fold every strike record, and whichever folds it first makes
the change; an authority per change would therefore fire on most live updates and keep the board LOADING through the
session. Evidence needs no invalidation: the reader folds it by the relay's own rule — greatest revision per identity,
newest opening per strike (`fold`, `:3405-3437`) — so it converges whether the evidence or a page arrives first. The only
state in which a change is not sent as evidence (the cache consumer while the replay is incomplete) is covered by the
completion, which IS an authority change. So the requested test assertion "authority advances" for the cache rev 0 →
rev 1 case is replaced by "the change's evidence reaches every socket, in order, and the authority does not move"; every
invalidating change still advances it.

### Do the new tests bite? (each fix reverted alone, sources restored, sha256 proven)

Each fix was reverted ALONE in the working tree (the exact pre-fix line restored), its tests run with
`mvn -B -o clean test -Dtest=…`, and the source copied back; the sha256 of `OutboundChannel.java` and
`FootprintStrikeView.java` was taken before and after every pass and was identical (`RESTORED: sha256 identical`,
`OutboundChannel.java` 55e3ec8a2f5f…, `FootprintStrikeView.java` 2828362fa483… — the committed sources).

| Revert | Went red (first failing assertion) |
|---|---|
| #1 teardown inline again (`teardown.run()` for `closers.execute(teardown)`) | `FootprintStrikeDeliveryTest.aSlowSocketWhoseCloseBlocks…:557` "the live consumer is not waiting inside the slow socket's close" (the live thread was still inside the blocked close 5 s later); `OutboundChannelTest.anOverflowNeverWaitsForABlockedSessionClose…:208` "…and returns without waiting for the close" (the overflowing enqueue took the 10 s the close was held) |
| #2 evidence only from the live consumer (`if (forward && outcome.reason() == Reason.ADMITTED)`, the reviewed line) | `FootprintStrikeViewTest.aChangeTheCacheConsumerFolds…:661` expected `[CONTROL, EVIDENCE]` but was `[CONTROL]` — the reviewer's "zero frames"; `FootprintStrikeDeliveryTest.anUpdateOnlyTheCacheConsumerFolded…:598` expected `[es-footprint-strike-control, es-footprint-strike]` but was `[es-footprint-strike-control]` |
| #3 an evicted head's index entry always removed (the reviewed behaviour) | `FootprintStrikeViewTest.anEvictedNewestEpisodeIsNamedInTombstones_theReviewersCase:717` expected `[Tombstone[strikeCents=680000, openBarStartMs=100]]` but was `[]` — the reviewer's reproduction; `…theMarkerLedgerIsBounded…:745` (0 markers); `…aRefusedNewestEpisodeBehindTheBoundaryIsStillNamed…:376`; `FootprintStrikeDeliveryTest.latestNamesAnEvictedNewestEpisodeOnTheWire:627` expected `[{"strikeCents":680000,"openBarStartMs":100}]` but was `[]` |

The campaign below re-breaks each clause again, separately, and records which named tests caught it.

### Mutation campaign

- Nine clauses added to `scripts/footprint-campaign.spec.json` for the new normative behaviour, each quoting the as-built
  sentence now appended to its requirement row in ES-FOOTPRINT-GATEWAY-DESIGN.md §2 (the amendment sits after the
  generated block, so it cannot be quoted): `G-R10.3` teardown never on the enqueuer (`closers.execute(teardown)` →
  `teardown.run()`); `G-R3.4` cache-consumer change after completion forwarded; `G-R3.5` a no-op admission queues
  nothing; `G-R3.6` the cache consumer queues no evidence while LOADING (three mutations of the one evidence line);
  `G-R7.1` an evicted newest episode leaves a marker; `G-R7.2` a newer opening retires it; `G-R8.1` the marker bound;
  `G-R8.2` markers before live heads under the byte budget; `G-R8.3` a dropped marker is an authority change. No
  existing clause needed re-anchoring: every anchor still resolves with its recorded occurrence count.
- Re-run per `scripts/footprint-mutate.py` in a CLEAN detached worktree at the code commit `6a300d5`
  (`git worktree add --detach`, clean before and after, removed afterwards). Baseline GREEN
  (`mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest`). Result: **42 KILLED, 1 SURVIVED of 43** — the same recorded
  inert survivor as before (`G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed`). All nine new clauses KILLED,
  by assertion failures in the named strike tests (`G-R10.3` by `aSlowSocketWhoseCloseBlocks…`; `G-R3.4`/`G-R3.5` by
  the cache-change view and delivery tests; `G-R3.6` additionally by the ordering tests, whose cache-consumer admissions
  would otherwise have sent evidence; `G-R7.1` by the four marker tests; `G-R8.2` also by
  `theBudgetsEvictTheOldestIdentities…`). For the 34 existing clauses: no status changed and no kill set changed.
- `ES-FOOTPRINT-CAMPAIGN.json` replaced by that record (every row names `6a300d5`) and §2a regenerated from it with
  `scripts/footprint-reqstate.sh` (G-R3 3→6, G-R7 5→7, G-R8 5→8, G-R10 2→3 probes; 43 mutations, 42 killed, 1
  surviving); `scripts/footprint-reqstate.sh --check` → "§2a matches the campaign record".
- Unlike the first round, the campaign now probes `FootprintStrikeView` and `OutboundChannel` directly.

### Verification

All clean builds, Java 21, offline Maven.

- Code commit `6a300d5`, before committing: `mvn -B -o clean test` (full) — **1184 run, 0 failures, 0 errors** (1177 before
  this round; +7: `FootprintStrikeViewTest` 30→33, `FootprintStrikeDeliveryTest` 7→10, `OutboundChannelTest` 5→6),
  plus the context smoke test 1/0. `FeedGatewayServiceTest` 259/0 — the volPremium* clock-dependent failures did not
  occur, so there was nothing to compare against `origin/main`.
- `mvn -B -o clean test -Dtest='Footprint*,OutboundChannelTest,CvdSpxLevelsWiringTest'` — 145/0.
- The three Jenkinsfile gates (`mvn -B -o clean test`, `scripts/footprint-reverify.sh`,
  `scripts/footprint-reqstate.sh --check`) were run on the commit that adds this record, with a clean tree before and
  after; their exit codes are stated in the PR description rather than here, so recording them does not move the head
  they were run on.
- Not done in this round: the earlier "remaining obligations" table is unchanged; no heap measurement; no Codex
  re-review of these fixes.

### Review text (verbatim)

1. [P1] A slow socket can stall both strike consumers and healthy sockets. FootprintStrikeView.java:364 holds drainLock throughout fan-out. However, OutboundChannel.java:137 handles overflow by synchronously calling session.close(). That close can block behind an outstanding socket write. Reproduced with the committed view and channel: the live consumer waits inside close, the cache consumer blocks acquiring drainLock, and its refusal control remains queued while healthy sockets wait. The watchdog skips this channel because it is already marked closed. Make overflow teardown nonblocking for the drainer; the existing delivery tests do not exercise blocked closes.

2. [P1] Replacing retained evidence does not advance authority or notify readers. FootprintStrikeView.java:328 replaces a head's revision/payload without incrementing authority. Consequently, a cache-only replacement queues neither evidence nor control. Reproduced: revision 0 → revision 1 changed REST's payload while authority remained 1 → 1, with zero frames emitted. Production scenario: the live Kafka consumer misses an update during reconnect and seeks to END; the continuing cache consumer folds that update after replay completion. Connected browsers retain the superseded evidence until another authority event occurs. Advance authority and queue control when evidence is replaced, including changes to which episode is latest.

3. [P2] Evicted newest episodes never produce the required tombstones. FootprintStrikeView.java:411 removes the episode's index entry completely; latest can only construct tombstones from entries remaining in that index. Reproduced with maxEpisodes=1: admit strike 680000/open 100, then strike 681000/open 200. Latest returns the second record, boundary 200, and tombstones: [], omitting {strikeCents:680000,openBarStartMs:100}. This violates the stated v2 eviction contract and leaves a browser relying on those tombstones without its explicit invalidation. Retain bounded newest-episode eviction markers; update the refusal-only documentation and tests accordingly.

## Re-review (round 3) — Codex re-review of `15ac5ca` (2026-09-11)

The re-review accepted every earlier fix, the additive-evidence design for round-2 #2 included, and raised exactly one
finding (verbatim below). Folded on `fix/footprint-strike-gw-r3` and pushed to `fix/footprint-strike-final-review`
(PR #182) as a fast-forward of `15ac5ca`. Not re-reviewed yet: no `VERDICT: APPROVE` exists for this change.

### Disposition

| # | Finding | Disposition | What changed | Pinning tests |
|---|---|---|---|---|
| 1 | **[P1]** Bound concurrent socket teardowns (`OutboundChannel.java:51` started a platform thread per closing channel with no shared limit; 64 blocked closes = 64 live closer threads; an `OutOfMemoryError` from `Thread.start()` escaped the `RuntimeException` fallback on the strike drainer, so the channel, already marked closed, was never torn down and the fan-out aborted after the frame left the view's outbox) | **FIXED** | **One shared, bounded pool.** `OutboundChannel.TEARDOWN` is a `TeardownPool` of `CLOSER_THREADS` = 4 daemon threads `options-edge-ws-closer-1..4`, started once when the class initialises (at the first channel's construction, never on a closing thread) and shared by every channel. `execute` only queues: it never blocks, never runs the teardown on the caller and never starts a thread. **Queue bound:** a channel hands its teardown over at most once (a CAS state machine NONE → PENDING → HANDING → HANDED; only PENDING → HANDING may offer it, so the close and the watchdog's retry can never both hand it over, and `teardown()` is guarded exactly-once besides), so the queue holds at most one entry per socket that is closed and not yet torn down. **Stuck closers:** a blocked close holds one closer thread and nothing else; the write watchdog (`enforceOutboundWriteDeadlines`, every max(100 ms, deadline/2)) interrupts a close that has held its thread past `CLOSE_DEADLINE_MS` = 30 s (above Tomcat's 20 s blocking-send timeout, which already bounds a container close) — once per close, and never the close that thread takes next. When every closer thread is stuck the other teardowns wait in the queue and nothing else waits: their channels are already closed (every enqueue refused, queue dropped), so the cost is that each stays registered until a thread frees; a close that ignores both its timeout and the interrupt keeps its thread for good and the pool continues with one fewer (the class javadoc says so). **Hand-over failure:** `closers.execute` failing with a `RuntimeException` OR an `Error` (the reviewer's `OutOfMemoryError`) is caught in `handOverTeardown`, never thrown at the enqueuer and never run inline: the fan-out continues to every other socket, the teardown stays PENDING, and the write watchdog's sweep — which finds the channel because it is still registered — calls `retryPendingTeardown()` until the closers accept it. Kept unchanged: marked closed + queue dropped at once, `disconnectedSlow`/`writeError`/`droppedOnClose` synchronous, session close then `onClose` exactly once, no write after close, quiet `shutdown()`, every other stream's semantics. New test seams on the service: `outboundClosersForTest`, `outboundChannelForTest`; `enforceOutboundWriteDeadlines` is package-private. | `OutboundChannelTest.sixtyFourChannelsOverflowingAtOnce_neverHoldMoreCloserThreadsThanTheBound_andStallNoSender` (the reviewer's reproduction: 64 channels whose `close()` blocks on a latch overflow in one fan-out; peak live threads named `options-edge-ws-closer*`, sampled every 2 ms, ≤ 4; exactly 4 closes in progress and 60 queued; slowest enqueue < 500 ms; both healthy channels — one before, one after the 64 — get all 20 frames in order; after release `onClose` exactly once and one session close for all 64), `…aTeardownThatCannotBeHandedOverNeverAbortsTheFanOut_andTheWatchdogTearsItDownLater` (an executor throwing `OutOfMemoryError("unable to create native thread…")` and one throwing `RejectedExecutionException`; nothing thrown at the fan-out, the channels after each failed hand-over get every frame, the teardowns stay pending and are not run inline; a retry while still refused leaves them pending; the next retry hands each over once; run twice by the closer, `onClose` still once), `…whenEveryCloserIsStuck_theOtherTeardownsWait_andTheCloseDeadlineFreesTheThreads` (a private 2-thread pool with a 1 s deadline: 5 closes that never return on their own; 2 in progress, 3 queued, no `onClose`; a watchdog tick within the deadline interrupts nothing; ticks past it interrupt each close once and all 5 are torn down once), `FootprintStrikeDeliveryTest.sixtyFourSlowSocketsOverflowingAtOnceNeverHoldMoreCloserThreadsThanTheBound` (the same on the production path: 64 real socket channels whose strike sends and closes block overflow on the strike drainer; peak ≤ 4, exactly 4 closes in progress, the live consumer finishes all 80 records, both healthy sockets get all 80 in order, the view's queue is empty, every slow socket stays registered and closed until its teardown, then is closed and detached exactly once), `…aSlowSocketsTeardownThatCannotBeScheduledNeitherAbortsTheFanOutNorIsLost` (the reviewer's failure mode on the production path: the slow socket's closers throw `OutOfMemoryError`; nothing thrown at the drainer, all three healthy sockets get all 80 records, one hand-over attempt; the channel stays registered, closed, pending; a watchdog tick retries (still refused), the next hands it over and the socket is closed and detached exactly once) |

Peak live closer threads in the green runs: **4** of a bound of 4 in both 64-channel tests (`[closer-bound] … peak live closer threads 4 (bound 4), closes in progress 4, queued 60`, printed by `OutboundChannelTest` and `FootprintStrikeDeliveryTest` in the full build) — the pool's four threads are the only closer threads that ever exist.

### Do the new tests bite? (each part reverted alone, sources restored, sha256 proven)

Each part of the fix was reverted ALONE in the working tree (an exact one-occurrence replacement), `mvn -B -o clean test
-Dtest='OutboundChannelTest,FootprintStrikeDeliveryTest'` run, and both sources copied back. The sha256 of
`OutboundChannel.java` (`6c0cadcd7ec5…`) and `FeedGatewayService.java` (`7716ca4cacf9…`) was taken before the first
revert and after every restore: identical every time (`RESTORED: sha256 identical`, ×5).

| Revert | Went red (assertion failures) |
|---|---|
| A — thread per teardown (`TeardownPool.execute` starts `new Thread(teardown, "options-edge-ws-closer")` instead of queueing: the round-2 behaviour) | `OutboundChannelTest.sixtyFourChannels…:347` "64 overflowing channels held **68** live closer threads at once; the bound is 4"; `FootprintStrikeDeliveryTest.sixtyFourSlowSockets…:658` "64 overflowing sockets held **73** live closer threads at once; the bound is 4"; `OutboundChannelTest.whenEveryCloserIsStuck…:476` expected 2 closes in progress but was 5; both existing blocked-close tests on the thread name (`…:223`, `…:560`) |
| B — an `Error` from the hand-over escapes (`catch (RuntimeException handOverFailed)`: the reviewed fallback's reach) | `OutboundChannelTest.aTeardownThatCannotBeHandedOver…:409` and `FootprintStrikeDeliveryTest.aSlowSocketsTeardownThatCannotBeScheduled…:725`: "…was thrown at the fan-out/drainer: java.lang.OutOfMemoryError: unable to create native thread…" |
| C — the watchdog does not retry a pending teardown | `FootprintStrikeDeliveryTest.aSlowSocketsTeardownThatCannotBeScheduled…:738` "the watchdog retried it" expected 2 but was 1 |
| D — the close deadline never interrupts | `OutboundChannelTest.whenEveryCloserIsStuck…:485` "the deadline freed the stuck threads and the queued teardowns ran" expected 0 but was 5 |
| A+B+C+D together | 7 failures: all of the above |

### Mutation campaign

- `scripts/footprint-campaign.spec.json`: `G-R10.3` re-anchored — the teardown line is now
  `closers.execute(this::teardown);` inside `handOverTeardown`, mutated to `teardown();` (inline on the enqueuer, the same
  intent); its clause and quoted sentence now say "a shared closer thread, never on the enqueuing thread" (the G-R10 row
  was reworded to match: the thread is no longer "of its own"). Three clauses added, each quoting the round-3 as-built
  sentence now in the G-R10 row: `G-R10.4` bounded teardown concurrency (`queue.add(teardown);` → a thread per
  teardown), `G-R10.5` a failed hand-over is never thrown at the enqueuer (`catch (RuntimeException | Error …)` →
  `catch (RuntimeException …)`), `G-R10.6` the write watchdog retries a pending teardown (the retry call commented
  out). The close-deadline interrupt has no clause: it is pinned only by `OutboundChannelTest`, which the campaign
  command (`-Dtest=Footprint*,CvdSpxLevelsWiringTest`) does not run, and a clause for it would be a survivor that says
  nothing about the test that holds it.
- Re-run per `scripts/footprint-mutate.py` in a CLEAN detached worktree at the code commit `5ae84f8`
  (`git worktree add --detach`, clean before and after, removed afterwards). Baseline GREEN
  (`mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest`, 141 tests). Result: **45 KILLED, 1 SURVIVED of 46** — the same recorded
  inert survivor as before (`G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed`). The three new clauses are
  KILLED by assertion failures: `G-R10.4` (bounded concurrency) by
  `FootprintStrikeDeliveryTest.sixtyFourSlowSocketsOverflowingAtOnceNeverHoldMoreCloserThreadsThanTheBound` and
  `…aSlowSocketWhoseCloseBlocksStallsNeitherConsumerNorAnyHealthySocket` (the closer's thread name); `G-R10.5` and
  `G-R10.6` by `…aSlowSocketsTeardownThatCannotBeScheduledNeitherAbortsTheFanOutNorIsLost`. The re-anchored `G-R10.3` is
  KILLED as before, its kill set grown by those two delivery tests. For the other 42 clauses no status and no kill set
  changed.
- `ES-FOOTPRINT-CAMPAIGN.json` replaced by that record (every row names `5ae84f8`) and §2a regenerated from it with
  `scripts/footprint-reqstate.sh` (G-R10 3→6 probes; 46 mutations, 45 killed, 1 surviving).

### Verification

All clean builds, Java 21, offline Maven.

- Code commit `5ae84f8`, before committing: `mvn -B -o clean test` (full) — **1189 run, 0 failures, 0 errors** (1184
  before this round; +5: `OutboundChannelTest` 6→9, `FootprintStrikeDeliveryTest` 10→12), plus the context smoke test
  1/0; `FeedGatewayServiceTest` 259/0.
- The three Jenkinsfile gates (`mvn -B -o clean test`, `scripts/footprint-reverify.sh`,
  `scripts/footprint-reqstate.sh --check`) were run on the commit that adds this record, with a clean tree before and
  after; their exit codes are stated in the PR description rather than here, so recording them does not move the head
  they were run on.
- Not done in this round: the earlier "remaining obligations" table is unchanged; no heap measurement; no Codex
  re-review of this fix. Adjacent and NOT changed (outside this finding): `OutboundChannel.enqueue` still hands its
  drain to the shared writer pool with `writers.execute`, a fixed pool of `GATEWAY_WS_WRITER_THREADS` whose threads
  start lazily on its first executes; a failure there would still propagate to the enqueuer.

### Review text (verbatim)

[P1] Bound concurrent socket teardowns — OutboundChannel.java:51. Every closing channel starts a new platform thread, with no shared concurrency limit. Reproduced by compiling the channel in memory: overflowing 64 channels whose close() blocks creates 64 simultaneously live closer threads. A timeout bounds their lifetime, not their peak count. During mass overflow, native-thread exhaustion can throw OutOfMemoryError from Thread.start() on the strike drainer; the RuntimeException fallback does not catch it. The channel is already marked closed, so teardown/onClose never runs and the watchdog skips it. Fan-out also aborts after the frame was removed from the view's outbox, leaving remaining healthy browsers without that evidence/control. Use bounded teardown concurrency without caller-runs or an unbounded thread fallback.

## Re-review (round 4) — Codex re-review of `66ac0d5` (2026-09-11)

The re-review confirmed the round-3 fix (one shared, pre-started pool of four closer threads) and raised exactly one
finding (verbatim below). Folded on `fix/footprint-strike-gw-r4` and pushed to `fix/footprint-strike-final-review`
(PR #182) as a fast-forward of `66ac0d5`. Not re-reviewed yet: no `VERDICT: APPROVE` exists for this change.

### Disposition

| # | Finding | Disposition | What changed | Pinning tests |
|---|---|---|---|---|
| 1 | **[P2]** Failure logging can permanently kill the shared closer pool (`OutboundChannel.java:471`: under heap exhaustion, formatting the teardown-failure log line threw `OutOfMemoryError` out of the worker's catch; workers are never replaced, so four such failures killed all four and every accepted teardown — queued or submitted later — stayed queued forever, `onClose` never ran, and the watchdog cannot recover an accepted task) | **FIXED** (both layers, plus the stranded task) | **(1) Reporting never throws.** A teardown that throws is counted first — `TeardownPool.failures()`, an `AtomicLong` increment that allocates nothing — then offered to the pool's reporter inside its own `try`/`catch (Throwable)`; a report that throws is swallowed and counted (`unreported()`), so the count is that failure's only trace. The reporter is a constructor seam (`TeardownPool(threads, prefix, deadline, Consumer<Throwable>)`); production keeps the same `System.out` line. **(2) No failure ends a closer thread.** Each turn of a closer thread — the take, the bookkeeping, the teardown — is ONE `try` whose `catch (Throwable)` handler is that non-throwing report, so a thread leaves its loop only when the pool is stopped; the loop's shape is the guarantee, and a dying-worker replacement or watchdog top-up was not added because no path to an exit is left for it to catch. **(3) A dequeued teardown is never stranded.** A session close that throws an `Error` (the finding's case: heap exhaustion inside the container) left the teardown spent — `tornDown` set, state HANDED, `onClose` never called — whatever happened to the thread. Now `teardown()` puts it back: `tornDown` cleared and the state PENDING, with the channel still registered and closed, so the write watchdog's sweep hands it over again (`retryPendingTeardown`) and the retry runs the close and then `onClose`. `onClose` stays exactly once: once it has been called it is never called again, however it ended. **Same defect, one more site:** the hand-over's own failure line in `handOverTeardown` (round 3) was built and printed unguarded inside the catch that keeps a failed hand-over from the enqueuer; under heap exhaustion it would have thrown the `OutOfMemoryError` at the fan-out anyway, or out of the write watchdog's `scheduleAtFixedRate` task, which cancels every later sweep. It is guarded the same way. Kept unchanged: the pool bound of 4, no thread per close, no caller-runs, `onClose` exactly once after the session close, no write after close, the watchdog retry, the 30 s close-deadline interrupt, every other stream's semantics. | `OutboundChannelTest.aFailureWhileReportingATeardownFailureNeverKillsACloser_andNoAcceptedTeardownIsStranded` (the reviewer's reproduction without exhausting the real heap: a private 4-thread pool whose reporter throws `OutOfMemoryError`; 8 teardowns whose session close throws `OutOfMemoryError`, each followed in the queue by a healthy one; afterwards 4 of 4 closer threads alive by name, `failures` 8, `unreported` 8, the 8 teardowns queued behind the failures ran with `onClose` once each, the 8 failed ones are closed and pending again (no `onClose`); 8 more handed over later all run; the watchdog retry tears each failed one down, `onClose` exactly once and a second close; a further retry does nothing), `…aTeardownThatThrowsAnErrorNeverEndsItsCloser_andTheNextTeardownRunsOnIt` (a ONE-thread pool: `OutOfMemoryError`, `StackOverflowError`, `AssertionError` in turn, then a task that records its thread: it runs on `error-closer-1`, still the only thread; `failures` 3; each reported in order), `…aFailureWhileReportingAFailedHandOverIsThrownNeitherAtTheFanOutNorAtTheWatchdog` (`System.out` replaced by one whose `println` throws `OutOfMemoryError`; a closer executor that throws `OutOfMemoryError`; nothing thrown at the fan-out or at the watchdog retry, both reports attempted, the next channel gets every frame, the teardown stays pending and is torn down once when accepted), `FootprintStrikeDeliveryTest.aSlowSocketsTeardownThatFailsUnderHeapExhaustionKillsNoCloserAndIsNeverStranded` (the same on the production path: 16 slow sockets overflow on the strike drainer into a private 4-thread pool whose reporter throws; 8 of them have a session close that throws `OutOfMemoryError`; nothing thrown at the drainer, 4 of 4 closer threads alive, both healthy sockets get all 80 records in order, the 8 others are closed and detached once, the 8 failed ones stay registered, closed and pending; one watchdog tick closes and detaches each, exactly once) |

### Do the new tests bite? (each part reverted alone, source restored, sha256 proven)

Run on the committed code (`9e8a85e`). Each part of the fix was reverted ALONE in the working tree (an exact
one-occurrence replacement), `mvn -B -o clean test -Dtest='OutboundChannelTest,FootprintStrikeDeliveryTest'` run, and
the source copied back. The sha256 of `OutboundChannel.java` (`1f61fa420fb5…`, the file at `9e8a85e`) was taken before
the first revert and after every restore: identical every time (`RESTORED: sha256 identical`, ×5). Every failure below
is an assertion failure, none an error.

| Revert | Went red (assertion failures) |
|---|---|
| A — the report is unguarded (`recordFailure` calls `reporter.accept(failure)` bare: the reviewer's exact defect) | `OutboundChannelTest.aFailureWhileReportingATeardownFailureNeverKillsACloser…:564` and `FootprintStrikeDeliveryTest.aSlowSocketsTeardownThatFailsUnderHeapExhaustion…:820`: "a closer thread died of a failure or of its report: nothing replaces it" expected **4** but was **0** — all four closer threads dead, as in the reviewer's 128 MB reproduction |
| B — the loop catches only `RuntimeException` (an `Error` out of a teardown escapes the turn) | the same two at `:564`/`:820` (4 expected, **0** alive), and `OutboundChannelTest.aTeardownThatThrowsAnErrorNeverEndsItsCloser…:607` "the teardown after three that threw an Error ran" — never ran on the one-thread pool |
| C — a teardown whose close throws is not put back to PENDING | `OutboundChannelTest.aFailureWhileReportingATeardownFailureNeverKillsACloser…:574` "oom-0: a teardown whose close threw is pending again, not stranded" and `FootprintStrikeDeliveryTest.aSlowSocketsTeardownThatFailsUnderHeapExhaustion…:831` "oom-0 is closed, its teardown pending again rather than stranded" — both expected true, were false (the teardown is spent and `onClose` never runs) |
| D — the hand-over's failure report is unguarded (the round-3 line) | `OutboundChannelTest.aFailureWhileReportingAFailedHandOverIsThrownNeitherAtTheFanOutNorAtTheWatchdog:666` "the failed hand-over's report was thrown at the fan-out: java.lang.OutOfMemoryError: Java heap space" |
| A+B+C+D together | 4 failures: all of the above |

Revert A and revert B each go red on their own, and each takes all four closer threads down, so the two layers are
not redundant and neither masks the other's absence. B keeps a teardown's own `Error` inside the turn. A keeps the
report of that failure from escaping the handler. When a teardown's close throws AND its report throws, both are
needed.

### Mutation campaign

- `scripts/footprint-campaign.spec.json`: `G-R10.3`–`G-R10.6` did not move. Their anchors
  (`closers.execute(this::teardown);`, `queue.add(teardown);`, `} catch (RuntimeException | Error handOverFailed) {` and
  the watchdog's `channel.retryPendingTeardown();`) are untouched by this round and still occur once each; only their
  source lines shifted, so none was re-anchored. Three clauses were added, each quoting a round-4 as-built sentence now in
  the G-R10 row:
  - `G-R10.7` "a failure while reporting a teardown's failure never kills a closer thread" — the clause this finding
    asked for. The report guard in `recordFailure` is replaced by a bare `reporter.accept(failure);`.
  - `G-R10.8` "an Error out of a teardown never ends its closer thread": `catch (Throwable teardownFailed)` →
    `catch (RuntimeException teardownFailed)`.
  - `G-R10.9` "a teardown whose session close throws is pending again": `if (!closeReturned)` → `if (false)`.

  A test in the campaign's classes kills all three:
  `FootprintStrikeDeliveryTest.aSlowSocketsTeardownThatFailsUnderHeapExhaustionKillsNoCloserAndIsNeverStranded`, by
  assertion failure. The hand-over report guard (revert D) has no clause. Only `OutboundChannelTest` pins it, and the
  campaign command (`-Dtest=Footprint*,CvdSpxLevelsWiringTest`) does not run that class, so a clause for it would be a
  survivor that says nothing about the test that holds it (the same reason the round-3 close-deadline interrupt has none).
- Re-run per `scripts/footprint-mutate.py` in a CLEAN detached worktree at the code commit `9e8a85e`
  (`git worktree add --detach`, clean before and after, removed afterwards). Baseline GREEN
  (`mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest`, 142 tests). Result: **48 KILLED, 1 SURVIVED of 49** — the same
  recorded inert survivor as before (`G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed`). The three new clauses
  are KILLED as above. `G-R10.3`, `G-R10.4` and `G-R10.6` are KILLED as before, each kill set grown by the new delivery
  test. The other 43 clauses changed neither status nor kill set.
- `ES-FOOTPRINT-CAMPAIGN.json` replaced by that record (every row names `9e8a85e`) and §2a regenerated from it with
  `scripts/footprint-reqstate.sh` (G-R10 6→9 probes; 49 mutations, 48 killed, 1 surviving).

### Verification

All clean builds, Java 21, offline Maven.

- Code commit `9e8a85e`, before committing: `mvn -B -o clean test` (full) — **1193 run, 0 failures, 0 errors** (1189
  before this round; +4: `OutboundChannelTest` 9→12, `FootprintStrikeDeliveryTest` 12→13), plus the context smoke test
  1/0; `FeedGatewayServiceTest` 259/0; peak live closer threads 4 (bound 4) in both 64-channel tests.
- The three Jenkinsfile gates (`mvn -B -o clean test`, `scripts/footprint-reverify.sh`,
  `scripts/footprint-reqstate.sh --check`) were run on the commit that adds this record, with a clean tree before and
  after. Their exit codes are stated in the PR description rather than here, so recording them does not move the head
  they were run on.
- Not done in this round: no Codex re-review of this fix. No real heap exhaustion: the reproduction injects
  `OutOfMemoryError` through the reporter seam and the session mocks rather than running under a 128 MB heap. The earlier
  "remaining obligations" table is unchanged.
- Adjacent and NOT changed (outside this finding):
  - An `Error` thrown from INSIDE `onClose` is not retried, because once `onClose` has been called exactly-once wins. The
    detach stays as far as it got, and the closer thread counts the failure and survives.
  - A session whose close throws on every attempt is retried at every watchdog tick (every max(100 ms, deadline/2)). Each
    attempt holds one closer thread only while it runs, and the channel stays registered and closed meanwhile.
  - `FeedGatewayService.enforceOutboundWriteDeadlines` still guards each per-channel call with
    `catch (RuntimeException)`. After this round nothing on that path in `OutboundChannel` throws an `Error` — the
    hand-over report is guarded — but an `Error` from elsewhere on it would still end the scheduled sweep.
  - The round-3 note on `writers.execute` still stands.

### Review text (verbatim)

[P2] Failure logging can permanently kill the shared closer pool — OutboundChannel.java:471. When a teardown encounters heap exhaustion, formatting this log message can itself throw OutOfMemoryError, escaping the worker's catch. Workers are never replaced. Reproduced twice using the current source with a 128 MB heap: all four workers died; after releasing the heap, existing and newly submitted tasks remained queued forever. Their teardown/onClose never runs, and watchdog retries cannot recover accepted tasks. Guard failure reporting against throwing or restore worker capacity after an unexpected exit. Guarding only this log statement in memory made the same reproduction recover successfully.

## Re-review (round 5) — Codex re-review of `abe0973` (2026-09-11)

The re-review confirmed the round-4 fix and raised exactly one finding (verbatim below). Folded on
`fix/footprint-strike-gw-r5` and pushed to `fix/footprint-strike-final-review` (PR #182) as a fast-forward of
`abe0973`. That push also carries `08341cc`, a test-only commit made between the rounds. It fixes a CME flake:
`FeedGatewayServiceTest.reconnectAfterSelectionIsReadyReceivesSourceReady` failed once on `66ac0d5` with a
`ConcurrentModificationException` at its `sent.stream()` poll, because `stream()` over a
`Collections.synchronizedList` takes no lock while the writer thread appends. `FeedGatewayServiceTest`'s two
recording sinks now stream from `CopyOnWriteArrayList`s. No production file changes in it, so the campaign record,
which hashes the sources it mutated, is unaffected. Not re-reviewed yet: no `VERDICT: APPROVE` exists for this
change.

### Disposition

| # | Finding | Disposition | What changed | Pinning tests |
|---|---|---|---|---|
| 1 | **[P2]** A fast failed close can permanently lose its retry (`OutboundChannel.java:352`, the closer's restore, and `:305`, the hand-over's `teardownState.set(TEARDOWN_HANDED)` after `closers.execute`). Once `execute()` had published the teardown, a closer could run it, have its session close throw, and restore PENDING before the submitting thread reached `:305`. That thread then wrote HANDED over it. The watchdog skips anything not PENDING, so the session was never retried and `onClose` never ran. This hit every stream. Reviewer: 47 of 20,000 fail-once closes stranded on the unmodified four-thread pool | **FIXED** | **The lifecycle is NONE → PENDING → HANDED → DONE, with a failed close going HANDED → PENDING.** HANDING is gone. **(1) The claim comes before the publish.** `handOverTeardown` takes the CAS PENDING → HANDED before `closers.execute`, and after a successful publish it writes nothing. Only one of the close and the watchdog's retry can win that CAS, so a teardown is still never handed over twice. **(2) A failed hand-over** goes back to PENDING only by the CAS HANDED → PENDING. An executor that published the teardown and then threw may already have run it, and what that run did stands (DONE, or its own restore). **(3) The closer's restore** clears `tornDown` first and then takes the CAS HANDED → PENDING. The retry therefore finds the teardown runnable, and the restore never overwrites a run of the same teardown that has since finished. **(4) DONE** is written once the session close returned. It is terminal and the only plain write; every other write is a CAS from PENDING or HANDED, so none can leave DONE. **(5) The close** takes the CAS NONE → PENDING. Only the close that won `closed` gets there, and the CAS states that precondition. **Audit of every transition** (publish-then-write and write-write races): NONE → PENDING has one writer. PENDING → HANDED is contended by the close and the watchdog: CAS, before the publish. HANDED → PENDING after a failed hand-over is contended by a closer that ran a published task: CAS. HANDED → PENDING after a close that threw is contended by another run that finished: CAS, after `tornDown` is cleared. DONE is written only by the run whose close returned. The write that raced the closer's restore, HANDED after the publish, no longer exists. Kept unchanged: `onClose` exactly once, the bound of 4 closer threads, no thread per close, no caller-runs, the round-4 `Throwable` guards, the 30 s close deadline and the watchdog retry. | `OutboundChannelTest.aHandOverNeverOverwritesTheClosersPendingRestore_soAFastFailedCloseIsRetriedNotStranded` is the deterministic interleaving. A closers executor (`FinishBeforeReturning`) hands the teardown to a real 4-thread `TeardownPool` and returns only after the closer ran it to its end: the session close threw `OutOfMemoryError` and the teardown restored PENDING, all before the hand-over's next step. The channel is closed and PENDING. The watchdog's retry hands it over again, the second close returns, `onClose` runs exactly once, a further retry does nothing, and the closer counted one failure with 4 of 4 threads alive. `…aHandOverThatThrowsAfterItsTeardownRanNeverOverwritesWhatTheCloserDid` covers the other side: an executor that publishes, lets the closer finish, then throws `RejectedExecutionException`. A finished teardown is not made pending again; one whose close threw is pending once and torn down once by the retry. `…manyFailOnceClosesOnTheRealFourThreadPool_noneIsStranded` is the reviewer's stress: 5 rounds × 20,000 closes whose first session close throws, on a real 4-thread pool from 4 enqueuers, with a bare `Proxy` session so the closer is fast. Every teardown is pending after its failed close, none is stranded, and the retry tears each down with `onClose` once and two closes. `FootprintStrikeDeliveryTest.aSlowSocketsCloseThatFailsBeforeItsHandOverReturnsIsRetriedNotStranded` is the deterministic interleaving on the production strike drainer. Nothing is thrown at the drainer, both healthy sockets get all 80 records in order, and the slow socket stays registered, closed and pending. One watchdog tick closes and detaches it; a second tick hands nothing over. |

### Do the new tests bite? (each part reverted alone, source restored, sha256 proven)

Run on the committed code (`48f614d`). Each part of the fix was reverted ALONE in the working tree (an exact
one-occurrence replacement), `mvn -B -o clean test -Dtest='OutboundChannelTest,FootprintStrikeDeliveryTest'` run, and
the source copied back. The last row puts back the whole pre-fix file. The sha256 of `OutboundChannel.java`
(`fde342f6cec0…`, the file at `48f614d`) was taken before the first revert and after every restore, and was identical
every time (`RESTORED: sha256 identical`, ×4). The tree was clean before and after. Every failure below is an
assertion failure, none an error.

| Revert | Result |
|---|---|
| A — the hand-over writes HANDED after `execute()` again (`teardownState.set(TEARDOWN_HANDED);` after the publish: the reviewer's defect on the new structure) | 3 failures. `OutboundChannelTest.aHandOverNeverOverwrites…:755` and `FootprintStrikeDeliveryTest.aSlowSocketsCloseThatFailsBeforeItsHandOverReturns…:905`: "the closer's restore to PENDING outlived the hand-over" expected true but was **false**. `OutboundChannelTest.manyFailOnceCloses…:928`: **390 of 100,000** stranded (per round `[144, 0, 0, 0, 246]`). Four more runs of the stress test alone on revert A stranded **133, 134, 235, 197** of 100,000 |
| B — a failed hand-over writes PENDING unconditionally (`teardownState.set(TEARDOWN_PENDING)` in the catch) | 1 failure. `OutboundChannelTest.aHandOverThatThrowsAfterItsTeardownRan…:807` "a teardown that finished is not made pending again by a hand-over that threw afterwards" expected false but was **true**. The stress test stays green, since its hand-overs never fail |
| C — the closer's restore writes PENDING unconditionally | **GREEN: not pinned.** Telling the CAS from a plain write takes a second run of the same teardown finishing (DONE) inside the window between the restoring run's `tornDown.set(false)` and its write. No test drives that window. The CAS is there by the audit, not because a test holds it |
| FULL — the whole pre-fix `OutboundChannel.java` (`08341cc`, sha256 `1f61fa420fb5…`) | 4 failures: A's three and B's one. The stress test stranded **454 of 100,000** (`[72, 0, 0, 0, 382]`) |

**Stress-test size.** On the unfixed source, a single 20,000-close round stranded 231 on an otherwise idle machine.
Single rounds run while a campaign was loading the machine stranded 42, 82, 2, 54 and 4 (revert A) and 8 (the pre-fix
file). One round could therefore come up empty, so the committed test runs 5 rounds × 20,000 = 100,000 closes, in
about 170 ms. Across the six red runs on `48f614d` above, the total ranged from 133 to 454. Rounds 2–4 stranded 0 in
every one of them, and all the stranded closes fell in the first and last rounds. With the fix: **0 of 100,000**
(`[0, 0, 0, 0, 0]`), and 0 of 20,000 in every single-round run.

**The new clause's kill is deterministic.** With the clause's mutation applied (revert A), the round-4 delivery test
(`…FailsUnderHeapExhaustion…`, eight fail-once closes on a real pool) was run 100 times in one JVM and **never failed**.
The new round-5 delivery test failed 20 of 20. So the campaign's kill set for this clause does not hang on a race.

### Mutation campaign

- `scripts/footprint-campaign.spec.json`: no G-R10 clause was re-anchored, because none of their anchors moved.
  `closers.execute(this::teardown);`, `queue.add(teardown);`, `} catch (RuntimeException | Error handOverFailed) {`,
  the watchdog's `channel.retryPendingTeardown();`, the report guard, `} catch (Throwable teardownFailed) {` and
  `if (!closeReturned) {` still occur exactly once each; only their source lines shifted. One clause was added,
  quoting the round-5 as-built sentence now in the G-R10 row:
  - `G-R10.10` "a hand-over never overwrites a closer's pending restore". The patch puts back the reviewer's defect on
    the new structure: `teardownState.set(TEARDOWN_HANDED);` written after `closers.execute(this::teardown);`.

  A test in the campaign's classes kills it:
  `FootprintStrikeDeliveryTest.aSlowSocketsCloseThatFailsBeforeItsHandOverReturnsIsRetriedNotStranded`, by assertion
  failure. The other round-5 tests (the stress and the publish-then-throw test) live in `OutboundChannelTest`, which
  the campaign command (`-Dtest=Footprint*,CvdSpxLevelsWiringTest`) does not run. The failed hand-over's CAS (revert
  B) therefore has no clause, for the same reason as the round-3 close-deadline interrupt and the round-4 hand-over
  report guard: a clause for it would be a survivor that says nothing about the test that holds it. The closer's
  restore CAS (revert C) has no clause either, because no test holds it at all.
- Re-run per `scripts/footprint-mutate.py` in a CLEAN detached worktree at the code commit `48f614d`
  (`git worktree add --detach`, clean before and after, removed afterwards). Baseline GREEN
  (`mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest`, 143 tests). Result: **49 KILLED, 1 SURVIVED of 50**, the
  same recorded inert survivor as before (`G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed`). `G-R10.10` is
  KILLED as above. `G-R10.3`, `G-R10.4`, `G-R10.6`, `G-R10.8` and `G-R10.9` are KILLED as before, each kill set grown
  by the new delivery test. The other 44 clauses (`G-R10.5` and `G-R10.7` among them) changed neither status nor kill
  set.
- `ES-FOOTPRINT-CAMPAIGN.json` was replaced by that record (every row names `48f614d`), and §2a was regenerated from it
  with `scripts/footprint-reqstate.sh` (G-R10 9→10 probes; 50 mutations, 49 killed, 1 surviving).

### Verification

All clean builds, Java 21, offline Maven.

- Code, before committing: `mvn -B -o clean test` (full) gave **1197 run, 0 failures, 0 errors**, plus the context
  smoke test 1/0. That is 1193 before this round, +4: `OutboundChannelTest` 12→15, `FootprintStrikeDeliveryTest`
  13→14. The stress test was then enlarged to five rounds and amended into `48f614d`; the two classes re-run on the
  commit gave 29/0, with 0 of 100,000 stranded.
- `08341cc`'s flake fix is exercised by both full runs of the final gates (`FeedGatewayServiceTest` in each).
- The three Jenkinsfile gates (`mvn -B -o clean test`, run twice; `scripts/footprint-reverify.sh`;
  `scripts/footprint-reqstate.sh --check`) were run on the commit that adds this record, with a clean tree before and
  after. Their exit codes are stated in the PR description rather than here, so recording them does not move the head
  they were run on.
- Not done in this round: no Codex re-review of this fix. The closer's restore CAS is not pinned by any test (revert C
  above). No real heap exhaustion: the failing closes throw an injected `OutOfMemoryError`.
- Adjacent and NOT changed: the round-4 list stands (an `Error` inside `onClose` is not retried; the service's
  watchdog sweep catches only `RuntimeException` per channel; the round-3 `writers.execute` note).

### Review text (verbatim)

[P2] A fast failed close can permanently lose its retry — OutboundChannel.java:352. After execute() publishes the teardown, a worker can run it, encounter an error, and restore PENDING before the submitting thread reaches line 305. That thread then unconditionally overwrites PENDING with HANDED. The watchdog skips the channel forever; the session is never retried and onClose never runs. This affects non-strike streams too. Reproduced by compiling HEAD's source in memory: 47 of 20,000 fail-once closes were stranded using the unmodified four-thread pool. A controlled interleaving reproduced it deterministically. Make hand-over completion preserve a worker's pending transition and add a regression test for this ordering.

## Merged main (#181) — `87b2ba5` + `8229b9c` → `030705b` (2026-09-11)

PR #181 ("Stream bounded footprint history to authenticated Basic clients", already deployed to production) landed on
`main` while this branch was in review, so the PR conflicted. It was merged into this branch with a merge commit
(first parent `87b2ba5`, second parent `8229b9c`), not a rebase, so the Codex-reviewed commits are unchanged.

- **`FeedGatewayService.java`**: git merged it with no textual conflict, because the two sides touch disjoint regions.
  #181's only hunk is the new `handleFootprintBasicMessage`, inserted just before `send()`. This branch's changes are
  the strike relay: the view outbox and drain, adoption-aware replay, retained-storage accounting,
  `enforceOutboundWriteDeadlines` with the closer-pool retry, and the round-4/5 guards. Both were checked in full and
  neither lost a line: the merged file minus `87b2ba5` is exactly #181's delta, and the merged file minus `8229b9c` is
  exactly this branch's delta.
- **Basic replies and the teardown lifecycle.** The Basic handler sends only through
  `send()` → `enqueueOutbound()` → `OutboundChannel.enqueue()`, the same channel every other stream uses. So an
  overflowing Basic reply is torn down by the bounded 4-thread closer pool, through the
  NONE → PENDING → HANDED → DONE state machine and the watchdog retry, never on the Tomcat receive thread. The only
  path around the channel is the untracked-session direct send, which is main's, unchanged.
- **`FeedWebSocketHandler.java`, `FootprintBasicHistory.java`, `FootprintBasicHistoryTest.java`,
  `ES-FOOTPRINT-BASIC-STREAM.md`**: main's, unchanged (this branch never touched them).
- **`Jenkinsfile`**: main's (this branch never changed it). It still runs `mvn -B test`,
  `scripts/footprint-reverify.sh` and `scripts/footprint-reqstate.sh --check`.
- **`scripts/footprint-campaign.spec.json`**: this branch's, unchanged. #181 did not touch `scripts/`, so the union
  of clauses is this branch's 50, and no spec site moved. Every anchor has the same count and the same
  surrounding source in the merged tree; the one multi-occurrence anchor, `G-R10.1` in `FootprintViews.java`
  (occurrence 1 of 2), is as before.
- **`ES-FOOTPRINT-CAMPAIGN.json`** is generated, so it was not hand-merged. It was re-recorded per
  `scripts/footprint-mutate.py` in a CLEAN detached worktree at the merge commit `030705b` (`git worktree add
  --detach`, clean before and after, removed afterwards). Baseline GREEN (`mvn -B test
  -Dtest=Footprint*,CvdSpxLevelsWiringTest`, 146 tests: the 143 before, plus the 3 in `FootprintBasicHistoryTest`, which the `Footprint*` pattern now includes). Result: **49 KILLED, 1 SURVIVED of 50**, every row naming `030705b`. The survivor is the same recorded inert one (`G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed`).
  No status changed against either parent record. Kill sets changed only by addition, and each addition is what the
  other parent brings:
  - Against this branch's record (`87b2ba5`, 50 rows), two kill sets grew by #181's new tests, exactly as #181's own
    refresh (`3029adf`) recorded them on main. `G-R4 equal-date-upserts` gained
    `FootprintBasicHistoryTest.streamedPagesAreBoundedAscendingAndExcludeOvernight`. `G-R7 to-is-inclusive` gained that
    test and `FootprintBasicHistoryTest.historyUsesRegisteredSocketsAndTheExistingBoundedWriter`. Both read through
    `FootprintViews.barsPage`, which those mutations break. The other 48 are unchanged.
  - Against main's record (`8229b9c`, 34 rows), the difference is this branch's own campaign: its 16 clauses
    (`G-R3.4`–`3.6`, `G-R7.1`–`7.2`, `G-R8.1`–`8.3`, `G-R10.3`–`10.10`, all KILLED), and the `G-R6.2` and `G-R8a
    ceiling-strict` kill sets grown by this branch's delivery tests. #181 adds no clause: it changed neither the spec nor
    any anchored site.
- §2a was regenerated from that record with `scripts/footprint-reqstate.sh`. The output is byte-identical to the committed
  section, because the table carries statuses and counts only (50 mutations, 49 killed, 1 surviving) and neither moved.

Verification: `mvn -B -o clean test` on `030705b` gave **1200 run, 0 failures, 0 errors** (1197 + the 3 in
`FootprintBasicHistoryTest`), plus the context smoke test 1/0. The Jenkinsfile gates on the final head are stated in
the PR description, for the same reason as in round 5.
