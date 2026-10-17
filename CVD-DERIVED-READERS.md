# Derived-output reader preparation

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
contracts artifact through the automatic main build flow. The final nineteen-binding preparation uses official contracts main
`7cb2633481f91739af75ff6cd1f6d186c4155b2c`, Jenkins91 JAR digest
`4fe33a93c114c49c0786eaa2b576fb80c62b9670edc4796c10d158a712059b3c`. Immutable coordinates and digests identify build evidence;
they do not select a separate service release or topic identity.

The earlier targeted validation passed five CvdAvroRollingGatewayTest cases, four
CvdRetainedPollTest cases and thirteen EarlyAuctionVerdictWiringTest cases,
plus the POM's mandatory separate mock Spring context smoke. These use actual
configured codec classes and a mock registry. The earlier four-case candidate
run is historical evidence only. EAV is now also bound to the canonical reader;
see [EAV reader preparation](CVD-AVRO-READER-PREPARATION.md) for its six synthetic
engine fixtures, exact API/cache assertions, assertion control, reused finite
codec measurements and limitations. No full gateway campaign ran locally.

This preparation enables no writer or new topic. Direction contracts and advanced
Python adapters are released; the gateway now binds four Direction outputs and
the optional aligned strike overlay. Complete runtime inventory, destination registry
compatibility/seed, archive/mirror preparation and each writer's runtime qualification
remain pre-writer gates. The earlier production gateway image does not qualify these
new bindings. Finite Codex/Claude source/main-preparation approvals allow a main merge
for the guarded Jenkins/es4 qualification run. Compatible reader deployment requires
that run's success and runtime acceptance; writer activation remains separately held.

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
