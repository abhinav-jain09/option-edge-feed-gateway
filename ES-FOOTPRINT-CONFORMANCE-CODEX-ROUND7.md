Reading additional input from stdin...
2026-09-08T22:21:25.321866Z ERROR codex_models_manager::manager: failed to load models cache: missing field `supports_parallel_tool_calls` at line 93 column 5
OpenAI Codex v0.147.0
--------
workdir: /private/tmp/oe-conf-gw
model: gpt-5.6-sol
provider: openai
approval: never
sandbox: read-only
reasoning effort: low
reasoning summaries: none
session id: 01a0831c-9d2b-76e0-8ef3-abfff7f758dc
--------
user
Round 7 of the review of option-edge-feed-gateway PR #174 at /private/tmp/oe-conf-gw.

Round 6's finding was folded. For the gateway that was four probes sharing one clause text after a
careless bulk reword — each now carries the clause it actually breaks. For the page it was a probe
recorded as proving "outcomes are evicted before bars" when it only establishes that retained
outcomes participate in eviction (the clause is narrowed and the ordering left unprobed), plus three
obligations quoted from the wrong sentence.

VERIFY against the files and ES-FOOTPRINT-CAMPAIGN.json. Then review the whole change once more,
line by line, and report anything that still overclaims, is misattributed, misquoted or unheld.
For EVERY row check: quotation verbatim in that requirement AND stating the clause the probe breaks;
probes filed under the requirement they exercise; probe and clause counts correct; every KILLED
record's named assertion failures present in its own failureLines; nothing claimed that the run does
not establish.

End with exactly one line: VERDICT: APPROVE  or  VERDICT: REQUEST_CHANGES
codex
I’ll audit the campaign file against the complete diff and supporting files, then trace every row’s quotations, counts, and killed-failure evidence before issuing the exact verdict line.
exec
/bin/bash -lc "pwd && rg --files -g 'AGENTS.md' -g 'ES-FOOTPRINT-CAMPAIGN.json' -g '"'!node_modules'"' | sed -n '1,120p' && git status --short && git diff --stat && git diff --name-only" in /private/tmp/oe-conf-gw
 succeeded in 5661ms:
2026-09-09 00:21:33.282 xcodebuild[26503:267214665]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:21:33.604 xcodebuild[26503:267214664] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-CIZFOOPc' (errno=Operation not permitted)
2026-09-09 00:21:34.144 xcodebuild[26520:267214733]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:21:34.288 xcodebuild[26520:267214730] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-DwueohPD' (errno=Operation not permitted)
2026-09-09 00:21:35.364 xcodebuild[26540:267214827]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:21:35.526 xcodebuild[26540:267214825] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-CD7GJ5tu' (errno=Operation not permitted)
2026-09-09 00:21:35.991 xcodebuild[26805:267215233]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:21:36.160 xcodebuild[26805:267215221] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-ap6aUEJb' (errno=Operation not permitted)
2026-09-09 00:21:37.201 xcodebuild[26899:267215441]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:21:37.346 xcodebuild[26899:267215438] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.
git: warning: confstr() failed with code 5: couldn't get path of DARWIN_USER_TEMP_DIR; using /tmp instead
git: error: couldn't create cache file '/tmp/xcrun_db-gH7b5g5z' (errno=Operation not permitted)
2026-09-09 00:21:37.819 xcodebuild[26908:267215487]  DVTFilePathFSEvents: Failed to start fs event stream.
2026-09-09 00:21:37.982 xcodebuild[26908:267215484] [MT] DVTDeveloperPaths: Failed to get length of DARWIN_USER_CACHE_DIR from confstr(3), error = Error Domain=NSPOSIXErrorDomain Code=5 "Input/output error". Using NSCachesDirectory instead.

