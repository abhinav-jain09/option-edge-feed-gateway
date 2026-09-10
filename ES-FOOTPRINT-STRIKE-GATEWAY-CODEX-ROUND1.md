# ES Footprint strike stream — gateway CODE Codex round 1 (gpt-6-astra, 2026-09-10)

Review of PR #179 at 80a15ce. Verdict: **REQUEST_CHANGES**. Every finding was folded in df57fea; dispositions:

| # | finding | disposition |
|---|---|---|
| 1 | collision detection forgets superseded revisions; evicted identity returns at a lower revision | FIXED — per-identity digest per observed revision; eviction advances a monotonic boundary below which records are `evicted`, never re-admitted; `aCollisionRefusesTheIdentity…` (both orders), `theBudgetsEvictTheOldestIdentitiesAndTheBoundaryIsMonotonic` |
| 2 | index drops symbol | FIXED — scope = symbol|timeframe everywhere; routes take `symbol` (default `GATEWAY_ES_FOOTPRINT_STRIKE_SYMBOL`); `symbolsHaveIndependentIndexesAndRemoval` |
| 3 | refusing the newest exposes an older episode as latest | FIXED — refused identities stay as tombstones; `latest` has no row for that strike; `aRefusedNewestEpisodeNeverFallsBackToAnOlderValue` |
| 4 | shape admission can corrupt the hello; CHECKPOINT date unvalidated; epoch ceiling on strike/revision | FIXED — timeframe vocabulary enforced, JSON-null CHECKPOINT date accepted / invalid refused, `nonNegative` for strike and revision; `shapeAndOversizeAreDropsNotCrashes_andTheHelloCanNeverBeCorrupted` |
| 5 | refusal metadata unbounded | FIXED — `GATEWAY_ES_FOOTPRINT_STRIKE_MAX_REFUSED_IDENTITIES`; overflow = UNAVAILABLE for the incarnation (503 + Retry-After, hello flag, gauge); `theRefusalLedgerIsBoundedAndOverflowFailsClosed`, `anUnavailableStrikeViewAnswers503WithRetryAfterOnBothRoutes` |
| 6 | historyBeginsAtMs not a reliable boundary | FIXED — monotonic eviction boundary reported; pre-eviction the oldest retained open |
| 7 | latest page stamped with another session | FIXED — the envelope names the REQUESTED session |
| 8 | locale-dependent keys/labels; cursor accepts impossible positions | FIXED — Locale.ROOT; cursor grammar validates calendar date and epoch domain; `keysAndCursorsDoNotDependOnTheDefaultLocale` |
| 9 | tests do not establish fifth-stream conformance | PARTIAL → FIXED where named: busy-precedes-validation, unavailable, fifth-partition END seek, quoted-record envelope; the view tests above. Byte-exact delivery: records now ride the frame and the pages as JSON string literals (`FootprintStrikeView.quoted`), verified by `quotedCarriesTheBytesVerbatim` and the controller envelope test |

## Review text (verbatim)

I found blocking fold, indexing, and memory-bound defects. The relay wiring largely follows the existing path, but the current implementation does not meet R14 reliably.

I reviewed `origin/main..HEAD`, both designs, the producer grammar, and `StrikeRecords.java`. Focused probes against the prebuilt classes reproduced the failures below. Of 53 existing tests executed, 32 passed; 21 failed during fixture construction because the read-only sandbox prevented `SellerActivityDiskStore` from creating its temporary directory. This was not a fresh compilation. The known 11 `volPremium*` baseline failures are excluded.

For the regression snippets below, add this helper to `FootprintStrikeViewTest`:

```java
private static String e(long open, long revision, String tag) {
    return episode(revision == 0 ? "OPEN" : "UPDATE",
            "2026-09-10", "1m", 680_000, open, revision,
            open + revision, tag);
}
```

