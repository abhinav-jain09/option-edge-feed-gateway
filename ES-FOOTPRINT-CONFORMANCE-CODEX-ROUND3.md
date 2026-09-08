Reading additional input from stdin...
2026-09-08T20:50:44.415711Z ERROR codex_models_manager::manager: failed to load models cache: missing field `supports_parallel_tool_calls` at line 93 column 5
OpenAI Codex v0.147.0
--------
workdir: /private/tmp/oe-conf-gw
model: gpt-5.6-sol
provider: openai
approval: never
sandbox: read-only
reasoning effort: low
reasoning summaries: none
session id: 01a082c9-9810-7aa0-9beb-06788d3bd065
--------
user
Round 3 of the review of option-edge-feed-gateway PR #174 at /private/tmp/oe-conf-gw, branch
test/footprint-conformance-pins. Round 2 gave two HIGH and three MEDIUM findings.

What was done — VERIFY EACH against the files and the record, do not take this as evidence:
 1. HIGH "§2a still overclaims assertion-backed pins (G-R10.1 has no assertionFailures)" — rather
    than special-case it, the TEST now states the clause directly with assertDoesNotThrow, so the
    mutation produces an ASSERTION failure like every other record. The generator's check now
    requires assertionFailures itself (the killedBy fallback is gone).
 2. HIGH "killedByThrow can launder incidental errors" — it now takes {test, throws} and BOTH must
    appear in the run's own failure lines. Nothing in this campaign uses it any more.
 3. MEDIUM "G-R10.3 is not killed for the claimed ordering reason" — the mutation now keeps the
    counter on the keyed path but moves it AFTER admission and returns early on OVERSIZE, which is
    exactly the case the clause names ("so it counts oversize records too").
 4. MEDIUM "generation is self-referential" — requirement ids are now read only from the text BEFORE
    the BEGIN marker, and the summary counts only requirements present in the rendered document.
 5. MEDIUM "the rewrite does not establish one well-formed marker block" — exactly one BEGIN and one
    END are now required, each at line start, BEGIN before END. The inaccurate comment about where
    the prose lives is corrected.
 Also: the column said "clauses" while counting mutation records; it now reads
 "n of m probes pinned, over k clauses".

Then review the WHOLE change again line by line and say what, if anything, still overclaims or is
unheld. Read ES-FOOTPRINT-CAMPAIGN.json's failureLines for the records you doubt.

End with exactly one line: VERDICT: APPROVE  or  VERDICT: REQUEST_CHANGES
codex
I’ll verify the claimed Round 2 fixes against the diff, generator logic, campaign record, and tests, then re-review the entire PR change for any remaining unsupported claims.
exec
/bin/bash -lc 'git status --short && git branch --show-current && git diff --stat && git diff --name-only && git log --oneline -5' in /private/tmp/oe-conf-gw
exec
/bin/bash -lc "git log --oneline --decorate --all -15; git branch -a; git status --porcelain=v1; rg --files | rg 'ES-FOOTPRINT|footprint|campaign|generate'" in /private/tmp/oe-conf-gw
 succeeded in 14179ms:
2026-09-08 22:50:51.134 xcodebuild[97446:266259529]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:50:51.685 xcodebuild[97446:266259526] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-rBXbwrCb' (errno=Operation not permitted)
2026-09-08 22:50:52.432 xcodebuild[97484:266259784]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:50:52.687 xcodebuild[97484:266259782] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-jZUknm9n' (errno=Operation not permitted)
2026-09-08 22:50:54.840 xcodebuild[97529:266260054]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:50:55.180 xcodebuild[97529:266260050] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-TjbJaSbS' (errno=Operation not permitted)
2026-09-08 22:50:56.067 xcodebuild[97554:266260215]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:50:56.328 xcodebuild[97554:266260208] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
test/footprint-conformance-pins
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-1WmLmfL6' (errno=Operation not permitted)
2026-09-08 22:50:57.855 xcodebuild[97610:266260524]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:50:58.060 xcodebuild[97610:266260518] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-0Nz8IP11' (errno=Operation not permitted)
2026-09-08 22:50:58.614 xcodebuild[97635:266260663]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:50:58.792 xcodebuild[97635:266260654] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-sCWx1iBO' (errno=Operation not permitted)
2026-09-08 22:51:00.098 xcodebuild[97699:266260965]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:00.342 xcodebuild[97699:266260951] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-VYMqSTSH' (errno=Operation not permitted)
2026-09-08 22:51:00.935 xcodebuild[97727:266261120]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:01.171 xcodebuild[97727:266261113] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-mHiMH4Vc' (errno=Operation not permitted)
2026-09-08 22:51:02.550 xcodebuild[97794:266261454]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:02.794 xcodebuild[97794:266261446] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-szB2vPoA' (errno=Operation not permitted)
2026-09-08 22:51:03.343 xcodebuild[97826:266261625]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:03.559 xcodebuild[97826:266261613] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
8bbcdab fix(footprint tooling): a kill must name an assertion, a throw-kill must name its throw, and the table counts probes
928983e test(gateway footprint): state the no-throw clause directly, not through a propagated throw
81202f4 wip: harness+generator round-2 folds
23ff325 docs(gateway footprint): §2a is regenerated in place, and says only what the record carries
0ecf5f7 wip: drop tainted records

 succeeded in 7788ms:
2026-09-08 22:51:03.893 xcodebuild[97851:266261751]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:04.142 xcodebuild[97851:266261750] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-jKvYnr7x' (errno=Operation not permitted)
2026-09-08 22:51:04.781 xcodebuild[97889:266261930]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:05.033 xcodebuild[97889:266261928] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
8bbcdab (HEAD -> test/footprint-conformance-pins, origin/test/footprint-conformance-pins) fix(footprint tooling): a kill must name an assertion, a throw-kill must name its throw, and the table counts probes
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
a181871 (origin/feat/direction-relay, feat/direction-relay) A5.7: PROD and DEV progress reports must never share a coalescing key
fc49c14 Merge pull request #171 from abhinav-jain09/docs/footprint-campaign-record
ac38b5c (origin/docs/footprint-campaign-record, docs/footprint-campaign-record) docs(gateway): the footprint mutation record, with the evidence to audit it
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-UyjwQex6' (errno=Operation not permitted)
2026-09-08 22:51:06.576 xcodebuild[97960:266262321]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:06.761 xcodebuild[97960:266262320] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-6OGuEADg' (errno=Operation not permitted)
2026-09-08 22:51:07.400 xcodebuild[97986:266262491]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:07.601 xcodebuild[97986:266262487] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
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
git: error: couldn't create cache file '/tmp/xcrun_db-D0RJ9Zsm' (errno=Operation not permitted)
2026-09-08 22:51:09.174 xcodebuild[98048:266262805]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:09.373 xcodebuild[98048:266262801] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-ewig4haw' (errno=Operation not permitted)
2026-09-08 22:51:10.034 xcodebuild[98083:266262941]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:10.218 xcodebuild[98083:266262939] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND4.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND9.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md
ES-FOOTPRINT-CAMPAIGN.json
ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-DESIGN.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md
scripts/footprint-reqstate.sh
scripts/footprint-reqstate.preamble
scripts/footprint-mutate.py
scripts/footprint-conformance.py
scripts/footprint-reqstate.py

