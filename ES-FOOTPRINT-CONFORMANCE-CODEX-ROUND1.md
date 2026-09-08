Reading additional input from stdin...
2026-09-08T19:24:27.966147Z ERROR codex_models_manager::manager: failed to load models cache: missing field `supports_parallel_tool_calls` at line 93 column 5
OpenAI Codex v0.147.0
--------
workdir: /private/tmp/oe-conf-gw
model: gpt-5.6-sol
provider: openai
approval: never
sandbox: read-only
reasoning effort: low
reasoning summaries: none
session id: 01a0827a-9b1d-7ee1-805b-8383a866d220
--------
user
Review option-edge-feed-gateway PR #174 at /private/tmp/oe-conf-gw, branch
test/footprint-conformance-pins, LINE BY LINE for institutional-grade accuracy, military-grade
completeness, NASA-grade preparation.

The change closes five G-R clauses that mutation testing showed no test could distinguish, and adds
a generated §2a conformance section to ES-FOOTPRINT-GATEWAY-DESIGN.md.

Production changes (all visibility/seam only — verify that claim):
  * addEsFootprintTopics: private -> package-private
  * cachePolicyFor and record CachePolicy: private -> package-private
  * the cvd-hello send condition extracted into a named `sendsCvdHello()`

Check, against the files and not the PR text:
 1. Whether any production BEHAVIOUR changed. If it did, say exactly what.
 2. Whether each new test actually fails when its clause is broken — reason about the specific
    mutation each was written against (they are listed in ES-FOOTPRINT-CAMPAIGN.json with the patch).
 3. Whether the two re-aimed source pins (CvdSpxLevelsWiringTest.connectReplayRidesInsideTheHelloContract,
    FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics)
    still assert what they were protecting, or whether re-aiming weakened them.
 4. Whether theWiringMethodPutsAllFourBindingsInTheMapItIsGiven, liveSnapshotsAreNeverRetained...,
    theHelloIsSentWhenFootprintAloneIsEnabled, anOutcomeRedeliveryOverwritesItsKeyInPlace and
    aNullPayloadIsARefusalNotAThrow each depend on the thing they claim to and nothing else
    (in particular whether any of them would pass for an unrelated reason).
 5. Whether §2a's generated table is CONSISTENT with ES-FOOTPRINT-CAMPAIGN.json, whether
    scripts/footprint-reqstate.py can emit a row that the record does not support, and whether any
    wording in §2a overclaims (especially "pinned", and the G-R11 "TEST INVENTORY" note).
 6. Anything the campaign should have probed for these five requirements and did not.

Do NOT accept the PR description or the document as evidence; verify against source and record.
End with exactly one line: VERDICT: APPROVE  or  VERDICT: REQUEST_CHANGES
codex
I’ll review the branch directly against its base, trace each listed mutation to the exact assertion path, and independently regenerate/check §2a from the campaign record. I’ll keep the conclusion evidence-based and end with the required verdict line.
exec
/bin/bash -lc 'git status --short && git branch --show-current && git log --oneline --decorate -8 && git diff --stat origin/main...HEAD || git diff --stat main...HEAD' in /private/tmp/oe-conf-gw
exec
/bin/bash -lc 'command -v git; /usr/bin/git status --short; /usr/bin/git branch --show-current; /usr/bin/git log --oneline --decorate -8' in /private/tmp/oe-conf-gw
 succeeded in 45987ms:
2026-09-08 21:24:39.611 xcodebuild[21509:265666589]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:24:40.917 xcodebuild[21509:265666585] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-EyIvS88E' (errno=Operation not permitted)
2026-09-08 21:24:43.590 xcodebuild[21717:265667419]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:24:44.492 xcodebuild[21717:265667401] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-3NTR5tRb' (errno=Operation not permitted)
2026-09-08 21:24:49.795 xcodebuild[22091:265668552]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:24:50.853 xcodebuild[22091:265668539] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-0bzf0NaA' (errno=Operation not permitted)
2026-09-08 21:24:53.320 xcodebuild[22231:265669185]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:24:54.602 xcodebuild[22231:265669182] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
test/footprint-conformance-pins
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-pWju3IqR' (errno=Operation not permitted)
2026-09-08 21:25:01.832 xcodebuild[22433:265670226]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:03.281 xcodebuild[22433:265670208] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-u29BmqxM' (errno=Operation not permitted)
2026-09-08 21:25:06.585 xcodebuild[22593:265670908]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:07.835 xcodebuild[22593:265670905] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
54e3952 (HEAD -> test/footprint-conformance-pins, origin/test/footprint-conformance-pins) docs(gateway footprint): the design carries what a test actually holds
1224cec test(gateway footprint): execute the four clauses the suite only read
3e13574 (origin/main, origin/HEAD) Merge pull request #172 from abhinav-jain09/docs/campaign-failure-lines
7e220e6 (origin/docs/campaign-failure-lines, docs/campaign-failure-lines) docs(gateway): the record now carries the failure lines its killedBy was parsed from
fc49c14 Merge pull request #171 from abhinav-jain09/docs/footprint-campaign-record
ac38b5c (origin/docs/footprint-campaign-record, docs/footprint-campaign-record) docs(gateway): the footprint mutation record, with the evidence to audit it
b933b39 Merge pull request #170 from abhinav-jain09/fix/pin-epoch-edge-cursor
de2f4f5 (origin/fix/pin-epoch-edge-cursor, fix/pin-epoch-edge-cursor) docs: archive the four merge-gate rounds for the epoch-edge cursor pin
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-LZJcgtSL' (errno=Operation not permitted)
2026-09-08 21:25:13.761 xcodebuild[22808:265671946]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:14.625 xcodebuild[22808:265671942] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-bm03vDIq' (errno=Operation not permitted)
2026-09-08 21:25:16.800 xcodebuild[22925:265672434]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:18.079 xcodebuild[22925:265672431] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
 ES-FOOTPRINT-CAMPAIGN.json                         | 1181 ++++++++++++++++----
 ES-FOOTPRINT-GATEWAY-DESIGN.md                     |   30 +
 scripts/footprint-mutate.py                        |   69 +-
 scripts/footprint-reqstate.py                      |   50 +
 scripts/footprint-reqstate.sh                      |   12 +
 .../java/app/feedgateway/FeedGatewayService.java   |   13 +-
 .../app/feedgateway/CvdSpxLevelsWiringTest.java    |    7 +-
 .../java/app/feedgateway/FootprintViewsTest.java   |   20 +
 .../java/app/feedgateway/FootprintWiringTest.java  |   47 +-
 9 files changed, 1220 insertions(+), 209 deletions(-)

 succeeded in 32755ms:
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-yl0aL3Jv' (errno=Operation not permitted)
2026-09-08 21:24:52.120 xcodebuild[22200:265669075]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:24:53.210 xcodebuild[22200:265669072] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-EGNnZrRK' (errno=Operation not permitted)
2026-09-08 21:24:55.872 xcodebuild[22297:265669520]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:24:56.468 xcodebuild[22297:265669518] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-KGQVbgV7' (errno=Operation not permitted)
2026-09-08 21:25:03.092 xcodebuild[22477:265670465]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:04.604 xcodebuild[22477:265670462] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-E1NZZXJ2' (errno=Operation not permitted)
2026-09-08 21:25:07.249 xcodebuild[22612:265670972]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:08.368 xcodebuild[22612:265670962] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
test/footprint-conformance-pins
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-LaO2aQqN' (errno=Operation not permitted)
2026-09-08 21:25:14.007 xcodebuild[22819:265672029]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:14.903 xcodebuild[22819:265672016] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-1y9wLGvI' (errno=Operation not permitted)
2026-09-08 21:25:16.843 xcodebuild[22926:265672460]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:18.101 xcodebuild[22926:265672451] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
54e3952 (HEAD -> test/footprint-conformance-pins, origin/test/footprint-conformance-pins) docs(gateway footprint): the design carries what a test actually holds
1224cec test(gateway footprint): execute the four clauses the suite only read
3e13574 (origin/main, origin/HEAD) Merge pull request #172 from abhinav-jain09/docs/campaign-failure-lines
7e220e6 (origin/docs/campaign-failure-lines, docs/campaign-failure-lines) docs(gateway): the record now carries the failure lines its killedBy was parsed from
fc49c14 Merge pull request #171 from abhinav-jain09/docs/footprint-campaign-record
ac38b5c (origin/docs/footprint-campaign-record, docs/footprint-campaign-record) docs(gateway): the footprint mutation record, with the evidence to audit it
b933b39 Merge pull request #170 from abhinav-jain09/fix/pin-epoch-edge-cursor
de2f4f5 (origin/fix/pin-epoch-edge-cursor, fix/pin-epoch-edge-cursor) docs: archive the four merge-gate rounds for the epoch-edge cursor pin

