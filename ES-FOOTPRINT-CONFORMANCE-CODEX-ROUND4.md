Reading additional input from stdin...
2026-09-08T21:01:37.060999Z ERROR codex_models_manager::manager: failed to load models cache: missing field `supports_parallel_tool_calls` at line 93 column 5
OpenAI Codex v0.147.0
--------
workdir: /private/tmp/oe-conf-gw
model: gpt-5.6-sol
provider: openai
approval: never
sandbox: read-only
reasoning effort: low
reasoning summaries: none
session id: 01a082d3-8cea-7b41-a4ba-fcffba47a82f
--------
user
Round 4 of the review of option-edge-feed-gateway PR #174 at /private/tmp/oe-conf-gw, branch
test/footprint-conformance-pins. Round 3 gave one HIGH and two MEDIUM.

What was done — VERIFY EACH against the files and the record:
 1. HIGH "records_total BEFORE admission is G-R9's clause, not G-R10's" — the probe is reassigned to
    G-R9 (key G-R9.2) with its obligation quoted from G-R9's own text; the table now reads G-R9 2/2
    and G-R10 2/2.
 2. MEDIUM "the G-R3 broadcast probe's clause said shape/session/SIZE" — narrowed to shape or
    session, matching what G-R3 says and what the run's failure lines exercise.
 3. MEDIUM "the generator still treats any truthy killedByThrow as sufficient" — it now requires
    {test, throws} AND both strings to appear in that record's own failureLines.
 Also folded: the summary now counts only records for requirements the table renders, and the
 generator's marker rule (line start) matches the rewriter's.

Then review the WHOLE change once more, line by line, and report anything that still overclaims, is
misattributed, or is unheld. For every row of §2a, check the obligation quoted is verbatim in THAT
requirement, and that the probes recorded under it actually exercise that requirement's clauses.

End with exactly one line: VERDICT: APPROVE  or  VERDICT: REQUEST_CHANGES
codex
I’ll re-audit the branch against its diff and review record, then trace every §2a row from quoted obligation through probe evidence and generator/rewriter behavior.
exec
/bin/bash -lc "pwd && rg --files -g 'AGENTS.md' -g '"'!node_modules'"' -g '"'!vendor'"' .. | head -50 && git status --short && git branch --show-current && git diff --stat && git diff --name-only" in /private/tmp/oe-conf-gw
exec
/bin/bash -lc 'find .. -name AGENTS.md -print 2>/dev/null; /usr/bin/git status --short; /usr/bin/git branch --show-current; /usr/bin/git diff --stat; /usr/bin/git diff --name-only' in /private/tmp/oe-conf-gw
 succeeded in 15756ms:
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-YGR2OMGu' (errno=Operation not permitted)
2026-09-08 23:01:45.434 xcodebuild[19604:266352845]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:01:45.640 xcodebuild[19604:266352842] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-al4WuIV7' (errno=Operation not permitted)
2026-09-08 23:01:47.380 xcodebuild[19631:266353031]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:01:47.777 xcodebuild[19631:266353021] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-zRwvj0pn' (errno=Operation not permitted)
2026-09-08 23:01:50.120 xcodebuild[19708:266353375]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:01:50.488 xcodebuild[19708:266353372] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-7Uedaqwt' (errno=Operation not permitted)
2026-09-08 23:01:51.878 xcodebuild[19743:266353552]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:01:52.176 xcodebuild[19743:266353548] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
test/footprint-conformance-pins
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-2d5D1p0t' (errno=Operation not permitted)
2026-09-08 23:01:54.276 xcodebuild[19811:266353882]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:01:54.605 xcodebuild[19811:266353875] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-W2JfGuWn' (errno=Operation not permitted)
2026-09-08 23:01:55.223 xcodebuild[19836:266354020]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:01:55.675 xcodebuild[19836:266354017] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-VadpS5TZ' (errno=Operation not permitted)
2026-09-08 23:01:57.293 xcodebuild[19905:266354384]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:01:57.539 xcodebuild[19905:266354375] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-Q24faQB3' (errno=Operation not permitted)
2026-09-08 23:01:58.220 xcodebuild[19945:266354686]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:01:58.472 xcodebuild[19945:266354684] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.

 succeeded in 16717ms:
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-VRwQf7Mi' (errno=Operation not permitted)
2026-09-08 23:02:03.049 xcodebuild[20152:266355692]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:02:03.284 xcodebuild[20152:266355682] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-9nepHmAt' (errno=Operation not permitted)
2026-09-08 23:02:03.975 xcodebuild[20202:266355943]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:02:04.236 xcodebuild[20202:266355939] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-ROU8fcez' (errno=Operation not permitted)
2026-09-08 23:02:05.887 xcodebuild[20296:266356448]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:02:06.352 xcodebuild[20296:266356439] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-QFSE9vzd' (errno=Operation not permitted)
2026-09-08 23:02:07.128 xcodebuild[20351:266356705]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:02:07.318 xcodebuild[20351:266356704] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
test/footprint-conformance-pins
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-aXlFcakd' (errno=Operation not permitted)
2026-09-08 23:02:09.156 xcodebuild[20449:266357190]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:02:09.366 xcodebuild[20449:266357185] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-tRfU2xbg' (errno=Operation not permitted)
2026-09-08 23:02:10.152 xcodebuild[20496:266357503]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:02:10.396 xcodebuild[20496:266357501] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-77EvY83o' (errno=Operation not permitted)
2026-09-08 23:02:11.926 xcodebuild[20595:266357987]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:02:12.280 xcodebuild[20595:266357979] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-k7yKG7k5' (errno=Operation not permitted)
2026-09-08 23:02:13.015 xcodebuild[20642:266358212]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:02:13.345 xcodebuild[20642:266358208] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.

exec
/bin/bash -lc "/usr/bin/git log --oneline --decorate -15 2>/dev/null; /usr/bin/git remote -v 2>/dev/null; /usr/bin/git branch -a 2>/dev/null; rg -n \"§2a|G-R9\\.2|killedByThrow|records_total|G-R3|G-R10\" . --glob '"'!node_modules/**'"' --glob '"'!vendor/**'"'" in /private/tmp/oe-conf-gw
 succeeded in 10811ms:
0ad2f33 (HEAD -> test/footprint-conformance-pins, origin/test/footprint-conformance-pins) fix(footprint conformance): put the ordering clause under the requirement that states it
ae8a687 wip: generator round-3 folds
8bbcdab fix(footprint tooling): a kill must name an assertion, a throw-kill must name its throw, and the table counts probes
928983e test(gateway footprint): state the no-throw clause directly, not through a propagated throw
81202f4 wip: harness+generator round-2 folds
23ff325 docs(gateway footprint): §2a is regenerated in place, and says only what the record carries
0ecf5f7 wip: drop tainted records
c4c5e0c wip: reqstate rewrites the section in place
94e2ea5 wip: harness declares throw-detection
236e742 wip: sync tooling
54e3952 docs(gateway footprint): the design carries what a test actually holds
1224cec test(gateway footprint): execute the four clauses the suite only read
3e13574 (origin/main, origin/HEAD) Merge pull request #172 from abhinav-jain09/docs/campaign-failure-lines
7e220e6 (origin/docs/campaign-failure-lines, docs/campaign-failure-lines) docs(gateway): the record now carries the failure lines its killedBy was parsed from
fc49c14 Merge pull request #171 from abhinav-jain09/docs/footprint-campaign-record
origin	git@github.com:abhinav-jain09/option-edge-feed-gateway.git (fetch)
origin	git@github.com:abhinav-jain09/option-edge-feed-gateway.git (push)
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
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md:13:5. **MEDIUM — doc:52** — The brokerless acceptance suite does not cover every normative requirement. Missing pins include topic/default settings, complete flag-off behavior (subscriptions, lazy views, hello bytes, HTTP 404 body/status, metrics), verbatim delivery and view-before-broadcast ordering, optional absent-topic startup, complete metric labels/counting, malformed-record containment, and the configured memory bounds. Allowlist membership alone also does not prove delivery behavior. **Required change:** expand G-R11 into explicit brokerless tests for every G-R1–G-R10 clause, including per-session routing tests proving all four events reach every authenticated socket and non-allowlisted events remain dropped.
./scripts/footprint-reqstate.sh:2:# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md IN PLACE from the checked-in campaign record.
./scripts/footprint-reqstate.sh:23:    '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
./scripts/footprint-reqstate.sh:60:    echo "§2a is stale: regenerate with scripts/footprint-reqstate.sh" >&2
./scripts/footprint-reqstate.sh:64:  echo "§2a matches the campaign record"
./scripts/footprint-reqstate.sh:69:echo "§2a regenerated in $DOC"
./scripts/footprint-reqstate.py:47:                t = m.get('killedByThrow')
./scripts/footprint-reqstate.py:53:                                    f"killedByThrow does not name a test and a throw that its own "
./scripts/footprint-mutate.py:227:        # the throw itself, and both must appear in the run's own failure lines. `killedByThrow:
./scripts/footprint-mutate.py:229:        want = m.get('killedByThrow') or {}
./scripts/footprint-mutate.py:266:                  'killedByThrow': (m.get('killedByThrow') if throw_kill_ok else None),
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:42:   It is unclear whether `records_total` counts consumed, accepted, or broadcast records; whether oversize records also increment it; when backfill request/rejection counters increment; and what the exhaustive `event` and `route` label values are. Consequently, G-R11’s “exact metrics contract” has no normative expected values.
./ES-FOOTPRINT-GATEWAY-DESIGN.md:107:| 5 | Acceptance suite incomplete | G-R11 enumerates a brokerless test per clause of G-R1–G-R10, including per-session fan-out |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:153:| G-R3 | **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`). `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt). All four events are added to the per-session standalone allowlist (D5). |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:160:| G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:161:| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:162:| G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:182:| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:189:| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
./ES-FOOTPRINT-CAMPAIGN.json:31:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:90:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:152:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:215:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:271:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:326:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:379:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:432:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:488:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:552:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:615:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:668:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:727:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:784:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:837:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:890:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:943:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:990:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1049:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1112:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1168:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1203: "G-R3.1 an-oversize-record-is-dropped-not-broadcast": {
./ES-FOOTPRINT-CAMPAIGN.json:1206:  "requirement": "G-R3",
./ES-FOOTPRINT-CAMPAIGN.json:1226:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1261: "G-R3.2 a-rejected-record-is-still-broadcast": {
./ES-FOOTPRINT-CAMPAIGN.json:1264:  "requirement": "G-R3",
./ES-FOOTPRINT-CAMPAIGN.json:1284:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1319: "G-R3.3 live-snapshots-are-never-retained": {
./ES-FOOTPRINT-CAMPAIGN.json:1322:  "requirement": "G-R3",
./ES-FOOTPRINT-CAMPAIGN.json:1339:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1397:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1465:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1528:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1581:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1634:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1687:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1743:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1778: "G-R10.1 a-footprint-record-never-escapes-the-consumer-loop": {
./ES-FOOTPRINT-CAMPAIGN.json:1781:  "requirement": "G-R10",
./ES-FOOTPRINT-CAMPAIGN.json:1798:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1831: "G-R10.2 every-admission-failure-is-a-counted-drop": {
./ES-FOOTPRINT-CAMPAIGN.json:1834:  "requirement": "G-R10",
./ES-FOOTPRINT-CAMPAIGN.json:1851:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1884: "G-R9.2 every-record-is-counted-before-admission": {
./ES-FOOTPRINT-CAMPAIGN.json:1886:  "clause": "records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too",
./ES-FOOTPRINT-CAMPAIGN.json:1888:  "documentText": "`gateway_footprint_records_total{event,consumer}` \u2014 every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).",
./ES-FOOTPRINT-CAMPAIGN.json:1893:  "enclosing": "footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();",
./ES-FOOTPRINT-CAMPAIGN.json:1907:  "killedByThrow": null,
./ES-FOOTPRINT-CAMPAIGN.json:1910:   "old": "        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {",
./ES-FOOTPRINT-CAMPAIGN.json:1911:   "new": "        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission\n        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {"
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:91:G-R9 overlap verification against G-R3/G-R4/G-R7:
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:93:- Oversize keyed live record: `records_total` + `drops_total{oversize}`; no view admission and no broadcast. Consistent with G-R3.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:94:- Shape/stale keyed live record: `records_total` + the applicable drop + `broadcast_total`. Consistent with G-R3/G-R4.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:95:- Admitted keyed live record: `records_total` + `broadcast_total`; possibly eviction or rollover depending on admission effects.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:96:- Cache-consumer keyed record: `records_total`; admission/drop/eviction/rollover applies, but never broadcast. The missing `consumer` dimension on `drops_total` makes this overlap insufficiently precise.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:108:   `records_total` distinguishes `cache` and `live`, while `drops_total` does not. Yet G-R9 refers to live-path oversize drops when defining broadcasts, without specifying whether the shared drop counter increments during cache admission, live admission, or both. Separately, a request can be both busy and malformed or session-mismatched, but no validation/acquisition precedence determines its HTTP result and single rejection reason.
./src/test/java/app/feedgateway/FootprintWiringTest.java:14: * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R1/G-R2/G-R3/G-R6/G-R9/G-R10/G-R11 — the wiring-shape pins, the
./src/test/java/app/feedgateway/FootprintWiringTest.java:90:    // ---- G-R1/G-R10: flag off is byte-identical ----------------------------------------------------
./src/test/java/app/feedgateway/FootprintWiringTest.java:126:    /** G-R3: the two live snapshot topics are never retained; the two keyed topics seek back a session. */
./src/test/java/app/feedgateway/FootprintWiringTest.java:157:    // ---- G-R3/G-R9: the shared admission path and overlap identities --------------------------------
./src/test/java/app/feedgateway/FootprintWiringTest.java:167:        assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint\",consumer=\"live\"} 1\n"), m);
./src/test/java/app/feedgateway/FootprintWiringTest.java:168:        assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint-bar\",consumer=\"live\"} 2\n"));
./src/test/java/app/feedgateway/FootprintWiringTest.java:200:        for (String e : events) for (String c : new String[]{"cache", "live"}) expect.add("gateway_footprint_records_total{event=\"" + e + "\",consumer=\"" + c + "\"} 0");
./src/test/java/app/feedgateway/FootprintWiringTest.java:213:        for (String name : List.of("gateway_footprint_enabled", "gateway_footprint_records_total", "gateway_footprint_drops_total", "gateway_footprint_broadcast_total",
./src/test/java/app/feedgateway/FootprintViewsTest.java:79:    /** G-R10: no admission failure escapes the consumer loop — a null payload is a counted drop. */
./src/test/java/app/feedgateway/FootprintSeamTest.java:164:    // ---- G-R3/D5: real socket fan-out through the live branch -----------------------------------------
./src/test/java/app/feedgateway/FootprintSeamTest.java:201:        assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint-bar\",consumer=\"live\"} 3\n"));
./src/main/java/app/feedgateway/FeedGatewayService.java:893:     * G-R3/G-R4/G-R5/G-R9: ONE admission path for both consumers. Returns true when the LIVE consumer
./src/main/java/app/feedgateway/FeedGatewayService.java:897:        footprintCounter("records_total{event=\"" + event + "\",consumer=\"" + consumer + "\"}").incrementAndGet();
./src/main/java/app/feedgateway/FeedGatewayService.java:963:    /** The live consumer's footprint branch (G-R3/G-R9): admit, then broadcast unless oversize. Package-private for the fan-out tests. */
./src/main/java/app/feedgateway/FeedGatewayService.java:998:        sb.append("# HELP gateway_footprint_records_total Footprint Kafka records polled, before admission.\n# TYPE gateway_footprint_records_total counter\n");
./src/main/java/app/feedgateway/FeedGatewayService.java:999:        for (String e : events) for (String c : consumers) line(sb, "gateway_footprint_records_total", "{event=\"" + e + "\",consumer=\"" + c + "\"}", footprintCounter("records_total{event=\"" + e + "\",consumer=\"" + c + "\"}").get());
./src/main/java/app/feedgateway/FeedGatewayService.java:1758:                + "# HELP gateway_es_auction_records_total SPX Auction Desk minute records forwarded verbatim as es-auction.\n"
./src/main/java/app/feedgateway/FeedGatewayService.java:1759:                + "# TYPE gateway_es_auction_records_total counter\n"
./src/main/java/app/feedgateway/FeedGatewayService.java:1760:                + "gateway_es_auction_records_total " + esAuctionRecords.get() + "\n"
./src/main/java/app/feedgateway/FeedGatewayService.java:2798:                        // ES Footprint (G-R3/G-R9): the CACHE consumer admits keyed records into the
./src/main/java/app/feedgateway/FeedGatewayService.java:3233:                        // ES Footprint (G-R3): keyed records go to the coordinator FIRST (the backfill's
./src/main/java/app/feedgateway/FeedGatewayService.java:12446:            // ES Footprint (G-R3, D5): ES-global market microstructure, identical for every
./src/test/java/app/feedgateway/EsAuctionWiringTest.java:189:        assertEquals(0L, s.esAuctionRecordsForTest(), "records_total counts what was FORWARDED, not hydrated");
./src/test/java/app/feedgateway/EsAuctionWiringTest.java:994:        assertTrue(metrics.contains("# TYPE gateway_es_auction_records_total counter\ngateway_es_auction_records_total 1\n"));

codex
The four stated Round 3 corrections are present at a first-pass textual level. I’m now checking whether the generated table’s claims are actually supported record-by-record, including mutation survival evidence rather than just key names.
exec
/bin/bash -lc '/usr/bin/git diff --no-ext-diff --unified=80 origin/main...HEAD -- ES-FOOTPRINT-GATEWAY-DESIGN.md scripts/footprint-reqstate.py scripts/footprint-reqstate.sh scripts/footprint-mutate.py 2>/dev/null' in /private/tmp/oe-conf-gw
 succeeded in 3119ms:
diff --git a/ES-FOOTPRINT-GATEWAY-DESIGN.md b/ES-FOOTPRINT-GATEWAY-DESIGN.md
index 315acad..10133df 100644
--- a/ES-FOOTPRINT-GATEWAY-DESIGN.md
+++ b/ES-FOOTPRINT-GATEWAY-DESIGN.md
@@ -84,122 +84,157 @@ F-R14 topics and keys, F-R15 live publication, F-E8 bounds, F-R20). Downstream:
 ### Revision-4 change log (Codex round 3 → disposition)
 
 | # | Finding | Disposition |
 |---|---------|-------------|
 | 1 | Transient (StringBuilder/encoding) and Kafka-poll bounds unproven; JVM layout assumptions unenforced | G-R7 mandates a streamed, byte-oriented response with a fixed buffer and a permit lifecycle; G-R8 derives the Kafka bound from the gateway's ENFORCED consumer settings and the oversized-first-batch rule, verifies the three HotSpot layout flags at start-up (fail-closed), and recomputes the total and both inequalities |
 | 2 | `drops_total` lacks the consumer dimension; route rejection precedence undefined | G-R9 adds `consumer` to `drops_total` and defines `broadcast_total` from live-path counts only; G-R7 fixes the route processing order so exactly one rejection reason applies; G-R11 tests overlapping conditions |
 
 ### Revision-3 change log (Codex round 2 → disposition)
 
 | # | Finding | Disposition |
 |---|---------|-------------|
 | 1 | Retained-heap bound unproven (average-based) | G-R8 rewritten with a per-entry overhead bound from the count limits and the payload bound from the byte limits — no average anywhere; transient backfill memory bounded by a concurrency limit |
 | 2 | Contingency ignores native memory (`-Xmx` = container limit today) | G-R8/§4: the container limit must exceed `-Xmx` by a justified native margin AND a measured working-set peak must leave the projected footprint headroom; both recorded in the deploy PR |
 | 3 | Metrics semantics/labels undefined | G-R9 defines every series' increment rule, overlap rule and exhaustive label domain; G-R11 (12) pins expected values |
 
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
 | Backfill `GET /api/cvd/bars?tf&toMs&afterMs&limit&sessionDate` → `{sessionDate, [sessionMismatch], bars[], nextCursor}`; page, cursor and session stamp are ONE synchronized snapshot; typed `long`/`int` parameters are bound by Spring BEFORE the handler runs (malformed ⇒ 400 with no footprint code executed) | `GatewayController.java:50-72`; `FeedGatewayService.java:10247-10269` |
 | REST authentication is EXPLICIT and in-handler where a route requires it: such routes call `LiquidityHistoryAuth.authenticate(...)` themselves (there is no JWT servlet filter for `/api`) — the precedent is `LiquidityHistoryController`; the CVD bars route makes NO such call and is cited in this document ONLY as the typed-binding and backfill-semantics precedent | `liquidityhistory/LiquidityHistoryController.java:86`; `GatewayController.java:50-72` |
 | Per-session (auth) routing drops any event not in the standalone allowlist; `es-cvd` / `es-cvd-bar` are allowlisted | `FeedGatewayService.java:11493-11494` |
 | The external ES CVD page stores the hello object and reads only `sessionDate`/`hwm`, so an added field is ignored by it | `options-edge/src/app/web/assets/es-cvd.js:278-304` |
 | Tests: view semantics and the one-wiring-path pin | `src/test/java/app/feedgateway/CvdBarViewTest.java` |
 | es4 deployment: `JAVA_TOOL_OPTIONS=-Xms256m -Xmx1536m`, container memory limit `1536Mi` | `options-edge-deploy/k8s/es4/services/es-feed-gateway.yaml:120-121`, `:176-178` |
 | Manually assigned consumers discover topology growth through `PartitionRefresh.apply(consumer, assigned)`, called on the CONSUMER'S OWN poll thread every `partitionMetadataRefreshMs`: it re-reads partitions for the consumer's (immutable, `Set.copyOf`) topic set, merges the newly discovered partitions and calls `consumer.assign(merged)` ITSELF, then returns a `Refresh` whose `added()` the CALLER seeks and books (never shrinks; a metadata timeout is absorbed and retried); an optional topic absent at bootstrap is discovered by exactly this path when it appears | `FeedGatewayService.java:3596-3680` (`PartitionRefresh`; the internal assign at `:3665-3671`), `apply()` call sites `:2310` (state cache) and `:2677` (state live) |
 | Consumer settings the gateway ENFORCES on every consumer: `max.poll.records` = `GATEWAY_KAFKA_MAX_POLL_RECORDS` (default 100), `fetch.max.bytes` = `GATEWAY_KAFKA_FETCH_MAX_BYTES` (default 4 MiB), `max.partition.fetch.bytes` = `GATEWAY_KAFKA_MAX_PARTITION_FETCH_BYTES` (default 512 KiB) | `GatewaySettings.java:1072`, `:1077`, `:1081`; `FeedGatewayService.java:12294-12296` |
 
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
 | G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's). `GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` ascending by view key within `tf`, `toMs` inclusive on `resolvedAtBarStartMs`, `after` = an EXCLUSIVE lower bound given as a previous page's `nextCursor` (the last key string). Cursor grammar: `tf|<19 digits>|<identity>` where `tf` equals the query `tf`; an `after` that is non-empty and fails the grammar ⇒ HTTP 400 `{"error":"bad cursor"}`; a well-formed cursor that no longer exists is legal (string lower bound). `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from. Same JWT gate as every `/api` route. **Processing order (exactly one outcome per request, in the AS-IS convention):** (1) Spring typed binding of `toMs`/`limit`/`afterMs` ⇒ 400 on malformed input BEFORE the handler runs (so a malformed request is never counted and never sees the flag or auth); (2) in the handler, flag off ⇒ 404 `{"enabled":false}`; (3) explicit `LiquidityHistoryAuth.authenticate(...)` exactly as `LiquidityHistoryController` does (`:86`) ⇒ its 401/403 on failure; (4) permit: `tryAcquire` on a `Semaphore(GATEWAY_ES_FOOTPRINT_BACKFILL_CONCURRENCY)` (default 4) ⇒ 503 `Retry-After: 1` (`busy`) when none is free; (5) cursor grammar (outcomes route) ⇒ 400 `{"error":"bad cursor"}`; (6) page snapshot under the coordinator lock — the session check (`sessionMismatch`, HTTP 200) and the selection of up to `limit` record REFERENCES into a `List<String>` (no copies) plus `sessionDate` and `nextCursor`; (7) the response is STREAMED: the controller writes to `HttpServletResponse.getOutputStream()` through ONE fixed 64 KiB buffer, the envelope pieces and each record as `record.getBytes(US_ASCII)` one at a time (records are ASCII by F-E8), status 200 set before the first byte; the permit is released in `finally` after the stream is flushed (or the write failed). The lock is held only during (6), never during (7). Transient memory per request is therefore ≤ one record's bytes (≤ 262 144 B) + the 64 KiB buffer + the reference list (≤ 100 × 4 B) < 0.32 MiB (G-R8). |
 | G-R8 | **Bounds and heap (proven from the limits, never from averages).** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key. **Retained-heap bound** (HotSpot, compressed oops and compressed class pointers — true for any heap < 32 GiB, as here): a JSON value is ASCII (F-E8 alphabet) so it is a Latin-1 compact `String`: 24 B object + 16 B array header + payload, aligned to 8 ⇒ ≤ payload + 48 B; a key `String` ≤ 160 B payload ⇒ ≤ 208 B; a `TreeMap.Entry` = 40 B; per entry overhead ≤ 296 B, so overhead ≤ (12 000 + 20 000) × 296 B = 9.04 MiB; payload ≤ 128 + 16 = 144 MiB; the coordinator's accounting (two `long` counters, one `LocalDate`, per-timeframe eviction cursors) < 1 KiB. Views total ≤ 153.1 MiB. **Transient** per in-flight backfill (G-R7 step 7): < 0.32 MiB, at most 4 concurrent ⇒ < 1.3 MiB; no `StringBuilder`, no response `String`, no encoder buffer beyond the fixed 64 KiB. **Kafka transient**, from the client's fetch mechanics and THREE enforced facts. Mechanics (Kafka consumer `Fetcher`, unchanged since KIP-74): the consumer keeps at most ONE in-flight fetch request per broker node, a fetch request for a node includes only assigned partitions that have NO buffered (completed, unconsumed) data, each partition's share of a response is ≤ `max.partition.fetch.bytes` except that the FIRST batch of a partition is returned whole when it is larger, and `max.poll.records` only paces how many buffered records a `poll()` hands out. Enforced facts: (i) `max.partition.fetch.bytes` = 512 KiB (§1); (ii) `M` = each footprint topic's effective `max.message.bytes` ≤ `GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588, the broker default), validated per G-R8a; (iii) the four topics are UNCOMPRESSED end to end — the upstream producer sets `compression.type=none` explicitly (options-edge-processing amendment: `EsCvdRuntime.producerProps`, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` per G-R8a — so a fetched share IS its decompressed size and no decompression buffer exists. Per consumer, each of the four single-partition footprint topics contributes at most: one in-flight share (≤ max(512 KiB, M) ≤ 1.0 MiB) + one buffered completed share (≤ 1.0 MiB) + the deserialized value `String`s of the records a `poll()` returned from that share (≤ the share's bytes + 48 B per record) ⇒ ≤ 3.0 MiB per topic + per-record overhead, ≤ 12.0 MiB per consumer + per-record overhead. Per-record client overhead for the records of ONE poll — here, and only here, bounded by `max.poll.records` = 100 (§1): key `String` ≤ 208 B, `ConsumerRecord` object ≤ 96 B (header, 7 references, two longs, two ints), empty `RecordHeaders` ≤ 32 B, `ConsumerRecords` map/list share ≤ 64 B ⇒ ≤ 400 B × 100 = 40 KiB. Per consumer ≤ 12.04 MiB; TWO consumers touch footprint topics (state cache, state live) ⇒ ≤ 24.1 MiB, rounded to 25 MiB. Non-footprint partitions in the same fetch responses are not attributable to this feature and are already part of the measured `H_peak`. **Layout assumptions are verified at start-up** when the flag is on: `HotSpotDiagnosticMXBean.getVMOption` for `UseCompressedOops`, `UseCompressedClassPointers` and `CompactStrings` must all be `true`, else the gateway refuses to start naming the flag (the arithmetic above is void otherwise); pinned by a test of the checker over a fake option map. Topic-side facts (ii) and (iii) are verified by G-R8a. **Footprint heap contribution ≤ 153.1 + 1.3 + 25 = 179.4 MiB, rounded to 180 MiB**, independent of record size distribution. **Deployment contingency (§4):** the flag may be enabled only when the deploy PR records, from a full ES session with CVD on and footprint off: (a) heap: `jvm_memory_bytes_used{area="heap"}` peak `H_peak` with `-Xmx − H_peak ≥ 180 MiB + 128 MiB = 308 MiB` (GC headroom); (b) container: `container_memory_working_set_bytes` peak `W_peak` with `limit − W_peak ≥ 180 MiB + 256 MiB = 436 MiB`, AND `limit − Xmx ≥ 512 MiB` (metaspace, code cache, thread stacks, socket/direct buffers, JVM overhead — the current es4 manifest has `limit = Xmx = 1536 MiB`, which violates this and MUST change in the same PR: e.g. `-Xmx1536m` with `limit 2560Mi`, or a measured alternative that satisfies both inequalities). Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits and the tests pin them. |
 | G-R8a | **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`. **Start-up** (`start()`, before any consumer launches): validate the topics that EXIST; an existing INVALID topic ⇒ refuse to start naming topic and value (fail-closed); an absent topic (`UnknownTopicOrPartitionException` for that resource) ⇒ deferred; any other Admin failure ⇒ refuse to start. **The seam (an API change to `PartitionRefresh`, the AS-IS contract otherwise kept):** `PartitionRefresh` gains a constructor parameter `Predicate<String> topicAdmit`: of the SIX existing constructor call sites (`:1739` selection, `:2087` hpsf-cache, `:2125` hpsf-live, `:2201` alerts, `:2303` state cache, `:2674` state live), the two STATE consumers pass `footprintGate::admit` and the other FOUR pass `t -> true`. Inside `apply()`, AFTER `partitionsFor` discovers new partitions and BEFORE `mergedAssignment`/`consumer.assign(...)` (`FeedGatewayService.java:3665-3671`), discovered partitions whose topic fails `topicAdmit` are WITHHELD: they are not merged, not assigned, not in `Refresh.added()`, hence never sought and never in the caller's caught-up bookkeeping — identical to a topic that does not exist yet; they are re-discovered (and re-evaluated) at the next refresh interval. The same predicate is applied to the bootstrap `partitionsFor` result before the initial `assign` in both state consumers. `addEsFootprintTopics` adds ALL FOUR topics to both consumers' `topicEvents` unconditionally, so the immutable `Set.copyOf(topics)` always contains them and discovery works whenever a topic appears. **`FootprintTopicGate.admit(topic)`** runs on the calling poll thread: a non-footprint topic ⇒ true; a footprint topic already marked VALID ⇒ true; otherwise it validates NOW (bounded timeout): VALID ⇒ mark and true; unknown topic, timeout or other Admin failure ⇒ false this cycle (counted, logged once per state change, retried at the next refresh — the absorbed-refresh-failure discipline); existing-but-invalid ⇒ false (counted `ceiling`/`compression`, logged once per state change). Per-topic state in a `ConcurrentHashMap` shared by both consumers (each evaluates on its own thread; a duplicate validation is harmless); once VALID a topic stays VALID for the incarnation (a topic config change after admission requires a gateway restart — §4 operational rule). Adoption of a late-admitted topic then follows the existing caller behaviour for `Refresh.added()`: the cache consumer seeks its window (a compacted keyed topic replays its retained keys into the coordinator, as `es-cvd-bar` does), the live consumer seeks END. The two validation series are defined in G-R9. |
 | G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
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
+| G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
+| G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
+| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
+| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
+| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
+| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
+| G-R7 | 5 of 6 probes pinned, over 6 clauses (1 survived) | 2 | Backfill routes |
+| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
+| G-R9 | 2 of 2 probes pinned, over 2 clauses | 2 | Metrics |
+| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
+| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
+| G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | Deployment contingency |
+
+12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).
+
+Read the state column narrowly. "n of m probes pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION (or propagate a throw the spec declared and the run matched) — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.
+
+<!-- END footprint-reqstate -->
+
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
 
 ## 4. Deploy (es4) and the upstream prerequisite
 
 **Upstream prerequisite (options-edge-processing) — MERGED:** PR #757 (`feat/es-footprint-uncompressed`,
 Codex APPROVE) sets `compression.type=none` explicitly in `EsCvdRuntime.producerProps`, pins it by
 test and records ES-FOOTPRINT-DESIGN.md amendment 12; fact (iii) of G-R8 therefore holds by
 contract on the producer side, and G-R8a's `compression.type` check on each topic remains the
 continuing runtime guard against a topic-level codec being configured later.
 Operational rule: a footprint topic's config change after admission requires a gateway
 restart (G-R8a caches VALID per incarnation).
 
 
 `k8s/es4/services/es-feed-gateway.yaml`: `GATEWAY_ES_FOOTPRINT_ENABLED=true`, the four
 `KAFKA_ES_FOOTPRINT_*` names (prefixed to `es.futures.footprint*` by the gateway),
 `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` equal to the service's `FOOTPRINT_MAX_RECORD_BYTES`, after the
 service PR is deployed. The SAME deploy PR must (1) record `H_peak` and `W_peak` from a full session
 (Prometheus queries quoted in the PR), (2) prove both G-R8 inequalities, and (3) set the container
 limit to at least `Xmx + 512 MiB` — the current `1536Mi = -Xmx1536m` fails that inequality on its
 own, so the PR changes the limit (or `-Xmx`) whatever the measurements say.
 
 ## 5. Codex review request (round 10)
 
 Check the round-9 disposition (the two validation series in G-R9's contract and G-R11's pins);
 verdict `APPROVE` or `REQUEST_CHANGES` with numbered findings.