exec
/bin/bash -lc 'git diff --stat origin/main...HEAD; git diff --name-status origin/main...HEAD; git diff --check origin/main...HEAD' in /private/tmp/oe-conf-gw
 succeeded in 8951ms:
2026-09-08 22:51:14.720 xcodebuild[98206:266263592]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:15.024 xcodebuild[98206:266263589] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-8ed6zp7w' (errno=Operation not permitted)
2026-09-08 22:51:15.723 xcodebuild[98295:266263852]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:15.941 xcodebuild[98295:266263848] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
 ES-FOOTPRINT-CAMPAIGN.json                         | 1574 +++++++++++++++++---
 ES-FOOTPRINT-GATEWAY-DESIGN.md                     |   35 +
 scripts/footprint-mutate.py                        |  151 +-
 scripts/footprint-reqstate.preamble                |    9 +
 scripts/footprint-reqstate.py                      |   99 ++
 scripts/footprint-reqstate.sh                      |   69 +
 .../java/app/feedgateway/FeedGatewayService.java   |   13 +-
 .../app/feedgateway/CvdSpxLevelsWiringTest.java    |    7 +-
 .../java/app/feedgateway/FootprintViewsTest.java   |   25 +
 .../java/app/feedgateway/FootprintWiringTest.java  |   47 +-
 10 files changed, 1808 insertions(+), 221 deletions(-)
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-18LG4IdY' (errno=Operation not permitted)
2026-09-08 22:51:17.356 xcodebuild[98356:266264178]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:17.598 xcodebuild[98356:266264175] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-80lyDLyy' (errno=Operation not permitted)
2026-09-08 22:51:18.208 xcodebuild[98366:266264252]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:18.464 xcodebuild[98366:266264249] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
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
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-B0mAy4Be' (errno=Operation not permitted)
2026-09-08 22:51:19.996 xcodebuild[98404:266264448]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:20.275 xcodebuild[98404:266264447] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-PIMygmIS' (errno=Operation not permitted)
2026-09-08 22:51:20.910 xcodebuild[98412:266264538]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:21.242 xcodebuild[98412:266264537] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.

exec
/bin/bash -lc 'git diff origin/main...HEAD -- scripts/footprint-reqstate.py scripts/footprint-reqstate.sh scripts/footprint-reqstate.preamble scripts/footprint-mutate.py src/main/java/app/feedgateway/FeedGatewayService.java src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java src/test/java/app/feedgateway/FootprintViewsTest.java src/test/java/app/feedgateway/FootprintWiringTest.java ES-FOOTPRINT-GATEWAY-DESIGN.md' in /private/tmp/oe-conf-gw
 succeeded in 2830ms:
2026-09-08 22:51:28.338 xcodebuild[98565:266265214]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:28.663 xcodebuild[98565:266265213] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-0mNZynOP' (errno=Operation not permitted)
2026-09-08 22:51:29.238 xcodebuild[98575:266265264]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:29.518 xcodebuild[98575:266265263] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
diff --git a/ES-FOOTPRINT-GATEWAY-DESIGN.md b/ES-FOOTPRINT-GATEWAY-DESIGN.md
index 315acad..2c6002e 100644
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
+| G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
+| G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
+| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
+| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
+| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
+| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
+| G-R7 | 5 of 6 probes pinned, over 6 clauses (1 survived) | 2 | Backfill routes |
+| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
+| G-R9 | 1 of 1 probes pinned, over 1 clause | 2 | Metrics |
+| G-R10 | 3 of 3 probes pinned, over 3 clauses | 2 | Non-interference |
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
diff --git a/scripts/footprint-mutate.py b/scripts/footprint-mutate.py
index 9e77d4b..21829a8 100644
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
@@ -121,23 +190,71 @@ def main():
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
@@ -145,11 +262,13 @@ def main():
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
index 0000000..6e67037
--- /dev/null
+++ b/scripts/footprint-reqstate.py
@@ -0,0 +1,99 @@
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
+    cut = text.find('<!-- BEGIN footprint-reqstate')
+    authoritative = text if cut < 0 else text[:cut]
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
+            if not m.get('assertionFailures') and not m.get('killedByThrow'):
+                problems.append(f"{rid}: a KILLED record names no ASSERTION failure and declares no "
+                                f"matched throw ({m.get('killedBy')})")
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
+    tot = collections.Counter(v['status'] for v in rec.values())
+    out.append("")
+    probed_here = len([r for r in ids if r in per])
+    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rec)} mutations "
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
index 3b666c5..a8d1eee 100644
--- a/src/test/java/app/feedgateway/FootprintViewsTest.java
+++ b/src/test/java/app/feedgateway/FootprintViewsTest.java
@@ -65,6 +65,31 @@ class FootprintViewsTest {
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
+        // State the clause the way it is written: the call must RETURN. Letting the throw propagate
+        // would also fail this test, but as an ERROR — and an error under the right test's name is
+        // not evidence of anything, since a broken fixture produces the same shape.
+        FootprintViews.Admission bar = assertDoesNotThrow(() -> v.admitBar(null), "a null bar payload must not throw");
+        FootprintViews.Admission out = assertDoesNotThrow(() -> v.admitOutcome(null), "a null outcome payload must not throw");
+        assertEquals(FootprintViews.Reason.SHAPE, bar.reason());
+        assertEquals(FootprintViews.Reason.SHAPE, out.reason());
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

exec
/bin/bash -lc "sed -n '1,280p' scripts/footprint-mutate.py; sed -n '1,240p' scripts/footprint-reqstate.py; cat scripts/footprint-reqstate.preamble; jq 'keys, to_entries[] | {key:.key,status:.value.status,requirement:.value.requirement,patch:.value.patch,killedBy:.value.killedBy,assertionFailures:.value.assertionFailures,killedByThrow:.value.killedByThrow,failureLines:.value.evidence.failureLines}' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 exited 5 in 0ms:
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
    cut = text.find('<!-- BEGIN footprint-reqstate')
    authoritative = text if cut < 0 else text[:cut]
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
            if not m.get('assertionFailures') and not m.get('killedByThrow'):
                problems.append(f"{rid}: a KILLED record names no ASSERTION failure and declares no "
                                f"matched throw ({m.get('killedBy')})")
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
    tot = collections.Counter(v['status'] for v in rec.values())
    out.append("")
    probed_here = len([r for r in ids if r in per])
    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rec)} mutations "
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
Every requirement above, and what this repository's mutation campaign established about it. The
campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
the spec declared in advance — never an incidental error under the right test's name; and the table
below refuses to render a "pinned" cell for any record that does not carry its evidence. That
evidence — the patch, the file, line and enclosing declaration, the command, the exit code, the
verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
record with `scripts/footprint-reqstate.sh --check`.
jq: error (at ES-FOOTPRINT-CAMPAIGN.json:1941): Cannot index array with string "key"