exec
/bin/bash -lc "pwd; ls -la .git | head; sed -n '1,200p' .git/config; find .git/refs -type f -maxdepth 4 -print -exec sed -n '1p' {} \\;; rg --files | sed -n '1,160p'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
/private/tmp/oe-conf-gw
-rw-r--r--@ 1 abhinav  wheel  97 Sep  8 01:14 .git
sed: .git/config: Not a directory
find: .git/refs: Not a directory
ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md
GATEWAY-SUREFIRE-ROUTING-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md
MULTI-REPLICA.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md
README.md
pom.xml
deploymodel.md
GATEWAY-SUREFIRE-ROUTING-CODEX-ROUND1.md
Dockerfile
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND4.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md
GATEWAY-EPOCH-CURSOR-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND1.md
GATEWAY-EPOCH-CURSOR-CODEX-ROUND4.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND3.md
src/main/java/app/feedgateway/FootprintTopicGate.java
src/main/java/app/feedgateway/stockgex/StockGexController.java
src/main/java/app/feedgateway/stockgex/StockGexAsyncConfig.java
src/main/java/app/feedgateway/stockgex/StockGexConfig.java
src/main/java/app/feedgateway/stockgex/StockGexUpstream.java
dev/keycloak/run-keycloak.sh
dev/keycloak/realm-export.json
src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryConfig.java
src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryStore.java
src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryController.java
src/main/java/app/feedgateway/liquidityhistory/SessionAggregate.java
src/main/java/app/feedgateway/liquidityhistory/HistoryFrame.java
src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryAuth.java
src/main/java/app/feedgateway/liquidityhistory/ChainSnapshot.java
src/main/java/app/feedgateway/WsTokenExpiryReaper.java
src/main/java/app/feedgateway/FootprintViews.java
src/main/java/app/feedgateway/WebSocketConfig.java
Jenkinsfile
accelration.md
src/main/java/app/feedgateway/mtsession/ConcurrencyLimits.java
scripts/footprint-reqstate.sh
scripts/footprint-mutate.py
scripts/footprint-conformance.py
scripts/footprint-reqstate.py
ES-FOOTPRINT-GATEWAY-CODEX-ROUND9.md
dev/raw-feed-replicator/pom.xml
dev/raw-feed-replicator/src/main/java/app/feedgateway/dev/RawFeedReplicator.java
src/main/java/app/feedgateway/GatewayController.java
src/main/java/app/feedgateway/GatewaySettings.java
src/main/java/app/feedgateway/OutboundChannel.java
src/main/java/app/feedgateway/FeedGatewayService.java
src/main/java/app/feedgateway/ApprovalReaper.java
dev/raw-feed-replicator/src/test/java/app/feedgateway/dev/RawFeedReplicatorSafetyTest.java
dev/raw-feed-replicator/dependency-reduced-pom.xml
ES-FOOTPRINT-CAMPAIGN.json
GATEWAY-EPOCH-CURSOR-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md
GATEWAY-EPOCH-CURSOR-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-DESIGN.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND2.md
src/main/java/app/feedgateway/selleractivity/SellerActivityController.java
src/main/java/app/feedgateway/selleractivity/SellerActivityAggregator.java
src/main/java/app/feedgateway/WsJwtHandshakeInterceptor.java
src/main/java/app/feedgateway/SecurityHeadersFilter.java
src/main/java/app/feedgateway/FeedGatewayApplication.java
src/main/java/app/feedgateway/SessionExpiryReaper.java
src/main/resources/application.properties
src/main/java/app/feedgateway/gammamigration/GammaMigrationController.java
src/main/java/app/feedgateway/gammamigration/GammaRotationController.java
src/main/java/app/feedgateway/gammamigration/GammaFragilityController.java
src/main/java/app/feedgateway/FeedWebSocketHandler.java
src/main/resources/static/vendor/react-dom.production.min.js
src/main/resources/static/vendor/react.production.min.js
src/main/resources/static/app.html
src/main/resources/static/option-chain.js
src/main/resources/static/login.js
src/main/resources/static/option-chain.css
src/main/resources/static/app-boot.js
src/main/resources/static/index.html
src/main/java/app/feedgateway/HpsfGatewayViewMapper.java
src/main/java/app/feedgateway/AudienceValidator.java
src/main/java/app/feedgateway/GatewayMarketCalendar.java
src/main/java/app/feedgateway/SellerActivityDiskStore.java
src/test/java/app/feedgateway/FeedGatewayShutdownTest.java
src/test/java/app/feedgateway/HotStrikeEventTest.java
src/test/java/app/feedgateway/SellerActivityCacheIntegrationTest.java
src/test/java/app/feedgateway/SessionExpirySweepTest.java
src/test/java/app/feedgateway/OutboundChannelTest.java
src/main/java/app/feedgateway/pinflow/PinFlowResponse.java
src/main/java/app/feedgateway/pinflow/PinFlowDataSourceConfig.java
src/main/java/app/feedgateway/pinflow/PinFlowController.java
src/main/java/app/feedgateway/pinflow/PinFlowStore.java
src/main/java/app/feedgateway/pinflow/PinFlowExecutor.java
src/main/java/app/feedgateway/pinflow/PinFlowAggregator.java
src/test/java/app/feedgateway/stockgex/StockGexMvcTest.java
src/test/java/app/feedgateway/stockgex/StockGexAuthAllowlistRegressionTest.java
src/test/java/app/feedgateway/stockgex/StockGexControllerTest.java
src/test/java/app/feedgateway/stockgex/StockGexAsyncConfigTest.java
src/test/java/app/feedgateway/stockgex/StockGexAsyncPipelineTest.java
src/test/java/app/feedgateway/stockgex/StockGexCloseBoardTest.java
src/test/java/app/feedgateway/EsAggressorFlowWiringTest.java
src/test/java/app/feedgateway/liquidityhistory/HistoryFrameFailClosedTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryTestSupport.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryControllerTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryStoreEpochTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryFoldTest.java
src/test/java/app/feedgateway/EsAuctionWiringTest.java
src/test/java/app/feedgateway/FeedGatewayServiceTest.java
src/test/java/app/feedgateway/GatewayMarketCalendarTest.java
src/test/java/app/feedgateway/FeedGatewayReplayCancellationTest.java
src/test/java/app/feedgateway/GatewayIsolationLevelTest.java
src/test/java/app/feedgateway/CvdBarViewTest.java
src/test/java/app/feedgateway/FootprintBackfillControllerTest.java
src/main/java/app/feedgateway/systemstatus/SystemStatusFailure.java
src/main/java/app/feedgateway/systemstatus/SystemStatusConfig.java
src/main/java/app/feedgateway/systemstatus/SystemStatusDataSource.java
src/main/java/app/feedgateway/systemstatus/SystemStatusStore.java
src/main/java/app/feedgateway/systemstatus/ConsumerLagReader.java
src/main/java/app/feedgateway/systemstatus/SystemStatusController.java
src/main/java/app/feedgateway/mtsession/UserSessionPolicy.java
src/main/java/app/feedgateway/mtsession/SubscriptionManager.java
src/main/java/app/feedgateway/mtsession/SharedSnapshotCache.java
src/main/java/app/feedgateway/mtsession/InMemoryTicketStore.java
src/main/java/app/feedgateway/mtsession/Selection.java
src/main/java/app/feedgateway/mtsession/NotApprovedException.java
src/main/java/app/feedgateway/mtsession/AppSession.java
src/main/java/app/feedgateway/mtsession/CapacityException.java
src/main/java/app/feedgateway/mtsession/RedisTicketStore.java
src/main/java/app/feedgateway/mtsession/SubscriptionKey.java
src/main/java/app/feedgateway/mtsession/WsTicket.java
src/main/java/app/feedgateway/mtsession/SharedUnderlyings.java
src/main/java/app/feedgateway/mtsession/SessionRoutingEngine.java
src/main/java/app/feedgateway/mtsession/EventType.java
src/main/java/app/feedgateway/mtsession/StrikeWindow.java
src/main/java/app/feedgateway/mtsession/TicketStore.java
src/main/java/app/feedgateway/mtsession/ApprovalStateMachine.java
src/main/java/app/feedgateway/mtsession/ApprovalState.java
src/main/java/app/feedgateway/mtsession/RoutableRecord.java
src/main/java/app/feedgateway/mtsession/Normalization.java
src/main/java/app/feedgateway/mtsession/RoutingTarget.java
src/main/java/app/feedgateway/mtsession/RoutingKeyDeriver.java
src/main/java/app/feedgateway/mtsession/EntitlementPolicy.java
src/main/java/app/feedgateway/mtsession/UnderlyingKey.java
src/main/java/app/feedgateway/mtsession/MarketDataSource.java
src/main/java/app/feedgateway/mtsession/NotEntitledException.java
src/main/java/app/feedgateway/mtsession/approval/RoleClaimApprovalAuthority.java
src/main/java/app/feedgateway/mtsession/approval/HttpApprovalAuthority.java
src/main/java/app/feedgateway/mtsession/approval/ApprovalAuthority.java
Jenkinsfile.deploy
src/test/java/app/feedgateway/WsTokenExpiryTest.java
src/test/java/app/feedgateway/GatewayCalendarConfigTest.java
src/test/java/app/feedgateway/LiquidityHistoryWsChainsTest.java
src/test/java/app/feedgateway/GatewayContextSmokeTest.java
src/test/java/app/feedgateway/StaticAssetSecurityTest.java
src/test/java/app/feedgateway/FootprintSeamTest.java
src/test/java/app/feedgateway/DeltaFlowAccelWiringTest.java
src/test/java/app/feedgateway/FeedGatewayReplayCompletionTest.java