diff --git a/scripts/footprint-mutate.py b/scripts/footprint-mutate.py
index 9e77d4b..21829a8 100644
--- a/scripts/footprint-mutate.py
+++ b/scripts/footprint-mutate.py
@@ -1,162 +1,281 @@
 #!/usr/bin/env python3
 """Mutation campaign harness — repo-agnostic, and it records what it did.
 
 A mutation result is only worth as much as its provenance, so every entry carries the exact patch,
 which occurrence of it was changed and at what line and in what enclosing declaration, the command
 that was run, and how the run ended. Three outcomes are distinguished, because conflating them is
 how a campaign starts lying:
 
   KILLED         a TEST failed. The clause is pinned.
   SURVIVED       the suite passed. Nothing caught the regression.
   BUILD-FAILED   the mutant did not compile, or the run died before testing. Says nothing about
                  pinning; the mutation must be rewritten.
 
 A clean baseline is proven before any mutation is applied — a campaign against an already-red suite
 would report every clause as pinned.
 """
-import json, subprocess, sys, os, shutil, re, time, hashlib
+import json, subprocess, sys, os, shutil, signal, re, time, hashlib
 
 def failure_lines(out, kind):
     """The VERBATIM lines the killedBy names were parsed from, so the record substantiates itself.
     A tail of the output does not: Maven prints its failures well before the build summary, so the
     last N characters of a run contain none of the names the harness reported."""
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
         #   [ERROR]   ClassName.method:123 message
         #   [ERROR] pkg.ClassName.method -- Time elapsed ... <<< FAILURE!
         # A naive (\w+\.\w+) truncates the second to its package, which then gets recorded as the
         # failing test. Take the last two dotted segments before the colon or the double dash.
         names = set()
         for m in re.finditer(r'^\[ERROR\]\s+([\w.$]+\.[\w$]+)(?::\d+|\s+--|\s)', out, re.M):
             parts = m.group(1).split('.')
             if len(parts) >= 2 and parts[-1][:1].islower() or len(parts) >= 2:
                 names.add('.'.join(parts[-2:]))
         return sorted(names)
     return sorted({m.group(1).strip() for m in re.finditer(r'^not ok \d+ - (.+)$', out, re.M)})
 
 def build_failed(out, kind):
     if kind == 'maven':
         return 'COMPILATION ERROR' in out or 'BUILD FAILURE' in out and 'Tests run:' not in out
     return 'SyntaxError' in out
 
 def run(cmd, cwd):
     p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=3600)
     return p.returncode, p.stdout + p.stderr
 
 def sha(text):
     return hashlib.sha256(text.encode('utf-8', 'replace')).hexdigest()
 
 def head_commit(root):
     rc, out = run(['git', 'rev-parse', 'HEAD'], root)
     return out.strip() if rc == 0 else None
 
 def tree_dirty(root):
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
     if dirty:
         print("TREE IS DIRTY — a campaign must run against a committed tree, or its record names a state"); 
         print("nobody can return to:"); [print("  ", d) for d in dirty[:10]]; sys.exit(3)
     commit = head_commit(root)
     rc, out = run(cmd, root)
     if rc != 0:
         print("BASELINE IS RED — refusing to run a campaign against a failing suite"); print(out[-3000:]); sys.exit(2)
     baseline = {'commit': commit, 'command': ' '.join(cmd), 'returnCode': rc,
                 'outputSha256': sha(out), 'outputTail': out[-1500:]}
     base_tests = re.search(r'Tests run: (\d+)', out) or re.search(r'# pass (\d+)', out)
     print(f"baseline GREEN ({base_tests.group(1) if base_tests else '?'} tests) — {' '.join(cmd)}\n")
 
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
         while True:
             i = src.find(m['old'], start)
             if i < 0: break
             seen += 1
             if seen == occ: pos = i; break
             start = i + 1
         line = src[:pos].count('\n') + 1
         lines = src.split('\n')
         # The enclosing DECLARATION, found by scanning FORWARD from the top of the file and tracking
         # brace depth, so the answer is the declaration whose body actually contains the mutated line.
         # A backwards "nearest line that looks like a signature" scan is what reported appendHwm()
         # for a mutation inside barsPage(); a forensic record that misplaces the mutation is worse
         # than none.
         encl, depth, stack = None, 0, []
         for idx, t in enumerate(lines, start=1):
             opens = t.count('{')
             stripped = t.strip()
             is_decl = ('(' in stripped and not re.match(r'^(if|for|while|switch|catch|try|else|do|synchronized|return)\b', stripped)) \
                       or re.match(r'^\s*(public|private|protected|static|final|abstract)?\s*(class|interface|enum|record)\b', t)
             if opens and is_decl:
                 stack.append((depth, ' '.join(t.split())[:140]))
             depth += opens - t.count('}')
             if idx == line:
                 # Capture BEFORE popping. A one-line method opens and closes on the same line, so a
                 # pop-first order removes it from the stack before the mutated line can name it —
                 # which is how `static int clamp(...)` was recorded as `public class GatewayController`.
                 encl = stack[-1][1] if stack else None
                 break
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
+        # A declared throw-kill must name BOTH the test that should propagate it and a signature of
+        # the throw itself, and both must appear in the run's own failure lines. `killedByThrow:
+        # true` alone would accept any error under any failing test.
+        want = m.get('killedByThrow') or {}
+        throw_kill_ok = False
+        if isinstance(want, dict) and want.get('test') and want.get('throws'):
+            lines = '\n'.join(failure_lines(out, kind))
+            throw_kill_ok = (want['test'] in failed or any(want['test'] in f2 for f2 in failed)) \
+                and want['throws'] in lines
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
+        elif rc != 0 and failed and throw_kill_ok:
+            # The clause under test IS "this must not throw", so a test that propagates the throw is
+            # detecting exactly the right thing. The spec must SAY so in advance AND name the throw
+            # it expects — a bare boolean would accept any incidental error under any failing test,
+            # which is the laundering this whole distinction exists to prevent.
+            status = 'KILLED'
+        elif rc != 0 and failed:
+            # the right test failed, but on a THROW rather than an assertion, and the spec did not
+            # declare that mode: the mutation may have broken the fixture rather than the behaviour
+            status = 'KILLED-BY-ERROR'
         elif rc != 0:
             status = 'BUILD-FAILED'
         else:
             status = 'SURVIVED'
         res[k] = {'status': status, 'clause': m.get('clause',''), 'requirement': m.get('requirement', k.split()[0]),
                   'documentText': m.get('documentText'),
                   'file': m['file'], 'occurrence': occ, 'occurrencesInFile': n, 'line': line,
-                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': failed[:4],
+                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': asserted[:4] or failed[:4], 'assertionFailures': asserted[:4], 'anyFailures': failed[:4],
+                  'killedByThrow': (m.get('killedByThrow') if throw_kill_ok else None),
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
                   'baseline': baseline}
         print(f"  {k:<40} {status:<13} {(failed[0][:52] if failed else '')}")
         json.dump(res, open(outp,'w'), indent=1)
     from collections import Counter
     print("\n", Counter(v['status'] for v in res.values()))
 
 main()