exec
/bin/bash -lc "find .. -name AGENTS.md -print 2>/dev/null; sed -n '1,260p' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 5399ms:
{
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
  "seconds": 8.4,
  "patch": {
   "old": "        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;",
   "new": "        bars.clear(); barsBytes = 0;"
  },
  "evidence": {
   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "returnCode": 1,
   "mutatedFileSha256": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6",
   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   "filesSha256": {
    "src/main/java/app/feedgateway/FootprintViews.java": {
     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
     "mutated": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6"
    }
   },
   "outputSha256": "e828bf491785b2f2126bd6f670f68a848e95719ff059004acee12a6a0db75304",
   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   "failureLines": [
    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.003 s <<< FAILURE!",
    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>"
   ],
   "treeRestoredClean": true
  },
  "baseline": {
   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "returnCode": 0,
   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  }
 },
 "G-R9.3 a-rollover-is-counted": {
  "status": "KILLED",
  "clause": "rollover clears are counted in gateway_footprint_rollovers_total, one per date advance",
  "requirement": "G-R9",
  "documentText": "`gateway_footprint_evictions_total{view}` \u2014 records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).",
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
  "seconds": 9.3,
  "patch": {
   "old": "        rollovers.incrementAndGet();",
   "new": "        if (false) rollovers.incrementAndGet();"
  },
  "evidence": {
   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "returnCode": 1,
   "mutatedFileSha256": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5",
   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   "filesSha256": {
    "src/main/java/app/feedgateway/FootprintViews.java": {
     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
     "mutated": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5"
    }
   },
   "outputSha256": "8c192e089a19d101b11fb761901287bda9d7e7acf2d0f07fbf59d1393a9860c7",
   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   "failureLines": [
    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.008 s <<< FAILURE!",
    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>"
   ],
   "treeRestoredClean": true
  },
  "baseline": {
   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "returnCode": 0,
   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  }
 },
 "G-R8 evict-oldest": {
  "status": "KILLED",
  "clause": "eviction takes the OLDEST key of the timeframe it is evicting from",
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
  "seconds": 9.7,
  "patch": {
   "old": "            String oldest = view.ceilingKey(tf + \"|\");",
   "new": "            String oldest = view.floorKey(tf + \"|~\");"
  },
  "evidence": {
   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "returnCode": 1,
   "mutatedFileSha256": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146",
   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   "filesSha256": {
    "src/main/java/app/feedgateway/FootprintViews.java": {
     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
     "mutated": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146"
    }
   },
   "outputSha256": "28bcb7d3a48b27e6f9571071daf0b935dd169f958fbd3f78e03f2ef1a13c7cca",
   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   "failureLines": [
    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE!",
    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246>",
    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>"
   ],
   "treeRestoredClean": true
  },
  "baseline": {
   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "returnCode": 0,
   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  }
 },
 "G-R8 evict-rotates": {
  "status": "KILLED",
  "clause": "eviction rotates round-robin across the timeframes rather than draining one",
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
  "seconds": 8.5,
  "patch": {
   "old": "            if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;",
   "new": "            if (isBars) barsEvictCursor = cursor; else outcomesEvictCursor = cursor;"
  },
  "evidence": {
   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "returnCode": 1,
   "mutatedFileSha256": "1c700daa48bd560d42ed500056b7f54ddf4a324cadb6fdd5913d4690ec844c21",
   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   "filesSha256": {
    "src/main/java/app/feedgateway/FootprintViews.java": {
     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
     "mutated": "1c700daa48bd560d42ed500056b7f54ddf4a324cadb6fdd5913d4690ec844c21"
    }
   },
   "outputSha256": "d725c9adb3fec20c73306d454323307c694fdc35a69c2b17baf8457d4ad305d9",
   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   "failureLines": [
    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.008 s <<< FAILURE!",
    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:245 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>"
   ],
   "treeRestoredClean": true
  },
  "baseline": {
   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "returnCode": 0,
   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
  }
 },
 "G-R8 byte-budget-decremented": {
  "status": "KILLED",
  "clause": "evicting a record decrements the view byte total by that record length",
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
  "seconds": 9.6,
  "patch": {
   "old": "            if (isBars) barsBytes -= len; else outcomesBytes -= len;",
   "new": "            if (isBars) barsBytes -= 0; else outcomesBytes -= 0;"
  },
  "evidence": {
   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
   "returnCode": 1,
   "mutatedFileSha256": "604a8ec8ce620cc3e8d1e4a18e51bdc41af6639c24b90f01754b45a9619c0d3a",
   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
   "filesSha256": {
    "src/main/java/app/feedgateway/FootprintViews.java": {
     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
     "mutated": "604a8ec8ce620cc3e8d1e4a18e51bdc41af6639c24b90f01754b45a9619c0d3a"
    }
   },
   "outputSha256": "9a312fb53d5937f2cf251ef66e8324bddf9f9227ed2af402a2244d5da58ac882",
   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
   "failureLines": [
    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!",
    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <3>"

exec
/bin/bash -lc "find . -maxdepth 3 -type f | sort | sed -n '1,240p'; rg -n 'G-R[0-9]|probe|ES-FOOTPRINT' . --glob '"'!ES-FOOTPRINT-CAMPAIGN.json'"' --glob '"'!target/**'"' | sed -n '1,300p'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
./.env.dev.example
./.env.prod.example
./.git
./.gitignore
./Dockerfile
./ES-FOOTPRINT-CAMPAIGN.json
./ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND1.md
./ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND2.md
./ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND3.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND9.md
./ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND1.md
./ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND2.md
./ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND3.md
./ES-FOOTPRINT-GATEWAY-CTOR-CODEX-ROUND4.md
./ES-FOOTPRINT-GATEWAY-DESIGN.md
./GATEWAY-EPOCH-CURSOR-CODEX-ROUND1.md
./GATEWAY-EPOCH-CURSOR-CODEX-ROUND2.md
./GATEWAY-EPOCH-CURSOR-CODEX-ROUND3.md
./GATEWAY-EPOCH-CURSOR-CODEX-ROUND4.md
./GATEWAY-SUREFIRE-ROUTING-CODEX-ROUND1.md
./GATEWAY-SUREFIRE-ROUTING-CODEX-ROUND2.md
./Jenkinsfile
./Jenkinsfile.deploy
./MULTI-REPLICA.md
./README.md
./accelration.md
./deploymodel.md
./dev/keycloak/realm-export.json
./dev/keycloak/run-keycloak.sh
./dev/raw-feed-replicator/dependency-reduced-pom.xml
./dev/raw-feed-replicator/pom.xml
./dev/redis/run-redis.sh
./pom.xml
./scripts/footprint-conformance.py
./scripts/footprint-mutate.py
./scripts/footprint-reqstate.preamble
./scripts/footprint-reqstate.py
./scripts/footprint-reqstate.sh
./target/classes/application.properties
./target/surefire-reports/TEST-app.feedgateway.ApprovalReaperTest.xml
./target/surefire-reports/TEST-app.feedgateway.CvdBarViewTest.xml
./target/surefire-reports/TEST-app.feedgateway.CvdSpxLevelsWiringTest.xml
./target/surefire-reports/TEST-app.feedgateway.DeltaFlowAccelWiringTest.xml
./target/surefire-reports/TEST-app.feedgateway.EsAggressorFlowWiringTest.xml
./target/surefire-reports/TEST-app.feedgateway.EsAuctionWiringTest.xml
./target/surefire-reports/TEST-app.feedgateway.FeedGatewayLogoutTest.xml
./target/surefire-reports/TEST-app.feedgateway.FeedGatewayPerSessionBroadcastTest.xml
./target/surefire-reports/TEST-app.feedgateway.FeedGatewayReplayCancellationTest.xml
./target/surefire-reports/TEST-app.feedgateway.FeedGatewayReplayCompletionTest.xml
./target/surefire-reports/TEST-app.feedgateway.FeedGatewayRolloverDiagnosticsTest.xml
./target/surefire-reports/TEST-app.feedgateway.FeedGatewayServiceTest.xml
./target/surefire-reports/TEST-app.feedgateway.FeedGatewayShutdownTest.xml
./target/surefire-reports/TEST-app.feedgateway.FeedWebSocketHandlerAttachTest.xml
./target/surefire-reports/TEST-app.feedgateway.FootprintBackfillControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.FootprintSeamTest.xml
./target/surefire-reports/TEST-app.feedgateway.FootprintTopicGateTest.xml
./target/surefire-reports/TEST-app.feedgateway.FootprintViewsTest.xml
./target/surefire-reports/TEST-app.feedgateway.FootprintWiringTest.xml
./target/surefire-reports/TEST-app.feedgateway.GatewayCalendarConfigTest.xml
./target/surefire-reports/TEST-app.feedgateway.GatewayContextSmokeTest.xml
./target/surefire-reports/TEST-app.feedgateway.GatewayIsolationLevelTest.xml
./target/surefire-reports/TEST-app.feedgateway.GatewayMarketCalendarTest.xml
./target/surefire-reports/TEST-app.feedgateway.HotStrikeEventTest.xml
./target/surefire-reports/TEST-app.feedgateway.HpsfGatewayViewMapperTest.xml
./target/surefire-reports/TEST-app.feedgateway.LiquidityHistoryWsChainsTest.xml
./target/surefire-reports/TEST-app.feedgateway.OutboundChannelTest.xml
./target/surefire-reports/TEST-app.feedgateway.PartitionExpansionIntegrationTest.xml
./target/surefire-reports/TEST-app.feedgateway.SecurityHeadersFilterTest.xml
./target/surefire-reports/TEST-app.feedgateway.SellerActivityCacheIntegrationTest.xml
./target/surefire-reports/TEST-app.feedgateway.SessionExpirySweepTest.xml
./target/surefire-reports/TEST-app.feedgateway.SpotBandForwardingTest.xml
./target/surefire-reports/TEST-app.feedgateway.StaticAssetSecurityTest.xml
./target/surefire-reports/TEST-app.feedgateway.WebSocketConfigOriginsTest.xml
./target/surefire-reports/TEST-app.feedgateway.WsInterceptorWiringTest.xml
./target/surefire-reports/TEST-app.feedgateway.WsJwtHandshakeInterceptorTest.xml
./target/surefire-reports/TEST-app.feedgateway.WsTokenExpiryTest.xml
./target/surefire-reports/TEST-app.feedgateway.contexttape.ContextTapeAuthAllowlistRegressionTest.xml
./target/surefire-reports/TEST-app.feedgateway.contexttape.ContextTapeControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.contexttape.ContextTapeMvcTest.xml
./target/surefire-reports/TEST-app.feedgateway.gammamigration.GammaFragilityControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.gammamigration.GammaMigrationControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.gammamigration.GammaRotationControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.liquidityhistory.HistoryFrameFailClosedTest.xml
./target/surefire-reports/TEST-app.feedgateway.liquidityhistory.LiquidityHistoryControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.liquidityhistory.LiquidityHistoryFoldTest.xml
./target/surefire-reports/TEST-app.feedgateway.liquidityhistory.LiquidityHistoryStoreEpochTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.ApprovalStateMachineTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.EventTypeTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.InMemoryTicketStoreConcurrencyTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.InMemoryTicketStoreTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.NormalizationTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.RedisTicketStoreIntegrationTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.RoutingKeyDeriverTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.SessionIsolationIntegrationTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.SessionLifecycleTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.SessionRoutingEngineConcurrencyTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.SessionRoutingEngineReplayTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.SessionRoutingEngineTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.StrikeWindowTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.SubscriptionManagerTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.UserSessionPolicyTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.approval.ApprovalAuthorityTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.auth.KeycloakJwtVerifierHermeticTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.auth.KeycloakJwtVerifierIntegrationTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.DealerLedgerJoinerTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.GatewayRecordMapperTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.HandshakeTicketAuthenticatorTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.MtSessionAuthConfigTicketStoreTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.MtSessionSecurityInvariantTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.OrchestratorReplayRunAuthorizerTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.OrchestratorReplayRunListerTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.ReplayChronologyTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.ReplayControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.ReplayParamsTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.ReplayServiceTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.ReplayTopicResolverTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.TicketHandshakeInterceptorTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.TicketSubProtocolHandshakeHandlerTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.WsTicketControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.mtsession.gateway.WsTicketServiceTest.xml
./target/surefire-reports/TEST-app.feedgateway.pinflow.PinFlowAggregatorTest.xml
./target/surefire-reports/TEST-app.feedgateway.pinflow.PinFlowAuthAllowlistRegressionTest.xml
./target/surefire-reports/TEST-app.feedgateway.pinflow.PinFlowControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.pinflow.PinFlowDataSourceConfigTest.xml
./target/surefire-reports/TEST-app.feedgateway.pinflow.PinFlowSessionWindowTest.xml
./target/surefire-reports/TEST-app.feedgateway.pinflow.PinFlowSpotHistoryTest.xml
./target/surefire-reports/TEST-app.feedgateway.pinflow.PinFlowSpotSymbolBindingTest.xml
./target/surefire-reports/TEST-app.feedgateway.pinflow.PinFlowStoreTest.xml
./target/surefire-reports/TEST-app.feedgateway.selleractivity.SellerActivityAggregatorTest.xml
./target/surefire-reports/TEST-app.feedgateway.selleractivity.SellerActivityControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.selleractivity.SellerActivityStreamReleaseTest.xml
./target/surefire-reports/TEST-app.feedgateway.stockgex.StockGexAsyncConfigTest.xml
./target/surefire-reports/TEST-app.feedgateway.stockgex.StockGexAsyncPipelineTest.xml
./target/surefire-reports/TEST-app.feedgateway.stockgex.StockGexAuthAllowlistRegressionTest.xml
./target/surefire-reports/TEST-app.feedgateway.stockgex.StockGexCloseBoardTest.xml
./target/surefire-reports/TEST-app.feedgateway.stockgex.StockGexControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.stockgex.StockGexMvcTest.xml
./target/surefire-reports/TEST-app.feedgateway.systemstatus.SystemStatusBudgetTest.xml
./target/surefire-reports/TEST-app.feedgateway.systemstatus.SystemStatusControllerTest.xml
./target/surefire-reports/TEST-app.feedgateway.systemstatus.SystemStatusFailureTest.xml
./target/surefire-reports/TEST-app.feedgateway.systemstatus.SystemStatusLogSafetyTest.xml
./target/surefire-reports/TEST-app.feedgateway.systemstatus.SystemStatusStoreTest.xml
./target/surefire-reports/app.feedgateway.ApprovalReaperTest.txt
./target/surefire-reports/app.feedgateway.CvdBarViewTest.txt
./target/surefire-reports/app.feedgateway.CvdSpxLevelsWiringTest.txt
./target/surefire-reports/app.feedgateway.DeltaFlowAccelWiringTest.txt
./target/surefire-reports/app.feedgateway.EsAggressorFlowWiringTest.txt
./target/surefire-reports/app.feedgateway.EsAuctionWiringTest.txt
./target/surefire-reports/app.feedgateway.FeedGatewayLogoutTest.txt
./target/surefire-reports/app.feedgateway.FeedGatewayPerSessionBroadcastTest.txt
./target/surefire-reports/app.feedgateway.FeedGatewayReplayCancellationTest.txt
./target/surefire-reports/app.feedgateway.FeedGatewayReplayCompletionTest.txt
./target/surefire-reports/app.feedgateway.FeedGatewayRolloverDiagnosticsTest.txt
./target/surefire-reports/app.feedgateway.FeedGatewayServiceTest.txt
./target/surefire-reports/app.feedgateway.FeedGatewayShutdownTest.txt
./target/surefire-reports/app.feedgateway.FeedWebSocketHandlerAttachTest.txt
./target/surefire-reports/app.feedgateway.FootprintBackfillControllerTest.txt
./target/surefire-reports/app.feedgateway.FootprintSeamTest.txt
./target/surefire-reports/app.feedgateway.FootprintTopicGateTest.txt
./target/surefire-reports/app.feedgateway.FootprintViewsTest.txt
./target/surefire-reports/app.feedgateway.FootprintWiringTest.txt
./target/surefire-reports/app.feedgateway.GatewayCalendarConfigTest.txt
./target/surefire-reports/app.feedgateway.GatewayContextSmokeTest.txt
./target/surefire-reports/app.feedgateway.GatewayIsolationLevelTest.txt
./target/surefire-reports/app.feedgateway.GatewayMarketCalendarTest.txt
./target/surefire-reports/app.feedgateway.HotStrikeEventTest.txt
./target/surefire-reports/app.feedgateway.HpsfGatewayViewMapperTest.txt
./target/surefire-reports/app.feedgateway.LiquidityHistoryWsChainsTest.txt
./target/surefire-reports/app.feedgateway.OutboundChannelTest.txt
./target/surefire-reports/app.feedgateway.PartitionExpansionIntegrationTest.txt
./target/surefire-reports/app.feedgateway.SecurityHeadersFilterTest.txt
./target/surefire-reports/app.feedgateway.SellerActivityCacheIntegrationTest.txt
./target/surefire-reports/app.feedgateway.SessionExpirySweepTest.txt
./target/surefire-reports/app.feedgateway.SpotBandForwardingTest.txt
./target/surefire-reports/app.feedgateway.StaticAssetSecurityTest.txt
./target/surefire-reports/app.feedgateway.WebSocketConfigOriginsTest.txt
./target/surefire-reports/app.feedgateway.WsInterceptorWiringTest.txt
./target/surefire-reports/app.feedgateway.WsJwtHandshakeInterceptorTest.txt
./target/surefire-reports/app.feedgateway.WsTokenExpiryTest.txt
./target/surefire-reports/app.feedgateway.contexttape.ContextTapeAuthAllowlistRegressionTest.txt
./target/surefire-reports/app.feedgateway.contexttape.ContextTapeControllerTest.txt
./target/surefire-reports/app.feedgateway.contexttape.ContextTapeMvcTest.txt
./target/surefire-reports/app.feedgateway.gammamigration.GammaFragilityControllerTest.txt
./target/surefire-reports/app.feedgateway.gammamigration.GammaMigrationControllerTest.txt
./target/surefire-reports/app.feedgateway.gammamigration.GammaRotationControllerTest.txt
./target/surefire-reports/app.feedgateway.liquidityhistory.HistoryFrameFailClosedTest.txt
./target/surefire-reports/app.feedgateway.liquidityhistory.LiquidityHistoryControllerTest.txt
./target/surefire-reports/app.feedgateway.liquidityhistory.LiquidityHistoryFoldTest.txt
./target/surefire-reports/app.feedgateway.liquidityhistory.LiquidityHistoryStoreEpochTest.txt
./target/surefire-reports/app.feedgateway.mtsession.ApprovalStateMachineTest.txt
./target/surefire-reports/app.feedgateway.mtsession.EventTypeTest.txt
./target/surefire-reports/app.feedgateway.mtsession.InMemoryTicketStoreConcurrencyTest.txt
./target/surefire-reports/app.feedgateway.mtsession.InMemoryTicketStoreTest.txt
./target/surefire-reports/app.feedgateway.mtsession.NormalizationTest.txt
./target/surefire-reports/app.feedgateway.mtsession.RedisTicketStoreIntegrationTest.txt
./target/surefire-reports/app.feedgateway.mtsession.RoutingKeyDeriverTest.txt
./target/surefire-reports/app.feedgateway.mtsession.SessionIsolationIntegrationTest.txt
./target/surefire-reports/app.feedgateway.mtsession.SessionLifecycleTest.txt
./target/surefire-reports/app.feedgateway.mtsession.SessionRoutingEngineConcurrencyTest.txt
./target/surefire-reports/app.feedgateway.mtsession.SessionRoutingEngineReplayTest.txt
./target/surefire-reports/app.feedgateway.mtsession.SessionRoutingEngineTest.txt
./target/surefire-reports/app.feedgateway.mtsession.StrikeWindowTest.txt
./target/surefire-reports/app.feedgateway.mtsession.SubscriptionManagerTest.txt
./target/surefire-reports/app.feedgateway.mtsession.UserSessionPolicyTest.txt
./target/surefire-reports/app.feedgateway.mtsession.approval.ApprovalAuthorityTest.txt
./target/surefire-reports/app.feedgateway.mtsession.auth.KeycloakJwtVerifierHermeticTest.txt
./target/surefire-reports/app.feedgateway.mtsession.auth.KeycloakJwtVerifierIntegrationTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.DealerLedgerJoinerTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.GatewayRecordMapperTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.HandshakeTicketAuthenticatorTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.MtSessionAuthConfigTicketStoreTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.MtSessionSecurityInvariantTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.OrchestratorReplayRunAuthorizerTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.OrchestratorReplayRunListerTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.ReplayChronologyTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.ReplayControllerTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.ReplayParamsTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.ReplayServiceTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.ReplayTopicResolverTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.TicketHandshakeInterceptorTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.TicketSubProtocolHandshakeHandlerTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.WsTicketControllerTest.txt
./target/surefire-reports/app.feedgateway.mtsession.gateway.WsTicketServiceTest.txt
./target/surefire-reports/app.feedgateway.pinflow.PinFlowAggregatorTest.txt
./target/surefire-reports/app.feedgateway.pinflow.PinFlowAuthAllowlistRegressionTest.txt
./target/surefire-reports/app.feedgateway.pinflow.PinFlowControllerTest.txt
./target/surefire-reports/app.feedgateway.pinflow.PinFlowDataSourceConfigTest.txt
./target/surefire-reports/app.feedgateway.pinflow.PinFlowSessionWindowTest.txt
./target/surefire-reports/app.feedgateway.pinflow.PinFlowSpotHistoryTest.txt
./target/surefire-reports/app.feedgateway.pinflow.PinFlowSpotSymbolBindingTest.txt
./target/surefire-reports/app.feedgateway.pinflow.PinFlowStoreTest.txt
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md:7:1. **RESOLVED.** G-R11(17) now requires all four footprint topics in both immutable discovery sets while restricting assignments to admitted partitions.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md:9:2. **RESOLVED.** G-R8a now defines an implementable seam: `Predicate<String> topicAdmit` is evaluated inside `PartitionRefresh.apply()` after discovery but before `addedPartitions`, merge, and the internal `consumer.assign(...)`.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md:37:1. **MEDIUM — `ES-FOOTPRINT-GATEWAY-DESIGN.md:117,120` — contradictory constructor-call-site count.** G-R8a says “the existing six constructor call sites pass `t -> true`; the two STATE consumers pass `footprintGate::admit`,” while G-R11(17) again requires all six existing sites to pass the always-true predicate. There are six total call sites, including the two state consumers at `FeedGatewayService.java:2303,2674`; both statements cannot hold. **Required change:** specify that the other four call sites pass `t -> true`, while the two state call sites pass `footprintGate::admit`; correct G-R11(17) accordingly.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND8.md:39:2. **MEDIUM — `ES-FOOTPRINT-GATEWAY-DESIGN.md:143-149` — merged prerequisite is still documented as unmerged and conditional.** The compression prerequisite has merged as options-edge-processing PR #757, but §4 still says “not yet merged,” “in its own CODE gate,” and “conditional on that merge.” **Required change:** record PR #757 as merged and remove the obsolete conditional/until-merge wording, while retaining G-R8a’s runtime validation as the continuing safety guard.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md:7:1. **RESOLVED.** The upstream change set exists at `/private/tmp/oe-footprint-compression` on branch `feat/es-footprint-uncompressed`. `EsCvdRuntime.producerProps` explicitly sets `compression.type=none` at `EsCvdRuntime.java:1364`; `EsCvdRuntimeTest.java:102-109` pins it; and `ES-FOOTPRINT-DESIGN.md` records amendment 12. Section 4 accurately makes G-R8 conditional on merging that change set.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md:9:2. **NOT RESOLVED.** G-R8a is not implementable with the AS-IS `PartitionRefresh` contract as written. `PartitionRefresh.apply()` discovers, merges, and calls `consumer.assign(merged)` internally at `FeedGatewayService.java:3665-3671`. The callers receive the result only afterward at `:2310-2313` and `:2677-2680`. Therefore no gate can sit “after `apply`, before the caller assigns”; the caller does not assign. Keeping all four topics in the immutable `Set.copyOf(topics)` is correct, but the design must explicitly require either:
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md:26:1. **HIGH — `ES-FOOTPRINT-GATEWAY-DESIGN.md:112` — acceptance test contradicts G-R8a.** G-R11(17) still requires `addEsFootprintTopics` to add only validated topics and says a later-valid topic appears in the “next topic set.” G-R8a correctly requires all four topics to be present from construction because `PartitionRefresh` snapshots an immutable topic set. Required change: rewrite the test to assert all four topics are always in both immutable discovery sets, while only validated partitions enter assignments.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md:28:2. **HIGH — `ES-FOOTPRINT-GATEWAY-DESIGN.md:84,109`; `FeedGatewayService.java:3665-3671` — the documented refresh/assignment seam does not exist.** Required change: specify the concrete contract/API change or an in-`apply()` validation callback, and update both state-consumer flows and lifecycle tests accordingly.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND7.md:30:3. **MEDIUM — `ES-FOOTPRINT-GATEWAY-DESIGN.md:79` — stale authentication statement and empty anchor remain despite the revision-7 disposition.** Required change: cite `LiquidityHistoryController.java:86` for explicit authentication and describe the CVD route solely as the typed-binding/backfill precedent.
./ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND1.md:5:1. **HIGH — [FeedGatewayService.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:2838), [FeedGatewayService.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:6283) — live-consumer retries replay and broadcast the keyed footprint cache window.** On every retry, `runLiveConsumerOnce` calls `seekToCacheWindow`; `cachePolicyFor` gives bars/outcomes a 24-hour window. Consequently the live consumer re-admits and broadcasts historical compacted bars/outcomes instead of seeking END. This contradicts G-R8a’s live-consumer semantics and can generate a large duplicate backfill over WebSockets. **Remediation:** make footprint partitions seek END in the live retry path, while preserving the full-session seek only for the cache consumer; add a behavioral retry/seek test.
./ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND1.md:7:2. **MEDIUM — [FootprintViews.java](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FootprintViews.java:225) — the bars cursor is not exclusive at epoch boundaries.** `afterMsExclusive + 1` overflows for `Long.MAX_VALUE`, and clamping `from` to `EPOCH_MAX_MS` makes `afterMs == EPOCH_MAX_MS` include the epoch-max record again. Both violate G-R7’s exclusive cursor contract. **Remediation:** return an empty page when `afterMs >= EPOCH_MAX_MS`, and avoid arithmetic before validating the range; test `EPOCH_MAX_MS` and `Long.MAX_VALUE`.
./ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND1.md:11:4. **MEDIUM — [FootprintWiringTest.java](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintWiringTest.java:54) — G-R11’s required predicate-seam test is replaced by source-text inspection.** The suite does not execute `PartitionRefresh.apply()` with a rejecting-then-accepting gate and therefore does not verify `Refresh.added()`, merged assignment, seek/caught-up effects, or both consumer flows. Other expressly enumerated behavioral pins are also absent, including malformed Spring parameter binding, real authenticated-socket fan-out through a fake sink, concurrent hello atomicity, and live retry seek behavior. The passing 1,062-test run therefore does not satisfy G-R11’s military-completeness bar. **Remediation:** introduce executable seams/fakes and add the enumerated behavioral tests instead of relying on string-position assertions.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md:7:2. **HIGH — doc:49** — G-R8 is neither numerically correct nor a real memory bound. `9,122 × 64 KiB` is about **570 MiB**, not 160 MB; accepting records up to 262,144 bytes permits roughly **2.23 GiB of raw bar bytes** before Java `String`, map-key, JSON, and object overhead. Outcomes have no oversize rejection at all, and the cited reference-session counts are observations rather than normative cardinality limits. No heap setting is visible in this worktree, so compatibility cannot be established. **Required change:** provide conservative retained-heap arithmetic against a specified deployment heap and introduce enforceable per-view byte/cardinality budgets—including outcomes—with deterministic eviction/rejection and metrics. Make deployment contingent on a verified heap margin.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md:11:4. **MEDIUM — doc:48,63–64** — Outcome cursor totality is asserted but not fully specified. A `TreeMap<String,…>` key containing an unpadded decimal timestamp is lexicographic rather than numeric over the upstream epoch-ms domain, while behavior for malformed, wrong-timeframe, or nonexistent opaque cursors is undefined. G-R11 does not explicitly require collision/tie and invalid-cursor cases. **Required change:** define a tuple comparator `(timeframe, resolvedAtBarStartMs numeric, identity)` or a fixed-width encoding; define cursor validation/error behavior and test multiple identities at one resolution time, timestamp digit-length boundaries, delimiter-bearing identities, stale/wrong-timeframe cursors, and URL/JSON round trips.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md:13:5. **MEDIUM — doc:52** — The brokerless acceptance suite does not cover every normative requirement. Missing pins include topic/default settings, complete flag-off behavior (subscriptions, lazy views, hello bytes, HTTP 404 body/status, metrics), verbatim delivery and view-before-broadcast ordering, optional absent-topic startup, complete metric labels/counting, malformed-record containment, and the configured memory bounds. Allowlist membership alone also does not prove delivery behavior. **Required change:** expand G-R11 into explicit brokerless tests for every G-R1–G-R10 clause, including per-session routing tests proving all four events reach every authenticated socket and non-allowlisted events remain dropped.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND1.md:17:7. **LOW — doc:31–34** — The upstream-record summary incorrectly says every record carries singular `timeframe`. The live grammars carry a `timeframes` object and no singular `timeframe` (`ES-FOOTPRINT-DESIGN.md:571–584`). **Required change:** say that bar and outcome records carry `timeframe`, while live records carry the fixed-key `timeframes` object.
./ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND2.md:13:4. **NOT RESOLVED — G-R11 is still not fully behavioral.** The predicate itself is now executed against a mocked consumer and correctly checks `Refresh.added()`, merged assignment, and rejecting-then-accepting admission. However, the required live retry/adoption test still reads Java source and searches for call text instead of executing `runLiveConsumerOnce` or an equivalent production orchestration seam: [FootprintSeamTest.java](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintSeamTest.java:108). Thus a refactor or control-flow change could leave the calls textually present while preventing their execution. The “both consumer flows” test also invokes the same synthetic helper twice under different names rather than exercising the two production consumer paths: [FootprintSeamTest.java](/private/tmp/oe-gw-footprint/src/test/java/app/feedgateway/FootprintSeamTest.java:87). This does not meet the Round-1 remediation’s behavioral retry/seek requirement or the stated military-completeness bar.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md:8:   G-R8’s arithmetic is reasonable only if fact (iii), uncompressed end-to-end delivery, is enforced. It is not: [`EsCvdRuntime.java:1349`](/private/tmp/oe-footprint-wire/es-cvd-service/src/main/java/com/optionsedge/processing/escvd/EsCvdRuntime.java:1349) configures serializers, acknowledgements, and idempotence but does not set `ProducerConfig.COMPRESSION_TYPE_CONFIG` to `none`. Therefore the claimed 25 MiB Kafka bound and 180 MiB total are not presently established.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md:13:   G-R8a correctly permits unknown topics and prevents unvalidated topics from initial assignment, but its described re-adoption path does not exist for the proposed setup. The gateway’s current state consumers manually `assign(...)` and use `PartitionRefresh`; that mechanism discovers topics created after startup only from the topic set captured when the refresh object is constructed. `PartitionRefresh` makes an immutable copy with `Set.copyOf(topics)` at [`FeedGatewayService.java:3605`](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/FeedGatewayService.java:3605). Because G-R8a says `addEsFootprintTopics` initially adds only validated topics, an absent/unvalidated topic is excluded from that immutable set and cannot later be discovered by the existing refresh path. No consumer restart, command queue, or other re-adoption mechanism is specified.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md:16:   The section-1 anchor remains incorrect and incomplete at [`ES-FOOTPRINT-GATEWAY-DESIGN.md:71`](/private/tmp/oe-gw-footprint/ES-FOOTPRINT-GATEWAY-DESIGN.md:71). It still says the unauthenticated CVD route “follows” the explicit-auth convention and gives the empty anchor ``GatewayController.java:``. A valid precedent is `LiquidityHistoryController`, whose explicit call is at [`LiquidityHistoryController.java:86`](/private/tmp/oe-gw-footprint/src/main/java/app/feedgateway/liquidityhistory/LiquidityHistoryController.java:86). The CVD route remains valid only for typed binding and backfill semantics.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md:20:- **HIGH — [`ES-FOOTPRINT-GATEWAY-DESIGN.md:126`](/private/tmp/oe-gw-footprint/ES-FOOTPRINT-GATEWAY-DESIGN.md:126) — upstream prerequisite is stated as present when it is absent.** The text says `EsCvdRuntime.producerProps` sets `compression.type=none`, but the supplied revision-13 implementation does not. Required change: amend and test the upstream producer before relying on fact (iii), or rewrite the design and bound for the actual producer behavior.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND6.md:22:- **HIGH — [`ES-FOOTPRINT-GATEWAY-DESIGN.md:100`](/private/tmp/oe-gw-footprint/ES-FOOTPRINT-GATEWAY-DESIGN.md:100) — late validation has no implementable consumer handoff.** Required change: specify a concrete Kafka-consumer-thread-safe mechanism, such as restarting both state consumers with rebuilt validated topic maps or queuing reconfiguration onto each owning poll thread. Define cache/live seek behavior and cache readiness during adoption, and pin an end-to-end lifecycle test: absent at startup → validated later → assigned by both consumers.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md:8:   G-R7 fixes the response-construction portion, and the HotSpot checks make the retained-layout assumptions enforceable. However, G-R8’s Kafka term remains unproven, so the 180 MiB total and derived inequalities are not established.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md:11:   G-R9 adds `consumer` to `drops_total`, precisely defines cache/live ownership and broadcast identities, and assigns one rejection reason through explicit precedence. G-R11 includes the necessary overlapping-condition assertions.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md:15:- **G-R7 streamed writer:** The stated implementation is feasible with the existing controller/service organization. The permit is acquired before snapshotting and released only after flush/failure; the coordinator lock covers reference selection only and is explicitly excluded from network writing. The arithmetic is sound: `262,144 + 65,536 + 400 < 0.32 MiB`; four requests remain below 1.3 MiB. The retained `String` objects are already charged to the view.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND4.md:20:- **G-R9:** The live/cache drop attribution and both broadcast identities now form one exact contract. The precedence tests pin busy over bad cursor, bad cursor over session mismatch, and session mismatch over a served page.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md:8:   G-R8 now correctly states the fetch mechanics: one in-flight fetch per node, partitions with buffered data excluded from subsequent fetches, oversized first batches returned whole, and `max.poll.records` pacing delivery rather than fetching. It also adds the required enforced upper check of each topic’s effective `max.message.bytes`.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md:10:   However, the resulting 25 MiB bound is still not proven. Kafka fetch and `max.message.bytes` limits apply to serialized/compressed record batches. G-R8 assumes the deserialized `String` payload is no larger than the fetched share, which does not hold when compression is enabled. It also counts only `String` payload plus 48 bytes per record, omitting keys, `ConsumerRecord` objects, collections, headers, decompression storage, and related client bookkeeping. Incidentally, the stated 4.8 KiB allowance does rely on the 100-record poll limit, despite saying the bound never uses it.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md:13:   G-R7 line 90 now gives the implementable order: Spring typed binding → handler flag check → explicit `LiquidityHistoryAuth.authenticate(...)` → permit → cursor → snapshot → streaming. The derived counting rules are consistent:
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md:23:   G-R11 line 94 covers the important overlap cases.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND5.md:33:   G-R1 requires startup to succeed when a footprint topic is absent, while G-R8 says startup reads all four topic configurations and refuses startup above the ceiling. An absent topic cannot be validated, and if it appears later with an excessive ceiling the startup-only check never protects the bound.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md:5:Round-9 disposition verified. G-R9 now fully defines both topic-validation series, including label domains, initialization, increment semantics, precedence, and flag-off behavior. G-R11(12) and G-R11(17) pin initial, failure, precedence, and successful-validation values.
./pom.xml:103:          probe, validate) a DataSource at boot — the gateway MUST start fine with NO database (§6.3).
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND9.md:7:- Source contains exactly six `new PartitionRefresh(...)` sites: lines 1739, 2087, 2125, 2201, 2303, and 2674. G-R8a and G-R11(17) consistently assign `t -> true` to the four non-state sites and `footprintGate::admit` to the two state sites.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND9.md:12:1. **MEDIUM — doc lines 124–127 — validation metrics fall outside the document’s purportedly exhaustive metrics contract.** G-R8a introduces `gateway_footprint_topic_validated{topic}` and `gateway_footprint_topic_validation_failures_total{topic,reason}`, but G-R9’s exhaustive label domains and series list omit both. G-R11(12) consequently verifies only “every G-R9 series,” while G-R11(17) does not require assertions for these metrics. **Required change:** add both series, their complete `topic` and `reason` label domains, initialization/increment semantics, and flag-off behavior to G-R9; extend G-R11 to pin their initial and failure/success values.
./src/main/java/app/feedgateway/FootprintTopicGate.java:28: * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R8a — topic validation BEFORE consumption, on the consumer's own
./src/main/java/app/feedgateway/FootprintTopicGate.java:31: * (fact (iii) of G-R8: a fetched byte is a decompressed byte). Start-up validates the topics that
./src/main/java/app/feedgateway/FootprintTopicGate.java:41:    /** G-R9 failure reasons, in precedence order: admin → unknown → ceiling → compression. */
./src/main/java/app/feedgateway/FootprintTopicGate.java:87:     * Start-up (G-R8a): validate the topics that EXIST. Throws {@link IllegalStateException} for an
./src/main/java/app/feedgateway/FootprintTopicGate.java:102:    /** The {@code PartitionRefresh} admission predicate (G-R8a): non-footprint topics always pass. */
./src/main/java/app/feedgateway/FootprintTopicGate.java:144:    // ---- G-R8 layout verification -----------------------------------------------------------------
./src/main/java/app/feedgateway/FootprintTopicGate.java:149:     * The three HotSpot flags the G-R8 retained-heap arithmetic assumes. Returns the first flag that is
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:7:G-R8 replaces the average-size assumption with byte/count budgets and gets the retained-view subtotal essentially right, but the total heap proof is still unsound because its transient-response and Kafka-batch bounds are not conservative. See new finding 1.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:11:G-R8 and §4 now require both heap headroom and container/native headroom. The current deployment has `-Xmx1536m` and a `1536Mi` container limit, so it fails `limit − Xmx ≥ 512 MiB`, exactly as revision 3 states. Enabling requires a deployment change and recorded measurements.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:15:G-R9 supplies label domains and most increment/overlap rules, but the drop series cannot unambiguously represent the cache/live semantics it defines, and simultaneous backfill rejection conditions have no precedence. Therefore G-R11 still cannot pin one exact contract for all executions. See new finding 2.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:17:G-R8 arithmetic verification:
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:80:  - G-R7’s semaphore limits served backfills to four and rejects excess requests with 503. This is a valid concurrency bound provided the permit is acquired before page snapshotting/response construction and released only after construction finishes. The text should state that lifecycle explicitly.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:91:G-R9 overlap verification against G-R3/G-R4/G-R7:
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:93:- Oversize keyed live record: `records_total` + `drops_total{oversize}`; no view admission and no broadcast. Consistent with G-R3.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:94:- Shape/stale keyed live record: `records_total` + the applicable drop + `broadcast_total`. Consistent with G-R3/G-R4.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:97:- Rollover clearing is excluded from eviction and increments rollover once per date advance. Consistent with G-R4.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:98:- Every enabled backfill call increments requests; bad cursor, mismatch, or busy increments one rejection. Individually consistent with G-R7, but precedence is undefined when multiple conditions apply.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND3.md:108:   `records_total` distinguishes `cache` and `live`, while `drops_total` does not. Yet G-R9 refers to live-path oversize drops when defining broadcasts, without specifying whether the shared drop counter increments during cache admission, live admission, or both. Separately, a request can be both busy and malformed or session-mismatched, but no validation/acquisition precedence determines its HTTP result and single rejection reason.
./scripts/footprint-conformance.py:39: 'G-R7 epoch-guard-relaxed':
./scripts/footprint-conformance.py:68:               "probed, the evidence that its behaviour is held by a test.\n")
./scripts/footprint-conformance.py:85:        probed = {r['rid'] for r in sub}
./scripts/footprint-conformance.py:87:        out.append(f"{len(ids)} requirements; {len(probed)} probed by {len(sub)} mutations. "
./scripts/footprint-conformance.py:113:               "not probe** — those are marked NOT PROBED and no claim is made about them.\n")
./scripts/footprint-reqstate.sh:2:# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md IN PLACE from the checked-in campaign record.
./scripts/footprint-reqstate.sh:13:DOC=ES-FOOTPRINT-GATEWAY-DESIGN.md
./scripts/footprint-reqstate.sh:19:    ES-FOOTPRINT-CAMPAIGN.json \
./scripts/footprint-reqstate.sh:23:    '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
./scripts/footprint-reqstate.sh:24:    '{"G-R11":"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break"}'
./scripts/footprint-reqstate.py:5:requirement the campaign did not probe says exactly that, and claims nothing else — the failure mode
./scripts/footprint-reqstate.py:13:    # lives in ANOTHER repository. Neither can be probed here, and neither is a gap — but saying
./scripts/footprint-reqstate.py:54:            # checked-in file, and filing a probe under the wrong requirement is how a table comes to
./scripts/footprint-reqstate.py:119:            state = (notes[rid] + " (not probed)") if rid in notes else "NOT PROBED"
./scripts/footprint-reqstate.py:123:            # Count PROBES, not clauses: a clause implemented at several sites is probed once per
./scripts/footprint-reqstate.py:125:            # A clause probed at several SITES, or broken several WAYS (variants), is still one
./scripts/footprint-reqstate.py:128:            state = f"{killed} of {len(ms)} probes pinned, over {groups} clause" + ("" if groups == 1 else "s")
./scripts/footprint-reqstate.py:137:    probed_here = len([r for r in ids if r in per])
./scripts/footprint-reqstate.py:138:    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rendered)} mutations "
./scripts/footprint-reqstate.py:142:               "\n\nRead the state column narrowly. \"n of m probes pinned\" says that breaking those "
./scripts/footprint-reqstate.preamble:7:verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
./scripts/footprint-reqstate.preamble:11:What this table cannot do is decide whether a quoted obligation is the clause a probe exercises.
./src/main/java/app/feedgateway/GatewaySettings.java:314:    // ---- ES Footprint (ES-FOOTPRINT-GATEWAY-DESIGN.md G-R1/G-R2/G-R7/G-R8/G-R8a) -----------------
./src/main/java/app/feedgateway/GatewaySettings.java:316:    /** G-R1: OFF by default — the four ES-only topics do not exist on every cluster. */
./src/main/java/app/feedgateway/GatewaySettings.java:321:    /** G-R2: live A1+A2 snapshot (event {@code es-footprint}), keyed symbol, change-driven + 5 s heartbeat. */
./src/main/java/app/feedgateway/GatewaySettings.java:326:    /** G-R2: live evidence snapshot (event {@code es-footprint-evidence}). */
./src/main/java/app/feedgateway/GatewaySettings.java:331:    /** G-R2: closed bars (event {@code es-footprint-bar}), keyed symbol|timeframe|barStartMs, compacted. */
./src/main/java/app/feedgateway/GatewaySettings.java:336:    /** G-R2: outcome resolutions (event {@code es-footprint-outcome}), keyed by identity, compacted. */
./src/main/java/app/feedgateway/GatewaySettings.java:341:    /** G-R8: accepted-record ceiling — MUST equal the producer's FOOTPRINT_MAX_RECORD_BYTES (one value, set together). */
./src/main/java/app/feedgateway/GatewaySettings.java:346:    /** G-R8: bars view byte budget (summed record lengths) and count budget. */
./src/main/java/app/feedgateway/GatewaySettings.java:355:    /** G-R8: outcomes view byte and count budgets. */
./src/main/java/app/feedgateway/GatewaySettings.java:364:    /** G-R7: concurrently served footprint backfill requests; beyond it the route answers 503 busy. */
./src/main/java/app/feedgateway/GatewaySettings.java:369:    /** G-R8a: a footprint topic whose effective max.message.bytes exceeds this is never consumed (broker default 1 048 588). */
./src/main/java/app/feedgateway/GatewayController.java:16:    /** ES Footprint (G-R7): explicit in-handler authentication, the LiquidityHistoryController convention (a function seam over the final auth bean). */
./src/main/java/app/feedgateway/GatewayController.java:42:    // ---- ES Footprint backfill (ES-FOOTPRINT-GATEWAY-DESIGN.md G-R7) ------------------------------
./src/main/java/app/feedgateway/GatewayController.java:44:    /** G-R7: both routes clamp the page to [1, 100]. */
./src/main/java/app/feedgateway/GatewayController.java:46:    /** G-R7/G-R8: the ONLY buffer between the page bytes and the socket; VERIFIED, not assumed (CODE round-1 #5). */
./src/main/java/app/feedgateway/GatewayController.java:106:    /** Steps (2) flag and (3) authentication; counts the request at the flag check (G-R9). */
./src/main/java/app/feedgateway/GatewayController.java:143:     * G-R7 step (7): {@code {"sessionDate":..,["sessionMismatch":true,]"<field>":[..],"nextCursor":..}}
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:11:1. **RESOLVED** — G-R4/G-R6 define one session coordinator, one lock, atomic hello snapshots, cross-view rollover, and both bootstrap orders.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:13:2. **NOT RESOLVED** — G-R8 introduces enforceable byte/count budgets and deployment gating, but its retained-heap proof and deployment contingency are unsound. See new findings 1–2.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:15:3. **RESOLVED** — G-R4 requires a present canonical ISO date, parsed before mutation, with appropriate invalid-date and boundary tests.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:19:5. **NOT RESOLVED** — G-R11 now covers the previously missing behaviors, but the “exact metrics contract” remains impossible to derive because G-R9 does not define counting semantics or complete label domains. See new finding 3.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:21:6. **RESOLVED** — D5 explicitly records the authorization decision and G-R11 tests authenticated multi-session fan-out and continued dropping of non-allowlisted events. This matches `FeedGatewayService.java:11487-11494` and `:11517-11548`.
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:27:1. **HIGH — `ES-FOOTPRINT-GATEWAY-DESIGN.md:67` — retained-heap bound is not proven.**  
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:36:2. **HIGH — `ES-FOOTPRINT-GATEWAY-DESIGN.md:67,93-96`; deployment manifest `es-feed-gateway.yaml:120-121,176-178` — deployment contingency checks heap only while `-Xmx` equals the container limit.**  
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:41:3. **MEDIUM — `ES-FOOTPRINT-GATEWAY-DESIGN.md:68-70` — metrics counting semantics remain unspecified.**  
./ES-FOOTPRINT-GATEWAY-CODEX-ROUND2.md:42:   It is unclear whether `records_total` counts consumed, accepted, or broadcast records; whether oversize records also increment it; when backfill request/rejection counters increment; and what the exhaustive `event` and `route` label values are. Consequently, G-R11’s “exact metrics contract” has no normative expected values.
./src/main/java/app/feedgateway/stockgex/StockGexConfig.java:14: * to render ("the service is down") rather than a failed readiness probe for the whole gateway.
./src/main/java/app/feedgateway/pinflow/PinFlowDataSourceConfig.java:31: *   <li><b>No health probe (§6.3):</b> there is no actuator DataSource health indicator on the classpath
./src/main/java/app/feedgateway/FootprintViews.java:20: * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R4/G-R5/G-R6/G-R7/G-R8 — the ONE session coordinator that owns
./src/main/java/app/feedgateway/FootprintViews.java:46:    /** One ATOMIC outcomes page: the cursor is the OPAQUE last view key (G-R7 grammar). */
./src/main/java/app/feedgateway/FootprintViews.java:91:    /** Closed-bar record (G-R4): key {@code timeframe|observations.barStartMs}. */
./src/main/java/app/feedgateway/FootprintViews.java:113:    /** Outcome record (G-R5): key {@code timeframe|resolvedAtBarStartMs|identity}. */
./src/main/java/app/feedgateway/FootprintViews.java:189:    /** The {@code footprint} field of the hello (G-R6): {@code {"sessionDate":..,"hwm":{..},"outcomeHwm":{..}}}. */
./src/main/java/app/feedgateway/FootprintViews.java:217:    /** Bars page (G-R7): ascending {@code barStartMs}, exclusive {@code afterMs}, inclusive {@code toMs}. */
./src/main/java/app/feedgateway/FootprintViews.java:243:     * Outcomes page (G-R7): ascending by view key within {@code tf}; {@code afterKey} is an EXCLUSIVE
./src/main/java/app/feedgateway/FootprintViews.java:275:    /** G-R7 cursor grammar: {@code tf|<19 digits>|<identity>} with {@code tf} equal to the query's. */
./src/main/java/app/feedgateway/contexttape/ContextTapeConfig.java:14: * ("the service is down") rather than a failed readiness probe for the whole gateway.
./src/main/java/app/feedgateway/FeedGatewayService.java:105:    // ---- ES Footprint (ES-FOOTPRINT-GATEWAY-DESIGN.md G-R1..G-R11) — null unless the flag is on ----
./src/main/java/app/feedgateway/FeedGatewayService.java:109:    /** G-R9 counters: records{event,consumer}, drops{event,consumer,reason}, broadcast{event}, backfill{route}, rejected{route,reason}. */
./src/main/java/app/feedgateway/FeedGatewayService.java:276:    // (emptiness probes, replay snapshots) need no lock.
./src/main/java/app/feedgateway/FeedGatewayService.java:845:    /** G-R8 layout check + G-R8a start-up validation; throws to refuse start-up. Runs before any lifecycle state moves. */
./src/main/java/app/feedgateway/FeedGatewayService.java:847:        // G-R8: the retained-heap arithmetic assumes compressed oops, compressed class pointers and
./src/main/java/app/feedgateway/FeedGatewayService.java:852:        // G-R8a start-up: existing invalid topic or a non-unknown Admin failure refuses start-up;
./src/main/java/app/feedgateway/FeedGatewayService.java:860:    /** Test seam for the G-R8a predicate seam: a refresh over the given topics with the given gate, past its first interval. */
./src/main/java/app/feedgateway/FeedGatewayService.java:888:    /** G-R9 counting: incremented at the route handler's flag check, so a 404 is never counted. */
./src/main/java/app/feedgateway/FeedGatewayService.java:893:     * G-R3/G-R4/G-R5/G-R9: ONE admission path for both consumers. Returns true when the LIVE consumer
./src/main/java/app/feedgateway/FeedGatewayService.java:909:     * The bootstrap resolution + assignment BOTH state consumers use (G-R8a): partitions of a footprint
./src/main/java/app/feedgateway/FeedGatewayService.java:921:     * shared cache window, then the levels resume, then footprint partitions forced to END (G-R8a: the
./src/main/java/app/feedgateway/FeedGatewayService.java:929:            seekFootprintToEnd(consumer, partitions);          // G-R8a (CODE round-1 #1)
./src/main/java/app/feedgateway/FeedGatewayService.java:952:     * G-R8a on the LIVE consumer: footprint partitions always start at END — the cache consumer owns
./src/main/java/app/feedgateway/FeedGatewayService.java:963:    /** The live consumer's footprint branch (G-R3/G-R9): admit, then broadcast unless oversize. Package-private for the fan-out tests. */
./src/main/java/app/feedgateway/FeedGatewayService.java:972:    /** G-R8a: the admission predicate applied to a bootstrap resolution; identity when the flag is off. */
./src/main/java/app/feedgateway/FeedGatewayService.java:984:    /** G-R9 text; the ONLY footprint line flag-off is the enabled gauge. */
./src/main/java/app/feedgateway/FeedGatewayService.java:1020:        sb.append("# HELP gateway_footprint_topic_validated Whether the footprint topic passed G-R8a validation this incarnation.\n# TYPE gateway_footprint_topic_validated gauge\n");
./src/main/java/app/feedgateway/FeedGatewayService.java:1052:        // ES Footprint preflight (G-R8/G-R8a) BEFORE any lifecycle state changes: a refusal here
./src/main/java/app/feedgateway/FeedGatewayService.java:2382:     * ES Footprint (G-R2/G-R8a): the same one-wiring-path rule as {@link #addEsCvdTopics}. ALL FOUR
./src/main/java/app/feedgateway/FeedGatewayService.java:2798:                        // ES Footprint (G-R3/G-R9): the CACHE consumer admits keyed records into the
./src/main/java/app/feedgateway/FeedGatewayService.java:3233:                        // ES Footprint (G-R3): keyed records go to the coordinator FIRST (the backfill's
./src/main/java/app/feedgateway/FeedGatewayService.java:3813:     * ({@link KafkaConsumer#listTopics(Duration)}). Replaces the per-topic probe loop AND the
./src/main/java/app/feedgateway/FeedGatewayService.java:3817:     * carried (.pace.rank, gex.oi-status, spot-band-flow mornings). The per-topic probes were also the
./src/main/java/app/feedgateway/FeedGatewayService.java:3818:     * cost center: an UNKNOWN topic blocked ~1s per probe, so absent topics serially burned the budget;
./src/main/java/app/feedgateway/FeedGatewayService.java:4018:        /** G-R8a: evaluated INSIDE apply() before merge/assign; discovered partitions whose topic fails are withheld. */
./src/main/java/app/feedgateway/FeedGatewayService.java:4091:            // G-R8a: a discovered partition of a topic the gate does not admit is WITHHELD here — not
./src/main/java/app/feedgateway/FeedGatewayService.java:11156:    /** G-R6: the cvd-hello frame is sent whenever CVD, SPX levels OR footprint is enabled. */
./src/main/java/app/feedgateway/FeedGatewayService.java:11178:            // G-R6: ONE atomic snapshot of the footprint coordinator rides the SAME hello; the field's
./src/main/java/app/feedgateway/FeedGatewayService.java:12446:            // ES Footprint (G-R3, D5): ES-global market microstructure, identical for every
./src/test/java/app/feedgateway/FootprintWiringTest.java:14: * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R1/G-R2/G-R3/G-R6/G-R9/G-R10/G-R11 — the wiring-shape pins, the
./src/test/java/app/feedgateway/FootprintWiringTest.java:39:    // ---- G-R2: one wiring path, both state consumers, all four topics unconditionally --------------
./src/test/java/app/feedgateway/FootprintWiringTest.java:90:    // ---- G-R1/G-R10: flag off is byte-identical ----------------------------------------------------
./src/test/java/app/feedgateway/FootprintWiringTest.java:104:     * G-R2: the four bindings are asserted by EXECUTING the wiring, not by reading its source. A
./src/test/java/app/feedgateway/FootprintWiringTest.java:126:    /** G-R3: the two live snapshot topics are never retained; the two keyed topics seek back a session. */
./src/test/java/app/feedgateway/FootprintWiringTest.java:139:    /** G-R6: footprint alone is enough to send the hello — the page needs the handshake either way. */
./src/test/java/app/feedgateway/FootprintWiringTest.java:148:    // ---- G-R6 hello ---------------------------------------------------------------------------------
./src/test/java/app/feedgateway/FootprintWiringTest.java:157:    // ---- G-R3/G-R9: the shared admission path and overlap identities --------------------------------
./src/test/java/app/feedgateway/FootprintWiringTest.java:192:    // ---- G-R9/G-R11 (12): the exact contract flag-on at start-up -----------------------------------
./src/test/java/app/feedgateway/PartitionExpansionIntegrationTest.java:65:    private static final String TOPIC = "partition-expansion-probe";
./src/test/java/app/feedgateway/PartitionExpansionIntegrationTest.java:189:        props.put(ConsumerConfig.GROUP_ID_CONFIG, "partition-expansion-probe");
./src/test/java/app/feedgateway/FootprintSeamTest.java:33: * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R11 — the EXECUTED seams (round-1 #4): the admission predicate
./src/test/java/app/feedgateway/FootprintSeamTest.java:55:    // ---- G-R8a: the predicate seam inside apply() ----------------------------------------------------
./src/test/java/app/feedgateway/FootprintSeamTest.java:164:    // ---- G-R3/D5: real socket fan-out through the live branch -----------------------------------------
./src/test/java/app/feedgateway/FootprintSeamTest.java:208:    // ---- G-R6: hello atomicity under concurrent admission --------------------------------------------
./src/test/java/app/feedgateway/FootprintSeamTest.java:230:    // ---- G-R7: Spring typed binding precedes the handler --------------------------------------------
./src/test/java/app/feedgateway/FootprintViewsTest.java:9:/** ES-FOOTPRINT-GATEWAY-DESIGN.md G-R4/G-R5/G-R6/G-R7/G-R8 — the coordinator, without a broker. */
./src/test/java/app/feedgateway/FootprintViewsTest.java:25:    // ---- G-R4 admission / shape ------------------------------------------------------------------
./src/test/java/app/feedgateway/FootprintViewsTest.java:68:    /** G-R5: an identity resolves exactly once upstream, so a redelivery must OVERWRITE in place. */
./src/test/java/app/feedgateway/FootprintViewsTest.java:79:    /** G-R10: no admission failure escapes the consumer loop — a null payload is a counted drop. */
./src/test/java/app/feedgateway/FootprintViewsTest.java:93:    // ---- G-R4 rollover: one coordinator, both views, strict dates ------------------------------
./src/test/java/app/feedgateway/FootprintViewsTest.java:133:    // ---- G-R6 hello --------------------------------------------------------------------------------
./src/test/java/app/feedgateway/FootprintViewsTest.java:143:    // ---- G-R7 pages ------------------------------------------------------------------------------
./src/test/java/app/feedgateway/FootprintViewsTest.java:146:     * G-R7's bars cursor is EXCLUSIVE, and the guard that keeps it safe at the top of the epoch
./src/test/java/app/feedgateway/FootprintViewsTest.java:233:    // ---- G-R8 budgets ----------------------------------------------------------------------------
./ES-FOOTPRINT-GATEWAY-DESIGN.md:7:1. G-R8a, live consumer: footprint partitions always seek END on the live consumer — on a retry (after
./ES-FOOTPRINT-GATEWAY-DESIGN.md:11:2. G-R7 bars cursor: `afterMs ≥ EPOCH_MAX_MS` returns an empty page, and the `+1` is taken only inside
./ES-FOOTPRINT-GATEWAY-DESIGN.md:13:3. G-R8/G-R8a preflight runs BEFORE any lifecycle state moves in `start()` (no `running` flag, no
./ES-FOOTPRINT-GATEWAY-DESIGN.md:15:4. G-R7 streaming: the page is written straight into the servlet response stream; there is no
./ES-FOOTPRINT-GATEWAY-DESIGN.md:23:   (per-event adoption seek plus the footprint END override). G-R11's seam tests drive these against
./ES-FOOTPRINT-GATEWAY-DESIGN.md:26:**Gate-2 CODE: APPROVED** — Codex round 3, 2026-09-07 (`ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND3.md`;
./ES-FOOTPRINT-GATEWAY-DESIGN.md:27:rounds 1–2 produced 5 + 2 findings, every one remediated). G-R1–G-R11 are IMPLEMENTED in
./ES-FOOTPRINT-GATEWAY-DESIGN.md:31:**Gate-2 DESIGN: APPROVED** — Codex round 10, 2026-09-07 (`ES-FOOTPRINT-GATEWAY-CODEX-ROUND10.md`; rounds
./ES-FOOTPRINT-GATEWAY-DESIGN.md:33:change set that implements G-R1–G-R11 carries its own CODE gate (`ES-FOOTPRINT-GATEWAY-CODE-CODEX-ROUND*.md`).
./ES-FOOTPRINT-GATEWAY-DESIGN.md:36:Upstream contract: `options-edge-processing/ES-FOOTPRINT-DESIGN.md` revision 13 (§5 grammar,
./ES-FOOTPRINT-GATEWAY-DESIGN.md:44:| 1 | G-R8a's two validation series missing from G-R9's exhaustive contract and G-R11's pins | G-R9 now lists both series with full label domains, initialization, increment semantics and flag-off behaviour; G-R11 (12) and (17) pin their initial, failure and success values |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:50:| 1 | Constructor call-site count contradictory (six total, two of them the state consumers) | G-R8a and G-R11 (17): of the SIX existing `PartitionRefresh` constructor sites, the two state consumers pass `footprintGate::admit`, the other FOUR pass `t -> true` |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:51:| 2 | §4 still called the compression prerequisite unmerged/conditional | §4 records options-edge-processing PR #757 as MERGED; the conditional wording is gone; G-R8a's runtime check stays as the continuing guard |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:57:| 1 | G-R11 (17) contradicted G-R8a (validated-only topic sets) | Rewritten: all four topics are ALWAYS in both immutable discovery sets; only admitted partitions enter assignments |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:58:| 2 | The gate seam "after `apply`, before the caller assigns" does not exist — `apply()` assigns internally | G-R8a now specifies the concrete API change: `PartitionRefresh` takes a topic-admission predicate evaluated INSIDE `apply()` before its merge/assign, so `Refresh.added()`, the seek and the caught-up bookkeeping see admitted partitions only; both state consumers pass the footprint gate |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:66:| 2 | Late validation had no consumer handoff (`PartitionRefresh` topic set is immutable) | G-R8a rewritten around the AS-IS mechanism: all four topics stay in the consumers' topic sets so `PartitionRefresh` discovers them; the VALIDATION runs on the consumer's own poll thread as a gate between discovery and `assign` — no cross-thread handoff, no restart; start-up refusal covers only topics that exist |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:73:| 1 | Kafka term assumed fetched bytes bound deserialized data (compression); per-record client overhead omitted | G-R8: the four topics are REQUIRED uncompressed end to end — the upstream producer pins `compression.type=none` (processing-repo amendment, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` — so fetched bytes ARE the decompressed bytes; per-record key/`ConsumerRecord`/collection overhead added; the 100-records-per-poll figure is used ONLY for that overhead and is now stated so |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:74:| 2 | Start-up ceiling check conflicts with optional absent topics | G-R8a: a topic is consumed only after its validation (`max.message.bytes` ceiling, `compression.type`); absent at start-up ⇒ not assigned, revalidated every 60 s, adopted on success via the consumers' existing topic-adoption path; existing-but-invalid ⇒ refuse start; Admin failures other than unknown-topic remain fatal |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:81:| 1 | Kafka transient bound unproven (`max.poll.records` does not bound fetching; `max.message.bytes` only lower-bounded upstream) | G-R8 derives the bound from the client's fetch mechanics (one in-flight fetch per node, no re-fetch of a partition with buffered data) and from an ENFORCED upper ceiling on each footprint topic's effective `max.message.bytes`, read at start-up via `describeConfigs` (refuse to start above it); total and inequalities recomputed |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:82:| 2 | Route order cited a non-existent JWT filter; Spring binding precedes the handler | G-R7 order rewritten to the AS-IS convention: Spring typed binding → in-handler flag check → explicit `LiquidityHistoryAuth.authenticate` → permit → cursor → snapshot → write; overlap tests added |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:88:| 1 | Transient (StringBuilder/encoding) and Kafka-poll bounds unproven; JVM layout assumptions unenforced | G-R7 mandates a streamed, byte-oriented response with a fixed buffer and a permit lifecycle; G-R8 derives the Kafka bound from the gateway's ENFORCED consumer settings and the oversized-first-batch rule, verifies the three HotSpot layout flags at start-up (fail-closed), and recomputes the total and both inequalities |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:89:| 2 | `drops_total` lacks the consumer dimension; route rejection precedence undefined | G-R9 adds `consumer` to `drops_total` and defines `broadcast_total` from live-path counts only; G-R7 fixes the route processing order so exactly one rejection reason applies; G-R11 tests overlapping conditions |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:95:| 1 | Retained-heap bound unproven (average-based) | G-R8 rewritten with a per-entry overhead bound from the count limits and the payload bound from the byte limits — no average anywhere; transient backfill memory bounded by a concurrency limit |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:96:| 2 | Contingency ignores native memory (`-Xmx` = container limit today) | G-R8/§4: the container limit must exceed `-Xmx` by a justified native margin AND a measured working-set peak must leave the projected footprint headroom; both recorded in the deploy PR |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:97:| 3 | Metrics semantics/labels undefined | G-R9 defines every series' increment rule, overlap rule and exhaustive label domain; G-R11 (12) pins expected values |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:103:| 1 | Two views, one hello `sessionDate`; cross-topic bootstrap order undefined | ONE session coordinator owns BOTH views under ONE lock (G-R4); the hello is an atomic snapshot under that lock (G-R6); cross-topic ordering tests (G-R11) |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:104:| 2 | G-R8 arithmetic wrong; outcomes unbounded; heap unknown | G-R8 rewritten: byte AND count budgets per view with deterministic eviction, oversize rejection for BOTH record classes, retained-heap arithmetic against the es4 heap (`-Xmx1536m`, container limit 1536Mi), deployment contingent on measured headroom |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:105:| 3 | Rollover accepts any non-empty string | `sessionDate` must be a strict canonical `YYYY-MM-DD` parsed with `LocalDate` (ISO_LOCAL_DATE, no lenient forms) BEFORE any mutation; invalid/missing = shape drop (G-R4) |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:106:| 4 | Outcome cursor not total over epoch-ms; invalid cursors undefined | Fixed-width key `tf\|%019d\|identity` so string order = `(tf, resolvedAtBarStartMs numeric, identity)`; cursor grammar, validation (HTTP 400) and tie/boundary tests (G-R5, G-R7, G-R11) |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:107:| 5 | Acceptance suite incomplete | G-R11 enumerates a brokerless test per clause of G-R1–G-R10, including per-session fan-out |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:108:| 6 | Allowlisting = global fan-out; authorization consequence unrecorded | D5 records the decision: footprint records are ES-global market data identical for every authenticated socket, the same class as `es-cvd`; there is no per-user or per-selection content; fan-out/isolation tests (G-R11) |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:137:Upstream record facts the gateway relies on (ES-FOOTPRINT-DESIGN.md §5): every record carries
./ES-FOOTPRINT-GATEWAY-DESIGN.md:151:| G-R1 | **Flag.** `GATEWAY_ES_FOOTPRINT_ENABLED` (default `false`; `GatewaySettings.esFootprintEnabled()`). Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`. Flag on with a topic absent on the cluster: the gateway starts (the topics are OPTIONAL like every other DATABENTO JSON topic) and the page reports no data. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:152:| G-R2 | **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`). ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:153:| G-R3 | **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`). `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt). All four events are added to the per-session standalone allowlist (D5). |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:154:| G-R4 | **Session coordinator and the bar view.** ONE `FootprintViews` object owns `footprintSessionDate` (a `LocalDate`, null before the first accepted record), the bar view `bars: TreeMap<tf\|%019d(barStartMs), json>` and the outcome view (G-R5), all under ONE lock. Admission of any bar/outcome record: parse JSON; `sessionDate` must be present and parse with `LocalDate.parse(s, ISO_LOCAL_DATE)` AND round-trip to the same string (strict canonical `YYYY-MM-DD`); `timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates). Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances; OLDER ⇒ `stale_session` drop; EQUAL (or first) ⇒ upsert (last write per key wins). Because one coordinator rolls both views, a bar from session N+1 arriving before any outcome of N+1 clears N's outcomes with N's bars, and an outcome of N arriving after that is dropped — whichever topic the cache consumer drains first. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:155:| G-R5 | **Outcome view.** `outcomes: TreeMap<tf\|%019d(resolvedAtBarStartMs)\|identity, json>` in the SAME coordinator. The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain; `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component). Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:156:| G-R6 | **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held). It rides the SAME frame so the page has one handshake; `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled. Flag off, the field is absent (its ABSENCE tells the page the gateway has no footprint). |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:157:| G-R7 | **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's). `GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], outcomes:[…], nextCursor}` ascending by view key within `tf`, `toMs` inclusive on `resolvedAtBarStartMs`, `after` = an EXCLUSIVE lower bound given as a previous page's `nextCursor` (the last key string). Cursor grammar: `tf|<19 digits>|<identity>` where `tf` equals the query `tf`; an `after` that is non-empty and fails the grammar ⇒ HTTP 400 `{"error":"bad cursor"}`; a well-formed cursor that no longer exists is legal (string lower bound). `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from. Same JWT gate as every `/api` route. **Processing order (exactly one outcome per request, in the AS-IS convention):** (1) Spring typed binding of `toMs`/`limit`/`afterMs` ⇒ 400 on malformed input BEFORE the handler runs (so a malformed request is never counted and never sees the flag or auth); (2) in the handler, flag off ⇒ 404 `{"enabled":false}`; (3) explicit `LiquidityHistoryAuth.authenticate(...)` exactly as `LiquidityHistoryController` does (`:86`) ⇒ its 401/403 on failure; (4) permit: `tryAcquire` on a `Semaphore(GATEWAY_ES_FOOTPRINT_BACKFILL_CONCURRENCY)` (default 4) ⇒ 503 `Retry-After: 1` (`busy`) when none is free; (5) cursor grammar (outcomes route) ⇒ 400 `{"error":"bad cursor"}`; (6) page snapshot under the coordinator lock — the session check (`sessionMismatch`, HTTP 200) and the selection of up to `limit` record REFERENCES into a `List<String>` (no copies) plus `sessionDate` and `nextCursor`; (7) the response is STREAMED: the controller writes to `HttpServletResponse.getOutputStream()` through ONE fixed 64 KiB buffer, the envelope pieces and each record as `record.getBytes(US_ASCII)` one at a time (records are ASCII by F-E8), status 200 set before the first byte; the permit is released in `finally` after the stream is flushed (or the write failed). The lock is held only during (6), never during (7). Transient memory per request is therefore ≤ one record's bytes (≤ 262 144 B) + the 64 KiB buffer + the reference list (≤ 100 × 4 B) < 0.32 MiB (G-R8). |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:158:| G-R8 | **Bounds and heap (proven from the limits, never from averages).** Accepted-record ceiling `GATEWAY_ES_FOOTPRINT_MAX_RECORD_BYTES` (default 262 144 — MUST equal the producer's `FOOTPRINT_MAX_RECORD_BYTES`; the deploy PR sets both from one value): a bar OR outcome record whose UTF-8 length exceeds it is dropped entirely (`oversize`). View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key. **Retained-heap bound** (HotSpot, compressed oops and compressed class pointers — true for any heap < 32 GiB, as here): a JSON value is ASCII (F-E8 alphabet) so it is a Latin-1 compact `String`: 24 B object + 16 B array header + payload, aligned to 8 ⇒ ≤ payload + 48 B; a key `String` ≤ 160 B payload ⇒ ≤ 208 B; a `TreeMap.Entry` = 40 B; per entry overhead ≤ 296 B, so overhead ≤ (12 000 + 20 000) × 296 B = 9.04 MiB; payload ≤ 128 + 16 = 144 MiB; the coordinator's accounting (two `long` counters, one `LocalDate`, per-timeframe eviction cursors) < 1 KiB. Views total ≤ 153.1 MiB. **Transient** per in-flight backfill (G-R7 step 7): < 0.32 MiB, at most 4 concurrent ⇒ < 1.3 MiB; no `StringBuilder`, no response `String`, no encoder buffer beyond the fixed 64 KiB. **Kafka transient**, from the client's fetch mechanics and THREE enforced facts. Mechanics (Kafka consumer `Fetcher`, unchanged since KIP-74): the consumer keeps at most ONE in-flight fetch request per broker node, a fetch request for a node includes only assigned partitions that have NO buffered (completed, unconsumed) data, each partition's share of a response is ≤ `max.partition.fetch.bytes` except that the FIRST batch of a partition is returned whole when it is larger, and `max.poll.records` only paces how many buffered records a `poll()` hands out. Enforced facts: (i) `max.partition.fetch.bytes` = 512 KiB (§1); (ii) `M` = each footprint topic's effective `max.message.bytes` ≤ `GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588, the broker default), validated per G-R8a; (iii) the four topics are UNCOMPRESSED end to end — the upstream producer sets `compression.type=none` explicitly (options-edge-processing amendment: `EsCvdRuntime.producerProps`, pinned by test) and the gateway validates each topic's effective `compression.type ∈ {producer, uncompressed}` per G-R8a — so a fetched share IS its decompressed size and no decompression buffer exists. Per consumer, each of the four single-partition footprint topics contributes at most: one in-flight share (≤ max(512 KiB, M) ≤ 1.0 MiB) + one buffered completed share (≤ 1.0 MiB) + the deserialized value `String`s of the records a `poll()` returned from that share (≤ the share's bytes + 48 B per record) ⇒ ≤ 3.0 MiB per topic + per-record overhead, ≤ 12.0 MiB per consumer + per-record overhead. Per-record client overhead for the records of ONE poll — here, and only here, bounded by `max.poll.records` = 100 (§1): key `String` ≤ 208 B, `ConsumerRecord` object ≤ 96 B (header, 7 references, two longs, two ints), empty `RecordHeaders` ≤ 32 B, `ConsumerRecords` map/list share ≤ 64 B ⇒ ≤ 400 B × 100 = 40 KiB. Per consumer ≤ 12.04 MiB; TWO consumers touch footprint topics (state cache, state live) ⇒ ≤ 24.1 MiB, rounded to 25 MiB. Non-footprint partitions in the same fetch responses are not attributable to this feature and are already part of the measured `H_peak`. **Layout assumptions are verified at start-up** when the flag is on: `HotSpotDiagnosticMXBean.getVMOption` for `UseCompressedOops`, `UseCompressedClassPointers` and `CompactStrings` must all be `true`, else the gateway refuses to start naming the flag (the arithmetic above is void otherwise); pinned by a test of the checker over a fake option map. Topic-side facts (ii) and (iii) are verified by G-R8a. **Footprint heap contribution ≤ 153.1 + 1.3 + 25 = 179.4 MiB, rounded to 180 MiB**, independent of record size distribution. **Deployment contingency (§4):** the flag may be enabled only when the deploy PR records, from a full ES session with CVD on and footprint off: (a) heap: `jvm_memory_bytes_used{area="heap"}` peak `H_peak` with `-Xmx − H_peak ≥ 180 MiB + 128 MiB = 308 MiB` (GC headroom); (b) container: `container_memory_working_set_bytes` peak `W_peak` with `limit − W_peak ≥ 180 MiB + 256 MiB = 436 MiB`, AND `limit − Xmx ≥ 512 MiB` (metaspace, code cache, thread stacks, socket/direct buffers, JVM overhead — the current es4 manifest has `limit = Xmx = 1536 MiB`, which violates this and MUST change in the same PR: e.g. `-Xmx1536m` with `limit 2560Mi`, or a measured alternative that satisfies both inequalities). Reference-session cardinalities (9 122 bars / 15 339 outcomes) are observations, not limits; the budgets above are the limits and the tests pin them. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:159:| G-R8a | **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`. **Start-up** (`start()`, before any consumer launches): validate the topics that EXIST; an existing INVALID topic ⇒ refuse to start naming topic and value (fail-closed); an absent topic (`UnknownTopicOrPartitionException` for that resource) ⇒ deferred; any other Admin failure ⇒ refuse to start. **The seam (an API change to `PartitionRefresh`, the AS-IS contract otherwise kept):** `PartitionRefresh` gains a constructor parameter `Predicate<String> topicAdmit`: of the SIX existing constructor call sites (`:1739` selection, `:2087` hpsf-cache, `:2125` hpsf-live, `:2201` alerts, `:2303` state cache, `:2674` state live), the two STATE consumers pass `footprintGate::admit` and the other FOUR pass `t -> true`. Inside `apply()`, AFTER `partitionsFor` discovers new partitions and BEFORE `mergedAssignment`/`consumer.assign(...)` (`FeedGatewayService.java:3665-3671`), discovered partitions whose topic fails `topicAdmit` are WITHHELD: they are not merged, not assigned, not in `Refresh.added()`, hence never sought and never in the caller's caught-up bookkeeping — identical to a topic that does not exist yet; they are re-discovered (and re-evaluated) at the next refresh interval. The same predicate is applied to the bootstrap `partitionsFor` result before the initial `assign` in both state consumers. `addEsFootprintTopics` adds ALL FOUR topics to both consumers' `topicEvents` unconditionally, so the immutable `Set.copyOf(topics)` always contains them and discovery works whenever a topic appears. **`FootprintTopicGate.admit(topic)`** runs on the calling poll thread: a non-footprint topic ⇒ true; a footprint topic already marked VALID ⇒ true; otherwise it validates NOW (bounded timeout): VALID ⇒ mark and true; unknown topic, timeout or other Admin failure ⇒ false this cycle (counted, logged once per state change, retried at the next refresh — the absorbed-refresh-failure discipline); existing-but-invalid ⇒ false (counted `ceiling`/`compression`, logged once per state change). Per-topic state in a `ConcurrentHashMap` shared by both consumers (each evaluates on its own thread; a duplicate validation is harmless); once VALID a topic stays VALID for the incarnation (a topic config change after admission requires a gateway restart — §4 operational rule). Adoption of a late-admitted topic then follows the existing caller behaviour for `Refresh.added()`: the cache consumer seeks its window (a compacted keyed topic replays its retained keys into the coordinator, as `es-cvd-bar` does), the live consumer seeks END. The two validation series are defined in G-R9. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:160:| G-R9 | **Metrics (Prometheus text on the gateway's `/metrics`; every rule below is normative and pinned by G-R11 (12)).** Label domains: `event ∈ {es-footprint, es-footprint-evidence, es-footprint-bar, es-footprint-outcome}`, `consumer ∈ {cache, live}`, `view ∈ {bars, outcomes}`, `route ∈ {bars, outcomes}`, `reason` as listed per series. Series: `gateway_footprint_enabled` (gauge 0/1; the ONLY series flag-off). `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too). `gateway_footprint_drops_total{event,consumer,reason}` with `reason ∈ {oversize, shape, stale_session}`, keyed events only (`es-footprint-bar`, `es-footprint-outcome`), incremented by WHICHEVER consumer performed the admission (both consumers admit into the ONE coordinator: the cache consumer during bootstrap, the live consumer afterwards); exactly ONE reason per non-admitted record, evaluated in the order oversize → shape → stale_session; an admitted record increments none. `gateway_footprint_broadcast_total{event}` — frames handed to `broadcast` by the LIVE consumer: for live events = `records_total{event,live}`; for keyed events = `records_total{event,live} − drops_total{event,live,oversize}` (shape/stale records ARE broadcast; the cache consumer never broadcasts, so its drops never enter this identity). `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance). Gauges `gateway_footprint_bars_in_view`, `gateway_footprint_outcomes_in_view`, `gateway_footprint_view_bytes{view}` — the coordinator's current counts/bytes. `gateway_footprint_backfill_requests_total{route}` — every request reaching the route handler while the flag is on (rejected ones included; flag-off requests hit the 404 and, the flag being off, no footprint series exists). `gateway_footprint_backfill_rejected_total{route,reason}` with `reason ∈ {busy, bad_cursor, session_mismatch}` — exactly one per rejected request, decided by the G-R7 processing order (a request that is both busy and malformed is `busy`; one with a bad cursor AND a stale session is `bad_cursor`; `busy` = HTTP 503; `bad_cursor` = HTTP 400; `session_mismatch` = HTTP 200 with `sessionMismatch:true`); a served page increments none; `requests_total` is incremented at step (2) when the flag is on (so 404s are never counted, binding failures never reach the handler, and an authentication failure IS counted as a request but is not a `rejected` reason — it is the auth layer's own outcome, visible in its own metrics). `gateway_footprint_topic_validated{topic}` — gauge 0/1 per footprint topic (`topic` ∈ the four configured topic names), 1 from the moment `FootprintTopicGate` marks the topic VALID (start-up validation or a later admission), never back to 0 within an incarnation. `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal). Both series are exported with every label value from start-up (gauges at 0, counters at 0) and, like every other footprint series, do not exist flag-off. Every series is exported with every label value it can take, from start-up, at 0 (so absence is never ambiguous). |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:161:| G-R10 | **Non-interference.** No CVD path changes when the flag is on except the one added hello field; flag off is byte-identical CVD behaviour (pinned: the hello JSON without footprint equals today's). A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:162:| G-R11 | **Tests** (JUnit, no broker — the view/coordinator is a plain object like `cvdBars`; the routes are exercised through the controller with a fake service where needed): (1) one-wiring-path source pin for the four topics; (2) flag off: `addEsFootprintTopics` adds nothing, no view object, hello bytes equal today's, both routes 404 `{"enabled":false}`, metrics = the single `enabled 0` line; (3) settings defaults for the flag and four topics; (4) admission: shape drops for missing/invalid/non-canonical `sessionDate` (`z`, `2026-8-14`, `20260814`, `2026-02-30`), missing `timeframe`, unknown timeframe, missing/non-integer/out-of-domain keys, non-canonical identity; (5) rollover: newer date clears BOTH views, older dropped, equal upserts, year/month boundaries (`2026-12-31` → `2027-01-01`), out-of-order bootstrap (bar of N+1 before outcomes of N+1; outcome of N after N+1) in both topic orders; (6) last-write-per-key for both views; (7) hello: atomic snapshot, empty before records, HWM per timeframe for both maps; (8) bars page: ascending, exclusive cursor, inclusive bound, clamp, `sessionMismatch`, atomic; (9) outcomes page: several identities at ONE `resolvedAtBarStartMs` in identity order, timestamps of different digit lengths ordered numerically, identities containing `|`, wrong-timeframe/malformed cursor ⇒ 400, nonexistent cursor legal, URL round trip; (10) oversize drop for both classes, byte and count eviction oldest-first round-robin, eviction metrics; (11) delivery: view-before-broadcast ordering and verbatim bytes through a fake sink, per-session (auth) routing fan-out of all four events to EVERY authenticated socket and a non-allowlisted event still dropped; (12) exact metrics contract: flag off ⇒ exactly `gateway_footprint_enabled 0`; flag on ⇒ every G-R9 series with every label value at 0 at start-up (the four `topic_validated` gauges at 0 until validated, the sixteen `topic_validation_failures_total` cells at 0), then expected values after a scripted sequence (one oversize bar, one shape-dropped outcome, one stale-session bar, one admitted bar, one eviction, one bad cursor, one session mismatch, one busy rejection) with the overlap rules asserted (a stale bar on the live path counts in records_total{live}, drops_total{live,stale_session} and broadcast_total; an oversize bar on the cache path counts in records_total{cache} and drops_total{cache,oversize} only and does not touch broadcast_total); overlapping route conditions: busy+bad cursor ⇒ 503 and `busy` only; bad cursor+stale session ⇒ 400 and `bad_cursor` only; stale session alone ⇒ 200 mismatch; (13) the G-R7 streamed writer: permit acquired before the snapshot and released after the flush even when the client disconnects mid-write; (14) the start-up layout check refuses each of the three flags being false; (15) the start-up `max.message.bytes` ceiling check refuses a topic one byte above the ceiling and accepts one at it (fake describeConfigs map); (16) route overlap under the AS-IS order: flag off + auth failure ⇒ 404; flag on + auth failure + bad cursor ⇒ the auth outcome (no `rejected` reason, `requests_total` +1); malformed typed parameter with flag off ⇒ 400 and no counter; (17) G-R8a: over a fake describeConfigs — accept at the ceiling, refuse one byte above, refuse `gzip`/`lz4`/`snappy`/`zstd`, accept `producer` and `uncompressed`; start-up: existing invalid ⇒ refused, absent ⇒ proceeds, other Admin failure ⇒ refused; `addEsFootprintTopics` puts ALL FOUR topics in BOTH state consumers' topic maps unconditionally (source pin, and the constructed `PartitionRefresh` topic sets contain them); the predicate seam: with a fake `partitionsFor`, a `PartitionRefresh` built with a gate that rejects one topic returns a `Refresh` whose `added()` excludes that topic's partitions and whose merged assignment excludes them, and admits them on the next `apply()` once the gate returns true — for both consumer flows; the bootstrap filter likewise; the four non-state constructor call sites pass the always-true predicate and the two state sites pass the gate (source pin); metrics: after the fake-describeConfigs cases above, `topic_validated{topic}` is 1 exactly for the topics that became VALID and `topic_validation_failures_total{topic,reason}` equals the attempt counts per reason (one `unknown` per refresh while absent, one `admin` per timeout, one `ceiling`/`compression` for the invalid cases, the precedence case counting `ceiling` only), and a topic validated after k failed attempts shows k failures and a gauge of 1. |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:174:verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
./ES-FOOTPRINT-GATEWAY-DESIGN.md:178:What this table cannot do is decide whether a quoted obligation is the clause a probe exercises.
./ES-FOOTPRINT-GATEWAY-DESIGN.md:185:| G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:186:| G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:187:| G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:188:| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:189:| G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:190:| G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:191:| G-R7 | 4 of 5 probes pinned, over 4 clauses (1 survived) | 2 | Backfill routes |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:192:| G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:193:| G-R9 | 3 of 3 probes pinned, over 3 clauses | 2 | Metrics |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:194:| G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:195:| G-R11 | TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break (not probed) | 2 | Tests |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:196:| G-R8a | 2 of 2 probes pinned, over 2 clauses | 2 | Deployment contingency |
./ES-FOOTPRINT-GATEWAY-DESIGN.md:198:12 requirements; 11 probed by 34 mutations (33 killed, 1 surviving).
./ES-FOOTPRINT-GATEWAY-DESIGN.md:200:Read the state column narrowly. "n of m probes pinned" says that breaking those clauses in the production source made a NAMED test fail an ASSERTION (or propagate a throw the spec declared and the run matched) — it does NOT say the requirement as a whole is held, because a requirement usually has more clauses than this campaign broke. "NOT PROBED" means this campaign did not test it and claims nothing either way; where a note appears beside it, that note is editorial and is not a campaign result. Evidence, per mutation — the patch, the file, line and enclosing declaration, the command, the exit code, the verbatim failure lines and a SHA-256 of the run output — is in the campaign record beside this document, and this table refuses to render a "pinned" cell for any record that does not carry it.
./ES-FOOTPRINT-GATEWAY-DESIGN.md:227:test and records ES-FOOTPRINT-DESIGN.md amendment 12; fact (iii) of G-R8 therefore holds by
./ES-FOOTPRINT-GATEWAY-DESIGN.md:228:contract on the producer side, and G-R8a's `compression.type` check on each topic remains the
./ES-FOOTPRINT-GATEWAY-DESIGN.md:231:restart (G-R8a caches VALID per incarnation).
./ES-FOOTPRINT-GATEWAY-DESIGN.md:238:(Prometheus queries quoted in the PR), (2) prove both G-R8 inequalities, and (3) set the container
./ES-FOOTPRINT-GATEWAY-DESIGN.md:244:Check the round-9 disposition (the two validation series in G-R9's contract and G-R11's pins);
./src/test/java/app/feedgateway/FootprintBackfillControllerTest.java:10:/** ES-FOOTPRINT-GATEWAY-DESIGN.md G-R7/G-R9/G-R11 (8)(9)(13)(16) — the two backfill routes, without a server. */
./src/test/java/app/feedgateway/FootprintBackfillControllerTest.java:130:        // G-R8/CODE round-1 #5: the ONLY buffer between the page bytes and the socket is the
./src/test/java/app/feedgateway/FootprintTopicGateTest.java:11:/** ES-FOOTPRINT-GATEWAY-DESIGN.md G-R8/G-R8a — layout check and topic validation over a fake describeConfigs. */
./src/test/java/app/feedgateway/FootprintTopicGateTest.java:115:                "the JVM running this suite satisfies the G-R8 layout assumptions");

2026-09-08T22:21:50.420665Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "git status --short 2>/dev/null; git diff --name-status 9128e372a56912777980b1cf5948384d4fe7380d..HEAD 2>/dev/null; git log -5 --oneline 2>/dev/null; jq 'length' ES-FOOTPRINT-CAMPAIGN.json; jq -r 'to_entries[] | [.key,.value.requirement,.value.status,.value.clause] | @tsv' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 6102ms:
M	ES-FOOTPRINT-CAMPAIGN.json
M	ES-FOOTPRINT-GATEWAY-DESIGN.md
M	scripts/footprint-reqstate.preamble
M	scripts/footprint-reqstate.py
da0a3a4 fix(footprint conformance): give each eviction probe back its own clause
689a8da fix(footprint conformance): file the epoch probes where their clauses live, and quote those clauses
5817c12 fix(footprint conformance): file each probe under the requirement that states its clause
9128e37 wip: round-4 folds
0ad2f33 fix(footprint conformance): put the ordering clause under the requirement that states it
34
G-R4 newer-date-rolls-both	G-R4	KILLED	a NEWER date rolls BOTH views
G-R9.3 a-rollover-is-counted	G-R9	KILLED	rollover clears are counted in gateway_footprint_rollovers_total, one per date advance
G-R8 evict-oldest	G-R8	KILLED	eviction takes the OLDEST key of the timeframe it is evicting from
G-R8 evict-rotates	G-R8	KILLED	eviction rotates round-robin across the timeframes rather than draining one
G-R8 byte-budget-decremented	G-R8	KILLED	evicting a record decrements the view byte total by that record length
G-R8 both-budgets-enforced	G-R8	KILLED	both the count AND byte budgets are enforced
G-R8 evictions-counted	G-R8	KILLED	every eviction is counted in the view eviction counter
G-R8a ceiling-strict	G-R8a	KILLED	a footprint topic is VALID iff its effective max.message.bytes is at or below the ceiling
G-R8a codec-allowlist	G-R8a	KILLED	only producer/uncompressed are accepted
G-R9 ceiling-precedence	G-R9	KILLED	ceiling takes precedence over compression
G-R7 to-is-inclusive	G-R7	KILLED	toMs is INCLUSIVE
G-R7 limit-clamped-low	G-R7	KILLED	the forwarded limit is clamped to at least 1
G-R7 limit-clamped-high	G-R7	KILLED	the forwarded limit is clamped to the ceiling
G-R1.1 the-flag-gates-every-footprint-object	G-R1	KILLED	Flag off: the views are never instantiated
G-R2.1 one-wiring-method-binds-all-four	G-R2	KILLED	ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers
G-R2.2 the-evidence-binding	G-R2	KILLED	KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)
G-R3.1 an-oversize-record-is-dropped-not-broadcast	G-R3	KILLED	a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped
G-R3.2 a-rejected-record-is-still-broadcast	G-R3	KILLED	a record the view REJECTS for shape or session is still broadcast
G-R3.3 live-snapshots-are-never-retained	G-R3	KILLED	never cached, never retained
G-R5.1 the-outcome-key-orders-numerically	G-R5	KILLED	the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)
G-R5.2 the-identity-is-the-last-component	G-R5	KILLED	identity CONTAINS |, which is why it is the LAST component
G-R5.3 a-foreign-identity-is-refused	G-R5	KILLED	a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)
G-R5.4 last-write-per-key-wins	G-R5	KILLED	Last write per full key wins; a redelivery carries the same key and overwrites in place
G-R6.1 the-hello-carries-the-footprint-field	G-R6	KILLED	the existing cvd-hello frame gains ONE field footprint
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on	G-R6	KILLED	cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled
G-R6.3 the-hello-carries-the-outcome-high-water-mark	G-R6	KILLED	outcomeHwm:{tf: maxResolvedAtBarStartMs}
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop	G-R10	KILLED	a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw
G-R10.2 every-admission-failure-is-a-counted-drop	G-R10	KILLED	every admission failure is a counted drop
G-R9.2 every-record-is-counted-before-admission	G-R9	KILLED	records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too
G-R4 older-date-dropped	G-R4	KILLED	an OLDER session date is dropped
G-R4 equal-date-upserts	G-R4	KILLED	an EQUAL date upserts without rolling
G-R4 key-fields-must-be-in-the-epoch-domain	G-R4	KILLED	an out-of-domain timestamp is refused
G-R7 the-exclusive-cursor-at-the-domain-edge site1-removed	G-R7	KILLED	the afterMs lower bound stays exclusive at the top of the epoch domain
G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed	G-R7	SURVIVED	the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way

2026-09-08T22:22:00.698847Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
The change spans four files and 34 mutation records. I’ve confirmed the latest commits are specifically the round-6 attribution fixes; I’m now running the repository’s own conformance checks and an independent cross-check of each record against the normative requirement text and summary table.
exec
/bin/bash -lc "sed -n '1,280p' scripts/footprint-conformance.py; sed -n '145,215p' ES-FOOTPRINT-GATEWAY-DESIGN.md; sed -n '1,260p' scripts/footprint-reqstate.py" in /private/tmp/oe-conf-gw
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

2026-09-08T22:22:06.132840Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "scripts/footprint-reqstate.sh --check; jq -r 'to_entries[] | \"KEY: \\(.key)\\nREQ: \\(.value.requirement)\\nQUOTE: \\(.value.documentText)\\nCLAUSE: \\(.value.clause)\\nSTATUS: \\(.value.status)\\nASSERT: \\((.value.assertionFailures // [])|join(\", \"))\\nFAIL: \\((.value.evidence.failureLines // [])|join(\" || \"))\\n\"' ES-FOOTPRINT-CAMPAIGN.json" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
mktemp: mkdtemp failed on /var/folders/t3/_t_9ln7d49g_yvjqnyr_rdj40000gn/T/tmp.cGWpzEdWAx: Operation not permitted
KEY: G-R4 newer-date-rolls-both
REQ: G-R4
QUOTE: Rollover is MONOTONIC on the parsed date: NEWER than current ⇒ BOTH views are cleared and the date advances;
CLAUSE: a NEWER date rolls BOTH views
STATUS: KILLED
ASSERT: FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>

KEY: G-R9.3 a-rollover-is-counted
REQ: G-R9
QUOTE: `gateway_footprint_evictions_total{view}` — records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).
CLAUSE: rollover clears are counted in gateway_footprint_rollovers_total, one per date advance
STATUS: KILLED
ASSERT: FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>