exec
/bin/bash -lc "sed -n '1p' .git; gitdir="'$(sed '"'s/"'^gitdir: //'"' .git); sed -n '1,160p' \""'$gitdir/config.worktree" 2>/dev/null; sed -n '"'1p' \""'$gitdir/HEAD"; sed -n '"'1,200p' \""'$gitdir/commondir"' in /private/tmp/oe-conf-gw
 succeeded in 55ms:
ref: refs/heads/test/footprint-conformance-pins
../..

codex
The checkout is a linked worktree whose Git metadata lives outside the restricted workspace, so ordinary Git operations are noisy and partially blocked by macOS tooling. I can still read the source and object metadata; I’m working around that without modifying the tree.
exec
/bin/bash -lc 'GIT_PAGER=cat git --no-optional-locks -c core.fsmonitor=false -c commit.gpgsign=false log --oneline -10 HEAD 2>&1; GIT_PAGER=cat git --no-optional-locks -c core.fsmonitor=false diff --name-status HEAD''^ HEAD 2>&1' in /private/tmp/oe-conf-gw
 succeeded in 18851ms:
2026-09-08 21:25:37.552 xcodebuild[23818:265676053]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:38.404 xcodebuild[23818:265676047] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-nCrgHmCZ' (errno=Operation not permitted)
2026-09-08 21:25:39.730 xcodebuild[23973:265676683]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:40.618 xcodebuild[23973:265676679] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
54e3952 docs(gateway footprint): the design carries what a test actually holds
1224cec test(gateway footprint): execute the four clauses the suite only read
3e13574 Merge pull request #172 from abhinav-jain09/docs/campaign-failure-lines
7e220e6 docs(gateway): the record now carries the failure lines its killedBy was parsed from
fc49c14 Merge pull request #171 from abhinav-jain09/docs/footprint-campaign-record
ac38b5c docs(gateway): the footprint mutation record, with the evidence to audit it
b933b39 Merge pull request #170 from abhinav-jain09/fix/pin-epoch-edge-cursor
de2f4f5 docs: archive the four merge-gate rounds for the epoch-edge cursor pin
1e6e6d5 fix(gateway): kill switch for the pre-open GEX VALUE plane — it has no consumer (#169)
b054e8d test(gateway): pin G-R7's epoch-edge cursor guard, which nothing held
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-q0wExnm7' (errno=Operation not permitted)
2026-09-08 21:25:44.529 xcodebuild[24212:265677414]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:46.499 xcodebuild[24212:265677412] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-lDN9NO7r' (errno=Operation not permitted)
2026-09-08 21:25:49.825 xcodebuild[24360:265678104]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:25:51.176 xcodebuild[24360:265678101] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
M	ES-FOOTPRINT-CAMPAIGN.json
M	ES-FOOTPRINT-GATEWAY-DESIGN.md
M	scripts/footprint-mutate.py
A	scripts/footprint-reqstate.py
A	scripts/footprint-reqstate.sh

exec
/bin/bash -lc 'GIT_PAGER=cat git --no-optional-locks -c core.fsmonitor=false diff --find-renames --stat 3e13574..HEAD; GIT_PAGER=cat git --no-optional-locks -c core.fsmonitor=false diff --name-status 3e13574..HEAD' in /private/tmp/oe-conf-gw
2026-09-08T19:26:10.740702Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
 succeeded in 17388ms:
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-IRwMXGen' (errno=Operation not permitted)
2026-09-08 21:26:00.911 xcodebuild[24710:265679770]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:26:02.027 xcodebuild[24710:265679758] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-VCX90S6j' (errno=Operation not permitted)
2026-09-08 21:26:04.761 xcodebuild[24812:265680164]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:26:05.905 xcodebuild[24812:265680133] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
 ES-FOOTPRINT-CAMPAIGN.json                         | 1181 ++++++++++++++++----
 ES-FOOTPRINT-GATEWAY-DESIGN.md                     |   30 +
 scripts/footprint-mutate.py                        |   69 +-
 scripts/footprint-reqstate.py                      |   50 +
 scripts/footprint-reqstate.sh                      |   12 +
 .../java/app/feedgateway/FeedGatewayService.java   |   13 +-
 .../app/feedgateway/CvdSpxLevelsWiringTest.java    |    7 +-
 .../java/app/feedgateway/FootprintViewsTest.java   |   20 +
 .../java/app/feedgateway/FootprintWiringTest.java  |   47 +-
 9 files changed, 1220 insertions(+), 209 deletions(-)
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-EKTDTFXh' (errno=Operation not permitted)
2026-09-08 21:26:10.401 xcodebuild[25034:265681158]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:26:11.536 xcodebuild[25034:265681147] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-cfXtbtdc' (errno=Operation not permitted)
2026-09-08 21:26:13.072 xcodebuild[25127:265681581]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:26:13.635 xcodebuild[25127:265681576] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
M	ES-FOOTPRINT-CAMPAIGN.json
M	ES-FOOTPRINT-GATEWAY-DESIGN.md
M	scripts/footprint-mutate.py
A	scripts/footprint-reqstate.py
A	scripts/footprint-reqstate.sh
M	src/main/java/app/feedgateway/FeedGatewayService.java
M	src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
M	src/test/java/app/feedgateway/FootprintViewsTest.java
M	src/test/java/app/feedgateway/FootprintWiringTest.java

2026-09-08T19:26:17.418010Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc 'GIT_PAGER=cat git --no-optional-locks -c core.fsmonitor=false diff 3e13574..HEAD -- src/main/java/app/feedgateway/FeedGatewayService.java src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java src/test/java/app/feedgateway/FootprintViewsTest.java src/test/java/app/feedgateway/FootprintWiringTest.java scripts/footprint-reqstate.py scripts/footprint-reqstate.sh ES-FOOTPRINT-GATEWAY-DESIGN.md' in /private/tmp/oe-conf-gw
 succeeded in 5854ms:
2026-09-08 21:26:21.369 xcodebuild[25369:265682299]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:26:21.964 xcodebuild[25369:265682295] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-zyoTFCo7' (errno=Operation not permitted)
2026-09-08 21:26:23.235 xcodebuild[25463:265682550]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 21:26:23.811 xcodebuild[25463:265682549] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
diff --git a/ES-FOOTPRINT-GATEWAY-DESIGN.md b/ES-FOOTPRINT-GATEWAY-DESIGN.md
index 315acad..f7606b7 100644
--- a/ES-FOOTPRINT-GATEWAY-DESIGN.md
+++ b/ES-FOOTPRINT-GATEWAY-DESIGN.md
@@ -161,6 +161,36 @@ under last-write-per-key. Epoch-ms values lie in `[0, 253402300799999]` (§5 tim
 | G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
 | G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |
 
+<!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->
+
+## 2a. Conformance — what a test actually holds
+
+Every requirement above, and what this repository's mutation campaign established about it. A clause
+is "pinned" when breaking it in the production source made a NAMED test fail; the campaign refuses to
+start against a dirty tree or a red baseline, and each row's evidence — the patch, the file, line and
+enclosing declaration, the command, the exit code, the failing test names and a SHA-256 of the run
+output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside this document. Regenerate with
+`scripts/footprint-reqstate.sh`; the full per-clause matrix is `ES-FOOTPRINT-CONFORMANCE.md`.
+
+| id | Conformance | Gate | Disposition |
+|----|-------------|------|-------------|
+| G-R1 | 1/1 pinned | 2 | Flag and wiring |
+| G-R2 | 2/2 pinned | 2 | Flag and wiring |
+| G-R3 | 3/3 pinned | 2 | Delivery and views |
+| G-R4 | 4/4 pinned | 2 | Delivery and views |
+| G-R5 | 4/4 pinned | 2 | Delivery and views |
+| G-R6 | 3/3 pinned | 2 | Hello |
+| G-R7 | 5/6 pinned — 1 characterised | 2 | Backfill routes |
+| G-R8 | 5/5 pinned | 2 | Deployment contingency |
+| G-R9 | 1/1 pinned | 2 | Metrics |
+| G-R10 | 1/1 pinned | 2 | Non-interference |
+| G-R11 | TEST INVENTORY — its items are the tests named in the rows above | 2 | Tests |
+| G-R8a | 2/2 pinned | 2 | Deployment contingency |
+
+12 requirements; 11 probed by 32 mutations (31 killed, 1 surviving). "n/n pinned" means every clause this campaign broke in that requirement made a NAMED test fail. "NOT PROBED" means this campaign did not test it and claims nothing either way. Evidence, per mutation, is in the campaign record beside this document.
+
+<!-- END footprint-reqstate -->
+
 ## 3. Decisions
 
 - **D1 No live retention.** `es-footprint`/`es-footprint-evidence` are not retained for connect
diff --git a/scripts/footprint-reqstate.py b/scripts/footprint-reqstate.py
new file mode 100644
index 0000000..640ad6d
--- /dev/null
+++ b/scripts/footprint-reqstate.py
@@ -0,0 +1,50 @@
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
+            state = notes.get(rid, "NOT PROBED")
+        else:
+            killed = sum(1 for m in ms if m['status'] == 'KILLED')
+            state = f"{killed}/{len(ms)} pinned" + ("" if killed == len(ms) else f" — {len(ms)-killed} characterised")
+        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
+    tot = collections.Counter(v['status'] for v in rec.values())
+    out.append("")
+    out.append(f"{len(ids)} requirements; {len(per)} probed by {len(rec)} mutations "
+               f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving). "
+               "\"n/n pinned\" means every clause this campaign broke in that requirement made a NAMED "
+               "test fail. \"NOT PROBED\" means this campaign did not test it and claims nothing "
+               "either way. Evidence, per mutation, is in the campaign record beside this document.")
+    print("\n".join(out))
+
+main()
diff --git a/scripts/footprint-reqstate.sh b/scripts/footprint-reqstate.sh
new file mode 100755
index 0000000..f17189b
--- /dev/null
+++ b/scripts/footprint-reqstate.sh
@@ -0,0 +1,12 @@
+#!/usr/bin/env bash
+# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md from the checked-in campaign record.
+# Run from the repository root; the output replaces the block between the two markers.
+set -euo pipefail
+cd "$(dirname "$0")/.."
+python3 scripts/footprint-reqstate.py \
+  ES-FOOTPRINT-CAMPAIGN.json \
+  ES-FOOTPRINT-GATEWAY-DESIGN.md \
+  'G-R\d+[a-z]?' \
+  '{"G-R.*":"2"}' \
+  '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
+  '{"G-R11":"TEST INVENTORY — its items are the tests named in the rows above"}'
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

2026-09-08T19:26:27.032674Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
2026-09-08T19:26:31.558825Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "python3 -c 'import json; r=json.load(open(\"ES-FOOTPRINT-CAMPAIGN.json\")); print(type(r),len(r)); [(print(\"\\nKEY\",k),print(json.dumps(v,indent=2))) for k,v in r.items() if any(x in json.dumps(v)+k for x in [\"theWiringMethodPutsAllFourBindings\",\"liveSnapshotsAreNeverRetained\",\"theHelloIsSentWhenFootprintAlone\",\"anOutcomeRedeliveryOverwrites\",\"aNullPayloadIsARefusal\",\"connectReplayRidesInside\",\"bootstrapAndLiveConsumers\"])]'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
<class 'dict'> 32

KEY G-R1.1 the-flag-gates-every-footprint-object
{
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
    "FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns",
    "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
    "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"
  ],
  "seconds": 24.5,
  "patch": {
    "old": "        if (settings.esFootprintEnabled()) {",
    "new": "        if (true) {"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "884cdb7be1fa827c909b4825d3adcbd701327d0771191be49d202c08cf1a7723",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
      "src/main/java/app/feedgateway/FeedGatewayService.java": {
        "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
        "mutated": "884cdb7be1fa827c909b4825d3adcbd701327d0771191be49d202c08cf1a7723"
      }
    },
    "outputSha256": "c8203d91612ee086ca3067c03c928d8e0e1efc8a9abc54e703b7b9da8d271e8c",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.005 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.002 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.007 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 1.144 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.015 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.926 s <<< ERROR!",
      "[ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0.001 s <<< FAILURE!",
      "[ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401>",
      "[ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123",
      "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]>",
      "[ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true>",
      "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true>"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

KEY G-R2.1 one-wiring-method-binds-all-four
{
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
  "seconds": 21.0,
  "patch": {
    "old": "        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));",
    "new": "        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
      "src/main/java/app/feedgateway/FeedGatewayService.java": {
        "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
        "mutated": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817"
      }
    },
    "outputSha256": "ac47343bc1d93ff8be55ec0c02756982770a5c9a1fefb9dce37b6a459b83473a",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.011 s <<< FAILURE!",
      "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

KEY G-R2.2 the-evidence-binding
{
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
  "seconds": 15.0,
  "patch": {
    "old": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-evidence\"));",
    "new": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint\"));"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
      "src/main/java/app/feedgateway/FeedGatewayService.java": {
        "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
        "mutated": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8"
      }
    },
    "outputSha256": "2e86d300a9f41306b0806945d646409a7632958377b2d18fec95bc556c980c94",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.012 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE!",
      "[ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint\" ==> expected: <1> but was: <2>",
      "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

KEY G-R3.3 live-snapshots-are-never-retained
{
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
  "seconds": 23.9,
  "patch": {
    "old": "            return CachePolicy.expiring(0L);",
    "new": "            return CachePolicy.expiring(settings.esFootprintSeekBackMs());"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "606a7fa3701572b9a8978bf6c9c8a0a027c1e56e2dcca908b19ff84993edc72f",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
      "src/main/java/app/feedgateway/FeedGatewayService.java": {
        "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
        "mutated": "606a7fa3701572b9a8978bf6c9c8a0a027c1e56e2dcca908b19ff84993edc72f"
      }
    },
    "outputSha256": "08ab81a454386302f471f5157830ddfcd29e4eed35a473935ab6df9edb19625e",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.021 s <<< FAILURE!",
      "[ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

KEY G-R5.1 the-outcome-key-orders-numerically
{
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
    "FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck",
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace",
    "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks"
  ],
  "seconds": 21.4,
  "patch": {
    "old": "        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;",
    "new": "        return tf + \"|\" + resolvedAtBarStartMs + \"|\" + identity;"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
      "src/main/java/app/feedgateway/FootprintViews.java": {
        "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
        "mutated": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec"
      }
    },
    "outputSha256": "51bcd8716634b8fbc57cdc4c36dd231596d50d014c57be5e372d1f1bccffe0b2",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.200 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.006 s <<< ERROR!",
      "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.004 s <<< ERROR!",
      "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< ERROR!",
      "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.002 s <<< ERROR!",
      "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8>",
      "[ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000>",
      "[ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 \u00bb NumberFormat For input string: \"10|ES.v.0|1m|1|BUYE\"",
      "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 \u00bb NumberFormat For input string: \"5|ES.v.0|1m|1|BUYER\"",
      "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:135 \u00bb NumberFormat For input string: \"77|ES.v.0|5m|1|SELL\"",
      "[ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:211 \u00bb StringIndexOutOfBounds Range [3, 22) out of bounds for length 8"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

KEY G-R5.2 the-identity-is-the-last-component
{
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
    "FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck",
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace",
    "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks"
  ],
  "seconds": 23.5,
  "patch": {
    "old": "        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;",
    "new": "        return tf + \"|\" + identity + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs);"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "7a75b9af663605c79e061f5994d60b4ec216178fbe6ad27e44f8cc7f76c49e57",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
      "src/main/java/app/feedgateway/FootprintViews.java": {
        "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
        "mutated": "7a75b9af663605c79e061f5994d60b4ec216178fbe6ad27e44f8cc7f76c49e57"
      }
    },
    "outputSha256": "89b4e014bcccb459311c8959fe949dbf2b1c297f379c745d7f00bea6fab89ff7",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.216 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.014 s <<< ERROR!",
      "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR!",
      "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.002 s <<< FAILURE!",
      "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR!",
      "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.010 s <<< ERROR!",
      "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: \"id0|000000000000000\">",
      "[ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005>",
      "[ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\"",
      "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\"",
      "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:135 \u00bb NumberFormat For input string: \"z|00000000000000000\"",
      "[ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:211 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\""
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

KEY G-R5.4 last-write-per-key-wins
{
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
  "seconds": 21.7,
  "patch": {
    "old": "            String previous = outcomes.put(key, json);",
    "new": "            String previous = outcomes.putIfAbsent(key, json);"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "1a29ff784d9ae87c27fa6256fe064b480ac945623b6facdba5238d69fa17937f",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
      "src/main/java/app/feedgateway/FootprintViews.java": {
        "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
        "mutated": "1a29ff784d9ae87c27fa6256fe064b480ac945623b6facdba5238d69fa17937f"
      }
    },
    "outputSha256": "65746174d0187c34eaa92b8303c3c635295fbf05c631cf10d6f0e2e8bd9216cf",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.011 s <<< FAILURE!",
      "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

KEY G-R6.2 the-hello-is-sent-when-footprint-alone-is-on
{
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
  "seconds": 18.0,
  "patch": {
    "old": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;",
    "new": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
    "filesSha256": {
      "src/main/java/app/feedgateway/FeedGatewayService.java": {
        "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
        "mutated": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc"
      }
    },
    "outputSha256": "642553afc2f92db41c81861944d13c316e637dd6323bc0cee556f928fc9d653c",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.009 s <<< FAILURE!",
      "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

KEY G-R10.1 a-footprint-record-never-escapes-the-consumer-loop
{
  "status": "KILLED",
  "clause": "A footprint record can never throw out of the consumer loop (every admission failure is a counted drop)",
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
  "seconds": 21.8,
  "patch": {
    "old": "        if (json == null) return Admission.SHAPE;",
    "new": "        if (json == null) throw new IllegalArgumentException(\"json\");"
  },
  "evidence": {
    "repoCommit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "returnCode": 1,
    "mutatedFileSha256": "5b915b8626df30f329a936e4fceef00fce3d32c66a3f820c7439a0cd381d7650",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
      "src/main/java/app/feedgateway/FootprintViews.java": {
        "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
        "mutated": "5b915b8626df30f329a936e4fceef00fce3d32c66a3f820c7439a0cd381d7650"
      }
    },
    "outputSha256": "0d92903a4308aef42ac719f722743fa6ea2f12a063df374ea7788d31eec4e58e",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
      "[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.005 s <<< ERROR!",
      "[ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:82 \u00bb IllegalArgument json"
    ],
    "treeRestoredClean": true
  },
  "baseline": {
    "commit": "1224cec71da2dbbb5bebc1b8486479182d727a83",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
    "outputSha256": "9d4a60eafd8e69cb2ffacaed473f669ce3a53a726e02448afffb3ab25281b26b",
    "outputTail": "instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\n"
  }
}

2026-09-08T19:26:35.584206Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "nl -ba src/test/java/app/feedgateway/FootprintWiringTest.java | sed -n '1,230p'; nl -ba src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java | sed -n '305,355p'; nl -ba src/main/java/app/feedgateway/FeedGatewayService.java | sed -n '1170,1225p;2360,2410p;6460,6555p;11135,11180p'; nl -ba src/main/java/app/feedgateway/FootprintViews.java | sed -n '60,160p'" in /private/tmp/oe-conf-gw
 succeeded in 10ms:
    60	    private final int barsMaxCount;
    61	    private final long outcomesMaxBytes;
    62	    private final int outcomesMaxCount;
    63	
    64	    private final Object lock = new Object();
    65	    private LocalDate sessionDate;                                   // null before the first admitted record
    66	    private final TreeMap<String, String> bars = new TreeMap<>();
    67	    private final TreeMap<String, String> outcomes = new TreeMap<>();
    68	    private long barsBytes;
    69	    private long outcomesBytes;
    70	    private int barsEvictCursor;
    71	    private int outcomesEvictCursor;
    72	    private final AtomicLong barsEvictions = new AtomicLong();
    73	    private final AtomicLong outcomesEvictions = new AtomicLong();
    74	    private final AtomicLong rollovers = new AtomicLong();
    75	
    76	    FootprintViews(ObjectMapper mapper, long maxRecordBytes, long barsMaxBytes, int barsMaxCount,
    77	                   long outcomesMaxBytes, int outcomesMaxCount) {
    78	        if (maxRecordBytes <= 0 || barsMaxBytes <= 0 || barsMaxCount <= 0 || outcomesMaxBytes <= 0 || outcomesMaxCount <= 0) {
    79	            throw new IllegalArgumentException("footprint view budgets must be positive");
    80	        }
    81	        this.mapper = mapper;
    82	        this.maxRecordBytes = maxRecordBytes;
    83	        this.barsMaxBytes = barsMaxBytes;
    84	        this.barsMaxCount = barsMaxCount;
    85	        this.outcomesMaxBytes = outcomesMaxBytes;
    86	        this.outcomesMaxCount = outcomesMaxCount;
    87	    }
    88	
    89	    // ---- admission --------------------------------------------------------------------------------
    90	
    91	    /** Closed-bar record (G-R4): key {@code timeframe|observations.barStartMs}. */
    92	    Admission admitBar(String json) {
    93	        if (json == null) return Admission.SHAPE;
    94	        int bytes = utf8Length(json);
    95	        if (bytes > maxRecordBytes) return Admission.OVERSIZE;
    96	        JsonNode root = parse(json);
    97	        if (root == null) return Admission.SHAPE;
    98	        LocalDate date = parseCanonicalDate(text(root, "sessionDate"));
    99	        String tf = text(root, "timeframe");
   100	        long start = epochField(root.path("observations"), "barStartMs");
   101	        if (date == null || !TIMEFRAME_KEY.matcher(tf).matches() || start < 0) return Admission.SHAPE;
   102	        String key = barKey(tf, start);
   103	        synchronized (lock) {
   104	            Boolean roll = rollover(date);
   105	            if (roll == null) return Admission.STALE;
   106	            String previous = bars.put(key, json);
   107	            barsBytes += bytes - (previous == null ? 0 : utf8Length(previous));
   108	            int evicted = enforce(bars, barsMaxBytes, barsMaxCount, true);
   109	            return new Admission(Reason.ADMITTED, roll, evicted);
   110	        }
   111	    }
   112	
   113	    /** Outcome record (G-R5): key {@code timeframe|resolvedAtBarStartMs|identity}. */
   114	    Admission admitOutcome(String json) {
   115	        if (json == null) return Admission.SHAPE;
   116	        int bytes = utf8Length(json);
   117	        if (bytes > maxRecordBytes) return Admission.OVERSIZE;
   118	        JsonNode root = parse(json);
   119	        if (root == null) return Admission.SHAPE;
   120	        LocalDate date = parseCanonicalDate(text(root, "sessionDate"));
   121	        String tf = text(root, "timeframe");
   122	        String identity = text(root, "identity");
   123	        long resolved = epochField(root, "resolvedAtBarStartMs");
   124	        if (date == null || !TIMEFRAME_KEY.matcher(tf).matches() || resolved < 0
   125	                || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {
   126	            return Admission.SHAPE;
   127	        }
   128	        String key = outcomeKey(tf, resolved, identity);
   129	        synchronized (lock) {
   130	            Boolean roll = rollover(date);
   131	            if (roll == null) return Admission.STALE;
   132	            String previous = outcomes.put(key, json);
   133	            outcomesBytes += bytes - (previous == null ? 0 : utf8Length(previous));
   134	            int evicted = enforce(outcomes, outcomesMaxBytes, outcomesMaxCount, false);
   135	            return new Admission(Reason.ADMITTED, roll, evicted);
   136	        }
   137	    }
   138	
   139	    /**
   140	     * Under the lock. Returns TRUE when the date rolled the session forward (both views cleared),
   141	     * FALSE when it is the current (or first) session, NULL when it is older (drop).
   142	     */
   143	    private Boolean rollover(LocalDate date) {
   144	        if (sessionDate == null) { sessionDate = date; return false; }
   145	        int cmp = date.compareTo(sessionDate);
   146	        if (cmp < 0) return null;
   147	        if (cmp == 0) return false;
   148	        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;
   149	        sessionDate = date;
   150	        rollovers.incrementAndGet();
   151	        return true;
   152	    }
   153	
   154	    /**
   155	     * Evict the OLDEST key of one timeframe at a time, ROTATING across the timeframes present (the
   156	     * cursor persists across admissions), until the view fits both budgets — so a run of single
   157	     * evictions spreads over every timeframe instead of draining the lexically first one. Under the lock.
   158	     */
   159	    private int enforce(TreeMap<String, String> view, long maxBytes, int maxCount, boolean isBars) {
   160	        int evicted = 0;

2026-09-08T19:26:44.676858Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc 'scripts/footprint-reqstate.sh > /dev/null; scripts/footprint-reqstate.sh' in /private/tmp/oe-conf-gw
 succeeded in 126ms:
| id | Conformance | Gate | Disposition |
|----|-------------|------|-------------|
| G-R1 | 1/1 pinned | 2 | Flag and wiring |
| G-R2 | 2/2 pinned | 2 | Flag and wiring |
| G-R3 | 3/3 pinned | 2 | Delivery and views |
| G-R4 | 4/4 pinned | 2 | Delivery and views |
| G-R5 | 4/4 pinned | 2 | Delivery and views |
| G-R6 | 3/3 pinned | 2 | Hello |
| G-R7 | 5/6 pinned — 1 characterised | 2 | Backfill routes |
| G-R8 | 5/5 pinned | 2 | Deployment contingency |
| G-R9 | 1/1 pinned | 2 | Metrics |
| G-R10 | 1/1 pinned | 2 | Non-interference |
| G-R11 | TEST INVENTORY — its items are the tests named in the rows above | 2 | Tests |
| G-R8a | 2/2 pinned | 2 | Deployment contingency |

12 requirements; 11 probed by 32 mutations (31 killed, 1 surviving). "n/n pinned" means every clause this campaign broke in that requirement made a NAMED test fail. "NOT PROBED" means this campaign did not test it and claims nothing either way. Evidence, per mutation, is in the campaign record beside this document.

2026-09-08T19:26:54.831366Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "python3 -c 'import json; r=json.load(open(\"ES-FOOTPRINT-CAMPAIGN.json\")); ks=[\"G-R2.1 one-wiring-method-binds-all-four\",\"G-R3.3 live-snapshots-are-never-retained\",\"G-R5.4 last-write-per-key-wins\",\"G-R6.2 the-hello-is-sent-when-footprint-alone-is-on\",\"G-R10.1 a-footprint-record-never-escapes-the-consumer-loop\"]; [(print(k),print(\" patch\",v[\"patch\"]),print(\" killedBy\",v[\"killedBy\"]),print(\" failures\",v[\"evidence\"][\"failureLines\"])) for k,v in r.items() if k in ks]'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
G-R2.1 one-wiring-method-binds-all-four
 patch {'old': '        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));', 'new': '        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));'}
 killedBy ['FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven']
 failures ['[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.011 s <<< FAILURE!', '[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot']
G-R3.3 live-snapshots-are-never-retained
 patch {'old': '            return CachePolicy.expiring(0L);', 'new': '            return CachePolicy.expiring(settings.esFootprintSeekBackMs());'}
 killedBy ['FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession']
 failures ['[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.021 s <<< FAILURE!', '[ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>']
G-R5.4 last-write-per-key-wins
 patch {'old': '            String previous = outcomes.put(key, json);', 'new': '            String previous = outcomes.putIfAbsent(key, json);'}
 killedBy ['FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace']
 failures ['[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.011 s <<< FAILURE!', '[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>']
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on
 patch {'old': '        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;', 'new': '        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();'}
 killedBy ['FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled']
 failures ['[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.009 s <<< FAILURE!', '[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>']
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop
 patch {'old': '        if (json == null) return Admission.SHAPE;', 'new': '        if (json == null) throw new IllegalArgumentException("json");'}
 killedBy ['FootprintViewsTest.aNullPayloadIsARefusalNotAThrow']
 failures ['[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.005 s <<< ERROR!', '[ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:82 » IllegalArgument json']

2026-09-08T19:27:03.646355Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
The five new pins do kill their recorded edits, but two important scope distinctions are emerging: one test proves a specific null guard, not the full “no admission failure escapes” clause; and §2a aggregates “mutations killed” into “clauses pinned,” which is stronger than the evidence permits. I’m checking whether those are isolated wording defects or symptoms of broader record-generation problems.
exec
/bin/bash -lc 'mvn -B test -Dtest=FootprintWiringTest,FootprintViewsTest,CvdSpxLevelsWiringTest -DfailIfNoTests=false' in /private/tmp/oe-conf-gw
2026-09-08T19:27:10.754084Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
 exited 1 in 6625ms:
/var/tmp/jansi-2.4.3-16bb9f021ab6577b-libjansi.jnilib.lck (Operation not permitted)
[INFO] Scanning for projects...
[INFO] 
[INFO] -------------------< app:options-edge-feed-gateway >--------------------
[INFO] Building OptionsEdge Feed Gateway 1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ options-edge-feed-gateway ---
[INFO] Copying 1 resource from src/main/resources to target/classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.917 s
[INFO] Finished at: 2026-09-08T21:27:16+02:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-resources-plugin:3.3.1:resources (default-resources) on project options-edge-feed-gateway: filtering /private/tmp/oe-conf-gw/src/main/resources/application.properties to /private/tmp/oe-conf-gw/target/classes/application.properties failed with FileNotFoundException: /private/tmp/oe-conf-gw/target/classes/application.properties (Operation not permitted) -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoExecutionException

2026-09-08T19:27:16.248151Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "python3 -c 'import json,collections,re,hashlib; r=json.load(open(\"ES-FOOTPRINT-CAMPAIGN.json\")); print(collections.Counter(v.get(\"status\") for v in r.values())); print(\"killed missing named\",[k for k,v in r.items() if v.get(\"status\")==\"KILLED\" and not v.get(\"killedBy\")]); print(\"killedBy not failure\",[(k,n) for k,v in r.items() for n in v.get(\"killedBy\",[]) if not any(n in x for x in v.get(\"evidence\",{}).get(\"failureLines\",[]))]); print(\"requirements\",collections.Counter(v.get(\"requirement\") for v in r.values())); print(\"commits\",collections.Counter(v.get(\"evidence\",{}).get(\"repoCommit\") for v in r.values()))'" in /private/tmp/oe-conf-gw
 succeeded in 3ms:
2026-09-08T19:27:21.563111Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "rg -n \"cachePolicyFor\\(|sendsCvdHello\\(|addEsFootprintTopics\\(\" src/main/java/app/feedgateway/FeedGatewayService.java; rg -n \"CachePolicy|ttlMs\\(\\)|seekBackMs\\(\\)|neverEvict\\(\" src/main/java/app/feedgateway/FeedGatewayService.java | head -80; sed -n '1,150p' scripts/footprint-mutate.py; sed -n '150,330p' scripts/footprint-mutate.py" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
1195:        if (sendsCvdHello()) {
2223:        addEsFootprintTopics(topicEvents);
2361:        addEsFootprintTopics(topicEvents);
2387:    void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {   // package-private: the wiring test EXECUTES it
3047:                return cachePolicyFor(binding.event(), nowMs).seekBackMs();
6526:    CachePolicy cachePolicyFor(String event, long nowMs) {   // package-private: the retention test EXECUTES it
6721:        CachePolicy policy = cachePolicyFor(event, nowMs);
11157:    boolean sendsCvdHello() {
3047:                return cachePolicyFor(binding.event(), nowMs).seekBackMs();
6488:    record CachePolicy(long ttlMs, boolean neverEvict, long seekBackMs) {   // package-private: the retention test reads it
6489:        static CachePolicy expiring(long ttlMs) {
6490:            return new CachePolicy(ttlMs, false, ttlMs);
6493:        static CachePolicy noEviction(long seekBackMs) {
6494:            return new CachePolicy(0L, true, seekBackMs);
6526:    CachePolicy cachePolicyFor(String event, long nowMs) {   // package-private: the retention test EXECUTES it
6530:            return CachePolicy.expiring(settings.esFootprintSeekBackMs());
6534:            return CachePolicy.expiring(0L);
6537:            return CachePolicy.expiring(settings.optionTruthTtlMs());
6542:            return CachePolicy.expiring(settings.zeroDteIntelligenceTtlMs());
6545:            return CachePolicy.expiring(settings.maxPainTtlMs());
6551:            return CachePolicy.expiring(settings.hotStrikeTtlMs());
6557:            return CachePolicy.expiring(settings.shortPremiumRecommendationTtlMs());
6566:            return CachePolicy.expiring(settings.esOpenDirectionStatusTtlMs());
6575:            return CachePolicy.expiring(settings.greekMoveAuthTtlMs());
6580:            return CachePolicy.expiring(settings.gammaLeadershipTtlMs());
6584:            return CachePolicy.expiring(settings.directionTtlMs());
6587:            return CachePolicy.expiring(settings.directionPushTtlMs());       // A4.8: 30 s on both stamps
6590:            return CachePolicy.expiring(settings.directionAlertTtlMs());      // A4.10: 60 s
6593:            return CachePolicy.expiring(settings.directionScorecardTtlMs());
6600:            return CachePolicy.expiring(settings.spotVolRegimeTtlMs());
6606:            return CachePolicy.expiring(settings.volPremiumIvrvTtlMs());
6611:            return CachePolicy.expiring(settings.indicatorsTtlMs());
6619:            return CachePolicy.expiring(settings.closeDirectionTtlMs());
6626:            return CachePolicy.expiring(settings.esOpenDirectionTtlMs());
6632:            return CachePolicy.expiring(settings.gexByStrikeTtlMs());
6637:            return CachePolicy.expiring(settings.gexOiStatusTtlMs());
6642:            return CachePolicy.expiring(settings.ibkrPreOpenStatusTtlMs());
6650:            return CachePolicy.expiring(settings.tapeZonesTtlMs());
6656:            return CachePolicy.expiring(settings.esGexTtlMs());
6662:            return CachePolicy.expiring(settings.esStrikeIntelTtlMs());
6669:            return CachePolicy.expiring(settings.strikeIntelTtlMs());
6675:            return CachePolicy.expiring(settings.gexStrikeLifecycleTtlMs());
6680:            return CachePolicy.expiring(settings.liquidityHeatmapTtlMs());
6687:            return CachePolicy.expiring(settings.dealerLedgerTtlMs());
6699:            return CachePolicy.noEviction(settings.optionChainOffHoursSeekBackMs());
6703:                return CachePolicy.expiring(settings.optionChainRthCacheTtlMs());
6705:            return CachePolicy.noEviction(settings.optionChainOffHoursSeekBackMs());
6714:            return CachePolicy.noEviction(settings.esAuctionSeekBackMs());
6716:        return CachePolicy.expiring(settings.cacheTtlMs());
6721:        CachePolicy policy = cachePolicyFor(event, nowMs);
6722:        if (policy.neverEvict()) {
6725:        return policy.ttlMs() <= 0 || eventTime < nowMs - policy.ttlMs();
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
import json, subprocess, sys, os, shutil, re, time, hashlib

def failure_lines(out, kind):
    """The VERBATIM lines the killedBy names were parsed from, so the record substantiates itself.
    A tail of the output does not: Maven prints its failures well before the build summary, so the
    last N characters of a run contain none of the names the harness reported."""
    pat = r'^\[ERROR\]\s+[\w.$]+\.[\w$]+(?::\d+|\s+--|\s).*$' if kind == 'maven' else r'^not ok \d+ - .*$'
    return [l.rstrip()[:400] for l in re.findall(pat, out, re.M)][:12]

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

def main():
    spec = json.load(open(sys.argv[1]))
    outp = sys.argv[2]
    root, cmd, kind = spec['root'], spec['command'], spec['kind']
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
        still_dirty = tree_dirty(root)
        failed = failing_tests(out, kind)
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
        elif rc != 0 and failed:
            status = 'KILLED'
        elif rc != 0:
            status = 'BUILD-FAILED'
        else:
            status = 'SURVIVED'
        res[k] = {'status': status, 'clause': m.get('clause',''), 'requirement': m.get('requirement', k.split()[0]),
                  'documentText': m.get('documentText'),
                  'file': m['file'], 'occurrence': occ, 'occurrencesInFile': n, 'line': line,
                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': failed[:4],
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

2026-09-08T19:27:27.578848Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "shasum -a 256 src/main/java/app/feedgateway/FeedGatewayService.java src/main/java/app/feedgateway/FootprintViews.java; python3 -c 'import json,hashlib; r=json.load(open(\"ES-FOOTPRINT-CAMPAIGN.json\")); print({v[\"file\"]:v[\"evidence\"][\"originalFileSha256\"] for v in r.values() if v[\"file\"] in [\"src/main/java/app/feedgateway/FeedGatewayService.java\",\"src/main/java/app/feedgateway/FootprintViews.java\"]})'; nl -ba scripts/footprint-reqstate.py; nl -ba scripts/footprint-reqstate.sh; nl -ba ES-FOOTPRINT-GATEWAY-DESIGN.md | sed -n '164,200p'" in /private/tmp/oe-conf-gw
 succeeded in 117ms:
{'src/main/java/app/feedgateway/FootprintViews.java': '915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96', 'src/main/java/app/feedgateway/FeedGatewayService.java': 'e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75'}
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
    24	    def gate(rid):
    25	        for pat, g in gates.items():
    26	            if re.fullmatch('(?:' + pat + ')', rid): return g
    27	        return '—'
    28	    def disp(rid):
    29	        for pat, d in dispositions.items():
    30	            if re.fullmatch('(?:' + pat + ')', rid): return d
    31	        return ''
    32	    out = ["| id | Conformance | Gate | Disposition |", "|----|-------------|------|-------------|"]
    33	    for rid in sorted(ids, key=lambda x: (re.sub(r'\d', '', x), int(re.search(r'\d+', x).group()), x)):
    34	        ms = per.get(rid, [])
    35	        if not ms:
    36	            state = notes.get(rid, "NOT PROBED")
    37	        else:
    38	            killed = sum(1 for m in ms if m['status'] == 'KILLED')
    39	            state = f"{killed}/{len(ms)} pinned" + ("" if killed == len(ms) else f" — {len(ms)-killed} characterised")
    40	        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
    41	    tot = collections.Counter(v['status'] for v in rec.values())
    42	    out.append("")
    43	    out.append(f"{len(ids)} requirements; {len(per)} probed by {len(rec)} mutations "
    44	               f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving). "
    45	               "\"n/n pinned\" means every clause this campaign broke in that requirement made a NAMED "
    46	               "test fail. \"NOT PROBED\" means this campaign did not test it and claims nothing "
    47	               "either way. Evidence, per mutation, is in the campaign record beside this document.")
    48	    print("\n".join(out))
    49	
    50	main()
     1	#!/usr/bin/env bash
     2	# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md from the checked-in campaign record.
     3	# Run from the repository root; the output replaces the block between the two markers.
     4	set -euo pipefail
     5	cd "$(dirname "$0")/.."
     6	python3 scripts/footprint-reqstate.py \
     7	  ES-FOOTPRINT-CAMPAIGN.json \
     8	  ES-FOOTPRINT-GATEWAY-DESIGN.md \
     9	  'G-R\d+[a-z]?' \
    10	  '{"G-R.*":"2"}' \
    11	  '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
    12	  '{"G-R11":"TEST INVENTORY — its items are the tests named in the rows above"}'
   164	<!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->
   165	
   166	## 2a. Conformance — what a test actually holds
   167	
   168	Every requirement above, and what this repository's mutation campaign established about it. A clause
   169	is "pinned" when breaking it in the production source made a NAMED test fail; the campaign refuses to
   170	start against a dirty tree or a red baseline, and each row's evidence — the patch, the file, line and
   171	enclosing declaration, the command, the exit code, the failing test names and a SHA-256 of the run
   172	output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside this document. Regenerate with
   173	`scripts/footprint-reqstate.sh`; the full per-clause matrix is `ES-FOOTPRINT-CONFORMANCE.md`.
   174	
   175	| id | Conformance | Gate | Disposition |
   176	|----|-------------|------|-------------|
   177	| G-R1 | 1/1 pinned | 2 | Flag and wiring |
   178	| G-R2 | 2/2 pinned | 2 | Flag and wiring |
   179	| G-R3 | 3/3 pinned | 2 | Delivery and views |
   180	| G-R4 | 4/4 pinned | 2 | Delivery and views |
   181	| G-R5 | 4/4 pinned | 2 | Delivery and views |
   182	| G-R6 | 3/3 pinned | 2 | Hello |
   183	| G-R7 | 5/6 pinned — 1 characterised | 2 | Backfill routes |
   184	| G-R8 | 5/5 pinned | 2 | Deployment contingency |
   185	| G-R9 | 1/1 pinned | 2 | Metrics |
   186	| G-R10 | 1/1 pinned | 2 | Non-interference |
   187	| G-R11 | TEST INVENTORY — its items are the tests named in the rows above | 2 | Tests |
   188	| G-R8a | 2/2 pinned | 2 | Deployment contingency |
   189	
   190	12 requirements; 11 probed by 32 mutations (31 killed, 1 surviving). "n/n pinned" means every clause this campaign broke in that requirement made a NAMED test fail. "NOT PROBED" means this campaign did not test it and claims nothing either way. Evidence, per mutation, is in the campaign record beside this document.
   191	
   192	<!-- END footprint-reqstate -->
   193	
   194	## 3. Decisions
   195	
   196	- **D1 No live retention.** `es-footprint`/`es-footprint-evidence` are not retained for connect
   197	  replay: the producer's 5 s heartbeat bounds the wait and the CVD page already lives with the same
   198	  bound. Retention would add the SPX-levels attestation machinery for no page benefit.
   199	- **D2 One hello.** A second `footprint-hello` frame would give the page two handshakes to order;
   200	  one field inside the existing frame keeps the R46 protocol single.

exec
/bin/bash -lc 'test -e ES-FOOTPRINT-CONFORMANCE.md; echo $?; git ls-tree -r --name-only HEAD 2>/dev/null | rg '"'ES-FOOTPRINT-CONFORMANCE' || true" in /private/tmp/oe-conf-gw
 succeeded in 7460ms:
codex
## Findings

1. **§2a contains unsupported and false generation claims.**  
   [ES-FOOTPRINT-GATEWAY-DESIGN.md:172](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:172) says `scripts/footprint-reqstate.sh` regenerates §2a, but the script only prints Markdown to stdout; it never replaces the marked block. The same paragraph points to `ES-FOOTPRINT-CONFORMANCE.md`, which does not exist.

2. **G-R11 is not derived from the campaign record.**  
   [footprint-reqstate.sh:12](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.sh:12) injects the G-R11 text manually through `notes`. The record contains zero G-R11 mutations. Moreover, “its items are the tests named in the rows above” is false: the generated rows contain counts, not test names, and the record’s `killedBy` names do not constitute the complete 17-item G-R11 inventory. It should say that G-R11 is not probed/inventory-only, without claiming derivation or completeness.

3. **The generator can emit claims the record does not support.**  
   [footprint-reqstate.py:38](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:38) treats every `status == KILLED` entry as pinned without requiring a nonempty `killedBy`, corresponding `failureLines`, successful baseline, test-run evidence, or valid evidence hashes. Every other status—including `BUILD-FAILED` and `ANCHOR-MISSING`—is misleadingly described as “characterised.” It also permits arbitrary injected notes such as G-R11 and counts record requirements absent from the rendered document in `len(per)`. Thus matching current output does not establish record-supported output.

4. **G-R10’s “1/1 pinned” wording materially overclaims.**  
   [FootprintViewsTest.java:80](/private/tmp/oe-conf-gw/src/test/java/app/feedgateway/FootprintViewsTest.java:80) proves only that direct `admitBar(null)` and `admitOutcome(null)` return `SHAPE`. The recorded mutation changes only the bar null guard to throw. The test does not exercise either consumer loop, the service admission wrapper, drop accounting, or the “coordinator lock is never held while sending” portion of G-R10. Consequently [the G-R10 row](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:186) cannot reasonably present the requirement as pinned.

## Production behavior

No runtime behavior changed. The new `sendsCvdHello()` returns exactly the former condition and replaces it at the sole send site:

```java
settings.esCvdEnabled()
    || settings.esCvdSpxLevelsEnabled()
    || footprintViews != null
```

The other changes only widen Java/package visibility. That does change the production class’s package-level callable surface—tests and any same-package production class can now invoke `addEsFootprintTopics`, `cachePolicyFor`, and construct/read `CachePolicy`—but it does not change execution behavior in the current source.

The checked-in source SHA-256 values exactly match the campaign record’s originals for both mutated production files.

## Five new pins

- `theWiringMethodPutsAllFourBindingsInTheMapItIsGiven`: correctly kills the recorded unreachable-outcomes mutation. It verifies the exact four topic→event mappings, all four `DATABENTO` sources, and flag-off emptiness. It does not prove that either consumer calls the method; the companion source pin does that.
- `liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession`: correctly kills `expiring(0)` → `expiring(seekBack)` and checks both live and keyed event classes. It tests the policy seam, not actual seek/cache execution, and does not explicitly assert `neverEvict == false` or `seekBackMs == 0` for live snapshots.
- `theHelloIsSentWhenFootprintAloneIsEnabled`: correctly kills removal of the footprint disjunct, but exercises only the predicate. Actual transmission remains dependent on a source-text test; no behavioral test observes a WebSocket `cvd-hello`.
- `anOutcomeRedeliveryOverwritesItsKeyInPlace`: cleanly and specifically kills `put` → `putIfAbsent`; it proves one row remains and the second payload survives.
- `aNullPayloadIsARefusalNotAThrow`: specifically kills the bar-null throw mutation, but its name/comment exceed its evidence because it does not verify service-level refusal, counted drops, or consumer-loop containment.

None of the five recorded failures appears accidental or caused solely by an unrelated assertion. The campaign’s stored failure lines name the expected test and assertion for each mutation.

## Re-aimed source pins

- `bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics` was only re-anchored from `private void` to the package-private signature. Its two-call-site, adjacency, flag guard, four-binding, and unconditional-discovery assertions remain intact. No weakening found.
- `connectReplayRidesInsideTheHelloContract` still establishes that `addClient` guards the hello send with the named predicate and that the predicate mentions the SPX-level flag. It is no weaker than its former literal-text assertion, but both versions are structurally weak: a predicate such as `true || settings.esCvdSpxLevelsEnabled()` would pass. It also does not independently preserve the ordinary CVD disjunct.

## Missing probes for these requirements

The campaign should additionally probe:

- removing or bypassing each of the two `addEsFootprintTopics` call sites, with behavioral construction of both consumer topic maps;
- live `seekBackMs`, `neverEvict`, and actual cache/seek behavior—not only `ttlMs`;
- observing `cvd-hello` on a fake WebSocket for footprint-only, CVD-only, SPX-only, and all-disabled configurations;
- null bar and null outcome independently through `admitFootprintRecord`, asserting the appropriate drop metric and no exception escaping the live/cache loop;
- the remaining G-R10 promise that sending never occurs while the coordinator lock is held;
- a redelivery with a different byte length, verifying byte accounting as well as value/count replacement.

I could not rerun Maven because the managed workspace forbids writes to `target/classes`; Maven stopped during resource copying before tests. The checked-in campaign records a green baseline and the relevant named failures, and its original production-file hashes match this checkout.

VERDICT: REQUEST_CHANGES
tokens used
58.412
## Findings

1. **§2a contains unsupported and false generation claims.**  
   [ES-FOOTPRINT-GATEWAY-DESIGN.md:172](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:172) says `scripts/footprint-reqstate.sh` regenerates §2a, but the script only prints Markdown to stdout; it never replaces the marked block. The same paragraph points to `ES-FOOTPRINT-CONFORMANCE.md`, which does not exist.

2. **G-R11 is not derived from the campaign record.**  
   [footprint-reqstate.sh:12](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.sh:12) injects the G-R11 text manually through `notes`. The record contains zero G-R11 mutations. Moreover, “its items are the tests named in the rows above” is false: the generated rows contain counts, not test names, and the record’s `killedBy` names do not constitute the complete 17-item G-R11 inventory. It should say that G-R11 is not probed/inventory-only, without claiming derivation or completeness.

3. **The generator can emit claims the record does not support.**  
   [footprint-reqstate.py:38](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:38) treats every `status == KILLED` entry as pinned without requiring a nonempty `killedBy`, corresponding `failureLines`, successful baseline, test-run evidence, or valid evidence hashes. Every other status—including `BUILD-FAILED` and `ANCHOR-MISSING`—is misleadingly described as “characterised.” It also permits arbitrary injected notes such as G-R11 and counts record requirements absent from the rendered document in `len(per)`. Thus matching current output does not establish record-supported output.

4. **G-R10’s “1/1 pinned” wording materially overclaims.**  
   [FootprintViewsTest.java:80](/private/tmp/oe-conf-gw/src/test/java/app/feedgateway/FootprintViewsTest.java:80) proves only that direct `admitBar(null)` and `admitOutcome(null)` return `SHAPE`. The recorded mutation changes only the bar null guard to throw. The test does not exercise either consumer loop, the service admission wrapper, drop accounting, or the “coordinator lock is never held while sending” portion of G-R10. Consequently [the G-R10 row](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:186) cannot reasonably present the requirement as pinned.

## Production behavior

No runtime behavior changed. The new `sendsCvdHello()` returns exactly the former condition and replaces it at the sole send site:

```java
settings.esCvdEnabled()
    || settings.esCvdSpxLevelsEnabled()
    || footprintViews != null
```

The other changes only widen Java/package visibility. That does change the production class’s package-level callable surface—tests and any same-package production class can now invoke `addEsFootprintTopics`, `cachePolicyFor`, and construct/read `CachePolicy`—but it does not change execution behavior in the current source.

The checked-in source SHA-256 values exactly match the campaign record’s originals for both mutated production files.

## Five new pins

- `theWiringMethodPutsAllFourBindingsInTheMapItIsGiven`: correctly kills the recorded unreachable-outcomes mutation. It verifies the exact four topic→event mappings, all four `DATABENTO` sources, and flag-off emptiness. It does not prove that either consumer calls the method; the companion source pin does that.
- `liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession`: correctly kills `expiring(0)` → `expiring(seekBack)` and checks both live and keyed event classes. It tests the policy seam, not actual seek/cache execution, and does not explicitly assert `neverEvict == false` or `seekBackMs == 0` for live snapshots.
- `theHelloIsSentWhenFootprintAloneIsEnabled`: correctly kills removal of the footprint disjunct, but exercises only the predicate. Actual transmission remains dependent on a source-text test; no behavioral test observes a WebSocket `cvd-hello`.
- `anOutcomeRedeliveryOverwritesItsKeyInPlace`: cleanly and specifically kills `put` → `putIfAbsent`; it proves one row remains and the second payload survives.
- `aNullPayloadIsARefusalNotAThrow`: specifically kills the bar-null throw mutation, but its name/comment exceed its evidence because it does not verify service-level refusal, counted drops, or consumer-loop containment.

None of the five recorded failures appears accidental or caused solely by an unrelated assertion. The campaign’s stored failure lines name the expected test and assertion for each mutation.

## Re-aimed source pins

- `bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics` was only re-anchored from `private void` to the package-private signature. Its two-call-site, adjacency, flag guard, four-binding, and unconditional-discovery assertions remain intact. No weakening found.
- `connectReplayRidesInsideTheHelloContract` still establishes that `addClient` guards the hello send with the named predicate and that the predicate mentions the SPX-level flag. It is no weaker than its former literal-text assertion, but both versions are structurally weak: a predicate such as `true || settings.esCvdSpxLevelsEnabled()` would pass. It also does not independently preserve the ordinary CVD disjunct.

## Missing probes for these requirements

The campaign should additionally probe:

- removing or bypassing each of the two `addEsFootprintTopics` call sites, with behavioral construction of both consumer topic maps;
- live `seekBackMs`, `neverEvict`, and actual cache/seek behavior—not only `ttlMs`;
- observing `cvd-hello` on a fake WebSocket for footprint-only, CVD-only, SPX-only, and all-disabled configurations;
- null bar and null outcome independently through `admitFootprintRecord`, asserting the appropriate drop metric and no exception escaping the live/cache loop;
- the remaining G-R10 promise that sending never occurs while the coordinator lock is held;
- a redelivery with a different byte length, verifying byte accounting as well as value/count replacement.

I could not rerun Maven because the managed workspace forbids writes to `target/classes`; Maven stopped during resource copying before tests. The checked-in campaign records a green baseline and the relevant named failures, and its original production-file hashes match this checkout.

VERDICT: REQUEST_CHANGES
