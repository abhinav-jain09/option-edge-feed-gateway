## Review result

**MAJOR — route isolation is not true under blocked-body load.**  
[ContextTapeUpstream.java:95](/Users/abhinav/development/workspace/oe-gateway-esbox/src/main/java/app/feedgateway/contexttape/ContextTapeUpstream.java:95) retains one shared 16-thread reader pool, while the new route adds a separate four-permit controller bulkhead at [ContextTapeController.java:153](/Users/abhinav/development/workspace/oe-gateway-esbox/src/main/java/app/feedgateway/contexttape/ContextTapeController.java:153). A concrete failing input:

1. Send 16 authenticated `/api/context-tape/session` requests whose upstream headers arrive but whose bodies block.
2. Each session consumes a shared reader thread.
3. Send authenticated `/api/context-tape/es-box`.

The ES-box request acquires its independent `esBoxSlots` permit, but `readers.submit()` is rejected when the shared pool is full ([ContextTapeUpstream.java:921](/Users/abhinav/development/workspace/oe-gateway-esbox/src/main/java/app/feedgateway/contexttape/ContextTapeUpstream.java:921)), yielding a gateway 502 rather than an independently admitted ES-box response. Thus the ES route can be starved by session traffic despite independent gateway limiter and semaphore state. Conversely, four blocking ES-box reads reduce session’s effective reader capacity from 16 to 12. This fails the requested “cannot starve or be starved” bar.

**MINOR — the new tests do not exercise bulkhead isolation or ES-box `Retry-After` forwarding.**  
[ContextTapeControllerTest.java:192](/Users/abhinav/development/workspace/oe-gateway-esbox/src/test/java/app/feedgateway/contexttape/ContextTapeControllerTest.java:192) proves independent rate budgets and increments the ES warming counter, but does not saturate either route’s slots or the shared reader pool. Its WARMING fixture also has no `Retry-After`; a future ES-box-only regression dropping that header would pass. The existing common forwarding logic is correct by inspection, but the requested operational guarantee lacks direct coverage.

No BLOCKER findings.

### Requested verification

1. **Boolean-to-`Route` refactor:** session and compression behavior is preserved in the controller:
   - Auth remains first and fail-closed.
   - Session selects `rateLimiter`/`sessionSlots`/`upstream.session`; compression selects their existing compression equivalents.
   - Rate-limit, busy, served, warming, and upstream-error counters map correctly through `counter(...)`.
   - Permit release remains in `finally`.
   - Upstream status, body, content type, `Cache-Control: no-store`, and `Retry-After` forwarding remain common and unchanged.
   - The new `esBox()` upstream method uses the same `MAX_SESSION_BYTES` ceiling.

2. **Independence:** controller-level rate budgets and semaphores are distinct and correctly constructed. But shared `ContextTapeUpstream.readers` invalidates end-to-end slot independence under the concrete load above.

3. **MVC/security allowlist:** covered. The generic allowlist regression forbids the entire `/api/context-tape/*` family ([ContextTapeAuthAllowlistRegressionTest.java:40](/Users/abhinav/development/workspace/oe-gateway-esbox/src/test/java/app/feedgateway/contexttape/ContextTapeAuthAllowlistRegressionTest.java:40)); it need not name `es-box` separately. The new MVC tests also establish authenticated dispatch and unauthenticated 401 before upstream contact.

4. **Footprint campaign:** unaffected. `origin/main...HEAD` changes only the four context-tape controller/upstream/test files; `FeedGatewayService.java` and footprint specifications/gates are untouched.

### Quality bars

- **Institutional grade — not met:** the advertised route isolation is false under a bounded, documented failure mode.
- **Military grade — not met:** concurrency failure coverage is absent, so the isolation claim was not fully validated.
- **NASA grade — partially met:** good inline contract documentation, bounded transport, no-store behavior, route counters, and MVC coverage; however, the missing contention and ES `Retry-After` tests leave operability preparation incomplete.

Read-only review completed after `git fetch origin main`; no files were changed and no builds or tests were run.

VERDICT: REQUEST_CHANGES - shared upstream reader capacity allows session traffic to starve the independently bulkheaded ES-box route.