KEY: G-R8 evict-oldest
REQ: G-R8
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: eviction takes the OLDEST key of the timeframe it is evicting from
STATUS: KILLED
ASSERT: FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact, FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>

KEY: G-R8 evict-rotates
REQ: G-R8
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: eviction rotates round-robin across the timeframes rather than draining one
STATUS: KILLED
ASSERT: FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:245 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>

KEY: G-R8 byte-budget-decremented
REQ: G-R8
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: evicting a record decrements the view byte total by that record length
STATUS: KILLED
ASSERT: FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <3>

KEY: G-R8 both-budgets-enforced
REQ: G-R8
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: both the count AND byte budgets are enforced
STATUS: KILLED
ASSERT: FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <0>

KEY: G-R8 evictions-counted
REQ: G-R8
QUOTE: View budgets, enforced under the lock at every admission: bars `B_bytes` ≤ `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` ≤ 12 000; outcomes `O_bytes` ≤ 16 MiB and `O_count` ≤ 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.
CLAUSE: every eviction is counted in the view eviction counter
STATUS: KILLED
ASSERT: FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact, FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:260 expected: <1> but was: <0> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>

KEY: G-R8a ceiling-strict
REQ: G-R8a
QUOTE: **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
CLAUSE: a footprint topic is VALID iff its effective max.message.bytes is at or below the ceiling
STATUS: KILLED
ASSERT: FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts, FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows, FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne, FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.005 s <<< ERROR! || [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.002 s <<< ERROR! || [ERROR] app.feedgateway.FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts:68 the validated topic is added; the unvalidated one is WITHHELD from added() ==> expected: <[futures.footprint.bars-0]> but was: <[]> || [ERROR]   FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows:106 expected: <[futures.cvd.bars-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd.bars-0]> || [ERROR]   FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne:99 expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]> || [ERROR]   FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears:69 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.outcomes: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588)] || [ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:55 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 > 1048588)] || [ERROR]   FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused:38 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.outcomes: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588  || [ERROR]   FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp:225 » IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 > 1048588)]