1. **[P1] Collision detection forgets every superseded revision.**

   **Evidence:** [FootprintStrikeView.java:102](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:102) compares bytes only when the incoming revision equals the current head. Line 110 accepts older revisions without checking their bytes. Replacement and eviction discard the earlier bytes.

   Executed sequence: revision `0/A`, revision `2/C`, revision `0/B`. Result: `ADMITTED`, `collisions=0`.

   **Why it matters:** R14 applies to any two observed records sharing identity and revision. The result currently depends on arrival order. Eviction also permits a previously observed identity to return at a lower revision: I reproduced revision 2 being replaced by revision 0 after eviction and a subsequent slot becoming available.

   **Corrected code or test:** Track collision evidence for every observed `(identity, revision)` before selecting the greatest revision. Coordinate its storage with finding 5; adding an unlimited fingerprint map is insufficient.

   ```java
   @Test void supersededRevisionCollisionsAreStillFatal() {
       for (boolean newerFirst : List.of(false, true)) {
           var v = view();
           if (newerFirst) v.admit(e(100, 2, "new"));
           v.admit(e(100, 0, "A"));
           if (!newerFirst) v.admit(e(100, 2, "new"));

           assertEquals(FootprintStrikeView.Reason.COLLISION,
                   v.admit(e(100, 0, "B")).reason());
           assertEquals(1, v.collisions());
           assertEquals(FootprintStrikeView.Reason.REFUSED,
                   v.admit(e(100, 3, "later")).reason());
           assertTrue(v.history("1m", 680_000, "", 100)
                   .records().isEmpty());
       }
   }
   ```

2. **[P1] The secondary index drops `symbol`, corrupting identity isolation and removal.**

   **Evidence:** [FootprintStrikeView.java:97](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:97) includes symbol in `heads`, but line 114 indexes only `(timeframe, strike, sessionDate, openBarStartMs)`. Line 133 removes that shared index entry without checking which identity owns it.

   Executed two records differing only in symbol: `heads.size()==2`, but history returned one record. Refusing the overwritten symbol’s identity then removed the other symbol’s index entry, leaving history empty while a head remained.

   **Why it matters:** R14 explicitly scopes both queries by symbol. The producer preserves symbol in its identity and record; there is no admission check establishing a single-symbol invariant here.

   **Corrected code or test:** Include symbol in the index and query scope. Preserve existing URLs with an explicit default symbol if required; do not silently merge symbols.

   With symbol-scoped overloads, add:

   ```java
   @Test void symbolsHaveIndependentIndexesAndRemoval() {
       var v = view();
       String es = e(100, 0, "ES");
       String other = e(100, 0, "other")
               .replace("ES.v.0", "ESZ6");

       v.admit(es);
       v.admit(other);
       assertEquals(List.of(es),
               v.history("ES.v.0", "1m", 680_000, "", 100).records());
       assertEquals(List.of(other),
               v.history("ESZ6", "1m", 680_000, "", 100).records());

       v.admit(e(100, 0, "collision"));
       assertEquals(List.of(other),
               v.history("ESZ6", "1m", 680_000, "", 100).records());
   }
   ```

3. **[P1] Refusing the newest episode exposes an older episode as “latest.”**

   **Evidence:** [FootprintStrikeView.java:105](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:105) removes the colliding episode completely from the index. Lines 177–179 then select the greatest remaining episode.

   Executed sequence: valid episode at `100`, colliding episode at `200`. `latest` returned episode `100`, with `refused=1`.

   **Why it matters:** R14 requires the colliding latest episode to produce `NO DATA`. Returning an earlier interaction gives the chip a valid-looking stale observation. A global refusal count does not identify which strike must suppress its value.

   **Corrected code or test:** Preserve a refusal tombstone in the ordering structure. Select the greatest episode identity first, then suppress its value if refused. History may still contain earlier valid episodes.

   ```java
   @Test void refusedNewestEpisodeNeverFallsBackToAnOlderValue() {
       var v = view();
       String older = e(100, 0, "older");
       v.admit(older);
       v.admit(e(200, 0, "A"));
       v.admit(e(200, 0, "B"));

       assertTrue(v.latest("1m", "2026-09-10", -1, 200)
               .records().isEmpty());
       assertEquals(List.of(older),
               v.history("1m", 680_000, "", 100).records());

       String newer = e(300, 0, "newer");
       v.admit(newer);
       assertEquals(List.of(newer),
               v.latest("1m", "2026-09-10", -1, 200).records());
   }
   ```

