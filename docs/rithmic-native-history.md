# Native full-book history — implementation pending review and release

This branch implements the gateway side of the RLF1 contract from OptionsEdge PR1565 and coordinates with six-phase reconciliation PR1546, OP-P4-02. No deployment has occurred. Production is out of scope. The native browser owner/controller is not mounted in the deployed UI.

## Scope and source cache

Explicit GATEWAY_RITHMIC_SYSTEM must be Rithmic Test or Rithmic Paper Trading, with CME/ESZ6. Known foreign-system frames are discarded before cadence/sequence state in both history and relay. The strict seven-field native request rejects price windows, client-supplied subject, unknown fields, unsafe/coerced timestamps and invalid cursors. Missing configuration refuses native requests. Legacy price-window JSON stays available.

Shared typed validation precedes sampling and serialization. Valid connected/mapped/ready nonempty uncrossed books alone acquire FULL_BOOK provenance. Invalid/empty/unready/oversize/unknown-scope input becomes bounded SOURCE_GAP metadata. Strings are bounded to 1,024 characters with strict surrogate validation; source times must be safe integers. This intentionally tightens malformed legacy inputs; ordinary valid legacy JSON remains unchanged. Cadence includes scope, stream, second and quality state so recovery within a bucket survives. Existing emitted-key corrections bypass sampling; compressed oversize candidates may be serialized again to detect recovery. They are never clipped into apparently full books.

One emitted-key TreeMap holds immutable compressed columns under 128 MiB and 8,192 columns for two hours. Native pages retain compressed references, not expanded price-sliced copies. Legacy rendering uses the same columns. Byte/column evictions expose expiring limits and retention gaps. Scan truncation records SCAN_SKIP and expires after its supported horizon. Source-gap metadata has 2,048 entries; exhaustion refuses history instead of forgetting markers. Late inserts/corrections and retroactive source-boundary changes increment safe lateRevision; wrap changes incarnation. Identical duplicates do not increment it. Source notices run after releasing the cache lock.

## Binary packets and writer

RLF1 packets contain the eight-byte prefix, bounded JSON header and sequential gzip bodies. Maximum packet is min(512 KiB,wsMaxQueuedBytes/4), header16 KiB, BOOK compressed body507,896 bytes. Capacity below32 KiB is refused. At most32 columns and one lookahead are snapshotted under lock; packet construction occurs outside it without decompression. Done requires READY coverage through the frozen end and no remaining key. Warming/waiting cannot complete a bootstrap. No subject enters history scope/header/invalidation.

Native format dispatch uses the existing WebSocket and shared history permit. OutboundChannel handles mixed text/binary ordering, UTF-8 byte accounting, bounded ownership copy, one writer/watchdog/teardown and4 KiB/one-message control headroom. Pages never bypass it. Busy leaves the history cursor unacknowledged; true saturation closes through the existing path. Structured invalidations preserve minimum affected boundary and maximum same-incarnation revision, with one pending item per scope, including across incarnation changes.

## Authentication and bootstrap clarification

Native data requires existing GATEWAY_AUTH_ENABLED, a tracked exact socket/channel, future verified access-token expiry, server-bound subject and attached current AppSession. The engine checks membership, subject, session deadlines, current selection entitlement and configured approval role atomically. With an authoritative approval URL, the existing approval reaper remains the revocation authority; no new per-page remote HTTP approval requests are introduced. These checks run before enqueue and immediately before writer send. Removal/logout/expiry/slow-disconnect clean history metadata.

A separate `session-identity` bootstrap control carries the authenticated subject only to that user's own authorized socket. It supplies the browser cache boundary without trusting unverified token parsing or putting a subject in shared history namespaces. This is an explicit addition to the prior wire design requiring review. `rithmic-history-hello` gives configured scope, format, ready/sourceState, availableThroughMs (null for no recorded data), incarnation, revision and limited. At most1,024 authorized subscriptions are retained, including no-data subscribers; initial readiness, first recorded data and late changes issue controls over the same writer. This release initially registers capable authorized sockets during existing addClient bootstrap; native requests from excess sockets return busy. There is no alternate HTTP market-data path.

## Evidence and remaining gates

Local focused r4:76 tests, zero failures/errors across strict request, typed validation, relay, cache, encoder, mixed writer, route and existing outbound/history suites; the isolated Spring smoke also ran. Source receipt: task outputs/rithmic-native-route-focused-r4.txt. Synthetic volatile7201×1540 cache qualification retained92,705,276 compressed bytes and produced226 pages; receipt rithmic-native-cache-density-focused-r1.txt. This is synthetic Mac evidence, not captured market data or Linux/RSS qualification.

Required before release: actual Claude self-review and independent Codex review, complete gateway regression comparison, existing Jenkins Linux tests/build and dev-only deployment, fresh producer golden compatibility, mounted authenticated browser/LAN/deadline/heap/graphics/resize/drag acceptance. Review may require corrections. No whole-six-phase acceptance is claimed.