KEY: G-R8a codec-allowlist
REQ: G-R8a
QUOTE: **Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes ≤ GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type ∈ {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.
CLAUSE: only producer/uncompressed are accepted
STATUS: KILLED
ASSERT: FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted
FAIL: [ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.015 s <<< FAILURE! || [ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:51 gzip ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.

KEY: G-R9 ceiling-precedence
REQ: G-R9
QUOTE: `gateway_footprint_topic_validation_failures_total{topic,reason}` — `reason ∈ {ceiling, compression, admin, unknown}`; incremented once per validation ATTEMPT that does not yield VALID: `ceiling` (exists, `max.message.bytes` above the ceiling), `compression` (exists, codec policy), `admin` (describeConfigs failed for a reason other than unknown topic — timeout included), `unknown` (topic does not exist yet); exactly one reason per failed attempt, decided in the order admin → unknown → ceiling → compression (a topic that is both over the ceiling and compressed counts `ceiling`); the start-up attempt counts too (an existing invalid topic increments `ceiling`/`compression` once before the refusal).
CLAUSE: ceiling takes precedence over compression
STATUS: KILLED
ASSERT: FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt
FAIL: [ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR]   FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt:62 expected: <1> but was: <0>

KEY: G-R7 to-is-inclusive
REQ: G-R7
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: toMs is INCLUSIVE
STATUS: KILLED
ASSERT: FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit, FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim, FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2> || [ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false> || [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:200 expected: <3> but was: <2>

KEY: G-R7 limit-clamped-low
REQ: G-R7
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: the forwarded limit is clamped to at least 1
STATUS: KILLED
ASSERT: FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit
FAIL: [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>

KEY: G-R7 limit-clamped-high
REQ: G-R7
QUOTE: `limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is ≤ 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.
CLAUSE: the forwarded limit is clamped to the ceiling
STATUS: KILLED
ASSERT: FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit
FAIL: [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>

KEY: G-R1.1 the-flag-gates-every-footprint-object
REQ: G-R1
QUOTE: Flag off: no footprint topic is subscribed by any consumer, the views are never instantiated, the hello carries no `footprint` field (byte-identical to today's hello), both backfill routes answer HTTP 404 with body `{"enabled":false}`, and the ONLY footprint metric exported is `gateway_footprint_enabled 0`.
CLAUSE: Flag off: the views are never instantiated
STATUS: KILLED
ASSERT: FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing, FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose, FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions, FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge, FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled, FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.348 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.308 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401> || [ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123 || [ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]> || [ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:123 flag off adds nothing ==> expected: <true> but was: <false> || [ERROR]   FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns:244 » Servlet Request processing failed: java.lang.NullPointerException: Cannot invoke "app.feedgateway.liquidityhistory.LiquidityHistoryAuth$Result.status()" because "auth" is null

KEY: G-R2.1 one-wiring-method-binds-all-four
REQ: G-R2
QUOTE: ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.
CLAUSE: ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers
STATUS: KILLED
ASSERT: FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot

KEY: G-R2.2 the-evidence-binding
REQ: G-R2
QUOTE: **Topics and bindings.** Four settings resolved like `esCvdTopic()` (the gateway's `*_TOPIC` prefixing): `KAFKA_ES_FOOTPRINT_TOPIC` → `futures.footprint` (event `es-footprint`), `KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC` → `futures.footprint.evidence` (`es-footprint-evidence`), `KAFKA_ES_FOOTPRINT_BARS_TOPIC` → `futures.footprint.bars` (`es-footprint-bar`), `KAFKA_ES_FOOTPRINT_OUTCOMES_TOPIC` → `futures.footprint.outcomes` (`es-footprint-outcome`).
CLAUSE: KAFKA_ES_FOOTPRINT_EVIDENCE_TOPIC -> futures.footprint.evidence (es-footprint-evidence)
STATUS: KILLED
ASSERT: FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics, FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint" ==> expected: <1> but was: <2> || [ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-

KEY: G-R3.1 an-oversize-record-is-dropped-not-broadcast
REQ: G-R3
QUOTE: `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
CLAUSE: a record the view REJECTS is still broadcast, except an OVERSIZE record, which is dropped
STATUS: KILLED
ASSERT: FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.130 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.014 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>

KEY: G-R3.2 a-rejected-record-is-still-broadcast
REQ: G-R3
QUOTE: `es-footprint-bar` and `es-footprint-outcome`: keyed view FIRST, then standalone VERBATIM broadcast (the `es-cvd-bar` class) — a record the view REJECTS for shape, session or size (G-R4, G-R8) is still broadcast, except an OVERSIZE record, which is dropped entirely (a record over the producer's own ceiling is corrupt).
CLAUSE: a record the view REJECTS for shape or session is still broadcast
STATUS: KILLED
ASSERT: FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.224 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.028 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>

KEY: G-R3.3 live-snapshots-are-never-retained
REQ: G-R3
QUOTE: **Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).
CLAUSE: never cached, never retained
STATUS: KILLED
ASSERT: FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>

KEY: G-R5.1 the-outcome-key-orders-numerically
REQ: G-R5
QUOTE: The zero-padded 19-digit encoding makes string order equal to `(timeframe, resolvedAtBarStartMs numeric, identity)` over the whole epoch domain;
CLAUSE: the zero-padded 19-digit encoding makes string order equal to (timeframe, resolvedAtBarStartMs numeric, identity)
STATUS: KILLED
ASSERT: FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession, FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth
FAIL: [ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.072 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.004 s <<< ERROR! || [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8> || [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> || [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "10|ES.v.0|1m|1|BUYE" || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "5|ES.v.0|1m|1|BUYER" || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "77|ES.v.0|5m|1|SELL" || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » StringIndexOutOfBounds Range [3, 22) out of bounds for length 8

KEY: G-R5.2 the-identity-is-the-last-component
REQ: G-R5
QUOTE: `identity` is ASCII `[A-Za-z0-9_.:|-]` (it CONTAINS `|`, which is why it is the LAST component).
CLAUSE: identity CONTAINS |, which is why it is the LAST component
STATUS: KILLED
ASSERT: FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession, FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth
FAIL: [ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.099 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< ERROR! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR! || [ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: "id3|000000000000000"> || [ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005> || [ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|" || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|" || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 » NumberFormat For input string: "z|00000000000000000" || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:216 » NumberFormat For input string: "ES.v.0|1m|1|BUYERS|"

KEY: G-R5.3 a-foreign-identity-is-refused
REQ: G-R5
QUOTE: Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
CLAUSE: a foreign shape (missing identity/timeframe/resolvedAtBarStartMs, non-canonical identity alphabet)
STATUS: KILLED
ASSERT: FootprintViewsTest.shapeDropsNeverMutate
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.008 s <<< FAILURE! || [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:40 expected: <SHAPE> but was: <ADMITTED>

KEY: G-R5.4 last-write-per-key-wins
REQ: G-R5
QUOTE: Last write per full key wins; an identity resolves EXACTLY ONCE upstream (F-E6), so a redelivery carries the same key and overwrites in place; a foreign shape (missing `identity`/`timeframe`/`resolvedAtBarStartMs`, non-canonical identity alphabet or > 128 bytes) is a `shape` drop but still broadcast.
CLAUSE: Last write per full key wins; a redelivery carries the same key and overwrites in place
STATUS: KILLED
ASSERT: FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>

KEY: G-R6.1 the-hello-carries-the-footprint-field
REQ: G-R6
QUOTE: **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
CLAUSE: the existing cvd-hello frame gains ONE field footprint
STATUS: KILLED
ASSERT: FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:152 expected: <{"sessionDate":null,"hwm":{},"footprint":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}> but was: <{"sessionDate":null,"hwm":{},"footprintX":{"sessionDate":null,"hwm":{},"outcomeHwm":{}}}>

KEY: G-R6.2 the-hello-is-sent-when-footprint-alone-is-on
REQ: G-R6
QUOTE: `cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.
CLAUSE: cvd-hello is sent whenever CVD, SPX-levels OR footprint is enabled
STATUS: KILLED
ASSERT: FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.004 s <<< FAILURE! || [ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>

KEY: G-R6.3 the-hello-carries-the-outcome-high-water-mark
REQ: G-R6
QUOTE: **Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).
CLAUSE: outcomeHwm:{tf: maxResolvedAtBarStartMs}
STATUS: KILLED
ASSERT: FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks, FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.006 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"30s":2000,"1m":5}}> || [ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>

KEY: G-R10.1 a-footprint-record-never-escapes-the-consumer-loop
REQ: G-R10
QUOTE: A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
CLAUSE: a footprint record can never throw out of the consumer loop — the mutation makes it throw, and the test that calls it propagates that throw
STATUS: KILLED
ASSERT: FootprintViewsTest.aNullPayloadIsARefusalNotAThrow
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:85 a null bar payload must not throw ==> Unexpected exception thrown: java.lang.IllegalArgumentException: json

KEY: G-R10.2 every-admission-failure-is-a-counted-drop
REQ: G-R10
QUOTE: A footprint record can never throw out of the consumer loop (every admission failure is a counted drop); the coordinator lock is never held while sending.
CLAUSE: every admission failure is a counted drop
STATUS: KILLED
ASSERT: FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.133 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.074 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:193 four events + the stale bar, never the oversize one: [{"type":"es-footprint","data":{"schemaVersion":6,"live":true}}, {"type":"es-footprint-evidence","data":{"schemaVersion":6,"ev":true}}, {"type":"es-footprint-bar","data":{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"1m","sessionDate":" || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>

KEY: G-R9.2 every-record-is-counted-before-admission
REQ: G-R9
QUOTE: `gateway_footprint_records_total{event,consumer}` — every Kafka record of that event polled by that consumer, incremented BEFORE admission (so it counts oversize/shape/stale records too).
CLAUSE: records_total is incremented BEFORE admission, so it counts oversize/shape/stale records too
STATUS: KILLED
ASSERT: FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.168 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.025 s <<< FAILURE! || [ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:193 four events + the stale bar, never the oversize one: [{"type":"es-footprint","data":{"schemaVersion":6,"live":true}}, {"type":"es-footprint-evidence","data":{"schemaVersion":6,"ev":true}}, {"type":"es-footprint-bar","data":{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"1m","sessionDate":" || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>

KEY: G-R4 older-date-dropped
REQ: G-R4
QUOTE: OLDER ⇒ `stale_session` drop;
CLAUSE: an OLDER session date is dropped
STATUS: KILLED
ASSERT: FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts, FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders, FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly, FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules
FAIL: [ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.160 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED> || [ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>

KEY: G-R4 equal-date-upserts
REQ: G-R4
QUOTE: EQUAL (or first) ⇒ upsert (last write per key wins).
CLAUSE: an EQUAL date upserts without rolling
STATUS: KILLED
ASSERT: FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit, FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic, FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact, FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin, FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks, FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor, FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.002 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< FAILURE! || [ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE! || [ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {"sessionDate":"2026-08-14","bars":[{"schemaVersion":6,"symbol":"ES.v.0","timeframe":"30s","sessionDate":"2026-08-14","observations":{"barStartMs":5000},"tag":"x"}],"nextCursor":null} ==> expected: <true> but was: <false> || [ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0> || [ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{"sessionDate":"2026-08-14","hwm":{"30s":2000,"1m":5},"outcomeHwm":{"5m":77}}> but was: <{"sessionDate":"2026-08-14","hwm":{},"outcomeHwm":{"5m":70}}> || [ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:217 expected: <2> but was: <0> || [ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:189 expected: <4> but was: <1>

KEY: G-R4 key-fields-must-be-in-the-epoch-domain
REQ: G-R4
QUOTE: `timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates).
CLAUSE: an out-of-domain timestamp is refused
STATUS: KILLED
ASSERT: FootprintViewsTest.shapeDropsNeverMutate
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.003 s <<< FAILURE! || [ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {"timeframe":"1m","sessionDate":"2026-08-14","observations":{"barStartMs":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>

KEY: G-R7 the-exclusive-cursor-at-the-domain-edge site1-removed
REQ: G-R7
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: the afterMs lower bound stays exclusive at the top of the epoch domain
STATUS: KILLED
ASSERT: FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain
FAIL: [ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.019 s <<< FAILURE! || [ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:185 a cursor beyond the domain admits nothing — it must not wrap ==> expected: <0> but was: <4>

KEY: G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed
REQ: G-R7
QUOTE: **Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` → `{sessionDate, [sessionMismatch:true], bars:[<record>…], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).
CLAUSE: the same guard relaxed from < to <= — recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way
STATUS: SURVIVED
ASSERT: 
FAIL: 


2026-09-08T22:22:10.121811Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "sed -n '1,220p' scripts/footprint-reqstate.sh; git diff 9128e37..HEAD -- ES-FOOTPRINT-CAMPAIGN.json ES-FOOTPRINT-GATEWAY-DESIGN.md scripts/footprint-reqstate.py scripts/footprint-reqstate.preamble 2>/dev/null" in /private/tmp/oe-conf-gw
 succeeded in 1888ms:
diff --git a/ES-FOOTPRINT-CAMPAIGN.json b/ES-FOOTPRINT-CAMPAIGN.json
index fa082ee..e934b5e 100644
--- a/ES-FOOTPRINT-CAMPAIGN.json
+++ b/ES-FOOTPRINT-CAMPAIGN.json
@@ -1,72 +1,4 @@
 {
- "G-R4 older-date-dropped": {
-  "status": "KILLED",
-  "clause": "an OLDER session date is dropped",
-  "requirement": "G-R4",
-  "documentText": "Rollover is MONOTONIC on the parsed date: NEWER than current \u21d2 BOTH views are cleared and the date advances;",
-  "file": "src/main/java/app/feedgateway/FootprintViews.java",
-  "occurrence": 1,
-  "occurrencesInFile": 1,
-  "line": 146,
-  "enclosing": "private Boolean rollover(LocalDate date) {",
-  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
-  "killedBy": [
-   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
-   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
-   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
-   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
-  ],
-  "assertionFailures": [
-   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
-   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
-   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
-   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
-  ],
-  "anyFailures": [
-   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
-   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
-   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
-   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
-  ],
-  "killedByThrow": null,
-  "seconds": 9.3,
-  "patch": {
-   "old": "        if (cmp < 0) return null;",
-   "new": "        if (cmp < 0) return false;"
-  },
-  "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
-   "returnCode": 1,
-   "mutatedFileSha256": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc",
-   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-   "filesSha256": {
-    "src/main/java/app/feedgateway/FootprintViews.java": {
-     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-     "mutated": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc"
-    }
-   },
-   "outputSha256": "08ff87ecfb0f487eb30d6d108ccf0061123fb6f6ad81c7d4f09ea2e266816229",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
-   "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.260 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED>",
-    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>"
-   ],
-   "treeRestoredClean": true
-  },
-  "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
-   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
-   "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
-   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
-  }
- },
  "G-R4 newer-date-rolls-both": {
   "status": "KILLED",
   "clause": "a NEWER date rolls BOTH views",
@@ -88,13 +20,13 @@
    "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "killedByThrow": null,
-  "seconds": 9.9,
+  "seconds": 8.4,
   "patch": {
    "old": "        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;",
    "new": "        bars.clear(); barsBytes = 0;"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -104,97 +36,78 @@
      "mutated": "80e54979d9f023c11f1239b119aeda0731e0e7ba7d7c8a91ca2c67c36d8a2bc6"
     }
    },
-   "outputSha256": "bcacad7451d47daefc4b0d22a4ab6d2c9416b6519b0c272d01fdd6819134c218",
+   "outputSha256": "e828bf491785b2f2126bd6f670f68a848e95719ff059004acee12a6a0db75304",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.009 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.003 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:100 the outcomes view rolled with the bars view ==> expected: <0> but was: <1>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
- "G-R4 equal-date-upserts": {
+ "G-R9.3 a-rollover-is-counted": {
   "status": "KILLED",
-  "clause": "an EQUAL date upserts without rolling",
-  "requirement": "G-R4",
-  "documentText": "Rollover is MONOTONIC on the parsed date: NEWER than current \u21d2 BOTH views are cleared and the date advances;",
+  "clause": "rollover clears are counted in gateway_footprint_rollovers_total, one per date advance",
+  "requirement": "G-R9",
+  "documentText": "`gateway_footprint_evictions_total{view}` \u2014 records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
-  "line": 147,
+  "line": 150,
   "enclosing": "private Boolean rollover(LocalDate date) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
-   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
-   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
-   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
-   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "assertionFailures": [
-   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
-   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
-   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
-   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "anyFailures": [
-   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
-   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
-   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
-   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
   ],
   "killedByThrow": null,
-  "seconds": 8.4,
+  "seconds": 9.3,
   "patch": {
-   "old": "        if (cmp == 0) return false;",
-   "new": "        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }"
+   "old": "        rollovers.incrementAndGet();",
+   "new": "        if (false) rollovers.incrementAndGet();"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
-   "mutatedFileSha256": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca",
+   "mutatedFileSha256": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-     "mutated": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca"
+     "mutated": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5"
     }
    },
-   "outputSha256": "976964853c4afc7edcc4a250f133ed1049d154b02dd71ccce82d0592c717ba26",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "8c192e089a19d101b11fb761901287bda9d7e7acf2d0f07fbf59d1393a9860c7",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.012 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {\"sessionDate\":\"2026-08-14\",\"bars\":[{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"30s\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":5000},\"tag\":\"x\"}],\"nextCursor\":null} ==> expected: <true> but was: <false>",
-    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0>",
-    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0>",
-    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>",
-    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{},\"outcomeHwm\":{\"5m\":70}}>"
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.008 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 evict-oldest": {
   "status": "KILLED",
-  "clause": "evict the OLDEST key of the timeframe",
+  "clause": "eviction takes the OLDEST key of the timeframe it is evicting from",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
@@ -216,13 +129,13 @@
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "killedByThrow": null,
-  "seconds": 9.1,
+  "seconds": 9.7,
   "patch": {
    "old": "            String oldest = view.ceilingKey(tf + \"|\");",
    "new": "            String oldest = view.floorKey(tf + \"|~\");"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -232,10 +145,10 @@
      "mutated": "49771ca4920cdb17ed55de64297d01cff1209b4c7674a0d338c87db431db4146"
     }
    },
-   "outputSha256": "645fd51dd50b5fdef7a318db025a99eaf42202112942e9e959f59f7d74126815",
+   "outputSha256": "28bcb7d3a48b27e6f9571071daf0b935dd169f958fbd3f78e03f2ef1a13c7cca",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:257 expected: <247> but was: <246>",
     "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:242 1m lost its oldest ==> expected: <[2]> but was: <[1]>"
@@ -243,16 +156,16 @@
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 evict-rotates": {
   "status": "KILLED",
-  "clause": "eviction ROTATES across timeframes",
+  "clause": "eviction rotates round-robin across the timeframes rather than draining one",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
@@ -271,13 +184,13 @@
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "killedByThrow": null,
-  "seconds": 10.6,
+  "seconds": 8.5,
   "patch": {
    "old": "            if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;",
    "new": "            if (isBars) barsEvictCursor = cursor; else outcomesEvictCursor = cursor;"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "1c700daa48bd560d42ed500056b7f54ddf4a324cadb6fdd5913d4690ec844c21",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -287,25 +200,25 @@
      "mutated": "1c700daa48bd560d42ed500056b7f54ddf4a324cadb6fdd5913d4690ec844c21"
     }
    },
-   "outputSha256": "c7e5f490aa39ddccbc0dbb6e69d9416c8f2c7ec3a5bbed11cf1b76f58d598e74",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "d725c9adb3fec20c73306d454323307c694fdc35a69c2b17baf8457d4ad305d9",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.057 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.008 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:245 no timeframe is starved: 30s kept two ==> expected: <2> but was: <3>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 byte-budget-decremented": {
   "status": "KILLED",
-  "clause": "evicting decrements the byte total",
+  "clause": "evicting a record decrements the view byte total by that record length",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
@@ -324,13 +237,13 @@
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
   "killedByThrow": null,
-  "seconds": 9.9,
+  "seconds": 9.6,
   "patch": {
    "old": "            if (isBars) barsBytes -= len; else outcomesBytes -= len;",
    "new": "            if (isBars) barsBytes -= 0; else outcomesBytes -= 0;"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "604a8ec8ce620cc3e8d1e4a18e51bdc41af6639c24b90f01754b45a9619c0d3a",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -340,19 +253,19 @@
      "mutated": "604a8ec8ce620cc3e8d1e4a18e51bdc41af6639c24b90f01754b45a9619c0d3a"
     }
    },
-   "outputSha256": "732222ed33cd5588f276b022fe80f486562b2dc6b230a56ea2af610fe426108d",
+   "outputSha256": "9a312fb53d5937f2cf251ef66e8324bddf9f9227ed2af402a2244d5da58ac882",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.007 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <3>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -377,13 +290,13 @@
    "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact"
   ],
   "killedByThrow": null,
-  "seconds": 9.6,
+  "seconds": 11.8,
   "patch": {
    "old": "        while (view.size() > maxCount || (isBars ? barsBytes : outcomesBytes) > maxBytes) {",
    "new": "        while (view.size() > maxCount) {"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "be3b5ec8befa5b49f38a03ab67a789b7cfc023b4d7ce49ad8354fc3661fc4248",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -393,25 +306,25 @@
      "mutated": "be3b5ec8befa5b49f38a03ab67a789b7cfc023b4d7ce49ad8354fc3661fc4248"
     }
    },
-   "outputSha256": "01b906f794bb1080c637d4233c5a6e7297c2d5ba863a71424fb2ea2e8a2f4529",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "b59d9dda210f42e08195440aef9832eb36c10eda4fe748cfa046eff6c07da97d",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.008 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:254 expected: <1> but was: <0>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8 evictions-counted": {
   "status": "KILLED",
-  "clause": "evictions are counted per view",
+  "clause": "every eviction is counted in the view eviction counter",
   "requirement": "G-R8",
   "documentText": "View budgets, enforced under the lock at every admission: bars `B_bytes` \u2264 `GATEWAY_ES_FOOTPRINT_BARS_MAX_BYTES` (default 128 MiB, summed JSON `String.length()`) and `B_count` \u2264 12 000; outcomes `O_bytes` \u2264 16 MiB and `O_count` \u2264 20 000; when a budget would be exceeded the OLDEST keys of that view (smallest key per timeframe, round-robin across timeframes so no timeframe is starved) are evicted until it fits, counted in `gateway_footprint_evictions_total{view}`; the hello HWM still reports the newest key.",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
@@ -433,13 +346,13 @@
    "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
   ],
   "killedByThrow": null,
-  "seconds": 9.2,
+  "seconds": 9.1,
   "patch": {
    "old": "        if (evicted > 0) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);",
    "new": "        if (false) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "a923db6234a63a8730dcd07cd80c8ecb899eb29df79d70d33101933d6634dcad",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -449,10 +362,10 @@
      "mutated": "a923db6234a63a8730dcd07cd80c8ecb899eb29df79d70d33101933d6634dcad"
     }
    },
-   "outputSha256": "5cd8a47c5aa0ddc106ecf98e95d2164fcaef5738fef16fb0b942f1d2a04eb6c5",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "dfaaad3647815004181478eb6edcc18a00bbd10d24cfda7291b394e6887487b4",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:260 expected: <1> but was: <0>",
     "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>"
@@ -460,16 +373,16 @@
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
  "G-R8a ceiling-strict": {
   "status": "KILLED",
-  "clause": "the ceiling is inclusive; only ABOVE is refused",
+  "clause": "a footprint topic is VALID iff its effective max.message.bytes is at or below the ceiling",
   "requirement": "G-R8a",
   "documentText": "**Topic validation before consumption, inside the refresh (optional topics kept optional).** A footprint topic is VALID iff its effective `max.message.bytes \u2264 GATEWAY_ES_FOOTPRINT_MAX_MESSAGE_BYTES_CEILING` (default 1 048 588) and `compression.type \u2208 {producer, uncompressed}`, read via `AdminClient.describeConfigs` with the same bounded timeout as `partitionRefreshMetadataTimeoutMs`.",
   "file": "src/main/java/app/feedgateway/FootprintTopicGate.java",
@@ -494,16 +407,20 @@
    "FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts",
    "FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows",
    "FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne",
-   "FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears"
+   "FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears",
+   "FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted",
+   "FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused",
+   "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions",
+   "FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp"
   ],
   "killedByThrow": null,
-  "seconds": 8.8,
+  "seconds": 8.3,
   "patch": {
    "old": "            else if (cfg.maxMessageBytes() > ceiling) o = new Outcome(false, Reason.ceiling,",
    "new": "            else if (cfg.maxMessageBytes() >= ceiling) o = new Outcome(false, Reason.ceiling,"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "10e91507bbd9c76a05f20ce70fd0d7e76f345dbeee0f0dfa8c79fd86e19317f1",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
@@ -513,29 +430,33 @@
      "mutated": "10e91507bbd9c76a05f20ce70fd0d7e76f345dbeee0f0dfa8c79fd86e19317f1"
     }
    },
-   "outputSha256": "795b2a6ac35768e7b64183fd156915a608096e43b443553013783745a1b08b32",
+   "outputSha256": "35ad374a6bc52f04efc55fe64e409d01b0dcc0236cb808c6105d88f638e20cc0",
    "outputTail": "ed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.evidence ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048588 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes ceiling max.message.bytes=1048588 > 1048588\n",
    "failureLines": [
     "[ERROR] app.feedgateway.FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp -- Time elapsed: 0.005 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.003 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.002 s <<< ERROR!",
     "[ERROR] app.feedgateway.FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0.002 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.007 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintSeamTest.applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts:68 the validated topic is added; the unvalidated one is WITHHELD from added() ==> expected: <[futures.footprint.bars-0]> but was: <[]>",
     "[ERROR]   FootprintSeamTest.theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows:106 expected: <[futures.cvd.bars-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd.bars-0]>",
     "[ERROR]   FootprintTopicGateTest.aTopicValidatedAfterFailuresShowsTheFailuresAndAGaugeOfOne:99 expected: <true> but was: <false>",
-    "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]>"
+    "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:186 the absent/unvalidated topic's partition is withheld; others pass ==> expected: <[futures.cvd-0, futures.footprint.bars-0]> but was: <[futures.cvd-0]>",
+    "[ERROR]   FootprintTopicGateTest.anAbsentTopicDefersStartUpProceedsAndItIsAdmittedOnceItAppears:69 \u00bb IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.outcomes: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588)]",
+    "[ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:55 \u00bb IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 > 1048588)]",
+    "[ERROR]   FootprintTopicGateTest.theCeilingIsInclusiveAndOneByteAboveIsRefused:38 \u00bb IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.outcomes: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 ",
+    "[ERROR]   FootprintWiringTest.validationSeriesFollowTheGateAfterStartUp:225 \u00bb IllegalState ES_FOOTPRINT_TOPIC_INVALID [futures.footprint: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.bars: ceiling (max.message.bytes=1048588 > 1048588), futures.footprint.evidence: ceiling (max.message.bytes=1048588 > 1048588)]"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -560,13 +481,13 @@
    "FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted"
   ],
   "killedByThrow": null,
-  "seconds": 10.6,
+  "seconds": 7.9,
   "patch": {
    "old": "            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression,",
    "new": "            else if (false) o = new Outcome(false, Reason.compression,"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "88113e222a9afc61792000b26899359fc51afc02b5fac10a12f82c0cf8b52ede",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
@@ -576,19 +497,19 @@
      "mutated": "88113e222a9afc61792000b26899359fc51afc02b5fac10a12f82c0cf8b52ede"
     }
    },
-   "outputSha256": "409af9d682efd2d4396d404b406d13693ed1752d4c62f0af40d381505383df6c",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "046357a965c009f9779cd3fffe2e2d755cc3f9b4decb0f963af96b2dae1cffa6",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted -- Time elapsed: 0.015 s <<< FAILURE!",
     "[ERROR]   FootprintTopicGateTest.codecsAreRefusedProducerAndUncompressedAccepted:51 gzip ==> Expected java.lang.IllegalStateException to be thrown, but nothing was thrown."
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -613,13 +534,13 @@
    "FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt"
   ],
   "killedByThrow": null,
-  "seconds": 9.3,
+  "seconds": 9.5,
   "patch": {
    "old": "            if (cfg == null) o = new Outcome(false, Reason.admin, \"no config returned\");",
    "new": "            if (cfg == null) o = new Outcome(false, Reason.admin, \"no config returned\");\n            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression, \"compression.type=\" + cfg.compressionType());"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "0f5935f573879932d3c401a5d1ad1589021d2a10ec5c4c6c0f814e71d53ff4b8",
    "originalFileSha256": "64bd5c519c81b92f0cc4b787561e31a2ff5428c4b1b8861929c05ce983c0c501",
@@ -629,7 +550,7 @@
      "mutated": "0f5935f573879932d3c401a5d1ad1589021d2a10ec5c4c6c0f814e71d53ff4b8"
     }
    },
-   "outputSha256": "61ebec0c0408229a52b30f201dfa9abc0e2150a31cfd863d8fef2cadcd559cb2",
+   "outputSha256": "cfa236af7de0c598c5b9c24e7617c74b9bc73b1cebce09b76af4412486e7e872",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
     "[ERROR] app.feedgateway.FootprintTopicGateTest.ceilingTakesPrecedenceOverCompressionForOneAttempt -- Time elapsed: 0.006 s <<< FAILURE!",
@@ -638,10 +559,10 @@
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -672,13 +593,13 @@
    "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic"
   ],
   "killedByThrow": null,
-  "seconds": 9.6,
+  "seconds": 8.0,
   "patch": {
    "old": "            String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));",
    "new": "            String hi = barKey(tf, Math.min(toMsInclusive - 1, EPOCH_MAX_MS));"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "f448803e303bdbdfaddcad7f8d1851a2cc77d9f21decc7d80ed25eea755d895a",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -688,12 +609,12 @@
      "mutated": "f448803e303bdbdfaddcad7f8d1851a2cc77d9f21decc7d80ed25eea755d895a"
     }
    },
-   "outputSha256": "b1c2ad68bf4c07d7a5a142d00b8cfe040df2b68d85ce57878064bcc9d96c1a06",
+   "outputSha256": "abd3e07a4b6394daa9bdaa44b2da9e9d63e7d067f93083c60503ae6e987d3330",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.016 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim -- Time elapsed: 0 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:65 expected: <3> but was: <2>",
     "[ERROR]   FootprintBackfillControllerTest.recordsAreWrittenAsTheirOwnAsciiBytesVerbatim:157 the producer's bytes, untouched ==> expected: <true> but was: <false>",
     "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:200 expected: <3> but was: <2>"
@@ -701,10 +622,10 @@
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -712,7 +633,7 @@
   "status": "KILLED",
   "clause": "the forwarded limit is clamped to at least 1",
   "requirement": "G-R7",
-  "documentText": "`limit` clamped to `[1, 100]` for both routes: with the producer ceiling 262 144 B a full page is \u2264 25 MiB + envelope (G-R8), the number the page proxy's byte ceiling is derived from.",
+  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   "file": "src/main/java/app/feedgateway/GatewayController.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
@@ -729,13 +650,13 @@
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
   "killedByThrow": null,
-  "seconds": 9.2,
+  "seconds": 7.7,
   "patch": {
    "old": "    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
    "new": "    static int clamp(int limit) { return Math.min(limit, FOOTPRINT_LIMIT_MAX); }"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "0b7f3a2971dc69c3850ba867c539c6f2b6b17a8013993bc0a9d661ffb40162dd",
    "originalFileSha256": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
@@ -745,8 +666,8 @@
      "mutated": "0b7f3a2971dc69c3850ba867c539c6f2b6b17a8013993bc0a9d661ffb40162dd"
     }
    },
-   "outputSha256": "97da7c51916ad7c3fe29595df3ba06ebadab73e12d28d717f8e575b1bd97d225",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "4fc86784c016c4b742506831aec4dc4cb74a2efb2f16792a33d3c2584aa54d3d",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
     "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <1> but was: <0>"
@@ -754,10 +675,10 @@
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -782,13 +703,13 @@
    "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit"
   ],
   "killedByThrow": null,
-  "seconds": 10.6,
+  "seconds": 7.7,
   "patch": {
    "old": "    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }",
    "new": "    static int clamp(int limit) { return Math.max(1, limit); }"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "55236d83d718eff57ec004a64e589fa1637998835869484f7548cbc1aeeca9b9",
    "originalFileSha256": "1ae9bf5b68f48a705e037b8b78b5522fa911238993af6b0a7b1a2de424bb76ca",
@@ -798,19 +719,19 @@
      "mutated": "55236d83d718eff57ec004a64e589fa1637998835869484f7548cbc1aeeca9b9"
     }
    },
-   "outputSha256": "a87149fe9eb3f64556e73d2e8ddcaef385aeb5dfa71f88272658e00ab137445b",
+   "outputSha256": "32f283a64f9113e4641aa9bda6f70edfa2a23510ad96755a11e7f41c99139a33",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.009 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:66 expected: <100> but was: <5000>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -835,22 +756,27 @@
    "FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing",
    "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
    "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions",
-   "FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge"
+   "FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge",
+   "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled",
+   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "anyFailures": [
    "FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing",
    "FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns",
    "FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose",
-   "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions"
+   "FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions",
+   "FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge",
+   "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled",
+   "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "killedByThrow": null,
-  "seconds": 9.8,
+  "seconds": 8.9,
   "patch": {
    "old": "        if (settings.esFootprintEnabled()) {",
    "new": "        if (true) {"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "884cdb7be1fa827c909b4825d3adcbd701327d0771191be49d202c08cf1a7723",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -860,29 +786,31 @@
      "mutated": "884cdb7be1fa827c909b4825d3adcbd701327d0771191be49d202c08cf1a7723"
     }
    },
-   "outputSha256": "83a116439c67b929ab7a4d0e45e16c48e628570c7711457e27706eb2ec88040c",
+   "outputSha256": "1f97e002a97bd17d315fc8bb5f7e65b595d0780fc3e06c88d1636f3fe97bd360",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.006 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.001 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.419 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.013 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.325 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions -- Time elapsed: 0.348 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose -- Time elapsed: 0.008 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns -- Time elapsed: 0.308 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing -- Time elapsed: 0.001 s <<< FAILURE!",
     "[ERROR]   FootprintBackfillControllerTest.flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing:30 expected: <404> but was: <401>",
     "[ERROR]   FootprintSeamTest.theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose:123",
     "[ERROR]   FootprintWiringTest.bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions:189 flag off: identity ==> expected: <[futures.cvd-0, futures.footprint.bars-0, futures.footprint.outcomes-0]> but was: <[futures.cvd-0]>",
     "[ERROR]   FootprintWiringTest.flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge:94 expected: <false> but was: <true>",
-    "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true>"
+    "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:145 and nothing enabled sends none ==> expected: <false> but was: <true>",
+    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:123 flag off adds nothing ==> expected: <true> but was: <false>",
+    "[ERROR]   FootprintSeamTest.malformedTypedParametersAre400BeforeAnyFootprintCodeRuns:244 \u00bb Servlet Request processing failed: java.lang.NullPointerException: Cannot invoke \"app.feedgateway.liquidityhistory.LiquidityHistoryAuth$Result.status()\" because \"auth\" is null"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -907,13 +835,13 @@
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "killedByThrow": null,
-  "seconds": 8.8,
+  "seconds": 8.9,
   "patch": {
    "old": "        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));",
    "new": "        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -923,19 +851,19 @@
      "mutated": "6f13b40b447c2af3303bacedce943fc041fe9d0ff599eec6c693b6fda49d2817"
     }
    },
-   "outputSha256": "36a6ce3ea255feca32515790717875b45fb874b1f99ca4e17ae5f390e8820194",
+   "outputSha256": "ff251c162bb7ca0e68e9faeaeffc420484a873d49c7c190c85713bee070d3b0f",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.004 s <<< FAILURE!",
-    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot"
+    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -963,13 +891,13 @@
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
   ],
   "killedByThrow": null,
-  "seconds": 10.5,
+  "seconds": 8.1,
   "patch": {
    "old": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-evidence\"));",
    "new": "        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint\"));"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -979,21 +907,21 @@
      "mutated": "434c0d07e687a382a500a516ed31c8daf50ec1537044f6fd96ac41be3ba7d4e8"
     }
    },
-   "outputSha256": "dff1eb8d0d7b95dba979f4ba69cac4a178f8f433f506f1abe9aabf4126723ba6",
+   "outputSha256": "d7946de3d02cc945ffe3969e72126a4097d15ad8e588067571bd872294d05d39",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.014 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics -- Time elapsed: 0.008 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics:49 es-footprint\" ==> expected: <1> but was: <2>",
-    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-"
+    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-footprint-evidence, futures.footprint=es-footprint, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.outcomes=es-footprint-outcome, futures.footprint.bars=es-"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1021,13 +949,13 @@
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 10.0,
+  "seconds": 7.6,
   "patch": {
    "old": "        return a.reason() != FootprintViews.Reason.OVERSIZE;",
    "new": "        return true;"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "5134cf0f9808ec272649bb6533231d5dd0a952cd584dd200754d8f424af03125",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -1037,21 +965,21 @@
      "mutated": "5134cf0f9808ec272649bb6533231d5dd0a952cd584dd200754d8f424af03125"
     }
    },