4. **[P1] Shape admission permits a record to corrupt the shared hello JSON.**

   **Evidence:** [FootprintStrikeView.java:89](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:89) requires only a nonempty timeframe. Line 158 interpolates it into JSON without escaping. Unlike the existing coordinator’s admission at [FootprintViews.java:101](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintViews.java:101), it does not enforce the timeframe vocabulary.

   The executed timeframe `x"y` was admitted and produced:

   ```text
   {"sessionDate":"2026-09-10","hwm":{"x"y":100},...}
   ```

   A CHECKPOINT with `sessionDate:"2026-02-30"` was also admitted and advanced HWM, because the CHECKPOINT branch bypasses date validation.

   **Why it matters:** One malformed strike record can make the entire `cvd-hello` invalid, affecting the four existing streams and CVD. Arbitrary CHECKPOINT timeframes also grow `hwm` without a bound.

   **Corrected code or test:** Validate timeframe and the nullable CHECKPOINT date before any mutation. Preserve a genuine JSON `null` date, which `StrikeRecords.checkpoint` can serialize; reject invalid non-null dates.

   ```java
   JsonNode dateNode = root.get("sessionDate");
   if (!FootprintViews.TIMEFRAMES.contains(tf)
           || dateNode == null
           || (!dateNode.isNull()
               && (!dateNode.isTextual() || date == null))) {
       return Admission.SHAPE;
   }
   ```

   Also separate numeric validators. Line 95 applies the epoch ceiling to `strikeCents` and `revision`, although neither is an epoch field. The producer grammar writes longs; the executed revision `253402300800000` was incorrectly rejected.

   ```java
   private static long nonNegativeLong(JsonNode n, String k) {
       JsonNode v = n.get(k);
       if (v == null || !v.isIntegralNumber() || !v.canConvertToLong()) {
           return -1;
       }
       long x = v.longValue();
       return x < 0 ? -1 : x;
   }
   ```

   Use that for strike/revision and retain `epoch` for timestamps. Add probes for quoted/unknown timeframes, invalid CHECKPOINT dates, valid nullable CHECKPOINT dates, and non-epoch numeric domains.

5. **[P1] Refusal metadata is unbounded, invalidating the bounded-heap claim.**

   **Evidence:** [FootprintStrikeView.java:62](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:62) retains every refused identity permanently. Line 106 adds entries; enforcement at line 143 accounts only for heads and their JSON bytes.

   Executed with `maxEpisodes=1`: after 1,000 distinct collisions, the view held zero episodes and **1,000 refusal entries**. Reported record bytes were zero.

   The amendment at [ES-FOOTPRINT-GATEWAY-DESIGN.md:276](/private/tmp/oe-gw-fpstrike/ES-FOOTPRINT-GATEWAY-DESIGN.md:276) adds budgets but does not replace G-R8’s four-stream heap calculation or deployment inequalities.

   **Why it matters:** A faulty stream can grow the gateway heap indefinitely despite both configured budgets. The additional 64 MiB payload allowance, indexes, collision tracking, and fifth topic also require a revised resource calculation.

   **Corrected code or test:** Bound collision/refusal metadata explicitly. Preserve incarnation-long refusal through bounded durable storage, or enter a terminal unavailable state when its limit is exhausted. Simply evicting refusal entries violates R14.

   A concrete acceptance test for the terminal-state option is:

   ```java
   @Test void refusalCapacityExhaustionMakesBackfillUnavailable()
           throws Exception {
       String key = "GATEWAY_ES_FOOTPRINT_STRIKE_MAX_REFUSED_IDENTITIES";
       String previous = System.getProperty(key);
       System.setProperty(key, "2");
       try {
           var s = FootprintWiringTest.on();
           for (int i = 1; i <= 3; i++) {
               for (String tag : List.of("A", "B")) {
                   s.admitFootprintRecord("es-footprint-strike",
                           FootprintStrikeViewTest.episode(
                                   "OPEN", "2026-09-10", "1m",
                                   680_000, i, 0, i, tag), "cache");
               }
           }
           var response = new MockHttpServletResponse();
           controller(s, 200).footprintStrikeLatest(
                   "1m", "2026-09-10", -1, 200, null, response);
           assertEquals(503, response.getStatus());
       } finally {
           if (previous == null) System.clearProperty(key);
           else System.setProperty(key, previous);
       }
   }
   ```

   This proposes a new bounded-ledger setting and unavailable-state contract. The hello/live path must expose that state consistently as well.

