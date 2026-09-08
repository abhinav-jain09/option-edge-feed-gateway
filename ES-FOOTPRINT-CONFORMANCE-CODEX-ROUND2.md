Reading additional input from stdin...
2026-09-08T20:24:15.619297Z ERROR codex_models_manager::manager: failed to load models cache: missing field `supports_parallel_tool_calls` at line 93 column 5
OpenAI Codex v0.147.0
--------
workdir: /private/tmp/oe-conf-gw
model: gpt-5.6-sol
provider: openai
approval: never
sandbox: read-only
reasoning effort: low
reasoning summaries: none
session id: 01a082b1-5914-7c22-be23-d760b0c7db73
--------
user
Round 2 of the review of option-edge-feed-gateway PR #174 at /private/tmp/oe-conf-gw, branch
test/footprint-conformance-pins. Your round-1 verdict was REQUEST_CHANGES with four findings.

Round-1 findings and what was done — VERIFY EACH against the files, do not take this as evidence:
 1. "§2a contains unsupported and false generation claims" — scripts/footprint-reqstate.sh now
    REWRITES the block between the markers in place (and --check holds the committed section to the
    record); the forward reference to ES-FOOTPRINT-CONFORMANCE.md is removed (that document was
    never merged into this repository).
 2. "G-R11 is not derived from the campaign record" — the note is reworded to say G-R11 lists the
    tests the other requirements are held by and has no production clause a mutation could break,
    and every editorial note is now suffixed "(not probed)".
 3. "The generator can emit claims the record does not support" — footprint-reqstate.py now REFUSES
    to emit when any KILLED record lacks a named assertion failure, lacks verbatim failure lines, or
    ran against a tree it did not restore. It names every non-KILLED status rather than calling them
    all "characterised", and counts only requirements present in the rendered document.
    Separately the harness now distinguishes surefire <<< FAILURE! (assertion) from <<< ERROR!
    (throw) and node ERR_ASSERTION from any other throw: a throw-only run is KILLED-BY-ERROR unless
    the spec declared killedByThrow in advance. This caught G-R10.1, which is now declared.
 4. "G-R10's 1/1 pinned materially overclaims" — two more G-R10 clauses are probed (every admission
    failure is a counted drop; records counted BEFORE admission), and the column now reads
    "n of m clauses probed here are pinned" with a header paragraph stating that this does NOT mean
    the requirement as a whole is held.

Then review the WHOLE change again line by line. In particular:
  * whether footprint-reqstate.sh's in-place rewrite is faithful (does --check actually detect a
    stale section? can the awk block mangle the document? is the preamble file part of the
    generated output or hand-maintained, and is that honest?);
  * whether the two new G-R10 mutations are killed for the RIGHT reason (read their failureLines);
  * whether killedByThrow can be abused to launder an incidental error as a kill, and whether
    G-R10.1's use of it is justified;
  * whether any remaining cell in §2a claims more than its record carries.

End with exactly one line: VERDICT: APPROVE  or  VERDICT: REQUEST_CHANGES
codex
I’ll re-open the repository instructions and diff first, then trace the generator, harness semantics, campaign record, and every rendered §2a cell back to its evidence.
exec
/bin/bash -lc "pwd && rg --files -g 'AGENTS.md' -g '"'!target'"' -g '"'!node_modules'"' .. | head -50 && git status --short --branch && git diff --stat && git diff --name-only" in /private/tmp/oe-conf-gw
exec
/bin/bash -lc 'find .. -name AGENTS.md -print; git branch -a; git log --oneline --decorate -15' in /private/tmp/oe-conf-gw
 succeeded in 14494ms:
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-DOpZPAB7' (errno=Operation not permitted)
2026-09-08 22:24:27.122 xcodebuild[23226:266016244]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:27.669 xcodebuild[23226:266016243] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-l61kLJRw' (errno=Operation not permitted)
2026-09-08 22:24:28.857 xcodebuild[23270:266016520]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:29.340 xcodebuild[23270:266016517] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
## test/footprint-conformance-pins...origin/test/footprint-conformance-pins
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-jEmRA552' (errno=Operation not permitted)
2026-09-08 22:24:31.709 xcodebuild[23579:266017176]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:32.112 xcodebuild[23579:266017170] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-8usZs4uR' (errno=Operation not permitted)
2026-09-08 22:24:32.870 xcodebuild[23615:266017327]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:33.213 xcodebuild[23615:266017319] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-qM39EKvT' (errno=Operation not permitted)
2026-09-08 22:24:35.043 xcodebuild[23704:266017626]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:35.423 xcodebuild[23704:266017624] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-hUkpxTpt' (errno=Operation not permitted)
2026-09-08 22:24:36.283 xcodebuild[23752:266017873]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:36.621 xcodebuild[23752:266017871] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.

2026-09-08T20:24:47.913394Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
 succeeded in 14266ms:
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-wcQFsoxM' (errno=Operation not permitted)
2026-09-08 22:24:44.470 xcodebuild[23883:266018495]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:44.814 xcodebuild[23883:266018494] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-Z09Flj2V' (errno=Operation not permitted)
2026-09-08 22:24:45.547 xcodebuild[23901:266018572]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:45.902 xcodebuild[23901:266018571] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
+ agent/graceful-kafka-shutdown
+ agent/restart-safe-selection
+ agent/strike-flow-payload-freshness
  chore/remove-redundant-replay-carveout
  chore/remove-staging
  chore/remove-versioned-identities
  ci/feed-gateway-fix-buildkit-prod
  ci/feed-gateway-use-oeprofile
  claude/maxpain-terminal-clean
  claude/maxpain-terminal-status
  claude/mission-sandwich-forward-barrier
+ claude/mission-sandwich-ui
+ codex/heatmap-replay-freshness
  codex/opb-gateway-forwarding
  codex/strike-flow-gateway
  dev-tag-main-only
  docs/campaign-failure-lines
  docs/footprint-campaign-record
+ feat/accel-ws-binding
  feat/build-number-in-tag
  feat/close-direction-gateway
+ feat/context-tape-routes
+ feat/corridor-gauge-ws
  feat/cvd-spx-levels-stream
  feat/cvd-stream
  feat/databento-gex-history-merge
+ feat/dealer-ledger-gateway
+ feat/dealer-ledger-per-strike-ladder
  feat/delta-flow-gateway-consumer
+ feat/direction-relay
  feat/drop-nowcast-gateway
+ feat/es-auction-gateway
+ feat/es-footprint-gateway
+ feat/es-gex-on-spx
+ feat/es-od-status-gateway
+ feat/es-open-direction-gateway
+ feat/forward-strike-clusters
  feat/gamma-migration-endpoint
+ feat/gamma-rotation-endpoint
  feat/gateway-auto-expiry-roll
  feat/gateway-es-strike-intel-overlay
  feat/gateway-gex-lifecycle
  feat/gateway-rollover-diagnostics
  feat/gateway-spx-price
  feat/gateway-strike-intel-consumer
  feat/gateway-turn-alert
  feat/gex-magnet-gateway
  feat/gex-magnet-gateway2
  feat/gex-oi-missing-banner
  feat/gex-oi-status-event
+ feat/gex-strike-lifecycle
  feat/greek-move-auth-gateway
  feat/gw-session-expiry-roll
  feat/heatmap-calibration-2026-07-30
  feat/hot-strike-event
+ feat/ibkr-preopen-arb
  feat/ibkr-preopen-arbitration
+ feat/ibkr-preopen-gateway
+ feat/indicators-ws
+ feat/liquidity-history
  feat/live-date-floor
  feat/mission-pace-gateway
  feat/opb-v2-gateway
  feat/option-price-behavior-dashboard-wip
+ feat/option-truth-gateway
+ feat/option-truth-relay
+ feat/pace-rank-gateway
  feat/pace-volume-trajectory-gateway
  feat/pin-flow-endpoint
  feat/pin-gateway-clauses
  feat/pinflow-net-flow
  feat/pinflow-spot-from-history
  feat/preopen-gex-gateway
  feat/prod-image-tag
  feat/replay-control-endpoints
  feat/replay-date-validation
  feat/replay-gateway-bridge-config
  feat/replay-run-picker
  feat/replay-runid-bridge
  feat/replay-session-bridge
  feat/replay-strike-cluster-on-connect
  feat/seller-activity-backend-endpoint
  feat/short-premium-gateway
  feat/spot-band-forwarding
  feat/spot-p2-ui-canonical
+ feat/spot-vol-regime
  feat/spread-skew-gateway
  feat/stock-gex-by-expiry
  feat/stock-gex-close-board
+ feat/stock-gex-endpoint
  feat/strike-invasion-direction
  feat/strike-invasion-gateway
  feat/strike-invasion-gateway-main
  feat/system-status-endpoint
+ feat/tape-zones-board
  feat/unified-sr-gateway
  feat/vol-premium-ivrv-event
  feat/zero-dte-direction-gateway
  feature/es-aggressor-flow-gateway
  feature/es-seller-activity-gateway
  feature/hpsf-v2.1-bugzilla-40-64
  feature/ibkr-strike-flow-gateway
  feature/ibkr-vix-price-topic
  feature/keyclock-multi-tenant-sessions
+ fix/align-vix-option-topic
  fix/auto-roll-override-stale
  fix/context-smoke-execution-selects-by-tag
+ fix/context-tape-upstream-connection-recycle
  fix/delta-flow-review
  fix/es-aggressor-flow-direct-broadcast
  fix/es-aggressor-flow-direct-broadcast-v2
  fix/es-gex-health-disabled
  fix/gamma-migration-default-selection
  fix/gateway-auto-only-explicit
+ fix/gateway-dynamic-partitions
  fix/gateway-forward-watchdog
  fix/gateway-gex-slow-ttl
  fix/gateway-market-calendar-single-source
  fix/gateway-never-creates-topics
  fix/gateway-partition-refresh-followup
  fix/gateway-partition-refresh-hardening
  fix/gateway-remove-stray-short-premium
  fix/gateway-restore-sp-decls
+ fix/gateway-service-constructor-ambiguity
  fix/gateway-ws-send-timeout
  fix/graceful-absence-default
  fix/heatmap-history-failclosed
+ fix/liquidity-history-partitions-dev-32
  fix/mission-pace-forward-barrier
+ fix/oi-banner-clears-on-live-gex
  fix/per-topic-catchup-gate
  fix/permanent-seller-activity-gateway
  fix/pill-anchor-side-correct
  fix/pin-epoch-edge-cursor
  fix/pinflow-session-window
  fix/pinflow-spot-centred-band
  fix/pinflow-trade-date-partition
+ fix/preopen-gex-arbitration-killswitch
  fix/preopen-session-id-basic-iso
  fix/prod-clean-replace
  fix/seller-activity-ctor-ambiguity
  fix/seller-activity-session-leader
  fix/seller-api-per-strike
  fix/seller-rest-only-backpressure
+ fix/spot-band-batch-enqueue
  fix/spot-band-selfreview
  fix/system-status-ledger-timeout
  fix/system-status-null-booleans
  fix/system-status-shortcircuit
  fix/ws-dual-auth-bearer-and-ticket
+ gw-detached
+ main
  pace-nasa-precision-20260616
  peak-move-card
  pin-mac-agent
  pr1
  promote/everything-to-main
  promote/exp-to-dev-gw
+ r14-fragility-endpoint
  rebase/gex-strike-lifecycle
  refactor/market-data-expiry
  revert/preopen-gex-arbitration
  revert/spot-band-forwarding
* test/footprint-conformance-pins
  test/invasion-fence-relay-audit
  remotes/origin/HEAD -> origin/main
  remotes/origin/agent/graceful-kafka-shutdown
  remotes/origin/agent/restart-safe-selection
  remotes/origin/agent/strike-flow-payload-freshness
  remotes/origin/chore/remove-staging
  remotes/origin/chore/remove-versioned-identities
  remotes/origin/ci/feed-gateway-fix-buildkit-prod
  remotes/origin/ci/feed-gateway-use-oeprofile
  remotes/origin/claude/databento-gex-ui
  remotes/origin/claude/maxpain-gateway
  remotes/origin/claude/maxpain-terminal-clean
  remotes/origin/claude/mission-sandwich-forward-barrier
  remotes/origin/claude/mission-sandwich-ui
  remotes/origin/docs/campaign-failure-lines
  remotes/origin/docs/footprint-campaign-record
  remotes/origin/feat/accel-ws-binding
  remotes/origin/feat/build-number-in-tag
  remotes/origin/feat/context-tape-routes
  remotes/origin/feat/corridor-gauge-ws
  remotes/origin/feat/cvd-spx-levels-stream
  remotes/origin/feat/cvd-stream
  remotes/origin/feat/delta-flow-gateway-consumer
  remotes/origin/feat/direction-relay
  remotes/origin/feat/drop-nowcast-gateway
  remotes/origin/feat/es-auction-gateway
  remotes/origin/feat/es-footprint-gateway
  remotes/origin/feat/es-gex-on-spx
  remotes/origin/feat/gamma-migration-endpoint
  remotes/origin/feat/gamma-rotation-endpoint
  remotes/origin/feat/gateway-auto-expiry-roll
  remotes/origin/feat/gateway-gamma-leadership-event
  remotes/origin/feat/gateway-gex-lifecycle
  remotes/origin/feat/gateway-rollover-diagnostics
  remotes/origin/feat/gateway-spx-price
  remotes/origin/feat/gateway-strike-intel-consumer
  remotes/origin/feat/gateway-turn-alert
  remotes/origin/feat/gex-magnet-gateway
  remotes/origin/feat/gex-magnet-gateway2
  remotes/origin/feat/gex-strike-lifecycle
  remotes/origin/feat/gw-session-expiry-roll
  remotes/origin/feat/heatmap-calibration-2026-07-30
  remotes/origin/feat/hot-strike-event
  remotes/origin/feat/ibkr-preopen-arb
  remotes/origin/feat/indicators-ws
  remotes/origin/feat/liquidity-history
  remotes/origin/feat/mission-pace-gateway
  remotes/origin/feat/opb-v2-gateway
  remotes/origin/feat/option-price-behavior-dashboard-wip
  remotes/origin/feat/option-truth-gateway
  remotes/origin/feat/option-truth-relay
  remotes/origin/feat/pin-flow-endpoint
  remotes/origin/feat/pinflow-net-flow
  remotes/origin/feat/pinflow-spot-from-history
  remotes/origin/feat/prod-image-tag
  remotes/origin/feat/replay-control-endpoints
  remotes/origin/feat/replay-gateway-bridge-config
  remotes/origin/feat/replay-session-bridge
  remotes/origin/feat/replay-strike-cluster-on-connect
  remotes/origin/feat/seller-activity-backend-endpoint
  remotes/origin/feat/short-premium-gateway
  remotes/origin/feat/spot-band-forwarding
  remotes/origin/feat/spot-p2-ui-canonical
  remotes/origin/feat/spot-vol-regime
  remotes/origin/feat/spread-skew-gateway
  remotes/origin/feat/stock-gex-by-expiry
  remotes/origin/feat/stock-gex-endpoint
  remotes/origin/feat/strike-invasion-direction
  remotes/origin/feat/system-status-endpoint
  remotes/origin/feat/tape-zones-board
  remotes/origin/feat/vol-premium-ivrv-event
  remotes/origin/feat/zero-dte-direction-gateway
  remotes/origin/feature/es-aggressor-flow-gateway
  remotes/origin/feature/hpsf-v2.1-bugzilla-40-64
  remotes/origin/feature/ibkr-strike-flow-gateway
  remotes/origin/feature/ibkr-vix-price-topic
  remotes/origin/fix/align-vix-option-topic
  remotes/origin/fix/auto-roll-override-stale
  remotes/origin/fix/context-smoke-execution-selects-by-tag
  remotes/origin/fix/context-tape-upstream-connection-recycle
  remotes/origin/fix/delta-flow-review
  remotes/origin/fix/es-aggressor-flow-direct-broadcast
  remotes/origin/fix/es-aggressor-flow-direct-broadcast-v2
  remotes/origin/fix/es-gex-health-disabled
  remotes/origin/fix/gamma-migration-default-selection
  remotes/origin/fix/gateway-auto-only-explicit
  remotes/origin/fix/gateway-dynamic-partitions
  remotes/origin/fix/gateway-forward-watchdog
  remotes/origin/fix/gateway-market-calendar-single-source
  remotes/origin/fix/gateway-never-creates-topics
  remotes/origin/fix/gateway-partition-refresh-followup
  remotes/origin/fix/gateway-partition-refresh-hardening
  remotes/origin/fix/gateway-remove-stray-short-premium
  remotes/origin/fix/gateway-restore-sp-decls
  remotes/origin/fix/gateway-service-constructor-ambiguity
  remotes/origin/fix/gateway-ws-send-timeout
  remotes/origin/fix/graceful-absence-default
  remotes/origin/fix/heatmap-history-failclosed
  remotes/origin/fix/liquidity-history-partitions-dev-32
  remotes/origin/fix/mission-pace-forward-barrier
  remotes/origin/fix/per-topic-catchup-gate
  remotes/origin/fix/pill-anchor-side-correct
  remotes/origin/fix/pin-epoch-edge-cursor
  remotes/origin/fix/pinflow-session-window
  remotes/origin/fix/pinflow-spot-centred-band
  remotes/origin/fix/pinflow-trade-date-partition
  remotes/origin/fix/preopen-gex-arbitration-killswitch
  remotes/origin/fix/preopen-session-id-basic-iso
  remotes/origin/fix/prod-clean-replace
  remotes/origin/fix/seller-api-per-strike
  remotes/origin/fix/strike-intel-keep-until-expiry
  remotes/origin/fix/system-status-ledger-timeout
  remotes/origin/fix/system-status-null-booleans
  remotes/origin/fix/system-status-shortcircuit
  remotes/origin/harden/pr4-review-fixes
  remotes/origin/main
  remotes/origin/pace-nasa-precision-20260616
  remotes/origin/pin-mac-agent
  remotes/origin/pr1
  remotes/origin/promote/exp-to-dev-gw
  remotes/origin/r14-fragility-endpoint
  remotes/origin/refactor/market-data-expiry
  remotes/origin/test/footprint-conformance-pins
  remotes/origin/test/invasion-fence-relay-audit
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-hZ4tNxrY' (errno=Operation not permitted)
2026-09-08 22:24:48.002 xcodebuild[23927:266018734]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:48.339 xcodebuild[23927:266018724] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-6UkGEa0q' (errno=Operation not permitted)
2026-09-08 22:24:49.106 xcodebuild[23948:266018832]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:49.394 xcodebuild[23948:266018828] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
23ff325 (HEAD -> test/footprint-conformance-pins, origin/test/footprint-conformance-pins) docs(gateway footprint): §2a is regenerated in place, and says only what the record carries
0ecf5f7 wip: drop tainted records
c4c5e0c wip: reqstate rewrites the section in place
94e2ea5 wip: harness declares throw-detection
236e742 wip: sync tooling
54e3952 docs(gateway footprint): the design carries what a test actually holds
1224cec test(gateway footprint): execute the four clauses the suite only read
3e13574 (origin/main, origin/HEAD) Merge pull request #172 from abhinav-jain09/docs/campaign-failure-lines
7e220e6 (origin/docs/campaign-failure-lines, docs/campaign-failure-lines) docs(gateway): the record now carries the failure lines its killedBy was parsed from
fc49c14 Merge pull request #171 from abhinav-jain09/docs/footprint-campaign-record
ac38b5c (origin/docs/footprint-campaign-record, docs/footprint-campaign-record) docs(gateway): the footprint mutation record, with the evidence to audit it
b933b39 Merge pull request #170 from abhinav-jain09/fix/pin-epoch-edge-cursor
de2f4f5 (origin/fix/pin-epoch-edge-cursor, fix/pin-epoch-edge-cursor) docs: archive the four merge-gate rounds for the epoch-edge cursor pin
1e6e6d5 fix(gateway): kill switch for the pre-open GEX VALUE plane — it has no consumer (#169)
b054e8d test(gateway): pin G-R7's epoch-edge cursor guard, which nothing held

2026-09-08T20:24:51.309666Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "git diff --find-renames origin/main...HEAD -- . ':(exclude)package-lock.json'" in /private/tmp/oe-conf-gw
 succeeded in 5255ms:
2026-09-08 22:24:53.731 xcodebuild[24102:266019383]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:54.094 xcodebuild[24102:266019379] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-pSGNqFzV' (errno=Operation not permitted)
2026-09-08 22:24:55.057 xcodebuild[24125:266019529]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:24:55.824 xcodebuild[24125:266019527] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
diff --git a/ES-FOOTPRINT-CAMPAIGN.json b/ES-FOOTPRINT-CAMPAIGN.json
index 3ef4e8f..821d30a 100644
--- a/ES-FOOTPRINT-CAMPAIGN.json
+++ b/ES-FOOTPRINT-CAMPAIGN.json
@@ -9,42 +9,61 @@
   "occurrencesInFile": 1,
   "line": 146,
   "enclosing": "private Boolean rollover(LocalDate date) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
    "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
    "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
-  "seconds": 10.2,
+  "assertionFailures": [
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
+   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
+   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
+   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
+   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "killedByThrow": false,
+  "seconds": 13.9,
   "patch": {
    "old": "        if (cmp < 0) return null;",
    "new": "        if (cmp < 0) return false;"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "ec78466bc95be4361035496b31dada2ca7a89979e9afca9d6256e3e6228578ef",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc"
+    }
+   },
+   "outputSha256": "5e021ee04b30f0063c26ca14004fbc25fc728a03543c533676bbd83f1397ed10",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.151 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.228 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:77 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:88 expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:101 expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:124 expected: <true> but was: <false>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:97 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:108 expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:121 expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -58,33 +77,46 @@
   "occurrencesInFile": 1,
   "line": 148,
   "enclosing": "private Boolean rollover(LocalDate date) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
-  "seconds": 8.8,
+  "assertionFailures": [
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
+  ],
+  "killedByThrow": false,
+  "seconds": 16.3,
   "patch": {
    "old": "        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;",
    "new": "        bars.clear(); barsBytes = 0;"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "2b4910e75b4982f1a3776765d046c802ac7ba914b7a82dc916903426387793e3",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6"
+    }
+   },
+   "outputSha256": "3aab049deb18a7f73892763b0b9aa8fc86375e7e4df2123e957720aa4a1ba1f2",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.005 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:75 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.038 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:95 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -98,46 +130,65 @@
   "occurrencesInFile": 1,
   "line": 147,
   "enclosing": "private Boolean rollover(LocalDate date) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
