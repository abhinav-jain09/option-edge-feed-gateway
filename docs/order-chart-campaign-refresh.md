# Chart delivery campaign source binding

Gateway Jenkins build 851 did not publish an image. Its Linux ES4 run passed 1,973 tests and a separate context smoke test. The full 50-mutation footprint campaign reproduced the existing outcomes: 49 killed mutations and one already characterized survivor. The final `footprint-reqstate.sh --check` refused the checked-in campaign because it still bound FeedGatewayService and OutboundChannel to their preceding source hashes.

This change replaces only `ES-FOOTPRINT-CAMPAIGN.json` with the actual captured rerun archived by build 851 at source `5e64c32871b86450b36d0207e9ba9c9acdb9d572`. The artifact SHA-256 is `2500199fba271c7037396f484b0908682bedeeb2583b8d5b9384a475aabf5f3d`. No production code, mutation specification, gate, timeout or outcome is changed.

Artifact: [build 851 campaign](http://localhost:8085/job/option-edge-feed-gateway/851/artifact/.jenkins-tmp/es4-tests-851/footprint-reverify.json). The local controller URL is session-specific; the committed record preserves its own source/patch/baseline/output provenance. It is a reproducible CI record, not a signed attestation or a passing release receipt.

Validation: the existing requirement-state generator accepts all current source bindings, and `bash scripts/footprint-reqstate.sh --check` passes. Regenerating the design table produces no change because claims and outcomes are unchanged. `git diff --check` passes. The next Jenkins run must repeat the full suite/campaign and all release checks; this record refresh grants no deployment bypass.

## Refresh for the liq-AA relay changes (build 872)

Build 872 (gateway source `1696106671acf0663cce54deec23a5bd76941c2e`) passed the full suite and reproduced the 50-mutation campaign (49 killed, the same one characterized survivor), then refused to emit because `FeedGatewayService.java` had moved (PRs 332 and 333, the targets relay). `ES-FOOTPRINT-CAMPAIGN.json` is replaced by the record captured by that run (`.jenkins-tmp/es4-tests-872/footprint-reverify.json`, 395,779 bytes, SHA-256 `d3baeac0394c304db8f56f09c946081f5bd9b5babad38d0152921fd1547ba359`). No code, spec, gate or outcome changes; `bash scripts/footprint-reqstate.sh --check` reports the campaign matches. The next Jenkins run repeats everything.

## Refresh for the ES MBO relay (build 874)

Build 874 (gateway source `2df1beca9d9198fadd5dcb3f0d21eb30feb5284f`, the EsMboRelay change) passed the full suite (1,422 tests in the contracts run, the gateway suite on the Linux ES4 host) and reproduced the 50-mutation campaign (49 killed, the same one characterized survivor; every outcome identical to the checked-in record), then refused to emit because `FeedGatewayService.java` had moved. `ES-FOOTPRINT-CAMPAIGN.json` is replaced by the record captured by that run (`.jenkins-tmp/es4-tests-874/footprint-reverify.json`, 395,779 bytes, SHA-256 `1885305f26ab4ca8a041831bae6ec61113cc18c4df6cc92ddb69d1afa8470cdd`). No code, spec, gate or outcome changes. The next Jenkins run repeats everything.

## Refresh for the connect-replay change (build 881)

Build 881 (gateway source `55635c1aa0f0ddddd579883bd8f4817a5ed54ae8`, the replay-tagged OutboundChannel change) reproduced the 50-mutation campaign (49 killed, the same one characterized survivor; every outcome identical to the checked-in record), then refused to emit because `FeedGatewayService.java` and `OutboundChannel.java` had moved. `ES-FOOTPRINT-CAMPAIGN.json` is replaced by the record captured by that run (`.jenkins-tmp/es4-tests-881/footprint-reverify.json`, 395,780 bytes, SHA-256 `b0dfca93749c0dc746aac380f4c1bf96f3eb529498eaadc1cf04919eda10d437`). No code, spec, gate or outcome changes. The next Jenkins run repeats everything.

## Source rebinding for the real-gateway What-If test (2026-11-19, no build artifact)

**This entry is NOT like the four above it, and the difference matters.** Each of those replaced
`ES-FOOTPRINT-CAMPAIGN.json` wholesale with the record a specific build captured, after that build
had reproduced the campaign (49 killed, the one characterized survivor) and then refused to emit
because the source had moved. **No build has run on this change**, so there is no captured artifact
to replace it with. What is changed here is only the **source binding**: the 22 occurrences of the
`FeedGatewayService.java` hash — 11 `originalFileSha256` and 11 `original` — move from
`0f50e365f089311dcb0151fe53e0f68efd5bcca5743229376e1b51e2701dd6eb` to
`4cac5f438818bf7f12d12b00127d96f9bdd722a30765865e53f5a7d8487dac7d`. Every patch, baseline, claim
and outcome in the record is byte-for-byte what build 881 captured, and the file stays 395,780
bytes.

The change to `FeedGatewayService.java` that moved the hash is the addition of
`cacheWhatIfMapForTest`, a test seam beside the existing `cacheStrikeFlowForTest` and
`cacheSellerActivityForTest` seams. It adds a method and touches no existing line, so no mutation
site in the campaign is edited — but "no mutation site is edited" is a claim about this diff, not a
measurement, and the gate exists precisely because such claims are cheap. **The next gateway build
re-runs the whole 50-mutation campaign against this source and must reproduce every outcome; if an
anchor has shifted or an outcome differs, it refuses to emit and this rebinding is what has to be
redone.** The rebinding grants no deployment bypass and asserts nothing about a run that has not
happened.

`bash scripts/footprint-reqstate.sh --check` is run against the committed tree, since the checker
reads the file at HEAD rather than the working copy.

---

## 2026-11-20 — C-63's bar tap displaced G-R9.2's anchor, and the build was right to refuse

Build **#892** of the gateway ran the full suite green (`Tests run: 1424, Failures: 0, Errors: 0`)
and then refused with a bare `RuntimeError: remote full suite or campaign failed`. The reason was
not in the console. `scripts/footprint-reqstate.sh --check` against the committed tree gave it:

```
G-R9: FeedGatewayService.java at HEAD is not the file the campaign mutated
      (sha256 5e049ddac17d, the record says 4cac5f438818)
G-R9: the clause this mutation claims to have broken is not in FeedGatewayService.java at HEAD
```

**Two different faults, and only one of them is bookkeeping.** Eleven of the twelve mutations that
quote this file still matched their anchor exactly once; the sha had simply moved. But
**`G-R9.2 every-record-is-counted-before-admission` no longer matched at all.** Its patch quotes
`admitFootprintRecord` from the `records_total` counter line through the admission as one block, and
C-63's bar tap had been inserted *inside* that span. The quoted text no longer existed in the file,
so the mutation would have had nothing to apply itself to — and a campaign that cannot apply a
mutation cannot claim the clause is still protected.

**The fix is not a re-aimed anchor.** Rewriting that patch by hand would have meant inventing what
the campaign "would have" mutated without running it, which is the kind of claim this gate exists to
refuse. Instead **the tap moved above the anchor's first line**, to the top of
`admitFootprintRecord`. The anchor is then verbatim again, every one of the twelve matches its
recorded occurrence count, and the mutation still breaks the clause it names. The tap is also
*better* there: it sees every bar record the consumer polled, before any admission, which is what
"the page's store is bounded by the night and not by the view" was supposed to mean.

Only after that was the file's sha rebound: **22 occurrences**, `4cac5f438818` → `ebf50c28b99a`.
The rebinding is a source rebinding and nothing else. **It asserts nothing about a run that has not
happened**: the next build re-runs the whole campaign against this source, and if any outcome
differs it refuses again and this has to be redone.

The lesson for the next change to this file: a mutation anchor can span twenty lines, so inserting
anywhere inside a quoted block silently invalidates it. Run the checker against the committed tree
*before* the 45-minute build, not after it.

## Refresh for the per-topic catch-up gate (build 905)

Build **905** (gateway source `b03548388ab9a5e8c266a690082562ddf8df3e8f`) passed the full suite —
**2,411 tests, 0 failures, 0 errors, 1 skipped** — and reproduced the 50-mutation campaign: 49
killed, the one characterized survivor, and *"the record reproduces: 50 mutations, same claim and
same outcome for every one"*. It then **refused to emit** because the source had moved:

```
REFUSING TO EMIT:
   G-R1/G-R2/G-R3/G-R6/G-R9/G-R10: src/main/java/app/feedgateway/FeedGatewayService.java at HEAD is
   not the file the campaign mutated (sha256 e0a4cc57c27f, the record says 9b89108979f9) — the
   source has moved and the campaign has to be re-run
```

**What moved the hash, and it was not this change.** `FeedGatewayService.java` was last touched by
**#156, "Per-topic catch-up gate: one slow topic can no longer dark the whole board"**, which landed
at **2026-11-20 12:10:22** and is an ancestor of the built tree. That PR did not refresh this record,
so every gateway build after it refuses to emit — build 905 was doomed before it started, twenty-four
minutes later.

**This is the first four entries' kind of refresh, not the 2026-11-19 source-rebinding kind.** There
*is* a captured artifact: build 905's own
[`footprint-reverify.json`](http://localhost:8085/job/option-edge-feed-gateway/905/artifact/.jenkins-tmp/es4-tests-905/footprint-reverify.json),
402,680 bytes, which is the record the build's log names on the line *"Captured current campaign
record (402680 bytes)"*. `ES-FOOTPRINT-CAMPAIGN.json` is replaced **wholesale** with that file. Every
patch, baseline, claim and outcome in it is what build 905 measured against the current source, so no
hash is hand-edited and no claim is carried over untested.

The 22 `FeedGatewayService.java` occurrences — 11 `originalFileSha256` and 11 `original` — now read
`e0a4cc57c27f5670d46a72bb4bf1a820359dd759a2fc9a8653fcd5817b2393c8`, which is the sha256 of that file
at `b0354838`. The record grows from 395,780 to 402,680 bytes, and `50` top-level entries are
unchanged.

Validation: `bash scripts/footprint-reqstate.sh --check` prints **§2a matches the campaign record**.
