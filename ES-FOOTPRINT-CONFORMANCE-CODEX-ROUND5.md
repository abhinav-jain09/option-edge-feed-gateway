Reading additional input from stdin...
2026-09-08T21:56:02.866444Z ERROR codex_models_manager::manager: failed to load models cache: missing field `supports_parallel_tool_calls` at line 93 column 5
OpenAI Codex v0.147.0
--------
workdir: /private/tmp/oe-conf-gw
model: gpt-5.6-sol
provider: openai
approval: never
sandbox: read-only
reasoning effort: low
reasoning summaries: none
session id: 01a08305-623e-7582-9dc4-acab9a100e93
--------
user
Round 5 of the review of option-edge-feed-gateway PR #174 at /private/tmp/oe-conf-gw, branch
test/footprint-conformance-pins. Round 4 gave one HIGH and three MEDIUM.

What was done — VERIFY EACH against the files and the record:
 1. HIGH "a rollover is counted is G-R9's clause, not G-R4's" — re-filed as G-R9.3, quoting G-R9's
    own rollovers_total sentence. G-R4 now reads 3/3, G-R9 3/3.
 2. MEDIUM "the three G-R7 epoch-guard records are counted as three clauses" — renamed as variants of
    one clause; the generator groups " variantN-..." exactly as it groups " siteN".
 3. MEDIUM "several quotations do not quote the recorded obligation" — every G-R7 and G-R4 probe
    re-quoted against the sentence that states its clause.
 4. MEDIUM "the renderer never validates documentText; it groups by the editable requirement field" —
    the generator now REFUSES to emit when a quoted obligation is not verbatim in the requirement the
    row sits under, and when a record names an assertion failure its own failureLines do not carry.
    The harness stores enough failure lines to substantiate every name it records, and records only
    the names those lines carry.

Then review the WHOLE change once more, line by line. For EVERY row of §2a, check: the obligation
quoted is verbatim in that requirement AND is the clause the probe exercises; the probes under it
exercise that requirement; the counts are right. Read ES-FOOTPRINT-CAMPAIGN.json failureLines for
anything you doubt. Say explicitly what remains unprobed and whether the document is honest about it.

End with exactly one line: VERDICT: APPROVE  or  VERDICT: REQUEST_CHANGES
codex
I’ll verify the branch diff, the review record, the generator/harness safeguards, and then audit every §2a row against the cited requirement and campaign evidence.
exec
/bin/bash -lc "pwd && rg --files -g 'AGENTS.md' -g '"'!node_modules'"' -g '"'!vendor'"' && git status --short --branch && git diff --stat && git log --oneline -8" in /private/tmp/oe-conf-gw
 exited 1 in 0ms:
/private/tmp/oe-conf-gw

exec
/bin/bash -lc 'find .. -name AGENTS.md -print

git status --short --branch
git diff --stat
git log --oneline -10' in /private/tmp/oe-conf-gw
 succeeded in 11028ms:
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-iM5tftE6' (errno=Operation not permitted)
2026-09-08 23:56:18.724 xcodebuild[57120:266924149]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:56:18.870 xcodebuild[57120:266924147] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-n5P5cCqF' (errno=Operation not permitted)
2026-09-08 23:56:19.308 xcodebuild[57123:266924171]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:56:19.452 xcodebuild[57123:266924170] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
## test/footprint-conformance-pins...origin/test/footprint-conformance-pins
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-Kgbaj7xl' (errno=Operation not permitted)
2026-09-08 23:56:20.469 xcodebuild[57130:266924227]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:56:20.628 xcodebuild[57130:266924225] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-W7FnLwGu' (errno=Operation not permitted)
2026-09-08 23:56:21.106 xcodebuild[57145:266924288]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:56:21.258 xcodebuild[57145:266924287] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-ThXHEvQi' (errno=Operation not permitted)
2026-09-08 23:56:22.407 xcodebuild[57159:266924369]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:56:22.577 xcodebuild[57159:266924366] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-4Ya4eebT' (errno=Operation not permitted)
2026-09-08 23:56:23.035 xcodebuild[57163:266924407]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-08 23:56:23.183 xcodebuild[57163:266924406] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
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

exec
/bin/bash -lc "rg --files | sed -n '1,240p'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md
src/main/java/app/feedgateway/FootprintTopicGate.java
src/main/java/app/feedgateway/stockgex/StockGexController.java
src/main/java/app/feedgateway/stockgex/StockGexAsyncConfig.java
src/main/java/app/feedgateway/stockgex/StockGexConfig.java
src/main/java/app/feedgateway/stockgex/StockGexUpstream.java
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
GATEWAY-EPOCH-CURSOR-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND1.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND3.md
src/main/java/app/feedgateway/SellerActivityDiskStore.java
src/main/java/app/feedgateway/GatewayController.java
src/main/java/app/feedgateway/GatewaySettings.java
src/main/java/app/feedgateway/OutboundChannel.java
src/main/java/app/feedgateway/FeedGatewayService.java
src/main/java/app/feedgateway/ApprovalReaper.java
src/main/java/app/feedgateway/mtsession/ConcurrencyLimits.java
src/main/resources/application.properties
src/main/java/app/feedgateway/mtsession/approval/RoleClaimApprovalAuthority.java
src/main/java/app/feedgateway/mtsession/approval/HttpApprovalAuthority.java
src/main/java/app/feedgateway/mtsession/approval/ApprovalAuthority.java
dev/keycloak/run-keycloak.sh
dev/keycloak/realm-export.json
src/main/java/app/feedgateway/mtsession/gateway/WsTicketService.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayService.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayParams.java
src/main/java/app/feedgateway/mtsession/gateway/LogoutController.java
src/main/java/app/feedgateway/mtsession/gateway/MtSessionAuthConfig.java
src/main/java/app/feedgateway/mtsession/gateway/WsTicketController.java
src/main/java/app/feedgateway/mtsession/gateway/OrchestratorReplayRunLister.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayRunAuthorizer.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayRunLister.java
src/main/java/app/feedgateway/mtsession/gateway/MtSessionSecurityInvariant.java
src/main/java/app/feedgateway/mtsession/gateway/TicketSubProtocolHandshakeHandler.java
src/main/java/app/feedgateway/mtsession/gateway/DealerLedgerJoiner.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayRunView.java
src/main/java/app/feedgateway/mtsession/gateway/GatewayRecordMapper.java
src/main/java/app/feedgateway/mtsession/gateway/OrchestratorReplayRunAuthorizer.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayChronology.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayTopicResolver.java
src/main/java/app/feedgateway/mtsession/gateway/TicketHandshakeInterceptor.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayRunner.java
src/main/java/app/feedgateway/mtsession/gateway/ReplayController.java
src/main/java/app/feedgateway/mtsession/gateway/HandshakeTicketAuthenticator.java
src/main/java/app/feedgateway/mtsession/MarketDataSource.java
src/main/java/app/feedgateway/mtsession/NotEntitledException.java
src/main/java/app/feedgateway/mtsession/RoutingTarget.java
src/main/java/app/feedgateway/mtsession/RoutingKeyDeriver.java
src/main/java/app/feedgateway/mtsession/EntitlementPolicy.java
src/main/java/app/feedgateway/mtsession/UnderlyingKey.java
src/main/java/app/feedgateway/mtsession/EventType.java
src/main/java/app/feedgateway/mtsession/StrikeWindow.java
src/main/java/app/feedgateway/mtsession/TicketStore.java
deploymodel.md
src/main/java/app/feedgateway/mtsession/ApprovalStateMachine.java
GATEWAY-SUREFIRE-ROUTING-CODEX-ROUND1.md
src/main/java/app/feedgateway/mtsession/ApprovalState.java
Dockerfile
src/main/java/app/feedgateway/mtsession/RoutableRecord.java
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND4.md
src/main/java/app/feedgateway/mtsession/Normalization.java
ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md
src/main/java/app/feedgateway/mtsession/UserSessionPolicy.java
GATEWAY-EPOCH-CURSOR-CODEX-ROUND1.md
src/main/java/app/feedgateway/mtsession/SubscriptionManager.java
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND2.md
src/main/java/app/feedgateway/mtsession/SharedSnapshotCache.java
ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md
src/main/java/app/feedgateway/mtsession/InMemoryTicketStore.java
ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md
src/main/java/app/feedgateway/mtsession/Selection.java
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND1.md
src/main/java/app/feedgateway/mtsession/NotApprovedException.java
GATEWAY-EPOCH-CURSOR-CODEX-ROUND4.md
src/main/java/app/feedgateway/mtsession/AppSession.java
ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND3.md
src/main/resources/static/vendor/react-dom.production.min.js
src/main/resources/static/vendor/react.production.min.js
src/main/resources/static/app.html
src/main/resources/static/option-chain.js
src/main/resources/static/login.js
src/main/resources/static/option-chain.css
src/main/resources/static/app-boot.js
src/main/resources/static/index.html
Jenkinsfile.deploy
Jenkinsfile
accelration.md
dev/raw-feed-replicator/pom.xml
src/main/java/app/feedgateway/mtsession/auth/KeycloakJwtVerifier.java
src/main/java/app/feedgateway/mtsession/auth/VerifiedPrincipal.java
src/main/java/app/feedgateway/mtsession/auth/TokenVerifier.java
src/main/java/app/feedgateway/mtsession/auth/JwtVerificationException.java
src/main/java/app/feedgateway/mtsession/SessionRoutingEngine.java
src/main/java/app/feedgateway/mtsession/WsTicket.java
src/main/java/app/feedgateway/mtsession/SharedUnderlyings.java
src/main/java/app/feedgateway/mtsession/CapacityException.java
src/main/java/app/feedgateway/mtsession/RedisTicketStore.java
src/main/java/app/feedgateway/mtsession/SubscriptionKey.java
scripts/footprint-reqstate.sh
scripts/footprint-reqstate.preamble
scripts/footprint-mutate.py
scripts/footprint-conformance.py
scripts/footprint-reqstate.py
ES-FOOTPRINT-GATEWAY-CODEX-ROUND9.md
GATEWAY-SUREFIRE-ROUTING-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md
MULTI-REPLICA.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md
README.md
pom.xml
dev/raw-feed-replicator/dependency-reduced-pom.xml
src/main/java/app/feedgateway/selleractivity/SellerActivityController.java
dev/redis/run-redis.sh
src/main/java/app/feedgateway/selleractivity/SellerActivityAggregator.java
src/main/java/app/feedgateway/WsJwtHandshakeInterceptor.java
ES-FOOTPRINT-CAMPAIGN.json
src/main/java/app/feedgateway/SecurityHeadersFilter.java
src/main/java/app/feedgateway/FeedGatewayApplication.java
GATEWAY-EPOCH-CURSOR-CODEX-ROUND3.md
src/main/java/app/feedgateway/SessionExpiryReaper.java
ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md
ES-FOOTPRINT-GATEWAY-DESIGN.md
ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md
ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND2.md
src/main/java/app/feedgateway/AvroJson.java
src/main/java/app/feedgateway/HpsfGatewayViewMapper.java
src/main/java/app/feedgateway/AudienceValidator.java
src/main/java/app/feedgateway/GatewayMarketCalendar.java
src/main/java/app/feedgateway/FeedWebSocketHandler.java
src/main/java/app/feedgateway/gammamigration/GammaMigrationController.java
src/main/java/app/feedgateway/gammamigration/GammaRotationController.java
src/main/java/app/feedgateway/gammamigration/GammaFragilityController.java
src/main/java/app/feedgateway/systemstatus/SystemStatusFailure.java
src/main/java/app/feedgateway/systemstatus/SystemStatusConfig.java
src/main/java/app/feedgateway/systemstatus/SystemStatusDataSource.java
src/main/java/app/feedgateway/systemstatus/SystemStatusStore.java
src/main/java/app/feedgateway/systemstatus/ConsumerLagReader.java
src/main/java/app/feedgateway/systemstatus/SystemStatusController.java
src/main/java/app/feedgateway/contexttape/ContextTapeUpstream.java
src/main/java/app/feedgateway/contexttape/ContextTapeConfig.java
src/main/java/app/feedgateway/contexttape/ContextTapeController.java
src/main/java/app/feedgateway/pinflow/PinFlowResponse.java
src/main/java/app/feedgateway/pinflow/PinFlowDataSourceConfig.java
src/main/java/app/feedgateway/pinflow/PinFlowController.java
src/main/java/app/feedgateway/pinflow/PinFlowStore.java
src/main/java/app/feedgateway/pinflow/PinFlowExecutor.java
src/main/java/app/feedgateway/pinflow/PinFlowAggregator.java
src/test/java/app/feedgateway/FeedGatewayShutdownTest.java
dev/raw-feed-replicator/src/test/java/app/feedgateway/dev/RawFeedReplicatorSafetyTest.java
dev/raw-feed-replicator/src/main/java/app/feedgateway/dev/RawFeedReplicator.java
src/test/java/app/feedgateway/HotStrikeEventTest.java
src/test/java/app/feedgateway/SellerActivityCacheIntegrationTest.java
src/test/java/app/feedgateway/SessionExpirySweepTest.java
src/test/java/app/feedgateway/OutboundChannelTest.java
src/test/java/app/feedgateway/contexttape/ContextTapeAuthAllowlistRegressionTest.java
src/test/java/app/feedgateway/stockgex/StockGexMvcTest.java
src/test/java/app/feedgateway/contexttape/ContextTapeMvcTest.java
src/test/java/app/feedgateway/contexttape/ContextTapeControllerTest.java
src/test/java/app/feedgateway/FootprintViewsTest.java
src/test/java/app/feedgateway/FeedGatewayRolloverDiagnosticsTest.java
src/test/java/app/feedgateway/CvdSpxLevelsWiringTest.java
src/test/java/app/feedgateway/WsTokenExpiryTest.java
src/test/java/app/feedgateway/GatewayCalendarConfigTest.java
src/test/java/app/feedgateway/LiquidityHistoryWsChainsTest.java
src/test/java/app/feedgateway/GatewayContextSmokeTest.java
src/test/java/app/feedgateway/StaticAssetSecurityTest.java
src/test/java/app/feedgateway/FootprintSeamTest.java
src/test/java/app/feedgateway/DeltaFlowAccelWiringTest.java
src/test/java/app/feedgateway/FeedGatewayReplayCompletionTest.java
src/test/java/app/feedgateway/SpotBandForwardingTest.java
src/test/java/app/feedgateway/ApprovalReaperTest.java
src/test/java/app/feedgateway/WsJwtHandshakeInterceptorTest.java
src/test/java/app/feedgateway/SecurityHeadersFilterTest.java
src/test/java/app/feedgateway/HpsfGatewayViewMapperTest.java
src/test/java/app/feedgateway/PartitionExpansionIntegrationTest.java
src/test/java/app/feedgateway/FeedWebSocketHandlerAttachTest.java
src/test/java/app/feedgateway/FootprintWiringTest.java
src/test/java/app/feedgateway/stockgex/StockGexAuthAllowlistRegressionTest.java
src/test/java/app/feedgateway/stockgex/StockGexControllerTest.java
src/test/java/app/feedgateway/stockgex/StockGexAsyncConfigTest.java
src/test/java/app/feedgateway/stockgex/StockGexAsyncPipelineTest.java
src/test/java/app/feedgateway/stockgex/StockGexCloseBoardTest.java
src/test/java/app/feedgateway/EsAggressorFlowWiringTest.java
src/test/java/app/feedgateway/gammamigration/GammaMigrationControllerTest.java
src/test/java/app/feedgateway/gammamigration/GammaRotationControllerTest.java
src/test/java/app/feedgateway/gammamigration/GammaFragilityControllerTest.java
src/test/java/app/feedgateway/mtsession/approval/ApprovalAuthorityTest.java
src/test/java/app/feedgateway/liquidityhistory/HistoryFrameFailClosedTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryTestSupport.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryControllerTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryStoreEpochTest.java
src/test/java/app/feedgateway/liquidityhistory/LiquidityHistoryFoldTest.java
src/test/java/app/feedgateway/pinflow/PinFlowAggregatorTest.java
src/test/java/app/feedgateway/pinflow/PinFlowDataSourceConfigTest.java
src/test/java/app/feedgateway/pinflow/PinFlowSessionWindowTest.java
src/test/java/app/feedgateway/pinflow/PinFlowSpotSymbolBindingTest.java
src/test/java/app/feedgateway/pinflow/PinFlowControllerTest.java
src/test/java/app/feedgateway/EsAuctionWiringTest.java
src/test/java/app/feedgateway/pinflow/PinFlowSpotHistoryTest.java
src/test/java/app/feedgateway/FeedGatewayServiceTest.java
src/test/java/app/feedgateway/pinflow/PinFlowAuthAllowlistRegressionTest.java
src/test/java/app/feedgateway/GatewayMarketCalendarTest.java
src/test/java/app/feedgateway/pinflow/PinFlowStoreTest.java
src/test/java/app/feedgateway/FeedGatewayReplayCancellationTest.java
src/test/java/app/feedgateway/GatewayIsolationLevelTest.java
src/test/java/app/feedgateway/CvdBarViewTest.java
src/test/java/app/feedgateway/FootprintBackfillControllerTest.java
src/test/java/app/feedgateway/FeedGatewayLogoutTest.java
src/test/java/app/feedgateway/FootprintTopicGateTest.java
src/test/java/app/feedgateway/mtsession/SessionLifecycleTest.java
src/test/java/app/feedgateway/mtsession/gateway/GatewayRecordMapperTest.java
src/test/java/app/feedgateway/mtsession/gateway/WsTicketControllerTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayTopicResolverTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayControllerTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayParamsTest.java
src/test/java/app/feedgateway/mtsession/gateway/MtSessionAuthConfigTicketStoreTest.java
src/test/java/app/feedgateway/mtsession/gateway/HandshakeTicketAuthenticatorTest.java
src/test/java/app/feedgateway/mtsession/gateway/GatewayContextBootIT.java
src/test/java/app/feedgateway/mtsession/gateway/DealerLedgerJoinerTest.java
src/test/java/app/feedgateway/mtsession/gateway/WsTicketServiceTest.java
src/test/java/app/feedgateway/mtsession/gateway/OrchestratorReplayRunAuthorizerTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayChronologyTest.java
src/test/java/app/feedgateway/mtsession/gateway/MtSessionSecurityInvariantTest.java
src/test/java/app/feedgateway/mtsession/gateway/TicketSubProtocolHandshakeHandlerTest.java
src/test/java/app/feedgateway/mtsession/gateway/ReplayServiceTest.java
src/test/java/app/feedgateway/mtsession/gateway/TicketHandshakeInterceptorTest.java
src/test/java/app/feedgateway/mtsession/gateway/OrchestratorReplayRunListerTest.java
src/test/java/app/feedgateway/mtsession/RoutingKeyDeriverTest.java
src/test/java/app/feedgateway/mtsession/SubscriptionManagerTest.java
src/test/java/app/feedgateway/mtsession/InMemoryTicketStoreTest.java