-  "seconds": 8.5,
+  "assertionFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
+   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "anyFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
+   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "killedByThrow": false,
+  "seconds": 17.9,
   "patch": {
    "old": "        if (cmp == 0) return false;",
    "new": "        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "e7d9988cf63f51531404d049ceb2b174f98a024532d5710262f601ee911881bc",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca"
+    }
+   },
+   "outputSha256": "69fc19f041c48924e7af47e81e9de3941e2096b473c6def4ec66a5f7510f98c9",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
     "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.002 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {\"sessionDate\":\"2026-08-14\",\"bars\":[{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"30s\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":5000},\"tag\":\"x\"}],\"nextCursor\":null} ==> expected: <true> but was: <false>",
-    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:172 expected: <2> but was: <0>",
-    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:230 expected: <2> but was: <0>",
-    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:215 expected: <1> but was: <0>",
-    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:115 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{},\"outcomeHwm\":{\"5m\":70}}>"
+    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:192 expected: <2> but was: <0>",
+    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:250 expected: <2> but was: <0>",
+    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:235 expected: <1> but was: <0>",
+    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:135 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{},\"outcomeHwm\":{\"5m\":70}}>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -151,33 +202,46 @@
   "occurrencesInFile": 1,
   "line": 150,
   "enclosing": "private Boolean rollover(LocalDate date) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
-  "seconds": 8.6,
+  "assertionFailures": [
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
+  ],
+  "killedByThrow": false,
+  "seconds": 13.7,
   "patch": {
    "old": "        rollovers.incrementAndGet();",
    "new": "        if (false) rollovers.incrementAndGet();"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "9e6ccf3c769ffa53ee8c96ed39d366bd70811d167c61c82638737b23c459272c",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5"
+    }
+   },
+   "outputSha256": "fa0ac70a00952c2540e8bbaf520f69a6b8631131ecd2b0c8a14188c8f0506a7b",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.007 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:76 expected: <1> but was: <0>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.015 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:96 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -191,36 +255,51 @@
   "occurrencesInFile": 1,
   "line": 167,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
-  "seconds": 7.2,
+  "assertionFailures": [
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "killedByThrow": false,
+  "seconds": 14.1,
   "patch": {
    "old": "            String oldest = view.ceilingKey(tf + \"|\");",
    "new": "            String oldest = view.floorKey(tf + \"|~\");"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "ea7501101d4bda446c06deb2f7eededf9f8c58dccc2b43c37e9bc598aff38621",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146"
+    }
+   },
+   "outputSha256": "01d4eaa84ff657dc806c92c6c369a501882283a14cdf2c2ca8734639b05fe21e",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
     "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:232 expected: <247> but was: <246>",
-    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:217 1m lost its oldest ==> expected: <[2]> but was: <[1]>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:252 expected: <247> but was: <246>",
+    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:237 1m lost its oldest ==> expected: <[2]> but was: <[1]>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -234,33 +313,46 @@
   "occurrencesInFile": 1,
   "line": 166,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
-  "seconds": 6.4,
+  "assertionFailures": [
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "killedByThrow": false,
+  "seconds": 15.6,
   "patch": {
    "old": "            if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;",
    "new": "            if (isBars) barsEvictCursor = cursor; else outcomesEvictCursor = cursor;"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "1c700daa48bd560d42ed500056b7f54ddf4a324cadb6fdd5913d4690ec844c21",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "b9defc28c88e369675adabf7390a7929b6668650bffb3e94eb98d4a9eada40bb",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "1c700daa48bd560d42ed500056b7f54ddf4a324cadb6fdd5913d4690ec844c21"
+    }
+   },
+   "outputSha256": "430c27ebc692beaea8bc43f21aba2aa30c02c410cad6abd0d340544715818ac2",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.011 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:220 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.009 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -274,33 +366,46 @@
   "occurrencesInFile": 1,
   "line": 171,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
-  "seconds": 6.3,
+  "assertionFailures": [
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
+  ],
+  "killedByThrow": false,
+  "seconds": 12.5,
   "patch": {
    "old": "            if (isBars) barsBytes -= len; else outcomesBytes -= len;",
    "new": "            if (isBars) barsBytes -= 0; else outcomesBytes -= 0;"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "604a8ec8ce620cc3e8d1e4a18e51bdc41af6639c24b90f01754b45a9619c0d3a",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "fc7778a39d264840ea1382981bb5f955404e3cbbec98c99ce1d7493f972c106d",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "604a8ec8ce620cc3e8d1e4a18e51bdc41af6639c24b90f01754b45a9619c0d3a"
+    }
+   },
+   "outputSha256": "dbb6a7ace18e7e3f0886d5f05ef318614b797559b30867e9532d69c32538d394",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.010 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:229 expected: <1> but was: <3>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:249 expected: <1> but was: <3>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -314,33 +419,46 @@
   "occurrencesInFile": 1,
   "line": 161,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
-  "seconds": 6.3,
+  "assertionFailures": [
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
+  ],
+  "killedByThrow": false,
+  "seconds": 13.3,
   "patch": {
    "old": "        while (view.size() > maxCount || (isBars ? barsBytes : outcomesBytes) > maxBytes) {",
    "new": "        while (view.size() > maxCount) {"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "be3b5ec8befa5b49f38a03ab67a789b7cfc023b4d7ce49ad8354fc3661fc4248",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "94955821bd5c499cbb593b1ed7082e310858522ebd248407b92d9bb3acf81f4f",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "be3b5ec8befa5b49f38a03ab67a789b7cfc023b4d7ce49ad8354fc3661fc4248"
+    }
+   },
+   "outputSha256": "829712c2cef02b35881e5dea3e4d44ab4ee4cdab5094bd51174a52afef64d5a3",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.014 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:229 expected: <1> but was: <0>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:249 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -354,36 +472,51 @@
   "occurrencesInFile": 1,
   "line": 174,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
-  "seconds": 7.1,
+  "assertionFailures": [
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "killedByThrow": false,
+  "seconds": 15.0,
   "patch": {
    "old": "        if (evicted > 0) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);",
    "new": "        if (false) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "a923db6234a63a8730dcd07cd80c8ecb899eb29df79d70d33101933d6634dcad",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "a6634a090273811259bace4d90ee57ba3e21a6d1c95163c35314b48e3021a272",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "a923db6234a63a8730dcd07cd80c8ecb899eb29df79d70d33101933d6634dcad"
+    }
+   },
+   "outputSha256": "f854bec721cfb277654986e2611322318596ecee7f82577e66fda8979971c804",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.018 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:235 expected: <1> but was: <0>",
-    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:215 expected: <1> but was: <0>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.008 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <1> but was: <0>",
+    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:235 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -397,46 +530,65 @@
   "occurrencesInFile": 1,
   "line": 118,
   "enclosing": "private Outcome attempt(String topic) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
+   "FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts",
+   "FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows",
+   "FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne",
+   "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"
+  ],
+  "assertionFailures": [
+   "FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts",
+   "FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows",
+   "FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne",
+   "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"
+  ],
+  "anyFailures": [
    "FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts",
    "FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows",
    "FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne",
    "FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears"
   ],
-  "seconds": 6.5,
+  "killedByThrow": false,
+  "seconds": 14.7,
   "patch": {
    "old": "            else if (cfg.maxMessageBytes() > ceiling) o = new Outcome(false, Reason.ceiling,",
    "new": "            else if (cfg.maxMessageBytes() >= ceiling) o = new Outcome(false, Reason.ceiling,"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "10e91507bbd9c76a05f20ce70fd0d7e76f345dbeee0f0dfa8c79fd86e19317f1",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
-   "outputSha256": "5ce5359bd84839cf246261b845318609da9f204b41a66628ba733ab60bf7b159",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintTopicGate.java": {
+     "original": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
+     "mutated": "10e91507bbd9c76a05f20ce70fd0d7e76f345dbeee0f0dfa8c79fd86e19317f1"
+    }
+   },
+   "outputSha256": "277f025f4de88eaa80af6041bf18114d5c323fec2a3db5727b5508481e5cb8bf",
    "outputTail": "ed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.evidence ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes ceiling max.message.bytes=1048588 > 1048588\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.004 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.005 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.005 s <<< ERROR!",
     "[ERROR] app.feedgateway.FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.002 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.006 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.027 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts:68 the validated topic is added; the unvalidated one is WITHHELD from added() ==> expected: <[futures.footprint.bars-0]> but was: <[]>",
     "[ERROR]   FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows:106 expected: <[futures.cvd.bars-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd.bars-0]>",
     "[ERROR]   FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne:99 expected: <true> but was: <false>",
-    "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:141 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]>"
+    "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -450,33 +602,46 @@
   "occurrencesInFile": 1,
   "line": 119,
   "enclosing": "private Outcome attempt(String topic) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"
   ],
-  "seconds": 6.3,
+  "assertionFailures": [
+   "FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"
+  ],
+  "anyFailures": [
+   "FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"
+  ],
+  "killedByThrow": false,
+  "seconds": 13.2,
   "patch": {
    "old": "            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression,",
    "new": "            else if (false) o = new Outcome(false, Reason.compression,"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "88113e222a9afc61792000b26899359fc51afc02b5fac10a12f82c0cf8b52ede",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
-   "outputSha256": "4ecbe04d6678cc7e55ed251c844c33f649d7b5fcbae6e8f6240331190563809d",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintTopicGate.java": {
+     "original": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
+     "mutated": "88113e222a9afc61792000b26899359fc51afc02b5fac10a12f82c0cf8b52ede"
+    }
+   },
+   "outputSha256": "cef1375f965539a31a8e971756c5a9cbad17d03529079789308dac7934f04749",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:51 gzip ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown."
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -490,33 +655,46 @@
   "occurrencesInFile": 1,
   "line": 117,
   "enclosing": "private Outcome attempt(String topic) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"
   ],
-  "seconds": 7.8,
+  "assertionFailures": [
+   "FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"
+  ],
+  "anyFailures": [
+   "FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"
+  ],
+  "killedByThrow": false,
+  "seconds": 13.4,
   "patch": {
    "old": "            if (cfg == null) o = new Outcome(false, Reason.admin, \"no config returned\");",
    "new": "            if (cfg == null) o = new Outcome(false, Reason.admin, \"no config returned\");\n            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression, \"compression.type=\" + cfg.compressionType());"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "0f5935f573879932d3c401a5d1ad1589021d2a10ec5c4c6c0f814e71d53ff4b8",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
-   "outputSha256": "add52f3813ba0ea9294f0cc88bf96b0f64ad8820e4c42805421e13854cd70e37",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintTopicGate.java": {
+     "original": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
+     "mutated": "0f5935f573879932d3c401a5d1ad1589021d2a10ec5c4c6c0f814e71d53ff4b8"
+    }
+   },
+   "outputSha256": "c5ecc164370a40b1d40922ec6c6419ed151c9c03a3767ba38f9d1744057a930e",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.007 s <<< FAILURE!",
     "[ERROR]   FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt:62 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -530,39 +708,56 @@
   "occurrencesInFile": 1,
   "line": 229,
   "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
    "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
   ],
-  "seconds": 6.8,
+  "assertionFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
+   "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
+   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
+  ],
+  "anyFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
+   "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
+   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
+  ],
+  "killedByThrow": false,
+  "seconds": 16.3,
   "patch": {
    "old": "            String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));",
    "new": "            String hi = barKey(tf, Math.min(toMsInclusive - 1, EPOCH_MAX_MS));"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "f448803e303bdbdfaddcad7f8d1851a2cc77d9f21decc7d80ed25eea755d895a",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "6544b1eb728d89507a0cdfec131033c4d8fe1175ea6bcceedbc2e098cdf20154",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "f448803e303bdbdfaddcad7f8d1851a2cc77d9f21decc7d80ed25eea755d895a"
+    }
+   },
+   "outputSha256": "c4432011b7e9718f97ec9d8caf1756462aa3ba1a9e9de1ea1c481eab0ed5822f",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2>",
     "[ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false>",
-    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:175 expected: <3> but was: <2>"
+    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:195 expected: <3> but was: <2>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -576,33 +771,46 @@
   "occurrencesInFile": 1,
   "line": 130,
   "enclosing": "static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