6. **[P2] `historyBeginsAtMs` is an inventory minimum, not a reliable history boundary.**

   **Evidence:** [FootprintStrikeView.java:212](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:212) derives the boundary exclusively from the oldest retained head. No eviction cutoff or replay-coverage boundary is retained.

   Executed sequence: eviction moved the boundary to `200`; a later refusal freed space; replaying an old identity moved it backward to `100`. An emptied view returns `null`, losing evidence of earlier truncation.

   Cache seeking at [FeedGatewayService.java:2979](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:2979) uses Kafka timestamps. A retained update can refer to an episode opened before that seek window; its open time does not establish continuous history coverage from that instant.

   **Why it matters:** R20 requires an explicit boundary that prevents partial history from appearing complete.

   **Corrected code or test:** Track replay coverage and a monotonic eviction cutoff separately from the oldest retained row. Prevent late records from reopening discarded coverage. Define treatment of equal-time evictions and empty retained views.

   ```java
   @Test void evictionBoundaryCannotMoveBackwardOnLateReplay() {
       var v = new FootprintStrikeView(
               new ObjectMapper(), 262_144, 1 << 20, 2);
       v.admit(e(100, 2, "old"));
       v.admit(e(200, 0, "middle"));
       v.admit(e(300, 0, "new"));
       long boundary = v.historyBeginsAtMs();

       v.admit(e(300, 0, "collision"));
       v.admit(e(100, 0, "late"));
       assertNotNull(v.historyBeginsAtMs());
       assertTrue(v.historyBeginsAtMs() >= boundary);
   }
   ```

7. **[P2] A session-scoped latest page is stamped with a different session.**

   **Evidence:** [FootprintStrikeView.java:184](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:184) returns the global `sessionDate`, although the records were selected using the requested `session`.

   Executed request for September 10 after admitting September 11 data: envelope `sessionDate=2026-09-11`, records `sessionDate=2026-09-10`.

   **Why it matters:** The envelope mislabels its records. This also affects valid future-session requests that correctly return no rows.

   **Corrected code:**

   ```java
   return new Page(session, out,
           out.size() >= limit && last != null
                   ? Long.toString(last) : null,
           historyBeginsAtLocked(), refused.size());
   ```

   Pin both a retained prior-session request and an empty future-session request.

