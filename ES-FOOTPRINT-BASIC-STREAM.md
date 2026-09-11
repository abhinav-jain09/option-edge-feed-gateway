# Basic footprint history over the existing stream

Status: IMPLEMENTING in this branch; not a deployment claim. Companion: options-edge `/es-footprint-basic`.

The authenticated `/ws/events` connection now accepts a read-only control message:

```json
{"type":"es-footprint-basic-history","sessionDate":"2026-09-11","afterMs":1789133399999,"toMs":1789133400000}
```

The usual `{type,data}` envelope returns type `es-footprint-basic-history`. Data contains `sessionDate`, `sessionMismatch`, the echoed `afterMs` and `toMs`, `sessionStartMs`, `sessionEndMs`, `bars` (up to four admitted schema-6 one-minute records), and an exclusive `nextCursor` or null. An exact four-record last page may be followed by an empty terminal page. Session mismatch returns no records. Errors are explicit: `bad_request`, `unavailable`, `busy`, `record_budget`, or `invalid_history`.

Both bounds must fall between 09:30 minus one millisecond and 16:00 exclusive on the requested New York date. The read uses the existing locked `FootprintViews.barsPage`, with ascending keys and an atomic session identity. No new Kafka consumer, topic, REST endpoint or unbounded session buffer is created. Completeness is not asserted by an empty terminal page; Basic checks every elapsed RTH minute itself.

| Requirement | Implementation and verification |
|---|---|
| Stream-only historical recovery | `FeedWebSocketHandler` dispatches text to the registered-session handler; existing live event behavior unchanged |
| Preserve authentication | Only the exact open session already registered in `clientsById` may read; ordinary handshake bearer/origin policy remains in force |
| Bounded requests and responses | 1 KiB UTF-8 request cap; maximum four records; conservative record-based page cap and final UTF-8 response size check; existing outbound queue |
| Backpressure | Existing footprint history semaphore; `busy` when exhausted; browser serializes pages and retries with a delay |
| Correct session/cursor behavior | Canonical date and integral values; RTH bounds, overnight exclusion, ascending page boundaries and cross-session mismatch tested |

All requirements are IMPLEMENTING pending coordinated merge. `FootprintBasicHistoryTest` exercises the helper and the real registered-session writer path, including authorization-by-registration, UTF-8 request bounds and busy response. Existing footprint view, attachment and gateway suites provide regression coverage. The web repository contains browser stream and stale-data tests.

Deploy gateway first and web second, only from approved main-branch merges through Jenkins. Existing footprint clients send no such control message and keep their current behavior. Basic against an older gateway reports a history timeout without HTTP fallback. Production/RTH verification is outstanding.