-   "outputSha256": "9d3883d5d359d4c1f93a82b3dab306cd07b2900031a677448ad852b4f21323c4",
+   "outputSha256": "143d2c20c46aa7d5adc8fbe0c8ebb430940758845a09acea3a4cb6ba2e41f34b",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.171 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.017 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.130 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.014 s <<< FAILURE!",
     "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:188 oversize: dropped entirely ==> expected: <false> but was: <true>",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:165 oversize: dropped entirely ==> expected: <false> but was: <true>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1079,13 +1007,13 @@
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 9.7,
+  "seconds": 9.6,
   "patch": {
    "old": "        return a.reason() != FootprintViews.Reason.OVERSIZE;",
    "new": "        return a.reason() == FootprintViews.Reason.ADMITTED;"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "66a9f89dfab6c87ccac729e9bcaf04b11ca59d06c4c08602d8ad7be5da36b8d1",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -1095,21 +1023,21 @@
      "mutated": "66a9f89dfab6c87ccac729e9bcaf04b11ca59d06c4c08602d8ad7be5da36b8d1"
     }
    },
-   "outputSha256": "526f404044c70ebe1ab33d970f810703bf67b8f9c0f925c8c9dc588d32526b0b",
+   "outputSha256": "02d373570b909bdc6a4fbae9dead0061dd7928fb4058d558091963b754322486",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.144 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.024 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.224 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.028 s <<< FAILURE!",
     "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:187 stale: still broadcast ==> expected: <true> but was: <false>",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:163 stale: dropped from the view, still broadcast ==> expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1134,13 +1062,13 @@
    "FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"
   ],
   "killedByThrow": null,