8. **[P2] Keys and metric labels depend on locale; cursor validation accepts impossible positions.**

   **Evidence:** [FootprintStrikeView.java:229](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:229) and line 230 use default-locale formatting. Under Arabic locale, the executed probe produced Arabic digits: `latest` returned no rows because those keys sort beyond its ASCII upper bound, while history returned a cursor its own validator rejects.

   [FeedGatewayService.java:914](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:914) similarly lowercases enum names using the default locale. Turkish casing produces a collision label different from the ASCII label exported by metrics.

   [FootprintStrikeView.java:210](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FootprintStrikeView.java:210) accepts `2026-02-30|9999999999999999999`.

   **Why it matters:** Valid data can disappear from latest, pagination can fail on a server-generated cursor, and counted drops can disappear from the exported label series.

   **Corrected code:**

   ```java
   static String openKey(String sessionDate, long openBarStartMs) {
       return sessionDate + "|"
               + String.format(java.util.Locale.ROOT, "%019d", openBarStartMs);
   }

   private static String ageKey(long openBarStartMs, String identity) {
       return String.format(java.util.Locale.ROOT, "%019d", openBarStartMs)
               + "|" + identity;
   }

   static boolean validHistoryCursor(String c) {
       if (c == null
               || !c.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}\\|[0-9]{19}")
               || FootprintViews.parseCanonicalDate(c.substring(0, 10)) == null) {
           return false;
       }
       try {
           return Long.parseLong(c.substring(11)) <= EPOCH_MAX_MS;
       } catch (NumberFormatException e) {
           return false;
       }
   }
   ```

   Change the strike drop-label conversion to:

   ```java
   sa.reason().name().toLowerCase(java.util.Locale.ROOT)
   ```

   Test Arabic formatting and Turkish labels with locale restoration, plus epoch `0`, `9`, `10`, maximum, overflow, impossible dates, and cursor URL round trips.

9. **[P2] The new tests do not establish the claimed fifth-stream conformance.**

   **Evidence:** [FootprintStrikeViewTest.java:25](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintStrikeViewTest.java:25) contains six tests; [FootprintBackfillControllerTest.java:73](/private/tmp/oe-gw-fpstrike/src/test/java/app/feedgateway/FootprintBackfillControllerTest.java:73) adds two route tests. `FootprintSeamTest` is unchanged: its END-seek, delivery, concurrent snapshot, and Spring-binding probes still exercise existing streams.

   **Why it matters:** The copied strike writer is a separate implementation. Tests of `writePage` do not protect `writeStrikePage`. Likewise, a fifth topic in a source assertion does not establish executed fifth-topic admission, seeking, or fan-out.

   **Corrected test example:** Add this to `FootprintBackfillControllerTest`; it pins busy-before-validation and permit ownership for both routes.

   ```java
   @Test void strikeBusyPrecedesValidationAndDoesNotReleaseAPermit()
           throws Exception {
       var s = FootprintWiringTest.on();
       var c = controller(s, 200);
       assertTrue(s.footprintBackfillPermits().tryAcquire(4));
       try {
           var latest = new MockHttpServletResponse();
           c.footprintStrikeLatest(
                   "1m", "invalid", -1, 200, null, latest);
           assertEquals(503, latest.getStatus());

           var history = new MockHttpServletResponse();
           c.footprintStrikeHistory(
                   "1m", 680_000, "invalid", 100, null, history);
           assertEquals(503, history.getStatus());

           assertEquals(0, s.footprintBackfillPermits().availablePermits());
           String metrics = s.footprintMetricsText();
           for (String route : List.of("strike_latest", "strike_history")) {
               assertTrue(metrics.contains(
                       "gateway_footprint_backfill_rejected_total{route=\""
                       + route + "\",reason=\"busy\"} 1\n"));
               assertTrue(metrics.contains(
                       "gateway_footprint_backfill_rejected_total{route=\""
                       + route + "\",reason=\"bad_cursor\"} 0\n"));
           }
       } finally {
           s.footprintBackfillPermits().release(4);
       }
   }
   ```

The remaining requested checks and coverage are below. **None** means no existing strike-specific probe establishes that clause.