-  "seconds": 6.7,
+  "assertionFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
+  ],
+  "anyFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
+  ],
+  "killedByThrow": false,
+  "seconds": 14.0,
   "patch": {
    "old": "    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
    "new": "    static int clamp(int limit) { return Math.min(limit, FOOTPRINT_LIMIT_MAX); }"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "0b7f3a2971dc69c3850ba867c539c6f2b6b17a8013993bc0a9d661ffb40162dd",
    "originalFileSha256": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
-   "outputSha256": "ce7258ca14ed99492f9d6997c75d3331d4cfc22d01bf881ec76e25bbfa528fec",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/GatewayController.java": {
+     "original": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
+     "mutated": "0b7f3a2971dc69c3850ba867c539c6f2b6b17a8013993bc0a9d661ffb40162dd"
+    }
+   },
+   "outputSha256": "7063959a040384f00635a3a5f606aa2b7bd69cfa31db98147dc89bc85fd13f68",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.008 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -616,33 +824,46 @@
   "occurrencesInFile": 1,
   "line": 130,
   "enclosing": "static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
-  "seconds": 7.2,
+  "assertionFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
+  ],
+  "anyFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
+  ],
+  "killedByThrow": false,
+  "seconds": 12.5,
   "patch": {
    "old": "    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
    "new": "    static int clamp(int limit) { return Math.max(1, limit); }"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "55236d83d718eff57ec004a64e589fa1637998835869484f7548cbc1aeeca9b9",
    "originalFileSha256": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
-   "outputSha256": "b82cb014dee75707da672126e398296b69ae8c177583155ae3d9c1bc6909ae2e",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/GatewayController.java": {
+     "original": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
+     "mutated": "55236d83d718eff57ec004a64e589fa1637998835869484f7548cbc1aeeca9b9"
+    }
+   },
+   "outputSha256": "f8f7a5f6a3a1f09c336daaa161af5068b59bdac33da639f4a42bc7e3fe691b11",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -656,33 +877,46 @@
   "occurrencesInFile": 1,
   "line": 329,
   "enclosing": "private static long epochField(JsonNode node, String field) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
-  "seconds": 6.9,
+  "assertionFailures": [
+   "FootprintViewsTest.shapeDropsNeverMutate"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.shapeDropsNeverMutate"
+  ],
+  "killedByThrow": false,
+  "seconds": 15.4,
   "patch": {
    "old": "        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;",
    "new": "        return x < 0 ? -1 : x;"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "e553a6d4e5b1827ccdc4191e78f47425f75f040f094bff517831a91eaf15a7f8",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "bf38f26e474f192379507aa23059a6a8f914666d746c40be72417f53b280420f",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "e553a6d4e5b1827ccdc4191e78f47425f75f040f094bff517831a91eaf15a7f8"
+    }
+   },
+   "outputSha256": "6e8b814a55fefdfa4348a7e6cc37a1a39f4c68638649d29a1f47f41a6bfc7666",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.012 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -696,33 +930,46 @@
   "occurrencesInFile": 1,
   "line": 226,
   "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   ],
-  "seconds": 6.6,
+  "assertionFailures": [
+   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
+  ],
+  "killedByThrow": false,
+  "seconds": 14.0,
   "patch": {
    "old": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {",
    "new": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0) {"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 1,
    "mutatedFileSha256": "bcdfcc3334a5212551a86cd937777facd6b5915b9f634abbb7a9a99469c0add6",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "40b17afb3151f8a30ce0a92f3f08dbfc80f45285d00cd74ff4f22a96d8539d3d",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "bcdfcc3334a5212551a86cd937777facd6b5915b9f634abbb7a9a99469c0add6"
+    }
+   },
+   "outputSha256": "f43f283b0677f488664f410bedacad5b143d1116bd4e6ab553181d0e20543aef",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.006 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:160 a cursor beyond the domain admits nothing \u2014 it must not wrap ==> expected: <0> but was: <4>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.013 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:180 a cursor beyond the domain admits nothing \u2014 it must not wrap ==> expected: <0> but was: <4>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  },
@@ -736,28 +983,957 @@
   "occurrencesInFile": 1,
   "line": 226,
   "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
-  "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [],
-  "seconds": 8.9,
+  "assertionFailures": [],
+  "anyFailures": [],
+  "killedByThrow": false,
+  "seconds": 18.0,
   "patch": {
    "old": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {",
    "new": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive <= EPOCH_MAX_MS) {"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
    "returnCode": 0,
    "mutatedFileSha256": "1805041d85205e5bad948989b9f83b1f4d321c9f9322dd96226cf8761ffb8058",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "outputSha256": "59f214184561400017cc7386388b711468a7e8f9f8552252a60babbfd684a3a5",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "1805041d85205e5bad948989b9f83b1f4d321c9f9322dd96226cf8761ffb8058"
+    }
+   },
+   "outputSha256": "e8c4c5929f9e45f8ba5dc31a88f0e29a403bcebf15780e06c8adca6f853afeec",
    "outputTail": "tures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n",
    "failureLines": [],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R1.1 the-flag-gates-every-footprint-object": {
+  "status": "KILLED",
+  "clause": "Flag off: the views are never instantiated",
+  "requirement": "G-R1",
+  "documentText": "Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{\"enabled\":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 809,
+  "enclosing": "public class FeedGatewayService implements ReplayRunner {",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing",
+   "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
+   "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions",
+   "FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge"
+  ],
+  "assertionFailures": [
+   "FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing",
+   "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
+   "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions",
+   "FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge"
+  ],
+  "anyFailures": [
+   "FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing",
+   "FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns",
+   "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
+   "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"
+  ],
+  "killedByThrow": false,
+  "seconds": 14.8,
+  "patch": {
+   "old": "        if (settings.esFootprintEnabled()) {",
+   "new": "        if (true) {"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "884cdb7be1fa827c909b4825d3adcbd701327d0771191be49d202c08cf1a7723",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "884cdb7be1fa827c909b4825d3adcbd701327d0771191be49d202c08cf1a7723"
+    }
+   },
+   "outputSha256": "242f19fec390c6e80eb699955d912c2cba43b349c879811699b2024eb8066d40",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.013 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.893 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.016 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.593 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401>",
+    "[ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123",
+    "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]>",
+    "[ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true>",
+    "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R2.1 one-wiring-method-binds-all-four": {
+  "status": "KILLED",
+  "clause": "ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers",
+  "requirement": "G-R2",
+  "documentText": "ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 2392,
+  "enclosing": "void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) { // package-private: the wiring test EXECUTES it",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
+  ],
+  "assertionFailures": [
+   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
+  ],
+  "anyFailures": [
+   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
+  ],
+  "killedByThrow": false,
+  "seconds": 14.3,
+  "patch": {
+   "old": "        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));",
+   "new": "        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817"
+    }
+   },
+   "outputSha256": "739db6f8dbbba0e58662028c7c67c5168a96969a630f9874d38c086d5ddb6098",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.008 s <<< FAILURE!",
+    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome, futures.footprint=es-footprint}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R2.2 the-evidence-binding": {
+  "status": "KILLED",
+  "clause": "KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)",
+  "requirement": "G-R2",
+  "documentText": "**Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` \u2192 `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` \u2192 `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` \u2192 `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` \u2192 `futures.footprint.outcomes` (`es-footprint-outcome`).",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 2390,
+  "enclosing": "void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) { // package-private: the wiring test EXECUTES it",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
+   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
+  ],
+  "assertionFailures": [
+   "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
+   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
+  ],
+  "anyFailures": [
+   "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
+   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
+  ],
+  "killedByThrow": false,
+  "seconds": 14.4,
+  "patch": {
+   "old": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-evidence\"));",
+   "new": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint\"));"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8"
+    }
+   },
+   "outputSha256": "8b1a81f52ffc9b414addcd13bfd7eeb1a0cc948bde6f042185e0f7589cd3b25d",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.016 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint\" ==> expected: <1> but was: <2>",
+    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome, futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R3.1 an-oversize-record-is-dropped-not-broadcast": {
+  "status": "KILLED",
+  "clause": "a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped",
+  "requirement": "G-R3",
+  "documentText": "`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) \u2014 a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 905,
+  "enclosing": "boolean admitFootprintRecord(String event, String json, String consumer) { // package-private: the wiring tests drive both consumer paths th",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "assertionFailures": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "anyFailures": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "killedByThrow": false,
+  "seconds": 11.6,
+  "patch": {
+   "old": "        return a.reason() != FootprintViews.Reason.OVERSIZE;",
+   "new": "        return true;"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "5134cf0f9808ec272649bb6533231d5dd0a952cd584dd200754d8f424af03125",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "5134cf0f9808ec272649bb6533231d5dd0a952cd584dd200754d8f424af03125"
+    }
+   },
+   "outputSha256": "e10e0391dae5739c8d30d93cb8b309147c1d9d101cd65f0dd7a1cd5d8db8bc33",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.187 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.014 s <<< FAILURE!",
+    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true>",
+    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R3.2 a-rejected-record-is-still-broadcast": {
+  "status": "KILLED",
+  "clause": "a record the view REJECTS for shape, session or size is still broadcast",
+  "requirement": "G-R3",
+  "documentText": "`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) \u2014 a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 905,
+  "enclosing": "boolean admitFootprintRecord(String event, String json, String consumer) { // package-private: the wiring tests drive both consumer paths th",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "assertionFailures": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "anyFailures": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "killedByThrow": false,
+  "seconds": 14.2,
+  "patch": {
+   "old": "        return a.reason() != FootprintViews.Reason.OVERSIZE;",
+   "new": "        return a.reason() == FootprintViews.Reason.ADMITTED;"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "66a9f89dfab6c87ccac729e9bcaf04b11ca59d06c4c08602d8ad7be5da36b8d1",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "66a9f89dfab6c87ccac729e9bcaf04b11ca59d06c4c08602d8ad7be5da36b8d1"
+    }
+   },
+   "outputSha256": "4c740a1ae388aa9230731cc646dfe4cf6fb9d75d0e8b443341c0628731e9b2ca",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.261 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.044 s <<< FAILURE!",
+    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false>",
+    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R3.3 live-snapshots-are-never-retained": {
+  "status": "KILLED",
+  "clause": "never cached, never retained",
+  "requirement": "G-R3",
+  "documentText": "**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every \u2264 5 s \u2014 F-R15 \u2014 so a new client waits at most one heartbeat; the same class as `es-cvd`).",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 6534,
+  "enclosing": "CachePolicy cachePolicyFor(String event, long nowMs) { // package-private: the retention test EXECUTES it",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"
+  ],
+  "assertionFailures": [
+   "FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"
+  ],
+  "anyFailures": [
+   "FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"
+  ],
+  "killedByThrow": false,
+  "seconds": 12.2,
+  "patch": {
+   "old": "            return CachePolicy.expiring(0L);",
+   "new": "            return CachePolicy.expiring(settings.esFootprintSeekBackMs());"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "606a7fa3701572b9a8978bf6c9c8a0a027c1e56e2dcca908b19ff84993edc72f",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "606a7fa3701572b9a8978bf6c9c8a0a027c1e56e2dcca908b19ff84993edc72f"
+    }
+   },
+   "outputSha256": "881517180e25eadc3861ad0db244238aaf023fd7408fc6b17a3707da92ba1314",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R5.1 the-outcome-key-orders-numerically": {
+  "status": "KILLED",
+  "clause": "the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)",
+  "requirement": "G-R5",
+  "documentText": "The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;",
+  "file": "src/main/java/app/feedgateway/FootprintViews.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 296,
+  "enclosing": "static String outcomeKey(String tf, long resolvedAtBarStartMs, String identity) {",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
+   "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"
+  ],
+  "assertionFailures": [
+   "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
+   "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"
+  ],
+  "anyFailures": [
+   "FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck",
+   "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
+   "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace",
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks"
+  ],
+  "killedByThrow": false,
+  "seconds": 12.0,
+  "patch": {
+   "old": "        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;",
+   "new": "        return tf + \"|\" + resolvedAtBarStartMs + \"|\" + identity;"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec",
+   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec"
+    }
+   },
+   "outputSha256": "b7b1bec4563801d4cd540e16e121abccadfac3a45c6f1f4de5a21f9e983d512e",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.086 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.003 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.002 s <<< ERROR!",
+    "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8>",
+    "[ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000>",
+    "[ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 \u00bb NumberFormat For input string: \"10|ES.v.0|1m|1|BUYE\"",
+    "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 \u00bb NumberFormat For input string: \"5|ES.v.0|1m|1|BUYER\"",
+    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:135 \u00bb NumberFormat For input string: \"77|ES.v.0|5m|1|SELL\"",
+    "[ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:211 \u00bb StringIndexOutOfBounds Range [3, 22) out of bounds for length 8"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R5.2 the-identity-is-the-last-component": {
+  "status": "KILLED",
+  "clause": "identity CONTAINS |, which is why it is the LAST component",
+  "requirement": "G-R5",
+  "documentText": "`identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).",
+  "file": "src/main/java/app/feedgateway/FootprintViews.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 296,
+  "enclosing": "static String outcomeKey(String tf, long resolvedAtBarStartMs, String identity) {",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
+   "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"
+  ],
+  "assertionFailures": [
+   "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
+   "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"
+  ],
+  "anyFailures": [
+   "FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck",
+   "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
+   "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace",
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks"
+  ],
+  "killedByThrow": false,
+  "seconds": 12.6,
+  "patch": {
+   "old": "        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;",
+   "new": "        return tf + \"|\" + identity + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs);"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "7a75b9af663605c79e061f5994d60b4ec216178fbe6ad27e44f8cc7f76c49e57",
+   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "7a75b9af663605c79e061f5994d60b4ec216178fbe6ad27e44f8cc7f76c49e57"
+    }
+   },
+   "outputSha256": "c65b18f783ea07033fa3e864db84929a18596d86e023dfbe815efb7c3f838972",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.132 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.006 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.006 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: \"id0|000000000000000\">",
+    "[ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005>",
+    "[ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\"",
+    "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\"",
+    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:135 \u00bb NumberFormat For input string: \"z|00000000000000000\"",
+    "[ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:211 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\""
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R5.3 a-foreign-identity-is-refused": {
+  "status": "KILLED",
+  "clause": "a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)",
+  "requirement": "G-R5",
+  "documentText": "Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.",
+  "file": "src/main/java/app/feedgateway/FootprintViews.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 125,
+  "enclosing": "|| !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintViewsTest.shapeDropsNeverMutate"
+  ],
+  "assertionFailures": [
+   "FootprintViewsTest.shapeDropsNeverMutate"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.shapeDropsNeverMutate"
+  ],
+  "killedByThrow": false,
+  "seconds": 13.2,
+  "patch": {
+   "old": "                || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {",
+   "new": "                || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "b41e5ceceaa36f9b18b60ab36542e40adda355dc0f933bbe8d3a92fb6cf1a31f",
+   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "b41e5ceceaa36f9b18b60ab36542e40adda355dc0f933bbe8d3a92fb6cf1a31f"
+    }
+   },
+   "outputSha256": "b6e6aa7b64a9d7dc729bf6bb6c26c182d0353d93345fad849bb4b3c790c0397c",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.shapeDropsNeverMutate:40 expected: <SHAPE> but was: <ADMITTED>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R5.4 last-write-per-key-wins": {
+  "status": "KILLED",
+  "clause": "Last write per full key wins; a redelivery carries the same key and overwrites in place",
+  "requirement": "G-R5",
+  "documentText": "Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.",
+  "file": "src/main/java/app/feedgateway/FootprintViews.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 132,
+  "enclosing": "Admission admitOutcome(String json) {",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"
+  ],
+  "assertionFailures": [
+   "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"
+  ],
+  "killedByThrow": false,
+  "seconds": 12.1,
+  "patch": {
+   "old": "            String previous = outcomes.put(key, json);",
+   "new": "            String previous = outcomes.putIfAbsent(key, json);"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "1a29ff784d9ae87c27fa6256fe064b480ac945623b6facdba5238d69fa17937f",
+   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "1a29ff784d9ae87c27fa6256fe064b480ac945623b6facdba5238d69fa17937f"
+    }
+   },
+   "outputSha256": "ff27fa1c7b3aa217c99f16c7bf96c0d5e9d7f8733d39da0be060a3ed787bde83",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.011 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R6.1 the-hello-carries-the-footprint-field": {
+  "status": "KILLED",
+  "clause": "the existing cvd-hello frame gains ONE field footprint",
+  "requirement": "G-R6",
+  "documentText": "**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 11180,
+  "enclosing": "StringBuilder sb = new StringBuilder(\"{\\\"sessionDate\\\":\");",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
+  ],
+  "assertionFailures": [
+   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
+  ],
+  "anyFailures": [
+   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
+  ],
+  "killedByThrow": false,
+  "seconds": 12.0,
+  "patch": {
+   "old": "            sb.append(\",\\\"footprint\\\":\").append(footprintViews.helloField());",
+   "new": "            sb.append(\",\\\"footprintX\\\":\").append(footprintViews.helloField());"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "5203835d0356ee0a9d2d478680c07baa48e3770b66cbe0c85d2a924d70865ae2",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "5203835d0356ee0a9d2d478680c07baa48e3770b66cbe0c85d2a924d70865ae2"
+    }
+   },
+   "outputSha256": "75afc52c137ae2ae6ca8e7de317a53229089347744f4fd4f7d6b6a4eee965221",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:152 expected: <{\"sessionDate\":null,\"hwm\":{},\"footprint\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}}> but was: <{\"sessionDate\":null,\"hwm\":{},\"footprintX\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}}>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R6.2 the-hello-is-sent-when-footprint-alone-is-on": {
+  "status": "KILLED",
+  "clause": "cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled",
+  "requirement": "G-R6",
+  "documentText": "`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 11158,
+  "enclosing": "boolean sendsCvdHello() {",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
+  ],
+  "assertionFailures": [
+   "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
+  ],
+  "anyFailures": [
+   "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
+  ],
+  "killedByThrow": false,
+  "seconds": 14.4,
+  "patch": {
+   "old": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;",
+   "new": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc"
+    }
+   },
+   "outputSha256": "ade0d8fe1385cf343f9966a1d9ee75d5ce916b82ff9985d9e6437783f9be2264",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.009 s <<< FAILURE!",
+    "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R6.3 the-hello-carries-the-outcome-high-water-mark": {
+  "status": "KILLED",
+  "clause": "outcomeHwm:{tf: maxResolvedAtBarStartMs}",
+  "requirement": "G-R6",
+  "documentText": "**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).",
+  "file": "src/main/java/app/feedgateway/FootprintViews.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 195,
+  "enclosing": "StringBuilder sb = new StringBuilder(\"{\\\"sessionDate\\\":\");",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
+   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
+  ],
+  "assertionFailures": [
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
+   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
+  ],
+  "anyFailures": [
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
+   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
+  ],
+  "killedByThrow": false,
+  "seconds": 13.5,
+  "patch": {
+   "old": "            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, outcomes, true);",
+   "new": "            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, bars, true);"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "368f116e662d3150e861190653954f413fbe2ce71254dec4501542935a35c416",
+   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "368f116e662d3150e861190653954f413fbe2ce71254dec4501542935a35c416"
+    }
+   },
+   "outputSha256": "6e95aab5783856ebbb58529f2b3760fd55991166d80055f952b39b5b8f43313b",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.008 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:135 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"30s\":2000,\"1m\":5}}>",
+    "[ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R10.1 a-footprint-record-never-escapes-the-consumer-loop": {
+  "status": "KILLED",
+  "clause": "a footprint record can never throw out of the consumer loop \u2014 the mutation makes it throw, and the test that calls it propagates that throw",
+  "requirement": "G-R10",
+  "documentText": "A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.",
+  "file": "src/main/java/app/feedgateway/FootprintViews.java",
+  "occurrence": 1,
+  "occurrencesInFile": 2,
+  "line": 93,
+  "enclosing": "Admission admitBar(String json) {",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintViewsTest.aNullPayloadIsARefusalNotAThrow"
+  ],
+  "assertionFailures": [],
+  "anyFailures": [
+   "FootprintViewsTest.aNullPayloadIsARefusalNotAThrow"
+  ],
+  "killedByThrow": true,
+  "seconds": 11.7,
+  "patch": {
+   "old": "        if (json == null) return Admission.SHAPE;",
+   "new": "        if (json == null) throw new IllegalArgumentException(\"json\");"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "5b915b8626df30f329a936e4fceef00fce3d32c66a3f820c7439a0cd381d7650",
+   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "5b915b8626df30f329a936e4fceef00fce3d32c66a3f820c7439a0cd381d7650"
+    }
+   },
+   "outputSha256": "084db191818cd59a03ca20597a180dcd4391069d0eb0625d625adea7eeb46233",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.003 s <<< ERROR!",
+    "[ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:82 \u00bb IllegalArgument json"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R10.2 every-admission-failure-is-a-counted-drop": {
+  "status": "KILLED",
+  "clause": "every admission failure is a counted drop",
+  "requirement": "G-R10",
+  "documentText": "A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 903,
+  "enclosing": "footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").in",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "assertionFailures": [
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "anyFailures": [
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "killedByThrow": false,
+  "seconds": 13.4,
+  "patch": {
+   "old": "            footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").incrementAndGet();",
+   "new": "            if (a.reason() != FootprintViews.Reason.SHAPE) footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").incrementAndGet();"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "0520888256109aba632dd9b7c1ae8a7df49b51fd61e33136f5a8b098fb1584c6",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "0520888256109aba632dd9b7c1ae8a7df49b51fd61e33136f5a8b098fb1584c6"
+    }
+   },
+   "outputSha256": "bebd93c1316001b203e5cb5eb2e42ae4680e1e35de90454731a8113996822610",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.335 s <<< FAILURE!",
+    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
+   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+  }
+ },
+ "G-R10.3 every-record-is-counted-before-admission": {
+  "status": "KILLED",
+  "clause": "incremented BEFORE admission (so it counts oversize/shape/stale records too)",
+  "requirement": "G-R10",
+  "documentText": "**Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's).",
+  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 897,
+  "enclosing": "footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "assertionFailures": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "anyFailures": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
+  ],
+  "killedByThrow": false,
+  "seconds": 12.9,
+  "patch": {
+   "old": "        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {",
+   "new": "        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();"
+  },
+  "evidence": {
+   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "returnCode": 1,
+   "mutatedFileSha256": "4282a98509424151e31ed2249590b999c4fa2311c784214ebb634aefa46406d3",
+   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FeedGatewayService.java": {
+     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
+     "mutated": "4282a98509424151e31ed2249590b999c4fa2311c784214ebb634aefa46406d3"
+    }
+   },
+   "outputSha256": "3bebb5d65d40c167829a4ff3cc2dfc4cd9776bc738a0d7c9018881d8a925d686",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.212 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.033 s <<< FAILURE!",
+    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false>",
+    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:168 expected: <true> but was: <false>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
+   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
   }
  }