-  "seconds": 9.7,
+  "seconds": 8.6,
   "patch": {
    "old": "            return CachePolicy.expiring(0L);",
    "new": "            return CachePolicy.expiring(settings.esFootprintSeekBackMs());"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "606a7fa3701572b9a8978bf6c9c8a0a027c1e56e2dcca908b19ff84993edc72f",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -1150,19 +1078,19 @@
      "mutated": "606a7fa3701572b9a8978bf6c9c8a0a027c1e56e2dcca908b19ff84993edc72f"
     }
    },
-   "outputSha256": "dc3bd0f89961a3d6da485c92e5caa786f0cb2c6e9dfa0dff2d5bee7e6e541152",
+   "outputSha256": "5c2844947204c0a114280ae01dc24075e6c0694e0c5cf88b2aabf7fb61c1e4c7",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1189,16 +1117,18 @@
    "FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck",
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace",
-   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks"
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
+   "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth",
+   "FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor"
   ],
   "killedByThrow": null,
-  "seconds": 11.5,
+  "seconds": 7.7,
   "patch": {
    "old": "        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;",
    "new": "        return tf + \"|\" + resolvedAtBarStartMs + \"|\" + identity;"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1208,15 +1138,15 @@
      "mutated": "150f204073f2958a9d8313e3e6b57cf916b2ad5f7f891bda927c43f87be1beec"
     }
    },