| Fold/read clause | Evidence and existing probe | Missing probe |
|---|---|---|
| Greatest revision; older revision after newer | `FootprintStrikeView.java:102–111`; `FootprintStrikeViewTest:25` | **None:** ascending replacement with byte-accounting assertions |
| Identical bytes are not a collision; current-head collision counted; later revision refused | `FootprintStrikeViewTest:34` | **None:** superseded/evicted revision collisions; newest-refused fallback |
| CHECKPOINT changes HWM without adding an episode; HWM does not regress | `FootprintStrikeViewTest:76` | **None:** nullable versus invalid date, invalid timeframe, malformed recovery keys |
| Latest greatest open time, one session/timeframe, strike order and exclusive cursor | `FootprintStrikeViewTest:50` | **None:** symbol isolation, requested-session envelope, numeric digit boundaries |
| History newest first across sessions; exclusive cursor | `FootprintStrikeViewTest:50`; controller test at `FootprintBackfillControllerTest:99` | **None:** nonexistent valid cursor, URL round trip, semantic cursor validation |
| Both eviction budgets | `FootprintStrikeViewTest:86` | **None:** byte-budget survivor identity, out-of-order/mixed-timeframe eviction, equal-time ties, revision growth/shrink |
| Index/heads/byAge consistency after removal/refusal | `FootprintStrikeView.java:128–148` | **None:** full consistency check after replacement, refusal, eviction, and symbol collision |
| One snapshot per read; no coordinator lock during client writes | View locks at `154`, `169`, `192`; controller snapshots before writing at `124`, `149` | **None:** concurrent strike admission/read and admission while a response writer is blocked |
| Hello/latest/history agreement | Basic hello at `FootprintStrikeViewTest:76` | **None:** refusal, eviction, session-envelope and locale consistency |
| LOADING versus completed NO DATA | Hello exposes observed HWM at `FootprintStrikeView.java:122–160` | **None:** cache behind live, partially folded bootstrap, CHECKPOINT-only terminal HWM; observed maximum alone is not a completion proof |
| Shape and oversize | Four basic cases at `FootprintStrikeViewTest:99` | **None:** full date/key/type/domain matrix, UTF-8 ceiling equality and one-byte excess |
| Fixed-width epoch ordering | Implementation at `FootprintStrikeView.java:229` | **None:** locale and full-domain probes |

| Service clause | Assessment and evidence | Coverage |
|---|---|---|
| Same flag; fifth topic bound unconditionally | Correct: construction at `FeedGatewayService.java:815`, binding at `2470–2477` | Executed five-binding map at `FootprintWiringTest:108`; shared call-site pin at `41` |
| Same gate for all five topics | Correct: topic list at `FeedGatewayService.java:849`; gate assignment/seeking at `933–980` | Startup zero labels include fifth topic. **None:** fifth-topic rejection, later admission, and validation counters through both consumer flows |
| Both consumers use one admission method | Correct: cache at `2886`; live at `985`, called from `3321` | Source pin at `FootprintWiringTest:70`; **None:** executed fifth-event consumer seam |
| Admit before live broadcast; oversize never broadcast | Correct sequence at `FeedGatewayService.java:984–989`; strike decision at `909–916` | **None:** strike sink observes admitted view; strike oversize reaches neither socket |
| Seven-day cache policy | Correct at `FeedGatewayService.java:6627`; actual timestamp seek at `2979` | Policy tested at `FootprintWiringTest:128`; **None:** fifth-topic timestamp seek and adoption |
| Live END on bootstrap/retry/adoption | Fifth topic included through gate topic set at `976–980` | **None:** fifth partition in `FootprintSeamTest` cases at `114`, `127`, `153` |
| `cvd-hello` field and flag-off bytes | Correct addition at `FeedGatewayService.java:11335` | Tested at `FootprintWiringTest:221` and `92`; **None:** explicit strike-view-null assertion flag-off |
| Per-session allowlist | Present at `FeedGatewayService.java:12607` | Source checked at `FootprintWiringTest:87`; **None:** executed fifth-event authenticated fan-out |
| Every startup metric cell; flag-off exception | Export loops at `FeedGatewayService.java:1005–1053` match amendment | Exact startup set tested at `FootprintWiringTest:269`; flag-off exports only `enabled 0` |
| Nonzero strike metrics and overlap identities | Admission counters at `FeedGatewayService.java:908–916` | **None:** scripted strike collision/refused/oversize/shape sequence across cache/live, broadcast identity, eviction gauges, and locale |
| Existing-stream non-interference | Existing admission branches remain; raw-pass-through path remains | Existing coverage retained, but malformed strike hello and unbounded memory create cross-stream failure paths |

