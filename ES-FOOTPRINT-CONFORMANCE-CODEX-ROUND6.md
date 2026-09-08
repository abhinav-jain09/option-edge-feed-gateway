Reading additional input from stdin...
2026-09-08T22:09:40.506304Z ERROR codex_models_manager::manager: failed to load models cache: missing field `supports_parallel_tool_calls` at line 93 column 5
OpenAI Codex v0.147.0
--------
workdir: /private/tmp/oe-conf-gw
model: gpt-5.6-sol
provider: openai
approval: never
sandbox: read-only
reasoning effort: low
reasoning summaries: none
session id: 01a08311-dbb4-7281-859d-d180a4eb862a
--------
user
Round 6 of the review of option-edge-feed-gateway PR #174 at /private/tmp/oe-conf-gw, branch test/footprint-conformance-pins.

Round 5's findings were folded: every probe your review named as misquoted or misfiled has been
re-quoted against the sentence that states its clause, or re-filed under the requirement whose clause
it exercises; probes that are two sites or variants of one clause are now named and grouped as such,
so the clause counts are no longer inflated. The generator gained a mechanical floor requiring a
quoted obligation to share vocabulary with the clause recorded against it, and its preamble now
states plainly that whether a quote states THE clause is a review judgement it cannot make.

VERIFY all of that against the files and ES-FOOTPRINT-CAMPAIGN.json — do not take this as evidence.
Then review the whole change once more, line by line. For EVERY row of the generated section check:
the quoted obligation is verbatim in that requirement AND states the clause the probe breaks; the
probes filed under it exercise that requirement; the probe and clause counts are right; every KILLED
record's named assertion failures appear in its own failureLines. Say explicitly what remains
unprobed and whether the document is honest about it.

End with exactly one line: VERDICT: APPROVE  or  VERDICT: REQUEST_CHANGES
codex
I’ll independently inspect the branch diff, generator, generated section, and campaign JSON, then cross-check every row and killed record before giving the required verdict.
exec
/bin/bash -lc 'git status --short && git branch --show-current && git diff --stat && git diff --name-only $(git merge-base HEAD origin/main)..HEAD 2>/dev/null || git diff --name-only HEAD~1..HEAD' in /private/tmp/oe-conf-gw
 succeeded in 9249ms:
2026-09-09 00:09:48.488 xcodebuild[94374:267077618]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:09:48.741 xcodebuild[94374:267077615] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-jndbt5bZ' (errno=Operation not permitted)
2026-09-09 00:09:49.234 xcodebuild[94379:267077653]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:09:49.390 xcodebuild[94379:267077649] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-8AYxIYJb' (errno=Operation not permitted)
2026-09-09 00:09:50.439 xcodebuild[94391:267077727]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:09:50.579 xcodebuild[94391:267077723] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-4RKo9K2u' (errno=Operation not permitted)
2026-09-09 00:09:51.074 xcodebuild[94398:267077761]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:09:51.215 xcodebuild[94398:267077758] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
test/footprint-conformance-pins
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-bYSvsA56' (errno=Operation not permitted)
2026-09-09 00:09:52.334 xcodebuild[94420:267077849]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:09:52.490 xcodebuild[94420:267077844] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-bOhYLVma' (errno=Operation not permitted)
2026-09-09 00:09:52.943 xcodebuild[94426:267077880]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:09:53.126 xcodebuild[94426:267077878] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-GtU31BSA' (errno=Operation not permitted)
2026-09-09 00:09:54.167 xcodebuild[94441:267077989]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:09:54.316 xcodebuild[94441:267077985] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-JgQv564H' (errno=Operation not permitted)
2026-09-09 00:09:54.764 xcodebuild[94445:267078048]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:09:54.914 xcodebuild[94445:267078047] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
ES-FOOTPRINT-CAMPAIGN.json
ES-FOOTPRINT-GATEWAY-DESIGN.md
scripts/footprint-mutate.py
scripts/footprint-reqstate.preamble
scripts/footprint-reqstate.py
scripts/footprint-reqstate.sh
src/main/java/app/feedgateway/FeedGatewayService.java
src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
src/test/java/app/feedgateway/FootprintViewsTest.java
src/test/java/app/feedgateway/FootprintWiringTest.java

exec
/bin/bash -lc "git log --oneline --decorate -12; git remote -v; rg --files | sort | sed -n '1,240p'" in /private/tmp/oe-conf-gw
 succeeded in 4530ms:
2026-09-09 00:10:00.171 xcodebuild[94535:267078565]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:00.566 xcodebuild[94535:267078564] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-RYABf529' (errno=Operation not permitted)
2026-09-09 00:10:01.207 xcodebuild[94542:267078664]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:01.333 xcodebuild[94542:267078662] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
689a8da (HEAD -> test/footprint-conformance-pins, origin/test/footprint-conformance-pins) fix(footprint conformance): file the epoch probes where their clauses live, and quote those clauses
5817c12 fix(footprint conformance): file each probe under the requirement that states its clause
9128e37 wip: round-4 folds
0ad2f33 fix(footprint conformance): put the ordering clause under the requirement that states it
ae8a687 wip: generator round-3 folds
8bbcdab fix(footprint tooling): a kill must name an assertion, a throw-kill must name its throw, and the table counts probes
928983e test(gateway footprint): state the no-throw clause directly, not through a propagated throw
81202f4 wip: harness+generator round-2 folds
23ff325 docs(gateway footprint): §2a is regenerated in place, and says only what the record carries
0ecf5f7 wip: drop tainted records
c4c5e0c wip: reqstate rewrites the section in place
94e2ea5 wip: harness declares throw-detection
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-ucyDQfjt' (errno=Operation not permitted)
2026-09-09 00:10:02.675 xcodebuild[94588:267078874]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:02.857 xcodebuild[94588:267078873] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-n2pGMpOO' (errno=Operation not permitted)
2026-09-09 00:10:03.339 xcodebuild[94607:267079022]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:03.571 xcodebuild[94607:267079020] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
origin	git@github.com:abhinav-jain09/option-edge-feed-gateway.git (fetch)
origin	git@github.com:abhinav-jain09/option-edge-feed-gateway.git (push)
Dockerfile
ES-FOOTPRINT-CAMPAIGN.json
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND9.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND4.md
ES-FOOTPRINT-GATEWAY-DESIGN.md
GATEWAY-EPOCH-CURSOR-CODEX-ROUND1.md
GATEWAY-EPOCH-CURSOR-CODEX-ROUND2.md
GATEWAY-EPOCH-CURSOR-CODEX-ROUND3.md
GATEWAY-EPOCH-CURSOR-CODEX-ROUND4.md
GATEWAY-SUREFIRE-ROUTING-CODEX-ROUND1.md
GATEWAY-SUREFIRE-ROUTING-CODEX-ROUND2.md
Jenkinsfile
Jenkinsfile.deploy
MULTI-REPLICA.md
README.md
accelration.md
deploymodel.md
dev/keycloak/realm-export.json
dev/keycloak/run-keycloak.sh
dev/raw-feed-replicator/dependency-reduced-pom.xml
dev/raw-feed-replicator/pom.xml
dev/raw-feed-replicator/src/main/java/app/feedgateway/dev/RawFeedReplicator.java
dev/raw-feed-replicator/src/test/java/app/feedgateway/dev/RawFeedReplicatorSafetyTest.java
dev/redis/run-redis.sh
pom.xml
scripts/footprint-conformance.py
scripts/footprint-mutate.py
scripts/footprint-reqstate.preamble
scripts/footprint-reqstate.py
scripts/footprint-reqstate.sh
src/main/java/app/feedgateway/ApprovalReaper.java
src/main/java/app/feedgateway/AudienceValidator.java
src/main/java/app/feedgateway/AvroJson.java
src/main/java/app/feedgateway/FeedGatewayApplication.java
src/main/java/app/feedgateway/FeedGatewayService.java
src/main/java/app/feedgateway/FeedWebSocketHandler.java
src/main/java/app/feedgateway/FootprintTopicGate.java
src/main/java/app/feedgateway/FootprintViews.java
src/main/java/app/feedgateway/GatewayController.java
src/main/java/app/feedgateway/GatewayMarketCalendar.java
src/main/java/app/feedgateway/GatewaySettings.java
src/main/java/app/feedgateway/HpsfGatewayViewMapper.java
src/main/java/app/feedgateway/OutboundChannel.java
src/main/java/app/feedgateway/SecurityHeadersFilter.java
src/main/java/app/feedgateway/SellerActivityDiskStore.java
src/main/java/app/feedgateway/SessionExpiryReaper.java
src/main/java/app/feedgateway/WebSocketConfig.java
src/main/java/app/feedgateway/WsJwtHandshakeInterceptor.java
src/main/java/app/feedgateway/WsTokenExpiryReaper.java
src/main/java/app/feedgateway/contexttape/ContextTapeConfig.java
src/main/java/app/feedgateway/contexttape/ContextTapeController.java
src/main/java/app/feedgateway/contexttape/ContextTapeUpstream.java
src/main/java/app/feedgateway/gammamigration/GammaFragilityController.java
src/main/java/app/feedgateway/gammamigration/GammaMigrationController.java
src/main/java/app/feedgateway/gammamigration/GammaRotationController.java
src/main/java/app/feedgateway/liquidityhistory/ChainSnapshot.java
src/main/java/app/feedgateway/liquidityhistory/HistoryFrame.java
src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryAuth.java
src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryConfig.java
src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryController.java
src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryStore.java
src/main/java/app/feedgateway/liquidityhistory/SessionAggregate.java
src/main/java/app/feedgateway/mtsession/AppSession.java
src/main/java/app/feedgateway/mtsession/ApprovalState.java
src/main/java/app/feedgateway/mtsession/ApprovalStateMachine.java
src/main/java/app/feedgateway/mtsession/CapacityException.java
src/main/java/app/feedgateway/mtsession/ConcurrencyLimits.java
src/main/java/app/feedgateway/mtsession/EntitlementPolicy.java
src/main/java/app/feedgateway/mtsession/EventType.java
src/main/java/app/feedgateway/mtsession/InMemoryTicketStore.java
src/main/java/app/feedgateway/mtsession/MarketDataSource.java
src/main/java/app/feedgateway/mtsession/Normalization.java
src/main/java/app/feedgateway/mtsession/NotApprovedException.java
src/main/java/app/feedgateway/mtsession/NotEntitledException.java
src/main/java/app/feedgateway/mtsession/RedisTicketStore.java
src/main/java/app/feedgateway/mtsession/RoutableRecord.java
src/main/java/app/feedgateway/mtsession/RoutingKeyDeriver.java
src/main/java/app/feedgateway/mtsession/RoutingTarget.java
src/main/java/app/feedgateway/mtsession/Selection.java
src/main/java/app/feedgateway/mtsession/SessionRoutingEngine.java
src/main/java/app/feedgateway/mtsession/SharedSnapshotCache.java
src/main/java/app/feedgateway/mtsession/SharedUnderlyings.java
src/main/java/app/feedgateway/mtsession/StrikeWindow.java
src/main/java/app/feedgateway/mtsession/SubscriptionKey.java
src/main/java/app/feedgateway/mtsession/SubscriptionManager.java
src/main/java/app/feedgateway/mtsession/TicketStore.java
src/main/java/app/feedgateway/mtsession/UnderlyingKey.java
src/main/java/app/feedgateway/mtsession/UserSessionPolicy.java
src/main/java/app/feedgateway/mtsession/WsTicket.java
src/main/java/app/feedgateway/mtsession/approval/ApprovalAuthority.java
src/main/java/app/feedgateway/mtsession/approval/HttpApprovalAuthority.java
src/main/java/app/feedgateway/mtsession/approval/RoleClaimApprovalAuthority.java
src/main/java/app/feedgateway/mtsession/auth/JwtVerificationException.java
src/main/java/app/feedgateway/mtsession/auth/KeycloakJwtVerifier.java
src/main/java/app/feedgateway/mtsession/auth/TokenVerifier.java
src/main/java/app/feedgateway/mtsession/auth/VerifiedPrincipal.java
src/main/java/app/feedgateway/mtsession/gateway/DealerLedgerJoiner.java
src/main/java/app/feedgateway/mtsession/gateway/GatewayRecordMapper.java
src/main/java/app/feedgateway/mtsession/gateway/HandshakeTicketAuthenticator.java
src/main/java/app/feedgateway/mtsession/gateway/LogoutController.java
src/main/java/app/feedgateway/mtsession/gateway/MtSessionAuthConfig.java
src/main/java/app/feedgateway/mtsession/gateway/MtSessionSecurityInvariant.java
src/main/java/app/feedgateway/mtsession/gateway/OrchestratorReplayRunAuthorizer.java
src/main/java/app/feedgateway/mtsession/gateway/OrchestratorReplayRunLister.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayChronology.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayController.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayParams.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayRunAuthorizer.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayRunLister.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayRunView.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayRunner.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayService.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayTopicResolver.java
src/main/java/app/feedgateway/mtsession/gateway/TicketHandshakeInterceptor.java
src/main/java/app/feedgateway/mtsession/gateway/TicketSubProtocolHandshakeHandler.java
src/main/java/app/feedgateway/mtsession/gateway/WsTicketController.java
src/main/java/app/feedgateway/mtsession/gateway/WsTicketService.java
src/main/java/app/feedgateway/pinflow/PinFlowAggregator.java
src/main/java/app/feedgateway/pinflow/PinFlowController.java
src/main/java/app/feedgateway/pinflow/PinFlowDataSourceConfig.java
src/main/java/app/feedgateway/pinflow/PinFlowExecutor.java
src/main/java/app/feedgateway/pinflow/PinFlowResponse.java
src/main/java/app/feedgateway/pinflow/PinFlowStore.java
src/main/java/app/feedgateway/selleractivity/SellerActivityAggregator.java
src/main/java/app/feedgateway/selleractivity/SellerActivityController.java
src/main/java/app/feedgateway/stockgex/StockGexAsyncConfig.java
src/main/java/app/feedgateway/stockgex/StockGexConfig.java
src/main/java/app/feedgateway/stockgex/StockGexController.java
src/main/java/app/feedgateway/stockgex/StockGexUpstream.java
src/main/java/app/feedgateway/systemstatus/ConsumerLagReader.java
src/main/java/app/feedgateway/systemstatus/SystemStatusConfig.java
src/main/java/app/feedgateway/systemstatus/SystemStatusController.java
src/main/java/app/feedgateway/systemstatus/SystemStatusDataSource.java
src/main/java/app/feedgateway/systemstatus/SystemStatusFailure.java
src/main/java/app/feedgateway/systemstatus/SystemStatusStore.java
src/main/resources/application.properties
src/main/resources/static/app-boot.js
src/main/resources/static/app.html
src/main/resources/static/index.html
src/main/resources/static/login.js
src/main/resources/static/option-chain.css
src/main/resources/static/option-chain.js
src/main/resources/static/vendor/react-dom.production.min.js
src/main/resources/static/vendor/react.production.min.js
src/test/java/app/feedgateway/ApprovalReaperTest.java
src/test/java/app/feedgateway/CvdBarViewTest.java
src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
src/test/java/app/feedgateway/DeltaFlowAccelWiringTest.java
src/test/java/app/feedgateway/EsAggressorFlowWiringTest.java
src/test/java/app/feedgateway/EsAuctionWiringTest.java
src/test/java/app/feedgateway/FeedGatewayLogoutTest.java
src/test/java/app/feedgateway/FeedGatewayPerSessionBroadcastTest.java
src/test/java/app/feedgateway/FeedGatewayReplayCancellationTest.java
src/test/java/app/feedgateway/FeedGatewayReplayCompletionTest.java
src/test/java/app/feedgateway/FeedGatewayRolloverDiagnosticsTest.java
src/test/java/app/feedgateway/FeedGatewayServiceTest.java
src/test/java/app/feedgateway/FeedGatewayShutdownTest.java
src/test/java/app/feedgateway/FeedWebSocketHandlerAttachTest.java
src/test/java/app/feedgateway/FootprintBackfillControllerTest.java
src/test/java/app/feedgateway/FootprintSeamTest.java
src/test/java/app/feedgateway/FootprintTopicGateTest.java
src/test/java/app/feedgateway/FootprintViewsTest.java
src/test/java/app/feedgateway/FootprintWiringTest.java
src/test/java/app/feedgateway/GatewayCalendarConfigTest.java
src/test/java/app/feedgateway/GatewayContextSmokeTest.java
src/test/java/app/feedgateway/GatewayIsolationLevelTest.java
src/test/java/app/feedgateway/GatewayMarketCalendarTest.java
src/test/java/app/feedgateway/HotStrikeEventTest.java
src/test/java/app/feedgateway/HpsfGatewayViewMapperTest.java
src/test/java/app/feedgateway/LiquidityHistoryWsChainsTest.java
src/test/java/app/feedgateway/OutboundChannelTest.java
src/test/java/app/feedgateway/PartitionExpansionIntegrationTest.java
src/test/java/app/feedgateway/SecurityHeadersFilterTest.java
src/test/java/app/feedgateway/SellerActivityCacheIntegrationTest.java
src/test/java/app/feedgateway/SessionExpirySweepTest.java
src/test/java/app/feedgateway/SpotBandForwardingTest.java
src/test/java/app/feedgateway/StaticAssetSecurityTest.java
src/test/java/app/feedgateway/WebSocketConfigOriginsTest.java
src/test/java/app/feedgateway/WsInterceptorWiringTest.java
src/test/java/app/feedgateway/WsJwtHandshakeInterceptorTest.java
src/test/java/app/feedgateway/WsTokenExpiryTest.java
src/test/java/app/feedgateway/contexttape/ContextTapeAuthAllowlistRegressionTest.java
src/test/java/app/feedgateway/contexttape/ContextTapeControllerTest.java
src/test/java/app/feedgateway/contexttape/ContextTapeMvcTest.java
src/test/java/app/feedgateway/gammamigration/GammaFragilityControllerTest.java
src/test/java/app/feedgateway/gammamigration/GammaMigrationControllerTest.java
src/test/java/app/feedgateway/gammamigration/GammaRotationControllerTest.java
src/test/java/app/feedgateway/liquidityhistory/HistoryFrameFailClosedTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryControllerTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryFoldTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryStoreEpochTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryTestSupport.java
src/test/java/app/feedgateway/mtsession/ApprovalStateMachineTest.java
src/test/java/app/feedgateway/mtsession/EventTypeTest.java
src/test/java/app/feedgateway/mtsession/InMemoryTicketStoreConcurrencyTest.java
src/test/java/app/feedgateway/mtsession/InMemoryTicketStoreTest.java
src/test/java/app/feedgateway/mtsession/NormalizationTest.java
src/test/java/app/feedgateway/mtsession/RedisTicketStoreIntegrationTest.java
src/test/java/app/feedgateway/mtsession/RoutingKeyDeriverTest.java
src/test/java/app/feedgateway/mtsession/SessionIsolationIntegrationTest.java
src/test/java/app/feedgateway/mtsession/SessionLifecycleTest.java
src/test/java/app/feedgateway/mtsession/SessionRoutingEngineConcurrencyTest.java
src/test/java/app/feedgateway/mtsession/SessionRoutingEngineReplayTest.java
src/test/java/app/feedgateway/mtsession/SessionRoutingEngineTest.java
src/test/java/app/feedgateway/mtsession/StrikeWindowTest.java
src/test/java/app/feedgateway/mtsession/SubscriptionManagerTest.java
src/test/java/app/feedgateway/mtsession/UserSessionPolicyTest.java
src/test/java/app/feedgateway/mtsession/approval/ApprovalAuthorityTest.java
src/test/java/app/feedgateway/mtsession/auth/KeycloakJwtVerifierHermeticTest.java
src/test/java/app/feedgateway/mtsession/auth/KeycloakJwtVerifierIntegrationTest.java
src/test/java/app/feedgateway/mtsession/gateway/DealerLedgerJoinerTest.java
src/test/java/app/feedgateway/mtsession/gateway/GatewayContextBootIT.java
src/test/java/app/feedgateway/mtsession/gateway/GatewayRecordMapperTest.java
src/test/java/app/feedgateway/mtsession/gateway/HandshakeTicketAuthenticatorTest.java
src/test/java/app/feedgateway/mtsession/gateway/MtSessionAuthConfigTicketStoreTest.java
src/test/java/app/feedgateway/mtsession/gateway/MtSessionSecurityInvariantTest.java
src/test/java/app/feedgateway/mtsession/gateway/OrchestratorReplayRunAuthorizerTest.java
src/test/java/app/feedgateway/mtsession/gateway/OrchestratorReplayRunListerTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayChronologyTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayControllerTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayParamsTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayServiceTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayTopicResolverTest.java
src/test/java/app/feedgateway/mtsession/gateway/TicketHandshakeInterceptorTest.java
src/test/java/app/feedgateway/mtsession/gateway/TicketSubProtocolHandshakeHandlerTest.java

exec
/bin/bash -lc "git for-each-ref --format='%(refname:short)' refs/remotes refs/heads | sort; git show --stat --oneline 689a8da; git show --stat --oneline 5817c12; git log --oneline --reverse --boundary --all -- ES-FOOTPRINT-CAMPAIGN.json | head -30" in /private/tmp/oe-conf-gw
 succeeded in 6949ms:
2026-09-09 00:10:08.633 xcodebuild[94914:267080830]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:08.790 xcodebuild[94914:267080829] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-AaIrzpEF' (errno=Operation not permitted)
2026-09-09 00:10:09.225 xcodebuild[94955:267081085]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:09.363 xcodebuild[94955:267081084] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
agent/graceful-kafka-shutdown
agent/restart-safe-selection
agent/strike-flow-payload-freshness
chore/remove-redundant-replay-carveout
chore/remove-staging
chore/remove-versioned-identities
ci/feed-gateway-fix-buildkit-prod
ci/feed-gateway-use-oeprofile
claude/maxpain-terminal-clean
claude/maxpain-terminal-status
claude/mission-sandwich-forward-barrier
claude/mission-sandwich-ui
codex/heatmap-replay-freshness
codex/opb-gateway-forwarding
codex/strike-flow-gateway
dev-tag-main-only
docs/campaign-failure-lines
docs/footprint-campaign-record
feat/accel-ws-binding
feat/build-number-in-tag
feat/close-direction-gateway
feat/context-tape-routes
feat/corridor-gauge-ws
feat/cvd-spx-levels-stream
feat/cvd-stream
feat/databento-gex-history-merge
feat/dealer-ledger-gateway
feat/dealer-ledger-per-strike-ladder
feat/delta-flow-gateway-consumer
feat/direction-relay
feat/drop-nowcast-gateway
feat/es-auction-gateway
feat/es-footprint-gateway
feat/es-gex-on-spx
feat/es-od-status-gateway
feat/es-open-direction-gateway
feat/forward-strike-clusters
feat/gamma-migration-endpoint
feat/gamma-rotation-endpoint
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
feat/gex-strike-lifecycle
feat/greek-move-auth-gateway
feat/gw-session-expiry-roll
feat/heatmap-calibration-2026-07-30
feat/hot-strike-event
feat/ibkr-preopen-arb
feat/ibkr-preopen-arbitration
feat/ibkr-preopen-gateway
feat/indicators-ws
feat/liquidity-history
feat/live-date-floor
feat/mission-pace-gateway
feat/opb-v2-gateway
feat/option-price-behavior-dashboard-wip
feat/option-truth-gateway
feat/option-truth-relay
feat/pace-rank-gateway
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
feat/spot-vol-regime
feat/spread-skew-gateway
feat/stock-gex-by-expiry
feat/stock-gex-close-board
feat/stock-gex-endpoint
feat/strike-invasion-direction
feat/strike-invasion-gateway
feat/strike-invasion-gateway-main
feat/system-status-endpoint
feat/tape-zones-board
feat/unified-sr-gateway
feat/vol-premium-ivrv-event
feat/zero-dte-direction-gateway
feature/es-aggressor-flow-gateway
feature/es-seller-activity-gateway
feature/hpsf-v2.1-bugzilla-40-64
feature/ibkr-strike-flow-gateway
feature/ibkr-vix-price-topic
feature/keyclock-multi-tenant-sessions
fix/align-vix-option-topic
fix/auto-roll-override-stale
fix/context-smoke-execution-selects-by-tag
fix/context-tape-upstream-connection-recycle
fix/delta-flow-review
fix/es-aggressor-flow-direct-broadcast
fix/es-aggressor-flow-direct-broadcast-v2
fix/es-gex-health-disabled
fix/gamma-migration-default-selection
fix/gateway-auto-only-explicit
fix/gateway-dynamic-partitions
fix/gateway-forward-watchdog
fix/gateway-gex-slow-ttl
fix/gateway-market-calendar-single-source
fix/gateway-never-creates-topics
fix/gateway-partition-refresh-followup
fix/gateway-partition-refresh-hardening
fix/gateway-remove-stray-short-premium
fix/gateway-restore-sp-decls
fix/gateway-service-constructor-ambiguity
fix/gateway-ws-send-timeout
fix/graceful-absence-default
fix/heatmap-history-failclosed
fix/liquidity-history-partitions-dev-32
fix/mission-pace-forward-barrier
fix/oi-banner-clears-on-live-gex
fix/per-topic-catchup-gate
fix/permanent-seller-activity-gateway
fix/pill-anchor-side-correct
fix/pin-epoch-edge-cursor
fix/pinflow-session-window
fix/pinflow-spot-centred-band
fix/pinflow-trade-date-partition
fix/preopen-gex-arbitration-killswitch
fix/preopen-session-id-basic-iso
fix/prod-clean-replace
fix/seller-activity-ctor-ambiguity
fix/seller-activity-session-leader
fix/seller-api-per-strike
fix/seller-rest-only-backpressure
fix/spot-band-batch-enqueue
fix/spot-band-selfreview
fix/system-status-ledger-timeout
fix/system-status-null-booleans
fix/system-status-shortcircuit
fix/ws-dual-auth-bearer-and-ticket
gw-detached
main
origin
origin/agent/graceful-kafka-shutdown
origin/agent/restart-safe-selection
origin/agent/strike-flow-payload-freshness
origin/chore/remove-staging
origin/chore/remove-versioned-identities
origin/ci/feed-gateway-fix-buildkit-prod
origin/ci/feed-gateway-use-oeprofile
origin/claude/databento-gex-ui
origin/claude/maxpain-gateway
origin/claude/maxpain-terminal-clean
origin/claude/mission-sandwich-forward-barrier
origin/claude/mission-sandwich-ui
origin/docs/campaign-failure-lines
origin/docs/footprint-campaign-record
origin/feat/accel-ws-binding
origin/feat/build-number-in-tag
origin/feat/context-tape-routes
origin/feat/corridor-gauge-ws
origin/feat/cvd-spx-levels-stream
origin/feat/cvd-stream
origin/feat/delta-flow-gateway-consumer
origin/feat/direction-relay
origin/feat/drop-nowcast-gateway
origin/feat/es-auction-gateway
origin/feat/es-footprint-gateway
origin/feat/es-gex-on-spx
origin/feat/gamma-migration-endpoint
origin/feat/gamma-rotation-endpoint
origin/feat/gateway-auto-expiry-roll
origin/feat/gateway-gamma-leadership-event
origin/feat/gateway-gex-lifecycle
origin/feat/gateway-rollover-diagnostics
origin/feat/gateway-spx-price
origin/feat/gateway-strike-intel-consumer
origin/feat/gateway-turn-alert
origin/feat/gex-magnet-gateway
origin/feat/gex-magnet-gateway2
origin/feat/gex-strike-lifecycle
origin/feat/gw-session-expiry-roll
origin/feat/heatmap-calibration-2026-07-30
origin/feat/hot-strike-event
origin/feat/ibkr-preopen-arb
origin/feat/indicators-ws
origin/feat/liquidity-history
origin/feat/mission-pace-gateway
origin/feat/opb-v2-gateway
origin/feat/option-price-behavior-dashboard-wip
origin/feat/option-truth-gateway
origin/feat/option-truth-relay
origin/feat/pin-flow-endpoint
origin/feat/pinflow-net-flow
origin/feat/pinflow-spot-from-history
origin/feat/prod-image-tag
origin/feat/replay-control-endpoints
origin/feat/replay-gateway-bridge-config
origin/feat/replay-session-bridge
origin/feat/replay-strike-cluster-on-connect
origin/feat/seller-activity-backend-endpoint
origin/feat/short-premium-gateway
origin/feat/spot-band-forwarding
origin/feat/spot-p2-ui-canonical
origin/feat/spot-vol-regime
origin/feat/spread-skew-gateway
origin/feat/stock-gex-by-expiry
origin/feat/stock-gex-endpoint
origin/feat/strike-invasion-direction
origin/feat/system-status-endpoint
origin/feat/tape-zones-board
origin/feat/vol-premium-ivrv-event
origin/feat/zero-dte-direction-gateway
origin/feature/es-aggressor-flow-gateway
origin/feature/hpsf-v2.1-bugzilla-40-64
origin/feature/ibkr-strike-flow-gateway
origin/feature/ibkr-vix-price-topic
origin/fix/align-vix-option-topic
origin/fix/auto-roll-override-stale
origin/fix/context-smoke-execution-selects-by-tag
origin/fix/context-tape-upstream-connection-recycle
origin/fix/delta-flow-review
origin/fix/es-aggressor-flow-direct-broadcast
origin/fix/es-aggressor-flow-direct-broadcast-v2
origin/fix/es-gex-health-disabled
origin/fix/gamma-migration-default-selection
origin/fix/gateway-auto-only-explicit
origin/fix/gateway-dynamic-partitions
origin/fix/gateway-forward-watchdog
origin/fix/gateway-market-calendar-single-source
origin/fix/gateway-never-creates-topics
origin/fix/gateway-partition-refresh-followup
origin/fix/gateway-partition-refresh-hardening
origin/fix/gateway-remove-stray-short-premium
origin/fix/gateway-restore-sp-decls
origin/fix/gateway-service-constructor-ambiguity
origin/fix/gateway-ws-send-timeout
origin/fix/graceful-absence-default
origin/fix/heatmap-history-failclosed
origin/fix/liquidity-history-partitions-dev-32
origin/fix/mission-pace-forward-barrier
origin/fix/per-topic-catchup-gate
origin/fix/pill-anchor-side-correct
origin/fix/pin-epoch-edge-cursor
origin/fix/pinflow-session-window
origin/fix/pinflow-spot-centred-band
origin/fix/pinflow-trade-date-partition
origin/fix/preopen-gex-arbitration-killswitch
origin/fix/preopen-session-id-basic-iso
origin/fix/prod-clean-replace
origin/fix/seller-api-per-strike
origin/fix/strike-intel-keep-until-expiry
origin/fix/system-status-ledger-timeout
origin/fix/system-status-null-booleans
origin/fix/system-status-shortcircuit
origin/harden/pr4-review-fixes
origin/main
origin/pace-nasa-precision-20260616
origin/pin-mac-agent
origin/pr1
origin/promote/exp-to-dev-gw
origin/r14-fragility-endpoint
origin/refactor/market-data-expiry
origin/test/footprint-conformance-pins
origin/test/invasion-fence-relay-audit
pace-nasa-precision-20260616
peak-move-card
pin-mac-agent
pr1
promote/everything-to-main
promote/exp-to-dev-gw
r14-fragility-endpoint
rebase/gex-strike-lifecycle
refactor/market-data-expiry
revert/preopen-gex-arbitration
revert/spot-band-forwarding
test/footprint-conformance-pins
test/invasion-fence-relay-audit
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-VK4Rshfm' (errno=Operation not permitted)
2026-09-09 00:10:10.370 xcodebuild[95026:267081508]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:10.497 xcodebuild[95026:267081496] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-BtHaklNP' (errno=Operation not permitted)
2026-09-09 00:10:10.949 xcodebuild[95063:267081724]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:11.089 xcodebuild[95063:267081715] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
689a8da fix(footprint conformance): file the epoch probes where their clauses live, and quote those clauses
 ES-FOOTPRINT-CAMPAIGN.json          | 744 ++++++++++++++++++------------------
 ES-FOOTPRINT-GATEWAY-DESIGN.md      |   9 +-
 scripts/footprint-reqstate.preamble |   5 +
 scripts/footprint-reqstate.py       |  16 +-
 4 files changed, 399 insertions(+), 375 deletions(-)
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-cskhn7uu' (errno=Operation not permitted)
2026-09-09 00:10:12.114 xcodebuild[95149:267082173]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:12.254 xcodebuild[95149:267082169] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-whPAKdLd' (errno=Operation not permitted)
2026-09-09 00:10:12.709 xcodebuild[95263:267082512]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:12.848 xcodebuild[95263:267082509] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
5817c12 fix(footprint conformance): file each probe under the requirement that states its clause
 ES-FOOTPRINT-CAMPAIGN.json | 906 +++++++++++++++++++++++----------------------
 1 file changed, 469 insertions(+), 437 deletions(-)
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-O67PiC1a' (errno=Operation not permitted)
2026-09-09 00:10:13.883 xcodebuild[95356:267083036]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:14.029 xcodebuild[95356:267083033] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-2dFiBaXA' (errno=Operation not permitted)
2026-09-09 00:10:14.483 xcodebuild[95397:267083287]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:14.636 xcodebuild[95397:267083276] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
- ae8a687 wip: generator round-3 folds
- 928983e test(gateway footprint): state the no-throw clause directly, not through a propagated throw
- 94e2ea5 wip: harness declares throw-detection
- 1224cec test(gateway footprint): execute the four clauses the suite only read
- fc49c14 Merge pull request #171 from abhinav-jain09/docs/footprint-campaign-record
- b933b39 Merge pull request #170 from abhinav-jain09/fix/pin-epoch-edge-cursor
ac38b5c docs(gateway): the footprint mutation record, with the evidence to audit it
7e220e6 docs(gateway): the record now carries the failure lines its killedBy was parsed from
54e3952 docs(gateway footprint): the design carries what a test actually holds
c4c5e0c wip: reqstate rewrites the section in place
0ecf5f7 wip: drop tainted records
23ff325 docs(gateway footprint): §2a is regenerated in place, and says only what the record carries
8bbcdab fix(footprint tooling): a kill must name an assertion, a throw-kill must name its throw, and the table counts probes
0ad2f33 fix(footprint conformance): put the ordering clause under the requirement that states it
9128e37 wip: round-4 folds
5817c12 fix(footprint conformance): file each probe under the requirement that states its clause
689a8da fix(footprint conformance): file the epoch probes where their clauses live, and quote those clauses

exec
/bin/bash -lc 'git merge-base HEAD origin/main; git rev-list --count $(git merge-base HEAD origin/main)..HEAD; git diff --stat origin/main...HEAD; git diff -- scripts/footprint-reqstate.py scripts/footprint-reqstate.preamble; sed -n '"'1,240p' scripts/footprint-reqstate.py; cat scripts/footprint-reqstate.preamble" in /private/tmp/oe-conf-gw
 succeeded in 9642ms:
2026-09-09 00:10:19.225 xcodebuild[95527:267083935]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:19.370 xcodebuild[95527:267083934] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-bwJg0xlv' (errno=Operation not permitted)
2026-09-09 00:10:19.846 xcodebuild[95533:267083980]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:19.987 xcodebuild[95533:267083979] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
3e13574732f3570f0d42ee3b37509628caa781ee
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-g7kWQxjU' (errno=Operation not permitted)
2026-09-09 00:10:21.022 xcodebuild[95548:267084046]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:21.163 xcodebuild[95548:267084045] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-GNpND3Rf' (errno=Operation not permitted)
2026-09-09 00:10:21.598 xcodebuild[95551:267084069]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:21.748 xcodebuild[95551:267084068] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-1BQxHoLQ' (errno=Operation not permitted)
2026-09-09 00:10:22.771 xcodebuild[95585:267084251]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:22.943 xcodebuild[95585:267084249] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-iW7iFFmm' (errno=Operation not permitted)
2026-09-09 00:10:23.395 xcodebuild[95588:267084281]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:23.542 xcodebuild[95588:267084280] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
15
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-Y5ZCVTEn' (errno=Operation not permitted)
2026-09-09 00:10:24.874 xcodebuild[95603:267084413]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:25.021 xcodebuild[95603:267084412] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-z8qsNHsv' (errno=Operation not permitted)
2026-09-09 00:10:25.463 xcodebuild[95626:267084547]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:25.622 xcodebuild[95626:267084546] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
 ES-FOOTPRINT-CAMPAIGN.json                         | 1832 ++++++++++++++++----
 ES-FOOTPRINT-GATEWAY-DESIGN.md                     |   40 +
 scripts/footprint-mutate.py                        |  160 +-
 scripts/footprint-reqstate.preamble                |   14 +
 scripts/footprint-reqstate.py                      |  155 ++
 scripts/footprint-reqstate.sh                      |   69 +
 .../java/app/feedgateway/FeedGatewayService.java   |   13 +-
 .../app/feedgateway/CvdSpxLevelsWiringTest.java    |    7 +-
 .../java/app/feedgateway/FootprintViewsTest.java   |   25 +
 .../java/app/feedgateway/FootprintWiringTest.java  |   47 +-
 10 files changed, 2027 insertions(+), 335 deletions(-)
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-IaFl2skn' (errno=Operation not permitted)
2026-09-09 00:10:26.911 xcodebuild[95640:267084647]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:27.217 xcodebuild[95640:267084644] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-BMKgrXG8' (errno=Operation not permitted)
2026-09-09 00:10:27.774 xcodebuild[95650:267084728]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:27.919 xcodebuild[95650:267084727] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
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
    # Use the SAME rule as the rewriter: the marker at LINE START. Finding the first substring
    # anywhere lets a prose mention of the marker truncate discovery before the real block.
    mk = re.search(r'^<!-- BEGIN footprint-reqstate', text, re.M)
    authoritative = text if mk is None else text[:mk.start()]
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
    # The requirement text as the DOCUMENT states it, so an obligation can be checked against the
    # requirement it is filed under rather than trusted.
    req_text = {}
    for line in authoritative.split('\n'):
        mm = re.match(r'^\|\s*(' + idPattern + r')\s*\|\s*(.*)$', line)
        if mm:
            t = mm.group(2).rstrip()
            if t.endswith('|'): t = t[:-1].rstrip()
            if len(t) > len(req_text.get(mm.group(1), '')): req_text[mm.group(1)] = t

    problems = []
    for rid, ms in per.items():
        for m in ms:
            # ATTRIBUTION, for every record whatever its status: the obligation a row quotes must be
            # verbatim in the requirement the row sits under. `requirement` is an editable field in a
            # checked-in file, and filing a probe under the wrong requirement is how a table comes to
            # over-count one requirement and under-count another while every individual cell looks fine.
            dt = (m.get('documentText') or '').strip()
            if not dt:
                problems.append(f"{rid}: a record quotes no obligation")
            elif rid not in req_text:
                problems.append(f"{rid}: is not a requirement in the document")
            elif dt not in req_text[rid]:
                problems.append(f"{rid}: the quoted obligation is not verbatim in {rid}: {dt[:70]!r}")
            # Compare on 4-character prefixes so "dropped"/"drop" and "upserts"/"upsert" match; a
            # short obligation ("OLDER ⇒ stale_session drop;") shares few whole words with the clause
            # it states, and demanding whole-word equality rejects correct attributions.
            stem = lambda t: {w.lower()[:4] for w in re.findall(r'[A-Za-z_]{4,}', t)}
            cw, qw = stem(m.get('clause', '')), stem(dt)
            # ONE shared stem, not two. The design states obligations in symbols as often as words
            # ("(3) `gen++`"), so a two-word floor rejects correct attributions and would push the
            # author to reword the clause until the tool is satisfied — which is worse than the gap
            # it closes. This catches a quote lifted from a wholly unrelated part of the requirement;
            # whether the quote states THE clause is a review judgement, and the preamble says so.
            if cw and not (cw & qw):
                problems.append(f"{rid}: the quoted obligation shares almost nothing with the clause "
                                f"it is filed against — clause {m.get('clause','')[:60]!r} vs "
                                f"quote {dt[:60]!r}")
            if m['status'] != 'KILLED':
                continue
            # the named assertion failures must be the ones THIS run produced
            fl = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
            for name in (m.get('assertionFailures') or []):
                if name.split('.')[-1] not in fl:
                    problems.append(f"{rid}: names assertion failure {name} that its own failure lines do not carry")
            if not m.get('assertionFailures'):
                # The renderer must hold this itself: a record is a checked-in FILE, and a hand-edited
                # or legacy one would otherwise be taken at its word.
                t = m.get('killedByThrow')
                lines = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
                ok = (isinstance(t, dict) and t.get('test') and t.get('throws')
                      and t['test'].split('.')[-1] in lines and t['throws'] in lines
                      and all(part in lines for part in [t['test'].split('.')[-1]]))
                if not ok:
                    problems.append(f"{rid}: a KILLED record names no ASSERTION failure, and its "
                                    f"killedByThrow does not name a test and a throw that its own "
                                    f"failure lines carry ({m.get('killedBy')})")
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
            # A clause probed at several SITES, or broken several WAYS (variants), is still one
            # clause; counting records as clauses triple-counts it.
            groups = len({re.sub(r' (?:site|variant)\d+\S*$', '', k) for k in keys[rid]})
            state = f"{killed} of {len(ms)} probes pinned, over {groups} clause" + ("" if groups == 1 else "s")
            rest = ", ".join(f"{n} {st.lower()}" for st, n in sorted(by.items()))
            if rest: state += f" ({rest})"
        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
    # Count only what the table shows. A record for a requirement the document no longer
    # renders is omitted from the rows, and must be omitted from the totals with it.
    rendered = [v for rid in ids for v in per.get(rid, [])]
    tot = collections.Counter(v['status'] for v in rendered)
    out.append("")
    probed_here = len([r for r in ids if r in per])
    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rendered)} mutations "
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

What this table cannot do is decide whether a quoted obligation is the clause a probe exercises.
The generator refuses a quote that is not verbatim in the requirement it sits under, and one that
shares no vocabulary with the clause recorded against it; beyond that, attribution is a judgement
made in review, and the merge gate for this change corrected several of them.

codex
The claimed generator changes are present: it checks verbatim inclusion, applies a shared-vocabulary floor, and explicitly disclaims semantic attribution. I’m now testing whether the campaign data and rendered claims actually satisfy those narrower mechanics and the stronger row-by-row review standard.
exec
/bin/bash -lc "python3 - <<'PY'
import json
p=json.load(open('ES-FOOTPRINT-CAMPAIGN.json'))
print(len(p))
for k,v in p.items():
 print('\\nKEY',k)
 for f in ['requirement','status','clause','documentText','assertionFailures','killedBy','killedByThrow']:
  print(f+':',v.get(f))
 print('failureLines:')
 for x in v.get('evidence',{}).get('failureLines',[]): print(' ',x)