-   "outputSha256": "c13b0ad4e299169c8d5f71115ac8de84d29e77628f9a22885d423a831c84d433",
+   "outputSha256": "fcfffacf75b9cecff9a6d9bb25de0ca26ebcec92f0e635e65ee10ba10752f8b1",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.180 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.002 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.002 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.072 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.004 s <<< ERROR!",
     "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.StringIndexOutOfBoundsException: Range [3, 22) out of bounds for length 8>",
     "[ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|5|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000>",
     "[ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 \u00bb NumberFormat For input string: \"10|ES.v.0|1m|1|BUYE\"",
@@ -1227,10 +1157,10 @@
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1257,16 +1187,18 @@
    "FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck",
    "FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession",
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace",
-   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks"
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
+   "FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth",
+   "FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor"
   ],
   "killedByThrow": null,
-  "seconds": 10.2,
+  "seconds": 7.6,
   "patch": {
    "old": "        return tf + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs) + \"|\" + identity;",
    "new": "        return tf + \"|\" + identity + \"|\" + String.format(\"%019d\", resolvedAtBarStartMs);"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "7a75b9af663605c79e061f5994d60b4ec216178fbe6ad27e44f8cc7f76c49e57",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1276,16 +1208,16 @@
      "mutated": "7a75b9af663605c79e061f5994d60b4ec216178fbe6ad27e44f8cc7f76c49e57"
     }
    },
-   "outputSha256": "57f2fac001b550e7801a5dec8fbfcfa8adf7a8f25f002eeb1ad06a9af991ec10",
+   "outputSha256": "84fdaa47ff4c3eb28b11ce989b2c67901335459a00c43d2415d04d14536b6368",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.120 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession -- Time elapsed: 0.099 s <<< FAILURE!",
     "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.002 s <<< ERROR!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< ERROR!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.001 s <<< ERROR!",
     "[ERROR] app.feedgateway.FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck -- Time elapsed: 0.001 s <<< ERROR!",
-    "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: \"id1|000000000000000\">",
+    "[ERROR]   FootprintSeamTest.theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession:226 expected: <null> but was: <java.lang.NumberFormatException: For input string: \"id3|000000000000000\">",
     "[ERROR]   FootprintViewsTest.lastWritePerKeyWinsAndKeysAreFixedWidth:64 expected: <1m|0000000000000000005|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000> but was: <1m|ES.v.0|1m|1|BUYERS|LEVEL|650000|650000|0000000000000000005>",
     "[ERROR]   FootprintBackfillControllerTest.outcomesRouteValidatesTheCursorBeforeTheSessionCheck:91 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\"",
     "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 \u00bb NumberFormat For input string: \"ES.v.0|1m|1|BUYERS|\"",
@@ -1295,10 +1227,10 @@
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1323,13 +1255,13 @@
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
   "killedByThrow": null,
-  "seconds": 11.1,
+  "seconds": 9.8,
   "patch": {
    "old": "                || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {",
    "new": "                || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "b41e5ceceaa36f9b18b60ab36542e40adda355dc0f933bbe8d3a92fb6cf1a31f",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1339,19 +1271,19 @@
      "mutated": "b41e5ceceaa36f9b18b60ab36542e40adda355dc0f933bbe8d3a92fb6cf1a31f"
     }
    },
-   "outputSha256": "8d74c3ef2ab8fbd0528a72e9b241e6e70edab2f24d2fc8c6e0b28675657b0672",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "3d8ba7f566abd3d02554c4e794647b6fe554b9bc2a7148f6648eca6053b67be6",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.008 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.shapeDropsNeverMutate:40 expected: <SHAPE> but was: <ADMITTED>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1376,13 +1308,13 @@
    "FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace"
   ],
   "killedByThrow": null,
-  "seconds": 10.2,
+  "seconds": 8.1,
   "patch": {
    "old": "            String previous = outcomes.put(key, json);",
    "new": "            String previous = outcomes.putIfAbsent(key, json);"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "1a29ff784d9ae87c27fa6256fe064b480ac945623b6facdba5238d69fa17937f",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1392,19 +1324,19 @@
      "mutated": "1a29ff784d9ae87c27fa6256fe064b480ac945623b6facdba5238d69fa17937f"
     }
    },
-   "outputSha256": "c53cbd4c751e01a5e7a09c1bd9f648487fa0a02732b20daa8b378e6a45df4d3b",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "fe43b7ca0d87718bed77942a8b5d384647600f46ca06f950b650b63b83f8b477",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.anOutcomeRedeliveryOverwritesItsKeyInPlace:75 the redelivery is the row that survives ==> expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1412,7 +1344,7 @@
   "status": "KILLED",
   "clause": "the existing cvd-hello frame gains ONE field footprint",
   "requirement": "G-R6",
-  "documentText": "`cvd-hello` is sent whenever CVD, SPX-levels OR footprint is enabled.",
+  "documentText": "**Hello.** When the flag is on, the existing `cvd-hello` frame gains ONE field `footprint: {sessionDate, hwm:{tf: maxBarStartMs}, outcomeHwm:{tf: maxResolvedAtBarStartMs}}` built as ONE snapshot under the coordinator lock (`sessionDate` null and both maps empty before the first accepted record; a timeframe absent from a map = nothing held).",
   "file": "src/main/java/app/feedgateway/FeedGatewayService.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
@@ -1429,13 +1361,13 @@
    "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
   ],
   "killedByThrow": null,
-  "seconds": 12.6,
+  "seconds": 8.6,
   "patch": {
    "old": "            sb.append(\",\\\"footprint\\\":\").append(footprintViews.helloField());",
    "new": "            sb.append(\",\\\"footprintX\\\":\").append(footprintViews.helloField());"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "5203835d0356ee0a9d2d478680c07baa48e3770b66cbe0c85d2a924d70865ae2",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -1445,19 +1377,19 @@
      "mutated": "5203835d0356ee0a9d2d478680c07baa48e3770b66cbe0c85d2a924d70865ae2"
     }
    },
-   "outputSha256": "2a06a54b559a994b5fce7084d6e2b25ad96d9c8f45f1b824ed444c379f7a981b",
+   "outputSha256": "eb0b1b133947fbaaf05c1e6c911c23fa04b2944f3fa0e80e398f217c1567c7d8",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:152 expected: <{\"sessionDate\":null,\"hwm\":{},\"footprint\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}}> but was: <{\"sessionDate\":null,\"hwm\":{},\"footprintX\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}}}>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1482,13 +1414,13 @@
    "FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled"
   ],
   "killedByThrow": null,
-  "seconds": 10.0,
+  "seconds": 8.2,
   "patch": {
    "old": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;",
    "new": "        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -1498,19 +1430,19 @@
      "mutated": "0d27b0b336e5d241d8d30412f251d3b47dbd6158388b425e234b381d274453fc"
     }
    },
-   "outputSha256": "da516b2828cb21ac518bada8d5eac3a01a94a9f833dcf5397b242ae3de7b455a",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "598b9e64f42d24f4bc797610d6515cc01ffa7f5e49e51f129110182e66dff77a",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.003 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled -- Time elapsed: 0.004 s <<< FAILURE!",
     "[ERROR]   FootprintWiringTest.theHelloIsSentWhenFootprintAloneIsEnabled:144 footprint alone still sends cvd-hello ==> expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1538,13 +1470,13 @@
    "FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot"
   ],
   "killedByThrow": null,
-  "seconds": 9.8,
+  "seconds": 9.7,
   "patch": {
    "old": "            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, outcomes, true);",
    "new": "            sb.append(\",\\\"outcomeHwm\\\":\"); appendHwm(sb, bars, true);"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "368f116e662d3150e861190653954f413fbe2ce71254dec4501542935a35c416",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1554,21 +1486,21 @@
      "mutated": "368f116e662d3150e861190653954f413fbe2ce71254dec4501542935a35c416"
     }
    },
-   "outputSha256": "95a01fc29841ba76302b3e2d410c1950cabbb24c9556262307f2e928270580d2",
+   "outputSha256": "979fa54b21f8f2f4f9d661dc26fd8ebc0eb58ad8567aba9f0b1758f36aab03a4",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.003 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot -- Time elapsed: 0.006 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0.003 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"30s\":2000,\"1m\":5}}>",
     "[ERROR]   FootprintWiringTest.flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot:154 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1593,13 +1525,13 @@
    "FootprintViewsTest.aNullPayloadIsARefusalNotAThrow"
   ],
   "killedByThrow": null,
-  "seconds": 11.5,
+  "seconds": 8.7,
   "patch": {
    "old": "        if (json == null) return Admission.SHAPE;",
    "new": "        if (json == null) throw new IllegalArgumentException(\"json\");"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "5b915b8626df30f329a936e4fceef00fce3d32c66a3f820c7439a0cd381d7650",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1609,19 +1541,19 @@
      "mutated": "5b915b8626df30f329a936e4fceef00fce3d32c66a3f820c7439a0cd381d7650"
     }
    },
-   "outputSha256": "495c6b9efeef51691efa70273bc81641f13a52cc8b635684224823d1b62ee32d",
+   "outputSha256": "2089bec71d1e99bb0e3be43256174880d80efb7bd28893bea3427027adadadf5",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.004 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNullPayloadIsARefusalNotAThrow -- Time elapsed: 0.005 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.aNullPayloadIsARefusalNotAThrow:85 a null bar payload must not throw ==> Unexpected exception thrown: java.lang.IllegalArgumentException: json"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1637,22 +1569,25 @@
   "enclosing": "footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").in",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "assertionFailures": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "anyFailures": [
+   "FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone",
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 10.6,
+  "seconds": 8.4,
   "patch": {
    "old": "            footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").incrementAndGet();",
    "new": "            if (a.reason() != FootprintViews.Reason.SHAPE) footprintCounter(\"drops_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\",reason=\\\"\" + a.reason().name().toLowerCase() + \"\\\"}\").incrementAndGet();"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "0520888256109aba632dd9b7c1ae8a7df49b51fd61e33136f5a8b098fb1584c6",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -1662,19 +1597,21 @@
      "mutated": "0520888256109aba632dd9b7c1ae8a7df49b51fd61e33136f5a8b098fb1584c6"
     }
    },