diff --git a/ES-FOOTPRINT-GATEWAY-DESIGN.md b/ES-FOOTPRINT-GATEWAY-DESIGN.md
index 315acad..6f7448f 100644
--- a/ES-FOOTPRINT-GATEWAY-DESIGN.md
+++ b/ES-FOOTPRINT-GATEWAY-DESIGN.md
@@ -161,6 +161,41 @@ under last-write-per-key. Epoch-ms values lie in `[0, 253402300799999]` (§5 tim
 | G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
 | G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |
 
+<!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->
+
+## 2a. Conformance — what a test actually holds
+
+Every requirement above, and what this repository's mutation campaign established about it. The
+campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
+failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
+the spec declared in advance — never an incidental error under the right test's name; and the table
+below refuses to render a "pinned" cell for any record that does not carry its evidence. That
+evidence — the patch, the file, line and enclosing declaration, the command, the exit code, the
+verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
+this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
+record with `scripts/footprint-reqstate.sh --check`.
+
+| id | Conformance | Gate | Disposition |
+|----|-------------|------|-------------|
+| G-R1 | 1 of 1 clauses probed here are pinned | 2 | Flag and wiring |
+| G-R2 | 2 of 2 clauses probed here are pinned | 2 | Flag and wiring |
+| G-R3 | 3 of 3 clauses probed here are pinned | 2 | Delivery and views |
+| G-R4 | 4 of 4 clauses probed here are pinned | 2 | Delivery and views |
+| G-R5 | 4 of 4 clauses probed here are pinned | 2 | Delivery and views |
+| G-R6 | 3 of 3 clauses probed here are pinned | 2 | Hello |
+| G-R7 | 5 of 6 clauses probed here are pinned (1 survived) | 2 | Backfill routes |
+| G-R8 | 5 of 5 clauses probed here are pinned | 2 | Deployment contingency |
+| G-R9 | 1 of 1 clauses probed here are pinned | 2 | Metrics |
+| G-R10 | 3 of 3 clauses probed here are pinned | 2 | Non-interference |
+| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
+| G-R8a | 2 of 2 clauses probed here are pinned | 2 | Deployment contingency |
+
+12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).
+
+Read the state column narrowly. "n of m clauses probed here are pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.
+
+<!-- END footprint-reqstate -->
+
 ## 3. Decisions
 
 - **D1 No live retention.** `es-footprint`/`es-footprint-evidence` are not retained for connect
diff --git a/scripts/footprint-mutate.py b/scripts/footprint-mutate.py
index 9e77d4b..253cf26 100644
--- a/scripts/footprint-mutate.py
+++ b/scripts/footprint-mutate.py
@@ -14,7 +14,7 @@ how a campaign starts lying:
 A clean baseline is proven before any mutation is applied — a campaign against an already-red suite
 would report every clause as pinned.
 """
-import json, subprocess, sys, os, shutil, re, time, hashlib
+import json, subprocess, sys, os, shutil, signal, re, time, hashlib
 
 def failure_lines(out, kind):
     """The VERBATIM lines the killedBy names were parsed from, so the record substantiates itself.
@@ -23,6 +23,37 @@ def failure_lines(out, kind):
     pat = r'^\[ERROR\]\s+[\w.$]+\.[\w$]+(?::\d+|\s+--|\s).*$' if kind == 'maven' else r'^not ok \d+ - .*$'
     return [l.rstrip()[:400] for l in re.findall(pat, out, re.M)][:12]
 
+def assertion_failures(out, kind):
+    """Failures that are an ASSERTION, not an infrastructure error.
+
+    A mutation that makes the test class fail to construct, a format string blow up, or a fixture
+    throw, produces a non-zero exit and names the right test — and is NOT evidence that the clause
+    is held. One recorded kill in this campaign was exactly that: adding a `%s` to a format template
+    failed with MissingFormatArgument under the very test whose assertion was supposed to catch the
+    shared module. Surefire distinguishes `<<< FAILURE!` (assertion) from `<<< ERROR!` (threw);
+    node's TAP reports `code: 'ERR_ASSERTION'` for an assert and something else for a throw.
+    """
+    if kind == 'maven':
+        # the summary lines under "[ERROR] Failures:" are assertions; those under "[ERROR] Errors:"
+        # are throws. Walk the trailing summary and attribute each name to the section it is under.
+        names, section = set(), None
+        for line in out.split('\n'):
+            if re.match(r'^\[ERROR\]\s+Failures:\s*$', line): section = 'assert'; continue
+            if re.match(r'^\[ERROR\]\s+Errors:\s*$', line): section = 'error'; continue
+            if re.match(r'^\[ERROR\]\s+Tests run:', line): section = None; continue
+            m = re.match(r'^\[ERROR\]\s{2,}([\w.$]+\.[\w$]+)[:\s]', line)
+            if m and section == 'assert':
+                names.add('.'.join(m.group(1).split('.')[-2:]))
+        return sorted(names)
+    # node --test: a failing subtest block carries `code: 'ERR_ASSERTION'` for an assertion
+    names = set()
+    for block in re.split(r'^not ok \d+ - ', out, flags=re.M)[1:]:
+        head, rest = block.split('\n', 1) if '\n' in block else (block, '')
+        body = rest.split('\nnot ok ')[0].split('\nok ')[0]
+        if "ERR_ASSERTION" in body or "AssertionError" in body:
+            names.add(head.strip())
+    return sorted(names)
+
 def failing_tests(out, kind):
     if kind == 'maven':
         # Surefire's failure lines take two shapes:
@@ -58,10 +89,32 @@ def tree_dirty(root):
     rc, out = run(['git', 'status', '--porcelain'], root)
     return [l for l in out.split('\n') if l.strip()]
 
+# A campaign that is killed between the write and the restore leaves the tree mutated AND leaves
+# compiled classes built from the mutation. Restoring the source is not enough: `mv` puts back the
+# ORIGINAL mtime, so an incremental build keeps testing the mutant. Handle the signal, restore, and
+# discard the compiled output so the next run cannot inherit a mutant.
+_ACTIVE = {'paths': [], 'root': None}
+
+def _restore_and_exit(signum, frame):
+    for path in _ACTIVE['paths']:
+        if os.path.exists(path + '.bak'):
+            shutil.move(path + '.bak', path)
+            os.utime(path, None)
+    root = _ACTIVE['root']
+    if root:
+        for d, _, _ in list(os.walk(root)):
+            if os.path.basename(d) == 'classes' and os.path.basename(os.path.dirname(d)) == 'target':
+                shutil.rmtree(d, ignore_errors=True)
+    print(f"\ninterrupted by signal {signum}: tree restored and compiled classes discarded")
+    sys.exit(130)
+
 def main():
     spec = json.load(open(sys.argv[1]))
     outp = sys.argv[2]
     root, cmd, kind = spec['root'], spec['command'], spec['kind']
+    _ACTIVE['root'] = root
+    signal.signal(signal.SIGTERM, _restore_and_exit)
+    signal.signal(signal.SIGINT, _restore_and_exit)
     res = json.load(open(outp)) if os.path.exists(outp) else {}
 
     dirty = tree_dirty(root)
@@ -79,14 +132,30 @@ def main():
 
     for m in spec['mutations']:
         k = m['key']
-        if k in res: continue
-        f = os.path.join(root, m['file'])
+        # Resume only when the stored record was produced by THIS patch at THIS commit. Keying the
+        # skip on the name alone silently reuses a record for a patch that has since been edited,
+        # and the campaign then reports a result no run ever produced.
+        prev = res.get(k)
+        # A clause may be implemented at SEVERAL sites (a guard duplicated for defence in depth). One
+        # clause is still ONE mutation: `sites` breaks every site of the clause together, because
+        # breaking one site of a duplicated guard changes no behaviour and would be recorded as a
+        # survivor of a test that is in fact perfectly capable of catching the clause's removal.
+        sites = m.get('sites') or [{'file': m['file'], 'old': m['old'], 'new': m['new'],
+                                    'occurrence': m.get('occurrence', 1)}]
+        patch = {'sites': sites} if m.get('sites') else {'old': m['old'], 'new': m['new']}
+        if prev is not None and prev.get('patch') == patch \
+                and prev.get('evidence', {}).get('repoCommit') == commit:
+            continue
+        res.pop(k, None)
+        f = os.path.join(root, sites[0]['file'])
         src = open(f).read()
-        n = src.count(m['old'])
-        if n == 0:
-            res[k] = {'status': 'ANCHOR-MISSING', 'file': m['file']}
+        missing = [s0 for s0 in sites if open(os.path.join(root, s0['file'])).read().count(s0['old']) == 0]
+        if missing:
+            res[k] = {'status': 'ANCHOR-MISSING', 'file': missing[0]['file']}
             print(f"  {k:<40} ANCHOR-MISSING"); json.dump(res, open(outp,'w'), indent=1); continue
-        occ = m.get('occurrence', 1)
+        m = dict(m, old=sites[0]['old'], new=sites[0]['new'])
+        n = src.count(sites[0]['old'])
+        occ = sites[0].get('occurrence', 1)
         # locate the chosen occurrence
         pos, seen = -1, 0
         start = 0
@@ -121,23 +190,61 @@ def main():
             while stack and depth <= stack[-1][0]:
                 stack.pop()
 
-        shutil.copy(f, f + '.bak')
-        mutated = src[:pos] + m['new'] + src[pos+len(m['old']):]
+        touched = sorted({os.path.join(root, s0['file']) for s0 in sites})
+        _ACTIVE['paths'] = touched
+        originals, mutants = {}, {}
+        for path in touched:
+            shutil.copy(path, path + '.bak')
         t0 = time.time()
         try:
-            open(f, 'w').write(mutated)
+            for s0 in sites:
+                path = os.path.join(root, s0['file'])
+                text = open(path).read()
+                originals.setdefault(s0['file'], sha(text))
+                at, seen2, start2 = -1, 0, 0
+                while True:
+                    j = text.find(s0['old'], start2)
+                    if j < 0: break
+                    seen2 += 1
+                    if seen2 == s0.get('occurrence', 1): at = j; break
+                    start2 = j + 1
+                open(path, 'w').write(text[:at] + s0['new'] + text[at+len(s0['old']):])
+            for s0 in sites:
+                path = os.path.join(root, s0['file'])
+                mutants[s0['file']] = sha(open(path).read())
             rc, out = run(cmd, root)
         finally:
             # UNCONDITIONAL. A timeout, an interrupt or a crash between the write and the restore
             # would otherwise leave the tree mutated and poison every later mutation AND the baseline.
-            shutil.move(f + '.bak', f)
-            os.utime(f, None)   # mv restores the ORIGINAL mtime; Maven's incremental compiler would skip it
+            for path in touched:
+                shutil.move(path + '.bak', path)
+                os.utime(path, None)   # mv restores the ORIGINAL mtime; Maven would skip recompiling
+            _ACTIVE['paths'] = []
         still_dirty = tree_dirty(root)
         failed = failing_tests(out, kind)
+        asserted = assertion_failures(out, kind)
+        # A `kind` that does not match the runner parses no failure names, and every kill is then
+        # recorded as BUILD-FAILED — silently, because the run really did exit non-zero. Refuse
+        # instead: the output plainly names failing tests in the other runner's shape.
+        if rc != 0 and not failed:
+            other = failing_tests(out, 'node' if kind == 'maven' else 'maven')
+            if other:
+                print(f"KIND MISMATCH: kind={kind!r} parsed no failures, but the output names {other[:3]}")
+                print("  the spec's `kind` does not match the test runner; refusing to record a wrong verdict")
+                sys.exit(4)
         if build_failed(out, kind) and not failed:
             status = 'BUILD-FAILED'
-        elif rc != 0 and failed:
+        elif rc != 0 and asserted:
             status = 'KILLED'
+        elif rc != 0 and failed and m.get('killedByThrow'):
+            # The clause under test IS "this must not throw", so a test that propagates the throw is
+            # detecting exactly the right thing. The spec must SAY so in advance — declaring the
+            # detection mode after seeing the result is how a fixture blow-up gets read as a kill.
+            status = 'KILLED'
+        elif rc != 0 and failed:
+            # the right test failed, but on a THROW rather than an assertion, and the spec did not
+            # declare that mode: the mutation may have broken the fixture rather than the behaviour
+            status = 'KILLED-BY-ERROR'
         elif rc != 0:
             status = 'BUILD-FAILED'
         else:
@@ -145,11 +252,13 @@ def main():
         res[k] = {'status': status, 'clause': m.get('clause',''), 'requirement': m.get('requirement', k.split()[0]),
                   'documentText': m.get('documentText'),
                   'file': m['file'], 'occurrence': occ, 'occurrencesInFile': n, 'line': line,
-                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': failed[:4],
+                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': asserted[:4] or failed[:4], 'assertionFailures': asserted[:4], 'anyFailures': failed[:4],
+                  'killedByThrow': bool(m.get('killedByThrow')),
                   'seconds': round(time.time()-t0, 1),
-                  'patch': {'old': m['old'], 'new': m['new']},
+                  'patch': patch,
                   'evidence': {'repoCommit': commit, 'returnCode': rc,
-                               'mutatedFileSha256': sha(mutated), 'originalFileSha256': sha(src),
+                               'mutatedFileSha256': mutants.get(sites[0]['file']), 'originalFileSha256': originals.get(sites[0]['file']),
+                               'filesSha256': {p: {'original': originals[p], 'mutated': mutants.get(p)} for p in originals},
                                'outputSha256': sha(out), 'outputTail': out[-1200:],
                                'failureLines': failure_lines(out, kind),
                                'treeRestoredClean': not still_dirty},
diff --git a/scripts/footprint-reqstate.preamble b/scripts/footprint-reqstate.preamble
new file mode 100644
index 0000000..29cfb07
--- /dev/null
+++ b/scripts/footprint-reqstate.preamble
@@ -0,0 +1,9 @@
+Every requirement above, and what this repository's mutation campaign established about it. The
+campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
+failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
+the spec declared in advance — never an incidental error under the right test's name; and the table
+below refuses to render a "pinned" cell for any record that does not carry its evidence. That
+evidence — the patch, the file, line and enclosing declaration, the command, the exit code, the
+verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
+this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
+record with `scripts/footprint-reqstate.sh --check`.
diff --git a/scripts/footprint-reqstate.py b/scripts/footprint-reqstate.py
new file mode 100644
index 0000000..e9d069c
--- /dev/null
+++ b/scripts/footprint-reqstate.py
@@ -0,0 +1,85 @@
+#!/usr/bin/env python3
+"""Render the §9 requirement-reconciliation table from the CHECKED-IN campaign record.
+
+The state column is derived, never typed: a requirement is what its mutations say it is. A
+requirement the campaign did not probe says exactly that, and claims nothing else — the failure mode
+this replaces is a hand-maintained table drifting away from the code it claims to describe.
+"""
+import json, re, sys, collections
+
+def main():
+    recordPath, docPath, idPattern, gates, dispositions = sys.argv[1], sys.argv[2], sys.argv[3], json.loads(sys.argv[4]), json.loads(sys.argv[5])
+    # Requirements whose subject is not production behaviour (a test inventory) or whose behaviour
+    # lives in ANOTHER repository. Neither can be probed here, and neither is a gap — but saying
+    # "NOT PROBED" without saying why invites the reader to assume it is one.
+    notes = json.loads(sys.argv[6]) if len(sys.argv) > 6 else {}
+    rec = json.load(open(recordPath))
+    ids = []
+    for line in open(docPath):
+        m = re.match(r'^\|\s*(' + idPattern + r')\s*\|', line)
+        if m and m.group(1) not in ids: ids.append(m.group(1))
+    per = collections.defaultdict(list)
+    for k, v in rec.items():
+        per[v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', k).group(1)].append(v)
+    # A record is only allowed to say "pinned" when it carries the evidence for it: an assertion
+    # failure naming a test, and the verbatim failure lines that name was parsed from. A record that
+    # merely says KILLED is a claim, not evidence — and one kill in this campaign turned out to be a
+    # format-string error under the right test's name, which is exactly what this refuses.
+    problems = []
+    for rid, ms in per.items():
+        for m in ms:
+            if m['status'] != 'KILLED':
+                continue
+            if not m.get('assertionFailures') and not m.get('killedBy'):
+                problems.append(f"{rid}: a KILLED record names no failing test")
+            if not m.get('evidence', {}).get('failureLines'):
+                problems.append(f"{rid}: a KILLED record carries no failure lines")
+            if not m.get('evidence', {}).get('treeRestoredClean', True):
+                problems.append(f"{rid}: a KILLED record ran against a tree it did not restore")
+    if problems:
+        print("REFUSING TO EMIT:", file=sys.stderr)
+        for p in problems: print("  ", p, file=sys.stderr)
+        sys.exit(1)
+
+    def gate(rid):
+        for pat, g in gates.items():
+            if re.fullmatch('(?:' + pat + ')', rid): return g
+        return '—'
+    def disp(rid):
+        for pat, d in dispositions.items():
+            if re.fullmatch('(?:' + pat + ')', rid): return d
+        return ''
+    out = ["| id | Conformance | Gate | Disposition |", "|----|-------------|------|-------------|"]
+    for rid in sorted(ids, key=lambda x: (re.sub(r'\d', '', x), int(re.search(r'\d+', x).group()), x)):
+        ms = per.get(rid, [])
+        if not ms:
+            # A note is EDITORIAL: it says why this requirement has no mutations, and is marked as
+            # such so it can never be read as a campaign result.
+            state = (notes[rid] + " (not probed)") if rid in notes else "NOT PROBED"
+        else:
+            by = collections.Counter(m['status'] for m in ms)
+            killed = by.pop('KILLED', 0)
+            state = f"{killed} of {len(ms)} clauses probed here are pinned"
+            rest = ", ".join(f"{n} {st.lower()}" for st, n in sorted(by.items()))
+            if rest: state += f" ({rest})"
+        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
+    tot = collections.Counter(v['status'] for v in rec.values())
+    out.append("")
+    probed_here = len([r for r in ids if r in per])
+    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rec)} mutations "
+               f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving"
+               + (", " + ", ".join(f"{n} {st.lower()}" for st, n in sorted(tot.items()) if st not in ('KILLED', 'SURVIVED')) if len(tot) > 2 else "")
+               + ")."
+               "\n\nRead the state column narrowly. \"n of m clauses probed here are pinned\" says that "
+               "breaking those clauses in the production source made a NAMED test fail an ASSERTION — "
+               "it does NOT say the requirement as a whole is held, because a requirement usually has "
+               "more clauses than this campaign broke. \"NOT PROBED\" means this campaign did not test "
+               "it and claims nothing either way; where a note appears beside it, that note is "
+               "editorial and is not a campaign result. Evidence, per mutation — the patch, the file, "
+               "line and enclosing declaration, the command, the exit code, the verbatim failure "
+               "lines and a SHA-256 of the run output — is in the campaign record beside this "
+               "document, and this table refuses to render a \"pinned\" cell for any record that does "
+               "not carry it.")
+    print("\n".join(out))
+
+main()
diff --git a/scripts/footprint-reqstate.sh b/scripts/footprint-reqstate.sh
new file mode 100755
index 0000000..dd365f3
--- /dev/null
+++ b/scripts/footprint-reqstate.sh
@@ -0,0 +1,59 @@
+#!/usr/bin/env bash
+# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md IN PLACE from the checked-in campaign record.
+#
+# It rewrites the block between the BEGIN/END markers and leaves everything else byte-identical, so
+# running it is the whole update: an earlier version of this script only printed to stdout while the
+# document claimed it regenerated the section, which is a claim the script could not keep.
+#
+# --check exits non-zero if the committed section differs from what the record produces, so CI can
+# hold the document to its own record.
+set -euo pipefail
+cd "$(dirname "$0")/.."
+
+DOC=ES-FOOTPRINT-GATEWAY-DESIGN.md
+BEGIN='<!-- BEGIN footprint-reqstate'
+END='<!-- END footprint-reqstate -->'
+
+table() {
+  python3 scripts/footprint-reqstate.py \
+    ES-FOOTPRINT-CAMPAIGN.json \
+    "$DOC" \
+    'G-R\d+[a-z]?' \
+    '{"G-R.*":"2"}' \
+    '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
+    '{"G-R11":"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break"}'
+}
+
+grep -qF "$BEGIN" "$DOC" || { echo "FATAL: $DOC has no $BEGIN marker" >&2; exit 1; }
+grep -qF "$END"   "$DOC" || { echo "FATAL: $DOC has no $END marker" >&2; exit 1; }
+
+tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
+table > "$tmp/table.md"
+
+# Rebuild the document: everything before the marker, the fixed prose, the generated table, the
+# fixed trailer, everything after. The prose lives HERE so the generated part is only the table.
+awk -v tablefile="$tmp/table.md" -v begin="$BEGIN" -v end="$END" '
+  index($0, begin) == 1 { skipping = 1; print; print ""; print "## 2a. Conformance — what a test actually holds"; print "";
+      while ((getline line < "scripts/footprint-reqstate.preamble") > 0) print line
+      close("scripts/footprint-reqstate.preamble")
+      print ""
+      while ((getline line < tablefile) > 0) print line
+      close(tablefile)
+      print ""
+      next }
+  index($0, end) == 1 { skipping = 0 }
+  !skipping { print }
+' "$DOC" > "$tmp/doc.md"
+
+if [ "${1:-}" = "--check" ]; then
+  if ! diff -q "$DOC" "$tmp/doc.md" >/dev/null; then
+    echo "§2a is stale: regenerate with scripts/footprint-reqstate.sh" >&2
+    diff -u "$DOC" "$tmp/doc.md" | head -60 >&2
+    exit 1
+  fi
+  echo "§2a matches the campaign record"
+  exit 0
+fi
+
+mv "$tmp/doc.md" "$DOC"
+echo "§2a regenerated in $DOC"
diff --git a/src/main/java/app/feedgateway/FeedGatewayService.java b/src/main/java/app/feedgateway/FeedGatewayService.java
index d242d5c..ef4e221 100644
--- a/src/main/java/app/feedgateway/FeedGatewayService.java
+++ b/src/main/java/app/feedgateway/FeedGatewayService.java
@@ -1192,7 +1192,7 @@ public class FeedGatewayService implements ReplayRunner {
         clients.add(session);
         clientsById.put(session.getId(), session);
         send(session, "status", statusJson());
-        if (settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null) {
+        if (sendsCvdHello()) {
             // R46 hello: the per-timeframe high-water marks of the bar view, so the page can bound
             // its REST backfill to exactly what this gateway holds and buffer WS bars past it.
             // U16 (CL-R8/G19): the latest ACCEPTED levels record rides INSIDE this same hello, so
@@ -2384,7 +2384,7 @@ public class FeedGatewayService implements ReplayRunner {
      * topic set always contains them and discovers them whenever they appear; consumption is gated
      * per topic by {@link FootprintTopicGate#admit} at bootstrap and inside every refresh.
      */
-    private void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {
+    void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {   // package-private: the wiring test EXECUTES it
         if (footprintViews == null) return;
         topicEvents.put(settings.esFootprintTopic(), new TopicBinding("DATABENTO", "es-footprint"));
         topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding("DATABENTO", "es-footprint-evidence"));
@@ -6485,7 +6485,7 @@ public class FeedGatewayService implements ReplayRunner {
      * (off-hours, so the published strike structure persists) while {@code seekBackMs} keeps the Kafka
      * cache-rebuild window BOUNDED regardless — the two concerns are deliberately decoupled.
      */
-    private record CachePolicy(long ttlMs, boolean neverEvict, long seekBackMs) {
+    record CachePolicy(long ttlMs, boolean neverEvict, long seekBackMs) {   // package-private: the retention test reads it
         static CachePolicy expiring(long ttlMs) {
             return new CachePolicy(ttlMs, false, ttlMs);
         }
@@ -6523,7 +6523,7 @@ public class FeedGatewayService implements ReplayRunner {
         return override != null ? override : marketCalendar.isRegularTradingHours(Instant.ofEpochMilli(nowMs));
     }
 
-    private CachePolicy cachePolicyFor(String event, long nowMs) {
+    CachePolicy cachePolicyFor(String event, long nowMs) {   // package-private: the retention test EXECUTES it
         if ("es-footprint-bar".equals(event) || "es-footprint-outcome".equals(event)) {
             // ES Footprint keyed topics: the cache consumer seeks back a whole session so a restart
             // re-fills both views from the compacted topics; the records never enter the generic cache.
@@ -11153,6 +11153,11 @@ public class FeedGatewayService implements ReplayRunner {
     }
 
     /** R46 hello payload: {"sessionDate":...,"hwm":{"30s":<lastBarStartMs>,...}}. */
+    /** G-R6: the cvd-hello frame is sent whenever CVD, SPX levels OR footprint is enabled. */
+    boolean sendsCvdHello() {
+        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;
+    }
+
     String cvdHelloJson() {
         StringBuilder sb = new StringBuilder("{\"sessionDate\":");
         String sd = cvdBarsSessionDate;
diff --git a/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java b/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
index f64802f..9ba9b15 100644
--- a/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
+++ b/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
@@ -329,8 +329,11 @@ class CvdSpxLevelsWiringTest {
         int addClient = source.indexOf("public void addClient(WebSocketSession session)");
         int replayEnd = source.indexOf("if (perSessionRouting())", addClient);
         String head = source.substring(addClient, replayEnd);
-        assertTrue(head.contains("esCvdSpxLevelsEnabled()") && head.contains("send(session, \"cvd-hello\""),
-                "the hello is sent when EITHER CVD flag is on, for both routing modes");
+        assertTrue(head.contains("sendsCvdHello()") && head.contains("send(session, \"cvd-hello\""),
+                "the hello send is guarded by the ONE predicate, for both routing modes");
+        int pred = source.indexOf("boolean sendsCvdHello() {");
+        assertTrue(pred > 0 && source.substring(pred, source.indexOf("\n    }", pred)).contains("esCvdSpxLevelsEnabled()"),
+                "and that predicate still admits the SPX-levels flag");
         assertFalse(head.contains("send(session, \"es-cvd-spx-levels\""),
                 "a separate connect frame would reintroduce the ambiguity G19 forbids");
     }
diff --git a/src/test/java/app/feedgateway/FootprintViewsTest.java b/src/test/java/app/feedgateway/FootprintViewsTest.java
index 3b666c5..6e0f638 100644
--- a/src/test/java/app/feedgateway/FootprintViewsTest.java
+++ b/src/test/java/app/feedgateway/FootprintViewsTest.java
@@ -65,6 +65,26 @@ class FootprintViewsTest {
                 FootprintViews.outcomeKey("1m", 5, "ES.v.0|1m|1|BUYERS|LEVEL|650000|650000"));
     }
 
+    /** G-R5: an identity resolves exactly once upstream, so a redelivery must OVERWRITE in place. */
+    @Test void anOutcomeRedeliveryOverwritesItsKeyInPlace() {
+        FootprintViews v = views();
+        String id = "ES.v.0|1m|1|BUYERS|LEVEL|650000|650000";
+        assertEquals(FootprintViews.Reason.ADMITTED, v.admitOutcome(outcome("2026-08-14", "1m", 5, id, "first")).reason());
+        assertEquals(FootprintViews.Reason.ADMITTED, v.admitOutcome(outcome("2026-08-14", "1m", 5, id, "second")).reason());
+        assertEquals(1, v.outcomesInView(), "one identity, one row");
+        assertTrue(v.outcomesPage("1m", Long.MAX_VALUE, "", 10, "").records().get(0).contains("\"tag\":\"second\""),
+                "the redelivery is the row that survives");
+    }
+
+    /** G-R10: no admission failure escapes the consumer loop — a null payload is a counted drop. */
+    @Test void aNullPayloadIsARefusalNotAThrow() {
+        FootprintViews v = views();
+        assertEquals(FootprintViews.Reason.SHAPE, v.admitBar(null).reason());
+        assertEquals(FootprintViews.Reason.SHAPE, v.admitOutcome(null).reason());
+        assertEquals(0, v.barsInView());
+        assertEquals(0, v.outcomesInView());
+    }
+
     // ---- G-R4 rollover: one coordinator, both views, strict dates ------------------------------
 
     @Test void aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts() {
diff --git a/src/test/java/app/feedgateway/FootprintWiringTest.java b/src/test/java/app/feedgateway/FootprintWiringTest.java
index 7c26e3f..d40d839 100644
--- a/src/test/java/app/feedgateway/FootprintWiringTest.java
+++ b/src/test/java/app/feedgateway/FootprintWiringTest.java
@@ -42,7 +42,7 @@ class FootprintWiringTest {
         String src = Files.readString(SERVICE);
         assertEquals(2, occurrences(src, "addEsFootprintTopics(topicEvents);"), "called from the state cache AND the state live consumer");
         assertEquals(2, occurrences(src, "addEsCvdTopics(topicEvents);\n        addEsFootprintTopics(topicEvents);"), "right after the CVD wiring at both sites");
-        int start = src.indexOf("private void addEsFootprintTopics(");
+        int start = src.indexOf("void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {");
         String body = src.substring(start, src.indexOf("\n    }", start));
         assertTrue(body.contains("if (footprintViews == null) return;"), "flag off adds nothing");
         for (String e : new String[]{"es-footprint\"", "es-footprint-evidence\"", "es-footprint-bar\"", "es-footprint-outcome\""}) {
@@ -100,6 +100,51 @@ class FootprintWiringTest {
         assertTrue(s.metrics().contains("gateway_footprint_enabled 0\n"));
     }
 
+    /**
+     * G-R2: the four bindings are asserted by EXECUTING the wiring, not by reading its source. A
+     * source assertion counts the text of a {@code TopicBinding} that a surrounding {@code if (false)}
+     * has made unreachable; only running the method can tell the two apart.
+     */
+    @Test void theWiringMethodPutsAllFourBindingsInTheMapItIsGiven() {
+        java.util.Map<String, FeedGatewayService.TopicBinding> wired = new java.util.LinkedHashMap<>();
+        on().addEsFootprintTopics(wired);
+        GatewaySettings g = new GatewaySettings();
+        assertEquals(java.util.Map.of(
+                g.esFootprintTopic(), "es-footprint",
+                g.esFootprintEvidenceTopic(), "es-footprint-evidence",
+                g.esFootprintBarsTopic(), "es-footprint-bar",
+                g.esFootprintOutcomesTopic(), "es-footprint-outcome"),
+                wired.entrySet().stream().collect(java.util.stream.Collectors.toMap(
+                        java.util.Map.Entry::getKey, e -> e.getValue().event())),
+                "every topic is bound, to its own event");
+        wired.values().forEach(b -> assertEquals("DATABENTO", b.source()));
+        java.util.Map<String, FeedGatewayService.TopicBinding> none = new java.util.LinkedHashMap<>();
+        off().addEsFootprintTopics(none);
+        assertTrue(none.isEmpty(), "flag off adds nothing");
+    }
+
+    /** G-R3: the two live snapshot topics are never retained; the two keyed topics seek back a session. */
+    @Test void liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession() {
+        FeedGatewayService s = on();
+        long back = new GatewaySettings().esFootprintSeekBackMs();
+        assertTrue(back > 0, "the keyed seek-back is a real window");
+        for (String live : new String[]{"es-footprint", "es-footprint-evidence"}) {
+            assertEquals(0L, s.cachePolicyFor(live, 0L).ttlMs(), live + " is never retained");
+        }
+        for (String keyed : new String[]{"es-footprint-bar", "es-footprint-outcome"}) {
+            assertEquals(back, s.cachePolicyFor(keyed, 0L).ttlMs(), keyed + " re-fills from the compacted topic");
+        }
+    }
+
+    /** G-R6: footprint alone is enough to send the hello — the page needs the handshake either way. */
+    @Test void theHelloIsSentWhenFootprintAloneIsEnabled() {
+        GatewaySettings g = new GatewaySettings();
+        assertFalse(g.esCvdEnabled(), "the fixture isolates the footprint disjunct");
+        assertFalse(g.esCvdSpxLevelsEnabled());
+        assertTrue(on().sendsCvdHello(), "footprint alone still sends cvd-hello");
+        assertFalse(off().sendsCvdHello(), "and nothing enabled sends none");
+    }
+
     // ---- G-R6 hello ---------------------------------------------------------------------------------
 
     @Test void flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot() {

2026-09-08T20:24:58.980087Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
The branch is clean and consists of seven commits over `origin/main`. I’m narrowing the review to the actual changed files now; the combined diff is large because the campaign record was regenerated.
exec
/bin/bash -lc 'git diff --name-status origin/main...HEAD; rg -n "killedByThrow|KILLED-BY-ERROR|assertionFailures|failureLines|G-R10" scripts ES-FOOTPRINT-CAMPAIGN.json ES-FOOTPRINT-GATEWAY-DESIGN.md src/test 2>/dev/null' in /private/tmp/oe-conf-gw
 succeeded in 3821ms:
2026-09-08 22:25:03.680 xcodebuild[24234:266020131]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:25:04.101 xcodebuild[24234:266020128] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-FKKqC99F' (errno=Operation not permitted)
2026-09-08 22:25:05.182 xcodebuild[24270:266020275]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:25:05.540 xcodebuild[24270:266020274] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
M	ES-FOOTPRINT-CAMPAIGN.json
M	ES-FOOTPRINT-GATEWAY-DESIGN.md
M	scripts/footprint-mutate.py
A	scripts/footprint-reqstate.preamble
A	scripts/footprint-reqstate.py
A	scripts/footprint-reqstate.sh
M	src/main/java/app/feedgateway/FeedGatewayService.java
M	src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
M	src/test/java/app/feedgateway/FootprintViewsTest.java
M	src/test/java/app/feedgateway/FootprintWiringTest.java
ES-FOOTPRINT-GATEWAY-DESIGN.md:107:| 5 | Acceptance suite incomplete | G-R11 enumerates a brokerless test per clause of G-R1–G-R10, including per-session fan-out |
ES-FOOTPRINT-GATEWAY-DESIGN.md:161:| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
ES-FOOTPRINT-GATEWAY-DESIGN.md:189:| G-R10 | 3 of 3 clauses probed here are pinned | 2 | Non-interference |
ES-FOOTPRINT-CAMPAIGN.json:19:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:31:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:50:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:84:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:90:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:109:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:140:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:152:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:171:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:209:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:215:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:234:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:263:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:271:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:290:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:320:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:326:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:345:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:373:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:379:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:398:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:426:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:432:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:451:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:480:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:488:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:507:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:540:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:552:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:571:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:609:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:615:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:634:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:662:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:668:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:687:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:717:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:727:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:746:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:778:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:784:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:803:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:831:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:837:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:856:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:884:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:890:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:909:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:937:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:943:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:962:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:988:  "assertionFailures": [],
ES-FOOTPRINT-CAMPAIGN.json:990:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1009:   "failureLines": [],
ES-FOOTPRINT-CAMPAIGN.json:1037:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1049:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1068:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1106:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1112:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1131:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1160:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1168:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1187:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1218:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1226:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1245:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1276:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1284:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1303:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1333:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1339:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1358:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1387:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1397:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1416:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1455:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1465:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1484:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1522:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1528:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1547:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1575:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1581:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1600:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1628:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1634:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1653:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1681:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1687:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1706:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1735:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1743:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1762:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1778: "G-R10.1 a-footprint-record-never-escapes-the-consumer-loop": {
ES-FOOTPRINT-CAMPAIGN.json:1781:  "requirement": "G-R10",
ES-FOOTPRINT-CAMPAIGN.json:1792:  "assertionFailures": [],
ES-FOOTPRINT-CAMPAIGN.json:1796:  "killedByThrow": true,
ES-FOOTPRINT-CAMPAIGN.json:1815:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1829: "G-R10.2 every-admission-failure-is-a-counted-drop": {
ES-FOOTPRINT-CAMPAIGN.json:1832:  "requirement": "G-R10",
ES-FOOTPRINT-CAMPAIGN.json:1843:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1849:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1868:   "failureLines": [
ES-FOOTPRINT-CAMPAIGN.json:1882: "G-R10.3 every-record-is-counted-before-admission": {
ES-FOOTPRINT-CAMPAIGN.json:1885:  "requirement": "G-R10",
ES-FOOTPRINT-CAMPAIGN.json:1897:  "assertionFailures": [
ES-FOOTPRINT-CAMPAIGN.json:1905:  "killedByThrow": false,
ES-FOOTPRINT-CAMPAIGN.json:1924:   "failureLines": [
scripts/footprint-mutate.py:239:        elif rc != 0 and failed and m.get('killedByThrow'):
scripts/footprint-mutate.py:247:            status = 'KILLED-BY-ERROR'
scripts/footprint-mutate.py:255:                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': asserted[:4] or failed[:4], 'assertionFailures': asserted[:4], 'anyFailures': failed[:4],
scripts/footprint-mutate.py:256:                  'killedByThrow': bool(m.get('killedByThrow')),
scripts/footprint-mutate.py:263:                               'failureLines': failure_lines(out, kind),
scripts/footprint-reqstate.sh:23:    '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
scripts/footprint-reqstate.py:33:            if not m.get('assertionFailures') and not m.get('killedBy'):
scripts/footprint-reqstate.py:35:            if not m.get('evidence', {}).get('failureLines'):
src/test/java/app/feedgateway/FootprintViewsTest.java:79:    /** G-R10: no admission failure escapes the consumer loop — a null payload is a counted drop. */
src/test/java/app/feedgateway/FootprintWiringTest.java:14: * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R1/G-R2/G-R3/G-R6/G-R9/G-R10/G-R11 — the wiring-shape pins, the
src/test/java/app/feedgateway/FootprintWiringTest.java:90:    // ---- G-R1/G-R10: flag off is byte-identical ----------------------------------------------------

2026-09-08T20:25:07.283995Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "sed -n '1,330p' scripts/footprint-mutate.py; sed -n '1750,1950p' ES-FOOTPRINT-CAMPAIGN.json; sed -n '130,215p' ES-FOOTPRINT-GATEWAY-DESIGN.md; git diff origin/main...HEAD -- src/test/java/app/feedgateway/FootprintWiringTest.java scripts/footprint-mutate.py | sed -n '1,500p'" in /private/tmp/oe-conf-gw
 succeeded in 4102ms:
2026-09-08 22:25:11.849 xcodebuild[24437:266020893]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:25:12.479 xcodebuild[24437:266020891] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-9hCtzEOn' (errno=Operation not permitted)
2026-09-08 22:25:13.450 xcodebuild[24479:266021104]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:25:13.815 xcodebuild[24479:266021103] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
diff --git a/scripts/footprint-mutate.py b/scripts/footprint-mutate.py
index 9e77d4b..253cf26 100644
--- a/scripts/footprint-mutate.py
+++ b/scripts/footprint-mutate.py
@@ -14,7 +14,7 @@ how a campaign starts lying:
 A clean baseline is proven before any mutation is applied — a campaign against an already-red suite
 would report every clause as pinned.
 """
-import json, subprocess, sys, os, shutil, re, time, hashlib
+import json, subprocess, sys, os, shutil, signal, re, time, hashlib
 
 def failure_lines(out, kind):
     """The VERBATIM lines the killedBy names were parsed from, so the record substantiates itself.
@@ -23,6 +23,37 @@ def failure_lines(out, kind):
     pat = r'^\[ERROR\]\s+[\w.$]+\.[\w$]+(?::\d+|\s+--|\s).*$' if kind == 'maven' else r'^not ok \d+ - .*$'
     return [l.rstrip()[:400] for l in re.findall(pat, out, re.M)][:12]
 
+def assertion_failures(out, kind):
+    """Failures that are an ASSERTION, not an infrastructure error.
+
+    A mutation that makes the test class fail to construct, a format string blow up, or a fixture
+    throw, produces a non-zero exit and names the right test — and is NOT evidence that the clause
+    is held. One recorded kill in this campaign was exactly that: adding a `%s` to a format template
+    failed with MissingFormatArgument under the very test whose assertion was supposed to catch the
+    shared module. Surefire distinguishes `<<< FAILURE!` (assertion) from `<<< ERROR!` (threw);
+    node's TAP reports `code: 'ERR_ASSERTION'` for an assert and something else for a throw.
+    """
+    if kind == 'maven':
+        # the summary lines under "[ERROR] Failures:" are assertions; those under "[ERROR] Errors:"
+        # are throws. Walk the trailing summary and attribute each name to the section it is under.
+        names, section = set(), None
+        for line in out.split('\n'):
+            if re.match(r'^\[ERROR\]\s+Failures:\s*$', line): section = 'assert'; continue
+            if re.match(r'^\[ERROR\]\s+Errors:\s*$', line): section = 'error'; continue
+            if re.match(r'^\[ERROR\]\s+Tests run:', line): section = None; continue
+            m = re.match(r'^\[ERROR\]\s{2,}([\w.$]+\.[\w$]+)[:\s]', line)
+            if m and section == 'assert':
+                names.add('.'.join(m.group(1).split('.')[-2:]))
+        return sorted(names)
+    # node --test: a failing subtest block carries `code: 'ERR_ASSERTION'` for an assertion
+    names = set()
+    for block in re.split(r'^not ok \d+ - ', out, flags=re.M)[1:]:
+        head, rest = block.split('\n', 1) if '\n' in block else (block, '')
+        body = rest.split('\nnot ok ')[0].split('\nok ')[0]
+        if "ERR_ASSERTION" in body or "AssertionError" in body:
+            names.add(head.strip())
+    return sorted(names)
+
 def failing_tests(out, kind):
     if kind == 'maven':
         # Surefire's failure lines take two shapes:
@@ -58,10 +89,32 @@ def tree_dirty(root):
     rc, out = run(['git', 'status', '--porcelain'], root)
     return [l for l in out.split('\n') if l.strip()]
 
+# A campaign that is killed between the write and the restore leaves the tree mutated AND leaves
+# compiled classes built from the mutation. Restoring the source is not enough: `mv` puts back the
+# ORIGINAL mtime, so an incremental build keeps testing the mutant. Handle the signal, restore, and
+# discard the compiled output so the next run cannot inherit a mutant.
+_ACTIVE = {'paths': [], 'root': None}
+
+def _restore_and_exit(signum, frame):
+    for path in _ACTIVE['paths']:
+        if os.path.exists(path + '.bak'):
+            shutil.move(path + '.bak', path)
+            os.utime(path, None)
+    root = _ACTIVE['root']
+    if root:
+        for d, _, _ in list(os.walk(root)):
+            if os.path.basename(d) == 'classes' and os.path.basename(os.path.dirname(d)) == 'target':
+                shutil.rmtree(d, ignore_errors=True)
+    print(f"\ninterrupted by signal {signum}: tree restored and compiled classes discarded")
+    sys.exit(130)
+
 def main():
     spec = json.load(open(sys.argv[1]))
     outp = sys.argv[2]
     root, cmd, kind = spec['root'], spec['command'], spec['kind']
+    _ACTIVE['root'] = root
+    signal.signal(signal.SIGTERM, _restore_and_exit)
+    signal.signal(signal.SIGINT, _restore_and_exit)
     res = json.load(open(outp)) if os.path.exists(outp) else {}
 
     dirty = tree_dirty(root)
@@ -79,14 +132,30 @@ def main():
 
     for m in spec['mutations']:
         k = m['key']
-        if k in res: continue
-        f = os.path.join(root, m['file'])
+        # Resume only when the stored record was produced by THIS patch at THIS commit. Keying the
+        # skip on the name alone silently reuses a record for a patch that has since been edited,
+        # and the campaign then reports a result no run ever produced.
+        prev = res.get(k)
+        # A clause may be implemented at SEVERAL sites (a guard duplicated for defence in depth). One
+        # clause is still ONE mutation: `sites` breaks every site of the clause together, because
+        # breaking one site of a duplicated guard changes no behaviour and would be recorded as a
+        # survivor of a test that is in fact perfectly capable of catching the clause's removal.
+        sites = m.get('sites') or [{'file': m['file'], 'old': m['old'], 'new': m['new'],
+                                    'occurrence': m.get('occurrence', 1)}]
+        patch = {'sites': sites} if m.get('sites') else {'old': m['old'], 'new': m['new']}
+        if prev is not None and prev.get('patch') == patch \
+                and prev.get('evidence', {}).get('repoCommit') == commit:
+            continue
+        res.pop(k, None)
+        f = os.path.join(root, sites[0]['file'])
         src = open(f).read()
-        n = src.count(m['old'])
-        if n == 0:
-            res[k] = {'status': 'ANCHOR-MISSING', 'file': m['file']}
+        missing = [s0 for s0 in sites if open(os.path.join(root, s0['file'])).read().count(s0['old']) == 0]
+        if missing:
+            res[k] = {'status': 'ANCHOR-MISSING', 'file': missing[0]['file']}
             print(f"  {k:<40} ANCHOR-MISSING"); json.dump(res, open(outp,'w'), indent=1); continue
-        occ = m.get('occurrence', 1)
+        m = dict(m, old=sites[0]['old'], new=sites[0]['new'])
+        n = src.count(sites[0]['old'])
+        occ = sites[0].get('occurrence', 1)
         # locate the chosen occurrence
         pos, seen = -1, 0
         start = 0
@@ -121,23 +190,61 @@ def main():
             while stack and depth <= stack[-1][0]:
                 stack.pop()
 
-        shutil.copy(f, f + '.bak')
-        mutated = src[:pos] + m['new'] + src[pos+len(m['old']):]
+        touched = sorted({os.path.join(root, s0['file']) for s0 in sites})
+        _ACTIVE['paths'] = touched
+        originals, mutants = {}, {}
+        for path in touched:
+            shutil.copy(path, path + '.bak')
         t0 = time.time()
         try:
-            open(f, 'w').write(mutated)
+            for s0 in sites:
+                path = os.path.join(root, s0['file'])
+                text = open(path).read()
+                originals.setdefault(s0['file'], sha(text))
+                at, seen2, start2 = -1, 0, 0
+                while True:
+                    j = text.find(s0['old'], start2)
+                    if j < 0: break
+                    seen2 += 1
+                    if seen2 == s0.get('occurrence', 1): at = j; break
+                    start2 = j + 1
+                open(path, 'w').write(text[:at] + s0['new'] + text[at+len(s0['old']):])
+            for s0 in sites:
+                path = os.path.join(root, s0['file'])
+                mutants[s0['file']] = sha(open(path).read())
             rc, out = run(cmd, root)
         finally:
             # UNCONDITIONAL. A timeout, an interrupt or a crash between the write and the restore
             # would otherwise leave the tree mutated and poison every later mutation AND the baseline.
-            shutil.move(f + '.bak', f)
-            os.utime(f, None)   # mv restores the ORIGINAL mtime; Maven's incremental compiler would skip it
+            for path in touched:
+                shutil.move(path + '.bak', path)
+                os.utime(path, None)   # mv restores the ORIGINAL mtime; Maven would skip recompiling
+            _ACTIVE['paths'] = []
         still_dirty = tree_dirty(root)
         failed = failing_tests(out, kind)
+        asserted = assertion_failures(out, kind)
+        # A `kind` that does not match the runner parses no failure names, and every kill is then
+        # recorded as BUILD-FAILED — silently, because the run really did exit non-zero. Refuse
+        # instead: the output plainly names failing tests in the other runner's shape.
+        if rc != 0 and not failed:
+            other = failing_tests(out, 'node' if kind == 'maven' else 'maven')
+            if other:
+                print(f"KIND MISMATCH: kind={kind!r} parsed no failures, but the output names {other[:3]}")
+                print("  the spec's `kind` does not match the test runner; refusing to record a wrong verdict")
+                sys.exit(4)
         if build_failed(out, kind) and not failed:
             status = 'BUILD-FAILED'
-        elif rc != 0 and failed:
+        elif rc != 0 and asserted:
             status = 'KILLED'
+        elif rc != 0 and failed and m.get('killedByThrow'):
+            # The clause under test IS "this must not throw", so a test that propagates the throw is
+            # detecting exactly the right thing. The spec must SAY so in advance — declaring the
+            # detection mode after seeing the result is how a fixture blow-up gets read as a kill.
+            status = 'KILLED'
+        elif rc != 0 and failed:
+            # the right test failed, but on a THROW rather than an assertion, and the spec did not
+            # declare that mode: the mutation may have broken the fixture rather than the behaviour
+            status = 'KILLED-BY-ERROR'
         elif rc != 0:
             status = 'BUILD-FAILED'
         else:
@@ -145,11 +252,13 @@ def main():
         res[k] = {'status': status, 'clause': m.get('clause',''), 'requirement': m.get('requirement', k.split()[0]),
                   'documentText': m.get('documentText'),
                   'file': m['file'], 'occurrence': occ, 'occurrencesInFile': n, 'line': line,
-                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': failed[:4],
+                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': asserted[:4] or failed[:4], 'assertionFailures': asserted[:4], 'anyFailures': failed[:4],
+                  'killedByThrow': bool(m.get('killedByThrow')),
                   'seconds': round(time.time()-t0, 1),
-                  'patch': {'old': m['old'], 'new': m['new']},
+                  'patch': patch,
                   'evidence': {'repoCommit': commit, 'returnCode': rc,
-                               'mutatedFileSha256': sha(mutated), 'originalFileSha256': sha(src),
+                               'mutatedFileSha256': mutants.get(sites[0]['file']), 'originalFileSha256': originals.get(sites[0]['file']),
+                               'filesSha256': {p: {'original': originals[p], 'mutated': mutants.get(p)} for p in originals},
                                'outputSha256': sha(out), 'outputTail': out[-1200:],
                                'failureLines': failure_lines(out, kind),
                                'treeRestoredClean': not still_dirty},
diff --git a/src/test/java/app/feedgateway/FootprintWiringTest.java b/src/test/java/app/feedgateway/FootprintWiringTest.java
index 7c26e3f..d40d839 100644
--- a/src/test/java/app/feedgateway/FootprintWiringTest.java
+++ b/src/test/java/app/feedgateway/FootprintWiringTest.java
@@ -42,7 +42,7 @@ class FootprintWiringTest {
         String src = Files.readString(SERVICE);
         assertEquals(2, occurrences(src, "addEsFootprintTopics(topicEvents);"), "called from the state cache AND the state live consumer");
         assertEquals(2, occurrences(src, "addEsCvdTopics(topicEvents);\n        addEsFootprintTopics(topicEvents);"), "right after the CVD wiring at both sites");
-        int start = src.indexOf("private void addEsFootprintTopics(");
+        int start = src.indexOf("void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {");
         String body = src.substring(start, src.indexOf("\n    }", start));
         assertTrue(body.contains("if (footprintViews == null) return;"), "flag off adds nothing");
         for (String e : new String[]{"es-footprint\"", "es-footprint-evidence\"", "es-footprint-bar\"", "es-footprint-outcome\""}) {
@@ -100,6 +100,51 @@ class FootprintWiringTest {
         assertTrue(s.metrics().contains("gateway_footprint_enabled 0\n"));
     }
 
+    /**
+     * G-R2: the four bindings are asserted by EXECUTING the wiring, not by reading its source. A
+     * source assertion counts the text of a {@code TopicBinding} that a surrounding {@code if (false)}
+     * has made unreachable; only running the method can tell the two apart.
+     */
+    @Test void theWiringMethodPutsAllFourBindingsInTheMapItIsGiven() {
+        java.util.Map<String, FeedGatewayService.TopicBinding> wired = new java.util.LinkedHashMap<>();
+        on().addEsFootprintTopics(wired);
+        GatewaySettings g = new GatewaySettings();
+        assertEquals(java.util.Map.of(
+                g.esFootprintTopic(), "es-footprint",
+                g.esFootprintEvidenceTopic(), "es-footprint-evidence",
+                g.esFootprintBarsTopic(), "es-footprint-bar",
+                g.esFootprintOutcomesTopic(), "es-footprint-outcome"),
+                wired.entrySet().stream().collect(java.util.stream.Collectors.toMap(
+                        java.util.Map.Entry::getKey, e -> e.getValue().event())),
+                "every topic is bound, to its own event");
+        wired.values().forEach(b -> assertEquals("DATABENTO", b.source()));
+        java.util.Map<String, FeedGatewayService.TopicBinding> none = new java.util.LinkedHashMap<>();
+        off().addEsFootprintTopics(none);
+        assertTrue(none.isEmpty(), "flag off adds nothing");
+    }
+
+    /** G-R3: the two live snapshot topics are never retained; the two keyed topics seek back a session. */
+    @Test void liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession() {
+        FeedGatewayService s = on();
+        long back = new GatewaySettings().esFootprintSeekBackMs();
+        assertTrue(back > 0, "the keyed seek-back is a real window");
+        for (String live : new String[]{"es-footprint", "es-footprint-evidence"}) {
+            assertEquals(0L, s.cachePolicyFor(live, 0L).ttlMs(), live + " is never retained");
+        }
+        for (String keyed : new String[]{"es-footprint-bar", "es-footprint-outcome"}) {
+            assertEquals(back, s.cachePolicyFor(keyed, 0L).ttlMs(), keyed + " re-fills from the compacted topic");
+        }
+    }
+
+    /** G-R6: footprint alone is enough to send the hello — the page needs the handshake either way. */
+    @Test void theHelloIsSentWhenFootprintAloneIsEnabled() {
+        GatewaySettings g = new GatewaySettings();
+        assertFalse(g.esCvdEnabled(), "the fixture isolates the footprint disjunct");
+        assertFalse(g.esCvdSpxLevelsEnabled());
+        assertTrue(on().sendsCvdHello(), "footprint alone still sends cvd-hello");
+        assertFalse(off().sendsCvdHello(), "and nothing enabled sends none");
+    }
+
     // ---- G-R6 hello ---------------------------------------------------------------------------------
 
     @Test void flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot() {

2026-09-08T20:25:15.739917Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "rg -l 'G-R10\\.1|every-admission-failure-is-a-counted-drop' . -g '*.json' -g '*.py' -g '*.md'; python3 - <<'PY'
import json
r=json.load(open('ES-FOOTPRINT-CAMPAIGN.json'))
for k,v in r.items():
 if v.get('requirement')=='G-R10':
  print('\\nKEY',k)
  for x in ['status','clause','file','line','enclosing','killedBy','assertionFailures','anyFailures','killedByThrow','patch']:
   print(x,repr(v.get(x)))
  print('failureLines:'); print('\\n'.join(v['evidence']['failureLines']))
PY
find . -maxdepth 3 -type f | sort | rg 'footprint|mutation|campaign'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
./ES-FOOTPRINT-CAMPAIGN.json
/bin/bash: cannot create temp file for here document: Operation not permitted
./scripts/footprint-conformance.py
./scripts/footprint-mutate.py
./scripts/footprint-reqstate.preamble
./scripts/footprint-reqstate.py
./scripts/footprint-reqstate.sh

2026-09-08T20:25:20.710186Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "python3 -c \"import json; r=json.load(open('ES-FOOTPRINT-CAMPAIGN.json')); [(print('\\nKEY',k),*[print(x,repr(v.get(x))) for x in ['status','clause','file','line','enclosing','killedBy','assertionFailures','anyFailures','killedByThrow','patch']],print('failureLines:'),print('\\\\n'.join(v['evidence']['failureLines']))) for k,v in r.items() if v.get('requirement')=='G-R10']\"; rg -n -C 12 'G-R10\\.1|G-R10\\.2|G-R10\\.3|killedByThrow' scripts/footprint-conformance.py" in /private/tmp/oe-conf-gw
 exited 1 in 0ms:

KEY G-R10.1 a-footprint-record-never-escapes-the-consumer-loop
status 'KILLED'
clause 'a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw'
file 'src/main/java/app/feedgateway/FootprintViews.java'
line 93
enclosing 'Admission admitBar(String json) {'
killedBy ['FootprintViewsTest.aNullPayloadIsARefusalNotAThrow']
assertionFailures []
anyFailures ['FootprintViewsTest.aNullPayloadIsARefusalNotAThrow']
killedByThrow True
patch {'old': '        if (json == null) return Admission.SHAPE;', 'new': '        if (json == null) throw new IllegalArgumentException("json");'}
failureLines:
[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.003 s <<< ERROR!
[ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:82 » IllegalArgument json

KEY G-R10.2 every-admission-failure-is-a-counted-drop
status 'KILLED'
clause 'every admission failure is a counted drop'
file 'src/main/java/app/feedgateway/FeedGatewayService.java'
line 903
enclosing 'footprintCounter("drops_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\",reason=\\"" + a.reason().name().toLowerCase() + "\\"}").in'
killedBy ['FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules']
assertionFailures ['FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules']
anyFailures ['FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules']
killedByThrow False
patch {'old': '            footprintCounter("drops_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\",reason=\\"" + a.reason().name().toLowerCase() + "\\"}").incrementAndGet();', 'new': '            if (a.reason() != FootprintViews.Reason.SHAPE) footprintCounter("drops_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\",reason=\\"" + a.reason().name().toLowerCase() + "\\"}").incrementAndGet();'}
failureLines:
[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.335 s <<< FAILURE!
[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>

KEY G-R10.3 every-record-is-counted-before-admission
status 'KILLED'
clause 'incremented BEFORE admission (so it counts oversize/shape/stale records too)'
file 'src/main/java/app/feedgateway/FeedGatewayService.java'
line 897
enclosing 'footprintCounter("records_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\"}").incrementAndGet();'
killedBy ['FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone', 'FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules']
assertionFailures ['FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone', 'FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules']
anyFailures ['FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone', 'FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules']
killedByThrow False
patch {'old': '        footprintCounter("records_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\"}").incrementAndGet();\n        if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {', 'new': '        if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {\n            footprintCounter("records_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\"}").incrementAndGet();'}
failureLines:
[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.212 s <<< FAILURE!
[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.033 s <<< FAILURE!
[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false>
[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:168 expected: <true> but was: <false>

2026-09-08T20:25:24.931523Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "sed -n '1,260p' scripts/footprint-conformance.py; git show --stat --oneline 1224cec; git show 1224cec -- scripts/footprint-conformance.py | sed -n '1,260p'; nl -ba src/test/java/app/feedgateway/FootprintWiringTest.java | sed -n '130,190p'; nl -ba src/main/java/app/feedgateway/FeedGatewayService.java | sed -n '875,920p'" in /private/tmp/oe-conf-gw
 succeeded in 9245ms:
2026-09-08 22:25:28.782 xcodebuild[24949:266023155]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:25:29.157 xcodebuild[24949:266023154] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-9fqHtji7' (errno=Operation not permitted)
2026-09-08 22:25:29.974 xcodebuild[24981:266023344]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:25:30.274 xcodebuild[24981:266023342] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
1224cec test(gateway footprint): execute the four clauses the suite only read
 .../java/app/feedgateway/FeedGatewayService.java   | 13 ++++--
 .../app/feedgateway/CvdSpxLevelsWiringTest.java    |  7 +++-
 .../java/app/feedgateway/FootprintViewsTest.java   | 20 +++++++++
 .../java/app/feedgateway/FootprintWiringTest.java  | 47 +++++++++++++++++++++-
 4 files changed, 80 insertions(+), 7 deletions(-)
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-7VlH6fKs' (errno=Operation not permitted)
2026-09-08 22:25:32.586 xcodebuild[25030:266023571]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:25:32.977 xcodebuild[25030:266023569] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-lllBKLxv' (errno=Operation not permitted)
2026-09-08 22:25:34.227 xcodebuild[25075:266023814]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:25:35.210 xcodebuild[25075:266023812] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
   130	        assertTrue(back > 0, "the keyed seek-back is a real window");
   131	        for (String live : new String[]{"es-footprint", "es-footprint-evidence"}) {
   132	            assertEquals(0L, s.cachePolicyFor(live, 0L).ttlMs(), live + " is never retained");
   133	        }
   134	        for (String keyed : new String[]{"es-footprint-bar", "es-footprint-outcome"}) {
   135	            assertEquals(back, s.cachePolicyFor(keyed, 0L).ttlMs(), keyed + " re-fills from the compacted topic");
   136	        }
   137	    }
   138	
   139	    /** G-R6: footprint alone is enough to send the hello — the page needs the handshake either way. */
   140	    @Test void theHelloIsSentWhenFootprintAloneIsEnabled() {
   141	        GatewaySettings g = new GatewaySettings();
   142	        assertFalse(g.esCvdEnabled(), "the fixture isolates the footprint disjunct");
   143	        assertFalse(g.esCvdSpxLevelsEnabled());
   144	        assertTrue(on().sendsCvdHello(), "footprint alone still sends cvd-hello");
   145	        assertFalse(off().sendsCvdHello(), "and nothing enabled sends none");
   146	    }
   147	
   148	    // ---- G-R6 hello ---------------------------------------------------------------------------------
   149	
   150	    @Test void flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot() {
   151	        FeedGatewayService s = on();
   152	        assertEquals("{\"sessionDate\":null,\"hwm\":{},\"footprint\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}}", s.cvdHelloJson());
   153	        assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "1m", 60_000), "live"));
   154	        assertTrue(s.cvdHelloJson().endsWith("\"footprint\":{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"1m\":60000},\"outcomeHwm\":{}}}"));
   155	    }
   156	
   157	    // ---- G-R3/G-R9: the shared admission path and overlap identities --------------------------------
   158	
   159	    @Test void admissionCountsAndBroadcastDecisionsFollowTheOverlapRules() {
   160	        FeedGatewayService s = on();
   161	        assertTrue(s.admitFootprintRecord("es-footprint", "{\"schemaVersion\":6}", "live"), "live snapshots are broadcast, never admitted");
   162	        assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "1m", 1), "live"));
   163	        assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-13", "1m", 1), "live"), "stale: dropped from the view, still broadcast");
   164	        assertTrue(s.admitFootprintRecord("es-footprint-outcome", "{\"unrelated\":true}", "cache"), "shape: dropped from the view (cache path never broadcasts anyway)");
   165	        assertFalse(s.admitFootprintRecord("es-footprint-outcome", "{\"pad\":\"" + "y".repeat(300_000) + "\"}", "live"), "oversize: dropped entirely");
   166	        String m = s.footprintMetricsText();
   167	        assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint\",consumer=\"live\"} 1\n"), m);
   168	        assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint-bar\",consumer=\"live\"} 2\n"));
   169	        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-bar\",consumer=\"live\",reason=\"stale_session\"} 1\n"));
   170	        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-outcome\",consumer=\"cache\",reason=\"shape\"} 1\n"));
   171	        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-outcome\",consumer=\"live\",reason=\"oversize\"} 1\n"));
   172	        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-bar\",consumer=\"cache\",reason=\"stale_session\"} 0\n"));
   173	        assertTrue(m.contains("gateway_footprint_bars_in_view 1\n"));
   174	        assertEquals(1, s.footprintViews().barsInView());
   175	    }
   176	
   177	    @Test void bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions() {
   178	        FootprintTopicGateTest.FakeReader r = new FootprintTopicGateTest.FakeReader();
   179	        r.valid("futures.footprint").valid("futures.footprint.evidence").valid("futures.footprint.bars").unknown("futures.footprint.outcomes");
   180	        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, r);
   181	        List<org.apache.kafka.common.TopicPartition> resolved = List.of(
   182	                new org.apache.kafka.common.TopicPartition("futures.cvd", 0),
   183	                new org.apache.kafka.common.TopicPartition("futures.footprint.bars", 0),
   184	                new org.apache.kafka.common.TopicPartition("futures.footprint.outcomes", 0));
   185	        List<org.apache.kafka.common.TopicPartition> admitted = s.footprintAdmitted(resolved);
   186	        assertEquals(List.of(resolved.get(0), resolved.get(1)), admitted, "the absent/unvalidated topic's partition is withheld; others pass");
   187	        r.valid("futures.footprint.outcomes");
   188	        assertEquals(resolved, s.footprintAdmitted(resolved), "admitted on the next evaluation once validated");
   189	        assertEquals(resolved, off().footprintAdmitted(resolved), "flag off: identity");
   190	    }
   875	
   876	    private static final String[] FOOTPRINT_EVENTS = {"es-footprint", "es-footprint-evidence", "es-footprint-bar", "es-footprint-outcome"};
   877	    private static final String[] FOOTPRINT_KEYED_EVENTS = {"es-footprint-bar", "es-footprint-outcome"};
   878	
   879	    static boolean isFootprintEvent(String event) {
   880	        return "es-footprint".equals(event) || "es-footprint-evidence".equals(event)
   881	                || "es-footprint-bar".equals(event) || "es-footprint-outcome".equals(event);
   882	    }
   883	
   884	    private AtomicLong footprintCounter(String series) {
   885	        return footprintCounters.computeIfAbsent(series, k -> new AtomicLong());
   886	    }
   887	
   888	    /** G-R9 counting: incremented at the route handler's flag check, so a 404 is never counted. */
   889	    void footprintBackfillRequested(String route) { footprintCounter("backfill_requests_total{route=\"" + route + "\"}").incrementAndGet(); }
   890	    void footprintBackfillRejected(String route, String reason) { footprintCounter("backfill_rejected_total{route=\"" + route + "\",reason=\"" + reason + "\"}").incrementAndGet(); }
   891	
   892	    /**
   893	     * G-R3/G-R4/G-R5/G-R9: ONE admission path for both consumers. Returns true when the LIVE consumer
   894	     * may broadcast the record (everything except an oversize drop; the cache consumer never broadcasts).
   895	     */
   896	    boolean admitFootprintRecord(String event, String json, String consumer) {   // package-private: the wiring tests drive both consumer paths through it
   897	        footprintCounter("records_total{event=\"" + event + "\",consumer=\"" + consumer + "\"}").incrementAndGet();
   898	        if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {
   899	            return true;                                            // live snapshots: never admitted to a view
   900	        }
   901	        FootprintViews.Admission a = "es-footprint-bar".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);
   902	        if (a.reason() != FootprintViews.Reason.ADMITTED) {
   903	            footprintCounter("drops_total{event=\"" + event + "\",consumer=\"" + consumer + "\",reason=\"" + a.reason().name().toLowerCase() + "\"}").incrementAndGet();
   904	        }
   905	        return a.reason() != FootprintViews.Reason.OVERSIZE;
   906	    }
   907	
   908	    /**
   909	     * The bootstrap resolution + assignment BOTH state consumers use (G-R8a): partitions of a footprint
   910	     * topic that has not validated are withheld, so they are never assigned, sought or polled — exactly
   911	     * like a topic that does not exist yet. Package-private: the seam the acceptance tests execute.
   912	     */
   913	    List<TopicPartition> bootstrapAssign(String name, KafkaConsumer<?, ?> consumer, Map<String, TopicBinding> topicEvents) {
   914	        List<TopicPartition> partitions = footprintAdmitted(partitionsFor(name, consumer, topicEvents.keySet()));
   915	        consumer.assign(partitions);
   916	        return partitions;
   917	    }
   918	
   919	    /**
   920	     * The LIVE consumer's bootstrap seek, in one place so its ordering is executable: on a RETRY the

2026-09-08T20:25:37.924659Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
Two material inconsistencies have emerged in the generation path: the renderer discovers requirement IDs from the document it is rewriting (including the stale generated block), and its overall totals still count every campaign record rather than only IDs rendered in §2a. I’m checking whether these are only latent or affect the committed output, and finishing the mutation-reason audit.
exec
/bin/bash -lc "scripts/footprint-reqstate.sh --check; python3 scripts/footprint-reqstate.py ES-FOOTPRINT-CAMPAIGN.json ES-FOOTPRINT-GATEWAY-DESIGN.md 'G-R\\d+[a-z]?' '{\"G-R.*\":\"2\"}' '{\"G-R1|G-R2\":\"Flag and wiring\",\"G-R3|G-R4|G-R5\":\"Delivery and views\",\"G-R6\":\"Hello\",\"G-R7\":\"Backfill routes\",\"G-R8|G-R8a\":\"Deployment contingency\",\"G-R9\":\"Metrics\",\"G-R10\":\"Non-interference\",\"G-R11\":\"Tests\"}' '{\"G-R11\":\"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break\"}' | tail -25; nl -ba scripts/footprint-reqstate.py; nl -ba scripts/footprint-reqstate.sh; nl -ba scripts/footprint-reqstate.preamble" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
mktemp: mkdtemp failed on /var/folders/t3/_t_9ln7d49g_yvjqnyr_rdj40000gn/T/tmp.dh0GatGGVM: Operation not permitted
| id | Conformance | Gate | Disposition |
|----|-------------|------|-------------|
| G-R1 | 1 of 1 clauses probed here are pinned | 2 | Flag and wiring |
| G-R2 | 2 of 2 clauses probed here are pinned | 2 | Flag and wiring |
| G-R3 | 3 of 3 clauses probed here are pinned | 2 | Delivery and views |
| G-R4 | 4 of 4 clauses probed here are pinned | 2 | Delivery and views |
| G-R5 | 4 of 4 clauses probed here are pinned | 2 | Delivery and views |
| G-R6 | 3 of 3 clauses probed here are pinned | 2 | Hello |
| G-R7 | 5 of 6 clauses probed here are pinned (1 survived) | 2 | Backfill routes |
| G-R8 | 5 of 5 clauses probed here are pinned | 2 | Deployment contingency |
| G-R9 | 1 of 1 clauses probed here are pinned | 2 | Metrics |
| G-R10 | 3 of 3 clauses probed here are pinned | 2 | Non-interference |
| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
| G-R8a | 2 of 2 clauses probed here are pinned | 2 | Deployment contingency |

12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).

Read the state column narrowly. "n of m clauses probed here are pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.
     1	#!/usr/bin/env python3
     2	"""Render the §9 requirement-reconciliation table from the CHECKED-IN campaign record.
     3	
     4	The state column is derived, never typed: a requirement is what its mutations say it is. A
     5	requirement the campaign did not probe says exactly that, and claims nothing else — the failure mode
     6	this replaces is a hand-maintained table drifting away from the code it claims to describe.
     7	"""
     8	import json, re, sys, collections
     9	
    10	def main():
    11	    recordPath, docPath, idPattern, gates, dispositions = sys.argv[1], sys.argv[2], sys.argv[3], json.loads(sys.argv[4]), json.loads(sys.argv[5])
    12	    # Requirements whose subject is not production behaviour (a test inventory) or whose behaviour
    13	    # lives in ANOTHER repository. Neither can be probed here, and neither is a gap — but saying
    14	    # "NOT PROBED" without saying why invites the reader to assume it is one.
    15	    notes = json.loads(sys.argv[6]) if len(sys.argv) > 6 else {}
    16	    rec = json.load(open(recordPath))
    17	    ids = []
    18	    for line in open(docPath):
    19	        m = re.match(r'^\|\s*(' + idPattern + r')\s*\|', line)
    20	        if m and m.group(1) not in ids: ids.append(m.group(1))
    21	    per = collections.defaultdict(list)
    22	    for k, v in rec.items():
    23	        per[v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', k).group(1)].append(v)
    24	    # A record is only allowed to say "pinned" when it carries the evidence for it: an assertion
    25	    # failure naming a test, and the verbatim failure lines that name was parsed from. A record that
    26	    # merely says KILLED is a claim, not evidence — and one kill in this campaign turned out to be a
    27	    # format-string error under the right test's name, which is exactly what this refuses.
    28	    problems = []
    29	    for rid, ms in per.items():
    30	        for m in ms:
    31	            if m['status'] != 'KILLED':
    32	                continue
    33	            if not m.get('assertionFailures') and not m.get('killedBy'):
    34	                problems.append(f"{rid}: a KILLED record names no failing test")
    35	            if not m.get('evidence', {}).get('failureLines'):
    36	                problems.append(f"{rid}: a KILLED record carries no failure lines")
    37	            if not m.get('evidence', {}).get('treeRestoredClean', True):
    38	                problems.append(f"{rid}: a KILLED record ran against a tree it did not restore")
    39	    if problems:
    40	        print("REFUSING TO EMIT:", file=sys.stderr)
    41	        for p in problems: print("  ", p, file=sys.stderr)
    42	        sys.exit(1)
    43	
    44	    def gate(rid):
    45	        for pat, g in gates.items():
    46	            if re.fullmatch('(?:' + pat + ')', rid): return g
    47	        return '—'
    48	    def disp(rid):
    49	        for pat, d in dispositions.items():
    50	            if re.fullmatch('(?:' + pat + ')', rid): return d
    51	        return ''
    52	    out = ["| id | Conformance | Gate | Disposition |", "|----|-------------|------|-------------|"]
    53	    for rid in sorted(ids, key=lambda x: (re.sub(r'\d', '', x), int(re.search(r'\d+', x).group()), x)):
    54	        ms = per.get(rid, [])
    55	        if not ms:
    56	            # A note is EDITORIAL: it says why this requirement has no mutations, and is marked as
    57	            # such so it can never be read as a campaign result.
    58	            state = (notes[rid] + " (not probed)") if rid in notes else "NOT PROBED"
    59	        else:
    60	            by = collections.Counter(m['status'] for m in ms)
    61	            killed = by.pop('KILLED', 0)
    62	            state = f"{killed} of {len(ms)} clauses probed here are pinned"
    63	            rest = ", ".join(f"{n} {st.lower()}" for st, n in sorted(by.items()))
    64	            if rest: state += f" ({rest})"
    65	        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
    66	    tot = collections.Counter(v['status'] for v in rec.values())
    67	    out.append("")
    68	    probed_here = len([r for r in ids if r in per])
    69	    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rec)} mutations "
    70	               f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving"
    71	               + (", " + ", ".join(f"{n} {st.lower()}" for st, n in sorted(tot.items()) if st not in ('KILLED', 'SURVIVED')) if len(tot) > 2 else "")
    72	               + ")."
    73	               "\n\nRead the state column narrowly. \"n of m clauses probed here are pinned\" says that "
    74	               "breaking those clauses in the production source made a NAMED test fail an ASSERTION — "
    75	               "it does NOT say the requirement as a whole is held, because a requirement usually has "
    76	               "more clauses than this campaign broke. \"NOT PROBED\" means this campaign did not test "
    77	               "it and claims nothing either way; where a note appears beside it, that note is "
    78	               "editorial and is not a campaign result. Evidence, per mutation — the patch, the file, "
    79	               "line and enclosing declaration, the command, the exit code, the verbatim failure "
    80	               "lines and a SHA-256 of the run output — is in the campaign record beside this "
    81	               "document, and this table refuses to render a \"pinned\" cell for any record that does "
    82	               "not carry it.")
    83	    print("\n".join(out))
    84	
    85	main()
     1	#!/usr/bin/env bash
     2	# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md IN PLACE from the checked-in campaign record.
     3	#
     4	# It rewrites the block between the BEGIN/END markers and leaves everything else byte-identical, so
     5	# running it is the whole update: an earlier version of this script only printed to stdout while the
     6	# document claimed it regenerated the section, which is a claim the script could not keep.
     7	#
     8	# --check exits non-zero if the committed section differs from what the record produces, so CI can
     9	# hold the document to its own record.
    10	set -euo pipefail
    11	cd "$(dirname "$0")/.."
    12	
    13	DOC=ES-FOOTPRINT-GATEWAY-DESIGN.md
    14	BEGIN='<!-- BEGIN footprint-reqstate'
    15	END='<!-- END footprint-reqstate -->'
    16	
    17	table() {
    18	  python3 scripts/footprint-reqstate.py \
    19	    ES-FOOTPRINT-CAMPAIGN.json \
    20	    "$DOC" \
    21	    'G-R\d+[a-z]?' \
    22	    '{"G-R.*":"2"}' \
    23	    '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
    24	    '{"G-R11":"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break"}'
    25	}
    26	
    27	grep -qF "$BEGIN" "$DOC" || { echo "FATAL: $DOC has no $BEGIN marker" >&2; exit 1; }
    28	grep -qF "$END"   "$DOC" || { echo "FATAL: $DOC has no $END marker" >&2; exit 1; }
    29	
    30	tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
    31	table > "$tmp/table.md"
    32	
    33	# Rebuild the document: everything before the marker, the fixed prose, the generated table, the
    34	# fixed trailer, everything after. The prose lives HERE so the generated part is only the table.
    35	awk -v tablefile="$tmp/table.md" -v begin="$BEGIN" -v end="$END" '
    36	  index($0, begin) == 1 { skipping = 1; print; print ""; print "## 2a. Conformance — what a test actually holds"; print "";
    37	      while ((getline line < "scripts/footprint-reqstate.preamble") > 0) print line
    38	      close("scripts/footprint-reqstate.preamble")
    39	      print ""
    40	      while ((getline line < tablefile) > 0) print line
    41	      close(tablefile)
    42	      print ""
    43	      next }
    44	  index($0, end) == 1 { skipping = 0 }
    45	  !skipping { print }
    46	' "$DOC" > "$tmp/doc.md"
    47	
    48	if [ "${1:-}" = "--check" ]; then
    49	  if ! diff -q "$DOC" "$tmp/doc.md" >/dev/null; then
    50	    echo "§2a is stale: regenerate with scripts/footprint-reqstate.sh" >&2
    51	    diff -u "$DOC" "$tmp/doc.md" | head -60 >&2
    52	    exit 1
    53	  fi
    54	  echo "§2a matches the campaign record"
    55	  exit 0
    56	fi
    57	
    58	mv "$tmp/doc.md" "$DOC"
    59	echo "§2a regenerated in $DOC"
     1	Every requirement above, and what this repository's mutation campaign established about it. The
     2	campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
     3	failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
     4	the spec declared in advance — never an incidental error under the right test's name; and the table
     5	below refuses to render a "pinned" cell for any record that does not carry its evidence. That
     6	evidence — the patch, the file, line and enclosing declaration, the command, the exit code, the
     7	verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
     8	this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
     9	record with `scripts/footprint-reqstate.sh --check`.

2026-09-08T20:26:02.987772Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
1. High — §2a still overclaims assertion-backed pins. [footprint-reqstate.py](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:73) defines every “pinned” count as a named assertion failure, but G-R10.1 has no `assertionFailures`; its evidence is `<<< ERROR!`. Thus “3 of 3 clauses … pinned” includes a result that contradicts the table’s own definition.

2. High — `killedByThrow` can still launder incidental errors. [footprint-mutate.py](/private/tmp/oe-conf-gw/scripts/footprint-mutate.py:239) accepts any parsed failing test when the boolean is set. It does not require a predeclared test name, expected exception type/message, or verify that the failure lines describe the intended throw. The checked-in record also cannot establish that the flag was declared before observing the run. G-R10.1’s actual `IllegalArgumentException("json")` is caused by its mutation and does violate the no-throw clause, but the mechanism’s broader “never an incidental error” claim is unsupported.

3. Medium — G-R10.3 is not killed for the claimed “counted before admission” reason. Its mutation removes counting from all keyed records by moving the increment into the live-snapshot branch. The failures establish that keyed records must be counted, but do not distinguish counting before admission from counting immediately afterward. The record and §2a therefore call an ordering clause pinned without exercising that ordering.

4. Medium — generation remains self-referential and can miss stale requirements. [footprint-reqstate.py](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:18) discovers IDs from the entire document, including the old generated §2a table. Removing a requirement from the authoritative requirements table can leave it perpetuated by the stale generated row, so `--check` would accept it. Additionally, lines 66–70 count every campaign record rather than only requirements present in the rendered document.

5. Medium — the rewrite does not safely establish one well-formed marker block. [footprint-reqstate.sh](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.sh:27) checks only that each marker occurs somewhere. Duplicate, reversed, or prefix-matching markers can duplicate generated content or discard unrelated document sections. The preamble is hand-maintained in a separate file and copied into the generated block; that arrangement is broadly disclosed by the script, although the comment saying the prose “lives HERE” is inaccurate.

Round-1 finding 2 is resolved: G-R11 is clearly editorial and suffixed “(not probed).” The two new G-R10 failure lines do correspond to missing drop accounting and missing keyed-record accounting, but only G-R10.2 supports its full stated clause. I could render the table directly and confirmed it matches the committed cells; the wrapper’s `--check` could not run because this review environment prohibits temporary-directory creation.

VERDICT: REQUEST_CHANGES
tokens used
50.518
1. High — §2a still overclaims assertion-backed pins. [footprint-reqstate.py](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:73) defines every “pinned” count as a named assertion failure, but G-R10.1 has no `assertionFailures`; its evidence is `<<< ERROR!`. Thus “3 of 3 clauses … pinned” includes a result that contradicts the table’s own definition.

2. High — `killedByThrow` can still launder incidental errors. [footprint-mutate.py](/private/tmp/oe-conf-gw/scripts/footprint-mutate.py:239) accepts any parsed failing test when the boolean is set. It does not require a predeclared test name, expected exception type/message, or verify that the failure lines describe the intended throw. The checked-in record also cannot establish that the flag was declared before observing the run. G-R10.1’s actual `IllegalArgumentException("json")` is caused by its mutation and does violate the no-throw clause, but the mechanism’s broader “never an incidental error” claim is unsupported.

3. Medium — G-R10.3 is not killed for the claimed “counted before admission” reason. Its mutation removes counting from all keyed records by moving the increment into the live-snapshot branch. The failures establish that keyed records must be counted, but do not distinguish counting before admission from counting immediately afterward. The record and §2a therefore call an ordering clause pinned without exercising that ordering.

4. Medium — generation remains self-referential and can miss stale requirements. [footprint-reqstate.py](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:18) discovers IDs from the entire document, including the old generated §2a table. Removing a requirement from the authoritative requirements table can leave it perpetuated by the stale generated row, so `--check` would accept it. Additionally, lines 66–70 count every campaign record rather than only requirements present in the rendered document.

5. Medium — the rewrite does not safely establish one well-formed marker block. [footprint-reqstate.sh](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.sh:27) checks only that each marker occurs somewhere. Duplicate, reversed, or prefix-matching markers can duplicate generated content or discard unrelated document sections. The preamble is hand-maintained in a separate file and copied into the generated block; that arrangement is broadly disclosed by the script, although the comment saying the prose “lives HERE” is inaccurate.

Round-1 finding 2 is resolved: G-R11 is clearly editorial and suffixed “(not probed).” The two new G-R10 failure lines do correspond to missing drop accounting and missing keyed-record accounting, but only G-R10.2 supports its full stated clause. I could render the table directly and confirmed it matches the committed cells; the wrapper’s `--check` could not run because this review environment prohibits temporary-directory creation.

VERDICT: REQUEST_CHANGES
