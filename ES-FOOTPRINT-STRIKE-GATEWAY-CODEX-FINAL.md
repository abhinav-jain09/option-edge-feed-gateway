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