diff --git a/scripts/footprint-reqstate.py b/scripts/footprint-reqstate.py
new file mode 100644
index 0000000..d24772e
--- /dev/null
+++ b/scripts/footprint-reqstate.py
@@ -0,0 +1,112 @@
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
+    # Discover requirement ids from the AUTHORITATIVE part of the document only — everything before
+    # the generated block. Reading the whole file lets the previous render's own rows keep a
+    # requirement alive after it has been deleted upstream, and --check would then accept it.
+    text = open(docPath).read()
+    # Use the SAME rule as the rewriter: the marker at LINE START. Finding the first substring
+    # anywhere lets a prose mention of the marker truncate discovery before the real block.
+    mk = re.search(r'^<!-- BEGIN footprint-reqstate', text, re.M)
+    authoritative = text if mk is None else text[:mk.start()]
+    ids = []
+    for line in authoritative.split('\n'):
+        m = re.match(r'^\|\s*(' + idPattern + r')\s*\|', line)
+        if m and m.group(1) not in ids: ids.append(m.group(1))
+    per = collections.defaultdict(list)
+    keys = collections.defaultdict(list)
+    for k, v in rec.items():
+        _rid = v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', k).group(1)
+        per[_rid].append(v)
+        keys[_rid].append(k)
+    # A record is only allowed to say "pinned" when it carries the evidence for it: an assertion
+    # failure naming a test, and the verbatim failure lines that name was parsed from. A record that
+    # merely says KILLED is a claim, not evidence — and one kill in this campaign turned out to be a
+    # format-string error under the right test's name, which is exactly what this refuses.
+    problems = []
+    for rid, ms in per.items():
+        for m in ms:
+            if m['status'] != 'KILLED':
+                continue
+            if not m.get('assertionFailures'):
+                # The renderer must hold this itself: a record is a checked-in FILE, and a hand-edited
+                # or legacy one would otherwise be taken at its word.
+                t = m.get('killedByThrow')
+                lines = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
+                ok = (isinstance(t, dict) and t.get('test') and t.get('throws')
+                      and t['test'].split('.')[-1] in lines and t['throws'] in lines)
+                if not ok:
+                    problems.append(f"{rid}: a KILLED record names no ASSERTION failure, and its "
+                                    f"killedByThrow does not name a test and a throw that its own "
+                                    f"failure lines carry ({m.get('killedBy')})")
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
+            # Count PROBES, not clauses: a clause implemented at several sites is probed once per
+            # site AND once for all sites together, so "n of m clauses" would triple-count it.
+            groups = len({re.sub(r' site\d+$', '', k) for k in keys[rid]})
+            state = f"{killed} of {len(ms)} probes pinned, over {groups} clause" + ("" if groups == 1 else "s")
+            rest = ", ".join(f"{n} {st.lower()}" for st, n in sorted(by.items()))
+            if rest: state += f" ({rest})"
+        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
+    # Count only what the table shows. A record for a requirement the document no longer
+    # renders is omitted from the rows, and must be omitted from the totals with it.
+    rendered = [v for rid in ids for v in per.get(rid, [])]
+    tot = collections.Counter(v['status'] for v in rendered)
+    out.append("")
+    probed_here = len([r for r in ids if r in per])
+    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rendered)} mutations "
+               f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving"
+               + (", " + ", ".join(f"{n} {st.lower()}" for st, n in sorted(tot.items()) if st not in ('KILLED', 'SURVIVED')) if len(tot) > 2 else "")
+               + ")."
+               "\n\nRead the state column narrowly. \"n of m probes pinned\" says that breaking those "
+               "clauses in the production source made a NAMED test fail an ASSERTION (or propagate a "
+               "throw the spec declared and the run matched) — "
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
index 0000000..f3b3115
--- /dev/null
+++ b/scripts/footprint-reqstate.sh
@@ -0,0 +1,69 @@
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
+# EXACTLY one marker pair, BEGIN before END, each at the start of its own line. A second pair, a
+# reversed pair, or a marker mentioned inside prose would let the rewrite duplicate the generated
+# block or swallow an unrelated section of the document.
+nb=$(grep -cE "^${BEGIN}" "$DOC" || true)
+ne=$(grep -cE "^${END}$"  "$DOC" || true)
+[ "$nb" = "1" ] || { echo "FATAL: $DOC has $nb BEGIN markers at line start; exactly one is required" >&2; exit 1; }
+[ "$ne" = "1" ] || { echo "FATAL: $DOC has $ne END markers at line start; exactly one is required" >&2; exit 1; }
+lb=$(grep -nE "^${BEGIN}" "$DOC" | cut -d: -f1)
+le=$(grep -nE "^${END}$"  "$DOC" | cut -d: -f1)
+[ "$lb" -lt "$le" ] || { echo "FATAL: in $DOC the END marker (line $le) precedes BEGIN (line $lb)" >&2; exit 1; }
+
+tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
+table > "$tmp/table.md"
+
+# Rebuild the document: everything before the marker, then the section heading, then the prose from
+# scripts/footprint-reqstate.preamble (hand-maintained — it explains the table and is reviewed like
+# any other prose), then the generated table, then everything from the END marker on. Only the table
+# is derived from the record; the preamble is not, and is kept in its own file so that is visible.
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

exec
/bin/bash -lc "sed -n '168,215p' ES-FOOTPRINT-GATEWAY-DESIGN.md; jq -r 'to_entries[] | [.key,.value.requirement,.value.clause,.value.documentText,.value.status,((.value.assertionFailures//[])|join(\";\")),((.value.killedByThrow//{})|tostring),((.value.evidence.failureLines//[])|join(\" || \"))] | @tsv' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
Every requirement above, and what this repository's mutation campaign established about it. The
campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
the spec declared in advance — never an incidental error under the right test's name; and the table
below refuses to render a "pinned" cell for any record that does not carry its evidence. That
evidence — the patch, the file, line and enclosing declaration, the command, the exit code, the
verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
record with `scripts/footprint-reqstate.sh --check`.

| id | Conformance | Gate | Disposition |
|----|-------------|------|-------------|
| G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
| G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
| G-R7 | 5 of 6 probes pinned, over 6 clauses (1 survived) | 2 | Backfill routes |
| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
| G-R9 | 2 of 2 probes pinned, over 2 clauses | 2 | Metrics |
| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
| G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | Deployment contingency |

12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).

Read the state column narrowly. "n of m probes pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION (or propagate a throw the spec declared and the run matched) — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.