exec
/bin/bash -lc "tail -20 scripts/footprint-mutate.py; jq -r 'to_entries[] | [.key,.value.requirement,.value.status,((.value.assertionFailures // [])|join(\",\")),(.value.killedByThrow // \"\"|tostring),((.value.evidence.failureLines // [])|join(\" || \"))] | @tsv' ES-FOOTPRINT-CAMPAIGN.json; rg -n 'G-R10\\.3|killedByThrow|counter|oversize|BEGIN footprint|END footprint' scripts ES-FOOTPRINT-CAMPAIGN.json ES-FOOTPRINT-GATEWAY-DESIGN.md" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
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
G-R4 older-date-dropped	G-R4	KILLED	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts,FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders,FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules		[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.140 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>
G-R4 newer-date-rolls-both	G-R4	KILLED	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts		[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>
G-R4 equal-date-upserts	G-R4	KILLED	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit,FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic,FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin		[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {"sessionDate":"2026-08-14","bars":[{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"30s","sessionDate":"2026-08-14","observations":{"barStartMs":5000},"tag":"x"}],"nextCursor":null} ==> expected: <true> but was: <false> || [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0> || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{},"outcomeHwm":{"5m":70}}>
G-R4 rollover-counted	G-R4	KILLED	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts		[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>
G-R8 evict-oldest	G-R8	KILLED	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin		[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>
G-R8 evict-rotates	G-R8	KILLED	FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin		[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:245 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>
G-R8 byte-budget-decremented	G-R8	KILLED	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact		[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <3>
G-R8 both-budgets-enforced	G-R8	KILLED	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact		[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <0>
G-R8 evictions-counted	G-R8	KILLED	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin		[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:260 expected: <1> but was: <0> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>
G-R8a ceiling-strict	G-R8a	KILLED	FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts,FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows,FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne,FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions		[ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.004 s <<< ERROR! || [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.003 s <<< ERROR! || [ERROR] app.feedgateway.FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0 s <<< ERROR! || [ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts:68 the validated topic is added; the unvalidated one is WITHHELD from added() ==> expected: <[futures.footprint.bars-0]> but was: <[]> || [ERROR]   FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows:106 expected: <[futures.cvd.bars-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd.bars-0]> || [ERROR]   FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne:99 expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]>
G-R8a codec-allowlist	G-R8a	KILLED	FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted		[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:51 gzip ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.
G-R9 ceiling-precedence	G-R9	KILLED	FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt		[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt:62 expected: <1> but was: <0>
G-R7 to-is-inclusive	G-R7	KILLED	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit,FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim,FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic		[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2> || [ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false> || [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:200 expected: <3> but was: <2>
G-R7 limit-clamped-low	G-R7	KILLED	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit		[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>
G-R7 limit-clamped-high	G-R7	KILLED	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit		[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>
G-R7 epoch-guard	G-R7	KILLED	FootprintViewsTest.shapeDropsNeverMutate		[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {"timeframe":"1m","sessionDate":"2026-08-14","observations":{"barStartMs":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>
G-R7 epoch-guard-removed	G-R7	KILLED	FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain		[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:185 a cursor beyond the domain admits nothing — it must not wrap ==> expected: <0> but was: <4>
G-R7 epoch-guard-relaxed	G-R7	SURVIVED			
G-R1.1 the-flag-gates-every-footprint-object	G-R1	KILLED	FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing,FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose,FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions,FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge		[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.010 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.347 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.013 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.259 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401> || [ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123 || [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]> || [ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true>
G-R2.1 one-wiring-method-binds-all-four	G-R2	KILLED	FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven		[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot
G-R2.2 the-evidence-binding	G-R2	KILLED	FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics,FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven		[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint" ==> expected: <1> but was: <2> || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-
G-R3.1 an-oversize-record-is-dropped-not-broadcast	G-R3	KILLED	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules		[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.149 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.015 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>
G-R3.2 a-rejected-record-is-still-broadcast	G-R3	KILLED	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules		[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.129 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.014 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>
G-R3.3 live-snapshots-are-never-retained	G-R3	KILLED	FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession		[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>
G-R5.1 the-outcome-key-orders-numerically	G-R5	KILLED	FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession,FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth		[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.062 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.002 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.002 s <<< ERROR! || [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8> || [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> || [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "10|ES.v.0|1m|1|BUYE" || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "5|ES.v.0|1m|1|BUYER" || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "77|ES.v.0|5m|1|SELL" || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » StringIndexOutOfBounds Range [3, 22) out of bounds for length 8
G-R5.2 the-identity-is-the-last-component	G-R5	KILLED	FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession,FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth		[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.082 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.002 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR! || [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: "id0|000000000000000"> || [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005> || [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|" || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|" || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "z|00000000000000000" || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|"
G-R5.3 a-foreign-identity-is-refused	G-R5	KILLED	FootprintViewsTest.shapeDropsNeverMutate		[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:40 expected: <SHAPE> but was: <ADMITTED>
G-R5.4 last-write-per-key-wins	G-R5	KILLED	FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace		[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>
G-R6.1 the-hello-carries-the-footprint-field	G-R6	KILLED	FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot		[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:152 expected: <{"sessionDate":null,"hwm":{},"footprint":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}> but was: <{"sessionDate":null,"hwm":{},"footprintX":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}>
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on	G-R6	KILLED	FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled		[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>
G-R6.3 the-hello-carries-the-outcome-high-water-mark	G-R6	KILLED	FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks,FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot		[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"30s":2000,"1m":5}}> || [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop	G-R10	KILLED	FootprintViewsTest.aNullPayloadIsARefusalNotAThrow		[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:85 a null bar payload must not throw ==> Unexpected exception thrown: java.lang.IllegalArgumentException: json
G-R10.2 every-admission-failure-is-a-counted-drop	G-R10	KILLED	FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules		[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.153 s <<< FAILURE! || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>
G-R10.3 every-record-is-counted-before-admission	G-R10	KILLED	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules		[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.147 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.013 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>
ES-FOOTPRINT-CAMPAIGN.json:31:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:90:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:152:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:215:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:271:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:326:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:379:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:432:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:488:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:552:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:615:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:668:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:727:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:784:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:837:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:890:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:943:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:990:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1049:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1112:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1168:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1203: "G-R3.1 an-oversize-record-is-dropped-not-broadcast": {
ES-FOOTPRINT-CAMPAIGN.json:1226:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1248:    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true>",
ES-FOOTPRINT-CAMPAIGN.json:1249:    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>"
ES-FOOTPRINT-CAMPAIGN.json:1284:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1339:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1397:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1465:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1528:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1581:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1634:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1687:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1743:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1798:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1851:  "killedByThrow": null,
ES-FOOTPRINT-CAMPAIGN.json:1884: "G-R10.3 every-record-is-counted-before-admission": {
ES-FOOTPRINT-CAMPAIGN.json:1886:  "clause": "records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too",
ES-FOOTPRINT-CAMPAIGN.json:1907:  "killedByThrow": null,
ES-FOOTPRINT-GATEWAY-DESIGN.md:88:| 1 | Transient (StringBuilder/encoding) and Kafka-poll bounds unproven; JVM layout assumptions unenforced | G-R7 mandates a streamed, byte-oriented response with a fixed buffer and a permit lifecycle; G-R8 derives the Kafka bound from the gateway's ENFORCED consumer settings and the oversized-first-batch rule, verifies the three HotSpot layout flags at start-up (fail-closed), and recomputes the total and both inequalities |
ES-FOOTPRINT-GATEWAY-DESIGN.md:104:| 2 | G-R8 arithmetic wrong; outcomes unbounded; heap unknown | G-R8 rewritten: byte AND count budgets per view with deterministic eviction, oversize rejection for BOTH record classes, retained-heap arithmetic against the es4 heap (`-Xmx1536m`, container limit 1536Mi), deployment contingent on measured headroom |
ES-FOOTPRINT-GATEWAY-DESIGN.md:158:| G-R8 | **Bounds and heap (proven from the limits, never from averages).** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key. **Retained-heap bound** (HotSpot, compressed oops and compressed class pointers — true for any heap < 32 GiB, as here): a JSON value is ASCII (F-E8 alphabet) so it is a Latin-1 compact `String`: 24 B object + 16 B array header + payload, aligned to 8 ⇒ ≤ payload + 48 B; a key `String` ≤ 160 B payload ⇒ ≤ 208 B; a `TreeMap.Entry` = 40 B; per entry overhead ≤ 296 B, so overhead ≤ (12 000 + 20 000) × 296 B = 9.04 MiB; payload ≤ 128 + 16 = 144 MiB; the coordinator's accounting (two `long` counters, one `LocalDate`, per-timeframe eviction cursors) < 1 KiB. Views total ≤ 153.1 MiB. **Transient** per in-flight backfill (G-R7 step 7): < 0.32 MiB, at most 4 concurrent ⇒ < 1.3 MiB; no `StringBuilder`, no response `String`, no encoder buffer beyond the fixed 64 KiB. **Kafka transient**, from the client's fetch mechanics and THREE enforced facts. Mechanics (Kafka consumer `Fetcher`, unchanged since KIP-74): the consumer keeps at most ONE in-flight fetch request per broker node, a fetch request for a node includes only assigned partitions that have NO buffered (completed, unconsumed) data, each partition's share of a response is ≤ `max.partition.fetch.bytes` except that the FIRST batch of a partition is returned whole when it is larger, and `max.poll.records` only paces how many buffered records a `poll()` hands out. Enforced facts: (i) `max.partition.fetch.bytes` = 512 KiB (§1); (ii) `M` = each footprint topic's effective `max.message.bytes` ≤ `GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588, the broker default), validated per G-R8a; (iii) the four topics are UNCOMPRESSED end to end — the upstream producer sets `compression.type=none` explicitly (options-edge-processing amendment: `EsCvdRuntime.producerProps`, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` per G-R8a — so a fetched share IS its decompressed size and no decompression buffer exists. Per consumer, each of the four single-partition footprint topics contributes at most: one in-flight share (≤ max(512 KiB, M) ≤ 1.0 MiB) + one buffered completed share (≤ 1.0 MiB) + the deserialized value `String`s of the records a `poll()` returned from that share (≤ the share's bytes + 48 B per record) ⇒ ≤ 3.0 MiB per topic + per-record overhead, ≤ 12.0 MiB per consumer + per-record overhead. Per-record client overhead for the records of ONE poll — here, and only here, bounded by `max.poll.records` = 100 (§1): key `String` ≤ 208 B, `ConsumerRecord` object ≤ 96 B (header, 7 references, two longs, two ints), empty `RecordHeaders` ≤ 32 B, `ConsumerRecords` map/list share ≤ 64 B ⇒ ≤ 400 B × 100 = 40 KiB. Per consumer ≤ 12.04 MiB; TWO consumers touch footprint topics (state cache, state live) ⇒ ≤ 24.1 MiB, rounded to 25 MiB. Non-footprint partitions in the same fetch responses are not attributable to this feature and are already part of the measured `H_peak`. **Layout assumptions are verified at start-up** when the flag is on: `HotSpotDiagnosticMXBean.getVMOption` for `UseCompressedOops`, `UseCompressedClassPointers` and `CompactStrings` must all be `true`, else the gateway refuses to start naming the flag (the arithmetic above is void otherwise); pinned by a test of the checker over a fake option map. Topic-side facts (ii) and (iii) are verified by G-R8a. **Footprint heap contribution ≤ 153.1 + 1.3 + 25 = 179.4 MiB, rounded to 180 MiB**, independent of record size distribution. **Deployment contingency (§4):** the flag may be enabled only when the deploy PR records, from a full ES session with CVD on and footprint off: (a) heap: `jvm_memory_bytes_used{area="heap"}` peak `H_peak` with `-Xmx − H_peak ≥ 180 MiB + 128 MiB = 308 MiB` (GC headroom); (b) container: `container_memory_working_set_bytes` peak `W_peak` with `limit − W_peak ≥ 180 MiB + 256 MiB = 436 MiB`, AND `limit − Xmx ≥ 512 MiB` (metaspace, code cache, thread stacks, socket/direct buffers, JVM overhead — the current es4 manifest has `limit = Xmx = 1536 MiB`, which violates this and MUST change in the same PR: e.g. `-Xmx1536m` with `limit 2560Mi`, or a measured alternative that satisfies both inequalities). Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits and the tests pin them. |
ES-FOOTPRINT-GATEWAY-DESIGN.md:160:| G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
ES-FOOTPRINT-GATEWAY-DESIGN.md:162:| G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |
ES-FOOTPRINT-GATEWAY-DESIGN.md:164:<!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->
ES-FOOTPRINT-GATEWAY-DESIGN.md:197:<!-- END footprint-reqstate -->
scripts/footprint-reqstate.sh:14:BEGIN='<!-- BEGIN footprint-reqstate'
scripts/footprint-reqstate.sh:15:END='<!-- END footprint-reqstate -->'
scripts/footprint-mutate.py:227:        # the throw itself, and both must appear in the run's own failure lines. `killedByThrow:
scripts/footprint-mutate.py:229:        want = m.get('killedByThrow') or {}
scripts/footprint-mutate.py:266:                  'killedByThrow': (m.get('killedByThrow') if throw_kill_ok else None),
scripts/footprint-conformance.py:112:               "load-bearing counterpart is killed. **That says nothing about the requirements this campaign did\n"
scripts/footprint-reqstate.py:21:    cut = text.find('<!-- BEGIN footprint-reqstate')
scripts/footprint-reqstate.py:42:            if not m.get('assertionFailures') and not m.get('killedByThrow'):

2026-09-08T20:51:41.162025Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
The five described edits are present. I’m now checking whether the generated claims are actually tied to the current commit and whether the harness/record can still accept evidence shapes that the prose says it rejects.
exec
/bin/bash -lc "scripts/footprint-reqstate.sh --check; jq -r '[.[].evidence.repoCommit] | group_by(.)[] | [.[0],length] | @tsv' ES-FOOTPRINT-CAMPAIGN.json; git rev-parse HEAD; jq -r 'to_entries[] | select(.key|contains(\"G-R10.3\")) | .value' ES-FOOTPRINT-CAMPAIGN.json; git diff origin/main...HEAD -- ES-FOOTPRINT-CAMPAIGN.json | head -80" in /private/tmp/oe-conf-gw
 succeeded in 5855ms:
2026-09-08 22:51:46.977 xcodebuild[98875:266267191]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:47.244 xcodebuild[98875:266267190] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-hq1bdWaY' (errno=Operation not permitted)
2026-09-08 22:51:47.930 xcodebuild[98911:266267366]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:48.144 xcodebuild[98911:266267364] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
8bbcdab403b230425306df89c66a929da9422fe1
{
  "status": "KILLED",
  "clause": "records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too",
  "requirement": "G-R10",
  "documentText": "**Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's).",
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
  "seconds": 8.0,
  "patch": {
    "old": "        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {",
    "new": "        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission\n        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {"
  },
  "evidence": {
    "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "returnCode": 1,
    "mutatedFileSha256": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
      "src/main/java/app/feedgateway/FeedGatewayService.java": {
        "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
        "mutated": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e"
      }
    },
    "outputSha256": "3aceba9b2d582160d00321118d23fe7472f18d0fe4c09d15af72fefe7d83ba2d",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.147 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.013 s <<< FAILURE!",
      "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false>",
      "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  }
}
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-CtItbYYo' (errno=Operation not permitted)
2026-09-08 22:51:49.534 xcodebuild[98989:266267839]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:49.808 xcodebuild[98989:266267838] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-9D60Vt7E' (errno=Operation not permitted)
2026-09-08 22:51:50.407 xcodebuild[99030:266268034]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:51:50.658 xcodebuild[99030:266268033] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
diff --git a/ES-FOOTPRINT-CAMPAIGN.json b/ES-FOOTPRINT-CAMPAIGN.json
index 3ef4e8f..b710df9 100644
--- a/ES-FOOTPRINT-CAMPAIGN.json
+++ b/ES-FOOTPRINT-CAMPAIGN.json
@@ -9,43 +9,62 @@
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
+  "killedByThrow": null,
+  "seconds": 8.2,
   "patch": {
    "old": "        if (cmp < 0) return null;",
    "new": "        if (cmp < 0) return false;"
   },
   "evidence": {
-   "repoCommit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
+   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
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
+   "outputSha256": "6d97f0b3ecf4776c2bcc784d17a7277acd1b3f94992d2f3af5c79c5abe6fa7f1",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.151 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.140 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:77 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:88 expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:101 expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:124 expected: <true> but was: <false>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "fc49c149dabb7b61ab608190ee1be4cc1c641644",
-   "command": "mvn -B test -Dtest=Footprint* -DfailIfNoTests=false",
+   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "5970c1f7c007850b3c2e1f72957b299dbb7266a1e1f3ea9f4062c5beb7c351c9",
-   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },

2026-09-08T20:51:52.760801Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "jq -r 'to_entries[] | [.key,.value.requirement,.value.clause,.value.documentText] | @tsv' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
G-R4 older-date-dropped	G-R4	an OLDER session date is dropped	**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.
G-R4 newer-date-rolls-both	G-R4	a NEWER date rolls BOTH views	Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
G-R4 equal-date-upserts	G-R4	an EQUAL date upserts without rolling	**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.
G-R4 rollover-counted	G-R4	a rollover is counted	Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
G-R8 evict-oldest	G-R8	evict the OLDEST key of the timeframe	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
G-R8 evict-rotates	G-R8	eviction ROTATES across timeframes	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
G-R8 byte-budget-decremented	G-R8	evicting decrements the byte total	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
G-R8 both-budgets-enforced	G-R8	both the count AND byte budgets are enforced	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
G-R8 evictions-counted	G-R8	evictions are counted per view	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
G-R8a ceiling-strict	G-R8a	the ceiling is inclusive; only ABOVE is refused	**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
G-R8a codec-allowlist	G-R8a	only producer/uncompressed are accepted	**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
G-R9 ceiling-precedence	G-R9	ceiling takes precedence over compression	`gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).
G-R7 to-is-inclusive	G-R7	toMs is INCLUSIVE	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
G-R7 limit-clamped-low	G-R7	the forwarded limit is clamped to at least 1	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
G-R7 limit-clamped-high	G-R7	the forwarded limit is clamped to the ceiling	`limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.
G-R7 epoch-guard	G-R7	an out-of-domain timestamp is refused	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
G-R7 epoch-guard-removed	G-R7	a cursor at or past the last epoch value can have nothing after it, and the increment is taken only inside the domain	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
G-R7 epoch-guard-relaxed	G-R7	the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
G-R1.1 the-flag-gates-every-footprint-object	G-R1	Flag off: the views are never instantiated	Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.
G-R2.1 one-wiring-method-binds-all-four	G-R2	ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers	ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.
G-R2.2 the-evidence-binding	G-R2	KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)	**Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).
G-R3.1 an-oversize-record-is-dropped-not-broadcast	G-R3	a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped	`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
G-R3.2 a-rejected-record-is-still-broadcast	G-R3	a record the view REJECTS for shape, session or size is still broadcast	`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
G-R3.3 live-snapshots-are-never-retained	G-R3	never cached, never retained	**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).
G-R5.1 the-outcome-key-orders-numerically	G-R5	the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)	The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;
G-R5.2 the-identity-is-the-last-component	G-R5	identity CONTAINS |, which is why it is the LAST component	`identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).
G-R5.3 a-foreign-identity-is-refused	G-R5	a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)	Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
G-R5.4 last-write-per-key-wins	G-R5	Last write per full key wins; a redelivery carries the same key and overwrites in place	Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
G-R6.1 the-hello-carries-the-footprint-field	G-R6	the existing cvd-hello frame gains ONE field footprint	**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on	G-R6	cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled	`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.
G-R6.3 the-hello-carries-the-outcome-high-water-mark	G-R6	outcomeHwm:{tf: maxResolvedAtBarStartMs}	**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop	G-R10	a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw	A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
G-R10.2 every-admission-failure-is-a-counted-drop	G-R10	every admission failure is a counted drop	A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
G-R10.3 every-record-is-counted-before-admission	G-R10	records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too	**Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's).

2026-09-08T20:52:10.268877Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "jq -r 'to_entries[] | select(.key|test(\"G-R3.2|G-R10.1|G-R10.2\")) | \"KEY: \\(.key)\\nCLAUSE: \\(.value.clause)\\nPATCH:\\n\\(.value.patch)\\nFAIL:\\n\\(.value.evidence.failureLines|join(\"\\n\"))\\n\"' ES-FOOTPRINT-CAMPAIGN.json; git show --stat --oneline HEAD; git show --format=fuller --no-ext-diff HEAD -- scripts/footprint-mutate.py scripts/footprint-reqstate.py scripts/footprint-reqstate.preamble" in /private/tmp/oe-conf-gw
 succeeded in 5360ms:
2026-09-08 22:52:14.583 xcodebuild[99670:266271894]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:14.879 xcodebuild[99670:266271893] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-VgDmd5oD' (errno=Operation not permitted)
2026-09-08 22:52:15.462 xcodebuild[99712:266272088]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:15.716 xcodebuild[99712:266272084] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
8bbcdab fix(footprint tooling): a kill must name an assertion, a throw-kill must name its throw, and the table counts probes
 ES-FOOTPRINT-CAMPAIGN.json | 682 +++++++++++++++++++++++----------------------
 1 file changed, 342 insertions(+), 340 deletions(-)
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-ttj3CjUB' (errno=Operation not permitted)
2026-09-08 22:52:17.418 xcodebuild[99905:266272598]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:17.651 xcodebuild[99905:266272593] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-M8XzwFlk' (errno=Operation not permitted)
2026-09-08 22:52:18.306 xcodebuild[244:266273068]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:18.551 xcodebuild[244:266273064] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.

2026-09-08T20:52:19.781542Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "git diff --stat 928983e..HEAD; git diff 928983e..HEAD -- scripts/footprint-mutate.py scripts/footprint-reqstate.py scripts/footprint-reqstate.preamble ES-FOOTPRINT-CAMPAIGN.json | sed -n '1,260p'; git log --format='%h %T %s' -4" in /private/tmp/oe-conf-gw
 succeeded in 7149ms:
2026-09-08 22:52:28.165 xcodebuild[1076:266275452]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:28.357 xcodebuild[1076:266275450] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-7xFMoLV9' (errno=Operation not permitted)
2026-09-08 22:52:29.026 xcodebuild[1109:266275684]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:29.219 xcodebuild[1109:266275677] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
 ES-FOOTPRINT-CAMPAIGN.json | 682 +++++++++++++++++++++++----------------------
 1 file changed, 342 insertions(+), 340 deletions(-)
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-loHNx5lZ' (errno=Operation not permitted)
2026-09-08 22:52:30.657 xcodebuild[1209:266276134]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:30.962 xcodebuild[1209:266276132] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-c7WIr9A4' (errno=Operation not permitted)
2026-09-08 22:52:31.575 xcodebuild[1240:266276311]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:31.805 xcodebuild[1240:266276310] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
diff --git a/ES-FOOTPRINT-CAMPAIGN.json b/ES-FOOTPRINT-CAMPAIGN.json
index 821d30a..b710df9 100644
--- a/ES-FOOTPRINT-CAMPAIGN.json
+++ b/ES-FOOTPRINT-CAMPAIGN.json
@@ -28,14 +28,14 @@
    "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
-  "killedByThrow": false,
-  "seconds": 13.9,
+  "killedByThrow": null,
+  "seconds": 8.2,
   "patch": {
    "old": "        if (cmp < 0) return null;",
    "new": "        if (cmp < 0) return false;"
   },
   "evidence": {
-   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "returnCode": 1,
    "mutatedFileSha256": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -45,26 +45,26 @@
      "mutated": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc"
     }
    },
-   "outputSha256": "5e021ee04b30f0063c26ca14004fbc25fc728a03543c533676bbd83f1397ed10",
+   "outputSha256": "6d97f0b3ecf4776c2bcc784d17a7277acd1b3f94992d2f3af5c79c5abe6fa7f1",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.228 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.140 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.003 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:97 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:108 expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:121 expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED>",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
-   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R4 newer-date-rolls-both": {
@@ -87,14 +87,14 @@
   "anyFailures": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
-  "killedByThrow": false,
-  "seconds": 16.3,
+  "killedByThrow": null,
+  "seconds": 7.9,
   "patch": {
    "old": "        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;",
    "new": "        bars.clear(); barsBytes = 0;"
   },
   "evidence": {
-   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "returnCode": 1,
    "mutatedFileSha256": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -104,20 +104,20 @@
      "mutated": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6"
     }
    },
-   "outputSha256": "3aab049deb18a7f73892763b0b9aa8fc86375e7e4df2123e957720aa4a1ba1f2",
+   "outputSha256": "82a81e9d36058cd5a5b60073fc258691db3065677472c2a227634f0e4a9d2fae",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.038 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:95 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
-   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R4 equal-date-upserts": {
@@ -149,14 +149,14 @@
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
-  "killedByThrow": false,
-  "seconds": 17.9,
+  "killedByThrow": null,
+  "seconds": 7.6,
   "patch": {
    "old": "        if (cmp == 0) return false;",
    "new": "        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }"
   },
   "evidence": {
-   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "returnCode": 1,
    "mutatedFileSha256": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -166,30 +166,30 @@
      "mutated": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca"
     }
    },
-   "outputSha256": "69fc19f041c48924e7af47e81e9de3941e2096b473c6def4ec66a5f7510f98c9",
+   "outputSha256": "88b550563c84ac1484a6a761d23eeab9fe75948c5deeae0a46b173449d93ddd5",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.008 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.002 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {\"sessionDate\":\"2026-08-14\",\"bars\":[{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"30s\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":5000},\"tag\":\"x\"}],\"nextCursor\":null} ==> expected: <true> but was: <false>",
-    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:192 expected: <2> but was: <0>",
-    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:250 expected: <2> but was: <0>",
-    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:235 expected: <1> but was: <0>",
-    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:135 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{},\"outcomeHwm\":{\"5m\":70}}>"
+    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0>",
+    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0>",
+    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>",
+    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{},\"outcomeHwm\":{\"5m\":70}}>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
-   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R4 rollover-counted": {
@@ -212,14 +212,14 @@
   "anyFailures": [
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
-  "killedByThrow": false,
-  "seconds": 13.7,
+  "killedByThrow": null,
+  "seconds": 8.1,
   "patch": {
    "old": "        rollovers.incrementAndGet();",
    "new": "        if (false) rollovers.incrementAndGet();"
   },
   "evidence": {
-   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "returnCode": 1,
    "mutatedFileSha256": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -229,20 +229,20 @@
      "mutated": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5"
     }
    },
-   "outputSha256": "fa0ac70a00952c2540e8bbaf520f69a6b8631131ecd2b0c8a14188c8f0506a7b",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "68dff56f6652491b8991b96a3fb78497fc6ea00a6a72dc3d79cbf4f64cda20a7",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.015 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:96 expected: <1> but was: <0>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
-   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 evict-oldest": {
@@ -268,14 +268,14 @@
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
-  "killedByThrow": false,
-  "seconds": 14.1,
+  "killedByThrow": null,
+  "seconds": 8.0,
   "patch": {
    "old": "            String oldest = view.ceilingKey(tf + \"|\");",
    "new": "            String oldest = view.floorKey(tf + \"|~\");"
   },
   "evidence": {
-   "repoCommit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "returnCode": 1,
    "mutatedFileSha256": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -285,22 +285,22 @@
      "mutated": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146"
     }
    },
-   "outputSha256": "01d4eaa84ff657dc806c92c6c369a501882283a14cdf2c2ca8734639b05fe21e",
+   "outputSha256": "ef2b53443135fea099a82f814cc9be9ab6a33162a373892d9c39023691a1df4c",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:252 expected: <247> but was: <246>",
-    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:237 1m lost its oldest ==> expected: <[2]> but was: <[1]>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246>",
+    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "0ecf5f79612b3a7e6234eae158b331c11413a21a",
+   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "38f79de07e423d33a0e3651eee4e54ae826f77c822e775d4804f920a9c360a51",
-   "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
+   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
+   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 evict-rotates": {
@@ -323,14 +323,14 @@
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-akWgKDUn' (errno=Operation not permitted)
2026-09-08 22:52:33.163 xcodebuild[1326:266276674]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:33.360 xcodebuild[1326:266276673] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-LtsDZFRE' (errno=Operation not permitted)
2026-09-08 22:52:33.944 xcodebuild[1373:266276836]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 22:52:34.127 xcodebuild[1373:266276835] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
8bbcdab 645b4b15ba54fcb2a0d5d9048ae88aefd8748096 fix(footprint tooling): a kill must name an assertion, a throw-kill must name its throw, and the table counts probes
928983e caa19de479ac68e19f5f3d2bff05aead35d03c1b test(gateway footprint): state the no-throw clause directly, not through a propagated throw
81202f4 6ebcaf184a62285fe13d1270e981326ec34bdbc4 wip: harness+generator round-2 folds
23ff325 4b9f398130bdcdaffbe1cd33d7d9a267b582d38f docs(gateway footprint): §2a is regenerated in place, and says only what the record carries

2026-09-08T20:52:35.260536Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "nl -ba ES-FOOTPRINT-CAMPAIGN.json | sed -n '1250,1325p;1880,1935p'; nl -ba scripts/footprint-reqstate.py | sed -n '34,55p'; nl -ba ES-FOOTPRINT-GATEWAY-DESIGN.md | sed -n '156,190p'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
  1250	   ],
  1251	   "treeRestoredClean": true
  1252	  },
  1253	  "baseline": {
  1254	   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
  1255	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1256	   "returnCode": 0,
  1257	   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
  1258	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1259	  }
  1260	 },
  1261	 "G-R3.2 a-rejected-record-is-still-broadcast": {
  1262	  "status": "KILLED",
  1263	  "clause": "a record the view REJECTS for shape, session or size is still broadcast",
  1264	  "requirement": "G-R3",
  1265	  "documentText": "`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) \u2014 a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).",
  1266	  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
  1267	  "occurrence": 1,
  1268	  "occurrencesInFile": 1,
  1269	  "line": 905,
  1270	  "enclosing": "boolean admitFootprintRecord(String event, String json, String consumer) { // package-private: the wiring tests drive both consumer paths th",
  1271	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1272	  "killedBy": [
  1273	   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
  1274	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
  1275	  ],
  1276	  "assertionFailures": [
  1277	   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
  1278	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
  1279	  ],
  1280	  "anyFailures": [
  1281	   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
  1282	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
  1283	  ],
  1284	  "killedByThrow": null,
  1285	  "seconds": 8.1,
  1286	  "patch": {
  1287	   "old": "        return a.reason() != FootprintViews.Reason.OVERSIZE;",
  1288	   "new": "        return a.reason() == FootprintViews.Reason.ADMITTED;"
  1289	  },
  1290	  "evidence": {
  1291	   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
  1292	   "returnCode": 1,
  1293	   "mutatedFileSha256": "66a9f89dfab6c87ccac729e9bcaf04b11ca59d06c4c08602d8ad7be5da36b8d1",
  1294	   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1295	   "filesSha256": {
  1296	    "src/main/java/app/feedgateway/FeedGatewayService.java": {
  1297	     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1298	     "mutated": "66a9f89dfab6c87ccac729e9bcaf04b11ca59d06c4c08602d8ad7be5da36b8d1"
  1299	    }
  1300	   },
  1301	   "outputSha256": "23d9cd8ee4d8b397d1aceae1d8a9b671d6a96a286c211338f86902173432893c",
  1302	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
  1303	   "failureLines": [
  1304	    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.129 s <<< FAILURE!",
  1305	    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.014 s <<< FAILURE!",
  1306	    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false>",
  1307	    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>"
  1308	   ],
  1309	   "treeRestoredClean": true
  1310	  },
  1311	  "baseline": {
  1312	   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
  1313	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1314	   "returnCode": 0,
  1315	   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
  1316	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1317	  }
  1318	 },
  1319	 "G-R3.3 live-snapshots-are-never-retained": {
  1320	  "status": "KILLED",
  1321	  "clause": "never cached, never retained",
  1322	  "requirement": "G-R3",
  1323	  "documentText": "**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every \u2264 5 s \u2014 F-R15 \u2014 so a new client waits at most one heartbeat; the same class as `es-cvd`).",
  1324	  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
  1325	  "occurrence": 1,
  1880	   "outputSha256": "907b8ffcd0b69125b462728581f541073e46261fa0df0414ee02bb069fe9699a",
  1881	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1882	  }
  1883	 },
  1884	 "G-R10.3 every-record-is-counted-before-admission": {
  1885	  "status": "KILLED",
  1886	  "clause": "records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too",
  1887	  "requirement": "G-R10",
  1888	  "documentText": "**Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's).",
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
  1908	  "seconds": 8.0,
  1909	  "patch": {
  1910	   "old": "        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {",
  1911	   "new": "        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission\n        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {"
  1912	  },
  1913	  "evidence": {
  1914	   "repoCommit": "928983ee1699a952cb5200d96617bad6f53dd00e",
  1915	   "returnCode": 1,
  1916	   "mutatedFileSha256": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e",
  1917	   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1918	   "filesSha256": {
  1919	    "src/main/java/app/feedgateway/FeedGatewayService.java": {
  1920	     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1921	     "mutated": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e"
  1922	    }
  1923	   },
  1924	   "outputSha256": "3aceba9b2d582160d00321118d23fe7472f18d0fe4c09d15af72fefe7d83ba2d",
  1925	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
  1926	   "failureLines": [
  1927	    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.147 s <<< FAILURE!",
  1928	    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.013 s <<< FAILURE!",
  1929	    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false>",
  1930	    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>"
  1931	   ],
  1932	   "treeRestoredClean": true
  1933	  },
  1934	  "baseline": {
  1935	   "commit": "928983ee1699a952cb5200d96617bad6f53dd00e",
    34	    # failure naming a test, and the verbatim failure lines that name was parsed from. A record that
    35	    # merely says KILLED is a claim, not evidence — and one kill in this campaign turned out to be a
    36	    # format-string error under the right test's name, which is exactly what this refuses.
    37	    problems = []
    38	    for rid, ms in per.items():
    39	        for m in ms:
    40	            if m['status'] != 'KILLED':
    41	                continue
    42	            if not m.get('assertionFailures') and not m.get('killedByThrow'):
    43	                problems.append(f"{rid}: a KILLED record names no ASSERTION failure and declares no "
    44	                                f"matched throw ({m.get('killedBy')})")
    45	            if not m.get('evidence', {}).get('failureLines'):
    46	                problems.append(f"{rid}: a KILLED record carries no failure lines")
    47	            if not m.get('evidence', {}).get('treeRestoredClean', True):
    48	                problems.append(f"{rid}: a KILLED record ran against a tree it did not restore")
    49	    if problems:
    50	        print("REFUSING TO EMIT:", file=sys.stderr)
    51	        for p in problems: print("  ", p, file=sys.stderr)
    52	        sys.exit(1)
    53	
    54	    def gate(rid):
    55	        for pat, g in gates.items():
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
   188	| G-R9 | 1 of 1 probes pinned, over 1 clause | 2 | Metrics |
   189	| G-R10 | 3 of 3 probes pinned, over 3 clauses | 2 | Non-interference |
   190	| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |

2026-09-08T20:52:42.732874Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
2026-09-08T20:52:49.686004Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
Found three remaining issues:

- HIGH — [ES-FOOTPRINT-CAMPAIGN.json:1884](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:1884) assigns `records_total ... BEFORE admission` to G-R10, but that clause belongs to G-R9 ([design:160](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:160)); G-R10 contains no such clause ([design:161](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:161)). Consequently, the generated table incorrectly reports G-R9 as 1/1 and G-R10 as 3/3. The mutation and its failure lines now demonstrate the intended oversize ordering, but under the wrong requirement.

- MEDIUM — [ES-FOOTPRINT-CAMPAIGN.json:1263](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:1263) claims rejected records of “shape, session or size” remain broadcast. Size/oversize is explicitly excluded by G-R3, and this probe’s failure lines mention only stale-session behavior ([lines 1303–1307](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:1303)). Narrow the recorded clause to shape/stale-session rejection.

- MEDIUM — [footprint-reqstate.py:42](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:42) still treats any truthy `killedByThrow` value as sufficient. It does not require the `{test, throws}` structure or verify both strings against `failureLines`. The harness performs that validation, but the renderer’s claim that it independently “refuses” unsupported records remains unheld for a modified or legacy checked-in record.

The five Round 2 implementation changes themselves are otherwise present: G-R10.1 now produces an assertion failure; current records use no throw-kills; G-R10.3’s mutation moves counting after admission and skips oversize; requirement discovery stops before the generated block; and the rewrite requires one ordered line-start marker pair. The rendered table exactly matches the generator output. Full test execution was unavailable because this review environment prohibits build/temp writes.

VERDICT: REQUEST_CHANGES
tokens used
80.080
Found three remaining issues:

- HIGH — [ES-FOOTPRINT-CAMPAIGN.json:1884](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:1884) assigns `records_total ... BEFORE admission` to G-R10, but that clause belongs to G-R9 ([design:160](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:160)); G-R10 contains no such clause ([design:161](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:161)). Consequently, the generated table incorrectly reports G-R9 as 1/1 and G-R10 as 3/3. The mutation and its failure lines now demonstrate the intended oversize ordering, but under the wrong requirement.

- MEDIUM — [ES-FOOTPRINT-CAMPAIGN.json:1263](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:1263) claims rejected records of “shape, session or size” remain broadcast. Size/oversize is explicitly excluded by G-R3, and this probe’s failure lines mention only stale-session behavior ([lines 1303–1307](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:1303)). Narrow the recorded clause to shape/stale-session rejection.

- MEDIUM — [footprint-reqstate.py:42](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:42) still treats any truthy `killedByThrow` value as sufficient. It does not require the `{test, throws}` structure or verify both strings against `failureLines`. The harness performs that validation, but the renderer’s claim that it independently “refuses” unsupported records remains unheld for a modified or legacy checked-in record.

The five Round 2 implementation changes themselves are otherwise present: G-R10.1 now produces an assertion failure; current records use no throw-kills; G-R10.3’s mutation moves counting after admission and skips oversize; requirement discovery stops before the generated block; and the rewrite requires one ordered line-start marker pair. The rendered table exactly matches the generator output. Full test execution was unavailable because this review environment prohibits build/temp writes.

VERDICT: REQUEST_CHANGES
