A — RESOLVED. `ContextTapeConfig` creates distinct primary and `esBoxUpstream` beans, each constructs its own upstream resources; qualified controller injection is unambiguous, and existing test-seam constructors intentionally retain one shared upstream.

B — NOT RESOLVED. `esBoxWarmingForwardsRetryAfterByteForByte` pins `Retry-After`, `Cache-Control`, and body forwarding. However, the isolation test blocks in mocked `HttpClient.send()` before `readOnDeadline()` submits work to the reader pool, so it does not reproduce saturation of all `READER_THREADS` by blocked response-body reads.

C — RESOLVED. `git diff origin/main...HEAD -- src/main/java/app/feedgateway/FeedGatewayService.java` is empty.

VERDICT: REQUEST_CHANGES - B's claimed saturation test blocks before reader submission and does not cover the round-1 reader-starvation failure.