<!-- END footprint-reqstate -->

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
G-R4 older-date-dropped	G-R4	an OLDER session date is dropped	**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.	KILLED	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts;FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders;FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly;FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	{}	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.260 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>
G-R4 newer-date-rolls-both	G-R4	a NEWER date rolls BOTH views	Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;	KILLED	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts	{}	[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.009 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>
G-R4 equal-date-upserts	G-R4	an EQUAL date upserts without rolling	**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.	KILLED	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit;FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic;FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact;FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin	{}	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.012 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {"sessionDate":"2026-08-14","bars":[{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"30s","sessionDate":"2026-08-14","observations":{"barStartMs":5000},"tag":"x"}],"nextCursor":null} ==> expected: <true> but was: <false> || [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0> || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{},"outcomeHwm":{"5m":70}}>
G-R4 rollover-counted	G-R4	a rollover is counted	Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;	KILLED	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts	{}	[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>
G-R8 evict-oldest	G-R8	evict the OLDEST key of the timeframe	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	KILLED	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact;FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin	{}	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>
G-R8 evict-rotates	G-R8	eviction ROTATES across timeframes	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	KILLED	FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin	{}	[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.057 s <<< FAILURE! || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:245 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>
G-R8 byte-budget-decremented	G-R8	evicting decrements the byte total	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	KILLED	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact	{}	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <3>
G-R8 both-budgets-enforced	G-R8	both the count AND byte budgets are enforced	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	KILLED	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact	{}	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <0>
G-R8 evictions-counted	G-R8	evictions are counted per view	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	KILLED	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact;FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin	{}	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:260 expected: <1> but was: <0> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>
G-R8a ceiling-strict	G-R8a	the ceiling is inclusive; only ABOVE is refused	**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.	KILLED	FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts;FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows;FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne;FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions	{}	[ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.005 s <<< ERROR! || [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.003 s <<< ERROR! || [ERROR] app.feedgateway.FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0.002 s <<< ERROR! || [ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.007 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts:68 the validated topic is added; the unvalidated one is WITHHELD from added() ==> expected: <[futures.footprint.bars-0]> but was: <[]> || [ERROR]   FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows:106 expected: <[futures.cvd.bars-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd.bars-0]> || [ERROR]   FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne:99 expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]>
G-R8a codec-allowlist	G-R8a	only producer/uncompressed are accepted	**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.	KILLED	FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted	{}	[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:51 gzip ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.
G-R9 ceiling-precedence	G-R9	ceiling takes precedence over compression	`gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).	KILLED	FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt	{}	[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR]   FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt:62 expected: <1> but was: <0>
G-R7 to-is-inclusive	G-R7	toMs is INCLUSIVE	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	KILLED	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit;FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim;FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic	{}	[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.016 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2> || [ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false> || [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:200 expected: <3> but was: <2>
G-R7 limit-clamped-low	G-R7	the forwarded limit is clamped to at least 1	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	KILLED	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit	{}	[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>
G-R7 limit-clamped-high	G-R7	the forwarded limit is clamped to the ceiling	`limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.	KILLED	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit	{}	[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.009 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>
G-R7 epoch-guard	G-R7	an out-of-domain timestamp is refused	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	KILLED	FootprintViewsTest.shapeDropsNeverMutate	{}	[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {"timeframe":"1m","sessionDate":"2026-08-14","observations":{"barStartMs":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>
G-R7 epoch-guard-removed	G-R7	a cursor at or past the last epoch value can have nothing after it, and the increment is taken only inside the domain	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	KILLED	FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain	{}	[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:185 a cursor beyond the domain admits nothing — it must not wrap ==> expected: <0> but was: <4>
G-R7 epoch-guard-relaxed	G-R7	the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	SURVIVED		{}	
G-R1.1 the-flag-gates-every-footprint-object	G-R1	Flag off: the views are never instantiated	Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.	KILLED	FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing;FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose;FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions;FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge	{}	[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.419 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.013 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.325 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401> || [ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123 || [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]> || [ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true>
G-R2.1 one-wiring-method-binds-all-four	G-R2	ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers	ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.	KILLED	FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven	{}	[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot
G-R2.2 the-evidence-binding	G-R2	KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)	**Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).	KILLED	FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics;FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven	{}	[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.014 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint" ==> expected: <1> but was: <2> || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-
G-R3.1 an-oversize-record-is-dropped-not-broadcast	G-R3	a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped	`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).	KILLED	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone;FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	{}	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.171 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.017 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>
G-R3.2 a-rejected-record-is-still-broadcast	G-R3	a record the view REJECTS for shape or session is still broadcast	`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).	KILLED	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone;FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	{}	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.144 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.024 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>
G-R3.3 live-snapshots-are-never-retained	G-R3	never cached, never retained	**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).	KILLED	FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession	{}	[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>
G-R5.1 the-outcome-key-orders-numerically	G-R5	the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)	The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;	KILLED	FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession;FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth	{}	[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.180 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.002 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR! || [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8> || [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> || [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "10|ES.v.0|1m|1|BUYE" || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "5|ES.v.0|1m|1|BUYER" || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "77|ES.v.0|5m|1|SELL" || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » StringIndexOutOfBounds Range [3, 22) out of bounds for length 8
G-R5.2 the-identity-is-the-last-component	G-R5	identity CONTAINS |, which is why it is the LAST component	`identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).	KILLED	FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession;FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth	{}	[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.120 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.002 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR! || [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: "id1|000000000000000"> || [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005> || [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|" || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|" || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "z|00000000000000000" || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|"
G-R5.3 a-foreign-identity-is-refused	G-R5	a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)	Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.	KILLED	FootprintViewsTest.shapeDropsNeverMutate	{}	[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:40 expected: <SHAPE> but was: <ADMITTED>
G-R5.4 last-write-per-key-wins	G-R5	Last write per full key wins; a redelivery carries the same key and overwrites in place	Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.	KILLED	FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace	{}	[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>
G-R6.1 the-hello-carries-the-footprint-field	G-R6	the existing cvd-hello frame gains ONE field footprint	**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).	KILLED	FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot	{}	[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:152 expected: <{"sessionDate":null,"hwm":{},"footprint":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}> but was: <{"sessionDate":null,"hwm":{},"footprintX":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}>
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on	G-R6	cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled	`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.	KILLED	FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled	{}	[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>
G-R6.3 the-hello-carries-the-outcome-high-water-mark	G-R6	outcomeHwm:{tf: maxResolvedAtBarStartMs}	**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).	KILLED	FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks;FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot	{}	[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"30s":2000,"1m":5}}> || [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop	G-R10	a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw	A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.	KILLED	FootprintViewsTest.aNullPayloadIsARefusalNotAThrow	{}	[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:85 a null bar payload must not throw ==> Unexpected exception thrown: java.lang.IllegalArgumentException: json
G-R10.2 every-admission-failure-is-a-counted-drop	G-R10	every admission failure is a counted drop	A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.	KILLED	FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	{}	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.248 s <<< FAILURE! || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>
G-R9.2 every-record-is-counted-before-admission	G-R9	records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too	`gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).	KILLED	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone;FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	{}	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.204 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.017 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>

exec
/bin/bash -lc "python3 -c 'import json,re; D=open(\"ES-FOOTPRINT-GATEWAY-DESIGN.md\").read(); R={m.group(1):m.group(2) for m in re.finditer(r\""'^'"\\\\| (G-R\\\\d+[a-z]?) \\\\| (.*?) \\\\|"'$",D,re.M)}; [(print(("OK " if v.get("documentText") and v["documentText"] in R.get(v.get("requirement"),"") else "BAD"),k,"'"\\\\n clause=\",v.get(\"clause\"),\"\\\\n doc=\",v.get(\"documentText\"))) for k,v in json.load(open(\"ES-FOOTPRINT-CAMPAIGN.json\")).items()]'" in /private/tmp/oe-conf-gw
 succeeded in 52ms:
BAD G-R4 older-date-dropped \n clause= an OLDER session date is dropped \n doc= **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.
BAD G-R4 newer-date-rolls-both \n clause= a NEWER date rolls BOTH views \n doc= Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
BAD G-R4 equal-date-upserts \n clause= an EQUAL date upserts without rolling \n doc= **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.
BAD G-R4 rollover-counted \n clause= a rollover is counted \n doc= Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
BAD G-R8 evict-oldest \n clause= evict the OLDEST key of the timeframe \n doc= View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8 evict-rotates \n clause= eviction ROTATES across timeframes \n doc= View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8 byte-budget-decremented \n clause= evicting decrements the byte total \n doc= View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8 both-budgets-enforced \n clause= both the count AND byte budgets are enforced \n doc= View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8 evictions-counted \n clause= evictions are counted per view \n doc= View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8a ceiling-strict \n clause= the ceiling is inclusive; only ABOVE is refused \n doc= **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
BAD G-R8a codec-allowlist \n clause= only producer/uncompressed are accepted \n doc= **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
BAD G-R9 ceiling-precedence \n clause= ceiling takes precedence over compression \n doc= `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).
BAD G-R7 to-is-inclusive \n clause= toMs is INCLUSIVE \n doc= **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R7 limit-clamped-low \n clause= the forwarded limit is clamped to at least 1 \n doc= **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R7 limit-clamped-high \n clause= the forwarded limit is clamped to the ceiling \n doc= `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.
BAD G-R7 epoch-guard \n clause= an out-of-domain timestamp is refused \n doc= **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R7 epoch-guard-removed \n clause= a cursor at or past the last epoch value can have nothing after it, and the increment is taken only inside the domain \n doc= **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R7 epoch-guard-relaxed \n clause= the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way \n doc= **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R1.1 the-flag-gates-every-footprint-object \n clause= Flag off: the views are never instantiated \n doc= Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.
BAD G-R2.1 one-wiring-method-binds-all-four \n clause= ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers \n doc= ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.
BAD G-R2.2 the-evidence-binding \n clause= KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence) \n doc= **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).
BAD G-R3.1 an-oversize-record-is-dropped-not-broadcast \n clause= a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped \n doc= `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
BAD G-R3.2 a-rejected-record-is-still-broadcast \n clause= a record the view REJECTS for shape or session is still broadcast \n doc= `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
BAD G-R3.3 live-snapshots-are-never-retained \n clause= never cached, never retained \n doc= **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).
BAD G-R5.1 the-outcome-key-orders-numerically \n clause= the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity) \n doc= The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;
BAD G-R5.2 the-identity-is-the-last-component \n clause= identity CONTAINS |, which is why it is the LAST component \n doc= `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).
BAD G-R5.3 a-foreign-identity-is-refused \n clause= a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet) \n doc= Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
BAD G-R5.4 last-write-per-key-wins \n clause= Last write per full key wins; a redelivery carries the same key and overwrites in place \n doc= Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
BAD G-R6.1 the-hello-carries-the-footprint-field \n clause= the existing cvd-hello frame gains ONE field footprint \n doc= **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
BAD G-R6.2 the-hello-is-sent-when-footprint-alone-is-on \n clause= cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled \n doc= `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.
BAD G-R6.3 the-hello-carries-the-outcome-high-water-mark \n clause= outcomeHwm:{tf: maxResolvedAtBarStartMs} \n doc= **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
BAD G-R10.1 a-footprint-record-never-escapes-the-consumer-loop \n clause= a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw \n doc= A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
BAD G-R10.2 every-admission-failure-is-a-counted-drop \n clause= every admission failure is a counted drop \n doc= A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
BAD G-R9.2 every-record-is-counted-before-admission \n clause= records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too \n doc= `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).

exec
/bin/bash -lc "python3 -c 'import json; lines=open(\"ES-FOOTPRINT-GATEWAY-DESIGN.md\").read().splitlines(); R={x.split(\" | \",2)[0][2:]:x.split(\" | \",2)[1] for x in lines if x.startswith(\"| G-R\")}; [(print(\"BAD\",k,\"::\",v.get(\"documentText\"))) for k,v in json.load(open(\"ES-FOOTPRINT-CAMPAIGN.json\")).items() if not v.get(\"documentText\") or v[\"documentText\"] not in R.get(v.get(\"requirement\"),\"\")]'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
BAD G-R4 older-date-dropped :: **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.
BAD G-R4 newer-date-rolls-both :: Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
BAD G-R4 equal-date-upserts :: **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.
BAD G-R4 rollover-counted :: Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
BAD G-R8 evict-oldest :: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8 evict-rotates :: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8 byte-budget-decremented :: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8 both-budgets-enforced :: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8 evictions-counted :: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
BAD G-R8a ceiling-strict :: **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
BAD G-R8a codec-allowlist :: **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
BAD G-R9 ceiling-precedence :: `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).
BAD G-R7 to-is-inclusive :: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R7 limit-clamped-low :: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R7 limit-clamped-high :: `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.
BAD G-R7 epoch-guard :: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R7 epoch-guard-removed :: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R7 epoch-guard-relaxed :: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
BAD G-R1.1 the-flag-gates-every-footprint-object :: Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.
BAD G-R2.1 one-wiring-method-binds-all-four :: ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.
BAD G-R2.2 the-evidence-binding :: **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).
BAD G-R3.1 an-oversize-record-is-dropped-not-broadcast :: `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
BAD G-R3.2 a-rejected-record-is-still-broadcast :: `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
BAD G-R3.3 live-snapshots-are-never-retained :: **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).
BAD G-R5.1 the-outcome-key-orders-numerically :: The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;
BAD G-R5.2 the-identity-is-the-last-component :: `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).
BAD G-R5.3 a-foreign-identity-is-refused :: Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
BAD G-R5.4 last-write-per-key-wins :: Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
BAD G-R6.1 the-hello-carries-the-footprint-field :: **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
BAD G-R6.2 the-hello-is-sent-when-footprint-alone-is-on :: `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.
BAD G-R6.3 the-hello-carries-the-outcome-high-water-mark :: **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
BAD G-R10.1 a-footprint-record-never-escapes-the-consumer-loop :: A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
BAD G-R10.2 every-admission-failure-is-a-counted-drop :: A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
BAD G-R9.2 every-record-is-counted-before-admission :: `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).

exec
/bin/bash -lc "python3 -c 'import json; D=open(\"ES-FOOTPRINT-GATEWAY-DESIGN.md\").read().split(\"<"'!-- BEGIN footprint-reqstate",1)[0]; lines=D.splitlines(); R={x.split(" | ",2)[0][2:]:x.split(" | ",2)[1] for x in lines if x.startswith("| G-R")}; [(print("BAD",k,"::",v.get("documentText"))) for k,v in json.load(open("ES-FOOTPRINT-CAMPAIGN.json")).items() if not v.get("documentText") or v["documentText"] not in R.get(v.get("requirement"),"")]'"'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
2026-09-08T21:03:11.315698Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "rg -n \"'G-R|\\\"G-R|G-R[0-9].*clause|epoch-guard|rollover-counted\" scripts/footprint-mutate.py; sed -n '1,360p' scripts/footprint-mutate.py" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
#!/usr/bin/env python3
"""Mutation campaign harness — repo-agnostic, and it records what it did.

A mutation result is only worth as much as its provenance, so every entry carries the exact patch,
which occurrence of it was changed and at what line and in what enclosing declaration, the command
that was run, and how the run ended. Three outcomes are distinguished, because conflating them is
how a campaign starts lying:

  KILLED         a TEST failed. The clause is pinned.
  SURVIVED       the suite passed. Nothing caught the regression.
  BUILD-FAILED   the mutant did not compile, or the run died before testing. Says nothing about
                 pinning; the mutation must be rewritten.

A clean baseline is proven before any mutation is applied — a campaign against an already-red suite
would report every clause as pinned.
"""
import json, subprocess, sys, os, shutil, signal, re, time, hashlib

def failure_lines(out, kind):
    """The VERBATIM lines the killedBy names were parsed from, so the record substantiates itself.
    A tail of the output does not: Maven prints its failures well before the build summary, so the
    last N characters of a run contain none of the names the harness reported."""
    pat = r'^\[ERROR\]\s+[\w.$]+\.[\w$]+(?::\d+|\s+--|\s).*$' if kind == 'maven' else r'^not ok \d+ - .*$'
    return [l.rstrip()[:400] for l in re.findall(pat, out, re.M)][:12]

def assertion_failures(out, kind):
    """Failures that are an ASSERTION, not an infrastructure error.

    A mutation that makes the test class fail to construct, a format string blow up, or a fixture
    throw, produces a non-zero exit and names the right test — and is NOT evidence that the clause
    is held. One recorded kill in this campaign was exactly that: adding a `%s` to a format template
    failed with MissingFormatArgument under the very test whose assertion was supposed to catch the
    shared module. Surefire distinguishes `<<< FAILURE!` (assertion) from `<<< ERROR!` (threw);
    node's TAP reports `code: 'ERR_ASSERTION'` for an assert and something else for a throw.
    """
    if kind == 'maven':
        # the summary lines under "[ERROR] Failures:" are assertions; those under "[ERROR] Errors:"
        # are throws. Walk the trailing summary and attribute each name to the section it is under.
        names, section = set(), None
        for line in out.split('\n'):
            if re.match(r'^\[ERROR\]\s+Failures:\s*$', line): section = 'assert'; continue
            if re.match(r'^\[ERROR\]\s+Errors:\s*$', line): section = 'error'; continue
            if re.match(r'^\[ERROR\]\s+Tests run:', line): section = None; continue
            m = re.match(r'^\[ERROR\]\s{2,}([\w.$]+\.[\w$]+)[:\s]', line)
            if m and section == 'assert':
                names.add('.'.join(m.group(1).split('.')[-2:]))
        return sorted(names)
    # node --test: a failing subtest block carries `code: 'ERR_ASSERTION'` for an assertion
    names = set()
    for block in re.split(r'^not ok \d+ - ', out, flags=re.M)[1:]:
        head, rest = block.split('\n', 1) if '\n' in block else (block, '')
        body = rest.split('\nnot ok ')[0].split('\nok ')[0]
        if "ERR_ASSERTION" in body or "AssertionError" in body:
            names.add(head.strip())
    return sorted(names)

def failing_tests(out, kind):
    if kind == 'maven':
        # Surefire's failure lines take two shapes:
        #   [ERROR]   ClassName.method:123 message
        #   [ERROR] pkg.ClassName.method -- Time elapsed ... <<< FAILURE!
        # A naive (\w+\.\w+) truncates the second to its package, which then gets recorded as the
        # failing test. Take the last two dotted segments before the colon or the double dash.
        names = set()
        for m in re.finditer(r'^\[ERROR\]\s+([\w.$]+\.[\w$]+)(?::\d+|\s+--|\s)', out, re.M):
            parts = m.group(1).split('.')
            if len(parts) >= 2 and parts[-1][:1].islower() or len(parts) >= 2:
                names.add('.'.join(parts[-2:]))
        return sorted(names)
    return sorted({m.group(1).strip() for m in re.finditer(r'^not ok \d+ - (.+)$', out, re.M)})

def build_failed(out, kind):
    if kind == 'maven':
        return 'COMPILATION ERROR' in out or 'BUILD FAILURE' in out and 'Tests run:' not in out
    return 'SyntaxError' in out

def run(cmd, cwd):
    p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=3600)
    return p.returncode, p.stdout + p.stderr

def sha(text):
    return hashlib.sha256(text.encode('utf-8', 'replace')).hexdigest()

def head_commit(root):
    rc, out = run(['git', 'rev-parse', 'HEAD'], root)
    return out.strip() if rc == 0 else None

def tree_dirty(root):
    rc, out = run(['git', 'status', '--porcelain'], root)
    return [l for l in out.split('\n') if l.strip()]

# A campaign that is killed between the write and the restore leaves the tree mutated AND leaves
# compiled classes built from the mutation. Restoring the source is not enough: `mv` puts back the
# ORIGINAL mtime, so an incremental build keeps testing the mutant. Handle the signal, restore, and
# discard the compiled output so the next run cannot inherit a mutant.
_ACTIVE = {'paths': [], 'root': None}

def _restore_and_exit(signum, frame):
    for path in _ACTIVE['paths']:
        if os.path.exists(path + '.bak'):
            shutil.move(path + '.bak', path)
            os.utime(path, None)
    root = _ACTIVE['root']
    if root:
        for d, _, _ in list(os.walk(root)):
            if os.path.basename(d) == 'classes' and os.path.basename(os.path.dirname(d)) == 'target':
                shutil.rmtree(d, ignore_errors=True)
    print(f"\ninterrupted by signal {signum}: tree restored and compiled classes discarded")
    sys.exit(130)

def main():
    spec = json.load(open(sys.argv[1]))
    outp = sys.argv[2]
    root, cmd, kind = spec['root'], spec['command'], spec['kind']
    _ACTIVE['root'] = root
    signal.signal(signal.SIGTERM, _restore_and_exit)
    signal.signal(signal.SIGINT, _restore_and_exit)
    res = json.load(open(outp)) if os.path.exists(outp) else {}

    dirty = tree_dirty(root)
    if dirty:
        print("TREE IS DIRTY — a campaign must run against a committed tree, or its record names a state"); 
        print("nobody can return to:"); [print("  ", d) for d in dirty[:10]]; sys.exit(3)
    commit = head_commit(root)
    rc, out = run(cmd, root)
    if rc != 0:
        print("BASELINE IS RED — refusing to run a campaign against a failing suite"); print(out[-3000:]); sys.exit(2)
    baseline = {'commit': commit, 'command': ' '.join(cmd), 'returnCode': rc,
                'outputSha256': sha(out), 'outputTail': out[-1500:]}
    base_tests = re.search(r'Tests run: (\d+)', out) or re.search(r'# pass (\d+)', out)
    print(f"baseline GREEN ({base_tests.group(1) if base_tests else '?'} tests) — {' '.join(cmd)}\n")

    for m in spec['mutations']:
        k = m['key']
        # Resume only when the stored record was produced by THIS patch at THIS commit. Keying the
        # skip on the name alone silently reuses a record for a patch that has since been edited,
        # and the campaign then reports a result no run ever produced.
        prev = res.get(k)
        # A clause may be implemented at SEVERAL sites (a guard duplicated for defence in depth). One
        # clause is still ONE mutation: `sites` breaks every site of the clause together, because
        # breaking one site of a duplicated guard changes no behaviour and would be recorded as a
        # survivor of a test that is in fact perfectly capable of catching the clause's removal.
        sites = m.get('sites') or [{'file': m['file'], 'old': m['old'], 'new': m['new'],
                                    'occurrence': m.get('occurrence', 1)}]
        patch = {'sites': sites} if m.get('sites') else {'old': m['old'], 'new': m['new']}
        if prev is not None and prev.get('patch') == patch \
                and prev.get('evidence', {}).get('repoCommit') == commit:
            continue
        res.pop(k, None)
        f = os.path.join(root, sites[0]['file'])
        src = open(f).read()
        missing = [s0 for s0 in sites if open(os.path.join(root, s0['file'])).read().count(s0['old']) == 0]
        if missing:
            res[k] = {'status': 'ANCHOR-MISSING', 'file': missing[0]['file']}
            print(f"  {k:<40} ANCHOR-MISSING"); json.dump(res, open(outp,'w'), indent=1); continue
        m = dict(m, old=sites[0]['old'], new=sites[0]['new'])
        n = src.count(sites[0]['old'])
        occ = sites[0].get('occurrence', 1)
        # locate the chosen occurrence
        pos, seen = -1, 0
        start = 0
        while True:
            i = src.find(m['old'], start)
            if i < 0: break
            seen += 1
            if seen == occ: pos = i; break
            start = i + 1
        line = src[:pos].count('\n') + 1
        lines = src.split('\n')
        # The enclosing DECLARATION, found by scanning FORWARD from the top of the file and tracking
        # brace depth, so the answer is the declaration whose body actually contains the mutated line.
        # A backwards "nearest line that looks like a signature" scan is what reported appendHwm()
        # for a mutation inside barsPage(); a forensic record that misplaces the mutation is worse
        # than none.
        encl, depth, stack = None, 0, []
        for idx, t in enumerate(lines, start=1):
            opens = t.count('{')
            stripped = t.strip()
            is_decl = ('(' in stripped and not re.match(r'^(if|for|while|switch|catch|try|else|do|synchronized|return)\b', stripped)) \
                      or re.match(r'^\s*(public|private|protected|static|final|abstract)?\s*(class|interface|enum|record)\b', t)
            if opens and is_decl:
                stack.append((depth, ' '.join(t.split())[:140]))
            depth += opens - t.count('}')
            if idx == line:
                # Capture BEFORE popping. A one-line method opens and closes on the same line, so a
                # pop-first order removes it from the stack before the mutated line can name it —
                # which is how `static int clamp(...)` was recorded as `public class GatewayController`.
                encl = stack[-1][1] if stack else None
                break
            while stack and depth <= stack[-1][0]:
                stack.pop()

        touched = sorted({os.path.join(root, s0['file']) for s0 in sites})
        _ACTIVE['paths'] = touched
        originals, mutants = {}, {}
        for path in touched:
            shutil.copy(path, path + '.bak')
        t0 = time.time()
        try:
            for s0 in sites:
                path = os.path.join(root, s0['file'])
                text = open(path).read()
                originals.setdefault(s0['file'], sha(text))
                at, seen2, start2 = -1, 0, 0
                while True:
                    j = text.find(s0['old'], start2)
                    if j < 0: break
                    seen2 += 1
                    if seen2 == s0.get('occurrence', 1): at = j; break
                    start2 = j + 1
                open(path, 'w').write(text[:at] + s0['new'] + text[at+len(s0['old']):])
            for s0 in sites:
                path = os.path.join(root, s0['file'])
                mutants[s0['file']] = sha(open(path).read())
            rc, out = run(cmd, root)
        finally:
            # UNCONDITIONAL. A timeout, an interrupt or a crash between the write and the restore
            # would otherwise leave the tree mutated and poison every later mutation AND the baseline.
            for path in touched:
                shutil.move(path + '.bak', path)
                os.utime(path, None)   # mv restores the ORIGINAL mtime; Maven would skip recompiling
            _ACTIVE['paths'] = []
        still_dirty = tree_dirty(root)
        failed = failing_tests(out, kind)
        asserted = assertion_failures(out, kind)
        # A declared throw-kill must name BOTH the test that should propagate it and a signature of
        # the throw itself, and both must appear in the run's own failure lines. `killedByThrow:
        # true` alone would accept any error under any failing test.
        want = m.get('killedByThrow') or {}
        throw_kill_ok = False
        if isinstance(want, dict) and want.get('test') and want.get('throws'):
            lines = '\n'.join(failure_lines(out, kind))
            throw_kill_ok = (want['test'] in failed or any(want['test'] in f2 for f2 in failed)) \
                and want['throws'] in lines
        # A `kind` that does not match the runner parses no failure names, and every kill is then
        # recorded as BUILD-FAILED — silently, because the run really did exit non-zero. Refuse
        # instead: the output plainly names failing tests in the other runner's shape.
        if rc != 0 and not failed:
            other = failing_tests(out, 'node' if kind == 'maven' else 'maven')
            if other:
                print(f"KIND MISMATCH: kind={kind!r} parsed no failures, but the output names {other[:3]}")
                print("  the spec's `kind` does not match the test runner; refusing to record a wrong verdict")
                sys.exit(4)
        if build_failed(out, kind) and not failed:
            status = 'BUILD-FAILED'
        elif rc != 0 and asserted:
            status = 'KILLED'
        elif rc != 0 and failed and throw_kill_ok:
            # The clause under test IS "this must not throw", so a test that propagates the throw is
            # detecting exactly the right thing. The spec must SAY so in advance AND name the throw
            # it expects — a bare boolean would accept any incidental error under any failing test,
            # which is the laundering this whole distinction exists to prevent.
            status = 'KILLED'
        elif rc != 0 and failed:
            # the right test failed, but on a THROW rather than an assertion, and the spec did not
            # declare that mode: the mutation may have broken the fixture rather than the behaviour
            status = 'KILLED-BY-ERROR'
        elif rc != 0:
            status = 'BUILD-FAILED'
        else:
            status = 'SURVIVED'
        res[k] = {'status': status, 'clause': m.get('clause',''), 'requirement': m.get('requirement', k.split()[0]),
                  'documentText': m.get('documentText'),
                  'file': m['file'], 'occurrence': occ, 'occurrencesInFile': n, 'line': line,
                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': asserted[:4] or failed[:4], 'assertionFailures': asserted[:4], 'anyFailures': failed[:4],
                  'killedByThrow': (m.get('killedByThrow') if throw_kill_ok else None),
                  'seconds': round(time.time()-t0, 1),
                  'patch': patch,
                  'evidence': {'repoCommit': commit, 'returnCode': rc,
                               'mutatedFileSha256': mutants.get(sites[0]['file']), 'originalFileSha256': originals.get(sites[0]['file']),
                               'filesSha256': {p: {'original': originals[p], 'mutated': mutants.get(p)} for p in originals},
                               'outputSha256': sha(out), 'outputTail': out[-1200:],
                               'failureLines': failure_lines(out, kind),
                               'treeRestoredClean': not still_dirty},
                  'baseline': baseline}
        print(f"  {k:<40} {status:<13} {(failed[0][:52] if failed else '')}")
        json.dump(res, open(outp,'w'), indent=1)
    from collections import Counter
    print("\n", Counter(v['status'] for v in res.values()))

main()

2026-09-08T21:03:18.133425Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "rg --files | rg 'footprint.*(json|md|py|sh)"'$'"'; jq -r 'keys[]' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
scripts/footprint-reqstate.sh
scripts/footprint-mutate.py
scripts/footprint-conformance.py
scripts/footprint-reqstate.py
G-R1.1 the-flag-gates-every-footprint-object
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop
G-R10.2 every-admission-failure-is-a-counted-drop
G-R2.1 one-wiring-method-binds-all-four
G-R2.2 the-evidence-binding
G-R3.1 an-oversize-record-is-dropped-not-broadcast
G-R3.2 a-rejected-record-is-still-broadcast
G-R3.3 live-snapshots-are-never-retained
G-R4 equal-date-upserts
G-R4 newer-date-rolls-both
G-R4 older-date-dropped
G-R4 rollover-counted
G-R5.1 the-outcome-key-orders-numerically
G-R5.2 the-identity-is-the-last-component
G-R5.3 a-foreign-identity-is-refused
G-R5.4 last-write-per-key-wins
G-R6.1 the-hello-carries-the-footprint-field
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on
G-R6.3 the-hello-carries-the-outcome-high-water-mark
G-R7 epoch-guard
G-R7 epoch-guard-relaxed
G-R7 epoch-guard-removed
G-R7 limit-clamped-high
G-R7 limit-clamped-low
G-R7 to-is-inclusive
G-R8 both-budgets-enforced
G-R8 byte-budget-decremented
G-R8 evict-oldest
G-R8 evict-rotates
G-R8 evictions-counted
G-R8a ceiling-strict
G-R8a codec-allowlist
G-R9 ceiling-precedence
G-R9.2 every-record-is-counted-before-admission

2026-09-08T21:03:21.706323Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "sed -n '1,360p' scripts/footprint-conformance.py" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
#!/usr/bin/env python3
"""Render the conformance matrix from the CHECKED-IN campaign records and the CHECKED-IN documents.

Reproducibility is the point: this reads only files that live in the three repositories, at paths
given on the command line, and nothing from a scratch directory. Two self-checks refuse to emit:

  * an obligation that is not a verbatim substring of the requirement it is attached to — the failure
    that transposed fourteen requirements in the first version of this document;
  * a survivor with no characterisation — so the summary can never claim "nothing is unpinned" while
    an uncharacterised gap sits in the table.
"""
import json, re, sys, collections, os

def load_requirements(path, pattern):
    rows = {}
    for i, l in enumerate(open(path).read().split('\n'), 1):
        m = re.match(r'^\|\s*(' + pattern + r')\s*\|\s*(.*)$', l)
        if not m: continue
        t = m.group(2).rstrip()
        if t.endswith('|'): t = t[:-1].rstrip()
        if m.group(1) in rows and len(t) <= len(rows[m.group(1)]['text']): continue
        lead = re.match(r'\*\*(.+?)\*\*', t)
        rows[m.group(1)] = {'line': i, 'text': t, 'lead': (lead.group(1).rstrip('.') if lead else '')}
    return rows

# Why each surviving mutation survives. A survivor with no entry here stops the render.
WHY = {
 'F-R5.1 no-half-apply':
   ("UNOBSERVABLE. Any failure after the premature insert calls latch(), which CLEARS the level map — "
    "so the half-applied state it would leave cannot be seen from outside. The clause is not "
    "falsifiable as written, and no test claims to pin it."),
 'F-E1 reason-precedence-overflow':
   ("INERT MUTATION. FootprintBar.quality() returns a single enum value with OVERFLOW selected before "
    "the volume-based QUIET classification, so the two are mutually exclusive and swapping those arms "
    "changes no output. The precedence is real only between the LATER arms, which are killed."),
 'F-R9 line50: delta ':
   ("ALGEBRAICALLY IMPLIED. Every route to violating it trips an earlier invariant first for any "
    "reachable CVD bar. The other 27 checks in Invariants are each killed by a named test."),
 'G-R7 epoch-guard-relaxed':
   ("INERT MUTATION, kept as the record of that fact. At afterMs == EPOCH_MAX_MS the +1 is safe and hi "
    "is capped, so lo > hi and the page is empty either way. The clause's load-bearing half is "
    "epoch-guard-removed, which IS killed."),
}

def main():
    specs = json.loads(sys.argv[1])   # [{repo,label,doc,docPath,idPattern,recordPath}]
    out, rows, problems = [], [], []
    reqs = {}
    for s in specs:
        reqs[s['repo']] = load_requirements(s['docPath'], s['idPattern'])
        rec = json.load(open(s['recordPath']))
        for key, v in rec.items():
            rid = v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', key).group(1)
            text = v.get('documentText')
            if rid not in reqs[s['repo']]:
                problems.append(f"{key}: {rid} is not a requirement in {s['doc']}"); continue
            if not text or text.strip() not in reqs[s['repo']][rid]['text']:
                problems.append(f"{key}: obligation is not verbatim in {rid}"); continue
            if v['status'] == 'SURVIVED' and key not in WHY:
                problems.append(f"{key}: SURVIVED with no characterisation"); continue
            rows.append(dict(repo=s['repo'], key=key, rid=rid, text=text, **v))
    if problems:
        print("REFUSING TO EMIT:"); [print("  ", p) for p in problems]; sys.exit(1)

    tot = collections.Counter(r['status'] for r in rows)
    out.append("# ES Footprint — conformance by mutation\n")
    out.append("Every requirement of the three ES Footprint design documents, and for each clause this campaign\n"
               "probed, the evidence that its behaviour is held by a test.\n")
    out.append("## What a verdict means\n")
    out.append("| | |\n|---|---|")
    out.append("| **KILLED** | the clause was broken and a NAMED test failed |")
    out.append("| **SURVIVED** | the clause was broken and the suite passed |")
    out.append("| **NOT PROBED** | this campaign did not test it. Nothing is claimed either way |")
    out.append("\nThe harness refuses to start against a dirty tree or a red baseline — a campaign against a red\n"
               "suite reports everything as pinned, which is what happened to an earlier run of this one. Every\n"
               "entry in the records carries the repository commit, the patch and the occurrence, line and\n"
               "declaration it was applied at, the command, the process exit code, a SHA-256 of the run output\n"
               "with its tail, and confirmation that the tree was restored clean.\n")
    out.append(f"**{len(rows)} mutations: {tot['KILLED']} killed, {tot.get('SURVIVED',0)} surviving.** "
               "Obligation text is quoted from the design document; where a requirement is longer than the cell, "
               "the quotation is a PREFIX of it and the document line is given so the rest can be read.\n")
    for s in specs:
        sub = [r for r in rows if r['repo'] == s['repo']]
        ids = sorted(reqs[s['repo']], key=lambda x: (re.sub(r'\d', '', x), int(re.search(r'\d+', x).group()), x))
        probed = {r['rid'] for r in sub}
        out.append(f"\n## {s['label']} — `{s['doc']}`\n")
        out.append(f"{len(ids)} requirements; {len(probed)} probed by {len(sub)} mutations. "
                   f"Record: `{os.path.basename(s['recordPath'])}`.\n")
        out.append("| Req | Obligation — quoted from the document | Clause broken | Verdict | Killed by |")
        out.append("|---|---|---|---|---|")
        for rid in ids:
            mine = sorted([r for r in sub if r['rid'] == rid], key=lambda x: x['key'])
            if not mine:
                out.append(f"| **{rid}**<br><sub>{s['doc']}:{reqs[s['repo']][rid]['line']}</sub> | "
                           f"*{reqs[s['repo']][rid]['lead']}* | — | NOT PROBED | — |")
                continue
            for i, r in enumerate(mine):
                head = f"**{rid}**<br><sub>{s['doc']}:{reqs[s['repo']][rid]['line']}</sub>" if i == 0 else ""
                q = r['text'].replace('|', '\\|')
                q = q if len(q) <= 260 else q[:260].rsplit(' ', 1)[0] + ' …'
                tag = "**KILLED**" if r['status'] == 'KILLED' else "⚠️ **SURVIVED**"
                by = (r.get('killedBy') or ['—'])[0].replace('|', '\\|')
                out.append(f"| {head} | {q} | `{r['key'].split(' ', 1)[-1]}` <br><sub>{r['file'].split('/')[-1]}:{r['line']}</sub> | {tag} | `{by}` |")
    surv = [r for r in rows if r['status'] == 'SURVIVED']
    out.append("\n## What nothing holds, and why\n")
    out.append("A surviving mutation is not automatically a gap. Each is named for what it is.\n")
    for r in surv:
        out.append(f"**{r['rid']} — `{r['key'].split(' ', 1)[-1]}`** ({r['file'].split('/')[-1]}:{r['line']})\n")
        out.append(f"> {r.get('clause','')}\n")
        out.append(WHY[r['key']] + "\n")
    out.append("Every survivor above is unobservable, algebraically implied, or an inert mutation whose\n"
               "load-bearing counterpart is killed. **That says nothing about the requirements this campaign did\n"
               "not probe** — those are marked NOT PROBED and no claim is made about them.\n")
    print("\n".join(out))

main()

2026-09-08T21:03:24.470815Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc '/usr/bin/git diff --name-status origin/main...HEAD 2>/dev/null; scripts/footprint-reqstate.sh --check' in /private/tmp/oe-conf-gw
 exited 1 in 2601ms:
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
mktemp: mkdtemp failed on /var/folders/t3/_t_9ln7d49g_yvjqnyr_rdj40000gn/T/tmp.35ajBvLWGT: Operation not permitted

2026-09-08T21:03:30.257118Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc '/usr/bin/git show --stat --oneline 0ad2f33 2>/dev/null; /usr/bin/git show --no-ext-diff --unified=40 0ad2f33 -- ES-FOOTPRINT-CAMPAIGN.json ES-FOOTPRINT-GATEWAY-DESIGN.md scripts/footprint-reqstate.py scripts/footprint-mutate.py 2>/dev/null; /usr/bin/git show --no-ext-diff --unified=40 ae8a687 -- ES-FOOTPRINT-CAMPAIGN.json scripts/footprint-reqstate.py scripts/footprint-mutate.py 2>/dev/null' in /private/tmp/oe-conf-gw
 succeeded in 7620ms:
0ad2f33 fix(footprint conformance): put the ordering clause under the requirement that states it
 ES-FOOTPRINT-CAMPAIGN.json     | 490 ++++++++++++++++++++---------------------
 ES-FOOTPRINT-GATEWAY-DESIGN.md |   4 +-
 2 files changed, 247 insertions(+), 247 deletions(-)
commit 0ad2f333e6cf1520270d0f92efa4285311bd96aa
Author: Abhinav Jain <abhinav.jain09@gmail.com>
Date:   Tue Sep 8 23:01:00 2026 +0200

    fix(footprint conformance): put the ordering clause under the requirement that states it
    
    Merge gate round 3, one HIGH and two MEDIUM, all correct:
    
    1. HIGH — "records_total is incremented BEFORE admission" is G-R9's
       clause, not G-R10's. The probe was recorded under G-R10, so the table
       read G-R9 1/1 and G-R10 3/3 when the truth is 2 and 2. Reassigned, and
       its obligation quote now comes from G-R9's own text.
    2. MEDIUM — the G-R3 broadcast probe's recorded clause said "shape,
       session or size", but G-R3 explicitly EXCLUDES oversize from what is
       still broadcast, and the run's failure lines only exercise the stale
       session. Narrowed to shape or session.
    3. MEDIUM — the generator treated any truthy killedByThrow as sufficient.
       The harness validated {test, throws} against the run, but a record is a
       checked-in FILE: a hand-edited or legacy one would have been taken at
       its word. The generator now holds the invariant itself, requiring both
       strings to appear in that record's own failure lines.
    
    Also folded from the page repo's round 3: the summary counted every
    record rather than only those the table renders, and the marker rule in
    the generator (first substring anywhere) disagreed with the rewriter's
    (line start), so a prose mention of the marker could truncate requirement
    discovery. Both now use the line-start rule.
    
    Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>

diff --git a/ES-FOOTPRINT-CAMPAIGN.json b/ES-FOOTPRINT-CAMPAIGN.json
index b710df9..34d5a50 100644
--- a/ES-FOOTPRINT-CAMPAIGN.json
+++ b/ES-FOOTPRINT-CAMPAIGN.json
@@ -1,1942 +1,1942 @@
 {
  "G-R4 older-date-dropped": {
   "status": "KILLED",
   "clause": "an OLDER session date is dropped",
   "requirement": "G-R4",
   "documentText": "**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 146,
   "enclosing": "private Boolean rollover(LocalDate date) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
    "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
    "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "assertionFailures": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
    "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
    "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "anyFailures": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
    "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
    "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 8.2,
+  "seconds": 9.3,
   "patch": {
    "old": "        if (cmp < 0) return null;",
    "new": "        if (cmp < 0) return false;"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc"
     }
    },
-   "outputSha256": "6d97f0b3ecf4776c2bcc784d17a7277acd1b3f94992d2f3af5c79c5abe6fa7f1",
+   "outputSha256": "08ff87ecfb0f487eb30d6d108ccf0061123fb6f6ad81c7d4f09ea2e266816229",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.140 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.260 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
     "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED>",
     "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED>",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R4 newer-date-rolls-both": {
   "status": "KILLED",
   "clause": "a NEWER date rolls BOTH views",
   "requirement": "G-R4",
   "documentText": "Rollover is MONOTONIC on the parsed date: NEWER than current \u21d2 BOTH views are cleared and the date advances;",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 148,
   "enclosing": "private Boolean rollover(LocalDate date) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "assertionFailures": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "anyFailures": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "killedByThrow": null,
-  "seconds": 7.9,
+  "seconds": 9.9,
   "patch": {
    "old": "        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;",
    "new": "        bars.clear(); barsBytes = 0;"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6"
     }
    },
-   "outputSha256": "82a81e9d36058cd5a5b60073fc258691db3065677472c2a227634f0e4a9d2fae",
+   "outputSha256": "bcacad7451d47daefc4b0d22a4ab6d2c9416b6519b0c272d01fdd6819134c218",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.009 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R4 equal-date-upserts": {
   "status": "KILLED",
   "clause": "an EQUAL date upserts without rolling",
   "requirement": "G-R4",
   "documentText": "**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 147,
   "enclosing": "private Boolean rollover(LocalDate date) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "assertionFailures": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "anyFailures": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "killedByThrow": null,
-  "seconds": 7.6,
+  "seconds": 8.4,
   "patch": {
    "old": "        if (cmp == 0) return false;",
    "new": "        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca"
     }
    },
-   "outputSha256": "88b550563c84ac1484a6a761d23eeab9fe75948c5deeae0a46b173449d93ddd5",
+   "outputSha256": "976964853c4afc7edcc4a250f133ed1049d154b02dd71ccce82d0592c717ba26",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.008 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.012 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {\"sessionDate\":\"2026-08-14\",\"bars\":[{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"30s\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":5000},\"tag\":\"x\"}],\"nextCursor\":null} ==> expected: <true> but was: <false>",
     "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0>",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0>",
     "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>",
     "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{},\"outcomeHwm\":{\"5m\":70}}>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R4 rollover-counted": {
   "status": "KILLED",
   "clause": "a rollover is counted",
   "requirement": "G-R4",
   "documentText": "Rollover is MONOTONIC on the parsed date: NEWER than current \u21d2 BOTH views are cleared and the date advances;",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 150,
   "enclosing": "private Boolean rollover(LocalDate date) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "assertionFailures": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "anyFailures": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "killedByThrow": null,
-  "seconds": 8.1,
+  "seconds": 9.9,
   "patch": {
    "old": "        rollovers.incrementAndGet();",
    "new": "        if (false) rollovers.incrementAndGet();"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5"
     }
    },
-   "outputSha256": "68dff56f6652491b8991b96a3fb78497fc6ea00a6a72dc3d79cbf4f64cda20a7",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "3f7d49f6a8db6e15b2dd03a4f0ef59108140e60e091bedc34ed0434a62a5702a",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.006 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 evict-oldest": {
   "status": "KILLED",
   "clause": "evict the OLDEST key of the timeframe",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 167,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "assertionFailures": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "anyFailures": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "killedByThrow": null,
-  "seconds": 8.0,
+  "seconds": 9.1,
   "patch": {
    "old": "            String oldest = view.ceilingKey(tf + \"|\");",
    "new": "            String oldest = view.floorKey(tf + \"|~\");"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146"
     }
    },
-   "outputSha256": "ef2b53443135fea099a82f814cc9be9ab6a33162a373892d9c39023691a1df4c",
+   "outputSha256": "645fd51dd50b5fdef7a318db025a99eaf42202112942e9e959f59f7d74126815",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.006 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246>",
     "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 evict-rotates": {
   "status": "KILLED",
   "clause": "eviction ROTATES across timeframes",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 166,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "assertionFailures": [
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "anyFailures": [
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "killedByThrow": null,
-  "seconds": 7.5,
+  "seconds": 10.6,
   "patch": {
    "old": "            if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;",
    "new": "            if (isBars) barsEvictCursor = cursor; else outcomesEvictCursor = cursor;"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "1c700daa48bd560d42ed500056b7f54ddf4a324cadb6fdd5913d4690ec844c21",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "1c700daa48bd560d42ed500056b7f54ddf4a324cadb6fdd5913d4690ec844c21"
     }
    },
-   "outputSha256": "845e6dcab662066058181100ec09af42f201546680d90b271259cf3cd60ff650",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "c7e5f490aa39ddccbc0dbb6e69d9416c8f2c7ec3a5bbed11cf1b76f58d598e74",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.057 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:245 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 byte-budget-decremented": {
   "status": "KILLED",
   "clause": "evicting decrements the byte total",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 171,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
   "assertionFailures": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
   "anyFailures": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
   "killedByThrow": null,
-  "seconds": 7.5,
+  "seconds": 9.9,
   "patch": {
    "old": "            if (isBars) barsBytes -= len; else outcomesBytes -= len;",
    "new": "            if (isBars) barsBytes -= 0; else outcomesBytes -= 0;"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "604a8ec8ce620cc3e8d1e4a18e51bdc41af6639c24b90f01754b45a9619c0d3a",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "604a8ec8ce620cc3e8d1e4a18e51bdc41af6639c24b90f01754b45a9619c0d3a"
     }
    },
-   "outputSha256": "6950bccc0915c7249e143f8c2e12f4b98a4790528b342821feda30ec4853a099",
+   "outputSha256": "732222ed33cd5588f276b022fe80f486562b2dc6b230a56ea2af610fe426108d",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <3>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 both-budgets-enforced": {
   "status": "KILLED",
   "clause": "both the count AND byte budgets are enforced",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 161,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
   "assertionFailures": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
   "anyFailures": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
   "killedByThrow": null,
-  "seconds": 7.9,
+  "seconds": 9.6,
   "patch": {
    "old": "        while (view.size() > maxCount || (isBars ? barsBytes : outcomesBytes) > maxBytes) {",
    "new": "        while (view.size() > maxCount) {"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "be3b5ec8befa5b49f38a03ab67a789b7cfc023b4d7ce49ad8354fc3661fc4248",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "be3b5ec8befa5b49f38a03ab67a789b7cfc023b4d7ce49ad8354fc3661fc4248"
     }
    },
-   "outputSha256": "cff7c2fc7fe5e66db9c84bcd177458730f5bc72f0c4812d85213af8876772f3b",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "01b906f794bb1080c637d4233c5a6e7297c2d5ba863a71424fb2ea2e8a2f4529",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 evictions-counted": {
   "status": "KILLED",
   "clause": "evictions are counted per view",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 174,
   "enclosing": "private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "assertionFailures": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "anyFailures": [
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "killedByThrow": null,
-  "seconds": 7.9,
+  "seconds": 9.2,
   "patch": {
    "old": "        if (evicted > 0) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);",
    "new": "        if (false) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "a923db6234a63a8730dcd07cd80c8ecb899eb29df79d70d33101933d6634dcad",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "a923db6234a63a8730dcd07cd80c8ecb899eb29df79d70d33101933d6634dcad"
     }
    },
-   "outputSha256": "2e34cf8f98a8c12caf9e8e439d8245bd3cfac3b47b02bac09af99b35e3e4cf69",
+   "outputSha256": "5cd8a47c5aa0ddc106ecf98e95d2164fcaef5738fef16fb0b942f1d2a04eb6c5",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.006 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:260 expected: <1> but was: <0>",
     "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8a ceiling-strict": {
   "status": "KILLED",
   "clause": "the ceiling is inclusive; only ABOVE is refused",
   "requirement": "G-R8a",
   "documentText": "**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes \u2264 GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type \u2208 {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.",
   "file": "src/main/java/app/feedgateway/FootprintTopicGate.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 118,
   "enclosing": "private Outcome attempt(String topic) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts",
    "FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows",
    "FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne",
    "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"
   ],
   "assertionFailures": [
    "FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts",
    "FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows",
    "FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne",
    "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"
   ],
   "anyFailures": [
    "FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts",
    "FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows",
    "FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne",
    "FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears"
   ],
   "killedByThrow": null,
-  "seconds": 8.4,
+  "seconds": 8.8,
   "patch": {
    "old": "            else if (cfg.maxMessageBytes() > ceiling) o = new Outcome(false, Reason.ceiling,",
    "new": "            else if (cfg.maxMessageBytes() >= ceiling) o = new Outcome(false, Reason.ceiling,"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "10e91507bbd9c76a05f20ce70fd0d7e76f345dbeee0f0dfa8c79fd86e19317f1",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintTopicGate.java": {
      "original": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
      "mutated": "10e91507bbd9c76a05f20ce70fd0d7e76f345dbeee0f0dfa8c79fd86e19317f1"
     }
    },
-   "outputSha256": "0e55062ebe22e9262ba6e57ce5ace4f828b4f69279b51f0b74343b947d3f518f",
-   "outputTail": "ed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.evidence ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes ceiling max.message.bytes=1048588 > 1048588\n",
+   "outputSha256": "795b2a6ac35768e7b64183fd156915a608096e43b443553013783745a1b08b32",
+   "outputTail": "ed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.evidence ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes ceiling max.message.bytes=1048588 > 1048588\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.004 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.005 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.003 s <<< ERROR!",
     "[ERROR] app.feedgateway.FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0.002 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.007 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.003 s <<< FAILURE!",
     "[ERROR]   FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts:68 the validated topic is added; the unvalidated one is WITHHELD from added() ==> expected: <[futures.footprint.bars-0]> but was: <[]>",
     "[ERROR]   FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows:106 expected: <[futures.cvd.bars-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd.bars-0]>",
     "[ERROR]   FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne:99 expected: <true> but was: <false>",
     "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8a codec-allowlist": {
   "status": "KILLED",
   "clause": "only producer/uncompressed are accepted",
   "requirement": "G-R8a",
   "documentText": "**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes \u2264 GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type \u2208 {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.",
   "file": "src/main/java/app/feedgateway/FootprintTopicGate.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 119,
   "enclosing": "private Outcome attempt(String topic) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"
   ],
   "assertionFailures": [
    "FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"
   ],
   "anyFailures": [
    "FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"
   ],
   "killedByThrow": null,
-  "seconds": 8.0,
+  "seconds": 10.6,
   "patch": {
    "old": "            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression,",
    "new": "            else if (false) o = new Outcome(false, Reason.compression,"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "88113e222a9afc61792000b26899359fc51afc02b5fac10a12f82c0cf8b52ede",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintTopicGate.java": {
      "original": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
      "mutated": "88113e222a9afc61792000b26899359fc51afc02b5fac10a12f82c0cf8b52ede"
     }
    },
-   "outputSha256": "7302d4a29eba9cec0df5ae2373f1cc7918fde39d7fc9b4f39a8704083503fa32",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "409af9d682efd2d4396d404b406d13693ed1752d4c62f0af40d381505383df6c",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
     "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:51 gzip ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown."
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R9 ceiling-precedence": {
   "status": "KILLED",
   "clause": "ceiling takes precedence over compression",
   "requirement": "G-R9",
   "documentText": "`gateway_footprint_topic_validation_failures_total{topic,reason}` \u2014 `reason \u2208 {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic \u2014 timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin \u2192 unknown \u2192 ceiling \u2192 compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).",
   "file": "src/main/java/app/feedgateway/FootprintTopicGate.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 117,
   "enclosing": "private Outcome attempt(String topic) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"
   ],
   "assertionFailures": [
    "FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"
   ],
   "anyFailures": [
    "FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"
   ],
   "killedByThrow": null,
-  "seconds": 7.8,
+  "seconds": 9.3,
   "patch": {
    "old": "            if (cfg == null) o = new Outcome(false, Reason.admin, \"no config returned\");",
    "new": "            if (cfg == null) o = new Outcome(false, Reason.admin, \"no config returned\");\n            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression, \"compression.type=\" + cfg.compressionType());"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "0f5935f573879932d3c401a5d1ad1589021d2a10ec5c4c6c0f814e71d53ff4b8",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintTopicGate.java": {
      "original": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
      "mutated": "0f5935f573879932d3c401a5d1ad1589021d2a10ec5c4c6c0f814e71d53ff4b8"
     }
    },
-   "outputSha256": "4e4d8f1469429f6c8aac9d662915fa06711af04578cb16099934f2c19004b503",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "61ebec0c0408229a52b30f201dfa9abc0e2150a31cfd863d8fef2cadcd559cb2",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.006 s <<< FAILURE!",
     "[ERROR]   FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt:62 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R7 to-is-inclusive": {
   "status": "KILLED",
   "clause": "toMs is INCLUSIVE",
   "requirement": "G-R7",
   "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 229,
   "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
    "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
   ],
   "assertionFailures": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
    "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
   ],
   "anyFailures": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
    "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
   ],
   "killedByThrow": null,
-  "seconds": 8.2,
+  "seconds": 9.6,
   "patch": {
    "old": "            String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));",
    "new": "            String hi = barKey(tf, Math.min(toMsInclusive - 1, EPOCH_MAX_MS));"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "f448803e303bdbdfaddcad7f8d1851a2cc77d9f21decc7d80ed25eea755d895a",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "f448803e303bdbdfaddcad7f8d1851a2cc77d9f21decc7d80ed25eea755d895a"
     }
    },
-   "outputSha256": "95a9d3baf3d7a4fdf7091ce90d177822826a1ab9c3643db52c29c51411861d8d",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "b1c2ad68bf4c07d7a5a142d00b8cfe040df2b68d85ce57878064bcc9d96c1a06",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.003 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.016 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2>",
     "[ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false>",
     "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:200 expected: <3> but was: <2>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R7 limit-clamped-low": {
   "status": "KILLED",
   "clause": "the forwarded limit is clamped to at least 1",
   "requirement": "G-R7",
   "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   "file": "src/main/java/app/feedgateway/GatewayController.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 130,
   "enclosing": "static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
   "assertionFailures": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
   "anyFailures": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
   "killedByThrow": null,
-  "seconds": 8.2,
+  "seconds": 9.2,
   "patch": {
    "old": "    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
    "new": "    static int clamp(int limit) { return Math.min(limit, FOOTPRINT_LIMIT_MAX); }"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "0b7f3a2971dc69c3850ba867c539c6f2b6b17a8013993bc0a9d661ffb40162dd",
    "originalFileSha256": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
    "filesSha256": {
     "src/main/java/app/feedgateway/GatewayController.java": {
      "original": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
      "mutated": "0b7f3a2971dc69c3850ba867c539c6f2b6b17a8013993bc0a9d661ffb40162dd"
     }
    },
-   "outputSha256": "09266bb5425363e6658924af73142f54c7c0df54271a460d00a60d64a05fba66",
+   "outputSha256": "97da7c51916ad7c3fe29595df3ba06ebadab73e12d28d717f8e575b1bd97d225",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R7 limit-clamped-high": {
   "status": "KILLED",
   "clause": "the forwarded limit is clamped to the ceiling",
   "requirement": "G-R7",
   "documentText": "`limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is \u2264 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.",
   "file": "src/main/java/app/feedgateway/GatewayController.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 130,
   "enclosing": "static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
   "assertionFailures": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
   "anyFailures": [
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
   "killedByThrow": null,
-  "seconds": 8.6,
+  "seconds": 10.6,
   "patch": {
    "old": "    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
    "new": "    static int clamp(int limit) { return Math.max(1, limit); }"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "55236d83d718eff57ec004a64e589fa1637998835869484f7548cbc1aeeca9b9",
    "originalFileSha256": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
    "filesSha256": {
     "src/main/java/app/feedgateway/GatewayController.java": {
      "original": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
      "mutated": "55236d83d718eff57ec004a64e589fa1637998835869484f7548cbc1aeeca9b9"
     }
    },
-   "outputSha256": "a5093d0bae85c86ff08851e60364d638c359aa03b9090e38dcca0a6125350ddd",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "a87149fe9eb3f64556e73d2e8ddcaef385aeb5dfa71f88272658e00ab137445b",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.009 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R7 epoch-guard": {
   "status": "KILLED",
   "clause": "an out-of-domain timestamp is refused",
   "requirement": "G-R7",
   "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 329,
   "enclosing": "private static long epochField(JsonNode node, String field) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
   "assertionFailures": [
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
   "anyFailures": [
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
   "killedByThrow": null,
-  "seconds": 8.4,
+  "seconds": 8.6,
   "patch": {
    "old": "        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;",
    "new": "        return x < 0 ? -1 : x;"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "e553a6d4e5b1827ccdc4191e78f47425f75f040f094bff517831a91eaf15a7f8",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "e553a6d4e5b1827ccdc4191e78f47425f75f040f094bff517831a91eaf15a7f8"
     }
    },
-   "outputSha256": "79d36104056f411b2c50af62840fd5f4c90a69c97014af854f8b22c9b863ede9",
+   "outputSha256": "63c03a3e0c428c83066cbb2eb4188bc9686808ad6f7a22018c03afe610781a3a",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.003 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R7 epoch-guard-removed": {
   "status": "KILLED",
   "clause": "a cursor at or past the last epoch value can have nothing after it, and the increment is taken only inside the domain",
   "requirement": "G-R7",
   "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 226,
   "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   ],
   "assertionFailures": [
    "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   ],
   "anyFailures": [
    "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   ],
   "killedByThrow": null,
-  "seconds": 8.0,
+  "seconds": 8.4,
   "patch": {
    "old": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {",
    "new": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0) {"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "bcdfcc3334a5212551a86cd937777facd6b5915b9f634abbb7a9a99469c0add6",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "bcdfcc3334a5212551a86cd937777facd6b5915b9f634abbb7a9a99469c0add6"
     }
    },
-   "outputSha256": "8d6216bbfeb5d2d1fe8a7487be65d4944bd2326c64898ed85772caaeea835051",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "2e473b8213962c118dde054e3292e1be92dec67dd677ed6a8ddd9661c73962c8",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:185 a cursor beyond the domain admits nothing \u2014 it must not wrap ==> expected: <0> but was: <4>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R7 epoch-guard-relaxed": {
   "status": "SURVIVED",
   "clause": "the same guard relaxed from < to <= \u2014 recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way",
   "requirement": "G-R7",
   "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 226,
   "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [],
   "assertionFailures": [],
   "anyFailures": [],
   "killedByThrow": null,
-  "seconds": 10.2,
+  "seconds": 13.2,
   "patch": {
    "old": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {",
    "new": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive <= EPOCH_MAX_MS) {"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 0,
    "mutatedFileSha256": "1805041d85205e5bad948989b9f83b1f4d321c9f9322dd96226cf8761ffb8058",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "1805041d85205e5bad948989b9f83b1f4d321c9f9322dd96226cf8761ffb8058"
     }
    },
-   "outputSha256": "1d764bb5711780398eb98c0656685427d85389d4f196223567271ef456279e5c",
+   "outputSha256": "f98d9901a798da0ed73bc57a9723db936e89b944fe2b293b54b923ff92b10188",
    "outputTail": "tures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n",
    "failureLines": [],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R1.1 the-flag-gates-every-footprint-object": {
   "status": "KILLED",
   "clause": "Flag off: the views are never instantiated",
   "requirement": "G-R1",
   "documentText": "Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{\"enabled\":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 809,
   "enclosing": "public class FeedGatewayService implements ReplayRunner {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing",
    "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
    "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions",
    "FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge"
   ],
   "assertionFailures": [
    "FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing",
    "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
    "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions",
    "FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge"
   ],
   "anyFailures": [
    "FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing",
    "FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns",
    "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
    "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"
   ],
   "killedByThrow": null,
-  "seconds": 7.9,
+  "seconds": 9.8,
   "patch": {
    "old": "        if (settings.esFootprintEnabled()) {",
    "new": "        if (true) {"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "884cdb7be1fa827c909b4825d3adcbd701327d0771191be49d202c08cf1a7723",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "884cdb7be1fa827c909b4825d3adcbd701327d0771191be49d202c08cf1a7723"
     }
    },
-   "outputSha256": "cf01fb8e5adb28461181ee22853431180719b36de46b6e59e91936cc67880384",
+   "outputSha256": "83a116439c67b929ab7a4d0e45e16c48e628570c7711457e27706eb2ec88040c",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.010 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.347 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.419 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.013 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.259 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.325 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401>",
     "[ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123",
     "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]>",
     "[ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true>",
     "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R2.1 one-wiring-method-binds-all-four": {
   "status": "KILLED",
   "clause": "ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers",
   "requirement": "G-R2",
   "documentText": "ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 2392,
   "enclosing": "void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) { // package-private: the wiring test EXECUTES it",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "assertionFailures": [
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "anyFailures": [
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "killedByThrow": null,
-  "seconds": 7.9,
+  "seconds": 8.8,
   "patch": {
    "old": "        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));",
    "new": "        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817"
     }
    },
-   "outputSha256": "e990ea49f7f2749896312c76c9624ebdfdfead29e4228446745d08ac6b1c9e05",
+   "outputSha256": "36a6ce3ea255feca32515790717875b45fb874b1f99ca4e17ae5f390e8820194",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE!",
-    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot"
+    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R2.2 the-evidence-binding": {
   "status": "KILLED",
   "clause": "KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)",
   "requirement": "G-R2",
   "documentText": "**Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` \u2192 `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` \u2192 `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` \u2192 `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` \u2192 `futures.footprint.outcomes` (`es-footprint-outcome`).",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 2390,
   "enclosing": "void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) { // package-private: the wiring test EXECUTES it",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "assertionFailures": [
    "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "anyFailures": [
    "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "killedByThrow": null,
-  "seconds": 8.2,
+  "seconds": 10.5,
   "patch": {
    "old": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-evidence\"));",
    "new": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint\"));"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8"
     }
    },
-   "outputSha256": "46a9a613d04d484ba2be4857834e773f6f0b6b8080d85af88760d7cfc8ece087",
+   "outputSha256": "dff1eb8d0d7b95dba979f4ba69cac4a178f8f433f506f1abe9aabf4126723ba6",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.014 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint\" ==> expected: <1> but was: <2>",
-    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-"
+    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R3.1 an-oversize-record-is-dropped-not-broadcast": {
   "status": "KILLED",
   "clause": "a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped",
   "requirement": "G-R3",
   "documentText": "`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) \u2014 a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 905,
   "enclosing": "boolean admitFootprintRecord(String event, String json, String consumer) { // package-private: the wiring tests drive both consumer paths th",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "assertionFailures": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "anyFailures": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 8.1,
+  "seconds": 10.0,
   "patch": {
    "old": "        return a.reason() != FootprintViews.Reason.OVERSIZE;",
    "new": "        return true;"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "5134cf0f9808ec272649bb6533231d5dd0a952cd584dd200754d8f424af03125",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "5134cf0f9808ec272649bb6533231d5dd0a952cd584dd200754d8f424af03125"
     }
    },
-   "outputSha256": "9d7c982209d1d91082438c392188b1da1ef4195abbd8126df02972741fef83c9",
+   "outputSha256": "9d3883d5d359d4c1f93a82b3dab306cd07b2900031a677448ad852b4f21323c4",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.149 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.015 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.171 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.017 s <<< FAILURE!",
     "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true>",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R3.2 a-rejected-record-is-still-broadcast": {
   "status": "KILLED",
-  "clause": "a record the view REJECTS for shape, session or size is still broadcast",
+  "clause": "a record the view REJECTS for shape or session is still broadcast",
   "requirement": "G-R3",
   "documentText": "`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) \u2014 a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 905,
   "enclosing": "boolean admitFootprintRecord(String event, String json, String consumer) { // package-private: the wiring tests drive both consumer paths th",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "assertionFailures": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "anyFailures": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 8.1,
+  "seconds": 9.7,
   "patch": {
    "old": "        return a.reason() != FootprintViews.Reason.OVERSIZE;",
    "new": "        return a.reason() == FootprintViews.Reason.ADMITTED;"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "66a9f89dfab6c87ccac729e9bcaf04b11ca59d06c4c08602d8ad7be5da36b8d1",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "66a9f89dfab6c87ccac729e9bcaf04b11ca59d06c4c08602d8ad7be5da36b8d1"
     }
    },
-   "outputSha256": "23d9cd8ee4d8b397d1aceae1d8a9b671d6a96a286c211338f86902173432893c",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "526f404044c70ebe1ab33d970f810703bf67b8f9c0f925c8c9dc588d32526b0b",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.129 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.014 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.144 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.024 s <<< FAILURE!",
     "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false>",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R3.3 live-snapshots-are-never-retained": {
   "status": "KILLED",
   "clause": "never cached, never retained",
   "requirement": "G-R3",
   "documentText": "**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every \u2264 5 s \u2014 F-R15 \u2014 so a new client waits at most one heartbeat; the same class as `es-cvd`).",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 6534,
   "enclosing": "CachePolicy cachePolicyFor(String event, long nowMs) { // package-private: the retention test EXECUTES it",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"
   ],
   "assertionFailures": [
    "FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"
   ],
   "anyFailures": [
    "FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"
   ],
   "killedByThrow": null,
-  "seconds": 8.1,
+  "seconds": 9.7,
   "patch": {
    "old": "            return CachePolicy.expiring(0L);",
    "new": "            return CachePolicy.expiring(settings.esFootprintSeekBackMs());"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "606a7fa3701572b9a8978bf6c9c8a0a027c1e56e2dcca908b19ff84993edc72f",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "606a7fa3701572b9a8978bf6c9c8a0a027c1e56e2dcca908b19ff84993edc72f"
     }
    },
-   "outputSha256": "a7e85731de267cebcdc7b23bd0aca617ec151bff4c356d52ac08f274671b9648",
+   "outputSha256": "dc3bd0f89961a3d6da485c92e5caa786f0cb2c6e9dfa0dff2d5bee7e6e541152",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R5.1 the-outcome-key-orders-numerically": {
   "status": "KILLED",
   "clause": "the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)",
   "requirement": "G-R5",
   "documentText": "The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 296,
   "enclosing": "static String outcomeKey(String tf, long resolvedAtBarStartMs, String identity) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"
   ],
   "assertionFailures": [
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"
   ],
   "anyFailures": [
    "FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck",
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace",
    "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks"
   ],
   "killedByThrow": null,
-  "seconds": 7.7,
+  "seconds": 11.5,
   "patch": {
    "old": "        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;",
    "new": "        return tf + \"|\" + resolvedAtBarStartMs + \"|\" + identity;"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec"
     }
    },
-   "outputSha256": "7746015e8bfdc22b092c6c726ba4db631d4fef40271590a93329b6082c5bbf06",
+   "outputSha256": "c13b0ad4e299169c8d5f71115ac8de84d29e77628f9a22885d423a831c84d433",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.062 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.180 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.002 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.002 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR!",
     "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8>",
     "[ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000>",
     "[ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 \u00bb NumberFormat For input string: \"10|ES.v.0|1m|1|BUYE\"",
     "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 \u00bb NumberFormat For input string: \"5|ES.v.0|1m|1|BUYER\"",
     "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 \u00bb NumberFormat For input string: \"77|ES.v.0|5m|1|SELL\"",
     "[ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 \u00bb StringIndexOutOfBounds Range [3, 22) out of bounds for length 8"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R5.2 the-identity-is-the-last-component": {
   "status": "KILLED",
   "clause": "identity CONTAINS |, which is why it is the LAST component",
   "requirement": "G-R5",
   "documentText": "`identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 296,
   "enclosing": "static String outcomeKey(String tf, long resolvedAtBarStartMs, String identity) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"
   ],
   "assertionFailures": [
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"
   ],
   "anyFailures": [
    "FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck",
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace",
    "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks"
   ],
   "killedByThrow": null,
-  "seconds": 7.8,
+  "seconds": 10.2,
   "patch": {
    "old": "        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;",
    "new": "        return tf + \"|\" + identity + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs);"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "7a75b9af663605c79e061f5994d60b4ec216178fbe6ad27e44f8cc7f76c49e57",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "7a75b9af663605c79e061f5994d60b4ec216178fbe6ad27e44f8cc7f76c49e57"
     }
    },
-   "outputSha256": "e080f8050c1230acb06a600632172742248f1ede2c86b5fc442571d30ec6acb9",
+   "outputSha256": "57f2fac001b550e7801a5dec8fbfcfa8adf7a8f25f002eeb1ad06a9af991ec10",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.082 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.002 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.120 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.002 s <<< ERROR!",
     "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR!",
     "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: \"id0|000000000000000\">",
+    "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: \"id1|000000000000000\">",
     "[ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005>",
     "[ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\"",
     "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\"",
     "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 \u00bb NumberFormat For input string: \"z|00000000000000000\"",
     "[ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\""
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R5.3 a-foreign-identity-is-refused": {
   "status": "KILLED",
   "clause": "a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)",
   "requirement": "G-R5",
   "documentText": "Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 125,
   "enclosing": "|| !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
   "assertionFailures": [
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
   "anyFailures": [
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
   "killedByThrow": null,
-  "seconds": 7.7,
+  "seconds": 11.1,
   "patch": {
    "old": "                || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {",
    "new": "                || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "b41e5ceceaa36f9b18b60ab36542e40adda355dc0f933bbe8d3a92fb6cf1a31f",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "b41e5ceceaa36f9b18b60ab36542e40adda355dc0f933bbe8d3a92fb6cf1a31f"
     }
    },
-   "outputSha256": "34e0bbe26c7578b335255a5a0756828ed748708aee617ead4177779ab6e38620",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "8d74c3ef2ab8fbd0528a72e9b241e6e70edab2f24d2fc8c6e0b28675657b0672",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.002 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.shapeDropsNeverMutate:40 expected: <SHAPE> but was: <ADMITTED>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R5.4 last-write-per-key-wins": {
   "status": "KILLED",
   "clause": "Last write per full key wins; a redelivery carries the same key and overwrites in place",
   "requirement": "G-R5",
   "documentText": "Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 132,
   "enclosing": "Admission admitOutcome(String json) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"
   ],
   "assertionFailures": [
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"
   ],
   "anyFailures": [
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"
   ],
   "killedByThrow": null,
-  "seconds": 8.1,
+  "seconds": 10.2,
   "patch": {
    "old": "            String previous = outcomes.put(key, json);",
    "new": "            String previous = outcomes.putIfAbsent(key, json);"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "1a29ff784d9ae87c27fa6256fe064b480ac945623b6facdba5238d69fa17937f",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "1a29ff784d9ae87c27fa6256fe064b480ac945623b6facdba5238d69fa17937f"
     }
    },