exec
/bin/bash -lc 'command -v git
ls -l $(command -v git)
ls /opt/homebrew/bin/git /usr/local/bin/git 2>/dev/null || true
sed -n '"'1,260p' ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
/usr/bin/git
-rwxr-xr-x  78 root  wheel  118640 Aug 13 04:51 /usr/bin/git
# ES Footprint gateway design — Codex round 10 (2026-09-07)

Reviewed: revision 10. Verdict: APPROVE.

Round-9 disposition verified. G-R9 now fully defines both topic-validation series, including label domains, initialization, increment semantics, precedence, and flag-off behavior. G-R11(12) and G-R11(17) pin initial, failure, precedence, and successful-validation values.

Full-document consistency pass found no new findings.

VERDICT: APPROVE

exec
/bin/bash -lc "rg -n \"§2a|2a|G-R9\\.3|variant[0-9]|failureLines|documentText|quoted obligation|obligation\" . -g '*.md' -g '*.py' -g '*.json' -g '*.java'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
./src/main/java/app/feedgateway/FeedGatewayService.java:4944:     * Rollover-diagnostics (Codex round-2 P2a): true only when {@code readySelectionKey} matches the CURRENT
./src/main/java/app/feedgateway/FeedGatewayService.java:14017:    // Codex round-2 P2a test seam: drive the applySelection-without-reset path so a test can verify
./src/main/java/app/feedgateway/FeedGatewayService.java:14031:    // Codex round-2 P2a: swap the active selection WITHOUT clearing readySelectionKey (mirrors the
./scripts/footprint-mutate.py:269:                  'documentText': m.get('documentText'),
./scripts/footprint-mutate.py:280:                               'failureLines': failure_lines(out, kind),
./src/test/java/app/feedgateway/FeedGatewayRolloverDiagnosticsTest.java:173:        // Codex round-2 P2a: applySelection intentionally does NOT reset readySelectionKey, so the
./scripts/footprint-conformance.py:7:  * an obligation that is not a verbatim substring of the requirement it is attached to — the failure
./scripts/footprint-conformance.py:54:            text = v.get('documentText')
./scripts/footprint-conformance.py:58:                problems.append(f"{key}: obligation is not verbatim in {rid}"); continue
./scripts/footprint-reqstate.py:39:    # The requirement text as the DOCUMENT states it, so an obligation can be checked against the
./scripts/footprint-reqstate.py:52:            # ATTRIBUTION, for every record whatever its status: the obligation a row quotes must be
./scripts/footprint-reqstate.py:56:            dt = (m.get('documentText') or '').strip()
./scripts/footprint-reqstate.py:58:                problems.append(f"{rid}: a record quotes no obligation")
./scripts/footprint-reqstate.py:62:                problems.append(f"{rid}: the quoted obligation is not verbatim in {rid}: {dt[:70]!r}")
./scripts/footprint-reqstate.py:66:            fl = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
./scripts/footprint-reqstate.py:74:                lines = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
./scripts/footprint-reqstate.py:82:            if not m.get('evidence', {}).get('failureLines'):
./ES-FOOTPRINT-GATEWAY-DESIGN.md:166:## 2a. Conformance — what a test actually holds
./ES-FOOTPRINT-CAMPAIGN.json:6:  "documentText": "**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.",
./ES-FOOTPRINT-CAMPAIGN.json:38:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:40:   "mutatedFileSha256": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc",
./ES-FOOTPRINT-CAMPAIGN.json:45:     "mutated": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc"
./ES-FOOTPRINT-CAMPAIGN.json:50:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:63:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:66:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:74:  "documentText": "Rollover is MONOTONIC on the parsed date: NEWER than current \u21d2 BOTH views are cleared and the date advances;",
./ES-FOOTPRINT-CAMPAIGN.json:97:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:107:   "outputSha256": "e828bf491785b2f2126bd6f670f68a848e95719ff059004acee12a6a0db75304",
./ES-FOOTPRINT-CAMPAIGN.json:109:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:116:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:119:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:127:  "documentText": "**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.",
./ES-FOOTPRINT-CAMPAIGN.json:165:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:175:   "outputSha256": "f26d8491495969b2b8b532267b45f3799611bce6b6046220a2a02040e60890e9",
./ES-FOOTPRINT-CAMPAIGN.json:177:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:196:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:199:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:203: "G-R9.3 a-rollover-is-counted": {
./ES-FOOTPRINT-CAMPAIGN.json:207:  "documentText": "`gateway_footprint_evictions_total{view}` \u2014 records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).",
./ES-FOOTPRINT-CAMPAIGN.json:230:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:242:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:249:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:252:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:260:  "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
./ES-FOOTPRINT-CAMPAIGN.json:286:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:298:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:307:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:310:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:318:  "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
./ES-FOOTPRINT-CAMPAIGN.json:341:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:353:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:360:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:363:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:371:  "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
./ES-FOOTPRINT-CAMPAIGN.json:394:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:404:   "outputSha256": "9a312fb53d5937f2cf251ef66e8324bddf9f9227ed2af402a2244d5da58ac882",
./ES-FOOTPRINT-CAMPAIGN.json:406:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:413:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:416:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:424:  "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
./ES-FOOTPRINT-CAMPAIGN.json:447:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:459:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:466:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:469:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:477:  "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
./ES-FOOTPRINT-CAMPAIGN.json:503:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:515:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:524:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:527:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:535:  "documentText": "**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes \u2264 GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type \u2208 {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.",
./ES-FOOTPRINT-CAMPAIGN.json:571:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:583:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:604:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:607:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:615:  "documentText": "**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes \u2264 GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type \u2208 {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.",
./ES-FOOTPRINT-CAMPAIGN.json:638:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:640:   "mutatedFileSha256": "88113e222a9afc61792000b26899359fc51afc02b5fac10a12f82c0cf8b52ede",
./ES-FOOTPRINT-CAMPAIGN.json:645:     "mutated": "88113e222a9afc61792000b26899359fc51afc02b5fac10a12f82c0cf8b52ede"
./ES-FOOTPRINT-CAMPAIGN.json:650:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:657:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:660:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:668:  "documentText": "`gateway_footprint_topic_validation_failures_total{topic,reason}` \u2014 `reason \u2208 {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic \u2014 timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin \u2192 unknown \u2192 ceiling \u2192 compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).",
./ES-FOOTPRINT-CAMPAIGN.json:691:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:693:   "mutatedFileSha256": "0f5935f573879932d3c401a5d1ad1589021d2a10ec5c4c6c0f814e71d53ff4b8",
./ES-FOOTPRINT-CAMPAIGN.json:698:     "mutated": "0f5935f573879932d3c401a5d1ad1589021d2a10ec5c4c6c0f814e71d53ff4b8"
./ES-FOOTPRINT-CAMPAIGN.json:703:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:710:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:713:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:721:  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
./ES-FOOTPRINT-CAMPAIGN.json:750:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:762:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:773:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:776:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:784:  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
./ES-FOOTPRINT-CAMPAIGN.json:807:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:817:   "outputSha256": "4fc86784c016c4b742506831aec4dc4cb74a2efb2f16792a33d3c2584aa54d3d",
./ES-FOOTPRINT-CAMPAIGN.json:819:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:826:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:829:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:837:  "documentText": "`limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is \u2264 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.",
./ES-FOOTPRINT-CAMPAIGN.json:860:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:870:   "outputSha256": "32f283a64f9113e4641aa9bda6f70edfa2a23510ad96755a11e7f41c99139a33",
./ES-FOOTPRINT-CAMPAIGN.json:872:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:879:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:882:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:886: "G-R7 the-epoch-guard variant1-guard": {
./ES-FOOTPRINT-CAMPAIGN.json:890:  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
./ES-FOOTPRINT-CAMPAIGN.json:913:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:925:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:932:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:935:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:939: "G-R7 the-epoch-guard variant2-removed": {
./ES-FOOTPRINT-CAMPAIGN.json:943:  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
./ES-FOOTPRINT-CAMPAIGN.json:966:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:978:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:985:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:988:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:992: "G-R7 the-epoch-guard variant3-relaxed": {
./ES-FOOTPRINT-CAMPAIGN.json:996:  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
./ES-FOOTPRINT-CAMPAIGN.json:1013:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1025:   "failureLines": [],
./ES-FOOTPRINT-CAMPAIGN.json:1029:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1032:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1040:  "documentText": "Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{\"enabled\":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.",
./ES-FOOTPRINT-CAMPAIGN.json:1077:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1087:   "outputSha256": "1f97e002a97bd17d315fc8bb5f7e65b595d0780fc3e06c88d1636f3fe97bd360",
./ES-FOOTPRINT-CAMPAIGN.json:1089:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1108:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1111:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1119:  "documentText": "ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.",
./ES-FOOTPRINT-CAMPAIGN.json:1142:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1144:   "mutatedFileSha256": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817",
./ES-FOOTPRINT-CAMPAIGN.json:1149:     "mutated": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817"
./ES-FOOTPRINT-CAMPAIGN.json:1154:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1161:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1164:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1172:  "documentText": "**Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` \u2192 `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` \u2192 `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` \u2192 `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` \u2192 `futures.footprint.outcomes` (`es-footprint-outcome`).",
./ES-FOOTPRINT-CAMPAIGN.json:1198:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1200:   "mutatedFileSha256": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8",
./ES-FOOTPRINT-CAMPAIGN.json:1205:     "mutated": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8"
./ES-FOOTPRINT-CAMPAIGN.json:1210:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1219:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1222:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1230:  "documentText": "`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) \u2014 a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).",
./ES-FOOTPRINT-CAMPAIGN.json:1256:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1268:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1277:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1280:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1288:  "documentText": "`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) \u2014 a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).",
./ES-FOOTPRINT-CAMPAIGN.json:1314:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1326:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1335:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1338:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1346:  "documentText": "**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every \u2264 5 s \u2014 F-R15 \u2014 so a new client waits at most one heartbeat; the same class as `es-cvd`).",
./ES-FOOTPRINT-CAMPAIGN.json:1369:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1379:   "outputSha256": "5c2844947204c0a114280ae01dc24075e6c0694e0c5cf88b2aabf7fb61c1e4c7",
./ES-FOOTPRINT-CAMPAIGN.json:1381:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1388:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1391:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1399:  "documentText": "The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;",
./ES-FOOTPRINT-CAMPAIGN.json:1429:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1431:   "mutatedFileSha256": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec",
./ES-FOOTPRINT-CAMPAIGN.json:1436:     "mutated": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec"
./ES-FOOTPRINT-CAMPAIGN.json:1441:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1458:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1461:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1469:  "documentText": "`identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).",
./ES-FOOTPRINT-CAMPAIGN.json:1499:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1511:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1528:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1531:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1539:  "documentText": "Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.",
./ES-FOOTPRINT-CAMPAIGN.json:1562:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1572:   "outputSha256": "3d8ba7f566abd3d02554c4e794647b6fe554b9bc2a7148f6648eca6053b67be6",
./ES-FOOTPRINT-CAMPAIGN.json:1574:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1581:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1584:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1592:  "documentText": "Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.",
./ES-FOOTPRINT-CAMPAIGN.json:1615:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1625:   "outputSha256": "fe43b7ca0d87718bed77942a8b5d384647600f46ca06f950b650b63b83f8b477",
./ES-FOOTPRINT-CAMPAIGN.json:1627:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1634:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1637:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1645:  "documentText": "**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).",
./ES-FOOTPRINT-CAMPAIGN.json:1668:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1670:   "mutatedFileSha256": "5203835d0356ee0a9d2d478680c07baa48e3770b66cbe0c85d2a924d70865ae2",
./ES-FOOTPRINT-CAMPAIGN.json:1675:     "mutated": "5203835d0356ee0a9d2d478680c07baa48e3770b66cbe0c85d2a924d70865ae2"
./ES-FOOTPRINT-CAMPAIGN.json:1680:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1687:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1690:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1698:  "documentText": "`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.",
./ES-FOOTPRINT-CAMPAIGN.json:1721:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1733:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1740:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1743:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1751:  "documentText": "**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).",
./ES-FOOTPRINT-CAMPAIGN.json:1777:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1789:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1798:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1801:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1809:  "documentText": "A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.",
./ES-FOOTPRINT-CAMPAIGN.json:1832:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1844:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1851:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1854:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1862:  "documentText": "A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.",
./ES-FOOTPRINT-CAMPAIGN.json:1888:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1900:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1909:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1912:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
./ES-FOOTPRINT-CAMPAIGN.json:1920:  "documentText": "`gateway_footprint_records_total{event,consumer}` \u2014 every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).",
./ES-FOOTPRINT-CAMPAIGN.json:1946:   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1958:   "failureLines": [
./ES-FOOTPRINT-CAMPAIGN.json:1967:   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
./ES-FOOTPRINT-CAMPAIGN.json:1970:   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",

codex
The four Round 4 dispositions are present in the expected artifacts. I’m now checking the stronger question: whether the new refusal logic is actually sound and whether each displayed row’s semantic attribution matches what its mutation and failing assertions prove.
exec
/bin/bash -lc "sed -n '1,260p' scripts/footprint-conformance.py
sed -n '1,260p' scripts/footprint-reqstate.py
sed -n '1,360p' scripts/footprint-mutate.py
sed -n '150,260p' ES-FOOTPRINT-GATEWAY-DESIGN.md" in /private/tmp/oe-conf-gw
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
            groups = len({re.sub(r' (?:site\d+|variant\d+\S*)$', '', k) for k in keys[rid]})
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
    lines = [l.rstrip()[:400] for l in re.findall(pat, out, re.M)]
    # The cap must not cut the record loose from its own names: a truncated list left a record naming
    # four failing tests whose lines were not in it, which a reader cannot check and a generator that
    # correlates the two will (correctly) refuse.
    if kind != 'maven':
        return lines[:40]
    return lines[:40]

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
                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': (asserted or failed)[:4], 'assertionFailures': [n for n in asserted if any(n.split('.')[-1] in l for l in failure_lines(out, kind))][:8],
                  'anyFailures': failed[:8],
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
| G-R4 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
| G-R7 | 5 of 6 probes pinned, over 4 clauses (1 survived) | 2 | Backfill routes |
| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
| G-R9 | 3 of 3 probes pinned, over 3 clauses | 2 | Metrics |
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

exec
/bin/bash -lc "jq -r 'to_entries[] | [.key,.value.requirement,.value.status,.value.documentText,.value.clause,.value.file,(.value.line|tostring),(.value.patch // \"\"),((.value.assertionFailures // [])|join(\",\")),((.value.evidence.failureLines // [])|join(\" || \"))] | @tsv' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 exited 5 in 0ms:
jq: error (at ES-FOOTPRINT-CAMPAIGN.json:1973): object ({"old":"   ...) is not valid in a csv row

exec
/bin/bash -lc "jq -r 'to_entries[] | [.key,.value.requirement,.value.status,.value.documentText,.value.clause,.value.file,(.value.line|tostring),(.value.mutation.old // .value.patch.old // \"\"),(.value.mutation.new // .value.patch.new // \"\"),((.value.assertionFailures // [])|join(\",\")),((.value.evidence.failureLines // [])|join(\" || \"))] | @tsv' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
G-R4 older-date-dropped	G-R4	KILLED	**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.	an OLDER session date is dropped	src/main/java/app/feedgateway/FootprintViews.java	146	        if (cmp < 0) return null;	        if (cmp < 0) return false;	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts,FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders,FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.160 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>
G-R4 newer-date-rolls-both	G-R4	KILLED	Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;	a NEWER date rolls BOTH views	src/main/java/app/feedgateway/FootprintViews.java	148	        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;	        bars.clear(); barsBytes = 0;	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts	[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>
G-R4 equal-date-upserts	G-R4	KILLED	**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.	an EQUAL date upserts without rolling	src/main/java/app/feedgateway/FootprintViews.java	147	        if (cmp == 0) return false;	        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit,FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic,FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin,FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks,FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor,FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {"sessionDate":"2026-08-14","bars":[{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"30s","sessionDate":"2026-08-14","observations":{"barStartMs":5000},"tag":"x"}],"nextCursor":null} ==> expected: <true> but was: <false> || [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0> || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{},"outcomeHwm":{"5m":70}}> || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:217 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:189 expected: <4> but was: <1>
G-R9.3 a-rollover-is-counted	G-R9	KILLED	`gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).	rollover clears are counted in gateway_footprint_rollovers_total, one per date advance	src/main/java/app/feedgateway/FootprintViews.java	150	        rollovers.incrementAndGet();	        if (false) rollovers.incrementAndGet();	FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts	[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>
G-R8 evict-oldest	G-R8	KILLED	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	evict the OLDEST key of the timeframe	src/main/java/app/feedgateway/FootprintViews.java	167	            String oldest = view.ceilingKey(tf + "|");	            String oldest = view.floorKey(tf + "|~");	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>
G-R8 evict-rotates	G-R8	KILLED	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	eviction ROTATES across timeframes	src/main/java/app/feedgateway/FootprintViews.java	166	            if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;	            if (isBars) barsEvictCursor = cursor; else outcomesEvictCursor = cursor;	FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin	[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:245 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>
G-R8 byte-budget-decremented	G-R8	KILLED	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	evicting decrements the byte total	src/main/java/app/feedgateway/FootprintViews.java	171	            if (isBars) barsBytes -= len; else outcomesBytes -= len;	            if (isBars) barsBytes -= 0; else outcomesBytes -= 0;	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <3>
G-R8 both-budgets-enforced	G-R8	KILLED	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	both the count AND byte budgets are enforced	src/main/java/app/feedgateway/FootprintViews.java	161	        while (view.size() > maxCount || (isBars ? barsBytes : outcomesBytes) > maxBytes) {	        while (view.size() > maxCount) {	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <0>
G-R8 evictions-counted	G-R8	KILLED	View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.	evictions are counted per view	src/main/java/app/feedgateway/FootprintViews.java	174	        if (evicted > 0) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);	        if (false) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);	FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact,FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin	[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:260 expected: <1> but was: <0> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>
G-R8a ceiling-strict	G-R8a	KILLED	**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.	the ceiling is inclusive; only ABOVE is refused	src/main/java/app/feedgateway/FootprintTopicGate.java	118	            else if (cfg.maxMessageBytes() > ceiling) o = new Outcome(false, Reason.ceiling,	            else if (cfg.maxMessageBytes() >= ceiling) o = new Outcome(false, Reason.ceiling,	FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts,FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows,FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne,FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions	[ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.005 s <<< ERROR! || [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.002 s <<< ERROR! || [ERROR] app.feedgateway.FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts:68 the validated topic is added; the unvalidated one is WITHHELD from added() ==> expected: <[futures.footprint.bars-0]> but was: <[]> || [ERROR]   FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows:106 expected: <[futures.cvd.bars-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd.bars-0]> || [ERROR]   FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne:99 expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]> || [ERROR]   FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears:69 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.outcomes: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588)] || [ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:55 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 > 1048588)] || [ERROR]   FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused:38 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.outcomes: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588  || [ERROR]   FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp:225 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 > 1048588)]
G-R8a codec-allowlist	G-R8a	KILLED	**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.	only producer/uncompressed are accepted	src/main/java/app/feedgateway/FootprintTopicGate.java	119	            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression,	            else if (false) o = new Outcome(false, Reason.compression,	FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted	[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.015 s <<< FAILURE! || [ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:51 gzip ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.
G-R9 ceiling-precedence	G-R9	KILLED	`gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).	ceiling takes precedence over compression	src/main/java/app/feedgateway/FootprintTopicGate.java	117	            if (cfg == null) o = new Outcome(false, Reason.admin, "no config returned");	            if (cfg == null) o = new Outcome(false, Reason.admin, "no config returned");\n            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression, "compression.type=" + cfg.compressionType());	FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt	[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR]   FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt:62 expected: <1> but was: <0>
G-R7 to-is-inclusive	G-R7	KILLED	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	toMs is INCLUSIVE	src/main/java/app/feedgateway/FootprintViews.java	229	            String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));	            String hi = barKey(tf, Math.min(toMsInclusive - 1, EPOCH_MAX_MS));	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit,FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim,FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic	[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2> || [ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false> || [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:200 expected: <3> but was: <2>
G-R7 limit-clamped-low	G-R7	KILLED	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	the forwarded limit is clamped to at least 1	src/main/java/app/feedgateway/GatewayController.java	130	    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }	    static int clamp(int limit) { return Math.min(limit, FOOTPRINT_LIMIT_MAX); }	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit	[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>
G-R7 limit-clamped-high	G-R7	KILLED	`limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.	the forwarded limit is clamped to the ceiling	src/main/java/app/feedgateway/GatewayController.java	130	    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }	    static int clamp(int limit) { return Math.max(1, limit); }	FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit	[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>
G-R7 the-epoch-guard variant1-guard	G-R7	KILLED	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	an out-of-domain timestamp is refused	src/main/java/app/feedgateway/FootprintViews.java	329	        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;	        return x < 0 ? -1 : x;	FootprintViewsTest.shapeDropsNeverMutate	[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {"timeframe":"1m","sessionDate":"2026-08-14","observations":{"barStartMs":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>
G-R7 the-epoch-guard variant2-removed	G-R7	KILLED	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	a cursor at or past the last epoch value can have nothing after it, and the increment is taken only inside the domain	src/main/java/app/feedgateway/FootprintViews.java	226	            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {	            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0) {	FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain	[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.019 s <<< FAILURE! || [ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:185 a cursor beyond the domain admits nothing — it must not wrap ==> expected: <0> but was: <4>
G-R7 the-epoch-guard variant3-relaxed	G-R7	SURVIVED	**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).	the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way	src/main/java/app/feedgateway/FootprintViews.java	226	            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {	            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive <= EPOCH_MAX_MS) {		
G-R1.1 the-flag-gates-every-footprint-object	G-R1	KILLED	Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.	Flag off: the views are never instantiated	src/main/java/app/feedgateway/FeedGatewayService.java	809	        if (settings.esFootprintEnabled()) {	        if (true) {	FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing,FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose,FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions,FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge,FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled,FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven	[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.348 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.308 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401> || [ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123 || [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]> || [ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:123 flag off adds nothing ==> expected: <true> but was: <false> || [ERROR]   FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns:244 » Servlet Request processing failed: java.lang.NullPointerException: Cannot invoke "app.feedgateway.liquidityhistory.LiquidityHistoryAuth$Result.status()" because "auth" is null
G-R2.1 one-wiring-method-binds-all-four	G-R2	KILLED	ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.	ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers	src/main/java/app/feedgateway/FeedGatewayService.java	2392	        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));	        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));	FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven	[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot
G-R2.2 the-evidence-binding	G-R2	KILLED	**Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).	KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)	src/main/java/app/feedgateway/FeedGatewayService.java	2390	        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding("DATABENTO", "es-footprint-evidence"));	        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding("DATABENTO", "es-footprint"));	FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics,FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven	[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint" ==> expected: <1> but was: <2> || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-
G-R3.1 an-oversize-record-is-dropped-not-broadcast	G-R3	KILLED	`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).	a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped	src/main/java/app/feedgateway/FeedGatewayService.java	905	        return a.reason() != FootprintViews.Reason.OVERSIZE;	        return true;	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.130 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.014 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>
G-R3.2 a-rejected-record-is-still-broadcast	G-R3	KILLED	`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).	a record the view REJECTS for shape or session is still broadcast	src/main/java/app/feedgateway/FeedGatewayService.java	905	        return a.reason() != FootprintViews.Reason.OVERSIZE;	        return a.reason() == FootprintViews.Reason.ADMITTED;	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.224 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.028 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>
G-R3.3 live-snapshots-are-never-retained	G-R3	KILLED	**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).	never cached, never retained	src/main/java/app/feedgateway/FeedGatewayService.java	6534	            return CachePolicy.expiring(0L);	            return CachePolicy.expiring(settings.esFootprintSeekBackMs());	FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession	[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>
G-R5.1 the-outcome-key-orders-numerically	G-R5	KILLED	The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;	the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)	src/main/java/app/feedgateway/FootprintViews.java	296	        return tf + "|" + String.format("%019d", resolvedAtBarStartMs) + "|" + identity;	        return tf + "|" + resolvedAtBarStartMs + "|" + identity;	FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession,FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth	[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.072 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.004 s <<< ERROR! || [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8> || [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> || [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "10|ES.v.0|1m|1|BUYE" || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "5|ES.v.0|1m|1|BUYER" || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "77|ES.v.0|5m|1|SELL" || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » StringIndexOutOfBounds Range [3, 22) out of bounds for length 8
G-R5.2 the-identity-is-the-last-component	G-R5	KILLED	`identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).	identity CONTAINS |, which is why it is the LAST component	src/main/java/app/feedgateway/FootprintViews.java	296	        return tf + "|" + String.format("%019d", resolvedAtBarStartMs) + "|" + identity;	        return tf + "|" + identity + "|" + String.format("%019d", resolvedAtBarStartMs);	FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession,FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth	[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.099 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR! || [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: "id3|000000000000000"> || [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005> || [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|" || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|" || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "z|00000000000000000" || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|"
G-R5.3 a-foreign-identity-is-refused	G-R5	KILLED	Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.	a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)	src/main/java/app/feedgateway/FootprintViews.java	125	                || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {	                || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {	FootprintViewsTest.shapeDropsNeverMutate	[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:40 expected: <SHAPE> but was: <ADMITTED>
G-R5.4 last-write-per-key-wins	G-R5	KILLED	Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.	Last write per full key wins; a redelivery carries the same key and overwrites in place	src/main/java/app/feedgateway/FootprintViews.java	132	            String previous = outcomes.put(key, json);	            String previous = outcomes.putIfAbsent(key, json);	FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace	[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>
G-R6.1 the-hello-carries-the-footprint-field	G-R6	KILLED	**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).	the existing cvd-hello frame gains ONE field footprint	src/main/java/app/feedgateway/FeedGatewayService.java	11180	            sb.append(",\\"footprint\\":").append(footprintViews.helloField());	            sb.append(",\\"footprintX\\":").append(footprintViews.helloField());	FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot	[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:152 expected: <{"sessionDate":null,"hwm":{},"footprint":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}> but was: <{"sessionDate":null,"hwm":{},"footprintX":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}>
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on	G-R6	KILLED	`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.	cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled	src/main/java/app/feedgateway/FeedGatewayService.java	11158	        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;	        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();	FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled	[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>
G-R6.3 the-hello-carries-the-outcome-high-water-mark	G-R6	KILLED	**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).	outcomeHwm:{tf: maxResolvedAtBarStartMs}	src/main/java/app/feedgateway/FootprintViews.java	195	            sb.append(",\\"outcomeHwm\\":"); appendHwm(sb, outcomes, true);	            sb.append(",\\"outcomeHwm\\":"); appendHwm(sb, bars, true);	FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks,FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot	[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"30s":2000,"1m":5}}> || [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop	G-R10	KILLED	A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.	a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw	src/main/java/app/feedgateway/FootprintViews.java	93	        if (json == null) return Admission.SHAPE;	        if (json == null) throw new IllegalArgumentException("json");	FootprintViewsTest.aNullPayloadIsARefusalNotAThrow	[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:85 a null bar payload must not throw ==> Unexpected exception thrown: java.lang.IllegalArgumentException: json
G-R10.2 every-admission-failure-is-a-counted-drop	G-R10	KILLED	A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.	every admission failure is a counted drop	src/main/java/app/feedgateway/FeedGatewayService.java	903	            footprintCounter("drops_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\",reason=\\"" + a.reason().name().toLowerCase() + "\\"}").incrementAndGet();	            if (a.reason() != FootprintViews.Reason.SHAPE) footprintCounter("drops_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\",reason=\\"" + a.reason().name().toLowerCase() + "\\"}").incrementAndGet();	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.133 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.074 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:193 four events + the stale bar, never the oversize one: [{"type":"es-footprint","data":{"schemaVersion":6,"live":true}}, {"type":"es-footprint-evidence","data":{"schemaVersion":6,"ev":true}}, {"type":"es-footprint-bar","data":{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"1m","sessionDate":" || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>
G-R9.2 every-record-is-counted-before-admission	G-R9	KILLED	`gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).	records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too	src/main/java/app/feedgateway/FeedGatewayService.java	897	        footprintCounter("records_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\"}").incrementAndGet();\n        if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = "es-footprint-bar".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {	        if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {\n            footprintCounter("records_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\"}").incrementAndGet();\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = "es-footprint-bar".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission\n        footprintCounter("records_total{event=\\"" + event + "\\",consumer=\\"" + consumer + "\\"}").incrementAndGet();\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {	FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone,FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules	[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.168 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.025 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:193 four events + the stale bar, never the oversize one: [{"type":"es-footprint","data":{"schemaVersion":6,"live":true}}, {"type":"es-footprint-evidence","data":{"schemaVersion":6,"ev":true}}, {"type":"es-footprint-bar","data":{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"1m","sessionDate":" || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>

exec
/bin/bash -lc "jq -r 'to_entries[] | \"KEY: \\(.key)\\nRID: \\(.value.requirement) STATUS: \\(.value.status)\\nQUOTE: \\(.value.documentText)\\nCLAUSE: \\(.value.clause)\\nOLD: \\(.value.patch.old // \"\")\\nNEW: \\(.value.patch.new // \"\")\\nFAILS: \\((.value.assertionFailures // [])|join(\", \"))\\n\"' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
KEY: G-R4 older-date-dropped
RID: G-R4 STATUS: KILLED
QUOTE: **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.
CLAUSE: an OLDER session date is dropped
OLD:         if (cmp < 0) return null;
NEW:         if (cmp < 0) return false;
FAILS: FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts, FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders, FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules

KEY: G-R4 newer-date-rolls-both
RID: G-R4 STATUS: KILLED
QUOTE: Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
CLAUSE: a NEWER date rolls BOTH views
OLD:         bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;
NEW:         bars.clear(); barsBytes = 0;
FAILS: FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts

KEY: G-R4 equal-date-upserts
RID: G-R4 STATUS: KILLED
QUOTE: **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.
CLAUSE: an EQUAL date upserts without rolling
OLD:         if (cmp == 0) return false;
NEW:         if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }
FAILS: FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit, FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic, FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact, FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin, FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks, FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor, FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain

KEY: G-R9.3 a-rollover-is-counted
RID: G-R9 STATUS: KILLED
QUOTE: `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).
CLAUSE: rollover clears are counted in gateway_footprint_rollovers_total, one per date advance
OLD:         rollovers.incrementAndGet();
NEW:         if (false) rollovers.incrementAndGet();
FAILS: FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts

KEY: G-R8 evict-oldest
RID: G-R8 STATUS: KILLED
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: evict the OLDEST key of the timeframe
OLD:             String oldest = view.ceilingKey(tf + "|");
NEW:             String oldest = view.floorKey(tf + "|~");
FAILS: FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact, FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin

KEY: G-R8 evict-rotates
RID: G-R8 STATUS: KILLED
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: eviction ROTATES across timeframes
OLD:             if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;
NEW:             if (isBars) barsEvictCursor = cursor; else outcomesEvictCursor = cursor;
FAILS: FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin

KEY: G-R8 byte-budget-decremented
RID: G-R8 STATUS: KILLED
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: evicting decrements the byte total
OLD:             if (isBars) barsBytes -= len; else outcomesBytes -= len;
NEW:             if (isBars) barsBytes -= 0; else outcomesBytes -= 0;
FAILS: FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact

KEY: G-R8 both-budgets-enforced
RID: G-R8 STATUS: KILLED
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: both the count AND byte budgets are enforced
OLD:         while (view.size() > maxCount || (isBars ? barsBytes : outcomesBytes) > maxBytes) {
NEW:         while (view.size() > maxCount) {
FAILS: FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact

KEY: G-R8 evictions-counted
RID: G-R8 STATUS: KILLED
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: evictions are counted per view
OLD:         if (evicted > 0) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);
NEW:         if (false) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);
FAILS: FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact, FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin

KEY: G-R8a ceiling-strict
RID: G-R8a STATUS: KILLED
QUOTE: **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
CLAUSE: the ceiling is inclusive; only ABOVE is refused
OLD:             else if (cfg.maxMessageBytes() > ceiling) o = new Outcome(false, Reason.ceiling,
NEW:             else if (cfg.maxMessageBytes() >= ceiling) o = new Outcome(false, Reason.ceiling,
FAILS: FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts, FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows, FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne, FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions

KEY: G-R8a codec-allowlist
RID: G-R8a STATUS: KILLED
QUOTE: **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
CLAUSE: only producer/uncompressed are accepted
OLD:             else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression,
NEW:             else if (false) o = new Outcome(false, Reason.compression,
FAILS: FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted

KEY: G-R9 ceiling-precedence
RID: G-R9 STATUS: KILLED
QUOTE: `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).
CLAUSE: ceiling takes precedence over compression
OLD:             if (cfg == null) o = new Outcome(false, Reason.admin, "no config returned");
NEW:             if (cfg == null) o = new Outcome(false, Reason.admin, "no config returned");
            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression, "compression.type=" + cfg.compressionType());
FAILS: FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt

KEY: G-R7 to-is-inclusive
RID: G-R7 STATUS: KILLED
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: toMs is INCLUSIVE
OLD:             String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));
NEW:             String hi = barKey(tf, Math.min(toMsInclusive - 1, EPOCH_MAX_MS));
FAILS: FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit, FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim, FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic

KEY: G-R7 limit-clamped-low
RID: G-R7 STATUS: KILLED
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: the forwarded limit is clamped to at least 1
OLD:     static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }
NEW:     static int clamp(int limit) { return Math.min(limit, FOOTPRINT_LIMIT_MAX); }
FAILS: FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit

KEY: G-R7 limit-clamped-high
RID: G-R7 STATUS: KILLED
QUOTE: `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.
CLAUSE: the forwarded limit is clamped to the ceiling
OLD:     static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }
NEW:     static int clamp(int limit) { return Math.max(1, limit); }
FAILS: FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit

KEY: G-R7 the-epoch-guard variant1-guard
RID: G-R7 STATUS: KILLED
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: an out-of-domain timestamp is refused
OLD:         return x < 0 || x > EPOCH_MAX_MS ? -1 : x;
NEW:         return x < 0 ? -1 : x;
FAILS: FootprintViewsTest.shapeDropsNeverMutate

KEY: G-R7 the-epoch-guard variant2-removed
RID: G-R7 STATUS: KILLED
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: a cursor at or past the last epoch value can have nothing after it, and the increment is taken only inside the domain
OLD:             if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {
NEW:             if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0) {
FAILS: FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain

KEY: G-R7 the-epoch-guard variant3-relaxed
RID: G-R7 STATUS: SURVIVED
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way
OLD:             if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {
NEW:             if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive <= EPOCH_MAX_MS) {
FAILS: 

KEY: G-R1.1 the-flag-gates-every-footprint-object
RID: G-R1 STATUS: KILLED
QUOTE: Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.
CLAUSE: Flag off: the views are never instantiated
OLD:         if (settings.esFootprintEnabled()) {
NEW:         if (true) {
FAILS: FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing, FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose, FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions, FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge, FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled, FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven

KEY: G-R2.1 one-wiring-method-binds-all-four
RID: G-R2 STATUS: KILLED
QUOTE: ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.
CLAUSE: ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers
OLD:         topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));
NEW:         if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));
FAILS: FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven

KEY: G-R2.2 the-evidence-binding
RID: G-R2 STATUS: KILLED
QUOTE: **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).
CLAUSE: KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)
OLD:         topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding("DATABENTO", "es-footprint-evidence"));
NEW:         topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding("DATABENTO", "es-footprint"));
FAILS: FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics, FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven

KEY: G-R3.1 an-oversize-record-is-dropped-not-broadcast
RID: G-R3 STATUS: KILLED
QUOTE: `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
CLAUSE: a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped
OLD:         return a.reason() != FootprintViews.Reason.OVERSIZE;
NEW:         return true;
FAILS: FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules

KEY: G-R3.2 a-rejected-record-is-still-broadcast
RID: G-R3 STATUS: KILLED
QUOTE: `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
CLAUSE: a record the view REJECTS for shape or session is still broadcast
OLD:         return a.reason() != FootprintViews.Reason.OVERSIZE;
NEW:         return a.reason() == FootprintViews.Reason.ADMITTED;
FAILS: FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules

KEY: G-R3.3 live-snapshots-are-never-retained
RID: G-R3 STATUS: KILLED
QUOTE: **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).
CLAUSE: never cached, never retained
OLD:             return CachePolicy.expiring(0L);
NEW:             return CachePolicy.expiring(settings.esFootprintSeekBackMs());
FAILS: FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession

KEY: G-R5.1 the-outcome-key-orders-numerically
RID: G-R5 STATUS: KILLED
QUOTE: The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;
CLAUSE: the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)
OLD:         return tf + "|" + String.format("%019d", resolvedAtBarStartMs) + "|" + identity;
NEW:         return tf + "|" + resolvedAtBarStartMs + "|" + identity;
FAILS: FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession, FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth

KEY: G-R5.2 the-identity-is-the-last-component
RID: G-R5 STATUS: KILLED
QUOTE: `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).
CLAUSE: identity CONTAINS |, which is why it is the LAST component
OLD:         return tf + "|" + String.format("%019d", resolvedAtBarStartMs) + "|" + identity;
NEW:         return tf + "|" + identity + "|" + String.format("%019d", resolvedAtBarStartMs);
FAILS: FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession, FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth

KEY: G-R5.3 a-foreign-identity-is-refused
RID: G-R5 STATUS: KILLED
QUOTE: Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
CLAUSE: a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)
OLD:                 || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {
NEW:                 || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {
FAILS: FootprintViewsTest.shapeDropsNeverMutate

KEY: G-R5.4 last-write-per-key-wins
RID: G-R5 STATUS: KILLED
QUOTE: Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
CLAUSE: Last write per full key wins; a redelivery carries the same key and overwrites in place
OLD:             String previous = outcomes.put(key, json);
NEW:             String previous = outcomes.putIfAbsent(key, json);
FAILS: FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace

KEY: G-R6.1 the-hello-carries-the-footprint-field
RID: G-R6 STATUS: KILLED
QUOTE: **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
CLAUSE: the existing cvd-hello frame gains ONE field footprint
OLD:             sb.append(",\"footprint\":").append(footprintViews.helloField());
NEW:             sb.append(",\"footprintX\":").append(footprintViews.helloField());
FAILS: FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot

KEY: G-R6.2 the-hello-is-sent-when-footprint-alone-is-on
RID: G-R6 STATUS: KILLED
QUOTE: `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.
CLAUSE: cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled
OLD:         return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;
NEW:         return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();
FAILS: FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled

KEY: G-R6.3 the-hello-carries-the-outcome-high-water-mark
RID: G-R6 STATUS: KILLED
QUOTE: **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
CLAUSE: outcomeHwm:{tf: maxResolvedAtBarStartMs}
OLD:             sb.append(",\"outcomeHwm\":"); appendHwm(sb, outcomes, true);
NEW:             sb.append(",\"outcomeHwm\":"); appendHwm(sb, bars, true);
FAILS: FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks, FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot

KEY: G-R10.1 a-footprint-record-never-escapes-the-consumer-loop
RID: G-R10 STATUS: KILLED
QUOTE: A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
CLAUSE: a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw
OLD:         if (json == null) return Admission.SHAPE;
NEW:         if (json == null) throw new IllegalArgumentException("json");
FAILS: FootprintViewsTest.aNullPayloadIsARefusalNotAThrow

KEY: G-R10.2 every-admission-failure-is-a-counted-drop
RID: G-R10 STATUS: KILLED
QUOTE: A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
CLAUSE: every admission failure is a counted drop
OLD:             footprintCounter("drops_total{event=\"" + event + "\",consumer=\"" + consumer + "\",reason=\"" + a.reason().name().toLowerCase() + "\"}").incrementAndGet();
NEW:             if (a.reason() != FootprintViews.Reason.SHAPE) footprintCounter("drops_total{event=\"" + event + "\",consumer=\"" + consumer + "\",reason=\"" + a.reason().name().toLowerCase() + "\"}").incrementAndGet();
FAILS: FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules

KEY: G-R9.2 every-record-is-counted-before-admission
RID: G-R9 STATUS: KILLED
QUOTE: `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).
CLAUSE: records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too
OLD:         footprintCounter("records_total{event=\"" + event + "\",consumer=\"" + consumer + "\"}").incrementAndGet();
        if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {
            return true;                                            // live snapshots: never admitted to a view
        }
        FootprintViews.Admission a = "es-footprint-bar".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);
        if (a.reason() != FootprintViews.Reason.ADMITTED) {
NEW:         if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {
            footprintCounter("records_total{event=\"" + event + "\",consumer=\"" + consumer + "\"}").incrementAndGet();
            return true;                                            // live snapshots: never admitted to a view
        }
        FootprintViews.Admission a = "es-footprint-bar".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);
        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission
        footprintCounter("records_total{event=\"" + event + "\",consumer=\"" + consumer + "\"}").incrementAndGet();
        if (a.reason() != FootprintViews.Reason.ADMITTED) {
FAILS: FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules


exec
/bin/bash -lc "sed -n '65,170p' ES-FOOTPRINT-GATEWAY-DESIGN.md
sed -n '230,330p' scripts/footprint-mutate.py
sed -n '1,180p' scripts/footprint-reqstate.sh
jq -r 'to_entries[] | select(.value.status==\"KILLED\") | select((.value.assertionFailures|length)==0) | [.key,.value.killedByThrow.test,.value.killedByThrow.throws,((.value.evidence.failureLines//[])|join(\" || \"))]|@tsv' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
| 1 | Fact (iii) stated as present upstream while `producerProps` did not set it | The upstream change was made as its own change set (options-edge-processing `feat/es-footprint-uncompressed`: `compression.type=none` explicit, test-pinned, design amendment 12), reviewed and merged as PR #757 (see the revision-9 log) |
| 2 | Late validation had no consumer handoff (`PartitionRefresh` topic set is immutable) | G-R8a rewritten around the AS-IS mechanism: all four topics stay in the consumers' topic sets so `PartitionRefresh` discovers them; the VALIDATION runs on the consumer's own poll thread as a gate between discovery and `assign` — no cross-thread handoff, no restart; start-up refusal covers only topics that exist |
| 3 | §1 auth anchor still wrong/empty | Anchor is `LiquidityHistoryController.java:86`; the CVD route is cited for typed binding and backfill semantics only |

### Revision-6 change log (Codex round 5 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Kafka term assumed fetched bytes bound deserialized data (compression); per-record client overhead omitted | G-R8: the four topics are REQUIRED uncompressed end to end — the upstream producer pins `compression.type=none` (processing-repo amendment, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` — so fetched bytes ARE the decompressed bytes; per-record key/`ConsumerRecord`/collection overhead added; the 100-records-per-poll figure is used ONLY for that overhead and is now stated so |
| 2 | Start-up ceiling check conflicts with optional absent topics | G-R8a: a topic is consumed only after its validation (`max.message.bytes` ceiling, `compression.type`); absent at start-up ⇒ not assigned, revalidated every 60 s, adopted on success via the consumers' existing topic-adoption path; existing-but-invalid ⇒ refuse start; Admin failures other than unknown-topic remain fatal |
| 3 | AS-IS anchor wrongly credited the CVD route with explicit auth | §1 anchor corrected: explicit auth is demonstrated by the `LiquidityHistoryAuth.authenticate` caller; the CVD route is the typed-binding/backfill precedent only |

### Revision-5 change log (Codex round 4 → disposition)

| # | Finding | Disposition |
|---|---------|-------------|
| 1 | Kafka transient bound unproven (`max.poll.records` does not bound fetching; `max.message.bytes` only lower-bounded upstream) | G-R8 derives the bound from the client's fetch mechanics (one in-flight fetch per node, no re-fetch of a partition with buffered data) and from an ENFORCED upper ceiling on each footprint topic's effective `max.message.bytes`, read at start-up via `describeConfigs` (refuse to start above it); total and inequalities recomputed |
| 2 | Route order cited a non-existent JWT filter; Spring binding precedes the handler | G-R7 order rewritten to the AS-IS convention: Spring typed binding → in-handler flag check → explicit `LiquidityHistoryAuth.authenticate` → permit → cursor → snapshot → write; overlap tests added |

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

<!-- BEGIN footprint-reqstate: generated by scripts/footprint-reqstate.sh — do not edit by hand -->

## 2a. Conformance — what a test actually holds

Every requirement above, and what this repository's mutation campaign established about it. The
campaign refuses to start against a dirty tree or a red baseline; a kill must be an ASSERTION
failure naming a test — or, where the clause under test IS "this must not throw", a propagated throw
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
                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': (asserted or failed)[:4], 'assertionFailures': [n for n in asserted if any(n.split('.')[-1] in l for l in failure_lines(out, kind))][:8],
                  'anyFailures': failed[:8],
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
#!/usr/bin/env bash
# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md IN PLACE from the checked-in campaign record.
#
# It rewrites the block between the BEGIN/END markers and leaves everything else byte-identical, so
# running it is the whole update: an earlier version of this script only printed to stdout while the
# document claimed it regenerated the section, which is a claim the script could not keep.
#
# --check exits non-zero if the committed section differs from what the record produces, so CI can
# hold the document to its own record.
set -euo pipefail
cd "$(dirname "$0")/.."

DOC=ES-FOOTPRINT-GATEWAY-DESIGN.md
BEGIN='<!-- BEGIN footprint-reqstate'
END='<!-- END footprint-reqstate -->'

table() {
  python3 scripts/footprint-reqstate.py \
    ES-FOOTPRINT-CAMPAIGN.json \
    "$DOC" \
    'G-R\d+[a-z]?' \
    '{"G-R.*":"2"}' \
    '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
    '{"G-R11":"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break"}'
}

# EXACTLY one marker pair, BEGIN before END, each at the start of its own line. A second pair, a
# reversed pair, or a marker mentioned inside prose would let the rewrite duplicate the generated
# block or swallow an unrelated section of the document.
nb=$(grep -cE "^${BEGIN}" "$DOC" || true)
ne=$(grep -cE "^${END}$"  "$DOC" || true)
[ "$nb" = "1" ] || { echo "FATAL: $DOC has $nb BEGIN markers at line start; exactly one is required" >&2; exit 1; }
[ "$ne" = "1" ] || { echo "FATAL: $DOC has $ne END markers at line start; exactly one is required" >&2; exit 1; }
lb=$(grep -nE "^${BEGIN}" "$DOC" | cut -d: -f1)
le=$(grep -nE "^${END}$"  "$DOC" | cut -d: -f1)
[ "$lb" -lt "$le" ] || { echo "FATAL: in $DOC the END marker (line $le) precedes BEGIN (line $lb)" >&2; exit 1; }

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
table > "$tmp/table.md"

# Rebuild the document: everything before the marker, then the section heading, then the prose from
# scripts/footprint-reqstate.preamble (hand-maintained — it explains the table and is reviewed like
# any other prose), then the generated table, then everything from the END marker on. Only the table
# is derived from the record; the preamble is not, and is kept in its own file so that is visible.
awk -v tablefile="$tmp/table.md" -v begin="$BEGIN" -v end="$END" '
  index($0, begin) == 1 { skipping = 1; print; print ""; print "## 2a. Conformance — what a test actually holds"; print "";
      while ((getline line < "scripts/footprint-reqstate.preamble") > 0) print line
      close("scripts/footprint-reqstate.preamble")
      print ""
      while ((getline line < tablefile) > 0) print line
      close(tablefile)
      print ""
      next }
  index($0, end) == 1 { skipping = 0 }
  !skipping { print }
' "$DOC" > "$tmp/doc.md"

if [ "${1:-}" = "--check" ]; then
  if ! diff -q "$DOC" "$tmp/doc.md" >/dev/null; then
    echo "§2a is stale: regenerate with scripts/footprint-reqstate.sh" >&2
    diff -u "$DOC" "$tmp/doc.md" | head -60 >&2
    exit 1
  fi
  echo "§2a matches the campaign record"
  exit 0
fi

mv "$tmp/doc.md" "$DOC"
echo "§2a regenerated in $DOC"

exec
/bin/bash -lc "nl -ba ES-FOOTPRINT-CAMPAIGN.json | sed -n '1,225p;700,1040p;1110,1235p'
nl -ba scripts/footprint-reqstate.py | sed -n '45,125p'
nl -ba ES-FOOTPRINT-GATEWAY-DESIGN.md | sed -n '125,205p'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
     1	{
     2	 "G-R4 older-date-dropped": {
     3	  "status": "KILLED",
     4	  "clause": "an OLDER session date is dropped",
     5	  "requirement": "G-R4",
     6	  "documentText": "**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.",
     7	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
     8	  "occurrence": 1,
     9	  "occurrencesInFile": 1,
    10	  "line": 146,
    11	  "enclosing": "private Boolean rollover(LocalDate date) {",
    12	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    13	  "killedBy": [
    14	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
    15	   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
    16	   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
    17	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
    18	  ],
    19	  "assertionFailures": [
    20	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
    21	   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
    22	   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
    23	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
    24	  ],
    25	  "anyFailures": [
    26	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
    27	   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
    28	   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
    29	   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
    30	  ],
    31	  "killedByThrow": null,
    32	  "seconds": 9.9,
    33	  "patch": {
    34	   "old": "        if (cmp < 0) return null;",
    35	   "new": "        if (cmp < 0) return false;"
    36	  },
    37	  "evidence": {
    38	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    39	   "returnCode": 1,
    40	   "mutatedFileSha256": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc",
    41	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    42	   "filesSha256": {
    43	    "src/main/java/app/feedgateway/FootprintViews.java": {
    44	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    45	     "mutated": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc"
    46	    }
    47	   },
    48	   "outputSha256": "f4ab8756f16dfed50301c918b25d3d79b6fbbcf6abcd9f37fb1deb649edc2547",
    49	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    50	   "failureLines": [
    51	    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.160 s <<< FAILURE!",
    52	    "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE!",
    53	    "[ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE!",
    54	    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE!",
    55	    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
    56	    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED>",
    57	    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED>",
    58	    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>"
    59	   ],
    60	   "treeRestoredClean": true
    61	  },
    62	  "baseline": {
    63	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    64	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    65	   "returnCode": 0,
    66	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    67	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
    68	  }
    69	 },
    70	 "G-R4 newer-date-rolls-both": {
    71	  "status": "KILLED",
    72	  "clause": "a NEWER date rolls BOTH views",
    73	  "requirement": "G-R4",
    74	  "documentText": "Rollover is MONOTONIC on the parsed date: NEWER than current \u21d2 BOTH views are cleared and the date advances;",
    75	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
    76	  "occurrence": 1,
    77	  "occurrencesInFile": 1,
    78	  "line": 148,
    79	  "enclosing": "private Boolean rollover(LocalDate date) {",
    80	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    81	  "killedBy": [
    82	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
    83	  ],
    84	  "assertionFailures": [
    85	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
    86	  ],
    87	  "anyFailures": [
    88	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
    89	  ],
    90	  "killedByThrow": null,
    91	  "seconds": 8.4,
    92	  "patch": {
    93	   "old": "        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;",
    94	   "new": "        bars.clear(); barsBytes = 0;"
    95	  },
    96	  "evidence": {
    97	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    98	   "returnCode": 1,
    99	   "mutatedFileSha256": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6",
   100	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   101	   "filesSha256": {
   102	    "src/main/java/app/feedgateway/FootprintViews.java": {
   103	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   104	     "mutated": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6"
   105	    }
   106	   },
   107	   "outputSha256": "e828bf491785b2f2126bd6f670f68a848e95719ff059004acee12a6a0db75304",
   108	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   109	   "failureLines": [
   110	    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.003 s <<< FAILURE!",
   111	    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>"
   112	   ],
   113	   "treeRestoredClean": true
   114	  },
   115	  "baseline": {
   116	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   117	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   118	   "returnCode": 0,
   119	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   120	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   121	  }
   122	 },
   123	 "G-R4 equal-date-upserts": {
   124	  "status": "KILLED",
   125	  "clause": "an EQUAL date upserts without rolling",
   126	  "requirement": "G-R4",
   127	  "documentText": "**Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock.",
   128	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
   129	  "occurrence": 1,
   130	  "occurrencesInFile": 1,
   131	  "line": 147,
   132	  "enclosing": "private Boolean rollover(LocalDate date) {",
   133	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   134	  "killedBy": [
   135	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
   136	   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
   137	   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
   138	   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   139	  ],
   140	  "assertionFailures": [
   141	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
   142	   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
   143	   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
   144	   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin",
   145	   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
   146	   "FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor",
   147	   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   148	  ],
   149	  "anyFailures": [
   150	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
   151	   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
   152	   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
   153	   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin",
   154	   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
   155	   "FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor",
   156	   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   157	  ],
   158	  "killedByThrow": null,
   159	  "seconds": 9.5,
   160	  "patch": {
   161	   "old": "        if (cmp == 0) return false;",
   162	   "new": "        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }"
   163	  },
   164	  "evidence": {
   165	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   166	   "returnCode": 1,
   167	   "mutatedFileSha256": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca",
   168	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   169	   "filesSha256": {
   170	    "src/main/java/app/feedgateway/FootprintViews.java": {
   171	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   172	     "mutated": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca"
   173	    }
   174	   },
   175	   "outputSha256": "f26d8491495969b2b8b532267b45f3799611bce6b6046220a2a02040e60890e9",
   176	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   177	   "failureLines": [
   178	    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!",
   179	    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.002 s <<< FAILURE!",
   180	    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE!",
   181	    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
   182	    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE!",
   183	    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< FAILURE!",
   184	    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!",
   185	    "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {\"sessionDate\":\"2026-08-14\",\"bars\":[{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"30s\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":5000},\"tag\":\"x\"}],\"nextCursor\":null} ==> expected: <true> but was: <false>",
   186	    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0>",
   187	    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0>",
   188	    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>",
   189	    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{},\"outcomeHwm\":{\"5m\":70}}>",
   190	    "[ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:217 expected: <2> but was: <0>",
   191	    "[ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:189 expected: <4> but was: <1>"
   192	   ],
   193	   "treeRestoredClean": true
   194	  },
   195	  "baseline": {
   196	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   197	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   198	   "returnCode": 0,
   199	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   200	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   201	  }
   202	 },
   203	 "G-R9.3 a-rollover-is-counted": {
   204	  "status": "KILLED",
   205	  "clause": "rollover clears are counted in gateway_footprint_rollovers_total, one per date advance",
   206	  "requirement": "G-R9",
   207	  "documentText": "`gateway_footprint_evictions_total{view}` \u2014 records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).",
   208	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
   209	  "occurrence": 1,
   210	  "occurrencesInFile": 1,
   211	  "line": 150,
   212	  "enclosing": "private Boolean rollover(LocalDate date) {",
   213	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   214	  "killedBy": [
   215	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   216	  ],
   217	  "assertionFailures": [
   218	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   219	  ],
   220	  "anyFailures": [
   221	   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   222	  ],
   223	  "killedByThrow": null,
   224	  "seconds": 9.3,
   225	  "patch": {
   700	   },
   701	   "outputSha256": "cfa236af7de0c598c5b9c24e7617c74b9bc73b1cebce09b76af4412486e7e872",
   702	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   703	   "failureLines": [
   704	    "[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.006 s <<< FAILURE!",
   705	    "[ERROR]   FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt:62 expected: <1> but was: <0>"
   706	   ],
   707	   "treeRestoredClean": true
   708	  },
   709	  "baseline": {
   710	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   711	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   712	   "returnCode": 0,
   713	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   714	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   715	  }
   716	 },
   717	 "G-R7 to-is-inclusive": {
   718	  "status": "KILLED",
   719	  "clause": "toMs is INCLUSIVE",
   720	  "requirement": "G-R7",
   721	  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   722	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
   723	  "occurrence": 1,
   724	  "occurrencesInFile": 1,
   725	  "line": 229,
   726	  "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
   727	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   728	  "killedBy": [
   729	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
   730	   "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
   731	   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
   732	  ],
   733	  "assertionFailures": [
   734	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
   735	   "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
   736	   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
   737	  ],
   738	  "anyFailures": [
   739	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
   740	   "FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim",
   741	   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
   742	  ],
   743	  "killedByThrow": null,
   744	  "seconds": 8.0,
   745	  "patch": {
   746	   "old": "            String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));",
   747	   "new": "            String hi = barKey(tf, Math.min(toMsInclusive - 1, EPOCH_MAX_MS));"
   748	  },
   749	  "evidence": {
   750	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   751	   "returnCode": 1,
   752	   "mutatedFileSha256": "f448803e303bdbdfaddcad7f8d1851a2cc77d9f21decc7d80ed25eea755d895a",
   753	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   754	   "filesSha256": {
   755	    "src/main/java/app/feedgateway/FootprintViews.java": {
   756	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   757	     "mutated": "f448803e303bdbdfaddcad7f8d1851a2cc77d9f21decc7d80ed25eea755d895a"
   758	    }
   759	   },
   760	   "outputSha256": "abd3e07a4b6394daa9bdaa44b2da9e9d63e7d067f93083c60503ae6e987d3330",
   761	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   762	   "failureLines": [
   763	    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.006 s <<< FAILURE!",
   764	    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.002 s <<< FAILURE!",
   765	    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0 s <<< FAILURE!",
   766	    "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2>",
   767	    "[ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false>",
   768	    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:200 expected: <3> but was: <2>"
   769	   ],
   770	   "treeRestoredClean": true
   771	  },
   772	  "baseline": {
   773	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   774	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   775	   "returnCode": 0,
   776	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   777	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   778	  }
   779	 },
   780	 "G-R7 limit-clamped-low": {
   781	  "status": "KILLED",
   782	  "clause": "the forwarded limit is clamped to at least 1",
   783	  "requirement": "G-R7",
   784	  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   785	  "file": "src/main/java/app/feedgateway/GatewayController.java",
   786	  "occurrence": 1,
   787	  "occurrencesInFile": 1,
   788	  "line": 130,
   789	  "enclosing": "static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
   790	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   791	  "killedBy": [
   792	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   793	  ],
   794	  "assertionFailures": [
   795	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   796	  ],
   797	  "anyFailures": [
   798	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   799	  ],
   800	  "killedByThrow": null,
   801	  "seconds": 7.7,
   802	  "patch": {
   803	   "old": "    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
   804	   "new": "    static int clamp(int limit) { return Math.min(limit, FOOTPRINT_LIMIT_MAX); }"
   805	  },
   806	  "evidence": {
   807	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   808	   "returnCode": 1,
   809	   "mutatedFileSha256": "0b7f3a2971dc69c3850ba867c539c6f2b6b17a8013993bc0a9d661ffb40162dd",
   810	   "originalFileSha256": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
   811	   "filesSha256": {
   812	    "src/main/java/app/feedgateway/GatewayController.java": {
   813	     "original": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
   814	     "mutated": "0b7f3a2971dc69c3850ba867c539c6f2b6b17a8013993bc0a9d661ffb40162dd"
   815	    }
   816	   },
   817	   "outputSha256": "4fc86784c016c4b742506831aec4dc4cb74a2efb2f16792a33d3c2584aa54d3d",
   818	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   819	   "failureLines": [
   820	    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE!",
   821	    "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>"
   822	   ],
   823	   "treeRestoredClean": true
   824	  },
   825	  "baseline": {
   826	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   827	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   828	   "returnCode": 0,
   829	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   830	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   831	  }
   832	 },
   833	 "G-R7 limit-clamped-high": {
   834	  "status": "KILLED",
   835	  "clause": "the forwarded limit is clamped to the ceiling",
   836	  "requirement": "G-R7",
   837	  "documentText": "`limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is \u2264 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.",
   838	  "file": "src/main/java/app/feedgateway/GatewayController.java",
   839	  "occurrence": 1,
   840	  "occurrencesInFile": 1,
   841	  "line": 130,
   842	  "enclosing": "static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
   843	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   844	  "killedBy": [
   845	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   846	  ],
   847	  "assertionFailures": [
   848	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   849	  ],
   850	  "anyFailures": [
   851	   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   852	  ],
   853	  "killedByThrow": null,
   854	  "seconds": 7.7,
   855	  "patch": {
   856	   "old": "    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
   857	   "new": "    static int clamp(int limit) { return Math.max(1, limit); }"
   858	  },
   859	  "evidence": {
   860	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   861	   "returnCode": 1,
   862	   "mutatedFileSha256": "55236d83d718eff57ec004a64e589fa1637998835869484f7548cbc1aeeca9b9",
   863	   "originalFileSha256": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
   864	   "filesSha256": {
   865	    "src/main/java/app/feedgateway/GatewayController.java": {
   866	     "original": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
   867	     "mutated": "55236d83d718eff57ec004a64e589fa1637998835869484f7548cbc1aeeca9b9"
   868	    }
   869	   },
   870	   "outputSha256": "32f283a64f9113e4641aa9bda6f70edfa2a23510ad96755a11e7f41c99139a33",
   871	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   872	   "failureLines": [
   873	    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.004 s <<< FAILURE!",
   874	    "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>"
   875	   ],
   876	   "treeRestoredClean": true
   877	  },
   878	  "baseline": {
   879	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   880	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   881	   "returnCode": 0,
   882	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   883	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   884	  }
   885	 },
   886	 "G-R7 the-epoch-guard variant1-guard": {
   887	  "status": "KILLED",
   888	  "clause": "an out-of-domain timestamp is refused",
   889	  "requirement": "G-R7",
   890	  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   891	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
   892	  "occurrence": 1,
   893	  "occurrencesInFile": 1,
   894	  "line": 329,
   895	  "enclosing": "private static long epochField(JsonNode node, String field) {",
   896	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   897	  "killedBy": [
   898	   "FootprintViewsTest.shapeDropsNeverMutate"
   899	  ],
   900	  "assertionFailures": [
   901	   "FootprintViewsTest.shapeDropsNeverMutate"
   902	  ],
   903	  "anyFailures": [
   904	   "FootprintViewsTest.shapeDropsNeverMutate"
   905	  ],
   906	  "killedByThrow": null,
   907	  "seconds": 9.3,
   908	  "patch": {
   909	   "old": "        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;",
   910	   "new": "        return x < 0 ? -1 : x;"
   911	  },
   912	  "evidence": {
   913	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   914	   "returnCode": 1,
   915	   "mutatedFileSha256": "e553a6d4e5b1827ccdc4191e78f47425f75f040f094bff517831a91eaf15a7f8",
   916	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   917	   "filesSha256": {
   918	    "src/main/java/app/feedgateway/FootprintViews.java": {
   919	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   920	     "mutated": "e553a6d4e5b1827ccdc4191e78f47425f75f040f094bff517831a91eaf15a7f8"
   921	    }
   922	   },
   923	   "outputSha256": "84109a218c15a75cf89a92f5f33501713b921b88f538a075c6afef57a5d36936",
   924	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   925	   "failureLines": [
   926	    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.003 s <<< FAILURE!",
   927	    "[ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>"
   928	   ],
   929	   "treeRestoredClean": true
   930	  },
   931	  "baseline": {
   932	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   933	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   934	   "returnCode": 0,
   935	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   936	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   937	  }
   938	 },
   939	 "G-R7 the-epoch-guard variant2-removed": {
   940	  "status": "KILLED",
   941	  "clause": "a cursor at or past the last epoch value can have nothing after it, and the increment is taken only inside the domain",
   942	  "requirement": "G-R7",
   943	  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   944	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
   945	  "occurrence": 1,
   946	  "occurrencesInFile": 1,
   947	  "line": 226,
   948	  "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
   949	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   950	  "killedBy": [
   951	   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   952	  ],
   953	  "assertionFailures": [
   954	   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   955	  ],
   956	  "anyFailures": [
   957	   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   958	  ],
   959	  "killedByThrow": null,
   960	  "seconds": 8.2,
   961	  "patch": {
   962	   "old": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {",
   963	   "new": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0) {"
   964	  },
   965	  "evidence": {
   966	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   967	   "returnCode": 1,
   968	   "mutatedFileSha256": "bcdfcc3334a5212551a86cd937777facd6b5915b9f634abbb7a9a99469c0add6",
   969	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   970	   "filesSha256": {
   971	    "src/main/java/app/feedgateway/FootprintViews.java": {
   972	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   973	     "mutated": "bcdfcc3334a5212551a86cd937777facd6b5915b9f634abbb7a9a99469c0add6"
   974	    }
   975	   },
   976	   "outputSha256": "fb1d4803500c116b623b7e9c6d2baf6d811f760c434751b8935812d4a9ed126d",
   977	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   978	   "failureLines": [
   979	    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.019 s <<< FAILURE!",
   980	    "[ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:185 a cursor beyond the domain admits nothing \u2014 it must not wrap ==> expected: <0> but was: <4>"
   981	   ],
   982	   "treeRestoredClean": true
   983	  },
   984	  "baseline": {
   985	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   986	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   987	   "returnCode": 0,
   988	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   989	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   990	  }
   991	 },
   992	 "G-R7 the-epoch-guard variant3-relaxed": {
   993	  "status": "SURVIVED",
   994	  "clause": "the same guard relaxed from < to <= \u2014 recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way",
   995	  "requirement": "G-R7",
   996	  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   997	  "file": "src/main/java/app/feedgateway/FootprintViews.java",
   998	  "occurrence": 1,
   999	  "occurrencesInFile": 1,
  1000	  "line": 226,
  1001	  "enclosing": "BarsPage barsPage(String tf, long toMsInclusive, long afterMsExclusive, int limit, String expectedSessionDate) {",
  1002	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1003	  "killedBy": [],
  1004	  "assertionFailures": [],
  1005	  "anyFailures": [],
  1006	  "killedByThrow": null,
  1007	  "seconds": 10.8,
  1008	  "patch": {
  1009	   "old": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {",
  1010	   "new": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive <= EPOCH_MAX_MS) {"
  1011	  },
  1012	  "evidence": {
  1013	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
  1014	   "returnCode": 0,
  1015	   "mutatedFileSha256": "1805041d85205e5bad948989b9f83b1f4d321c9f9322dd96226cf8761ffb8058",
  1016	   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
  1017	   "filesSha256": {
  1018	    "src/main/java/app/feedgateway/FootprintViews.java": {
  1019	     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
  1020	     "mutated": "1805041d85205e5bad948989b9f83b1f4d321c9f9322dd96226cf8761ffb8058"
  1021	    }
  1022	   },
  1023	   "outputSha256": "8bc34cd70bbee8d0320e948ae2325884ce13359266e15fd9cb2fc6f7bd6b78f3",
  1024	   "outputTail": "tures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n",
  1025	   "failureLines": [],
  1026	   "treeRestoredClean": true
  1027	  },
  1028	  "baseline": {
  1029	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
  1030	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1031	   "returnCode": 0,
  1032	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
  1033	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1034	  }
  1035	 },
  1036	 "G-R1.1 the-flag-gates-every-footprint-object": {
  1037	  "status": "KILLED",
  1038	  "clause": "Flag off: the views are never instantiated",
  1039	  "requirement": "G-R1",
  1040	  "documentText": "Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{\"enabled\":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.",
  1110	   "returnCode": 0,
  1111	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
  1112	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1113	  }
  1114	 },
  1115	 "G-R2.1 one-wiring-method-binds-all-four": {
  1116	  "status": "KILLED",
  1117	  "clause": "ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers",
  1118	  "requirement": "G-R2",
  1119	  "documentText": "ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.",
  1120	  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
  1121	  "occurrence": 1,
  1122	  "occurrencesInFile": 1,
  1123	  "line": 2392,
  1124	  "enclosing": "void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) { // package-private: the wiring test EXECUTES it",
  1125	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1126	  "killedBy": [
  1127	   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
  1128	  ],
  1129	  "assertionFailures": [
  1130	   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
  1131	  ],
  1132	  "anyFailures": [
  1133	   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
  1134	  ],
  1135	  "killedByThrow": null,
  1136	  "seconds": 8.9,
  1137	  "patch": {
  1138	   "old": "        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));",
  1139	   "new": "        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));"
  1140	  },
  1141	  "evidence": {
  1142	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
  1143	   "returnCode": 1,
  1144	   "mutatedFileSha256": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817",
  1145	   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1146	   "filesSha256": {
  1147	    "src/main/java/app/feedgateway/FeedGatewayService.java": {
  1148	     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1149	     "mutated": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817"
  1150	    }
  1151	   },
  1152	   "outputSha256": "ff251c162bb7ca0e68e9faeaeffc420484a873d49c7c190c85713bee070d3b0f",
  1153	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
  1154	   "failureLines": [
  1155	    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE!",
  1156	    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot"
  1157	   ],
  1158	   "treeRestoredClean": true
  1159	  },
  1160	  "baseline": {
  1161	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
  1162	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1163	   "returnCode": 0,
  1164	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
  1165	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1166	  }
  1167	 },
  1168	 "G-R2.2 the-evidence-binding": {
  1169	  "status": "KILLED",
  1170	  "clause": "KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)",
  1171	  "requirement": "G-R2",
  1172	  "documentText": "**Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` \u2192 `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` \u2192 `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` \u2192 `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` \u2192 `futures.footprint.outcomes` (`es-footprint-outcome`).",
  1173	  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
  1174	  "occurrence": 1,
  1175	  "occurrencesInFile": 1,
  1176	  "line": 2390,
  1177	  "enclosing": "void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) { // package-private: the wiring test EXECUTES it",
  1178	  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1179	  "killedBy": [
  1180	   "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
  1181	   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
  1182	  ],
  1183	  "assertionFailures": [
  1184	   "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
  1185	   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
  1186	  ],
  1187	  "anyFailures": [
  1188	   "FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics",
  1189	   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
  1190	  ],
  1191	  "killedByThrow": null,
  1192	  "seconds": 8.1,
  1193	  "patch": {
  1194	   "old": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-evidence\"));",
  1195	   "new": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint\"));"
  1196	  },
  1197	  "evidence": {
  1198	   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
  1199	   "returnCode": 1,
  1200	   "mutatedFileSha256": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8",
  1201	   "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1202	   "filesSha256": {
  1203	    "src/main/java/app/feedgateway/FeedGatewayService.java": {
  1204	     "original": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
  1205	     "mutated": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8"
  1206	    }
  1207	   },
  1208	   "outputSha256": "d7946de3d02cc945ffe3969e72126a4097d15ad8e588067571bd872294d05d39",
  1209	   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
  1210	   "failureLines": [
  1211	    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.008 s <<< FAILURE!",
  1212	    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.004 s <<< FAILURE!",
  1213	    "[ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint\" ==> expected: <1> but was: <2>",
  1214	    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-"
  1215	   ],
  1216	   "treeRestoredClean": true
  1217	  },
  1218	  "baseline": {
  1219	   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
  1220	   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
  1221	   "returnCode": 0,
  1222	   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
  1223	   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  1224	  }
  1225	 },
  1226	 "G-R3.1 an-oversize-record-is-dropped-not-broadcast": {
  1227	  "status": "KILLED",
  1228	  "clause": "a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped",
  1229	  "requirement": "G-R3",
  1230	  "documentText": "`es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) \u2014 a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).",
  1231	  "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
  1232	  "occurrence": 1,
  1233	  "occurrencesInFile": 1,
  1234	  "line": 905,
  1235	  "enclosing": "boolean admitFootprintRecord(String event, String json, String consumer) { // package-private: the wiring tests drive both consumer paths th",
    45	            t = mm.group(2).rstrip()
    46	            if t.endswith('|'): t = t[:-1].rstrip()
    47	            if len(t) > len(req_text.get(mm.group(1), '')): req_text[mm.group(1)] = t
    48	
    49	    problems = []
    50	    for rid, ms in per.items():
    51	        for m in ms:
    52	            # ATTRIBUTION, for every record whatever its status: the obligation a row quotes must be
    53	            # verbatim in the requirement the row sits under. `requirement` is an editable field in a
    54	            # checked-in file, and filing a probe under the wrong requirement is how a table comes to
    55	            # over-count one requirement and under-count another while every individual cell looks fine.
    56	            dt = (m.get('documentText') or '').strip()
    57	            if not dt:
    58	                problems.append(f"{rid}: a record quotes no obligation")
    59	            elif rid not in req_text:
    60	                problems.append(f"{rid}: is not a requirement in the document")
    61	            elif dt not in req_text[rid]:
    62	                problems.append(f"{rid}: the quoted obligation is not verbatim in {rid}: {dt[:70]!r}")
    63	            if m['status'] != 'KILLED':
    64	                continue
    65	            # the named assertion failures must be the ones THIS run produced
    66	            fl = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
    67	            for name in (m.get('assertionFailures') or []):
    68	                if name.split('.')[-1] not in fl:
    69	                    problems.append(f"{rid}: names assertion failure {name} that its own failure lines do not carry")
    70	            if not m.get('assertionFailures'):
    71	                # The renderer must hold this itself: a record is a checked-in FILE, and a hand-edited
    72	                # or legacy one would otherwise be taken at its word.
    73	                t = m.get('killedByThrow')
    74	                lines = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
    75	                ok = (isinstance(t, dict) and t.get('test') and t.get('throws')
    76	                      and t['test'].split('.')[-1] in lines and t['throws'] in lines
    77	                      and all(part in lines for part in [t['test'].split('.')[-1]]))
    78	                if not ok:
    79	                    problems.append(f"{rid}: a KILLED record names no ASSERTION failure, and its "
    80	                                    f"killedByThrow does not name a test and a throw that its own "
    81	                                    f"failure lines carry ({m.get('killedBy')})")
    82	            if not m.get('evidence', {}).get('failureLines'):
    83	                problems.append(f"{rid}: a KILLED record carries no failure lines")
    84	            if not m.get('evidence', {}).get('treeRestoredClean', True):
    85	                problems.append(f"{rid}: a KILLED record ran against a tree it did not restore")
    86	    if problems:
    87	        print("REFUSING TO EMIT:", file=sys.stderr)
    88	        for p in problems: print("  ", p, file=sys.stderr)
    89	        sys.exit(1)
    90	
    91	    def gate(rid):
    92	        for pat, g in gates.items():
    93	            if re.fullmatch('(?:' + pat + ')', rid): return g
    94	        return '—'
    95	    def disp(rid):
    96	        for pat, d in dispositions.items():
    97	            if re.fullmatch('(?:' + pat + ')', rid): return d
    98	        return ''
    99	    out = ["| id | Conformance | Gate | Disposition |", "|----|-------------|------|-------------|"]
   100	    for rid in sorted(ids, key=lambda x: (re.sub(r'\d', '', x), int(re.search(r'\d+', x).group()), x)):
   101	        ms = per.get(rid, [])
   102	        if not ms:
   103	            # A note is EDITORIAL: it says why this requirement has no mutations, and is marked as
   104	            # such so it can never be read as a campaign result.
   105	            state = (notes[rid] + " (not probed)") if rid in notes else "NOT PROBED"
   106	        else:
   107	            by = collections.Counter(m['status'] for m in ms)
   108	            killed = by.pop('KILLED', 0)
   109	            # Count PROBES, not clauses: a clause implemented at several sites is probed once per
   110	            # site AND once for all sites together, so "n of m clauses" would triple-count it.
   111	            # A clause probed at several SITES, or broken several WAYS (variants), is still one
   112	            # clause; counting records as clauses triple-counts it.
   113	            groups = len({re.sub(r' (?:site\d+|variant\d+\S*)$', '', k) for k in keys[rid]})
   114	            state = f"{killed} of {len(ms)} probes pinned, over {groups} clause" + ("" if groups == 1 else "s")
   115	            rest = ", ".join(f"{n} {st.lower()}" for st, n in sorted(by.items()))
   116	            if rest: state += f" ({rest})"
   117	        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
   118	    # Count only what the table shows. A record for a requirement the document no longer
   119	    # renders is omitted from the rows, and must be omitted from the totals with it.
   120	    rendered = [v for rid in ids for v in per.get(rid, [])]
   121	    tot = collections.Counter(v['status'] for v in rendered)
   122	    out.append("")
   123	    probed_here = len([r for r in ids if r in per])
   124	    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rendered)} mutations "
   125	               f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving"
   125	| `es-cvd` is broadcast STANDALONE (never selection-gated, never cached); `es-cvd-bar` is upserted into the keyed view FIRST, then broadcast | `FeedGatewayService.java:2833-2848` (`runLiveConsumerOnce`) |
   126	| Keyed view `cvdBars: TreeMap<tf\|barStartMs, json>` with MONOTONIC session rollover on the record's `sessionDate` (string order = date order for `yyyymmdd`); older-session records dropped; foreign shapes dropped from the view but still broadcast | `FeedGatewayService.java:9656-9690` |
   127	| Connect hello `cvd-hello` = `{sessionDate, hwm:{tf: maxBarStartMs}[, levels]}` sent to every new session when CVD or SPX-levels is enabled | `FeedGatewayService.java:908-916`, `:10219-10235` |
   128	| Backfill `GET /api/cvd/bars?tf&toMs&afterMs&limit&sessionDate` → `{sessionDate, [sessionMismatch], bars[], nextCursor}`; page, cursor and session stamp are ONE synchronized snapshot; typed `long`/`int` parameters are bound by Spring BEFORE the handler runs (malformed ⇒ 400 with no footprint code executed) | `GatewayController.java:50-72`; `FeedGatewayService.java:10247-10269` |
   129	| REST authentication is EXPLICIT and in-handler where a route requires it: such routes call `LiquidityHistoryAuth.authenticate(...)` themselves (there is no JWT servlet filter for `/api`) — the precedent is `LiquidityHistoryController`; the CVD bars route makes NO such call and is cited in this document ONLY as the typed-binding and backfill-semantics precedent | `liquidityhistory/LiquidityHistoryController.java:86`; `GatewayController.java:50-72` |
   130	| Per-session (auth) routing drops any event not in the standalone allowlist; `es-cvd` / `es-cvd-bar` are allowlisted | `FeedGatewayService.java:11493-11494` |
   131	| The external ES CVD page stores the hello object and reads only `sessionDate`/`hwm`, so an added field is ignored by it | `options-edge/src/app/web/assets/es-cvd.js:278-304` |
   132	| Tests: view semantics and the one-wiring-path pin | `src/test/java/app/feedgateway/CvdBarViewTest.java` |
   133	| es4 deployment: `JAVA_TOOL_OPTIONS=-Xms256m -Xmx1536m`, container memory limit `1536Mi` | `options-edge-deploy/k8s/es4/services/es-feed-gateway.yaml:120-121`, `:176-178` |
   134	| Manually assigned consumers discover topology growth through `PartitionRefresh.apply(consumer, assigned)`, called on the CONSUMER'S OWN poll thread every `partitionMetadataRefreshMs`: it re-reads partitions for the consumer's (immutable, `Set.copyOf`) topic set, merges the newly discovered partitions and calls `consumer.assign(merged)` ITSELF, then returns a `Refresh` whose `added()` the CALLER seeks and books (never shrinks; a metadata timeout is absorbed and retried); an optional topic absent at bootstrap is discovered by exactly this path when it appears | `FeedGatewayService.java:3596-3680` (`PartitionRefresh`; the internal assign at `:3665-3671`), `apply()` call sites `:2310` (state cache) and `:2677` (state live) |
   135	| Consumer settings the gateway ENFORCES on every consumer: `max.poll.records` = `GATEWAY_KAFKA_MAX_POLL_RECORDS` (default 100), `fetch.max.bytes` = `GATEWAY_KAFKA_FETCH_MAX_BYTES` (default 4 MiB), `max.partition.fetch.bytes` = `GATEWAY_KAFKA_MAX_PARTITION_FETCH_BYTES` (default 512 KiB) | `GatewaySettings.java:1072`, `:1077`, `:1081`; `FeedGatewayService.java:12294-12296` |
   136	
   137	Upstream record facts the gateway relies on (ES-FOOTPRINT-DESIGN.md §5): every record carries
   138	`schemaVersion` (6), `symbol` and `sessionDate` (ISO `YYYY-MM-DD`); a closed-bar record carries
   139	`timeframe` and `observations.barStartMs`; an outcome record carries `timeframe`, `identity` and
   140	`resolvedAtBarStartMs`; the two LIVE records carry no singular `timeframe` but the fixed-key
   141	`timeframes` object and `eventTimeMs`. Every string is drawn from the ASCII alphabet
   142	`[A-Za-z0-9_.:|-]` (F-E8), identities are ≤ 128 bytes, and every record is ≤ the producer's
   143	`maxRecordBytes` (default 262 144) — the evidence live record ≤ `maxEvidenceBytes` (same default).
   144	Bar and outcome records are compacted-topic upserts (F-R14): at-least-once delivery is invisible
   145	under last-write-per-key. Epoch-ms values lie in `[0, 253402300799999]` (§5 timestamp domain).
   146	
   147	## 2. Requirements
   148	
   149	| id | Requirement |
   150	|----|-------------|
   151	| G-R1 | **Flag.** `GATEWAY_ES_FOOTPRINT_ENABLED` (default `false`; `GatewaySettings.esFootprintEnabled()`). Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`. Flag on with a topic absent on the cluster: the gateway starts (the topics are OPTIONAL like every other DATABENTO JSON topic) and the page reports no data. |
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
   183	| G-R4 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
   184	| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
   185	| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
   186	| G-R7 | 5 of 6 probes pinned, over 4 clauses (1 survived) | 2 | Backfill routes |
   187	| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
   188	| G-R9 | 3 of 3 probes pinned, over 3 clauses | 2 | Metrics |
   189	| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
   190	| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
   191	| G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | Deployment contingency |
   192	
   193	12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).
   194	
   195	Read the state column narrowly. "n of m probes pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION (or propagate a throw the spec declared and the run matched) — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.
   196	
   197	<!-- END footprint-reqstate -->
   198	
   199	## 3. Decisions
   200	
   201	- **D1 No live retention.** `es-footprint`/`es-footprint-evidence` are not retained for connect
   202	  replay: the producer's 5 s heartbeat bounds the wait and the CVD page already lives with the same
   203	  bound. Retention would add the SPX-levels attestation machinery for no page benefit.
   204	- **D2 One hello.** A second `footprint-hello` frame would give the page two handshakes to order;
   205	  one field inside the existing frame keeps the R46 protocol single.

2026-09-08T21:57:13.091035Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
1. HIGH — The quotation-to-clause validation still accepts unrelated text from the same requirement. `documentText` is checked only as a substring of the entire requirement ([footprint-reqstate.py](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:56)); it does not establish that the quotation states the mutated clause.

   This leaves five misquoted probes:

   - `G-R4 older-date-dropped` quotes coordinator ownership, not “OLDER ⇒ stale_session drop” ([campaign](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:2)).
   - `G-R4 equal-date-upserts` likewise quotes ownership, not “EQUAL (or first) ⇒ upsert” ([campaign](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:123)).
   - All three `G-R7 the-epoch-guard` variants quote the general bars-backfill sentence. That sentence contains no epoch-domain obligation. Variant 1 mutates admission timestamp validation, which is expressly a G-R4 clause; variants 2–3 concern overflow-safe exclusive-cursor implementation, which G-R7 does not state as a separate normative clause ([design](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:156)).

   Consequently, G-R4’s displayed `3/3 over 3 clauses` omits its epoch-domain probe while counting two correctly filed but incorrectly quoted clauses; G-R7’s `5/6 over 4 clauses` counts an unsupported “epoch guard” clause. The §2a counts are therefore not yet defensible ([design](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:183)).

Round 4 disposition check:

- Rollover accounting is now correctly filed under G-R9 and quotes G-R9’s own sentence.
- The three epoch records are correctly grouped as variants of one nominal clause.
- The G-R4/G-R7 re-quoting is incomplete for the five records above.
- Failure evidence is materially improved: every named assertion failure I checked appears in that record’s `failureLines`, and the harness records names derived from those lines. The renderer does refuse missing/non-verbatim quotations and unsupported failure names, but its substring test cannot verify semantic clause attribution.

All other §2a probes have quotations that state the exercised obligation, mutations that break that obligation, substantiating assertion lines, and correct record/group counts.

What remains unprobed is substantial: most clauses within G-R1–G-R10—including many flag-off effects, delivery ordering/verbatim behavior, most admission validation, atomic locking, most backfill/cursor/auth/streaming rules, heap/layout bounds, most topic-admission behavior, most metrics, and CVD/lock non-interference—have no mutation here; G-R11 is wholly unprobed as a test inventory. The prose at lines 193–195 honestly says probe counts do not establish whole-requirement coverage, but the five misattributed records still make the displayed clause counts misleading.

VERDICT: REQUEST_CHANGES
tokens used
80.036
1. HIGH — The quotation-to-clause validation still accepts unrelated text from the same requirement. `documentText` is checked only as a substring of the entire requirement ([footprint-reqstate.py](/private/tmp/oe-conf-gw/scripts/footprint-reqstate.py:56)); it does not establish that the quotation states the mutated clause.

   This leaves five misquoted probes:

   - `G-R4 older-date-dropped` quotes coordinator ownership, not “OLDER ⇒ stale_session drop” ([campaign](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:2)).
   - `G-R4 equal-date-upserts` likewise quotes ownership, not “EQUAL (or first) ⇒ upsert” ([campaign](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:123)).
   - All three `G-R7 the-epoch-guard` variants quote the general bars-backfill sentence. That sentence contains no epoch-domain obligation. Variant 1 mutates admission timestamp validation, which is expressly a G-R4 clause; variants 2–3 concern overflow-safe exclusive-cursor implementation, which G-R7 does not state as a separate normative clause ([design](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:156)).

   Consequently, G-R4’s displayed `3/3 over 3 clauses` omits its epoch-domain probe while counting two correctly filed but incorrectly quoted clauses; G-R7’s `5/6 over 4 clauses` counts an unsupported “epoch guard” clause. The §2a counts are therefore not yet defensible ([design](/private/tmp/oe-conf-gw/ES-FOOTPRINT-GATEWAY-DESIGN.md:183)).

Round 4 disposition check:

- Rollover accounting is now correctly filed under G-R9 and quotes G-R9’s own sentence.
- The three epoch records are correctly grouped as variants of one nominal clause.
- The G-R4/G-R7 re-quoting is incomplete for the five records above.
- Failure evidence is materially improved: every named assertion failure I checked appears in that record’s `failureLines`, and the harness records names derived from those lines. The renderer does refuse missing/non-verbatim quotations and unsupported failure names, but its substring test cannot verify semantic clause attribution.

All other §2a probes have quotations that state the exercised obligation, mutations that break that obligation, substantiating assertion lines, and correct record/group counts.

What remains unprobed is substantial: most clauses within G-R1–G-R10—including many flag-off effects, delivery ordering/verbatim behavior, most admission validation, atomic locking, most backfill/cursor/auth/streaming rules, heap/layout bounds, most topic-admission behavior, most metrics, and CVD/lock non-interference—have no mutation here; G-R11 is wholly unprobed as a test inventory. The prose at lines 193–195 honestly says probe counts do not establish whole-requirement coverage, but the five misattributed records still make the displayed clause counts misleading.

VERDICT: REQUEST_CHANGES