-   "outputSha256": "004035b0e09fd4deda345254b7f496efd6402285905a882f375a7ac2aed1b1bf",
+   "outputSha256": "90b67c2c619abb7ee6b2e3b2fd1c431eb0febc0e1ef5cc8f6cb2c75976af6419",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.248 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.133 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.074 s <<< FAILURE!",
+    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:193 four events + the stale bar, never the oversize one: [{\"type\":\"es-footprint\",\"data\":{\"schemaVersion\":6,\"live\":true}}, {\"type\":\"es-footprint-evidence\",\"data\":{\"schemaVersion\":6,\"ev\":true}}, {\"type\":\"es-footprint-bar\",\"data\":{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\",\"sessionDate\":\"",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:170 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
@@ -1702,13 +1639,13 @@
    "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
-  "seconds": 10.9,
+  "seconds": 8.1,
   "patch": {
    "old": "        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {",
    "new": "        if (!\"es-footprint-bar\".equals(event) && !\"es-footprint-outcome\".equals(event)) {\n            footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n            return true;                                            // live snapshots: never admitted to a view\n        }\n        FootprintViews.Admission a = \"es-footprint-bar\".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);\n        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission\n        footprintCounter(\"records_total{event=\\\"\" + event + \"\\\",consumer=\\\"\" + consumer + \"\\\"}\").incrementAndGet();\n        if (a.reason() != FootprintViews.Reason.ADMITTED) {"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e",
    "originalFileSha256": "e234d7ce2bb14fc2cab5637b831799a6b84b05b95da8d1791a7592060f5b0e75",
@@ -1718,82 +1655,177 @@
      "mutated": "0bf77a46cc510ae7abe70e604bb7196f8aa334efc5cd927c30515e3481543b8e"
     }
    },
-   "outputSha256": "4bb2899583fc11e9cf410f45b8a0d6cc72b64e76b033ba79409f50c2c68d48c8",
+   "outputSha256": "f6acba2b37bfa532600079850d795f0b99106b6928d660b64165aa93a4a5f58c",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.204 s <<< FAILURE!",
-    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.017 s <<< FAILURE!",
-    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:201 expected: <true> but was: <false>",
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.168 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone -- Time elapsed: 0.025 s <<< FAILURE!",
+    "[ERROR]   FootprintSeamTest.everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone:193 four events + the stale bar, never the oversize one: [{\"type\":\"es-footprint\",\"data\":{\"schemaVersion\":6,\"live\":true}}, {\"type\":\"es-footprint-evidence\",\"data\":{\"schemaVersion\":6,\"ev\":true}}, {\"type\":\"es-footprint-bar\",\"data\":{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\",\"sessionDate\":\"",
     "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:171 expected: <true> but was: <false>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
- "G-R9.3 a-rollover-is-counted": {
+ "G-R4 older-date-dropped": {
   "status": "KILLED",
-  "clause": "rollover clears are counted in gateway_footprint_rollovers_total, one per date advance",
-  "requirement": "G-R9",
-  "documentText": "`gateway_footprint_evictions_total{view}` \u2014 records removed by the byte/count budgets (never by rollover; rollover clears are counted in `gateway_footprint_rollovers_total`, one per date advance).",
+  "clause": "an OLDER session date is dropped",
+  "requirement": "G-R4",
+  "documentText": "OLDER \u21d2 `stale_session` drop;",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
-  "line": 150,
+  "line": 146,
   "enclosing": "private Boolean rollover(LocalDate date) {",
   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
   "killedBy": [
-   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
+   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
+   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "assertionFailures": [
-   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
+   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
+   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "anyFailures": [
-   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts"
+   "FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts",
+   "FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders",
+   "FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly",
+   "FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules"
   ],
   "killedByThrow": null,
   "seconds": 9.9,
   "patch": {
-   "old": "        rollovers.incrementAndGet();",
-   "new": "        if (false) rollovers.incrementAndGet();"
+   "old": "        if (cmp < 0) return null;",
+   "new": "        if (cmp < 0) return false;"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
-   "mutatedFileSha256": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5",
+   "mutatedFileSha256": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
    "filesSha256": {
     "src/main/java/app/feedgateway/FootprintViews.java": {
      "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
-     "mutated": "f767572912ed5b40ae12612d7c26e50e8b5b32ce4d4424f49e5a648630fc91c5"
+     "mutated": "e206c3d812af8d34dafbd81072df45f86f8f9d645ac67b6d8a5dfea59112dacc"
     }
    },
-   "outputSha256": "3f7d49f6a8db6e15b2dd03a4f0ef59108140e60e091bedc34ed0434a62a5702a",
+   "outputSha256": "f4ab8756f16dfed50301c918b25d3d79b6fbbcf6abcd9f37fb1deb649edc2547",
    "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.006 s <<< FAILURE!",
-    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:101 expected: <1> but was: <0>"
+    "[ERROR] app.feedgateway.FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules -- Time elapsed: 0.160 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR]   FootprintViewsTest.aNewerDateRollsBothViewsAnOlderDateIsDroppedAndEqualUpserts:102 late record from a dead session ==> expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintViewsTest.outOfOrderBootstrapAcrossTopicsInBothOrders:113 expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintViewsTest.yearAndMonthBoundariesOrderCorrectly:126 expected: <STALE_SESSION> but was: <ADMITTED>",
+    "[ERROR]   FootprintWiringTest.admissionCountsAndBroadcastDecisionsFollowTheOverlapRules:169 expected: <true> but was: <false>"
+   ],
+   "treeRestoredClean": true
+  },
+  "baseline": {
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
+   "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+   "returnCode": 0,
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
+   "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
+  }
+ },
+ "G-R4 equal-date-upserts": {
+  "status": "KILLED",
+  "clause": "an EQUAL date upserts without rolling",
+  "requirement": "G-R4",
+  "documentText": "EQUAL (or first) \u21d2 upsert (last write per key wins).",
+  "file": "src/main/java/app/feedgateway/FootprintViews.java",
+  "occurrence": 1,
+  "occurrencesInFile": 1,
+  "line": 147,
+  "enclosing": "private Boolean rollover(LocalDate date) {",
+  "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
+  "killedBy": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
+   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin"
+  ],
+  "assertionFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
+   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin",
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
+   "FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor",
+   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
+  ],
+  "anyFailures": [
+   "FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit",
+   "FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic",
+   "FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact",
+   "FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin",
+   "FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks",
+   "FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor",
+   "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
+  ],
+  "killedByThrow": null,
+  "seconds": 9.5,
+  "patch": {
+   "old": "        if (cmp == 0) return false;",
+   "new": "        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }"
+  },
+  "evidence": {
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
+   "returnCode": 1,
+   "mutatedFileSha256": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca",
+   "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+   "filesSha256": {
+    "src/main/java/app/feedgateway/FootprintViews.java": {
+     "original": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
+     "mutated": "bb23ff808e60cdb5c05bd5e4a4f37293d5c59e413cb61374f0aec61eeab46dca"
+    }
+   },
+   "outputSha256": "f26d8491495969b2b8b532267b45f3799611bce6b6046220a2a02040e60890e9",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "failureLines": [
+    "[ERROR] app.feedgateway.FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.002 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks -- Time elapsed: 0 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit -- Time elapsed: 0.001 s <<< FAILURE!",
+    "[ERROR]   FootprintBackfillControllerTest.barsPageIsStreamedWithTheEnvelopeAndClampedLimit:60 {\"sessionDate\":\"2026-08-14\",\"bars\":[{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"30s\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":5000},\"tag\":\"x\"}],\"nextCursor\":null} ==> expected: <true> but was: <false>",
+    "[ERROR]   FootprintViewsTest.barsPageIsAscendingExclusiveCursorInclusiveBoundAndAtomic:197 expected: <2> but was: <0>",
+    "[ERROR]   FootprintViewsTest.byteBudgetEvictsUntilItFitsAndBytesAreExact:255 expected: <2> but was: <0>",
+    "[ERROR]   FootprintViewsTest.countBudgetEvictsTheOldestKeyPerTimeframeRoundRobin:240 expected: <1> but was: <0>",
+    "[ERROR]   FootprintViewsTest.helloIsEmptyBeforeRecordsAndCarriesPerTimeframeHighWaterMarks:140 expected: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"30s\":2000,\"1m\":5},\"outcomeHwm\":{\"5m\":77}}> but was: <{\"sessionDate\":\"2026-08-14\",\"hwm\":{},\"outcomeHwm\":{\"5m\":70}}>",
+    "[ERROR]   FootprintViewsTest.outcomesPageOrdersByNumericTimestampThenIdentityWithAnOpaqueCursor:217 expected: <2> but was: <0>",
+    "[ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:189 expected: <4> but was: <1>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
- "G-R7 the-epoch-guard variant1-guard": {
+ "G-R4 key-fields-must-be-in-the-epoch-domain": {
   "status": "KILLED",
   "clause": "an out-of-domain timestamp is refused",
-  "requirement": "G-R7",
-  "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
+  "requirement": "G-R4",
+  "documentText": "`timeframe` must be one of the seven keys; the key fields must be present integers in the epoch domain; otherwise `shape` drop (never mutates).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
   "occurrence": 1,
   "occurrencesInFile": 1,
@@ -1810,13 +1842,13 @@
    "FootprintViewsTest.shapeDropsNeverMutate"
   ],
   "killedByThrow": null,
-  "seconds": 8.6,
+  "seconds": 9.3,
   "patch": {
    "old": "        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;",
    "new": "        return x < 0 ? -1 : x;"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "e553a6d4e5b1827ccdc4191e78f47425f75f040f094bff517831a91eaf15a7f8",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1826,8 +1858,8 @@
      "mutated": "e553a6d4e5b1827ccdc4191e78f47425f75f040f094bff517831a91eaf15a7f8"
     }
    },
-   "outputSha256": "63c03a3e0c428c83066cbb2eb4188bc9686808ad6f7a22018c03afe610781a3a",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "84109a218c15a75cf89a92f5f33501713b921b88f538a075c6afef57a5d36936",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
     "[ERROR] app.feedgateway.FootprintViewsTest.shapeDropsNeverMutate -- Time elapsed: 0.003 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.shapeDropsNeverMutate:36 {\"timeframe\":\"1m\",\"sessionDate\":\"2026-08-14\",\"observations\":{\"barStartMs\":253402300800000}} ==> expected: <SHAPE> but was: <ADMITTED>"
@@ -1835,16 +1867,16 @@
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
- "G-R7 the-epoch-guard variant2-removed": {
+ "G-R7 the-exclusive-cursor-at-the-domain-edge site1-removed": {
   "status": "KILLED",
-  "clause": "a cursor at or past the last epoch value can have nothing after it, and the increment is taken only inside the domain",
+  "clause": "the afterMs lower bound stays exclusive at the top of the epoch domain",
   "requirement": "G-R7",
   "documentText": "**Backfill.** `GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=` \u2192 `{sessionDate, [sessionMismatch:true], bars:[<record>\u2026], nextCursor}` with the `/api/cvd/bars` semantics (ascending `barStartMs`, exclusive `afterMs`, inclusive `toMs`, `limit` clamped to `[1, 100]`), computed as ONE snapshot under the coordinator lock with the session check (`sessionMismatch` when a non-empty `sessionDate` differs from the coordinator's).",
   "file": "src/main/java/app/feedgateway/FootprintViews.java",
@@ -1863,13 +1895,13 @@
    "FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain"
   ],
   "killedByThrow": null,
-  "seconds": 8.4,
+  "seconds": 8.2,
   "patch": {
    "old": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {",
    "new": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0) {"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 1,
    "mutatedFileSha256": "bcdfcc3334a5212551a86cd937777facd6b5915b9f634abbb7a9a99469c0add6",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1879,23 +1911,23 @@
      "mutated": "bcdfcc3334a5212551a86cd937777facd6b5915b9f634abbb7a9a99469c0add6"
     }
    },
-   "outputSha256": "2e473b8213962c118dde054e3292e1be92dec67dd677ed6a8ddd9661c73962c8",
-   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
+   "outputSha256": "fb1d4803500c116b623b7e9c6d2baf6d811f760c434751b8935812d4a9ed126d",
+   "outputTail": " described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\n",
    "failureLines": [
-    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.005 s <<< FAILURE!",
+    "[ERROR] app.feedgateway.FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain -- Time elapsed: 0.019 s <<< FAILURE!",
     "[ERROR]   FootprintViewsTest.theBarsCursorIsExclusiveAtTheTopOfTheEpochDomain:185 a cursor beyond the domain admits nothing \u2014 it must not wrap ==> expected: <0> but was: <4>"
    ],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  },
- "G-R7 the-epoch-guard variant3-relaxed": {
+ "G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed": {
   "status": "SURVIVED",
   "clause": "the same guard relaxed from < to <= \u2014 recorded because it is INERT: at afterMs == EPOCH_MAX_MS the +1 is safe and hi is capped, so lo > hi either way",
   "requirement": "G-R7",
@@ -1910,13 +1942,13 @@
   "assertionFailures": [],
   "anyFailures": [],
   "killedByThrow": null,
-  "seconds": 13.2,
+  "seconds": 10.8,
   "patch": {
    "old": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {",
    "new": "            if (TIMEFRAME_KEY.matcher(tf == null ? \"\" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive <= EPOCH_MAX_MS) {"
   },
   "evidence": {
-   "repoCommit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "repoCommit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "returnCode": 0,
    "mutatedFileSha256": "1805041d85205e5bad948989b9f83b1f4d321c9f9322dd96226cf8761ffb8058",
    "originalFileSha256": "915d37cc20d21609a16608dc1cf7b72bbe42e7b152c08c456ff509aafafd4f96",
@@ -1926,16 +1958,16 @@
      "mutated": "1805041d85205e5bad948989b9f83b1f4d321c9f9322dd96226cf8761ffb8058"
     }
    },
-   "outputSha256": "f98d9901a798da0ed73bc57a9723db936e89b944fe2b293b54b923ff92b10188",
+   "outputSha256": "8bc34cd70bbee8d0320e948ae2325884ce13359266e15fd9cb2fc6f7bd6b78f3",
    "outputTail": "tures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n",
    "failureLines": [],
    "treeRestoredClean": true
   },
   "baseline": {
-   "commit": "ae8a68762366e752a4010ee2fdaf850fbdcd9fde",
+   "commit": "9128e372a56912777980b1cf5948384d4fe7380d",
    "command": "mvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false",
    "returnCode": 0,
-   "outputSha256": "118b4aa496785ab057b261943fad511b08db81ecb75a5bf6dd0d133046fca3ea",
+   "outputSha256": "106958f0669e632abf5c439af8fd97a62a0d05fcb9b8e2873e441fc97cd0c5a8",
    "outputTail": "enJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars ceiling max.message.bytes=1048589 > 1048588\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.bars unknown unknown topic futures.footprint.bars\nRGW_ALERT GATEWAY_FOOTPRINT_TOPIC_NOT_ADMITTED futures.footprint.outcomes unknown unknown topic futures.footprint.outcomes\nMockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3\nOpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended\nWARNING: A Java agent has been loaded dynamically (/Users/abhinav/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar)\nWARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\nWARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information\nWARNING: Dynamic loading of agents will be disallowed by default in a future release\n"
   }
  }
diff --git a/ES-FOOTPRINT-GATEWAY-DESIGN.md b/ES-FOOTPRINT-GATEWAY-DESIGN.md
index aebb360..9697619 100644
--- a/ES-FOOTPRINT-GATEWAY-DESIGN.md
+++ b/ES-FOOTPRINT-GATEWAY-DESIGN.md
@@ -175,15 +175,20 @@ verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-C
 this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
 record with `scripts/footprint-reqstate.sh --check`.
 
+What this table cannot do is decide whether a quoted obligation is the clause a probe exercises.
+The generator refuses a quote that is not verbatim in the requirement it sits under, and one that
+shares no vocabulary with the clause recorded against it; beyond that, attribution is a judgement
+made in review, and the merge gate for this change corrected several of them.
+
 | id | Conformance | Gate | Disposition |
 |----|-------------|------|-------------|
 | G-R1 | 1 of 1 probes pinned, over 1 clause | 2 | Flag and wiring |
 | G-R2 | 2 of 2 probes pinned, over 2 clauses | 2 | Flag and wiring |
 | G-R3 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
-| G-R4 | 3 of 3 probes pinned, over 3 clauses | 2 | Delivery and views |
+| G-R4 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
 | G-R5 | 4 of 4 probes pinned, over 4 clauses | 2 | Delivery and views |
 | G-R6 | 3 of 3 probes pinned, over 3 clauses | 2 | Hello |
-| G-R7 | 5 of 6 probes pinned, over 4 clauses (1 survived) | 2 | Backfill routes |
+| G-R7 | 4 of 5 probes pinned, over 4 clauses (1 survived) | 2 | Backfill routes |
 | G-R8 | 5 of 5 probes pinned, over 5 clauses | 2 | Deployment contingency |
 | G-R9 | 3 of 3 probes pinned, over 3 clauses | 2 | Metrics |
 | G-R10 | 2 of 2 probes pinned, over 2 clauses | 2 | Non-interference |
diff --git a/scripts/footprint-reqstate.preamble b/scripts/footprint-reqstate.preamble
index 29cfb07..901a40c 100644
--- a/scripts/footprint-reqstate.preamble
+++ b/scripts/footprint-reqstate.preamble
@@ -7,3 +7,8 @@ evidence — the patch, the file, line and enclosing declaration, the command, t
 verbatim failure lines and a SHA-256 of the run output — is in `ES-FOOTPRINT-CAMPAIGN.json` beside
 this document. Regenerate this section with `scripts/footprint-reqstate.sh`, or check it against the
 record with `scripts/footprint-reqstate.sh --check`.
+
+What this table cannot do is decide whether a quoted obligation is the clause a probe exercises.
+The generator refuses a quote that is not verbatim in the requirement it sits under, and one that
+shares no vocabulary with the clause recorded against it; beyond that, attribution is a judgement
+made in review, and the merge gate for this change corrected several of them.
diff --git a/scripts/footprint-reqstate.py b/scripts/footprint-reqstate.py
index 7286250..5310c1c 100644
--- a/scripts/footprint-reqstate.py
+++ b/scripts/footprint-reqstate.py
@@ -60,6 +60,20 @@ def main():
                 problems.append(f"{rid}: is not a requirement in the document")
             elif dt not in req_text[rid]:
                 problems.append(f"{rid}: the quoted obligation is not verbatim in {rid}: {dt[:70]!r}")
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
             if m['status'] != 'KILLED':
                 continue
             # the named assertion failures must be the ones THIS run produced
@@ -110,7 +124,7 @@ def main():
             # site AND once for all sites together, so "n of m clauses" would triple-count it.
             # A clause probed at several SITES, or broken several WAYS (variants), is still one
             # clause; counting records as clauses triple-counts it.
-            groups = len({re.sub(r' (?:site\d+|variant\d+\S*)$', '', k) for k in keys[rid]})
+            groups = len({re.sub(r' (?:site|variant)\d+\S*$', '', k) for k in keys[rid]})
             state = f"{killed} of {len(ms)} probes pinned, over {groups} clause" + ("" if groups == 1 else "s")
             rest = ", ".join(f"{n} {st.lower()}" for st, n in sorted(by.items()))
             if rest: state += f" ({rest})"

2026-09-08T22:22:16.441672Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "jq -r 'to_entries[] | \"\\(.key)\\t\\(.value.patch.old)\\t=>\\t\\(.value.patch.new)\"' ES-FOOTPRINT-CAMPAIGN.json; python3 scripts/footprint-reqstate.py ES-FOOTPRINT-CAMPAIGN.json ES-FOOTPRINT-GATEWAY-DESIGN.md 'G-R\\d+[a-z]?' '{\"G-R.*\":\"2\"}' '{\"G-R1|G-R2\":\"Flag and wiring\",\"G-R3|G-R4|G-R5\":\"Delivery and views\",\"G-R6\":\"Hello\",\"G-R7\":\"Backfill routes\",\"G-R8|G-R8a\":\"Deployment contingency\",\"G-R9\":\"Metrics\",\"G-R10\":\"Non-interference\",\"G-R11\":\"Tests\"}' '{\"G-R11\":\"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break\"}' >/dev/null && echo VALIDATOR_OK" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
G-R4 newer-date-rolls-both	        bars.clear(); outcomes.clear(); barsBytes = 0; outcomesBytes = 0;	=>	        bars.clear(); barsBytes = 0;
G-R9.3 a-rollover-is-counted	        rollovers.incrementAndGet();	=>	        if (false) rollovers.incrementAndGet();
G-R8 evict-oldest	            String oldest = view.ceilingKey(tf + "|");	=>	            String oldest = view.floorKey(tf + "|~");
G-R8 evict-rotates	            if (isBars) barsEvictCursor = cursor + 1; else outcomesEvictCursor = cursor + 1;	=>	            if (isBars) barsEvictCursor = cursor; else outcomesEvictCursor = cursor;
G-R8 byte-budget-decremented	            if (isBars) barsBytes -= len; else outcomesBytes -= len;	=>	            if (isBars) barsBytes -= 0; else outcomesBytes -= 0;
G-R8 both-budgets-enforced	        while (view.size() > maxCount || (isBars ? barsBytes : outcomesBytes) > maxBytes) {	=>	        while (view.size() > maxCount) {
G-R8 evictions-counted	        if (evicted > 0) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);	=>	        if (false) (isBars ? barsEvictions : outcomesEvictions).addAndGet(evicted);
G-R8a ceiling-strict	            else if (cfg.maxMessageBytes() > ceiling) o = new Outcome(false, Reason.ceiling,	=>	            else if (cfg.maxMessageBytes() >= ceiling) o = new Outcome(false, Reason.ceiling,
G-R8a codec-allowlist	            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression,	=>	            else if (false) o = new Outcome(false, Reason.compression,
G-R9 ceiling-precedence	            if (cfg == null) o = new Outcome(false, Reason.admin, "no config returned");	=>	            if (cfg == null) o = new Outcome(false, Reason.admin, "no config returned");
            else if (cfg.compressionType() == null || !UNCOMPRESSED.contains(cfg.compressionType().trim().toLowerCase())) o = new Outcome(false, Reason.compression, "compression.type=" + cfg.compressionType());
G-R7 to-is-inclusive	            String hi = barKey(tf, Math.min(toMsInclusive, EPOCH_MAX_MS));	=>	            String hi = barKey(tf, Math.min(toMsInclusive - 1, EPOCH_MAX_MS));
G-R7 limit-clamped-low	    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }	=>	    static int clamp(int limit) { return Math.min(limit, FOOTPRINT_LIMIT_MAX); }
G-R7 limit-clamped-high	    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }	=>	    static int clamp(int limit) { return Math.max(1, limit); }
G-R1.1 the-flag-gates-every-footprint-object	        if (settings.esFootprintEnabled()) {	=>	        if (true) {
G-R2.1 one-wiring-method-binds-all-four	        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));	=>	        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding("DATABENTO", "es-footprint-outcome"));
G-R2.2 the-evidence-binding	        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding("DATABENTO", "es-footprint-evidence"));	=>	        topicEvents.put(settings.esFootprintEvidenceTopic(), new TopicBinding("DATABENTO", "es-footprint"));
G-R3.1 an-oversize-record-is-dropped-not-broadcast	        return a.reason() != FootprintViews.Reason.OVERSIZE;	=>	        return true;
G-R3.2 a-rejected-record-is-still-broadcast	        return a.reason() != FootprintViews.Reason.OVERSIZE;	=>	        return a.reason() == FootprintViews.Reason.ADMITTED;
G-R3.3 live-snapshots-are-never-retained	            return CachePolicy.expiring(0L);	=>	            return CachePolicy.expiring(settings.esFootprintSeekBackMs());
G-R5.1 the-outcome-key-orders-numerically	        return tf + "|" + String.format("%019d", resolvedAtBarStartMs) + "|" + identity;	=>	        return tf + "|" + resolvedAtBarStartMs + "|" + identity;
G-R5.2 the-identity-is-the-last-component	        return tf + "|" + String.format("%019d", resolvedAtBarStartMs) + "|" + identity;	=>	        return tf + "|" + identity + "|" + String.format("%019d", resolvedAtBarStartMs);
G-R5.3 a-foreign-identity-is-refused	                || !IDENTITY.matcher(identity).matches() || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {	=>	                || identity.getBytes(StandardCharsets.UTF_8).length > IDENTITY_MAX_BYTES) {
G-R5.4 last-write-per-key-wins	            String previous = outcomes.put(key, json);	=>	            String previous = outcomes.putIfAbsent(key, json);
G-R6.1 the-hello-carries-the-footprint-field	            sb.append(",\"footprint\":").append(footprintViews.helloField());	=>	            sb.append(",\"footprintX\":").append(footprintViews.helloField());
G-R6.2 the-hello-is-sent-when-footprint-alone-is-on	        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled() || footprintViews != null;	=>	        return settings.esCvdEnabled() || settings.esCvdSpxLevelsEnabled();
G-R6.3 the-hello-carries-the-outcome-high-water-mark	            sb.append(",\"outcomeHwm\":"); appendHwm(sb, outcomes, true);	=>	            sb.append(",\"outcomeHwm\":"); appendHwm(sb, bars, true);
G-R10.1 a-footprint-record-never-escapes-the-consumer-loop	        if (json == null) return Admission.SHAPE;	=>	        if (json == null) throw new IllegalArgumentException("json");
G-R10.2 every-admission-failure-is-a-counted-drop	            footprintCounter("drops_total{event=\"" + event + "\",consumer=\"" + consumer + "\",reason=\"" + a.reason().name().toLowerCase() + "\"}").incrementAndGet();	=>	            if (a.reason() != FootprintViews.Reason.SHAPE) footprintCounter("drops_total{event=\"" + event + "\",consumer=\"" + consumer + "\",reason=\"" + a.reason().name().toLowerCase() + "\"}").incrementAndGet();
G-R9.2 every-record-is-counted-before-admission	        footprintCounter("records_total{event=\"" + event + "\",consumer=\"" + consumer + "\"}").incrementAndGet();
        if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {
            return true;                                            // live snapshots: never admitted to a view
        }
        FootprintViews.Admission a = "es-footprint-bar".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);
        if (a.reason() != FootprintViews.Reason.ADMITTED) {	=>	        if (!"es-footprint-bar".equals(event) && !"es-footprint-outcome".equals(event)) {
            footprintCounter("records_total{event=\"" + event + "\",consumer=\"" + consumer + "\"}").incrementAndGet();
            return true;                                            // live snapshots: never admitted to a view
        }
        FootprintViews.Admission a = "es-footprint-bar".equals(event) ? footprintViews.admitBar(json) : footprintViews.admitOutcome(json);
        if (a.reason() == FootprintViews.Reason.OVERSIZE) return false;   // MUTATION: counted only AFTER admission
        footprintCounter("records_total{event=\"" + event + "\",consumer=\"" + consumer + "\"}").incrementAndGet();
        if (a.reason() != FootprintViews.Reason.ADMITTED) {
G-R4 older-date-dropped	        if (cmp < 0) return null;	=>	        if (cmp < 0) return false;
G-R4 equal-date-upserts	        if (cmp == 0) return false;	=>	        if (cmp == 0) { bars.clear(); outcomes.clear(); return false; }
G-R4 key-fields-must-be-in-the-epoch-domain	        return x < 0 || x > EPOCH_MAX_MS ? -1 : x;	=>	        return x < 0 ? -1 : x;
G-R7 the-exclusive-cursor-at-the-domain-edge site1-removed	            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {	=>	            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0) {
G-R7 the-exclusive-cursor-at-the-domain-edge site2-relaxed	            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive < EPOCH_MAX_MS) {	=>	            if (TIMEFRAME_KEY.matcher(tf == null ? "" : tf).matches() && toMsInclusive >= 0 && afterMsExclusive <= EPOCH_MAX_MS) {
VALIDATOR_OK

2026-09-08T22:22:22.565530Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
exec
/bin/bash -lc "jq '.\"G-R2.1 one-wiring-method-binds-all-four\" | {clause,documentText,patch,assertionFailures,failureLines:.evidence.failureLines}' ES-FOOTPRINT-CAMPAIGN.json; jq '.\"G-R3.3 live-snapshots-are-never-retained\" | {clause,documentText,patch,assertionFailures,failureLines:.evidence.failureLines}' ES-FOOTPRINT-CAMPAIGN.json; nl -ba ES-FOOTPRINT-CAMPAIGN.json | rg 'G-R2.1 one-wiring|\"clause\": \"ONE wiring|G-R3.3 live'" in /private/tmp/oe-conf-gw
 succeeded in 0ms:
{
  "clause": "ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers",
  "documentText": "ONE wiring method `addEsFootprintTopics(topicEvents)` called from BOTH the state cache consumer and the state live consumer (the `addEsCvdTopics` rule), pinned by a source test exactly like `CvdBarViewTest.bootstrapAndLiveConsumersShareOneCvdTopicWiringPath`.",
  "patch": {
    "old": "        topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));",
    "new": "        if (false) topicEvents.put(settings.esFootprintOutcomesTopic(), new TopicBinding(\"DATABENTO\", \"es-footprint-outcome\"));"
  },
  "assertionFailures": [
    "FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven"
  ],
  "failureLines": [
    "[ERROR] app.feedgateway.FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven -- Time elapsed: 0.003 s <<< FAILURE!",
    "[ERROR]   FootprintWiringTest.theWiringMethodPutsAllFourBindingsInTheMapItIsGiven:112 every topic is bound, to its own event ==> expected: <{futures.footprint=es-footprint, futures.footprint.evidence=es-footprint-evidence, futures.footprint.bars=es-footprint-bar, futures.footprint.outcomes=es-footprint-outcome}> but was: <{futures.footprint.bars=es-footprint-bar, futures.footprint.evidence=es-foot"
  ]
}
{
  "clause": "never cached, never retained",
  "documentText": "**Delivery classes.** `es-footprint` and `es-footprint-evidence`: standalone broadcast, VERBATIM, never selection-gated, never cached, never retained (the producer heartbeats every ≤ 5 s — F-R15 — so a new client waits at most one heartbeat; the same class as `es-cvd`).",
  "patch": {
    "old": "            return CachePolicy.expiring(0L);",
    "new": "            return CachePolicy.expiring(settings.esFootprintSeekBackMs());"
  },
  "assertionFailures": [
    "FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession"
  ],
  "failureLines": [
    "[ERROR] app.feedgateway.FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession -- Time elapsed: 0.004 s <<< FAILURE!",
    "[ERROR]   FootprintWiringTest.liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession:132 es-footprint is never retained ==> expected: <0> but was: <86400000>"
  ]
}
   817	 "G-R2.1 one-wiring-method-binds-all-four": {
   819	  "clause": "ONE wiring method addEsFootprintTopics(topicEvents) called from BOTH consumers",
  1044	 "G-R3.3 live-snapshots-are-never-retained": {

2026-09-08T22:22:27.724980Z ERROR codex_models_manager::manager: failed to renew cache TTL: missing field `supports_parallel_tool_calls` at line 93 column 5
codex
- [ES-FOOTPRINT-CAMPAIGN.json:819](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:819) still misstates `G-R2.1`. The mutation removes only the outcomes binding from `addEsFootprintTopics`; its failure proves that the method binds all four topics. It does not break or prove the recorded clause that one wiring method is “called from BOTH consumers.” The clause must describe the all-four-bindings obligation, or the probe must target the two call sites.

All quotations are otherwise verbatim and correctly attributed. Counts resolve to 34 mutations, 33 killed, one surviving, across 11 probed requirements and 33 distinct clause groups. Every named assertion failure appears in its record’s own `failureLines`. The generated table matches those counts and makes no ordering claim for outcomes versus bars.

VERDICT: REQUEST_CHANGES
tokens used
64.733
- [ES-FOOTPRINT-CAMPAIGN.json:819](/private/tmp/oe-conf-gw/ES-FOOTPRINT-CAMPAIGN.json:819) still misstates `G-R2.1`. The mutation removes only the outcomes binding from `addEsFootprintTopics`; its failure proves that the method binds all four topics. It does not break or prove the recorded clause that one wiring method is “called from BOTH consumers.” The clause must describe the all-four-bindings obligation, or the probe must target the two call sites.

All quotations are otherwise verbatim and correctly attributed. Counts resolve to 34 mutations, 33 killed, one surviving, across 11 probed requirements and 33 distinct clause groups. Every named assertion failure appears in its record’s own `failureLines`. The generated table matches those counts and makes no ordering claim for outcomes versus bars.

VERDICT: REQUEST_CHANGES