-   "outputSha256": "8d4b38efdb076cab61e8c72eefabb6fee2522aa66cb2838098e7d8641dcf08f5",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "c53cbd4c751e01a5e7a09c1bd9f648487fa0a02732b20daa8b378e6a45df4d3b",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.006 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R6.1 the-hello-carries-the-footprint-field": {
   "status": "KILLED",
   "clause": "the existing cvd-hello frame gains ONE field footprint",
   "requirement": "G-R6",
   "documentText": "**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 11180,
   "enclosing": "StringBuilder sb = new StringBuilder(\"{\\\"sessionDate\\\":\");",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
   ],
   "assertionFailures": [
    "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
   ],
   "anyFailures": [
    "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
   ],
   "killedByThrow": null,
-  "seconds": 9.2,
+  "seconds": 12.6,
   "patch": {
    "old": "            sb.append(\",\\\"footprint\\\":\").append(footprintViews.helloField());",
    "new": "            sb.append(\",\\\"footprintX\\\":\").append(footprintViews.helloField());"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "5203835d0356ee0a9d2d478680c07baa48e3770b66cbe0c85d2a924d70865ae2",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "5203835d0356ee0a9d2d478680c07baa48e3770b66cbe0c85d2a924d70865ae2"
     }
    },
-   "outputSha256": "49c12dc852a157f6d9f93186c20ff7a75191d0914751633bf59ea42de62e55ed",
+   "outputSha256": "2a06a54b559a994b5fce7084d6e2b25ad96d9c8f45f1b824ed444c379f7a981b",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:152 expected: <{\"sessionDate\":null,\"hwm\":{},\"footprint\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}}> but was: <{\"sessionDate\":null,\"hwm\":{},\"footprintX\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}}>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R6.2 the-hello-is-sent-when-footprint-alone-is-on": {
   "status": "KILLED",
   "clause": "cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled",
   "requirement": "G-R6",
   "documentText": "`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 11158,
   "enclosing": "boolean sendsCvdHello() {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
   ],
   "assertionFailures": [
    "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
   ],
   "anyFailures": [
    "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
   ],
   "killedByThrow": null,
-  "seconds": 7.9,
+  "seconds": 10.0,
   "patch": {
    "old": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;",
    "new": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc"
     }
    },
