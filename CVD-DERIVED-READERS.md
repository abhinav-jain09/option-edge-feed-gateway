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

The dependency advances to contracts0.11.0, whose Java/schema contents are unchanged
from0.10.0;0.11 adds the approved Python boundary. It must be released successfully
before this PR can build a runtime image. The candidate JAR is used only for local
unit preparation, never installed under an earlier version or deployed.

Validation: four focused CvdAvroRollingGatewayTest cases pass against the0.11
candidate, using production Confluent classes with an in-memory registry. They
cover retained JSON before and after real frames, exact aligned-book and
recommendation API bytes, wrong-topic schema refusal, truncated frames and null
refusal. The new test uses explicit source-shaped fixtures, not live captures.
No algorithm or codec hot path changed; the existing configured codec is reused.
The first lifecycle test command also executed the POM's separately pinned Spring
context smoke with Kafka disabled; subsequent narrow validation used test-compile
with skipped execution and surefire:test for this class only. Full suite/mutation
qualification remains Jenkins/es4 work, not a local test claim.

This draft enables no writer or new topic. Remaining Direction/strike-intelligence
contracts, actual reader inventory, destination registry compatibility and runtime
qualification remain required before their writers are enabled. Do not merge this
branch until the pending gateway0.10 image has finished and the0.11 release is ready.

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