PY
rg -n '"'^'"\\| (F|G|P)-|BEGIN footprint-reqstate|requirements;|Read the state' ES-FOOTPRINT-GATEWAY-DESIGN.md" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
/bin/bash: cannot create temp file for here document: Operation not permitted
151:| G-R1 | **Flag.** `GATEWAY_ES_FOOTPRINT_ENABLED` (default `false`; `GatewaySettings.esFootprintEnabled()`). Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`. Flag on with a topic absent on the cluster: the gateway starts (the topics are OPTIONAL like every other DATABENTO JSON topic) and the page reports no data. |
152:| G-R2 | **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`). ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`. |
153:| G-R3 | **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`). `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt). All four events are added to the per-session standalone allowlist (D5). |
154:| G-R4 | **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock. Admission of any bar/outcome record: parse JSON; `sessionDate` must be present and parse with `LocalDate.parse(s, ISO_LOCAL_DATE)` AND round-trip to the same string (strict canonical `YYYY-MM-DD`); `timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates). Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances; OLDER ⇒ `stale_session` drop; EQUAL (or first) ⇒ upsert (last write per key wins). Because one coordinator rolls both views, a bar from session N+1 arriving before any outcome of N+1 clears N's outcomes with N's bars, and an outcome of N arriving after that is dropped — whichever topic the cache consumer drains first. |
155:| G-R5 | **Outcome view.** `outcomes: TreeMap<tf\|%019d(resolvedAtBarStartMs)\|identity, json>` in the SAME coordinator. The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain; `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component). Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast. |
156:| G-R6 | **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held). It rides the SAME frame so the page has one handshake; `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled. Flag off, the field is absent (its ABSENCE tells the page the gateway has no footprint). |
157:| G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's). `GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` ascending by view key within `tf`, `toMs` inclusive on `resolvedAtBarStartMs`, `after` = an EXCLUSIVE lower bound given as a previous page's `nextCursor` (the last key string). Cursor grammar: `tf|<19 digits>|<identity>` where `tf` equals the query `tf`; an `after` that is non-empty and fails the grammar ⇒ HTTP 400 `{"error":"bad cursor"}`; a well-formed cursor that no longer exists is legal (string lower bound). `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from. Same JWT gate as every `/api` route. **Processing order (exactly one outcome per request, in the AS-IS convention):** (1) Spring typed binding of `toMs`/`limit`/`afterMs` ⇒ 400 on malformed input BEFORE the handler runs (so a malformed request is never counted and never sees the flag or auth); (2) in the handler, flag off ⇒ 404 `{"enabled":false}`; (3) explicit `LiquidityHistoryAuth.authenticate(...)` exactly as `LiquidityHistoryController` does (`:86`) ⇒ its 401/403 on failure; (4) permit: `tryAcquire` on a `Semaphore(GATEWAY_ES_FOOTPRINT_BACKFILL_CONCURRENCY)` (default 4) ⇒ 503 `Retry-After: 1` (`busy`) when none is free; (5) cursor grammar (outcomes route) ⇒ 400 `{"error":"bad cursor"}`; (6) page snapshot under the coordinator lock — the session check (`sessionMismatch`, HTTP 200) and the selection of up to `limit` record REFERENCES into a `List<String>` (no copies) plus `sessionDate` and `nextCursor`; (7) the response is STREAMED: the controller writes to `HttpServletResponse.getOutputStream()` through ONE fixed 64 KiB buffer, the envelope pieces and each record as `record.getBytes(US_ASCII)` one at a time (records are ASCII by F-E8), status 200 set before the first byte; the permit is released in `finally` after the stream is flushed (or the write failed). The lock is held only during (6), never during (7). Transient memory per request is therefore ≤ one record's bytes (≤ 262 144 B) + the 64 KiB buffer + the reference list (≤ 100 × 4 B) < 0.32 MiB (G-R8). |
158:| G-R8 | **Bounds and heap (proven from the limits, never from averages).** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key. **Retained-heap bound** (HotSpot, compressed oops and compressed class pointers — true for any heap < 32 GiB, as here): a JSON value is ASCII (F-E8 alphabet) so it is a Latin-1 compact `String`: 24 B object + 16 B array header + payload, aligned to 8 ⇒ ≤ payload + 48 B; a key `String` ≤ 160 B payload ⇒ ≤ 208 B; a `TreeMap.Entry` = 40 B; per entry overhead ≤ 296 B, so overhead ≤ (12 000 + 20 000) × 296 B = 9.04 MiB; payload ≤ 128 + 16 = 144 MiB; the coordinator's accounting (two `long` counters, one `LocalDate`, per-timeframe eviction cursors) < 1 KiB. Views total ≤ 153.1 MiB. **Transient** per in-flight backfill (G-R7 step 7): < 0.32 MiB, at most 4 concurrent ⇒ < 1.3 MiB; no `StringBuilder`, no response `String`, no encoder buffer beyond the fixed 64 KiB. **Kafka transient**, from the client's fetch mechanics and THREE enforced facts. Mechanics (Kafka consumer `Fetcher`, unchanged since KIP-74): the consumer keeps at most ONE in-flight fetch request per broker node, a fetch request for a node includes only assigned partitions that have NO buffered (completed, unconsumed) data, each partition's share of a response is ≤ `max.partition.fetch.bytes` except that the FIRST batch of a partition is returned whole when it is larger, and `max.poll.records` only paces how many buffered records a `poll()` hands out. Enforced facts: (i) `max.partition.fetch.bytes` = 512 KiB (§1); (ii) `M` = each footprint topic's effective `max.message.bytes` ≤ `GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588, the broker default), validated per G-R8a; (iii) the four topics are UNCOMPRESSED end to end — the upstream producer sets `compression.type=none` explicitly (options-edge-processing amendment: `EsCvdRuntime.producerProps`, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` per G-R8a — so a fetched share IS its decompressed size and no decompression buffer exists. Per consumer, each of the four single-partition footprint topics contributes at most: one in-flight share (≤ max(512 KiB, M) ≤ 1.0 MiB) + one buffered completed share (≤ 1.0 MiB) + the deserialized value `String`s of the records a `poll()` returned from that share (≤ the share's bytes + 48 B per record) ⇒ ≤ 3.0 MiB per topic + per-record overhead, ≤ 12.0 MiB per consumer + per-record overhead. Per-record client overhead for the records of ONE poll — here, and only here, bounded by `max.poll.records` = 100 (§1): key `String` ≤ 208 B, `ConsumerRecord` object ≤ 96 B (header, 7 references, two longs, two ints), empty `RecordHeaders` ≤ 32 B, `ConsumerRecords` map/list share ≤ 64 B ⇒ ≤ 400 B × 100 = 40 KiB. Per consumer ≤ 12.04 MiB; TWO consumers touch footprint topics (state cache, state live) ⇒ ≤ 24.1 MiB, rounded to 25 MiB. Non-footprint partitions in the same fetch responses are not attributable to this feature and are already part of the measured `H_peak`. **Layout assumptions are verified at start-up** when the flag is on: `HotSpotDiagnosticMXBean.getVMOption` for `UseCompressedOops`, `UseCompressedClassPointers` and `CompactStrings` must all be `true`, else the gateway refuses to start naming the flag (the arithmetic above is void otherwise); pinned by a test of the checker over a fake option map. Topic-side facts (ii) and (iii) are verified by G-R8a. **Footprint heap contribution ≤ 153.1 + 1.3 + 25 = 179.4 MiB, rounded to 180 MiB**, independent of record size distribution. **Deployment contingency (§4):** the flag may be enabled only when the deploy PR records, from a full ES session with CVD on and footprint off: (a) heap: `jvm_memory_bytes_used{area="heap"}` peak `H_peak` with `-Xmx − H_peak ≥ 180 MiB + 128 MiB = 308 MiB` (GC headroom); (b) container: `container_memory_working_set_bytes` peak `W_peak` with `limit − W_peak ≥ 180 MiB + 256 MiB = 436 MiB`, AND `limit − Xmx ≥ 512 MiB` (metaspace, code cache, thread stacks, socket/direct buffers, JVM overhead — the current es4 manifest has `limit = Xmx = 1536 MiB`, which violates this and MUST change in the same PR: e.g. `-Xmx1536m` with `limit 2560Mi`, or a measured alternative that satisfies both inequalities). Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits and the tests pin them. |
159:| G-R8a | **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`. **Start-up** (`start()`, before any consumer launches): validate the topics that EXIST; an existing INVALID topic ⇒ refuse to start naming topic and value (fail-closed); an absent topic (`UnknownTopicOrPartitionException` for that resource) ⇒ deferred; any other Admin failure ⇒ refuse to start. **The seam (an API change to `PartitionRefresh`, the AS-IS contract otherwise kept):** `PartitionRefresh` gains a constructor parameter `Predicate<String> topicAdmit`: of the SIX existing constructor call sites (`:1739` selection, `:2087` hpsf-cache, `:2125` hpsf-live, `:2201` alerts, `:2303` state cache, `:2674` state live), the two STATE consumers pass `footprintGate::admit` and the other FOUR pass `t -> true`. Inside `apply()`, AFTER `partitionsFor` discovers new partitions and BEFORE `mergedAssignment`/`consumer.assign(...)` (`FeedGatewayService.java:3665-3671`), discovered partitions whose topic fails `topicAdmit` are WITHHELD: they are not merged, not assigned, not in `Refresh.added()`, hence never sought and never in the caller's caught-up bookkeeping — identical to a topic that does not exist yet; they are re-discovered (and re-evaluated) at the next refresh interval. The same predicate is applied to the bootstrap `partitionsFor` result before the initial `assign` in both state consumers. `addEsFootprintTopics` adds ALL FOUR topics to both consumers' `topicEvents` unconditionally, so the immutable `Set.copyOf(topics)` always contains them and discovery works whenever a topic appears. **`FootprintTopicGate.admit(topic)`** runs on the calling poll thread: a non-footprint topic ⇒ true; a footprint topic already marked VALID ⇒ true; otherwise it validates NOW (bounded timeout): VALID ⇒ mark and true; unknown topic, timeout or other Admin failure ⇒ false this cycle (counted, logged once per state change, retried at the next refresh — the absorbed-refresh-failure discipline); existing-but-invalid ⇒ false (counted `ceiling`/`compression`, logged once per state change). Per-topic state in a `ConcurrentHashMap` shared by both consumers (each evaluates on its own thread; a duplicate validation is harmless); once VALID a topic stays VALID for the incarnation (a topic config change after admission requires a gateway restart — §4 operational rule). Adoption of a late-admitted topic then follows the existing caller behaviour for `Refresh.added()`: the cache consumer seeks its window (a compacted keyed topic replays its retained keys into the coordinator, as `es-cvd-bar` does), the live consumer seeks END. The two validation series are defined in G-R9. |
160:| G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
161:| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
162:| G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |
164:<!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->
185:| G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
186:| G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
187:| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
188:| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
189:| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
190:| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
191:| G-R7 | 4 of 5 probes pinned, over 4 clauses (1 survived) | 2 | Backfill routes |
192:| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
193:| G-R9 | 3 of 3 probes pinned, over 3 clauses | 2 | Metrics |
194:| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
195:| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
196:| G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | Deployment contingency |
198:12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).
200:Read the state column narrowly. "n of m probes pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION (or propagate a throw the spec declared and the run matched) — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.

exec
/bin/bash -lc "jq -r 'to_entries[] | \"\\nKEY \\(.key)\\nrequirement: \\(.value.requirement)\\nstatus: \\(.value.status)\\nclause: \\(.value.clause)\\ndocumentText: \\(.value.documentText)\\nassertionFailures: \\(.value.assertionFailures|tojson)\\nkilledBy: \\(.value.killedBy)\\nkilledByThrow: \\(.value.killedByThrow|tojson)\\nfailureLines:\\n  \\(.value.evidence.failureLines // [] | join(\"\\n  \"))\"' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:

KEY G-R4 newer-date-rolls-both
requirement: G-R4
status: KILLED
clause: a NEWER date rolls BOTH views
documentText: Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
assertionFailures: ["FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"]
killedBy: ["FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.003 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>

KEY G-R9.3 a-rollover-is-counted
requirement: G-R9
status: KILLED
clause: rollover clears are counted in gateway_footprint_rollovers_total, one per date advance
documentText: `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).
assertionFailures: ["FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"]
killedBy: ["FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.008 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>

KEY G-R8 evict-oldest
requirement: G-R8
status: KILLED
clause: the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes
documentText: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
assertionFailures: ["FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact","FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"]
killedBy: ["FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact","FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246>
  [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>

KEY G-R8 evict-rotates
requirement: G-R8
status: KILLED
clause: the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes
documentText: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
assertionFailures: ["FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"]
killedBy: ["FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.008 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:245 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>

KEY G-R8 byte-budget-decremented
requirement: G-R8
status: KILLED
clause: the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes
documentText: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
assertionFailures: ["FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"]
killedBy: ["FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <3>

KEY G-R8 both-budgets-enforced
requirement: G-R8
status: KILLED
clause: both the count AND byte budgets are enforced
documentText: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
assertionFailures: ["FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"]
killedBy: ["FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.008 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <0>

KEY G-R8 evictions-counted
requirement: G-R8
status: KILLED
clause: the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes
documentText: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
assertionFailures: ["FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact","FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"]
killedBy: ["FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact","FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:260 expected: <1> but was: <0>
  [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>

KEY G-R8a ceiling-strict
requirement: G-R8a
status: KILLED
clause: a footprint topic is VALID iff its effective max.message.bytes is at or below the ceiling
documentText: **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
assertionFailures: ["FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts","FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows","FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne","FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"]
killedBy: ["FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts","FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows","FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne","FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.005 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.005 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.002 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused -- Time elapsed: 0.001 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0.001 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.004 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.005 s <<< FAILURE!
  [ERROR]   FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts:68 the validated topic is added; the unvalidated one is WITHHELD from added() ==> expected: <[futures.footprint.bars-0]> but was: <[]>
  [ERROR]   FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows:106 expected: <[futures.cvd.bars-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd.bars-0]>
  [ERROR]   FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne:99 expected: <true> but was: <false>
  [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]>
  [ERROR]   FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears:69 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.outcomes: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588)]
  [ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:55 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 > 1048588)]
  [ERROR]   FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused:38 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.outcomes: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 
  [ERROR]   FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp:225 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 > 1048588)]

KEY G-R8a codec-allowlist
requirement: G-R8a
status: KILLED
clause: only producer/uncompressed are accepted
documentText: **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
assertionFailures: ["FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"]
killedBy: ["FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.015 s <<< FAILURE!
  [ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:51 gzip ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.

KEY G-R9 ceiling-precedence
requirement: G-R9
status: KILLED
clause: ceiling takes precedence over compression
documentText: `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).
assertionFailures: ["FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"]
killedBy: ["FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.006 s <<< FAILURE!
  [ERROR]   FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt:62 expected: <1> but was: <0>

KEY G-R7 to-is-inclusive
requirement: G-R7
status: KILLED
clause: toMs is INCLUSIVE
documentText: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
assertionFailures: ["FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit","FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim","FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"]
killedBy: ["FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit","FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim","FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.006 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.002 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0 s <<< FAILURE!
  [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2>
  [ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false>
  [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:200 expected: <3> but was: <2>

KEY G-R7 limit-clamped-low
requirement: G-R7
status: KILLED
clause: the forwarded limit is clamped to at least 1
documentText: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
assertionFailures: ["FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"]
killedBy: ["FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE!
  [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>

KEY G-R7 limit-clamped-high
requirement: G-R7
status: KILLED
clause: the forwarded limit is clamped to the ceiling
documentText: `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.
assertionFailures: ["FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"]
killedBy: ["FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.004 s <<< FAILURE!
  [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>

KEY G-R1.1 the-flag-gates-every-footprint-object
requirement: G-R1
status: KILLED
clause: Flag off: the views are never instantiated
documentText: Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.
assertionFailures: ["FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing","FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose","FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions","FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge","FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled","FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"]
killedBy: ["FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing","FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose","FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions","FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.005 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.002 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.002 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.348 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.008 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.308 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401>
  [ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123
  [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]>
  [ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true>
  [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true>
  [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:123 flag off adds nothing ==> expected: <true> but was: <false>
  [ERROR]   FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns:244 » Servlet Request processing failed: java.lang.NullPointerException: Cannot invoke "app.feedgateway.liquidityhistory.LiquidityHistoryAuth$Result.status()" because "auth" is null

KEY G-R2.1 one-wiring-method-binds-all-four
requirement: G-R2
status: KILLED
clause: ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers
documentText: ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.
assertionFailures: ["FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"]
killedBy: ["FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE!
  [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot

KEY G-R2.2 the-evidence-binding
requirement: G-R2
status: KILLED
clause: KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)
documentText: **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).
assertionFailures: ["FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics","FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"]
killedBy: ["FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics","FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.008 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.004 s <<< FAILURE!
  [ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint" ==> expected: <1> but was: <2>
  [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-

KEY G-R3.1 an-oversize-record-is-dropped-not-broadcast
requirement: G-R3
status: KILLED
clause: a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped
documentText: `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
assertionFailures: ["FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedBy: ["FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.130 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.014 s <<< FAILURE!
  [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true>
  [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>

KEY G-R3.2 a-rejected-record-is-still-broadcast
requirement: G-R3
status: KILLED
clause: a record the view REJECTS for shape or session is still broadcast
documentText: `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
assertionFailures: ["FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedBy: ["FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.224 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.028 s <<< FAILURE!
  [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false>
  [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>

KEY G-R3.3 live-snapshots-are-never-retained
requirement: G-R3
status: KILLED
clause: never cached, never retained
documentText: **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).
assertionFailures: ["FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"]
killedBy: ["FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.004 s <<< FAILURE!
  [ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>

KEY G-R5.1 the-outcome-key-orders-numerically
requirement: G-R5
status: KILLED
clause: the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)
documentText: The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;
assertionFailures: ["FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession","FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"]
killedBy: ["FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession","FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.072 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.004 s <<< ERROR!
  [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8>
  [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000>
  [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "10|ES.v.0|1m|1|BUYE"
  [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "5|ES.v.0|1m|1|BUYER"
  [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "77|ES.v.0|5m|1|SELL"
  [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » StringIndexOutOfBounds Range [3, 22) out of bounds for length 8

KEY G-R5.2 the-identity-is-the-last-component
requirement: G-R5
status: KILLED
clause: identity CONTAINS |, which is why it is the LAST component
documentText: `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).
assertionFailures: ["FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession","FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"]
killedBy: ["FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession","FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.099 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.002 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< ERROR!
  [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR!
  [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: "id3|000000000000000">
  [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005>
  [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|"
  [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|"
  [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "z|00000000000000000"
  [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|"

KEY G-R5.3 a-foreign-identity-is-refused
requirement: G-R5
status: KILLED
clause: a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)
documentText: Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
assertionFailures: ["FootprintViewsTest.shapeDropsNeverMutate"]
killedBy: ["FootprintViewsTest.shapeDropsNeverMutate"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.008 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:40 expected: <SHAPE> but was: <ADMITTED>

KEY G-R5.4 last-write-per-key-wins
requirement: G-R5
status: KILLED
clause: Last write per full key wins; a redelivery carries the same key and overwrites in place
documentText: Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
assertionFailures: ["FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"]
killedBy: ["FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.005 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>

KEY G-R6.1 the-hello-carries-the-footprint-field
requirement: G-R6
status: KILLED
clause: the existing cvd-hello frame gains ONE field footprint
documentText: **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
assertionFailures: ["FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"]
killedBy: ["FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE!
  [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:152 expected: <{"sessionDate":null,"hwm":{},"footprint":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}> but was: <{"sessionDate":null,"hwm":{},"footprintX":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}>

KEY G-R6.2 the-hello-is-sent-when-footprint-alone-is-on
requirement: G-R6
status: KILLED
clause: cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled
documentText: `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.
assertionFailures: ["FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"]
killedBy: ["FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.004 s <<< FAILURE!
  [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>

KEY G-R6.3 the-hello-carries-the-outcome-high-water-mark
requirement: G-R6
status: KILLED
clause: outcomeHwm:{tf: maxResolvedAtBarStartMs}
documentText: **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
assertionFailures: ["FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks","FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"]
killedBy: ["FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks","FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.006 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.003 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"30s":2000,"1m":5}}>
  [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>

KEY G-R10.1 a-footprint-record-never-escapes-the-consumer-loop
requirement: G-R10
status: KILLED
clause: a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw
documentText: A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
assertionFailures: ["FootprintViewsTest.aNullPayloadIsARefusalNotAThrow"]
killedBy: ["FootprintViewsTest.aNullPayloadIsARefusalNotAThrow"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.005 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:85 a null bar payload must not throw ==> Unexpected exception thrown: java.lang.IllegalArgumentException: json

KEY G-R10.2 every-admission-failure-is-a-counted-drop
requirement: G-R10
status: KILLED
clause: every admission failure is a counted drop
documentText: A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
assertionFailures: ["FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedBy: ["FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.133 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.074 s <<< FAILURE!
  [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:193 four events + the stale bar, never the oversize one: [{"type":"es-footprint","data":{"schemaVersion":6,"live":true}}, {"type":"es-footprint-evidence","data":{"schemaVersion":6,"ev":true}}, {"type":"es-footprint-bar","data":{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"1m","sessionDate":"
  [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>

KEY G-R9.2 every-record-is-counted-before-admission
requirement: G-R9
status: KILLED
clause: records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too
documentText: `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).
assertionFailures: ["FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedBy: ["FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.168 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.025 s <<< FAILURE!
  [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:193 four events + the stale bar, never the oversize one: [{"type":"es-footprint","data":{"schemaVersion":6,"live":true}}, {"type":"es-footprint-evidence","data":{"schemaVersion":6,"ev":true}}, {"type":"es-footprint-bar","data":{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"1m","sessionDate":"
  [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>

KEY G-R4 older-date-dropped
requirement: G-R4
status: KILLED
clause: an OLDER session date is dropped
documentText: OLDER ⇒ `stale_session` drop;
assertionFailures: ["FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts","FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders","FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedBy: ["FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts","FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders","FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly","FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.160 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>
  [ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED>
  [ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED>
  [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>

KEY G-R4 equal-date-upserts
requirement: G-R4
status: KILLED
clause: an EQUAL date upserts without rolling
documentText: EQUAL (or first) ⇒ upsert (last write per key wins).
assertionFailures: ["FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit","FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic","FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact","FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin","FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks","FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor","FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"]
killedBy: ["FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit","FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic","FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact","FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.002 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< FAILURE!
  [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!
  [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {"sessionDate":"2026-08-14","bars":[{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"30s","sessionDate":"2026-08-14","observations":{"barStartMs":5000},"tag":"x"}],"nextCursor":null} ==> expected: <true> but was: <false>
  [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0>
  [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0>
  [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>
  [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{},"outcomeHwm":{"5m":70}}>
  [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:217 expected: <2> but was: <0>
  [ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:189 expected: <4> but was: <1>

KEY G-R4 key-fields-must-be-in-the-epoch-domain
requirement: G-R4
status: KILLED
clause: an out-of-domain timestamp is refused
documentText: `timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates).
assertionFailures: ["FootprintViewsTest.shapeDropsNeverMutate"]
killedBy: ["FootprintViewsTest.shapeDropsNeverMutate"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.003 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {"timeframe":"1m","sessionDate":"2026-08-14","observations":{"barStartMs":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>

KEY G-R7 the-exclusive-cursor-at-the-domain-edge site1-removed
requirement: G-R7
status: KILLED
clause: the afterMs lower bound stays exclusive at the top of the epoch domain
documentText: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
assertionFailures: ["FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"]
killedBy: ["FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"]
killedByThrow: null
failureLines:
  [ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.019 s <<< FAILURE!
  [ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:185 a cursor beyond the domain admits nothing — it must not wrap ==> expected: <0> but was: <4>

KEY G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed
requirement: G-R7
status: SURVIVED
clause: the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way
documentText: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
assertionFailures: []
killedBy: []
killedByThrow: null
failureLines:
  

2026-09-08T22:10:38.769028Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "scripts/footprint-reqstate.sh --check; jq -r 'to_entries[] | [.key,.value.requirement,.value.status,.value.clause,.value.documentText,((.value.assertionFailures//[])|join(\",\"))] | @tsv' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
mktemp: mkdtemp failed on /var/folders/t3/_t_9ln7d49g_yvjqnyr_rdj40000gn/T/tmp.ZNu4Zsp2QJ: Operation not permitted
G-R4 newer-date-rolls-both	G-R4	KILLED	a NEWER date rolls BOTH views	Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts
G-R9.3 a-rollover-is-counted	G-R9	KILLED	rollover clears are counted in gateway_footprint_rollovers_total, one per date advance	`gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts
G-R8 evict-oldest	G-R8	KILLED	the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin
G-R8 evict-rotates	G-R8	KILLED	the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin
G-R8 byte-budget-decremented	G-R8	KILLED	the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact
G-R8 both-budgets-enforced	G-R8	KILLED	both the count AND byte budgets are enforced	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact
G-R8 evictions-counted	G-R8	KILLED	the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin
G-R8a ceiling-strict	G-R8a	KILLED	a footprint topic is VALID iff its effective max.message.bytes is at or below the ceiling	**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.	FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts,FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows,FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne,FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions
G-R8a codec-allowlist	G-R8a	KILLED	only producer/uncompressed are accepted	**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.	FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted
G-R9 ceiling-precedence	G-R9	KILLED	ceiling takes precedence over compression	`gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).	FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt
G-R7 to-is-inclusive	G-R7	KILLED	toMs is INCLUSIVE	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit,FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim,FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic
G-R7 limit-clamped-low	G-R7	KILLED	the forwarded limit is clamped to at least 1	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit
G-R7 limit-clamped-high	G-R7	KILLED	the forwarded limit is clamped to the ceiling	`limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit
G-R1.1 the-flag-gates-every-footprint-object	G-R1	KILLED	Flag off: the views are never instantiated	Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.	FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing,FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose,FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions,FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge,FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled,FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven
G-R2.1 one-wiring-method-binds-all-four	G-R2	KILLED	ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers	ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.	FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven
G-R2.2 the-evidence-binding	G-R2	KILLED	KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)	**Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).	FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics,FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven
G-R3.1 an-oversize-record-is-dropped-not-broadcast	G-R3	KILLED	a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped	`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
G-R3.2 a-rejected-record-is-still-broadcast	G-R3	KILLED	a record the view REJECTS for shape or session is still broadcast	`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
G-R3.3 live-snapshots-are-never-retained	G-R3	KILLED	never cached, never retained	**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).	FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession
G-R5.1 the-outcome-key-orders-numerically	G-R5	KILLED	the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)	The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;	FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession,FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth
G-R5.2 the-identity-is-the-last-component	G-R5	KILLED	identity CONTAINS |, which is why it is the LAST component	`identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).	FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession,FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth
G-R5.3 a-foreign-identity-is-refused	G-R5	KILLED	a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)	Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.	FootprintViewsTest.shapeDropsNeverMutate
G-R5.4 last-write-per-key-wins	G-R5	KILLED	Last write per full key wins; a redelivery carries the same key and overwrites in place	Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.	FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace
G-R6.1 the-hello-carries-the-footprint-field	G-R6	KILLED	the existing cvd-hello frame gains ONE field footprint	**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).	FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on	G-R6	KILLED	cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled	`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.	FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled
G-R6.3 the-hello-carries-the-outcome-high-water-mark	G-R6	KILLED	outcomeHwm:{tf: maxResolvedAtBarStartMs}	**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).	FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks,FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop	G-R10	KILLED	a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw	A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.	FootprintViewsTest.aNullPayloadIsARefusalNotAThrow
G-R10.2 every-admission-failure-is-a-counted-drop	G-R10	KILLED	every admission failure is a counted drop	A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
G-R9.2 every-record-is-counted-before-admission	G-R9	KILLED	records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too	`gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
G-R4 older-date-dropped	G-R4	KILLED	an OLDER session date is dropped	OLDER ⇒ `stale_session` drop;	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts,FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders,FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
G-R4 equal-date-upserts	G-R4	KILLED	an EQUAL date upserts without rolling	EQUAL (or first) ⇒ upsert (last write per key wins).	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit,FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic,FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin,FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks,FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor,FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain
G-R4 key-fields-must-be-in-the-epoch-domain	G-R4	KILLED	an out-of-domain timestamp is refused	`timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates).	FootprintViewsTest.shapeDropsNeverMutate
G-R7 the-exclusive-cursor-at-the-domain-edge site1-removed	G-R7	KILLED	the afterMs lower bound stays exclusive at the top of the epoch domain	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain
G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed	G-R7	SURVIVED	the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	

2026-09-08T22:10:42.536705Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "jq -r 'to_entries[] | \"\\n### \"+.key+\"\\n\"+(.value.mutation|tojson)+\"\\nfile=\"+(.value.file//\"\")+\" line=\"+((.value.line//\"\")|tostring)+\" decl=\"+(.value.declaration//\"\")+\"\\npatch:\\n\"+((.value.patch//.value.diff//\"\")|tostring)' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:

### G-R4 newer-date-rolls-both
null
file=src/main/java/app/feedgateway/FootprintViews.java line=148 decl=
patch:
{"old":"        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;","new":"        bars.clear(); barsBytes = 0;"}

### G-R9.3 a-rollover-is-counted
null
file=src/main/java/app/feedgateway/FootprintViews.java line=150 decl=
patch:
{"old":"        rollovers.incrementAndGet();","new":"        if (false) rollovers.incrementAndGet();"}

### G-R8 evict-oldest
null
file=src/main/java/app/feedgateway/FootprintViews.java line=167 decl=
patch:
{"old":"            String oldest = view.ceilingKey(tf + \"|\");","new":"            String oldest = view.floorKey(tf + \"|~\");"}

### G-R8 evict-rotates
null
file=src/main/java/app/feedgateway/FootprintViews.java line=166 decl=
patch:
{"old":"            if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;","new":"            if (isBars) barsEvictCursor = cursor; else outcomesEvictCursor = cursor;"}

### G-R8 byte-budget-decremented
null
file=src/main/java/app/feedgateway/FootprintViews.java line=171 decl=
patch:
{"old":"            if (isBars) barsBytes -= len; else outcomesBytes -= len;","new":"            if (isBars) barsBytes -= 0; else outcomesBytes -= 0;"}

### G-R8 both-budgets-enforced
null
file=src/main/java/app/feedgateway/FootprintViews.java line=161 decl=
patch:
{"old":"        while (view.size() > maxCount || (isBars ? barsBytes : outcomesBytes) > maxBytes) {","new":"        while (view.size() > maxCount) {"}

### G-R8 evictions-counted
null
file=src/main/java/app/feedgateway/FootprintViews.java line=174 decl=
patch:
{"old":"        if (evicted > 0) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);","new":"        if (false) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);"}

### G-R8a ceiling-strict
null
file=src/main/java/app/feedgateway/FootprintTopicGate.java line=118 decl=
patch:
{"old":"            else if (cfg.maxMessageBytes() > ceiling) o = new Outcome(false, Reason.ceiling,","new":"            else if (cfg.maxMessageBytes() >= ceiling) o = new Outcome(false, Reason.ceiling,"}

### G-R8a codec-allowlist
null
file=src/main/java/app/feedgateway/FootprintTopicGate.java line=119 decl=
patch:
{"old":"            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression,","new":"            else if (false) o = new Outcome(false, Reason.compression,"}

### G-R9 ceiling-precedence
null
file=src/main/java/app/feedgateway/FootprintTopicGate.java line=117 decl=
patch:
{"old":"            if (cfg == null) o = new Outcome(false, Reason.admin, \"no config returned\");","new":"            if (cfg == null) o = new Outcome(false, Reason.admin, \"no config returned\");\n            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression, \"compression.type=\" + cfg.compressionType());"}

### G-R7 to-is-inclusive
null
file=src/main/java/app/feedgateway/FootprintViews.java line=229 decl=
patch:
{"old":"            String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));","new":"            String hi = barKey(tf, Math.min(toMsInclusive - 1, EPOCH_MAX_MS));"}

### G-R7 limit-clamped-low
null
file=src/main/java/app/feedgateway/GatewayController.java line=130 decl=
patch:
{"old":"    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }","new":"    static int clamp(int limit) { return Math.min(limit, FOOTPRINT_LIMIT_MAX); }"}

### G-R7 limit-clamped-high
null
file=src/main/java/app/feedgateway/GatewayController.java line=130 decl=
patch:
{"old":"    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }","new":"    static int clamp(int limit) { return Math.max(1, limit); }"}

### G-R1.1 the-flag-gates-every-footprint-object
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=809 decl=
patch:
{"old":"        if (settings.esFootprintEnabled()) {","new":"        if (true) {"}

### G-R2.1 one-wiring-method-binds-all-four
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=2392 decl=
patch:
{"old":"        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));","new":"        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));"}

### G-R2.2 the-evidence-binding
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=2390 decl=
patch:
{"old":"        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-evidence\"));","new":"        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint\"));"}

### G-R3.1 an-oversize-record-is-dropped-not-broadcast
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=905 decl=
patch:
{"old":"        return a.reason() != FootprintViews.Reason.OVERSIZE;","new":"        return true;"}

### G-R3.2 a-rejected-record-is-still-broadcast
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=905 decl=
patch:
{"old":"        return a.reason() != FootprintViews.Reason.OVERSIZE;","new":"        return a.reason() == FootprintViews.Reason.ADMITTED;"}

### G-R3.3 live-snapshots-are-never-retained
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=6534 decl=
patch:
{"old":"            return CachePolicy.expiring(0L);","new":"            return CachePolicy.expiring(settings.esFootprintSeekBackMs());"}

### G-R5.1 the-outcome-key-orders-numerically
null
file=src/main/java/app/feedgateway/FootprintViews.java line=296 decl=
patch:
{"old":"        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;","new":"        return tf + \"|\" + resolvedAtBarStartMs + \"|\" + identity;"}

### G-R5.2 the-identity-is-the-last-component
null
file=src/main/java/app/feedgateway/FootprintViews.java line=296 decl=
patch:
{"old":"        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;","new":"        return tf + \"|\" + identity + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs);"}

### G-R5.3 a-foreign-identity-is-refused
null
file=src/main/java/app/feedgateway/FootprintViews.java line=125 decl=
patch:
{"old":"                || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {","new":"                || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {"}

### G-R5.4 last-write-per-key-wins
null
file=src/main/java/app/feedgateway/FootprintViews.java line=132 decl=
patch:
{"old":"            String previous = outcomes.put(key, json);","new":"            String previous = outcomes.putIfAbsent(key, json);"}

### G-R6.1 the-hello-carries-the-footprint-field
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=11180 decl=
patch:
{"old":"            sb.append(\",\\\"footprint\\\":\").append(footprintViews.helloField());","new":"            sb.append(\",\\\"footprintX\\\":\").append(footprintViews.helloField());"}

### G-R6.2 the-hello-is-sent-when-footprint-alone-is-on
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=11158 decl=
patch:
{"old":"        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;","new":"        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();"}

### G-R6.3 the-hello-carries-the-outcome-high-water-mark
null
file=src/main/java/app/feedgateway/FootprintViews.java line=195 decl=
patch:
{"old":"            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, outcomes, true);","new":"            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, bars, true);"}

### G-R10.1 a-footprint-record-never-escapes-the-consumer-loop
null
file=src/main/java/app/feedgateway/FootprintViews.java line=93 decl=
patch:
{"old":"        if (json == null) return Admission.SHAPE;","new":"        if (json == null) throw new IllegalArgumentException(\"json\");"}

### G-R10.2 every-admission-failure-is-a-counted-drop
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=903 decl=
patch:
{"old":"            footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").incrementAndGet();","new":"            if (a.reason() != FootprintViews.Reason.SHAPE) footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").incrementAndGet();"}

### G-R9.2 every-record-is-counted-before-admission
null
file=src/main/java/app/feedgateway/FeedGatewayService.java line=897 decl=
patch:
{"old":"        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {","new":"        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission\n        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {"}

### G-R4 older-date-dropped
null
file=src/main/java/app/feedgateway/FootprintViews.java line=146 decl=
patch:
{"old":"        if (cmp < 0) return null;","new":"        if (cmp < 0) return false;"}

### G-R4 equal-date-upserts
null
file=src/main/java/app/feedgateway/FootprintViews.java line=147 decl=
patch:
{"old":"        if (cmp == 0) return false;","new":"        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }"}

### G-R4 key-fields-must-be-in-the-epoch-domain
null
file=src/main/java/app/feedgateway/FootprintViews.java line=329 decl=
patch:
{"old":"        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;","new":"        return x < 0 ? -1 : x;"}

### G-R7 the-exclusive-cursor-at-the-domain-edge site1-removed
null
file=src/main/java/app/feedgateway/FootprintViews.java line=226 decl=
patch:
{"old":"            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {","new":"            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0) {"}

### G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed
null
file=src/main/java/app/feedgateway/FootprintViews.java line=226 decl=
patch:
{"old":"            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {","new":"            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive <= EPOCH_MAX_MS) {"}

2026-09-08T22:10:50.487699Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc 'git diff --unified=80 origin/main...HEAD -- src/main/java/app/feedgateway/FeedGatewayService.java src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java src/test/java/app/feedgateway/FootprintViewsTest.java src/test/java/app/feedgateway/FootprintWiringTest.java scripts/footprint-mutate.py scripts/footprint-reqstate.sh scripts/footprint-reqstate.py scripts/footprint-reqstate.preamble' in /private/tmp/oe-conf-gw
 succeeded in 1657ms:
2026-09-09 00:10:54.289 xcodebuild[97220:267091290]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:54.433 xcodebuild[97220:267091280] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-MM9eA0aO' (errno=Operation not permitted)
2026-09-09 00:10:54.894 xcodebuild[97289:267091443]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:10:55.039 xcodebuild[97289:267091442] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
diff --git a/scripts/footprint-mutate.py b/scripts/footprint-mutate.py
index 9e77d4b..5d944a9 100644
--- a/scripts/footprint-mutate.py
+++ b/scripts/footprint-mutate.py
@@ -1,162 +1,288 @@
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
-    return [l.rstrip()[:400] for l in re.findall(pat, out, re.M)][:12]
+    lines = [l.rstrip()[:400] for l in re.findall(pat, out, re.M)]
+    # The cap must not cut the record loose from its own names: a truncated list left a record naming
+    # four failing tests whose lines were not in it, which a reader cannot check and a generator that
+    # correlates the two will (correctly) refuse.
+    if kind != 'maven':
+        return lines[:40]
+    return lines[:40]
+
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
+                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': (asserted or failed)[:4], 'assertionFailures': [n for n in asserted if any(n.split('.')[-1] in l for l in failure_lines(out, kind))][:8],
+                  'anyFailures': failed[:8],
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
diff --git a/scripts/footprint-reqstate.preamble b/scripts/footprint-reqstate.preamble
new file mode 100644
index 0000000..901a40c
--- /dev/null
+++ b/scripts/footprint-reqstate.preamble
@@ -0,0 +1,14 @@
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
+What this table cannot do is decide whether a quoted obligation is the clause a probe exercises.
+The generator refuses a quote that is not verbatim in the requirement it sits under, and one that
+shares no vocabulary with the clause recorded against it; beyond that, attribution is a judgement
+made in review, and the merge gate for this change corrected several of them.
diff --git a/scripts/footprint-reqstate.py b/scripts/footprint-reqstate.py
new file mode 100644
index 0000000..5310c1c
--- /dev/null
+++ b/scripts/footprint-reqstate.py
@@ -0,0 +1,155 @@
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
+    # The requirement text as the DOCUMENT states it, so an obligation can be checked against the
+    # requirement it is filed under rather than trusted.
+    req_text = {}
+    for line in authoritative.split('\n'):
+        mm = re.match(r'^\|\s*(' + idPattern + r')\s*\|\s*(.*)$', line)
+        if mm:
+            t = mm.group(2).rstrip()
+            if t.endswith('|'): t = t[:-1].rstrip()
+            if len(t) > len(req_text.get(mm.group(1), '')): req_text[mm.group(1)] = t
+
+    problems = []
+    for rid, ms in per.items():
+        for m in ms:
+            # ATTRIBUTION, for every record whatever its status: the obligation a row quotes must be
+            # verbatim in the requirement the row sits under. `requirement` is an editable field in a
+            # checked-in file, and filing a probe under the wrong requirement is how a table comes to
+            # over-count one requirement and under-count another while every individual cell looks fine.
+            dt = (m.get('documentText') or '').strip()
+            if not dt:
+                problems.append(f"{rid}: a record quotes no obligation")
+            elif rid not in req_text:
+                problems.append(f"{rid}: is not a requirement in the document")
+            elif dt not in req_text[rid]:
+                problems.append(f"{rid}: the quoted obligation is not verbatim in {rid}: {dt[:70]!r}")
+            # Compare on 4-character prefixes so "dropped"/"drop" and "upserts"/"upsert" match; a
+            # short obligation ("OLDER ⇒ stale_session drop;") shares few whole words with the clause
+            # it states, and demanding whole-word equality rejects correct attributions.
+            stem = lambda t: {w.lower()[:4] for w in re.findall(r'[A-Za-z_]{4,}', t)}
+            cw, qw = stem(m.get('clause', '')), stem(dt)
+            # ONE shared stem, not two. The design states obligations in symbols as often as words
+            # ("(3) `gen++`"), so a two-word floor rejects correct attributions and would push the
+            # author to reword the clause until the tool is satisfied — which is worse than the gap
+            # it closes. This catches a quote lifted from a wholly unrelated part of the requirement;
+            # whether the quote states THE clause is a review judgement, and the preamble says so.
+            if cw and not (cw & qw):
+                problems.append(f"{rid}: the quoted obligation shares almost nothing with the clause "
+                                f"it is filed against — clause {m.get('clause','')[:60]!r} vs "
+                                f"quote {dt[:60]!r}")
+            if m['status'] != 'KILLED':
+                continue
+            # the named assertion failures must be the ones THIS run produced
+            fl = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
+            for name in (m.get('assertionFailures') or []):
+                if name.split('.')[-1] not in fl:
+                    problems.append(f"{rid}: names assertion failure {name} that its own failure lines do not carry")
+            if not m.get('assertionFailures'):
+                # The renderer must hold this itself: a record is a checked-in FILE, and a hand-edited
+                # or legacy one would otherwise be taken at its word.
+                t = m.get('killedByThrow')
+                lines = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
+                ok = (isinstance(t, dict) and t.get('test') and t.get('throws')
+                      and t['test'].split('.')[-1] in lines and t['throws'] in lines
+                      and all(part in lines for part in [t['test'].split('.')[-1]]))
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
+            # A clause probed at several SITES, or broken several WAYS (variants), is still one
+            # clause; counting records as clauses triple-counts it.
+            groups = len({re.sub(r' (?:site|variant)\d+\S*$', '', k) for k in keys[rid]})
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
diff --git a/src/main/java/app/feedgateway/FeedGatewayService.java b/src/main/java/app/feedgateway/FeedGatewayService.java
index d242d5c..ef4e221 100644
--- a/src/main/java/app/feedgateway/FeedGatewayService.java
+++ b/src/main/java/app/feedgateway/FeedGatewayService.java
@@ -1115,161 +1115,161 @@ public class FeedGatewayService implements ReplayRunner {
                     // silent either: repeated failures leave projections on screen past 09:35 with
                     // no other symptom, so they get a counter (scraped) and a log line.
                     ibkrPreOpenSweepFailures.incrementAndGet();
                     System.out.println("Feed gateway ibkr-preopen sweep FAILED "
                             + "(projections may outlive 09:35): "
                             + e.getClass().getSimpleName() + ": " + e.getMessage());
                 }
             }, 5L, 5L, TimeUnit.SECONDS);
         }
         // Rollover-diagnostics 60s dump (additive; separate executor so a diag exception can never wedge
         // the batch/deadline/autoroll cadence). See dumpDiagnosticState() for the semantics.
         diagnosticsExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
             Thread thread = new Thread(runnable, "options-edge-feed-gateway-diagnostics");
             thread.setDaemon(true);
             return thread;
         });
         diagnosticsExecutor.scheduleAtFixedRate(this::dumpDiagnosticState, 60L, 60L, TimeUnit.SECONDS);
     }
 
     private void enforceOutboundWriteDeadlines() {
         long now = System.currentTimeMillis();
         long deadline = settings.wsWriteDeadlineMs();
         for (OutboundChannel channel : outbound.values()) {
             try {
                 channel.enforceWriteDeadline(now, deadline);
             } catch (RuntimeException ignored) {
                 // a single channel must not break the sweep
             }
         }
     }
 
     @PreDestroy
     public void stop() {
         running.set(false);
         // Let Kafka polling loops observe running=false and leave their try-with-resources blocks.
         // Interrupting first makes KafkaConsumer.close() inherit the interrupted flag and emit one
         // "Failed to close fetcher" ERROR per consumer during every normal Kubernetes rollout.
         shutdownExecutorGracefully(executor);
         shutdownExecutorGracefully(batchExecutor);
         shutdownExecutorGracefully(diagnosticsExecutor);
         // Wake and stop any in-flight replay readers so shutdown is not held up by a blocking poll.
         for (ReplayHandle handle : replayHandles.values()) {
             handle.active.set(false);
             handle.wakeConsumers();
         }
         shutdownExecutorGracefully(replayExecutor);
         for (OutboundChannel channel : outbound.values()) {
             channel.shutdown();
         }
         outbound.clear();
         shutdownExecutorGracefully(outboundWriters);
         sellerActivityStore.close();
     }
 
     static void shutdownExecutorGracefully(ExecutorService executor) {
         if (executor == null) {
             return;
         }
         executor.shutdown();
         try {
             if (executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                 return;
             }
             executor.shutdownNow();
             executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
         } catch (InterruptedException e) {
             executor.shutdownNow();
             Thread.currentThread().interrupt();
         }
     }
 
     public void addClient(WebSocketSession session) {
         long nowMs = System.currentTimeMillis();
         purgeExpiredCache(nowMs);
         OutboundChannel channel = new OutboundChannel(session, outboundWriterExecutor(),
                 settings.wsMaxQueuedMessages(), settings.wsMaxQueuedBytes(), outboundMetrics, this::onSlowDisconnect);
         outbound.put(session.getId(), channel);
         clients.add(session);
         clientsById.put(session.getId(), session);
         send(session, "status", statusJson());
-        if (settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null) {
+        if (sendsCvdHello()) {
             // R46 hello: the per-timeframe high-water marks of the bar view, so the page can bound
             // its REST backfill to exactly what this gateway holds and buffer WS bars past it.
             // U16 (CL-R8/G19): the latest ACCEPTED levels record rides INSIDE this same hello, so
             // "hello carried no levels record" (levels: null) is distinguishable from "replay still
             // pending" — the page needs that distinction to choose `no_data` over staying blank.
             send(session, "cvd-hello", cvdHelloJson());
         }
         // A client may reconnect after the one-shot readiness broadcast (for example after a
         // gateway restart or a transient socket failure). Re-announce the already-committed
         // readiness to this socket so its UI cannot remain stuck in source-switching forever.
         ActiveSelection selection = activeSelection.get();
         if (readySelectionKeyMatchesActive(selection)) {
             send(session, "source-ready", activeSelectionJson(selection, "source-ready"));
         }
         if (settings.esAuctionEnabled()) {
             // Auction-desk hello: what this gateway holds for the current trade date, so the page can
             // bound its /api/auction/minutes backfill and buffer live WS minutes past it. It must NOT
             // go out before the state cache consumer has finished its seven-day hydration (code review
             // round 3): a page that reconnects mid-restart would bound its backfill by a PARTIAL view
             // and never learn about the records hydrated afterwards, because the hydration path is
             // deliberately silent. Hold the socket instead — the page buffers live frames until the
             // hello arrives — and flush every held socket the moment hydration completes.
             // The latch and the send are ONE step, under the incarnation lock an invalidation also holds:
             // checked and sent separately, an invalidation could land in between and this socket would be
             // promised a view that had just been dropped (code review round 18). The lost-wakeup recheck of
             // round 4 is then unnecessary — the insertion cannot race a flush that needs the same lock.
             synchronized (esAuctionIncarnationLock) {
                 if (esAuctionHandoffFrozen.get()) {
                     send(session, "es-auction-hello", esAuctionHelloJson());
                 } else {
                     esAuctionHelloPending.add(session);
                 }
             }
         }
         // In per-session mode the GLOBAL cached replay is replaced by a PER-SESSION filtered replay:
         // each socket gets only the cached state matching its own AppSession selection (no cross-
         // contract leak), then live routed data (FR-11).
         if (perSessionRouting()) {
             // A socket that connects BETWEEN a source switch and its readiness can be served a cache that
             // is still filling, and the readiness replay iterates `clients` weakly — if its iterator was
             // snapshotted before this session was registered, no later attempt repairs it, because
             // readiness is one-shot per selection key. Bracket the replay with the key: if readiness
             // committed while we were replaying, take the completed band board ourselves.
             String keyBeforeReplay = readySelectionKey.get();
             replayCachedToSocket(session);
             if (!java.util.Objects.equals(keyBeforeReplay, readySelectionKey.get())) {
                 replaySpotBandBatchOncePerReadiness(session);
             }
             // short-premium is a GLOBAL advisory (symbol-filtered client-side), not per-session
             // routed — replayCacheMap can't deliver it (no GatewayRecordMapper case), so replay it
             // the same standalone way legacy mode does, so an auth-mode reload restores the overlay.
             replayShortPremiumCached(session);
             // corridor-gauge standalone replay: a reload mid-session restores the strip instantly
             replayCorridorGaugeCached(session);
             replayZeroDteIntelligenceCached(session);
             // Indicators are the same GLOBAL advisory class (symbol-filtered
             // client-side) — the production auth mode owes the connect replay too
             // (r1 finding 5).
             replayIndicatorsCached(session);
             // Tape-zones board is the same GLOBAL advisory class — auth mode owes it too.
             replayTapeZonesCached(session);
             return;
         }
         if (avroCaughtUp.get()) {
             // max-pain + strike-sr are DATABENTO-only Avro, so they bootstrap purely via the Avro consumer.
             sendCachedState(session, List.of("snapshot", "pace", "pace-rank", "directional-pressure", "max-pain", "strike-sr", "gex-magnet", "gamma-migration", "gex-strike-lifecycle"));
         }
         if (stateCaughtUp.get()) {
             sendCachedState(session, List.of("vix-price", "index-price", "spx-price", "strike-flow", "spot-band", "delta-flow", "strike-intel", "strike-invasion", "liquidity-heatmap", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "option-price-behavior", "opb-by-option", "opb-session", "es-gex", "es-strike-intel"));
             replayOptionTruthCachedLegacy(session);
             // dealer-ledger is delivered STANDALONE (its own message.type), never inside the ui-batch,
             // so it replays via its own path rather than sendCachedState's batch envelope.
             replayDealerLedgerCached(session);
             // hot-strike is likewise STANDALONE and GLOBAL: replay the fresh cached
             // envelope(s) so a legacy-mode reload restores the gold mark (§4.4).
             replayHotStrikeCached(session);
             // short-premium recommendations are likewise STANDALONE; replay the day's cached recommendations
             // so a page reload mid-session (or a client that connects after Agent A acted) still shows the
             // active overlay rather than waiting for the next live broadcast.
             replayShortPremiumCached(session);
@@ -2307,161 +2307,161 @@ public class FeedGatewayService implements ReplayRunner {
         // corridor-gauge live state: JSON per symbol|expiry, standalone delivery like dealer-ledger
         topicEvents.put(settings.corridorGaugeTopic(), new TopicBinding("DATABENTO", "corridor-gauge"));
         // Keep the cache + live JSON consumer topic sets symmetric (same rule as gex-history).
         topicEvents.put(settings.strikeLiquidityTopic(), new TopicBinding("DATABENTO", "liquidity-heatmap"));
         topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding("DATABENTO", "mission-pace"));
         topicEvents.put(settings.missionControlTopic(), new TopicBinding("DATABENTO", "mission-control"));
         // spread-skew.current is plain JSON (single record keyed "SPX") — keep the cache + live JSON
         // consumer topic sets symmetric (same rule as mission-control above).
         topicEvents.put(settings.spreadSkewTopic(), new TopicBinding("DATABENTO", "spread-skew"));
         // spread-skew.events: discrete FIRE/EXIT/REVERSAL/RESTART transitions, broadcast STANDALONE (never cached).
         topicEvents.put(settings.spreadSkewEventsTopic(), new TopicBinding("DATABENTO", "spread-skew-event"));
         // drop-classifier SHADOW nowcast: discrete k=1 verdicts + refinements, broadcast STANDALONE
         // (never cached) — the spread-skew-event sibling. Advisory-only (SHADOW).
         topicEvents.put(settings.dropNowcastTopic(), new TopicBinding("DATABENTO", "drop-nowcast"));
         topicEvents.put(settings.optionPriceBehaviorDashboardTopic(), new TopicBinding("DATABENTO", "option-price-behavior"));
         topicEvents.put(settings.optionPriceBehaviorByOptionTopic(), new TopicBinding("DATABENTO", "opb-by-option"));
         topicEvents.put(settings.optionPriceBehaviorSessionTopic(), new TopicBinding("DATABENTO", "opb-session"));
         // Keep the cache + live JSON consumer topic sets symmetric: ES open-direction forecast +
         // outcomes (JSON, standalone/optional — same rule as the cache consumer above).
         topicEvents.put(settings.esOpenDirectionForecastTopic(), new TopicBinding("DATABENTO", "es-open-direction-forecast"));
         topicEvents.put(settings.esOpenDirectionOutcomeTopic(), new TopicBinding("DATABENTO", "es-open-direction-outcome"));
         // Keep the cache + live JSON consumer topic sets symmetric: the ES open-direction live STATUS
         // heartbeat (JSON, standalone/optional — same rule as the forecast/outcome siblings above).
         topicEvents.put(settings.esOpenDirectionStatusTopic(), new TopicBinding("DATABENTO", "es-open-direction-status"));
         // Keep the cache + live JSON consumer topic sets symmetric: the greek-move-authenticity CURRENT
         // verdict (JSON, key = symbol, standalone/optional — same rule as the open-direction siblings above).
         topicEvents.put(settings.greekMoveAuthCurrentTopic(), new TopicBinding("DATABENTO", "greek-move-auth"));
         // Spot-vol-regime CURRENT rides the same optional/standalone JSON class as greek-move-auth.
         topicEvents.put(settings.spotVolRegimeTopic(), new TopicBinding("DATABENTO", "spot-vol-regime"));
         // Vol-premium IV/RV rides the same optional/standalone JSON class as spot-vol-regime.
         topicEvents.put(settings.volPremiumIvrvTopic(), new TopicBinding("DATABENTO", "vol-premium-ivrv"));
         // Gamma-leadership CURRENT rides the same optional/standalone JSON class.
         topicEvents.put(settings.gammaLeadershipTopic(), new TopicBinding("DATABENTO", "gamma-leadership"));
         // Candle Direction CURRENT decision (commissioning shadow) rides the same optional/standalone JSON class.
         topicEvents.put(settings.directionCurrentTopic(), new TopicBinding("DATABENTO", "direction"));
         topicEvents.put(settings.directionPushTopic(), new TopicBinding("DATABENTO", "direction-push"));
         topicEvents.put(settings.directionAlertTopic(), new TopicBinding("DATABENTO", "direction-alert"));
         topicEvents.put(settings.directionScorecardTopic(), new TopicBinding("DATABENTO", "direction-scorecard"));
         // r1 finding 1: dev/prod consume BOTH the locally-computed SPX topic AND
         // the es4-mirrored ES topic (§7.3); on es4 the set collapses to one.
         for (String indicatorTopic : settings.indicatorsSnapshotTopics()) {
             topicEvents.put(indicatorTopic, new TopicBinding("DATABENTO", "indicators"));
         }
         // Tape-zones CURRENT board (plain JSON, key ES|sessionDate): standalone global advisory on
         // the SHORT tapeZonesTtlMs window, OPTIONAL topic — absent on dev/prod until the MM1 mirror
         // is installed and on es4 until the service first produces (§6.2).
         topicEvents.put(settings.tapeZonesBoardTopic(), new TopicBinding("DATABENTO", "tapeZones"));
         // Keep the cache + live JSON consumer topic sets symmetric: the SPX close-direction signal
         // (JSON, standalone/optional — same rule as the open-direction siblings above).
         topicEvents.put(settings.closeDirectionSignalTopic(), new TopicBinding("DATABENTO", "close-direction"));
         if (settings.esAggressorFlowEnabled()) {
             topicEvents.put(settings.esAggressorFlowTopic(), new TopicBinding("DATABENTO", "es-aggressor-flow"));
         }
         addEsCvdTopics(topicEvents);
         addEsFootprintTopics(topicEvents);
         addEsAuctionTopics(topicEvents);
         if (settings.esCvdSpxLevelsEnabled()) {
             // U16: SPX-translated CVD structure levels (compacted single-partition heartbeat,
             // >=1 record per ALIGN_HEARTBEAT while the aligner runs) — LIVE consumer only. The
             // cache consumer is deliberately NOT subscribed: updateCache has no case for it, and
             // this event keeps its own latest-record retention for the connect replay.
             topicEvents.put(settings.esCvdSpxLevelsTopic(), new TopicBinding("DATABENTO", "es-cvd-spx-levels"));
         }
         topicEvents.put(settings.vixOptionInteligenceTopic(), new TopicBinding("DATABENTO", "zero-dte-intelligence"));
         runLiveConsumer("state-live", topicEvents, false, stateCaughtUp);
     }
 
     private void addEsCvdTopics(Map<String, TopicBinding> topicEvents) {
         if (!settings.esCvdEnabled()) return;
         // One wiring path is shared by bootstrap and live consumers so their topic sets cannot drift.
         topicEvents.put(settings.esCvdTopic(), new TopicBinding("DATABENTO", "es-cvd"));
         topicEvents.put(settings.esCvdBarsTopic(), new TopicBinding("DATABENTO", "es-cvd-bar"));
     }
 
     /**
      * ES Footprint (G-R2/G-R8a): the same one-wiring-path rule as {@link #addEsCvdTopics}. ALL FOUR
      * topics are added unconditionally when the flag is on, so {@code PartitionRefresh}'s immutable
      * topic set always contains them and discovers them whenever they appear; consumption is gated
      * per topic by {@link FootprintTopicGate#admit} at bootstrap and inside every refresh.
      */
-    private void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {
+    void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {   // package-private: the wiring test EXECUTES it
         if (footprintViews == null) return;
         topicEvents.put(settings.esFootprintTopic(), new TopicBinding("DATABENTO", "es-footprint"));
         topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding("DATABENTO", "es-footprint-evidence"));
         topicEvents.put(settings.esFootprintBarsTopic(), new TopicBinding("DATABENTO", "es-footprint-bar"));
         topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));
     }
 
     /** SPX Auction Desk minute stream: same shared-wiring rule as {@link #addEsCvdTopics}. */
     private void addEsAuctionTopics(Map<String, TopicBinding> topicEvents) {
         if (!settings.esAuctionEnabled()) return;
         topicEvents.put(settings.esAuctionTopic(), new TopicBinding("DATABENTO", "es-auction"));
     }
 
     private void runAlertConsumer() {
         Map<String, TopicBinding> topicEvents = new LinkedHashMap<>();
         topicEvents.put(settings.ibkrVolumeSandwichAlertsTopic(), new TopicBinding("IBKR", "volume-sandwich-alert"));
         topicEvents.put(settings.databentoVolumeSandwichAlertsTopic(), new TopicBinding("DATABENTO", "volume-sandwich-alert"));
         runRetryingConsumer("alerts", retry -> runAlertConsumerOnce(topicEvents), null);
     }
 
     private void runHpsfCacheConsumer() {
         runRetryingConsumer(
                 "hpsf-cache",
                 retry -> runHpsfCacheConsumerOnce(),
                 () -> markCacheRecovering(hpsfCaughtUp)
         );
     }
 
     private void runHpsfLiveConsumer() {
         runRetryingConsumer("hpsf-live", retry -> runHpsfLiveConsumerOnce(retry), null);
     }
 
     private void runHpsfCacheConsumerOnce() {
         try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(stringConsumerProperties("hpsf-cache"))) {
             List<TopicPartition> partitions = partitionsFor("hpsf-cache", consumer, hpsfTopics());
             consumer.assign(partitions);
             seekToCacheWindow(consumer, partitions);
             Map<TopicPartition, Long> bootstrapEndOffsets =
                     new LinkedHashMap<>(consumer.endOffsets(partitions));
             boolean live = caughtUp(consumer, bootstrapEndOffsets);
             PartitionRefresh partitionRefresh = new PartitionRefresh("hpsf-cache", hpsfTopics());
             if (live) {
                 markCacheCaughtUp("hpsf", hpsfEvents(), hpsfCaughtUp);
             }
             while (running.get()) {
                 ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(settings.pollMs()));
                 // CACHE consumer: a new partition rebuilds its full window, exactly like bootstrap, and the
                 // cache is NOT complete again until it has. Readiness must drop back to RECOVERING or
                 // clients trust a cache that is missing every strike on the new partitions.
                 Refresh refresh = partitionRefresh.apply(consumer, partitions);
                 partitions = refresh.partitions();
                 if (!refresh.added().isEmpty()) {
                     seekToCacheWindow(consumer, refresh.added());
                     bootstrapEndOffsets.putAll(consumer.endOffsets(refresh.added()));
                     live = false;
                     markCacheRecovering(hpsfCaughtUp);
                 }
                 for (ConsumerRecord<String, String> record : records) {
                     updateHpsfCache(record);
                 }
                 purgeExpiredCache(System.currentTimeMillis());
                 if (!live && caughtUp(consumer, bootstrapEndOffsets)) {
                     live = true;
                     markCacheCaughtUp("hpsf", hpsfEvents(), hpsfCaughtUp);
                 }
             }
         }
     }
 
     private void runHpsfLiveConsumerOnce(boolean retry) {
         try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(stringConsumerProperties("hpsf-live"))) {
             List<TopicPartition> partitions = partitionsFor("hpsf-live", consumer, hpsfTopics());
             consumer.assign(partitions);
             if (retry) {
                 seekToCacheWindow(consumer, partitions);
             } else {
                 consumer.seekToEnd(partitions);
             }
             PartitionRefresh partitionRefresh = new PartitionRefresh("hpsf-live", hpsfTopics());
             while (running.get()) {
@@ -6408,199 +6408,199 @@ public class FeedGatewayService implements ReplayRunner {
                         .map(entry -> new CachedEvent("max-pain", entry.getValue()))
                         .forEach(cachedEvents::add);
                 case "option-price-behavior" -> optionPriceBehaviors.entrySet().stream()
                         .filter(entry -> isCacheFresh("option-price-behavior:" + entry.getKey(), nowMs))
                         .filter(entry -> passesSelectionBarrier("option-price-behavior:" + entry.getKey(), selection, true, false))
                         .filter(entry -> "DATABENTO".equals(selection.source()))
                         .filter(entry -> matchesOptionPriceBehaviorSelection(entry.getValue(), selection))
                         .sorted(Map.Entry.comparingByKey())
                         .map(entry -> new CachedEvent("option-price-behavior", entry.getValue()))
                         .forEach(cachedEvents::add);
                 case "opb-by-option" -> opbByOptions.entrySet().stream()
                         .filter(entry -> isCacheFresh("opb-by-option:" + entry.getKey(), nowMs))
                         .filter(entry -> passesSelectionBarrier("opb-by-option:" + entry.getKey(), selection))
                         .filter(entry -> "DATABENTO".equals(selection.source()))
                         .filter(entry -> matchesCachedSelection(entry.getValue(), selection))
                         .sorted(Map.Entry.comparingByKey())
                         .map(entry -> new CachedEvent("opb-by-option", entry.getValue()))
                         .forEach(cachedEvents::add);
                 case "opb-session" -> opbSessions.entrySet().stream()
                         .filter(entry -> isCacheFresh("opb-session:" + entry.getKey(), nowMs))
                         .filter(entry -> passesSelectionBarrier("opb-session:" + entry.getKey(), selection, true, false))
                         .filter(entry -> "DATABENTO".equals(selection.source()))
                         .filter(entry -> matchesOptionPriceBehaviorSelection(entry.getValue(), selection))
                         .sorted(Map.Entry.comparingByKey())
                         .map(entry -> new CachedEvent("opb-session", entry.getValue()))
                         .forEach(cachedEvents::add);
                 case "hpsf-latest-signal" -> hpsfLatestSignals.entrySet().stream()
                         .filter(entry -> isCacheFresh("hpsf-latest-signal:" + entry.getKey(), nowMs))
                         .sorted(Map.Entry.comparingByKey())
                         .map(entry -> new CachedEvent("hpsf-latest-signal", entry.getValue()))
                         .forEach(cachedEvents::add);
                 case "hpsf-market-flow" -> hpsfMarketFlows.entrySet().stream()
                         .filter(entry -> isCacheFresh("hpsf-market-flow:" + entry.getKey(), nowMs))
                         .sorted(Map.Entry.comparingByKey())
                         .map(entry -> new CachedEvent("hpsf-market-flow", entry.getValue()))
                         .forEach(cachedEvents::add);
                 case "hpsf-top-candidates" -> hpsfTopCandidates.entrySet().stream()
                         .filter(entry -> isCacheFresh("hpsf-top-candidates:" + entry.getKey(), nowMs))
                         .sorted(Map.Entry.comparingByKey())
                         .map(entry -> new CachedEvent("hpsf-top-candidates", entry.getValue()))
                         .forEach(cachedEvents::add);
                 case "hpsf-audit" -> hpsfAudits.entrySet().stream()
                         .filter(entry -> isCacheFresh("hpsf-audit:" + entry.getKey(), nowMs))
                         .sorted(Map.Entry.comparingByKey())
                         .map(entry -> new CachedEvent("hpsf-audit", entry.getValue()))
                         .forEach(cachedEvents::add);
                 case "hpsf-exit-intent" -> hpsfExitIntents.entrySet().stream()
                         .filter(entry -> isCacheFresh("hpsf-exit-intent:" + entry.getKey(), nowMs))
                         .sorted(Map.Entry.comparingByKey())
                         .map(entry -> new CachedEvent("hpsf-exit-intent", entry.getValue()))
                         .forEach(cachedEvents::add);
                 default -> {
                     // Unknown events are ignored because there is no replay cache for them.
                 }
             }
         }
         return cachedEvents;
     }
 
     private boolean isExpired(long eventTime, long nowMs) {
         long ttlMs = settings.cacheTtlMs();
         return ttlMs <= 0 || eventTime < nowMs - ttlMs;
     }
 
     /**
      * The structural option-chain cache events that follow market-aware freshness (10-min RTH, never
      * off-hours). Scoped to exactly the "current-state" caches that are replayed IN FULL on connect — i.e.
      * those EXEMPT from the cached-replay staleness/offset barrier (see {@link #enforceCachedReplayMaxStale}:
      * {@code snapshot} + {@code max-pain}). max-pain keeps its own 12h window; the fast order-flow signals
      * (pace/directional-pressure/strike-flow/gex-by-strike) are deliberately stale-gated by the 15s selection
      * barrier — a 12h-old flow value is misleading — so making them never-evict would retain-but-never-deliver
      * them. Keeping the never-evict policy aligned with what is actually delivered off-hours is the contract.
      */
     private static final Set<String> MARKET_AWARE_CHAIN_EVENTS = Set.of("snapshot");
 
     /**
      * The freshness/seek decision for a cache event. {@code neverEvict} disables staleness eviction entirely
      * (off-hours, so the published strike structure persists) while {@code seekBackMs} keeps the Kafka
      * cache-rebuild window BOUNDED regardless — the two concerns are deliberately decoupled.
      */
-    private record CachePolicy(long ttlMs, boolean neverEvict, long seekBackMs) {
+    record CachePolicy(long ttlMs, boolean neverEvict, long seekBackMs) {   // package-private: the retention test reads it
         static CachePolicy expiring(long ttlMs) {
             return new CachePolicy(ttlMs, false, ttlMs);
         }
 
         static CachePolicy noEviction(long seekBackMs) {
             return new CachePolicy(0L, true, seekBackMs);
         }
     }
 
     /**
      * Effective cache policy for an event type — the ONE seam all freshness flows through (seek window,
      * ingest eviction, periodic purge, cached-state send-filter):
      * <ul>
      *   <li>{@code max-pain}: its own long window ({@link GatewaySettings#maxPainTtlMs()}, 12h) — a slow
      *       daily last-value-wins signal, unchanged.</li>
      *   <li>the structural chain ({@code snapshot}, see {@link #MARKET_AWARE_CHAIN_EVENTS}): MARKET-AWARE.
      *       During regular trading hours a short freshness TTL ({@link GatewaySettings#optionChainRthCacheTtlMs()},
      *       10m); OFF-hours never evicted (so the published strikes stay visible overnight/weekends/holidays),
      *       with a bounded off-hours seek-back ({@link GatewaySettings#optionChainOffHoursSeekBackMs()}, 24h).</li>
      *   <li>everything else (pace/directional-pressure/strike-flow/gex-by-strike/index-price/vix/HPSF/...):
      *       the generic {@link GatewaySettings#cacheTtlMs()} (fast signals also keep their 15s selection barrier).</li>
      * </ul>
      */
     // Test seam: when non-null, forces the market-hours decision (true=RTH, false=off-hours) so the cache
     // POLICY can be tested deterministically without the wall clock. The calendar date/holiday math itself
     // is covered separately by GatewayMarketCalendarTest. Mirrors the other override*ForTest seams.
     private volatile Boolean regularTradingHoursOverrideForTest = null;
 
     void overrideRegularTradingHoursForTest(Boolean regularTradingHours) {
         this.regularTradingHoursOverrideForTest = regularTradingHours;
     }
 
     private boolean isRegularTradingHours(long nowMs) {
         Boolean override = regularTradingHoursOverrideForTest;
         return override != null ? override : marketCalendar.isRegularTradingHours(Instant.ofEpochMilli(nowMs));
     }
 
-    private CachePolicy cachePolicyFor(String event, long nowMs) {
+    CachePolicy cachePolicyFor(String event, long nowMs) {   // package-private: the retention test EXECUTES it
         if ("es-footprint-bar".equals(event) || "es-footprint-outcome".equals(event)) {
             // ES Footprint keyed topics: the cache consumer seeks back a whole session so a restart
             // re-fills both views from the compacted topics; the records never enter the generic cache.
             return CachePolicy.expiring(settings.esFootprintSeekBackMs());
         }
         if ("es-footprint".equals(event) || "es-footprint-evidence".equals(event)) {
             // Live snapshots are never retained: seek END (a heartbeat arrives within 5 s).
             return CachePolicy.expiring(0L);
         }
         if ("option-truth".equals(event)) {
             return CachePolicy.expiring(settings.optionTruthTtlMs());
         }
         if ("zero-dte-intelligence".equals(event)) {
             // This state controls a whole-chain red/green background. Keep its lifetime deliberately
             // short and shared across ingest, purge, reconnect replay, and restart seek-back.
             return CachePolicy.expiring(settings.zeroDteIntelligenceTtlMs());
         }
         if ("max-pain".equals(event)) {
             return CachePolicy.expiring(settings.maxPainTtlMs());
         }
         if ("hot-strike".equals(event)) {
             // The day's hot-strike row stays valid for the whole session (recompute is
             // hourly): 12h window + matching seek-back so a restarted gateway
             // re-bootstraps the CURRENT row instead of waiting for the next compute.
             return CachePolicy.expiring(settings.hotStrikeTtlMs());
         }
         if ("short-premium-recommendation".equals(event)) {
             // A recommendation is emitted once at entry and stays valid for the whole 0DTE session:
             // use a long last-value-wins window (default 12h, like max-pain), NOT the generic 15-min TTL,
             // so the overlay persists and replays on reconnect instead of vanishing minutes after entry.
             return CachePolicy.expiring(settings.shortPremiumRecommendationTtlMs());
         }
         if ("es-open-direction-status".equals(event)) {
             // 60s live heartbeat: SHORT window (default 5 min, the liquidity-heatmap/dealer-ledger
             // freshness class) — NEVER the siblings' 12h window below (this check MUST stay before the
             // isEsOpenDirectionEvent fall-through). A stale status (dead producer, overnight leftover)
             // must read as absent, not replay as current. The ONE seam: drives ingest eviction
             // (updateCache -> isExpired), periodic purge, the isCacheFresh replay gate, and the bounded
             // ~5-min seek-back (windowTtlMsFor -> seekBackMs) after a gateway restart.
             return CachePolicy.expiring(settings.esOpenDirectionStatusTtlMs());
         }
         if ("greek-move-auth".equals(event)) {
             // Move-authenticity CURRENT verdict: SHORT window (default 5 min, the es-open-direction
             // STATUS / liquidity-heatmap / dealer-ledger freshness class) — a verdict is only meaningful
             // while CURRENT. A stale verdict (dead producer, overnight leftover) must read as absent, not
             // replay as live. The ONE seam: drives ingest eviction (updateCache -> isExpired), periodic
             // purge, the isCacheFresh replay gate, and the bounded ~5-min seek-back (windowTtlMsFor ->
             // seekBackMs) after a gateway restart.
             return CachePolicy.expiring(settings.greekMoveAuthTtlMs());
         }
         if ("gamma-leadership".equals(event)) {
             // Gamma-leadership CURRENT: SHORT window — a leadership reading is only meaningful while
             // CURRENT; a dead producer's last value must read as absent, never replay as live.
             return CachePolicy.expiring(settings.gammaLeadershipTtlMs());
         }
         if ("direction".equals(event)) {
             // Candle Direction CURRENT: a minute's decision is only meaningful while CURRENT (3 min).
             return CachePolicy.expiring(settings.directionTtlMs());
         }
         if ("direction-push".equals(event)) {
             return CachePolicy.expiring(settings.directionPushTtlMs());       // A4.8: 30 s on both stamps
         }
         if ("direction-alert".equals(event)) {
             return CachePolicy.expiring(settings.directionAlertTtlMs());      // A4.10: 60 s
         }
         if ("direction-scorecard".equals(event)) {
             return CachePolicy.expiring(settings.directionScorecardTtlMs());
         }
         if ("spot-vol-regime".equals(event)) {
             // Spot-vol regime CURRENT snapshot: SHORT window (default 5 min, the greek-move-auth /
             // es-open-direction STATUS freshness class) — a regime is only meaningful while CURRENT.
             // A stale regime (dead producer, overnight leftover) must read as absent, not replay as
             // live. Same ONE-seam consequences as the sibling above.
             return CachePolicy.expiring(settings.spotVolRegimeTtlMs());
         }
         if ("vol-premium-ivrv".equals(event)) {
             // Vol-premium IV/RV reading: same SHORT freshness class — an implied-vs-realised point
             // is only meaningful while CURRENT, and a dead producer's last reading must read as
             // absent rather than replay as live.
             return CachePolicy.expiring(settings.volPremiumIvrvTtlMs());
@@ -11076,160 +11076,165 @@ public class FeedGatewayService implements ReplayRunner {
 
     /** OK records carry the source's provenance inline (sessionDate + foldPositionMs + foldPrints). */
     /** UNAVAILABLE records carry it under sourceProvenance when any validated record has existed. */
     private static CvdSpxLevelsProvenance provenanceOfUnavailable(com.fasterxml.jackson.databind.JsonNode sp) {
         if (!sp.isObject() || !sp.path("sessionDate").isTextual()) return null;   // textual by contract
         String sd = sp.path("sessionDate").asText("");
         Long pos = levelsInt(sp, "foldPositionMs");
         Long prints = levelsInt(sp, "foldPrints");
         if (!sd.matches("\\d{8}") || !isCalendarDate(sd)
                 || pos == null || pos < 0 || prints == null || prints < 0) {
             return null;
         }
         return new CvdSpxLevelsProvenance(sd, pos, prints);
     }
 
     /**
      * U16 retention (CL-R7 V1, hop-to-hop freshness): accept the record iff it does not REGRESS
      * against what is retained, applying the SAME calendar-aware total order the aligner applies.
      * ONLY the exact tombstone combination resets the baseline; a provenance-less startup-absence
      * record is accepted only when nothing is retained (never a regression to "nothing known").
      * Rejections increment gateway_cvd_spx_levels_position_regressions_total and forward nothing,
      * so a lagging incarnation can never displace a newer attestation on the browser.
      */
     synchronized boolean retainCvdSpxLevels(CvdSpxLevelsAccepted accepted) {
         if (accepted.baselineReset()) {
             cvdSpxLevelsProvenance = null;                       // operator wipe flows through
             cvdSpxLevelsLatest.set(accepted.json());
             return true;
         }
         CvdSpxLevelsProvenance incoming = accepted.provenance();
         if (incoming == null) {
             if (cvdSpxLevelsProvenance != null) {
                 cvdSpxLevelsRegressions.incrementAndGet();
                 return false;
             }
             cvdSpxLevelsLatest.set(accepted.json());
             return true;
         }
         CvdSpxLevelsProvenance held = cvdSpxLevelsProvenance;
         if (held != null && (incoming.crossed(held) || incoming.compareTo(held) < 0)) {
             cvdSpxLevelsRegressions.incrementAndGet();
             return false;
         }
         cvdSpxLevelsProvenance = incoming;
         cvdSpxLevelsLatest.set(accepted.json());
         return true;
     }
 
     /**
      * U16 withdrawal handling: a TOMBSTONE on the levels topic (reset tooling) clears the connect
      * replay AND the retention baseline — a withdrawn record must never be served to a late joiner,
      * and the next record must not be judged against an erased history. Counted so the operation is
      * visible. Non-tombstone unparseable values just count.
      */
     synchronized void evictCvdSpxLevelsTombstone(String event, org.apache.kafka.clients.consumer.ConsumerRecord<String, ?> record) {
         if (!"es-cvd-spx-levels".equals(event)) {
             return;
         }
         // A withdrawal is as governing as a value, so it passes the SAME key gate: a foreign-key
         // tombstone is malformed input, counted and ignored, never an erase of the baseline.
         if (record.value() == null && CVD_SPX_LEVELS_KEY.equals(record.key())) {
             cvdSpxLevelsLatest.set(null);
             cvdSpxLevelsProvenance = null;
         }
         cvdSpxLevelsDrops.incrementAndGet();
     }
 
     java.util.concurrent.atomic.AtomicReference<String> cvdSpxLevelsLatestForTest() {
         return cvdSpxLevelsLatest;
     }
 
     long cvdSpxLevelsDropsForTest() {
         return cvdSpxLevelsDrops.get();
     }
 
     long cvdSpxLevelsRegressionsForTest() {
         return cvdSpxLevelsRegressions.get();
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
         sb.append(sd == null ? "null" : "\"" + sd + "\"").append(",\"hwm\":{");
         boolean first = true;
         for (java.util.Map.Entry<String, Long> e : cvdBarsHighWaterMarks().entrySet()) {
             if (!first) sb.append(',');
             sb.append('\"').append(e.getKey()).append("\":").append(e.getValue());
             first = false;
         }
         sb.append('}');
         if (settings.esCvdSpxLevelsEnabled()) {
             // Verbatim record or an explicit null — the FIELD's presence is the completion signal.
             String levels = cvdSpxLevelsLatest.get();
             sb.append(",\"levels\":").append(levels == null ? "null" : levels);
         }
         if (footprintViews != null) {
             // G-R6: ONE atomic snapshot of the footprint coordinator rides the SAME hello; the field's
             // ABSENCE (flag off) tells the page this gateway has no footprint stream.
             sb.append(",\"footprint\":").append(footprintViews.helloField());
         }
         return sb.append('}').toString();
     }
 
     /** One ATOMIC backfill page: session check, rows, cursor and session stamp under one lock. */
     public record CvdBarsPage(String sessionDate, boolean sessionMismatch,
                               java.util.List<String> bars, Long nextCursor) { }
 
     /**
      * R46 (merge-gate finding 3): the mismatch check, the rows, the response session and the
      * cursor are ONE synchronized snapshot — a rollover can happen before or after this call,
      * never inside it, so a page can never mix sessions or mislabel itself.
      */
     public CvdBarsPage cvdBarsPage(String timeframe, long toMsInclusive, long afterMsExclusive,
                                    int limit, String expectedSessionDate) {
         synchronized (cvdBars) {
             String current = cvdBarsSessionDate;
             if (expectedSessionDate != null && !expectedSessionDate.isEmpty()
                     && current != null && !expectedSessionDate.equals(current)) {
                 return new CvdBarsPage(current, true, java.util.List.of(), null);
             }
             java.util.List<String> out = new java.util.ArrayList<>();
             long lastStart = -1;
             for (java.util.Map.Entry<String, String> e : cvdBars.entrySet()) {
                 int sep = e.getKey().lastIndexOf('|');
                 if (!e.getKey().substring(0, sep).equals(timeframe)) continue;
                 long start = Long.parseLong(e.getKey().substring(sep + 1));
                 if (start <= afterMsExclusive || start > toMsInclusive) continue;
                 out.add(e.getValue());
                 lastStart = start;
                 if (out.size() >= limit) break;
             }
             return new CvdBarsPage(current, false, out, out.size() >= limit ? lastStart : null);
         }
     }
 
     public String cvdBarsSessionDate() { return cvdBarsSessionDate; }
 
     /** Per-timeframe high-water marks of the keyed CVD bar view, for the R46 handshake. */
     public java.util.Map<String, Long> cvdBarsHighWaterMarks() {
         java.util.Map<String, Long> hwm = new java.util.LinkedHashMap<>();
         synchronized (cvdBars) {
             for (String key : cvdBars.keySet()) {
                 int sep = key.lastIndexOf('|');
                 hwm.merge(key.substring(0, sep), Long.parseLong(key.substring(sep + 1)), Math::max);
             }
         }
         return hwm;
     }
 
     private static String text(JsonNode root, String field) {
         JsonNode value = root == null ? null : root.get(field);
         return value == null || value.isNull() ? "" : value.asText("").trim();
     }
 
     private static long longField(JsonNode root, String field, long fallback) {
         JsonNode value = root == null ? null : root.get(field);
         if (value == null || value.isNull()) {
             return fallback;
         }
diff --git a/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java b/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
index f64802f..9ba9b15 100644
--- a/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
+++ b/src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
@@ -252,162 +252,165 @@ class CvdSpxLevelsWiringTest {
         s.evictCvdSpxLevelsTombstone("es-cvd-spx-levels", tombstone);
         assertNull(s.cvdSpxLevelsLatestForTest().get(), "a withdrawn record must never replay");
         assertEquals(1L, s.cvdSpxLevelsDropsForTest());
         // The baseline goes with it: the next record must not be judged against an erased history.
         assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260101", 1, 1))));
         assertEquals(0L, s.cvdSpxLevelsRegressionsForTest());
     }
 
     @Test void otherEventsTombstonesAreIgnored() {
         var s = service();
         s.cvdSpxLevelsLatestForTest().set(levels("OK"));
         ConsumerRecord<String, String> tombstone =
                 new ConsumerRecord<>("es.tape-zones.board", 0, 5L, "ES", null);
         s.evictCvdSpxLevelsTombstone("tapeZones", tombstone);
         s.evictCvdSpxLevelsTombstone(null, tombstone);
         assertEquals(levels("OK"), s.cvdSpxLevelsLatestForTest().get());
         assertEquals(0L, s.cvdSpxLevelsDropsForTest());
     }
 
     @Test void metricsCarryTheAlertGateNames() {
         // The paging-rule conjunction (deploy repo cvd-spx-levels-alerts.yaml) references these
         // EXACT series names; renaming either silently disarms every U16 alert.
         String metrics = service().metrics();
         assertTrue(metrics.contains("\ngateway_cvd_spx_levels_enabled 0"), "flag off by default");
         assertTrue(metrics.contains("\ngateway_cvd_spx_levels_drops_total 0"));
         assertTrue(metrics.contains("\ngateway_cvd_spx_levels_position_regressions_total 0"));
     }
 
     // ---- wiring-shape pins on the source (the es-cvd defect class: right branch, wrong loop) ----
 
     @Test void liveConsumerBindsTheTopicBehindTheFlagAndCacheConsumerDoesNot() throws Exception {
         String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
         int liveBuilder = source.indexOf("private void runJsonStateLiveConsumer()");
         int liveRun = source.indexOf("runLiveConsumer(\"state-live\"", liveBuilder);
         assertTrue(liveBuilder >= 0 && liveRun > liveBuilder);
         String builder = source.substring(liveBuilder, liveRun);
         assertTrue(builder.contains("esCvdSpxLevelsEnabled()")
                         && builder.contains("\"es-cvd-spx-levels\""),
                 "the delivery branch lives in the LIVE loop, so the LIVE consumer must subscribe");
         // The es-cvd/es-cvd-bar wiring was centralised into addEsCvdTopics() so the bootstrap and
         // live topic sets cannot drift. Pin the invariant that still matters — the LIVE builder
         // wires those topics, and the shared helper binds both — instead of grepping for literal
         // put() calls in this method body, which a refactor legitimately moves.
         assertTrue(builder.contains("addEsCvdTopics("),
                 "defect fix: the LIVE consumer must wire es-cvd/es-cvd-bar (via addEsCvdTopics)");
         int helper = source.indexOf("private void addEsCvdTopics(");
         assertTrue(helper >= 0, "addEsCvdTopics helper must exist");
         int helperEnd = source.indexOf("\n    }", helper);
         assertTrue(helperEnd > helper, "addEsCvdTopics body must be locatable");
         String helperBody = source.substring(helper, helperEnd);
         assertTrue(helperBody.contains("settings.esCvdTopic()") && helperBody.contains("settings.esCvdBarsTopic()"),
                 "addEsCvdTopics must bind BOTH es-cvd and es-cvd-bar");
         int cacheBuilder = source.indexOf("private void runJsonStateCacheConsumer()");
         int cacheRun = source.indexOf("runAssignedCacheConsumer(\"state\"", cacheBuilder);
         assertFalse(source.substring(cacheBuilder, cacheRun).contains("es-cvd-spx-levels"),
                 "updateCache has no case for the levels event; cache subscription would only churn");
     }
 
     @Test void deliveryBroadcastsVerbatimBeforeTheGenericCacheKeyGate() throws Exception {
         String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
         int direct = source.indexOf("if (\"es-cvd-spx-levels\".equals(binding.event()))");
         int generic = source.indexOf("String cacheKey = updateCache(binding, record, json);", direct);
         assertTrue(direct >= 0 && generic > direct);
         String branch = source.substring(direct, generic);
         assertTrue(branch.contains("validateCvdSpxLevels(") && branch.contains("record.key()")
                         && branch.contains("retainCvdSpxLevels(accepted)"),
                 "the schema gate (fed the KAFKA KEY) and the regression gate both precede the broadcast");
         assertTrue(branch.contains("broadcast(binding.event(), json);"));
         assertTrue(FeedGatewayServiceSourcePins.isRawPassThrough(source),
                 "producer-authored attestation must reach the browser byte-untouched");
     }
 
     @Test void connectReplayRidesInsideTheHelloContract() throws Exception {
         // CL-R8/G19: the levels record replays INSIDE cvd-hello, so "hello carried no levels
         // record" (levels: null) is distinguishable from "replay still pending" — that is what
         // lets the page choose `no_data` instead of staying blank forever.
         String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
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
 
     @Test void helloCarriesTheLevelsFieldOnlyWhenEnabled() {
         var off = service();
         assertFalse(off.cvdHelloJson().contains("levels"), "field absent while the flag is off");
 
         System.setProperty("GATEWAY_ES_CVD_SPX_LEVELS_ENABLED", "true");
         try {
             var on = service();
             assertTrue(on.cvdHelloJson().contains("\"levels\":null"),
                     "an explicit null IS the completion signal: hello arrived, no record retained");
             on.retainCvdSpxLevels(on.validateCvdSpxLevels(ok("20260817", 1000, 5)));
             assertTrue(on.cvdHelloJson().contains("\"foldPositionMs\":1000"), "retained record rides verbatim");
         } finally {
             System.clearProperty("GATEWAY_ES_CVD_SPX_LEVELS_ENABLED");
         }
     }
 
     /** Tiny helper so the pin reads as one assertion. */
     private static final class FeedGatewayServiceSourcePins {
         static boolean isRawPassThrough(String source) {
             int m = source.indexOf("private static boolean isRawPassThroughEvent(String event)");
             int end = source.indexOf('}', m);
             return m >= 0 && source.substring(m, end).contains("\"es-cvd-spx-levels\"");
         }
     }
 
     @Test void startupHydrationIsWiredBehindTheFlag() throws Exception {
         // Without it the only subscriber is the live consumer, which starts at seekToEnd: a
         // restarted gateway would answer every hello with levels:null until the next heartbeat —
         // and if the aligner is down after committing, forever.
         String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
         int start = source.indexOf("public void start()");
         int end = source.indexOf("runSelectionConsumer);", start);
         String head = source.substring(start, end);
         assertTrue(head.contains("esCvdSpxLevelsEnabled()") && head.contains("hydrateCvdSpxLevels"),
                 "hydration runs at startup, behind the flag, before clients can connect");
 
         int hydrate = source.indexOf("void hydrateCvdSpxLevels()");
         int hydrateEnd = source.indexOf("static long resolveCvdSpxLevelsSeek", hydrate);
         assertTrue(hydrate >= 0 && hydrateEnd > hydrate);
         String body = source.substring(hydrate, hydrateEnd);
         assertTrue(body.contains("infos.size() != 1"), "the single-partition contract is re-asserted");
         assertTrue(body.contains("validateCvdSpxLevels(CVD_SPX_LEVELS_KEY, latestValue)"),
                 "the hydrated record goes through the SAME gate as a live one");
         assertTrue(body.contains("retainCvdSpxLevels(accepted)"), "and through the same retention");
     }
 
     @Test void theStateFieldMatrixIsEnforcedInBothDirections() {
         var s = service();
         // UNAVAILABLE-only fields on an OK record — malformed WHATEVER their value, because the
         // record reaches the browser verbatim and would read as a state it is not in.
         for (String field : new String[] { "reason", "sourceProvenance", "provenanceRetained", "baselineReset" }) {
             String value = "provenanceRetained".equals(field) || "baselineReset".equals(field) ? "false"
                     : ("reason".equals(field) ? "\"source_stale\"" : "null");
             assertNull(s.validateCvdSpxLevels(ok("20260817", 1000, 5)
                             .replace("\"state\":\"OK\"", "\"state\":\"OK\",\"" + field + "\":" + value)),
                     field + " belongs to UNAVAILABLE only");
         }
         // OK-only fields on an UNAVAILABLE record, provenance included.
         for (String injected : new String[] { "\"sessionDate\":\"20260817\"", "\"foldPositionMs\":10",
                 "\"foldPrints\":2", "\"flowEventTimeMs\":1", "\"basisState\":\"MEASURED\"" }) {
             assertNull(s.validateCvdSpxLevels(unavailable("source_stale", "null", false, false)
                             .replace("\"state\":\"UNAVAILABLE\"", "\"state\":\"UNAVAILABLE\"," + injected)),
                     injected + " is OK-only");
         }
     }
 
     @Test void everyTranslatedPriceObeysItsDeclaredRange() {
         var s = service();
         long max = FeedGatewayService.CVD_SPX_LEVELS_MAX_PRICE_CENTS;
         assertNotNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]", "null", String.valueOf(max))),
                 "the top of the range is legal");
         assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]", "null", String.valueOf(max + 1))),
                 "a balance above 10,000,000 cents is not an SPX price");
         assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1,
                         "[" + level(max + 1, 900) + "]", "[]", "null", "null")));
         assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]",
diff --git a/src/test/java/app/feedgateway/FootprintViewsTest.java b/src/test/java/app/feedgateway/FootprintViewsTest.java
index 3b666c5..a8d1eee 100644
--- a/src/test/java/app/feedgateway/FootprintViewsTest.java
+++ b/src/test/java/app/feedgateway/FootprintViewsTest.java
@@ -1,147 +1,172 @@
 package app.feedgateway;
 
 import static org.junit.jupiter.api.Assertions.*;
 
 import com.fasterxml.jackson.databind.ObjectMapper;
 import java.util.List;
 import org.junit.jupiter.api.Test;
 
 /** ES-FOOTPRINT-GATEWAY-DESIGN.md G-R4/G-R5/G-R6/G-R7/G-R8 — the coordinator, without a broker. */
 class FootprintViewsTest {
 
     private static FootprintViews views() { return new FootprintViews(new ObjectMapper(), 262_144, 128L << 20, 12_000, 16L << 20, 20_000); }
 
     static String bar(String session, String tf, long start) { return bar(session, tf, start, "x"); }
     static String bar(String session, String tf, long start, String tag) {
         return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"" + tf + "\",\"sessionDate\":\"" + session
                 + "\",\"observations\":{\"barStartMs\":" + start + "},\"tag\":\"" + tag + "\"}";
     }
     static String outcome(String session, String tf, long resolved, String identity) { return outcome(session, tf, resolved, identity, "x"); }
     static String outcome(String session, String tf, long resolved, String identity, String tag) {
         return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"" + tf + "\",\"sessionDate\":\"" + session
                 + "\",\"identity\":\"" + identity + "\",\"resolvedAtBarStartMs\":" + resolved + ",\"outcome\":\"HELD\",\"tag\":\"" + tag + "\"}";
     }
 
     // ---- G-R4 admission / shape ------------------------------------------------------------------
 
     @Test void shapeDropsNeverMutate() {
         FootprintViews v = views();
         for (String bad : new String[]{"not json", "{\"unrelated\":true}",
                 bar("z", "1m", 1), bar("2026-8-14", "1m", 1), bar("20260814", "1m", 1), bar("2026-02-30", "1m", 1),
                 bar("2026-08-14", "2m", 1), "{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{}}",
                 "{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":\"1\"}}",
                 "{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":-1}}",
                 "{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":253402300800000}}",
                 "{\"timeframe\":\"1m\",\"observations\":{\"barStartMs\":1}}"}) {
             assertEquals(FootprintViews.Reason.SHAPE, v.admitBar(bad).reason(), bad);
         }
         assertNull(v.sessionDate(), "a shape drop never anchors the session");
         assertEquals(0, v.barsInView());
         assertEquals(FootprintViews.Reason.SHAPE, v.admitOutcome(outcome("2026-08-14", "1m", 5, "bad identity!")).reason());
         assertEquals(FootprintViews.Reason.SHAPE, v.admitOutcome(outcome("2026-08-14", "1m", 5, "a".repeat(129))).reason());
         assertEquals(FootprintViews.Reason.SHAPE, v.admitOutcome("{\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"identity\":\"x\"}").reason());
         assertEquals(0, v.outcomesInView());
     }
 
     @Test void oversizeIsDroppedEntirelyAndNeverRollsTheSession() {
         FootprintViews v = new FootprintViews(new ObjectMapper(), 200, 1 << 20, 100, 1 << 20, 100);
         String big = bar("2026-08-14", "1m", 1, "y".repeat(300));
         assertEquals(FootprintViews.Reason.OVERSIZE, v.admitBar(big).reason());
         assertEquals(FootprintViews.Reason.OVERSIZE, v.admitOutcome(outcome("2026-08-14", "1m", 1, "ES|1m|1|BUYERS|LEVEL|1|1", "y".repeat(300))).reason());
         assertNull(v.sessionDate());
         String exact = bar("2026-08-14", "1m", 1, "y".repeat(200 - bar("2026-08-14", "1m", 1, "").length()));
         assertEquals(200, FootprintViews.utf8Length(exact));
         assertEquals(FootprintViews.Reason.ADMITTED, v.admitBar(exact).reason(), "at the ceiling is legal");
     }
 
     @Test void lastWritePerKeyWinsAndKeysAreFixedWidth() {
         FootprintViews v = views();
         assertEquals(FootprintViews.Reason.ADMITTED, v.admitBar(bar("2026-08-14", "30s", 1000, "first")).reason());
         assertEquals(FootprintViews.Reason.ADMITTED, v.admitBar(bar("2026-08-14", "30s", 1000, "second")).reason());
         assertEquals(1, v.barsInView());
         assertTrue(v.barsPage("30s", Long.MAX_VALUE, -1, 10, "").records().get(0).contains("\"tag\":\"second\""));
         assertEquals("30s|0000000000000001000", FootprintViews.barKey("30s", 1000));
         assertEquals("1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000",
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
         FootprintViews v = views();
         v.admitBar(bar("2026-08-14", "1m", 1)); v.admitOutcome(outcome("2026-08-14", "1m", 1, "a"));
         FootprintViews.Admission roll = v.admitBar(bar("2026-08-17", "5m", 9));
         assertTrue(roll.rolledOver()); assertEquals("2026-08-17", v.sessionDate());
         assertEquals(1, v.barsInView()); assertEquals(0, v.outcomesInView(), "the outcomes view rolled with the bars view");
         assertEquals(1, v.rollovers());
         assertEquals(FootprintViews.Reason.STALE_SESSION, v.admitOutcome(outcome("2026-08-14", "1m", 2, "b")).reason(), "late record from a dead session");
         assertEquals(FootprintViews.Reason.STALE_SESSION, v.admitBar(bar("2026-08-14", "1m", 2)).reason());
         assertFalse(v.admitOutcome(outcome("2026-08-17", "1m", 3, "c")).rolledOver());
         assertEquals(1, v.outcomesInView());
     }
 
     @Test void outOfOrderBootstrapAcrossTopicsInBothOrders() {
         // bar of N+1 before outcomes of N+1, then an outcome of N: dropped; and the mirror order
         FootprintViews v = views();
         v.admitOutcome(outcome("2026-08-14", "1m", 1, "n")); v.admitBar(bar("2026-08-14", "1m", 1));
         v.admitBar(bar("2026-08-15", "1m", 2));                      // N+1 arrives on the bars topic first
         assertEquals(FootprintViews.Reason.STALE_SESSION, v.admitOutcome(outcome("2026-08-14", "1m", 1, "late")).reason());
         assertEquals(FootprintViews.Reason.ADMITTED, v.admitOutcome(outcome("2026-08-15", "1m", 2, "n1")).reason());
         FootprintViews w = views();
         w.admitBar(bar("2026-08-14", "1m", 1));
         w.admitOutcome(outcome("2026-08-15", "1m", 2, "n1"));         // N+1 arrives on the outcomes topic first
         assertEquals("2026-08-15", w.sessionDate()); assertEquals(0, w.barsInView());
         assertEquals(FootprintViews.Reason.STALE_SESSION, w.admitBar(bar("2026-08-14", "1m", 3)).reason());
     }
 
     @Test void yearAndMonthBoundariesOrderCorrectly() {
         FootprintViews v = views();
         v.admitBar(bar("2026-12-31", "1m", 1));
         assertTrue(v.admitBar(bar("2027-01-01", "1m", 2)).rolledOver());
         assertEquals(FootprintViews.Reason.STALE_SESSION, v.admitBar(bar("2026-12-31", "1m", 3)).reason());
         assertNull(FootprintViews.parseCanonicalDate("2026-02-30"));
         assertNull(FootprintViews.parseCanonicalDate("2026-2-3"));
         assertNotNull(FootprintViews.parseCanonicalDate("2024-02-29"));
         assertNull(FootprintViews.parseCanonicalDate("2023-02-29"));
     }
 
     // ---- G-R6 hello --------------------------------------------------------------------------------
 
     @Test void helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks() {
         FootprintViews v = views();
         assertEquals("{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}", v.helloField());
         v.admitBar(bar("2026-08-14", "30s", 1000)); v.admitBar(bar("2026-08-14", "30s", 2000)); v.admitBar(bar("2026-08-14", "1m", 5));
         v.admitOutcome(outcome("2026-08-14", "5m", 77, "ES.v.0|5m|1|SELLERS|STACK|1|2")); v.admitOutcome(outcome("2026-08-14", "5m", 70, "z"));
         assertEquals("{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}", v.helloField());
     }
 
     // ---- G-R7 pages ------------------------------------------------------------------------------
 
     /**
      * G-R7's bars cursor is EXCLUSIVE, and the guard that keeps it safe at the top of the epoch
      * domain is {@code afterMsExclusive < EPOCH_MAX_MS}. Nothing held it: no test passed a cursor
      * anywhere near the domain edge, so deleting it changed nothing any assertion could see.
      *
      * <p>What it prevents is not a boundary nicety. Without it, {@code afterMs = Long.MAX_VALUE}
      * takes the {@code + 1}, wraps to {@code Long.MIN_VALUE}, and {@code barKey}'s {@code %019d}
      * renders a NEGATIVE cursor that sorts below every real key — so {@code lo <= hi} holds and the
      * page returns the WHOLE VIEW instead of nothing. A client sending a nonsense cursor would be
      * handed every bar the gateway is holding.
      *
      * <p>Relaxing the SAME comparison from {@code <} to {@code <=} is INERT, and that is written
      * down here so it is not rediscovered: at {@code afterMs == EPOCH_MAX_MS} the branch is entered,
      * but {@code from} becomes {@code EPOCH_MAX_MS + 1} — no overflow, no wrap — while {@code hi} is
      * capped at {@code EPOCH_MAX_MS}, so {@code lo > hi} and the page is empty either way. That
      * mutation survives and says nothing about the code. The overflow lives only at
      * {@code Long.MAX_VALUE}.
      */
     @Test void theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain() {
         FootprintViews v = views();
         for (long s = 1000; s <= 3000; s += 1000) v.admitBar(bar("2026-08-14", "30s", s));
         // A record AT the top of the domain, so the exclusive comparison has something to include or
         // exclude. Without it every cursor in this test would return an empty page for any
         // implementation, and the assertions would pin nothing.
         v.admitBar(bar("2026-08-14", "30s", FootprintViews.EPOCH_MAX_MS));
 
         // EXCLUSIVE: a cursor one BELOW the top must still return the record at the top.
         FootprintViews.BarsPage below = v.barsPage("30s", Long.MAX_VALUE, FootprintViews.EPOCH_MAX_MS - 1, 10, "");
diff --git a/src/test/java/app/feedgateway/FootprintWiringTest.java b/src/test/java/app/feedgateway/FootprintWiringTest.java
index 7c26e3f..d40d839 100644
--- a/src/test/java/app/feedgateway/FootprintWiringTest.java
+++ b/src/test/java/app/feedgateway/FootprintWiringTest.java
@@ -1,182 +1,227 @@
 package app.feedgateway;
 
 import static org.junit.jupiter.api.Assertions.*;
 
 import com.fasterxml.jackson.databind.ObjectMapper;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.util.List;
 import java.util.regex.Matcher;
 import java.util.regex.Pattern;
 import org.junit.jupiter.api.Test;
 
 /**
  * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R1/G-R2/G-R3/G-R6/G-R9/G-R10/G-R11 — the wiring-shape pins, the
  * flag-off identity, the hello field, the admission path both consumers share, and the exact
  * metrics contract, without a broker.
  */
 class FootprintWiringTest {
 
     private static final Path SERVICE = Path.of("src/main/java/app/feedgateway/FeedGatewayService.java");
     private static final Path CONTROLLER = Path.of("src/main/java/app/feedgateway/GatewayController.java");
 
     private static FeedGatewayService off() {
         return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
     }
 
     static FeedGatewayService on() {
         FootprintTopicGateTest.FakeReader r = new FootprintTopicGateTest.FakeReader();
         for (String t : List.of("futures.footprint", "futures.footprint.evidence", "futures.footprint.bars", "futures.footprint.outcomes")) r.valid(t);
         return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, r);
     }
 
     private static int occurrences(String text, String needle) {
         int count = 0;
         for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
         return count;
     }
 
     // ---- G-R2: one wiring path, both state consumers, all four topics unconditionally --------------
 
     @Test void bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics() throws Exception {
         String src = Files.readString(SERVICE);
         assertEquals(2, occurrences(src, "addEsFootprintTopics(topicEvents);"), "called from the state cache AND the state live consumer");
         assertEquals(2, occurrences(src, "addEsCvdTopics(topicEvents);\n        addEsFootprintTopics(topicEvents);"), "right after the CVD wiring at both sites");
-        int start = src.indexOf("private void addEsFootprintTopics(");
+        int start = src.indexOf("void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {");
         String body = src.substring(start, src.indexOf("\n    }", start));
         assertTrue(body.contains("if (footprintViews == null) return;"), "flag off adds nothing");
         for (String e : new String[]{"es-footprint\"", "es-footprint-evidence\"", "es-footprint-bar\"", "es-footprint-outcome\""}) {
             assertEquals(1, occurrences(body, "new TopicBinding(\"DATABENTO\", \"" + e + ")"), e);
         }
         assertFalse(body.contains("admit("), "the topic SET is unconditional; admission gates consumption, not discovery");
     }
 
     @Test void thePartitionRefreshPredicateSeamSitsBeforeMergeAndAssignAndBothStateSitesPassTheGate() throws Exception {
         String src = Files.readString(SERVICE);
         assertEquals(2, occurrences(src, "new PartitionRefresh(name, topicEvents.keySet(), footprintTopicAdmit())"), "the two STATE consumers pass the gate");
         assertEquals(4, occurrences(src, "new PartitionRefresh(\"") + occurrences(src, "new PartitionRefresh(name, topicEvents.keySet());"),
                 "the other four sites keep the always-true predicate (through the two-argument constructor)");
         int apply = src.indexOf("Refresh apply(KafkaConsumer<?, ?> consumer, List<TopicPartition> assigned)");
         int filter = src.indexOf("if (topicAdmit.test(p.topic())) admitted.add(p);", apply);
         int added = src.indexOf("List<TopicPartition> added = addedPartitions(assigned, admitted);", apply);
         int assign = src.indexOf("consumer.assign(merged);", apply);
         assertTrue(apply > 0 && filter > apply && added > filter && assign > added, "gate → added() → merge/assign, in that order inside apply()");
         assertEquals(2, occurrences(src, "bootstrapAssign(name, consumer, topicEvents)"), "both state consumers bootstrap through the ONE filtered assign seam");
         int seam = src.indexOf("List<TopicPartition> bootstrapAssign(String name, KafkaConsumer<?, ?> consumer, Map<String, TopicBinding> topicEvents) {");
         String seamBody = src.substring(seam, src.indexOf("\n    }", seam));
         assertTrue(seamBody.indexOf("footprintAdmitted(partitionsFor(") < seamBody.indexOf("consumer.assign(partitions);"), "filtered BEFORE the assign");
     }
 
     @Test void deliveryClassesAreWiredAsDesigned() throws Exception {
         String src = Files.readString(SERVICE);
         int live = src.indexOf("if (footprintViews != null && isFootprintEvent(binding.event())) {");
         int cvd = src.indexOf("if (\"es-cvd\".equals(binding.event())) {");
         assertTrue(live > 0 && live < cvd, "the live-consumer footprint branch precedes the es-cvd branch");
         String branch = src.substring(live, src.indexOf("continue;", live));
         assertTrue(branch.contains("onFootprintLiveRecord(binding.event(), json);"), "the live branch delegates to the one admit-then-broadcast method");
         int method = src.indexOf("boolean onFootprintLiveRecord(String event, String json) {");
         String body = src.substring(method, src.indexOf("\n    }", method));
         assertTrue(body.indexOf("admitFootprintRecord(event, json, \"live\")") < body.indexOf("broadcast(event, json);"), "view first, then broadcast");
         assertTrue(body.contains("if (!admitFootprintRecord(event, json, \"live\")) return false;"), "an oversize record is never broadcast");
         int cache = src.indexOf("admitFootprintRecord(binding.event(), json, \"cache\");");
         assertTrue(cache > 0 && cache < src.indexOf("updateCache(binding, record, json);", cache), "the cache consumer admits before the generic cache and never broadcasts");
         int raw = src.indexOf("private static boolean isRawPassThroughEvent(String event)");
         assertTrue(src.substring(raw, src.indexOf("}", raw)).contains("isFootprintEvent(event)"), "verbatim: never enriched");
         int allow = src.indexOf("\"es-cvd-bar\",");
         String after = src.substring(allow, allow + 600);
         for (String e : new String[]{"\"es-footprint\",", "\"es-footprint-evidence\",", "\"es-footprint-bar\",", "\"es-footprint-outcome\","}) assertTrue(after.contains(e), e + " allowlisted");
     }
 
     // ---- G-R1/G-R10: flag off is byte-identical ----------------------------------------------------
 
     @Test void flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge() {
         FeedGatewayService s = off();
         assertFalse(s.footprintEnabled());
         assertNull(s.footprintViews());
         assertEquals("{\"sessionDate\":null,\"hwm\":{}}", s.cvdHelloJson(), "today's hello, byte for byte");
         String m = s.footprintMetricsText();
         assertTrue(m.endsWith("gateway_footprint_enabled 0\n"));
         assertEquals(1, m.lines().filter(l -> !l.startsWith("#")).count(), "exactly one footprint series flag-off");
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
         FeedGatewayService s = on();
         assertEquals("{\"sessionDate\":null,\"hwm\":{},\"footprint\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}}", s.cvdHelloJson());
         assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "1m", 60_000), "live"));
         assertTrue(s.cvdHelloJson().endsWith("\"footprint\":{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"1m\":60000},\"outcomeHwm\":{}}}"));
     }
 
     // ---- G-R3/G-R9: the shared admission path and overlap identities --------------------------------
 
     @Test void admissionCountsAndBroadcastDecisionsFollowTheOverlapRules() {
         FeedGatewayService s = on();
         assertTrue(s.admitFootprintRecord("es-footprint", "{\"schemaVersion\":6}", "live"), "live snapshots are broadcast, never admitted");
         assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "1m", 1), "live"));
         assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-13", "1m", 1), "live"), "stale: dropped from the view, still broadcast");
         assertTrue(s.admitFootprintRecord("es-footprint-outcome", "{\"unrelated\":true}", "cache"), "shape: dropped from the view (cache path never broadcasts anyway)");
         assertFalse(s.admitFootprintRecord("es-footprint-outcome", "{\"pad\":\"" + "y".repeat(300_000) + "\"}", "live"), "oversize: dropped entirely");
         String m = s.footprintMetricsText();
         assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint\",consumer=\"live\"} 1\n"), m);
         assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint-bar\",consumer=\"live\"} 2\n"));
         assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-bar\",consumer=\"live\",reason=\"stale_session\"} 1\n"));
         assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-outcome\",consumer=\"cache\",reason=\"shape\"} 1\n"));
         assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-outcome\",consumer=\"live\",reason=\"oversize\"} 1\n"));
         assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-bar\",consumer=\"cache\",reason=\"stale_session\"} 0\n"));
         assertTrue(m.contains("gateway_footprint_bars_in_view 1\n"));
         assertEquals(1, s.footprintViews().barsInView());
     }
 
     @Test void bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions() {
         FootprintTopicGateTest.FakeReader r = new FootprintTopicGateTest.FakeReader();
         r.valid("futures.footprint").valid("futures.footprint.evidence").valid("futures.footprint.bars").unknown("futures.footprint.outcomes");
         FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, r);
         List<org.apache.kafka.common.TopicPartition> resolved = List.of(
                 new org.apache.kafka.common.TopicPartition("futures.cvd", 0),
                 new org.apache.kafka.common.TopicPartition("futures.footprint.bars", 0),
                 new org.apache.kafka.common.TopicPartition("futures.footprint.outcomes", 0));
         List<org.apache.kafka.common.TopicPartition> admitted = s.footprintAdmitted(resolved);
         assertEquals(List.of(resolved.get(0), resolved.get(1)), admitted, "the absent/unvalidated topic's partition is withheld; others pass");
         r.valid("futures.footprint.outcomes");
         assertEquals(resolved, s.footprintAdmitted(resolved), "admitted on the next evaluation once validated");
         assertEquals(resolved, off().footprintAdmitted(resolved), "flag off: identity");
     }
 
     // ---- G-R9/G-R11 (12): the exact contract flag-on at start-up -----------------------------------
 
     @Test void everyMetricsSeriesIsExportedWithEveryLabelValueAtZeroAtStartUp() {
         String m = on().footprintMetricsText();
         String[] events = {"es-footprint", "es-footprint-evidence", "es-footprint-bar", "es-footprint-outcome"};
         String[] topics = {"futures.footprint", "futures.footprint.evidence", "futures.footprint.bars", "futures.footprint.outcomes"};
         java.util.List<String> expect = new java.util.ArrayList<>();
         expect.add("gateway_footprint_enabled 1");
         for (String e : events) for (String c : new String[]{"cache", "live"}) expect.add("gateway_footprint_records_total{event=\"" + e + "\",consumer=\"" + c + "\"} 0");
         for (String e : new String[]{"es-footprint-bar", "es-footprint-outcome"}) for (String c : new String[]{"cache", "live"}) for (String r : new String[]{"oversize", "shape", "stale_session"})
             expect.add("gateway_footprint_drops_total{event=\"" + e + "\",consumer=\"" + c + "\",reason=\"" + r + "\"} 0");
         for (String e : events) expect.add("gateway_footprint_broadcast_total{event=\"" + e + "\"} 0");
         expect.add("gateway_footprint_evictions_total{view=\"bars\"} 0"); expect.add("gateway_footprint_evictions_total{view=\"outcomes\"} 0");
         expect.add("gateway_footprint_rollovers_total 0"); expect.add("gateway_footprint_bars_in_view 0"); expect.add("gateway_footprint_outcomes_in_view 0");
         expect.add("gateway_footprint_view_bytes{view=\"bars\"} 0"); expect.add("gateway_footprint_view_bytes{view=\"outcomes\"} 0");
         for (String r : new String[]{"bars", "outcomes"}) expect.add("gateway_footprint_backfill_requests_total{route=\"" + r + "\"} 0");
         for (String r : new String[]{"bars", "outcomes"}) for (String x : new String[]{"busy", "bad_cursor", "session_mismatch"}) expect.add("gateway_footprint_backfill_rejected_total{route=\"" + r + "\",reason=\"" + x + "\"} 0");
         for (String t : topics) expect.add("gateway_footprint_topic_validated{topic=\"" + t + "\"} 0");
         for (String t : topics) for (String r : new String[]{"admin", "unknown", "ceiling", "compression"}) expect.add("gateway_footprint_topic_validation_failures_total{topic=\"" + t + "\",reason=\"" + r + "\"} 0");
         List<String> actual = m.lines().filter(l -> !l.startsWith("#")).toList();
         assertEquals(expect, actual, "exact series set, order and initial values");
         for (String name : List.of("gateway_footprint_enabled", "gateway_footprint_records_total", "gateway_footprint_drops_total", "gateway_footprint_broadcast_total",
                 "gateway_footprint_evictions_total", "gateway_footprint_rollovers_total", "gateway_footprint_bars_in_view", "gateway_footprint_outcomes_in_view",
                 "gateway_footprint_view_bytes", "gateway_footprint_backfill_requests_total", "gateway_footprint_backfill_rejected_total",
                 "gateway_footprint_topic_validated", "gateway_footprint_topic_validation_failures_total")) {
             assertEquals(1, occurrences(m, "# TYPE " + name + " "), name + " typed once");
         }
     }
 
     @Test void validationSeriesFollowTheGateAfterStartUp() {
         FootprintTopicGateTest.FakeReader r = new FootprintTopicGateTest.FakeReader();
         r.valid("futures.footprint").valid("futures.footprint.evidence").valid("futures.footprint.bars").unknown("futures.footprint.outcomes");
         FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, r);
         s.footprintGate().validateExisting();
         assertFalse(s.footprintGate().admit("futures.footprint.outcomes"));
         String m = s.footprintMetricsText();

2026-09-08T22:10:55.921225Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "python3 -c 'import json; p=json.load(open(\"ES-FOOTPRINT-CAMPAIGN.json\")); bad=[]
for k,v in p.items():
 fl=\"\\\\n\".join(v.get(\"evidence\",{}).get(\"failureLines\",[]))
 for n in v.get(\"assertionFailures\",[]):
  if n.split(\".\")[-1] not in fl: bad.append((k,n))
print(\"bad assertion links\",bad)
from collections import Counter,defaultdict
c=Counter(v[\"status\"] for v in p.values()); print(c)
d=defaultdict(list)
for k,v in p.items(): d[v[\"requirement\"]].append(k)
for x in sorted(d): print(x,len(d[x]),*d[x],sep=\" | \")'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
bad assertion links []
Counter({'KILLED': 33, 'SURVIVED': 1})
G-R1 | 1 | G-R1.1 the-flag-gates-every-footprint-object
G-R10 | 2 | G-R10.1 a-footprint-record-never-escapes-the-consumer-loop | G-R10.2 every-admission-failure-is-a-counted-drop
G-R2 | 2 | G-R2.1 one-wiring-method-binds-all-four | G-R2.2 the-evidence-binding
G-R3 | 3 | G-R3.1 an-oversize-record-is-dropped-not-broadcast | G-R3.2 a-rejected-record-is-still-broadcast | G-R3.3 live-snapshots-are-never-retained
G-R4 | 4 | G-R4 newer-date-rolls-both | G-R4 older-date-dropped | G-R4 equal-date-upserts | G-R4 key-fields-must-be-in-the-epoch-domain
G-R5 | 4 | G-R5.1 the-outcome-key-orders-numerically | G-R5.2 the-identity-is-the-last-component | G-R5.3 a-foreign-identity-is-refused | G-R5.4 last-write-per-key-wins
G-R6 | 3 | G-R6.1 the-hello-carries-the-footprint-field | G-R6.2 the-hello-is-sent-when-footprint-alone-is-on | G-R6.3 the-hello-carries-the-outcome-high-water-mark
G-R7 | 5 | G-R7 to-is-inclusive | G-R7 limit-clamped-low | G-R7 limit-clamped-high | G-R7 the-exclusive-cursor-at-the-domain-edge site1-removed | G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed
G-R8 | 5 | G-R8 evict-oldest | G-R8 evict-rotates | G-R8 byte-budget-decremented | G-R8 both-budgets-enforced | G-R8 evictions-counted
G-R8a | 2 | G-R8a ceiling-strict | G-R8a codec-allowlist
G-R9 | 3 | G-R9.3 a-rollover-is-counted | G-R9 ceiling-precedence | G-R9.2 every-record-is-counted-before-admission

2026-09-08T22:11:03.654183Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "sed -n '1,220p' scripts/footprint-reqstate.sh; git diff --check origin/main...HEAD; git diff --numstat origin/main...HEAD" in /private/tmp/oe-conf-gw
 succeeded in 3594ms:
2026-09-09 00:11:07.032 xcodebuild[98110:267093477]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:11:07.218 xcodebuild[98110:267093473] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-QeCwBKjW' (errno=Operation not permitted)
2026-09-09 00:11:07.689 xcodebuild[98131:267093554]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:11:07.835 xcodebuild[98131:267093553] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-YOlk4dlZ' (errno=Operation not permitted)
2026-09-09 00:11:08.877 xcodebuild[98145:267093622]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:11:09.080 xcodebuild[98145:267093621] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-qNYzXcsE' (errno=Operation not permitted)
2026-09-09 00:11:09.511 xcodebuild[98151:267093655]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:11:09.658 xcodebuild[98151:267093654] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
1521	311	ES-FOOTPRINT-CAMPAIGN.json
40	0	ES-FOOTPRINT-GATEWAY-DESIGN.md
143	17	scripts/footprint-mutate.py
14	0	scripts/footprint-reqstate.preamble
155	0	scripts/footprint-reqstate.py
69	0	scripts/footprint-reqstate.sh
9	4	src/main/java/app/feedgateway/FeedGatewayService.java
5	2	src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
25	0	src/test/java/app/feedgateway/FootprintViewsTest.java
46	1	src/test/java/app/feedgateway/FootprintWiringTest.java

2026-09-08T22:11:10.535249Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "python3 scripts/footprint-reqstate.py ES-FOOTPRINT-CAMPAIGN.json ES-FOOTPRINT-GATEWAY-DESIGN.md 'G-R\\d+[a-z]?' '{\"G-R.*\":\"2\"}' '{\"G-R1|G-R2\":\"Flag and wiring\",\"G-R3|G-R4|G-R5\":\"Delivery and views\",\"G-R6\":\"Hello\",\"G-R7\":\"Backfill routes\",\"G-R8|G-R8a\":\"Deployment contingency\",\"G-R9\":\"Metrics\",\"G-R10\":\"Non-interference\",\"G-R11\":\"Tests\"}' '{\"G-R11\":\"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break\"}' | shasum -a 256; awk '/"'^'"\\| id \\| Conformance/{p=1} p{print} /"'^does not carry it'"\\."'$/{exit}'"' ES-FOOTPRINT-GATEWAY-DESIGN.md | shasum -a 256" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
1e304bba779818a25c17da9ad4116bc96626e881b7297fde43739e781ad2940d  -
2cfb6f228bf0b86b7973c0497bbe53f546e9542b87ebd75b0b5c61803022eca9  -

2026-09-08T22:11:17.366248Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
2026-09-08T22:11:23.041583Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "python3 scripts/footprint-reqstate.py ES-FOOTPRINT-CAMPAIGN.json ES-FOOTPRINT-GATEWAY-DESIGN.md 'G-R\\d+[a-z]?' '{\"G-R.*\":\"2\"}' '{\"G-R1|G-R2\":\"Flag and wiring\",\"G-R3|G-R4|G-R5\":\"Delivery and views\",\"G-R6\":\"Hello\",\"G-R7\":\"Backfill routes\",\"G-R8|G-R8a\":\"Deployment contingency\",\"G-R9\":\"Metrics\",\"G-R10\":\"Non-interference\",\"G-R11\":\"Tests\"}' '{\"G-R11\":\"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break\"}'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
| id | Conformance | Gate | Disposition |
|----|-------------|------|-------------|
| G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
| G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
| G-R7 | 4 of 5 probes pinned, over 4 clauses (1 survived) | 2 | Backfill routes |
| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
| G-R9 | 3 of 3 probes pinned, over 3 clauses | 2 | Metrics |
| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
| G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | Deployment contingency |

12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).

Read the state column narrowly. "n of m probes pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION (or propagate a throw the spec declared and the run matched) — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.

2026-09-08T22:11:28.007984Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "rg -n '\"G-R8 (evict-oldest|evict-rotates|byte-budget-decremented|evictions-counted)\"|\"clause\": \"the view byte budgets' ES-FOOTPRINT-CAMPAIGN.json; rg -n 'groups =|shares almost nothing|judgement made in review' scripts/footprint-reqstate.py scripts/footprint-reqstate.preamble" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
108: "G-R8 evict-oldest": {
110:  "clause": "the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes",
166: "G-R8 evict-rotates": {
168:  "clause": "the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes",
219: "G-R8 byte-budget-decremented": {
221:  "clause": "the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes",
325: "G-R8 evictions-counted": {
327:  "clause": "the view byte budgets are enforced under the lock at every admission, so evicting lowers the bytes",
scripts/footprint-reqstate.py:74:                problems.append(f"{rid}: the quoted obligation shares almost nothing with the clause "
scripts/footprint-reqstate.py:127:            groups = len({re.sub(r' (?:site|variant)\d+\S*$', '', k) for k in keys[rid]})
scripts/footprint-reqstate.py:128:            state = f"{killed} of {len(ms)} probes pinned, over {groups} clause" + ("" if groups == 1 else "s")

2026-09-08T22:11:40.568647Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
Found one blocking campaign-record defect:

- [ES-FOOTPRINT-CAMPAIGN.json:110](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:110), plus lines 168 and 327, gives three different probes the same inaccurate clause: “byte budgets … so evicting lowers the bytes.” The mutations actually probe oldest-key selection, round-robin rotation, and eviction-counter increments, respectively. Their broad `documentText` contains the real obligations, so the vocabulary floor passes, but the recorded clauses do not describe what those probes break. Line 221 is appropriate only for the byte-accounting mutation. This contradicts the claim that every probe is recorded against its actual clause.

Everything else checked out:

- All 34 quotations are verbatim within their assigned requirements.
- Probe totals are 34: 33 `KILLED`, one `SURVIVED`; row totals reconcile.
- G-R7 correctly groups its two domain-edge sites into one clause, yielding five probes over four clauses.
- Every named assertion failure in every `KILLED` record appears in that record’s own `failureLines`.
- Direct generator execution accepted the record and reproduced the committed table. The wrapper’s `--check` could not create its temporary directory under this read-only review environment.
- The preamble accurately says that vocabulary matching cannot establish semantic attribution.

Much remains unprobed: most individual obligations within G-R1–G-R10/G-R8a, including many flag-off effects, settings/defaults, admission-shape cases, atomic locking, route/auth/concurrency/streaming order, heap and Kafka bounds, startup layout checks, topic-refresh behavior, metric label completeness and identities, and lock-free sending. G-R11 is wholly unprobed because it is a test inventory. The document is honest at the requirement level: it explicitly says the rows cover only mutated clauses and do not establish whole-requirement conformance, though it does not enumerate these remaining clauses.

VERDICT: REQUEST_CHANGES
tokens used
72.540
Found one blocking campaign-record defect:

- [ES-FOOTPRINT-CAMPAIGN.json:110](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:110), plus lines 168 and 327, gives three different probes the same inaccurate clause: “byte budgets … so evicting lowers the bytes.” The mutations actually probe oldest-key selection, round-robin rotation, and eviction-counter increments, respectively. Their broad `documentText` contains the real obligations, so the vocabulary floor passes, but the recorded clauses do not describe what those probes break. Line 221 is appropriate only for the byte-accounting mutation. This contradicts the claim that every probe is recorded against its actual clause.

Everything else checked out:

- All 34 quotations are verbatim within their assigned requirements.
- Probe totals are 34: 33 `KILLED`, one `SURVIVED`; row totals reconcile.
- G-R7 correctly groups its two domain-edge sites into one clause, yielding five probes over four clauses.
- Every named assertion failure in every `KILLED` record appears in that record’s own `failureLines`.
- Direct generator execution accepted the record and reproduced the committed table. The wrapper’s `--check` could not create its temporary directory under this read-only review environment.
- The preamble accurately says that vocabulary matching cannot establish semantic attribution.

Much remains unprobed: most individual obligations within G-R1–G-R10/G-R8a, including many flag-off effects, settings/defaults, admission-shape cases, atomic locking, route/auth/concurrency/streaming order, heap and Kafka bounds, startup layout checks, topic-refresh behavior, metric label completeness and identities, and lock-free sending. G-R11 is wholly unprobed because it is a test inventory. The document is honest at the requirement level: it explicitly says the rows cover only mutated clauses and do not establish whole-requirement conformance, though it does not enumerate these remaining clauses.

VERDICT: REQUEST_CHANGES