-   "outputSha256": "7308a99ed9386745d8eea56a7abe41f98f738771b5ed151eb16e251cb6a08468",
+   "outputSha256": "da516b2828cb21ac518bada8d5eac3a01a94a9f833dcf5397b242ae3de7b455a",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
     "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.003 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R6.3 the-hello-carries-the-outcome-high-water-mark": {
   "status": "KILLED",
   "clause": "outcomeHwm:{tf: maxResolvedAtBarStartMs}",
   "requirement": "G-R6",
   "documentText": "**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 195,
   "enclosing": "StringBuilder sb = new StringBuilder(\"{\\\"sessionDate\\\":\");",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
    "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
   ],
   "assertionFailures": [
    "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
    "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
   ],
   "anyFailures": [
    "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
    "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
   ],
   "killedByThrow": null,
-  "seconds": 7.9,
+  "seconds": 9.8,
   "patch": {
    "old": "            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, outcomes, true);",
    "new": "            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, bars, true);"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "368f116e662d3150e861190653954f413fbe2ce71254dec4501542935a35c416",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "368f116e662d3150e861190653954f413fbe2ce71254dec4501542935a35c416"
     }
    },
-   "outputSha256": "e0fe1e632270a5f0e9e20b09db8e1ac66029ab87056468f7d3085e18bb020a31",
+   "outputSha256": "95a01fc29841ba76302b3e2d410c1950cabbb24c9556262307f2e928270580d2",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"30s\":2000,\"1m\":5}}>",
     "[ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R10.1 a-footprint-record-never-escapes-the-consumer-loop": {
   "status": "KILLED",
   "clause": "a footprint record can never throw out of the consumer loop \u2014 the mutation makes it throw, and the test that calls it propagates that throw",
   "requirement": "G-R10",
   "documentText": "A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 2,
   "line": 93,
   "enclosing": "Admission admitBar(String json) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintViewsTest.aNullPayloadIsARefusalNotAThrow"
   ],
   "assertionFailures": [
    "FootprintViewsTest.aNullPayloadIsARefusalNotAThrow"
   ],
   "anyFailures": [
    "FootprintViewsTest.aNullPayloadIsARefusalNotAThrow"
   ],
   "killedByThrow": null,
-  "seconds": 7.9,
+  "seconds": 11.5,
   "patch": {
    "old": "        if (json == null) return Admission.SHAPE;",
    "new": "        if (json == null) throw new IllegalArgumentException(\"json\");"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "5b915b8626df30f329a936e4fceef00fce3d32c66a3f820c7439a0cd381d7650",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
      "mutated": "5b915b8626df30f329a936e4fceef00fce3d32c66a3f820c7439a0cd381d7650"
     }
    },
-   "outputSha256": "6a5741b648f7d313f6f3b268d93329c57e2025ce04a8c357ea5c260c6ada3d5a",
+   "outputSha256": "495c6b9efeef51691efa70273bc81641f13a52cc8b635684224823d1b62ee32d",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.008 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:85 a null bar payload must not throw ==> Unexpected exception thrown: java.lang.IllegalArgumentException: json"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R10.2 every-admission-failure-is-a-counted-drop": {
   "status": "KILLED",
   "clause": "every admission failure is a counted drop",
   "requirement": "G-R10",
   "documentText": "A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 903,
   "enclosing": "footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").in",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "assertionFailures": [
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "anyFailures": [
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 7.8,
+  "seconds": 10.6,
   "patch": {
    "old": "            footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").incrementAndGet();",
    "new": "            if (a.reason() != FootprintViews.Reason.SHAPE) footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").incrementAndGet();"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "0520888256109aba632dd9b7c1ae8a7df49b51fd61e33136f5a8b098fb1584c6",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "0520888256109aba632dd9b7c1ae8a7df49b51fd61e33136f5a8b098fb1584c6"
     }
    },
-   "outputSha256": "e55c04c4c122785c085418d2baa2017f039ed9cb58455461fc18ffbdadb3b83e",
+   "outputSha256": "004035b0e09fd4deda345254b7f496efd6402285905a882f375a7ac2aed1b1bf",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.153 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.248 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
- "G-R10.3 every-record-is-counted-before-admission": {
+ "G-R9.2 every-record-is-counted-before-admission": {
   "status": "KILLED",
   "clause": "records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too",
-  "requirement": "G-R10",
-  "documentText": "**Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's).",
+  "requirement": "G-R9",
+  "documentText": "`gateway_footprint_records_total{event,consumer}` \u2014 every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
   "line": 897,
   "enclosing": "footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "assertionFailures": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "anyFailures": [
    "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 8.0,
+  "seconds": 10.9,
   "patch": {
    "old": "        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {",
    "new": "        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission\n        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {"
   },
   "evidence": {
-   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "returnCode": 1,
    "mutatedFileSha256": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
     "src/main/java/app/feedgateway/FeedGatewayService.java": {
      "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
      "mutated": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e"
     }
    },
-   "outputSha256": "3aceba9b2d582160d00321118d23fe7472f18d0fe4c09d15af72fefe7d83ba2d",
+   "outputSha256": "4bb2899583fc11e9cf410f45b8a0d6cc72b64e76b033ba79409f50c2c68d48c8",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.147 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.013 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.204 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.017 s <<< FAILURE!",
     "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false>",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  }
 }
