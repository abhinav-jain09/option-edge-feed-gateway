# Same-topic derived reader preparation

Incomplete source preparation for the coordinated CVD roll-forward migration.
No merge, writer enablement or runtime eligibility is claimed here. Topics,
consumer groups and offsets remain unchanged. Latest merged gateway main was
integrated before the new EAV binding; the dependency and image-build flow follow
qualified latest contracts main, with immutable coordinates/digests as provenance.

| Requirement | Implementation / evidence | State |
|---|---|---|
| Existing-topic EAV reader | FeedGatewayService.cvdAvroBindings includes EARLY_AUCTION_LEDGER on settings.earlyAuctionVerdictTopic | Prepared |
| Retained JSON / canonical Avro equivalence | CvdAvroRollingGatewayTest uses the actual configured decoder, six engine-emitted fixture models interleaved JSON/Avro/JSON, byte-identical API strings and immediate cache admission | Ordinary unit validated |
| Decode/schema failure policy | Wrong-topic frame, truncated frame and forbidden null refused; no Avro-to-JSON fallback | Ordinary unit validated; real registry OPEN |
| Original opt-in cache subscription | Existing earlyAuctionVerdictEnabled default false; cache-only consumer remains sole reader, no new WebSocket event or replay namespace | Unchanged; runtime closure OPEN |
| Latest dependency integration | Latest main includes Indicator reader and clean-package fixes; merge conflict resolved solely to main's contracts coordinate | Targeted unit validated; final campaign OPEN |
| Coordinated migration | Direction outputs, advanced Python adapters, remaining readers, registry seed/compatibility, archive/mirror and Jenkins verification | OPEN |

The generic test fixture builder now handles the released API scalar and ordered
map metadata, so it creates API models rather than physical scalar records when
checking the latest schemas. Production does not infer schema from fixtures.
The six EAV fixtures are copied from the processing engine-generated ordinary
unit fixture, preserving its source SHA/provenance. They are synthetic minute
outputs, not captured live market evidence.

Local command (Java21, task-local official contracts artifact):
`mvn -B -Dmaven.repo.local=<qualified-task-repo> -Dtest=CvdAvroRollingGatewayTest,CvdRetainedPollTest,EarlyAuctionVerdictWiringTest test`.
The three focused classes pass22 tests; the POM's mandatory separate mock Spring
context smoke passes1. The isolated binding-absence control has a positive
baseline and must fail the exact EAV schema-binding assertion when removed.
Task evidence: gateway-eav-reader-focused-tests.log and
gateway-eav-binding-control/{baseline.log,binding-absent.log,report.json}.
No full gateway campaign, broker, feed, load or replay was run on the Mac.

Changed steady-state behavior is only admission of one additional explicit schema
through the same released decoder. Decoder CPU/allocation for these exact six
models was measured in the processing bridge increment with the same official
contracts JVM classes, one prime plus five single values per arm, mock registry,
macOS arm64/JDK21. Avro medians61–371 microseconds and4776–13032 allocated bytes;
plain JSON baselines1–2 microseconds/320–888 bytes. The fixture-only5ms/256KiB
budget passes; all relative regressions above10% remain OPEN. This is reuse of
codec evidence, not a gateway cache-loop or service-level measurement. Cache
folding is unchanged and its existing unit suite is included. A single final
full gateway campaign on es4 after all remaining bindings, including actual
cadence/heap/restore/cold-registry/cache budgets, remains required. Container
budgets and record batching are unchanged. No producer may be enabled based on
this increment. Changed-line Claude then Codex outcomes are tracked in PR219.

The prime is unmeasured; each raw array contains the following five measured
operations. No topic cadence has been measured or assumed. The fixtures cover
independent codec cases, including two different session terminal kinds, and are
not an internally coherent market session.

Extending the existing compatible-reader policy also extends its operational
effects: invalid retained nonframed EAV values (including null) are skipped by
pollCompatible and counted in retainedCvdRecordsDropped, before the EAV drop
counter. The existing stderr label still says CVD. Avro-framed decode failures
remain fatal and can stall the shared state consumer when EAV is enabled.
Cold-registry/poison-frame recovery and decoding a bounded oversized value before
the existing64KiB EAV cache gate belong in the final es4 campaign. The global
binding map rejects duplicate/blank/new-.avro topic overrides at construction,
even when EAV is disabled. These are disclosed limits, not runtime qualification.