| Route/settings clause | Assessment and evidence | Missing probe |
|---|---|---|
| Binding → flag → auth → permit → validation → snapshot → write | Correct source order at `GatewayController.java:112–154`; shared auth at `191–211` | **None:** strike MockMvc binding-before-flag; both routes’ overlapping rejection conditions |
| Flag/auth precedence | Latest 404 tested at `FootprintBackfillControllerTest:93`; history 401 at `114` | **None:** history flag-off; latest auth failure; flag-off plus failed auth; auth failure plus bad cursor |
| Limits clamp to `[1,200]` / `[1,100]` | Correct at `GatewayController.java:125`, `150` | **None:** more than 200/100 matching records with excessive limit; zero/negative limit |
| Permit release | Both handlers use `finally` at `127`, `152` | **None:** both strike routes’ mid-write/flush failure, buffer refusal, and post-acquisition validation failure |
| Verified 64 KiB streaming buffer | Correct copied implementation at `GatewayController.java:160–187`; one record encoded at a time | **None:** strike writer ignores requested buffer size, oversized reported buffer, blocked writer, exact UTF-8 bytes |
| Envelope | Fields written at `GatewayController.java:174–185` | Basic happy paths covered; session mislabeling and refusal semantics are findings above |
| Request `sessionDate` validation | Uses strict canonical parser at `GatewayController.java:123` | One compact-date rejection covered. **None:** invalid calendar date, unpadded date, empty date |
| Setting names/defaults | `GatewaySettings.java:342–357`: `KAFKA_ES_FOOTPRINT_STRIKE_TOPIC=futures.footprint.strike`; bytes 64 MiB; episodes 50,000; seek-back 604,800,000 ms | Defaults tested at `FootprintWiringTest:312` |
| Setting bounds/prefix/overrides | Minimum 1 through helpers at `GatewaySettings.java:1909–1924`; topic prefix through `1870–1887`; no explicit upper caps beyond Java numeric types | **None:** strike overrides, prefix/no-double-prefix, zero/negative clamp, malformed/overflow fallback |

**R21:** I found no line that adds or renames a field inside a producer record, buckets evidence, or derives a market interpretation. The gateway parses routing/fold metadata, stores the original JSON, bypasses enrichment through [FeedGatewayService.java:9550](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/FeedGatewayService.java:9550), and writes retained record bytes at [GatewayController.java:183](/private/tmp/oe-gw-fpstrike/src/main/java/app/feedgateway/GatewayController.java:183). Hello/page metadata is outside the producer record.

The episode fixture’s envelope follows `StrikeRecords.EPISODE_KEYS`, and its CHECKPOINT envelope follows `CHECKPOINT_KEYS`. However, its series contains only a fabricated `tag` field at `FootprintStrikeViewTest:17`. **None:** an independent strike relay test using actual producer-shaped series, unknown enum values/nulls, and exact byte equality through latest, history, and live delivery. Add that test; the existing bars-only verbatim test does not establish strike R21.

The requested bars are therefore:

- **Institutional: FAIL.** Arrival-order-dependent collision handling, symbol-index corruption, stale latest fallback, and mislabeled session envelopes.
- **Military: FAIL.** Malformed input can corrupt a shared handshake; refusal metadata is unbounded; critical failure-path probes are absent.
- **NASA: FAIL.** The fifth stream lacks a valid complete heap bound and updated deployment arithmetic. Snapshot/write lock separation and streaming are sound by inspection, but they do not compensate for the correctness and resource failures.

VERDICT: REQUEST_CHANGES
R14 folding and identity isolation are incorrect, and bounded-memory safety and fifth-stream conformance are not established.
