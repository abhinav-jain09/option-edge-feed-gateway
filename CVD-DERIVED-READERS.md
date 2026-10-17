# Draft derived-output readers

Existing-topic roll-forward reader preparation for the owner's CVD migration.
The gateway's existing compatible consumer now binds the aligned GEX book and
short-premium recommendation topics to their released canonical schemas. Retained
JSON keeps its exact API text. Confluent frames decode to that same API text;
unknown or mismatching schemas and invalid Avro frames do not fall back to JSON.
Neither topic declares a null-value tombstone here. The aligned book clears with
an empty book; the recommendation topic carries recommendation records.

This changes only the transport binding. Topic subscriptions, cache semantics,
WebSocket routing, keys, groups and offset behavior stay as before. In particular,
this change does not add a recommendation subscription: the current source contains
recommendation routing/cache helpers and a setting but no corresponding topicEvents
binding. Its consumer inventory must be reconciled against runtime before enabling
that optional writer. It is not evidence that recommendations currently reach users.
The gateway has no Kafka writer on these paths.

The branch now includes latest gateway main and uses the qualified latest
contracts artifact through the automatic main build flow. Current verification
uses the official Jenkins87 JAR (digest8f5888c04786d0a409dd063c712166ef9068fb42b1340d4fce61ec941c60ef45),
not the old candidate. Immutable coordinates and digests identify build evidence;
they do not select a separate service release or topic identity.

Current targeted validation passes five CvdAvroRollingGatewayTest cases, four
CvdRetainedPollTest cases and thirteen EarlyAuctionVerdictWiringTest cases,
plus the POM's mandatory separate mock Spring context smoke. These use actual
configured codec classes and a mock registry. The earlier four-case candidate
run is historical evidence only. EAV is now also bound to the canonical reader;
see [EAV reader preparation](CVD-AVRO-READER-PREPARATION.md) for its six synthetic
engine fixtures, exact API/cache assertions, assertion control, reused finite
codec measurements and limitations. No full gateway campaign ran locally.

This draft enables no writer or new topic. Remaining Direction contracts,
advanced Python readers, complete reader/runtime inventory, destination registry
compatibility/seed and runtime qualification remain required before writers are
enabled. Strike-intelligence contracts are already released but its reader/runtime
closure remains open. The earlier production gateway image has completed; that
does not qualify these new bindings. This branch remains held until the remaining
coordinated gates and the single final es4 gateway campaign are complete.

Run-scoped replay now derives canonical bindings for the selected replay topics in
the orchestrator's existing options/underlying namespace contract. The additional
focused test reads a real aligned-book frame and retained JSON under its resolved
run topic with the same schema. No replay is executed and no topic is copied here.
The shared consumer still refuses an Avro failure without JSON fallback. The
existing counted skip policy applies to the live-name derived bindings, including
windowed replay. Run-scoped replay decode failures are fatal and uncounted: no
failed offset is skipped, and the existing replay error handling reports REPLAY_FAILED.
Four CvdRetainedPollTest cases cover the retained skip/fatal boundary, including
that run-scoped policy. The metric's historical CVD name also covers live derived
records; it does not count run-scoped failures.
Reader-first runtime qualification must include the shared-consumer cold-registry
failure behavior and replay namespace/registry provenance before writer enablement.