\ No newline at end of file
diff --git a/ES-FOOTPRINT-GATEWAY-DESIGN.md b/ES-FOOTPRINT-GATEWAY-DESIGN.md
index 2c6002e..10133df 100644
--- a/ES-FOOTPRINT-GATEWAY-DESIGN.md
+++ b/ES-FOOTPRINT-GATEWAY-DESIGN.md
@@ -148,82 +148,82 @@ under last-write-per-key. Epoch-ms values lie in `[0, 253402300799999]` (§5 tim
 
 | id | Requirement |
 |----|-------------|
 | G-R1 | **Flag.** `GATEWAY_ES_FOOTPRINT_ENABLED` (default `false`; `GatewaySettings.esFootprintEnabled()`). Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`. Flag on with a topic absent on the cluster: the gateway starts (the topics are OPTIONAL like every other DATABENTO JSON topic) and the page reports no data. |
 | G-R2 | **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`). ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`. |
 | G-R3 | **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`). `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt). All four events are added to the per-session standalone allowlist (D5). |
 | G-R4 | **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock. Admission of any bar/outcome record: parse JSON; `sessionDate` must be present and parse with `LocalDate.parse(s, ISO_LOCAL_DATE)` AND round-trip to the same string (strict canonical `YYYY-MM-DD`); `timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates). Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances; OLDER ⇒ `stale_session` drop; EQUAL (or first) ⇒ upsert (last write per key wins). Because one coordinator rolls both views, a bar from session N+1 arriving before any outcome of N+1 clears N's outcomes with N's bars, and an outcome of N arriving after that is dropped — whichever topic the cache consumer drains first. |
 | G-R5 | **Outcome view.** `outcomes: TreeMap<tf\|%019d(resolvedAtBarStartMs)\|identity, json>` in the SAME coordinator. The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain; `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component). Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast. |
 | G-R6 | **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held). It rides the SAME frame so the page has one handshake; `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled. Flag off, the field is absent (its ABSENCE tells the page the gateway has no footprint). |
 | G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's). `GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` ascending by view key within `tf`, `toMs` inclusive on `resolvedAtBarStartMs`, `after` = an EXCLUSIVE lower bound given as a previous page's `nextCursor` (the last key string). Cursor grammar: `tf|<19 digits>|<identity>` where `tf` equals the query `tf`; an `after` that is non-empty and fails the grammar ⇒ HTTP 400 `{"error":"bad cursor"}`; a well-formed cursor that no longer exists is legal (string lower bound). `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from. Same JWT gate as every `/api` route. **Processing order (exactly one outcome per request, in the AS-IS convention):** (1) Spring typed binding of `toMs`/`limit`/`afterMs` ⇒ 400 on malformed input BEFORE the handler runs (so a malformed request is never counted and never sees the flag or auth); (2) in the handler, flag off ⇒ 404 `{"enabled":false}`; (3) explicit `LiquidityHistoryAuth.authenticate(...)` exactly as `LiquidityHistoryController` does (`:86`) ⇒ its 401/403 on failure; (4) permit: `tryAcquire` on a `Semaphore(GATEWAY_ES_FOOTPRINT_BACKFILL_CONCURRENCY)` (default 4) ⇒ 503 `Retry-After: 1` (`busy`) when none is free; (5) cursor grammar (outcomes route) ⇒ 400 `{"error":"bad cursor"}`; (6) page snapshot under the coordinator lock — the session check (`sessionMismatch`, HTTP 200) and the selection of up to `limit` record REFERENCES into a `List<String>` (no copies) plus `sessionDate` and `nextCursor`; (7) the response is STREAMED: the controller writes to `HttpServletResponse.getOutputStream()` through ONE fixed 64 KiB buffer, the envelope pieces and each record as `record.getBytes(US_ASCII)` one at a time (records are ASCII by F-E8), status 200 set before the first byte; the permit is released in `finally` after the stream is flushed (or the write failed). The lock is held only during (6), never during (7). Transient memory per request is therefore ≤ one record's bytes (≤ 262 144 B) + the 64 KiB buffer + the reference list (≤ 100 × 4 B) < 0.32 MiB (G-R8). |
 | G-R8 | **Bounds and heap (proven from the limits, never from averages).** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key. **Retained-heap bound** (HotSpot, compressed oops and compressed class pointers — true for any heap < 32 GiB, as here): a JSON value is ASCII (F-E8 alphabet) so it is a Latin-1 compact `String`: 24 B object + 16 B array header + payload, aligned to 8 ⇒ ≤ payload + 48 B; a key `String` ≤ 160 B payload ⇒ ≤ 208 B; a `TreeMap.Entry` = 40 B; per entry overhead ≤ 296 B, so overhead ≤ (12 000 + 20 000) × 296 B = 9.04 MiB; payload ≤ 128 + 16 = 144 MiB; the coordinator's accounting (two `long` counters, one `LocalDate`, per-timeframe eviction cursors) < 1 KiB. Views total ≤ 153.1 MiB. **Transient** per in-flight backfill (G-R7 step 7): < 0.32 MiB, at most 4 concurrent ⇒ < 1.3 MiB; no `StringBuilder`, no response `String`, no encoder buffer beyond the fixed 64 KiB. **Kafka transient**, from the client's fetch mechanics and THREE enforced facts. Mechanics (Kafka consumer `Fetcher`, unchanged since KIP-74): the consumer keeps at most ONE in-flight fetch request per broker node, a fetch request for a node includes only assigned partitions that have NO buffered (completed, unconsumed) data, each partition's share of a response is ≤ `max.partition.fetch.bytes` except that the FIRST batch of a partition is returned whole when it is larger, and `max.poll.records` only paces how many buffered records a `poll()` hands out. Enforced facts: (i) `max.partition.fetch.bytes` = 512 KiB (§1); (ii) `M` = each footprint topic's effective `max.message.bytes` ≤ `GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588, the broker default), validated per G-R8a; (iii) the four topics are UNCOMPRESSED end to end — the upstream producer sets `compression.type=none` explicitly (options-edge-processing amendment: `EsCvdRuntime.producerProps`, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` per G-R8a — so a fetched share IS its decompressed size and no decompression buffer exists. Per consumer, each of the four single-partition footprint topics contributes at most: one in-flight share (≤ max(512 KiB, M) ≤ 1.0 MiB) + one buffered completed share (≤ 1.0 MiB) + the deserialized value `String`s of the records a `poll()` returned from that share (≤ the share's bytes + 48 B per record) ⇒ ≤ 3.0 MiB per topic + per-record overhead, ≤ 12.0 MiB per consumer + per-record overhead. Per-record client overhead for the records of ONE poll — here, and only here, bounded by `max.poll.records` = 100 (§1): key `String` ≤ 208 B, `ConsumerRecord` object ≤ 96 B (header, 7 references, two longs, two ints), empty `RecordHeaders` ≤ 32 B, `ConsumerRecords` map/list share ≤ 64 B ⇒ ≤ 400 B × 100 = 40 KiB. Per consumer ≤ 12.04 MiB; TWO consumers touch footprint topics (state cache, state live) ⇒ ≤ 24.1 MiB, rounded to 25 MiB. Non-footprint partitions in the same fetch responses are not attributable to this feature and are already part of the measured `H_peak`. **Layout assumptions are verified at start-up** when the flag is on: `HotSpotDiagnosticMXBean.getVMOption` for `UseCompressedOops`, `UseCompressedClassPointers` and `CompactStrings` must all be `true`, else the gateway refuses to start naming the flag (the arithmetic above is void otherwise); pinned by a test of the checker over a fake option map. Topic-side facts (ii) and (iii) are verified by G-R8a. **Footprint heap contribution ≤ 153.1 + 1.3 + 25 = 179.4 MiB, rounded to 180 MiB**, independent of record size distribution. **Deployment contingency (§4):** the flag may be enabled only when the deploy PR records, from a full ES session with CVD on and footprint off: (a) heap: `jvm_memory_bytes_used{area="heap"}` peak `H_peak` with `-Xmx − H_peak ≥ 180 MiB + 128 MiB = 308 MiB` (GC headroom); (b) container: `container_memory_working_set_bytes` peak `W_peak` with `limit − W_peak ≥ 180 MiB + 256 MiB = 436 MiB`, AND `limit − Xmx ≥ 512 MiB` (metaspace, code cache, thread stacks, socket/direct buffers, JVM overhead — the current es4 manifest has `limit = Xmx = 1536 MiB`, which violates this and MUST change in the same PR: e.g. `-Xmx1536m` with `limit 2560Mi`, or a measured alternative that satisfies both inequalities). Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits and the tests pin them. |
 | G-R8a | **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`. **Start-up** (`start()`, before any consumer launches): validate the topics that EXIST; an existing INVALID topic ⇒ refuse to start naming topic and value (fail-closed); an absent topic (`UnknownTopicOrPartitionException` for that resource) ⇒ deferred; any other Admin failure ⇒ refuse to start. **The seam (an API change to `PartitionRefresh`, the AS-IS contract otherwise kept):** `PartitionRefresh` gains a constructor parameter `Predicate<String> topicAdmit`: of the SIX existing constructor call sites (`:1739` selection, `:2087` hpsf-cache, `:2125` hpsf-live, `:2201` alerts, `:2303` state cache, `:2674` state live), the two STATE consumers pass `footprintGate::admit` and the other FOUR pass `t -> true`. Inside `apply()`, AFTER `partitionsFor` discovers new partitions and BEFORE `mergedAssignment`/`consumer.assign(...)` (`FeedGatewayService.java:3665-3671`), discovered partitions whose topic fails `topicAdmit` are WITHHELD: they are not merged, not assigned, not in `Refresh.added()`, hence never sought and never in the caller's caught-up bookkeeping — identical to a topic that does not exist yet; they are re-discovered (and re-evaluated) at the next refresh interval. The same predicate is applied to the bootstrap `partitionsFor` result before the initial `assign` in both state consumers. `addEsFootprintTopics` adds ALL FOUR topics to both consumers' `topicEvents` unconditionally, so the immutable `Set.copyOf(topics)` always contains them and discovery works whenever a topic appears. **`FootprintTopicGate.admit(topic)`** runs on the calling poll thread: a non-footprint topic ⇒ true; a footprint topic already marked VALID ⇒ true; otherwise it validates NOW (bounded timeout): VALID ⇒ mark and true; unknown topic, timeout or other Admin failure ⇒ false this cycle (counted, logged once per state change, retried at the next refresh — the absorbed-refresh-failure discipline); existing-but-invalid ⇒ false (counted `ceiling`/`compression`, logged once per state change). Per-topic state in a `ConcurrentHashMap` shared by both consumers (each evaluates on its own thread; a duplicate validation is harmless); once VALID a topic stays VALID for the incarnation (a topic config change after admission requires a gateway restart — §4 operational rule). Adoption of a late-admitted topic then follows the existing caller behaviour for `Refresh.added()`: the cache consumer seeks its window (a compacted keyed topic replays its retained keys into the coordinator, as `es-cvd-bar` does), the live consumer seeks END. The two validation series are defined in G-R9. |
 | G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
 | G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
 | G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |
 
 <!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->
 
 ## 2a. Conformance — what a test actually holds
 
 Every requirement above, and what this repository's mutation campaign established about it. The
 campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
 failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
 the spec declared in advance — never an incidental error under the right test's name; and the table
 below refuses to render a "pinned" cell for any record that does not carry its evidence. That
 evidence — the patch, the file, line and enclosing declaration, the command, the exit code, the
 verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
 this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
 record with `scripts/footprint-reqstate.sh --check`.
 
 | id | Conformance | Gate | Disposition |
 |----|-------------|------|-------------|
 | G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
 | G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
 | G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
 | G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
 | G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
 | G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
 | G-R7 | 5 of 6 probes pinned, over 6 clauses (1 survived) | 2 | Backfill routes |
 | G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
-| G-R9 | 1 of 1 probes pinned, over 1 clause | 2 | Metrics |
-| G-R10 | 3 of 3 probes pinned, over 3 clauses | 2 | Non-interference |
+| G-R9 | 2 of 2 probes pinned, over 2 clauses | 2 | Metrics |
+| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
 | G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
 | G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | Deployment contingency |
 
 12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).
 
 Read the state column narrowly. "n of m probes pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION (or propagate a throw the spec declared and the run matched) — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.
 
 <!-- END footprint-reqstate -->
 
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
 
 ## 4. Deploy (es4) and the upstream prerequisite
 
 **Upstream prerequisite (options-edge-processing) — MERGED:** PR #757 (`feat/es-footprint-uncompressed`,
 Codex APPROVE) sets `compression.type=none` explicitly in `EsCvdRuntime.producerProps`, pins it by
 test and records ES-FOOTPRINT-DESIGN.md amendment 12; fact (iii) of G-R8 therefore holds by
 contract on the producer side, and G-R8a's `compression.type` check on each topic remains the
 continuing runtime guard against a topic-level codec being configured later.
 Operational rule: a footprint topic's config change after admission requires a gateway
 restart (G-R8a caches VALID per incarnation).
 
 
 `k8s/es4/services/es-feed-gateway.yaml`: `GATEWAY_ES_FOOTPRINT_ENABLED=true`, the four
commit ae8a68762366e752a4010ee2fdaf850fbdcd9fde
Author: Abhinav Jain <abhinav.jain09@gmail.com>
Date:   Tue Sep 8 22:54:38 2026 +0200

    wip: generator round-3 folds

diff --git a/scripts/footprint-reqstate.py b/scripts/footprint-reqstate.py
index 6e67037..d24772e 100644
--- a/scripts/footprint-reqstate.py
+++ b/scripts/footprint-reqstate.py
@@ -1,99 +1,112 @@
 #!/usr/bin/env python3
 """Render the §9 requirement-reconciliation table from the CHECKED-IN campaign record.
 
 The state column is derived, never typed: a requirement is what its mutations say it is. A
 requirement the campaign did not probe says exactly that, and claims nothing else — the failure mode
 this replaces is a hand-maintained table drifting away from the code it claims to describe.
 """
 import json, re, sys, collections
 
 def main():
     recordPath, docPath, idPattern, gates, dispositions = sys.argv[1], sys.argv[2], sys.argv[3], json.loads(sys.argv[4]), json.loads(sys.argv[5])
     # Requirements whose subject is not production behaviour (a test inventory) or whose behaviour
     # lives in ANOTHER repository. Neither can be probed here, and neither is a gap — but saying
     # "NOT PROBED" without saying why invites the reader to assume it is one.
     notes = json.loads(sys.argv[6]) if len(sys.argv) > 6 else {}
     rec = json.load(open(recordPath))
     # Discover requirement ids from the AUTHORITATIVE part of the document only — everything before
     # the generated block. Reading the whole file lets the previous render's own rows keep a
     # requirement alive after it has been deleted upstream, and --check would then accept it.
     text = open(docPath).read()
-    cut = text.find('<!-- BEGIN footprint-reqstate')
-    authoritative = text if cut < 0 else text[:cut]
+    # Use the SAME rule as the rewriter: the marker at LINE START. Finding the first substring
+    # anywhere lets a prose mention of the marker truncate discovery before the real block.
+    mk = re.search(r'^<!-- BEGIN footprint-reqstate', text, re.M)
+    authoritative = text if mk is None else text[:mk.start()]
     ids = []
     for line in authoritative.split('\n'):
         m = re.match(r'^\|\s*(' + idPattern + r')\s*\|', line)
         if m and m.group(1) not in ids: ids.append(m.group(1))
     per = collections.defaultdict(list)
     keys = collections.defaultdict(list)
     for k, v in rec.items():
         _rid = v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', k).group(1)
         per[_rid].append(v)
         keys[_rid].append(k)
     # A record is only allowed to say "pinned" when it carries the evidence for it: an assertion
     # failure naming a test, and the verbatim failure lines that name was parsed from. A record that
     # merely says KILLED is a claim, not evidence — and one kill in this campaign turned out to be a
     # format-string error under the right test's name, which is exactly what this refuses.
     problems = []
     for rid, ms in per.items():
         for m in ms:
             if m['status'] != 'KILLED':
                 continue
-            if not m.get('assertionFailures') and not m.get('killedByThrow'):
-                problems.append(f"{rid}: a KILLED record names no ASSERTION failure and declares no "
-                                f"matched throw ({m.get('killedBy')})")
+            if not m.get('assertionFailures'):
+                # The renderer must hold this itself: a record is a checked-in FILE, and a hand-edited
+                # or legacy one would otherwise be taken at its word.
+                t = m.get('killedByThrow')
+                lines = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
+                ok = (isinstance(t, dict) and t.get('test') and t.get('throws')
+                      and t['test'].split('.')[-1] in lines and t['throws'] in lines)
+                if not ok:
+                    problems.append(f"{rid}: a KILLED record names no ASSERTION failure, and its "
+                                    f"killedByThrow does not name a test and a throw that its own "
+                                    f"failure lines carry ({m.get('killedBy')})")
             if not m.get('evidence', {}).get('failureLines'):
                 problems.append(f"{rid}: a KILLED record carries no failure lines")
             if not m.get('evidence', {}).get('treeRestoredClean', True):
                 problems.append(f"{rid}: a KILLED record ran against a tree it did not restore")
     if problems:
         print("REFUSING TO EMIT:", file=sys.stderr)
         for p in problems: print("  ", p, file=sys.stderr)
         sys.exit(1)
 
     def gate(rid):
         for pat, g in gates.items():
             if re.fullmatch('(?:' + pat + ')', rid): return g
         return '—'
     def disp(rid):
         for pat, d in dispositions.items():
             if re.fullmatch('(?:' + pat + ')', rid): return d
         return ''
     out = ["| id | Conformance | Gate | Disposition |", "|----|-------------|------|-------------|"]
     for rid in sorted(ids, key=lambda x: (re.sub(r'\d', '', x), int(re.search(r'\d+', x).group()), x)):
         ms = per.get(rid, [])
         if not ms:
             # A note is EDITORIAL: it says why this requirement has no mutations, and is marked as
             # such so it can never be read as a campaign result.
             state = (notes[rid] + " (not probed)") if rid in notes else "NOT PROBED"
         else:
             by = collections.Counter(m['status'] for m in ms)
             killed = by.pop('KILLED', 0)
             # Count PROBES, not clauses: a clause implemented at several sites is probed once per
             # site AND once for all sites together, so "n of m clauses" would triple-count it.
             groups = len({re.sub(r' site\d+$', '', k) for k in keys[rid]})
             state = f"{killed} of {len(ms)} probes pinned, over {groups} clause" + ("" if groups == 1 else "s")
             rest = ", ".join(f"{n} {st.lower()}" for st, n in sorted(by.items()))
             if rest: state += f" ({rest})"
         out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
-    tot = collections.Counter(v['status'] for v in rec.values())
+    # Count only what the table shows. A record for a requirement the document no longer
+    # renders is omitted from the rows, and must be omitted from the totals with it.
+    rendered = [v for rid in ids for v in per.get(rid, [])]
+    tot = collections.Counter(v['status'] for v in rendered)
     out.append("")
     probed_here = len([r for r in ids if r in per])
-    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rec)} mutations "
+    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rendered)} mutations "
                f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving"
                + (", " + ", ".join(f"{n} {st.lower()}" for st, n in sorted(tot.items()) if st not in ('KILLED', 'SURVIVED')) if len(tot) > 2 else "")
                + ")."
                "\n\nRead the state column narrowly. \"n of m probes pinned\" says that breaking those "
                "clauses in the production source made a NAMED test fail an ASSERTION (or propagate a "
                "throw the spec declared and the run matched) — "
                "it does NOT say the requirement as a whole is held, because a requirement usually has "
                "more clauses than this campaign broke. \"NOT PROBED\" means this campaign did not test "
                "it and claims nothing either way; where a note appears beside it, that note is "
                "editorial and is not a campaign result. Evidence, per mutation — the patch, the file, "
                "line and enclosing declaration, the command, the exit code, the verbatim failure "
                "lines and a SHA-256 of the run output — is in the campaign record beside this "
                "document, and this table refuses to render a \"pinned\" cell for any record that does "
                "not carry it.")
     print("\n".join(out))
 
 main()

