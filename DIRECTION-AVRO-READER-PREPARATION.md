# Direction and projected strike readers

The gateway's actual existing topic subscriptions gain canonical bindings for Direction Current,
Push, Alert and Scorecard, plus the optional SPX-aligned ES strike-intelligence overlay. With the
existing fourteen bindings, the map contains nineteen. Topic settings still select the same names;
no new topic, group, offset reset or upstream producer is introduced. Kafka security, manual commits,
read-committed isolation and the bounded eight-record poll remain unchanged.

Retained JSON and exact released Avro share each binding. A framed registry/schema/decode error
remains fatal. Null values are allowed only on the previously declared aligned CVD level topic,
the projected strike topic's withdrawal boundary, and the Direction push topic's retained history.
New push producers must use non-null typed deletions on the existing delete-retained topic.

Both push ingestion paths use the same SPX withdrawal handler. The cache path hydrates without
emitting; the live path sends updates after its existing caught-up gate. A withdrawal removes the
cached state and records one topic/partition/offset marker for this source/key. It rejects older
or equal upserts and another partition, so a second consumer cannot restore the withdrawn reading.
A newer current record survives a delayed older withdrawal. A repeated withdrawal is emitted once
within the marker's retention window. The marker expires on monotonic time after the push freshness
window, when an older push cannot still pass the existing freshness checks. A later upsert still
uses the existing payload timestamp/session rules. No trading decision is calculated here.

A typed deletion must have boolean `tombstone: true`, matching SPX identity and at most 4096 API
characters; the actual source deletion is seven small fixed fields. Escaped JSON member names are
recognized. False, malformed, wrong-identity and oversized deletions do not clear current state.
The one retained deletion is bounded to 8 KiB UTF-16 text, plus constant metadata. Legacy Kafka nulls
produce only the minimal SPX deletion notification; no price or trading value is synthesized.

Focused source tests cover nine real-renderer codec cuts, wrong framed schemas, retained null
permissions, cache/live ordering, duplicate withdrawals, partition conflicts, invalid deletions and
expiry. The control runner compiles isolated single-source mutants and requires actual AssertionError
results; missing methods are incidental failures. It requires the same 1536 MiB JVM maximum as the
existing test POM because the unchanged session-store constructor enforces its heap floor; the first
256 MiB probe correctly refused that floor and is not counted as a passing baseline.

`DirectionReaderMicrocheck` uses one prime and five single fixed ingestion units per arm with calibrated
allocation counters. It excludes construction and does not establish service startup, full-cache,
throughput, source maximum domain, replay, restore or runtime capacity. Full gateway validation and
actual reader/consumer/archive/mirror closure must still run on es4, followed by reviewed latest-main
Jenkins reader-first deployment in dev and production. No migration writer is eligible from this
source-preparation document alone. Held-zero services must remain zero.