2026-09-08T21:03:41.944516Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "nl -ba scripts/footprint-reqstate.py | sed -n '28,67p'; nl -ba ES-FOOTPRINT-CAMPAIGN.json | sed -n '190,250p;1660,1775p;1868,1930p'; nl -ba ES-FOOTPRINT-GATEWAY-DESIGN.md | sed -n '152,192p'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
    28	        if m and m.group(1) not in ids: ids.append(m.group(1))
    29	    per = collections.defaultdict(list)
    30	    keys = collections.defaultdict(list)
    31	    for k, v in rec.items():
    32	        _rid = v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', k).group(1)
    33	        per[_rid].append(v)
    34	        keys[_rid].append(k)
    35	    # A record is only allowed to say "pinned" when it carries the evidence for it: an assertion
    36	    # failure naming a test, and the verbatim failure lines that name was parsed from. A record that
    37	    # merely says KILLED is a claim, not evidence — and one kill in this campaign turned out to be a
    38	    # format-string error under the right test's name, which is exactly what this refuses.
    39	    problems = []
    40	    for rid, ms in per.items():
    41	        for m in ms:
    42	            if m['status'] != 'KILLED':
    43	                continue
    44	            if not m.get('assertionFailures'):
    45	                # The renderer must hold this itself: a record is a checked-in FILE, and a hand-edited
    46	                # or legacy one would otherwise be taken at its word.
    47	                t = m.get('killedByThrow')
    48	                lines = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
    49	                ok = (isinstance(t, dict) and t.get('test') and t.get('throws')
    50	                      and t['test'].split('.')[-1] in lines and t['throws'] in lines)
    51	                if not ok:
    52	                    problems.append(f"{rid}: a KILLED record names no ASSERTION failure, and its "
    53	                                    f"killedByThrow does not name a test and a throw that its own "
    54	                                    f"failure lines carry ({m.get('killedBy')})")
    55	            if not m.get('evidence', {}).get('failureLines'):
    56	                problems.append(f"{rid}: a KILLED record carries no failure lines")
    57	            if not m.get('evidence', {}).get('treeRestoredClean', True):
    58	                problems.append(f"{rid}: a KILLED record ran against a tree it did not restore")
    59	    if problems:
    60	        print("REFUSING TO EMIT:", file=sys.stderr)
    61	        for p in problems: print("  ", p, file=sys.stderr)
    62	        sys.exit(1)
    63	
    64	    def gate(rid):
    65	        for pat, g in gates.items():
    66	            if re.fullmatch('(?:' + pat + ')', rid): return g
    67	        return '—'
   190	   "returnCode": 0,
   191	   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
   192	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   193	  }
   194	 },
   195	 "G-R4 rollover-counted": {
   196	  "status": "KILLED",
   197	  "clause": "a rollover is counted",
   198	  "requirement": "G-R4",
   199	  "documentText": "Rollover is MONOTONIC on the parsed date: NEWER than current \u21d2 BOTH views are cleared and the date advances;",
   200	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
   201	  "occurrence": 1,
   202	  "occurrencesInFile": 1,
   203	  "line": 150,
   204	  "enclosing": "private Boolean rollover(LocalDate date) {",
   205	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   206	  "killedBy": [
   207	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   208	  ],
   209	  "assertionFailures": [
   210	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   211	  ],
   212	  "anyFailures": [
   213	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   214	  ],
   215	  "killedByThrow": null,
   216	  "seconds": 9.9,
   217	  "patch": {
   218	   "old": "        rollovers.incrementAndGet();",
   219	   "new": "        if (false) rollovers.incrementAndGet();"
   220	  },
   221	  "evidence": {
   222	   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
   223	   "returnCode": 1,
   224	   "mutatedFileSha256": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5",
   225	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   226	   "filesSha256": {
   227	    "src/main/java/app/feedgateway/FootprintViews.java": {
   228	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   229	     "mutated": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5"
   230	    }
   231	   },
   232	   "outputSha256": "3f7d49f6a8db6e15b2dd03a4f0ef59108140e60e091bedc34ed0434a62a5702a",
   233	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   234	   "failureLines": [
   235	    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.006 s <<< FAILURE!",
   236	    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>"
   237	   ],
   238	   "treeRestoredClean": true
   239	  },
   240	  "baseline": {
   241	   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
   242	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   243	   "returnCode": 0,
   244	   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
   245	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   246	  }
   247	 },
   248	 "G-R8 evict-oldest": {
   249	  "status": "KILLED",
   250	  "clause": "evict the OLDEST key of the timeframe",
  1660	   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
  1661	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1662	   "returnCode": 0,
  1663	   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
  1664	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1665	  }
  1666	 },
  1667	 "G-R6.2 the-hello-is-sent-when-footprint-alone-is-on": {
  1668	  "status": "KILLED",
  1669	  "clause": "cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled",
  1670	  "requirement": "G-R6",
  1671	  "documentText": "`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.",
  1672	  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
  1673	  "occurrence": 1,
  1674	  "occurrencesInFile": 1,
  1675	  "line": 11158,
  1676	  "enclosing": "boolean sendsCvdHello() {",
  1677	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1678	  "killedBy": [
  1679	   "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
  1680	  ],
  1681	  "assertionFailures": [
  1682	   "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
  1683	  ],
  1684	  "anyFailures": [
  1685	   "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
  1686	  ],
  1687	  "killedByThrow": null,
  1688	  "seconds": 10.0,
  1689	  "patch": {
  1690	   "old": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;",
  1691	   "new": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();"
  1692	  },
  1693	  "evidence": {
  1694	   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
  1695	   "returnCode": 1,
  1696	   "mutatedFileSha256": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc",
  1697	   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1698	   "filesSha256": {
  1699	    "src/main/java/app/feedgateway/FeedGatewayService.java": {
  1700	     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1701	     "mutated": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc"
  1702	    }
  1703	   },
  1704	   "outputSha256": "da516b2828cb21ac518bada8d5eac3a01a94a9f833dcf5397b242ae3de7b455a",
  1705	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
  1706	   "failureLines": [
  1707	    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.003 s <<< FAILURE!",
  1708	    "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>"
  1709	   ],
  1710	   "treeRestoredClean": true
  1711	  },
  1712	  "baseline": {
  1713	   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
  1714	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1715	   "returnCode": 0,
  1716	   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
  1717	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1718	  }
  1719	 },
  1720	 "G-R6.3 the-hello-carries-the-outcome-high-water-mark": {
  1721	  "status": "KILLED",
  1722	  "clause": "outcomeHwm:{tf: maxResolvedAtBarStartMs}",
  1723	  "requirement": "G-R6",
  1724	  "documentText": "**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).",
  1725	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
  1726	  "occurrence": 1,
  1727	  "occurrencesInFile": 1,
  1728	  "line": 195,
  1729	  "enclosing": "StringBuilder sb = new StringBuilder(\"{\\\"sessionDate\\\":\");",
  1730	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1731	  "killedBy": [
  1732	   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
  1733	   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
  1734	  ],
  1735	  "assertionFailures": [
  1736	   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
  1737	   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
  1738	  ],
  1739	  "anyFailures": [
  1740	   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
  1741	   "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
  1742	  ],
  1743	  "killedByThrow": null,
  1744	  "seconds": 9.8,
  1745	  "patch": {
  1746	   "old": "            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, outcomes, true);",
  1747	   "new": "            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, bars, true);"
  1748	  },
  1749	  "evidence": {
  1750	   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
  1751	   "returnCode": 1,
  1752	   "mutatedFileSha256": "368f116e662d3150e861190653954f413fbe2ce71254dec4501542935a35c416",
  1753	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
  1754	   "filesSha256": {
  1755	    "src/main/java/app/feedgateway/FootprintViews.java": {
  1756	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
  1757	     "mutated": "368f116e662d3150e861190653954f413fbe2ce71254dec4501542935a35c416"
  1758	    }
  1759	   },
  1760	   "outputSha256": "95a01fc29841ba76302b3e2d410c1950cabbb24c9556262307f2e928270580d2",
  1761	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
  1762	   "failureLines": [
  1763	    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.003 s <<< FAILURE!",
  1764	    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< FAILURE!",
  1765	    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"30s\":2000,\"1m\":5}}>",
  1766	    "[ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>"
  1767	   ],
  1768	   "treeRestoredClean": true
  1769	  },
  1770	  "baseline": {
  1771	   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
  1772	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1773	   "returnCode": 0,
  1774	   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
  1775	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1868	   "outputSha256": "004035b0e09fd4deda345254b7f496efd6402285905a882f375a7ac2aed1b1bf",
  1869	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
  1870	   "failureLines": [
  1871	    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.248 s <<< FAILURE!",
  1872	    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>"
  1873	   ],
  1874	   "treeRestoredClean": true
  1875	  },
  1876	  "baseline": {
  1877	   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
  1878	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1879	   "returnCode": 0,
  1880	   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
  1881	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1882	  }
  1883	 },
  1884	 "G-R9.2 every-record-is-counted-before-admission": {
  1885	  "status": "KILLED",
  1886	  "clause": "records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too",
  1887	  "requirement": "G-R9",
  1888	  "documentText": "`gateway_footprint_records_total{event,consumer}` \u2014 every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).",
  1889	  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
  1890	  "occurrence": 1,
  1891	  "occurrencesInFile": 1,
  1892	  "line": 897,
  1893	  "enclosing": "footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();",
  1894	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1895	  "killedBy": [
  1896	   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
  1897	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
  1898	  ],
  1899	  "assertionFailures": [
  1900	   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
  1901	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
  1902	  ],
  1903	  "anyFailures": [
  1904	   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
  1905	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
  1906	  ],
  1907	  "killedByThrow": null,
  1908	  "seconds": 10.9,
  1909	  "patch": {
  1910	   "old": "        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {",
  1911	   "new": "        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission\n        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {"
  1912	  },
  1913	  "evidence": {
  1914	   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
  1915	   "returnCode": 1,
  1916	   "mutatedFileSha256": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e",
  1917	   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1918	   "filesSha256": {
  1919	    "src/main/java/app/feedgateway/FeedGatewayService.java": {
  1920	     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1921	     "mutated": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e"
  1922	    }
  1923	   },
  1924	   "outputSha256": "4bb2899583fc11e9cf410f45b8a0d6cc72b64e76b033ba79409f50c2c68d48c8",
  1925	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
  1926	   "failureLines": [
  1927	    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.204 s <<< FAILURE!",
  1928	    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.017 s <<< FAILURE!",
  1929	    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false>",
  1930	    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>"
   152	| G-R2 | **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`). ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`. |
   153	| G-R3 | **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`). `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt). All four events are added to the per-session standalone allowlist (D5). |
   154	| G-R4 | **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock. Admission of any bar/outcome record: parse JSON; `sessionDate` must be present and parse with `LocalDate.parse(s, ISO_LOCAL_DATE)` AND round-trip to the same string (strict canonical `YYYY-MM-DD`); `timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates). Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances; OLDER ⇒ `stale_session` drop; EQUAL (or first) ⇒ upsert (last write per key wins). Because one coordinator rolls both views, a bar from session N+1 arriving before any outcome of N+1 clears N's outcomes with N's bars, and an outcome of N arriving after that is dropped — whichever topic the cache consumer drains first. |
   155	| G-R5 | **Outcome view.** `outcomes: TreeMap<tf\|%019d(resolvedAtBarStartMs)\|identity, json>` in the SAME coordinator. The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain; `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component). Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast. |
   156	| G-R6 | **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held). It rides the SAME frame so the page has one handshake; `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled. Flag off, the field is absent (its ABSENCE tells the page the gateway has no footprint). |
   157	| G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's). `GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` ascending by view key within `tf`, `toMs` inclusive on `resolvedAtBarStartMs`, `after` = an EXCLUSIVE lower bound given as a previous page's `nextCursor` (the last key string). Cursor grammar: `tf|<19 digits>|<identity>` where `tf` equals the query `tf`; an `after` that is non-empty and fails the grammar ⇒ HTTP 400 `{"error":"bad cursor"}`; a well-formed cursor that no longer exists is legal (string lower bound). `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from. Same JWT gate as every `/api` route. **Processing order (exactly one outcome per request, in the AS-IS convention):** (1) Spring typed binding of `toMs`/`limit`/`afterMs` ⇒ 400 on malformed input BEFORE the handler runs (so a malformed request is never counted and never sees the flag or auth); (2) in the handler, flag off ⇒ 404 `{"enabled":false}`; (3) explicit `LiquidityHistoryAuth.authenticate(...)` exactly as `LiquidityHistoryController` does (`:86`) ⇒ its 401/403 on failure; (4) permit: `tryAcquire` on a `Semaphore(GATEWAY_ES_FOOTPRINT_BACKFILL_CONCURRENCY)` (default 4) ⇒ 503 `Retry-After: 1` (`busy`) when none is free; (5) cursor grammar (outcomes route) ⇒ 400 `{"error":"bad cursor"}`; (6) page snapshot under the coordinator lock — the session check (`sessionMismatch`, HTTP 200) and the selection of up to `limit` record REFERENCES into a `List<String>` (no copies) plus `sessionDate` and `nextCursor`; (7) the response is STREAMED: the controller writes to `HttpServletResponse.getOutputStream()` through ONE fixed 64 KiB buffer, the envelope pieces and each record as `record.getBytes(US_ASCII)` one at a time (records are ASCII by F-E8), status 200 set before the first byte; the permit is released in `finally` after the stream is flushed (or the write failed). The lock is held only during (6), never during (7). Transient memory per request is therefore ≤ one record's bytes (≤ 262 144 B) + the 64 KiB buffer + the reference list (≤ 100 × 4 B) < 0.32 MiB (G-R8). |
   158	| G-R8 | **Bounds and heap (proven from the limits, never from averages).** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key. **Retained-heap bound** (HotSpot, compressed oops and compressed class pointers — true for any heap < 32 GiB, as here): a JSON value is ASCII (F-E8 alphabet) so it is a Latin-1 compact `String`: 24 B object + 16 B array header + payload, aligned to 8 ⇒ ≤ payload + 48 B; a key `String` ≤ 160 B payload ⇒ ≤ 208 B; a `TreeMap.Entry` = 40 B; per entry overhead ≤ 296 B, so overhead ≤ (12 000 + 20 000) × 296 B = 9.04 MiB; payload ≤ 128 + 16 = 144 MiB; the coordinator's accounting (two `long` counters, one `LocalDate`, per-timeframe eviction cursors) < 1 KiB. Views total ≤ 153.1 MiB. **Transient** per in-flight backfill (G-R7 step 7): < 0.32 MiB, at most 4 concurrent ⇒ < 1.3 MiB; no `StringBuilder`, no response `String`, no encoder buffer beyond the fixed 64 KiB. **Kafka transient**, from the client's fetch mechanics and THREE enforced facts. Mechanics (Kafka consumer `Fetcher`, unchanged since KIP-74): the consumer keeps at most ONE in-flight fetch request per broker node, a fetch request for a node includes only assigned partitions that have NO buffered (completed, unconsumed) data, each partition's share of a response is ≤ `max.partition.fetch.bytes` except that the FIRST batch of a partition is returned whole when it is larger, and `max.poll.records` only paces how many buffered records a `poll()` hands out. Enforced facts: (i) `max.partition.fetch.bytes` = 512 KiB (§1); (ii) `M` = each footprint topic's effective `max.message.bytes` ≤ `GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588, the broker default), validated per G-R8a; (iii) the four topics are UNCOMPRESSED end to end — the upstream producer sets `compression.type=none` explicitly (options-edge-processing amendment: `EsCvdRuntime.producerProps`, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` per G-R8a — so a fetched share IS its decompressed size and no decompression buffer exists. Per consumer, each of the four single-partition footprint topics contributes at most: one in-flight share (≤ max(512 KiB, M) ≤ 1.0 MiB) + one buffered completed share (≤ 1.0 MiB) + the deserialized value `String`s of the records a `poll()` returned from that share (≤ the share's bytes + 48 B per record) ⇒ ≤ 3.0 MiB per topic + per-record overhead, ≤ 12.0 MiB per consumer + per-record overhead. Per-record client overhead for the records of ONE poll — here, and only here, bounded by `max.poll.records` = 100 (§1): key `String` ≤ 208 B, `ConsumerRecord` object ≤ 96 B (header, 7 references, two longs, two ints), empty `RecordHeaders` ≤ 32 B, `ConsumerRecords` map/list share ≤ 64 B ⇒ ≤ 400 B × 100 = 40 KiB. Per consumer ≤ 12.04 MiB; TWO consumers touch footprint topics (state cache, state live) ⇒ ≤ 24.1 MiB, rounded to 25 MiB. Non-footprint partitions in the same fetch responses are not attributable to this feature and are already part of the measured `H_peak`. **Layout assumptions are verified at start-up** when the flag is on: `HotSpotDiagnosticMXBean.getVMOption` for `UseCompressedOops`, `UseCompressedClassPointers` and `CompactStrings` must all be `true`, else the gateway refuses to start naming the flag (the arithmetic above is void otherwise); pinned by a test of the checker over a fake option map. Topic-side facts (ii) and (iii) are verified by G-R8a. **Footprint heap contribution ≤ 153.1 + 1.3 + 25 = 179.4 MiB, rounded to 180 MiB**, independent of record size distribution. **Deployment contingency (§4):** the flag may be enabled only when the deploy PR records, from a full ES session with CVD on and footprint off: (a) heap: `jvm_memory_bytes_used{area="heap"}` peak `H_peak` with `-Xmx − H_peak ≥ 180 MiB + 128 MiB = 308 MiB` (GC headroom); (b) container: `container_memory_working_set_bytes` peak `W_peak` with `limit − W_peak ≥ 180 MiB + 256 MiB = 436 MiB`, AND `limit − Xmx ≥ 512 MiB` (metaspace, code cache, thread stacks, socket/direct buffers, JVM overhead — the current es4 manifest has `limit = Xmx = 1536 MiB`, which violates this and MUST change in the same PR: e.g. `-Xmx1536m` with `limit 2560Mi`, or a measured alternative that satisfies both inequalities). Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits and the tests pin them. |
   159	| G-R8a | **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`. **Start-up** (`start()`, before any consumer launches): validate the topics that EXIST; an existing INVALID topic ⇒ refuse to start naming topic and value (fail-closed); an absent topic (`UnknownTopicOrPartitionException` for that resource) ⇒ deferred; any other Admin failure ⇒ refuse to start. **The seam (an API change to `PartitionRefresh`, the AS-IS contract otherwise kept):** `PartitionRefresh` gains a constructor parameter `Predicate<String> topicAdmit`: of the SIX existing constructor call sites (`:1739` selection, `:2087` hpsf-cache, `:2125` hpsf-live, `:2201` alerts, `:2303` state cache, `:2674` state live), the two STATE consumers pass `footprintGate::admit` and the other FOUR pass `t -> true`. Inside `apply()`, AFTER `partitionsFor` discovers new partitions and BEFORE `mergedAssignment`/`consumer.assign(...)` (`FeedGatewayService.java:3665-3671`), discovered partitions whose topic fails `topicAdmit` are WITHHELD: they are not merged, not assigned, not in `Refresh.added()`, hence never sought and never in the caller's caught-up bookkeeping — identical to a topic that does not exist yet; they are re-discovered (and re-evaluated) at the next refresh interval. The same predicate is applied to the bootstrap `partitionsFor` result before the initial `assign` in both state consumers. `addEsFootprintTopics` adds ALL FOUR topics to both consumers' `topicEvents` unconditionally, so the immutable `Set.copyOf(topics)` always contains them and discovery works whenever a topic appears. **`FootprintTopicGate.admit(topic)`** runs on the calling poll thread: a non-footprint topic ⇒ true; a footprint topic already marked VALID ⇒ true; otherwise it validates NOW (bounded timeout): VALID ⇒ mark and true; unknown topic, timeout or other Admin failure ⇒ false this cycle (counted, logged once per state change, retried at the next refresh — the absorbed-refresh-failure discipline); existing-but-invalid ⇒ false (counted `ceiling`/`compression`, logged once per state change). Per-topic state in a `ConcurrentHashMap` shared by both consumers (each evaluates on its own thread; a duplicate validation is harmless); once VALID a topic stays VALID for the incarnation (a topic config change after admission requires a gateway restart — §4 operational rule). Adoption of a late-admitted topic then follows the existing caller behaviour for `Refresh.added()`: the cache consumer seeks its window (a compacted keyed topic replays its retained keys into the coordinator, as `es-cvd-bar` does), the live consumer seeks END. The two validation series are defined in G-R9. |
   160	| G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
   161	| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
   162	| G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |
   163	
   164	<!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->
   165	
   166	## 2a. Conformance — what a test actually holds
   167	
   168	Every requirement above, and what this repository's mutation campaign established about it. The
   169	campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
   170	failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
   171	the spec declared in advance — never an incidental error under the right test's name; and the table
   172	below refuses to render a "pinned" cell for any record that does not carry its evidence. That
   173	evidence — the patch, the file, line and enclosing declaration, the command, the exit code, the
   174	verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
   175	this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
   176	record with `scripts/footprint-reqstate.sh --check`.
   177	
   178	| id | Conformance | Gate | Disposition |
   179	|----|-------------|------|-------------|
   180	| G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
   181	| G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
   182	| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
   183	| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
   184	| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
   185	| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
   186	| G-R7 | 5 of 6 probes pinned, over 6 clauses (1 survived) | 2 | Backfill routes |
   187	| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
   188	| G-R9 | 2 of 2 probes pinned, over 2 clauses | 2 | Metrics |
   189	| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
   190	| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
   191	| G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | Deployment contingency |
   192	

2026-09-08T21:03:51.512513Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
- HIGH — [ES-FOOTPRINT-CAMPAIGN.json:195](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:195) still assigns “a rollover is counted” to G-R4. G-R4 specifies rollover behavior but not its metric; the counting obligation appears in G-R9 at [ES-FOOTPRINT-GATEWAY-DESIGN.md:160](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:160). Consequently §2a overcounts G-R4 and undercounts G-R9. This is the same class of requirement transposition corrected for `records_total`.

- MEDIUM — Round 3 item 3 is not implemented as stated. [scripts/footprint-reqstate.py:49](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:49) checks only `t['test'].split('.')[-1]`, not the complete `test` string, against `failureLines`. A record whose declared fully qualified test does not appear can therefore pass. Additionally, assertion-backed records need only have a nonempty `assertionFailures` array and unrelated nonempty `failureLines`; their names are never correlated.

- MEDIUM — The three G-R7 epoch-guard records claim clauses that G-R7 does not state: `epoch-guard`, `epoch-guard-removed`, and `epoch-guard-relaxed`. Their `documentText` is a verbatim but unrelated general backfill passage. The relaxed mutation is explicitly inert, yet §2a counts all three as separate G-R7 “clauses,” producing the overclaim “5 of 6 probes … over 6 clauses” at [ES-FOOTPRINT-GATEWAY-DESIGN.md:186](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:186).

- MEDIUM — Although every record’s `documentText` is a verbatim substring of its assigned requirement, several quotations do not quote the recorded obligation. Besides the cases above, G-R4 `older-date-dropped` and `equal-date-upserts` quote only the coordinator-ownership sentence. The §2a renderer never validates `documentText` at all; it groups records solely by their editable `requirement` field at [scripts/footprint-reqstate.py:31](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:31). Thus the generated table does not hold the attribution invariant requested here.

The G-R9.2 reassignment, narrowed G-R3 shape/session wording, rendered-record-only totals, and line-start marker alignment are otherwise present. The regeneration check could not execute in this read-only review environment because `mktemp` was denied.

VERDICT: REQUEST_CHANGES
tokens used
83.026
- HIGH — [ES-FOOTPRINT-CAMPAIGN.json:195](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:195) still assigns “a rollover is counted” to G-R4. G-R4 specifies rollover behavior but not its metric; the counting obligation appears in G-R9 at [ES-FOOTPRINT-GATEWAY-DESIGN.md:160](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:160). Consequently §2a overcounts G-R4 and undercounts G-R9. This is the same class of requirement transposition corrected for `records_total`.

- MEDIUM — Round 3 item 3 is not implemented as stated. [scripts/footprint-reqstate.py:49](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:49) checks only `t['test'].split('.')[-1]`, not the complete `test` string, against `failureLines`. A record whose declared fully qualified test does not appear can therefore pass. Additionally, assertion-backed records need only have a nonempty `assertionFailures` array and unrelated nonempty `failureLines`; their names are never correlated.

- MEDIUM — The three G-R7 epoch-guard records claim clauses that G-R7 does not state: `epoch-guard`, `epoch-guard-removed`, and `epoch-guard-relaxed`. Their `documentText` is a verbatim but unrelated general backfill passage. The relaxed mutation is explicitly inert, yet §2a counts all three as separate G-R7 “clauses,” producing the overclaim “5 of 6 probes … over 6 clauses” at [ES-FOOTPRINT-GATEWAY-DESIGN.md:186](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:186).

- MEDIUM — Although every record’s `documentText` is a verbatim substring of its assigned requirement, several quotations do not quote the recorded obligation. Besides the cases above, G-R4 `older-date-dropped` and `equal-date-upserts` quote only the coordinator-ownership sentence. The §2a renderer never validates `documentText` at all; it groups records solely by their editable `requirement` field at [scripts/footprint-reqstate.py:31](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:31). Thus the generated table does not hold the attribution invariant requested here.

The G-R9.2 reassignment, narrowed G-R3 shape/session wording, rendered-record-only totals, and line-start marker alignment are otherwise present. The regeneration check could not execute in this read-only review environment because `mktemp` was denied.

VERDICT: REQUEST_CHANGES
